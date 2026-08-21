package app.zhijuan.reader

import app.zhijuan.core.s0.S0FakeProvider
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0Settlement
import app.zhijuan.core.s0.S0TextGenerationProvider
import app.zhijuan.data.s0.FileS0NovelRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class S0VerticalSliceTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `app-core-data slice creates generates reads and restores after process restart`() = kotlinx.coroutines.runBlocking {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(
            S0Project("project_s0", "灯下回卷", "悬疑", "林岑", "安静克制", "旧车站里出现回卷印记", "2026-08-17T00:00:00Z"),
            (1..8).map { chapter -> S0PlanItem(chapter, "第${chapter}章", "确认线索", "线索未明", "线索前进", "灯下档案") },
        )
        val provider = S0FakeProvider()
        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0")
        assertTrue(result is S0GenerationResult.Committed)
        assertEquals(1, provider.proseCalls)
        assertEquals(1, provider.settlementCalls)

        val restarted = FileS0NovelRepository(tempDir)
        val restored = restarted.loadProject("project_s0")!!
        assertEquals(1, restored.storyState.revision)
        assertEquals(1, restored.storyState.committedChapters.single())
        assertEquals(S0ChapterState.COMMITTED, restored.chapters.single().state)
        assertTrue(restored.chapters.single().prose.isNotBlank())
    }

    @Test
    fun `replayed one-time event keeps second chapter as readable draft without advancing state`() = kotlinx.coroutines.runBlocking {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(
            S0Project("project_s2", "灯下回卷", "悬疑", "林岑", "安静克制", "旧车站里出现回卷印记", "2026-08-17T00:00:00Z"),
            (1..8).map { chapter -> S0PlanItem(chapter, "第${chapter}章", "确认线索", "线索未明", "线索前进", "灯下档案") },
        )
        val first = S0GenerationCoordinator(repository, S0FakeProvider()).generateNextChapter("project_s2")
        assertTrue(first is S0GenerationResult.Committed)

        val replayProvider = object : S0TextGenerationProvider {
            var proseCalls = 0
            var settlementCalls = 0

            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                proseCalls += 1
                return "第二章正文仍然保存为可读草稿。".also(onChunk)
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                return S0Settlement(
                    taskId = task.taskId,
                    chapter = task.chapter,
                    baseRevision = task.baseRevision,
                    summary = "试图重复第一章的一次性事件。",
                    eventKey = "chapter_1_station_clue",
                    eventDescription = "重复旧车站线索",
                )
            }
        }

        val replay = S0GenerationCoordinator(repository, replayProvider).generateNextChapter("project_s2")

        assertTrue(replay is S0GenerationResult.ReadableDraft)
        assertEquals("ONE_TIME_EVENT_REPLAY", (replay as S0GenerationResult.ReadableDraft).reason)
        assertEquals(1, replayProvider.proseCalls)
        assertEquals(1, replayProvider.settlementCalls)
        val restored = FileS0NovelRepository(tempDir).loadProject("project_s2")!!
        assertEquals(1, restored.storyState.revision)
        assertEquals(2, restored.storyState.nextChapter)
        assertEquals(listOf("chapter_1_station_clue"), restored.storyState.recentEventKeys)
        assertEquals(S0ChapterState.READABLE_DRAFT, restored.chapters.single { it.number == 2 }.state)
        assertEquals(1, File(tempDir, "project_s2/events.jsonl").readLines().size)
    }

    @Test
    fun `process death immediately after prose save recovers draft and retries settlement only`() = kotlinx.coroutines.runBlocking {
        val firstRepository = FileS0NovelRepository(tempDir)
        firstRepository.createProject(
            S0Project("project_kill", "灯下回卷", "悬疑", "林岑", "安静克制", "旧车站里出现回卷印记", "2026-08-17T00:00:00Z"),
            (1..8).map { chapter -> S0PlanItem(chapter, "第${chapter}章", "确认线索", "线索未明", "线索前进", "灯下档案") },
        )
        val firstProvider = S0FakeProvider()
        assertThrows(InjectedProcessDeath::class.java) {
            kotlinx.coroutines.runBlocking {
                S0GenerationCoordinator(firstRepository, firstProvider).generateNextChapter("project_kill") { _, stage ->
                    if (stage == app.zhijuan.core.s0.S3JobStage.PROSE_SAVED) throw InjectedProcessDeath()
                }
            }
        }
        assertEquals(1, firstProvider.proseCalls)
        assertEquals(0, firstProvider.settlementCalls)

        val restarted = FileS0NovelRepository(tempDir)
        val draft = restarted.loadProject("project_kill")!!
        assertEquals(0, draft.storyState.revision)
        assertEquals(S0ChapterState.READABLE_DRAFT, draft.chapters.single().state)
        var proseCalls = 0
        var settlementCalls = 0
        val settlementOnlyProvider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                proseCalls += 1
                error("saved prose must be reused")
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                return S0Settlement(
                    task.taskId,
                    task.chapter,
                    task.baseRevision,
                    "进程重启后只重新执行结算，已经安全保存的正文没有再次生成。",
                    "chapter_1_process_recovery",
                    "恢复后完成结算",
                )
            }
        }

        val recovered = S0GenerationCoordinator(restarted, settlementOnlyProvider).retrySettlement("project_kill")

        assertTrue(recovered is S0GenerationResult.Committed)
        assertEquals(0, proseCalls)
        assertEquals(1, settlementCalls)
        assertEquals(1, FileS0NovelRepository(tempDir).loadProject("project_kill")!!.storyState.revision)
    }
}

private class InjectedProcessDeath : RuntimeException()
