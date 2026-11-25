package com.gugachat.app.ui.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineImagePresetDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePresetSheet(
    presets: List<OnlineImagePresetDto>,
    onApply: (OnlineImagePresetDto) -> Unit,
    onCreate: (name: String, includePrompt: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var name by remember { mutableStateOf("") }
    var includePrompt by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.images.presetsTitle, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)

            GuCard {
                GuTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = strings.images.presetsNameLabel,
                    placeholder = strings.images.presetsNamePlaceholder,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.images.presetsIncludePrompt, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = includePrompt, onCheckedChange = { includePrompt = it })
                }
                Spacer(Modifier.height(6.dp))
                GuButton(
                    onClick = {
                        onCreate(name, includePrompt)
                        name = ""
                    },
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(strings.images.presetsSaveAction, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (presets.isEmpty()) {
                GuEmptyState(title = strings.images.presetsEmptyList)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(presets, key = { it.id }) { preset ->
                        GuCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        GuBadge(preset.model, variant = GuBadgeVariant.BLUE)
                                        GuBadge("${preset.aspect_ratio} · ${preset.resolution}", variant = GuBadgeVariant.NEUTRAL)
                                    }
                                }
                                GuButton(
                                    onClick = { onApply(preset) },
                                    compact = true,
                                    variant = GuButtonVariant.HONEY,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onDelete(preset.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
