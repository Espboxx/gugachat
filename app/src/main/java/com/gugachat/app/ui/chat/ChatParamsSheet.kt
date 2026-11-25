package com.gugachat.app.ui.chat

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.gugachat.app.core.theme.GuPill
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
    val c = LocalAppTheme.current.colors
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

    val sliderColors = SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.surfaceSoft,
        activeTickColor = c.primarySoft,
        inactiveTickColor = c.border,
    )

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
                .fillMaxHeight(0.86f)
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Context Waterline Card
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.chat.contextWaterline.fmt(
                                "used" to waterline.usedTokens,
                                "budget" to waterline.effectiveLimitTokens,
                                "pct" to waterline.percentage,
                            ),
                            color = c.ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        GuBadge(
                            text = "${waterline.percentage}%",
                            variant = if (waterline.exceedsModelWindow) GuBadgeVariant.DANGER else GuBadgeVariant.BLUE,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (waterline.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (waterline.exceedsModelWindow) c.danger else c.primary,
                        trackColor = c.surfaceSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    GuButton(
                        onClick = onCompactNow,
                        loading = isCompacting,
                        compact = true,
                        variant = GuButtonVariant.SUBTLE,
                    ) {
                        Text(strings.chat.compactNow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GuTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    label = strings.chat.systemPrompt,
                    placeholder = strings.chat.systemPromptPlaceholder,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                )

                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.chat.temperature, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        GuBadge(String.format(Locale.US, "%.2f", temp), variant = GuBadgeVariant.NEUTRAL)
                    }
                    Slider(
                        value = temp,
                        onValueChange = { temp = it },
                        valueRange = 0f..2f,
                        colors = sliderColors,
                    )

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.chat.topP, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        GuBadge(String.format(Locale.US, "%.2f", topP), variant = GuBadgeVariant.NEUTRAL)
                    }
                    Slider(
                        value = topP,
                        onValueChange = { topP = it },
                        valueRange = 0.1f..1f,
                        colors = sliderColors,
                    )

                    Spacer(Modifier.height(4.dp))
                    GuTextField(
                        value = maxTokensText,
                        onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                        label = strings.chat.maxTokens,
                        placeholder = "2048",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(strings.chat.reasoningEffort, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuPill(strings.chat.effortDefault, effort == null, { effort = null })
                        GuPill(strings.chat.effortLow, effort == "low", { effort = "low" })
                        GuPill(strings.chat.effortMedium, effort == "medium", { effort = "medium" })
                        GuPill(strings.chat.effortHigh, effort == "high", { effort = "high" })
                    }

                    Spacer(Modifier.height(12.dp))
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        GuBadge(imageTurnsValueText, variant = GuBadgeVariant.BLUE)
                    }
                    Slider(
                        value = maxImageTurns.toFloat(),
                        onValueChange = { maxImageTurns = it.roundToInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = sliderColors,
                    )
                }

                GuCard(modifier = Modifier.fillMaxWidth()) {
                    GuSwitchRow(
                        label = strings.chat.thinkingTitle,
                        checked = enableReasoning,
                        onCheckedChange = { enableReasoning = it },
                    )
                    HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                    GuSwitchRow(
                        label = strings.chat.sessionMemoryToggle,
                        subtitle = strings.chat.sessionMemoryHint,
                        checked = enableAutoMemory,
                        onCheckedChange = { enableAutoMemory = it },
                    )
                    HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                    GuSwitchRow(
                        label = strings.chat.bgConsolidateToggle,
                        subtitle = strings.chat.bgConsolidateHint,
                        checked = enableAutoConsolidate,
                        onCheckedChange = { enableAutoConsolidate = it },
                    )
                }
            }

            HorizontalDivider(color = c.border.copy(alpha = 0.7f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Text(strings.common.save, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
