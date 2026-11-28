package com.gugachat.app.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
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
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuFullscreenPage
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPopupMenu
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.IndexedPresetBlock
import com.gugachat.app.domain.chat.PresetBlockStatusFilter
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.PromptPresetBlockOps
import com.gugachat.app.domain.chat.RpRegexScript

enum class PromptPresetEditorMode {
    CHAT,
    STUDIO,
}

/**
 * Unified full-screen Prompt Preset & Regex editor shared by both Chat (`对话参数 -> 提示词预设`)
 * and Creative Studio (`创作 -> 设定 -> 预设/正则`).
 */
@Composable
fun PromptPresetEditorScreen(
    mode: PromptPresetEditorMode,
    activePreset: PromptPresetEntity?,
    allPresets: List<PromptPresetEntity> = emptyList(),
    onSelectPreset: ((String?) -> Unit)? = null,
    onToggleDefaultRp: ((presetId: String, enabled: Boolean) -> Unit)? = null,
    onDeletePreset: ((presetId: String) -> Unit)? = null,
    onToggleBlock: (presetId: String, identifier: String, index: Int, enabled: Boolean) -> Unit,
    onBatchSetBlocksEnabled: (presetId: String, indices: List<Int>, enabled: Boolean) -> Unit,
    onMoveBlock: (presetId: String, index: Int, delta: Int) -> Unit,
    onToggleRegex: ((presetId: String, scriptId: String, enabled: Boolean) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH

    val blocks = remember(activePreset?.id, activePreset?.blocksJson) {
        decodeJsonList<PromptPresetBlock>(activePreset?.blocksJson)
    }
    val regexScripts = remember(activePreset?.id, activePreset?.regexScriptsJson) {
        decodeJsonList<RpRegexScript>(activePreset?.regexScriptsJson)
    }

    var searchQuery by remember(activePreset?.id) { mutableStateOf("") }
    var statusFilter by remember(activePreset?.id) { mutableStateOf(PresetBlockStatusFilter.ALL) }
    var activeSubTab by remember(activePreset?.id) {
        mutableIntStateOf(if (blocks.isEmpty() && regexScripts.isNotEmpty()) 1 else 0)
    }
    var reorderMode by remember(activePreset?.id) { mutableStateOf(false) }
    var expandedOrigIdx by remember(activePreset?.id) { mutableStateOf<Int?>(null) }
    var showBatchMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val presetSwitcherState = rememberLazyListState()

    LaunchedEffect(activePreset?.id, blocks.isEmpty(), regexScripts.isNotEmpty()) {
        if (blocks.isEmpty() && regexScripts.isNotEmpty()) {
            activeSubTab = 1
        } else if (regexScripts.isEmpty() && activeSubTab == 1) {
            activeSubTab = 0
        }
    }

    LaunchedEffect(mode, activePreset?.id, allPresets) {
        if (mode == PromptPresetEditorMode.CHAT && allPresets.isNotEmpty()) {
            val activeIdx = activePreset?.id?.let { id -> allPresets.indexOfFirst { it.id == id } } ?: -1
            if (activeIdx >= 0) {
                presetSwitcherState.animateScrollToItem((activeIdx + 1).coerceAtLeast(0))
            }
        }
    }

    val nonMarkerTotal = remember(blocks) { blocks.count { !it.marker } }
    val nonMarkerEnabled = remember(blocks) { blocks.count { !it.marker && it.enabled } }
    val allEnabledCount = remember(blocks) { blocks.count { it.enabled } }
    val allDisabledCount = remember(blocks) { blocks.count { !it.enabled } }
    val activeTokens = remember(blocks) { PromptPresetBlockOps.estimateActiveTokens(blocks) }

    val trimmedQuery = searchQuery.trim()
    val canReorder = trimmedQuery.isEmpty() && statusFilter == PresetBlockStatusFilter.ALL
    LaunchedEffect(canReorder) {
        if (!canReorder && reorderMode) {
            reorderMode = false
        }
    }

    val filteredBlocks = remember(blocks, trimmedQuery, statusFilter) {
        PromptPresetBlockOps.filterBlocks(blocks, trimmedQuery, statusFilter)
    }
    val staticBlocks = remember(filteredBlocks) {
        filteredBlocks.filter { it.block.injectionPosition != 1 }
    }
    val dynamicBlocks = remember(filteredBlocks) {
        filteredBlocks.filter { it.block.injectionPosition == 1 }
    }
    val batchCandidateIndices = remember(filteredBlocks) {
        filteredBlocks.filter { !it.block.marker }.map { it.originalIndex }
    }

    val filteredRegex = remember(regexScripts, trimmedQuery) {
        val indexed = regexScripts.mapIndexed { idx, script -> idx to script }
        if (trimmedQuery.isEmpty()) {
            indexed
        } else {
            FuzzyMatcher.filterAndRank(indexed, trimmedQuery) { (_, rx) ->
                listOf(rx.name, rx.findRegex, rx.replaceString, rx.placement)
            }
        }
    }

    val pageTitle = activePreset?.name?.ifBlank {
        if (isZh) "未命名预设" else "Untitled Preset"
    } ?: if (isZh) "提示词预设槽位" else "Prompt Preset Blocks"

    val pageSubtitle = remember(activePreset?.id, blocks.size, nonMarkerEnabled, nonMarkerTotal, activeTokens, regexScripts, isZh) {
        if (activePreset == null) {
            if (isZh) "从下方选择预设挂载到当前会话" else "Pick a preset below to bind to this session"
        } else if (blocks.isEmpty() && regexScripts.isNotEmpty()) {
            val rxEnabled = regexScripts.count { it.enabled }
            if (isZh) "已启用 $rxEnabled/${regexScripts.size} 条正则脚本" else "$rxEnabled/${regexScripts.size} regex scripts active"
        } else {
            val tokLabel = FileContextBudgeter.formatShortTokens(activeTokens)
            if (isZh) {
                "已启用 $nonMarkerEnabled/$nonMarkerTotal 槽位 · 约 $tokLabel"
            } else {
                "$nonMarkerEnabled/$nonMarkerTotal active · ~$tokLabel"
            }
        }
    }

    if (confirmDelete && activePreset != null && onDeletePreset != null) {
        GuConfirmDialog(
            title = if (isZh) "删除设定预设" else "Delete Preset",
            message = if (isZh) "确认删除「${activePreset.name}」？" else "Delete \"${activePreset.name}\"?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDeletePreset(activePreset.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    GuFullscreenPage(
        title = pageTitle,
        subtitle = pageSubtitle,
        backContentDescription = strings.common.back,
        onBack = onBack,
        trailing = {
            when (mode) {
                PromptPresetEditorMode.CHAT -> {
                    if (activePreset != null && onSelectPreset != null) {
                        TextButton(
                            onClick = { onSelectPreset(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isZh) "停用预设" else "Clear",
                                color = c.danger,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                PromptPresetEditorMode.STUDIO -> {
                    if (activePreset != null) {
                        if (activePreset.sourceFormat != "sillytavern_regex" && onToggleDefaultRp != null) {
                            Surface(
                                onClick = { onToggleDefaultRp(activePreset.id, !activePreset.isDefaultRp) },
                                shape = RoundedCornerShape(999.dp),
                                color = if (activePreset.isDefaultRp) c.primarySoft else c.surfaceSoft,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    if (activePreset.isDefaultRp) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = c.primarySoftInk,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    Text(
                                        text = if (activePreset.isDefaultRp) {
                                            if (isZh) "默认 RP" else "Default RP"
                                        } else {
                                            if (isZh) "设为默认 RP" else "Set Default"
                                        },
                                        color = if (activePreset.isDefaultRp) c.primarySoftInk else c.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (onDeletePreset != null) {
                            IconButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.common.delete,
                                    tint = c.danger,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        // Optional Chat-mode horizontal preset switcher
        if (mode == PromptPresetEditorMode.CHAT && onSelectPreset != null) {
            LazyRow(
                state = presetSwitcherState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "__none__") {
                    GuPill(
                        label = if (isZh) "不使用预设" else "No Preset",
                        selected = activePreset == null,
                        onClick = { onSelectPreset(null) },
                    )
                }
                items(allPresets, key = { it.id }) { preset ->
                    GuPill(
                        label = preset.name.ifBlank { if (isZh) "预设" else "Preset" },
                        selected = preset.id == activePreset?.id,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }
            GuHairlineDivider(alpha = 0.4f)
        }

        if (activePreset == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                GuEmptyState(
                    title = if (isZh) "当前未绑定提示词预设" else "No Prompt Preset Bound",
                    subtitle = if (allPresets.isEmpty()) {
                        if (isZh) "暂无已导入的预设，可在「创作 → 设定」或导入页添加 SillyTavern 预设。"
                        else "No presets imported yet. Import a Tavern preset from Studio → Lore."
                    } else {
                        if (isZh) "在上方选择预设即可查看、搜索、批量开关或调整槽位顺序。"
                        else "Pick a preset above to search, toggle, or reorder prompt blocks."
                    },
                    icon = Icons.Default.Tune,
                )
            }
            return@GuFullscreenPage
        }

        // Sticky control panel (subtab switcher + 40dp search + filter pills + batch/reorder actions)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (blocks.isNotEmpty() && regexScripts.isNotEmpty()) {
                GuSegmentedControl(
                    items = listOf(
                        if (isZh) "提示词槽位 (${blocks.size})" else "Prompt Blocks (${blocks.size})",
                        if (isZh) "正则脚本 (${regexScripts.size})" else "Regex (${regexScripts.size})",
                    ),
                    selectedIndex = activeSubTab,
                    onSelect = { activeSubTab = it },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            GuCompactSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = if (activeSubTab == 0) {
                    if (isZh) "搜索槽位名称、标识、角色或内容…" else "Search block name, id, role, or content…"
                } else {
                    if (isZh) "搜索正则名称或表达式…" else "Search regex name or pattern…"
                },
                clearContentDescription = strings.common.clear,
                modifier = Modifier.height(40.dp),
            )

            if (activeSubTab == 0 && blocks.isNotEmpty()) {
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
                            label = if (isZh) "全部 (${blocks.size})" else "All (${blocks.size})",
                            selected = statusFilter == PresetBlockStatusFilter.ALL,
                            onClick = { statusFilter = PresetBlockStatusFilter.ALL },
                        )
                        GuPill(
                            label = if (isZh) "已启用 ($allEnabledCount)" else "On ($allEnabledCount)",
                            selected = statusFilter == PresetBlockStatusFilter.ENABLED,
                            onClick = { statusFilter = PresetBlockStatusFilter.ENABLED },
                        )
                        GuPill(
                            label = if (isZh) "已停用 ($allDisabledCount)" else "Off ($allDisabledCount)",
                            selected = statusFilter == PresetBlockStatusFilter.DISABLED,
                            onClick = { statusFilter = PresetBlockStatusFilter.DISABLED },
                        )
                    }

                    // Compact Batch menu (automatically skips structural markers)
                    Box {
                        Surface(
                            onClick = { showBatchMenu = true },
                            enabled = batchCandidateIndices.isNotEmpty(),
                            shape = RoundedCornerShape(999.dp),
                            color = c.surfaceSoft,
                            modifier = Modifier.alpha(if (batchCandidateIndices.isNotEmpty()) 1f else 0.45f),
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
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isZh) {
                                                "批量启用当前 (${batchCandidateIndices.size})"
                                            } else {
                                                "Enable Visible (${batchCandidateIndices.size})"
                                            },
                                            color = c.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (isZh) "自动跳过系统占位符" else "Skips structural markers",
                                            color = c.muted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                },
                                onClick = {
                                    showBatchMenu = false
                                    onBatchSetBlocksEnabled(activePreset.id, batchCandidateIndices, true)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = if (isZh) {
                                                "批量停用当前 (${batchCandidateIndices.size})"
                                            } else {
                                                "Disable Visible (${batchCandidateIndices.size})"
                                            },
                                            color = c.danger,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (isZh) "自动跳过系统占位符" else "Skips structural markers",
                                            color = c.muted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                },
                                onClick = {
                                    showBatchMenu = false
                                    onBatchSetBlocksEnabled(activePreset.id, batchCandidateIndices, false)
                                },
                            )
                        }
                    }

                    // Reorder toggle pill (disabled while search/filter is active)
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
                        text = if (isZh) "提示：清空搜索与状态筛选后可调整槽位顺序" else "Clear search & filter to reorder blocks",
                        color = c.muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        GuHairlineDivider(alpha = 0.45f)

        // Main virtualized list
        if (activeSubTab == 0) {
            if (filteredBlocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GuEmptyState(
                        title = if (blocks.isEmpty()) {
                            if (isZh) "该预设暂无提示词槽位" else "No prompt blocks in this preset"
                        } else {
                            if (isZh) "没有匹配的提示词槽位" else "No matching blocks"
                        },
                        subtitle = if (trimmedQuery.isNotEmpty() || statusFilter != PresetBlockStatusFilter.ALL) {
                            if (isZh) "尝试清空搜索词或切换回「全部」筛选" else "Clear search or switch filter back to All"
                        } else {
                            ""
                        },
                        icon = Icons.Default.Tune,
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
                    if (staticBlocks.isNotEmpty()) {
                        item(key = "zone_static_header") {
                            PresetEditorZoneHeader(
                                text = if (isZh) {
                                    "⚡ 静态前缀缓存区 (A/B) · ${staticBlocks.size}"
                                } else {
                                    "⚡ Static Prefix Cache Zone (A/B) · ${staticBlocks.size}"
                                },
                                accentColor = c.primarySoftInk,
                                backgroundColor = c.primarySoft,
                            )
                        }
                        itemsIndexed(
                            items = staticBlocks,
                            key = { _, item -> "blk_${item.originalIndex}_${item.block.identifier}" },
                        ) { pos, item ->
                            val upDelta = if (pos > 0) staticBlocks[pos - 1].originalIndex - item.originalIndex else null
                            val downDelta = if (pos < staticBlocks.lastIndex) staticBlocks[pos + 1].originalIndex - item.originalIndex else null
                            PresetEditorBlockCard(
                                item = item,
                                expanded = expandedOrigIdx == item.originalIndex,
                                reorderMode = reorderMode && canReorder,
                                canMoveUp = upDelta != null,
                                canMoveDown = downDelta != null,
                                isZh = isZh,
                                onToggleExpand = {
                                    expandedOrigIdx = if (expandedOrigIdx == item.originalIndex) null else item.originalIndex
                                },
                                onToggleEnabled = { nextEnabled ->
                                    onToggleBlock(activePreset.id, item.block.identifier, item.originalIndex, nextEnabled)
                                },
                                onMoveUp = {
                                    if (upDelta != null) onMoveBlock(activePreset.id, item.originalIndex, upDelta)
                                },
                                onMoveDown = {
                                    if (downDelta != null) onMoveBlock(activePreset.id, item.originalIndex, downDelta)
                                },
                            )
                        }
                    }

                    if (dynamicBlocks.isNotEmpty()) {
                        item(key = "zone_dynamic_header") {
                            if (staticBlocks.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                            }
                            PresetEditorZoneHeader(
                                text = if (isZh) {
                                    "🌊 动态注入区 (C/D) · ${dynamicBlocks.size}"
                                } else {
                                    "🌊 Dynamic Injection Zone (C/D) · ${dynamicBlocks.size}"
                                },
                                accentColor = c.accent,
                                backgroundColor = c.accentSoft,
                            )
                        }
                        itemsIndexed(
                            items = dynamicBlocks,
                            key = { _, item -> "blk_${item.originalIndex}_${item.block.identifier}" },
                        ) { pos, item ->
                            val upDelta = if (pos > 0) dynamicBlocks[pos - 1].originalIndex - item.originalIndex else null
                            val downDelta = if (pos < dynamicBlocks.lastIndex) dynamicBlocks[pos + 1].originalIndex - item.originalIndex else null
                            PresetEditorBlockCard(
                                item = item,
                                expanded = expandedOrigIdx == item.originalIndex,
                                reorderMode = reorderMode && canReorder,
                                canMoveUp = upDelta != null,
                                canMoveDown = downDelta != null,
                                isZh = isZh,
                                onToggleExpand = {
                                    expandedOrigIdx = if (expandedOrigIdx == item.originalIndex) null else item.originalIndex
                                },
                                onToggleEnabled = { nextEnabled ->
                                    onToggleBlock(activePreset.id, item.block.identifier, item.originalIndex, nextEnabled)
                                },
                                onMoveUp = {
                                    if (upDelta != null) onMoveBlock(activePreset.id, item.originalIndex, upDelta)
                                },
                                onMoveDown = {
                                    if (downDelta != null) onMoveBlock(activePreset.id, item.originalIndex, downDelta)
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // Regex scripts tab
            if (filteredRegex.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GuEmptyState(
                        title = if (isZh) "没有匹配的正则脚本" else "No matching regex scripts",
                        subtitle = if (trimmedQuery.isNotEmpty()) {
                            if (isZh) "尝试更换或清空搜索关键词" else "Try clearing the search query"
                        } else {
                            ""
                        },
                        icon = Icons.Default.Tune,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filteredRegex,
                        key = { (idx, rx) -> "rx_${idx}_${rx.id}" },
                    ) { (_, rx) ->
                        GuCard(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
                                        .alpha(if (rx.enabled) 1f else 0.60f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = rx.name.ifBlank { rx.findRegex.take(28) },
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        GuBadge(
                                            text = rx.placement.ifBlank { "ai_output" },
                                            variant = GuBadgeVariant.EMERALD,
                                        )
                                    }
                                    Text(
                                        text = "/${rx.findRegex}/",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = rx.enabled,
                                    enabled = onToggleRegex != null,
                                    onCheckedChange = { next -> onToggleRegex?.invoke(activePreset.id, rx.id, next) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = c.primary,
                                        uncheckedThumbColor = c.muted,
                                        uncheckedTrackColor = c.surfaceSoft,
                                    ),
                                    modifier = Modifier.graphicsLayer { scaleX = 0.84f; scaleY = 0.84f },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetEditorZoneHeader(
    text: String,
    accentColor: Color,
    backgroundColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PresetEditorBlockCard(
    item: IndexedPresetBlock,
    expanded: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isZh: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    val block = item.block
    val estTokens = remember(block.marker, block.content) {
        PromptPresetBlockOps.estimateBlockTokens(block)
    }
    val roleShort = when {
        block.role.equals("system", ignoreCase = true) -> "SYS"
        block.role.equals("user", ignoreCase = true) -> "USR"
        block.role.equals("assistant", ignoreCase = true) -> "AST"
        else -> block.role.uppercase().take(4).ifBlank { "SYS" }
    }
    val roleVariant = when {
        block.role.equals("assistant", ignoreCase = true) -> GuBadgeVariant.AMBER
        block.role.equals("user", ignoreCase = true) -> GuBadgeVariant.EMERALD
        else -> GuBadgeVariant.BLUE
    }

    GuCard(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (block.enabled) 1f else 0.60f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GuBadge(text = roleShort, variant = roleVariant)
                        if (block.marker) {
                            GuBadge(
                                text = if (isZh) "占位符" else "Marker",
                                variant = GuBadgeVariant.NEUTRAL,
                            )
                        }
                        if (block.injectionPosition == 1) {
                            GuBadge(
                                text = "@D${block.injectionDepth}",
                                variant = GuBadgeVariant.BLUE,
                            )
                        }
                        Text(
                            text = block.name.ifBlank { block.identifier.ifBlank { "#${item.originalIndex + 1}" } },
                            color = c.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    val meta = buildList {
                        if (block.identifier.isNotBlank()) add(block.identifier)
                        if (!block.marker && estTokens > 0) add("~${FileContextBudgeter.formatShortTokens(estTokens)}")
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        GuMetaText(text = meta, color = c.muted)
                    }
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
                    Switch(
                        checked = block.enabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.muted,
                            uncheckedTrackColor = c.surfaceSoft,
                        ),
                        modifier = Modifier.graphicsLayer { scaleX = 0.84f; scaleY = 0.84f },
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = block.content.ifBlank {
                            if (isZh) "(系统占位符槽位，发送时自动替换)" else "(Dynamic system marker slot)"
                        },
                        color = c.ink.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 17.5.sp,
                    )
                }
            }
        }
    }
}
