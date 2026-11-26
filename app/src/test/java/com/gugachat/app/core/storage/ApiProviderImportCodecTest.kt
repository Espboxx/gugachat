package com.gugachat.app.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApiProviderImportCodecTest {

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun parseText_rikkaShareStrings_decodesProtocolsAndSkipsAccountOnlyProviders() {
        val openAiJson = """
            {
              "type": "me.rerere.rikkahub.data.model.ProviderSetting.OpenAI",
              "name": "DeepSeek 官方",
              "baseUrl": "https://api.deepseek.com/v1",
              "apiKey": "sk-ds-123456",
              "useResponseApi": false,
              "models": [{ "modelId": "deepseek-chat" }, { "modelId": "deepseek-reasoner" }]
            }
        """.trimIndent()
        val claudeJson = """
            {
              "type": "Claude",
              "name": "Claude 中转",
              "baseUrl": "https://relay.example.com/v1",
              "apiKey": "sk-ant-999",
              "models": ["claude-3-7-sonnet"]
            }
        """.trimIndent()
        val codexAccountJson = """
            {
              "type": "OpenAICodex",
              "name": "Codex OAuth",
              "baseUrl": "https://chatgpt.com/backend-api",
              "apiKey": ""
            }
        """.trimIndent()

        fun encode(s: String): String =
            "ai-provider:v1:" + Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

        val pasted = listOf(encode(openAiJson), encode(claudeJson), encode(codexAccountJson)).joinToString("\n")
        val bundle = ApiProviderImportCodec.parseText(pasted)
        assertEquals("rikkahub_share_v1", bundle.sourceFormat)
        assertEquals(2, bundle.providers.size)

        val ds = bundle.providers.first { it.name == "DeepSeek 官方" }
        assertEquals(ApiProtocol.OPENAI_CHAT, ds.protocol)
        assertEquals("https://api.deepseek.com/v1", ds.baseUrl)
        assertEquals("sk-ds-123456", ds.apiKey)
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), ds.customModels)

        val cl = bundle.providers.first { it.name == "Claude 中转" }
        assertEquals(ApiProtocol.ANTHROPIC, cl.protocol)
        assertEquals(listOf("claude-3-7-sonnet"), cl.customModels)
    }

    @Test
    fun parseBytes_cherryStudioZip_extractsDoubleEncodedReduxPersistProviders() {
        val llmState = """
            {
              "providers": [
                {
                  "id": "openai",
                  "type": "openai-response",
                  "name": "OpenAI Responses",
                  "apiKey": "sk-resp-001",
                  "apiHost": "https://api.openai.com/v1",
                  "enabled": true,
                  "models": [{ "id": "gpt-5" }, { "id": "o3" }]
                },
                {
                  "id": "gemini",
                  "type": "gemini",
                  "name": "Google Gemini",
                  "apiKey": "AIza-test-key",
                  "apiHost": "https://generativelanguage.googleapis.com",
                  "enabled": true,
                  "models": [{ "id": "gemini-2.5-pro" }]
                },
                {
                  "id": "empty",
                  "type": "openai",
                  "name": "Unconfigured",
                  "apiKey": "",
                  "apiHost": "https://example.com"
                }
              ]
            }
        """.trimIndent()
        val persisted = mapOf("llm" to llmState)
        val persistStr = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                kotlinx.serialization.serializer<String>(),
                kotlinx.serialization.serializer<String>(),
            ),
            persisted,
        )
        val dataJson = """
            {
              "time": 1740000000000,
              "version": 5,
              "localStorage": {
                "persist:cherry-studio": ${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), persistStr)}
              }
            }
        """.trimIndent()

        val zip = buildZip(mapOf("data.json" to dataJson))
        val bundle = ApiProviderImportCodec.parseBytes(zip, fileName = "cherry-studio-backup.zip")
        assertEquals("cherry_studio_zip", bundle.sourceFormat)
        assertEquals("Cherry Studio", bundle.sourceLabel)
        assertEquals(2, bundle.providers.size)

        val resp = bundle.providers.first { it.name == "OpenAI Responses" }
        assertEquals(ApiProtocol.OPENAI_RESPONSES, resp.protocol)
        assertEquals(listOf("gpt-5", "o3"), resp.customModels)

        val gem = bundle.providers.first { it.name == "Google Gemini" }
        assertEquals(ApiProtocol.GEMINI, gem.protocol)
        assertEquals(listOf("gemini-2.5-pro"), gem.customModels)
    }

    @Test
    fun parseBytes_chatboxV2Zip_extractsProvidersWithFallbackHostsAndResponsesApiStyle() {
        val manifest = """
            {
              "format": "chatbox-backup",
              "formatVersion": 2,
              "data": { "settings": { "path": "settings.json" } },
              "sessions": []
            }
        """.trimIndent()
        val settings = """
            {
              "providers": {
                "openai": {
                  "apiKey": "sk-cb-openai",
                  "apiHost": "https://api.openai.com",
                  "apiStyle": "responses",
                  "models": [{ "modelId": "gpt-5-mini" }]
                },
                "deepseek": {
                  "apiKey": "sk-cb-ds"
                },
                "claude": {
                  "apiKey": "sk-ant-cb",
                  "models": [{ "modelId": "claude-sonnet-4" }]
                }
              }
            }
        """.trimIndent()

        val zip = buildZip(mapOf("manifest.json" to manifest, "settings.json" to settings))
        val bundle = ApiProviderImportCodec.parseBytes(zip, fileName = "chatbox-backup.zip")
        assertEquals("chatbox_v2_zip", bundle.sourceFormat)
        assertEquals(3, bundle.providers.size)

        val oai = bundle.providers.first { it.apiKey == "sk-cb-openai" }
        assertEquals(ApiProtocol.OPENAI_RESPONSES, oai.protocol)
        assertEquals(listOf("gpt-5-mini"), oai.customModels)

        val ds = bundle.providers.first { it.apiKey == "sk-cb-ds" }
        assertEquals(ApiProtocol.OPENAI_CHAT, ds.protocol)
        assertEquals("https://api.deepseek.com/v1", ds.baseUrl)

        val cl = bundle.providers.first { it.apiKey == "sk-ant-cb" }
        assertEquals(ApiProtocol.ANTHROPIC, cl.protocol)
        assertEquals("https://api.anthropic.com", cl.baseUrl)
    }

    @Test
    fun parseText_genericArrayAndDedupeKeyNormalization() {
        val json = """
            [
              {
                "name": "My Relay",
                "protocol": "openai_chat",
                "baseUrl": "https://my.relay.ai/v1/",
                "apiKey": "sk-abc",
                "models": ["gpt-4o"]
              },
              {
                "name": "My Relay Dup",
                "protocol": "openai",
                "baseUrl": "https://MY.RELAY.AI/v1",
                "apiKey": "sk-abc",
                "models": ["gpt-4o-mini"]
              }
            ]
        """.trimIndent()

        val bundle = ApiProviderImportCodec.parseText(json)
        assertEquals(1, bundle.providers.size)
        assertTrue(ApiProviderImportCodec.dedupeKey(bundle.providers.first()).contains("https://my.relay.ai/v1"))
    }
}
