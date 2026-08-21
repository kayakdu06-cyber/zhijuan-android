package app.zhijuan.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0Settlement
import app.zhijuan.core.s0.S0TextGenerationProvider
import app.zhijuan.core.s0.S1ProviderErrorCode
import app.zhijuan.core.s0.S1ProviderErrors
import app.zhijuan.core.s0.S1ProviderException
import app.zhijuan.core.s0.S1RequestIds
import app.zhijuan.core.s0.S3GenerationJob
import app.zhijuan.core.s0.S3JobPurpose
import app.zhijuan.core.s0.S3JobStage
import app.zhijuan.data.s0.FileS0NovelRepository
import app.zhijuan.data.s0.FileS3GenerationJobStore
import app.zhijuan.data.s0.provider.OpenAiCompatibleS1Provider
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface S3GenerationUiState {
    data object Idle : S3GenerationUiState
    data class Running(
        val projectId: String,
        val chapter: Int,
        val stage: S3JobStage,
        val batchPosition: Int = 1,
        val batchTotal: Int = 1,
    ) : S3GenerationUiState
    data class NeedsAction(val projectId: String, val code: String) : S3GenerationUiState
    data class Finished(val projectId: String, val message: String) : S3GenerationUiState
}

object S3GenerationRuntime {
    private val mutableStatus = MutableStateFlow<S3GenerationUiState>(S3GenerationUiState.Idle)
    val status = mutableStatus.asStateFlow()

    internal fun update(value: S3GenerationUiState) {
        mutableStatus.value = value
    }
}

class S3GenerationController(context: Context) {
    private val applicationContext = context.applicationContext

    fun generateChapter(projectId: String) = generateChapters(projectId, 1)

    fun generateChapters(projectId: String, count: Int) {
        require(count in 1..3) { "BATCH_SIZE_INVALID" }
        start(ACTION_GENERATE, projectId, count)
    }

    fun retrySettlement(projectId: String) = start(ACTION_RETRY_SETTLEMENT, projectId)

    fun discardProject(projectId: String) = start(ACTION_DISCARD_PROJECT, projectId)

    fun cancel() {
        applicationContext.startService(Intent(applicationContext, S3GenerationForegroundService::class.java).setAction(ACTION_CANCEL))
    }

    private fun start(action: String, projectId: String, chapterCount: Int = 1) {
        val intent = Intent(applicationContext, S3GenerationForegroundService::class.java)
            .setAction(action)
            .putExtra(EXTRA_PROJECT_ID, projectId)
            .putExtra(EXTRA_CHAPTER_COUNT, chapterCount)
        ContextCompat.startForegroundService(applicationContext, intent)
    }
}

class S3GenerationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val projectsRoot by lazy { File(filesDir, "zhijuan-projects") }
    private val repository by lazy { FileS0NovelRepository(projectsRoot) }
    private val jobStore by lazy { FileS3GenerationJobStore(projectsRoot) }
    private val provider by lazy { OpenAiCompatibleS1Provider.forApplication(this) }
    private var generationJob: Job? = null
    private var timeoutJob: Job? = null
    private var activeTask: S0ChapterTask? = null
    private var activeProjectId: String? = null
    private var settlementRepair = false
    private var activeAttempt = 1
    private var activeBatchPosition = 1
    private var activeBatchTotal = 1
    private var cancellationCode: String? = null
    private var activeProviderProfileId: String? = null
    private var discardRequestedProjectId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelActive("USER_CANCELLED")
            return START_NOT_STICKY
        }
        val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_DISCARD_PROJECT) {
            return discardProject(projectId, startId)
        }
        if (generationJob?.isActive == true) {
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "JOB_ALREADY_ACTIVE"))
            return START_NOT_STICKY
        }
        val retrySettlement = intent.action == ACTION_RETRY_SETTLEMENT
        val requestedChapterCount = if (retrySettlement) 1 else intent.getIntExtra(EXTRA_CHAPTER_COUNT, 1)
        if (requestedChapterCount !in 1..3) {
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "BATCH_SIZE_INVALID"))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val debugFault = intent.getStringExtra(EXTRA_DEBUG_FAULT).takeIf { BuildConfig.DEBUG }
        settlementRepair = retrySettlement
        activeBatchPosition = 1
        activeBatchTotal = requestedChapterCount
        cancellationCode = null
        val previousJobResult = runCatching { jobStore.load(projectId) }
        if (previousJobResult.isFailure) {
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "ACTIVE_JOB_CORRUPT"))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val previousJob = previousJobResult.getOrNull()
        activeProviderProfileId = previousJob?.providerProfileId ?: provider.connectionSummary()?.providerId
        val profileId = activeProviderProfileId
        if (profileId == null || runCatching { provider.lockProfile(profileId) }.isFailure) {
            val snapshot = repository.loadProject(projectId)
            val chapter = snapshot?.storyState?.nextChapter ?: 1
            val title = snapshot?.project?.title ?: "织卷"
            startForeground(
                NOTIFICATION_ID,
                notification(title, chapter, "API 配置不可用，任务未发送"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            val now = Instant.now().toString()
            jobStore.save(
                S3GenerationJob(
                    jobId = previousJob?.jobId ?: "job_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    projectId = projectId,
                    chapter = chapter,
                    purpose = S3JobPurpose.PROSE,
                    stage = S3JobStage.PREPARE,
                    createdAt = previousJob?.createdAt ?: now,
                    updatedAt = now,
                    attempt = previousJob?.attempt ?: 1,
                    promptTemplateId = "chapter-prose-v1",
                    lastErrorCode = "PROVIDER_PROFILE_UNAVAILABLE",
                    outcomeKnown = true,
                    providerProfileId = profileId,
                ),
            )
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "PROVIDER_PROFILE_UNAVAILABLE"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        activeAttempt = if (previousJob == null) 1 else previousJob.attempt + 1
        if (activeAttempt > 3) {
            provider.unlockProfile()
            activeProviderProfileId = null
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "ATTEMPT_LIMIT_REACHED"))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val snapshot = repository.loadProject(projectId)
        val chapter = snapshot?.storyState?.nextChapter ?: 1
        val title = snapshot?.project?.title ?: "织卷"
        activeProjectId = projectId
        startForeground(
            NOTIFICATION_ID,
            notification(
                title,
                chapter,
                when {
                    retrySettlement -> "准备重新结算"
                    requestedChapterCount > 1 -> "批次 1/$requestedChapterCount · 准备正文"
                    else -> "准备正文"
                },
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        generationJob = serviceScope.launch {
            runGeneration(projectId, title, retrySettlement, debugFault, requestedChapterCount)
            if (discardRequestedProjectId != projectId) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun discardProject(projectId: String, startId: Int): Int {
        val runningProjectId = activeProjectId.takeIf { generationJob?.isActive == true }
        if (runningProjectId != null && runningProjectId != projectId) {
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "PROJECT_DELETE_OTHER_JOB_ACTIVE"))
            return START_NOT_STICKY
        }
        val snapshot = runCatching { repository.loadProject(projectId) }.getOrNull()
        val title = snapshot?.project?.title ?: "织卷"
        val chapter = snapshot?.storyState?.nextChapter ?: 1
        if (runningProjectId == null) {
            startForeground(
                NOTIFICATION_ID,
                notification(title, chapter, "正在停止任务并删除本书"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            updateNotification(title, chapter, "正在停止任务并删除本书")
        }
        discardRequestedProjectId = projectId
        serviceScope.launch {
            try {
                val persistedJob = runCatching { jobStore.load(projectId) }.getOrNull()
                val requestIds = linkedSetOf<String>()
                persistedJob?.requestId?.let(requestIds::add)
                persistedJob?.taskId?.let { taskId ->
                    requestIds += S1RequestIds.prose(taskId)
                    requestIds += S1RequestIds.settlement(taskId)
                }
                activeTask?.takeIf { it.projectId == projectId }?.let { task ->
                    requestIds += S1RequestIds.prose(task.taskId)
                    requestIds += S1RequestIds.settlement(task.taskId)
                }
                requestIds.forEach(provider::cancel)
                if (runningProjectId == projectId) {
                    cancellationCode = "PROJECT_DISCARDED"
                    timeoutJob?.cancel()
                    generationJob?.cancelAndJoin()
                }
                jobStore.clear(projectId)
                val removed = repository.discardProject(projectId)
                check(removed || repository.loadProject(projectId) == null) { "PROJECT_DELETE_FAILED" }
                S3GenerationRuntime.update(S3GenerationUiState.Finished(projectId, "项目已从本机删除；API 配置与其他书未改变"))
            } catch (failure: Throwable) {
                S3GenerationRuntime.update(
                    S3GenerationUiState.NeedsAction(
                        projectId,
                        failure.message?.takeIf(String::isNotBlank) ?: "PROJECT_DELETE_FAILED",
                    ),
                )
            } finally {
                if (runningProjectId == projectId) {
                    activeTask = null
                    activeProjectId = null
                    generationJob = null
                    provider.unlockProfile()
                    activeProviderProfileId = null
                }
                discardRequestedProjectId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        persistFailure("FOREGROUND_SERVICE_TIMEOUT", outcomeKnown = false)
        cancelActive("FOREGROUND_SERVICE_TIMEOUT")
        stopSelf(startId)
    }

    override fun onDestroy() {
        timeoutJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runGeneration(
        projectId: String,
        title: String,
        retrySettlement: Boolean,
        debugFault: String?,
        requestedChapterCount: Int,
    ) {
        val generationProvider = if (debugFault == DEBUG_FAULT_BAD_SETTLEMENT_BEFORE_CALL) {
            object : S0TextGenerationProvider by provider {
                override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
                    throw S1ProviderException(S1ProviderErrors.of(S1ProviderErrorCode.SETTLEMENT_NOT_JSON))
                }
            }
        } else {
            provider
        }
        val coordinator = S0GenerationCoordinator(repository, generationProvider)
        val settlementRepairHint = if (retrySettlement) jobStore.load(projectId)?.lastErrorCode else null
        try {
            val batch = runS3SequentialBatch(
                requested = requestedChapterCount,
                generateChapter = { position ->
                    activeBatchPosition = position
                    if (position > 1) activeAttempt = 1
                    activeTask = null
                    val currentChapter = repository.loadProject(projectId)?.storyState?.nextChapter ?: 1
                    updateNotification(
                        title,
                        currentChapter,
                        if (requestedChapterCount > 1) "批次 $position/$requestedChapterCount · 准备章节任务" else "准备章节任务",
                    )
                    armChapterTimeout()
                    if (retrySettlement) {
                        coordinator.retrySettlement(projectId, settlementRepairHint) { task, stage -> checkpoint(task, stage, debugFault) }
                    } else {
                        coordinator.generateNextChapter(projectId) { task, stage -> checkpoint(task, stage, debugFault) }
                    }
                },
                afterCommit = { _, _ ->
                    timeoutJob?.cancel()
                    jobStore.clear(projectId)
                    activeTask = null
                },
            )
            val result = batch.terminal
            when {
                result is S0GenerationResult.Committed && batch.completed == batch.requested -> {
                    val message = if (batch.requested == 1) {
                        "第 ${result.chapter.number} 章已提交"
                    } else {
                        val firstChapter = result.chapter.number - batch.completed + 1
                        "已顺序提交第 $firstChapter 至 ${result.chapter.number} 章（${batch.completed}/${batch.requested}）"
                    }
                    S3GenerationRuntime.update(S3GenerationUiState.Finished(projectId, message))
                }
                result is S0GenerationResult.ReadableDraft -> {
                    persistFailure(result.reason, outcomeKnown = result.reason.hasKnownOutcome())
                    S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, result.reason))
                }
                result is S0GenerationResult.IncompleteDraft -> {
                    persistFailure(result.reason, outcomeKnown = result.reason.hasKnownOutcome())
                    S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, result.reason))
                }
                result is S0GenerationResult.Rejected -> {
                    persistFailure(result.reason, outcomeKnown = result.reason.hasKnownOutcome())
                    S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, result.reason))
                }
            }
        } catch (cancelled: CancellationException) {
            val code = cancellationCode ?: "USER_CANCELLED"
            if (code != "PROJECT_DISCARDED") {
                persistFailure(code, outcomeKnown = false)
                S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, code))
            }
        } catch (failure: Throwable) {
            persistFailure("REQUEST_OUTCOME_UNKNOWN", outcomeKnown = false)
            S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(projectId, "REQUEST_OUTCOME_UNKNOWN"))
        } finally {
            timeoutJob?.cancel()
            activeTask = null
            provider.unlockProfile()
            val chapter = repository.loadProject(projectId)?.storyState?.nextChapter ?: 1
            updateNotification(title, chapter, "任务已停止")
            cancellationCode = null
            activeProviderProfileId = null
        }
    }

    private fun armChapterTimeout() {
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(APP_CHAPTER_TIMEOUT_MILLIS)
            if (generationJob?.isActive == true) cancelActive("APP_CHAPTER_TIMEOUT")
        }
    }

    private fun checkpoint(task: S0ChapterTask, stage: S3JobStage, debugFault: String? = null) {
        activeTask = task
        val now = Instant.now().toString()
        val previous = jobStore.load(task.projectId)
        val purpose = when {
            stage <= S3JobStage.PROSE_REQUEST -> S3JobPurpose.PROSE
            settlementRepair -> S3JobPurpose.SETTLEMENT_REPAIR
            else -> S3JobPurpose.SETTLEMENT
        }
        val requestId = when (stage) {
            S3JobStage.PROSE_REQUEST -> S1RequestIds.prose(task.taskId)
            S3JobStage.SETTLEMENT_REQUEST -> S1RequestIds.settlement(task.taskId)
            else -> null
        }
        val job = S3GenerationJob(
            jobId = previous?.jobId ?: "job_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            projectId = task.projectId,
            chapter = task.chapter,
            purpose = purpose,
            stage = stage,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            attempt = activeAttempt,
            promptTemplateId = if (purpose == S3JobPurpose.PROSE) "chapter-prose-v1" else "chapter-settlement-v1",
            requestId = requestId,
            taskId = task.taskId,
            commitId = previous?.commitId,
            draftPath = if (stage >= S3JobStage.PROSE_SAVED) "chapters/${task.chapter.toString().padStart(6, '0')}.md" else null,
            lastErrorCode = null,
            outcomeKnown = stage !in setOf(S3JobStage.PROSE_REQUEST, S3JobStage.SETTLEMENT_REQUEST),
            providerProfileId = activeProviderProfileId,
        )
        jobStore.save(job)
        S3GenerationRuntime.update(
            S3GenerationUiState.Running(
                projectId = task.projectId,
                chapter = task.chapter,
                stage = stage,
                batchPosition = activeBatchPosition,
                batchTotal = activeBatchTotal,
            ),
        )
        val title = repository.loadProject(task.projectId)?.project?.title ?: "织卷"
        val batchPrefix = if (activeBatchTotal > 1) "批次 $activeBatchPosition/$activeBatchTotal · " else ""
        updateNotification(title, task.chapter, "$batchPrefix${stage.label()}")
        if (debugFault == DEBUG_FAULT_KILL_AFTER_PROSE && stage == S3JobStage.PROSE_SAVED) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun persistFailure(code: String, outcomeKnown: Boolean) {
        val projectId = activeProjectId ?: return
        val current = jobStore.load(projectId) ?: return
        jobStore.save(current.copy(updatedAt = Instant.now().toString(), lastErrorCode = code, outcomeKnown = outcomeKnown))
    }

    private fun cancelActive(code: String) {
        cancellationCode = code
        activeTask?.let { task ->
            provider.cancel(S1RequestIds.prose(task.taskId))
            provider.cancel(S1RequestIds.settlement(task.taskId))
        }
        persistFailure(code, outcomeKnown = false)
        generationJob?.cancel()
        activeProjectId?.let { S3GenerationRuntime.update(S3GenerationUiState.NeedsAction(it, code)) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "章节生成", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示当前章节的正文、结算与保存阶段"
                setSound(null, null)
            },
        )
    }

    private fun notification(title: String, chapter: Int, stage: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, S3GenerationForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_stories)
            .setContentTitle("$title · 第 $chapter 章")
            .setContentText(stage)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "停止", cancelIntent).build())
            .build()
    }

    private fun updateNotification(title: String, chapter: Int, stage: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, chapter, stage))
    }
}

private fun S3JobStage.label(): String = when (this) {
    S3JobStage.PREPARE -> "准备章节任务"
    S3JobStage.PROSE_REQUEST -> "正在生成正文"
    S3JobStage.PROSE_SAVED -> "正文已保存"
    S3JobStage.SETTLEMENT_REQUEST -> "正在结构化结算"
    S3JobStage.VALIDATE -> "正在本地校验"
    S3JobStage.COMMIT -> "正在安全提交"
    S3JobStage.DONE -> "章节已完成"
}

private fun String.hasKnownOutcome(): Boolean =
    !contains("REQUEST_OUTCOME_UNKNOWN") && !contains("USER_CANCELLED") && !contains("TIMEOUT")

internal const val ACTION_GENERATE = "app.zhijuan.reader.action.GENERATE"
internal const val ACTION_RETRY_SETTLEMENT = "app.zhijuan.reader.action.RETRY_SETTLEMENT"
internal const val ACTION_CANCEL = "app.zhijuan.reader.action.CANCEL_GENERATION"
internal const val ACTION_DISCARD_PROJECT = "app.zhijuan.reader.action.DISCARD_PROJECT"
internal const val EXTRA_PROJECT_ID = "projectId"
internal const val EXTRA_CHAPTER_COUNT = "chapterCount"
internal const val EXTRA_DEBUG_FAULT = "debugFault"
internal const val DEBUG_FAULT_KILL_AFTER_PROSE = "kill-after-prose"
internal const val DEBUG_FAULT_BAD_SETTLEMENT_BEFORE_CALL = "bad-settlement-before-call"
private const val CHANNEL_ID = "zhijuan_generation"
private const val NOTIFICATION_ID = 301
private const val APP_CHAPTER_TIMEOUT_MILLIS = 20L * 60L * 1_000L
