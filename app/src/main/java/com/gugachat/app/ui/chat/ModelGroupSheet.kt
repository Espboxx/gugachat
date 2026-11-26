package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCompactSearchBar
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSelectableListRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.FreeQuotaUtils
import java.util.Locale

private val CONTEXT_WINDOW_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "AUTO",
    8_000 to "8K",
    16_000 to "16K",
    32_000 to "32K",
    64_000 to "64K",
    128_000 to "128K",
    200_000 to "200K",
    512_000 to "512K",
    1_000_000 to "1M",
)

private val HISTORY_MESSAGE_PRESETS: List<Int?> = listOf(null, 6, 12, 20, 40, 80)

private val MAX_OUTPUT_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "AUTO",
    2048 to "2K",
    4096 to "4K",
    8192 to "8K",
    16384 to "16K",
    32768 to "32K",
)

internal fun formatContextTokenBadge(tokens: Int): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000f)
    tokens >= 1000 -> "${tokens / 1000}K"
    else -> "${tokens}t"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelGroupSheet(
    groups: List<OnlineChatGroupDto>,
    models: List<OnlineChatModelDto>,
    selectedGroupId: Long?,
    selectedModelId: String?,
    params: ChatParams = ChatParams(),
    waterline: ContextWaterlineSummary = ContextWaterlineSummary(),
    onSelectGroup: (Long) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectCustomModel: (String) -> Unit = onSelectModel,
    onUpdateContextSettings: ((contextWindowOverride: Int?, maxHistoryMessages: Int?, maxTokens: Int?) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var groupSearch by remember { mutableStateOf("") }
    var showGroupSearch by remember { mutableStateOf(false) }
    var showContextSettings by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    var capFilterIndex by remember { mutableIntStateOf(0) } // 0=All, 1=Free, 2=Vision, 3=Reasoning

    val capFilterLabels = remember(isZh, strings) {
        listOf(
            strings.novel.filterAll,
            if (isZh) "限免" else "Free",
            strings.chat.visionCap,
            strings.chat.reasoningCap,
        )
    }

    val filteredGroups = remember(groups, groupSearch, strings.providers.protocolShortNames) {
        FuzzyMatcher.filterAndRank(groups, groupSearch) { g ->
            val protoTag = if (ApiProviderConfig.isCustomProviderId(g.id)) {
                strings.providers.protocolShortNames[g.platform].orEmpty()
            } else {
                ""
            }
            listOf(
                g.name,
                g.platform,
                protoTag,
                if (g.has_free_quotas) "限免 free" else "",
                if (g.rate_multiplier != 1.0) "${g.rate_multiplier}x" else "",
            )
        }
    }

    val filteredModels = remember(models, modelSearch, capFilterIndex) {
        val byCap = when (capFilterIndex) {
            1 -> models.filter { FreeQuotaUtils.hasAvailableFreeQuota(it.free_quota) || it.free_quota != null }
            2 -> models.filter { it.supports_vision }
            3 -> models.filter { it.supports_reasoning }
            else -> models
        }
        FuzzyMatcher.filterAndRank(byCap, modelSearch) { m ->
            listOf(
                m.id,
                if (m.supports_vision) "视觉 vision" else "",
                if (m.supports_reasoning) "思考 reasoning" else "",
                if (m.free_quota != null) "限免 free" else "",
            )
        }
    }

    val activeGroup = remember(groups, selectedGroupId) {
        groups.firstOrNull { it.id == selectedGroupId }
    }
    val trimmedModelSearch = modelSearch.trim()
    val showCustomAction = trimmedModelSearch.isNotEmpty() &&
        models.none { it.id.equals(trimmedModelSearch, ignoreCase = true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.selectModel,
                subtitle = activeGroup?.name,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            // Provider / Group channel strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuSectionHeader(
                    title = strings.chat.selectGroup,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GuBadge(
                                text = if (groupSearch.isNotBlank()) "${filteredGroups.size}/${groups.size}" else "${groups.size}",
                                variant = GuBadgeVariant.NEUTRAL,
                            )
                            if (groups.size > 4) {
                                IconButton(
                                    onClick = {
                                        showGroupSearch = !showGroupSearch
                                        if (!showGroupSearch) groupSearch = ""
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = strings.chat.searchGroups,
                                        tint = if (showGroupSearch || groupSearch.isNotBlank()) c.primary else c.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                )

                AnimatedVisibility(visible = showGroupSearch) {
                    GuCompactSearchBar(
                        value = groupSearch,
                        onValueChange = { groupSearch = it },
                        placeholder = strings.chat.searchGroups,
                        clearContentDescription = strings.common.clear,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (filteredGroups.isEmpty()) {
                    Text(
                        text = if (groups.isEmpty()) strings.chat.noGroups else strings.chat.noMatchingGroups,
                        color = c.muted,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredGroups, key = { it.id }) { g ->
                            val selected = g.id == selectedGroupId
                            val protoTag = if (ApiProviderConfig.isCustomProviderId(g.id)) {
                                strings.providers.protocolShortNames[g.platform]
                            } else {
                                null
                            }
                            Surface(
                                onClick = {
                                    onSelectGroup(g.id)
                                    modelSearch = ""
                                    capFilterIndex = 0
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) c.primary else c.surface,
                                border = BorderStroke(theme.borderWidth, if (selected) c.primary else c.border),
                                modifier = Modifier.heightIn(min = 38.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (g.has_free_quotas) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (selected) Color.White else c.success),
                                        )
                                    }
                                    Text(
                                        text = g.name,
                                        color = if (selected) Color.White else c.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    if (protoTag != null) {
                                        Text(
                                            text = protoTag,
                                            color = if (selected) Color.White.copy(alpha = 0.86f) else c.primarySoftInk,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (g.rate_multiplier != 1.0) {
                                        Text(
                                            text = "${g.rate_multiplier}x",
                                            color = if (selected) Color.White.copy(alpha = 0.86f) else c.accent,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.75f),
            )

            if (onUpdateContextSettings != null) {
                val activeModelWindow = remember(models, selectedModelId) {
                    models.firstOrNull { it.id == selectedModelId }?.context_window ?: 128_000
                }
                ModelContextSettingsSection(
                    expanded = showContextSettings,
                    onToggleExpand = { showContextSettings = !showContextSettings },
                    activeModelId = selectedModelId,
                    nativeModelWindow = activeModelWindow,
                    params = params,
                    waterline = waterline,
                    isZh = isZh,
                    onUpdate = onUpdateContextSettings,
                )

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.75f),
                )
            }

            // Full-height searchable model picker
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuSectionHeader(
                    title = strings.chat.model,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!selectedModelId.isNullOrBlank()) {
                                GuBadge(
                                    text = selectedModelId,
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                            GuBadge(
                                text = if (modelSearch.isNotBlank() || capFilterIndex != 0) {
                                    "${filteredModels.size}/${models.size}"
                                } else {
                                    "${models.size}"
                                },
                                variant = if (filteredModels.isEmpty() && models.isNotEmpty()) {
                                    GuBadgeVariant.AMBER
                                } else {
                                    GuBadgeVariant.NEUTRAL
                                },
                            )
                        }
                    },
                )

                GuCompactSearchBar(
                    value = modelSearch,
                    onValueChange = { modelSearch = it },
                    placeholder = strings.chat.searchModels,
                    clearContentDescription = strings.common.clear,
                )

                if (models.isNotEmpty()) {
                    GuSegmentedControl(
                        items = capFilterLabels,
                        selectedIndex = capFilterIndex,
                        onSelect = { capFilterIndex = it },
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (filteredModels.isEmpty() && !showCustomAction) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GuEmptyState(
                            title = if (models.isEmpty()) strings.chat.noModels else strings.chat.noMatchingModels,
                            icon = Icons.Default.Search,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showCustomAction) {
                            item(key = "__custom_model__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.primarySoft)
                                        .border(theme.borderWidth, c.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectCustomModel(trimmedModelSearch)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    GuIconTile(
                                        icon = Icons.Default.Add,
                                        tint = Color.White,
                                        backgroundColor = c.primary,
                                        size = 30.dp,
                                        iconSize = 17.dp,
                                        cornerRadius = 8.dp,
                                    )
                                    Text(
                                        text = strings.providers.useCustomModelFormat.fmt("model" to trimmedModelSearch),
                                        color = c.primarySoftInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        items(filteredModels, key = { it.id }) { model ->
                            val quotaBadge = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh = isZh)
                            GuSelectableListRow(
                                title = model.id,
                                selected = model.id == selectedModelId,
                                highlightFree = false,
                                onClick = {
                                    onSelectModel(model.id)
                                    onDismiss()
                                },
                                badges = {
                                    if (quotaBadge != null) {
                                        GuBadge(
                                            text = quotaBadge,
                                            variant = if (FreeQuotaUtils.hasAvailableFreeQuota(model.free_quota)) {
                                                GuBadgeVariant.EMERALD
                                            } else {
                                                GuBadgeVariant.NEUTRAL
                                            },
                                        )
                                    }
                                    if (model.supports_vision) {
                                        GuBadge(strings.chat.visionCap, variant = GuBadgeVariant.BLUE)
                                    }
                                    if (model.supports_reasoning) {
                                        GuBadge(strings.chat.reasoningCap, variant = GuBadgeVariant.HONEY)
                                    }
                                    GuBadge(formatContextTokenBadge(model.context_window), variant = GuBadgeVariant.NEUTRAL)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelContextSettingsSection(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    activeModelId: String?,
    nativeModelWindow: Int,
    params: ChatParams,
    waterline: ContextWaterlineSummary,
    isZh: Boolean,
    onUpdate: (contextWindowOverride: Int?, maxHistoryMessages: Int?, maxTokens: Int?) -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val hasAnyCustom = params.contextWindowOverride != null ||
        params.maxHistoryMessages != null ||
        params.maxTokens != null

    val effectiveWindow = params.contextWindowOverride ?: nativeModelWindow
    val windowBadgeText = if (params.contextWindowOverride != null) {
        formatContextTokenBadge(params.contextWindowOverride)
    } else {
        if (isZh) "自动 ${formatContextTokenBadge(nativeModelWindow)}" else "Auto ${formatContextTokenBadge(nativeModelWindow)}"
    }
    val historyBadgeText = params.maxHistoryMessages?.let { n ->
        if (isZh) "近 $n 条" else "Last $n"
    } ?: if (isZh) "不限条数" else "All msgs"

    var customWindowKText by remember {
        mutableStateOf(params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty())
    }
    var customHistoryText by remember {
        mutableStateOf(params.maxHistoryMessages?.toString().orEmpty())
    }

    LaunchedEffect(params.contextWindowOverride) {
        val expected = params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty()
        if (customWindowKText != expected) {
            customWindowKText = expected
        }
    }
    LaunchedEffect(params.maxHistoryMessages) {
        val expected = params.maxHistoryMessages?.toString().orEmpty()
        if (customHistoryText != expected) {
            customHistoryText = expected
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Compact 1-line summary header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuIconTile(
                icon = Icons.Default.Tune,
                tint = if (hasAnyCustom) c.primarySoftInk else c.muted,
                backgroundColor = if (hasAnyCustom) c.primarySoft else c.surfaceSoft,
                size = 28.dp,
                iconSize = 15.dp,
                cornerRadius = 8.dp,
            )
            Text(
                text = if (isZh) "上下文设置" else "Context Settings",
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuBadge(
                    text = windowBadgeText,
                    variant = if (params.contextWindowOverride != null) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL,
                )
                GuBadge(
                    text = historyBadgeText,
                    variant = if (params.maxHistoryMessages != null) GuBadgeVariant.BLUE else GuBadgeVariant.NEUTRAL,
                )
                if (params.maxTokens != null) {
                    GuBadge(
                        text = if (isZh) "输出 ${formatContextTokenBadge(params.maxTokens)}" else "Out ${formatContextTokenBadge(params.maxTokens)}",
                        variant = GuBadgeVariant.HONEY,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 365.dp)
                    .verticalScroll(rememberScrollState())
                    .background(c.surfaceSoft.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Live waterline & model window status + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val usedCompact = if (waterline.usedTokens >= 1000) {
                        String.format(Locale.US, "%.1fK", waterline.usedTokens / 1000f)
                    } else {
                        "${waterline.usedTokens}"
                    }
                    val budgetCompact = formatContextTokenBadge(waterline.effectiveLimitTokens.coerceAtLeast(1024))
                    val modelLabel = activeModelId?.take(22).orEmpty().ifBlank { if (isZh) "当前模型" else "Model" }
                    Text(
                        text = if (isZh) {
                            "$modelLabel 原生 ${formatContextTokenBadge(nativeModelWindow)} · 已用 $usedCompact / $budgetCompact (${waterline.percentage}%)"
                        } else {
                            "$modelLabel native ${formatContextTokenBadge(nativeModelWindow)} · Used $usedCompact / $budgetCompact (${waterline.percentage}%)"
                        },
                        color = if (waterline.exceedsModelWindow) c.danger else c.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasAnyCustom) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onUpdate(null, null, null) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (isZh) "重置默认" else "Reset",
                                color = c.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // 1. Context Window Override
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isZh) "上下文窗口上限" else "Context Window Limit",
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (params.contextWindowOverride == null) {
                                if (isZh) "跟随模型 (${formatContextTokenBadge(effectiveWindow)})" else "Model (${formatContextTokenBadge(effectiveWindow)})"
                            } else {
                                formatContextTokenBadge(effectiveWindow)
                            },
                            color = if (params.contextWindowOverride != null) c.primarySoftInk else c.muted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(CONTEXT_WINDOW_PRESETS, key = { it.second }) { (tokens, label) ->
                            val selected = params.contextWindowOverride == tokens
                            val displayLabel = if (tokens == null) {
                                if (isZh) "自动" else "Auto"
                            } else {
                                label
                            }
                            GuPill(
                                label = displayLabel,
                                selected = selected,
                                onClick = {
                                    onUpdate(tokens, params.maxHistoryMessages, params.maxTokens)
                                },
                            )
                        }
                    }
                }

                // 2. Attached History Messages Count
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isZh) "附带历史消息数" else "Attached History Messages",
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = params.maxHistoryMessages?.let {
                                if (isZh) "最近 $it 条" else "Last $it"
                            } ?: if (isZh) "不限（按 Token 裁剪）" else "Unlimited (token budget)",
                            color = if (params.maxHistoryMessages != null) c.primarySoftInk else c.muted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(HISTORY_MESSAGE_PRESETS, key = { it ?: -1 }) { count ->
                            val selected = params.maxHistoryMessages == count
                            val label = count?.let { if (isZh) "$it 条" else "$it" }
                                ?: if (isZh) "不限" else "All"
                            GuPill(
                                label = label,
                                selected = selected,
                                onClick = {
                                    onUpdate(params.contextWindowOverride, count, params.maxTokens)
                                },
                            )
                        }
                    }
                }

                // 3. Max Output Tokens
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isZh) "单次最大输出" else "Max Output Tokens",
                            color = c.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = params.maxTokens?.let { formatContextTokenBadge(it) }
                                ?: if (isZh) "模型默认" else "Default",
                            color = if (params.maxTokens != null) c.primarySoftInk else c.muted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(MAX_OUTPUT_PRESETS, key = { it.second }) { (tokens, label) ->
                            val selected = params.maxTokens == tokens
                            val displayLabel = if (tokens == null) {
                                if (isZh) "默认" else "Auto"
                            } else {
                                label
                            }
                            GuPill(
                                label = displayLabel,
                                selected = selected,
                                onClick = {
                                    onUpdate(params.contextWindowOverride, params.maxHistoryMessages, tokens)
                                },
                            )
                        }
                    }
                }

                // 4. Custom numeric inputs row (Custom Window K + Custom Message Count)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuTextField(
                        value = customWindowKText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            customWindowKText = digits
                            if (digits.isEmpty()) {
                                onUpdate(null, params.maxHistoryMessages, params.maxTokens)
                            } else {
                                val kVal = digits.toIntOrNull()
                                if (kVal != null && kVal >= 4) {
                                    onUpdate(
                                        (kVal * 1000).coerceIn(4096, 2_000_000),
                                        params.maxHistoryMessages,
                                        params.maxTokens,
                                    )
                                }
                            }
                        },
                        placeholder = if (isZh) "自定义上限 (K)" else "Custom K",
                        trailingIcon = {
                            Text(
                                text = "K",
                                color = c.muted,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    GuTextField(
                        value = customHistoryText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            customHistoryText = digits
                            if (digits.isEmpty()) {
                                onUpdate(params.contextWindowOverride, null, params.maxTokens)
                            } else {
                                val n = digits.toIntOrNull()
                                if (n != null && n > 0) {
                                    onUpdate(
                                        params.contextWindowOverride,
                                        n.coerceIn(1, 500),
                                        params.maxTokens,
                                    )
                                }
                            }
                        },
                        placeholder = if (isZh) "自定义消息条数" else "Custom msgs",
                        trailingIcon = {
                            Text(
                                text = if (isZh) "条" else "#",
                                color = c.muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
