package com.gugachat.app.domain.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class NovelEpubExportTest {

    private data class ZipArchiveItem(
        val name: String,
        val method: Int,
        val size: Long,
        val compressedSize: Long,
        val crc: Long,
        val bytes: ByteArray,
    )

    private fun readZipItems(epubBytes: ByteArray): List<ZipArchiveItem> {
        val items = ArrayList<ZipArchiveItem>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val bytes = zis.readBytes()
                items.add(
                    ZipArchiveItem(
                        name = entry.name,
                        method = entry.method,
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                        crc = entry.crc,
                        bytes = bytes,
                    ),
                )
                zis.closeEntry()
            }
        }
        return items
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    @Test
    fun exportEpubBytes_writesStrictOcfMimetypeFirstEntryAndValidXmlChapters() {
        val novel = NovelConfig(
            title = "剑与星 & 沧海 <卷一>",
            synopsis = "少年握紧 \"残剑\" 与 '命运'。",
            genre = "奇幻冒险",
            worldview = "九霄灵脉纵横。",
            mainOutline = "初出茅庐 -> 问道长生",
        )
        val conv = ChatConversation(
            id = "conv-epub-1",
            title = novel.title,
            mode = ConversationMode.novel,
            novel = novel,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_500_000L,
        )
        val ch1 = NovelChapter(
            id = "ch1",
            conversationId = conv.id,
            order = 1,
            title = "第一章：风起 & 云涌",
            startSeq = 1,
            endSeq = 2,
        )
        val ch2 = NovelChapter(
            id = "ch2",
            conversationId = conv.id,
            order = 2,
            title = "第二章：破晓 <微光>",
            startSeq = 3,
            endSeq = 4,
        )
        val messages = listOf(
            ChatMessage(id = "u1", conversationId = conv.id, role = "user", content = "开章", createdAt = 1L, seq = 1),
            ChatMessage(
                id = "a1",
                conversationId = conv.id,
                role = "assistant",
                content = "夜雨落瓦，A < B & C > D。\n\n他推开木门。<status_hud>{\"time\":\"子夜\"}</status_hud><choices>[\"继续\"]</choices>",
                createdAt = 2L,
                seq = 2,
            ),
            ChatMessage(id = "u2", conversationId = conv.id, role = "user", content = "次章", createdAt = 3L, seq = 3),
            ChatMessage(
                id = "a2",
                conversationId = conv.id,
                role = "assistant",
                content = "　　晨曦刺破薄雾。\n第二段正文。",
                createdAt = 4L,
                seq = 4,
            ),
        )
        val lore = listOf(
            NovelLoreEntry(
                id = "lore1",
                conversationId = conv.id,
                category = "item",
                title = "星陨残剑",
                content = "锋刃刻着 \"古篆\" & <符文>。",
                keywords = listOf("残剑", "星陨"),
                alwaysActive = true,
                priority = 90,
            ),
        )
        val fakeCover = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02)

        val epubBytes = NovelExport.exportEpubBytes(
            conv = conv,
            chapters = listOf(ch1, ch2),
            messages = messages,
            lore = lore,
            coverImageBytes = fakeCover,
            coverMimeType = "image/png",
        )
        val items = readZipItems(epubBytes)
        assertTrue(items.isNotEmpty())

        // 1. Entry #1 MUST be uncompressed STORED `mimetype` with exact CRC32
        val first = items.first()
        assertEquals("mimetype", first.name)
        assertEquals(ZipEntry.STORED, first.method)
        assertEquals("application/epub+zip", first.bytes.toString(Charsets.UTF_8))
        val expectedMimeBytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
        val expectedCrc = CRC32().apply { update(expectedMimeBytes) }.value
        assertEquals(expectedMimeBytes.size.toLong(), first.size)
        assertEquals(expectedMimeBytes.size.toLong(), first.compressedSize)
        assertEquals(expectedCrc, first.crc)

        val byName = items.associateBy { it.name }

        // 2. container.xml points to OEBPS/content.opf
        val containerItem = byName["META-INF/container.xml"]
        assertNotNull(containerItem)
        val containerDoc = parseXml(containerItem!!.bytes)
        val rootfiles = containerDoc.getElementsByTagName("rootfile")
        assertEquals(1, rootfiles.length)
        assertEquals("OEBPS/content.opf", rootfiles.item(0).attributes.getNamedItem("full-path").nodeValue)

        // 3. OEBPS/content.opf valid EPUB 3.0 package with Dublin Core metadata & cover meta
        val opfItem = byName["OEBPS/content.opf"]
        assertNotNull(opfItem)
        val opfDoc = parseXml(opfItem!!.bytes)
        assertEquals("3.0", opfDoc.documentElement.getAttribute("version"))
        val opfText = opfItem.bytes.toString(Charsets.UTF_8)
        assertTrue(opfText.contains("<dc:language>zh-CN</dc:language>"))
        assertTrue(opfText.contains("剑与星 &amp; 沧海 &lt;卷一&gt;"))
        assertTrue(opfText.contains("""<meta name="cover" content="cover-image"/>"""))
        assertTrue(opfText.contains("""href="ch_001.xhtml""""))
        assertTrue(opfText.contains("""href="ch_002.xhtml""""))
        assertTrue(opfText.contains("""href="lore.xhtml""""))

        // 4. nav.xhtml & toc.ncx valid XML
        val navItem = byName["OEBPS/nav.xhtml"]
        assertNotNull(navItem)
        val navDoc = parseXml(navItem!!.bytes)
        assertTrue(navDoc.getElementsByTagName("nav").length >= 1)
        val navText = navItem.bytes.toString(Charsets.UTF_8)
        assertTrue(navText.contains("epub:type=\"toc\""))
        assertTrue(navText.contains("ch_001.xhtml"))

        val ncxItem = byName["OEBPS/toc.ncx"]
        assertNotNull(ncxItem)
        val ncxDoc = parseXml(ncxItem!!.bytes)
        assertEquals(3, ncxDoc.getElementsByTagName("navPoint").length)

        // 5. style.css
        val cssItem = byName["OEBPS/style.css"]
        assertNotNull(cssItem)
        val cssText = cssItem!!.bytes.toString(Charsets.UTF_8)
        assertTrue(cssText.contains("text-indent: 2em;"))
        assertTrue(cssText.contains("line-height: 1.8;"))
        assertTrue(cssText.contains("margin: 0.6em 0;"))

        // 6. Chapter XHTML documents valid XML, tags stripped, entities escaped
        val ch1Item = byName["OEBPS/ch_001.xhtml"]
        val ch2Item = byName["OEBPS/ch_002.xhtml"]
        assertNotNull(ch1Item)
        assertNotNull(ch2Item)
        val ch1Doc = parseXml(ch1Item!!.bytes)
        val ch2Doc = parseXml(ch2Item!!.bytes)
        assertEquals(2, ch1Doc.getElementsByTagName("p").length)
        assertEquals(2, ch2Doc.getElementsByTagName("p").length)
        val ch1Text = ch1Item.bytes.toString(Charsets.UTF_8)
        assertTrue(ch1Text.contains("A &lt; B &amp; C &gt; D"))
        assertFalse(ch1Text.contains("<status_hud>"))
        assertFalse(ch1Text.contains("<choices>"))

        // 7. Cover image & cover.xhtml & lore.xhtml
        val coverImg = byName["OEBPS/cover.png"]
        assertNotNull(coverImg)
        assertArrayEquals(fakeCover, coverImg!!.bytes)
        val coverXhtml = byName["OEBPS/cover.xhtml"]
        assertNotNull(coverXhtml)
        parseXml(coverXhtml!!.bytes)

        val loreItem = byName["OEBPS/lore.xhtml"]
        assertNotNull(loreItem)
        parseXml(loreItem!!.bytes)
        val loreText = loreItem.bytes.toString(Charsets.UTF_8)
        assertTrue(loreText.contains("星陨残剑"))
        assertTrue(loreText.contains("九霄灵脉纵横"))
    }

    @Test
    fun exportEpubBytes_handlesUnchapteredMessagesEnglishLocaleAndNoOptionalFiles() {
        val conv = ChatConversation(
            id = "conv-en-2",
            title = "The Glass Observatory",
            mode = ConversationMode.novel,
            novel = NovelConfig(title = "The Glass Observatory"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )
        val messages = listOf(
            ChatMessage(id = "u1", conversationId = conv.id, role = "user", content = "Begin", createdAt = 1L, seq = 1),
            ChatMessage(
                id = "a1",
                conversationId = conv.id,
                role = "assistant",
                content = "Snow fell softly over the dome.\n\nElena adjusted the brass lens.",
                createdAt = 2L,
                seq = 2,
            ),
        )

        val epubBytes = NovelExport.exportEpubBytes(
            conv = conv,
            chapters = emptyList(),
            messages = messages,
        )
        val byName = readZipItems(epubBytes).associateBy { it.name }

        assertTrue(byName.containsKey("OEBPS/ch_001.xhtml"))
        assertFalse(byName.containsKey("OEBPS/cover.xhtml"))
        assertFalse(byName.containsKey("OEBPS/lore.xhtml"))

        val opfText = byName["OEBPS/content.opf"]!!.bytes.toString(Charsets.UTF_8)
        assertTrue(opfText.contains("<dc:language>en</dc:language>"))
        assertFalse(opfText.contains("""name="cover""""))

        val ch1Doc = parseXml(byName["OEBPS/ch_001.xhtml"]!!.bytes)
        assertEquals(2, ch1Doc.getElementsByTagName("p").length)
    }
}
