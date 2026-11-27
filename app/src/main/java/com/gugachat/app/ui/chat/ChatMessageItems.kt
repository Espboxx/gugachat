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
internal fun UserMessageBlock(
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
internal fun UserMessageBody(
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
internal fun UserMessageActionsRow(
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
internal fun UserMicroActionChip(
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
internal fun NovelChapterDividerRibbon(chapter: NovelChapter) {
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
internal fun AssistantMessageBlock(
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
internal fun MicroVariantBadge(
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
internal fun AssistantBodyAndNotices(
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
internal fun DramaticChoiceCard(
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
internal fun VariantSwipePill(
    index: Int,
    total: Int,
    onSwitch: (Int) -> Unit,
) {
    GuVariantSwipePill(
        index = index,
        total = total,
        onSwitch = onSwitch,
    )
}

@Composable
internal fun AssistantFooterRow(
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

