package com.gugachat.app.domain

import com.gugachat.app.data.api.DeepSeekLinkAuthRequiredException
import com.gugachat.app.data.api.ExternalImportService
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import com.gugachat.app.domain.chat.RpCardCodec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExternalImportCodecTest {

    // Minimal valid 1x1 transparent PNG
    private val minimalPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
    )

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun parseBytes_sillyTavernPngCard_extractsRpConfigGreetingAndLore() {
        val cardJson = """
            {
              "spec": "chara_card_v3",
              "spec_version": "3.0",
              "data": {
                "name": "艾琳娜",
                "description": "银发星象师，守望古老天文塔。",
                "personality": "冷静温柔、略带神秘",
                "scenario": "流星雨落下的午夜塔顶",
                "first_mes": "「你终于来了，旅人。今晚的星轨已经偏移。」",
                "character_book": {
                  "name": "天文塔秘录",
                  "entries": [
                    {
                      "keys": ["星盘", "星轨"],
                      "comment": "古老星盘",
                      "content": "黄铜铸成的三环星盘，可预兆灾厄。",
                      "enabled": true,
                      "constant": false,
                      "priority": 60
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val cardPng = RpCardCodec.embedCardJsonInPng(minimalPng, cardJson)

        val bundle = ExternalImportCodec.parseBytes(cardPng, fileName = "elena_v3.png")
        assertEquals(ExternalImportSource.SILLYTAVERN, bundle.source)
        assertEquals("sillytavern_card_v3", bundle.sourceFormat)
        assertEquals(1, bundle.items.size)

        val item = bundle.items.first()
        assertEquals(ConversationMode.rp, item.conversation.mode)
        assertEquals("艾琳娜", item.conversation.title)
        assertNotNull(item.conversation.rp)
        assertEquals(1, item.messages.size)
        assertTrue(item.messages.first().content.contains("星轨已经偏移"))
        assertEquals(1, item.loreEntries.size)
        assertEquals("古老星盘", item.loreEntries.first().title)
    }

    @Test
    fun parseBytes_sillyTavernStandaloneLorebook_importsEntries() {
        val loreJson = """
            {
              "name": "霜月王国世界书",
              "entries": {
                "0": {
                  "key": ["霜月", "王都"],
                  "comment": "霜月王都",
                  "content": "常年飘雪的北方城邦，以晶石供暖。",
                  "constant": true,
                  "order": 90
                },
                "1": {
                  "key": ["赤焰骑士团"],
                  "comment": "骑士团",
                  "content": "守护边境冰原的精锐军团。",
                  "disable": false,
                  "order": 40
                }
              }
            }
        """.trimIndent()

        val bundle = ExternalImportCodec.parseBytes(
            loreJson.toByteArray(Charsets.UTF_8),
            fileName = "frostmoon_world.json",
        )
        assertEquals(ExternalImportSource.SILLYTAVERN, bundle.source)
        assertEquals("sillytavern_lorebook", bundle.sourceFormat)
        val item = bundle.items.first()
        assertEquals("霜月王国世界书", item.conversation.title)
        assertEquals(ConversationMode.rp, item.conversation.mode)
        assertEquals(2, item.loreEntries.size)
        assertTrue(item.loreEntries.any { it.title == "霜月王都" && it.alwaysActive })
    }

    @Test
    fun parseBytes_sillyTavernJsonl_preservesSwipesReasoningAndPlayerName() {
        val jsonl = listOf(
            """{"user_name":"阿远","character_name":"露西","create_date":"2026-03-01T20:00:00Z"}""",
            """{"name":"露西","is_user":false,"mes":"夜城的霓虹今晚格外刺眼。","send_date":1772395205}""",
            """{"name":"阿远","is_user":true,"mes":"芯片已经拿到手了。","send_date":1772395215}""",
            """{"name":"露西","is_user":false,"mes":"版本二：干得漂亮，立刻撤离！","swipe_id":1,"swipes":["版本一：小心点，荒坂在附近。","版本二：干得漂亮，立刻撤离！"],"extra":{"reasoning":"保持赛博朋克紧凑节奏","model":"claude-3-7-sonnet"},"send_date":1772395225}""",
        ).joinToString("\n")

        val bundle = ExternalImportCodec.parseBytes(
            jsonl.toByteArray(Charsets.UTF_8),
            fileName = "Lucy - 2026-03-01.jsonl",
        )
        assertEquals(ExternalImportSource.SILLYTAVERN, bundle.source)
        assertEquals("sillytavern_jsonl", bundle.sourceFormat)
        val item = bundle.items.first()
        assertEquals("露西", item.conversation.title)
        assertEquals("阿远", item.conversation.rp?.playerName)
        assertEquals(3, item.messages.size)
        assertTrue(item.hasReasoning)

        val last = item.messages.last()
        assertEquals("assistant", last.role)
        assertEquals("保持赛博朋克紧凑节奏", last.reasoning)
        assertEquals(2, last.variants.size)
        assertEquals(1, last.activeVariantIndex)
        assertEquals("claude-3-7-sonnet", last.model)
    }

    @Test
    fun parseBytes_chatboxJsonBackup_restoresSystemPromptContentPartsAndReasoning() {
        val chatboxJson = """
            {
              "settings": { "theme": "dark" },
              "chat-sessions": [
                {
                  "id": "session-old",
                  "name": "SQL 调优记录",
                  "updatedAt": 1700000000000,
                  "messages": [
                    { "id": "m0", "role": "system", "content": "你是资深数据库架构师。" },
                    { "id": "m1", "role": "user", "contentParts": [{ "type": "text", "text": "如何优化慢查询？" }], "timestamp": 1700000001000 },
                    { "id": "m2", "role": "assistant", "content": "先执行 EXPLAIN ANALYZE 检查索引命中。", "reasoningContent": "先定位全表扫描", "model": "deepseek-r1", "timestamp": 1700000005000 }
                  ]
                },
                {
                  "id": "session-new",
                  "name": "Compose 动画",
                  "updatedAt": 1710000000000,
                  "messages": [
                    { "id": "n1", "role": "user", "content": "animateFloatAsState 怎么用？", "timestamp": 1710000001000 },
                    { "id": "n2", "role": "assistant", "content": "直接声明 by animateFloatAsState(targetValue)。", "timestamp": 1710000002000 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val bundle = ExternalImportCodec.parseBytes(
            chatboxJson.toByteArray(Charsets.UTF_8),
            fileName = "chatbox-exported-data.json",
        )
        assertEquals(ExternalImportSource.CHATBOX, bundle.source)
        assertEquals(2, bundle.items.size)
        // Sorted descending by updatedAt
        assertEquals("Compose 动画", bundle.items[0].conversation.title)

        val sqlItem = bundle.items[1]
        assertEquals("cb_session-old", sqlItem.conversation.id)
        assertEquals("你是资深数据库架构师。", sqlItem.conversation.systemPrompt)
        assertEquals(2, sqlItem.messages.size)
        assertTrue(sqlItem.hasReasoning)
        assertEquals("先定位全表扫描", sqlItem.messages[1].reasoning)
        assertEquals("deepseek-r1", sqlItem.messages[1].model)
    }

    @Test
    fun parsePastedScript_chengguangText_extractsChaptersCastChoicesAndSupportsModeSwitch() {
        val script = """
            《雨夜偏殿》

            第一章 雨落长阶
            旁白：秋雨淅沥，烛火在偏殿摇曳不定。
            【沈清雪】大人深夜来访，所为何事？
            陆沉：城外密报刚至，我必须当面告知殿下。

            第二章 暗潮涌动
            【沈清雪】既如此，你打算如何应对？
            选项：
            1. 交出密信，请她定夺
            2. 隐瞒半句，试探她的反应
        """.trimIndent()

        val bundle = ExternalImportCodec.parsePastedScript(script)
        assertEquals(ExternalImportSource.CHENGGUANG, bundle.source)
        val item = bundle.items.first()
        assertEquals("雨夜偏殿", item.conversation.title)
        assertEquals(ConversationMode.rp, item.conversation.mode)
        assertEquals(2, item.messages.size)
        assertEquals(2, item.novelChapters.size)

        val castNames = item.conversation.rp?.cast?.map { it.name }.orEmpty()
        assertTrue(castNames.contains("沈清雪"))
        assertTrue(castNames.contains("陆沉"))
        assertFalse(castNames.contains("旁白"))

        val choices = item.messages.last().rpChoices
        assertEquals(2, choices.size)
        assertEquals("交出密信，请她定夺", choices[0].label)
        assertEquals("隐瞒半句，试探她的反应", choices[1].label)

        // Switching target mode to Novel preserves all parsed chapters!
        val asNovel = ExternalImportCodec.applyTargetMode(item, ConversationMode.novel)
        assertEquals(ConversationMode.novel, asNovel.conversation.mode)
        assertEquals("《雨夜偏殿》", asNovel.conversation.title)
        assertEquals(2, asNovel.novelChapters.size)
        assertNotNull(asNovel.conversation.novel)
    }

    @Test
    fun parseBytes_chengguangStructuredJson_extractsScenesAndChoices() {
        val json = """
            {
              "id": "palace_01",
              "title": "金銮旧梦",
              "worldview": "大梁永安三年，朝堂暗流涌动。",
              "characters": [
                { "name": "萧景珩", "role": "companion", "personality": "深沉克制", "affinity": 35 }
              ],
              "scenes": [
                {
                  "title": "御花园初遇",
                  "narration": "梅香浮雪，朱墙映寒光。",
                  "dialogues": [
                    { "speaker": "萧景珩", "text": "风大，披上这件斗篷。" }
                  ],
                  "choices": [
                    { "label": "接过斗篷道谢" },
                    { "label": "婉拒并转身离去" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val bundle = ExternalImportCodec.parseBytes(json.toByteArray(Charsets.UTF_8), fileName = "story.json")
        assertEquals(ExternalImportSource.CHENGGUANG, bundle.source)
        val item = bundle.items.first()
        assertEquals("金銮旧梦", item.conversation.title)
        assertEquals(ConversationMode.rp, item.conversation.mode)
        assertEquals(1, item.conversation.rp?.cast?.size)
        assertEquals(35, item.conversation.rp?.cast?.first()?.affinity)
        assertEquals(2, item.messages.first().rpChoices.size)
    }

    @Test
    fun parseBytes_claudeAndChatGptExports_detectCorrectSourcesAndReasoning() {
        val claudeJson = """
            [
              {
                "uuid": "cl-101",
                "name": "Rust 生命周期",
                "created_at": "2026-04-01T10:00:00Z",
                "updated_at": "2026-04-01T10:02:00Z",
                "chat_messages": [
                  { "uuid": "m1", "sender": "human", "text": "什么是 'a 生命周期？", "created_at": "2026-04-01T10:00:05Z" },
                  {
                    "uuid": "m2",
                    "sender": "assistant",
                    "created_at": "2026-04-01T10:01:00Z",
                    "content": [
                      { "type": "thinking", "thinking": "先解释借用检查器的静态标注" },
                      { "type": "text", "text": "'a 用于向编译器描述引用之间的存活关系。" }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()
        val claudeBundle = ExternalImportCodec.parseBytes(claudeJson.toByteArray(Charsets.UTF_8), fileName = "conversations.json")
        assertEquals(ExternalImportSource.CLAUDE, claudeBundle.source)
        assertEquals(1, claudeBundle.items.size)
        assertTrue(claudeBundle.items.first().hasReasoning)
        assertEquals("先解释借用检查器的静态标注", claudeBundle.items.first().messages[1].reasoning)

        val chatGptJson = """
            [
              {
                "id": "gpt-conv-1",
                "title": "旅行清单",
                "create_time": 1700000100.0,
                "update_time": 1700000200.0,
                "mapping": {
                  "n0": { "id": "n0", "parent": null, "children": ["n1"], "message": null },
                  "n1": {
                    "id": "n1",
                    "parent": "n0",
                    "children": ["n2"],
                    "message": {
                      "author": { "role": "user" },
                      "create_time": 1700000110.0,
                      "content": { "content_type": "text", "parts": ["去京都要带什么？"] }
                    }
                  },
                  "n2": {
                    "id": "n2",
                    "parent": "n1",
                    "children": [],
                    "message": {
                      "author": { "role": "assistant" },
                      "create_time": 1700000120.0,
                      "content": { "content_type": "text", "parts": ["转换插头、交通卡和舒适步行鞋。"] }
                    }
                  }
                }
              }
            ]
        """.trimIndent()
        val gptZip = buildZip(mapOf("conversations.json" to chatGptJson.toByteArray(Charsets.UTF_8)))
        val gptBundle = ExternalImportCodec.parseBytes(gptZip, fileName = "chatgpt_export.zip")
        assertEquals(ExternalImportSource.CHATGPT, gptBundle.source)
        assertEquals(1, gptBundle.items.size)
        assertEquals("cgpt_gpt-conv-1", gptBundle.items.first().conversation.id)
        assertEquals(2, gptBundle.items.first().messages.size)
    }

    @Test
    fun parseBytes_markdownTranscriptAndMultiAppZipBundle() {
        val md = """
            ## User
            帮我写首五言绝句

            ## Assistant
            <think>构思江南春雨意象</think>
            细雨润花枝，青苔上石阶。
        """.trimIndent()
        val mdBundle = ExternalImportCodec.parseBytes(md.toByteArray(Charsets.UTF_8), fileName = "poem.md")
        assertEquals(ExternalImportSource.GENERIC, mdBundle.source)
        assertEquals(2, mdBundle.items.first().messages.size)
        assertTrue(mdBundle.items.first().hasReasoning)
        assertEquals("构思江南春雨意象", mdBundle.items.first().messages[1].reasoning)

        val zip = buildZip(
            mapOf(
                "manifest.json" to """{"version":1}""".toByteArray(Charsets.UTF_8),
                "chat.jsonl" to """{"user_name":"我","character_name":"白狐"}
{"is_user":false,"mes":"林间雾起。"}""".toByteArray(Charsets.UTF_8),
                "script.txt" to "【白狐】随我进山。\n选项：A. 跟上".toByteArray(Charsets.UTF_8),
            ),
        )
        val zipBundle = ExternalImportCodec.parseBytes(zip, fileName = "pack.zip")
        assertEquals(2, zipBundle.items.size)
    }

    @Test
    fun externalImportService_downloadAndParseLink_supportsChatboxAndRejectsHtml() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val chatboxPayload = """
                {
                  "chat-sessions": [
                    {
                      "id": "remote-1",
                      "name": "云端 Chatbox 备份",
                      "messages": [
                        { "id": "1", "role": "user", "content": "Hello" },
                        { "id": "2", "role": "assistant", "content": "World" }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Content-Disposition", "attachment; filename=\"chatbox_backup.json\"")
                    .setBody(chatboxPayload),
            )
            val client = OkHttpClient()
            val bundle = ExternalImportService.downloadAndParseLink(
                httpClient = client,
                rawLinkInput = server.url("/dl/backup").toString(),
            )
            assertEquals(ExternalImportSource.CHATBOX, bundle.source)
            assertEquals("云端 Chatbox 备份", bundle.items.first().conversation.title)

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "text/html")
                    .setBody("<html><body>Sign in</body></html>"),
            )
            var threwAuth = false
            try {
                ExternalImportService.downloadAndParseLink(client, server.url("/dl/login").toString())
            } catch (_: DeepSeekLinkAuthRequiredException) {
                threwAuth = true
            }
            assertTrue(threwAuth)
        } finally {
            server.shutdown()
        }
    }
}
