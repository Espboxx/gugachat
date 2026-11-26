package com.gugachat.app.domain.chat

data class NovelChapterSection(
    val chapter: NovelChapter?,
    val messages: List<ChatMessage>,
    val proseChunks: List<String>,
    val wordCount: Int,
)

object NovelExport {
    private val COMPLETE_TAG_RE = Regex(
        "<(status_hud|lore_sync|chapter_end|choices)\\b[^>]*>[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )
    private val UNCLOSED_TAG_RE = Regex(
        "<(status_hud|lore_sync|chapter_end|choices)\\b[^>]*>[\\s\\S]*$",
        RegexOption.IGNORE_CASE,
    )
    private val KNOWN_NOVEL_TAG_TOKENS = listOf(
        "<status_hud>", "</status_hud>",
        "<lore_sync>", "</lore_sync>",
        "<chapter_end>", "</chapter_end>",
        "<choices>", "</choices>",
    )

    fun stripInternalTags(prose: String): String {
        if (prose.isEmpty()) return ""
        var s = COMPLETE_TAG_RE.replace(prose, "")
        s = UNCLOSED_TAG_RE.replace(s, "")
        val lastLt = s.lastIndexOf('<')
        if (lastLt >= 0) {
            val tail = s.substring(lastLt).lowercase()
            if (tail.length <= 16) {
                val matchesPrefix = if (tail == "<") {
                    lastLt == 0 || s[lastLt - 1].isWhitespace()
                } else {
                    KNOWN_NOVEL_TAG_TOKENS.any { it.startsWith(tail) }
                }
                if (matchesPrefix) {
                    s = s.substring(0, lastLt)
                }
            }
        }
        return s.trim()
    }

    fun isStreamingInternalMeta(raw: String): Boolean {
        if (raw.isEmpty()) return false
        if (UNCLOSED_TAG_RE.containsMatchIn(raw)) return true
        val lastLt = raw.lastIndexOf('<')
        if (lastLt >= 0) {
            val tail = raw.substring(lastLt).lowercase()
            if (tail.length in 2..16 && KNOWN_NOVEL_TAG_TOKENS.any { it.startsWith(tail) }) {
                return true
            }
        }
        return false
    }

    fun countProseWords(prose: String): Int {
        val clean = stripInternalTags(prose)
        if (clean.isEmpty()) return 0
        var count = 0
        for (ch in clean) {
            if (!ch.isWhitespace()) count++
        }
        return count
    }

    fun groupMessagesByChapter(
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
    ): List<NovelChapterSection> {
        val activeAssistant = TimelineReconciler.activeMessages(messages)
            .filter { it.role == "assistant" && stripInternalTags(it.content).isNotBlank() }

        val sortedChapters = chapters.sortedBy { it.order }
        if (sortedChapters.isEmpty()) {
            if (activeAssistant.isEmpty()) return emptyList()
            val chunks = activeAssistant.map { stripInternalTags(it.content) }
            return listOf(
                NovelChapterSection(
                    chapter = null,
                    messages = activeAssistant,
                    proseChunks = chunks,
                    wordCount = chunks.sumOf { countProseWords(it) },
                ),
            )
        }

        val usedMessageIds = HashSet<String>()
        val sections = ArrayList<NovelChapterSection>()

        sortedChapters.forEachIndexed { idx, ch ->
            val nextStart = sortedChapters.getOrNull(idx + 1)?.startSeq
            val effectiveEnd = when {
                ch.endSeq != null -> ch.endSeq
                nextStart != null && nextStart > ch.startSeq -> nextStart - 1
                else -> Int.MAX_VALUE
            }
            val chapterMsgs = activeAssistant.filter { m ->
                m.id !in usedMessageIds && m.seq >= ch.startSeq && m.seq <= effectiveEnd
            }
            chapterMsgs.forEach { usedMessageIds.add(it.id) }
            val chunks = chapterMsgs.map { stripInternalTags(it.content) }.filter { it.isNotBlank() }
            sections.add(
                NovelChapterSection(
                    chapter = ch,
                    messages = chapterMsgs,
                    proseChunks = chunks,
                    wordCount = chunks.sumOf { countProseWords(it) },
                ),
            )
        }

        val orphanMsgs = activeAssistant.filter { it.id !in usedMessageIds }
        if (orphanMsgs.isNotEmpty()) {
            val chunks = orphanMsgs.map { stripInternalTags(it.content) }.filter { it.isNotBlank() }
            sections.add(
                NovelChapterSection(
                    chapter = null,
                    messages = orphanMsgs,
                    proseChunks = chunks,
                    wordCount = chunks.sumOf { countProseWords(it) },
                ),
            )
        }
        return sections
    }

    fun sanitizeFileName(rawTitle: String, ext: String): String {
        val base = rawTitle
            .trim()
            .ifEmpty { "novel" }
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
            .take(48)
        return "$base.$ext"
    }

    fun formatAsTxt(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        lore: List<NovelLoreEntry> = emptyList(),
        includeLoreAppendix: Boolean = false,
    ): String = buildExport(
        novel = novel,
        chapters = chapters,
        messages = messages,
        lore = lore,
        includeLoreAppendix = includeLoreAppendix,
        markdown = false,
    )

    fun formatAsMarkdown(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        lore: List<NovelLoreEntry> = emptyList(),
        includeLoreAppendix: Boolean = false,
    ): String = buildExport(
        novel = novel,
        chapters = chapters,
        messages = messages,
        lore = lore,
        includeLoreAppendix = includeLoreAppendix,
        markdown = true,
    )

    fun formatSingleChapter(
        novel: NovelConfig,
        chapter: NovelChapter,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        markdown: Boolean = false,
    ): String {
        val section = groupMessagesByChapter(chapters, messages).firstOrNull { it.chapter?.id == chapter.id }
        val sb = StringBuilder()
        if (markdown) {
            sb.appendLine("# ${novel.title.ifBlank { "未命名小说" }} · ${chapter.title}")
        } else {
            sb.appendLine("《${novel.title.ifBlank { "未命名小说" }}》 ${chapter.title}")
            sb.appendLine("-".repeat(24))
        }
        val chunks = section?.proseChunks.orEmpty()
        if (chunks.isEmpty()) {
            if (chapter.outline.isNotBlank()) {
                sb.appendLine()
                sb.appendLine("（细纲：${chapter.outline.trim()}）")
            }
        } else {
            chunks.forEach { chunk ->
                sb.appendLine().appendLine(chunk)
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun buildExport(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        lore: List<NovelLoreEntry>,
        includeLoreAppendix: Boolean,
        markdown: Boolean,
    ): String {
        val sections = groupMessagesByChapter(chapters, messages)
        val totalWords = sections.sumOf { it.wordCount }

        val sb = StringBuilder()
        if (markdown) {
            sb.appendLine("# ${novel.title.ifBlank { "未命名小说" }}")
            val metaParts = buildList {
                if (novel.genre.isNotBlank()) add("题材：${novel.genre}")
                if (totalWords > 0) add("正文约 $totalWords 字")
            }
            if (metaParts.isNotEmpty()) sb.appendLine("\n> ${metaParts.joinToString(" · ")}")
            if (novel.synopsis.isNotBlank()) sb.appendLine("\n## 简介\n\n${novel.synopsis.trim()}")
        } else {
            sb.appendLine("《${novel.title.ifBlank { "未命名小说" }}》")
            if (novel.genre.isNotBlank()) sb.appendLine("题材：${novel.genre}")
            if (totalWords > 0) sb.appendLine("字数：约 $totalWords 字")
            if (novel.synopsis.isNotBlank()) sb.appendLine("\n简介：\n${novel.synopsis.trim()}")
            sb.appendLine("\n" + "=".repeat(32))
        }

        if (sections.isEmpty()) {
            val activeAssistant = TimelineReconciler.activeMessages(messages)
                .filter { it.role == "assistant" }
                .map { stripInternalTags(it.content) }
                .filter { it.isNotBlank() }
            activeAssistant.forEach { clean ->
                sb.appendLine().appendLine(clean)
            }
        } else {
            sections.forEach { section ->
                val ch = section.chapter
                sb.appendLine()
                if (ch != null) {
                    if (markdown) {
                        sb.appendLine("## ${ch.title}")
                    } else {
                        sb.appendLine(ch.title)
                        sb.appendLine("-".repeat(24))
                    }
                    if (section.proseChunks.isEmpty()) {
                        if (ch.outline.isNotBlank()) {
                            sb.appendLine()
                            sb.appendLine(if (markdown) "*（细纲：${ch.outline.trim()}）*" else "（细纲：${ch.outline.trim()}）")
                        }
                    } else {
                        section.proseChunks.forEach { clean ->
                            sb.appendLine().appendLine(clean)
                        }
                    }
                } else {
                    sb.appendLine(if (markdown) "## 续篇片段" else "续篇片段")
                    section.proseChunks.forEach { clean ->
                        sb.appendLine().appendLine(clean)
                    }
                }
            }
        }

        if (includeLoreAppendix) {
            appendWorldbookAppendix(sb, novel, chapters, lore, markdown)
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun appendWorldbookAppendix(
        sb: StringBuilder,
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        lore: List<NovelLoreEntry>,
        markdown: Boolean,
    ) {
        val activeLore = lore.filter { !it.stale }
        val hasOutline = novel.worldview.isNotBlank() || novel.mainOutline.isNotBlank()
        val hasSummaries = chapters.any { it.outline.isNotBlank() || it.summary.isNotBlank() }
        if (!hasOutline && !hasSummaries && activeLore.isEmpty()) return

        sb.appendLine()
        if (markdown) {
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("## 附录：大纲与世界设定集")
        } else {
            sb.appendLine("=".repeat(32))
            sb.appendLine("附录：大纲与世界设定集")
            sb.appendLine("=".repeat(32))
        }

        if (novel.worldview.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(if (markdown) "### 世界观基石" else "【世界观基石】")
            sb.appendLine(novel.worldview.trim())
        }
        if (novel.mainOutline.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(if (markdown) "### 全书主线总纲" else "【全书主线总纲】")
            sb.appendLine(novel.mainOutline.trim())
        }
        if (hasSummaries) {
            sb.appendLine()
            sb.appendLine(if (markdown) "### 分章细纲与梗概" else "【分章细纲与梗概】")
            chapters.sortedBy { it.order }.forEach { ch ->
                val detail = ch.summary.ifBlank { ch.outline }.trim()
                if (detail.isNotBlank()) {
                    sb.appendLine("- ${ch.title}：$detail")
                }
            }
        }
        if (activeLore.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(if (markdown) "### 世界书设定条目" else "【世界书设定条目】")
            activeLore.sortedWith(compareByDescending<NovelLoreEntry> { it.alwaysActive }.thenByDescending { it.priority })
                .forEach { entry ->
                    val kw = if (entry.keywords.isNotEmpty()) "（关键词：${entry.keywords.joinToString("、")}）" else ""
                    val tag = if (entry.alwaysActive) "[常驻/${entry.category}]" else "[${entry.category}]"
                    sb.appendLine("- $tag ${entry.title}$kw：${entry.content.trim()}")
                }
        }
    }
}
