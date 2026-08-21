package app.zhijuan.data.s0

import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0Event
import app.zhijuan.core.s0.S0PendingCommit
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0StoryState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class S0DataPersistenceTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `explicit project discard removes recovery artifacts without touching another project`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        repository.createProject(project().copy(id = "project_keep", title = "保留项目"), plan())
        File(tempDir, "project_s0/jobs").mkdirs()
        File(tempDir, "project_s0/jobs/active.json").writeText("{}")
        File(tempDir, "project_s0/commits/pending").mkdirs()
        File(tempDir, "project_s0/commits/pending/commit_discard_100000.json").writeText("{}")

        assertThrows(IllegalStateException::class.java) { repository.deleteProject("project_s0") }
        assertTrue(repository.discardProject("project_s0"))

        assertTrue(repository.loadProject("project_s0") == null)
        assertEquals("保留项目", repository.loadProject("project_keep")!!.project.title)
    }

    @Test
    fun `incomplete draft reason survives repository restart and cannot look committed`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        val task = app.zhijuan.core.s0.S0ChapterTask("task_incomplete_123", "project_s0", 1, 0, "第一章", "确认线索", "")

        repository.saveIncompleteDraft(
            "project_s0",
            task,
            "在句子中途停止的正文",
            "PROSE_TRUNCATED_LENGTH",
        )

        val restored = FileS0NovelRepository(tempDir).loadProject("project_s0")!!.chapters.single()
        assertEquals(S0ChapterState.PAUSED, restored.state)
        assertEquals("PROSE_TRUNCATED_LENGTH", restored.incompleteReason)
        assertEquals(null, restored.commitId)
        assertEquals("在句子中途停止的正文", restored.prose)
    }

    @Test
    fun `draft and pending commit survive a new repository instance and commit is idempotent`() {
        val first = FileS0NovelRepository(tempDir)
        first.createProject(project(), plan())
        val task = app.zhijuan.core.s0.S0ChapterTask("task_1234567890", "project_s0", 1, 0, "第一章", "确认线索", "")
        first.saveReadableDraft("project_s0", task, "这是可读草稿。")
        val pending = S0PendingCommit(
            commitId = "commit_1234567890",
            projectId = "project_s0",
            chapter = 1,
            baseRevision = 0,
            targetRevision = 1,
            newState = S0StoryState(revision = 1, nextChapter = 2, committedChapters = listOf(1)),
            newPlan = plan().drop(1),
            events = listOf(S0Event("event_1234567890", "commit_1234567890", 1, "station_clue", "获得线索")),
            chapterMeta = first.loadProject("project_s0")!!.chapters.single().copy(
                state = S0ChapterState.COMMITTED,
                summary = "已确认线索",
                commitId = "commit_1234567890",
            ),
        )
        first.writePendingCommit(pending)

        val restarted = FileS0NovelRepository(tempDir)
        assertEquals(S0ChapterState.READABLE_DRAFT, restarted.loadProject("project_s0")!!.chapters.single().state)
        restarted.applyPendingCommit("commit_1234567890")
        restarted.applyPendingCommit("commit_1234567890")

        val finalSnapshot = FileS0NovelRepository(tempDir).loadProject("project_s0")!!
        assertEquals(1, finalSnapshot.storyState.revision)
        assertEquals(S0ChapterState.COMMITTED, finalSnapshot.chapters.single().state)
        assertEquals(1, File(tempDir, "project_s0/events.jsonl").readLines().size)
        assertTrue(File(tempDir, "project_s0/commits/completed/commit_1234567890.json").isFile)
        assertTrue(!File(tempDir, "project_s0/commits/pending/commit_1234567890.json").exists())
    }

    @Test
    fun `state written before recent event keys remains readable`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        File(tempDir, "project_s0/state.json").writeText(
            """{"schemaVersion":"1.0","revision":0,"nextChapter":1,"committedChapters":[]}""",
        )

        val restored = FileS0NovelRepository(tempDir).loadProject("project_s0")!!

        assertEquals(emptyList<String>(), restored.storyState.recentEventKeys)
        assertEquals(1, restored.storyState.nextChapter)
    }

    @Test
    fun `legacy project defaults to clear scale and balanced pace while selections survive restart`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        File(tempDir, "project_s0/project.json").writeText(
            """{"schemaVersion":"1.0","id":"project_s0","title":"灯下回卷","genre":"悬疑","protagonist":"林岑","tone":"安静克制","premise":"旧车站里出现回卷印记","createdAt":"2026-08-20T00:00:00Z"}""",
        )

        assertEquals(S0ContentScale.QING_XU, FileS0NovelRepository(tempDir).loadProject("project_s0")!!.project.contentScale)
        assertEquals(S0PlotPace.BALANCED, FileS0NovelRepository(tempDir).loadProject("project_s0")!!.project.plotPace)

        repository.saveContentScale("project_s0", S0ContentScale.CHEN_JIN)
        repository.savePlotPace("project_s0", S0PlotPace.TIGHT)
        val restored = FileS0NovelRepository(tempDir).loadProject("project_s0")!!.project
        assertEquals(S0ContentScale.CHEN_JIN, restored.contentScale)
        assertEquals(S0PlotPace.TIGHT, restored.plotPace)
        assertTrue(File(tempDir, "project_s0/project.json").readText().contains("\"schemaVersion\":\"1.2\""))
        assertTrue(File(tempDir, "project_s0/project.json").readText().contains("\"contentScale\":\"CHEN_JIN\""))
        assertTrue(File(tempDir, "project_s0/project.json").readText().contains("\"plotPace\":\"TIGHT\""))
    }

    @Test
    fun `project bootstrap is collision safe plan replacement is revision guarded and delete is scoped`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        repository.createProject(project().copy(id = "project_keep", title = "保留项目"), plan())

        val duplicate = assertThrows(IllegalStateException::class.java) {
            repository.createProject(project().copy(title = "不应覆盖"), plan())
        }
        assertEquals("PROJECT_ALREADY_EXISTS", duplicate.message)
        assertEquals("灯下回卷", repository.loadProject("project_s0")!!.project.title)

        val replacement = (1..8).map { offset ->
            val chapter = offset
            S0PlanItem(chapter, "新计划$chapter", "推进新目标$chapter", "承接", "发生变化", "留下入口")
        }
        assertThrows(IllegalStateException::class.java) { repository.replacePlan("project_s0", 1, replacement) }
        assertEquals("新计划1", repository.replacePlan("project_s0", 0, replacement).plan.first().title)

        File(tempDir, "project_s0/jobs").mkdirs()
        File(tempDir, "project_s0/jobs/active.json").writeText("{}")
        assertThrows(IllegalStateException::class.java) { repository.deleteProject("project_s0") }
        File(tempDir, "project_s0/jobs/active.json").delete()
        assertTrue(repository.deleteProject("project_s0"))
        assertTrue(repository.loadProject("project_s0") == null)
        assertEquals("保留项目", repository.loadProject("project_keep")!!.project.title)
    }

    @Test
    fun `pending commit recovers idempotently after every durable step`() {
        val steps = listOf(
            "STATE_WRITTEN",
            "PLAN_WRITTEN",
            "EVENTS_APPENDED",
            "CHAPTER_META_WRITTEN",
            "COMPLETED_WRITTEN",
        )
        steps.forEachIndexed { index, failedStep ->
            val root = File(tempDir, "step-$index")
            val repository = FileS0NovelRepository(root) { step ->
                if (step == failedStep) error("INJECTED_CRASH:$step")
            }
            repository.createProject(project(), plan())
            val task = app.zhijuan.core.s0.S0ChapterTask("task_step_${index}000000", "project_s0", 1, 0, "第一章", "确认线索", "")
            val draft = repository.saveReadableDraft("project_s0", task, "故障注入仍需保留的正文。")
            val commit = pendingFor(repository, draft, "commit_step_${index}000000")
            repository.writePendingCommit(commit)

            assertThrows(IllegalStateException::class.java) { repository.applyPendingCommit(commit.commitId) }
            val restarted = FileS0NovelRepository(root)
            restarted.recoverPendingCommits()
            restarted.recoverPendingCommits()

            val restored = restarted.loadProject("project_s0")!!
            assertEquals(1, restored.storyState.revision, failedStep)
            assertEquals(S0ChapterState.COMMITTED, restored.chapters.single().state, failedStep)
            assertEquals(1, File(root, "project_s0/events.jsonl").readLines().size, failedStep)
            assertTrue(!File(root, "project_s0/commits/pending/${commit.commitId}.json").exists(), failedStep)
        }
    }

    @Test
    fun `incomplete jsonl tail is discarded before the next deduplicated append`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        val firstTask = app.zhijuan.core.s0.S0ChapterTask("task_jsonl_100000", "project_s0", 1, 0, "第一章", "确认线索", "")
        val firstDraft = repository.saveReadableDraft("project_s0", firstTask, "第一章正文。")
        val firstCommit = pendingFor(repository, firstDraft, "commit_jsonl_100000").copy(
            newState = S0StoryState(1, 2, listOf(1), listOf("event_1a", "event_1b")),
            events = listOf(
                S0Event("event_jsonl_100000a", "commit_jsonl_100000", 1, "event_1a", "第一章事件 A"),
                S0Event("event_jsonl_100000b", "commit_jsonl_100000", 1, "event_1b", "第一章事件 B"),
            ),
        )
        repository.writePendingCommit(firstCommit)
        repository.applyPendingCommit(firstCommit.commitId)
        File(tempDir, "project_s0/events.jsonl").appendText("{\"eventId\":\"half")

        val secondTask = app.zhijuan.core.s0.S0ChapterTask("task_jsonl_200000", "project_s0", 2, 1, "第二章", "推进线索", "")
        val secondDraft = repository.saveReadableDraft("project_s0", secondTask, "第二章正文。")
        val secondCommit = S0PendingCommit(
            commitId = "commit_jsonl_200000",
            projectId = "project_s0",
            chapter = 2,
            baseRevision = 1,
            targetRevision = 2,
            newState = S0StoryState(2, 3, listOf(1, 2), listOf("event_1", "event_2")),
            newPlan = plan().drop(2),
            events = listOf(S0Event("event_jsonl_200000", "commit_jsonl_200000", 2, "event_2", "第二章事件")),
            chapterMeta = secondDraft.copy(state = S0ChapterState.COMMITTED, summary = "第二章已经推进线索并形成新的可恢复状态。", commitId = "commit_jsonl_200000"),
        )
        repository.writePendingCommit(secondCommit)
        repository.applyPendingCommit(secondCommit.commitId)

        val lines = File(tempDir, "project_s0/events.jsonl").readLines()
        assertEquals(3, lines.size)
        assertTrue(lines.none { it.contains("half") })
    }

    @Test
    fun `reused model event id cannot hide a later committed event and startup repairs old logs`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        val firstTask = app.zhijuan.core.s0.S0ChapterTask("task_collision_100000", "project_s0", 1, 0, "第一章", "确认线索", "")
        val firstDraft = repository.saveReadableDraft("project_s0", firstTask, "第一章正文。")
        val reusedEventId = "event_reused_123456"
        val firstCommit = pendingFor(repository, firstDraft, "commit_collision_100000").copy(
            newState = S0StoryState(1, 2, listOf(1), listOf("event_first")),
            events = listOf(S0Event(reusedEventId, "commit_collision_100000", 1, "event_first", "第一章事件")),
        )
        repository.writePendingCommit(firstCommit)
        repository.applyPendingCommit(firstCommit.commitId)

        val secondTask = app.zhijuan.core.s0.S0ChapterTask("task_collision_200000", "project_s0", 2, 1, "第二章", "推进线索", "")
        val secondDraft = repository.saveReadableDraft("project_s0", secondTask, "第二章正文。")
        val secondCommit = S0PendingCommit(
            commitId = "commit_collision_200000",
            projectId = "project_s0",
            chapter = 2,
            baseRevision = 1,
            targetRevision = 2,
            newState = S0StoryState(2, 3, listOf(1, 2), listOf("event_first", "event_second")),
            newPlan = plan().drop(2),
            events = listOf(S0Event(reusedEventId, "commit_collision_200000", 2, "event_second", "第二章事件")),
            chapterMeta = secondDraft.copy(
                state = S0ChapterState.COMMITTED,
                summary = "第二章已经推进线索并形成新的可恢复状态。",
                commitId = "commit_collision_200000",
            ),
        )
        repository.writePendingCommit(secondCommit)
        repository.applyPendingCommit(secondCommit.commitId)

        // Simulate an older ID-only de-duplication bug by removing the second event line.
        val eventLog = File(tempDir, "project_s0/events.jsonl")
        eventLog.writeText(eventLog.readLines().first() + "\n")
        FileS0NovelRepository(tempDir).recoverPendingCommits()

        val events = eventLog.readLines().map { line ->
            kotlinx.serialization.json.Json.parseToJsonElement(line).jsonObject
        }
        assertEquals(2, events.size)
        assertEquals(setOf("event_first", "event_second"), events.map { it["eventKey"]!!.jsonPrimitive.content }.toSet())
        assertEquals(2, events.map { it["eventId"]!!.jsonPrimitive.content }.toSet().size)
    }

    @Test
    fun `corrupt state primary uses intact backup while pending commit finishes`() {
        val root = File(tempDir, "state-backup")
        val repository = FileS0NovelRepository(root) { step ->
            if (step == "STATE_WRITTEN") error("INJECTED_CRASH")
        }
        repository.createProject(project(), plan())
        val task = app.zhijuan.core.s0.S0ChapterTask("task_backup_100000", "project_s0", 1, 0, "第一章", "确认线索", "")
        val draft = repository.saveReadableDraft("project_s0", task, "备份恢复正文。")
        val commit = pendingFor(repository, draft, "commit_backup_100000")
        repository.writePendingCommit(commit)
        assertThrows(IllegalStateException::class.java) { repository.applyPendingCommit(commit.commitId) }
        File(root, "project_s0/state.json").writeText("{broken")

        val restarted = FileS0NovelRepository(root)
        restarted.recoverPendingCommits()

        assertEquals(1, restarted.loadProject("project_s0")!!.storyState.revision)
        assertEquals(1, File(root, "project_s0/events.jsonl").readLines().size)
    }

    @Test
    fun `complete corrupt jsonl record blocks commit before authoritative state changes`() {
        val repository = FileS0NovelRepository(tempDir)
        repository.createProject(project(), plan())
        File(tempDir, "project_s0/events.jsonl").writeText("{broken}\n")
        val task = app.zhijuan.core.s0.S0ChapterTask("task_corrupt_100000", "project_s0", 1, 0, "第一章", "确认线索", "")
        val draft = repository.saveReadableDraft("project_s0", task, "正文仍然可读。")
        val commit = pendingFor(repository, draft, "commit_corrupt_100000")
        repository.writePendingCommit(commit)

        val failure = assertThrows(S0StorageException::class.java) { repository.applyPendingCommit(commit.commitId) }

        assertEquals("EVENT_LOG_CORRUPT", failure.message)
        assertEquals(0, repository.loadProject("project_s0")!!.storyState.revision)
        assertEquals(S0ChapterState.READABLE_DRAFT, repository.loadProject("project_s0")!!.chapters.single().state)
        assertTrue(File(tempDir, "project_s0/commits/pending/${commit.commitId}.json").isFile)
    }

    private fun pendingFor(repository: FileS0NovelRepository, draft: app.zhijuan.core.s0.S0Chapter, commitId: String) =
        S0PendingCommit(
            commitId = commitId,
            projectId = "project_s0",
            chapter = 1,
            baseRevision = 0,
            targetRevision = 1,
            newState = S0StoryState(revision = 1, nextChapter = 2, committedChapters = listOf(1), recentEventKeys = listOf("event_1")),
            newPlan = plan().drop(1),
            events = listOf(S0Event("event_${commitId.removePrefix("commit_")}", commitId, 1, "event_1", "获得线索")),
            chapterMeta = draft.copy(
                state = S0ChapterState.COMMITTED,
                summary = "第一章已经确认线索并形成下一章可以继续使用的状态。",
                commitId = commitId,
            ),
        )

    private fun project() = S0Project("project_s0", "灯下回卷", "悬疑", "林岑", "安静克制", "旧车站里出现回卷印记", "2026-08-17T00:00:00Z")

    private fun plan() = (1..8).map { chapter ->
        S0PlanItem(chapter, "第${chapter}章", "确认第${chapter}章线索", "线索未明", "线索前进", "灯下档案")
    }
}
