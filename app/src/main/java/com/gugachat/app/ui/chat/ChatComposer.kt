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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
    onDraftChange: (String) -> Unit,
    onSelectNovelKind: (NovelInputKind) -> Unit,
    onSelectRpKind: (RpInputKind) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleMemory: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenDiceDialog: () -> Unit,
    onClearDiceRoll: () -> Unit,
    onOpenRecallInspector: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isImpersonating: Boolean = false,
    onImpersonate: () -> Unit = {},
    onContinueRp: () -> Unit = {},
    onContinueNovel: () -> Unit = {},
    onOpenNovelStudio: (Int) -> Unit = {},
    onEnterPureReading: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    var showToolsPanel by remember { mutableStateOf(false) }
    var showKindMenu by remember(mode) { mutableStateOf(false) }

    BackHandler(enabled = showToolsPanel) {
        showToolsPanel = false
    }

    Surface(
        color = if (theme.isDark) c.surfaceElevated else c.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(theme.borderWidth, c.border),
        shadowElevation = if (theme.isDark) 3.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draftAttachments.forEach { att ->
                        Box(modifier = Modifier.size(60.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(File(att.localPath)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(theme.borderWidth, c.border, RoundedCornerShape(12.dp)),
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

            // Row 1: Compact full-width input area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draftText.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = c.muted,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    maxLines = 6,
                    textStyle = TextStyle(
                        color = c.ink,
                        fontSize = 14.5.sp,
                        lineHeight = 20.5.sp,
                    ),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Row 2: Compact balanced bottom toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(modifier = Modifier.size(32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (showToolsPanel) c.primarySoft else c.surfaceSoft)
                            .border(
                                width = theme.borderWidth,
                                color = if (showToolsPanel) c.primary.copy(alpha = 0.55f) else c.border,
                                shape = CircleShape,
                            )
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
                    if (!showToolsPanel && waterline.exceedsModelWindow) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(1.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(c.danger),
                        )
                    }
                }

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

                    Box {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillBg)
                                .border(theme.borderWidth, pillBorder, RoundedCornerShape(999.dp))
                                .clickable { showKindMenu = true }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = activeKindLabel,
                                color = pillFg,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(14.dp),
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

                // Iconic DeepSeek DeepThink cobalt pill
                DeepThinkTogglePill(
                    label = strings.chat.deepThinkToggle,
                    active = enableReasoning,
                    onClick = onToggleReasoning,
                )

                if (mode == ConversationMode.chat) {
                    ComposerChip(
                        label = strings.chat.memoryShort,
                        icon = Icons.Default.Bookmarks,
                        active = enableAutoMemory,
                        onClick = onToggleMemory,
                    )
                }

                Spacer(Modifier.weight(1f))

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
                            .border(
                                width = theme.borderWidth,
                                color = if (canSend) c.primary else c.border,
                                shape = CircleShape,
                            )
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

            AnimatedVisibility(visible = showToolsPanel) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.7f),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComposerChip(
                            label = strings.chat.memoryShort,
                            icon = Icons.Default.Bookmarks,
                            active = enableAutoMemory,
                            onClick = onToggleMemory,
                        )
                        if (supportsVision || draftAttachments.isNotEmpty()) {
                            ComposerChip(
                                label = strings.chat.uploadImages,
                                icon = Icons.Default.AddPhotoAlternate,
                                active = draftAttachments.isNotEmpty(),
                                onClick = {
                                    showToolsPanel = false
                                    onPickImage()
                                },
                            )
                        }
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
                        ComposerChip(
                            label = "${waterline.percentage}% · ${waterline.usedTokens / 1000}K",
                            icon = null,
                            active = waterline.exceedsModelWindow,
                            tone = if (waterline.exceedsModelWindow) ComposerChipTone.DANGER else ComposerChipTone.NEUTRAL,
                            onClick = {
                                showToolsPanel = false
                                onOpenRecallInspector()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepThinkTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val bg by animateColorAsState(
        targetValue = if (active) c.primary else c.surfaceSoft,
        label = "deep_think_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) Color.White else c.muted,
        label = "deep_think_fg",
    )
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = theme.borderWidth,
                color = if (active) c.primary else c.border,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ComposerChipTone {
    PRIMARY,
    ACCENT,
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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val bg = when {
        tone == ComposerChipTone.DANGER -> c.dangerBg
        active && tone == ComposerChipTone.ACCENT -> c.tag
        active && tone == ComposerChipTone.PRIMARY -> c.primarySoft
        else -> c.surfaceSoft
    }
    val fg = when {
        tone == ComposerChipTone.DANGER -> c.danger
        active && tone == ComposerChipTone.ACCENT -> c.tagInk
        active && tone == ComposerChipTone.PRIMARY -> c.primarySoftInk
        else -> c.muted
    }
    val border = when {
        tone == ComposerChipTone.DANGER -> c.danger.copy(alpha = 0.45f)
        active && tone == ComposerChipTone.ACCENT -> c.accent.copy(alpha = 0.48f)
        active && tone == ComposerChipTone.PRIMARY -> c.primary.copy(alpha = 0.42f)
        else -> c.border
    }

    Row(
        modifier = Modifier
            .height(30.dp)
            .alpha(if (enabled || loading) 1f else 0.48f)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(theme.borderWidth, border, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 9.dp),
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
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
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
