package com.gugachat.app.ui.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImagePresetDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImagePresetSheet(
    presets: List<OnlineImagePresetDto>,
    onApply: (OnlineImagePresetDto) -> Unit,
    onCreate: (name: String, includePrompt: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    hasSelectedModel: Boolean = true,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    var name by remember { mutableStateOf("") }
    var includePrompt by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OnlineImagePresetDto?>(null) }

    pendingDelete?.let { target ->
        GuConfirmDialog(
            title = strings.common.delete,
            message = if (isZh) "确定删除参数预设「${target.name}」吗？" else "Delete preset \"${target.name}\"?",
            confirmText = strings.common.delete,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                val id = target.id
                pendingDelete = null
                onDelete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.images.presetsTitle,
                subtitle = if (presets.isNotEmpty()) {
                    if (isZh) "已保存 ${presets.size} 个预设" else "${presets.size} saved"
                } else {
                    null
                },
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "__create_preset__") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(title = strings.images.presetsSaveTitle)
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GuTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = strings.images.presetsNameLabel,
                                    placeholder = strings.images.presetsNamePlaceholder,
                                )
                                HorizontalDivider(
                                    thickness = theme.borderWidth,
                                    color = c.border.copy(alpha = 0.72f),
                                )
                                GuSwitchRow(
                                    label = strings.images.presetsIncludePrompt,
                                    checked = includePrompt,
                                    onCheckedChange = { includePrompt = it },
                                )
                                if (!hasSelectedModel) {
                                    Text(
                                        text = if (isZh) "请先在创作面板选择一个图片模型" else "Select an image model first to save a preset",
                                        color = c.muted,
                                        fontSize = 11.5.sp,
                                    )
                                }
                                GuButton(
                                    onClick = {
                                        if (name.isNotBlank() && hasSelectedModel) {
                                            onCreate(name.trim(), includePrompt)
                                            name = ""
                                        }
                                    },
                                    enabled = name.isNotBlank() && hasSelectedModel,
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.images.presetsSaveAction, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item(key = "__saved_presets__") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuSectionHeader(title = strings.images.presetsManageTitle)
                        if (presets.isEmpty()) {
                            GuEmptyState(
                                title = strings.images.presetsEmptyList,
                                icon = Icons.Default.Bookmarks,
                            )
                        } else {
                            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                                presets.forEachIndexed { idx, preset ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                GuIconTile(
                                                    icon = Icons.Default.Bookmarks,
                                                    tint = c.warning,
                                                    backgroundColor = c.warningBg,
                                                    size = 32.dp,
                                                    iconSize = 16.dp,
                                                    cornerRadius = 9.dp,
                                                )
                                                Text(
                                                    text = preset.name,
                                                    color = c.ink,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                GuButton(
                                                    onClick = { onApply(preset) },
                                                    compact = true,
                                                    variant = GuButtonVariant.PRIMARY,
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(strings.images.presetsApplyAction, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(
                                                    onClick = { pendingDelete = preset },
                                                    modifier = Modifier.size(44.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = strings.common.delete,
                                                        tint = c.danger,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                            FlowRow(
                                                modifier = Modifier.padding(start = 42.dp, end = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (preset.model.isNotBlank()) {
                                                    GuBadge(preset.model, variant = GuBadgeVariant.BLUE)
                                                }
                                                val sizeLabel = listOf(preset.aspect_ratio, preset.resolution)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · ")
                                                if (sizeLabel.isNotBlank()) {
                                                    GuBadge(sizeLabel, variant = GuBadgeVariant.NEUTRAL)
                                                }
                                            }
                                        }
                                        if (idx < presets.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp),
                                                thickness = theme.borderWidth,
                                                color = c.border.copy(alpha = 0.78f),
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
    }
}
