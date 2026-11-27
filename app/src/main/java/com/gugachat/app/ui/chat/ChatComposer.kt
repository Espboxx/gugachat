package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpInputKind
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatComposerBar(
    mode: ConversationMode,
    draftText: String,
    draftAttachments: List<ChatAttachmentMeta>,
    draftNovelKind: NovelInputKind,
    draftRpKind: RpInputKind,
    draftDiceRoll: RpDiceRoll?,
    waterline: ContextWaterlineSummary,
    enableReasoning: Boolean,
    enableAutoMemory: Boolean,
    isStreaming: Boolean,
    supportsVision: Boolean,
    isImportingAttachment: Boolean = false,
    onDraftChange: (String) -> Unit,
    onSelectNovelKind: (NovelInputKind) -> Unit,
    onSelectRpKind: (RpInputKind) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleMemory: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit,
    onUpdateAttachmentInjectMode: (String, FileInjectMode) -> Unit = { _, _ -> },
    onReadAttachmentText: (suspend (String) -> String?)? = null,
    onOpenModelSheet: () -> Unit = {},
    onToast: (String) -> Unit = {},
    onOpenDiceDialog: () -> Unit,
    onClearDiceRoll: () -> Unit,
    onOpenRecallInspector: () -> Unit,
    onOpenContextSettings: () -> Unit = onOpenRecallInspector,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isImpersonating: Boolean = false,
    onImpersonate: () -> Unit = {},
    onContinueRp: () -> Unit = {},
    onContinueNovel: () -> Unit = {},
    onOpenNovelStudio: (Int) -> Unit = {},
    onEnterPureReading: () -> Unit = {},
    modifier: Modifier = Modifier,
    inputFocusRequester: FocusRequester? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    var showToolsPanel by remember { mutableStateOf(false) }
    var showKindMenu by remember(mode) { mutableStateOf(false) }
    var previewFileTarget by remember { mutableStateOf<ChatAttachmentMeta?>(null) }
    var previewImageTarget by remember { mutableStateOf<ChatAttachmentMeta?>(null) }

    val draftFiles = remember(draftAttachments) { draftAttachments.filter { it.isFile } }
    val draftHasImages = remember(draftAttachments) { draftAttachments.any { it.isImage } }
    val reservedEst = remember(waterline) {
        (waterline.memoryTokens + waterline.summaryTokens + waterline.novelLoreTokens + 1000).coerceAtLeast(1500)
    }

    previewFileTarget?.let { target ->
        val guarded = FileContextBudgeter.willCompressFile(
            meta = target,
            allDraftFiles = draftFiles,
            effectiveLimitTokens = waterline.effectiveLimitTokens,
            reservedFixedTokens = reservedEst,
        )
        FilePreviewAndStrategyDialog(
            meta = target,
            isSmartGuarded = guarded,
            onReadText = { id -> onReadAttachmentText?.invoke(id) },
            onSelectInjectMode = { newMode ->
                onUpdateAttachmentInjectMode(target.id, newMode)
                previewFileTarget = target.copy(injectMode = newMode)
            },
            onDismiss = { previewFileTarget = null },
            onCopiedToast = { onToast(strings.common.copied) },
        )
    }

    previewImageTarget?.let { target ->
        LocalImageLightboxDialog(
            file = File(target.localPath),
            title = target.fileName,
            onDismiss = { previewImageTarget = null },
        )
    }

    BackHandler(enabled = showToolsPanel) {
        showToolsPanel = false
    }

    val containerColor = if (theme.isDark) c.surfaceElevated.copy(alpha = 0.94f) else c.surface
    val elevation = if (!theme.isDark && containerColor.alpha >= 0.99f) 1.dp else 0.dp
    val borderStroke = BorderStroke(
        width = 0.5.dp,
        color = if (theme.isDark) c.border.copy(alpha = 0.55f) else c.outline.copy(alpha = 0.25f),
    )

    val showWaterlineStrip = waterline.hardOverflow || waterline.isSliding || waterline.sentPercentage >= 80
    val showRecallStrip = waterline.retrievedMemoryCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        if (showRecallStrip || showWaterlineStrip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showRecallStrip) {
                    Text(
                        text = "✦ ${strings.chat.retrievedMemoryCountPill.fmt("count" to waterline.retrievedMemoryCount)}",
                        color = c.primarySoftInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenRecallInspector)
                            .padding(horizontal = 2.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f))
                if (showWaterlineStrip) {
                    val sentShort = FileContextBudgeter.formatShortTokens(waterline.sentTokens)
                    val limitShort = FileContextBudgeter.formatShortTokens(waterline.effectiveLimitTokens)
                    val statusColor = if (waterline.hardOverflow) c.danger else c.warning
                    val prefix = if (waterline.hardOverflow) "⚠ " else ""
                    Text(
                        text = "$prefix${strings.contextUi.sentShortPrefix} ${waterline.sentPercentage}% · $sentShort / $limitShort",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenContextSettings)
                            .padding(horizontal = 2.dp),
                    )
                }
            }
        }

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(22.dp),
            border = borderStroke,
            shadowElevation = elevation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Expandable tools drawer renders ABOVE the single input row when opened
            AnimatedVisibility(visible = showToolsPanel) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Chatbox / pure-chat style 3-card quick upload row (always visible, never hidden on non-vision models)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComposerAttachmentActionTile(
                            label = strings.attachments.albumImages,
                            icon = Icons.Default.PhotoLibrary,
                            tint = c.primarySoftInk,
                            iconBg = c.primarySoft,
                            onClick = {
                                showToolsPanel = false
                                onPickImage()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ComposerAttachmentActionTile(
                            label = strings.attachments.takePhoto,
                            icon = Icons.Default.PhotoCamera,
                            tint = c.accent,
                            iconBg = c.accentSoft,
                            onClick = {
                                showToolsPanel = false
                                onTakePhoto()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ComposerAttachmentActionTile(
                            label = strings.attachments.uploadFiles,
                            icon = Icons.Default.AttachFile,
                            tint = c.success,
                            iconBg = c.successBg,
                            onClick = {
                                showToolsPanel = false
                                onPickFile()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DeepThinkTogglePill(
                            label = strings.chat.deepThinkToggle,
                            active = enableReasoning,
                            onClick = onToggleReasoning,
                        )
                        ComposerChip(
                            label = strings.chat.memoryShort,
                            icon = Icons.Default.Bookmarks,
                            active = enableAutoMemory,
                            onClick = onToggleMemory,
                        )
                        if (mode == ConversationMode.novel) {
                            ComposerChip(
                                label = strings.novel.continueBtn,
                                icon = Icons.Default.PlayArrow,
                                active = false,
                                enabled = !isStreaming,
                                tone = ComposerChipTone.PRIMARY,
                                onClick = {
                                    showToolsPanel = false
                                    onContinueNovel()
                                },
                            )
                            ComposerChip(
                                label = strings.novel.threePathsBtn,
                                icon = Icons.Default.AutoAwesome,
                                active = false,
                                tone = ComposerChipTone.ACCENT,
                                onClick = {
                                    showToolsPanel = false
                                    onOpenNovelStudio(2)
                                },
                            )
                            ComposerChip(
                                label = strings.novel.studioBtn,
                                icon = Icons.Default.Book,
                                active = false,
                                tone = ComposerChipTone.ACCENT,
                                onClick = {
                                    showToolsPanel = false
                                    onOpenNovelStudio(0)
                                },
                            )
                            ComposerChip(
                                label = strings.novel.pureReading,
                                icon = Icons.Default.AutoStories,
                                active = false,
                                tone = ComposerChipTone.NEUTRAL,
                                onClick = {
                                    showToolsPanel = false
                                    onEnterPureReading()
                                },
                            )
                        }
                        if (mode == ConversationMode.rp) {
                            ComposerChip(
                                label = strings.rp.diceBtn,
                                icon = Icons.Default.Casino,
                                active = draftDiceRoll != null,
                                tone = ComposerChipTone.ACCENT,
                                onClick = {
                                    showToolsPanel = false
                                    onOpenDiceDialog()
                                },
                            )
                            ComposerChip(
                                label = if (isImpersonating) strings.rp.impersonating else strings.rp.impersonateBtn,
                                icon = Icons.Default.AutoFixHigh,
                                active = isImpersonating,
                                loading = isImpersonating,
                                enabled = !isStreaming && !isImpersonating,
                                tone = ComposerChipTone.PRIMARY,
                                onClick = {
                                    showToolsPanel = false
                                    onImpersonate()
                                },
                            )
                            ComposerChip(
                                label = strings.rp.continueBtn,
                                icon = Icons.Default.PlayArrow,
                                active = false,
                                enabled = !isStreaming,
                                tone = ComposerChipTone.PRIMARY,
                                onClick = {
                                    showToolsPanel = false
                                    onContinueRp()
                                },
                            )
                            ComposerChip(
                                label = strings.rp.insertActionFmt,
                                icon = null,
                                active = false,
                                tone = ComposerChipTone.NEUTRAL,
                                onClick = {
                                    onDraftChange(insertRpFormatting(draftText, "*", "*"))
                                },
                            )
                            ComposerChip(
                                label = strings.rp.insertSpeechFmt,
                                icon = null,
                                active = false,
                                tone = ComposerChipTone.NEUTRAL,
                                onClick = {
                                    val (openQ, closeQ) = if (strings.language == AppLanguage.ZH) "“" to "”" else "\"" to "\""
                                    onDraftChange(insertRpFormatting(draftText, openQ, closeQ))
                                },
                            )
                        }
                        if (waterline.retrievedMemoryCount > 0) {
                            ComposerChip(
                                label = strings.chat.retrievedMemoryCountPill.fmt("count" to waterline.retrievedMemoryCount),
                                icon = null,
                                active = true,
                                tone = ComposerChipTone.ACCENT,
                                onClick = {
                                    showToolsPanel = false
                                    onOpenRecallInspector()
                                },
                            )
                        }
                        val sentPct = waterline.sentPercentage
                        val sentLabel = "${strings.contextUi.sentShortPrefix} ${sentPct}% · ${FileContextBudgeter.formatShortTokens(waterline.sentTokens)}"
                        val waterlineTone = when {
                            waterline.hardOverflow -> ComposerChipTone.DANGER
                            waterline.isSliding -> ComposerChipTone.WARNING
                            else -> ComposerChipTone.NEUTRAL
                        }
                        ComposerChip(
                            label = sentLabel,
                            icon = null,
                            active = waterline.hardOverflow || waterline.isSliding,
                            tone = waterlineTone,
                            onClick = {
                                showToolsPanel = false
                                onOpenContextSettings()
                            },
                        )
                    }
                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.7f),
                    )
                }
            }

            // Non-vision model warning banner with 1-tap switch
            if (draftHasImages && !supportsVision) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.warning.copy(alpha = if (theme.isDark) 0.16f else 0.11f),
                    border = BorderStroke(theme.borderWidth, c.warning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = c.warning,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = strings.attachments.visionUnsupportedBanner,
                            color = c.warning,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            onClick = onOpenModelSheet,
                            shape = RoundedCornerShape(999.dp),
                            color = c.warning,
                        ) {
                            Text(
                                text = strings.attachments.switchVisionModel,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // Parsing spinner strip
            if (isImportingAttachment) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.surfaceSoft)
                        .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.8.dp,
                        color = c.primary,
                    )
                    Text(
                        text = strings.attachments.parsingFile,
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    draftAttachments.forEach { att ->
                        if (att.isFile) {
                            val guarded = FileContextBudgeter.willCompressFile(
                                meta = att,
                                allDraftFiles = draftFiles,
                                effectiveLimitTokens = waterline.effectiveLimitTokens,
                                reservedFixedTokens = reservedEst,
                            )
                            FileAttachmentCard(
                                meta = att,
                                isSmartGuarded = guarded,
                                onClick = { previewFileTarget = att },
                                onRemove = { onRemoveAttachment(att.id) },
                                compact = true,
                            )
                        } else {
                            Box(modifier = Modifier.size(60.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(File(att.localPath)),
                                    contentDescription = att.fileName.ifBlank { null },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .align(Alignment.BottomStart)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(theme.borderWidth, c.border, RoundedCornerShape(12.dp))
                                        .clickable { previewImageTarget = att },
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onRemoveAttachment(att.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(c.danger),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = strings.common.delete,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mode == ConversationMode.rp && draftDiceRoll != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.tag)
                        .border(theme.borderWidth, c.accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "🎲 ${draftDiceRoll.skill ?: ""} ${draftDiceRoll.expression} = ${draftDiceRoll.total}".trim(),
                        color = c.tagInk,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClearDiceRoll),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.common.clear,
                            tint = c.tagInk,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            val placeholder = when (mode) {
                ConversationMode.novel -> strings.novel.composerPlaceholder
                ConversationMode.rp -> strings.rp.composerPlaceholder
                else -> strings.chat.composerPlaceholder
            }

            // Single-row compact composer (~46-48dp idle, grows upward cleanly up to 5 lines)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Left: 32dp circle (+) tools trigger with status dot
                Box(modifier = Modifier.size(32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (showToolsPanel) c.primarySoft else c.surfaceSoft)
                            .clickable { showToolsPanel = !showToolsPanel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showToolsPanel) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = strings.chat.composerTools,
                            tint = if (showToolsPanel) c.primarySoftInk else c.muted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    val dotColor = when {
                        showToolsPanel -> null
                        waterline.hardOverflow -> c.danger
                        waterline.isSliding -> c.warning
                        draftHasImages && !supportsVision -> c.warning
                        draftDiceRoll != null -> c.warning
                        else -> null
                    }
                    if (dotColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(1.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }

                // Novel / RP ultra-compact kind selector pill (28dp)
                if (mode == ConversationMode.novel || mode == ConversationMode.rp) {
                    val isZh = strings.language == AppLanguage.ZH
                    val novelBranchLabel = if (isZh) "分支" else "Branch"
                    val activeKindLabel = if (mode == ConversationMode.novel) {
                        when (draftNovelKind) {
                            NovelInputKind.director -> strings.novel.inputDirector
                            NovelInputKind.`continue` -> strings.novel.inputContinue
                            NovelInputKind.branch -> novelBranchLabel
                            NovelInputKind.dialogue -> strings.novel.inputDialogue
                        }
                    } else {
                        when (draftRpKind) {
                            RpInputKind.action -> strings.rp.inputAction
                            RpInputKind.speech -> strings.rp.inputSay
                            RpInputKind.thought -> strings.rp.inputThought
                            RpInputKind.gm_command -> strings.rp.inputOoc
                        }
                    }
                    val pillBg = if (mode == ConversationMode.novel) c.tag else c.primarySoft
                    val pillFg = if (mode == ConversationMode.novel) c.tagInk else c.primarySoftInk
                    val pillBorder = (if (mode == ConversationMode.novel) c.accent else c.primary).copy(alpha = 0.42f)

                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillBg)
                                .border(theme.borderWidth, pillBorder, RoundedCornerShape(999.dp))
                                .clickable { showKindMenu = true }
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = activeKindLabel,
                                color = pillFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(13.dp),
                            )
                        }

                        GuPopupMenu(
                            expanded = showKindMenu,
                            onDismissRequest = { showKindMenu = false },
                        ) {
                            if (mode == ConversationMode.novel) {
                                listOf(
                                    NovelInputKind.director to strings.novel.inputDirector,
                                    NovelInputKind.`continue` to strings.novel.inputContinue,
                                    NovelInputKind.branch to novelBranchLabel,
                                    NovelInputKind.dialogue to strings.novel.inputDialogue,
                                ).forEach { (kind, label) ->
                                    val selected = draftNovelKind == kind
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                color = if (selected) c.accent else c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                        },
                                        trailingIcon = if (selected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            onSelectNovelKind(kind)
                                            showKindMenu = false
                                        },
                                    )
                                }
                            } else {
                                listOf(
                                    RpInputKind.action to strings.rp.inputAction,
                                    RpInputKind.speech to strings.rp.inputSay,
                                    RpInputKind.thought to strings.rp.inputThought,
                                    RpInputKind.gm_command to strings.rp.inputOoc,
                                ).forEach { (kind, label) ->
                                    val selected = draftRpKind == kind
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                color = if (selected) c.primarySoftInk else c.ink,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                        },
                                        trailingIcon = if (selected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            onSelectRpKind(kind)
                                            showKindMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Center: vertically centered 1-line BasicTextField that expands cleanly up to 5 lines
                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    maxLines = 5,
                    textStyle = TextStyle(
                        color = c.ink,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .weight(1f)
                        .then(inputFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draftText.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = c.muted,
                                    fontSize = 14.5.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                // Right: lightweight DeepThink toggle (frameless muted brain when off; soft primarySoft capsule when on)
                val thinkBg by animateColorAsState(
                    targetValue = if (enableReasoning) c.primarySoft else Color.Transparent,
                    label = "composer_think_bg",
                )
                val thinkFg by animateColorAsState(
                    targetValue = if (enableReasoning) c.primarySoftInk else c.muted,
                    label = "composer_think_fg",
                )
                if (enableReasoning) {
                    val thinkShortLabel = if (strings.language == AppLanguage.ZH) "深度" else "Think"
                    Row(
                        modifier = Modifier
                            .padding(bottom = 1.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(thinkBg)
                            .clickable(onClick = onToggleReasoning)
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "✦",
                            color = thinkFg,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = thinkShortLabel,
                            color = thinkFg,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 1.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleReasoning),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = strings.chat.deepThinkToggle,
                            tint = thinkFg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Right: sole solid primary circle Send / Stop button
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.danger)
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = strings.chat.stop,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    val canSend = draftText.isNotBlank() || draftAttachments.isNotEmpty()
                    val sendBg by animateColorAsState(
                        targetValue = if (canSend) c.primary else c.surfaceSoft,
                        label = "composer_send_bg",
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(sendBg)
                            .clickable(enabled = canSend, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = strings.chat.send,
                            tint = if (canSend) Color.White else c.muted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ComposerAttachmentActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceSoft,
        border = null,
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                color = c.ink,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeepThinkTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    val bg by animateColorAsState(
        targetValue = if (active) c.primarySoft else c.surfaceSoft,
        label = "deep_think_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) c.primarySoftInk else c.muted,
        label = "deep_think_fg",
    )
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ComposerChipTone {
    PRIMARY,
    ACCENT,
    WARNING,
    DANGER,
    NEUTRAL,
}

@Composable
private fun ComposerChip(
    label: String,
    icon: ImageVector?,
    active: Boolean,
    onClick: () -> Unit,
    tone: ComposerChipTone = ComposerChipTone.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = LocalAppTheme.current.colors
    val bg = when {
        tone == ComposerChipTone.DANGER -> c.dangerBg
        tone == ComposerChipTone.WARNING -> c.warningBg
        active && tone == ComposerChipTone.ACCENT -> c.tag
        active && tone == ComposerChipTone.PRIMARY -> c.primarySoft
        else -> c.surfaceSoft
    }
    val fg = when {
        tone == ComposerChipTone.DANGER -> c.danger
        tone == ComposerChipTone.WARNING -> c.warning
        active && tone == ComposerChipTone.ACCENT -> c.tagInk
        active && tone == ComposerChipTone.PRIMARY -> c.primarySoftInk
        else -> c.muted
    }

    Row(
        modifier = Modifier
            .height(30.dp)
            .alpha(if (enabled || loading) 1f else 0.48f)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.8.dp,
                color = fg,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun insertRpFormatting(draft: String, open: String, close: String): String {
    val trimmed = draft.trim()
    if (trimmed.isEmpty()) return "$open$close"
    if (!trimmed.contains(open) && !trimmed.contains(close)) {
        return "$open$trimmed$close"
    }
    val hasUnclosed = if (open == close) {
        trimmed.count { it.toString() == open } % 2 == 1
    } else {
        trimmed.count { it.toString() == open } > trimmed.count { it.toString() == close }
    }
    return if (hasUnclosed) {
        "${draft.trimEnd()}$close"
    } else {
        "${draft.trimEnd()} $open$close"
    }
}
