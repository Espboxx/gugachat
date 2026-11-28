package com.gugachat.app.domain.chat

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object ContextDepthInjector {
    private data class DepthInjectionItem(
        val depth: Int,
        val role: String,
        val text: String,
        val sortBucket: Int,
        val subOrder: Int,
    )

    suspend fun buildChatContextV4(input: ContextBuildInput): BuiltChatContextV4 {
        // Auto-hydrate preloadedFileChunks for active files when caller supplies resolveAttachmentText
        // without pre-populating AttachmentChunkCache (e.g. direct domain calls / JVM tests).
        val resolvedTextsById = HashMap<String, String>()
        val hydratedPreloaded = LinkedHashMap<String, IndexedDocument>(input.preloadedFileChunks)
        if (input.resolveAttachmentText != null) {
            val activeMsgs = TimelineReconciler.activeMessages(input.messages)
            val neededFileIds = LinkedHashSet<String>()
            for (msg in activeMsgs) {
                if (msg.role != "user") continue
                for (attId in msg.attachmentIds) {
                    if (input.attachmentsById[attId]?.isFile == true) {
                        neededFileIds.add(attId)
                    }
                }
            }
            for (df in input.draftAttachments) {
                if (df.isFile) neededFileIds.add(df.id)
            }
            for (attId in neededFileIds) {
                if (hydratedPreloaded.containsKey(attId)) continue
                val meta = input.attachmentsById[attId]
                    ?: input.draftAttachments.firstOrNull { it.id == attId }
                    ?: continue
                val rawText = input.resolveAttachmentText.invoke(attId)
                if (!rawText.isNullOrBlank()) {
                    resolvedTextsById[attId] = rawText
                    hydratedPreloaded[attId] = FileContextBudgeter.buildIndexedDocument(
                        attachmentId = attId,
                        fileName = meta.fileName,
                        text = rawText,
                    )
                }
            }
        }
        val effectiveInput = if (hydratedPreloaded.size != input.preloadedFileChunks.size) {
            input.copy(preloadedFileChunks = hydratedPreloaded)
        } else {
            input
        }

        val pipe = ContextRecallAssembler.runSharedPipeline(effectiveInput, allowScorer = true)
        val conv = effectiveInput.conversation
        val active = pipe.active
        val decayedImages = pipe.waterline.decayedImageMessageIds.toMutableSet()
        val retentionByAttId = pipe.fileRetentionByAttachmentId.toMutableMap()
        val decayedFiles = pipe.waterline.decayedFileMessageIds.toMutableSet()
        val fileTurnBudget = FileContextBudgeter.computeFileTurnBudget(
            effectiveLimitTokens = pipe.effectiveLimitTokens,
            reservedFixedTokens = pipe.prefix.systemTokens + pipe.dynamicOverhead,
        )

        fun msgCost(msg: ChatMessage): Int = ContextRecallAssembler.resolveMemoizedMsgTokenCost(
            msg = msg,
            imageDecayed = msg.id in decayedImages,
            fileDecayed = msg.id in decayedFiles,
            attachmentsById = effectiveInput.attachmentsById,
            perTurnFileBudget = fileTurnBudget,
            modelId = pipe.effectiveModelId,
            conversation = conv,
            cache = pipe.messageCacheById,
            fileRetentionByAttachmentId = retentionByAttId,
        )

        val maxHistoryLimit = conv.params.maxHistoryMessages?.takeIf { it > 0 }
        val candidateWindow = if (maxHistoryLimit != null && active.size > maxHistoryLimit) {
            active.takeLast(maxHistoryLimit)
        } else {
            active
        }

        val msgCosts = IntArray(candidateWindow.size) { idx ->
            msgCost(candidateWindow[idx])
        }
        var runningRawTokens = pipe.prefix.systemTokens + pipe.dynamicOverhead + msgCosts.sum()
        fun totalCalibratedTokens(): Int = (runningRawTokens * pipe.calibration).roundToInt()
        fun refreshMsgCost(idx: Int) {
            val newCost = msgCost(candidateWindow[idx])
            runningRawTokens += newCost - msgCosts[idx]
            msgCosts[idx] = newCost
        }

        var newlyDemotedTier2 = false
        if (totalCalibratedTokens() > pipe.effectiveLimitTokens) {
            for (i in 0 until max(0, candidateWindow.size - 1)) {
                val msg = candidateWindow[i]
                val hasImgs = msg.attachmentIds.any { effectiveInput.attachmentsById[it]?.isFile != true }
                var demotedAutoFile = false
                for (attId in msg.attachmentIds) {
                    val meta = effectiveInput.attachmentsById[attId]?.takeIf { it.isFile } ?: continue
                    if (meta.injectMode == FileInjectMode.auto && retentionByAttId[attId] == FileRetentionTier.FULL) {
                        retentionByAttId[attId] = FileRetentionTier.INDEX_RECALL
                        demotedAutoFile = true
                        newlyDemotedTier2 = true
                    }
                }
                if (demotedAutoFile) {
                    decayedFiles.add(msg.id)
                    refreshMsgCost(i)
                    if (totalCalibratedTokens() <= pipe.effectiveLimitTokens) break
                }
                if (hasImgs && msg.id !in decayedImages) {
                    decayedImages.add(msg.id)
                    refreshMsgCost(i)
                    if (totalCalibratedTokens() <= pipe.effectiveLimitTokens) break
                }
            }
        }

        var used = ((pipe.prefix.systemTokens + pipe.dynamicOverhead) * pipe.calibration).roundToInt()
        var rawSelectedCount = 0
        for (i in active.indices.reversed()) {
            if (maxHistoryLimit != null && rawSelectedCount >= maxHistoryLimit) break
            val msg = active[i]
            val rawCost = msgCost(msg)
            val msgTok = (rawCost * pipe.calibration).roundToInt()
            if (rawSelectedCount > 0 && used + msgTok > pipe.effectiveLimitTokens) break
            used += msgTok
            rawSelectedCount++
        }

        val selectedCount = ContextBudgetPolicy.computeSlidingWindowKeepCount(
            activeSize = active.size,
            rawKeptCount = rawSelectedCount,
            maxHistoryLimit = maxHistoryLimit,
        )
        val selected = if (selectedCount > 0) active.takeLast(selectedCount) else emptyList()
        val selectedIds = selected.map { it.id }.toSet()
        for (msg in active) {
            if (msg.id in selectedIds || msg.role != "user") continue
            for (attId in msg.attachmentIds) {
                if (effectiveInput.attachmentsById[attId]?.isFile == true && retentionByAttId[attId] != FileRetentionTier.INDEX_RECALL) {
                    retentionByAttId[attId] = FileRetentionTier.INDEX_RECALL
                    newlyDemotedTier2 = true
                }
            }
        }

        var selectedRawHistory = 0
        var selectedRawFiles = 0
        for (msg in selected) {
            selectedRawHistory += msgCost(msg)
            selectedRawFiles += ContextBudgetPolicy.resolveMsgFileTokens(
                msg = msg,
                fileDecayed = msg.id in decayedFiles,
                attachmentsById = effectiveInput.attachmentsById,
                perTurnFileBudget = fileTurnBudget,
                fileRetentionByAttachmentId = retentionByAttId,
            )
        }

        val wireMessages = ArrayList<WireChatMessage>()
        if (pipe.prefix.systemText.isNotBlank()) {
            wireMessages.add(WireChatMessage(role = "system", textContent = pipe.prefix.systemText))
        }

        val wbResult = pipe.worldbookResult
        val zone3StandardLore: List<NovelLoreEntry> = if (pipe.isV4Mode && wbResult != null) {
            wbResult.zone3TriggeredEntries
                .filter { !(it.position == WorldbookEngine.POSITION_AT_DEPTH && it.depth == 0) }
                .map { entry ->
                    val expanded = ContextZonePrefixBuilder.expandDynamicV4Macros(
                        entry.content.trim(),
                        conv,
                        effectiveInput.characterCard,
                        effectiveInput.persona,
                    )
                    NovelLoreEntry(
                        id = entry.id,
                        conversationId = conv.id,
                        title = entry.title,
                        category = entry.category,
                        content = expanded,
                        alwaysActive = entry.constant,
                        createdAt = 0L,
                        updatedAt = 0L,
                        estimatedTokens = entry.estimatedTokens,
                    )
                }
        } else {
            pipe.loreMatch.triggered
        }

        val lastUserIdx = selected.indexOfLast { it.role == "user" }
        val latestUserQuery = if (lastUserIdx >= 0) selected[lastUserIdx].content else effectiveInput.draftText
        val zone3TrailingSuffix = buildZone3TrailingSuffix(effectiveInput, pipe, wbResult, conv, latestUserQuery)

        val chatWorldbookTriggeredBlock = if (pipe.isV4Mode && !pipe.isRp && !pipe.isNovel && zone3StandardLore.isNotEmpty()) {
            val lines = zone3StandardLore.joinToString("\n") { entry ->
                "- [${entry.category}] ${entry.title}: ${entry.content.trim()}"
            }
            "<worldbook_triggered>\n$lines\n</worldbook_triggered>"
        } else {
            ""
        }

        val existingLoreTitles = if (pipe.isNovel) pipe.novelLore.map { it.title } else emptyList()

        // If pressure/sliding demoted additional files to Tier 2, recompute file recall bundle
        val effectiveFileRecallBundle = if (newlyDemotedTier2) {
            val orderedTier2Ids = LinkedHashSet<String>()
            for (msg in pipe.allActiveMessages) {
                if (msg.role != "user") continue
                for (attId in msg.attachmentIds) {
                    if (effectiveInput.attachmentsById[attId]?.isFile == true &&
                        retentionByAttId[attId] == FileRetentionTier.INDEX_RECALL
                    ) {
                        orderedTier2Ids.add(attId)
                    }
                }
            }
            val tier2Docs = orderedTier2Ids.mapNotNull { hydratedPreloaded[it] }
            val uploadIds = if (lastUserIdx >= 0) {
                selected[lastUserIdx].attachmentIds.filter { effectiveInput.attachmentsById[it]?.isFile == true }.toSet()
            } else {
                emptySet()
            }
            val baseDyn = pipe.dynamicOverhead - pipe.fileRecallBundle.recalledTokens
            val remAfterSys = max(0, pipe.effectiveLimitTokens - pipe.prefix.systemTokens - baseDyn)
            val recallBudget = FileContextBudgeter.computeFileRecallBudget(
                remainingAfterSystem = remAfterSys,
                memoryBudget = effectiveInput.memoryBudget,
                isUploadTurn = uploadIds.any { it in orderedTier2Ids },
            )
            val recentForQuery = if (lastUserIdx >= 0) {
                selected.filterIndexed { i, _ -> i != lastUserIdx }
            } else {
                selected
            }
            FileRecallEngine.recall(
                tier2Docs = tier2Docs,
                fileMetasById = effectiveInput.attachmentsById,
                uploadTurnFileIds = uploadIds,
                conversation = conv,
                recentMessages = recentForQuery,
                latestUserQuery = latestUserQuery,
                activeChapter = pipe.activeChapter,
                budgetTokens = recallBudget,
                semanticScores = pipe.resolvedSemanticScores,
                semanticScorer = null,
                minSemanticCosine = effectiveInput.minSemanticCosine,
                mutedRecallKeys = effectiveInput.mutedRecallKeys,
                modelId = pipe.effectiveModelId,
            )
        } else {
            pipe.fileRecallBundle
        }
        val recalledFileContextBlock = effectiveFileRecallBundle.recalledBlock

        fun appendTrailingZone3(baseText: String): String = buildList {
            if (baseText.isNotBlank()) add(baseText.trim())
            if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
        }.joinToString("\n\n")

        for (idx in selected.indices) {
            val msg = selected[idx]
            val rawContent = pipe.messageCacheById[msg.id]?.cleanedText
                ?: ContextBudgetPolicy.cleanAssistantHistoryText(msg, conv)

            val isFinalNovelUser = pipe.isNovel && idx == lastUserIdx
            val isFinalRpUser = pipe.isRp && idx == lastUserIdx
            val isFinalChatUser = !pipe.isNovel && !pipe.isRp && idx == lastUserIdx

            val finalText = when {
                isFinalNovelUser -> {
                    val wrapped = NovelEngine.wrapNovelTurnUserMessage(
                        rawContent = rawContent,
                        inputKind = msg.inputKind ?: effectiveInput.draftInputKind ?: NovelInputKind.director,
                        novel = conv.novel!!,
                        activeChapter = pipe.activeChapter,
                        triggeredLore = zone3StandardLore,
                        existingLoreTitles = existingLoreTitles,
                        archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                    )
                    val combined = buildList {
                        if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                        if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                        add(wrapped)
                    }.joinToString("\n\n")
                    appendTrailingZone3(combined)
                }
                isFinalRpUser -> {
                    val wrappedRp = RpEngine.wrapRpTurnUserMessage(
                        rawContent = rawContent,
                        inputKind = msg.rpInputKind ?: effectiveInput.draftRpInputKind ?: RpInputKind.action,
                        rp = conv.rp!!,
                        diceRoll = msg.rpDiceRoll,
                        activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                        recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                        archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                        triggeredLore = zone3StandardLore,
                        includeAuthorNote = conv.rp.authorNoteDepth <= 0,
                    )
                    val combined = buildList {
                        if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                        add(wrappedRp)
                    }.joinToString("\n\n")
                    appendTrailingZone3(combined)
                }
                isFinalChatUser -> buildList {
                    if (chatWorldbookTriggeredBlock.isNotBlank()) add(chatWorldbookTriggeredBlock)
                    if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                    if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                    if (rawContent.isNotBlank()) add(rawContent)
                    if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
                }.joinToString("\n\n")
                else -> rawContent
            }

            val attIds = msg.attachmentIds
            val fileMetas = if (msg.role == "user") {
                attIds.mapNotNull { effectiveInput.attachmentsById[it]?.takeIf { a -> a.isFile } }
            } else {
                emptyList()
            }
            val imageIds = if (msg.role == "user") {
                attIds.filter { id -> effectiveInput.attachmentsById[id]?.isFile != true }
            } else {
                emptyList()
            }

            val fileBlocksText = buildString {
                if (fileMetas.isNotEmpty()) {
                    val tier1Metas = fileMetas.filter { fm ->
                        (retentionByAttId[fm.id] ?: FileRetentionTier.FULL) == FileRetentionTier.FULL
                    }
                    val allocations = if (tier1Metas.isNotEmpty()) {
                        FileContextBudgeter.allocateFileBudgets(tier1Metas, fileTurnBudget)
                    } else {
                        emptyMap()
                    }
                    for (fm in fileMetas) {
                        val tier = retentionByAttId[fm.id] ?: FileRetentionTier.FULL
                        if (tier == FileRetentionTier.INDEX_RECALL) {
                            val indexed = hydratedPreloaded[fm.id]
                            appendLine(
                                FileContextBudgeter.buildDocumentIndexAnchor(
                                    meta = fm,
                                    outline = indexed?.outline.orEmpty(),
                                    chunkCount = indexed?.chunks?.size ?: 0,
                                ),
                            )
                            appendLine()
                        } else {
                            val rawFileText = resolvedTextsById[fm.id]
                                ?: effectiveInput.resolveAttachmentText?.invoke(fm.id)
                            if (!rawFileText.isNullOrBlank()) {
                                val fitted = FileContextBudgeter.fitFileToBudget(
                                    meta = fm,
                                    fullText = rawFileText,
                                    queryText = "",
                                    budgetTokens = allocations[fm.id] ?: fileTurnBudget,
                                    modeOverride = FileInjectMode.full,
                                )
                                appendLine(fitted.formattedBlock)
                                appendLine()
                            } else {
                                val indexed = hydratedPreloaded[fm.id]
                                appendLine(
                                    FileContextBudgeter.buildDocumentIndexAnchor(
                                        meta = fm,
                                        outline = indexed?.outline.orEmpty(),
                                        chunkCount = indexed?.chunks?.size ?: 0,
                                    ),
                                )
                                appendLine()
                            }
                        }
                    }
                }
            }.trim()

            val mergedText = buildString {
                if (fileBlocksText.isNotBlank()) {
                    append(fileBlocksText)
                    if (finalText.isNotBlank()) append("\n\n")
                }
                append(finalText)
            }.trim()

            if (imageIds.isNotEmpty() && msg.role == "user") {
                if (msg.id in decayedImages || effectiveInput.resolveAttachmentDataUrl == null) {
                    val notice = "[${imageIds.size} 张历史图片已省略]"
                    wireMessages.add(
                        WireChatMessage(
                            role = "user",
                            textContent = if (mergedText.isNotBlank()) "$notice\n$mergedText" else notice,
                        ),
                    )
                } else {
                    val parts = ArrayList<WireChatContentPart>()
                    if (mergedText.isNotBlank()) {
                        parts.add(WireChatContentPart(type = "text", text = mergedText))
                    }
                    for (id in imageIds) {
                        val url = effectiveInput.resolveAttachmentDataUrl.invoke(id)
                        if (!url.isNullOrBlank()) {
                            parts.add(WireChatContentPart(type = "image_url", imageUrl = url))
                        }
                    }
                    if (parts.isEmpty()) {
                        wireMessages.add(WireChatMessage(role = "user", textContent = mergedText.ifBlank { "[图片]" }))
                    } else {
                        wireMessages.add(WireChatMessage(role = "user", parts = parts))
                    }
                }
            } else {
                wireMessages.add(WireChatMessage(role = msg.role, textContent = mergedText))
            }
        }

        if (lastUserIdx == -1) {
            appendSyntheticOpeningTurn(
                wireMessages = wireMessages,
                input = effectiveInput,
                pipe = pipe,
                conv = conv,
                zone3StandardLore = zone3StandardLore,
                existingLoreTitles = existingLoreTitles,
                chatWorldbookTriggeredBlock = chatWorldbookTriggeredBlock,
                recalledFileContextBlock = recalledFileContextBlock,
                zone3TrailingSuffix = zone3TrailingSuffix,
                appendTrailingZone3 = ::appendTrailingZone3,
            )
        }

        injectShallowDepthMessages(wireMessages, effectiveInput, pipe, wbResult, conv)

        val fileRecallDelta = effectiveFileRecallBundle.recalledTokens - pipe.fileRecallBundle.recalledTokens
        val actualSentRaw = pipe.prefix.systemTokens + pipe.dynamicOverhead + fileRecallDelta + selectedRawHistory
        val actualSentTokens = (actualSentRaw * pipe.calibration).roundToInt()
        val actualSentPct = min(100, ((actualSentTokens.toDouble() / max(1, pipe.effectiveLimitTokens)) * 100.0).roundToInt())
        val actualFileTokens = ((selectedRawFiles + effectiveFileRecallBundle.recalledTokens) * pipe.calibration).roundToInt()
        val actualTrimmedCount = pipe.uncompactedExcludedCount + max(0, active.size - selected.size)
        val actualHardOverflow = actualSentTokens > pipe.windowLimit
        val actualIsSliding = actualTrimmedCount > 0

        val updatedRecalledItems = if (newlyDemotedTier2) {
            pipe.waterline.recalledItems.filterNot { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE } +
                effectiveFileRecallBundle.recalledItems
        } else {
            pipe.waterline.recalledItems
        }

        val built = BuiltChatContext(
            wireMessages = wireMessages,
            summary = pipe.waterline.copy(
                usedTokens = actualSentTokens,
                percentage = actualSentPct,
                exceedsModelWindow = actualHardOverflow,
                decayedImageMessageIds = decayedImages,
                decayedFileMessageIds = decayedFiles,
                trimmedMessageCount = actualTrimmedCount,
                fileTokens = actualFileTokens,
                retrievedMemoryCount = updatedRecalledItems.size,
                recalledItems = updatedRecalledItems,
                recalledTotalTokens = updatedRecalledItems.sumOf { it.estimatedTokens },
                sentTokens = actualSentTokens,
                sentPercentage = actualSentPct,
                isSliding = actualIsSliding,
                hardOverflow = actualHardOverflow,
                uncompactedOverflowCount = actualTrimmedCount,
            ),
        )
        return BuiltChatContextV4(
            built = built,
            worldbookResult = pipe.worldbookResult,
            resolvedAssistantPrefill = pipe.resolvedAssistantPrefill,
        )
    }

    private fun buildZone3TrailingSuffix(
        input: ContextBuildInput,
        pipe: ComputedPipeline,
        wbResult: WorldbookEvalResult?,
        conv: ChatConversation,
        lastUserText: String,
    ): String {
        val parts = ArrayList<String>()
        if (pipe.isV4Mode) {
            val depth0Lore = wbResult?.zone3TriggeredEntries.orEmpty()
                .filter { it.position == WorldbookEngine.POSITION_AT_DEPTH && it.depth == 0 }
            if (depth0Lore.isNotEmpty()) {
                val body = depth0Lore.joinToString("\n") { e ->
                    val txt = ContextZonePrefixBuilder.expandDynamicV4Macros(e.content.trim(), conv, input.characterCard, input.persona)
                    "- [${e.category}] ${e.title}: $txt"
                }
                parts.add("<depth_instructions>\n$body\n</depth_instructions>")
            }
            val depth0PresetBlocks = pipe.parsedPresetBlocks
                .filter {
                    it.enabled && !it.marker &&
                        ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition) &&
                        it.injectionDepth == 0 &&
                        !it.role.equals("assistant", ignoreCase = true) &&
                        it.content.isNotBlank()
                }
            for (b in depth0PresetBlocks) {
                parts.add(ContextZonePrefixBuilder.expandDynamicV4Macros(b.content.trim(), conv, input.characterCard, input.persona))
            }
            val personaDepth0 = input.persona
                ?.takeIf {
                    it.description.isNotBlank() &&
                        ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition) &&
                        it.injectionDepth == 0
                }
            if (personaDepth0 != null) {
                val userName = ContextZonePrefixBuilder.resolveStaticUserName(conv, input.persona)
                val body = ContextZonePrefixBuilder.expandDynamicV4Macros(personaDepth0.description.trim(), conv, input.characterCard, input.persona)
                parts.add("【用户人设 ($userName)】\n$body")
            }
            val cardDepth0 = input.characterCard
                ?.takeIf { it.depthPromptText.isNotBlank() && it.depthPromptDepth == 0 }
            if (cardDepth0 != null) {
                parts.add(ContextZonePrefixBuilder.expandDynamicV4Macros(cardDepth0.depthPromptText.trim(), conv, input.characterCard, input.persona))
            }
            val cardPost = input.characterCard?.postHistoryInstructions?.trim().orEmpty()
            val rpPost = conv.rp?.postHistoryInstructions?.trim().orEmpty()
            if (cardPost.isNotEmpty() && (!pipe.isRp || rpPost != cardPost)) {
                val expandedPost = ContextZonePrefixBuilder.expandDynamicV4Macros(cardPost, conv, input.characterCard, input.persona)
                parts.add("【后置核心指令 (Post-History Instructions)】\n$expandedPost")
            }
        }
        val lengthDirective = OutputLengthPolicy.buildLengthDirective(
            mode = conv.mode,
            minOutputChars = conv.params.minOutputChars,
            novelConfig = conv.novel,
            activeChapter = pipe.activeChapter,
            isEnglish = OutputLengthPolicy.isEnglishPreference(lastUserText),
        )
        if (lengthDirective.isNotBlank()) {
            parts.add(lengthDirective)
        }
        return parts.joinToString("\n\n").trim()
    }

    private fun appendSyntheticOpeningTurn(
        wireMessages: MutableList<WireChatMessage>,
        input: ContextBuildInput,
        pipe: ComputedPipeline,
        conv: ChatConversation,
        zone3StandardLore: List<NovelLoreEntry>,
        existingLoreTitles: List<String>,
        chatWorldbookTriggeredBlock: String,
        recalledFileContextBlock: String,
        zone3TrailingSuffix: String,
        appendTrailingZone3: (String) -> String,
    ) {
        when {
            pipe.isNovel -> {
                val openingPrompt = NovelEngine.wrapNovelTurnUserMessage(
                    rawContent = if (!pipe.activeChapter?.outline.isNullOrBlank()) {
                        "请按本章细纲重新撰写精彩开篇正文：${pipe.activeChapter!!.outline.trim()}"
                    } else {
                        "请结合书名、简介、大纲与世界观，重新撰写本章精彩开篇正文。"
                    },
                    inputKind = NovelInputKind.director,
                    novel = conv.novel!!,
                    activeChapter = pipe.activeChapter,
                    triggeredLore = zone3StandardLore,
                    existingLoreTitles = existingLoreTitles,
                    archivedEpisodeLines = pipe.tailBundle.novelArchivedEpisodeLines,
                )
                val content = buildList {
                    if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                    if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                    add(openingPrompt)
                }.joinToString("\n\n")
                wireMessages.add(WireChatMessage(role = "user", textContent = appendTrailingZone3(content)))
            }
            pipe.isRp -> {
                val openingRp = RpEngine.wrapRpTurnUserMessage(
                    rawContent = "请结合世界观设定、角色关系与当前场景状态，继续演绎本幕剧情。",
                    inputKind = input.draftRpInputKind ?: RpInputKind.action,
                    rp = conv.rp!!,
                    activeLedgerLines = pipe.tailBundle.rpActiveLedgerLines,
                    recalledMemoryLines = pipe.tailBundle.rpRecalledMemoryLines,
                    archivedEpisodeLines = pipe.tailBundle.rpArchivedEpisodeLines,
                    triggeredLore = zone3StandardLore,
                    includeAuthorNote = conv.rp.authorNoteDepth <= 0,
                )
                val content = buildList {
                    if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                    add(openingRp)
                }.joinToString("\n\n")
                wireMessages.add(WireChatMessage(role = "user", textContent = appendTrailingZone3(content)))
            }
            else -> {
                val syntheticChat = buildList {
                    if (chatWorldbookTriggeredBlock.isNotBlank()) add(chatWorldbookTriggeredBlock)
                    if (pipe.tailBundle.chatTailRecallBlock.isNotBlank()) add(pipe.tailBundle.chatTailRecallBlock)
                    if (recalledFileContextBlock.isNotBlank()) add(recalledFileContextBlock)
                    if (zone3TrailingSuffix.isNotBlank()) add(zone3TrailingSuffix)
                }.joinToString("\n\n")
                if (syntheticChat.isNotBlank()) {
                    wireMessages.add(WireChatMessage(role = "user", textContent = syntheticChat))
                }
            }
        }
    }

    private fun injectShallowDepthMessages(
        wireMessages: MutableList<WireChatMessage>,
        input: ContextBuildInput,
        pipe: ComputedPipeline,
        wbResult: WorldbookEvalResult?,
        conv: ChatConversation,
    ) {
        if (!pipe.isV4Mode) {
            if (pipe.isRp && conv.rp != null && conv.rp.authorNote.isNotBlank() && conv.rp.authorNoteDepth >= 1) {
                val firstNonSystemIdx = wireMessages.indexOfFirst { it.role != "system" }.let {
                    if (it < 0) wireMessages.size else it
                }
                val insertIdx = (wireMessages.size - conv.rp.authorNoteDepth).coerceIn(firstNonSystemIdx, wireMessages.size)
                val noteText = RpEngine.replaceMacros("【OOC 作者备注】\n${conv.rp.authorNote.trim()}", conv.rp)
                wireMessages.add(insertIdx, WireChatMessage(role = "system", textContent = noteText, isDepthInjection = true))
            }
            return
        }

        val depthItems = ArrayList<DepthInjectionItem>()
        wbResult?.zone2DepthEntries.orEmpty().forEachIndexed { idx, entry ->
            val expanded = ContextZonePrefixBuilder.expandDynamicV4Macros(entry.content.trim(), conv, input.characterCard, input.persona)
            if (expanded.isNotBlank()) {
                depthItems.add(
                    DepthInjectionItem(
                        depth = entry.depth.coerceAtLeast(1),
                        role = ContextZonePrefixBuilder.normalizeWireRole(entry.outletRole),
                        text = "- [${entry.category}] ${entry.title}: $expanded",
                        sortBucket = 0,
                        subOrder = idx,
                    ),
                )
            }
        }
        pipe.parsedPresetBlocks
            .filter {
                it.enabled && !it.marker &&
                    ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition) &&
                    it.injectionDepth >= 1 &&
                    it.content.isNotBlank()
            }
            .forEachIndexed { idx, block ->
                val expanded = ContextZonePrefixBuilder.expandDynamicV4Macros(block.content.trim(), conv, input.characterCard, input.persona)
                if (expanded.isNotBlank()) {
                    depthItems.add(
                        DepthInjectionItem(
                            depth = block.injectionDepth,
                            role = ContextZonePrefixBuilder.normalizeWireRole(block.role),
                            text = expanded,
                            sortBucket = 1,
                            subOrder = idx,
                        ),
                    )
                }
            }
        input.persona
            ?.takeIf {
                it.description.isNotBlank() &&
                    ContextZonePrefixBuilder.isDepthInjectionPosition(it.injectionPosition) &&
                    it.injectionDepth >= 1
            }
            ?.let { persona ->
                val userName = ContextZonePrefixBuilder.resolveStaticUserName(conv, persona)
                val expanded = ContextZonePrefixBuilder.expandDynamicV4Macros(persona.description.trim(), conv, input.characterCard, input.persona)
                depthItems.add(
                    DepthInjectionItem(
                        depth = persona.injectionDepth,
                        role = "system",
                        text = "【用户人设 ($userName)】\n$expanded",
                        sortBucket = 2,
                        subOrder = 0,
                    ),
                )
            }
        input.characterCard
            ?.takeIf {
                it.depthPromptText.isNotBlank() &&
                    it.depthPromptDepth >= 1 &&
                    (!pipe.isRp || conv.rp == null || it.depthPromptText.trim() != conv.rp.authorNote.trim())
            }
            ?.let { card ->
                val expanded = ContextZonePrefixBuilder.expandDynamicV4Macros(card.depthPromptText.trim(), conv, input.characterCard, input.persona)
                depthItems.add(
                    DepthInjectionItem(
                        depth = card.depthPromptDepth,
                        role = ContextZonePrefixBuilder.normalizeWireRole(card.depthPromptRole),
                        text = expanded,
                        sortBucket = 3,
                        subOrder = 0,
                    ),
                )
            }
        if (pipe.isRp && conv.rp != null && conv.rp.authorNote.isNotBlank() && conv.rp.authorNoteDepth >= 1) {
            val noteText = ContextZonePrefixBuilder.expandDynamicV4Macros(
                "【OOC 作者备注】\n${conv.rp.authorNote.trim()}",
                conv,
                input.characterCard,
                input.persona,
            )
            depthItems.add(
                DepthInjectionItem(
                    depth = conv.rp.authorNoteDepth,
                    role = "system",
                    text = noteText,
                    sortBucket = 4,
                    subOrder = 0,
                ),
            )
        }

        if (depthItems.isEmpty()) return

        val baseMessages = wireMessages.toList()
        val firstNonSystemIdx = baseMessages.indexOfFirst { it.role != "system" }.let {
            if (it < 0) baseMessages.size else it
        }
        val buckets = HashMap<Int, ArrayList<DepthInjectionItem>>()
        for (item in depthItems) {
            val anchor = (baseMessages.size - item.depth).coerceIn(firstNonSystemIdx, baseMessages.size)
            buckets.getOrPut(anchor) { ArrayList() }.add(item)
        }
        wireMessages.clear()
        for (pos in 0..baseMessages.size) {
            val bucket = buckets[pos]
            if (!bucket.isNullOrEmpty()) {
                val sortedBucket = bucket.sortedWith(
                    compareByDescending<DepthInjectionItem> { it.depth }
                        .thenBy { it.sortBucket }
                        .thenBy { it.subOrder },
                )
                var currentRole: String? = null
                val currentLines = ArrayList<String>()
                fun flushRoleGroup() {
                    val r = currentRole ?: return
                    if (currentLines.isNotEmpty()) {
                        wireMessages.add(
                            WireChatMessage(
                                role = r,
                                textContent = currentLines.joinToString("\n\n"),
                                isDepthInjection = true,
                            ),
                        )
                    }
                    currentLines.clear()
                    currentRole = null
                }
                for (item in sortedBucket) {
                    if (currentRole == null || currentRole == item.role) {
                        currentRole = item.role
                        currentLines.add(item.text)
                    } else {
                        flushRoleGroup()
                        currentRole = item.role
                        currentLines.add(item.text)
                    }
                }
                flushRoleGroup()
            }
            if (pos < baseMessages.size) {
                wireMessages.add(baseMessages[pos])
            }
        }
    }
}
