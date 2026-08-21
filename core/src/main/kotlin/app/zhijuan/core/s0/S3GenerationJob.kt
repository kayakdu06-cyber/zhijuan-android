package app.zhijuan.core.s0

import java.time.Instant

enum class S3JobPurpose { PROSE, SETTLEMENT, SETTLEMENT_REPAIR, BOOTSTRAP, PLAN_REFRESH }

enum class S3JobStage {
    PREPARE,
    PROSE_REQUEST,
    PROSE_SAVED,
    SETTLEMENT_REQUEST,
    VALIDATE,
    COMMIT,
    DONE,
}

data class S3GenerationJob(
    val jobId: String,
    val projectId: String,
    val chapter: Int,
    val purpose: S3JobPurpose,
    val stage: S3JobStage,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = createdAt,
    val attempt: Int = 1,
    val promptTemplateId: String,
    val requestId: String? = null,
    val taskId: String? = null,
    val commitId: String? = null,
    val draftPath: String? = null,
    val lastErrorCode: String? = null,
    val outcomeKnown: Boolean = true,
    val providerProfileId: String? = null,
)

interface S3GenerationJobStore {
    fun load(projectId: String): S3GenerationJob?

    fun save(job: S3GenerationJob)

    fun clear(projectId: String)
}

enum class S3RecoveryAction {
    NONE,
    RETRY_PROSE,
    RETRY_SETTLEMENT,
    CONFIRM_RESEND,
    REVIEW_DRAFT,
}

data class S3RecoveryDecision(
    val action: S3RecoveryAction,
    val job: S3GenerationJob? = null,
    val recoveredCommitIds: List<String> = emptyList(),
)

class S3RecoveryAuditor(
    private val repository: S0NovelRepository,
    private val jobStore: S3GenerationJobStore,
) {
    fun audit(projectId: String): S3RecoveryDecision {
        val recovered = repository.recoverPendingCommits()
        val job = jobStore.load(projectId)
            ?: return S3RecoveryDecision(S3RecoveryAction.NONE, recoveredCommitIds = recovered)
        val snapshot = repository.loadProject(projectId)
            ?: return S3RecoveryDecision(S3RecoveryAction.CONFIRM_RESEND, job, recovered)
        val chapter = snapshot.chapters.firstOrNull { it.number == job.chapter }
        if (chapter?.state == S0ChapterState.COMMITTED || job.stage == S3JobStage.DONE) {
            jobStore.clear(projectId)
            return S3RecoveryDecision(S3RecoveryAction.NONE, recoveredCommitIds = recovered)
        }
        if (chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW) {
            val settlementRetriesExhausted = job.attempt >= MAX_SETTLEMENT_ATTEMPTS &&
                job.lastErrorCode?.contains("SETTLEMENT") == true
            return S3RecoveryDecision(
                if (settlementRetriesExhausted) S3RecoveryAction.REVIEW_DRAFT else S3RecoveryAction.RETRY_SETTLEMENT,
                job,
                recovered,
            )
        }
        if (chapter?.state == S0ChapterState.PAUSED) {
            return S3RecoveryDecision(S3RecoveryAction.RETRY_PROSE, job, recovered)
        }
        if (!job.outcomeKnown && job.stage in setOf(S3JobStage.PROSE_REQUEST, S3JobStage.SETTLEMENT_REQUEST)) {
            return S3RecoveryDecision(S3RecoveryAction.CONFIRM_RESEND, job, recovered)
        }
        return S3RecoveryDecision(
            action = if (job.stage == S3JobStage.PROSE_SAVED) S3RecoveryAction.REVIEW_DRAFT else S3RecoveryAction.RETRY_PROSE,
            job = job,
            recoveredCommitIds = recovered,
        )
    }

    private companion object {
        const val MAX_SETTLEMENT_ATTEMPTS = 3
    }
}
