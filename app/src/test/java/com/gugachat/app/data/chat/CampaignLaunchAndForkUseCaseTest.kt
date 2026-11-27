package com.gugachat.app.data.chat

import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.domain.chat.ChatConversation
import com.gugachat.app.domain.chat.ChatMessage
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.MemoryEpisode
import com.gugachat.app.domain.chat.NovelChapter
import com.gugachat.app.domain.chat.NovelConfig
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpChoice
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpGameState
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
}
