package com.gugachat.app.domain.chat

object NovelExport {
    private val STRIP_TAG_RE = Regex(
        "<(status_hud|lore_sync|chapter_end|choices)>[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )

    fun stripInternalTags(prose: String): String =
        prose.replace(STRIP_TAG_RE, "").trim()

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
    ): String = buildExport(novel, chapters, messages, markdown = false)

    fun formatAsMarkdown(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
    ): String = buildExport(novel, chapters, messages, markdown = true)

    private fun buildExport(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        markdown: Boolean,
    ): String {
        val activeAssistant = TimelineReconciler.activeMessages(messages)
            .filter { it.role == "assistant" && it.content.isNotBlank() }

        val sb = StringBuilder()
        if (markdown) {
            sb.appendLine("# ${novel.title.ifBlank { "未命名小说" }}")
            if (novel.genre.isNotBlank()) sb.appendLine("\n> 题材：${novel.genre}")
            if (novel.synopsis.isNotBlank()) sb.appendLine("\n## 简介\n\n${novel.synopsis.trim()}")
        } else {
            sb.appendLine("《${novel.title.ifBlank { "未命名小说" }}》")
            if (novel.genre.isNotBlank()) sb.appendLine("题材：${novel.genre}")
            if (novel.synopsis.isNotBlank()) sb.appendLine("\n简介：\n${novel.synopsis.trim()}")
            sb.appendLine("\n" + "=".repeat(32))
        }

        val sortedChapters = chapters.sortedBy { it.order }
        if (sortedChapters.isEmpty()) {
            activeAssistant.forEach { msg ->
                val clean = stripInternalTags(msg.content)
                if (clean.isNotBlank()) {
                    sb.appendLine().appendLine(clean)
                }
            }
            return sb.toString().trimEnd() + "\n"
        }

        val usedMessageIds = HashSet<String>()
        sortedChapters.forEach { ch ->
            val end = ch.endSeq ?: Int.MAX_VALUE
            val chapterMsgs = activeAssistant.filter { m ->
                m.id !in usedMessageIds && m.seq >= ch.startSeq && m.seq <= end
            }
            chapterMsgs.forEach { usedMessageIds.add(it.id) }

            sb.appendLine()
            if (markdown) {
                sb.appendLine("## ${ch.title}")
            } else {
                sb.appendLine(ch.title)
                sb.appendLine("-".repeat(24))
            }
            if (chapterMsgs.isEmpty()) {
                if (ch.outline.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine(if (markdown) "*（细纲：${ch.outline.trim()}）*" else "（细纲：${ch.outline.trim()}）")
                }
            } else {
                chapterMsgs.forEach { msg ->
                    val clean = stripInternalTags(msg.content)
                    if (clean.isNotBlank()) {
                        sb.appendLine().appendLine(clean)
                    }
                }
            }
        }

        val orphanMsgs = activeAssistant.filter { it.id !in usedMessageIds }
        if (orphanMsgs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(if (markdown) "## 续篇片段" else "续篇片段")
            orphanMsgs.forEach { msg ->
                val clean = stripInternalTags(msg.content)
                if (clean.isNotBlank()) sb.appendLine().appendLine(clean)
            }
        }
        return sb.toString().trimEnd() + "\n"
    }
}
