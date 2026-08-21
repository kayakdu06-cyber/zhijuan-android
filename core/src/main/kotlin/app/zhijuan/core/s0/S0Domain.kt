package app.zhijuan.core.s0

import java.time.Instant

enum class S0ChapterState {
    PLANNED,
    WRITING,
    READABLE_DRAFT,
    NEEDS_REVIEW,
    COMMITTED,
    PAUSED,
}

enum class S0ContentScale {
    QING_XU,
    AN_YONG,
    CHEN_JIN,
}

enum class S0PlotPace {
    EXPANSIVE,
    BALANCED,
    TIGHT,
}

data class S0Project(
    val id: String,
    val title: String,
    val genre: String,
    val protagonist: String,
    val tone: String,
    val premise: String,
    val createdAt: String = Instant.now().toString(),
    val contentScale: S0ContentScale = S0ContentScale.QING_XU,
    val plotPace: S0PlotPace = S0PlotPace.BALANCED,
)

data class S0PlanItem(
    val chapter: Int,
    val title: String,
    val goal: String,
    val entryState: String,
    val mustChange: String,
    val exitHook: String,
    val involvedEntityIds: List<String> = emptyList(),
    val mustNotRepeatEventKeys: List<String> = emptyList(),
)

data class S0StoryState(
    val revision: Int = 0,
    val nextChapter: Int = 1,
    val committedChapters: List<Int> = emptyList(),
    val recentEventKeys: List<String> = emptyList(),
)

data class S0Chapter(
    val number: Int,
    val title: String,
    val taskId: String,
    val prose: String,
    val state: S0ChapterState,
    val summary: String? = null,
    val commitId: String? = null,
    val incompleteReason: String? = null,
)

enum class S0WritingSkillFormat { MARKDOWN, JSON }

enum class S0WritingSkillStatus { NONE, ACTIVE, DISABLED_CORRUPT }

data class S0WritingQualityCard(
    val name: String,
    val version: Int = 1,
    val rules: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    val preferredTerms: List<String> = emptyList(),
    val sha256: String,
)

data class S0WritingSkillImport(
    val sourceFileName: String,
    val format: S0WritingSkillFormat,
    val sourceText: String,
    val sourceSha256: String,
    val qualityCard: S0WritingQualityCard,
)

data class S0WritingSkillState(
    val status: S0WritingSkillStatus = S0WritingSkillStatus.NONE,
    val displayName: String? = null,
    val format: S0WritingSkillFormat? = null,
    val sourceSha256: String? = null,
    val importedAt: String? = null,
    val qualityCard: S0WritingQualityCard? = null,
    val errorCode: String? = null,
)

data class S0ProjectSnapshot(
    val project: S0Project,
    val storyState: S0StoryState,
    val plan: List<S0PlanItem>,
    val chapters: List<S0Chapter>,
    val writingSkill: S0WritingSkillState = S0WritingSkillState(),
)

data class S0ChapterTask(
    val taskId: String,
    val projectId: String,
    val chapter: Int,
    val baseRevision: Int,
    val title: String,
    val goal: String,
    val previousTail: String,
    val povCharacterId: String = "char_protagonist",
    val allowedEntityIds: List<String> = emptyList(),
    val hardFacts: List<String> = emptyList(),
    val recentSummaries: List<String> = emptyList(),
    val openThreads: List<String> = emptyList(),
    val mustDo: List<String> = emptyList(),
    val mustNotDo: List<String> = emptyList(),
    val recentEventKeys: List<String> = emptyList(),
    val qualityCardId: String = "prose-quality-card-zh-v1",
    val writingQualityCard: S0WritingQualityCard? = null,
    val contentScale: S0ContentScale = S0ContentScale.QING_XU,
    val settlementRepairHint: String? = null,
    val plotPace: S0PlotPace = S0PlotPace.BALANCED,
)

data class S0Settlement(
    val taskId: String,
    val chapter: Int,
    val baseRevision: Int,
    val summary: String,
    val eventKey: String,
    val eventDescription: String,
    val events: List<S0SettlementEvent> = emptyList(),
)

data class S0SettlementEvent(
    val eventId: String,
    val eventKey: String,
    val description: String,
    val participants: List<String> = emptyList(),
    val stateTargets: List<String> = emptyList(),
)

data class S0Event(
    val eventId: String,
    val commitId: String,
    val chapter: Int,
    val eventKey: String,
    val payload: String,
)

data class S0PendingCommit(
    val commitId: String,
    val projectId: String,
    val chapter: Int,
    val baseRevision: Int,
    val targetRevision: Int,
    val newState: S0StoryState,
    val newPlan: List<S0PlanItem>,
    val events: List<S0Event>,
    val chapterMeta: S0Chapter,
)

sealed interface S0GenerationResult {
    data class Committed(val chapter: S0Chapter, val proseCalls: Int, val settlementCalls: Int) : S0GenerationResult
    data class ReadableDraft(val chapter: S0Chapter, val reason: String) : S0GenerationResult
    data class IncompleteDraft(val chapter: S0Chapter, val reason: String) : S0GenerationResult
    data class Rejected(val reason: String) : S0GenerationResult
}

interface S0TextGenerationProvider {
    suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String

    suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement

    fun connectionSummary(): S1ProviderSummary? = null

    fun connectionProfiles(): List<S1ProviderSummary> = listOfNotNull(connectionSummary())

    fun selectConnectionProfile(profileId: String): Result<S1ProviderSummary> =
        Result.failure(UnsupportedOperationException("PROVIDER_PROFILE_SELECTION_UNAVAILABLE"))

    fun deleteConnectionProfile(profileId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("PROVIDER_PROFILE_DELETE_UNAVAILABLE"))

    suspend fun testAndSaveConnection(input: S1ProviderSetupInput): S1ConnectionTestResult =
        S1ConnectionTestResult.Failed(S1ProviderErrors.configurationUnavailable())

    fun cancel(requestId: String): S1CancelResult = S1CancelResult.NOT_ACTIVE
}

interface S0NovelRepository {
    fun createProject(
        project: S0Project,
        plan: List<S0PlanItem>,
        writingSkill: S0WritingSkillImport? = null,
    ): S0ProjectSnapshot

    fun listProjects(): List<S0ProjectSnapshot>

    fun loadProject(projectId: String): S0ProjectSnapshot?

    fun replacePlan(projectId: String, expectedRevision: Int, plan: List<S0PlanItem>): S0ProjectSnapshot =
        error("PLAN_REPLACE_UNSUPPORTED")

    fun deleteProject(projectId: String): Boolean = false

    /**
     * Explicit destructive action used only after the user confirms that any saved draft,
     * active-job checkpoint and pending commit for this project may be discarded together.
     */
    fun discardProject(projectId: String): Boolean = deleteProject(projectId)

    fun saveWritingSkill(projectId: String, writingSkill: S0WritingSkillImport): S0ProjectSnapshot =
        error("WRITING_SKILL_SAVE_UNSUPPORTED")

    fun removeWritingSkill(projectId: String): S0ProjectSnapshot =
        error("WRITING_SKILL_REMOVE_UNSUPPORTED")

    fun saveContentScale(projectId: String, contentScale: S0ContentScale): S0ProjectSnapshot =
        error("CONTENT_SCALE_SAVE_UNSUPPORTED")

    fun savePlotPace(projectId: String, plotPace: S0PlotPace): S0ProjectSnapshot =
        error("PLOT_PACE_SAVE_UNSUPPORTED")

    fun saveReadableDraft(projectId: String, task: S0ChapterTask, prose: String): S0Chapter

    fun saveIncompleteDraft(projectId: String, task: S0ChapterTask, prose: String, reason: String): S0Chapter =
        saveReadableDraft(projectId, task, prose).copy(
            state = S0ChapterState.PAUSED,
            incompleteReason = reason,
        )

    fun writePendingCommit(commit: S0PendingCommit)

    fun applyPendingCommit(commitId: String)

    fun recoverPendingCommits(): List<String>
}
