package app.zhijuan.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0StoryState
import app.zhijuan.core.s0.S0WritingQualityCard
import app.zhijuan.core.s0.S0WritingSkillFormat
import app.zhijuan.core.s0.S0WritingSkillImport
import app.zhijuan.core.s0.S0WritingSkillState
import app.zhijuan.core.s0.S0WritingSkillStatus
import app.zhijuan.core.s0.S3RecoveryAction
import app.zhijuan.core.s0.S1ProviderKind
import app.zhijuan.core.s0.S1ProviderSummary
import app.zhijuan.data.s0.S5WritingSkillParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class S5TextProjectUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createSheetCollectsFiveTextFieldsPreviewsPlanAndConfirmsBeforePersistenceCallback() {
        var confirmed: S5ProjectDraft? = null
        compose.setContent {
            ZhijuanS0Theme {
                S5CreateProjectSheet(onDismiss = {}, onConfirm = { confirmed = it })
            }
        }

        compose.onNodeWithTag("create-title").performTextInput("雾钟")
        compose.onNodeWithTag("create-project-list").performScrollToIndex(6)
        compose.onNodeWithTag("create-genre").performTextInput("悬疑")
        compose.onNodeWithTag("create-project-list").performScrollToIndex(7)
        compose.onNodeWithTag("create-protagonist").performTextInput("沈砚")
        compose.onNodeWithTag("create-project-list").performScrollToIndex(10)
        compose.onNodeWithTag("create-tone").performTextInput("克制")
        compose.onNodeWithTag("create-project-list").performScrollToIndex(12)
        compose.onNodeWithTag("create-content-scale-2").performClick().assertIsSelected()
        compose.onNodeWithText("成年题材完整落笔", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(14)
        compose.onNodeWithTag("create-plot-pace-2").performClick().assertIsSelected()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(15)
        compose.onNodeWithTag("create-premise").performTextInput("无雾之夜钟楼少走一分钟")
        compose.onNodeWithTag("create-project-list").performScrollToIndex(18)
        compose.onNodeWithTag("create-preview").performClick()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(0)
        compose.onNodeWithText("确认小说信息").assertIsDisplayed()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(3)
        compose.onNodeWithTag("create-confirm").performClick()

        compose.runOnIdle {
            assertNotNull(confirmed)
            assertEquals("雾钟", confirmed!!.project.title)
            assertEquals(S0ContentScale.CHEN_JIN, confirmed!!.project.contentScale)
            assertEquals(S0PlotPace.TIGHT, confirmed!!.project.plotPace)
            assertEquals(8, confirmed!!.plan.size)
        }
    }

    @Test
    fun contentScaleSheetShowsOnlyScaleNamesWithoutBehaviorDescriptions() {
        var saved: S0ContentScale? = null
        compose.setContent {
            ZhijuanS0Theme {
                S5ContentScaleSheet(
                    projectTitle = "雾钟",
                    current = S0ContentScale.AN_YONG,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText("清叙").assertExists()
        compose.onNodeWithText("暗涌").assertExists().assertIsSelected()
        compose.onNodeWithText("沉浸").assertExists()
        compose.onNodeWithText("允许清晰的成年亲密情节", substring = true).assertDoesNotExist()
        compose.onNodeWithText("身体、感官", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("content-scale-2").performClick().assertIsSelected()
        compose.onNodeWithTag("content-scale-save").performClick()
        compose.runOnIdle { assertEquals(S0ContentScale.CHEN_JIN, saved) }
    }

    @Test
    fun plotPaceSheetShowsOnlyThreeNamesAndStrongSelection() {
        var saved: S0PlotPace? = null
        compose.setContent {
            ZhijuanS0Theme {
                S5PlotPaceSheet(
                    projectTitle = "雾钟",
                    current = S0PlotPace.BALANCED,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText("舒展").assertExists()
        compose.onNodeWithText("均衡").assertExists().assertIsSelected()
        compose.onNodeWithText("紧凑").assertExists()
        compose.onNodeWithText("不得跳过当前计划项", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("plot-pace-2").performClick().assertIsSelected()
        compose.onNodeWithTag("plot-pace-save").performClick()
        compose.runOnIdle { assertEquals(S0PlotPace.TIGHT, saved) }
    }

    @Test
    fun genreAndTonePresetsHaveStrongSelectedStateAndRemainEditable() {
        compose.setContent {
            ZhijuanS0Theme {
                S5CreateProjectSheet(onDismiss = {}, onConfirm = {})
            }
        }

        compose.onNodeWithTag("create-genre-main-0").performClick().assertIsSelected()
        compose.onNodeWithTag("create-genre-detail-0").performClick().assertIsSelected()
        compose.onNodeWithTag("create-relationship-0").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("create-viewpoint-0").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("create-genre").performScrollTo().assertTextContains("玄幻 / 东方玄幻 / 言情 / 男主")

        compose.onNodeWithTag("create-project-list").performScrollToIndex(10)
        compose.onNodeWithTag("create-tone-preset-0").performClick().assertIsSelected()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(11)
        compose.onNodeWithTag("create-tone").performScrollTo().assertTextContains("克制冷峻")
        compose.onNodeWithTag("create-tone").performTextInput("，但人物关系保留温度")
        compose.onNodeWithTag("create-tone").assertTextContains("克制冷峻，但人物关系保留温度")
    }

    @Test
    fun writingSkillRulesArePreviewedAndExplicitlyConfirmedBeforeCreation() {
        val skill = writingSkill()
        compose.setContent {
            ZhijuanS0Theme {
                S5CreateProjectSheet(
                    onDismiss = {},
                    onConfirm = {},
                    writingSkill = skill,
                )
            }
        }

        compose.onNodeWithTag("create-project-list").performScrollToIndex(16)
        compose.onNodeWithText("创作 Skill（可选）").performClick()
        compose.onNodeWithTag("create-project-list").performScrollToIndex(18)
        compose.onNodeWithTag("writing-skill-rules").assertTextContains("以动作和可见细节推进")
        compose.onNodeWithTag("writing-skill-confirm").performScrollTo().performClick()
        compose.onNodeWithText("已确认：创建后每次正文请求都会应用此质量卡。").assertExists()
    }

    @Test
    fun existingProjectSkillSheetAppliesPreviewedCandidate() {
        val skill = writingSkill()
        var applied: S0WritingSkillImport? = null
        compose.setContent {
            ZhijuanS0Theme {
                S5WritingSkillSheet(
                    projectTitle = "规则测试",
                    current = S0WritingSkillState(),
                    candidate = skill,
                    error = null,
                    onDismiss = {},
                    onChoose = {},
                    onApply = { applied = it },
                    onRemove = {},
                )
            }
        }
        compose.onNodeWithTag("writing-skill-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(skill, applied) }
    }

    @Test
    fun generationKeepsInternalQualityCardAndPlanOutOfTheFrontEnd() {
        val skill = writingSkill()
        val project = snapshot("project_skill_ui", "规则测试").copy(
            writingSkill = S0WritingSkillState(
                status = S0WritingSkillStatus.ACTIVE,
                displayName = skill.qualityCard.name,
                format = skill.format,
                sourceSha256 = skill.sourceSha256,
                importedAt = "2026-08-21T00:00:00Z",
                qualityCard = skill.qualityCard,
            ),
        )
        compose.setContent {
            ZhijuanS0Theme {
                GenerationScreen(
                    modifier = Modifier,
                    snapshot = project,
                    isBusy = false,
                    providerSummary = providerSummary(),
                    onGenerate = {},
                    onRead = {},
                )
            }
        }
        compose.onNodeWithText("动作质量卡", substring = true).assertDoesNotExist()
        compose.onNodeWithText("关键线索", substring = true).assertDoesNotExist()
        compose.onNodeWithText("第 1 章").assertExists()
    }

    @Test
    fun libraryListsProjectsShowsRecoveryAndRequiresDeleteConfirmation() {
        val first = snapshot("project_one", "第一部")
        val second = snapshot("project_two", "第二部")
        var deleted: String? = null
        compose.setContent {
            ZhijuanS0Theme {
                S5LibraryScreen(
                    modifier = Modifier,
                    projects = listOf(first, second),
                    activeProjectId = first.project.id,
                    recoveryActions = mapOf(second.project.id to S3RecoveryAction.RETRY_SETTLEMENT),
                    archiveBusy = false,
                    onCreate = {},
                    onSelect = {},
                    onGenerate = {},
                    onRead = {},
                    onExport = {},
                    onImport = {},
                    onDelete = { deleted = it },
                )
            }
        }

        compose.onNodeWithTag("library-project-project_one").assertExists().assertIsSelected()
        compose.onNodeWithTag("library-project-project_two").performScrollTo().assertExists().assertIsNotSelected()
        compose.onNodeWithText("需要恢复：只重试结算").assertExists()
        compose.onNodeWithTag("library-menu-project_two").performScrollTo().performClick()
        compose.onNodeWithTag("library-menu-popup-project_two").assertIsDisplayed()
        compose.onNodeWithTag("library-delete-project_two").performClick()
        compose.onNodeWithText("删除《第二部》？").assertIsDisplayed()
        compose.onNodeWithText("不会发送新的 API 请求", substring = true).assertExists()
        compose.onNodeWithText("停止任务并删除").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithTag("library-delete-confirm").performClick()
        compose.runOnIdle { assertEquals("project_two", deleted) }
    }

    @Test
    fun committedChapterStillOffersTheNextChapterAndTwoRemainingItemsRequireExplicitRefresh() {
        val base = snapshot("project_generate", "生成测试").copy(
            storyState = S0StoryState(1, 2, listOf(1)),
            chapters = listOf(S0Chapter(1, "第一章", "task_1", "正文", S0ChapterState.COMMITTED)),
            plan = (2..4).map { chapter -> S0PlanItem(chapter, "第$chapter 章", "目标", "承接", "变化", "入口") },
        )
        var current by mutableStateOf(base)
        var requestedCount = 0
        compose.setContent {
            ZhijuanS0Theme {
                GenerationScreen(
                    modifier = Modifier,
                    snapshot = current,
                    isBusy = false,
                    providerSummary = providerSummary(),
                    onGenerate = { requestedCount = it },
                    onRead = {},
                )
            }
        }

        compose.onNodeWithText("续写第 2 章").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("generation-batch-1").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("generation-batch-3").performClick().assertIsSelected()
        compose.onNodeWithText("本次最多 6 次模型调用", substring = true).assertExists()
        compose.onNodeWithText("顺序续写 3 章").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3, requestedCount) }
        compose.runOnIdle { current = current.copy(plan = current.plan.take(2)) }
        compose.onNodeWithTag("plan-refresh").assertIsDisplayed()
        compose.onNodeWithText("续写第 2 章").assertDoesNotExist()
    }

    @Test
    fun incompleteChapterShowsCauseAndRequiresExplicitProseRegenerationInsteadOfSettlement() {
        val incomplete = snapshot("project_incomplete", "截断测试").copy(
            chapters = listOf(
                S0Chapter(
                    1,
                    "第一章",
                    "task_incomplete",
                    "尚未结束的正文片段",
                    S0ChapterState.PAUSED,
                    incompleteReason = "PROSE_TRUNCATED_LENGTH",
                ),
            ),
        )
        var generateCalls = 0
        var settlementCalls = 0
        compose.setContent {
            ZhijuanS0Theme {
                GenerationScreen(
                    modifier = Modifier,
                    snapshot = incomplete,
                    isBusy = false,
                    providerSummary = providerSummary(),
                    onGenerate = { generateCalls += 1 },
                    onRetrySettlement = { settlementCalls += 1 },
                    onRead = {},
                )
            }
        }

        compose.onNodeWithText("正文达到输出上限", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("generation-retry-incomplete").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, generateCalls)
            assertEquals(0, settlementCalls)
        }
        compose.onNodeWithText("只重试结算").assertDoesNotExist()
    }

    private fun snapshot(id: String, title: String) = S0ProjectSnapshot(
        project = S0Project(id, title, "悬疑", "主角", "克制", "核心设定"),
        storyState = S0StoryState(),
        plan = (1..8).map { chapter ->
            S0PlanItem(chapter, "第$chapter 章", "目标", "承接", "变化", "入口")
        },
        chapters = emptyList(),
    )

    private fun writingSkill() = S5WritingSkillParser().parse(
        "动作质量卡.md",
        ByteArrayInputStream("## 规则\n- 以动作和可见细节推进".toByteArray()),
    )

    private fun providerSummary() = S1ProviderSummary(
        providerId = "provider_test",
        baseUrl = "https://api.example.com/v1",
        normalizedChatCompletionsUrl = "https://api.example.com/v1/chat/completions",
        model = "model-test",
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 180,
        totalTimeoutSeconds = 300,
        maxProseCharacters = 12_000,
        lastConnectionTestAt = "2026-08-21T00:00:00Z",
        displayName = "测试 API",
        kind = S1ProviderKind.OPENAI_COMPATIBLE,
    )
}
