package com.gugachat.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLatexGuardTest {

    private fun collectItalicTexts(tokens: List<MarkdownMathGuard.InlineToken>): List<String> {
        val out = ArrayList<String>()
        fun walk(list: List<MarkdownMathGuard.InlineToken>) {
            list.forEach { tok ->
                when (tok) {
                    is MarkdownMathGuard.InlineToken.Italic -> {
                        out.add(flattenText(tok.children))
                        walk(tok.children)
                    }
                    is MarkdownMathGuard.InlineToken.Bold -> walk(tok.children)
                    is MarkdownMathGuard.InlineToken.BoldItalic -> {
                        out.add(flattenText(tok.children))
                        walk(tok.children)
                    }
                    is MarkdownMathGuard.InlineToken.Strike -> walk(tok.children)
                    else -> Unit
                }
            }
        }
        walk(tokens)
        return out
    }

    private fun flattenText(tokens: List<MarkdownMathGuard.InlineToken>): String =
        tokens.joinToString("") { tok ->
            when (tok) {
                is MarkdownMathGuard.InlineToken.Plain -> tok.text
                is MarkdownMathGuard.InlineToken.Bold -> flattenText(tok.children)
                is MarkdownMathGuard.InlineToken.Italic -> flattenText(tok.children)
                is MarkdownMathGuard.InlineToken.BoldItalic -> flattenText(tok.children)
                is MarkdownMathGuard.InlineToken.Strike -> flattenText(tok.children)
                is MarkdownMathGuard.InlineToken.Code -> tok.code
                is MarkdownMathGuard.InlineToken.Link -> tok.label
                is MarkdownMathGuard.InlineToken.InlineMath -> tok.displayText
                is MarkdownMathGuard.InlineToken.DisplayMath -> tok.displayText
            }
        }

    @Test
    fun pandocGuard_neverParsesCurrencyStringsAsMath() {
        val currencyCases = listOf(
            "It costs $20 and $50 today.",
            "Budget is $100 for lunch.",
            "Range is $20-$50 per seat.",
            "Total is $20 ($10 each).",
            "Space padded $ 20 + 30 $ is not math.",
            "Blank line split $20\n\n$50 should stay text.",
        )

        currencyCases.forEach { sample ->
            val segments = MarkdownMathGuard.splitMathSegments(sample)
            assertEquals("Currency should stay a single Text segment for: $sample", 1, segments.size)
            assertTrue(segments[0] is MarkdownMathGuard.MathSegment.Text)
            assertEquals(sample, (segments[0] as MarkdownMathGuard.MathSegment.Text).text)

            val tokens = MarkdownMathGuard.tokenizeInline(sample)
            assertFalse(
                "No InlineMath or DisplayMath tokens expected for: $sample",
                tokens.any { it is MarkdownMathGuard.InlineToken.InlineMath || it is MarkdownMathGuard.InlineToken.DisplayMath },
            )
        }
    }

    @Test
    fun pandocGuard_preservesLeadingCurrencyWhenFollowedByValidMath() {
        val input = "Price is $20 and formula is \$x_1 * y_2$."
        val segments = MarkdownMathGuard.splitMathSegments(input)
        assertEquals(3, segments.size)
        assertEquals(MarkdownMathGuard.MathSegment.Text("Price is $20 and formula is "), segments[0])
        val math = segments[1] as MarkdownMathGuard.MathSegment.InlineMath
        assertEquals("x_1 * y_2", math.rawLatex)
        assertEquals("x₁ * y₂", math.displayText)
        assertEquals(MarkdownMathGuard.MathSegment.Text("."), segments[2])
    }

    @Test
    fun inlineMath_bypassesStarAndUnderscoreEmphasisParsing() {
        val expr1 = "\$a_1 * b_2$"
        val tokens1 = MarkdownMathGuard.tokenizeInline(expr1)
        assertEquals(1, tokens1.size)
        assertTrue(tokens1[0] is MarkdownMathGuard.InlineToken.InlineMath)
        assertEquals("a₁ * b₂", (tokens1[0] as MarkdownMathGuard.InlineToken.InlineMath).displayText)
        assertTrue(collectItalicTexts(tokens1).isEmpty())

        val expr2 = "\$x_{i,j} = y_i * z_j$"
        val tokens2 = MarkdownMathGuard.tokenizeInline(expr2)
        assertEquals(1, tokens2.size)
        assertTrue(tokens2[0] is MarkdownMathGuard.InlineToken.InlineMath)
        assertEquals("x_(i,j) = yᵢ * zⱼ", (tokens2[0] as MarkdownMathGuard.InlineToken.InlineMath).displayText)
        assertTrue(collectItalicTexts(tokens2).isEmpty())

        val parenExpr = """\(a_1 * b_2\)"""
        val tokens3 = MarkdownMathGuard.tokenizeInline(parenExpr)
        assertEquals(1, tokens3.size)
        assertTrue(tokens3[0] is MarkdownMathGuard.InlineToken.InlineMath)
        assertTrue(collectItalicTexts(tokens3).isEmpty())

        // Mixed normal italics + protected math + underscore note
        val mixed = "Check *label* with \$a_1 * b_2$ and _note_ plus snake_case_var."
        val mixedTokens = MarkdownMathGuard.tokenizeInline(mixed)
        assertEquals(listOf("label", "note"), collectItalicTexts(mixedTokens))
        assertEquals(1, mixedTokens.count { it is MarkdownMathGuard.InlineToken.InlineMath })

        // Math inside outer italic must not break at math's inner '*'
        val wrapped = "*prefix \$a_1 * b_2$ suffix*"
        val wrappedTokens = MarkdownMathGuard.tokenizeInline(wrapped)
        assertEquals(1, wrappedTokens.size)
        val outerItalic = wrappedTokens[0] as MarkdownMathGuard.InlineToken.Italic
        assertEquals(3, outerItalic.children.size)
        assertTrue(outerItalic.children[1] is MarkdownMathGuard.InlineToken.InlineMath)
    }

    @Test
    fun displayMath_parsesSingleAndMultilineBlocksWithoutCodeFenceLeakage() {
        val doc = """
            Intro text.
            
            $$
            E = mc^2
            $$
            
            \[\sum_{i=1}^n i = \frac{n(n+1)}{2}\]
            
            ```tex
            ${'$'}${'$'}not_a_math_block${'$'}${'$'}
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(doc)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        val blockMath1 = blocks[1] as MdBlock.MathBlock
        assertEquals("E = mc^2", blockMath1.rawLatex)
        assertEquals("E = mc²", LatexUnicodeFormatter.formatToDisplay(blockMath1.rawLatex))

        val blockMath2 = blocks[2] as MdBlock.MathBlock
        assertEquals("""\sum_{i=1}^n i = \frac{n(n+1)}{2}""", blockMath2.rawLatex)
        assertEquals("∑ᵢ₌₁ⁿ i = (n(n+1))/(2)", LatexUnicodeFormatter.formatToDisplay(blockMath2.rawLatex))

        val codeFence = blocks[3] as MdBlock.CodeFence
        assertEquals("tex", codeFence.lang)
        assertEquals("${'$'}${'$'}not_a_math_block${'$'}${'$'}", codeFence.code)

        // Inline backtick code spans must also remain untouched
        val backtickSample = "Code `\$a_1 * b_2$` stays code."
        val segments = MarkdownMathGuard.splitMathSegments(backtickSample)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownMathGuard.MathSegment.Text)
    }

    @Test
    fun latexUnicodeFormatter_translatesCommonTokensFractionsRootsAndScripts() {
        assertEquals("α + β ≤ γ", LatexUnicodeFormatter.formatToDisplay("""\alpha + \beta \leq \gamma"""))
        assertEquals("a × b · c ± d", LatexUnicodeFormatter.formatToDisplay("""a \times b \cdot c \pm d"""))
        assertEquals("x ≥ y ≠ z ≈ 0 → ∞", LatexUnicodeFormatter.formatToDisplay("""x \geq y \neq z \approx 0 \rightarrow \infty"""))
        assertEquals("∫₀^∞ e⁻ˣ dx", LatexUnicodeFormatter.formatToDisplay("""\int_0^\infty e^{-x} dx"""))
        assertEquals("√(a² + b²)", LatexUnicodeFormatter.formatToDisplay("""\sqrt{a^2 + b^2}"""))
        assertEquals("(a)/(b)", LatexUnicodeFormatter.formatToDisplay("""\frac{a}{b}"""))
        assertEquals("(a₁ + 1)/(b²)", LatexUnicodeFormatter.formatToDisplay("""\frac{a_{1} + 1}{b^2}"""))
    }
}
