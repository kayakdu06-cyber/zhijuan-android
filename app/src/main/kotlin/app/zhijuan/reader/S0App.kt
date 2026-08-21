package app.zhijuan.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0GenerationCoordinator
import app.zhijuan.core.s0.S0GenerationResult
import app.zhijuan.core.s0.S0NovelRepository
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0TextGenerationProvider
import app.zhijuan.core.s0.S0WritingSkillImport
import app.zhijuan.core.s0.S0WritingSkillStatus
import app.zhijuan.core.s0.S2PlanWindow
import app.zhijuan.core.s0.S3JobStage
import app.zhijuan.core.s0.S3RecoveryAction
import app.zhijuan.core.s0.S3RecoveryDecision
import app.zhijuan.data.s0.S4ProjectArchive
import app.zhijuan.data.s0.S5WritingSkillParser
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class S0Route {
    CONNECT_SETTINGS,
    LIBRARY_CREATE,
    READER,
    GENERATION,
}

internal data class S0NavigationState(
    val route: S0Route,
    val backStack: List<S0Route> = emptyList(),
) {
    fun forward(target: S0Route): S0NavigationState = when {
        target == route -> this
        backStack.lastOrNull() == target -> copy(route = target, backStack = backStack.dropLast(1))
        else -> copy(route = target, backStack = (backStack + route).takeLast(MAX_BACK_STACK_DEPTH))
    }

    fun back(fallback: S0Route = S0Route.LIBRARY_CREATE): S0NavigationState = backStack.lastOrNull()?.let { parent ->
        copy(route = parent, backStack = backStack.dropLast(1))
    } ?: copy(route = fallback, backStack = emptyList())

    fun topLevel(target: S0Route): S0NavigationState = copy(route = target, backStack = emptyList())

    private companion object {
        const val MAX_BACK_STACK_DEPTH = 12
    }
}

private const val CREATE_WRITING_SKILL_TARGET = "__create_project_writing_skill__"
private val WRITING_SKILL_MIME_TYPES = arrayOf(
    "text/markdown",
    "text/plain",
    "application/json",
    "application/octet-stream",
)

@Composable
fun ZhijuanS0App(
    repository: S0NovelRepository,
    provider: S0TextGenerationProvider,
    generationController: S3GenerationController? = null,
    initialRecoveryDecisions: Map<String, S3RecoveryDecision> = emptyMap(),
    projectArchive: S4ProjectArchive? = null,
) {
    var route by rememberSaveable {
        mutableStateOf(
            if (provider.connectionSummary() == null) S0Route.CONNECT_SETTINGS else S0Route.LIBRARY_CREATE,
        )
    }
    var routeBackStack by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var projects by remember { mutableStateOf<List<S0ProjectSnapshot>>(emptyList()) }
    var snapshot by remember { mutableStateOf<S0ProjectSnapshot?>(null) }
    var recoveryActions by remember {
        mutableStateOf(initialRecoveryDecisions.mapValues { it.value.action })
    }
    var showCreateProject by rememberSaveable { mutableStateOf(false) }
    var showPlanRefresh by rememberSaveable { mutableStateOf(false) }
    var providerConfigured by remember { mutableStateOf(provider.connectionSummary() != null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var generationBusy by rememberSaveable { mutableStateOf(false) }
    var pendingGenerationProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGenerationCount by rememberSaveable { mutableIntStateOf(1) }
    var pendingExportProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveBusy by rememberSaveable { mutableStateOf(false) }
    var pendingWritingSkillTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var createWritingSkillCandidate by remember { mutableStateOf<S0WritingSkillImport?>(null) }
    var writingSkillProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var writingSkillCandidate by remember { mutableStateOf<S0WritingSkillImport?>(null) }
    var writingSkillError by rememberSaveable { mutableStateOf<String?>(null) }
    var contentScaleProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var plotPaceProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val coordinator = remember(repository, provider) { S0GenerationCoordinator(repository, provider) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val readerPreferencesStore = remember(context) { S3ReaderPreferencesStore(context) }
    val readerPositionStore = remember(context) { S5ReaderPositionStore(context) }
    var readerPreferences by remember { mutableStateOf(readerPreferencesStore.load()) }
    val serviceStatus by S3GenerationRuntime.status.collectAsState()

    fun navigationState() = S0NavigationState(
        route = route,
        backStack = routeBackStack.mapNotNull { saved -> runCatching { S0Route.valueOf(saved) }.getOrNull() },
    )

    fun applyNavigation(next: S0NavigationState) {
        route = next.route
        routeBackStack = ArrayList(next.backStack.map(S0Route::name))
    }

    fun navigateForward(target: S0Route) = applyNavigation(navigationState().forward(target))
    fun navigateBack() = applyNavigation(navigationState().back())
    fun navigateTopLevel(target: S0Route) = applyNavigation(navigationState().topLevel(target))

    fun refreshProjects(preferredProjectId: String? = snapshot?.project?.id) {
        val loaded = repository.listProjects()
        projects = loaded
        snapshot = loaded.firstOrNull { it.project.id == preferredProjectId } ?: loaded.firstOrNull()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingGenerationProjectId?.let { projectId -> generationController?.generateChapters(projectId, pendingGenerationCount) }
        if (!granted) message = "通知权限未开启；生成仍会运行，请留在应用内查看状态"
        pendingGenerationProjectId = null
        pendingGenerationCount = 1
    }
    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val projectId = pendingExportProjectId
        pendingExportProjectId = null
        if (uri == null || projectId == null || projectArchive == null) return@rememberLauncherForActivityResult
        archiveBusy = true
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        projectArchive.export(projectId, output)
                    } ?: error("EXPORT_DESTINATION_UNAVAILABLE")
                }
                "备份已导出"
            }.getOrElse { "导出失败，项目仍安全保留在本机" }
            archiveBusy = false
        }
    }
    val openBackupDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || projectArchive == null) return@rememberLauncherForActivityResult
        archiveBusy = true
        scope.launch {
            message = runCatching {
                val imported = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(projectArchive::import)
                        ?: error("IMPORT_SOURCE_UNAVAILABLE")
                }
                refreshProjects(imported.projectId)
                "备份已安全导入，可继续阅读和生成"
            }.getOrElse { "导入失败：备份无效或不安全，未写入项目" }
            archiveBusy = false
        }
    }
    val openWritingSkillDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingWritingSkillTarget
        pendingWritingSkillTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        S5WritingSkillParser().parse(fileName, input)
                    } ?: error("WRITING_SKILL_SOURCE_UNAVAILABLE")
                }
            }
            parsed.onSuccess { imported ->
                writingSkillError = null
                if (target == CREATE_WRITING_SKILL_TARGET) {
                    createWritingSkillCandidate = imported
                } else {
                    writingSkillProjectId = target
                    writingSkillCandidate = imported
                }
            }.onFailure { failure ->
                writingSkillError = writingSkillErrorMessage(failure.message.orEmpty())
            }
        }
    }

    fun startVisibleGeneration(projectId: String, chapterCount: Int) {
        if (generationController == null) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingGenerationProjectId = projectId
            pendingGenerationCount = chapterCount
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            generationController.generateChapters(projectId, chapterCount)
        }
    }

    LaunchedEffect(repository) {
        repository.recoverPendingCommits()
        refreshProjects()
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }
    LaunchedEffect(serviceStatus) {
        val projectId = when (val status = serviceStatus) {
            S3GenerationUiState.Idle -> null
            is S3GenerationUiState.Running -> status.projectId
            is S3GenerationUiState.NeedsAction -> status.projectId
            is S3GenerationUiState.Finished -> status.projectId
        }
        projectId?.let { refreshProjects(it) }
        when (val status = serviceStatus) {
            is S3GenerationUiState.Finished -> {
                recoveryActions = recoveryActions - status.projectId
                if (repository.loadProject(status.projectId) == null) {
                    readerPositionStore.remove(status.projectId)
                    if (contentScaleProjectId == status.projectId) contentScaleProjectId = null
                    if (plotPaceProjectId == status.projectId) plotPaceProjectId = null
                    if (writingSkillProjectId == status.projectId) writingSkillProjectId = null
                    if (pendingExportProjectId == status.projectId) pendingExportProjectId = null
                }
                message = status.message
            }
            is S3GenerationUiState.NeedsAction -> {
                if (status.code.startsWith("PROJECT_DELETE")) {
                    message = when {
                        status.code.contains("OTHER_JOB_ACTIVE") -> "另一部书正在生成，请完成或停止后再删除"
                        else -> "删除失败：本地文件未完整移除，请重试"
                    }
                } else {
                    recoveryActions = recoveryActions + (
                        status.projectId to when {
                            status.code.contains("ATTEMPT_LIMIT_REACHED") -> S3RecoveryAction.REVIEW_DRAFT
                            status.code.contains("SETTLEMENT") -> S3RecoveryAction.RETRY_SETTLEMENT
                            status.code.contains("UNKNOWN") -> S3RecoveryAction.CONFIRM_RESEND
                            status.code.contains("CONTENT_FILTERED") -> S3RecoveryAction.REVIEW_DRAFT
                            else -> S3RecoveryAction.RETRY_PROSE
                        }
                    )
                    message = recoveryMessage(status.code)
                }
            }
            else -> Unit
        }
    }

    BackHandler(enabled = route == S0Route.READER) {
        navigateBack()
    }
    BackHandler(enabled = route == S0Route.GENERATION) {
        navigateBack()
    }

    ZhijuanS0Theme(readerPreferences.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (route == S0Route.LIBRARY_CREATE || route == S0Route.CONNECT_SETTINGS) {
                    S0BottomNavigation(route = route, onRoute = ::navigateTopLevel)
                }
            },
        ) { innerPadding ->
            when (route) {
                S0Route.CONNECT_SETTINGS -> S1ProviderSettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    provider = provider,
                    onSaved = {
                        providerConfigured = provider.connectionSummary() != null
                    },
                    profilesLocked = serviceStatus is S3GenerationUiState.Running,
                    footer = {
                        ReaderSettingsSection(
                            preferences = readerPreferences,
                            onChange = { updated ->
                                readerPreferences = updated
                                readerPreferencesStore.save(updated)
                            },
                        )
                    },
                )
                S0Route.LIBRARY_CREATE -> S5LibraryScreen(
                    modifier = Modifier.padding(innerPadding),
                    projects = projects,
                    activeProjectId = snapshot?.project?.id,
                    runningProjectId = (serviceStatus as? S3GenerationUiState.Running)?.projectId,
                    recoveryActions = recoveryActions,
                    archiveBusy = archiveBusy,
                    onCreate = {
                        createWritingSkillCandidate = null
                        writingSkillError = null
                        showCreateProject = true
                    },
                    onSelect = { projectId -> snapshot = repository.loadProject(projectId) },
                    onGenerate = { projectId ->
                        snapshot = repository.loadProject(projectId)
                        if (providerConfigured) {
                            navigateForward(S0Route.GENERATION)
                        } else {
                            navigateTopLevel(S0Route.CONNECT_SETTINGS)
                            message = "请先测试并保存 Provider"
                        }
                    },
                    onRead = { projectId ->
                        snapshot = repository.loadProject(projectId)
                        navigateForward(S0Route.READER)
                    },
                    onExport = projectArchive?.let {
                        { projectId ->
                            repository.loadProject(projectId)?.let { current ->
                                pendingExportProjectId = projectId
                                createBackupDocument.launch("${safeBackupName(current.project.title)}.zhijuan.zip")
                            }
                        }
                    },
                    onImport = projectArchive?.let {
                        { openBackupDocument.launch(arrayOf("application/zip", "application/octet-stream")) }
                    },
                    onDelete = { projectId ->
                        val running = serviceStatus as? S3GenerationUiState.Running
                        when {
                            running != null && running.projectId != projectId -> {
                                message = "另一部书正在生成，请完成或停止后再删除"
                            }
                            generationController != null -> generationController.discardProject(projectId)
                            else -> {
                                message = runCatching {
                                    check(repository.discardProject(projectId)) { "PROJECT_DELETE_FAILED" }
                                    readerPositionStore.remove(projectId)
                                    recoveryActions = recoveryActions - projectId
                                    refreshProjects(preferredProjectId = null)
                                    "项目已从本机删除；API 配置与其他书未改变"
                                }.getOrElse { "删除失败：本地文件未完整移除，请重试" }
                            }
                        }
                    },
                    onManageWritingSkill = { projectId ->
                        writingSkillProjectId = projectId
                        writingSkillCandidate = null
                        writingSkillError = null
                    },
                    onManageContentScale = { projectId -> contentScaleProjectId = projectId },
                    onManagePlotPace = { projectId -> plotPaceProjectId = projectId },
                )
                S0Route.GENERATION -> GenerationScreen(
                    modifier = Modifier.padding(innerPadding),
                    snapshot = snapshot,
                    isBusy = generationBusy || serviceStatus is S3GenerationUiState.Running,
                    serviceStatus = serviceStatus,
                    recoveryAction = snapshot?.project?.id
                        ?.let(recoveryActions::get)
                        ?: S3RecoveryAction.NONE,
                    providerSummary = provider.connectionSummary(),
                    onBack = ::navigateBack,
                    onGenerate = { chapterCount ->
                        val projectId = snapshot?.project?.id ?: return@GenerationScreen
                        if (generationController != null) {
                            startVisibleGeneration(projectId, chapterCount)
                        } else if (!generationBusy) {
                            generationBusy = true
                            scope.launch {
                                try {
                                    val batch = runS3SequentialBatch(
                                        requested = chapterCount,
                                        generateChapter = { coordinator.generateNextChapter(projectId) },
                                    )
                                    refreshProjects(projectId)
                                    when (val result = batch.terminal) {
                                        is S0GenerationResult.Committed -> message = if (batch.completed == 1) {
                                            "正文已保存，结算已幂等提交"
                                        } else {
                                            "已顺序完成 ${batch.completed} 章；每章正文与结算均独立提交"
                                        }
                                        is S0GenerationResult.ReadableDraft -> message = "正文已保存，可阅读；结算待处理"
                                        is S0GenerationResult.IncompleteDraft -> message = incompleteDraftMessage(result.reason)
                                        is S0GenerationResult.Rejected -> message = "生成未完成：${result.reason}"
                                    }
                                } finally {
                                    generationBusy = false
                                }
                            }
                        }
                    },
                    onRetrySettlement = {
                        snapshot?.project?.id?.let { generationController?.retrySettlement(it) }
                    },
                    onCancel = { generationController?.cancel() },
                    onRefreshPlan = { showPlanRefresh = true },
                    onRead = {
                        navigateForward(S0Route.READER)
                    },
                )
                S0Route.READER -> ReaderScreen(
                    modifier = Modifier.padding(innerPadding),
                    snapshot = snapshot,
                    preferences = readerPreferences,
                    initialPosition = snapshot?.project?.id?.let(readerPositionStore::load),
                    initialOffsetForChapter = { chapterNumber ->
                        snapshot?.project?.id
                            ?.let { readerPositionStore.loadChapterOffset(it, chapterNumber) }
                            ?: 0
                    },
                    onPositionChange = { position ->
                        snapshot?.project?.id?.let { readerPositionStore.save(it, position) }
                    },
                    onBack = ::navigateBack,
                    onReturnToCreation = { navigateForward(S0Route.GENERATION) },
                    generationInProgress = serviceStatus is S3GenerationUiState.Running,
                    onOpenGeneration = {
                        navigateForward(S0Route.GENERATION)
                    },
                    onContinueWriting = { chapterCount ->
                        val projectId = snapshot?.project?.id ?: return@ReaderScreen
                        if (providerConfigured) {
                            navigateForward(S0Route.GENERATION)
                            startVisibleGeneration(projectId, chapterCount)
                        } else {
                            navigateTopLevel(S0Route.CONNECT_SETTINGS)
                            message = "请先添加并验证一个 API 配置"
                        }
                    },
                )
            }
        }
        if (showCreateProject) {
            S5CreateProjectSheet(
                onDismiss = { showCreateProject = false },
                writingSkill = createWritingSkillCandidate,
                writingSkillError = writingSkillError,
                onChooseWritingSkill = {
                    pendingWritingSkillTarget = CREATE_WRITING_SKILL_TARGET
                    openWritingSkillDocument.launch(WRITING_SKILL_MIME_TYPES)
                },
                onUpdateWritingSkill = { createWritingSkillCandidate = it },
                onRemoveWritingSkill = {
                    createWritingSkillCandidate = null
                    writingSkillError = null
                },
                onConfirm = { draft ->
                    message = runCatching {
                        repository.createProject(draft.project, draft.plan, draft.writingSkill)
                        refreshProjects(draft.project.id)
                        showCreateProject = false
                        createWritingSkillCandidate = null
                        "小说已保存到本机"
                    }.getOrElse { "创建失败：请检查五项文字内容后重试" }
                },
            )
        }
        writingSkillProjectId?.let { projectId ->
            repository.loadProject(projectId)?.let { current ->
                S5WritingSkillSheet(
                    projectTitle = current.project.title,
                    current = current.writingSkill,
                    candidate = writingSkillCandidate,
                    error = writingSkillError,
                    onDismiss = {
                        writingSkillProjectId = null
                        writingSkillCandidate = null
                        writingSkillError = null
                    },
                    onChoose = {
                        pendingWritingSkillTarget = projectId
                        openWritingSkillDocument.launch(WRITING_SKILL_MIME_TYPES)
                    },
                    onApply = { imported ->
                        message = runCatching {
                            repository.saveWritingSkill(projectId, imported)
                            refreshProjects(projectId)
                            writingSkillProjectId = null
                            writingSkillCandidate = null
                            "创作质量卡已应用；后续正文请求会携带其哈希"
                        }.getOrElse { "应用失败：生成或安全提交期间不能替换质量卡" }
                    },
                    onRemove = {
                        message = runCatching {
                            repository.removeWritingSkill(projectId)
                            refreshProjects(projectId)
                            writingSkillProjectId = null
                            writingSkillCandidate = null
                            "创作 Skill 已移除；后续使用织卷默认质量卡"
                        }.getOrElse { "移除失败：生成或安全提交期间不能修改质量卡" }
                    },
                    onDiscardCandidate = {
                        writingSkillCandidate = null
                        writingSkillError = null
                    },
                )
            }
        }
        contentScaleProjectId?.let { projectId ->
            repository.loadProject(projectId)?.let { current ->
                S5ContentScaleSheet(
                    projectTitle = current.project.title,
                    current = current.project.contentScale,
                    onDismiss = { contentScaleProjectId = null },
                    onSave = { selected ->
                        message = runCatching {
                            repository.saveContentScale(projectId, selected)
                            refreshProjects(projectId)
                            contentScaleProjectId = null
                            "叙事尺度已更新为${selected.displayName()}"
                        }.getOrElse { "保存失败：生成或安全提交期间不能修改叙事尺度" }
                    },
                )
            }
        }
        plotPaceProjectId?.let { projectId ->
            repository.loadProject(projectId)?.let { current ->
                S5PlotPaceSheet(
                    projectTitle = current.project.title,
                    current = current.project.plotPace,
                    onDismiss = { plotPaceProjectId = null },
                    onSave = { selected ->
                        message = runCatching {
                            repository.savePlotPace(projectId, selected)
                            refreshProjects(projectId)
                            plotPaceProjectId = null
                            "剧情节奏已更新为${selected.displayName()}"
                        }.getOrElse { "保存失败：生成或安全提交期间不能修改剧情节奏" }
                    },
                )
            }
        }
        if (showPlanRefresh) {
            snapshot?.let { current ->
                S5PlanRefreshSheet(
                    snapshot = current,
                    onDismiss = { showPlanRefresh = false },
                    onConfirm = { plan ->
                        message = runCatching {
                            repository.replacePlan(current.project.id, current.storyState.revision, plan)
                            refreshProjects(current.project.id)
                            showPlanRefresh = false
                            "后续 8 章文字计划已确认"
                        }.getOrElse { "计划刷新失败：项目状态已变化，请重新打开" }
                    },
                )
            }
        }
    }
}

@Composable
private fun S0BottomNavigation(route: S0Route, onRoute: (S0Route) -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        NavigationBar(
            modifier = Modifier
                .height(80.dp)
                .navigationBarsPadding()
                .padding(top = 6.dp)
                .testTag("bottom-navigation"),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            listOf(
                S0Route.LIBRARY_CREATE to ("书库" to R.drawable.ic_menu_book),
                S0Route.CONNECT_SETTINGS to ("设置" to R.drawable.ic_settings),
            ).forEach { (target, labelAndIcon) ->
                val (label, icon) = labelAndIcon
                val isSelected = route == target
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onRoute(target) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = label,
                                modifier = Modifier.size(24.dp),
                            )
                            Box(
                                Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    },
                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier
                        .testTag("bottom-navigation-${target.name.lowercase()}")
                        .semantics { selected = isSelected },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun LibraryScreen(
    modifier: Modifier,
    snapshot: S0ProjectSnapshot?,
    onCreate: () -> Unit,
    onGenerate: () -> Unit,
    onRead: () -> Unit,
    archiveBusy: Boolean = false,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.zhijuan_logo_draft),
                contentDescription = "织卷标志",
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("织卷", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("把下一章留在本机", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        if (snapshot == null) {
            Text("还没有本地项目", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("创建一部本地小说，正文、章节状态和备份都只保存在你的设备上。")
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                Text("创建小说")
            }
            onImport?.let { importBackup ->
                OutlinedButton(
                    onClick = importBackup,
                    enabled = !archiveBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("library-import"),
                ) {
                    Text(if (archiveBusy) "正在处理备份…" else "导入备份")
                }
            }
        } else {
            Text("本地书库", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            ProjectCard(snapshot)
            if (snapshot.chapters.isNotEmpty()) {
                Button(onClick = onRead, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Text("继续阅读")
                }
                OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Text("生成下一章")
                }
            } else {
                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Text("生成第一章")
                }
            }
            if (onExport != null || onImport != null) {
                HorizontalDivider()
                Text("本地备份", style = MaterialTheme.typography.titleMedium)
                Text("备份只包含小说项目文件，不包含 API Key、活动请求或诊断内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                onExport?.let { exportBackup ->
                    OutlinedButton(
                        onClick = exportBackup,
                        enabled = !archiveBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("library-export"),
                    ) {
                        Text(if (archiveBusy) "正在处理备份…" else "导出项目备份")
                    }
                }
                onImport?.let { importBackup ->
                    TextButton(
                        onClick = importBackup,
                        enabled = !archiveBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("library-import"),
                    ) {
                        Text("导入其他备份")
                    }
                }
            }
        }
    }
}

private fun safeBackupName(title: String): String = title
    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .trim()
    .ifBlank { "织卷项目" }
    .take(60)

@Composable
private fun ProjectCard(snapshot: S0ProjectSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(snapshot.project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${snapshot.project.genre} · ${snapshot.project.protagonist}")
            Text("已完成 ${snapshot.storyState.committedChapters.size} 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
            snapshot.chapters.lastOrNull()?.let { chapter ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${chapter.number} 章", color = MaterialTheme.colorScheme.primary)
                    Text(chapter.state.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LegacyGenerationScreen(
    modifier: Modifier,
    snapshot: S0ProjectSnapshot?,
    isBusy: Boolean,
    serviceStatus: S3GenerationUiState = S3GenerationUiState.Idle,
    recoveryAction: S3RecoveryAction = S3RecoveryAction.NONE,
    onGenerate: (chapterCount: Int) -> Unit,
    onRetrySettlement: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRefreshPlan: () -> Unit = {},
    onRead: () -> Unit,
) {
    val chapter = snapshot?.chapters?.lastOrNull()
    val currentPlan = snapshot?.plan?.firstOrNull()
    val nextChapter = snapshot?.storyState?.nextChapter ?: 1
    var showDirectory by rememberSaveable { mutableStateOf(false) }
    var requestedChapterCount by rememberSaveable(snapshot?.project?.id) { mutableIntStateOf(1) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditorialBrandBar(
            title = snapshot?.project?.title ?: "创作",
            actionIcon = snapshot?.let { R.drawable.ic_more_vert },
            actionDescription = snapshot?.let { "打开章节目录" },
            onAction = snapshot?.let { { showDirectory = true } },
        )
        HorizontalDivider()
        if (snapshot == null) {
            EditorialSectionHeader("创作手稿")
            Text("请先在书库创建项目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val availableBatchCount = minOf(3, snapshot.plan.size).coerceAtLeast(1)
        LaunchedEffect(availableBatchCount) {
            requestedChapterCount = requestedChapterCount.coerceAtMost(availableBatchCount)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "创作手稿",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif),
            )
            Text(" · ", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                "%02d / %02d 章".format(
                    snapshot.storyState.committedChapters.size,
                    snapshot.storyState.committedChapters.size + snapshot.plan.size,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialSerif),
            )
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EditorialWorkflowRail(
                hasPlan = snapshot.plan.isNotEmpty(),
                chapter = nextChapter,
                modifier = Modifier.width(76.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "第 $nextChapter 章",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif),
                )
                Text(
                    currentPlan?.title ?: chapter?.title ?: "等待章节计划",
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = EditorialSerif),
                )
                Text("当前任务", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Text(
                    currentPlan?.goal ?: "当前计划窗口已用完",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif),
                )
                when (snapshot.writingSkill.status) {
                    S0WritingSkillStatus.ACTIVE -> {
                        val card = requireNotNull(snapshot.writingSkill.qualityCard)
                        Text(
                            "本章使用：${card.name} · v${card.version} · ${card.sha256.take(8)}",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("generation-quality-card"),
                        )
                    }
                    S0WritingSkillStatus.DISABLED_CORRUPT -> Text(
                        "创作 Skill 已损坏并安全禁用；本章使用织卷默认质量卡。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("generation-quality-card-disabled"),
                    )
                    S0WritingSkillStatus.NONE -> Text(
                        "本章使用：织卷默认质量卡",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("generation-quality-card-default"),
                    )
                }
                Text("关键线索", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                listOfNotNull(
                    currentPlan?.entryState,
                    currentPlan?.mustChange,
                    currentPlan?.exitHook,
                ).filter(String::isNotBlank).take(3).forEachIndexed { index, fact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "%02d".format(index + 1),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                        )
                        Text(
                            fact,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                }
                Text(
                    "已完成 ${snapshot.storyState.committedChapters.size} 章 · 计划 ${snapshot.plan.size} 章",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        if (chapter != null && chapter.state != S0ChapterState.COMMITTED) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("已保存正文 · ${chapter.state.label()}", color = MaterialTheme.colorScheme.primary)
                Text(
                    chapter.prose.take(220) + if (chapter.prose.length > 220) "…" else "",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                )
            }
        }

        when {
            isBusy -> {
                val running = serviceStatus as? S3GenerationUiState.Running
                val batchPrefix = running
                    ?.takeIf { it.batchTotal > 1 }
                    ?.let { "批次 ${it.batchPosition}/${it.batchTotal} · " }
                    .orEmpty()
                Text(
                    running?.stage?.let { "$batchPrefix${it.readerLabel()}" } ?: "正在生成，请稍候…",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditorialPrimaryButton("停止生成", onCancel, Modifier.fillMaxWidth())
            }
            (chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW) &&
                recoveryAction == S3RecoveryAction.REVIEW_DRAFT -> {
                Text("正文已保留。结算初次请求及两次显式重试均未通过，请先检查草稿或更换 API 配置。", color = MaterialTheme.colorScheme.error)
            }
            (chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW) &&
                recoveryAction == S3RecoveryAction.REVIEW_DRAFT -> {
                Text("正文已保留。结算初次请求及两次显式重试均未通过，请先检查草稿或更换 API 配置。", color = MaterialTheme.colorScheme.error)
            }
            chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW -> {
                Text("正文已保留。此操作只重试结构化结算，不会重新生成正文。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialPrimaryButton("只重试结算", onRetrySettlement, Modifier.fillMaxWidth())
            }
            chapter?.state == S0ChapterState.PAUSED -> {
                Text(
                    incompleteDraftMessage(chapter.incompleteReason.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                EditorialPrimaryButton(
                    label = "重新生成本章",
                    onClick = { onGenerate(1) },
                    icon = R.drawable.ic_edit,
                    modifier = Modifier.fillMaxWidth().testTag("generation-retry-incomplete"),
                )
            }
            recoveryAction == S3RecoveryAction.CONFIRM_RESEND -> {
                Text("上次请求结果无法确认。只有明确点击才会重新发送正文请求。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialPrimaryButton("确认重发正文", { onGenerate(1) }, Modifier.fillMaxWidth())
            }
            S2PlanWindow.needsExplicitRefresh(snapshot.plan.size) || snapshot.plan.isEmpty() -> {
                Text(
                    "滚动计划只剩 ${snapshot.plan.size} 章；织卷不会自动连写或隐藏刷新计划。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditorialPrimaryButton(
                    "扩展后续计划",
                    onRefreshPlan,
                    Modifier.fillMaxWidth().testTag("plan-refresh"),
                    enabled = !isBusy,
                )
            }
            else -> {
                S3SequentialBatchSelector(
                    selectedCount = requestedChapterCount,
                    availableCount = availableBatchCount,
                    onSelected = { requestedChapterCount = it },
                )
                EditorialPrimaryButton(
                    label = if (requestedChapterCount == 1) "继续写第 $nextChapter 章" else "顺序续写 $requestedChapterCount 章",
                    onClick = { onGenerate(requestedChapterCount) },
                    icon = R.drawable.ic_edit,
                    modifier = Modifier.fillMaxWidth().testTag("generation-primary"),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorialSecondaryButton(
                label = "查看目录",
                onClick = { showDirectory = true },
                icon = R.drawable.ic_list,
                modifier = Modifier.weight(1f).testTag("generation-directory"),
            )
            if (chapter != null) {
                EditorialSecondaryButton(
                    label = if (chapter.state == S0ChapterState.COMMITTED) "继续阅读" else "阅读草稿",
                    onClick = onRead,
                    icon = R.drawable.ic_menu_book,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDirectory && snapshot != null) {
        val needsRefresh = S2PlanWindow.needsExplicitRefresh(snapshot.plan.size) || snapshot.plan.isEmpty()
        EditorialChapterDirectorySheet(
            snapshot = snapshot,
            selectedChapterNumber = chapter?.number,
            onDismiss = { showDirectory = false },
            onChapterSelected = null,
            primaryLabel = if (needsRefresh) "扩展后续计划" else "写第 $nextChapter 章",
            onPrimaryAction = {
                showDirectory = false
                if (needsRefresh) onRefreshPlan() else onGenerate(requestedChapterCount)
            },
            secondaryLabel = chapter?.let { "继续阅读第 ${it.number} 章" },
            onSecondaryAction = chapter?.let { { showDirectory = false; onRead() } },
            primaryEnabled = !isBusy,
        )
    }
}

@Composable
internal fun GenerationScreen(
    modifier: Modifier,
    snapshot: S0ProjectSnapshot?,
    isBusy: Boolean,
    serviceStatus: S3GenerationUiState = S3GenerationUiState.Idle,
    recoveryAction: S3RecoveryAction = S3RecoveryAction.NONE,
    providerSummary: app.zhijuan.core.s0.S1ProviderSummary? = null,
    onBack: () -> Unit = {},
    onGenerate: (chapterCount: Int) -> Unit,
    onRetrySettlement: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRefreshPlan: () -> Unit = {},
    onRead: () -> Unit,
) {
    val chapter = snapshot?.chapters?.lastOrNull()
    val nextChapter = snapshot?.storyState?.nextChapter ?: 1
    var requestedChapterCount by rememberSaveable(snapshot?.project?.id) { mutableIntStateOf(1) }
    val availableBatchCount = minOf(3, snapshot?.plan?.size ?: 0).coerceAtLeast(1)
    LaunchedEffect(availableBatchCount) {
        requestedChapterCount = requestedChapterCount.coerceAtMost(availableBatchCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("generation-back")) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回上一层")
            }
            Text(
                snapshot?.project?.title ?: "续写",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        if (snapshot == null) {
            Text("没有可续写的作品", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = EditorialSerif))
            Text("请先从书库选择一本书。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Text("续写", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = EditorialSerif))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("第 $nextChapter 章", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = EditorialSerif))
                Text("已完成 ${snapshot.storyState.committedChapters.size} 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "当前 API：${providerSummary?.displayName ?: "尚未配置"}",
                    color = if (providerSummary == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("generation-provider-profile"),
                )
            }
        }

        if (chapter != null && chapter.state != S0ChapterState.COMMITTED) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第 ${chapter.number} 章", color = MaterialTheme.colorScheme.primary)
                    Text(chapter.state.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("正文已保存在本机，可阅读或按当前状态继续处理。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when {
            isBusy -> {
                val running = serviceStatus as? S3GenerationUiState.Running
                val batchPrefix = running?.takeIf { it.batchTotal > 1 }
                    ?.let { "${it.batchPosition}/${it.batchTotal} · " }.orEmpty()
                Text(
                    running?.stage?.let { "$batchPrefix${it.readerLabel()}" } ?: "正在生成，请稍候…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditorialPrimaryButton("停止生成", onCancel, Modifier.fillMaxWidth())
            }
            chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW -> {
                Text("正文已保留；这里只重新整理状态，不会重写正文。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialPrimaryButton("只重试结算", onRetrySettlement, Modifier.fillMaxWidth())
            }
            chapter?.state == S0ChapterState.PAUSED -> {
                Text(incompleteDraftMessage(chapter.incompleteReason.orEmpty()), color = MaterialTheme.colorScheme.error)
                EditorialPrimaryButton(
                    label = "重新生成第 ${chapter.number} 章",
                    onClick = { onGenerate(1) },
                    icon = R.drawable.ic_edit,
                    modifier = Modifier.fillMaxWidth().testTag("generation-retry-incomplete"),
                )
            }
            recoveryAction == S3RecoveryAction.CONFIRM_RESEND -> {
                Text("上次请求结果无法确认；只有明确点击才会重新发送。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialPrimaryButton("确认重发正文", { onGenerate(1) }, Modifier.fillMaxWidth())
            }
            S2PlanWindow.needsExplicitRefresh(snapshot.plan.size) || snapshot.plan.isEmpty() -> {
                Text("需要先在本机准备后续章节。这个操作不调用模型，也不会自动开始生成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorialPrimaryButton(
                    "准备后续章节",
                    onRefreshPlan,
                    Modifier.fillMaxWidth().testTag("plan-refresh"),
                    enabled = !isBusy,
                )
            }
            providerSummary == null -> {
                Text("请先到设置添加并验证一个 API。", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                S3SequentialBatchSelector(
                    selectedCount = requestedChapterCount,
                    availableCount = availableBatchCount,
                    onSelected = { requestedChapterCount = it },
                )
                EditorialPrimaryButton(
                    label = if (requestedChapterCount == 1) "续写第 $nextChapter 章" else "顺序续写 $requestedChapterCount 章",
                    onClick = { onGenerate(requestedChapterCount) },
                    icon = R.drawable.ic_edit,
                    modifier = Modifier.fillMaxWidth().testTag("generation-primary"),
                )
            }
        }
        if (chapter != null) {
            EditorialSecondaryButton(
                label = if (chapter.state == S0ChapterState.COMMITTED) "继续阅读" else "阅读已保存正文",
                onClick = onRead,
                icon = R.drawable.ic_menu_book,
                modifier = Modifier.fillMaxWidth().testTag("generation-read"),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun S3SequentialBatchSelector(
    selectedCount: Int,
    availableCount: Int,
    onSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag("generation-batch-selector"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本次续写", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("默认 1 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { count ->
                val selected = selectedCount == count
                val enabled = count <= availableCount
                OutlinedButton(
                    onClick = { onSelected(count) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { this.selected = selected }
                        .testTag("generation-batch-$count"),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text("$count 章", fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Text(
            "本次最多 ${selectedCount * 2} 次模型调用；每章仍先独立保存、结算并提交，再开始下一章。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("generation-batch-call-count"),
        )
        Text(
            "选择 2–3 章不会并行生成，但会减少中途审阅和调整后续方向的机会。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EditorialWorkflowRail(
    hasPlan: Boolean,
    chapter: Int,
    modifier: Modifier = Modifier,
) {
    val success = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) EditorialSuccessDark else EditorialSuccess
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EditorialWorkflowStep("01", "故事设定", complete = true, active = false, success = success)
        Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
        EditorialWorkflowStep("02", "章节计划", complete = hasPlan, active = false, success = success)
        Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
        EditorialWorkflowStep("03", "写第${chapter}章", complete = false, active = true, success = success)
    }
}

@Composable
private fun EditorialWorkflowStep(
    number: String,
    label: String,
    complete: Boolean,
    active: Boolean,
    success: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            number,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
        )
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
        if (complete) {
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = "$label，已完成",
                tint = success,
                modifier = Modifier.size(24.dp),
            )
        } else if (active) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = "$label，当前步骤",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun S3JobStage.readerLabel(): String = when (this) {
    S3JobStage.PREPARE -> "正在准备本章任务…"
    S3JobStage.PROSE_REQUEST -> "正在生成正文；离开应用也会继续…"
    S3JobStage.PROSE_SAVED -> "正文已保存，准备结算…"
    S3JobStage.SETTLEMENT_REQUEST -> "正文已保存，正在结构化结算…"
    S3JobStage.VALIDATE -> "正在本地检查连续性…"
    S3JobStage.COMMIT -> "正在安全提交本章…"
    S3JobStage.DONE -> "本章已完成"
}

private fun recoveryMessage(code: String): String = when {
    code.contains("SETTLEMENT_NOT_JSON") -> "正文已保存；结算没有返回可解析的 JSON，可能为空或夹带了其他文字"
    code.contains("SETTLEMENT_SCHEMA_INVALID") -> "正文已保存；结算 JSON 缺少必要字段或字段格式不正确"
    code.contains("SETTLEMENT_CONTRACT_INVALID") -> "正文已保存；结算返回的章节、任务或版本与当前草稿不一致"
    code.contains("ATTEMPT_LIMIT_REACHED") -> "正文已保存；结算初次请求及两次显式重试均失败，已停止继续请求"
    code.contains("SETTLEMENT") -> "正文已保存；结构化结算失败，请只重试结算"
    code.contains("TRUNCATED_LENGTH") || code.contains("LIMIT_EXCEEDED") -> "正文达到长度上限，已保存为未完成草稿"
    code.contains("CONTENT_FILTERED") -> "Provider 已停止此内容；片段已保存，不会结算"
    code.contains("RESOURCE_INTERRUPTED") -> "Provider 资源中断；片段已保存，不会结算"
    code.contains("USER_CANCELLED") -> "生成已停止；已保存内容不会删除"
    code.contains("REQUEST_OUTCOME_UNKNOWN") -> "请求结果无法确认，重发前需要明确确认"
    else -> "任务已停止：$code"
}

private fun incompleteDraftMessage(code: String): String = when {
    code.contains("TRUNCATED_LENGTH") || code.contains("LIMIT_EXCEEDED") ->
        "正文达到输出上限，当前片段已安全保留但不会结算。可明确重新生成本章。"
    code.contains("CONTENT_FILTERED") ->
        "Provider 因内容规则停止输出，当前片段不会结算。请使用符合 Provider 规则的表达后重新生成。"
    code.contains("RESOURCE_INTERRUPTED") ->
        "Provider 资源中断，当前片段已安全保留但不会结算。可稍后重新生成本章。"
    else -> "正文没有确认自然结束，当前片段已安全保留但不会结算。"
}

private fun writingSkillErrorMessage(code: String): String = when {
    code.contains("FORMAT_UNSUPPORTED") -> "只支持单个 .md 或 .json 文件"
    code.contains("UTF8_REQUIRED") || code.contains("BINARY_REJECTED") -> "文件必须是有效 UTF-8 纯文本"
    code.contains("SOURCE_TOO_LARGE") -> "文件超过 256 KiB（约 262 KB），请先删减"
    code.contains("TOO_MANY_RULES") || code.contains("CARD_TOO_LONG") -> "质量卡最多 8 条、合计 1600 字符，请先删减"
    code.contains("JSON_INVALID") -> "JSON 文件格式无效，请检查括号和引号"
    code.contains("UNSAFE") || code.contains("REFERENCE") || code.contains("HTML") ->
        "文件包含工具、文件、网络、外部引用或指令覆盖内容，未导入"
    code.contains("NO_SUPPORTED_RULES") -> "没有在受支持标题下找到可用列表规则"
    else -> "无法读取该 Skill；未保存任何内容"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReaderScreen(
    modifier: Modifier,
    snapshot: S0ProjectSnapshot?,
    preferences: S3ReaderPreferences = S3ReaderPreferences(),
    initialPosition: S5ReaderPosition? = null,
    initialOffsetForChapter: (Int) -> Int = { 0 },
    onPositionChange: (S5ReaderPosition) -> Unit = {},
    onBack: () -> Unit,
    onReturnToCreation: () -> Unit = onBack,
    generationInProgress: Boolean = false,
    onOpenGeneration: () -> Unit = onReturnToCreation,
    onContinueWriting: (Int) -> Unit = {},
) {
    val chapters = snapshot?.chapters.orEmpty().sortedBy { it.number }
    var selectedChapterNumber by rememberSaveable(snapshot?.project?.id) {
        mutableIntStateOf(
            initialPosition?.chapterNumber?.takeIf { saved -> chapters.any { it.number == saved } }
                ?: chapters.lastOrNull()?.number
                ?: -1,
        )
    }
    var showDirectory by rememberSaveable { mutableStateOf(false) }
    var showContinueSheet by rememberSaveable { mutableStateOf(false) }
    var continueCount by rememberSaveable(snapshot?.project?.id) { mutableIntStateOf(1) }
    var chromeVisible by rememberSaveable(snapshot?.project?.id) { mutableStateOf(true) }
    var forceTopChapter by rememberSaveable(snapshot?.project?.id) { mutableIntStateOf(-1) }
    BackHandler(enabled = showDirectory) { showDirectory = false }
    LaunchedEffect(chapters.map(S0Chapter::number)) {
        if (chapters.none { it.number == selectedChapterNumber }) {
            selectedChapterNumber = chapters.lastOrNull()?.number ?: -1
        }
    }
    val selectedIndex = chapters.indexOfFirst { it.number == selectedChapterNumber }
        .takeIf { it >= 0 }
        ?: chapters.lastIndex
    val chapter = chapters.getOrNull(selectedIndex)
    val chapterScrollState = key(chapter?.number) {
        rememberScrollState(
            initial = initialPosition
                ?.takeIf { it.chapterNumber == chapter?.number }
                ?.scrollOffset
                ?: chapter?.number?.let(initialOffsetForChapter)
                ?: 0,
        )
    }
    LaunchedEffect(chapter?.number, forceTopChapter, chapterScrollState) {
        val number = chapter?.number ?: return@LaunchedEffect
        if (forceTopChapter == number) {
            chapterScrollState.scrollTo(0)
            onPositionChange(S5ReaderPosition(number, 0))
            forceTopChapter = -1
        }
    }
    LaunchedEffect(chapter?.number, chapterScrollState) {
        val number = chapter?.number ?: return@LaunchedEffect
        snapshotFlow { chapterScrollState.value }
            .distinctUntilChanged()
            .collectLatest { offset ->
                delay(250)
                onPositionChange(S5ReaderPosition(number, offset))
            }
    }
    val nextChapter = chapters.getOrNull(selectedIndex + 1)
    val autoNextChapter = nextChapter?.takeIf { it.state == S0ChapterState.COMMITTED }
    val autoAdvanceThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val autoAdvanceScope = rememberCoroutineScope()
    var endOverscroll by remember(chapter?.number) { mutableFloatStateOf(0f) }
    var autoAdvanceRequested by remember(chapter?.number) { mutableStateOf(false) }
    val continuousReadingConnection = remember(chapter?.number, autoNextChapter?.number, autoAdvanceThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (chapterScrollState.value < chapterScrollState.maxValue) {
                    endOverscroll = 0f
                    return Offset.Zero
                }
                if (
                    source == NestedScrollSource.UserInput &&
                    available.y < 0f &&
                    autoNextChapter != null &&
                    !autoAdvanceRequested
                ) {
                    endOverscroll += -available.y
                    if (endOverscroll >= autoAdvanceThreshold) {
                        autoAdvanceRequested = true
                        autoAdvanceScope.launch {
                            chapter?.let { onPositionChange(S5ReaderPosition(it.number, chapterScrollState.maxValue)) }
                            forceTopChapter = autoNextChapter.number
                            selectedChapterNumber = autoNextChapter.number
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }
    val useDarkReader = readerUsesDarkTheme(preferences.theme)
    val readerBackground = if (useDarkReader) MaterialTheme.colorScheme.background else ReaderPaperLight
    ReaderSystemBars(readerBackground, useDarkReader)

    Box(modifier = modifier.fillMaxSize().background(readerBackground)) {
        if (chapter == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有可读章节", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = EditorialSerif))
                Text("完成首章后，正文会出现在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reader-content-toggle")
                    .pointerInput(chapter.number) {
                        detectTapGestures { position ->
                            val safeHorizontal = position.x in size.width * 0.2f..size.width * 0.8f
                            val safeVertical = position.y in size.height * 0.18f..size.height * 0.82f
                            if (safeHorizontal && safeVertical) chromeVisible = !chromeVisible
                        }
                    }
                    .semantics {
                        onClick(label = if (chromeVisible) "隐藏阅读工具栏" else "显示阅读工具栏") {
                            chromeVisible = !chromeVisible
                            true
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .nestedScroll(continuousReadingConnection)
                        .verticalScroll(chapterScrollState)
                        .padding(horizontal = 28.dp)
                        .padding(top = 92.dp, bottom = 132.dp),
                ) {
                    Text(
                        "第 ${chapter.number} 章",
                        modifier = Modifier.testTag("reader-chapter-heading").semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = EditorialSerif),
                    )
                    Box(
                        Modifier
                            .padding(top = 12.dp, bottom = 18.dp)
                            .width(36.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        chapter.prose,
                        modifier = Modifier.padding(top = 30.dp).testTag("reader-prose"),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = EditorialSerif,
                            fontSize = preferences.fontSizeSp.sp,
                            lineHeight = preferences.lineHeightSp.sp,
                        ),
                    )
                    Spacer(Modifier.height(40.dp))
                    Text(
                        when {
                            autoNextChapter != null -> "继续上滑进入下一章"
                            nextChapter != null -> "下一章尚未完成"
                            else -> "已读至当前最新章节"
                        },
                        modifier = Modifier.fillMaxWidth().testTag("reader-end-hint"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter).testTag("reader-chrome-top"),
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 },
        ) {
            Column(Modifier.fillMaxWidth().background(readerBackground)) {
                Box(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("reader-back")) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回上一层")
                        }
                        Text(
                            snapshot?.project?.title ?: "阅读",
                            modifier = Modifier.widthIn(max = 160.dp).padding(start = 4.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialSerif),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = { if (generationInProgress) onOpenGeneration() else showContinueSheet = true },
                        modifier = Modifier.align(Alignment.CenterEnd).height(48.dp).testTag("reader-continue-writing-top"),
                    ) { Text(if (generationInProgress) "生成中" else "续写", fontWeight = FontWeight.Bold) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            }
        }

        if (chapter != null) {
            val readingProgress = if (chapterScrollState.maxValue > 0) {
                chapterScrollState.value.toFloat() / chapterScrollState.maxValue
            } else {
                1f
            }.coerceIn(0f, 1f)
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter).testTag("reader-chrome-bottom"),
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 4 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 },
            ) {
                Column(Modifier.fillMaxWidth().background(readerBackground).navigationBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) {
                            Box(
                                Modifier
                                    .fillMaxWidth(readingProgress)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text(
                            "${(readingProgress * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { selectedChapterNumber = chapters[selectedIndex - 1].number },
                            enabled = selectedIndex > 0,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("reader-previous"),
                        ) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("上一章")
                        }
                        TextButton(
                            onClick = { showDirectory = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("reader-bottom-directory"),
                        ) {
                            Icon(painterResource(R.drawable.ic_list), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${selectedIndex + 1} / ${chapters.size}")
                        }
                        if (selectedIndex in 0 until chapters.lastIndex) {
                            TextButton(
                                onClick = { selectedChapterNumber = chapters[selectedIndex + 1].number },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("reader-next"),
                            ) {
                                Text("下一章")
                                Spacer(Modifier.width(6.dp))
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            TextButton(
                                onClick = { if (generationInProgress) onOpenGeneration() else showContinueSheet = true },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("reader-return-creation"),
                            ) {
                                Text(if (generationInProgress) "生成中" else "续写")
                                Spacer(Modifier.width(6.dp))
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContinueSheet && snapshot != null) {
        val available = minOf(3, snapshot.plan.size)
        LaunchedEffect(available) { if (available > 0) continueCount = continueCount.coerceIn(1, available) }
        ModalBottomSheet(
            onDismissRequest = { showContinueSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.testTag("reader-continue-sheet"),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("续写后续章节", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = EditorialSerif))
                Text("每章仍按正文、结构化结算的顺序独立完成；不会并行生成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (available > 0) {
                    S3SequentialBatchSelector(continueCount, available) { continueCount = it }
                    EditorialPrimaryButton(
                        label = if (continueCount == 1) "续写第 ${snapshot.storyState.nextChapter} 章" else "顺序续写 $continueCount 章",
                        onClick = {
                            showContinueSheet = false
                            onContinueWriting(continueCount)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("reader-continue-confirm"),
                    )
                } else {
                    Text("后续章节尚未准备。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EditorialPrimaryButton(
                        label = "去准备后续章节",
                        onClick = { showContinueSheet = false; onOpenGeneration() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showDirectory && snapshot != null && chapters.isNotEmpty()) {
        EditorialChapterDirectorySheet(
            snapshot = snapshot,
            selectedChapterNumber = chapter?.number,
            onDismiss = { showDirectory = false },
            onChapterSelected = { chapterNumber ->
                selectedChapterNumber = chapterNumber
                showDirectory = false
            },
            primaryLabel = if (generationInProgress) "查看生成进度" else "续写",
            onPrimaryAction = {
                showDirectory = false
                if (generationInProgress) onOpenGeneration() else showContinueSheet = true
            },
        )
    }
}

@Composable
internal fun ReaderSettingsSection(
    preferences: S3ReaderPreferences,
    onChange: (S3ReaderPreferences) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阅读设置", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text("字号、行距和主题只保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("正文字号", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(16 to "小", 18 to "标准", 20 to "大", 22 to "特大").forEach { (size, label) ->
                val selected = preferences.fontSizeSp == size
                if (selected) {
                    Button(
                        onClick = { onChange(preferences.copy(fontSizeSp = size)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-font-$size").semantics {
                            this.selected = selected
                            contentDescription = "正文字号$label，已选择"
                        },
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(preferences.copy(fontSizeSp = size)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-font-$size").semantics {
                            this.selected = selected
                            contentDescription = "正文字号$label"
                        },
                    ) { Text(label) }
                }
            }
        }
        Text("正文行距", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(26 to "紧凑", 30 to "标准", 34 to "舒展", 38 to "宽松").forEach { (height, label) ->
                val selected = preferences.lineHeightSp == height
                if (selected) {
                    Button(
                        onClick = { onChange(preferences.copy(lineHeightSp = height)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-line-$height").semantics {
                            this.selected = selected
                            contentDescription = "正文行距$label，已选择"
                        },
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(preferences.copy(lineHeightSp = height)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-line-$height").semantics {
                            this.selected = selected
                            contentDescription = "正文行距$label"
                        },
                    ) { Text(label) }
                }
            }
        }
        Text("显示主题", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                S3ReaderTheme.SYSTEM to "跟随系统",
                S3ReaderTheme.LIGHT to "浅色",
                S3ReaderTheme.DARK to "深色",
            ).forEach { (theme, label) ->
                val selected = preferences.theme == theme
                if (selected) {
                    Button(
                        onClick = { onChange(preferences.copy(theme = theme)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-theme-${theme.name.lowercase()}").semantics {
                            this.selected = selected
                            contentDescription = "显示主题$label，已选择"
                        },
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(preferences.copy(theme = theme)) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("reader-theme-${theme.name.lowercase()}").semantics {
                            this.selected = selected
                            contentDescription = "显示主题$label"
                        },
                    ) { Text(label) }
                }
            }
        }
    }
}

private fun S0Chapter.readerHeading(): String {
    return "第 $number 章"
}

private fun S0ChapterState.label(): String = when (this) {
    S0ChapterState.PLANNED -> "计划中"
    S0ChapterState.WRITING -> "写作中"
    S0ChapterState.READABLE_DRAFT -> "可读草稿"
    S0ChapterState.NEEDS_REVIEW -> "待检查"
    S0ChapterState.COMMITTED -> "已提交"
    S0ChapterState.PAUSED -> "已暂停"
}
