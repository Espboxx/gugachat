package com.gugachat.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    @Test
    fun blankQuery_preservesOriginalOrder() {
        val items = listOf("gpt-4o", "claude-3-7-sonnet", "gemini-2.5-flash")
        assertEquals(items, FuzzyMatcher.filterAndRank(items, "") { listOf(it) })
        assertEquals(items, FuzzyMatcher.filterAndRank(items, "   \t  ") { listOf(it) })
    }

    @Test
    fun ranksExactPrefixSubstringCompactAndSubsequenceInOrder() {
        val exact = FuzzyMatcher.scoreToken("gpt-4o", "gpt-4o")!!
        val prefix = FuzzyMatcher.scoreToken("gpt-4o", "gpt-4o-mini")!!
        val boundarySub = FuzzyMatcher.scoreToken("4o", "openai/gpt-4o")!!
        val compactSub = FuzzyMatcher.scoreToken("gpt4o", "openai/gpt-4o")!!
        val subseq = FuzzyMatcher.scoreToken("g25f", "gemini-2.5-flash")!!

        assertTrue(exact > prefix)
        assertTrue(prefix > boundarySub)
        assertTrue(boundarySub > compactSub)
        assertTrue(compactSub > subseq)

        val candidates = listOf(
            "my-gpt-4o-custom",
            "gpt-4o-mini",
            "openai/gpt-4o",
            "gpt-4o",
        )
        val ranked = FuzzyMatcher.filterAndRank(candidates, "gpt-4o") { listOf(it) }
        assertEquals("gpt-4o", ranked.first())
        assertEquals("gpt-4o-mini", ranked[1])
    }

    @Test
    fun matchesCompactSubstringsAndSubsequencesAcrossModels() {
        val models = listOf(
            "claude-sonnet-4-5-20250929",
            "gemini-2.5-flash-image-preview",
            "gemini-2.5-pro",
            "gpt-image-1",
            "nai-diffusion-4-5-full",
        )

        val g25f = FuzzyMatcher.filterAndRank(models, "g25f") { listOf(it) }
        assertEquals(listOf("gemini-2.5-flash-image-preview"), g25f)

        val cs45 = FuzzyMatcher.filterAndRank(models, "cs45") { listOf(it) }
        assertEquals(listOf("claude-sonnet-4-5-20250929"), cs45)

        val nai45 = FuzzyMatcher.filterAndRank(models, "nai45") { listOf(it) }
        assertEquals(listOf("nai-diffusion-4-5-full"), nai45)
    }

    @Test
    fun multiTokenQuery_matchesAcrossPrimaryAndMetadataFields() {
        data class Entry(val id: String, val tags: String)

        val entries = listOf(
            Entry("gemini-2.5-pro", "思考 reasoning"),
            Entry("gemini-2.5-flash", "限免 free 视觉 vision"),
            Entry("claude-opus-4-1", "思考 reasoning"),
        )

        val res = FuzzyMatcher.filterAndRank(entries, "ｇｅｍ 限免") { listOf(it.id, it.tags) }
        assertEquals(1, res.size)
        assertEquals("gemini-2.5-flash", res.first().id)

        assertNull(FuzzyMatcher.scoreItem("gemini nonexistent", listOf("gemini-2.5-pro", "reasoning")))
        assertTrue(FuzzyMatcher.filterAndRank(entries, "zzz") { listOf(it.id, it.tags) }.isEmpty())
    }
}
