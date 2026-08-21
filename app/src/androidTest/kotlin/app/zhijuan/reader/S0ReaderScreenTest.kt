package app.zhijuan.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0StoryState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S0ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settlementSummaryRemainsStoredButIsNotRenderedWithProse() {
        val internalSummary = "INTERNAL_SETTLEMENT_SUMMARY_SENTINEL"
        val snapshot = S0ProjectSnapshot(
            project = S0Project(
                id = "reader_test",
                title = "阅读测试",
                genre = "悬疑",
                protagonist = "林岑",
                tone = "安静克制",
                premise = "验证内部结算摘要不进入阅读表面。",
            ),
            storyState = S0StoryState(
                revision = 1,
                nextChapter = 2,
                committedChapters = listOf(1),
            ),
            plan = emptyList(),
            chapters = listOf(
                S0Chapter(
                    number = 1,
                    title = "灯下回卷",
                    taskId = "task_reader_1",
                    prose = "这是读者应该看到的正文。",
                    state = S0ChapterState.COMMITTED,
                    summary = internalSummary,
                    commitId = "commit_reader_1",
                ),
            ),
        )

        assertEquals(internalSummary, snapshot.chapters.single().summary)

        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(
                    modifier = Modifier,
                    snapshot = snapshot,
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("这是读者应该看到的正文。").assertExists()
        compose.onNodeWithText(internalSummary, substring = true).assertDoesNotExist()
        compose.onNodeWithText("结算摘要", substring = true).assertDoesNotExist()
        compose.onNodeWithText("灯下回卷").assertDoesNotExist()
    }

    @Test
    fun directorySelectsSavedChaptersAndPreviousNextStayWithinBounds() {
        val snapshot = S0ProjectSnapshot(
            project = S0Project("reader_directory", "三章测试", "悬疑", "林岑", "克制", "目录切章"),
            storyState = S0StoryState(revision = 3, nextChapter = 4, committedChapters = listOf(1, 2, 3)),
            plan = emptyList(),
            chapters = (1..3).map { number ->
                S0Chapter(
                    number = number,
                    title = "第${number}章",
                    taskId = "task_reader_$number",
                    prose = "这是第${number}章正文。",
                    state = S0ChapterState.COMMITTED,
                    summary = "内部摘要$number",
                    commitId = "commit_reader_$number",
                )
            },
        )

        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(modifier = Modifier, snapshot = snapshot, onBack = {})
            }
        }

        compose.onNodeWithText("这是第3章正文。").assertExists()
        compose.onNodeWithTag("reader-return-creation").assertIsEnabled()
        compose.onNodeWithTag("reader-bottom-directory").performClick()
        compose.onNodeWithTag("reader-directory-sheet").assertExists()
        compose.onNodeWithTag("reader-directory-chapter-1").performClick()
        compose.onNodeWithText("这是第1章正文。").assertExists()
        compose.onNodeWithText("第 1 章").assertExists()
        compose.onNodeWithTag("reader-previous").assertIsNotEnabled()
        compose.onNodeWithTag("reader-next").assertIsEnabled().performClick()
        compose.onNodeWithText("这是第2章正文。").assertExists()
        compose.onNodeWithTag("reader-previous").assertIsEnabled()
        compose.onNodeWithTag("reader-next").assertIsEnabled()
        compose.onNodeWithText("内部摘要1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("内部摘要2", substring = true).assertDoesNotExist()
    }

    @Test
    fun centerTapTogglesReaderChromeWithoutRemovingProse() {
        val snapshot = readerSnapshot(
            id = "reader_chrome",
            chapters = listOf(committedChapter(1, "工具栏测试正文。")),
        )

        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(modifier = Modifier, snapshot = snapshot, onBack = {})
            }
        }

        compose.onNodeWithTag("reader-chrome-top").assertExists()
        compose.onNodeWithTag("reader-chrome-bottom").assertExists()
        compose.onNodeWithTag("reader-content-toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reader-chrome-top").assertDoesNotExist()
        compose.onNodeWithTag("reader-chrome-bottom").assertDoesNotExist()
        compose.onNodeWithText("工具栏测试正文。").assertExists()

        compose.onNodeWithTag("reader-content-toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reader-chrome-top").assertExists()
        compose.onNodeWithTag("reader-chrome-bottom").assertExists()
    }

    @Test
    fun readerTopContinueWritingChoosesSequentialChapterCountWithoutShowingPlanTitles() {
        val base = readerSnapshot(
            id = "reader_continue",
            chapters = listOf(committedChapter(1, "第一章正文。")),
        )
        val snapshot = base.copy(
            plan = (2..4).map { chapter ->
                S0PlanItem(chapter, "内部方向$chapter", "内部目标$chapter", "承接", "变化", "入口")
            },
        )
        var requested = 0
        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(
                    modifier = Modifier,
                    snapshot = snapshot,
                    onBack = {},
                    onContinueWriting = { requested = it },
                )
            }
        }

        compose.onNodeWithTag("reader-continue-writing-top").performClick()
        compose.onNodeWithTag("reader-continue-sheet").assertExists()
        compose.onNodeWithTag("generation-batch-2").performClick().assertIsSelected()
        compose.onNodeWithTag("reader-continue-confirm").performClick()
        compose.runOnIdle { assertEquals(2, requested) }
        compose.onNodeWithText("内部方向2", substring = true).assertDoesNotExist()
        compose.onNodeWithText("内部目标2", substring = true).assertDoesNotExist()
    }

    @Test
    fun upwardDragPastCommittedChapterEndAdvancesToNextChapter() {
        val longProse = (1..120).joinToString("\n") { "第 $it 段用于验证连续阅读滚动。" }
        val snapshot = readerSnapshot(
            id = "reader_continuous",
            chapters = listOf(
                committedChapter(1, longProse),
                committedChapter(2, "自动进入的第二章正文。"),
            ),
        )

        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(
                    modifier = Modifier,
                    snapshot = snapshot,
                    initialPosition = S5ReaderPosition(chapterNumber = 1, scrollOffset = 0),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("reader-end-hint").performScrollTo()
        compose.onNodeWithTag("reader-content-toggle").performTouchInput { swipeUp() }
        compose.waitForIdle()

        compose.onNodeWithText("自动进入的第二章正文。").assertExists()
        compose.onNodeWithText("第 2 章").assertExists()
    }

    @Test
    fun readerRestoresTheSavedChapterBeforeShowingTheLatestChapter() {
        val snapshot = S0ProjectSnapshot(
            project = S0Project("reader_restore", "恢复阅读", "悬疑", "林岑", "克制", "恢复位置"),
            storyState = S0StoryState(revision = 3, nextChapter = 4, committedChapters = listOf(1, 2, 3)),
            plan = emptyList(),
            chapters = (1..3).map { number ->
                S0Chapter(number, "第${number}章", "task_restore_$number", "恢复第${number}章正文。", S0ChapterState.COMMITTED)
            },
        )

        compose.setContent {
            ZhijuanS0Theme {
                ReaderScreen(
                    modifier = Modifier,
                    snapshot = snapshot,
                    initialPosition = S5ReaderPosition(chapterNumber = 2, scrollOffset = 240),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("恢复第2章正文。").assertExists()
        compose.onNodeWithText("恢复第3章正文。").assertDoesNotExist()
    }

    @Test
    fun readerSettingsExposeExplicitPersistentChoicesWithoutTouchingChapterContent() {
        var preferences by mutableStateOf(S3ReaderPreferences())
        compose.setContent {
            ZhijuanS0Theme(preferences.theme) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReaderSettingsSection(preferences = preferences, onChange = { preferences = it })
                }
            }
        }

        compose.onNodeWithTag("reader-font-22").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("reader-line-38").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("reader-theme-dark").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(S3ReaderPreferences(22, 38, S3ReaderTheme.DARK), preferences)
        }
    }

    @Test
    fun libraryExposesImportInEmptyState() {
        var imported = false
        compose.setContent {
            ZhijuanS0Theme {
                LibraryScreen(
                    modifier = Modifier,
                    snapshot = null,
                    onCreate = {},
                    onGenerate = {},
                    onRead = {},
                    onImport = { imported = true },
                )
            }
        }
        compose.onNodeWithTag("library-import").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true, imported) }
    }

    @Test
    fun libraryExposesBackupActionsForExistingProject() {
        var imported = false
        var exported = false
        val snapshot = S0ProjectSnapshot(
            project = S0Project("archive_ui", "备份测试", "悬疑", "林岑", "克制", "验证备份入口"),
            storyState = S0StoryState(),
            plan = emptyList(),
            chapters = emptyList(),
        )
        compose.setContent {
            ZhijuanS0Theme {
                LibraryScreen(
                    modifier = Modifier,
                    snapshot = snapshot,
                    onCreate = {},
                    onGenerate = {},
                    onRead = {},
                    onExport = { exported = true },
                    onImport = { imported = true },
                )
            }
        }
        compose.onNodeWithTag("library-export").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("library-import").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertEquals(true, exported) }
    }

    private fun readerSnapshot(id: String, chapters: List<S0Chapter>): S0ProjectSnapshot = S0ProjectSnapshot(
        project = S0Project(id, "阅读测试", "悬疑", "林岑", "克制", "验证阅读交互"),
        storyState = S0StoryState(
            revision = chapters.size,
            nextChapter = (chapters.maxOfOrNull(S0Chapter::number) ?: 0) + 1,
            committedChapters = chapters.filter { it.state == S0ChapterState.COMMITTED }.map(S0Chapter::number),
        ),
        plan = emptyList(),
        chapters = chapters,
    )

    private fun committedChapter(number: Int, prose: String): S0Chapter = S0Chapter(
        number = number,
        title = "第${number}章",
        taskId = "task_reader_$number",
        prose = prose,
        state = S0ChapterState.COMMITTED,
        summary = "内部摘要$number",
        commitId = "commit_reader_$number",
    )
}
