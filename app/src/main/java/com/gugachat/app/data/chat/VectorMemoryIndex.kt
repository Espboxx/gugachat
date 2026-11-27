package com.gugachat.app.data.chat

import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.local.MemoryEmbeddingDao
import com.gugachat.app.data.local.MemoryEmbeddingEntity
import com.gugachat.app.domain.chat.EmbeddingEngine
import com.gugachat.app.domain.chat.LruQueryEmbeddingCache
import com.gugachat.app.domain.chat.RerankEngine
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.VectorIndexTarget
import com.gugachat.app.domain.chat.VectorQuantizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

class VectorMemoryIndex(
    private val dao: MemoryEmbeddingDao,
    private val queryCache: LruQueryEmbeddingCache = LruQueryEmbeddingCache(64),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    private data class CacheKey(
        val conversationId: String,
        val profileKey: String,
    )

    private class FlatVectorSlice(
        val dim: Int,
        val ownerIds: List<String>,
        val ownerIndexById: Map<String, Int>,
        val scales: FloatArray,
        val flatMatrix: ByteArray,
    )

    private data class ContentVectorKey(
        val profileKey: String,
        val contentHash: String,
    )

    private class CachedInt8Vector(
        val dim: Int,
        val quantType: String,
        val scale: Float,
        val vectorBlob: ByteArray,
    )

    private val cacheLock = Any()
    private val sliceCache = HashMap<CacheKey, FlatVectorSlice>()
    private val contentVectorCache = HashMap<ContentVectorKey, CachedInt8Vector>()
    private val knownDimByProfile = HashMap<String, Int>()

    private fun isValidInt8Row(row: MemoryEmbeddingEntity, expectedDim: Int?): Boolean {
        if (row.dim <= 0) return false
        if (row.quantType != VectorQuantizer.QUANT_INT8_UNIT) return false
        if (row.vectorBlob.size != row.dim) return false
        if (expectedDim != null && row.dim != expectedDim) return false
        return true
    }

    private fun recordCachedVector(
        profileKey: String,
        hash: String,
        dim: Int,
        quantType: String,
        scale: Float,
        blob: ByteArray,
        fromAuthoritativeEmbed: Boolean = false,
    ) {
        if (dim <= 0 || quantType != VectorQuantizer.QUANT_INT8_UNIT || blob.size != dim) return
        synchronized(cacheLock) {
            val activeDim = knownDimByProfile[profileKey]
            if (fromAuthoritativeEmbed) {
                if (activeDim != null && activeDim != dim) {
                    contentVectorCache.entries.removeAll { it.key.profileKey == profileKey }
                }
                knownDimByProfile[profileKey] = dim
            } else {
                if (activeDim != null && activeDim != dim) return
                if (activeDim == null) {
                    knownDimByProfile[profileKey] = dim
                }
            }
            contentVectorCache[ContentVectorKey(profileKey, hash)] = CachedInt8Vector(
                dim = dim,
                quantType = quantType,
                scale = scale,
                vectorBlob = blob.copyOf(),
            )
        }
    }

    suspend fun ensureIndexed(
        targets: List<VectorIndexTarget>,
        engine: EmbeddingEngine,
        batchSize: Int = 16,
        pruneStale: Boolean = true,
        onProgress: (VectorIndexProgress) -> Unit = {},
    ): VectorIndexProgress {
        val profileKey = engine.profileKey
        if (targets.isEmpty()) {
            val done = VectorIndexProgress(running = false, indexed = 0, total = 0, profileKey = profileKey)
            onProgress(done)
            return done
        }
        if (!engine.isAvailable()) {
            val unavailable = VectorIndexProgress(
                running = false,
                indexed = 0,
                total = targets.size,
                profileKey = profileKey,
                lastError = "Embedding engine unavailable",
            )
            onProgress(unavailable)
            return unavailable
        }

        val validTargets = ArrayList<Pair<VectorIndexTarget, String>>(targets.size)
        val seenKeys = HashSet<Pair<String, String>>(targets.size)
        for (t in targets) {
            val normalized = VectorQuantizer.normalizeCanonicalText(t.canonicalText)
            if (normalized.isEmpty()) continue
            val dedupeKey = t.ownerType to t.ownerId
            if (!seenKeys.add(dedupeKey)) continue
            val hash = VectorQuantizer.sha256Hex(normalized)
            validTargets.add(t.copy(canonicalText = normalized) to hash)
        }

        if (validTargets.isEmpty()) {
            val empty = VectorIndexProgress(running = false, indexed = 0, total = 0, profileKey = profileKey)
            onProgress(empty)
            return empty
        }

        val existingRowsByOwner = HashMap<Pair<String, String>, MemoryEmbeddingEntity>()
        val convIds = validTargets.map { it.first.conversationId }.distinct()
        for (convId in convIds) {
            currentCoroutineContext().ensureActive()
            val hashRows = withContext(Dispatchers.IO) {
                dao.getHashesForProfile(convId, profileKey)
            }
            if (hashRows.isNotEmpty()) {
                val fullRows = withContext(Dispatchers.IO) {
                    if (convId.isEmpty()) {
                        dao.getGlobalByProfile(profileKey)
                    } else {
                        dao.getByConversationAndProfile(convId, profileKey)
                    }
                }
                for (row in fullRows) {
                    existingRowsByOwner[row.ownerType to row.ownerId] = row
                    if (isValidInt8Row(row, expectedDim = null)) {
                        recordCachedVector(
                            profileKey = profileKey,
                            hash = row.contentHash,
                            dim = row.dim,
                            quantType = row.quantType,
                            scale = row.scale,
                            blob = row.vectorBlob,
                        )
                    }
                }
            }
        }

        val knownDimSnapshot = synchronized(cacheLock) { knownDimByProfile[profileKey] }
        val needProcess = ArrayList<Pair<VectorIndexTarget, String>>()
        for ((target, hash) in validTargets) {
            val existing = existingRowsByOwner[target.ownerType to target.ownerId]
            val existingMatches = existing != null &&
                existing.contentHash == hash &&
                isValidInt8Row(existing, expectedDim = knownDimSnapshot)
            if (!existingMatches) {
                needProcess.add(target to hash)
            }
        }

        val reusedEntities = ArrayList<MemoryEmbeddingEntity>()
        val pending = ArrayList<Pair<VectorIndexTarget, String>>()
        val nowForReuse = clockMs()
        val dirtyReuseConvs = HashSet<String>()
        for ((target, hash) in needProcess) {
            val cached = synchronized(cacheLock) {
                val expectedDim = knownDimByProfile[profileKey]
                contentVectorCache[ContentVectorKey(profileKey, hash)]?.takeIf { vec ->
                    vec.quantType == VectorQuantizer.QUANT_INT8_UNIT &&
                        vec.dim > 0 &&
                        vec.vectorBlob.size == vec.dim &&
                        (expectedDim == null || vec.dim == expectedDim)
                }
            }
            if (cached != null) {
                reusedEntities.add(
                    MemoryEmbeddingEntity(
                        ownerType = target.ownerType,
                        ownerId = target.ownerId,
                        conversationId = target.conversationId,
                        profileKey = profileKey,
                        contentHash = hash,
                        dim = cached.dim,
                        quantType = cached.quantType,
                        scale = cached.scale,
                        vectorBlob = cached.vectorBlob.copyOf(),
                        updatedAt = nowForReuse,
                    ),
                )
                dirtyReuseConvs.add(target.conversationId)
            } else {
                pending.add(target to hash)
            }
        }

        if (reusedEntities.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                dao.upsertAll(reusedEntities)
            }
            for (convId in dirtyReuseConvs) {
                invalidateCache(conversationId = convId, profileKey = profileKey)
            }
        }

        val total = validTargets.size
        var indexedSoFar = total - pending.size

        if (pending.isEmpty()) {
            if (pruneStale) {
                pruneStaleProfilesInternal(profileKey)
            }
            val complete = VectorIndexProgress(
                running = false,
                indexed = indexedSoFar,
                total = total,
                profileKey = profileKey,
            )
            onProgress(complete)
            return complete
        }

        onProgress(
            VectorIndexProgress(
                running = true,
                indexed = indexedSoFar,
                total = total,
                profileKey = profileKey,
            ),
        )

        val effectiveBatch = batchSize.coerceIn(1, 64)
        var start = 0
        while (start < pending.size) {
            currentCoroutineContext().ensureActive()
            val end = (start + effectiveBatch).coerceAtMost(pending.size)
            val batch = pending.subList(start, end)
            val batchTexts = batch.map { it.first.canonicalText }

            val vectors = try {
                embedBatchWithBackoff(engine, batchTexts)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val failed = VectorIndexProgress(
                    running = false,
                    indexed = indexedSoFar,
                    total = total,
                    profileKey = profileKey,
                    lastError = t.message ?: "Embedding batch failed",
                )
                onProgress(failed)
                return failed
            }

            if (vectors.size != batch.size) {
                val failed = VectorIndexProgress(
                    running = false,
                    indexed = indexedSoFar,
                    total = total,
                    profileKey = profileKey,
                    lastError = "Embedding count mismatch: expected ${batch.size}, got ${vectors.size}",
                )
                onProgress(failed)
                return failed
            }

            val now = clockMs()
            val entities = ArrayList<MemoryEmbeddingEntity>(batch.size)
            val dirtyConversations = HashSet<String>()
            for (i in batch.indices) {
                val (target, hash) = batch[i]
                val unit = VectorQuantizer.l2Normalize(vectors[i])
                if (unit.isEmpty()) continue
                val (blob, scale) = VectorQuantizer.quantizeInt8(unit)
                recordCachedVector(
                    profileKey = profileKey,
                    hash = hash,
                    dim = unit.size,
                    quantType = VectorQuantizer.QUANT_INT8_UNIT,
                    scale = scale,
                    blob = blob,
                    fromAuthoritativeEmbed = true,
                )
                entities.add(
                    MemoryEmbeddingEntity(
                        ownerType = target.ownerType,
                        ownerId = target.ownerId,
                        conversationId = target.conversationId,
                        profileKey = profileKey,
                        contentHash = hash,
                        dim = unit.size,
                        quantType = VectorQuantizer.QUANT_INT8_UNIT,
                        scale = scale,
                        vectorBlob = blob,
                        updatedAt = now,
                    ),
                )
                dirtyConversations.add(target.conversationId)
            }

            if (entities.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    dao.upsertAll(entities)
                }
                for (convId in dirtyConversations) {
                    invalidateCache(conversationId = convId, profileKey = profileKey)
                }
                indexedSoFar += entities.size
            }

            onProgress(
                VectorIndexProgress(
                    running = end < pending.size,
                    indexed = indexedSoFar,
                    total = total,
                    profileKey = profileKey,
                ),
            )
            start = end
            if (start < pending.size) {
                yield()
            }
        }

        if (pruneStale) {
            pruneStaleProfilesInternal(profileKey)
        }

        val finished = VectorIndexProgress(
            running = false,
            indexed = indexedSoFar,
            total = total,
            profileKey = profileKey,
        )
        onProgress(finished)
        return finished
    }

    suspend fun scoreCandidates(
        conversationId: String,
        queryText: String,
        candidateIds: Set<String>,
        engine: EmbeddingEngine,
        timeoutMs: Long = 1500L,
        minCosineOverride: Double? = null,
        extraScopeIds: List<String> = emptyList(),
    ): Map<String, Double> {
        if (candidateIds.isEmpty() || queryText.isBlank()) return emptyMap()
        if (!engine.isAvailable()) return emptyMap()

        val truncatedQuery = truncateQueryForEmbedding(queryText, DEFAULT_MAX_QUERY_CHARS)
        if (truncatedQuery.isEmpty()) return emptyMap()

        val profileKey = engine.profileKey
        val queryHash = VectorQuantizer.sha256Hex(truncatedQuery)
        val cachedVec = queryCache.get(profileKey, queryHash)

        val queryUnit = if (cachedVec != null) {
            cachedVec
        } else {
            val rawVec = withTimeoutOrNull(timeoutMs.coerceAtLeast(50L)) {
                try {
                    engine.embed(listOf(truncatedQuery), isQuery = true).firstOrNull()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    null
                }
            } ?: return emptyMap()
            if (rawVec.isEmpty()) return emptyMap()
            val normalized = VectorQuantizer.l2Normalize(rawVec)
            queryCache.put(profileKey, queryHash, normalized)
            normalized
        }

        val minCosine = (minCosineOverride ?: engine.minCosineFloor).coerceIn(0.0, 1.0)
        val convSlice = getOrLoadSlice(conversationId, profileKey)
        val normalizedExtras = extraScopeIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != conversationId }
            .distinct()
        val extraSlices = HashMap<String, FlatVectorSlice>(normalizedExtras.size)
        var globalSlice: FlatVectorSlice? = null

        val scores = HashMap<String, Double>()
        for (id in candidateIds) {
            var cos = scoreInSlice(queryUnit, convSlice, id)
            if (cos == null && normalizedExtras.isNotEmpty()) {
                for (scopeId in normalizedExtras) {
                    val slice = extraSlices[scopeId] ?: getOrLoadSlice(scopeId, profileKey).also {
                        extraSlices[scopeId] = it
                    }
                    cos = scoreInSlice(queryUnit, slice, id)
                    if (cos != null) break
                }
            }
            if (cos == null && conversationId.isNotEmpty()) {
                if (globalSlice == null) {
                    globalSlice = getOrLoadSlice("", profileKey)
                }
                cos = scoreInSlice(queryUnit, globalSlice, id)
            }
            if (cos != null) {
                val clamped = cos.coerceIn(0.0, 1.0)
                if (clamped >= minCosine) {
                    scores[id] = clamped
                }
            }
        }
        return scores
    }

    suspend fun rerankIfEnabled(
        query: String,
        candidates: List<Pair<String, String>>,
        rerankEngine: RerankEngine?,
        timeoutMs: Long = 1500L,
        maxCandidates: Int = DEFAULT_RERANK_MAX_CANDIDATES,
    ): Map<String, Double> {
        if (rerankEngine == null || candidates.isEmpty() || query.isBlank()) return emptyMap()
        val capped = candidates.take(maxCandidates.coerceIn(1, 32))
        val trimmedQuery = truncateQueryForEmbedding(query, DEFAULT_RERANK_QUERY_CHARS)
        if (trimmedQuery.isEmpty()) return emptyMap()

        val docs = capped.map { it.second }
        val rawScores = withTimeoutOrNull(timeoutMs.coerceAtLeast(50L)) {
            try {
                rerankEngine.rerank(trimmedQuery, docs, docs.size)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                emptyMap()
            }
        } ?: return emptyMap()

        if (rawScores.isEmpty()) return emptyMap()
        val mapped = LinkedHashMap<String, Double>(rawScores.size)
        for ((idx, score) in rawScores) {
            if (idx in capped.indices && score.isFinite()) {
                mapped[capped[idx].first] = score.coerceIn(0.0, 1.0)
            }
        }
        return mapped
    }

    suspend fun deleteOwner(
        ownerType: String,
        ownerId: String,
        conversationId: String? = null,
    ) {
        withContext(Dispatchers.IO) {
            dao.deleteByOwner(ownerType, ownerId)
        }
        invalidateCache(conversationId = conversationId, profileKey = null)
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
        invalidateCache(conversationId = null, profileKey = null)
        synchronized(cacheLock) {
            contentVectorCache.clear()
            knownDimByProfile.clear()
        }
        queryCache.clear()
    }

    suspend fun countByProfile(profileKey: String): Int =
        withContext(Dispatchers.IO) { dao.countByProfile(profileKey) }

    fun invalidateCache(
        conversationId: String? = null,
        profileKey: String? = null,
    ) {
        synchronized(cacheLock) {
            if (conversationId == null && profileKey == null) {
                sliceCache.clear()
                return
            }
            val iter = sliceCache.entries.iterator()
            while (iter.hasNext()) {
                val key = iter.next().key
                val convMatch = conversationId == null || key.conversationId == conversationId
                val profileMatch = profileKey == null || key.profileKey == profileKey
                if (convMatch && profileMatch) {
                    iter.remove()
                }
            }
        }
    }

    private suspend fun getOrLoadSlice(
        conversationId: String,
        profileKey: String,
    ): FlatVectorSlice {
        val key = CacheKey(conversationId, profileKey)
        synchronized(cacheLock) {
            sliceCache[key]?.let { return it }
        }
        val rows = withContext(Dispatchers.IO) {
            if (conversationId.isEmpty()) {
                dao.getGlobalByProfile(profileKey)
            } else {
                dao.getByConversationAndProfile(conversationId, profileKey)
            }
        }
        val slice = buildFlatSlice(rows)
        synchronized(cacheLock) {
            sliceCache[key] = slice
        }
        return slice
    }

    private fun buildFlatSlice(rows: List<MemoryEmbeddingEntity>): FlatVectorSlice {
        val validInt8 = rows.filter {
            it.dim > 0 &&
                it.quantType == VectorQuantizer.QUANT_INT8_UNIT &&
                it.vectorBlob.size == it.dim
        }
        if (validInt8.isEmpty()) {
            return EMPTY_SLICE
        }
        val expectedDim = validInt8.first().dim
        val aligned = validInt8.filter { it.dim == expectedDim }
        val count = aligned.size
        val ownerIds = ArrayList<String>(count)
        val indexById = HashMap<String, Int>(count * 2)
        val scales = FloatArray(count)
        val flatMatrix = ByteArray(count * expectedDim)

        for (i in 0 until count) {
            val row = aligned[i]
            ownerIds.add(row.ownerId)
            indexById[row.ownerId] = i
            scales[i] = row.scale
            System.arraycopy(row.vectorBlob, 0, flatMatrix, i * expectedDim, expectedDim)
        }

        return FlatVectorSlice(
            dim = expectedDim,
            ownerIds = ownerIds,
            ownerIndexById = indexById,
            scales = scales,
            flatMatrix = flatMatrix,
        )
    }

    private fun scoreInSlice(
        queryUnit: FloatArray,
        slice: FlatVectorSlice,
        ownerId: String,
    ): Double? {
        if (slice.dim <= 0 || slice.dim != queryUnit.size) return null
        val rowIdx = slice.ownerIndexById[ownerId] ?: return null
        val dot = VectorQuantizer.dotProductInt8(
            queryUnit = queryUnit,
            flatMatrix = slice.flatMatrix,
            rowOffset = rowIdx * slice.dim,
            dim = slice.dim,
            scale = slice.scales[rowIdx],
        )
        return dot.toDouble()
    }

    private suspend fun embedBatchWithBackoff(
        engine: EmbeddingEngine,
        texts: List<String>,
    ): List<FloatArray> {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return engine.embed(texts, isQuery = false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: ApiException) {
                val retryable = e.status == 429 || e.status == 503 || e.status == 504
                if (!retryable || attempt >= MAX_BATCH_RETRIES) {
                    throw e
                }
                delayFn(delayMs)
                delayMs = (delayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                attempt++
            }
        }
    }

    private suspend fun pruneStaleProfilesInternal(activeProfileKey: String) {
        withContext(Dispatchers.IO) {
            dao.deleteStaleProfiles(activeProfileKey)
        }
        synchronized(cacheLock) {
            val iter = sliceCache.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().key.profileKey != activeProfileKey) {
                    iter.remove()
                }
            }
            val vecIter = contentVectorCache.entries.iterator()
            while (vecIter.hasNext()) {
                if (vecIter.next().key.profileKey != activeProfileKey) {
                    vecIter.remove()
                }
            }
            val dimIter = knownDimByProfile.entries.iterator()
            while (dimIter.hasNext()) {
                if (dimIter.next().key != activeProfileKey) {
                    dimIter.remove()
                }
            }
        }
    }

    companion object {
        const val WORLDBOOK_CONV_PREFIX = "wb:"
        const val DEFAULT_MAX_QUERY_CHARS = 500
        const val DEFAULT_RERANK_QUERY_CHARS = 800
        const val DEFAULT_RERANK_MAX_CANDIDATES = 16

        fun worldbookScopeId(worldbookId: String): String = "$WORLDBOOK_CONV_PREFIX${worldbookId.trim()}"

        private const val MAX_BATCH_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 500L
        private const val MAX_RETRY_DELAY_MS = 4000L

        private val EMPTY_SLICE = FlatVectorSlice(
            dim = 0,
            ownerIds = emptyList(),
            ownerIndexById = emptyMap(),
            scales = FloatArray(0),
            flatMatrix = ByteArray(0),
        )

        fun truncateQueryForEmbedding(rawQuery: String, maxChars: Int = DEFAULT_MAX_QUERY_CHARS): String {
            val clean = VectorQuantizer.normalizeCanonicalText(rawQuery)
            if (clean.length <= maxChars) return clean
            if (maxChars <= 16) return clean.take(maxChars)
            val headLen = (maxChars * 3) / 10
            val tailLen = maxChars - headLen - 1
            val head = clean.substring(0, headLen).trimEnd()
            val tail = clean.substring(clean.length - tailLen).trimStart()
            return "$head\n$tail"
        }
    }
}
