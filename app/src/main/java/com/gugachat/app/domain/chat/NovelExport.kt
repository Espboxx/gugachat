package com.gugachat.app.domain.chat

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    private data class EpubChapterDoc(
        val id: String,
        val href: String,
        val title: String,
        val xhtml: String,
    )

    private data class EpubCoverSpec(
        val href: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    fun exportEpubBytes(
        conv: ChatConversation,
        chapters: List<NovelChapter>,
        messages: List<ChatMessage>,
        lore: List<NovelLoreEntry> = emptyList(),
        coverImageBytes: ByteArray? = null,
        coverMimeType: String = "image/png",
    ): ByteArray {
        val novel = conv.novel ?: NovelConfig(title = conv.title.ifBlank { "未命名小说" })
        val bookTitle = novel.title.ifBlank { conv.title }.ifBlank { "未命名小说" }
        val sections = groupMessagesByChapter(chapters, messages)
        val activeLore = lore.filter { !it.stale }
        val isZh = detectChineseContent(bookTitle, novel, sections, activeLore)
        val lang = if (isZh) "zh-CN" else "en"
        val bookUuid = UUID.nameUUIDFromBytes(
            conv.id.ifBlank { bookTitle }.toByteArray(Charsets.UTF_8),
        ).toString()
        val modifiedIso = formatUtcIsoTimestamp(
            conv.updatedAt.takeIf { it > 0L } ?: conv.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )

        val chapterDocs = buildEpubChapterDocs(
            bookTitle = bookTitle,
            novel = novel,
            sections = sections,
            messages = messages,
            lang = lang,
            isZh = isZh,
        )
        val loreXhtml = buildEpubLoreXhtml(
            novel = novel,
            chapters = chapters,
            activeLore = activeLore,
            lang = lang,
            isZh = isZh,
        )
        val coverSpec = coverImageBytes
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes ->
                val (normMime, ext) = normalizeCoverMime(coverMimeType)
                EpubCoverSpec(href = "cover.$ext", mimeType = normMime, bytes = bytes)
            }
        val coverXhtml = coverSpec?.let { buildEpubCoverXhtml(bookTitle, it.href, lang) }

        val tocEntries = buildList {
            chapterDocs.forEach { add(it.href to it.title) }
            if (loreXhtml != null) {
                add("lore.xhtml" to if (isZh) "附录：大纲与世界设定集" else "Appendix: Outline & Lore")
            }
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // EPUB OCF requirement: `mimetype` MUST be the very first entry, STORED uncompressed, with exact CRC32.
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(mimetypeBytes) }.value
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                this.crc = crc
            }
            zos.putNextEntry(mimetypeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            zos.putTextEntry("META-INF/container.xml", buildContainerXml())
            zos.putTextEntry("OEBPS/style.css", EPUB_STYLE_CSS)
            zos.putTextEntry(
                "OEBPS/content.opf",
                buildContentOpf(
                    bookUuid = bookUuid,
                    bookTitle = bookTitle,
                    novel = novel,
                    lang = lang,
                    modifiedIso = modifiedIso,
                    chapterDocs = chapterDocs,
                    hasLore = loreXhtml != null,
                    coverSpec = coverSpec,
                ),
            )
            zos.putTextEntry("OEBPS/nav.xhtml", buildNavXhtml(bookTitle, lang, isZh, tocEntries))
            zos.putTextEntry("OEBPS/toc.ncx", buildTocNcx(bookUuid, bookTitle, tocEntries))

            if (coverSpec != null && coverXhtml != null) {
                zos.putBytesEntry("OEBPS/${coverSpec.href}", coverSpec.bytes)
                zos.putTextEntry("OEBPS/cover.xhtml", coverXhtml)
            }

            chapterDocs.forEach { doc ->
                zos.putTextEntry("OEBPS/${doc.href}", doc.xhtml)
            }

            if (loreXhtml != null) {
                zos.putTextEntry("OEBPS/lore.xhtml", loreXhtml)
            }
        }
        return baos.toByteArray()
    }

    private fun ZipOutputStream.putTextEntry(path: String, text: String) {
        putBytesEntry(path, text.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.putBytesEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun buildEpubChapterDocs(
        bookTitle: String,
        novel: NovelConfig,
        sections: List<NovelChapterSection>,
        messages: List<ChatMessage>,
        lang: String,
        isZh: Boolean,
    ): List<EpubChapterDoc> {
        val docs = ArrayList<EpubChapterDoc>()

        fun appendChapter(index: Int, title: String, chunks: List<String>, outlineFallback: String = "") {
            val id = String.format(Locale.US, "ch_%03d", index)
            val href = "$id.xhtml"
            val paragraphs = splitEpubParagraphs(chunks)
            val bodySb = StringBuilder()
            bodySb.appendLine("  <h1>${escapeXml(title)}</h1>")
            if (index == 1 && novel.synopsis.isNotBlank() && sections.size <= 1) {
                bodySb.appendLine("  <p class=\"no-indent outline\">${escapeXml(novel.synopsis.trim())}</p>")
            }
            if (paragraphs.isNotEmpty()) {
                paragraphs.forEach { para ->
                    bodySb.appendLine("  <p>${escapeXml(para)}</p>")
                }
            } else if (outlineFallback.isNotBlank()) {
                val label = if (isZh) "（细纲：${outlineFallback.trim()}）" else "(Outline: ${outlineFallback.trim()})"
                bodySb.appendLine("  <p class=\"no-indent outline\">${escapeXml(label)}</p>")
            } else if (novel.synopsis.isNotBlank()) {
                bodySb.appendLine("  <p>${escapeXml(novel.synopsis.trim())}</p>")
            } else {
                val emptyHint = if (isZh) "（暂无正文）" else "(No content yet)"
                bodySb.appendLine("  <p class=\"no-indent outline\">${escapeXml(emptyHint)}</p>")
            }
            docs.add(
                EpubChapterDoc(
                    id = id,
                    href = href,
                    title = title,
                    xhtml = wrapXhtmlDocument(title = title, lang = lang, bodyHtml = bodySb.toString().trimEnd()),
                ),
            )
        }

        if (sections.isEmpty()) {
            val fallbackChunks = TimelineReconciler.activeMessages(messages)
                .filter { it.role == "assistant" }
                .map { stripInternalTags(it.content) }
                .filter { it.isNotBlank() }
            appendChapter(1, bookTitle, fallbackChunks)
            return docs
        }

        var chapterNumber = 1
        sections.forEach { section ->
            val ch = section.chapter
            val title = when {
                ch != null && ch.title.isNotBlank() -> ch.title.trim()
                sections.size == 1 -> bookTitle
                isZh -> "续篇片段"
                else -> "Continued"
            }
            appendChapter(
                index = chapterNumber++,
                title = title,
                chunks = section.proseChunks,
                outlineFallback = ch?.outline.orEmpty(),
            )
        }
        return docs
    }

    private fun buildEpubLoreXhtml(
        novel: NovelConfig,
        chapters: List<NovelChapter>,
        activeLore: List<NovelLoreEntry>,
        lang: String,
        isZh: Boolean,
    ): String? {
        val hasOutline = novel.worldview.isNotBlank() || novel.mainOutline.isNotBlank()
        val hasSummaries = chapters.any { it.outline.isNotBlank() || it.summary.isNotBlank() }
        if (!hasOutline && !hasSummaries && activeLore.isEmpty()) return null

        val appendixTitle = if (isZh) "附录：大纲与世界设定集" else "Appendix: Outline & Lore"
        val sb = StringBuilder()
        sb.appendLine("  <h1>${escapeXml(appendixTitle)}</h1>")

        if (novel.worldview.isNotBlank()) {
            val heading = if (isZh) "世界观基石" else "Worldview"
            sb.appendLine("  <div class=\"lore-section\">")
            sb.appendLine("    <h2>${escapeXml(heading)}</h2>")
            splitEpubParagraphs(listOf(novel.worldview)).forEach { line ->
                sb.appendLine("    <p>${escapeXml(line)}</p>")
            }
            sb.appendLine("  </div>")
        }

        if (novel.mainOutline.isNotBlank()) {
            val heading = if (isZh) "全书主线总纲" else "Main Outline"
            sb.appendLine("  <div class=\"lore-section\">")
            sb.appendLine("    <h2>${escapeXml(heading)}</h2>")
            splitEpubParagraphs(listOf(novel.mainOutline)).forEach { line ->
                sb.appendLine("    <p>${escapeXml(line)}</p>")
            }
            sb.appendLine("  </div>")
        }

        if (hasSummaries) {
            val heading = if (isZh) "分章细纲与梗概" else "Chapter Summaries"
            sb.appendLine("  <div class=\"lore-section\">")
            sb.appendLine("    <h2>${escapeXml(heading)}</h2>")
            chapters.sortedBy { it.order }.forEach { ch ->
                val detail = ch.summary.ifBlank { ch.outline }.trim()
                if (detail.isNotBlank()) {
                    sb.appendLine("    <p class=\"lore-item\"><strong>${escapeXml(ch.title)}</strong>：${escapeXml(detail)}</p>")
                }
            }
            sb.appendLine("  </div>")
        }

        if (activeLore.isNotEmpty()) {
            val heading = if (isZh) "世界书设定条目" else "Worldbook Lore"
            sb.appendLine("  <div class=\"lore-section\">")
            sb.appendLine("    <h2>${escapeXml(heading)}</h2>")
            activeLore
                .sortedWith(compareByDescending<NovelLoreEntry> { it.alwaysActive }.thenByDescending { it.priority })
                .forEach { entry ->
                    val kw = if (entry.keywords.isNotEmpty()) {
                        val kwPrefix = if (isZh) "关键词：" else "Keywords: "
                        " ($kwPrefix${entry.keywords.joinToString(", ")})"
                    } else {
                        ""
                    }
                    val badge = if (entry.alwaysActive) "[★ ${entry.category}]" else "[${entry.category}]"
                    sb.appendLine(
                        "    <p class=\"lore-item\"><strong>${escapeXml("$badge ${entry.title}$kw")}</strong>：${escapeXml(entry.content.trim())}</p>",
                    )
                }
            sb.appendLine("  </div>")
        }

        return wrapXhtmlDocument(title = appendixTitle, lang = lang, bodyHtml = sb.toString().trimEnd())
    }

    private fun buildEpubCoverXhtml(bookTitle: String, coverHref: String, lang: String): String {
        val body = """
            |  <div class="cover">
            |    <img src="${escapeXml(coverHref)}" alt="${escapeXml(bookTitle)}"/>
            |  </div>
        """.trimMargin()
        return wrapXhtmlDocument(title = bookTitle, lang = lang, bodyHtml = body)
    }

    private fun wrapXhtmlDocument(title: String, lang: String, bodyHtml: String): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE html>
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${escapeXml(lang)}" lang="${escapeXml(lang)}">
        |<head>
        |  <meta charset="UTF-8"/>
        |  <title>${escapeXml(title)}</title>
        |  <link rel="stylesheet" type="text/css" href="style.css"/>
        |</head>
        |<body>
        |$bodyHtml
        |</body>
        |</html>
    """.trimMargin() + "\n"

    private fun buildContainerXml(): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
        |  <rootfiles>
        |    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
        |  </rootfiles>
        |</container>
    """.trimMargin() + "\n"

    private fun buildContentOpf(
        bookUuid: String,
        bookTitle: String,
        novel: NovelConfig,
        lang: String,
        modifiedIso: String,
        chapterDocs: List<EpubChapterDoc>,
        hasLore: Boolean,
        coverSpec: EpubCoverSpec?,
    ): String {
        val metaExtra = buildString {
            if (novel.genre.isNotBlank()) {
                appendLine("    <dc:subject>${escapeXml(novel.genre.trim())}</dc:subject>")
            }
            if (novel.synopsis.isNotBlank()) {
                appendLine("    <dc:description>${escapeXml(novel.synopsis.trim())}</dc:description>")
            }
            if (coverSpec != null) {
                appendLine("    <meta name=\"cover\" content=\"cover-image\"/>")
            }
        }.trimEnd()

        val manifestItems = buildString {
            appendLine("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
            appendLine("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
            appendLine("    <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
            if (coverSpec != null) {
                appendLine(
                    "    <item id=\"cover-image\" href=\"${escapeXml(coverSpec.href)}\" media-type=\"${escapeXml(coverSpec.mimeType)}\" properties=\"cover-image\"/>",
                )
                appendLine("    <item id=\"cover-page\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>")
            }
            chapterDocs.forEach { doc ->
                appendLine("    <item id=\"${escapeXml(doc.id)}\" href=\"${escapeXml(doc.href)}\" media-type=\"application/xhtml+xml\"/>")
            }
            if (hasLore) {
                appendLine("    <item id=\"lore\" href=\"lore.xhtml\" media-type=\"application/xhtml+xml\"/>")
            }
        }.trimEnd()

        val spineItems = buildString {
            if (coverSpec != null) {
                appendLine("    <itemref idref=\"cover-page\"/>")
            }
            chapterDocs.forEach { doc ->
                appendLine("    <itemref idref=\"${escapeXml(doc.id)}\"/>")
            }
            if (hasLore) {
                appendLine("    <itemref idref=\"lore\"/>")
            }
        }.trimEnd()

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<package xmlns=\"http://www.idpf.org/opf/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\" xml:lang=\"${escapeXml(lang)}\">")
            appendLine("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
            appendLine("    <dc:identifier id=\"pub-id\">urn:uuid:${escapeXml(bookUuid)}</dc:identifier>")
            appendLine("    <dc:title>${escapeXml(bookTitle)}</dc:title>")
            appendLine("    <dc:creator>GuGuChat</dc:creator>")
            appendLine("    <dc:language>${escapeXml(lang)}</dc:language>")
            appendLine("    <meta property=\"dcterms:modified\">${escapeXml(modifiedIso)}</meta>")
            if (metaExtra.isNotEmpty()) {
                appendLine(metaExtra)
            }
            appendLine("  </metadata>")
            appendLine("  <manifest>")
            appendLine(manifestItems)
            appendLine("  </manifest>")
            appendLine("  <spine toc=\"ncx\">")
            appendLine(spineItems)
            appendLine("  </spine>")
            appendLine("</package>")
        }
    }

    private fun buildNavXhtml(
        bookTitle: String,
        lang: String,
        isZh: Boolean,
        tocEntries: List<Pair<String, String>>,
    ): String {
        val tocHeading = if (isZh) "目录" else "Table of Contents"
        val items = tocEntries.joinToString("\n") { (href, title) ->
            "      <li><a href=\"${escapeXml(href)}\">${escapeXml(title)}</a></li>"
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${escapeXml(lang)}" lang="${escapeXml(lang)}">
            |<head>
            |  <meta charset="UTF-8"/>
            |  <title>${escapeXml(tocHeading)} - ${escapeXml(bookTitle)}</title>
            |  <link rel="stylesheet" type="text/css" href="style.css"/>
            |</head>
            |<body>
            |  <nav epub:type="toc" id="toc">
            |    <h1>${escapeXml(tocHeading)}</h1>
            |    <ol>
            |$items
            |    </ol>
            |  </nav>
            |</body>
            |</html>
        """.trimMargin() + "\n"
    }

    private fun buildTocNcx(
        bookUuid: String,
        bookTitle: String,
        tocEntries: List<Pair<String, String>>,
    ): String {
        val navPoints = tocEntries.mapIndexed { idx, (href, title) ->
            val order = idx + 1
            """
                |    <navPoint id="navPoint-$order" playOrder="$order">
                |      <navLabel><text>${escapeXml(title)}</text></navLabel>
                |      <content src="${escapeXml(href)}"/>
                |    </navPoint>
            """.trimMargin()
        }.joinToString("\n")

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
            |  <head>
            |    <meta name="dtb:uid" content="urn:uuid:${escapeXml(bookUuid)}"/>
            |    <meta name="dtb:depth" content="1"/>
            |    <meta name="dtb:totalPageCount" content="0"/>
            |    <meta name="dtb:maxPageNumber" content="0"/>
            |  </head>
            |  <docTitle><text>${escapeXml(bookTitle)}</text></docTitle>
            |  <navMap>
            |$navPoints
            |  </navMap>
            |</ncx>
        """.trimMargin() + "\n"
    }

    private fun splitEpubParagraphs(chunks: List<String>): List<String> =
        chunks.flatMap { chunk ->
            chunk.replace("\r\n", "\n")
                .split('\n')
                .map { it.trim().trimStart('\u3000').trim() }
                .filter { it.isNotEmpty() }
        }

    private fun detectChineseContent(
        bookTitle: String,
        novel: NovelConfig,
        sections: List<NovelChapterSection>,
        lore: List<NovelLoreEntry>,
    ): Boolean {
        fun hasCjk(s: String): Boolean = s.any { ch -> ch in '\u4E00'..'\u9FFF' }
        if (hasCjk(bookTitle) || hasCjk(novel.synopsis) || hasCjk(novel.genre)) return true
        if (sections.any { sec ->
                hasCjk(sec.chapter?.title.orEmpty()) || sec.proseChunks.any(::hasCjk)
            }
        ) {
            return true
        }
        if (lore.any { hasCjk(it.title) || hasCjk(it.content) }) return true
        val hasLatin = bookTitle.any { it in 'A'..'Z' || it in 'a'..'z' } ||
            sections.any { sec -> sec.proseChunks.any { chunk -> chunk.any { it in 'A'..'Z' || it in 'a'..'z' } } }
        return !hasLatin
    }

    private fun normalizeCoverMime(rawMime: String): Pair<String, String> {
        val clean = rawMime.trim().lowercase(Locale.ROOT)
        return when {
            clean.contains("jpeg") || clean.contains("jpg") -> "image/jpeg" to "jpg"
            clean.contains("webp") -> "image/webp" to "webp"
            clean.contains("gif") -> "image/gif" to "gif"
            else -> "image/png" to "png"
        }
    }

    private fun formatUtcIsoTimestamp(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochMs))
    }

    private fun escapeXml(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            val code = ch.code
            val validXmlChar = code == 0x09 || code == 0x0A || code == 0x0D ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (!validXmlChar) continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val EPUB_STYLE_CSS = """
        body {
          font-family: serif;
          line-height: 1.8;
          margin: 1.2em;
          color: #1c1917;
        }
        h1, h2, h3 {
          line-height: 1.35;
          margin: 0.9em 0 0.5em;
        }
        p {
          text-indent: 2em;
          line-height: 1.8;
          margin: 0.6em 0;
        }
        p.no-indent, .outline {
          text-indent: 0;
          color: #57534e;
          font-style: italic;
        }
        .cover {
          text-align: center;
          margin: 0;
          padding: 0;
        }
        .cover img {
          max-width: 100%;
          max-height: 100%;
        }
        .lore-section {
          margin: 1.2em 0;
        }
        .lore-item {
          text-indent: 0;
          margin: 0.5em 0;
        }
    """.trimIndent() + "\n"
}
