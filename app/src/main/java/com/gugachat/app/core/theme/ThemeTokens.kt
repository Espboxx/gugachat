package com.gugachat.app.core.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class UiScalePreset(val wireValue: String, val scale: Float) {
    COMPACT("compact", 0.86f),
    COMFORTABLE("comfortable", 0.92f),
    STANDARD("standard", 1.00f),
    LARGE("large", 1.08f);

    companion object {
        val DEFAULT: UiScalePreset = COMFORTABLE

        fun fromWire(value: String?): UiScalePreset =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
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
    page = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF1EFEA),
    awning = Color(0xFFFAF8F5),
    plankGrain = Color(0x0D4361EE),
    primary = Color(0xFF4361EE),
    primaryDark = Color(0xFF344ED6),
    primaryDeep = Color(0xFF263DB3),
    primarySoft = Color(0xFFEDF1FF),
    primarySoftInk = Color(0xFF2842BA),
    surfaceElevated = Color(0xFFFFFFFF),
    accent = Color(0xFFD97706),
    accentSoft = Color(0xFFFFF7ED),
    tag = Color(0xFFFEF0C7),
    tagInk = Color(0xFF8A3E08),
    ink = Color(0xFF141821),
    muted = Color(0xFF646D7E),
    outline = Color(0xFFCBD2DF),
    border = Color(0xFFE6E3DC),
    success = Color(0xFF15803D),
    successBg = Color(0xFFE6F9EE),
    warning = Color(0xFFB45309),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEE2E2),
)

val GUGUGA_DARK = AppColors(
    page = Color(0xFF0B0E14),
    surface = Color(0xFF131823),
    surfaceSoft = Color(0xFF1B2232),
    awning = Color(0xFF0E131D),
    plankGrain = Color(0x184968F2),
    primary = Color(0xFF4968F2),
    primaryDark = Color(0xFF3A58DE),
    primaryDeep = Color(0xFF2B45B8),
    primarySoft = Color(0xFF182442),
    primarySoftInk = Color(0xFFB2C8FF),
    surfaceElevated = Color(0xFF20283B),
    accent = Color(0xFFF5AD3B),
    accentSoft = Color(0xFF271F13),
    tag = Color(0xFF332614),
    tagInk = Color(0xFFFDE68A),
    ink = Color(0xFFF2F5FC),
    muted = Color(0xFF96A3BD),
    outline = Color(0xFF34425E),
    border = Color(0xFF232C40),
    success = Color(0xFF4ADE80),
    successBg = Color(0xFF102A1E),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF2D210F),
    danger = Color(0xFFF87171),
    dangerBg = Color(0xFF33151B),
)

val CLASSIC_LIGHT = AppColors(
    page = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFEFEFF2),
    awning = Color(0xFFF7F7F8),
    plankGrain = Color(0x06000000),
    primary = Color(0xFF2563EB),
    primaryDark = Color(0xFF1D4ED8),
    primaryDeep = Color(0xFF1E40AF),
    primarySoft = Color(0xFFEBF3FF),
    primarySoftInk = Color(0xFF1D4ED8),
    surfaceElevated = Color(0xFFFFFFFF),
    accent = Color(0xFFC26A08),
    accentSoft = Color(0xFFFFFBEB),
    tag = Color(0xFFFEF3C7),
    tagInk = Color(0xFF8C3D0A),
    ink = Color(0xFF111318),
    muted = Color(0xFF606570),
    outline = Color(0xFFCFD3DC),
    border = Color(0xFFE6E7EB),
    success = Color(0xFF15803D),
    successBg = Color(0xFFE6F9EE),
    warning = Color(0xFFB45309),
    warningBg = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626),
    dangerBg = Color(0xFFFEE2E2),
)

val CLASSIC_DARK = AppColors(
    page = Color(0xFF09090B),
    surface = Color(0xFF121216),
    surfaceSoft = Color(0xFF1C1C22),
    awning = Color(0xFF0C0C10),
    plankGrain = Color(0x0DFFFFFF),
    primary = Color(0xFF2F74F0),
    primaryDark = Color(0xFF2563EB),
    primaryDeep = Color(0xFF1D4ED8),
    primarySoft = Color(0xFF172440),
    primarySoftInk = Color(0xFF9BC2FF),
    surfaceElevated = Color(0xFF24242C),
    accent = Color(0xFFF5A623),
    accentSoft = Color(0xFF261F13),
    tag = Color(0xFF342411),
    tagInk = Color(0xFFFDE68A),
    ink = Color(0xFFF4F4F6),
    muted = Color(0xFFA0A0AB),
    outline = Color(0xFF3A3A44),
    border = Color(0xFF27272F),
    success = Color(0xFF4ADE80),
    successBg = Color(0xFF0E291C),
    warning = Color(0xFFFBBF24),
    warningBg = Color(0xFF2B1E0C),
    danger = Color(0xFFF87171),
    dangerBg = Color(0xFF311317),
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
        borderWidth = 0.5.dp,
        cardRadius = if (style == ThemeStyle.GUGUGA) 18.dp else 14.dp,
        pillRadius = 999.dp,
        inputRadius = if (style == ThemeStyle.GUGUGA) 12.dp else 11.dp,
    )
}

val LocalAppTheme = staticCompositionLocalOf { resolveAppTheme(ThemeStyle.GUGUGA, false) }

@Immutable
data class AppShellController(
    val setChatDrawerOpen: (Boolean) -> Unit = {},
    val setScrollHideBottomBar: (Boolean) -> Unit = {},
    val selectMainTab: (Int) -> Unit = {},
)

val LocalAppShellController = staticCompositionLocalOf { AppShellController() }

private val GuTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.5.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
)

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
            typography = GuTypography,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
