package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSearchableListBox
import com.gugachat.app.core.theme.GuSelectableListRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.images.OnlineImageStudioUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageCreateSheet(
    container: AppContainer,
    state: ImageUiState,
    onSelectKey: (Long) -> Unit,
    onSelectModel: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onAspectChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onUpdateNovelAI: ((com.gugachat.app.domain.images.NovelAIParams) -> com.gugachat.app.domain.images.NovelAIParams) -> Unit,
    onAddRefUri: (android.net.Uri) -> Unit,
    onRemoveRefUpload: (String) -> Unit,
    onRemoveRefAsset: (com.gugachat.app.data.api.OnlineImageReferenceIdDto) -> Unit,
    onOpenPresets: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    val activeKey = state.keys.firstOrNull { it.id == state.selectedKeyId }
    val activeModel = state.models.firstOrNull { it.id == state.selectedModelId }
    val isNai = OnlineImageStudioUtils.isNovelAIModel(activeModel)
    val maxCount = (activeModel?.max_count ?: 4).coerceIn(1, 8)
    val maxRefs = (activeModel?.max_reference_images ?: 0).coerceIn(0, 5)
    val ratios = activeModel?.aspect_ratios?.takeIf { it.isNotEmpty() } ?: OnlineImageStudioUtils.PRESET_ASPECT_RATIOS
    val resolutions = activeModel?.resolutions?.takeIf { it.isNotEmpty() } ?: OnlineImageStudioUtils.PRESET_RESOLUTIONS

    val costEstimate = OnlineImageStudioUtils.estimateGenerationCost(
        key = activeKey,
        model = activeModel,
        aspectRatio = state.aspectRatio,
        resolution = state.resolution,
        count = state.count,
        novelAISteps = state.novelaiParams.steps,
        novelAIWidth = if (isNai) state.novelaiParams.width else null,
        novelAIHeight = if (isNai) state.novelaiParams.height else null,
        isZh = isZh,
    )

    val refPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAddRefUri(uri)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedPicker by remember { mutableStateOf<String?>(null) }
    var groupSearch by remember { mutableStateOf("") }
    var modelSearch by remember { mutableStateOf("") }

    val filteredKeys = remember(state.keys, groupSearch) {
        FuzzyMatcher.filterAndRank(state.keys, groupSearch) { key ->
            listOf(
                key.name,
                key.group_name.orEmpty(),
                if (key.has_free_quotas) "限免 free" else "",
            )
        }
    }

    val filteredModels = remember(state.models, modelSearch) {
        FuzzyMatcher.filterAndRank(state.models, modelSearch) { model ->
            listOf(
                model.id,
                model.provider,
                model.parameter_schema?.upstream_model.orEmpty(),
                if (model.free_quota != null) "限免 free" else "",
            )
        }
    }

    val canSubmit = state.prompt.isNotBlank() && activeKey != null && activeModel != null && !state.isSubmitting
    val totalRefs = state.referenceUploads.size + state.referenceAssets.size
    val modelHasFree = activeModel != null && FreeQuotaUtils.hasAvailableFreeQuota(activeModel.free_quota)

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            GuSheetHeader(
                title = strings.images.title,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
                trailing = {
                    GuButton(onClick = onOpenPresets, compact = true, variant = GuButtonVariant.SUBTLE) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.images.presetsTitle, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )

            // Compact 1-screen creation body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Single-row dual light capsules: [分组: 默认 ▾] [模型: gpt-image ▾]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectorCapsule(
                        prefix = if (isZh) "分组" else "Group",
                        value = activeKey?.name ?: strings.images.selectGroup,
                        expanded = expandedPicker == "group",
                        hasFreeDot = activeKey?.has_free_quotas == true,
                        onClick = {
                            expandedPicker = if (expandedPicker == "group") null else "group"
                        },
                        modifier = Modifier.weight(0.95f),
                    )
                    SelectorCapsule(
                        prefix = if (isZh) "模型" else "Model",
                        value = activeModel?.id ?: strings.images.noModels,
                        expanded = expandedPicker == "model",
                        hasFreeDot = modelHasFree,
                        onClick = {
                            expandedPicker = if (expandedPicker == "model") null else "model"
                        },
                        modifier = Modifier.weight(1.25f),
                    )
                }

                AnimatedVisibility(visible = expandedPicker == "group") {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(10.dp),
                    ) {
                        GuSearchableListBox(
                            title = strings.images.groupLabel,
                            items = filteredKeys,
                            totalCount = state.keys.size,
                            query = groupSearch,
                            onQueryChange = { groupSearch = it },
                            searchPlaceholder = strings.images.searchGroups,
                            emptyText = strings.images.noKeys,
                            noMatchText = strings.images.noMatchingGroups,
                            listHeight = 160.dp,
                            itemKey = { it.id },
                            selectedSummary = activeKey?.name,
                        ) { key ->
                            GuSelectableListRow(
                                title = key.name,
                                selected = key.id == state.selectedKeyId,
                                highlightFree = key.has_free_quotas,
                                onClick = {
                                    onSelectKey(key.id)
                                    modelSearch = ""
                                    expandedPicker = null
                                },
                                badges = {
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
                }

                AnimatedVisibility(visible = expandedPicker == "model") {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(10.dp),
                    ) {
                        GuSearchableListBox(
                            title = strings.images.modelLabel,
                            items = filteredModels,
                            totalCount = state.models.size,
                            query = modelSearch,
                            onQueryChange = { modelSearch = it },
                            searchPlaceholder = strings.images.searchModels,
                            emptyText = strings.images.noModels,
                            noMatchText = strings.images.noMatchingModels,
                            listHeight = 190.dp,
                            itemKey = { it.id },
                            selectedSummary = activeModel?.id,
                        ) { model ->
                            val fq = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh)
                            GuSelectableListRow(
                                title = model.id,
                                selected = model.id == state.selectedModelId,
                                highlightFree = model.free_quota != null,
                                onClick = {
                                    onSelectModel(model.id)
                                    expandedPicker = null
                                },
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
                }

                // Soft borderless c.surface Prompt Card with embedded Reference strip & [+ 垫图 0/4] bottom bar
                GuCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surface,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = strings.images.promptLabel,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.prompt.isNotEmpty()) {
                            Text(
                                text = strings.common.clear,
                                color = c.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onPromptChange("") }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.prompt.isEmpty()) {
                            Text(
                                text = strings.images.promptPlaceholder,
                                color = c.muted.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        BasicTextField(
                            value = state.prompt,
                            onValueChange = onPromptChange,
                            textStyle = TextStyle(
                                color = c.ink,
                                fontSize = 14.5.sp,
                                lineHeight = 20.5.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            minLines = 3,
                            maxLines = 5,
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (totalRefs > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.referenceUploads.forEach { ref ->
                                EmbeddedRefThumb(
                                    onRemove = { onRemoveRefUpload(ref.id) },
                                    removeDescription = strings.common.delete,
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(ref.previewUri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                            }
                            state.referenceAssets.forEach { asset ->
                                EmbeddedRefThumb(
                                    onRemove = { onRemoveRefAsset(asset) },
                                    removeDescription = strings.common.delete,
                                ) {
                                    AuthenticatedImage(
                                        container = container,
                                        generationId = asset.generation_id,
                                        imageIndex = asset.image_index,
                                        variant = "thumb",
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.45f))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (maxRefs > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (totalRefs > 0) c.primarySoft else c.surfaceSoft)
                                    .clickable {
                                        refPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = if (totalRefs > 0) c.primarySoftInk else c.muted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (isZh) "+ 垫图 $totalRefs/$maxRefs" else "+ Ref $totalRefs/$maxRefs",
                                        color = if (totalRefs > 0) c.primarySoftInk else c.ink,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
                                    )
                                }
                            }
                        } else {
                            GuMetaText(text = strings.images.referencesUnsupported)
                        }

                        if (modelHasFree) {
                            GuBadge(
                                text = if (isZh) "限免可用" else "Free",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                    }
                }

                // Unified compact settings card (Aspect Ratio + Resolution & Count)
                if (!isNai) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            // Row 1: Single-line horizontal aspect ratio selector with mini proportion frames
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isZh) "画幅" else strings.images.aspectRatio,
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    items(ratios, key = { it }) { r ->
                                        val selected = state.aspectRatio == r
                                        AspectRatioChip(
                                            ratio = r,
                                            label = if (r == "auto") strings.images.auto else r,
                                            selected = selected,
                                            onClick = { onAspectChange(r) },
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.45f))

                            // Row 2: Resolution & Count [1 | 2 | 4] side-by-side in one row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Resolution segment
                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = if (isZh) "清晰度" else "Quality",
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(c.surfaceSoft)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        resolutions.forEach { res ->
                                            val selected = state.resolution == res
                                            CompactTrackOption(
                                                label = if (res == "auto") strings.images.auto else res,
                                                selected = selected,
                                                onClick = { onResolutionChange(res) },
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .height(38.dp)
                                        .background(c.border.copy(alpha = 0.55f)),
                                )

                                // Count segment [1 | 2 | 4] (or 1..maxCount)
                                val countOptions = remember(maxCount) {
                                    when {
                                        maxCount <= 1 -> listOf(1)
                                        maxCount == 2 -> listOf(1, 2)
                                        maxCount == 3 -> listOf(1, 2, 3)
                                        maxCount == 4 -> listOf(1, 2, 4)
                                        else -> listOf(1, 2, 4, maxCount).distinct()
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = if (isZh) "张数" else "Count",
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(c.surfaceSoft)
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        countOptions.forEach { n ->
                                            val selected = state.count == n
                                            CompactTrackOption(
                                                label = "$n",
                                                selected = selected,
                                                onClick = { onCountChange(n) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    NovelAIParamsPanel(
                        modelId = activeModel?.id.orEmpty(),
                        params = state.novelaiParams,
                        onChange = onUpdateNovelAI,
                        upstreamModel = activeModel?.parameter_schema?.upstream_model,
                    )

                    if (maxCount > 1) {
                        val countOptions = remember(maxCount) {
                            if (maxCount <= 4) (1..maxCount).toList() else listOf(1, 2, 4, maxCount).distinct()
                        }
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = c.surface,
                            cornerRadius = 14.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = strings.images.countLabel,
                                    color = c.muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(c.surfaceSoft)
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    countOptions.forEach { n ->
                                        CompactTrackOption(
                                            label = strings.images.countUnit.fmt("count" to n),
                                            selected = state.count == n,
                                            onClick = { onCountChange(n) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sticky bottom Cost + Generate primary CTA footer
            HorizontalDivider(thickness = 0.5.dp, color = c.border.copy(alpha = 0.55f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GuBadge(
                        text = strings.images.estimatePrefix.fmt("cost" to costEstimate.label),
                        variant = if (costEstimate.isFreeByQuota || costEstimate.isOpusFree) GuBadgeVariant.EMERALD else GuBadgeVariant.HONEY,
                    )
                    GuMetaText(
                        text = buildString {
                            append(strings.images.countUnit.fmt("count" to state.count))
                            append(" · ")
                            if (isNai) {
                                append("${state.novelaiParams.width}×${state.novelaiParams.height}")
                            } else {
                                append(if (state.aspectRatio == "auto") strings.images.auto else state.aspectRatio)
                                if (state.resolution != "auto") append(" · ${state.resolution}")
                            }
                        },
                    )
                }

                GuButton(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    loading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.isSubmitting -> strings.images.submitting
                            costEstimate.label.isNotBlank() -> strings.images.generateWithCost.fmt("cost" to costEstimate.label)
                            else -> strings.images.generateBtn
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorCapsule(
    prefix: String,
    value: String,
    expanded: Boolean,
    hasFreeDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(999.dp)
    val bg = if (expanded) c.primarySoft else c.surface
    val fg = if (expanded) c.primarySoftInk else c.ink

    Surface(
        onClick = onClick,
        shape = shape,
        color = bg,
        shadowElevation = if (theme.isDark || expanded) 0.dp else 0.6.dp,
        modifier = modifier.height(36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "$prefix:",
                color = if (expanded) c.primarySoftInk.copy(alpha = 0.8f) else c.muted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = value,
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasFreeDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.success),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (expanded) c.primarySoftInk else c.muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmbeddedRefThumb(
    onRemove: () -> Unit,
    removeDescription: String,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceSoft),
            content = content,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.danger),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = removeDescription,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun AspectRatioChip(
    ratio: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    val bg = if (selected) c.primarySoft else c.surfaceSoft
    val fg = if (selected) c.primarySoftInk else c.ink

    val (wRatio, hRatio) = remember(ratio) {
        if (ratio == "auto") 1.0 to 1.0 else OnlineImageStudioUtils.parseAspectRatio(ratio)
    }
    val boxW = remember(wRatio, hRatio) {
        val r = (wRatio / hRatio).toFloat().coerceIn(0.45f, 2.1f)
        if (r >= 1f) 11.dp else (11f * r).coerceAtLeast(6f).dp
    }
    val boxH = remember(wRatio, hRatio) {
        val r = (wRatio / hRatio).toFloat().coerceIn(0.45f, 2.1f)
        if (r <= 1f) 11.dp else (11f / r).coerceAtLeast(6f).dp
    }

    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (ratio != "auto") {
            Box(
                modifier = Modifier
                    .width(boxW)
                    .height(boxH)
                    .border(
                        width = 1.2.dp,
                        color = if (selected) c.primary else c.muted.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactTrackOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val thumbBg = if (selected) {
        if (theme.isDark) c.surfaceElevated else c.surface
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = thumbBg,
        shadowElevation = if (selected && !theme.isDark) 0.8.dp else 0.dp,
        modifier = modifier.height(28.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) c.primarySoftInk else c.muted,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
    }
}
