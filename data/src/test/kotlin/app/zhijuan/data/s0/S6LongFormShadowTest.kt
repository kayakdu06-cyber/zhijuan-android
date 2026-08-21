package app.zhijuan.data.s0

import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0Settlement
import app.zhijuan.core.s0.S0TextGenerationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S6LongFormShadowTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `fifty chapters survive rolling plan refreshes and repeated process restarts`() = runBlocking {
        var repository = FileS0NovelRepository(tempDir)
        val provider = RecordingProvider()
        repository.createProject(
            S0Project(
                id = PROJECT_ID,
                title = "五十章影子长跑",
                genre = "长篇悬疑",
                protagonist = "沈砚",
                tone = "克制",
                premise = "沿一条跨越五十章的线索追查旧档案",
            ),
            planWindow(1),
        )

        repeat(TARGET_CHAPTERS) { index ->
            val before = requireNotNull(repository.loadProject(PROJECT_ID))
            if (before.plan.size <= 2) {
                repository.replacePlan(
                    projectId = PROJECT_ID,
                    expectedRevision = before.storyState.revision,
                    plan = planWindow(before.storyState.nextChapter),
                )
            }

            val result = S0GenerationCoordinator(repository, provider).generateNextChapter(PROJECT_ID)
            assertTrue(result is S0GenerationResult.Committed, "chapter=${index + 1}, result=$result")
            val committed = result as S0GenerationResult.Committed
            assertEquals(1, committed.proseCalls)
            assertEquals(1, committed.settlementCalls)

            if ((index + 1) % 10 == 0) {
                repository = FileS0NovelRepository(tempDir)
                assertTrue(repository.recoverPendingCommits().isEmpty())
                assertEquals(index + 1, repository.loadProject(PROJECT_ID)?.storyState?.revision)
            }
        }

        val restored = FileS0NovelRepository(tempDir).loadProject(PROJECT_ID)!!
        assertEquals(TARGET_CHAPTERS, restored.storyState.revision)
        assertEquals(TARGET_CHAPTERS + 1, restored.storyState.nextChapter)
        assertEquals((1..TARGET_CHAPTERS).toList(), restored.storyState.committedChapters)
        assertEquals(TARGET_CHAPTERS, restored.chapters.size)
        assertTrue(restored.chapters.all { it.state == S0ChapterState.COMMITTED })
        assertEquals(TARGET_CHAPTERS, provider.proseCalls)
        assertEquals(TARGET_CHAPTERS, provider.settlementCalls)
        assertEquals(TARGET_CHAPTERS, File(tempDir, "$PROJECT_ID/events.jsonl").readLines().size)
        assertTrue(File(tempDir, "$PROJECT_ID/commits/pending").listFiles().orEmpty().isEmpty())
    }

    private fun planWindow(startChapter: Int): List<S0PlanItem> = (startChapter until startChapter + 8).map { chapter ->
        S0PlanItem(
            chapter = chapter,
            title = "内部方向$chapter",
            goal = "让第${chapter}章推进唯一的新变化",
            entryState = "承接第${chapter - 1}章",
            mustChange = "完成第${chapter}章变化",
            exitHook = "留下第${chapter + 1}章入口",
        )
    }

    private class RecordingProvider : S0TextGenerationProvider {
        var proseCalls = 0
        var settlementCalls = 0

        override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
            proseCalls += 1
            val prose = "第${task.chapter}章正文完整收束。${task.goal}"
            onChunk(prose)
            return prose
        }

        override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
            settlementCalls += 1
            return S0Settlement(
                taskId = task.taskId,
                chapter = task.chapter,
                baseRevision = task.baseRevision,
                summary = "第${task.chapter}章完成一次不可逆推进，并保留后续承接点。",
                eventKey = "chapter_${task.chapter}_advanced",
                eventDescription = "第${task.chapter}章已推进",
            )
        }
    }

    private companion object {
        const val PROJECT_ID = "project_longform_shadow"
        const val TARGET_CHAPTERS = 50
    }
}
