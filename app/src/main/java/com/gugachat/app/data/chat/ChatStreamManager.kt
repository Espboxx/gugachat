package com.gugachat.app.data.chat

import android.content.Context
import androidx.room.withTransaction
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.ZhStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.runtime.AppForegroundTracker
import com.gugachat.app.core.runtime.RuntimeKeepAliveController
import com.gugachat.app.core.runtime.RuntimeKeepAliveService
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.theme.ToastController
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.ChatTokenUsageDto
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatApi
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.CompactionOutcome
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ContextCompactor
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.EpisodeConsolidator
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryExtractor
import com.gugachat.app.domain.chat.MessageUsage
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.WorldbookEvalResult
import com.gugachat.app.domain.chat.activeVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveStreamSession(
    val conversationId: String,
    val streamingMessage: ChatMessage,
    val charsGenerated: Int = 0,
    val isStreaming: Boolean = true,
    val statusHint: String? = null,
    val error: String? = null,
)

data class StreamLaunchRequest(
    val conversation: ChatConversation,
    val groupId: Long,
    val modelId: String,
    val existingMessageForVariant: ChatMessage? = null,
    val continuePrefixText: String? = null,
    val overrideMessages: List<com.gugachat.app.data.api.WireChatMessageDto>? = null,
    val draftText: String = "",
    val mutedRecallKeys: Set<String> = emptySet(),
    val modelMetaOverride: OnlineChatModelDto? = null,
)

internal interface StreamKeepAliveGateway {
    fun acquireWork(taskId: String, label: String, onStopRequested: (() -> Unit)? = null)
    fun updateProgress(taskId: String, label: String, charCount: Int)
    fun releaseWork(taskId: String)
}

internal interface StreamContextGateway {
    suspend fun assembleContext(
        conv: ChatConversation,
        msgs: List<ChatMessage>,
        groupId: Long,
        modelId: String,
        modelMetaOverride: OnlineChatModelDto? = null,
        episodesOverride: List<MemoryEpisode>? = null,
        assistantPrefill: String = "",
        draftText: String = "",
        mutedRecallKeys: Set<String> = emptySet(),
    ): AssembledChatPayload

    suspend fun compactOnOverflow(
        conv: ChatConversation,
        messages: List<ChatMessage>,
        fallbackGroupId: Long,
        fallbackModel: String,
    ): CompactionOutcome?

    suspend fun updateTokenCalibration(
        conv: ChatConversation,
        tracking: SentPayloadTracking,
        usage: MessageUsage,
    ): ChatConversation
}

internal interface StreamCompletionGateway {
    suspend fun streamOnce(
        request: OnlineChatCompletionRequestDto,
        onTextDelta: (String) -> Unit,
        onReasoningDelta: (String) -> Unit,
        onUsageSet: (MessageUsage) -> Unit,
        onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
        onResetForRetry: () -> Unit,
    )
}

internal interface StreamPersistenceGateway {
    suspend fun getMessages(convId: String): List<ChatMessage>
    suspend fun upsertMessageSnapshot(msg: ChatMessage)
    suspend fun saveCompactionOutcome(conv: ChatConversation, outcome: CompactionOutcome): List<MemoryEpisode>
    suspend fun finalizeTurnTransaction(
        convId: String,
        fallbackConv: ChatConversation,
        assistantTemplate: ChatMessage,
        rawContent: String,
        reasoning: String,
        usage: MessageUsage?,
        continuePrefixText: String?,
        worldbookResult: WorldbookEvalResult?,
        updatedTokenCalibration: Double?,
        onEndingUnlockedToast: ((String) -> Unit)? = null,
    ): Pair<ChatConversation, ChatMessage>

    suspend fun abortOrphanStreamingMessages()
}

/**
 * Application-scoped streaming orchestration service holding concurrent per-conversation
 * SSE streams decoupled from Android configuration changes and ViewModel lifecycle.
 *
 * All Room reads and transactional commits inside [finalizeTurnTransaction] are strictly
 * keyed by `convId` inside `database.withTransaction` to guarantee zero cross-session bleed
 * when multiple conversations stream concurrently.
 */
class ChatStreamManager internal constructor(
    private val scope: CoroutineScope,
    private val keepAlive: StreamKeepAliveGateway,
    private val contextGateway: StreamContextGateway,
    private val completionGateway: StreamCompletionGateway,
    private val persistenceGateway: StreamPersistenceGateway,
    private val schedulePostTurnMaintenance: (ChatConversation) -> Unit = {},
    private val scheduleVectorIndexing: (String) -> Unit = {},
    private val completionNotifier: (String, ChatConversation, ChatMessage) -> Unit = { _, _, _ -> },
    private val isAppForeground: () -> Boolean = { true },
    private val toastShower: (String, Boolean) -> Unit = { _, _ -> },
    private val retryHintFormatter: (Int, Int) -> String = { attempt, max -> "Retrying ($attempt/$max)..." },
    private val compactingNoticeProvider: () -> String = { "Context compacted automatically, retrying..." },
    private val endingBannerProvider: () -> String = { "Ending unlocked" },
    private val clearTransientMutes: () -> Unit = {},
) {
    constructor(
        appContext: Context,
        scope: CoroutineScope,
        database: AppDatabase,
        settingsStore: SettingsStore,
        settingsState: StateFlow<AppSettings>,
        apiProviderStore: ApiProviderStore,
        onlineChatApi: OnlineChatApi,
        contextAssembler: ChatContextAssembler,
        retrievalCoordinator: RetrievalCoordinator,
        foregroundTracker: AppForegroundTracker,
        keepAliveController: RuntimeKeepAliveController,
        toastController: ToastController,
    ) : this(
        scope = scope,
        keepAlive = object : StreamKeepAliveGateway {
            override fun acquireWork(taskId: String, label: String, onStopRequested: (() -> Unit)?) {
                keepAliveController.acquireWork(taskId = taskId, label = label, onStopRequested = onStopRequested)
            }

            override fun updateProgress(taskId: String, label: String, charCount: Int) {
                keepAliveController.updateProgress(taskId = taskId, label = label, charCount = charCount)
            }

            override fun releaseWork(taskId: String) {
                keepAliveController.releaseWork(taskId)
            }
        },
        contextGateway = object : StreamContextGateway {
            override suspend fun assembleContext(
                conv: ChatConversation,
                msgs: List<ChatMessage>,
                groupId: Long,
                modelId: String,
                modelMetaOverride: OnlineChatModelDto?,
                episodesOverride: List<MemoryEpisode>?,
                assistantPrefill: String,
                draftText: String,
                mutedRecallKeys: Set<String>,
            ): AssembledChatPayload = contextAssembler.assembleContext(
                conv = conv,
                msgs = msgs,
                groupId = groupId,
                modelId = modelId,
                modelMetaOverride = modelMetaOverride,
                episodesOverride = episodesOverride,
                assistantPrefill = assistantPrefill,
                draftText = draftText,
                mutedRecallKeys = mutedRecallKeys,
            )

            override suspend fun compactOnOverflow(
                conv: ChatConversation,
                messages: List<ChatMessage>,
                fallbackGroupId: Long,
                fallbackModel: String,
            ): CompactionOutcome? = ContextCompactor.compactNow(
                chatApi = onlineChatApi,
                conversation = conv,
                messages = messages,
                fallbackGroupId = fallbackGroupId,
                fallbackModel = fallbackModel,
            )

            override suspend fun updateTokenCalibration(
                conv: ChatConversation,
                tracking: SentPayloadTracking,
                usage: MessageUsage,
            ): ChatConversation {
                if (tracking.hasUndecayedImages || tracking.estimatedRawTokens <= 0) return conv
                val usageDto = ChatTokenUsageDto(
                    prompt_tokens = usage.promptTokens,
                    completion_tokens = usage.completionTokens,
                    total_tokens = usage.totalTokens,
                    cache_read_input_tokens = usage.cacheReadInputTokens,
                )
                val actualInput = DirectProtocolCodec.totalInputTokens(tracking.protocol, usageDto)
                if (actualInput == null || actualInput < 200) return conv
                val settings = settingsStore.currentSettings()
                val currentFactor = settings.tokenCalibrationMap[tracking.calibrationKey]
                    ?: conv.tokenCalibration.takeIf { it in 0.60..1.60 }
                    ?: 1.0
                val updatedFactor = TokenEstimator.updateCalibrationEma(
                    currentFactor = currentFactor,
                    actualPromptTokens = actualInput,
                    estimatedRawTokens = tracking.estimatedRawTokens,
                )
                settingsStore.updateTokenCalibration(tracking.calibrationKey, updatedFactor)
                return conv.copy(tokenCalibration = updatedFactor)
            }
        },
        completionGateway = object : StreamCompletionGateway {
            override suspend fun streamOnce(
                request: OnlineChatCompletionRequestDto,
                onTextDelta: (String) -> Unit,
                onReasoningDelta: (String) -> Unit,
                onUsageSet: (MessageUsage) -> Unit,
                onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                onResetForRetry: () -> Unit,
            ) {
                onlineChatApi.streamCompletion(
                    request = request,
                    onDelta = onTextDelta,
                    onReasoning = onReasoningDelta,
                    onUsage = { u ->
                        onUsageSet(
                            MessageUsage(
                                promptTokens = u.prompt_tokens,
                                completionTokens = u.completion_tokens,
                                totalTokens = u.total_tokens,
                                cacheReadInputTokens = u.effectiveCacheReadTokens,
                            ),
                        )
                    },
                    onRetryAttempt = onRetryAttempt,
                    onResetForRetry = onResetForRetry,
                )
            }
        },
        persistenceGateway = RoomStreamPersistenceGateway(database),
        schedulePostTurnMaintenance = { conv ->
            val maintenance = BackgroundTurnMaintenance(
                scope = scope,
                database = database,
                onlineChatApi = onlineChatApi,
                settingsState = settingsState,
                retrievalCoordinator = retrievalCoordinator,
            )
            maintenance.scheduleIfEnabled(conv)
        },
        scheduleVectorIndexing = { convId ->
            val settings = settingsState.value.retrievalSettings
            if (settings.mode != com.gugachat.app.domain.chat.RetrievalMode.LEXICAL_ONLY) {
                scope.launch(Dispatchers.IO) {
                    val dao = database.chatDao()
                    val memDao = database.userMemoryDao()
                    retrievalCoordinator.scheduleConversationIndexing(
                        scope = scope,
                        conversationId = convId,
                        lore = dao.getNovelLoreForConversation(convId).map { it.toDomain() },
                        ledger = dao.getMemoryLedgerForConversation(convId).map { it.toDomain() },
                        episodes = dao.getMemoryEpisodesForConversation(convId).map { it.toDomain() },
                        userMemories = memDao.getAll().map { it.toDomain() },
                    )
                }
            }
        },
        completionNotifier = { convId, conv, finalMsg ->
            val isEn = settingsState.value.language == AppLanguage.EN
            val title = conv.title.ifBlank { if (isEn) "Reply ready" else "回复已生成" }
            val preview = finalMsg.content.trim().take(120).ifEmpty {
                if (isEn) "Generation completed in background" else "后台生成已完成，点击查看回复"
            }
            val notifId = 1104 + (convId.hashCode() and 0x0FFF)
            RuntimeKeepAliveService.completionNotice(
                context = appContext,
                title = title,
                message = preview,
                notificationId = notifId,
            )
        },
        isAppForeground = { foregroundTracker.isAppForeground },
        toastShower = { msg, isError -> toastController.show(msg, isError = isError) },
        retryHintFormatter = { attempt, max ->
            val s = if (settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            s.chat.streamingRetrying.fmt("attempt" to attempt, "max" to max)
        },
        compactingNoticeProvider = {
            val s = if (settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            s.chat.compactingProgress
        },
        endingBannerProvider = {
            val s = if (settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
            s.rp.endingUnlockedBanner
        },
    )

    private val jobsLock = Any()
    private val activeJobs = HashMap<String, Job>()

    private val _activeSessions = MutableStateFlow<Map<String, ActiveStreamSession>>(emptyMap())
    val activeSessions: StateFlow<Map<String, ActiveStreamSession>> = _activeSessions.asStateFlow()

    private val _lastCommittedMessages = MutableStateFlow<Map<String, ChatMessage>>(emptyMap())
    val lastCommittedMessages: StateFlow<Map<String, ChatMessage>> = _lastCommittedMessages.asStateFlow()

    private val _lastWaterlineContext = MutableStateFlow<com.gugachat.app.domain.chat.BuiltChatContext?>(null)
    val lastWaterlineContext: StateFlow<com.gugachat.app.domain.chat.BuiltChatContext?> = _lastWaterlineContext.asStateFlow()

    fun isStreaming(conversationId: String): Boolean =
        _activeSessions.value[conversationId]?.isStreaming == true

    fun clearCommittedBridge(conversationId: String, messageId: String) {
        _lastCommittedMessages.update { cur ->
            val existing = cur[conversationId]
            if (existing?.id == messageId) cur - conversationId else cur
        }
    }

    suspend fun recoverOrphanStreams() {
        persistenceGateway.abortOrphanStreamingMessages()
    }

    fun cancelStream(conversationId: String) {
        val job = synchronized(jobsLock) { activeJobs[conversationId] }
        job?.cancel()
    }

    suspend fun cancelStreamAndWait(conversationId: String) {
        val job = synchronized(jobsLock) { activeJobs[conversationId] }
        if (job != null) {
            job.cancel()
            runCatching { job.join() }
        }
    }

    fun startStream(request: StreamLaunchRequest): Boolean {
        val conv = request.conversation
        val convId = conv.id
        val taskId = "stream:$convId"
        val keepAliveLabel = conv.title.trim().ifBlank { "GuGuChat" }

        synchronized(jobsLock) {
            val existing = activeJobs[convId]
            if (existing != null && existing.isActive) {
                return false
            }
            val job = scope.launch {
                keepAlive.acquireWork(
                    taskId = taskId,
                    label = keepAliveLabel,
                    onStopRequested = { cancelStream(convId) },
                )
                try {
                    runStreamForConversation(request = request, taskId = taskId, keepAliveLabel = keepAliveLabel)
                } finally {
                    withContext(NonCancellable) {
                        keepAlive.releaseWork(taskId)
                        _activeSessions.update { it - convId }
                        synchronized(jobsLock) {
                            if (activeJobs[convId] === coroutineContext[Job]) {
                                activeJobs.remove(convId)
                            }
                        }
                    }
                }
            }
            activeJobs[convId] = job
            return true
        }
    }

    private suspend fun runStreamForConversation(
        request: StreamLaunchRequest,
        taskId: String,
        keepAliveLabel: String,
    ) {
        val conv = request.conversation
        val convId = conv.id
        val isContinue = request.continuePrefixText != null
        val allMsgs = persistenceGateway.getMessages(convId)
        val nextSeq = request.existingMessageForVariant?.seq ?: ((allMsgs.maxOfOrNull { it.seq } ?: 0) + 1)
        val assistantId = request.existingMessageForVariant?.id ?: UUID.randomUUID().toString()
        val preservedVariants = preservePreviousVariants(conv, request.existingMessageForVariant)
        val assistantStart = ChatMessage(
            id = assistantId,
            conversationId = convId,
            role = "assistant",
            content = request.continuePrefixText.orEmpty(),
            reasoning = "",
            model = request.modelId,
            createdAt = request.existingMessageForVariant?.createdAt ?: System.currentTimeMillis(),
            seq = nextSeq,
            rpChoices = request.existingMessageForVariant?.rpChoices.orEmpty(),
            novelChoices = request.existingMessageForVariant?.novelChoices.orEmpty(),
            status = "streaming",
            variants = preservedVariants,
            activeVariantIndex = request.existingMessageForVariant?.activeVariantIndex ?: 0,
        )

        val streamLock = Any()
        val contentBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        var bestPartialContent = ""
        var bestPartialReasoning = ""
        var latestUsage: MessageUsage? = null
        var reasoningStartMs: Long? = null
        var reasoningDurationMs: Long? = null
        var lastUiEmitMs = 0L
        var lastDbFlushMs = System.currentTimeMillis()
        var lastKeepAlivePushMs = 0L
        var lastKeepAliveChars = 0
        var uiDirty = false
        var activeResolvedPrefill: String? = null

        fun mergeWithPrefill(raw: String): String {
            val pre = activeResolvedPrefill
            if (pre.isNullOrEmpty()) return raw
            if (raw.isEmpty()) return pre
            return if (raw.startsWith(pre)) raw else "$pre$raw"
        }

        fun buildSnapshotLocked(status: String = "streaming", error: String? = null): ChatMessage {
            val baseUsage = latestUsage
            val effectiveUsage = if (reasoningDurationMs != null) {
                (baseUsage ?: MessageUsage()).copy(reasoningDurationMs = reasoningDurationMs)
            } else {
                baseUsage
            }
            val merged = if (isContinue) {
                combineContinueText(request.continuePrefixText, contentBuf.toString())
            } else {
                mergeWithPrefill(contentBuf.toString())
            }
            return assistantStart.copy(
                content = merged,
                reasoning = reasoningBuf.toString(),
                reasoningDurationMs = reasoningDurationMs,
                usage = effectiveUsage,
                status = status,
                error = error,
            )
        }

        fun buildSnapshot(status: String = "streaming", error: String? = null): ChatMessage =
            synchronized(streamLock) { buildSnapshotLocked(status, error) }

        fun publishSessionSnapshot(
            snap: ChatMessage,
            isStreaming: Boolean = true,
            clearHint: Boolean = false,
            error: String? = snap.error,
        ) {
            val chars = snap.content.length + snap.reasoning.length
            _activeSessions.update { map ->
                val prevHint = if (clearHint) null else map[convId]?.statusHint
                map + (
                    convId to ActiveStreamSession(
                        conversationId = convId,
                        streamingMessage = snap,
                        charsGenerated = chars,
                        isStreaming = isStreaming,
                        statusHint = prevHint,
                        error = error,
                    )
                    )
            }
        }

        fun maybeUpdateKeepAliveProgress(charCount: Int) {
            val now = System.currentTimeMillis()
            if (now - lastKeepAlivePushMs >= KEEP_ALIVE_THROTTLE_MS ||
                charCount - lastKeepAliveChars >= KEEP_ALIVE_CHAR_STEP
            ) {
                lastKeepAlivePushMs = now
                lastKeepAliveChars = charCount
                keepAlive.updateProgress(taskId, keepAliveLabel, charCount)
            }
        }

        fun onChunkAppended(deltaText: String? = null, deltaReasoning: String? = null) {
            var charTotal = 0
            val immediateSnap: ChatMessage? = synchronized(streamLock) {
                val now = System.currentTimeMillis()
                if (!deltaReasoning.isNullOrEmpty()) {
                    if (reasoningStartMs == null) reasoningStartMs = now
                    reasoningBuf.append(deltaReasoning)
                }
                if (!deltaText.isNullOrEmpty()) {
                    if (reasoningStartMs != null && reasoningDurationMs == null) {
                        reasoningDurationMs = (now - reasoningStartMs!!).coerceAtLeast(1000L)
                    }
                    contentBuf.append(deltaText)
                }
                charTotal = contentBuf.length + reasoningBuf.length
                if (lastUiEmitMs == 0L || now - lastUiEmitMs >= UI_EMIT_INTERVAL_MS) {
                    lastUiEmitMs = now
                    uiDirty = false
                    buildSnapshotLocked()
                } else {
                    uiDirty = true
                    null
                }
            }
            maybeUpdateKeepAliveProgress(charTotal)
            if (immediateSnap != null) {
                publishSessionSnapshot(immediateSnap, clearHint = true)
            }
        }

        fun onRetryScheduled(attempt: Int, maxRetries: Int) {
            val hint = retryHintFormatter(attempt, maxRetries)
            _activeSessions.update { map ->
                val existing = map[convId] ?: return@update map
                map + (convId to existing.copy(statusHint = hint))
            }
        }

        fun onResetBuffersForRetry() {
            val resetSnap = synchronized(streamLock) {
                if (contentBuf.length > bestPartialContent.length) bestPartialContent = contentBuf.toString()
                if (reasoningBuf.length > bestPartialReasoning.length) bestPartialReasoning = reasoningBuf.toString()
                contentBuf.clear()
                reasoningBuf.clear()
                latestUsage = null
                lastUiEmitMs = System.currentTimeMillis()
                uiDirty = false
                buildSnapshotLocked()
            }
            publishSessionSnapshot(resetSnap)
        }

        publishSessionSnapshot(assistantStart, isStreaming = true)
        persistenceGateway.upsertMessageSnapshot(assistantStart)

        val flushTickerJob = scope.launch {
            while (isActive) {
                delay(UI_EMIT_INTERVAL_MS)
                val pendingSnap: ChatMessage? = synchronized(streamLock) {
                    if (!uiDirty) {
                        null
                    } else {
                        lastUiEmitMs = System.currentTimeMillis()
                        uiDirty = false
                        buildSnapshotLocked()
                    }
                }
                if (pendingSnap != null) {
                    publishSessionSnapshot(pendingSnap)
                    maybeUpdateKeepAliveProgress(pendingSnap.content.length + pendingSnap.reasoning.length)
                }
                val now = System.currentTimeMillis()
                if (now - lastDbFlushMs >= DB_SNAPSHOT_INTERVAL_MS) {
                    lastDbFlushMs = now
                    val checkpoint = buildSnapshot()
                    if (checkpoint.content.isNotEmpty() || checkpoint.reasoning.isNotEmpty()) {
                        persistenceGateway.upsertMessageSnapshot(checkpoint)
                    }
                }
            }
        }

        try {
            var activeConv = conv
            var assembledPayload: AssembledChatPayload? = null
            var wireRequest = if (request.overrideMessages != null) {
                OnlineChatCompletionRequestDto(
                    group_id = request.groupId,
                    model = request.modelId,
                    messages = request.overrideMessages,
                    stream = true,
                    temperature = activeConv.params.temperature,
                    top_p = activeConv.params.topP,
                    max_tokens = activeConv.params.maxTokens,
                    reasoning_effort = if (activeConv.params.enableReasoning) activeConv.params.reasoningEffort else null,
                    frequency_penalty = activeConv.params.frequencyPenalty,
                    presence_penalty = activeConv.params.presencePenalty,
                    repetition_penalty = activeConv.params.repetitionPenalty,
                    top_k = activeConv.params.topK,
                    min_p = activeConv.params.minP,
                    stop = activeConv.params.stopSequences.map { it.trim() }.filter { it.isNotEmpty() }.take(4).ifEmpty { null },
                    enablePromptCaching = activeConv.params.enablePromptCaching,
                    promptCacheKey = if (activeConv.params.enablePromptCaching) "gugachat_${activeConv.id.take(24)}" else null,
                    assistantPrefill = null,
                )
            } else {
                val rawPrefill = if (!isContinue) activeConv.params.assistantPrefill.trim() else ""
                val prepared = contextGateway.assembleContext(
                    conv = activeConv,
                    msgs = allMsgs,
                    groupId = request.groupId,
                    modelId = request.modelId,
                    modelMetaOverride = request.modelMetaOverride,
                    assistantPrefill = rawPrefill,
                    draftText = request.draftText,
                    mutedRecallKeys = request.mutedRecallKeys,
                )
                assembledPayload = prepared
                activeResolvedPrefill = prepared.resolvedAssistantPrefill
                _lastWaterlineContext.value = prepared.builtContext
                prepared.request
            }

            try {
                completionGateway.streamOnce(
                    request = wireRequest,
                    onTextDelta = { delta -> onChunkAppended(deltaText = delta) },
                    onReasoningDelta = { r -> onChunkAppended(deltaReasoning = r) },
                    onUsageSet = { u -> synchronized(streamLock) { latestUsage = u } },
                    onRetryAttempt = { attempt, maxRetries, _ -> onRetryScheduled(attempt, maxRetries) },
                    onResetForRetry = ::onResetBuffersForRetry,
                )
            } catch (e: ApiException) {
                val isBufEmpty = synchronized(streamLock) { contentBuf.isEmpty() }
                if (e.isContextTooLong && isBufEmpty && request.overrideMessages == null) {
                    toastShower(compactingNoticeProvider(), false)
                    val outcome = contextGateway.compactOnOverflow(
                        conv = activeConv,
                        messages = allMsgs,
                        fallbackGroupId = request.groupId,
                        fallbackModel = request.modelId,
                    ) ?: throw e
                    val freshEpisodes = persistenceGateway.saveCompactionOutcome(activeConv, outcome)
                    activeConv = activeConv.copy(
                        archivedSummary = outcome.archivedSummary,
                        summarizedUntilSeq = outcome.summarizedUntilSeq,
                        updatedAt = System.currentTimeMillis(),
                    )
                    scheduleVectorIndexing(activeConv.id)
                    val rawPrefill = if (!isContinue) activeConv.params.assistantPrefill.trim() else ""
                    val retryPrepared = contextGateway.assembleContext(
                        conv = activeConv,
                        msgs = allMsgs,
                        groupId = request.groupId,
                        modelId = request.modelId,
                        modelMetaOverride = request.modelMetaOverride,
                        episodesOverride = freshEpisodes,
                        assistantPrefill = rawPrefill,
                        draftText = request.draftText,
                        mutedRecallKeys = request.mutedRecallKeys,
                    )
                    assembledPayload = retryPrepared
                    activeResolvedPrefill = retryPrepared.resolvedAssistantPrefill
                    _lastWaterlineContext.value = retryPrepared.builtContext
                    wireRequest = retryPrepared.request
                    completionGateway.streamOnce(
                        request = wireRequest,
                        onTextDelta = { delta -> onChunkAppended(deltaText = delta) },
                        onReasoningDelta = { r -> onChunkAppended(deltaReasoning = r) },
                        onUsageSet = { u -> synchronized(streamLock) { latestUsage = u } },
                        onRetryAttempt = { attempt, maxRetries, _ -> onRetryScheduled(attempt, maxRetries) },
                        onResetForRetry = ::onResetBuffersForRetry,
                    )
                } else {
                    throw e
                }
            }

            flushTickerJob.cancel()
            val (finalRawContent, finalReasoning, finalUsage) = synchronized(streamLock) {
                if (reasoningStartMs != null && reasoningDurationMs == null) {
                    reasoningDurationMs = (System.currentTimeMillis() - reasoningStartMs!!).coerceAtLeast(1000L)
                }
                val mergedUsage = if (reasoningDurationMs != null) {
                    (latestUsage ?: MessageUsage()).copy(reasoningDurationMs = reasoningDurationMs)
                } else {
                    latestUsage
                }
                val mergedContent = if (isContinue) contentBuf.toString() else mergeWithPrefill(contentBuf.toString())
                Triple(mergedContent, reasoningBuf.toString(), mergedUsage)
            }

            val tracking = assembledPayload?.tracking
            val updatedCal = if (finalUsage != null && tracking != null) {
                val calibratedConv = contextGateway.updateTokenCalibration(activeConv, tracking, finalUsage)
                activeConv = calibratedConv
                calibratedConv.tokenCalibration
            } else {
                null
            }

            val bannerPrefix = endingBannerProvider()
            val (committedConv, committedMsg) = persistenceGateway.finalizeTurnTransaction(
                convId = convId,
                fallbackConv = activeConv,
                assistantTemplate = assistantStart,
                rawContent = finalRawContent,
                reasoning = finalReasoning,
                usage = finalUsage,
                continuePrefixText = request.continuePrefixText,
                worldbookResult = assembledPayload?.worldbookResult,
                updatedTokenCalibration = updatedCal,
                onEndingUnlockedToast = { msg -> toastShower("$bannerPrefix: $msg", false) },
            )
            _lastCommittedMessages.update { it + (convId to committedMsg) }
            clearTransientMutes()
            schedulePostTurnMaintenance(committedConv)
            if (!isAppForeground()) {
                completionNotifier(convId, committedConv, committedMsg)
            }
        } catch (_: CancellationException) {
            flushTickerJob.cancel()
            withContext(NonCancellable) {
                val isBufEmpty = synchronized(streamLock) {
                    if (contentBuf.isEmpty() && bestPartialContent.isNotEmpty()) contentBuf.append(bestPartialContent)
                    if (reasoningBuf.isEmpty() && bestPartialReasoning.isNotEmpty()) reasoningBuf.append(bestPartialReasoning)
                    contentBuf.isEmpty()
                }
                val aborted = if (isContinue && request.existingMessageForVariant != null && isBufEmpty) {
                    request.existingMessageForVariant
                } else {
                    buildSnapshot(status = "aborted")
                }
                _lastCommittedMessages.update { it + (convId to aborted) }
                persistenceGateway.upsertMessageSnapshot(aborted)
            }
        } catch (e: Throwable) {
            flushTickerJob.cancel()
            val isBufEmpty = synchronized(streamLock) {
                if (contentBuf.isEmpty() && bestPartialContent.isNotEmpty()) contentBuf.append(bestPartialContent)
                if (reasoningBuf.isEmpty() && bestPartialReasoning.isNotEmpty()) reasoningBuf.append(bestPartialReasoning)
                contentBuf.isEmpty()
            }
            val failed = if (isContinue && request.existingMessageForVariant != null && isBufEmpty) {
                request.existingMessageForVariant
            } else {
                buildSnapshot(status = "error", error = e.message ?: "Generation failed")
            }
            _lastCommittedMessages.update { it + (convId to failed) }
            persistenceGateway.upsertMessageSnapshot(failed)
            toastShower(e.message ?: "Generation failed", true)
        } finally {
            flushTickerJob.cancel()
        }
    }

    private fun preservePreviousVariants(
        conv: ChatConversation,
        existingMessage: ChatMessage?,
    ): List<MessageVariant> {
        if (existingMessage == null) return emptyList()
        if (existingMessage.variants.isNotEmpty()) return existingMessage.variants
        if (existingMessage.content.isBlank()) return emptyList()
        return listOf(
            MessageVariant(
                id = UUID.randomUUID().toString(),
                content = existingMessage.content,
                reasoning = existingMessage.reasoning,
                createdAt = existingMessage.createdAt,
                usage = existingMessage.usage,
                model = existingMessage.model,
                rpStateSnapshot = conv.rp?.state,
                rpCastSnapshot = conv.rp?.cast,
                rpChoices = existingMessage.rpChoices,
                novelStatusSnapshot = conv.novel?.currentStatus,
                novelChoices = existingMessage.novelChoices,
            ),
        )
    }

    companion object {
        private const val UI_EMIT_INTERVAL_MS = 35L
        private const val DB_SNAPSHOT_INTERVAL_MS = 1000L
        private const val KEEP_ALIVE_THROTTLE_MS = 500L
        private const val KEEP_ALIVE_CHAR_STEP = 120

        fun combineContinueText(prefix: String?, streamed: String): String {
            val cleanPrefix = prefix?.trimEnd().orEmpty()
            val cleanStream = streamed.trimStart()
            return when {
                cleanPrefix.isEmpty() -> streamed
                cleanStream.isEmpty() -> cleanPrefix
                else -> "$cleanPrefix\n\n$cleanStream"
            }
        }
    }
}

private class RoomStreamPersistenceGateway(
    private val database: AppDatabase,
) : StreamPersistenceGateway {
    private val chatDao = database.chatDao()

    override suspend fun getMessages(convId: String): List<ChatMessage> =
        chatDao.getMessagesForConversation(convId).map { it.toDomain() }

    override suspend fun upsertMessageSnapshot(msg: ChatMessage) {
        chatDao.upsertMessage(msg.toEntity())
    }

    override suspend fun saveCompactionOutcome(
        conv: ChatConversation,
        outcome: CompactionOutcome,
    ): List<MemoryEpisode> {
        val updatedConv = conv.copy(
            archivedSummary = outcome.archivedSummary,
            summarizedUntilSeq = outcome.summarizedUntilSeq,
            updatedAt = System.currentTimeMillis(),
        )
        database.withTransaction {
            chatDao.upsertConversation(updatedConv.toEntity())
            if (outcome.newEpisodes.isNotEmpty()) {
                chatDao.upsertMemoryEpisodes(outcome.newEpisodes.map { it.toEntity() })
            } else {
                outcome.newEpisode?.let { chatDao.upsertMemoryEpisode(it.toEntity()) }
            }
        }
        return chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
    }

    override suspend fun finalizeTurnTransaction(
        convId: String,
        fallbackConv: ChatConversation,
        assistantTemplate: ChatMessage,
        rawContent: String,
        reasoning: String,
        usage: MessageUsage?,
        continuePrefixText: String?,
        worldbookResult: WorldbookEvalResult?,
        updatedTokenCalibration: Double?,
        onEndingUnlockedToast: ((String) -> Unit)?,
    ): Pair<ChatConversation, ChatMessage> {
        var unlockedBannerText: String? = null
        val result = database.withTransaction {
            // Read FRESH conversation & lore strictly scoped by convId to eliminate cross-session bleed!
            val freshDbConv = chatDao.getConversationById(convId)?.toDomain() ?: fallbackConv
            val calibratedConv = if (updatedTokenCalibration != null) {
                freshDbConv.copy(tokenCalibration = updatedTokenCalibration)
            } else {
                freshDbConv
            }
            var nextConv = if (worldbookResult != null) {
                calibratedConv.copy(
                    loreTimedStatesJson = ChatContextAssembler.encodeLoreTimedStates(worldbookResult.updatedTimedStates),
                )
            } else {
                calibratedConv
            }

            var finalContent = rawContent
            var rpChoices: List<RpChoice> = emptyList()
            var novelChoices: List<String> = emptyList()
            var updatedVariants = assistantTemplate.variants
            val now = System.currentTimeMillis()

            if (nextConv.mode == ConversationMode.novel && nextConv.novel != null) {
                val existingLore = chatDao.getNovelLoreForConversation(convId).map { it.toDomain() }
                val outcome = NovelEngine.parseNovelTurnResponse(
                    rawText = rawContent,
                    conversationId = convId,
                    turnSeq = assistantTemplate.seq,
                    existingLore = existingLore,
                )
                finalContent = if (continuePrefixText != null) {
                    ChatStreamManager.combineContinueText(continuePrefixText, outcome.cleanProse)
                } else {
                    outcome.cleanProse
                }
                novelChoices = if (continuePrefixText != null && outcome.choices.isEmpty()) {
                    assistantTemplate.novelChoices
                } else {
                    outcome.choices
                }
                if (outcome.loreUpserts.isNotEmpty()) {
                    chatDao.upsertNovelLoreList(outcome.loreUpserts.map { it.toEntity() })
                }
                val nextStatus = outcome.statusHud ?: nextConv.novel.currentStatus
                var novelCfg = nextConv.novel.copy(
                    currentStatus = nextStatus,
                    pendingChoices = novelChoices,
                )
                val currentChapters = chatDao.getNovelChaptersForConversation(convId).map { it.toDomain() }
                val activeChapter = currentChapters.firstOrNull { it.id == novelCfg.activeChapterId }
                    ?: currentChapters.maxByOrNull { it.order }
                if (activeChapter != null && outcome.chapterEndSummary != null && novelCfg.autoAdvanceChapter) {
                    chatDao.upsertNovelChapter(
                        activeChapter.copy(
                            status = "done",
                            summary = outcome.chapterEndSummary,
                            endSeq = assistantTemplate.seq,
                            updatedAt = now,
                        ).toEntity(),
                    )
                    val nextOrder = (currentChapters.maxOfOrNull { it.order } ?: 0) + 1
                    val nextChap = NovelChapter(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        order = nextOrder,
                        title = outcome.suggestedNextChapterTitle ?: "第${nextOrder}章",
                        status = "writing",
                        startSeq = assistantTemplate.seq + 1,
                        createdAt = now,
                        updatedAt = now,
                    )
                    chatDao.upsertNovelChapter(nextChap.toEntity())
                    novelCfg = novelCfg.copy(activeChapterId = nextChap.id)
                }
                nextConv = nextConv.copy(novel = novelCfg)
                val newVariant = MessageVariant(
                    id = UUID.randomUUID().toString(),
                    content = finalContent,
                    reasoning = reasoning,
                    createdAt = now,
                    usage = usage,
                    model = assistantTemplate.model,
                    novelStatusSnapshot = nextStatus,
                    novelChoices = novelChoices,
                )
                updatedVariants = assistantTemplate.variants + newVariant
            } else if (nextConv.mode == ConversationMode.rp && nextConv.rp != null) {
                val rpCfg = nextConv.rp
                val outcome = RpEngine.parseRpTurnResponse(
                    rawText = rawContent,
                    currentState = rpCfg.state,
                    currentCast = rpCfg.cast,
                    conversationId = convId,
                    turnSeq = assistantTemplate.seq,
                )
                finalContent = if (continuePrefixText != null) {
                    ChatStreamManager.combineContinueText(continuePrefixText, outcome.cleanNarrative)
                } else {
                    outcome.cleanNarrative
                }
                rpChoices = if (continuePrefixText != null && outcome.choices.isEmpty()) {
                    assistantTemplate.rpChoices
                } else {
                    outcome.choices
                }
                if (outcome.ledgerUpserts.isNotEmpty()) {
                    chatDao.upsertMemoryLedgerList(outcome.ledgerUpserts.map { it.toEntity() })
                }
                val finalState = if (outcome.unlockedEnding != null &&
                    outcome.updatedState.unlockedEndings.none {
                        it.title.equals(outcome.unlockedEnding.title, ignoreCase = true) &&
                            it.code.equals(outcome.unlockedEnding.code, ignoreCase = true)
                    }
                ) {
                    outcome.updatedState.copy(unlockedEndings = outcome.updatedState.unlockedEndings + outcome.unlockedEnding)
                } else {
                    outcome.updatedState
                }
                val nextRp = rpCfg.copy(
                    state = finalState,
                    cast = outcome.updatedCast,
                    pendingChoices = rpChoices,
                )
                nextConv = nextConv.copy(rp = nextRp)
                val resolvedSpeaker = outcome.speakerName
                    ?: (if (continuePrefixText != null) assistantTemplate.activeVariant()?.rpSpeakerName else null)
                    ?: outcome.updatedCast.firstOrNull { !it.isPlayer && it.inScene }?.name
                    ?: outcome.updatedCast.firstOrNull { !it.isPlayer }?.name
                val newVariant = MessageVariant(
                    id = UUID.randomUUID().toString(),
                    content = finalContent,
                    reasoning = reasoning,
                    createdAt = now,
                    usage = usage,
                    model = assistantTemplate.model,
                    rpStateSnapshot = finalState,
                    rpCastSnapshot = outcome.updatedCast,
                    rpChoices = rpChoices,
                    rpDeltas = outcome.turnDeltas,
                    rpSpeakerName = resolvedSpeaker,
                    rpEndingUnlocked = outcome.unlockedEnding,
                )
                updatedVariants = assistantTemplate.variants + newVariant
                outcome.unlockedEnding?.let { ending ->
                    unlockedBannerText = "[${ending.code}] ${ending.title}"
                }
            }

            val finalMsg = assistantTemplate.copy(
                content = finalContent,
                reasoning = reasoning,
                usage = usage,
                rpChoices = rpChoices,
                novelChoices = novelChoices,
                status = "done",
                tokenEstimate = ContextBuilder.estimateMessageTokens(finalContent),
                variants = updatedVariants,
                activeVariantIndex = if (updatedVariants.isNotEmpty()) updatedVariants.lastIndex else 0,
            )
            val committedConv = nextConv.copy(updatedAt = now)
            chatDao.upsertMessage(finalMsg.toEntity())
            chatDao.upsertConversation(committedConv.toEntity())

            if (committedConv.mode == ConversationMode.novel) {
                val chapters = chatDao.getNovelChaptersForConversation(convId).map { it.toDomain() }
                if (chapters.isNotEmpty()) {
                    val msgs = chatDao.getMessagesForConversation(convId).map { it.toDomain() }
                    val recalculated = NovelEngine.recalculateChapterWordCounts(chapters, msgs)
                    val changed = recalculated.filterIndexed { idx, ch -> ch != chapters[idx] }
                    if (changed.isNotEmpty()) {
                        chatDao.upsertNovelChapters(changed.map { it.copy(updatedAt = now).toEntity() })
                    }
                }
            }
            committedConv to finalMsg
        }
        unlockedBannerText?.let { onEndingUnlockedToast?.invoke(it) }
        return result
    }

    override suspend fun abortOrphanStreamingMessages() {
        chatDao.abortOrphanStreamingMessages()
    }
}

private class BackgroundTurnMaintenance(
    private val scope: CoroutineScope,
    private val database: AppDatabase,
    private val onlineChatApi: OnlineChatApi,
    private val settingsState: StateFlow<AppSettings>,
    private val retrievalCoordinator: RetrievalCoordinator,
) {
    private val chatDao = database.chatDao()
    private val userMemoryDao = database.userMemoryDao()

    fun scheduleIfEnabled(conv: ChatConversation) {
        val convId = conv.id
        if (conv.mode == ConversationMode.novel || conv.mode == ConversationMode.rp) {
            scheduleVectorIndexing(convId)
        }
        val gid = conv.groupId ?: return
        val model = conv.model?.takeIf { it.isNotBlank() } ?: return
        if (conv.mode == ConversationMode.chat && conv.params.enableAutoMemory) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(convId).map { it.toDomain() })
                    if (msgs.size < 4 || msgs.size % 4 != 0) return@launch
                    val existing = userMemoryDao.getAll().map { it.toDomain() }
                    val prompt = MemoryExtractor.buildMemoryExtractionMessages(existing, msgs)
                    val res = onlineChatApi.completeOnce(
                        OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.2, max_tokens = 500),
                    )
                    val extracted = MemoryExtractor.parseExtractedMemories(res.content)
                    if (extracted.isNotEmpty()) {
                        val (merged, added) = MemoryExtractor.mergeExtractedMemories(existing, extracted)
                        if (added > 0) {
                            userMemoryDao.upsertAll(merged.map { it.toEntity() })
                            scheduleVectorIndexing(convId)
                        }
                    }
                }
            }
        }
        if (conv.params.enableAutoConsolidate && activeConsolidations.add(convId)) {
            scope.launch(Dispatchers.IO) {
                try {
                    runCatching {
                        val msgs = TimelineReconciler.activeMessages(chatDao.getMessagesForConversation(convId).map { it.toDomain() })
                        val eps = chatDao.getMemoryEpisodesForConversation(convId).map { it.toDomain() }
                        val batch = EpisodeConsolidator.findNextMessagesBatch(msgs, eps)
                        var changed = false
                        if (batch != null) {
                            val prompt = EpisodeConsolidator.buildEpisodeConsolidationPrompt(conv.title, batch)
                            val res = onlineChatApi.completeOnce(
                                OnlineChatCompletionRequestDto(gid, model, prompt, stream = false, temperature = 0.25, max_tokens = 600),
                            )
                            val newEp = EpisodeConsolidator.parseConsolidationResponse(
                                raw = res.content,
                                conversationId = convId,
                                fromSeq = batch.fromSeq,
                                toSeq = batch.toSeq,
                                level = 1,
                            )
                            chatDao.upsertMemoryEpisode(newEp.toEntity())
                            changed = true
                        }
                        val updatedEps = if (changed) {
                            chatDao.getMemoryEpisodesForConversation(convId).map { it.toDomain() }
                        } else {
                            eps
                        }
                        val arcCandidates = EpisodeConsolidator.findEpisodesForArcRollup(updatedEps)
                        if (arcCandidates.isNotEmpty()) {
                            val rollupPrompt = EpisodeConsolidator.buildRollupPrompt(arcCandidates, targetLevel = 2)
                            val res = onlineChatApi.completeOnce(
                                OnlineChatCompletionRequestDto(gid, model, rollupPrompt, stream = false, temperature = 0.25, max_tokens = 650),
                            )
                            val arcEp = EpisodeConsolidator.parseConsolidationResponse(
                                raw = res.content,
                                conversationId = convId,
                                fromSeq = arcCandidates.first().fromSeq,
                                toSeq = arcCandidates.last().toSeq,
                                level = 2,
                            )
                            chatDao.upsertMemoryEpisode(arcEp.toEntity())
                            changed = true
                        }
                        if (changed) {
                            scheduleVectorIndexing(convId)
                        }
                    }
                } finally {
                    activeConsolidations.remove(convId)
                }
            }
        }
    }

    private fun scheduleVectorIndexing(convId: String) {
        val settings = settingsState.value.retrievalSettings
        if (settings.mode == com.gugachat.app.domain.chat.RetrievalMode.LEXICAL_ONLY) return
        scope.launch(Dispatchers.IO) {
            retrievalCoordinator.scheduleConversationIndexing(
                scope = scope,
                conversationId = convId,
                lore = chatDao.getNovelLoreForConversation(convId).map { it.toDomain() },
                ledger = chatDao.getMemoryLedgerForConversation(convId).map { it.toDomain() },
                episodes = chatDao.getMemoryEpisodesForConversation(convId).map { it.toDomain() },
                userMemories = userMemoryDao.getAll().map { it.toDomain() },
            )
        }
    }

    companion object {
        private val activeConsolidations: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
