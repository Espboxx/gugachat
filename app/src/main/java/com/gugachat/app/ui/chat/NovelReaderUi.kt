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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
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
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelExport
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private val READER_FONT_TIERS = listOf(
    15 to ("小" to "S"),
    17 to ("适中" to "M"),
    19 to ("大" to "L"),
    21 to ("特大" to "XL"),
)

private sealed interface ReaderRowItem {
    val key: String
    val chapterTitle: String
    val chapterOrder: Int?

    data class BookCover(
        override val key: String,
        override val chapterTitle: String,
        override val chapterOrder: Int?,
        val bookTitle: String,
        val genre: String,
        val synopsis: String,
        val totalWords: Int,
        val chapterCount: Int,
    ) : ReaderRowItem

    data class ChapterHeader(
        override val key: String,
        override val chapterTitle: String,
        override val chapterOrder: Int?,
        val chapterId: String?,
        val wordCount: Int,
        val outline: String,
    ) : ReaderRowItem

    data class Passage(
        override val key: String,
        override val chapterTitle: String,
        override val chapterOrder: Int?,
        val prose: String,
        val showDividerBelow: Boolean,
    ) : ReaderRowItem

    data class ManuscriptTail(
        override val key: String,
        override val chapterTitle: String,
        override val chapterOrder: Int?,
        val totalWords: Int,
    ) : ReaderRowItem
}

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
    val isAppDark = LocalAppTheme.current.isDark
    val readerColors = remember(paper, isAppDark) {
        resolveNovelPaperTheme(paper, isAppDark)
    }
    val isNight = readerColors.isNight
    var showChrome by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
    val nonEmptySections = remember(sections) {
        sections.mapIndexedNotNull { idx, sec ->
            if (sec.proseChunks.isEmpty()) null else idx to sec
        }
    }
    val totalWords = remember(sections) { sections.sumOf { it.wordCount } }
    val hasAnyProse = nonEmptySections.isNotEmpty()

    val (rows, chapterRowIndexMap) = remember(sections, novel.title, novel.genre, novel.synopsis, isZh) {
        val list = ArrayList<ReaderRowItem>()
        val idxMap = LinkedHashMap<String, Int>()
        if (hasAnyProse) {
            val firstSec = sections.firstOrNull { it.proseChunks.isNotEmpty() }
            val firstTitle = firstSec?.chapter?.title ?: novel.title
            list.add(
                ReaderRowItem.BookCover(
                    key = "__cover__",
                    chapterTitle = firstTitle,
                    chapterOrder = firstSec?.chapter?.order,
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
                val chOrder = sec.chapter?.order
                val chKey = sec.chapter?.id ?: "__orphan_$secIdx"
                idxMap[chKey] = list.size
                list.add(
                    ReaderRowItem.ChapterHeader(
                        key = "__ch_hdr_${chKey}__",
                        chapterTitle = chTitle,
                        chapterOrder = chOrder,
                        chapterId = sec.chapter?.id,
                        wordCount = sec.wordCount,
                        outline = sec.chapter?.outline.orEmpty(),
                    ),
                )
                sec.proseChunks.forEachIndexed { chunkIdx, chunk ->
                    list.add(
                        ReaderRowItem.Passage(
                            key = "__p_${chKey}_${chunkIdx}__",
                            chapterTitle = chTitle,
                            chapterOrder = chOrder,
                            prose = formatHardcoverProse(chunk),
                            showDividerBelow = chunkIdx < sec.proseChunks.lastIndex,
                        ),
                    )
                }
            }
            val lastSec = sections.lastOrNull { it.proseChunks.isNotEmpty() }
            list.add(
                ReaderRowItem.ManuscriptTail(
                    key = "__tail__",
                    chapterTitle = lastSec?.chapter?.title ?: novel.title,
                    chapterOrder = lastSec?.chapter?.order,
                    totalWords = totalWords,
                ),
            )
        }
        list to idxMap
    }

    val currentRow by remember(rows) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            rows.getOrNull(idx)
        }
    }
    val currentChapterTitle = currentRow?.chapterTitle ?: novel.title
    val currentChapterOrder = currentRow?.chapterOrder
    val currentSectionPos = remember(nonEmptySections, currentChapterTitle, currentChapterOrder) {
        val matchIdx = nonEmptySections.indexOfFirst { (_, sec) ->
            val t = sec.chapter?.title ?: if (isZh) "续篇片段" else "Continuation"
            t == currentChapterTitle && sec.chapter?.order == currentChapterOrder
        }
        if (matchIdx >= 0) matchIdx else 0
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
    val formattedFooterChapter = remember(currentChapterOrder, currentChapterTitle, isZh) {
        formatChapterFooterLabel(currentChapterOrder, currentChapterTitle, isZh)
    }
    val formattedTotalWords = remember(totalWords) {
        String.format(Locale.US, "%,d", totalWords.coerceAtLeast(0))
    }

    fun jumpToNonEmptySection(targetPos: Int) {
        val pair = nonEmptySections.getOrNull(targetPos) ?: return
        val chKey = pair.second.chapter?.id ?: "__orphan_${pair.first}"
        val targetRow = chapterRowIndexMap[chKey] ?: return
        scope.launch { listState.animateScrollToItem(targetRow) }
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
                                .background(readerColors.surface),
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
                                "返回创作区发送剧情指令，AI 续写的正文将按实体书排版呈现在这里。"
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
                            label = if (isZh) "回到创作" else "Back to Write",
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
                        start = 24.dp,
                        end = 24.dp,
                        top = 112.dp,
                        bottom = 200.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(rows, key = { it.key }) { item ->
                        when (item) {
                            is ReaderRowItem.BookCover -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "《${item.bookTitle}》",
                                        color = readerColors.ink,
                                        fontSize = (fontSizeSp + 7).coerceIn(22, 32).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                        textAlign = TextAlign.Center,
                                    )
                                    val metaLine = buildString {
                                        if (item.genre.isNotBlank()) append("${item.genre} · ")
                                        append(
                                            if (isZh) {
                                                "共 ${item.chapterCount} 章 · ${formatCompactReaderCount(item.totalWords)} 字"
                                            } else {
                                                "${item.chapterCount} ch · ${formatCompactReaderCount(item.totalWords)} words"
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
                                            lineHeight = 20.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider(
                                        thickness = 0.6.dp,
                                        color = readerColors.border.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            is ReaderRowItem.ChapterHeader -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    if (item.chapterOrder != null) {
                                        Text(
                                            text = if (isZh) "— 第 ${item.chapterOrder} 章 —" else "— Chapter ${item.chapterOrder} —",
                                            color = readerColors.accent.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                            letterSpacing = 1.sp,
                                        )
                                    }
                                    Text(
                                        text = item.chapterTitle,
                                        color = readerColors.ink,
                                        fontSize = (fontSizeSp + 4).coerceIn(19, 27).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                        lineHeight = ((fontSizeSp + 4).coerceIn(19, 27) * 1.35f).sp,
                                    )
                                    Text(
                                        text = if (isZh) {
                                            "本章约 ${formatCompactReaderCount(item.wordCount)} 字"
                                        } else {
                                            "~${formatCompactReaderCount(item.wordCount)} words"
                                        },
                                        color = readerColors.muted,
                                        fontSize = 11.5.sp,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(
                                        thickness = 0.6.dp,
                                        color = readerColors.border.copy(alpha = 0.55f),
                                    )
                                }
                            }
                            is ReaderRowItem.Passage -> {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                                            color = readerColors.muted.copy(alpha = 0.45f),
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
                                        .padding(top = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    HorizontalDivider(
                                        thickness = 0.6.dp,
                                        color = readerColors.border.copy(alpha = 0.65f),
                                    )
                                    Text(
                                        text = if (isZh) {
                                            "· 已读至最新进度（全书 $formattedTotalWords 字） ·"
                                        } else {
                                            "· End of manuscript ($formattedTotalWords words) ·"
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
                                            label = if (isZh) "回到创作/润色" else "Back to Edit",
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

            // Ultra-subtle single-line footer when reading controls are hidden
            AnimatedVisibility(
                visible = !showChrome && hasAnyProse,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.38f to readerColors.bg.copy(alpha = 0.92f),
                                1f to readerColors.bg,
                            ),
                        )
                        .navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = formattedFooterChapter,
                            color = readerColors.muted.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isZh) "$readingProgressPct% · 约 $formattedTotalWords 字" else "$readingProgressPct% · ~$formattedTotalWords w",
                            color = readerColors.muted.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Top Translucent Reading Control Bar (Title + 5 Paper Modes)
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
                            .height(50.dp)
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
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (hasAnyProse) {
                                    "《${novel.title}》 · $readingProgressPct% · ${if (isZh) "约 $formattedTotalWords 字" else "~$formattedTotalWords w"}"
                                } else {
                                    "《${novel.title}》"
                                },
                                color = readerColors.muted,
                                fontSize = 11.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasAnyProse && chapterRowIndexMap.isNotEmpty()) {
                            IconButton(
                                onClick = { showTocSheet = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(readerColors.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = if (isZh) "目录" else "Contents",
                                        tint = readerColors.ink,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(readerColors.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.novel.exitPureReading,
                                    tint = readerColors.ink,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }

                    // 5-option paper selector strip inside Top Reading Bar
                    val cleanPaper = paper.trim().lowercase()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NOVEL_PAPER_PRESETS.forEach { opt ->
                            val isSelected = cleanPaper in opt.matchIds
                            NovelPaperSwatchPill(
                                label = if (isZh) opt.labelZh else opt.labelEn,
                                swatchColor = opt.swatchColor,
                                selected = isSelected,
                                palette = readerColors,
                                onClick = { onPaperChange(opt.valueToStore) },
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = readerColors.border.copy(alpha = 0.6f),
                    )
                }
            }

            // Bottom Floating Typography & Navigation Console
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
                    border = if (isNight) BorderStroke(0.5.dp, readerColors.border) else null,
                    shadowElevation = if (isNight) 10.dp else 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        // Row 1: 4-tier quick font size + fine stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (isZh) "字号" else "Size",
                                color = readerColors.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                READER_FONT_TIERS.forEach { (spVal, labels) ->
                                    val selected = fontSizeSp == spVal
                                    ReaderChip(
                                        label = "${if (isZh) labels.first else labels.second} $spVal",
                                        selected = selected,
                                        palette = readerColors,
                                        onClick = { onFontSizeChange(spVal) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // Row 2: Line Height + Serif + First-Line Indent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                1.55f to if (isZh) "紧凑" else "Tight",
                                1.75f to if (isZh) "1.75x" else "1.75x",
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
                            Spacer(Modifier.width(2.dp))
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
                        }

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = readerColors.border.copy(alpha = 0.45f),
                        )

                        // Row 3: Bottom-left Chapter Quick Switch | Bottom-right [✍️ 回到创作/润色]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasAnyProse && nonEmptySections.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(readerColors.surface),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = currentSectionPos > 0) {
                                                jumpToNonEmptySection(currentSectionPos - 1)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronLeft,
                                            contentDescription = null,
                                            tint = if (currentSectionPos > 0) readerColors.ink else readerColors.muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { showTocSheet = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (isZh) "目录 ${currentSectionPos + 1}/${nonEmptySections.size}" else "Ch ${currentSectionPos + 1}/${nonEmptySections.size}",
                                            color = readerColors.ink,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            maxLines = 1,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = currentSectionPos < nonEmptySections.lastIndex) {
                                                jumpToNonEmptySection(currentSectionPos + 1)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = if (currentSectionPos < nonEmptySections.lastIndex) readerColors.ink else readerColors.muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Surface(
                                onClick = onExit,
                                shape = RoundedCornerShape(999.dp),
                                color = readerColors.accent,
                                modifier = Modifier.heightIn(min = 34.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = if (isZh) "回到创作/润色" else "Back to Edit",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Table of Contents Bottom Sheet
        if (showTocSheet) {
            val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            GuModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = tocSheetState,
                containerColor = readerColors.bg,
                dragHandle = null,
                applyStatusBarsPadding = false,
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
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                            )
                            Text(
                                text = if (isZh) {
                                    "共 ${sections.size} 章 · 总计 $formattedTotalWords 字"
                                } else {
                                    "${sections.size} chapters · $formattedTotalWords words"
                                },
                                color = readerColors.muted,
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { showTocSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = readerColors.muted)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = readerColors.border.copy(alpha = 0.7f))
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
                            val isCurrent = title == currentChapterTitle && sec.chapter?.order == currentChapterOrder
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
                                border = if (isCurrent) BorderStroke(1.dp, readerColors.accent.copy(alpha = 0.65f)) else null,
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
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
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
                                        fontWeight = FontWeight.SemiBold,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
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

/**
 * Ensures single-line Chinese novel paragraphs have clear breathing room while
 * keeping Markdown fences, blockquotes, lists, headings, and tables intact.
 */
private fun formatHardcoverProse(raw: String): String {
    val lines = raw.replace("\r\n", "\n").lines()
    if (lines.size <= 1) return raw.trim()
    val out = StringBuilder()
    var inFence = false
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            out.append(line)
            if (i < lines.lastIndex) out.append('\n')
            continue
        }
        out.append(line)
        if (i >= lines.lastIndex) continue
        if (inFence) {
            out.append('\n')
            continue
        }
        val nextTrimmed = lines[i + 1].trim()
        val isStructural = trimmed.isEmpty() || nextTrimmed.isEmpty() ||
            trimmed.startsWith(">") || nextTrimmed.startsWith(">") ||
            trimmed.startsWith("#") || nextTrimmed.startsWith("#") ||
            trimmed.startsWith("|") || nextTrimmed.startsWith("|") ||
            isListOrRuleLine(trimmed) || isListOrRuleLine(nextTrimmed)
        out.append(if (isStructural) "\n" else "\n\n")
    }
    return out.toString().trim()
}

private fun isListOrRuleLine(trimmed: String): Boolean {
    if (trimmed == "---" || trimmed == "***" || trimmed == "___") return true
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) return true
    val dot = trimmed.indexOf('.')
    return dot in 1..3 && trimmed.substring(0, dot).all { it.isDigit() } && trimmed.getOrNull(dot + 1) == ' '
}

private fun formatChapterFooterLabel(order: Int?, title: String, isZh: Boolean): String {
    val cleanTitle = title.trim()
    if (order == null) return cleanTitle
    return if (isZh) {
        if (cleanTitle.startsWith("第")) cleanTitle else "第 $order 章 · $cleanTitle"
    } else {
        if (cleanTitle.startsWith("Ch", ignoreCase = true)) cleanTitle else "Ch. $order · $cleanTitle"
    }
}

private fun formatCompactReaderCount(count: Int): String =
    com.gugachat.app.core.util.FormatUtils.formatCommaCount(count)

@Composable
private fun ReaderActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean,
    palette: NovelPaperTheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) palette.accent else palette.surface,
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
private fun ReaderChip(
    label: String,
    selected: Boolean,
    palette: NovelPaperTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) palette.accent else palette.surface
    val fg = if (selected) Color.White else palette.ink
    Box(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
