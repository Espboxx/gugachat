package com.gugachat.app.core.util

import java.text.Normalizer

/**
 * Ranks items against multi-token fuzzy search queries (exact, prefix, substring,
 * separator-stripped compact match, and ordered subsequence match).
 */
object FuzzyMatcher {
    private val WHITESPACE_RE = Regex("\\s+")
    private val SEPARATORS = charArrayOf('-', '_', '.', '/', ' ', ':')

    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase().trim()

    fun <T> filterAndRank(
        items: List<T>,
        query: String,
        fieldsProvider: (T) -> List<String>,
    ): List<T> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return items

        return items.mapIndexedNotNull { index, item ->
            val score = scoreTokens(tokens, fieldsProvider(item)) ?: return@mapIndexedNotNull null
            Triple(item, score, index)
        }.sortedWith(
            compareByDescending<Triple<T, Int, Int>> { it.second }
                .thenBy { it.third },
        ).map { it.first }
    }

    fun scoreItem(query: String, fields: List<String>): Int? {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return null
        return scoreTokens(tokens, fields)
    }

    fun scoreToken(queryToken: String, candidate: String): Int? {
        val q = normalize(queryToken)
        val cand = normalize(candidate)
        if (q.isEmpty() || cand.isEmpty()) return null

        if (cand == q) return 1000
        if (cand.startsWith(q)) {
            return (850 - (cand.length - q.length).coerceAtMost(200)).coerceAtLeast(650)
        }

        val subIdx = cand.indexOf(q)
        if (subIdx >= 0) {
            val boundaryBonus = if (isWordBoundary(cand, subIdx)) 100 else 0
            return (650 - subIdx.coerceAtMost(200) + boundaryBonus).coerceAtLeast(420)
        }

        val compactQ = stripSeparators(q)
        val compactCand = stripSeparators(cand)
        if (compactQ.isNotEmpty()) {
            if (compactCand == compactQ) return 600
            val compactIdx = compactCand.indexOf(compactQ)
            if (compactIdx >= 0) {
                return (520 - compactIdx.coerceAtMost(140) - (compactCand.length - compactQ.length).coerceAtMost(80))
                    .coerceAtLeast(300)
            }
        }

        return scoreSubsequence(q, cand)
            ?: compactQ.takeIf { it.isNotEmpty() && it != q }?.let { scoreSubsequence(it, cand) }
    }

    private fun tokenize(query: String): List<String> =
        normalize(query).split(WHITESPACE_RE).filter { it.isNotEmpty() }

    private fun scoreTokens(tokens: List<String>, fields: List<String>): Int? {
        if (fields.isEmpty()) return null
        var total = 0
        for (token in tokens) {
            var bestFieldScore: Int? = null
            var bestIsPrimary = false
            for (i in fields.indices) {
                val fieldScore = scoreToken(token, fields[i]) ?: continue
                val adjusted = fieldScore + if (i == 0) 15 else 0
                if (bestFieldScore == null || adjusted > bestFieldScore) {
                    bestFieldScore = adjusted
                    bestIsPrimary = (i == 0)
                }
            }
            val matched = bestFieldScore ?: return null
            total += matched + if (bestIsPrimary) 5 else 0
        }
        return total
    }

    private fun stripSeparators(s: String): String = buildString(s.length) {
        for (ch in s) {
            if (ch !in SEPARATORS) append(ch)
        }
    }

    private fun isWordBoundary(cand: String, idx: Int): Boolean {
        if (idx <= 0) return true
        val prev = cand[idx - 1]
        val cur = cand[idx]
        if (prev in SEPARATORS) return true
        return cur.isDigit() != prev.isDigit()
    }

    private fun scoreSubsequence(q: String, cand: String): Int? {
        if (q.length > cand.length) return null
        var qi = 0
        var firstPos = -1
        var lastPos = -1
        var prevPos = -2
        var consecutive = 0
        var boundaryHits = 0

        for (ci in cand.indices) {
            if (qi >= q.length) break
            if (cand[ci] == q[qi]) {
                if (firstPos == -1) firstPos = ci
                if (ci == prevPos + 1) consecutive++
                if (isWordBoundary(cand, ci)) boundaryHits++
                lastPos = ci
                prevPos = ci
                qi++
            }
        }

        if (qi < q.length) return null
        val spanGap = ((lastPos - firstPos + 1) - q.length).coerceAtLeast(0)
        return (260 + consecutive * 22 + boundaryHits * 28 - spanGap * 3 - (cand.length - q.length).coerceAtMost(80))
            .coerceIn(10, 480)
    }
}
