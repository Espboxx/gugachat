package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.OnlineChatApi
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.WireChatMessageDto

data class CompactionOutcome(
    val archivedSummary: String,
    val summarizedUntilSeq: Int,
    val newEpisode: MemoryEpisode?,
)

object ContextCompactor {

    fun shouldAutoCompact(waterline: ContextWaterlineSummary): Boolean =
        waterline.exceedsModelWindow || waterline.percentage >= 88

    fun buildCompactionMessages(
        conversation: ChatConversation,
        messagesToCompact: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val transcript = messagesToCompact.joinToString("\n\n") { m ->
            val who = when (m.role) {
                "user" -> "用户"
                "assistant" -> "助手"
                else -> "系统"
            }
            "[#${m.seq} $who] ${m.content.take(1400)}"
        }
        val sys = """
            你是精准的对话上下文压缩引擎。请将旧对话历史浓缩为高密度前情纪要，用于后续继续无缝对话。
            要求保留：核心事实、决定、未完成事项、人物关系、参数/代码约束，控制在 250-450 字内。
        """.trimIndent()
        val prev = if (conversation.archivedSummary.isNotBlank()) {
            "【已有历史纪要】\n${conversation.archivedSummary.trim()}\n\n"
        } else {
            ""
        }
        val usr = "${prev}【需要合并压缩的新片段】\n$transcript"
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", usr),
        )
    }

    suspend fun compactConversation(
        chatApi: OnlineChatApi,
        conversation: ChatConversation,
        messages: List<ChatMessage>,
        keepTailCount: Int = 6,
    ): CompactionOutcome? {
        val groupId = conversation.groupId ?: return null
        val model = conversation.model ?: return null
        val active = TimelineReconciler.activeMessages(messages)
            .filter { it.role != "system" && it.content.isNotBlank() }
        if (active.size <= keepTailCount + 2) return null

        val slice = active.dropLast(keepTailCount)
            .filter { it.seq > conversation.summarizedUntilSeq }
        if (slice.isEmpty()) return null

        val prompt = buildCompactionMessages(conversation, slice)
        val result = chatApi.completeOnce(
            OnlineChatCompletionRequestDto(
                group_id = groupId,
                model = model,
                messages = prompt,
                stream = false,
                temperature = 0.3,
                max_tokens = 800,
            ),
        )
        val summary = result.content.trim()
        if (summary.isEmpty()) return null

        val fromSeq = slice.first().seq
        val toSeq = slice.last().seq
        val now = System.currentTimeMillis()
        val episode = MemoryEpisode(
            id = "ep-compact-$fromSeq-$toSeq-$now",
            conversationId = conversation.id,
            level = 1,
            title = "压缩归档 #$fromSeq-$toSeq",
            summary = summary,
            fromSeq = fromSeq,
            toSeq = toSeq,
            createdAt = now,
            updatedAt = now,
            estimatedTokens = EpisodeConsolidator.estimateEpisodeTokens("压缩归档", summary),
        )
        return CompactionOutcome(
            archivedSummary = summary,
            summarizedUntilSeq = toSeq,
            newEpisode = episode,
        )
    }
}
