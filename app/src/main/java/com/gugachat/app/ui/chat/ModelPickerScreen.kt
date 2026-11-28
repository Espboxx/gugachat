package com.gugachat.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModelAvatar
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.FreeQuotaUtils
import java.util.Locale

private val CONTEXT_WINDOW_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "AUTO",
    16_000 to "16K",
    32_000 to "32K",
    64_000 to "64K",
    128_000 to "128K",
    200_000 to "200K",
    512_000 to "512K",
    1_000_000 to "1M",
)

private val HISTORY_MESSAGE_PRESETS: List<Int?> = listOf(null, 10, 20, 40, 80)

private val MAX_OUTPUT_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "AUTO",
    2048 to "2K",
    4096 to "4K",
    8192 to "8K",
    16384 to "16K",
)

internal fun formatContextTokenBadge(tokens: Int): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000f)
    tokens >= 1000 -> "${tokens / 1000}K"
    else -> "${tokens}t"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelPickerScreen(
    groups: List<OnlineChatGroupDto>,
    models: List<OnlineChatModelDto>,
    selectedGroupId: Long?,
    selectedModelId: String?,
    params: ChatParams = ChatParams(),
    waterline: ContextWaterlineSummary = ContextWaterlineSummary(),
    isLoadingModels: Boolean = false,
    onSelectGroup: (Long) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectCustomModel: (String) -> Unit = onSelectModel,
    onUpdateContextSettings: ((contextWindowOverride: Int?, maxHistoryMessages: Int?, maxTokens: Int?) -> Unit)? = null,
    mode: ConversationMode = ConversationMode.chat,
    onUpdateMinOutput: ((minChars: Int?, autoContinue: Boolean) -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var searchQuery by remember { mutableStateOf("") }
    var capFilterIndex by remember { mutableIntStateOf(0) } // 0=All, 1=Free, 2=Vision, 3=Reasoning
    var showContextSettings by remember { mutableStateOf(false) }
    var groupsExpanded by remember { mutableStateOf(false) }
    var hasInitialGroupAligned by remember { mutableStateOf(false) }

    val groupListState = rememberLazyListState()
    val modelListState = rememberLazyListState()
    var lastScrolledContextKey by remember { mutableStateOf<Pair<Long?, String?>?>(null) }

    val capFilterLabels = remember(isZh, strings) {
        listOf(
            strings.novel.filterAll,
            if (isZh) "限免" else "Free",
            strings.chat.visionCap,
            strings.chat.reasoningCap,
        )
    }

    val groupFieldsProvider = remember(strings.providers.protocolShortNames) {
        { g: OnlineChatGroupDto ->
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

    val matchedGroups = remember(groups, searchQuery, groupFieldsProvider) {
        if (searchQuery.isBlank()) groups
        else FuzzyMatcher.filterAndRank(groups, searchQuery, groupFieldsProvider)
    }
    val displayedGroups = remember(groups, matchedGroups, searchQuery) {
        if (searchQuery.isBlank() || matchedGroups.isNotEmpty()) matchedGroups else groups
    }

    val filteredModels = remember(models, searchQuery, capFilterIndex) {
        val byCap = when (capFilterIndex) {
            1 -> models.filter { FreeQuotaUtils.hasAvailableFreeQuota(it.free_quota) || it.free_quota != null }
            2 -> models.filter { it.supports_vision }
            3 -> models.filter { it.supports_reasoning }
            else -> models
        }
        FuzzyMatcher.filterAndRank(byCap, searchQuery) { m ->
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
    val trimmedSearch = searchQuery.trim()
    val showCustomAction = trimmedSearch.isNotEmpty() &&
        models.none { it.id.equals(trimmedSearch, ignoreCase = true) }

    val hasCustomContext = params.contextWindowOverride != null ||
        params.maxHistoryMessages != null ||
        params.maxTokens != null ||
        params.minOutputChars != null

    val canScrollGroupsForward by remember {
        derivedStateOf { groupListState.canScrollForward }
    }
    val canScrollGroupsBackward by remember {
        derivedStateOf { groupListState.canScrollBackward }
    }
    val showExpandGroupsButton = displayedGroups.size > 3 || canScrollGroupsForward || canScrollGroupsBackward

    LaunchedEffect(selectedGroupId, displayedGroups, groupsExpanded) {
        if (groupsExpanded || selectedGroupId == null) return@LaunchedEffect
        val idx = displayedGroups.indexOfFirst { it.id == selectedGroupId }
        if (idx < 0) {
            hasInitialGroupAligned = true
            return@LaunchedEffect
        }
        val layoutInfo = groupListState.layoutInfo
        if (!hasInitialGroupAligned || layoutInfo.visibleItemsInfo.isEmpty()) {
            groupListState.scrollToItem((idx - 1).coerceAtLeast(0))
        } else {
            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
            val isFullyVisible = visibleItem != null &&
                visibleItem.offset >= layoutInfo.viewportStartOffset &&
                visibleItem.offset + visibleItem.size <= layoutInfo.viewportEndOffset
            if (!isFullyVisible) {
                groupListState.animateScrollToItem((idx - 1).coerceAtLeast(0))
            }
        }
        hasInitialGroupAligned = true
    }

    LaunchedEffect(selectedGroupId, capFilterIndex, trimmedSearch) {
        if (lastScrolledContextKey != null) {
            modelListState.scrollToItem(0)
        }
    }

    LaunchedEffect(selectedGroupId, selectedModelId, filteredModels, capFilterIndex, trimmedSearch, isLoadingModels) {
        if (isLoadingModels || trimmedSearch.isNotEmpty() || capFilterIndex != 0) return@LaunchedEffect
        val currentKey = selectedGroupId to selectedModelId
        if (lastScrolledContextKey == currentKey) return@LaunchedEffect
        val idx = selectedModelId?.let { targetId ->
            filteredModels.indexOfFirst { it.id == targetId }
        } ?: -1
        lastScrolledContextKey = currentKey
        if (idx > 2) {
            modelListState.scrollToItem((idx - 1).coerceAtLeast(0))
        }
    }

    val subtitleText = remember(activeGroup?.name, models.size, filteredModels.size, trimmedSearch, capFilterIndex) {
        val countPart = if (trimmedSearch.isNotEmpty() || capFilterIndex != 0) {
            "${filteredModels.size}/${models.size}"
        } else {
            "${models.size}"
        }
        val base = activeGroup?.name?.takeIf { it.isNotBlank() }
        if (base != null) "$base · $countPart" else countPart
    }

    Surface(
        color = c.page,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Dedicated full-screen top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.common.back,
                        tint = c.ink,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = strings.chat.selectModel,
                        color = c.ink,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleText,
                        color = c.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onUpdateContextSettings != null) {
                    val triggerShape = RoundedCornerShape(999.dp)
                    val activeTrigger = showContextSettings || hasCustomContext
                    Row(
                        modifier = Modifier
                            .clip(triggerShape)
                            .background(if (activeTrigger) c.primarySoft else c.surfaceSoft)
                            .clickable { showContextSettings = !showContextSettings }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (hasCustomContext) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(c.primary),
                            )
                        }
                        Text(
                            text = if (isZh) {
                                "上下文设置 ${if (showContextSettings) "▾" else "▸"}"
                            } else {
                                "Context ${if (showContextSettings) "▾" else "▸"}"
                            },
                            color = if (activeTrigger) c.primarySoftInk else c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            GuHairlineDivider(alpha = 0.55f)

            // Collapsible context settings drawer
            if (onUpdateContextSettings != null || onUpdateMinOutput != null) {
                val activeModelMeta = remember(models, selectedModelId) {
                    models.firstOrNull { it.id == selectedModelId }
                }
                val activeModelWindow = activeModelMeta?.context_window ?: 128_000
                AnimatedVisibility(visible = showContextSettings) {
                    Column {
                        ModelContextSettingsDrawer(
                            activeModelId = selectedModelId,
                            activeModelMeta = activeModelMeta,
                            nativeModelWindow = activeModelWindow,
                            params = params,
                            waterline = waterline,
                            mode = mode,
                            isZh = isZh,
                            onUpdate = onUpdateContextSettings ?: { _, _, _ -> },
                            onUpdateMinOutput = onUpdateMinOutput,
                        )
                        GuHairlineDivider(alpha = 0.45f)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Unified 40dp search bar (fuzzy matches both channel names and model IDs)
                GuCompactSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = if (isZh) "搜索渠道或模型…" else "Search channels or models…",
                    clearContentDescription = strings.common.clear,
                    modifier = Modifier.height(40.dp),
                )

                // 2. Option A: Horizontal channel capsule strip + one-tap "全部 ⌄" multi-row expansion
                if (displayedGroups.isEmpty()) {
                    Text(
                        text = strings.chat.noGroups,
                        color = c.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                } else {
                    val onPickGroup: (OnlineChatGroupDto) -> Unit = { g ->
                        onSelectGroup(g.id)
                        capFilterIndex = 0
                        if (trimmedSearch.isNotEmpty() &&
                            FuzzyMatcher.scoreItem(trimmedSearch, groupFieldsProvider(g)) != null
                        ) {
                            searchQuery = ""
                        }
                        groupsExpanded = false
                    }

                    if (!groupsExpanded) {
                        val fadeColorState = rememberUpdatedState(c.page)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .drawWithCache {
                                        val w = 22.dp.toPx()
                                        val startX = (size.width - w).coerceAtLeast(0f)
                                        val brush = Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, fadeColorState.value),
                                            startX = startX,
                                            endX = size.width,
                                        )
                                        onDrawWithContent {
                                            drawContent()
                                            if (canScrollGroupsForward) {
                                                drawRect(
                                                    brush = brush,
                                                    topLeft = Offset(startX, 0f),
                                                    size = Size(w.coerceAtMost(size.width), size.height),
                                                )
                                            }
                                        }
                                    },
                            ) {
                                LazyRow(
                                    state = groupListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(
                                        top = 2.dp,
                                        bottom = 2.dp,
                                        end = if (canScrollGroupsForward) 18.dp else 0.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(displayedGroups, key = { it.id }) { g ->
                                        val protoTag = if (ApiProviderConfig.isCustomProviderId(g.id)) {
                                            strings.providers.protocolShortNames[g.platform]
                                        } else {
                                            null
                                        }
                                        GroupChipPill(
                                            group = g,
                                            selected = g.id == selectedGroupId,
                                            protoTag = protoTag,
                                            onClick = { onPickGroup(g) },
                                        )
                                    }
                                }
                            }

                            if (showExpandGroupsButton) {
                                Surface(
                                    onClick = { groupsExpanded = true },
                                    shape = RoundedCornerShape(999.dp),
                                    color = c.surfaceSoft,
                                    border = null,
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = if (isZh) "全部" else "All",
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = if (isZh) "展开全部分组" else "Expand all groups",
                                            tint = c.muted,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GuMetaText(
                                    text = if (isZh) {
                                        "全部分组 (${displayedGroups.size})"
                                    } else {
                                        "All groups (${displayedGroups.size})"
                                    },
                                    color = c.muted,
                                )
                                Surface(
                                    onClick = { groupsExpanded = false },
                                    shape = RoundedCornerShape(999.dp),
                                    color = c.surfaceSoft,
                                    border = null,
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = if (isZh) "收起" else "Collapse",
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ExpandLess,
                                            contentDescription = if (isZh) "收起分组" else "Collapse groups",
                                            tint = c.muted,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                displayedGroups.forEach { g ->
                                    val protoTag = if (ApiProviderConfig.isCustomProviderId(g.id)) {
                                        strings.providers.protocolShortNames[g.platform]
                                    } else {
                                        null
                                    }
                                    GroupChipPill(
                                        group = g,
                                        selected = g.id == selectedGroupId,
                                        protoTag = protoTag,
                                        onClick = { onPickGroup(g) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Single-line capability filter capsule bar
                if (models.isNotEmpty()) {
                    GuSegmentedControl(
                        items = capFilterLabels,
                        selectedIndex = capFilterIndex,
                        onSelect = { capFilterIndex = it },
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Subtle loading bar while switching groups
                if (isLoadingModels) {
                    GuLinearProgressBar(
                        modifier = Modifier.fillMaxWidth(),
                        height = 2.dp,
                    )
                }

                // 4. Full-width clean grouped model list
                if (isLoadingModels && filteredModels.isEmpty() && !showCustomAction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.4.dp,
                                color = c.primary,
                            )
                            Text(
                                text = if (isZh) "正在加载模型…" else "Loading models…",
                                color = c.muted,
                                fontSize = 12.5.sp,
                            )
                        }
                    }
                } else if (filteredModels.isEmpty() && !showCustomAction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GuEmptyState(
                            title = if (models.isEmpty()) strings.chat.noModels else strings.chat.noMatchingModels,
                            icon = Icons.Default.Search,
                        )
                    }
                } else {
                    LazyColumn(
                        state = modelListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer { alpha = if (isLoadingModels) 0.55f else 1f },
                        contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
                    ) {
                        if (showCustomAction) {
                            item(key = "__custom_model__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.primarySoft)
                                        .clickable {
                                            onSelectCustomModel(trimmedSearch)
                                            onBack()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    GuIconTile(
                                        icon = Icons.Default.Add,
                                        tint = Color.White,
                                        backgroundColor = c.primary,
                                        size = 24.dp,
                                        iconSize = 15.dp,
                                        cornerRadius = 7.dp,
                                    )
                                    Text(
                                        text = strings.providers.useCustomModelFormat.fmt("model" to trimmedSearch),
                                        color = c.primarySoftInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        itemsIndexed(filteredModels, key = { _, m -> m.id }) { index, model ->
                            val selected = model.id == selectedModelId
                            val isFirst = index == 0
                            val isLast = index == filteredModels.lastIndex
                            val cornerRadius = theme.cardRadius
                            val itemShape = RoundedCornerShape(
                                topStart = if (isFirst) cornerRadius else 0.dp,
                                topEnd = if (isFirst) cornerRadius else 0.dp,
                                bottomStart = if (isLast) cornerRadius else 0.dp,
                                bottomEnd = if (isLast) cornerRadius else 0.dp,
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .background(if (selected) c.primarySoft else c.surface),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectModel(model.id)
                                            onBack()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    GuModelAvatar(
                                        modelId = model.id,
                                        size = 22.dp,
                                    )
                                    Text(
                                        text = model.id,
                                        color = if (selected) c.primarySoftInk else c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (model.free_quota != null) {
                                        val available = FreeQuotaUtils.hasAvailableFreeQuota(model.free_quota)
                                        GuBadge(
                                            text = if (isZh) "限免" else "Free",
                                            variant = if (available) GuBadgeVariant.EMERALD else GuBadgeVariant.NEUTRAL,
                                        )
                                    }
                                    if (model.supports_vision) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = strings.chat.visionCap,
                                            tint = if (selected) c.primarySoftInk else c.muted.copy(alpha = 0.78f),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    if (model.supports_reasoning) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = strings.chat.reasoningCap,
                                            tint = if (selected) c.primarySoftInk else c.muted.copy(alpha = 0.78f),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    GuMetaText(
                                        text = formatContextTokenBadge(model.context_window),
                                        color = if (selected) c.primarySoftInk.copy(alpha = 0.85f) else c.muted,
                                    )
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = c.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                if (!isLast) {
                                    GuHairlineDivider(
                                        modifier = Modifier.padding(start = 43.dp, end = 12.dp),
                                        alpha = 0.45f,
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

@Composable
private fun GroupChipPill(
    group: OnlineChatGroupDto,
    selected: Boolean,
    protoTag: String?,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) c.primary else c.surfaceSoft,
        border = null,
        modifier = Modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (group.has_free_quotas) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else c.success),
                )
            }
            Text(
                text = group.name,
                color = if (selected) Color.White else c.ink,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (protoTag != null) {
                Text(
                    text = protoTag,
                    color = if (selected) Color.White.copy(alpha = 0.85f) else c.primarySoftInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (group.rate_multiplier != 1.0) {
                Text(
                    text = "${group.rate_multiplier}x",
                    color = if (selected) Color.White.copy(alpha = 0.85f) else c.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModelContextSettingsDrawer(
    activeModelId: String?,
    activeModelMeta: OnlineChatModelDto?,
    nativeModelWindow: Int,
    params: ChatParams,
    waterline: ContextWaterlineSummary,
    mode: ConversationMode,
    isZh: Boolean,
    onUpdate: (contextWindowOverride: Int?, maxHistoryMessages: Int?, maxTokens: Int?) -> Unit,
    onUpdateMinOutput: ((minChars: Int?, autoContinue: Boolean) -> Unit)? = null,
) {
    val c = LocalAppTheme.current.colors
    val hasAnyCustom = params.contextWindowOverride != null ||
        params.maxHistoryMessages != null ||
        params.maxTokens != null ||
        params.minOutputChars != null
    val effectiveWindow = params.contextWindowOverride ?: nativeModelWindow

    var customWindowKText by remember {
        mutableStateOf(params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty())
    }
    var customHistoryText by remember {
        mutableStateOf(params.maxHistoryMessages?.toString().orEmpty())
    }

    LaunchedEffect(params.contextWindowOverride) {
        val expected = params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty()
        if (customWindowKText != expected) customWindowKText = expected
    }
    LaunchedEffect(params.maxHistoryMessages) {
        val expected = params.maxHistoryMessages?.toString().orEmpty()
        if (customHistoryText != expected) customHistoryText = expected
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .background(c.surfaceSoft.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            val modelLabel = activeModelId?.take(20).orEmpty().ifBlank { if (isZh) "当前模型" else "Model" }
            GuMetaText(
                text = if (isZh) {
                    "$modelLabel · 原生 ${formatContextTokenBadge(nativeModelWindow)} · 已用 $usedCompact / $budgetCompact (${waterline.percentage}%)"
                } else {
                    "$modelLabel · Native ${formatContextTokenBadge(nativeModelWindow)} · Used $usedCompact / $budgetCompact (${waterline.percentage}%)"
                },
                color = if (waterline.exceedsModelWindow) c.danger else c.muted,
                modifier = Modifier.weight(1f),
            )
            if (hasAnyCustom) {
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            onUpdate(null, null, null)
                            onUpdateMinOutput?.invoke(null, false)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                        text = if (isZh) "重置" else "Reset",
                        color = c.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Context Window Override
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isZh) "窗口上限" else "Context Window",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            GuMetaText(
                text = if (params.contextWindowOverride == null) {
                    if (isZh) "跟随模型 (${formatContextTokenBadge(effectiveWindow)})" else "Auto (${formatContextTokenBadge(effectiveWindow)})"
                } else {
                    formatContextTokenBadge(effectiveWindow)
                },
                color = if (params.contextWindowOverride != null) c.primarySoftInk else c.muted,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CONTEXT_WINDOW_PRESETS, key = { it.second }) { (tokens, label) ->
                GuPill(
                    label = if (tokens == null) (if (isZh) "自动" else "Auto") else label,
                    selected = params.contextWindowOverride == tokens,
                    onClick = { onUpdate(tokens, params.maxHistoryMessages, params.maxTokens) },
                )
            }
        }

        // History Message Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isZh) "历史保留条数" else "History Messages",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            GuMetaText(
                text = params.maxHistoryMessages?.let {
                    if (isZh) "最近 $it 条" else "Last $it"
                } ?: if (isZh) "不限" else "Unlimited",
                color = if (params.maxHistoryMessages != null) c.primarySoftInk else c.muted,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(HISTORY_MESSAGE_PRESETS, key = { it ?: -1 }) { count ->
                val label = count?.let { if (isZh) "${it}条" else "$it" } ?: if (isZh) "不限" else "All"
                GuPill(
                    label = label,
                    selected = params.maxHistoryMessages == count,
                    onClick = { onUpdate(params.contextWindowOverride, count, params.maxTokens) },
                )
            }
        }

        // Max Output Tokens
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isZh) "单次最大输出" else "Max Output",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            GuMetaText(
                text = params.maxTokens?.let { formatContextTokenBadge(it) } ?: if (isZh) "默认" else "Default",
                color = if (params.maxTokens != null) c.primarySoftInk else c.muted,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MAX_OUTPUT_PRESETS, key = { it.second }) { (tokens, label) ->
                GuPill(
                    label = if (tokens == null) (if (isZh) "默认" else "Auto") else label,
                    selected = params.maxTokens == tokens,
                    onClick = { onUpdate(params.contextWindowOverride, params.maxHistoryMessages, tokens) },
                )
            }
        }

        if (onUpdateMinOutput != null) {
            CompactOutputLengthControl(
                params = params,
                mode = mode,
                isZh = isZh,
                modelId = activeModelId,
                modelMeta = activeModelMeta,
                windowLimit = effectiveWindow,
                onUpdateMinOutput = onUpdateMinOutput,
            )
        }

        // Compact custom inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuTextField(
                value = customWindowKText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(4)
                    customWindowKText = digits
                    if (digits.isEmpty()) {
                        onUpdate(null, params.maxHistoryMessages, params.maxTokens)
                    } else {
                        digits.toIntOrNull()?.takeIf { it >= 4 }?.let { kVal ->
                            onUpdate((kVal * 1000).coerceIn(4096, 2_000_000), params.maxHistoryMessages, params.maxTokens)
                        }
                    }
                },
                placeholder = if (isZh) "自定义窗口 (K)" else "Custom K",
                trailingIcon = {
                    Text(
                        text = "K",
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
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
                        digits.toIntOrNull()?.takeIf { it > 0 }?.let { n ->
                            onUpdate(params.contextWindowOverride, n.coerceIn(1, 500), params.maxTokens)
                        }
                    }
                },
                placeholder = if (isZh) "自定义条数" else "Custom msgs",
                trailingIcon = {
                    Text(
                        text = if (isZh) "条" else "#",
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
