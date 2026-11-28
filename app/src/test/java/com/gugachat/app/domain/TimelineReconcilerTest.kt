package com.gugachat.app.domain.chat

import com.gugachat.app.data.chat.ConversationForkUseCase
import com.gugachat.app.ui.chat.NovelStudioDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReconcilerTest {

    @Test
    fun reconcileOnRollback_invalidatesFutureBranchAndRestoresSupersededPriorEntries() {
        val messages = listOf(
            ChatMessage(id = "m1", conversationId = "c1", role = "user", content = "q1", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "c1", role = "assistant", content = "a1", createdAt = 2L, seq = 2),
            ChatMessage(id = "m3", conversationId = "c1", role = "user", content = "q2", createdAt = 3L, seq = 3),
            ChatMessage(id = "m4", conversationId = "c1", role = "assistant", content = "a2", createdAt = 4L, seq = 4),
        )
        val ledger = listOf(
            MemoryLedgerItem(id = "l-early", conversationId = "c1", title = "早期约定", content = "x", validFromSeq = 2),
            MemoryLedgerItem(id = "l-late", conversationId = "c1", title = "后续事件", content = "y", validFromSeq = 4),
            MemoryLedgerItem(id = "l-locked", conversationId = "c1", title = "锁定事实", content = "z", validFromSeq = 4, locked = true),
            MemoryLedgerItem(id = "l-restored", conversationId = "c1", title = "曾被覆盖", content = "w", validFromSeq = 1, supersededAtSeq = 4, stale = true),
        )
        val lore = listOf(
            NovelLoreEntry(id = "lore-late", conversationId = "c1", title = "新增角色", content = "...", validFromSeq = 4),
            NovelLoreEntry(id = "lore-const", conversationId = "c1", title = "常驻规则", content = "...", validFromSeq = 4, alwaysActive = true),
        )
        val chapters = listOf(
            NovelChapter(id = "ch1", conversationId = "c1", order = 1, title = "第1章", startSeq = 1, endSeq = 3, status = "done"),
            NovelChapter(id = "ch2", conversationId = "c1", order = 2, title = "第2章", startSeq = 4, status = "writing"),
        )

        val res = TimelineReconciler.reconcileOnRollback(
            cutoffSeq = 3,
            inclusiveOfCutoff = true,
            messages = messages,
            ledgerItems = ledger,
            loreEntries = lore,
            chapters = chapters,
        )

        assertEquals(2, res.newTipSeq)
        assertEquals(2, TimelineReconciler.activeMessages(res.messages).size)

        val ledgerById = res.ledgerItems.associateBy { it.id }
        assertFalse(ledgerById.getValue("l-early").stale)
        assertTrue(ledgerById.getValue("l-late").stale)
        assertFalse(ledgerById.getValue("l-locked").stale)
        assertFalse(ledgerById.getValue("l-restored").stale)
        assertNull(ledgerById.getValue("l-restored").supersededAtSeq)

        val loreById = res.loreEntries.associateBy { it.id }
        assertTrue(loreById.getValue("lore-late").stale)
        assertFalse(loreById.getValue("lore-const").stale)

        val chById = res.chapters.associateBy { it.id }
        assertEquals("writing", chById.getValue("ch1").status)
        assertNull(chById.getValue("ch1").endSeq)
        assertEquals("draft", chById.getValue("ch2").status)
        assertTrue("Reset future draft startSeq must move past max message seq", chById.getValue("ch2").startSeq > 4)
    }

    @Test
    fun latestTipAssistant_returnsNullWhenTrailingMessageIsUser() {
        val a1 = ChatMessage(id = "a1", conversationId = "c1", role = "assistant", content = "Opening", createdAt = 1L, seq = 1)
        val u2 = ChatMessage(id = "u2", conversationId = "c1", role = "user", content = "Action", createdAt = 2L, seq = 2)
        val a3 = ChatMessage(id = "a3", conversationId = "c1", role = "assistant", content = "Reply", createdAt = 3L, seq = 3)

        assertNull(TimelineReconciler.latestTipAssistant(listOf(a1, u2)))
        assertEquals("a1", TimelineReconciler.latestTipAssistant(listOf(a1))?.id)
        assertEquals("a3", TimelineReconciler.latestTipAssistant(listOf(a1, u2, a3))?.id)
        assertNull(TimelineReconciler.latestTipAssistant(emptyList()))
    }

    @Test
    fun novelRollbackAcrossChapterBoundaries_restoresActiveChapterIdAndPreventsDraftHijack() {
        val messages = listOf(
            ChatMessage(id = "m1", conversationId = "n1", role = "user", content = "u1", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "n1", role = "assistant", content = "ch1 prose", createdAt = 2L, seq = 2),
            ChatMessage(id = "m3", conversationId = "n1", role = "user", content = "u2", createdAt = 3L, seq = 3),
            ChatMessage(id = "m4", conversationId = "n1", role = "assistant", content = "ch1 end", createdAt = 4L, seq = 4),
            ChatMessage(id = "m5", conversationId = "n1", role = "user", content = "u3", createdAt = 5L, seq = 5),
            ChatMessage(id = "m6", conversationId = "n1", role = "assistant", content = "ch2 prose", createdAt = 6L, seq = 6),
        )
        val ch1 = NovelChapter(id = "ch1", conversationId = "n1", order = 1, title = "第1章", startSeq = 1, endSeq = 4, status = "done")
        val ch2 = NovelChapter(id = "ch2", conversationId = "n1", order = 2, title = "第2章", startSeq = 5, endSeq = null, status = "writing")
        val conv = ChatConversation(
            id = "n1",
            title = "《测试》",
            mode = ConversationMode.novel,
            novel = NovelConfig(title = "测试", activeChapterId = "ch2"),
            createdAt = 1L,
            updatedAt = 2L,
        )

        val res = TimelineReconciler.reconcileOnRollback(
            cutoffSeq = 3,
            inclusiveOfCutoff = false,
            messages = messages,
            chapters = listOf(ch1, ch2),
        )
        val rolledConv = TimelineReconciler.applyRollbackToConversation(conv, res)

        assertEquals("ch1", rolledConv.novel?.activeChapterId)
        val chById = res.chapters.associateBy { it.id }
        assertEquals("writing", chById.getValue("ch1").status)
        assertNull(chById.getValue("ch1").endSeq)
        assertEquals("draft", chById.getValue("ch2").status)
        assertTrue(chById.getValue("ch2").startSeq > 6)

        // New message at seq = 7 must land in ch1, NOT hijacked by reset draft ch2
        val afterGenMsgs = res.messages + ChatMessage(
            id = "m7",
            conversationId = "n1",
            role = "assistant",
            content = "regenerated ch1 prose",
            createdAt = 7L,
            seq = 7,
        )
        val grouped = NovelExport.groupMessagesByChapter(res.chapters, afterGenMsgs)
        val ch1Msgs = grouped.first { it.chapter?.id == "ch1" }.messages.map { it.id }
        val ch2Msgs = grouped.first { it.chapter?.id == "ch2" }.messages.map { it.id }
        assertEquals(listOf("m2", "m7"), ch1Msgs)
        assertTrue(ch2Msgs.isEmpty())
    }

    @Test
    fun novelRollbackNonInclusiveAtChapterStart_keepsActiveChapterOpen() {
        val messages = listOf(
            ChatMessage(id = "m1", conversationId = "n1", role = "assistant", content = "ch1", createdAt = 1L, seq = 1),
            ChatMessage(id = "m2", conversationId = "n1", role = "user", content = "ch2 first user msg", createdAt = 2L, seq = 2),
            ChatMessage(id = "m3", conversationId = "n1", role = "assistant", content = "ch2 reply", createdAt = 3L, seq = 3),
        )
        val ch1 = NovelChapter(id = "ch1", conversationId = "n1", order = 1, title = "第1章", startSeq = 1, endSeq = 1, status = "done")
        val ch2 = NovelChapter(id = "ch2", conversationId = "n1", order = 2, title = "第2章", startSeq = 2, endSeq = null, status = "writing")
        val conv = ChatConversation(
            id = "n1",
            title = "《测试》",
            mode = ConversationMode.novel,
            novel = NovelConfig(title = "测试", activeChapterId = "ch2"),
            createdAt = 1L,
            updatedAt = 2L,
        )

        val res = TimelineReconciler.reconcileOnRollback(
            cutoffSeq = 2,
            inclusiveOfCutoff = false,
            messages = messages,
            chapters = listOf(ch1, ch2),
        )
        val rolledConv = TimelineReconciler.applyRollbackToConversation(conv, res)
        val chById = res.chapters.associateBy { it.id }
        assertEquals("writing", chById.getValue("ch2").status)
        assertEquals(2, chById.getValue("ch2").startSeq)
        assertEquals("ch2", rolledConv.novel?.activeChapterId)
    }

    @Test
    fun restoreModeStateAtCutoff_restoresRpAndNovelSnapshotsForRollback() {
        val rpS0 = RpGameState(scene = "营地", hp = 100)
        val rpS1 = RpGameState(scene = "悬崖", hp = 40)
        val c0 = listOf(RpChoice(id = "c0", label = "休息", actionText = "休息"))
        val c1 = listOf(RpChoice(id = "c1", label = "跳跃", actionText = "跳跃"))
        val rpConv = ChatConversation(
            id = "rp1",
            title = "RP",
            mode = ConversationMode.rp,
            rp = RpConfig(title = "RP", state = rpS1, pendingChoices = c1),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val a1 = ChatMessage(
            id = "a1",
            conversationId = "rp1",
            role = "assistant",
            content = "营地夜色",
            createdAt = 1L,
            seq = 1,
            rpChoices = c0,
            variants = listOf(MessageVariant(id = "v1", content = "营地夜色", createdAt = 1L, rpStateSnapshot = rpS0, rpChoices = c0)),
        )
        val u2 = ChatMessage(id = "u2", conversationId = "rp1", role = "user", content = "改词后的行动", createdAt = 2L, seq = 2)

        val (restoredRp, _) = ConversationForkUseCase.restoreModeStateAtCutoff(rpConv, listOf(a1, u2), null)
        assertEquals(rpS0, restoredRp?.state)
        assertEquals(c0, restoredRp?.pendingChoices)
    }

    @Test
    fun removeChapterAndCloseGap_keepsDeletedProseInAdjacentChapterWithoutOrphans() {
        val ch1 = NovelChapter(id = "ch1", conversationId = "n1", order = 1, title = "第1章", startSeq = 1, endSeq = 2, status = "done")
        val ch2 = NovelChapter(id = "ch2", conversationId = "n1", order = 2, title = "第2章", startSeq = 3, endSeq = 4, status = "done")
        val ch3 = NovelChapter(id = "ch3", conversationId = "n1", order = 3, title = "第3章", startSeq = 5, endSeq = null, status = "writing")
        val msgs = listOf(
            ChatMessage(id = "m1", conversationId = "n1", role = "assistant", content = "一", createdAt = 1L, seq = 1),
            ChatMessage(id = "m3", conversationId = "n1", role = "assistant", content = "二", createdAt = 3L, seq = 3),
            ChatMessage(id = "m5", conversationId = "n1", role = "assistant", content = "三", createdAt = 5L, seq = 5),
        )

        // Delete middle chapter -> ch1 absorbs seq 3..4
        val middleResult = NovelStudioDelegate.removeChapterAndCloseGap(listOf(ch1, ch2, ch3), "ch2", "ch3", now = 100L)
        assertNotNull(middleResult)
        val groupedMid = NovelExport.groupMessagesByChapter(middleResult!!.first, msgs)
        assertTrue("No orphan section allowed", groupedMid.none { it.chapter == null })
        assertEquals(listOf("m1", "m3"), groupedMid.first { it.chapter?.id == "ch1" }.messages.map { it.id })
        assertEquals(listOf("m5"), groupedMid.first { it.chapter?.id == "ch3" }.messages.map { it.id })

        // Delete first chapter -> ch2 pulls startSeq down to 1 and absorbs seq 1..2
        val firstResult = NovelStudioDelegate.removeChapterAndCloseGap(listOf(ch1, ch2, ch3), "ch1", "ch1", now = 100L)
        assertNotNull(firstResult)
        assertEquals("ch2", firstResult!!.second)
        val groupedFirst = NovelExport.groupMessagesByChapter(firstResult.first, msgs)
        assertTrue("No orphan section allowed", groupedFirst.none { it.chapter == null })
        assertEquals(listOf("m1", "m3"), groupedFirst.first { it.chapter?.id == "ch2" }.messages.map { it.id })
    }
}
