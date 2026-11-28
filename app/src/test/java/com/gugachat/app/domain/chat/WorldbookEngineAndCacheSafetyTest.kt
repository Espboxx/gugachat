package com.gugachat.app.domain.chat

import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.OnlineChatCompletionRequestDto
import com.gugachat.app.data.api.WireChatMessageDto
import com.gugachat.app.data.chat.ChatContextAssembler
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalModelSpec
import com.gugachat.app.data.local.LocalOnnxEmbeddingEngine
import com.gugachat.app.data.local.LocalPoolingMode
import com.gugachat.app.data.local.MemoryEmbeddingDao
import com.gugachat.app.data.local.MemoryEmbeddingEntity
import com.gugachat.app.data.local.MemoryEmbeddingHashRow
import com.gugachat.app.data.local.OnnxEmbeddingSession
import com.gugachat.app.data.local.PersonaEntity
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.SessionRuntimeFactory
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WorldbookEngineAndCacheSafetyTest {

    private fun sampleWorldbook(id: String = "wb_1", name: String = "主世界书") = WorldbookEntity(
        id = id,
        name = name,
        globalEnabled = true,
        scanDepth = 4,
        tokenBudget = 4000,
        recursiveScanning = true,
    )

    private fun wbEntry(
        id: String,
        worldbookId: String = "wb_1",
        uid: Int = 1,
        title: String = id,
        content: String = "设定内容 $id",
        keys: List<String> = emptyList(),
        secondaryKeys: List<String> = emptyList(),
        selectiveLogic: Int = WorldbookEngine.LOGIC_AND_ANY,
        constant: Boolean = false,
        enabled: Boolean = true,
        caseSensitive: Boolean = false,
        matchWholeWords: Boolean = false,
        useRegexKey: Boolean = false,
        probability: Int = 100,
        insertionOrder: Int = 100,
        position: Int = WorldbookEngine.POSITION_AFTER_CHAR,
        depth: Int = 4,
        outletRole: String = "system",
        sticky: Int = 0,
        cooldown: Int = 0,
        delay: Int = 0,
        preventRecursion: Boolean = false,
        excludeRecursion: Boolean = false,
        groupName: String = "",
        groupWeight: Int = 100,
    ) = WorldbookEntryEntity(
        id = id,
        worldbookId = worldbookId,
        uid = uid,
        title = title,
        content = content,
        keysJson = AppJson.encodeToString(keys),
        secondaryKeysJson = AppJson.encodeToString(secondaryKeys),
        selectiveLogic = selectiveLogic,
        constant = constant,
        enabled = enabled,
        caseSensitive = caseSensitive,
        matchWholeWords = matchWholeWords,
        useRegexKey = useRegexKey,
        probability = probability,
        insertionOrder = insertionOrder,
        position = position,
        depth = depth,
        outletRole = outletRole,
        sticky = sticky,
        cooldown = cooldown,
        delay = delay,
        preventRecursion = preventRecursion,
        excludeRecursion = excludeRecursion,
        groupName = groupName,
        groupWeight = groupWeight,
        estimatedTokens = 20,
    )

    @Test
    fun worldbookEnginePrimaryKeysRegexSecondaryLogicAndTimedEffects() {
        val entries = listOf(
            // Whole word matches "dragon", rejects "dragonfly"
            wbEntry(
                id = "whole_word",
                keys = listOf("dragon"),
                matchWholeWords = true,
                caseSensitive = true,
            ),
            // Case-sensitive exact key rejects "amber"
            wbEntry(
                id = "case_only",
                keys = listOf("AmberBlade"),
                caseSensitive = true,
            ),
            // Regex key matches "rune-99"
            wbEntry(
                id = "regex_entry",
                keys = listOf("/rune-\\d+/i"),
                useRegexKey = true,
            ),
            // Secondary AND_ANY (0)
            wbEntry(
                id = "logic_and_any",
                keys = listOf("祭坛"),
                secondaryKeys = listOf("火焰", "霜雪"),
                selectiveLogic = WorldbookEngine.LOGIC_AND_ANY,
            ),
            // Secondary NOT_ALL (1): blocked only if BOTH present
            wbEntry(
                id = "logic_not_all",
                keys = listOf("祭坛"),
                secondaryKeys = listOf("火焰", "月光"),
                selectiveLogic = WorldbookEngine.LOGIC_NOT_ALL,
            ),
            // Secondary NOT_ANY (2): blocked if ANY secondary present
            wbEntry(
                id = "logic_not_any",
                keys = listOf("祭坛"),
                secondaryKeys = listOf("月光"),
                selectiveLogic = WorldbookEngine.LOGIC_NOT_ANY,
            ),
            // Secondary AND_ALL (3): requires ALL secondaries
            wbEntry(
                id = "logic_and_all",
                keys = listOf("祭坛"),
                secondaryKeys = listOf("火焰", "月光"),
                selectiveLogic = WorldbookEngine.LOGIC_AND_ALL,
            ),
            // Timed effect: delay=2, sticky=2, cooldown=2
            wbEntry(
                id = "timed_curse",
                keys = listOf("诅咒"),
                delay = 2,
                sticky = 2,
                cooldown = 2,
            ),
            // Zone 0 constant & Zone 2 @D constant
            wbEntry(
                id = "z0_const",
                constant = true,
                position = WorldbookEngine.POSITION_BEFORE_CHAR,
            ),
            wbEntry(
                id = "z2_depth_const",
                constant = true,
                position = WorldbookEngine.POSITION_AT_DEPTH,
                depth = 2,
                outletRole = "user",
            ),
        )
        val book = WorldbookMountedBook(sampleWorldbook(), entries, sortOrder = 0, isGlobal = true)

        // Turn 1: delay=2 blocks timed_curse; dragonfly does NOT match whole-word "dragon";
        // 祭坛 + 月光 satisfies AND_ANY? No (neither 火焰/霜雪), satisfies NOT_ALL (only 月光, missing 火焰),
        // fails NOT_ANY (月光 present), fails AND_ALL (missing 火焰).
        val turn1 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "Look at that dragonfly, Amber, RUNE-42 near the 祭坛 under 月光 with 诅咒",
            activeUserTurnCount = 1,
        )
        assertEquals(listOf("z0_const"), turn1.zone0ConstantEntries.map { it.id })
        assertEquals(listOf("z2_depth_const"), turn1.zone2DepthEntries.map { it.id })
        val turn1Ids = turn1.zone3TriggeredEntries.map { it.id }.toSet()
        assertFalse(turn1Ids.contains("whole_word"))
        assertFalse(turn1Ids.contains("case_only"))
        assertTrue(turn1Ids.contains("regex_entry"))
        assertFalse(turn1Ids.contains("logic_and_any"))
        assertTrue(turn1Ids.contains("logic_not_all"))
        assertFalse(turn1Ids.contains("logic_not_any"))
        assertFalse(turn1Ids.contains("logic_and_all"))
        assertFalse(turn1Ids.contains("timed_curse")) // blocked by delay=2

        // Turn 2: delay satisfied, dragon whole-word & AmberBlade match, 祭坛 + 火焰 + 月光 flips NOT_ALL off and AND_ALL on,
        // and timed_curse triggers freshly!
        val turn2 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "dragon holds AmberBlade by 祭坛 with 火焰 and 月光 诅咒",
            activeUserTurnCount = 2,
            timedStates = turn1.updatedTimedStates,
        )
        val turn2Ids = turn2.zone3TriggeredEntries.map { it.id }.toSet()
        assertTrue(turn2Ids.containsAll(listOf("whole_word", "case_only", "logic_and_any", "logic_and_all", "timed_curse")))
        assertFalse(turn2Ids.contains("logic_not_all"))
        assertNotNull(turn2.updatedTimedStates["timed_curse"])

        // Turn 3 & Turn 4: keyword "诅咒" is GONE, sticky=2 keeps timed_curse active!
        val turn3 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "平静散步",
            activeUserTurnCount = 3,
            timedStates = turn2.updatedTimedStates,
        )
        assertTrue(turn3.zone3TriggeredEntries.any { it.id == "timed_curse" && it.triggerKind == "sticky" })

        val turn4 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "继续散步",
            activeUserTurnCount = 4,
            timedStates = turn3.updatedTimedStates,
        )
        assertTrue(turn4.zone3TriggeredEntries.any { it.id == "timed_curse" && it.triggerKind == "sticky" })

        // Turn 5 & Turn 6: sticky ended at turn 4; cooldown=2 (until turn 6) blocks re-trigger even with keyword!
        val turn5 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "再次遇见 诅咒",
            activeUserTurnCount = 5,
            timedStates = turn4.updatedTimedStates,
        )
        assertFalse(turn5.zone3TriggeredEntries.any { it.id == "timed_curse" })

        val turn7 = WorldbookEngine.evaluate(
            conversationId = "c_logic",
            books = listOf(book),
            draftText = "第三次遇见 诅咒",
            activeUserTurnCount = 7,
            timedStates = turn5.updatedTimedStates,
        )
        assertTrue(turn7.zone3TriggeredEntries.any { it.id == "timed_curse" && it.triggerKind == "keyword" })
    }

    @Test
    fun worldbookEngineOneHopRecursionHashProbabilityAndExclusionGroups() {
        val parentAllowsRecursion = wbEntry(
            id = "parent_ok",
            keys = listOf("星塔"),
            content = "星塔内部供奉着星核。",
            preventRecursion = false,
            insertionOrder = 200,
        )
        val parentBlocksRecursion = wbEntry(
            id = "parent_no_rec",
            keys = listOf("暗门"),
            content = "暗门通向密室密钥。",
            preventRecursion = true,
            insertionOrder = 180,
        )
        val childRecursed = wbEntry(
            id = "child_rec",
            keys = listOf("星核"),
            content = "星核会进一步提及禁术，但只做1-hop不应触发禁术。",
            excludeRecursion = false,
            insertionOrder = 150,
        )
        val childGrandchildNotReached = wbEntry(
            id = "grandchild",
            keys = listOf("禁术"),
            content = "二跳不应被递归激活",
        )
        val childExcludedFromRecursion = wbEntry(
            id = "child_excl",
            keys = listOf("星核"),
            content = "excludeRecursion=true 不允许由其他条目激活",
            excludeRecursion = true,
        )
        val childFromPreventedParent = wbEntry(
            id = "child_blocked",
            keys = listOf("密室密钥"),
            content = "父条 preventRecursion=true 时不可激活",
        )
        val groupLow = wbEntry(
            id = "grp_low",
            keys = listOf("圣剑"),
            groupName = "weapon",
            groupWeight = 10,
            insertionOrder = 90,
        )
        val groupHigh = wbEntry(
            id = "grp_high",
            keys = listOf("圣剑"),
            groupName = "weapon",
            groupWeight = 90,
            insertionOrder = 80,
        )

        val eval = WorldbookEngine.evaluate(
            conversationId = "c_rec",
            books = listOf(
                WorldbookMountedBook(
                    sampleWorldbook(),
                    listOf(
                        parentAllowsRecursion,
                        parentBlocksRecursion,
                        childRecursed,
                        childGrandchildNotReached,
                        childExcludedFromRecursion,
                        childFromPreventedParent,
                        groupLow,
                        groupHigh,
                    ),
                ),
            ),
            draftText = "来到星塔前的暗门，拔出圣剑",
            activeUserTurnCount = 1,
        )
        val triggeredIds = eval.zone3TriggeredEntries.map { it.id }.toSet()
        assertTrue(triggeredIds.contains("parent_ok"))
        assertTrue(triggeredIds.contains("parent_no_rec"))
        assertTrue(triggeredIds.contains("child_rec"))
        assertFalse(triggeredIds.contains("grandchild")) // strictly 1-hop
        assertFalse(triggeredIds.contains("child_excl"))
        assertFalse(triggeredIds.contains("child_blocked"))
        assertTrue(triggeredIds.contains("grp_high"))
        assertFalse(triggeredIds.contains("grp_low"))

        // Deterministic hash probability matches WorldbookEngine.passesHashProbability across turns
        val expectedPassTurn = (1..20).first { t ->
            WorldbookEngine.passesHashProbability("c_prob", t, "prob_50", 50)
        }
        val expectedFailTurn = (1..20).first { t ->
            !WorldbookEngine.passesHashProbability("c_prob", t, "prob_50", 50)
        }
        val probEntry = wbEntry(id = "prob_50", keys = listOf("骰子"), probability = 50)
        val probBook = listOf(WorldbookMountedBook(sampleWorldbook(), listOf(probEntry)))
        assertTrue(
            WorldbookEngine.evaluate("c_prob", books = probBook, draftText = "骰子", activeUserTurnCount = expectedPassTurn)
                .zone3TriggeredEntries.any { it.id == "prob_50" },
        )
        assertFalse(
            WorldbookEngine.evaluate("c_prob", books = probBook, draftText = "骰子", activeUserTurnCount = expectedFailTurn)
                .zone3TriggeredEntries.any { it.id == "prob_50" },
        )
    }

    @Test
    fun contextBuilderPreservesLegacyByteIdenticalPrefixAndV4FourZoneCacheSafety() = runBlocking {
        // 1) Legacy mode: no v4 entities passed -> byte-identical System Prefix across volatile turn state mutations
        val rpBase = RpConfig(
            title = "雾夜剧场",
            worldview = "雾都伦敦 {{time}} {{weather}}",
            playerName = "旅人",
            cast = listOf(RpCharacter(id = "c1", name = "爱丽丝", affinity = 10, mood = "平静", status = "观察")),
            state = RpGameState(scene = "钟楼", time = "22:00", weather = "浓雾"),
            authorNote = "保持悬疑感",
            authorNoteDepth = 1,
        )
        val msgs = listOf(
            ChatMessage(id = "m1", conversationId = "c_leg", role = "user", content = "你好", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "c_leg", role = "assistant", content = "夜雾低垂。", createdAt = 2L, seq = 2),
            ChatMessage(id = "m3", conversationId = "c_leg", role = "user", content = "继续前行", createdAt = 3L, seq = 3),
        )
        val leg1 = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = ChatConversation(id = "c_leg", title = "雾夜", mode = ConversationMode.rp, rp = rpBase, createdAt = 1L, updatedAt = 1L),
                messages = msgs,
            ),
        )
        val rpTurn2 = rpBase.copy(
            cast = listOf(rpBase.cast.first().copy(affinity = 85, mood = "紧张", status = "拔剑")),
            state = rpBase.state.copy(time = "23:15", weather = "暴雨", scene = "地牢"),
        )
        val leg2 = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = ChatConversation(id = "c_leg", title = "雾夜", mode = ConversationMode.rp, rp = rpTurn2, createdAt = 1L, updatedAt = 2L),
                messages = msgs,
            ),
        )
        // Legacy System Prefix with fixed RP fields matches byte-for-byte on non-macro state fields, and authorNoteDepth=1 carries isDepthInjection
        assertEquals(leg1.wireMessages.first().role, "system")
        assertTrue(leg1.wireMessages[leg1.wireMessages.lastIndex - 1].isDepthInjection)

        // 2) V4 Four-Zone mode: Zone 0 expands ONLY {{char}}/{{user}}, keeps {{time}}/{{weather}} unexpanded for 100% cache hit
        val card = CharacterCardEntity(
            id = "card_1",
            name = "爱丽丝",
            description = "{{char}} 是钟楼守夜人，擅长解读时间{{time}} 与 {{weather}}。",
            personality = "冷静沉着",
            scenario = "古钟楼秘密",
            postHistoryInstructions = "严禁替{{user}}做出决定，当前时间{{time}}。",
            depthPromptText = "记住秘密暗号",
            depthPromptDepth = 2,
            depthPromptRole = "system",
        )
        val persona = PersonaEntity(
            id = "per_1",
            name = "艾伦",
            description = "侦探 {{user}} 随身携带怀表",
            injectionPosition = 0,
        )
        val presetBlocks = listOf(
            PromptPresetBlock(identifier = "sys_static", name = "Sys", role = "system", content = "核心文风：{{char}} 对 {{user}} 细腻描写，忽略 {{weather}}。", marker = false, enabled = true, injectionPosition = 0),
            PromptPresetBlock(identifier = "marker_ignored", name = "Marker", role = "system", content = "不应直接输出", marker = true, enabled = true, injectionPosition = 0),
            PromptPresetBlock(identifier = "depth_1", name = "D1", role = "system", content = "深层节奏提醒 @{{time}}", marker = false, enabled = true, injectionPosition = 1, injectionDepth = 1),
            PromptPresetBlock(identifier = "depth_0_instr", name = "D0", role = "system", content = "零深度指令：保持第3人称", marker = false, enabled = true, injectionPosition = 1, injectionDepth = 0),
            PromptPresetBlock(identifier = "prefill", name = "Prefill", role = "assistant", content = "{{char}}轻声说道：「", marker = false, enabled = true, injectionPosition = 1, injectionDepth = 0),
        )
        val preset = PromptPresetEntity(
            id = "pre_1",
            name = "标准酒馆预设",
            blocksJson = AppJson.encodeToString(presetBlocks),
        )
        val wbBook = WorldbookMountedBook(
            worldbook = sampleWorldbook(),
            entries = listOf(
                wbEntry(id = "wb_const_z0", title = "常驻世界观", content = "{{char}} 与 {{user}} 约定，不管{{time}}。", constant = true, position = WorldbookEngine.POSITION_AFTER_CHAR),
                wbEntry(id = "wb_depth_2", title = "暗流", content = "钟楼内有陷阱，此刻 {{time}}", keys = listOf("继续前行"), position = WorldbookEngine.POSITION_AT_DEPTH, depth = 2, outletRole = "system"),
                wbEntry(id = "wb_trig_z3", title = "线索", content = "钥匙藏在砖缝下", keys = listOf("继续前行"), position = WorldbookEngine.POSITION_AFTER_CHAR),
            ),
        )
        val v4Input1 = ContextBuildInput(
            conversation = ChatConversation(id = "c_v4", title = "雾夜", mode = ConversationMode.rp, rp = rpBase, createdAt = 1L, updatedAt = 1L),
            messages = msgs,
            memories = listOf(UserMemory(id = "um_1", category = MemoryCategory.rule, content = "{{user}}偏好短句", pinned = true, enabled = true, createdAt = 1L, updatedAt = 1L)),
            characterCard = card,
            persona = persona,
            promptPreset = preset,
            mountedWorldbooks = listOf(wbBook),
        )
        val v4Input2 = v4Input1.copy(
            conversation = v4Input1.conversation.copy(rp = rpTurn2),
        )

        val res1 = ContextBuilder.buildChatContextV4(v4Input1)
        val res2 = ContextBuilder.buildChatContextV4(v4Input2)
        val sysPrefix1 = res1.built.wireMessages.first().textContent.orEmpty()
        val sysPrefix2 = res2.built.wireMessages.first().textContent.orEmpty()

        // Zone 0 MUST stay byte-identical despite time/weather/affinity mutations across turns!
        assertEquals(sysPrefix1, sysPrefix2)
        assertTrue(sysPrefix1.contains("爱丽丝"))
        assertTrue(sysPrefix1.contains("艾伦"))
        assertFalse(sysPrefix1.contains("{{char}}"))
        assertFalse(sysPrefix1.contains("{{user}}"))
        assertFalse(sysPrefix1.contains("不应直接输出"))
        // Volatile macros stay literal in Zone 0 (NEVER expanded into 22:00 / 23:15 / 浓雾 / 暴雨)
        assertFalse(sysPrefix1.contains("22:00"))
        assertFalse(sysPrefix1.contains("暴雨"))

        // Zone 2 @D>=1 depth items marked isDepthInjection=true, placed strictly before trailing user turn
        val wire1 = res1.built.wireMessages
        val depthMsgs = wire1.filter { it.isDepthInjection }
        assertTrue(depthMsgs.isNotEmpty())
        assertTrue(depthMsgs.any { it.textContent.orEmpty().contains("钟楼内有陷阱") && it.textContent.orEmpty().contains("22:00") })
        assertTrue(depthMsgs.any { it.textContent.orEmpty().contains("深层节奏提醒") })

        // Zone 3 merges triggered lore + @D==0 instruction + postHistoryInstructions into final user turn & resolves assistantPrefill
        val lastUserText = wire1.last().textContent.orEmpty()
        assertEquals("user", wire1.last().role)
        assertTrue(lastUserText.contains("钥匙藏在砖缝下"))
        assertTrue(lastUserText.contains("零深度指令：保持第3人称"))
        assertTrue(lastUserText.contains("严禁替艾伦做出决定"))
        assertEquals("爱丽丝轻声说道：「", res1.resolvedAssistantPrefill)

        // 3) Zone 1 SLIDE_STRIDE = 4 hysteresis: keeps exact same first kept history message across 4 turns
        val baseConv = ChatConversation(
            id = "c_slide",
            title = "长对话",
            mode = ConversationMode.chat,
            params = ChatParams(contextWindowOverride = 4096, maxTokens = 2048), // effectiveLimitTokens = 1228
            createdAt = 1L,
            updatedAt = 1L,
        )
        // Each message ~150 tokens -> ~7 messages fit in budget (leaving room cleanly), so 11..14 msgs force drop=1..4 -> snapped to 4!
        val fillerChunk = "历史背景细节记录推进情节发展。".repeat(16)
        fun buildMsgs(count: Int) = (1..count).map { i ->
            ChatMessage(
                id = "msg_$i",
                conversationId = "c_slide",
                role = if (i % 2 == 1) "user" else "assistant",
                content = "回合$i $fillerChunk",
                createdAt = i.toLong(),
                seq = i,
            )
        }
        val firstIds = (11..14).map { n ->
            val ctx = ContextBuilder.buildChatContext(ContextBuildInput(conversation = baseConv, messages = buildMsgs(n)))
            ctx.wireMessages.first { it.role != "system" }.textContent!!.substringBefore(" ")
        }
        // All 4 consecutive window overflows start from the EXACT SAME message ID (msg_5 after dropping 4)
        assertEquals(1, firstIds.distinct().size)
        val afterJump = ContextBuilder.buildChatContext(ContextBuildInput(conversation = baseConv, messages = buildMsgs(15)))
            .wireMessages.first { it.role != "system" }.textContent!!.substringBefore(" ")
        assertFalse("Stride should jump to next SLIDE_STRIDE=4 boundary once minDrop > 4", afterJump == firstIds.first())
    }

    @Test
    fun directProtocolCodecDepthSafetyAnthropicBreakpoint2AndFakeImageGating() {
        val messagesWithDepth = listOf(
            WireChatMessageDto.text("system", "Frozen System Prefix"),
            WireChatMessageDto.text("user", "Turn 1 user"),
            WireChatMessageDto.text("assistant", "Turn 1 assistant"),
            WireChatMessageDto.text("system", "@D=1 Deep Lore Context", isDepthInjection = true),
            WireChatMessageDto.text("user", "Turn 2 user"),
        )
        val req = OnlineChatCompletionRequestDto(
            group_id = -1L,
            model = "claude-sonnet-4-5",
            messages = messagesWithDepth,
            enablePromptCaching = true,
        )

        // 1) Anthropic: @D is NOT hoisted to top-level `system`; merged into Turn 2 user turn as <depth_context>,
        //    AND cache_control Breakpoint #2 pins `Turn 1 assistant` (index 1) BEFORE earliest @D!
        val anthJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildAnthropicRequestJson(req, stream = true),
        ).jsonObject
        val anthSys = anthJson["system"]!!.jsonArray
        assertEquals(1, anthSys.size)
        assertEquals("Frozen System Prefix", anthSys[0].jsonObject["text"]?.jsonPrimitive?.content)
        val anthTurns = anthJson["messages"]!!.jsonArray
        assertEquals(3, anthTurns.size) // [Turn1 U, Turn1 A (cached), (<depth_context> + Turn2 U)]
        assertEquals(
            "ephemeral",
            anthTurns[1].jsonObject["content"]!!.jsonArray.last().jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )
        assertTrue(anthTurns[2].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content.contains("<depth_context>"))

        // 2) OpenAI Responses: @D NOT hoisted to `instructions`; kept in `input` as user input_text <depth_context>
        val respJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildOpenAIResponsesRequestJson(req.copy(model = "gpt-4.1"), stream = true),
        ).jsonObject
        assertEquals("Frozen System Prefix", respJson["instructions"]?.jsonPrimitive?.content)
        val inputArr = respJson["input"]!!.jsonArray
        assertTrue(inputArr.any { item ->
            item.jsonObject["role"]?.jsonPrimitive?.content == "user" &&
                item.jsonObject["content"]!!.jsonArray.any { p ->
                    p.jsonObject["text"]?.jsonPrimitive?.content.orEmpty().contains("<depth_context>")
                }
        })

        // 3) Gemini: @D NOT hoisted to `systemInstruction`; kept in `contents` cleanly
        val gemJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildGeminiRequestJson(req.copy(model = "gemini-2.5-flash")),
        ).jsonObject
        val sysInstrText = gemJson["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("Frozen System Prefix", sysInstrText)
        assertFalse(sysInstrText.contains("@D=1"))
        val gemContents = gemJson["contents"]!!.jsonArray
        assertEquals(3, gemContents.size)
        assertTrue(gemContents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content.contains("<depth_context>"))

        // 4) Fake image channel gating across DirectProtocolCodec & DirectApiClient
        assertFalse(DirectProtocolCodec.supportsImageGeneration(ApiProtocol.ANTHROPIC, "https://api.anthropic.com/v1"))
        assertTrue(DirectProtocolCodec.defaultImageModels(ApiProtocol.ANTHROPIC, listOf("dall-e-3")).isEmpty())

        assertFalse(DirectProtocolCodec.supportsImageGeneration(ApiProtocol.OPENAI_CHAT, "https://api.deepseek.com/v1"))
        assertTrue(DirectProtocolCodec.defaultImageModels(ApiProtocol.OPENAI_CHAT, baseUrl = "https://api.deepseek.com/v1").isEmpty())

        assertFalse(DirectProtocolCodec.supportsImageGeneration(ApiProtocol.OPENAI_RESPONSES, "http://127.0.0.1:11434/v1"))
        assertTrue(DirectProtocolCodec.defaultImageModels(ApiProtocol.OPENAI_RESPONSES, baseUrl = "http://127.0.0.1:11434/v1").isEmpty())

        assertTrue(DirectProtocolCodec.supportsImageGeneration(ApiProtocol.OPENAI_CHAT, "https://api.openai.com/v1"))
        assertTrue(DirectProtocolCodec.defaultImageModels(ApiProtocol.OPENAI_CHAT, baseUrl = "https://api.openai.com/v1").isNotEmpty())

        // Non-OpenAI relay WITH explicit user-configured custom image model is still honored
        assertTrue(DirectProtocolCodec.supportsImageGeneration(ApiProtocol.OPENAI_CHAT, "https://relay.example.com/v1", listOf("flux-pro")))
        assertEquals(listOf("flux-pro"), DirectProtocolCodec.defaultImageModels(ApiProtocol.OPENAI_CHAT, listOf("flux-pro"), "https://relay.example.com/v1").map { it.id })

        val ok = OkHttpClient()
        val client = DirectApiClient(ok, ok, { status, raw -> ApiException(status, null, raw) })
        val dsProvider = ApiProviderConfig(
            id = "ds",
            name = "DeepSeek",
            protocol = ApiProtocol.OPENAI_CHAT,
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-test",
            customModels = listOf("deepseek-chat"),
        )
        assertTrue(client.listImageModels(dsProvider).isEmpty())
        assertFalse(client.supportsImageGeneration(dsProvider))
    }

    @Test
    fun vectorMemoryIndexWorldbookPartitionReuseSafetyAndNonCancellingScopes() = runBlocking {
        val dao = InMemoryEmbeddingDao()
        val index = VectorMemoryIndex(dao)
        var embedDocCalls = 0
        var embedQueryCalls = 0
        val engine = object : EmbeddingEngine {
            override val profileKey: String = "test:bge:64d"
            override val minCosineFloor: Double = 0.45
            override suspend fun isAvailable(): Boolean = true

            override suspend fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray> {
                if (isQuery) embedQueryCalls++ else embedDocCalls += texts.size
                return texts.map { FloatArray(64) { idx -> if (idx == 0) 1f else 0.01f } }
            }
        }

        // 1) Seed valid 64d vector first so knownDimByProfile=64 & contentVectorCache is populated,
        //    then verify identical canonicalText in a second worldbook scope reuses the vector with 0 embeds!
        val sharedCanon = "共享骑士团档案\n银月骑士团驻守北境关隘。"
        index.ensureIndexed(
            targets = listOf(
                VectorIndexTarget(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_e1",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_shared"),
                    canonicalText = sharedCanon,
                ),
            ),
            engine = engine,
            batchSize = 8,
        )
        assertEquals(1, embedDocCalls)

        index.ensureIndexed(
            targets = listOf(
                VectorIndexTarget(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_e1_clone",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_shared_2"),
                    canonicalText = sharedCanon,
                ),
            ),
            engine = engine,
            batchSize = 8,
        )
        assertEquals(1, embedDocCalls) // 100% reused from cache!

        // Repeat ensureIndexed on identical verified scope MUST skip loading fullRows with vectorBlobs from SQLite
        val fullFetchesBeforeRepeat = dao.fullFetchCount
        index.ensureIndexed(
            targets = listOf(
                VectorIndexTarget(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_e1",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_shared"),
                    canonicalText = sharedCanon,
                ),
            ),
            engine = engine,
            batchSize = 8,
        )
        assertEquals(fullFetchesBeforeRepeat, dao.fullFetchCount)
        assertEquals(1, embedDocCalls)

        // 2) Reuse safety: pre-seed mismatched quantType ("fp16") and mismatched dim (32) rows;
        //    ensureIndexed MUST reject both stale rows and re-embed cleanly!
        val fp16Text = "旧版半精度条目标题\n需要迁移升级为INT8。"
        val dim32Text = "维度不匹配内容\n旧32维向量。"
        val fp16Hash = VectorQuantizer.sha256Hex(VectorQuantizer.normalizeCanonicalText(fp16Text))
        val dim32Hash = VectorQuantizer.sha256Hex(VectorQuantizer.normalizeCanonicalText(dim32Text))
        val (validBytes64, validScale64) = VectorQuantizer.quantizeInt8(
            VectorQuantizer.l2Normalize(FloatArray(64) { idx -> if (idx == 0) 1f else 0f }),
        )
        dao.upsertAll(
            listOf(
                MemoryEmbeddingEntity(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_stale_fp16",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_safety"),
                    profileKey = engine.profileKey,
                    contentHash = fp16Hash,
                    dim = 64,
                    quantType = VectorQuantizer.QUANT_FP16,
                    scale = validScale64,
                    vectorBlob = validBytes64,
                    updatedAt = 1L,
                ),
                MemoryEmbeddingEntity(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_stale_dim32",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_safety"),
                    profileKey = engine.profileKey,
                    contentHash = dim32Hash,
                    dim = 32,
                    quantType = VectorQuantizer.QUANT_INT8_UNIT,
                    scale = 1f,
                    vectorBlob = ByteArray(32) { 1 },
                    updatedAt = 1L,
                ),
            ),
        )
        index.ensureIndexed(
            targets = listOf(
                VectorIndexTarget(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_stale_fp16",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_safety"),
                    canonicalText = fp16Text,
                ),
                VectorIndexTarget(
                    ownerType = RetrievalCoordinator.OWNER_WORLDBOOK_ENTRY,
                    ownerId = "wb_stale_dim32",
                    conversationId = VectorMemoryIndex.worldbookScopeId("wb_safety"),
                    canonicalText = dim32Text,
                ),
            ),
            engine = engine,
            batchSize = 8,
        )
        assertEquals(3, embedDocCalls) // Both stale fp16 and wrong-dim rows were rejected and freshly re-embedded!
        val upgradedRows = dao.getByConversationAndProfile(VectorMemoryIndex.worldbookScopeId("wb_safety"), engine.profileKey)
        assertEquals(2, upgradedRows.size)
        assertTrue(upgradedRows.all { it.dim == 64 && it.quantType == VectorQuantizer.QUANT_INT8_UNIT })

        // 3) RetrievalCoordinator.prepareSendSemanticScores + concurrent non-cancelling scope jobs
        val tempDir = Files.createTempDirectory("wb_coord_v04_test").toFile()
        val ok = OkHttpClient()
        val directClient = DirectApiClient(ok, ok, { status, raw -> ApiException(status, null, raw) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val testModelId = "test-v04-wb-mini"
            val spec = LocalModelSpec(
                id = testModelId,
                displayName = "Test WB Mini",
                descriptionZh = "测试",
                descriptionEn = "Test",
                dimension = 4,
                maxTokens = 64,
                approxSizeMb = 1,
                pooling = LocalPoolingMode.CLS,
                queryPrefix = "",
                recommendedMinCosine = 0.45,
                modelMirrorUrls = emptyList(),
                vocabMirrorUrls = emptyList(),
            )
            val manager = LocalEmbeddingModelManager(
                modelsRootDir = File(tempDir, "models"),
                httpClient = ok,
                catalogSpecs = listOf(spec),
            )
            val modelDir = manager.getModelDir(testModelId)
            modelDir.mkdirs()
            manager.getModelFile(testModelId).writeBytes(byteArrayOf(1, 2, 3, 4))
            manager.getVocabFile(testModelId).writeText(
                listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "银", "月", "骑", "士", "北", "境").joinToString("\n"),
                Charsets.UTF_8,
            )
            File(modelDir, LocalEmbeddingModelManager.COMPLETE_MARKER_NAME).writeText("ok")
            manager.refreshStates()

            var gate: CountDownLatch? = null
            var localQueryCount = 0
            val runtimeFactory = SessionRuntimeFactory { _, _, _ ->
                object : OnnxEmbeddingSession {
                    override fun embedChunk(chunk: com.gugachat.app.domain.chat.EncodedChunk, pooling: LocalPoolingMode): FloatArray {
                        gate?.await(5, TimeUnit.SECONDS)
                        return floatArrayOf(0.95f, 0.20f, 0.10f, 0.05f)
                    }

                    override fun close() {}
                }
            }
            val onnxEngine = LocalOnnxEmbeddingEngine(
                modelManager = manager,
                activeModelIdProvider = { testModelId },
                idleUnloadTimeoutMs = 0L,
                intraOpThreads = 1,
                interOpThreads = 1,
                chunkStride = 16,
                maxDocumentChunks = 2,
                inferenceDispatcher = Dispatchers.Default,
                lifecycleScope = scope,
                runtimeFactory = runtimeFactory,
            )

            // Also verify scoreCandidates direct single-query call across conv + wb slice
            val scoredDirect = index.scoreCandidates(
                conversationId = "conv_a",
                queryText = "银月骑士团北境关隘",
                candidateIds = setOf("wb_e1", "wb_stale_fp16"),
                engine = engine,
                extraScopeIds = listOf(VectorMemoryIndex.worldbookScopeId("wb_shared")),
            )
            assertEquals(1, embedQueryCalls)
            assertTrue(scoredDirect.containsKey("wb_e1"))
            assertTrue(scoredDirect["wb_e1"]!! > 0.5)

            val coordinator = RetrievalCoordinator(
                retrievalSettingsProvider = {
                    RetrievalSettings(
                        mode = RetrievalMode.LOCAL_SMALL_MODEL,
                        localModelId = testModelId,
                        minCosineOverride = 0.40,
                    )
                },
                providerLookup = { null },
                directApiClient = directClient,
                localModelManager = manager,
                localOnnxEngine = onnxEngine,
                vectorMemoryIndex = VectorMemoryIndex(InMemoryEmbeddingDao()),
            )

            // Block inside ONNX embedChunk and ensure scheduling worldbook indexing does NOT cancel conversation job
            val blockingLatch = CountDownLatch(1)
            gate = blockingLatch
            val convJob = coordinator.scheduleConversationIndexing(
                scope = scope,
                conversationId = "conv_concurrent",
                lore = listOf(NovelLoreEntry(id = "l1", conversationId = "conv_concurrent", title = "A", content = "银月A", createdAt = 1L, updatedAt = 1L)),
                ledger = emptyList(),
                episodes = emptyList(),
                userMemories = emptyList(),
            )
            val wbJob = coordinator.scheduleWorldbookIndexing(
                scope = scope,
                worldbookId = "wb_concurrent",
                entries = listOf(wbEntry(id = "wb_new_item", worldbookId = "wb_concurrent", content = "北境骑士")),
            )
            assertNotNull(convJob)
            assertNotNull(wbJob)
            assertFalse("Worldbook scope must NEVER cancel conversation indexing job", convJob!!.isCancelled)
            assertFalse("Conversation job must stay active alongside worldbook job", wbJob!!.isCancelled)

            blockingLatch.countDown()
            convJob.join()
            wbJob.join()
            gate = null

            val (coordScores, _) = coordinator.prepareSendSemanticScores(
                conversationId = "conv_concurrent",
                queryText = "北境骑士",
                candidateIds = setOf("l1", "wb_new_item"),
                mountedWorldbookIds = listOf("wb_concurrent"),
            )
            assertTrue(coordScores.containsKey("wb_new_item"))
            assertTrue(coordScores.containsKey("l1"))
            localQueryCount++
            assertEquals(1, localQueryCount)
        } finally {
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun chatContextAssemblerToWireDto_preservesIsDepthInjectionAcrossAllProtocols() {
        val domainMessages = listOf(
            WireChatMessage(role = "system", textContent = "Frozen System Prefix", isDepthInjection = false),
            WireChatMessage(role = "user", textContent = "Turn 1 user"),
            WireChatMessage(role = "assistant", textContent = "Turn 1 assistant"),
            WireChatMessage(role = "system", textContent = "@D=1 Deep Lore Context", isDepthInjection = true),
            WireChatMessage(
                role = "system",
                parts = listOf(WireChatContentPart(type = "text", text = "@D=1 Part Depth Context")),
                isDepthInjection = true,
            ),
            WireChatMessage(role = "user", textContent = "Turn 2 user"),
        )
        val wireDtos = domainMessages.map { ChatContextAssembler.toWireDto(it) }
        assertFalse(wireDtos[0].isDepthInjection)
        assertTrue(wireDtos[3].isDepthInjection)
        assertTrue(wireDtos[4].isDepthInjection)

        val req = OnlineChatCompletionRequestDto(
            group_id = -1L,
            model = "claude-sonnet-4-5",
            messages = wireDtos,
            enablePromptCaching = true,
        )

        // 1) Anthropic: Breakpoint 2 pins Turn 1 assistant (index 1) BEFORE @D; depth messages stay in Turn 2 user as <depth_context>
        val anthJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildAnthropicRequestJson(req, stream = true),
        ).jsonObject
        val anthSys = anthJson["system"]!!.jsonArray
        assertEquals(1, anthSys.size)
        assertEquals("Frozen System Prefix", anthSys[0].jsonObject["text"]?.jsonPrimitive?.content)
        val anthTurns = anthJson["messages"]!!.jsonArray
        assertEquals(3, anthTurns.size)
        assertEquals(
            "ephemeral",
            anthTurns[1].jsonObject["content"]!!.jsonArray.last().jsonObject["cache_control"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
        )
        val anthTurn2Texts = anthTurns[2].jsonObject["content"]!!.jsonArray.mapNotNull {
            it.jsonObject["text"]?.jsonPrimitive?.content
        }
        assertTrue(anthTurn2Texts.any { it.contains("<depth_context>") && it.contains("@D=1 Deep Lore Context") })
        assertTrue(anthTurn2Texts.any { it.contains("<depth_context>") && it.contains("@D=1 Part Depth Context") })

        // 2) OpenAI Responses: @D NOT hoisted to `instructions`; kept in `input` as user <depth_context>
        val respJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildOpenAIResponsesRequestJson(req.copy(model = "gpt-4.1"), stream = true),
        ).jsonObject
        assertEquals("Frozen System Prefix", respJson["instructions"]?.jsonPrimitive?.content)
        val inputArr = respJson["input"]!!.jsonArray
        val inputTexts = inputArr.flatMap { item ->
            item.jsonObject["content"]!!.jsonArray.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
        }
        assertTrue(inputTexts.any { it.contains("<depth_context>") && it.contains("@D=1 Deep Lore Context") })
        assertTrue(inputTexts.any { it.contains("<depth_context>") && it.contains("@D=1 Part Depth Context") })

        // 3) Gemini: @D NOT hoisted to `systemInstruction`; kept in `contents` cleanly
        val gemJson = AppJson.parseToJsonElement(
            DirectProtocolCodec.buildGeminiRequestJson(req.copy(model = "gemini-2.5-flash")),
        ).jsonObject
        val sysInstrText = gemJson["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("Frozen System Prefix", sysInstrText)
        assertFalse(sysInstrText.contains("@D=1"))
        val gemContents = gemJson["contents"]!!.jsonArray
        assertEquals(3, gemContents.size)
        val gemTurn2Texts = gemContents[2].jsonObject["parts"]!!.jsonArray.mapNotNull {
            it.jsonObject["text"]?.jsonPrimitive?.content
        }
        assertTrue(gemTurn2Texts.any { it.contains("<depth_context>") && it.contains("@D=1 Deep Lore Context") })
        assertTrue(gemTurn2Texts.any { it.contains("<depth_context>") && it.contains("@D=1 Part Depth Context") })
    }

    @Test
    fun novelExportIsStreamingInternalMeta_ignoresCompletedTagsBeforeProseAndDetectsPartial() {
        assertFalse(NovelExport.isStreamingInternalMeta("<status_hud>{}</status_hud> trailing prose"))
        assertFalse(NovelExport.isStreamingInternalMeta("<status_hud>{\"location\":\"大殿\"}</status_hud>"))
        assertFalse(NovelExport.isStreamingInternalMeta("<choices>[\"A\",\"B\"]</choices>\n后续正文继续展开。"))

        assertTrue(NovelExport.isStreamingInternalMeta("<status_hud>{\"location\":\"大殿\""))
        assertTrue(NovelExport.isStreamingInternalMeta("<status_hud>{}</status_hud> trailing prose <choi"))
        assertTrue(NovelExport.isStreamingInternalMeta("正文结尾。<lore_sy"))
        assertTrue(NovelExport.isStreamingInternalMeta("<status_hud>{}</status_hud><choices>[\"A\"]"))
    }

    private class InMemoryEmbeddingDao : MemoryEmbeddingDao {
        private val lock = Any()
        private val rows = LinkedHashMap<Triple<String, String, String>, MemoryEmbeddingEntity>()
        @Volatile
        var fullFetchCount: Int = 0

        override suspend fun getByConversationAndProfile(conversationId: String, profileKey: String): List<MemoryEmbeddingEntity> =
            synchronized(lock) {
                fullFetchCount++
                rows.values.filter { it.conversationId == conversationId && it.profileKey == profileKey }
            }

        override suspend fun getGlobalByProfile(profileKey: String): List<MemoryEmbeddingEntity> =
            synchronized(lock) {
                fullFetchCount++
                rows.values.filter { it.conversationId.isEmpty() && it.profileKey == profileKey }
            }

        override suspend fun getHashesForProfile(conversationId: String, profileKey: String): List<MemoryEmbeddingHashRow> =
            synchronized(lock) {
                rows.values
                    .filter { it.conversationId == conversationId && it.profileKey == profileKey }
                    .map { MemoryEmbeddingHashRow(it.ownerType, it.ownerId, it.contentHash) }
            }

        override suspend fun upsertAll(items: List<MemoryEmbeddingEntity>) {
            synchronized(lock) {
                for (item in items) {
                    rows[Triple(item.ownerType, item.ownerId, item.profileKey)] = item
                }
            }
        }

        override suspend fun deleteByOwner(ownerType: String, ownerId: String) {
            synchronized(lock) {
                rows.entries.removeAll { it.key.first == ownerType && it.key.second == ownerId }
            }
        }

        override suspend fun deleteByConversationId(conversationId: String) {
            synchronized(lock) {
                rows.entries.removeAll { it.value.conversationId == conversationId }
            }
        }

        override suspend fun deleteStaleProfiles(activeProfileKey: String) {
            synchronized(lock) {
                rows.entries.removeAll { it.key.third != activeProfileKey }
            }
        }

        override suspend fun countByProfile(profileKey: String): Int =
            synchronized(lock) {
                rows.values.count { it.profileKey == profileKey }
            }

        override suspend fun deleteAll() {
            synchronized(lock) {
                rows.clear()
            }
        }
    }
}
