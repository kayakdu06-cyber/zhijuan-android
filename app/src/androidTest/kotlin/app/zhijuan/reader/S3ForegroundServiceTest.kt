package app.zhijuan.reader

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S1RequestIds
import app.zhijuan.core.s0.S3GenerationJob
import app.zhijuan.core.s0.S3GenerationJobStore
import app.zhijuan.core.s0.S3JobPurpose
import app.zhijuan.core.s0.S3JobStage
import app.zhijuan.data.s0.FileS0NovelRepository
import app.zhijuan.data.s0.FileS3GenerationJobStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S3ForegroundServiceTest {
    @Test
    fun explicitDiscardRemovesStaleJobAndProjectWithoutChangingProviderConfiguration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val projectsRoot = File(context.filesDir, "zhijuan-projects")
        val projectId = "service_discard_test"
        File(projectsRoot, projectId).deleteRecursively()
        val repository = FileS0NovelRepository(projectsRoot)
        repository.createProject(
            S0Project(projectId, "待删除测试", "悬疑", "林岑", "克制", "只验证本地安全删除"),
            (1..8).map { chapter ->
                S0PlanItem(chapter, "第${chapter}章", "验证删除", "未开始", "记录状态", "测试结束")
            },
        )
        val taskId = "task_discard_100000"
        FileS3GenerationJobStore(projectsRoot).save(
            S3GenerationJob(
                jobId = "job_discard_100000",
                projectId = projectId,
                chapter = 1,
                purpose = S3JobPurpose.SETTLEMENT_REPAIR,
                stage = S3JobStage.SETTLEMENT_REQUEST,
                requestId = S1RequestIds.settlement(taskId),
                taskId = taskId,
                promptTemplateId = "chapter-settlement-v1",
                attempt = 2,
                createdAt = "2026-08-21T00:00:00Z",
                updatedAt = "2026-08-21T00:00:00Z",
                lastErrorCode = "SETTLEMENT_NOT_JSON",
                outcomeKnown = true,
            ),
        )
        val configDirectory = File(context.filesDir, "zhijuan-config")
        val configBefore = textFileSnapshot(configDirectory)

        S3GenerationController(context).discardProject(projectId)
        var attempts = 0
        while (repository.loadProject(projectId) != null && attempts < 200) {
            SystemClock.sleep(50)
            attempts += 1
        }

        assertTrue(repository.loadProject(projectId) == null)
        assertTrue(!File(projectsRoot, projectId).exists())
        assertEquals(configBefore, textFileSnapshot(configDirectory))
        val state = S3GenerationRuntime.status.value
        assertTrue(state is S3GenerationUiState.Finished)
        assertEquals(projectId, (state as S3GenerationUiState.Finished).projectId)
        assertTrue(state.message.contains("API 配置与其他书未改变"))
        context.stopService(Intent(context, S3GenerationForegroundService::class.java))
    }

    @Suppress("DEPRECATION")
    @Test
    fun foregroundServiceDeclaresDataSyncAndPersistsSafeCheckpointWhenProviderIsUnavailable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val projectsRoot = File(context.filesDir, "zhijuan-projects")
        val projectId = "service_test"
        File(projectsRoot, projectId).deleteRecursively()
        FileS0NovelRepository(projectsRoot).createProject(
            S0Project(projectId, "服务测试", "悬疑", "林岑", "克制", "只验证本地检查点"),
            (1..8).map { chapter ->
                S0PlanItem(chapter, "第${chapter}章", "验证前台任务安全失败", "未开始", "记录状态", "测试结束")
            },
        )
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, S3GenerationForegroundService::class.java),
            0,
        )
        assertTrue(serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0)

        ContextCompat.startForegroundService(
            context,
            Intent(context, S3GenerationForegroundService::class.java)
                .setAction(ACTION_GENERATE)
                .putExtra(EXTRA_PROJECT_ID, projectId),
        )
        val store: S3GenerationJobStore = FileS3GenerationJobStore(projectsRoot)
        repeat(100) {
            if (store.load(projectId)?.lastErrorCode != null) return@repeat
            SystemClock.sleep(50)
        }

        val checkpoint = store.load(projectId)
        assertNotNull(checkpoint)
        assertNotNull(checkpoint?.lastErrorCode)
        assertTrue(!File(projectsRoot, "$projectId/jobs/active.json").readText().contains("Authorization"))
        context.stopService(Intent(context, S3GenerationForegroundService::class.java))
    }

    private fun textFileSnapshot(directory: File): Map<String, String> =
        directory.takeIf(File::isDirectory)?.walkTopDown()?.filter(File::isFile)?.associate { file ->
            file.relativeTo(directory).invariantSeparatorsPath to file.readText()
        }.orEmpty()
}
