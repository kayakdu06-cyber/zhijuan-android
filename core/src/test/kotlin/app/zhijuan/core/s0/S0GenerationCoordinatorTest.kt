package app.zhijuan.core.s0

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S0GenerationCoordinatorTest {
    @Test
    fun `three explicit sequential chapters each receive their own permit two calls and commit`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        val provider = S0FakeProvider()
        val coordinator = S0GenerationCoordinator(repository, provider)

        val results = (1..3).map { coordinator.generateNextChapter("project_s0") }

        assertTrue(results.all { it is S0GenerationResult.Committed })
        assertEquals(listOf(1, 2, 3), results.map { (it as S0GenerationResult.Committed).chapter.number })
        assertTrue(results.all { (it as S0GenerationResult.Committed).proseCalls == 1 })
        assertTrue(results.all { (it as S0GenerationResult.Committed).settlementCalls == 1 })
        assertEquals(3, provider.proseCalls)
        assertEquals(3, provider.settlementCalls)
        assertEquals(3, repository.loadProject("project_s0")!!.storyState.revision)
        assertEquals(listOf(1, 2, 3), repository.loadProject("project_s0")!!.storyState.committedChapters)
    }

    @Test
    fun `normal chapter saves readable draft then commits with exactly two provider calls`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        val provider = S0FakeProvider()
        val stages = mutableListOf<S3JobStage>()
        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0") { _, stage ->
            stages += stage
        }

        val committed = result as S0GenerationResult.Committed
        assertEquals(1, committed.proseCalls)
        assertEquals(1, committed.settlementCalls)
        assertEquals(1, provider.proseCalls)
        assertEquals(1, provider.settlementCalls)
        assertEquals(S0ChapterState.COMMITTED, repository.loadProject("project_s0")!!.chapters.single().state)
        assertEquals(1, repository.events.size)
        assertTrue(repository.events.single().eventId.matches(Regex("event_[0-9a-f]{16}_1")))
        assertEquals(S3JobStage.entries, stages)
    }

    @Test
    fun `settlement failure keeps readable draft without changing revision`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit) = "可读草稿"
            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement = error("bad-json")
        }
        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0")
        assertTrue(result is S0GenerationResult.ReadableDraft)
        val snapshot = repository.loadProject("project_s0")!!
        assertEquals(0, snapshot.storyState.revision)
        assertEquals(S0ChapterState.READABLE_DRAFT, snapshot.chapters.single().state)
    }

    @Test
    fun `prose overflow keeps completed stream chunks without calling settlement`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        var settlementCalls = 0
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                onChunk("已经完整接收的正文片段。")
                throw S1ProviderException(S1ProviderErrors.of(S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED))
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                error("settlement must not run after prose overflow")
            }
        }

        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0")

        val incompleteDraft = result as S0GenerationResult.IncompleteDraft
        assertEquals(S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED.name, incompleteDraft.reason)
        assertEquals(0, settlementCalls)
        val snapshot = repository.loadProject("project_s0")!!
        assertEquals(0, snapshot.storyState.revision)
        assertEquals("已经完整接收的正文片段。", snapshot.chapters.single().prose)
        assertEquals(S0ChapterState.PAUSED, snapshot.chapters.single().state)
        assertEquals(S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED.name, snapshot.chapters.single().incompleteReason)
    }

    @Test
    fun `explicit regeneration may replace a paused incomplete draft and then commit normally`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        var proseCalls = 0
        var settlementCalls = 0
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                proseCalls += 1
                if (proseCalls == 1) {
                    onChunk("首次被截断的片段。")
                    throw S1ProviderException(S1ProviderErrors.of(S1ProviderErrorCode.PROSE_TRUNCATED_LENGTH))
                }
                return "重新生成后完整收束的正文。".also(onChunk)
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                return S0Settlement(task.taskId, task.chapter, task.baseRevision, "完整正文已结算。", "chapter_1_complete", "本章完整完成")
            }
        }
        val coordinator = S0GenerationCoordinator(repository, provider)

        assertTrue(coordinator.generateNextChapter("project_s0") is S0GenerationResult.IncompleteDraft)
        val recovered = coordinator.generateNextChapter("project_s0") as S0GenerationResult.Committed

        assertEquals(2, proseCalls)
        assertEquals(1, settlementCalls)
        assertEquals("重新生成后完整收束的正文。", recovered.chapter.prose)
        assertEquals(null, recovered.chapter.incompleteReason)
        assertEquals(S0ChapterState.COMMITTED, repository.loadProject("project_s0")!!.chapters.single().state)
    }

    @Test
    fun `explicit settlement retry reuses saved prose and makes no second prose call`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        var proseCalls = 0
        var settlementCalls = 0
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                proseCalls += 1
                return "已经保存的正文。".also(onChunk)
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                if (settlementCalls == 1) error("bad-json")
                return S0Settlement(
                    task.taskId,
                    task.chapter,
                    task.baseRevision,
                    "正文结算在用户明确操作后完成，既有正文没有重新生成。",
                    "chapter_1_recovered",
                    "明确重试结算成功",
                )
            }
        }
        val coordinator = S0GenerationCoordinator(repository, provider)
        assertTrue(coordinator.generateNextChapter("project_s0") is S0GenerationResult.ReadableDraft)

        val retry = coordinator.retrySettlement("project_s0") as S0GenerationResult.Committed

        assertEquals(0, retry.proseCalls)
        assertEquals(1, retry.settlementCalls)
        assertEquals(1, proseCalls)
        assertEquals(2, settlementCalls)
        assertEquals(S0ChapterState.COMMITTED, repository.loadProject("project_s0")!!.chapters.single().state)
    }

    @Test
    fun `network failure before first prose chunk leaves authority unchanged and does not settle`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        var settlementCalls = 0
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                throw S1ProviderException(S1ProviderErrors.of(S1ProviderErrorCode.NETWORK_OFFLINE))
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                error("must not settle without prose")
            }
        }

        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0")

        assertTrue(result is S0GenerationResult.Rejected)
        assertTrue((result as S0GenerationResult.Rejected).reason.contains("NETWORK_OFFLINE"))
        assertEquals(0, settlementCalls)
        assertEquals(0, repository.loadProject("project_s0")!!.storyState.revision)
        assertTrue(repository.loadProject("project_s0")!!.chapters.isEmpty())
    }

    @Test
    fun `mid stream cancellation never promotes partial prose or calls settlement`() = runBlocking {
        val repository = InMemoryS0NovelRepository()
        repository.createProject(project(), plan())
        var settlementCalls = 0
        val provider = object : S0TextGenerationProvider {
            override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
                onChunk("尚未完成的正文片段")
                throw S1ProviderException(S1ProviderErrors.of(S1ProviderErrorCode.USER_CANCELLED))
            }

            override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                settlementCalls += 1
                error("must not settle a cancelled stream")
            }
        }

        val result = S0GenerationCoordinator(repository, provider).generateNextChapter("project_s0")

        assertTrue(result is S0GenerationResult.Rejected)
        assertTrue((result as S0GenerationResult.Rejected).reason.contains("USER_CANCELLED"))
        assertEquals(0, settlementCalls)
        assertEquals(0, repository.loadProject("project_s0")!!.storyState.revision)
        assertTrue(repository.loadProject("project_s0")!!.chapters.isEmpty())
    }

    private fun project() = S0Project("project_s0", "灯下回卷", "悬疑", "林岑", "安静克制", "旧车站里出现一枚回卷印记")

    private fun plan() = (1..8).map { chapter ->
        S0PlanItem(chapter, "第${chapter}章", "确认第${chapter}章的具体线索变化", "线索未明", "线索前进一步", "灯下档案留下下一章入口")
    }
}

private class InMemoryS0NovelRepository : S0NovelRepository {
    private val snapshots = linkedMapOf<String, S0ProjectSnapshot>()
    val events = mutableListOf<S0Event>()
    private val pending = linkedMapOf<String, S0PendingCommit>()

    override fun createProject(
        project: S0Project,
        plan: List<S0PlanItem>,
        writingSkill: S0WritingSkillImport?,
    ): S0ProjectSnapshot {
        val snapshot = S0ProjectSnapshot(project, S0StoryState(), plan, emptyList())
        snapshots[project.id] = snapshot
        return snapshot
    }

    override fun listProjects() = snapshots.values.toList()
    override fun loadProject(projectId: String) = snapshots[projectId]

    override fun saveReadableDraft(projectId: String, task: S0ChapterTask, prose: String): S0Chapter {
        val current = requireNotNull(snapshots[projectId])
        val chapter = S0Chapter(task.chapter, task.title, task.taskId, prose, S0ChapterState.READABLE_DRAFT)
        snapshots[projectId] = current.copy(chapters = current.chapters.filterNot { it.number == task.chapter } + chapter)
        return chapter
    }

    override fun saveIncompleteDraft(projectId: String, task: S0ChapterTask, prose: String, reason: String): S0Chapter {
        val current = requireNotNull(snapshots[projectId])
        val chapter = S0Chapter(
            task.chapter,
            task.title,
            task.taskId,
            prose,
            S0ChapterState.PAUSED,
            incompleteReason = reason,
        )
        snapshots[projectId] = current.copy(chapters = current.chapters.filterNot { it.number == task.chapter } + chapter)
        return chapter
    }

    override fun writePendingCommit(commit: S0PendingCommit) { pending[commit.commitId] = commit }

    override fun applyPendingCommit(commitId: String) {
        val commit = pending.remove(commitId) ?: return
        val current = requireNotNull(snapshots[commit.projectId])
        if (current.storyState.revision == commit.baseRevision) {
            snapshots[commit.projectId] = current.copy(
                storyState = commit.newState,
                plan = commit.newPlan,
                chapters = current.chapters.filterNot { it.number == commit.chapter } + commit.chapterMeta,
            )
            commit.events.forEach { event -> if (events.none { it.eventId == event.eventId }) events += event }
        }
    }

    override fun recoverPendingCommits() = pending.keys.toList().also { it.forEach(::applyPendingCommit) }
}
