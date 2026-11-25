package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuSearchableListBox
import com.gugachat.app.core.theme.GuSelectableListRow
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuSearchableListBox(
                title = strings.chat.selectGroup,
                items = filteredGroups,
                totalCount = groups.size,
                query = groupSearch,
                onQueryChange = { groupSearch = it },
                searchPlaceholder = strings.chat.searchGroups,
                emptyText = strings.chat.noGroups,
                noMatchText = strings.chat.noMatchingGroups,
                listHeight = 160.dp,
                itemKey = { it.id },
                selectedSummary = activeGroup?.name,
            ) { g ->
                val protoTag = if (ApiProviderConfig.isCustomProviderId(g.id)) {
                    strings.providers.protocolShortNames[g.platform]
                } else {
                    null
                }
                GuSelectableListRow(
                    title = g.name,
                    selected = g.id == selectedGroupId,
                    highlightFree = g.has_free_quotas,
                    onClick = {
                        onSelectGroup(g.id)
                        modelSearch = ""
                    },
                    badges = {
                        if (protoTag != null) {
                            GuBadge(protoTag, variant = GuBadgeVariant.BLUE)
                        } else if (g.platform.isNotBlank()) {
                            GuBadge(g.platform, variant = GuBadgeVariant.NEUTRAL)
                        }
                        if (g.has_free_quotas) {
                            GuBadge(
                                text = if (isZh) "★ 含限免" else "★ Free",
                                variant = GuBadgeVariant.EMERALD,
                            )
                        }
                        if (g.rate_multiplier != 1.0) {
                            GuBadge("${g.rate_multiplier}x", variant = GuBadgeVariant.HONEY)
                        }
                    },
                )
            }

            GuSearchableListBox(
                title = strings.chat.selectModel,
                items = filteredModels,
                totalCount = models.size,
                query = modelSearch,
                onQueryChange = { modelSearch = it },
                searchPlaceholder = strings.chat.searchModels,
                emptyText = strings.chat.noModels,
                noMatchText = strings.chat.noMatchingModels,
                listHeight = 236.dp,
                itemKey = { it.id },
                selectedSummary = selectedModelId,
                topExtraItem = if (showCustomAction) {
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accentSoft)
                                .border(1.dp, c.accent, RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectCustomModel(trimmedModelSearch)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = c.ink)
                            Text(
                                text = strings.providers.useCustomModelFormat.fmt("model" to trimmedModelSearch),
                                color = c.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else {
                    null
                },
            ) { model ->
                val quotaBadge = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh = isZh)
                GuSelectableListRow(
                    title = model.id,
                    selected = model.id == selectedModelId,
                    highlightFree = model.free_quota != null,
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
            Spacer(Modifier.height(14.dp))
        }
    }
}
