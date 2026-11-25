package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipInputStream

data class DeepSeekImportedConversation(
    val sourceId: String,
    val conversation: ChatConversation,
    val messages: List<ChatMessage>,
    val hasReasoning: Boolean,
)

data class DeepSeekImportBundle(
    val sourceFormat: String,
    val items: List<DeepSeekImportedConversation>,
)

object DeepSeekImportCodec {
    private const val MAX_JSON_ENTRY_BYTES = 64 * 1024 * 1024
    private val URL_REGEX = Regex("""https?://[^\s<>"'`\u3000\uFF0C\u3002\uFF1B\uFF09\)]+""", RegexOption.IGNORE_CASE)

    fun extractDownloadUrl(rawInput: String): String? {
        val match = URL_REGEX.find(rawInput.trim()) ?: return null
        return match.value.trimEnd('.', ',', ';', ')', ']', '>', '"', '\'')
    }

    fun isZipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    fun parseBytes(bytes: ByteArray): DeepSeekImportBundle {
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Empty file")
        }
        return if (isZipBytes(bytes)) {
            parseZipBytes(bytes)
        } else {
            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
            if (text.startsWith("<!DOCTYPE", ignoreCase = true) || text.startsWith("<html", ignoreCase = true)) {
                throw IllegalArgumentException("HTML_RESPONSE")
            }
            parseJsonText(text)
        }
    }

    fun parseZipBytes(zipBytes: ByteArray): DeepSeekImportBundle {
        var conversationsJson: String? = null
        var sessionIndexJson: String? = null
        val v3SessionFiles = LinkedHashMap<String, String>()
        val fallbackJsonFiles = LinkedHashMap<String, String>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val normName = entry.name.replace('\\', '/').trimStart('/')
                val lower = normName.lowercase()
                if (!lower.endsWith(".json")) {
                    zis.closeEntry()
                    continue
                }
                val content = readBoundedUtf8(zis, MAX_JSON_ENTRY_BYTES).removePrefix("\uFEFF").trim()
                zis.closeEntry()
                if (content.isEmpty()) continue

                val fileName = lower.substringAfterLast('/')
                when {
                    fileName == "conversations.json" -> conversationsJson = content
                    fileName == "chat_session_index.json" -> sessionIndexJson = content
                    lower.contains("chat_sessions/") && !lower.contains("_files/") -> {
                        val sessionKey = normName.substringAfterLast('/').removeSuffix(".json")
                        v3SessionFiles[sessionKey] = content
                    }
                    fileName != "manifest.json" && fileName != "user.json" && fileName != "user_info.json" -> {
                        fallbackJsonFiles[normName] = content
                    }
                }
            }
        }

        if (v3SessionFiles.isNotEmpty()) {
            val indexMeta = sessionIndexJson?.let(::parseV3IndexMetadata).orEmpty()
            val items = v3SessionFiles.mapNotNull { (fallbackId, raw) ->
                val elem = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return@mapNotNull null
                val meta = indexMeta[fallbackId] ?: elem.stringField("id")?.let { indexMeta[it] }
                parseConversationObject(elem, fallbackId = fallbackId, indexMeta = meta)
            }.sortedByDescending { it.conversation.updatedAt }
            if (items.isNotEmpty()) {
                return DeepSeekImportBundle(sourceFormat = "deepseek_zip_v3", items = items)
            }
        }

        conversationsJson?.let { raw ->
            val bundle = parseJsonText(raw)
            if (bundle.items.isNotEmpty()) {
                return bundle.copy(sourceFormat = "deepseek_zip_conversations")
            }
        }

        for ((name, raw) in fallbackJsonFiles) {
            val parsed = runCatching { parseJsonText(raw) }.getOrNull()
            if (parsed != null && parsed.items.isNotEmpty()) {
                return parsed.copy(sourceFormat = "deepseek_zip_$name")
            }
        }

        throw IllegalArgumentException("ZIP 中未找到可识别的 DeepSeek 对话记录（conversations.json 或 chat_sessions/*.json）")
    }

    fun parseJsonText(rawJson: String): DeepSeekImportBundle {
        val trimmed = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            AppJson.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的 JSON 格式：${e.message}")
        }

        val items = when (root) {
            is JsonArray -> root.mapIndexedNotNull { idx, elem ->
                (elem as? JsonObject)?.let { parseConversationObject(it, fallbackId = "conv_$idx") }
            }
            is JsonObject -> {
                val listField = (root["conversations"] as? JsonArray)
                    ?: (root["data"] as? JsonArray)
                    ?: ((root["data"] as? JsonObject)?.get("conversations") as? JsonArray)
                    ?: (root["sessions"] as? JsonArray)
                if (listField != null) {
                    listField.mapIndexedNotNull { idx, elem ->
                        (elem as? JsonObject)?.let { parseConversationObject(it, fallbackId = "conv_$idx") }
                    }
                } else {
                    listOfNotNull(parseConversationObject(root, fallbackId = "single_0"))
                }
            }
            else -> emptyList()
        }.sortedByDescending { it.conversation.updatedAt }

        if (items.isEmpty()) {
            throw IllegalArgumentException("未解析到有效的 DeepSeek 对话内容")
        }

        return DeepSeekImportBundle(
            sourceFormat = if (root is JsonArray) "deepseek_conversations_json" else "deepseek_single_json",
            items = items,
        )
    }

    private data class V3SessionIndexMeta(
        val title: String? = null,
        val insertedAt: Long? = null,
        val updatedAt: Long? = null,
    )

    private fun parseV3IndexMetadata(raw: String): Map<String, V3SessionIndexMeta> {
        val root = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyMap()
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["sessions"] as? JsonArray) ?: (root["chat_sessions"] as? JsonArray) ?: (root["items"] as? JsonArray)
            else -> null
        } ?: return emptyMap()

        val out = LinkedHashMap<String, V3SessionIndexMeta>()
        arr.forEach { elem ->
            val obj = elem as? JsonObject ?: return@forEach
            val id = obj.stringField("id") ?: obj.stringField("session_id") ?: return@forEach
            out[id] = V3SessionIndexMeta(
                title = obj.stringField("title"),
                insertedAt = parseTimestamp(obj["inserted_at"] ?: obj["created_at"] ?: obj["create_time"]),
                updatedAt = parseTimestamp(obj["updated_at"] ?: obj["update_time"]),
            )
        }
        return out
    }

    private fun parseConversationObject(
        obj: JsonObject,
        fallbackId: String,
        indexMeta: V3SessionIndexMeta? = null,
    ): DeepSeekImportedConversation? {
        val rawId = obj.stringField("id")
            ?: obj.stringField("conversation_id")
            ?: obj.stringField("session_id")
            ?: fallbackId
        val convId = if (rawId.startsWith("ds_")) rawId else "ds_$rawId"

        val now = System.currentTimeMillis()
        val createdAt = parseTimestamp(obj["inserted_at"] ?: obj["created_at"] ?: obj["create_time"])
            ?: indexMeta?.insertedAt
            ?: now
        val updatedAt = parseTimestamp(obj["updated_at"] ?: obj["update_time"])
            ?: indexMeta?.updatedAt
            ?: createdAt

        val parsedMessages = when {
            obj["mapping"] is JsonObject -> parseMappingTree(
                mapping = obj["mapping"]!!.jsonObject,
                convId = convId,
                currentNodeId = obj.stringField("current_node"),
                fallbackTimeMs = createdAt,
            )
            obj["chat_messages"] is JsonArray -> parseFlatMessagesArray(
                arr = obj["chat_messages"] as JsonArray,
                convId = convId,
                fallbackTimeMs = createdAt,
            )
            obj["messages"] is JsonArray -> parseFlatMessagesArray(
                arr = obj["messages"] as JsonArray,
                convId = convId,
                fallbackTimeMs = createdAt,
            )
            else -> emptyList()
        }

        if (parsedMessages.isEmpty()) return null

        val rawTitle = obj.stringField("title")?.trim().orEmpty()
            .ifBlank { indexMeta?.title?.trim().orEmpty() }
        val effectiveTitle = rawTitle.ifBlank {
            parsedMessages.firstOrNull { it.role == "user" }?.content
                ?.lineSequence()?.firstOrNull()?.trim()?.take(24)
                .orEmpty()
                .ifBlank { "DeepSeek 对话" }
        }

        val lastMsgTime = parsedMessages.maxOfOrNull { it.createdAt } ?: updatedAt
        val effectiveUpdatedAt = maxOf(updatedAt, lastMsgTime)
        val hasReasoning = parsedMessages.any { it.reasoning.isNotBlank() }

        val conv = ChatConversation(
            id = convId,
            title = effectiveTitle,
            mode = ConversationMode.chat,
            groupId = null,
            model = null,
            createdAt = minOf(createdAt, parsedMessages.first().createdAt),
            updatedAt = effectiveUpdatedAt,
        )

        return DeepSeekImportedConversation(
            sourceId = rawId,
            conversation = conv,
            messages = parsedMessages,
            hasReasoning = hasReasoning,
        )
    }

    private data class ExtractedNodeMsg(
        val nodeId: String,
        val role: String,
        val content: String,
        val reasoning: String,
        val model: String?,
        val createdAt: Long,
    )

    private fun parseMappingTree(
        mapping: JsonObject,
        convId: String,
        currentNodeId: String?,
        fallbackTimeMs: Long,
    ): List<ChatMessage> {
        if (mapping.isEmpty()) return emptyList()

        val parentOf = HashMap<String, String?>()
        val childrenOf = HashMap<String, List<String>>()
        for ((key, value) in mapping) {
            val node = value as? JsonObject ?: continue
            val nodeId = node.stringField("id") ?: key
            parentOf[nodeId] = node.stringField("parent")
            val kids = (node["children"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
            childrenOf[nodeId] = kids
        }

        // If current_node exists in mapping, collect its ancestor path so we follow the exact active branch.
        val preferredAncestors = HashSet<String>()
        if (!currentNodeId.isNullOrBlank() && mapping.containsKey(currentNodeId)) {
            var cur: String? = currentNodeId
            val guard = HashSet<String>()
            while (cur != null && guard.add(cur)) {
                preferredAncestors.add(cur)
                cur = parentOf[cur]
            }
        }

        val rootId = when {
            mapping.containsKey("root") -> "root"
            else -> mapping.keys.firstOrNull { k ->
                val p = parentOf[k]
                p == null || !mapping.containsKey(p)
            } ?: mapping.keys.first()
        }

        val result = ArrayList<ChatMessage>()
        val visited = HashSet<String>()
        var seq = 0

        fun walkFrom(nodeId: String) {
            if (!visited.add(nodeId)) return
            val node = mapping[nodeId] as? JsonObject ?: return
            val kids = childrenOf[nodeId].orEmpty().filter { mapping.containsKey(it) && it !in visited }
            if (kids.isEmpty()) return

            val chosenChildId = kids.firstOrNull { it in preferredAncestors }
                ?: kids.last()

            // Extract chosen child message; if multiple assistant siblings exist, preserve them as variants!
            val siblingMsgs = kids.mapNotNull { kidId ->
                val kidNode = mapping[kidId] as? JsonObject ?: return@mapNotNull null
                extractMessageFromNode(kidId, kidNode["message"] as? JsonObject, fallbackTimeMs)
            }
            val chosenMsg = siblingMsgs.firstOrNull { it.nodeId == chosenChildId }

            if (chosenMsg != null) {
                seq += 1
                val assistantSiblings = if (chosenMsg.role == "assistant" && siblingMsgs.size > 1) {
                    siblingMsgs.filter { it.role == "assistant" }
                } else {
                    emptyList()
                }
                val variants = if (assistantSiblings.size > 1) {
                    assistantSiblings.map { sib ->
                        MessageVariant(
                            id = "${convId}_${sib.nodeId}",
                            content = sib.content,
                            reasoning = sib.reasoning,
                            createdAt = sib.createdAt,
                            model = sib.model,
                        )
                    }
                } else {
                    emptyList()
                }
                val activeVariantIdx = if (variants.isNotEmpty()) {
                    assistantSiblings.indexOfFirst { it.nodeId == chosenMsg.nodeId }.coerceAtLeast(0)
                } else {
                    0
                }
                result.add(
                    ChatMessage(
                        id = "${convId}_${chosenMsg.nodeId}",
                        conversationId = convId,
                        role = chosenMsg.role,
                        content = chosenMsg.content,
                        reasoning = chosenMsg.reasoning,
                        model = chosenMsg.model,
                        createdAt = chosenMsg.createdAt,
                        seq = seq,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(chosenMsg.content),
                        variants = variants,
                        activeVariantIndex = activeVariantIdx,
                    ),
                )
            }

            walkFrom(chosenChildId)
        }

        // Handle case where root itself carries a message
        val rootNode = mapping[rootId] as? JsonObject
        val rootMsg = extractMessageFromNode(rootId, rootNode?.get("message") as? JsonObject, fallbackTimeMs)
        if (rootMsg != null) {
            seq += 1
            result.add(
                ChatMessage(
                    id = "${convId}_${rootMsg.nodeId}",
                    conversationId = convId,
                    role = rootMsg.role,
                    content = rootMsg.content,
                    reasoning = rootMsg.reasoning,
                    model = rootMsg.model,
                    createdAt = rootMsg.createdAt,
                    seq = seq,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(rootMsg.content),
                ),
            )
        }

        walkFrom(rootId)
        return result
    }

    private fun parseFlatMessagesArray(
        arr: JsonArray,
        convId: String,
        fallbackTimeMs: Long,
    ): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        arr.forEachIndexed { idx, elem ->
            val obj = elem as? JsonObject ?: return@forEachIndexed
            val nodeId = obj.stringField("id") ?: obj.stringField("message_id") ?: "m$idx"
            val extracted = extractMessageFromNode(nodeId, obj, fallbackTimeMs + idx * 1000L) ?: return@forEachIndexed
            out.add(
                ChatMessage(
                    id = "${convId}_${extracted.nodeId}",
                    conversationId = convId,
                    role = extracted.role,
                    content = extracted.content,
                    reasoning = extracted.reasoning,
                    model = extracted.model,
                    createdAt = extracted.createdAt,
                    seq = out.size + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(extracted.content),
                ),
            )
        }
        return out
    }

    private fun extractMessageFromNode(
        nodeId: String,
        msgObj: JsonObject?,
        fallbackTimeMs: Long,
    ): ExtractedNodeMsg? {
        if (msgObj == null) return null

        val createdAt = parseTimestamp(
            msgObj["inserted_at"] ?: msgObj["created_at"] ?: msgObj["create_time"],
        ) ?: fallbackTimeMs
        val model = msgObj.stringField("model")
            ?: (msgObj["metadata"] as? JsonObject)?.stringField("model_slug")

        val fileNotes = (msgObj["files"] as? JsonArray)?.mapNotNull { f ->
            val fObj = f as? JsonObject
            fObj?.stringField("file_name")
                ?: fObj?.stringField("name")
                ?: (f as? JsonPrimitive)?.contentOrNull
        }.orEmpty()

        val fragments = msgObj["fragments"] as? JsonArray
        if (fragments != null && fragments.isNotEmpty()) {
            var inferredRole: String? = normalizeRole(msgObj.stringField("role"))
            val contentParts = ArrayList<String>()
            val reasoningParts = ArrayList<String>()

            fragments.forEach { fragElem ->
                val frag = fragElem as? JsonObject ?: return@forEach
                val type = frag.stringField("type")?.uppercase().orEmpty()
                val text = frag.stringField("content").orEmpty()
                when (type) {
                    "REQUEST", "USER" -> {
                        if (inferredRole == null) inferredRole = "user"
                        if (text.isNotBlank()) contentParts.add(text)
                    }
                    "THINK", "THINKING", "REASONING" -> {
                        if (inferredRole == null) inferredRole = "assistant"
                        if (text.isNotBlank()) reasoningParts.add(text)
                    }
                    "RESPONSE", "ASSISTANT", "ANSWER", "TEXT" -> {
                        if (inferredRole == null) inferredRole = "assistant"
                        if (text.isNotBlank()) contentParts.add(text)
                    }
                    "FILE" -> {
                        val fname = frag.stringField("file_name") ?: frag.stringField("name")
                        if (!fname.isNullOrBlank()) contentParts.add("[附件: $fname]")
                        else if (text.isNotBlank()) contentParts.add(text)
                    }
                    "SEARCH", "TOOL_SEARCH" -> {
                        // Skip raw web search telemetry unless there is no other content
                    }
                    else -> {
                        if (text.isNotBlank()) contentParts.add(text)
                    }
                }
            }

            if (fileNotes.isNotEmpty() && inferredRole == "user") {
                val prefix = fileNotes.joinToString(" ") { "[附件: $it]" }
                if (contentParts.none { it.contains(prefix) }) {
                    contentParts.add(0, prefix)
                }
            }

            val role = inferredRole ?: return null
            val body = contentParts.joinToString("\n\n").trim()
            val reasoning = reasoningParts.joinToString("\n\n").trim()
            if (body.isEmpty() && reasoning.isEmpty()) return null
            return ExtractedNodeMsg(
                nodeId = nodeId,
                role = role,
                content = body,
                reasoning = reasoning,
                model = if (role == "assistant") model else null,
                createdAt = createdAt,
            )
        }

        // Fallback: direct role + content (DeepSeek flat export or ChatGPT/generic format)
        val role = normalizeRole(
            msgObj.stringField("role")
                ?: (msgObj["author"] as? JsonObject)?.stringField("role"),
        ) ?: return null
        if (role == "system" || role == "tool") return null

        val reasoning = msgObj.stringField("reasoning_content")
            ?: msgObj.stringField("thinking_content")
            ?: msgObj.stringField("reasoning")
            ?: msgObj.stringField("thinking")
            ?: ""

        val contentField = msgObj["content"]
        val contentText = when (contentField) {
            is JsonPrimitive -> contentField.contentOrNull.orEmpty()
            is JsonObject -> {
                val parts = contentField["parts"] as? JsonArray
                parts?.mapNotNull { part ->
                    when (part) {
                        is JsonPrimitive -> part.contentOrNull
                        is JsonObject -> part.stringField("text") ?: part.stringField("content")
                        else -> null
                    }
                }?.joinToString("\n").orEmpty()
            }
            is JsonArray -> contentField.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> part.stringField("text") ?: part.stringField("content")
                    else -> null
                }
            }.joinToString("\n")
            else -> ""
        }.trim()

        val withFiles = if (fileNotes.isNotEmpty() && role == "user") {
            val prefix = fileNotes.joinToString(" ") { "[附件: $it]" }
            if (contentText.isEmpty()) prefix else "$prefix\n\n$contentText"
        } else {
            contentText
        }

        if (withFiles.isEmpty() && reasoning.isBlank()) return null

        return ExtractedNodeMsg(
            nodeId = nodeId.ifBlank { UUID.randomUUID().toString() },
            role = role,
            content = withFiles,
            reasoning = reasoning.trim(),
            model = if (role == "assistant") model else null,
            createdAt = createdAt,
        )
    }

    private fun normalizeRole(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "user", "human", "request" -> "user"
        "assistant", "ai", "model", "response", "deepseek" -> "assistant"
        "system" -> "system"
        "tool" -> "tool"
        else -> null
    }

    private fun parseTimestamp(elem: JsonElement?): Long? {
        val prim = elem as? JsonPrimitive ?: return null
        if (prim.isString) {
            val s = prim.content.trim()
            if (s.isEmpty()) return null
            s.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000L else it }
            s.toDoubleOrNull()?.let { d ->
                val v = d.toLong()
                return if (v < 10_000_000_000L) (d * 1000.0).toLong() else v
            }
            runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            runCatching { return Instant.parse(s).toEpochMilli() }
            runCatching {
                val normalized = s.replace(' ', 'T')
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            return null
        }
        prim.longOrNull?.let { return if (it < 10_000_000_000L) it * 1000L else it }
        prim.doubleOrNull?.let { d ->
            val v = d.toLong()
            return if (v < 10_000_000_000L) (d * 1000.0).toLong() else v
        }
        return null
    }

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun readBoundedUtf8(zis: ZipInputStream, maxBytes: Int): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zis.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("导出文件体积过大（超过 64MB）")
            }
            baos.write(buf, 0, read)
        }
        return baos.toString(Charsets.UTF_8.name())
    }
}
