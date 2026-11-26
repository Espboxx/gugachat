package com.gugachat.app.core.storage

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import com.gugachat.app.domain.chat.RpCardCodec.arrOrNull
import com.gugachat.app.domain.chat.RpCardCodec.boolOrNull
import com.gugachat.app.domain.chat.RpCardCodec.objOrNull
import com.gugachat.app.domain.chat.RpCardCodec.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipInputStream

data class ApiProviderImportBundle(
    val sourceFormat: String,
    val sourceLabel: String,
    val providers: List<ApiProviderConfig>,
)

/**
 * Pure-JVM multi-format API provider importer compatible with:
 * - RikkaHub share code (`ai-provider:v1:<base64>`)
 * - RikkaHub `settings.json` / backup `.zip`
 * - Cherry Studio backup `.zip` / `data.json` (`localStorage["persist:cherry-studio"] -> llm.providers`)
 * - Chatbox Backup v2 `.zip` / `settings.json` (`providers` map)
 * - Generic providers JSON (`{ "providers": [...] }`, array, or single provider object)
 */
object ApiProviderImportCodec {
    private const val MAX_JSON_BYTES = 32 * 1024 * 1024
    private val RIKKA_SHARE_RE = Regex("""ai-provider:v1:([A-Za-z0-9+/=_-]+)""")

    fun dedupeKey(cfg: ApiProviderConfig): String =
        "${cfg.protocol.wireValue}|${cfg.baseUrl.trim().trimEnd('/').lowercase()}|${cfg.apiKey.trim()}"

    fun parseBytes(bytes: ByteArray, fileName: String? = null): ApiProviderImportBundle {
        if (bytes.isEmpty()) throw IllegalArgumentException("文件内容为空")
        if (DeepSeekImportCodec.isZipBytes(bytes)) {
            return parseZipBytes(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        return parseText(text, fileName)
    }

    fun parseText(rawText: String, fileName: String? = null): ApiProviderImportBundle {
        val clean = rawText.removePrefix("\uFEFF").trim()
        if (clean.isEmpty()) throw IllegalArgumentException("输入内容为空")

        // 1. RikkaHub QR / Share strings (`ai-provider:v1:<base64>`)
        val shareMatches = RIKKA_SHARE_RE.findAll(clean).toList()
        if (shareMatches.isNotEmpty()) {
            val decoded = shareMatches.mapNotNull { m ->
                decodeRikkaShareBase64(m.groupValues[1])
            }.distinctBy(::dedupeKey)
            if (decoded.isEmpty()) {
                throw IllegalArgumentException("未在 RikkaHub 分享串中解析到有效 API 供应商（账号型凭据或空 Key 已自动忽略）")
            }
            return ApiProviderImportBundle(
                sourceFormat = "rikkahub_share_v1",
                sourceLabel = "RikkaHub",
                providers = decoded,
            )
        }

        // 2. JSON payload
        val root = try {
            AppJson.parseToJsonElement(clean)
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的 JSON 或分享串格式：${e.message}")
        }

        return tryExtractFromJsonElement(root, fileName)
            ?: throw IllegalArgumentException("未在 JSON 中识别到有效的 API 供应商配置（需包含 apiKey 与 baseUrl/apiHost）")
    }

    fun tryExtractFromJsonElement(
        root: JsonElement,
        fileName: String? = null,
    ): ApiProviderImportBundle? {
        if (root is JsonObject) {
            // 1. Cherry Studio data.json (`localStorage["persist:cherry-studio"]`)
            extractCherryStudioProviders(root)?.let { list ->
                if (list.isNotEmpty()) {
                    return ApiProviderImportBundle(
                        sourceFormat = "cherry_studio_json",
                        sourceLabel = "Cherry Studio",
                        providers = list.distinctBy(::dedupeKey),
                    )
                }
            }

            // 2. Chatbox settings.json (`providers` as Map<String, JsonObject>)
            val providersElem = root["providers"]
            if (providersElem is JsonObject) {
                val list = parseChatboxProvidersMap(providersElem)
                if (list.isNotEmpty()) {
                    return ApiProviderImportBundle(
                        sourceFormat = "chatbox_settings",
                        sourceLabel = "Chatbox",
                        providers = list.distinctBy(::dedupeKey),
                    )
                }
            }

            // 3. RikkaHub settings.json or Generic `{ "providers": [...] }`
            if (providersElem is JsonArray) {
                val isRikka = root.containsKey("assistants") ||
                    root.containsKey("assistantId") ||
                    root.containsKey("displaySetting") ||
                    providersElem.any { (it as? JsonObject)?.strOrNull("type")?.contains("ProviderSetting") == true }
                val list = providersElem.mapNotNull { (it as? JsonObject)?.let(::parseSingleProviderObject) }
                if (list.isNotEmpty()) {
                    return ApiProviderImportBundle(
                        sourceFormat = if (isRikka) "rikkahub_settings" else "generic_providers_json",
                        sourceLabel = if (isRikka) "RikkaHub" else "JSON",
                        providers = list.distinctBy(::dedupeKey),
                    )
                }
            }

            // 4. Single provider object
            parseSingleProviderObject(root)?.let { single ->
                val isRikka = root.strOrNull("type")?.contains("ProviderSetting") == true
                return ApiProviderImportBundle(
                    sourceFormat = if (isRikka) "rikkahub_provider_json" else "generic_provider_object",
                    sourceLabel = if (isRikka) "RikkaHub" else (fileName?.substringBeforeLast('.')?.ifBlank { null } ?: "JSON"),
                    providers = listOf(single),
                )
            }
        } else if (root is JsonArray) {
            val list = root.mapNotNull { (it as? JsonObject)?.let(::parseSingleProviderObject) }
            if (list.isNotEmpty()) {
                return ApiProviderImportBundle(
                    sourceFormat = "generic_providers_array",
                    sourceLabel = "JSON",
                    providers = list.distinctBy(::dedupeKey),
                )
            }
        }
        return null
    }

    private fun parseZipBytes(zipBytes: ByteArray): ApiProviderImportBundle {
        val jsonEntries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val norm = entry.name.replace('\\', '/').trimStart('/')
                val lower = norm.lowercase()
                val base = lower.substringAfterLast('/')
                if (!lower.contains("__macosx/") && !base.startsWith("._") && lower.endsWith(".json")) {
                    if (base == "data.json" || base == "settings.json" || base == "manifest.json" || base.contains("provider")) {
                        val content = readBoundedUtf8(zis, MAX_JSON_BYTES).removePrefix("\uFEFF").trim()
                        if (content.isNotEmpty()) jsonEntries[norm] = content
                    }
                }
                zis.closeEntry()
            }
        }

        // Priority 1: Cherry Studio `data.json`
        jsonEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("data.json", ignoreCase = true) }?.let { (_, raw) ->
            val elem = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull()
            if (elem != null) {
                tryExtractFromJsonElement(elem, "data.json")?.let { return it.copy(sourceFormat = "cherry_studio_zip") }
            }
        }

        // Priority 2: Chatbox Backup v2 `manifest.json` -> settings path
        val manifestRaw = jsonEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("manifest.json", ignoreCase = true) }?.value
        if (manifestRaw != null) {
            val manifest = runCatching { AppJson.parseToJsonElement(manifestRaw) as? JsonObject }.getOrNull()
            if (manifest?.strOrNull("format") == "chatbox-backup") {
                val settingsPath = manifest.objOrNull("data")?.objOrNull("settings")?.strOrNull("path") ?: "settings.json"
                val settingsRaw = jsonEntries[settingsPath]
                    ?: jsonEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("settings.json", ignoreCase = true) }?.value
                if (settingsRaw != null) {
                    val elem = runCatching { AppJson.parseToJsonElement(settingsRaw) }.getOrNull()
                    if (elem != null) {
                        tryExtractFromJsonElement(elem, settingsPath)?.let {
                            return it.copy(sourceFormat = "chatbox_v2_zip", sourceLabel = "Chatbox")
                        }
                    }
                }
            }
        }

        // Priority 3: RikkaHub `settings.json` or any settings/provider JSON in ZIP
        for ((path, raw) in jsonEntries) {
            val elem = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: continue
            tryExtractFromJsonElement(elem, path)?.let { return it }
        }

        throw IllegalArgumentException("压缩包中未找到可识别的 API 供应商配置（支持 Cherry Studio / Chatbox / RikkaHub 备份）")
    }

    private fun decodeRikkaShareBase64(encoded: String): ApiProviderConfig? {
        val rawBytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
            ?: return null
        val jsonStr = rawBytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        val obj = runCatching { AppJson.parseToJsonElement(jsonStr) as? JsonObject }.getOrNull() ?: return null
        return parseSingleProviderObject(obj)
    }

    internal fun extractCherryStudioProviders(root: JsonObject): List<ApiProviderConfig>? {
        val localStorage = root.objOrNull("localStorage") ?: return null
        val persistElem = localStorage["persist:cherry-studio"] ?: return null
        val persisted: JsonObject = when (persistElem) {
            is JsonObject -> persistElem
            is JsonPrimitive -> persistElem.takeUnless { it is JsonNull }?.contentOrNull?.let { raw ->
                runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            } ?: return null
            else -> return null
        }
        val llmElem = persisted["llm"] ?: return null
        val llm: JsonObject = when (llmElem) {
            is JsonObject -> llmElem
            is JsonPrimitive -> llmElem.takeUnless { it is JsonNull }?.contentOrNull?.let { raw ->
                runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            } ?: return null
            else -> return null
        }
        val providersArr = llm.arrOrNull("providers") ?: return emptyList()
        return providersArr.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val apiKey = p.strOrNull("apiKey")?.trim().orEmpty()
            if (apiKey.isBlank()) return@mapNotNull null
            val type = p.strOrNull("type")?.trim()?.lowercase().orEmpty()
            val name = p.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: "Cherry Studio"
            val apiHost = p.strOrNull("apiHost").orEmpty().trim()
            val enabled = p.boolOrNull("enabled") ?: true
            val modelsArr = p.arrOrNull("models")
            val models = extractModelIds(modelsArr)
            val usesResponses = type == "openai-response" || type == "openai_responses" ||
                modelsArr.orEmpty().any { (it as? JsonObject)?.strOrNull("endpoint_type")?.contains("response", true) == true }

            val protocol = when {
                type.contains("anthropic") || type.contains("claude") -> ApiProtocol.ANTHROPIC
                type.contains("gemini") || type.contains("vertex") || type.contains("google") -> ApiProtocol.GEMINI
                usesResponses -> ApiProtocol.OPENAI_RESPONSES
                else -> ApiProtocol.OPENAI_CHAT
            }
            val baseUrl = resolveBaseUrl(protocol, apiHost)
            ApiProviderConfig(
                name = name,
                protocol = protocol,
                baseUrl = baseUrl,
                apiKey = apiKey,
                customModels = models,
                enabled = enabled,
            )
        }
    }

    internal fun parseChatboxProvidersMap(providersObj: JsonObject): List<ApiProviderConfig> {
        return providersObj.entries.mapNotNull { (providerKey, element) ->
            val p = element as? JsonObject ?: return@mapNotNull null
            val apiKey = p.strOrNull("apiKey")?.trim().orEmpty()
            if (apiKey.isBlank()) return@mapNotNull null
            val normalizedKey = providerKey.trim().lowercase()
            val apiHost = p.strOrNull("apiHost").orEmpty().trim()
            val modelsArr = p.arrOrNull("models")
            val models = extractModelIds(modelsArr)
            val displayName = p.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: chatboxProviderDisplayName(normalizedKey)

            when (normalizedKey) {
                "claude", "anthropic" -> ApiProviderConfig(
                    name = displayName,
                    protocol = ApiProtocol.ANTHROPIC,
                    baseUrl = resolveBaseUrl(ApiProtocol.ANTHROPIC, apiHost),
                    apiKey = apiKey,
                    customModels = models,
                )
                "gemini", "google" -> ApiProviderConfig(
                    name = displayName,
                    protocol = ApiProtocol.GEMINI,
                    baseUrl = resolveBaseUrl(ApiProtocol.GEMINI, apiHost),
                    apiKey = apiKey,
                    customModels = models,
                )
                else -> {
                    val fallbackHost = chatboxOpenAiBaseUrl(normalizedKey)
                        ?: apiHost.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val usesResponses = p.strOrNull("apiStyle")?.contains("response", true) == true ||
                        modelsArr.orEmpty().any { (it as? JsonObject)?.strOrNull("apiStyle")?.contains("response", true) == true }
                    val proto = if (usesResponses) ApiProtocol.OPENAI_RESPONSES else ApiProtocol.OPENAI_CHAT
                    ApiProviderConfig(
                        name = displayName,
                        protocol = proto,
                        baseUrl = resolveBaseUrl(proto, apiHost.ifBlank { fallbackHost }),
                        apiKey = apiKey,
                        customModels = models,
                    )
                }
            }
        }
    }

    internal fun parseSingleProviderObject(obj: JsonObject): ApiProviderConfig? {
        val rawType = (
            obj.strOrNull("protocol")
                ?: obj.strOrNull("type")
                ?: obj.strOrNull("providerType")
            ).orEmpty().trim()
        val lowerType = rawType.lowercase()

        // Skip RikkaHub account-based OAuth providers (ChatGPT / Grok) that don't use API keys
        if (lowerType.endsWith(".chatgpt") || lowerType.endsWith(".grok") || lowerType == "chatgpt" || lowerType == "grok") {
            return null
        }

        val apiKey = (
            obj.strOrNull("apiKey")
                ?: obj.strOrNull("api_key")
                ?: obj.strOrNull("key")
            ).orEmpty().trim()
        if (apiKey.isBlank()) return null

        val rawBaseUrl = (
            obj.strOrNull("baseUrl")
                ?: obj.strOrNull("base_url")
                ?: obj.strOrNull("apiHost")
                ?: obj.strOrNull("api_host")
                ?: obj.strOrNull("endpoint")
                ?: obj.strOrNull("url")
            ).orEmpty().trim()

        // Must look like a provider object (has baseUrl or explicit protocol/type)
        if (rawBaseUrl.isBlank() && lowerType.isBlank()) return null

        val useResponseApi = obj.boolOrNull("useResponseApi") == true ||
            obj.boolOrNull("use_response_api") == true ||
            obj.strOrNull("apiStyle")?.contains("response", true) == true
        val protocol = inferProtocol(lowerType, rawBaseUrl, useResponseApi)
        val baseUrl = resolveBaseUrl(protocol, rawBaseUrl)
        val name = (
            obj.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.strOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: protocol.displayName
            )
        val enabled = obj.boolOrNull("enabled") ?: true
        val models = (
            extractModelIds(obj.arrOrNull("customModels")) +
                extractModelIds(obj.arrOrNull("models"))
            ).distinct()

        return ApiProviderConfig(
            name = name,
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = apiKey,
            customModels = models,
            enabled = enabled,
        )
    }

    private fun inferProtocol(lowerType: String, baseUrl: String, useResponseApi: Boolean): ApiProtocol {
        val lowerUrl = baseUrl.lowercase()
        return when {
            lowerType.contains("anthropic") || lowerType.contains("claude") -> ApiProtocol.ANTHROPIC
            lowerType.contains("gemini") || lowerType.contains("google") || lowerType.contains("vertex") -> ApiProtocol.GEMINI
            lowerType.contains("response") || useResponseApi -> ApiProtocol.OPENAI_RESPONSES
            lowerType.contains("openai") -> ApiProtocol.OPENAI_CHAT
            lowerUrl.contains("anthropic.com") -> ApiProtocol.ANTHROPIC
            lowerUrl.contains("generativelanguage.googleapis.com") -> ApiProtocol.GEMINI
            else -> ApiProtocol.OPENAI_CHAT
        }
    }

    private fun resolveBaseUrl(protocol: ApiProtocol, raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return trimmed.ifBlank { protocol.defaultBaseUrl }
    }

    private fun extractModelIds(arr: JsonArray?): List<String> {
        if (arr == null || arr.isEmpty()) return emptyList()
        return arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.takeUnless { it is JsonNull }?.contentOrNull?.trim()
                is JsonObject -> (
                    el.strOrNull("modelId")
                        ?: el.strOrNull("model_id")
                        ?: el.strOrNull("id")
                        ?: el.strOrNull("name")
                    )?.trim()
                else -> null
            }?.takeIf { it.isNotEmpty() }
        }.distinct()
    }

    private fun chatboxOpenAiBaseUrl(providerKey: String): String? = when (providerKey) {
        "openai" -> ApiProtocol.OPENAI_CHAT.defaultBaseUrl
        "deepseek" -> "https://api.deepseek.com/v1"
        "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "moonshot", "moonshot-cn" -> "https://api.moonshot.cn/v1"
        "openrouter" -> "https://openrouter.ai/api/v1"
        "siliconflow" -> "https://api.siliconflow.cn/v1"
        "groq" -> "https://api.groq.com/openai/v1"
        "xai" -> "https://api.x.ai/v1"
        "mistral" -> "https://api.mistral.ai/v1"
        "perplexity" -> "https://api.perplexity.ai"
        else -> null
    }

    private fun chatboxProviderDisplayName(providerKey: String): String = when (providerKey) {
        "openai" -> "OpenAI"
        "claude", "anthropic" -> "Claude"
        "gemini", "google" -> "Gemini"
        "deepseek" -> "DeepSeek"
        "qwen" -> "Qwen"
        "moonshot", "moonshot-cn" -> "Moonshot CN"
        "openrouter" -> "OpenRouter"
        "siliconflow" -> "SiliconFlow"
        "groq" -> "Groq"
        "xai" -> "xAI"
        "mistral" -> "Mistral"
        "perplexity" -> "Perplexity"
        else -> providerKey.replaceFirstChar { it.uppercaseChar() }
    }

    private fun readBoundedUtf8(zis: ZipInputStream, maxBytes: Int): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = zis.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) throw IllegalArgumentException("配置文件超过体积限制")
            baos.write(buf, 0, n)
        }
        return baos.toString(Charsets.UTF_8.name())
    }
}
