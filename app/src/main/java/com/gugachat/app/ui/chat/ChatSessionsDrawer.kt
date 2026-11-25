package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ConversationMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatSessionsDrawerContent(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenNovelWizard: () -> Unit,
    onOpenRpWizard: () -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf<ConversationMode?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }

    if (deleteTarget != null) {
        GuConfirmDialog(
            title = strings.common.delete,
            message = strings.chat.deleteConfirm,
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onDeleteConversation(deleteTarget!!.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    val filtered = remember(conversations, search, filterMode) {
        val q = search.trim().lowercase()
        conversations.filter { conv ->
            (filterMode == null || conv.mode == filterMode) &&
                (q.isEmpty() || conv.title.lowercase().contains(q))
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = c.page,
        modifier = Modifier.width(308.dp).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings.chat.sessionsTitle,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GuButton(
                    onClick = { onNewChat(); onCloseDrawer() },
                    compact = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(strings.chat.newChat, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GuButton(
                    onClick = { onOpenNovelWizard(); onCloseDrawer() },
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                ) {
                    Icon(Icons.Default.AutoStories, contentDescription = strings.novel.writeNovel, modifier = Modifier.size(15.dp))
                }
                GuButton(
                    onClick = { onOpenRpWizard(); onCloseDrawer() },
                    compact = true,
                    variant = GuButtonVariant.SECONDARY,
                ) {
                    Icon(Icons.Default.Casino, contentDescription = strings.rp.newBtn, modifier = Modifier.size(15.dp))
                }
            }

            GuTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = strings.chat.searchSessions,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted) },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GuPill(strings.novel.filterAll, filterMode == null, { filterMode = null })
                GuPill(strings.novel.filterChat, filterMode == ConversationMode.chat, { filterMode = ConversationMode.chat })
                GuPill(strings.novel.filterNovel, filterMode == ConversationMode.novel, { filterMode = ConversationMode.novel })
                GuPill(strings.rp.filterRp, filterMode == ConversationMode.rp, { filterMode = ConversationMode.rp })
            }

            HorizontalDivider(color = c.border)

            if (filtered.isEmpty()) {
                GuEmptyState(
                    title = if (conversations.isEmpty()) strings.chat.emptySessions else strings.chat.noMatchingSessions,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val selected = conv.id == activeConversationId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) c.primarySoft else c.surface)
                                .clickable {
                                    onSelectConversation(conv.id)
                                    onCloseDrawer()
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (conv.mode == ConversationMode.novel) {
                                        GuBadge(strings.novel.badge, variant = GuBadgeVariant.HONEY)
                                    } else if (conv.mode == ConversationMode.rp) {
                                        GuBadge(strings.rp.badge, variant = GuBadgeVariant.BLUE)
                                    }
                                    Text(
                                        text = conv.title.ifBlank { strings.chat.untitled },
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onTogglePin(conv.id) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (conv.pinned) c.accent else c.muted.copy(alpha = 0.45f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { deleteTarget = conv },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = strings.common.delete,
                                    tint = c.muted,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
