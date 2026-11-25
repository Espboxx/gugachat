package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
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
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var search by remember { mutableStateOf("") }

    val filteredModels = remember(models, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) models else models.filter { it.id.lowercase().contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.page,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings.chat.selectGroup,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (groups.isEmpty()) {
                GuEmptyState(title = strings.chat.noGroups)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groups.forEach { g ->
                        val label = buildString {
                            append(g.name)
                            if (g.has_free_quotas) append(" ★")
                            if (g.rate_multiplier != 1.0) append(" (${g.rate_multiplier}x)")
                        }
                        GuPill(
                            label = label,
                            selected = g.id == selectedGroupId,
                            onClick = { onSelectGroup(g.id) },
                            useHoneySelected = g.has_free_quotas,
                        )
                    }
                }
            }

            HorizontalDivider(color = c.border)

            Text(
                text = strings.chat.selectModel,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            GuTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = strings.chat.searchModels,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted) },
            )

            if (filteredModels.isEmpty()) {
                GuEmptyState(title = strings.chat.noModels)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        val isSel = model.id == selectedModelId
                        val quotaBadge = FreeQuotaUtils.formatFreeQuotaBadge(model.free_quota, isZh = isZh)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) c.primarySoft else c.surface)
                                .border(1.dp, if (isSel) c.primary else c.border, RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectModel(model.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.id,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                }
                            }
                            if (isSel) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
