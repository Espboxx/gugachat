package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileContextBudgeterTest {

    private fun makeFileMeta(
        id: String,
        name: String,
        tokens: Int,
        mode: FileInjectMode = FileInjectMode.auto,
        snippet: String = "示例摘要内容",
    ) = ChatAttachmentMeta(
        id = id,
        mimeType = "text/plain",
        sizeBytes = tokens.toLong() * 3L,
        localPath = "/tmp/$id.txt",
        kind = AttachmentKind.file,
        fileName = name,
        textPath = "/tmp/$id.txt",
        charCount = tokens * 2,
        estimatedTokens = tokens,
        previewSnippet = snippet,
        injectMode = mode,
    )

    @Test
    fun computeFileTurnBudget_respects45PercentShareAnd48kHardCap() {
        val b32k = FileContextBudgeter.computeFileTurnBudget(effectiveLimitTokens = 24_000, reservedFixedTokens = 2_000)
        assertTrue("32K window file budget should be <= 45% (10800), was $b32k", b32k <= (24_000 * 0.45).toInt())
        assertTrue("32K window file budget should be >= 1200, was $b32k", b32k >= 1200)

        val b1m = FileContextBudgeter.computeFileTurnBudget(effectiveLimitTokens = 800_000, reservedFixedTokens = 4_000)
        assertEquals(FileContextBudgeter.HARD_MAX_FILE_TURN_TOKENS, b1m)
    }

    @Test
    fun fitFileToBudget_injectsSmallFileInFull() {
        val text = "# API 配置\n默认超时 30 秒，重试次数 3 次。"
        val meta = makeFileMeta("f1", "config.md", ContextBuilder.estimateTextTokens(text))
        val fitted = FileContextBudgeter.fitFileToBudget(
            meta = meta,
            fullText = text,
            queryText = "超时是多少？",
            budgetTokens = 4000,
        )
        assertEquals("full", fitted.appliedMode)
        assertTrue(fitted.formattedBlock.contains("mode=\"full\""))
        assertTrue(fitted.formattedBlock.contains("默认超时 30 秒"))
    }

    @Test
    fun fitFileToBudget_giant100kFileNeverOverflowsBudgetAndRetrievesTargetedChapter() {
        val sb = StringBuilder()
        sb.appendLine("# 2026 年度集团运营白皮书")
        sb.appendLine("引言：本报告汇总各业务线全年关键经营指标与组织升级方案。")
        for (i in 1..140) {
            sb.appendLine("\n## 第 $i 节 常规业务运营记录")
            if (i == 77) {
                sb.appendLine("【核心保密条款】量子冷链仓储项目在哈尔滨极寒测试场的年度预算为 4820 万元，负责人为林雪松。")
                sb.appendLine("第二期扩建将在十月中旬验收，液氮温控冗余系数要求达到 3.6 倍。")
            } else {
                sb.appendLine("本章节详细记录了第 $i 周期的标准流程巡检、门店日常物料补给以及例行培训复盘数据。".repeat(16))
            }
        }
        sb.appendLine("\n## 结语与附录")
        sb.appendLine("全文最终审批由董事会于 2026 年 12 月签署归档。")

        val giantText = sb.toString()
        val rawTokens = ContextBuilder.estimateTextTokens(giantText)
        assertTrue("Fixture should be a huge doc (>40K tok), was $rawTokens", rawTokens > 40_000)

        val meta = makeFileMeta("giant-1", "whitepaper_2026.md", rawTokens)
        val budget32k = FileContextBudgeter.computeFileTurnBudget(effectiveLimitTokens = 24_000, reservedFixedTokens = 3_000)

        val fitted = FileContextBudgeter.fitFileToBudget(
            meta = meta,
            fullText = giantText,
            queryText = "量子冷链仓储项目在哈尔滨的预算和负责人是谁？",
            budgetTokens = budget32k,
        )
        assertEquals("smart_rag", fitted.appliedMode)
        assertTrue(
            "Injected tokens (${fitted.injectedTokens}) must stay safely within budget ($budget32k + small wrapper margin)",
            fitted.injectedTokens <= budget32k + 120,
        )
        assertTrue("BM25 retrieval should capture section 77 budget fact", fitted.formattedBlock.contains("4820 万元"))
        assertTrue("BM25 retrieval should capture leader name", fitted.formattedBlock.contains("林雪松"))
        assertTrue("Head anchor should be preserved", fitted.formattedBlock.contains("2026 年度集团运营白皮书"))
        assertTrue("Tail anchor should be preserved", fitted.formattedBlock.contains("董事会于 2026 年 12 月签署归档"))
    }

    @Test
    fun fitFileToBudget_summaryQueryUsesHeadTailSampling() {
        val giant = (1..90).joinToString("\n\n") { idx ->
            "## 第 $idx 章\n" + "这是第 $idx 章的正文叙述内容，用来测试全文总结模式下的均匀抽样策略。".repeat(14)
        }
        val rawTokens = ContextBuilder.estimateTextTokens(giant)
        val meta = makeFileMeta("sum-1", "book.md", rawTokens)
        val fitted = FileContextBudgeter.fitFileToBudget(
            meta = meta,
            fullText = giant,
            queryText = "请帮我总结一下这个文档",
            budgetTokens = 3_500,
        )
        assertEquals("head_tail", fitted.appliedMode)
        assertTrue(fitted.injectedTokens <= 3_620)
        assertTrue(fitted.formattedBlock.contains("第 1 章"))
        assertTrue(fitted.formattedBlock.contains("第 90 章"))
    }

    @Test
    fun allocateFileBudgets_protectsSmallFileFromGiantCompanion() {
        val small = makeFileMeta("small", "notes.txt", 300)
        val giant = makeFileMeta("giant", "huge.pdf", 80_000)
        val alloc = FileContextBudgeter.allocateFileBudgets(listOf(small, giant), totalBudgetTokens = 10_000)

        assertTrue("Small file should get at least its full tokens", (alloc["small"] ?: 0) >= 300)
        assertTrue("Giant file should get the bulk of remaining budget", (alloc["giant"] ?: 0) >= 9_000)
        assertTrue("Total allocation must stay within budget", alloc.values.sum() <= 10_048)
    }

    @Test
    fun decayedStubAndFilenameMentionWorkAsExpected() {
        val meta = makeFileMeta("f-old", "Q3_Financial_Report.pdf", 42_500, snippet = "第三季度财务营收与利润拆分")
        val stub = FileContextBudgeter.buildDecayedFileStub(meta)
        assertTrue(stub.contains("Q3_Financial_Report.pdf"))
        assertTrue(stub.contains("42.5K tok"))
        assertTrue(ContextBuilder.estimateTextTokens(stub) <= 100)

        assertTrue(FileContextBudgeter.messageMentionsFile("再帮我查一下 Q3_Financial_Report 里的现金流", meta))
        assertFalse(FileContextBudgeter.messageMentionsFile("今天天气真好", meta))
    }

    @Test
    fun chunkDocument_preserves15PercentOverlapAndCapsHugeDocsAt600Chunks() {
        // 1) Overlap continuity across mid-section split
        val sectionLines = (1..80).map { i ->
            "第${i}条记录：角色属性与互动规则编号 R-${i}，包含完整数值说明与背景线索。"
        }
        val doc = "# 核心规则\n" + sectionLines.joinToString("\n")
        val chunks = FileContextBudgeter.chunkDocument(doc, "rules.md", targetChunkTokens = 240)
        assertTrue("Expected at least 3 chunks, got ${chunks.size}", chunks.size >= 3)
        // Verify trailing line of chunk[0] overlaps into chunk[1]
        val tailLine0 = chunks[0].text.lines().last().trim()
        assertTrue(
            "Expected overlap line '$tailLine0' from chunk 0 to appear in chunk 1",
            chunks[1].text.contains(tailLine0),
        )

        // 2) >100K token giant document caps <= 600 chunks via adaptive chunk sizing
        val giantLine = "这是一段用于测试超大文档自适应分块上限的标准中文长文本记录，涵盖世界观设定与角色时间线事件。".repeat(3)
        val hugeText = (1..1500).joinToString("\n") { idx -> "条目$idx：$giantLine" }
        val hugeTokens = ContextBuilder.estimateTextTokens(hugeText)
        assertTrue("Expected >100K tokens fixture, got $hugeTokens", hugeTokens > 100_000)
        val hugeChunks = FileContextBudgeter.chunkDocument(hugeText, "huge_novel.txt")
        assertTrue(
            "Huge doc chunks (${hugeChunks.size}) must be capped <= ${FileContextBudgeter.MAX_ADAPTIVE_CHUNKS}",
            hugeChunks.size in 10..FileContextBudgeter.MAX_ADAPTIVE_CHUNKS,
        )
    }

    @Test
    fun broadenedChineseHeadingsAndImmutableIndexAnchor() {
        val text = """
            一、节目背景与规则
            本节目采用封闭式海岛录制。
            【男嘉宾资料】
            崔晨浩，建筑设计师，28岁。
            Day 1 初遇与破冰晚餐
            全体嘉宾于傍晚六点在玻璃花房集合。
        """.trimIndent()
        val outline = FileContextBudgeter.extractDocumentOutline(text, maxTokens = 260)
        assertTrue(outline.contains("一、节目背景与规则"))
        assertTrue(outline.contains("【男嘉宾资料】"))
        assertTrue(outline.contains("Day 1 初遇与破冰晚餐"))

        val meta = makeFileMeta("doc-1", "恋综2.0后会有期.docx", 19_800)
        val anchor = FileContextBudgeter.buildDocumentIndexAnchor(meta, outline, chunkCount = 48)
        assertTrue(anchor.contains("<attached_file_index name=\"恋综2.0后会有期.docx\""))
        assertTrue(anchor.contains("chunks=\"48\""))
        assertTrue(anchor.contains("Day 1 初遇与破冰晚餐"))
        assertTrue(anchor.contains("严禁编造"))
        assertTrue(ContextBuilder.estimateTextTokens(anchor) <= 360)
    }

    @Test
    fun fileRecallEngine_recallsSingleProperNounAndExpandsNeighborAndGroupsAcrossFiles() {
        val doc1Text = buildString {
            appendLine("# 恋综设定")
            for (i in 1..20) {
                appendLine("## 第 $i 节 常规录制安排")
                if (i == 12) {
                    appendLine("男三号崔晨浩是海归建筑设计师，随身携带一枚银色指南针，性格表面冷静实则温柔。")
                    appendLine("他的秘密任务是在第一晚晚宴前找到遗落在露台的明信片。")
                } else {
                    appendLine("节目组日常机位调度、灯光测试与场地安全检查说明。".repeat(12))
                }
            }
        }
        val doc2Text = buildString {
            appendLine("# 别墅地图与规则")
            for (i in 1..12) {
                appendLine("## 区域 $i")
                if (i == 7) {
                    appendLine("二楼露台设有星空望远镜，崔晨浩常在夜间独自在此画草图。")
                } else {
                    appendLine("房间基础设施、空调开关与公共厨房使用规范。".repeat(12))
                }
            }
        }
        val indexed1 = FileContextBudgeter.buildIndexedDocument("att-1", "恋综2.0后会有期.docx", doc1Text)
        val indexed2 = FileContextBudgeter.buildIndexedDocument("att-2", "别墅地图.md", doc2Text)
        val conv = ChatConversation(
            id = "conv-recall",
            title = "恋综",
            mode = ConversationMode.chat,
            createdAt = 1L,
            updatedAt = 2L,
        )

        // Single proper noun query ("崔晨浩") should pass relaxed minCleanTermsOverride = 1
        val bundle = FileRecallEngine.recall(
            tier2Docs = listOf(indexed1, indexed2),
            fileMetasById = mapOf(
                "att-1" to makeFileMeta("att-1", "恋综2.0后会有期.docx", indexed1.totalTokens, FileInjectMode.retrieve),
                "att-2" to makeFileMeta("att-2", "别墅地图.md", indexed2.totalTokens, FileInjectMode.retrieve),
            ),
            uploadTurnFileIds = emptySet(),
            conversation = conv,
            recentMessages = emptyList(),
            latestUserQuery = "崔晨浩",
            budgetTokens = 4_000,
        )
        assertTrue(bundle.recalledBlock.contains("<recalled_file_context>"))
        assertTrue("Should recall single proper noun chunk from doc1", bundle.recalledBlock.contains("海归建筑设计师"))
        assertTrue("Should recall cross-file chunk from doc2", bundle.recalledBlock.contains("星空望远镜"))
        assertTrue(bundle.recalledItems.all { it.source == FileContextBudgeter.FILE_CHUNK_SOURCE })
        // Neighbor expansion (i + 1) should pull adjacent chunk after the hit
        assertTrue(
            "Expected neighbor chunk to be expanded when budget permits",
            bundle.recalledItems.any { it.triggerKind == "neighbor" },
        )
        // Document-order grouping: doc1 excerpts appear before doc2 excerpts
        val doc1Pos = bundle.recalledBlock.indexOf("恋综2.0后会有期.docx")
        val doc2Pos = bundle.recalledBlock.indexOf("别墅地图.md")
        assertTrue("Expected doc1 before doc2 in recalled block", doc1Pos in 0 until doc2Pos)
    }
}
