package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }
}
