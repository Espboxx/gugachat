package com.gugachat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.OutputLengthPolicy

private fun formatMinLengthPillLabel(value: Int?, isZh: Boolean): String = when (value) {
    null -> if (isZh) "不限" else "Off"
    else -> if (isZh) "≥${value}字" else "≥${value}w"
}

private fun formatMinLengthValueBadge(value: Int?, isZh: Boolean): String = when (value) {
    null -> if (isZh) "不限" else "Off"
    else -> if (isZh) "≥ ${value} 字" else "≥ $value words"
}

/**
 * Full minimum-output-length control intended for `ChatParamsScreen` / `ChatParamsSheet`.
 * All strings are bilingual via [isZh].
 */
@Composable
fun OutputLengthControlSection(
    params: ChatParams,
    mode: ConversationMode,
    isZh: Boolean,
    modelId: String? = null,
    modelMeta: OnlineChatModelDto? = null,
    windowLimit: Int = 128_000,
    onUpdateMinOutput: (minChars: Int?, autoContinue: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutputLengthControlBody(
        params = params,
        mode = mode,
        isZh = isZh,
        compact = false,
        title = if (isZh) "最低输出字数" else "Min Output Length",
        subtitle = null,
        showAutoContinueToggle = true,
        modelId = modelId,
        modelMeta = modelMeta,
        windowLimit = windowLimit,
        onUpdateMinOutput = onUpdateMinOutput,
        modifier = modifier,
    )
}

/**
 * Compact synced minimum-output-length control for `ModelContextSettingsDrawer`,
 * `RpStudioWorldLoreTab`, and `NovelStudioUi`.
 * All strings are bilingual via [isZh].
 */
@Composable
fun CompactOutputLengthControl(
    params: ChatParams,
    mode: ConversationMode,
    isZh: Boolean,
    onUpdateMinOutput: (minChars: Int?, autoContinue: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showAutoContinueToggle: Boolean = true,
    modelId: String? = null,
    modelMeta: OnlineChatModelDto? = null,
    windowLimit: Int = 128_000,
) {
    val resolvedTitle = title ?: if (isZh) "最低输出字数" else "Min Output Length"
    OutputLengthControlBody(
        params = params,
        mode = mode,
        isZh = isZh,
        compact = true,
        title = resolvedTitle,
        subtitle = subtitle,
        showAutoContinueToggle = showAutoContinueToggle,
        modelId = modelId,
        modelMeta = modelMeta,
        windowLimit = windowLimit,
        onUpdateMinOutput = onUpdateMinOutput,
        modifier = modifier,
    )
}

@Composable
private fun OutputLengthControlBody(
    params: ChatParams,
    mode: ConversationMode,
    isZh: Boolean,
    compact: Boolean,
    title: String,
    subtitle: String?,
    showAutoContinueToggle: Boolean,
    modelId: String?,
    modelMeta: OnlineChatModelDto?,
    windowLimit: Int,
    onUpdateMinOutput: (minChars: Int?, autoContinue: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val presets = OutputLengthPolicy.PRESET_LENGTHS
    val currentMin = OutputLengthPolicy.normalizeMinOutputChars(params.minOutputChars)
    val isCustomInitial = currentMin != null && currentMin !in presets

    var showCustomInput by remember { mutableStateOf(isCustomInitial) }
    var customText by remember { mutableStateOf(currentMin?.toString().orEmpty()) }

    LaunchedEffect(currentMin) {
        if (currentMin == null) {
            if (!showCustomInput) customText = ""
        } else {
            val expected = currentMin.toString()
            if (customText != expected) customText = expected
            if (currentMin !in presets) {
                showCustomInput = true
            }
        }
    }

    val requiredFloor = remember(params.minOutputChars, params.enableReasoning, params.reasoningEffort, mode, modelId, modelMeta, windowLimit, isZh) {
        OutputLengthPolicy.computeRequiredMaxTokensFloor(
            params = params,
            mode = mode,
            modelId = modelId,
            modelMeta = modelMeta,
            windowLimit = windowLimit,
            isEnglish = !isZh,
        )
    }

    val autoFloorHint = if (requiredFloor != null) {
        when {
            params.maxTokens != null && params.maxTokens < requiredFloor -> if (isZh) {
                "单次最大输出将自动放宽至 ≥ ${formatContextTokenBadge(requiredFloor)} 以避免截断"
            } else {
                "Max output will auto-floor to ≥ ${formatContextTokenBadge(requiredFloor)} to prevent cutoff"
            }
            params.maxTokens == null && requiredFloor > 4096 -> if (isZh) {
                "预计需 ≥ ${formatContextTokenBadge(requiredFloor)} 输出额度，发送时将自动保障下限"
            } else {
                "Estimated ≥ ${formatContextTokenBadge(requiredFloor)} output budget; auto-floored when required"
            }
            else -> null
        }
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = if (compact) 12.sp else 13.5.sp,
                    fontWeight = if (compact) FontWeight.Bold else FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    GuMetaText(text = subtitle, color = c.muted)
                }
            }
            GuMetaText(
                text = formatMinLengthValueBadge(currentMin, isZh),
                color = if (currentMin != null) c.primarySoftInk else c.muted,
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets, key = { it ?: -1 }) { presetVal ->
                val selected = !showCustomInput && currentMin == presetVal
                GuPill(
                    label = formatMinLengthPillLabel(presetVal, isZh),
                    selected = selected,
                    onClick = {
                        showCustomInput = false
                        customText = presetVal?.toString().orEmpty()
                        val nextAuto = if (presetVal == null) false else params.autoContinueShortReply
                        onUpdateMinOutput(presetVal, nextAuto)
                    },
                )
            }
            item(key = "__min_len_custom__") {
                GuPill(
                    label = if (isZh) "自定义" else "Custom",
                    selected = showCustomInput,
                    onClick = {
                        if (!showCustomInput) {
                            if (customText.isBlank() && currentMin != null) {
                                customText = currentMin.toString()
                            }
                            showCustomInput = true
                        }
                    },
                )
            }
        }

        AnimatedVisibility(visible = showCustomInput) {
            GuTextField(
                value = customText,
                onValueChange = { raw ->
                    val digits = raw.filter(Char::isDigit).take(5)
                    customText = digits
                    if (digits.isEmpty()) {
                        onUpdateMinOutput(null, false)
                    } else {
                        digits.toIntOrNull()?.let { parsed ->
                            if (parsed >= OutputLengthPolicy.MIN_OUTPUT_CHARS_LOWER) {
                                val clamped = parsed.coerceIn(
                                    OutputLengthPolicy.MIN_OUTPUT_CHARS_LOWER,
                                    OutputLengthPolicy.MIN_OUTPUT_CHARS_UPPER,
                                )
                                onUpdateMinOutput(clamped, params.autoContinueShortReply)
                            }
                        }
                    }
                },
                placeholder = if (isZh) {
                    "输入 ${OutputLengthPolicy.MIN_OUTPUT_CHARS_LOWER} ~ ${OutputLengthPolicy.MIN_OUTPUT_CHARS_UPPER} 字"
                } else {
                    "${OutputLengthPolicy.MIN_OUTPUT_CHARS_LOWER} ~ ${OutputLengthPolicy.MIN_OUTPUT_CHARS_UPPER} words"
                },
                trailingIcon = {
                    Text(
                        text = if (isZh) "字" else "w",
                        color = c.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }

        if (autoFloorHint != null) {
            GuMetaText(
                text = autoFloorHint,
                color = c.primarySoftInk,
            )
        }

        if (showAutoContinueToggle) {
            AnimatedVisibility(visible = currentMin != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!compact) {
                        HorizontalDivider(
                            thickness = theme.borderWidth,
                            color = c.border.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    GuSwitchRow(
                        label = if (isZh) "字数不足时自动续写" else "Auto-continue short replies",
                        subtitle = if (isZh) {
                            "未达最低字数 85% 时自动续写（最多 2 次，消耗额外 Token）"
                        } else {
                            "Auto-continue up to 2 passes if under 85% of target (uses extra tokens)"
                        },
                        checked = params.autoContinueShortReply,
                        onCheckedChange = { enabled ->
                            onUpdateMinOutput(currentMin, enabled)
                        },
                    )
                }
            }
        }
    }
}
