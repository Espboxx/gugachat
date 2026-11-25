package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.WireChatMessageDto
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

@Serializable
private data class ExtractedMemoryDto(
    val category: String = "other",
    val content: String = "",
    val aliases: List<String> = emptyList(),
    val pinned: Boolean = false,
)

object MemoryExtractor {
    fun estimateUserMemoryTokens(content: String): Int {
        if (content.isBlank()) return 0
        var cjk = 0
        var other = 0
        for (ch in content) {
            if (CjkSearch.isHanChar(ch) || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) {
                cjk++
            } else if (!ch.isWhitespace()) {
                other++
            }
        }
        return max(6, ceil(cjk * 1.45 + other * 0.38).toInt() + 6)
    }

    fun buildMemoryExtractionMessages(
        existingMemories: List<UserMemory>,
        recentMessages: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val existingBlock = if (existingMemories.isEmpty()) {
            "(暂无已有记忆)"
        } else {
            existingMemories.take(40).joinToString("\n") { "- [${it.category.name}] ${it.content}" }
        }
        val turnsBlock = recentMessages
            .filter { it.deletedAt == null && it.role != "system" && it.content.isNotBlank() }
            .takeLast(6)
            .joinToString("\n\n") { m -> "${m.role.uppercase()}: ${m.content.take(900)}" }

        val sys = """
            你是跨会话长期记忆提取器。请从最近对话中提取值得长期记住的【用户个人特征、偏好、正在进行的项目、行为规范】。
            规则：
            1. 只提取明确的长期事实，不要提取一次性寒暄或模型自述。
            2. 避免与“已有记忆”重复。
            3. 每条简明扼要（15-60字），只返回合法 JSON 数组：
               [{"category":"preference|profile|project|rule|other","content":"...","aliases":["别名"],"pinned":false}]
            4. 若无新增值得记录的长期信息，返回 []。
        """.trimIndent()
        val usr = "已有记忆：\n$existingBlock\n\n最近对话：\n$turnsBlock"
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", usr),
        )
    }

    fun parseExtractedMemories(raw: String): List<UserMemory> {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('[')
        val end = stripped.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val jsonArr = stripped.substring(start, end + 1)
        val dtos = try {
            AppJson.decodeFromString<List<ExtractedMemoryDto>>(jsonArr)
        } catch (_: Exception) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        return dtos.mapNotNull { dto ->
            val text = dto.content.trim()
            if (text.length < 4) return@mapNotNull null
            val cat = parseCategory(dto.category)
            UserMemory(
                id = UUID.randomUUID().toString(),
                category = cat,
                content = text,
                aliases = dto.aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                pinned = dto.pinned,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                estimatedTokens = estimateUserMemoryTokens(text),
            )
        }
    }

    fun mergeExtractedMemories(
        existing: List<UserMemory>,
        extracted: List<UserMemory>,
        maxTotal: Int = 200,
    ): Pair<List<UserMemory>, Int> {
        val merged = existing.toMutableList()
        var added = 0
        for (item in extracted) {
            val normNew = CjkSearch.normalizeForSearch(item.content)
            val dup = merged.any { cur ->
                val normCur = CjkSearch.normalizeForSearch(cur.content)
                normCur == normNew ||
                    (normNew.length >= 10 && normCur.contains(normNew)) ||
                    (normCur.length >= 10 && normNew.contains(normCur))
            }
            if (!dup && merged.size < maxTotal) {
                merged.add(item)
                added++
            }
        }
        return merged to added
    }

    private fun parseCategory(raw: String): MemoryCategory =
        when (raw.trim().lowercase()) {
            "preference" -> MemoryCategory.preference
            "profile" -> MemoryCategory.profile
            "project" -> MemoryCategory.project
            "rule" -> MemoryCategory.rule
            else -> MemoryCategory.other
        }
}
