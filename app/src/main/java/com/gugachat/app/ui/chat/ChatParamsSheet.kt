package com.gugachat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatParamsSheet(
    systemPrompt: String,
    params: ChatParams,
    waterline: ContextWaterlineSummary,
    isCompacting: Boolean,
    onSave: (String, ChatParams) -> Unit,
    onCompactNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var promptDraft by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    var temp by remember(params) { mutableFloatStateOf(params.temperature.toFloat()) }
    var topP by remember(params) { mutableFloatStateOf(params.topP.toFloat()) }
    var maxTokensText by remember(params) { mutableStateOf(params.maxTokens?.toString().orEmpty()) }
    var effort by remember(params) { mutableStateOf(params.reasoningEffort) }
    var maxImageTurns by remember(params) { mutableIntStateOf(params.maxImageTurns) }
    var enableReasoning by remember(params) { mutableStateOf(params.enableReasoning) }
    var enableAutoMemory by remember(params) { mutableStateOf(params.enableAutoMemory) }
    var enableAutoConsolidate by remember(params) { mutableStateOf(params.enableAutoConsolidate) }

    val effortKeys = remember { listOf(null, "low", "medium", "high") }
    val effortLabels = remember(strings) {
        listOf(
            strings.chat.effortDefault,
            strings.chat.effortLow,
            strings.chat.effortMedium,
            strings.chat.effortHigh,
        )
    }
    val selectedEffortIndex = effortKeys.indexOf(effort).coerceAtLeast(0)

    val sliderColors = SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.outline,
        activeTickColor = c.primarySoft,
        inactiveTickColor = c.border,
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.chat.paramsTitle,
                onClose = onDismiss,
                closeContentDescription = strings.common.close,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Section 1: Context Budget / Waterline
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(
                        title = strings.chat.contextBudget,
                        trailing = {
                            val pctVariant = when {
                                waterline.exceedsModelWindow || waterline.percentage >= 90 -> GuBadgeVariant.DANGER
                                waterline.percentage >= 65 -> GuBadgeVariant.AMBER
                                else -> GuBadgeVariant.EMERALD
                            }
                            GuBadge(text = "${waterline.percentage}%", variant = pctVariant)
                        },
                    )

                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.chat.contextWaterline.fmt(
                                "used" to waterline.usedTokens,
                                "budget" to waterline.effectiveLimitTokens,
                                "pct" to waterline.percentage,
                            ),
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(Modifier.height(10.dp))

                        val systemTokens = waterline.summaryTokens.coerceAtLeast(0)
                        val memoryTokens = (waterline.memoryTokens + waterline.recalledMemoryTokens + waterline.novelLoreTokens).coerceAtLeast(0)
                        val historyTokens = (waterline.usedTokens - systemTokens - memoryTokens).coerceAtLeast(0)
                        val limit = waterline.effectiveLimitTokens.coerceAtLeast(1)
                        val scaleDenom = maxOf(limit, waterline.usedTokens).coerceAtLeast(1).toFloat()
                        val sysFrac = (systemTokens / scaleDenom).coerceIn(0f, 1f)
                        val memFrac = (memoryTokens / scaleDenom).coerceIn(0f, 1f)
                        val histFrac = (historyTokens / scaleDenom).coerceIn(0f, 1f)
                        val freeFrac = (1f - sysFrac - memFrac - histFrac).coerceAtLeast(0f)
                        val histColor = if (waterline.exceedsModelWindow) c.danger else c.primary

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.surfaceSoft),
                        ) {
                            if (sysFrac > 0.002f) {
                                Box(
                                    modifier = Modifier
                                        .weight(sysFrac)
                                        .fillMaxHeight()
                                        .background(c.accent),
                                )
                            }
                            if (memFrac > 0.002f) {
                                Box(
                                    modifier = Modifier
                                        .weight(memFrac)
                                        .fillMaxHeight()
                                        .background(c.success),
                                )
                            }
                            if (histFrac > 0.002f) {
                                Box(
                                    modifier = Modifier
                                        .weight(histFrac)
                                        .fillMaxHeight()
                                        .background(histColor),
                                )
                            }
                            if (freeFrac > 0.002f) {
                                Spacer(modifier = Modifier.weight(freeFrac))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WaterlineLegendDot(
                                color = c.accent,
                                label = if (isZh) "系统/摘要" else "System",
                                value = formatCompactTokens(systemTokens),
                            )
                            WaterlineLegendDot(
                                color = c.success,
                                label = if (isZh) "记忆" else "Memory",
                                value = formatCompactTokens(memoryTokens),
                            )
                            WaterlineLegendDot(
                                color = histColor,
                                label = if (isZh) "历史" else "History",
                                value = formatCompactTokens(historyTokens),
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        GuButton(
                            onClick = onCompactNow,
                            loading = isCompacting,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                        ) {
                            Text(strings.chat.compactNow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Section 2: System Prompt
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(title = strings.chat.systemPrompt)
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        GuTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            placeholder = strings.chat.systemPromptPlaceholder,
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5,
                        )
                    }
                }

                // Section 3: Sampling & Reasoning Controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(title = strings.chat.advancedParams)
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.chat.temperature, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                MonoValuePill(String.format(Locale.US, "%.2f", temp))
                            }
                            Slider(
                                value = temp,
                                onValueChange = { temp = it },
                                valueRange = 0f..2f,
                                colors = sliderColors,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.chat.topP, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                MonoValuePill(String.format(Locale.US, "%.2f", topP))
                            }
                            Slider(
                                value = topP,
                                onValueChange = { topP = it },
                                valueRange = 0.1f..1f,
                                colors = sliderColors,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            GuTextField(
                                value = maxTokensText,
                                onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                                label = strings.chat.maxTokens,
                                placeholder = "2048",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(strings.chat.reasoningEffort, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            GuSegmentedControl(
                                items = effortLabels,
                                selectedIndex = selectedEffortIndex,
                                onSelect = { idx -> effort = effortKeys.getOrNull(idx) },
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            val imageTurnsValueText = if (maxImageTurns == 0) {
                                if (isZh) "不保留" else "None"
                            } else {
                                strings.chat.imageTurnsUnit.fmt("n" to maxImageTurns)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strings.chat.imageTurnsLabel,
                                    color = c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                MonoValuePill(imageTurnsValueText, highlight = true)
                            }
                            Slider(
                                value = maxImageTurns.toFloat(),
                                onValueChange = { maxImageTurns = it.roundToInt() },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = sliderColors,
                            )
                        }
                    }
                }

                // Section 4: Thinking & Memory Automation
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(title = strings.chat.memoryTitle)
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        GuSwitchRow(
                            label = strings.chat.thinkingTitle,
                            checked = enableReasoning,
                            onCheckedChange = { enableReasoning = it },
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )
                        GuSwitchRow(
                            label = strings.chat.sessionMemoryToggle,
                            subtitle = strings.chat.sessionMemoryHint,
                            checked = enableAutoMemory,
                            onCheckedChange = { enableAutoMemory = it },
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )
                        GuSwitchRow(
                            label = strings.chat.bgConsolidateToggle,
                            subtitle = strings.chat.bgConsolidateHint,
                            checked = enableAutoConsolidate,
                            onCheckedChange = { enableAutoConsolidate = it },
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.8f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                GuButton(
                    onClick = {
                        val updated = params.copy(
                            temperature = ((temp * 100).roundToInt() / 100.0),
                            topP = ((topP * 100).roundToInt() / 100.0),
                            maxTokens = maxTokensText.toIntOrNull(),
                            reasoningEffort = effort,
                            maxImageTurns = maxImageTurns,
                            enableReasoning = enableReasoning,
                            enableAutoMemory = enableAutoMemory,
                            enableAutoConsolidate = enableAutoConsolidate,
                        )
                        onSave(promptDraft, updated)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.common.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MonoValuePill(
    text: String,
    highlight: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (highlight) c.primarySoft else c.surfaceSoft)
            .border(
                width = theme.borderWidth,
                color = if (highlight) c.primary.copy(alpha = 0.34f) else c.border,
                shape = shape,
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (highlight) c.primarySoftInk else c.ink,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WaterlineLegendDot(
    color: Color,
    label: String,
    value: String,
) {
    val c = LocalAppTheme.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = c.muted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = c.ink,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatCompactTokens(tokens: Int): String = when {
    tokens >= 1000 -> String.format(Locale.US, "%.1fK", tokens / 1000f)
    else -> "${tokens}t"
}
