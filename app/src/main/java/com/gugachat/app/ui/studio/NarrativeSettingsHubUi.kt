package com.gugachat.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCollapsibleSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpRegexScript

/**
 * Worldbook, Prompt Preset & Regex asset management tab (`设定`) inside [CreativeStudioScreen].
 */
@Composable
fun LoreSettingsHubTab(
    assetHubViewModel: AssetHubViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearchBar: Boolean,
    onCloseSearchBar: () -> Unit,
    onRequestImportSetting: () -> Unit,
    onScrollStateChange: (Boolean) -> Unit,
) {
    val ui by assetHubViewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH

    var activeFilter by rememberSaveable { mutableStateOf(SettingFilterKind.ALL) }
    var showCreateWorldbookDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6
        }
    }
    LaunchedEffect(isScrolled) { onScrollStateChange(isScrolled) }

    val cleanSearch = searchQuery.trim().lowercase()

    val filteredWorldbooks = remember(ui.worldbooks, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.WORLDBOOK) {
            emptyList()
        } else {
            ui.worldbooks.filter { wb ->
                cleanSearch.isEmpty() ||
                    wb.name.lowercase().contains(cleanSearch) ||
                    wb.description.lowercase().contains(cleanSearch)
            }
        }
    }

    val filteredPresets = remember(ui.promptPresets, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.PRESET) {
            emptyList()
        } else {
            ui.promptPresets.filter { preset ->
                preset.sourceFormat != "sillytavern_regex" &&
                    (cleanSearch.isEmpty() || preset.name.lowercase().contains(cleanSearch))
            }
        }
    }

    val regexPresets = remember(ui.promptPresets, activeFilter, cleanSearch) {
        if (activeFilter != SettingFilterKind.ALL && activeFilter != SettingFilterKind.REGEX) {
            emptyList()
        } else {
            ui.promptPresets.filter { preset ->
                val hasRegex = decodeJsonList<RpRegexScript>(preset.regexScriptsJson).isNotEmpty()
                (preset.sourceFormat == "sillytavern_regex" || (activeFilter == SettingFilterKind.REGEX && hasRegex)) &&
                    (cleanSearch.isEmpty() || preset.name.lowercase().contains(cleanSearch))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Single-row 32dp borderless pills: 全部 · 世界书 · 提示词预设 · 正则
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CharacterFilterPill(
                label = if (isZh) "全部" else "All",
                selected = activeFilter == SettingFilterKind.ALL,
                onClick = { activeFilter = SettingFilterKind.ALL },
            )
            CharacterFilterPill(
                label = if (isZh) "世界书" else "Worldbooks",
                selected = activeFilter == SettingFilterKind.WORLDBOOK,
                onClick = { activeFilter = SettingFilterKind.WORLDBOOK },
            )
            CharacterFilterPill(
                label = if (isZh) "提示词预设" else "Presets",
                selected = activeFilter == SettingFilterKind.PRESET,
                onClick = { activeFilter = SettingFilterKind.PRESET },
            )
            CharacterFilterPill(
                label = if (isZh) "正则" else "Regex",
                selected = activeFilter == SettingFilterKind.REGEX,
                onClick = { activeFilter = SettingFilterKind.REGEX },
            )
        }

        GuCollapsibleSearchBar(
            visible = showSearchBar,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = if (isZh) "搜索世界书、预设或正则..." else "Search worldbooks or presets...",
            onClose = onCloseSearchBar,
            clearContentDescription = strings.common.clear,
            closeText = strings.common.close,
            useTextCloseButton = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item(key = "settings_quick_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuButton(
                        onClick = onRequestImportSetting,
                        variant = GuButtonVariant.SECONDARY,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isZh) "导入设定" else "Import JSON", fontSize = 12.sp, maxLines = 1)
                    }
                    GuButton(
                        onClick = { showCreateWorldbookDialog = true },
                        variant = GuButtonVariant.SECONDARY,
                        compact = true,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (isZh) "新建世界书" else "New Book", fontSize = 12.sp, maxLines = 1)
                    }
                    GuButton(
                        onClick = { assetHubViewModel.harvestLegacyNovelLoreToWorldbooks() },
                        variant = GuButtonVariant.HONEY,
                        compact = true,
                        enabled = !ui.isBusy,
                    ) {
                        Text(
                            text = if (ui.legacyLoreSessionCount > 0) {
                                if (isZh) "提炼旧设定(${ui.legacyLoreSessionCount})" else "Extract(${ui.legacyLoreSessionCount})"
                            } else {
                                if (isZh) "从旧会话提炼设定" else "Extract Legacy"
                            },
                            fontSize = 11.5.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (filteredWorldbooks.isEmpty() && filteredPresets.isEmpty() && regexPresets.isEmpty()) {
                item(key = "settings_empty") {
                    GuEmptyState(
                        title = if (isZh) "设定库暂无内容" else "No assets in library",
                        subtitle = if (isZh) "支持导入 SillyTavern 世界书、Chat 预设、RikkaHub 设定与正则脚本"
                        else "Import Tavern worldbooks, chat presets, RikkaHub lore or regex scripts",
                        actionLabel = if (isZh) "导入设定文件" else "Import Setting",
                        onAction = onRequestImportSetting,
                        icon = Icons.Default.Layers,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            items(filteredWorldbooks, key = { "wb_${it.id}" }) { wb ->
                val vectorReady = ui.worldbookVectorReady[wb.id] == true
                val isIndexing = ui.vectorIndexProgress.running
                WorldbookSummaryCard(
                    worldbook = wb,
                    vectorReady = vectorReady,
                    isIndexing = isIndexing,
                    isZh = isZh,
                    onToggleGlobal = { enabled ->
                        assetHubViewModel.toggleWorldbookGlobalEnabled(wb.id, enabled)
                    },
                    onOpenDetail = { assetHubViewModel.openWorldbookDetail(wb.id) },
                )
            }

            items(filteredPresets, key = { "pr_${it.id}" }) { preset ->
                val blocks = remember(preset.blocksJson) { decodeJsonList<PromptPresetBlock>(preset.blocksJson) }
                val regexes = remember(preset.regexScriptsJson) { decodeJsonList<RpRegexScript>(preset.regexScriptsJson) }
                PresetSummaryCard(
                    preset = preset,
                    blocksCount = blocks.size,
                    enabledBlocksCount = blocks.count { it.enabled },
                    regexCount = regexes.size,
                    isZh = isZh,
                    onToggleDefault = { enabled ->
                        assetHubViewModel.setDefaultRpPreset(preset.id, enabled)
                    },
                    onOpenDetail = { assetHubViewModel.openPresetDetail(preset.id) },
                )
            }

            items(regexPresets, key = { "rx_${it.id}" }) { pack ->
                val scripts = remember(pack.regexScriptsJson) { decodeJsonList<RpRegexScript>(pack.regexScriptsJson) }
                RegexPackSummaryCard(
                    preset = pack,
                    scripts = scripts,
                    isZh = isZh,
                    onOpenDetail = { assetHubViewModel.openPresetDetail(pack.id) },
                )
            }
        }
    }

    if (showCreateWorldbookDialog) {
        CreateWorldbookSheet(
            onCreate = { name, desc ->
                assetHubViewModel.createBlankWorldbook(name, desc)
                showCreateWorldbookDialog = false
            },
            onDismiss = { showCreateWorldbookDialog = false },
        )
    }
}

@Composable
internal fun WorldbookSummaryCard(
    worldbook: WorldbookEntity,
    vectorReady: Boolean,
    isIndexing: Boolean,
    isZh: Boolean,
    onToggleGlobal: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Book,
                    tint = c.tagInk,
                    backgroundColor = c.tag,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worldbook.name.ifBlank { if (isZh) "未命名世界书" else "Untitled Worldbook" },
                        color = c.ink,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val statusDotColor = when {
                    isIndexing -> c.warning
                    vectorReady -> c.success
                    else -> c.muted.copy(alpha = 0.45f)
                }
                val statusLabel = when {
                    isIndexing -> if (isZh) "◌ 索引中" else "Indexing"
                    vectorReady -> if (isZh) "● 向量就绪" else "Vector Ready"
                    else -> if (isZh) "词法检索" else "Lexical"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Text(
                        text = statusLabel,
                        color = if (vectorReady) c.success else c.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Switch(
                    checked = worldbook.globalEnabled,
                    onCheckedChange = onToggleGlobal,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isZh) {
                        "${worldbook.entryCount} 条词条 · ~${worldbook.totalEstimatedTokens} tokens"
                    } else {
                        "${worldbook.entryCount} entries · ~${worldbook.totalEstimatedTokens} tokens"
                    },
                    color = c.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isZh) "全局启用 · 点按编辑 ▸" else "Global · Edit ▸",
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun PresetSummaryCard(
    preset: PromptPresetEntity,
    blocksCount: Int,
    enabledBlocksCount: Int,
    regexCount: Int,
    isZh: Boolean,
    onToggleDefault: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Tune,
                    tint = c.primarySoftInk,
                    backgroundColor = c.primarySoft,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Text(
                    text = preset.name.ifBlank { if (isZh) "未命名预设" else "Untitled Preset" },
                    color = c.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (preset.isDefaultRp) {
                    GuBadge(text = if (isZh) "默认 RP" else "Default", variant = GuBadgeVariant.BLUE)
                }
                Switch(
                    checked = preset.isDefaultRp,
                    onCheckedChange = onToggleDefault,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        append(if (isZh) "$enabledBlocksCount/$blocksCount 模块启用" else "$enabledBlocksCount/$blocksCount blocks active")
                        if (regexCount > 0) {
                            append(if (isZh) " · $regexCount 正则" else " · $regexCount regex")
                        }
                    },
                    color = c.muted,
                    fontSize = 12.sp,
                )
                Text(
                    text = if (isZh) "模块预览 ▸" else "Blocks ▸",
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun RegexPackSummaryCard(
    preset: PromptPresetEntity,
    scripts: List<RpRegexScript>,
    isZh: Boolean,
    onOpenDetail: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        onClick = onOpenDetail,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.Code,
                    tint = c.success,
                    backgroundColor = c.successBg,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
                Text(
                    text = preset.name,
                    color = c.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GuBadge(text = if (isZh) "正则脚本" else "Regex", variant = GuBadgeVariant.EMERALD)
            }
            Text(
                text = if (isZh) {
                    "${scripts.count { it.enabled }}/${scripts.size} 条规则生效 · 点按管理"
                } else {
                    "${scripts.count { it.enabled }}/${scripts.size} scripts enabled · Tap to manage"
                },
                color = c.muted,
                fontSize = 12.sp,
            )
        }
    }
}
