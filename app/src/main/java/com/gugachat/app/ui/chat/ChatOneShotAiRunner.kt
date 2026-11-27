package com.gugachat.app.ui.chat

import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppStrings
import com.gugachat.app.data.api.CompletionResult
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.RpConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Narrow internal contract shared between [ChatViewModel] and its domain-specific delegates so
 * delegates never reach back into Compose locals or hold duplicate repository references.
 */
internal interface ChatViewModelHost {
    val container: AppContainer
    val scope: CoroutineScope
    val database: AppDatabase
        get() = container.database
    val uiFlow: MutableStateFlow<ChatUiState>
    val activeConvIdFlow: MutableStateFlow<String?>
    val oneShotRunner: ChatOneShotAiRunner

    fun strings(): AppStrings
    fun updateDraftText(text: String)
    fun setDraftNovelKind(kind: com.gugachat.app.domain.chat.NovelInputKind)
    fun sendMessage(overrideText: String? = null)
    fun launchCompletionForConversation(
        conv: ChatConversation,
        existingMessageForVariant: ChatMessage? = null,
        continuePrefixText: String? = null,
        overrideMessages: List<WireChatMessageDto>? = null,
    )
    fun scheduleWaterline(debounceMs: Long = 80L)
    fun scheduleIndexing(convIdOverride: String? = null)
    suspend fun syncNovelChapterWordCounts(convId: String)

    fun upsertNovelLore(category: String, title: String, keywords: String, content: String, alwaysActive: Boolean)
    fun toggleNovelLoreAlwaysActive(id: String)
    fun updateNovelLore(entry: NovelLoreEntry)
    fun mountWorldbook(worldbookId: String, convId: String? = null)
    fun updateRpConfig(transform: (RpConfig) -> RpConfig)
    fun refreshOpeningGreetingForActiveCard(
        conv: ChatConversation,
        card: com.gugachat.app.data.local.CharacterCardEntity?,
        persona: com.gugachat.app.data.local.PersonaEntity?,
        selectedVariantIndex: Int? = null,
    ) {
        scope.launch {
            refreshOpeningGreetingSuspend(conv, card, persona, selectedVariantIndex)
        }
    }
    suspend fun refreshOpeningGreetingSuspend(
        conv: ChatConversation,
        card: com.gugachat.app.data.local.CharacterCardEntity?,
        persona: com.gugachat.app.data.local.PersonaEntity?,
        selectedVariantIndex: Int? = null,
    )
}

/**
 * Centralizes non-streaming [OnlineChatApi.completeOnce] utility calls (rephrase, polish,
 * outline chat, lore/memory extraction, choice refresh, AI genesis) with consistent
 * channel/model validation, busy flag lifecycle, and localized error toasts.
 */
internal class ChatOneShotAiRunner(private val host: ChatViewModelHost) {
    fun resolveGroupAndModel(
        conv: ChatConversation? = host.uiFlow.value.activeConversation,
        toastIfMissing: Boolean = true,
    ): Pair<Long, String>? {
        val st = host.uiFlow.value
        val gid = conv?.groupId ?: st.selectedGroupId
        val model = conv?.model?.takeIf { it.isNotBlank() } ?: st.selectedModelId?.takeIf { it.isNotBlank() }
        if (gid == null || model == null) {
            if (toastIfMissing) {
                host.container.toastController.show(host.strings().chat.selectGroupAndModelFirst, isError = true)
            }
            return null
        }
        return gid to model
    }

    suspend fun completeOnce(
        groupId: Long,
        modelId: String,
        messages: List<WireChatMessageDto>,
        temperature: Double = 0.7,
        maxTokens: Int = 1200,
    ): CompletionResult = host.container.onlineChatApi.completeOnce(
        OnlineChatCompletionRequestDto(
            group_id = groupId,
            model = modelId,
            messages = messages,
            stream = false,
            temperature = temperature,
            max_tokens = maxTokens,
        ),
    )

    fun launchWithGroupModelAndFlag(
        alreadyBusy: (ChatUiState) -> Boolean = { false },
        requireActiveConv: Boolean = true,
        setBusy: (ChatUiState, Boolean) -> ChatUiState,
        fallbackError: (() -> String)? = null,
        block: suspend (conv: ChatConversation?, gid: Long, modelId: String) -> Unit,
    ) {
        val st = host.uiFlow.value
        if (alreadyBusy(st)) return
        val conv = st.activeConversation
        if (requireActiveConv && conv == null) return
        val (gid, modelId) = resolveGroupAndModel(conv) ?: return
        host.scope.launch {
            host.uiFlow.update { setBusy(it, true) }
            try {
                block(conv, gid, modelId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.message ?: fallbackError?.invoke() ?: host.strings().common.unknownError
                host.container.toastController.show(msg, isError = true)
            } finally {
                host.uiFlow.update { setBusy(it, false) }
            }
        }
    }
}
