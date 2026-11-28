package com.gugachat.app.domain.chat

import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class WeightedTerm(
    val term: String,
    val weight: Double,
)

data class MemoryQuery(
    val rawQueryText: String,
    val terms: Map<String, Double>,
    val demonstratives: List<String>,
    val detectedPronounGenders: List<String>,
    val presentEntityNames: List<String>,
    val recentTexts: List<String>,
    val termFrequencies: Map<String, Int> = emptyMap(),
    val priorityTerms: Set<String> = emptySet(),
)

data class MemorySearchCandidate(
    val id: String,
    val title: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val kind: String? = null,
    val category: String? = null,
    val keywords: List<String>? = null,
    val aliases: List<String>? = null,
    val keyEntities: List<String>? = null,
    val entityType: String? = null,
    val gender: String? = null,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val enabled: Boolean = true,
    val resolved: Boolean = false,
    val stale: Boolean = false,
    val validFromSeq: Int? = null,
    val supersededAtSeq: Int? = null,
    val lastReferencedSeq: Int? = null,
    val toSeq: Int? = null,
    val origin: String? = null,
    val estimatedTokens: Int? = null,
)

data class ScoredMemoryCandidate(
    val item: MemorySearchCandidate,
    var score: Double,
    val lexicalScore: Double,
    val rawBm25: Double,
    val normBm25: Double,
    val aliasHit: Double,
    val recency: Double,
    val salienceBoost: Double,
    val semanticScore: Double? = null,
    val estimatedTokens: Int,
    val matchedTerms: List<String>,
    val triggerKind: String = "lexical", // constant | pinned_ledger | keyword | semantic | lexical | recursion_1hop
    val exactAliasHit: Boolean = false,
    val cleanMatchedTermCount: Int = 0,
)

fun interface SemanticScorer {
    fun score(queryText: String, candidates: List<MemorySearchCandidate>): Map<String, Double>
}

data class ScoreMemoryOptions(
    val activeTipSeq: Int,
    val maxTokens: Int,
    val semanticScores: Map<String, Double>? = null,
    val semanticScorer: SemanticScorer? = null,
    val minSemanticCosine: Double = 0.50,
    val maxResults: Int = 8,
    val modelId: String? = null,
    val mutedIds: Set<String> = emptySet(),
    val allowPinnedBypass: Boolean = true,
)

data class ScoreMemoryResult(
    val selected: List<ScoredMemoryCandidate>,
    val scored: List<ScoredMemoryCandidate>,
    val usedTokens: Int,
    val droppedCount: Int = 0,
    val retrievalModeUsed: String = "lexical",
)

object CjkSearch {
    const val DEFAULT_SCAN_DEPTH = 2
    const val DEFAULT_MAX_QUERY_CHARS = 1500
    const val MAX_PRUNED_QUERY_TERMS = 50
    const val MAX_VECTOR_CANDIDATES = 12
    const val MIN_BM25_NORM_FLOOR = 0.20
    const val RELATIVE_PEAK_RATIO = 0.45
    const val ELBOW_CLIFF_RATIO = 0.55

    @Volatile
    var segmenter: CjkWordSegmenter = AndroidIcuCjkSegmenter(PureKotlinClauseSegmenter)

    private val PERSON_ENTITY_TYPES = setOf("character", "person", "npc", "protagonist")

    private val INTERNAL_META_TAG_RE = Regex(
        "<(rp_state|rp_choices|rp_ledger|rp_ending|rp_speaker|status_hud|lore_sync|chapter_end|choices)\\b[^>]*>[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )
    private val DEMONSTRATIVE_RE = Regex("([这那])(个|把|件|封|名|位|座|条|只|张|段|本|块)?([\\u4e00-\\u9fff]{1,5})")
    private val HEAD_NOUN_TAIL_STOP_RE = Regex("[的了了吗呢吧啊呀在是有和与及或也都就才却这那].*$")
    private val NON_PERSON_OTHER_PRONOUN_RE = Regex("其他|他人|他乡|其它")
    private val MALE_EN_PRONOUN_RE = Regex("\\b(he|him|his)\\b")
    private val FEMALE_EN_PRONOUN_RE = Regex("\\b(she|her|hers)\\b")
    private val NON_COMPOUND_TAIL_CHARS = setOf(
        '上', '下', '里', '外', '旁', '中', '前', '后', '拔', '断', '掉', '落',
        '飞', '开', '关', '说', '看', '想', '去', '来', '拿', '放', '递', '给',
    )

    fun normalizeForSearch(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().trim()

    fun isHanChar(ch: Char): Boolean = TokenEstimator.isHanChar(ch)

    fun tokenizeCjkHybrid(text: String): List<WeightedTerm> = segmenter.segment(text)

    fun extractDemonstrativeHeadNouns(text: String): List<String> {
        val norm = normalizeForSearch(text)
        if (norm.isEmpty()) return emptyList()
        val nouns = LinkedHashSet<String>()
        for (m in DEMONSTRATIVE_RE.findAll(norm)) {
            val rawHead = m.groupValues.getOrNull(3)?.trim().orEmpty()
            var head = HEAD_NOUN_TAIL_STOP_RE.replace(rawHead, "").trim()
            if (head.length >= 3) {
                val stopIdx = (2 until head.length).firstOrNull { head[it] in NON_COMPOUND_TAIL_CHARS }
                if (stopIdx != null) {
                    head = head.substring(0, stopIdx)
                }
            }
            if (head.length >= 2 && head.any { it !in PureKotlinClauseSegmenter.HARD_CJK_STOPWORDS }) {
                nouns.add(head)
            }
        }
        return nouns.toList()
    }

    private fun detectPronounGenders(text: String): List<String> {
        val norm = normalizeForSearch(text).replace(NON_PERSON_OTHER_PRONOUN_RE, " ")
        val out = LinkedHashSet<String>()
        if (norm.contains("他") || MALE_EN_PRONOUN_RE.containsMatchIn(norm)) {
            out.add("male")
        }
        if (norm.contains("她") || FEMALE_EN_PRONOUN_RE.containsMatchIn(norm)) {
            out.add("female")
        }
        return out.toList()
    }

    internal fun sampleTurnText(
        text: String,
        perTurnCap: Int = 1000,
        headChars: Int = 200,
        tailChars: Int = 800,
    ): String {
        val stripped = INTERNAL_META_TAG_RE.replace(text, "").trim()
        if (stripped.length <= perTurnCap) return stripped
        if (perTurnCap <= headChars + 40) {
            return stripped.takeLast(perTurnCap)
        }
        val effectiveHead = min(headChars, perTurnCap / 4)
        val effectiveTail = min(tailChars, max(1, perTurnCap - effectiveHead - 1))
        return "${stripped.take(effectiveHead)}\n${stripped.takeLast(effectiveTail)}"
    }

    fun buildMemoryQuery(
        draftText: String,
        recentMessages: List<ChatMessage>,
        presentEntityNames: List<String> = emptyList(),
        scanDepth: Int = DEFAULT_SCAN_DEPTH,
        maxQueryChars: Int = DEFAULT_MAX_QUERY_CHARS,
    ): MemoryQuery {
        val clampedDepth = scanDepth.coerceIn(1, 6)
        val clampedCharBudget = max(200, maxQueryChars)

        val rawRecent = recentMessages
            .filter { it.deletedAt == null && it.role != "system" && it.content.isNotBlank() }
            .takeLast(clampedDepth)
            .map { it.content }

        val terms = LinkedHashMap<String, Double>()
        val termFreqs = HashMap<String, Int>()
        val priorityTerms = HashSet<String>()

        fun addText(src: String, multiplier: Double, isPriority: Boolean) {
            if (src.isBlank()) return
            for (tok in tokenizeCjkHybrid(src)) {
                val w = tok.weight * multiplier
                val cur = terms[tok.term] ?: 0.0
                if (w > cur) terms[tok.term] = w
                termFreqs[tok.term] = (termFreqs[tok.term] ?: 0) + 1
                if (isPriority && PureKotlinClauseSegmenter.isCleanContentTerm(tok.term)) {
                    priorityTerms.add(tok.term)
                }
            }
        }

        var remainingChars = clampedCharBudget
        val budgetedRawParts = ArrayList<String>()

        val cleanDraft = draftText.trim()
        val sampledDraft = if (cleanDraft.isNotEmpty()) {
            val s = sampleTurnText(cleanDraft, perTurnCap = min(1000, remainingChars))
            remainingChars = max(0, remainingChars - s.length)
            budgetedRawParts.add(s)
            addText(s, multiplier = 1.0, isPriority = true)
            s
        } else {
            ""
        }

        val sampledRecentNewestFirst = ArrayList<String>()
        rawRecent.asReversed().forEachIndexed { idx, txt ->
            if (remainingChars <= 0) return@forEachIndexed
            val perTurnLimit = min(1000, remainingChars)
            val sampled = sampleTurnText(txt, perTurnCap = perTurnLimit)
            if (sampled.isNotEmpty()) {
                remainingChars = max(0, remainingChars - sampled.length)
                sampledRecentNewestFirst.add(sampled)
                budgetedRawParts.add(sampled)
                val mult = when (idx) {
                    0 -> 0.85
                    1 -> 0.45
                    else -> max(0.25, 0.45 - (idx - 1) * 0.15)
                }
                addText(sampled, multiplier = mult, isPriority = false)
            }
        }

        presentEntityNames.forEach { entity ->
            if (entity.isNotBlank()) {
                addText(entity, multiplier = 0.75, isPriority = true)
            }
        }

        val combinedRaw = budgetedRawParts.joinToString("\n")
        val dems = extractDemonstrativeHeadNouns(combinedRaw)
        val pronounSource = sampledDraft.ifBlank { sampledRecentNewestFirst.firstOrNull().orEmpty() }
        val genders = detectPronounGenders(pronounSource)

        return MemoryQuery(
            rawQueryText = normalizeForSearch(combinedRaw),
            terms = terms,
            demonstratives = dems,
            detectedPronounGenders = genders,
            presentEntityNames = presentEntityNames,
            recentTexts = sampledRecentNewestFirst.asReversed(),
            termFrequencies = termFreqs,
            priorityTerms = priorityTerms,
        )
    }

    data class EntityHint(
        val name: String,
        val aliases: List<String> = emptyList(),
        val entityType: String? = null,
        val gender: String? = null,
        val present: Boolean = false,
    )

    fun resolveTop2PronounMatches(
        candidates: List<EntityHint>,
        gender: String,
        recentTexts: List<String>,
    ): List<EntityHint> {
        val normRecent = normalizeForSearch(recentTexts.joinToString("\n"))
        val targetGender = gender.lowercase().trim()
        return candidates
            .asSequence()
            .filter { cand ->
                val isPerson = cand.entityType?.trim()?.lowercase() in PERSON_ENTITY_TYPES
                if (!isPerson) return@filter false
                val g = cand.gender?.lowercase()?.trim()
                when {
                    g == targetGender -> true
                    g.isNullOrBlank() -> cand.present ||
                        (listOf(cand.name) + cand.aliases).any { n ->
                            val low = normalizeForSearch(n)
                            low.length >= 2 && normRecent.contains(low)
                        }
                    else -> false
                }
            }
            .sortedWith(
                compareByDescending<EntityHint> { it.gender.equals(gender, ignoreCase = true) }
                    .thenByDescending { it.present }
                    .thenByDescending { cand ->
                        val names = listOf(cand.name) + cand.aliases
                        names.any { n ->
                            val low = normalizeForSearch(n)
                            low.length >= 2 && normRecent.contains(low)
                        }
                    }
                    .thenBy { it.name },
            )
            .distinctBy { it.name.lowercase() }
            .take(2)
            .toList()
    }

    data class AliasHitResult(
        val aliasHit: Double,
        val demEntityBonus: Double,
        val matchedAliases: List<String>,
        val exactMatchCount: Int = 0,
    )

    private fun isAsciiWordChar(ch: Char): Boolean =
        (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9')

    internal fun matchesSingleKey(haystackNorm: String, keyNorm: String): Boolean {
        if (keyNorm.length < 2 || haystackNorm.isEmpty()) return false
        val isPureAscii = keyNorm.all { it.code < 128 }
        if (!isPureAscii) {
            return haystackNorm.contains(keyNorm)
        }
        var searchFrom = 0
        while (searchFrom <= haystackNorm.length - keyNorm.length) {
            val idx = haystackNorm.indexOf(keyNorm, searchFrom)
            if (idx < 0) return false
            val leftOk = idx == 0 || !isAsciiWordChar(haystackNorm[idx - 1])
            val end = idx + keyNorm.length
            val rightOk = end == haystackNorm.length || !isAsciiWordChar(haystackNorm[end])
            if (leftOk && rightOk) return true
            searchFrom = idx + 1
        }
        return false
    }

    internal fun matchesExactKey(haystackNorm: String, rawKey: String): Boolean {
        val normKey = normalizeForSearch(rawKey)
        if (normKey.isEmpty()) return false
        if (normKey.contains('&')) {
            val parts = normKey.split('&').map { it.trim() }.filter { it.isNotEmpty() }
            return parts.isNotEmpty() && parts.all { matchesSingleKey(haystackNorm, it) }
        }
        return matchesSingleKey(haystackNorm, normKey)
    }

    fun computeAliasAndDemonstrativeHit(
        candidate: MemorySearchCandidate,
        query: MemoryQuery,
        effectivePronounNames: Set<String>,
    ): AliasHitResult {
        val names = LinkedHashSet<String>()
        candidate.title?.trim()?.takeIf { it.isNotEmpty() }?.let { names.add(it) }
        candidate.aliases?.forEach { it.trim().takeIf { a -> a.isNotEmpty() }?.let { a -> names.add(a) } }
        candidate.keywords?.forEach { it.trim().takeIf { k -> k.isNotEmpty() }?.let { k -> names.add(k) } }
        candidate.keyEntities?.forEach { it.trim().takeIf { e -> e.isNotEmpty() }?.let { e -> names.add(e) } }

        val matched = ArrayList<String>()
        var exactMatches = 0
        var demHit = false
        val bodyNorm = normalizeForSearch("${candidate.title.orEmpty()} ${candidate.content.orEmpty()} ${candidate.summary.orEmpty()}")

        for (n in names) {
            val lower = normalizeForSearch(n)
            if (matchesExactKey(query.rawQueryText, lower)) {
                exactMatches++
                matched.add(n)
            } else if (!lower.contains('&') && lower in effectivePronounNames) {
                matched.add(n)
            }
            if (lower.length >= 2 && !lower.contains('&')) {
                if (query.demonstratives.any { d -> d.length >= 2 && (lower.contains(d) || d.contains(lower)) }) {
                    demHit = true
                }
            }
        }

        if (!demHit && query.demonstratives.isNotEmpty()) {
            if (query.demonstratives.any { d -> d.length >= 2 && bodyNorm.contains(d) }) {
                demHit = true
            }
        }

        val pronounMatched = candidate.title?.trim()?.lowercase()?.let { it in effectivePronounNames } == true
        val aliasHit = when {
            exactMatches >= 2 -> 1.0
            exactMatches == 1 -> 0.85
            pronounMatched -> 0.55
            else -> 0.0
        }
        val demBonus = when {
            demHit && exactMatches > 0 -> 0.35
            demHit -> 0.25
            else -> 0.0
        }
        return AliasHitResult(
            aliasHit = aliasHit,
            demEntityBonus = demBonus,
            matchedAliases = matched.distinct(),
            exactMatchCount = exactMatches,
        )
    }

    fun fuseRrf(
        lexRankedIds: List<String>,
        vecRankedIds: List<String>,
        k: Double = 60.0,
        lexWeight: Double = 1.0,
        vecWeight: Double = 1.0,
    ): Map<String, Double> {
        val scores = LinkedHashMap<String, Double>()
        lexRankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0.0) + lexWeight / (k + idx + 1)
        }
        vecRankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0.0) + vecWeight / (k + idx + 1)
        }
        return scores
    }

    private fun isOpenPromiseOrForeshadow(c: MemorySearchCandidate): Boolean {
        val kind = c.kind?.lowercase().orEmpty()
        return (kind == "promise" || kind == "foreshadow" || kind == "quest") && !c.resolved
    }

    private fun estimateCandidateTokens(c: MemorySearchCandidate, modelId: String?): Int {
        val est = c.estimatedTokens ?: 0
        if (modelId == null && est > 0) return est
        return TokenEstimator.estimateItem(
            title = c.title,
            body = c.content ?: c.summary,
            modelId = modelId,
            overhead = 8,
            minTokens = 8,
        )
    }

    private fun accumulateFieldTf(text: String): Pair<HashMap<String, Double>, Double> {
        val tf = HashMap<String, Double>()
        var dl = 0.0
        if (text.isBlank()) return tf to 0.0
        for (tok in tokenizeCjkHybrid(text)) {
            tf[tok.term] = (tf[tok.term] ?: 0.0) + tok.weight
            dl += tok.weight
        }
        return tf to dl
    }

    fun scoreMemoryCandidates(
        candidates: List<MemorySearchCandidate>,
        query: MemoryQuery,
        options: ScoreMemoryOptions,
    ): ScoreMemoryResult {
        val validCandidates = candidates.filter { c ->
            c.enabled &&
                !c.stale &&
                c.id !in options.mutedIds &&
                (c.validFromSeq ?: 0) <= options.activeTipSeq &&
                (c.supersededAtSeq == null || c.supersededAtSeq > options.activeTipSeq)
        }
        if (validCandidates.isEmpty()) {
            return ScoreMemoryResult(emptyList(), emptyList(), 0, droppedCount = 0, retrievalModeUsed = "lexical")
        }

        val semanticScores = options.semanticScores
            ?: options.semanticScorer?.score(query.rawQueryText, validCandidates)
        val hasSemanticSignal = !semanticScores.isNullOrEmpty() &&
            validCandidates.any { semanticScores.containsKey(it.id) }

        val effectiveTerms = LinkedHashMap(query.terms)
        val effectiveFreqs = HashMap(query.termFrequencies)
        val effectivePriority = HashSet(query.priorityTerms)
        val effectivePronounNames = LinkedHashSet<String>()

        if (query.detectedPronounGenders.isNotEmpty()) {
            val presentSet = query.presentEntityNames.map { it.lowercase() }.toSet()
            val candHints = validCandidates
                .mapNotNull { c ->
                    val name = c.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    EntityHint(
                        name = name,
                        aliases = c.aliases ?: c.keywords ?: emptyList(),
                        entityType = c.entityType,
                        gender = c.gender,
                        present = name.lowercase() in presentSet,
                    )
                }
            for (gender in query.detectedPronounGenders) {
                val top2 = resolveTop2PronounMatches(candHints, gender, query.recentTexts)
                for (ent in top2) {
                    effectivePronounNames.add(ent.name.lowercase())
                    for (tok in tokenizeCjkHybrid(ent.name)) {
                        val w = tok.weight * 0.70
                        val cur = effectiveTerms[tok.term] ?: 0.0
                        if (w > cur) effectiveTerms[tok.term] = w
                        effectiveFreqs[tok.term] = (effectiveFreqs[tok.term] ?: 0) + 1
                        if (PureKotlinClauseSegmenter.isCleanContentTerm(tok.term)) {
                            effectivePriority.add(tok.term)
                        }
                    }
                }
            }
        }

        data class FieldDocStat(
            val item: MemorySearchCandidate,
            val tfTitle: Map<String, Double>,
            val dlTitle: Double,
            val tfEntities: Map<String, Double>,
            val dlEntities: Double,
            val tfBody: Map<String, Double>,
            val dlBody: Double,
        ) {
            fun containsTerm(term: String): Boolean =
                (tfTitle[term] ?: 0.0) > 0.0 ||
                    (tfEntities[term] ?: 0.0) > 0.0 ||
                    (tfBody[term] ?: 0.0) > 0.0
        }

        val docs = validCandidates.map { c ->
            val titleKeyText = buildList {
                c.title?.takeIf { it.isNotBlank() }?.let { add(it) }
                c.keywords?.forEach { kw ->
                    kw.split('&').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                }
                c.aliases?.forEach { al ->
                    al.split('&').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                }
            }.joinToString(" ")
            val entitiesText = c.keyEntities.orEmpty().filter { it.isNotBlank() }.joinToString(" ")
            val bodyText = buildList {
                val content = c.content?.trim().orEmpty()
                val summary = c.summary?.trim().orEmpty()
                if (content.isNotEmpty()) add(content)
                if (summary.isNotEmpty() && summary != content) add(summary)
            }.joinToString(" ")

            val (tfT, dlT) = accumulateFieldTf(titleKeyText)
            val (tfE, dlE) = accumulateFieldTf(entitiesText)
            val (tfB, dlB) = accumulateFieldTf(bodyText)
            FieldDocStat(c, tfT, dlT, tfE, dlE, tfB, dlB)
        }

        val docCount = docs.size
        val effectiveN = max(docCount, 6).toDouble()
        val avgdlTitle = max(1.0, docs.sumOf { it.dlTitle } / docCount)
        val avgdlEntities = max(1.0, docs.sumOf { it.dlEntities } / docCount)
        val avgdlBody = max(1.0, docs.sumOf { it.dlBody } / docCount)

        // Compute DF for query terms, drop corpus-ubiquitous terms (DF/N > 0.50 when N >= 8),
        // saturate query TF with k3 = 1.0, and prune to Top-50 matchable query terms.
        data class PrunedQueryTerm(
            val term: String,
            val effectiveWeight: Double,
            val idf: Double,
            val rankScore: Double,
        )

        val k3 = 1.0
        val candidateQueryTerms = ArrayList<PrunedQueryTerm>()
        val totalCleanQueryTerms = effectiveTerms.keys.count { PureKotlinClauseSegmenter.isCleanContentTerm(it) }

        for ((term, qWeight) in effectiveTerms) {
            var dfCount = 0
            for (d in docs) {
                if (d.containsTerm(term)) dfCount++
            }
            if (dfCount <= 0) continue
            if (docCount >= 8 && (dfCount.toDouble() / docCount) > 0.50 && term !in effectivePriority) {
                continue
            }
            val idf = ln(1.0 + (effectiveN - dfCount + 0.5) / (dfCount + 0.5))
            val qTf = (effectiveFreqs[term] ?: 1).toDouble()
            val qSat = (qTf * (k3 + 1.0)) / (qTf + k3)
            val effW = qWeight * qSat
            val priorityMult = if (term in effectivePriority) 1.35 else 1.0
            candidateQueryTerms.add(
                PrunedQueryTerm(
                    term = term,
                    effectiveWeight = effW,
                    idf = idf,
                    rankScore = effW * idf * priorityMult,
                ),
            )
        }

        val prunedQueryTerms = if (candidateQueryTerms.size > MAX_PRUNED_QUERY_TERMS) {
            candidateQueryTerms.sortedByDescending { it.rankScore }.take(MAX_PRUNED_QUERY_TERMS)
        } else {
            candidateQueryTerms
        }

        val queryIdfSum = prunedQueryTerms.sumOf { it.effectiveWeight * it.idf }
        val bm25UpperBoundDenom = max(3.5, 0.45 * queryIdfSum)

        // BM25F field weights & length normalization
        val wTitle = 3.0
        val bTitle = 0.25
        val wEntities = 2.0
        val bEntities = 0.35
        val wBody = 1.0
        val bBody = 0.75
        val k1 = 1.2

        // Calibrated semantic cosine floor: cos >= max(minSemanticCosine, topCos - 0.12)
        val validSemValues = if (hasSemanticSignal) {
            validCandidates.mapNotNull { semanticScores?.get(it.id) }
        } else {
            emptyList()
        }
        val topCos = validSemValues.maxOrNull() ?: 0.0
        val effectiveSemanticFloor = max(options.minSemanticCosine, topCos - 0.12)

        val minCleanTermsRequired = if (totalCleanQueryTerms <= 1) 1 else 2
        val minNormBm25Required = if (totalCleanQueryTerms <= 1) 0.30 else MIN_BM25_NORM_FLOOR

        data class InternalScoreMeta(
            val candidate: ScoredMemoryCandidate,
            val lexicalPass: Boolean,
            val semanticPass: Boolean,
        )

        val metas = docs.map { doc ->
            val c = doc.item
            var rawBm25 = 0.0
            val bm25Terms = ArrayList<Pair<String, Double>>()
            val matchedCleanTerms = HashSet<String>()

            for (qt in prunedQueryTerms) {
                val tfT = doc.tfTitle[qt.term] ?: 0.0
                val tfE = doc.tfEntities[qt.term] ?: 0.0
                val tfB = doc.tfBody[qt.term] ?: 0.0
                if (tfT <= 0.0 && tfE <= 0.0 && tfB <= 0.0) continue

                val pseudoTf =
                    (if (tfT > 0.0) wTitle * tfT / (1.0 - bTitle + bTitle * (doc.dlTitle / avgdlTitle)) else 0.0) +
                        (if (tfE > 0.0) wEntities * tfE / (1.0 - bEntities + bEntities * (doc.dlEntities / avgdlEntities)) else 0.0) +
                        (if (tfB > 0.0) wBody * tfB / (1.0 - bBody + bBody * (doc.dlBody / avgdlBody)) else 0.0)

                val tfNorm = (pseudoTf * (k1 + 1.0)) / (pseudoTf + k1)
                val contrib = qt.effectiveWeight * qt.idf * tfNorm
                rawBm25 += contrib
                if (contrib > 0.0) {
                    bm25Terms.add(qt.term to contrib)
                    if (PureKotlinClauseSegmenter.isCleanContentTerm(qt.term)) {
                        matchedCleanTerms.add(qt.term)
                    }
                }
            }

            val normBm25 = if (rawBm25 > 0.0) min(1.0, rawBm25 / bm25UpperBoundDenom) else 0.0
            val aliasRes = computeAliasAndDemonstrativeHit(c, query, effectivePronounNames)
            val exactKeyHit = aliasRes.exactMatchCount > 0

            // Clean matchedTerms display: prioritize exact matched keywords/aliases/title,
            // then clean segmented terms (never stopword-touching bigrams or 1-char fragments).
            bm25Terms.sortByDescending { it.second }
            val matchedTerms = ArrayList<String>(6)
            val seenTermLower = HashSet<String>()
            for (alias in aliasRes.matchedAliases) {
                val clean = alias.trim()
                val lower = clean.lowercase()
                if (clean.isNotEmpty() && seenTermLower.add(lower)) {
                    matchedTerms.add(clean)
                    if (matchedTerms.size >= 6) break
                }
            }
            if (matchedTerms.size < 6) {
                for ((term, _) in bm25Terms) {
                    val clean = term.trim()
                    val lower = clean.lowercase()
                    if (PureKotlinClauseSegmenter.isCleanContentTerm(clean) && seenTermLower.add(lower)) {
                        matchedTerms.add(clean)
                        if (matchedTerms.size >= 6) break
                    }
                }
            }

            val refSeq = c.lastReferencedSeq ?: c.toSeq ?: c.validFromSeq ?: 0
            val deltaTurns = max(0, options.activeTipSeq - refSeq)
            val recency = exp(-0.03 * deltaTurns)

            val lexicalBm25Pass = matchedCleanTerms.size >= minCleanTermsRequired && normBm25 >= minNormBm25Required
            val lexicalPass = exactKeyHit || lexicalBm25Pass

            val semScore = semanticScores?.get(c.id)
            val semanticPass = semScore != null && semScore >= effectiveSemanticFloor

            // Salience / recency / openPromise only boost passing or pinned items (never solo-trigger zero-match docs)
            var salienceBoost = if (lexicalPass || semanticPass) aliasRes.demEntityBonus else 0.0
            if (c.pinned) salienceBoost += 1.0
            if (isOpenPromiseOrForeshadow(c) && (lexicalPass || semanticPass || c.pinned)) salienceBoost += 0.35
            if ((c.locked || c.origin == "user") && (lexicalPass || semanticPass)) salienceBoost += 0.15

            val lexicalScore = if (lexicalPass || c.pinned) {
                0.50 * normBm25 + 0.30 * aliasRes.aliasHit + 0.12 * recency + salienceBoost
            } else {
                0.50 * normBm25 + 0.25 * aliasRes.aliasHit
            }

            val triggerKind = when {
                c.pinned -> "constant"
                exactKeyHit -> "keyword"
                semanticPass && (!lexicalBm25Pass || (semScore ?: 0.0) >= effectiveSemanticFloor + 0.08) -> "semantic"
                else -> "lexical"
            }

            val scoredItem = ScoredMemoryCandidate(
                item = c,
                score = lexicalScore,
                lexicalScore = lexicalScore,
                rawBm25 = rawBm25,
                normBm25 = normBm25,
                aliasHit = aliasRes.aliasHit,
                recency = recency,
                salienceBoost = salienceBoost,
                semanticScore = semScore,
                estimatedTokens = estimateCandidateTokens(c, options.modelId),
                matchedTerms = matchedTerms,
                triggerKind = triggerKind,
                exactAliasHit = exactKeyHit,
                cleanMatchedTermCount = matchedCleanTerms.size,
            )
            InternalScoreMeta(scoredItem, lexicalPass = lexicalPass, semanticPass = semanticPass)
        }

        // Thresholded Weighted RRF when semantic scores are provided:
        // - Filter vecRanked by score >= max(minSemanticCosine, topCos - 0.12) and cap at Top-12
        // - Protect Tier-0 exactAliasHit bonus so noisy vectors never override exact RP/worldbook keys
        if (hasSemanticSignal) {
            val lexRanked = metas
                .filter { it.lexicalPass }
                .sortedWith(compareByDescending<InternalScoreMeta> { it.candidate.lexicalScore }.thenBy { it.candidate.item.id })
                .take(24)
                .map { it.candidate.item.id }

            val vecRanked = metas
                .filter { it.semanticPass }
                .sortedWith(compareByDescending<InternalScoreMeta> { it.candidate.semanticScore ?: 0.0 }.thenBy { it.candidate.item.id })
                .take(MAX_VECTOR_CANDIDATES)
                .map { it.candidate.item.id }

            val vecAcceptedIds = vecRanked.toSet()
            val rrfMap = fuseRrf(lexRanked, vecRanked)

            for (m in metas) {
                val s = m.candidate
                val inVec = s.item.id in vecAcceptedIds
                val passesAny = m.lexicalPass || inVec
                val rrf = if (passesAny) (rrfMap[s.item.id] ?: 0.0) else 0.0
                val exactBonus = if (s.exactAliasHit) 0.50 + 0.15 * s.aliasHit else 0.0
                val pinBonus = if (s.item.pinned) 1.0 else 0.0
                val openBonus = if (isOpenPromiseOrForeshadow(s.item) && (passesAny || s.item.pinned)) 0.10 else 0.0
                s.score = exactBonus + rrf + pinBonus + openBonus
            }
        }

        val vecAcceptedSet = if (hasSemanticSignal) {
            metas
                .filter { it.semanticPass }
                .sortedWith(compareByDescending<InternalScoreMeta> { it.candidate.semanticScore ?: 0.0 }.thenBy { it.candidate.item.id })
                .take(MAX_VECTOR_CANDIDATES)
                .map { it.candidate.item.id }
                .toSet()
        } else {
            emptySet()
        }

        val sortedMetas = metas.sortedWith(
            compareByDescending<InternalScoreMeta> { it.candidate.score }
                .thenByDescending { it.candidate.item.pinned }
                .thenByDescending { it.candidate.item.validFromSeq ?: 0 }
                .thenBy { it.candidate.item.id },
        )

        val hasSearchSignals = effectiveTerms.isNotEmpty() || query.demonstratives.isNotEmpty() || hasSemanticSignal
        val selected = ArrayList<ScoredMemoryCandidate>()
        var usedTokens = 0
        var droppedCount = 0

        // Peak reference scores for Gate 2 (Relative Peak Ratio 0.45) and Gate 3 (Elbow 0.55)
        val passingDynamicMetas = sortedMetas.filter { m ->
            val c = m.candidate
            !c.item.pinned && hasSearchSignals && (m.lexicalPass || c.item.id in vecAcceptedSet)
        }
        val topDynamicScore = passingDynamicMetas.maxOfOrNull { it.candidate.score } ?: 0.0
        val topNonExactScore = passingDynamicMetas
            .filter { !it.candidate.exactAliasHit }
            .maxOfOrNull { it.candidate.score } ?: 0.0

        var dynamicSelectedCount = 0
        var nonExactSelectedCount = 0
        var prevNonExactScore = 0.0
        var elbowTriggered = false
        val maxDynamicK = max(0, options.maxResults)

        for (m in sortedMetas) {
            val s = m.candidate
            val isPinnedBypass = s.item.pinned && options.allowPinnedBypass
            if (isPinnedBypass) {
                if (usedTokens + s.estimatedTokens <= options.maxTokens) {
                    selected.add(s)
                    usedTokens += s.estimatedTokens
                } else {
                    droppedCount++
                }
                continue
            }

            // Gate 1: Relevance Floor (exactKey OR (cleanTerms >= 2 && normBm25 >= 0.20) OR calibrated semantic pass)
            if (!hasSearchSignals) continue
            val passesGate1 = m.lexicalPass || s.item.id in vecAcceptedSet
            if (!passesGate1) continue

            // Gate 2: Relative Peak Ratio (0.45) for non-exact fuzzy matches
            if (!s.exactAliasHit) {
                val peakRef = if (hasSemanticSignal) topNonExactScore else topDynamicScore
                if (peakRef > 0.0 && s.score < RELATIVE_PEAK_RATIO * peakRef) {
                    droppedCount++
                    continue
                }
            }

            // Gate 3: Score-Cliff / Elbow Cutoff (0.55) for non-exact fuzzy tail items
            if (!s.exactAliasHit) {
                if (elbowTriggered) {
                    droppedCount++
                    continue
                }
                if (nonExactSelectedCount >= 2 && prevNonExactScore > 0.0 && (s.score / prevNonExactScore) < ELBOW_CLIFF_RATIO) {
                    elbowTriggered = true
                    droppedCount++
                    continue
                }
            }

            // Gate 4: Hard Top-K + Token Cap
            if (dynamicSelectedCount >= maxDynamicK) {
                droppedCount++
                continue
            }
            if (usedTokens + s.estimatedTokens > options.maxTokens) {
                droppedCount++
                continue
            }

            selected.add(s)
            usedTokens += s.estimatedTokens
            dynamicSelectedCount++
            if (!s.exactAliasHit) {
                nonExactSelectedCount++
                prevNonExactScore = s.score
            }
        }

        return ScoreMemoryResult(
            selected = selected,
            scored = sortedMetas.map { it.candidate },
            usedTokens = usedTokens,
            droppedCount = droppedCount,
            retrievalModeUsed = if (hasSemanticSignal) "hybrid" else "lexical",
        )
    }

    fun episodeToSearchCandidate(ep: MemoryEpisode): MemorySearchCandidate = MemorySearchCandidate(
        id = ep.id,
        title = ep.title,
        content = ep.summary,
        summary = ep.summary,
        keyEntities = ep.keyEntities,
        aliases = ep.keyEntities,
        validFromSeq = ep.fromSeq,
        lastReferencedSeq = ep.toSeq,
        toSeq = ep.toSeq,
        locked = ep.locked,
        stale = ep.stale,
        estimatedTokens = ep.estimatedTokens,
    )

    fun ledgerItemToSearchCandidate(item: MemoryLedgerItem): MemorySearchCandidate = MemorySearchCandidate(
        id = item.id,
        title = item.title,
        content = item.content,
        kind = item.kind,
        aliases = item.aliases,
        keyEntities = item.keyEntities,
        entityType = item.entityType,
        gender = item.gender,
        pinned = item.pinned,
        locked = item.locked,
        enabled = item.enabled,
        resolved = item.resolved,
        stale = item.stale,
        validFromSeq = item.validFromSeq,
        supersededAtSeq = item.supersededAtSeq,
        lastReferencedSeq = item.lastReferencedSeq,
        toSeq = item.toSeq,
        origin = item.origin,
        estimatedTokens = item.estimatedTokens,
    )

    fun novelLoreToSearchCandidate(entry: NovelLoreEntry): MemorySearchCandidate = MemorySearchCandidate(
        id = entry.id,
        title = entry.title,
        content = entry.content,
        category = entry.category,
        keywords = entry.keywords,
        aliases = entry.aliases,
        entityType = entry.category,
        pinned = entry.alwaysActive,
        enabled = entry.enabled,
        stale = entry.stale,
        validFromSeq = entry.validFromSeq,
        supersededAtSeq = entry.supersededAtSeq,
        lastReferencedSeq = entry.lastReferencedSeq,
        estimatedTokens = entry.estimatedTokens,
    )

    fun chatMemoryToSearchCandidate(mem: UserMemory): MemorySearchCandidate = MemorySearchCandidate(
        id = mem.id,
        content = mem.content,
        aliases = mem.aliases,
        category = mem.category.name,
        pinned = mem.pinned,
        enabled = mem.enabled,
        validFromSeq = 0,
        lastReferencedSeq = 0,
        estimatedTokens = mem.estimatedTokens,
    )
}
