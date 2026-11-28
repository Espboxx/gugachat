package com.gugachat.app.domain.chat

import com.gugachat.app.data.local.CharacterCardEntity
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
        assertTrue(textPart.contains("<attached_file_index name=\"arch_manual.md\""))
        assertTrue(textPart.contains("<recalled_file_context>"))
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
        assertTrue(firstUserWire.textContent!!.contains("<attached_file_index name=\"Q3_Report.pdf\""))
        assertFalse(firstUserWire.textContent!!.contains("<attached_file "))

        val latestUserWire = built.wireMessages.last { it.role == "user" }
        assertTrue(latestUserWire.textContent!!.contains("<recalled_file_context>"))
        assertTrue(latestUserWire.textContent!!.contains("Q3_Report.pdf"))
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

    @Test
    fun tier1AdaptiveRetention_keeps19kFileFullOn128kTurn3WithoutCacheBusting() = runBlocking {
        val conv = ChatConversation(
            id = "conv-tier1",
            title = "恋综2.0",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 2048, maxFileTurns = 2),
            createdAt = 1L,
            updatedAt = 10L,
        )
        // ~19.8K tokens Chinese docx
        val unit = "男嘉宾崔晨浩是建筑设计师，性格沉稳；女嘉宾林知夏是独立策展人，擅长油画修复。第一天傍晚在海岛玻璃花房集合。\n"
        val docText = "# 恋综2.0后会有期\n## 角色名单\n" + unit.repeat(360)
        val docTokens = ContextBuilder.estimateTextTokens(docText)
        assertTrue("Expected ~15K-23K tokens doc, was $docTokens", docTokens in 12_000..23_500)

        val meta = ChatAttachmentMeta(
            id = "att-show",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = docText.length.toLong(),
            localPath = "/tmp/att-show.txt",
            kind = AttachmentKind.file,
            fileName = "恋综2.0后会有期.docx",
            textPath = "/tmp/att-show.txt",
            charCount = docText.length,
            estimatedTokens = docTokens,
            previewSnippet = "恋综2.0后会有期角色设定",
            injectMode = FileInjectMode.auto,
        )
        val turn1Messages = listOf(
            ChatMessage(id = "u1", conversationId = conv.id, role = "user", content = "看下这个设定，准备开局", createdAt = 1L, seq = 1, attachmentIds = listOf("att-show")),
        )
        val turn3Messages = turn1Messages + listOf(
            ChatMessage(id = "a1", conversationId = conv.id, role = "assistant", content = "好的，已完整阅读全部嘉宾与流程。", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = conv.id, role = "user", content = "介绍一下海岛别墅", createdAt = 3L, seq = 3),
            ChatMessage(id = "a2", conversationId = conv.id, role = "assistant", content = "别墅临海而建，二楼带星空露台。", createdAt = 4L, seq = 4),
            ChatMessage(id = "u3", conversationId = conv.id, role = "user", content = "第一天开局，崔晨浩出场了", createdAt = 5L, seq = 5),
        )

        val builtTurn1 = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = turn1Messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-show" to meta),
                resolveAttachmentText = { docText },
            ),
        )
        val builtTurn3 = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = turn3Messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-show" to meta),
                resolveAttachmentText = { docText },
            ),
        )

        assertFalse("19.8K file on 128K model must NOT decay on Turn 3", builtTurn3.summary.decayedFileMessageIds.contains("u1"))
        val u1Turn1 = builtTurn1.wireMessages.first { it.role == "user" }.textContent!!
        val u1Turn3 = builtTurn3.wireMessages.first { it.role == "user" }.textContent!!
        assertTrue(u1Turn3.contains("<attached_file name=\"恋综2.0后会有期.docx\""))
        assertTrue(u1Turn3.contains("mode=\"full\""))
        assertFalse(u1Turn3.contains("历史文件已折叠"))
        // Prompt cache safety: u1 wire text must remain byte-for-byte identical between Turn 1 and Turn 3
        assertEquals(u1Turn1, u1Turn3)
    }

    @Test
    fun tier2Recall_worksWithoutMentioningFilenameAndAfterCompactionAndRespectsSoftDeleteAndMute() = runBlocking {
        val conv = ChatConversation(
            id = "conv-tier2",
            title = "超大文档动态召回",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 1024),
            createdAt = 1L,
            updatedAt = 20L,
        )
        val docText = buildString {
            appendLine("# 恋综2.0后会有期")
            for (i in 1..100) {
                appendLine("## 第 $i 节 录制流程与嘉宾安排")
                if (i == 62) {
                    appendLine("【核心人物档案】林知夏是27岁的独立策展人，随身携带一把蓝柄折扇，最怕雷雨天。")
                    appendLine("她在第二天秘密约会中抽中了灯塔手作陶艺任务。")
                } else {
                    appendLine("例行拍摄排期、海岛天气播报与节目组后勤统筹清单。".repeat(16))
                }
            }
        }
        val docTokens = ContextBuilder.estimateTextTokens(docText)
        assertTrue("Expected >25K tokens doc, was $docTokens", docTokens > 25_000)

        val meta = ChatAttachmentMeta(
            id = "att-big",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = docText.length.toLong(),
            localPath = "/tmp/att-big.txt",
            kind = AttachmentKind.file,
            fileName = "恋综2.0后会有期.docx",
            textPath = "/tmp/att-big.txt",
            charCount = docText.length,
            estimatedTokens = docTokens,
            previewSnippet = "恋综2.0后会有期完整设定",
            injectMode = FileInjectMode.auto,
        )
        val indexedDoc = FileContextBudgeter.buildIndexedDocument("att-big", meta.fileName, docText)
        val messages = listOf(
            ChatMessage(id = "u1", conversationId = conv.id, role = "user", content = "看下这份设定文档", createdAt = 1L, seq = 1, attachmentIds = listOf("att-big")),
            ChatMessage(id = "a1", conversationId = conv.id, role = "assistant", content = "已收到并建立索引。", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = conv.id, role = "user", content = "我们先聊聊海岛天气", createdAt = 3L, seq = 3),
            ChatMessage(id = "a2", conversationId = conv.id, role = "assistant", content = "海风清凉，晚间有阵雨。", createdAt = 4L, seq = 4),
            // Does NOT mention filename "恋综2.0后会有期" at all!
            ChatMessage(id = "u3", conversationId = conv.id, role = "user", content = "林知夏随身带了什么，抽中了什么任务？", createdAt = 5L, seq = 5),
        )

        // 1) Normal Turn 3 without filename mention -> immutable TOC in u1 + Zone 3 recall in u3
        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-big" to meta),
                preloadedFileChunks = mapOf("att-big" to indexedDoc),
                resolveAttachmentText = { docText },
            ),
        )
        val u1Wire = built.wireMessages.first { it.role == "user" }.textContent!!
        val u3Wire = built.wireMessages.last { it.role == "user" }.textContent!!
        assertTrue(u1Wire.contains("<attached_file_index name=\"恋综2.0后会有期.docx\""))
        assertTrue(u3Wire.contains("<recalled_file_context>"))
        assertTrue(u3Wire.contains("蓝柄折扇"))
        assertTrue(u3Wire.contains("灯塔手作陶艺"))

        // Verify waterline & Recall Inspector parity ("file_chunk" source)
        val fileChunkItems = built.summary.recalledItems.filter { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE }
        assertTrue("Expected recalled file_chunk inspect items", fileChunkItems.isNotEmpty())
        val wl = ContextBuilder.computeContextWaterline(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-big" to meta),
                preloadedFileChunks = mapOf("att-big" to indexedDoc),
            ),
        )
        assertEquals(
            fileChunkItems.map { it.id },
            wl.recalledItems.filter { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE }.map { it.id },
        )

        // 2) Muting the top recalled chunk excludes it from both recall items and prompt
        val topKey = "${FileContextBudgeter.FILE_CHUNK_SOURCE}:${fileChunkItems.first().id}"
        val builtMuted = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-big" to meta),
                preloadedFileChunks = mapOf("att-big" to indexedDoc),
                mutedRecallKeys = setOf(topKey),
            ),
        )
        assertFalse(builtMuted.summary.recalledItems.any { "${it.source}:${it.id}" == topKey })

        // 3) Post-compaction recall: compacting past u1 (summarizedUntilSeq = 2) keeps file recallable!
        val compactedConv = conv.copy(summarizedUntilSeq = 2, archivedSummary = "前两轮已归档。")
        val builtCompacted = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = compactedConv,
                messages = messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-big" to meta),
                preloadedFileChunks = mapOf("att-big" to indexedDoc),
            ),
        )
        val u3Compacted = builtCompacted.wireMessages.last { it.role == "user" }.textContent!!
        assertTrue("Compacted file must remain recallable in Zone 3", u3Compacted.contains("蓝柄折扇"))

        // 4) Soft-deleted upload message must stop recalling immediately (zero ghost recall)
        val softDeletedMsgs = messages.map { if (it.id == "u1") it.copy(deletedAt = 999L) else it }
        val builtDeleted = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = softDeletedMsgs,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-big" to meta),
                preloadedFileChunks = mapOf("att-big" to indexedDoc),
            ),
        )
        assertFalse(builtDeleted.wireMessages.last { it.role == "user" }.textContent!!.contains("<recalled_file_context>"))
        assertTrue(builtDeleted.summary.recalledItems.none { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE })
    }

    @Test
    fun fileInjectModeFull_pinsLarge28kFileFullOn128kModel() = runBlocking {
        val conv = ChatConversation(
            id = "conv-pin-full",
            title = "固定全文",
            mode = ConversationMode.chat,
            params = ChatParams(maxTokens = 2048),
            createdAt = 1L,
            updatedAt = 5L,
        )
        val docText = "# 完整剧本设定\n" + "这是用于测试固定全文模式的剧本详细设定条目与世界观说明。\n".repeat(900)
        val docTokens = ContextBuilder.estimateTextTokens(docText)
        assertTrue("Expected 25K-40K tokens doc, was $docTokens", docTokens in 24_500..42_000)

        val pinnedMeta = ChatAttachmentMeta(
            id = "att-pin",
            mimeType = "text/plain",
            sizeBytes = docText.length.toLong(),
            localPath = "/tmp/att-pin.txt",
            kind = AttachmentKind.file,
            fileName = "full_script.txt",
            textPath = "/tmp/att-pin.txt",
            charCount = docText.length,
            estimatedTokens = docTokens,
            injectMode = FileInjectMode.full,
        )
        val messages = listOf(
            ChatMessage(id = "u1", conversationId = conv.id, role = "user", content = "固定全文记住", createdAt = 1L, seq = 1, attachmentIds = listOf("att-pin")),
            ChatMessage(id = "a1", conversationId = conv.id, role = "assistant", content = "已记住。", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = conv.id, role = "user", content = "继续剧情", createdAt = 3L, seq = 3),
            ChatMessage(id = "a2", conversationId = conv.id, role = "assistant", content = "继续中。", createdAt = 4L, seq = 4),
            ChatMessage(id = "u3", conversationId = conv.id, role = "user", content = "第三轮对话", createdAt = 5L, seq = 5),
        )
        val built = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = conv,
                messages = messages,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-pin" to pinnedMeta),
                resolveAttachmentText = { docText },
            ),
        )
        assertFalse("FileInjectMode.full under hard cap must stay Tier-1 full", built.summary.decayedFileMessageIds.contains("u1"))
        val u1Wire = built.wireMessages.first { it.role == "user" }.textContent!!
        assertTrue(u1Wire.contains("<attached_file name=\"full_script.txt\""))
        assertTrue(u1Wire.contains("mode=\"full\""))
    }

    @Test
    fun zone3LengthDirective_injectedAcrossChatRpNovelLegacyAndV4WithoutMutatingCachePrefix() = runBlocking {
        // 1) Standard Chat (Legacy) + cache prefix invariance
        val chatMsgs = listOf(
            ChatMessage(id = "u1", conversationId = "c-len-chat", role = "user", content = "第一轮问题", createdAt = 1L, seq = 1),
            ChatMessage(id = "a1", conversationId = "c-len-chat", role = "assistant", content = "第一轮回答", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = "c-len-chat", role = "user", content = "请详细分析 Android 渲染管线", createdAt = 3L, seq = 3),
        )
        val chatNoMin = ChatConversation(
            id = "c-len-chat",
            title = "普通对话",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个资深工程师。",
            params = ChatParams(maxTokens = 2048, minOutputChars = null),
            createdAt = 1L,
            updatedAt = 3L,
        )
        val chatWithMin = chatNoMin.copy(params = chatNoMin.params.copy(minOutputChars = 1500))

        val builtChatBase = ContextBuilder.buildChatContext(
            ContextBuildInput(conversation = chatNoMin, messages = chatMsgs, modelContextWindow = 64_000, modelId = "claude-sonnet-4"),
        )
        val builtChatMin = ContextBuilder.buildChatContext(
            ContextBuildInput(conversation = chatWithMin, messages = chatMsgs, modelContextWindow = 64_000, modelId = "claude-sonnet-4"),
        )
        // Zone 0 system prompt and Zone 1/2 prior turns must remain byte-identical (Prompt Cache safe)
        assertEquals(builtChatBase.wireMessages[0].textContent, builtChatMin.wireMessages[0].textContent)
        assertEquals(builtChatBase.wireMessages[1].textContent, builtChatMin.wireMessages[1].textContent)
        assertEquals(builtChatBase.wireMessages[2].textContent, builtChatMin.wireMessages[2].textContent)
        val chatZone3 = builtChatMin.wireMessages.last().textContent!!
        assertTrue(chatZone3.contains("【输出长度要求】"))
        assertTrue(chatZone3.contains("1500"))
        // Output reserve should tighten effectiveLimitTokens when minOutputChars floor exceeds default maxTokens
        assertTrue(builtChatMin.summary.effectiveLimitTokens < builtChatBase.summary.effectiveLimitTokens)

        // 2) RP (V4 mode with CharacterCard post-history instructions) — length directive placed AFTER V4 post-history block
        val rpCard = CharacterCardEntity(
            id = "card-v4",
            name = "林雾",
            description = "雨夜侦探",
            postHistoryInstructions = "保持第一人称冷硬派叙事风格。",
        )
        val rpConvBase = ChatConversation(
            id = "c-len-rp",
            title = "雨夜推理",
            mode = ConversationMode.rp,
            characterCardId = rpCard.id,
            params = ChatParams(maxTokens = 2048, minOutputChars = null),
            rp = RpConfig(title = "雨夜推理", worldview = "霓虹港城"),
            createdAt = 1L,
            updatedAt = 3L,
        )
        val rpConvMin = rpConvBase.copy(params = rpConvBase.params.copy(minOutputChars = 800))
        val rpMsgs = listOf(
            ChatMessage(id = "ru1", conversationId = rpConvBase.id, role = "user", content = "推开侦探社木门", createdAt = 1L, seq = 1),
            ChatMessage(id = "ra1", conversationId = rpConvBase.id, role = "assistant", content = "铃铛轻响。", createdAt = 2L, seq = 2),
            ChatMessage(id = "ru2", conversationId = rpConvBase.id, role = "user", content = "把信封放在桌上", createdAt = 3L, seq = 3),
        )
        val builtRpBase = ContextBuilder.buildChatContextV4(
            ContextBuildInput(conversation = rpConvBase, messages = rpMsgs, characterCard = rpCard, modelContextWindow = 64_000),
        ).built
        val builtRpMin = ContextBuilder.buildChatContextV4(
            ContextBuildInput(conversation = rpConvMin, messages = rpMsgs, characterCard = rpCard, modelContextWindow = 64_000),
        ).built
        assertEquals(builtRpBase.wireMessages[0].textContent, builtRpMin.wireMessages[0].textContent)
        assertEquals(builtRpBase.wireMessages[1].textContent, builtRpMin.wireMessages[1].textContent)
        val rpZone3 = builtRpMin.wireMessages.last().textContent!!
        val postIdx = rpZone3.indexOf("【后置核心指令")
        val lenIdx = rpZone3.indexOf("【输出长度要求】")
        assertTrue("Expected V4 post-history instructions in Zone 3", postIdx >= 0)
        assertTrue("Expected RP length directive after V4 post-history block", lenIdx > postIdx)
        assertTrue(rpZone3.contains("<rp_state>"))

        // 3) Novel (Legacy + active chapter + synthetic opening turn when no user messages exist yet)
        val ch1 = NovelChapter(id = "ch1", conversationId = "c-len-novel", order = 1, title = "第一章 风起", outline = "少年踏出青石镇", wordCount = 400)
        val novelConv = ChatConversation(
            id = "c-len-novel",
            title = "剑来风云",
            mode = ConversationMode.novel,
            params = ChatParams(maxTokens = 2048, minOutputChars = 1200),
            novel = NovelConfig(title = "剑来风云", activeChapterId = ch1.id, targetWordsPerChapter = 3000),
            createdAt = 1L,
            updatedAt = 2L,
        )
        val builtNovelOpening = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = novelConv,
                messages = emptyList(),
                novelChapters = listOf(ch1),
                modelContextWindow = 64_000,
            ),
        )
        val novelSynthetic = builtNovelOpening.wireMessages.last { it.role == "user" }.textContent!!
        assertTrue(novelSynthetic.contains("【输出长度要求】"))
        assertTrue(novelSynthetic.contains("1200"))
        assertTrue(novelSynthetic.contains("3000"))
    }

    @Test
    fun zone3LengthDirective_coexistsCleanlyWithTier1AndTier2FileRecall() = runBlocking {
        // Tier-1 full file + minOutputChars: Zone 0 & u1 stay byte-identical when minOutputChars toggles
        val smallDoc = "# 人物小传\n" + "林知夏擅长古画修复，常驻海岛玻璃花房。".repeat(80)
        val smallMeta = ChatAttachmentMeta(
            id = "att-small",
            mimeType = "text/plain",
            sizeBytes = smallDoc.length.toLong(),
            localPath = "/tmp/att-small.txt",
            kind = AttachmentKind.file,
            fileName = "小传.txt",
            textPath = "/tmp/att-small.txt",
            charCount = smallDoc.length,
            estimatedTokens = ContextBuilder.estimateTextTokens(smallDoc),
            injectMode = FileInjectMode.auto,
        )
        val t1Msgs = listOf(
            ChatMessage(id = "u1", conversationId = "c-coex-t1", role = "user", content = "记住这份设定", createdAt = 1L, seq = 1, attachmentIds = listOf("att-small")),
            ChatMessage(id = "a1", conversationId = "c-coex-t1", role = "assistant", content = "已记住。", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = "c-coex-t1", role = "user", content = "描写她在花房修复古画的桥段", createdAt = 3L, seq = 3),
        )
        val t1BaseConv = ChatConversation(
            id = "c-coex-t1",
            title = "Tier1协同",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个细腻的创作者。",
            params = ChatParams(maxTokens = 2048, minOutputChars = null),
            createdAt = 1L,
            updatedAt = 3L,
        )
        val t1MinConv = t1BaseConv.copy(params = t1BaseConv.params.copy(minOutputChars = 800))
        val builtT1Base = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = t1BaseConv,
                messages = t1Msgs,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-small" to smallMeta),
                resolveAttachmentText = { smallDoc },
            ),
        )
        val builtT1Min = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = t1MinConv,
                messages = t1Msgs,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-small" to smallMeta),
                resolveAttachmentText = { smallDoc },
            ),
        )
        assertEquals(builtT1Base.wireMessages[0].textContent, builtT1Min.wireMessages[0].textContent)
        assertEquals(builtT1Base.wireMessages[1].textContent, builtT1Min.wireMessages[1].textContent)
        assertTrue(builtT1Min.wireMessages[1].textContent!!.contains("mode=\"full\""))
        assertTrue(builtT1Min.wireMessages.last().textContent!!.contains("【输出长度要求】"))

        // Tier-2 large file (>25K tok) + minOutputChars: immutable <attached_file_index> in u1,
        // and <recalled_file_context> followed by Zone 3 length directive in final user turn
        val largeDoc = buildString {
            appendLine("# 星港编年史")
            for (i in 1..100) {
                appendLine("## 第 $i 节 港区档案")
                if (i == 48) {
                    appendLine("【密室线索】青铜怀表背面刻着“北纬31度，潮汐钟楼三层”。")
                } else {
                    appendLine("星港货轮调度表、常规气象简报与港区维护记录。".repeat(16))
                }
            }
        }
        val largeTokens = ContextBuilder.estimateTextTokens(largeDoc)
        assertTrue("Expected >25K tokens doc, was $largeTokens", largeTokens > 25_000)
        val largeMeta = ChatAttachmentMeta(
            id = "att-large",
            mimeType = "text/plain",
            sizeBytes = largeDoc.length.toLong(),
            localPath = "/tmp/att-large.txt",
            kind = AttachmentKind.file,
            fileName = "星港编年史.txt",
            textPath = "/tmp/att-large.txt",
            charCount = largeDoc.length,
            estimatedTokens = largeTokens,
            injectMode = FileInjectMode.auto,
        )
        val indexedLarge = FileContextBudgeter.buildIndexedDocument("att-large", largeMeta.fileName, largeDoc)
        val t2Msgs = listOf(
            ChatMessage(id = "u1", conversationId = "c-coex-t2", role = "user", content = "导入港区档案", createdAt = 1L, seq = 1, attachmentIds = listOf("att-large")),
            ChatMessage(id = "a1", conversationId = "c-coex-t2", role = "assistant", content = "档案已索引。", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = "c-coex-t2", role = "user", content = "青铜怀表背面刻着什么？请详细推理它指向的地点。", createdAt = 3L, seq = 3),
        )
        val t2BaseConv = ChatConversation(
            id = "c-coex-t2",
            title = "Tier2协同",
            mode = ConversationMode.chat,
            systemPrompt = "你是一个推理助手。",
            params = ChatParams(maxTokens = 2048, minOutputChars = null),
            createdAt = 1L,
            updatedAt = 3L,
        )
        val t2MinConv = t2BaseConv.copy(params = t2BaseConv.params.copy(minOutputChars = 1500))
        val builtT2Base = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = t2BaseConv,
                messages = t2Msgs,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-large" to largeMeta),
                preloadedFileChunks = mapOf("att-large" to indexedLarge),
            ),
        )
        val builtT2Min = ContextBuilder.buildChatContext(
            ContextBuildInput(
                conversation = t2MinConv,
                messages = t2Msgs,
                modelContextWindow = 128_000,
                attachmentsById = mapOf("att-large" to largeMeta),
                preloadedFileChunks = mapOf("att-large" to indexedLarge),
            ),
        )
        // Zone 0 & immutable u1 <attached_file_index> remain byte-identical
        assertEquals(builtT2Base.wireMessages[0].textContent, builtT2Min.wireMessages[0].textContent)
        assertEquals(builtT2Base.wireMessages[1].textContent, builtT2Min.wireMessages[1].textContent)
        assertTrue(builtT2Min.wireMessages[1].textContent!!.contains("<attached_file_index name=\"星港编年史.txt\""))

        val t2Zone3 = builtT2Min.wireMessages.last().textContent!!
        val recallIdx = t2Zone3.indexOf("<recalled_file_context>")
        val directiveIdx = t2Zone3.indexOf("【输出长度要求】")
        assertTrue("Expected recalled file context in Zone 3", recallIdx >= 0)
        assertTrue("Expected recalled clue text in Zone 3", t2Zone3.contains("潮汐钟楼三层"))
        assertTrue("Expected length directive after recalled file context", directiveIdx > recallIdx)
        assertTrue(builtT2Min.summary.recalledItems.any { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE })
    }
}
