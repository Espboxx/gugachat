package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.R
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ConversationMode
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatSessionsDrawerContent(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewSession: () -> Unit,
    onImportDeepSeek: () -> Unit,
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
        val byMode = if (filterMode == null) {
            conversations
        } else {
            conversations.filter { it.mode == filterMode }
        }
        FuzzyMatcher.filterAndRank(byMode, search) { conv ->
            listOf(conv.title, conv.model.orEmpty())
        }
    }

    val sections = remember(filtered, strings) {
        groupConversationsByTime(
            items = filtered,
            pinnedLabel = strings.chat.pinnedGroup,
            todayLabel = strings.chat.today,
            yesterdayLabel = strings.chat.yesterday,
            earlierLabel = strings.chat.earlier,
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = c.page,
        modifier = Modifier.width(312.dp).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_logo),
                    contentDescription = strings.common.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
                Text(
                    text = strings.chat.sessionsTitle,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                GuBadge("${conversations.size}", variant = GuBadgeVariant.NEUTRAL)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuButton(
                    onClick = { onNewSession(); onCloseDrawer() },
                    compact = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.chat.newChat, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                GuButton(
                    onClick = { onImportDeepSeek(); onCloseDrawer() },
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = strings.chat.importExternalTitle, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.chat.importDeepSeekShort, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            GuCompactSearchBar(
                value = search,
                onValueChange = { search = it },
                placeholder = strings.chat.searchSessions,
                clearContentDescription = strings.common.clear,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuPill(strings.novel.filterAll, filterMode == null, { filterMode = null })
                GuPill(strings.novel.filterChat, filterMode == ConversationMode.chat, { filterMode = ConversationMode.chat })
                GuPill(strings.novel.filterNovel, filterMode == ConversationMode.novel, { filterMode = ConversationMode.novel })
                GuPill(strings.rp.filterRp, filterMode == ConversationMode.rp, { filterMode = ConversationMode.rp })
            }

            HorizontalDivider(color = c.border.copy(alpha = 0.7f))

            if (filtered.isEmpty()) {
                GuEmptyState(
                    title = if (conversations.isEmpty()) strings.chat.emptySessions else strings.chat.noMatchingSessions,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sections.forEach { (sectionTitle, sectionItems) ->
                        item(key = "sec_$sectionTitle") {
                            Text(
                                text = sectionTitle,
                                color = c.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(sectionItems, key = { it.id }) { conv ->
                            val selected = conv.id == activeConversationId
                            val (modeIcon, modeTint) = when (conv.mode) {
                                ConversationMode.novel -> Icons.Default.AutoStories to c.accent
                                ConversationMode.rp -> Icons.Default.Casino to c.success
                                ConversationMode.chat -> Icons.AutoMirrored.Filled.Chat to c.muted
                            }
                            var rowMenuOpen by remember(conv.id) { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.primarySoft else Color.Transparent)
                                    .clickable {
                                        onSelectConversation(conv.id)
                                        onCloseDrawer()
                                    }
                                    .padding(start = 8.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.primary),
                                    )
                                }
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (selected) c.primarySoftInk else modeTint,
                                    modifier = Modifier.size(17.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = conv.title.ifBlank { strings.chat.untitled },
                                            color = if (selected) c.primarySoftInk else c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (conv.pinned) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = null,
                                                tint = c.accent,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                    if (!conv.model.isNullOrBlank()) {
                                        Text(
                                            text = conv.model,
                                            color = c.muted,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { rowMenuOpen = true },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = strings.chat.moreActions,
                                            tint = c.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    GuPopupMenu(
                                        expanded = rowMenuOpen,
                                        onDismissRequest = { rowMenuOpen = false },
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.PushPin,
                                                    contentDescription = null,
                                                    tint = if (conv.pinned) c.accent else c.muted,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            },
                                            text = {
                                                Text(
                                                    text = if (conv.pinned) strings.chat.ledgerUnpin else strings.chat.ledgerPin,
                                                    color = c.ink,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                onTogglePin(conv.id)
                                            },
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = c.danger,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            },
                                            text = {
                                                Text(
                                                    text = strings.common.delete,
                                                    color = c.danger,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                deleteTarget = conv
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupConversationsByTime(
    items: List<ChatConversation>,
    pinnedLabel: String,
    todayLabel: String,
    yesterdayLabel: String,
    earlierLabel: String,
): List<Pair<String, List<ChatConversation>>> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStartMs = cal.timeInMillis
    val yesterdayStartMs = todayStartMs - 86_400_000L

    val pinned = items.filter { it.pinned }
    val unpinned = items.filterNot { it.pinned }
    val today = unpinned.filter { it.updatedAt >= todayStartMs }
    val yesterday = unpinned.filter { it.updatedAt in yesterdayStartMs until todayStartMs }
    val earlier = unpinned.filter { it.updatedAt < yesterdayStartMs }

    return buildList {
        if (pinned.isNotEmpty()) add(pinnedLabel to pinned)
        if (today.isNotEmpty()) add(todayLabel to today)
        if (yesterday.isNotEmpty()) add(yesterdayLabel to yesterday)
        if (earlier.isNotEmpty()) add(earlierLabel to earlier)
    }
}
