package com.gugachat.app.ui.studio

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCollapsibleSearchBar
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuPushPopContainer
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresetTemplate
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.WorldbookEngine
import com.gugachat.app.ui.images.ImageViewModel
import kotlinx.coroutines.launch
import java.io.File

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
    val c = LocalAppTheme.current.colors
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

    val activeWorldbook = remember(ui.activeWorldbookId, ui.worldbooks) {
        ui.activeWorldbookId?.let { id -> ui.worldbooks.firstOrNull { it.id == id } }
    }
    val activePreset = remember(ui.activePresetId, ui.promptPresets) {
        ui.activePresetId?.let { id -> ui.promptPresets.firstOrNull { it.id == id } }
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

    if (activeWorldbook != null) {
        WorldbookDetailSheet(
            worldbook = activeWorldbook,
            entries = ui.activeWorldbookEntries,
            vectorReady = ui.worldbookVectorReady[activeWorldbook.id] == true,
            isIndexing = ui.vectorIndexProgress.running,
            onToggleGlobal = { assetHubViewModel.toggleWorldbookGlobalEnabled(activeWorldbook.id, it) },
            onUpdateMeta = { name, desc, scanDepth, tokenBudget, recursive ->
                assetHubViewModel.updateWorldbookMeta(
                    activeWorldbook.id,
                    name,
                    desc,
                    scanDepth,
                    tokenBudget,
                    recursive,
                )
            },
            onReindex = { assetHubViewModel.reindexWorldbook(activeWorldbook.id) },
            onDeleteBook = { assetHubViewModel.deleteWorldbook(activeWorldbook.id) },
            onToggleEntryEnabled = { entry, enabled ->
                assetHubViewModel.toggleWorldbookEntryEnabled(entry, enabled)
            },
            onSaveEntry = { entryId, title, content, keys, secKeys, logic, constant, enabled, pos, depth, sticky, cooldown ->
                assetHubViewModel.saveWorldbookEntry(
                    worldbookId = activeWorldbook.id,
                    entryId = entryId,
                    title = title,
                    content = content,
                    primaryKeys = keys,
                    secondaryKeys = secKeys,
                    selectiveLogic = logic,
                    constant = constant,
                    enabled = enabled,
                    position = pos,
                    depth = depth,
                    sticky = sticky,
                    cooldown = cooldown,
                )
            },
            onDeleteEntry = { assetHubViewModel.deleteWorldbookEntry(it) },
            onDismiss = { assetHubViewModel.openWorldbookDetail(null) },
        )
    }

    if (activePreset != null) {
        PromptPresetDetailSheet(
            preset = activePreset,
            onToggleDefault = { assetHubViewModel.setDefaultRpPreset(activePreset.id, it) },
            onToggleBlock = { identifier, enabled ->
                assetHubViewModel.togglePresetBlockEnabled(activePreset.id, identifier, enabled)
            },
            onToggleRegex = { scriptId, enabled ->
                assetHubViewModel.togglePresetRegexEnabled(activePreset.id, scriptId, enabled)
            },
            onDeletePreset = { assetHubViewModel.deletePromptPreset(activePreset.id) },
            onDismiss = { assetHubViewModel.openPresetDetail(null) },
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptPresetDetailSheet(
    preset: PromptPresetEntity,
    onToggleDefault: (Boolean) -> Unit,
    onToggleBlock: (identifier: String, enabled: Boolean) -> Unit,
    onToggleRegex: (scriptId: String, enabled: Boolean) -> Unit,
    onDeletePreset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val blocks = remember(preset.blocksJson) { decodeJsonList<PromptPresetBlock>(preset.blocksJson) }
    val regexScripts = remember(preset.regexScriptsJson) { decodeJsonList<RpRegexScript>(preset.regexScriptsJson) }
    var expandedBlockId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        GuConfirmDialog(
            title = if (isZh) "删除设定预设" else "Delete Preset",
            message = if (isZh) "确认删除「${preset.name}」？" else "Delete \"${preset.name}\"?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                onDeletePreset()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = preset.name,
                subtitle = "${preset.sourceFormat} · ${blocks.size} blocks · ${regexScripts.size} regex",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
                trailing = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger)
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preset.sourceFormat != "sillytavern_regex") {
                    item(key = "preset_default_switch") {
                        GuCard(backgroundColor = c.surfaceSoft) {
                            GuSwitchRow(
                                label = if (isZh) "设为新周目默认预设" else "Default for New RP Campaigns",
                                checked = preset.isDefaultRp,
                                onCheckedChange = onToggleDefault,
                            )
                        }
                    }
                }

                if (blocks.isNotEmpty()) {
                    item(key = "blocks_header") {
                        Text(
                            text = if (isZh) "提示词模块 (${blocks.count { it.enabled }}/${blocks.size})" else "Prompt Blocks",
                            color = c.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(blocks, key = { "blk_${it.identifier}" }) { blk ->
                        val isExpanded = expandedBlockId == blk.identifier
                        GuCard(
                            onClick = { expandedBlockId = if (isExpanded) null else blk.identifier },
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = blk.name.ifBlank { blk.identifier },
                                            color = c.ink,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${blk.role} · pos=${blk.injectionPosition} @D${blk.injectionDepth}",
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                        )
                                    }
                                    Switch(
                                        checked = blk.enabled,
                                        onCheckedChange = { onToggleBlock(blk.identifier, it) },
                                    )
                                }
                                if (isExpanded && blk.content.isNotBlank()) {
                                    Text(
                                        text = blk.content,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (regexScripts.isNotEmpty()) {
                    item(key = "regex_header") {
                        Text(
                            text = if (isZh) "正则脚本 (${regexScripts.count { it.enabled }}/${regexScripts.size})" else "Regex Scripts",
                            color = c.ink,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(regexScripts, key = { "rx_${it.id}" }) { rx ->
                        GuCard(contentPadding = PaddingValues(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rx.name.ifBlank { rx.findRegex.take(28) },
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${rx.placement} · /${rx.findRegex.take(40)}/",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = rx.enabled,
                                    onCheckedChange = { onToggleRegex(rx.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
