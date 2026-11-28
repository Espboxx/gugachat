package com.gugachat.app.core.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GuStatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = LocalAppTheme.current.colors.page,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = { GuModalDragHandle() },
    applyStatusBarsPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedModifier = if (applyStatusBarsPadding) modifier.statusBarsPadding() else modifier
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        modifier = resolvedModifier,
        content = content,
    )
}

@Composable
fun GuHairlineDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
    alpha: Float = 0.5f,
    color: Color = LocalAppTheme.current.colors.border,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color.copy(alpha = alpha),
    )
}

/**
 * Reusable full-screen subpage shell for dense settings, prompt preset editors, and worldbook
 * entry managers (zero swipe-dismiss conflict).
 *
 * Uses `statusBarsPadding().imePadding()` WITHOUT extra `navigationBarsPadding()` because
 * [com.gugachat.app.ui.navigation.GuGuChatRoot] already applies navigation bar bottom padding
 * to the root content container when the bottom navigation bar is hidden.
 */
@Composable
fun GuFullscreenPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backContentDescription: String? = null,
    handleBack: Boolean = true,
    containerColor: Color = LocalAppTheme.current.colors.page,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppTheme.current.colors
    if (handleBack) {
        BackHandler(onBack = onBack)
    }

    Surface(
        color = containerColor,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                        tint = c.ink,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = title,
                        color = c.ink,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = c.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailing != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = trailing,
                    )
                }
            }
            GuHairlineDivider(alpha = 0.55f)
            content()
        }
    }
}

@Composable
fun GuSubpageTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backLabel,
                    tint = c.primary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = backLabel,
                    color = c.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = title,
                color = c.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
        GuHairlineDivider(thickness = theme.borderWidth, alpha = 0.55f)
    }
}

@Composable
fun <T> GuPushPopContainer(
    targetState: T,
    isForward: (initial: T, target: T) -> Boolean,
    modifier: Modifier = Modifier,
    label: String = "gu_push_pop",
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            if (isForward(initialState, targetState)) {
                (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(140)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(140)))
            }
        },
        label = label,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun GuPushPopContainer(
    showingSubpage: Boolean,
    modifier: Modifier = Modifier,
    label: String = "gu_push_pop_bool",
    content: @Composable AnimatedContentScope.(Boolean) -> Unit,
) {
    GuPushPopContainer(
        targetState = showingSubpage,
        isForward = { initial, target -> !initial && target },
        modifier = modifier,
        label = label,
        content = content,
    )
}

@Composable
fun GuInlineDeleteConfirmStrip(
    prompt: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = c.dangerBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = prompt,
                color = c.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(cancelText, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            GuButton(
                onClick = onConfirm,
                compact = true,
                variant = GuButtonVariant.DANGER,
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(confirmText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun GuAccordionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    preview: String = "",
    emptyText: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailingExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leading != null) leading()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = c.ink,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!expanded && (preview.isNotBlank() || emptyText.isNotBlank())) {
                        Text(
                            text = preview.trim().replace('\n', ' ').ifBlank { emptyText },
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailingExtra != null) trailingExtra()
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun GuPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    extraTrailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = LocalAppTheme.current.colors
    var visible by remember { mutableStateOf(false) }

    GuTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        isError = isError,
        leadingIcon = leadingIcon,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (extraTrailing != null) extraTrailing()
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
fun GuStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: GuStatusTone = GuStatusTone.ERROR,
    icon: ImageVector? = when (tone) {
        GuStatusTone.ERROR, GuStatusTone.WARNING -> Icons.Default.ErrorOutline
        GuStatusTone.SUCCESS -> Icons.Default.CheckCircle
        GuStatusTone.INFO -> null
    },
    progress: Float? = null,
    indeterminateProgress: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppTheme.current.colors
    val (bg, fg) = when (tone) {
        GuStatusTone.ERROR -> c.dangerBg to c.danger
        GuStatusTone.WARNING -> c.warningBg to c.warning
        GuStatusTone.SUCCESS -> c.successBg to c.success
        GuStatusTone.INFO -> c.primarySoft to c.primarySoftInk
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp),
                    )
                }
                Text(
                    text = message,
                    color = fg,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) trailing()
            }
            if (indeterminateProgress || progress != null) {
                GuLinearProgressBar(
                    progress = if (indeterminateProgress) null else progress,
                    color = if (tone == GuStatusTone.INFO) c.primary else fg,
                    trackColor = c.surface,
                )
            }
        }
    }
}

@Composable
fun GuLabeledSliderRow(
    label: String,
    value: Double?,
    defaultAnchor: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Double,
    autoText: String,
    resetText: String,
    onValueChange: (Double?) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    sliderColors: SliderColors? = null,
    formatValue: (Double) -> String = { String.format(Locale.US, "%.2f", it) },
) {
    val c = LocalAppTheme.current.colors
    val effectiveColors = sliderColors ?: SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.surfaceSoft,
        activeTickColor = c.primarySoft,
        inactiveTickColor = c.border,
    )
    val minVal = valueRange.start.toDouble()
    val maxVal = valueRange.endInclusive.toDouble()
    val currentNumeric = value ?: defaultAnchor
    val snapThreshold = step * 0.45

    fun normalize(raw: Double): Double? {
        val clamped = raw.coerceIn(minVal, maxVal)
        val rounded = (clamped * 100.0).roundToInt() / 100.0
        return if (abs(rounded - defaultAnchor) < snapThreshold) null else rounded
    }

    val displayText = if (value == null) autoText else formatValue(value)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = resetText,
                    color = c.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onValueChange(null)
                            onCommit()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            GuInlineStepper(
                valueText = displayText,
                onDecrement = {
                    onValueChange(normalize(currentNumeric - step))
                    onCommit()
                },
                onIncrement = {
                    onValueChange(normalize(currentNumeric + step))
                    onCommit()
                },
                canDecrement = currentNumeric > minVal + 0.001,
                canIncrement = currentNumeric < maxVal - 0.001,
            )
        }
        Slider(
            value = currentNumeric.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { sliderVal ->
                onValueChange(normalize(sliderVal.toDouble()))
            },
            onValueChangeFinished = onCommit,
            valueRange = valueRange,
            colors = effectiveColors,
        )
    }
}

@Composable
fun GuLabeledSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueBadgeText: String? = null,
    subtitle: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    sliderColors: SliderColors? = null,
) {
    val c = LocalAppTheme.current.colors
    val effectiveColors = sliderColors ?: SliderDefaults.colors(
        thumbColor = c.primary,
        activeTrackColor = c.primary,
        inactiveTrackColor = c.surfaceSoft,
        activeTickColor = c.primarySoft,
        inactiveTickColor = c.border,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    GuMetaText(text = subtitle, color = c.muted)
                }
            }
            if (!valueBadgeText.isNullOrBlank()) {
                GuBadge(text = valueBadgeText, variant = GuBadgeVariant.BLUE)
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = effectiveColors,
        )
    }
}

@Composable
fun GuLabeledStepperRow(
    title: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        GuInlineStepper(
            valueText = valueText,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
            canDecrement = canDecrement,
            canIncrement = canIncrement,
        )
    }
}

@Composable
fun GuCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val trackColor = if (checked) c.primary else c.surfaceSoft
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 2.dp,
        label = "gu_compact_switch_offset",
    )
    Box(
        modifier = modifier
            .width(34.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF94A3B8)),
        )
    }
}

@Composable
fun GuVariantSwipePill(
    index: Int,
    total: Int,
    onSwitch: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalAppTheme.current.colors.surfaceSoft,
    arrowColor: Color = LocalAppTheme.current.colors.ink,
    disabledArrowColor: Color = LocalAppTheme.current.colors.muted.copy(alpha = 0.35f),
    labelColor: Color = LocalAppTheme.current.colors.ink,
    border: BorderStroke? = null,
    monospace: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 28.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 25.dp, height = 28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = index > 0) { onSwitch((index - 1).coerceAtLeast(0)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = if (index > 0) arrowColor else disabledArrowColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = "${index + 1}/$total",
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
            Box(
                modifier = Modifier
                    .size(width = 25.dp, height = 28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = index < total - 1) { onSwitch((index + 1).coerceAtMost(total - 1)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (index < total - 1) arrowColor else disabledArrowColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
fun GuCompactActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = !loading,
    icon: ImageVector? = Icons.Default.Bolt,
    fgColor: Color = LocalAppTheme.current.colors.primarySoftInk,
    bgColor: Color = LocalAppTheme.current.colors.primarySoft,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.6.dp,
                    color = fgColor,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = label,
                color = fgColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GuTextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
    supportingMessage: String? = null,
    errorMessage: String? = null,
    isPassword: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = if (singleLine) 1 else 3,
    maxLines: Int = if (singleLine) 1 else 6,
    isBusy: Boolean = false,
    confirmEnabled: Boolean = !isBusy && value.isNotBlank(),
) {
    val c = LocalAppTheme.current.colors
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        containerColor = c.surfaceElevated,
        title = {
            Text(
                text = title,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!supportingMessage.isNullOrBlank()) {
                    Text(
                        text = supportingMessage,
                        color = c.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (isPassword) {
                    GuPasswordField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = placeholder,
                        isError = !errorMessage.isNullOrBlank(),
                    )
                } else {
                    GuTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = placeholder,
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = maxLines,
                        isError = !errorMessage.isNullOrBlank(),
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = c.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            GuButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                compact = true,
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(cancelText, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
fun GuLinearProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    height: Dp = 4.dp,
    rounded: Boolean = true,
    color: Color = LocalAppTheme.current.colors.primary,
    trackColor: Color = LocalAppTheme.current.colors.surfaceSoft,
) {
    val barModifier = modifier
        .height(height)
        .let { if (rounded) it.clip(RoundedCornerShape(999.dp)) else it }
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

@Composable
fun GuRemovableChip(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    removeContentDescription: String? = null,
) {
    val c = LocalAppTheme.current.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = c.surfaceSoft,
        modifier = modifier.heightIn(min = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeContentDescription,
                    tint = c.muted,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
fun GuCollapsibleSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    clearContentDescription: String = "Clear",
    closeContentDescription: String = "Close",
    useTextCloseButton: Boolean = false,
    closeText: String = closeContentDescription,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 6.dp, top = 6.dp),
) {
    val c = LocalAppTheme.current.colors
    AnimatedVisibility(
        visible = visible || query.isNotEmpty(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GuCompactSearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                clearContentDescription = clearContentDescription,
                modifier = Modifier.weight(1f),
            )
            if (useTextCloseButton) {
                TextButton(
                    onClick = {
                        if (query.isNotEmpty()) onQueryChange("")
                        onClose()
                    },
                ) {
                    Text(closeText, color = c.muted, fontSize = 12.sp)
                }
            } else {
                IconButton(
                    onClick = {
                        if (query.isNotEmpty()) onQueryChange("")
                        onClose()
                    },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = closeContentDescription,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GuHorizontalFadeChipStrip(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    fadeColor: Color = LocalAppTheme.current.colors.page,
    fadeWidth: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val w = fadeWidth.toPx()
                val brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, fadeColor),
                    startX = size.width - w,
                    endX = size.width,
                )
                onDrawWithContent {
                    drawContent()
                    if (listState.canScrollForward) {
                        drawRect(
                            brush = brush,
                            topLeft = Offset(size.width - w, 0f),
                            size = Size(w, size.height),
                        )
                    }
                }
            },
        content = content,
    )
}

@Composable
fun GuHorizontalFadeOverlayStrip(
    modifier: Modifier = Modifier,
    fadeColor: Color = LocalAppTheme.current.colors.page,
    fadeWidth: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(fadeWidth)
                .height(32.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, fadeColor),
                    ),
                ),
        )
    }
}
