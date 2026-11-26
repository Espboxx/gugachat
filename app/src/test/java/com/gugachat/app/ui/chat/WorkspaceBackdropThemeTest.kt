package com.gugachat.app.ui.chat

import androidx.compose.ui.graphics.Color
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.resolveAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBackdropThemeTest {

    @Test
    fun `resolveNovelPaperTheme returns exact cream palette and default fallback`() {
        val cream = resolveNovelPaperTheme("cream")
        assertEquals("cream", cream.id)
        assertFalse(cream.isNight)
        assertEquals(Color(0xFFFDFBF6), cream.bg)
        assertEquals(Color(0xFFFCF8EF), cream.cardBg)
        assertEquals(Color(0xFFF3EFE6), cream.surface)
        assertEquals(Color(0xF2FAF7F0), cream.chromeBg)
        assertEquals(Color(0xFF1F1C1B), cream.ink)
        assertEquals(Color(0xFF68625C), cream.muted)
        assertEquals(Color(0xFF4361EE), cream.accent)
        assertEquals(Color(0xFFE3DDD1), cream.border)
        assertEquals(Color(0xFFF2EEE6), cream.codeBg)
        assertEquals(Color(0xFFE7E1D5), cream.codeHeaderBg)

        assertEquals(cream, resolveNovelPaperTheme(""))
        assertEquals(cream, resolveNovelPaperTheme("   "))
        assertEquals(cream, resolveNovelPaperTheme("unknown_paper"))
    }

    @Test
    fun `resolveNovelPaperTheme returns exact parchment sage and night palettes`() {
        val parchment = resolveNovelPaperTheme("parchment")
        assertEquals("parchment", parchment.id)
        assertFalse(parchment.isNight)
        assertEquals(Color(0xFFF3E6C8), parchment.bg)
        assertEquals(Color(0xFFF7EDD5), parchment.cardBg)
        assertEquals(Color(0xFFEAD9B4), parchment.surface)
        assertEquals(Color(0xF2EFE1C0), parchment.chromeBg)
        assertEquals(Color(0xFF3A2818), parchment.ink)
        assertEquals(Color(0xFF6F563E), parchment.muted)
        assertEquals(Color(0xFFB45309), parchment.accent)
        assertEquals(Color(0xFFD2B888), parchment.border)
        assertEquals(Color(0xFFE6D4AC), parchment.codeBg)
        assertEquals(Color(0xFFDDC797), parchment.codeHeaderBg)
        assertEquals(parchment, resolveNovelPaperTheme("  PARCHMENT "))

        val sage = resolveNovelPaperTheme("sage")
        assertEquals("sage", sage.id)
        assertFalse(sage.isNight)
        assertEquals(Color(0xFFE7F0E6), sage.bg)
        assertEquals(Color(0xFFEEF6ED), sage.cardBg)
        assertEquals(Color(0xFFD8E6D7), sage.surface)
        assertEquals(Color(0xF2E2ECE1), sage.chromeBg)
        assertEquals(Color(0xFF1D2F23), sage.ink)
        assertEquals(Color(0xFF4F6B58), sage.muted)
        assertEquals(Color(0xFF2D6A4F), sage.accent)
        assertEquals(Color(0xFFBDD2BC), sage.border)
        assertEquals(Color(0xFFDCE9DB), sage.codeBg)
        assertEquals(Color(0xFFCCE0CB), sage.codeHeaderBg)
        assertEquals(sage, resolveNovelPaperTheme("Sage"))

        val night = resolveNovelPaperTheme("night")
        assertEquals("night", night.id)
        assertTrue(night.isNight)
        assertEquals(Color(0xFF0E121B), night.bg)
        assertEquals(Color(0xFF141A29), night.cardBg)
        assertEquals(Color(0xFF171E2E), night.surface)
        assertEquals(Color(0xF2151B29), night.chromeBg)
        assertEquals(Color(0xFFE4E9F4), night.ink)
        assertEquals(Color(0xFF8F9DB6), night.muted)
        assertEquals(Color(0xFF5B7BF6), night.accent)
        assertEquals(Color(0xFF253047), night.border)
        assertEquals(Color(0xFF0A0D14), night.codeBg)
        assertEquals(Color(0xFF141B29), night.codeHeaderBg)
        assertEquals(night, resolveNovelPaperTheme("NIGHT"))
    }

    @Test
    fun `resolveRpStageAccent parses 6-digit and 8-digit hex and falls back cleanly`() {
        val defaultGold = Color(0xFFF5C563)

        assertEquals(Color(0xFFFF6B6B), resolveRpStageAccent("#FF6B6B"))
        assertEquals(Color(0xFF4361EE), resolveRpStageAccent("  4361ee "))
        assertEquals(Color(0x804361EE), resolveRpStageAccent("#804361EE"))
        assertEquals(Color(0xCC112233), resolveRpStageAccent("cc112233"))

        assertEquals(defaultGold, resolveRpStageAccent(null))
        assertEquals(defaultGold, resolveRpStageAccent(""))
        assertEquals(defaultGold, resolveRpStageAccent("   "))
        assertEquals(defaultGold, resolveRpStageAccent("#123"))
        assertEquals(defaultGold, resolveRpStageAccent("#GGGGGG"))
        assertEquals(defaultGold, resolveRpStageAccent("#1234567"))
        assertEquals(defaultGold, resolveRpStageAccent("#123456789"))
        assertEquals(defaultGold, resolveRpStageAccent("not-a-color"))
    }

    @Test
    fun `deriveNovelPaperAppTheme maps all paper tokens and syncs light or dark flag`() {
        val lightBase = resolveAppTheme(ThemeStyle.GUGUGA, isDark = false)
        val darkBase = resolveAppTheme(ThemeStyle.CLASSIC, isDark = true)

        listOf("cream", "parchment", "sage", "night").forEach { paperId ->
            val paper = resolveNovelPaperTheme(paperId)
            val base = if (paper.isNight) lightBase else darkBase
            val derived = deriveNovelPaperAppTheme(base, paper)

            assertEquals(base.style, derived.style)
            assertEquals(paper.isNight, derived.isDark)
            assertEquals(base.borderWidth, derived.borderWidth)
            assertEquals(base.cardRadius, derived.cardRadius)
            assertEquals(base.pillRadius, derived.pillRadius)
            assertEquals(base.inputRadius, derived.inputRadius)

            val c = derived.colors
            assertEquals(paper.bg, c.page)
            assertEquals(paper.cardBg, c.surface)
            assertEquals(paper.cardBg, c.surfaceElevated)
            assertEquals(paper.surface, c.surfaceSoft)
            assertEquals(paper.chromeBg, c.awning)
            assertEquals(paper.ink, c.ink)
            assertEquals(paper.muted, c.muted)
            assertEquals(paper.border, c.border)
            assertEquals(paper.border, c.outline)
            assertEquals(paper.accent, c.accent)
            assertNotEquals(c.primarySoft, c.primarySoftInk)
            assertNotEquals(c.tag, c.tagInk)
        }
    }

    @Test
    fun `deriveRpStageAppTheme forces dark frosted glass palette with stage accent`() {
        val lightBase = resolveAppTheme(ThemeStyle.GUGUGA, isDark = false)
        val customAccent = resolveRpStageAccent("#E06D9A")
        val derived = deriveRpStageAppTheme(lightBase, customAccent)

        assertTrue(derived.isDark)
        assertEquals(lightBase.style, derived.style)
        assertEquals(lightBase.borderWidth, derived.borderWidth)
        assertEquals(lightBase.cardRadius, derived.cardRadius)

        val c = derived.colors
        assertEquals(Color.Transparent, c.page)
        assertEquals(Color(0xCC111624), c.surface)
        assertEquals(Color(0xDE161D2F), c.surfaceElevated)
        assertEquals(Color(0xB8192136), c.surfaceSoft)
        assertEquals(Color(0x78090D18), c.awning)
        assertEquals(Color(0xFFF3F6FC), c.ink)
        assertEquals(Color(0xFFA7B3CB), c.muted)
        assertEquals(customAccent.copy(alpha = 0.34f), c.border)
        assertEquals(Color(0x5594A3B8), c.outline)
        assertEquals(customAccent, c.accent)
        assertEquals(Color(0xCC2C2014), c.tag)
        assertEquals(Color(0xFFFDE68A), c.tagInk)
        assertEquals(Color(0xCC1A274A), c.primarySoft)
        assertEquals(Color(0xFFBED2FF), c.primarySoftInk)
    }
}
