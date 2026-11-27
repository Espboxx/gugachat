package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModelAvatar
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.GuTextInputDialog
import com.gugachat.app.core.theme.GuVariantSwipePill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.activeVariant
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatMessageList(
    mode: ConversationMode,
    messages: List<ChatMessage>,
    waterline: ContextWaterlineSummary,
    hasModelSelected: Boolean,
    selectedModelLabel: String = "",
    selectedGroupLabel: String = "",
    memoryEnabled: Boolean = true,
    rpConfig: RpConfig? = null,
    novelConfig: NovelConfig? = null,
    novelChapters: List<NovelChapter> = emptyList(),
    isStreaming: Boolean,
    streamingStatusHint: String? = null,
    isRefreshingChoices: Boolean = false,
    attachmentPathResolver: (String) -> File,
    attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    onReadAttachmentText: (suspend (String) -> String?)? = null,
    onOpenModelSheet: () -> Unit = {},
    onStarterPrompt: (String) -> Unit,
    onSelectNovelChoice: (String) -> Unit,
    onRefreshNovelChoices: () -> Unit = {},
    onContinueNovel: () -> Unit = {},
    onOpenNovelStudio: (Int) -> Unit = {},
    onSelectRpChoice: (RpChoice, Boolean) -> Unit,
    onRefreshRpChoices: () -> Unit = {},
    onContinueRp: () -> Unit = {},
    onRegenerate: (String) -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onEditAssistantMessage: (String, String) -> Unit = { _, _ -> },
    onBranchFromMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onToast: (String) -> Unit,
    onFrontendAction: (String) -> Unit = {},
    onFrontendFillInput: (String) -> Unit = onFrontendAction,
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onScrollHideBottomBarChanged: (Boolean) -> Unit = {},
    onCanScrollBackwardChanged: (Boolean) -> Unit = {},
    onOpenRpWizard: () -> Unit = {},
    onOpenNovelWizard: () -> Unit = {},
    onStartNormalChat: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = remember(messages) { TimelineReconciler.activeMessages(messages) }
    val listState = rememberLazyListState()
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editBuffer by remember { mutableStateOf("") }
    var editAssistantTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editAssistantBuffer by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var previewFileTarget by remember { mutableStateOf<ChatAttachmentMeta?>(null) }
    var previewImageTarget by remember { mutableStateOf<Pair<File, String>?>(null) }

    previewFileTarget?.let { target ->
        val turnBudget = FileContextBudgeter.computeFileTurnBudget(waterline.effectiveLimitTokens, 2000)
        FilePreviewAndStrategyDialog(
            meta = target,
            isSmartGuarded = target.estimatedTokens > turnBudget,
            onReadText = { id -> onReadAttachmentText?.invoke(id) },
            onSelectInjectMode = null,
            onDismiss = { previewFileTarget = null },
            onCopiedToast = { onToast(strings.common.copied) },
        )
    }

    previewImageTarget?.let { (file, title) ->
        LocalImageLightboxDialog(
            file = file,
            title = title,
            onDismiss = { previewImageTarget = null },
        )
    }

    val lastMsg = active.lastOrNull()
    val haptic = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    var autoFollowBottom by remember { mutableStateOf(true) }
    var revealedActionMsgId by remember(active.firstOrNull()?.conversationId) { mutableStateOf<String?>(null) }

    val canScrollBack by remember(listState, active.size) {
        derivedStateOf { active.isNotEmpty() && listState.canScrollBackward }
    }
    LaunchedEffect(canScrollBack) {
        onCanScrollBackwardChanged(canScrollBack)
    }
    ObserveListScrollHideBottomBar(
        listState = listState,
        hasMessages = active.isNotEmpty(),
        onScrollHideBottomBarChanged = onScrollHideBottomBarChanged,
        onCanScrollBackwardChanged = onCanScrollBackwardChanged,
    )

    // Respect user scrolling up during long streaming replies; re-lock when returning to bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canGoDown) ->
                if (scrolling && canGoDown) {
                    autoFollowBottom = false
                } else if (!canGoDown) {
                    autoFollowBottom = true
                }
            }
    }

    LaunchedEffect(active.size) {
        if (active.isEmpty()) return@LaunchedEffect
        val bottomIndex = active.size + 1
        if (lastMsg?.role == "user" || !isStreaming) {
            autoFollowBottom = true
            listState.scrollToItem(bottomIndex)
        } else if (autoFollowBottom) {
            listState.scrollToItem(bottomIndex)
        }
    }

    LaunchedEffect(lastMsg?.content?.length, lastMsg?.reasoning?.length, isStreaming) {
        if (active.isEmpty() || !isStreaming || !autoFollowBottom) return@LaunchedEffect
        val bottomIndex = active.size + 1
        listState.scrollToItem(bottomIndex)
    }

    if (deleteTarget != null) {
        val isZh = strings.language == AppLanguage.ZH
        GuConfirmDialog(
            title = strings.common.delete,
            message = if (isZh) "确定删除这条消息？删除后无法恢复。" else "Delete this message permanently?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteMessage(deleteTarget!!.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (editTarget != null) {
        GuTextInputDialog(
            title = strings.chat.editResend,
            value = editBuffer,
            onValueChange = { editBuffer = it },
            confirmText = strings.chat.send,
            cancelText = strings.common.cancel,
            onConfirm = {
                onEditUserMessage(editTarget!!.id, editBuffer)
                editTarget = null
            },
            onDismiss = { editTarget = null },
            minLines = 3,
            maxLines = 6,
        )
    }

    if (editAssistantTarget != null) {
        GuTextInputDialog(
            title = if (mode == ConversationMode.rp) strings.rp.editReplyTitle else strings.common.edit,
            value = editAssistantBuffer,
            onValueChange = { editAssistantBuffer = it },
            confirmText = strings.common.save,
            cancelText = strings.common.cancel,
            onConfirm = {
                onEditAssistantMessage(editAssistantTarget!!.id, editAssistantBuffer)
                editAssistantTarget = null
            },
            onDismiss = { editAssistantTarget = null },
            minLines = 4,
            maxLines = 10,
        )
    }

    if (active.isEmpty()) {
        ChatEmptyWelcomeCanvas(
            mode = mode,
            hasModelSelected = hasModelSelected,
            selectedModelLabel = selectedModelLabel,
            selectedGroupLabel = selectedGroupLabel,
            memoryEnabled = memoryEnabled,
            rpConfig = rpConfig,
            novelConfig = novelConfig,
            novelChapters = novelChapters,
            onOpenModelSheet = onOpenModelSheet,
            onStarterPrompt = onStarterPrompt,
            onOpenNovelStudio = onOpenNovelStudio,
            onOpenRpWizard = onOpenRpWizard,
            onOpenNovelWizard = onOpenNovelWizard,
            onStartNormalChat = onStartNormalChat,
            modifier = modifier,
        )
        return
    }

    val chapterStartMsgMap = remember(mode, novelChapters, active) {
        if (mode != ConversationMode.novel || novelChapters.isEmpty()) {
            emptyMap()
        } else {
            val map = HashMap<String, NovelChapter>()
            NovelExport.groupMessagesByChapter(novelChapters, active).forEach { sec ->
                val ch = sec.chapter ?: return@forEach
                val firstMsg = sec.messages.firstOrNull() ?: return@forEach
                map[firstMsg.id] = ch
            }
            map
        }
    }

    val showScrollToBottom by remember(listState, active.size) {
        derivedStateOf {
            active.isNotEmpty() && listState.canScrollForward
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            items(active, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                val isImageDecayed = msg.id in waterline.decayedImageMessageIds
                val isFileDecayed = msg.id in waterline.decayedFileMessageIds
                val isLatestAssistant = !isUser && msg.id == active.lastOrNull { it.role == "assistant" }?.id

                if (isUser) {
                    val isRevealed = revealedActionMsgId == msg.id
                    UserMessageBlock(
                        mode = mode,
                        msg = msg,
                        isImageDecayed = isImageDecayed,
                        isFileDecayed = isFileDecayed,
                        effectiveLimitTokens = waterline.effectiveLimitTokens,
                        isStreaming = isStreaming,
                        isActionsRevealed = isRevealed,
                        onToggleActions = {
                            revealedActionMsgId = if (revealedActionMsgId == msg.id) null else msg.id
                        },
                        attachmentPathResolver = attachmentPathResolver,
                        attachmentsById = attachmentsById,
                        onOpenFilePreview = { previewFileTarget = it },
                        onOpenImagePreview = { f, title -> previewImageTarget = f to title },
                        onEdit = {
                            revealedActionMsgId = null
                            editBuffer = msg.content
                            editTarget = msg
                        },
                        onBranch = {
                            revealedActionMsgId = null
                            onBranchFromMessage(msg.id)
                        },
                        onCopy = {
                            com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, "message", msg.content)
                            onToast(strings.common.copied)
                            revealedActionMsgId = null
                        },
                        onDelete = {
                            revealedActionMsgId = null
                            deleteTarget = msg
                        },
                    )
                } else {
                    val chapterStart = chapterStartMsgMap[msg.id]
                    val canSwipeVariants = !isStreaming && msg.status != "streaming" && msg.variants.size > 1
                    val swipeModifier = if (canSwipeVariants) {
                        Modifier.pointerInput(msg.id, msg.activeVariantIndex, msg.variants.size) {
                            var totalDragX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                                onDragEnd = {
                                    when {
                                        totalDragX <= -swipeThresholdPx && msg.activeVariantIndex < msg.variants.lastIndex -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSwitchVariant(msg.id, msg.activeVariantIndex + 1)
                                        }
                                        totalDragX >= swipeThresholdPx && msg.activeVariantIndex > 0 -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSwitchVariant(msg.id, msg.activeVariantIndex - 1)
                                        }
                                    }
                                    totalDragX = 0f
                                },
                                onDragCancel = { totalDragX = 0f },
                            )
                        }
                    } else {
                        Modifier
                    }
                    val isRevealed = revealedActionMsgId == msg.id
                    Column(
                        modifier = swipeModifier,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (chapterStart != null) {
                            NovelChapterDividerRibbon(chapter = chapterStart)
                        }
                        AssistantMessageBlock(
                            mode = mode,
                            msg = msg,
                            rpConfig = rpConfig,
                            novelConfig = novelConfig,
                            isLatestAssistant = isLatestAssistant,
                            isActionsRevealed = isRevealed,
                            onToggleActions = {
                                if (!isLatestAssistant) {
                                    revealedActionMsgId = if (revealedActionMsgId == msg.id) null else msg.id
                                }
                            },
                            isStreaming = isStreaming,
                            streamingStatusHint = streamingStatusHint,
                            isRefreshingChoices = isRefreshingChoices,
                            onSelectNovelChoice = onSelectNovelChoice,
                            onRefreshNovelChoices = onRefreshNovelChoices,
                            onContinueNovel = onContinueNovel,
                            onOpenNovelPolish = { onOpenNovelStudio(2) },
                            onSelectRpChoice = onSelectRpChoice,
                            onRefreshRpChoices = onRefreshRpChoices,
                            onContinueRp = onContinueRp,
                            onEditAssistant = { clean ->
                                editAssistantBuffer = clean
                                editAssistantTarget = msg
                            },
                            onRegenerate = { onRegenerate(msg.id) },
                            onBranch = { onBranchFromMessage(msg.id) },
                            onDelete = { deleteTarget = msg },
                            onSwitchVariant = { idx -> onSwitchVariant(msg.id, idx) },
                            onCopyText = { body ->
                                com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, "reply", body)
                                onToast(strings.common.copied)
                            },
                            onFrontendAction = onFrontendAction,
                            onFrontendFillInput = onFrontendFillInput,
                            onFrontendSetFlag = onFrontendSetFlag,
                            onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                            onToast = onToast,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 10.dp),
        ) {
            Surface(
                onClick = {
                    autoFollowBottom = true
                    onScrollHideBottomBarChanged(false)
                    scope.launch {
                        val bottomIndex = (active.size + 1).coerceAtLeast(0)
                        listState.animateScrollToItem(bottomIndex)
                    }
                },
                shape = CircleShape,
                color = c.surfaceElevated.copy(alpha = 0.94f),
                border = BorderStroke(0.5.dp, c.outline.copy(alpha = 0.45f)),
                shadowElevation = if (theme.isDark) 4.dp else 2.dp,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}


internal fun extractMessageThinkAndBody(
    explicitReasoning: String,
    rawContent: String,
    mode: ConversationMode,
): Pair<String, String> {
    var reasoning = explicitReasoning
    var body = rawContent
    val closedMatch = Regex("^\\s*<think>([\\s\\S]*?)</think>\\s*", RegexOption.IGNORE_CASE).find(body)
    if (closedMatch != null) {
        val inner = closedMatch.groupValues[1].trim()
        if (inner.isNotEmpty()) {
            reasoning = if (reasoning.isBlank()) inner else "$reasoning\n$inner"
        }
        body = body.removeRange(closedMatch.range)
    } else if (body.trimStart().startsWith("<think>", ignoreCase = true)) {
        val idx = body.indexOf("<think>", ignoreCase = true)
        val inner = body.substring(idx + 7).trim()
        if (inner.isNotEmpty()) reasoning = inner
        body = ""
    }
    when (mode) {
        ConversationMode.novel -> body = NovelExport.stripInternalTags(body)
        ConversationMode.rp -> body = RpEngine.stripRpInternalTags(body)
        ConversationMode.chat -> Unit
    }
    return reasoning to body.trim()
}
