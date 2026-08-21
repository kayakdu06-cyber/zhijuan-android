package app.zhijuan.data.s0

import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0Event
import app.zhijuan.core.s0.S0NovelRepository
import app.zhijuan.core.s0.S0PendingCommit
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0StoryState
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0WritingQualityCard
import app.zhijuan.core.s0.S0WritingSkillFormat
import app.zhijuan.core.s0.S0WritingSkillImport
import app.zhijuan.core.s0.S0WritingSkillState
import app.zhijuan.core.s0.S0WritingSkillStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class S0StorageException(message: String) : IllegalStateException(message)

/** File-only repository for the first vertical slice. It deliberately has no Room or database dependency. */
class FileS0NovelRepository(
    private val root: File,
    private val commitStepObserver: (String) -> Unit = {},
) : S0NovelRepository {
    private val json = Json { ignoreUnknownKeys = false }

    init {
        root.mkdirs()
    }

    override fun createProject(
        project: S0Project,
        plan: List<S0PlanItem>,
        writingSkill: S0WritingSkillImport?,
    ): S0ProjectSnapshot {
        require(project.id.matches(ID_PATTERN)) { "PROJECT_ID_INVALID" }
        require(project.title.isNotBlank()) { "PROJECT_TITLE_REQUIRED" }
        validatePlanWindow(plan, expectedFirstChapter = 1)
        val dir = projectDir(project.id)
        check(!dir.exists()) { "PROJECT_ALREADY_EXISTS" }
        val staging = File(root, ".create-${project.id}-${UUID.randomUUID().toString().replace("-", "")}")
        try {
            check(staging.mkdirs()) { "PROJECT_STAGE_CREATE_FAILED" }
            File(staging, "chapters").mkdirs()
            File(staging, "commits/pending").mkdirs()
            File(staging, "commits/completed").mkdirs()
            File(staging, "diagnostics").mkdirs()
            writeJson(File(staging, "project.json"), projectJson(project))
            writeJson(File(staging, "state.json"), stateJson(S0StoryState()))
            writeJson(File(staging, "plan.json"), planJson(plan))
            writingSkill?.let { writeWritingSkill(staging, it) }
            validateStagedProject(staging, project.id)
            check(staging.renameTo(dir)) { "PROJECT_PROMOTE_FAILED" }
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
        return loadProject(project.id) ?: throw S0StorageException("PROJECT_PROMOTE_FAILED")
    }

    override fun listProjects(): List<S0ProjectSnapshot> = root.listFiles()
        ?.filter { it.isDirectory && File(it, "project.json").isFile }
        ?.mapNotNull { runCatching { loadProject(it.name) }.getOrNull() }
        ?.sortedBy { it.project.createdAt }
        .orEmpty()

    override fun loadProject(projectId: String): S0ProjectSnapshot? {
        if (!projectId.matches(ID_PATTERN)) return null
        val dir = projectDir(projectId)
        val projectFile = File(dir, "project.json")
        if (!projectFile.isFile) return null
        return runCatching {
            val projectObject = json.parseToJsonElement(projectFile.readText()).jsonObject
            val projectSchemaVersion = projectObject.requiredString("schemaVersion")
            check(projectSchemaVersion in setOf("1.0", "1.1", "1.2")) { "PROJECT_SCHEMA_VERSION" }
            val stateObject = readJsonWithBackup(File(dir, "state.json"))
            val planObject = json.parseToJsonElement(File(dir, "plan.json").readText()).jsonObject
            val project = S0Project(
                id = projectObject.requiredString("id"),
                title = projectObject.requiredString("title"),
                genre = projectObject.requiredString("genre"),
                protagonist = projectObject.requiredString("protagonist"),
                tone = projectObject.requiredString("tone"),
                premise = projectObject.requiredString("premise"),
                contentScale = projectObject["contentScale"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { S0ContentScale.valueOf(it) }.getOrNull() }
                    ?: S0ContentScale.QING_XU,
                plotPace = projectObject["plotPace"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { S0PlotPace.valueOf(it) }.getOrNull() }
                    ?: S0PlotPace.BALANCED,
                createdAt = projectObject.requiredString("createdAt"),
            )
            S0ProjectSnapshot(
                project = project,
                storyState = stateFrom(stateObject),
                plan = planFrom(planObject.required("items").jsonArray),
                chapters = readChapters(dir),
                writingSkill = readWritingSkill(dir),
            )
        }.getOrElse { failure ->
            throw S0StorageException("PROJECT_CORRUPT:${failure.message.orEmpty()}")
        }
    }

    override fun replacePlan(projectId: String, expectedRevision: Int, plan: List<S0PlanItem>): S0ProjectSnapshot {
        val snapshot = loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
        check(snapshot.storyState.revision == expectedRevision) { "BASE_REVISION_MISMATCH" }
        check(snapshot.chapters.none { it.state != S0ChapterState.COMMITTED }) { "DRAFT_REQUIRES_SETTLEMENT" }
        validatePlanWindow(plan, snapshot.storyState.nextChapter)
        writeJson(File(projectDir(projectId), "plan.json"), planJson(plan))
        return loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
    }

    override fun deleteProject(projectId: String): Boolean {
        val dir = validatedDeleteTarget(projectId)
        if (!dir.isDirectory) return false
        check(!File(dir, "jobs/active.json").isFile) { "PROJECT_DELETE_ACTIVE_JOB" }
        check(File(dir, "commits/pending").listFiles().orEmpty().none { it.isFile }) { "PROJECT_DELETE_PENDING_COMMIT" }
        return dir.deleteRecursively()
    }

    override fun discardProject(projectId: String): Boolean {
        val dir = validatedDeleteTarget(projectId)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    override fun saveWritingSkill(projectId: String, writingSkill: S0WritingSkillImport): S0ProjectSnapshot {
        val projectDirectory = projectDir(projectId)
        check(File(projectDirectory, "project.json").isFile) { "PROJECT_NOT_FOUND" }
        check(!File(projectDirectory, "jobs/active.json").isFile) { "WRITING_SKILL_ACTIVE_JOB" }
        check(File(projectDirectory, "commits/pending").listFiles().orEmpty().none(File::isFile)) {
            "WRITING_SKILL_PENDING_COMMIT"
        }
        val staged = File(projectDirectory, ".writing-skill-${UUID.randomUUID().toString().replace("-", "")}")
        val target = File(projectDirectory, "writing-skill")
        val backup = File(projectDirectory, ".writing-skill-backup-${UUID.randomUUID().toString().replace("-", "")}")
        try {
            check(staged.mkdirs()) { "WRITING_SKILL_STAGE_CREATE_FAILED" }
            writeWritingSkillDirectory(staged, writingSkill)
            check(readWritingSkillDirectory(staged).status == S0WritingSkillStatus.ACTIVE) { "WRITING_SKILL_STAGE_INVALID" }
            if (target.exists()) check(target.renameTo(backup)) { "WRITING_SKILL_BACKUP_FAILED" }
            check(staged.renameTo(target)) { "WRITING_SKILL_PROMOTE_FAILED" }
            if (backup.exists()) backup.deleteRecursively()
        } catch (failure: Throwable) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw failure
        } finally {
            if (staged.exists()) staged.deleteRecursively()
            if (backup.exists() && target.exists()) backup.deleteRecursively()
        }
        return loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
    }

    override fun removeWritingSkill(projectId: String): S0ProjectSnapshot {
        val projectDirectory = projectDir(projectId)
        check(File(projectDirectory, "project.json").isFile) { "PROJECT_NOT_FOUND" }
        check(!File(projectDirectory, "jobs/active.json").isFile) { "WRITING_SKILL_ACTIVE_JOB" }
        check(File(projectDirectory, "commits/pending").listFiles().orEmpty().none(File::isFile)) {
            "WRITING_SKILL_PENDING_COMMIT"
        }
        val target = File(projectDirectory, "writing-skill")
        if (target.exists()) {
            val removed = File(projectDirectory, ".writing-skill-remove-${UUID.randomUUID().toString().replace("-", "")}")
            check(target.renameTo(removed)) { "WRITING_SKILL_REMOVE_FAILED" }
            check(removed.deleteRecursively()) { "WRITING_SKILL_REMOVE_FAILED" }
        }
        return loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
    }

    override fun saveContentScale(projectId: String, contentScale: S0ContentScale): S0ProjectSnapshot {
        val projectDirectory = projectDir(projectId)
        val projectFile = File(projectDirectory, "project.json")
        check(projectFile.isFile) { "PROJECT_NOT_FOUND" }
        check(!File(projectDirectory, "jobs/active.json").isFile) { "CONTENT_SCALE_ACTIVE_JOB" }
        check(File(projectDirectory, "commits/pending").listFiles().orEmpty().none(File::isFile)) {
            "CONTENT_SCALE_PENDING_COMMIT"
        }
        val current = loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
        writeJson(projectFile, projectJson(current.project.copy(contentScale = contentScale)))
        return loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
    }

    override fun savePlotPace(projectId: String, plotPace: S0PlotPace): S0ProjectSnapshot {
        val projectDirectory = projectDir(projectId)
        val projectFile = File(projectDirectory, "project.json")
        check(projectFile.isFile) { "PROJECT_NOT_FOUND" }
        check(!File(projectDirectory, "jobs/active.json").isFile) { "PLOT_PACE_ACTIVE_JOB" }
        check(File(projectDirectory, "commits/pending").listFiles().orEmpty().none(File::isFile)) {
            "PLOT_PACE_PENDING_COMMIT"
        }
        val current = loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
        writeJson(projectFile, projectJson(current.project.copy(plotPace = plotPace)))
        return loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
    }

    override fun saveReadableDraft(projectId: String, task: S0ChapterTask, prose: String): S0Chapter =
        saveDraft(projectId, task, prose, S0ChapterState.READABLE_DRAFT, incompleteReason = null)

    override fun saveIncompleteDraft(
        projectId: String,
        task: S0ChapterTask,
        prose: String,
        reason: String,
    ): S0Chapter {
        require(reason.isNotBlank()) { "INCOMPLETE_REASON_REQUIRED" }
        return saveDraft(projectId, task, prose, S0ChapterState.PAUSED, incompleteReason = reason)
    }

    private fun saveDraft(
        projectId: String,
        task: S0ChapterTask,
        prose: String,
        state: S0ChapterState,
        incompleteReason: String?,
    ): S0Chapter {
        require(prose.isNotBlank()) { "PROSE_EMPTY" }
        val dir = projectDir(projectId)
        val current = loadProject(projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
        if (current.storyState.revision != task.baseRevision) throw S0StorageException("BASE_REVISION_MISMATCH")
        val chapter = S0Chapter(
            number = task.chapter,
            title = task.title,
            taskId = task.taskId,
            prose = prose,
            state = state,
            incompleteReason = incompleteReason,
        )
        val chapterFile = File(dir, "chapters/${task.chapter.toString().padStart(6, '0')}.md")
        writeText(chapterFile, prose)
        writeJson(File(dir, "chapters/${chapterFile.nameWithoutExtension}.meta.json"), chapterMetaJson(chapter))
        return chapter
    }

    override fun writePendingCommit(commit: S0PendingCommit) {
        writeJson(File(projectDir(commit.projectId), "commits/pending/${commit.commitId}.json"), pendingJson(commit))
    }

    override fun applyPendingCommit(commitId: String) {
        require(commitId.matches(ID_PATTERN)) { "COMMIT_ID_INVALID" }
        val projectDir = root.listFiles()?.firstOrNull { File(it, "commits/pending/$commitId.json").isFile }
            ?: root.listFiles()?.firstOrNull { File(it, "commits/completed/$commitId.json").isFile }
            ?: return
        val pendingFile = File(projectDir, "commits/pending/$commitId.json")
        if (!pendingFile.isFile) return
        val completedFile = File(projectDir, "commits/completed/$commitId.json")
        val commit = commitFrom(json.parseToJsonElement(pendingFile.readText()).jsonObject)
        val current = loadProject(commit.projectId) ?: throw S0StorageException("PROJECT_NOT_FOUND")
        validEventLines(File(projectDir, "events.jsonl"))
        when {
            current.storyState.revision == commit.baseRevision -> writeJson(File(projectDir, "state.json"), stateJson(commit.newState))
            current.storyState.revision == commit.targetRevision -> Unit
            current.storyState.revision > commit.targetRevision -> throw S0StorageException("COMMIT_BASE_MISMATCH")
            else -> throw S0StorageException("COMMIT_BASE_MISMATCH")
        }
        commitStepObserver("STATE_WRITTEN")
        writeJson(File(projectDir, "plan.json"), planJson(commit.newPlan))
        commitStepObserver("PLAN_WRITTEN")
        appendEvents(File(projectDir, "events.jsonl"), commit.events)
        commitStepObserver("EVENTS_APPENDED")
        writeJson(File(projectDir, "chapters/${commit.chapter.toString().padStart(6, '0')}.meta.json"), chapterMetaJson(commit.chapterMeta))
        commitStepObserver("CHAPTER_META_WRITTEN")
        writeText(completedFile, pendingFile.readText())
        commitStepObserver("COMPLETED_WRITTEN")
        pendingFile.delete()
    }

    override fun recoverPendingCommits(): List<String> {
        val pending = root.listFiles().orEmpty().flatMap { project ->
            File(project, "commits/pending").listFiles().orEmpty().filter { it.extension == "json" }
        }
        pending.forEach { applyPendingCommit(it.nameWithoutExtension) }
        repairCompletedEventLogs()
        return pending.map { it.nameWithoutExtension }
    }

    /**
     * Older provider responses could reuse a syntactically valid eventId in a later chapter.
     * The state commit remained authoritative, but ID-only JSONL de-duplication then omitted that
     * later event. Completed commits are durable recovery records, so startup safely restores any
     * missing commit/eventKey pair without changing story state or chapter files.
     */
    private fun repairCompletedEventLogs() {
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { project ->
            val completedEvents = File(project, "commits/completed").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "json" }
                .map { completed -> commitFrom(json.parseToJsonElement(completed.readText()).jsonObject) }
                .sortedWith(compareBy<S0PendingCommit> { it.chapter }.thenBy { it.commitId })
                .flatMap { commit ->
                    commit.events.mapIndexed { index, event ->
                        event.copy(eventId = "event_${commit.commitId.removePrefix("commit_")}_${index + 1}")
                    }
                }
            if (completedEvents.isNotEmpty()) {
                appendEvents(File(project, "events.jsonl"), completedEvents)
            }
        }
    }

    private fun readChapters(dir: File): List<S0Chapter> = File(dir, "chapters").listFiles().orEmpty()
        .filter { it.name.endsWith(".meta.json") }
        .map { metaFile ->
            val meta = json.parseToJsonElement(metaFile.readText()).jsonObject
            val number = meta.requiredInt("number")
            val proseFile = File(metaFile.parentFile, "${number.toString().padStart(6, '0')}.md")
            S0Chapter(
                number = number,
                title = meta.requiredString("title"),
                taskId = meta.requiredString("taskId"),
                prose = proseFile.takeIf(File::isFile)?.readText().orEmpty(),
                state = S0ChapterState.valueOf(meta.requiredString("state")),
                summary = meta.optionalString("summary"),
                commitId = meta.optionalString("commitId"),
                incompleteReason = meta.optionalString("incompleteReason"),
            )
        }
        .sortedBy { it.number }

    private fun validatedDeleteTarget(projectId: String): File {
        require(projectId.matches(ID_PATTERN)) { "PROJECT_ID_INVALID" }
        val dir = projectDir(projectId)
        val rootPath = root.canonicalFile.toPath()
        val projectPath = dir.canonicalFile.toPath()
        check(projectPath.parent == rootPath) { "PROJECT_DELETE_TARGET_INVALID" }
        return dir
    }

    private fun projectDir(id: String) = File(root, id)

    private fun writeJson(file: File, value: JsonObject) = writeText(file, value.toString())

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (file.isFile) file.copyTo(File(file.parentFile, "${file.name}.bak"), overwrite = true)
        check(tmp.renameTo(file) || (tmp.copyTo(file, overwrite = true).let { tmp.delete(); true })) { "STORAGE_WRITE_FAILED:${file.name}" }
    }

    private fun appendEvents(file: File, events: List<S0Event>) {
        val validLines = validEventLines(file)
        val existingEvents = validLines.map { line -> eventFrom(json.parseToJsonElement(line).jsonObject) }
        val eventIds = existingEvents.mapTo(mutableSetOf(), S0Event::eventId)
        val eventIdentities = existingEvents.mapTo(mutableSetOf()) { it.commitId to it.eventKey }
        val accepted = buildList {
            events.forEach { event ->
                val identity = event.commitId to event.eventKey
                if (identity in eventIdentities) return@forEach
                var candidate = event
                if (candidate.eventId in eventIds) {
                    candidate = candidate.copy(eventId = collisionSafeEventId(candidate))
                }
                var salt = 1
                while (candidate.eventId in eventIds) {
                    candidate = candidate.copy(eventId = collisionSafeEventId(candidate, salt++))
                }
                add(candidate)
                eventIds += candidate.eventId
                eventIdentities += identity
            }
        }
        val newLines = accepted.joinToString(separator = "") { event -> eventJson(event).toString() + "\n" }
        val repairedPrefix = validLines.joinToString(separator = "\n", postfix = if (validLines.isEmpty()) "" else "\n")
        if (newLines.isNotEmpty() || (file.isFile && file.readText(StandardCharsets.UTF_8) != repairedPrefix)) {
            writeText(file, repairedPrefix + newLines)
        }
    }

    private fun collisionSafeEventId(event: S0Event, salt: Int = 0): String {
        val seed = "${event.commitId}|${event.eventKey}|$salt"
        return "event_${sha256(seed).take(24)}"
    }

    private fun validEventLines(file: File): List<String> {
        if (!file.isFile) return emptyList()
        val raw = file.readText(StandardCharsets.UTF_8)
        val completeLines = raw.split('\n').dropLast(1)
        return completeLines.filter(String::isNotBlank).map { candidate ->
            runCatching { json.parseToJsonElement(candidate).jsonObject.requiredString("eventId") }
                .getOrElse { throw S0StorageException("EVENT_LOG_CORRUPT") }
            candidate
        }
    }

    private fun readJsonWithBackup(file: File): JsonObject = runCatching {
        json.parseToJsonElement(file.readText()).jsonObject
    }.getOrElse { primaryFailure ->
        val backup = File(file.parentFile, "${file.name}.bak")
        if (!backup.isFile) throw primaryFailure
        runCatching { json.parseToJsonElement(backup.readText()).jsonObject }.getOrElse { throw primaryFailure }
    }

    private fun validatePlanWindow(plan: List<S0PlanItem>, expectedFirstChapter: Int) {
        require(plan.size in 8..10) { "PLAN_WINDOW_INVALID" }
        val chapters = plan.map(S0PlanItem::chapter)
        require(chapters == (expectedFirstChapter until expectedFirstChapter + plan.size).toList()) {
            "PLAN_SEQUENCE_INVALID"
        }
        require(plan.all { it.title.isNotBlank() && it.goal.isNotBlank() && it.mustChange.isNotBlank() }) {
            "PLAN_ITEM_INVALID"
        }
    }

    private fun validateStagedProject(staging: File, expectedProjectId: String) {
        val project = json.parseToJsonElement(File(staging, "project.json").readText()).jsonObject
        val state = json.parseToJsonElement(File(staging, "state.json").readText()).jsonObject
        val stagedPlan = json.parseToJsonElement(File(staging, "plan.json").readText()).jsonObject
        check(project.requiredString("id") == expectedProjectId) { "PROJECT_ID_MISMATCH" }
        check(stateFrom(state) == S0StoryState()) { "PROJECT_INITIAL_STATE_INVALID" }
        validatePlanWindow(planFrom(stagedPlan.required("items").jsonArray), expectedFirstChapter = 1)
        if (File(staging, "writing-skill").exists()) {
            check(readWritingSkill(staging).status == S0WritingSkillStatus.ACTIVE) { "WRITING_SKILL_STAGE_INVALID" }
        }
    }

    private fun writeWritingSkill(projectDirectory: File, writingSkill: S0WritingSkillImport) {
        val directory = File(projectDirectory, "writing-skill")
        check(directory.mkdirs()) { "WRITING_SKILL_DIRECTORY_CREATE_FAILED" }
        writeWritingSkillDirectory(directory, writingSkill)
    }

    private fun writeWritingSkillDirectory(directory: File, requested: S0WritingSkillImport) {
        val validated = S5WritingSkillParser().validateImport(requested)
        val sourceName = when (validated.format) {
            S0WritingSkillFormat.MARKDOWN -> "source.md"
            S0WritingSkillFormat.JSON -> "source.json"
        }
        val importedAt = Instant.now().toString()
        writeText(File(directory, sourceName), validated.sourceText)
        writeJson(File(directory, "quality-card.json"), writingQualityCardJson(validated.qualityCard))
        writeJson(
            File(directory, "manifest.json"),
            buildJsonObject {
                put("schemaVersion", "1.0")
                put("displayName", validated.qualityCard.name)
                put("sourceFileName", validated.sourceFileName)
                put("format", validated.format.name)
                put("sourceSha256", validated.sourceSha256)
                put("cardSha256", validated.qualityCard.sha256)
                put("cardVersion", validated.qualityCard.version)
                put("active", JsonPrimitive(true))
                put("importedAt", importedAt)
            },
        )
    }

    private fun readWritingSkill(projectDirectory: File): S0WritingSkillState {
        val directory = File(projectDirectory, "writing-skill")
        if (!directory.exists()) return S0WritingSkillState()
        return runCatching { readWritingSkillDirectory(directory) }.getOrElse {
            S0WritingSkillState(
                status = S0WritingSkillStatus.DISABLED_CORRUPT,
                errorCode = "WRITING_SKILL_CORRUPT",
            )
        }
    }

    private fun readWritingSkillDirectory(directory: File): S0WritingSkillState {
        val manifestFile = File(directory, "manifest.json")
        val cardFile = File(directory, "quality-card.json")
        check(manifestFile.isFile && manifestFile.length() in 1..MAX_SKILL_METADATA_BYTES) { "WRITING_SKILL_MANIFEST_INVALID" }
        check(cardFile.isFile && cardFile.length() in 1..MAX_SKILL_METADATA_BYTES) { "WRITING_SKILL_CARD_INVALID" }
        val manifest = json.parseToJsonElement(manifestFile.readText(StandardCharsets.UTF_8)).jsonObject
        check(manifest.keys == SKILL_MANIFEST_KEYS) { "WRITING_SKILL_MANIFEST_FIELDS" }
        check(manifest.requiredString("schemaVersion") == "1.0") { "WRITING_SKILL_MANIFEST_VERSION" }
        check(manifest.required("active").jsonPrimitive.content.toBooleanStrict()) { "WRITING_SKILL_INACTIVE" }
        val format = S0WritingSkillFormat.valueOf(manifest.requiredString("format"))
        val sourceFile = File(directory, if (format == S0WritingSkillFormat.MARKDOWN) "source.md" else "source.json")
        check(sourceFile.isFile && sourceFile.length() in 1..MAX_SKILL_SOURCE_BYTES) { "WRITING_SKILL_SOURCE_INVALID" }
        val sourceHash = app.zhijuan.data.s0.sha256(sourceFile.readBytes())
        check(sourceHash == manifest.requiredString("sourceSha256")) { "WRITING_SKILL_SOURCE_HASH_MISMATCH" }
        val cardObject = json.parseToJsonElement(cardFile.readText(StandardCharsets.UTF_8)).jsonObject
        check(cardObject.keys == SKILL_CARD_KEYS) { "WRITING_SKILL_CARD_FIELDS" }
        check(cardObject.requiredString("schemaVersion") == "1.0") { "WRITING_SKILL_CARD_VERSION" }
        check(cardObject.requiredString("scope") == "chapter_prose_quality_card") { "WRITING_SKILL_CARD_SCOPE" }
        val card = S0WritingQualityCard(
            name = cardObject.requiredString("name"),
            version = cardObject.requiredInt("version"),
            rules = cardObject.required("rules").jsonArray.map { it.jsonPrimitive.content },
            avoid = cardObject.required("avoid").jsonArray.map { it.jsonPrimitive.content },
            preferredTerms = cardObject.required("preferredTerms").jsonArray.map { it.jsonPrimitive.content },
            sha256 = manifest.requiredString("cardSha256"),
        )
        check(card.name.length in 1..80 && card.version == manifest.requiredInt("cardVersion")) { "WRITING_SKILL_CARD_IDENTITY" }
        val instructions = card.rules + card.avoid + card.preferredTerms
        check(instructions.isNotEmpty() && instructions.size <= 8 && instructions.sumOf(String::length) <= 1_600) {
            "WRITING_SKILL_CARD_LIMIT"
        }
        check(card.sha256.matches(SHA256_PATTERN) && qualityCardSha256(card) == card.sha256) {
            "WRITING_SKILL_CARD_HASH_MISMATCH"
        }
        return S0WritingSkillState(
            status = S0WritingSkillStatus.ACTIVE,
            displayName = manifest.requiredString("displayName"),
            format = format,
            sourceSha256 = sourceHash,
            importedAt = manifest.requiredString("importedAt"),
            qualityCard = card,
        )
    }

    private fun projectJson(project: S0Project) = buildJsonObject {
        put("schemaVersion", "1.2"); put("id", project.id); put("title", project.title); put("genre", project.genre)
        put("protagonist", project.protagonist); put("tone", project.tone); put("premise", project.premise)
        put("contentScale", project.contentScale.name); put("plotPace", project.plotPace.name); put("createdAt", project.createdAt)
    }

    private fun stateJson(state: S0StoryState) = buildJsonObject {
        put("schemaVersion", "1.0"); put("revision", state.revision); put("nextChapter", state.nextChapter)
        put("committedChapters", buildJsonArray { state.committedChapters.forEach { add(JsonPrimitive(it)) } })
        put("recentEventKeys", buildJsonArray { state.recentEventKeys.forEach { add(JsonPrimitive(it)) } })
    }

    private fun stateFrom(value: JsonObject) = S0StoryState(
        revision = value.requiredInt("revision"),
        nextChapter = value.requiredInt("nextChapter"),
        committedChapters = value.required("committedChapters").jsonArray.map { it.jsonPrimitive.int },
        recentEventKeys = value["recentEventKeys"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
    )

    private fun planJson(items: List<S0PlanItem>) = buildJsonObject {
        put("schemaVersion", "1.0"); put("items", buildJsonArray { items.forEach { add(planItemJson(it)) } })
    }

    private fun planFrom(array: JsonArray) = array.map { itemElement ->
        val item = itemElement.jsonObject
        S0PlanItem(item.requiredInt("chapter"), item.requiredString("title"), item.requiredString("goal"), item.requiredString("entryState"), item.requiredString("mustChange"), item.requiredString("exitHook"), item.required("involvedEntityIds").jsonArray.map { it.jsonPrimitive.content }, item.required("mustNotRepeatEventKeys").jsonArray.map { it.jsonPrimitive.content })
    }

    private fun planItemJson(item: S0PlanItem) = buildJsonObject {
        put("chapter", item.chapter); put("title", item.title); put("goal", item.goal); put("entryState", item.entryState); put("mustChange", item.mustChange); put("exitHook", item.exitHook)
        put("involvedEntityIds", buildJsonArray { item.involvedEntityIds.forEach { add(JsonPrimitive(it)) } }); put("mustNotRepeatEventKeys", buildJsonArray { item.mustNotRepeatEventKeys.forEach { add(JsonPrimitive(it)) } })
    }

    private fun chapterMetaJson(chapter: S0Chapter) = buildJsonObject {
        put("schemaVersion", "1.0"); put("number", chapter.number); put("title", chapter.title); put("taskId", chapter.taskId); put("state", chapter.state.name)
        put("summary", chapter.summary?.let(::JsonPrimitive) ?: JsonNull); put("commitId", chapter.commitId?.let(::JsonPrimitive) ?: JsonNull); put("incompleteReason", chapter.incompleteReason?.let(::JsonPrimitive) ?: JsonNull); put("proseSha256", sha256(chapter.prose)); put("updatedAt", Instant.now().toString())
    }

    private fun pendingJson(commit: S0PendingCommit) = buildJsonObject {
        put("schemaVersion", "1.0"); put("commitId", commit.commitId); put("projectId", commit.projectId); put("chapter", commit.chapter); put("baseRevision", commit.baseRevision); put("targetRevision", commit.targetRevision)
        put("newState", stateJson(commit.newState)); put("newPlan", planJson(commit.newPlan)); put("events", buildJsonArray { commit.events.forEach { add(eventJson(it)) } }); put("chapterMeta", chapterMetaJson(commit.chapterMeta))
    }

    private fun commitFrom(value: JsonObject): S0PendingCommit {
        val chapterMeta = value.required("chapterMeta").jsonObject
        val chapterNumber = chapterMeta.requiredInt("number")
        val dir = projectDir(value.requiredString("projectId"))
        val prose = File(dir, "chapters/${chapterNumber.toString().padStart(6, '0')}.md").readText()
        val chapter = S0Chapter(chapterNumber, chapterMeta.requiredString("title"), chapterMeta.requiredString("taskId"), prose, S0ChapterState.COMMITTED, chapterMeta.optionalString("summary"), chapterMeta.optionalString("commitId"))
        return S0PendingCommit(value.requiredString("commitId"), value.requiredString("projectId"), value.requiredInt("chapter"), value.requiredInt("baseRevision"), value.requiredInt("targetRevision"), stateFrom(value.required("newState").jsonObject), planFrom(value.required("newPlan").jsonObject.required("items").jsonArray), value.required("events").jsonArray.map { eventFrom(it.jsonObject) }, chapter)
    }

    private fun eventJson(event: S0Event) = buildJsonObject {
        put("eventId", event.eventId); put("commitId", event.commitId); put("chapter", event.chapter); put("eventKey", event.eventKey); put("payload", event.payload)
    }

    private fun eventFrom(value: JsonObject) = S0Event(value.requiredString("eventId"), value.requiredString("commitId"), value.requiredInt("chapter"), value.requiredString("eventKey"), value.requiredString("payload"))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }

    private fun JsonObject.required(key: String): JsonElement = this[key] ?: throw S0StorageException("MISSING_FIELD:$key")
    private fun JsonObject.requiredString(key: String): String = required(key).jsonPrimitive.content
    private fun JsonObject.requiredInt(key: String): Int = required(key).jsonPrimitive.int
    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObjectBuilder.put(key: String, value: String) = put(key, JsonPrimitive(value))
    private fun JsonObjectBuilder.put(key: String, value: Int) = put(key, JsonPrimitive(value))
    private fun JsonArrayBuilder.add(value: String) = add(JsonPrimitive(value))
    private fun JsonArrayBuilder.add(value: Int) = add(JsonPrimitive(value))

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{3,80}")
        const val MAX_SKILL_SOURCE_BYTES = 256L * 1024L
        const val MAX_SKILL_METADATA_BYTES = 16L * 1024L
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        val SKILL_MANIFEST_KEYS = setOf(
            "schemaVersion", "displayName", "sourceFileName", "format", "sourceSha256",
            "cardSha256", "cardVersion", "active", "importedAt",
        )
        val SKILL_CARD_KEYS = setOf(
            "schemaVersion", "name", "version", "scope", "rules", "avoid", "preferredTerms",
        )
    }
}
