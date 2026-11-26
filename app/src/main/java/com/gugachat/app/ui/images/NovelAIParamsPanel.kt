package com.gugachat.app.ui.images

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelAIParamsPanel(
    modelId: String,
    params: NovelAIParams,
    onChange: ((NovelAIParams) -> NovelAIParams) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val version = OnlineImageNovelAI.detectVersion(modelId)
    val samplers = OnlineImageNovelAI.availableSamplers(version)

    var showAdvanced by remember { mutableStateOf(false) }

    val advancedSummary = remember(params.sampler, params.noiseSchedule, params.seed, params.characters.size) {
        buildString {
            append(params.sampler.removePrefix("k_"))
            append(" · ")
            append(params.noiseSchedule)
            if (params.seed != null) {
                append(" · #${params.seed}")
            }
            if (params.characters.isNotEmpty()) {
                append(" · ${params.characters.size}")
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

                if (version != NovelAIVersion.V3) {
                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.72f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.images.novelaiCharacters, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                if (params.characters.size < 6) {
                                    onChange { it.copy(characters = it.characters + NovelAICharacterPrompt()) }
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.images.novelaiAddCharacter, tint = c.primary)
                        }
                    }
                    params.characters.forEachIndexed { idx, ch ->
                        GuCard(
                            backgroundColor = c.surfaceSoft,
                            cornerRadius = 12.dp,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (isZh) "角色 #${idx + 1}" else "Character #${idx + 1}",
                                    color = c.ink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                IconButton(
                                    onClick = {
                                        onChange { it.copy(characters = it.characters.filterIndexed { i, _ -> i != idx }) }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.common.delete, tint = c.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                            GuTextField(
                                value = ch.prompt,
                                onValueChange = { p ->
                                    onChange { cur ->
                                        cur.copy(characters = cur.characters.mapIndexed { i, old -> if (i == idx) old.copy(prompt = p) else old })
                                    }
                                },
                                placeholder = if (isZh) "角色特征提示词…" else "Character tags…",
                            )
                            Spacer(Modifier.height(6.dp))
                            GuTextField(
                                value = ch.negativePrompt,
                                onValueChange = { np ->
                                    onChange { cur ->
                                        cur.copy(characters = cur.characters.mapIndexed { i, old -> if (i == idx) old.copy(negativePrompt = np) else old })
                                    }
                                },
                                placeholder = if (isZh) "角色负面词…" else "Negative tags…",
                            )
                        }
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
