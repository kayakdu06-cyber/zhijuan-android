package app.zhijuan.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0ProjectSnapshot

internal val EditorialSerif = FontFamily.Serif
internal val EditorialSuccess = Color(0xFF2F6B47)
internal val EditorialSuccessDark = Color(0xFF76C796)

@Composable
internal fun EditorialBrandBar(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: Int? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 600
    Box(
        modifier = modifier.fillMaxWidth().height(if (compactHeight) 56.dp else 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.zhijuan_logo_draft),
                contentDescription = "织卷标志",
                modifier = Modifier.size(40.dp),
            )
            Text("织卷", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = EditorialSerif),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionIcon != null && actionDescription != null && onAction != null) {
            IconButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
            ) {
                Icon(
                    painter = painterResource(actionIcon),
                    contentDescription = actionDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun EditorialSectionHeader(
    title: String,
    meta: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif),
        )
        if (meta != null) {
            Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline))
            Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EditorialBookCover(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val visibleTitle = title.trim().take(if (compact) 8 else 12)
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(if (compact) 6.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                if (compact) "长篇" else "长篇\n小说",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = EditorialSerif,
                fontSize = if (compact) 10.sp else 12.sp,
                lineHeight = if (compact) 14.sp else 18.sp,
            )
            Box(Modifier.width(1.dp).height(if (compact) 54.dp else 132.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)))
            Text(
                visibleTitle.map(Char::toString).joinToString("\n"),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = EditorialSerif,
                fontSize = if (compact) 13.sp else 24.sp,
                lineHeight = if (compact) 16.sp else 30.sp,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 8 else 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EditorialPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun EditorialSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun EditorialStateLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorialChapterDirectorySheet(
    snapshot: S0ProjectSnapshot,
    selectedChapterNumber: Int?,
    onDismiss: () -> Unit,
    onChapterSelected: ((Int) -> Unit)?,
    primaryLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val nextChapter = snapshot.storyState.nextChapter
    val completed = snapshot.chapters.filter { it.number < nextChapter }.sortedBy { it.number }
    val currentChapter = snapshot.chapters.firstOrNull { it.number == nextChapter }
    val currentPlan = snapshot.plan.firstOrNull { it.chapter == nextChapter } ?: snapshot.plan.firstOrNull()
    val futurePlan = snapshot.plan.filter { it.chapter > nextChapter }.sortedBy { it.chapter }
    val successColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) EditorialSuccessDark else EditorialSuccess

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("reader-directory-sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "章节目录",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = EditorialSerif),
                        )
                        Text(
                            "${snapshot.project.title} · ${completed.size} 章已完成",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "关闭章节目录")
                    }
                }
                HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 12.dp))
            }
            if (completed.isNotEmpty()) {
                item { DirectorySectionTitle("已完成", successColor) }
                items(completed, key = { "completed-${it.number}" }) { chapter ->
                    DirectoryChapterRow(
                        chapter = chapter,
                        selected = chapter.number == selectedChapterNumber,
                        successColor = successColor,
                        onClick = onChapterSelected?.let { select -> { select(chapter.number) } },
                    )
                }
            }
            item {
                DirectorySectionTitle("当前", MaterialTheme.colorScheme.primary, Modifier.padding(top = 16.dp))
                val state = currentChapter?.state?.editorialLabel() ?: "待写"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .then(
                            if (currentChapter != null && onChapterSelected != null) {
                                Modifier.clickable(role = Role.Button) { onChapterSelected(currentChapter.number) }
                            } else Modifier
                        )
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(4.dp).height(88.dp).background(MaterialTheme.colorScheme.primary))
                    Icon(
                        painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "第 $nextChapter 章",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialSerif),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            EditorialStateLabel(state)
                        }
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    TextButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(secondaryLabel) }
                }
            }
            if (futurePlan.isNotEmpty()) {
                item { DirectorySectionTitle("待写章节", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 8.dp)) }
                items(futurePlan, key = { "plan-${it.chapter}" }) { plan ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_auto_stories),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "第 ${plan.chapter} 章",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("待写", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                }
            }
            item {
                EditorialPrimaryButton(
                    label = primaryLabel,
                    onClick = onPrimaryAction,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun DirectorySectionTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { heading() },
        color = color,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = EditorialSerif),
    )
}

@Composable
private fun DirectoryChapterRow(
    chapter: S0Chapter,
    selected: Boolean,
    successColor: Color,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("reader-directory-chapter-${chapter.number}")
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                else Color.Transparent,
            )
            .semantics {
                this.selected = selected
                contentDescription = "第 ${chapter.number} 章，${chapter.state.editorialLabel()}${if (selected) "，当前阅读" else ""}"
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(start = if (selected) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = successColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "第 ${chapter.number} 章",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = EditorialSerif),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selected) "当前 · ${chapter.state.editorialLabel()}" else chapter.state.editorialLabel(),
                color = if (selected) MaterialTheme.colorScheme.primary else successColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
}

private fun S0ChapterState.editorialLabel(): String = when (this) {
    S0ChapterState.PLANNED -> "已规划"
    S0ChapterState.WRITING -> "写作中"
    S0ChapterState.READABLE_DRAFT -> "可读草稿"
    S0ChapterState.NEEDS_REVIEW -> "待检查"
    S0ChapterState.COMMITTED -> "已完成"
    S0ChapterState.PAUSED -> "已暂停"
}
