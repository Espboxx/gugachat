package com.gugachat.app.data.api

import com.gugachat.app.core.storage.ApiProtocol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class DecodedDirectImage(
    val bytes: ByteArray? = null,
    val url: String? = null,
    val mimeType: String = "image/png",
)

data class DirectInlineImage(
    val mimeType: String,
    val bytes: ByteArray,
)

enum class DiscoveredModelKind {
    EMBEDDING,
    RERANK,
}

data class DiscoveredRetrievalModel(
    val id: String,
    val kind: DiscoveredModelKind,
)

object DirectProtocolCodec {
    private val VERSION_SUFFIX_RE = Regex("/v\\d+(alpha|beta)?$", RegexOption.IGNORE_CASE)
    private val GEMINI_VERSION_SUFFIX_RE = Regex("/v1(beta|alpha)?$", RegexOption.IGNORE_CASE)
    private val DATA_URL_RE = Regex("^data:([^;]+);base64,(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val E5_MODEL_RE = Regex("(^|[/_-])e5([/_-]|$)")
    private val ENDPOINT_TAILS = listOf(
        "/chat/completions",
        "/responses",
        "/messages",
        "/images/generations",
        "/images/edits",
        "/embeddings",
        "/rerank",
        "/models",
    )

    fun normalizeBaseOrigin(raw: String, fallback: String = ""): String {
        val trimmed = raw.trim().ifBlank { fallback }.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase() ?: return ""
        if (scheme != "http" && scheme != "https") return ""
        val host = uri.host ?: return ""
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        var path = (uri.rawPath ?: "").trimEnd('/')
        for (tail in ENDPOINT_TAILS) {
            if (path.lowercase().endsWith(tail)) {
                path = path.substring(0, path.length - tail.length).trimEnd('/')
                break
            }
        }
        return "$scheme://$host$portPart$path"
    }

    private fun openAiOrAnthropicRoot(rawBase: String, defaultBase: String): String {
        val norm = normalizeBaseOrigin(rawBase, defaultBase)
        require(norm.isNotEmpty()) { "Invalid Base URL" }
        val uri = URI(norm)
        val path = (uri.rawPath ?: "").trimEnd('/')
        return if (VERSION_SUFFIX_RE.containsMatchIn(path)) norm else "$norm/v1"
    }

    private fun geminiRoot(rawBase: String): String {
        var norm = normalizeBaseOrigin(rawBase, ApiProtocol.GEMINI.defaultBaseUrl)
        require(norm.isNotEmpty()) { "Invalid Base URL" }
        val modelActionIdx = norm.indexOf("/models/")
        if (modelActionIdx > 0) {
            norm = norm.substring(0, modelActionIdx).trimEnd('/')
        }
        val uri = URI(norm)
        val path = (uri.rawPath ?: "").trimEnd('/')
        return if (GEMINI_VERSION_SUFFIX_RE.containsMatchIn(path)) norm else "$norm/v1beta"
    }

    fun openaiChatUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/chat/completions"
    fun openaiResponsesUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_RESPONSES.defaultBaseUrl)}/responses"
    fun openaiModelsUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/models"
    fun openaiImagesGenUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/images/generations"
    fun openaiImagesEditUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/images/edits"
    fun openaiEmbeddingsUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/embeddings"
    fun openaiRerankUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.OPENAI_CHAT.defaultBaseUrl)}/rerank"

    fun anthropicMessagesUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.ANTHROPIC.defaultBaseUrl)}/messages"
    fun anthropicModelsUrl(baseUrl: String): String = "${openAiOrAnthropicRoot(baseUrl, ApiProtocol.ANTHROPIC.defaultBaseUrl)}/models"

    fun geminiModelsUrl(baseUrl: String): String = "${geminiRoot(baseUrl)}/models"
    fun geminiChatUrl(baseUrl: String, model: String, stream: Boolean): String {
        val cleanModel = model.trim().removePrefix("models/")
        val encoded = URLEncoder.encode(cleanModel, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return if (stream) {
            "${geminiRoot(baseUrl)}/models/$encoded:streamGenerateContent?alt=sse"
        } else {
            "${geminiRoot(baseUrl)}/models/$encoded:generateContent"
        }
    }

    fun geminiImagenPredictUrl(baseUrl: String, model: String): String {
        val cleanModel = model.trim().removePrefix("models/")
        val encoded = URLEncoder.encode(cleanModel, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "${geminiRoot(baseUrl)}/models/$encoded:predict"
    }

    fun geminiEmbedContentUrl(baseUrl: String, modelId: String): String {
        val cleanModel = modelId.trim().removePrefix("models/")
        val encoded = URLEncoder.encode(cleanModel, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "${geminiRoot(baseUrl)}/models/$encoded:embedContent"
    }

    fun geminiBatchEmbedContentsUrl(baseUrl: String, modelId: String): String {
        val cleanModel = modelId.trim().removePrefix("models/")
        val encoded = URLEncoder.encode(cleanModel, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "${geminiRoot(baseUrl)}/models/$encoded:batchEmbedContents"
    }

    // ==================== Chat Request Builders ====================

    fun isOfficialOpenAiHost(baseUrl: String): Boolean {
        val norm = normalizeBaseOrigin(baseUrl)
        if (norm.isEmpty()) return false
        val host = runCatching { URI(norm).host }.getOrNull()?.lowercase() ?: return false
        return host == "api.openai.com"
    }

    fun buildOpenAIChatRequestJson(
        request: OnlineChatCompletionRequestDto,
        stream: Boolean,
        baseUrl: String = "",
    ): String {
        val officialOpenAi = isOfficialOpenAiHost(baseUrl)
        val prefill = request.assistantPrefill?.takeIf { it.isNotBlank() && !officialOpenAi }
        val obj = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            if (stream) {
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            put("messages", buildJsonArray {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                }
                if (prefill != null) {
                    addJsonObject {
                        put("role", "assistant")
                        put("content", prefill)
                    }
                }
            })
            request.temperature?.let { put("temperature", it) }
            request.top_p?.let { put("top_p", it) }
            request.max_tokens?.let { put("max_tokens", it) }
            request.reasoning_effort?.takeIf { it.isNotBlank() }?.let { put("reasoning_effort", it) }
            request.frequency_penalty?.let { put("frequency_penalty", it) }
            request.presence_penalty?.let { put("presence_penalty", it) }
            if (!officialOpenAi) {
                request.repetition_penalty?.let { put("repetition_penalty", it) }
                request.top_k?.let { put("top_k", it) }
                request.min_p?.let { put("min_p", it) }
            }
            request.stop?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4)?.takeIf { it.isNotEmpty() }?.let { stops ->
                putJsonArray("stop") { stops.forEach { add(JsonPrimitive(it)) } }
            }
            if (request.enablePromptCaching && officialOpenAi && !request.promptCacheKey.isNullOrBlank()) {
                put("prompt_cache_key", request.promptCacheKey)
            }
        }
        return obj.toString()
    }

    fun buildOpenAIResponsesRequestJson(request: OnlineChatCompletionRequestDto, stream: Boolean): String {
        val systemTexts = mutableListOf<String>()
        val inputItems = mutableListOf<JsonObject>()

        for (msg in request.messages) {
            val role = msg.role.lowercase()
            if (role == "system" || role == "developer") {
                val txt = extractPlainText(msg.content)
                if (txt.isNotBlank()) systemTexts += txt
                continue
            }
            val mappedRole = if (role == "assistant") "assistant" else "user"
            val parts = extractParts(msg.content)
            val contentArray = buildJsonArray {
                for (part in parts) {
                    if (part.text != null) {
                        addJsonObject {
                            put("type", if (mappedRole == "assistant") "output_text" else "input_text")
                            put("text", part.text)
                        }
                    } else if (part.imageUrl != null && mappedRole == "user") {
                        addJsonObject {
                            put("type", "input_image")
                            put("image_url", part.imageUrl)
                        }
                    }
                }
            }
            if (contentArray.isNotEmpty()) {
                inputItems += buildJsonObject {
                    put("role", mappedRole)
                    put("content", contentArray)
                }
            }
        }
        if (inputItems.isEmpty()) {
            inputItems += buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "input_text")
                        put("text", "Hello")
                    }
                }
            }
        }

        val obj = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            if (systemTexts.isNotEmpty()) {
                put("instructions", systemTexts.joinToString("\n\n"))
            }
            put("input", JsonArray(inputItems))
            request.temperature?.let { put("temperature", it) }
            request.top_p?.let { put("top_p", it) }
            request.max_tokens?.let { put("max_output_tokens", it) }
            request.reasoning_effort?.takeIf { it.isNotBlank() }?.let { effort ->
                putJsonObject("reasoning") { put("effort", effort) }
            }
            if (request.enablePromptCaching && !request.promptCacheKey.isNullOrBlank()) {
                put("prompt_cache_key", request.promptCacheKey)
            }
        }
        return obj.toString()
    }

    fun buildAnthropicRequestJson(request: OnlineChatCompletionRequestDto, stream: Boolean): String {
        val systemTexts = mutableListOf<String>()
        val rawTurns = mutableListOf<Pair<String, MutableList<JsonObject>>>()

        for (msg in request.messages) {
            val role = msg.role.lowercase()
            if (role == "system" || role == "developer") {
                val txt = extractPlainText(msg.content)
                if (txt.isNotBlank()) systemTexts += txt
                continue
            }
            val mappedRole = if (role == "assistant") "assistant" else "user"
            val blocks = mutableListOf<JsonObject>()
            for (part in extractParts(msg.content)) {
                if (part.text != null && part.text.isNotEmpty()) {
                    blocks += buildJsonObject {
                        put("type", "text")
                        put("text", part.text)
                    }
                } else if (part.imageUrl != null && mappedRole == "user") {
                    val dataMatch = DATA_URL_RE.find(part.imageUrl)
                    if (dataMatch != null) {
                        blocks += buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", dataMatch.groupValues[1])
                                put("data", dataMatch.groupValues[2].trim())
                            }
                        }
                    } else {
                        blocks += buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "url")
                                put("url", part.imageUrl)
                            }
                        }
                    }
                }
            }
            if (blocks.isEmpty()) continue
            if (rawTurns.isNotEmpty() && rawTurns.last().first == mappedRole) {
                rawTurns.last().second.addAll(blocks)
            } else {
                rawTurns += mappedRole to blocks
            }
        }

        if (rawTurns.isEmpty() || rawTurns.first().first != "user") {
            rawTurns.add(
                0,
                "user" to mutableListOf(buildJsonObject {
                    put("type", "text")
                    put("text", "Hello")
                }),
            )
        }

        val thinkingBudget = effortToBudgetTokens(request.reasoning_effort)
        val maxTokens = (request.max_tokens ?: 4096).let { reqMax ->
            if (thinkingBudget != null && reqMax <= thinkingBudget) thinkingBudget + 1024 else reqMax
        }

        // Prompt Caching Breakpoint 2: pin stable conversation history right before the latest turn
        if (request.enablePromptCaching && rawTurns.size >= 3) {
            val cacheTurnIdx = rawTurns.lastIndex - 1
            val blocks = rawTurns[cacheTurnIdx].second
            val textBlockIdx = blocks.indexOfLast { it["type"]?.jsonPrimitive?.content == "text" }
            if (textBlockIdx >= 0) {
                val orig = blocks[textBlockIdx]
                blocks[textBlockIdx] = buildJsonObject {
                    orig.forEach { (k, v) -> put(k, v) }
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                }
            }
        }

        // Anthropic rejects assistant prefill when extended thinking is enabled
        val prefill = request.assistantPrefill?.takeIf { it.isNotBlank() && thinkingBudget == null }
        if (prefill != null) {
            val prefillBlock = buildJsonObject {
                put("type", "text")
                put("text", prefill)
            }
            if (rawTurns.isNotEmpty() && rawTurns.last().first == "assistant") {
                rawTurns.last().second.add(prefillBlock)
            } else {
                rawTurns += "assistant" to mutableListOf(prefillBlock)
            }
        }

        val obj = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put("max_tokens", maxTokens)
            if (systemTexts.isNotEmpty()) {
                val joinedSystem = systemTexts.joinToString("\n\n")
                if (request.enablePromptCaching) {
                    putJsonArray("system") {
                        addJsonObject {
                            put("type", "text")
                            put("text", joinedSystem)
                            putJsonObject("cache_control") { put("type", "ephemeral") }
                        }
                    }
                } else {
                    put("system", joinedSystem)
                }
            }
            put("messages", buildJsonArray {
                rawTurns.forEach { (role, blocks) ->
                    addJsonObject {
                        put("role", role)
                        put("content", JsonArray(blocks))
                    }
                }
            })
            if (thinkingBudget != null) {
                // Anthropic rejects temperature / top_p / top_k when extended thinking is enabled
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", thinkingBudget)
                }
            } else {
                request.temperature?.let { put("temperature", it) }
                request.top_p?.let { put("top_p", it) }
                request.top_k?.let { put("top_k", it) }
            }
            request.stop?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4)?.takeIf { it.isNotEmpty() }?.let { stops ->
                putJsonArray("stop_sequences") { stops.forEach { add(JsonPrimitive(it)) } }
            }
        }
        return obj.toString()
    }

    fun buildGeminiRequestJson(request: OnlineChatCompletionRequestDto): String {
        val systemTexts = mutableListOf<String>()
        val contents = mutableListOf<JsonObject>()

        for (msg in request.messages) {
            val role = msg.role.lowercase()
            if (role == "system" || role == "developer") {
                val txt = extractPlainText(msg.content)
                if (txt.isNotBlank()) systemTexts += txt
                continue
            }
            val mappedRole = if (role == "assistant") "model" else "user"
            val parts = buildJsonArray {
                for (part in extractParts(msg.content)) {
                    if (part.text != null && part.text.isNotEmpty()) {
                        addJsonObject { put("text", part.text) }
                    } else if (part.imageUrl != null) {
                        val dataMatch = DATA_URL_RE.find(part.imageUrl)
                        if (dataMatch != null) {
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", dataMatch.groupValues[1])
                                    put("data", dataMatch.groupValues[2].trim())
                                }
                            }
                        } else {
                            addJsonObject { put("text", "[Image: ${part.imageUrl}]") }
                        }
                    }
                }
            }
            if (parts.isNotEmpty()) {
                contents += buildJsonObject {
                    put("role", mappedRole)
                    put("parts", parts)
                }
            }
        }

        if (contents.isEmpty()) {
            contents += buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", "Hello") }
                }
            }
        }

        val prefill = request.assistantPrefill?.takeIf { it.isNotBlank() }
        if (prefill != null) {
            contents += buildJsonObject {
                put("role", "model")
                putJsonArray("parts") {
                    addJsonObject { put("text", prefill) }
                }
            }
        }

        val thinkingBudget = effortToBudgetTokens(request.reasoning_effort)
        val obj = buildJsonObject {
            if (systemTexts.isNotEmpty()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemTexts.joinToString("\n\n")) }
                    }
                }
            }
            put("contents", JsonArray(contents))
            putJsonObject("generationConfig") {
                request.temperature?.let { put("temperature", it) }
                request.top_p?.let { put("topP", it) }
                request.top_k?.let { put("topK", it) }
                request.max_tokens?.let { put("maxOutputTokens", it) }
                request.frequency_penalty?.let { put("frequencyPenalty", it) }
                request.presence_penalty?.let { put("presencePenalty", it) }
                request.stop?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4)?.takeIf { it.isNotEmpty() }?.let { stops ->
                    putJsonArray("stopSequences") { stops.forEach { add(JsonPrimitive(it)) } }
                }
                if (thinkingBudget != null) {
                    putJsonObject("thinkingConfig") {
                        put("includeThoughts", true)
                        put("thinkingBudget", thinkingBudget)
                    }
                }
            }
        }
        return obj.toString()
    }

    // ==================== Chat Response & SSE Parsers ====================

    fun parseOpenAIResponsesSse(eventType: String?, rawData: String): SseLineOutcome? {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "[DONE]") return SseLineOutcome(done = true)

        val obj = runCatching { AppJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: eventType.orEmpty()

        checkEmbeddedError(obj, 502)

        return when (type) {
            "response.output_text.delta" -> SseLineOutcome(
                deltaText = obj["delta"]?.jsonPrimitive?.content.orEmpty(),
            )
            "response.reasoning_summary_text.delta",
            "response.reasoning_text.delta" -> SseLineOutcome(
                deltaReasoning = obj["delta"]?.jsonPrimitive?.content.orEmpty(),
            )
            "response.completed", "response.done" -> {
                val usage = obj["response"]?.jsonObject?.get("usage")?.let { parseResponsesUsage(it.jsonObject) }
                SseLineOutcome(done = true, usage = usage)
            }
            "response.failed", "error" -> {
                val msg = obj["response"]?.jsonObject?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: obj["message"]?.jsonPrimitive?.content
                    ?: "OpenAI Responses stream error"
                throw ApiException(502, "RESPONSES_ERROR", msg)
            }
            else -> null
        }
    }

    fun parseOpenAIResponsesSingleJson(rawBody: String, statusCode: Int): CompletionResult {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid OpenAI Responses JSON")
        checkEmbeddedError(obj, statusCode)

        val textBuf = StringBuilder()
        val reasonBuf = StringBuilder()
        obj["output_text"]?.jsonPrimitive?.content?.let { textBuf.append(it) }

        val outputArr = obj["output"] as? JsonArray
        if (outputArr != null && textBuf.isEmpty()) {
            for (itemEl in outputArr) {
                val item = itemEl as? JsonObject ?: continue
                val itemType = item["type"]?.jsonPrimitive?.content.orEmpty()
                if (itemType == "reasoning") {
                    val summaryArr = item["summary"] as? JsonArray
                    summaryArr?.forEach { s ->
                        (s as? JsonObject)?.get("text")?.jsonPrimitive?.content?.let { reasonBuf.append(it) }
                    }
                } else {
                    val contentArr = item["content"] as? JsonArray ?: continue
                    for (cEl in contentArr) {
                        val cObj = cEl as? JsonObject ?: continue
                        val cType = cObj["type"]?.jsonPrimitive?.content.orEmpty()
                        val txt = cObj["text"]?.jsonPrimitive?.content.orEmpty()
                        if (cType == "reasoning" || cType == "thinking") {
                            reasonBuf.append(txt)
                        } else if (txt.isNotEmpty()) {
                            textBuf.append(txt)
                        }
                    }
                }
            }
        }
        val usage = (obj["usage"] as? JsonObject)?.let(::parseResponsesUsage)
        return CompletionResult(textBuf.toString(), reasonBuf.toString(), usage)
    }

    fun parseAnthropicSse(eventType: String?, rawData: String): SseLineOutcome? {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "[DONE]") return SseLineOutcome(done = true)

        val obj = runCatching { AppJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: eventType.orEmpty()
        checkEmbeddedError(obj, 502)

        return when (type) {
            "message_start" -> {
                val usageObj = obj["message"]?.jsonObject?.get("usage") as? JsonObject
                val usage = usageObj?.let(::parseAnthropicUsage)
                SseLineOutcome(usage = usage)
            }
            "content_block_delta" -> {
                val delta = obj["delta"] as? JsonObject ?: return null
                val dType = delta["type"]?.jsonPrimitive?.content.orEmpty()
                when (dType) {
                    "text_delta" -> SseLineOutcome(deltaText = delta["text"]?.jsonPrimitive?.content.orEmpty())
                    "thinking_delta" -> SseLineOutcome(deltaReasoning = delta["thinking"]?.jsonPrimitive?.content.orEmpty())
                    else -> null
                }
            }
            "message_delta" -> {
                val usageObj = obj["usage"] as? JsonObject
                SseLineOutcome(usage = usageObj?.let(::parseAnthropicUsage))
            }
            "message_stop" -> SseLineOutcome(done = true)
            else -> null
        }
    }

    fun parseAnthropicSingleJson(rawBody: String, statusCode: Int): CompletionResult {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid Anthropic JSON")
        checkEmbeddedError(obj, statusCode)

        val textBuf = StringBuilder()
        val reasonBuf = StringBuilder()
        val blocks = obj["content"] as? JsonArray
        blocks?.forEach { blockEl ->
            val block = blockEl as? JsonObject ?: return@forEach
            when (block["type"]?.jsonPrimitive?.content) {
                "thinking" -> reasonBuf.append(block["thinking"]?.jsonPrimitive?.content.orEmpty())
                "text" -> textBuf.append(block["text"]?.jsonPrimitive?.content.orEmpty())
            }
        }
        val usage = (obj["usage"] as? JsonObject)?.let(::parseAnthropicUsage)
        return CompletionResult(textBuf.toString(), reasonBuf.toString(), usage)
    }

    fun parseGeminiSseData(rawData: String): SseLineOutcome? {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "[DONE]") return SseLineOutcome(done = true)

        val obj = runCatching { AppJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
        checkEmbeddedError(obj, 502)

        val textBuf = StringBuilder()
        val reasonBuf = StringBuilder()
        extractGeminiPartsText(obj, textBuf, reasonBuf)
        val usage = (obj["usageMetadata"] as? JsonObject)?.let(::parseGeminiUsage)
        val finishReason = ((obj["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("finishReason")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        if (textBuf.isEmpty() && reasonBuf.isEmpty() && usage == null && finishReason == null) return null
        return SseLineOutcome(
            done = false,
            deltaText = textBuf.toString(),
            deltaReasoning = reasonBuf.toString(),
            usage = usage,
            finishReason = finishReason,
        )
    }

    fun parseGeminiSingleJson(rawBody: String, statusCode: Int): CompletionResult {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid Gemini JSON")
        checkEmbeddedError(obj, statusCode)

        val textBuf = StringBuilder()
        val reasonBuf = StringBuilder()
        extractGeminiPartsText(obj, textBuf, reasonBuf)
        val usage = (obj["usageMetadata"] as? JsonObject)?.let(::parseGeminiUsage)
        return CompletionResult(textBuf.toString(), reasonBuf.toString(), usage)
    }

    // ==================== Model List & Capability Inference ====================

    fun parseModelsJson(protocol: ApiProtocol, rawBody: String): List<OnlineChatModelDto> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return emptyList()
        return when (protocol) {
            ApiProtocol.GEMINI -> {
                val arr = obj["models"] as? JsonArray ?: return emptyList()
                arr.mapNotNull { el ->
                    val m = el as? JsonObject ?: return@mapNotNull null
                    val rawName = m["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val id = rawName.removePrefix("models/").trim()
                    if (id.isEmpty()) return@mapNotNull null
                    val methods = (m["supportedGenerationMethods"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.content }
                        .orEmpty()
                    if (methods.isNotEmpty() &&
                        methods.none { it.equals("generateContent", true) || it.equals("predict", true) }
                    ) {
                        return@mapNotNull null
                    }
                    val limit = m["inputTokenLimit"]?.jsonPrimitive?.intOrNull ?: inferContextWindow(id)
                    inferChatModelMeta(id, overrideContext = limit)
                }.distinctBy { it.id }
            }
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES,
            ApiProtocol.ANTHROPIC -> {
                val arr = obj["data"] as? JsonArray ?: return emptyList()
                arr.mapNotNull { el ->
                    val m = el as? JsonObject ?: return@mapNotNull null
                    val id = m["id"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (id.isEmpty()) return@mapNotNull null
                    val lower = id.lowercase()
                    if (lower.startsWith("text-embedding") ||
                        lower.startsWith("tts-") ||
                        lower.startsWith("whisper") ||
                        lower.contains("moderation")
                    ) {
                        return@mapNotNull null
                    }
                    inferChatModelMeta(id)
                }.distinctBy { it.id }
            }
        }
    }

    fun inferChatModelMeta(modelId: String, overrideContext: Int? = null): OnlineChatModelDto {
        val lower = modelId.lowercase()
        val supportsVision = lower.contains("gpt-4o") ||
            lower.contains("gpt-4.1") ||
            lower.contains("gpt-4.5") ||
            lower.contains("gpt-5") ||
            lower.startsWith("o3") ||
            lower.startsWith("o4") ||
            lower.contains("claude-3") ||
            lower.contains("claude-sonnet-4") ||
            lower.contains("claude-opus-4") ||
            lower.contains("claude-haiku-4") ||
            lower.contains("gemini") ||
            lower.contains("vision") ||
            lower.contains("vl")
        val supportsReasoning = lower.startsWith("o1") ||
            lower.startsWith("o3") ||
            lower.startsWith("o4") ||
            lower.contains("gpt-5") ||
            lower.contains("reasoner") ||
            lower.contains("deepseek-r1") ||
            lower.contains("claude-3-7") ||
            lower.contains("claude-sonnet-4") ||
            lower.contains("claude-opus-4") ||
            lower.contains("gemini-2.5") ||
            lower.contains("gemini-3") ||
            lower.contains("thinking")
        return OnlineChatModelDto(
            id = modelId,
            supports_vision = supportsVision,
            supports_reasoning = supportsReasoning,
            context_window = overrideContext ?: inferContextWindow(modelId),
        )
    }

    private fun inferContextWindow(modelId: String): Int {
        val lower = modelId.lowercase()
        return when {
            lower.contains("gemini-1.5") || lower.contains("gemini-2") || lower.contains("gemini-3") -> 1_000_000
            lower.contains("gpt-4.1") || lower.contains("gpt-5") -> 400_000
            lower.contains("claude") -> 200_000
            lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") -> 200_000
            else -> 128_000
        }
    }

    fun defaultChatModels(protocol: ApiProtocol): List<OnlineChatModelDto> {
        val ids = when (protocol) {
            ApiProtocol.OPENAI_CHAT -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1", "o3-mini", "deepseek-chat")
            ApiProtocol.OPENAI_RESPONSES -> listOf("gpt-4.1", "gpt-5", "o4-mini", "o3")
            ApiProtocol.ANTHROPIC -> listOf("claude-sonnet-4-20250514", "claude-3-7-sonnet-20250219", "claude-3-5-haiku-20241022")
            ApiProtocol.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        }
        return ids.map { inferChatModelMeta(it) }
    }

    fun defaultImageModels(protocol: ApiProtocol, customModels: List<String> = emptyList()): List<OnlineImageModelDto> {
        val customImageModels = customModels.filter { id ->
            val lower = id.lowercase()
            lower.contains("image") || lower.contains("dall-e") || lower.contains("imagen") || lower.contains("flux")
        }
        val baseIds = when (protocol) {
            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES -> (customImageModels + listOf("gpt-image-1", "dall-e-3")).distinct()
            ApiProtocol.GEMINI -> (customImageModels + listOf("gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation", "imagen-3.0-generate-002")).distinct()
            ApiProtocol.ANTHROPIC -> return emptyList()
        }
        return baseIds.map { id ->
            val isDalle = id.lowercase().startsWith("dall-e")
            OnlineImageModelDto(
                id = id,
                provider = protocol.wireValue,
                transport = "direct",
                max_count = if (isDalle) 1 else 4,
                max_reference_images = if (isDalle) 0 else 4,
                aspect_ratios = listOf("auto", "1:1", "16:9", "9:16", "4:3", "3:4"),
                resolutions = listOf("auto", "1K", "2K"),
                supports_text_edit = !isDalle,
                supports_mask_edit = !isDalle,
                supports_outpaint = !isDalle,
            )
        }
    }

    // ==================== Direct Image Request & Response Helpers ====================

    fun mapOpenAIImageSize(model: String, aspectRatio: String): String {
        val isDalle3 = model.lowercase().startsWith("dall-e-3")
        return when (aspectRatio) {
            "16:9", "4:3", "3:2", "21:9" -> if (isDalle3) "1792x1024" else "1536x1024"
            "9:16", "3:4", "2:3" -> if (isDalle3) "1024x1792" else "1024x1536"
            else -> "1024x1024"
        }
    }

    fun buildOpenAIImageGenerationJson(model: String, prompt: String, count: Int, aspectRatio: String): String {
        val size = mapOpenAIImageSize(model, aspectRatio)
        return buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", count.coerceIn(1, 4))
            put("size", size)
            if (!model.lowercase().startsWith("gpt-image")) {
                put("response_format", "b64_json")
            }
        }.toString()
    }

    fun buildGeminiImageRequestJson(
        prompt: String,
        aspectRatio: String,
        inlineImages: List<DirectInlineImage>,
    ): String = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    for (img in inlineImages) {
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", img.mimeType)
                                put("data", Base64.getEncoder().encodeToString(img.bytes))
                            }
                        }
                    }
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") {
                add(JsonPrimitive("TEXT"))
                add(JsonPrimitive("IMAGE"))
            }
            if (aspectRatio != "auto" && aspectRatio.contains(":")) {
                putJsonObject("imageConfig") {
                    put("aspectRatio", aspectRatio)
                }
            }
        }
    }.toString()

    fun buildGeminiImagenPredictJson(prompt: String, count: Int, aspectRatio: String): String = buildJsonObject {
        putJsonArray("instances") {
            addJsonObject { put("prompt", prompt) }
        }
        putJsonObject("parameters") {
            put("sampleCount", count.coerceIn(1, 4))
            if (aspectRatio != "auto" && aspectRatio.contains(":")) {
                put("aspectRatio", aspectRatio)
            }
        }
    }.toString()

    fun parseOpenAIImagesJson(rawBody: String, statusCode: Int): List<DecodedDirectImage> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid image response JSON")
        checkEmbeddedError(obj, statusCode)
        val data = obj["data"] as? JsonArray ?: emptyList()
        return data.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val b64 = item["b64_json"]?.jsonPrimitive?.content
            val url = item["url"]?.jsonPrimitive?.content
            when {
                !b64.isNullOrBlank() -> DecodedDirectImage(bytes = Base64.getDecoder().decode(b64), mimeType = "image/png")
                !url.isNullOrBlank() -> DecodedDirectImage(url = url, mimeType = "image/png")
                else -> null
            }
        }
    }

    fun parseGeminiImagesJson(rawBody: String, statusCode: Int): List<DecodedDirectImage> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid Gemini image response JSON")
        checkEmbeddedError(obj, statusCode)

        val out = mutableListOf<DecodedDirectImage>()
        val candidates = obj["candidates"] as? JsonArray
        candidates?.forEach { candEl ->
            val parts = (candEl as? JsonObject)
                ?.get("content")?.jsonObject
                ?.get("parts") as? JsonArray ?: return@forEach
            for (pEl in parts) {
                val pObj = pEl as? JsonObject ?: continue
                val inline = (pObj["inlineData"] ?: pObj["inline_data"]) as? JsonObject ?: continue
                val data = inline["data"]?.jsonPrimitive?.content ?: continue
                val mime = (inline["mimeType"] ?: inline["mime_type"])?.jsonPrimitive?.content ?: "image/png"
                out += DecodedDirectImage(bytes = Base64.getDecoder().decode(data), mimeType = mime)
            }
        }
        val predictions = obj["predictions"] as? JsonArray
        predictions?.forEach { predEl ->
            val pObj = predEl as? JsonObject ?: return@forEach
            val b64 = pObj["bytesBase64Encoded"]?.jsonPrimitive?.content ?: return@forEach
            val mime = pObj["mimeType"]?.jsonPrimitive?.content ?: "image/png"
            out += DecodedDirectImage(bytes = Base64.getDecoder().decode(b64), mimeType = mime)
        }
        return out
    }

    // ==================== Embedding & Rerank Request/Response Helpers ====================

    fun buildOpenAIEmbeddingRequestJson(
        modelId: String,
        texts: List<String>,
        dimensions: Int? = null,
    ): String = buildJsonObject {
        put("model", modelId.trim())
        putJsonArray("input") {
            texts.forEach { add(JsonPrimitive(it)) }
        }
        if (dimensions != null && dimensions > 0) {
            put("dimensions", dimensions)
        }
    }.toString()

    fun parseOpenAIEmbeddingsJson(rawBody: String, statusCode: Int): List<FloatArray> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid embeddings response JSON")
        checkEmbeddedError(obj, statusCode)

        val dataArr = obj["data"] as? JsonArray
        if (dataArr != null) {
            val indexed = dataArr.mapIndexedNotNull { fallbackIdx, el ->
                val item = el as? JsonObject ?: return@mapIndexedNotNull null
                val idx = item["index"]?.jsonPrimitive?.intOrNull ?: fallbackIdx
                val embArr = item["embedding"] as? JsonArray ?: return@mapIndexedNotNull null
                val vec = parseFloatVector(embArr)
                if (vec.isEmpty()) return@mapIndexedNotNull null
                idx to vec
            }
            if (indexed.isEmpty()) {
                throw ApiException(statusCode, "EMPTY_EMBEDDING", "No embedding vectors returned")
            }
            return indexed.sortedBy { it.first }.map { it.second }
        }

        // Fallback for Ollama native /api/embed shape: {"embeddings": [[...], [...]]}
        val ollamaArr = obj["embeddings"] as? JsonArray
        if (ollamaArr != null) {
            val vectors = ollamaArr.mapNotNull { el ->
                val vecArr = el as? JsonArray ?: return@mapNotNull null
                parseFloatVector(vecArr).takeIf { it.isNotEmpty() }
            }
            if (vectors.isNotEmpty()) return vectors
        }

        throw ApiException(statusCode, "EMPTY_EMBEDDING", "No embedding vectors returned")
    }

    fun buildGeminiEmbedContentRequestJson(
        modelId: String,
        text: String,
        isQuery: Boolean,
        outputDimensionality: Int? = null,
    ): String {
        val modelRef = formatGeminiModelRef(modelId)
        val taskType = if (isQuery) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT"
        return buildJsonObject {
            put("model", modelRef)
            putJsonObject("content") {
                putJsonArray("parts") {
                    addJsonObject { put("text", text) }
                }
            }
            put("taskType", taskType)
            if (outputDimensionality != null && outputDimensionality > 0) {
                put("outputDimensionality", outputDimensionality)
            }
        }.toString()
    }

    fun buildGeminiBatchEmbedContentsRequestJson(
        modelId: String,
        texts: List<String>,
        isQuery: Boolean,
        outputDimensionality: Int? = null,
    ): String {
        val modelRef = formatGeminiModelRef(modelId)
        val taskType = if (isQuery) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT"
        return buildJsonObject {
            putJsonArray("requests") {
                for (text in texts) {
                    addJsonObject {
                        put("model", modelRef)
                        putJsonObject("content") {
                            putJsonArray("parts") {
                                addJsonObject { put("text", text) }
                            }
                        }
                        put("taskType", taskType)
                        if (outputDimensionality != null && outputDimensionality > 0) {
                            put("outputDimensionality", outputDimensionality)
                        }
                    }
                }
            }
        }.toString()
    }

    fun parseGeminiEmbeddingsJson(rawBody: String, statusCode: Int): List<FloatArray> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid Gemini embeddings JSON")
        checkEmbeddedError(obj, statusCode)

        val batchArr = obj["embeddings"] as? JsonArray
        if (batchArr != null) {
            val vectors = batchArr.mapNotNull { el ->
                val values = (el as? JsonObject)?.get("values") as? JsonArray ?: return@mapNotNull null
                parseFloatVector(values).takeIf { it.isNotEmpty() }
            }
            if (vectors.isEmpty()) {
                throw ApiException(statusCode, "EMPTY_EMBEDDING", "No Gemini embedding vectors returned")
            }
            return vectors
        }

        val singleValues = (obj["embedding"] as? JsonObject)?.get("values") as? JsonArray
        if (singleValues != null) {
            val vec = parseFloatVector(singleValues)
            if (vec.isNotEmpty()) return listOf(vec)
        }

        throw ApiException(statusCode, "EMPTY_EMBEDDING", "No Gemini embedding vectors returned")
    }

    fun buildRerankRequestJson(
        modelId: String,
        query: String,
        documents: List<String>,
        topN: Int,
        useTeiSchema: Boolean = false,
    ): String = if (useTeiSchema) {
        buildJsonObject {
            put("query", query)
            putJsonArray("texts") {
                documents.forEach { add(JsonPrimitive(it)) }
            }
            put("return_text", false)
            put("truncate", true)
        }.toString()
    } else {
        buildJsonObject {
            put("model", modelId.trim())
            put("query", query)
            putJsonArray("documents") {
                documents.forEach { add(JsonPrimitive(it)) }
            }
            if (topN > 0) {
                put("top_n", topN.coerceAtMost(documents.size.coerceAtLeast(1)))
            }
            put("return_documents", false)
        }.toString()
    }

    fun parseRerankResponseJson(rawBody: String, statusCode: Int): Map<Int, Double> {
        val root = runCatching { AppJson.parseToJsonElement(rawBody) }.getOrNull()
            ?: throw ApiException(statusCode, "INVALID_JSON", "Invalid rerank response JSON")

        val resultsArr: JsonArray = when (root) {
            is JsonArray -> root // HuggingFace TEI returns top-level [{"index": 0, "score": 0.9}]
            is JsonObject -> {
                checkEmbeddedError(root, statusCode)
                val direct = root["results"] as? JsonArray
                val nested = (root["output"] as? JsonObject)?.get("results") as? JsonArray
                val found = direct ?: nested
                if (found == null) {
                    val detail = root["detail"]?.jsonPrimitive?.content
                        ?: root["message"]?.jsonPrimitive?.content
                    if (!detail.isNullOrBlank()) {
                        throw ApiException(statusCode, "RERANK_ERROR", detail)
                    }
                    return emptyMap()
                }
                found
            }
            else -> throw ApiException(statusCode, "INVALID_JSON", "Unexpected rerank response JSON")
        }

        val scores = LinkedHashMap<Int, Double>(resultsArr.size)
        for (el in resultsArr) {
            val item = el as? JsonObject ?: continue
            val idx = item["index"]?.jsonPrimitive?.intOrNull ?: continue
            if (idx < 0) continue
            val score = item["relevance_score"]?.jsonPrimitive?.doubleOrNull
                ?: item["score"]?.jsonPrimitive?.doubleOrNull
                ?: continue
            if (score.isFinite()) {
                scores[idx] = score.coerceIn(0.0, 1.0)
            }
        }
        return scores
    }

    fun parseEmbeddingAndRerankModelsJson(
        protocol: ApiProtocol,
        rawBody: String,
    ): List<DiscoveredRetrievalModel> {
        val obj = runCatching { AppJson.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return emptyList()
        return when (protocol) {
            ApiProtocol.GEMINI -> {
                val arr = obj["models"] as? JsonArray ?: return emptyList()
                arr.mapNotNull { el ->
                    val m = el as? JsonObject ?: return@mapNotNull null
                    val rawName = m["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val id = rawName.removePrefix("models/").trim()
                    if (id.isEmpty()) return@mapNotNull null
                    val methods = (m["supportedGenerationMethods"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.content }
                        .orEmpty()
                    val supportsEmbed = methods.any {
                        it.equals("embedContent", ignoreCase = true) ||
                            it.equals("batchEmbedContents", ignoreCase = true)
                    }
                    val lower = id.lowercase()
                    if (supportsEmbed || lower.contains("embedding") || lower.contains("embed")) {
                        DiscoveredRetrievalModel(id = id, kind = DiscoveredModelKind.EMBEDDING)
                    } else {
                        null
                    }
                }.distinctBy { it.id }
            }

            ApiProtocol.OPENAI_CHAT,
            ApiProtocol.OPENAI_RESPONSES,
            ApiProtocol.ANTHROPIC -> {
                val arr = (obj["data"] as? JsonArray) ?: (obj["models"] as? JsonArray) ?: return emptyList()
                arr.mapNotNull { el ->
                    val m = el as? JsonObject ?: return@mapNotNull null
                    val id = (m["id"] ?: m["name"])?.jsonPrimitive?.content?.trim().orEmpty()
                    if (id.isEmpty()) return@mapNotNull null
                    classifyRetrievalModelId(id)
                }.distinctBy { it.id }
            }
        }
    }

    fun totalInputTokens(protocol: ApiProtocol, usage: ChatTokenUsageDto): Int? = when (protocol) {
        ApiProtocol.ANTHROPIC -> {
            val prompt = usage.prompt_tokens
            val cacheRead = usage.cache_read_input_tokens
            if (prompt != null || cacheRead != null) (prompt ?: 0) + (cacheRead ?: 0) else null
        }
        ApiProtocol.OPENAI_CHAT,
        ApiProtocol.OPENAI_RESPONSES,
        ApiProtocol.GEMINI -> usage.prompt_tokens
    }

    private fun classifyRetrievalModelId(id: String): DiscoveredRetrievalModel? {
        val lower = id.lowercase()
        if (lower.startsWith("tts-") ||
            lower.startsWith("whisper") ||
            lower.contains("moderation") ||
            lower.startsWith("dall-e") ||
            lower.startsWith("gpt-image")
        ) {
            return null
        }
        // Check rerank BEFORE embedding because bge-reranker / gte-rerank contain "bge" / "gte".
        if (lower.contains("rerank")) {
            return DiscoveredRetrievalModel(id = id, kind = DiscoveredModelKind.RERANK)
        }
        val isEmbedding = lower.contains("embedding") ||
            lower.contains("embed") ||
            lower.contains("bge") ||
            lower.contains("gte") ||
            lower.contains("m3e") ||
            E5_MODEL_RE.containsMatchIn(lower)
        return if (isEmbedding) {
            DiscoveredRetrievalModel(id = id, kind = DiscoveredModelKind.EMBEDDING)
        } else {
            null
        }
    }

    private fun formatGeminiModelRef(modelId: String): String {
        val clean = modelId.trim()
        return if (clean.startsWith("models/")) clean else "models/$clean"
    }

    private fun parseFloatVector(arr: JsonArray): FloatArray {
        val out = FloatArray(arr.size)
        for (i in arr.indices) {
            val prim = arr[i] as? JsonPrimitive ?: return FloatArray(0)
            val v = prim.floatOrNull ?: prim.doubleOrNull?.toFloat() ?: return FloatArray(0)
            if (!v.isFinite()) return FloatArray(0)
            out[i] = v
        }
        return out
    }

    // ==================== Private Helpers ====================

    private data class WirePart(val text: String? = null, val imageUrl: String? = null)

    private fun extractPlainText(elem: JsonElement): String =
        extractParts(elem).mapNotNull { it.text }.joinToString("\n").trim()

    private fun extractParts(elem: JsonElement): List<WirePart> = when (elem) {
        is JsonPrimitive -> listOf(WirePart(text = elem.content))
        is JsonArray -> elem.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.content) {
                "text", "input_text" -> obj["text"]?.jsonPrimitive?.content?.let { WirePart(text = it) }
                "image_url" -> obj["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
                    WirePart(imageUrl = it)
                }
                else -> obj["text"]?.jsonPrimitive?.content?.let { WirePart(text = it) }
            }
        }
        else -> emptyList()
    }

    private fun effortToBudgetTokens(effort: String?): Int? = when (effort?.lowercase()) {
        "low" -> 1024
        "medium" -> 4096
        "high" -> 8192
        else -> null
    }

    private fun extractGeminiPartsText(obj: JsonObject, textBuf: StringBuilder, reasonBuf: StringBuilder) {
        val candidates = obj["candidates"] as? JsonArray ?: return
        val first = candidates.firstOrNull() as? JsonObject ?: return
        val content = first["content"] as? JsonObject ?: return
        val parts = content["parts"] as? JsonArray ?: return
        for (partEl in parts) {
            val part = partEl as? JsonObject ?: continue
            val txt = part["text"]?.jsonPrimitive?.content.orEmpty()
            if (txt.isEmpty()) continue
            val isThought = part["thought"]?.jsonPrimitive?.booleanOrNull == true
            if (isThought) reasonBuf.append(txt) else textBuf.append(txt)
        }
    }

    private fun parseResponsesUsage(u: JsonObject): ChatTokenUsageDto = ChatTokenUsageDto(
        prompt_tokens = u["input_tokens"]?.jsonPrimitive?.intOrNull,
        completion_tokens = u["output_tokens"]?.jsonPrimitive?.intOrNull,
        total_tokens = u["total_tokens"]?.jsonPrimitive?.intOrNull,
        cache_read_input_tokens = u["input_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull,
    )

    private fun parseAnthropicUsage(u: JsonObject): ChatTokenUsageDto {
        val input = u["input_tokens"]?.jsonPrimitive?.intOrNull
        val output = u["output_tokens"]?.jsonPrimitive?.intOrNull
        val cacheRead = u["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull
        val cacheCreation = u["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull
        val effectivePrompt = if (input != null || cacheCreation != null) {
            (input ?: 0) + (cacheCreation ?: 0)
        } else {
            null
        }
        val totalInput = if (input != null || cacheRead != null || cacheCreation != null) {
            (input ?: 0) + (cacheRead ?: 0) + (cacheCreation ?: 0)
        } else {
            null
        }
        val total = if (totalInput != null && output != null) totalInput + output else null
        return ChatTokenUsageDto(
            prompt_tokens = effectivePrompt,
            completion_tokens = output,
            total_tokens = total,
            cache_read_input_tokens = cacheRead,
        )
    }

    private fun parseGeminiUsage(u: JsonObject): ChatTokenUsageDto = ChatTokenUsageDto(
        prompt_tokens = u["promptTokenCount"]?.jsonPrimitive?.intOrNull,
        completion_tokens = u["candidatesTokenCount"]?.jsonPrimitive?.intOrNull,
        total_tokens = u["totalTokenCount"]?.jsonPrimitive?.intOrNull,
        cache_read_input_tokens = u["cachedContentTokenCount"]?.jsonPrimitive?.intOrNull,
    )

    private fun checkEmbeddedError(obj: JsonObject, statusCode: Int) {
        val errEl = obj["error"] ?: return
        if (errEl is JsonObject) {
            val code = errEl["code"]?.jsonPrimitive?.content ?: errEl["type"]?.jsonPrimitive?.content
            val msg = errEl["message"]?.jsonPrimitive?.content ?: "API error"
            throw ApiException(statusCode, code, msg)
        } else if (errEl is JsonPrimitive && errEl.isString) {
            throw ApiException(statusCode, statusCode.toString(), errEl.content)
        }
    }
}
