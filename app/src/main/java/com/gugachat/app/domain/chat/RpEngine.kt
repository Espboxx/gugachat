package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Serializable
private data class RpCastUpdateDto(
    val name: String = "",
    val affinityDelta: Int? = null,
    val mood: String? = null,
    val status: String? = null,
)

@Serializable
private data class RpStatePatchDto(
    val scene: String? = null,
    val time: String? = null,
    val weather: String? = null,
    val hp: Int? = null,
    val hpDelta: Int? = null,
    val mp: Int? = null,
    val mpDelta: Int? = null,
    val gold: Int? = null,
    val goldDelta: Int? = null,
    val level: Int? = null,
    val addItems: List<String> = emptyList(),
    val removeItems: List<String> = emptyList(),
    val addQuests: List<String> = emptyList(),
    val completeQuests: List<String> = emptyList(),
    val flags: Map<String, String> = emptyMap(),
    val castUpdates: List<RpCastUpdateDto> = emptyList(),
)

@Serializable
private data class RpChoiceDto(
    val label: String = "",
    val actionText: String = "",
    val inputKind: String = "action",
    val skillCheck: String? = null,
    val difficulty: Int? = null,
)

@Serializable
private data class RpLedgerSyncDto(
    val kind: String = "fact",
    val title: String = "",
    val content: String = "",
    val keyEntities: List<String> = emptyList(),
    val resolved: Boolean = false,
)

data class RpTurnParseOutcome(
    val cleanNarrative: String,
    val updatedState: RpGameState,
    val updatedCast: List<RpCharacter>,
    val choices: List<RpChoice> = emptyList(),
    val ledgerUpserts: List<MemoryLedgerItem> = emptyList(),
)

object RpEngine {
    private val DICE_EXPR_RE = Regex("^\\s*(\\d*)\\s*[dD]\\s*(\\d+)\\s*([+-]\\s*\\d+)?\\s*$")
    private val STATE_TAG_RE = Regex("<rp_state>([\\s\\S]*?)</rp_state>", RegexOption.IGNORE_CASE)
    private val CHOICES_TAG_RE = Regex("<rp_choices>([\\s\\S]*?)</rp_choices>", RegexOption.IGNORE_CASE)
    private val LEDGER_TAG_RE = Regex("<rp_ledger>([\\s\\S]*?)</rp_ledger>", RegexOption.IGNORE_CASE)

    fun rollDice(
        expression: String,
        difficulty: Int? = null,
        skill: String? = null,
        mode: String = "normal",
        random: Random = Random.Default,
    ): RpDiceRoll {
        val m = DICE_EXPR_RE.matchEntire(expression)
        val count = (m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 20)
        val faces = (m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 20).coerceIn(2, 1000)
        val modifier = m?.groupValues?.getOrNull(3)?.replace(" ", "")?.toIntOrNull() ?: 0

        val rollOnce = { List(count) { random.nextInt(1, faces + 1) } }
        val normalizedMode = mode.lowercase().let {
            if (it == "advantage" || it == "disadvantage") it else "normal"
        }

        val chosenRolls: List<Int> = when (normalizedMode) {
            "advantage" -> {
                val r1 = rollOnce()
                val r2 = rollOnce()
                if (r1.sum() >= r2.sum()) r1 else r2
            }
            "disadvantage" -> {
                val r1 = rollOnce()
                val r2 = rollOnce()
                if (r1.sum() <= r2.sum()) r1 else r2
            }
            else -> rollOnce()
        }

        val baseSum = chosenRolls.sum()
        val total = baseSum + modifier
        val critical = if (count == 1 && faces == 20) {
            when (chosenRolls.first()) {
                20 -> "nat20"
                1 -> "nat1"
                else -> null
            }
        } else {
            null
        }
        val success = difficulty?.let {
            when (critical) {
                "nat20" -> true
                "nat1" -> false
                else -> total >= it
            }
        }

        val modStr = when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> "$modifier"
            else -> ""
        }

        return RpDiceRoll(
            expression = "${count}d$faces$modStr",
            rolls = chosenRolls,
            modifier = modifier,
            total = total,
            difficulty = difficulty,
            skill = skill,
            success = success,
            critical = critical,
            mode = normalizedMode,
            createdAt = System.currentTimeMillis(),
        )
    }

    fun buildRpSystemPrompt(rp: RpConfig, chronicleSummary: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("你正在主持沉浸式角色扮演（RP / TRPG）《${rp.title}》。")
        sb.appendLine("玩家扮演角色：【${rp.playerName}】${if (rp.playerPersona.isNotBlank()) "—— ${rp.playerPersona}" else ""}")
        if (rp.worldview.isNotBlank()) sb.appendLine("\n【世界观设定】\n${rp.worldview.trim()}")
        if (rp.systemRules.isNotBlank()) sb.appendLine("\n【游戏与检定规则】\n${rp.systemRules.trim()}")
        if (rp.narratorStyle.isNotBlank()) sb.appendLine("\n【叙述风格】\n${rp.narratorStyle.trim()}")
        if (chronicleSummary.isNotBlank()) sb.appendLine("\n【前期历程】\n$chronicleSummary")
        if (rp.cast.isNotEmpty()) {
            sb.appendLine("\n【主要登场人物表】")
            rp.cast.forEach { c ->
                sb.append("- ${c.name}（${c.role}, 当前心情:${c.mood}, 好感:${c.affinity}）")
                if (c.personality.isNotBlank()) sb.append("：${c.personality}")
                if (c.appearance.isNotBlank()) sb.append("；外貌：${c.appearance}")
                sb.appendLine()
            }
        }
        sb.appendLine("\n【主持原则】")
        sb.appendLine("1. 禁止代替玩家决定心理活动或擅自替玩家做重大抉择（尊重玩家主权）。")
        sb.appendLine("2. 若本回合包含骰子检定结果，必须忠实依据成败与大成功/大失败演绎后果。")
        return sb.toString().trim()
    }

    fun wrapRpTurnUserMessage(
        rawContent: String,
        inputKind: RpInputKind,
        rp: RpConfig,
        diceRoll: RpDiceRoll? = null,
        activeLedgerLines: List<String> = emptyList(),
        recalledMemoryLines: List<String> = emptyList(),
        archivedEpisodeLines: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        if (activeLedgerLines.isNotEmpty()) {
            sb.appendLine("<rp_active_ledger>")
            activeLedgerLines.forEach { sb.appendLine(it) }
            sb.appendLine("</rp_active_ledger>\n")
        }
        if (recalledMemoryLines.isNotEmpty()) {
            sb.appendLine("<recalled_rp_memory>")
            recalledMemoryLines.forEach { sb.appendLine(it) }
            sb.appendLine("</recalled_rp_memory>\n")
        }
        if (archivedEpisodeLines.isNotEmpty()) {
            sb.appendLine("<archived_episodes>")
            archivedEpisodeLines.forEach { sb.appendLine(it) }
            sb.appendLine("</archived_episodes>\n")
        }

        sb.appendLine("【当前状态 HUD】场景:${rp.state.scene} | 时间:${rp.state.time} | HP:${rp.state.hp}/${rp.state.maxHp} | MP:${rp.state.mp}/${rp.state.maxMp} | 金币/信用:${rp.state.gold}")
        if (rp.state.inventory.isNotEmpty()) {
            sb.appendLine("随身物品: ${rp.state.inventory.joinToString("、")}")
        }
        if (rp.authorNote.isNotBlank()) {
            sb.appendLine("【OOC 作者备注】${rp.authorNote.trim()}")
        }

        if (diceRoll != null) {
            val outcome = when {
                diceRoll.critical == "nat20" -> "大成功 (Nat20)"
                diceRoll.critical == "nat1" -> "大失败 (Nat1)"
                diceRoll.success == true -> "检定成功 (>= DC ${diceRoll.difficulty})"
                diceRoll.success == false -> "检定失败 (< DC ${diceRoll.difficulty})"
                else -> "已投掷"
            }
            sb.appendLine("【系统掷骰结果】${diceRoll.skill ?: "行动"} ${diceRoll.expression} (${diceRoll.mode}) = ${diceRoll.rolls.joinToString("+")} -> 总计 ${diceRoll.total} [$outcome]")
        }

        val kindTag = when (inputKind) {
            RpInputKind.action -> "【行动 (Action)】"
            RpInputKind.speech -> "【对白 (Speech)】"
            RpInputKind.thought -> "【内心想法 (Thought - 周围NPC无法听见)】"
            RpInputKind.gm_command -> "【GM 指令 (OOC)】"
        }
        sb.appendLine("\n$kindTag\n${rawContent.trim()}")

        val suffixes = buildList {
            if (rp.enableStateHud) {
                add("""<rp_state>{"scene":"场景名","time":"时间","hpDelta":0,"mpDelta":0,"goldDelta":0,"addItems":[],"removeItems":[],"castUpdates":[{"name":"NPC名","affinityDelta":5,"mood":"心情"}]}</rp_state>""")
            }
            if (rp.enableLedger) {
                add("""仅在发生重要线索/誓约/重大事件时追加 <rp_ledger>[{"kind":"fact|relation|promise|event","title":"条目名","content":"事实描述"}]</rp_ledger>""")
            }
            if (rp.enableChoices) {
                add("""末尾提供 2-3 个行动选项：<rp_choices>[{"label":"简短选项","actionText":"行动描述","inputKind":"action|speech","skillCheck":"察觉","difficulty":12}]</rp_choices>""")
            }
        }
        if (suffixes.isNotEmpty()) {
            sb.appendLine("\n【状态同步协议（叙述结束后附带）】")
            suffixes.forEach { sb.appendLine(it) }
        }
        return sb.toString().trim()
    }

    fun parseRpTurnResponse(
        rawText: String,
        currentState: RpGameState,
        currentCast: List<RpCharacter>,
        conversationId: String,
        turnSeq: Int,
    ): RpTurnParseOutcome {
        var cleaned = rawText
        var newState = currentState
        var newCast = currentCast

        STATE_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(m.groupValues[1])
            if (jsonObj != null) {
                try {
                    val patch = AppJson.decodeFromString(RpStatePatchDto.serializer(), jsonObj)
                    val nextHp = (patch.hp ?: (newState.hp + (patch.hpDelta ?: 0))).coerceIn(0, newState.maxHp)
                    val nextMp = (patch.mp ?: (newState.mp + (patch.mpDelta ?: 0))).coerceIn(0, newState.maxMp)
                    val nextGold = max(0, patch.gold ?: (newState.gold + (patch.goldDelta ?: 0)))
                    val inv = newState.inventory.toMutableList()
                    patch.removeItems.forEach { rem -> inv.removeAll { it.equals(rem.trim(), ignoreCase = true) } }
                    patch.addItems.forEach { add ->
                        val t = add.trim()
                        if (t.isNotEmpty() && !inv.contains(t)) inv.add(t)
                    }
                    val quests = newState.quests.toMutableList()
                    patch.completeQuests.forEach { q -> quests.removeAll { it.contains(q.trim(), ignoreCase = true) } }
                    patch.addQuests.forEach { q ->
                        val t = q.trim()
                        if (t.isNotEmpty() && !quests.contains(t)) quests.add(t)
                    }
                    val mergedFlags = LinkedHashMap(newState.flags).apply { putAll(patch.flags) }

                    newState = newState.copy(
                        scene = patch.scene?.takeIf { it.isNotBlank() } ?: newState.scene,
                        time = patch.time?.takeIf { it.isNotBlank() } ?: newState.time,
                        weather = patch.weather?.takeIf { it.isNotBlank() } ?: newState.weather,
                        hp = nextHp,
                        mp = nextMp,
                        gold = nextGold,
                        level = patch.level ?: newState.level,
                        inventory = inv,
                        quests = quests,
                        flags = mergedFlags,
                    )

                    if (patch.castUpdates.isNotEmpty()) {
                        newCast = currentCast.map { char ->
                            val upd = patch.castUpdates.firstOrNull { u ->
                                u.name.trim().equals(char.name.trim(), ignoreCase = true) ||
                                    char.aliases.any { it.equals(u.name.trim(), ignoreCase = true) }
                            }
                            if (upd == null) char
                            else char.copy(
                                affinity = (char.affinity + (upd.affinityDelta ?: 0)).coerceIn(-100, 100),
                                mood = upd.mood?.takeIf { it.isNotBlank() } ?: char.mood,
                                status = upd.status?.takeIf { it.isNotBlank() } ?: char.status,
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        val choices = ArrayList<RpChoice>()
        CHOICES_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    val dtos = AppJson.decodeFromString<List<RpChoiceDto>>(body.substring(arrStart, arrEnd + 1))
                    dtos.take(4).forEach { dto ->
                        if (dto.label.isNotBlank() || dto.actionText.isNotBlank()) {
                            choices.add(
                                RpChoice(
                                    id = UUID.randomUUID().toString(),
                                    label = dto.label.ifBlank { dto.actionText.take(18) },
                                    actionText = dto.actionText.ifBlank { dto.label },
                                    inputKind = parseRpInputKind(dto.inputKind),
                                    skillCheck = dto.skillCheck?.takeIf { it.isNotBlank() },
                                    difficulty = dto.difficulty,
                                ),
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        val ledgerUpserts = ArrayList<MemoryLedgerItem>()
        LEDGER_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    val dtos = AppJson.decodeFromString<List<RpLedgerSyncDto>>(body.substring(arrStart, arrEnd + 1))
                    val now = System.currentTimeMillis()
                    dtos.forEach { dto ->
                        val title = dto.title.trim()
                        val content = dto.content.trim()
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            ledgerUpserts.add(
                                MemoryLedgerItem(
                                    id = UUID.randomUUID().toString(),
                                    conversationId = conversationId,
                                    kind = dto.kind.ifBlank { "fact" },
                                    title = title,
                                    content = content,
                                    aliases = dto.keyEntities,
                                    keyEntities = dto.keyEntities,
                                    resolved = dto.resolved,
                                    validFromSeq = turnSeq,
                                    lastReferencedSeq = turnSeq,
                                    origin = "ai",
                                    createdAt = now,
                                    updatedAt = now,
                                    estimatedTokens = max(10, (title.length + content.length) / 2 + 8),
                                ),
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return RpTurnParseOutcome(
            cleanNarrative = cleaned.trim(),
            updatedState = newState,
            updatedCast = newCast,
            choices = choices,
            ledgerUpserts = ledgerUpserts,
        )
    }

    fun createSaveSlot(
        conversationId: String,
        name: String,
        note: String = "",
        rp: RpConfig,
        messages: List<ChatMessage>,
        ledger: List<MemoryLedgerItem>,
        episodes: List<MemoryEpisode>,
    ): RpSaveSlot {
        val active = TimelineReconciler.activeMessages(messages)
        val tipSeq = active.maxOfOrNull { it.seq } ?: 0
        return RpSaveSlot(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            name = name.ifBlank { "存档 #$tipSeq" },
            note = note,
            createdAt = System.currentTimeMillis(),
            messageSeq = tipSeq,
            rpConfig = rp,
            ledgerItems = ledger,
            episodes = episodes,
            activeMessagesSnapshot = active,
        )
    }

    private fun parseRpInputKind(raw: String): RpInputKind =
        when (raw.trim().lowercase()) {
            "speech" -> RpInputKind.speech
            "thought" -> RpInputKind.thought
            "gm_command" -> RpInputKind.gm_command
            else -> RpInputKind.action
        }
}
