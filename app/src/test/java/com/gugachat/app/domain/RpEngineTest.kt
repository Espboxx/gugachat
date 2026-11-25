package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RpEngineTest {

    @Test
    fun rollDice_evaluatesModifiersAdvantageAndCriticals() {
        val roll = RpEngine.rollDice("1d20+4", difficulty = 15, skill = "潜行", mode = "advantage", random = Random(42))
        assertEquals("1d20+4", roll.expression)
        assertEquals(1, roll.rolls.size)
        assertEquals(roll.rolls[0] + 4, roll.total)
        assertEquals("advantage", roll.mode)
        assertEquals(roll.total >= 15 || roll.critical == "nat20", roll.success)

        // Check deterministic d100 basic roll
        val d100 = RpEngine.rollDice("d100", difficulty = 50, skill = "侦查", random = Random(7))
        assertEquals("1d100", d100.expression)
        assertEquals(d100.rolls[0], d100.total)
    }

    @Test
    fun rollDice_supportsCocD100RollUnderAdvantageDisadvantageAndCriticals() {
        // Find seeds yielding crit <= 5, fumble >= 96, and normal success/fail
        var sawCrit = false
        var sawFumble = false
        var sawNormalSuccess = false
        var sawNormalFail = false
        for (seed in 1..500) {
            val roll = RpEngine.rollDice("1d100", difficulty = 55, skill = "图书馆", random = Random(seed), rollUnder = true)
            val v = roll.rolls.first()
            when {
                v <= 5 -> {
                    assertEquals("nat20", roll.critical)
                    assertEquals(true, roll.success)
                    sawCrit = true
                }
                v >= 96 -> {
                    assertEquals("nat1", roll.critical)
                    assertEquals(false, roll.success)
                    sawFumble = true
                }
                v <= 55 -> {
                    assertNull(roll.critical)
                    assertEquals(true, roll.success)
                    sawNormalSuccess = true
                }
                else -> {
                    assertNull(roll.critical)
                    assertEquals(false, roll.success)
                    sawNormalFail = true
                }
            }
            if (sawCrit && sawFumble && sawNormalSuccess && sawNormalFail) break
        }
        assertTrue(sawCrit && sawFumble && sawNormalSuccess && sawNormalFail)

        // Verify rollUnder advantage chooses LOWER roll while disadvantage chooses HIGHER roll
        val rAdv = RpEngine.rollDice("1d100", difficulty = 50, mode = "advantage", random = Random(123), rollUnder = true)
        val probe1 = Random(123)
        val a1 = probe1.nextInt(1, 101)
        val a2 = probe1.nextInt(1, 101)
        assertEquals(minOf(a1, a2), rAdv.total)

        val rDis = RpEngine.rollDice("1d100", difficulty = 50, mode = "disadvantage", random = Random(123), rollUnder = true)
        assertEquals(maxOf(a1, a2), rDis.total)
    }

    @Test
    fun resolveSkillModifierAndRollForChoice_honorsAutoBonusAndD100Rules() {
        val fantasy = RpPresets.findById("fantasy_rpg").config
        // 敏捷 = 16 -> floor((16-10)/2) = +3
        val (exprAgile, diffOverride) = RpEngine.resolveSkillModifier("潜行/敏捷", fantasy)
        assertEquals("1d20+3", exprAgile)
        assertNull(diffOverride)

        val noBonus = fantasy.copy(autoDiceSkillBonus = false)
        assertEquals("1d20" to null, RpEngine.resolveSkillModifier("潜行/敏捷", noBonus))

        val d20Choice = RpChoice("c1", "潜行", "悄悄靠近", RpInputKind.action, skillCheck = "敏捷", difficulty = 14)
        val d20Roll = RpEngine.rollForChoice(d20Choice, fantasy, random = Random(9))
        assertEquals("1d20+3", d20Roll.expression)
        assertEquals(3, d20Roll.modifier)
        assertEquals(14, d20Roll.difficulty)

        // CoC d100 preset: 图书馆 = 70
        val coc = RpPresets.findById("steampunk_coc").config
        val (cocExpr, cocTarget) = RpEngine.resolveSkillModifier("图书馆/d100", coc)
        assertEquals("1d100", cocExpr)
        assertEquals(70, cocTarget)

        val cocChoice = coc.pendingChoices.first()
        val cocRoll = RpEngine.rollForChoice(cocChoice, coc, random = Random(5))
        assertEquals("1d100", cocRoll.expression)
        assertEquals(70, cocRoll.difficulty)
        assertEquals(cocRoll.total <= 70 || cocRoll.critical == "nat20", cocRoll.success)

        // With autoDiceSkillBonus = false, falls back to choice.difficulty (65)
        val cocNoBonusRoll = RpEngine.rollForChoice(cocChoice, coc.copy(autoDiceSkillBonus = false), random = Random(5))
        assertEquals(65, cocNoBonusRoll.difficulty)
    }

    @Test
    fun replaceMacros_substitutesAllPlaceholdersCaseInsensitively() {
        val rp = RpPresets.findById("modern_tavern").config
        val tpl = "{{CHAR}}看向{{ user }}，在{{SCENE}}（{{Time}} · {{Weather}}）轻声念出{{CHAPTER}}。"
        val rendered = RpEngine.replaceMacros(tpl, rp)
        assertEquals(
            "江晚柠看向林深，在「第七站台」吧台最角落高脚凳（周五 00:40 · 绵绵细雨，霓虹倒映湿街）轻声念出深夜开张 · 雨夜重逢。",
            rendered,
        )

        // Fallback when cast has only player and chapterTitle is blank
        val minimal = RpConfig(
            title = "无名剧场",
            playerName = "旅者",
            cast = listOf(RpCharacter(id = "p1", name = "旅者", isPlayer = true)),
            state = RpGameState(actNumber = 3, chapterTitle = ""),
        )
        assertEquals("无名剧场/旅者/第3幕", RpEngine.replaceMacros("{{char}}/{{user}}/{{chapter}}", minimal))
    }

    @Test
    fun affinityTier_coversAllSevenBandsAndClampsProgress() {
        assertEquals("hostile", RpEngine.affinityTier(-120).code)
        assertEquals(0f, RpEngine.affinityTier(-100).progress, 0.001f)
        assertEquals("敌视", RpEngine.affinityTier(-21).zhLabel)
        assertEquals("冷淡", RpEngine.affinityTier(-20).zhLabel)
        assertEquals("cold", RpEngine.affinityTier(-1).code)
        assertEquals("初识", RpEngine.affinityTier(0).zhLabel)
        assertEquals(0.5f, RpEngine.affinityTier(0).progress, 0.001f)
        assertEquals("acquaintance", RpEngine.affinityTier(19).code)
        assertEquals("友善", RpEngine.affinityTier(20).zhLabel)
        assertEquals("friendly", RpEngine.affinityTier(44).code)
        assertEquals("信赖", RpEngine.affinityTier(45).zhLabel)
        assertEquals("trusted", RpEngine.affinityTier(69).code)
        assertEquals("心动", RpEngine.affinityTier(70).zhLabel)
        assertEquals("smitten", RpEngine.affinityTier(89).code)
        assertEquals("羁绊", RpEngine.affinityTier(90).zhLabel)
        assertEquals("Bonded", RpEngine.affinityTier(100).enLabel)
        assertEquals(1f, RpEngine.affinityTier(150).progress, 0.001f)
    }

    @Test
    fun evaluateChoiceCondition_checksStatAffinityItemFlagAndGold() {
        val palace = RpPresets.findById("palace_intrigue").config
        // Palace starts with 城府=22, 萧景珩 affinity=22, gold=120, inventory contains 白玉梅花簪
        val unlocked = RpChoice("u", "普通选项", "行动")
        assertTrue(RpEngine.evaluateChoiceCondition(unlocked, palace).unlocked)

        val lockedStat = palace.pendingChoices.first { it.requiresStat == "城府" }
        val statEval = RpEngine.evaluateChoiceCondition(lockedStat, palace)
        assertFalse(statEval.unlocked)
        assertTrue(statEval.reasonZh!!.contains("城府"))
        assertTrue(statEval.reasonEn!!.contains("30"))

        val withHighStat = palace.copy(state = palace.state.copy(stats = palace.state.stats + ("城府" to 32)))
        assertTrue(RpEngine.evaluateChoiceCondition(lockedStat, withHighStat).unlocked)

        val multiLocked = RpChoice(
            id = "m",
            label = "密室之约",
            actionText = "亮出信物进入密室",
            requiresAffinityNpc = "皇上", // alias for 萧景珩 (affinity 22)
            requiresAffinityMin = 50,
            requiresItem = "御赐令牌",
            requiresFlag = "cloud_case=reopened",
            costGold = 200,
        )
        val multiEval = RpEngine.evaluateChoiceCondition(multiLocked, palace)
        assertFalse(multiEval.unlocked)
        assertTrue(multiEval.reasonZh!!.contains("萧景珩"))
        assertTrue(multiEval.reasonZh!!.contains("御赐令牌"))
        assertTrue(multiEval.reasonZh!!.contains("cloud_case=reopened"))
        assertTrue(multiEval.reasonZh!!.contains("200"))

        val satisfied = palace.copy(
            cast = palace.cast.map { if (it.id == "npc_xiao") it.copy(affinity = 60) else it },
            state = palace.state.copy(
                gold = 250,
                inventory = palace.state.inventory + "御赐令牌",
                flags = mapOf("cloud_case" to "reopened"),
            ),
        )
        assertTrue(RpEngine.evaluateChoiceCondition(multiLocked, satisfied).unlocked)
    }

    @Test
    fun stripRpInternalTags_stripsCompleteUnclosedAndStreamingPartialTags() {
        val complete = """
            <rp_speaker>莱拉·晨星</rp_speaker>
            火光照亮石壁上的符文，且满足 x < 5 的古算式。
            <rp_state>{"scene":"密室"}</rp_state>
            <rp_choices>[{"label":"推进"}]</rp_choices>
            <rp_ledger>[{"title":"符文"}]</rp_ledger>
            <rp_ending>{"code":"HE","title":"破晓"}</rp_ending>
        """.trimIndent()
        assertEquals("火光照亮石壁上的符文，且满足 x < 5 的古算式。", RpEngine.stripRpInternalTags(complete))

        val streamingUnclosed = "她微微侧首望来。\n<rp_state>{\"scene\":\"月下长廊\",\"hpDelta\":-5"
        assertEquals("她微微侧首望来。", RpEngine.stripRpInternalTags(streamingUnclosed))

        val streamingPartialPrefix = "雨点敲打窗棂。<rp_ch"
        assertEquals("雨点敲打窗棂。", RpEngine.stripRpInternalTags(streamingPartialPrefix))

        val streamingPartialClose = "雨点敲打窗棂。</rp_st"
        assertEquals("雨点敲打窗棂。", RpEngine.stripRpInternalTags(streamingPartialClose))
    }

    @Test
    fun parseRpTurnResponse_updatesStateCastChoicesAndLedger() {
        val preset = RpPresets.findById("fantasy_rpg").config
        val raw = """
            莱拉眯起金瞳，指尖点向暗门缝隙：“看，风是从下面吹上来的。”
            <rp_state>{"scene":"黑曜迷宫入口","hpDelta":-10,"goldDelta":50,"addItems":["星光钥匙"],"removeItems":["火折子"],"castUpdates":[{"name":"莱拉·晨星","affinityDelta":8,"mood":"信任"}]}</rp_state>
            <rp_ledger>[{"kind":"clue","title":"西门暗道","content":"通往矿坑一层的地下通风口","keyEntities":["黑曜迷宫"]}]</rp_ledger>
            <rp_choices>[{"label":"撬开暗门","actionText":"用短刀撬动铁锁","inputKind":"action","skillCheck":"敏捷","difficulty":14}]</rp_choices>
        """.trimIndent()

        val out = RpEngine.parseRpTurnResponse(raw, preset.state, preset.cast, "rp1", turnSeq = 3)
        assertFalse(out.cleanNarrative.contains("<rp_state>"))
        assertEquals("黑曜迷宫入口", out.updatedState.scene)
        assertEquals(90, out.updatedState.hp)
        assertEquals(135, out.updatedState.gold)
        assertTrue(out.updatedState.inventory.contains("星光钥匙"))
        assertFalse(out.updatedState.inventory.contains("火折子"))
        val lyra = out.updatedCast.first { it.id == "npc_lyra" }
        assertEquals(33, lyra.affinity)
        assertEquals("信任", lyra.mood)
        assertEquals(1, out.choices.size)
        assertEquals(14, out.choices[0].difficulty)
        assertEquals(1, out.ledgerUpserts.size)
        assertEquals("西门暗道", out.ledgerUpserts[0].title)
        assertTrue(out.turnDeltas.any { it.kind == "hp" && !it.positive })
        assertTrue(out.turnDeltas.any { it.kind == "gold" && it.positive })
        assertTrue(out.turnDeltas.any { it.kind == "item_add" && it.label.contains("星光钥匙") })
        assertTrue(out.turnDeltas.any { it.kind == "item_remove" && it.label.contains("火折子") })
        assertTrue(out.turnDeltas.any { it.kind == "affinity" && it.label.contains("莱拉") })
    }

    @Test
    fun parseRpTurnResponse_extractsRp20ChapterStatsSpeakerInSceneEndingAndRichChoices() {
        val palace = RpPresets.findById("palace_intrigue").config
        val raw = """
            玉案烛火摇曳，萧景珩抬眸看了你一眼，唇角微扬。
            <rp_state>{
              "scene":"乾清宫暖阁",
              "chapterTitle":"第二幕 · 霜雪初晴",
              "actNumber":2,
              "mood":"暗香浮动",
              "activeSpeaker":"萧景珩",
              "statDeltas":{"城府":5,"圣宠":8},
              "flags":{"autumn_banquet":"won"},
              "castUpdates":[
                {"name":"皇上","affinityDelta":12,"mood":"赞赏","inScene":true},
                {"name":"沈清欢","affinityDelta":-4,"inScene":false}
              ]
            }</rp_state>
            <rp_ending>{"code":"HE","title":"凤鸣九霄","summary":"终得君心相知，共掌山河明月。"}</rp_ending>
            <rp_choices>[
              {"label":"借机呈上旧案残卷","actionText":"奉上云家残谱请求重审","inputKind":"action","tone":"bold","conditionText":"城府 ≥ 25","requiresStat":"城府","requiresStatMin":25,"costGold":20,"consequenceHint":"开启真结局支线"}
            ]</rp_choices>
        """.trimIndent()

        val out = RpEngine.parseRpTurnResponse(raw, palace.state, palace.cast, "rp-palace", turnSeq = 9)
        assertEquals("玉案烛火摇曳，萧景珩抬眸看了你一眼，唇角微扬。", out.cleanNarrative)
        assertEquals("第二幕 · 霜雪初晴", out.updatedState.chapterTitle)
        assertEquals(2, out.updatedState.actNumber)
        assertEquals("暗香浮动", out.updatedState.mood)
        assertEquals(27, out.updatedState.stats["城府"])
        assertEquals(26, out.updatedState.stats["圣宠"])
        assertEquals("won", out.updatedState.flags["autumn_banquet"])
        assertEquals("萧景珩", out.speakerName)

        val xiao = out.updatedCast.first { it.id == "npc_xiao" }
        val shen = out.updatedCast.first { it.id == "npc_shen" }
        assertEquals(34, xiao.affinity)
        assertTrue(xiao.inScene)
        assertEquals(-9, shen.affinity)
        assertFalse(shen.inScene)

        assertNotNull(out.unlockedEnding)
        assertEquals("HE", out.unlockedEnding!!.code)
        assertEquals("凤鸣九霄", out.unlockedEnding!!.title)
        assertEquals(1, out.updatedState.unlockedEndings.size)
        assertTrue(out.turnDeltas.any { it.kind == "chapter" })
        assertTrue(out.turnDeltas.any { it.kind == "stat" && it.label.contains("城府 +5") })
        assertTrue(out.turnDeltas.any { it.kind == "flag" })
        assertTrue(out.turnDeltas.any { it.kind == "ending" })

        assertEquals(1, out.choices.size)
        assertEquals("bold", out.choices[0].tone)
        assertEquals("城府", out.choices[0].requiresStat)
        assertEquals(25, out.choices[0].requiresStatMin)
        assertEquals(20, out.choices[0].costGold)
        assertEquals("开启真结局支线", out.choices[0].consequenceHint)
    }

    @Test
    fun buildPromptsAndHelpers_supportLoreMacrosContinueImpersonateAndChoicesOnly() {
        val tavern = RpPresets.findById("modern_tavern").config
        val constLore = listOf(
            NovelLoreEntry(
                id = "l1",
                conversationId = "c1",
                category = "location",
                title = "第七站台店规",
                content = "午夜十二点开张，禁止开闪光灯。",
                alwaysActive = true,
            ),
        )
        val dynLore = listOf(
            NovelLoreEntry(
                id = "l2",
                conversationId = "c1",
                category = "item",
                title = "特调失眠蓝",
                content = "海盐金酒与蝶豆花冷萃。",
            ),
        )

        val sysPrompt = RpEngine.buildRpSystemPrompt(tavern, constantLore = constLore)
        assertTrue(sysPrompt.contains("第七站台店规"))
        assertTrue(sysPrompt.contains("对话风格示例"))
        assertTrue(sysPrompt.contains("江晚柠"))
        assertFalse(sysPrompt.contains("{{char}}"))
        assertFalse(sysPrompt.contains("{{user}}"))

        val wrappedContinue = RpEngine.wrapRpTurnUserMessage(
            rawContent = "",
            inputKind = RpInputKind.action,
            rp = tavern,
            triggeredLore = dynLore,
            isContinue = true,
        )
        assertTrue(wrappedContinue.contains("<rp_worldbook>"))
        assertTrue(wrappedContinue.contains("特调失眠蓝"))
        assertTrue(wrappedContinue.contains("【续写指令 (Continue)】"))
        assertTrue(wrappedContinue.endsWith("决定行动或台词。"))

        val history = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = "assistant",
                content = "江晚柠推来一杯温水。<rp_state>{\"mood\":\"柔和\"}</rp_state>",
                createdAt = 1L,
                seq = 1,
            ),
        )
        val continueMsgs = RpEngine.buildContinueMessages(tavern, history)
        assertEquals("system", continueMsgs.first().role)
        assertEquals("user", continueMsgs.last().role)
        assertFalse((continueMsgs[1].content as JsonPrimitive).content.contains("<rp_state>"))

        val impersonateMsgs = RpEngine.buildImpersonateMessages(tavern, history)
        assertEquals(2, impersonateMsgs.size)
        assertTrue((impersonateMsgs[0].content as JsonPrimitive).content.contains("林深"))

        val refreshMsgs = RpEngine.buildRefreshChoicesMessages(tavern, history)
        assertEquals(2, refreshMsgs.size)
        val parsedBare = RpEngine.parseChoicesOnly(
            """[{"label":"点一杯失眠蓝","actionText":"我微笑点头","inputKind":"speech","tone":"gentle"}]""",
        )
        assertEquals(1, parsedBare.size)
        assertEquals("gentle", parsedBare[0].tone)
    }

    @Test
    fun legacyRoomJson_deserializesWithRp20DefaultsAndActiveVariantHelper() {
        val oldRpJson = """
            {
              "title": "旧版冒险",
              "playerName": "勇者",
              "cast": [{"id":"n1","name":"向导","affinity":10}],
              "state": {"scene":"酒馆","hp":90,"maxHp":100},
              "pendingChoices": [{"id":"c1","label":"出发","actionText":"推门离开"}]
            }
        """.trimIndent()
        val decodedRp = AppJson.decodeFromString(RpConfig.serializer(), oldRpJson)
        assertEquals(RpPlayStyle.chengguang, decodedRp.playStyle)
        assertFalse(decodedRp.theaterMode)
        assertEquals(1600, decodedRp.loreTokenBudget)
        assertTrue(decodedRp.autoDiceSkillBonus)
        assertTrue(decodedRp.cast.first().inScene)
        assertEquals(1, decodedRp.state.actNumber)
        assertTrue(decodedRp.state.unlockedEndings.isEmpty())

        val oldVariantJson = """{"id":"v1","content":"旧回复","createdAt":100}"""
        val decodedVar = AppJson.decodeFromString(MessageVariant.serializer(), oldVariantJson)
        assertNull(decodedVar.rpCastSnapshot)
        assertTrue(decodedVar.rpDeltas.isEmpty())
        assertNull(decodedVar.rpSpeakerName)
        assertNull(decodedVar.rpEndingUnlocked)

        val msg = ChatMessage(
            id = "m1",
            conversationId = "c1",
            role = "assistant",
            content = "v2",
            createdAt = 1L,
            seq = 1,
            variants = listOf(decodedVar, decodedVar.copy(id = "v2", content = "v2")),
            activeVariantIndex = 1,
        )
        assertEquals("v2", msg.activeVariant()?.id)
    }

    @Test
    fun rpPresets_containsEightEnrichedPresetsWithFlagships() {
        assertEquals(8, RpPresets.ALL.size)
        val palace = RpPresets.findById("palace_intrigue")
        assertEquals(RpPlayStyle.chengguang, palace.config.playStyle)
        assertTrue(palace.config.theaterMode)
        assertEquals(3, palace.config.alternateGreetings.size)
        assertTrue(palace.starterLore.size >= 3)
        assertTrue(palace.config.pendingChoices.any { it.requiresStat == "城府" })

        val tavern = RpPresets.findById("modern_tavern")
        assertEquals(RpPlayStyle.tavern, tavern.config.playStyle)
        assertFalse(tavern.config.theaterMode)
        assertEquals(3, tavern.config.alternateGreetings.size)
        assertTrue(tavern.config.greeting.contains("{{char}}"))
        assertTrue(tavern.config.mesExample.isNotBlank())
        assertTrue(tavern.config.postHistoryInstructions.isNotBlank())
        assertTrue(tavern.starterLore.size >= 3)
        assertEquals("classic_vn", palace.config.visualTheme.dialogStyle)
        assertTrue(palace.config.visualTheme.showCustomStatusWidget)
        assertTrue(palace.config.visualTheme.customStatusHtml.contains("pal-hud"))
        assertEquals("left", palace.config.cast.first { it.id == "npc_xiao" }.stageSlot)
        assertEquals("right", palace.config.cast.first { it.id == "npc_shen" }.stageSlot)
        val fantasy = RpPresets.findById("fantasy_rpg")
        assertEquals("right", fantasy.config.cast.first { it.id == "npc_lyra" }.stageSlot)
        assertEquals("left", fantasy.config.cast.first { it.id == "npc_garrick" }.stageSlot)
        assertTrue(palace.config.cast.all { it.appearancePrompt.isNotBlank() })
        assertEquals(2, tavern.config.regexScripts.size)
        assertEquals("cyber", RpPresets.findById("cyberpunk_noir").config.visualTheme.dialogStyle)
        assertEquals("#F472B6", RpPresets.findById("modern_romance").config.visualTheme.accentHex)
    }

    @Test
    fun resolveExpressionKey_mapsBilingualMoodsAccurately() {
        assertEquals(8, RpEngine.STANDARD_EXPRESSIONS.size)
        assertEquals("neutral", RpEngine.resolveExpressionKey(null))
        assertEquals("neutral", RpEngine.resolveExpressionKey("淡漠审视"))
        assertEquals("neutral", RpEngine.resolveExpressionKey("平静"))
        assertEquals("happy", RpEngine.resolveExpressionKey("开心"))
        assertEquals("happy", RpEngine.resolveExpressionKey("跃跃欲试"))
        assertEquals("happy", RpEngine.resolveExpressionKey("轻松惬意"))
        assertEquals("shy", RpEngine.resolveExpressionKey("耳尖脸红害羞"))
        assertEquals("shy", RpEngine.resolveExpressionKey("心动"))
        assertEquals("angry", RpEngine.resolveExpressionKey("生气"))
        assertEquals("angry", RpEngine.resolveExpressionKey("冷笑戒备"))
        assertEquals("angry", RpEngine.resolveExpressionKey("居高临下"))
        assertEquals("sad", RpEngine.resolveExpressionKey("愧疚失落"))
        assertEquals("surprised", RpEngine.resolveExpressionKey("震惊错愕"))
        assertEquals("smirk", RpEngine.resolveExpressionKey("似笑非笑"))
        assertEquals("smirk", RpEngine.resolveExpressionKey("腹黑玩味"))
        assertEquals("dark", RpEngine.resolveExpressionKey("黑化阴鸷"))
        assertEquals("dark", RpEngine.resolveExpressionKey("杀意疯狂"))
        assertEquals("shy", RpEngine.resolveExpressionKey("blushing shyly"))
        assertEquals("neutral", RpEngine.resolveExpressionKey("unknown mood text"))
    }

    @Test
    fun resolveCharacterSpritePath_followsExpressionNeutralSpriteAvatarFallbackCascade() {
        val fullChar = RpCharacter(
            id = "c1",
            name = "萧景珩",
            mood = "害羞",
            avatarPath = "/tmp/avatar.png",
            spritePath = "/tmp/base_sprite.png",
            expressionSprites = mapOf(
                "neutral" to "/tmp/neutral.png",
                "shy" to "/tmp/shy.png",
                "angry" to "   ",
                "battle_ready" to "/tmp/battle.png",
            ),
        )
        // Exact mapped expression
        assertEquals("/tmp/shy.png", RpEngine.resolveCharacterSpritePath(fullChar))
        // Direct custom expression key override
        assertEquals("/tmp/battle.png", RpEngine.resolveCharacterSpritePath(fullChar, overrideMood = "battle_ready"))
        // Blank mapped expression falls back to neutral expression sprite
        assertEquals("/tmp/neutral.png", RpEngine.resolveCharacterSpritePath(fullChar, overrideMood = "生气"))
        // Without neutral expression sprite, falls back to spritePath
        val noExpr = fullChar.copy(expressionSprites = emptyMap())
        assertEquals("/tmp/base_sprite.png", RpEngine.resolveCharacterSpritePath(noExpr))
        // Without spritePath, falls back to avatarPath
        val avatarOnly = noExpr.copy(spritePath = " ")
        assertEquals("/tmp/avatar.png", RpEngine.resolveCharacterSpritePath(avatarOnly))
        // All blank -> null
        assertNull(RpEngine.resolveCharacterSpritePath(avatarOnly.copy(avatarPath = "")))
    }

    @Test
    fun resolveStageCharacters_assignsSlotsHonoringExplicitSlotsAndActiveSpeaker() {
        val player = RpCharacter(id = "p", name = "云疏月", isPlayer = true, inScene = true)
        val c1 = RpCharacter(id = "n1", name = "萧景珩", inScene = true, stageSlot = "auto")
        val c2 = RpCharacter(id = "n2", name = "沈清欢", inScene = true, stageSlot = "right")
        val c3 = RpCharacter(id = "n3", name = "太医", inScene = true, stageSlot = "auto")
        val c4 = RpCharacter(id = "n4", name = "密探", inScene = true, stageSlot = "auto")
        val absent = RpCharacter(id = "n5", name = "离场者", inScene = false, stageSlot = "left")

        // Single auto NPC gets center
        val single = RpEngine.resolveStageCharacters(listOf(player, c1, absent), activeSpeakerName = null)
        assertEquals(listOf("center" to c1), single)

        // Two NPCs where one has explicit "right", other is auto -> balanced left & right
        val duo = RpEngine.resolveStageCharacters(listOf(player, c1, c2), activeSpeakerName = "萧景珩")
        assertEquals(listOf("left" to c1, "right" to c2), duo)

        // Two auto NPCs with active speaker -> balanced left & right without center overlap
        val duoAuto = RpEngine.resolveStageCharacters(listOf(player, c1, c3), activeSpeakerName = "太医")
        assertEquals(listOf("left" to c1, "right" to c3), duoAuto)

        // Four in-scene NPCs -> capped at 3, activeSpeaker c4 MUST be included and placed prominently in center
        val quad = RpEngine.resolveStageCharacters(listOf(player, c1, c2, c3, c4), activeSpeakerName = "密探")
        assertEquals(3, quad.size)
        assertEquals("center", quad.first { it.second.id == "n4" }.first)
        assertEquals("right", quad.first { it.second.id == "n2" }.first)
    }

    @Test
    fun buildPortraitAndSceneImagePrompts_andCastUpdateExpressionSlotParsing() {
        val palace = RpPresets.findById("palace_intrigue").config
        val xiao = palace.cast.first { it.id == "npc_xiao" }

        val portraitPrompt = RpEngine.buildPortraitImagePrompt(
            rp = palace,
            character = xiao,
            expressionKey = "smirk",
            customHint = "holding jade flute",
        )
        assertTrue(portraitPrompt.contains("gufeng", ignoreCase = true))
        assertTrue(portraitPrompt.contains("dragon-embroidered"))
        assertTrue(portraitPrompt.contains("smirk", ignoreCase = true))
        assertTrue(portraitPrompt.contains("holding jade flute"))

        val cyber = RpPresets.findById("cyberpunk_noir").config
        val scenePrompt = RpEngine.buildSceneImagePrompt(cyber, customHint = "rain reflections on wet asphalt")
        assertTrue(scenePrompt.contains("cyberpunk", ignoreCase = true))
        assertTrue(scenePrompt.contains("九龙城下层"))
        assertTrue(scenePrompt.contains("酸雨倾盆"))
        assertTrue(scenePrompt.contains("rain reflections on wet asphalt"))

        // Parse castUpdates with expression & stageSlot
        val raw = """
            她轻轻抿唇。
            <rp_state>{"castUpdates":[{"name":"沈清欢","expression":"shy","stageSlot":"left"}]}</rp_state>
        """.trimIndent()
        val parsed = RpEngine.parseRpTurnResponse(raw, palace.state, palace.cast, "rp-p", turnSeq = 4)
        val shen = parsed.updatedCast.first { it.id == "npc_shen" }
        assertEquals("shy", shen.mood)
        assertEquals("left", shen.stageSlot)

        // Legacy JSON still deserializes cleanly with all RP 3.0 defaults
        val legacyJson = """{"title":"旧版","cast":[{"id":"a","name":"甲"}],"state":{"scene":"客栈"}}"""
        val decoded = AppJson.decodeFromString(RpConfig.serializer(), legacyJson)
        assertTrue(decoded.enableHtmlCards)
        assertTrue(decoded.regexScripts.isEmpty())
        assertEquals("glass", decoded.visualTheme.dialogStyle)
        assertEquals("stage", decoded.visualTheme.spriteLayout)
        assertEquals("auto", decoded.cast.first().stageSlot)
        assertTrue(decoded.cast.first().expressionSprites.isEmpty())
        assertTrue(decoded.state.unlockedCgs.isEmpty())
        assertNull(decoded.state.activeCgId)
    }
}
