package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
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
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
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
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    var showToolsPanel by remember { mutableStateOf(false) }
    var showKindMenu by remember(mode) { mutableStateOf(false) }

    Surface(
        color = c.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, c.border),
        shadowElevation = if (theme.isDark) 2.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draftAttachments.forEach { att ->
                        Box(modifier = Modifier.size(50.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(File(att.localPath)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, c.border, RoundedCornerShape(10.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(c.danger)
                                    .clickable { onRemoveAttachment(att.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
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
                        .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "🎲 ${draftDiceRoll.skill ?: ""} ${draftDiceRoll.expression} = ${draftDiceRoll.total}".trim(),
                        color = c.tagInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.common.clear,
                        tint = c.tagInk,
                        modifier = Modifier.size(14.dp).clickable(onClick = onClearDiceRoll),
                    )
                }
            }

            val placeholder = when (mode) {
                ConversationMode.novel -> strings.novel.composerPlaceholder
                ConversationMode.rp -> strings.rp.composerPlaceholder
                else -> strings.chat.composerPlaceholder
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(34.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (showToolsPanel) c.primarySoft else c.surfaceSoft)
                            .border(
                                width = 1.dp,
                                color = if (showToolsPanel) c.primary.copy(alpha = 0.5f) else c.border,
                                shape = CircleShape,
                            )
                            .clickable { showToolsPanel = !showToolsPanel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (showToolsPanel) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = strings.chat.composerTools,
                            tint = if (showToolsPanel) c.primary else c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!showToolsPanel && (waterline.exceedsModelWindow || enableReasoning)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (waterline.exceedsModelWindow) c.danger else c.primary),
                        )
                    }
                }

                if (mode == ConversationMode.novel || mode == ConversationMode.rp) {
                    val activeKindLabel = if (mode == ConversationMode.novel) {
                        when (draftNovelKind) {
                            NovelInputKind.director -> strings.novel.inputDirector
                            NovelInputKind.`continue` -> strings.novel.inputContinue
                            NovelInputKind.branch -> strings.novel.inputAction
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
                    val pillFg = if (mode == ConversationMode.novel) c.tagInk else c.primary

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillBg)
                                .border(
                                    width = 1.dp,
                                    color = (if (mode == ConversationMode.novel) c.accent else c.primary).copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable { showKindMenu = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
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

                        DropdownMenu(
                            expanded = showKindMenu,
                            onDismissRequest = { showKindMenu = false },
                            containerColor = c.surface,
                        ) {
                            if (mode == ConversationMode.novel) {
                                listOf(
                                    NovelInputKind.director to strings.novel.inputDirector,
                                    NovelInputKind.`continue` to strings.novel.inputContinue,
                                    NovelInputKind.branch to strings.novel.inputAction,
                                    NovelInputKind.dialogue to strings.novel.inputDialogue,
                                ).forEach { (kind, label) ->
                                    val selected = draftNovelKind == kind
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                color = if (selected) c.accent else c.ink,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                        },
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
                                                color = if (selected) c.primary else c.ink,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            )
                                        },
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = c.muted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = onDraftChange,
                        maxLines = 5,
                        textStyle = TextStyle(
                            color = c.ink,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        ),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c.danger)
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = strings.chat.stop, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                } else {
                    val canSend = draftText.isNotBlank() || draftAttachments.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (canSend) c.primary else c.surfaceSoft)
                            .border(
                                width = 1.dp,
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
                    HorizontalDivider(color = c.border.copy(alpha = 0.55f))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ComposerTogglePill(
                            label = strings.chat.deepThinkToggle,
                            icon = Icons.Default.AutoAwesome,
                            active = enableReasoning,
                            onClick = onToggleReasoning,
                        )
                        ComposerTogglePill(
                            label = strings.chat.memoryShort,
                            icon = Icons.Default.Bookmarks,
                            active = enableAutoMemory,
                            onClick = onToggleMemory,
                        )
                        if (supportsVision || draftAttachments.isNotEmpty()) {
                            ComposerTogglePill(
                                label = strings.chat.uploadImages,
                                icon = Icons.Default.AddPhotoAlternate,
                                active = draftAttachments.isNotEmpty(),
                                onClick = {
                                    showToolsPanel = false
                                    onPickImage()
                                },
                            )
                        }
                        if (mode == ConversationMode.rp) {
                            ComposerTogglePill(
                                label = strings.rp.diceBtn,
                                icon = Icons.Default.Casino,
                                active = draftDiceRoll != null,
                                useAccent = true,
                                onClick = {
                                    showToolsPanel = false
                                    onOpenDiceDialog()
                                },
                            )
                        }
                        if (waterline.retrievedMemoryCount > 0) {
                            GuBadge(
                                text = strings.chat.retrievedMemoryCountPill.fmt("count" to waterline.retrievedMemoryCount),
                                variant = GuBadgeVariant.HONEY,
                                modifier = Modifier.clickable {
                                    showToolsPanel = false
                                    onOpenRecallInspector()
                                },
                            )
                        }
                        GuBadge(
                            text = "${waterline.percentage}% · ${waterline.usedTokens / 1000}K",
                            variant = if (waterline.exceedsModelWindow) GuBadgeVariant.DANGER else GuBadgeVariant.NEUTRAL,
                            modifier = Modifier.clickable {
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
private fun ComposerTogglePill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    useAccent: Boolean = false,
) {
    val c = LocalAppTheme.current.colors
    val bg = when {
        active && useAccent -> c.tag
        active -> c.primarySoft
        else -> c.surfaceSoft
    }
    val fg = when {
        active && useAccent -> c.tagInk
        active -> c.primary
        else -> c.muted
    }
    val border = when {
        active && useAccent -> c.accent.copy(alpha = 0.5f)
        active -> c.primary.copy(alpha = 0.5f)
        else -> c.border
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
