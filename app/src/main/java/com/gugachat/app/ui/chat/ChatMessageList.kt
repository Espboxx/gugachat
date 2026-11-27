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
    LaunchedEffect(active.isEmpty()) {
        if (active.isEmpty()) {
            onScrollHideBottomBarChanged(false)
            onCanScrollBackwardChanged(false)
        }
    }
    LaunchedEffect(listState, active.size) {
        if (active.isEmpty()) {
            onScrollHideBottomBarChanged(false)
            return@LaunchedEffect
        }
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        var accumDelta = 0
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset,
                listState.canScrollBackward to listState.canScrollForward,
            )
        }.collect { (scrolling, pos, edges) ->
            val (idx, offset) = pos
            val (canBack, canForward) = edges
            val rawDelta = when {
                idx > prevIndex -> 120
                idx < prevIndex -> -120
                else -> offset - prevOffset
            }
            prevIndex = idx
            prevOffset = offset

            if (!canBack || !canForward) {
                accumDelta = 0
                onScrollHideBottomBarChanged(false)
            } else if (scrolling && rawDelta != 0) {
                if ((accumDelta > 0 && rawDelta < 0) || (accumDelta < 0 && rawDelta > 0)) {
                    accumDelta = 0
                }
                accumDelta += rawDelta
                if (accumDelta > 28) {
                    onScrollHideBottomBarChanged(true)
                    accumDelta = 0
                } else if (accumDelta < -18) {
                    onScrollHideBottomBarChanged(false)
                    accumDelta = 0
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onScrollHideBottomBarChanged(false)
            onCanScrollBackwardChanged(false)
        }
    }

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
        AlertDialog(
            onDismissRequest = { editTarget = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = c.surfaceElevated,
            title = { Text(strings.chat.editResend, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) },
            text = {
                GuTextField(
                    value = editBuffer,
                    onValueChange = { editBuffer = it },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
            },
            confirmButton = {
                GuButton(
                    onClick = {
                        onEditUserMessage(editTarget!!.id, editBuffer)
                        editTarget = null
                    },
                    enabled = editBuffer.isNotBlank(),
                    compact = true,
                ) {
                    Text(strings.chat.send, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (editAssistantTarget != null) {
        AlertDialog(
            onDismissRequest = { editAssistantTarget = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = c.surfaceElevated,
            title = {
                Text(
                    text = if (mode == ConversationMode.rp) strings.rp.editReplyTitle else strings.common.edit,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                GuTextField(
                    value = editAssistantBuffer,
                    onValueChange = { editAssistantBuffer = it },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 10,
                )
            },
            confirmButton = {
                GuButton(
                    onClick = {
                        onEditAssistantMessage(editAssistantTarget!!.id, editAssistantBuffer)
                        editAssistantTarget = null
                    },
                    enabled = editAssistantBuffer.isNotBlank(),
                    compact = true,
                ) {
                    Text(strings.common.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editAssistantTarget = null }) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
            },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyWelcomeCanvas(
    mode: ConversationMode,
    hasModelSelected: Boolean,
    selectedModelLabel: String,
    selectedGroupLabel: String,
    memoryEnabled: Boolean,
    rpConfig: RpConfig?,
    novelConfig: NovelConfig?,
    novelChapters: List<NovelChapter> = emptyList(),
    onOpenModelSheet: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    onOpenNovelStudio: (Int) -> Unit = {},
    onOpenRpWizard: () -> Unit = {},
    onOpenNovelWizard: () -> Unit = {},
    onStartNormalChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    if ((mode == ConversationMode.rp && rpConfig != null) || (mode == ConversationMode.novel && novelConfig != null)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!hasModelSelected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.surfaceSoft)
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = c.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            if (mode == ConversationMode.rp && rpConfig != null) {
                GuGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.primarySoft.copy(alpha = 0.85f),
                                        c.tag.copy(alpha = 0.65f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuIconTile(
                                icon = Icons.Default.Casino,
                                tint = c.primarySoftInk,
                                backgroundColor = c.surfaceSoft,
                                size = 42.dp,
                                iconSize = 21.dp,
                                cornerRadius = 12.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = rpConfig.title,
                                    color = c.ink,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (rpConfig.playerName.isNotBlank()) {
                                    Text(
                                        text = "🎭 ${rpConfig.playerName}",
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            GuBadge(strings.rp.badge, variant = GuBadgeVariant.BLUE)
                        }
                    }

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (rpConfig.worldview.isNotBlank()) {
                            Text(
                                text = rpConfig.worldview,
                                color = c.muted,
                                fontSize = 13.5.sp,
                                lineHeight = 20.5.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val npcs = rpConfig.cast.filter { !it.isPlayer }
                        if (npcs.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                npcs.forEach { npc ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.surfaceSoft)
                                            .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        RpNpcPortraitAvatar(
                                            character = npc,
                                            fallbackName = npc.name,
                                            size = 20.dp,
                                        )
                                        Text(
                                            text = "${npc.name} ♥${npc.affinity}",
                                            color = c.ink,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        GuButton(
                            onClick = {
                                onStarterPrompt(
                                    if (isZh) "开始第一幕，请描写我身处的初始场景并给出行动选择。"
                                    else "Start Scene 1: describe the opening setting and suggest my first choices.",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.rp.wizardStartBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (mode == ConversationMode.novel && novelConfig != null) {
                GuGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.surfaceSoft,
                                        c.tag.copy(alpha = if (theme.isDark) 0.55f else 0.65f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GuIconTile(
                                icon = Icons.Default.AutoStories,
                                tint = c.tagInk,
                                backgroundColor = c.surface,
                                size = 42.dp,
                                iconSize = 21.dp,
                                cornerRadius = 12.dp,
                            )
                            Text(
                                text = novelConfig.title,
                                color = c.ink,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (novelConfig.genre.isNotBlank()) {
                                GuBadge(novelConfig.genre, variant = GuBadgeVariant.HONEY)
                            }
                        }
                    }

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val povLabel = when (novelConfig.pov) {
                            "first" -> strings.novel.povFirst
                            "third_omniscient" -> strings.novel.povThirdOmniscient
                            else -> strings.novel.povThirdLimited
                        }
                        val firstCh = novelChapters.firstOrNull { it.id == novelConfig.activeChapterId } ?: novelChapters.firstOrNull()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(povLabel, variant = GuBadgeVariant.BLUE)
                            GuBadge(
                                text = if (isZh) "目标 ${novelConfig.targetWordsPerChapter} 字/章" else "~${novelConfig.targetWordsPerChapter}w/ch",
                                variant = GuBadgeVariant.NEUTRAL,
                            )
                            if (novelConfig.antiAiRules) {
                                GuBadge(if (isZh) "反AI味" else "Anti-AI", variant = GuBadgeVariant.EMERALD)
                            }
                        }
                        if (novelConfig.synopsis.isNotBlank()) {
                            Text(
                                text = novelConfig.synopsis,
                                color = c.muted,
                                fontSize = 13.5.sp,
                                lineHeight = 20.5.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (firstCh != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceSoft)
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = firstCh.title,
                                    color = c.ink,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (firstCh.outline.isNotBlank()) {
                                    Text(
                                        text = firstCh.outline,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuButton(
                                onClick = { onOpenNovelStudio(3) },
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.weight(0.85f),
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.novel.studioBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = {
                                    val prompt = if (!firstCh?.outline.isNullOrBlank()) {
                                        if (isZh) "请正式开启《${firstCh!!.title}》，围绕本章细纲「${firstCh.outline}」，以有画面感和人物动作细节的场景切入故事。"
                                        else "Begin ${firstCh!!.title} with a vivid opening scene grounded in: ${firstCh.outline}"
                                    } else {
                                        if (isZh) "请正式开启第一章，以有画面感的场景切入故事。"
                                        else "Begin Chapter 1 with a vivid opening scene."
                                    }
                                    onStarterPrompt(prompt)
                                },
                                variant = GuButtonVariant.HONEY,
                                modifier = Modifier.weight(1.35f),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.novel.wizardEmptyBtn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )

            val heading = when (mode) {
                ConversationMode.rp -> rpConfig?.title?.ifBlank { strings.chat.modeRpTitle } ?: strings.chat.modeRpTitle
                ConversationMode.novel -> novelConfig?.title?.ifBlank { strings.chat.modeNovelTitle } ?: strings.chat.modeNovelTitle
                else -> strings.chat.welcomeTitle
            }
            Text(
                text = heading,
                color = c.ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            if (!hasModelSelected) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(c.muted.copy(alpha = 0.5f)),
                    )
                    Text(
                        text = strings.chat.selectModel,
                        color = c.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else if (selectedModelLabel.isNotBlank()) {
                val memLabel = if (memoryEnabled) strings.chat.memoryOn else strings.chat.memoryOff
                val metaLine = buildString {
                    if (selectedGroupLabel.isNotBlank()) append("$selectedGroupLabel · ")
                    append("${strings.chat.memoryShort}$memLabel")
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(c.primary),
                    )
                    GuMetaText(
                        text = metaLine,
                        color = c.muted,
                    )
                }
            }
        }

        // 3 light quick-mode pills: Normal Chat / Immersive RP / Long-form Novel
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WelcomeModeQuickPill(
                label = if (isZh) "💬 普通对话" else "💬 Normal Chat",
                active = mode == ConversationMode.chat,
                onClick = onStartNormalChat,
            )
            WelcomeModeQuickPill(
                label = if (isZh) "🎭 沉浸文游" else "🎭 Story RP",
                active = mode == ConversationMode.rp,
                onClick = onOpenRpWizard,
            )
            WelcomeModeQuickPill(
                label = if (isZh) "📖 长篇创作" else "📖 Novel",
                active = mode == ConversationMode.novel,
                onClick = onOpenNovelWizard,
            )
        }

        if (mode == ConversationMode.chat) {
            val starters = listOf(
                StarterTileSpec(
                    icon = Icons.Default.Code,
                    tint = c.primarySoftInk,
                    bg = c.primarySoft,
                    title = strings.chat.starterCodeTitle,
                    prompt = strings.chat.starterCodePrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.ImageSearch,
                    tint = c.success,
                    bg = c.successBg,
                    title = strings.chat.starterVisionTitle,
                    prompt = strings.chat.starterVisionPrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.Description,
                    tint = c.accent,
                    bg = c.accentSoft,
                    title = strings.chat.starterDocTitle,
                    prompt = strings.chat.starterDocPrompt,
                ),
                StarterTileSpec(
                    icon = Icons.Default.Psychology,
                    tint = c.primarySoftInk,
                    bg = c.primarySoft,
                    title = strings.chat.starterMemoryTitle,
                    prompt = strings.chat.starterMemoryPrompt,
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                starters.forEachIndexed { idx, spec ->
                    if (idx > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = c.border.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStarterPrompt(spec.prompt) }
                            .padding(horizontal = 6.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = spec.tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = spec.title,
                                color = c.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = spec.prompt,
                                color = c.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = c.muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeModeQuickPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) c.primarySoft else c.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (active) c.primarySoftInk else c.ink,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private data class StarterTileSpec(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val title: String,
    val prompt: String,
)

@Composable
private fun UserMessageBlock(
    mode: ConversationMode,
    msg: ChatMessage,
    isImageDecayed: Boolean,
    isFileDecayed: Boolean,
    effectiveLimitTokens: Int,
    isStreaming: Boolean,
    isActionsRevealed: Boolean,
    onToggleActions: () -> Unit,
    attachmentPathResolver: (String) -> File,
    attachmentsById: Map<String, ChatAttachmentMeta>,
    onOpenFilePreview: (ChatAttachmentMeta) -> Unit,
    onOpenImagePreview: (File, String) -> Unit,
    onEdit: () -> Unit,
    onBranch: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val tapInteraction = remember { MutableInteractionSource() }

    when (mode) {
        ConversationMode.novel -> {
            val noteShape = RoundedCornerShape(topStart = 14.dp, topEnd = 6.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 296.dp)
                        .clip(noteShape)
                        .background(c.surfaceSoft.copy(alpha = if (theme.isDark) 0.72f else 0.78f))
                        .drawBehind {
                            val barWidth = 2.dp.toPx()
                            drawRect(
                                color = c.accent.copy(alpha = 0.55f),
                                topLeft = Offset.Zero,
                                size = Size(barWidth, size.height),
                            )
                        }
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            onClick = onToggleActions,
                        )
                        .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UserMessageBody(
                        msg = msg,
                        isImageDecayed = isImageDecayed,
                        isFileDecayed = isFileDecayed,
                        effectiveLimitTokens = effectiveLimitTokens,
                        attachmentPathResolver = attachmentPathResolver,
                        attachmentsById = attachmentsById,
                        onOpenFilePreview = onOpenFilePreview,
                        onOpenImagePreview = onOpenImagePreview,
                        fontSizeSp = 13.5f,
                        lineHeightSp = 21.5f,
                    )
                }
                AnimatedVisibility(
                    visible = isActionsRevealed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    UserMessageActionsRow(
                        isStreaming = isStreaming,
                        onEdit = onEdit,
                        onBranch = onBranch,
                        onCopy = onCopy,
                        onDelete = onDelete,
                    )
                }
            }
        }

        ConversationMode.rp -> {
            val bubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 304.dp)
                        .clip(bubbleShape)
                        .background(c.surfaceSoft)
                        .border(0.5.dp, c.accent.copy(alpha = 0.30f), bubbleShape)
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            onClick = onToggleActions,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UserMessageBody(
                        msg = msg,
                        isImageDecayed = isImageDecayed,
                        isFileDecayed = isFileDecayed,
                        effectiveLimitTokens = effectiveLimitTokens,
                        attachmentPathResolver = attachmentPathResolver,
                        attachmentsById = attachmentsById,
                        onOpenFilePreview = onOpenFilePreview,
                        onOpenImagePreview = onOpenImagePreview,
                        fontSizeSp = 14.5f,
                        lineHeightSp = 22f,
                    )
                }
                AnimatedVisibility(
                    visible = isActionsRevealed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    UserMessageActionsRow(
                        isStreaming = isStreaming,
                        onEdit = onEdit,
                        onBranch = onBranch,
                        onCopy = onCopy,
                        onDelete = onDelete,
                    )
                }
            }
        }

        ConversationMode.chat -> {
            val bubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            val bubbleBg = if (theme.isDark) c.surfaceSoft else c.primarySoft

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 304.dp)
                        .clip(bubbleShape)
                        .background(bubbleBg)
                        .then(
                            if (theme.isDark) {
                                Modifier.border(0.5.dp, c.primary.copy(alpha = 0.25f), bubbleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            onClick = onToggleActions,
                        )
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UserMessageBody(
                        msg = msg,
                        isImageDecayed = isImageDecayed,
                        isFileDecayed = isFileDecayed,
                        effectiveLimitTokens = effectiveLimitTokens,
                        attachmentPathResolver = attachmentPathResolver,
                        attachmentsById = attachmentsById,
                        onOpenFilePreview = onOpenFilePreview,
                        onOpenImagePreview = onOpenImagePreview,
                        fontSizeSp = 15f,
                        lineHeightSp = 23f,
                    )
                }
                AnimatedVisibility(
                    visible = isActionsRevealed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    UserMessageActionsRow(
                        isStreaming = isStreaming,
                        onEdit = onEdit,
                        onBranch = onBranch,
                        onCopy = onCopy,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMessageBody(
    msg: ChatMessage,
    isImageDecayed: Boolean,
    isFileDecayed: Boolean,
    effectiveLimitTokens: Int,
    attachmentPathResolver: (String) -> File,
    attachmentsById: Map<String, ChatAttachmentMeta>,
    onOpenFilePreview: (ChatAttachmentMeta) -> Unit,
    onOpenImagePreview: (File, String) -> Unit,
    fontSizeSp: Float,
    lineHeightSp: Float,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    msg.inputKind?.let { kind ->
        val isZh = strings.language == AppLanguage.ZH
        val tagText = when (kind) {
            NovelInputKind.director -> strings.novel.inputDirector
            NovelInputKind.`continue` -> strings.novel.inputContinue
            NovelInputKind.branch -> if (isZh) "分支" else "Branch"
            NovelInputKind.dialogue -> strings.novel.inputDialogue
        }
        GuBadge(tagText, variant = GuBadgeVariant.HONEY)
    }
    msg.rpInputKind?.let { kind ->
        val tagText = when (kind) {
            RpInputKind.action -> strings.rp.inputAction
            RpInputKind.speech -> strings.rp.inputSay
            RpInputKind.thought -> strings.rp.inputThought
            RpInputKind.gm_command -> strings.rp.inputOoc
        }
        GuBadge(tagText, variant = GuBadgeVariant.BLUE)
    }
    if (msg.attachmentIds.isNotEmpty()) {
        val imageIds = msg.attachmentIds.filter { id -> attachmentsById[id]?.isFile != true }
        val fileMetas = msg.attachmentIds.mapNotNull { id -> attachmentsById[id]?.takeIf { it.isFile } }
        val turnBudget = remember(effectiveLimitTokens) {
            FileContextBudgeter.computeFileTurnBudget(effectiveLimitTokens, 2000)
        }

        if (imageIds.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                imageIds.forEach { id ->
                    val meta = attachmentsById[id]
                    val file = meta?.localPath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: attachmentPathResolver(id)
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = meta?.fileName?.ifBlank { null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenImagePreview(file, meta?.fileName.orEmpty()) },
                    )
                }
            }
            if (isImageDecayed) {
                GuBadge(strings.chat.decayedImageNotice, variant = GuBadgeVariant.AMBER)
            }
        }

        if (fileMetas.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fileMetas.forEach { meta ->
                    FileAttachmentCard(
                        meta = meta,
                        isSmartGuarded = meta.estimatedTokens > turnBudget,
                        onClick = { onOpenFilePreview(meta) },
                        compact = true,
                    )
                }
            }
            if (isFileDecayed) {
                GuBadge(strings.attachments.decayedFileNotice, variant = GuBadgeVariant.AMBER)
            }
        }
    }
    if (msg.rpDiceRoll != null) {
        GuBadge(
            text = "🎲 ${msg.rpDiceRoll.skill ?: ""} ${msg.rpDiceRoll.expression} = ${msg.rpDiceRoll.total}".trim(),
            variant = GuBadgeVariant.HONEY,
        )
    }
    if (msg.content.isNotBlank()) {
        SelectionContainer {
            Text(
                text = msg.content,
                color = c.ink,
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeightSp.sp,
            )
        }
    }
}

@Composable
private fun UserMessageActionsRow(
    isStreaming: Boolean,
    onEdit: () -> Unit,
    onBranch: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserMicroActionChip(
            icon = Icons.Default.Edit,
            label = if (isZh) "改词" else strings.common.edit,
            enabled = !isStreaming,
            tint = c.muted,
            onClick = onEdit,
        )
        UserMicroActionChip(
            icon = Icons.Default.AccountTree,
            label = if (isZh) "分支" else strings.novel.branchBtn,
            enabled = true,
            tint = c.muted,
            onClick = onBranch,
        )
        UserMicroActionChip(
            icon = Icons.Default.ContentCopy,
            label = strings.common.copy,
            enabled = true,
            tint = c.muted,
            onClick = onCopy,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !isStreaming, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = strings.common.delete,
                tint = if (!isStreaming) c.muted else c.muted.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun UserMicroActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = effectiveTint,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NovelChapterDividerRibbon(chapter: NovelChapter) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = theme.borderWidth,
            color = c.accent.copy(alpha = 0.35f),
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = c.tag,
            border = BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = c.tagInk,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = chapter.title,
                    color = c.tagInk,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = theme.borderWidth,
            color = c.accent.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun AssistantMessageBlock(
    mode: ConversationMode,
    msg: ChatMessage,
    rpConfig: RpConfig?,
    novelConfig: NovelConfig? = null,
    isLatestAssistant: Boolean,
    isActionsRevealed: Boolean,
    onToggleActions: () -> Unit,
    isStreaming: Boolean,
    streamingStatusHint: String? = null,
    isRefreshingChoices: Boolean,
    onSelectNovelChoice: (String) -> Unit,
    onRefreshNovelChoices: () -> Unit = {},
    onContinueNovel: () -> Unit = {},
    onOpenNovelPolish: () -> Unit = {},
    onSelectRpChoice: (RpChoice, Boolean) -> Unit,
    onRefreshRpChoices: () -> Unit,
    onContinueRp: () -> Unit,
    onEditAssistant: (String) -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVariant: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onFrontendAction: (String) -> Unit,
    onFrontendFillInput: (String) -> Unit = onFrontendAction,
    onFrontendSetFlag: (String, String) -> Unit,
    onFrontendAdjustAffinity: (String, Int) -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val (parsedReasoning, cleanBody) = remember(msg.content, msg.reasoning, mode) {
        extractMessageThinkAndBody(msg.reasoning, msg.content, mode)
    }
    val isThisMsgStreaming = isStreaming && msg.status == "streaming"
    val showFooterActions = !isThisMsgStreaming && (isLatestAssistant || isActionsRevealed)
    val showTopVariantMicroBadge = !isThisMsgStreaming && !isLatestAssistant && !isActionsRevealed && msg.variants.size > 1
    val tapInteraction = remember { MutableInteractionSource() }

    when (mode) {
        ConversationMode.rp -> {
            val (speakerName, speakerNpc) = remember(msg.id, msg.activeVariantIndex, rpConfig?.cast, rpConfig?.title) {
                resolveRpSpeaker(msg, rpConfig, strings.rp.badge)
            }
            val stageAccent = remember(rpConfig?.visualTheme?.accentHex) {
                resolveRpStageAccent(rpConfig?.visualTheme?.accentHex)
            }
            val activeVar = msg.activeVariant()
            val deltas = activeVar?.rpDeltas.orEmpty()
            val unlockedEnding = activeVar?.rpEndingUnlocked
            val affinityTier = speakerNpc?.let { RpEngine.affinityTier(it.affinity) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isLatestAssistant && !isThisMsgStreaming) {
                            Modifier.clickable(
                                interactionSource = tapInteraction,
                                indication = null,
                                onClick = onToggleActions,
                            )
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(theme.cardRadius),
                color = c.surface,
                border = BorderStroke(theme.borderWidth, c.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        RpNpcPortraitAvatar(
                            character = speakerNpc,
                            fallbackName = speakerName,
                            size = 34.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = speakerName,
                                color = c.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (speakerNpc != null && speakerNpc.mood.isNotBlank()) {
                                Text(
                                    text = speakerNpc.mood,
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (showTopVariantMicroBadge) {
                            MicroVariantBadge(
                                index = msg.activeVariantIndex,
                                total = msg.variants.size,
                                onClick = onToggleActions,
                            )
                        }
                        if (speakerNpc != null && affinityTier != null) {
                            val tierLabel = strings.rp.affinityTiers[affinityTier.code]
                                ?: if (isZh) affinityTier.zhLabel else affinityTier.enLabel
                            GuBadge(
                                text = "♥ ${speakerNpc.affinity} · $tierLabel",
                                variant = if (speakerNpc.affinity < 0) GuBadgeVariant.DANGER else GuBadgeVariant.HONEY,
                            )
                        }
                    }

                    val rpTheme = rpConfig?.visualTheme
                    val rpFontSizeSp = (rpTheme?.fontSizeSp ?: 15f).coerceIn(12f, 22f)

                    AssistantBodyAndNotices(
                        parsedReasoning = parsedReasoning,
                        cleanBody = cleanBody,
                        msg = msg,
                        isStreaming = isStreaming,
                        streamingStatusHint = streamingStatusHint,
                        useSerif = rpTheme?.useSerif ?: false,
                        fontSize = rpFontSizeSp.sp,
                        lineHeight = (rpFontSizeSp * 1.6f).sp,
                        rpConfig = rpConfig,
                        isRpMode = true,
                        onRetry = onRegenerate,
                        onFrontendAction = onFrontendAction,
                        onFrontendFillInput = onFrontendFillInput,
                        onFrontendSetFlag = onFrontendSetFlag,
                        onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                        onToast = onToast,
                    )

                    if (deltas.isNotEmpty() && !isStreaming && rpTheme?.showTurnDeltas != false) {
                        RpTurnDeltaStrip(deltas = deltas, onDarkStage = true)
                    }

                    if (unlockedEnding != null) {
                        RpEndingCard(ending = unlockedEnding)
                    }

                    if (isLatestAssistant && !isStreaming && (msg.rpChoices.isNotEmpty() || isRefreshingChoices || rpConfig?.enableChoices == true)) {
                        RpChoiceDeck(
                            choices = msg.rpChoices,
                            rp = rpConfig,
                            isRefreshing = isRefreshingChoices,
                            onSelectChoice = onSelectRpChoice,
                            onRefreshChoices = onRefreshRpChoices,
                            enabled = !isStreaming,
                            accentColor = stageAccent,
                        )
                    }

                    if (isLatestAssistant) {
                        if (showFooterActions) {
                            AssistantFooterRow(
                                msg = msg,
                                showVariantBar = msg.variants.size > 1,
                                isStreaming = isStreaming,
                                showContinue = !isStreaming,
                                onContinue = onContinueRp,
                                onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                                onCopy = { onCopyText(cleanBody) },
                                onRegenerate = onRegenerate,
                                onBranch = onBranch,
                                onDelete = onDelete,
                                onSwitchVariant = onSwitchVariant,
                            )
                        }
                    } else {
                        AnimatedVisibility(
                            visible = showFooterActions,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            AssistantFooterRow(
                                msg = msg,
                                showVariantBar = msg.variants.size > 1,
                                isStreaming = isStreaming,
                                showContinue = false,
                                onContinue = onContinueRp,
                                onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                                onCopy = { onCopyText(cleanBody) },
                                onRegenerate = onRegenerate,
                                onBranch = onBranch,
                                onDelete = onDelete,
                                onSwitchVariant = onSwitchVariant,
                            )
                        }
                    }
                }
            }
        }

        ConversationMode.novel -> {
            val syncingMeta = isThisMsgStreaming && NovelExport.isStreamingInternalMeta(msg.content)
            val showNovelChoicesDeck = isLatestAssistant && !isStreaming && (
                msg.novelChoices.isNotEmpty() ||
                    isRefreshingChoices ||
                    (novelConfig?.enableChoices == true && novelConfig.playStyle != "author")
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isLatestAssistant && !isThisMsgStreaming) {
                            Modifier.clickable(
                                interactionSource = tapInteraction,
                                indication = null,
                                onClick = onToggleActions,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showTopVariantMicroBadge) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        MicroVariantBadge(
                            index = msg.activeVariantIndex,
                            total = msg.variants.size,
                            onClick = onToggleActions,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }

                AssistantBodyAndNotices(
                    parsedReasoning = parsedReasoning,
                    cleanBody = cleanBody,
                    msg = msg,
                    isStreaming = isStreaming,
                    streamingStatusHint = streamingStatusHint,
                    useSerif = true,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    onRetry = onRegenerate,
                    onToast = onToast,
                )

                if (syncingMeta) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.surfaceSoft)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = c.accent,
                        )
                        Text(
                            text = strings.novel.syncingMeta,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (showNovelChoicesDeck) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.novel.choicesTitle,
                                color = c.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                onClick = onRefreshNovelChoices,
                                enabled = !isRefreshingChoices && !isStreaming,
                                shape = RoundedCornerShape(999.dp),
                                color = c.surfaceSoft,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isRefreshingChoices) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(11.dp),
                                            strokeWidth = 1.6.dp,
                                            color = c.primary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = c.primary,
                                            modifier = Modifier.size(12.5.dp),
                                        )
                                    }
                                    Text(
                                        text = if (isRefreshingChoices) strings.rp.refreshingChoices else strings.rp.refreshChoicesBtn,
                                        color = c.ink,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        msg.novelChoices.forEachIndexed { idx, choice ->
                            DramaticChoiceCard(
                                index = idx + 1,
                                label = choice,
                                metaBadge = null,
                                onClick = { onSelectNovelChoice(choice) },
                            )
                        }
                    }
                }

                if (isLatestAssistant) {
                    if (showFooterActions) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            showContinue = !isStreaming,
                            continueLabel = strings.novel.continueBtn,
                            onContinue = onContinueNovel,
                            showPolish = !isStreaming,
                            onPolish = onOpenNovelPolish,
                            onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                            onCopy = { onCopyText(cleanBody) },
                            onRegenerate = onRegenerate,
                            onBranch = onBranch,
                            onDelete = onDelete,
                            onSwitchVariant = onSwitchVariant,
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = showFooterActions,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            showContinue = false,
                            continueLabel = strings.novel.continueBtn,
                            onContinue = onContinueNovel,
                            showPolish = false,
                            onPolish = onOpenNovelPolish,
                            onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                            onCopy = { onCopyText(cleanBody) },
                            onRegenerate = onRegenerate,
                            onBranch = onBranch,
                            onDelete = onDelete,
                            onSwitchVariant = onSwitchVariant,
                        )
                    }
                }
            }
        }

        ConversationMode.chat -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isLatestAssistant && !isThisMsgStreaming) {
                            Modifier.clickable(
                                interactionSource = tapInteraction,
                                indication = null,
                                onClick = onToggleActions,
                            )
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuModelAvatar(
                        modelId = msg.model,
                        size = 20.dp,
                    )
                    Text(
                        text = msg.model?.takeIf { it.isNotBlank() } ?: strings.common.appName,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (showTopVariantMicroBadge) {
                        MicroVariantBadge(
                            index = msg.activeVariantIndex,
                            total = msg.variants.size,
                            onClick = onToggleActions,
                        )
                    }
                }

                AssistantBodyAndNotices(
                    parsedReasoning = parsedReasoning,
                    cleanBody = cleanBody,
                    msg = msg,
                    isStreaming = isStreaming,
                    streamingStatusHint = streamingStatusHint,
                    useSerif = false,
                    fontSize = 15.5.sp,
                    lineHeight = 24.5.sp,
                    onRetry = onRegenerate,
                    onToast = onToast,
                )

                if (isLatestAssistant) {
                    if (showFooterActions) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                            onCopy = { onCopyText(cleanBody) },
                            onRegenerate = onRegenerate,
                            onBranch = onBranch,
                            onDelete = onDelete,
                            onSwitchVariant = onSwitchVariant,
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = showFooterActions,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            onEdit = { onEditAssistant(cleanBody.ifBlank { msg.content }) },
                            onCopy = { onCopyText(cleanBody) },
                            onRegenerate = onRegenerate,
                            onBranch = onBranch,
                            onDelete = onDelete,
                            onSwitchVariant = onSwitchVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroVariantBadge(
    index: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        GuMetaText(
            text = "${index + 1}/$total",
            color = c.muted,
        )
    }
}

@Composable
private fun AssistantBodyAndNotices(
    parsedReasoning: String,
    cleanBody: String,
    msg: ChatMessage,
    isStreaming: Boolean,
    streamingStatusHint: String? = null,
    useSerif: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    onRetry: () -> Unit,
    onToast: (String) -> Unit,
    rpConfig: RpConfig? = null,
    isRpMode: Boolean = false,
    onFrontendAction: (String) -> Unit = {},
    onFrontendFillInput: (String) -> Unit = onFrontendAction,
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    if (parsedReasoning.isNotBlank()) {
        ReasoningCard(
            reasoning = parsedReasoning,
            isStreaming = msg.status == "streaming" && cleanBody.isEmpty(),
            messageKey = "${msg.id}:${msg.activeVariantIndex}",
            durationMs = msg.reasoningDurationMs ?: msg.usage?.reasoningDurationMs,
        )
    }

    if (cleanBody.isNotBlank()) {
        if (isRpMode) {
            RpRichMessageContent(
                rawMarkdown = cleanBody,
                rp = rpConfig,
                fontSize = fontSize,
                lineHeight = lineHeight,
                useSerif = useSerif,
                onCopyToast = onToast,
                onFrontendAction = onFrontendAction,
                onFrontendFillInput = onFrontendFillInput,
                onFrontendSetFlag = onFrontendSetFlag,
                onFrontendAdjustAffinity = onFrontendAdjustAffinity,
            )
        } else {
            MarkdownMessageText(
                markdown = cleanBody,
                fontSize = fontSize,
                lineHeight = lineHeight,
                useSerif = useSerif,
                onCopyToast = onToast,
            )
        }
    } else if (msg.status == "streaming") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!streamingStatusHint.isNullOrBlank()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = c.warning,
                )
                Text(
                    text = streamingStatusHint,
                    color = c.warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(strings.chat.streamingWriting, color = c.muted, fontSize = 13.5.sp)
            }
        }
    }

    if (msg.status == "streaming" && cleanBody.isNotBlank() && !streamingStatusHint.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = c.warningBg,
            border = BorderStroke(theme.borderWidth, c.warning.copy(alpha = 0.42f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = c.warning,
                )
                Text(
                    text = streamingStatusHint,
                    color = c.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (!msg.error.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = c.dangerBg,
            border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.42f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = c.danger,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp),
                )
                Text(
                    text = msg.error,
                    color = c.danger,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!isStreaming) {
                    TextButton(onClick = onRetry) {
                        Text(strings.common.retry, color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (msg.status == "aborted") {
        Text(strings.chat.stoppedNote, color = c.muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun DramaticChoiceCard(
    index: Int,
    label: String,
    metaBadge: String?,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceSoft,
        border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(c.primarySoft.copy(alpha = 0.65f), c.surfaceSoft)))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$index",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                text = label,
                color = c.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!metaBadge.isNullOrBlank()) {
                GuBadge(metaBadge, variant = GuBadgeVariant.HONEY)
            }
        }
    }
}

@Composable
private fun VariantSwipePill(
    index: Int,
    total: Int,
    onSwitch: (Int) -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surfaceSoft),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 30.dp)
                .clip(CircleShape)
                .clickable(enabled = index > 0) { onSwitch((index - 1).coerceAtLeast(0)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = if (index > 0) c.ink else c.muted.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp),
            )
        }
        GuMetaText(
            text = "${index + 1}/$total",
            color = c.ink,
        )
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 30.dp)
                .clip(CircleShape)
                .clickable(enabled = index < total - 1) { onSwitch((index + 1).coerceAtMost(total - 1)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (index < total - 1) c.ink else c.muted.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun AssistantFooterRow(
    msg: ChatMessage,
    showVariantBar: Boolean,
    isStreaming: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVariant: (Int) -> Unit,
    showContinue: Boolean = false,
    continueLabel: String? = null,
    onContinue: () -> Unit = {},
    showPolish: Boolean = false,
    onPolish: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showContinue || showPolish) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showContinue) {
                    Surface(
                        onClick = onContinue,
                        enabled = !isStreaming,
                        shape = RoundedCornerShape(999.dp),
                        color = c.primarySoft,
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = c.primarySoftInk,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = continueLabel ?: strings.rp.continueBtn,
                                color = c.primarySoftInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (showPolish) {
                    Surface(
                        onClick = onPolish,
                        enabled = !isStreaming,
                        shape = RoundedCornerShape(999.dp),
                        color = c.tag,
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = c.tagInk,
                                modifier = Modifier.size(13.5.dp),
                            )
                            Text(
                                text = if (isZh) "精修 / 三走向" else "Polish / 3 Paths",
                                color = c.tagInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = strings.common.copy,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onEdit, enabled = !isStreaming, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.common.edit,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onRegenerate, enabled = !isStreaming, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = strings.chat.regenerate,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onBranch, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = strings.novel.branchBtn,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onDelete, enabled = !isStreaming, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.common.delete,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
            }

            val cachedTokens = msg.usage?.cacheReadInputTokens ?: 0
            val totalTokens = msg.usage?.totalTokens
            val metaText = remember(cachedTokens, totalTokens, msg.usage?.promptTokens) {
                val parts = ArrayList<String>(2)
                if (cachedTokens > 0) {
                    val promptTok = msg.usage?.promptTokens ?: 0
                    val totalIn = if (promptTok >= cachedTokens) promptTok else promptTok + cachedTokens
                    val hitPct = if (totalIn > 0) {
                        kotlin.math.round(cachedTokens * 100.0 / totalIn).toInt().coerceIn(1, 100)
                    } else {
                        0
                    }
                    if (hitPct > 0) {
                        parts.add("⚡${hitPct}%")
                    } else {
                        parts.add("⚡${FileContextBudgeter.formatShortTokens(cachedTokens)}")
                    }
                }
                if (totalTokens != null && totalTokens > 0) {
                    parts.add(FileContextBudgeter.formatShortTokens(totalTokens))
                }
                parts.joinToString(" · ")
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showVariantBar && msg.variants.size > 1) {
                    VariantSwipePill(
                        index = msg.activeVariantIndex,
                        total = msg.variants.size,
                        onSwitch = onSwitchVariant,
                    )
                }
                if (metaText.isNotEmpty()) {
                    GuMetaText(
                        text = metaText,
                        color = c.muted,
                        modifier = Modifier.weight(1f, fill = false),
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
