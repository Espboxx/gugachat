package com.gugachat.app.domain.chat

import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mounted global worldbook paired with its entries and deterministic sort priority.
 */
data class WorldbookMountedBook(
    val worldbook: WorldbookEntity,
    val entries: List<WorldbookEntryEntity>,
    val sortOrder: Int = 0,
    val isGlobal: Boolean = false,
)

/**
 * Normalized entry emitted by [WorldbookEngine.evaluate] across Zone 0, Zone 2 (`@D`), and Zone 3.
 */
data class EvaluatedWorldbookEntry(
    val id: String,
    val worldbookId: String,
    val worldbookName: String,
    val uid: Int,
    val title: String,
    val content: String,
    val category: String,
    val constant: Boolean,
    val position: Int,
    val depth: Int,
    val outletRole: String,
    val insertionOrder: Int,
    val bookSortOrder: Int,
    val estimatedTokens: Int,
    val triggerKind: String,
    val matchedTerms: List<String> = emptyList(),
    val score: Double? = null,
    val isLegacySessionLore: Boolean = false,
)

/**
 * Partitioned evaluation result ready for Four-Zone Cache-Safe Assembly.
 */
data class WorldbookEvalResult(
    val zone0ConstantEntries: List<EvaluatedWorldbookEntry> = emptyList(),
    val zone2DepthEntries: List<EvaluatedWorldbookEntry> = emptyList(),
    val zone3TriggeredEntries: List<EvaluatedWorldbookEntry> = emptyList(),
    val updatedTimedStates: Map<String, LoreTimedEntryState> = emptyMap(),
    val inspectItems: List<RecalledMemoryInspectItem> = emptyList(),
    val constantTokens: Int = 0,
    val depthTokens: Int = 0,
    val triggeredTokens: Int = 0,
    val totalTokens: Int = 0,
    val droppedCount: Int = 0,
    val retrievalModeUsed: String = "lexical",
) {
    val allTriggeredIds: List<String>
        get() = (zone2DepthEntries + zone3TriggeredEntries)
            .filter { !it.constant }
            .map { it.id }

    val allTriggeredTitles: List<String>
        get() = (zone2DepthEntries + zone3TriggeredEntries)
            .filter { !it.constant }
            .map { it.title }
}

/**
 * Pure-function SillyTavern V2/V3 Worldbook + GuGuChat session lore evaluator.
 *
 * Supports:
 * - Merged global-enabled + conversation-mounted [WorldbookEntryEntity] items + session [NovelLoreEntry] items
 * - Primary keys (plain CJK/ASCII substring, case sensitivity, whole-word matching, and JS regex `/pat/flags`)
 * - Secondary keys with `selectiveLogic` (`0=AND_ANY`, `1=NOT_ALL`, `2=NOT_ANY`, `3=AND_ALL`)
 * - Timed effects (`delay`, `sticky`, `cooldown`) against `activeUserTurnCount` and [LoreTimedEntryState]
 * - Deterministic hash probability: `sha256("$convId|$turn|$entryId") % 100 < probability`
 * - 1-hop recursion honoring `preventRecursion` and `excludeRecursion`
 * - Exclusion groups (`groupName` / `groupWeight`) and strict `totalBudget` capping
 */
object WorldbookEngine {
    const val POSITION_BEFORE_CHAR = 0
    const val POSITION_AFTER_CHAR = 1
    const val POSITION_BEFORE_AN = 2
    const val POSITION_AFTER_AN = 3
    const val POSITION_AT_DEPTH = 4
    const val POSITION_BEFORE_EXAMPLES = 5
    const val POSITION_AFTER_EXAMPLES = 6
    const val POSITION_OUTLET = 7

    const val LOGIC_AND_ANY = 0
    const val LOGIC_NOT_ALL = 1
    const val LOGIC_NOT_ANY = 2
    const val LOGIC_AND_ALL = 3

    private const val MAX_ONE_HOP_RECURSION_ENTRIES = 8
    private const val MAX_REGEX_CACHE_SIZE = 128

    private data class RegexCacheKey(
        val pattern: String,
        val options: Set<RegexOption>,
    )

    private val regexCacheLock = Any()
    private val compiledRegexCache = object : LinkedHashMap<RegexCacheKey, Regex>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RegexCacheKey, Regex>?): Boolean =
            size > MAX_REGEX_CACHE_SIZE
    }

    private fun compileCachedRegex(pattern: String, options: Set<RegexOption>): Regex {
        val key = RegexCacheKey(pattern, options)
        synchronized(regexCacheLock) {
            compiledRegexCache[key]?.let { return it }
        }
        val compiled = Regex(pattern, options)
        return synchronized(regexCacheLock) {
            compiledRegexCache.getOrPut(key) { compiled }
        }
    }

    internal data class PreparedScanText(
        val raw: String,
        val nfkc: String,
        val nfkcLower: String,
    ) {
        companion object {
            val EMPTY = PreparedScanText("", "", "")

            fun fromRaw(raw: String): PreparedScanText {
                if (raw.isEmpty()) return EMPTY
                val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                return PreparedScanText(
                    raw = raw,
                    nfkc = nfkc,
                    nfkcLower = nfkc.lowercase(),
                )
            }
        }
    }

    private data class Candidate(
        val id: String,
        val worldbookId: String,
        val worldbookName: String,
        val uid: Int,
        val title: String,
        val content: String,
        val category: String,
        val primaryKeys: List<String>,
        val secondaryKeys: List<String>,
        val selectiveLogic: Int,
        val constant: Boolean,
        val caseSensitive: Boolean,
        val matchWholeWords: Boolean,
        val useRegexKey: Boolean,
        val probability: Int,
        val insertionOrder: Int,
        val position: Int,
        val depth: Int,
        val outletRole: String,
        val scanDepth: Int,
        val sticky: Int,
        val cooldown: Int,
        val delay: Int,
        val preventRecursion: Boolean,
        val excludeRecursion: Boolean,
        val groupName: String,
        val groupWeight: Int,
        val bookSortOrder: Int,
        val bookRecursiveScanning: Boolean,
        val estimatedTokens: Int,
        val isLegacySessionLore: Boolean,
        val legacyEntry: NovelLoreEntry? = null,
    )

    private data class TriggerHit(
        val candidate: Candidate,
        val triggerKind: String,
        val matchedTerms: List<String>,
        val score: Double?,
        val activatedFreshly: Boolean,
        val keptBySticky: Boolean,
    )

    fun evaluate(
        conversationId: String,
        books: List<WorldbookMountedBook> = emptyList(),
        sessionLore: List<NovelLoreEntry> = emptyList(),
        recentMessages: List<ChatMessage> = emptyList(),
        draftText: String = "",
        extraScanTexts: List<String> = emptyList(),
        activeUserTurnCount: Int? = null,
        timedStates: Map<String, LoreTimedEntryState> = emptyMap(),
        totalBudget: Int = 1600,
        defaultScanDepth: Int = 4,
        recursiveScanning: Boolean = true,
        activeTipSeq: Int = Int.MAX_VALUE,
        mutedIds: Set<String> = emptySet(),
        semanticScores: Map<String, Double>? = null,
        minSemanticCosine: Double = 0.50,
        modelId: String? = null,
    ): WorldbookEvalResult {
        val effectiveTurn = activeUserTurnCount ?: inferActiveUserTurnCount(recentMessages, draftText)
        val prunedIncomingStates = timedStates.filterValues { state ->
            state.lastTriggeredTurn in 0..effectiveTurn
        }

        val candidates = collectMergedCandidates(
            books = books,
            sessionLore = sessionLore,
            defaultScanDepth = defaultScanDepth,
            activeTipSeq = activeTipSeq,
            mutedIds = mutedIds,
            modelId = modelId,
        )
        if (candidates.isEmpty() || totalBudget <= 0) {
            return WorldbookEvalResult(updatedTimedStates = prunedIncomingStates)
        }

        val activeMessages = recentMessages
            .filter { it.deletedAt == null && it.role != "system" && it.content.isNotBlank() }

        val scanTextCache = HashMap<Int, PreparedScanText>()
        fun scanTextForDepth(depth: Int): PreparedScanText = scanTextCache.getOrPut(depth.coerceAtLeast(1)) {
            PreparedScanText.fromRaw(
                buildScanText(
                    activeMessages = activeMessages,
                    draftText = draftText,
                    extraScanTexts = extraScanTexts,
                    scanDepth = depth.coerceAtLeast(1),
                ),
            )
        }

        val activatedById = LinkedHashMap<String, TriggerHit>()
        var usedSemantic = false

        // Pass 1: Constant entries + Sticky continuations + Direct keyword/regex/semantic triggers
        for (c in candidates) {
            val prevState = prunedIncomingStates[c.id]

            // Delay gate: entry cannot activate before activeUserTurnCount >= delay
            if (c.delay > 0 && effectiveTurn < c.delay) {
                continue
            }

            if (c.constant) {
                if (passesHashProbability(conversationId, effectiveTurn, c.id, c.probability)) {
                    activatedById[c.id] = TriggerHit(
                        candidate = c,
                        triggerKind = "constant",
                        matchedTerms = emptyList(),
                        score = null,
                        activatedFreshly = false,
                        keptBySticky = false,
                    )
                }
                continue
            }

            // Sticky continuation takes precedence over cooldown and does not require re-matching keys
            val isStickyActive = prevState != null &&
                c.sticky > 0 &&
                effectiveTurn > prevState.lastTriggeredTurn &&
                effectiveTurn <= prevState.stickyUntilTurn
            if (isStickyActive) {
                activatedById[c.id] = TriggerHit(
                    candidate = c,
                    triggerKind = "sticky",
                    matchedTerms = emptyList(),
                    score = null,
                    activatedFreshly = false,
                    keptBySticky = true,
                )
                continue
            }

            // Cooldown gate: once triggered (and past sticky), cannot re-trigger while turn <= cooldownUntilTurn
            val isInCooldown = prevState != null &&
                c.cooldown > 0 &&
                effectiveTurn > prevState.lastTriggeredTurn &&
                effectiveTurn <= prevState.cooldownUntilTurn
            if (isInCooldown) {
                continue
            }

            val scanText = scanTextForDepth(c.scanDepth)
            val matchedPrimary = matchPrimaryKeys(c, scanText)
            if (matchedPrimary.isNotEmpty()) {
                if (!matchesSecondaryLogic(c, scanText)) continue
                if (!passesHashProbability(conversationId, effectiveTurn, c.id, c.probability)) continue
                activatedById[c.id] = TriggerHit(
                    candidate = c,
                    triggerKind = "keyword",
                    matchedTerms = matchedPrimary.take(6),
                    score = 1.0,
                    activatedFreshly = true,
                    keptBySticky = false,
                )
                continue
            }

            // Optional semantic fallback trigger when lexical keys did not match
            val semScore = semanticScores?.get(c.id)
            if (semScore != null && semScore >= minSemanticCosine) {
                if (!matchesSecondaryLogic(c, scanText)) continue
                if (!passesHashProbability(conversationId, effectiveTurn, c.id, c.probability)) continue
                usedSemantic = true
                activatedById[c.id] = TriggerHit(
                    candidate = c,
                    triggerKind = "semantic",
                    matchedTerms = emptyList(),
                    score = (semScore * 1000.0).roundToInt() / 1000.0,
                    activatedFreshly = true,
                    keptBySticky = false,
                )
            }
        }

        // Pass 2: 1-hop recursion (if enabled globally & on source books)
        if (recursiveScanning && activatedById.isNotEmpty()) {
            val recursionBuffer = activatedById.values
                .asSequence()
                .filter { hit -> !hit.candidate.preventRecursion && hit.candidate.bookRecursiveScanning }
                .map { hit -> "${hit.candidate.title}\n${hit.candidate.content}" }
                .joinToString("\n\n")
                .trim()

            if (recursionBuffer.isNotEmpty()) {
                val preparedRecursion = PreparedScanText.fromRaw(recursionBuffer)
                val combinedScanCache = HashMap<Int, PreparedScanText>()
                fun combinedSecondaryForDepth(depth: Int): PreparedScanText =
                    combinedScanCache.getOrPut(depth.coerceAtLeast(1)) {
                        val base = scanTextForDepth(depth)
                        PreparedScanText.fromRaw(
                            buildString(base.raw.length + 1 + preparedRecursion.raw.length) {
                                append(base.raw)
                                append('\n')
                                append(preparedRecursion.raw)
                            },
                        )
                    }

                val hopHits = ArrayList<TriggerHit>()
                for (c in candidates) {
                    if (c.constant || c.id in activatedById) continue
                    if (c.excludeRecursion) continue
                    if (c.delay > 0 && effectiveTurn < c.delay) continue

                    val prevState = prunedIncomingStates[c.id]
                    val isInCooldown = prevState != null &&
                        c.cooldown > 0 &&
                        effectiveTurn > prevState.lastTriggeredTurn &&
                        effectiveTurn <= prevState.cooldownUntilTurn
                    if (isInCooldown) continue

                    val matchedInHop = matchPrimaryKeys(c, preparedRecursion)
                    if (matchedInHop.isEmpty()) continue

                    val combinedForSecondary = combinedSecondaryForDepth(c.scanDepth)
                    if (!matchesSecondaryLogic(c, combinedForSecondary)) continue
                    if (!passesHashProbability(conversationId, effectiveTurn, c.id, c.probability)) continue

                    hopHits.add(
                        TriggerHit(
                            candidate = c,
                            triggerKind = "recursion_1hop",
                            matchedTerms = (matchedInHop + "1-hop").distinct().take(6),
                            score = 0.85,
                            activatedFreshly = true,
                            keptBySticky = false,
                        ),
                    )
                }

                hopHits
                    .sortedWith(
                        compareByDescending<TriggerHit> { it.candidate.insertionOrder }
                            .thenBy { it.candidate.bookSortOrder }
                            .thenBy { it.candidate.uid }
                            .thenBy { it.candidate.id },
                    )
                    .take(MAX_ONE_HOP_RECURSION_ENTRIES)
                    .forEach { hit ->
                        activatedById[hit.candidate.id] = hit
                    }
            }
        }

        // Resolve SillyTavern exclusion groups among non-constant dynamic hits
        val groupWinners = resolveExclusionGroups(activatedById.values.toList())

        // Sort constant entries deterministically (bookSortOrder, insertionOrder DESC, uid ASC, id)
        val constantPool = groupWinners
            .filter { it.candidate.constant }
            .sortedWith(
                compareBy<TriggerHit> { it.candidate.bookSortOrder }
                    .thenByDescending { it.candidate.insertionOrder }
                    .thenBy { it.candidate.uid }
                    .thenBy { it.candidate.id },
            )

        // Sort dynamic entries by insertion priority
        val dynamicPool = groupWinners
            .filter { !it.candidate.constant }
            .sortedWith(
                compareByDescending<TriggerHit> { it.candidate.insertionOrder }
                    .thenBy { it.candidate.bookSortOrder }
                    .thenBy { it.candidate.uid }
                    .thenBy { it.candidate.id },
            )

        val admitted = ArrayList<TriggerHit>()
        var remainingBudget = totalBudget
        var droppedCount = 0

        for (hit in constantPool) {
            val tok = hit.candidate.estimatedTokens
            if (tok <= remainingBudget) {
                admitted.add(hit)
                remainingBudget -= tok
            } else {
                droppedCount++
            }
        }

        for (hit in dynamicPool) {
            val tok = hit.candidate.estimatedTokens
            if (tok <= remainingBudget) {
                admitted.add(hit)
                remainingBudget -= tok
            } else {
                droppedCount++
            }
        }

        // Update timed states for admitted dynamic entries (and retain unexpired states for others)
        val nextTimedStates = LinkedHashMap<String, LoreTimedEntryState>(prunedIncomingStates)
        for (hit in admitted) {
            val c = hit.candidate
            if (c.constant) continue
            if (hit.activatedFreshly && (c.sticky > 0 || c.cooldown > 0)) {
                val stickyEnd = if (c.sticky > 0) effectiveTurn + c.sticky else -1
                val cooldownEnd = if (c.cooldown > 0) {
                    if (c.sticky > 0) effectiveTurn + c.sticky + c.cooldown else effectiveTurn + c.cooldown
                } else {
                    -1
                }
                nextTimedStates[c.id] = LoreTimedEntryState(
                    lastTriggeredTurn = effectiveTurn,
                    stickyUntilTurn = stickyEnd,
                    cooldownUntilTurn = cooldownEnd,
                )
            }
        }
        // Clean up fully expired timed states so persisted map stays compact
        nextTimedStates.entries.removeAll { (_, st) ->
            val stickyDone = st.stickyUntilTurn < 0 || effectiveTurn > st.stickyUntilTurn
            val cooldownDone = st.cooldownUntilTurn < 0 || effectiveTurn > st.cooldownUntilTurn
            stickyDone && cooldownDone && st.lastTriggeredTurn < effectiveTurn
        }

        val zone0 = ArrayList<EvaluatedWorldbookEntry>()
        val zone2 = ArrayList<EvaluatedWorldbookEntry>()
        val zone3 = ArrayList<EvaluatedWorldbookEntry>()

        for (hit in admitted) {
            val c = hit.candidate
            val evaluated = EvaluatedWorldbookEntry(
                id = c.id,
                worldbookId = c.worldbookId,
                worldbookName = c.worldbookName,
                uid = c.uid,
                title = c.title,
                content = c.content,
                category = c.category,
                constant = c.constant,
                position = c.position,
                depth = c.depth,
                outletRole = c.outletRole,
                insertionOrder = c.insertionOrder,
                bookSortOrder = c.bookSortOrder,
                estimatedTokens = c.estimatedTokens,
                triggerKind = hit.triggerKind,
                matchedTerms = hit.matchedTerms,
                score = hit.score,
                isLegacySessionLore = c.isLegacySessionLore,
            )
            when {
                c.position == POSITION_AT_DEPTH && c.depth >= 1 -> zone2.add(evaluated)
                c.constant -> zone0.add(evaluated)
                else -> zone3.add(evaluated)
            }
        }

        val sortedZone0 = zone0.sortedWith(
            compareBy<EvaluatedWorldbookEntry> { it.bookSortOrder }
                .thenByDescending { it.insertionOrder }
                .thenBy { it.uid }
                .thenBy { it.id },
        )
        val sortedZone2 = zone2.sortedWith(
            compareByDescending<EvaluatedWorldbookEntry> { it.depth }
                .thenBy { it.bookSortOrder }
                .thenByDescending { it.insertionOrder }
                .thenBy { it.uid }
                .thenBy { it.id },
        )
        val sortedZone3 = zone3.sortedWith(
            compareBy<EvaluatedWorldbookEntry> { it.bookSortOrder }
                .thenByDescending { it.insertionOrder }
                .thenBy { it.uid }
                .thenBy { it.id },
        )

        val inspectItems = (sortedZone0 + sortedZone2 + sortedZone3).map { e ->
            RecalledMemoryInspectItem(
                id = e.id,
                title = e.title,
                contentPreview = e.content.take(80),
                category = e.category,
                source = if (e.isLegacySessionLore) "novel_lore" else "worldbook_entry",
                score = e.score,
                estimatedTokens = e.estimatedTokens,
                matchedTerms = e.matchedTerms,
                triggerKind = e.triggerKind,
            )
        }

        val constTok = sortedZone0.sumOf { it.estimatedTokens }
        val depthTok = sortedZone2.sumOf { it.estimatedTokens }
        val trigTok = sortedZone3.sumOf { it.estimatedTokens }

        return WorldbookEvalResult(
            zone0ConstantEntries = sortedZone0,
            zone2DepthEntries = sortedZone2,
            zone3TriggeredEntries = sortedZone3,
            updatedTimedStates = nextTimedStates,
            inspectItems = inspectItems,
            constantTokens = constTok,
            depthTokens = depthTok,
            triggeredTokens = trigTok,
            totalTokens = constTok + depthTok + trigTok,
            droppedCount = droppedCount,
            retrievalModeUsed = if (usedSemantic) "hybrid" else "lexical",
        )
    }

    fun inferActiveUserTurnCount(messages: List<ChatMessage>, draftText: String = ""): Int {
        val active = messages.filter { it.deletedAt == null }
        val userCount = active.count { it.role == "user" }
        val lastIsUser = active.lastOrNull()?.role == "user"
        return if (draftText.isNotBlank() && !lastIsUser) userCount + 1 else userCount
    }

    fun passesHashProbability(
        conversationId: String,
        turn: Int,
        entryId: String,
        probability: Int,
    ): Boolean {
        val clamped = probability.coerceIn(0, 100)
        if (clamped >= 100) return true
        if (clamped <= 0) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$conversationId|$turn|$entryId".toByteArray(Charsets.UTF_8))
        val rawUint = ((digest[0].toLong() and 0xFFL) shl 24) or
            ((digest[1].toLong() and 0xFFL) shl 16) or
            ((digest[2].toLong() and 0xFFL) shl 8) or
            (digest[3].toLong() and 0xFFL)
        val bucket = (rawUint % 100L).toInt()
        return bucket < clamped
    }

    internal fun matchesKey(
        scanText: String,
        rawKey: String,
        caseSensitive: Boolean,
        matchWholeWords: Boolean,
        useRegexKey: Boolean,
    ): Boolean = matchesKey(
        preparedScan = PreparedScanText.fromRaw(scanText),
        rawKey = rawKey,
        caseSensitive = caseSensitive,
        matchWholeWords = matchWholeWords,
        useRegexKey = useRegexKey,
    )

    internal fun matchesKey(
        preparedScan: PreparedScanText,
        rawKey: String,
        caseSensitive: Boolean,
        matchWholeWords: Boolean,
        useRegexKey: Boolean,
    ): Boolean {
        val key = rawKey.trim()
        if (key.isEmpty() || preparedScan.raw.isEmpty()) return false

        if (useRegexKey) {
            val defaultFlags = if (caseSensitive) "" else "i"
            val (pattern, flags) = if (key.startsWith("/") && key.lastIndexOf('/') > 0) {
                RpRegexEngine.parseRegexLiteral(key, fallbackFlags = defaultFlags)
            } else {
                key to defaultFlags
            }
            if (pattern.isBlank()) return false
            val options = buildSet {
                if (!caseSensitive || 'i' in flags.lowercase()) add(RegexOption.IGNORE_CASE)
                if ('m' in flags.lowercase()) add(RegexOption.MULTILINE)
                if ('s' in flags.lowercase()) add(RegexOption.DOT_MATCHES_ALL)
            }
            return runCatching {
                compileCachedRegex(pattern, options).containsMatchIn(preparedScan.raw)
            }.getOrDefault(false)
        }

        val normHay = if (caseSensitive) preparedScan.nfkc else preparedScan.nfkcLower
        val normKey = Normalizer.normalize(key, Normalizer.Form.NFKC)
        if (normKey.isEmpty()) return false

        if (normKey.contains('&')) {
            val parts = normKey.split('&').map { it.trim() }.filter { it.isNotEmpty() }
            return parts.isNotEmpty() && parts.all { part ->
                matchesPlainSingleKey(
                    haystack = normHay,
                    key = part,
                    caseSensitive = caseSensitive,
                    matchWholeWords = matchWholeWords,
                    haystackAlreadyLowered = !caseSensitive,
                )
            }
        }
        return matchesPlainSingleKey(
            haystack = normHay,
            key = normKey,
            caseSensitive = caseSensitive,
            matchWholeWords = matchWholeWords,
            haystackAlreadyLowered = !caseSensitive,
        )
    }

    private fun matchesPlainSingleKey(
        haystack: String,
        key: String,
        caseSensitive: Boolean,
        matchWholeWords: Boolean,
        haystackAlreadyLowered: Boolean = false,
    ): Boolean {
        if (key.isEmpty() || haystack.isEmpty()) return false
        val hay = if (caseSensitive || haystackAlreadyLowered) haystack else haystack.lowercase()
        val target = if (caseSensitive) key else key.lowercase()
        if (hay.length < target.length) return false
        if (!matchWholeWords) {
            return hay.contains(target)
        }

        val checkLeftWord = isAsciiWordChar(target.first())
        val checkRightWord = isAsciiWordChar(target.last())
        var searchFrom = 0
        while (searchFrom <= hay.length - target.length) {
            val idx = hay.indexOf(target, searchFrom)
            if (idx < 0) return false
            val leftOk = !checkLeftWord || idx == 0 || !isAsciiWordChar(hay[idx - 1])
            val end = idx + target.length
            val rightOk = !checkRightWord || end == hay.length || !isAsciiWordChar(hay[end])
            if (leftOk && rightOk) return true
            searchFrom = idx + 1
        }
        return false
    }

    private fun isAsciiWordChar(ch: Char): Boolean =
        (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_'

    internal fun decodeStringListJson(rawJson: String): List<String> =
        com.gugachat.app.core.util.JsonUtils.decodeStringListJson(rawJson, trimAndFilterEmpty = true)

    private fun collectMergedCandidates(
        books: List<WorldbookMountedBook>,
        sessionLore: List<NovelLoreEntry>,
        defaultScanDepth: Int,
        activeTipSeq: Int,
        mutedIds: Set<String>,
        modelId: String?,
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        val seenEntryIds = HashSet<String>()

        // Mounted books first, then global-enabled books; deduplicated by worldbook.id
        val orderedBooks = books
            .sortedWith(
                compareBy<WorldbookMountedBook> { if (it.isGlobal) 1 else 0 }
                    .thenBy { it.sortOrder }
                    .thenBy { it.worldbook.id },
            )
            .distinctBy { it.worldbook.id }

        orderedBooks.forEachIndexed { bookIdx, mounted ->
            val book = mounted.worldbook
            val effectiveBookSort = if (mounted.isGlobal) 10_000 + bookIdx else mounted.sortOrder
            for (entry in mounted.entries) {
                if (!entry.enabled || entry.id in mutedIds || entry.content.isBlank()) continue
                if (!seenEntryIds.add(entry.id)) continue
                val keys = decodeStringListJson(entry.keysJson)
                val secondaryKeys = decodeStringListJson(entry.secondaryKeysJson)
                val estTokens = if (modelId == null && entry.estimatedTokens > 0) {
                    entry.estimatedTokens
                } else {
                    TokenEstimator.estimateItem(entry.title, entry.content, modelId = modelId, overhead = 8, minTokens = 8)
                }
                out.add(
                    Candidate(
                        id = entry.id,
                        worldbookId = book.id,
                        worldbookName = book.name,
                        uid = entry.uid,
                        title = entry.title.ifBlank { keys.firstOrNull() ?: "Entry #${entry.uid}" },
                        content = entry.content,
                        category = entry.category,
                        primaryKeys = keys,
                        secondaryKeys = secondaryKeys,
                        selectiveLogic = entry.selectiveLogic,
                        constant = entry.constant,
                        caseSensitive = entry.caseSensitive,
                        matchWholeWords = entry.matchWholeWords,
                        useRegexKey = entry.useRegexKey,
                        probability = entry.probability.coerceIn(0, 100),
                        insertionOrder = entry.insertionOrder,
                        position = entry.position,
                        depth = entry.depth.coerceAtLeast(0),
                        outletRole = normalizeRole(entry.outletRole),
                        scanDepth = (entry.scanDepthOverride ?: book.scanDepth).coerceIn(1, 24),
                        sticky = entry.sticky.coerceAtLeast(0),
                        cooldown = entry.cooldown.coerceAtLeast(0),
                        delay = entry.delay.coerceAtLeast(0),
                        preventRecursion = entry.preventRecursion,
                        excludeRecursion = entry.excludeRecursion,
                        groupName = entry.groupName.trim(),
                        groupWeight = entry.groupWeight.coerceAtLeast(1),
                        bookSortOrder = effectiveBookSort,
                        bookRecursiveScanning = book.recursiveScanning,
                        estimatedTokens = estTokens,
                        isLegacySessionLore = false,
                    ),
                )
            }
        }

        val sessionBookSort = 20_000
        sessionLore.forEachIndexed { idx, lore ->
            if (lore.id in mutedIds || lore.content.isBlank()) return@forEachIndexed
            if (!TimelineReconciler.isItemValidAtTip(
                    enabled = lore.enabled,
                    stale = lore.stale,
                    validFromSeq = lore.validFromSeq,
                    supersededAtSeq = lore.supersededAtSeq,
                    tipSeq = activeTipSeq,
                )
            ) {
                return@forEachIndexed
            }
            if (!seenEntryIds.add(lore.id)) return@forEachIndexed
            // Legacy NovelLoreEntry treats aliases as OR synonyms on primary keys (NOT secondary AND filter)
            val keys = (lore.keywords + lore.aliases)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { listOfNotNull(lore.title.trim().takeIf { it.isNotEmpty() }) }

            out.add(
                Candidate(
                    id = lore.id,
                    worldbookId = "session:${lore.conversationId}",
                    worldbookName = "会话世界书",
                    uid = idx,
                    title = lore.title,
                    content = lore.content,
                    category = lore.category,
                    primaryKeys = keys,
                    secondaryKeys = emptyList(),
                    selectiveLogic = LOGIC_AND_ANY,
                    constant = lore.alwaysActive,
                    caseSensitive = false,
                    matchWholeWords = false,
                    useRegexKey = false,
                    probability = 100,
                    insertionOrder = lore.priority,
                    position = POSITION_BEFORE_CHAR,
                    depth = 4,
                    outletRole = "system",
                    scanDepth = defaultScanDepth.coerceIn(1, 24),
                    sticky = 0,
                    cooldown = 0,
                    delay = 0,
                    preventRecursion = false,
                    excludeRecursion = false,
                    groupName = "",
                    groupWeight = 100,
                    bookSortOrder = sessionBookSort,
                    bookRecursiveScanning = true,
                    estimatedTokens = NovelEngine.estimateLoreTokens(lore, modelId),
                    isLegacySessionLore = true,
                    legacyEntry = lore,
                ),
            )
        }
        return out
    }

    private fun buildScanText(
        activeMessages: List<ChatMessage>,
        draftText: String,
        extraScanTexts: List<String>,
        scanDepth: Int,
    ): String {
        val parts = ArrayList<String>(scanDepth + extraScanTexts.size + 1)
        if (draftText.isNotBlank()) {
            parts.add(draftText.trim())
        }
        activeMessages.takeLast(scanDepth).forEach { msg ->
            parts.add(CjkSearch.sampleTurnText(msg.content, perTurnCap = 1800))
        }
        extraScanTexts.forEach { extra ->
            if (extra.isNotBlank()) parts.add(extra.trim())
        }
        return parts.joinToString("\n")
    }

    private fun matchPrimaryKeys(candidate: Candidate, preparedScan: PreparedScanText): List<String> {
        if (candidate.primaryKeys.isEmpty() || preparedScan.raw.isBlank()) return emptyList()
        val matched = ArrayList<String>()
        for (rawKey in candidate.primaryKeys) {
            if (matchesKey(
                    preparedScan = preparedScan,
                    rawKey = rawKey,
                    caseSensitive = candidate.caseSensitive,
                    matchWholeWords = candidate.matchWholeWords,
                    useRegexKey = candidate.useRegexKey,
                )
            ) {
                matched.add(rawKey.trim())
            }
        }
        return matched.distinct()
    }

    private fun matchesSecondaryLogic(candidate: Candidate, preparedScan: PreparedScanText): Boolean {
        val secKeys = candidate.secondaryKeys.filter { it.isNotBlank() }
        if (secKeys.isEmpty()) return true
        val hitCount = secKeys.count { secKey ->
            matchesKey(
                preparedScan = preparedScan,
                rawKey = secKey,
                caseSensitive = candidate.caseSensitive,
                matchWholeWords = candidate.matchWholeWords,
                useRegexKey = candidate.useRegexKey,
            )
        }
        return when (candidate.selectiveLogic) {
            LOGIC_AND_ANY -> hitCount >= 1
            LOGIC_NOT_ALL -> hitCount < secKeys.size
            LOGIC_NOT_ANY -> hitCount == 0
            LOGIC_AND_ALL -> hitCount == secKeys.size
            else -> hitCount >= 1
        }
    }

    private fun resolveExclusionGroups(hits: List<TriggerHit>): List<TriggerHit> {
        val grouped = HashMap<String, MutableList<TriggerHit>>()
        val ungrouped = ArrayList<TriggerHit>(hits.size)
        for (hit in hits) {
            val g = hit.candidate.groupName
            if (g.isEmpty() || hit.candidate.constant) {
                ungrouped.add(hit)
            } else {
                grouped.getOrPut(g.lowercase()) { ArrayList() }.add(hit)
            }
        }
        if (grouped.isEmpty()) return ungrouped

        val winners = ArrayList<TriggerHit>(ungrouped.size + grouped.size)
        winners.addAll(ungrouped)
        for ((_, contenders) in grouped) {
            val winner = contenders.sortedWith(
                compareByDescending<TriggerHit> { it.keptBySticky }
                    .thenByDescending { it.candidate.groupWeight }
                    .thenByDescending { it.candidate.insertionOrder }
                    .thenBy { it.candidate.uid }
                    .thenBy { it.candidate.id },
            ).first()
            winners.add(winner)
        }
        return winners
    }

    private fun normalizeRole(raw: String): String = when (raw.trim().lowercase()) {
        "user" -> "user"
        "assistant", "model" -> "assistant"
        else -> "system"
    }
}
