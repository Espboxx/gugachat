package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.R
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpInputKind
import com.gugachat.app.domain.chat.TimelineReconciler
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
    isStreaming: Boolean,
    attachmentPathResolver: (String) -> File,
    onOpenModelSheet: () -> Unit = {},
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
        ChatEmptyWelcomeCanvas(
            mode = mode,
            hasModelSelected = hasModelSelected,
            selectedModelLabel = selectedModelLabel,
            selectedGroupLabel = selectedGroupLabel,
            memoryEnabled = memoryEnabled,
            rpConfig = rpConfig,
            novelConfig = novelConfig,
            onOpenModelSheet = onOpenModelSheet,
            onStarterPrompt = onStarterPrompt,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }

        items(active, key = { it.id }) { msg ->
            val isUser = msg.role == "user"
            val isDecayed = msg.id in waterline.decayedImageMessageIds
            val isLatestAssistant = !isUser && msg.id == active.lastOrNull { it.role == "assistant" }?.id

            if (isUser) {
                UserMessageBlock(
                    msg = msg,
                    isDecayed = isDecayed,
                    isStreaming = isStreaming,
                    attachmentPathResolver = attachmentPathResolver,
                    onEdit = { editBuffer = msg.content; editTarget = msg },
                    onBranch = { onBranchFromMessage(msg.id) },
                    onDelete = { onDeleteMessage(msg.id) },
                )
            } else {
                AssistantMessageBlock(
                    mode = mode,
                    msg = msg,
                    rpConfig = rpConfig,
                    isLatestAssistant = isLatestAssistant,
                    isStreaming = isStreaming,
                    onSelectNovelChoice = onSelectNovelChoice,
                    onSelectRpChoice = onSelectRpChoice,
                    onRegenerate = { onRegenerate(msg.id) },
                    onBranch = { onBranchFromMessage(msg.id) },
                    onDelete = { onDeleteMessage(msg.id) },
                    onSwitchVariant = { idx -> onSwitchVariant(msg.id, idx) },
                    onCopyText = { body ->
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("reply", body))
                        onToast(strings.common.copied)
                    },
                    onToast = onToast,
                )
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
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
    onOpenModelSheet: () -> Unit,
    onStarterPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.primarySoft)
                .border(1.dp, c.primary.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)),
            )
        }

        val heading = when (mode) {
            ConversationMode.rp -> rpConfig?.title?.ifBlank { strings.chat.modeRpTitle } ?: strings.chat.modeRpTitle
            ConversationMode.novel -> novelConfig?.title?.ifBlank { strings.chat.modeNovelTitle } ?: strings.chat.modeNovelTitle
            else -> strings.chat.welcomeTitle
        }
        Text(
            text = heading,
            color = c.ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )

        Surface(
            onClick = onOpenModelSheet,
            shape = RoundedCornerShape(999.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (memoryEnabled) c.success else c.muted),
                )
                Text(
                    text = if (hasModelSelected) {
                        "$selectedModelLabel · ${selectedGroupLabel.ifBlank { "-" }}"
                    } else {
                        strings.chat.selectModel
                    },
                    color = c.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(15.dp))
            }
        }

        when (mode) {
            ConversationMode.rp -> {
                if (rpConfig != null) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = c.accent.copy(alpha = 0.4f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(rpConfig.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            GuBadge(strings.rp.badge, variant = GuBadgeVariant.BLUE)
                        }
                        if (rpConfig.worldview.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = rpConfig.worldview,
                                color = c.muted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val npcs = rpConfig.cast.filter { !it.isPlayer }
                        if (npcs.isNotEmpty() || rpConfig.playerName.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuBadge("🎭 ${rpConfig.playerName}", variant = GuBadgeVariant.NEUTRAL)
                                npcs.forEach { npc ->
                                    GuBadge("${npc.name} ❤️${npc.affinity}", variant = GuBadgeVariant.HONEY)
                                }
                            }
                        }
                    }
                }
            }

            ConversationMode.novel -> {
                if (novelConfig != null) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = c.accent.copy(alpha = 0.4f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(novelConfig.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            if (novelConfig.genre.isNotBlank()) {
                                GuBadge(novelConfig.genre, variant = GuBadgeVariant.HONEY)
                            }
                        }
                        if (novelConfig.synopsis.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = novelConfig.synopsis,
                                color = c.muted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            ConversationMode.chat -> {
                val starters = listOf(
                    StarterTileSpec(
                        icon = Icons.Default.Code,
                        tint = c.primary,
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
                        tint = c.primaryDark,
                        bg = c.primarySoft,
                        title = strings.chat.starterMemoryTitle,
                        prompt = strings.chat.starterMemoryPrompt,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    starters.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { spec ->
                                StarterGridCard(
                                    spec = spec,
                                    onClick = { onStarterPrompt(spec.prompt) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
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
private fun StarterGridCard(
    spec: StarterTileSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(spec.bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(spec.icon, contentDescription = null, tint = spec.tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = spec.title,
            color = c.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = spec.prompt,
            color = c.muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UserMessageBlock(
    msg: ChatMessage,
    isDecayed: Boolean,
    isStreaming: Boolean,
    attachmentPathResolver: (String) -> File,
    onEdit: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 316.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(c.primarySoft)
                .border(
                    width = 1.dp,
                    color = c.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    msg.attachmentIds.forEach { id ->
                        Image(
                            painter = rememberAsyncImagePainter(attachmentPathResolver(id)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
                if (isDecayed) {
                    GuBadge(strings.chat.decayedImageNotice, variant = GuBadgeVariant.AMBER)
                }
            }
            if (msg.rpDiceRoll != null) {
                GuBadge(
                    text = "🎲 ${msg.rpDiceRoll.skill ?: ""} ${msg.rpDiceRoll.expression} = ${msg.rpDiceRoll.total}".trim(),
                    variant = GuBadgeVariant.HONEY,
                )
            }
            if (msg.content.isNotBlank()) {
                Text(
                    text = msg.content,
                    color = c.ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onEdit,
                enabled = !isStreaming,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = strings.chat.editResend, tint = c.muted, modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = onBranch,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = onDelete,
                enabled = !isStreaming,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun AssistantMessageBlock(
    mode: ConversationMode,
    msg: ChatMessage,
    rpConfig: RpConfig?,
    isLatestAssistant: Boolean,
    isStreaming: Boolean,
    onSelectNovelChoice: (String) -> Unit,
    onSelectRpChoice: (RpChoice) -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVariant: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val (parsedReasoning, cleanBody) = remember(msg.content, msg.reasoning, mode) {
        extractThinkBlock(msg.reasoning, msg.content, stripNovelTags = mode == ConversationMode.novel)
    }

    when (mode) {
        ConversationMode.rp -> {
            val leadNpc = rpConfig?.cast?.firstOrNull { !it.isPlayer }
            val shape = RoundedCornerShape(18.dp)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = c.surface,
                border = BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(c.primarySoft.copy(alpha = 0.32f), c.surface),
                            ),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val speakerName = leadNpc?.name ?: rpConfig?.title ?: strings.rp.badge
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c.tag)
                                .border(1.dp, c.accent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = speakerName.firstOrNull()?.toString() ?: "✦",
                                color = c.tagInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = speakerName,
                                color = c.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (leadNpc != null) {
                                Text(
                                    text = "${leadNpc.mood} · ❤️${leadNpc.affinity}",
                                    color = c.muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (msg.variants.size > 1) {
                            VariantSwipePill(
                                index = msg.activeVariantIndex,
                                total = msg.variants.size,
                                onSwitch = onSwitchVariant,
                            )
                        }
                    }

                    AssistantBodyAndNotices(
                        parsedReasoning = parsedReasoning,
                        cleanBody = cleanBody,
                        msg = msg,
                        useSerif = false,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        onToast = onToast,
                    )

                    if (isLatestAssistant && msg.rpChoices.isNotEmpty() && !isStreaming) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            msg.rpChoices.forEachIndexed { idx, choice ->
                                val badgeLabel = choice.skillCheck?.let { "🎲 $it DC${choice.difficulty ?: 12}" }
                                DramaticChoiceCard(
                                    index = idx + 1,
                                    label = choice.label,
                                    metaBadge = badgeLabel,
                                    onClick = { onSelectRpChoice(choice) },
                                )
                            }
                        }
                    }

                    AssistantFooterRow(
                        msg = msg,
                        showVariantBar = false,
                        isStreaming = isStreaming,
                        onCopy = { onCopyText(cleanBody) },
                        onRegenerate = onRegenerate,
                        onBranch = onBranch,
                        onDelete = onDelete,
                        onSwitchVariant = onSwitchVariant,
                    )
                }
            }
        }

        ConversationMode.novel -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.accent.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✦",
                            color = c.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = c.border)
                        if (!msg.model.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(msg.model, color = c.muted, fontSize = 10.sp)
                        }
                    }

                    AssistantBodyAndNotices(
                        parsedReasoning = parsedReasoning,
                        cleanBody = cleanBody,
                        msg = msg,
                        useSerif = true,
                        fontSize = 15.5.sp,
                        lineHeight = 26.sp,
                        onToast = onToast,
                    )

                    if (isLatestAssistant && msg.novelChoices.isNotEmpty() && !isStreaming) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    AssistantFooterRow(
                        msg = msg,
                        showVariantBar = msg.variants.size > 1,
                        isStreaming = isStreaming,
                        onCopy = { onCopyText(cleanBody) },
                        onRegenerate = onRegenerate,
                        onBranch = onBranch,
                        onDelete = onDelete,
                        onSwitchVariant = onSwitchVariant,
                    )
                }
            }
        }

        ConversationMode.chat -> {
            // DeepSeek-inspired borderless editorial canvas
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(1.dp, c.border, CircleShape),
                    )
                    Text(
                        text = msg.model?.takeIf { it.isNotBlank() } ?: strings.common.appName,
                        color = c.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                AssistantBodyAndNotices(
                    parsedReasoning = parsedReasoning,
                    cleanBody = cleanBody,
                    msg = msg,
                    useSerif = false,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    onToast = onToast,
                )

                AssistantFooterRow(
                    msg = msg,
                    showVariantBar = msg.variants.size > 1,
                    isStreaming = isStreaming,
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

@Composable
private fun AssistantBodyAndNotices(
    parsedReasoning: String,
    cleanBody: String,
    msg: ChatMessage,
    useSerif: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    onToast: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    if (parsedReasoning.isNotBlank() || msg.status == "streaming") {
        ReasoningCard(
            reasoning = parsedReasoning,
            isStreaming = msg.status == "streaming" && cleanBody.isEmpty(),
        )
    }

    if (cleanBody.isNotBlank()) {
        MarkdownMessageText(
            markdown = cleanBody,
            fontSize = fontSize,
            lineHeight = lineHeight,
            useSerif = useSerif,
            onCopyToast = onToast,
        )
    } else if (msg.status == "streaming") {
        Text(strings.chat.streamingWriting, color = c.muted, fontSize = 13.sp)
    }

    if (!msg.error.isNullOrBlank()) {
        GuBadge(msg.error, variant = GuBadgeVariant.DANGER)
    }
    if (msg.status == "aborted") {
        Text(strings.chat.stoppedNote, color = c.muted, fontSize = 11.sp)
    }
}

@Composable
private fun DramaticChoiceCard(
    index: Int,
    label: String,
    metaBadge: String?,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceSoft,
        border = BorderStroke(1.dp, c.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(c.primarySoft.copy(alpha = 0.65f), c.surfaceSoft)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$index",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                text = label,
                color = c.ink,
                fontSize = 13.sp,
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
            .clip(RoundedCornerShape(999.dp))
            .background(c.surfaceSoft)
            .border(1.dp, c.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onSwitch((index - 1).coerceAtLeast(0)) },
            enabled = index > 0,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = c.ink, modifier = Modifier.size(15.dp))
        }
        Text(
            text = "${index + 1}/$total",
            color = c.ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(
            onClick = { onSwitch((index + 1).coerceAtMost(total - 1)) },
            enabled = index < total - 1,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.ink, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun AssistantFooterRow(
    msg: ChatMessage,
    showVariantBar: Boolean,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
    onSwitchVariant: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = strings.common.copy, tint = c.muted, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onRegenerate, enabled = !isStreaming, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = strings.chat.regenerate, tint = c.muted, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onBranch, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.AccountTree, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onDelete, enabled = !isStreaming, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.muted, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.weight(1f))
        if (showVariantBar && msg.variants.size > 1) {
            VariantSwipePill(
                index = msg.activeVariantIndex,
                total = msg.variants.size,
                onSwitch = onSwitchVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        msg.usage?.totalTokens?.let { tok ->
            GuBadge("${tok}t", variant = GuBadgeVariant.NEUTRAL)
        }
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
