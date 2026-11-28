package com.gugachat.app.ui.chat

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuCompactActionPill
import com.gugachat.app.core.theme.GuFullscreenPage
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuInlineStepper
import com.gugachat.app.core.theme.GuLabeledSliderRow
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.FileContextBudgeter
import com.gugachat.app.domain.chat.PromptPresetBlock
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import java.util.Locale
import kotlin.math.roundToInt

internal val PARAMS_MAX_OUTPUT_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "DEFAULT",
    2048 to "2K",
    4096 to "4K",
    8192 to "8K",
    16384 to "16K",
)

internal val PARAMS_WINDOW_PRESETS: List<Pair<Int?, String>> = listOf(
    null to "AUTO",
    16_000 to "16K",
    32_000 to "32K",
    64_000 to "64K",
    128_000 to "128K",
    200_000 to "200K",
)

internal val PARAMS_HISTORY_PRESETS: List<Int?> = listOf(null, 10, 20, 40)

internal val PARAMS_TOP_K_PRESETS: List<Int?> = listOf(null, 20, 40, 64)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatParamsScreen(
    systemPrompt: String,
    params: ChatParams,
    waterline: ContextWaterlineSummary,
    isCompacting: Boolean,
    compactingProgressText: String? = null,
    nativeModelWindow: Int = waterline.modelContextWindow,
    initialTab: Int = 0,
    onActiveTabChange: ((Int) -> Unit)? = null,
    onSave: (String, ChatParams) -> Unit,
    onCompactNow: () -> Unit,
    onBack: () -> Unit,
    promptPresets: List<PromptPresetEntity> = emptyList(),
    boundPromptPreset: PromptPresetEntity? = null,
    onOpenPresetBlocks: () -> Unit = {},
    minOutputSlot: (@Composable () -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH

    var activeTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) } // 0=Persona&Sampling, 1=ContextBudget, 2=Advanced&Cache
    LaunchedEffect(initialTab) {
        activeTab = initialTab.coerceIn(0, 2)
    }

    // Draft state initialized when sheet opens
    var promptDraft by remember { mutableStateOf(systemPrompt) }
    var promptExpanded by remember { mutableStateOf(false) }

    var temp by remember { mutableFloatStateOf(params.temperature.toFloat().coerceIn(0f, 2f)) }
    var topP by remember { mutableFloatStateOf(params.topP.toFloat().coerceIn(0.1f, 1f)) }
    var effort by remember { mutableStateOf(params.reasoningEffort) }

    val initialMaxTokensPreset = remember(params.maxTokens) {
        PARAMS_MAX_OUTPUT_PRESETS.any { it.first == params.maxTokens }
    }
    var selectedMaxTokensPreset by remember { mutableStateOf(if (initialMaxTokensPreset) params.maxTokens else null) }
    var showCustomMaxTokens by remember { mutableStateOf(!initialMaxTokensPreset && params.maxTokens != null) }
    var customMaxTokensText by remember { mutableStateOf(params.maxTokens?.toString().orEmpty()) }

    val initialWindowPreset = remember(params.contextWindowOverride) {
        PARAMS_WINDOW_PRESETS.any { it.first == params.contextWindowOverride }
    }
    var selectedWindowPreset by remember { mutableStateOf(if (initialWindowPreset) params.contextWindowOverride else null) }
    var showCustomWindow by remember { mutableStateOf(!initialWindowPreset && params.contextWindowOverride != null) }
    var customWindowKText by remember {
        mutableStateOf(params.contextWindowOverride?.let { (it / 1000).toString() }.orEmpty())
    }

    val initialHistoryPreset = remember(params.maxHistoryMessages) {
        PARAMS_HISTORY_PRESETS.any { it == params.maxHistoryMessages }
    }
    var selectedHistoryPreset by remember { mutableStateOf(if (initialHistoryPreset) params.maxHistoryMessages else null) }
    var showCustomHistory by remember { mutableStateOf(!initialHistoryPreset && params.maxHistoryMessages != null) }
    var customHistoryText by remember { mutableStateOf(params.maxHistoryMessages?.toString().orEmpty()) }

    var maxImageTurns by remember { mutableIntStateOf(params.maxImageTurns.coerceIn(0, 10)) }

    var enablePromptCaching by remember { mutableStateOf(params.enablePromptCaching) }
    var enableReasoning by remember { mutableStateOf(params.enableReasoning) }
    var enableAutoMemory by remember { mutableStateOf(params.enableAutoMemory) }
    var enableAutoConsolidate by remember { mutableStateOf(params.enableAutoConsolidate) }

    var minPVal by remember { mutableStateOf(params.minP?.coerceIn(0.0, 1.0)) }
    var freqPenaltyVal by remember { mutableStateOf(params.frequencyPenalty?.coerceIn(-2.0, 2.0)) }
    var presPenaltyVal by remember { mutableStateOf(params.presencePenalty?.coerceIn(-2.0, 2.0)) }
    var repPenaltyVal by remember { mutableStateOf(params.repetitionPenalty?.coerceIn(0.8, 1.5)) }

    val initialTopKPreset = remember(params.topK) {
        PARAMS_TOP_K_PRESETS.any { it == params.topK }
    }
    var selectedTopKPreset by remember { mutableStateOf(if (initialTopKPreset) params.topK else null) }
    var showCustomTopK by remember { mutableStateOf(!initialTopKPreset && params.topK != null) }
    var customTopKText by remember { mutableStateOf(params.topK?.toString().orEmpty()) }

    var stopSequences by remember { mutableStateOf(params.stopSequences.take(4)) }
    var showAddStopInput by remember { mutableStateOf(false) }
    var newStopDraft by remember { mutableStateOf("") }

    var prefillText by remember { mutableStateOf(params.assistantPrefill) }
    val latestParams by rememberUpdatedState(params)

    fun buildCurrentParams(): ChatParams {
        val resolvedMaxTokens = if (showCustomMaxTokens) {
            customMaxTokensText.toIntOrNull()?.coerceIn(256, 131_072)
        } else {
            selectedMaxTokensPreset
        }
        val resolvedWindow = if (showCustomWindow) {
            customWindowKText.toIntOrNull()?.takeIf { it >= 4 }?.let { (it * 1000).coerceIn(4096, 2_000_000) }
        } else {
            selectedWindowPreset
        }
        val resolvedHistory = if (showCustomHistory) {
            customHistoryText.toIntOrNull()?.takeIf { it > 0 }?.coerceIn(1, 500)
        } else {
            selectedHistoryPreset
        }
        val resolvedTopK = if (showCustomTopK) {
            customTopKText.toIntOrNull()?.coerceIn(1, 500)
        } else {
            selectedTopKPreset
        }
        return latestParams.copy(
            temperature = ((temp * 100).roundToInt() / 100.0).coerceIn(0.0, 2.0),
            topP = ((topP * 100).roundToInt() / 100.0).coerceIn(0.1, 1.0),
            maxTokens = resolvedMaxTokens,
            reasoningEffort = effort,
            maxImageTurns = maxImageTurns.coerceIn(0, 10),
            enableReasoning = enableReasoning,
            enableAutoMemory = enableAutoMemory,
            enableAutoConsolidate = enableAutoConsolidate,
            contextWindowOverride = resolvedWindow,
            maxHistoryMessages = resolvedHistory,
            enablePromptCaching = enablePromptCaching,
            frequencyPenalty = freqPenaltyVal,
            presencePenalty = presPenaltyVal,
            repetitionPenalty = repPenaltyVal,
            topK = resolvedTopK,
            minP = minPVal,
            stopSequences = stopSequences.take(4),
            assistantPrefill = prefillText.trim(),
        )
    }

    var lastCommittedPrompt by remember { mutableStateOf(systemPrompt) }
    var lastCommittedParams by remember { mutableStateOf(buildCurrentParams()) }
    val latestOnSave by rememberUpdatedState(onSave)
    val latestBuildParams by rememberUpdatedState<() -> ChatParams> { buildCurrentParams() }
    val latestPromptDraft by rememberUpdatedState(promptDraft)

    LaunchedEffect(params.minOutputChars, params.autoContinueShortReply) {
        lastCommittedParams = lastCommittedParams.copy(
            minOutputChars = params.minOutputChars,
            autoContinueShortReply = params.autoContinueShortReply,
        )
    }

    fun flushIfDirty() {
        val built = latestBuildParams()
        val prompt = latestPromptDraft
        if (prompt != lastCommittedPrompt || built != lastCommittedParams) {
            lastCommittedPrompt = prompt
            lastCommittedParams = built
            latestOnSave(prompt, built)
        }
    }

    val currentBuiltParams = buildCurrentParams()
    val hasPendingChanges = promptDraft != lastCommittedPrompt || currentBuiltParams != lastCommittedParams

    // 400ms debounced auto-save for text inputs (System Prompt, Prefill, custom numbers)
    LaunchedEffect(
        promptDraft,
        prefillText,
        customMaxTokensText,
        customWindowKText,
        customHistoryText,
        customTopKText,
        showCustomMaxTokens,
        showCustomWindow,
        showCustomHistory,
        showCustomTopK,
    ) {
        val built = buildCurrentParams()
        if (promptDraft != lastCommittedPrompt || built != lastCommittedParams) {
            delay(400L)
            flushIfDirty()
        }
    }

    // Flush any pending changes when user dismisses by swipe-down, scrim tap, or close button
    DisposableEffect(Unit) {
        onDispose {
            flushIfDirty()
        }
    }

    fun resetParamsToDefault() {
        val defaults = ChatParams(
            maxFileTurns = latestParams.maxFileTurns,
            activeWindowStartSeq = latestParams.activeWindowStartSeq,
        )
        temp = defaults.temperature.toFloat()
        topP = defaults.topP.toFloat()
        effort = defaults.reasoningEffort
        selectedMaxTokensPreset = defaults.maxTokens
        showCustomMaxTokens = false
        customMaxTokensText = ""
        selectedWindowPreset = defaults.contextWindowOverride
        showCustomWindow = false
        customWindowKText = ""
        selectedHistoryPreset = defaults.maxHistoryMessages
        showCustomHistory = false
        customHistoryText = ""
        maxImageTurns = defaults.maxImageTurns
        enablePromptCaching = defaults.enablePromptCaching
        enableReasoning = defaults.enableReasoning
        enableAutoMemory = defaults.enableAutoMemory
        enableAutoConsolidate = defaults.enableAutoConsolidate
        minPVal = defaults.minP
        freqPenaltyVal = defaults.frequencyPenalty
        presPenaltyVal = defaults.presencePenalty
        repPenaltyVal = defaults.repetitionPenalty
        selectedTopKPreset = defaults.topK
        showCustomTopK = false
        customTopKText = ""
        stopSequences = emptyList()
        showAddStopInput = false
        newStopDraft = ""
        prefillText = ""
        lastCommittedPrompt = promptDraft
        lastCommittedParams = defaults
        latestOnSave(promptDraft, defaults)
    }

    val tabLabels = remember(isZh) {
        if (isZh) {
            listOf("人设与采样", "上下文预算", "高级与缓存")
        } else {
            listOf("Sampling", "Budget", "Advanced")
        }
    }

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
        inactiveTrackColor = c.surfaceSoft,
        activeTickColor = c.primarySoft,
        inactiveTickColor = c.border,
    )

    GuFullscreenPage(
        title = strings.chat.paramsTitle,
        backContentDescription = strings.common.back,
        onBack = {
            flushIfDirty()
            onBack()
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (hasPendingChanges) {
                        if (isZh) "保存中…" else "Saving…"
                    } else {
                        if (isZh) "已自动保存" else "Auto-saved"
                    },
                    color = if (hasPendingChanges) c.primary else c.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = strings.common.reset,
                    color = c.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { resetParamsToDefault() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        GuSegmentedControl(
            items = tabLabels,
            selectedIndex = activeTab,
            onSelect = {
                activeTab = it
                onActiveTabChange?.invoke(it)
            },
            compact = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                when (activeTab) {
                    // Tab 0: Persona & Sampling (fits comfortably in 1 screen)
                    0 -> {
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = strings.chat.systemPrompt,
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(c.surfaceSoft)
                                        .clickable { promptExpanded = !promptExpanded }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = if (promptExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = if (promptExpanded) {
                                            if (isZh) "收起" else "Collapse"
                                        } else {
                                            if (isZh) "展开" else "Expand"
                                        },
                                        color = c.muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            GuTextField(
                                value = promptDraft,
                                onValueChange = { promptDraft = it },
                                placeholder = strings.chat.systemPromptPlaceholder,
                                singleLine = false,
                                minLines = if (promptExpanded) 7 else 3,
                                maxLines = if (promptExpanded) 12 else 3,
                            )
                        }

                        val boundPreset = boundPromptPreset
                        val blockCount = remember(boundPreset?.id, boundPreset?.blocksJson) {
                            if (boundPreset == null) {
                                0
                            } else {
                                runCatching {
                                    AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), boundPreset.blocksJson)
                                        .count { it.enabled && !it.marker }
                                }.getOrDefault(0)
                            }
                        }
                        val presetSubtitle = if (boundPreset != null) {
                            if (isZh) "${boundPreset.name} · 已启用 $blockCount 个提示槽位" else "${boundPreset.name} · $blockCount active blocks"
                        } else {
                            val avail = promptPresets.size
                            if (avail > 0) {
                                if (isZh) "未挂载预设（点击从 $avail 套预设中选择）" else "None (tap to pick from $avail presets)"
                            } else {
                                if (isZh) "暂无预设，可在「创作→设定」或导入页添加" else "No presets imported yet"
                            }
                        }
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            GuSettingsRow(
                                title = if (isZh) "提示词预设" else "Prompt Preset",
                                subtitle = presetSubtitle,
                                icon = Icons.Default.Tune,
                                iconTint = c.primarySoftInk,
                                iconBg = c.primarySoft,
                                showChevron = true,
                                onClick = onOpenPresetBlocks,
                            )
                        }

                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            // Temperature
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = strings.chat.temperature,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    ValueTag(String.format(Locale.US, "%.2f", temp))
                                }
                                Slider(
                                    value = temp,
                                    onValueChange = { temp = it },
                                    onValueChangeFinished = { flushIfDirty() },
                                    valueRange = 0f..2f,
                                    colors = sliderColors,
                                )
                            }

                            SheetHairlineDivider()

                            // Top-P
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = strings.chat.topP,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    ValueTag(String.format(Locale.US, "%.2f", topP))
                                }
                                Slider(
                                    value = topP,
                                    onValueChange = { topP = it },
                                    onValueChangeFinished = { flushIfDirty() },
                                    valueRange = 0.1f..1f,
                                    colors = sliderColors,
                                )
                            }

                            SheetHairlineDivider()

                            // Reasoning effort 4-segment bar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = strings.chat.reasoningEffort,
                                    color = c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                GuSegmentedControl(
                                    items = effortLabels,
                                    selectedIndex = selectedEffortIndex,
                                    onSelect = { idx ->
                                        effort = effortKeys.getOrNull(idx)
                                        flushIfDirty()
                                    },
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            SheetHairlineDivider()

                            // Max Output Tokens presets + conditional custom box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isZh) "单次最大输出" else strings.chat.maxTokens,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuMetaText(
                                        text = currentBuiltParams.maxTokens?.let { formatContextTokenBadge(it) }
                                            ?: if (isZh) "默认" else "Default",
                                        color = if (currentBuiltParams.maxTokens != null) c.primarySoftInk else c.muted,
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PARAMS_MAX_OUTPUT_PRESETS, key = { it.second }) { (tokens, label) ->
                                        GuPill(
                                            label = if (tokens == null) (if (isZh) "默认" else "Default") else label,
                                            selected = !showCustomMaxTokens && selectedMaxTokensPreset == tokens,
                                            onClick = {
                                                showCustomMaxTokens = false
                                                selectedMaxTokensPreset = tokens
                                                flushIfDirty()
                                            },
                                        )
                                    }
                                    item(key = "__max_tok_custom__") {
                                        GuPill(
                                            label = if (isZh) "自定义" else "Custom",
                                            selected = showCustomMaxTokens,
                                            onClick = {
                                                if (!showCustomMaxTokens) {
                                                    if (customMaxTokensText.isBlank() && selectedMaxTokensPreset != null) {
                                                        customMaxTokensText = selectedMaxTokensPreset.toString()
                                                    }
                                                    showCustomMaxTokens = true
                                                }
                                            },
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = showCustomMaxTokens) {
                                    GuTextField(
                                        value = customMaxTokensText,
                                        onValueChange = { customMaxTokensText = it.filter(Char::isDigit).take(6) },
                                        placeholder = if (isZh) "输入 256 ~ 131072" else "256 ~ 131072 tokens",
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                    )
                                }
                            }

                            if (minOutputSlot != null) {
                                SheetHairlineDivider()
                                minOutputSlot()
                            }
                        }
                    }

                    // Tab 1: Context Budget
                    1 -> {
                        val ctxUi = strings.contextUi
                        val sentTokens = waterline.sentTokens.coerceAtLeast(0)
                        val sentPct = waterline.sentPercentage.coerceIn(0, 100)
                        val limitTokens = waterline.effectiveLimitTokens.coerceAtLeast(1)
                        val sentShort = FileContextBudgeter.formatShortTokens(sentTokens)
                        val limitShort = FileContextBudgeter.formatShortTokens(limitTokens)
                        val archiveShort = FileContextBudgeter.formatShortTokens(waterline.archiveTotalTokens.coerceAtLeast(sentTokens))
                        val nativeWindowShort = FileContextBudgeter.formatShortTokens(nativeModelWindow.coerceAtLeast(1))

                        // Tri-color waterline card with inline [⚡ 立即压缩] pill
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (waterline.archiveTotalTokens > sentTokens) {
                                    GuBadge(
                                        text = ctxUi.archiveTotalLabel.fmt("archive" to archiveShort),
                                        variant = if (waterline.hardOverflow) GuBadgeVariant.DANGER else GuBadgeVariant.AMBER,
                                    )
                                }
                                val pctVariant = when {
                                    waterline.hardOverflow -> GuBadgeVariant.DANGER
                                    waterline.isSliding || sentPct >= 80 -> GuBadgeVariant.AMBER
                                    else -> GuBadgeVariant.EMERALD
                                }
                                GuBadge(text = "${sentPct}%", variant = pctVariant)
                            }

                            Spacer(Modifier.height(8.dp))

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
                                    .height(7.dp)
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

                            Spacer(Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                WaterlineLegendDot(c.accent, ctxUi.legendSystemSummary, FileContextBudgeter.formatShortTokens(systemTokens))
                                WaterlineLegendDot(c.success, ctxUi.legendMemoryLore, FileContextBudgeter.formatShortTokens(memoryTokens))
                                WaterlineLegendDot(histColor, ctxUi.legendHistoryDraft, FileContextBudgeter.formatShortTokens(historyTokens))
                                if (waterline.fileTokens > 0) {
                                    WaterlineLegendDot(c.muted, ctxUi.legendFiles, FileContextBudgeter.formatShortTokens(waterline.fileTokens))
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Status/warning bar with inline [⚡ 立即压缩] pill on the right
                            val isDanger = waterline.hardOverflow
                            val isAlert = isDanger || waterline.isSliding || waterline.trimmedMessageCount > 0
                            val stripBg = when {
                                isDanger -> c.dangerBg
                                isAlert -> c.warningBg
                                else -> c.surfaceSoft
                            }
                            val stripFg = when {
                                isDanger -> c.danger
                                isAlert -> c.warning
                                else -> c.muted
                            }
                            val stripCopy = when {
                                isDanger -> ctxUi.hardOverflowWarning.fmt(
                                    "sent" to sentShort,
                                    "window" to FileContextBudgeter.formatShortTokens(waterline.modelContextWindow),
                                )
                                isAlert -> ctxUi.slidingExplanation.fmt(
                                    "archive" to archiveShort,
                                    "trimmed" to waterline.trimmedMessageCount,
                                )
                                else -> ctxUi.healthyExplanation
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(stripBg)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDanger) Icons.Default.WarningAmber else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = stripFg,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stripCopy,
                                    color = stripFg,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactInlineActionPill(
                                    label = if (isCompacting && !compactingProgressText.isNullOrBlank()) {
                                        compactingProgressText
                                    } else {
                                        strings.chat.compactNow
                                    },
                                    loading = isCompacting,
                                    fgColor = if (isDanger) Color.White else if (isAlert) c.tagInk else c.primarySoftInk,
                                    bgColor = if (isDanger) c.danger else if (isAlert) c.tag else c.primarySoft,
                                    onClick = onCompactNow,
                                )
                            }
                        }

                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            // Context window limit preset strip
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isZh) "窗口上限" else ctxUi.contextLimitLabel,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuMetaText(
                                        text = currentBuiltParams.contextWindowOverride?.let { formatContextTokenBadge(it) }
                                            ?: ctxUi.contextLimitAuto.fmt("window" to nativeWindowShort),
                                        color = if (currentBuiltParams.contextWindowOverride != null) c.primarySoftInk else c.muted,
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PARAMS_WINDOW_PRESETS, key = { it.second }) { (tokens, label) ->
                                        GuPill(
                                            label = if (tokens == null) (if (isZh) "自动" else "Auto") else label,
                                            selected = !showCustomWindow && selectedWindowPreset == tokens,
                                            onClick = {
                                                showCustomWindow = false
                                                selectedWindowPreset = tokens
                                                flushIfDirty()
                                            },
                                        )
                                    }
                                    item(key = "__win_custom__") {
                                        GuPill(
                                            label = if (isZh) "自定义" else "Custom",
                                            selected = showCustomWindow,
                                            onClick = {
                                                if (!showCustomWindow) {
                                                    if (customWindowKText.isBlank() && selectedWindowPreset != null) {
                                                        customWindowKText = (selectedWindowPreset!! / 1000).toString()
                                                    }
                                                    showCustomWindow = true
                                                }
                                            },
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = showCustomWindow) {
                                    GuTextField(
                                        value = customWindowKText,
                                        onValueChange = { customWindowKText = it.filter(Char::isDigit).take(4) },
                                        placeholder = if (isZh) "输入 4 ~ 2000 (单位 K)" else "4 ~ 2000 (K)",
                                        trailingIcon = { GuMetaText("K") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                    )
                                }
                            }

                            SheetHairlineDivider()

                            // History message count preset strip
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isZh) "历史保留条数" else ctxUi.maxHistoryLabel,
                                        color = c.ink,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuMetaText(
                                        text = currentBuiltParams.maxHistoryMessages?.let {
                                            if (isZh) "最近 $it 条" else "Last $it"
                                        } ?: ctxUi.maxHistoryUnlimited,
                                        color = if (currentBuiltParams.maxHistoryMessages != null) c.primarySoftInk else c.muted,
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PARAMS_HISTORY_PRESETS, key = { it ?: -1 }) { count ->
                                        val label = count?.let { if (isZh) "${it}条" else "$it" }
                                            ?: ctxUi.maxHistoryUnlimited
                                        GuPill(
                                            label = label,
                                            selected = !showCustomHistory && selectedHistoryPreset == count,
                                            onClick = {
                                                showCustomHistory = false
                                                selectedHistoryPreset = count
                                                flushIfDirty()
                                            },
                                        )
                                    }
                                    item(key = "__hist_custom__") {
                                        GuPill(
                                            label = if (isZh) "自定义" else "Custom",
                                            selected = showCustomHistory,
                                            onClick = {
                                                if (!showCustomHistory) {
                                                    if (customHistoryText.isBlank() && selectedHistoryPreset != null) {
                                                        customHistoryText = selectedHistoryPreset.toString()
                                                    }
                                                    showCustomHistory = true
                                                }
                                            },
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = showCustomHistory) {
                                    GuTextField(
                                        value = customHistoryText,
                                        onValueChange = { customHistoryText = it.filter(Char::isDigit).take(3) },
                                        placeholder = if (isZh) "输入 1 ~ 500 条" else "1 ~ 500 messages",
                                        trailingIcon = { GuMetaText(if (isZh) "条" else "#") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                    )
                                }
                            }

                            SheetHairlineDivider()

                            // Image retention turns single-row GuInlineStepper
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (isZh) "保留识图轮数" else strings.chat.imageTurnsLabel,
                                    color = c.ink,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                GuInlineStepper(
                                    valueText = if (maxImageTurns == 0) {
                                        if (isZh) "不保留" else "None"
                                    } else {
                                        strings.chat.imageTurnsUnit.fmt("n" to maxImageTurns)
                                    },
                                    onDecrement = {
                                        if (maxImageTurns > 0) {
                                            maxImageTurns--
                                            flushIfDirty()
                                        }
                                    },
                                    onIncrement = {
                                        if (maxImageTurns < 10) {
                                            maxImageTurns++
                                            flushIfDirty()
                                        }
                                    },
                                    canDecrement = maxImageTurns > 0,
                                    canIncrement = maxImageTurns < 10,
                                )
                            }
                        }
                    }

                    // Tab 2: Advanced & Cache (Tavern params)
                    else -> {
                        // Top 4 switches unified in 1 GuGroupedCard
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            GuSwitchRow(
                                label = "Prompt Caching",
                                subtitle = if (isZh) "固定系统与历史前缀断点以降低长文费用" else "Pin static prefix breakpoints to save input cost",
                                checked = enablePromptCaching,
                                onCheckedChange = {
                                    enablePromptCaching = it
                                    flushIfDirty()
                                },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                            SheetHairlineDivider(Modifier.padding(horizontal = 14.dp))
                            GuSwitchRow(
                                label = if (isZh) "启用深度思考" else strings.chat.thinkingTitle,
                                checked = enableReasoning,
                                onCheckedChange = {
                                    enableReasoning = it
                                    flushIfDirty()
                                },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                            SheetHairlineDivider(Modifier.padding(horizontal = 14.dp))
                            GuSwitchRow(
                                label = if (isZh) "自动长期记忆" else strings.chat.sessionMemoryToggle,
                                subtitle = strings.chat.sessionMemoryHint,
                                checked = enableAutoMemory,
                                onCheckedChange = {
                                    enableAutoMemory = it
                                    flushIfDirty()
                                },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                            SheetHairlineDivider(Modifier.padding(horizontal = 14.dp))
                            GuSwitchRow(
                                label = if (isZh) "后台自动归档" else strings.chat.bgConsolidateToggle,
                                subtitle = strings.chat.bgConsolidateHint,
                                checked = enableAutoConsolidate,
                                onCheckedChange = {
                                    enableAutoConsolidate = it
                                    flushIfDirty()
                                },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }

                        // Tavern advanced sampling card: Sliders + GuInlineSteppers + Chips
                        GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                            NullableSamplingSliderRow(
                                label = "Min-P",
                                value = minPVal,
                                defaultAnchor = 0.0,
                                valueRange = 0f..1f,
                                step = 0.05,
                                isZh = isZh,
                                sliderColors = sliderColors,
                                onValueChange = { next -> minPVal = next },
                                onCommit = { flushIfDirty() },
                            )
                            SheetHairlineDivider()

                            NullableSamplingSliderRow(
                                label = if (isZh) "频率惩罚" else "Freq Penalty",
                                value = freqPenaltyVal,
                                defaultAnchor = 0.0,
                                valueRange = -2f..2f,
                                step = 0.10,
                                isZh = isZh,
                                sliderColors = sliderColors,
                                onValueChange = { next -> freqPenaltyVal = next },
                                onCommit = { flushIfDirty() },
                            )
                            SheetHairlineDivider()

                            NullableSamplingSliderRow(
                                label = if (isZh) "存在惩罚" else "Pres Penalty",
                                value = presPenaltyVal,
                                defaultAnchor = 0.0,
                                valueRange = -2f..2f,
                                step = 0.10,
                                isZh = isZh,
                                sliderColors = sliderColors,
                                onValueChange = { next -> presPenaltyVal = next },
                                onCommit = { flushIfDirty() },
                            )
                            SheetHairlineDivider()

                            NullableSamplingSliderRow(
                                label = if (isZh) "重复惩罚" else "Repetition Penalty",
                                value = repPenaltyVal,
                                defaultAnchor = 1.0,
                                valueRange = 0.8f..1.5f,
                                step = 0.05,
                                isZh = isZh,
                                sliderColors = sliderColors,
                                onValueChange = { next -> repPenaltyVal = next },
                                onCommit = { flushIfDirty() },
                            )
                            SheetHairlineDivider()

                            // Top-K preset pills
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Top-K", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    GuMetaText(
                                        text = currentBuiltParams.topK?.toString() ?: if (isZh) "默认" else "Default",
                                        color = if (currentBuiltParams.topK != null) c.primarySoftInk else c.muted,
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(PARAMS_TOP_K_PRESETS, key = { it ?: -1 }) { kVal ->
                                        GuPill(
                                            label = kVal?.toString() ?: if (isZh) "默认" else "Default",
                                            selected = !showCustomTopK && selectedTopKPreset == kVal,
                                            onClick = {
                                                showCustomTopK = false
                                                selectedTopKPreset = kVal
                                                flushIfDirty()
                                            },
                                        )
                                    }
                                    item(key = "__topk_custom__") {
                                        GuPill(
                                            label = if (isZh) "自定义" else "Custom",
                                            selected = showCustomTopK,
                                            onClick = {
                                                if (!showCustomTopK) {
                                                    if (customTopKText.isBlank() && selectedTopKPreset != null) {
                                                        customTopKText = selectedTopKPreset.toString()
                                                    }
                                                    showCustomTopK = true
                                                }
                                            },
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = showCustomTopK) {
                                    GuTextField(
                                        value = customTopKText,
                                        onValueChange = { customTopKText = it.filter(Char::isDigit).take(4) },
                                        placeholder = "1 ~ 500",
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                    )
                                }
                            }
                            SheetHairlineDivider()

                            // Stop sequences removable chips + [+ 添加]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isZh) "Stop 停止词" else "Stop Sequences",
                                        color = c.ink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    GuMetaText("${stopSequences.size}/4")
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    stopSequences.forEachIndexed { idx, seq ->
                                        Surface(
                                            onClick = {
                                                stopSequences = stopSequences.filterIndexed { i, _ -> i != idx }
                                                flushIfDirty()
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.primarySoft,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = seq,
                                                    color = c.primarySoftInk,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = strings.common.delete,
                                                    tint = c.primarySoftInk,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        }
                                    }
                                    if (stopSequences.size < 4) {
                                        Surface(
                                            onClick = { showAddStopInput = !showAddStopInput },
                                            shape = RoundedCornerShape(999.dp),
                                            color = c.surfaceSoft,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = c.primary,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Text(
                                                    text = if (isZh) "添加" else "Add",
                                                    color = c.primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }
                                }
                                AnimatedVisibility(visible = showAddStopInput && stopSequences.size < 4) {
                                    fun commitNewStop() {
                                        val clean = newStopDraft.trim()
                                        if (clean.isNotEmpty() && clean !in stopSequences && stopSequences.size < 4) {
                                            stopSequences = stopSequences + clean
                                            newStopDraft = ""
                                            showAddStopInput = false
                                            flushIfDirty()
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuTextField(
                                            value = newStopDraft,
                                            onValueChange = { newStopDraft = it },
                                            placeholder = if (isZh) "例如：{{user}}:" else "e.g. {{user}}:",
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { commitNewStop() }),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            onClick = { commitNewStop() },
                                            enabled = newStopDraft.isNotBlank(),
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (newStopDraft.isNotBlank()) c.primary else c.surfaceSoft,
                                        ) {
                                            Text(
                                                text = strings.common.add,
                                                color = if (newStopDraft.isNotBlank()) Color.White else c.muted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            SheetHairlineDivider()

                            // Compact 2-line Assistant Prefill
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Assistant Prefill",
                                    color = c.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                GuTextField(
                                    value = prefillText,
                                    onValueChange = { prefillText = it },
                                    placeholder = if (isZh) "留空不启用；支持 {{char}}/{{user}} 宏" else "Optional opening text; supports {{char}}/{{user}}",
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
internal fun NullableSamplingSliderRow(
    label: String,
    value: Double?,
    defaultAnchor: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Double,
    isZh: Boolean,
    sliderColors: androidx.compose.material3.SliderColors,
    onValueChange: (Double?) -> Unit,
    onCommit: () -> Unit,
) {
    GuLabeledSliderRow(
        label = label,
        value = value,
        defaultAnchor = defaultAnchor,
        valueRange = valueRange,
        step = step,
        autoText = if (isZh) "默认" else "Auto",
        resetText = if (isZh) "归零" else "Reset",
        sliderColors = sliderColors,
        onValueChange = onValueChange,
        onCommit = onCommit,
    )
}

@Composable
internal fun CompactInlineActionPill(
    label: String,
    loading: Boolean,
    fgColor: Color,
    bgColor: Color,
    onClick: () -> Unit,
) {
    GuCompactActionPill(
        label = label,
        onClick = onClick,
        loading = loading,
        fgColor = fgColor,
        bgColor = bgColor,
    )
}

@Composable
internal fun SheetHairlineDivider(modifier: Modifier = Modifier) {
    GuHairlineDivider(modifier = modifier, alpha = 0.45f)
}

@Composable
internal fun ValueTag(text: String) {
    val c = LocalAppTheme.current.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surfaceSoft)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        GuMetaText(
            text = text,
            color = c.ink,
        )
    }
}

@Composable
internal fun WaterlineLegendDot(
    color: Color,
    label: String,
    value: String,
) {
    val c = LocalAppTheme.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        GuMetaText(
            text = value,
            color = c.ink,
        )
    }
}

