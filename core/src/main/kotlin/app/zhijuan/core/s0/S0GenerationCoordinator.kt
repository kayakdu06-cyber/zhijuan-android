package app.zhijuan.core.s0

import java.util.UUID

class S0ChapterRoute(
    private val repository: S0NovelRepository,
    private val contextBuilder: S2ContextBuilder = S2ContextBuilder(),
) {
    fun permit(projectId: String): Result<S0ChapterTask> = runCatching {
        val snapshot = repository.loadProject(projectId) ?: error("PROJECT_NOT_FOUND")
        val item = snapshot.plan.firstOrNull { it.chapter == snapshot.storyState.nextChapter }
            ?: error("PLAN_EXHAUSTED")
        check(item.chapter == snapshot.storyState.nextChapter) { "CHAPTER_SEQUENCE_INVALID" }
        if (item.chapter > 1) {
            check(item.chapter - 1 in snapshot.storyState.committedChapters) { "PREVIOUS_CHAPTER_NOT_COMMITTED" }
        }
        val existing = snapshot.chapters.firstOrNull { it.number == item.chapter }
        check(existing == null || existing.state == S0ChapterState.PAUSED) {
            when (existing?.state) {
                S0ChapterState.COMMITTED -> "CHAPTER_ALREADY_COMMITTED"
                else -> "EXISTING_DRAFT_REQUIRES_SETTLEMENT"
            }
        }
        contextBuilder.build(
            snapshot = snapshot,
            item = item,
            taskId = "task_${UUID.randomUUID().toString().replace("-", "").take(16)}",
        )
    }

    fun permitSettlement(projectId: String): Result<Pair<S0ChapterTask, S0Chapter>> = runCatching {
        val snapshot = repository.loadProject(projectId) ?: error("PROJECT_NOT_FOUND")
        val item = snapshot.plan.firstOrNull { it.chapter == snapshot.storyState.nextChapter }
            ?: error("PLAN_EXHAUSTED")
        val draft = snapshot.chapters.firstOrNull { it.number == item.chapter }
            ?: error("READABLE_DRAFT_NOT_FOUND")
        check(draft.state == S0ChapterState.READABLE_DRAFT || draft.state == S0ChapterState.NEEDS_REVIEW) {
            "READABLE_DRAFT_NOT_FOUND"
        }
        contextBuilder.build(snapshot, item, draft.taskId) to draft
    }
}

class S0GenerationCoordinator(
    private val repository: S0NovelRepository,
    private val provider: S0TextGenerationProvider,
    private val route: S0ChapterRoute = S0ChapterRoute(repository),
) {
    suspend fun generateNextChapter(
        projectId: String,
        checkpoint: (S0ChapterTask, S3JobStage) -> Unit = { _, _ -> },
    ): S0GenerationResult {
        val task = route.permit(projectId).getOrElse { return S0GenerationResult.Rejected(it.message.orEmpty()) }
        checkpoint(task, S3JobStage.PREPARE)
        checkpoint(task, S3JobStage.PROSE_REQUEST)
        val streamedProse = StringBuilder()
        val prose = try {
            provider.streamProse(task) { chunk -> streamedProse.append(chunk) }
        } catch (failure: Throwable) {
            val incompleteCode = (failure as? S1ProviderException)?.failure?.code
                ?.takeIf { it in INCOMPLETE_PROSE_CODES }
            if (incompleteCode != null && streamedProse.isNotBlank()) {
                val draft = repository.saveIncompleteDraft(
                    projectId = projectId,
                    task = task,
                    prose = streamedProse.toString(),
                    reason = incompleteCode.name,
                )
                checkpoint(task, S3JobStage.PROSE_SAVED)
                return S0GenerationResult.IncompleteDraft(draft, incompleteCode.name)
            }
            return S0GenerationResult.Rejected("PROSE_FAILED:${failure.message.orEmpty()}")
        }
        if (prose.isBlank()) return S0GenerationResult.Rejected("PROSE_EMPTY")
        val draft = repository.saveReadableDraft(projectId, task, prose)
        checkpoint(task, S3JobStage.PROSE_SAVED)
        return settleAndCommit(projectId, task, draft, prose, proseCalls = 1, checkpoint = checkpoint)
    }

    suspend fun retrySettlement(
        projectId: String,
        settlementRepairHint: String? = null,
        checkpoint: (S0ChapterTask, S3JobStage) -> Unit = { _, _ -> },
    ): S0GenerationResult {
        val (task, draft) = route.permitSettlement(projectId)
            .getOrElse { return S0GenerationResult.Rejected(it.message.orEmpty()) }
        val repairTask = task.copy(settlementRepairHint = settlementRepairHint?.take(240))
        checkpoint(repairTask, S3JobStage.PROSE_SAVED)
        return settleAndCommit(projectId, repairTask, draft, draft.prose, proseCalls = 0, checkpoint = checkpoint)
    }

    private suspend fun settleAndCommit(
        projectId: String,
        task: S0ChapterTask,
        draft: S0Chapter,
        prose: String,
        proseCalls: Int,
        checkpoint: (S0ChapterTask, S3JobStage) -> Unit,
    ): S0GenerationResult {
        checkpoint(task, S3JobStage.SETTLEMENT_REQUEST)
        val settlement = try {
            provider.completeSettlement(task, prose)
        } catch (failure: Throwable) {
            return S0GenerationResult.ReadableDraft(draft, "SETTLEMENT_FAILED:${failure.message.orEmpty()}")
        }
        if (settlement.taskId != task.taskId || settlement.chapter != task.chapter || settlement.baseRevision != task.baseRevision) {
            return S0GenerationResult.ReadableDraft(draft, "SETTLEMENT_CONTRACT_INVALID")
        }
        checkpoint(task, S3JobStage.VALIDATE)
        val snapshot = repository.loadProject(projectId)
            ?: return S0GenerationResult.ReadableDraft(draft, "PROJECT_NOT_FOUND_AFTER_DRAFT")
        val settlementEvents = settlement.events.ifEmpty {
            listOf(
                S0SettlementEvent(
                    eventId = "event_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    eventKey = settlement.eventKey,
                    description = settlement.eventDescription,
                ),
            )
        }
        if (settlementEvents.any { it.eventKey in snapshot.storyState.recentEventKeys }) {
            return S0GenerationResult.ReadableDraft(draft, S2HardViolation.ONE_TIME_EVENT_REPLAY.name)
        }
        val plan = snapshot.plan.filterNot { it.chapter == task.chapter }
        val nextState = S0StoryState(
            revision = task.baseRevision + 1,
            nextChapter = task.chapter + 1,
            committedChapters = (snapshot.storyState.committedChapters + task.chapter).distinct().sorted(),
            recentEventKeys = (snapshot.storyState.recentEventKeys + settlementEvents.map(S0SettlementEvent::eventKey))
                .distinct()
                .takeLast(100),
        )
        val commitId = "commit_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val events = settlementEvents.mapIndexed { index, event ->
            S0Event(
                // The model's eventId is validated as response shape only. Durable identity is
                // generated locally so a model cannot accidentally reuse an ID from an older chapter.
                eventId = "event_${commitId.removePrefix("commit_")}_${index + 1}",
                commitId = commitId,
                chapter = task.chapter,
                eventKey = event.eventKey,
                payload = event.description,
            )
        }
        val committedChapter = draft.copy(
            state = S0ChapterState.COMMITTED,
            summary = settlement.summary,
            commitId = commitId,
            incompleteReason = null,
        )
        checkpoint(task, S3JobStage.COMMIT)
        repository.writePendingCommit(
            S0PendingCommit(
                commitId = commitId,
                projectId = projectId,
                chapter = task.chapter,
                baseRevision = task.baseRevision,
                targetRevision = nextState.revision,
                newState = nextState,
                newPlan = plan,
                events = events,
                chapterMeta = committedChapter,
            ),
        )
        repository.applyPendingCommit(commitId)
        checkpoint(task, S3JobStage.DONE)
        return S0GenerationResult.Committed(committedChapter, proseCalls = proseCalls, settlementCalls = 1)
    }

    private companion object {
        val INCOMPLETE_PROSE_CODES = setOf(
            S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED,
            S1ProviderErrorCode.PROSE_TRUNCATED_LENGTH,
            S1ProviderErrorCode.PROSE_CONTENT_FILTERED,
            S1ProviderErrorCode.PROSE_RESOURCE_INTERRUPTED,
            S1ProviderErrorCode.PROSE_FINISH_REASON_UNKNOWN,
            S1ProviderErrorCode.REQUEST_OUTCOME_UNKNOWN,
        )
    }
}
