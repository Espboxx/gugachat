package com.gugachat.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ConversationMode
import java.util.Calendar

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
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var search by remember { mutableStateOf("") }
    var filterIndex by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }

    val filterMode: ConversationMode? = when (filterIndex) {
        1 -> ConversationMode.chat
        2 -> ConversationMode.novel
        3 -> ConversationMode.rp
        else -> null
    }
    val filterLabels = remember(strings) {
        listOf(
            strings.novel.filterAll,
            strings.novel.filterChat,
            strings.novel.filterNovel,
            strings.rp.filterRp,
        )
    }

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

    val last7DaysLabel = if (isZh) "近7天" else "Last 7 Days"
    val sections = remember(filtered, strings, last7DaysLabel) {
        groupConversationsByTime(
            items = filtered,
            pinnedLabel = strings.chat.pinnedGroup,
            todayLabel = strings.chat.today,
            yesterdayLabel = strings.chat.yesterday,
            last7DaysLabel = last7DaysLabel,
            earlierLabel = strings.chat.earlier,
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = c.page,
        modifier = Modifier
            .width(308.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Clean DeepSeek brand header + compact New pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_logo),
                        contentDescription = strings.common.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .border(theme.borderWidth, c.border, RoundedCornerShape(9.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.chat.sessionsTitle,
                            color = c.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GuButton(
                        onClick = {
                            onNewSession()
                            onCloseDrawer()
                        },
                        compact = true,
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = strings.chat.newChat,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                GuCompactSearchBar(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = strings.chat.searchSessions,
                    clearContentDescription = strings.common.clear,
                )

                GuSegmentedControl(
                    items = filterLabels,
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.75f),
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GuEmptyState(
                        title = if (conversations.isEmpty()) strings.chat.emptySessions else strings.chat.noMatchingSessions,
                        icon = Icons.AutoMirrored.Filled.Chat,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sections.forEach { (sectionTitle, sectionItems) ->
                        item(key = "sec_$sectionTitle") {
                            GuSectionHeader(
                                title = sectionTitle,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
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
                                    .heightIn(min = 46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.primarySoft else Color.Transparent)
                                    .clickable {
                                        onSelectConversation(conv.id)
                                        onCloseDrawer()
                                    }
                                    .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(c.primary),
                                    )
                                }
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (selected) c.primarySoftInk else modeTint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = conv.title.ifBlank { strings.chat.untitled },
                                            color = if (selected) c.primarySoftInk else c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
                                        modifier = Modifier.size(38.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = strings.chat.moreActions,
                                            tint = c.muted,
                                            modifier = Modifier.size(17.dp),
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

            // Pinned bottom footer bar with Import External Conversations row
            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.8f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .clickable {
                        onImportDeepSeek()
                        onCloseDrawer()
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.FolderZip,
                    tint = c.accent,
                    backgroundColor = c.accentSoft,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.chat.importExternalTitle,
                        color = c.ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.muted.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun groupConversationsByTime(
    items: List<ChatConversation>,
    pinnedLabel: String,
    todayLabel: String,
    yesterdayLabel: String,
    last7DaysLabel: String,
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
    val last7DaysStartMs = todayStartMs - 7L * 86_400_000L

    val pinned = items.filter { it.pinned }
    val unpinned = items.filterNot { it.pinned }
    val today = unpinned.filter { it.updatedAt >= todayStartMs }
    val yesterday = unpinned.filter { it.updatedAt in yesterdayStartMs until todayStartMs }
    val last7Days = unpinned.filter { it.updatedAt in last7DaysStartMs until yesterdayStartMs }
    val earlier = unpinned.filter { it.updatedAt < last7DaysStartMs }

    return buildList {
        if (pinned.isNotEmpty()) add(pinnedLabel to pinned)
        if (today.isNotEmpty()) add(todayLabel to today)
        if (yesterday.isNotEmpty()) add(yesterdayLabel to yesterday)
        if (last7Days.isNotEmpty()) add(last7DaysLabel to last7Days)
        if (earlier.isNotEmpty()) add(earlierLabel to earlier)
    }
}
