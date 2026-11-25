package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkSearchTest {

    @Test
    fun tokenizeCjkHybrid_generatesBigramsUnigramsAndLatinTokens() {
        val tokens = CjkSearch.tokenizeCjkHybrid("青岚剑宗 AI-2026 残碑")
        val termSet = tokens.map { it.term }.toSet()
        assertTrue(termSet.contains("青岚"))
        assertTrue(termSet.contains("岚剑"))
        assertTrue(termSet.contains("剑宗"))
        assertTrue(termSet.contains("ai-2026"))
        assertTrue(termSet.contains("残碑"))
    }

    @Test
    fun extractDemonstrativesAndScoreCandidates_matchesRelevantAndTimelineValidItems() {
        val dems = CjkSearch.extractDemonstrativeHeadNouns("把那把青霜剑递给那位银发法师")
        assertTrue(dems.contains("青霜剑"))

        val msgs = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = "user",
                content = "我们去铁砧堡查那把青霜剑的下落，她怎么看？",
                createdAt = 1L,
                seq = 10,
            ),
        )
        val query = CjkSearch.buildMemoryQuery("她手里的青霜剑有线索吗", msgs, listOf("苏挽月"))
        assertTrue(query.detectedPronounGenders.contains("female"))

        val candidates = listOf(
            MemorySearchCandidate(
                id = "c-valid",
                title = "苏挽月",
                content = "持有半块残锋令，曾在寒山驿追查青霜剑来源",
                aliases = listOf("苏姑娘"),
                entityType = "character",
                gender = "female",
                validFromSeq = 2,
                lastReferencedSeq = 9,
                estimatedTokens = 25,
            ),
            MemorySearchCandidate(
                id = "c-future",
                title = "未来条目",
                content = "青霜剑真相",
                validFromSeq = 15,
                estimatedTokens = 20,
            ),
            MemorySearchCandidate(
                id = "c-stale",
                title = "作废条目",
                content = "青霜剑伪线索",
                stale = true,
                validFromSeq = 1,
                estimatedTokens = 20,
            ),
            MemorySearchCandidate(
                id = "c-pinned",
                title = "核心契约",
                content = "与天气无关的常驻条款",
                pinned = true,
                validFromSeq = 1,
                estimatedTokens = 15,
            ),
        )

        val result = CjkSearch.scoreMemoryCandidates(
            candidates = candidates,
            query = query,
            options = ScoreMemoryOptions(activeTipSeq = 10, maxTokens = 50),
        )

        val ids = result.selected.map { it.item.id }
        assertTrue(ids.contains("c-valid"))
        assertTrue(ids.contains("c-pinned"))
        assertFalse(ids.contains("c-future"))
        assertFalse(ids.contains("c-stale"))
        assertTrue(result.usedTokens <= 50)
    }

    @Test
    fun fuseRrf_mergesLexicalAndVectorRankings() {
        val rrf = CjkSearch.fuseRrf(listOf("a", "b", "c"), listOf("b", "c", "d"))
        assertTrue(rrf.getValue("b") > rrf.getValue("a"))
        assertTrue(rrf.getValue("c") > rrf.getValue("d"))
    }
}
