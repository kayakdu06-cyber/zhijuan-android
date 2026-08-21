package app.zhijuan.core.s0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class S3RecoveryAuditorTest {
    @Test
    fun `readable draft is never mistaken for permission to resend prose`() {
        val repository = RecoveryRepository(S0ChapterState.READABLE_DRAFT)
        val store = MemoryJobStore(job(S3JobStage.SETTLEMENT_REQUEST, outcomeKnown = false))

        val decision = S3RecoveryAuditor(repository, store).audit("project_s3")

        assertEquals(S3RecoveryAction.RETRY_SETTLEMENT, decision.action)
    }

    @Test
    fun `unknown prose request outcome requires confirmation before resend`() {
        val repository = RecoveryRepository(null)
        val store = MemoryJobStore(job(S3JobStage.PROSE_REQUEST, outcomeKnown = false))

        val decision = S3RecoveryAuditor(repository, store).audit("project_s3")

        assertEquals(S3RecoveryAction.CONFIRM_RESEND, decision.action)
    }

    @Test
    fun `completed chapter clears stale active job after recovery audit`() {
        val repository = RecoveryRepository(S0ChapterState.COMMITTED)
        val store = MemoryJobStore(job(S3JobStage.COMMIT))

        val decision = S3RecoveryAuditor(repository, store).audit("project_s3")

        assertEquals(S3RecoveryAction.NONE, decision.action)
        assertNull(store.load("project_s3"))
    }

    @Test
    fun `paused incomplete prose requires explicit prose retry instead of settlement`() {
        val repository = RecoveryRepository(S0ChapterState.PAUSED)
        val store = MemoryJobStore(job(S3JobStage.PROSE_SAVED))

        val decision = S3RecoveryAuditor(repository, store).audit("project_s3")

        assertEquals(S3RecoveryAction.RETRY_PROSE, decision.action)
    }

    @Test
    fun `initial settlement plus two failed explicit retries stops the retry loop`() {
        val repository = RecoveryRepository(S0ChapterState.READABLE_DRAFT)
        val store = MemoryJobStore(
            job(S3JobStage.SETTLEMENT_REQUEST).copy(
                attempt = 3,
                lastErrorCode = "SETTLEMENT_FAILED:SETTLEMENT_SCHEMA_INVALID",
            ),
        )

        val decision = S3RecoveryAuditor(repository, store).audit("project_s3")

        assertEquals(S3RecoveryAction.REVIEW_DRAFT, decision.action)
    }

    private fun job(stage: S3JobStage, outcomeKnown: Boolean = true) = S3GenerationJob(
        jobId = "job_1234567890",
        projectId = "project_s3",
        chapter = 1,
        purpose = S3JobPurpose.PROSE,
        stage = stage,
        promptTemplateId = "chapter-prose-v1",
        outcomeKnown = outcomeKnown,
    )
}

private class MemoryJobStore(private var job: S3GenerationJob?) : S3GenerationJobStore {
    override fun load(projectId: String): S3GenerationJob? = job
    override fun save(job: S3GenerationJob) { this.job = job }
    override fun clear(projectId: String) { job = null }
}

private class RecoveryRepository(chapterState: S0ChapterState?) : S0NovelRepository {
    private val project = S0Project("project_s3", "恢复", "悬疑", "林岑", "克制", "恢复测试")
    private val chapter = chapterState?.let {
        S0Chapter(1, "第一章", "task_1234567890", "已有正文", it)
    }

    override fun createProject(project: S0Project, plan: List<S0PlanItem>, writingSkill: S0WritingSkillImport?) = error("unused")
    override fun listProjects() = listOf(requireNotNull(loadProject("project_s3")))
    override fun loadProject(projectId: String) = S0ProjectSnapshot(
        project,
        S0StoryState(revision = if (chapter?.state == S0ChapterState.COMMITTED) 1 else 0),
        listOf(S0PlanItem(1, "第一章", "确认恢复路径", "未开始", "发生变化", "留下入口")),
        listOfNotNull(chapter),
    )
    override fun saveReadableDraft(projectId: String, task: S0ChapterTask, prose: String) = error("unused")
    override fun writePendingCommit(commit: S0PendingCommit) = error("unused")
    override fun applyPendingCommit(commitId: String) = Unit
    override fun recoverPendingCommits(): List<String> = emptyList()
}
