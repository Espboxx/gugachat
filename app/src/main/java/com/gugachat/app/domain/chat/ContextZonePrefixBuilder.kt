package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.core.util.JsonUtils
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.max

internal object ContextZonePrefixBuilder {
    private val STATIC_MACRO_RE = Regex("\\{\\{\\s*(char|user)\\s*\\}\\}", RegexOption.IGNORE_CASE)

    val CATEGORY_TAG = mapOf(
        MemoryCategory.preference to "偏好",
        MemoryCategory.profile to "个人档案",
        MemoryCategory.project to "长期项目",
        MemoryCategory.rule to "规则要求",
        MemoryCategory.other to "备忘",
    )

    private data class PartitionedChronicle(
        val chronicleText: String,
        val overflowEpisodes: List<MemoryEpisode>,
    )

    private fun partitionChronicleEpisodes(
        conversation: ChatConversation,
        episodes: List<MemoryEpisode>,
        effectiveTipSeq: Int,
        effectiveLimitTokens: Int,
        modelId: String?,
    ): PartitionedChronicle {
        val activeEpisodes = episodes
            .filter { !it.stale && it.toSeq <= effectiveTipSeq }
            .sortedWith(compareByDescending<MemoryEpisode> { it.level }.thenBy { it.fromSeq })

        val summaryCap = ContextBudgetPolicy.computeSummaryCap(effectiveLimitTokens)
        val tier1Episodes = ArrayList<MemoryEpisode>()
        val overflowEpisodes = ArrayList<MemoryEpisode>()
        var sumUsed = 0

        for (ep in activeEpisodes) {
            val tok = if (modelId == null && ep.estimatedTokens > 0) {
                ep.estimatedTokens
            } else {
                EpisodeConsolidator.estimateEpisodeTokens(ep.title, ep.summary, modelId)
            }
            if (sumUsed + tok <= summaryCap) {
                tier1Episodes.add(ep)
                sumUsed += tok
            } else {
                overflowEpisodes.add(ep)
            }
        }

        val chronicleText = buildString {
            val frozenLines = tier1Episodes
                .sortedBy { it.fromSeq }
                .map { "- [Seq ${it.fromSeq}~${it.toSeq} ${it.title}] ${it.summary}" }
            if (frozenLines.isNotEmpty()) {
                append(frozenLines.joinToString("\n"))
            } else if (conversation.archivedSummary.isNotBlank()) {
                append(conversation.archivedSummary.trim())
            }
        }
        return PartitionedChronicle(chronicleText, overflowEpisodes)
    }

    fun buildCoreUserMemoryBlock(
        memories: List<UserMemory>,
        budget: MemoryBudget,
        modelId: String? = null,
    ): CoreMemoryBlockResult {
        val enabled = memories.filter { it.enabled && it.content.isNotBlank() }
        if (enabled.isEmpty()) return CoreMemoryBlockResult("", 0, emptyList())

        val cap = ContextBudgetPolicy.coreMemoryLimit(budget)
        val sorted = enabled.sortedWith(
            compareByDescending<UserMemory> { it.pinned }
                .thenByDescending { it.category == MemoryCategory.rule || it.category == MemoryCategory.profile }
                .thenByDescending { it.updatedAt },
        )

        val coreLines = ArrayList<String>()
        val archival = ArrayList<UserMemory>()
        var used = 16

        for (mem in sorted) {
            val tok = if (modelId == null && mem.estimatedTokens > 0) {
                mem.estimatedTokens
            } else {
                MemoryExtractor.estimateUserMemoryTokens(mem.content, modelId)
            }
            val isCoreCandidate = mem.pinned || mem.category == MemoryCategory.rule || mem.category == MemoryCategory.profile
            if (isCoreCandidate && used + tok <= cap) {
                val tag = CATEGORY_TAG[mem.category] ?: "其他"
                coreLines.add("- [$tag] ${mem.content.trim()}")
                used += tok
            } else {
                archival.add(mem)
            }
        }

        val block = if (coreLines.isNotEmpty()) {
            "<user_core_memory>\n以下是用户的核心长期记忆与偏好（请始终遵守）：\n${coreLines.joinToString("\n")}\n</user_core_memory>"
        } else {
            ""
        }
        val actualTokens = if (block.isNotEmpty()) ContextBudgetPolicy.estimateTextTokens(block, modelId) + 8 else 0
        return CoreMemoryBlockResult(block, actualTokens, archival)
    }

    fun buildSystemPrefix(
        conversation: ChatConversation,
        memories: List<UserMemory>,
        memoryBudget: MemoryBudget,
        novelChapters: List<NovelChapter>,
        constantLore: List<NovelLoreEntry>,
        episodes: List<MemoryEpisode>,
        effectiveTipSeq: Int,
        effectiveLimitTokens: Int,
        modelId: String? = null,
    ): SystemPrefixResult {
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val partitioned = partitionChronicleEpisodes(
            conversation = conversation,
            episodes = episodes,
            effectiveTipSeq = effectiveTipSeq,
            effectiveLimitTokens = effectiveLimitTokens,
            modelId = modelId,
        )
        val chronicleText = partitioned.chronicleText

        val effectiveMemories = if (conversation.params.enableAutoMemory) memories else emptyList()
        val coreMem = buildCoreUserMemoryBlock(effectiveMemories, memoryBudget, modelId)

        val activeChapter = if (isNovel) {
            val cid = conversation.novel!!.activeChapterId
            novelChapters.firstOrNull { it.id == cid } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        val baseSystem = when {
            isNovel -> NovelEngine.buildNovelSystemPrompt(
                novel = conversation.novel!!,
                activeChapter = activeChapter,
                constantLore = constantLore,
                chronicleSummary = chronicleText,
                previousChapters = novelChapters,
            )
            isRp -> RpEngine.buildRpSystemPrompt(
                rp = conversation.rp!!,
                constantLore = constantLore,
                chronicleSummary = chronicleText,
            )
            else -> buildString {
                if (conversation.systemPrompt.isNotBlank()) {
                    appendLine(conversation.systemPrompt.trim())
                }
                if (chronicleText.isNotBlank()) {
                    if (isNotEmpty()) appendLine()
                    appendLine("<archived_memory_chronicle>")
                    appendLine(chronicleText)
                    appendLine("</archived_memory_chronicle>")
                }
            }.trim()
        }

        val fullSystem = buildString {
            if (baseSystem.isNotBlank()) appendLine(baseSystem)
            if (coreMem.blockText.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(coreMem.blockText)
            }
        }.trim()

        val loreTok = constantLore.sumOf { NovelEngine.estimateLoreTokens(it, modelId) }
        val chronicleTok = if (chronicleText.isNotBlank()) {
            ContextBudgetPolicy.estimateTextTokens(chronicleText, modelId)
        } else {
            0
        }
        val sysTok = if (fullSystem.isNotBlank()) {
            ContextBudgetPolicy.estimateTextTokens(fullSystem, modelId) + 12
        } else {
            0
        }
        val baseSystemTok = max(0, sysTok - coreMem.usedTokens - chronicleTok - loreTok)

        return SystemPrefixResult(
            systemText = fullSystem,
            systemTokens = sysTok,
            memoryTokens = coreMem.usedTokens,
            summaryTokens = chronicleTok,
            novelLoreTokens = loreTok,
            archivalUserMemories = coreMem.archivalMemories,
            overflowEpisodes = partitioned.overflowEpisodes,
            baseSystemTokens = baseSystemTok,
        )
    }

    fun buildV4Zone0SystemPrefix(
        input: ContextBuildInput,
        episodes: List<MemoryEpisode>,
        novelChapters: List<NovelChapter>,
        worldbookResult: WorldbookEvalResult,
        presetBlocks: List<PromptPresetBlock>,
        effectiveTipSeq: Int,
        effectiveLimitTokens: Int,
        modelId: String?,
    ): SystemPrefixResult {
        val conv = input.conversation
        val isNovel = conv.mode == ConversationMode.novel && conv.novel != null
        val isRp = conv.mode == ConversationMode.rp && conv.rp != null
        val charName = resolveStaticCharName(conv, input.characterCard)
        val userName = resolveStaticUserName(conv, input.persona)

        val partitioned = partitionChronicleEpisodes(
            conversation = conv,
            episodes = episodes,
            effectiveTipSeq = effectiveTipSeq,
            effectiveLimitTokens = effectiveLimitTokens,
            modelId = modelId,
        )
        val chronicleText = partitioned.chronicleText

        val effectiveMemories = if (conv.params.enableAutoMemory) input.memories else emptyList()
        val coreMem = buildCoreUserMemoryBlock(effectiveMemories, input.memoryBudget, modelId)

        val activeChapter = if (isNovel) {
            val cid = conv.novel!!.activeChapterId
            novelChapters.firstOrNull { it.id == cid } ?: novelChapters.maxByOrNull { it.order }
        } else {
            null
        }

        val staticPresetText = presetBlocks
            .filter { it.enabled && !it.marker && it.content.isNotBlank() && !isDepthInjectionPosition(it.injectionPosition) }
            .map { expandStaticMacros(it.content.trim(), charName, userName) }
            .joinToString("\n\n")

        val beforeCharConstant = worldbookResult.zone0ConstantEntries
            .filter { it.position == WorldbookEngine.POSITION_BEFORE_CHAR }
        val afterCharConstant = worldbookResult.zone0ConstantEntries
            .filter { it.position != WorldbookEngine.POSITION_BEFORE_CHAR }

        val card = input.characterCard
        val baseStaticSection = when {
            isNovel -> expandStaticMacros(
                NovelEngine.buildNovelSystemPrompt(
                    novel = conv.novel!!,
                    activeChapter = activeChapter,
                    constantLore = emptyList(),
                    chronicleSummary = "",
                    previousChapters = novelChapters,
                ),
                charName,
                userName,
            )
            card != null -> buildString {
                if (card.systemPrompt.isNotBlank()) {
                    appendLine(card.systemPrompt.trim())
                }
                val rp = conv.rp
                if (isRp && rp != null) {
                    val styleTitle = RpEngine.playStyleTitle(rp.playStyle)
                    if (isNotEmpty()) appendLine()
                    appendLine("你正在主持$styleTitle《${rp.title.ifBlank { charName }}》。")
                    val personaHint = input.persona?.description?.trim()?.takeIf { it.isNotEmpty() }
                        ?: rp.playerPersona.trim()
                    appendLine("玩家扮演角色：【$userName】${if (personaHint.isNotBlank() && input.persona == null) "—— $personaHint" else ""}")
                } else {
                    if (isNotEmpty()) appendLine()
                    appendLine("角色档案：$charName")
                }

                val desc = card.description.trim()
                if (desc.isNotEmpty()) appendLine("\n【角色设定与背景】\n$desc")
                val pers = card.personality.trim()
                if (pers.isNotEmpty()) appendLine("\n【性格特征】\n$pers")
                val scen = card.scenario.trim().ifBlank { conv.rp?.worldview?.trim().orEmpty() }
                if (scen.isNotEmpty()) appendLine("\n【世界观设定】\n$scen")

                val rules = conv.rp?.systemRules?.trim().orEmpty()
                if (rules.isNotEmpty() && rules != card.systemPrompt.trim()) {
                    appendLine("\n【游戏与检定规则】\n$rules")
                }
                val narrator = conv.rp?.narratorStyle?.trim().orEmpty().ifBlank { card.creatorNotes.trim() }
                if (narrator.isNotEmpty()) appendLine("\n【叙述风格】\n$narrator")

                val example = card.mesExample.trim().ifBlank { conv.rp?.mesExample?.trim().orEmpty() }
                if (example.isNotEmpty()) appendLine("\n【角色对话风格示例 (Example Dialogue)】\n$example")

                val staticCast = parseCardCast(card).ifEmpty { conv.rp?.cast.orEmpty() }
                RpEngine.appendStaticCastSection(this, staticCast)
            }.trim().let { expandStaticMacros(it, charName, userName) }
            isRp -> {
                val rp = conv.rp!!
                buildString {
                    val styleTitle = RpEngine.playStyleTitle(rp.playStyle)
                    appendLine("你正在主持$styleTitle《${rp.title}》。")
                    appendLine("玩家扮演角色：【$userName】${if (rp.playerPersona.isNotBlank() && input.persona == null) "—— ${rp.playerPersona}" else ""}")
                    if (rp.worldview.isNotBlank()) appendLine("\n【世界观设定】\n${rp.worldview.trim()}")
                    if (rp.systemRules.isNotBlank()) appendLine("\n【游戏与检定规则】\n${rp.systemRules.trim()}")
                    if (rp.narratorStyle.isNotBlank()) appendLine("\n【叙述风格】\n${rp.narratorStyle.trim()}")
                    if (rp.mesExample.isNotBlank()) appendLine("\n【角色对话风格示例 (Example Dialogue)】\n${rp.mesExample.trim()}")
                    RpEngine.appendStaticCastSection(this, rp.cast)
                    appendLine("\n【演出与主持原则】")
                    appendLine("1. 严格尊重玩家主权：禁止代替【$userName】说出对白、决定内心想法或擅自代做重大抉择。")
                    appendLine("2. 若本回合包含骰子检定结果，必须忠实依据成败与大成功/大失败演绎客观后果。")
                }.trim().let { expandStaticMacros(it, charName, userName) }
            }
            else -> expandStaticMacros(conv.systemPrompt.trim(), charName, userName)
        }

        val personaStaticSection = input.persona
            ?.takeIf { it.description.isNotBlank() && !isDepthInjectionPosition(it.injectionPosition) }
            ?.let { p ->
                val expanded = expandStaticMacros(p.description.trim(), charName, userName)
                "<user_persona name=\"$userName\">\n$expanded\n</user_persona>"
            }.orEmpty()

        fun formatWorldbookSection(tag: String, entries: List<EvaluatedWorldbookEntry>): String {
            if (entries.isEmpty()) return ""
            val body = entries.joinToString("\n") { e ->
                "- [${e.category}] ${e.title}：${expandStaticMacros(e.content.trim(), charName, userName)}"
            }
            return "<$tag>\n$body\n</$tag>"
        }

        val sections = buildList {
            if (staticPresetText.isNotBlank()) add(staticPresetText)
            formatWorldbookSection("worldbook_before_char", beforeCharConstant).takeIf { it.isNotEmpty() }?.let { add(it) }
            if (baseStaticSection.isNotBlank()) add(baseStaticSection)
            if (personaStaticSection.isNotBlank()) add(personaStaticSection)
            formatWorldbookSection("worldbook_constant", afterCharConstant).takeIf { it.isNotEmpty() }?.let { add(it) }
            if (chronicleText.isNotBlank()) add("<archived_memory_chronicle>\n$chronicleText\n</archived_memory_chronicle>")
            if (coreMem.blockText.isNotBlank()) add(expandStaticMacros(coreMem.blockText, charName, userName))
        }

        val fullSystem = sections.joinToString("\n\n").trim()
        val loreTok = worldbookResult.constantTokens
        val chronicleTok = if (chronicleText.isNotBlank()) {
            ContextBudgetPolicy.estimateTextTokens(chronicleText, modelId)
        } else {
            0
        }
        val sysTok = if (fullSystem.isNotBlank()) {
            ContextBudgetPolicy.estimateTextTokens(fullSystem, modelId) + 12
        } else {
            0
        }
        val baseSystemTok = max(0, sysTok - coreMem.usedTokens - chronicleTok - loreTok)

        return SystemPrefixResult(
            systemText = fullSystem,
            systemTokens = sysTok,
            memoryTokens = coreMem.usedTokens,
            summaryTokens = chronicleTok,
            novelLoreTokens = loreTok,
            archivalUserMemories = coreMem.archivalMemories,
            overflowEpisodes = partitioned.overflowEpisodes,
            baseSystemTokens = baseSystemTok,
        )
    }

    fun isV4AssetMode(input: ContextBuildInput): Boolean =
        input.characterCard != null ||
            input.persona != null ||
            input.promptPreset != null ||
            input.mountedWorldbooks.isNotEmpty() ||
            input.worldbookEvalOverride != null

    fun isDepthInjectionPosition(position: Int): Boolean =
        position == 1 || position == WorldbookEngine.POSITION_AT_DEPTH

    fun normalizeWireRole(raw: String): String = when (raw.trim().lowercase()) {
        "user" -> "user"
        "assistant", "model" -> "assistant"
        else -> "system"
    }

    fun decodeStringList(rawJson: String): List<String> =
        JsonUtils.decodeStringListJson(rawJson, trimAndFilterEmpty = false)

    fun parsePresetBlocks(preset: PromptPresetEntity?): List<PromptPresetBlock> {
        val raw = preset?.blocksJson?.trim().orEmpty()
        if (raw.isEmpty() || raw == "[]") return emptyList()
        return runCatching {
            CoreAppJson.decodeFromString(ListSerializer(PromptPresetBlock.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun parseCardCast(card: CharacterCardEntity?): List<RpCharacter> {
        val raw = card?.castJson?.trim().orEmpty()
        if (raw.isEmpty() || raw == "[]") return emptyList()
        return runCatching {
            CoreAppJson.decodeFromString(ListSerializer(RpCharacter.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun resolveStaticCharName(conv: ChatConversation, card: CharacterCardEntity?): String {
        card?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val rp = conv.rp
        if (rp != null) {
            rp.cast.firstOrNull { !it.isPlayer }?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            if (rp.title.isNotBlank()) return rp.title.trim()
        }
        return conv.title.trim().ifBlank { "角色" }
    }

    fun resolveStaticUserName(conv: ChatConversation, persona: PersonaEntity?): String {
        persona?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        conv.rp?.playerName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "旅人"
    }

    fun expandStaticMacros(text: String, charName: String, userName: String): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        return STATIC_MACRO_RE.replace(text) { m ->
            when (m.groupValues[1].lowercase()) {
                "char" -> charName
                "user" -> userName
                else -> m.value
            }
        }
    }

    fun expandDynamicV4Macros(
        text: String,
        conv: ChatConversation,
        card: CharacterCardEntity?,
        persona: PersonaEntity?,
    ): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        val charName = resolveStaticCharName(conv, card)
        val userName = resolveStaticUserName(conv, persona)
        val rp = conv.rp
        if (rp != null) {
            val effectiveCast = if (rp.cast.any { !it.isPlayer }) {
                rp.cast
            } else {
                listOf(RpCharacter(id = "lead_macro", name = charName))
            }
            return RpEngine.replaceMacros(
                text = text,
                rp = rp.copy(playerName = userName, cast = effectiveCast),
            )
        }
        return expandStaticMacros(text, charName, userName)
    }
}
