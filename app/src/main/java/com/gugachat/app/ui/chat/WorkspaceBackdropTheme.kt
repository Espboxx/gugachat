package com.gugachat.app.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gugachat.app.core.theme.AppThemeSpec
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.resolveAppTheme

@Immutable
data class NovelPaperTheme(
    val id: String,
    val isNight: Boolean,
    val bg: Color,
    val cardBg: Color,
    val surface: Color,
    val chromeBg: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val codeBg: Color,
    val codeHeaderBg: Color,
)

private val NOVEL_PAPER_CREAM = NovelPaperTheme(
    id = "cream",
    isNight = false,
    bg = Color(0xFFFDFBF6),
    cardBg = Color(0xFFFCF8EF),
    surface = Color(0xFFF3EFE6),
    chromeBg = Color(0xF2FAF7F0),
    ink = Color(0xFF1F1C1B),
    muted = Color(0xFF68625C),
    accent = Color(0xFF4361EE),
    border = Color(0xFFE3DDD1),
    codeBg = Color(0xFFF2EEE6),
    codeHeaderBg = Color(0xFFE7E1D5),
)

private val NOVEL_PAPER_PARCHMENT = NovelPaperTheme(
    id = "parchment",
    isNight = false,
    bg = Color(0xFFF3E6C8),
    cardBg = Color(0xFFF7EDD5),
    surface = Color(0xFFEAD9B4),
    chromeBg = Color(0xF2EFE1C0),
    ink = Color(0xFF3A2818),
    muted = Color(0xFF6F563E),
    accent = Color(0xFFB45309),
    border = Color(0xFFD2B888),
    codeBg = Color(0xFFE6D4AC),
    codeHeaderBg = Color(0xFFDDC797),
)

private val NOVEL_PAPER_SAGE = NovelPaperTheme(
    id = "sage",
    isNight = false,
    bg = Color(0xFFE7F0E6),
    cardBg = Color(0xFFEEF6ED),
    surface = Color(0xFFD8E6D7),
    chromeBg = Color(0xF2E2ECE1),
    ink = Color(0xFF1D2F23),
    muted = Color(0xFF4F6B58),
    accent = Color(0xFF2D6A4F),
    border = Color(0xFFBDD2BC),
    codeBg = Color(0xFFDCE9DB),
    codeHeaderBg = Color(0xFFCCE0CB),
)

private val NOVEL_PAPER_NIGHT = NovelPaperTheme(
    id = "night",
    isNight = true,
    bg = Color(0xFF0E121B),
    cardBg = Color(0xFF141A29),
    surface = Color(0xFF171E2E),
    chromeBg = Color(0xF2151B29),
    ink = Color(0xFFE4E9F4),
    muted = Color(0xFF8F9DB6),
    accent = Color(0xFF5B7BF6),
    border = Color(0xFF253047),
    codeBg = Color(0xFF0A0D14),
    codeHeaderBg = Color(0xFF141B29),
)

private val HEX_COLOR_REGEX = Regex("^#?([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
private val DEFAULT_RP_STAGE_ACCENT = Color(0xFFF5C563)

fun resolveNovelPaperTheme(paper: String): NovelPaperTheme = when (paper.trim().lowercase()) {
    "parchment" -> NOVEL_PAPER_PARCHMENT
    "sage" -> NOVEL_PAPER_SAGE
    "night" -> NOVEL_PAPER_NIGHT
    else -> NOVEL_PAPER_CREAM
}

fun resolveRpStageAccent(accentHex: String?): Color {
    val clean = accentHex?.trim().orEmpty()
    val match = HEX_COLOR_REGEX.matchEntire(clean) ?: return DEFAULT_RP_STAGE_ACCENT
    val digits = match.groupValues[1]
    val argb = when (digits.length) {
        6 -> 0xFF000000L or digits.toLong(16)
        8 -> digits.toLong(16)
        else -> return DEFAULT_RP_STAGE_ACCENT
    }
    return Color(argb)
}

fun deriveNovelPaperAppTheme(base: AppThemeSpec, paper: NovelPaperTheme): AppThemeSpec {
    val semanticBase = resolveAppTheme(base.style, paper.isNight).colors
    val primarySoft: Color
    val primarySoftInk: Color
    val accentSoft: Color
    val tag: Color
    val tagInk: Color
    when (paper.id) {
        "parchment" -> {
            primarySoft = Color(0xFFE7D1A4)
            primarySoftInk = Color(0xFF7C3808)
            accentSoft = Color(0xFFEDE0BE)
            tag = Color(0xFFE8D3A6)
            tagInk = Color(0xFF7C3808)
        }
        "sage" -> {
            primarySoft = Color(0xFFCDE1CC)
            primarySoftInk = Color(0xFF1B4D38)
            accentSoft = Color(0xFFD5E6D4)
            tag = Color(0xFFD0E3CF)
            tagInk = Color(0xFF1F513B)
        }
        "night" -> {
            primarySoft = Color(0xFF1C294A)
            primarySoftInk = Color(0xFFB8CEFF)
            accentSoft = Color(0xFF1A2338)
            tag = Color(0xFF222D48)
            tagInk = Color(0xFFC4D7FF)
        }
        else -> {
            primarySoft = Color(0xFFE8EEFA)
            primarySoftInk = Color(0xFF2842BA)
            accentSoft = Color(0xFFF6F1E5)
            tag = Color(0xFFF3E6C8)
            tagInk = Color(0xFF7A440E)
        }
    }

    return base.copy(
        isDark = paper.isNight,
        colors = semanticBase.copy(
            page = paper.bg,
            surface = paper.cardBg,
            surfaceElevated = paper.cardBg,
            surfaceSoft = paper.surface,
            awning = paper.chromeBg,
            plankGrain = paper.accent.copy(alpha = if (paper.isNight) 0.14f else 0.08f),
            primary = paper.accent,
            primaryDark = paper.accent,
            primaryDeep = paper.accent,
            primarySoft = primarySoft,
            primarySoftInk = primarySoftInk,
            accent = paper.accent,
            accentSoft = accentSoft,
            tag = tag,
            tagInk = tagInk,
            ink = paper.ink,
            muted = paper.muted,
            border = paper.border,
            outline = paper.border,
        ),
    )
}

fun deriveRpStageAppTheme(base: AppThemeSpec, stageAccent: Color): AppThemeSpec {
    val darkBase = resolveAppTheme(base.style, isDark = true).colors
    return base.copy(
        isDark = true,
        colors = darkBase.copy(
            page = Color.Transparent,
            surface = Color(0xCC111624),
            surfaceElevated = Color(0xDE161D2F),
            surfaceSoft = Color(0xB8192136),
            awning = Color(0x78090D18),
            plankGrain = stageAccent.copy(alpha = 0.12f),
            ink = Color(0xFFF3F6FC),
            muted = Color(0xFFA7B3CB),
            border = stageAccent.copy(alpha = 0.34f),
            outline = Color(0x5594A3B8),
            accent = stageAccent,
            accentSoft = stageAccent.copy(alpha = 0.18f),
            tag = Color(0xCC2C2014),
            tagInk = Color(0xFFFDE68A),
            primarySoft = Color(0xCC1A274A),
            primarySoftInk = Color(0xFFBED2FF),
        ),
    )
}

@Composable
fun WorkspaceSystemBarsEffect(isDarkBackdrop: Boolean) {
    val view = LocalView.current
    val baseTheme = LocalAppTheme.current
    if (view.isInEditMode) return

    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDarkBackdrop
        controller.isAppearanceLightNavigationBars = !isDarkBackdrop
    }

    DisposableEffect(view, isDarkBackdrop, baseTheme.isDark) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDarkBackdrop
            controller.isAppearanceLightNavigationBars = !isDarkBackdrop
        }
        onDispose {
            val restoreWindow = view.context.findActivity()?.window ?: return@onDispose
            val controller = WindowCompat.getInsetsController(restoreWindow, view)
            controller.isAppearanceLightStatusBars = !baseTheme.isDark
            controller.isAppearanceLightNavigationBars = !baseTheme.isDark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
