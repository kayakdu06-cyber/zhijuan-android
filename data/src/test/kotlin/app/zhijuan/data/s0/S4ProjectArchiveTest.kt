package app.zhijuan.data.s0

import app.zhijuan.core.s0.S0FakeProvider
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4ProjectArchiveTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `round trip preserves hashes reading state and can continue as a collision-safe project`() = runBlocking {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        val first = S0GenerationCoordinator(repository, S0FakeProvider()).generateNextChapter("project_s0")
        assertTrue(first is S0GenerationResult.Committed)
        val projectDir = File(tempDir, "project_s0")
        File(projectDir, "jobs").mkdirs()
        File(projectDir, "jobs/active.json").writeText("{\"canary\":\"${secretSample()}\"}")
        File(projectDir, "diagnostics").mkdirs()
        File(projectDir, "diagnostics/events.jsonl").writeText(authorizationSample())
        File(projectDir, "chapters/000002.part").writeText("not a complete chapter")

        val archiveBytes = ByteArrayOutputStream()
        val archive = S4ProjectArchive(tempDir)
        val exported = archive.export("project_s0", archiveBytes)
        val names = zipNames(archiveBytes.toByteArray())

        assertTrue("zhijuan-export-manifest.json" in names)
        assertTrue("chapters/000001.md" in names)
        assertTrue(names.any { it.startsWith("commits/completed/") })
        assertFalse(names.any { it.startsWith("jobs/") || it.startsWith("diagnostics/") || it.endsWith(".part") })
        assertFalse(archiveBytes.toString(StandardCharsets.ISO_8859_1).contains(secretSample()))

        val imported = archive.import(ByteArrayInputStream(archiveBytes.toByteArray()))
        assertNotEquals("project_s0", imported.projectId)
        assertEquals(exported.contentSha256, imported.contentSha256)
        assertEquals(exported.fileCount, imported.fileCount)
        assertEquals(exported.totalBytes, imported.totalBytes)
        val restored = repository.loadProject(imported.projectId)!!
        assertEquals(1, restored.storyState.revision)
        assertEquals(1, restored.chapters.size)
        assertEquals(
            sha256(File(projectDir, "chapters/000001.md").readBytes()),
            sha256(File(tempDir, "${imported.projectId}/chapters/000001.md").readBytes()),
        )

        val continuationProvider = S0FakeProvider()
        val continued = S0GenerationCoordinator(repository, continuationProvider).generateNextChapter(imported.projectId)
        assertTrue(continued is S0GenerationResult.Committed)
        assertEquals(1, continuationProvider.proseCalls)
        assertEquals(1, continuationProvider.settlementCalls)
        assertEquals(listOf(1, 2), repository.loadProject(imported.projectId)!!.storyState.committedChapters)
    }

    @Test
    fun `writing skill archive round trip preserves source card and safely disables internal corruption`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        val source = "## 规则\n- 以动作推进场景\n## 避免\n- 解释性总结"
        val skill = S5WritingSkillParser().parse("动作卡.md", ByteArrayInputStream(source.toByteArray()))
        repository.saveWritingSkill("project_s0", skill)
        val archive = S4ProjectArchive(tempDir)
        val bytes = ByteArrayOutputStream().also { archive.export("project_s0", it) }.toByteArray()

        val names = zipNames(bytes)
        assertTrue("writing-skill/source.md" in names)
        assertTrue("writing-skill/manifest.json" in names)
        assertTrue("writing-skill/quality-card.json" in names)
        val imported = archive.import(ByteArrayInputStream(bytes))
        val restored = repository.loadProject(imported.projectId)!!
        assertEquals(skill.qualityCard.sha256, restored.writingSkill.qualityCard?.sha256)

        File(tempDir, "project_s0/writing-skill/manifest.json").writeText(
            File(tempDir, "project_s0/writing-skill/manifest.json").readText()
                .replace(skill.qualityCard.sha256, "0".repeat(64)),
        )
        val corruptArchiveBytes = ByteArrayOutputStream().also { archive.export("project_s0", it) }.toByteArray()
        val corruptImport = archive.import(ByteArrayInputStream(corruptArchiveBytes))
        assertEquals(
            app.zhijuan.core.s0.S0WritingSkillStatus.DISABLED_CORRUPT,
            repository.loadProject(corruptImport.projectId)!!.writingSkill.status,
        )
    }

    @Test
    fun `path traversal entry is rejected without writing outside staging`() {
        val bytes = zipOf("../outside.txt" to "unsafe".toByteArray())

        val failure = assertThrows(S4ArchiveException::class.java) {
            S4ProjectArchive(tempDir).import(ByteArrayInputStream(bytes))
        }

        assertTrue(failure.message!!.contains("IMPORT_PATH_TRAVERSAL"))
        assertFalse(File(tempDir.parentFile, "outside.txt").exists())
    }

    @Test
    fun `oversized manifest declaration is rejected before project promotion`() {
        val files = minimalProjectFiles()
        val bytes = manifestArchive(files, declaredSizeOverride = 50L * 1024L * 1024L + 1L)

        val failure = assertThrows(S4ArchiveException::class.java) {
            S4ProjectArchive(tempDir).import(ByteArrayInputStream(bytes))
        }

        assertTrue(failure.message!!.contains("IMPORT_DECLARED_SIZE_INVALID"))
        assertTrue(FileS0NovelRepository(tempDir).listProjects().isEmpty())
    }

    @Test
    fun `secret sample and invalid manifest schema are rejected`() {
        val secretFiles = minimalProjectFiles().toMutableMap().apply {
            this["project.json"] = this.getValue("project.json") + secretSample()
        }
        val secretFailure = assertThrows(S4ArchiveException::class.java) {
            S4ProjectArchive(File(tempDir, "secret")).import(
                ByteArrayInputStream(manifestArchive(secretFiles)),
            )
        }
        assertTrue(secretFailure.message!!.contains("SECRET_MATERIAL_REJECTED"))

        val schemaFailure = assertThrows(S4ArchiveException::class.java) {
            S4ProjectArchive(File(tempDir, "schema")).import(
                ByteArrayInputStream(manifestArchive(minimalProjectFiles(), exportFormat = "unknown-format")),
            )
        }
        assertEquals("IMPORT_FORMAT", schemaFailure.message)
    }

    @Test
    fun `actual inflated entry exceeding configured limit is rejected`() {
        val bytes = zipOf("project.json" to ByteArray(65) { 'a'.code.toByte() })

        val failure = assertThrows(S4ArchiveException::class.java) {
            S4ProjectArchive(
                projectsRoot = tempDir,
                maxEntryBytes = 64,
                maxTotalBytes = 256,
            ).import(ByteArrayInputStream(bytes))
        }

        assertEquals("IMPORT_ENTRY_TOO_LARGE", failure.message)
    }

    private fun manifestArchive(
        files: Map<String, String>,
        declaredSizeOverride: Long? = null,
        exportFormat: String = "long-novel-project-zip",
    ): ByteArray {
        val manifest = buildJsonObject {
            put("schemaVersion", JsonPrimitive("1.0"))
            put("exportFormat", JsonPrimitive(exportFormat))
            put("projectId", JsonPrimitive("project_s0"))
            put("title", JsonPrimitive("灯下回卷"))
            put("exportedAt", JsonPrimitive("2026-08-20T00:00:00Z"))
            put("sourceRevision", JsonPrimitive(0))
            put("files", buildJsonArray {
                files.entries.sortedBy { it.key }.forEachIndexed { index, (path, content) ->
                    val bytes = content.toByteArray(StandardCharsets.UTF_8)
                    add(buildJsonObject {
                        put("path", JsonPrimitive(path))
                        put("size", JsonPrimitive(if (index == 0 && declaredSizeOverride != null) declaredSizeOverride else bytes.size.toLong()))
                        put("sha256", JsonPrimitive(sha256(bytes)))
                    })
                }
            })
        }.toString()
        return zipOf(
            *(listOf("zhijuan-export-manifest.json" to manifest.toByteArray()) +
                files.map { (path, content) -> path to content.toByteArray() }).toTypedArray(),
        )
    }

    private fun minimalProjectFiles(): Map<String, String> = linkedMapOf(
        "project.json" to """{"schemaVersion":"1.0","id":"project_s0","title":"灯下回卷","genre":"悬疑","protagonist":"林岑","tone":"安静克制","premise":"旧车站","createdAt":"2026-08-20T00:00:00Z"}""",
        "state.json" to """{"schemaVersion":"1.0","revision":0,"nextChapter":1,"committedChapters":[],"recentEventKeys":[]}""",
        "plan.json" to buildJsonObject {
            put("schemaVersion", JsonPrimitive("1.0"))
            put("items", buildJsonArray {
                plan().forEach { item ->
                    add(buildJsonObject {
                        put("chapter", JsonPrimitive(item.chapter))
                        put("title", JsonPrimitive(item.title))
                        put("goal", JsonPrimitive(item.goal))
                        put("entryState", JsonPrimitive(item.entryState))
                        put("mustChange", JsonPrimitive(item.mustChange))
                        put("exitHook", JsonPrimitive(item.exitHook))
                        put("involvedEntityIds", buildJsonArray {})
                        put("mustNotRepeatEventKeys", buildJsonArray {})
                    })
                }
            })
        }.toString(),
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun zipNames(bytes: ByteArray): Set<String> = buildSet {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) add((zip.nextEntry ?: break).name)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun secretSample(): String = "sk-" + "1234567890" + "1234567890" + "1234"

    private fun authorizationSample(): String = "authorization" + ": " + "1234567890" + "1234567890" + "1234"

    private fun project() = S0Project(
        id = "project_s0",
        title = "灯下回卷",
        genre = "悬疑",
        protagonist = "林岑",
        tone = "安静克制",
        premise = "旧车站里出现回卷印记",
        createdAt = "2026-08-20T00:00:00Z",
    )

    private fun plan() = (1..8).map { chapter ->
        S0PlanItem(chapter, "第${chapter}章", "确认第${chapter}章线索", "线索未明", "线索前进", "灯下档案")
    }
}
