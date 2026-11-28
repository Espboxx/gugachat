package com.gugachat.app.domain.chat

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.min

data class PdfExtractionResult(
    val text: String,
    val pageCount: Int,
    val truncated: Boolean = false,
)

data class ExtractedFileContent(
    val text: String,
    val charCount: Int,
    val estimatedTokens: Int,
    val pageCount: Int = 0,
    val previewSnippet: String = "",
    val isLikelyScannedPdf: Boolean = false,
    val truncatedAtExtraction: Boolean = false,
    val extractionNote: String = "",
)

object FileTextExtractor {
    const val MAX_EXTRACTED_CHARS = 1_500_000
    private const val MAX_ZIP_ENTRY_BYTES = 20 * 1024 * 1024
    private const val MAX_XLSX_ROWS_PER_SHEET = 3_000
    private const val MAX_XLSX_SHEETS = 20

    private val SCRIPT_STYLE_RE = Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>")
    private val BLOCK_TAG_RE = Regex("(?i)</?(p|div|br|li|tr|h[1-6]|section|article|blockquote|hr|pre)[^>]*>")
    private val ANY_TAG_RE = Regex("<[^>]+>")
    private val MULTI_BLANK_LINES_RE = Regex("\\n{3,}")
    private val NUMERIC_ENTITY_RE = Regex("&#(\\d+);")
    private val HEX_ENTITY_RE = Regex("&#[xX]([0-9a-fA-F]+);")
    private val WHITESPACE_COLLAPSE_RE = Regex("\\s+")
    private val TRAILING_XML_NUMBER_RE = Regex("(\\d+)\\.xml$", RegexOption.IGNORE_CASE)

    private val KNOWN_TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "jsonl", "ndjson", "yaml", "yml", "xml", "csv", "tsv",
        "log", "ini", "cfg", "conf", "toml", "env", "properties", "sql", "graphql", "proto",
        "html", "htm", "xhtml", "css", "scss", "less", "js", "mjs", "cjs", "jsx", "ts", "tsx",
        "py", "kt", "kts", "java", "c", "cc", "cpp", "cxx", "h", "hpp", "cs", "go", "rs", "rb",
        "php", "swift", "dart", "scala", "lua", "sh", "bash", "zsh", "bat", "ps1", "r", "m",
        "vue", "svelte", "gradle", "cmake", "dockerfile", "makefile", "tex", "rst", "srt", "vtt",
        "ass", "lrc", "diff", "patch",
    )

    private val KNOWN_BINARY_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "exe", "dll", "so", "dmg", "iso",
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "mp4", "mkv", "mov", "avi", "webm", "flv",
        "ttf", "otf", "woff", "woff2", "db", "sqlite", "bin", "class", "jar", "dex",
    )

    fun extensionOf(fileName: String): String {
        val clean = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
        val dot = clean.lastIndexOf('.')
        if (dot <= 0 || dot >= clean.lastIndex) return ""
        return clean.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun isImageFile(fileName: String, mimeType: String): Boolean {
        if (mimeType.lowercase(Locale.ROOT).startsWith("image/")) return true
        return extensionOf(fileName) in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    fun extractFromBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        pdfExtractor: ((ByteArray) -> PdfExtractionResult)? = null,
    ): ExtractedFileContent {
        val ext = extensionOf(fileName)
        val lowerMime = mimeType.lowercase(Locale.ROOT)

        if (ext == "pdf" || lowerMime == "application/pdf") {
            val pdfRes = pdfExtractor?.invoke(bytes)
                ?: throw IllegalArgumentException("PDF parser unavailable")
            val cleaned = normalizeText(pdfRes.text)
            val nonWs = cleaned.count { !it.isWhitespace() }
            if (nonWs < 40 && pdfRes.pageCount > 0) {
                return ExtractedFileContent(
                    text = cleaned,
                    charCount = cleaned.length,
                    estimatedTokens = ContextBuilder.estimateTextTokens(cleaned),
                    pageCount = pdfRes.pageCount,
                    previewSnippet = cleaned.take(180),
                    isLikelyScannedPdf = true,
                    truncatedAtExtraction = pdfRes.truncated,
                    extractionNote = "扫描版 PDF（共 ${pdfRes.pageCount} 页，未检测到可选中文字）",
                )
            }
            return finalizeContent(
                rawText = cleaned,
                pageCount = pdfRes.pageCount,
                alreadyTruncated = pdfRes.truncated,
                note = if (pdfRes.pageCount > 0) "共 ${pdfRes.pageCount} 页" else "",
            )
        }

        if (ext == "docx" || lowerMime.contains("wordprocessingml")) {
            return extractDocx(bytes)
        }
        if (ext == "xlsx" || lowerMime.contains("spreadsheetml")) {
            return extractXlsx(bytes)
        }
        if (ext == "pptx" || lowerMime.contains("presentationml")) {
            return extractPptx(bytes)
        }
        if (ext == "epub" || lowerMime == "application/epub+zip") {
            return extractEpub(bytes)
        }
        if (ext in setOf("html", "htm", "xhtml") || lowerMime.contains("text/html")) {
            val decoded = decodeTextSmart(bytes)
            return finalizeContent(extractHtml(decoded))
        }

        if (ext in KNOWN_BINARY_EXTENSIONS && ext !in KNOWN_TEXT_EXTENSIONS) {
            throw IllegalArgumentException("暂不支持直接解析 .$ext 二进制文件")
        }

        if (ext !in KNOWN_TEXT_EXTENSIONS && !lowerMime.startsWith("text/") &&
            !lowerMime.contains("json") && !lowerMime.contains("xml") && !lowerMime.contains("javascript") &&
            looksBinary(bytes)
        ) {
            throw IllegalArgumentException("无法提取该二进制文件文本内容")
        }

        val decoded = decodeTextSmart(bytes)
        if ((ext == "json" || lowerMime.contains("json")) && decoded.contains("\"uu-game-save\"")) {
            val distilled = UuSaveCodec.formatAsAttachmentMarkdown(decoded, fileName)
            if (!distilled.isNullOrBlank()) {
                return finalizeContent(
                    rawText = distilled,
                    note = "UU 互动文游存档（已智能提炼，去除冗余元数据）",
                )
            }
        }
        return finalizeContent(decoded)
    }

    fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // Check BOM first
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // Strict UTF-8 validation first
        val utf8 = runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8 != null) return utf8

        // Fallback to GB18030 / GBK (common for Windows Chinese TXT/CSV exports)
        val cnCharset = runCatching { Charset.forName("GB18030") }.getOrNull()
            ?: runCatching { Charset.forName("GBK") }.getOrNull()
        if (cnCharset != null) {
            val decoded = runCatching { String(bytes, cnCharset) }.getOrNull()
            if (decoded != null) return decoded
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun extractHtml(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        var s = SCRIPT_STYLE_RE.replace(rawHtml, " ")
        s = BLOCK_TAG_RE.replace(s, "\n")
        s = ANY_TAG_RE.replace(s, "")
        s = decodeHtmlEntities(s)
        return normalizeText(s)
    }

    fun extractDocx(bytes: ByteArray): ExtractedFileContent {
        val entries = readZipEntries(bytes) { name ->
            name == "word/document.xml" || name.startsWith("word/header") || name.startsWith("word/footer")
        }
        val docBytes = entries["word/document.xml"]
            ?: throw IllegalArgumentException("无效的 DOCX 文件（缺少 document.xml）")
        val doc = parseXmlSafely(docBytes)
            ?: throw IllegalArgumentException("无法解析 DOCX XML 结构")

        val out = StringBuilder()
        val bodyList = doc.getElementsByTagNameNS("*", "body")
        val rootNode: Node = if (bodyList.length > 0) bodyList.item(0) else doc.documentElement
        val children = rootNode.childNodes
        for (i in 0 until children.length) {
            if (out.length >= MAX_EXTRACTED_CHARS) break
            val node = children.item(i)
            val local = localTagName(node)
            when (local) {
                "p" -> {
                    val line = collectDocxParagraphText(node).trim()
                    if (line.isNotEmpty()) {
                        out.appendLine(line)
                    }
                }
                "tbl" -> {
                    appendDocxTable(node, out)
                }
            }
        }
        return finalizeContent(out.toString())
    }

    fun extractXlsx(bytes: ByteArray): ExtractedFileContent {
        val entries = readZipEntries(bytes) { name ->
            name == "xl/sharedStrings.xml" ||
                name == "xl/workbook.xml" ||
                (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseXlsxSharedStrings).orEmpty()
        val sheetNames = entries["xl/workbook.xml"]?.let(::parseXlsxSheetNames).orEmpty()

        val sheetEntries = entries.entries
            .filter { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }
            .sortedBy { extractTrailingNumber(it.key) }
            .take(MAX_XLSX_SHEETS)

        if (sheetEntries.isEmpty()) {
            throw IllegalArgumentException("无效的 XLSX 文件（未找到工作表）")
        }

        val out = StringBuilder()
        var totalRows = 0
        var truncated = false
        sheetEntries.forEachIndexed { idx, (_, sheetBytes) ->
            if (out.length >= MAX_EXTRACTED_CHARS) {
                truncated = true
                return@forEachIndexed
            }
            val title = sheetNames.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Sheet ${idx + 1}"
            val doc = parseXmlSafely(sheetBytes) ?: return@forEachIndexed
            val rowNodes = doc.getElementsByTagNameNS("*", "row")
            if (rowNodes.length == 0) return@forEachIndexed

            out.appendLine("## $title")
            val rowLimit = min(rowNodes.length, MAX_XLSX_ROWS_PER_SHEET)
            if (rowNodes.length > MAX_XLSX_ROWS_PER_SHEET) truncated = true

            for (r in 0 until rowLimit) {
                if (out.length >= MAX_EXTRACTED_CHARS) {
                    truncated = true
                    break
                }
                val rowEl = rowNodes.item(r) as? Element ?: continue
                val cells = rowEl.getElementsByTagNameNS("*", "c")
                val values = ArrayList<String>(cells.length)
                for (c in 0 until cells.length) {
                    val cell = cells.item(c) as? Element ?: continue
                    val cellType = cell.getAttribute("t")
                    val value = when (cellType) {
                        "s" -> {
                            val idxText = firstChildText(cell, "v")?.trim()?.toIntOrNull()
                            if (idxText != null && idxText in sharedStrings.indices) sharedStrings[idxText] else ""
                        }
                        "inlineStr" -> collectTagTexts(cell, "t")
                        else -> firstChildText(cell, "v").orEmpty()
                    }.replace('\n', ' ').replace('\t', ' ').trim()
                    values.add(value)
                }
                if (values.any { it.isNotEmpty() }) {
                    out.appendLine(values.joinToString(" | "))
                    totalRows++
                }
            }
            out.appendLine()
        }

        val note = buildString {
            append("${sheetEntries.size} 个工作表 · $totalRows 行")
            if (truncated) append("（已按上限节选）")
        }
        return finalizeContent(out.toString(), pageCount = sheetEntries.size, alreadyTruncated = truncated, note = note)
    }

    fun extractPptx(bytes: ByteArray): ExtractedFileContent {
        val entries = readZipEntries(bytes) { name ->
            name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
        }
        val slides = entries.entries
            .sortedBy { extractTrailingNumber(it.key) }
        if (slides.isEmpty()) {
            throw IllegalArgumentException("无效的 PPTX 文件（未找到幻灯片）")
        }

        val out = StringBuilder()
        slides.forEachIndexed { idx, (_, slideBytes) ->
            if (out.length >= MAX_EXTRACTED_CHARS) return@forEachIndexed
            val doc = parseXmlSafely(slideBytes) ?: return@forEachIndexed
            val pNodes = doc.getElementsByTagNameNS("*", "p")
            val slideLines = ArrayList<String>()
            for (i in 0 until pNodes.length) {
                val txt = collectTagTexts(pNodes.item(i), "t").trim()
                if (txt.isNotEmpty()) slideLines.add(txt)
            }
            if (slideLines.isNotEmpty()) {
                out.appendLine("## 第 ${idx + 1} 页")
                slideLines.forEach { out.appendLine(it) }
                out.appendLine()
            }
        }
        return finalizeContent(out.toString(), pageCount = slides.size, note = "共 ${slides.size} 页幻灯片")
    }

    fun extractEpub(bytes: ByteArray): ExtractedFileContent {
        val entries = readZipEntries(bytes) { name ->
            val lower = name.lowercase(Locale.ROOT)
            lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".opf")
        }
        val htmlEntries = entries.entries
            .filter {
                val lower = it.key.lowercase(Locale.ROOT)
                lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
            }
            .sortedBy { it.key }
        if (htmlEntries.isEmpty()) {
            throw IllegalArgumentException("无效的 EPUB 文件（未找到正文内容）")
        }

        val out = StringBuilder()
        var chapters = 0
        for ((_, raw) in htmlEntries) {
            if (out.length >= MAX_EXTRACTED_CHARS) break
            val text = extractHtml(decodeTextSmart(raw)).trim()
            if (text.length >= 12) {
                out.appendLine(text)
                out.appendLine()
                chapters++
            }
        }
        return finalizeContent(out.toString(), pageCount = chapters, note = "共 $chapters 节")
    }

    private fun finalizeContent(
        rawText: String,
        pageCount: Int = 0,
        alreadyTruncated: Boolean = false,
        note: String = "",
    ): ExtractedFileContent {
        val normalized = normalizeText(rawText)
        val truncated = alreadyTruncated || normalized.length > MAX_EXTRACTED_CHARS
        val capped = if (normalized.length > MAX_EXTRACTED_CHARS) {
            normalized.substring(0, MAX_EXTRACTED_CHARS)
        } else {
            normalized
        }
        val tokens = ContextBuilder.estimateTextTokens(capped)
        val preview = capped
            .replace(WHITESPACE_COLLAPSE_RE, " ")
            .trim()
            .take(180)
        val finalNote = buildString {
            if (note.isNotBlank()) append(note)
            if (truncated && !note.contains("节选")) {
                if (isNotEmpty()) append(" · ")
                append("超大文件已截断保留前 150 万字符")
            }
        }
        return ExtractedFileContent(
            text = capped,
            charCount = capped.length,
            estimatedTokens = tokens,
            pageCount = pageCount,
            previewSnippet = preview,
            isLikelyScannedPdf = false,
            truncatedAtExtraction = truncated,
            extractionNote = finalNote,
        )
    }

    private fun normalizeText(input: String): String {
        if (input.isEmpty()) return ""
        val unix = input.replace("\r\n", "\n").replace('\r', '\n')
        return MULTI_BLANK_LINES_RE.replace(unix, "\n\n").trim()
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sampleLen = min(bytes.size, 4096)
        if (sampleLen == 0) return false
        var nulCount = 0
        var ctrlCount = 0
        for (i in 0 until sampleLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) nulCount++
            else if (b < 0x09 || (b in 0x0E..0x1F)) ctrlCount++
        }
        if (nulCount > 0) return true
        return ctrlCount.toDouble() / sampleLen > 0.15
    }

    private fun readZipEntries(
        bytes: ByteArray,
        predicate: (String) -> Boolean,
    ): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        com.gugachat.app.core.util.StreamZipUtils.forEachZipEntry(
            zipBytes = bytes,
            skipMacOsMeta = false,
        ) { entryName, zis ->
            if (predicate(entryName)) {
                val data = com.gugachat.app.core.util.StreamZipUtils.run {
                    zis.readTruncatedBytes(MAX_ZIP_ENTRY_BYTES)
                }
                totalBytes += data.size
                if (totalBytes <= MAX_ZIP_ENTRY_BYTES * 3L) {
                    out[entryName] = data
                }
            }
        }
        return out
    }

    private fun parseXmlSafely(bytes: ByteArray): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull()

    private fun localTagName(node: Node): String =
        (node.localName ?: node.nodeName).substringAfter(':')

    private fun collectDocxParagraphText(node: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            val local = localTagName(n)
            when (local) {
                "t" -> sb.append(n.textContent.orEmpty())
                "tab" -> sb.append('\t')
                "br", "cr" -> sb.append('\n')
                else -> {
                    val ch = n.childNodes
                    for (i in 0 until ch.length) walk(ch.item(i))
                }
            }
        }
        walk(node)
        return sb.toString()
    }

    private fun appendDocxTable(tableNode: Node, out: StringBuilder) {
        val rows = (tableNode as? Element)?.getElementsByTagNameNS("*", "tr") ?: return
        for (r in 0 until rows.length) {
            if (out.length >= MAX_EXTRACTED_CHARS) break
            val rowEl = rows.item(r) as? Element ?: continue
            val cells = rowEl.getElementsByTagNameNS("*", "tc")
            val cols = ArrayList<String>(cells.length)
            for (c in 0 until cells.length) {
                val cellText = collectDocxParagraphText(cells.item(c)).replace('\n', ' ').trim()
                cols.add(cellText)
            }
            if (cols.any { it.isNotEmpty() }) {
                out.appendLine(cols.joinToString(" | "))
            }
        }
        out.appendLine()
    }

    private fun parseXlsxSharedStrings(bytes: ByteArray): List<String> {
        val doc = parseXmlSafely(bytes) ?: return emptyList()
        val siNodes = doc.getElementsByTagNameNS("*", "si")
        val list = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            list.add(collectTagTexts(siNodes.item(i), "t"))
        }
        return list
    }

    private fun parseXlsxSheetNames(bytes: ByteArray): List<String> {
        val doc = parseXmlSafely(bytes) ?: return emptyList()
        val sheetNodes = doc.getElementsByTagNameNS("*", "sheet")
        val list = ArrayList<String>(sheetNodes.length)
        for (i in 0 until sheetNodes.length) {
            val el = sheetNodes.item(i) as? Element ?: continue
            list.add(el.getAttribute("name").orEmpty())
        }
        return list
    }

    private fun firstChildText(parent: Element, localName: String): String? {
        val list = parent.getElementsByTagNameNS("*", localName)
        return if (list.length > 0) list.item(0).textContent else null
    }

    private fun collectTagTexts(node: Node, targetLocal: String): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (localTagName(n) == targetLocal) {
                sb.append(n.textContent.orEmpty())
            } else {
                val ch = n.childNodes
                for (i in 0 until ch.length) walk(ch.item(i))
            }
        }
        walk(node)
        return sb.toString()
    }

    private fun extractTrailingNumber(path: String): Int =
        TRAILING_XML_NUMBER_RE.find(path)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun decodeHtmlEntities(input: String): String {
        var s = input
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        s = NUMERIC_ENTITY_RE.replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0xFFFF }?.toChar()?.toString() ?: m.value
        }
        s = HEX_ENTITY_RE.replace(s) { m ->
            m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0xFFFF }?.toChar()?.toString() ?: m.value
        }
        return s
    }
}
