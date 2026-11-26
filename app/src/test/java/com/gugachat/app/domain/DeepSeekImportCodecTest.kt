package com.gugachat.app.domain

import com.gugachat.app.data.api.DeepSeekImportService
import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import com.gugachat.app.ui.chat.isPlainTextCodeFence
import com.gugachat.app.ui.chat.shouldDefaultWrapCodeFence
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parseZip_v3TreeWithParentIdsAndRegeneratedSiblings_collapsesToActiveBranchAndVariants() {
        val v3SessionJson = """
            {
              "id": "sess-tree-1",
              "title": "深度树形对话",
              "current_message_id": 8,
              "messages": [
                {
                  "id": 1,
                  "parent_id": null,
                  "role": "user",
                  "inserted_at": "2026-04-01T10:00:00Z",
                  "fragments": [{ "type": "text", "content": "抱..." }]
                },
                {
                  "id": 2,
                  "parent_id": 1,
                  "role": "assistant",
                  "inserted_at": "2026-04-01T10:00:05Z",
                  "fragments": [
                    { "type": "think", "content": "思考1" },
                    { "type": "text", "content": "回复变体一" }
                  ]
                },
                {
                  "id": 3,
                  "parent_id": 1,
                  "role": "assistant",
                  "inserted_at": "2026-04-01T10:00:10Z",
                  "fragments": [
                    { "type": "think", "content": "思考2" },
                    { "type": "text", "content": "回复变体二（继续分支）" }
                  ]
                },
                {
                  "id": 4,
                  "parent_id": 1,
                  "role": "assistant",
                  "inserted_at": "2026-04-01T10:00:15Z",
                  "fragments": [
                    { "type": "think", "content": "思考3" },
                    { "type": "text", "content": "回复变体三（未继续）" }
                  ]
                },
                {
                  "id": 5,
                  "parent_id": 3,
                  "role": "user",
                  "inserted_at": "2026-04-01T10:01:00Z",
                  "fragments": [{ "type": "text", "content": "亲亲" }]
                },
                {
                  "id": 6,
                  "parent_id": 3,
                  "role": "user",
                  "inserted_at": "2026-04-01T10:00:50Z",
                  "fragments": [{ "type": "text", "content": "废弃的编辑分支" }]
                },
                {
                  "id": 7,
                  "parent_id": 5,
                  "role": "assistant",
                  "inserted_at": "2026-04-01T10:01:05Z",
                  "fragments": [
                    { "type": "think", "content": "亲亲思考1" },
                    { "type": "text", "content": "亲亲回复1" }
                  ]
                },
                {
                  "id": 8,
                  "parent_id": 5,
                  "role": "assistant",
                  "inserted_at": "2026-04-01T10:01:12Z",
                  "fragments": [
                    { "type": "think", "content": "亲亲思考2" },
                    { "type": "text", "content": "亲亲回复2" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val zip = buildZip(
            mapOf(
                "manifest.json" to """{"export_format_version": 3}""",
                "chat_session_index.json" to """[{"id":"sess-tree-1","title":"深度树形对话","updated_at":"2026-04-01T10:01:12Z"}]""",
                "chat_sessions/sess-tree-1.json" to v3SessionJson,
            ),
        )

        val bundle = DeepSeekImportCodec.parseBytes(zip)
        assertEquals(1, bundle.items.size)
        val msgs = bundle.items.first().messages
        // Strictly 4 turns on active branch: user("抱...") -> assistant(id=3) -> user("亲亲") -> assistant(id=8)
        assertEquals(4, msgs.size)
        assertEquals(listOf("user", "assistant", "user", "assistant"), msgs.map { it.role })
        assertEquals("抱...", msgs[0].content)
        assertEquals("回复变体二（继续分支）", msgs[1].content)
        assertEquals("思考2", msgs[1].reasoning)
        assertEquals(3, msgs[1].variants.size)
        assertEquals(1, msgs[1].activeVariantIndex)
        assertEquals("亲亲", msgs[2].content)
        assertEquals("亲亲回复2", msgs[3].content)
        assertEquals(2, msgs[3].variants.size)
        assertEquals(1, msgs[3].activeVariantIndex)
    }

    @Test
    fun parseMapping_withoutCurrentNode_followsDeepActiveBranchInsteadOfDeadEndLastSibling() {
        val mappingJson = """
            [
              {
                "id": "conv-branch-pick",
                "title": "多分支选择",
                "mapping": {
                  "root": { "id": "root", "parent": null, "children": ["1"], "message": null },
                  "1": {
                    "id": "1",
                    "parent": "root",
                    "children": ["2a", "2b"],
                    "message": {
                      "inserted_at": "2026-04-01T10:00:00Z",
                      "fragments": [{ "type": "REQUEST", "content": "第一句" }]
                    }
                  },
                  "2a": {
                    "id": "2a",
                    "parent": "1",
                    "children": ["3"],
                    "message": {
                      "inserted_at": "2026-04-01T10:00:05Z",
                      "fragments": [{ "type": "RESPONSE", "content": "延续分支A" }]
                    }
                  },
                  "2b": {
                    "id": "2b",
                    "parent": "1",
                    "children": [],
                    "message": {
                      "inserted_at": "2026-04-01T10:00:08Z",
                      "fragments": [{ "type": "RESPONSE", "content": "未延续的重试B" }]
                    }
                  },
                  "3": {
                    "id": "3",
                    "parent": "2a",
                    "children": ["4"],
                    "message": {
                      "inserted_at": "2026-04-01T10:01:00Z",
                      "fragments": [{ "type": "REQUEST", "content": "继续聊" }]
                    }
                  },
                  "4": {
                    "id": "4",
                    "parent": "3",
                    "children": [],
                    "message": {
                      "inserted_at": "2026-04-01T10:01:05Z",
                      "fragments": [{ "type": "RESPONSE", "content": "后续回复" }]
                    }
                  }
                }
              }
            ]
        """.trimIndent()

        val bundle = DeepSeekImportCodec.parseJsonText(mappingJson)
        val msgs = bundle.items.first().messages
        assertEquals(4, msgs.size)
        assertEquals("延续分支A", msgs[1].content)
        assertEquals(2, msgs[1].variants.size)
        assertEquals(0, msgs[1].activeVariantIndex)
        assertEquals("继续聊", msgs[2].content)
        assertEquals("后续回复", msgs[3].content)
    }

    @Test
    fun parseFlatMessages_withoutParentLinks_foldsConsecutiveAssistantRepliesIntoVariants() {
        val flatJson = """
            {
              "id": "flat-regen",
              "title": "扁平重Roll对话",
              "chat_messages": [
                { "message_id": "1", "role": "user", "content": "抱一下" },
                { "message_id": "2", "role": "assistant", "reasoning_content": "想1", "content": "回复一" },
                { "message_id": "3", "role": "assistant", "reasoning_content": "想2", "content": "回复二" },
                { "message_id": "4", "role": "assistant", "reasoning_content": "想3", "content": "回复三" },
                { "message_id": "5", "role": "user", "content": "亲亲" },
                { "message_id": "6", "role": "assistant", "content": "亲亲回复一" },
                { "message_id": "7", "role": "assistant", "content": "亲亲回复二" }
              ]
            }
        """.trimIndent()

        val bundle = DeepSeekImportCodec.parseJsonText(flatJson)
        val msgs = bundle.items.first().messages
        assertEquals(4, msgs.size)
        assertEquals(listOf("user", "assistant", "user", "assistant"), msgs.map { it.role })
        assertEquals("回复三", msgs[1].content)
        assertEquals(3, msgs[1].variants.size)
        assertEquals(2, msgs[1].activeVariantIndex)
        assertEquals("亲亲回复二", msgs[3].content)
        assertEquals(2, msgs[3].variants.size)
        assertEquals(1, msgs[3].activeVariantIndex)
    }

    @Test
    fun codeFenceWrappingHeuristic_wrapsPlainTextAndProseButKeepsCodeScrollable() {
        assertTrue(isPlainTextCodeFence("text", "他肯主动要亲，这两天那口气算是过去了。"))
        assertTrue(shouldDefaultWrapCodeFence("text", "他肯主动要亲，这两天那口气算是过去了。"))
        assertTrue(shouldDefaultWrapCodeFence("plaintext", "Long plain note line"))
        assertTrue(shouldDefaultWrapCodeFence("markdown", "# Heading\n中文长段内心独白"))
        assertTrue(shouldDefaultWrapCodeFence("", "他总算肯往回走了。这两天的事先翻过去，别再提。"))

        assertFalse(isPlainTextCodeFence("kotlin", "val x = 1\nprintln(x)"))
        assertFalse(shouldDefaultWrapCodeFence("kotlin", "val x = 1\nprintln(x)"))
        assertFalse(shouldDefaultWrapCodeFence("python", "def hello():\n    print('你好世界')"))
        assertFalse(shouldDefaultWrapCodeFence("json", "{\"key\": \"value\"}"))
    }

    @Test
    fun parseZip_v3RealFragments_handlesNestedFilesStripsContentRiskAndPreservesTemplateFallback() {
        val sessionJson = """
            {
              "id": "sess-v3-real",
              "title": "实测片段解析",
              "pinned": true,
              "current_message_id": 4,
              "messages": [
                {
                  "id": 1,
                  "parent_id": null,
                  "role": "USER",
                  "inserted_at": "2026-11-16T23:32:52.139Z",
                  "fragments": [
                    {
                      "type": "FILE",
                      "files": [
                        { "id": "f1", "file_name": "IMG_6987.webp", "is_image": true }
                      ]
                    }
                  ]
                },
                {
                  "id": 2,
                  "parent_id": 1,
                  "role": "ASSISTANT",
                  "inserted_at": "2026-11-16T23:33:05.000Z",
                  "fragments": [
                    { "type": "THINK", "content": "先观察图片构图", "elapsed_secs": 3.2 },
                    { "type": "TOOL_SEARCH", "queries": ["test"], "results": [] },
                    { "type": "TOOL_OPEN", "title": "Blog", "url": "https://example.com" },
                    { "type": "THINK", "content": "再整理提示词" },
                    { "type": "RESPONSE", "content": "这是反推后的提示词内容。" },
                    { "type": "CONTENT_RISK", "style": "WARNING", "content": "This response is AI-generated, for reference only." }
                  ]
                },
                {
                  "id": 3,
                  "parent_id": 2,
                  "role": "USER",
                  "inserted_at": "2026-11-16T23:34:00.000Z",
                  "fragments": [
                    { "type": "FILE", "files": [{ "file_name": "docker.txt", "is_image": false }] },
                    { "type": "REQUEST", "content": "帮我看下日志" }
                  ]
                },
                {
                  "id": 4,
                  "parent_id": 3,
                  "role": "ASSISTANT",
                  "status": "TIMEOUT",
                  "inserted_at": "2026-11-16T23:34:15.000Z",
                  "fragments": [
                    { "type": "TEMPLATE_RESPONSE", "content": "服务器繁忙，请稍后再试。" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val zip = buildZip(
            mapOf(
                "manifest.json" to """{"export_format_version":3}""",
                "chat_sessions/sess-v3-real.json" to sessionJson,
            ),
        )
        val bundle = ExternalImportCodec.parseBytes(zip, fileName = "deepseek_data-2026-11-26.zip")
        assertEquals(ExternalImportSource.DEEPSEEK, bundle.source)
        assertEquals("deepseek_zip_v3", bundle.sourceFormat)
        assertEquals(1, bundle.items.size)

        val item = bundle.items.first()
        assertTrue(item.conversation.pinned)
        assertEquals(4, item.messages.size)
        // Image-only user message preserved
        assertEquals("[图片: IMG_6987.webp]", item.messages[0].content)
        // Assistant combines multiple THINK blocks, keeps RESPONSE, strips CONTENT_RISK & TOOL_OPEN
        assertEquals("先观察图片构图\n\n再整理提示词", item.messages[1].reasoning)
        assertEquals("这是反推后的提示词内容。", item.messages[1].content)
        assertFalse(item.messages[1].content.contains("AI-generated"))
        // File + Request combined cleanly
        assertTrue(item.messages[2].content.contains("[附件: docker.txt]"))
        assertTrue(item.messages[2].content.contains("帮我看下日志"))
        // TEMPLATE_RESPONSE preserved as fallback when no RESPONSE exists
        assertEquals("服务器繁忙，请稍后再试。", item.messages[3].content)
    }

    @Test
    fun parseZip_legacyCsvExport_parsesMultilineQuotedRowsAndSplitsByTimeGap() {
        val chatCsv = "id,time,role,content\n"
        val coderCsv = buildString {
            appendLine("id,time,role,content")
            appendLine("101,2024-08-07 18:24:55.724797,user,\"第一段代码，含逗号与\"\"引号\"\"\n第二行\"")
            appendLine("102,2024-08-07 18:24:59.100000,assistant,\"```kotlin\nprintln(\"\"ok\"\")\n```\"")
            appendLine("201,2024-08-08 15:53:38.080174,user,\"//\n#include <iostream>\n隔天的新问题\"")
            appendLine("202,2024-08-08 15:53:40.000000,assistant,隔天的回复")
        }
        val zip = buildZip(
            mapOf(
                "chat.csv" to chatCsv,
                "coder.csv" to coderCsv,
            ),
        )

        val bundle = ExternalImportCodec.parseBytes(zip, fileName = "old-version_conversations.zip")
        assertEquals(ExternalImportSource.DEEPSEEK, bundle.source)
        assertEquals("deepseek_zip_legacy_csv", bundle.sourceFormat)
        assertEquals(2, bundle.items.size)

        // Sorted descending by updatedAt: newer day first; skips bare "//" and preserves "#include"
        val newer = bundle.items[0]
        assertEquals("#include <iostream>", newer.conversation.title)
        assertEquals(2, newer.messages.size)
        assertEquals("deepseek-coder", newer.messages[1].model)

        val older = bundle.items[1]
        assertEquals(2, older.messages.size)
        assertEquals("第一段代码，含逗号与\"引号\"\n第二行", older.messages[0].content)
        assertEquals("```kotlin\nprintln(\"ok\")\n```", older.messages[1].content)
    }
}
