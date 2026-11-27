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

    @Test
    fun compactionWindowExclusionAndRollbackReset_reducesSentTokensAndReconcilesCleanly() = runBlocking {
        val longChunk = "兰溪谷公馆二楼大厅灯光微暗，众人在长桌旁激烈讨论并购细节。".repeat(30)
        val messages = (1..10).map { seq ->
            ChatMessage(
                id = "m$seq",
                conversationId = "conv-compact",
                role = if (seq % 2 == 1) "user" else "assistant",
                content = "第${seq}回合：$longChunk",
                createdAt = seq.toLong(),
                seq = seq,
            )
        }
        val baseConv = ChatConversation(
            id = "conv-compact",
            title = "压缩测试",
            mode = ConversationMode.chat,
            createdAt = 1L,
            updatedAt = 10L,
        )
        val beforeWaterline = ContextBuilder.computeContextWaterline(
            ContextBuildInput(conversation = baseConv, messages = messages, modelContextWindow = 128_000),
        )

        val ep = MemoryEpisode(
            id = "ep-1-6",
            conversationId = "conv-compact",
            level = 1,
            title = "前六回合纪要",
            summary = "完成了第一阶段谈判并签下保密协议。",
            fromSeq = 1,
            toSeq = 6,
            createdAt = 11L,
            updatedAt = 11L,
        )
        val compactedConv = baseConv.copy(
            archivedSummary = ep.summary,
            summarizedUntilSeq = 6,
        )
        val afterBuilt = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = compactedConv,
                messages = messages,
                memoryEpisodes = listOf(ep),
                modelContextWindow = 128_000,
            ),
        )

        // Compacted messages (seq <= 6) MUST be excluded from wire messages and reduce sentTokens
        val nonSystem = afterBuilt.wireMessages.filter { it.role != "system" }
        assertEquals(4, nonSystem.size)
        assertTrue(nonSystem.first().textContent!!.startsWith("第7回合"))
        assertTrue(afterBuilt.summary.sentTokens < beforeWaterline.sentTokens)
        assertTrue(afterBuilt.summary.archiveTotalTokens > afterBuilt.summary.sentTokens)

        // Rolling back past summarizedUntilSeq resets summarizedUntilSeq & archivedSummary cleanly
        val rolled = TimelineReconciler.reconcileOnRollback(
            cutoffSeq = 5,
            inclusiveOfCutoff = true,
            messages = messages,
            episodes = listOf(ep),
            summarizedUntilSeq = 6,
            activeWindowStartSeq = 7,
        )
        assertEquals(0, rolled.resetSummarizedUntilSeq)
        assertTrue(rolled.shouldClearArchivedSummary)
        assertTrue(rolled.resetActiveWindowStartSeq)
        val updatedConv = TimelineReconciler.applyRollbackToConversation(
            compactedConv.copy(params = ChatParams(activeWindowStartSeq = 7)),
            rolled,
        )
        assertEquals(0, updatedConv.summarizedUntilSeq)
        assertEquals("", updatedConv.archivedSummary)
        assertEquals(null, updatedConv.params.activeWindowStartSeq)
    }

    @Test
    fun dualMetricWaterline_separatesSlidingArchiveFromHardOverflowAndRespectsBudgetCaps() = runBlocking {
        val longBody = "这是一段很长的角色扮演对话正文内容用于触发滑动窗口裁剪验证双指标水位。".repeat(35)
        val messages = (1..24).map { seq ->
            ChatMessage(
                id = "m$seq",
                conversationId = "conv-slide",
                role = if (seq % 2 == 1) "user" else "assistant",
                content = "第${seq}轮：$longBody",
                createdAt = seq.toLong(),
                seq = seq,
            )
        }
        val conv = ChatConversation(
            id = "conv-slide",
            title = "长对话滑动测试",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 1024),
            createdAt = 1L,
            updatedAt = 24L,
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 16_384,
                modelId = "deepseek-chat",
            ),
        )
        val sum = built.summary
        assertTrue("Expected sliding trim on 16K window", sum.isSliding)
        assertTrue(sum.trimmedMessageCount > 0)
        assertEquals(sum.trimmedMessageCount, sum.uncompactedOverflowCount)
        assertFalse("Auto-trimmed sliding window should NOT fire hardOverflow alarm", sum.hardOverflow)
        assertFalse(sum.exceedsModelWindow)
        assertEquals(sum.sentTokens, sum.usedTokens)
        assertEquals(sum.sentPercentage, sum.percentage)
        assertTrue(sum.archiveTotalTokens > sum.sentTokens)
        assertTrue("Compactor should recommend proactive compaction on overflow >= 8", ContextCompactor.shouldAutoCompact(sum))

        // H4: Bounded tailBudget (2K/4K/8K) and summaryCap (<= 8K) even on 1M window
        assertEquals(2000, ContextBuilder.computeTailBudget(800_000, MemoryBudget.low))
        assertEquals(4000, ContextBuilder.computeTailBudget(800_000, MemoryBudget.standard))
        assertEquals(8000, ContextBuilder.computeTailBudget(800_000, MemoryBudget.high))
        assertEquals(8000, ContextBuilder.computeSummaryCap(800_000))
    }

    @Test
    fun enableAutoMemoryFalse_skipsPersonalUserMemoryAndAccountingMatchesRecalledItems() = runBlocking {
        val convDisabled = ChatConversation(
            id = "conv-nomem",
            title = "关闭个人记忆",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个助手。",
            params = ChatParams(enableAutoMemory = false),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val memories = listOf(
            UserMemory(
                id = "m-core",
                category = MemoryCategory.rule,
                content = "始终使用 Kotlin 回答所有问题。",
                pinned = true,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            UserMemory(
                id = "m-arch",
                category = MemoryCategory.project,
                content = "正在开发 GuGuChat Compose 应用。",
                pinned = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val msgs = listOf(
            ChatMessage(
                id = "u1",
                conversationId = convDisabled.id,
                role = "user",
                content = "帮我优化 GuGuChat Compose 列表性能",
                createdAt = 1L,
                seq = 1,
            ),
        )

        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = convDisabled,
                messages = msgs,
                memories = memories,
                modelId = "deepseek-chat",
            ),
        )
        val sysText = built.wireMessages.firstOrNull { it.role == "system" }?.textContent.orEmpty()
        val userText = built.wireMessages.last { it.role == "user" }.textContent.orEmpty()
        assertFalse(sysText.contains("<user_core_memory>"))
        assertFalse(userText.contains("<recalled_user_memory>"))
        assertEquals(0, built.summary.memoryTokens)
        assertTrue(built.summary.baseSystemTokens > 0)
        assertEquals(0, built.summary.recalledTotalTokens)
    }

    @Test
    fun rpAccounting_includesPinnedLedgerAndConstantLoreWithExactTotalAndSupportsMuteAndModelRecompute() = runBlocking {
        val preset = RpPresets.findById("modern_tavern")
        val conv = ChatConversation(
            id = "conv-rp-acct",
            title = preset.config.title,
            mode = ConversationMode.rp,
            rp = preset.config,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val lore = listOf(
            NovelLoreEntry(
                id = "const-1",
                conversationId = conv.id,
                category = "rule",
                title = "清吧规矩",
                content = "午夜后禁止开闪光灯。",
                alwaysActive = true,
                priority = 90,
                estimatedTokens = 999, // Stale inflated DB value
            ),
            NovelLoreEntry(
                id = "trig-1",
                conversationId = conv.id,
                category = "item",
                title = "特调失眠蓝",
                content = "海盐金酒与蝶豆花冷萃调制。",
                keywords = listOf("失眠蓝"),
                priority = 70,
                estimatedTokens = 999, // Stale inflated DB value
            ),
        )
        val ledger = listOf(
            MemoryLedgerItem(
                id = "led-pin",
                conversationId = conv.id,
                kind = "promise",
                title = "周五之约",
                content = "答应江晚柠周五带回旧黑胶唱片。",
                pinned = true,
                validFromSeq = 1,
                estimatedTokens = 888,
            ),
        )
        val msgs = listOf(
            ChatMessage(
                id = "u1",
                conversationId = conv.id,
                role = "user",
                content = "今晚来一杯失眠蓝吧。",
                createdAt = 1L,
                seq = 1,
            ),
        )

        val deepseekSummary = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                novelLore = lore,
                memoryLedger = ledger,
                modelId = "deepseek-chat",
            ),
        )
        val claudeSummary = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                novelLore = lore,
                memoryLedger = ledger,
                modelId = "claude-3-7-sonnet",
            ),
        )

        // Exact 1:1 recalledTotalTokens == recalledItems.sumOf { it.estimatedTokens }
        assertEquals(deepseekSummary.recalledItems.sumOf { it.estimatedTokens }, deepseekSummary.recalledTotalTokens)
        assertTrue(deepseekSummary.recalledItems.any { it.id == "const-1" && it.triggerKind == "constant" })
        assertTrue(deepseekSummary.recalledItems.any { it.id == "trig-1" && it.triggerKind == "keyword" })
        assertTrue(deepseekSummary.recalledItems.any { it.id == "led-pin" && it.triggerKind == "pinned_ledger" })
        // Dynamic model-family recompute overrides stale 999 Room estimates
        assertTrue(deepseekSummary.recalledTotalTokens < 200)
        assertTrue(deepseekSummary.sentTokens < claudeSummary.sentTokens)

        // Muting a recall key excludes it cleanly
        val mutedSummary = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                novelLore = lore,
                memoryLedger = ledger,
                modelId = "deepseek-chat",
                mutedRecallKeys = setOf("novel_lore:trig-1", "rp_ledger:led-pin"),
            ),
        )
        assertFalse(mutedSummary.recalledItems.any { it.id == "trig-1" })
        assertFalse(mutedSummary.recalledItems.any { it.id == "led-pin" })

        // Precomputed semantic scores enable hybrid mode cleanly even on synchronous waterline preview
        val semanticLore = lore + NovelLoreEntry(
            id = "sem-only",
            conversationId = conv.id,
            category = "concept",
            title = "雨夜往事",
            content = "十年前码头旧事。",
            keywords = listOf("旧码头"),
            priority = 60,
        )
        val hybridSummary = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                novelLore = semanticLore,
                memoryLedger = ledger,
                modelId = "deepseek-chat",
                precomputedSemanticScores = mapOf("sem-only" to 0.78, "trig-1" to 0.41),
                minSemanticCosine = 0.55,
            ),
        )
        assertEquals("hybrid", hybridSummary.retrievalModeUsed)
        assertTrue(hybridSummary.recalledItems.any { it.id == "sem-only" && it.triggerKind == "semantic" })
        assertTrue(hybridSummary.recalledItems.any { it.id == "trig-1" && it.triggerKind == "keyword" })
    }

    @Test
    fun novelSystemPrompt_omitsVolatileWordCountAndCompactorChunksWithout1400Truncation() {
        val novel = NovelConfig(title = "问道长生", targetWordsPerChapter = 3000)
        val ch1 = NovelChapter(id = "c1", conversationId = "n1", order = 1, title = "第一章", wordCount = 120)
        val ch2 = ch1.copy(wordCount = 2480)
        val sys1 = NovelEngine.buildNovelSystemPrompt(novel, ch1, emptyList())
        val sys2 = NovelEngine.buildNovelSystemPrompt(novel, ch2, emptyList())
        assertEquals("System prefix must stay byte-identical as chapter wordCount grows", sys1, sys2)

        // ContextCompactor chunks long 6K-char turns on paragraph boundaries instead of .take(1400)
        val paraTurn = (1..20).joinToString("\n\n") { idx ->
            "第${idx}段剧情：" + "海港夜雾弥漫，船坞灯火映照着旧货船的铁锈船身。".repeat(12)
        }
        val longMsgs = (1..6).map { seq ->
            ChatMessage(
                id = "cm$seq",
                conversationId = "c-chunk",
                role = if (seq % 2 == 1) "user" else "assistant",
                content = "TURN#$seq\n\n$paraTurn\n\nTAIL_MARKER_$seq",
                createdAt = seq.toLong(),
                seq = seq,
            )
        }
        val chunks = ContextCompactor.splitMessagesIntoTokenChunks(longMsgs, modelId = "deepseek-chat", targetChunkTokens = 6_000)
        assertTrue("Expected multiple chunks for long transcript, got ${chunks.size}", chunks.size >= 2)
        val joinedAll = chunks.flatten().joinToString("\n") { it.content }
        assertTrue("Tail of long turn must be preserved, not truncated at 1400 chars", joinedAll.contains("TAIL_MARKER_1"))
        assertTrue(joinedAll.contains("TAIL_MARKER_6"))
    }

    @Test
    fun overflowEpisodes_respectsMinSemanticCosineThreshold() = runBlocking {
        val conv = ChatConversation(
            id = "conv-ep-sem",
            title = "测试",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个助手。",
            summarizedUntilSeq = 4,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val fillerEpisodes = (1..8).map { idx ->
            MemoryEpisode(
                id = "ep-filler-$idx",
                conversationId = conv.id,
                level = 2,
                fromSeq = 1,
                toSeq = 1,
                title = "总览归档$idx",
                summary = "第${idx}章前情背景宏观脉络记录。".repeat(10),
                createdAt = idx.toLong(),
            )
        }
        val episodes = fillerEpisodes + listOf(
            MemoryEpisode(
                id = "ep-high-sem",
                conversationId = conv.id,
                level = 1,
                fromSeq = 2,
                toSeq = 3,
                title = "高语义关联片段",
                summary = "我们在海边灯塔讨论了密室钥匙。".repeat(16),
                keyEntities = listOf("灯塔", "密室"),
                createdAt = 10L,
            ),
            MemoryEpisode(
                id = "ep-low-sem",
                conversationId = conv.id,
                level = 1,
                fromSeq = 4,
                toSeq = 4,
                title = "低于自定义阈值的噪音片段",
                summary = "街角咖啡馆偶遇旧同事寒暄。".repeat(16),
                keyEntities = listOf("咖啡馆", "同事"),
                createdAt = 11L,
            ),
        )
        val msgs = listOf(
            ChatMessage(
                id = "u5",
                conversationId = conv.id,
                role = "user",
                content = "重新理一下线索吧",
                createdAt = 5L,
                seq = 5,
            ),
        )
        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = msgs,
                memoryEpisodes = episodes,
                memoryBudget = MemoryBudget.standard,
                modelContextWindow = 4_096,
                modelId = "deepseek-chat",
                precomputedSemanticScores = mapOf(
                    "ep-high-sem" to 0.68,
                    "ep-low-sem" to 0.58,
                ),
                minSemanticCosine = 0.64,
            ),
        )
        val recalledIds = built.summary.recalledItems.map { it.id }.toSet()
        assertTrue(recalledIds.contains("ep-high-sem"))
        assertFalse(recalledIds.contains("ep-low-sem"))
    }

    @Test
    fun buildChatContextV4_incrementalAttachmentDecayReducesWithinBudget() = runBlocking {
        val conv = ChatConversation(
            id = "conv-v4-decay",
            title = "多附件渐进衰减",
            mode = ConversationMode.chat,
            systemPrompt = "Assistant.",
            params = ChatParams(maxTokens = 256, maxImageTurns = 3, maxFileTurns = 3),
            createdAt = 1L,
            updatedAt = 5L,
        )
        val bigFileText = "Detailed protocol trace line with payload data.\n".repeat(180)
        val atts = (1..3).associate { idx ->
            val id = "att-f$idx"
            id to ChatAttachmentMeta(
                id = id,
                mimeType = "text/plain",
                sizeBytes = bigFileText.length.toLong(),
                localPath = "/tmp/$id.txt",
                kind = AttachmentKind.file,
                fileName = "trace_$idx.txt",
                textPath = "/tmp/$id.txt",
                charCount = bigFileText.length,
                estimatedTokens = ContextBuilder.estimateTextTokens(bigFileText),
            )
        }
        val messages = (1..3).flatMap { turn ->
            listOf(
                ChatMessage(
                    id = "u$turn",
                    conversationId = conv.id,
                    role = "user",
                    content = "Check turn $turn",
                    createdAt = turn * 2L - 1,
                    seq = turn * 2 - 1,
                    attachmentIds = listOf("att-f$turn"),
                ),
                ChatMessage(
                    id = "a$turn",
                    conversationId = conv.id,
                    role = "assistant",
                    content = "Checked $turn.",
                    createdAt = turn * 2L,
                    seq = turn * 2,
                ),
            )
        } + ChatMessage(
            id = "u4",
            conversationId = conv.id,
            role = "user",
            content = "Summarize all findings concisely.",
            createdAt = 10L,
            seq = 7,
        )

        val v4 = ContextBuilder.buildChatContextV4(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 2_400,
                attachmentsById = atts,
                resolveAttachmentText = { bigFileText },
            ),
        )
        assertTrue(v4.built.summary.sentTokens <= v4.built.summary.effectiveLimitTokens)
        assertFalse(v4.built.summary.hardOverflow)
    }
}
