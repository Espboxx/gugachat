package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
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
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler

private data class NovelGenreVisual(
    val startColor: Color,
    val endColor: Color,
    val icon: ImageVector,
)

private fun resolveNovelGenreVisual(presetId: String): NovelGenreVisual = when (presetId.lowercase()) {
    "xuanhuan" -> NovelGenreVisual(Color(0xFF1F3B5C), Color(0xFF112038), Icons.Default.AutoAwesome)
    "urban_fantasy" -> NovelGenreVisual(Color(0xFF312E81), Color(0xFF1B194D), Icons.Default.Psychology)
    "scifi" -> NovelGenreVisual(Color(0xFF0E4A66), Color(0xFF0A2438), Icons.Default.Public)
    "western_fantasy" -> NovelGenreVisual(Color(0xFF5A371A), Color(0xFF2E1B0C), Icons.Default.Security)
    "mystery" -> NovelGenreVisual(Color(0xFF334155), Color(0xFF18202C), Icons.Default.Search)
    "historical" -> NovelGenreVisual(Color(0xFF6B2435), Color(0xFF33101B), Icons.Default.AutoStories)
    "apocalypse" -> NovelGenreVisual(Color(0xFF5E2B20), Color(0xFF2B1310), Icons.Default.WarningAmber)
    "custom" -> NovelGenreVisual(Color(0xFF2E5A4C), Color(0xFF163028), Icons.Default.Edit)
    else -> NovelGenreVisual(Color(0xFF4C3075), Color(0xFF261742), Icons.Default.Landscape)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelStatusHudBar(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    triggeredLoreCount: Int = 0,
    onOpenStudio: (initialTab: Int) -> Unit,
    onEnterPureReading: () -> Unit,
    onFinishChapterAndAdvance: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit = { _, _, _ -> },
    onUpdateAuthorNote: (String) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val hud = if (novel.enableStatusHud) novel.currentStatus else null
    var expanded by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }

    if (showFinishDialog && activeCh != null) {
        FinishChapterDialog(
            activeChapter = activeCh,
            nextOrder = (chapters.maxOfOrNull { it.order } ?: 0) + 1,
            nextPlannedChapter = chapters.sortedBy { it.order }
                .firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 },
            onConfirm = { nextTitle, nextOutline, autoRecap ->
                onFinishChapterAndAdvance(nextTitle, nextOutline, autoRecap)
                showFinishDialog = false
            },
            onDismiss = { showFinishDialog = false },
        )
    }

    val chapterWords = activeCh?.wordCount ?: 0
    val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
    val progressRatio = (chapterWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
    val progressLabel = if (isZh) "$chapterWords / $targetWords 字" else "$chapterWords / ${targetWords}w"
    val summarySub = buildString {
        if (hud != null && hud.location.isNotBlank()) {
            append("📍 ${hud.location}")
            if (hud.time.isNotBlank()) append(" · ${hud.time}")
            append(" · ")
        }
        append(progressLabel)
        if (triggeredLoreCount > 0) {
            append(if (isZh) " · 设定 $triggeredLoreCount" else " · Lore $triggeredLoreCount")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.awning),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GuIconTile(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                tint = c.tagInk,
                backgroundColor = c.tag,
                size = 24.dp,
                iconSize = 13.5.dp,
                cornerRadius = 7.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = activeCh?.title ?: novel.title,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded) {
                    Text(
                        text = summarySub,
                        color = c.muted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!expanded) {
                Surface(
                    onClick = onEnterPureReading,
                    shape = RoundedCornerShape(999.dp),
                    color = c.surfaceSoft,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier.heightIn(min = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = strings.novel.pureReading,
                            tint = c.primarySoftInk,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (isZh) "阅读" else "Read",
                            color = c.ink,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    onClick = { onOpenStudio(0) },
                    shape = RoundedCornerShape(999.dp),
                    color = c.tag,
                    border = BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.45f)),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.novel.studioBtn,
                            color = c.tagInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        // Subtle chapter target-word progress strip
        LinearProgressIndicator(
            progress = { progressRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = if (progressRatio >= 1f) c.success else c.accent,
            trackColor = c.border.copy(alpha = 0.35f),
        )

        AnimatedVisibility(visible = expanded) {
            NovelChapterQuickDrawerContent(
                novel = novel,
                chapters = chapters,
                activeCh = activeCh,
                triggeredLoreCount = triggeredLoreCount,
                onOpenStudio = onOpenStudio,
                onEnterPureReading = onEnterPureReading,
                onRequestFinishChapter = { showFinishDialog = true },
                onUpdateAuthorNote = onUpdateAuthorNote,
            )
        }
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = c.border.copy(alpha = 0.85f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelChapterQuickDrawerContent(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    activeCh: NovelChapter?,
    triggeredLoreCount: Int,
    onOpenStudio: (Int) -> Unit,
    onEnterPureReading: () -> Unit,
    onRequestFinishChapter: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
    onEditAuthorNoteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val hud = if (novel.enableStatusHud) novel.currentStatus else null
    var editingNote by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }
    var noteDraft by remember(novel.authorNote) { mutableStateOf(novel.authorNote) }

    val chapterWords = activeCh?.wordCount ?: 0
    val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
    val totalBookWords = remember(chapters) { chapters.sumOf { it.wordCount } }
    val progressRatio = (chapterWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
    val progressLabel = if (isZh) "$chapterWords / $targetWords 字" else "$chapterWords / ${targetWords}w"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = c.surfaceSoft,
            border = BorderStroke(theme.borderWidth, c.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "《${novel.title}》",
                        color = c.ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (novel.genre.isNotBlank()) {
                        GuBadge(novel.genre.substringBefore(" /"), variant = GuBadgeVariant.HONEY)
                    }
                    GuBadge(
                        text = if (isZh) "全书 $totalBookWords 字" else "Total ${totalBookWords}w",
                        variant = GuBadgeVariant.BLUE,
                    )
                }

                // Chapter progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (progressRatio >= 1f) c.success else c.accent,
                        trackColor = c.border.copy(alpha = 0.45f),
                    )
                    Text(
                        text = progressLabel,
                        color = c.muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!activeCh?.outline.isNullOrBlank()) {
                    Text(
                        text = "${strings.novel.chapterOutline}：${activeCh!!.outline}",
                        color = c.muted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hud != null) {
                    val chips = buildList {
                        if (hud.location.isNotBlank()) add("📍 ${hud.location}")
                        if (hud.time.isNotBlank()) add("🕒 ${hud.time}")
                        if (hud.mood.isNotBlank()) add("🎭 ${hud.mood}")
                        if (hud.conflict.isNotBlank()) add("⚡ ${hud.conflict}")
                    }
                    if (chips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            chips.forEach { tag ->
                                GuBadge(tag, variant = GuBadgeVariant.NEUTRAL)
                            }
                        }
                    }
                    if (hud.present.isNotEmpty()) {
                        Text(
                            text = "👥 ${strings.novel.hudPresent}：${hud.present.joinToString("、")}",
                            color = c.ink,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (hud.foreshadows.isNotEmpty()) {
                        Text(
                            text = "🧵 ${if (isZh) "伏笔" else "Clues"}：${hud.foreshadows.joinToString(" · ")}",
                            color = c.primarySoftInk,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Quick Author Director Note editor (inline or delegated dialog)
                if (editingNote && onEditAuthorNoteClick == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            placeholder = strings.novel.notePlaceholder,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { editingNote = false; noteDraft = novel.authorNote }) {
                                Text(strings.common.cancel, fontSize = 11.5.sp, color = c.muted)
                            }
                            GuButton(
                                onClick = {
                                    onUpdateAuthorNote(noteDraft.trim())
                                    editingNote = false
                                },
                                compact = true,
                            ) {
                                Text(strings.common.save, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surface)
                            .border(theme.borderWidth, c.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .clickable {
                                if (onEditAuthorNoteClick != null) {
                                    onEditAuthorNoteClick()
                                } else {
                                    noteDraft = novel.authorNote
                                    editingNote = true
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
                        Text(
                            text = if (novel.authorNote.isNotBlank()) {
                                "${strings.novel.noteShort}：${novel.authorNote}"
                            } else {
                                if (isZh) "点击添加作者导演备注（每轮自动注入）…" else "Tap to add Author Director Note…"
                            },
                            color = if (novel.authorNote.isNotBlank()) c.ink else c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (triggeredLoreCount > 0) {
                    Surface(
                        onClick = { onOpenStudio(1) },
                        shape = RoundedCornerShape(999.dp),
                        color = c.successBg,
                        border = BorderStroke(theme.borderWidth, c.success.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = if (isZh) "✦ 本轮命中 $triggeredLoreCount 条世界书设定（点此管理）" else "✦ $triggeredLoreCount Worldbook entries active",
                            color = c.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GuButton(
                onClick = onEnterPureReading,
                compact = true,
                variant = GuButtonVariant.SUBTLE,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isZh) "纯净阅读" else "Reader", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            GuButton(
                onClick = onRequestFinishChapter,
                enabled = activeCh != null,
                compact = true,
                variant = GuButtonVariant.SUBTLE,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isZh) "完结切章" else "Next Ch.", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            GuButton(
                onClick = { onOpenStudio(0) },
                compact = true,
                variant = GuButtonVariant.HONEY,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.novel.studioBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelWizardDialog(
    hasModelSelected: Boolean = true,
    isCreatingWithAi: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (
        presetId: String,
        title: String,
        idea: String,
        pov: String,
        targetWords: Int,
        playStyle: String,
        antiAiRules: Boolean,
    ) -> Unit,
    onCreateWithAi: (
        presetId: String,
        title: String,
        idea: String,
        pov: String,
        targetWords: Int,
        playStyle: String,
        antiAiRules: Boolean,
        onDone: () -> Unit,
    ) -> Unit = { presetId, title, idea, pov, targetWords, playStyle, antiAiRules, onDone ->
        onCreate(presetId, title, idea, pov, targetWords, playStyle, antiAiRules)
        onDone()
    },
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var selectedPreset by remember { mutableStateOf(NovelPresets.ALL.first().id) }
    var bookTitle by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }
    var pov by remember { mutableStateOf(NovelPresets.ALL.first().pov) }
    var targetWords by remember { mutableIntStateOf(NovelPresets.ALL.first().targetWordsPerChapter) }
    var playStyle by remember { mutableStateOf("hybrid") }
    var antiAiRules by remember { mutableStateOf(true) }

    val activePreset = NovelPresets.findById(selectedPreset)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isCreatingWithAi) onDismiss() },
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.wizardTitle,
                subtitle = if (isZh) "挑选题材模板或自由原创，可由 AI 一键生成大纲、世界书与第一章开篇" else "Choose a genre template or go freeform — optionally let AI craft your outline & Chapter 1",
                onClose = { if (!isCreatingWithAi) onDismiss() },
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GuSectionHeader(
                    title = strings.novel.wizardGenreStep,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(NovelPresets.ALL, key = { it.id }) { preset ->
                        val isSelected = selectedPreset == preset.id
                        val visual = remember(preset.id) { resolveNovelGenreVisual(preset.id) }
                        val label = strings.novel.genres[preset.id] ?: preset.name

                        Surface(
                            onClick = {
                                selectedPreset = preset.id
                                pov = preset.pov
                                targetWords = preset.targetWordsPerChapter
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = c.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.6.dp else theme.borderWidth,
                                color = if (isSelected) c.primary else c.border,
                            ),
                            shadowElevation = if (isSelected && !theme.isDark) 3.dp else 0.dp,
                            modifier = Modifier
                                .width(146.dp)
                                .height(104.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .background(Brush.horizontalGradient(listOf(visual.startColor, visual.endColor)))
                                        .padding(horizontal = 10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        GuIconTile(
                                            icon = visual.icon,
                                            tint = Color(0xFFFFF5E1),
                                            backgroundColor = Color.White.copy(alpha = 0.16f),
                                            size = 24.dp,
                                            iconSize = 14.dp,
                                            cornerRadius = 7.dp,
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(c.primary),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) c.primarySoftInk else c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (isZh) "约 ${preset.targetWordsPerChapter} 字/章" else "~${preset.targetWordsPerChapter}w / ch",
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Preset Preview
                GuGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.78f else 0.85f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = activePreset.genre,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GuBadge(activePreset.firstChapterTitle, variant = GuBadgeVariant.HONEY)
                        }
                        Text(
                            text = activePreset.synopsis,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.5.sp,
                        )
                        Text(
                            text = if (isZh) "文风：${activePreset.style}" else "Style: ${activePreset.style}",
                            color = c.primarySoftInk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Book Title & Premise
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(title = if (isZh) "书名与核心构思" else "Book Details & Premise")
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuTextField(
                                value = bookTitle,
                                onValueChange = { bookTitle = it },
                                label = strings.novel.wizardBookTitle,
                                placeholder = strings.novel.genres[activePreset.id] ?: activePreset.name,
                            )
                            GuTextField(
                                value = idea,
                                onValueChange = { idea = it },
                                label = strings.novel.wizardIdea,
                                placeholder = strings.novel.wizardIdeaPlaceholder,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                            )
                        }
                    }
                }

                // Writing Craft Options
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuSectionHeader(title = if (isZh) "视角、节奏与玩法" else "POV, Pacing & Play Style")
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.povLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(
                                        "third_limited" to strings.novel.povThirdLimited,
                                        "first" to strings.novel.povFirst,
                                        "third_omniscient" to strings.novel.povThirdOmniscient,
                                    ).forEach { (k, label) ->
                                        GuPill(label = label, selected = pov == k, onClick = { pov = k })
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.playStyleLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(
                                        "hybrid" to strings.novel.playHybrid,
                                        "author" to strings.novel.playAuthor,
                                        "interactive" to strings.novel.playInteractive,
                                    ).forEach { (k, label) ->
                                        GuPill(label = label, selected = playStyle == k, onClick = { playStyle = k })
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(strings.novel.chapterTargetWords, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(1500, 2200, 2800, 3500, 5000).forEach { w ->
                                        GuPill(
                                            label = if (isZh) "${w}字" else "${w}w",
                                            selected = targetWords == w,
                                            onClick = { targetWords = w },
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.7f))
                            GuSwitchRow(
                                label = strings.novel.antiAiToggle,
                                checked = antiAiRules,
                                onCheckedChange = { antiAiRules = it },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.85f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isCreatingWithAi) {
                    Text(
                        text = "✨ ${strings.novel.wizardGenerating}",
                        color = c.primarySoftInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuButton(
                        onClick = {
                            onCreate(selectedPreset, bookTitle, idea, pov, targetWords, playStyle, antiAiRules)
                            onDismiss()
                        },
                        enabled = !isCreatingWithAi,
                        variant = GuButtonVariant.SUBTLE,
                        modifier = Modifier.weight(0.9f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(strings.novel.wizardEmptyBtn, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    GuButton(
                        onClick = {
                            onCreateWithAi(
                                selectedPreset,
                                bookTitle,
                                idea,
                                pov,
                                targetWords,
                                playStyle,
                                antiAiRules,
                                onDismiss,
                            )
                        },
                        enabled = hasModelSelected && !isCreatingWithAi,
                        loading = isCreatingWithAi,
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.weight(1.3f),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isZh) "AI 一键开书" else "AI Generate Book",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelStudioSheet(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    lore: List<NovelLoreEntry>,
    messages: List<ChatMessage>,
    triggeredLoreIds: Set<String> = emptySet(),
    initialTab: Int = 0,
    draftOptions: List<NovelDraftOption>,
    outlineReply: String,
    polishResult: String,
    isBusy: Boolean,
    isExtractingLore: Boolean = false,
    isGeneratingRecap: Boolean = false,
    isSyncingChapters: Boolean = false,
    onSelectChapter: (String) -> Unit,
    onAddChapter: (String, String) -> Unit,
    onUpdateChapter: (NovelChapter) -> Unit = {},
    onDeleteChapter: (String) -> Unit = {},
    onFinishChapterAndAdvance: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit = { _, _, _ -> },
    onGenerateChapterRecap: (String) -> Unit = {},
    onAddLore: (String, String, String, String, Boolean) -> Unit,
    onUpdateLore: (NovelLoreEntry) -> Unit = {},
    onToggleLoreEnabled: (String) -> Unit = {},
    onToggleLoreAlwaysActive: (String) -> Unit = {},
    onDeleteLore: (String) -> Unit,
    onExtractLoreNow: () -> Unit = {},
    onSaveForeshadowToLore: (String) -> Unit = {},
    onUpdateConfig: ((NovelConfig) -> NovelConfig) -> Unit,
    onGenerate3Paths: (String) -> Unit,
    onAdoptDraft: (NovelDraftOption, Boolean) -> Unit,
    onOutlineChat: (String) -> Unit,
    onApplyOutlineToBook: (String) -> Unit = {},
    onSyncChaptersFromOutline: (String) -> Unit = {},
    onPolishLast: (instruction: String, presetMode: String?) -> Unit,
    onApplyPolishAsVariant: (String) -> Unit = {},
    onFillComposerDraft: (String) -> Unit = {},
    onExportTxt: (android.net.Uri, Boolean) -> Unit,
    onExportMd: (android.net.Uri, Boolean) -> Unit,
    onToast: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    // Tab 0 state
    var newChapTitle by remember { mutableStateOf("") }
    var newChapOutline by remember { mutableStateOf("") }
    var showAddChapterForm by remember { mutableStateOf(false) }
    var editChapterTarget by remember { mutableStateOf<NovelChapter?>(null) }
    var deleteChapterTarget by remember { mutableStateOf<NovelChapter?>(null) }
    var showFinishChapterDialog by remember { mutableStateOf(false) }
    var showInlineOutlineEditor by remember { mutableStateOf(false) }
    var inlineOutlineDraft by remember(novel.mainOutline) { mutableStateOf(novel.mainOutline) }

    // Tab 1 state
    var loreSearch by remember { mutableStateOf("") }
    var loreFilterCat by remember { mutableStateOf("all") }
    var showLoreForm by remember { mutableStateOf(false) }
    var editingLoreId by remember { mutableStateOf<String?>(null) }
    var loreCat by remember { mutableStateOf("character") }
    var loreTitle by remember { mutableStateOf("") }
    var loreKeywords by remember { mutableStateOf("") }
    var loreAliases by remember { mutableStateOf("") }
    var lorePriority by remember { mutableIntStateOf(50) }
    var loreContent by remember { mutableStateOf("") }
    var loreAlways by remember { mutableStateOf(false) }

    // Tab 2 state
    var outlinePrompt by remember { mutableStateOf("") }
    var polishInstruction by remember { mutableStateOf("") }
    var draftHint by remember { mutableStateOf("") }

    // Tab 3 state (Book Profile Editor + Export)
    var bookTitleDraft by remember(novel.title) { mutableStateOf(novel.title) }
    var bookGenreDraft by remember(novel.genre) { mutableStateOf(novel.genre) }
    var bookSynopsisDraft by remember(novel.synopsis) { mutableStateOf(novel.synopsis) }
    var bookWorldviewDraft by remember(novel.worldview) { mutableStateOf(novel.worldview) }
    var bookOutlineDraft by remember(novel.mainOutline) { mutableStateOf(novel.mainOutline) }
    var bookAuthorNoteDraft by remember(novel.authorNote) { mutableStateOf(novel.authorNote) }
    var bookStyleDraft by remember(novel.style) { mutableStateOf(novel.style) }
    var bookStyleSampleDraft by remember(novel.styleSample) { mutableStateOf(novel.styleSample) }
    var exportIncludeLore by remember { mutableStateOf(true) }

    val profileDirty = bookTitleDraft != novel.title ||
        bookGenreDraft != novel.genre ||
        bookSynopsisDraft != novel.synopsis ||
        bookWorldviewDraft != novel.worldview ||
        bookOutlineDraft != novel.mainOutline ||
        bookAuthorNoteDraft != novel.authorNote ||
        bookStyleDraft != novel.style ||
        bookStyleSampleDraft != novel.styleSample

    val hasAssistantProse = remember(messages) {
        TimelineReconciler.activeMessages(messages).any { it.role == "assistant" && NovelExport.stripInternalTags(it.content).isNotBlank() }
    }
    val activeCh = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    val totalBookWords = remember(chapters) { chapters.sumOf { it.wordCount } }

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExportTxt(uri, exportIncludeLore)
    }
    val exportMdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onExportMd(uri, exportIncludeLore)
    }

    // Chapter Edit Dialog
    editChapterTarget?.let { target ->
        NovelChapterEditDialog(
            chapter = target,
            isGeneratingRecap = isGeneratingRecap,
            onGenerateRecap = { onGenerateChapterRecap(target.id) },
            onSave = { updated ->
                onUpdateChapter(updated)
                editChapterTarget = null
            },
            onDismiss = { editChapterTarget = null },
        )
    }

    // Delete Chapter Confirmation
    deleteChapterTarget?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = if (isZh) "确定删除《${target.title}》？已生成的正文消息不会被删除，将归入相邻章节。" else "Delete chapter \"${target.title}\"? Prose messages will remain in adjacent chapters.",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteChapter(target.id)
                deleteChapterTarget = null
            },
            onDismiss = { deleteChapterTarget = null },
        )
    }

    // Finish Active Chapter & Advance Dialog
    if (showFinishChapterDialog && activeCh != null) {
        FinishChapterDialog(
            activeChapter = activeCh,
            nextOrder = (chapters.maxOfOrNull { it.order } ?: 0) + 1,
            nextPlannedChapter = chapters.sortedBy { it.order }
                .firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 },
            onConfirm = { nextTitle, nextOutline, autoRecap ->
                onFinishChapterAndAdvance(nextTitle, nextOutline, autoRecap)
                showFinishChapterDialog = false
            },
            onDismiss = { showFinishChapterDialog = false },
        )
    }

    val tabLabels = remember(strings, isZh) {
        listOf(
            strings.novel.tabChapters,
            strings.novel.tabLore,
            if (isZh) "工坊工具" else "Tools",
            if (isZh) "设定导出" else "Book",
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.novel.studioTitle,
                subtitle = "《${novel.title}》 · ${if (isZh) "共 ${chapters.size} 章 · $totalBookWords 字" else "${chapters.size} ch · ${totalBookWords}w"}",
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                GuSegmentedControl(
                    items = tabLabels,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (tab) {
                // ==================== TAB 0: CHAPTERS & MASTER OUTLINE ====================
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__book_progress__") {
                            val activeWords = activeCh?.wordCount ?: 0
                            val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
                            val pct = (activeWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = activeCh?.title ?: novel.title,
                                                color = c.ink,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (isZh) {
                                                    "本章 $activeWords / $targetWords 字 · 全书 $totalBookWords 字"
                                                } else {
                                                    "Chapter $activeWords / ${targetWords}w · Book ${totalBookWords}w"
                                                },
                                                color = c.muted,
                                                fontSize = 11.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                        GuButton(
                                            onClick = { showFinishChapterDialog = true },
                                            enabled = activeCh != null,
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isZh) "完结切章" else "Finish Ch.", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = if (pct >= 1f) c.success else c.primary,
                                        trackColor = c.border.copy(alpha = 0.45f),
                                    )
                                }
                            }
                        }

                        // Quick Master Outline Viewer / Editor
                        item(key = "__master_outline__") {
                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showInlineOutlineEditor = !showInlineOutlineEditor },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = strings.novel.masterOutline,
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = if (showInlineOutlineEditor) strings.common.close else strings.common.edit,
                                                color = c.primary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Icon(
                                                imageVector = if (showInlineOutlineEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = c.muted,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                    if (showInlineOutlineEditor) {
                                        GuTextField(
                                            value = inlineOutlineDraft,
                                            onValueChange = { inlineOutlineDraft = it },
                                            placeholder = strings.novel.masterOutlinePlaceholder,
                                            singleLine = false,
                                            minLines = 3,
                                            maxLines = 8,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        ) {
                                            TextButton(onClick = { tab = 2 }) {
                                                Text(strings.novel.outlineChatBtn, fontSize = 12.sp, color = c.primary)
                                            }
                                            GuButton(
                                                onClick = {
                                                    onUpdateConfig { it.copy(mainOutline = inlineOutlineDraft.trim()) }
                                                    showInlineOutlineEditor = false
                                                    onToast(strings.novel.outlineChatApplied)
                                                },
                                                compact = true,
                                            ) {
                                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = novel.mainOutline.ifBlank {
                                                if (isZh) "尚未填写全书主线总纲，点击展开编辑或在「工坊工具」中与 AI 共创。" else "No master outline yet — tap to edit or brainstorm with AI in Tools."
                                            },
                                            color = c.muted,
                                            fontSize = 12.sp,
                                            lineHeight = 17.5.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // Chapter List Header + New Chapter toggle
                        item(key = "__chapter_list_header__") {
                            GuSectionHeader(
                                title = strings.novel.tabChapters,
                                trailing = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuBadge("${chapters.size}", variant = GuBadgeVariant.BLUE)
                                        Surface(
                                            onClick = { showAddChapterForm = !showAddChapterForm },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.primarySoft,
                                            border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.4f)),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = c.primarySoftInk, modifier = Modifier.size(13.dp))
                                                Text(
                                                    text = strings.novel.newChapterBtn,
                                                    color = c.primarySoftInk,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        if (showAddChapterForm) {
                            item(key = "__chapter_form__") {
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GuTextField(
                                            value = newChapTitle,
                                            onValueChange = { newChapTitle = it },
                                            placeholder = if (isZh) "新章节标题（留空默认第${chapters.size + 1}章）" else "New chapter title",
                                        )
                                        GuTextField(
                                            value = newChapOutline,
                                            onValueChange = { newChapOutline = it },
                                            placeholder = strings.novel.chapterOutlinePlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            TextButton(onClick = { showAddChapterForm = false }) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                                            }
                                            GuButton(
                                                onClick = {
                                                    onAddChapter(newChapTitle, newChapOutline)
                                                    newChapTitle = ""
                                                    newChapOutline = ""
                                                    showAddChapterForm = false
                                                },
                                                compact = true,
                                            ) {
                                                Text(strings.novel.newChapterBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(chapters.sortedBy { it.order }, key = { it.id }) { ch ->
                            val isActive = ch.id == novel.activeChapterId
                            val statusText = localizeChapterStatus(ch.status, isZh)
                            val chTarget = novel.targetWordsPerChapter.coerceAtLeast(500)
                            val chProgress = (ch.wordCount.toFloat() / chTarget.toFloat()).coerceIn(0f, 1f)
                            val accentBarColor = if (isActive) c.primary else if (ch.status == "done") c.success else c.border

                            Surface(
                                onClick = { onSelectChapter(ch.id) },
                                shape = RoundedCornerShape(theme.cardRadius),
                                color = if (isActive) c.primarySoft.copy(alpha = if (theme.isDark) 0.72f else 0.78f) else c.surface,
                                border = BorderStroke(
                                    width = theme.borderWidth,
                                    color = if (isActive) c.primary.copy(alpha = 0.55f) else c.border,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawRect(
                                                color = accentBarColor,
                                                topLeft = Offset.Zero,
                                                size = Size(3.5.dp.toPx(), size.height),
                                            )
                                        }
                                        .padding(start = 15.dp, end = 12.dp, top = 11.dp, bottom = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = ch.title,
                                            color = if (isActive) c.primarySoftInk else c.ink,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        GuBadge(
                                            text = if (isZh) "${ch.wordCount}字 · $statusText" else "${ch.wordCount}w · $statusText",
                                            variant = when {
                                                isActive -> GuBadgeVariant.BLUE
                                                ch.status == "done" -> GuBadgeVariant.EMERALD
                                                else -> GuBadgeVariant.NEUTRAL
                                            },
                                        )
                                    }

                                    if (ch.wordCount > 0) {
                                        LinearProgressIndicator(
                                            progress = { chProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(999.dp)),
                                            color = if (ch.status == "done" || chProgress >= 1f) c.success else c.primary,
                                            trackColor = c.border.copy(alpha = 0.4f),
                                        )
                                    }

                                    if (ch.outline.isNotBlank()) {
                                        Text(
                                            text = "${strings.novel.chapterOutline}：${ch.outline}",
                                            color = c.muted,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (ch.summary.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = c.surfaceSoft.copy(alpha = 0.9f),
                                            border = BorderStroke(0.6.dp, c.border),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = "${strings.novel.chapterSummaryLabel}：${ch.summary}",
                                                color = c.ink.copy(alpha = 0.85f),
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.5.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        if (!isActive) {
                                            TextButton(
                                                onClick = { onSelectChapter(ch.id) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = if (isZh) "设为当前" else "Activate",
                                                    color = c.primary,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        } else {
                                            GuBadge(if (isZh) "当前写作中" else "Active", variant = GuBadgeVariant.HONEY)
                                        }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val txt = NovelExport.formatSingleChapter(novel, ch, chapters, messages, markdown = false)
                                                copyTextToClipboard(ctx, ch.title, txt)
                                                onToast(strings.common.copied)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = strings.common.copy, tint = c.muted, modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(
                                            onClick = { editChapterTarget = ch },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = strings.common.edit, tint = c.primary, modifier = Modifier.size(16.dp))
                                        }
                                        if (chapters.size > 1) {
                                            IconButton(
                                                onClick = { deleteChapterTarget = ch },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 1: WORLDBOOK / LORE ====================
                1 -> {
                    val filteredLore = remember(lore, loreSearch, loreFilterCat) {
                        val byCat = if (loreFilterCat == "all") {
                            lore
                        } else {
                            lore.filter { it.category.equals(loreFilterCat, ignoreCase = true) }
                        }
                        FuzzyMatcher.filterAndRank(byCat, loreSearch) { entry ->
                            listOf(entry.title, entry.content, entry.keywords.joinToString(" "), entry.aliases.joinToString(" "))
                        }
                    }

                    fun resetLoreForm() {
                        editingLoreId = null
                        loreCat = "character"
                        loreTitle = ""
                        loreKeywords = ""
                        loreAliases = ""
                        lorePriority = 50
                        loreContent = ""
                        loreAlways = false
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__lore_toolbar__") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuButton(
                                    onClick = {
                                        if (showLoreForm && editingLoreId == null) {
                                            showLoreForm = false
                                        } else {
                                            resetLoreForm()
                                            showLoreForm = true
                                        }
                                    },
                                    compact = true,
                                    variant = if (showLoreForm) GuButtonVariant.SUBTLE else GuButtonVariant.PRIMARY,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.novel.loreAddBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                GuButton(
                                    onClick = onExtractLoreNow,
                                    enabled = hasAssistantProse && !isExtractingLore,
                                    loading = isExtractingLore,
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    modifier = Modifier.weight(1.25f),
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isExtractingLore) strings.novel.loreExtracting else strings.novel.loreExtractBtn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        if (showLoreForm) {
                            item(key = "__lore_form__") {
                                GuCard(modifier = Modifier.fillMaxWidth(), borderColor = c.primary.copy(alpha = 0.4f)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = if (editingLoreId != null) {
                                                if (isZh) "编辑世界书条目" else "Edit Worldbook Entry"
                                            } else {
                                                strings.novel.loreAddBtn
                                            },
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(listOf("character", "location", "item", "faction", "rule", "foreshadow", "plot", "world"), key = { it }) { cat ->
                                                GuPill(
                                                    label = strings.novel.loreCats[cat] ?: cat,
                                                    selected = loreCat == cat,
                                                    onClick = { loreCat = cat },
                                                )
                                            }
                                        }
                                        GuTextField(
                                            value = loreTitle,
                                            onValueChange = { loreTitle = it },
                                            placeholder = strings.novel.loreTitlePlaceholder,
                                        )
                                        GuTextField(
                                            value = loreKeywords,
                                            onValueChange = { loreKeywords = it },
                                            placeholder = strings.novel.loreKeywordsPlaceholder,
                                        )
                                        GuTextField(
                                            value = loreAliases,
                                            onValueChange = { loreAliases = it },
                                            placeholder = if (isZh) "别名/简称（逗号分隔，选填）" else "Aliases (comma-separated, optional)",
                                        )
                                        GuTextField(
                                            value = loreContent,
                                            onValueChange = { loreContent = it },
                                            placeholder = strings.novel.loreContentPlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 5,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = if (isZh) "优先级" else "Priority",
                                                color = c.muted,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            listOf(30 to (if (isZh) "低" else "Low"), 50 to (if (isZh) "中" else "Mid"), 80 to (if (isZh) "高" else "High"), 100 to (if (isZh) "核心" else "Core")).forEach { (pVal, pLabel) ->
                                                GuPill(label = pLabel, selected = lorePriority == pVal, onClick = { lorePriority = pVal })
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                GuSwitchRow(
                                                    label = strings.novel.loreAlways,
                                                    checked = loreAlways,
                                                    onCheckedChange = { loreAlways = it },
                                                )
                                            }
                                            TextButton(onClick = { resetLoreForm(); showLoreForm = false }) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                                            }
                                            val canSaveLore = loreTitle.isNotBlank() && loreContent.isNotBlank()
                                            GuButton(
                                                onClick = {
                                                    if (!canSaveLore) return@GuButton
                                                    val editingExisting = editingLoreId?.let { id -> lore.firstOrNull { it.id == id } }
                                                    if (editingExisting != null) {
                                                        val kws = loreKeywords.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                                                        val als = loreAliases.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                                                        onUpdateLore(
                                                            editingExisting.copy(
                                                                category = loreCat,
                                                                title = loreTitle.trim(),
                                                                keywords = kws.ifEmpty { listOf(loreTitle.trim()) },
                                                                aliases = als,
                                                                priority = lorePriority,
                                                                content = loreContent.trim(),
                                                                alwaysActive = loreAlways,
                                                            ),
                                                        )
                                                    } else {
                                                        onAddLore(loreCat, loreTitle, loreKeywords, loreContent, loreAlways)
                                                    }
                                                    resetLoreForm()
                                                    showLoreForm = false
                                                },
                                                enabled = canSaveLore,
                                                compact = true,
                                            ) {
                                                Text(
                                                    text = if (editingLoreId != null) strings.common.save else strings.novel.loreAddBtn,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "__lore_search_filter__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuCompactSearchBar(
                                    value = loreSearch,
                                    onValueChange = { loreSearch = it },
                                    placeholder = if (isZh) "搜索世界书标题、关键词或设定内容…" else "Search worldbook entries…",
                                    clearContentDescription = strings.common.clear,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf("all", "character", "location", "item", "faction", "rule", "foreshadow", "plot"), key = { it }) { cat ->
                                        GuPill(
                                            label = strings.novel.loreCats[cat] ?: cat,
                                            selected = loreFilterCat == cat,
                                            onClick = { loreFilterCat = cat },
                                        )
                                    }
                                }
                                GuSectionHeader(
                                    title = strings.novel.loreHeader,
                                    trailing = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (triggeredLoreIds.isNotEmpty()) {
                                                GuBadge(
                                                    text = if (isZh) "命中 ${triggeredLoreIds.size}" else "${triggeredLoreIds.size} active",
                                                    variant = GuBadgeVariant.EMERALD,
                                                )
                                            }
                                            GuBadge(strings.novel.loreCount.fmt("count" to lore.size), variant = GuBadgeVariant.HONEY)
                                        }
                                    },
                                )
                            }
                        }

                        if (filteredLore.isEmpty()) {
                            item(key = "__lore_empty__") {
                                GuEmptyState(
                                    title = strings.novel.loreEmpty,
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                )
                            }
                        } else {
                            items(filteredLore, key = { it.id }) { entry ->
                                val isTriggered = entry.id in triggeredLoreIds
                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    borderColor = if (isTriggered) c.success.copy(alpha = 0.5f) else null,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuBadge(
                                            text = strings.novel.loreCats[entry.category] ?: entry.category,
                                            variant = GuBadgeVariant.HONEY,
                                        )
                                        Text(
                                            text = entry.title,
                                            color = if (entry.enabled) c.ink else c.muted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isTriggered) {
                                            GuBadge(if (isZh) "已触发" else "Hit", variant = GuBadgeVariant.EMERALD)
                                        }
                                        if (entry.alwaysActive) {
                                            GuBadge(strings.novel.loreAlways, variant = GuBadgeVariant.BLUE)
                                        }
                                        Switch(
                                            checked = entry.enabled,
                                            onCheckedChange = { onToggleLoreEnabled(entry.id) },
                                        )
                                    }
                                    if (entry.keywords.isNotEmpty()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "🔑 ${entry.keywords.joinToString(", ")}",
                                            color = c.muted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = entry.content,
                                        color = if (entry.enabled) c.muted else c.muted.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        lineHeight = 17.5.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuPill(
                                            label = if (entry.alwaysActive) {
                                                if (isZh) "✓ 常驻注入" else "✓ Always On"
                                            } else {
                                                if (isZh) "按关键词触发" else "Keyword Trigger"
                                            },
                                            selected = entry.alwaysActive,
                                            onClick = { onToggleLoreAlwaysActive(entry.id) },
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                editingLoreId = entry.id
                                                loreCat = entry.category
                                                loreTitle = entry.title
                                                loreKeywords = entry.keywords.joinToString(", ")
                                                loreAliases = entry.aliases.joinToString(", ")
                                                lorePriority = entry.priority
                                                loreContent = entry.content
                                                loreAlways = entry.alwaysActive
                                                showLoreForm = true
                                            },
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = strings.common.edit, tint = c.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteLore(entry.id) },
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 2: AI CRAFT TOOLS ====================
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuSectionHeader(title = if (isZh) "AI 创作工坊工具" else "Editorial Craft Tools")

                        // Tool 1: Three-Path Plot Branches
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(icon = Icons.Default.AutoAwesome, tint = c.tagInk, backgroundColor = c.tag)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.novel.threePathsBtn, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    Text(
                                        text = if (isZh) "推演三种不同走向的剧情草案，可直接发送或填入草稿" else "Generate 3 divergent plot paths to adopt or send",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = draftHint,
                                    onValueChange = { draftHint = it },
                                    placeholder = if (isZh) "分歧方向提示（可选，如：突然遇伏 / 内部反水）" else "Branch hint (optional)",
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onGenerate3Paths(draftHint) },
                                    loading = isBusy,
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = strings.novel.threePathsBtn, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (draftOptions.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                GuGroupedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = c.surfaceSoft,
                                    cornerRadius = 12.dp,
                                ) {
                                    draftOptions.forEachIndexed { idx, opt ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(opt.title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                                                if (opt.tone.isNotBlank()) {
                                                    GuBadge(opt.tone, variant = GuBadgeVariant.BLUE)
                                                }
                                            }
                                            Text(opt.summary, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                                            if (opt.opening.isNotBlank()) {
                                                Text(
                                                    text = opt.opening,
                                                    color = c.ink,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.5.sp,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                GuButton(
                                                    onClick = {
                                                        onAdoptDraft(opt, false)
                                                        onDismiss()
                                                    },
                                                    compact = true,
                                                    variant = GuButtonVariant.SUBTLE,
                                                ) {
                                                    Text(if (isZh) "填入草稿" else "Use Draft", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                                GuButton(
                                                    onClick = {
                                                        onAdoptDraft(opt, true)
                                                        onDismiss()
                                                    },
                                                    compact = true,
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(if (isZh) "按此推进" else "Write This", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        if (idx < draftOptions.lastIndex) {
                                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }

                        // Tool 2: Outline Co-Creation + Apply + Sync Chapters
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(icon = Icons.Default.Psychology, tint = c.primarySoftInk, backgroundColor = c.primarySoft)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.novel.outlineChatTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    Text(strings.novel.outlineChatSub, color = c.muted, fontSize = 11.5.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val quickPrompts = if (isZh) {
                                    listOf("规划后续4章细纲", "设计一个意料之外的反转", "梳理未回收伏笔清单")
                                } else {
                                    listOf("Plan next 4 chapter beats", "Design an unexpected twist", "Audit unresolved clues")
                                }
                                quickPrompts.forEach { qp ->
                                    GuPill(label = qp, selected = false, onClick = { outlinePrompt = qp })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = outlinePrompt,
                                    onValueChange = { outlinePrompt = it },
                                    placeholder = strings.novel.outlineChatPlaceholder,
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onOutlineChat(outlinePrompt) },
                                    enabled = !isBusy && outlinePrompt.isNotBlank(),
                                    loading = isBusy,
                                    compact = true,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Text(strings.chat.send, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (outlineReply.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(theme.borderWidth, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        Text(outlineReply, color = c.ink, fontSize = 12.5.sp, lineHeight = 19.sp)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            GuButton(
                                                onClick = {
                                                    copyTextToClipboard(ctx, "outline", outlineReply)
                                                    onToast(strings.common.copied)
                                                },
                                                compact = true,
                                                variant = GuButtonVariant.SUBTLE,
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            GuButton(
                                                onClick = { onApplyOutlineToBook(outlineReply) },
                                                compact = true,
                                                variant = GuButtonVariant.PRIMARY,
                                            ) {
                                                Text(strings.novel.outlineChatApply, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            GuButton(
                                                onClick = { onSyncChaptersFromOutline(outlineReply) },
                                                enabled = !isSyncingChapters,
                                                loading = isSyncingChapters,
                                                compact = true,
                                                variant = GuButtonVariant.HONEY,
                                            ) {
                                                Text(if (isZh) "提取并导入分章" else "Import Chapters", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tool 3: Prose Polish (4 Presets + Replace as Swipeable Variant)
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(icon = Icons.Default.AutoFixHigh, tint = c.success, backgroundColor = c.successBg)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isZh) "正文精修与去 AI 味" else "Prose Polish & De-AI",
                                        color = c.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = if (isZh) "精修上一轮正文，一键存入版本库（可 ◀ ▶ 对比原版）" else "Refine latest prose & save as swipeable variant",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    "describe" to strings.novel.polishDescribe,
                                    "deai" to strings.novel.polishDeai,
                                    "tighten" to strings.novel.polishTighten,
                                    "dialogue" to (if (isZh) "对白增色" else "Dialogue"),
                                ).forEach { (presetKey, label) ->
                                    GuPill(
                                        label = "✨ $label",
                                        selected = false,
                                        onClick = { if (!isBusy && hasAssistantProse) onPolishLast(polishInstruction, presetKey) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuTextField(
                                    value = polishInstruction,
                                    onValueChange = { polishInstruction = it },
                                    placeholder = if (isZh) "自定义精修要求（可选）" else "Custom polish direction (optional)",
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = { onPolishLast(polishInstruction, null) },
                                    enabled = !isBusy && hasAssistantProse,
                                    loading = isBusy,
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                ) {
                                    Text(if (isZh) "精修" else "Polish", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (polishResult.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = c.surfaceSoft,
                                    border = BorderStroke(theme.borderWidth, c.border),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(polishResult, color = c.ink, fontSize = 12.5.sp, lineHeight = 19.sp)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            GuButton(
                                                onClick = {
                                                    copyTextToClipboard(ctx, "polish", polishResult)
                                                    onToast(strings.common.copied)
                                                },
                                                compact = true,
                                                variant = GuButtonVariant.SUBTLE,
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.common.copy, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            GuButton(
                                                onClick = {
                                                    onApplyPolishAsVariant(polishResult)
                                                    onDismiss()
                                                },
                                                compact = true,
                                            ) {
                                                Text(if (isZh) "应用为新版本" else "Apply Variant", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            GuButton(
                                                onClick = {
                                                    onFillComposerDraft(polishResult)
                                                    onDismiss()
                                                },
                                                compact = true,
                                                variant = GuButtonVariant.SUBTLE,
                                            ) {
                                                Text(if (isZh) "填入输入框" else "To Draft", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tool 4: Active Foreshadow Tracker
                        val hudForeshadows = novel.currentStatus?.foreshadows.orEmpty()
                        val loreForeshadows = lore.filter { it.category.equals("foreshadow", ignoreCase = true) && !it.stale }
                        if (hudForeshadows.isNotEmpty() || loreForeshadows.isNotEmpty()) {
                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuIconTile(icon = Icons.Default.BookmarkAdd, tint = c.accent, backgroundColor = c.accentSoft)
                                        Text(
                                            text = if (isZh) "伏笔线索追踪" else "Foreshadow Tracker",
                                            color = c.ink,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    hudForeshadows.forEach { fs ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(c.surfaceSoft)
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text("🧵 $fs", color = c.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            TextButton(
                                                onClick = { onSaveForeshadowToLore(fs) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(if (isZh) "收录世界书" else "Save Lore", fontSize = 11.sp, color = c.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    loreForeshadows.forEach { entry ->
                                        Text(
                                            text = "• ${entry.title}：${entry.content}",
                                            color = c.muted,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 3: FULL BOOK SETTINGS & EXPORT ====================
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuSectionHeader(
                            title = if (isZh) "作品档案与设定" else "Book Profile & World",
                            trailing = {
                                if (profileDirty) {
                                    GuButton(
                                        onClick = {
                                            onUpdateConfig { cur ->
                                                cur.copy(
                                                    title = bookTitleDraft.trim().ifBlank { cur.title },
                                                    genre = bookGenreDraft.trim(),
                                                    synopsis = bookSynopsisDraft.trim(),
                                                    worldview = bookWorldviewDraft.trim(),
                                                    mainOutline = bookOutlineDraft.trim(),
                                                    authorNote = bookAuthorNoteDraft.trim(),
                                                    style = bookStyleDraft.trim(),
                                                    styleSample = bookStyleSampleDraft.trim(),
                                                )
                                            }
                                            onToast(if (isZh) "已保存本书档案设定" else "Book profile saved")
                                        },
                                        compact = true,
                                    ) {
                                        Text(strings.common.save, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                        )
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuTextField(
                                    value = bookTitleDraft,
                                    onValueChange = { bookTitleDraft = it },
                                    label = if (isZh) "书名" else "Book Title",
                                )
                                GuTextField(
                                    value = bookGenreDraft,
                                    onValueChange = { bookGenreDraft = it },
                                    label = if (isZh) "题材定位" else "Genre",
                                )
                                GuTextField(
                                    value = bookSynopsisDraft,
                                    onValueChange = { bookSynopsisDraft = it },
                                    label = strings.novel.blurbLabel,
                                    placeholder = strings.novel.blurbPlaceholder,
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                )
                                GuTextField(
                                    value = bookWorldviewDraft,
                                    onValueChange = { bookWorldviewDraft = it },
                                    label = if (isZh) "世界观基石" else "Worldview",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 5,
                                )
                                GuTextField(
                                    value = bookOutlineDraft,
                                    onValueChange = { bookOutlineDraft = it },
                                    label = strings.novel.masterOutline,
                                    placeholder = strings.novel.masterOutlinePlaceholder,
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 6,
                                )
                                GuTextField(
                                    value = bookAuthorNoteDraft,
                                    onValueChange = { bookAuthorNoteDraft = it },
                                    label = strings.novel.noteTitle,
                                    placeholder = strings.novel.notePlaceholder,
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                )
                                GuTextField(
                                    value = bookStyleDraft,
                                    onValueChange = { bookStyleDraft = it },
                                    label = strings.novel.stylePromptLabel,
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 3,
                                )
                                GuTextField(
                                    value = bookStyleSampleDraft,
                                    onValueChange = { bookStyleSampleDraft = it },
                                    label = strings.novel.styleSampleLabel,
                                    placeholder = if (isZh) "粘贴 100-400 字你偏爱的文风样段，AI 将贴近其句式语感…" else "Paste a short prose excerpt to match cadence & voice…",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 5,
                                )
                                if (profileDirty) {
                                    GuButton(
                                        onClick = {
                                            onUpdateConfig { cur ->
                                                cur.copy(
                                                    title = bookTitleDraft.trim().ifBlank { cur.title },
                                                    genre = bookGenreDraft.trim(),
                                                    synopsis = bookSynopsisDraft.trim(),
                                                    worldview = bookWorldviewDraft.trim(),
                                                    mainOutline = bookOutlineDraft.trim(),
                                                    authorNote = bookAuthorNoteDraft.trim(),
                                                    style = bookStyleDraft.trim(),
                                                    styleSample = bookStyleSampleDraft.trim(),
                                                )
                                            }
                                            onToast(if (isZh) "已保存本书档案设定" else "Book profile saved")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (isZh) "保存作品档案" else "Save Book Profile", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        GuSectionHeader(title = if (isZh) "视角、字数与预算" else "POV, Pacing & Lore Budget")
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.novel.povLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "third_limited" to strings.novel.povThirdLimited,
                                            "first" to strings.novel.povFirst,
                                            "third_omniscient" to strings.novel.povThirdOmniscient,
                                        ).forEach { (k, label) ->
                                            GuPill(label = label, selected = novel.pov == k, onClick = { onUpdateConfig { it.copy(pov = k) } })
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.novel.playStyleLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "hybrid" to strings.novel.playHybrid,
                                            "author" to strings.novel.playAuthor,
                                            "interactive" to strings.novel.playInteractive,
                                        ).forEach { (k, label) ->
                                            GuPill(
                                                label = label,
                                                selected = novel.playStyle == k,
                                                onClick = { onUpdateConfig { it.copy(playStyle = k, enableChoices = if (k == "author") false else it.enableChoices) } },
                                            )
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.novel.chapterTargetWords, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(1500, 2200, 2800, 3500, 5000).forEach { w ->
                                            GuPill(
                                                label = if (isZh) "${w}字" else "${w}w",
                                                selected = novel.targetWordsPerChapter == w,
                                                onClick = { onUpdateConfig { it.copy(targetWordsPerChapter = w) } },
                                            )
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (isZh) "世界书注入预算 (Tokens)" else "Worldbook Token Budget", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(800, 1600, 2400, 3600).forEach { b ->
                                            GuPill(
                                                label = "$b",
                                                selected = novel.loreTokenBudget == b,
                                                onClick = { onUpdateConfig { it.copy(loreTokenBudget = b) } },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        GuSectionHeader(title = strings.novel.rulesTitle)
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                GuSwitchRow(
                                    label = strings.novel.antiAiToggle,
                                    checked = novel.antiAiRules,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(antiAiRules = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.hudToggle,
                                    checked = novel.enableStatusHud,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableStatusHud = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.autoSyncLoreToggle,
                                    checked = novel.autoSyncLore,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(autoSyncLore = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.autoAdvanceChapterToggle,
                                    checked = novel.autoAdvanceChapter,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(autoAdvanceChapter = v) } },
                                )
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                                GuSwitchRow(
                                    label = strings.novel.choicesToggle,
                                    checked = novel.enableChoices,
                                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                                )
                            }
                        }

                        GuSectionHeader(title = if (isZh) "导出成书" else "Export Manuscript")
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                GuSwitchRow(
                                    label = strings.novel.exportIncludeLore,
                                    checked = exportIncludeLore,
                                    onCheckedChange = { exportIncludeLore = it },
                                )
                            }
                            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.75f))
                            GuSettingsRow(
                                title = strings.novel.exportTxt,
                                subtitle = if (isZh) "适合通用阅读器与手机本地备份 (.txt)" else "Plain text manuscript for any e-reader (.txt)",
                                icon = Icons.Default.Description,
                                iconTint = c.primarySoftInk,
                                iconBg = c.primarySoft,
                                showChevron = true,
                                showDivider = true,
                                onClick = {
                                    exportTxtLauncher.launch(NovelExport.sanitizeFileName(novel.title, "txt"))
                                },
                            )
                            GuSettingsRow(
                                title = strings.novel.exportMd,
                                subtitle = if (isZh) "保留章节标题与排版格式 (.md)" else "Structured chapters with Markdown formatting (.md)",
                                icon = Icons.Default.Download,
                                iconTint = c.tagInk,
                                iconBg = c.tag,
                                showChevron = true,
                                showDivider = true,
                                onClick = {
                                    exportMdLauncher.launch(NovelExport.sanitizeFileName(novel.title, "md"))
                                },
                            )
                            GuSettingsRow(
                                title = if (isZh) "复制全书正文到剪贴板" else "Copy Full Manuscript",
                                subtitle = if (isZh) "一键复制当前 $totalBookWords 字完整稿件" else "Copy full $totalBookWords-word manuscript",
                                icon = Icons.Default.ContentCopy,
                                iconTint = c.success,
                                iconBg = c.successBg,
                                showChevron = false,
                                onClick = {
                                    val fullText = NovelExport.formatAsTxt(
                                        novel = novel,
                                        chapters = chapters,
                                        messages = messages,
                                        lore = lore,
                                        includeLoreAppendix = exportIncludeLore,
                                    )
                                    copyTextToClipboard(ctx, novel.title, fullText)
                                    onToast(strings.common.copied)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelChapterEditDialog(
    chapter: NovelChapter,
    isGeneratingRecap: Boolean,
    onGenerateRecap: () -> Unit,
    onSave: (NovelChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var status by remember(chapter.id) { mutableStateOf(chapter.status) }
    var outline by remember(chapter.id) { mutableStateOf(chapter.outline) }
    var summary by remember(chapter.id, chapter.summary) { mutableStateOf(chapter.summary) }

    LaunchedEffect(chapter.summary) {
        if (chapter.summary.isNotBlank() && summary.isBlank()) {
            summary = chapter.summary
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = if (isZh) "编辑第 ${chapter.order} 章" else "Edit Chapter ${chapter.order}",
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = if (isZh) "章节标题" else "Chapter Title",
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (isZh) "章节状态" else "Status", color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("draft", "writing", "done").forEach { st ->
                            GuPill(
                                label = localizeChapterStatus(st, isZh),
                                selected = status == st,
                                onClick = { status = st },
                            )
                        }
                    }
                }
                GuTextField(
                    value = outline,
                    onValueChange = { outline = it },
                    label = strings.novel.chapterOutline,
                    placeholder = strings.novel.chapterOutlinePlaceholder,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                )
                GuTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = strings.novel.chapterSummaryLabel,
                    placeholder = if (isZh) "本章完结后的前情梗概（将注入后续章节提示词）…" else "Chapter recap injected into future chapters…",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
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
        },
        confirmButton = {
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
                Text(strings.common.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun FinishChapterDialog(
    activeChapter: NovelChapter,
    nextOrder: Int,
    nextPlannedChapter: NovelChapter?,
    onConfirm: (nextTitle: String, nextOutline: String, autoRecap: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var nextTitle by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.title.orEmpty()) }
    var nextOutline by remember(nextPlannedChapter?.id) { mutableStateOf(nextPlannedChapter?.outline.orEmpty()) }
    var autoRecap by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = if (isZh) "完结《${activeChapter.title}》并开启新章" else "Finish \"${activeChapter.title}\"",
                color = c.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (nextPlannedChapter != null) {
                    GuBadge(
                        text = if (isZh) "已检测到预规划草稿：${nextPlannedChapter.title}" else "Next planned draft: ${nextPlannedChapter.title}",
                        variant = GuBadgeVariant.EMERALD,
                    )
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
                        minLines = 2,
                        maxLines = 4,
                    )
                }
                GuSwitchRow(
                    label = if (isZh) "AI 自动总结本章前情梗概" else "Auto-generate AI recap for finished chapter",
                    checked = autoRecap,
                    onCheckedChange = { autoRecap = it },
                )
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    val usePlanned = nextPlannedChapter != null
                    onConfirm(
                        if (usePlanned) "" else nextTitle,
                        if (usePlanned) "" else nextOutline,
                        autoRecap,
                    )
                },
                compact = true,
                variant = GuButtonVariant.HONEY,
            ) {
                Text(if (isZh) "确认切章" else "Start Next", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

private fun localizeChapterStatus(status: String, isZh: Boolean): String = when (status.lowercase()) {
    "writing", "in_progress" -> if (isZh) "连载中" else "Writing"
    "done", "completed", "finished" -> if (isZh) "已完成" else "Done"
    "draft" -> if (isZh) "草稿" else "Draft"
    else -> status
}

private fun copyTextToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
