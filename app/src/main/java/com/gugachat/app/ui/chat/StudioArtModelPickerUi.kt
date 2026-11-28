package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuSearchableListBox
import com.gugachat.app.core.theme.GuSelectableListRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.FreeQuotaUtils

/**
 * Compact inline status row placed above AI portrait / scene CG / book-cover buttons
 * so users always see which image channel & model will be invoked and can reconfigure it anytime.
 */
@Composable
internal fun StudioArtModelStatusRow(
    state: StudioArtModelUiState,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val configured = state.isConfigured
    val channelLabel = state.resolvedChannelDisplayName()
    val valueText = if (configured) {
        if (channelLabel.isNotBlank()) "$channelLabel · ${state.selectedModelId}" else state.selectedModelId
    } else {
        strings.rp.aiArtModelUnconfigured
    }

    Surface(
        onClick = onConfigure,
        shape = RoundedCornerShape(12.dp),
        color = if (configured) c.surfaceSoft else c.accentSoft.copy(alpha = if (theme.isDark) 0.55f else 0.72f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = if (configured) c.primary else c.accent,
                modifier = Modifier.size(16.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = strings.rp.aiArtModelLabel,
                    color = c.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = valueText,
                    color = if (configured) c.ink else c.accent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GuBadge(
                text = if (configured) strings.rp.aiArtModelSwitchBtn else strings.rp.aiArtModelConfigureBtn,
                variant = if (configured) GuBadgeVariant.BLUE else GuBadgeVariant.AMBER,
            )
        }
    }
}

/**
 * Reusable push-pop subpage for configuring the channel and image model used by
 * RP character sprites, RP scene CGs, and Novel book covers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioArtModelConfigPage(
    state: StudioArtModelUiState,
    actions: StudioArtModelActions,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var draftKeyId by remember { mutableLongStateOf(state.selectedKeyId) }
    var draftModelId by remember { mutableStateOf(state.selectedModelId) }
    var channelSearch by remember { mutableStateOf("") }
    var modelSearch by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (state.channels.isEmpty() && !state.isLoadingChannels) {
            actions.onRefreshChannels()
        }
    }

    // Auto-select an initial channel in the draft picker once channels finish loading
    LaunchedEffect(state.channels) {
        if (state.channels.isEmpty()) return@LaunchedEffect
        val validCurrent = state.channels.firstOrNull { it.id == draftKeyId && draftKeyId != 0L }
        if (validCurrent == null) {
            val fallback = state.channels.firstOrNull {
                it.id == state.selectedKeyId && state.selectedKeyId != 0L
            } ?: state.channels.firstOrNull {
                it.id == state.galleryFallbackKeyId && state.galleryFallbackKeyId != 0L
            } ?: state.channels.first()
            draftKeyId = fallback.id
            actions.onSelectKey(fallback.id)
        } else if (state.modelsByKeyId[validCurrent.id] == null && state.loadingModelsKeyId != validCurrent.id) {
            actions.onSelectKey(validCurrent.id)
        }
    }

    val activeChannel = remember(state.channels, draftKeyId) {
        state.channels.firstOrNull { it.id == draftKeyId }
    }
    val isCustomChannel = activeChannel != null && ApiProviderConfig.isCustomProviderId(activeChannel.id)
    val channelModels = remember(state.modelsByKeyId, draftKeyId, activeChannel) {
        state.modelsByKeyId[draftKeyId] ?: activeChannel?.models.orEmpty()
    }
    val filteredChannels = remember(state.channels, channelSearch) {
        val q = channelSearch.trim().lowercase()
        if (q.isEmpty()) state.channels else state.channels.filter {
            it.name.lowercase().contains(q) || (it.group_name?.lowercase()?.contains(q) == true)
        }
    }
    val filteredModels = remember(channelModels, modelSearch) {
        val q = modelSearch.trim().lowercase()
        if (q.isEmpty()) channelModels else channelModels.filter {
            it.id.lowercase().contains(q) || it.provider.lowercase().contains(q)
        }
    }

    val galleryFallbackChannel = remember(state.channels, state.galleryFallbackKeyId) {
        if (state.galleryFallbackKeyId == 0L) null
        else state.channels.firstOrNull { it.id == state.galleryFallbackKeyId }
    }
    val canSyncGallery = galleryFallbackChannel != null &&
        state.galleryFallbackModelId.isNotBlank() &&
        (draftKeyId != galleryFallbackChannel.id || draftModelId.trim() != state.galleryFallbackModelId.trim())

    val cleanModelDraft = draftModelId.trim()
    val modelValidForActiveChannel = activeChannel != null && cleanModelDraft.isNotEmpty() && (
        isCustomChannel ||
            channelModels.any { it.id.equals(cleanModelDraft, ignoreCase = true) }
        )

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.common.back,
                    tint = c.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = strings.rp.aiArtModelPickerTitle,
                    color = c.ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.pendingRequest != null) {
                    GuMetaText(
                        text = strings.rp.aiArtModelPickerPendingSubtitle,
                        color = c.primary,
                    )
                }
            }
            IconButton(
                onClick = actions.onRefreshChannels,
                enabled = !state.isLoadingChannels,
                modifier = Modifier.size(38.dp),
            ) {
                if (state.isLoadingChannels) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = c.primary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.common.refresh,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canSyncGallery) {
                val syncChannel = galleryFallbackChannel!!
                Surface(
                    onClick = {
                        draftKeyId = syncChannel.id
                        draftModelId = state.galleryFallbackModelId.trim()
                        actions.onSelectKey(syncChannel.id)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = c.primarySoft.copy(alpha = if (theme.isDark) 0.5f else 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isZh) "一键沿用「创作」画廊当前模型" else "Use Image Studio Current Model",
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            GuMetaText(
                                text = "${syncChannel.name} · ${state.galleryFallbackModelId}",
                            )
                        }
                        GuBadge(
                            text = if (isZh) "选用" else "Use",
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                }
            }

            if (state.isLoadingChannels && state.channels.isEmpty()) {
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = c.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isZh) "正在加载可用生图渠道…" else "Loading image channels…",
                            color = c.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else if (state.channels.isEmpty()) {
                GuEmptyState(
                    title = if (isZh) "暂无可用生图渠道" else "No Image Channels Available",
                    subtitle = strings.rp.aiGenArtNoChannel,
                    icon = Icons.Default.Image,
                )
            } else {
                // Step 1: Channel / Provider selection
                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    GuSearchableListBox(
                        title = if (isZh) "1. 选择生图渠道 / 分组" else "1. Image Channel / Group",
                        items = filteredChannels,
                        totalCount = state.channels.size,
                        query = channelSearch,
                        onQueryChange = { channelSearch = it },
                        searchPlaceholder = strings.images.searchGroups,
                        emptyText = strings.images.noKeys,
                        noMatchText = strings.images.noMatchingGroups,
                        listHeight = 160.dp,
                        itemKey = { it.id },
                        selectedSummary = activeChannel?.name,
                    ) { key ->
                        val isCustom = ApiProviderConfig.isCustomProviderId(key.id)
                        GuSelectableListRow(
                            title = key.name,
                            selected = key.id == draftKeyId,
                            highlightFree = key.has_free_quotas,
                            onClick = {
                                if (draftKeyId != key.id) {
                                    draftKeyId = key.id
                                    modelSearch = ""
                                    val cached = state.modelsByKeyId[key.id] ?: key.models
                                    if (!isCustom && cached.none { it.id.equals(draftModelId.trim(), ignoreCase = true) }) {
                                        draftModelId = cached.firstOrNull()?.id.orEmpty()
                                    }
                                    actions.onSelectKey(key.id)
                                }
                            },
                            badges = {
                                if (isCustom) {
                                    GuBadge(if (isZh) "自定义直连" else "Custom API", variant = GuBadgeVariant.BLUE)
                                }
                                key.group_name?.takeIf { it.isNotBlank() && !it.equals(key.name, ignoreCase = true) }?.let { gn ->
                                    GuBadge(gn, variant = GuBadgeVariant.NEUTRAL)
                                }
                                if (key.has_free_quotas) {
                                    GuBadge(
                                        text = if (isZh) "★ 含限免" else "★ Free",
                                        variant = GuBadgeVariant.EMERALD,
                                    )
                                }
                            },
                        )
                    }
                }

                // Step 2: Model selection + Custom Model ID input
                if (activeChannel != null) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.loadingModelsKeyId == activeChannel.id && channelModels.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = c.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    GuMetaText(if (isZh) "正在拉取该渠道的生图模型…" else "Loading models…")
                                }
                            } else if (channelModels.isNotEmpty() || !isCustomChannel) {
                                GuSearchableListBox(
                                    title = if (isZh) "2. 选择生图模型" else "2. Image Model",
                                    items = filteredModels,
                                    totalCount = channelModels.size,
                                    query = modelSearch,
                                    onQueryChange = { modelSearch = it },
                                    searchPlaceholder = strings.images.searchModels,
                                    emptyText = strings.images.noModels,
                                    noMatchText = strings.images.noMatchingModels,
                                    listHeight = 190.dp,
                                    itemKey = { it.id },
                                    selectedSummary = cleanModelDraft.takeIf { it.isNotEmpty() },
                                ) { model ->
                                    val fq = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh)
                                    GuSelectableListRow(
                                        title = model.id,
                                        selected = model.id.equals(cleanModelDraft, ignoreCase = true),
                                        highlightFree = model.free_quota != null,
                                        onClick = { draftModelId = model.id },
                                        badges = {
                                            if (model.provider.isNotBlank()) {
                                                GuBadge(model.provider, variant = GuBadgeVariant.BLUE)
                                            }
                                            if (fq != null) {
                                                GuBadge(
                                                    text = fq,
                                                    variant = if (FreeQuotaUtils.hasAvailableFreeQuota(model.free_quota)) {
                                                        GuBadgeVariant.EMERALD
                                                    } else {
                                                        GuBadgeVariant.NEUTRAL
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            if (isCustomChannel) {
                                if (channelModels.isNotEmpty()) {
                                    HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.55f))
                                } else {
                                    Text(
                                        text = if (isZh) "2. 填写自定义生图模型 ID" else "2. Custom Image Model ID",
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                GuTextField(
                                    value = draftModelId,
                                    onValueChange = { draftModelId = it },
                                    label = if (isZh) "自定义生图模型 ID（直连支持任意中转模型）" else "Custom Image Model ID",
                                    placeholder = if (isZh) "例如：gpt-image-1、flux-kontext-pro、seedream-3.0、gemini-2.5-flash-image" else "e.g. gpt-image-1, flux-kontext-pro, gemini-2.5-flash-image",
                                    singleLine = true,
                                )
                                Text(
                                    text = if (isZh) {
                                        "支持直接填写当前直连渠道可用的任意生图模型 ID，生成立绘、表情差分、场景 CG 与书封时将严格使用该模型。"
                                    } else {
                                        "Enter any image model ID supported by this direct provider. Portraits, sprites, scene CGs, and book covers will strictly use it."
                                    },
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Bottom CTA Bar
        HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isConfigured) {
                GuButton(
                    onClick = {
                        actions.onClearSelection()
                        draftModelId = ""
                    },
                    variant = GuButtonVariant.SECONDARY,
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Text(
                        text = if (isZh) "清除" else "Clear",
                        color = c.danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            GuButton(
                onClick = {
                    val channel = activeChannel ?: return@GuButton
                    actions.onSaveSelection(
                        channel.id,
                        cleanModelDraft,
                        channel.name,
                        state.pendingRequest != null,
                    )
                },
                enabled = modelValidForActiveChannel,
                variant = GuButtonVariant.PRIMARY,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.pendingRequest != null) {
                        if (isZh) "保存并开始生成" else "Save & Generate"
                    } else {
                        if (isZh) "保存模型配置" else "Save Model"
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
