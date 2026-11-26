package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.OnlineChatApi
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.WireChatMessageDto
import kotlin.math.max
import kotlin.math.min

data class CompactionOutcome(
    val archivedSummary: String,
    val summarizedUntilSeq: Int,
    val newEpisode: MemoryEpisode?,
    val newEpisodes: List<MemoryEpisode> = listOfNotNull(newEpisode),
    val chunksProcessed: Int = 1,
)

object ContextCompactor {
    const val DEFAULT_CHUNK_TARGET_TOKENS = 14_000
    const val MIN_CHUNK_TARGET_TOKENS = 4_000
    const val MAX_CHUNK_TARGET_TOKENS = 16_000

    fun shouldAutoCompact(waterline: ContextWaterlineSummary): Boolean {
        val effectivePct = if (waterline.sentPercentage > 0) waterline.sentPercentage else waterline.percentage
        return waterline.hardOverflow ||
            waterline.exceedsModelWindow ||
            effectivePct >= 85 ||
            waterline.uncompactedOverflowCount >= 8
    }

    /**
     * Splits oversized turns along paragraph/line/sentence boundaries so long RP/novel turns
     * (~6K+ chars) are preserved intact across token-budgeted compaction chunks instead of
     * losing ~77% of each turn to hard `.take(1400)` truncation.
     */
    internal fun splitContentByParagraphs(
        text: String,
        maxTokens: Int,
        modelId: String? = null,
    ): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val budget = max(512, maxTokens)
        if (TokenEstimator.estimateText(clean, modelId) <= budget) return listOf(clean)

        val paragraphs = clean.split(Regex("\\n\\s*\\n+")).flatMap { para ->
            if (TokenEstimator.estimateText(para, modelId) <= budget) {
                listOf(para)
            } else {
                para.split('\n').flatMap { line ->
                    if (TokenEstimator.estimateText(line, modelId) <= budget) {
                        listOf(line)
                    } else {
                        splitLongLine(line, budget, modelId)
                    }
                }
            }
        }

        val pieces = ArrayList<String>()
        val buf = StringBuilder()
        var bufTok = 0
        for (para in paragraphs) {
            val p = para.trim()
            if (p.isEmpty()) continue
            val pTok = TokenEstimator.estimateText(p, modelId)
            if (buf.isNotEmpty() && bufTok + pTok > budget) {
                pieces.add(buf.toString().trim())
                buf.setLength(0)
                bufTok = 0
            }
            if (buf.isNotEmpty()) buf.append("\n\n")
            buf.append(p)
            bufTok += pTok
        }
        if (buf.isNotEmpty()) pieces.add(buf.toString().trim())
        return pieces
    }

    private fun splitLongLine(line: String, maxTokens: Int, modelId: String?): List<String> {
        val out = ArrayList<String>()
        val approxCharsPerTok = max(0.5, 1.0 / TokenEstimator.priorFor(modelId).hanRatio)
        val step = max(200, (maxTokens * approxCharsPerTok * 0.85).toInt())
        var pos = 0
        while (pos < line.length) {
            val rawEnd = min(line.length, pos + step)
            val end = if (rawEnd < line.length) {
                val sentenceCut = (rawEnd downTo max(pos + step / 2, pos + 1))
                    .firstOrNull { line[it] in setOf('。', '！', '？', '.', '!', '?', '；', ';') }
                if (sentenceCut != null) sentenceCut + 1 else rawEnd
            } else {
                rawEnd
            }
            out.add(line.substring(pos, end).trim())
            pos = end
        }
        return out.filter { it.isNotEmpty() }
    }

    /**
     * Groups messages chronologically into token-budgeted chunks (12K–16K est. tokens by default),
     * splitting any single oversized turn on paragraph boundaries rather than discarding its tail.
     */
    fun splitMessagesIntoTokenChunks(
        messages: List<ChatMessage>,
        modelId: String? = null,
        targetChunkTokens: Int = DEFAULT_CHUNK_TARGET_TOKENS,
    ): List<List<ChatMessage>> {
        if (messages.isEmpty()) return emptyList()
        val budget = targetChunkTokens.coerceIn(MIN_CHUNK_TARGET_TOKENS, MAX_CHUNK_TARGET_TOKENS)
        val singleTurnCap = max(2000, (budget * 0.85).toInt())

        val expanded = ArrayList<Pair<ChatMessage, Int>>()
        for (m in messages) {
            val pieces = splitContentByParagraphs(m.content, maxTokens = singleTurnCap, modelId = modelId)
            if (pieces.size <= 1) {
                val tok = TokenEstimator.estimateMessage(m.content, modelId = modelId)
                expanded.add(m to tok)
            } else {
                pieces.forEachIndexed { idx, piece ->
                    val labeled = if (idx == 0) piece else "[续 ${idx + 1}/${pieces.size}]\n$piece"
                    val sub = m.copy(content = labeled)
                    expanded.add(sub to TokenEstimator.estimateMessage(labeled, modelId = modelId))
                }
            }
        }

        val chunks = ArrayList<List<ChatMessage>>()
        var current = ArrayList<ChatMessage>()
        var currentTok = 0
        for ((msg, tok) in expanded) {
            if (current.isNotEmpty() && currentTok + tok > budget) {
                chunks.add(current)
                current = ArrayList()
                currentTok = 0
            }
            current.add(msg)
            currentTok += tok
        }
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        return chunks
    }

    fun buildCompactionMessages(
        conversation: ChatConversation,
        messagesToCompact: List<ChatMessage>,
        runningSummary: String = conversation.archivedSummary,
        chunkIndex: Int = 1,
        totalChunks: Int = 1,
        modelId: String? = conversation.model,
    ): List<WireChatMessageDto> {
        val transcriptParts = ArrayList<String>()
        var usedTokens = 0
        val transcriptCap = MAX_CHUNK_TARGET_TOKENS
        for (m in messagesToCompact) {
            val who = when (m.role) {
                "user" -> "用户"
                "assistant" -> "助手"
                else -> "系统"
            }
            val entry = "[#${m.seq} $who] ${m.content.trim()}"
            val tok = TokenEstimator.estimateText(entry, modelId) + 4
            if (transcriptParts.isNotEmpty() && usedTokens + tok > transcriptCap) break
            transcriptParts.add(entry)
            usedTokens += tok
        }
        val transcript = transcriptParts.joinToString("\n\n")
        val progressHint = if (totalChunks > 1) {
            "（当前正在滚动压缩第 $chunkIndex / $totalChunks 段历史，请完整继承已有纪要中的核心事实并融合本段新进展。）"
        } else {
            ""
        }
        val sys = """
            你是精准的对话上下文压缩引擎。请将旧对话历史浓缩为高密度前情纪要，用于后续继续无缝对话。$progressHint
            要求保留：核心事实、决定、未完成事项、人物关系、道具/数值/代码约束，控制在 250-500 字内。
        """.trimIndent()
        val prev = if (runningSummary.isNotBlank()) {
            "【已有历史纪要】\n${runningSummary.trim()}\n\n"
        } else {
            ""
        }
        val usr = "${prev}【需要合并压缩的新片段】\n$transcript"
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", usr),
        )
    }

    suspend fun compactNow(
        chatApi: OnlineChatApi,
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        keepTailCount: Int = 6,
        fallbackGroupId: Long? = null,
        fallbackModel: String? = null,
        chunkTargetTokens: Int = DEFAULT_CHUNK_TARGET_TOKENS,
        onChunkProgress: ((completedChunks: Int, totalChunks: Int) -> Unit)? = null,
    ): CompactionOutcome? = compactConversation(
        chatApi = chatApi,
        conversation = conversation,
        messages = messages,
        keepTailCount = keepTailCount,
        fallbackGroupId = fallbackGroupId,
        fallbackModel = fallbackModel,
        chunkTargetTokens = chunkTargetTokens,
        onChunkProgress = onChunkProgress,
    )

    suspend fun compactConversation(
        chatApi: OnlineChatApi,
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        keepTailCount: Int = 6,
        fallbackGroupId: Long? = null,
        fallbackModel: String? = null,
        chunkTargetTokens: Int = DEFAULT_CHUNK_TARGET_TOKENS,
        onChunkProgress: ((completedChunks: Int, totalChunks: Int) -> Unit)? = null,
    ): CompactionOutcome? {
        val groupId = conversation.groupId ?: fallbackGroupId ?: return null
        val model = conversation.model?.takeIf { it.isNotBlank() }
            ?: fallbackModel?.takeIf { it.isNotBlank() }
            ?: return null

        val active = TimelineReconciler.activeMessages(messages)
            .filter { it.role != "system" && it.content.isNotBlank() }
        val effectiveKeepTail = max(1, keepTailCount)
        if (active.size <= effectiveKeepTail) return null

        val slice = active.dropLast(effectiveKeepTail)
            .filter { it.seq > conversation.summarizedUntilSeq }
        if (slice.isEmpty()) return null

        val sliceTokens = slice.sumOf { TokenEstimator.estimateMessage(it.content, modelId = model) }
        if (active.size <= effectiveKeepTail + 2 && sliceTokens < 8_000) return null

        val chunks = splitMessagesIntoTokenChunks(
            messages = slice,
            modelId = model,
            targetChunkTokens = chunkTargetTokens,
        )
        if (chunks.isEmpty()) return null

        var runningSummary = conversation.archivedSummary.trim()
        var lastCompactedToSeq = conversation.summarizedUntilSeq
        val createdEpisodes = ArrayList<MemoryEpisode>(chunks.size)

        chunks.forEachIndexed { idx, chunkMsgs ->
            val prompt = buildCompactionMessages(
                conversation = conversation,
                messagesToCompact = chunkMsgs,
                runningSummary = runningSummary,
                chunkIndex = idx + 1,
                totalChunks = chunks.size,
                modelId = model,
            )
            val result = chatApi.completeOnce(
                OnlineChatCompletionRequestDto(
                    group_id = groupId,
                    model = model,
                    messages = prompt,
                    stream = false,
                    temperature = 0.3,
                    max_tokens = 1000,
                ),
            )
            val summary = result.content.trim()
            if (summary.isEmpty()) {
                if (createdEpisodes.isEmpty()) return null
                return CompactionOutcome(
                    archivedSummary = runningSummary,
                    summarizedUntilSeq = lastCompactedToSeq,
                    newEpisode = createdEpisodes.lastOrNull(),
                    newEpisodes = createdEpisodes,
                    chunksProcessed = createdEpisodes.size,
                )
            }
            val fromSeq = chunkMsgs.first().seq
            val toSeq = chunkMsgs.last().seq
            val now = System.currentTimeMillis()
            val title = "压缩归档 #$fromSeq-$toSeq"
            val episode = MemoryEpisode(
                id = "ep-compact-$fromSeq-$toSeq-$now-$idx",
                conversationId = conversation.id,
                level = 1,
                title = title,
                summary = summary,
                fromSeq = fromSeq,
                toSeq = toSeq,
                createdAt = now,
                updatedAt = now,
                estimatedTokens = EpisodeConsolidator.estimateEpisodeTokens(title, summary, modelId = model),
            )
            runningSummary = summary
            lastCompactedToSeq = max(lastCompactedToSeq, toSeq)
            createdEpisodes.add(episode)
            onChunkProgress?.invoke(idx + 1, chunks.size)
        }

        if (createdEpisodes.isEmpty()) return null
        return CompactionOutcome(
            archivedSummary = runningSummary,
            summarizedUntilSeq = lastCompactedToSeq,
            newEpisode = createdEpisodes.lastOrNull(),
            newEpisodes = createdEpisodes,
            chunksProcessed = createdEpisodes.size,
        )
    }
}
