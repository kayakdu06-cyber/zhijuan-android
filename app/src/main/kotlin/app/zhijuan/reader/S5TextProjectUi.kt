package app.zhijuan.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.zhijuan.core.s0.S0PlanItem
import app.zhijuan.core.s0.S0PlotPace
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ContentScale
import app.zhijuan.core.s0.S0Project
import app.zhijuan.core.s0.S0ProjectSnapshot
import app.zhijuan.core.s0.S0WritingSkillImport
import app.zhijuan.core.s0.S0WritingSkillState
import app.zhijuan.core.s0.S0WritingSkillStatus
import app.zhijuan.core.s0.S3RecoveryAction
import app.zhijuan.data.s0.S5WritingSkillParser
import java.util.UUID

internal data class S5ProjectDraft(
    val project: S0Project,
    val plan: List<S0PlanItem>,
    val writingSkill: S0WritingSkillImport? = null,
)

@Composable
internal fun S5LibraryScreen(
    modifier: Modifier,
    projects: List<S0ProjectSnapshot>,
    activeProjectId: String?,
    runningProjectId: String? = null,
    recoveryActions: Map<String, S3RecoveryAction>,
    archiveBusy: Boolean,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onGenerate: (String) -> Unit,
    onRead: (String) -> Unit,
    onExport: ((String) -> Unit)?,
    onImport: (() -> Unit)?,
    onDelete: (String) -> Unit,
    onManageWritingSkill: (String) -> Unit = {},
    onManageContentScale: (String) -> Unit = {},
    onManagePlotPace: (String) -> Unit = {},
) {
    var deleteCandidate by remember { mutableStateOf<S0ProjectSnapshot?>(null) }
    var expandedProjectMenu by rememberSaveable { mutableStateOf<String?>(null) }
    val featured = projects.firstOrNull { it.project.id == activeProjectId } ?: projects.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            EditorialBrandBar(
                title = "书库",
                actionIcon = R.drawable.ic_add_book,
                actionDescription = "新建小说",
                onAction = onCreate,
            )
        }
        item { HorizontalDivider() }
        if (projects.isEmpty()) {
            item { EditorialSectionHeader("还没有本地作品") }
            item {
                Text(
                    "从书名、题材、主角、基调和核心设定开始，确认后安全保存到本机。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                EditorialPrimaryButton(
                    label = "创建小说",
                    onClick = onCreate,
                    icon = R.drawable.ic_add_book,
                    modifier = Modifier.fillMaxWidth().testTag("library-create"),
                )
            }
            onImport?.let { importProject ->
                item {
                    EditorialSecondaryButton(
                        label = if (archiveBusy) "正在处理备份…" else "导入备份",
                        onClick = importProject,
                        enabled = !archiveBusy,
                        modifier = Modifier.fillMaxWidth().testTag("library-import"),
                    )
                }
            }
        } else {
            item { EditorialSectionHeader("最近创作", "${projects.size} 本书") }
            featured?.let { current ->
                item {
                    FeaturedProject(
                        snapshot = current,
                        recoveryAction = recoveryActions[current.project.id] ?: S3RecoveryAction.NONE,
                        onGenerate = { onGenerate(current.project.id) },
                        onRead = { onRead(current.project.id) },
                    )
                }
            }
            item { HorizontalDivider() }
            item { EditorialSectionHeader("全部作品", "${projects.size} 本书") }
            items(projects, key = { it.project.id }) { project ->
                val selected = project.project.id == featured?.project?.id
                val recovery = recoveryActions[project.project.id] ?: S3RecoveryAction.NONE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library-project-${project.project.id}")
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                            else MaterialTheme.colorScheme.background,
                        )
                        .semantics {
                            this.selected = selected
                            contentDescription = "${project.project.title}${if (selected) "，当前项目" else ""}"
                        }
                        .clickable(role = Role.Button) { onSelect(project.project.id) },
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .width(4.dp)
                                .height(68.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = if (selected) 12.dp else 0.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        EditorialBookCover(project.project.title, Modifier.width(62.dp).height(86.dp), compact = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    project.project.title,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialSerif),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                                if (selected) {
                                    Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text("${project.project.genre} · ${project.project.protagonist}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            project.chapters.lastOrNull()?.let { chapter ->
                                Text(
                                    "第 ${chapter.number} 章",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Text(project.editorialProgressLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (recovery != S3RecoveryAction.NONE) {
                                Text("需要恢复：${recovery.safeLabel()}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { expandedProjectMenu = project.project.id },
                                modifier = Modifier.size(48.dp).testTag("library-menu-${project.project.id}"),
                            ) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "${project.project.title}更多操作")
                            }
                            DropdownMenu(
                                expanded = expandedProjectMenu == project.project.id,
                                onDismissRequest = { expandedProjectMenu = null },
                                modifier = Modifier
                                    .widthIn(min = 188.dp, max = 232.dp)
                                    .testTag("library-menu-popup-${project.project.id}"),
                                shape = RoundedCornerShape(8.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (project.chapters.isEmpty()) "开始创作" else "继续创作") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                                    onClick = {
                                        expandedProjectMenu = null
                                        onGenerate(project.project.id)
                                    },
                                    modifier = Modifier.height(48.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                )
                                if (project.chapters.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("继续阅读") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                                        onClick = {
                                            expandedProjectMenu = null
                                            onRead(project.project.id)
                                        },
                                        modifier = Modifier.height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("创作 Skill") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_auto_stories), contentDescription = null) },
                                    onClick = {
                                        expandedProjectMenu = null
                                        onManageWritingSkill(project.project.id)
                                    },
                                    modifier = Modifier.height(48.dp).testTag("library-skill-${project.project.id}"),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text("叙事尺度") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                                    onClick = {
                                        expandedProjectMenu = null
                                        onManageContentScale(project.project.id)
                                    },
                                    modifier = Modifier.height(48.dp).testTag("library-content-scale-${project.project.id}"),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                )
                                DropdownMenuItem(
                                    text = { Text("剧情节奏") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                                    onClick = {
                                        expandedProjectMenu = null
                                        onManagePlotPace(project.project.id)
                                    },
                                    modifier = Modifier.height(48.dp).testTag("library-plot-pace-${project.project.id}"),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                )
                                onExport?.let { export ->
                                    DropdownMenuItem(
                                        text = { Text("导出备份") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_auto_stories), contentDescription = null) },
                                        onClick = {
                                            expandedProjectMenu = null
                                            export(project.project.id)
                                        },
                                        enabled = !archiveBusy,
                                        modifier = Modifier.height(48.dp).testTag("library-export-${project.project.id}"),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.ic_close),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        expandedProjectMenu = null
                                        deleteCandidate = project
                                    },
                                    modifier = Modifier.height(48.dp).testTag("library-delete-${project.project.id}"),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorialSecondaryButton(
                        label = "新建小说",
                        onClick = onCreate,
                        icon = R.drawable.ic_add_book,
                        modifier = Modifier.weight(1f).testTag("library-create"),
                    )
                    onImport?.let { importProject ->
                        EditorialSecondaryButton(
                            label = if (archiveBusy) "处理中…" else "导入备份",
                            onClick = importProject,
                            enabled = !archiveBusy,
                            modifier = Modifier.weight(1f).testTag("library-import"),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "备份不包含 API Key、活动请求或诊断正文。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    deleteCandidate?.let { candidate ->
        val needsTaskDiscard = runningProjectId == candidate.project.id ||
            (recoveryActions[candidate.project.id] != null &&
                recoveryActions[candidate.project.id] != S3RecoveryAction.NONE)
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(8.dp),
            title = { Text("删除《${candidate.project.title}》？") },
            text = {
                Text(
                    if (needsTaskDiscard) {
                        "将先停止这本书的生成或结算，再丢弃未提交任务并删除全部本机项目文件。不会发送新的 API 请求；API 配置和其他书不受影响。此操作无法撤销。"
                    } else {
                        "将删除这本书的全部本机项目文件。不会发送 API 请求；API 配置和其他书不受影响。此操作无法撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDelete(candidate.project.id)
                    },
                    modifier = Modifier.height(48.dp).testTag("library-delete-confirm"),
                ) { Text(if (needsTaskDiscard) "停止任务并删除" else "确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }, modifier = Modifier.height(48.dp)) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FeaturedProject(
    snapshot: S0ProjectSnapshot,
    recoveryAction: S3RecoveryAction,
    onGenerate: () -> Unit,
    onRead: () -> Unit,
) {
    val latest = snapshot.chapters.lastOrNull()
    val next = snapshot.plan.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 360.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
            ) {
                EditorialBookCover(
                    snapshot.project.title,
                    Modifier
                        .width(if (compact) 108.dp else 142.dp)
                        .height(if (compact) 170.dp else 214.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                    Text(
                        snapshot.project.title,
                        style = (if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                            .copy(fontFamily = EditorialSerif),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${snapshot.project.genre} · 长篇",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    Text(
                        latest?.let { "第 ${it.number} 章" }
                            ?: next?.let { "第 ${it.chapter} 章" }
                            ?: "尚无章节",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    Text(
                        snapshot.editorialProgressLabel(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        if (latest != null) "可继续阅读或续写后续章节" else "可开始生成第一章",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (recoveryAction != S3RecoveryAction.NONE) {
                        Text("需要恢复：${recoveryAction.safeLabel()}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorialPrimaryButton(
                label = if (snapshot.chapters.isEmpty()) "开始创作" else "继续创作",
                onClick = onGenerate,
                icon = R.drawable.ic_edit,
                modifier = Modifier.weight(1f),
            )
            if (snapshot.chapters.isNotEmpty()) {
                EditorialSecondaryButton(
                    label = "继续阅读",
                    onClick = onRead,
                    icon = R.drawable.ic_menu_book,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun S0ProjectSnapshot.editorialProgressLabel(): String {
    val characters = chapters.sumOf { it.prose.length }
    val count = if (characters >= 10_000) {
        val tenth = characters / 1_000
        "${tenth / 10}.${tenth % 10} 万字"
    } else {
        "$characters 字"
    }
    return "已完成 ${storyState.committedChapters.size} 章 · $count"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun S5CreateProjectSheet(
    onDismiss: () -> Unit,
    onConfirm: (S5ProjectDraft) -> Unit,
    writingSkill: S0WritingSkillImport? = null,
    writingSkillError: String? = null,
    onChooseWritingSkill: () -> Unit = {},
    onUpdateWritingSkill: (S0WritingSkillImport) -> Unit = {},
    onRemoveWritingSkill: () -> Unit = {},
) {
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var genre by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var protagonist by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var tone by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var premise by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var selectedGenreMain by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGenreDetails by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedRelationship by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedViewpoint by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTone by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedContentScale by rememberSaveable { mutableStateOf(S0ContentScale.QING_XU) }
    var selectedPlotPace by rememberSaveable { mutableStateOf(S0PlotPace.BALANCED) }
    var writingSkillExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmedWritingSkillSha by rememberSaveable(writingSkill?.qualityCard?.sha256) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<S5ProjectDraft?>(null) }
    val writingSkillConfirmed = writingSkill == null || confirmedWritingSkillSha == writingSkill.qualityCard.sha256
    val valid = listOf(title, genre, protagonist, tone, premise).all { it.text.trim().isNotEmpty() } && writingSkillConfirmed

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create-project-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.testTag("create-project-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (preview == null) "创建小说" else "确认小说信息",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (preview == null) {
                item { S5TextField(title, "书名", "create-title") { title = it } }
                item {
                    Text("题材预设", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "先选主分类，再选 0–3 个细分类；关系和视角独立可选。最终结果仍可手动编辑。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    S5PresetRow(
                        label = "主分类",
                        options = S5GenrePresets.keys.toList(),
                        selected = { it == selectedGenreMain },
                        tagPrefix = "create-genre-main",
                    ) { main ->
                        selectedGenreMain = main
                        selectedGenreDetails = emptyList()
                        genre = s5EditableValue(s5ComposeGenre(main, emptyList(), selectedRelationship, selectedViewpoint))
                    }
                }
                selectedGenreMain?.let { main ->
                    item {
                        S5PresetRow(
                            label = "细分类（最多 3 项）",
                            options = S5GenrePresets.getValue(main),
                            selected = selectedGenreDetails::contains,
                            tagPrefix = "create-genre-detail",
                        ) { detail ->
                            selectedGenreDetails = when {
                                detail in selectedGenreDetails -> selectedGenreDetails - detail
                                selectedGenreDetails.size < 3 -> selectedGenreDetails + detail
                                else -> selectedGenreDetails
                            }
                            genre = s5EditableValue(s5ComposeGenre(main, selectedGenreDetails, selectedRelationship, selectedViewpoint))
                        }
                    }
                }
                item {
                    S5PresetRow(
                        label = "关系（可选）",
                        options = S5RelationshipPresets,
                        selected = { it == selectedRelationship },
                        tagPrefix = "create-relationship",
                    ) { relationship ->
                        selectedRelationship = relationship.takeUnless { it == selectedRelationship }
                        genre = s5EditableValue(s5ComposeGenre(selectedGenreMain, selectedGenreDetails, selectedRelationship, selectedViewpoint))
                    }
                }
                item {
                    S5PresetRow(
                        label = "视角（可选）",
                        options = S5ViewpointPresets,
                        selected = { it == selectedViewpoint },
                        tagPrefix = "create-viewpoint",
                    ) { viewpoint ->
                        selectedViewpoint = viewpoint.takeUnless { it == selectedViewpoint }
                        genre = s5EditableValue(s5ComposeGenre(selectedGenreMain, selectedGenreDetails, selectedRelationship, selectedViewpoint))
                    }
                }
                item { S5TextField(genre, "最终题材（可编辑）", "create-genre") { genre = it } }
                item { S5TextField(protagonist, "主角", "create-protagonist") { protagonist = it } }
                item {
                    Text("基调", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "决定正文的语气、节奏、描写密度和情绪温度；不会改变题材、模型、篇幅或内容规则。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    S5PresetRow(
                        label = "常用基调",
                        options = S5TonePresets,
                        selected = { it == selectedTone },
                        tagPrefix = "create-tone-preset",
                    ) { preset ->
                        selectedTone = preset
                        tone = s5EditableValue(preset)
                    }
                }
                item { S5TextField(tone, "最终基调（可编辑）", "create-tone") { tone = it } }
                item {
                    Text("叙事尺度", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                item {
                    S5PresetRow(
                        label = "描写层级",
                        options = S0ContentScale.entries.map(S0ContentScale::displayName),
                        selected = { it == selectedContentScale.displayName() },
                        tagPrefix = "create-content-scale",
                    ) { label ->
                        selectedContentScale = S0ContentScale.entries.first { it.displayName() == label }
                    }
                }
                item {
                    Text("剧情节奏", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                item {
                    S5PresetRow(
                        label = "推进速度",
                        options = S0PlotPace.entries.map(S0PlotPace::displayName),
                        selected = { it == selectedPlotPace.displayName() },
                        tagPrefix = "create-plot-pace",
                    ) { label ->
                        selectedPlotPace = S0PlotPace.entries.first { it.displayName() == label }
                    }
                }
                item { S5TextField(premise, "核心设定", "create-premise", singleLine = false) { premise = it } }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(role = Role.Button) { writingSkillExpanded = !writingSkillExpanded }
                            .semantics { contentDescription = "创作 Skill，可选，${if (writingSkillExpanded) "已展开" else "已折叠"}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("创作 Skill（可选）", style = MaterialTheme.typography.titleMedium)
                            Text("本书专用写作规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (writingSkillExpanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (writingSkillExpanded) {
                    item {
                        Text(
                            "导入单个 UTF-8 .md 或 .json，织卷会在本机提取候选规则供你修改。不会执行原文件、代码或外部链接。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        S5WritingSkillPreview(
                            writingSkill = writingSkill,
                            confirmed = writingSkillConfirmed,
                            error = writingSkillError,
                            onChoose = onChooseWritingSkill,
                            onConfirm = { edited ->
                                onUpdateWritingSkill(edited)
                                confirmedWritingSkillSha = edited.qualityCard.sha256
                            },
                            onRemove = {
                                confirmedWritingSkillSha = null
                                onRemoveWritingSkill()
                            },
                        )
                    }
                }
                item {
                    Text("确认后，织卷会在本机建立后续写作所需的连续性状态；不会调用模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Button(
                        onClick = {
                            preview = buildInitialProjectDraft(
                                title.text,
                                genre.text,
                                protagonist.text,
                                tone.text,
                                premise.text,
                                selectedContentScale,
                                selectedPlotPace,
                            ).copy(writingSkill = writingSkill?.takeIf { writingSkillConfirmed })
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create-preview"),
                    ) { Text("确认信息") }
                }
            } else {
                val draft = requireNotNull(preview)
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(draft.project.title, style = MaterialTheme.typography.titleLarge)
                            Text("${draft.project.genre} · ${draft.project.protagonist} · ${draft.project.tone}")
                            Text("叙事尺度：${draft.project.contentScale.displayName()}", color = MaterialTheme.colorScheme.primary)
                            Text("剧情节奏：${draft.project.plotPace.displayName()}", color = MaterialTheme.colorScheme.primary)
                            Text(draft.project.premise)
                            draft.writingSkill?.qualityCard?.let { card ->
                                Text("已确认质量卡：${card.name} · ${card.sha256.take(8)}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    Text("后续章节方向只在后台维护，不会显示在目录或阅读页。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Button(
                        onClick = { onConfirm(draft) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create-confirm"),
                    ) { Text("确认并创建") }
                }
                item {
                    OutlinedButton(
                        onClick = { preview = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("返回修改") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun S5ContentScaleSheet(
    projectTitle: String,
    current: S0ContentScale,
    onDismiss: () -> Unit,
    onSave: (S0ContentScale) -> Unit,
) {
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("content-scale-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("叙事尺度", style = MaterialTheme.typography.headlineSmall)
            Text(projectTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            S5PresetRow(
                label = "描写层级",
                options = S0ContentScale.entries.map(S0ContentScale::displayName),
                selected = { it == selected.displayName() },
                tagPrefix = "content-scale",
            ) { label -> selected = S0ContentScale.entries.first { it.displayName() == label } }
            Button(
                onClick = { onSave(selected) },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("content-scale-save"),
            ) { Text("保存") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun S5PlotPaceSheet(
    projectTitle: String,
    current: S0PlotPace,
    onDismiss: () -> Unit,
    onSave: (S0PlotPace) -> Unit,
) {
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("plot-pace-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("剧情节奏", style = MaterialTheme.typography.headlineSmall)
            Text(projectTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            S5PresetRow(
                label = "推进速度",
                options = S0PlotPace.entries.map(S0PlotPace::displayName),
                selected = { it == selected.displayName() },
                tagPrefix = "plot-pace",
            ) { label -> selected = S0PlotPace.entries.first { it.displayName() == label } }
            Button(
                onClick = { onSave(selected) },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("plot-pace-save"),
            ) { Text("保存") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun S5WritingSkillPreview(
    writingSkill: S0WritingSkillImport?,
    confirmed: Boolean,
    error: String?,
    onChoose: () -> Unit,
    onConfirm: (S0WritingSkillImport) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("writing-skill-error")) }
        if (writingSkill == null) {
            EditorialSecondaryButton(
                label = "选择 .md 或 .json",
                onClick = onChoose,
                icon = R.drawable.ic_auto_stories,
                modifier = Modifier.fillMaxWidth().testTag("writing-skill-choose"),
            )
            Text("未导入时继续使用织卷默认质量卡。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val card = writingSkill.qualityCard
        var cardName by rememberSaveable(card.sha256) { mutableStateOf(card.name) }
        var ruleLines by rememberSaveable(card.sha256) { mutableStateOf(card.rules.joinToString("\n")) }
        var avoidLines by rememberSaveable(card.sha256) { mutableStateOf(card.avoid.joinToString("\n")) }
        var termLines by rememberSaveable(card.sha256) { mutableStateOf(card.preferredTerms.joinToString("\n")) }
        var editError by rememberSaveable(card.sha256) { mutableStateOf<String?>(null) }
        fun lines(value: String) = value.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val itemCount = lines(ruleLines).size + lines(avoidLines).size + lines(termLines).size
        val characterCount = lines(ruleLines).sumOf(String::length) + lines(avoidLines).sumOf(String::length) +
            lines(termLines).sumOf(String::length)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "${writingSkill.sourceFileName} · ${writingSkill.format.name.lowercase()} · ${card.sha256.take(8)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it.take(80); editError = null },
                    label = { Text("质量卡名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("writing-skill-name"),
                )
                OutlinedTextField(
                    value = ruleLines,
                    onValueChange = { ruleLines = it; editError = null },
                    label = { Text("写作规则（每行一条）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("writing-skill-rules"),
                )
                OutlinedTextField(
                    value = avoidLines,
                    onValueChange = { avoidLines = it; editError = null },
                    label = { Text("避免事项（每行一条）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("writing-skill-avoid"),
                )
                OutlinedTextField(
                    value = termLines,
                    onValueChange = { termLines = it; editError = null },
                    label = { Text("用词偏好（可空）") },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth().testTag("writing-skill-terms"),
                )
                Text("$itemCount / 8 条 · $characterCount / 1600 字符", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (itemCount == 0) Text("未自动识别出规则，请在上方手动填写。", color = MaterialTheme.colorScheme.error)
                editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (confirmed) {
            Text("已确认：创建后每次正文请求都会应用此质量卡。", color = MaterialTheme.colorScheme.secondary)
        } else {
            EditorialPrimaryButton(
                label = "确认质量卡",
                onClick = {
                    runCatching {
                        S5WritingSkillParser().editQualityCard(
                            writingSkill,
                            cardName,
                            lines(ruleLines),
                            lines(avoidLines),
                            lines(termLines),
                        )
                    }.onSuccess(onConfirm).onFailure {
                        editError = "请保留 1–8 条安全规则，合计不超过 1600 字符。"
                    }
                },
                enabled = itemCount in 1..8 && characterCount in 1..1_600,
                modifier = Modifier.fillMaxWidth().testTag("writing-skill-confirm"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorialSecondaryButton("替换文件", onChoose, Modifier.weight(1f))
            EditorialSecondaryButton("移除", onRemove, Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun S5WritingSkillSheet(
    projectTitle: String,
    current: S0WritingSkillState,
    candidate: S0WritingSkillImport?,
    error: String?,
    onDismiss: () -> Unit,
    onChoose: () -> Unit,
    onApply: (S0WritingSkillImport) -> Unit,
    onRemove: () -> Unit,
    onDiscardCandidate: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("writing-skill-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("创作 Skill", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("《$projectTitle》 · 只影响后续正文的写法", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                when (current.status) {
                    S0WritingSkillStatus.ACTIVE -> {
                        val card = requireNotNull(current.qualityCard)
                        Text("已应用质量卡", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Text("${card.name} · v${card.version} · ${card.sha256.take(8)}")
                    }
                    S0WritingSkillStatus.DISABLED_CORRUPT -> {
                        Text("现有 Skill 已损坏并安全禁用；正文与已有章节不受影响。", color = MaterialTheme.colorScheme.error)
                    }
                    S0WritingSkillStatus.NONE -> Text("当前使用织卷默认质量卡。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                S5WritingSkillPreview(
                    writingSkill = candidate,
                    confirmed = false,
                    error = error,
                    onChoose = onChoose,
                    onConfirm = onApply,
                    onRemove = onDiscardCandidate,
                )
            }
            if (current.status != S0WritingSkillStatus.NONE) {
                item {
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("writing-skill-remove-current"),
                    ) { Text("移除当前创作 Skill", color = MaterialTheme.colorScheme.error) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

internal val S5GenrePresets: Map<String, List<String>> = linkedMapOf(
    "玄幻" to listOf("东方玄幻", "高武世界", "异世大陆", "王朝争霸", "灵气复苏"),
    "奇幻" to listOf("西方奇幻", "史诗奇幻", "剑与魔法", "克苏鲁", "蒸汽朋克"),
    "武侠" to listOf("传统武侠", "武侠幻想", "江湖恩仇", "国术"),
    "仙侠" to listOf("古典仙侠", "幻想修仙", "修真文明", "洪荒封神"),
    "都市" to listOf("都市生活", "都市异能", "职场商战", "娱乐圈", "青春校园"),
    "现实" to listOf("家庭伦理", "市井生活", "行业职场", "乡村年代", "社会纪实"),
    "历史" to listOf("架空历史", "秦汉三国", "隋唐宋元", "明清", "民国", "历史穿越"),
    "军事" to listOf("军旅", "战争", "谍战特工", "架空战争"),
    "科幻" to listOf("未来世界", "星际文明", "末世危机", "废土", "赛博朋克", "机甲"),
    "悬疑推理" to listOf("侦探推理", "犯罪悬疑", "灵异惊悚", "规则怪谈", "探险盗墓", "无限流"),
    "游戏电竞" to listOf("虚拟网游", "电子竞技", "游戏异界", "全息", "卡牌", "第四天灾"),
    "体育" to listOf("足球", "篮球", "综合体育", "竞技成长"),
    "古代言情" to listOf("宫廷侯爵", "权谋宅斗", "种田经商", "仙侠奇缘", "古代穿越"),
    "现代言情" to listOf("都市情感", "婚恋职场", "豪门世家", "娱乐圈", "青春校园"),
    "幻想言情" to listOf("奇幻爱情", "未来爱情", "末世爱情", "悬疑爱情", "穿书快穿"),
    "轻小说" to listOf("原生幻想", "校园日常", "搞笑吐槽", "冒险", "二次元衍生"),
)

internal val S5RelationshipPresets = listOf("言情", "纯爱", "百合", "无CP", "多元")
internal val S5ViewpointPresets = listOf("男主", "女主", "双主角", "群像", "多视角", "第一人称")
internal val S5TonePresets = listOf(
    "克制冷峻",
    "温暖治愈",
    "轻快幽默",
    "紧张凌厉",
    "阴郁压迫",
    "宏大史诗",
    "浪漫唯美",
    "现实质朴",
    "荒诞讽刺",
    "黑暗残酷",
)

internal fun s5ComposeGenre(
    main: String?,
    details: List<String>,
    relationship: String?,
    viewpoint: String?,
): String = buildList {
    main?.takeIf(String::isNotBlank)?.let(::add)
    addAll(details.distinct().take(3).filter(String::isNotBlank))
    relationship?.takeIf(String::isNotBlank)?.let(::add)
    viewpoint?.takeIf(String::isNotBlank)?.let(::add)
}.joinToString(" / ").take(80)

private fun s5EditableValue(text: String): TextFieldValue = TextFieldValue(text, selection = TextRange(text.length))

@Composable
private fun S5PresetRow(
    label: String,
    options: List<String>,
    selected: (String) -> Boolean,
    tagPrefix: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                S5PresetChip(
                    label = option,
                    selected = selected(option),
                    onClick = { onSelect(option) },
                    modifier = Modifier.testTag("$tagPrefix-$index"),
                )
            }
        }
    }
}

@Composable
private fun S5PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "$label${if (selected) "，已选择" else ""}"
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun S5PlanRefreshSheet(
    snapshot: S0ProjectSnapshot,
    onDismiss: () -> Unit,
    onConfirm: (List<S0PlanItem>) -> Unit,
) {
    val refreshed = remember(snapshot.project.id, snapshot.storyState.revision, snapshot.plan) {
        buildRefreshedPlan(snapshot)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("plan-refresh-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("准备后续章节", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            item { Text("织卷会在本机准备接下来的章节方向，不展示内部规划，不调用模型，也不会自动开始写作。") }
            item {
                Button(
                    onClick = { onConfirm(refreshed) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("plan-refresh-confirm"),
                ) { Text("确认准备后续章节") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun S5TextField(
    value: TextFieldValue,
    label: String,
    tag: String,
    singleLine: Boolean = true,
    onChange: (TextFieldValue) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { updated -> onChange(updated.copy(text = updated.text.take(if (singleLine) 80 else 600))) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

internal fun buildInitialProjectDraft(
    rawTitle: String,
    rawGenre: String,
    rawProtagonist: String,
    rawTone: String,
    rawPremise: String,
    contentScale: S0ContentScale = S0ContentScale.QING_XU,
    plotPace: S0PlotPace = S0PlotPace.BALANCED,
): S5ProjectDraft {
    val title = rawTitle.trim().take(80)
    val genre = rawGenre.trim().take(80)
    val protagonist = rawProtagonist.trim().take(80)
    val tone = rawTone.trim().take(80)
    val premise = rawPremise.trim().take(600)
    require(listOf(title, genre, protagonist, tone, premise).all(String::isNotBlank)) { "PROJECT_FIELDS_REQUIRED" }
    val project = S0Project(
        id = "project_${UUID.randomUUID().toString().replace("-", "").take(16)}",
        title = title,
        genre = genre,
        protagonist = protagonist,
        tone = tone,
        premise = premise,
        contentScale = contentScale,
        plotPace = plotPace,
    )
    val beats = listOf(
        Triple("引线", "$protagonist 在“$premise”中遇到迫使其行动的具体事件", "让核心设定第一次改变主角处境"),
        Triple("第一次抉择", "$protagonist 作出不能轻易撤回的选择", "选择带来清晰的新代价"),
        Triple("代价显现", "让前一章的选择造成可见后果", "主角失去资源、关系或安全感中的一项"),
        Triple("关系转折", "通过一次共同压力改变关键关系", "信任或冲突必须发生方向性变化"),
        Triple("线索反转", "揭示一个能重新解释既有线索的新事实", "主角的判断被证据修正"),
        Triple("压力升级", "把外部阻力与内部选择同时推高", "问题不能用重复上一章的方法解决"),
        Triple("真相门槛", "让主角接近阶段真相并承担进入门槛", "至少一条前置伏笔得到发展"),
        Triple("阶段收束", "完成当前阶段目标并留下下一段明确入口", "回收一项承诺，同时打开新的文字线索"),
    )
    val plan = beats.mapIndexed { index, (chapterTitle, goal, change) ->
        S0PlanItem(
            chapter = index + 1,
            title = chapterTitle,
            goal = goal,
            entryState = if (index == 0) "$protagonist 尚未被卷入核心事件" else "承接上一章结尾",
            mustChange = change,
            exitHook = if (index == beats.lastIndex) "新的问题在已完成的阶段之后出现" else "留下只能在下一章推进的具体问题",
        )
    }
    return S5ProjectDraft(project, plan)
}

internal fun S0ContentScale.displayName(): String = when (this) {
    S0ContentScale.QING_XU -> "清叙"
    S0ContentScale.AN_YONG -> "暗涌"
    S0ContentScale.CHEN_JIN -> "沉浸"
}

internal fun S0PlotPace.displayName(): String = when (this) {
    S0PlotPace.EXPANSIVE -> "舒展"
    S0PlotPace.BALANCED -> "均衡"
    S0PlotPace.TIGHT -> "紧凑"
}

internal fun buildRefreshedPlan(snapshot: S0ProjectSnapshot): List<S0PlanItem> {
    require(snapshot.plan.size <= 2) { "PLAN_REFRESH_NOT_DUE" }
    val kept = snapshot.plan.sortedBy(S0PlanItem::chapter)
    val start = (kept.lastOrNull()?.chapter ?: snapshot.storyState.nextChapter - 1) + 1
    val needed = 8 - kept.size
    val lastSummary = snapshot.chapters.lastOrNull()?.summary ?: snapshot.project.premise
    val beats = listOf("余波", "新线索", "阻力", "选择", "代价", "转折", "逼近", "回收")
    val additions = (0 until needed).map { index ->
        val chapter = start + index
        val beat = beats[index % beats.size]
        S0PlanItem(
            chapter = chapter,
            title = "$beat · 第 $chapter 章",
            goal = "承接“${lastSummary.take(120)}”，让${snapshot.project.protagonist}推进一个新的可验证变化",
            entryState = "承接上一章已提交状态",
            mustChange = "本章必须产生新的信息、关系、资源或风险变化",
            exitHook = "留下第 ${chapter + 1} 章可以直接承接的具体问题",
            mustNotRepeatEventKeys = snapshot.storyState.recentEventKeys.takeLast(20),
        )
    }
    return kept + additions
}

private fun S3RecoveryAction.safeLabel(): String = when (this) {
    S3RecoveryAction.NONE -> "无"
    S3RecoveryAction.RETRY_PROSE -> "重试正文"
    S3RecoveryAction.RETRY_SETTLEMENT -> "只重试结算"
    S3RecoveryAction.CONFIRM_RESEND -> "确认是否重发"
    S3RecoveryAction.REVIEW_DRAFT -> "检查已保存草稿"
}

private fun S0ChapterState.safeLabel(): String = when (this) {
    S0ChapterState.PLANNED -> "计划中"
    S0ChapterState.WRITING -> "写作中"
    S0ChapterState.READABLE_DRAFT -> "可读草稿"
    S0ChapterState.NEEDS_REVIEW -> "待检查"
    S0ChapterState.COMMITTED -> "已提交"
    S0ChapterState.PAUSED -> "已暂停"
}
