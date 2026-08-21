package app.zhijuan.data.s0

import app.zhijuan.core.s0.S3GenerationJob
import app.zhijuan.core.s0.S3GenerationJobStore
import app.zhijuan.core.s0.S3JobPurpose
import app.zhijuan.core.s0.S3JobStage
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FileS3GenerationJobStore(private val projectsRoot: File) : S3GenerationJobStore {
    override fun load(projectId: String): S3GenerationJob? {
        val file = activeFile(projectId)
        if (!file.isFile) return null
        return runCatching {
            val value = Json.parseToJsonElement(file.readText()).jsonObject
            val legacy = setOf(
                "schemaVersion", "jobId", "projectId", "chapter", "purpose", "stage", "requestId", "taskId",
                "commitId", "draftPath", "promptTemplateId", "attempt", "createdAt", "updatedAt", "lastErrorCode",
                "outcomeKnown",
            )
            val schemaVersion = value.getValue("schemaVersion").jsonPrimitive.content
            require(
                (schemaVersion == "1.0" && value.keys == legacy) ||
                    (schemaVersion == "1.1" && value.keys == legacy + "providerProfileId"),
            )
            S3GenerationJob(
                jobId = value.text("jobId"),
                projectId = value.text("projectId"),
                chapter = value.getValue("chapter").jsonPrimitive.int,
                purpose = S3JobPurpose.valueOf(value.text("purpose")),
                stage = S3JobStage.valueOf(value.text("stage")),
                requestId = value.optionalText("requestId"),
                taskId = value.optionalText("taskId"),
                commitId = value.optionalText("commitId"),
                draftPath = value.optionalText("draftPath"),
                promptTemplateId = value.text("promptTemplateId"),
                attempt = value.getValue("attempt").jsonPrimitive.int,
                createdAt = value.text("createdAt"),
                updatedAt = value.text("updatedAt"),
                lastErrorCode = value.optionalText("lastErrorCode"),
                outcomeKnown = value.getValue("outcomeKnown").jsonPrimitive.boolean,
                providerProfileId = value["providerProfileId"]?.jsonPrimitive?.contentOrNull,
            ).also { job ->
                require(job.projectId == projectId)
                require(job.jobId.matches(JOB_ID_PATTERN))
                require(job.chapter >= 0)
                require(job.attempt in 1..3)
                job.draftPath?.let { require(it.matches(DRAFT_PATH_PATTERN)) }
            }
        }.getOrElse { failure ->
            throw S0StorageException("ACTIVE_JOB_CORRUPT:${failure.message.orEmpty()}")
        }
    }

    override fun save(job: S3GenerationJob) {
        require(job.jobId.matches(JOB_ID_PATTERN)) { "JOB_ID_INVALID" }
        require(job.projectId.matches(PROJECT_ID_PATTERN)) { "PROJECT_ID_INVALID" }
        require(job.chapter >= 0) { "CHAPTER_INVALID" }
        require(job.attempt in 1..3) { "ATTEMPT_INVALID" }
        job.draftPath?.let { require(it.matches(DRAFT_PATH_PATTERN)) { "DRAFT_PATH_INVALID" } }
        writeAtomic(activeFile(job.projectId), buildJsonObject {
            put("schemaVersion", JsonPrimitive("1.1"))
            put("jobId", JsonPrimitive(job.jobId))
            put("projectId", JsonPrimitive(job.projectId))
            put("chapter", JsonPrimitive(job.chapter))
            put("purpose", JsonPrimitive(job.purpose.name))
            put("stage", JsonPrimitive(job.stage.name))
            put("requestId", job.requestId?.let(::JsonPrimitive) ?: JsonNull)
            put("taskId", job.taskId?.let(::JsonPrimitive) ?: JsonNull)
            put("commitId", job.commitId?.let(::JsonPrimitive) ?: JsonNull)
            put("draftPath", job.draftPath?.let(::JsonPrimitive) ?: JsonNull)
            put("promptTemplateId", JsonPrimitive(job.promptTemplateId))
            put("attempt", JsonPrimitive(job.attempt))
            put("createdAt", JsonPrimitive(job.createdAt))
            put("updatedAt", JsonPrimitive(job.updatedAt))
            put("lastErrorCode", job.lastErrorCode?.let(::JsonPrimitive) ?: JsonNull)
            put("outcomeKnown", JsonPrimitive(job.outcomeKnown))
            put("providerProfileId", job.providerProfileId?.let(::JsonPrimitive) ?: JsonNull)
        }.toString())
    }

    override fun clear(projectId: String) {
        activeFile(projectId).takeIf(File::isFile)?.delete()
    }

    private fun activeFile(projectId: String) = File(projectsRoot, "$projectId/jobs/active.json")

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (file.isFile) file.copyTo(File(file.parentFile, "${file.name}.bak"), overwrite = true)
        check(temporary.renameTo(file) || (temporary.copyTo(file, overwrite = true).let { temporary.delete(); true })) {
            "ACTIVE_JOB_WRITE_FAILED"
        }
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.optionalText(key: String): String? = getValue(key).jsonPrimitive.contentOrNull

    private companion object {
        val JOB_ID_PATTERN = Regex("^job_[A-Za-z0-9_-]{10,}$")
        val PROJECT_ID_PATTERN = Regex("^[A-Za-z0-9_-]+$")
        val DRAFT_PATH_PATTERN = Regex("^chapters/[0-9]{6}\\.md$")
    }
}
