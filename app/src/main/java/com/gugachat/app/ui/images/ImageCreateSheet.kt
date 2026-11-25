package com.gugachat.app.ui.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSearchableListBox
import com.gugachat.app.core.theme.GuSelectableListRow
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
    val c = LocalAppTheme.current.colors
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.images.title, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                GuButton(onClick = onOpenPresets, compact = true, variant = GuButtonVariant.HONEY) {
                    Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.images.presetsTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Key / Group & Model Fixed-Height Fuzzy-Searchable List Boxes
            GuSearchableListBox(
                title = strings.images.groupLabel,
                items = filteredKeys,
                totalCount = state.keys.size,
                query = groupSearch,
                onQueryChange = { groupSearch = it },
                searchPlaceholder = strings.images.searchGroups,
                emptyText = strings.images.noKeys,
                noMatchText = strings.images.noMatchingGroups,
                listHeight = 148.dp,
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

            GuSearchableListBox(
                title = strings.images.modelLabel,
                items = filteredModels,
                totalCount = state.models.size,
                query = modelSearch,
                onQueryChange = { modelSearch = it },
                searchPlaceholder = strings.images.searchModels,
                emptyText = strings.images.noModels,
                noMatchText = strings.images.noMatchingModels,
                listHeight = 176.dp,
                itemKey = { it.id },
                selectedSummary = activeModel?.id,
            ) { model ->
                val fq = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh)
                GuSelectableListRow(
                    title = model.id,
                    selected = model.id == state.selectedModelId,
                    highlightFree = model.free_quota != null,
                    onClick = { onSelectModel(model.id) },
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

            GuTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = strings.images.promptLabel,
                placeholder = strings.images.promptPlaceholder,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )

            // Reference Images (up to 5)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.images.referencesLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (maxRefs > 0) {
                        GuButton(
                            onClick = {
                                refPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.images.addReferences, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        GuBadge(strings.images.referencesUnsupported, variant = GuBadgeVariant.NEUTRAL)
                    }
                }
                if (state.referenceUploads.isNotEmpty() || state.referenceAssets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.referenceUploads.forEach { ref ->
                            Box(modifier = Modifier.size(64.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(ref.previewUri),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(c.danger)
                                        .clickable { onRemoveRefUpload(ref.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        state.referenceAssets.forEach { asset ->
                            Box(modifier = Modifier.size(64.dp)) {
                                AuthenticatedImage(
                                    container = container,
                                    generationId = asset.generation_id,
                                    imageIndex = asset.image_index,
                                    variant = "thumb",
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(c.danger)
                                        .clickable { onRemoveRefAsset(asset) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (!isNai) {
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(strings.images.aspectRatio, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ratios.forEach { r ->
                            GuPill(if (r == "auto") strings.images.auto else r, state.aspectRatio == r, { onAspectChange(r) })
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(strings.images.resolution, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        resolutions.forEach { res ->
                            GuPill(if (res == "auto") strings.images.auto else res, state.resolution == res, { onResolutionChange(res) })
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

            // Count + Cost Estimate
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(strings.images.countLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    (1..maxCount).forEach { n ->
                        GuPill(strings.images.countUnit.fmt("count" to n), state.count == n, { onCountChange(n) })
                    }
                    Spacer(Modifier.weight(1f))
                    GuBadge(
                        text = strings.images.estimatePrefix.fmt("cost" to costEstimate.label),
                        variant = if (costEstimate.isFreeByQuota || costEstimate.isOpusFree) GuBadgeVariant.EMERALD else GuBadgeVariant.HONEY,
                    )
                }
            }

            GuButton(
                onClick = onSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isSubmitting) strings.images.submitting else strings.images.generateBtn,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
