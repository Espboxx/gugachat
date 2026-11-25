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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.gugachat.app.core.theme.GuSelectableListRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.domain.chat.FreeQuotaUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelGroupSheet(
    groups: List<OnlineChatGroupDto>,
    models: List<OnlineChatModelDto>,
    selectedGroupId: Long?,
    selectedModelId: String?,
    onSelectGroup: (Long) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectCustomModel: (String) -> Unit = onSelectModel,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var groupSearch by remember { mutableStateOf("") }
    var showGroupSearch by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }

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

    val filteredModels = remember(models, modelSearch) {
        FuzzyMatcher.filterAndRank(models, modelSearch) { m ->
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
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.selectModel,
                subtitle = activeGroup?.name,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            // Compact horizontal group selector (no competing nested vertical scroll!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = strings.chat.selectGroup,
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    GuBadge(
                        text = if (groupSearch.isNotBlank()) "${filteredGroups.size}/${groups.size}" else "${groups.size}",
                        variant = GuBadgeVariant.NEUTRAL,
                    )
                    Spacer(Modifier.weight(1f))
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
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) c.primary else c.surface,
                                border = BorderStroke(1.dp, if (selected) c.primary else c.border),
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
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    if (protoTag != null) {
                                        Text(
                                            text = protoTag,
                                            color = if (selected) Color.White.copy(alpha = 0.85f) else c.primarySoftInk,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    } else if (g.rate_multiplier != 1.0) {
                                        Text(
                                            text = "${g.rate_multiplier}x",
                                            color = if (selected) Color.White.copy(alpha = 0.85f) else c.accent,
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

            HorizontalDivider(color = c.border.copy(alpha = 0.65f))

            // Single full-height searchable model list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = strings.chat.model,
                            color = c.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (!selectedModelId.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = c.primarySoft,
                                border = BorderStroke(1.dp, c.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Text(
                                    text = selectedModelId,
                                    color = c.primarySoftInk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    GuBadge(
                        text = if (modelSearch.isNotBlank()) "${filteredModels.size}/${models.size}" else "${models.size}",
                        variant = if (modelSearch.isNotBlank() && filteredModels.isEmpty() && models.isNotEmpty()) {
                            GuBadgeVariant.AMBER
                        } else {
                            GuBadgeVariant.NEUTRAL
                        },
                    )
                }

                GuCompactSearchBar(
                    value = modelSearch,
                    onValueChange = { modelSearch = it },
                    placeholder = strings.chat.searchModels,
                    clearContentDescription = strings.common.clear,
                )

                if (filteredModels.isEmpty() && !showCustomAction) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (models.isEmpty()) strings.chat.noModels else strings.chat.noMatchingModels,
                            color = c.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showCustomAction) {
                            item(key = "__custom_model__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.primarySoft)
                                        .border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectCustomModel(trimmedModelSearch)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = c.primarySoftInk)
                                    Text(
                                        text = strings.providers.useCustomModelFormat.fmt("model" to trimmedModelSearch),
                                        color = c.primarySoftInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
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
                                    GuBadge("${model.context_window / 1000}K", variant = GuBadgeVariant.NEUTRAL)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
