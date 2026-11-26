package com.gugachat.app.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.NovelAICharacterPrompt
import com.gugachat.app.domain.images.NovelAIParams
import com.gugachat.app.domain.images.NovelAIVersion
import com.gugachat.app.domain.images.OnlineImageNovelAI
import kotlin.math.abs
import kotlin.math.roundToInt

private val CHAR_PIN_COLORS = listOf(
    Color(0xFF3B82F6), // #1 Blue
    Color(0xFFEC4899), // #2 Pink
    Color(0xFF10B981), // #3 Emerald
    Color(0xFFF59E0B), // #4 Amber
    Color(0xFF8B5CF6), // #5 Violet
    Color(0xFFEF4444), // #6 Rose
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelAIParamsPanel(
    modelId: String,
    params: NovelAIParams,
    onChange: ((NovelAIParams) -> NovelAIParams) -> Unit,
    upstreamModel: String? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val version = OnlineImageNovelAI.detectVersion(modelId, upstreamModel)
    val samplers = OnlineImageNovelAI.availableSamplers(version)

    var showAdvanced by remember { mutableStateOf(false) }
    var activeCharIndex by remember { mutableIntStateOf(0) }
    var showPositionGrid by remember { mutableStateOf(true) }

    LaunchedEffect(params.characters.size) {
        if (params.characters.isEmpty()) {
            activeCharIndex = 0
        } else if (activeCharIndex > params.characters.lastIndex) {
            activeCharIndex = params.characters.lastIndex
        }
    }

    val advancedSummary = remember(params.sampler, params.noiseSchedule, params.seed) {
        buildString {
            append(params.sampler.removePrefix("k_"))
            append(" · ")
            append(params.noiseSchedule)
            if (params.seed != null) {
                append(" · #${params.seed}")
            }
        }
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.outline,
    )

    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuIconTile(
                    icon = Icons.Default.ColorLens,
                    tint = c.primarySoftInk,
                    backgroundColor = c.primarySoft,
                    size = 30.dp,
                    iconSize = 16.dp,
                    cornerRadius = 8.dp,
                )
                Text(
                    text = strings.images.novelaiTitle,
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                GuBadge(text = version.label, variant = GuBadgeVariant.BLUE)
            }

            GuTextField(
                value = params.negativePrompt,
                onValueChange = { v -> onChange { it.copy(negativePrompt = v) } },
                label = strings.images.novelaiNegative,
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )

            // Character Prompts & Positions (V4 / V4.5 / V5) — directly visible at top level
            if (version != NovelAIVersion.V3) {
                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )
                NovelAICharactersSection(
                    params = params,
                    activeCharIndex = activeCharIndex,
                    showPositionGrid = showPositionGrid,
                    isZh = isZh,
                    onSelectActiveChar = { activeCharIndex = it },
                    onTogglePositionGrid = { showPositionGrid = !showPositionGrid },
                    onAddCharacter = {
                        if (params.characters.size < OnlineImageNovelAI.MAX_CHARACTERS) {
                            val (nx, ny) = OnlineImageNovelAI.nextDefaultCharacterPosition(params.characters)
                            val newIdx = params.characters.size
                            onChange {
                                it.copy(
                                    characters = it.characters + NovelAICharacterPrompt(
                                        centerX = nx,
                                        centerY = ny,
                                    ),
                                )
                            }
                            activeCharIndex = newIdx
                        }
                    },
                    onUpdateCharacter = { idx, transform ->
                        onChange { cur ->
                            cur.copy(
                                characters = cur.characters.mapIndexed { i, old ->
                                    if (i == idx) transform(old) else old
                                },
                            )
                        }
                    },
                    onDeleteCharacter = { idx ->
                        onChange { cur ->
                            cur.copy(characters = cur.characters.filterIndexed { i, _ -> i != idx })
                        }
                    },
                    onToggleUseCoords = { enabled ->
                        onChange { it.copy(useCoords = enabled) }
                    },
                )
                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.images.novelaiSize, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnlineImageNovelAI.PRESET_DIMENSIONS.forEach { (w, h) ->
                        val selected = params.width == w && params.height == h
                        GuPill(
                            label = "${w}×$h",
                            selected = selected,
                            onClick = { onChange { OnlineImageNovelAI.applyDimensionPreset(it, w, h) } },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.images.novelaiSteps, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MonoValuePill(text = "${params.steps}", highlighted = true)
                }
                Slider(
                    value = params.steps.toFloat(),
                    onValueChange = { v -> onChange { it.copy(steps = v.roundToInt().coerceIn(1, 50)) } },
                    valueRange = 1f..50f,
                    colors = sliderColors,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.images.novelaiScale, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MonoValuePill(text = "${((params.scale * 10).roundToInt() / 10.0)}", highlighted = true)
                }
                Slider(
                    value = params.scale.toFloat(),
                    onValueChange = { v -> onChange { it.copy(scale = (v * 10).roundToInt() / 10.0) } },
                    valueRange = 1f..15f,
                    colors = sliderColors,
                )
            }
        }

        HorizontalDivider(
            thickness = theme.borderWidth,
            color = c.border.copy(alpha = 0.78f),
        )

        // Collapsible Advanced Parameters accordion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GuIconTile(
                icon = Icons.Default.Tune,
                tint = c.accent,
                backgroundColor = c.accentSoft,
                size = 28.dp,
                iconSize = 15.dp,
                cornerRadius = 8.dp,
            )
            Text(
                text = if (isZh) "高级参数" else "Advanced",
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuBadge(
                    text = advancedSummary,
                    variant = GuBadgeVariant.NEUTRAL,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.images.novelaiCfgRescale, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        MonoValuePill(text = "${((params.cfgRescale * 100).roundToInt() / 100.0)}", highlighted = false)
                    }
                    Slider(
                        value = params.cfgRescale.toFloat(),
                        onValueChange = { v -> onChange { it.copy(cfgRescale = (v * 100).roundToInt() / 100.0) } },
                        valueRange = 0f..1f,
                        colors = sliderColors,
                    )
                }

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.images.novelaiSampler, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        samplers.forEach { s ->
                            GuPill(s, params.sampler == s, { onChange { it.copy(sampler = s) } })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.images.novelaiNoiseSchedule, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OnlineImageNovelAI.NOISE_SCHEDULES.forEach { ns ->
                            GuPill(ns, params.noiseSchedule == ns, { onChange { it.copy(noiseSchedule = ns) } })
                        }
                    }
                }

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )

                Column {
                    GuSwitchRow(
                        label = strings.images.novelaiQualityToggle,
                        checked = params.qualityToggle,
                        onCheckedChange = { q -> onChange { it.copy(qualityToggle = q) } },
                    )
                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.65f),
                    )
                    if (version == NovelAIVersion.V3) {
                        GuSwitchRow(
                            label = "SMEA (sm)",
                            checked = params.sm,
                            onCheckedChange = { v -> onChange { it.copy(sm = v) } },
                        )
                        HorizontalDivider(
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.65f),
                        )
                        GuSwitchRow(
                            label = "SMEA DYN (sm_dyn)",
                            checked = params.smDyn,
                            onCheckedChange = { v -> onChange { it.copy(smDyn = v) } },
                        )
                    } else {
                        GuSwitchRow(
                            label = "Variety+",
                            checked = params.varietyPlus,
                            onCheckedChange = { v -> onChange { it.copy(varietyPlus = v) } },
                        )
                    }
                }

                HorizontalDivider(
                    thickness = theme.borderWidth,
                    color = c.border.copy(alpha = 0.72f),
                )

                GuTextField(
                    value = params.seed?.toString().orEmpty(),
                    onValueChange = { s -> onChange { it.copy(seed = s.filter { ch -> ch.isDigit() }.toLongOrNull()) } },
                    label = strings.images.novelaiSeed,
                    placeholder = strings.images.novelaiSeedRandom,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = if (params.seed != null) {
                        {
                            IconButton(onClick = { onChange { it.copy(seed = null) } }) {
                                Icon(Icons.Default.Close, contentDescription = strings.common.clear, tint = c.muted)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun NovelAICharactersSection(
    params: NovelAIParams,
    activeCharIndex: Int,
    showPositionGrid: Boolean,
    isZh: Boolean,
    onSelectActiveChar: (Int) -> Unit,
    onTogglePositionGrid: () -> Unit,
    onAddCharacter: () -> Unit,
    onUpdateCharacter: (Int, (NovelAICharacterPrompt) -> NovelAICharacterPrompt) -> Unit,
    onDeleteCharacter: (Int) -> Unit,
    onToggleUseCoords: (Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = strings.images.novelaiCharacters,
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (params.characters.isNotEmpty()) {
                    GuBadge(
                        text = "${params.characters.size}/${OnlineImageNovelAI.MAX_CHARACTERS}",
                        variant = GuBadgeVariant.BLUE,
                    )
                }
            }
            GuButton(
                onClick = onAddCharacter,
                enabled = params.characters.size < OnlineImageNovelAI.MAX_CHARACTERS,
                compact = true,
                variant = GuButtonVariant.SUBTLE,
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.images.novelaiAddCharacter, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (params.characters.isEmpty()) {
            Text(
                text = if (isZh) {
                    "可为最多 6 位角色分别设置正/负面提示词与画面坐标（X / Y 或 A1–E5 宫格定位）"
                } else {
                    "Add up to 6 characters with independent prompts and canvas coordinates (X/Y or A1–E5 grid)"
                },
                color = c.muted,
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp,
            )
        } else {
            params.characters.forEachIndexed { idx, ch ->
                val pinColor = CHAR_PIN_COLORS[idx % CHAR_PIN_COLORS.size]
                val isSelected = idx == activeCharIndex
                NovelAICharacterCard(
                    index = idx,
                    character = ch,
                    pinColor = pinColor,
                    isSelected = isSelected,
                    useCoords = params.useCoords,
                    isZh = isZh,
                    onSelect = { onSelectActiveChar(idx) },
                    onPromptChange = { p -> onUpdateCharacter(idx) { it.copy(prompt = p) } },
                    onNegativeChange = { np -> onUpdateCharacter(idx) { it.copy(negativePrompt = np) } },
                    onCoordChange = { nx, ny ->
                        onUpdateCharacter(idx) { it.copy(centerX = nx, centerY = ny) }
                    },
                    onDelete = { onDeleteCharacter(idx) },
                )
            }

            // Use Coordinates toggle row + visual 5x5 grid toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleUseCoords(!params.useCoords) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Checkbox(
                        checked = params.useCoords,
                        onCheckedChange = onToggleUseCoords,
                        colors = CheckboxDefaults.colors(
                            checkedColor = c.primary,
                            uncheckedColor = c.muted,
                        ),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = strings.images.novelaiUseCoords,
                            color = c.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (params.useCoords) {
                                if (isZh) "已开启：按角色 X / Y 坐标精确定位" else "Enabled: place each character at its X/Y coordinates"
                            } else {
                                if (isZh) "关闭时由 AI 自动安排站位（AI's Choice）" else "Off: AI automatically chooses character placement"
                            },
                            color = c.muted,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (params.useCoords) {
                    Surface(
                        onClick = onTogglePositionGrid,
                        shape = RoundedCornerShape(999.dp),
                        color = if (showPositionGrid) c.primarySoft else c.surfaceSoft,
                        border = BorderStroke(
                            theme.borderWidth,
                            if (showPositionGrid) c.primary.copy(alpha = 0.4f) else c.border,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = if (showPositionGrid) c.primarySoftInk else c.muted,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (isZh) "站位图" else "Grid",
                                color = if (showPositionGrid) c.primarySoftInk else c.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Interactive 5×5 A1–E5 Stage Coordinate Picker
            AnimatedVisibility(visible = params.useCoords && showPositionGrid) {
                NovelAIPositionGridCard(
                    characters = params.characters,
                    activeIndex = activeCharIndex.coerceIn(0, params.characters.lastIndex),
                    isZh = isZh,
                    onSelectActive = onSelectActiveChar,
                    onPickCell = { col, row ->
                        val targetIdx = activeCharIndex.coerceIn(0, params.characters.lastIndex)
                        val nx = OnlineImageNovelAI.COORD_GRID_VALUES[col]
                        val ny = OnlineImageNovelAI.COORD_GRID_VALUES[row]
                        onUpdateCharacter(targetIdx) { it.copy(centerX = nx, centerY = ny) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NovelAICharacterCard(
    index: Int,
    character: NovelAICharacterPrompt,
    pinColor: Color,
    isSelected: Boolean,
    useCoords: Boolean,
    isZh: Boolean,
    onSelect: () -> Unit,
    onPromptChange: (String) -> Unit,
    onNegativeChange: (String) -> Unit,
    onCoordChange: (Float, Float) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    var xText by remember(index) { mutableStateOf(OnlineImageNovelAI.formatCoordNumber(character.centerX)) }
    var yText by remember(index) { mutableStateOf(OnlineImageNovelAI.formatCoordNumber(character.centerY)) }

    LaunchedEffect(character.centerX) {
        val parsed = xText.toFloatOrNull()
        if (parsed == null || abs(parsed - character.centerX) > 0.005f) {
            xText = OnlineImageNovelAI.formatCoordNumber(character.centerX)
        }
    }
    LaunchedEffect(character.centerY) {
        val parsed = yText.toFloatOrNull()
        if (parsed == null || abs(parsed - character.centerY) > 0.005f) {
            yText = OnlineImageNovelAI.formatCoordNumber(character.centerY)
        }
    }

    val cardBorder = if (isSelected && useCoords) pinColor.copy(alpha = 0.65f) else c.border
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceSoft,
        border = BorderStroke(if (isSelected && useCoords) 1.3.dp else theme.borderWidth, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(pinColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    text = if (isZh) "角色 #${index + 1}" else "Character #${index + 1}",
                    color = c.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f),
                )
                MonoValuePill(
                    text = if (useCoords) {
                        OnlineImageNovelAI.formatCoordinateLabel(character.centerX, character.centerY)
                    } else {
                        if (isZh) "AI 站位" else "AI Pos"
                    },
                    highlighted = useCoords && isSelected,
                )
            }

            GuTextField(
                value = character.prompt,
                onValueChange = onPromptChange,
                placeholder = if (isZh) "角色提示词（如：1girl, silver hair, red eyes…）" else "Character prompt (e.g. 1girl, silver hair…)",
                singleLine = false,
                minLines = 1,
                maxLines = 3,
            )

            GuTextField(
                value = character.negativePrompt,
                onValueChange = onNegativeChange,
                placeholder = if (isZh) "角色负面提示词（选填）" else "Character undesired content (optional)",
                singleLine = false,
                minLines = 1,
                maxLines = 2,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GuTextField(
                    value = xText,
                    onValueChange = { raw ->
                        val cleaned = sanitizeDecimalInput(raw)
                        xText = cleaned
                        cleaned.toFloatOrNull()?.let { v ->
                            onCoordChange(v.coerceIn(0f, 1f), character.centerY)
                        }
                    },
                    placeholder = "0.5",
                    leadingIcon = {
                        Text(
                            text = "X",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
                GuTextField(
                    value = yText,
                    onValueChange = { raw ->
                        val cleaned = sanitizeDecimalInput(raw)
                        yText = cleaned
                        cleaned.toFloatOrNull()?.let { v ->
                            onCoordChange(character.centerX, v.coerceIn(0f, 1f))
                        }
                    },
                    placeholder = "0.5",
                    leadingIcon = {
                        Text(
                            text = "Y",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onDelete,
                    shape = RoundedCornerShape(theme.pillRadius),
                    color = c.dangerBg.copy(alpha = 0.55f),
                    border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.45f)),
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.common.delete,
                            tint = c.danger,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = strings.common.delete,
                            color = c.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelAIPositionGridCard(
    characters: List<NovelAICharacterPrompt>,
    activeIndex: Int,
    isZh: Boolean,
    onSelectActive: (Int) -> Unit,
    onPickCell: (col: Int, row: Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val activeColor = CHAR_PIN_COLORS[activeIndex % CHAR_PIN_COLORS.size]

    GuCard(
        backgroundColor = c.surfaceSoft,
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isZh) "点击宫格放置 角色 #${activeIndex + 1}" else "Tap grid to place Character #${activeIndex + 1}",
                    color = c.ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    characters.indices.forEach { idx ->
                        val col = CHAR_PIN_COLORS[idx % CHAR_PIN_COLORS.size]
                        val sel = idx == activeIndex
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (sel) col else col.copy(alpha = 0.22f))
                                .border(
                                    width = if (sel) 1.5.dp else 0.8.dp,
                                    color = if (sel) c.ink else col.copy(alpha = 0.55f),
                                    shape = CircleShape,
                                )
                                .clickable { onSelectActive(idx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = if (sel) Color.White else col,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            // 5×5 A1–E5 Interactive Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0..4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (col in 0..4) {
                            val occupants = characters.indices.filter { idx ->
                                val (cCol, cRow) = OnlineImageNovelAI.nearestGridCell(
                                    characters[idx].centerX,
                                    characters[idx].centerY,
                                )
                                cCol == col && cRow == row
                            }
                            val hasActive = activeIndex in occupants
                            val cellLabel = OnlineImageNovelAI.gridCellLabel(col, row)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        when {
                                            hasActive -> activeColor.copy(alpha = 0.24f)
                                            occupants.isNotEmpty() -> c.surface
                                            else -> c.surface.copy(alpha = 0.55f)
                                        },
                                    )
                                    .border(
                                        width = if (hasActive) 1.4.dp else 0.8.dp,
                                        color = if (hasActive) activeColor else c.border.copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(7.dp),
                                    )
                                    .clickable { onPickCell(col, row) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (occupants.isEmpty()) {
                                    Text(
                                        text = cellLabel,
                                        color = c.muted.copy(alpha = 0.72f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        occupants.take(3).forEach { occIdx ->
                                            val pinCol = CHAR_PIN_COLORS[occIdx % CHAR_PIN_COLORS.size]
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(pinCol),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "${occIdx + 1}",
                                                    color = Color.White,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
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
}

private fun sanitizeDecimalInput(raw: String): String {
    var dotSeen = false
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !dotSeen -> {
                dotSeen = true
                sb.append(ch)
            }
        }
    }
    return sb.toString().take(5)
}

@Composable
private fun MonoValuePill(
    text: String,
    highlighted: Boolean,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) c.primarySoft else c.surfaceSoft,
        border = BorderStroke(
            theme.borderWidth,
            if (highlighted) c.primary.copy(alpha = 0.32f) else c.border,
        ),
    ) {
        Text(
            text = text,
            color = if (highlighted) c.primarySoftInk else c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
        )
    }
}
