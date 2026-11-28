package com.gugachat.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.data.chat.CampaignLaunchUseCase
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelDraftOption
import com.gugachat.app.domain.chat.NovelEngine
import com.gugachat.app.domain.chat.NovelExport
import com.gugachat.app.domain.chat.NovelInputKind
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelPresets
import com.gugachat.app.domain.chat.TimelineReconciler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Encapsulates Novel Studio workflows: book wizard creation (manual & AI genesis), chapters,
 * lore extraction/editing, 3-path drafts, outline chat & sync, prose polishing, choices,
 * reader preferences, and TXT/Markdown/EPUB export.
 */
internal class NovelStudioDelegate(private val host: ChatViewModelHost) {
    private val chatDao get() = host.database.chatDao()

    suspend fun syncNovelChapterWordCounts(convId: String) {
        val chapters = chatDao.getNovelChaptersForConversation(convId).map { it.toDomain() }
        if (chapters.isEmpty()) return
        val msgs = chatDao.getMessagesForConversation(convId).map { it.toDomain() }
        val recalculated = NovelEngine.recalculateChapterWordCounts(chapters, msgs)
        val changed = recalculated.filterIndexed { idx, ch -> ch != chapters[idx] }
        if (changed.isNotEmpty()) {
            val now = System.currentTimeMillis()
            chatDao.upsertNovelChapters(changed.map { it.copy(updatedAt = now).toEntity() })
        }
    }

    fun createNovelSession(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
    ) {
        host.scope.launch {
            val preset = NovelPresets.findById(presetId)
            val now = System.currentTimeMillis()
            val convId = UUID.randomUUID().toString()
            val ch1Id = UUID.randomUUID().toString()
            val bookTitle = title.trim().ifBlank { preset.name }
            val resolvedPlayStyle = playStyle.takeIf { it in setOf("hybrid", "author", "interactive") } ?: "hybrid"
            val cfg = NovelConfig(
                title = bookTitle,
                synopsis = customIdea.trim().ifBlank { preset.synopsis },
                genre = preset.genre,
                style = preset.style,
                pov = pov?.takeIf { it in setOf("first", "third_limited", "third_omniscient") } ?: preset.pov,
                playStyle = resolvedPlayStyle,
                antiAiRules = antiAiRules,
                worldview = preset.worldview,
                mainOutline = preset.mainOutline,
                authorNote = preset.authorNote,
                targetWordsPerChapter = (targetWords ?: preset.targetWordsPerChapter).coerceIn(500, 10000),
                enableChoices = resolvedPlayStyle != "author",
                activeChapterId = ch1Id,
            )
            val settings = host.container.settingsState.value
            val conv = ChatConversation(
                id = convId,
                title = "《$bookTitle》",
                mode = ConversationMode.novel,
                groupId = host.uiFlow.value.selectedGroupId,
                model = host.uiFlow.value.selectedModelId,
                params = CampaignLaunchUseCase.defaultChatParams(settings, ConversationMode.novel),
                novel = cfg,
                createdAt = now,
                updatedAt = now,
            )
            val ch1 = NovelChapter(
                id = ch1Id,
                conversationId = convId,
                order = 1,
                title = preset.firstChapterTitle,
                outline = preset.firstChapterOutline,
                status = "writing",
                startSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            val lore = buildDefaultStarterLore(convId, preset.starterLore, now)
            host.container.database.withTransaction {
                chatDao.upsertConversation(conv.toEntity())
                chatDao.upsertNovelChapter(ch1.toEntity())
                if (lore.isNotEmpty()) chatDao.upsertNovelLoreList(lore.map { it.toEntity() })
            }
            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            host.activeConvIdFlow.value = convId
            host.container.toastController.show(host.strings().novel.wizardCreated.fmt("title" to bookTitle))
        }
    }

    fun createNovelSessionWithAi(
        presetId: String,
        title: String,
        customIdea: String,
        pov: String? = null,
        targetWords: Int? = null,
        playStyle: String = "hybrid",
        antiAiRules: Boolean = true,
        onCompleted: () -> Unit = {},
    ) {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isCreatingNovelWithAi },
            requireActiveConv = false,
            setBusy = { st, busy -> st.copy(isCreatingNovelWithAi = busy) },
        ) { _, gid, model ->
            val preset = NovelPresets.findById(presetId)
            val resolvedPov = pov?.takeIf { it in setOf("first", "third_limited", "third_omniscient") } ?: preset.pov
            val resolvedTarget = (targetWords ?: preset.targetWordsPerChapter).coerceIn(500, 10000)
            val resolvedPlayStyle = playStyle.takeIf { it in setOf("hybrid", "author", "interactive") } ?: "hybrid"
            val convId = UUID.randomUUID().toString()
            val ch1Id = UUID.randomUUID().toString()

            val prompt = NovelEngine.buildWizardGenesisMessages(
                preset = preset,
                title = title,
                idea = customIdea,
                pov = resolvedPov,
                targetWordsPerChapter = resolvedTarget,
            )
            val res = host.oneShotRunner.completeOnce(
                groupId = gid,
                modelId = model,
                messages = prompt,
                temperature = 0.8,
                maxTokens = 2400,
            )
            val genesis = NovelEngine.parseWizardGenesisResponse(
                raw = res.content,
                preset = preset,
                fallbackTitle = title,
                conversationId = convId,
            )
            val now = System.currentTimeMillis()
            val bookTitle = genesis?.title ?: title.trim().ifBlank { preset.name }
            val openingProse = genesis?.openingProse.orEmpty().trim()
            val openingWords = NovelExport.countProseWords(openingProse)

            val cfg = NovelConfig(
                title = bookTitle,
                synopsis = genesis?.synopsis ?: customIdea.trim().ifBlank { preset.synopsis },
                genre = preset.genre,
                style = genesis?.style ?: preset.style,
                pov = resolvedPov,
                playStyle = resolvedPlayStyle,
                antiAiRules = antiAiRules,
                worldview = genesis?.worldview ?: preset.worldview,
                mainOutline = genesis?.mainOutline ?: preset.mainOutline,
                authorNote = genesis?.authorNote ?: preset.authorNote,
                targetWordsPerChapter = resolvedTarget,
                enableChoices = resolvedPlayStyle != "author",
                activeChapterId = ch1Id,
            )
            val settings = host.container.settingsState.value
            val conv = ChatConversation(
                id = convId,
                title = "《$bookTitle》",
                mode = ConversationMode.novel,
                groupId = gid,
                model = model,
                params = CampaignLaunchUseCase.defaultChatParams(settings, ConversationMode.novel),
                novel = cfg,
                createdAt = now,
                updatedAt = now,
            )
            val ch1 = NovelChapter(
                id = ch1Id,
                conversationId = convId,
                order = 1,
                title = genesis?.firstChapterTitle ?: preset.firstChapterTitle,
                outline = genesis?.firstChapterOutline ?: preset.firstChapterOutline,
                status = "writing",
                wordCount = openingWords,
                startSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            val lore = genesis?.starterLore?.ifEmpty { null }
                ?: buildDefaultStarterLore(convId, preset.starterLore, now)
            host.container.database.withTransaction {
                chatDao.upsertConversation(conv.toEntity())
                chatDao.upsertNovelChapter(ch1.toEntity())
                if (lore.isNotEmpty()) chatDao.upsertNovelLoreList(lore.map { it.toEntity() })
                if (openingProse.isNotBlank()) {
                    val openingMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = openingProse,
                        model = model,
                        createdAt = now,
                        seq = 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(openingProse),
                        variants = listOf(
                            MessageVariant(
                                id = UUID.randomUUID().toString(),
                                content = openingProse,
                                createdAt = now,
                                model = model,
                            ),
                        ),
                    )
                    chatDao.upsertMessage(openingMsg.toEntity())
                }
            }
            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
            host.activeConvIdFlow.value = convId
            host.container.toastController.show(host.strings().novel.wizardCreated.fmt("title" to bookTitle))
            onCompleted()
        }
    }

    fun updateNovelConfig(transform: (NovelConfig) -> NovelConfig) {
        host.scope.launch {
            val activeId = host.uiFlow.value.activeConversation?.id ?: return@launch
            val conv = chatDao.getConversationById(activeId)?.toDomain()
                ?: host.uiFlow.value.activeConversation ?: return@launch
            val cur = conv.novel ?: return@launch
            val updated = transform(cur)
            if (updated == cur) return@launch
            val nextConvTitle = if (updated.title.trim() != cur.title.trim() && updated.title.isNotBlank()) {
                "《${updated.title.trim()}》"
            } else {
                conv.title
            }
            chatDao.upsertConversation(
                conv.copy(
                    title = nextConvTitle,
                    novel = updated,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            host.scheduleWaterline()
        }
    }

    fun addOrSelectNovelChapter(
        title: String,
        outline: String,
        markPreviousDone: Boolean = false,
        activateImmediately: Boolean = true,
    ) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (conv.novel == null) return
        host.scope.launch {
            addOrSelectNovelChapterSuspend(
                convId = conv.id,
                fallbackConv = conv,
                title = title,
                outline = outline,
                markPreviousDone = markPreviousDone,
                activateImmediately = activateImmediately,
            )
            syncNovelChapterWordCounts(conv.id)
        }
    }

    private suspend fun addOrSelectNovelChapterSuspend(
        convId: String,
        fallbackConv: ChatConversation,
        title: String,
        outline: String,
        markPreviousDone: Boolean,
        activateImmediately: Boolean,
    ) {
        host.container.database.withTransaction {
            val fresh = chatDao.getConversationById(convId)?.toDomain() ?: fallbackConv
            val freshNovel = fresh.novel ?: return@withTransaction
            val now = System.currentTimeMillis()
            val chapters = chatDao.getNovelChaptersForConversation(fresh.id).map { it.toDomain() }
            val nextOrder = (chapters.maxOfOrNull { it.order } ?: 0) + 1
            val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(fresh.id).map { it.toDomain() })

            val activeCh = chapters.firstOrNull { it.id == freshNovel.activeChapterId } ?: chapters.maxByOrNull { it.order }
            if (activeCh != null && (markPreviousDone || activateImmediately)) {
                val closedEnd = if (tip >= activeCh.startSeq) tip else activeCh.endSeq
                chatDao.upsertNovelChapter(
                    activeCh.copy(
                        status = if (markPreviousDone) "done" else activeCh.status,
                        endSeq = closedEnd,
                        updatedAt = now,
                    ).toEntity(),
                )
            }

            val ch = NovelChapter(
                id = UUID.randomUUID().toString(),
                conversationId = fresh.id,
                order = nextOrder,
                title = title.trim().ifBlank { "第${nextOrder}章" },
                outline = outline.trim(),
                status = if (activateImmediately) "writing" else "draft",
                startSeq = tip + 1,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertNovelChapter(ch.toEntity())
            if (activateImmediately) {
                chatDao.upsertConversation(fresh.copy(novel = freshNovel.copy(activeChapterId = ch.id), updatedAt = now).toEntity())
            }
        }
    }

    fun updateNovelChapter(chapter: NovelChapter) {
        val conv = host.uiFlow.value.activeConversation ?: return
        host.scope.launch {
            val normalized = chapter.copy(
                title = chapter.title.trim().ifBlank { "第${chapter.order}章" },
                outline = chapter.outline.trim(),
                summary = chapter.summary.trim(),
                status = chapter.status.takeIf { it in setOf("draft", "writing", "done") } ?: "writing",
                updatedAt = System.currentTimeMillis(),
            )
            chatDao.upsertNovelChapter(normalized.toEntity())
            syncNovelChapterWordCounts(conv.id)
            host.scheduleWaterline()
        }
    }

    fun deleteNovelChapter(chapterId: String) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (conv.novel == null) return
        host.scope.launch {
            val deleted = host.container.database.withTransaction {
                val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshNovel = fresh.novel ?: return@withTransaction false
                val chapters = chatDao.getNovelChaptersForConversation(fresh.id).map { it.toDomain() }
                val now = System.currentTimeMillis()
                val (reindexed, fallbackActiveId) = removeChapterAndCloseGap(
                    chapters = chapters,
                    chapterId = chapterId,
                    currentActiveChapterId = freshNovel.activeChapterId,
                    now = now,
                ) ?: return@withTransaction false
                chatDao.deleteNovelChapter(chapterId)
                if (reindexed.isNotEmpty()) {
                    chatDao.upsertNovelChapters(reindexed.map { it.toEntity() })
                }
                if (freshNovel.activeChapterId == chapterId) {
                    chatDao.upsertConversation(
                        fresh.copy(novel = freshNovel.copy(activeChapterId = fallbackActiveId), updatedAt = now).toEntity(),
                    )
                }
                true
            }
            if (deleted) {
                syncNovelChapterWordCounts(conv.id)
            }
        }
    }

    fun finishActiveChapterAndAdvance(
        nextTitle: String = "",
        nextOutline: String = "",
        autoRecap: Boolean = true,
    ) {
        val conv = host.uiFlow.value.activeConversation ?: return
        val novel = conv.novel ?: return
        val currentChapters = host.uiFlow.value.novelChapters.sortedBy { it.order }
        val activeCh = currentChapters.firstOrNull { it.id == novel.activeChapterId } ?: currentChapters.lastOrNull()
        val currentActiveId = activeCh?.id
        val nextPlannedDraft = if (nextTitle.isBlank() && nextOutline.isBlank() && activeCh != null) {
            currentChapters.firstOrNull { it.order > activeCh.order && it.status == "draft" && it.wordCount == 0 }
        } else {
            null
        }
        if (nextPlannedDraft != null && activeCh != null) {
            host.scope.launch {
                host.container.database.withTransaction {
                    val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                    val freshNovel = fresh.novel ?: return@withTransaction
                    val now = System.currentTimeMillis()
                    val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(fresh.id).map { it.toDomain() })
                    chatDao.upsertNovelChapter(
                        activeCh.copy(
                            status = "done",
                            endSeq = if (tip >= activeCh.startSeq) tip else activeCh.endSeq,
                            updatedAt = now,
                        ).toEntity(),
                    )
                    chatDao.upsertNovelChapter(
                        nextPlannedDraft.copy(
                            status = "writing",
                            startSeq = tip + 1,
                            endSeq = null,
                            updatedAt = now,
                        ).toEntity(),
                    )
                    chatDao.upsertConversation(
                        fresh.copy(novel = freshNovel.copy(activeChapterId = nextPlannedDraft.id), updatedAt = now).toEntity(),
                    )
                }
                syncNovelChapterWordCounts(conv.id)
                if (autoRecap) generateChapterRecap(activeCh.id)
            }
        } else {
            host.scope.launch {
                addOrSelectNovelChapterSuspend(
                    convId = conv.id,
                    fallbackConv = conv,
                    title = nextTitle,
                    outline = nextOutline,
                    markPreviousDone = true,
                    activateImmediately = true,
                )
                syncNovelChapterWordCounts(conv.id)
                if (autoRecap && currentActiveId != null) {
                    generateChapterRecap(currentActiveId)
                }
            }
        }
    }

    fun generateChapterRecap(chapterId: String) {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isGeneratingRecap },
            setBusy = { st, busy -> st.copy(isGeneratingRecap = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val novel = conv.novel ?: return@launchWithGroupModelAndFlag
            val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            val target = chapters.firstOrNull { it.id == chapterId } ?: return@launchWithGroupModelAndFlag
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val section = NovelExport.groupMessagesByChapter(chapters, msgs).firstOrNull { it.chapter?.id == chapterId }
            if (section == null || section.messages.isEmpty()) {
                host.container.toastController.show(host.strings().novel.recapEmpty)
                return@launchWithGroupModelAndFlag
            }
            val prompt = NovelEngine.buildChapterRecapMessages(novel, target, section.messages)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.4, maxTokens = 400)
            val summary = res.content.trim()
            if (summary.isNotEmpty()) {
                host.container.database.withTransaction {
                    val freshTarget = chatDao.getNovelChaptersForConversation(conv.id)
                        .map { it.toDomain() }
                        .firstOrNull { it.id == chapterId }
                        ?: return@withTransaction
                    chatDao.upsertNovelChapter(
                        freshTarget.copy(summary = summary, updatedAt = System.currentTimeMillis()).toEntity(),
                    )
                }
                host.container.toastController.show(host.strings().novel.recapGenerated)
            }
        }
    }

    fun selectActiveChapter(chapterId: String) {
        host.scope.launch {
            val activeId = host.uiFlow.value.activeConversation?.id ?: return@launch
            val switched = host.container.database.withTransaction {
                val conv = chatDao.getConversationById(activeId)?.toDomain() ?: return@withTransaction false
                val novel = conv.novel ?: return@withTransaction false
                val chapters = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
                val target = chapters.firstOrNull { it.id == chapterId } ?: return@withTransaction false
                val now = System.currentTimeMillis()
                if (target.status == "draft" && target.wordCount == 0) {
                    val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
                    val prevActive = chapters.firstOrNull { it.id == novel.activeChapterId && it.id != target.id }
                    if (prevActive != null && prevActive.endSeq == null && tip >= prevActive.startSeq) {
                        chatDao.upsertNovelChapter(prevActive.copy(endSeq = tip, updatedAt = now).toEntity())
                    }
                    chatDao.upsertNovelChapter(
                        target.copy(
                            status = "writing",
                            startSeq = tip + 1,
                            endSeq = null,
                            updatedAt = now,
                        ).toEntity(),
                    )
                }
                chatDao.upsertConversation(
                    conv.copy(novel = novel.copy(activeChapterId = chapterId), updatedAt = now).toEntity(),
                )
                true
            }
            if (switched) {
                syncNovelChapterWordCounts(activeId)
                host.scheduleWaterline()
            }
        }
    }

    fun upsertNovelLore(category: String, title: String, keywords: String, content: String, alwaysActive: Boolean) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (title.isBlank() || content.isBlank()) return
        host.scope.launch {
            val now = System.currentTimeMillis()
            val kws = keywords.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val entry = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                category = category,
                title = title.trim(),
                content = content.trim(),
                keywords = kws.ifEmpty { listOf(title.trim()) },
                alwaysActive = alwaysActive,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            chatDao.upsertNovelLore(entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry)).toEntity())
            host.scheduleIndexing(conv.id)
            host.scheduleWaterline()
        }
    }

    fun saveForeshadowToLore(foreshadowText: String) {
        val clean = foreshadowText.trim()
        if (clean.isEmpty()) return
        val title = clean.take(16)
        upsertNovelLore(
            category = "foreshadow",
            title = title,
            keywords = title,
            content = clean,
            alwaysActive = false,
        )
        host.container.toastController.show(host.strings().novel.loreAddBtn)
    }

    fun extractNovelLoreNow() {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isExtractingNovelLore },
            setBusy = { st, busy -> st.copy(isExtractingNovelLore = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val novel = conv.novel ?: return@launchWithGroupModelAndFlag
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val activeMsgs = TimelineReconciler.activeMessages(msgs)
            if (activeMsgs.none { it.role == "assistant" && NovelExport.stripInternalTags(it.content).isNotBlank() }) {
                host.container.toastController.show(host.strings().novel.loreExtractNoNew)
                return@launchWithGroupModelAndFlag
            }
            val existing = chatDao.getNovelLoreForConversation(conv.id).map { it.toDomain() }
            val tipSeq = activeMsgs.maxOfOrNull { it.seq } ?: 0
            val prompt = NovelEngine.buildLoreExtractMessages(novel, activeMsgs, existing)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.3, maxTokens = 1200)
            val extracted = NovelEngine.parseLoreExtractResponse(res.content, conv.id, tipSeq, existing)
            if (extracted.isEmpty()) {
                host.container.toastController.show(host.strings().novel.loreExtractNoNew)
            } else {
                chatDao.upsertNovelLoreList(extracted.map { it.toEntity() })
                host.scheduleIndexing(conv.id)
                host.scheduleWaterline()
                val isZh = host.container.settingsState.value.language == AppLanguage.ZH
                host.container.toastController.show(
                    if (isZh) "已提炼并收录 ${extracted.size} 条设定/伏笔" else "Extracted ${extracted.size} lore entries",
                )
            }
        }
    }

    fun deleteNovelLore(id: String) {
        val convId = host.uiFlow.value.activeConversation?.id
        host.scope.launch {
            chatDao.deleteNovelLore(id)
            host.container.retrievalCoordinator.deleteOwnerVector(
                com.gugachat.app.data.chat.RetrievalCoordinator.OWNER_NOVEL_LORE,
                id,
                conversationId = convId,
            )
            host.scheduleWaterline()
        }
    }

    fun toggleNovelLoreEnabled(id: String) {
        host.scope.launch {
            val existing = host.uiFlow.value.novelLore.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertNovelLore(
                existing.copy(
                    enabled = !existing.enabled,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            if (!existing.enabled) host.scheduleIndexing(existing.conversationId)
            host.scheduleWaterline()
        }
    }

    fun toggleNovelLoreAlwaysActive(id: String) {
        host.scope.launch {
            val existing = host.uiFlow.value.novelLore.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertNovelLore(
                existing.copy(
                    alwaysActive = !existing.alwaysActive,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            host.scheduleWaterline()
        }
    }

    fun updateNovelLore(entry: NovelLoreEntry) {
        if (entry.title.isBlank() || entry.content.isBlank()) return
        host.scope.launch {
            val cleanTitle = entry.title.trim()
            val cleanContent = entry.content.trim()
            val cleanKeywords = entry.keywords.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(cleanTitle) }
            val cleanAliases = entry.aliases.map { it.trim() }.filter { it.isNotEmpty() }
            val normalized = entry.copy(
                title = cleanTitle,
                content = cleanContent,
                keywords = cleanKeywords,
                aliases = cleanAliases,
                updatedAt = System.currentTimeMillis(),
            )
            chatDao.upsertNovelLore(
                normalized.copy(estimatedTokens = NovelEngine.estimateLoreTokens(normalized)).toEntity(),
            )
            host.scheduleIndexing(normalized.conversationId)
            host.scheduleWaterline()
        }
    }

    fun generateThreePathDrafts(hint: String) {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            setBusy = { st, busy -> st.copy(isGeneratingDrafts = busy) },
            fallbackError = { "Draft generation failed" },
        ) { activeConv, gid, model ->
            val novel = activeConv?.novel ?: return@launchWithGroupModelAndFlag
            val activeCh = host.uiFlow.value.novelChapters.firstOrNull { it.id == novel.activeChapterId }
            val prompt = NovelEngine.buildThreePathDraftMessages(novel, activeCh, host.uiFlow.value.messages, hint)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.85, maxTokens = 1500)
            val options = NovelEngine.parseThreePathDrafts(res.content)
            host.uiFlow.update { it.copy(threePathDrafts = options) }
            if (options.isEmpty()) {
                host.container.toastController.show(host.strings().novel.threePathsFailed, isError = true)
            }
        }
    }

    fun adoptThreePathDraft(option: NovelDraftOption, sendImmediately: Boolean) {
        val directive = buildString {
            append("按走向「${option.title}」推进：${option.summary}")
            if (option.opening.isNotBlank()) {
                append("\n参考开篇语感：${option.opening}")
            }
        }
        if (sendImmediately) {
            host.setDraftNovelKind(NovelInputKind.branch)
            host.sendMessage(overrideText = directive)
        } else {
            host.updateDraftText(option.opening.ifBlank { directive })
        }
    }

    fun runOutlineChat(instruction: String) {
        if (instruction.isBlank()) return
        host.oneShotRunner.launchWithGroupModelAndFlag(
            setBusy = { st, busy -> st.copy(isGeneratingDrafts = busy) },
            fallbackError = { "Outline chat failed" },
        ) { activeConv, gid, model ->
            val novel = activeConv?.novel ?: return@launchWithGroupModelAndFlag
            val prompt = NovelEngine.buildOutlineChatMessages(novel, host.uiFlow.value.novelChapters, instruction)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.7, maxTokens = 1500)
            host.uiFlow.update { it.copy(outlineChatReply = res.content.trim()) }
        }
    }

    fun applyOutlineToBook(outlineText: String) {
        val clean = outlineText.trim()
        if (clean.isEmpty()) return
        updateNovelConfig { it.copy(mainOutline = clean) }
        host.container.toastController.show(host.strings().novel.outlineChatApplied)
    }

    fun syncChaptersFromOutline(outlineDraft: String) {
        if (outlineDraft.isBlank()) return
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isSyncingOutlineChapters },
            setBusy = { st, busy -> st.copy(isSyncingOutlineChapters = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val novel = conv.novel ?: return@launchWithGroupModelAndFlag
            val existing = chatDao.getNovelChaptersForConversation(conv.id).map { it.toDomain() }
            val prompt = NovelEngine.buildOutlineChapterSyncMessages(novel, existing, outlineDraft)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.4, maxTokens = 1200)
            val parsed = NovelEngine.parseSuggestedChapters(res.content)
            if (parsed.isEmpty()) {
                host.container.toastController.show(host.strings().common.unknownError, isError = true)
                return@launchWithGroupModelAndFlag
            }
            val now = System.currentTimeMillis()
            var nextOrder = (existing.maxOfOrNull { it.order } ?: 0) + 1
            val tip = TimelineReconciler.findActiveTipSeq(chatDao.getMessagesForConversation(conv.id).map { it.toDomain() })
            val newChapters = parsed.map { spec ->
                val ch = NovelChapter(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    order = nextOrder,
                    title = spec.title,
                    outline = spec.outline,
                    status = "draft",
                    startSeq = tip + nextOrder * 1000,
                    createdAt = now,
                    updatedAt = now,
                )
                nextOrder++
                ch
            }
            chatDao.upsertNovelChapters(newChapters.map { it.toEntity() })
            host.container.toastController.show(host.strings().novel.outlineChatApplyChapters.fmt("count" to newChapters.size))
        }
    }

    fun runPolish(prose: String, instruction: String, presetMode: String? = null) {
        if (prose.isBlank()) return
        host.oneShotRunner.launchWithGroupModelAndFlag(
            setBusy = { st, busy -> st.copy(isGeneratingDrafts = busy) },
            fallbackError = { "Polish failed" },
        ) { activeConv, gid, model ->
            val prompt = NovelEngine.buildPolishMessages(
                prose = prose,
                style = activeConv?.novel?.style.orEmpty(),
                instruction = instruction,
                presetMode = presetMode,
            )
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.65, maxTokens = 2200)
            host.uiFlow.update { it.copy(polishResult = NovelExport.stripInternalTags(res.content)) }
        }
    }

    fun applyPolishAsVariant(polishedText: String) {
        val clean = NovelExport.stripInternalTags(polishedText)
        if (clean.isEmpty()) return
        host.scope.launch {
            val conv = host.uiFlow.value.activeConversation ?: return@launch
            val msgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
            val lastAssistant = TimelineReconciler.activeMessages(msgs).lastOrNull { it.role == "assistant" } ?: return@launch
            val now = System.currentTimeMillis()
            val baseVariants = if (lastAssistant.variants.isNotEmpty()) {
                lastAssistant.variants
            } else {
                listOf(
                    MessageVariant(
                        id = UUID.randomUUID().toString(),
                        content = lastAssistant.content,
                        reasoning = lastAssistant.reasoning,
                        createdAt = lastAssistant.createdAt,
                        usage = lastAssistant.usage,
                        model = lastAssistant.model,
                        novelStatusSnapshot = conv.novel?.currentStatus,
                        novelChoices = lastAssistant.novelChoices,
                    ),
                )
            }
            val polishedVariant = MessageVariant(
                id = UUID.randomUUID().toString(),
                content = clean,
                createdAt = now,
                model = lastAssistant.model,
                novelStatusSnapshot = conv.novel?.currentStatus,
                novelChoices = lastAssistant.novelChoices,
            )
            val nextVariants = baseVariants + polishedVariant
            val updatedMsg = lastAssistant.copy(
                content = clean,
                editedAt = now,
                tokenEstimate = ContextBuilder.estimateMessageTokens(clean),
                variants = nextVariants,
                activeVariantIndex = nextVariants.lastIndex,
            )
            chatDao.upsertMessage(updatedMsg.toEntity())
            syncNovelChapterWordCounts(conv.id)
            host.scheduleWaterline()
            host.container.toastController.show(host.strings().novel.polishDone)
        }
    }

    fun refreshNovelChoices() {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            alreadyBusy = { it.isStreaming || it.isRefreshingChoices },
            setBusy = { st, busy -> st.copy(isRefreshingChoices = busy) },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val novel = conv.novel ?: return@launchWithGroupModelAndFlag
            val activeCh = host.uiFlow.value.novelChapters.firstOrNull { it.id == novel.activeChapterId }
            val prompt = NovelEngine.buildRefreshChoicesMessages(novel, activeCh, host.uiFlow.value.messages)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.85, maxTokens = 500)
            val newChoices = NovelEngine.parseChoicesOnly(res.content)
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
                            if (idx == activeIdx) v.copy(novelChoices = newChoices) else v
                        }
                    } else {
                        lastAssistant.variants
                    }
                    chatDao.upsertMessage(
                        lastAssistant.copy(novelChoices = newChoices, variants = updatedVariants).toEntity(),
                    )
                }
                val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                val freshNovel = freshConv.novel ?: novel
                chatDao.upsertConversation(
                    freshConv.copy(
                        novel = freshNovel.copy(pendingChoices = newChoices),
                        updatedAt = System.currentTimeMillis(),
                    ).toEntity(),
                )
            }
        }
    }

    fun setPureReadingMode(enabled: Boolean) {
        host.uiFlow.update { it.copy(pureReadingMode = enabled) }
    }

    fun updateReaderPrefs(
        fontSize: Int? = null,
        paper: String? = null,
        lineHeight: Float? = null,
        serif: Boolean? = null,
        indent: Boolean? = null,
    ) {
        host.uiFlow.update {
            it.copy(
                readerFontSizeSp = fontSize?.coerceIn(13, 24) ?: it.readerFontSizeSp,
                readerPaper = paper ?: it.readerPaper,
                readerLineHeight = lineHeight?.coerceIn(1.4f, 2.2f) ?: it.readerLineHeight,
                readerSerif = serif ?: it.readerSerif,
                readerIndent = indent ?: it.readerIndent,
            )
        }
        host.scope.launch {
            host.container.settingsStore.setReaderPrefs(
                paper = paper,
                fontSizeSp = fontSize,
                lineHeight = lineHeight,
                serif = serif,
                indent = indent,
            )
        }
    }

    fun exportNovelToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        markdown: Boolean,
        includeLore: Boolean = false,
    ) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        val novel = conv.novel ?: return
        host.scope.launch {
            try {
                val text = if (markdown) {
                    NovelExport.formatAsMarkdown(
                        novel = novel,
                        chapters = st.novelChapters,
                        messages = st.messages,
                        lore = st.novelLore,
                        includeLoreAppendix = includeLore,
                    )
                } else {
                    NovelExport.formatAsTxt(
                        novel = novel,
                        chapters = st.novelChapters,
                        messages = st.messages,
                        lore = st.novelLore,
                        includeLoreAppendix = includeLore,
                    )
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                host.container.toastController.show(host.strings().novel.exportDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    fun exportNovelEpubToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        includeLore: Boolean = false,
    ) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        val novel = conv.novel ?: return
        host.scope.launch {
            try {
                val coverFile = LocalMediaPathResolver
                    .resolveFile(host.container.appContext.filesDir.absolutePath, novel.coverImagePath)
                    ?.takeIf { it.isFile }
                val epubBytes = withContext(Dispatchers.IO) {
                    val coverBytes = coverFile?.readBytes()
                    val mime = when (coverFile?.extension?.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        else -> "image/png"
                    }
                    NovelExport.exportEpubBytes(
                        conv = conv,
                        chapters = st.novelChapters,
                        messages = st.messages,
                        lore = if (includeLore) st.novelLore else emptyList(),
                        coverImageBytes = coverBytes,
                        coverMimeType = mime,
                    )
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(epubBytes) }
                        ?: error("Failed to open output stream")
                }
                host.container.toastController.show(host.strings().novel.exportDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    private fun buildDefaultStarterLore(
        convId: String,
        specs: List<Triple<String, String, String>>,
        now: Long,
    ): List<NovelLoreEntry> = specs.map { (cat, lTitle, lContent) ->
        val item = NovelLoreEntry(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            category = cat,
            title = lTitle,
            content = lContent,
            keywords = listOf(lTitle),
            alwaysActive = cat == "rule",
            validFromSeq = 0,
            createdAt = now,
            updatedAt = now,
        )
        item.copy(estimatedTokens = NovelEngine.estimateLoreTokens(item))
    }

    companion object {
        /**
         * Removes [chapterId] from [chapters], closes the resulting `[prev.endSeq, next.startSeq]`
         * gap so deleted chapter prose stays in the adjacent chapter instead of becoming trailing
         * orphans, reindexes `order`, and returns the updated chapters plus the fallback active
         * chapter ID when the active chapter was deleted.
         */
        internal fun removeChapterAndCloseGap(
            chapters: List<NovelChapter>,
            chapterId: String,
            currentActiveChapterId: String?,
            now: Long = System.currentTimeMillis(),
        ): Pair<List<NovelChapter>, String?>? {
            if (chapters.size <= 1) return null
            val sorted = chapters.sortedBy { it.order }
            val targetIdx = sorted.indexOfFirst { it.id == chapterId }
            if (targetIdx < 0) return null

            val deletedCh = sorted[targetIdx]
            val mutable = sorted.toMutableList()
            mutable.removeAt(targetIdx)

            val fallbackIdx = (targetIdx - 1).coerceAtLeast(0).coerceAtMost(mutable.lastIndex)
            val isDeletingActive = currentActiveChapterId == chapterId

            if (targetIdx > 0) {
                val prev = mutable[targetIdx - 1]
                val next = mutable.getOrNull(targetIdx)
                val mergedEndSeq = when {
                    deletedCh.endSeq != null -> deletedCh.endSeq
                    next != null && next.status != "draft" -> (next.startSeq - 1).coerceAtLeast(prev.startSeq)
                    else -> null
                }
                val nextPrevStatus = if (mergedEndSeq == null && (isDeletingActive || prev.status == "done")) {
                    "writing"
                } else {
                    prev.status
                }
                mutable[targetIdx - 1] = prev.copy(
                    endSeq = mergedEndSeq,
                    status = nextPrevStatus,
                    updatedAt = now,
                )
            } else {
                val next = mutable[0]
                mutable[0] = next.copy(
                    startSeq = minOf(next.startSeq, deletedCh.startSeq),
                    status = if (next.status == "draft") "writing" else next.status,
                    updatedAt = now,
                )
            }

            val reindexed = mutable.mapIndexed { idx, ch ->
                ch.copy(order = idx + 1, updatedAt = now)
            }
            val fallbackActiveId = reindexed.getOrNull(fallbackIdx)?.id ?: reindexed.lastOrNull()?.id
            return reindexed to fallbackActiveId
        }
    }
}
