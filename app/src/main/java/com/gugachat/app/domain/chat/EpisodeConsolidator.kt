package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.WireChatMessageDto
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

@Serializable
private data class EpisodeConsolidationJson(
    val title: String = "",
    val summary: String = "",
    val keyEntities: List<String> = emptyList(),
)

data class EpisodeBatchCandidate(
    val messages: List<ChatMessage>,
    val fromSeq: Int,
    val toSeq: Int,
)

object EpisodeConsolidator {
    const val DEFAULT_BATCH_MESSAGES = 12
    const val HOT_TAIL_MESSAGES = 6
    const val ARC_TRIGGER_EPISODES = 4
    const val CHRONICLE_TRIGGER_ARCS = 4

    fun estimateEpisodeTokens(title: String, summary: String): Int {
        val text = "$title: $summary"
        if (text.isBlank()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (CjkSearch.isHanChar(ch) || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) {
                cjk++
            } else if (!ch.isWhitespace()) {
                other++
            }
        }
        return max(12, ceil(cjk * 1.45 + other * 0.38).toInt() + 8)
    }

    fun findNextMessagesBatch(
        activeMessages: List<ChatMessage>,
        existingEpisodes: List<MemoryEpisode>,
        batchSize: Int = DEFAULT_BATCH_MESSAGES,
        hotTailCount: Int = HOT_TAIL_MESSAGES,
    ): EpisodeBatchCandidate? {
        val nonSystem = activeMessages.filter { it.role != "system" && it.content.isNotBlank() }
        if (nonSystem.size <= hotTailCount + 4) return null

        val lastCoveredSeq = existingEpisodes
            .filter { !it.stale && it.level == 1 }
            .maxOfOrNull { it.toSeq } ?: 0

        val eligible = nonSystem
            .dropLast(hotTailCount)
            .filter { it.seq > lastCoveredSeq }

        if (eligible.size < 6) return null
        val batch = eligible.take(batchSize)
        return EpisodeBatchCandidate(
            messages = batch,
            fromSeq = batch.first().seq,
            toSeq = batch.last().seq,
        )
    }

    fun buildEpisodeConsolidationPrompt(
        conversationTitle: String,
        batch: EpisodeBatchCandidate,
    ): List<WireChatMessageDto> {
        val transcript = batch.messages.joinToString("\n\n") { m ->
            val speaker = if (m.role == "user") "用户" else "助手"
            "[#${m.seq} $speaker] ${m.content.take(1200)}"
        }
        val sys = """
            你是精准的对话记忆归档引擎。请将下方历史对话片段浓缩为一段结构化情景摘要（Episode）。
            要求：
            1. 保留关键人物、专有名词、重要决定、约定/伏笔、物品/状态变化与因果关系。
            2. 不要虚构未出现的事实，使用客观紧凑的中文叙述（120-260字）。
            3. 只返回合法 JSON，不要额外解释：
            {"title":"4-12字标题","summary":"详细摘要","keyEntities":["实体1","实体2"]}
        """.trimIndent()
        val usr = "会话：《$conversationTitle》 (Seq ${batch.fromSeq}~${batch.toSeq})\n\n$transcript"
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", usr),
        )
    }

    fun findEpisodesForArcRollup(
        episodes: List<MemoryEpisode>,
        triggerCount: Int = ARC_TRIGGER_EPISODES,
    ): List<MemoryEpisode> {
        val activeL1 = episodes.filter { !it.stale && it.level == 1 }.sortedBy { it.fromSeq }
        val lastL2End = episodes.filter { !it.stale && it.level == 2 }.maxOfOrNull { it.toSeq } ?: 0
        val pending = activeL1.filter { it.fromSeq > lastL2End }
        return if (pending.size >= triggerCount) pending.take(triggerCount) else emptyList()
    }

    fun buildRollupPrompt(
        items: List<MemoryEpisode>,
        targetLevel: Int,
    ): List<WireChatMessageDto> {
        val levelLabel = if (targetLevel >= 3) "编年史总纲 (Chronicle)" else "阶段篇章摘要 (Arc)"
        val body = items.joinToString("\n") { ep ->
            "- [Seq ${ep.fromSeq}~${ep.toSeq} ${ep.title}]: ${ep.summary}"
        }
        val sys = """
            请将多条阶段摘要整合为一条更高层级的$levelLabel。
            保留长期主线推进、关键关系转变、未解决悬念与核心设定，控制在 180-320 字。
            只返回合法 JSON：{"title":"篇章标题","summary":"整合摘要","keyEntities":["实体1","实体2"]}
        """.trimIndent()
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", body),
        )
    }

    fun parseConsolidationResponse(
        raw: String,
        conversationId: String,
        fromSeq: Int,
        toSeq: Int,
        level: Int = 1,
    ): MemoryEpisode {
        val jsonBlock = extractFirstJsonObject(raw)
        val parsed = if (jsonBlock != null) {
            try {
                AppJson.decodeFromString(EpisodeConsolidationJson.serializer(), jsonBlock)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val title = parsed?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: "阶段记忆 #$fromSeq-$toSeq"
        val summary = parsed?.summary?.trim()?.takeIf { it.isNotEmpty() }
            ?: raw.trim().take(400)
        val now = System.currentTimeMillis()
        return MemoryEpisode(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            level = level,
            title = title,
            summary = summary,
            keyEntities = parsed?.keyEntities?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList(),
            fromSeq = fromSeq,
            toSeq = toSeq,
            locked = false,
            stale = false,
            createdAt = now,
            updatedAt = now,
            estimatedTokens = estimateEpisodeTokens(title, summary),
        )
    }

    internal fun extractFirstJsonObject(raw: String): String? {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }
}
