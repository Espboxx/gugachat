package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelEngineTest {

    @Test
    fun matchNovelLore_supportsConstantDirectAndOneHopRecursion() {
        val lore = listOf(
            NovelLoreEntry(
                id = "const",
                conversationId = "n1",
                category = "rule",
                title = "五境体系",
                content = "炼气至化神",
                alwaysActive = true,
                priority = 90,
                estimatedTokens = 20,
            ),
            NovelLoreEntry(
                id = "hop0",
                conversationId = "n1",
                category = "location",
                title = "青岚剑冢",
                content = "位于宗门后山，深处镇压着太古残碑。",
                keywords = listOf("剑冢"),
                estimatedTokens = 30,
            ),
            NovelLoreEntry(
                id = "hop1",
                conversationId = "n1",
                category = "item",
                title = "太古残碑",
                content = "刻有九道先天灵纹，唯有废脉体质能唤醒。",
                keywords = listOf("残碑"),
                estimatedTokens = 30,
            ),
        )

        val res = NovelEngine.matchNovelLore(
            lore = lore,
            recentMessages = emptyList(),
            draftText = "顾尘夜访青岚剑冢",
            chapterOutline = "",
            loreTokenBudget = 500,
            activeTipSeq = 5,
        )

        assertEquals(listOf("const"), res.constant.map { it.id })
        val trigIds = res.triggered.map { it.id }
        assertTrue(trigIds.contains("hop0"))
        assertTrue(trigIds.contains("hop1"))
    }

    @Test
    fun parseNovelTurnResponse_andExport_stripTagsCleanly() {
        val raw = """
            雨点噼啪打在青石阶上，顾尘指尖轻触碑面。
            <status_hud>{"chapterTitle":"第一章 残碑微光","time":"子夜","location":"青岚剑冢","present":["顾尘"],"mood":"肃杀","conflict":"灵纹暴走","foreshadows":["黑衣人窥伺"]}</status_hud>
            <lore_sync>[{"category":"item","title":"玄铁扫帚","content":"杂役院特制法器","keywords":["扫帚"]}]</lore_sync>
            <chapter_end>{"summary":"顾尘唤醒残碑成功","nextChapterTitle":"第二章 外门风波"}</chapter_end>
            <choices>["按压灵纹","转身隐入剑林"]</choices>
        """.trimIndent()

        val outcome = NovelEngine.parseNovelTurnResponse(raw, "n1", turnSeq = 2)
        assertEquals("雨点噼啪打在青石阶上，顾尘指尖轻触碑面。", outcome.cleanProse)
        assertEquals("青岚剑冢", outcome.statusHud?.location)
        assertEquals(1, outcome.loreUpserts.size)
        assertEquals("玄铁扫帚", outcome.loreUpserts[0].title)
        assertEquals("顾尘唤醒残碑成功", outcome.chapterEndSummary)
        assertEquals("第二章 外门风波", outcome.suggestedNextChapterTitle)
        assertEquals(2, outcome.choices.size)

        val novel = NovelConfig(title = "问剑/测试?")
        val ch1 = NovelChapter(id = "ch1", conversationId = "n1", order = 1, title = "第一章 残碑微光", startSeq = 1)
        val msgs = listOf(ChatMessage(id = "m2", conversationId = "n1", role = "assistant", content = raw, createdAt = 1L, seq = 2))
        val md = NovelExport.formatAsMarkdown(novel, listOf(ch1), msgs)
        assertTrue(md.contains("# 问剑/测试?"))
        assertTrue(md.contains("雨点噼啪打在青石阶上"))
        assertFalse(md.contains("<status_hud>"))
        assertEquals("问剑_测试_.md", NovelExport.sanitizeFileName(novel.title, "md"))
    }
}
