package app.zhijuan.data.s0.provider

import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0WritingQualityCard
import app.zhijuan.core.s0.S1CancelResult
import app.zhijuan.core.s0.S1ConnectionTestResult
import app.zhijuan.core.s0.S1ProviderErrorCode
import app.zhijuan.core.s0.S1ProviderException
import app.zhijuan.core.s0.S1ProviderKind
import app.zhijuan.core.s0.S1ProviderSetupInput
import app.zhijuan.core.s0.S1RequestIds
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiCompatibleS1ProviderTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterEach
    fun closeServers() {
        servers.forEach(MockWebServer::close)
    }

    @Test
    fun `legacy single profile migrates to version two without losing its active settings`() {
        val directory = Files.createTempDirectory("zhijuan-s1-migration").toFile()
        val settingsFile = File(directory, "provider-settings.json")
        settingsFile.writeText(
            """{"schemaVersion":"1.0","providerId":"provider_main","baseUrl":"https://api.deepseek.com","normalizedChatCompletionsUrl":"https://api.deepseek.com/chat/completions","model":"deepseek-v4-pro","credentialAlias":"legacy_alias","connectTimeoutSeconds":20,"readTimeoutSeconds":200,"totalTimeoutSeconds":360,"maxProseCharacters":14000,"lastConnectionTestAt":"2026-08-20T00:00:00Z"}""",
            Charsets.UTF_8,
        )
        val store = S1ProviderSettingsStore(settingsFile)

        val legacy = requireNotNull(store.load())
        assertEquals("provider_main", legacy.providerId)
        assertEquals("DeepSeek V4 Pro", legacy.displayName)
        assertEquals(S1ProviderKind.DEEPSEEK, legacy.kind)

        store.save(legacy)
        val migrated = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        assertEquals("2.0", migrated.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("provider_main", migrated.getValue("activeProfileId").jsonPrimitive.content)
        assertEquals(1, migrated.getValue("profiles").jsonArray.size)
    }

    @Test
    fun `multiple profiles switch safely and a running job lock prevents profile mutation`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"id":"first","choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(jsonResponse("""{"id":"second","choices":[{"message":{"content":"OK"}}]}"""))
        val fixture = fixture()

        fixture.provider.testAndSaveConnection(
            input(server, displayName = "主力 DeepSeek", kind = S1ProviderKind.DEEPSEEK),
        )
        val first = requireNotNull(fixture.provider.connectionSummary())
        fixture.provider.testAndSaveConnection(
            input(
                server,
                key = "SECOND_KEY_12345".toCharArray(),
                model = "qwen3-max",
                displayName = "备用 Qwen",
                kind = S1ProviderKind.QWEN,
            ),
        )
        val second = requireNotNull(fixture.provider.connectionSummary())

        assertEquals(2, fixture.provider.connectionProfiles().size)
        assertEquals(first.providerId, fixture.provider.selectConnectionProfile(first.providerId).getOrThrow().providerId)
        fixture.provider.lockProfile(first.providerId)
        assertTrue(fixture.provider.selectConnectionProfile(second.providerId).isFailure)
        assertTrue(fixture.provider.deleteConnectionProfile(first.providerId).isFailure)
        fixture.provider.unlockProfile()
        fixture.provider.deleteConnectionProfile(first.providerId).getOrThrow()
        assertEquals(listOf(second.providerId), fixture.provider.connectionProfiles().map { it.providerId })
        assertEquals(second.providerId, fixture.provider.connectionSummary()?.providerId)
        assertFalse(fixture.directory.readAllText().contains("SECOND_KEY_12345"))
    }

    @Test
    fun `editing a profile can reuse its stored key without creating a duplicate`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"id":"create","choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(jsonResponse("""{"id":"edit","choices":[{"message":{"content":"OK"}}]}"""))
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server, displayName = "原配置"))
        val created = requireNotNull(fixture.provider.connectionSummary())

        val edited = fixture.provider.testAndSaveConnection(
            input(
                server,
                key = CharArray(0),
                model = "model-edited",
                profileId = created.providerId,
                displayName = "改名后的配置",
            ),
        ) as S1ConnectionTestResult.Saved

        assertEquals(created.providerId, edited.summary.providerId)
        assertEquals("改名后的配置", edited.summary.displayName)
        assertEquals("model-edited", edited.summary.model)
        assertEquals(1, fixture.provider.connectionProfiles().size)
    }

    @Test
    fun `connection test saves only non secret settings and clears caller key`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"id":"remote-1","choices":[{"message":{"content":"OK"}}]}"""))
        val fixture = fixture()
        val key = TEST_KEY.toCharArray()

        val result = fixture.provider.testAndSaveConnection(input(server, key))

        assertTrue(result is S1ConnectionTestResult.Saved)
        assertTrue(key.all { it == '\u0000' })
        assertEquals("test-model", fixture.provider.connectionSummary()?.model)
        assertFalse(fixture.directory.readAllText().contains(TEST_KEY))
        val request = server.takeRequest()
        assertEquals("Bearer $TEST_KEY", request.headers["Authorization"])
        assertEquals("/v1/chat/completions", request.target)
    }

    @Test
    fun `one prose stream and one settlement call satisfy the same provider contract`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"id":"connection","choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .chunkedBody(
                    ": heartbeat\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"第一段\"}}]}\n\n" +
                        "data: {\"id\":\"remote-prose\",\"choices\":[{\"delta\":{\"content\":\"。第二段\"},\"finish_reason\":\"stop\"}]}\n\n" +
                        "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}\n\n" +
                        "data: [DONE]\n\n",
                    1,
                )
                .build(),
        )
        server.enqueue(
            jsonResponse(
                settlementEnvelope("task_1", 1, 0, "chapter_1_clue", "remote-settlement"),
            ),
        )
        val fixture = fixture()
        assertTrue(fixture.provider.testAndSaveConnection(input(server, model = "deepseek-v4-pro")) is S1ConnectionTestResult.Saved)
        val task = task().copy(contentScale = S0ContentScale.CHEN_JIN, plotPace = S0PlotPace.TIGHT)
        val chunks = mutableListOf<String>()

        val prose = fixture.provider.streamProse(task, chunks::add)
        val settlement = fixture.provider.completeSettlement(task, prose)

        assertEquals("第一段。第二段", prose)
        assertEquals(listOf("第一段", "。第二段"), chunks)
        assertEquals("林岑在旧车站确认了第一条线索，并决定沿着灯下档案继续调查。", settlement.summary)
        assertEquals(3, server.requestCount)
        val connectionRequest = server.takeRequest()
        val proseRequest = server.takeRequest()
        val settlementRequest = server.takeRequest()
        val connectionRequestBody = Json.parseToJsonElement(requireNotNull(connectionRequest.body).utf8()).jsonObject
        val proseRequestBody = Json.parseToJsonElement(requireNotNull(proseRequest.body).utf8()).jsonObject
        val settlementRequestBody = Json.parseToJsonElement(requireNotNull(settlementRequest.body).utf8()).jsonObject
        assertThinkingDisabled(connectionRequestBody)
        assertTrue(proseRequestBody.getValue("stream").jsonPrimitive.boolean)
        assertEquals(1_333, proseRequestBody.getValue("max_tokens").jsonPrimitive.int)
        val proseSystemPrompt = proseRequestBody.getValue("messages").jsonArray.first().jsonObject
            .getValue("content").jsonPrimitive.content
        assertTrue(proseSystemPrompt.contains("800 到 1000 个中文字符"))
        assertTrue(proseSystemPrompt.contains("不要输出分析"))
        assertTrue(proseSystemPrompt.contains("不得再次演出 recentEventKeys"))
        val proseUserPrompt = proseRequestBody.getValue("messages").jsonArray[1].jsonObject
            .getValue("content").jsonPrimitive.content
        assertTrue(proseUserPrompt.contains("\"recentSummaries\":[\"上一章确认线索\"]"))
        assertTrue(proseUserPrompt.contains("\"recentEventKeys\":[\"found:first_mark\"]"))
        assertTrue(proseUserPrompt.contains("\"qualityCardId\":\"prose-quality-card-zh-v1\""))
        assertTrue(proseUserPrompt.contains("<content_scale>"))
        assertTrue(proseUserPrompt.contains("\"name\":\"沉浸\""))
        assertTrue(proseUserPrompt.contains("身体与感官连续性"))
        assertTrue(proseUserPrompt.contains("强迫或非自愿关系"))
        assertTrue(proseUserPrompt.contains("不得用淡出、概括、跳时"))
        assertTrue(proseUserPrompt.contains("刺激、进入、体位、节奏、力度"))
        assertTrue(proseUserPrompt.contains("不得只用亲吻、拥抱、喘息"))
        assertTrue(proseUserPrompt.contains("<plot_pace>"))
        assertTrue(proseUserPrompt.contains("\"name\":\"紧凑\""))
        assertTrue(proseUserPrompt.contains("不得跳过当前计划项"))
        assertTrue(proseUserPrompt.contains("紧凑不等于跳跃"))
        assertFalse(proseUserPrompt.contains("人物卡"))
        assertTrue(proseUserPrompt.contains("<previous_tail>\n雨停在旧站"))
        assertThinkingDisabled(proseRequestBody)
        assertFalse(settlementRequestBody.getValue("stream").jsonPrimitive.boolean)
        assertThinkingDisabled(settlementRequestBody)
        assertEquals("Bearer $TEST_KEY", proseRequest.headers["Authorization"])
        val diagnostics = File(fixture.directory, "provider-diagnostics.jsonl").readText()
        assertTrue(diagnostics.contains("\"inputTokens\":12"))
        assertTrue(diagnostics.contains("\"outputTokens\":10"))
        assertFalse(diagnostics.contains(TEST_KEY))
    }

    @Test
    fun `confirmed project quality card enters only prose and records hash without leaking rules`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"id":"connection","choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .body(
                    "data: {\"id\":\"skill-prose\",\"choices\":[{\"delta\":{\"content\":\"正文完成。\"},\"finish_reason\":\"stop\"}]}\n\n" +
                        "data: [DONE]\n\n",
                )
                .build(),
        )
        server.enqueue(jsonResponse(settlementEnvelope("task_1", 1, 0, "skill_event", "skill-settlement")))
        val fixture = fixture()
        assertTrue(fixture.provider.testAndSaveConnection(input(server)) is S1ConnectionTestResult.Saved)
        val ruleSentinel = "通过可见动作表现犹豫"
        val hash = "a".repeat(64)
        val card = S0WritingQualityCard(
            name = "克制动作卡",
            rules = listOf(ruleSentinel),
            avoid = listOf("解释性总结"),
            sha256 = hash,
        )
        val task = task().copy(
            qualityCardId = "project-quality-card-v1",
            writingQualityCard = card,
        )

        val prose = fixture.provider.streamProse(task) { }
        fixture.provider.completeSettlement(task, prose)

        server.takeRequest()
        val proseBody = Json.parseToJsonElement(requireNotNull(server.takeRequest().body).utf8()).jsonObject
        val settlementBody = Json.parseToJsonElement(requireNotNull(server.takeRequest().body).utf8()).jsonObject
        val prosePrompt = proseBody.getValue("messages").jsonArray.joinToString { it.jsonObject.getValue("content").jsonPrimitive.content }
        val settlementPrompt = settlementBody.getValue("messages").jsonArray.joinToString { it.jsonObject.getValue("content").jsonPrimitive.content }
        assertTrue(prosePrompt.contains(ruleSentinel))
        assertTrue(prosePrompt.contains(hash))
        assertTrue(prosePrompt.contains("Provider 与应用安全约束"))
        assertFalse(settlementPrompt.contains(ruleSentinel))
        assertFalse(settlementPrompt.contains(hash))

        val diagnostics = File(fixture.directory, "provider-diagnostics.jsonl").readText()
        assertTrue(diagnostics.contains("\"qualityCardName\":\"克制动作卡\""))
        assertTrue(diagnostics.contains("\"qualityCardVersion\":1"))
        assertTrue(diagnostics.contains("\"qualityCardSha256\":\"$hash\""))
        assertFalse(diagnostics.contains(ruleSentinel))
    }

    @Test
    fun `provider body and key never escape a safe authentication failure`() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .addHeader("Content-Type", "application/json")
                .body("""{"error":{"message":"$REMOTE_CANARY"}}""")
                .build(),
        )
        val fixture = fixture()

        val result = fixture.provider.testAndSaveConnection(input(server)) as S1ConnectionTestResult.Failed

        assertEquals(S1ProviderErrorCode.AUTH_REJECTED, result.failure.code)
        assertFalse(result.toString().contains(REMOTE_CANARY))
        assertFalse(result.toString().contains(TEST_KEY))
        assertFalse(fixture.directory.readAllText().contains(TEST_KEY))
        assertFalse(fixture.directory.readAllText().contains(REMOTE_CANARY))
    }

    @Test
    fun `http status and content type map to stable safe connection errors`() = runBlocking {
        val server = server()
        server.enqueue(MockResponse.Builder().code(404).body(REMOTE_CANARY).build())
        server.enqueue(MockResponse.Builder().code(429).body(REMOTE_CANARY).build())
        server.enqueue(MockResponse.Builder().code(503).body(REMOTE_CANARY).build())
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/html")
                .body("<$REMOTE_CANARY>").build(),
        )
        val fixture = fixture()

        val results = List(4) {
            fixture.provider.testAndSaveConnection(input(server)) as S1ConnectionTestResult.Failed
        }

        assertEquals(
            listOf(
                S1ProviderErrorCode.MODEL_UNAVAILABLE,
                S1ProviderErrorCode.PROVIDER_RATE_LIMIT,
                S1ProviderErrorCode.PROVIDER_SERVER_ERROR,
                S1ProviderErrorCode.MODEL_UNAVAILABLE,
            ),
            results.map { it.failure.code },
        )
        assertTrue(results.none { it.toString().contains(REMOTE_CANARY) })
        assertFalse(fixture.directory.readAllText().contains(REMOTE_CANARY))
    }

    @Test
    fun `unfinished stream and character overflow never become readable success`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream")
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"半章\"}}]}\n\n")
                .build(),
        )
        val oversized = "字".repeat(1_001)
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream")
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"$oversized\"}}]}\n\ndata: [DONE]\n\n")
                .build(),
        )
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val unfinished = runCatching { fixture.provider.streamProse(task().copy(taskId = "unfinished")) { } }
            .exceptionOrNull() as S1ProviderException
        val overflow = runCatching { fixture.provider.streamProse(task().copy(taskId = "overflow")) { } }
            .exceptionOrNull() as S1ProviderException

        assertEquals(S1ProviderErrorCode.REQUEST_OUTCOME_UNKNOWN, unfinished.failure.code)
        assertEquals(S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED, overflow.failure.code)
    }

    @Test
    fun `only stop is a complete prose finish reason and every partial reason stays diagnostic`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        val reasons = listOf(
            "length" to S1ProviderErrorCode.PROSE_TRUNCATED_LENGTH,
            "content_filter" to S1ProviderErrorCode.PROSE_CONTENT_FILTERED,
            "insufficient_system_resource" to S1ProviderErrorCode.PROSE_RESOURCE_INTERRUPTED,
            "tool_calls" to S1ProviderErrorCode.PROSE_FINISH_REASON_UNKNOWN,
        )
        reasons.forEach { (reason, _) ->
            server.enqueue(
                MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream")
                    .body(
                        "data: {\"id\":\"remote-$reason\",\"choices\":[{\"delta\":{\"content\":\"已收到的片段\"},\"finish_reason\":\"$reason\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20}}\n\n" +
                            "data: [DONE]\n\n",
                    )
                    .build(),
            )
        }
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val actual = reasons.mapIndexed { index, _ ->
            val failure = runCatching {
                fixture.provider.streamProse(task().copy(taskId = "finish-reason-$index")) { }
            }.exceptionOrNull() as S1ProviderException
            failure.failure.code
        }

        assertEquals(reasons.map { it.second }, actual)
        val diagnostics = File(fixture.directory, "provider-diagnostics.jsonl").readLines().takeLast(reasons.size)
        reasons.zip(diagnostics).forEach { (expected, line) ->
            val json = Json.parseToJsonElement(line).jsonObject
            assertEquals(expected.first, json.getValue("finishReason").jsonPrimitive.content)
            assertEquals(expected.second.name, json.getValue("errorCode").jsonPrimitive.content)
            assertEquals(20, json.getValue("outputTokens").jsonPrimitive.int)
        }
    }

    @Test
    fun `large reasoning stream does not consume the prose character limit`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        val reasoning = "推".repeat(50_000)
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"$reasoning\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"正文完成。\"},\"finish_reason\":\"stop\"}]}\n\n" +
                        "data: [DONE]\n\n",
                )
                .build(),
        )
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val prose = fixture.provider.streamProse(task().copy(taskId = "reasoning-stream")) { }

        assertEquals("正文完成。", prose)
        val diagnostic = File(fixture.directory, "provider-diagnostics.jsonl").readLines().last()
        assertTrue(Json.parseToJsonElement(diagnostic).jsonObject.getValue("responseBytes").jsonPrimitive.int > 100_000)
    }

    @Test
    fun `non json settlement remains a readable draft failure`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"not-json"}}]}"""))
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val failure = runCatching { fixture.provider.completeSettlement(task(), "已保存正文") }
            .exceptionOrNull() as S1ProviderException

        assertEquals(S1ProviderErrorCode.SETTLEMENT_NOT_JSON, failure.failure.code)
    }

    @Test
    fun `authoritative settlement fixture is accepted and all event keys survive parsing`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        val fixtureJson = requireNotNull(javaClass.getResource("/settlement-valid.json")).readText()
        server.enqueue(jsonResponse(settlementEnvelopeFromContent(fixtureJson, "authoritative-settlement")))
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val settlement = fixture.provider.completeSettlement(
            task().copy(
                taskId = "task_01HVALID0001",
                chapter = 2,
                baseRevision = 1,
                settlementRepairHint = "SETTLEMENT_SCHEMA_INVALID:ROOT_FIELDS",
            ),
            "林砚把铜钥按进苏禾掌心，示意她立刻收好。",
        )

        assertEquals("transfer:item_key001:char_lin001:char_su0001", settlement.eventKey)
        assertEquals(listOf("transfer:item_key001:char_lin001:char_su0001"), settlement.events.map { it.eventKey })
        assertEquals(listOf("item.holderCharacterId"), settlement.events.single().stateTargets)
        server.takeRequest()
        val repairRequest = Json.parseToJsonElement(requireNotNull(server.takeRequest().body).utf8()).jsonObject
        val repairPrompt = repairRequest.getValue("messages").jsonArray.joinToString { it.jsonObject.getValue("content").jsonPrimitive.content }
        assertTrue(repairPrompt.contains("SETTLEMENT_SCHEMA_INVALID:ROOT_FIELDS"))
        assertTrue(repairPrompt.contains("用户明确触发的结算修复重试"))
    }

    @Test
    fun `single json code fence is normalized without accepting extra prose`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        val fixtureJson = requireNotNull(javaClass.getResource("/settlement-valid.json")).readText()
        server.enqueue(jsonResponse(settlementEnvelopeFromContent("```json\n$fixtureJson\n```", "fenced-settlement")))
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val settlement = fixture.provider.completeSettlement(
            task().copy(taskId = "task_01HVALID0001", chapter = 2, baseRevision = 1),
            "林砚把铜钥按进苏禾掌心，示意她立刻收好。",
        )

        assertEquals("transfer:item_key001:char_lin001:char_su0001", settlement.eventKey)
    }

    @Test
    fun `legacy six-key settlement is rejected as schema invalid`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(
            jsonResponse(
                settlementEnvelopeFromContent(
                    """{"taskId":"task_1","chapter":1,"baseRevision":0,"summary":"旧摘要","eventKey":"old","eventDescription":"旧事件"}""",
                    "legacy-settlement",
                ),
            ),
        )
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))

        val failure = runCatching { fixture.provider.completeSettlement(task(), "已保存正文") }
            .exceptionOrNull() as S1ProviderException

        assertEquals(S1ProviderErrorCode.SETTLEMENT_SCHEMA_INVALID, failure.failure.code)
    }

    @Test
    fun `cancel closes the active prose socket and maps user cancelled`() = runBlocking {
        val server = server()
        server.enqueue(jsonResponse("""{"choices":[{"message":{"content":"OK"}}]}"""))
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .headersDelay(10, TimeUnit.SECONDS)
                .body("data: [DONE]\n\n")
                .build(),
        )
        val fixture = fixture()
        fixture.provider.testAndSaveConnection(input(server))
        val task = task()
        val result = async { runCatching { fixture.provider.streamProse(task) { } }.exceptionOrNull() }
        repeat(200) {
            if (server.requestCount >= 2) return@repeat
            delay(10)
        }

        assertEquals(S1CancelResult.CANCEL_REQUESTED, fixture.provider.cancel(S1RequestIds.prose(task.taskId)))
        val failure = result.await() as S1ProviderException
        assertEquals(S1ProviderErrorCode.USER_CANCELLED, failure.failure.code)
        assertEquals(S1CancelResult.NOT_ACTIVE, fixture.provider.cancel(S1RequestIds.prose(task.taskId)))
    }

    @Test
    fun `real adapter crosses coordinator and file repository with exactly two chapter posts`() = runBlocking {
        val server = server()
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (calls.getAndIncrement()) {
                0 -> jsonResponse("""{"id":"connection","choices":[{"message":{"content":"OK"}}]}""")
                1 -> MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(
                        "data: {\"id\":\"prose\",\"choices\":[{\"delta\":{\"content\":\"雨停后，林岑在灯下确认了第一条线索。\"},\"finish_reason\":\"stop\"}]}\n\n" +
                            "data: [DONE]\n\n",
                    )
                    .build()
                else -> settlementFor(request)
            }
        }
        val fixture = fixture()
        val repository = app.zhijuan.data.s0.FileS0NovelRepository(File(fixture.directory, "novels"))
        repository.createProject(
            S0Project("project_s1", "灯下回卷", "悬疑", "林岑", "克制", "寻找印记"),
            (1..8).map { chapter ->
                S0PlanItem(chapter, "第${chapter}章", "推进线索", "未确认", "确认", "下一线索")
            },
        )
        assertTrue(fixture.provider.testAndSaveConnection(input(server)) is S1ConnectionTestResult.Saved)

        val result = S0GenerationCoordinator(repository, fixture.provider).generateNextChapter("project_s1")

        assertTrue(result is S0GenerationResult.Committed)
        assertEquals(3, server.requestCount, "one explicit connection test plus two normal chapter calls")
        val restarted = app.zhijuan.data.s0.FileS0NovelRepository(File(fixture.directory, "novels"))
        val chapter = restarted.loadProject("project_s1")!!.chapters.single()
        assertEquals(S0ChapterState.COMMITTED, chapter.state)
        assertTrue(chapter.prose.contains("第一条线索"))
    }

    private fun fixture(): Fixture {
        val directory = Files.createTempDirectory("zhijuan-s1-provider").toFile()
        return Fixture(
            directory,
            OpenAiCompatibleS1Provider(
                settingsStore = S1ProviderSettingsStore(File(directory, "provider-settings.json")),
                secretStore = S1InMemoryProviderSecretStore(),
                diagnosticSink = S1ProviderDiagnosticSink(File(directory, "provider-diagnostics.jsonl")),
                allowHttpForLocalTests = true,
            ),
        )
    }

    private fun input(
        server: MockWebServer,
        key: CharArray = TEST_KEY.toCharArray(),
        model: String = "test-model",
        profileId: String? = null,
        displayName: String = "DeepSeek V4 Pro",
        kind: S1ProviderKind = S1ProviderKind.DEEPSEEK,
    ) = S1ProviderSetupInput(
        baseUrl = server.url("/v1").toString(),
        apiKey = key,
        model = model,
        connectTimeoutSeconds = 5,
        readTimeoutSeconds = 30,
        totalTimeoutSeconds = 60,
        maxProseCharacters = 1_000,
        profileId = profileId,
        displayName = displayName,
        kind = kind,
    )

    private fun task() = S0ChapterTask(
        taskId = "task_1",
        projectId = "project_s1",
        chapter = 1,
        baseRevision = 0,
        title = "第一章",
        goal = "找到线索",
        previousTail = "雨停在旧站",
        povCharacterId = "char_lincen",
        allowedEntityIds = listOf("char_lincen", "location_station"),
        hardFacts = listOf("林岑尚未找到手稿"),
        recentSummaries = listOf("上一章确认线索"),
        openThreads = listOf("回卷印记来源"),
        mustDo = listOf("确认第一条线索"),
        mustNotDo = listOf("不得重复发现第一枚印记"),
        recentEventKeys = listOf("found:first_mark"),
    )

    private fun server(): MockWebServer = MockWebServer().also {
        it.start()
        servers += it
    }

    private fun assertThinkingDisabled(body: kotlinx.serialization.json.JsonObject) {
        assertEquals("disabled", body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun settlementFor(request: RecordedRequest): MockResponse {
        val body = Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject
        val userContent = body.getValue("messages").jsonArray[1].jsonObject
            .getValue("content").jsonPrimitive.content
        val taskId = requireNotNull(Regex("\\\"taskId\\\":\\\"([^\\\"]+)\\\"").find(userContent)?.groupValues?.get(1))
        val chapter = requireNotNull(Regex("\\\"chapter\\\":(\\d+)").find(userContent)?.groupValues?.get(1)).toInt()
        val baseRevision = requireNotNull(Regex("\\\"baseRevision\\\":(\\d+)").find(userContent)?.groupValues?.get(1)).toInt()
        return jsonResponse(settlementEnvelope(taskId, chapter, baseRevision, "chapter_${chapter}_clue", "settlement-$chapter"))
    }

    private fun settlementEnvelope(
        taskId: String,
        chapter: Int,
        baseRevision: Int,
        eventKey: String,
        remoteRequestId: String,
    ): String {
        val settlement = buildJsonObject {
            put("schemaVersion", "1.0")
            put("taskId", taskId)
            put("chapter", chapter)
            put("baseRevision", baseRevision)
            put("summary", "林岑在旧车站确认了第一条线索，并决定沿着灯下档案继续调查。")
            put("goalOutcome", buildJsonObject {
                put("status", "ACHIEVED")
                put("evidence", evidence(0, "林岑把带有回卷印记的纸页收进书里。"))
            })
            put("events", buildJsonArray {
                add(buildJsonObject {
                    put("eventId", "event_${chapter.toString().padStart(10, '0')}")
                    put("eventKey", eventKey)
                    put("participants", buildJsonArray { add(JsonPrimitive("char_lincen")) })
                    put("action", "CONFIRM_CLUE")
                    put("before", "线索未确认")
                    put("after", "线索已确认")
                    put("result", "第一条线索已确认。")
                    put("stateTargets", buildJsonArray { add(JsonPrimitive("fact.active")) })
                    put("evidence", evidence(0, "林岑把带有回卷印记的纸页收进书里。"))
                })
            })
            put("entityCreates", buildJsonArray { })
            put("mutations", buildJsonArray { })
            put("foreshadowActions", buildJsonArray { })
            put("openTaskActions", buildJsonArray { })
            put("continuationHook", "林岑将继续追查灯下档案。")
        }
        return settlementEnvelopeFromContent(settlement.toString(), remoteRequestId)
    }

    private fun settlementEnvelopeFromContent(content: String, remoteRequestId: String): String = buildJsonObject {
            put("id", remoteRequestId)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("message", buildJsonObject { put("content", content) })
                    put("finish_reason", "stop")
                })
            })
            put("usage", buildJsonObject {
                put("prompt_tokens", 20)
                put("completion_tokens", 10)
            })
        }.toString()

    private fun evidence(paragraphIndex: Int, excerpt: String) = buildJsonObject {
        put("paragraphIndex", paragraphIndex)
        put("excerpt", excerpt)
    }

    private fun File.readAllText(): String = walkTopDown()
        .filter(File::isFile)
        .joinToString("\n") { it.readText(Charsets.UTF_8) }

    private data class Fixture(val directory: File, val provider: OpenAiCompatibleS1Provider)

    private companion object {
        const val TEST_KEY = "ZHIJUAN_TEST_KEY_001"
        const val REMOTE_CANARY = "REMOTE_PROVIDER_SECRET_BODY"
    }
}
