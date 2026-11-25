package com.gugachat.app.ui.images

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
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
    val c = LocalAppTheme.current.colors
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.images.presetsTitle,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.common.close,
                        tint = c.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(color = c.border)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "__create_preset__") {
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        GuTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = strings.images.presetsNameLabel,
                            placeholder = strings.images.presetsNamePlaceholder,
                        )
                        Spacer(Modifier.height(4.dp))
                        GuSwitchRow(
                            label = strings.images.presetsIncludePrompt,
                            checked = includePrompt,
                            onCheckedChange = { includePrompt = it },
                        )
                        if (!hasSelectedModel) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isZh) "请先在创作面板选择一个图片模型" else "Select an image model first to save a preset",
                                color = c.muted,
                                fontSize = 11.5.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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

                if (presets.isEmpty()) {
                    item(key = "__empty_presets__") {
                        GuEmptyState(title = strings.images.presetsEmptyList)
                    }
                } else {
                    items(presets, key = { it.id }) { preset ->
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
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
                        }
                    }
                }
            }
        }
    }
}
