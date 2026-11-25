package com.gugachat.app.core.theme

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            toast?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(theme.pillRadius),
                    color = if (msg.isError) c.dangerBg else c.tag,
                    border = BorderStroke(
                        width = theme.borderWidth,
                        color = if (msg.isError) c.danger else c.outline,
                    ),
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = msg.text,
                        color = if (msg.isError) c.danger else c.tagInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GuAwningHeader(
    modifier: Modifier = Modifier,
    includeStatusBarPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.awning, c.page)))
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = c.border,
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke,
                )
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

    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(theme.borderWidth, effectiveBorderColor),
        shadowElevation = if (theme.isDark) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
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
    val minHeight = if (compact) 34.dp else 44.dp

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
        GuButtonVariant.SUBTLE -> c.primaryDark
    }
    val borderStroke = when (variant) {
        GuButtonVariant.PRIMARY, GuButtonVariant.DANGER -> null
        GuButtonVariant.SECONDARY -> BorderStroke(1.dp, c.border)
        GuButtonVariant.HONEY -> BorderStroke(1.dp, c.accent.copy(alpha = 0.45f))
        GuButtonVariant.SUBTLE -> BorderStroke(1.dp, c.primary.copy(alpha = 0.25f))
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
        selected && useHoneySelected -> c.accent.copy(alpha = 0.65f)
        selected -> c.primary
        else -> c.border
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (enabled) bg else bg.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            color = if (enabled) fg else fg.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
        GuBadgeVariant.BLUE -> Triple(c.primarySoft, c.primaryDark, c.primary.copy(alpha = 0.35f))
        GuBadgeVariant.EMERALD -> Triple(c.successBg, c.success, c.success.copy(alpha = 0.35f))
        GuBadgeVariant.AMBER -> Triple(c.warningBg, c.warning, c.warning.copy(alpha = 0.35f))
        GuBadgeVariant.HONEY -> Triple(c.tag, c.tagInk, c.outline.copy(alpha = 0.45f))
        GuBadgeVariant.DANGER -> Triple(c.dangerBg, c.danger, c.danger.copy(alpha = 0.35f))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(theme.pillRadius),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                unfocusedContainerColor = c.surface,
                disabledContainerColor = c.surfaceSoft,
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
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = theme.borderWidth,
                color = c.border,
                shape = RoundedCornerShape(theme.cardRadius),
            )
            .background(c.surfaceSoft.copy(alpha = 0.6f), RoundedCornerShape(theme.cardRadius))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = c.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = c.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
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
        containerColor = c.surface,
        titleContentColor = c.ink,
        textContentColor = c.muted,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Text(text = message, fontSize = 14.sp)
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
