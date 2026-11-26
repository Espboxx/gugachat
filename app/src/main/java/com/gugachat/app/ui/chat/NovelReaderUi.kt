package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelExport
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface ReaderRowItem {
    val key: String
    val chapterTitle: String

    data class BookCover(
        override val key: String,
        override val chapterTitle: String,
        val bookTitle: String,
        val genre: String,
        val synopsis: String,
        val totalWords: Int,
        val chapterCount: Int,
    ) : ReaderRowItem

    data class ChapterHeader(
        override val key: String,
        override val chapterTitle: String,
        val chapterId: String?,
        val order: Int?,
        val wordCount: Int,
        val outline: String,
    ) : ReaderRowItem

    data class Passage(
        override val key: String,
        override val chapterTitle: String,
        val prose: String,
        val showDividerBelow: Boolean,
    ) : ReaderRowItem

    data class ManuscriptTail(
        override val key: String,
        override val chapterTitle: String,
        val totalWords: Int,
    ) : ReaderRowItem
}

private data class ReaderPaperTheme(
    val bg: Color,
    val surface: Color,
    val chromeBg: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureReadingOverlay(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    messages: List<ChatMessage>,
    paper: String,
    fontSizeSp: Int,
    lineHeightMultiplier: Float = 1.75f,
    useSerif: Boolean,
    useIndent: Boolean = true,
    onPaperChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit = {},
    onSerifToggle: (Boolean) -> Unit,
    onIndentToggle: (Boolean) -> Unit = {},
    onContinueWriting: () -> Unit = {},
    onExit: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val isNight = paper == "night"
    var showChrome by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val readerColors = remember(paper) {
        when (paper) {
            "parchment" -> ReaderPaperTheme(
                bg = Color(0xFFF3E6C8),
                surface = Color(0xFFEAD9B4),
                chromeBg = Color(0xF2EFE1C0),
                ink = Color(0xFF3A2818),
                muted = Color(0xFF6F563E),
                accent = Color(0xFFB45309),
                border = Color(0xFFD2B888),
                codeBg = Color(0xFFE6D4AC),
                codeHeaderBg = Color(0xFFDDC797),
            )
            "sage" -> ReaderPaperTheme(
                bg = Color(0xFFE7F0E6),
                surface = Color(0xFFD8E6D7),
                chromeBg = Color(0xF2E2ECE1),
                ink = Color(0xFF1D2F23),
                muted = Color(0xFF4F6B58),
                accent = Color(0xFF2D6A4F),
                border = Color(0xFFBDD2BC),
                codeBg = Color(0xFFDCE9DB),
                codeHeaderBg = Color(0xFFCCE0CB),
            )
            "night" -> ReaderPaperTheme(
                bg = Color(0xFF0E121B),
                surface = Color(0xFF171E2E),
                chromeBg = Color(0xF2151B29),
                ink = Color(0xFFE4E9F4),
                muted = Color(0xFF8F9DB6),
                accent = Color(0xFF5B7BF6),
                border = Color(0xFF253047),
                codeBg = Color(0xFF0A0D14),
                codeHeaderBg = Color(0xFF141B29),
            )
            else -> ReaderPaperTheme(
                bg = Color(0xFFFDFBF6),
                surface = Color(0xFFF3EFE6),
                chromeBg = Color(0xF2FAF7F0),
                ink = Color(0xFF1F1C1B),
                muted = Color(0xFF68625C),
                accent = Color(0xFF4361EE),
                border = Color(0xFFE3DDD1),
                codeBg = Color(0xFFF2EEE6),
                codeHeaderBg = Color(0xFFE7E1D5),
            )
        }
    }

    val mdPalette = remember(readerColors) {
        MarkdownPalette(
            text = readerColors.ink,
            muted = readerColors.muted,
            accent = readerColors.accent,
            border = readerColors.border,
            quoteBg = readerColors.surface,
            inlineCodeBg = readerColors.surface,
            inlineCodeInk = readerColors.accent,
            codeBg = readerColors.codeBg,
            codeHeaderBg = readerColors.codeHeaderBg,
            codeInk = readerColors.ink,
            codeMeta = readerColors.muted,
            tableHeaderBg = readerColors.surface,
        )
    }

    val sections = remember(chapters, messages) {
        NovelExport.groupMessagesByChapter(chapters, messages)
    }
    val totalWords = remember(sections) { sections.sumOf { it.wordCount } }
    val hasAnyProse = remember(sections) { sections.any { it.proseChunks.isNotEmpty() } }

    val (rows, chapterRowIndexMap) = remember(sections, novel.title, novel.genre, novel.synopsis, isZh) {
        val list = ArrayList<ReaderRowItem>()
        val idxMap = LinkedHashMap<String, Int>()
        if (hasAnyProse) {
            val firstTitle = sections.firstOrNull { it.proseChunks.isNotEmpty() }?.chapter?.title ?: novel.title
            list.add(
                ReaderRowItem.BookCover(
                    key = "__cover__",
                    chapterTitle = firstTitle,
                    bookTitle = novel.title,
                    genre = novel.genre,
                    synopsis = novel.synopsis,
                    totalWords = totalWords,
                    chapterCount = sections.count { it.chapter != null && it.proseChunks.isNotEmpty() }.coerceAtLeast(1),
                ),
            )
            sections.forEachIndexed { secIdx, sec ->
                if (sec.proseChunks.isEmpty()) return@forEachIndexed
                val chTitle = sec.chapter?.title ?: if (isZh) "续篇片段" else "Continuation"
                val chKey = sec.chapter?.id ?: "__orphan_$secIdx"
                idxMap[chKey] = list.size
                list.add(
                    ReaderRowItem.ChapterHeader(
                        key = "__ch_hdr_${chKey}__",
                        chapterTitle = chTitle,
                        chapterId = sec.chapter?.id,
                        order = sec.chapter?.order,
                        wordCount = sec.wordCount,
                        outline = sec.chapter?.outline.orEmpty(),
                    ),
                )
                sec.proseChunks.forEachIndexed { chunkIdx, chunk ->
                    list.add(
                        ReaderRowItem.Passage(
                            key = "__p_${chKey}_${chunkIdx}__",
                            chapterTitle = chTitle,
                            prose = chunk,
                            showDividerBelow = chunkIdx < sec.proseChunks.lastIndex,
                        ),
                    )
                }
            }
            list.add(
                ReaderRowItem.ManuscriptTail(
                    key = "__tail__",
                    chapterTitle = sections.lastOrNull { it.proseChunks.isNotEmpty() }?.chapter?.title ?: novel.title,
                    totalWords = totalWords,
                ),
            )
        }
        list to idxMap
    }

    val currentChapterTitle by remember(rows) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            rows.getOrNull(idx)?.chapterTitle ?: novel.title
        }
    }
    val readingProgressPct by remember(rows) {
        derivedStateOf {
            if (rows.size <= 1) 100
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex
                ((lastVisible.toFloat() / (rows.size - 1).toFloat()) * 100f).toInt().coerceIn(1, 100)
            }
        }
    }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, dialogView)
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerColors.bg),
        ) {
            if (!hasAnyProse) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(readerColors.surface)
                                .border(0.8.dp, readerColors.border, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = readerColors.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            text = if (isZh) "本书尚无正文内容" else "No prose written yet",
                            color = readerColors.ink,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (isZh) {
                                "返回对话窗口发送剧情指令，AI 续写的正文将按章节自动排版在这里。"
                            } else {
                                "Return to the workspace to write your first chapter."
                            },
                            color = readerColors.muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        ReaderChip(
                            label = strings.novel.exitPureReading,
                            selected = true,
                            palette = readerColors,
                            onClick = onExit,
                        )
                    }
                }
            } else {
                val tapSource = remember { MutableInteractionSource() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = tapSource,
                            indication = null,
                            onClick = { showChrome = !showChrome },
                        )
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 74.dp,
                        bottom = 188.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(rows, key = { it.key }) { item ->
                        when (item) {
                            is ReaderRowItem.BookCover -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "《${item.bookTitle}》",
                                        color = readerColors.ink,
                                        fontSize = (fontSizeSp + 6).coerceIn(21, 30).sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                        textAlign = TextAlign.Center,
                                    )
                                    val metaLine = buildString {
                                        if (item.genre.isNotBlank()) append("${item.genre} · ")
                                        append(
                                            if (isZh) {
                                                "共 ${item.chapterCount} 章 · ${item.totalWords} 字"
                                            } else {
                                                "${item.chapterCount} ch · ${item.totalWords} words"
                                            },
                                        )
                                    }
                                    Text(
                                        text = metaLine,
                                        color = readerColors.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                    )
                                    if (item.synopsis.isNotBlank()) {
                                        Text(
                                            text = item.synopsis.trim(),
                                            color = readerColors.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 19.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(
                                        thickness = 0.8.dp,
                                        color = readerColors.border.copy(alpha = 0.85f),
                                    )
                                }
                            }
                            is ReaderRowItem.ChapterHeader -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(readerColors.accent),
                                        )
                                        Text(
                                            text = item.chapterTitle,
                                            color = readerColors.ink,
                                            fontSize = (fontSizeSp + 3).coerceIn(18, 26).sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                            lineHeight = ((fontSizeSp + 3).coerceIn(18, 26) * 1.35f).sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Text(
                                        text = if (isZh) "本章约 ${item.wordCount} 字" else "~${item.wordCount} words",
                                        color = readerColors.muted,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(start = 11.dp),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(
                                        thickness = 0.7.dp,
                                        color = readerColors.border.copy(alpha = 0.65f),
                                    )
                                }
                            }
                            is ReaderRowItem.Passage -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MarkdownMessageText(
                                        markdown = item.prose,
                                        textColor = readerColors.ink,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                        useSerif = useSerif,
                                        indentFirstLine = useIndent,
                                        palette = mdPalette,
                                    )
                                    if (item.showDividerBelow) {
                                        Text(
                                            text = "— ✦ —",
                                            color = readerColors.muted.copy(alpha = 0.55f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            is ReaderRowItem.ManuscriptTail -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    HorizontalDivider(
                                        thickness = 0.8.dp,
                                        color = readerColors.border.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        text = if (isZh) {
                                            "· 已读至最新进度（全书 ${item.totalWords} 字） ·"
                                        } else {
                                            "· End of manuscript (${item.totalWords} words) ·"
                                        },
                                        color = readerColors.muted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ReaderActionPill(
                                            label = if (isZh) "返回写作" else "Back to Write",
                                            icon = Icons.Default.Edit,
                                            highlighted = false,
                                            palette = readerColors,
                                            onClick = onExit,
                                        )
                                        ReaderActionPill(
                                            label = if (isZh) "AI 续写下一段" else "Continue Next",
                                            icon = Icons.Default.PlayArrow,
                                            highlighted = true,
                                            palette = readerColors,
                                            onClick = {
                                                onExit()
                                                onContinueWriting()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Translucent Top Bar
            AnimatedVisibility(
                visible = showChrome || !hasAnyProse,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(readerColors.chromeBg)
                        .statusBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = currentChapterTitle,
                                color = readerColors.ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (hasAnyProse) {
                                    "${novel.title} · $readingProgressPct% · ${if (isZh) "$totalWords 字" else "$totalWords w"}"
                                } else {
                                    novel.title
                                },
                                color = readerColors.muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasAnyProse && chapterRowIndexMap.isNotEmpty()) {
                            IconButton(
                                onClick = { showTocSheet = true },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(readerColors.surface)
                                        .border(0.8.dp, readerColors.border, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = if (isZh) "目录" else "Contents",
                                        tint = readerColors.ink,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(readerColors.surface)
                                    .border(0.8.dp, readerColors.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.novel.exitPureReading,
                                    tint = readerColors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.8.dp,
                        color = readerColors.border.copy(alpha = 0.8f),
                    )
                }
            }

            // Floating Translucent Bottom Typography & Paper Console
            AnimatedVisibility(
                visible = showChrome || !hasAnyProse,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = readerColors.chromeBg,
                    border = BorderStroke(0.8.dp, readerColors.border),
                    shadowElevation = if (isNight) 8.dp else 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        // Row 1: 4 Paper Theme Swatches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ReaderPaperSwatchPill(
                                label = if (isZh) "奶油" else "Cream",
                                swatchColor = Color(0xFFFDFBF6),
                                selected = paper == "cream",
                                palette = readerColors,
                                onClick = { onPaperChange("cream") },
                                modifier = Modifier.weight(1f),
                            )
                            ReaderPaperSwatchPill(
                                label = if (isZh) "羊皮" else "Warm",
                                swatchColor = Color(0xFFF3E6C8),
                                selected = paper == "parchment",
                                palette = readerColors,
                                onClick = { onPaperChange("parchment") },
                                modifier = Modifier.weight(1f),
                            )
                            ReaderPaperSwatchPill(
                                label = if (isZh) "护眼" else "Sage",
                                swatchColor = Color(0xFFE7F0E6),
                                selected = paper == "sage",
                                palette = readerColors,
                                onClick = { onPaperChange("sage") },
                                modifier = Modifier.weight(1f),
                            )
                            ReaderPaperSwatchPill(
                                label = if (isZh) "夜蓝" else "Night",
                                swatchColor = Color(0xFF141824),
                                selected = paper == "night",
                                palette = readerColors,
                                onClick = { onPaperChange("night") },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Row 2: Font Size Stepper + Line Height Stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = readerColors.surface,
                                border = BorderStroke(0.8.dp, readerColors.border),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    ReaderFontStepBtn(
                                        label = "A-",
                                        palette = readerColors,
                                        onClick = { onFontSizeChange((fontSizeSp - 1).coerceAtLeast(13)) },
                                    )
                                    Text(
                                        text = "${fontSizeSp}sp",
                                        color = readerColors.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                    ReaderFontStepBtn(
                                        label = "A+",
                                        palette = readerColors,
                                        onClick = { onFontSizeChange((fontSizeSp + 1).coerceAtMost(24)) },
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            listOf(
                                1.55f to if (isZh) "紧凑" else "Tight",
                                1.75f to if (isZh) "适中" else "Norm",
                                2.0f to if (isZh) "宽松" else "Wide",
                            ).forEach { (lh, label) ->
                                val sel = abs(lineHeightMultiplier - lh) < 0.08f
                                ReaderChip(
                                    label = label,
                                    selected = sel,
                                    palette = readerColors,
                                    onClick = { onLineHeightChange(lh) },
                                )
                            }
                        }

                        // Row 3: Serif + First-Line Indent toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReaderChip(
                                label = strings.novel.serifToggle,
                                selected = useSerif,
                                palette = readerColors,
                                onClick = { onSerifToggle(!useSerif) },
                            )
                            ReaderChip(
                                label = strings.novel.indentToggle,
                                selected = useIndent,
                                palette = readerColors,
                                onClick = { onIndentToggle(!useIndent) },
                            )
                            Spacer(Modifier.weight(1f))
                            if (hasAnyProse && chapterRowIndexMap.isNotEmpty()) {
                                ReaderChip(
                                    label = if (isZh) "目录 (${sections.size})" else "TOC (${sections.size})",
                                    selected = false,
                                    palette = readerColors,
                                    onClick = { showTocSheet = true },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Table of Contents Bottom Sheet
        if (showTocSheet) {
            val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = tocSheetState,
                containerColor = readerColors.bg,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isZh) "全书目录" else "Table of Contents",
                                color = readerColors.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = if (isZh) {
                                    "共 ${sections.size} 章 · 总计 $totalWords 字"
                                } else {
                                    "${sections.size} chapters · $totalWords words"
                                },
                                color = readerColors.muted,
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { showTocSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = readerColors.muted)
                        }
                    }
                    HorizontalDivider(thickness = 0.8.dp, color = readerColors.border)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(sections, key = { idx, sec -> sec.chapter?.id ?: "orphan_$idx" }) { idx, sec ->
                            val chKey = sec.chapter?.id ?: "__orphan_$idx"
                            val targetRow = chapterRowIndexMap[chKey]
                            val title = sec.chapter?.title ?: if (isZh) "续篇片段" else "Continuation"
                            val isCurrent = title == currentChapterTitle
                            val hasContent = targetRow != null
                            Surface(
                                onClick = {
                                    if (targetRow != null) {
                                        showTocSheet = false
                                        scope.launch { listState.animateScrollToItem(targetRow) }
                                    }
                                },
                                enabled = hasContent,
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) readerColors.accent.copy(alpha = 0.14f) else readerColors.surface,
                                border = BorderStroke(
                                    width = if (isCurrent) 1.2.dp else 0.7.dp,
                                    color = if (isCurrent) readerColors.accent else readerColors.border,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = title,
                                            color = if (hasContent) readerColors.ink else readerColors.muted,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!sec.chapter?.outline.isNullOrBlank()) {
                                            Text(
                                                text = sec.chapter!!.outline,
                                                color = readerColors.muted,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (sec.wordCount > 0) {
                                            if (isZh) "${sec.wordCount} 字" else "${sec.wordCount}w"
                                        } else {
                                            if (isZh) "待写" else "Empty"
                                        },
                                        color = if (isCurrent) readerColors.accent else readerColors.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) palette.accent else palette.surface,
        border = BorderStroke(0.8.dp, if (highlighted) palette.accent else palette.border),
        modifier = Modifier.heightIn(min = 38.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) Color.White else palette.ink,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                color = if (highlighted) Color.White else palette.ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReaderPaperSwatchPill(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) palette.accent.copy(alpha = 0.14f) else palette.surface,
        border = BorderStroke(
            width = if (selected) 1.2.dp else 0.8.dp,
            color = if (selected) palette.accent else palette.border,
        ),
        modifier = modifier.heightIn(min = 34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(
                        width = if (selected) 1.4.dp else 0.8.dp,
                        color = if (selected) palette.accent else palette.border,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (swatchColor == Color(0xFF141824)) Color.White else palette.accent,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
            Text(
                text = label,
                color = if (selected) palette.accent else palette.ink,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderChip(
    label: String,
    selected: Boolean,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
) {
    val bg = if (selected) palette.accent else palette.surface
    val fg = if (selected) Color.White else palette.ink
    val border = if (selected) palette.accent else palette.border
    Box(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderFontStepBtn(
    label: String,
    palette: ReaderPaperTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
