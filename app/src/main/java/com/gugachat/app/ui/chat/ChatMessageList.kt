package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.TimelineReconciler
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageList(
    mode: ConversationMode,
    messages: List<ChatMessage>,
    waterline: ContextWaterlineSummary,
    hasModelSelected: Boolean,
    selectedModelLabel: String = "",
    selectedGroupLabel: String = "",
    memoryEnabled: Boolean = true,
    isStreaming: Boolean,
    attachmentPathResolver: (String) -> File,
    onStarterPrompt: (String) -> Unit,
    onSelectNovelChoice: (String) -> Unit,
    onSelectRpChoice: (RpChoice) -> Unit,
    onRegenerate: (String) -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onBranchFromMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val active = remember(messages) { TimelineReconciler.activeMessages(messages) }
    val listState = rememberLazyListState()
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editBuffer by remember { mutableStateOf("") }

    LaunchedEffect(active.size, active.lastOrNull()?.content?.length) {
        if (active.isNotEmpty()) {
            listState.animateScrollToItem(active.lastIndex)
        }
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = c.surface,
            title = { Text(strings.chat.editResend, color = c.ink, fontWeight = FontWeight.ExtraBold) },
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
                    compact = true,
                ) {
                    Text(strings.chat.send, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text(strings.common.cancel, color = c.muted)
                }
            },
        )
    }

    if (active.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuCard(backgroundColor = c.awning, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.chat.welcomeTitle,
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                val memLabel = if (memoryEnabled) strings.chat.memoryOn else strings.chat.memoryOff
                Text(
                    text = if (hasModelSelected) {
                        strings.chat.welcomeSub.fmt(
                            "model" to selectedModelLabel.ifBlank { "-" },
                            "group" to selectedGroupLabel.ifBlank { "-" },
                            "memoryState" to memLabel,
                        )
                    } else {
                        strings.chat.welcomePickModel.fmt("group" to selectedGroupLabel.ifBlank { "-" })
                    },
                    color = c.muted,
                    fontSize = 13.sp,
                )
            }

            if (mode == ConversationMode.chat) {
                val starters = listOf(
                    strings.chat.starterCodeTitle to strings.chat.starterCodePrompt,
                    strings.chat.starterVisionTitle to strings.chat.starterVisionPrompt,
                    strings.chat.starterDocTitle to strings.chat.starterDocPrompt,
                    strings.chat.starterMemoryTitle to strings.chat.starterMemoryPrompt,
                )
                starters.forEach { (title, prompt) ->
                    GuCard(onClick = { onStarterPrompt(prompt) }, modifier = Modifier.fillMaxWidth()) {
                        Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(prompt, color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        items(active, key = { it.id }) { msg ->
            val isUser = msg.role == "user"
            val isDecayed = msg.id in waterline.decayedImageMessageIds
            val isLatestAssistant = !isUser && msg.id == active.lastOrNull { it.role == "assistant" }?.id

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                if (isUser) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                            .background(c.primary)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (msg.attachmentIds.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                msg.attachmentIds.forEach { id ->
                                    Image(
                                        painter = rememberAsyncImagePainter(attachmentPathResolver(id)),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                            if (isDecayed) {
                                GuBadge(strings.chat.decayedImageNotice, variant = GuBadgeVariant.AMBER)
                            }
                        }
                        if (msg.rpDiceRoll != null) {
                            GuBadge(
                                text = "🎲 ${msg.rpDiceRoll.skill ?: ""} ${msg.rpDiceRoll.expression} = ${msg.rpDiceRoll.total}",
                                variant = GuBadgeVariant.HONEY,
                            )
                        }
                        if (msg.content.isNotBlank()) {
                            Text(
                                text = msg.content,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { editBuffer = msg.content; editTarget = msg },
                            enabled = !isStreaming,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = strings.chat.editResend, tint = c.muted, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = { onBranchFromMessage(msg.id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = { onDeleteMessage(msg.id) },
                            enabled = !isStreaming,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.muted, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                    ) {
                        val (parsedReasoning, cleanBody) = remember(msg.content, msg.reasoning, mode) {
                            extractThinkBlock(msg.reasoning, msg.content, stripNovelTags = mode == ConversationMode.novel)
                        }

                        if (parsedReasoning.isNotBlank() || msg.status == "streaming") {
                            ReasoningCard(
                                reasoning = parsedReasoning,
                                isStreaming = msg.status == "streaming" && cleanBody.isEmpty(),
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        if (cleanBody.isNotBlank()) {
                            MarkdownMessageText(
                                markdown = cleanBody,
                                onCopyToast = onToast,
                            )
                        } else if (msg.status == "streaming") {
                            Text(strings.chat.streamingWriting, color = c.muted, fontSize = 13.sp)
                        }

                        if (!msg.error.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            GuBadge(msg.error, variant = GuBadgeVariant.DANGER)
                        }
                        if (msg.status == "aborted") {
                            Spacer(Modifier.height(4.dp))
                            Text(strings.chat.stoppedNote, color = c.muted, fontSize = 11.sp)
                        }

                        if (isLatestAssistant && msg.novelChoices.isNotEmpty() && !isStreaming) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                msg.novelChoices.forEach { choice ->
                                    GuButton(
                                        onClick = { onSelectNovelChoice(choice) },
                                        compact = true,
                                        variant = GuButtonVariant.SUBTLE,
                                    ) {
                                        Text(choice, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        if (isLatestAssistant && msg.rpChoices.isNotEmpty() && !isStreaming) {
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                msg.rpChoices.forEach { choice ->
                                    GuButton(
                                        onClick = { onSelectRpChoice(choice) },
                                        compact = true,
                                        variant = GuButtonVariant.SUBTLE,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        val checkLabel = choice.skillCheck?.let { " [$it DC${choice.difficulty ?: 12}]" }.orEmpty()
                                        Text(
                                            text = "${choice.label}$checkLabel",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!msg.model.isNullOrBlank()) {
                                Text(
                                    text = msg.model,
                                    color = c.muted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }

                            if (msg.variants.size > 1) {
                                IconButton(
                                    onClick = {
                                        val prev = (msg.activeVariantIndex - 1).coerceAtLeast(0)
                                        onSwitchVariant(msg.id, prev)
                                    },
                                    enabled = msg.activeVariantIndex > 0,
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    text = "${msg.activeVariantIndex + 1}/${msg.variants.size}",
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                IconButton(
                                    onClick = {
                                        val next = (msg.activeVariantIndex + 1).coerceAtMost(msg.variants.lastIndex)
                                        onSwitchVariant(msg.id, next)
                                    },
                                    enabled = msg.activeVariantIndex < msg.variants.lastIndex,
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            }

                            msg.usage?.totalTokens?.let { tok ->
                                GuBadge("${tok}t", variant = GuBadgeVariant.NEUTRAL)
                                Spacer(Modifier.width(4.dp))
                            }

                            IconButton(
                                onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("reply", cleanBody))
                                    onToast(strings.common.copied)
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.common.copy, tint = c.muted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onRegenerate(msg.id) },
                                enabled = !isStreaming,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = strings.chat.regenerate, tint = c.muted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onBranchFromMessage(msg.id) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.AccountTree, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onDeleteMessage(msg.id) },
                                enabled = !isStreaming,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.muted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun extractThinkBlock(
    explicitReasoning: String,
    rawContent: String,
    stripNovelTags: Boolean,
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
    if (stripNovelTags) {
        body = NovelExport.stripInternalTags(body)
    }
    return reasoning to body.trim()
}
