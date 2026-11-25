package com.gugachat.app.core.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeStyle(val wireValue: String) {
    GUGUGA("guguga"),
    CLASSIC("classic");

    companion object {
        fun fromWire(value: String?): ThemeStyle =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: GUGUGA
    }
}

enum class ColorMode(val wireValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromWire(value: String?): ColorMode =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

@Immutable
data class AppColors(
    val page: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val awning: Color,
    val plankGrain: Color,
    val primary: Color,
    val primaryDark: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val primarySoftInk: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val accentSoft: Color,
    val tag: Color,
    val tagInk: Color,
    val ink: Color,
    val muted: Color,
    val outline: Color,
    val border: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val shadowColor: Color,
)

@Immutable
data class AppThemeSpec(
    val style: ThemeStyle,
    val isDark: Boolean,
    val colors: AppColors,
    val borderWidth: Dp,
    val cardRadius: Dp,
    val pillRadius: Dp,
    val inputRadius: Dp,
)

val GUGUGA_LIGHT = AppColors(
    page = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF0F4FA),
    awning = Color(0xFFEEF3FF),
    plankGrain = Color(0x144D6BFE),
    primary = Color(0xFF4D6BFE),
    primaryDark = Color(0xFF3956E0),
    primaryDeep = Color(0xFF2842BA),
    primarySoft = Color(0xFFEDF2FF),
    primarySoftInk = Color(0xFF2842BA),
    surfaceElevated = Color(0xFFFFFFFF),
    accent = Color(0xFFD97706),
    accentSoft = Color(0xFFFFF8EB),
    tag = Color(0xFFFEF3C7),
    tagInk = Color(0xFF92400E),
    ink = Color(0xFF141824),
    muted = Color(0xFF647087),
    outline = Color(0xFFCFD8E8),
    border = Color(0xFFE5EAF2),
    success = Color(0xFF16A34A),
    successBg = Color(0xFFDCFCE7),
    warning = Color(0xFFD97706),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEE2E2),
    shadowColor = Color(0x14141824),
)

val GUGUGA_DARK = AppColors(
    page = Color(0xFF0C1018),
    surface = Color(0xFF151B28),
    surfaceSoft = Color(0xFF1C2436),
    awning = Color(0xFF121826),
    plankGrain = Color(0x1A5B7CFF),
    primary = Color(0xFF5B7CFF),
    primaryDark = Color(0xFF4364E8),
    primaryDeep = Color(0xFF314EC4),
    primarySoft = Color(0xFF1A2540),
    primarySoftInk = Color(0xFFA8C0FF),
    surfaceElevated = Color(0xFF1F283C),
    accent = Color(0xFFF5A623),
    accentSoft = Color(0xFF2A2115),
    tag = Color(0xFF382A16),
    tagInk = Color(0xFFFDE68A),
    ink = Color(0xFFF0F4FC),
    muted = Color(0xFF93A1BA),
    outline = Color(0xFF33415C),
    border = Color(0xFF253047),
    success = Color(0xFF4ADE80),
    successBg = Color(0xFF122C20),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF2E2210),
    danger = Color(0xFFF87171),
    dangerBg = Color(0xFF33161B),
    shadowColor = Color(0x66000000),
)

val CLASSIC_LIGHT = AppColors(
    page = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF9FAFB),
    awning = Color(0xFFFFFFFF),
    plankGrain = Color(0x0A000000),
    primary = Color(0xFF2563EB),
    primaryDark = Color(0xFF1D4ED8),
    primaryDeep = Color(0xFF1E40AF),
    primarySoft = Color(0xFFEFF6FF),
    primarySoftInk = Color(0xFF1D4ED8),
    surfaceElevated = Color(0xFFFFFFFF),
    accent = Color(0xFFD97706),
    accentSoft = Color(0xFFFFFBEB),
    tag = Color(0xFFFEF3C7),
    tagInk = Color(0xFF92400E),
    ink = Color(0xFF111827),
    muted = Color(0xFF6B7280),
    outline = Color(0xFFD1D5DB),
    border = Color(0xFFE5E7EB),
    success = Color(0xFF16A34A),
    successBg = Color(0xFFDCFCE7),
    warning = Color(0xFFD97706),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEE2E2),
    shadowColor = Color(0x14111827),
)

val CLASSIC_DARK = AppColors(
    page = Color(0xFF09090B),
    surface = Color(0xFF18181B),
    surfaceSoft = Color(0xFF27272A),
    awning = Color(0xFF18181B),
    plankGrain = Color(0x0FFFFFFF),
    primary = Color(0xFF3B82F6),
    primaryDark = Color(0xFF2563EB),
    primaryDeep = Color(0xFF1D4ED8),
    primarySoft = Color(0xFF172554),
    primarySoftInk = Color(0xFF93C5FD),
    surfaceElevated = Color(0xFF232329),
    accent = Color(0xFFF59E0B),
    accentSoft = Color(0xFF292114),
    tag = Color(0xFF422006),
    tagInk = Color(0xFFFDE68A),
    ink = Color(0xFFF4F4F5),
    muted = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    border = Color(0xFF2C2C31),
    success = Color(0xFF4ADE80),
    successBg = Color(0xFF0E291C),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF2C1E0B),
    danger = Color(0xFFF87171),
    dangerBg = Color(0xFF311316),
    shadowColor = Color(0x73000000),
)

fun resolveAppTheme(style: ThemeStyle, isDark: Boolean): AppThemeSpec {
    val colors = when (style) {
        ThemeStyle.GUGUGA -> if (isDark) GUGUGA_DARK else GUGUGA_LIGHT
        ThemeStyle.CLASSIC -> if (isDark) CLASSIC_DARK else CLASSIC_LIGHT
    }
    return AppThemeSpec(
        style = style,
        isDark = isDark,
        colors = colors,
        borderWidth = 1.dp,
        cardRadius = if (style == ThemeStyle.GUGUGA) 18.dp else 14.dp,
        pillRadius = 999.dp,
        inputRadius = 14.dp,
    )
}

val LocalAppTheme = staticCompositionLocalOf { resolveAppTheme(ThemeStyle.GUGUGA, false) }

@Composable
fun GuGuChatTheme(
    themeStyle: ThemeStyle = ThemeStyle.GUGUGA,
    colorMode: ColorMode = ColorMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (colorMode) {
        ColorMode.SYSTEM -> systemDark
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val spec = resolveAppTheme(themeStyle, isDark)
    val c = spec.colors

    val materialScheme = if (isDark) {
        val m3Outline = if (themeStyle == ThemeStyle.GUGUGA) Color(0xFF6B7B99) else Color(0xFF71717A)
        darkColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.primarySoftInk,
            secondary = c.accent,
            onSecondary = Color.White,
            secondaryContainer = c.surfaceSoft,
            onSecondaryContainer = c.ink,
            background = c.page,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surfaceSoft,
            onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.page,
            surfaceContainerLow = c.page,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surfaceElevated,
            surfaceContainerHighest = c.surfaceSoft,
            outline = m3Outline,
            outlineVariant = c.border,
            error = c.danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.primarySoftInk,
            secondary = c.accent,
            onSecondary = Color.White,
            secondaryContainer = c.surfaceSoft,
            onSecondaryContainer = c.ink,
            background = c.page,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surfaceSoft,
            onSurfaceVariant = c.muted,
            surfaceContainerLowest = c.page,
            surfaceContainerLow = c.page,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surfaceElevated,
            surfaceContainerHighest = c.surfaceSoft,
            outline = Color(0xFF94A3B8),
            outlineVariant = c.border,
            error = c.danger,
            onError = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalAppTheme provides spec) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
