package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.WireChatMessageDto
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

data class LoreMatchResult(
    val constant: List<NovelLoreEntry>,
    val triggered: List<NovelLoreEntry>,
    val constantTokens: Int,
    val triggeredTokens: Int,
    val triggeredScores: Map<String, Double> = emptyMap(),
    val triggeredMatchedTerms: Map<String, List<String>> = emptyMap(),
)

@Serializable
private data class NovelLoreSyncDto(
    val category: String = "other",
    val title: String = "",
    val content: String = "",
    val keywords: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val action: String = "upsert",
)

@Serializable
private data class NovelStatusHudDto(
    val chapterTitle: String? = null,
    val time: String? = null,
    val location: String? = null,
    val present: List<String> = emptyList(),
    val mood: String? = null,
    val conflict: String? = null,
    val foreshadows: List<String> = emptyList(),
)

@Serializable
private data class NovelChapterEndDto(
    val summary: String = "",
    val nextChapterTitle: String? = null,
    val nextTitle: String? = null,
)

@Serializable
private data class ThreePathDraftDto(
    val title: String = "",
    val tone: String = "",
    val summary: String = "",
    val opening: String = "",
)

data class NovelTurnParseOutcome(
    val cleanProse: String,
    val statusHud: NovelStatusHud? = null,
    val loreUpserts: List<NovelLoreEntry> = emptyList(),
    val chapterEndSummary: String? = null,
    val suggestedNextChapterTitle: String? = null,
    val choices: List<String> = emptyList(),
)

data class NovelDraftOption(
    val title: String,
    val tone: String,
    val summary: String,
    val opening: String,
)

object NovelEngine {
    private val STATUS_HUD_RE = Regex("<status_hud>([\\s\\S]*?)</status_hud>", RegexOption.IGNORE_CASE)
    private val LORE_SYNC_RE = Regex("<lore_sync>([\\s\\S]*?)</lore_sync>", RegexOption.IGNORE_CASE)
    private val CHAPTER_END_RE = Regex("<chapter_end>([\\s\\S]*?)</chapter_end>", RegexOption.IGNORE_CASE)
    private val CHOICES_RE = Regex("<choices>([\\s\\S]*?)</choices>", RegexOption.IGNORE_CASE)

    fun estimateLoreTokens(entry: NovelLoreEntry): Int {
        if (entry.estimatedTokens > 0) return entry.estimatedTokens
        val text = "${entry.title}: ${entry.content}"
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (CjkSearch.isHanChar(ch) || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) cjk++
            else if (!ch.isWhitespace()) other++
        }
        return max(8, ceil(cjk * 1.45 + other * 0.38).toInt() + 8)
    }

    fun matchNovelLore(
        lore: List<NovelLoreEntry>,
        recentMessages: List<ChatMessage>,
        draftText: String,
        chapterOutline: String,
        loreTokenBudget: Int,
        activeTipSeq: Int,
        presentEntities: List<String> = emptyList(),
        sceneTitle: String? = null,
        semanticScorer: SemanticScorer? = null,
    ): LoreMatchResult {
        val valid = lore.filter { entry ->
            TimelineReconciler.isItemValidAtTip(
                enabled = entry.enabled,
                stale = entry.stale,
                validFromSeq = entry.validFromSeq,
                supersededAtSeq = entry.supersededAtSeq,
                tipSeq = activeTipSeq,
            )
        }
        if (valid.isEmpty() || loreTokenBudget <= 0) {
            return LoreMatchResult(emptyList(), emptyList(), 0, 0)
        }

        val constantPool = valid.filter { it.alwaysActive }.sortedByDescending { it.priority }
        val constant = ArrayList<NovelLoreEntry>()
        var constantTokens = 0
        for (entry in constantPool) {
            val cost = estimateLoreTokens(entry)
            if (constantTokens + cost <= loreTokenBudget) {
                constant.add(entry)
                constantTokens += cost
            }
        }

        val remainingBudget = max(0, loreTokenBudget - constantTokens)
        val dynamicPool = valid.filter { !it.alwaysActive }
        if (dynamicPool.isEmpty() || remainingBudget <= 0) {
            return LoreMatchResult(constant, emptyList(), constantTokens, 0)
        }

        val entitiesWithScene = buildList {
            addAll(presentEntities)
            if (!sceneTitle.isNullOrBlank()) add(sceneTitle)
        }
        val queryText = listOf(draftText, chapterOutline).filter { it.isNotBlank() }.joinToString("\n")
        val query = CjkSearch.buildMemoryQuery(queryText, recentMessages, entitiesWithScene)

        val candidates = dynamicPool.map { CjkSearch.novelLoreToSearchCandidate(it) }
        val firstPass = CjkSearch.scoreMemoryCandidates(
            candidates = candidates,
            query = query,
            options = ScoreMemoryOptions(
                activeTipSeq = activeTipSeq,
                maxTokens = remainingBudget,
                semanticScorer = semanticScorer,
            ),
        )

        val entryById = dynamicPool.associateBy { it.id }
        val picked = ArrayList<NovelLoreEntry>()
        val scores = LinkedHashMap<String, Double>()
        val terms = LinkedHashMap<String, List<String>>()
        val pickedIds = HashSet<String>()
        var usedDynamic = 0

        for (s in firstPass.selected) {
            val e = entryById[s.item.id] ?: continue
            picked.add(e)
            pickedIds.add(e.id)
            scores[e.id] = s.score
            terms[e.id] = s.matchedTerms
            usedDynamic += s.estimatedTokens
        }

        // 1-hop lore recursion: expand entries explicitly referenced inside top-3 triggered lore texts
        val hopRemaining = max(0, remainingBudget - usedDynamic)
        if (picked.isNotEmpty() && hopRemaining > 16) {
            val hopText = picked.take(3).joinToString("\n") { "${it.title} ${it.content}" }
            val hopQuery = CjkSearch.buildMemoryQuery(hopText, emptyList(), presentEntities)
            val secondCandidates = dynamicPool
                .filter { it.id !in pickedIds }
                .map { CjkSearch.novelLoreToSearchCandidate(it) }
            if (secondCandidates.isNotEmpty()) {
                val secondPass = CjkSearch.scoreMemoryCandidates(
                    candidates = secondCandidates,
                    query = hopQuery,
                    options = ScoreMemoryOptions(activeTipSeq = activeTipSeq, maxTokens = hopRemaining),
                )
                for (s in secondPass.selected) {
                    if (s.aliasHit <= 0.0 && s.normBm25 < 0.25) continue
                    val e = entryById[s.item.id] ?: continue
                    val cost = estimateLoreTokens(e)
                    if (usedDynamic + cost > remainingBudget) break
                    picked.add(e)
                    pickedIds.add(e.id)
                    scores[e.id] = s.score * 0.8
                    terms[e.id] = (s.matchedTerms + "1-hop").distinct().take(6)
                    usedDynamic += cost
                }
            }
        }

        return LoreMatchResult(
            constant = constant,
            triggered = picked,
            constantTokens = constantTokens,
            triggeredTokens = usedDynamic,
            triggeredScores = scores,
            triggeredMatchedTerms = terms,
        )
    }

    fun buildNovelSystemPrompt(
        novel: NovelConfig,
        activeChapter: NovelChapter?,
        constantLore: List<NovelLoreEntry>,
        chronicleSummary: String = "",
    ): String {
        val povText = when (novel.pov) {
            "first" -> "第一人称（“我”）"
            "third_omniscient" -> "第三人称全知视角"
            else -> "第三人称有限视角"
        }
        val sb = StringBuilder()
        sb.appendLine("你正在担任长篇小说《${novel.title}》的主笔作家。")
        if (novel.genre.isNotBlank()) sb.appendLine("题材定位：${novel.genre}")
        if (novel.style.isNotBlank()) sb.appendLine("文风要求：${novel.style}")
        sb.appendLine("叙事视角：$povText；单章目标字数约 ${novel.targetWordsPerChapter} 字。")
        if (novel.synopsis.isNotBlank()) sb.appendLine("\n【作品简介】\n${novel.synopsis.trim()}")
        if (novel.worldview.isNotBlank()) sb.appendLine("\n【世界观基石】\n${novel.worldview.trim()}")
        if (novel.mainOutline.isNotBlank()) sb.appendLine("\n【全书主线脉络】\n${novel.mainOutline.trim()}")
        if (chronicleSummary.isNotBlank()) sb.appendLine("\n【前情提要】\n$chronicleSummary")
        if (constantLore.isNotEmpty()) {
            sb.appendLine("\n【常驻核心设定 (Constant Lore)】")
            constantLore.forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.title}：${entry.content.trim()}")
            }
        }
        if (activeChapter != null) {
            sb.appendLine("\n【当前撰写章节】第${activeChapter.order}章：${activeChapter.title}")
            if (activeChapter.outline.isNotBlank()) sb.appendLine("本章细纲：${activeChapter.outline.trim()}")
        }
        sb.appendLine("\n【写作规范】")
        sb.appendLine("1. 注重画面细节、动作质感、感官描写与人物动机一致性，避免空洞总结式推进。")
        sb.appendLine("2. 严格维护既有人名、境界、道具与时间线，不擅自更改已定设定。")
        return sb.toString().trim()
    }

    fun wrapNovelTurnUserMessage(
        rawContent: String,
        inputKind: NovelInputKind,
        novel: NovelConfig,
        activeChapter: NovelChapter?,
        triggeredLore: List<NovelLoreEntry>,
        existingLoreTitles: List<String>,
        archivedEpisodeLines: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        if (triggeredLore.isNotEmpty()) {
            sb.appendLine("<triggered_lore>")
            triggeredLore.forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.title}: ${entry.content.trim()}")
            }
            sb.appendLine("</triggered_lore>\n")
        }
        if (archivedEpisodeLines.isNotEmpty()) {
            sb.appendLine("<archived_episodes>")
            archivedEpisodeLines.forEach { sb.appendLine(it) }
            sb.appendLine("</archived_episodes>\n")
        }
        if (novel.authorNote.isNotBlank()) {
            sb.appendLine("【作者导演备注】${novel.authorNote.trim()}\n")
        }
        if (activeChapter != null) {
            sb.appendLine("【当前章节进度】${activeChapter.title} (目标约 ${novel.targetWordsPerChapter} 字)")
        }
        val modeDirective = when (inputKind) {
            NovelInputKind.director -> "【写作指令·导演调度】请根据以下作者调度撰写正文："
            NovelInputKind.`continue` -> "【写作指令·自然续写】请紧接上文情绪与节奏继续撰写："
            NovelInputKind.branch -> "【写作指令·剧情分歧重构】请按新思路推演并重开本段剧情："
            NovelInputKind.dialogue -> "【写作指令·对话特写】请聚焦角色对话交锋、神态微表情与暗流张力："
        }
        sb.appendLine(modeDirective)
        sb.appendLine(rawContent.trim())

        val tagInstructions = buildList {
            if (novel.enableStatusHud) {
                add("""<status_hud>{"chapterTitle":"章节名","time":"时间","location":"地点","present":["在场角色"],"mood":"氛围","conflict":"当前冲突","foreshadows":["伏笔"]}</status_hud>""")
            }
            if (novel.autoSyncLore) {
                val hint = if (existingLoreTitles.isNotEmpty()) "（已有词条请勿重复：${existingLoreTitles.take(15).joinToString("、")}）" else ""
                add("""<lore_sync>[{"category":"character|location|item|faction|rule","title":"新词条","content":"设定描述","keywords":["关键词"],"action":"upsert"}]</lore_sync>$hint""")
            }
            if (novel.autoAdvanceChapter) {
                add("""仅当本章核心冲突完整收束适合切章时，附加 <chapter_end>{"summary":"本章50字总结","nextChapterTitle":"下一章建议标题"}</chapter_end>""")
            }
            if (novel.enableChoices) {
                add("""在末尾给出 2-3 个下一步剧情走向建议：<choices>["走向1","走向2","走向3"]</choices>""")
            }
        }
        if (tagInstructions.isNotEmpty()) {
            sb.appendLine("\n【结构化后缀（在正文完成后附加）】")
            tagInstructions.forEach { sb.appendLine(it) }
        }
        return sb.toString().trim()
    }

    fun parseNovelTurnResponse(
        rawText: String,
        conversationId: String,
        turnSeq: Int,
        existingLore: List<NovelLoreEntry> = emptyList(),
    ): NovelTurnParseOutcome {
        var cleaned = rawText
        var statusHud: NovelStatusHud? = null
        STATUS_HUD_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(m.groupValues[1])
            if (jsonObj != null) {
                try {
                    val dto = AppJson.decodeFromString(NovelStatusHudDto.serializer(), jsonObj)
                    statusHud = NovelStatusHud(
                        chapterTitle = dto.chapterTitle.orEmpty(),
                        time = dto.time.orEmpty(),
                        location = dto.location.orEmpty(),
                        present = dto.present,
                        mood = dto.mood.orEmpty(),
                        conflict = dto.conflict.orEmpty(),
                        foreshadows = dto.foreshadows,
                    )
                } catch (_: Exception) {
                }
            }
        }

        val loreUpserts = ArrayList<NovelLoreEntry>()
        LORE_SYNC_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    val dtos = AppJson.decodeFromString<List<NovelLoreSyncDto>>(body.substring(arrStart, arrEnd + 1))
                    val now = System.currentTimeMillis()
                    val existingByTitle = existingLore.associateBy { it.title.trim().lowercase() }
                    dtos.forEach { dto ->
                        val title = dto.title.trim()
                        val content = dto.content.trim()
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            val prior = existingByTitle[title.lowercase()]
                            val entry = NovelLoreEntry(
                                id = prior?.id ?: UUID.randomUUID().toString(),
                                conversationId = conversationId,
                                category = dto.category.ifBlank { "other" },
                                title = title,
                                content = content,
                                keywords = dto.keywords.ifEmpty { listOf(title) },
                                aliases = dto.aliases,
                                alwaysActive = prior?.alwaysActive ?: false,
                                priority = prior?.priority ?: 50,
                                enabled = true,
                                validFromSeq = prior?.validFromSeq ?: turnSeq,
                                createdAt = prior?.createdAt ?: now,
                                updatedAt = now,
                            )
                            loreUpserts.add(entry.copy(estimatedTokens = estimateLoreTokens(entry)))
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        var chapterEndSummary: String? = null
        var nextChapterTitle: String? = null
        CHAPTER_END_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val inner = m.groupValues[1].trim()
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(inner)
            if (jsonObj != null) {
                try {
                    val dto = AppJson.decodeFromString(NovelChapterEndDto.serializer(), jsonObj)
                    chapterEndSummary = dto.summary.trim().ifEmpty { inner }
                    nextChapterTitle = (dto.nextChapterTitle ?: dto.nextTitle)?.trim()?.ifEmpty { null }
                } catch (_: Exception) {
                    chapterEndSummary = inner
                }
            } else if (inner.isNotEmpty()) {
                chapterEndSummary = inner
            }
        }

        var choices: List<String> = emptyList()
        CHOICES_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    choices = AppJson.decodeFromString<List<String>>(body.substring(arrStart, arrEnd + 1))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(4)
                } catch (_: Exception) {
                }
            }
        }

        return NovelTurnParseOutcome(
            cleanProse = cleaned.trim(),
            statusHud = statusHud,
            loreUpserts = loreUpserts,
            chapterEndSummary = chapterEndSummary,
            suggestedNextChapterTitle = nextChapterTitle,
            choices = choices,
        )
    }

    fun buildOutlineChatMessages(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        userInstruction: String,
    ): List<WireChatMessageDto> {
        val chList = chapters.sortedBy { it.order }.joinToString("\n") { ch ->
            "- 第${ch.order}章 《${ch.title}》 (${ch.status}): ${ch.outline.ifBlank { ch.summary }}"
        }
        val sys = """
            你是专业小说结构策划编辑。请协助打磨《${novel.title}》的故事大纲、章节节奏与伏笔回收建议。
            题材：${novel.genre}；世界观：${novel.worldview}
        """.trimIndent()
        val usr = "【当前总纲】\n${novel.mainOutline}\n\n【章节列表】\n$chList\n\n【用户诉求】\n$userInstruction"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun buildPolishMessages(
        prose: String,
        style: String,
        instruction: String,
    ): List<WireChatMessageDto> {
        val sys = "你是文学润色编辑。在不改变核心情节与人物设定的前提下润色文本，文风参考：$style。直接输出润色后的正文。"
        val usr = "润色要求：${instruction.ifBlank { "增强画面感染力、句式节奏与动作/对话张力" }}\n\n待润色正文：\n$prose"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun buildThreePathDraftMessages(
        novel: NovelConfig,
        activeChapter: NovelChapter?,
        recentMessages: List<ChatMessage>,
        directionHint: String,
    ): List<WireChatMessageDto> {
        val tail = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(4)
            .joinToString("\n\n") { it.content.take(700) }
        val sys = """
            你是创意小说策划师。请基于前文与本章细纲设计 3 条风格迥异的剧情走向草稿（如：正面交锋 / 暗流悬疑 / 情感破局）。
            每条包含简短标题、基调标签、100字剧情推演梗概以及约180字示例开篇段落。
            只返回合法 JSON 数组（长度 3）：
            [{"title":"方案名","tone":"基调","summary":"推演梗概","opening":"示例开篇正文"}]
        """.trimIndent()
        val usr = "小说：《${novel.title}》\n当前章节：${activeChapter?.title.orEmpty()} (${activeChapter?.outline.orEmpty()})\n补充方向：${directionHint.ifBlank { "多元分歧" }}\n\n前文片段：\n$tail"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun parseThreePathDrafts(raw: String): List<NovelDraftOption> {
        val trimmed = raw.trim()
        val arrStart = trimmed.indexOf('[')
        val arrEnd = trimmed.lastIndexOf(']')
        if (arrStart < 0 || arrEnd <= arrStart) return emptyList()
        return try {
            AppJson.decodeFromString<List<ThreePathDraftDto>>(trimmed.substring(arrStart, arrEnd + 1))
                .mapNotNull { dto ->
                    if (dto.title.isBlank() && dto.summary.isBlank()) null
                    else NovelDraftOption(
                        title = dto.title.ifBlank { "剧情分支" },
                        tone = dto.tone.ifBlank { "常规" },
                        summary = dto.summary.trim(),
                        opening = dto.opening.trim(),
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
