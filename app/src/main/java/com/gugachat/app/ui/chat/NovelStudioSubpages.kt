package com.gugachat.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuAccordionCard
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuCompactSwitch
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineDeleteConfirmStrip
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSubpageTopBar
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler
import java.util.Locale

@Composable
internal fun NovelSubPageHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    GuSubpageTopBar(
        title = title,
        backLabel = backLabel,
        onBack = onBack,
        trailing = trailing,
    )
}

@Composable
internal fun NovelChapterDetailSubPage(
    chapter: NovelChapter,
    canDelete: Boolean,
    isGeneratingRecap: Boolean,
    onBack: () -> Unit,
    onGenerateRecap: () -> Unit,
    onSave: (NovelChapter) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var status by remember(chapter.id) { mutableStateOf(chapter.status) }
    var outline by remember(chapter.id) { mutableStateOf(chapter.outline) }
    var summary by remember(chapter.id, chapter.summary) { mutableStateOf(chapter.summary) }
    var confirmDelete by remember(chapter.id) { mutableStateOf(false) }

    LaunchedEffect(chapter.summary) {
        if (chapter.summary.isNotBlank() && summary.isBlank()) {
            summary = chapter.summary
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (isZh) "第 ${chapter.order} 章 · 细纲与梗概" else "Chapter ${chapter.order}",
            backLabel = if (isZh) "目录" else "Back",
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = {
                        onSave(
                            chapter.copy(
                                title = title.trim().ifBlank { chapter.title },
                                status = status,
                                outline = outline.trim(),
                                summary = summary.trim(),
                            ),
                        )
                    },
                    compact = true,
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = if (isZh) "章节标题" else "Chapter Title",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isZh) "章节状态" else "Status", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val statusKeys = listOf("draft", "writing", "done")
                        GuSegmentedControl(
                            items = statusKeys.map { localizeChapterStatus(it, isZh) },
                            selectedIndex = statusKeys.indexOf(status).coerceAtLeast(0),
                            onSelect = { idx -> status = statusKeys[idx] },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = outline,
                        onValueChange = { outline = it },
                        label = strings.novel.chapterOutline,
                        placeholder = strings.novel.chapterOutlinePlaceholder,
                        singleLine = false,
                        minLines = 3,
                        maxLines = 7,
                    )
                    GuTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = strings.novel.chapterSummaryLabel,
                        placeholder = if (isZh) "本章完结后的前情梗概（将注入后续章节提示词）…" else "Chapter recap injected into future chapters…",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                    )
                    GuButton(
                        onClick = onGenerateRecap,
                        enabled = !isGeneratingRecap && chapter.wordCount > 0,
                        loading = isGeneratingRecap,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.recapBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (canDelete) {
                if (confirmDelete) {
                    GuInlineDeleteConfirmStrip(
                        prompt = if (isZh) "删除《${chapter.title}》？正文将保留在相邻章节。" else "Delete chapter? Prose remains intact.",
                        confirmText = strings.common.delete,
                        cancelText = strings.common.cancel,
                        onConfirm = onDelete,
                        onCancel = { confirmDelete = false },
                    )
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "删除本章" else "Delete Chapter", color = c.danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelFinishChapterSubPage(
    activeChapter: NovelChapter,
    nextOrder: Int,
    nextPlannedChapter: NovelChapter?,
    onBack: () -> Unit,
    onConfirm: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var nextTitle by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.title.orEmpty()) }
    var nextOutline by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.outline.orEmpty()) }
    var autoRecap by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (isZh) "完结并开启下一章" else "Finish & Start Next",
            backLabel = if (isZh) "目录" else "Back",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuCard(modifier = Modifier.fillMaxWidth(), backgroundColor = c.surfaceSoft) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GuMetaText(
                        text = if (isZh) "即将完结当前章节" else "Finishing Active Chapter",
                        color = c.muted,
                    )
                    Text(
                        text = "《${activeChapter.title}》 · ${formatStudioCount(activeChapter.wordCount)} ${if (isZh) "字" else "w"}",
                        color = c.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nextPlannedChapter != null) {
                        GuBadge(
                            text = if (isZh) "已检测到预规划草稿：${nextPlannedChapter.title}" else "Next planned draft: ${nextPlannedChapter.title}",
                            variant = GuBadgeVariant.EMERALD,
                        )
                        if (nextPlannedChapter.outline.isNotBlank()) {
                            Text(
                                text = nextPlannedChapter.outline,
                                color = c.muted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        GuTextField(
                            value = nextTitle,
                            onValueChange = { nextTitle = it },
                            label = if (isZh) "下一章标题（可选）" else "Next Chapter Title",
                            placeholder = if (isZh) "第${nextOrder}章" else "Chapter $nextOrder",
                        )
                        GuTextField(
                            value = nextOutline,
                            onValueChange = { nextOutline = it },
                            label = strings.novel.chapterOutline,
                            placeholder = strings.novel.chapterOutlinePlaceholder,
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))
                    GuSwitchRow(
                        label = if (isZh) "AI 自动总结本章前情梗概" else "Auto-generate AI recap for finished chapter",
                        checked = autoRecap,
                        onCheckedChange = { autoRecap = it },
                    )
                }
            }

            GuButton(
                onClick = {
                    val usePlanned = nextPlannedChapter != null
                    onConfirm(
                        if (usePlanned) "" else nextTitle,
                        if (usePlanned) "" else nextOutline,
                        autoRecap,
                    )
                },
                variant = GuButtonVariant.HONEY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isZh) "确认完结并开启下一章" else "Finish & Start Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun NovelLoreEditorSubPage(
    existing: NovelLoreEntry?,
    onBack: () -> Unit,
    onSaveNew: (cat: String, title: String, keywords: String, content: String, always: Boolean) -> Unit,
    onSaveExisting: (NovelLoreEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    var cat by remember(existing?.id) { mutableStateOf(existing?.category ?: "character") }
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var keywords by remember(existing?.id) { mutableStateOf(existing?.keywords?.joinToString(", ").orEmpty()) }
    var aliases by remember(existing?.id) { mutableStateOf(existing?.aliases?.joinToString(", ").orEmpty()) }
    var priority by remember(existing?.id) { mutableIntStateOf(existing?.priority ?: 50) }
    var content by remember(existing?.id) { mutableStateOf(existing?.content.orEmpty()) }
    var always by remember(existing?.id) { mutableStateOf(existing?.alwaysActive ?: false) }
    var confirmDelete by remember(existing?.id) { mutableStateOf(false) }

    val canSave = title.isNotBlank() && content.isNotBlank()

    fun commitSave() {
        if (!canSave) return
        if (existing != null) {
            val kws = keywords.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val als = aliases.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            onSaveExisting(
                existing.copy(
                    category = cat,
                    title = title.trim(),
                    keywords = kws.ifEmpty { listOf(title.trim()) },
                    aliases = als,
                    priority = priority,
                    content = content.trim(),
                    alwaysActive = always,
                ),
            )
        } else {
            onSaveNew(cat, title, keywords, content, always)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovelSubPageHeader(
            title = if (existing != null) {
                if (isZh) "编辑世界书条目" else "Edit Worldbook Entry"
            } else {
                strings.novel.loreAddBtn
            },
            backLabel = if (isZh) "世界设定" else "Back",
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = ::commitSave,
                    enabled = canSave,
                    compact = true,
                ) {
                    Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("character", "location", "item", "faction", "rule", "foreshadow", "plot", "world"), key = { it }) { catKey ->
                    GuPill(
                        label = strings.novel.loreCats[catKey] ?: catKey,
                        selected = cat == catKey,
                        onClick = { cat = catKey },
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = if (isZh) "词条名称" else "Entry Title",
                        placeholder = strings.novel.loreTitlePlaceholder,
                    )
                    GuTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = if (isZh) "触发关键词" else "Trigger Keywords",
                        placeholder = strings.novel.loreKeywordsPlaceholder,
                    )
                    GuTextField(
                        value = aliases,
                        onValueChange = { aliases = it },
                        label = if (isZh) "别名 / 简称（选填）" else "Aliases (Optional)",
                        placeholder = if (isZh) "别名/简称（逗号分隔，选填）" else "Aliases (comma-separated)",
                    )
                    GuTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = if (isZh) "设定内容" else "Lore Content",
                        placeholder = strings.novel.loreContentPlaceholder,
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8,
                    )
                }
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (isZh) "注入优先级" else "Priority", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val pValues = listOf(30, 50, 80, 100)
                        val pLabels = if (isZh) listOf("低", "中", "高", "核心") else listOf("Low", "Mid", "High", "Core")
                        GuSegmentedControl(
                            items = pLabels,
                            selectedIndex = pValues.indexOfFirst { it >= priority }.takeIf { it >= 0 } ?: 1,
                            onSelect = { idx -> priority = pValues[idx] },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.5f))
                    GuSwitchRow(
                        label = strings.novel.loreAlways,
                        checked = always,
                        onCheckedChange = { always = it },
                    )
                }
            }

            if (existing != null) {
                if (confirmDelete) {
                    GuInlineDeleteConfirmStrip(
                        prompt = if (isZh) "确认永久删除「${existing.title}」？" else "Delete \"${existing.title}\"?",
                        confirmText = strings.common.delete,
                        cancelText = strings.common.cancel,
                        onConfirm = { onDelete(existing.id) },
                        onCancel = { confirmDelete = false },
                    )
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "删除此词条" else "Delete Entry", color = c.danger, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

