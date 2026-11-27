package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.DiscoveredRetrievalModel
import com.gugachat.app.data.api.EmbeddingConnectionTestResult
import com.gugachat.app.data.api.RemoteApiEmbeddingEngine
import com.gugachat.app.data.api.RemoteApiRerankEngine
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalOnnxEmbeddingEngine
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.domain.chat.EmbeddingEngine
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RerankEngine
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.domain.chat.VectorIndexTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapts [LocalOnnxEmbeddingEngine] for a specific local model ID into the shared [EmbeddingEngine]
 * interface consumed by [VectorMemoryIndex].
 */
class LocalOnnxEmbeddingAdapter(
    private val engine: LocalOnnxEmbeddingEngine,
    private val modelId: String,
) : EmbeddingEngine {
    override val profileKey: String
        get() = engine.profileKey(modelId)

    override val minCosineFloor: Double
        get() = engine.minCosineFloor(modelId)

    override suspend fun isAvailable(): Boolean = engine.isModelInstalled(modelId)

    override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> =
        engine.embed(modelId = modelId, texts = texts, isQuery = isQuery)
}

/**
 * Orchestrates hybrid semantic retrieval across Pillar 1 (on-device ONNX embedding),
 * Pillar 2 (cloud/LAN embedding & optional rerank APIs), and the Room-backed INT8 [VectorMemoryIndex].
 *
 * Always degrades gracefully to empty semantic scores (pure lexical BM25F + CJK segmentation)
 * on offline errors, timeouts, missing credentials, or uninstalled local models.
 */
class RetrievalCoordinator internal constructor(
    private val retrievalSettingsProvider: () -> RetrievalSettings,
    private val providerLookup: (String) -> ApiProviderConfig?,
    private val directApiClient: DirectApiClient,
    private val localModelManager: LocalEmbeddingModelManager,
    private val localOnnxEngine: LocalOnnxEmbeddingEngine,
    private val vectorMemoryIndex: VectorMemoryIndex,
) {
    constructor(
        settingsStore: SettingsStore,
        settingsState: StateFlow<AppSettings>,
        apiProviderStore: ApiProviderStore,
        directApiClient: DirectApiClient,
        localModelManager: LocalEmbeddingModelManager,
        localOnnxEngine: LocalOnnxEmbeddingEngine,
        vectorMemoryIndex: VectorMemoryIndex,
    ) : this(
        retrievalSettingsProvider = { settingsState.value.retrievalSettings },
        providerLookup = { id -> apiProviderStore.findById(id) },
        directApiClient = directApiClient,
        localModelManager = localModelManager,
        localOnnxEngine = localOnnxEngine,
        vectorMemoryIndex = vectorMemoryIndex,
    ) {
        // Keep SettingsStore reference available for future async settings queries if needed
        @Suppress("UNUSED_EXPRESSION")
        settingsStore
    }

    private val _indexProgress = MutableStateFlow(VectorIndexProgress())
    val indexProgress: StateFlow<VectorIndexProgress> = _indexProgress.asStateFlow()

    private val activeIndexJobs = ConcurrentHashMap<String, Job>()

    fun currentRetrievalSettings(): RetrievalSettings = retrievalSettingsProvider()

    fun isLocalSessionLoaded(): Boolean = localOnnxEngine.isSessionLoaded

    fun unloadLocalModelNow() {
        localOnnxEngine.unloadNow()
    }

    fun resolveActiveEmbeddingEngine(
        settings: RetrievalSettings = currentRetrievalSettings(),
    ): EmbeddingEngine? = when (settings.mode) {
        RetrievalMode.LEXICAL_ONLY -> null
        RetrievalMode.LOCAL_SMALL_MODEL -> {
            val modelId = settings.localModelId.trim().ifBlank { LocalEmbeddingModelManager.DEFAULT_MODEL_ID }
            if (localModelManager.isModelInstalled(modelId)) {
                LocalOnnxEmbeddingAdapter(localOnnxEngine, modelId)
            } else {
                null
            }
        }
        RetrievalMode.REMOTE_API -> {
            val cfg = settings.remoteEmbedding
            if (cfg.modelId.isBlank()) {
                null
            } else {
                val provider = DirectApiClient.resolveRemoteEmbeddingProvider(cfg, providerLookup)
                if (provider != null && provider.protocol != ApiProtocol.ANTHROPIC) {
                    RemoteApiEmbeddingEngine(directApiClient, provider, cfg)
                } else {
                    null
                }
            }
        }
    }

    fun resolveActiveRerankEngine(
        settings: RetrievalSettings = currentRetrievalSettings(),
    ): RerankEngine? {
        if (settings.mode == RetrievalMode.LEXICAL_ONLY) return null
        val cfg = settings.remoteRerank
        if (!cfg.enabled) return null
        if (!cfg.useTeiSchema && cfg.modelId.isBlank()) return null
        val provider = DirectApiClient.resolveRemoteRerankProvider(cfg, providerLookup) ?: return null
        if (provider.protocol == ApiProtocol.ANTHROPIC || provider.protocol == ApiProtocol.GEMINI) {
            return null
        }
        return RemoteApiRerankEngine(directApiClient, provider, cfg)
    }

    fun isRerankEnabled(settings: RetrievalSettings = currentRetrievalSettings()): Boolean =
        resolveActiveRerankEngine(settings) != null

    /**
     * Returns one of: `"lexical"`, `"local_vector"`, `"remote_vector"`, `"remote_vector_rerank"`.
     */
    fun activeModeLabel(settings: RetrievalSettings = currentRetrievalSettings()): String {
        val engine = resolveActiveEmbeddingEngine(settings) ?: return MODE_LEXICAL
        return when (settings.mode) {
            RetrievalMode.LEXICAL_ONLY -> MODE_LEXICAL
            RetrievalMode.LOCAL_SMALL_MODEL -> {
                if (engine is LocalOnnxEmbeddingAdapter) MODE_LOCAL_VECTOR else MODE_LEXICAL
            }
            RetrievalMode.REMOTE_API -> {
                if (resolveActiveRerankEngine(settings) != null) {
                    MODE_REMOTE_VECTOR_RERANK
                } else {
                    MODE_REMOTE_VECTOR
                }
            }
        }
    }

    /**
     * Computes semantic cosine scores for [candidateIds] against [queryText] with a strict timeout
     * (default 1500ms, 0ms on query LRU cache hit). Never throws on network/inference errors;
     * returns `(emptyMap(), minCosineFloor)` as a clean lexical fallback.
     */
    suspend fun prepareSendSemanticScores(
        conversationId: String,
        queryText: String,
        candidateIds: Set<String>,
        timeoutMs: Long = DEFAULT_SEND_TIMEOUT_MS,
        mountedWorldbookIds: List<String> = emptyList(),
    ): Pair<Map<String, Double>, Double> {
        val settings = currentRetrievalSettings()
        val engine = resolveActiveEmbeddingEngine(settings)
        val fallbackFloor = (settings.minCosineOverride ?: DEFAULT_MIN_COSINE_FLOOR).coerceIn(0.0, 1.0)
        if (engine == null || candidateIds.isEmpty() || queryText.isBlank()) {
            return emptyMap<String, Double>() to fallbackFloor
        }
        val effectiveFloor = (settings.minCosineOverride ?: engine.minCosineFloor).coerceIn(0.0, 1.0)
        val extraScopes = mountedWorldbookIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { VectorMemoryIndex.worldbookScopeId(it) }
        val scores = try {
            vectorMemoryIndex.scoreCandidates(
                conversationId = conversationId,
                queryText = queryText,
                candidateIds = candidateIds,
                engine = engine,
                timeoutMs = timeoutMs,
                minCosineOverride = effectiveFloor,
                extraScopeIds = extraScopes,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyMap()
        }
        return scores to effectiveFloor
    }

    /**
     * Optionally reranks dynamic recall candidates when remote rerank is enabled.
     * Gracefully falls back to `emptyMap()` on timeout or network error.
     */
    suspend fun rerankCandidatesIfEnabled(
        query: String,
        candidates: List<Pair<String, String>>,
        timeoutMs: Long = DEFAULT_SEND_TIMEOUT_MS,
    ): Map<String, Double> {
        val rerankEngine = resolveActiveRerankEngine() ?: return emptyMap()
        if (candidates.isEmpty() || query.isBlank()) return emptyMap()
        return try {
            vectorMemoryIndex.rerankIfEnabled(
                query = query,
                candidates = candidates,
                rerankEngine = rerankEngine,
                timeoutMs = timeoutMs,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    /**
     * Schedules background incremental vector indexing on [Dispatchers.Default] for all enabled
     * lore, ledger, episodes, and global user memories. Cancels only a superseded job for the same
     * [conversationId] scope so concurrent worldbook or global jobs are never cancelled.
     */
    fun scheduleConversationIndexing(
        scope: CoroutineScope,
        conversationId: String,
        lore: List<NovelLoreEntry>,
        ledger: List<MemoryLedgerItem>,
        episodes: List<MemoryEpisode>,
        userMemories: List<UserMemory>,
        batchSize: Int = DEFAULT_INDEX_BATCH_SIZE,
    ): Job? {
        val scopeKey = conversationId.ifBlank { SCOPE_GLOBAL }
        val settings = currentRetrievalSettings()
        val engine = resolveActiveEmbeddingEngine(settings)
        if (engine == null) {
            activeIndexJobs.remove(scopeKey)?.cancel()
            if (activeIndexJobs.isEmpty() && _indexProgress.value.running) {
                _indexProgress.value = VectorIndexProgress()
            }
            return null
        }

        val targets = buildIndexTargets(
            conversationId = conversationId,
            lore = lore,
            ledger = ledger,
            episodes = episodes,
            userMemories = userMemories,
        )
        return launchScopedIndexing(
            scope = scope,
            scopeKey = scopeKey,
            targets = targets,
            engine = engine,
            batchSize = batchSize,
            pruneStale = true,
        )
    }

    /**
     * Indexes a global [worldbookId] ONCE under `conversationId = "wb:$worldbookId"`
     * (`ownerType = "worldbook_entry"`) so every bound conversation shares the exact same vectors.
     */
    fun scheduleWorldbookIndexing(
        scope: CoroutineScope,
        worldbookId: String,
        entries: List<WorldbookEntryEntity>,
        batchSize: Int = DEFAULT_INDEX_BATCH_SIZE,
    ): Job? {
        val cleanWbId = worldbookId.trim()
        if (cleanWbId.isEmpty()) return null
        val scopeKey = VectorMemoryIndex.worldbookScopeId(cleanWbId)
        val settings = currentRetrievalSettings()
        val engine = resolveActiveEmbeddingEngine(settings)
        if (engine == null) {
            activeIndexJobs.remove(scopeKey)?.cancel()
            if (activeIndexJobs.isEmpty() && _indexProgress.value.running) {
                _indexProgress.value = VectorIndexProgress()
            }
            return null
        }

        val targets = buildWorldbookIndexTargets(worldbookId = cleanWbId, entries = entries)
        return launchScopedIndexing(
            scope = scope,
            scopeKey = scopeKey,
            targets = targets,
            engine = engine,
            batchSize = batchSize,
            pruneStale = true,
        )
    }

    /**
     * Schedules background indexing strictly for global [UserMemory] records under [SCOPE_GLOBAL].
     */
    fun scheduleGlobalMemoriesIndexing(
        scope: CoroutineScope,
        userMemories: List<UserMemory>,
        batchSize: Int = DEFAULT_INDEX_BATCH_SIZE,
    ): Job? {
        val settings = currentRetrievalSettings()
        val engine = resolveActiveEmbeddingEngine(settings)
        if (engine == null) {
            activeIndexJobs.remove(SCOPE_GLOBAL)?.cancel()
            if (activeIndexJobs.isEmpty() && _indexProgress.value.running) {
                _indexProgress.value = VectorIndexProgress()
            }
            return null
        }
        val targets = buildIndexTargets(
            conversationId = "",
            lore = emptyList(),
            ledger = emptyList(),
            episodes = emptyList(),
            userMemories = userMemories,
        )
        return launchScopedIndexing(
            scope = scope,
            scopeKey = SCOPE_GLOBAL,
            targets = targets,
            engine = engine,
            batchSize = batchSize,
            pruneStale = true,
        )
    }

    private fun launchScopedIndexing(
        scope: CoroutineScope,
        scopeKey: String,
        targets: List<VectorIndexTarget>,
        engine: EmbeddingEngine,
        batchSize: Int,
        pruneStale: Boolean,
    ): Job {
        lateinit var launched: Job
        launched = scope.launch(Dispatchers.Default) {
            try {
                vectorMemoryIndex.ensureIndexed(
                    targets = targets,
                    engine = engine,
                    batchSize = batchSize,
                    pruneStale = pruneStale,
                    onProgress = { progress -> _indexProgress.value = progress },
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _indexProgress.value = _indexProgress.value.copy(
                    running = false,
                    lastError = t.message ?: "Vector indexing failed",
                )
            } finally {
                activeIndexJobs.remove(scopeKey, launched)
            }
        }
        val prev = activeIndexJobs.put(scopeKey, launched)
        if (prev != null && prev !== launched) {
            prev.cancel()
        }
        return launched
    }

    suspend fun testRemoteEmbedding(
        config: RemoteEmbeddingConfig,
    ): Result<EmbeddingConnectionTestResult> {
        val provider = DirectApiClient.resolveRemoteEmbeddingProvider(config, providerLookup)
            ?: return Result.failure(IllegalArgumentException("Embedding endpoint URL is empty"))
        return try {
            Result.success(
                directApiClient.testEmbeddingConnection(
                    provider = provider,
                    modelId = config.modelId,
                    dimensions = config.dimensions,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchRemoteRetrievalModels(
        providerId: String,
        customBaseUrl: String,
        customApiKey: String,
        protocol: ApiProtocol,
    ): Result<List<DiscoveredRetrievalModel>> {
        val tempConfig = RemoteEmbeddingConfig(
            providerId = providerId,
            customBaseUrl = customBaseUrl,
            customApiKey = customApiKey,
            protocol = protocol,
        )
        val provider = DirectApiClient.resolveRemoteEmbeddingProvider(tempConfig, providerLookup)
            ?: return Result.failure(IllegalArgumentException("Endpoint URL is empty"))
        return try {
            Result.success(directApiClient.fetchEmbeddingAndRerankModels(provider))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun countIndexedVectors(): Int {
        val settings = currentRetrievalSettings()
        val profileKey = resolveActiveEmbeddingEngine(settings)?.profileKey
            ?: when (settings.mode) {
                RetrievalMode.LOCAL_SMALL_MODEL -> localOnnxEngine.profileKey(
                    settings.localModelId.ifBlank { LocalEmbeddingModelManager.DEFAULT_MODEL_ID },
                )
                RetrievalMode.REMOTE_API -> {
                    val cfg = settings.remoteEmbedding
                    val provider = DirectApiClient.resolveRemoteEmbeddingProvider(cfg, providerLookup)
                    if (provider != null && cfg.modelId.isNotBlank()) {
                        RemoteApiEmbeddingEngine(directApiClient, provider, cfg).profileKey
                    } else {
                        null
                    }
                }
                RetrievalMode.LEXICAL_ONLY -> null
            }
        if (profileKey == null) return 0
        return runCatching { vectorMemoryIndex.countByProfile(profileKey) }.getOrDefault(0)
    }

    suspend fun clearAllVectors() {
        val keys = activeIndexJobs.keys.toList()
        for (k in keys) {
            activeIndexJobs.remove(k)?.cancel()
        }
        vectorMemoryIndex.clearAll()
        _indexProgress.value = VectorIndexProgress()
    }

    suspend fun deleteOwnerVector(
        ownerType: String,
        ownerId: String,
        conversationId: String? = null,
    ) {
        runCatching {
            vectorMemoryIndex.deleteOwner(ownerType, ownerId, conversationId)
        }
    }

    companion object {
        const val MODE_LEXICAL = "lexical"
        const val MODE_LOCAL_VECTOR = "local_vector"
        const val MODE_REMOTE_VECTOR = "remote_vector"
        const val MODE_REMOTE_VECTOR_RERANK = "remote_vector_rerank"

        const val OWNER_NOVEL_LORE = "novel_lore"
        const val OWNER_RP_LEDGER = "rp_ledger"
        const val OWNER_EPISODE = "episode_archived"
        const val OWNER_USER_MEMORY = "user_memory"
        const val OWNER_WORLDBOOK_ENTRY = "worldbook_entry"

        const val SCOPE_GLOBAL = "global"

        const val DEFAULT_SEND_TIMEOUT_MS = 1500L
        const val DEFAULT_INDEX_BATCH_SIZE = 16
        const val DEFAULT_MIN_COSINE_FLOOR = 0.50

        internal fun buildWorldbookIndexTargets(
            worldbookId: String,
            entries: List<WorldbookEntryEntity>,
        ): List<VectorIndexTarget> {
            val scopeConvId = VectorMemoryIndex.worldbookScopeId(worldbookId)
            val targets = ArrayList<VectorIndexTarget>(entries.size)
            for (entry in entries) {
                if (!entry.enabled) continue
                val text = formatCanonicalTargetText(entry.title, entry.content)
                if (text.isNotEmpty()) {
                    targets.add(
                        VectorIndexTarget(
                            ownerType = OWNER_WORLDBOOK_ENTRY,
                            ownerId = entry.id,
                            conversationId = scopeConvId,
                            canonicalText = text,
                        ),
                    )
                }
            }
            return targets
        }

        internal fun formatCanonicalTargetText(title: String?, content: String?): String {
            val t = title?.trim().orEmpty()
            val c = content?.trim().orEmpty()
            return when {
                t.isNotEmpty() && c.isNotEmpty() -> "$t: $c"
                t.isNotEmpty() -> t
                else -> c
            }
        }

        internal fun buildIndexTargets(
            conversationId: String,
            lore: List<NovelLoreEntry>,
            ledger: List<MemoryLedgerItem>,
            episodes: List<MemoryEpisode>,
            userMemories: List<UserMemory>,
        ): List<VectorIndexTarget> {
            val targets = ArrayList<VectorIndexTarget>(
                lore.size + ledger.size + episodes.size + userMemories.size,
            )
            for (entry in lore) {
                if (!entry.enabled || entry.stale) continue
                val text = formatCanonicalTargetText(entry.title, entry.content)
                if (text.isNotEmpty()) {
                    targets.add(
                        VectorIndexTarget(
                            ownerType = OWNER_NOVEL_LORE,
                            ownerId = entry.id,
                            conversationId = conversationId,
                            canonicalText = text,
                        ),
                    )
                }
            }
            for (item in ledger) {
                if (!item.enabled || item.stale) continue
                val text = formatCanonicalTargetText(item.title, item.content)
                if (text.isNotEmpty()) {
                    targets.add(
                        VectorIndexTarget(
                            ownerType = OWNER_RP_LEDGER,
                            ownerId = item.id,
                            conversationId = conversationId,
                            canonicalText = text,
                        ),
                    )
                }
            }
            for (ep in episodes) {
                if (ep.stale) continue
                val text = formatCanonicalTargetText(ep.title, ep.summary)
                if (text.isNotEmpty()) {
                    targets.add(
                        VectorIndexTarget(
                            ownerType = OWNER_EPISODE,
                            ownerId = ep.id,
                            conversationId = conversationId,
                            canonicalText = text,
                        ),
                    )
                }
            }
            for (mem in userMemories) {
                if (!mem.enabled || mem.content.isBlank()) continue
                val text = formatCanonicalTargetText(mem.category.name, mem.content)
                if (text.isNotEmpty()) {
                    targets.add(
                        VectorIndexTarget(
                            ownerType = OWNER_USER_MEMORY,
                            ownerId = mem.id,
                            conversationId = "",
                            canonicalText = text,
                        ),
                    )
                }
            }
            return targets
        }
    }
}
