package com.gugachat.app.ui.chat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gugachat.app.domain.chat.FileContextBudgeter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatParamsSheet(
    systemPrompt: String,
    params: ChatParams,
    waterline: ContextWaterlineSummary,
    isCompacting: Boolean,
    compactingProgressText: String? = null,
    nativeModelWindow: Int = waterline.modelContextWindow,
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
    var contextWindowKText by remember(params) {
        mutableStateOf(params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty())
    }
    var maxHistoryText by remember(params) {
        mutableStateOf(params.maxHistoryMessages?.toString().orEmpty())
    }
    var effort by remember(params) { mutableStateOf(params.reasoningEffort) }
    var maxImageTurns by remember(params) { mutableIntStateOf(params.maxImageTurns) }
    var enableReasoning by remember(params) { mutableStateOf(params.enableReasoning) }
    var enableAutoMemory by remember(params) { mutableStateOf(params.enableAutoMemory) }
    var enableAutoConsolidate by remember(params) { mutableStateOf(params.enableAutoConsolidate) }
    var enablePromptCaching by remember(params) { mutableStateOf(params.enablePromptCaching) }
    var freqPenaltyText by remember(params) { mutableStateOf(params.frequencyPenalty?.toString().orEmpty()) }
    var presPenaltyText by remember(params) { mutableStateOf(params.presencePenalty?.toString().orEmpty()) }
    var repPenaltyText by remember(params) { mutableStateOf(params.repetitionPenalty?.toString().orEmpty()) }
    var topKText by remember(params) { mutableStateOf(params.topK?.toString().orEmpty()) }
    var minPText by remember(params) { mutableStateOf(params.minP?.toString().orEmpty()) }
    var stopSeqText by remember(params) { mutableStateOf(params.stopSequences.joinToString("\n")) }
    var prefillText by remember(params) { mutableStateOf(params.assistantPrefill) }
    val activeTavernCount = listOf(
        freqPenaltyText,
        presPenaltyText,
        repPenaltyText,
        topKText,
        minPText,
        stopSeqText,
        prefillText,
    ).count { it.isNotBlank() }
    var showTavernAdvanced by remember(params) { mutableStateOf(activeTavernCount > 0) }

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
                // Section 1: Dual-Metric Context Budget / Waterline
                val ctxUi = strings.contextUi
                val sentTokens = waterline.sentTokens.coerceAtLeast(0)
                val sentPct = waterline.sentPercentage.coerceIn(0, 100)
                val limitTokens = waterline.effectiveLimitTokens.coerceAtLeast(1)
                val sentShort = FileContextBudgeter.formatShortTokens(sentTokens)
                val limitShort = FileContextBudgeter.formatShortTokens(limitTokens)
                val archiveShort = FileContextBudgeter.formatShortTokens(waterline.archiveTotalTokens.coerceAtLeast(sentTokens))
                val nativeWindowShort = FileContextBudgeter.formatShortTokens(nativeModelWindow.coerceAtLeast(1))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(
                        title = strings.chat.contextBudget,
                        trailing = {
                            val pctVariant = when {
                                waterline.hardOverflow -> GuBadgeVariant.DANGER
                                waterline.isSliding || sentPct >= 80 -> GuBadgeVariant.AMBER
                                else -> GuBadgeVariant.EMERALD
                            }
                            GuBadge(text = "${ctxUi.sentShortPrefix} ${sentPct}%", variant = pctVariant)
                        },
                    )

                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = ctxUi.sentPayloadHeader.fmt(
                                    "sent" to sentShort,
                                    "limit" to limitShort,
                                    "pct" to sentPct,
                                ),
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (waterline.archiveTotalTokens > sentTokens) {
                                GuBadge(
                                    text = ctxUi.archiveTotalLabel.fmt("archive" to archiveShort),
                                    variant = if (waterline.hardOverflow) GuBadgeVariant.DANGER else GuBadgeVariant.AMBER,
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Calibrated, strictly non-overlapping Actual Sent Payload segments
                        val systemTokens = (waterline.baseSystemTokens + waterline.summaryTokens).coerceAtLeast(0)
                        val memoryTokens = (
                            waterline.memoryTokens +
                                waterline.novelLoreTokens +
                                waterline.recalledMemoryTokens
                            ).coerceAtLeast(0)
                        val historyTokens = (sentTokens - systemTokens - memoryTokens).coerceAtLeast(0)
                        val barTotal = systemTokens + memoryTokens + historyTokens
                        val scaleDenom = maxOf(limitTokens, barTotal).coerceAtLeast(1).toFloat()
                        val sysFrac = (systemTokens / scaleDenom).coerceIn(0f, 1f)
                        val memFrac = (memoryTokens / scaleDenom).coerceIn(0f, 1f)
                        val histFrac = (historyTokens / scaleDenom).coerceIn(0f, 1f)
                        val freeFrac = (1f - sysFrac - memFrac - histFrac).coerceAtLeast(0f)
                        val histColor = when {
                            waterline.hardOverflow -> c.danger
                            waterline.isSliding || sentPct >= 80 -> c.warning
                            else -> c.primary
                        }

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
                                label = ctxUi.legendSystemSummary,
                                value = FileContextBudgeter.formatShortTokens(systemTokens),
                            )
                            WaterlineLegendDot(
                                color = c.success,
                                label = ctxUi.legendMemoryLore,
                                value = FileContextBudgeter.formatShortTokens(memoryTokens),
                            )
                            WaterlineLegendDot(
                                color = histColor,
                                label = ctxUi.legendHistoryDraft,
                                value = FileContextBudgeter.formatShortTokens(historyTokens),
                            )
                            if (waterline.fileTokens > 0) {
                                WaterlineLegendDot(
                                    color = c.muted,
                                    label = ctxUi.legendFiles,
                                    value = FileContextBudgeter.formatShortTokens(waterline.fileTokens),
                                )
                            }
                        }

                        if (waterline.hardOverflow || waterline.isSliding || waterline.trimmedMessageCount > 0) {
                            Spacer(Modifier.height(10.dp))
                            val isDanger = waterline.hardOverflow
                            val stripBg = if (isDanger) c.dangerBg else c.warningBg
                            val stripFg = if (isDanger) c.danger else c.warning
                            val statusCopy = if (isDanger) {
                                ctxUi.hardOverflowWarning.fmt(
                                    "sent" to sentShort,
                                    "window" to FileContextBudgeter.formatShortTokens(waterline.modelContextWindow),
                                )
                            } else {
                                ctxUi.slidingExplanation.fmt(
                                    "archive" to archiveShort,
                                    "trimmed" to waterline.trimmedMessageCount,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = stripBg,
                                border = BorderStroke(theme.borderWidth, stripFg.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isDanger) Icons.Default.WarningAmber else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = stripFg,
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .size(15.dp),
                                    )
                                    Text(
                                        text = statusCopy,
                                        color = stripFg,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GuTextField(
                                value = contextWindowKText,
                                onValueChange = { contextWindowKText = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = ctxUi.contextLimitLabel,
                                placeholder = ctxUi.contextLimitAuto.fmt("window" to nativeWindowShort),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            GuTextField(
                                value = maxHistoryText,
                                onValueChange = { maxHistoryText = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = ctxUi.maxHistoryLabel,
                                placeholder = ctxUi.maxHistoryUnlimited,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        GuButton(
                            onClick = onCompactNow,
                            loading = isCompacting,
                            compact = true,
                            variant = when {
                                waterline.hardOverflow -> GuButtonVariant.DANGER
                                waterline.isSliding -> GuButtonVariant.HONEY
                                else -> GuButtonVariant.SUBTLE
                            },
                        ) {
                            Text(
                                text = if (isCompacting && !compactingProgressText.isNullOrBlank()) {
                                    compactingProgressText
                                } else {
                                    strings.chat.compactNow
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
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

                // Section 3.5: Collapsible Tavern Advanced Sampling & Assistant Prefill
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTavernAdvanced = !showTavernAdvanced }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isZh) "高级采样与预填（酒馆参数）" else "Advanced Sampling & Prefill",
                                    color = c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isZh) "Min-P / Top-K / 重复惩罚 / 停止词 / 开头预填" else "Min-P / Top-K / Penalties / Stop / Assistant Prefill",
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                )
                            }
                            if (activeTavernCount > 0) {
                                MonoValuePill("$activeTavernCount", highlight = true)
                            }
                            Icon(
                                imageVector = if (showTavernAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        AnimatedVisibility(visible = showTavernAdvanced) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HorizontalDivider(thickness = theme.borderWidth, color = c.border.copy(alpha = 0.78f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GuTextField(
                                        value = minPText,
                                        onValueChange = { minPText = filterDecimalInput(it, allowNegative = false) },
                                        label = "Min-P",
                                        placeholder = "0.05",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuTextField(
                                        value = topKText,
                                        onValueChange = { topKText = it.filter(Char::isDigit).take(4) },
                                        label = "Top-K",
                                        placeholder = "40",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GuTextField(
                                        value = freqPenaltyText,
                                        onValueChange = { freqPenaltyText = filterDecimalInput(it, allowNegative = true) },
                                        label = if (isZh) "频率惩罚" else "Freq Penalty",
                                        placeholder = "-2 ~ 2",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                    GuTextField(
                                        value = presPenaltyText,
                                        onValueChange = { presPenaltyText = filterDecimalInput(it, allowNegative = true) },
                                        label = if (isZh) "存在惩罚" else "Pres Penalty",
                                        placeholder = "-2 ~ 2",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                GuTextField(
                                    value = repPenaltyText,
                                    onValueChange = { repPenaltyText = filterDecimalInput(it, allowNegative = false) },
                                    label = if (isZh) "重复惩罚 (Repetition Penalty)" else "Repetition Penalty",
                                    placeholder = "1.0 ~ 1.3",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                GuTextField(
                                    value = stopSeqText,
                                    onValueChange = { stopSeqText = it },
                                    label = if (isZh) "停止词 Stop Sequences（每行一条，最多 4 条）" else "Stop Sequences (one per line, max 4)",
                                    placeholder = if (isZh) "例如：{{user}}:" else "e.g. {{user}}:",
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 3,
                                )
                                GuTextField(
                                    value = prefillText,
                                    onValueChange = { prefillText = it },
                                    label = if (isZh) "助手开头预填 (Assistant Prefill)" else "Assistant Prefill",
                                    placeholder = if (isZh) "留空不启用；支持 {{char}}/{{user}} 宏（Anthropic 开启思考时自动跳过）" else "Optional opening text; supports {{char}}/{{user}} macros",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                )
                                if (activeTavernCount > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                freqPenaltyText = ""
                                                presPenaltyText = ""
                                                repPenaltyText = ""
                                                topKText = ""
                                                minPText = ""
                                                stopSeqText = ""
                                                prefillText = ""
                                            },
                                        ) {
                                            Text(
                                                text = strings.common.reset,
                                                color = c.muted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Thinking & Memory Automation
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuSectionHeader(title = strings.chat.memoryTitle)
                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        GuSwitchRow(
                            label = if (isZh) "Prompt Caching 提示词缓存" else "Prompt Caching",
                            subtitle = if (isZh) "自动固定系统与历史前缀断点，显著降低长剧情输入成本（老中转站报错时可关）" else "Pin static system & history breakpoints to cut long-context input cost",
                            checked = enablePromptCaching,
                            onCheckedChange = { enablePromptCaching = it },
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.78f),
                        )
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
                        val parsedWindow = contextWindowKText.toIntOrNull()?.takeIf { it >= 4 }?.let { (it * 1000).coerceIn(4096, 2_000_000) }
                        val parsedHistory = maxHistoryText.toIntOrNull()?.takeIf { it > 0 }?.coerceIn(1, 500)
                        val parsedStops = stopSeqText
                            .lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .take(4)
                        val updated = params.copy(
                            temperature = ((temp * 100).roundToInt() / 100.0),
                            topP = ((topP * 100).roundToInt() / 100.0),
                            maxTokens = maxTokensText.toIntOrNull(),
                            reasoningEffort = effort,
                            maxImageTurns = maxImageTurns,
                            enableReasoning = enableReasoning,
                            enableAutoMemory = enableAutoMemory,
                            enableAutoConsolidate = enableAutoConsolidate,
                            contextWindowOverride = parsedWindow,
                            maxHistoryMessages = parsedHistory,
                            enablePromptCaching = enablePromptCaching,
                            frequencyPenalty = freqPenaltyText.toDoubleOrNull()?.coerceIn(-2.0, 2.0),
                            presencePenalty = presPenaltyText.toDoubleOrNull()?.coerceIn(-2.0, 2.0),
                            repetitionPenalty = repPenaltyText.toDoubleOrNull()?.coerceIn(0.5, 2.5),
                            topK = topKText.toIntOrNull()?.coerceIn(1, 500),
                            minP = minPText.toDoubleOrNull()?.coerceIn(0.0, 1.0),
                            stopSequences = parsedStops,
                            assistantPrefill = prefillText.trim(),
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

private fun filterDecimalInput(raw: String, allowNegative: Boolean): String {
    val sb = StringBuilder()
    var hasDot = false
    for ((idx, ch) in raw.withIndex()) {
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !hasDot -> {
                hasDot = true
                sb.append(ch)
            }
            ch == '-' && allowNegative && idx == 0 -> sb.append(ch)
        }
    }
    return sb.toString().take(6)
}
