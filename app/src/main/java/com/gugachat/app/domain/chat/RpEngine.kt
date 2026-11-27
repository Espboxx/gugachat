package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.data.api.WireChatMessageDto
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

@Serializable
private data class RpCastUpdateDto(
    val name: String = "",
    val affinityDelta: Int? = null,
    val mood: String? = null,
    val expression: String? = null,
    val status: String? = null,
    val inScene: Boolean? = null,
    val stageSlot: String? = null,
)

@Serializable
private data class RpStatePatchDto(
    val scene: String? = null,
    val time: String? = null,
    val weather: String? = null,
    val chapterTitle: String? = null,
    val actNumber: Int? = null,
    val mood: String? = null,
    val activeSpeaker: String? = null,
    val hp: Int? = null,
    val hpDelta: Int? = null,
    val mp: Int? = null,
    val mpDelta: Int? = null,
    val gold: Int? = null,
    val goldDelta: Int? = null,
    val level: Int? = null,
    val statDeltas: Map<String, Int> = emptyMap(),
    val stats: Map<String, Int> = emptyMap(),
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
    val tone: String? = null,
    val conditionText: String? = null,
    val requiresStat: String? = null,
    val requiresStatMin: Int? = null,
    val requiresAffinityNpc: String? = null,
    val requiresAffinityMin: Int? = null,
    val requiresItem: String? = null,
    val requiresFlag: String? = null,
    val costGold: Int? = null,
    val consequenceHint: String? = null,
)

@Serializable
private data class RpLedgerSyncDto(
    val kind: String = "fact",
    val title: String = "",
    val content: String = "",
    val keyEntities: List<String> = emptyList(),
    val resolved: Boolean = false,
)

@Serializable
private data class RpEndingDto(
    val code: String = "NE",
    val title: String = "",
    val summary: String = "",
)

data class RpAffinityTier(
    val code: String,
    val zhLabel: String,
    val enLabel: String,
    val progress: Float,
)

data class RpChoiceEval(
    val unlocked: Boolean,
    val reasonZh: String? = null,
    val reasonEn: String? = null,
)

data class RpTurnParseOutcome(
    val cleanNarrative: String,
    val updatedState: RpGameState,
    val updatedCast: List<RpCharacter>,
    val choices: List<RpChoice> = emptyList(),
    val ledgerUpserts: List<MemoryLedgerItem> = emptyList(),
    val turnDeltas: List<RpTurnDelta> = emptyList(),
    val speakerName: String? = null,
    val unlockedEnding: RpEndingRecord? = null,
)

object RpEngine {
    val STANDARD_EXPRESSIONS: List<String> = listOf(
        "neutral",
        "happy",
        "shy",
        "angry",
        "sad",
        "surprised",
        "smirk",
        "dark",
    )
    private val VALID_STAGE_SLOTS = setOf("left", "center", "right", "auto")
    private val EXPLICIT_STAGE_SLOTS = setOf("left", "center", "right")
    private val STAGE_SLOT_ORDER = mapOf("left" to 0, "center" to 1, "right" to 2)
    private val DICE_EXPR_RE = Regex("^\\s*(\\d*)\\s*[dD]\\s*(\\d+)\\s*([+-]\\s*\\d+)?\\s*$")
    private val MACRO_RE = Regex("\\{\\{\\s*(char|user|scene|time|weather|chapter)\\s*\\}\\}", RegexOption.IGNORE_CASE)
    private val STATE_TAG_RE = Regex("<rp_state>([\\s\\S]*?)</rp_state>", RegexOption.IGNORE_CASE)
    private val CHOICES_TAG_RE = Regex("<rp_choices>([\\s\\S]*?)</rp_choices>", RegexOption.IGNORE_CASE)
    private val LEDGER_TAG_RE = Regex("<rp_ledger>([\\s\\S]*?)</rp_ledger>", RegexOption.IGNORE_CASE)
    private val ENDING_TAG_RE = Regex("<rp_ending>([\\s\\S]*?)</rp_ending>", RegexOption.IGNORE_CASE)
    private val SPEAKER_TAG_RE = Regex("<rp_speaker>([\\s\\S]*?)</rp_speaker>", RegexOption.IGNORE_CASE)
    private val COMPLETE_RP_TAGS_RE = Regex(
        "<(rp_state|rp_choices|rp_ledger|rp_ending|rp_speaker)\\b[^>]*>[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )
    private val UNCLOSED_RP_TAG_RE = Regex(
        "<(rp_state|rp_choices|rp_ledger|rp_ending|rp_speaker)\\b[^>]*>[\\s\\S]*$",
        RegexOption.IGNORE_CASE,
    )
    private val KNOWN_RP_TAG_TOKENS = listOf(
        "<rp_state>", "</rp_state>",
        "<rp_choices>", "</rp_choices>",
        "<rp_ledger>", "</rp_ledger>",
        "<rp_ending>", "</rp_ending>",
        "<rp_speaker>", "</rp_speaker>",
    )
    private val SKILL_SPLIT_RE = Regex("[/·\\s,、()（）:：]+")

    fun replaceMacros(text: String, rp: RpConfig): String {
        if (text.isEmpty() || !text.contains("{{")) return text
        val leadChar = rp.cast.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() } ?: rp.title
        val userName = rp.playerName.ifBlank { "旅人" }
        val chapter = rp.state.chapterTitle.ifBlank { "第${rp.state.actNumber}幕" }
        return MACRO_RE.replace(text) { m ->
            when (m.groupValues[1].lowercase()) {
                "char" -> leadChar
                "user" -> userName
                "scene" -> rp.state.scene
                "time" -> rp.state.time
                "weather" -> rp.state.weather
                "chapter" -> chapter
                else -> m.value
            }
        }
    }

    fun affinityTier(affinity: Int): RpAffinityTier {
        val clamped = affinity.coerceIn(-100, 100)
        val progress = ((clamped + 100) / 200f).coerceIn(0f, 1f)
        return when {
            clamped <= -21 -> RpAffinityTier("hostile", "敌视", "Hostile", progress)
            clamped <= -1 -> RpAffinityTier("cold", "冷淡", "Cold", progress)
            clamped <= 19 -> RpAffinityTier("acquaintance", "初识", "Acquaintance", progress)
            clamped <= 44 -> RpAffinityTier("friendly", "友善", "Friendly", progress)
            clamped <= 69 -> RpAffinityTier("trusted", "信赖", "Trusted", progress)
            clamped <= 89 -> RpAffinityTier("smitten", "心动", "Smitten", progress)
            else -> RpAffinityTier("bonded", "羁绊", "Bonded", progress)
        }
    }

    fun evaluateChoiceCondition(choice: RpChoice, rp: RpConfig): RpChoiceEval {
        val reasonsZh = ArrayList<String>()
        val reasonsEn = ArrayList<String>()

        val reqStat = choice.requiresStat?.trim().orEmpty()
        val reqStatMin = choice.requiresStatMin
        if (reqStat.isNotEmpty() && reqStatMin != null) {
            val matched = findMatchingStat(reqStat, rp.state.stats)
            val current = matched?.second ?: 0
            if (current < reqStatMin) {
                reasonsZh.add("需要${reqStat} ≥ $reqStatMin（当前 $current）")
                reasonsEn.add("Requires $reqStat ≥ $reqStatMin (current $current)")
            }
        }

        val reqNpc = choice.requiresAffinityNpc?.trim().orEmpty()
        val reqAffMin = choice.requiresAffinityMin
        if (reqNpc.isNotEmpty() && reqAffMin != null) {
            val npc = findCharacterByName(reqNpc, rp.cast)
            val current = npc?.affinity ?: 0
            val displayNpc = npc?.name ?: reqNpc
            if (current < reqAffMin) {
                reasonsZh.add("需要${displayNpc}好感 ≥ $reqAffMin（当前 $current）")
                reasonsEn.add("Requires $displayNpc affinity ≥ $reqAffMin (current $current)")
            }
        }

        val reqItem = choice.requiresItem?.trim().orEmpty()
        if (reqItem.isNotEmpty()) {
            val hasItem = rp.state.inventory.any { item ->
                item.trim().equals(reqItem, ignoreCase = true) ||
                    item.contains(reqItem, ignoreCase = true)
            }
            if (!hasItem) {
                reasonsZh.add("需要道具「$reqItem」")
                reasonsEn.add("Requires item \"$reqItem\"")
            }
        }

        val reqFlag = choice.requiresFlag?.trim().orEmpty()
        if (reqFlag.isNotEmpty()) {
            val satisfied = if (reqFlag.contains('=')) {
                val key = reqFlag.substringBefore('=').trim()
                val expected = reqFlag.substringAfter('=').trim()
                val actual = rp.state.flags.entries.firstOrNull { it.key.trim().equals(key, ignoreCase = true) }?.value?.trim()
                actual != null && actual.equals(expected, ignoreCase = true)
            } else {
                val actual = rp.state.flags.entries.firstOrNull { it.key.trim().equals(reqFlag, ignoreCase = true) }?.value?.trim()
                !actual.isNullOrBlank() && !actual.equals("false", ignoreCase = true) && actual != "0"
            }
            if (!satisfied) {
                reasonsZh.add("需要剧情条件「$reqFlag」")
                reasonsEn.add("Requires story flag \"$reqFlag\"")
            }
        }

        val costGold = choice.costGold
        if (costGold != null && costGold > 0 && rp.state.gold < costGold) {
            reasonsZh.add("金币不足（需 $costGold，当前 ${rp.state.gold}）")
            reasonsEn.add("Insufficient gold (need $costGold, have ${rp.state.gold})")
        }

        return if (reasonsZh.isEmpty()) {
            RpChoiceEval(unlocked = true)
        } else {
            RpChoiceEval(
                unlocked = false,
                reasonZh = reasonsZh.joinToString("；"),
                reasonEn = reasonsEn.joinToString("; "),
            )
        }
    }

    fun stripRpInternalTags(raw: String): String {
        if (raw.isEmpty()) return ""
        var s = COMPLETE_RP_TAGS_RE.replace(raw, "")
        s = UNCLOSED_RP_TAG_RE.replace(s, "")
        // Strip streaming partial tag prefix right at end-of-string (e.g., "<rp_", "<rp_st", "</rp_ch")
        val lastLt = s.lastIndexOf('<')
        if (lastLt >= 0) {
            val tail = s.substring(lastLt).lowercase()
            if (tail.length <= 16) {
                val matchesPrefix = if (tail == "<") {
                    lastLt == 0 || s[lastLt - 1].isWhitespace()
                } else {
                    KNOWN_RP_TAG_TOKENS.any { it.startsWith(tail) }
                }
                if (matchesPrefix) {
                    s = s.substring(0, lastLt)
                }
            }
        }
        return s.trim()
    }

    fun rollDice(
        expression: String,
        difficulty: Int? = null,
        skill: String? = null,
        mode: String = "normal",
        random: Random = Random.Default,
        rollUnder: Boolean = false,
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
                if (rollUnder) {
                    if (r1.sum() <= r2.sum()) r1 else r2
                } else {
                    if (r1.sum() >= r2.sum()) r1 else r2
                }
            }
            "disadvantage" -> {
                val r1 = rollOnce()
                val r2 = rollOnce()
                if (rollUnder) {
                    if (r1.sum() >= r2.sum()) r1 else r2
                } else {
                    if (r1.sum() <= r2.sum()) r1 else r2
                }
            }
            else -> rollOnce()
        }

        val baseSum = chosenRolls.sum()
        val total = baseSum + modifier
        val critical = when {
            rollUnder && count == 1 && faces == 100 -> when {
                chosenRolls.first() <= 5 -> "nat20"
                chosenRolls.first() >= 96 -> "nat1"
                else -> null
            }
            rollUnder && count == 1 && faces == 20 -> when (chosenRolls.first()) {
                1 -> "nat20"
                20 -> "nat1"
                else -> null
            }
            !rollUnder && count == 1 && faces == 20 -> when (chosenRolls.first()) {
                20 -> "nat20"
                1 -> "nat1"
                else -> null
            }
            else -> null
        }
        val success = difficulty?.let { target ->
            when (critical) {
                "nat20" -> true
                "nat1" -> false
                else -> if (rollUnder) total <= target else total >= target
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

    fun resolveSkillModifier(skill: String?, rp: RpConfig): Pair<String, Int?> {
        val isD100 = isD100Ruleset(skill, rp)
        val matchedStat = if (!skill.isNullOrBlank()) findMatchingStat(skill, rp.state.stats) else null
        if (isD100) {
            val targetOverride = if (rp.autoDiceSkillBonus) matchedStat?.second else null
            return "1d100" to targetOverride
        }
        if (!rp.autoDiceSkillBonus || matchedStat == null) {
            return "1d20" to null
        }
        val mod = Math.floorDiv(matchedStat.second - 10, 2).coerceIn(-5, 10)
        val expr = when {
            mod > 0 -> "1d20+$mod"
            mod < 0 -> "1d20$mod"
            else -> "1d20"
        }
        return expr to null
    }

    fun rollForChoice(
        choice: RpChoice,
        rp: RpConfig,
        random: Random = Random.Default,
    ): RpDiceRoll {
        val isD100 = isD100Ruleset(choice.skillCheck, rp, choice.difficulty)
        val (expr, statTarget) = resolveSkillModifier(choice.skillCheck, rp)
        return if (isD100) {
            val effectiveTarget = statTarget ?: choice.difficulty
            rollDice(
                expression = if (expr.contains("d100", ignoreCase = true)) expr else "1d100",
                difficulty = effectiveTarget,
                skill = choice.skillCheck,
                mode = "normal",
                random = random,
                rollUnder = true,
            )
        } else {
            rollDice(
                expression = expr,
                difficulty = choice.difficulty,
                skill = choice.skillCheck,
                mode = "normal",
                random = random,
                rollUnder = false,
            )
        }
    }

    fun buildRpSystemPrompt(
        rp: RpConfig,
        constantLore: List<NovelLoreEntry> = emptyList(),
        chronicleSummary: String = "",
    ): String {
        val sb = StringBuilder()
        val styleTitle = when (rp.playStyle) {
            RpPlayStyle.chengguang -> "沉浸式互动剧场 / 视觉小说（橙光风格）"
            RpPlayStyle.tavern -> "深度角色沉浸互动（SillyTavern 酒馆风格）"
            RpPlayStyle.trpg -> "桌面角色扮演跑团（TRPG / GM 主持风格）"
        }
        sb.appendLine("你正在主持$styleTitle《${rp.title}》。")
        sb.appendLine("玩家扮演角色：【${rp.playerName}】${if (rp.playerPersona.isNotBlank()) "—— ${rp.playerPersona}" else ""}")
        if (rp.worldview.isNotBlank()) sb.appendLine("\n【世界观设定】\n${rp.worldview.trim()}")
        if (rp.systemRules.isNotBlank()) sb.appendLine("\n【游戏与检定规则】\n${rp.systemRules.trim()}")
        if (rp.narratorStyle.isNotBlank()) sb.appendLine("\n【叙述风格】\n${rp.narratorStyle.trim()}")
        if (constantLore.isNotEmpty()) {
            sb.appendLine("\n【常驻世界书设定 (Constant Worldbook)】")
            constantLore.forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.title}：${entry.content.trim()}")
            }
        }
        if (rp.mesExample.isNotBlank()) {
            sb.appendLine("\n【角色对话风格示例 (Example Dialogue)】\n${rp.mesExample.trim()}")
        }
        if (chronicleSummary.isNotBlank()) sb.appendLine("\n【前期历程】\n$chronicleSummary")
        if (rp.cast.isNotEmpty()) {
            sb.appendLine("\n【主要登场人物表（静态人设）】")
            rp.cast.forEach { c ->
                sb.append("- ${c.name}（${c.role}）")
                if (c.personality.isNotBlank()) sb.append("；性格：${c.personality}")
                if (c.speakingStyle.isNotBlank()) sb.append("；口吻：${c.speakingStyle}")
                if (c.appearance.isNotBlank()) sb.append("；外貌：${c.appearance}")
                if (c.relationship.isNotBlank()) sb.append("；关系：${c.relationship}")
                if (c.secret.isNotBlank()) sb.append("；暗线秘密（仅GM掌握，切勿直白泄密）：${c.secret}")
                sb.appendLine()
            }
        }

        sb.appendLine("\n【演出与主持原则】")
        sb.appendLine("1. 严格尊重玩家主权：禁止代替【${rp.playerName}】说出对白、决定内心想法或擅自代做重大抉择。")
        sb.appendLine("2. 若本回合包含骰子检定结果，必须忠实依据成败与大成功/大失败演绎客观后果。")
        when (rp.playStyle) {
            RpPlayStyle.chengguang -> {
                sb.appendLine("3. 【橙光剧场导演指令】注重高张力的 AVG 场景画面、光影氛围与角色微表情/肢体细节；对白力求潜台词与情绪起伏，每回合留出鲜明的戏剧钩子与多路线条件分支。")
            }
            RpPlayStyle.tavern -> {
                sb.appendLine("3. 【酒馆沉浸指令】始终以角色本人视角深度入戏，保持独特说话口癖、情绪惯性与人物弱点；让互动自然流动，避免生硬的任务清单腔或跳戏旁白。")
            }
            RpPlayStyle.trpg -> {
                sb.appendLine("3. 【TRPG 主持指令】严谨维护规则书、空间位置与资源消耗（生命/精力/金币/道具/属性/理智），在危机处提示明确检定方向，让每次风险决策都带来真实代价与回报。")
            }
        }
        return replaceMacros(sb.toString().trim(), rp)
    }

    fun wrapRpTurnUserMessage(
        rawContent: String,
        inputKind: RpInputKind,
        rp: RpConfig,
        diceRoll: RpDiceRoll? = null,
        activeLedgerLines: List<String> = emptyList(),
        recalledMemoryLines: List<String> = emptyList(),
        archivedEpisodeLines: List<String> = emptyList(),
        triggeredLore: List<NovelLoreEntry> = emptyList(),
        isContinue: Boolean = false,
        includeAuthorNote: Boolean = true,
    ): String {
        val sb = StringBuilder()
        if (triggeredLore.isNotEmpty()) {
            sb.appendLine("<rp_worldbook>")
            triggeredLore.forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.title}: ${entry.content.trim()}")
            }
            sb.appendLine("</rp_worldbook>\n")
        }
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

        val chapterSeg = buildString {
            append("第${rp.state.actNumber}幕")
            if (rp.state.chapterTitle.isNotBlank()) append("·${rp.state.chapterTitle}")
        }
        val weatherSeg = if (rp.state.weather.isNotBlank()) " | 天气:${rp.state.weather}" else ""
        val moodSeg = if (rp.state.mood.isNotBlank()) " | 氛围:${rp.state.mood}" else ""
        sb.appendLine(
            "【当前状态 HUD】$chapterSeg | 场景:${rp.state.scene} | 时间:${rp.state.time}$weatherSeg$moodSeg | HP:${rp.state.hp}/${rp.state.maxHp} | MP:${rp.state.mp}/${rp.state.maxMp} | 金币/信用:${rp.state.gold}",
        )
        if (rp.state.stats.isNotEmpty()) {
            sb.appendLine("属性: ${rp.state.stats.entries.joinToString("、") { "${it.key}=${it.value}" }}")
        }
        if (rp.state.inventory.isNotEmpty()) {
            sb.appendLine("随身物品: ${rp.state.inventory.joinToString("、")}")
        }
        if (rp.state.quests.isNotEmpty()) {
            sb.appendLine("当前任务: ${rp.state.quests.joinToString("；")}")
        }
        if (rp.state.flags.isNotEmpty()) {
            sb.appendLine("剧情旗标: ${rp.state.flags.entries.joinToString("、") { "${it.key}=${it.value}" }}")
        }
        if (rp.cast.isNotEmpty()) {
            val present = rp.cast.filter { it.inScene && !it.isPlayer }
            val absent = rp.cast.filter { !it.inScene && !it.isPlayer }
            if (present.isNotEmpty()) {
                sb.appendLine(
                    "当前在场角色: ${present.joinToString("、") { c ->
                        val statusPart = c.status.trim().takeIf { it.isNotEmpty() }?.let { "/状态:$it" }.orEmpty()
                        "${c.name}(${c.mood}/好感${c.affinity}·${affinityTier(c.affinity).zhLabel}$statusPart)"
                    }}",
                )
            }
            if (absent.isNotEmpty()) {
                sb.appendLine(
                    "暂离场角色: ${absent.joinToString("、") { c ->
                        val statusPart = c.status.trim().takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()
                        "${c.name}$statusPart"
                    }}",
                )
            }
        }
        if (includeAuthorNote && rp.authorNote.isNotBlank()) {
            sb.appendLine("【OOC 作者备注】${rp.authorNote.trim()}")
        }

        if (diceRoll != null) {
            val isRollUnder = diceRoll.expression.contains("d100", ignoreCase = true) ||
                isD100Ruleset(diceRoll.skill, rp, diceRoll.difficulty)
            val outcome = when {
                diceRoll.critical == "nat20" -> if (isRollUnder) "大成功 (Critical)" else "大成功 (Nat20)"
                diceRoll.critical == "nat1" -> if (isRollUnder) "大失败 (Fumble)" else "大失败 (Nat1)"
                diceRoll.success == true -> if (isRollUnder) "检定成功 (<= ${diceRoll.difficulty})" else "检定成功 (>= DC ${diceRoll.difficulty})"
                diceRoll.success == false -> if (isRollUnder) "检定失败 (> ${diceRoll.difficulty})" else "检定失败 (< DC ${diceRoll.difficulty})"
                else -> "已投掷"
            }
            sb.appendLine("【系统掷骰结果】${diceRoll.skill ?: "行动"} ${diceRoll.expression} (${diceRoll.mode}) = ${diceRoll.rolls.joinToString("+")} -> 总计 ${diceRoll.total} [$outcome]")
        }

        if (isContinue) {
            sb.appendLine("\n【续写指令 (Continue)】")
            sb.appendLine(
                rawContent.trim().ifBlank {
                    "请无缝衔接上一句的画面、动作与情绪继续演绎，不要重复前文已输出的内容，也不要代替玩家做决定。"
                },
            )
        } else {
            val kindTag = when (inputKind) {
                RpInputKind.action -> "【行动 (Action)】"
                RpInputKind.speech -> "【对白 (Speech)】"
                RpInputKind.thought -> "【内心想法 (Thought - 周围NPC无法听见)】"
                RpInputKind.gm_command -> "【GM 指令 (OOC)】"
            }
            sb.appendLine("\n$kindTag\n${rawContent.trim()}")
        }

        val suffixes = buildList {
            if (rp.enableStateHud) {
                add(
                    """<rp_state>{"scene":"场景名","time":"时间","weather":"天气","chapterTitle":"幕次标题(切幕时填)","actNumber":${rp.state.actNumber},"mood":"氛围","activeSpeaker":"本段主要发言NPC名","hpDelta":0,"mpDelta":0,"goldDelta":0,"statDeltas":{"属性名":0},"addItems":[],"removeItems":[],"addQuests":[],"completeQuests":[],"flags":{},"castUpdates":[{"name":"NPC名","affinityDelta":5,"mood":"心情","expression":"neutral|happy|shy|angry|sad|surprised|smirk|dark","status":"状态","inScene":true,"stageSlot":"auto"}]}</rp_state>""",
                )
                add(
                    """仅当故事抵达重要大结局（HE/BE/TE/NE）时额外追加：<rp_ending>{"code":"HE|BE|TE|NE","title":"结局名","summary":"结局寄语"}</rp_ending>""",
                )
            }
            if (rp.enableLedger) {
                add("""仅在发生重要线索/誓约/重大事件时追加 <rp_ledger>[{"kind":"fact|relation|promise|event","title":"条目名","content":"事实描述","keyEntities":[]}]</rp_ledger>""")
            }
            if (rp.enableChoices) {
                add(
                    """末尾提供 2-4 个行动选项：<rp_choices>[{"label":"简短选项","actionText":"行动描述","inputKind":"action|speech","tone":"gentle|bold|cunning|calm|romantic|mystery","skillCheck":"可选技能","difficulty":12,"conditionText":"可选门槛说明","requiresStat":"可选属性名","requiresStatMin":15,"requiresAffinityNpc":"可选NPC名","requiresAffinityMin":30,"requiresItem":"可选道具","costGold":0,"consequenceHint":"可选后果提示"}]</rp_choices>""",
                )
            }
        }
        if (suffixes.isNotEmpty()) {
            sb.appendLine("\n【状态同步协议（叙述结束后附带）】")
            suffixes.forEach { sb.appendLine(it) }
        }
        if (rp.postHistoryInstructions.isNotBlank()) {
            sb.appendLine("\n【后置核心指令 (Post-History Instructions)】")
            sb.appendLine(rp.postHistoryInstructions.trim())
        }
        return replaceMacros(sb.toString().trim(), rp)
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
        val deltas = ArrayList<RpTurnDelta>()
        var speakerName: String? = null
        var unlockedEnding: RpEndingRecord? = null

        SPEAKER_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val parsedSpeaker = m.groupValues[1].trim()
            if (parsedSpeaker.isNotEmpty()) speakerName = parsedSpeaker
        }

        STATE_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(m.groupValues[1])
            if (jsonObj != null) {
                try {
                    val patch = CoreAppJson.decodeFromString(RpStatePatchDto.serializer(), jsonObj)
                    patch.activeSpeaker?.trim()?.takeIf { it.isNotEmpty() }?.let { speakerName = it }

                    val nextHp = (patch.hp ?: (newState.hp + (patch.hpDelta ?: 0))).coerceIn(0, newState.maxHp)
                    val nextMp = (patch.mp ?: (newState.mp + (patch.mpDelta ?: 0))).coerceIn(0, newState.maxMp)
                    val nextGold = max(0, patch.gold ?: (newState.gold + (patch.goldDelta ?: 0)))

                    val hpDiff = nextHp - newState.hp
                    if (hpDiff != 0) deltas.add(RpTurnDelta("HP ${formatSigned(hpDiff)}", "hp", hpDiff > 0))
                    val mpDiff = nextMp - newState.mp
                    if (mpDiff != 0) deltas.add(RpTurnDelta("MP ${formatSigned(mpDiff)}", "mp", mpDiff > 0))
                    val goldDiff = nextGold - newState.gold
                    if (goldDiff != 0) deltas.add(RpTurnDelta("金币 ${formatSigned(goldDiff)}", "gold", goldDiff > 0))

                    val mergedStats = LinkedHashMap(newState.stats)
                    patch.statDeltas.forEach { (k, delta) ->
                        val key = k.trim()
                        if (key.isNotEmpty() && delta != 0) {
                            mergedStats[key] = (mergedStats[key] ?: 0) + delta
                        }
                    }
                    patch.stats.forEach { (k, value) ->
                        val key = k.trim()
                        if (key.isNotEmpty()) {
                            mergedStats[key] = value
                        }
                    }
                    mergedStats.forEach { (k, after) ->
                        val before = newState.stats[k] ?: 0
                        val diff = after - before
                        if (diff != 0) {
                            deltas.add(RpTurnDelta("$k ${formatSigned(diff)}", "stat", diff > 0))
                        }
                    }

                    val inv = newState.inventory.toMutableList()
                    patch.removeItems.forEach { rem ->
                        val target = rem.trim()
                        if (target.isNotEmpty()) {
                            val removed = inv.filter { it.equals(target, ignoreCase = true) }
                            if (removed.isNotEmpty()) {
                                inv.removeAll { it.equals(target, ignoreCase = true) }
                                deltas.add(RpTurnDelta("-${removed.first()}", "item_remove", false))
                            }
                        }
                    }
                    patch.addItems.forEach { add ->
                        val t = add.trim()
                        if (t.isNotEmpty() && !inv.contains(t)) {
                            inv.add(t)
                            deltas.add(RpTurnDelta("+$t", "item_add", true))
                        }
                    }

                    val quests = newState.quests.toMutableList()
                    patch.completeQuests.forEach { q ->
                        val target = q.trim()
                        if (target.isNotEmpty()) {
                            val done = quests.filter { it.contains(target, ignoreCase = true) }
                            if (done.isNotEmpty()) {
                                quests.removeAll { it.contains(target, ignoreCase = true) }
                                deltas.add(RpTurnDelta("完成任务：${done.first().take(18)}", "quest", true))
                            }
                        }
                    }
                    patch.addQuests.forEach { q ->
                        val t = q.trim()
                        if (t.isNotEmpty() && !quests.contains(t)) {
                            quests.add(t)
                            deltas.add(RpTurnDelta("新任务：${t.take(18)}", "quest", true))
                        }
                    }

                    val mergedFlags = LinkedHashMap(newState.flags)
                    patch.flags.forEach { (k, v) ->
                        val key = k.trim()
                        val value = v.trim()
                        if (key.isNotEmpty() && mergedFlags[key] != value) {
                            mergedFlags[key] = value
                            deltas.add(RpTurnDelta("旗标 $key=$value", "flag", true))
                        }
                    }

                    val nextChapter = patch.chapterTitle?.trim()?.takeIf { it.isNotEmpty() } ?: newState.chapterTitle
                    val nextAct = patch.actNumber?.coerceAtLeast(1) ?: newState.actNumber
                    if (nextChapter != newState.chapterTitle || nextAct != newState.actNumber) {
                        val label = buildString {
                            append("第${nextAct}幕")
                            if (nextChapter.isNotBlank()) append("·$nextChapter")
                        }
                        deltas.add(RpTurnDelta(label, "chapter", true))
                    }

                    newState = newState.copy(
                        scene = patch.scene?.trim()?.takeIf { it.isNotEmpty() } ?: newState.scene,
                        time = patch.time?.trim()?.takeIf { it.isNotEmpty() } ?: newState.time,
                        weather = patch.weather?.trim()?.takeIf { it.isNotEmpty() } ?: newState.weather,
                        chapterTitle = nextChapter,
                        actNumber = nextAct,
                        mood = patch.mood?.trim()?.takeIf { it.isNotEmpty() } ?: newState.mood,
                        hp = nextHp,
                        mp = nextMp,
                        gold = nextGold,
                        level = patch.level ?: newState.level,
                        inventory = inv,
                        quests = quests,
                        flags = mergedFlags,
                        stats = mergedStats,
                    )

                    if (patch.castUpdates.isNotEmpty()) {
                        newCast = currentCast.map { char ->
                            val upd = patch.castUpdates.firstOrNull { u ->
                                val target = u.name.trim()
                                target.equals(char.name.trim(), ignoreCase = true) ||
                                    char.aliases.any { it.equals(target, ignoreCase = true) }
                            }
                            if (upd == null) {
                                char
                            } else {
                                val nextAff = (char.affinity + (upd.affinityDelta ?: 0)).coerceIn(-100, 100)
                                val affDiff = nextAff - char.affinity
                                if (affDiff != 0) {
                                    deltas.add(
                                        RpTurnDelta(
                                            label = "${char.name} 好感 ${formatSigned(affDiff)}",
                                            kind = "affinity",
                                            positive = affDiff > 0,
                                        ),
                                    )
                                }
                                val nextMood = upd.mood?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: upd.expression?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: char.mood
                                val nextSlot = upd.stageSlot?.trim()?.lowercase()?.takeIf { it in VALID_STAGE_SLOTS }
                                    ?: char.stageSlot
                                char.copy(
                                    affinity = nextAff,
                                    mood = nextMood,
                                    status = upd.status?.trim()?.takeIf { it.isNotEmpty() } ?: char.status,
                                    inScene = upd.inScene ?: char.inScene,
                                    stageSlot = nextSlot,
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        ENDING_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val inner = m.groupValues[1].trim()
            val jsonObj = EpisodeConsolidator.extractFirstJsonObject(inner)
            val endingRecord = if (jsonObj != null) {
                try {
                    val dto = CoreAppJson.decodeFromString(RpEndingDto.serializer(), jsonObj)
                    val title = dto.title.trim()
                    if (title.isNotEmpty()) {
                        RpEndingRecord(
                            id = UUID.randomUUID().toString(),
                            code = dto.code.trim().uppercase().ifBlank { "NE" },
                            title = title,
                            summary = dto.summary.trim(),
                            unlockedAt = System.currentTimeMillis(),
                            turnSeq = turnSeq,
                        )
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            } else if (inner.isNotEmpty()) {
                RpEndingRecord(
                    id = UUID.randomUUID().toString(),
                    code = "NE",
                    title = inner.take(40),
                    unlockedAt = System.currentTimeMillis(),
                    turnSeq = turnSeq,
                )
            } else {
                null
            }
            if (endingRecord != null) {
                unlockedEnding = endingRecord
                deltas.add(RpTurnDelta("达成结局 [${endingRecord.code}] ${endingRecord.title}", "ending", endingRecord.code != "BE"))
                val alreadyHas = newState.unlockedEndings.any {
                    it.title.equals(endingRecord.title, ignoreCase = true) && it.code.equals(endingRecord.code, ignoreCase = true)
                }
                if (!alreadyHas) {
                    newState = newState.copy(unlockedEndings = newState.unlockedEndings + endingRecord)
                }
            }
        }

        val choices = ArrayList<RpChoice>()
        CHOICES_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            choices.addAll(parseChoicesJsonBody(m.groupValues[1]))
        }

        val ledgerUpserts = ArrayList<MemoryLedgerItem>()
        LEDGER_TAG_RE.find(rawText)?.let { m ->
            cleaned = cleaned.replace(m.value, "")
            val body = m.groupValues[1].trim()
            val arrStart = body.indexOf('[')
            val arrEnd = body.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                try {
                    val dtos = CoreAppJson.decodeFromString<List<RpLedgerSyncDto>>(body.substring(arrStart, arrEnd + 1))
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
                                    estimatedTokens = TokenEstimator.estimateItem(title, content, overhead = 8, minTokens = 10),
                                ),
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        newState = newState.copy(turnDeltas = deltas)

        return RpTurnParseOutcome(
            cleanNarrative = stripRpInternalTags(cleaned),
            updatedState = newState,
            updatedCast = newCast,
            choices = choices,
            ledgerUpserts = ledgerUpserts,
            turnDeltas = deltas,
            speakerName = speakerName,
            unlockedEnding = unlockedEnding,
        )
    }

    fun buildContinueMessages(
        rp: RpConfig,
        recentMessages: List<ChatMessage>,
        instruction: String = "",
        constantLore: List<NovelLoreEntry> = emptyList(),
        triggeredLore: List<NovelLoreEntry> = emptyList(),
    ): List<WireChatMessageDto> {
        val result = ArrayList<WireChatMessageDto>()
        result.add(WireChatMessageDto.text("system", buildRpSystemPrompt(rp, constantLore = constantLore)))
        val history = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(8)
        history.forEach { msg ->
            val body = if (msg.role == "assistant") stripRpInternalTags(msg.content) else msg.content
            if (body.isNotBlank()) {
                result.add(WireChatMessageDto.text(msg.role, body))
            }
        }
        val prompt = wrapRpTurnUserMessage(
            rawContent = instruction,
            inputKind = RpInputKind.action,
            rp = rp,
            triggeredLore = triggeredLore,
            isContinue = true,
        )
        result.add(WireChatMessageDto.text("user", prompt))
        return result
    }

    fun buildImpersonateMessages(
        rp: RpConfig,
        recentMessages: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val historyText = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(6)
            .joinToString("\n\n") { msg ->
                val who = if (msg.role == "user") rp.playerName else (msg.activeVariant()?.rpSpeakerName ?: rp.title)
                val body = if (msg.role == "assistant") stripRpInternalTags(msg.content) else msg.content
                "[$who]: ${body.take(500)}"
            }
        val sys = replaceMacros(
            """
            你正在协助玩家代写角色扮演《${rp.title}》中的下一句回复（Impersonate）。
            玩家角色：【${rp.playerName}】${if (rp.playerPersona.isNotBlank()) "（${rp.playerPersona}）" else ""}
            请完全站在【${rp.playerName}】的第一人称或行动视角，写一段简短自然、贴合当下情境的行动与对白（可用 *动作* 与 “对白” 穿插，60~140字）。
            只输出玩家角色本人的回复草稿，严禁输出任何 <rp_*> 标签、解释或旁白提示。
            """.trimIndent(),
            rp,
        )
        val usr = buildString {
            appendLine("当前场景：${rp.state.scene}（${rp.state.time}）")
            if (historyText.isNotBlank()) {
                appendLine("\n近期剧情：\n$historyText")
            }
            appendLine("\n请为【${rp.playerName}】草拟一句自然贴合的下一步回应：")
        }.trim()
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", replaceMacros(usr, rp)),
        )
    }

    fun buildRefreshChoicesMessages(
        rp: RpConfig,
        recentMessages: List<ChatMessage>,
    ): List<WireChatMessageDto> {
        val historyText = TimelineReconciler.activeMessages(recentMessages)
            .filter { it.role != "system" }
            .takeLast(4)
            .joinToString("\n\n") { msg ->
                val body = if (msg.role == "assistant") stripRpInternalTags(msg.content) else msg.content
                "[${msg.role}]: ${body.take(600)}"
            }
        val castSummary = rp.cast.filter { !it.isPlayer }.joinToString("、") {
            "${it.name}(好感${it.affinity}/${it.mood})"
        }
        val statsSummary = rp.state.stats.entries.joinToString("、") { "${it.key}=${it.value}" }
        val sys = replaceMacros(
            """
            你是互动剧本分支策划师，请为《${rp.title}》当前节点设计 3~4 个风格迥异、富有戏剧张力的行动选项。
            必须严格只返回一个合法 JSON 数组（可不带或带 <rp_choices> 标签），字段如下：
            [{"label":"8字内短标题","actionText":"玩家点击后发出的具体行动或对白","inputKind":"action|speech","tone":"gentle|bold|cunning|calm|romantic|mystery","skillCheck":"可选技能名","difficulty":13,"conditionText":"可选门槛简述","requiresStat":"可选属性名","requiresStatMin":15,"requiresAffinityNpc":"可选NPC名","requiresAffinityMin":30,"requiresItem":"可选道具名","costGold":0,"consequenceHint":"可选后果暗示"}]
            要求：至少 2 个选项无前置门槛可直接选择，至多 1 个选项可设置高回报的属性/好感/道具条件或金币消耗。
            """.trimIndent(),
            rp,
        )
        val usr = buildString {
            appendLine("场景：${rp.state.scene} | 时间：${rp.state.time} | 金币：${rp.state.gold}")
            if (statsSummary.isNotBlank()) appendLine("玩家属性：$statsSummary")
            if (rp.state.inventory.isNotEmpty()) appendLine("随身道具：${rp.state.inventory.joinToString("、")}")
            if (castSummary.isNotBlank()) appendLine("在场/主要人物：$castSummary")
            if (historyText.isNotBlank()) appendLine("\n最新剧情：\n$historyText")
            appendLine("\n请换一批全新的 3~4 个行动选项 JSON 数组：")
        }.trim()
        return listOf(
            WireChatMessageDto.text("system", sys),
            WireChatMessageDto.text("user", replaceMacros(usr, rp)),
        )
    }

    fun parseChoicesOnly(rawText: String): List<RpChoice> {
        val tagged = CHOICES_TAG_RE.find(rawText)?.groupValues?.get(1)
        return parseChoicesJsonBody(tagged ?: rawText)
    }

    fun resolveExpressionKey(mood: String?): String {
        val m = mood?.trim()?.lowercase().orEmpty()
        if (m.isEmpty()) return "neutral"
        if (m in STANDARD_EXPRESSIONS) return m
        return when {
            containsAny(m, "黑化", "阴鸷", "杀意", "疯狂", "绝望", "崩坏", "狂气", "yandere", "sinister", "menacing", "obsessive", "despair", "dark") -> "dark"
            containsAny(m, "得意", "腹黑", "似笑非笑", "玩味", "嘲讽", "戏谑", "挑眉", "坏笑", "smirk", "teasing", "sly", "cunning", "smug", "mischievous") -> "smirk"
            containsAny(m, "生气", "怒", "冷笑", "戒备", "敌意", "居高临下", "愤怒", "愠", "不满", "凶", "冰冷", "angry", "furious", "hostile", "glare", "annoyed", "wary") -> "angry"
            containsAny(m, "害羞", "脸红", "心动", "羞", "赧", "局促", "耳红", "微红", "shy", "blush", "embarrassed", "flustered", "bashful") -> "shy"
            containsAny(m, "惊讶", "震惊", "错愕", "意外", "惊", "愣", "诧异", "surprised", "shock", "astonished", "startled", "amazed") -> "surprised"
            containsAny(m, "悲伤", "难过", "哭", "愧疚", "失落", "黯然", "委屈", "忧伤", "哀", "痛心", "泪", "sad", "cry", "grief", "guilty", "sorrow", "melancholy") -> "sad"
            containsAny(m, "开心", "笑", "愉悦", "跃跃欲试", "轻松", "喜", "温柔", "赞赏", "欣慰", "微醺", "惬意", "乐观", "happy", "smile", "joy", "cheerful", "pleased", "excited", "warm", "relaxed") -> "happy"
            containsAny(m, "淡漠", "平静", "冷静", "沉静", "初遇", "凝重", "审视", "neutral", "calm", "composed", "stoic") -> "neutral"
            else -> "neutral"
        }
    }

    fun resolveCharacterSpritePath(character: RpCharacter, overrideMood: String? = null): String? {
        val rawMood = overrideMood?.trim()?.takeIf { it.isNotEmpty() } ?: character.mood.trim()
        if (rawMood.isNotEmpty()) {
            character.expressionSprites.entries
                .firstOrNull { it.key.equals(rawMood, ignoreCase = true) }
                ?.value?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val key = resolveExpressionKey(rawMood)
        character.expressionSprites.entries
            .firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        character.expressionSprites.entries
            .firstOrNull { it.key.equals("neutral", ignoreCase = true) }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        character.spritePath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        character.avatarPath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    fun resolveStageCharacters(
        cast: List<RpCharacter>,
        activeSpeakerName: String?,
    ): List<Pair<String, RpCharacter>> {
        val present = cast.filter { it.inScene && !it.isPlayer }
        if (present.isEmpty()) return emptyList()

        val activeSpeaker = activeSpeakerName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            findCharacterByName(it, present)
        }

        val selected: List<RpCharacter> = if (present.size <= 3) {
            present
        } else {
            val picked = ArrayList<RpCharacter>(3)
            if (activeSpeaker != null) picked.add(activeSpeaker)
            val remaining = present.filter { c -> picked.none { it.id == c.id } }
            val prioritized = remaining.sortedByDescending { c ->
                val hasSprite = if (resolveCharacterSpritePath(c) != null) 2 else 0
                val hasExplicitSlot = if (c.stageSlot.lowercase() in EXPLICIT_STAGE_SLOTS) 1 else 0
                hasSprite + hasExplicitSlot
            }
            for (c in prioritized) {
                if (picked.size >= 3) break
                picked.add(c)
            }
            picked
        }

        val assigned = LinkedHashMap<String, RpCharacter>()
        val unplacedIds = LinkedHashSet<String>()

        // Honor explicit stage slots first; if two characters claim the same slot, active speaker wins
        val explicitOrdered = selected.sortedBy { if (it.id == activeSpeaker?.id) 0 else 1 }
        for (c in explicitOrdered) {
            val slot = c.stageSlot.trim().lowercase()
            if (slot in EXPLICIT_STAGE_SLOTS && !assigned.containsKey(slot)) {
                assigned[slot] = c
            } else {
                unplacedIds.add(c.id)
            }
        }
        // Preserve cast order for unplaced characters so 2-NPC stages never swap sides when speaker alternates
        val unplaced = selected.filterTo(ArrayList()) { it.id in unplacedIds }

        if (unplaced.isNotEmpty()) {
            if (selected.size == 2) {
                // Keep 2-character stage balanced side-by-side (left + right) without center overlap
                val preferredSlots = listOf("left", "right", "center")
                for (c in unplaced) {
                    val freeSlot = preferredSlots.firstOrNull { !assigned.containsKey(it) } ?: break
                    assigned[freeSlot] = c
                }
            } else {
                // For 1-character or 3-character stage, place active speaker prominently in center if unplaced
                val unplacedSpeaker = unplaced.firstOrNull { it.id == activeSpeaker?.id }
                if (unplacedSpeaker != null) {
                    val speakerSlot = listOf("center", "left", "right").first { !assigned.containsKey(it) }
                    assigned[speakerSlot] = unplacedSpeaker
                    unplaced.remove(unplacedSpeaker)
                }

                // Place remaining unplaced characters cleanly
                val preferredSlots = when (selected.size) {
                    1 -> listOf("center", "left", "right")
                    else -> listOf("left", "right", "center")
                }
                for (c in unplaced) {
                    val freeSlot = preferredSlots.firstOrNull { !assigned.containsKey(it) } ?: break
                    assigned[freeSlot] = c
                }
            }
        }

        return assigned.entries
            .sortedBy { STAGE_SLOT_ORDER[it.key] ?: 99 }
            .map { it.key to it.value }
    }

    fun buildPortraitImagePrompt(
        rp: RpConfig,
        character: RpCharacter,
        expressionKey: String = "neutral",
        customHint: String = "",
    ): String {
        val stylePrefix = inferVisualArtGenre(rp, isPortrait = true)
        val normalizedExpr = expressionKey.trim().lowercase().ifEmpty { "neutral" }
        val exprDesc = when (normalizedExpr) {
            "neutral" -> "calm composed facial expression, serene poised gaze, relaxed natural posture"
            "happy" -> "warm genuine smile, bright sparkling eyes, joyful relaxed posture"
            "shy" -> "soft rosy blush on cheeks, shy averted gaze, slightly flustered gentle expression"
            "angry" -> "furrowed brows, sharp intense glare, stern displeased expression"
            "sad" -> "downcast misty eyes, sorrowful melancholy expression, gentle vulnerable posture"
            "surprised" -> "widened eyes, startled surprised expression, slightly parted lips"
            "smirk" -> "knowing confident half-smile smirk, playful sly gaze, subtly tilted chin"
            "dark" -> "chilling shadowed eyes, intense ominous yandere expression, dramatic shadow across upper face"
            else -> "$normalizedExpr facial expression, expressive eyes"
        }
        val genderTag = when (character.gender?.trim()?.lowercase()) {
            "female", "f", "女" -> "female character"
            "male", "m", "男" -> "male character"
            else -> "character"
        }
        val appearance = character.appearancePrompt.trim().ifEmpty { character.appearance.trim() }
        val parts = buildList {
            add(stylePrefix)
            add("1 solo $genderTag, ${character.name}")
            if (appearance.isNotEmpty()) add(appearance)
            add(exprDesc)
            if (customHint.isNotBlank()) add(customHint.trim())
            add("half-body upper portrait facing viewer, centered composition, clean simple soft gradient studio backdrop for visual novel sprite stage, masterpiece, crisp high detail, no text, no watermark, no UI")
        }
        return parts.joinToString(", ")
    }

    fun buildSceneImagePrompt(
        rp: RpConfig,
        customHint: String = "",
    ): String {
        val stylePrefix = inferVisualArtGenre(rp, isPortrait = false)
        val parts = buildList {
            add(stylePrefix)
            if (rp.state.scene.isNotBlank()) add("location: ${rp.state.scene.trim()}")
            if (rp.state.time.isNotBlank()) add("time of day: ${rp.state.time.trim()}")
            if (rp.state.weather.isNotBlank()) add("weather and atmosphere: ${rp.state.weather.trim()}")
            if (rp.state.mood.isNotBlank()) add("emotional mood: ${rp.state.mood.trim()}")
            if (rp.worldview.isNotBlank()) add("world setting: ${rp.worldview.trim().take(160)}")
            if (customHint.isNotBlank()) add(customHint.trim())
            add("rich atmospheric visual novel background CG illustration, cinematic depth and dramatic lighting, environmental storytelling, no characters, no people, no text, no watermark, no UI")
        }
        return parts.joinToString(", ")
    }

    private fun inferVisualArtGenre(rp: RpConfig, isPortrait: Boolean): String {
        val hay = "${rp.title} ${rp.worldview} ${rp.narratorStyle} ${rp.visualTheme.dialogStyle}".lowercase()
        val subject = if (isPortrait) "character sprite" else "scenic background CG"
        return when {
            containsAny(hay, "宫", "古风", "后宫", "帝王", "武侠", "江湖", "仙侠", "大宁", "客栈", "红梅", "残锋") ->
                "Chinese gufeng painterly visual novel $subject, exquisite traditional silk and architectural detail, ink-and-gold luminous illustration"
            containsAny(hay, "赛博", "霓虹", "义体", "黑客", "星际", "护卫舰", "跃迁", "cyber") ->
                "cyberpunk sci-fi anime visual novel $subject, sleek futuristic detail, dramatic neon rim lighting, crisp cel-shaded illustration"
            containsAny(hay, "维多利亚", "克苏鲁", "蒸汽", "哥特", "泰晤士", "伦敦", "灯塔") ->
                "Victorian gothic painterly visual novel $subject, atmospheric oil painting brushwork, moody gaslamp chiaroscuro"
            containsAny(hay, "魔法", "剑与魔法", "迷宫", "遗迹", "冒险", "酒馆", "精灵") && rp.playStyle == RpPlayStyle.trpg ->
                "high-fantasy RPG painterly anime $subject, rich medieval fantasy lighting, intricate craftsmanship"
            else ->
                "high-aesthetic modern anime visual novel $subject, Chengguang cinematic illustration quality, luminous soft atmosphere"
        }
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }

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

    private fun parseChoicesJsonBody(rawBody: String): List<RpChoice> {
        val body = rawBody.trim()
        val arrStart = body.indexOf('[')
        val arrEnd = body.lastIndexOf(']')
        if (arrStart < 0 || arrEnd <= arrStart) return emptyList()
        return try {
            val dtos = CoreAppJson.decodeFromString<List<RpChoiceDto>>(body.substring(arrStart, arrEnd + 1))
            dtos.take(4).mapNotNull { dto ->
                if (dto.label.isBlank() && dto.actionText.isBlank()) return@mapNotNull null
                RpChoice(
                    id = UUID.randomUUID().toString(),
                    label = dto.label.trim().ifBlank { dto.actionText.trim().take(18) },
                    actionText = dto.actionText.trim().ifBlank { dto.label.trim() },
                    inputKind = parseRpInputKind(dto.inputKind),
                    skillCheck = dto.skillCheck?.trim()?.takeIf { it.isNotEmpty() },
                    difficulty = dto.difficulty,
                    tone = dto.tone?.trim()?.takeIf { it.isNotEmpty() },
                    conditionText = dto.conditionText?.trim()?.takeIf { it.isNotEmpty() },
                    requiresStat = dto.requiresStat?.trim()?.takeIf { it.isNotEmpty() },
                    requiresStatMin = dto.requiresStatMin,
                    requiresAffinityNpc = dto.requiresAffinityNpc?.trim()?.takeIf { it.isNotEmpty() },
                    requiresAffinityMin = dto.requiresAffinityMin,
                    requiresItem = dto.requiresItem?.trim()?.takeIf { it.isNotEmpty() },
                    requiresFlag = dto.requiresFlag?.trim()?.takeIf { it.isNotEmpty() },
                    costGold = dto.costGold?.takeIf { it > 0 },
                    consequenceHint = dto.consequenceHint?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isD100Ruleset(skill: String?, rp: RpConfig, difficulty: Int? = null): Boolean {
        if (skill?.contains("d100", ignoreCase = true) == true) return true
        if (rp.systemRules.contains("d100", ignoreCase = true) || rp.systemRules.contains("越低越成功")) return true
        if (difficulty != null && difficulty > 30 && rp.state.stats.values.any { it >= 40 }) return true
        return false
    }

    private fun findMatchingStat(skill: String, stats: Map<String, Int>): Pair<String, Int>? {
        if (stats.isEmpty()) return null
        val trimmed = skill.trim()
        stats.entries.firstOrNull { it.key.trim().equals(trimmed, ignoreCase = true) }?.let {
            return it.key to it.value
        }
        val tokens = trimmed.split(SKILL_SPLIT_RE).map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            stats.entries.firstOrNull { it.key.trim().equals(token, ignoreCase = true) }?.let {
                return it.key to it.value
            }
        }
        for (entry in stats.entries) {
            val k = entry.key.trim()
            if (k.length >= 2 && trimmed.contains(k, ignoreCase = true)) {
                return entry.key to entry.value
            }
        }
        for (token in tokens) {
            if (token.length >= 2) {
                stats.entries.firstOrNull { it.key.contains(token, ignoreCase = true) }?.let {
                    return it.key to it.value
                }
            }
        }
        return null
    }

    private fun findCharacterByName(name: String, cast: List<RpCharacter>): RpCharacter? {
        val target = name.trim()
        return cast.firstOrNull { c ->
            c.name.trim().equals(target, ignoreCase = true) ||
                c.aliases.any { it.trim().equals(target, ignoreCase = true) }
        } ?: cast.firstOrNull { c ->
            c.name.contains(target, ignoreCase = true) || target.contains(c.name, ignoreCase = true)
        }
    }

    private fun formatSigned(value: Int): String = if (value > 0) "+$value" else "$value"

    private fun parseRpInputKind(raw: String): RpInputKind =
        when (raw.trim().lowercase()) {
            "speech" -> RpInputKind.speech
            "thought" -> RpInputKind.thought
            "gm_command" -> RpInputKind.gm_command
            else -> RpInputKind.action
        }
}
