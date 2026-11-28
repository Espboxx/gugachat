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

data class IndexedDocument(
    val attachmentId: String,
    val fileName: String,
    val outline: String,
    val chunks: List<DocumentChunk>,
    val totalTokens: Int,
)

enum class FileRetentionTier {
    FULL,
    INDEX_RECALL,
}

data class FileRecallBundle(
    val recalledBlock: String = "",
    val recalledTokens: Int = 0,
    val recalledItems: List<RecalledMemoryInspectItem> = emptyList(),
    val recalledChunkTexts: Map<String, String> = emptyMap(),
    val budgetTokens: Int = 0,
    val droppedCount: Int = 0,
    val retrievalModeUsed: String = "lexical",
    val tier2FileIds: Set<String> = emptySet(),
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
    const val DEFAULT_CHUNK_OVERLAP_RATIO = 0.15
    const val MAX_ADAPTIVE_CHUNKS = 600
    const val ADAPTIVE_LARGE_DOC_TOKENS = 100_000
    const val FULL_INLINE_CAP_TOKENS = 24_000
    const val FULL_INLINE_MIN_TOKENS = 4_000
    const val FULL_INLINE_WINDOW_SHARE = 0.25
    const val DECAYED_STUB_TOKENS = 72
    const val INDEX_ANCHOR_ESTIMATED_TOKENS = 240
    const val FILE_CHUNK_SOURCE = "file_chunk"

    private val PRIMARY_HEADING_RE = Regex(
        "^\\s*(#{1,4}\\s+.+|\\[第\\s*\\d+\\s*页\\]|第[一二三四五六七八九十百千零〇\\d]+[章节回篇卷幕天日部节场].*)$",
    )
    private val CN_ENUM_HEADING_RE = Regex("^\\s*[一二三四五六七八九十百]+[、.．]\\s*\\S.*$")
    private val BRACKET_HEADING_RE = Regex("^\\s*【[^】]{1,28}】.*$")
    private val ENGLISH_SECTION_HEADING_RE = Regex(
        "^\\s*(?:Day|Part|Phase|Act|Chapter|Section|Episode)\\s*\\d+(?:[\\s:：.\\-—].*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val NUMERIC_SECTION_HEADING_RE = Regex("^\\s*\\d+(?:\\.\\d+){0,2}[、.．]\\s*\\S.*$")
    private val CODE_DECL_RE = Regex(
        "^\\s*(public|private|internal|protected|export|async|override|suspend|data|sealed)?\\s*(class|interface|object|enum|struct|trait|fun|def|function|func|const\\s+\\w+\\s*=)\\s+[\\w$<>]+.*",
    )
    private val GENERIC_SUMMARY_RE = Regex(
        "(请|帮我|帮忙|麻烦|一下|一下子|这个|这份|这篇|该|总结|概括|梳理|提炼|分析|看看|读一下|看下|读下|主要|核心|整体|大概|全文|文档|文件|资料|附件|pdf|word|docx|内容|要点|大纲|讲了什么|说了什么|写了什么|是什么|开始|开局|设定|please|summarize|summary|overview|explain|review|read|analyze|this|the|attached|file|document|doc|me|for|of|about|in|start|begin)",
        RegexOption.IGNORE_CASE,
    )
    private val EXPLICIT_SUMMARY_RE = Regex(
        "(总结|概括|梳理全文|提炼要点|全文大纲|主要内容|讲了什么|说了什么|写了什么|overview|summarize|summary|tl;?dr)",
        RegexOption.IGNORE_CASE,
    )
    private val PUNCT_STRIP_RE = Regex("[\\p{Punct}\\s，。？！、；：“”‘’（）()【】\\[\\]]+")

    fun isHeadingLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > 140) return false
        if (PRIMARY_HEADING_RE.matches(trimmed)) return true
        if (trimmed.length <= 64 && ENGLISH_SECTION_HEADING_RE.matches(trimmed)) return true
        if (trimmed.length <= 48 && CN_ENUM_HEADING_RE.matches(trimmed)) return true
        if (trimmed.length <= 48 && BRACKET_HEADING_RE.matches(trimmed)) return true
        if (trimmed.length <= 44 && NUMERIC_SECTION_HEADING_RE.matches(trimmed) && !trimmed.endsWith("，") && !trimmed.endsWith(",")) {
            return true
        }
        return false
    }

    fun chunkOwnerId(attachmentId: String, chunkIndex: Int): String = "fc:$attachmentId:$chunkIndex"

    fun formatContextualChunkText(fileName: String, chunk: DocumentChunk): String {
        val safeName = fileName.ifBlank { "document.txt" }
        return "[$safeName | ${chunk.label}]\n${chunk.text}"
    }

    fun computeFullInlineThreshold(effectiveLimitTokens: Int): Int {
        val safeLimit = max(4096, effectiveLimitTokens)
        return min(FULL_INLINE_CAP_TOKENS, max(FULL_INLINE_MIN_TOKENS, (safeLimit * FULL_INLINE_WINDOW_SHARE).toInt()))
    }

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

    fun computeFileRecallBudget(
        remainingAfterSystem: Int,
        memoryBudget: MemoryBudget,
        isUploadTurn: Boolean = false,
    ): Int {
        val safeRemaining = max(0, remainingAfterSystem)
        if (safeRemaining < 1_000) return 0
        val base = (safeRemaining * 0.20).toInt().coerceIn(2_400, 12_000)
        val mult = when (memoryBudget) {
            MemoryBudget.low -> 0.70
            MemoryBudget.standard -> 1.00
            MemoryBudget.high -> 1.40
        }
        val scaled = (base * mult).roundToInt()
        val withUpload = if (isUploadTurn) {
            max(scaled, (safeRemaining * 0.28).toInt().coerceIn(4_000, 20_000))
        } else {
            scaled
        }
        val cap = if (isUploadTurn) 20_000 else 16_000
        val windowGuard = max(1_200, (safeRemaining * 0.35).toInt())
        return withUpload.coerceIn(1_200, min(cap, windowGuard))
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
            return costs.mapValues { min(budget, it.value + 96) }
        }

        val allocated = LinkedHashMap<String, Int>()
        val remainingFiles = files.toMutableList()
        var leftover = budget

        // Small files get their full size first so one giant file never starves small companion files
        while (remainingFiles.isNotEmpty()) {
            val fairShare = leftover / remainingFiles.size
            val small = remainingFiles.filter { (costs[it.id] ?: 0) + 96 <= fairShare }
            if (small.isEmpty()) break
            for (f in small) {
                val need = (costs[f.id] ?: 0) + 96
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
        val fullThreshold = computeFullInlineThreshold(effectiveLimitTokens)
        val turnBudget = computeFileTurnBudget(effectiveLimitTokens, reservedFixedTokens)
        val tier1Sum = files.sumOf { meta ->
            val est = max(32, meta.estimatedTokens) + 48
            when (meta.injectMode) {
                FileInjectMode.full -> min(est, turnBudget)
                FileInjectMode.retrieve, FileInjectMode.head_tail -> INDEX_ANCHOR_ESTIMATED_TOKENS
                FileInjectMode.auto -> if (meta.estimatedTokens <= fullThreshold) est else INDEX_ANCHOR_ESTIMATED_TOKENS
            }
        }
        return min(tier1Sum, turnBudget)
    }

    fun willCompressFile(
        meta: ChatAttachmentMeta,
        allDraftFiles: List<ChatAttachmentMeta>,
        effectiveLimitTokens: Int,
        reservedFixedTokens: Int = 2000,
    ): Boolean {
        if (!meta.isFile) return false
        return when (meta.injectMode) {
            FileInjectMode.retrieve, FileInjectMode.head_tail -> true
            FileInjectMode.full -> {
                val budget = computeFileTurnBudget(effectiveLimitTokens, reservedFixedTokens)
                val alloc = allocateFileBudgets(allDraftFiles.filter { it.isFile }, budget)
                val perFile = alloc[meta.id] ?: budget
                meta.estimatedTokens > perFile
            }
            FileInjectMode.auto -> {
                val fullThreshold = computeFullInlineThreshold(effectiveLimitTokens)
                if (meta.estimatedTokens > fullThreshold) return true
                val budget = computeFileTurnBudget(effectiveLimitTokens, reservedFixedTokens)
                val alloc = allocateFileBudgets(allDraftFiles.filter { it.isFile }, budget)
                val perFile = alloc[meta.id] ?: budget
                meta.estimatedTokens > perFile
            }
        }
    }

    fun buildIndexedDocument(
        attachmentId: String,
        fileName: String,
        text: String,
    ): IndexedDocument {
        val clean = text.trim()
        val totalTokens = ContextBuilder.estimateTextTokens(clean)
        val outline = extractDocumentOutline(clean, maxTokens = 260)
        val chunks = chunkDocument(clean, fileName)
        return IndexedDocument(
            attachmentId = attachmentId,
            fileName = fileName.ifBlank { "document.txt" },
            outline = outline,
            chunks = chunks,
            totalTokens = totalTokens,
        )
    }

    fun chunkDocument(
        text: String,
        fileName: String,
        targetChunkTokens: Int = DEFAULT_CHUNK_TOKENS,
    ): List<DocumentChunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val docTokens = ContextBuilder.estimateTextTokens(trimmed)
        val baseTarget = targetChunkTokens.coerceAtLeast(80)
        val effectiveTarget = if (docTokens > ADAPTIVE_LARGE_DOC_TOKENS || docTokens / baseTarget > MAX_ADAPTIVE_CHUNKS) {
            max(baseTarget, ceil(docTokens.toDouble() / (MAX_ADAPTIVE_CHUNKS * 0.85)).toInt()).coerceAtLeast(600)
        } else {
            baseTarget
        }
        val overlapTokens = (effectiveTarget * DEFAULT_CHUNK_OVERLAP_RATIO)
            .roundToInt()
            .coerceIn(32, max(32, effectiveTarget / 3))

        val lines = trimmed.split('\n')
        val chunks = ArrayList<DocumentChunk>()
        val bufLines = ArrayList<Pair<Int, String>>() // (lineNo, lineText)
        val bufLineToks = ArrayList<Int>()
        var bufTokens = 0
        var activeSection = ""
        var chunkIndex = 0

        fun buildLabel(section: String, startLine: Int, endLine: Int): String = buildString {
            if (section.isNotBlank()) append("$section · ")
            append("L$startLine-$endLine")
        }

        fun flush(carryOverlap: Boolean) {
            if (bufLines.isEmpty()) return
            val content = bufLines.joinToString("\n") { it.second }.trim()
            val startLine = bufLines.first().first
            val endLine = bufLines.last().first
            if (content.isNotEmpty()) {
                chunks.add(
                    DocumentChunk(
                        index = chunkIndex++,
                        label = buildLabel(activeSection, startLine, endLine),
                        text = content,
                        estimatedTokens = ContextBuilder.estimateTextTokens(content),
                    ),
                )
            }
            if (carryOverlap && bufLines.size > 1 && overlapTokens > 0) {
                val carryLines = ArrayList<Pair<Int, String>>()
                val carryToks = ArrayList<Int>()
                var carrySum = 0
                // Keep trailing lines up to overlapTokens, always dropping at least the first line
                for (k in bufLines.lastIndex downTo 1) {
                    val lt = bufLineToks[k]
                    if (carryLines.isNotEmpty() && carrySum + lt > overlapTokens) break
                    carryLines.add(0, bufLines[k])
                    carryToks.add(0, lt)
                    carrySum += lt
                    if (carrySum >= overlapTokens) break
                }
                bufLines.clear()
                bufLineToks.clear()
                bufLines.addAll(carryLines)
                bufLineToks.addAll(carryToks)
                bufTokens = carrySum
            } else {
                bufLines.clear()
                bufLineToks.clear()
                bufTokens = 0
            }
        }

        for (i in lines.indices) {
            val lineNo = i + 1
            val line = lines[i]
            val lineTok = ContextBuilder.estimateTextTokens(line) + 1
            val isHeading = isHeadingLine(line)

            if (isHeading && bufTokens >= effectiveTarget / 3) {
                flush(carryOverlap = false)
            }
            if (isHeading) {
                activeSection = line.trim().take(36)
            }

            // Handle an individual huge line (e.g., minified JSON) with 15% char overlap
            if (lineTok > effectiveTarget * 2) {
                if (bufLines.isNotEmpty()) {
                    flush(carryOverlap = false)
                }
                val charsPerChunk = max(400, (effectiveTarget * 1.8).toInt())
                val overlapChars = (charsPerChunk * DEFAULT_CHUNK_OVERLAP_RATIO)
                    .toInt()
                    .coerceIn(40, charsPerChunk / 3)
                val step = max(120, charsPerChunk - overlapChars)
                var pos = 0
                var partNo = 1
                while (pos < line.length) {
                    val end = min(line.length, pos + charsPerChunk)
                    val piece = line.substring(pos, end)
                    chunks.add(
                        DocumentChunk(
                            index = chunkIndex++,
                            label = buildString {
                                if (activeSection.isNotBlank()) append("$activeSection · ")
                                append("L$lineNo ($partNo)")
                            },
                            text = piece,
                            estimatedTokens = ContextBuilder.estimateTextTokens(piece),
                        ),
                    )
                    if (end >= line.length) break
                    pos += step
                    partNo++
                }
                continue
            }

            if (bufTokens + lineTok > effectiveTarget && bufLines.isNotEmpty()) {
                flush(carryOverlap = true)
            }
            bufLines.add(lineNo to line)
            bufLineToks.add(lineTok)
            bufTokens += lineTok
        }
        flush(carryOverlap = false)

        return enforceAdaptiveChunkCap(chunks, MAX_ADAPTIVE_CHUNKS)
    }

    private fun enforceAdaptiveChunkCap(
        rawChunks: List<DocumentChunk>,
        maxChunks: Int,
    ): List<DocumentChunk> {
        if (rawChunks.size <= maxChunks) return rawChunks
        var current = rawChunks
        while (current.size > maxChunks) {
            val merged = ArrayList<DocumentChunk>((current.size + 1) / 2)
            var idx = 0
            var newIndex = 0
            while (idx < current.size) {
                val first = current[idx]
                val second = current.getOrNull(idx + 1)
                if (second == null) {
                    merged.add(first.copy(index = newIndex++))
                } else {
                    val combinedText = "${first.text}\n${second.text}"
                    merged.add(
                        DocumentChunk(
                            index = newIndex++,
                            label = first.label,
                            text = combinedText,
                            estimatedTokens = ContextBuilder.estimateTextTokens(combinedText),
                        ),
                    )
                }
                idx += 2
            }
            current = merged
        }
        return current
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
            if (isHeadingLine(line) || CODE_DECL_RE.matches(line)) {
                val tok = ContextBuilder.estimateTextTokens(line) + 2
                if (used + tok > maxTokens) break
                picked.add("- $line")
                used += tok
                if (picked.size >= 24) break
            }
        }
        return picked.joinToString("\n")
    }

    /**
     * Renders an immutable, prompt-cache-safe document index anchor placed in the original upload
     * turn (`u_orig`) for Tier-2 files. Never mutates across turns so KV cache prefixes remain hot.
     */
    fun buildDocumentIndexAnchor(
        meta: ChatAttachmentMeta,
        outline: String = "",
        chunkCount: Int = 0,
    ): String {
        val safeName = meta.fileName.ifBlank { "document.txt" }
        val ext = FileTextExtractor.extensionOf(safeName).ifBlank { "text" }
        val totalTokens = max(1, meta.estimatedTokens)
        val cleanOutline = outline.trim()
        val preview = meta.previewSnippet.replace(Regex("\\s+"), " ").trim().take(120)
        return buildString {
            append("<attached_file_index name=\"$safeName\" type=\"$ext\" tokens=\"$totalTokens\"")
            if (meta.pageCount > 0) append(" pages=\"${meta.pageCount}\"")
            if (chunkCount > 0) append(" chunks=\"$chunkCount\"")
            appendLine(">")
            if (cleanOutline.isNotEmpty()) {
                appendLine("[文档结构目录]")
                appendLine(cleanOutline)
            } else if (preview.isNotEmpty()) {
                appendLine("[开头摘要] $preview")
            }
            appendLine("[系统说明：以上为该文档的固定目录锚点。每轮对话会自动按当前话题动态召回最相关的原文片段至最新轮次；请严格依据已提供的目录与召回片段作答，严禁编造未出现的角色、数值或剧情细节。]")
            append("</attached_file_index>")
        }
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

        // User explicitly chose "固定全文" -> continuous head truncation within hard budget cap
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
                            title = "$safeName · ${ch.label}",
                            content = ch.text,
                            estimatedTokens = ch.estimatedTokens,
                        )
                    },
                    query = query,
                    options = ScoreMemoryOptions(
                        activeTipSeq = chunks.size,
                        maxTokens = ragBudget,
                        minCleanTermsOverride = 1,
                        minNormBm25Override = 0.12,
                    ),
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

    internal fun hasMeaningfulSpecificQuery(queryText: String): Boolean {
        val clean = queryText.trim()
        if (clean.length < 2) return false
        val stripped = PUNCT_STRIP_RE.replace(GENERIC_SUMMARY_RE.replace(clean, ""), "")
        return stripped.length >= 2
    }

    internal fun isExplicitSummaryIntent(queryText: String): Boolean {
        val clean = queryText.trim()
        if (clean.isEmpty()) return false
        return EXPLICIT_SUMMARY_RE.containsMatchIn(clean) && !hasMeaningfulSpecificQuery(clean)
    }

    internal fun pickPrefixChunks(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
        val out = ArrayList<DocumentChunk>()
        var used = 0
        for (ch in chunks) {
            if (out.isNotEmpty() && used + ch.estimatedTokens > budgetTokens) break
            out.add(ch)
            used += ch.estimatedTokens
        }
        return out
    }

    internal fun pickSuffixChunks(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
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

    internal fun pickUniformSample(chunks: List<DocumentChunk>, budgetTokens: Int): List<DocumentChunk> {
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

    internal fun pickCoverageSample(
        chunks: List<DocumentChunk>,
        budgetTokens: Int,
        mutedOwnerIds: Set<String>,
        attachmentId: String,
    ): List<DocumentChunk> {
        val available = chunks.filter { FileContextBudgeter.chunkOwnerId(attachmentId, it.index) !in mutedOwnerIds }
        if (available.isEmpty() || budgetTokens <= 0) return emptyList()
        if (available.size <= 3) {
            return pickPrefixChunks(available, budgetTokens)
        }
        val headBudget = (budgetTokens * 0.35).toInt().coerceAtLeast(180)
        val tailBudget = (budgetTokens * 0.25).toInt().coerceAtLeast(140)
        val midBudget = max(120, budgetTokens - headBudget - tailBudget)
        val head = pickPrefixChunks(available, headBudget)
        val headIds = head.map { it.index }.toSet()
        val tail = pickSuffixChunks(available.filter { it.index !in headIds }, tailBudget)
        val fixedIds = headIds + tail.map { it.index }
        val mid = pickUniformSample(available.filter { it.index !in fixedIds }, midBudget)
        return (head + mid + tail).distinctBy { it.index }.sortedBy { it.index }
    }

    internal fun truncateTextToTokenBudget(text: String, maxTokens: Int): String {
        val target = max(60, maxTokens)
        if (ContextBuilder.estimateTextTokens(text) <= target) return text
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

/**
 * Multi-turn hybrid chunk recall engine for Tier-2 attached files.
 * Combines multi-turn conversational queries (scanDepth = 3 + RP/Novel scene context),
 * CJK BM25F (with single-proper-noun relaxation), vector cosine scores + Thresholded RRF,
 * neighbor expansion (`i + 1`), cross-file budget allocation, and document-order assembly.
 */
object FileRecallEngine {
    private const val FILE_RECALL_SCAN_DEPTH = 3
    private const val FILE_RECALL_MAX_QUERY_CHARS = 1800
    private const val FILE_RECALL_MAX_RESULTS = 16
    private const val MAX_PREVIEW_CHARS = 1200

    private data class SelectedChunkEntry(
        val doc: IndexedDocument,
        val chunk: DocumentChunk,
        val ownerId: String,
        val score: Double?,
        val matchedTerms: List<String>,
        val triggerKind: String,
    )

    fun recall(
        tier2Docs: List<IndexedDocument>,
        fileMetasById: Map<String, ChatAttachmentMeta>,
        uploadTurnFileIds: Set<String>,
        conversation: ChatConversation,
        recentMessages: List<ChatMessage>,
        latestUserQuery: String,
        activeChapter: NovelChapter? = null,
        budgetTokens: Int,
        semanticScores: Map<String, Double>? = null,
        semanticScorer: SemanticScorer? = null,
        minSemanticCosine: Double = 0.50,
        mutedRecallKeys: Set<String> = emptySet(),
        modelId: String? = null,
    ): FileRecallBundle {
        val validDocs = tier2Docs.filter { it.chunks.isNotEmpty() }
        val tier2Ids = validDocs.map { it.attachmentId }.toSet()
        if (validDocs.isEmpty() || budgetTokens < 240) {
            return FileRecallBundle(budgetTokens = max(0, budgetTokens), tier2FileIds = tier2Ids)
        }

        val mutedChunkIds = ContextBudgetPolicy.mutedIdsForSource(mutedRecallKeys, FileContextBudgeter.FILE_CHUNK_SOURCE)
        val isNovel = conversation.mode == ConversationMode.novel && conversation.novel != null
        val isRp = conversation.mode == ConversationMode.rp && conversation.rp != null

        val presentNames = when {
            isNovel -> conversation.novel?.currentStatus?.present.orEmpty()
            isRp -> conversation.rp?.cast?.filter { it.inScene }?.map { it.name }.orEmpty()
            else -> emptyList()
        }
        val querySeed = buildList {
            if (latestUserQuery.isNotBlank()) add(latestUserQuery.trim())
            if (isNovel) {
                activeChapter?.outline?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                conversation.novel?.currentStatus?.location?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            if (isRp) {
                conversation.rp?.state?.scene?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                conversation.rp?.state?.chapterTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }.joinToString("\n")

        val query = CjkSearch.buildMemoryQuery(
            draftText = querySeed,
            recentMessages = recentMessages,
            presentEntityNames = presentNames,
            scanDepth = FILE_RECALL_SCAN_DEPTH,
            maxQueryChars = FILE_RECALL_MAX_QUERY_CHARS,
        )

        val hasSpecificQuery = FileContextBudgeter.hasMeaningfulSpecificQuery(latestUserQuery)
        val isExplicitSummary = FileContextBudgeter.isExplicitSummaryIntent(latestUserQuery)

        val coverageDocs = ArrayList<IndexedDocument>()
        val searchDocs = ArrayList<IndexedDocument>()
        for (doc in validDocs) {
            val meta = fileMetasById[doc.attachmentId]
            val mode = meta?.injectMode ?: FileInjectMode.auto
            val isUploadTurn = doc.attachmentId in uploadTurnFileIds
            val forceCoverage = mode == FileInjectMode.head_tail ||
                isExplicitSummary ||
                (isUploadTurn && !hasSpecificQuery && mode != FileInjectMode.retrieve)
            if (forceCoverage) {
                coverageDocs.add(doc)
            } else {
                searchDocs.add(doc)
            }
        }

        val selectedByOwnerId = LinkedHashMap<String, SelectedChunkEntry>()
        var usedTokens = 0
        var droppedCount = 0
        var retrievalModeUsed = "lexical"

        // 1) Coverage fallback for head_tail / summary / generic upload turns
        if (coverageDocs.isNotEmpty()) {
            val coverageShare = if (searchDocs.isEmpty()) {
                budgetTokens
            } else {
                (budgetTokens * coverageDocs.size) / (coverageDocs.size + searchDocs.size)
            }.coerceAtLeast(360)
            val perDocBudget = max(320, coverageShare / coverageDocs.size)
            for (doc in coverageDocs) {
                val sampled = FileContextBudgeter.pickCoverageSample(
                    chunks = doc.chunks,
                    budgetTokens = perDocBudget,
                    mutedOwnerIds = mutedChunkIds,
                    attachmentId = doc.attachmentId,
                )
                for (ch in sampled) {
                    val ownerId = FileContextBudgeter.chunkOwnerId(doc.attachmentId, ch.index)
                    val tok = max(16, ch.estimatedTokens) + 18
                    if (usedTokens + tok > budgetTokens && selectedByOwnerId.isNotEmpty()) {
                        droppedCount++
                        continue
                    }
                    selectedByOwnerId[ownerId] = SelectedChunkEntry(
                        doc = doc,
                        chunk = ch,
                        ownerId = ownerId,
                        score = null,
                        matchedTerms = emptyList(),
                        triggerKind = "summary_sample",
                    )
                    usedTokens += tok
                }
            }
        }

        // 2) Cross-file hybrid scoring (CJK BM25F + Vector Cosine + Thresholded RRF)
        val remainingBudget = max(0, budgetTokens - usedTokens)
        if (searchDocs.isNotEmpty() && remainingBudget >= 180) {
            val chunkLookup = HashMap<String, Pair<IndexedDocument, DocumentChunk>>()
            val candidates = ArrayList<MemorySearchCandidate>()

            for (doc in searchDocs) {
                for (ch in doc.chunks) {
                    val ownerId = FileContextBudgeter.chunkOwnerId(doc.attachmentId, ch.index)
                    chunkLookup[ownerId] = doc to ch
                    candidates.add(
                        MemorySearchCandidate(
                            id = ownerId,
                            title = "${doc.fileName} · ${ch.label}",
                            content = ch.text,
                            estimatedTokens = ch.estimatedTokens,
                            enabled = true,
                        ),
                    )
                }
            }

            // Reserve ~20% headroom for neighbor expansion (i + 1) when budget is roomy
            val primaryBudget = if (remainingBudget >= 1_800) {
                (remainingBudget * 0.80).toInt().coerceAtLeast(800)
            } else {
                remainingBudget
            }

            val scoreResult = CjkSearch.scoreMemoryCandidates(
                candidates = candidates,
                query = query,
                options = ScoreMemoryOptions(
                    activeTipSeq = Int.MAX_VALUE,
                    maxTokens = primaryBudget,
                    semanticScores = semanticScores,
                    semanticScorer = if (semanticScores == null) semanticScorer else null,
                    minSemanticCosine = minSemanticCosine,
                    maxResults = FILE_RECALL_MAX_RESULTS,
                    modelId = modelId,
                    mutedIds = mutedChunkIds,
                    allowPinnedBypass = false,
                    minCleanTermsOverride = 1,
                    minNormBm25Override = 0.12,
                ),
            )
            droppedCount += scoreResult.droppedCount
            if (scoreResult.retrievalModeUsed == "hybrid") {
                retrievalModeUsed = "hybrid"
            }

            // Diversity floor across multiple active files when multiple files have passing hits
            val primaryPicked = ArrayList<ScoredMemoryCandidate>(scoreResult.selected)
            if (searchDocs.size > 1 && primaryPicked.isNotEmpty()) {
                val representedDocs = primaryPicked.mapNotNull { chunkLookup[it.item.id]?.first?.attachmentId }.toHashSet()
                val passingByDoc = scoreResult.scored
                    .filter { s ->
                        s.item.id !in mutedChunkIds &&
                            (s.cleanMatchedTermCount >= 1 || (s.semanticScore ?: 0.0) >= minSemanticCosine) &&
                            s.score > 0.0
                    }
                    .groupBy { chunkLookup[it.item.id]?.first?.attachmentId }
                for ((docId, hits) in passingByDoc) {
                    if (docId == null || docId in representedDocs) continue
                    val topHit = hits.maxByOrNull { it.score } ?: continue
                    val tok = max(16, topHit.estimatedTokens) + 18
                    if (usedTokens + primaryPicked.sumOf { max(16, it.estimatedTokens) + 18 } + tok <= remainingBudget) {
                        primaryPicked.add(topHit)
                        representedDocs.add(docId)
                    }
                }
            }

            val topHitsForNeighbor = ArrayList<Pair<IndexedDocument, DocumentChunk>>()
            for (s in primaryPicked) {
                val (doc, ch) = chunkLookup[s.item.id] ?: continue
                if (selectedByOwnerId.containsKey(s.item.id)) continue
                val tok = max(16, ch.estimatedTokens) + 18
                if (usedTokens + tok > budgetTokens && selectedByOwnerId.isNotEmpty()) {
                    droppedCount++
                    continue
                }
                selectedByOwnerId[s.item.id] = SelectedChunkEntry(
                    doc = doc,
                    chunk = ch,
                    ownerId = s.item.id,
                    score = (s.score * 1000.0).roundToInt() / 1000.0,
                    matchedTerms = s.matchedTerms,
                    triggerKind = s.triggerKind,
                )
                usedTokens += tok
                topHitsForNeighbor.add(doc to ch)
            }

            // 3) Neighbor expansion (i + 1) for top matched chunks to prevent heading/body splits
            for ((doc, ch) in topHitsForNeighbor.take(4)) {
                val nextChunk = doc.chunks.getOrNull(ch.index + 1) ?: continue
                val nextOwnerId = FileContextBudgeter.chunkOwnerId(doc.attachmentId, nextChunk.index)
                if (nextOwnerId in mutedChunkIds || selectedByOwnerId.containsKey(nextOwnerId)) continue
                val tok = max(16, nextChunk.estimatedTokens) + 18
                if (usedTokens + tok > budgetTokens) continue
                selectedByOwnerId[nextOwnerId] = SelectedChunkEntry(
                    doc = doc,
                    chunk = nextChunk,
                    ownerId = nextOwnerId,
                    score = null,
                    matchedTerms = emptyList(),
                    triggerKind = "neighbor",
                )
                usedTokens += tok
            }

            // 4) Upload-turn safety net: if a file was uploaded on this turn (even in retrieve mode)
            //    and produced 0 hits, provide head+sample+tail so the model isn't blind on Turn 1.
            val representedAfterSearch = selectedByOwnerId.values.map { it.doc.attachmentId }.toSet()
            val missedUploadDocs = searchDocs.filter { it.attachmentId in uploadTurnFileIds && it.attachmentId !in representedAfterSearch }
            if (missedUploadDocs.isNotEmpty()) {
                val avail = max(0, budgetTokens - usedTokens)
                if (avail >= 240) {
                    val perDoc = max(240, avail / missedUploadDocs.size)
                    for (doc in missedUploadDocs) {
                        val sampled = FileContextBudgeter.pickCoverageSample(
                            chunks = doc.chunks,
                            budgetTokens = perDoc,
                            mutedOwnerIds = mutedChunkIds,
                            attachmentId = doc.attachmentId,
                        )
                        for (ch in sampled) {
                            val ownerId = FileContextBudgeter.chunkOwnerId(doc.attachmentId, ch.index)
                            if (selectedByOwnerId.containsKey(ownerId)) continue
                            val tok = max(16, ch.estimatedTokens) + 18
                            if (usedTokens + tok > budgetTokens && selectedByOwnerId.isNotEmpty()) break
                            selectedByOwnerId[ownerId] = SelectedChunkEntry(
                                doc = doc,
                                chunk = ch,
                                ownerId = ownerId,
                                score = null,
                                matchedTerms = emptyList(),
                                triggerKind = "summary_sample",
                            )
                            usedTokens += tok
                        }
                    }
                }
            }
        }

        if (selectedByOwnerId.isEmpty()) {
            return FileRecallBundle(
                budgetTokens = budgetTokens,
                droppedCount = droppedCount,
                retrievalModeUsed = retrievalModeUsed,
                tier2FileIds = tier2Ids,
            )
        }

        // 5) Group by document upload order and sort chunks in natural document order
        val docOrder = validDocs.withIndex().associate { (i, d) -> d.attachmentId to i }
        val orderedEntries = selectedByOwnerId.values.sortedWith(
            compareBy<SelectedChunkEntry> { docOrder[it.doc.attachmentId] ?: Int.MAX_VALUE }
                .thenBy { it.chunk.index },
        )

        val block = buildString {
            appendLine("<recalled_file_context>")
            appendLine("以下是从本会话已上传文件中按当前对话动态召回的相关原文片段（请优先依据以下原文作答）：")
            for (entry in orderedEntries) {
                val safeName = entry.doc.fileName.ifBlank { "document.txt" }
                val totalChunks = max(1, entry.doc.chunks.size)
                appendLine(
                    "<file_excerpt name=\"$safeName\" section=\"${entry.chunk.label}\" chunk=\"${entry.chunk.index + 1}/$totalChunks\">",
                )
                appendLine(entry.chunk.text)
                appendLine("</file_excerpt>")
            }
            append("</recalled_file_context>")
        }

        val inspectItems = orderedEntries.map { entry ->
            RecalledMemoryInspectItem(
                id = entry.ownerId,
                title = "${entry.doc.fileName} · ${entry.chunk.label}",
                contentPreview = entry.chunk.text.take(MAX_PREVIEW_CHARS),
                category = FileTextExtractor.extensionOf(entry.doc.fileName).ifBlank { "file" },
                source = FileContextBudgeter.FILE_CHUNK_SOURCE,
                score = entry.score,
                estimatedTokens = entry.chunk.estimatedTokens,
                matchedTerms = entry.matchedTerms,
                triggerKind = entry.triggerKind,
            )
        }
        val chunkTexts = orderedEntries.associate { it.ownerId to it.chunk.text }
        val actualTokens = ContextBuilder.estimateTextTokens(block, modelId)

        return FileRecallBundle(
            recalledBlock = block,
            recalledTokens = actualTokens,
            recalledItems = inspectItems,
            recalledChunkTexts = chunkTexts,
            budgetTokens = budgetTokens,
            droppedCount = droppedCount,
            retrievalModeUsed = retrievalModeUsed,
            tier2FileIds = tier2Ids,
        )
    }
}
