package com.gugachat.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    page = Color(0xFFF4F8FD),
    surface = Color(0xFFFFFDF9),
    surfaceSoft = Color(0xFFEDF4FC),
    awning = Color(0xFFE9F3FF),
    plankGrain = Color(0x1C2F7ED8),
    primary = Color(0xFF2F7ED8),
    primaryDark = Color(0xFF2266B8),
    primaryDeep = Color(0xFF195196),
    primarySoft = Color(0xFFEEF6FF),
    accent = Color(0xFFE58018),
    accentSoft = Color(0xFFFFF8EB),
    tag = Color(0xFFFFF0C8),
    tagInk = Color(0xFF4A2E1B),
    ink = Color(0xFF2A1C13),
    muted = Color(0xFF635347),
    outline = Color(0xFF4A3121),
    border = Color(0xFFD5E2F0),
    success = Color(0xFF15803D),
    successBg = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEE2E2),
    shadowColor = Color(0x2E4A3121),
)

val GUGUGA_DARK = AppColors(
    page = Color(0xFF131A26),
    surface = Color(0xFF1E2839),
    surfaceSoft = Color(0xFF182130),
    awning = Color(0xFF24334A),
    plankGrain = Color(0x146DB8F5),
    primary = Color(0xFF4494E8),
    primaryDark = Color(0xFF2D77CC),
    primaryDeep = Color(0xFF2262B0),
    primarySoft = Color(0xFF192A42),
    accent = Color(0xFFF59D2A),
    accentSoft = Color(0xFF2E2418),
    tag = Color(0xFF443523),
    tagInk = Color(0xFFFDE8BA),
    ink = Color(0xFFF6F0E4),
    muted = Color(0xFFA6B4CA),
    outline = Color(0xFF927C63),
    border = Color(0xFF33435C),
    success = Color(0xFF4ADE80),
    successBg = Color(0x59166534),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0x5992400E),
    danger = Color(0xFFF87171),
    dangerBg = Color(0x59991B1B),
    shadowColor = Color(0x73000000),
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
    accent = Color(0xFFF59E0B),
    accentSoft = Color(0xFF292114),
    tag = Color(0xFF422006),
    tagInk = Color(0xFFFDE68A),
    ink = Color(0xFFF4F4F5),
    muted = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    border = Color(0xFF27272A),
    success = Color(0xFF4ADE80),
    successBg = Color(0x6614532D),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0x6678350F),
    danger = Color(0xFFF87171),
    dangerBg = Color(0x667F1D1D),
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
        borderWidth = if (style == ThemeStyle.GUGUGA) 2.dp else 1.dp,
        cardRadius = if (style == ThemeStyle.GUGUGA) 20.dp else 14.dp,
        pillRadius = 999.dp,
        inputRadius = if (style == ThemeStyle.GUGUGA) 14.dp else 10.dp,
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
        darkColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.ink,
            secondary = c.accent,
            onSecondary = Color.White,
            secondaryContainer = c.tag,
            onSecondaryContainer = c.tagInk,
            background = c.page,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surfaceSoft,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            outlineVariant = c.border,
            error = c.danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.ink,
            secondary = c.accent,
            onSecondary = Color.White,
            secondaryContainer = c.tag,
            onSecondaryContainer = c.tagInk,
            background = c.page,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surfaceSoft,
            onSurfaceVariant = c.muted,
            outline = c.outline,
            outlineVariant = c.border,
            error = c.danger,
            onError = Color.White,
        )
    }

    CompositionLocalProvider(LocalAppTheme provides spec) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content,
        )
    }
}
