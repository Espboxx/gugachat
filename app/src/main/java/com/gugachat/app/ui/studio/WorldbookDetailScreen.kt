package com.gugachat.app.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuFullscreenPage
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.WorldbookEngine

private enum class WorldbookEntryFilter {
    ALL,
    ENABLED,
    DISABLED,
    CONSTANT,
    KEYWORD,
}

/**
 * Dedicated full-screen Worldbook entry manager & push-pop 7-field Entry Editor
 * (replaces `WorldbookDetailSheet` to eliminate swipe-dismiss conflicts on dense entry lists).
 */
@Composable
fun WorldbookDetailScreen(
    worldbook: WorldbookEntity,
    entries: List<WorldbookEntryEntity>,
    vectorReady: Boolean,
    isIndexing: Boolean,
    onToggleGlobal: (Boolean) -> Unit,
    onUpdateMeta: (name: String, desc: String, scanDepth: Int, tokenBudget: Int, recursive: Boolean) -> Unit,
    onReindex: () -> Unit,
    onDeleteBook: () -> Unit,
    onToggleEntryEnabled: (WorldbookEntryEntity, Boolean) -> Unit,
    onBatchSetEntriesEnabled: (entryIds: List<String>, enabled: Boolean) -> Unit,
    onMoveEntryOrder: (entryId: String, delta: Int) -> Unit,
    onSaveEntry: (
        entryId: String?,
        title: String,
        content: String,
        keys: List<String>,
        secKeys: List<String>,
        selectiveLogic: Int,
        constant: Boolean,
        enabled: Boolean,
        position: Int,
        depth: Int,
        sticky: Int,
        cooldown: Int,
    ) -> Unit,
    onDeleteEntry: (WorldbookEntryEntity) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    // Hoisted above GuPushPopContainer so LazyColumn scroll position survives editor push/pop!
    val listState = rememberLazyListState()

    var searchQuery by remember(worldbook.id) { mutableStateOf("") }
    var activeFilter by remember(worldbook.id) { mutableStateOf(WorldbookEntryFilter.ALL) }
    var reorderMode by remember(worldbook.id) { mutableStateOf(false) }
    var showBookConfig by remember(worldbook.id) { mutableStateOf(false) }
    var showBatchMenu by remember { mutableStateOf(false) }
    var confirmDeleteBook by remember { mutableStateOf(false) }
    var pendingDeleteEntry by remember { mutableStateOf<WorldbookEntryEntity?>(null) }

    var editingEntryId by remember(worldbook.id) { mutableStateOf<String?>(null) }
    var isEditingMode by remember(worldbook.id) { mutableStateOf(false) }

    // 7-field entry editor drafts
    var draftTitle by remember { mutableStateOf("") }
    var draftPrimaryKeys by remember { mutableStateOf("") }
    var draftSecondaryKeys by remember { mutableStateOf("") }
    var draftSelectiveLogic by remember { mutableIntStateOf(0) }
    var draftContent by remember { mutableStateOf("") }
    var draftConstant by remember { mutableStateOf(false) }
    var draftEnabled by remember { mutableStateOf(true) }
    var draftPosition by remember { mutableIntStateOf(WorldbookEngine.POSITION_AFTER_CHAR) }
    var draftDepth by remember { mutableIntStateOf(4) }
    var draftSticky by remember { mutableIntStateOf(0) }
    var draftCooldown by remember { mutableIntStateOf(0) }

    fun openEntryEditor(entry: WorldbookEntryEntity?) {
        editingEntryId = entry?.id
        draftTitle = entry?.title.orEmpty()
        draftPrimaryKeys = decodeJsonList<String>(entry?.keysJson).joinToString(", ")
        draftSecondaryKeys = decodeJsonList<String>(entry?.secondaryKeysJson).joinToString(", ")
        draftSelectiveLogic = entry?.selectiveLogic ?: 0
        draftContent = entry?.content.orEmpty()
        draftConstant = entry?.constant ?: false
        draftEnabled = entry?.enabled ?: true
        draftPosition = entry?.position ?: WorldbookEngine.POSITION_AFTER_CHAR
        draftDepth = entry?.depth ?: 4
        draftSticky = entry?.sticky ?: 0
        draftCooldown = entry?.cooldown ?: 0
        isEditingMode = true
    }

    val decodedKeysById = remember(entries) {
        entries.associate { entry ->
            entry.id to (decodeJsonList<String>(entry.keysJson) to decodeJsonList<String>(entry.secondaryKeysJson))
        }
    }

    val trimmedQuery = searchQuery.trim()
    val canReorder = trimmedQuery.isEmpty() && activeFilter == WorldbookEntryFilter.ALL
    LaunchedEffect(canReorder) {
        if (!canReorder && reorderMode) {
            reorderMode = false
        }
    }

    val enabledCount = remember(entries) { entries.count { it.enabled } }
    val disabledCount = remember(entries) { entries.count { !it.enabled } }
    val constantCount = remember(entries) { entries.count { it.constant } }
    val keywordCount = remember(entries) { entries.count { !it.constant } }
    val enabledTokens = remember(entries) { entries.filter { it.enabled }.sumOf { it.estimatedTokens } }

    val filteredEntries = remember(entries, decodedKeysById, trimmedQuery, activeFilter) {
        val byFilter = entries.filter { entry ->
            when (activeFilter) {
                WorldbookEntryFilter.ALL -> true
                WorldbookEntryFilter.ENABLED -> entry.enabled
                WorldbookEntryFilter.DISABLED -> !entry.enabled
                WorldbookEntryFilter.CONSTANT -> entry.constant
                WorldbookEntryFilter.KEYWORD -> !entry.constant
            }
        }
        if (trimmedQuery.isEmpty()) {
            byFilter
        } else {
            FuzzyMatcher.filterAndRank(byFilter, trimmedQuery) { entry ->
                val (pKeys, sKeys) = decodedKeysById[entry.id] ?: (emptyList<String>() to emptyList())
                listOf(
                    entry.title,
                    pKeys.joinToString(" "),
                    sKeys.joinToString(" "),
                    entry.content,
                )
            }
        }
    }

    if (confirmDeleteBook) {
        GuConfirmDialog(
            title = if (isZh) "删除世界书" else "Delete Worldbook",
            message = if (isZh) "确认删除「${worldbook.name}」及其中 ${entries.size} 条词条？"
            else "Delete \"${worldbook.name}\" and its ${entries.size} entries?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDeleteBook = false
                onDeleteBook()
            },
            onDismiss = { confirmDeleteBook = false },
        )
    }

    pendingDeleteEntry?.let { target ->
        GuConfirmDialog(
            title = if (isZh) "删除词条" else "Delete Entry",
            message = if (isZh) {
                "确认删除词条「${target.title.ifBlank { "#${target.uid}" }}」？"
            } else {
                "Delete entry \"${target.title.ifBlank { "#${target.uid}" }}\"?"
            },
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                pendingDeleteEntry = null
                onDeleteEntry(target)
            },
            onDismiss = { pendingDeleteEntry = null },
        )
    }

    GuPushPopContainer(
        showingSubpage = isEditingMode,
        label = "worldbook_detail_editor_push_pop",
    ) { inEditor ->
        if (!inEditor) {
            val statusLabel = when {
                isIndexing -> if (isZh) "索引中" else "Indexing"
                vectorReady -> if (isZh) "向量就绪" else "Vector Ready"
                else -> if (isZh) "词法检索" else "Lexical"
            }
            val tokenShort = FileContextBudgeter.formatShortTokens(enabledTokens)
            val subtitle = if (isZh) {
                "已启用 $enabledCount/${entries.size} 条 · ~$tokenShort · $statusLabel"
            } else {
                "$enabledCount/${entries.size} active · ~$tokenShort · $statusLabel"
            }

            GuFullscreenPage(
                title = worldbook.name.ifBlank { if (isZh) "未命名世界书" else "Untitled Worldbook" },
                subtitle = subtitle,
                backContentDescription = strings.common.back,
                onBack = onBack,
                trailing = {
                    Surface(
                        onClick = { openEntryEditor(null) },
                        shape = RoundedCornerShape(999.dp),
                        color = c.primarySoft,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = c.primarySoftInk,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (isZh) "新建" else "New",
                                color = c.primarySoftInk,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    IconButton(
                        onClick = onReindex,
                        enabled = !isIndexing,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isZh) "重建索引" else "Reindex",
                            tint = if (isIndexing) c.muted else c.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { showBookConfig = !showBookConfig },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = if (isZh) "配置" else "Settings",
                            tint = if (showBookConfig) c.primary else c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { confirmDeleteBook = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.common.delete,
                            tint = c.danger,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                // Collapsible worldbook configuration card
                AnimatedVisibility(visible = showBookConfig) {
                    var nameDraft by remember(worldbook.id, worldbook.name) { mutableStateOf(worldbook.name) }
                    var descDraft by remember(worldbook.id, worldbook.description) { mutableStateOf(worldbook.description) }
                    var scanDepthDraft by remember(worldbook.id, worldbook.scanDepth) { mutableIntStateOf(worldbook.scanDepth) }
                    var budgetDraft by remember(worldbook.id, worldbook.tokenBudget) { mutableIntStateOf(worldbook.tokenBudget) }
                    var recursiveDraft by remember(worldbook.id, worldbook.recursiveScanning) { mutableStateOf(worldbook.recursiveScanning) }

                    Column {
                        GuCard(
                            backgroundColor = c.surfaceSoft,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GuSwitchRow(
                                    label = if (isZh) "全局启用此世界书" else "Global Active",
                                    subtitle = if (isZh) "所有会话自动挂载并参与关键词/向量检索" else "Auto-mount across all sessions",
                                    checked = worldbook.globalEnabled,
                                    onCheckedChange = onToggleGlobal,
                                )
                                GuHairlineDivider(alpha = 0.45f)
                                GuTextField(
                                    value = nameDraft,
                                    onValueChange = { nameDraft = it },
                                    label = if (isZh) "世界书名称" else "Name",
                                )
                                GuTextField(
                                    value = descDraft,
                                    onValueChange = { descDraft = it },
                                    label = if (isZh) "简介" else "Description",
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(if (isZh) "扫描深度 (最近消息)" else "Scan Depth", color = c.ink, fontSize = 13.sp)
                                    GuInlineStepper(
                                        valueText = "$scanDepthDraft",
                                        onDecrement = { if (scanDepthDraft > 1) scanDepthDraft-- },
                                        onIncrement = { if (scanDepthDraft < 24) scanDepthDraft++ },
                                        canDecrement = scanDepthDraft > 1,
                                        canIncrement = scanDepthDraft < 24,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(if (isZh) "Token 预算上限" else "Token Budget", color = c.ink, fontSize = 13.sp)
                                    GuInlineStepper(
                                        valueText = "$budgetDraft",
                                        onDecrement = { budgetDraft = (budgetDraft - 200).coerceAtLeast(400) },
                                        onIncrement = { budgetDraft = (budgetDraft + 200).coerceAtMost(16_000) },
                                        canDecrement = budgetDraft > 400,
                                        canIncrement = budgetDraft < 16_000,
                                    )
                                }
                                GuSwitchRow(
                                    label = if (isZh) "允许 1-hop 递归触发" else "Recursive Scanning",
                                    checked = recursiveDraft,
                                    onCheckedChange = { recursiveDraft = it },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    GuButton(
                                        onClick = {
                                            onUpdateMeta(nameDraft, descDraft, scanDepthDraft, budgetDraft, recursiveDraft)
                                            showBookConfig = false
                                        },
                                        enabled = nameDraft.isNotBlank(),
                                        compact = true,
                                    ) { Text(strings.common.save) }
                                }
                            }
                        }
                        GuHairlineDivider(alpha = 0.45f)
                    }
                }

                // Sticky search + filter pills + batch/reorder actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuCompactSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = if (isZh) "搜索词条标题、关键词、次级词或正文…" else "Search title, keys, or lore content…",
                        clearContentDescription = strings.common.clear,
                        modifier = Modifier.height(40.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GuPill(
                                label = if (isZh) "全部 (${entries.size})" else "All (${entries.size})",
                                selected = activeFilter == WorldbookEntryFilter.ALL,
                                onClick = { activeFilter = WorldbookEntryFilter.ALL },
                            )
                            GuPill(
                                label = if (isZh) "已启用 ($enabledCount)" else "On ($enabledCount)",
                                selected = activeFilter == WorldbookEntryFilter.ENABLED,
                                onClick = { activeFilter = WorldbookEntryFilter.ENABLED },
                            )
                            GuPill(
                                label = if (isZh) "已停用 ($disabledCount)" else "Off ($disabledCount)",
                                selected = activeFilter == WorldbookEntryFilter.DISABLED,
                                onClick = { activeFilter = WorldbookEntryFilter.DISABLED },
                            )
                            GuPill(
                                label = if (isZh) "常驻 ($constantCount)" else "Const ($constantCount)",
                                selected = activeFilter == WorldbookEntryFilter.CONSTANT,
                                onClick = { activeFilter = WorldbookEntryFilter.CONSTANT },
                            )
                            GuPill(
                                label = if (isZh) "按词 ($keywordCount)" else "Keyword ($keywordCount)",
                                selected = activeFilter == WorldbookEntryFilter.KEYWORD,
                                onClick = { activeFilter = WorldbookEntryFilter.KEYWORD },
                            )
                        }

                        // Batch Enable/Disable dropdown
                        Box {
                            Surface(
                                onClick = { showBatchMenu = true },
                                enabled = filteredEntries.isNotEmpty(),
                                shape = RoundedCornerShape(999.dp),
                                color = c.surfaceSoft,
                                modifier = Modifier.alpha(if (filteredEntries.isNotEmpty()) 1f else 0.45f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = c.ink,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (isZh) "批量" else "Batch",
                                        color = c.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            GuPopupMenu(
                                expanded = showBatchMenu,
                                onDismissRequest = { showBatchMenu = false },
                            ) {
                                val ids = filteredEntries.map { it.id }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isZh) "批量启用当前 (${ids.size})" else "Enable Visible (${ids.size})",
                                            color = c.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        showBatchMenu = false
                                        onBatchSetEntriesEnabled(ids, true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isZh) "批量停用当前 (${ids.size})" else "Disable Visible (${ids.size})",
                                            color = c.danger,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        showBatchMenu = false
                                        onBatchSetEntriesEnabled(ids, false)
                                    },
                                )
                            }
                        }

                        // Reorder toggle pill (insertionOrder swap, disabled while search/filter active)
                        Surface(
                            onClick = { if (canReorder) reorderMode = !reorderMode },
                            enabled = canReorder,
                            shape = RoundedCornerShape(999.dp),
                            color = if (reorderMode && canReorder) c.primarySoft else c.surfaceSoft,
                            modifier = Modifier.alpha(if (canReorder) 1f else 0.45f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = if (reorderMode && canReorder) c.primarySoftInk else c.ink,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (reorderMode && canReorder) {
                                        if (isZh) "完成" else "Done"
                                    } else {
                                        if (isZh) "排序" else "Sort"
                                    },
                                    color = if (reorderMode && canReorder) c.primarySoftInk else c.ink,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    if (!canReorder) {
                        Text(
                            text = if (isZh) "提示：清空搜索与筛选后可调整词条插入优先级顺序" else "Clear search & filter to reorder entries",
                            color = c.muted,
                            fontSize = 11.sp,
                        )
                    }
                }
                GuHairlineDivider(alpha = 0.45f)

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GuEmptyState(
                            title = if (entries.isEmpty()) {
                                if (isZh) "暂无世界书词条" else "No entries yet"
                            } else {
                                if (isZh) "没有匹配的词条" else "No matching entries"
                            },
                            subtitle = if (entries.isEmpty()) {
                                if (isZh) "点击右上角「+ 新建」添加常驻设定或关键词触发条目" else "Tap New to create lore entries"
                            } else {
                                if (isZh) "尝试清空搜索或切换回「全部」筛选" else "Clear search or switch filter back to All"
                            },
                            actionLabel = if (entries.isEmpty()) (if (isZh) "新建词条" else "Add Entry") else null,
                            onAction = if (entries.isEmpty()) ({ openEntryEditor(null) }) else null,
                            icon = Icons.Default.Book,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { _, entry -> entry.id },
                        ) { idx, entry ->
                            val primaryKeys = decodedKeysById[entry.id]?.first.orEmpty()
                            WorldbookEntryRowCard(
                                entry = entry,
                                primaryKeys = primaryKeys,
                                reorderMode = reorderMode && canReorder,
                                canMoveUp = idx > 0,
                                canMoveDown = idx < filteredEntries.lastIndex,
                                isZh = isZh,
                                deleteContentDescription = strings.common.delete,
                                onOpenEditor = { openEntryEditor(entry) },
                                onToggleEnabled = { next -> onToggleEntryEnabled(entry, next) },
                                onDelete = { pendingDeleteEntry = entry },
                                onMoveUp = { onMoveEntryOrder(entry.id, -1) },
                                onMoveDown = { onMoveEntryOrder(entry.id, +1) },
                            )
                        }
                    }
                }
            }
        } else {
            // Full-screen 7-field Entry Editor (Back pops to list preserving LazyListState)
            val canSave = draftContent.isNotBlank()
            fun commitEntrySave() {
                if (!canSave) return
                val pKeys = draftPrimaryKeys.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                val sKeys = draftSecondaryKeys.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                onSaveEntry(
                    editingEntryId,
                    draftTitle,
                    draftContent,
                    pKeys,
                    sKeys,
                    draftSelectiveLogic,
                    draftConstant,
                    draftEnabled,
                    draftPosition,
                    draftDepth,
                    draftSticky,
                    draftCooldown,
                )
                isEditingMode = false
            }

            GuFullscreenPage(
                title = if (editingEntryId == null) {
                    if (isZh) "新建词条 (7 项核心字段)" else "New Entry"
                } else {
                    if (isZh) "编辑词条" else "Edit Entry"
                },
                subtitle = worldbook.name,
                backContentDescription = strings.common.back,
                onBack = { isEditingMode = false },
                trailing = {
                    GuButton(
                        onClick = { commitEntrySave() },
                        enabled = canSave,
                        compact = true,
                    ) {
                        Text(strings.common.save, fontSize = 12.sp)
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    // 1. Title
                    GuTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = if (isZh) "1. 词条标题" else "1. Entry Title",
                        placeholder = if (isZh) "例如：永安宫夜禁律令" else "Entry title",
                    )

                    // 2. Primary Keywords
                    GuTextField(
                        value = draftPrimaryKeys,
                        onValueChange = { draftPrimaryKeys = it },
                        label = if (isZh) "2. 主关键词（英文逗号或换行分隔）" else "2. Primary Keywords",
                        placeholder = if (isZh) "夜禁, 侍卫, 坤宁宫" else "key1, key2",
                    )

                    // 3. Secondary Keywords + selectiveLogic (AND/NOT)
                    GuTextField(
                        value = draftSecondaryKeys,
                        onValueChange = { draftSecondaryKeys = it },
                        label = if (isZh) "3. 次级关键词 & AND/NOT 逻辑" else "3. Secondary Keys & Logic",
                        placeholder = if (isZh) "留空表示不使用次级条件" else "Optional secondary keys",
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val logicOptions = listOf(
                            0 to if (isZh) "任一匹配 AND_ANY" else "AND ANY",
                            3 to if (isZh) "全部匹配 AND_ALL" else "AND ALL",
                            1 to if (isZh) "非全包含 NOT_ALL" else "NOT ALL",
                            2 to if (isZh) "均不包含 NOT_ANY" else "NOT ANY",
                        )
                        logicOptions.forEach { (code, label) ->
                            GuPill(
                                label = label,
                                selected = draftSelectiveLogic == code,
                                onClick = { draftSelectiveLogic = code },
                            )
                        }
                    }

                    // 4. Content
                    GuTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        label = if (isZh) "4. 设定正文" else "4. Lore Content",
                        placeholder = if (isZh) "详细描述设定内容..." else "Lore text...",
                        singleLine = false,
                        minLines = 5,
                        maxLines = 12,
                    )

                    // 5. Constant + Enabled switches
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        GuSwitchRow(
                            label = if (isZh) "5. 常驻激活 (Constant)" else "5. Always Active (Constant)",
                            subtitle = if (isZh) "无需触发关键词，每轮稳态拼入缓存前缀" else "Always included without keyword match",
                            checked = draftConstant,
                            onCheckedChange = { draftConstant = it },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(color = c.border.copy(alpha = 0.45f))
                        GuSwitchRow(
                            label = if (isZh) "启用此词条" else "Entry Enabled",
                            checked = draftEnabled,
                            onCheckedChange = { draftEnabled = it },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    // 6. Position + @D Depth
                    Text(
                        text = if (isZh) "6. 注入位置与 @D 深度" else "6. Injection Position & @D Depth",
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val posList = listOf(
                            WorldbookEngine.POSITION_BEFORE_CHAR to if (isZh) "角色前" else "Before Char",
                            WorldbookEngine.POSITION_AFTER_CHAR to if (isZh) "角色后" else "After Char",
                            WorldbookEngine.POSITION_BEFORE_AN to if (isZh) "作者注前" else "Before AN",
                            WorldbookEngine.POSITION_AFTER_AN to if (isZh) "作者注后" else "After AN",
                            WorldbookEngine.POSITION_AT_DEPTH to "@D",
                        )
                        posList.forEach { (posCode, label) ->
                            GuPill(
                                label = label,
                                selected = draftPosition == posCode,
                                onClick = { draftPosition = posCode },
                            )
                        }
                    }
                    if (draftPosition == WorldbookEngine.POSITION_AT_DEPTH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(if (isZh) "插入深度 (@D)" else "Depth (@D)", color = c.ink, fontSize = 13.sp)
                            GuInlineStepper(
                                valueText = "@D$draftDepth",
                                onDecrement = { if (draftDepth > 0) draftDepth-- },
                                onIncrement = { if (draftDepth < 32) draftDepth++ },
                                canDecrement = draftDepth > 0,
                                canIncrement = draftDepth < 32,
                            )
                        }
                    }

                    // 7. Timed effects: Sticky & Cooldown
                    Text(
                        text = if (isZh) "7. 粘滞 (Sticky) 与冷却 (Cooldown)" else "7. Sticky & Cooldown Turns",
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuCard(backgroundColor = c.surfaceSoft) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "Sticky 粘滞回合" else "Sticky Turns", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$draftSticky",
                                    onDecrement = { if (draftSticky > 0) draftSticky-- },
                                    onIncrement = { if (draftSticky < 50) draftSticky++ },
                                    canDecrement = draftSticky > 0,
                                    canIncrement = draftSticky < 50,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(if (isZh) "Cooldown 冷却回合" else "Cooldown Turns", color = c.ink, fontSize = 13.sp)
                                GuInlineStepper(
                                    valueText = "$draftCooldown",
                                    onDecrement = { if (draftCooldown > 0) draftCooldown-- },
                                    onIncrement = { if (draftCooldown < 50) draftCooldown++ },
                                    canDecrement = draftCooldown > 0,
                                    canIncrement = draftCooldown < 50,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                GuHairlineDivider(alpha = 0.45f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuButton(
                        onClick = { isEditingMode = false },
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.common.cancel) }
                    GuButton(
                        onClick = { commitEntrySave() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) { Text(strings.common.save) }
                }
            }
        }
    }
}

@Composable
private fun WorldbookEntryRowCard(
    entry: WorldbookEntryEntity,
    primaryKeys: List<String>,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isZh: Boolean,
    deleteContentDescription: String,
    onOpenEditor: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenEditor,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (entry.enabled) 1f else 0.60f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.title.ifBlank { if (isZh) "词条 #${entry.uid}" else "Entry #${entry.uid}" },
                        color = c.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.constant) {
                        GuBadge(text = if (isZh) "常驻" else "Const", variant = GuBadgeVariant.AMBER)
                    } else if (entry.position == WorldbookEngine.POSITION_AT_DEPTH) {
                        GuBadge(text = "@D${entry.depth}", variant = GuBadgeVariant.BLUE)
                    }
                    if (entry.estimatedTokens > 0) {
                        GuBadge(
                            text = "~${FileContextBudgeter.formatShortTokens(entry.estimatedTokens)}",
                            variant = GuBadgeVariant.NEUTRAL,
                        )
                    }
                }
                val keyLine = primaryKeys.joinToString(" · ")
                if (keyLine.isNotBlank()) {
                    Text(
                        text = keyLine,
                        color = c.primary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = entry.content.replace('\n', ' ').trim(),
                    color = c.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (reorderMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isZh) "上移" else "Move Up",
                            tint = if (canMoveUp) c.primary else c.muted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isZh) "下移" else "Move Down",
                            tint = if (canMoveDown) c.primary else c.muted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.muted,
                            uncheckedTrackColor = c.surfaceSoft,
                        ),
                        modifier = Modifier.graphicsLayer { scaleX = 0.84f; scaleY = 0.84f },
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = deleteContentDescription,
                            tint = c.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
