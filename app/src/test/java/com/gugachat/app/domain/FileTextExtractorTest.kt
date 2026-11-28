package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileTextExtractorTest {

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, body) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun decodeTextSmart_handlesUtf8BomAndGb18030Chinese() {
        val sample = "中文合同条款：第一条 双方约定在 2026 年完成交付。"
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sample.toByteArray(Charsets.UTF_8)
        assertEquals(sample, FileTextExtractor.decodeTextSmart(utf8Bom))

        val gbBytes = sample.toByteArray(Charset.forName("GB18030"))
        assertEquals(sample, FileTextExtractor.decodeTextSmart(gbBytes))
    }

    @Test
    fun extractFromBytes_stripsHtmlScriptsAndPreservesText() {
        val html = """
            <html><head><style>.a{color:red}</style><script>alert("xss")</script></head>
            <body><h1>季度报告</h1><p>营收同比增长 &amp; 利润率创新高。</p></body></html>
        """.trimIndent()
        val res = FileTextExtractor.extractFromBytes(html.toByteArray(Charsets.UTF_8), "report.html", "text/html")
        assertTrue(res.text.contains("季度报告"))
        assertTrue(res.text.contains("营收同比增长 & 利润率创新高"))
        assertFalse(res.text.contains("alert"))
        assertFalse(res.text.contains("color:red"))
    }

    @Test
    fun extractDocx_extractsParagraphsAndTables() {
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>项目需求说明书</w:t></w:r></w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>模块</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>负责人</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>文件解析引擎</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>小咕</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()
        val bytes = buildZip(mapOf("word/document.xml" to docXml))
        val res = FileTextExtractor.extractFromBytes(bytes, "spec.docx", "")
        assertTrue(res.text.contains("项目需求说明书"))
        assertTrue(res.text.contains("文件解析引擎"))
        assertTrue(res.text.contains("小咕"))
        assertTrue(res.estimatedTokens > 0)
    }

    @Test
    fun extractXlsx_resolvesSharedStringsAndSheetNames() {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheets><sheet name="销售明细" sheetId="1"/></sheets>
            </workbook>
        """.trimIndent()
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>产品名</t></si>
              <si><t>营收</t></si>
              <si><t>GuGuChat Pro</t></si>
            </sst>
        """.trimIndent()
        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>98500</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        val bytes = buildZip(
            mapOf(
                "xl/workbook.xml" to workbookXml,
                "xl/sharedStrings.xml" to sharedStringsXml,
                "xl/worksheets/sheet1.xml" to sheet1Xml,
            ),
        )
        val res = FileTextExtractor.extractFromBytes(bytes, "sales.xlsx", "")
        assertTrue(res.text.contains("## 销售明细"))
        assertTrue(res.text.contains("产品名 | 营收"))
        assertTrue(res.text.contains("GuGuChat Pro | 98500"))
        assertEquals(1, res.pageCount)
    }

    @Test
    fun extractPptx_ordersSlidesNumerically() {
        val slide1 = """
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>第一页：架构总览</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()
        val slide10 = """
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>第十页：防爆上下文压测</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
            </p:sld>
        """.trimIndent()
        val bytes = buildZip(
            mapOf(
                "ppt/slides/slide10.xml" to slide10,
                "ppt/slides/slide1.xml" to slide1,
            ),
        )
        val res = FileTextExtractor.extractFromBytes(bytes, "deck.pptx", "")
        assertEquals(2, res.pageCount)
        assertTrue(res.text.indexOf("第一页：架构总览") < res.text.indexOf("第十页：防爆上下文压测"))
    }

    @Test
    fun extractEpub_readsChapterHtml() {
        val ch1 = "<html><body><h2>第一章 启程</h2><p>晨雾笼罩着海港，蒸汽船发出低沉的鸣笛声。</p></body></html>"
        val bytes = buildZip(mapOf("OEBPS/Text/ch01.xhtml" to ch1))
        val res = FileTextExtractor.extractFromBytes(bytes, "novel.epub", "application/epub+zip")
        assertTrue(res.text.contains("第一章 启程"))
        assertTrue(res.text.contains("蒸汽船发出低沉的鸣笛声"))
    }

    @Test
    fun extractPdf_detectsScannedPdfAndNormalPdf() {
        val scanned = FileTextExtractor.extractFromBytes(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "scan.pdf",
            mimeType = "application/pdf",
            pdfExtractor = { PdfExtractionResult(text = "   ", pageCount = 5) },
        )
        assertTrue(scanned.isLikelyScannedPdf)
        assertEquals(5, scanned.pageCount)

        val normal = FileTextExtractor.extractFromBytes(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "paper.pdf",
            mimeType = "application/pdf",
            pdfExtractor = {
                PdfExtractionResult(
                    text = "[第 1 页]\n这是一篇关于大语言模型上下文预算与稀疏检索的详细论文正文内容，包含充分的可提取文本。",
                    pageCount = 12,
                )
            },
        )
        assertFalse(normal.isLikelyScannedPdf)
        assertEquals(12, normal.pageCount)
        assertTrue(normal.text.contains("稀疏检索"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractFromBytes_rejectsBinaryExecutable() {
        val binary = byteArrayOf(0x00, 0x01, 0x02, 0x00, 0x7F, 0x45, 0x4C, 0x46)
        FileTextExtractor.extractFromBytes(binary, "app.bin", "application/octet-stream")
    }

    @Test
    fun extractDocx_convertsHeadingStylesAndOutlineLvlToMarkdownHeadings() {
        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:styleId="a3">
                <w:name w:val="heading 2"/>
              </w:style>
            </w:styles>
        """.trimIndent()
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Title"/></w:pPr>
                  <w:r><w:t>恋综2.0后会有期</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>角色名单</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="a3"/></w:pPr>
                  <w:r><w:t>男嘉宾档案</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="标题 3"/></w:pPr>
                  <w:r><w:t>崔晨浩</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:outlineLvl w:val="2"/></w:pPr>
                  <w:r><w:t>Day 1 破冰流程</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:t>正文段落内容保持原样。</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val bytes = buildZip(
            mapOf(
                "word/document.xml" to docXml,
                "word/styles.xml" to stylesXml,
            ),
        )
        val res = FileTextExtractor.extractFromBytes(bytes, "show.docx", "")
        assertTrue(res.text.contains("# 恋综2.0后会有期"))
        assertTrue(res.text.contains("# 角色名单"))
        assertTrue(res.text.contains("## 男嘉宾档案"))
        assertTrue(res.text.contains("### 崔晨浩"))
        assertTrue(res.text.contains("### Day 1 破冰流程"))
        assertTrue(res.text.contains("正文段落内容保持原样。"))
        assertFalse(res.text.contains("# 正文段落内容保持原样。"))
    }

    @Test
    fun extractEpub_preservesHtmlHeadingsAsMarkdown() {
        val ch1 = "<html><body><h1>卷一 雾港</h1><h2>第一章 启程</h2><h3>甲板密谈</h3><p>晨雾笼罩着海港，蒸汽船发出低沉的鸣笛声。</p></body></html>"
        val bytes = buildZip(mapOf("OEBPS/Text/ch01.xhtml" to ch1))
        val res = FileTextExtractor.extractFromBytes(bytes, "novel.epub", "application/epub+zip")
        assertTrue(res.text.contains("# 卷一 雾港"))
        assertTrue(res.text.contains("## 第一章 启程"))
        assertTrue(res.text.contains("### 甲板密谈"))
        assertTrue(res.text.contains("蒸汽船发出低沉的鸣笛声"))
    }
}
