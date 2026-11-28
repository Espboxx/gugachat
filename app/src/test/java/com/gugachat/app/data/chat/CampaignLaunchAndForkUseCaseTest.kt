package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ChatParams
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.MemoryLedgerItem
import com.gugachat.app.domain.chat.MessageVariant
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.NovelLoreEntry
import com.gugachat.app.domain.chat.NovelStatusHud
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpDiceRoll
import com.gugachat.app.domain.chat.RpGameState
import com.gugachat.app.domain.chat.RpSaveSlot
import com.gugachat.app.domain.chat.RpStarterLore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignLaunchAndForkUseCaseTest {

    @Test
    fun expandRuntimeRpMacros_replacesStateAndChoicesAndPreservesOtherFields() {
        val rp = RpConfig(
            title = "Test Campaign",
            playerName = "Lin",
            cast = listOf(RpCharacter(id = "npc1", name = "Sora", isPlayer = false)),
            state = RpGameState(
                scene = "{{char}} waiting for {{user}}",
                chapterTitle = "Meet {{char}}",
                inventory = listOf("{{user}}'s badge"),
                quests = listOf("Talk to {{char}}"),
            ),
            pendingChoices = listOf(
                RpChoice(
                    id = "c1",
                    label = "Greet {{char}}",
                    actionText = "{{user}} waves at {{char}}",
                    consequenceHint = "{{char}} smiles at {{user}}",
                ),
            ),
        )

        val expanded = CampaignLaunchUseCase.expandRuntimeRpMacros(rp)

        assertEquals("Sora waiting for Lin", expanded.state.scene)
        assertEquals("Meet Sora", expanded.state.chapterTitle)
        assertEquals(listOf("Lin's badge"), expanded.state.inventory)
        assertEquals(listOf("Talk to Sora"), expanded.state.quests)
        assertEquals("Greet Sora", expanded.pendingChoices.single().label)
        assertEquals("Lin waves at Sora", expanded.pendingChoices.single().actionText)
        assertEquals("Sora smiles at Lin", expanded.pendingChoices.single().consequenceHint)
    }

    @Test
    fun buildOpeningGreetingMessage_selectsVariantAndComputesTokenEstimate() {
        val rp = RpConfig(
            title = "Test",
            playerName = "Kai",
            cast = listOf(RpCharacter(id = "npc1", name = "Vesper", isPlayer = false)),
            state = RpGameState(scene = "Rooftop"),
        )
        val msg = CampaignLaunchUseCase.buildOpeningGreetingMessage(
            conversationId = "conv_1",
            rp = rp,
            rawGreetings = listOf("   ", "Hi {{user}}, I'm {{char}}", "Second greeting for {{user}}"),
            selectedGreetingIndex = 99,
            now = 1234L,
        )

        assertNotNull(msg)
        msg!!
        assertEquals(2, msg.variants.size)
        assertEquals(1, msg.activeVariantIndex)
        assertEquals("Second greeting for Kai", msg.content)
        assertEquals("Vesper", msg.variants.first().rpSpeakerName)
        assertTrue(msg.tokenEstimate > 0)
        assertNull(
            CampaignLaunchUseCase.buildOpeningGreetingMessage("conv_1", rp, listOf("  "), 0, 1234L),
        )
    }

    @Test
    fun defaultChatParams_filtersOutOfRangeValues() {
        val outOfBounds = AppSettings(
            defaultMaxOutputTokens = 10,
            defaultContextWindowOverride = 1024,
            defaultMaxHistoryMessages = 999,
        )
        val params1 = CampaignLaunchUseCase.defaultChatParams(outOfBounds)
        assertNull(params1.maxTokens)
        assertNull(params1.contextWindowOverride)
        assertNull(params1.maxHistoryMessages)

        val valid = AppSettings(
            defaultMaxOutputTokens = 4096,
            defaultContextWindowOverride = 32768,
            defaultMaxHistoryMessages = 40,
        )
        val params2 = CampaignLaunchUseCase.defaultChatParams(valid)
        assertEquals(4096, params2.maxTokens)
        assertEquals(32768, params2.contextWindowOverride)
        assertEquals(40, params2.maxHistoryMessages)
    }

    @Test
    fun buildStarterLoreEntries_defaultsKeywordsToTitleAndEstimatesTokens() {
        val lore = CampaignLaunchUseCase.buildStarterLoreEntries(
            conversationId = "conv_2",
            starterLore = listOf(
                RpStarterLore(
                    category = "world",
                    title = "Jade Court",
                    keywords = emptyList(),
                    content = "An imperial palace shrouded in intrigue.",
                    alwaysActive = true,
                ),
            ),
            now = 555L,
        )
        assertEquals(1, lore.size)
        assertEquals(listOf("Jade Court"), lore.single().keywords)
        assertTrue(lore.single().alwaysActive)
        assertTrue(lore.single().estimatedTokens > 0)
    }

    @Test
    fun forkHelpers_reopenTruncatedChapterRemapActiveIdAndResetSummaryBounds() {
        val conv = ChatConversation(
            id = "old_conv",
            title = "Novel",
            mode = ConversationMode.novel,
            novel = NovelConfig(title = "Novel", activeChapterId = "ch2"),
            archivedSummary = "Summary beyond cutoff",
            summarizedUntilSeq = 12,
            createdAt = 100L,
            updatedAt = 200L,
        )
        val ch1 = NovelChapter(
            id = "ch1",
            conversationId = "old_conv",
            order = 1,
            title = "Ch 1",
            status = "done",
            startSeq = 1,
            endSeq = 4,
        )
        val ch2 = NovelChapter(
            id = "ch2",
            conversationId = "old_conv",
            order = 2,
            title = "Ch 2",
            status = "done",
            startSeq = 5,
            endSeq = 10,
        )
        val ch3 = NovelChapter(
            id = "ch3",
            conversationId = "old_conv",
            order = 3,
            title = "Ch 3",
            status = "writing",
            startSeq = 11,
            endSeq = null,
        )
        val copiedMsgs = listOf(
            ChatMessage(id = "m1", conversationId = "new_conv", role = "user", content = "Hello", createdAt = 100L, seq = 1),
            ChatMessage(id = "m2", conversationId = "new_conv", role = "assistant", content = "World prose here", createdAt = 150L, seq = 6),
        )

        val (branchedChapters, nextNovel) = ConversationForkUseCase.buildBranchedChaptersAndNovel(
            sourceConv = conv,
            existingChapters = listOf(ch3, ch1, ch2),
            copiedMsgs = copiedMsgs,
            cutoffSeq = 6,
            newConvId = "new_conv",
            now = 999L,
        )

        assertEquals(2, branchedChapters.size)
        assertEquals("done", branchedChapters[0].status)
        assertEquals(4, branchedChapters[0].endSeq)
        assertEquals("writing", branchedChapters[1].status)
        assertNull(branchedChapters[1].endSeq)
        assertEquals(branchedChapters[1].id, nextNovel?.activeChapterId)

        val (untilWithEp, summaryWithEp) = ConversationForkUseCase.computeForkedCompactionBounds(
            sourceConv = conv,
            filteredEpisodes = listOf(
                MemoryEpisode(id = "e1", conversationId = "old_conv", title = "Ep1", summary = "S", fromSeq = 1, toSeq = 5),
            ),
            cutoffSeq = 6,
        )
        assertEquals(5, untilWithEp)
        assertEquals("Summary beyond cutoff", summaryWithEp)

        val (untilEmpty, summaryEmpty) = ConversationForkUseCase.computeForkedCompactionBounds(
            sourceConv = conv,
            filteredEpisodes = emptyList(),
            cutoffSeq = 6,
        )
        assertEquals(0, untilEmpty)
        assertEquals("", summaryEmpty)
    }

    @Test
    fun branchFromUserMessage_excludesTargetUserMsg_restoresPriorRpStateAndChoices_andReturnsPrefill() {
        val s0 = RpGameState(scene = "档案室", hp = 100, gold = 50)
        val c0 = listOf(RpCharacter(id = "npc1", name = "艾琳", affinity = 10))
        val choices1 = listOf(RpChoice(id = "ch1", label = "细读铜板", actionText = "细读铜板楔形文字"))

        val s1 = RpGameState(scene = "密道", hp = 80, gold = 30)
        val c1 = listOf(RpCharacter(id = "npc1", name = "艾琳", affinity = 25))
        val choices2 = listOf(RpChoice(id = "ch2", label = "逃离", actionText = "撤退"))

        val conv = ChatConversation(
            id = "rp_conv",
            title = "伦敦迷雾",
            mode = ConversationMode.rp,
            rp = RpConfig(title = "伦敦迷雾", state = s1, cast = c1, pendingChoices = choices2),
            loreTimedStatesJson = "{\"k\":{\"lastTriggeredTurn\":3}}",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val a1 = ChatMessage(
            id = "a1",
            conversationId = "rp_conv",
            role = "assistant",
            content = "开场白",
            createdAt = 10L,
            seq = 1,
            rpChoices = choices1,
            variants = listOf(
                MessageVariant(
                    id = "v1",
                    content = "开场白",
                    createdAt = 10L,
                    rpStateSnapshot = s0,
                    rpCastSnapshot = c0,
                    rpChoices = choices1,
                ),
            ),
            activeVariantIndex = 0,
        )
        val u2 = ChatMessage(
            id = "u2",
            conversationId = "rp_conv",
            role = "user",
            content = "细读铜板楔形文字",
            createdAt = 20L,
            seq = 2,
            rpDiceRoll = RpDiceRoll(expression = "1d100", rolls = listOf(13), total = 13),
        )
        val a3 = ChatMessage(
            id = "a3",
            conversationId = "rp_conv",
            role = "assistant",
            content = "密道开启",
            createdAt = 30L,
            seq = 3,
            rpChoices = choices2,
            variants = listOf(
                MessageVariant(
                    id = "v3",
                    content = "密道开启",
                    createdAt = 30L,
                    rpStateSnapshot = s1,
                    rpCastSnapshot = c1,
                    rpChoices = choices2,
                ),
            ),
            activeVariantIndex = 0,
        )

        val plan = ConversationForkUseCase.planBranchFromMessage(
            sourceConv = conv,
            targetMsgId = "u2",
            allMessages = listOf(a1, u2, a3),
            titleSuffix = " (分支)",
            now = 500L,
            newConvId = "fork_1",
        )

        assertNotNull(plan)
        plan!!
        assertEquals(listOf(1), plan.messages.map { it.seq })
        assertEquals(listOf("assistant"), plan.messages.map { it.role })
        assertEquals(u2, plan.prefillUserMessage)
        assertEquals(s0, plan.conversation.rp?.state)
        assertEquals(c0, plan.conversation.rp?.cast)
        assertEquals(choices1, plan.conversation.rp?.pendingChoices)
        assertEquals("{}", plan.conversation.loreTimedStatesJson)
        assertEquals("伦敦迷雾 (分支)", plan.conversation.title)
    }

    @Test
    fun branchFromFirstUserMessage_producesEmptyTimelineWithPrefill() {
        val conv = ChatConversation(
            id = "chat_1",
            title = "新对话",
            mode = ConversationMode.chat,
            params = ChatParams(activeWindowStartSeq = 1),
            archivedSummary = "Old summary",
            summarizedUntilSeq = 2,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val u1 = ChatMessage(id = "u1", conversationId = "chat_1", role = "user", content = "Hello", createdAt = 10L, seq = 1)
        val a2 = ChatMessage(id = "a2", conversationId = "chat_1", role = "assistant", content = "Hi", createdAt = 20L, seq = 2)

        val plan = ConversationForkUseCase.planBranchFromMessage(
            sourceConv = conv,
            targetMsgId = "u1",
            allMessages = listOf(u1, a2),
            titleSuffix = " (Branch)",
            now = 999L,
            newConvId = "fork_empty",
        )

        assertNotNull(plan)
        plan!!
        assertTrue(plan.messages.isEmpty())
        assertEquals(u1, plan.prefillUserMessage)
        assertEquals(0, plan.conversation.summarizedUntilSeq)
        assertEquals("", plan.conversation.archivedSummary)
        assertNull(plan.conversation.params.activeWindowStartSeq)
    }

    @Test
    fun branchFromAssistantMessage_includesAssistant_restoresNovelSnapshot_andFiltersFutureLoreAndStreaming() {
        val statusA3 = NovelStatusHud(location = "旧码头", mood = "紧张")
        val statusA5 = NovelStatusHud(location = "钟楼顶", mood = "决战")
        val conv = ChatConversation(
            id = "nov_1",
            title = "《雾都》",
            mode = ConversationMode.novel,
            novel = NovelConfig(title = "雾都", currentStatus = statusA5, pendingChoices = listOf("决战")),
            loreTimedStatesJson = "{\"x\":{\"lastTriggeredTurn\":5}}",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val msgs = listOf(
            ChatMessage(id = "u1", conversationId = "nov_1", role = "user", content = "开篇", createdAt = 1L, seq = 1),
            ChatMessage(
                id = "a3",
                conversationId = "nov_1",
                role = "assistant",
                content = "旧码头段落",
                createdAt = 3L,
                seq = 3,
                novelChoices = listOf("上船", "调查"),
                variants = listOf(
                    MessageVariant(
                        id = "v3",
                        content = "旧码头段落",
                        createdAt = 3L,
                        novelStatusSnapshot = statusA3,
                        novelChoices = listOf("上船", "调查"),
                    ),
                ),
                activeVariantIndex = 0,
            ),
            ChatMessage(id = "u4", conversationId = "nov_1", role = "user", content = "继续", createdAt = 4L, seq = 4),
            ChatMessage(id = "a5", conversationId = "nov_1", role = "assistant", content = "正在生成...", createdAt = 5L, seq = 5, status = "streaming"),
        )
        val lore = listOf(
            NovelLoreEntry(id = "l1", conversationId = "nov_1", title = "码头", content = "早", validFromSeq = 2),
            NovelLoreEntry(id = "l2", conversationId = "nov_1", title = "钟楼", content = "晚", validFromSeq = 5),
        )

        // Branching on an in-flight streaming message is rejected
        assertNull(
            ConversationForkUseCase.planBranchFromMessage(
                sourceConv = conv,
                targetMsgId = "a5",
                allMessages = msgs,
            ),
        )

        val plan = ConversationForkUseCase.planBranchFromMessage(
            sourceConv = conv,
            targetMsgId = "a3",
            allMessages = msgs,
            existingLore = lore,
            now = 777L,
            newConvId = "fork_nov",
        )
        assertNotNull(plan)
        plan!!
        assertEquals(listOf(1, 3), plan.messages.map { it.seq })
        assertNull(plan.prefillUserMessage)
        assertEquals(statusA3, plan.conversation.novel?.currentStatus)
        assertEquals(listOf("上船", "调查"), plan.conversation.novel?.pendingChoices)
        assertEquals(listOf("码头"), plan.lore.map { it.title })
        assertEquals("{}", plan.conversation.loreTimedStatesJson)
    }

    @Test
    fun branchFromRpSaveSlot_resetsCompactionBoundsWindowAndFiltersFutureLoreAndStreaming() {
        val conv = ChatConversation(
            id = "rp_slot_conv",
            title = "冒险",
            mode = ConversationMode.rp,
            params = ChatParams(activeWindowStartSeq = 20),
            archivedSummary = "Future Turn 30 summary",
            summarizedUntilSeq = 24,
            loreTimedStatesJson = "{\"t\":{\"lastTriggeredTurn\":15}}",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val slot = RpSaveSlot(
            id = "slot_1",
            conversationId = "rp_slot_conv",
            name = "进地牢前",
            createdAt = 100L,
            messageSeq = 4,
            rpConfig = RpConfig(title = "冒险", state = RpGameState(scene = "入口", hp = 100)),
            activeMessagesSnapshot = listOf(
                ChatMessage(id = "m1", conversationId = "rp_slot_conv", role = "assistant", content = "开场", createdAt = 1L, seq = 1),
                ChatMessage(id = "m4", conversationId = "rp_slot_conv", role = "assistant", content = "入口", createdAt = 4L, seq = 4),
                ChatMessage(id = "m5", conversationId = "rp_slot_conv", role = "assistant", content = "半截", createdAt = 5L, seq = 5, status = "streaming"),
            ),
            ledgerItems = listOf(
                MemoryLedgerItem(id = "led1", conversationId = "rp_slot_conv", title = "有效", content = "ok", validFromSeq = 2, stale = false),
                MemoryLedgerItem(id = "led2", conversationId = "rp_slot_conv", title = "过期", content = "no", validFromSeq = 2, stale = true),
            ),
            episodes = listOf(
                MemoryEpisode(id = "ep1", conversationId = "rp_slot_conv", title = "序章", summary = "ok", fromSeq = 1, toSeq = 3, stale = false),
                MemoryEpisode(id = "ep2", conversationId = "rp_slot_conv", title = "过期", summary = "no", fromSeq = 1, toSeq = 4, stale = true),
            ),
        )
        val lore = listOf(
            NovelLoreEntry(id = "l1", conversationId = "rp_slot_conv", title = "村庄", content = "ok", validFromSeq = 2),
            NovelLoreEntry(id = "l2", conversationId = "rp_slot_conv", title = "终章Boss", content = "future", validFromSeq = 18),
            NovelLoreEntry(id = "l3", conversationId = "rp_slot_conv", title = "失效", content = "stale", validFromSeq = 1, stale = true),
        )

        val plan = ConversationForkUseCase.planBranchFromRpSaveSlot(
            sourceConv = conv,
            slot = slot,
            existingLore = lore,
            now = 888L,
            newConvId = "fork_slot",
        )

        assertEquals(3, plan.conversation.summarizedUntilSeq)
        assertNull(plan.conversation.params.activeWindowStartSeq)
        assertEquals("{}", plan.conversation.loreTimedStatesJson)
        assertEquals(listOf(1, 4), plan.messages.map { it.seq })
        assertEquals(listOf("村庄"), plan.lore.map { it.title })
        assertEquals(listOf("有效"), plan.ledger.map { it.title })
        assertEquals(listOf("序章"), plan.episodes.map { it.title })

        // When slot has no episodes and source summarizedUntilSeq > cutoff, archivedSummary clears to ""
        val planNoEp = ConversationForkUseCase.planBranchFromRpSaveSlot(
            sourceConv = conv,
            slot = slot.copy(episodes = emptyList()),
            existingLore = lore,
            now = 888L,
            newConvId = "fork_slot_no_ep",
        )
        assertEquals(0, planNoEp.conversation.summarizedUntilSeq)
        assertEquals("", planNoEp.conversation.archivedSummary)
    }
}
