package app.zhijuan.reader

import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0StoryState
import app.zhijuan.data.s0.FileS0NovelRepository
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S5TextProjectPlanTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `five text fields produce a confirmed eight chapter local plan without provider work`() {
        val draft = buildInitialProjectDraft(
            "雾钟",
            "悬疑",
            "沈砚",
            "克制",
            "无雾之夜钟楼少走一分钟",
            S0ContentScale.CHEN_JIN,
            S0PlotPace.TIGHT,
        )

        assertTrue(draft.project.id.matches(Regex("project_[0-9a-f]{16}")))
        assertEquals("雾钟", draft.project.title)
        assertEquals(S0ContentScale.CHEN_JIN, draft.project.contentScale)
        assertEquals(S0PlotPace.TIGHT, draft.project.plotPace)
        assertEquals((1..8).toList(), draft.plan.map(S0PlanItem::chapter))
        assertTrue(draft.plan.all { it.goal.isNotBlank() && it.mustChange.isNotBlank() })
        assertThrows(IllegalArgumentException::class.java) {
            buildInitialProjectDraft("", "悬疑", "沈砚", "克制", "设定")
        }
    }

    @Test
    fun `explicit refresh keeps two remaining items and extends a contiguous eight item window`() {
        val snapshot = S0ProjectSnapshot(
            project = S0Project("project_refresh", "雾钟", "悬疑", "沈砚", "克制", "钟楼谜题"),
            storyState = S0StoryState(6, 7, (1..6).toList(), listOf("first_meeting")),
            plan = listOf(planItem(7), planItem(8)),
            chapters = listOf(S0Chapter(6, "第六章", "task_6", "正文", S0ChapterState.COMMITTED, "雾散后钟声再次出现", "commit_6")),
        )

        val refreshed = buildRefreshedPlan(snapshot)

        assertEquals((7..14).toList(), refreshed.map(S0PlanItem::chapter))
        assertEquals("原计划7", refreshed.first().title)
        assertTrue(refreshed.drop(2).all { "first_meeting" in it.mustNotRepeatEventKeys })
    }

    @Test
    fun `confirmed text draft crosses app and data into a second independently readable project`() {
        val repository = FileS0NovelRepository(tempDir)
        val first = buildInitialProjectDraft("第一部", "悬疑", "甲", "克制", "第一项设定")
        val second = buildInitialProjectDraft("第二部", "奇幻", "乙", "明快", "第二项设定")

        repository.createProject(first.project, first.plan)
        repository.createProject(second.project, second.plan)

        assertEquals(setOf("第一部", "第二部"), repository.listProjects().map { it.project.title }.toSet())
        assertEquals(8, repository.loadProject(second.project.id)!!.plan.size)
        assertTrue(File(tempDir, first.project.id).isDirectory)
        assertTrue(File(tempDir, second.project.id).isDirectory)
    }

    private fun planItem(chapter: Int) = S0PlanItem(
        chapter,
        "原计划$chapter",
        "推进$chapter",
        "承接",
        "变化",
        "入口",
    )
}
