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

    @Test
    fun buildChatContext_honorsContextWindowOverrideAndMaxHistoryMessages() = runBlocking {
        val longChunk = "这是一段用于测试上下文裁剪的长对话内容，重复多次以占用较多 Token。".repeat(25)
        val messages = (1..6).map { idx ->
            ChatMessage(
                id = "m$idx",
                conversationId = "conv-ctx",
                role = if (idx % 2 == 1) "user" else "assistant",
                content = "第${idx}条消息：$longChunk",
                createdAt = idx.toLong(),
                seq = idx,
            )
        }

        // 1. Cap history messages to last 2 even with a large 128K model window
        val convLimitMsgs = ChatConversation(
            id = "conv-ctx",
            title = "限制条数",
            mode = ConversationMode.chat,
            params = ChatParams(maxHistoryMessages = 2),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val builtLimitedMsgs = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = convLimitMsgs,
                messages = messages,
                modelContextWindow = 128_000,
            ),
        )
        val nonSystemLimited = builtLimitedMsgs.wireMessages.filter { it.role != "system" }
        assertEquals(2, nonSystemLimited.size)
        assertTrue(nonSystemLimited.first().textContent!!.startsWith("第5条消息"))
        assertTrue(nonSystemLimited.last().textContent!!.startsWith("第6条消息"))
        assertEquals(4, builtLimitedMsgs.summary.trimmedMessageCount)

        // 2. Cap context window override down to 4096 tokens on a 128K model
        val convSmallWin = convLimitMsgs.copy(
            params = ChatParams(contextWindowOverride = 4096, maxTokens = 1024),
        )
        val builtSmallWin = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = convSmallWin,
                messages = messages,
                modelContextWindow = 128_000,
            ),
        )
        assertEquals(4096, builtSmallWin.summary.modelContextWindow)
        assertTrue(builtSmallWin.summary.trimmedMessageCount > 0)
        assertTrue(builtSmallWin.wireMessages.count { it.role != "system" } < 6)

        // 3. Raise context window override above default 128K (e.g. 256K)
        val convLargeWin = convLimitMsgs.copy(
            params = ChatParams(contextWindowOverride = 256_000),
        )
        val wlLarge = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = convLargeWin,
                messages = messages,
                modelContextWindow = 128_000,
            ),
        )
        assertEquals(256_000, wlLarge.modelContextWindow)
        assertEquals(0, wlLarge.trimmedMessageCount)
    }

    @Test
    fun buildChatContext_budgetsGiantFileAndCombinesWithImageSafely() = runBlocking {
        val conv = ChatConversation(
            id = "conv-file-img",
            title = "大文件加图片",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 1024, maxFileTurns = 2),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val hugeDoc = buildString {
            appendLine("# 系统架构手册")
            for (i in 1..100) {
                appendLine("## 模块 $i")
                if (i == 66) {
                    appendLine("关键熔断阈值：网关单节点 QPS 超过 12800 时触发令牌桶降级，恢复窗口 45 秒。")
                } else {
                    appendLine("常规接口监控日志与例行巡检参数描述数据记录。".repeat(16))
                }
            }
        }
        val rawTok = ContextBuilder.estimateTextTokens(hugeDoc)
        val fileMeta = ChatAttachmentMeta(
            id = "att-doc",
            mimeType = "text/markdown",
            sizeBytes = hugeDoc.length.toLong(),
            localPath = "/tmp/att-doc.txt",
            kind = AttachmentKind.file,
            fileName = "arch_manual.md",
            textPath = "/tmp/att-doc.txt",
            charCount = hugeDoc.length,
            estimatedTokens = rawTok,
            previewSnippet = "系统架构手册",
        )
        val imgMeta = ChatAttachmentMeta(
            id = "att-img",
            mimeType = "image/jpeg",
            width = 1024,
            height = 768,
            sizeBytes = 120_000,
            localPath = "/tmp/att-img.jpg",
            kind = AttachmentKind.image,
            fileName = "diagram.jpg",
        )
        val messages = listOf(
            ChatMessage(
                id = "u1",
                conversationId = conv.id,
                role = "user",
                content = "请结合截图告诉我网关熔断阈值 QPS 是多少？",
                createdAt = 1L,
                seq = 1,
                attachmentIds = listOf("att-doc", "att-img"),
            ),
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 16_384,
                attachmentsById = mapOf("att-doc" to fileMeta, "att-img" to imgMeta),
                resolveAttachmentDataUrl = { id -> if (id == "att-img") "data:image/jpeg;base64,IMG" else null },
                resolveAttachmentText = { id -> if (id == "att-doc") hugeDoc else null },
            ),
        )

        val userMsg = built.wireMessages.last { it.role == "user" }
        val textPart = userMsg.parts?.firstOrNull { it.type == "text" }?.text.orEmpty()
        val imgPart = userMsg.parts?.firstOrNull { it.type == "image_url" }?.imageUrl

        assertEquals("data:image/jpeg;base64,IMG", imgPart)
        assertTrue(textPart.contains("<attached_file name=\"arch_manual.md\""))
        assertTrue(textPart.contains("mode=\"smart_rag\""))
        assertTrue(textPart.contains("12800"))
        assertTrue(
            "Actual wire text tokens should never blow up the 16K window",
            ContextBuilder.estimateTextTokens(textPart) < built.summary.effectiveLimitTokens,
        )
    }

    @Test
    fun buildChatContext_decaysOldFileToStubAndReRecallsWhenMentioned() = runBlocking {
        val conv = ChatConversation(
            id = "conv-file-decay",
            title = "多轮文件衰减",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 1024, maxFileTurns = 1),
            createdAt = 1L,
            updatedAt = 5L,
        )
        val docText = buildString {
            appendLine("# 2026 Q3 财报")
            for (i in 1..50) {
                appendLine("## 章节 $i")
                if (i == 31) {
                    appendLine("海外云服务毛利率提升至 68.4%，成为增长第一引擎。")
                } else {
                    appendLine("季度财务例行说明和基础折旧摊销条目汇总。".repeat(14))
                }
            }
        }
        val fileMeta = ChatAttachmentMeta(
            id = "att-q3",
            mimeType = "application/pdf",
            sizeBytes = docText.length.toLong(),
            localPath = "/tmp/att-q3.txt",
            kind = AttachmentKind.file,
            fileName = "Q3_Report.pdf",
            textPath = "/tmp/att-q3.txt",
            charCount = docText.length,
            estimatedTokens = ContextBuilder.estimateTextTokens(docText),
            pageCount = 18,
            previewSnippet = "2026 Q3 财报概览",
        )
        val messages = listOf(
            ChatMessage(
                id = "u1",
                conversationId = conv.id,
                role = "user",
                content = "帮我看下这份财报",
                createdAt = 1L,
                seq = 1,
                attachmentIds = listOf("att-q3"),
            ),
            ChatMessage(
                id = "a1",
                conversationId = conv.id,
                role = "assistant",
                content = "好的，整体营收表现稳健。",
                createdAt = 2L,
                seq = 2,
            ),
            ChatMessage(
                id = "u2",
                conversationId = conv.id,
                role = "user",
                content = "另外帮我列个明天会议议程",
                createdAt = 3L,
                seq = 3,
            ),
            ChatMessage(
                id = "a2",
                conversationId = conv.id,
                role = "assistant",
                content = "这是明天的会议议程草案。",
                createdAt = 4L,
                seq = 4,
            ),
            ChatMessage(
                id = "u3",
                conversationId = conv.id,
                role = "user",
                content = "再查一下刚才 Q3_Report 里的海外云服务毛利率是多少？",
                createdAt = 5L,
                seq = 5,
            ),
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 32_768,
                attachmentsById = mapOf("att-q3" to fileMeta),
                resolveAttachmentText = { id -> if (id == "att-q3") docText else null },
            ),
        )

        assertTrue(built.summary.decayedFileMessageIds.contains("u1"))
        val firstUserWire = built.wireMessages.first { it.role == "user" }
        assertTrue(firstUserWire.textContent!!.contains("历史文件已折叠"))
        assertFalse(firstUserWire.textContent!!.contains("<attached_file"))

        val latestUserWire = built.wireMessages.last { it.role == "user" }
        assertTrue(latestUserWire.textContent!!.contains("<attached_file name=\"Q3_Report.pdf\""))
        assertTrue(latestUserWire.textContent!!.contains("68.4%"))
    }
}
