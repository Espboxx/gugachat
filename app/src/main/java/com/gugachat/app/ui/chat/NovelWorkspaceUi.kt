package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuTextField
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

private data class NovelPaperDotSpec(
    val id: String,
    val color: Color,
    val labelZh: String,
    val labelEn: String,
)

private val NOVEL_PAPER_DOTS = listOf(
    NovelPaperDotSpec("cream", Color(0xFFFDFBF6), "米白", "Cream"),
    NovelPaperDotSpec("parchment", Color(0xFFF3E6C8), "羊皮", "Parchment"),
    NovelPaperDotSpec("sage", Color(0xFFE7F0E6), "护眼", "Sage"),
    NovelPaperDotSpec("night", Color(0xFF1A2236), "夜读", "Night"),
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
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    val outerTheme = LocalAppTheme.current
    val paperTheme = remember(readerPaper) { resolveNovelPaperTheme(readerPaper) }
    val novelTheme = remember(outerTheme, paperTheme) { deriveNovelPaperAppTheme(outerTheme, paperTheme) }

    WorkspaceSystemBarsEffect(isDarkBackdrop = paperTheme.isNight)

    val activeCh = remember(chapters, novel.activeChapterId) {
        chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.lastOrNull()
    }
    val chapterWords = activeCh?.wordCount ?: 0
    val targetWords = novel.targetWordsPerChapter.coerceAtLeast(500)
    val progressRatio = (chapterWords.toFloat() / targetWords.toFloat()).coerceIn(0f, 1f)
    val triggeredLoreCount = waterline.triggeredLoreIds.size

    var showQuickDrawer by remember(novel.title, novel.activeChapterId) { mutableStateOf(false) }
    var showAuthorNoteDialog by remember { mutableStateOf(false) }
    var authorNoteDraft by remember(novel.authorNote) { mutableStateOf(novel.authorNote) }
    var showFinishConfirm by remember { mutableStateOf(false) }

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

    CompositionLocalProvider(LocalAppTheme provides novelTheme) {
        if (showAuthorNoteDialog) {
            AlertDialog(
                onDismissRequest = { showAuthorNoteDialog = false },
                shape = RoundedCornerShape(22.dp),
                containerColor = novelTheme.colors.surfaceElevated,
                title = {
                    Text(
                        text = strings.novel.noteTitle,
                        color = paperTheme.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.novel.noteHint,
                            color = paperTheme.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        GuTextField(
                            value = authorNoteDraft,
                            onValueChange = { authorNoteDraft = it },
                            placeholder = strings.novel.notePlaceholder,
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                        )
                    }
                },
                confirmButton = {
                    GuButton(
                        onClick = {
                            onUpdateAuthorNote(authorNoteDraft.trim())
                            showAuthorNoteDialog = false
                        },
                        compact = true,
                        variant = GuButtonVariant.HONEY,
                    ) {
                        Text(strings.common.save, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAuthorNoteDialog = false }) {
                        Text(strings.common.cancel, color = paperTheme.muted, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        if (showFinishConfirm && activeCh != null) {
            val nextPlanned = remember(chapters, activeCh.id) {
                chapters.sortedBy { it.order }
                    .firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 }
            }
            GuConfirmDialog(
                title = if (isZh) "完结《${activeCh.title}》并切章" else "Finish \"${activeCh.title}\"",
                message = if (isZh) {
                    if (nextPlanned != null) {
                        "将完结当前章节并切换到预规划草稿《${nextPlanned.title}》，同时由 AI 自动生成本章前情梗概。"
                    } else {
                        "将完结当前章节并开启下一新章，同时由 AI 自动生成本章前情梗概。"
                    }
                } else {
                    if (nextPlanned != null) {
                        "Mark this chapter done, switch to planned draft \"${nextPlanned.title}\", and auto-generate a recap."
                    } else {
                        "Mark this chapter done, start the next chapter, and auto-generate a recap."
                    }
                },
                confirmText = if (isZh) "确认切章" else "Advance",
                cancelText = strings.common.cancel,
                isDestructive = false,
                onConfirm = {
                    showFinishConfirm = false
                    showQuickDrawer = false
                    onFinishChapterAndAdvance()
                },
                onDismiss = { showFinishConfirm = false },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(paperTheme.bg)
                .background(canvasWarmthBrush),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Unified Book-Studio Header (Row 1: Navigation + Chapter Title + Reader/Studio + Session/More; Row 2: Bookmark Ribbon)
                GuAwningHeader(
                    includeStatusBarPadding = true,
                    containerBrush = headerBrush,
                    dividerColor = Color.Transparent,
                ) {
                    // Row 1 (42.dp): [☰] · Chapter/Book Title · [📖 阅读] [📚 工坊] [+] [⋮]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = strings.chat.sessions,
                                tint = paperTheme.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showQuickDrawer = !showQuickDrawer }
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = activeCh?.title ?: novel.title,
                                color = paperTheme.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Serif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = if (showQuickDrawer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = paperTheme.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Compact Pure Reading pill
                        Surface(
                            onClick = onEnterPureReading,
                            shape = RoundedCornerShape(999.dp),
                            color = paperTheme.surface,
                            border = BorderStroke(0.8.dp, paperTheme.border),
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
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (isZh) "阅读" else "Read",
                                    color = paperTheme.ink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }

                        // Compact Novel Studio pill
                        Surface(
                            onClick = { onOpenNovelStudio(0) },
                            shape = RoundedCornerShape(999.dp),
                            color = paperTheme.accent.copy(alpha = if (paperTheme.isNight) 0.22f else 0.13f),
                            border = BorderStroke(0.8.dp, paperTheme.accent.copy(alpha = 0.48f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(28.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = strings.novel.studioBtn,
                                    tint = paperTheme.accent,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (isZh) "工坊" else "Studio",
                                    color = paperTheme.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }

                        IconButton(
                            onClick = onNewSession,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = strings.chat.newChat,
                                tint = paperTheme.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        Box {
                            IconButton(
                                onClick = onOpenMoreMenu,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = strings.chat.moreActions,
                                    tint = paperTheme.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            moreMenuContent()
                        }
                    }

                    // Row 2 (28.dp): Paper Bookmark Ribbon — Word Count & Lore · 4 Paper Swatch Dots · Compact Model Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val ribbonSummary = remember(chapterWords, targetWords, triggeredLoreCount, isZh) {
                            buildString {
                                append(if (isZh) "$chapterWords/${targetWords}字" else "$chapterWords/${targetWords}w")
                                if (triggeredLoreCount > 0) {
                                    append(if (isZh) " · 设定$triggeredLoreCount" else " · Lore $triggeredLoreCount")
                                }
                            }
                        }
                        Text(
                            text = ribbonSummary,
                            color = paperTheme.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showQuickDrawer = !showQuickDrawer }
                                .padding(vertical = 2.dp),
                        )

                        // 4 clickable paper theme swatch dots
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            NOVEL_PAPER_DOTS.forEach { dot ->
                                val isSelected = paperTheme.id == dot.id
                                val dotLabel = if (isZh) dot.labelZh else dot.labelEn
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .semantics {
                                            contentDescription = dotLabel
                                            selected = isSelected
                                        }
                                        .clickable(
                                            role = Role.RadioButton,
                                            onClick = { onSelectPaper(dot.id) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 16.dp else 13.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .border(1.6.dp, paperTheme.accent, CircleShape)
                                                        .padding(2.dp)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clip(CircleShape)
                                            .background(dot.color)
                                            .border(
                                                width = 0.8.dp,
                                                color = if (isSelected) {
                                                    paperTheme.border.copy(alpha = 0.7f)
                                                } else {
                                                    paperTheme.muted.copy(alpha = 0.48f)
                                                },
                                                shape = CircleShape,
                                            ),
                                    )
                                }
                            }
                        }

                        // Compact paper-styled model selector pill
                        val hasModel = !selectedModelId.isNullOrBlank()
                        val modelDotColor = when {
                            activeModelFreeQuota -> novelTheme.colors.success
                            hasModel -> paperTheme.accent
                            else -> paperTheme.muted.copy(alpha = 0.55f)
                        }
                        Surface(
                            onClick = onOpenModelSheet,
                            shape = RoundedCornerShape(999.dp),
                            color = paperTheme.surface,
                            border = BorderStroke(0.75.dp, paperTheme.border),
                            modifier = Modifier.widthIn(max = 128.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.5.dp)
                                        .clip(CircleShape)
                                        .background(modelDotColor),
                                )
                                Text(
                                    text = selectedModelId ?: strings.chat.selectModel,
                                    color = if (hasModel) paperTheme.ink else paperTheme.muted,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (hasModel) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = paperTheme.muted,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }

                    // Subtle chapter progress hairline acting as header divider
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp),
                        color = if (progressRatio >= 1f) novelTheme.colors.success else paperTheme.accent,
                        trackColor = paperTheme.border.copy(alpha = 0.45f),
                    )
                }

                // Expandable Chapter / Author Note / Quick-Actions Drawer
                AnimatedVisibility(
                    visible = showQuickDrawer,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(paperTheme.chromeBg.copy(alpha = 0.96f)),
                    ) {
                        NovelChapterQuickDrawerContent(
                            novel = novel,
                            chapters = chapters,
                            activeCh = activeCh,
                            triggeredLoreCount = triggeredLoreCount,
                            onOpenStudio = { tab ->
                                showQuickDrawer = false
                                onOpenNovelStudio(tab)
                            },
                            onEnterPureReading = {
                                showQuickDrawer = false
                                onEnterPureReading()
                            },
                            onRequestFinishChapter = { showFinishConfirm = true },
                            onUpdateAuthorNote = onUpdateAuthorNote,
                            onEditAuthorNoteClick = {
                                authorNoteDraft = novel.authorNote
                                showAuthorNoteDialog = true
                            },
                        )
                        HorizontalDivider(
                            thickness = novelTheme.borderWidth,
                            color = paperTheme.border.copy(alpha = 0.78f),
                        )
                    }
                }

                // Manuscript Prose Stream inheriting unified paper LocalAppTheme
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
                    modifier = Modifier.weight(1f),
                )

                // Single-row paper-tinted Composer inheriting LocalAppTheme
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
