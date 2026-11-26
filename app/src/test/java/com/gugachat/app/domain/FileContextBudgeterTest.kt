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
}
