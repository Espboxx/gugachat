package com.gugachat.app.ui.chat

import androidx.room.withTransaction
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.data.chat.ConversationForkUseCase
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.OutputLengthPolicy
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpPlayStyle
import com.gugachat.app.domain.chat.RpPresets
import com.gugachat.app.domain.chat.RpRegexEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpVisualTheme
import com.gugachat.app.domain.chat.TimelineReconciler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Encapsulates RP Studio & Theater gameplay actions: campaign launch, theater/play-style
 * configuration, continue/impersonate/choices/dice, regex & status widgets, frontend action
 * bridge, NPC roster CRUD, and save-slot rewind/branching.
 */
internal class RpStudioDelegate(private val host: ChatViewModelHost) {
    private val chatDao get() = host.database.chatDao()
    private val assetDao get() = host.database.assetDao()

    fun createRpSessionFromPreset(
        presetId: String,
        customTitle: String? = null,
        playStyle: RpPlayStyle? = null,
        playerName: String? = null,
        playerPersona: String? = null,
    ) {
        host.scope.launch {
            val preset = RpPresets.findById(presetId)
            val st = host.uiFlow.value
            val convId = withContext(Dispatchers.IO) {
                host.container.campaignLaunchUseCase.launchFromPreset(
                    presetTemplate = preset,
                    customTitle = customTitle,
                    playStyleOverride = playStyle,
                    playerNameOverride = playerName,
                    playerPersonaOverride = playerPersona,
                    groupId = st.selectedGroupId,
                    modelId = st.selectedModelId,
                )
            }
            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            host.activeConvIdFlow.value = convId
        }
    }

    fun createRpSessionFromCharacterCard(cardId: String) {
        host.scope.launch {
            val card = assetDao.getCharacterCardById(cardId) ?: return@launch
            val st = host.uiFlow.value
            val convId = withContext(Dispatchers.IO) {
                host.container.campaignLaunchUseCase.launchFromCard(
                    card = card,
                    selectedGreetingIndex = 0,
                    personaId = null,
                    worldbookIds = listOfNotNull(card.primaryWorldbookId),
                    presetId = null,
                    groupId = st.selectedGroupId,
                    modelId = st.selectedModelId,
                )
            }
            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            host.activeConvIdFlow.value = convId
        }
    }

    fun toggleRpTheaterMode() {
        updateRpConfig { it.copy(theaterMode = !it.theaterMode) }
    }

    fun setRpTheaterMode(enabled: Boolean) {
        updateRpConfig { it.copy(theaterMode = enabled) }
    }

    fun setRpPlayStyle(style: RpPlayStyle) {
        updateRpConfig { it.copy(playStyle = style) }
    }

    fun continueLastAssistantMessage(instruction: String = "") {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        if (host.container.chatStreamManager.isStreaming(conv.id)) return
        val (groupId, modelId) = host.oneShotRunner.resolveGroupAndModel(conv) ?: return
        if (!host.tryAcquireConversationMutation(conv.id)) return

        host.scope.launch {
            try {
                if (host.container.chatStreamManager.isStreaming(conv.id)) return@launch
                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val allMsgs = chatDao.getMessagesForConversation(freshConv.id).map { it.toDomain() }
                val activeMsgs = TimelineReconciler.activeMessages(allMsgs)
                val lastAssistant = TimelineReconciler.latestTipAssistant(activeMsgs)
                    ?.takeIf { it.status != "error" }
                    ?: return@launch

                val rp = freshConv.rp
                val novel = freshConv.novel
                if (freshConv.mode == ConversationMode.rp && rp != null) {
                    val loreList = chatDao.getNovelLoreForConversation(freshConv.id).map { it.toDomain() }
                    val loreMatch = NovelEngine.matchNovelLore(
                        lore = loreList,
                        recentMessages = activeMsgs,
                        draftText = instruction,
                        chapterOutline = "",
                        loreTokenBudget = rp.loreTokenBudget,
                        activeTipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0,
                        presentEntities = rp.cast.filter { it.inScene }.map { it.name },
                        sceneTitle = rp.state.scene,
                    )
                    val wireDtos = RpEngine.buildContinueMessages(
                        rp = rp,
                        recentMessages = activeMsgs,
                        instruction = instruction,
                        constantLore = loreMatch.constant,
                        triggeredLore = loreMatch.triggered,
                    )
                    host.launchCompletionForConversation(
                        conv = freshConv.copy(groupId = groupId, model = modelId),
                        existingMessageForVariant = lastAssistant,
                        continuePrefixText = lastAssistant.content,
                        overrideMessages = wireDtos,
                    )
                } else if (freshConv.mode == ConversationMode.novel && novel != null) {
                    val chapters = chatDao.getNovelChaptersForConversation(freshConv.id).map { it.toDomain() }
                    val activeChapter = chapters.firstOrNull { it.id == novel.activeChapterId } ?: chapters.maxByOrNull { it.order }
                    val loreList = chatDao.getNovelLoreForConversation(freshConv.id).map { it.toDomain() }
                    val loreMatch = NovelEngine.matchNovelLore(
                        lore = loreList,
                        recentMessages = activeMsgs,
                        draftText = instruction,
                        chapterOutline = activeChapter?.outline.orEmpty(),
                        loreTokenBudget = novel.loreTokenBudget,
                        activeTipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0,
                        presentEntities = novel.currentStatus?.present.orEmpty(),
                        sceneTitle = novel.currentStatus?.location,
                    )
                    val wireDtos = NovelEngine.buildContinueMessages(
                        novel = novel,
                        activeChapter = activeChapter,
                        recentMessages = activeMsgs,
                        constantLore = loreMatch.constant,
                        triggeredLore = loreMatch.triggered,
                        existingLoreTitles = loreList.map { it.title },
                        previousChapters = chapters,
                        instruction = instruction,
                    )
                    host.launchCompletionForConversation(
                        conv = freshConv.copy(groupId = groupId, model = modelId),
                        existingMessageForVariant = lastAssistant,
                        continuePrefixText = NovelExport.stripInternalTags(lastAssistant.content),
                        overrideMessages = wireDtos,
                    )
                } else if (freshConv.mode == ConversationMode.chat) {
                    val lastUserText = activeMsgs.lastOrNull { it.role == "user" }?.content
                    val isEnglish = OutputLengthPolicy.isEnglishPreference(
                        recentUserText = lastUserText,
                        appLanguageIsEnglish = host.container.settingsState.value.language == AppLanguage.EN,
                    )
                    val wireDtos = OutputLengthPolicy.buildStandardChatContinueMessages(
                        conv = freshConv,
                        activeMessages = activeMsgs,
                        instruction = instruction,
                        isEnglish = isEnglish,
                    )
                    host.launchCompletionForConversation(
                        conv = freshConv.copy(groupId = groupId, model = modelId),
                        existingMessageForVariant = lastAssistant,
                        continuePrefixText = lastAssistant.content,
                        overrideMessages = wireDtos,
                    )
                }
            } finally {
                host.releaseConversationMutation(conv.id)
            }
        }
    }

    fun impersonatePlayerDraft() {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isStreaming || it.isImpersonating },
            setBusy = { st, busy -> st.copy(isImpersonating = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val rp = conv.rp ?: return@launchWithGroupModelAndFlag
            val prompt = RpEngine.buildImpersonateMessages(rp, host.uiFlow.value.messages)
            val res = host.oneShotRunner.completeOnce(
                groupId = gid,
                modelId = model,
                messages = prompt,
                temperature = conv.params.temperature.coerceIn(0.6, 1.0),
                maxTokens = 320,
            )
            val draft = RpEngine.stripRpInternalTags(res.content).trim()
            if (draft.isNotEmpty()) {
                host.uiFlow.update { it.copy(draftText = draft) }
                host.scheduleWaterline()
            }
        }
    }

    fun refreshRpChoices() {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isStreaming || it.isRefreshingChoices },
            setBusy = { st, busy -> st.copy(isRefreshingChoices = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val rp = conv.rp ?: return@launchWithGroupModelAndFlag
            val prompt = RpEngine.buildRefreshChoicesMessages(rp, host.uiFlow.value.messages)
            val res = host.oneShotRunner.completeOnce(
                groupId = gid,
                modelId = model,
                messages = prompt,
                temperature = 0.85,
                maxTokens = 700,
            )
            val newChoices = RpEngine.parseChoicesOnly(res.content)
            if (newChoices.isEmpty()) {
                host.container.toastController.show(host.strings().common.unknownError, isError = true)
                return@launchWithGroupModelAndFlag
            }
            host.container.database.withTransaction {
                val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
                val lastAssistant = TimelineReconciler.activeMessages(msgs).lastOrNull { it.role == "assistant" }
                if (lastAssistant != null) {
                    val updatedVariants = if (lastAssistant.variants.isNotEmpty()) {
                        val activeIdx = lastAssistant.activeVariantIndex.coerceIn(0, lastAssistant.variants.lastIndex)
                        lastAssistant.variants.mapIndexed { idx, v ->
                            if (idx == activeIdx) v.copy(rpChoices = newChoices) else v
                        }
                    } else {
                        lastAssistant.variants
                    }
                    chatDao.upsertMessage(
                        lastAssistant.copy(rpChoices = newChoices, variants = updatedVariants).toEntity(),
                    )
                }
                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshRp = freshConv.rp ?: rp
                chatDao.upsertConversation(
                    freshConv.copy(
                        rp = freshRp.copy(pendingChoices = newChoices),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
            }
        }
    }

    fun rollDiceForDraft(expression: String, skill: String?, difficulty: Int?, mode: String) {
        val rp = host.uiFlow.value.activeConversation?.rp
        val rollUnder = expression.contains("d100", ignoreCase = true) ||
            (rp?.systemRules?.contains("d100", ignoreCase = true) == true)
        val roll = RpEngine.rollDice(
            expression = expression,
            difficulty = difficulty,
            skill = skill?.takeIf { it.isNotBlank() },
            mode = mode,
            rollUnder = rollUnder,
        )
        host.uiFlow.update { it.copy(draftDiceRoll = roll) }
    }

    fun clearDraftDiceRoll() {
        host.uiFlow.update { it.copy(draftDiceRoll = null) }
    }

    fun triggerRpChoice(choice: RpChoice, bypassLock: Boolean = false) {
        val st = host.uiFlow.value
        val conv = st.activeConversation
        if (st.isStreaming || (conv != null && host.container.chatStreamManager.isStreaming(conv.id))) return
        val groupId = st.selectedGroupId
        val modelId = st.selectedModelId
        if (groupId == null || modelId.isNullOrBlank()) {
            host.container.toastController.show(host.strings().chat.selectGroupAndModelFirst, isError = true)
            return
        }
        val rp = conv?.rp
        if (rp != null) {
            val eval = RpEngine.evaluateChoiceCondition(choice, rp)
            if (!eval.unlocked && !bypassLock) {
                val isEn = host.container.settingsState.value.language == AppLanguage.EN
                val reason = (if (isEn) eval.reasonEn else eval.reasonZh).orEmpty()
                val msg = if (reason.isNotBlank()) "${host.strings().rp.choiceLockedLabel}：$reason" else host.strings().rp.choiceLockedLabel
                host.container.toastController.show(msg, isError = true)
                return
            }
        }

        val lockKey = conv?.id ?: ChatViewModel.NEW_CONV_MUTATION_KEY
        if (!host.tryAcquireConversationMutation(lockKey)) return

        val roll = if (!choice.skillCheck.isNullOrBlank() && rp != null) {
            RpEngine.rollForChoice(choice, rp)
        } else {
            null
        }
        val cost = choice.costGold ?: 0

        host.scope.launch {
            try {
                if (conv != null && host.container.chatStreamManager.isStreaming(conv.id)) return@launch
                if (conv != null && rp != null && cost > 0) {
                    val updatedConv = host.container.database.withTransaction {
                        val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                        val freshRp = fresh.rp ?: rp
                        if (freshRp.state.gold >= cost) {
                            val nextRp = freshRp.copy(
                                state = freshRp.state.copy(gold = (freshRp.state.gold - cost).coerceAtLeast(0)),
                            )
                            val nextConv = fresh.copy(rp = nextRp, updatedAt = System.currentTimeMillis())
                            chatDao.upsertConversation(nextConv.toEntity())
                            nextConv
                        } else {
                            null
                        }
                    }
                    if (updatedConv != null) {
                        host.uiFlow.update {
                            it.copy(
                                activeConversation = updatedConv,
                                draftRpKind = choice.inputKind,
                                draftDiceRoll = roll,
                            ).withDerivedBindings()
                        }
                    } else {
                        host.uiFlow.update { it.copy(draftRpKind = choice.inputKind, draftDiceRoll = roll) }
                    }
                } else {
                    host.uiFlow.update { it.copy(draftRpKind = choice.inputKind, draftDiceRoll = roll) }
                }
                host.sendMessageWhileLocked(
                    overrideText = choice.actionText,
                    overrideRpKind = choice.inputKind,
                    overrideDiceRoll = roll,
                )
            } finally {
                host.releaseConversationMutation(lockKey)
            }
        }
    }

    fun updateRpConfig(transform: (RpConfig) -> RpConfig) {
        host.scope.launch {
            val activeId = host.uiFlow.value.activeConversation?.id ?: return@launch
            val conv = chatDao.getConversationById(activeId)?.toDomain()
                ?: host.uiFlow.value.activeConversation ?: return@launch
            val rp = conv.rp ?: return@launch
            val updated = transform(rp)
            if (updated == rp) return@launch
            chatDao.upsertConversation(conv.copy(rp = updated, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun updateRpState(transform: (RpGameState) -> RpGameState) {
        updateRpConfig { cfg -> cfg.copy(state = transform(cfg.state)) }
    }

    fun updateRpVisualTheme(transform: (RpVisualTheme) -> RpVisualTheme) {
        updateRpConfig { cfg -> cfg.copy(visualTheme = transform(cfg.visualTheme)) }
    }

    fun resetRpVisualTheme() {
        updateRpConfig { cfg -> cfg.copy(visualTheme = RpVisualTheme()) }
    }

    fun upsertRpRegexScript(script: RpRegexScript) {
        val cleanName = script.name.trim()
        if (cleanName.isEmpty() || script.findRegex.isBlank()) return
        val normalized = script.copy(
            id = script.id.ifBlank { UUID.randomUUID().toString() },
            name = cleanName,
            flags = script.flags.trim().ifEmpty { "gms" },
            placement = script.placement.trim().lowercase().ifEmpty { "ai_output" },
        )
        updateRpConfig { cfg ->
            val exists = cfg.regexScripts.any { it.id == normalized.id }
            val nextScripts = if (exists) {
                cfg.regexScripts.map { if (it.id == normalized.id) normalized else it }
            } else {
                cfg.regexScripts + normalized
            }
            cfg.copy(regexScripts = nextScripts)
        }
    }

    fun toggleRpRegexScript(scriptId: String) {
        updateRpConfig { cfg ->
            cfg.copy(
                regexScripts = cfg.regexScripts.map { s ->
                    if (s.id == scriptId) s.copy(enabled = !s.enabled) else s
                },
            )
        }
    }

    fun deleteRpRegexScript(scriptId: String) {
        updateRpConfig { cfg ->
            cfg.copy(regexScripts = cfg.regexScripts.filterNot { it.id == scriptId })
        }
    }

    fun applyRpRegexPreset(presetId: String) {
        val preset = RpRegexEngine.findRegexPreset(presetId) ?: return
        updateRpConfig { cfg ->
            val dup = cfg.regexScripts.any {
                it.id == preset.script.id ||
                    it.name.equals(preset.script.name, ignoreCase = true) ||
                    it.name.equals(preset.enName, ignoreCase = true)
            }
            if (dup) {
                cfg.copy(
                    regexScripts = cfg.regexScripts.map { s ->
                        if (s.id == preset.script.id ||
                            s.name.equals(preset.script.name, ignoreCase = true) ||
                            s.name.equals(preset.enName, ignoreCase = true)
                        ) {
                            s.copy(enabled = true)
                        } else {
                            s
                        }
                    },
                )
            } else {
                cfg.copy(regexScripts = cfg.regexScripts + preset.script)
            }
        }
    }

    fun applyRpStatusWidgetTemplate(templateId: String) {
        val tpl = RpRegexEngine.findStatusWidgetTemplateOrNull(templateId) ?: return
        updateRpVisualTheme { theme ->
            theme.copy(
                customStatusHtml = tpl.html,
                showCustomStatusWidget = true,
            )
        }
    }

    fun handleRpFrontendAction(actionText: String) {
        val clean = actionText.trim()
        if (clean.isEmpty()) return
        host.scope.launch { host.sendMessage(overrideText = clean) }
    }

    fun handleRpFrontendFillInput(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        host.updateDraftText(clean)
    }

    fun handleRpFrontendSetFlag(key: String, value: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        val v = value.trim()
        updateRpState { st ->
            val nextFlags = if (v.isEmpty()) st.flags - k else st.flags + (k to v)
            st.copy(flags = nextFlags)
        }
    }

    fun handleRpFrontendAdjustAffinity(npcName: String, delta: Int) {
        val target = npcName.trim()
        if (target.isEmpty() || delta == 0) return
        updateRpConfig { cfg ->
            val matched = cfg.cast.firstOrNull { c ->
                c.name.trim().equals(target, ignoreCase = true) ||
                    c.aliases.any { it.trim().equals(target, ignoreCase = true) }
            } ?: cfg.cast.firstOrNull { c ->
                c.name.contains(target, ignoreCase = true) || target.contains(c.name, ignoreCase = true)
            }
            if (matched == null) {
                cfg
            } else {
                cfg.copy(
                    cast = cfg.cast.map { c ->
                        if (c.id == matched.id) {
                            c.copy(affinity = (c.affinity + delta).coerceIn(-100, 100))
                        } else {
                            c
                        }
                    },
                )
            }
        }
    }

    fun updateRpCharacterAffinity(charId: String, delta: Int) {
        updateRpConfig { cfg ->
            cfg.copy(
                cast = cfg.cast.map { c ->
                    if (c.id == charId) c.copy(affinity = (c.affinity + delta).coerceIn(-100, 100)) else c
                },
            )
        }
    }

    fun upsertRpNpc(character: RpCharacter) {
        val cleanName = character.name.trim()
        if (cleanName.isEmpty()) return
        val normalized = character.copy(
            id = character.id.ifBlank { UUID.randomUUID().toString() },
            name = cleanName,
        )
        updateRpConfig { cfg ->
            val exists = cfg.cast.any { it.id == normalized.id }
            val nextCast = if (exists) {
                cfg.cast.map { if (it.id == normalized.id) normalized else it }
            } else {
                cfg.cast + normalized
            }
            cfg.copy(cast = nextCast)
        }
    }

    fun deleteRpNpc(charId: String) {
        updateRpConfig { cfg ->
            cfg.copy(cast = cfg.cast.filterNot { it.id == charId })
        }
    }

    fun createRpSaveSlot(name: String, note: String = "") {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        if (host.container.chatStreamManager.isStreaming(conv.id)) return
        if (conv.rp == null) return
        host.scope.launch {
            if (host.container.chatStreamManager.isStreaming(conv.id)) return@launch
            val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            val freshRp = freshConv.rp ?: return@launch
            val freshMsgs = chatDao.getMessagesForConversation(conv.id)
                .map { it.toDomain() }
                .filter { it.status != "streaming" }
            val freshLedger = chatDao.getMemoryLedgerForConversation(conv.id).map { it.toDomain() }
            val freshEpisodes = chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
            val slot = RpEngine.createSaveSlot(
                conversationId = conv.id,
                name = name,
                note = note.trim(),
                rp = freshRp,
                messages = freshMsgs,
                ledger = freshLedger,
                episodes = freshEpisodes,
            )
            chatDao.upsertRpSaveSlot(slot.toEntity())
            host.container.toastController.show(host.strings().rp.quickSaved.fmt("name" to slot.name))
        }
    }

    fun overwriteRpSaveSlot(slotId: String) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        if (host.container.chatStreamManager.isStreaming(conv.id)) return
        if (conv.rp == null) return
        val existing = st.rpSaveSlots.firstOrNull { it.id == slotId } ?: return
        host.scope.launch {
            if (host.container.chatStreamManager.isStreaming(conv.id)) return@launch
            val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            val freshRp = freshConv.rp ?: return@launch
            val freshMsgs = chatDao.getMessagesForConversation(conv.id)
                .map { it.toDomain() }
                .filter { it.status != "streaming" }
            val freshLedger = chatDao.getMemoryLedgerForConversation(conv.id).map { it.toDomain() }
            val freshEpisodes = chatDao.getMemoryEpisodesForConversation(conv.id).map { it.toDomain() }
            val fresh = RpEngine.createSaveSlot(
                conversationId = conv.id,
                name = existing.name,
                note = existing.note,
                rp = freshRp,
                messages = freshMsgs,
                ledger = freshLedger,
                episodes = freshEpisodes,
            ).copy(id = existing.id)
            chatDao.upsertRpSaveSlot(fresh.toEntity())
            host.container.toastController.show(host.strings().rp.saveOverwrittenToast.fmt("name" to existing.name))
        }
    }

    fun deleteRpSaveSlot(slotId: String) {
        host.scope.launch {
            chatDao.deleteRpSaveSlot(slotId)
        }
    }

    fun loadRpSaveSlotRewind(slot: RpSaveSlot) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (!host.tryAcquireConversationMutation(conv.id)) return
        host.scope.launch {
            try {
                host.container.chatStreamManager.cancelStreamAndWait(conv.id)
                host.container.chatStreamManager.clearLastCommittedMessage(conv.id)
                host.container.database.withTransaction {
                    val now = System.currentTimeMillis()
                    val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                    val currentLore = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
                    val cleanMsgs = TimelineReconciler.activeMessages(slot.activeMessagesSnapshot)
                        .filter { it.status != "streaming" }
                    val cleanLedger = slot.ledgerItems.filter { !it.stale }
                    val cleanEpisodes = slot.episodes.filter { !it.stale }

                    val reconciledLore = currentLore.map { entry ->
                        when {
                            entry.validFromSeq > slot.messageSeq && !entry.alwaysActive ->
                                entry.copy(stale = true, supersededAtSeq = slot.messageSeq, updatedAt = now)
                            entry.validFromSeq <= slot.messageSeq &&
                                entry.stale &&
                                entry.supersededAtSeq != null &&
                                entry.supersededAtSeq > slot.messageSeq ->
                                entry.copy(stale = false, supersededAtSeq = null, updatedAt = now)
                            else -> entry
                        }
                    }

                    val (nextSummarizedUntil, nextArchivedSummary) = ConversationForkUseCase.computeForkedCompactionBounds(
                        sourceConv = fresh,
                        filteredEpisodes = cleanEpisodes,
                        cutoffSeq = slot.messageSeq,
                    )
                    val nextParams = if (fresh.params.activeWindowStartSeq != null &&
                        fresh.params.activeWindowStartSeq > slot.messageSeq
                    ) {
                        fresh.params.copy(activeWindowStartSeq = null)
                    } else {
                        fresh.params
                    }

                    chatDao.deleteMessagesForConversation(conv.id)
                    chatDao.deleteMemoryLedgerForConversation(conv.id)
                    chatDao.deleteMemoryEpisodesForConversation(conv.id)
                    chatDao.deleteMemoryEmbeddingsForConversation(conv.id)
                    if (cleanMsgs.isNotEmpty()) {
                        chatDao.upsertMessages(cleanMsgs.map { it.copy(conversationId = conv.id).toEntity() })
                    }
                    if (cleanLedger.isNotEmpty()) {
                        chatDao.upsertMemoryLedgerList(cleanLedger.map { it.copy(conversationId = conv.id).toEntity() })
                    }
                    if (cleanEpisodes.isNotEmpty()) {
                        chatDao.upsertMemoryEpisodes(cleanEpisodes.map { it.copy(conversationId = conv.id).toEntity() })
                    }
                    if (reconciledLore.isNotEmpty()) {
                        chatDao.upsertNovelLoreList(reconciledLore.map { it.toEntity() })
                    }
                    chatDao.upsertConversation(
                        fresh.copy(
                            rp = slot.rpConfig,
                            params = nextParams,
                            archivedSummary = nextArchivedSummary,
                            summarizedUntilSeq = nextSummarizedUntil,
                            loreTimedStatesJson = "{}",
                            updatedAt = now,
                        ).toEntity(),
                    )
                }
                host.uiFlow.update {
                    it.copy(
                        draftText = "",
                        draftAttachments = emptyList(),
                        draftDiceRoll = null,
                        mutedRecallKeys = emptySet(),
                    )
                }
                host.scheduleIndexing(conv.id)
                host.container.toastController.show(host.strings().rp.loadedRewindToast.fmt("name" to slot.name))
            } finally {
                host.releaseConversationMutation(conv.id)
            }
        }
    }

    fun branchFromRpSaveSlot(slot: RpSaveSlot) {
        val conv = host.uiFlow.value.activeConversation ?: return
        host.scope.launch {
            val forkedConv = host.container.conversationForkUseCase.branchFromRpSaveSlot(conv, slot)
            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            host.activeConvIdFlow.value = forkedConv.id
            host.container.toastController.show(host.strings().rp.saveBranchedToast.fmt("name" to slot.name))
        }
    }
}
