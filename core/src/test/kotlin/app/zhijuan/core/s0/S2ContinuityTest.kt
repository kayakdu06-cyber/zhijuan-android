package app.zhijuan.core.s0

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S2ContinuityTest {
    @Test
    fun `all authoritative continuity fixtures produce exactly the declared hard violations`() {
        val fixture = Json.parseToJsonElement(
            requireNotNull(javaClass.getResource("/continuity-cases.json")).readText(),
        ).jsonObject
        val cases = fixture.getValue("cases").jsonArray

        assertEquals(9, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val expected = case.getValue("expectedHardViolations").jsonArray
                .map { S2HardViolation.valueOf(it.jsonPrimitive.content) }
                .toSet()

            assertEquals(
                expected,
                S2ContinuityValidator().validate(
                    before = parseBefore(case.getValue("before").jsonObject),
                    task = parseTask(case.getValue("task").jsonObject),
                    proposal = parseProposal(case.getValue("proposal").jsonObject),
                ),
                case.getValue("id").jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `context builder keeps only bounded recent continuity context`() {
        val chapters = (1..7).map { chapter ->
            S0Chapter(
                number = chapter,
                title = "第${chapter}章",
                taskId = "task_$chapter",
                prose = "旧正文-$chapter-" + "字".repeat(7_000),
                state = S0ChapterState.COMMITTED,
                summary = "摘要-$chapter-" + "摘".repeat(1_200),
            )
        }
        val item = S0PlanItem(
            chapter = 8,
            title = "回声",
            goal = "查明回声来源",
            entryState = "林岑进入地窖",
            mustChange = "确认声音来自墙后",
            exitHook = "墙上出现新门",
            involvedEntityIds = listOf("char_lincen", "location_cellar"),
            mustNotRepeatEventKeys = listOf("opened:first_door"),
        )
        val snapshot = S0ProjectSnapshot(
            project = S0Project("project_1", "回卷", "悬疑", "林岑", "克制", "寻找失踪手稿")
                .copy(contentScale = S0ContentScale.CHEN_JIN, plotPace = S0PlotPace.TIGHT),
            storyState = S0StoryState(
                revision = 7,
                nextChapter = 8,
                committedChapters = (1..7).toList(),
                recentEventKeys = listOf("found:first_mark", "opened:first_door"),
            ),
            plan = listOf(item),
            chapters = chapters,
        )

        val task = S2ContextBuilder().build(snapshot, item, "task_8")

        assertEquals(6_000, task.previousTail.length)
        assertFalse(task.previousTail.contains("旧正文-7-"))
        assertEquals(listOf(3, 4, 5, 6, 7), task.recentSummaries.map { it.substringAfter("摘要-").substringBefore("-").toInt() })
        assertTrue(task.recentSummaries.all { it.length <= 1_000 })
        assertEquals("char_lincen", task.povCharacterId)
        assertEquals(listOf("char_lincen", "location_cellar"), task.allowedEntityIds)
        assertEquals(listOf("found:first_mark", "opened:first_door"), task.recentEventKeys)
        assertTrue(task.hardFacts.contains("题材：悬疑"))
        assertTrue(task.hardFacts.contains("基调：克制"))
        assertTrue(task.mustDo.contains("查明回声来源"))
        assertEquals(listOf("不得重复一次性事件：opened:first_door"), task.mustNotDo)
        assertEquals(S0ContentScale.CHEN_JIN, task.contentScale)
        assertEquals(S0PlotPace.TIGHT, task.plotPace)
    }

    @Test
    fun `plan refresh is explicit only when one or two items remain`() {
        assertTrue(S2PlanWindow.needsExplicitRefresh(1))
        assertTrue(S2PlanWindow.needsExplicitRefresh(2))
        assertFalse(S2PlanWindow.needsExplicitRefresh(0))
        assertFalse(S2PlanWindow.needsExplicitRefresh(3))
    }

    @Test
    fun `unique item cannot be settled to two holders and input authority remains immutable`() {
        val before = S2ContinuityState(
            lastCommittedChapter = 1,
            items = listOf(S2ItemState("item_seal", unique = true, holderCharacterId = "char_a")),
        )
        val proposal = S2ContinuityProposal(
            presentEntityIds = setOf("char_protagonist"),
            mutations = listOf(
                S2Mutation(
                    entityId = "item_seal",
                    target = "item.holderCharacterId",
                    before = S2StateValue.Text("char_a"),
                    after = S2StateValue.TextList(listOf("char_a", "char_b")),
                ),
            ),
            events = listOf(
                S2ContinuityEvent(
                    eventKey = "item_conflict_attempt",
                    participants = setOf("item_seal"),
                    stateTargets = setOf("item.holderCharacterId"),
                ),
            ),
        )

        val violations = S2ContinuityValidator().validate(
            before,
            S2ContinuityTask(2, "char_protagonist", setOf("char_protagonist", "item_seal")),
            proposal,
        )

        assertTrue(S2HardViolation.UNIQUE_ITEM_CONFLICT in violations)
        assertEquals("char_a", before.items.single().holderCharacterId)
    }

    private fun parseBefore(value: JsonObject): S2ContinuityState = S2ContinuityState(
        lastCommittedChapter = value.getValue("lastCommittedChapter").jsonPrimitive.int,
        characters = value.array("characters").map { item ->
            val character = item.jsonObject
            S2CharacterState(
                id = character.string("id"),
                alive = character.getValue("alive").jsonPrimitive.booleanOrNull ?: false,
                knownFactIds = character.stringSet("knownFactIds"),
            )
        },
        items = value.array("items").map { item ->
            val state = item.jsonObject
            S2ItemState(
                id = state.string("id"),
                unique = state.getValue("unique").jsonPrimitive.booleanOrNull ?: false,
                holderCharacterId = state.optionalString("holderCharacterId"),
                locationId = state.optionalString("locationId"),
            )
        },
        foreshadows = value.array("foreshadows").map { item ->
            val state = item.jsonObject
            S2ForeshadowState(
                id = state.string("id"),
                status = state.optionalString("status")?.let(S2ForeshadowStatus::valueOf)
                    ?: S2ForeshadowStatus.PLANTED,
            )
        },
        eventKeys = value.stringSet("eventKeys"),
    )

    private fun parseTask(value: JsonObject): S2ContinuityTask = S2ContinuityTask(
        chapter = value.getValue("chapter").jsonPrimitive.int,
        povCharacterId = value.string("povCharacterId"),
        allowedEntityIds = value.stringSet("allowedEntityIds"),
    )

    private fun parseProposal(value: JsonObject): S2ContinuityProposal = S2ContinuityProposal(
        presentEntityIds = value.stringSet("presentEntityIds"),
        mentionedOnlyEntityIds = value.stringSet("mentionedOnlyEntityIds"),
        createdEntityIds = value.stringSet("createdEntityIds"),
        usedKnowledge = value.array("usedKnowledge").map(::parseKnowledge),
        knowledgeGains = value.array("knowledgeGains").map(::parseKnowledge),
        mutations = value.array("mutations").map { item ->
            val mutation = item.jsonObject
            S2Mutation(
                entityId = mutation.string("entityId"),
                target = mutation.string("target"),
                before = stateValue(mutation["before"]),
                after = stateValue(mutation["after"]),
            )
        },
        events = value.array("events").map { item ->
            val event = item.jsonObject
            S2ContinuityEvent(
                eventKey = event.string("eventKey"),
                participants = event.stringSet("participants"),
                stateTargets = event.stringSet("stateTargets"),
            )
        },
        foreshadowActions = value.array("foreshadowActions").map { item ->
            val action = item.jsonObject
            S2ForeshadowAction(action.string("operation"), action.string("id"))
        },
    )

    private fun parseKnowledge(element: JsonElement): S2KnowledgeUse = element.jsonObject.let {
        S2KnowledgeUse(it.string("characterId"), it.string("factId"))
    }

    private fun stateValue(element: JsonElement?): S2StateValue = when (element) {
        null, JsonNull -> S2StateValue.Null
        is JsonArray -> S2StateValue.TextList(element.map { it.jsonPrimitive.content })
        is JsonPrimitive -> when {
            element.isString -> S2StateValue.Text(element.content)
            element.booleanOrNull != null -> S2StateValue.Bool(requireNotNull(element.booleanOrNull))
            element.doubleOrNull != null -> S2StateValue.Number(requireNotNull(element.doubleOrNull))
            else -> S2StateValue.Text(element.content)
        }
        else -> S2StateValue.Text(element.toString())
    }

    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringSet(key: String): Set<String> = array(key).mapTo(linkedSetOf()) {
        it.jsonPrimitive.content
    }
}
