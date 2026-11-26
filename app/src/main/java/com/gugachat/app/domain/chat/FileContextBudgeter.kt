package com.gugachat.app.domain.chat

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DocumentChunk(
    val index: Int,
    val label: String,
    val text: String,
    val estimatedTokens: Int,
)

data class FittedFileResult(
    val formattedBlock: String,
    val appliedMode: String, // "full" | "smart_rag" | "head_tail" | "head_truncated"
    val injectedTokens: Int,
    val totalTokens: Int,
    val selectedChunkCount: Int,
    val totalChunkCount: Int,
)

object FileContextBudgeter {
    const val HARD_MAX_FILE_TURN_TOKENS = 48_000
    const val MIN_FILE_TURN_TOKENS = 1_200
    const val MAX_FILE_WINDOW_SHARE = 0.45
    const val DEFAULT_CHUNK_TOKENS = 420
    const val DECAYED_STUB_TOKENS = 72

    private val HEADING_RE = Regex("^\\s*(#{1,4}\\s+.+|\\[第\\s*\\d+\\s*页\\]|第[一二三四五六七八九十百千\\d]+[章节回篇卷].*|##\\s+.+)$")
    private val CODE_DECL_RE = Regex("^\\s*(public|private|internal|protected|export|async|override|suspend|data|sealed)?\\s*(class|interface|object|enum|struct|trait|fun|def|function|func|const\\s+\\w+\\s*=)\\s+[\\w$<>]+.*")
    private val GENERIC_SUMMARY_RE = Regex(
        "(请|帮我|帮忙|麻烦|一下|一下子|这个|这份|这篇|该|一下|总结|概括|梳理|提炼|分析|看看|读一下|看下|读下|主要|核心|整体|大概|全文|文档|文件|资料|附件|pdf|word|内容|要点|大纲|讲了什么|说了什么|写了什么|是什么|please|summarize|summary|overview|explain|review|read|analyze|this|the|attached|file|document|doc|me|for|of|about|in)",
        RegexOption.IGNORE_CASE,
    )

    fun computeFileTurnBudget(
        effectiveLimitTokens: Int,
        reservedFixedTokens: Int,
    ): Int {
        val safeLimit = max(4096, effectiveLimitTokens)
        val minKeepForChat = max(2048, (safeLimit * 0.25).toInt())
        val remaining = (safeLimit - reservedFixedTokens - minKeepForChat).coerceAtLeast(MIN_FILE_TURN_TOKENS)
        val shareCap = (safeLimit * MAX_FILE_WINDOW_SHARE).toInt()
        return minOf(remaining, shareCap, HARD_MAX_FILE_TURN_TOKENS).coerceAtLeast(MIN_FILE_TURN_TOKENS)
    }

    fun allocateFileBudgets(
        files: List<ChatAttachmentMeta>,
        totalBudgetTokens: Int,
    ): Map<String, Int> {
        if (files.isEmpty()) return emptyMap()
        val budget = max(MIN_FILE_TURN_TOKENS, totalBudgetTokens)
        val costs = files.associate { it.id to max(64, if (it.estimatedTokens > 0) it.estimatedTokens else 1000) }
        val totalRaw = costs.values.sum()
        if (totalRaw <= budget) {
            return costs.mapValues { it.value + 64 }
        }

        val allocated = LinkedHashMap<String, Int>()
        val remainingFiles = files.toMutableList()
        var leftover = budget

        // Small files get their full size first so one giant file never starves small companion files
        while (remainingFiles.isNotEmpty()) {
            val fairShare = leftover / remainingFiles.size
            val small = remainingFiles.filter { (costs[it.id] ?: 0) + 48 <= fairShare }
            if (small.isEmpty()) break
            for (f in small) {
                val need = (costs[f.id] ?: 0) + 48
                allocated[f.id] = need
                leftover = max(0, leftover - need)
                remainingFiles.remove(f)
            }
        }

        if (remainingFiles.isNotEmpty()) {
            val weightSum = remainingFiles.sumOf { (costs[it.id] ?: 1).toLong() }.coerceAtLeast(1L)
            for (f in remainingFiles) {
                val w = (costs[f.id] ?: 1).toLong()
                val share = max(400, ((leftover * w) / weightSum).toInt())
                allocated[f.id] = share
            }
        }
        return allocated
    }

    fun estimateDraftFilesInjectedTokens(
        files: List<ChatAttachmentMeta>,
        effectiveLimitTokens: Int,
        reservedFixedTokens: Int,
    ): Int {
        if (files.isEmpty()) return 0
        val budget = computeFileTurnBudget(effectiveLimitTokens, reservedFixedTokens)
        val rawSum = files.sumOf { max(32, it.estimatedTokens) + 48 }
        return min(rawSum, budget)
    }

    fun willCompressFile(
        meta: ChatAttachmentMeta,
        allDraftFiles: List<ChatAttachmentMeta>,
        effectiveLimitTokens: Int,
        reservedFixedTokens: Int = 2000,
    ): Boolean {
        if (!meta.isFile) return false
        val budget = computeFileTurnBudget(effectiveLimitTokens, reservedFixedTokens)
        val alloc = allocateFileBudgets(allDraftFiles.filter { it.isFile }, budget)
        val perFile = alloc[meta.id] ?: budget
        return meta.estimatedTokens > perFile
    }

    fun chunkDocument(
        text: String,
        fileName: String,
        targetChunkTokens: Int = DEFAULT_CHUNK_TOKENS,
    ): List<DocumentChunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lines = trimmed.split('\n')
        val chunks = ArrayList<DocumentChunk>()
        val buf = StringBuilder()
        var bufTokens = 0
        var chunkStartLine = 1
        var activeSection = ""
        var chunkIndex = 0

        fun flush(endLine: Int) {
            val content = buf.toString().trim()
            if (content.isEmpty()) return
            val label = buildString {
                if (activeSection.isNotBlank()) append("$activeSection · ")
                append("L$chunkStartLine-$endLine")
            }
            chunks.add(
                DocumentChunk(
                    index = chunkIndex++,
                    label = label,
                    text = content,
                    estimatedTokens = ContextBuilder.estimateTextTokens(content),
                ),
            )
            buf.clear()
            bufTokens = 0
        }

        for (i in lines.indices) {
            val lineNo = i + 1
            val line = lines[i]
            val lineTok = ContextBuilder.estimateTextTokens(line) + 1
            val isHeading = HEADING_RE.matches(line)

            if (isHeading && bufTokens >= targetChunkTokens / 3) {
                flush(lineNo - 1)
                chunkStartLine = lineNo
            }
            if (isHeading) {
                activeSection = line.trim().take(36)
            }

            // Handle an individual huge line (e.g., minified JSON) safely
            if (lineTok > targetChunkTokens * 2) {
                if (bufTokens > 0) {
                    flush(lineNo - 1)
                    chunkStartLine = lineNo
                }
                val charsPerChunk = max(400, (targetChunkTokens * 1.8).toInt())
                var pos = 0
                while (pos < line.length) {
                    val end = min(line.length, pos + charsPerChunk)
                    val piece = line.substring(pos, end)
                    chunks.add(
                        DocumentChunk(
                            index = chunkIndex++,
                            label = "L$lineNo (${pos / charsPerChunk + 1})",
                            text = piece,
                            estimatedTokens = ContextBuilder.estimateTextTokens(piece),
                        ),
                    )
                    pos = end
                }
                chunkStartLine = lineNo + 1
                continue
            }

            if (bufTokens + lineTok > targetChunkTokens && bufTokens > 0) {
                flush(lineNo - 1)
                chunkStartLine = lineNo
            }
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line)
            bufTokens += lineTok
        }
        flush(lines.size)
        return chunks
    }

    fun extractDocumentOutline(
        text: String,
        maxTokens: Int = 240,
    ): String {
        if (text.isBlank() || maxTokens <= 40) return ""
        val lines = text.lineSequence()
        val picked = ArrayList<String>()
        var used = 0
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.length > 140) continue
            if (HEADING_RE.matches(line) || CODE_DECL_RE.matches(line)) {
                val tok = ContextBuilder.estimateTextTokens(line) + 2
                if (used + tok > maxTokens) break
                picked.add("- $line")
                used += tok
                if (picked.size >= 24) break
            }
        }
        return picked.joinToString("\n")
    }

    fun fitFileToBudget(
        meta: ChatAttachmentMeta,
        fullText: String,
        queryText: String,
        budgetTokens: Int,
        modeOverride: FileInjectMode? = null,
    ): FittedFileResult {
        val cleanText = fullText.trim()
        val totalTokens = max(1, if (meta.estimatedTokens > 0) meta.estimatedTokens else ContextBuilder.estimateTextTokens(cleanText))
        val safeName = meta.fileName.ifBlank { "document.txt" }
        val ext = FileTextExtractor.extensionOf(safeName).ifBlank { "text" }
        val overheadReserve = 80
        val contentBudget = max(320, budgetTokens - overheadReserve)

        // Tier 1: Small file fits cleanly within budget -> full injection
        if (totalTokens <= contentBudget) {
            val block = buildString {
                appendLine("<attached_file name=\"$safeName\" type=\"$ext\" mode=\"full\" tokens=\"$totalTokens\">")
                appendLine(cleanText)
                append("</attached_file>")
            }
            val actualTok = ContextBuilder.estimateTextTokens(block)
            return FittedFileResult(
                formattedBlock = block,
                appliedMode = "full",
                injectedTokens = actualTok,
                totalTokens = totalTokens,
                selectedChunkCount = 1,
                totalChunkCount = 1,
            )
        }

        val effectiveMode = modeOverride ?: meta.injectMode
        val chunks = chunkDocument(cleanText, safeName)

        // User explicitly chose "尽可能全文" -> continuous head truncation within hard budget cap
        if (effectiveMode == FileInjectMode.full) {
            val truncatedText = truncateTextToTokenBudget(cleanText, contentBudget - 40)
            val omittedTokens = max(0, totalTokens - ContextBuilder.estimateTextTokens(truncatedText))
            val block = buildString {
                appendLine(
                    "<attached_file name=\"$safeName\" type=\"$ext\" mode=\"head_truncated\" " +
                        "injected_tokens=\"${ContextBuilder.estimateTextTokens(truncatedText)}\" total_tokens=\"$totalTokens\">",
                )
                appendLine(truncatedText)
                appendLine("[注：文件超出当前模型安全上限，已截断后续约 ${omittedTokens} tokens，可按章节/关键词继续追问。]")
                append("</attached_file>")
            }
            return FittedFileResult(
                formattedBlock = block,
                appliedMode = "head_truncated",
                injectedTokens = ContextBuilder.estimateTextTokens(block),
                totalTokens = totalTokens,
                selectedChunkCount = 1,
                totalChunkCount = max(1, chunks.size),
            )
        }

        val hasSpecificQuery = hasMeaningfulSpecificQuery(queryText)
        val useRag = when (effectiveMode) {
            FileInjectMode.retrieve -> true
            FileInjectMode.head_tail -> false
            else -> hasSpecificQuery
        }

        val outlineBudget = min(220, (contentBudget * 0.10).toInt())
        val outline = extractDocumentOutline(cleanText, outlineBudget)
        val outlineTok = if (outline.isNotBlank()) ContextBuilder.estimateTextTokens(outline) + 12 else 0
        val bodyBudget = max(240, contentBudget - outlineTok)

        val selectedChunks: List<DocumentChunk>
        val appliedModeLabel: String

        if (chunks.size <= 3) {
            val headText = truncateTextToTokenBudget(cleanText, bodyBudget)
            selectedChunks = listOf(DocumentChunk(0, "开头节选", headText, ContextBuilder.estimateTextTokens(headText)))
            appliedModeLabel = "head_tail"
        } else if (useRag) {
            appliedModeLabel = "smart_rag"
            val headBudget = (bodyBudget * 0.20).toInt().coerceAtLeast(180)
            val tailBudget = (bodyBudget * 0.15).toInt().coerceAtLeast(140)
            val ragBudget = max(200, bodyBudget - headBudget - tailBudget)

            val headChunks = pickPrefixChunks(chunks, headBudget)
            val headIds = headChunks.map { it.index }.toSet()
            val tailChunks = pickSuffixChunks(chunks.filter { it.index !in headIds }, tailBudget)
            val fixedIds = headIds + tailChunks.map { it.index }

            val middleCandidates = chunks.filter { it.index !in fixedIds }
            val ragPicked = if (middleCandidates.isNotEmpty()) {
                val query = CjkSearch.buildMemoryQuery(queryText, emptyList(), emptyList())
                val scored = CjkSearch.scoreMemoryCandidates(
                    candidates = middleCandidates.map { ch ->
                        MemorySearchCandidate(
                            id = ch.index.toString(),
                            title = ch.label,
                            content = ch.text,
                            estimatedTokens = ch.estimatedTokens,
                        )
                    },
                    query = query,
                    options = ScoreMemoryOptions(activeTipSeq = chunks.size, maxTokens = ragBudget),
                )
                val byIdx = middleCandidates.associateBy { it.index }
                val matched = scored.selected.mapNotNull { byIdx[it.item.id.toIntOrNull()] }
                if (matched.isNotEmpty()) {
                    matched
                } else {
                    pickUniformSample(middleCandidates, ragBudget)
                }
            } else {
                emptyList()
            }

            selectedChunks = (headChunks + ragPicked + tailChunks)
                .distinctBy { it.index }
                .sortedBy { it.index }
        } else {
            // Head + uniform middle sampling + tail for whole-document summaries
            appliedModeLabel = "head_tail"
            val headBudget = (bodyBudget * 0.30).toInt().coerceAtLeast(180)
            val tailBudget = (bodyBudget * 0.20).toInt().coerceAtLeast(140)
            val midBudget = max(120, bodyBudget - headBudget - tailBudget)

            val headChunks = pickPrefixChunks(chunks, headBudget)
            val headIds = headChunks.map { it.index }.toSet()
            val tailChunks = pickSuffixChunks(chunks.filter { it.index !in headIds }, tailBudget)
            val fixedIds = headIds + tailChunks.map { it.index }
            val midSample = pickUniformSample(chunks.filter { it.index !in fixedIds }, midBudget)

            selectedChunks = (headChunks + midSample + tailChunks)
                .distinctBy { it.index }
                .sortedBy { it.index }
        }

        val sb = StringBuilder()
        var usedContentTok = outlineTok
        val renderedParts = ArrayList<String>()
        for (ch in selectedChunks) {
            val part = "--- [${ch.label}] ---\n${ch.text}"
            val pTok = ContextBuilder.estimateTextTokens(part) + 4
            if (usedContentTok + pTok > contentBudget && renderedParts.isNotEmpty()) break
            if (usedContentTok + pTok > contentBudget) {
                val clipped = truncateTextToTokenBudget(ch.text, max(120, contentBudget - usedContentTok - 12))
                renderedParts.add("--- [${ch.label}] ---\n$clipped")
                usedContentTok += ContextBuilder.estimateTextTokens(clipped) + 12
                break
            }
            renderedParts.add(part)
            usedContentTok += pTok
        }

        val coveragePct = ((usedContentTok.toDouble() / max(1, totalTokens)) * 100.0).roundToInt().coerceIn(1, 99)
        sb.appendLine(
            "<attached_file name=\"$safeName\" type=\"$ext\" mode=\"$appliedModeLabel\" " +
                "injected_tokens=\"$usedContentTok\" total_tokens=\"$totalTokens\" coverage=\"${coveragePct}%\">",
        )
        sb.appendLine("[系统说明：该文件原文约 $totalTokens tokens，为避免撑爆上下文已自动智能节选（覆盖约 $coveragePct%）。如需定位其他章节/页码请告知用户继续追问。]")
        if (outline.isNotBlank()) {
            sb.appendLine("[文档结构大纲]")
            sb.appendLine(outline)
            sb.appendLine()
        }
        sb.appendLine(renderedParts.joinToString("\n\n"))
        sb.append("</attached_file>")

        val finalBlock = sb.toString()
        return FittedFileResult(
            formattedBlock = finalBlock,
            appliedMode = appliedModeLabel,
            injectedTokens = ContextBuilder.estimateTextTokens(finalBlock),
            totalTokens = totalTokens,
            selectedChunkCount = renderedParts.size,
            totalChunkCount = max(1, chunks.size),
        )
    }

    fun buildDecayedFileStub(meta: ChatAttachmentMeta): String {
        val name = meta.fileName.ifBlank { "document" }
        val tokText = formatShortTokens(meta.estimatedTokens)
        val preview = meta.previewSnippet.take(80).trim()
        return buildString {
            append("[历史文件已折叠以节省上下文：$name · 约 $tokText")
            if (meta.pageCount > 0) append(" · ${meta.pageCount} 页")
            if (preview.isNotEmpty()) append(" · 摘要：$preview")
            append("]")
        }
    }

    fun messageMentionsFile(queryText: String, meta: ChatAttachmentMeta): Boolean {
        val q = queryText.trim().lowercase()
        if (q.isEmpty()) return false
        val name = meta.fileName.trim().lowercase()
        if (name.isEmpty()) return false
        if (q.contains(name)) return true
        val stem = name.substringBeforeLast('.')
        if (stem.length >= 2 && q.contains(stem)) return true
        return false
    }

    fun formatShortTokens(tokens: Int): String = when {
        tokens >= 1000 -> "${String.format(java.util.Locale.US, "%.1f", tokens / 1000.0)}K tok"
        else -> "$tokens tok"
    }

    private fun hasMeaningfulSpecificQuery(queryText: String): Boolean {
        val clean = queryText.trim()
        if (clean.length < 4) return false
        val stripped = GENERIC_SUMMARY_RE.replace(clean, "").replace(Regex("[\\p{Punct}\\s，。？！、；：]+"), "")
        return stripped.length >= 3
    }

    private fun pickPrefixChunks(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
        val out = ArrayList<DocumentChunk>()
        var used = 0
        for (ch in chunks) {
            if (out.isNotEmpty() && used + ch.estimatedTokens > budgetTokens) break
            out.add(ch)
            used += ch.estimatedTokens
        }
        return out
    }

    private fun pickSuffixChunks(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
        val out = ArrayList<DocumentChunk>()
        var used = 0
        for (i in chunks.indices.reversed()) {
            val ch = chunks[i]
            if (out.isNotEmpty() && used + ch.estimatedTokens > budgetTokens) break
            out.add(0, ch)
            used += ch.estimatedTokens
        }
        return out
    }

    private fun pickUniformSample(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
        if (chunks.isEmpty() || budgetTokens <= 0) return emptyList()
        val avgTok = max(80, chunks.sumOf { it.estimatedTokens } / chunks.size)
        val desiredCount = max(1, min(chunks.size, budgetTokens / avgTok))
        val out = ArrayList<DocumentChunk>(desiredCount)
        var used = 0
        for (i in 0 until desiredCount) {
            val pos = if (desiredCount == 1) {
                chunks.size / 2
            } else {
                ((i.toDouble() / (desiredCount - 1)) * (chunks.size - 1)).roundToInt().coerceIn(0, chunks.lastIndex)
            }
            val ch = chunks[pos]
            if (out.any { it.index == ch.index }) continue
            if (out.isNotEmpty() && used + ch.estimatedTokens > budgetTokens) break
            out.add(ch)
            used += ch.estimatedTokens
        }
        return out
    }

    private fun truncateTextToTokenBudget(text: String, maxTokens: Int): String {
        val target = max(60, maxTokens)
        if (ContextBuilder.estimateTextTokens(text) <= target) return text
        // Binary search exact prefix fitting target token estimate
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (ContextBuilder.estimateTextTokens(text.substring(0, mid)) <= target) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        val cut = lo.coerceAtLeast(1)
        val snapNewline = text.lastIndexOf('\n', cut).takeIf { it > cut * 0.85 }
        return text.substring(0, snapNewline ?: cut).trimEnd()
    }
}
