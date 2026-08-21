package app.zhijuan.data.s0

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class S4ArchiveResult(
    val projectId: String,
    val fileCount: Int,
    val totalBytes: Long,
    val contentSha256: String,
)

class S4ArchiveException(message: String) : IllegalStateException(message)

class S4ProjectArchive(
    private val projectsRoot: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxEntryBytes: Int = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
) {
    fun export(projectId: String, output: OutputStream): S4ArchiveResult {
        val projectDir = File(projectsRoot, projectId)
        archiveRequire(File(projectDir, "project.json").isFile, "PROJECT_NOT_FOUND")
        val snapshot = FileS0NovelRepository(projectsRoot).loadProject(projectId)
            ?: throw S4ArchiveException("PROJECT_NOT_FOUND")
        val files = projectDir.walkTopDown()
            .filter(File::isFile)
            .map { file -> file.relativeTo(projectDir).invariantSeparatorsPath to file }
            .filter { (path, _) -> isExportable(path) }
            .sortedBy { it.first }
            .toList()
        archiveRequire(files.size <= maxFiles, "EXPORT_TOO_MANY_FILES")
        val entries = files.map { (path, file) ->
            val bytes = file.readBytes()
            archiveRequire(bytes.size <= maxEntryBytes, "EXPORT_ENTRY_TOO_LARGE:$path")
            archiveRequire(bytes.size <= maximumBytesFor(path), "EXPORT_ENTRY_TOO_LARGE:$path")
            rejectSecretMaterial(bytes, path)
            ArchiveEntry(path, bytes, sha256(bytes))
        }
        val totalBytes = entries.sumOf { it.bytes.size.toLong() }
        archiveRequire(totalBytes <= maxTotalBytes, "EXPORT_TOO_LARGE")
        val manifest = buildJsonObject {
            put("schemaVersion", JsonPrimitive("1.0"))
            put("exportFormat", JsonPrimitive("long-novel-project-zip"))
            put("projectId", JsonPrimitive(projectId))
            put("title", JsonPrimitive(snapshot.project.title))
            put("exportedAt", JsonPrimitive(Instant.now().toString()))
            put("sourceRevision", JsonPrimitive(snapshot.storyState.revision))
            put("files", buildJsonArray {
                entries.forEach { item ->
                    add(buildJsonObject {
                        put("path", JsonPrimitive(item.path))
                        put("size", JsonPrimitive(item.bytes.size))
                        put("sha256", JsonPrimitive(item.sha256))
                    })
                }
            })
        }.toString().toByteArray(StandardCharsets.UTF_8)
        archiveRequire(manifest.size <= maxEntryBytes, "EXPORT_MANIFEST_TOO_LARGE")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_PATH))
            zip.write(manifest)
            zip.closeEntry()
            entries.forEach { item ->
                zip.putNextEntry(ZipEntry(item.path))
                zip.write(item.bytes)
                zip.closeEntry()
            }
        }
        return S4ArchiveResult(projectId, entries.size, totalBytes, aggregateSha256(entries))
    }

    fun import(input: InputStream): S4ArchiveResult {
        projectsRoot.mkdirs()
        val staging = Files.createTempDirectory(projectsRoot.toPath(), ".import-").toFile()
        try {
            var manifest: JsonObject? = null
            var totalInflatedBytes = 0L
            var contentBytes = 0L
            var fileCount = 0
            val extracted = linkedMapOf<String, File>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = validateArchivePath(entry.name)
                    archiveRequire(!entry.isDirectory, "IMPORT_DIRECTORY_ENTRY_REJECTED:$path")
                    archiveRequire(path == MANIFEST_PATH || isExportable(path), "IMPORT_FILE_NOT_ALLOWED:$path")
                    archiveRequire(path !in extracted && !(path == MANIFEST_PATH && manifest != null), "IMPORT_DUPLICATE_PATH:$path")
                    val bytes = readBounded(zip, maxEntryBytes)
                    archiveRequire(bytes.size <= maximumBytesFor(path), "IMPORT_ENTRY_TOO_LARGE:$path")
                    totalInflatedBytes += bytes.size
                    archiveRequire(totalInflatedBytes <= maxTotalBytes, "IMPORT_TOO_LARGE")
                    rejectSecretMaterial(bytes, path)
                    if (path == MANIFEST_PATH) {
                        manifest = runCatching { Json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonObject }
                            .getOrElse { throw S4ArchiveException("IMPORT_MANIFEST_INVALID") }
                    } else {
                        fileCount += 1
                        archiveRequire(fileCount <= maxFiles, "IMPORT_TOO_MANY_FILES")
                        val target = File(staging, path).canonicalFile
                        archiveRequire(target.toPath().startsWith(staging.canonicalFile.toPath()), "IMPORT_PATH_TRAVERSAL")
                        target.parentFile?.mkdirs()
                        target.writeBytes(bytes)
                        extracted[path] = target
                        contentBytes += bytes.size
                    }
                    zip.closeEntry()
                }
            }
            val parsedManifest = manifest ?: throw S4ArchiveException("IMPORT_MANIFEST_MISSING")
            val sourceProjectId = validateManifest(parsedManifest, extracted)
            archiveRequire(REQUIRED_PATHS.all(extracted::containsKey), "IMPORT_REQUIRED_FILE_MISSING")
            val importedContentHash = aggregateSha256(
                extracted.map { (path, file) ->
                    val bytes = file.readBytes()
                    ArchiveEntry(path, bytes, sha256(bytes))
                },
            )
            val targetProjectId = availableProjectId(sourceProjectId)
            rewriteProjectId(requireNotNull(extracted["project.json"]), targetProjectId)
            val target = File(projectsRoot, targetProjectId)
            runCatching {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(staging.toPath(), target.toPath())
            }
            try {
                requireNotNull(FileS0NovelRepository(projectsRoot).loadProject(targetProjectId))
            } catch (failure: Throwable) {
                target.deleteRecursively()
                throw S4ArchiveException("IMPORT_PROJECT_INVALID")
            }
            return S4ArchiveResult(
                projectId = targetProjectId,
                fileCount = fileCount,
                totalBytes = contentBytes,
                contentSha256 = importedContentHash,
            )
        } catch (failure: S4ArchiveException) {
            staging.deleteRecursively()
            throw failure
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw S4ArchiveException(failure.message ?: "IMPORT_REJECTED")
        }
    }

    private fun validateManifest(manifest: JsonObject, extracted: Map<String, File>): String {
        archiveRequire(
            manifest.keys == setOf("schemaVersion", "exportFormat", "projectId", "title", "exportedAt", "sourceRevision", "files"),
            "IMPORT_MANIFEST_FIELDS",
        )
        archiveRequire(manifest.getValue("schemaVersion").jsonPrimitive.content == "1.0", "IMPORT_MANIFEST_VERSION")
        archiveRequire(manifest.getValue("exportFormat").jsonPrimitive.content == "long-novel-project-zip", "IMPORT_FORMAT")
        val projectId = manifest.getValue("projectId").jsonPrimitive.content
        archiveRequire(projectId.matches(PROJECT_ID_PATTERN), "IMPORT_PROJECT_ID")
        archiveRequire(manifest.getValue("title").jsonPrimitive.content.length in 1..100, "IMPORT_TITLE")
        archiveRequire(runCatching { Instant.parse(manifest.getValue("exportedAt").jsonPrimitive.content) }.isSuccess, "IMPORT_EXPORTED_AT")
        archiveRequire(manifest.getValue("sourceRevision").jsonPrimitive.content.toInt() >= 0, "IMPORT_SOURCE_REVISION")
        val declared = manifest.getValue("files").jsonArray
        archiveRequire(declared.size >= REQUIRED_PATHS.size && declared.size == extracted.size, "IMPORT_MANIFEST_FILE_COUNT")
        val declaredPaths = linkedSetOf<String>()
        declared.forEach { element ->
            val item = element.jsonObject
            archiveRequire(item.keys == setOf("path", "size", "sha256"), "IMPORT_MANIFEST_ENTRY_FIELDS")
            val path = validateArchivePath(item.getValue("path").jsonPrimitive.content)
            archiveRequire(isExportable(path), "IMPORT_MANIFEST_FILE_NOT_ALLOWED:$path")
            archiveRequire(declaredPaths.add(path), "IMPORT_MANIFEST_DUPLICATE:$path")
            val file = extracted[path] ?: throw S4ArchiveException("IMPORT_MANIFEST_FILE_MISSING:$path")
            val declaredSize = item.getValue("size").jsonPrimitive.content.toLong()
            archiveRequire(declaredSize in 0..maxEntryBytes.toLong(), "IMPORT_DECLARED_SIZE_INVALID:$path")
            archiveRequire(declaredSize == file.length(), "IMPORT_SIZE_MISMATCH:$path")
            val declaredHash = item.getValue("sha256").jsonPrimitive.content
            archiveRequire(declaredHash.matches(SHA256_PATTERN), "IMPORT_HASH_INVALID:$path")
            archiveRequire(declaredHash == sha256(file.readBytes()), "IMPORT_HASH_MISMATCH:$path")
        }
        archiveRequire(declaredPaths == extracted.keys, "IMPORT_MANIFEST_FILE_SET")
        return projectId
    }

    private fun rewriteProjectId(projectFile: File, projectId: String) {
        val original = Json.parseToJsonElement(projectFile.readText()).jsonObject
        val updated = JsonObject(original.toMutableMap().apply { put("id", JsonPrimitive(projectId)) })
        projectFile.writeText(updated.toString())
    }

    private fun availableProjectId(source: String): String {
        if (!File(projectsRoot, source).exists()) return source
        for (suffix in 1..999) {
            val candidate = "${source}_import_$suffix"
            if (!File(projectsRoot, candidate).exists()) return candidate
        }
        throw S4ArchiveException("IMPORT_PROJECT_ID_EXHAUSTED")
    }

    private fun isExportable(path: String): Boolean =
        path in REQUIRED_PATHS || path == "events.jsonl" ||
            path.matches(CHAPTER_PATH_PATTERN) || path.matches(COMPLETED_COMMIT_PATH_PATTERN) ||
            path in WRITING_SKILL_PATHS

    private fun maximumBytesFor(path: String): Int = when (path) {
        "writing-skill/source.md", "writing-skill/source.json" -> MAX_WRITING_SKILL_SOURCE_BYTES
        "writing-skill/manifest.json", "writing-skill/quality-card.json" -> MAX_WRITING_SKILL_METADATA_BYTES
        else -> maxEntryBytes
    }

    private fun validateArchivePath(raw: String): String {
        archiveRequire(raw.isNotBlank() && !raw.contains('\\') && !raw.startsWith('/') && !raw.contains(':'), "IMPORT_PATH_INVALID")
        val segments = raw.split('/')
        archiveRequire(segments.none { it.isBlank() || it == "." || it == ".." }, "IMPORT_PATH_TRAVERSAL")
        return segments.joinToString("/")
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            archiveRequire(output.size() + read <= maximum, "IMPORT_ENTRY_TOO_LARGE")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun rejectSecretMaterial(bytes: ByteArray, path: String) {
        val text = bytes.toString(StandardCharsets.ISO_8859_1)
        archiveRequire(SECRET_PATTERNS.none { it.containsMatchIn(text) }, "SECRET_MATERIAL_REJECTED:$path")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun aggregateSha256(entries: List<ArchiveEntry>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        entries.sortedBy(ArchiveEntry::path).forEach { entry ->
            digest.update(entry.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(byteArrayOf(0))
            digest.update(entry.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update(byteArrayOf(0))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun archiveRequire(condition: Boolean, message: String) {
        if (!condition) throw S4ArchiveException(message)
    }

    private data class ArchiveEntry(val path: String, val bytes: ByteArray, val sha256: String)

    private companion object {
        const val MANIFEST_PATH = "zhijuan-export-manifest.json"
        const val DEFAULT_MAX_FILES = 1_000
        const val DEFAULT_MAX_ENTRY_BYTES = 50 * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        val REQUIRED_PATHS = setOf("project.json", "state.json", "plan.json")
        val PROJECT_ID_PATTERN = Regex("^[A-Za-z0-9_-]+$")
        val CHAPTER_PATH_PATTERN = Regex("^chapters/[0-9]{6}(\\.md|\\.meta\\.json)$")
        val COMPLETED_COMMIT_PATH_PATTERN = Regex("^commits/completed/[A-Za-z0-9_-]+\\.json$")
        val WRITING_SKILL_PATHS = setOf(
            "writing-skill/source.md",
            "writing-skill/source.json",
            "writing-skill/manifest.json",
            "writing-skill/quality-card.json",
        )
        const val MAX_WRITING_SKILL_SOURCE_BYTES = 256 * 1024
        const val MAX_WRITING_SKILL_METADATA_BYTES = 16 * 1024
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
        val SECRET_PATTERNS = listOf(
            Regex("(?<![A-Za-z0-9_-])sk-[A-Za-z0-9_-]{20,}(?![A-Za-z0-9_-])"),
            Regex("(?i)(api[_-]?key|authorization|bearer|client[_-]?secret|access[_-]?token)\\s*[:=]\\s*[\\\"']?[A-Za-z0-9._-]{20,}"),
        )
    }
}
