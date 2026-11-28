package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.domain.chat.BuiltChatContext
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.CompactionOutcome
import com.gugachat.app.domain.chat.ContextWaterlineSummary
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.LoreTimedEntryState
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MessageUsage
import com.gugachat.app.domain.chat.WorldbookEvalResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ChatStreamManagerTest {

    private class FakeKeepAliveGateway : StreamKeepAliveGateway {
        data class AcquireRecord(val taskId: String, val label: String, val onStopRequested: (() -> Unit)?)
        data class ProgressRecord(val taskId: String, val label: String, val charCount: Int)

        val acquired = CopyOnWriteArrayList<AcquireRecord>()
        val progresses = CopyOnWriteArrayList<ProgressRecord>()
        val released = CopyOnWriteArrayList<String>()

        override fun acquireWork(taskId: String, label: String, onStopRequested: (() -> Unit)?) {
            acquired += AcquireRecord(taskId, label, onStopRequested)
        }

        override fun updateProgress(taskId: String, label: String, charCount: Int) {
            progresses += ProgressRecord(taskId, label, charCount)
        }

        override fun releaseWork(taskId: String) {
            released += taskId
        }
    }

    private class FakeContextGateway : StreamContextGateway {
        var assembleHandler: (ChatConversation) -> AssembledChatPayload = { conv ->
            AssembledChatPayload(
                request = OnlineChatCompletionRequestDto(
                    group_id = conv.groupId ?: 1L,
                    model = conv.model ?: "test-model",
                    messages = listOf(WireChatMessageDto(role = "user", content = JsonPrimitive("hi"))),
                    stream = true,
                    promptCacheKey = conv.id,
                ),
                builtContext = BuiltChatContext(emptyList(), ContextWaterlineSummary()),
                worldbookResult = null,
                resolvedAssistantPrefill = null,
                tracking = SentPayloadTracking(
                    calibrationKey = "default|test",
                    protocol = ApiProtocol.OPENAI_CHAT,
                    estimatedRawTokens = 42,
                    hasUndecayedImages = false,
                ),
                v4Assets = V4ConversationAssets(),
            )
        }

        var compactHandler: (ChatConversation) -> CompactionOutcome? = { null }
        var calibrationHandler: (ChatConversation, SentPayloadTracking, MessageUsage) -> ChatConversation =
            { conv, _, _ -> conv }

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
        ): AssembledChatPayload = assembleHandler(conv)

        override suspend fun compactOnOverflow(
            conv: ChatConversation,
            messages: List<ChatMessage>,
            fallbackGroupId: Long,
            fallbackModel: String,
        ): CompactionOutcome? = compactHandler(conv)

        override suspend fun updateTokenCalibration(
            conv: ChatConversation,
            tracking: SentPayloadTracking,
            usage: MessageUsage,
        ): ChatConversation = calibrationHandler(conv, tracking, usage)
    }

    private class FakePersistenceGateway : StreamPersistenceGateway {
        val conversations = ConcurrentHashMap<String, ChatConversation>()
        val messagesByConv = ConcurrentHashMap<String, MutableList<ChatMessage>>()
        val snapshots = CopyOnWriteArrayList<ChatMessage>()
        val finalizedCalls = CopyOnWriteArrayList<FinalizedCall>()
        val orphanAbortCalls = AtomicInteger(0)
        val compactionOutcomesSaved = CopyOnWriteArrayList<Pair<String, CompactionOutcome>>()

        data class FinalizedCall(
            val convId: String,
            val fallbackConv: ChatConversation,
            val rawContent: String,
            val reasoning: String,
            val usage: MessageUsage?,
            val worldbookResult: WorldbookEvalResult?,
            val updatedTokenCalibration: Double?,
        )

        override suspend fun getMessages(convId: String): List<ChatMessage> =
            synchronized(messagesByConv) { messagesByConv[convId]?.toList().orEmpty() }

        override suspend fun upsertMessageSnapshot(msg: ChatMessage) {
            snapshots += msg
            synchronized(messagesByConv) {
                val list = messagesByConv.getOrPut(msg.conversationId) { mutableListOf() }
                val idx = list.indexOfFirst { it.id == msg.id }
                if (idx >= 0) list[idx] = msg else list += msg
            }
        }

        override suspend fun saveCompactionOutcome(
            conv: ChatConversation,
            outcome: CompactionOutcome,
        ): List<MemoryEpisode> {
            compactionOutcomesSaved += conv.id to outcome
            val updated = conv.copy(
                archivedSummary = outcome.archivedSummary,
                summarizedUntilSeq = outcome.summarizedUntilSeq,
            )
            conversations[conv.id] = updated
            return outcome.newEpisodes
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
            finalizedCalls += FinalizedCall(
                convId = convId,
                fallbackConv = fallbackConv,
                rawContent = rawContent,
                reasoning = reasoning,
                usage = usage,
                worldbookResult = worldbookResult,
                updatedTokenCalibration = updatedTokenCalibration,
            )
            val committedConv = (conversations[convId] ?: fallbackConv).copy(
                tokenCalibration = updatedTokenCalibration ?: fallbackConv.tokenCalibration,
            )
            val committedMsg = assistantTemplate.copy(
                content = rawContent,
                reasoning = reasoning,
                usage = usage,
                status = "done",
            )
            conversations[convId] = committedConv
            upsertMessageSnapshot(committedMsg)
            return committedConv to committedMsg
        }

        override suspend fun abortOrphanStreamingMessages() {
            orphanAbortCalls.incrementAndGet()
        }
    }

    private fun sampleConv(
        id: String,
        title: String = "Conv $id",
    ): ChatConversation = ChatConversation(
        id = id,
        title = title,
        groupId = 1L,
        model = "gpt-4o-mini",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private suspend fun awaitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                delay(10L)
            }
        }
    }

    @Test
    fun concurrentStreamsAcrossDistinctConversationsTrackIndependently() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val keepAlive = FakeKeepAliveGateway()
            val ctxGateway = FakeContextGateway()
            val persistence = FakePersistenceGateway()

            val convAStarted = CompletableDeferred<Unit>()
            val convBStarted = CompletableDeferred<Unit>()
            val finishConvA = CompletableDeferred<Unit>()
            val finishConvB = CompletableDeferred<Unit>()

            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    val promptKey = request.promptCacheKey.orEmpty()
                    if (promptKey.endsWith("conv_a")) {
                        onTextDelta("Alpha chunk")
                        convAStarted.complete(Unit)
                        finishConvA.await()
                    } else {
                        onTextDelta("Beta longer response chunk")
                        convBStarted.complete(Unit)
                        finishConvB.await()
                    }
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = keepAlive,
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_a", "Story A"), 1L, "m1")))
            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_b", "Story B"), 1L, "m1")))

            convAStarted.await()
            convBStarted.await()
            awaitUntil {
                manager.activeSessions.value["conv_a"]?.streamingMessage?.content == "Alpha chunk" &&
                    manager.activeSessions.value["conv_b"]?.streamingMessage?.content == "Beta longer response chunk"
            }

            val sessions = manager.activeSessions.value
            assertEquals(setOf("conv_a", "conv_b"), sessions.keys)
            assertEquals(11, sessions["conv_a"]?.charsGenerated)
            assertEquals(26, sessions["conv_b"]?.charsGenerated)
            assertTrue(manager.isStreaming("conv_a"))
            assertTrue(manager.isStreaming("conv_b"))
            assertEquals(
                setOf("stream:conv_a", "stream:conv_b"),
                keepAlive.acquired.map { it.taskId }.toSet(),
            )

            finishConvA.complete(Unit)
            finishConvB.complete(Unit)

            awaitUntil {
                manager.activeSessions.value.isEmpty() &&
                    keepAlive.released.containsAll(listOf("stream:conv_a", "stream:conv_b"))
            }
            assertEquals("Alpha chunk", manager.lastCommittedMessages.value["conv_a"]?.content)
            assertEquals("Beta longer response chunk", manager.lastCommittedMessages.value["conv_b"]?.content)
            assertEquals("done", manager.lastCommittedMessages.value["conv_a"]?.status)
            assertEquals("done", manager.lastCommittedMessages.value["conv_b"]?.status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateStartStreamOnSameConversationIsRejectedWhileActive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val keepAlive = FakeKeepAliveGateway()
            val ctxGateway = FakeContextGateway()
            val persistence = FakePersistenceGateway()
            val enteredStream = CompletableDeferred<Unit>()
            val allowFinish = CompletableDeferred<Unit>()
            val streamCalls = AtomicInteger(0)

            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    val callIdx = streamCalls.incrementAndGet()
                    if (callIdx == 1) {
                        onTextDelta("first")
                        enteredStream.complete(Unit)
                        allowFinish.await()
                    } else {
                        onTextDelta("second")
                    }
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = keepAlive,
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
            )

            val req = StreamLaunchRequest(sampleConv("conv_dup"), 1L, "m1")
            assertTrue(manager.startStream(req))
            enteredStream.await()

            assertFalse(manager.startStream(req))
            assertEquals(1, streamCalls.get())

            allowFinish.complete(Unit)
            awaitUntil { !manager.isStreaming("conv_dup") }

            assertTrue(manager.startStream(req))
            awaitUntil { manager.lastCommittedMessages.value["conv_dup"]?.content == "second" }
            assertEquals(2, streamCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelStreamAndWaitSavesAbortedSnapshotAndReleasesKeepAlive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val keepAlive = FakeKeepAliveGateway()
            val ctxGateway = FakeContextGateway()
            val persistence = FakePersistenceGateway()
            val chunkDelivered = CompletableDeferred<Unit>()

            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    onTextDelta("partial reply")
                    chunkDelivered.complete(Unit)
                    delay(30_000L)
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = keepAlive,
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_abort"), 1L, "m1")))
            chunkDelivered.await()

            manager.cancelStreamAndWait("conv_abort")

            assertFalse(manager.isStreaming("conv_abort"))
            assertTrue(manager.activeSessions.value.isEmpty())
            assertEquals("aborted", manager.lastCommittedMessages.value["conv_abort"]?.status)
            assertEquals("partial reply", manager.lastCommittedMessages.value["conv_abort"]?.content)
            assertTrue(persistence.snapshots.any { it.conversationId == "conv_abort" && it.status == "aborted" && it.content == "partial reply" })
            assertEquals(listOf("stream:conv_abort"), keepAlive.released)
            assertTrue(keepAlive.progresses.any { it.taskId == "stream:conv_abort" && it.charCount >= 13 })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun keepAliveReleasedAndErrorMessagePersistedOnStreamFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val keepAlive = FakeKeepAliveGateway()
            val ctxGateway = FakeContextGateway()
            val persistence = FakePersistenceGateway()
            val toastedErrors = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())

            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    onTextDelta("before boom")
                    error("upstream exploded")
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = keepAlive,
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
                toastShower = { msg, err -> toastedErrors += msg to err },
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_err"), 1L, "m1")))
            awaitUntil {
                manager.lastCommittedMessages.value["conv_err"]?.status == "error" &&
                    keepAlive.released.contains("stream:conv_err")
            }

            val errMsg = manager.lastCommittedMessages.value["conv_err"]
            assertNotNull(errMsg)
            assertEquals("error", errMsg?.status)
            assertEquals("upstream exploded", errMsg?.error)
            assertEquals("before boom", errMsg?.content)
            assertTrue(toastedErrors.any { it.second && it.first == "upstream exploded" })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recoverOrphanStreamsDelegatesToPersistenceGateway() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val persistence = FakePersistenceGateway()
            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = FakeKeepAliveGateway(),
                contextGateway = FakeContextGateway(),
                completionGateway = object : StreamCompletionGateway {
                    override suspend fun streamOnce(
                        request: OnlineChatCompletionRequestDto,
                        onTextDelta: (String) -> Unit,
                        onReasoningDelta: (String) -> Unit,
                        onUsageSet: (MessageUsage) -> Unit,
                        onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                        onResetForRetry: () -> Unit,
                    ) = Unit
                },
                persistenceGateway = persistence,
            )

            manager.recoverOrphanStreams()
            assertEquals(1, persistence.orphanAbortCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun zeroCrossSessionBleedAcrossConcurrentCommitsAndWorldbookStates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val keepAlive = FakeKeepAliveGateway()
            val persistence = FakePersistenceGateway()
            val ctxGateway = FakeContextGateway().apply {
                assembleHandler = { conv ->
                    val timedStateKey = "lore_${conv.id}"
                    val turn = if (conv.id == "conv_one") 3 else 7
                    AssembledChatPayload(
                        request = OnlineChatCompletionRequestDto(
                            group_id = 1L,
                            model = "m1",
                            messages = listOf(WireChatMessageDto(role = "user", content = JsonPrimitive(conv.id))),
                            promptCacheKey = conv.id,
                        ),
                        builtContext = BuiltChatContext(emptyList(), ContextWaterlineSummary()),
                        worldbookResult = WorldbookEvalResult(
                            updatedTimedStates = mapOf(timedStateKey to LoreTimedEntryState(lastTriggeredTurn = turn)),
                        ),
                        resolvedAssistantPrefill = null,
                        tracking = SentPayloadTracking(
                            calibrationKey = conv.id,
                            protocol = ApiProtocol.OPENAI_CHAT,
                            estimatedRawTokens = if (conv.id == "conv_one") 100 else 200,
                            hasUndecayedImages = false,
                        ),
                        v4Assets = V4ConversationAssets(),
                    )
                }
                calibrationHandler = { conv, _, _ ->
                    conv.copy(tokenCalibration = if (conv.id == "conv_one") 1.15 else 0.85)
                }
            }

            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    val id = request.promptCacheKey.orEmpty()
                    onTextDelta("reply:$id")
                    onUsageSet(MessageUsage(promptTokens = if (id == "conv_one") 115 else 170))
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = keepAlive,
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_one"), 1L, "m1")))
            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_two"), 1L, "m1")))

            awaitUntil { persistence.finalizedCalls.size == 2 && manager.activeSessions.value.isEmpty() }

            val callOne = persistence.finalizedCalls.first { it.convId == "conv_one" }
            val callTwo = persistence.finalizedCalls.first { it.convId == "conv_two" }

            assertEquals("reply:conv_one", callOne.rawContent)
            assertEquals(setOf("lore_conv_one"), callOne.worldbookResult?.updatedTimedStates?.keys)
            assertEquals(1.15, callOne.updatedTokenCalibration ?: 0.0, 0.0001)

            assertEquals("reply:conv_two", callTwo.rawContent)
            assertEquals(setOf("lore_conv_two"), callTwo.worldbookResult?.updatedTimedStates?.keys)
            assertEquals(0.85, callTwo.updatedTokenCalibration ?: 0.0, 0.0001)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun backgroundCompletionNotificationFiresOnlyWhenAppNotForeground() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var isForeground = true
            val notifications = CopyOnWriteArrayList<String>()
            val persistence = FakePersistenceGateway()
            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    onTextDelta("done text")
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = FakeKeepAliveGateway(),
                contextGateway = FakeContextGateway(),
                completionGateway = completionGateway,
                persistenceGateway = persistence,
                completionNotifier = { convId, _, _ -> notifications += convId },
                isAppForeground = { isForeground },
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_fg"), 1L, "m1")))
            awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_fg") }
            assertTrue(notifications.isEmpty())

            isForeground = false
            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_bg"), 1L, "m1")))
            awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_bg") }
            assertEquals(listOf("conv_bg"), notifications.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoCompactionRetriesOnceOnContextLengthOverflowWhenBufferEmpty() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val persistence = FakePersistenceGateway()
            val indexedConvs = CopyOnWriteArrayList<String>()
            val attempts = AtomicInteger(0)
            val ctxGateway = FakeContextGateway().apply {
                compactHandler = { conv ->
                    CompactionOutcome(
                        archivedSummary = "compacted summary for ${conv.id}",
                        summarizedUntilSeq = 4,
                        newEpisode = null,
                    )
                }
            }
            val completionGateway = object : StreamCompletionGateway {
                override suspend fun streamOnce(
                    request: OnlineChatCompletionRequestDto,
                    onTextDelta: (String) -> Unit,
                    onReasoningDelta: (String) -> Unit,
                    onUsageSet: (MessageUsage) -> Unit,
                    onRetryAttempt: (attempt: Int, maxRetries: Int, error: Throwable) -> Unit,
                    onResetForRetry: () -> Unit,
                ) {
                    if (attempts.incrementAndGet() == 1) {
                        throw ApiException(
                            status = 400,
                            code = "context_length_exceeded",
                            message = "This model's maximum context length is exceeded",
                        )
                    }
                    onTextDelta("recovered after compaction")
                }
            }

            val manager = ChatStreamManager(
                scope = scope,
                keepAlive = FakeKeepAliveGateway(),
                contextGateway = ctxGateway,
                completionGateway = completionGateway,
                persistenceGateway = persistence,
                scheduleVectorIndexing = { indexedConvs += it },
            )

            assertTrue(manager.startStream(StreamLaunchRequest(sampleConv("conv_compact"), 1L, "m1")))
            awaitUntil {
                manager.lastCommittedMessages.value["conv_compact"]?.status == "done" &&
                    manager.activeSessions.value.isEmpty()
            }

            assertEquals(2, attempts.get())
            assertEquals(1, persistence.compactionOutcomesSaved.size)
            assertEquals("recovered after compaction", manager.lastCommittedMessages.value["conv_compact"]?.content)
            assertEquals(listOf("conv_compact"), indexedConvs.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoContinue_triggersWhenShort_stripsPrematureRpTags_capsAtTwoPasses_andStopsOnLowDelta() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // 1. Disabled by default: never fires when autoContinueShortReply = false
            run {
                val persistence = FakePersistenceGateway()
                val calls = AtomicInteger(0)
                val completion = object : StreamCompletionGateway {
                    override suspend fun streamOnce(
                        request: OnlineChatCompletionRequestDto,
                        onTextDelta: (String) -> Unit,
                        onReasoningDelta: (String) -> Unit,
                        onUsageSet: (MessageUsage) -> Unit,
                        onRetryAttempt: (Int, Int, Throwable) -> Unit,
                        onResetForRetry: () -> Unit,
                    ) {
                        calls.incrementAndGet()
                        onTextDelta("短回复。")
                    }
                }
                val manager = ChatStreamManager(
                    scope = scope,
                    keepAlive = FakeKeepAliveGateway(),
                    contextGateway = FakeContextGateway(),
                    completionGateway = completion,
                    persistenceGateway = persistence,
                )
                val conv = sampleConv("conv_off").copy(
                    params = ChatParams(minOutputChars = 300, autoContinueShortReply = false),
                )
                assertTrue(manager.startStream(StreamLaunchRequest(conv, 1L, "m1")))
                awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_off") && manager.activeSessions.value.isEmpty() }
                assertEquals(1, calls.get())
            }

            // 2. RP mode: pass 1 ends prematurely with <rp_state>, pass 2 strips premature tag and reaches >= 85%
            run {
                val persistence = FakePersistenceGateway()
                val recordedRequests = CopyOnWriteArrayList<OnlineChatCompletionRequestDto>()
                val observedHints = CopyOnWriteArrayList<String>()
                val completion = object : StreamCompletionGateway {
                    override suspend fun streamOnce(
                        request: OnlineChatCompletionRequestDto,
                        onTextDelta: (String) -> Unit,
                        onReasoningDelta: (String) -> Unit,
                        onUsageSet: (MessageUsage) -> Unit,
                        onRetryAttempt: (Int, Int, Throwable) -> Unit,
                        onResetForRetry: () -> Unit,
                    ) {
                        recordedRequests += request
                        when (recordedRequests.size) {
                            1 -> {
                                onTextDelta("雨夜微冷，酒馆木门吱呀作响。<rp_state>{\"scene\":\"酒馆\"}</rp_state>")
                                onUsageSet(MessageUsage(promptTokens = 100, completionTokens = 25, totalTokens = 125))
                            }
                            else -> {
                                onTextDelta("桌上的烛火摇曳不定，映照着斑驳的墙壁与行人的剪影。".repeat(12) + "\n<rp_state>{\"scene\":\"酒馆内\"}</rp_state>")
                                onUsageSet(MessageUsage(promptTokens = 140, completionTokens = 180, totalTokens = 320))
                            }
                        }
                    }
                }
                val manager = ChatStreamManager(
                    scope = scope,
                    keepAlive = FakeKeepAliveGateway(),
                    contextGateway = FakeContextGateway(),
                    completionGateway = completion,
                    persistenceGateway = persistence,
                    autoContinueHintFormatter = { actual, target, pass, max ->
                        val h = "auto:$actual/$target:$pass/$max"
                        observedHints += h
                        h
                    },
                )
                val rpConv = sampleConv("conv_rp_ac").copy(
                    mode = ConversationMode.rp,
                    params = ChatParams(minOutputChars = 200, autoContinueShortReply = true),
                )
                assertTrue(manager.startStream(StreamLaunchRequest(rpConv, 1L, "m1")))
                awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_rp_ac") && manager.activeSessions.value.isEmpty() }

                assertEquals(2, recordedRequests.size)
                assertEquals(1, observedHints.size)
                assertTrue(observedHints.first().contains(":1/2"))
                val finalRaw = persistence.finalizedCalls.last().rawContent
                assertFalse("Premature pass-1 <rp_state> must be stripped before pass 2", finalRaw.contains("\"scene\":\"酒馆\""))
                assertTrue("Final pass-2 <rp_state> must remain intact", finalRaw.contains("\"scene\":\"酒馆内\""))
                assertEquals(205, persistence.finalizedCalls.last().usage?.completionTokens)
            }

            // 3. Caps at 2 auto-continue passes (3 total calls) even when still below threshold
            run {
                val persistence = FakePersistenceGateway()
                val calls = AtomicInteger(0)
                val completion = object : StreamCompletionGateway {
                    override suspend fun streamOnce(
                        request: OnlineChatCompletionRequestDto,
                        onTextDelta: (String) -> Unit,
                        onReasoningDelta: (String) -> Unit,
                        onUsageSet: (MessageUsage) -> Unit,
                        onRetryAttempt: (Int, Int, Throwable) -> Unit,
                        onResetForRetry: () -> Unit,
                    ) {
                        calls.incrementAndGet()
                        onTextDelta("这是一段用于测试自动续写次数上限的五十个中文字符片段内容，确保每次增量超过四十字但总长度仍未达到八百字的目标下限。")
                    }
                }
                val manager = ChatStreamManager(
                    scope = scope,
                    keepAlive = FakeKeepAliveGateway(),
                    contextGateway = FakeContextGateway(),
                    completionGateway = completion,
                    persistenceGateway = persistence,
                )
                val conv = sampleConv("conv_cap2").copy(
                    params = ChatParams(minOutputChars = 800, autoContinueShortReply = true),
                )
                assertTrue(manager.startStream(StreamLaunchRequest(conv, 1L, "m1")))
                awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_cap2") && manager.activeSessions.value.isEmpty() }
                assertEquals(3, calls.get()) // 1 initial + 2 auto-continue passes max
            }

            // 4. Stops early after pass 2 when continuation delta < 40 effective units
            run {
                val persistence = FakePersistenceGateway()
                val calls = AtomicInteger(0)
                val completion = object : StreamCompletionGateway {
                    override suspend fun streamOnce(
                        request: OnlineChatCompletionRequestDto,
                        onTextDelta: (String) -> Unit,
                        onReasoningDelta: (String) -> Unit,
                        onUsageSet: (MessageUsage) -> Unit,
                        onRetryAttempt: (Int, Int, Throwable) -> Unit,
                        onResetForRetry: () -> Unit,
                    ) {
                        when (calls.incrementAndGet()) {
                            1 -> onTextDelta("第一段较短的内容。")
                            else -> onTextDelta("补充十个字。") // < 40 effective units -> stop early
                        }
                    }
                }
                val manager = ChatStreamManager(
                    scope = scope,
                    keepAlive = FakeKeepAliveGateway(),
                    contextGateway = FakeContextGateway(),
                    completionGateway = completion,
                    persistenceGateway = persistence,
                )
                val conv = sampleConv("conv_low_delta").copy(
                    params = ChatParams(minOutputChars = 800, autoContinueShortReply = true),
                )
                assertTrue(manager.startStream(StreamLaunchRequest(conv, 1L, "m1")))
                awaitUntil { manager.lastCommittedMessages.value.containsKey("conv_low_delta") && manager.activeSessions.value.isEmpty() }
                assertEquals(2, calls.get()) // stopped after pass 1 of auto-continue
            }
        } finally {
            scope.cancel()
        }
    }
}
