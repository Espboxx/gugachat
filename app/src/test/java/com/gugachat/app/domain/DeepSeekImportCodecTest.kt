package com.gugachat.app.domain

import com.gugachat.app.data.api.DeepSeekImportService
import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DeepSeekImportCodecTest {

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun sampleConversationsJson(): String = """
        [
          {
            "id": "conv-abc-1",
            "title": "火星基地规划",
            "inserted_at": "2026-01-10T09:00:00.000000+08:00",
            "updated_at": "2026-01-10T09:05:00.000000+08:00",
            "mapping": {
              "root": { "id": "root", "parent": null, "children": ["1"], "message": null },
              "1": {
                "id": "1",
                "parent": "root",
                "children": ["2a", "2b"],
                "message": {
                  "files": [{"file_name": "mars_map.png"}],
                  "model": "deepseek-reasoner",
                  "inserted_at": "2026-01-10T09:00:05.000000+08:00",
                  "fragments": [
                    { "type": "REQUEST", "content": "帮我设计一个火星前哨站" }
                  ]
                }
              },
              "2a": {
                "id": "2a",
                "parent": "1",
                "children": [],
                "message": {
                  "model": "deepseek-reasoner",
                  "inserted_at": "2026-01-10T09:01:00.000000+08:00",
                  "fragments": [
                    { "type": "THINK", "content": "初版思路：先看能源" },
                    { "type": "RESPONSE", "content": "初版方案：采用核电池与充气穹顶。" }
                  ]
                }
              },
              "2b": {
                "id": "2b",
                "parent": "1",
                "children": ["3"],
                "message": {
                  "model": "deepseek-reasoner",
                  "inserted_at": "2026-01-10T09:02:00.000000+08:00",
                  "fragments": [
                    { "type": "THINK", "content": "深度思考：辐射屏蔽和制氧优先" },
                    { "type": "RESPONSE", "content": "完整方案：利用熔岩管屏蔽辐射并部署 MOXIE 制氧。" }
                  ]
                }
              },
              "3": {
                "id": "3",
                "parent": "2b",
                "children": [],
                "message": {
                  "inserted_at": "2026-01-10T09:05:00.000000+08:00",
                  "fragments": [
                    { "type": "REQUEST", "content": "预算大概多少？" }
                  ]
                }
              }
            }
          }
        ]
    """.trimIndent()

    @Test
    fun parseZip_standardConversationsJson_preservesReasoningVariantsAndIdempotency() {
        val zip = buildZip(
            mapOf(
                "user.json" to """{"email":"test@example.com"}""",
                "conversations.json" to sampleConversationsJson(),
            ),
        )
        val bundle = DeepSeekImportCodec.parseBytes(zip)
        assertEquals("deepseek_zip_conversations", bundle.sourceFormat)
        assertEquals(1, bundle.items.size)

        val item = bundle.items.first()
        assertEquals("ds_conv-abc-1", item.conversation.id)
        assertEquals("火星基地规划", item.conversation.title)
        assertNull(item.conversation.model)
        assertTrue(item.hasReasoning)
        assertEquals(3, item.messages.size)

        assertEquals("user", item.messages[0].role)
        assertTrue(item.messages[0].content.contains("[附件: mars_map.png]"))
        assertTrue(item.messages[0].content.contains("帮我设计一个火星前哨站"))
        assertEquals(1, item.messages[0].seq)

        val assistant = item.messages[1]
        assertEquals("assistant", assistant.role)
        assertEquals("深度思考：辐射屏蔽和制氧优先", assistant.reasoning)
        assertEquals("完整方案：利用熔岩管屏蔽辐射并部署 MOXIE 制氧。", assistant.content)
        assertEquals("deepseek-reasoner", assistant.model)
        assertEquals(2, assistant.variants.size)
        assertEquals(1, assistant.activeVariantIndex)

        assertEquals("user", item.messages[2].role)
        assertEquals("预算大概多少？", item.messages[2].content)
        assertEquals(3, item.messages[2].seq)
        assertTrue(item.messages[2].tokenEstimate > 0)
    }

    @Test
    fun parseZip_v3ChatSessionsLayout_readsIndexAndSessions() {
        val zip = buildZip(
            mapOf(
                "manifest.json" to """{"export_format_version": 3}""",
                "chat_session_index.json" to """[{"id":"s-99","title":"Kotlin 协程笔记","updated_at":"2026-02-01T12:00:00Z"}]""",
                "chat_sessions/s-99.json" to """
                    {
                      "id": "s-99",
                      "chat_messages": [
                        { "message_id": "1", "role": "user", "content": "解释 Dispatchers.IO" },
                        { "message_id": "2", "role": "assistant", "reasoning_content": "先说线程池", "content": "Dispatchers.IO 面向阻塞 I/O 操作。", "model": "deepseek-chat" }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val bundle = DeepSeekImportCodec.parseBytes(zip)
        assertEquals("deepseek_zip_v3", bundle.sourceFormat)
        assertEquals(1, bundle.items.size)
        val item = bundle.items.first()
        assertEquals("ds_s-99", item.conversation.id)
        assertEquals("Kotlin 协程笔记", item.conversation.title)
        assertTrue(item.hasReasoning)
        assertEquals(2, item.messages.size)
        assertEquals("先说线程池", item.messages[1].reasoning)
        assertEquals("Dispatchers.IO 面向阻塞 I/O 操作。", item.messages[1].content)
    }

    @Test
    fun extractDownloadUrl_handlesSurroundingChineseTextAndInvalidInputs() {
        val raw = "您的 DeepSeek 数据导出已准备好，请复制链接下载：https://cdn.deepseek.com/export/deepseek_data.zip?token=abc123。"
        assertEquals(
            "https://cdn.deepseek.com/export/deepseek_data.zip?token=abc123",
            DeepSeekImportCodec.extractDownloadUrl(raw),
        )
        assertNull(DeepSeekImportCodec.extractDownloadUrl("没有链接的纯文本"))
    }

    @Test
    fun downloadAndParseLink_handlesRedirectZipAndRejectsHtmlLoginPage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val zipBytes = buildZip(mapOf("conversations.json" to sampleConversationsJson()))
            val finalUrl = server.url("/files/deepseek_data.zip").toString()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", finalUrl))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/zip")
                    .setBody(Buffer().write(zipBytes)),
            )

            val okClient = OkHttpClient()
            val bundle = DeepSeekImportService.downloadAndParseLink(
                httpClient = okClient,
                rawLinkInput = "下载链接：${server.url("/download")}",
            )
            assertEquals(1, bundle.items.size)
            assertEquals("火星基地规划", bundle.items.first().conversation.title)

            // HTML login wall -> DeepSeekLinkAuthRequiredException
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<!DOCTYPE html><html><body>Please login</body></html>"),
            )
            var authRequiredThrown = false
            try {
                DeepSeekImportService.downloadAndParseLink(okClient, server.url("/expired").toString())
            } catch (_: DeepSeekLinkAuthRequiredException) {
                authRequiredThrown = true
            }
            assertTrue(authRequiredThrown)
        } finally {
            server.shutdown()
        }
    }
}
