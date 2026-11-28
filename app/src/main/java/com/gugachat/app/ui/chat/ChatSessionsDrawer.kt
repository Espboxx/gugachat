package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ConversationMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatSessionsDrawerContent(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewSession: () -> Unit,
    onImportDeepSeek: () -> Unit,
    onTogglePin: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    onSwitchMainTab: (Int) -> Unit = {},
    streamingConversationIds: Set<String> = emptySet(),
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val activeStreamingIds = streamingConversationIds
    val streamPulseAlpha = if (activeStreamingIds.isNotEmpty()) rememberDrawerStreamPulseAlphaState() else null

    var search by remember { mutableStateOf("") }
    var filterIndex by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var renamingConvId by remember { mutableStateOf<String?>(null) }
    var renameBuffer by remember { mutableStateOf("") }

    BackHandler(enabled = renamingConvId != null) {
        renamingConvId = null
    }

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
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.chat.sessionsTitle,
                            color = c.ink,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GuButton(
                        onClick = {
                            renamingConvId = null
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
                            fontWeight = FontWeight.SemiBold,
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
                color = c.border.copy(alpha = 0.65f),
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
                            val isRenaming = renamingConvId == conv.id
                            val (modeIcon, modeTint) = when (conv.mode) {
                                ConversationMode.novel -> Icons.Default.AutoStories to c.accent
                                ConversationMode.rp -> Icons.Default.Casino to c.success
                                ConversationMode.chat -> Icons.AutoMirrored.Filled.Chat to c.primary
                            }
                            var rowMenuOpen by remember(conv.id) { mutableStateOf(false) }

                            if (isRenaming) {
                                val focusRequester = remember(conv.id) { FocusRequester() }
                                LaunchedEffect(conv.id) {
                                    runCatching { focusRequester.requestFocus() }
                                }
                                val commitRename = {
                                    val trimmed = renameBuffer.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onRenameConversation(conv.id, trimmed)
                                    }
                                    renamingConvId = null
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(c.surfaceSoft)
                                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(modeTint),
                                    )
                                    BasicTextField(
                                        value = renameBuffer,
                                        onValueChange = { renameBuffer = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        cursorBrush = SolidColor(c.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { commitRename() }),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(c.surface)
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                if (renameBuffer.isEmpty()) {
                                                    Text(
                                                        text = strings.chat.untitled,
                                                        color = c.muted,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        },
                                    )
                                    IconButton(
                                        onClick = commitRename,
                                        enabled = renameBuffer.isNotBlank(),
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = strings.common.save,
                                            tint = if (renameBuffer.isNotBlank()) c.primary else c.muted.copy(alpha = 0.4f),
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { renamingConvId = null },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = strings.common.cancel,
                                            tint = c.muted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                val relTime = remember(conv.updatedAt, isZh) {
                                    formatSessionTimeShort(
                                        updatedAtMs = conv.updatedAt,
                                        isZh = isZh,
                                        yesterdayLabel = strings.chat.yesterday,
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) c.primarySoft else Color.Transparent)
                                        .clickable {
                                            renamingConvId = null
                                            onSelectConversation(conv.id)
                                            onCloseDrawer()
                                        }
                                        .padding(start = 8.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(c.primary),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(modeTint.copy(alpha = if (selected) 0.16f else 0.11f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            tint = if (selected) c.primarySoftInk else modeTint,
                                            modifier = Modifier.size(12.5.dp),
                                        )
                                    }
                                    Text(
                                        text = conv.title.ifBlank { strings.chat.untitled },
                                        color = if (selected) c.primarySoftInk else c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (conv.id in activeStreamingIds) {
                                        StreamingPulseDot(
                                            color = c.primary,
                                            alphaState = streamPulseAlpha,
                                        )
                                    }
                                    if (conv.pinned) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = c.accent,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                    GuMetaText(
                                        text = relTime,
                                        color = if (selected) c.primarySoftInk.copy(alpha = 0.78f) else c.muted.copy(alpha = 0.75f),
                                    )
                                    Box {
                                        IconButton(
                                            onClick = { rowMenuOpen = true },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = strings.chat.moreActions,
                                                tint = c.muted,
                                                modifier = Modifier.size(16.dp),
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
                                                        Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = c.muted,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                },
                                                text = {
                                                    Text(
                                                        text = strings.common.rename,
                                                        color = c.ink,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                },
                                                onClick = {
                                                    rowMenuOpen = false
                                                    renameBuffer = conv.title
                                                    renamingConvId = conv.id
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

            // Pinned bottom footer: quick cross-tab anchors + external import entry
            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.7f),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawerQuickTabAnchor(
                        icon = Icons.Default.AutoAwesome,
                        label = strings.nav.tabStudio,
                        tint = c.primarySoftInk,
                        backgroundColor = c.primarySoft,
                        onClick = {
                            renamingConvId = null
                            onCloseDrawer()
                            onSwitchMainTab(1)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DrawerQuickTabAnchor(
                        icon = Icons.Default.Person,
                        label = strings.nav.tabMine,
                        tint = c.ink,
                        backgroundColor = c.surfaceSoft,
                        onClick = {
                            renamingConvId = null
                            onCloseDrawer()
                            onSwitchMainTab(2)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.55f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            renamingConvId = null
                            onImportDeepSeek()
                            onCloseDrawer()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuIconTile(
                        icon = Icons.Default.FolderZip,
                        tint = c.accent,
                        backgroundColor = c.accentSoft,
                        size = 30.dp,
                        iconSize = 16.dp,
                        cornerRadius = 8.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.chat.importExternalTitle,
                            color = c.ink,
                            fontSize = 13.sp,
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
}

@Composable
private fun DrawerQuickTabAnchor(
    icon: ImageVector,
    label: String,
    tint: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberDrawerStreamPulseAlphaState(): State<Float> {
    val transition = rememberInfiniteTransition(label = "drawer_stream_pulse")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drawer_stream_pulse_alpha",
    )
}

@Composable
private fun StreamingPulseDot(
    color: Color,
    alphaState: State<Float>?,
) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.18f * (alphaState?.value ?: 1f)))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = alphaState?.value ?: 1f }
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun formatSessionTimeShort(
    updatedAtMs: Long,
    isZh: Boolean,
    yesterdayLabel: String,
): String {
    if (updatedAtMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = (now - updatedAtMs).coerceAtLeast(0L)
    if (diffMs < 60_000L) return if (isZh) "刚刚" else "Now"

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStartMs = cal.timeInMillis
    val yesterdayStartMs = todayStartMs - 86_400_000L
    val weekStartMs = todayStartMs - 7L * 86_400_000L

    return when {
        updatedAtMs >= todayStartMs -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAtMs))
        }
        updatedAtMs >= yesterdayStartMs -> yesterdayLabel
        updatedAtMs >= weekStartMs -> {
            val days = ((todayStartMs - updatedAtMs) / 86_400_000L + 1L).toInt().coerceIn(2, 7)
            if (isZh) "${days}天前" else "${days}d"
        }
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(updatedAtMs))
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
