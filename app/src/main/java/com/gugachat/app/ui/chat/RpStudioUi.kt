package com.gugachat.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpSaveSlot

@Composable
fun RpStatusHudBar(
    rp: RpConfig,
    onQuickSave: () -> Unit,
    onOpenStudio: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.primarySoft)
            .border(1.dp, c.border)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${rp.title} · ${rp.state.scene.ifBlank { rp.playerName }}",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "❤️ ${rp.state.hp}/${rp.state.maxHp}  ✨ ${rp.state.mp}/${rp.state.maxMp}  🪙 ${rp.state.gold}  🎒 ${rp.state.inventory.size}",
                color = c.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GuButton(onClick = onQuickSave, compact = true, variant = GuButtonVariant.SUBTLE) {
            Icon(Icons.Default.Save, contentDescription = strings.rp.quickSaveBtn, modifier = Modifier.size(14.dp))
        }
        GuButton(onClick = onOpenStudio, compact = true, variant = GuButtonVariant.HONEY) {
            Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(strings.rp.studioBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpWizardDialog(
    onDismiss: () -> Unit,
    onCreateFromPreset: (presetId: String, title: String) -> Unit,
    onImportCardUri: (android.net.Uri) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var selectedPreset by remember { mutableStateOf(RpPresets.ALL.first().id) }
    var worldTitle by remember { mutableStateOf("") }

    val cardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportCardUri(uri)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.page,
        title = { Text(strings.rp.wizardTitle, color = c.ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuButton(
                    onClick = { cardLauncher.launch(arrayOf("application/json", "image/png", "*/*")) },
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.rp.importCardBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Text(strings.rp.wizardPresetLabel, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RpPresets.ALL.forEach { p ->
                        GuPill(
                            label = strings.rp.presets[p.id] ?: p.name,
                            selected = selectedPreset == p.id,
                            onClick = { selectedPreset = p.id },
                        )
                    }
                }
                val active = RpPresets.findById(selectedPreset)
                Text(active.description, color = c.muted, fontSize = 11.sp)

                GuTextField(
                    value = worldTitle,
                    onValueChange = { worldTitle = it },
                    label = strings.rp.worldTitleLabel,
                    placeholder = active.config.title,
                )
            }
        },
        confirmButton = {
            GuButton(onClick = { onCreateFromPreset(selectedPreset, worldTitle); onDismiss() }, compact = true) {
                Text(strings.rp.wizardStartBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpDiceDialog(
    onDismiss: () -> Unit,
    onRoll: (expression: String, skill: String?, difficulty: Int?, mode: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var formula by remember { mutableStateOf("1d20") }
    var skill by remember { mutableStateOf("") }
    var dcText by remember { mutableStateOf("12") }
    var mode by remember { mutableStateOf("normal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.page,
        title = { Text(strings.rp.diceTitle, color = c.ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1d20", "1d100", "2d6+2", "1d12").forEach { f ->
                        GuPill(f, formula == f, { formula = f })
                    }
                }
                GuTextField(formula, { formula = it }, label = strings.rp.diceFormulaLabel)
                GuTextField(skill, { skill = it }, label = strings.rp.diceSkillLabel, placeholder = strings.rp.diceSkillPlaceholder)
                GuTextField(dcText, { dcText = it.filter { ch -> ch.isDigit() } }, label = strings.rp.diceDcLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill("Normal", mode == "normal", { mode = "normal" })
                    GuPill("Advantage", mode == "advantage", { mode = "advantage" })
                    GuPill("Disadvantage", mode == "disadvantage", { mode = "disadvantage" })
                }
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    onRoll(formula, skill, dcText.toIntOrNull(), mode)
                    onDismiss()
                },
                compact = true,
                variant = GuButtonVariant.HONEY,
            ) {
                Text(strings.rp.diceRollBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel, color = c.muted)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpStudioSheet(
    rp: RpConfig,
    saveSlots: List<RpSaveSlot>,
    onAdjustAffinity: (String, Int) -> Unit,
    onAddNpc: (String, String, String) -> Unit,
    onCreateSaveSlot: (String) -> Unit,
    onLoadSaveSlot: (RpSaveSlot) -> Unit,
    onUpdateConfig: ((RpConfig) -> RpConfig) -> Unit,
    onExportCardJson: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    var tab by remember { mutableIntStateOf(0) }

    var npcName by remember { mutableStateOf("") }
    var npcPersona by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }

    val exportCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportCardJson(uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.page) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${strings.rp.studioTitle} · ${rp.title}", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuPill(strings.rp.castTab, tab == 0, { tab = 0 })
                GuPill(strings.rp.rpgTab, tab == 1, { tab = 1 })
                GuPill(strings.rp.savesTab, tab == 2, { tab = 2 }, useHoneySelected = true)
                GuPill(strings.rp.worldTab, tab == 3, { tab = 3 })
            }

            when (tab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GuTextField(npcName, { npcName = it }, placeholder = strings.rp.addNpcBtn, modifier = Modifier.weight(1f))
                        GuButton(
                            onClick = {
                                onAddNpc(npcName, "ally", npcPersona)
                                npcName = ""
                                npcPersona = ""
                            },
                            compact = true,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    GuTextField(npcPersona, { npcPersona = it }, placeholder = strings.rp.leadPersonalityLabel)

                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rp.cast, key = { it.id }) { npc ->
                            GuCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${npc.name} (${npc.mood})", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                        Text(npc.personality.ifBlank { npc.role }, color = c.muted, fontSize = 11.sp, maxLines = 2)
                                    }
                                    GuButton(onClick = { onAdjustAffinity(npc.id, -5) }, compact = true, variant = GuButtonVariant.SUBTLE) { Text("-5", fontSize = 11.sp) }
                                    Spacer(Modifier.size(4.dp))
                                    GuBadge("${strings.rp.npcAffinityLabel} ${npc.affinity}", variant = GuBadgeVariant.HONEY)
                                    Spacer(Modifier.size(4.dp))
                                    GuButton(onClick = { onAdjustAffinity(npc.id, 5) }, compact = true, variant = GuButtonVariant.SUBTLE) { Text("+5", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuCard {
                            Text("${strings.rp.sceneLabel}: ${rp.state.scene}", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${strings.rp.timeLabel}: ${rp.state.time} (${rp.state.weather})", color = c.muted, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                rp.state.stats.forEach { (k, v) ->
                                    GuBadge("$k: $v", variant = GuBadgeVariant.BLUE)
                                }
                            }
                        }
                        GuCard {
                            Text(strings.rp.inventoryTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                rp.state.inventory.forEach { item -> GuBadge(item, variant = GuBadgeVariant.NEUTRAL) }
                            }
                        }
                        GuCard {
                            Text(strings.rp.questsTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            rp.state.quests.forEach { q -> Text("• $q", color = c.ink, fontSize = 12.sp) }
                        }
                    }
                }

                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GuTextField(saveName, { saveName = it }, placeholder = strings.rp.saveNamePlaceholder, modifier = Modifier.weight(1f))
                        GuButton(
                            onClick = {
                                onCreateSaveSlot(saveName)
                                saveName = ""
                            },
                            compact = true,
                        ) {
                            Text(strings.rp.createSaveBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (saveSlots.isEmpty()) {
                        GuEmptyState(title = strings.rp.savesEmpty)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(saveSlots, key = { it.id }) { slot ->
                                GuCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(slot.name, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                            Text("Seq #${slot.messageSeq} · ${slot.rpConfig.state.scene}", color = c.muted, fontSize = 11.sp)
                                        }
                                        GuButton(
                                            onClick = { onLoadSaveSlot(slot); onDismiss() },
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                        ) {
                                            Text(strings.rp.loadRewindBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuCard {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.rp.enableHudToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = rp.enableStateHud, onCheckedChange = { v -> onUpdateConfig { it.copy(enableStateHud = v) } })
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(strings.rp.enableChoicesToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = rp.enableChoices, onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } })
                            }
                        }
                        GuButton(
                            onClick = { exportCardLauncher.launch("${rp.title.ifBlank { "character" }}.json") },
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.rp.exportStBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
