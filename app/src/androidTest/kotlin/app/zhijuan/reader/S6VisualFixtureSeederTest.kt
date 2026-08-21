package app.zhijuan.reader

import androidx.test.platform.app.InstrumentationRegistry
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0Event
import app.zhijuan.core.s0.S0PendingCommit
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0StoryState
import app.zhijuan.data.s0.FileS0NovelRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Creates stable local-only content used by repeatable emulator visual QA. */
class S6VisualFixtureSeederTest {
    @Test
    fun seedEditorialUiFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectsRoot = File(context.filesDir, "zhijuan-projects")
        val projectDir = File(projectsRoot, PROJECT_ID)
        if (projectDir.exists()) {
            check(projectDir.canonicalFile.toPath().startsWith(projectsRoot.canonicalFile.toPath()))
            check(projectDir.deleteRecursively())
        }

        val plan = editorialPlan()
        val repository = FileS0NovelRepository(projectsRoot)
        repository.createProject(
            S0Project(
                id = PROJECT_ID,
                title = "雾港铜钥",
                genre = "近未来悬疑",
                protagonist = "沈砚",
                tone = "克制、冷静、有古典叙事感",
                premise = "雾港的旧钟楼每逢无雾之夜就少走一分钟，档案员沈砚必须在第五声钟响前找回被删去的时间。",
                createdAt = "2026-08-01T00:00:00Z",
            ),
            plan,
        )

        (1..4).forEach { chapterNumber ->
            val planItem = plan.first { it.chapter == chapterNumber }
            val taskId = "task_visual_${chapterNumber.toString().padStart(6, '0')}"
            val commitId = "commit_visual_${chapterNumber.toString().padStart(6, '0')}"
            val task = S0ChapterTask(
                taskId = taskId,
                projectId = PROJECT_ID,
                chapter = chapterNumber,
                baseRevision = chapterNumber - 1,
                title = planItem.title,
                goal = planItem.goal,
                previousTail = planItem.entryState,
            )
            val draft = repository.saveReadableDraft(PROJECT_ID, task, prose(chapterNumber, planItem.title))
            repository.writePendingCommit(
                S0PendingCommit(
                    commitId = commitId,
                    projectId = PROJECT_ID,
                    chapter = chapterNumber,
                    baseRevision = chapterNumber - 1,
                    targetRevision = chapterNumber,
                    newState = S0StoryState(
                        revision = chapterNumber,
                        nextChapter = chapterNumber + 1,
                        committedChapters = (1..chapterNumber).toList(),
                        recentEventKeys = (1..chapterNumber).map { "visual_event_$it" },
                    ),
                    newPlan = plan.drop(chapterNumber),
                    events = listOf(
                        S0Event(
                            eventId = "event_visual_${chapterNumber.toString().padStart(6, '0')}",
                            commitId = commitId,
                            chapter = chapterNumber,
                            eventKey = "visual_event_$chapterNumber",
                            payload = "第 $chapterNumber 章关键变化",
                        ),
                    ),
                    chapterMeta = draft.copy(
                        state = S0ChapterState.COMMITTED,
                        summary = "内部结算摘要：仅供连续性计算，绝不显示在正文中。",
                        commitId = commitId,
                    ),
                ),
            )
            repository.applyPendingCommit(commitId)
        }

        val configDirectory = File(context.filesDir, "zhijuan-config").apply { mkdirs() }
        File(configDirectory, "provider-settings.json").writeText(
            """{"schemaVersion":"1.0","providerId":"deepseek","baseUrl":"https://api.deepseek.com","normalizedChatCompletionsUrl":"https://api.deepseek.com/chat/completions","model":"deepseek-chat","credentialAlias":"visual_qa_only","connectTimeoutSeconds":30,"readTimeoutSeconds":300,"totalTimeoutSeconds":900,"maxProseCharacters":12000,"lastConnectionTestAt":null}""",
        )

        assertEquals(5, repository.loadProject(PROJECT_ID)?.storyState?.nextChapter)
    }

    private fun editorialPlan(): List<S0PlanItem> = listOf(
        S0PlanItem(1, "雾中初见", "让沈砚在钟楼遇见维修员叶舟", "两人互不相识", "建立最低限度合作", "钟摆背面出现被刮去的日期"),
        S0PlanItem(2, "缺失的一分钟", "从旧维修记录确认时间缺口", "两人开始合作", "获得可验证的新线索", "日志提到第五声钟响"),
        S0PlanItem(3, "锈梯断裂", "让调查付出明确代价", "掌握缺失一分钟的规律", "叶舟受伤且照明耗尽", "断梯下露出检修门"),
        S0PlanItem(4, "门后的选择", "在不转移钥匙所有权的前提下建立信任", "资源紧张且叶舟带伤", "沈砚选择相信叶舟", "第五声钟响前雾突然散去"),
        S0PlanItem(5, "空白页", "调查新出现的空白页", "港口停电持续十三分钟", "沈砚仍保管着黄铜钥匙", "旧档案缺失一页"),
        S0PlanItem(6, "空白页", "调查新出现的空白页", "钟楼恢复走时", "建立后续谜题", "页角浮出陌生编号"),
        S0PlanItem(7, "编号仓", "追查陌生编号", "空白页编号出现", "推进后续谜题", "仓门内传来钟声"),
        S0PlanItem(8, "雾外钟声", "确认钟声来源", "两人抵达编号仓", "完成当前滚动计划", "新的调查方向出现"),
    )

    private fun prose(chapter: Int, title: String): String = """
        雾从港口一路漫到钟楼脚下，石阶像被谁从夜色里一层层擦亮。

        沈砚把黄铜钥匙收回掌心。它带着旧金属的凉意，也带着一段没有写进档案的时间。远处传来第 $chapter 声钟响，声音穿过空荡的维修层，落在《${title}》那页尚未干透的墨迹上。

        叶舟没有催促。他只是抬头看向停住的分针，等那被删去的一分钟重新出现。
    """.trimIndent()

    private companion object {
        const val PROJECT_ID = "visual_editorial_fixture"
    }
}
