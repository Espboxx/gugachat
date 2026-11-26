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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
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
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSearchableListBox
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSelectableListRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuTextField
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
                        Text(strings.images.presetsTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                },
            )

            // Scrollable creation body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Collapsible Channel & Model selector inside a clean GuGroupedCard — keeps Prompt above the fold
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedPicker = if (expandedPicker == "group") null else "group"
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.Tune,
                            tint = c.accent,
                            backgroundColor = c.accentSoft,
                            size = 30.dp,
                            iconSize = 16.dp,
                            cornerRadius = 8.dp,
                        )
                        Text(
                            text = strings.images.groupLabel,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeKey?.name ?: strings.images.selectGroup,
                                color = if (activeKey != null) c.ink else c.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (activeKey?.has_free_quotas == true) {
                                GuBadge(
                                    text = if (isZh) "限免" else "Free",
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            }
                            if (state.keys.isNotEmpty()) {
                                Text(
                                    text = "${state.keys.size}",
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Icon(
                                imageVector = if (expandedPicker == "group") Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = expandedPicker == "group") {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
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

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.78f),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedPicker = if (expandedPicker == "model") null else "model"
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.AutoAwesome,
                            tint = c.primarySoftInk,
                            backgroundColor = c.primarySoft,
                            size = 30.dp,
                            iconSize = 16.dp,
                            cornerRadius = 8.dp,
                        )
                        Text(
                            text = strings.images.modelLabel,
                            color = c.muted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeModel?.id ?: strings.images.noModels,
                                color = if (activeModel != null) c.primary else c.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (activeModel != null && FreeQuotaUtils.hasAvailableFreeQuota(activeModel.free_quota)) {
                                GuBadge(
                                    text = if (isZh) "免费" else "Free",
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            }
                            if (state.models.isNotEmpty()) {
                                Text(
                                    text = "${state.models.size}",
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Icon(
                                imageVector = if (expandedPicker == "model") Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = expandedPicker == "model") {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                            GuSearchableListBox(
                                title = strings.images.modelLabel,
                                items = filteredModels,
                                totalCount = state.models.size,
                                query = modelSearch,
                                onQueryChange = { modelSearch = it },
                                searchPlaceholder = strings.images.searchModels,
                                emptyText = strings.images.noModels,
                                noMatchText = strings.images.noMatchingModels,
                                listHeight = 196.dp,
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
                }

                // Prompt immediately visible above the fold
                GuTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    label = strings.images.promptLabel,
                    placeholder = strings.images.promptPlaceholder,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )

                // Reference Images with 36dp remove hitboxes
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(
                        title = strings.images.referencesLabel,
                        subtitle = if (maxRefs > 0 && totalRefs > 0) "$totalRefs / $maxRefs" else null,
                        trailing = {
                            if (maxRefs > 0) {
                                GuButton(
                                    onClick = {
                                        refPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    compact = true,
                                    variant = GuButtonVariant.SUBTLE,
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.images.addReferences, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                GuBadge(strings.images.referencesUnsupported, variant = GuBadgeVariant.NEUTRAL)
                            }
                        },
                    )
                    if (totalRefs > 0) {
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                state.referenceUploads.forEach { ref ->
                                    Box(modifier = Modifier.size(72.dp)) {
                                        val refShape = RoundedCornerShape(10.dp)
                                        Image(
                                            painter = rememberAsyncImagePainter(ref.previewUri),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .align(Alignment.BottomStart)
                                                .clip(refShape)
                                                .border(theme.borderWidth, c.border, refShape),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(36.dp)
                                                .clickable { onRemoveRefUpload(ref.id) },
                                            contentAlignment = Alignment.TopEnd,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(c.danger),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = strings.common.delete,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                state.referenceAssets.forEach { asset ->
                                    Box(modifier = Modifier.size(72.dp)) {
                                        val refShape = RoundedCornerShape(10.dp)
                                        AuthenticatedImage(
                                            container = container,
                                            generationId = asset.generation_id,
                                            imageIndex = asset.image_index,
                                            variant = "thumb",
                                            modifier = Modifier
                                                .size(64.dp)
                                                .align(Alignment.BottomStart)
                                                .clip(refShape)
                                                .border(theme.borderWidth, c.border, refShape),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(36.dp)
                                                .clickable { onRemoveRefAsset(asset) },
                                            contentAlignment = Alignment.TopEnd,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(c.danger),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = strings.common.delete,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isNai) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(title = strings.images.aspectRatio)
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ratios.forEach { r ->
                                    GuPill(
                                        label = if (r == "auto") strings.images.auto else r,
                                        selected = state.aspectRatio == r,
                                        onClick = { onAspectChange(r) },
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(title = strings.images.resolution)
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                resolutions.forEach { res ->
                                    GuPill(
                                        label = if (res == "auto") strings.images.auto else res,
                                        selected = state.resolution == res,
                                        onClick = { onResolutionChange(res) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    NovelAIParamsPanel(
                        modelId = activeModel?.id.orEmpty(),
                        params = state.novelaiParams,
                        onChange = onUpdateNovelAI,
                    )
                }

                // Image Count
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(title = strings.images.countLabel)
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (1..maxCount).forEach { n ->
                                GuPill(
                                    label = strings.images.countUnit.fmt("count" to n),
                                    selected = state.count == n,
                                    onClick = { onCountChange(n) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // Sticky bottom Cost + Generate primary CTA footer
            HorizontalDivider(thickness = theme.borderWidth, color = c.border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    Text(
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
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
