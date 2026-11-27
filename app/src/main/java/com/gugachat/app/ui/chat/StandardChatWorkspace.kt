package com.gugachat.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.RpInputKind
import java.io.File

@Composable
fun StandardChatWorkspace(
    messages: List<ChatMessage>,
    waterline: ContextWaterlineSummary,
    selectedModelId: String?,
    activeModelFreeQuota: Boolean,
    selectedGroupLabel: String,
    memoryEnabled: Boolean,
    enableReasoning: Boolean,
    supportsVision: Boolean,
    draftText: String,
    draftAttachments: List<ChatAttachmentMeta>,
    attachmentsById: Map<String, ChatAttachmentMeta> = emptyMap(),
    isImportingAttachment: Boolean = false,
    isStreaming: Boolean,
    streamingStatusHint: String?,
    attachmentPathResolver: (String) -> File,
    onOpenDrawer: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onNewSession: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    moreMenuContent: @Composable () -> Unit,
    onStarterPrompt: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onEditAssistantMessage: (String, String) -> Unit,
    onBranchFromMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSwitchVariant: (String, Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleMemory: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit,
    onUpdateAttachmentInjectMode: (String, FileInjectMode) -> Unit = { _, _ -> },
    onReadAttachmentText: (suspend (String) -> String?)? = null,
    onOpenRecallInspector: () -> Unit,
    onOpenContextSettings: () -> Unit = onOpenRecallInspector,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToast: (String) -> Unit,
    conversationTitle: String = "",
    onScrollHideBottomBarChanged: (Boolean) -> Unit = {},
    onOpenRpWizard: () -> Unit = {},
    onOpenNovelWizard: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    var canScrollBackward by remember { mutableStateOf(false) }
    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) canScrollBackward = false
    }
    val headerDividerColor by animateColorAsState(
        targetValue = if (canScrollBackward && messages.isNotEmpty()) {
            c.border.copy(alpha = 0.72f)
        } else {
            Color.Transparent
        },
        label = "std_chat_header_divider",
    )
    val composerFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        GuAwningHeader(dividerColor = headerDividerColor) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = strings.chat.sessions,
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }

                val hasModel = !selectedModelId.isNullOrBlank()
                val dotColor = when {
                    activeModelFreeQuota -> c.success
                    hasModel -> c.primary
                    else -> c.muted.copy(alpha = 0.55f)
                }
                val showWaterlineHint = waterline.sentPercentage >= 80 || waterline.isSliding
                val waterlineColor = if (waterline.hardOverflow) c.danger else c.warning

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenModelSheet)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = conversationTitle.ifBlank { strings.chat.untitled },
                        color = c.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Text(
                            text = selectedModelId?.takeIf { it.isNotBlank() } ?: strings.chat.selectModel,
                            color = if (hasModel) c.muted else c.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showWaterlineHint) {
                            Text(
                                text = "· ${waterline.sentPercentage}%",
                                color = waterlineColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(13.5.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onNewSession,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = strings.chat.newChat,
                            tint = c.ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Box {
                        IconButton(
                            onClick = onOpenMoreMenu,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = strings.chat.moreActions,
                                tint = c.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        moreMenuContent()
                    }
                }
            }
        }

        ChatMessageList(
            mode = ConversationMode.chat,
            messages = messages,
            waterline = waterline,
            hasModelSelected = !selectedModelId.isNullOrBlank(),
            selectedModelLabel = selectedModelId.orEmpty(),
            selectedGroupLabel = selectedGroupLabel,
            memoryEnabled = memoryEnabled,
            isStreaming = isStreaming,
            streamingStatusHint = streamingStatusHint,
            attachmentPathResolver = attachmentPathResolver,
            attachmentsById = attachmentsById,
            onReadAttachmentText = onReadAttachmentText,
            onOpenModelSheet = onOpenModelSheet,
            onStarterPrompt = onStarterPrompt,
            onSelectNovelChoice = {},
            onSelectRpChoice = { _, _ -> },
            onRegenerate = onRegenerate,
            onEditUserMessage = onEditUserMessage,
            onEditAssistantMessage = onEditAssistantMessage,
            onBranchFromMessage = onBranchFromMessage,
            onDeleteMessage = onDeleteMessage,
            onSwitchVariant = onSwitchVariant,
            onToast = onToast,
            modifier = Modifier.weight(1f),
            onScrollHideBottomBarChanged = onScrollHideBottomBarChanged,
            onCanScrollBackwardChanged = { canScrollBackward = it },
            onOpenRpWizard = onOpenRpWizard,
            onOpenNovelWizard = onOpenNovelWizard,
            onStartNormalChat = { runCatching { composerFocusRequester.requestFocus() } },
        )

        ChatComposerBar(
            mode = ConversationMode.chat,
            draftText = draftText,
            draftAttachments = draftAttachments,
            draftNovelKind = NovelInputKind.director,
            draftRpKind = RpInputKind.action,
            draftDiceRoll = null,
            waterline = waterline,
            enableReasoning = enableReasoning,
            enableAutoMemory = memoryEnabled,
            isStreaming = isStreaming,
            supportsVision = supportsVision,
            isImportingAttachment = isImportingAttachment,
            onDraftChange = onDraftChange,
            onSelectNovelKind = {},
            onSelectRpKind = {},
            onToggleReasoning = onToggleReasoning,
            onToggleMemory = onToggleMemory,
            onPickImage = onPickImage,
            onTakePhoto = onTakePhoto,
            onPickFile = onPickFile,
            onRemoveAttachment = onRemoveAttachment,
            onUpdateAttachmentInjectMode = onUpdateAttachmentInjectMode,
            onReadAttachmentText = onReadAttachmentText,
            onOpenModelSheet = onOpenModelSheet,
            onToast = onToast,
            onOpenDiceDialog = {},
            onClearDiceRoll = {},
            onOpenRecallInspector = onOpenRecallInspector,
            onOpenContextSettings = onOpenContextSettings,
            onSend = onSend,
            onStop = onStop,
            inputFocusRequester = composerFocusRequester,
        )
    }
}
