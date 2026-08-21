package app.zhijuan.data.s0

import app.zhijuan.core.s0.S3GenerationJob
import app.zhijuan.core.s0.S3JobPurpose
import app.zhijuan.core.s0.S3JobStage
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileS3GenerationJobStoreTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `active job is atomic strict and removable without storing prompts`() {
        val store = FileS3GenerationJobStore(root)
        val job = S3GenerationJob(
            jobId = "job_1234567890",
            projectId = "project_s3",
            chapter = 2,
            purpose = S3JobPurpose.SETTLEMENT,
            stage = S3JobStage.SETTLEMENT_REQUEST,
            requestId = "settlement_task_1234567890",
            taskId = "task_1234567890",
            draftPath = "chapters/000002.md",
            promptTemplateId = "chapter-settlement-v1",
            outcomeKnown = false,
        )

        store.save(job)

        assertEquals(job, FileS3GenerationJobStore(root).load("project_s3"))
        val raw = File(root, "project_s3/jobs/active.json").readText()
        assertTrue(!raw.contains("chapter_prose"))
        assertTrue(!raw.contains("Authorization"))
        store.clear("project_s3")
        assertNull(store.load("project_s3"))
    }
}
