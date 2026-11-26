package com.gugachat.app.core.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class GuButtonVariant {
    PRIMARY,
    SECONDARY,
    HONEY,
    DANGER,
    SUBTLE,
}

enum class GuBadgeVariant {
    NEUTRAL,
    BLUE,
    EMERALD,
    AMBER,
    HONEY,
    DANGER,
}

data class ToastMessage(
    val id: Long,
    val text: String,
    val isError: Boolean = false,
)

class ToastController {
    private val seq = AtomicLong(0L)
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    fun show(text: String, isError: Boolean = false) {
        if (text.isBlank()) return
        _current.value = ToastMessage(id = seq.incrementAndGet(), text = text, isError = isError)
    }

    fun dismiss(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}

@Composable
fun GuToastHost(
    controller: ToastController,
    modifier: Modifier = Modifier,
) {
    val toast by controller.current.collectAsStateWithLifecycle()
    val theme = LocalAppTheme.current
    val c = theme.colors

    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        delay(2600L)
        controller.dismiss(id)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            toast?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(theme.pillRadius),
                    color = if (msg.isError) c.dangerBg else c.surfaceElevated,
                    border = BorderStroke(
                        width = theme.borderWidth,
                        color = if (msg.isError) c.danger.copy(alpha = 0.55f) else c.outline.copy(alpha = 0.72f),
                    ),
                    shadowElevation = if (theme.isDark) 8.dp else 5.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (msg.isError) c.danger else c.primary),
                        )
                        Text(
                            text = msg.text,
                            color = if (msg.isError) c.danger else c.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuAwningHeader(
    modifier: Modifier = Modifier,
    includeStatusBarPadding: Boolean = true,
    containerBrush: Brush? = null,
    dividerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val effectiveBrush = containerBrush ?: Brush.verticalGradient(listOf(c.awning, c.page))
    val effectiveDividerColor = dividerColor ?: c.border.copy(alpha = 0.85f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(effectiveBrush)
            .drawBehind {
                if (effectiveDividerColor.alpha > 0f) {
                    val stroke = theme.borderWidth.toPx()
                    drawLine(
                        color = effectiveDividerColor,
                        start = Offset(0f, size.height - stroke / 2f),
                        end = Offset(size.width, size.height - stroke / 2f),
                        strokeWidth = stroke,
                    )
                }
            }
            .then(if (includeStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
        content = content,
    )
}

@Composable
fun GuCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalAppTheme.current.colors.surface,
    borderColor: Color? = null,
    cornerRadius: Dp = LocalAppTheme.current.cardRadius,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val effectiveBorderColor = borderColor ?: c.border
    val shape = RoundedCornerShape(cornerRadius)
    val elevation = if (theme.isDark) 0.dp else 0.5.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(theme.borderWidth, effectiveBorderColor),
            shadowElevation = elevation,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            border = BorderStroke(theme.borderWidth, effectiveBorderColor),
            shadowElevation = elevation,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
fun GuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GuButtonVariant = GuButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    contentPadding: PaddingValues = if (compact) {
        PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    } else {
        PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    },
    content: @Composable RowScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.pillRadius)
    val minHeight = if (compact) 36.dp else 46.dp

    val containerColor = when (variant) {
        GuButtonVariant.PRIMARY -> c.primary
        GuButtonVariant.SECONDARY -> c.surface
        GuButtonVariant.HONEY -> c.tag
        GuButtonVariant.DANGER -> c.danger
        GuButtonVariant.SUBTLE -> c.primarySoft
    }
    val contentColor = when (variant) {
        GuButtonVariant.PRIMARY -> Color.White
        GuButtonVariant.SECONDARY -> c.ink
        GuButtonVariant.HONEY -> c.tagInk
        GuButtonVariant.DANGER -> Color.White
        GuButtonVariant.SUBTLE -> c.primarySoftInk
    }
    val borderStroke = when (variant) {
        GuButtonVariant.PRIMARY, GuButtonVariant.DANGER -> null
        GuButtonVariant.SECONDARY -> BorderStroke(theme.borderWidth, c.border)
        GuButtonVariant.HONEY -> BorderStroke(theme.borderWidth, c.accent.copy(alpha = 0.45f))
        GuButtonVariant.SUBTLE -> BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.28f))
    }

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.55f),
        ),
        border = borderStroke,
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
        modifier = modifier.heightIn(min = minHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        content()
    }
}

@Composable
fun GuPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useHoneySelected: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.pillRadius)
    val bg = when {
        selected && useHoneySelected -> c.tag
        selected -> c.primary
        else -> c.surface
    }
    val fg = when {
        selected && useHoneySelected -> c.tagInk
        selected -> Color.White
        else -> c.ink
    }
    val border = when {
        selected && useHoneySelected -> c.accent.copy(alpha = 0.55f)
        selected -> c.primary
        else -> c.border
    }

    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (enabled) bg else bg.copy(alpha = 0.5f),
        border = BorderStroke(theme.borderWidth, border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) fg else fg.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GuBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: GuBadgeVariant = GuBadgeVariant.NEUTRAL,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val (bg, fg, border) = when (variant) {
        GuBadgeVariant.NEUTRAL -> Triple(c.surfaceSoft, c.muted, c.border)
        GuBadgeVariant.BLUE -> Triple(c.primarySoft, c.primarySoftInk, c.primary.copy(alpha = 0.32f))
        GuBadgeVariant.EMERALD -> Triple(c.successBg, c.success, c.success.copy(alpha = 0.36f))
        GuBadgeVariant.AMBER -> Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.38f))
        GuBadgeVariant.HONEY -> Triple(c.tag, c.tagInk, c.accent.copy(alpha = 0.42f))
        GuBadgeVariant.DANGER -> Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.38f))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(theme.pillRadius),
        color = bg,
        border = BorderStroke(theme.borderWidth, border),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 2.5.dp),
        )
    }
}

@Composable
fun GuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.inputRadius)
    val unfocusedFill = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.72f else 0.55f)

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = c.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            textStyle = TextStyle(
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
            ),
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        color = c.muted,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.ink,
                unfocusedTextColor = c.ink,
                disabledTextColor = c.muted,
                focusedContainerColor = c.surface,
                unfocusedContainerColor = unfocusedFill,
                disabledContainerColor = c.surfaceSoft.copy(alpha = 0.45f),
                focusedBorderColor = c.primary,
                unfocusedBorderColor = c.border,
                errorBorderColor = c.danger,
                cursorColor = c.primary,
            ),
        )
    }
}

@Composable
fun GuEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = theme.borderWidth,
                color = c.border,
                shape = shape,
            )
            .background(
                color = c.surfaceSoft.copy(alpha = if (theme.isDark) 0.55f else 0.65f),
                shape = shape,
            )
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            GuIconTile(
                icon = icon,
                tint = c.primarySoftInk,
                backgroundColor = c.primarySoft,
                size = 48.dp,
                iconSize = 24.dp,
                cornerRadius = 14.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = title,
            color = c.ink,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 17.5.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            GuButton(onClick = onAction, compact = true, variant = GuButtonVariant.SUBTLE) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GuConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    val c = LocalAppTheme.current.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = c.surfaceElevated,
        tonalElevation = 0.dp,
        titleContentColor = c.ink,
        textContentColor = c.muted,
        title = {
            Text(
                text = title,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Text(
                text = message,
                color = c.muted,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            GuButton(
                onClick = onConfirm,
                compact = true,
                variant = if (isDestructive) GuButtonVariant.DANGER else GuButtonVariant.PRIMARY,
            ) {
                Text(confirmText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = c.muted, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
fun GuSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeContentDescription: String = "Close",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
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
                trailing()
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c.surfaceSoft)
                        .border(theme.borderWidth, c.border, CircleShape),
                    contentAlignment = Alignment.Center,
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
        HorizontalDivider(
            thickness = theme.borderWidth,
            color = c.border.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun GuPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = c.surfaceElevated,
        tonalElevation = 0.dp,
        shadowElevation = if (theme.isDark) 10.dp else 6.dp,
        border = BorderStroke(theme.borderWidth, c.outline.copy(alpha = 0.85f)),
        content = content,
    )
}

@Composable
fun GuSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = c.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.primary,
                checkedBorderColor = c.primary,
                uncheckedThumbColor = if (theme.isDark) Color(0xFFB4C0D6) else Color(0xFF64748B),
                uncheckedTrackColor = c.surfaceSoft,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

@Composable
fun GuCompactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearContentDescription: String = "Clear",
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.inputRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(c.surfaceSoft)
            .border(theme.borderWidth, c.border, shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (value.isNotEmpty()) c.primary else c.muted,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = c.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = clearContentDescription,
                    tint = c.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun GuIconTile(
    icon: ImageVector,
    tint: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 17.dp,
    cornerRadius: Dp = 9.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun GuSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = c.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.muted.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun GuGroupedCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalAppTheme.current.colors.surface,
    borderColor: Color? = null,
    cornerRadius: Dp = LocalAppTheme.current.cardRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        border = BorderStroke(theme.borderWidth, borderColor ?: c.border),
        shadowElevation = if (theme.isDark) 0.dp else 0.5.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun GuSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LocalAppTheme.current.colors.primarySoftInk,
    iconBg: Color = LocalAppTheme.current.colors.primarySoft,
    valueText: String? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = false,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    val resolvedIconTint = if (destructive && iconTint == c.primarySoftInk) c.danger else iconTint
    val resolvedIconBg = if (destructive && iconBg == c.primarySoft) c.dangerBg else iconBg

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                GuIconTile(
                    icon = icon,
                    tint = resolvedIconTint,
                    backgroundColor = resolvedIconBg,
                    size = 32.dp,
                    iconSize = 17.dp,
                    cornerRadius = 9.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = if (destructive) c.danger else c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    color = c.muted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.muted.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 56.dp else 14.dp),
                thickness = theme.borderWidth,
                color = c.border.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
fun GuSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<Int, Int> = emptyMap(),
    indicatorDots: Set<Int> = emptySet(),
    compact: Boolean = false,
) {
    if (items.isEmpty()) return
    val theme = LocalAppTheme.current
    val c = theme.colors
    val shape = RoundedCornerShape(theme.pillRadius)
    val trackHeight = if (compact) 32.dp else 36.dp

    Row(
        modifier = modifier
            .height(trackHeight)
            .clip(shape)
            .background(c.surfaceSoft)
            .border(theme.borderWidth, c.border, shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bgColor by animateColorAsState(
                targetValue = if (selected) c.primary else Color.Transparent,
                label = "gu_seg_bg_$index",
            )
            val fgColor by animateColorAsState(
                targetValue = if (selected) Color.White else c.muted,
                label = "gu_seg_fg_$index",
            )
            val count = badgeCounts[index] ?: 0
            val showDot = index in indicatorDots

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(bgColor)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = if (compact) 6.dp else 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = label,
                        color = fgColor,
                        fontSize = if (compact) 11.5.sp else 12.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.22f)
                                    else c.border.copy(alpha = 0.65f),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (count > 99) "99+" else "$count",
                                color = if (selected) Color.White else c.muted,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    if (showDot) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color.White else c.accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuModalDragHandle(
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.outline.copy(alpha = 0.7f)),
        )
    }
}
