package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppShellController
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpInputKind
import java.io.File
import java.util.Locale

private data class NovelPaperPresetSpec(
    val valueToStore: String,
    val matchIds: Set<String>,
    val swatchColor: Color,
    val labelZh: String,
    val labelEn: String,
)

private val NOVEL_PAPER_PRESETS = listOf(
    NovelPaperPresetSpec(
        valueToStore = "cream",
        matchIds = setOf("cream", "auto", ""),
        swatchColor = Color(0xFFFDFBF6),
        labelZh = "自动跟随",
        labelEn = "Auto",
    ),
    NovelPaperPresetSpec(
        valueToStore = "parchment",
        matchIds = setOf("parchment"),
        swatchColor = Color(0xFFF3E6C8),
        labelZh = "羊皮",
        labelEn = "Warm",
    ),
    NovelPaperPresetSpec(
        valueToStore = "sage",
        matchIds = setOf("sage"),
        swatchColor = Color(0xFFE7F0E6),
        labelZh = "护眼绿",
        labelEn = "Sage",
    ),
    NovelPaperPresetSpec(
        valueToStore = "night",
        matchIds = setOf("night"),
        swatchColor = Color(0xFF141824),
        labelZh = "暗夜",
        labelEn = "Night",
    ),
    NovelPaperPresetSpec(
        valueToStore = "cream_light",
        matchIds = setOf("cream_light", "cream_force"),
        swatchColor = Color(0xFFFFFFFF),
        labelZh = "日间白",
        labelEn = "Day",
    ),
)

@Composable
fun NovelWorkspaceScreen(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    messages: List<ChatMessage>,
    waterline: ContextWaterlineSummary,
    readerPaper: String,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    selectedGroupLabel: String,
    memoryEnabled: Boolean,
    enableReasoning: Boolean,
    supportsVision: Boolean,
    draftText: String,
    draftAttachments: List<ChatAttachmentMeta>,
    attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    isImportingAttachment: Boolean = false,
    draftNovelKind: NovelInputKind,
    isStreaming: Boolean,
    streamingStatusHint: String?,
    isRefreshingChoices: Boolean,
    attachmentPathResolver: (String) -> File,
    onOpenDrawer: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onNewSession: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    moreMenuContent: @Composable () -> Unit,
    onSelectPaper: (String) -> Unit,
    onOpenNovelStudio: (Int) -> Unit,
    onEnterPureReading: () -> Unit,
    onFinishChapterAndAdvance: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
    onStarterPrompt: (String) -> Unit,
    onSelectNovelChoice: (String) -> Unit,
    onRefreshNovelChoices: () -> Unit,
    onContinueNovel: () -> Unit,
    onRegenerate: (String) -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onEditAssistantMessage: (String, String) -> Unit,
    onBranchFromMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onSelectNovelKind: (NovelInputKind) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleMemory: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit,
    onUpdateAttachmentInjectMode: (String, FileInjectMode) -> Unit = { _, _ -> },
    onReadAttachmentText: (suspend (String) -> String?)? = null,
    onOpenRecallInspector: () -> Unit,
    onOpenContextSettings: () -> Unit = onOpenRecallInspector,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val outerTheme = LocalAppTheme.current
    val shellController = LocalAppShellController.current
    val paperTheme = remember(readerPaper, outerTheme.isDark) {
        resolveNovelPaperTheme(readerPaper, outerTheme.isDark)
    }
    val novelTheme = remember(outerTheme, paperTheme) {
        deriveNovelPaperAppTheme(outerTheme, paperTheme)
    }

    WorkspaceSystemBarsEffect(isDarkBackdrop = paperTheme.isNight)

    val activeCh = remember(chapters, novel.activeChapterId) {
        chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    }
    val chapterWords = activeCh?.wordCount ?: 0
    val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
    val progressRatio = (chapterWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
    val triggeredLoreCount = waterline.triggeredLoreIds.size

    var showQuickOverlay by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }

    BackHandler(enabled = showQuickOverlay) {
        showQuickOverlay = false
    }

    // Restore bottom navigation when leaving novel workspace or when prose is empty
    val hasMessages = messages.isNotEmpty()
    var isBottomBarHidden by remember { mutableStateOf(false) }
    var scrollAccumPx by remember { mutableFloatStateOf(0f) }

    DisposableEffect(shellController) {
        onDispose {
            shellController.setScrollHideBottomBar(false)
        }
    }
    LaunchedEffect(hasMessages) {
        if (!hasMessages) {
            isBottomBarHidden = false
            scrollAccumPx = 0f
            shellController.setScrollHideBottomBar(false)
        }
    }

    val nestedScrollConnection = remember(hasMessages, shellController) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!hasMessages || source != NestedScrollSource.UserInput) return Offset.Zero
                // Reached top boundary: immediately restore global bottom bar
                if (consumed.y == 0f && available.y > 6f) {
                    scrollAccumPx = 0f
                    if (isBottomBarHidden) {
                        isBottomBarHidden = false
                        shellController.setScrollHideBottomBar(false)
                    }
                    return Offset.Zero
                }
                if (consumed.y < -2f) {
                    if (scrollAccumPx > 0f) scrollAccumPx = 0f
                    scrollAccumPx += consumed.y
                    if (scrollAccumPx <= -24f && !isBottomBarHidden) {
                        isBottomBarHidden = true
                        shellController.setScrollHideBottomBar(true)
                    }
                } else if (consumed.y > 2f) {
                    if (scrollAccumPx < 0f) scrollAccumPx = 0f
                    scrollAccumPx += consumed.y
                    if (scrollAccumPx >= 220f && isBottomBarHidden) {
                        isBottomBarHidden = false
                        shellController.setScrollHideBottomBar(false)
                    }
                }
                return Offset.Zero
            }
        }
    }

    val canvasWarmthBrush = remember(paperTheme) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to paperTheme.chromeBg.copy(alpha = 1f),
                0.14f to paperTheme.bg,
                0.86f to paperTheme.bg,
                1.0f to paperTheme.surface.copy(alpha = if (paperTheme.isNight) 0.50f else 0.42f),
            ),
        )
    }
    val headerBrush = remember(paperTheme) {
        Brush.verticalGradient(
            listOf(
                paperTheme.chromeBg.copy(alpha = 1f),
                paperTheme.bg,
            ),
        )
    }
    val goldRibbonColor = remember(paperTheme, progressRatio, novelTheme.colors.success) {
        when {
            progressRatio >= 1f -> novelTheme.colors.success
            paperTheme.isNight -> Color(0xFFD9AE5F)
            paperTheme.id == "sage" -> paperTheme.accent
            else -> Color(0xFFC8923A)
        }
    }

    CompositionLocalProvider(LocalAppTheme provides novelTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(paperTheme.bg)
                .background(canvasWarmthBrush),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Single-row 44dp serif book header + 1.5dp progress hairline
                GuAwningHeader(
                    includeStatusBarPadding = true,
                    containerBrush = headerBrush,
                    dividerColor = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = strings.chat.sessions,
                                tint = paperTheme.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        // Center Book · Chapter trigger (opens floating Box overlay)
                        val bookSnippet = remember(novel.title) {
                            val clean = novel.title.trim().ifBlank { strings.novel.untitledBook }
                            if (clean.length > 9) "${clean.take(8)}…" else clean
                        }
                        val headerTitle = remember(bookSnippet, activeCh?.title, activeCh?.order, isZh) {
                            val chLabel = activeCh?.title?.takeIf { it.isNotBlank() }
                                ?: if (activeCh != null) {
                                    if (isZh) "第${activeCh.order}章" else "Ch.${activeCh.order}"
                                } else {
                                    null
                                }
                            if (chLabel != null) "《$bookSnippet》· $chLabel" else "《$bookSnippet》"
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showQuickOverlay = !showQuickOverlay }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = headerTitle,
                                color = paperTheme.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = if (showQuickOverlay) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = paperTheme.muted,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        // Pure Reading compact button
                        Surface(
                            onClick = {
                                showQuickOverlay = false
                                onEnterPureReading()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = paperTheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(28.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = strings.novel.pureReading,
                                    tint = paperTheme.accent,
                                    modifier = Modifier.size(13.5.dp),
                                )
                                Text(
                                    text = if (isZh) "阅读" else "Read",
                                    color = paperTheme.ink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }

                        // Studio Desk compact pill
                        Surface(
                            onClick = {
                                showQuickOverlay = false
                                onOpenNovelStudio(0)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = paperTheme.accent.copy(alpha = if (paperTheme.isNight) 0.20f else 0.12f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(28.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = strings.novel.studioBtn,
                                    tint = paperTheme.accent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (isZh) "案头" else "Desk",
                                    color = paperTheme.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }

                        IconButton(
                            onClick = onNewSession,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = strings.chat.newChat,
                                tint = paperTheme.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Box {
                            IconButton(
                                onClick = onOpenMoreMenu,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = strings.chat.moreActions,
                                    tint = paperTheme.ink,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            moreMenuContent()
                        }
                    }

                    // 1.5dp chapter progress hairline flush below the single-row header
                    GuLinearProgressBar(
                        progress = progressRatio,
                        height = 1.5.dp,
                        rounded = false,
                        color = goldRibbonColor,
                        trackColor = paperTheme.border.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Content area wrapped in Box so bookmark quick-control floats ABOVE prose without pushing it down
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(nestedScrollConnection),
                ) {
                    ChatMessageList(
                        mode = ConversationMode.novel,
                        messages = messages,
                        waterline = waterline,
                        hasModelSelected = !selectedModelId.isNullOrBlank(),
                        selectedModelLabel = selectedModelId.orEmpty(),
                        selectedGroupLabel = selectedGroupLabel,
                        memoryEnabled = memoryEnabled,
                        rpConfig = null,
                        novelConfig = novel,
                        novelChapters = chapters,
                        isStreaming = isStreaming,
                        streamingStatusHint = streamingStatusHint,
                        isRefreshingChoices = isRefreshingChoices,
                        attachmentPathResolver = attachmentPathResolver,
                        attachmentsById = attachmentsById,
                        onReadAttachmentText = onReadAttachmentText,
                        onOpenModelSheet = onOpenModelSheet,
                        onStarterPrompt = onStarterPrompt,
                        onSelectNovelChoice = onSelectNovelChoice,
                        onRefreshNovelChoices = onRefreshNovelChoices,
                        onContinueNovel = onContinueNovel,
                        onOpenNovelStudio = onOpenNovelStudio,
                        onSelectRpChoice = { _, _ -> },
                        onRefreshRpChoices = {},
                        onContinueRp = {},
                        onRegenerate = onRegenerate,
                        onEditUserMessage = onEditUserMessage,
                        onEditAssistantMessage = onEditAssistantMessage,
                        onBranchFromMessage = onBranchFromMessage,
                        onDeleteMessage = onDeleteMessage,
                        onSwitchVariant = onSwitchVariant,
                        onToast = onToast,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Floating Bookmark Quick-Control Overlay (never pushes prose down)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showQuickOverlay,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (paperTheme.isNight) 0.48f else 0.20f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showQuickOverlay = false },
                                    ),
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .animateEnterExit(
                                        enter = expandVertically(expandFrom = Alignment.Top),
                                        exit = shrinkVertically(shrinkTowards = Alignment.Top),
                                    ),
                            ) {
                                NovelBookmarkFloatingOverlay(
                                    novel = novel,
                                    chapters = chapters,
                                    activeCh = activeCh,
                                    readerPaper = readerPaper,
                                    paperTheme = paperTheme,
                                    selectedModelId = selectedModelId,
                                    activeModelFreeQuota = activeModelFreeQuota,
                                    triggeredLoreCount = triggeredLoreCount,
                                    goldRibbonColor = goldRibbonColor,
                                    onSelectPaper = onSelectPaper,
                                    onOpenModelSheet = {
                                        showQuickOverlay = false
                                        onOpenModelSheet()
                                    },
                                    onOpenStudio = { tab ->
                                        showQuickOverlay = false
                                        onOpenNovelStudio(tab)
                                    },
                                    onEnterPureReading = {
                                        showQuickOverlay = false
                                        onEnterPureReading()
                                    },
                                    onConfirmFinishChapter = {
                                        showQuickOverlay = false
                                        onFinishChapterAndAdvance()
                                    },
                                    onUpdateAuthorNote = onUpdateAuthorNote,
                                )
                            }
                        }
                    }
                }

                // Composer bar inheriting unified paper LocalAppTheme
                ChatComposerBar(
                    mode = ConversationMode.novel,
                    draftText = draftText,
                    draftAttachments = draftAttachments,
                    draftNovelKind = draftNovelKind,
                    draftRpKind = RpInputKind.action,
                    draftDiceRoll = null,
                    waterline = waterline,
                    enableReasoning = enableReasoning,
                    enableAutoMemory = memoryEnabled,
                    isStreaming = isStreaming,
                    supportsVision = supportsVision,
                    isImportingAttachment = isImportingAttachment,
                    onDraftChange = onDraftChange,
                    onSelectNovelKind = onSelectNovelKind,
                    onSelectRpKind = {},
                    onToggleReasoning = onToggleReasoning,
                    onToggleMemory = onToggleMemory,
                    onPickImage = onPickImage,
                    onTakePhoto = onTakePhoto,
                    onPickFile = onPickFile,
                    onRemoveAttachment = onRemoveAttachment,
                    onUpdateAttachmentInjectMode = onUpdateAttachmentInjectMode,
                    onReadAttachmentText = onReadAttachmentText,
                    onOpenModelSheet = onOpenModelSheet,
                    onToast = onToast,
                    onOpenDiceDialog = {},
                    onClearDiceRoll = {},
                    onOpenRecallInspector = onOpenRecallInspector,
                    onOpenContextSettings = onOpenContextSettings,
                    onSend = onSend,
                    onStop = onStop,
                    onContinueNovel = onContinueNovel,
                    onOpenNovelStudio = onOpenNovelStudio,
                    onEnterPureReading = onEnterPureReading,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelBookmarkFloatingOverlay(
    novel: NovelConfig,
    chapters: List<NovelChapter>,
    activeCh: NovelChapter?,
    readerPaper: String,
    paperTheme: NovelPaperTheme,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    triggeredLoreCount: Int,
    goldRibbonColor: Color,
    onSelectPaper: (String) -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenStudio: (Int) -> Unit,
    onEnterPureReading: () -> Unit,
    onConfirmFinishChapter: () -> Unit,
    onUpdateAuthorNote: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val chapterWords = activeCh?.wordCount ?: 0
    val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
    val totalBookWords = remember(chapters) { chapters.sumOf { it.wordCount } }
    val progressRatio = (chapterWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
    val progressLabel = remember(chapterWords, targetWords, isZh) {
        val cw = formatCompactCount(chapterWords)
        val tw = formatCompactCount(targetWords)
        if (isZh) "$cw / $tw 字" else "$cw / ${tw}w"
    }

    var editingNote by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }
    var noteDraft by remember(novel.authorNote) { mutableStateOf(novel.authorNote) }
    var confirmFinish by remember(activeCh?.id) { mutableStateOf(false) }

    val nextPlanned = remember(chapters, activeCh?.id) {
        if (activeCh == null) null
        else {
            chapters.sortedBy { it.order }
                .firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 }
        }
    }

    Surface(
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        color = paperTheme.cardBg,
        shadowElevation = if (paperTheme.isNight) 14.dp else 10.dp,
        border = if (paperTheme.isNight) BorderStroke(0.5.dp, paperTheme.border) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Book Header & Word Progress Card (borderless soft paper surface)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(paperTheme.surface.copy(alpha = if (paperTheme.isNight) 0.72f else 0.85f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "《${novel.title}》",
                        color = paperTheme.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (novel.genre.isNotBlank()) {
                        GuBadge(novel.genre.substringBefore(" /"), variant = GuBadgeVariant.HONEY)
                    }
                    GuMetaText(
                        text = if (isZh) "共 ${chapters.size} 章 · ${formatCompactCount(totalBookWords)} 字" else "${chapters.size} ch · ${formatCompactCount(totalBookWords)}w",
                        color = paperTheme.muted,
                    )
                }

                // Active chapter progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuLinearProgressBar(
                        progress = progressRatio,
                        height = 5.dp,
                        color = goldRibbonColor,
                        trackColor = paperTheme.border.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f),
                    )
                    GuMetaText(
                        text = progressLabel,
                        color = paperTheme.muted,
                    )
                }

                if (!activeCh?.outline.isNullOrBlank()) {
                    Text(
                        text = "${strings.novel.chapterOutline}：${activeCh!!.outline}",
                        color = paperTheme.muted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val hud = if (novel.enableStatusHud) novel.currentStatus else null
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
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            chips.forEach { tag ->
                                GuBadge(tag, variant = GuBadgeVariant.NEUTRAL)
                            }
                        }
                    }
                    if (hud.present.isNotEmpty()) {
                        Text(
                            text = "👥 ${strings.novel.hudPresent}：${hud.present.joinToString("、")}",
                            color = paperTheme.ink,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 2. Paper Theme Switcher + Compact Model Selector Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GuMetaText(
                        text = if (isZh) "书稿纸张" else "Paper Surface",
                        color = paperTheme.muted,
                    )
                    // Compact model selector moved cleanly out of Row 2 into quick overlay
                    val hasModel = !selectedModelId.isNullOrBlank()
                    val modelDotColor = when {
                        activeModelFreeQuota -> c.success
                        hasModel -> paperTheme.accent
                        else -> paperTheme.muted.copy(alpha = 0.55f)
                    }
                    Surface(
                        onClick = onOpenModelSheet,
                        shape = RoundedCornerShape(999.dp),
                        color = paperTheme.surface,
                        modifier = Modifier.widthIn(max = 176.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(modelDotColor),
                            )
                            Text(
                                text = selectedModelId ?: strings.chat.selectModel,
                                color = if (hasModel) paperTheme.ink else paperTheme.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = paperTheme.muted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                val cleanPaper = readerPaper.trim().lowercase()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NOVEL_PAPER_PRESETS.forEach { preset ->
                        val isSelected = cleanPaper in preset.matchIds
                        val label = if (isZh) preset.labelZh else preset.labelEn
                        Surface(
                            onClick = { onSelectPaper(preset.valueToStore) },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) {
                                paperTheme.accent.copy(alpha = if (paperTheme.isNight) 0.24f else 0.14f)
                            } else {
                                paperTheme.surface
                            },
                            border = if (isSelected) BorderStroke(1.dp, paperTheme.accent.copy(alpha = 0.7f)) else null,
                            modifier = Modifier
                                .height(30.dp)
                                .semantics {
                                    contentDescription = label
                                    selected = isSelected
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preset.swatchColor)
                                        .border(0.6.dp, paperTheme.border, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (preset.valueToStore == "night") Color.White else paperTheme.accent,
                                            modifier = Modifier.size(8.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) paperTheme.accent else paperTheme.ink,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // 3. Inline AI Director Note Editor (Zero AlertDialog)
            if (editingNote) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(paperTheme.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GuTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = strings.novel.noteTitle,
                        placeholder = strings.novel.notePlaceholder,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 5,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                noteDraft = novel.authorNote
                                editingNote = false
                            },
                        ) {
                            Text(strings.common.cancel, fontSize = 12.sp, color = paperTheme.muted)
                        }
                        GuButton(
                            onClick = {
                                onUpdateAuthorNote(noteDraft.trim())
                                editingNote = false
                            },
                            compact = true,
                            variant = GuButtonVariant.HONEY,
                        ) {
                            Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(paperTheme.surface)
                        .clickable {
                            noteDraft = novel.authorNote
                            editingNote = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = paperTheme.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (novel.authorNote.isNotBlank()) {
                            "${strings.novel.noteShort}：${novel.authorNote}"
                        } else {
                            if (isZh) "点击添加 AI 导演备注（每轮自动注入上下文）…" else "Tap to add AI Director Note…"
                        },
                        color = if (novel.authorNote.isNotBlank()) paperTheme.ink else paperTheme.muted,
                        fontSize = 12.sp,
                        maxLines = 2,
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
                ) {
                    Text(
                        text = if (isZh) "✦ 本轮命中 $triggeredLoreCount 条世界设定（点击管理）" else "✦ $triggeredLoreCount Worldbook entries active",
                        color = c.success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 4. Inline Finish-Chapter Confirmation Strip (replaces GuConfirmDialog)
            AnimatedVisibility(visible = confirmFinish && activeCh != null) {
                if (activeCh != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.tag)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (isZh) {
                                if (nextPlanned != null) {
                                    "确认完结《${activeCh.title}》，切换至预规划《${nextPlanned.title}》并生成梗概？"
                                } else {
                                    "确认完结《${activeCh.title}》并开启下一新章？（将由 AI 自动总结前情梗概）"
                                }
                            } else {
                                if (nextPlanned != null) {
                                    "Finish \"${activeCh.title}\", advance to \"${nextPlanned.title}\", and generate recap?"
                                } else {
                                    "Finish \"${activeCh.title}\", open next chapter, and auto-generate recap?"
                                }
                            },
                            color = c.tagInk,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { confirmFinish = false }) {
                                Text(strings.common.cancel, color = c.tagInk.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            GuButton(
                                onClick = {
                                    confirmFinish = false
                                    onConfirmFinishChapter()
                                },
                                compact = true,
                                variant = GuButtonVariant.PRIMARY,
                            ) {
                                Text(if (isZh) "确认切章" else "Advance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Bottom Quick Actions
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
                    Text(if (isZh) "纯享阅读" else "Reader", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GuButton(
                    onClick = { confirmFinish = !confirmFinish },
                    enabled = activeCh != null,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isZh) "完结切章" else "Next Ch.", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GuButton(
                    onClick = { onOpenStudio(0) },
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isZh) "书卷案头" else "Desk", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

private fun formatCompactCount(value: Int): String =
    com.gugachat.app.core.util.FormatUtils.formatCommaCount(value)
