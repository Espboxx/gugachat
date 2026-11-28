package com.gugachat.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.ExternalImportService
import com.gugachat.app.data.chat.AssetImportResult
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.StudioArtModelUnconfiguredException
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.ConversationWorldbookCrossRef
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import com.gugachat.app.data.local.decodedAltGreetings
import com.gugachat.app.data.local.decodedCast
import com.gugachat.app.data.local.decodedExpressionSprites
import com.gugachat.app.data.local.decodedRegexScripts
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ContextBuilder
import com.gugachat.app.domain.chat.ContextCompactor
import com.gugachat.app.domain.chat.MemoryCategory
import com.gugachat.app.domain.chat.MemoryExtractor
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.PromptPresetBlock
import com.gugachat.app.domain.chat.PromptPresetBlockOps
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.chat.RpCgRecord
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpEngine
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.TimelineReconciler
import com.gugachat.app.domain.chat.TokenEstimator
import com.gugachat.app.domain.chat.UserMemory
import com.gugachat.app.domain.chat.UuSaveCodec
import com.gugachat.app.domain.chat.WorldbookEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.UUID

/**
 * Encapsulates memory, ledger, recall inspector controls, manual compaction, Tavern card/preset/worldbook
 * import & export, V2 conversation lore persistence, and RP/Novel local + AI artwork binding.
 */
internal class ChatAssetsAndMemoryDelegate(private val host: ChatViewModelHost) {
    private val chatDao get() = host.database.chatDao()
    private val userMemoryDao get() = host.database.userMemoryDao()
    private val assetDao get() = host.database.assetDao()

    // ==================== Memory CRUD & Recall Controls ====================

    fun extractUserMemoriesNow() {
        host.oneShotRunner.launchWithGroupModelAndFlag(
            setBusy = { st, busy -> st.copy(isExtractingMemory = busy) },
            fallbackError = { "Extraction failed" },
        ) { activeConv, gid, model ->
            val conv = activeConv ?: return@launchWithGroupModelAndFlag
            val msgs = TimelineReconciler.activeMessages(host.uiFlow.value.messages)
            val existing = host.uiFlow.value.userMemories
            val prompt = MemoryExtractor.buildMemoryExtractionMessages(existing, msgs)
            val res = host.oneShotRunner.completeOnce(gid, model, prompt, temperature = 0.2, maxTokens = 600)
            val extracted = MemoryExtractor.parseExtractedMemories(res.content)
            val isZh = host.container.settingsState.value.language == AppLanguage.ZH
            if (extracted.isEmpty()) {
                host.container.toastController.show(if (isZh) "未提炼出新的长期记忆" else "No new long-term memories extracted")
            } else {
                val (merged, added) = MemoryExtractor.mergeExtractedMemories(existing, extracted)
                userMemoryDao.upsertAll(merged.map { it.toEntity() })
                if (added > 0) host.scheduleIndexing(conv.id)
                host.container.toastController.show(if (isZh) "已新增 $added 条长期记忆" else "Added $added memories")
            }
        }
    }

    fun addUserMemory(category: MemoryCategory, content: String, pinned: Boolean = false) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        host.scope.launch {
            val now = System.currentTimeMillis()
            val item = UserMemory(
                id = UUID.randomUUID().toString(),
                category = category,
                content = clean,
                pinned = pinned,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                estimatedTokens = MemoryExtractor.estimateUserMemoryTokens(clean),
            )
            userMemoryDao.upsert(item.toEntity())
            host.scheduleIndexing()
        }
    }

    fun toggleUserMemoryPin(id: String) {
        host.scope.launch {
            val m = host.uiFlow.value.userMemories.firstOrNull { it.id == id } ?: return@launch
            userMemoryDao.upsert(m.copy(pinned = !m.pinned, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun toggleUserMemoryEnabled(id: String) {
        host.scope.launch {
            val m = host.uiFlow.value.userMemories.firstOrNull { it.id == id } ?: return@launch
            userMemoryDao.upsert(m.copy(enabled = !m.enabled, updatedAt = System.currentTimeMillis()).toEntity())
            if (!m.enabled) host.scheduleIndexing()
        }
    }

    fun deleteUserMemory(id: String) {
        host.scope.launch {
            userMemoryDao.deleteById(id)
            host.container.retrievalCoordinator.deleteOwnerVector(RetrievalCoordinator.OWNER_USER_MEMORY, id, conversationId = "")
        }
    }

    fun addLedgerItem(kind: String, title: String, content: String) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        if (title.isBlank() || content.isBlank()) return
        host.scope.launch {
            val now = System.currentTimeMillis()
            val tip = TimelineReconciler.findActiveTipSeq(st.messages)
            val item = MemoryLedgerItem(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                kind = kind,
                title = title.trim(),
                content = content.trim(),
                validFromSeq = tip,
                origin = "user",
                createdAt = now,
                updatedAt = now,
                estimatedTokens = ContextBuilder.estimateTextTokens("$title: $content") + 8,
            )
            chatDao.upsertMemoryLedger(item.toEntity())
            host.scheduleIndexing(conv.id)
        }
    }

    fun toggleLedgerPin(id: String) {
        host.scope.launch {
            val item = host.uiFlow.value.memoryLedger.firstOrNull { it.id == id } ?: return@launch
            chatDao.upsertMemoryLedger(item.copy(pinned = !item.pinned, updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    fun deleteLedgerItem(id: String) {
        val convId = host.uiFlow.value.activeConversation?.id
        host.scope.launch {
            chatDao.deleteMemoryLedger(id)
            host.container.retrievalCoordinator.deleteOwnerVector(RetrievalCoordinator.OWNER_RP_LEDGER, id, conversationId = convId)
        }
    }

    fun toggleMuteRecallKey(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        host.uiFlow.update { st ->
            val next = if (clean in st.mutedRecallKeys) st.mutedRecallKeys - clean else st.mutedRecallKeys + clean
            st.copy(mutedRecallKeys = next)
        }
        host.scheduleWaterline(debounceMs = 0L)
    }

    fun togglePinRecallItem(source: String, id: String) {
        when (source) {
            "user_memory_archival" -> toggleUserMemoryPin(id)
            "rp_ledger" -> toggleLedgerPin(id)
            "novel_lore" -> host.toggleNovelLoreAlwaysActive(id)
        }
    }

    fun disableRecallItem(source: String, id: String) {
        val isZh = host.container.settingsState.value.language == AppLanguage.ZH
        host.scope.launch {
            val st = host.uiFlow.value
            when (source) {
                "user_memory_archival" -> {
                    val m = st.userMemories.firstOrNull { it.id == id } ?: return@launch
                    if (m.enabled) {
                        userMemoryDao.upsert(m.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                "rp_ledger" -> {
                    val item = st.memoryLedger.firstOrNull { it.id == id } ?: return@launch
                    if (item.enabled) {
                        chatDao.upsertMemoryLedger(item.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                "novel_lore" -> {
                    val entry = st.novelLore.firstOrNull { it.id == id } ?: return@launch
                    if (entry.enabled) {
                        chatDao.upsertNovelLore(entry.copy(enabled = false, updatedAt = System.currentTimeMillis()).toEntity())
                    }
                }
                else -> return@launch
            }
            host.scheduleWaterline(debounceMs = 0L)
            host.container.toastController.show(if (isZh) "已停用该记忆条目" else "Disabled memory item")
        }
    }

    fun reindexActiveConversationVectors() {
        val convId = host.uiFlow.value.activeConversation?.id ?: return
        val isZh = host.container.settingsState.value.language == AppLanguage.ZH
        val settings = host.container.settingsState.value.retrievalSettings
        if (settings.mode == RetrievalMode.LEXICAL_ONLY) {
            host.container.toastController.show(
                if (isZh) "当前为纯词法检索模式，请先在设置中启用向量检索" else "Switch retrieval mode to Local or Cloud Vector in settings first",
            )
            return
        }
        if (host.container.retrievalCoordinator.resolveActiveEmbeddingEngine(settings) == null) {
            host.container.toastController.show(
                if (isZh) "向量模型尚未就绪，请检查下载或接口配置" else "Vector model is not ready yet; check download or API settings",
                isError = true,
            )
            return
        }
        host.scheduleIndexing(convId)
    }

    fun triggerManualCompaction() {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        val s = host.strings().chat
        host.scope.launch {
            host.uiFlow.update { it.copy(isCompacting = true, compactingProgressText = s.compactingProgress) }
            try {
                val out = ContextCompactor.compactNow(
                    chatApi = host.container.onlineChatApi,
                    conversation = conv,
                    messages = st.messages,
                    fallbackGroupId = st.selectedGroupId,
                    fallbackModel = st.selectedModelId,
                    onChunkProgress = { completed, total ->
                        host.uiFlow.update {
                            it.copy(
                                compactingProgressText = s.compactingChunkProgress.fmt(
                                    "current" to completed,
                                    "total" to total,
                                ),
                            )
                        }
                    },
                )
                if (out == null) {
                    host.container.toastController.show(s.compactNotEnough)
                } else {
                    host.container.database.withTransaction {
                        val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                        chatDao.upsertConversation(
                            fresh.copy(
                                archivedSummary = out.archivedSummary,
                                summarizedUntilSeq = out.summarizedUntilSeq,
                                updatedAt = System.currentTimeMillis(),
                            ).toEntity(),
                        )
                        if (out.newEpisodes.isNotEmpty()) {
                            chatDao.upsertMemoryEpisodes(out.newEpisodes.map { it.toEntity() })
                        } else {
                            out.newEpisode?.let { chatDao.upsertMemoryEpisode(it.toEntity()) }
                        }
                    }
                    host.scheduleIndexing(conv.id)
                    host.scheduleWaterline(debounceMs = 0L)
                    host.container.toastController.show(s.compactSuccess)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: host.strings().common.unknownError
                host.container.toastController.show(s.compactFailedFallback.fmt("reason" to reason), isError = true)
            } finally {
                host.uiFlow.update { it.copy(isCompacting = false, compactingProgressText = null) }
            }
        }
    }

    // ==================== Tavern Import, Export & V4 Mounts ====================

    fun importSillyTavernCardUri(uri: Uri, contentResolver: ContentResolver) {
        val isZh = host.strings().language == AppLanguage.ZH
        host.scope.launch {
            try {
                when (val imported = host.container.assetImportCoordinator.importUri(uri, contentResolver)) {
                    is AssetImportResult.UuGameSaveDetected -> {
                        val rootElem = runCatching { AppJson.parseToJsonElement(imported.rawJson) }.getOrNull()
                            as? kotlinx.serialization.json.JsonObject ?: error("Invalid UUGame save")
                        val bundle = withContext(Dispatchers.Default) {
                            UuSaveCodec.parseJsonElement(rootElem, imported.displayName)
                        }
                        val st = host.uiFlow.value
                        val defaults = ChatParams(
                            maxTokens = st.defaultMaxOutputTokens,
                            contextWindowOverride = st.defaultContextWindowOverride,
                            maxHistoryMessages = st.defaultMaxHistoryMessages,
                        )
                        val prepared = bundle.items.map { item ->
                            val ic = item.conversation
                            val mergedParams = ic.params.copy(
                                maxTokens = ic.params.maxTokens ?: defaults.maxTokens,
                                contextWindowOverride = ic.params.contextWindowOverride ?: defaults.contextWindowOverride,
                                maxHistoryMessages = ic.params.maxHistoryMessages ?: defaults.maxHistoryMessages,
                                activeWindowStartSeq = ic.params.activeWindowStartSeq,
                            )
                            item.copy(
                                conversation = ic.copy(
                                    groupId = ic.groupId ?: st.selectedGroupId,
                                    model = ic.model?.takeIf { it.isNotBlank() } ?: st.selectedModelId,
                                    params = mergedParams,
                                ),
                            )
                        }
                        val res = ExternalImportService.persistSelected(host.database, prepared)
                        val convIds = prepared.map { it.conversation.id }
                        host.container.scope.launch {
                            ExternalImportService.cacheRemoteRpAvatars(
                                appContext = host.container.appContext,
                                httpClient = host.container.apiClient.standardClient,
                                db = host.database,
                                conversationIds = convIds,
                            )
                        }
                        res.firstConversationId?.let { cid ->
                            host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
                            host.activeConvIdFlow.value = cid
                        }
                        val title = prepared.firstOrNull()?.conversation?.title.orEmpty()
                        host.container.toastController.show(host.strings().rp.importSuccess.fmt("name" to title))
                    }

                    is AssetImportResult.CharacterCardImported -> {
                        launchImportedCardConversation(imported)
                        host.container.toastController.show(host.strings().rp.importSuccess.fmt("name" to imported.card.name))
                    }

                    is AssetImportResult.WorldbookImported -> {
                        host.container.toastController.show(
                            if (isZh) "已导入世界书「${imported.worldbook.name}」到「创作 → 设定」"
                            else "Imported worldbook \"${imported.worldbook.name}\" to library",
                        )
                    }

                    is AssetImportResult.PromptPresetImported -> {
                        host.container.toastController.show(
                            if (isZh) "已导入预设「${imported.preset.name}」到「创作 → 设定」"
                            else "Imported preset \"${imported.preset.name}\" to library",
                        )
                    }

                    else -> error(if (isZh) "未识别到可导入的角色卡" else "Unrecognized character card")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(
                    host.strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    fun importLorebookOrPresetUri(uri: Uri, contentResolver: ContentResolver) {
        val conv = host.uiFlow.value.activeConversation ?: return
        val isZh = host.strings().language == AppLanguage.ZH
        host.scope.launch {
            try {
                when (val imported = host.container.assetImportCoordinator.importUri(uri, contentResolver)) {
                    is AssetImportResult.WorldbookImported -> {
                        mountWorldbook(imported.worldbook.id, convId = conv.id)
                        host.container.toastController.show(
                            host.strings().rp.importLorebookSuccess.fmt(
                                "name" to imported.worldbook.name,
                                "count" to imported.entries.size,
                            ),
                        )
                    }

                    is AssetImportResult.PromptPresetImported -> {
                        val addedRegex = mergeRegexIntoConvRp(conv, imported.regexScripts) { current ->
                            current.copy(presetId = imported.preset.id)
                        }
                        val baseMsg = host.strings().rp.importPresetSuccess.fmt("name" to imported.preset.name)
                        val extras = if (addedRegex > 0) {
                            if (isZh) "+$addedRegex 条正则" else "+$addedRegex regex"
                        } else {
                            ""
                        }
                        host.container.toastController.show(if (extras.isEmpty()) baseMsg else "$baseMsg（$extras）")
                    }

                    is AssetImportResult.QuickRepliesImported -> {
                        val qr = imported.quickReplies
                        if (conv.rp != null) {
                            host.updateRpConfig { cfg ->
                                cfg.copy(quickActions = (cfg.quickActions + qr.actions).distinct())
                            }
                            host.container.toastController.show(
                                if (isZh) "已导入快速回复「${qr.name}」（${qr.actions.size} 条）" else "Imported quick replies \"${qr.name}\" (${qr.actions.size})",
                            )
                        } else {
                            host.container.toastController.show(
                                if (isZh) "快速回复仅适用于文游会话" else "Quick replies apply to RP sessions only",
                                isError = true,
                            )
                        }
                    }

                    is AssetImportResult.RegexScriptsImported -> {
                        val addedRegex = mergeRegexIntoConvRp(conv, imported.scripts)
                        if (conv.rp != null) {
                            host.container.toastController.show(
                                if (isZh) "已导入 $addedRegex 条正则脚本" else "Imported $addedRegex regex scripts",
                            )
                        } else {
                            host.container.toastController.show(
                                if (isZh) "正则脚本仅适用于文游会话" else "Regex scripts apply to RP sessions only",
                                isError = true,
                            )
                        }
                    }

                    is AssetImportResult.SettingsImported -> {
                        val addedRegex = mergeRegexIntoConvRp(conv, imported.regexScripts) { current ->
                            val rp = current.rp
                            if (rp != null && imported.authorNote.isNotBlank()) {
                                current.copy(rp = rp.copy(authorNote = mergeTextSection(rp.authorNote, imported.authorNote)))
                            } else {
                                current
                            }
                        }
                        host.container.toastController.show(
                            if (isZh) "已从酒馆设置导入（+$addedRegex 条正则）" else "Imported Tavern settings (+$addedRegex regex)",
                        )
                    }

                    is AssetImportResult.CharacterCardImported -> {
                        imported.worldbook?.let { wb -> mountWorldbook(wb.id, convId = conv.id) }
                        val addedRegex = mergeRegexIntoConvRp(conv, imported.rpConfig.regexScripts)
                        host.container.toastController.show(
                            if (isZh) {
                                "已从角色卡「${imported.card.name}」挂载 ${imported.entryCount} 条世界书" +
                                    if (addedRegex > 0) " · $addedRegex 条正则" else ""
                            } else {
                                "Mounted ${imported.entryCount} lore entries" +
                                    (if (addedRegex > 0) " · $addedRegex regex" else "") +
                                    " from \"${imported.card.name}\""
                            },
                        )
                    }

                    else -> error(
                        if (isZh) "未识别到世界书、酒馆预设或正则 JSON"
                        else "Unrecognized lorebook, Tavern preset, or regex JSON",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(
                    host.strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    fun importRpRegexJsonUri(uri: Uri, contentResolver: ContentResolver) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (conv.rp == null) return
        val isZh = host.strings().language == AppLanguage.ZH
        host.scope.launch {
            try {
                val scripts = host.container.assetImportCoordinator.extractRegexScriptsFromUri(uri, contentResolver)
                if (scripts.isEmpty()) {
                    error(
                        if (isZh) {
                            "文件中未找到酒馆正则脚本（支持单条/数组/预设/角色卡/settings.json）"
                        } else {
                            "No Tavern regex scripts found (supports single, array, preset, card, or settings.json)"
                        },
                    )
                }
                val addedCount = mergeRegexIntoConvRp(conv, scripts)
                host.container.toastController.show(
                    if (addedCount > 0) {
                        if (isZh) "已导入 $addedCount 条酒馆正则脚本" else "Imported $addedCount Tavern regex scripts"
                    } else {
                        if (isZh) "正则脚本均已存在（未重复添加）" else "All regex scripts already exist"
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(
                    host.strings().rp.importFailed.fmt("reason" to (e.message ?: "Unknown")),
                    isError = true,
                )
            }
        }
    }

    suspend fun importTavernAssetFromBytes(bytes: ByteArray, fileName: String?): Boolean {
        val isZh = host.strings().language == AppLanguage.ZH
        return when (val imported = host.container.assetImportCoordinator.importBytes(bytes = bytes, fileName = fileName)) {
            is AssetImportResult.CharacterCardImported -> {
                launchImportedCardConversation(imported)
                host.container.toastController.show(host.strings().rp.importSuccess.fmt("name" to imported.card.name))
                true
            }
            is AssetImportResult.WorldbookImported -> {
                host.container.toastController.show(
                    if (isZh) "已导入世界书「${imported.worldbook.name}」（${imported.entries.size} 条）到「创作 → 设定」"
                    else "Imported worldbook \"${imported.worldbook.name}\" (${imported.entries.size} entries)",
                )
                true
            }
            is AssetImportResult.PromptPresetImported -> {
                host.container.toastController.show(
                    if (isZh) "已导入预设「${imported.preset.name}」到「创作 → 设定」"
                    else "Imported preset \"${imported.preset.name}\" to library",
                )
                true
            }
            else -> false
        }
    }

    fun resolveLocalMediaFile(storedPath: String?): File? =
        LocalMediaPathResolver.resolveFile(host.container.appContext.filesDir.absolutePath, storedPath)?.takeIf { it.isFile }

    fun exportRpCardJsonToUri(uri: Uri, contentResolver: ContentResolver) {
        val st = host.uiFlow.value
        val rp = st.activeConversation?.rp ?: return
        host.scope.launch {
            try {
                val json = RpCardCodec.exportToV3Json(rp, st.novelLore)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Failed to open output stream")
                }
                host.container.toastController.show(host.strings().rp.exportDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    fun exportRpCardPngToUri(uri: Uri, contentResolver: ContentResolver) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        val rp = conv.rp ?: return
        val lore = st.novelLore
        val boundCard = st.boundCharacterCard
        host.scope.launch {
            try {
                host.container.assetImportCoordinator.exportCharacterCardToPngUri(
                    rp = rp,
                    lore = lore,
                    cardEntity = boundCard,
                    uri = uri,
                    contentResolver = contentResolver,
                )
                host.container.toastController.show(host.strings().rp.exportDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: "Export failed", isError = true)
            }
        }
    }

    fun selectGreetingVariant(index: Int) {
        host.scope.launch {
            val conv = host.uiFlow.value.activeConversation ?: return@launch
            if (host.container.chatStreamManager.isStreaming(conv.id)) return@launch
            val card = conv.characterCardId?.let { assetDao.getCharacterCardById(it) }
            val persona = conv.personaId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = conv,
                card = card,
                persona = persona,
                selectedVariantIndex = index,
            )
        }
    }

    fun selectConversationPersona(personaId: String?) {
        host.scope.launch {
            val activeId = host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return@launch
            if (host.container.chatStreamManager.isStreaming(activeId)) return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val normalizedId = personaId?.trim()?.takeIf { it.isNotEmpty() }
            val updated = fresh.copy(personaId = normalizedId, updatedAt = System.currentTimeMillis())
            chatDao.upsertConversation(updated.toEntity())
            val card = updated.characterCardId?.let { assetDao.getCharacterCardById(it) }
            val persona = normalizedId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = updated,
                card = card,
                persona = persona,
                selectedVariantIndex = null,
            )
            host.scheduleWaterline()
        }
    }

    fun selectConversationPreset(presetId: String?) {
        host.scope.launch {
            val activeId = host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val normalizedId = presetId?.trim()?.takeIf { it.isNotEmpty() }
            chatDao.upsertConversation(
                fresh.copy(presetId = normalizedId, updatedAt = System.currentTimeMillis()).toEntity(),
            )
            host.scheduleWaterline()
        }
    }

    fun togglePromptPresetBlockEnabled(presetId: String, identifier: String, index: Int) {
        host.scope.launch {
            val preset = assetDao.getPromptPresetById(presetId) ?: return@launch
            val blocks = runCatching {
                AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), preset.blocksJson)
            }.getOrDefault(emptyList())
            if (index !in blocks.indices) return@launch
            if (identifier.isNotBlank() && blocks[index].identifier != identifier) return@launch
            val updated = PromptPresetBlockOps.toggleBlock(blocks, index)
            if (updated == blocks) return@launch
            val encoded = AppJson.encodeToString(ListSerializer(PromptPresetBlock.serializer()), updated)
            assetDao.upsertPromptPreset(
                preset.copy(blocksJson = encoded, updatedAt = System.currentTimeMillis()),
            )
            host.scheduleWaterline()
        }
    }

    fun setPromptPresetBlocksEnabled(presetId: String, indices: List<Int>, enabled: Boolean) {
        if (indices.isEmpty()) return
        host.scope.launch {
            val preset = assetDao.getPromptPresetById(presetId) ?: return@launch
            val blocks = runCatching {
                AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), preset.blocksJson)
            }.getOrDefault(emptyList())
            val updated = PromptPresetBlockOps.batchSetEnabled(blocks, indices, enabled)
            if (updated === blocks || updated == blocks) return@launch
            val encoded = AppJson.encodeToString(ListSerializer(PromptPresetBlock.serializer()), updated)
            assetDao.upsertPromptPreset(
                preset.copy(blocksJson = encoded, updatedAt = System.currentTimeMillis()),
            )
            host.scheduleWaterline()
        }
    }

    fun movePromptPresetBlock(presetId: String, index: Int, delta: Int) {
        if (delta == 0) return
        host.scope.launch {
            val preset = assetDao.getPromptPresetById(presetId) ?: return@launch
            val blocks = runCatching {
                AppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), preset.blocksJson)
            }.getOrDefault(emptyList())
            val updated = PromptPresetBlockOps.moveBlock(blocks, index, delta)
            if (updated === blocks || updated == blocks) return@launch
            val encoded = AppJson.encodeToString(ListSerializer(PromptPresetBlock.serializer()), updated)
            assetDao.upsertPromptPreset(
                preset.copy(blocksJson = encoded, updatedAt = System.currentTimeMillis()),
            )
            host.scheduleWaterline()
        }
    }

    fun togglePromptPresetRegexEnabled(presetId: String, scriptId: String, enabled: Boolean) {
        host.scope.launch {
            val preset = assetDao.getPromptPresetById(presetId) ?: return@launch
            val scripts = runCatching {
                AppJson.decodeFromString(ListSerializer(RpRegexScript.serializer()), preset.regexScriptsJson)
            }.getOrDefault(emptyList())
            val updated = scripts.map { s ->
                if (s.id == scriptId) s.copy(enabled = enabled) else s
            }
            if (updated == scripts) return@launch
            val encoded = AppJson.encodeToString(ListSerializer(RpRegexScript.serializer()), updated)
            assetDao.upsertPromptPreset(
                preset.copy(regexScriptsJson = encoded, updatedAt = System.currentTimeMillis()),
            )
            host.scheduleWaterline()
        }
    }

    fun mountWorldbook(worldbookId: String, convId: String? = null) {
        val targetId = convId ?: host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return
        host.scope.launch {
            val existing = assetDao.getMountedWorldbookRefs(targetId)
            if (existing.any { it.worldbookId == worldbookId }) {
                val updated = existing.map {
                    if (it.worldbookId == worldbookId) it.copy(enabled = true, updatedAt = System.currentTimeMillis()) else it
                }
                assetDao.replaceMountedWorldbooks(targetId, updated)
            } else {
                val nextSort = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val added = ConversationWorldbookCrossRef(
                    conversationId = targetId,
                    worldbookId = worldbookId,
                    sortOrder = nextSort,
                    enabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
                assetDao.replaceMountedWorldbooks(targetId, existing + added)
            }
            host.scheduleWaterline()
        }
    }

    fun unmountWorldbook(worldbookId: String) {
        val targetId = host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return
        host.scope.launch {
            val remaining = assetDao.getMountedWorldbookRefs(targetId)
                .filterNot { it.worldbookId == worldbookId }
                .mapIndexed { idx, ref -> ref.copy(sortOrder = idx) }
            assetDao.replaceMountedWorldbooks(targetId, remaining)
            host.scheduleWaterline()
        }
    }

    fun toggleMountedWorldbookEnabled(worldbookId: String) {
        val targetId = host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return
        host.scope.launch {
            val updated = assetDao.getMountedWorldbookRefs(targetId).map { ref ->
                if (ref.worldbookId == worldbookId) {
                    ref.copy(enabled = !ref.enabled, updatedAt = System.currentTimeMillis())
                } else {
                    ref
                }
            }
            assetDao.replaceMountedWorldbooks(targetId, updated)
            host.scheduleWaterline()
        }
    }

    fun saveV2LoreEntryForActiveConversation(
        existingLegacyId: String?,
        category: String,
        title: String,
        keywordsRaw: String,
        secondaryKeysRaw: String,
        selectiveLogic: Int,
        useDepthInjection: Boolean,
        depth: Int,
        sticky: Int,
        cooldown: Int,
        delay: Int,
        content: String,
        alwaysActive: Boolean,
    ) {
        val st = host.uiFlow.value
        val conv = st.activeConversation ?: return
        val hasV2Triggers = useDepthInjection ||
            secondaryKeysRaw.trim().isNotEmpty() ||
            sticky > 0 ||
            cooldown > 0 ||
            delay > 0
        if (!hasV2Triggers) {
            if (existingLegacyId != null) {
                val existing = st.novelLore.firstOrNull { it.id == existingLegacyId }
                if (existing != null) {
                    val parsedKeys = keywordsRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                    host.updateNovelLore(
                        existing.copy(
                            category = category,
                            title = title.trim(),
                            keywords = parsedKeys.ifEmpty { listOf(title.trim()) },
                            content = content.trim(),
                            alwaysActive = alwaysActive,
                        ),
                    )
                    return
                }
            }
            host.upsertNovelLore(category, title, keywordsRaw, content, alwaysActive)
            return
        }

        host.scope.launch {
            val now = System.currentTimeMillis()
            val wbId = "conv_v2_wb_${conv.id}"
            val primaryKeys = keywordsRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { if (alwaysActive) emptyList() else listOf(title.trim()) }
            val secondaryKeys = secondaryKeysRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
            val legacyIdToDelete = existingLegacyId?.takeIf { it.isNotBlank() }
            val entryId = legacyIdToDelete ?: UUID.randomUUID().toString()

            val wbEntries = host.container.database.withTransaction {
                val existingWb = assetDao.getWorldbookById(wbId)
                val wb = existingWb ?: WorldbookEntity(
                    id = wbId,
                    name = "${conv.title.take(18)} · 高级词条",
                    description = "Session V2 worldbook triggers",
                    sourceFormat = "gugachat_v2",
                    globalEnabled = false,
                    createdAt = now,
                    updatedAt = now,
                )
                if (existingWb == null) {
                    assetDao.upsertWorldbook(wb)
                }
                val entry = WorldbookEntryEntity(
                    id = entryId,
                    worldbookId = wbId,
                    uid = entryId.hashCode(),
                    title = title.trim(),
                    content = content.trim(),
                    category = category,
                    keysJson = AppJson.encodeToString(ListSerializer(String.serializer()), primaryKeys),
                    secondaryKeysJson = AppJson.encodeToString(ListSerializer(String.serializer()), secondaryKeys),
                    selectiveLogic = selectiveLogic.coerceIn(0, 3),
                    constant = alwaysActive,
                    enabled = true,
                    position = if (useDepthInjection) WorldbookEngine.POSITION_AT_DEPTH else WorldbookEngine.POSITION_BEFORE_CHAR,
                    depth = depth.coerceIn(0, 16),
                    sticky = sticky.coerceAtLeast(0),
                    cooldown = cooldown.coerceAtLeast(0),
                    delay = delay.coerceAtLeast(0),
                    estimatedTokens = TokenEstimator.estimateItem(title.trim(), content.trim(), overhead = 8, minTokens = 8),
                    createdAt = now,
                    updatedAt = now,
                )
                assetDao.upsertWorldbookEntry(entry)
                val entries = assetDao.getWorldbookEntries(wbId)
                assetDao.upsertWorldbook(
                    wb.copy(
                        entryCount = entries.size,
                        totalEstimatedTokens = entries.sumOf { it.estimatedTokens },
                        updatedAt = now,
                    ),
                )
                val refs = assetDao.getMountedWorldbookRefs(conv.id)
                if (refs.none { it.worldbookId == wbId }) {
                    assetDao.replaceMountedWorldbooks(
                        conv.id,
                        refs + ConversationWorldbookCrossRef(
                            conversationId = conv.id,
                            worldbookId = wbId,
                            sortOrder = (refs.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                            enabled = true,
                            updatedAt = now,
                        ),
                    )
                }
                if (legacyIdToDelete != null) {
                    chatDao.deleteNovelLore(legacyIdToDelete)
                }
                entries
            }
            if (legacyIdToDelete != null) {
                host.container.retrievalCoordinator.deleteOwnerVector(
                    ownerType = RetrievalCoordinator.OWNER_NOVEL_LORE,
                    ownerId = legacyIdToDelete,
                    conversationId = conv.id,
                )
            }
            host.container.retrievalCoordinator.scheduleWorldbookIndexing(host.scope, wbId, wbEntries)
            host.scheduleWaterline()
        }
    }

    fun syncBoundCharacterCardFields() {
        val isZh = host.strings().language == AppLanguage.ZH
        host.scope.launch {
            val activeId = host.activeConvIdFlow.value ?: host.uiFlow.value.activeConversation?.id ?: return@launch
            if (host.container.chatStreamManager.isStreaming(activeId)) return@launch
            val fresh = chatDao.getConversationById(activeId)?.toDomain() ?: return@launch
            val rp = fresh.rp ?: return@launch
            val card = fresh.characterCardId?.let { assetDao.getCharacterCardById(it) } ?: return@launch
            val cardCast = card.decodedCast()
            val cardExpr = card.decodedExpressionSprites()
            val cardRegex = card.decodedRegexScripts()
            val altGreetings = card.decodedAltGreetings()

            val leadCardChar = cardCast.firstOrNull { !it.isPlayer }
            val updatedCast = rp.cast.mapIndexed { idx, ch ->
                val src = cardCast.firstOrNull { it.id == ch.id }
                    ?: cardCast.firstOrNull { it.name.equals(ch.name, ignoreCase = true) }
                    ?: if (!ch.isPlayer && idx == 0) leadCardChar else null
                if (src != null) {
                    ch.copy(
                        appearance = src.appearance.ifBlank { ch.appearance },
                        personality = src.personality.ifBlank { ch.personality },
                        background = src.background.ifBlank { ch.background },
                        avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: src.avatarPath ?: card.avatarPath,
                        spritePath = ch.spritePath?.takeIf { it.isNotBlank() } ?: src.spritePath ?: card.spritePath,
                        expressionSprites = ch.expressionSprites.ifEmpty {
                            src.expressionSprites.ifEmpty { cardExpr }
                        },
                    )
                } else {
                    ch
                }
            }
            val syncedRp = rp.copy(
                worldview = card.scenario.ifBlank { rp.worldview },
                systemRules = card.systemPrompt.ifBlank { rp.systemRules },
                narratorStyle = card.creatorNotes.ifBlank { rp.narratorStyle },
                authorNote = card.depthPromptText.ifBlank { rp.authorNote },
                authorNoteDepth = card.depthPromptDepth,
                postHistoryInstructions = card.postHistoryInstructions.ifBlank { rp.postHistoryInstructions },
                mesExample = card.mesExample.ifBlank { rp.mesExample },
                greeting = card.firstMes.ifBlank { rp.greeting },
                alternateGreetings = altGreetings.ifEmpty { rp.alternateGreetings },
                regexScripts = cardRegex.ifEmpty { rp.regexScripts },
                cast = updatedCast,
            )
            chatDao.upsertConversation(
                fresh.copy(rp = syncedRp, updatedAt = System.currentTimeMillis()).toEntity(),
            )
            val persona = fresh.personaId?.let { assetDao.getPersonaById(it) }
            refreshOpeningGreetingMessage(
                conv = fresh.copy(rp = syncedRp),
                card = card,
                persona = persona,
                selectedVariantIndex = null,
            )
            host.container.toastController.show(
                if (isZh) "已同步角色卡「${card.name}」最新设定" else "Synced latest fields from \"${card.name}\"",
            )
        }
    }

    suspend fun refreshOpeningGreetingMessage(
        conv: ChatConversation,
        card: CharacterCardEntity?,
        persona: PersonaEntity?,
        selectedVariantIndex: Int? = null,
    ) {
        val allMsgs = chatDao.getMessagesForConversation(conv.id).map { it.toDomain() }
        val active = TimelineReconciler.activeMessages(allMsgs)
        if (active.any { it.role == "user" }) return
        val greetingMsg = active.firstOrNull { it.role == "assistant" && it.seq == 1 } ?: return

        val rawGreetings = if (card != null) {
            val alts = card.decodedAltGreetings()
            (listOf(card.firstMes) + alts).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val rp = conv.rp
            if (rp != null) {
                (listOf(rp.greeting) + rp.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        }
        if (rawGreetings.isEmpty()) return

        val charName = card?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.rp?.cast?.firstOrNull { !it.isPlayer }?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.title.trim().ifBlank { "角色" }
        val userName = persona?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: conv.rp?.playerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "旅人"

        // When explicitly picking an existing variant from the greeting picker and the message
        // already holds at least all card greetings, switch in-place to preserve manual edits and
        // any AI-regenerated extra variants.
        if (selectedVariantIndex != null &&
            greetingMsg.variants.size >= rawGreetings.size &&
            selectedVariantIndex in greetingMsg.variants.indices
        ) {
            val chosen = greetingMsg.variants[selectedVariantIndex]
            chatDao.upsertMessage(
                greetingMsg.copy(
                    content = chosen.content,
                    reasoning = chosen.reasoning,
                    reasoningDurationMs = chosen.reasoningDurationMs ?: chosen.usage?.reasoningDurationMs,
                    usage = chosen.usage,
                    model = chosen.model ?: greetingMsg.model,
                    status = "done",
                    error = null,
                    rpChoices = chosen.rpChoices,
                    activeVariantIndex = selectedVariantIndex,
                    tokenEstimate = ContextBuilder.estimateMessageTokens(chosen.content),
                ).toEntity(),
            )
            return
        }

        val baseRp = conv.rp
        val leadSpeaker = baseRp?.cast?.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() } ?: charName
        val now = System.currentTimeMillis()
        val cardVariants = rawGreetings.mapIndexed { idx, raw ->
            val withRpMacros = if (baseRp != null) {
                val effectiveCast = if (baseRp.cast.any { !it.isPlayer }) {
                    baseRp.cast
                } else {
                    listOf(RpCharacter(id = "lead_macro", name = charName))
                }
                RpEngine.replaceMacros(raw, baseRp.copy(playerName = userName, cast = effectiveCast))
            } else {
                raw
            }
            val rendered = ContextBuilder.expandStaticMacros(withRpMacros, charName, userName)
            val prev = greetingMsg.variants.getOrNull(idx)
            MessageVariant(
                id = prev?.id ?: UUID.randomUUID().toString(),
                content = rendered,
                createdAt = prev?.createdAt ?: now,
                rpStateSnapshot = prev?.rpStateSnapshot ?: baseRp?.state,
                rpCastSnapshot = prev?.rpCastSnapshot ?: baseRp?.cast,
                rpChoices = prev?.rpChoices ?: baseRp?.pendingChoices.orEmpty(),
                rpSpeakerName = prev?.rpSpeakerName ?: leadSpeaker,
            )
        }
        val extraVariants = if (greetingMsg.variants.size > rawGreetings.size) {
            greetingMsg.variants.drop(rawGreetings.size)
        } else {
            emptyList()
        }
        val variants = cardVariants + extraVariants
        val activeIdx = (selectedVariantIndex ?: greetingMsg.activeVariantIndex).coerceIn(0, variants.lastIndex)
        val selectedContent = variants[activeIdx].content
        chatDao.upsertMessage(
            greetingMsg.copy(
                content = selectedContent,
                variants = variants,
                activeVariantIndex = activeIdx,
                tokenEstimate = ContextBuilder.estimateMessageTokens(selectedContent),
            ).toEntity(),
        )
    }

    // ==================== RP / Novel Artwork Binding & AI Generation ====================

    fun bindRpCharacterAvatar(charId: String, uri: Uri, contentResolver: ContentResolver) {
        host.scope.launch {
            try {
                val savedPath = saveImageUriToRelative(uri, contentResolver, prefix = "avatar", transcodeNonPng = true)
                host.updateRpConfig { cfg ->
                    cfg.copy(
                        cast = cfg.cast.map { c ->
                            if (c.id == charId) c.copy(avatarPath = savedPath) else c
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun bindRpCharacterSprite(
        charId: String,
        expressionKey: String?,
        uri: Uri,
        contentResolver: ContentResolver,
    ) {
        host.scope.launch {
            try {
                val savedPath = saveImageUriToRelative(uri, contentResolver, prefix = "sprite")
                val exprKey = expressionKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                host.updateRpConfig { cfg ->
                    cfg.copy(
                        cast = cfg.cast.map { c ->
                            if (c.id != charId) {
                                c
                            } else if (exprKey == null) {
                                c.copy(
                                    spritePath = savedPath,
                                    avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: savedPath,
                                )
                            } else {
                                c.copy(
                                    expressionSprites = c.expressionSprites + (exprKey to savedPath),
                                    spritePath = if (exprKey == "neutral" && c.spritePath.isNullOrBlank()) savedPath else c.spritePath,
                                    avatarPath = if (exprKey == "neutral" && c.avatarPath.isNullOrBlank()) savedPath else c.avatarPath,
                                )
                            }
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearRpCharacterSprite(charId: String, expressionKey: String?) {
        val exprKey = expressionKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        host.updateRpConfig { cfg ->
            cfg.copy(
                cast = cfg.cast.map { c ->
                    if (c.id != charId) {
                        c
                    } else if (exprKey == null) {
                        c.copy(spritePath = null)
                    } else {
                        c.copy(
                            expressionSprites = c.expressionSprites.filterKeys { !it.equals(exprKey, ignoreCase = true) },
                        )
                    }
                },
            )
        }
    }

    fun bindRpSceneImage(uri: Uri, contentResolver: ContentResolver) {
        host.scope.launch {
            try {
                val savedPath = saveImageUriToRelative(uri, contentResolver, prefix = "scene")
                host.updateRpConfig { cfg ->
                    cfg.copy(state = cfg.state.copy(sceneImagePath = savedPath, activeCgId = null))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearRpSceneImage() {
        host.updateRpConfig { cfg ->
            cfg.copy(state = cfg.state.copy(sceneImagePath = null, activeCgId = null))
        }
    }

    fun addRpCgFromUri(title: String, caption: String, uri: Uri, contentResolver: ContentResolver) {
        host.scope.launch {
            try {
                val savedPath = saveImageUriToRelative(uri, contentResolver, prefix = "cg")
                val tipSeq = TimelineReconciler.findActiveTipSeq(host.uiFlow.value.messages)
                host.updateRpConfig { cfg ->
                    val resolvedTitle = title.trim().ifBlank {
                        cfg.state.scene.ifBlank { "CG #${cfg.state.unlockedCgs.size + 1}" }
                    }
                    val record = RpCgRecord(
                        id = UUID.randomUUID().toString(),
                        title = resolvedTitle,
                        caption = caption.trim(),
                        imagePath = savedPath,
                        scene = cfg.state.scene,
                        characterNames = cfg.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                        unlockedAt = System.currentTimeMillis(),
                        turnSeq = tipSeq,
                    )
                    cfg.copy(state = cfg.state.copy(unlockedCgs = cfg.state.unlockedCgs + record))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun saveCurrentSceneAsCg(title: String) {
        val tipSeq = TimelineReconciler.findActiveTipSeq(host.uiFlow.value.messages)
        host.updateRpConfig { cfg ->
            val scenePath = cfg.state.sceneImagePath?.trim()?.takeIf { it.isNotEmpty() } ?: return@updateRpConfig cfg
            if (cfg.state.unlockedCgs.any { it.imagePath == scenePath }) {
                return@updateRpConfig cfg
            }
            val resolvedTitle = title.trim().ifBlank {
                cfg.state.scene.ifBlank { "CG #${cfg.state.unlockedCgs.size + 1}" }
            }
            val caption = listOf(cfg.state.time, cfg.state.weather, cfg.state.mood)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val record = RpCgRecord(
                id = UUID.randomUUID().toString(),
                title = resolvedTitle,
                caption = caption,
                imagePath = scenePath,
                scene = cfg.state.scene,
                characterNames = cfg.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                unlockedAt = System.currentTimeMillis(),
                turnSeq = tipSeq,
            )
            cfg.copy(
                state = cfg.state.copy(
                    unlockedCgs = cfg.state.unlockedCgs + record,
                    activeCgId = record.id,
                ),
            )
        }
    }

    fun applyCgAsScene(cg: RpCgRecord) {
        host.updateRpConfig { cfg ->
            cfg.copy(state = cfg.state.copy(sceneImagePath = cg.imagePath, activeCgId = cg.id))
        }
    }

    fun deleteRpCg(cgId: String) {
        host.updateRpConfig { cfg ->
            cfg.copy(
                state = cfg.state.copy(
                    unlockedCgs = cfg.state.unlockedCgs.filterNot { it.id == cgId },
                    activeCgId = if (cfg.state.activeCgId == cgId) null else cfg.state.activeCgId,
                ),
            )
        }
    }

    // ==================== Studio Art Model Selection ====================

    fun openStudioArtModelPicker(pending: PendingStudioArtRequest? = null) {
        host.uiFlow.update { st ->
            st.copy(
                studioArtModel = st.studioArtModel.copy(
                    showPicker = true,
                    pendingRequest = pending ?: st.studioArtModel.pendingRequest,
                ),
            )
        }
        refreshStudioArtChannels()
    }

    fun dismissStudioArtModelPicker() {
        host.uiFlow.update { st ->
            st.copy(
                studioArtModel = st.studioArtModel.copy(
                    showPicker = false,
                    pendingRequest = null,
                ),
            )
        }
    }

    fun refreshStudioArtChannels() {
        host.scope.launch {
            host.uiFlow.update { st ->
                st.copy(studioArtModel = st.studioArtModel.copy(isLoadingChannels = true))
            }
            try {
                val resolved = host.container.imageChannelResolver.resolveSelectableArtChannels()
                val channels = resolved.allKeys
                val initialSeedModels = channels.associate { key -> key.id to key.models }
                host.uiFlow.update { st ->
                    val merged = st.studioArtModel.modelsByKeyId.toMutableMap()
                    initialSeedModels.forEach { (k, v) ->
                        if (merged[k].isNullOrEmpty() && v.isNotEmpty()) merged[k] = v
                    }
                    st.copy(
                        studioArtModel = st.studioArtModel.copy(
                            channels = channels,
                            modelsByKeyId = merged,
                            isLoadingChannels = false,
                        ),
                    )
                }
                val cur = host.uiFlow.value.studioArtModel
                val keyToLoad = channels.firstOrNull { it.id == cur.selectedKeyId && cur.selectedKeyId != 0L }
                    ?: channels.firstOrNull { it.id == cur.galleryFallbackKeyId && cur.galleryFallbackKeyId != 0L }
                    ?: channels.firstOrNull()
                if (keyToLoad != null) {
                    loadStudioArtModelsForKey(keyToLoad.id)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                host.uiFlow.update { st ->
                    st.copy(studioArtModel = st.studioArtModel.copy(isLoadingChannels = false))
                }
                host.container.toastController.show(e.message ?: host.strings().common.unknownError, isError = true)
            }
        }
    }

    fun loadStudioArtModelsForKey(keyId: Long) {
        if (keyId == 0L) return
        val key = host.uiFlow.value.studioArtModel.channels.firstOrNull { it.id == keyId } ?: return
        host.scope.launch {
            host.uiFlow.update { st ->
                st.copy(studioArtModel = st.studioArtModel.copy(loadingModelsKeyId = keyId))
            }
            try {
                val sorted = host.container.imageChannelResolver.resolveModelsForKey(key)
                host.uiFlow.update { st ->
                    val nextLoading = if (st.studioArtModel.loadingModelsKeyId == keyId) null else st.studioArtModel.loadingModelsKeyId
                    st.copy(
                        studioArtModel = st.studioArtModel.copy(
                            modelsByKeyId = st.studioArtModel.modelsByKeyId + (keyId to sorted),
                            loadingModelsKeyId = nextLoading,
                        ),
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                host.uiFlow.update { st ->
                    val nextLoading = if (st.studioArtModel.loadingModelsKeyId == keyId) null else st.studioArtModel.loadingModelsKeyId
                    st.copy(studioArtModel = st.studioArtModel.copy(loadingModelsKeyId = nextLoading))
                }
            }
        }
    }

    fun saveStudioArtModelSelection(
        keyId: Long,
        modelId: String,
        channelName: String,
        runPending: Boolean = true,
    ) {
        val cleanModel = modelId.trim()
        if (keyId == 0L || cleanModel.isEmpty()) {
            host.container.toastController.show(host.strings().rp.aiArtModelRequiredToast, isError = true)
            return
        }
        val cleanChannel = channelName.trim().ifBlank {
            host.uiFlow.value.studioArtModel.channels.firstOrNull { it.id == keyId }?.name.orEmpty()
        }
        val pending = if (runPending) host.uiFlow.value.studioArtModel.pendingRequest else null
        host.uiFlow.update { st ->
            st.copy(
                studioArtModel = st.studioArtModel.copy(
                    selectedKeyId = keyId,
                    selectedModelId = cleanModel,
                    selectedChannelName = cleanChannel,
                    showPicker = false,
                    pendingRequest = null,
                ),
            )
        }
        host.scope.launch {
            host.container.settingsStore.setStudioArtModelSelection(
                keyId = keyId,
                model = cleanModel,
                channelName = cleanChannel,
            )
            if (pending == null) {
                host.container.toastController.show(
                    host.strings().rp.aiArtModelSavedToast.fmt("model" to cleanModel),
                )
            } else {
                when (pending) {
                    is PendingStudioArtRequest.RpSceneCg -> generateRpSceneCg(
                        customHint = pending.customHint,
                        saveToGallery = pending.saveToGallery,
                        explicitKeyId = keyId,
                        explicitModelId = cleanModel,
                    )
                    is PendingStudioArtRequest.RpCharacterSprite -> generateRpCharacterSprite(
                        charId = pending.charId,
                        expressionKey = pending.expressionKey,
                        customHint = pending.customHint,
                        explicitKeyId = keyId,
                        explicitModelId = cleanModel,
                    )
                    PendingStudioArtRequest.NovelCover -> generateNovelCoverArt(
                        explicitKeyId = keyId,
                        explicitModelId = cleanModel,
                    )
                }
            }
        }
    }

    fun clearStudioArtModelSelection() {
        host.uiFlow.update { st ->
            st.copy(
                studioArtModel = st.studioArtModel.copy(
                    selectedKeyId = 0L,
                    selectedModelId = "",
                    selectedChannelName = "",
                ),
            )
        }
        host.scope.launch {
            host.container.settingsStore.clearStudioArtModelSelection()
        }
    }

    fun generateRpCharacterSprite(
        charId: String,
        expressionKey: String = "neutral",
        customHint: String = "",
        explicitKeyId: Long? = null,
        explicitModelId: String? = null,
    ) {
        val st = host.uiFlow.value
        if (st.isGeneratingRpArt) return
        val conv = st.activeConversation ?: return
        val rp = conv.rp ?: return
        val character = rp.cast.firstOrNull { it.id == charId } ?: return
        val exprKey = expressionKey.trim().lowercase().ifEmpty { "neutral" }
        val keyId = explicitKeyId ?: st.studioArtModel.selectedKeyId
        val modelId = (explicitModelId ?: st.studioArtModel.selectedModelId).trim()
        val pending = PendingStudioArtRequest.RpCharacterSprite(charId, exprKey, customHint)
        if (keyId == 0L || modelId.isEmpty()) {
            host.container.toastController.show(host.strings().rp.aiArtModelRequiredToast)
            openStudioArtModelPicker(pending = pending)
            return
        }
        val exprLabel = host.strings().rp.expressionLabels[exprKey] ?: exprKey
        val statusLabel = "${host.strings().rp.aiGeneratingArt} (${character.name} · $exprLabel · $modelId)"

        launchRpArtGeneration(statusLabel, pendingOnUnconfigured = pending) {
            val prompt = RpEngine.buildPortraitImagePrompt(
                rp = rp,
                character = character,
                expressionKey = exprKey,
                customHint = customHint,
            )
            val refPath = if (exprKey != "neutral") {
                character.expressionSprites["neutral"]?.takeIf { it.isNotBlank() }
                    ?: character.spritePath?.takeIf { it.isNotBlank() }
                    ?: character.avatarPath?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val generated = host.container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                prompt = prompt,
                aspectRatio = "3:4",
                referenceFilePath = refPath,
                noChannelMessage = host.strings().rp.aiGenArtNoChannel,
                modelUnconfiguredMessage = host.strings().rp.aiArtModelRequiredToast,
                overrideKeyId = keyId,
                overrideModelId = modelId,
            )

            val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            val freshRp = freshConv.rp ?: return@launchRpArtGeneration
            val updatedCast = freshRp.cast.map { c ->
                if (c.id != charId) {
                    c
                } else {
                    c.copy(
                        expressionSprites = c.expressionSprites + (exprKey to generated.relativePath),
                        spritePath = c.spritePath?.takeIf { it.isNotBlank() } ?: generated.relativePath,
                        avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: generated.relativePath,
                    )
                }
            }
            chatDao.upsertConversation(
                freshConv.copy(
                    rp = freshRp.copy(cast = updatedCast),
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            host.container.toastController.show(host.strings().rp.aiGenArtDone)
        }
    }

    fun generateRpSceneCg(
        customHint: String = "",
        saveToGallery: Boolean = true,
        explicitKeyId: Long? = null,
        explicitModelId: String? = null,
    ) {
        val st = host.uiFlow.value
        if (st.isGeneratingRpArt) return
        val conv = st.activeConversation ?: return
        val rp = conv.rp ?: return
        val keyId = explicitKeyId ?: st.studioArtModel.selectedKeyId
        val modelId = (explicitModelId ?: st.studioArtModel.selectedModelId).trim()
        val pending = PendingStudioArtRequest.RpSceneCg(customHint, saveToGallery)
        if (keyId == 0L || modelId.isEmpty()) {
            host.container.toastController.show(host.strings().rp.aiArtModelRequiredToast)
            openStudioArtModelPicker(pending = pending)
            return
        }
        val sceneName = rp.state.scene.ifBlank { rp.title }
        val statusLabel = "${host.strings().rp.aiGeneratingArt} ($sceneName · $modelId)"

        launchRpArtGeneration(statusLabel, pendingOnUnconfigured = pending) {
            val prompt = RpEngine.buildSceneImagePrompt(rp = rp, customHint = customHint)
            val generated = host.container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                prompt = prompt,
                aspectRatio = "9:16",
                referenceFilePath = null,
                noChannelMessage = host.strings().rp.aiGenArtNoChannel,
                modelUnconfiguredMessage = host.strings().rp.aiArtModelRequiredToast,
                overrideKeyId = keyId,
                overrideModelId = modelId,
            )

            val freshConv = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            val freshRp = freshConv.rp ?: return@launchRpArtGeneration
            val tipSeq = TimelineReconciler.findActiveTipSeq(host.uiFlow.value.messages)
            val cgRecord = if (saveToGallery) {
                val cgTitle = customHint.trim().take(24).ifBlank {
                    freshRp.state.scene.ifBlank { "${freshRp.title} CG #${freshRp.state.unlockedCgs.size + 1}" }
                }
                val caption = customHint.trim().ifBlank {
                    listOf(freshRp.state.time, freshRp.state.weather, freshRp.state.mood)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                }
                RpCgRecord(
                    id = UUID.randomUUID().toString(),
                    title = cgTitle,
                    caption = caption,
                    imagePath = generated.relativePath,
                    scene = freshRp.state.scene,
                    characterNames = freshRp.cast.filter { !it.isPlayer && it.inScene }.map { it.name },
                    unlockedAt = System.currentTimeMillis(),
                    turnSeq = tipSeq,
                )
            } else {
                null
            }
            val nextState = freshRp.state.copy(
                sceneImagePath = generated.relativePath,
                unlockedCgs = if (cgRecord != null) freshRp.state.unlockedCgs + cgRecord else freshRp.state.unlockedCgs,
                activeCgId = cgRecord?.id ?: freshRp.state.activeCgId,
            )
            chatDao.upsertConversation(
                freshConv.copy(
                    rp = freshRp.copy(state = nextState),
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(),
            )
            host.container.toastController.show(host.strings().rp.aiGenArtDone)
        }
    }

    fun bindNovelCoverImage(uri: Uri, contentResolver: ContentResolver) {
        val conv = host.uiFlow.value.activeConversation ?: return
        if (conv.novel == null) return
        host.scope.launch {
            try {
                val relPath = saveImageUriToRelative(uri, contentResolver, prefix = "novel_cover")
                val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
                fresh.novel?.let { n ->
                    chatDao.upsertConversation(fresh.copy(novel = n.copy(coverImagePath = relPath), updatedAt = System.currentTimeMillis()).toEntity())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().chat.invalidImage, isError = true)
            }
        }
    }

    fun clearNovelCoverImage() {
        val conv = host.uiFlow.value.activeConversation ?: return
        host.scope.launch {
            val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            fresh.novel?.let { n ->
                chatDao.upsertConversation(fresh.copy(novel = n.copy(coverImagePath = null), updatedAt = System.currentTimeMillis()).toEntity())
            }
        }
    }

    fun generateNovelCoverArt(
        explicitKeyId: Long? = null,
        explicitModelId: String? = null,
    ) {
        val st = host.uiFlow.value
        if (st.isGeneratingRpArt) return
        val conv = st.activeConversation ?: return
        val novel = conv.novel ?: return
        val keyId = explicitKeyId ?: st.studioArtModel.selectedKeyId
        val modelId = (explicitModelId ?: st.studioArtModel.selectedModelId).trim()
        if (keyId == 0L || modelId.isEmpty()) {
            host.container.toastController.show(host.strings().rp.aiArtModelRequiredToast)
            openStudioArtModelPicker(pending = PendingStudioArtRequest.NovelCover)
            return
        }
        val isZh = host.strings().language == AppLanguage.ZH
        val statusLabel = if (isZh) "正在绘制书封 (${novel.title} · $modelId)" else "Generating cover (${novel.title} · $modelId)"
        launchRpArtGeneration(statusLabel, pendingOnUnconfigured = PendingStudioArtRequest.NovelCover) {
            val prompt = buildString {
                append("Vertical book cover illustration, high quality literary artwork, no text, no watermark. ")
                append("Title mood: ").append(novel.title).append(". ")
                if (novel.genre.isNotBlank()) append("Genre: ").append(novel.genre).append(". ")
                if (novel.synopsis.isNotBlank()) append("Premise: ").append(novel.synopsis.take(260)).append(". ")
                if (novel.worldview.isNotBlank()) append("Worldview: ").append(novel.worldview.take(200))
            }
            val generated = host.container.studioImageGenerationUseCase.generateAndSaveRpMedia(
                prompt = prompt,
                aspectRatio = "3:4",
                prefix = "novel_cover",
                noChannelMessage = host.strings().rp.aiGenArtNoChannel,
                modelUnconfiguredMessage = host.strings().rp.aiArtModelRequiredToast,
                overrideKeyId = keyId,
                overrideModelId = modelId,
            )
            val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
            fresh.novel?.let { n ->
                chatDao.upsertConversation(
                    fresh.copy(novel = n.copy(coverImagePath = generated.relativePath), updatedAt = System.currentTimeMillis()).toEntity(),
                )
            }
            host.container.toastController.show(if (isZh) "书封已生成并保存" else "Book cover generated")
        }
    }

    // ==================== Private Helpers ====================

    private suspend fun launchImportedCardConversation(imported: AssetImportResult.CharacterCardImported) {
        val st = host.uiFlow.value
        val convId = withContext(Dispatchers.IO) {
            host.container.campaignLaunchUseCase.insertRpConversation(
                rp = imported.rpConfig,
                importedLore = emptyList(),
                characterCardId = imported.card.id,
                mountedWorldbookId = imported.worldbook?.id,
                groupId = st.selectedGroupId,
                modelId = st.selectedModelId,
            )
        }
        host.uiFlow.update { it.copy(draftText = "", draftAttachments = emptyList(), draftDiceRoll = null) }
        host.activeConvIdFlow.value = convId
    }

    private suspend fun mergeRegexIntoConvRp(
        conv: ChatConversation,
        scripts: List<RpRegexScript>,
        extraConvTransform: (ChatConversation) -> ChatConversation = { it },
    ): Int = host.container.database.withTransaction {
        val fresh = chatDao.getConversationById(conv.id)?.toDomain() ?: conv
        var added = 0
        val withRegex = if (scripts.isNotEmpty() && fresh.rp != null) {
            val cfg = fresh.rp
            val existingKeys = cfg.regexScripts.map { "${it.findRegex}|${it.replaceString}" }.toHashSet()
            val toAdd = scripts.filter { existingKeys.add("${it.findRegex}|${it.replaceString}") }
            added = toAdd.size
            if (toAdd.isEmpty()) fresh else fresh.copy(rp = cfg.copy(regexScripts = cfg.regexScripts + toAdd))
        } else {
            fresh
        }
        val transformed = extraConvTransform(withRegex)
        if (transformed != fresh) {
            chatDao.upsertConversation(transformed.copy(updatedAt = System.currentTimeMillis()).toEntity())
        }
        added
    }

    private fun mergeTextSection(existing: String, incoming: String): String {
        val cleanIncoming = incoming.trim()
        if (cleanIncoming.isEmpty()) return existing
        val cleanExisting = existing.trim()
        if (cleanExisting.isEmpty()) return cleanIncoming
        if (cleanExisting.contains(cleanIncoming)) return cleanExisting
        return "$cleanExisting\n\n$cleanIncoming"
    }

    private suspend fun saveImageUriToRelative(
        uri: Uri,
        contentResolver: ContentResolver,
        prefix: String,
        transcodeNonPng: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image")
        val useCase = host.container.studioImageGenerationUseCase
        when {
            RpCardCodec.isPngBytes(bytes) -> useCase.saveMediaBytesSync(bytes, prefix, "png")
            transcodeNonPng -> {
                val pngBytes = useCase.transcodeToPngBytes(bytes)
                if (pngBytes != null) {
                    useCase.saveMediaBytesSync(pngBytes, prefix, "png")
                } else {
                    useCase.saveMediaBytesSync(bytes, prefix, useCase.inferImageExt(contentResolver.getType(uri)))
                }
            }
            else -> useCase.saveMediaBytesSync(bytes, prefix, useCase.inferImageExt(contentResolver.getType(uri)))
        }
    }

    private fun launchRpArtGeneration(
        statusLabel: String,
        pendingOnUnconfigured: PendingStudioArtRequest? = null,
        block: suspend () -> Unit,
    ) {
        host.scope.launch {
            host.uiFlow.update { it.copy(isGeneratingRpArt = true, rpArtStatusLabel = statusLabel) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StudioArtModelUnconfiguredException) {
                host.container.toastController.show(e.message ?: host.strings().rp.aiArtModelRequiredToast)
                if (pendingOnUnconfigured != null) {
                    openStudioArtModelPicker(pending = pendingOnUnconfigured)
                }
            } catch (e: Throwable) {
                host.container.toastController.show(e.message ?: host.strings().common.unknownError, isError = true)
            } finally {
                host.uiFlow.update { it.copy(isGeneratingRpArt = false, rpArtStatusLabel = "") }
            }
        }
    }
}
