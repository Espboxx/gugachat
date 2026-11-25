package com.gugachat.app.ui.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
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
    val c = LocalAppTheme.current.colors
    val version = OnlineImageNovelAI.detectVersion(modelId)
    val samplers = OnlineImageNovelAI.availableSamplers(version)

    GuCard(backgroundColor = c.surfaceSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${strings.images.novelaiTitle} (${version.label})",
                color = c.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            GuTextField(
                value = params.negativePrompt,
                onValueChange = { v -> onChange { it.copy(negativePrompt = v) } },
                label = strings.images.novelaiNegative,
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )

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

            Text(
                text = "${strings.images.novelaiSteps}: ${params.steps}",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = params.steps.toFloat(),
                onValueChange = { v -> onChange { it.copy(steps = v.roundToInt().coerceIn(1, 50)) } },
                valueRange = 1f..50f,
            )

            Text(
                text = "${strings.images.novelaiScale}: ${((params.scale * 10).roundToInt() / 10.0)}",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = params.scale.toFloat(),
                onValueChange = { v -> onChange { it.copy(scale = (v * 10).roundToInt() / 10.0) } },
                valueRange = 1f..15f,
            )

            Text(
                text = "${strings.images.novelaiCfgRescale}: ${((params.cfgRescale * 100).roundToInt() / 100.0)}",
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = params.cfgRescale.toFloat(),
                onValueChange = { v -> onChange { it.copy(cfgRescale = (v * 100).roundToInt() / 100.0) } },
                valueRange = 0f..1f,
            )

            Text(strings.images.novelaiSampler, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                samplers.forEach { s ->
                    GuPill(s, params.sampler == s, { onChange { it.copy(sampler = s) } })
                }
            }

            Text(strings.images.novelaiNoiseSchedule, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnlineImageNovelAI.NOISE_SCHEDULES.forEach { ns ->
                    GuPill(ns, params.noiseSchedule == ns, { onChange { it.copy(noiseSchedule = ns) } })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(strings.images.novelaiQualityToggle, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Switch(checked = params.qualityToggle, onCheckedChange = { q -> onChange { it.copy(qualityToggle = q) } })
            }

            if (version == NovelAIVersion.V3) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("SMEA (sm)", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = params.sm, onCheckedChange = { v -> onChange { it.copy(sm = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("SMEA DYN (sm_dyn)", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = params.smDyn, onCheckedChange = { v -> onChange { it.copy(smDyn = v) } })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Variety+ (skip_cfg_above_sigma)", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = params.varietyPlus, onCheckedChange = { v -> onChange { it.copy(varietyPlus = v) } })
                }

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
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.images.novelaiAddCharacter, tint = c.primary)
                    }
                }
                params.characters.forEachIndexed { idx, ch ->
                    GuCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Character #${idx + 1}", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            IconButton(onClick = {
                                onChange { it.copy(characters = it.characters.filterIndexed { i, _ -> i != idx }) }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = c.danger)
                            }
                        }
                        GuTextField(
                            value = ch.prompt,
                            onValueChange = { p ->
                                onChange { cur ->
                                    cur.copy(characters = cur.characters.mapIndexed { i, old -> if (i == idx) old.copy(prompt = p) else old })
                                }
                            },
                            placeholder = "character tags...",
                        )
                        Spacer(Modifier.height(4.dp))
                        GuTextField(
                            value = ch.negativePrompt,
                            onValueChange = { np ->
                                onChange { cur ->
                                    cur.copy(characters = cur.characters.mapIndexed { i, old -> if (i == idx) old.copy(negativePrompt = np) else old })
                                }
                            },
                            placeholder = "negative tags...",
                        )
                    }
                }
            }

            GuTextField(
                value = params.seed?.toString().orEmpty(),
                onValueChange = { s -> onChange { it.copy(seed = s.filter { ch -> ch.isDigit() }.toLongOrNull()) } },
                label = strings.images.novelaiSeed,
                placeholder = strings.images.novelaiSeedRandom,
            )
        }
    }
}
