package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.VectorIndexProgress
import java.util.Locale

internal val MEMORY_CATEGORIES = listOf(
    MemoryCategory.preference,
    MemoryCategory.profile,
    MemoryCategory.project,
    MemoryCategory.rule,
    MemoryCategory.other,
)

internal val LEDGER_KINDS = listOf("fact", "relation", "promise", "event", "quest")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMemorySheet(
    userMemories: List<UserMemory>,
    ledgerItems: List<MemoryLedgerItem>,
    episodes: List<MemoryEpisode>,
    waterline: ContextWaterlineSummary,
    isExtracting: Boolean,
    initialTab: Int = 0,
    mutedRecallKeys: Set<String> = emptySet(),
    vectorIndexProgress: VectorIndexProgress = VectorIndexProgress(),
    activeRetrievalMode: RetrievalMode = RetrievalMode.LEXICAL_ONLY,
    isRerankActive: Boolean = false,
    onExtractMemories: () -> Unit,
    onAddMemory: (MemoryCategory, String) -> Unit,
    onToggleMemoryPin: (String) -> Unit,
    onToggleMemoryEnabled: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onAddLedger: (String, String, String) -> Unit,
    onToggleLedgerPin: (String) -> Unit,
    onDeleteLedger: (String) -> Unit,
    onToggleMuteRecall: ((String) -> Unit)? = null,
    onTogglePinRecall: ((String, String) -> Unit)? = null,
    onDisableRecall: ((String, String) -> Unit)? = null,
    onReindexVectors: (() -> Unit)? = null,
    onOpenContextSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    // Tab 0 state
    var memSearch by remember { mutableStateOf("") }
    var showAddMemory by remember { mutableStateOf(false) }
    var newMemCat by remember { mutableStateOf(MemoryCategory.preference) }
    var newMemText by remember { mutableStateOf("") }
    var editingMemoryId by remember { mutableStateOf<String?>(null) }
    var editMemCat by remember { mutableStateOf(MemoryCategory.preference) }
    var editMemText by remember { mutableStateOf("") }

    // Tab 1 state
    var ledgerSearch by remember { mutableStateOf("") }
    var showAddLedger by remember { mutableStateOf(false) }
    var newLedgerKind by remember { mutableStateOf("fact") }
    var newLedgerTitle by remember { mutableStateOf("") }
    var newLedgerContent by remember { mutableStateOf("") }
    var editingLedgerId by remember { mutableStateOf<String?>(null) }
    var editLedgerKind by remember { mutableStateOf("fact") }
    var editLedgerTitle by remember { mutableStateOf("") }
    var editLedgerContent by remember { mutableStateOf("") }

    val tabLabels = remember(isZh) {
        if (isZh) {
            listOf("已保存", "账本", "编年史", "召回")
        } else {
            listOf("Saved", "Ledger", "Arcs", "Recall")
        }
    }
    val tabBadges = remember(userMemories.size, waterline.recalledItems.size) {
        buildMap {
            if (userMemories.isNotEmpty()) put(0, userMemories.size)
            if (waterline.recalledItems.isNotEmpty()) put(3, waterline.recalledItems.size)
        }
    }

    val filteredMemories = remember(userMemories, memSearch, strings) {
        FuzzyMatcher.filterAndRank(userMemories, memSearch) { mem ->
            listOf(mem.content, localizeMemoryCategory(mem.category, strings))
        }
    }

    val filteredLedger = remember(ledgerItems, ledgerSearch, strings) {
        FuzzyMatcher.filterAndRank(ledgerItems, ledgerSearch) { item ->
            listOf(
                item.title,
                item.content,
                strings.chat.ledgerKinds[item.kind] ?: item.kind,
                item.aliases.joinToString(" "),
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.memoryTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            GuSegmentedControl(
                items = tabLabels,
                selectedIndex = tab,
                onSelect = { tab = it },
                badgeCounts = tabBadges,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            when (tab) {
                // Tab 0: Saved Memories
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "__mem_toolbar__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 40dp single-line toolbar: [🔍 检索…] [+ 新增] [✨ AI 提炼]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GuCompactSearchBar(
                                        value = memSearch,
                                        onValueChange = { memSearch = it },
                                        placeholder = strings.chat.searchMemories,
                                        clearContentDescription = strings.common.clear,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                    )
                                    ToolbarPillButton(
                                        icon = Icons.Default.Add,
                                        label = if (isZh) "新增" else "Add",
                                        fgColor = if (showAddMemory) Color.White else c.primarySoftInk,
                                        bgColor = if (showAddMemory) c.primary else c.primarySoft,
                                        onClick = { showAddMemory = !showAddMemory },
                                    )
                                    ToolbarPillButton(
                                        icon = Icons.Default.AutoAwesome,
                                        label = strings.chat.extractMemory,
                                        loading = isExtracting,
                                        fgColor = c.tagInk,
                                        bgColor = c.tag,
                                        onClick = onExtractMemories,
                                    )
                                }

                                // Collapsible add-memory form
                                AnimatedVisibility(visible = showAddMemory) {
                                    GuCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(12.dp),
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(MEMORY_CATEGORIES, key = { it.name }) { cat ->
                                                GuPill(
                                                    label = localizeMemoryCategory(cat, strings),
                                                    selected = newMemCat == cat,
                                                    onClick = { newMemCat = cat },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = newMemText,
                                            onValueChange = { newMemText = it },
                                            placeholder = strings.chat.memoryContentPlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showAddMemory = false
                                                    newMemText = ""
                                                },
                                            ) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.5.sp)
                                            }
                                            GuButton(
                                                onClick = {
                                                    if (newMemText.isNotBlank()) {
                                                        onAddMemory(newMemCat, newMemText)
                                                        newMemText = ""
                                                        showAddMemory = false
                                                    }
                                                },
                                                enabled = newMemText.isNotBlank(),
                                                compact = true,
                                            ) {
                                                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                // Compact count & capacity meta line
                                val enabledTok = remember(userMemories) {
                                    userMemories.filter { it.enabled }.sumOf { it.estimatedTokens }
                                }
                                GuMetaText(
                                    text = if (isZh) {
                                        "共 ${userMemories.size} / 500 条 · 已启用约 ${enabledTok}t（点击条目可编辑）"
                                    } else {
                                        "${userMemories.size} / 500 items · ~${enabledTok}t enabled (tap to edit)"
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }

                        if (filteredMemories.isEmpty()) {
                            item(key = "__mem_empty__") {
                                GuEmptyState(
                                    title = when {
                                        userMemories.isEmpty() -> strings.chat.emptyMemories
                                        isZh -> "未找到匹配记忆"
                                        else -> "No matching memories"
                                    },
                                    icon = Icons.Default.Psychology,
                                )
                            }
                        } else {
                            items(filteredMemories, key = { it.id }) { mem ->
                                val isEditing = editingMemoryId == mem.id
                                var menuOpen by remember(mem.id) { mutableStateOf(false) }

                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    onClick = if (!isEditing) {
                                        {
                                            editingMemoryId = mem.id
                                            editMemCat = mem.category
                                            editMemText = mem.content
                                        }
                                    } else {
                                        null
                                    },
                                ) {
                                    if (isEditing) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(MEMORY_CATEGORIES, key = { it.name }) { cat ->
                                                GuPill(
                                                    label = localizeMemoryCategory(cat, strings),
                                                    selected = editMemCat == cat,
                                                    onClick = { editMemCat = cat },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = editMemText,
                                            onValueChange = { editMemText = it },
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 5,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(onClick = { editingMemoryId = null }) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                                            }
                                            GuButton(
                                                onClick = {
                                                    val clean = editMemText.trim()
                                                    if (clean.isNotEmpty() && (editMemCat != mem.category || clean != mem.content.trim())) {
                                                        onDeleteMemory(mem.id)
                                                        onAddMemory(editMemCat, clean)
                                                    }
                                                    editingMemoryId = null
                                                },
                                                enabled = editMemText.isNotBlank(),
                                                compact = true,
                                            ) {
                                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            // Semantic status dot (tappable to toggle enabled)
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .clickable { onToggleMemoryEnabled(mem.id) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (mem.enabled) c.success else c.muted.copy(alpha = 0.45f)),
                                                )
                                            }
                                            GuBadge(
                                                text = localizeMemoryCategory(mem.category, strings),
                                                variant = if (mem.enabled) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL,
                                            )
                                            GuMetaText("~${mem.estimatedTokens}t")
                                            if (!mem.enabled) {
                                                GuMetaText(if (isZh) "已停用" else "Off")
                                            }
                                            Spacer(Modifier.weight(1f))
                                            IconButton(
                                                onClick = { onToggleMemoryPin(mem.id) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = if (mem.pinned) strings.chat.ledgerUnpin else strings.chat.ledgerPin,
                                                    tint = if (mem.pinned) c.accent else c.muted.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                            Box {
                                                IconButton(
                                                    onClick = { menuOpen = true },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreHoriz,
                                                        contentDescription = strings.chat.moreActions,
                                                        tint = c.muted,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                                GuPopupMenu(
                                                    expanded = menuOpen,
                                                    onDismissRequest = { menuOpen = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = if (mem.enabled) {
                                                                    if (isZh) "停用" else "Disable"
                                                                } else {
                                                                    if (isZh) "启用" else "Enable"
                                                                },
                                                                fontSize = 13.5.sp,
                                                            )
                                                        },
                                                        onClick = {
                                                            menuOpen = false
                                                            onToggleMemoryEnabled(mem.id)
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.common.edit, fontSize = 13.5.sp) },
                                                        onClick = {
                                                            menuOpen = false
                                                            editingMemoryId = mem.id
                                                            editMemCat = mem.category
                                                            editMemText = mem.content
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.common.delete, color = c.danger, fontSize = 13.5.sp) },
                                                        onClick = {
                                                            menuOpen = false
                                                            onDeleteMemory(mem.id)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = mem.content,
                                            color = if (mem.enabled) c.ink else c.muted,
                                            fontSize = 13.5.sp,
                                            lineHeight = 19.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: RP Ledger
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "__ledger_toolbar__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 40dp single-line toolbar: [🔍 检索…] [+ 新增]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GuCompactSearchBar(
                                        value = ledgerSearch,
                                        onValueChange = { ledgerSearch = it },
                                        placeholder = if (isZh) "检索账本…" else "Search ledger…",
                                        clearContentDescription = strings.common.clear,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                    )
                                    ToolbarPillButton(
                                        icon = Icons.Default.Add,
                                        label = if (isZh) "新增" else "Add",
                                        fgColor = if (showAddLedger) Color.White else c.primarySoftInk,
                                        bgColor = if (showAddLedger) c.primary else c.primarySoft,
                                        onClick = { showAddLedger = !showAddLedger },
                                    )
                                }

                                AnimatedVisibility(visible = showAddLedger) {
                                    GuCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(12.dp),
                                    ) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(LEDGER_KINDS, key = { it }) { k ->
                                                GuPill(
                                                    label = strings.chat.ledgerKinds[k] ?: k,
                                                    selected = newLedgerKind == k,
                                                    onClick = { newLedgerKind = k },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = newLedgerTitle,
                                            onValueChange = { newLedgerTitle = it },
                                            placeholder = strings.chat.ledgerTitlePlaceholder,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = newLedgerContent,
                                            onValueChange = { newLedgerContent = it },
                                            placeholder = strings.chat.ledgerContentPlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showAddLedger = false
                                                    newLedgerTitle = ""
                                                    newLedgerContent = ""
                                                },
                                            ) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.5.sp)
                                            }
                                            val canAddLedger = newLedgerTitle.isNotBlank() && newLedgerContent.isNotBlank()
                                            GuButton(
                                                onClick = {
                                                    if (canAddLedger) {
                                                        onAddLedger(newLedgerKind, newLedgerTitle, newLedgerContent)
                                                        newLedgerTitle = ""
                                                        newLedgerContent = ""
                                                        showAddLedger = false
                                                    }
                                                },
                                                enabled = canAddLedger,
                                                compact = true,
                                            ) {
                                                Text(strings.common.save, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                GuMetaText(
                                    text = if (isZh) {
                                        "${strings.chat.ledgerTabTitle} · ${ledgerItems.size} 条（点击条目可编辑）"
                                    } else {
                                        "${strings.chat.ledgerTabTitle} · ${ledgerItems.size} items (tap to edit)"
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }

                        if (filteredLedger.isEmpty()) {
                            item(key = "__ledger_empty__") {
                                GuEmptyState(
                                    title = when {
                                        ledgerItems.isEmpty() -> strings.chat.ledgerEmpty
                                        isZh -> "未找到匹配账本条目"
                                        else -> "No matching ledger entries"
                                    },
                                    icon = Icons.Default.Bookmarks,
                                )
                            }
                        } else {
                            items(filteredLedger, key = { it.id }) { item ->
                                val isEditing = editingLedgerId == item.id
                                var menuOpen by remember(item.id) { mutableStateOf(false) }

                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    onClick = if (!isEditing) {
                                        {
                                            editingLedgerId = item.id
                                            editLedgerKind = item.kind
                                            editLedgerTitle = item.title
                                            editLedgerContent = item.content
                                        }
                                    } else {
                                        null
                                    },
                                ) {
                                    if (isEditing) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(LEDGER_KINDS, key = { it }) { k ->
                                                GuPill(
                                                    label = strings.chat.ledgerKinds[k] ?: k,
                                                    selected = editLedgerKind == k,
                                                    onClick = { editLedgerKind = k },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = editLedgerTitle,
                                            onValueChange = { editLedgerTitle = it },
                                            placeholder = strings.chat.ledgerTitlePlaceholder,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        GuTextField(
                                            value = editLedgerContent,
                                            onValueChange = { editLedgerContent = it },
                                            placeholder = strings.chat.ledgerContentPlaceholder,
                                            singleLine = false,
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(onClick = { editingLedgerId = null }) {
                                                Text(strings.common.cancel, color = c.muted, fontSize = 12.sp)
                                            }
                                            val canSave = editLedgerTitle.isNotBlank() && editLedgerContent.isNotBlank()
                                            GuButton(
                                                onClick = {
                                                    if (canSave) {
                                                        val changed = editLedgerKind != item.kind ||
                                                            editLedgerTitle.trim() != item.title.trim() ||
                                                            editLedgerContent.trim() != item.content.trim()
                                                        if (changed) {
                                                            onDeleteLedger(item.id)
                                                            onAddLedger(editLedgerKind, editLedgerTitle.trim(), editLedgerContent.trim())
                                                        }
                                                        editingLedgerId = null
                                                    }
                                                },
                                                enabled = canSave,
                                                compact = true,
                                            ) {
                                                Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            item.stale -> c.danger
                                                            item.enabled -> c.success
                                                            else -> c.muted.copy(alpha = 0.45f)
                                                        },
                                                    ),
                                            )
                                            GuBadge(
                                                text = strings.chat.ledgerKinds[item.kind] ?: item.kind,
                                                variant = GuBadgeVariant.HONEY,
                                            )
                                            Text(
                                                text = item.title,
                                                color = if (item.enabled) c.ink else c.muted,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (item.stale) {
                                                GuBadge(if (isZh) "待更新" else "Stale", variant = GuBadgeVariant.DANGER)
                                            }
                                            IconButton(
                                                onClick = { onToggleLedgerPin(item.id) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = if (item.pinned) strings.chat.ledgerUnpin else strings.chat.ledgerPin,
                                                    tint = if (item.pinned) c.accent else c.muted.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                            Box {
                                                IconButton(
                                                    onClick = { menuOpen = true },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreHoriz,
                                                        contentDescription = strings.chat.moreActions,
                                                        tint = c.muted,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                                GuPopupMenu(
                                                    expanded = menuOpen,
                                                    onDismissRequest = { menuOpen = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(strings.common.edit, fontSize = 13.5.sp) },
                                                        onClick = {
                                                            menuOpen = false
                                                            editingLedgerId = item.id
                                                            editLedgerKind = item.kind
                                                            editLedgerTitle = item.title
                                                            editLedgerContent = item.content
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(strings.common.delete, color = c.danger, fontSize = 13.5.sp) },
                                                        onClick = {
                                                            menuOpen = false
                                                            onDeleteLedger(item.id)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = item.content,
                                            color = c.muted,
                                            fontSize = 12.5.sp,
                                            lineHeight = 18.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 2: Episodes Chronicle
                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "__ep_header__") {
                            GuSectionHeader(
                                title = strings.chat.episodesTitle,
                                trailing = {
                                    GuBadge("${episodes.size}", variant = GuBadgeVariant.NEUTRAL)
                                },
                            )
                        }
                        if (episodes.isEmpty()) {
                            item(key = "__ep_empty__") {
                                GuEmptyState(
                                    title = strings.chat.episodesEmpty,
                                    icon = Icons.Default.AutoStories,
                                )
                            }
                        } else {
                            items(episodes, key = { it.id }) { ep ->
                                GuCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(12.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val lvlName = when (ep.level) {
                                            3 -> if (isZh) "总编年 L3" else "Chronicle L3"
                                            2 -> if (isZh) "分卷 L2" else "Arc L2"
                                            else -> if (isZh) "幕次 L1" else "Episode L1"
                                        }
                                        GuBadge(lvlName, variant = GuBadgeVariant.BLUE)
                                        GuBadge("Seq ${ep.fromSeq}~${ep.toSeq}", variant = GuBadgeVariant.NEUTRAL)
                                        if (ep.stale) {
                                            GuBadge(if (isZh) "待更新" else "Stale", variant = GuBadgeVariant.DANGER)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(ep.title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(ep.summary, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }

                // Tab 3: Recall Inspector (2-row ~84dp cards, 4+ visible per screen)
                else -> {
                    ChatMemoryRecallInspectorTab(
                        userMemories = userMemories,
                        ledgerItems = ledgerItems,
                        episodes = episodes,
                        waterline = waterline,
                        mutedRecallKeys = mutedRecallKeys,
                        vectorIndexProgress = vectorIndexProgress,
                        activeRetrievalMode = activeRetrievalMode,
                        isRerankActive = isRerankActive,
                        onToggleMuteRecall = onToggleMuteRecall,
                        onTogglePinRecall = onTogglePinRecall,
                        onDisableRecall = onDisableRecall,
                        onReindexVectors = onReindexVectors,
                        onOpenContextSettings = onOpenContextSettings,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToolbarPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fgColor: Color,
    bgColor: Color,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(11.dp),
        color = bgColor,
        modifier = Modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = fgColor,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = fgColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

