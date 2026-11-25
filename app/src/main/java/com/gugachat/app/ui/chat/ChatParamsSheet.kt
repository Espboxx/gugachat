package com.gugachat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextWaterlineSummary
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

    var promptDraft by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    var temp by remember(params) { mutableFloatStateOf(params.temperature.toFloat()) }
    var topP by remember(params) { mutableFloatStateOf(params.topP.toFloat()) }
    var maxTokensText by remember(params) { mutableStateOf(params.maxTokens?.toString().orEmpty()) }
    var effort by remember(params) { mutableStateOf(params.reasoningEffort) }
    var maxImageTurns by remember(params) { mutableIntStateOf(params.maxImageTurns) }
    var enableReasoning by remember(params) { mutableStateOf(params.enableReasoning) }
    var enableAutoMemory by remember(params) { mutableStateOf(params.enableAutoMemory) }
    var enableAutoConsolidate by remember(params) { mutableStateOf(params.enableAutoConsolidate) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.page) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = strings.chat.paramsTitle,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            // Context Waterline Card
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.chat.contextWaterline.fmt(
                        "used" to waterline.usedTokens,
                        "budget" to waterline.effectiveLimitTokens,
                        "pct" to waterline.percentage,
                    ),
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (waterline.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (waterline.exceedsModelWindow) c.danger else c.primary,
                    trackColor = c.surfaceSoft,
                )
                Spacer(Modifier.height(8.dp))
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
                Text(
                    text = "${strings.chat.temperature}: ${((temp * 100).roundToInt() / 100f)}",
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..2f)

                Text(
                    text = "${strings.chat.topP}: ${((topP * 100).roundToInt() / 100f)}",
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Slider(value = topP, onValueChange = { topP = it }, valueRange = 0.1f..1f)

                GuTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                    label = strings.chat.maxTokens,
                    placeholder = "2048",
                )

                Spacer(Modifier.height(10.dp))
                Text(strings.chat.reasoningEffort, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(strings.chat.effortDefault, effort == null, { effort = null })
                    GuPill(strings.chat.effortLow, effort == "low", { effort = "low" })
                    GuPill(strings.chat.effortMedium, effort == "medium", { effort = "medium" })
                    GuPill(strings.chat.effortHigh, effort == "high", { effort = "high" })
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${strings.chat.imageTurnsLabel}: $maxImageTurns ${strings.chat.imageTurnsUnit}",
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = maxImageTurns.toFloat(),
                    onValueChange = { maxImageTurns = it.roundToInt() },
                    valueRange = 0f..10f,
                )
            }

            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.chat.thinkingTitle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = enableReasoning, onCheckedChange = { enableReasoning = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.chat.sessionMemoryToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = enableAutoMemory, onCheckedChange = { enableAutoMemory = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.chat.bgConsolidateToggle, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = enableAutoConsolidate, onCheckedChange = { enableAutoConsolidate = it })
                }
            }

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
            Spacer(Modifier.height(16.dp))
        }
    }
}
