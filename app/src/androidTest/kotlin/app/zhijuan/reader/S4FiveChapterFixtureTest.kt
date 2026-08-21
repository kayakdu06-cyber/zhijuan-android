package app.zhijuan.reader

import androidx.test.platform.app.InstrumentationRegistry
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.data.s0.FileS0NovelRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S4FiveChapterFixtureTest {
    @Test
    fun seedDedicatedFiveChapterAcceptanceProjectWithoutTouchingExistingBooks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectsRoot = File(context.filesDir, "zhijuan-projects")
        val projectDir = File(projectsRoot, PROJECT_ID)
        if (projectDir.exists()) {
            check(projectDir.canonicalFile.toPath().startsWith(projectsRoot.canonicalFile.toPath()))
            assertTrue(projectDir.deleteRecursively())
        }
        val repository = FileS0NovelRepository(projectsRoot)
        val snapshot = repository.createProject(
            project = S0Project(
                id = PROJECT_ID,
                title = "雾港铜钥",
                genre = "近未来悬疑",
                protagonist = "沈砚",
                tone = "克制、紧张、避免复述",
                premise = "档案员沈砚与维修员叶舟在停摆的雾港钟楼相遇；世上仅有一把黄铜钥匙，最初由沈砚持有。钟楼每逢无雾之夜少走一分钟，这条伏笔必须到第五章才回收。",
                createdAt = "2026-08-20T10:00:00Z",
            ),
            plan = acceptancePlan(),
        )

        assertEquals(PROJECT_ID, snapshot.project.id)
        assertEquals(8, snapshot.plan.size)
        assertEquals(1, snapshot.storyState.nextChapter)
        assertTrue(snapshot.chapters.isEmpty())
    }

    private fun acceptancePlan(): List<S0PlanItem> = listOf(
        S0PlanItem(1, "雾中初见", "让沈砚与叶舟在钟楼首次相遇，并明确唯一黄铜钥匙由沈砚持有", "两人互不相识；钥匙在沈砚手中", "两人建立最低限度合作", "钟摆背面出现被刮去的日期", ENTITY_IDS, listOf("first_meeting", "brass_key_acquired")),
        S0PlanItem(2, "缺失的一分钟", "两人从维修记录发现钟楼每逢无雾之夜少走一分钟", "已合作但互不信任", "获得可验证的新信息并种下第五章回收线索", "旧维修日志提到第五声钟响", ENTITY_IDS, listOf("first_meeting", "brass_key_acquired")),
        S0PlanItem(3, "锈梯断裂", "调查时让叶舟轻伤并耗尽一枚应急照明棒", "两人掌握缺失一分钟的规律", "伤势和资源发生明确变化", "断梯下露出只能用黄铜钥匙开启的检修门", ENTITY_IDS, listOf("first_meeting", "brass_key_acquired")),
        S0PlanItem(4, "门后的选择", "在不转移黄铜钥匙所有权的前提下推进两人的信任关系", "叶舟带伤；照明棒耗尽", "沈砚选择相信叶舟对钟机的判断", "第五声钟响前，雾突然散去", ENTITY_IDS, listOf("first_meeting", "brass_key_acquired")),
        S0PlanItem(5, "归还的一分钟", "回收无雾之夜少走一分钟与第五声钟响的伏笔", "两人已形成信任；黄铜钥匙仍由沈砚持有", "解释并阻止钟楼吞掉下一分钟", "钟楼恢复走时，但档案出现新的空白页", ENTITY_IDS, listOf("first_meeting", "brass_key_acquired")),
        S0PlanItem(6, "空白页", "调查新出现的空白页", "钟楼恢复走时", "建立后续谜题", "页角浮出陌生编号", ENTITY_IDS),
        S0PlanItem(7, "编号仓", "追查陌生编号", "空白页编号出现", "推进后续谜题", "仓门内传来钟声", ENTITY_IDS),
        S0PlanItem(8, "雾外钟声", "确认钟声来源", "两人抵达编号仓", "完成当前滚动计划", "新的调查方向出现", ENTITY_IDS),
    )

    private companion object {
        const val PROJECT_ID = "release_five_chapter"
        val ENTITY_IDS = listOf("char_shen", "char_ye", "item_brass_key", "place_clocktower", "fact_missing_minute")
    }
}
