package com.gugachat.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
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
    val leadNpc = rp.cast.firstOrNull { !it.isPlayer }
    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)
    var expanded by remember(rp.title) { mutableStateOf(false) }

    val summaryTail = if (leadNpc != null) "♥${leadNpc.affinity}" else "🪙${rp.state.gold}"
    val collapsedSummary = if (expanded) {
        "📍 ${rp.state.scene.ifBlank { rp.title }}"
    } else {
        "📍 ${rp.state.scene.ifBlank { rp.title }} · HP ${rp.state.hp}/${rp.state.maxHp} · $summaryTail"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceSoft.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = collapsedSummary,
                color = if (expanded) c.ink else c.muted,
                fontSize = 11.5.sp,
                fontWeight = if (expanded) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!expanded) {
                Text(
                    text = strings.rp.studioBtn,
                    color = c.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenStudio)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "HP ${rp.state.hp}/${rp.state.maxHp}",
                            color = c.muted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        LinearProgressIndicator(
                            progress = { hpFrac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                            color = c.danger,
                            trackColor = c.surface,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "MP ${rp.state.mp}/${rp.state.maxMp}",
                            color = c.muted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        LinearProgressIndicator(
                            progress = { mpFrac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                            color = c.primary,
                            trackColor = c.surface,
                        )
                    }
                    if (leadNpc != null) {
                        GuBadge("♥ ${leadNpc.name} ${leadNpc.affinity}", variant = GuBadgeVariant.HONEY)
                    } else {
                        GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.NEUTRAL)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuButton(onClick = onQuickSave, compact = true, variant = GuButtonVariant.SUBTLE) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.rp.quickSaveBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    GuButton(onClick = onOpenStudio, compact = true, variant = GuButtonVariant.HONEY) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(strings.rp.studioBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        HorizontalDivider(color = c.border.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            GuSheetHeader(
                title = strings.rp.wizardTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuButton(
                    onClick = { cardLauncher.launch(arrayOf("application/json", "image/png", "*/*")) },
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.rp.importCardBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Text(strings.rp.wizardPresetLabel, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                GuCard(modifier = Modifier.fillMaxWidth(), backgroundColor = c.surfaceSoft) {
                    Text(active.description, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                }

                GuTextField(
                    value = worldTitle,
                    onValueChange = { worldTitle = it },
                    label = strings.rp.worldTitleLabel,
                    placeholder = active.config.title,
                )
            }

            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(strings.common.cancel, color = c.muted, fontWeight = FontWeight.SemiBold)
                }
                GuButton(
                    onClick = { onCreateFromPreset(selectedPreset, worldTitle); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.rp.wizardStartBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpDiceDialog(
    onDismiss: () -> Unit,
    onRoll: (expression: String, skill: String?, difficulty: Int?, mode: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var formula by remember { mutableStateOf("1d20") }
    var skill by remember { mutableStateOf("") }
    var dcText by remember { mutableStateOf("12") }
    var mode by remember { mutableStateOf("normal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = { Text(strings.rp.diceTitle, color = c.ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1d20", "1d100", "2d6+2", "1d12").forEach { f ->
                        GuPill(f, formula == f, { formula = f })
                    }
                }
                GuTextField(formula, { formula = it }, label = strings.rp.diceFormulaLabel)
                GuTextField(skill, { skill = it }, label = strings.rp.diceSkillLabel, placeholder = strings.rp.diceSkillPlaceholder)
                GuTextField(
                    value = dcText,
                    onValueChange = { dcText = it.filter { ch -> ch.isDigit() } },
                    label = strings.rp.diceDcLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(if (isZh) "普通" else "Normal", mode == "normal", { mode = "normal" })
                    GuPill(if (isZh) "优势" else "Advantage", mode == "advantage", { mode = "advantage" })
                    GuPill(if (isZh) "劣势" else "Disadvantage", mode == "disadvantage", { mode = "disadvantage" })
                }
            }
        },
        confirmButton = {
            GuButton(
                onClick = {
                    onRoll(formula, skill, dcText.toIntOrNull(), mode)
                    onDismiss()
                },
                enabled = formula.isNotBlank(),
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
    val isZh = strings.language == AppLanguage.ZH
    var tab by remember { mutableIntStateOf(0) }

    var npcName by remember { mutableStateOf("") }
    var npcPersona by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }
    var rewindTarget by remember { mutableStateOf<RpSaveSlot?>(null) }

    val exportCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportCardJson(uri)
    }

    if (rewindTarget != null) {
        val target = rewindTarget!!
        GuConfirmDialog(
            title = strings.rp.loadRewindBtn,
            message = if (isZh) {
                "确定回溯读档至「${target.name}」？该存档节点之后的对话消息将被永久清除。"
            } else {
                "Rewind to \"${target.name}\"? Messages after this save point will be permanently removed."
            },
            confirmText = strings.rp.loadRewindBtn,
            cancelText = strings.common.cancel,
            isDestructive = true,
            onConfirm = {
                onLoadSaveSlot(target)
                rewindTarget = null
                onDismiss()
            },
            onDismiss = { rewindTarget = null },
        )
    }

    val tabs = remember(strings) {
        listOf(
            0 to strings.rp.castTab,
            1 to strings.rp.rpgTab,
            2 to strings.rp.savesTab,
            3 to strings.rp.worldTab,
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
                .fillMaxHeight(0.84f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.rp.studioTitle,
                subtitle = rp.title,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.first }) { (idx, label) ->
                    GuPill(
                        label = label,
                        selected = tab == idx,
                        onClick = { tab = idx },
                        useHoneySelected = false,
                    )
                }
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__add_npc_form__") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GuTextField(npcName, { npcName = it }, placeholder = strings.rp.addNpcBtn, modifier = Modifier.weight(1f))
                                    GuButton(
                                        onClick = {
                                            if (npcName.isNotBlank()) {
                                                onAddNpc(npcName, "ally", npcPersona)
                                                npcName = ""
                                                npcPersona = ""
                                            }
                                        },
                                        enabled = npcName.isNotBlank(),
                                        compact = true,
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = strings.rp.addNpcBtn, modifier = Modifier.size(18.dp))
                                    }
                                }
                                GuTextField(npcPersona, { npcPersona = it }, placeholder = strings.rp.leadPersonalityLabel)
                            }
                        }

                        items(rp.cast, key = { it.id }) { npc ->
                            GuCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = npc.name,
                                        color = c.ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (npc.isPlayer) {
                                        GuBadge(if (isZh) "玩家角色" else "Player", variant = GuBadgeVariant.BLUE)
                                    } else if (npc.mood.isNotBlank()) {
                                        GuBadge(npc.mood, variant = GuBadgeVariant.NEUTRAL)
                                    }
                                }
                                val desc = npc.personality.ifBlank { npc.role }
                                if (desc.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(desc, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                                if (!npc.isPlayer) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuBadge(strings.rp.npcAffinityLabel.fmt("val" to npc.affinity), variant = GuBadgeVariant.HONEY)
                                        Spacer(Modifier.weight(1f))
                                        GuButton(
                                            onClick = { onAdjustAffinity(npc.id, -5) },
                                            compact = true,
                                            variant = GuButtonVariant.SUBTLE,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text("-5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        GuButton(
                                            onClick = { onAdjustAffinity(npc.id, 5) },
                                            compact = true,
                                            variant = GuButtonVariant.SUBTLE,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text("+5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text("${strings.rp.sceneLabel}: ${rp.state.scene.ifBlank { rp.title }}", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            if (rp.state.time.isNotBlank() || rp.state.weather.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                val weatherPart = rp.state.weather.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                                Text("${strings.rp.timeLabel}: ${rp.state.time}$weatherPart", color = c.muted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GuBadge("HP ${rp.state.hp}/${rp.state.maxHp}", variant = GuBadgeVariant.DANGER)
                                GuBadge("MP ${rp.state.mp}/${rp.state.maxMp}", variant = GuBadgeVariant.BLUE)
                                GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.HONEY)
                                rp.state.stats.forEach { (k, v) ->
                                    GuBadge("$k: $v", variant = GuBadgeVariant.BLUE)
                                }
                            }
                        }

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.rp.inventoryTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            Spacer(Modifier.height(6.dp))
                            if (rp.state.inventory.isEmpty()) {
                                Text(if (isZh) "背包空空如也" else "No items in inventory", color = c.muted, fontSize = 12.sp)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rp.state.inventory.forEach { item -> GuBadge(item, variant = GuBadgeVariant.NEUTRAL) }
                                }
                            }
                        }

                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.rp.questsTitle, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                            Spacer(Modifier.height(6.dp))
                            if (rp.state.quests.isEmpty()) {
                                Text(if (isZh) "暂无进行中的任务" else "No active quests", color = c.muted, fontSize = 12.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rp.state.quests.forEach { q ->
                                        Text("• $q", color = c.ink, fontSize = 12.5.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "__save_form__") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GuTextField(saveName, { saveName = it }, placeholder = strings.rp.saveNamePlaceholder, modifier = Modifier.weight(1f))
                                GuButton(
                                    onClick = {
                                        onCreateSaveSlot(saveName.ifBlank { strings.rp.quickSaveBtn })
                                        saveName = ""
                                    },
                                    compact = true,
                                ) {
                                    Text(strings.rp.createSaveBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (saveSlots.isEmpty()) {
                            item(key = "__saves_empty__") {
                                GuEmptyState(title = strings.rp.savesEmpty)
                            }
                        } else {
                            items(saveSlots, key = { it.id }) { slot ->
                                GuCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = slot.name,
                                                color = c.ink,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Seq #${slot.messageSeq} · ${slot.rpConfig.state.scene}",
                                                color = c.muted,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        GuButton(
                                            onClick = { rewindTarget = slot },
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                        ) {
                                            Text(strings.rp.loadRewindBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GuCard(modifier = Modifier.fillMaxWidth()) {
                            GuSwitchRow(
                                label = strings.rp.enableHudToggle,
                                checked = rp.enableStateHud,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(enableStateHud = v) } },
                            )
                            HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                            GuSwitchRow(
                                label = strings.rp.enableChoicesToggle,
                                checked = rp.enableChoices,
                                onCheckedChange = { v -> onUpdateConfig { it.copy(enableChoices = v) } },
                            )
                        }
                        GuButton(
                            onClick = { exportCardLauncher.launch("${rp.title.ifBlank { "character" }}.json") },
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.rp.exportStBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
