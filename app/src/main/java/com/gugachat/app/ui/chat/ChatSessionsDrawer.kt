package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
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
                .padding(14.dp),
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

            GuButton(
                onClick = { onNewSession(); onCloseDrawer() },
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.chat.newChat, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    sections.forEach { (sectionTitle, sectionItems) ->
                        item(key = "sec_$sectionTitle") {
                            Text(
                                text = sectionTitle,
                                color = c.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(sectionItems, key = { it.id }) { conv ->
                            val selected = conv.id == activeConversationId
                            val (modeIcon, modeTint) = when (conv.mode) {
                                ConversationMode.novel -> Icons.Default.AutoStories to c.accent
                                ConversationMode.rp -> Icons.Default.Casino to c.primary
                                ConversationMode.chat -> Icons.AutoMirrored.Filled.Chat to c.muted
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.primarySoft else c.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) c.primary.copy(alpha = 0.55f) else c.border,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        onSelectConversation(conv.id)
                                        onCloseDrawer()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
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
                                    tint = if (selected) c.primary else modeTint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conv.title.ifBlank { strings.chat.untitled },
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!conv.model.isNullOrBlank()) {
                                        Text(
                                            text = conv.model,
                                            color = c.muted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onTogglePin(conv.id) },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = if (conv.pinned) c.accent else c.muted.copy(alpha = 0.45f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { deleteTarget = conv },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = strings.common.delete,
                                        tint = c.muted,
                                        modifier = Modifier.size(14.dp),
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
