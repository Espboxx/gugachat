package com.gugachat.app.domain.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    @Test
    fun buildChatContext_reservesPrefixDecaysOldImagesAndInjectsTailIntoLastUser() = runBlocking {
        val conv = ChatConversation(
            id = "conv-1",
            title = "测试对话",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个简洁的助手。",
            params = ChatParams(maxImageTurns = 1, maxTokens = 1024),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val memories = listOf(
            UserMemory(
                id = "mem-core",
                category = MemoryCategory.rule,
                content = "所有代码例子默认使用 Kotlin。",
                pinned = true,
                createdAt = 1L,
                updatedAt = 1L,
                estimatedTokens = 20,
            ),
            UserMemory(
                id = "mem-archival",
                category = MemoryCategory.project,
                content = "我正在开发 GuGuChat 原生安卓应用，需要支持 Compose UI。",
                pinned = false,
                createdAt = 1L,
                updatedAt = 1L,
                estimatedTokens = 25,
            ),
        )
        val messages = listOf(
            ChatMessage(
                id = "u1",
                conversationId = "conv-1",
                role = "user",
                content = "看下第一张设计图",
                createdAt = 1L,
                seq = 1,
                attachmentIds = listOf("att-1"),
            ),
            ChatMessage(
                id = "a1",
                conversationId = "conv-1",
                role = "assistant",
                content = "好的，我看到你的线框图了。",
                createdAt = 2L,
                seq = 2,
            ),
            ChatMessage(
                id = "u2",
                conversationId = "conv-1",
                role = "user",
                content = "这是 GuGuChat Compose UI 的第二张截图",
                createdAt = 3L,
                seq = 3,
                attachmentIds = listOf("att-2"),
            ),
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                memories = memories,
                memoryBudget = MemoryBudget.standard,
                modelContextWindow = 16384,
                resolveAttachmentDataUrl = { id -> "data:image/jpeg;base64,DATA_$id" },
            ),
        )

        assertTrue(built.summary.decayedImageMessageIds.contains("u1"))
        assertFalse(built.summary.decayedImageMessageIds.contains("u2"))

        val sysMsg = built.wireMessages.first { it.role == "system" }
        assertTrue(sysMsg.textContent!!.contains("所有代码例子默认使用 Kotlin"))

        val firstUser = built.wireMessages.first { it.role == "user" }
        assertTrue(firstUser.textContent!!.contains("历史图片已省略"))

        val lastUser = built.wireMessages.last { it.role == "user" }
        val textPart = lastUser.parts?.firstOrNull { it.type == "text" }?.text.orEmpty()
        assertTrue(textPart.contains("<recalled_user_memory>"))
        assertTrue(textPart.contains("GuGuChat"))
        assertEquals(
            "data:image/jpeg;base64,DATA_att-2",
            lastUser.parts?.firstOrNull { it.type == "image_url" }?.imageUrl,
        )
    }

    @Test
    fun buildChatContext_createsSyntheticUserTurnWhenNoUserMessagesExist() = runBlocking {
        val conv = ChatConversation(
            id = "conv-novel",
            title = "玄幻开篇",
            mode = ConversationMode.novel,
            novel = NovelPresets.findById("xuanhuan").let { p ->
                NovelConfig(
                    title = "残碑问道",
                    genre = p.genre,
                    style = p.style,
                    worldview = p.worldview,
                    activeChapterId = "ch-1",
                )
            },
            createdAt = 1L,
            updatedAt = 1L,
        )
        val chapters = listOf(
            NovelChapter(
                id = "ch-1",
                conversationId = "conv-novel",
                order = 1,
                title = "第一章 残碑微光",
                outline = "主角清扫剑冢触发灵纹",
                status = "writing",
                startSeq = 0,
            ),
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = emptyList(),
                novelChapters = chapters,
            ),
        )

        assertTrue(built.wireMessages.any { it.role == "system" && it.textContent!!.contains("残碑问道") })
        val syntheticUser = built.wireMessages.lastOrNull { it.role == "user" }
        assertTrue(syntheticUser != null && syntheticUser.textContent!!.contains("主角清扫剑冢触发灵纹"))
    }

    @Test
    fun buildChatContext_rpModeInjectsConstantAndTriggeredWorldbookAndStripsAssistantTags() = runBlocking {
        val preset = RpPresets.findById("modern_tavern")
        val conv = ChatConversation(
            id = "conv-rp-worldbook",
            title = preset.config.title,
            mode = ConversationMode.rp,
            rp = preset.config,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val loreEntries = listOf(
            NovelLoreEntry(
                id = "lore-const",
                conversationId = conv.id,
                category = "location",
                title = "清吧常驻规矩",
                content = "午夜十二点营业，禁止店里开闪光灯。",
                keywords = listOf("第七站台"),
                alwaysActive = true,
                priority = 90,
                enabled = true,
            ),
            NovelLoreEntry(
                id = "lore-trig",
                conversationId = conv.id,
                category = "item",
                title = "特调失眠蓝",
                content = "海盐金酒与蝶豆花冷萃调制的雨夜限定特调。",
                keywords = listOf("失眠蓝", "特调"),
                alwaysActive = false,
                priority = 70,
                enabled = true,
            ),
            NovelLoreEntry(
                id = "lore-disabled",
                conversationId = conv.id,
                category = "concept",
                title = "已禁用秘密",
                content = "这条词条处于关闭状态不应触发。",
                keywords = listOf("失眠蓝"),
                alwaysActive = false,
                enabled = false,
            ),
        )
        val messages = listOf(
            ChatMessage(
                id = "rp-a1",
                conversationId = conv.id,
                role = "assistant",
                content = "江晚柠微笑着推来温毛巾。<rp_state>{\"mood\":\"柔和\"}</rp_state><rp_choices>[{\"label\":\"点酒\"}]</rp_choices>",
                createdAt = 1L,
                seq = 1,
            ),
            ChatMessage(
                id = "rp-u1",
                conversationId = conv.id,
                role = "user",
                content = "老板娘，今晚来一杯你新调的失眠蓝吧。",
                rpInputKind = RpInputKind.speech,
                createdAt = 2L,
                seq = 2,
            ),
        )

        val input = ContextBuildInput(
            conversation = conv,
            messages = messages,
            novelLore = loreEntries,
            draftText = "失眠蓝",
        )
        val built = ContextBuilder.buildChatContext(input)
        val waterline = ContextBuilder.computeContextWaterline(input)

        val sysMsg = built.wireMessages.first { it.role == "system" }
        assertTrue(sysMsg.textContent!!.contains("清吧常驻规矩"))
        assertFalse(sysMsg.textContent!!.contains("已禁用秘密"))

        val assistantHistory = built.wireMessages.first { it.role == "assistant" }
        assertEquals("江晚柠微笑着推来温毛巾。", assistantHistory.textContent)

        val userTurn = built.wireMessages.last { it.role == "user" }
        assertTrue(userTurn.textContent!!.contains("<rp_worldbook>"))
        assertTrue(userTurn.textContent!!.contains("特调失眠蓝"))
        assertTrue(userTurn.textContent!!.contains("后置核心指令"))
        assertFalse(userTurn.textContent!!.contains("已禁用秘密"))

        assertTrue(built.summary.triggeredLoreIds.contains("lore-trig"))
        assertTrue(built.summary.triggeredLoreTitles.contains("特调失眠蓝"))
        assertTrue(built.summary.recalledItems.any { it.id == "lore-trig" })
        assertTrue(built.summary.novelLoreTokens > 0)
        assertTrue(waterline.recalledItems.any { it.id == "lore-trig" })
        assertTrue(waterline.novelLoreTokens > 0)
    }
}
