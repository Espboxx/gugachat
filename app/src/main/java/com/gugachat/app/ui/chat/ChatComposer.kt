package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpInputKind
import java.io.File

@Composable
fun ChatComposerBar(
    mode: ConversationMode,
    draftText: String,
    draftAttachments: List<ChatAttachmentMeta>,
    draftNovelKind: NovelInputKind,
    draftRpKind: RpInputKind,
    draftDiceRoll: RpDiceRoll?,
    waterline: ContextWaterlineSummary,
    isStreaming: Boolean,
    supportsVision: Boolean,
    onDraftChange: (String) -> Unit,
    onSelectNovelKind: (NovelInputKind) -> Unit,
    onSelectRpKind: (RpInputKind) -> Unit,
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

    Surface(
        color = c.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, c.border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Top micro-strip: Mode pills or Waterline + Recall badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode == ConversationMode.novel) {
                    GuPill(strings.novel.inputDirector, draftNovelKind == NovelInputKind.director, { onSelectNovelKind(NovelInputKind.director) })
                    GuPill(strings.novel.inputContinue, draftNovelKind == NovelInputKind.`continue`, { onSelectNovelKind(NovelInputKind.`continue`) })
                    GuPill(strings.novel.inputAction, draftNovelKind == NovelInputKind.branch, { onSelectNovelKind(NovelInputKind.branch) })
                    GuPill(strings.novel.inputDialogue, draftNovelKind == NovelInputKind.dialogue, { onSelectNovelKind(NovelInputKind.dialogue) })
                } else if (mode == ConversationMode.rp) {
                    GuPill(strings.rp.inputAction, draftRpKind == RpInputKind.action, { onSelectRpKind(RpInputKind.action) })
                    GuPill(strings.rp.inputSay, draftRpKind == RpInputKind.speech, { onSelectRpKind(RpInputKind.speech) })
                    GuPill(strings.rp.inputThought, draftRpKind == RpInputKind.thought, { onSelectRpKind(RpInputKind.thought) })
                    GuPill(strings.rp.inputOoc, draftRpKind == RpInputKind.gm_command, { onSelectRpKind(RpInputKind.gm_command) })
                }

                if (waterline.retrievedMemoryCount > 0) {
                    GuBadge(
                        text = strings.chat.retrievedMemoryCountPill.fmt("count" to waterline.retrievedMemoryCount),
                        variant = GuBadgeVariant.HONEY,
                        modifier = Modifier.clickable(onClick = onOpenRecallInspector),
                    )
                }
                GuBadge(
                    text = "${waterline.percentage}% (${waterline.usedTokens / 1000}K)",
                    variant = if (waterline.exceedsModelWindow) GuBadgeVariant.DANGER else GuBadgeVariant.NEUTRAL,
                    modifier = Modifier.clickable(onClick = onOpenRecallInspector),
                )
            }

            if (draftDiceRoll != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.tag)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "🎲 ${draftDiceRoll.skill ?: ""} ${draftDiceRoll.expression} = ${draftDiceRoll.total}",
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

            if (draftAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draftAttachments.forEach { att ->
                        Box(modifier = Modifier.size(56.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(File(att.localPath)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, c.outline, RoundedCornerShape(8.dp)),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (mode == ConversationMode.rp) {
                    IconButton(onClick = onOpenDiceDialog, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Casino, contentDescription = strings.rp.diceBtn, tint = c.accent)
                    }
                }
                if (supportsVision || draftAttachments.isNotEmpty()) {
                    IconButton(onClick = onPickImage, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = strings.chat.uploadImages, tint = c.primary)
                    }
                }

                val placeholder = when (mode) {
                    ConversationMode.novel -> strings.novel.composerPlaceholder
                    ConversationMode.rp -> strings.rp.composerPlaceholder
                    else -> strings.chat.composerPlaceholder
                }
                OutlinedTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder, color = c.muted, fontSize = 13.sp, maxLines = 1) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(theme.inputRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink,
                        focusedContainerColor = c.surfaceSoft,
                        unfocusedContainerColor = c.surfaceSoft,
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.border,
                    ),
                )

                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(c.danger)
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = strings.chat.stop, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    val canSend = draftText.isNotBlank() || draftAttachments.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (canSend) c.primary else c.surfaceSoft)
                            .clickable(enabled = canSend, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = strings.chat.send,
                            tint = if (canSend) Color.White else c.muted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}
