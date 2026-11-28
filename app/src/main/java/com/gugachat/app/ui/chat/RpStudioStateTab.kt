package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.gugachat.app.core.storage.LocalMediaPathResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuEmptyState
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpSaveSlot

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpStateAndArchiveSection(
    rp: RpConfig,
    saveSlots: List<RpSaveSlot>,
    isStreaming: Boolean = false,
    isGeneratingArt: Boolean,
    modifier: Modifier = Modifier,
    onUpdateState: ((RpGameState) -> RpGameState) -> Unit,
    onCreateSaveSlot: (name: String, note: String) -> Unit,
    onLoadSaveSlot: (RpSaveSlot) -> Unit,
    onBranchSaveSlot: (RpSaveSlot) -> Unit,
    onOverwriteSaveSlot: (String) -> Unit,
    onDeleteSaveSlot: (String) -> Unit,
    onGenerateSceneCg: () -> Unit,
    onPickCgFromAlbum: () -> Unit,
    onSaveCurrentSceneAsCg: () -> Unit,
    onOpenCgDetail: (RpCgRecord) -> Unit,
    studioArtModelState: StudioArtModelUiState = StudioArtModelUiState(),
    onConfigureArtModel: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    // Inline Scene & Act editor state (replaces RpVitalsEditorDialog)
    var sceneExpanded by remember { mutableStateOf(false) }
    var actDraft by remember(rp.state.actNumber) { mutableIntStateOf(rp.state.actNumber) }
    var chapterDraft by remember(rp.state.chapterTitle) { mutableStateOf(rp.state.chapterTitle) }
    var sceneDraft by remember(rp.state.scene) { mutableStateOf(rp.state.scene) }
    var timeDraft by remember(rp.state.time) { mutableStateOf(rp.state.time) }
    var weatherDraft by remember(rp.state.weather) { mutableStateOf(rp.state.weather) }
    var maxHpDraft by remember(rp.state.maxHp) { mutableStateOf(rp.state.maxHp.toString()) }
    var maxMpDraft by remember(rp.state.maxMp) { mutableStateOf(rp.state.maxMp.toString()) }

    // Inline stat editing state (replaces editingStat AlertDialog)
    var inlineEditingStatKey by remember { mutableStateOf<String?>(null) }
    var inlineEditingStatDraft by remember { mutableStateOf("") }
    var newStatName by remember { mutableStateOf("") }
    var newStatVal by remember { mutableStateOf("") }

    var newItemText by remember { mutableStateOf("") }
    var newQuestText by remember { mutableStateOf("") }
    var newFlagKey by remember { mutableStateOf("") }
    var newFlagVal by remember { mutableStateOf("") }

    var saveName by remember { mutableStateOf("") }
    var saveNote by remember { mutableStateOf("") }
    var confirmRewindSlotId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteSlotId by remember { mutableStateOf<String?>(null) }

    val hpFrac = (rp.state.hp.toFloat() / rp.state.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (rp.state.mp.toFloat() / rp.state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Act, Scene & Vitals Card (Inline Expandable + GuInlineStepper)
        GuSectionHeader(
            title = if (isZh) "幕次场景与核心数值" else "Act, Scene & Vitals",
            trailing = {
                GuButton(
                    onClick = { sceneExpanded = !sceneExpanded },
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Icon(
                        imageVector = if (sceneExpanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(13.5.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (sceneExpanded) (if (isZh) "收起" else "Hide") else (if (isZh) "微调" else "Adjust"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { sceneExpanded = !sceneExpanded }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = formatRpActChapter(rp.state.actNumber, rp.state.chapterTitle, isZh),
                    color = c.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "📍 ${rp.state.scene.ifBlank { rp.title }}",
                    color = c.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                )
                if (rp.state.time.isNotBlank() || rp.state.weather.isNotBlank()) {
                    val weatherPart = rp.state.weather.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    GuMetaText(text = "${rp.state.time}$weatherPart")
                }
            }

            AnimatedVisibility(visible = sceneExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surfaceSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (isZh) "当前幕次" else "Act", color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        GuInlineStepper(
                            valueText = if (isZh) "第${actDraft}幕" else "Act $actDraft",
                            canDecrement = actDraft > 1,
                            onDecrement = { actDraft = (actDraft - 1).coerceAtLeast(1) },
                            onIncrement = { actDraft += 1 },
                        )
                    }
                    GuTextField(value = chapterDraft, onValueChange = { chapterDraft = it }, label = strings.rp.chapterLabel)
                    GuTextField(value = sceneDraft, onValueChange = { sceneDraft = it }, label = strings.rp.sceneLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(value = timeDraft, onValueChange = { timeDraft = it }, label = strings.rp.timeLabel, modifier = Modifier.weight(1f))
                        GuTextField(value = weatherDraft, onValueChange = { weatherDraft = it }, label = strings.rp.weatherLabel, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuTextField(
                            value = maxHpDraft,
                            onValueChange = { maxHpDraft = it.filter { ch -> ch.isDigit() } },
                            label = "Max HP",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        GuTextField(
                            value = maxMpDraft,
                            onValueChange = { maxMpDraft = it.filter { ch -> ch.isDigit() } },
                            label = "Max MP",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        GuButton(
                            onClick = {
                                val nextMaxHp = (maxHpDraft.toIntOrNull() ?: rp.state.maxHp).coerceAtLeast(1)
                                val nextMaxMp = (maxMpDraft.toIntOrNull() ?: rp.state.maxMp).coerceAtLeast(1)
                                onUpdateState { s ->
                                    s.copy(
                                        actNumber = actDraft.coerceAtLeast(1),
                                        chapterTitle = chapterDraft.trim(),
                                        scene = sceneDraft.trim(),
                                        time = timeDraft.trim(),
                                        weather = weatherDraft.trim(),
                                        maxHp = nextMaxHp,
                                        hp = s.hp.coerceIn(0, nextMaxHp),
                                        maxMp = nextMaxMp,
                                        mp = s.mp.coerceIn(0, nextMaxMp),
                                    )
                                }
                                sceneExpanded = false
                            },
                            compact = true,
                        ) {
                            Text(strings.common.save, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // HP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("HP ${rp.state.hp}/${rp.state.maxHp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        GuLinearProgressBar(
                            progress = hpFrac,
                            height = 6.dp,
                            color = c.danger,
                            trackColor = c.surfaceSoft,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.hp > 0,
                        canIncrement = rp.state.hp < rp.state.maxHp,
                        onDecrement = { onUpdateState { s -> s.copy(hp = (s.hp - 10).coerceIn(0, s.maxHp)) } },
                        onIncrement = { onUpdateState { s -> s.copy(hp = (s.hp + 10).coerceIn(0, s.maxHp)) } },
                    )
                }
                // MP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MP ${rp.state.mp}/${rp.state.maxMp}", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        GuLinearProgressBar(
                            progress = mpFrac,
                            height = 6.dp,
                            color = c.primary,
                            trackColor = c.surfaceSoft,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.mp > 0,
                        canIncrement = rp.state.mp < rp.state.maxMp,
                        onDecrement = { onUpdateState { s -> s.copy(mp = (s.mp - 10).coerceIn(0, s.maxMp)) } },
                        onIncrement = { onUpdateState { s -> s.copy(mp = (s.mp + 10).coerceIn(0, s.maxMp)) } },
                    )
                }
                // Gold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GuBadge("🪙 ${rp.state.gold}", variant = GuBadgeVariant.HONEY)
                    GuInlineStepper(
                        valueText = "±10",
                        canDecrement = rp.state.gold > 0,
                        onDecrement = { onUpdateState { s -> s.copy(gold = (s.gold - 10).coerceAtLeast(0)) } },
                        onIncrement = { onUpdateState { s -> s.copy(gold = s.gold + 10) } },
                    )
                }
            }
        }

        // 2. Attributes (GuInlineStepper + Tap-to-Edit In-Row)
        GuSectionHeader(
            title = strings.rp.statsTitle,
            trailing = { GuBadge("${rp.state.stats.size}", variant = GuBadgeVariant.NEUTRAL) },
        )
        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
            if (rp.state.stats.isEmpty()) {
                Text(
                    text = if (isZh) "暂无自定义属性" else "No custom attributes yet",
                    color = c.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                val statEntries = rp.state.stats.entries.toList()
                statEntries.forEachIndexed { idx, (statName, statValue) ->
                    val isInlineEditing = inlineEditingStatKey == statName
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        inlineEditingStatKey = if (isInlineEditing) null else statName
                                        inlineEditingStatDraft = statValue.toString()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = statName,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = strings.common.edit,
                                    tint = c.muted.copy(alpha = 0.65f),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            GuInlineStepper(
                                valueText = "$statValue",
                                onDecrement = {
                                    onUpdateState { s -> s.copy(stats = s.stats + (statName to (statValue - 1))) }
                                },
                                onIncrement = {
                                    onUpdateState { s -> s.copy(stats = s.stats + (statName to (statValue + 1))) }
                                },
                            )
                            IconButton(
                                onClick = { onUpdateState { s -> s.copy(stats = s.stats - statName) } },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.common.delete,
                                    tint = c.muted,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        AnimatedVisibility(visible = isInlineEditing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(c.surfaceSoft)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GuTextField(
                                    value = inlineEditingStatDraft,
                                    onValueChange = { inlineEditingStatDraft = it.filter { ch -> ch.isDigit() || ch == '-' } },
                                    placeholder = strings.rp.statValuePlaceholder,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                GuButton(
                                    onClick = {
                                        val parsed = inlineEditingStatDraft.toIntOrNull()
                                        if (parsed != null) {
                                            onUpdateState { s -> s.copy(stats = s.stats + (statName to parsed)) }
                                        }
                                        inlineEditingStatKey = null
                                    },
                                    enabled = inlineEditingStatDraft.toIntOrNull() != null,
                                    compact = true,
                                ) {
                                    Text(strings.common.save, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    if (idx < statEntries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.65f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = newStatName,
                    onValueChange = { newStatName = it },
                    placeholder = strings.rp.statNamePlaceholder,
                    modifier = Modifier.weight(1.3f),
                )
                GuTextField(
                    value = newStatVal,
                    onValueChange = { newStatVal = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    placeholder = strings.rp.statValuePlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f),
                )
                GuButton(
                    onClick = {
                        val k = newStatName.trim()
                        val v = newStatVal.toIntOrNull() ?: 10
                        if (k.isNotEmpty()) {
                            onUpdateState { s -> s.copy(stats = s.stats + (k to v)) }
                            newStatName = ""
                            newStatVal = ""
                        }
                    },
                    enabled = newStatName.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addStatBtn, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 3. Lightweight Inventory, Quests & Story Flags
        GuSectionHeader(
            title = if (isZh) "随身行囊 · 任务 · 旗标" else "Inventory · Quests · Flags",
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            // Inventory
            Text(strings.rp.inventoryTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (rp.state.inventory.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.inventory.forEachIndexed { idx, item ->
                        RpRemovableChip(
                            text = item,
                            onRemove = {
                                onUpdateState { s -> s.copy(inventory = s.inventory.filterIndexed { i, _ -> i != idx }) }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder = strings.rp.addItemPlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuButton(
                    onClick = {
                        val clean = newItemText.trim()
                        if (clean.isNotEmpty()) {
                            onUpdateState { s -> s.copy(inventory = s.inventory + clean) }
                            newItemText = ""
                        }
                    },
                    enabled = newItemText.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addItemBtn, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // Quests
            Text(strings.rp.questsTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            rp.state.quests.forEachIndexed { idx, quest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "• $quest",
                        color = c.ink,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onUpdateState { s -> s.copy(quests = s.quests.filterIndexed { i, _ -> i != idx }) }
                        },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = "Complete",
                            tint = c.success,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuTextField(
                    value = newQuestText,
                    onValueChange = { newQuestText = it },
                    placeholder = strings.rp.addQuestPlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuButton(
                    onClick = {
                        val clean = newQuestText.trim()
                        if (clean.isNotEmpty()) {
                            onUpdateState { s -> s.copy(quests = s.quests + clean) }
                            newQuestText = ""
                        }
                    },
                    enabled = newQuestText.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.rp.addQuestBtn, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // Flags
            Text(strings.rp.flagsTitle, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (rp.state.flags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rp.state.flags.forEach { (k, v) ->
                        RpRemovableChip(
                            text = "$k = $v",
                            onRemove = { onUpdateState { s -> s.copy(flags = s.flags - k) } },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = newFlagKey,
                    onValueChange = { newFlagKey = it },
                    placeholder = strings.rp.flagKeyPlaceholder,
                    modifier = Modifier.weight(1.1f),
                )
                GuTextField(
                    value = newFlagVal,
                    onValueChange = { newFlagVal = it },
                    placeholder = strings.rp.flagValPlaceholder,
                    modifier = Modifier.weight(0.9f),
                )
                GuButton(
                    onClick = {
                        val k = newFlagKey.trim()
                        val v = newFlagVal.trim().ifBlank { "true" }
                        if (k.isNotEmpty()) {
                            onUpdateState { s -> s.copy(flags = s.flags + (k to v)) }
                            newFlagKey = ""
                            newFlagVal = ""
                        }
                    },
                    enabled = newFlagKey.isNotBlank(),
                    compact = true,
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.common.add, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 4. Time Save Slots (Inline 2-tap confirm for Rewind / Delete — Zero Dialog!)
        GuSectionHeader(
            title = strings.rp.savesTab,
            subtitle = strings.rp.savesHint,
            trailing = { GuBadge("${saveSlots.size}", variant = GuBadgeVariant.BLUE) },
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    placeholder = strings.rp.saveNamePlaceholder,
                    modifier = Modifier.weight(1f),
                )
                GuTextField(
                    value = saveNote,
                    onValueChange = { saveNote = it },
                    placeholder = strings.rp.saveNotePlaceholder,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            GuButton(
                onClick = {
                    onCreateSaveSlot(saveName.trim().ifBlank { strings.rp.quickSaveBtn }, saveNote.trim())
                    saveName = ""
                    saveNote = ""
                },
                enabled = !isStreaming,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.rp.createSaveBtn, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            if (saveSlots.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                GuEmptyState(title = strings.rp.savesEmpty, icon = Icons.Default.Save)
            } else {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saveSlots.forEach { slot ->
                        val stateSnap = slot.rpConfig.state
                        val locationLine = buildString {
                            if (stateSnap.chapterTitle.isNotBlank()) {
                                append(stateSnap.chapterTitle)
                                append(" · ")
                            }
                            append("📍 ")
                            append(stateSnap.scene.ifBlank { slot.rpConfig.title })
                        }
                        val isRewindPending = confirmRewindSlotId == slot.id
                        val isDeletePending = confirmDeleteSlotId == slot.id

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.surfaceSoft,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        drawRect(
                                            color = c.primary.copy(alpha = 0.72f),
                                            topLeft = Offset.Zero,
                                            size = Size(3.dp.toPx(), size.height),
                                        )
                                    }
                                    .padding(start = 13.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = slot.name,
                                        color = c.ink,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuBadge("#${slot.messageSeq}", variant = GuBadgeVariant.BLUE)
                                }
                                if (slot.note.isNotBlank()) {
                                    Text(
                                        text = slot.note,
                                        color = c.ink.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                GuMetaText(text = locationLine)

                                if (isRewindPending) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = if (isZh) "回溯将清除后续消息，确定读档？" else "Rewind removes later messages?",
                                        confirmText = strings.rp.loadRewindBtn,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            if (!isStreaming) {
                                                confirmRewindSlotId = null
                                                onLoadSaveSlot(slot)
                                            }
                                        },
                                        onCancel = { confirmRewindSlotId = null },
                                    )
                                } else if (isDeletePending) {
                                    RpInlineDeleteConfirmStrip(
                                        prompt = strings.rp.saveDeleteConfirm.fmt("name" to slot.name),
                                        confirmText = strings.common.delete,
                                        cancelText = strings.common.cancel,
                                        onConfirm = {
                                            confirmDeleteSlotId = null
                                            onDeleteSaveSlot(slot.id)
                                        },
                                        onCancel = { confirmDeleteSlotId = null },
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        GuButton(
                                            onClick = {
                                                confirmDeleteSlotId = null
                                                confirmRewindSlotId = slot.id
                                            },
                                            enabled = !isStreaming,
                                            compact = true,
                                            variant = GuButtonVariant.HONEY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.rp.loadRewindBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onBranchSaveSlot(slot) },
                                            compact = true,
                                            variant = GuButtonVariant.SUBTLE,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.rp.saveBranchBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = { onOverwriteSaveSlot(slot.id) },
                                            enabled = !isStreaming,
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Text(strings.rp.saveOverwriteBtn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        GuButton(
                                            onClick = {
                                                confirmRewindSlotId = null
                                                confirmDeleteSlotId = slot.id
                                            },
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.heightIn(min = 32.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. CG Gallery & Unlocked Endings
        GuSectionHeader(
            title = strings.rp.cgGalleryTitle,
            trailing = { GuBadge("${rp.state.unlockedCgs.size}", variant = GuBadgeVariant.BLUE) },
        )
        GuCard(modifier = Modifier.fillMaxWidth()) {
            if (onConfigureArtModel != null) {
                StudioArtModelStatusRow(
                    state = studioArtModelState,
                    onConfigure = onConfigureArtModel,
                )
                Spacer(Modifier.height(10.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuButton(
                    onClick = onGenerateSceneCg,
                    enabled = !isGeneratingArt,
                    compact = true,
                    variant = GuButtonVariant.HONEY,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.aiGenSceneCgBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                GuButton(
                    onClick = onPickCgFromAlbum,
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rp.cgAddFromAlbumBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!rp.state.sceneImagePath.isNullOrBlank()) {
                    GuButton(
                        onClick = onSaveCurrentSceneAsCg,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.rp.cgSaveCurrentSceneBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rp.state.unlockedCgs.isEmpty()) {
                GuEmptyState(title = strings.rp.cgGalleryEmpty, icon = Icons.Default.Collections)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rp.state.unlockedCgs.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { cg ->
                                val isActive = cg.id == rp.state.activeCgId
                                val cgFile = remember(cg.imagePath) {
                                    LocalMediaPathResolver.resolveFile(ctx.applicationContext.filesDir.absolutePath, cg.imagePath)
                                        ?.takeIf { it.isFile }
                                }
                                Surface(
                                    onClick = { onOpenCgDetail(cg) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = c.surfaceSoft,
                                    border = if (isActive) BorderStroke(1.5.dp, c.primary) else null,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(c.surfaceSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (cgFile != null) {
                                            Image(
                                                painter = rememberAsyncImagePainter(cgFile),
                                                contentDescription = cg.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Icon(Icons.Default.Landscape, contentDescription = null, tint = c.muted, modifier = Modifier.size(26.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        0.0f to Color.Transparent,
                                                        0.5f to Color.Transparent,
                                                        1.0f to Color(0xD90A0D14),
                                                    ),
                                                ),
                                        )
                                        if (isActive) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(7.dp),
                                                shape = RoundedCornerShape(999.dp),
                                                color = c.primary,
                                            ) {
                                                Text(
                                                    text = if (isZh) "当前" else "Active",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = cg.title,
                                            color = Color(0xFFFDF8EE),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        GuSectionHeader(
            title = strings.rp.endingsTitle,
            trailing = { GuBadge("${rp.state.unlockedEndings.size}", variant = GuBadgeVariant.HONEY) },
        )
        if (rp.state.unlockedEndings.isEmpty()) {
            GuEmptyState(title = strings.rp.endingsEmpty, icon = Icons.Default.EmojiEvents)
        } else {
            GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                rp.state.unlockedEndings.forEachIndexed { idx, ending ->
                    RpEndingTrophyRow(ending = ending)
                    if (idx < rp.state.unlockedEndings.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

// ==================== Push/Pop Subpage 2: CG Viewer (Replaces RpCgLightboxDialog) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RpCgViewerSubpage(
    cg: RpCgRecord,
    isActive: Boolean,
    onBack: () -> Unit,
    onApplyAsScene: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH
    var confirmDelete by remember { mutableStateOf(false) }

    val cgFile = remember(cg.imagePath) {
        LocalMediaPathResolver.resolveFile(ctx.applicationContext.filesDir.absolutePath, cg.imagePath)
            ?.takeIf { it.isFile }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSubpageTopBar(
            backLabel = if (isZh) "返回状态" else "State",
            title = cg.title,
            onBack = onBack,
            trailing = {
                GuButton(
                    onClick = onApplyAsScene,
                    enabled = !isActive,
                    compact = true,
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Text(
                        text = if (isActive) (if (isZh) "当前场景" else "Active") else strings.rp.cgSetAsSceneBtn,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0E1320)),
                contentAlignment = Alignment.Center,
            ) {
                if (cgFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(cgFile),
                        contentDescription = cg.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = c.muted, modifier = Modifier.size(36.dp))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isActive) {
                    GuBadge(if (isZh) "当前场景" else "Active Scene", variant = GuBadgeVariant.EMERALD)
                }
                if (cg.scene.isNotBlank()) GuBadge("📍 ${cg.scene}", variant = GuBadgeVariant.NEUTRAL)
                if (cg.turnSeq > 0) GuBadge("#${cg.turnSeq}", variant = GuBadgeVariant.BLUE)
                cg.characterNames.forEach { charName -> GuBadge(charName, variant = GuBadgeVariant.HONEY) }
            }

            if (cg.caption.isNotBlank()) {
                Text(text = cg.caption, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }

            if (confirmDelete) {
                RpInlineDeleteConfirmStrip(
                    prompt = strings.rp.cgDeleteConfirm.fmt("title" to cg.title),
                    confirmText = strings.common.delete,
                    cancelText = strings.common.cancel,
                    onConfirm = onDelete,
                    onCancel = { confirmDelete = false },
                )
            } else {
                GuButton(
                    onClick = { confirmDelete = true },
                    variant = GuButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.common.delete, color = c.danger, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ==================== Tab 2: 设定 (Worldview + Rules + High-Density Worldbook) ====================
