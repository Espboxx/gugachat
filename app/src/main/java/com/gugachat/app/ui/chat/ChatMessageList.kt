package com.gugachat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
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

    val lastMsg = active.lastOrNull()
    LaunchedEffect(active.size, lastMsg?.content?.length, lastMsg?.reasoning?.length) {
        if (active.isEmpty()) return@LaunchedEffect
        val bottomIndex = active.size + 1
        if (isStreaming) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: bottomIndex
            if (lastVisible >= bottomIndex - 2) {
                listState.scrollToItem(bottomIndex)
            }
        } else {
            listState.scrollToItem(bottomIndex)
        }
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

    val showScrollToBottom by remember(active.size) {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 2 && lastVisible < totalItems - 2
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Spacer(Modifier.height(2.dp)) }

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
                        onDelete = { deleteTarget = msg },
                    )
                } else {
                    val chapterStart = chapterStartMsgMap[msg.id]
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (chapterStart != null) {
                            NovelChapterDividerRibbon(chapter = chapterStart)
                        }
                        AssistantMessageBlock(
                            mode = mode,
                            msg = msg,
                            rpConfig = rpConfig,
                            novelConfig = novelConfig,
                            isLatestAssistant = isLatestAssistant,
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
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("reply", body))
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
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            Surface(
                onClick = {
                    scope.launch {
                        val bottomIndex = (active.size + 1).coerceAtLeast(0)
                        listState.animateScrollToItem(bottomIndex)
                    }
                },
                shape = CircleShape,
                color = c.surfaceElevated,
                border = BorderStroke(theme.borderWidth, c.outline.copy(alpha = 0.8f)),
                shadowElevation = if (theme.isDark) 6.dp else 4.dp,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
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
                Surface(
                    onClick = onOpenModelSheet,
                    shape = RoundedCornerShape(999.dp),
                    color = c.surface,
                    border = BorderStroke(theme.borderWidth, c.border),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = strings.chat.selectModel,
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (mode == ConversationMode.rp && rpConfig != null) {
                GuGroupedCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = c.primary.copy(alpha = 0.35f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.primarySoft.copy(alpha = if (theme.isDark) 0.9f else 0.85f),
                                        c.accentSoft.copy(alpha = if (theme.isDark) 0.55f else 0.7f),
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
                                backgroundColor = c.surface,
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
                                    fontWeight = FontWeight.ExtraBold,
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

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.7f))

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
                                            .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
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
                    borderColor = c.accent.copy(alpha = 0.4f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        c.accentSoft,
                                        c.tag.copy(alpha = if (theme.isDark) 0.5f else 0.65f),
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
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (novelConfig.genre.isNotBlank()) {
                                GuBadge(novelConfig.genre, variant = GuBadgeVariant.HONEY)
                            }
                        }
                    }

                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.7f))

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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = c.surfaceSoft,
                                border = BorderStroke(theme.borderWidth, c.border),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = firstCh.title,
                                        color = c.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
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
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.primarySoft)
                .border(theme.borderWidth, c.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
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

        if (!hasModelSelected) {
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(999.dp),
                color = c.surface,
                border = BorderStroke(theme.borderWidth, c.border),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        } else if (selectedModelLabel.isNotBlank()) {
            val memLabel = if (memoryEnabled) strings.chat.memoryOn else strings.chat.memoryOff
            val metaLine = buildString {
                if (selectedGroupLabel.isNotBlank()) append("$selectedGroupLabel · ")
                append("${strings.chat.memoryShort}$memLabel")
            }
            Surface(
                onClick = onOpenModelSheet,
                shape = RoundedCornerShape(999.dp),
                color = c.surfaceSoft,
                border = BorderStroke(theme.borderWidth, c.border),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.primary),
                    )
                    Text(
                        text = metaLine,
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (mode == ConversationMode.chat) {
            Spacer(Modifier.height(4.dp))
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                starters.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { spec ->
                            StarterGridCard(
                                spec = spec,
                                onClick = { onStarterPrompt(spec.prompt) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
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
        GuIconTile(
            icon = spec.icon,
            tint = spec.tint,
            backgroundColor = spec.bg,
            size = 36.dp,
            iconSize = 18.dp,
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = spec.title,
            color = c.ink,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = spec.prompt,
            color = c.muted,
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp,
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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val bubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val bubbleBg = if (theme.isDark) c.surfaceSoft else c.primarySoft
    val bubbleBorder = if (theme.isDark) c.primary.copy(alpha = 0.34f) else c.primary.copy(alpha = 0.22f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 304.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(theme.borderWidth, bubbleBorder, bubbleShape)
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    msg.attachmentIds.forEach { id ->
                        Image(
                            painter = rememberAsyncImagePainter(attachmentPathResolver(id)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(12.dp)),
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
                SelectionContainer {
                    Text(
                        text = msg.content,
                        color = c.ink,
                        fontSize = 15.sp,
                        lineHeight = 22.5.sp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onEdit,
                enabled = !isStreaming,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.chat.editResend,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onBranch,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = strings.novel.branchBtn,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = !isStreaming,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.common.delete,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
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

    when (mode) {
        ConversationMode.rp -> {
            val (speakerName, speakerNpc) = remember(msg.id, msg.activeVariantIndex, rpConfig?.cast, rpConfig?.title) {
                resolveRpSpeaker(msg, rpConfig, strings.rp.badge)
            }
            val activeVar = msg.activeVariant()
            val deltas = activeVar?.rpDeltas.orEmpty()
            val unlockedEnding = activeVar?.rpEndingUnlocked
            val affinityTier = speakerNpc?.let { RpEngine.affinityTier(it.affinity) }

            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        RpTurnDeltaStrip(deltas = deltas)
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
                            onDarkStage = false,
                            enabled = !isStreaming,
                        )
                    }

                    if (!isThisMsgStreaming) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            showContinue = isLatestAssistant && !isStreaming,
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

        ConversationMode.novel -> {
            val passageWordCount = remember(cleanBody) { NovelExport.countProseWords(cleanBody) }
            val syncingMeta = isThisMsgStreaming && NovelExport.isStreamingInternalMeta(msg.content)
            val showNovelChoicesDeck = isLatestAssistant && !isStreaming && (
                msg.novelChoices.isNotEmpty() ||
                    isRefreshingChoices ||
                    (novelConfig?.enableChoices == true && novelConfig.playStyle != "author")
                )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(theme.cardRadius),
                color = c.surface,
                border = BorderStroke(theme.borderWidth, c.border.copy(alpha = 0.8f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✦",
                            color = c.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (passageWordCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isZh) "$passageWordCount 字" else "${passageWordCount}w",
                                color = c.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.7f),
                        )
                        if (!msg.model.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = msg.model,
                                color = c.muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        lineHeight = 27.sp,
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
                                .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp))
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
                                    border = BorderStroke(theme.borderWidth, c.border),
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

                    if (!isThisMsgStreaming) {
                        AssistantFooterRow(
                            msg = msg,
                            showVariantBar = msg.variants.size > 1,
                            isStreaming = isStreaming,
                            showContinue = isLatestAssistant && !isStreaming,
                            continueLabel = strings.novel.continueBtn,
                            onContinue = onContinueNovel,
                            showPolish = isLatestAssistant && !isStreaming,
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
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
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
                            .border(theme.borderWidth, c.border, CircleShape),
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
                }

                AssistantBodyAndNotices(
                    parsedReasoning = parsedReasoning,
                    cleanBody = cleanBody,
                    msg = msg,
                    isStreaming = isStreaming,
                    streamingStatusHint = streamingStatusHint,
                    useSerif = false,
                    fontSize = 15.5.sp,
                    lineHeight = 25.sp,
                    onRetry = onRegenerate,
                    onToast = onToast,
                )

                if (!isThisMsgStreaming) {
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
    val theme = LocalAppTheme.current
    val c = theme.colors
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surfaceSoft)
            .border(theme.borderWidth, c.border, RoundedCornerShape(999.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 36.dp)
                .clip(CircleShape)
                .clickable(enabled = index > 0) { onSwitch((index - 1).coerceAtLeast(0)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = if (index > 0) c.ink else c.muted.copy(alpha = 0.38f),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = "${index + 1}/$total",
            color = c.ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 36.dp)
                .clip(CircleShape)
                .clickable(enabled = index < total - 1) { onSwitch((index + 1).coerceAtMost(total - 1)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (index < total - 1) c.ink else c.muted.copy(alpha = 0.38f),
                modifier = Modifier.size(16.dp),
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
                        border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.4f)),
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
                                fontWeight = FontWeight.Bold,
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
                        border = BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.42f)),
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
                                fontWeight = FontWeight.Bold,
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
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = strings.common.copy,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onEdit, enabled = !isStreaming, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.common.edit,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onRegenerate, enabled = !isStreaming, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = strings.chat.regenerate,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onBranch, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = strings.novel.branchBtn,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, enabled = !isStreaming, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.common.delete,
                    tint = c.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (showVariantBar && msg.variants.size > 1) {
                VariantSwipePill(
                    index = msg.activeVariantIndex,
                    total = msg.variants.size,
                    onSwitch = onSwitchVariant,
                )
            }
            msg.usage?.totalTokens?.let { tok ->
                Spacer(Modifier.width(6.dp))
                GuBadge("${tok}t", variant = GuBadgeVariant.NEUTRAL)
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
