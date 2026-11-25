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
)

fun interface SemanticScorer {
    fun score(queryText: String, candidates: List<MemorySearchCandidate>): Map<String, Double>
}

data class ScoreMemoryOptions(
    val activeTipSeq: Int,
    val maxTokens: Int,
    val semanticScores: Map<String, Double>? = null,
    val semanticScorer: SemanticScorer? = null,
)

data class ScoreMemoryResult(
    val selected: List<ScoredMemoryCandidate>,
    val scored: List<ScoredMemoryCandidate>,
    val usedTokens: Int,
)

object CjkSearch {
    private val CJK_STOPWORDS = setOf(
        "的", "了", "在", "是", "我", "你", "他", "她", "它", "们", "和", "与", "或",
        "着", "过", "地", "得", "吗", "呢", "吧", "啊", "哦", "呀", "也", "都", "就",
        "还", "又", "很", "太", "不", "没", "有", "把", "被", "给", "让", "从", "到",
        "向", "往", "对", "为", "于", "而", "但", "却", "这", "那", "其", "之", "以",
        "上", "下", "中", "里", "后", "前", "个", "种", "件", "位",
    )

    private val EN_STOPWORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "to", "of",
        "in", "on", "at", "for", "with", "and", "or", "but", "not", "this", "that",
        "it", "he", "she", "they", "them", "his", "her", "their", "you", "your",
    )

    private val HAN_RUN_RE = Regex("\\p{IsHan}+")
    private val LATIN_WORD_RE = Regex("[a-z0-9][a-z0-9_-]*")
    private val DEMONSTRATIVE_RE = Regex("([这那])(个|把|件|封|名|位|座|条|只|张|段|本|块)?([\\u4e00-\\u9fff]{1,5})")
    private val HEAD_NOUN_TAIL_STOP_RE = Regex("[的了了吗呢吧啊呀在是有和与及或也都就才却这那].*$")
    private val NON_COMPOUND_TAIL_CHARS = setOf(
        '上', '下', '里', '外', '旁', '中', '前', '后', '拔', '断', '掉', '落',
        '飞', '开', '关', '说', '看', '想', '去', '来', '拿', '放', '递', '给',
    )

    fun normalizeForSearch(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().trim()

    fun isHanChar(ch: Char): Boolean = Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN

    fun tokenizeCjkHybrid(text: String): List<WeightedTerm> {
        val norm = normalizeForSearch(text)
        if (norm.isEmpty()) return emptyList()
        val result = ArrayList<WeightedTerm>()

        for (match in HAN_RUN_RE.findAll(norm)) {
            val run = match.value
            if (run.length == 1) {
                if (run !in CJK_STOPWORDS) {
                    result.add(WeightedTerm(run, 0.65))
                }
            } else {
                for (i in 0 until run.length - 1) {
                    val bigram = run.substring(i, i + 2)
                    val bothStop = bigram.substring(0, 1) in CJK_STOPWORDS && bigram.substring(1, 2) in CJK_STOPWORDS
                    result.add(WeightedTerm(bigram, if (bothStop) 0.2 else 1.0))
                }
                for (ch in run) {
                    val s = ch.toString()
                    if (s !in CJK_STOPWORDS) {
                        result.add(WeightedTerm(s, 0.35))
                    }
                }
            }
        }

        for (match in LATIN_WORD_RE.findAll(norm)) {
            val w = match.value
            if (w.length >= 2 && w !in EN_STOPWORDS) {
                result.add(WeightedTerm(w, 1.0))
            }
        }
        return result
    }

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
            if (head.isNotEmpty() && head !in CJK_STOPWORDS) {
                nouns.add(head)
            }
        }
        return nouns.toList()
    }

    private fun detectPronounGenders(text: String): List<String> {
        val norm = normalizeForSearch(text).replace(Regex("其他|他人|他乡|其它"), " ")
        val out = LinkedHashSet<String>()
        if (norm.contains("他") || Regex("\\b(he|him|his)\\b").containsMatchIn(norm)) {
            out.add("male")
        }
        if (norm.contains("她") || Regex("\\b(she|her|hers)\\b").containsMatchIn(norm)) {
            out.add("female")
        }
        return out.toList()
    }

    fun buildMemoryQuery(
        draftText: String,
        recentMessages: List<ChatMessage>,
        presentEntityNames: List<String> = emptyList(),
    ): MemoryQuery {
        val recentUserOrAssistant = recentMessages
            .filter { it.deletedAt == null && it.role != "system" && it.content.isNotBlank() }
            .takeLast(4)
            .map { it.content }

        val rawParts = buildList {
            if (draftText.isNotBlank()) add(draftText)
            addAll(recentUserOrAssistant)
        }
        val combinedRaw = rawParts.joinToString("\n")
        val terms = LinkedHashMap<String, Double>()

        fun addText(src: String, multiplier: Double) {
            for (tok in tokenizeCjkHybrid(src)) {
                val w = tok.weight * multiplier
                val cur = terms[tok.term] ?: 0.0
                if (w > cur) terms[tok.term] = w
            }
        }

        if (draftText.isNotBlank()) addText(draftText, 1.0)
        recentUserOrAssistant.asReversed().forEachIndexed { idx, txt ->
            val mult = max(0.35, 0.85 - idx * 0.15)
            addText(txt, mult)
        }
        presentEntityNames.forEach { addText(it, 0.75) }

        val dems = extractDemonstrativeHeadNouns(combinedRaw)
        val pronounSource = if (draftText.isNotBlank()) draftText else recentUserOrAssistant.lastOrNull().orEmpty()
        val genders = detectPronounGenders(pronounSource)

        return MemoryQuery(
            rawQueryText = normalizeForSearch(combinedRaw),
            terms = terms,
            demonstratives = dems,
            detectedPronounGenders = genders,
            presentEntityNames = presentEntityNames,
            recentTexts = recentUserOrAssistant,
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
        return candidates
            .asSequence()
            .filter { cand ->
                val g = cand.gender?.lowercase()?.trim()
                val isPerson = cand.entityType == null || cand.entityType == "character" || cand.entityType == "person"
                isPerson && (g == gender.lowercase() || g.isNullOrBlank())
            }
            .sortedWith(
                compareByDescending<EntityHint> { it.gender.equals(gender, ignoreCase = true) }
                    .thenByDescending { it.present }
                    .thenByDescending { cand ->
                        val names = listOf(cand.name) + cand.aliases
                        names.any { n -> n.isNotBlank() && normRecent.contains(n.lowercase()) }
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
    )

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
            val lower = n.lowercase()
            if (query.rawQueryText.contains(lower)) {
                exactMatches++
                matched.add(n)
            } else if (lower in effectivePronounNames) {
                matched.add(n)
            }
            if (query.demonstratives.any { d -> lower.contains(d) || d.contains(lower) }) {
                demHit = true
            }
        }

        if (!demHit && query.demonstratives.isNotEmpty()) {
            if (query.demonstratives.any { d -> bodyNorm.contains(d) }) {
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
        return AliasHitResult(aliasHit = aliasHit, demEntityBonus = demBonus, matchedAliases = matched.distinct())
    }

    fun fuseRrf(
        lexRankedIds: List<String>,
        vecRankedIds: List<String>,
        k: Double = 60.0,
    ): Map<String, Double> {
        val scores = LinkedHashMap<String, Double>()
        lexRankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + idx + 1)
        }
        vecRankedIds.forEachIndexed { idx, id ->
            scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + idx + 1)
        }
        return scores
    }

    private fun isOpenPromiseOrForeshadow(c: MemorySearchCandidate): Boolean {
        val kind = c.kind?.lowercase().orEmpty()
        return (kind == "promise" || kind == "foreshadow" || kind == "quest") && !c.resolved
    }

    private fun estimateCandidateTokens(c: MemorySearchCandidate): Int {
        val est = c.estimatedTokens ?: 0
        if (est > 0) return est
        val text = "${c.title.orEmpty()}: ${c.content ?: c.summary.orEmpty()}"
        return max(8, estimateApproxTokens(text) + 8)
    }

    private fun estimateApproxTokens(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isHanChar(ch) || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) {
                cjk++
            } else if (!ch.isWhitespace()) {
                other++
            }
        }
        val raw = cjk * 1.45 + other * 0.38
        return max(1, kotlin.math.ceil(raw).toInt())
    }

    fun scoreMemoryCandidates(
        candidates: List<MemorySearchCandidate>,
        query: MemoryQuery,
        options: ScoreMemoryOptions,
    ): ScoreMemoryResult {
        val validCandidates = candidates.filter { c ->
            c.enabled &&
                !c.stale &&
                (c.validFromSeq ?: 0) <= options.activeTipSeq &&
                (c.supersededAtSeq == null || c.supersededAtSeq > options.activeTipSeq)
        }
        if (validCandidates.isEmpty()) {
            return ScoreMemoryResult(emptyList(), emptyList(), 0)
        }

        val semanticScores = options.semanticScores
            ?: options.semanticScorer?.score(query.rawQueryText, validCandidates)

        val effectiveTerms = LinkedHashMap(query.terms)
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
                        val w = tok.weight * 0.7
                        val cur = effectiveTerms[tok.term] ?: 0.0
                        if (w > cur) effectiveTerms[tok.term] = w
                    }
                }
            }
        }

        data class DocStat(
            val item: MemorySearchCandidate,
            val tf: Map<String, Double>,
            val dl: Double,
        )

        val docs = validCandidates.map { c ->
            val text = buildList {
                c.title?.takeIf { it.isNotBlank() }?.let { add(it) }
                (c.content ?: c.summary)?.takeIf { it.isNotBlank() }?.let { add(it) }
                c.keyEntities?.let { addAll(it.filter { e -> e.isNotBlank() }) }
            }.joinToString(" ")
            val toks = tokenizeCjkHybrid(text)
            val tf = HashMap<String, Double>()
            var dl = 0.0
            for (tok in toks) {
                tf[tok.term] = (tf[tok.term] ?: 0.0) + tok.weight
                dl += tok.weight
            }
            DocStat(c, tf, dl)
        }

        val docCount = docs.size
        val avgdl = max(1.0, docs.sumOf { it.dl } / docCount)
        val df = HashMap<String, Int>()
        for (term in effectiveTerms.keys) {
            var count = 0
            for (d in docs) {
                if ((d.tf[term] ?: 0.0) > 0.0) count++
            }
            df[term] = count
        }

        val k1 = 1.2
        val b = 0.65

        val scored = docs.map { doc ->
            val c = doc.item
            var rawBm25 = 0.0
            val bm25Terms = ArrayList<Pair<String, Double>>()
            for ((term, qWeight) in effectiveTerms) {
                val tfVal = doc.tf[term] ?: 0.0
                if (tfVal <= 0.0) continue
                val dfVal = df[term] ?: 0
                val idf = ln(1.0 + (docCount - dfVal + 0.5) / (dfVal + 0.5))
                val tfNorm = (tfVal * (k1 + 1.0)) / (tfVal + k1 * (1.0 - b + b * (doc.dl / avgdl)))
                val contrib = qWeight * idf * tfNorm
                rawBm25 += contrib
                if (contrib > 0.0) bm25Terms.add(term to contrib)
            }

            val normBm25 = if (rawBm25 > 0.0) rawBm25 / (rawBm25 + 2.5) else 0.0
            val aliasRes = computeAliasAndDemonstrativeHit(c, query, effectivePronounNames)

            bm25Terms.sortByDescending { it.second }
            val matchedTerms = ArrayList<String>(6)
            val seenTermLower = HashSet<String>()
            for (alias in aliasRes.matchedAliases) {
                val lower = alias.lowercase()
                if (lower.isNotEmpty() && seenTermLower.add(lower)) {
                    matchedTerms.add(alias)
                    if (matchedTerms.size >= 6) break
                }
            }
            if (matchedTerms.size < 6) {
                for ((term, _) in bm25Terms) {
                    val clean = term.trim()
                    val lower = clean.lowercase()
                    if (clean.isNotEmpty() && seenTermLower.add(lower)) {
                        matchedTerms.add(clean)
                        if (matchedTerms.size >= 6) break
                    }
                }
            }

            val refSeq = c.lastReferencedSeq ?: c.toSeq ?: c.validFromSeq ?: 0
            val deltaTurns = max(0, options.activeTipSeq - refSeq)
            val recency = exp(-0.03 * deltaTurns)

            var salienceBoost = aliasRes.demEntityBonus
            if (c.pinned) salienceBoost += 1.0
            if (isOpenPromiseOrForeshadow(c)) salienceBoost += 0.45
            if (c.locked || c.origin == "user") salienceBoost += 0.20

            val lexicalScore = 0.50 * normBm25 + 0.25 * aliasRes.aliasHit + 0.15 * recency + salienceBoost
            val semanticScore = semanticScores?.get(c.id)

            ScoredMemoryCandidate(
                item = c,
                score = lexicalScore,
                lexicalScore = lexicalScore,
                rawBm25 = rawBm25,
                normBm25 = normBm25,
                aliasHit = aliasRes.aliasHit,
                recency = recency,
                salienceBoost = salienceBoost,
                semanticScore = semanticScore,
                estimatedTokens = estimateCandidateTokens(c),
                matchedTerms = matchedTerms,
            )
        }.toMutableList()

        if (!semanticScores.isNullOrEmpty()) {
            val lexRanked = scored
                .sortedWith(compareByDescending<ScoredMemoryCandidate> { it.lexicalScore }.thenBy { it.item.id })
                .map { it.item.id }
            val vecRanked = scored
                .filter { (it.semanticScore ?: 0.0) > 0.0 }
                .sortedWith(compareByDescending<ScoredMemoryCandidate> { it.semanticScore ?: 0.0 }.thenBy { it.item.id })
                .map { it.item.id }
            val rrfMap = fuseRrf(lexRanked, vecRanked)
            for (s in scored) {
                val rrf = rrfMap[s.item.id] ?: 0.0
                val pinBonus = if (s.item.pinned) 1.0 else 0.0
                val openBonus = if (isOpenPromiseOrForeshadow(s.item)) 0.25 else 0.0
                s.score = rrf + pinBonus + openBonus
            }
        }

        scored.sortWith(
            compareByDescending<ScoredMemoryCandidate> { it.score }
                .thenByDescending { it.item.pinned }
                .thenByDescending { it.item.validFromSeq ?: 0 }
                .thenBy { it.item.id },
        )

        val hasSearchSignals = effectiveTerms.isNotEmpty() || query.demonstratives.isNotEmpty() || !semanticScores.isNullOrEmpty()
        val selected = ArrayList<ScoredMemoryCandidate>()
        var usedTokens = 0

        for (s in scored) {
            val relevant = !hasSearchSignals ||
                s.normBm25 > 0.0 ||
                s.aliasHit > 0.0 ||
                (s.semanticScore != null && s.semanticScore > 0.0) ||
                s.item.pinned ||
                isOpenPromiseOrForeshadow(s.item)
            if (!relevant) continue
            if (usedTokens + s.estimatedTokens > options.maxTokens) continue
            selected.add(s)
            usedTokens += s.estimatedTokens
        }

        return ScoreMemoryResult(selected = selected, scored = scored, usedTokens = usedTokens)
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
