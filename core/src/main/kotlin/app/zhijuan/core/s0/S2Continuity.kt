package app.zhijuan.core.s0

/** Builds the bounded, deterministic input for one prose call. */
class S2ContextBuilder(
    private val maximumPreviousTailCharacters: Int = 6_000,
    private val maximumRecentSummaries: Int = 5,
) {
    fun build(snapshot: S0ProjectSnapshot, item: S0PlanItem, taskId: String): S0ChapterTask {
        require(item.chapter == snapshot.storyState.nextChapter) { "CHAPTER_SEQUENCE_INVALID" }
        val previousChapter = snapshot.chapters
            .filter { it.number < item.chapter }
            .maxByOrNull(S0Chapter::number)
        val recentSummaries = snapshot.chapters
            .asSequence()
            .filter { it.number < item.chapter }
            .sortedByDescending(S0Chapter::number)
            .mapNotNull(S0Chapter::summary)
            .take(maximumRecentSummaries)
            .toList()
            .asReversed()
            .map { it.take(1_000) }
        val povCharacterId = item.involvedEntityIds.firstOrNull { it.startsWith("char_") }
            ?: "char_protagonist"

        return S0ChapterTask(
            taskId = taskId,
            projectId = snapshot.project.id,
            chapter = item.chapter,
            baseRevision = snapshot.storyState.revision,
            title = item.title,
            goal = item.goal.take(800),
            previousTail = previousChapter?.prose?.takeLast(maximumPreviousTailCharacters).orEmpty(),
            povCharacterId = povCharacterId,
            allowedEntityIds = (listOf(povCharacterId) + item.involvedEntityIds).distinct(),
            hardFacts = listOf(
                "题材：${snapshot.project.genre}",
                "核心设定：${snapshot.project.premise}",
                "主角：${snapshot.project.protagonist}",
                "基调：${snapshot.project.tone}",
                "进入状态：${item.entryState}",
            ).map { it.take(500) },
            recentSummaries = recentSummaries,
            mustDo = listOf(item.goal, item.mustChange, item.exitHook).map { it.take(500) },
            mustNotDo = item.mustNotRepeatEventKeys.map { "不得重复一次性事件：$it".take(500) },
            recentEventKeys = snapshot.storyState.recentEventKeys.distinct(),
            qualityCardId = snapshot.writingSkill.qualityCard
                ?.takeIf { snapshot.writingSkill.status == S0WritingSkillStatus.ACTIVE }
                ?.let { "project-quality-card-v${it.version}" }
                ?: "prose-quality-card-zh-v1",
            writingQualityCard = snapshot.writingSkill.qualityCard
                ?.takeIf { snapshot.writingSkill.status == S0WritingSkillStatus.ACTIVE },
            contentScale = snapshot.project.contentScale,
            plotPace = snapshot.project.plotPace,
        )
    }
}

enum class S2HardViolation {
    DEAD_CHARACTER_PRESENT,
    POV_NOT_PRESENT,
    ENTITY_NOT_ALLOWED,
    CHAPTER_SEQUENCE_INVALID,
    UNPLANTED_FORESHADOW_PAYOFF,
    UNIQUE_ITEM_CONFLICT,
    UNKNOWN_KNOWLEDGE_USED,
    MUTATION_WITHOUT_EVENT,
    ONE_TIME_EVENT_REPLAY,
}

data class S2CharacterState(
    val id: String,
    val alive: Boolean,
    val knownFactIds: Set<String> = emptySet(),
)

data class S2ItemState(
    val id: String,
    val unique: Boolean,
    val holderCharacterId: String? = null,
    val locationId: String? = null,
)

enum class S2ForeshadowStatus { PLANTED, DEVELOPING, PAID_OFF, CANCELLED }

data class S2ForeshadowState(val id: String, val status: S2ForeshadowStatus = S2ForeshadowStatus.PLANTED)

data class S2ContinuityState(
    val lastCommittedChapter: Int,
    val characters: List<S2CharacterState> = emptyList(),
    val items: List<S2ItemState> = emptyList(),
    val foreshadows: List<S2ForeshadowState> = emptyList(),
    val eventKeys: Set<String> = emptySet(),
)

data class S2ContinuityTask(
    val chapter: Int,
    val povCharacterId: String,
    val allowedEntityIds: Set<String>,
)

sealed interface S2StateValue {
    data class Text(val value: String) : S2StateValue
    data class TextList(val values: List<String>) : S2StateValue
    data class Bool(val value: Boolean) : S2StateValue
    data class Number(val value: Double) : S2StateValue
    data object Null : S2StateValue
}

data class S2Mutation(
    val entityId: String,
    val target: String,
    val before: S2StateValue = S2StateValue.Null,
    val after: S2StateValue = S2StateValue.Null,
)

data class S2ContinuityEvent(
    val eventKey: String,
    val participants: Set<String> = emptySet(),
    val stateTargets: Set<String> = emptySet(),
)

data class S2KnowledgeUse(val characterId: String, val factId: String)

data class S2ForeshadowAction(val operation: String, val id: String)

data class S2ContinuityProposal(
    val presentEntityIds: Set<String>,
    val mentionedOnlyEntityIds: Set<String> = emptySet(),
    val createdEntityIds: Set<String> = emptySet(),
    val usedKnowledge: List<S2KnowledgeUse> = emptyList(),
    val knowledgeGains: List<S2KnowledgeUse> = emptyList(),
    val mutations: List<S2Mutation> = emptyList(),
    val events: List<S2ContinuityEvent> = emptyList(),
    val foreshadowActions: List<S2ForeshadowAction> = emptyList(),
)

/** Pure local validation. It never calls a model or mutates state. */
class S2ContinuityValidator {
    fun validate(
        before: S2ContinuityState,
        task: S2ContinuityTask,
        proposal: S2ContinuityProposal,
    ): Set<S2HardViolation> = buildSet {
        val deadCharacterIds = before.characters.filterNot(S2CharacterState::alive).mapTo(mutableSetOf(), S2CharacterState::id)
        val hasDeadCharacterPresent = proposal.presentEntityIds.any(deadCharacterIds::contains)
        if (hasDeadCharacterPresent) {
            add(S2HardViolation.DEAD_CHARACTER_PRESENT)
        }
        if (!hasDeadCharacterPresent && task.povCharacterId !in proposal.presentEntityIds) {
            add(S2HardViolation.POV_NOT_PRESENT)
        }
        val allowed = task.allowedEntityIds + proposal.createdEntityIds
        if (!hasDeadCharacterPresent && proposal.presentEntityIds.any { it !in allowed }) {
            add(S2HardViolation.ENTITY_NOT_ALLOWED)
        }
        if (task.chapter != before.lastCommittedChapter + 1) {
            add(S2HardViolation.CHAPTER_SEQUENCE_INVALID)
        }

        val openForeshadows = before.foreshadows
            .filter { it.status == S2ForeshadowStatus.PLANTED || it.status == S2ForeshadowStatus.DEVELOPING }
            .mapTo(mutableSetOf(), S2ForeshadowState::id)
        if (proposal.foreshadowActions.any { it.operation == "PAY_OFF" && it.id !in openForeshadows }) {
            add(S2HardViolation.UNPLANTED_FORESHADOW_PAYOFF)
        }

        val conflictingUniqueItemIds = before.items
            .asSequence()
            .filter(S2ItemState::unique)
            .filter { item -> uniqueItemConflicts(item, proposal.mutations) }
            .map(S2ItemState::id)
            .toSet()
        if (conflictingUniqueItemIds.isNotEmpty()) {
            add(S2HardViolation.UNIQUE_ITEM_CONFLICT)
        }

        val knownFacts = before.characters.associate { it.id to it.knownFactIds }
        val gainedKnowledge = proposal.knowledgeGains.mapTo(mutableSetOf()) { it.characterId to it.factId }
        if (proposal.usedKnowledge.any { use ->
                use.factId !in knownFacts[use.characterId].orEmpty() && (use.characterId to use.factId) !in gainedKnowledge
            }
        ) {
            add(S2HardViolation.UNKNOWN_KNOWLEDGE_USED)
        }

        val meaningfulMutations = proposal.mutations.filter {
            it.before != it.after && it.entityId !in conflictingUniqueItemIds
        }
        if (meaningfulMutations.any { mutation ->
                proposal.events.none { event ->
                    mutation.target in event.stateTargets &&
                        (event.participants.isEmpty() || mutation.entityId in event.participants)
                }
            }
        ) {
            add(S2HardViolation.MUTATION_WITHOUT_EVENT)
        }

        if (proposal.events.any { it.eventKey in before.eventKeys }) {
            add(S2HardViolation.ONE_TIME_EVENT_REPLAY)
        }
    }

    private fun uniqueItemConflicts(item: S2ItemState, mutations: List<S2Mutation>): Boolean {
        var holder: S2StateValue = item.holderCharacterId?.let(S2StateValue::Text) ?: S2StateValue.Null
        var location: S2StateValue = item.locationId?.let(S2StateValue::Text) ?: S2StateValue.Null
        mutations.filter { it.entityId == item.id }.forEach { mutation ->
            when (mutation.target) {
                "item.holderCharacterId" -> holder = mutation.after
                "item.locationId" -> location = mutation.after
            }
        }
        if (holder is S2StateValue.TextList && holder.values.distinct().size > 1) return true
        if (location is S2StateValue.TextList && location.values.distinct().size > 1) return true
        return holder !is S2StateValue.Null && location !is S2StateValue.Null
    }
}

object S2PlanWindow {
    fun needsExplicitRefresh(remainingItems: Int): Boolean = remainingItems in 1..2
}
