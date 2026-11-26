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
    fun clauseSegmenter_dropsStopwordBoundaryBigramsAndSideUnigramsWhilePreservingCompounds() {
        val tokens = CjkSearch.tokenizeCjkHybrid("半的林晚棠和顶层的何瑞璋的确了解世界的目的与存在")
        val termSet = tokens.map { it.term }.toSet()

        // Zero stopword-boundary garbage bigrams
        assertFalse(termSet.contains("半的"))
        assertFalse(termSet.contains("的林"))
        assertFalse(termSet.contains("的何"))
        assertFalse(termSet.contains("的顶"))
        assertFalse(termSet.contains("层的"))
        assertFalse(termSet.contains("棠和"))

        // Zero side-emitted single-char unigrams from multi-char clauses
        assertFalse(termSet.contains("林"))
        assertFalse(termSet.contains("晚"))
        assertFalse(termSet.contains("棠"))
        assertFalse(termSet.contains("何"))
        assertFalse(termSet.contains("瑞"))
        assertFalse(termSet.contains("璋"))

        // Clean domain terms and whitelisted legitimate compounds preserved
        assertTrue(termSet.contains("林晚"))
        assertTrue(termSet.contains("晚棠"))
        assertTrue(termSet.contains("何瑞"))
        assertTrue(termSet.contains("瑞璋"))
        assertTrue(termSet.contains("的确"))
        assertTrue(termSet.contains("了解"))
        assertTrue(termSet.contains("世界"))
        assertTrue(termSet.contains("目的"))
        assertTrue(termSet.contains("存在"))
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
    fun buildMemoryQuery_enforcesDualScanDepthAndMaxQueryCharsWithHeadTailSampling() {
        val longTurnBody = "场景开头在兰溪谷书房。" + "无关日常背景铺陈文字内容填充。".repeat(300) + "最后焦点转向青霜剑密信。"
        val messages = (1..5).map { idx ->
            ChatMessage(
                id = "m$idx",
                conversationId = "c1",
                role = if (idx % 2 == 1) "user" else "assistant",
                content = if (idx == 1) "早期绝密关键字星陨铁" else "第${idx}轮：$longTurnBody",
                createdAt = idx.toLong(),
                seq = idx,
            )
        }
        val query = CjkSearch.buildMemoryQuery(draftText = "", recentMessages = messages)
        // Default scanDepth = 2 must exclude turn 1, and maxQueryChars = 1500 caps query size
        assertFalse(query.rawQueryText.contains("星陨铁"))
        assertTrue("Expected budgeted query <= 1600 chars, was ${query.rawQueryText.length}", query.rawQueryText.length <= 1600)
        // Head + tail sampling preserves both scene opening and closing hook
        assertTrue(query.rawQueryText.contains("兰溪谷书房"))
        assertTrue(query.rawQueryText.contains("青霜剑密信"))
    }

    @Test
    fun compoundAmpersandKeywordsAndAsciiBoundaries_matchStrictly() {
        val query = CjkSearch.buildMemoryQuery("林晚棠在灯下取出玉佩，准备测试 ai 引擎", emptyList())
        val bothMatch = MemorySearchCandidate(
            id = "both",
            title = "玉佩秘密",
            keywords = listOf("林晚棠&玉佩"),
            content = "家族信物",
        )
        val halfMatch = MemorySearchCandidate(
            id = "half",
            title = "剑穗秘密",
            keywords = listOf("林晚棠&玄铁剑穗"),
            content = "未触发",
        )
        val asciiBoundaryFail = MemorySearchCandidate(
            id = "ascii-fail",
            title = "AI工具",
            keywords = listOf("ai"),
            content = "测试",
        )

        val resBoth = CjkSearch.computeAliasAndDemonstrativeHit(bothMatch, query, emptySet())
        val resHalf = CjkSearch.computeAliasAndDemonstrativeHit(halfMatch, query, emptySet())
        assertTrue(resBoth.exactMatchCount == 1)
        assertTrue(resBoth.aliasHit >= 0.85)
        assertEquals(0, resHalf.exactMatchCount)
        assertEquals(0.0, resHalf.aliasHit, 1e-6)

        // ASCII whole-word matching: "ai" should match standalone "ai" but NOT inside "said" or "detail"
        val querySaidOnly = CjkSearch.buildMemoryQuery("she said every detail matters", emptyList())
        val resBoundaryFail = CjkSearch.computeAliasAndDemonstrativeHit(asciiBoundaryFail, querySaidOnly, emptySet())
        assertEquals(0, resBoundaryFail.exactMatchCount)
        val resBoundaryOk = CjkSearch.computeAliasAndDemonstrativeHit(asciiBoundaryFail, query, emptySet())
        assertEquals(1, resBoundaryOk.exactMatchCount)
    }

    @Test
    fun pronounResolution_neverMatchesNullOrNonPersonEntityTypes() {
        val hints = listOf(
            CjkSearch.EntityHint(name = "秘密日记", entityType = null, gender = null, present = true),
            CjkSearch.EntityHint(name = "青铜匕首", entityType = "item", gender = null, present = true),
            CjkSearch.EntityHint(name = "社区热帖", entityType = "concept", gender = null, present = true),
            CjkSearch.EntityHint(name = "苏挽月", entityType = "character", gender = "female", present = true),
            CjkSearch.EntityHint(name = "顾尘", entityType = "protagonist", gender = "male", present = true),
        )

        val femaleMatches = CjkSearch.resolveTop2PronounMatches(hints, "female", listOf("她轻轻点头"))
        assertEquals(listOf("苏挽月"), femaleMatches.map { it.name })

        val maleMatches = CjkSearch.resolveTop2PronounMatches(hints, "male", listOf("他推开大门"))
        assertEquals(listOf("顾尘"), maleMatches.map { it.name })
    }

    @Test
    fun fourGateCutoff_preventsRecallEverythingOn34DocCorpusAndKeepsCleanMatchedTerms() {
        val query = CjkSearch.buildMemoryQuery(
            draftText = "的何瑞璋在兰溪谷公馆核对朗月国际臻品鉴定牌照",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = "assistant",
                    content = "夜色渐浓，窗外细雨敲打着玻璃，桌上的账单与鉴定证书摆放整齐。".repeat(30),
                    createdAt = 1L,
                    seq = 10,
                ),
            ),
        )
        val candidates = ArrayList<MemorySearchCandidate>()
        candidates.add(
            MemorySearchCandidate(
                id = "relevant-1",
                title = "朗月国际鉴定牌照",
                keywords = listOf("朗月国际", "鉴定牌照"),
                content = "林逸朗已正式获得朗月国际臻品鉴定有限公司营业牌照。",
                validFromSeq = 1,
                estimatedTokens = 30,
            ),
        )
        candidates.add(
            MemorySearchCandidate(
                id = "relevant-2",
                title = "兰溪谷公馆账单约定",
                keywords = listOf("何瑞璋&兰溪谷"),
                content = "何瑞璋与主角在兰溪谷公馆核对二十万自有本金账单。",
                validFromSeq = 1,
                estimatedTokens = 30,
            ),
        )
        for (i in 1..32) {
            candidates.add(
                MemorySearchCandidate(
                    id = "noise-$i",
                    title = "无关杂项记录 $i",
                    content = "这是第${i}条关于天气、交通、菜单与日常作息的普通备忘录，夜晚窗外偶尔有风。",
                    validFromSeq = 1,
                    estimatedTokens = 30,
                ),
            )
        }

        val res = CjkSearch.scoreMemoryCandidates(
            candidates = candidates,
            query = query,
            options = ScoreMemoryOptions(activeTipSeq = 10, maxTokens = 4000, maxResults = 8),
        )

        val selectedIds = res.selected.map { it.item.id }
        assertTrue(selectedIds.contains("relevant-1"))
        assertTrue(selectedIds.contains("relevant-2"))
        assertTrue("Expected selective recall (<= 4), but got ${selectedIds.size}: $selectedIds", selectedIds.size <= 4)

        for (s in res.selected) {
            assertTrue("matchedTerms must never contain stopword bigrams like 的何", s.matchedTerms.none { it.contains("的") && it.length == 2 })
            assertTrue("matchedTerms must never contain 1-char fragments", s.matchedTerms.none { it.length == 1 })
        }
    }

    @Test
    fun hybridRrf_filtersAnisotropicLowCosineNoiseAndProtectsExactKeyTier0() {
        val query = CjkSearch.buildMemoryQuery("林晚棠悄悄把银针藏进袖口", emptyList())
        val candidates = listOf(
            MemorySearchCandidate(
                id = "exact-char",
                title = "林晚棠",
                keywords = listOf("林晚棠"),
                content = "青云剑宗外门弟子，性情冷淡。",
                entityType = "character",
                validFromSeq = 1,
                estimatedTokens = 25,
            ),
            MemorySearchCandidate(
                id = "semantic-paraphrase",
                title = "暗器防身习惯",
                content = "遇险前习惯将淬毒细针暗藏衣袖深处以备突袭。",
                validFromSeq = 1,
                estimatedTokens = 25,
            ),
            MemorySearchCandidate(
                id = "anisotropic-noise-1",
                title = "港口天气记录",
                content = "今日海风偏大，渔船暂停出海。",
                validFromSeq = 1,
                estimatedTokens = 25,
            ),
            MemorySearchCandidate(
                id = "anisotropic-noise-2",
                title = "商铺税率条例",
                content = "城南集市每月征收半成商税。",
                validFromSeq = 1,
                estimatedTokens = 25,
            ),
        )

        // Simulate real Chinese embedding anisotropy: unrelated docs still have 0.40-0.48 cosine!
        val semScores = mapOf(
            "exact-char" to 0.52,
            "semantic-paraphrase" to 0.82,
            "anisotropic-noise-1" to 0.48,
            "anisotropic-noise-2" to 0.43,
        )

        val res = CjkSearch.scoreMemoryCandidates(
            candidates = candidates,
            query = query,
            options = ScoreMemoryOptions(
                activeTipSeq = 10,
                maxTokens = 500,
                semanticScores = semScores,
                minSemanticCosine = 0.50,
            ),
        )

        assertEquals("hybrid", res.retrievalModeUsed)
        val ids = res.selected.map { it.item.id }
        assertEquals(listOf("exact-char", "semantic-paraphrase"), ids)
        assertEquals("keyword", res.selected[0].triggerKind)
        assertEquals("semantic", res.selected[1].triggerKind)
        assertFalse(ids.contains("anisotropic-noise-1"))
        assertFalse(ids.contains("anisotropic-noise-2"))
    }

    @Test
    fun fuseRrf_mergesLexicalAndVectorRankings() {
        val rrf = CjkSearch.fuseRrf(listOf("a", "b", "c"), listOf("b", "c", "d"))
        assertTrue(rrf.getValue("b") > rrf.getValue("a"))
        assertTrue(rrf.getValue("c") > rrf.getValue("d"))
    }
}
