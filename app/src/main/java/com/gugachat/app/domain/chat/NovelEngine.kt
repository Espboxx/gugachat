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
    val alwaysActive: Boolean? = null,
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

@Serializable
private data class SuggestedChapterDto(
    val title: String = "",
    val outline: String = "",
)

@Serializable
private data class NovelGenesisDto(
    val title: String = "",
    val synopsis: String = "",
    val worldview: String = "",
    val mainOutline: String = "",
    val style: String = "",
    val authorNote: String = "",
    val firstChapterTitle: String = "",
    val firstChapterOutline: String = "",
    val starterLore: List<NovelLoreSyncDto> = emptyList(),
    val openingProse: String = "",
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

data class SuggestedChapterOutline(
    val title: String,
    val outline: String,
)

data class NovelGenesisResult(
    val title: String,
    val synopsis: String,
    val worldview: String,
    val mainOutline: String,
    val style: String,
    val authorNote: String,
    val firstChapterTitle: String,
    val firstChapterOutline: String,
    val starterLore: List<NovelLoreEntry>,
    val openingProse: String,
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

    fun recalculateChapterWordCounts(
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
    ): List<NovelChapter> {
        if (chapters.isEmpty()) return emptyList()
        val sections = NovelExport.groupMessagesByChapter(chapters, messages)
        val countById = sections.mapNotNull { sec -> sec.chapter?.id?.let { it to sec.wordCount } }.toMap()
        return chapters.map { ch ->
            val actual = countById[ch.id] ?: 0
            if (ch.wordCount != actual) ch.copy(wordCount = actual) else ch
        }
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
        previousChapters: List<NovelChapter> = emptyList(),
    ): String {
        val povText = when (novel.pov) {
            "first" -> "第一人称（“我”）"
            "third_omniscient" -> "第三人称全知视角"
            else -> "第三人称有限视角"
        }
        val playStyleDirective = when (novel.playStyle) {
            "author" -> "纯作者连载模式：聚焦高完成度文学正文、节奏推进与章尾悬念，不打破第四面墙。"
            "interactive" -> "沉浸互动模式：在关键抉择处留下明确张力与行动空间，充分响应作者/玩家的决策。"
            else -> "创作与互动融合模式：既保持长篇连载的文学水准，又在段落收尾保留剧情走向的延展性。"
        }
        val sb = StringBuilder()
        sb.appendLine("你正在担任长篇小说《${novel.title}》的主笔作家。")
        if (novel.genre.isNotBlank()) sb.appendLine("题材定位：${novel.genre}")
        if (novel.style.isNotBlank()) sb.appendLine("文风要求：${novel.style}")
        sb.appendLine("叙事视角：$povText；单章目标字数约 ${novel.targetWordsPerChapter} 字。")
        sb.appendLine("模式倾向：$playStyleDirective")
        if (novel.synopsis.isNotBlank()) sb.appendLine("\n【作品简介】\n${novel.synopsis.trim()}")
        if (novel.worldview.isNotBlank()) sb.appendLine("\n【世界观基石】\n${novel.worldview.trim()}")
        if (novel.mainOutline.isNotBlank()) sb.appendLine("\n【全书主线脉络】\n${novel.mainOutline.trim()}")

        val recaps = previousChapters
            .filter { it.id != activeChapter?.id && it.summary.isNotBlank() }
            .sortedBy { it.order }
            .takeLast(5)
        if (recaps.isNotEmpty()) {
            sb.appendLine("\n【已完结章节梗概】")
            recaps.forEach { ch ->
                sb.appendLine("- 第${ch.order}章 《${ch.title}》：${ch.summary.trim()}")
            }
        }
        if (chronicleSummary.isNotBlank()) sb.appendLine("\n【前情提要】\n$chronicleSummary")
        if (constantLore.isNotEmpty()) {
            sb.appendLine("\n【常驻核心设定 (Constant Lore)】")
            constantLore.forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.title}：${entry.content.trim()}")
            }
        }
        if (novel.styleSample.isNotBlank()) {
            sb.appendLine("\n<style_reference>")
            sb.appendLine("请贴近以下参考样段的句式节奏、用词质感与叙事留白（切勿照搬内容）：")
            sb.appendLine(novel.styleSample.trim().take(1200))
            sb.appendLine("</style_reference>")
        }
        if (activeChapter != null) {
            sb.appendLine("\n【当前撰写章节】第${activeChapter.order}章：${activeChapter.title}（已写约 ${activeChapter.wordCount} / 目标 ${novel.targetWordsPerChapter} 字）")
            if (activeChapter.outline.isNotBlank()) sb.appendLine("本章细纲：${activeChapter.outline.trim()}")
        }
        sb.appendLine("\n【写作规范】")
        sb.appendLine("1. 注重画面细节、动作质感、感官描写与人物动机一致性，避免空洞总结式推进。")
        sb.appendLine("2. 严格维护既有人名、境界、道具与时间线，不擅自更改已定设定。")
        if (novel.antiAiRules) {
            sb.appendLine("3. 反 AI 味约束：禁止“不由得”“嘴角微微上扬”“眼神变得复杂”“空气仿佛凝固”“一抹难以察觉的笑意”等高频套话；禁止段末强行升华、说教或替读者总结主题；用具体动作、环境细节和潜台词替代抽象情绪词。")
        }
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
            sb.appendLine("【当前章节进度】${activeChapter.title}（已写 ${activeChapter.wordCount} 字 / 目标约 ${novel.targetWordsPerChapter} 字）")
        }
        val modeDirective = when (inputKind) {
            NovelInputKind.director -> "【写作指令·导演调度】请根据以下作者调度撰写正文："
            NovelInputKind.`continue` -> "【写作指令·自然续写】请紧接上文情绪与节奏继续撰写："
            NovelInputKind.branch -> "【写作指令·剧情分歧重构】请按新思路推演并重开本段剧情："
            NovelInputKind.dialogue -> "【写作指令·对话特写】请聚焦角色对话交锋、神态微表情与暗流张力："
        }
        sb.appendLine(modeDirective)
        sb.appendLine(rawContent.trim())

        appendStructuredTagInstructions(sb, novel, existingLoreTitles)
        return sb.toString().trim()
    }

    private fun appendStructuredTagInstructions(
        sb: StringBuilder,
        novel: NovelConfig,
        existingLoreTitles: List<String>,
    ) {
        val tagInstructions = buildList {
            if (novel.enableStatusHud) {
                add("""<status_hud>{"chapterTitle":"章节名","time":"时间","location":"地点","present":["在场角色"],"mood":"氛围","conflict":"当前冲突","foreshadows":["伏笔"]}</status_hud>""")
            }
            if (novel.autoSyncLore) {
                val hint = if (existingLoreTitles.isNotEmpty()) "（已有词条仅在设定更新时覆盖，勿无故重复：${existingLoreTitles.take(15).joinToString("、")}）" else ""
                add("""<lore_sync>[{"category":"character|location|item|faction|rule|foreshadow|plot","title":"新词条","content":"设定描述","keywords":["关键词"],"action":"upsert"}]</lore_sync>$hint""")
            }
            if (novel.autoAdvanceChapter) {
                add("""仅当本章核心冲突完整收束适合切章时，附加 <chapter_end>{"summary":"本章50字总结","nextChapterTitle":"下一章建议标题"}</chapter_end>""")
            }
            if (novel.enableChoices && novel.playStyle != "author") {
                add("""在末尾给出 2-3 个下一步剧情走向建议：<choices>["走向1","走向2","走向3"]</choices>""")
            }
        }
        if (tagInstructions.isNotEmpty()) {
            sb.appendLine("\n【结构化后缀（在正文完成后附加，正文内切勿提及这些标签）】")
            tagInstructions.forEach { sb.appendLine(it) }
        }
    }

    fun buildContinueMessages(
        novel: NovelConfig,
        activeChapter: NovelChapter?,
        recentMessages: List<ChatMessage>,
        constantLore: List<NovelLoreEntry>,
        triggeredLore: List<NovelLoreEntry>,
        existingLoreTitles: List<String>,
        previousChapters: List<NovelChapter> = emptyList(),
        instruction: String = "",
    ): List<WireChatMessageDto> {
        val sys = buildNovelSystemPrompt(
            novel = novel,
            activeChapter = activeChapter,
            constantLore = constantLore,
            previousChapters = previousChapters,
        )
        val out = ArrayList<WireChatMessageDto>()
        out.add(WireChatMessageDto.text("system", sys))

        val activeHistory = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(8)
        for (m in activeHistory) {
            val clean = if (m.role == "assistant") NovelExport.stripInternalTags(m.content) else m.content.trim()
            if (clean.isNotBlank()) {
                out.add(WireChatMessageDto.text(m.role, clean))
            }
        }

        val directive = buildString {
            if (triggeredLore.isNotEmpty()) {
                appendLine("<triggered_lore>")
                triggeredLore.forEach { entry ->
                    appendLine("- [${entry.category}] ${entry.title}: ${entry.content.trim()}")
                }
                appendLine("</triggered_lore>\n")
            }
            if (novel.authorNote.isNotBlank()) {
                appendLine("【作者导演备注】${novel.authorNote.trim()}\n")
            }
            if (activeChapter != null) {
                appendLine("【当前章节进度】${activeChapter.title}（已写 ${activeChapter.wordCount} 字 / 目标约 ${novel.targetWordsPerChapter} 字）")
            }
            appendLine("【无缝续写指令】请紧接上一句自然续写后续正文，不要重复前文已写内容，不要输出“好的/以下是续写”等元说明。")
            if (instruction.isNotBlank()) {
                appendLine("补充方向：${instruction.trim()}")
            }
            appendStructuredTagInstructions(this, novel, existingLoreTitles)
        }.trim()
        out.add(WireChatMessageDto.text("user", directive))
        return out
    }

    fun parseNovelTurnResponse(
        rawText: String,
        conversationId: String,
        turnSeq: Int,
        existingLore: List<NovelLoreEntry> = emptyList(),
    ): NovelTurnParseOutcome {
        var statusHud: NovelStatusHud? = null
        STATUS_HUD_RE.findAll(rawText).lastOrNull()?.let { m ->
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(m.groupValues[1])
            if (jsonObj != null) {
                try {
                    val dto = AppJson.decodeFromString(NovelStatusHudDto.serializer(), jsonObj)
                    statusHud = NovelStatusHud(
                        chapterTitle = dto.chapterTitle.orEmpty().trim(),
                        time = dto.time.orEmpty().trim(),
                        location = dto.location.orEmpty().trim(),
                        present = dto.present.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        mood = dto.mood.orEmpty().trim(),
                        conflict = dto.conflict.orEmpty().trim(),
                        foreshadows = dto.foreshadows.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    )
                } catch (_: Exception) {
                }
            }
        }

        val loreUpserts = ArrayList<NovelLoreEntry>()
        val existingByTitle = existingLore.associateBy { it.title.trim().lowercase() }.toMutableMap()
        val now = System.currentTimeMillis()
        LORE_SYNC_RE.findAll(rawText).forEach { m ->
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    val dtos = AppJson.decodeFromString<List<NovelLoreSyncDto>>(body.substring(arrStart, arrEnd + 1))
                    dtos.forEach { dto ->
                        val title = dto.title.trim()
                        val content = dto.content.trim()
                        if (title.isNotEmpty() && content.isNotEmpty() && !dto.action.equals("delete", ignoreCase = true)) {
                            val key = title.lowercase()
                            val prior = existingByTitle[key]
                            val mergedKeywords = (dto.keywords + prior?.keywords.orEmpty())
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .ifEmpty { listOf(title) }
                            val mergedAliases = (dto.aliases + prior?.aliases.orEmpty())
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            val entry = NovelLoreEntry(
                                id = prior?.id ?: UUID.randomUUID().toString(),
                                conversationId = conversationId,
                                category = dto.category.trim().lowercase().ifBlank { prior?.category ?: "other" },
                                title = title,
                                content = content,
                                keywords = mergedKeywords,
                                aliases = mergedAliases,
                                alwaysActive = dto.alwaysActive ?: prior?.alwaysActive ?: false,
                                priority = prior?.priority ?: 50,
                                enabled = prior?.enabled ?: true,
                                validFromSeq = prior?.validFromSeq ?: turnSeq,
                                createdAt = prior?.createdAt ?: now,
                                updatedAt = now,
                            )
                            val finalized = entry.copy(estimatedTokens = estimateLoreTokens(entry))
                            existingByTitle[key] = finalized
                            val existingIdx = loreUpserts.indexOfFirst { it.id == finalized.id }
                            if (existingIdx >= 0) {
                                loreUpserts[existingIdx] = finalized
                            } else {
                                loreUpserts.add(finalized)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        var chapterEndSummary: String? = null
        var nextChapterTitle: String? = null
        CHAPTER_END_RE.findAll(rawText).lastOrNull()?.let { m ->
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
        CHOICES_RE.findAll(rawText).lastOrNull()?.let { m ->
            choices = parseStringArrayJson(m.groupValues[1]).take(4)
        }

        return NovelTurnParseOutcome(
            cleanProse = NovelExport.stripInternalTags(rawText),
            statusHud = statusHud,
            loreUpserts = loreUpserts,
            chapterEndSummary = chapterEndSummary,
            suggestedNextChapterTitle = nextChapterTitle,
            choices = choices,
        )
    }

    fun buildWizardGenesisMessages(
        preset: NovelPresetTemplate,
        title: String,
        idea: String,
        pov: String,
        targetWordsPerChapter: Int,
    ): List<WireChatMessageDto> {
        val povLabel = when (pov) {
            "first" -> "第一人称（“我”）"
            "third_omniscient" -> "第三人称全知视角"
            else -> "第三人称有限视角"
        }
        val sys = """
            你是资深网文与出版小说总编。请根据用户提供的题材、书名和构思，一键完成新书筹备并写出精彩的第一章开篇段落。
            必须严格只返回一个合法 JSON 对象，字段如下：
            {
              "title": "书名（若用户已填则直接沿用）",
              "synopsis": "100字以内紧凑吸睛的作品简介",
              "worldview": "150-250字世界观核心规则与背景",
              "mainOutline": "全书三幕/四卷主线大纲脉络",
              "style": "简明的文风与叙事质感描述",
              "authorNote": "1-2条写作核心注意事项",
              "firstChapterTitle": "第一章 标题",
              "firstChapterOutline": "第一章核心事件、冲突与章尾钩子",
              "starterLore": [
                {"category":"character|location|item|faction|rule|foreshadow","title":"词条名","content":"简洁具体设定","keywords":["触发词1","触发词2"],"alwaysActive":false}
              ],
              "openingProse": "约500-750字富有画面感、人物动作与悬念钩子的第一章开篇正文（避免AI套话与说教）"
            }
        """.trimIndent()
        val usr = buildString {
            appendLine("题材模板：${preset.name}（${preset.genre}）")
            appendLine("模板文风参考：${preset.style}")
            appendLine("叙事视角：$povLabel；单章目标字数：约 $targetWordsPerChapter 字")
            if (title.isNotBlank()) appendLine("指定书名：${title.trim()}")
            if (idea.isNotBlank()) {
                appendLine("作者核心灵感：${idea.trim()}")
            } else if (preset.synopsis.isNotBlank()) {
                appendLine("参考简介：${preset.synopsis}")
            }
            appendLine("请生成 3-5 条 starterLore，并写出高质量第一章开篇 openingProse。")
        }
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun parseWizardGenesisResponse(
        raw: String,
        preset: NovelPresetTemplate,
        fallbackTitle: String,
        conversationId: String,
    ): NovelGenesisResult? {
        val jsonObj = EpisodeConsolidator.extractFirstJsonObject(raw) ?: return null
        return try {
            val dto = AppJson.decodeFromString(NovelGenesisDto.serializer(), jsonObj)
            val now = System.currentTimeMillis()
            val resolvedTitle = fallbackTitle.trim().ifBlank { dto.title.trim() }.ifBlank { preset.name }
            val loreList = dto.starterLore.mapNotNull { item ->
                val t = item.title.trim()
                val c = item.content.trim()
                if (t.isEmpty() || c.isEmpty()) {
                    null
                } else {
                    val entry = NovelLoreEntry(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        category = item.category.trim().lowercase().ifBlank { "other" },
                        title = t,
                        content = c,
                        keywords = item.keywords.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(t) },
                        aliases = item.aliases.map { it.trim() }.filter { it.isNotEmpty() },
                        alwaysActive = item.alwaysActive ?: (item.category.equals("rule", ignoreCase = true)),
                        validFromSeq = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                    entry.copy(estimatedTokens = estimateLoreTokens(entry))
                }
            }
            NovelGenesisResult(
                title = resolvedTitle,
                synopsis = dto.synopsis.trim().ifBlank { preset.synopsis },
                worldview = dto.worldview.trim().ifBlank { preset.worldview },
                mainOutline = dto.mainOutline.trim().ifBlank { preset.mainOutline },
                style = dto.style.trim().ifBlank { preset.style },
                authorNote = dto.authorNote.trim().ifBlank { preset.authorNote },
                firstChapterTitle = dto.firstChapterTitle.trim().ifBlank { preset.firstChapterTitle },
                firstChapterOutline = dto.firstChapterOutline.trim().ifBlank { preset.firstChapterOutline },
                starterLore = loreList,
                openingProse = NovelExport.stripInternalTags(dto.openingProse),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun buildChapterRecapMessages(
        novel: NovelConfig,
        chapter: NovelChapter,
        chapterMessages: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val prose = chapterMessages
            .filter { it.role == "assistant" }
            .map { NovelExport.stripInternalTags(it.content) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .take(6000)
        val sys = "你是小说责编。请将《${novel.title}》的章节正文提炼为 80-140 字的紧凑前情梗概，保留关键人物行动、因果转折与遗留悬念。直接输出梗概正文，不加前缀。"
        val usr = "章节：第${chapter.order}章 ${chapter.title}\n细纲：${chapter.outline.ifBlank { "无" }}\n\n正文：\n$prose"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun buildLoreExtractMessages(
        novel: NovelConfig,
        recentMessages: List<ChatMessage>,
        existingLore: List<NovelLoreEntry>,
    ): List<WireChatMessageDto> {
        val prose = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role == "assistant" }
            .takeLast(5)
            .map { NovelExport.stripInternalTags(it.content) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n---\n\n")
            .take(6500)
        val existingList = existingLore.filter { !it.stale }.take(30).joinToString("\n") {
            "- [${it.category}] ${it.title}: ${it.content.take(60)}"
        }
        val sys = """
            你是长篇小说世界书整理员。请从《${novel.title}》近期正文中提炼新出场或有关键状态变化的角色、地点、道具、势力、规则与伏笔线索。
            不要重复已有设定，仅输出新增或有实质更新的条目。
            严格只返回合法 JSON 数组（最多 6 条；若无新设定返回 []）：
            [{"category":"character|location|item|faction|rule|foreshadow|plot","title":"词条名","content":"精炼设定描述","keywords":["触发关键词1","触发关键词2"],"aliases":["别名"],"alwaysActive":false}]
        """.trimIndent()
        val usr = "【已有世界书】\n${existingList.ifBlank { "（空）" }}\n\n【近期正文】\n$prose"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun parseLoreExtractResponse(
        raw: String,
        conversationId: String,
        turnSeq: Int,
        existingLore: List<NovelLoreEntry>,
    ): List<NovelLoreEntry> {
        val trimmed = raw.trim()
        val arrStart = trimmed.indexOf('[')
        val arrEnd = trimmed.lastIndexOf(']')
        if (arrStart < 0 || arrEnd <= arrStart) return emptyList()
        return try {
            val dtos = AppJson.decodeFromString<List<NovelLoreSyncDto>>(trimmed.substring(arrStart, arrEnd + 1))
            val now = System.currentTimeMillis()
            val existingByTitle = existingLore.associateBy { it.title.trim().lowercase() }
            dtos.mapNotNull { dto ->
                val title = dto.title.trim()
                val content = dto.content.trim()
                if (title.isEmpty() || content.isEmpty()) return@mapNotNull null
                val prior = existingByTitle[title.lowercase()]
                val entry = NovelLoreEntry(
                    id = prior?.id ?: UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    category = dto.category.trim().lowercase().ifBlank { prior?.category ?: "other" },
                    title = title,
                    content = content,
                    keywords = (dto.keywords + prior?.keywords.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(title) },
                    aliases = (dto.aliases + prior?.aliases.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    alwaysActive = dto.alwaysActive ?: prior?.alwaysActive ?: false,
                    priority = prior?.priority ?: 50,
                    enabled = prior?.enabled ?: true,
                    validFromSeq = prior?.validFromSeq ?: turnSeq,
                    createdAt = prior?.createdAt ?: now,
                    updatedAt = now,
                )
                entry.copy(estimatedTokens = estimateLoreTokens(entry))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildOutlineChatMessages(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        userInstruction: String,
    ): List<WireChatMessageDto> {
        val chList = chapters.sortedBy { it.order }.joinToString("\n") { ch ->
            "- 第${ch.order}章 《${ch.title}》 (${ch.status}, ${ch.wordCount}字): ${ch.outline.ifBlank { ch.summary }}"
        }
        val sys = """
            你是专业小说结构策划编辑。请协助打磨《${novel.title}》的故事大纲、章节节奏与伏笔回收建议。
            题材：${novel.genre}；世界观：${novel.worldview}
            回复要求结构清晰、可直接用于落地修改总纲或分章细纲。
        """.trimIndent()
        val usr = "【当前总纲】\n${novel.mainOutline.ifBlank { "（尚未填写）" }}\n\n【章节列表】\n${chList.ifBlank { "（无）" }}\n\n【作者诉求】\n$userInstruction"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun buildOutlineChapterSyncMessages(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        outlineDraft: String,
    ): List<WireChatMessageDto> {
        val existingCount = chapters.size
        val sys = """
            你是小说分章规划助手。请根据给定的大纲讨论内容，提取或规划后续 3-6 章的分章标题与细纲。
            只返回合法 JSON 数组，不要输出任何额外说明：
            [{"title":"第${existingCount + 1}章 章节标题","outline":"本章核心冲突、场景推进与章尾钩子"}]
        """.trimIndent()
        val existingList = chapters.sortedBy { it.order }.joinToString("\n") { "第${it.order}章 ${it.title}" }
        val usr = "小说：《${novel.title}》\n已有章节：\n${existingList.ifBlank { "无" }}\n\n大纲草案：\n${outlineDraft.take(4000)}"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun parseSuggestedChapters(raw: String): List<SuggestedChapterOutline> {
        val trimmed = raw.trim()
        val arrStart = trimmed.indexOf('[')
        val arrEnd = trimmed.lastIndexOf(']')
        if (arrStart < 0 || arrEnd <= arrStart) return emptyList()
        return try {
            AppJson.decodeFromString<List<SuggestedChapterDto>>(trimmed.substring(arrStart, arrEnd + 1))
                .mapNotNull { dto ->
                    val title = dto.title.trim()
                    val outline = dto.outline.trim()
                    if (title.isEmpty() && outline.isEmpty()) null
                    else SuggestedChapterOutline(
                        title = title.ifEmpty { "新章节" },
                        outline = outline,
                    )
                }
                .take(10)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildPolishMessages(
        prose: String,
        style: String,
        instruction: String,
        presetMode: String? = null,
    ): List<WireChatMessageDto> {
        val presetDirective = when (presetMode) {
            "describe" -> "五感扩写：保留全部情节走向，重点增强光影、气味、声音、触感与微表情描写，让画面更具电影沉浸感。"
            "deai" -> "去 AI 味精修：彻底删改“不由得/嘴角微微上扬/空气凝固/一股莫名的情绪”等模板化句式与段末说教总结，改用凝练克制的自然汉语与具象动作。"
            "tighten" -> "节奏提速：删去冗余铺垫与重复心理描写，收紧句式节奏，突出冲突张力与事件推进速度。"
            "dialogue" -> "对白增色：强化人物对话的潜台词、语气停顿与性格辨识度，配合简短动作神态让交锋更有张力。"
            else -> ""
        }
        val mergedInstruction = listOf(presetDirective, instruction.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { "增强画面感染力、句式节奏与动作/对话张力" }
        val sys = "你是文学润色编辑。在不改变核心情节与人物设定的前提下精修文本，文风参考：${style.ifBlank { "自然克制、画面感强" }}。只输出精修后的正文，不要附加解释或任何 XML 标签。"
        val usr = "精修要求：\n$mergedInstruction\n\n待精修正文：\n${NovelExport.stripInternalTags(prose)}"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun buildRefreshChoicesMessages(
        novel: NovelConfig,
        activeChapter: NovelChapter?,
        recentMessages: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val tail = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(4)
            .joinToString("\n\n") { NovelExport.stripInternalTags(it.content).take(700) }
        val sys = """
            你是小说剧情策划师。请基于《${novel.title}》的最新正文与当前章细纲，生成 3 个风格各异、具体可执行的下一步剧情走向建议（每条 12-36 字）。
            严格只返回合法 JSON 字符串数组：["走向1","走向2","走向3"]
        """.trimIndent()
        val usr = "当前章节：${activeChapter?.title.orEmpty()} (${activeChapter?.outline.orEmpty()})\n\n近期正文：\n$tail"
        return listOf(WireChatMessageDto.text("system", sys), WireChatMessageDto.text("user", usr))
    }

    fun parseChoicesOnly(raw: String): List<String> {
        val tagged = CHOICES_RE.findAll(raw).lastOrNull()?.groupValues?.getOrNull(1)
        val parsed = parseStringArrayJson(tagged ?: raw).take(4)
        if (parsed.isNotEmpty()) return parsed
        return raw.lineSequence()
            .map { it.trim().replace(Regex("^[-*•\\d.、)）]+\\s*"), "").trim().trim('"', '“', '”') }
            .filter { it.length in 4..80 && !it.startsWith("[") && !it.startsWith("{") }
            .distinct()
            .take(3)
            .toList()
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
            .joinToString("\n\n") { NovelExport.stripInternalTags(it.content).take(700) }
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

    private fun parseStringArrayJson(raw: String): List<String> {
        val body = raw.trim()
        val arrStart = body.indexOf('[')
        val arrEnd = body.lastIndexOf(']')
        if (arrStart < 0 || arrEnd <= arrStart) return emptyList()
        return try {
            AppJson.decodeFromString<List<String>>(body.substring(arrStart, arrEnd + 1))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
