package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.CoreAppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    private const val MAX_TOTAL_ZIP_EXTRACTED_BYTES = 96L * 1024L * 1024L
    private val URL_REGEX = Regex("""https?://[^\s<>"'`\u3000\uFF0C\u3002\uFF1B\uFF09\)]+""", RegexOption.IGNORE_CASE)
    private val MARKDOWN_HEADING_PREFIX_RE = Regex("^#{1,6}\\s+")

    fun extractDownloadUrl(rawInput: String): String? {
        val match = URL_REGEX.find(rawInput.trim()) ?: return null
        return match.value.trimEnd('.', ',', ';', ')', ']', '>', '"', '\'')
    }

    fun isZipBytes(bytes: ByteArray): Boolean =
        com.gugachat.app.core.util.StreamZipUtils.isZipBytes(bytes)

    fun parseBytes(bytes: ByteArray, fileName: String? = null): DeepSeekImportBundle {
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
            if (looksLikeLegacyCsv(text, fileName)) {
                val items = parseLegacyCsvText(text, sourceLabel = fileName ?: "deepseek.csv")
                if (items.isEmpty()) {
                    throw IllegalArgumentException("CSV 文件中未包含有效的 DeepSeek 对话记录")
                }
                return DeepSeekImportBundle(
                    sourceFormat = "deepseek_legacy_csv",
                    items = items.sortedByDescending { it.conversation.updatedAt },
                )
            }
            parseJsonText(text)
        }
    }

    fun parseZipBytes(zipBytes: ByteArray): DeepSeekImportBundle {
        val textEntries = LinkedHashMap<String, String>()
        var totalExtractedBytes = 0L
        com.gugachat.app.core.util.StreamZipUtils.forEachZipEntry(zipBytes, skipMacOsMeta = false) { normName, zis ->
            val lower = normName.lowercase()
            if (!lower.contains("_files/") && (lower.endsWith(".json") || lower.endsWith(".csv"))) {
                val remaining = (MAX_TOTAL_ZIP_EXTRACTED_BYTES - totalExtractedBytes).coerceAtLeast(0L)
                val cap = minOf(MAX_JSON_ENTRY_BYTES.toLong(), remaining)
                val rawBytes = com.gugachat.app.core.util.StreamZipUtils.run {
                    zis.readBoundedBytesOrThrow(cap, "导出文件解压总体积过大（超过 96MB）")
                }
                totalExtractedBytes += rawBytes.size
                val content = rawBytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                if (content.isNotEmpty()) {
                    textEntries[normName] = content
                }
            }
        }
        return parseZipTextEntries(textEntries)
    }

    internal fun parseZipTextEntries(textEntries: Map<String, String>): DeepSeekImportBundle {
        var conversationsJson: String? = null
        var sessionIndexJson: String? = null
        val v3SessionFiles = LinkedHashMap<String, String>()
        val csvFiles = LinkedHashMap<String, String>()
        val fallbackJsonFiles = LinkedHashMap<String, String>()

        textEntries.forEach { (normName, content) ->
            val lower = normName.lowercase()
            val fileName = lower.substringAfterLast('/')
            when {
                lower.endsWith(".csv") -> csvFiles[normName] = content
                !lower.endsWith(".json") -> Unit
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

        if (v3SessionFiles.isNotEmpty()) {
            val indexMeta = sessionIndexJson?.let(::parseV3IndexMetadata).orEmpty()
            val items = v3SessionFiles.mapNotNull { (fallbackId, raw) ->
                val elem = runCatching { CoreAppJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return@mapNotNull null
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

        if (csvFiles.isNotEmpty()) {
            val csvItems = ArrayList<DeepSeekImportedConversation>()
            csvFiles.forEach { (name, raw) ->
                if (looksLikeLegacyCsv(raw, name)) {
                    csvItems.addAll(parseLegacyCsvText(raw, sourceLabel = name))
                }
            }
            if (csvItems.isNotEmpty()) {
                return DeepSeekImportBundle(
                    sourceFormat = "deepseek_zip_legacy_csv",
                    items = csvItems.sortedByDescending { it.conversation.updatedAt },
                )
            }
        }

        for ((name, raw) in fallbackJsonFiles) {
            val parsed = runCatching { parseJsonText(raw) }.getOrNull()
            if (parsed != null && parsed.items.isNotEmpty()) {
                return parsed.copy(sourceFormat = "deepseek_zip_$name")
            }
        }

        throw IllegalArgumentException("ZIP 中未找到可识别的 DeepSeek 对话记录（conversations.json、chat_sessions/*.json 或旧版 chat.csv / coder.csv）")
    }

    internal fun looksLikeLegacyCsv(text: String, fileName: String? = null): Boolean {
        val firstLine = text.lineSequence().firstOrNull()?.trim()?.lowercase().orEmpty()
        val headerCols = firstLine.split(',').map { it.trim().trim('"') }
        val hasColumns = "role" in headerCols && "content" in headerCols && ("time" in headerCols || "id" in headerCols)
        if (hasColumns) return true
        val lowerName = fileName.orEmpty().lowercase().substringAfterLast('/')
        return (lowerName == "chat.csv" || lowerName == "coder.csv") && firstLine.contains(',')
    }

    internal fun parseLegacyCsvText(
        rawCsv: String,
        sourceLabel: String = "chat.csv",
    ): List<DeepSeekImportedConversation> {
        val rows = parseRfc4180Csv(rawCsv)
        if (rows.size <= 1) return emptyList()

        val headers = rows.first().map { it.trim().lowercase() }
        val idIdx = headers.indexOfFirst { it == "id" || it == "message_id" }
        val timeIdx = headers.indexOfFirst { it == "time" || it == "timestamp" || it == "created_at" }
        val roleIdx = headers.indexOfFirst { it == "role" || it == "sender" }
        val contentIdx = headers.indexOfFirst { it == "content" || it == "text" || it == "message" }
        if (roleIdx < 0 || contentIdx < 0) return emptyList()

        val baseTag = sourceLabel.substringAfterLast('/').substringBeforeLast('.').lowercase().ifBlank { "legacy" }
        val modelHint = when {
            baseTag.contains("coder") -> "deepseek-coder"
            baseTag.contains("chat") -> "deepseek-chat"
            else -> null
        }
        val now = System.currentTimeMillis()

        data class CsvMsgRow(
            val rowId: String,
            val timeMs: Long,
            val hasExplicitTime: Boolean,
            val role: String,
            val content: String,
        )

        val parsedRows = ArrayList<CsvMsgRow>()
        for (rIdx in 1 until rows.size) {
            val cols = rows[rIdx]
            if (cols.size <= maxOf(roleIdx, contentIdx)) continue
            val role = normalizeRole(cols[roleIdx]) ?: continue
            if (role != "user" && role != "assistant") continue
            val content = cols[contentIdx].trim()
            if (content.isEmpty()) continue
            val rawId = if (idIdx in cols.indices) cols[idIdx].trim() else ""
            val rawTime = if (timeIdx in cols.indices) cols[timeIdx].trim() else ""
            val explicitMs = rawTime.takeIf { it.isNotEmpty() }?.let { parseTimestamp(JsonPrimitive(it)) }
            parsedRows.add(
                CsvMsgRow(
                    rowId = rawId.ifBlank { "r$rIdx" },
                    timeMs = explicitMs ?: (now + rIdx * 1000L),
                    hasExplicitTime = explicitMs != null,
                    role = role,
                    content = content,
                ),
            )
        }
        if (parsedRows.isEmpty()) return emptyList()

        // Split flat legacy CSV stream into logical sessions when there is a >2 hour gap starting a new user turn.
        val sessionGapMs = 2L * 60L * 60L * 1000L
        val groups = ArrayList<MutableList<CsvMsgRow>>()
        var current = ArrayList<CsvMsgRow>()
        var prevTime: Long? = null
        parsedRows.forEach { row ->
            val shouldSplit = current.isNotEmpty() &&
                row.role == "user" &&
                row.hasExplicitTime &&
                prevTime != null &&
                (row.timeMs - prevTime!! > sessionGapMs || row.timeMs < prevTime!! - 60_000L)
            if (shouldSplit) {
                groups.add(current)
                current = ArrayList()
            }
            current.add(row)
            if (row.hasExplicitTime) prevTime = row.timeMs
        }
        if (current.isNotEmpty()) groups.add(current)

        return groups.mapIndexed { gIdx, group ->
            val firstId = group.first().rowId
            val sourceId = "legacy_${baseTag}_$firstId"
            val convId = "ds_$sourceId"
            val firstUserSnippet = group.firstOrNull { it.role == "user" }?.content
                ?.lineSequence()
                ?.map { it.trim().replace(MARKDOWN_HEADING_PREFIX_RE, "").trim() }
                ?.firstOrNull { line ->
                    line.isNotEmpty() && !line.startsWith("```") && line.any { ch -> ch.isLetterOrDigit() }
                }
                ?.take(28)
                .orEmpty()
            val prefix = if (baseTag.contains("coder")) "DeepSeek Coder" else "DeepSeek"
            val title = firstUserSnippet.ifBlank { "$prefix 历史对话 #${gIdx + 1}" }

            val messages = group.mapIndexed { mIdx, r ->
                ChatMessage(
                    id = "${convId}_${r.rowId}",
                    conversationId = convId,
                    role = r.role,
                    content = r.content,
                    model = if (r.role == "assistant") modelHint else null,
                    createdAt = r.timeMs,
                    seq = mIdx + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(r.content),
                )
            }
            val conv = ChatConversation(
                id = convId,
                title = title,
                mode = ConversationMode.chat,
                groupId = null,
                model = null,
                createdAt = messages.first().createdAt,
                updatedAt = messages.maxOf { it.createdAt },
            )
            DeepSeekImportedConversation(
                sourceId = sourceId,
                conversation = conv,
                messages = messages,
                hasReasoning = false,
            )
        }
    }

    private fun parseRfc4180Csv(raw: String): List<List<String>> {
        val text = raw.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length
        while (i < len) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i += 1
                    }
                } else {
                    field.append(ch)
                    i += 1
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (field.isEmpty()) {
                            inQuotes = true
                        } else {
                            field.append(ch)
                        }
                        i += 1
                    }
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        i += 1
                    }
                    '\r' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                        i += if (i + 1 < len && text[i + 1] == '\n') 2 else 1
                    }
                    '\n' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                        i += 1
                    }
                    else -> {
                        field.append(ch)
                        i += 1
                    }
                }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    fun parseJsonText(rawJson: String): DeepSeekImportBundle {
        val trimmed = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            CoreAppJson.parseToJsonElement(trimmed)
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
        val pinned: Boolean? = null,
        val insertedAt: Long? = null,
        val updatedAt: Long? = null,
        val currentMessageId: String? = null,
    )

    private fun parseV3IndexMetadata(raw: String): Map<String, V3SessionIndexMeta> {
        val root = runCatching { CoreAppJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyMap()
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
                pinned = (obj["pinned"] as? JsonPrimitive)?.booleanOrNull,
                insertedAt = parseTimestamp(obj["inserted_at"] ?: obj["created_at"] ?: obj["create_time"]),
                updatedAt = parseTimestamp(obj["updated_at"] ?: obj["update_time"]),
                currentMessageId = obj.stringField("current_message_id")
                    ?: obj.stringField("current_node")
                    ?: obj.stringField("active_message_id"),
            )
        }
        return out
    }

    private fun parseConversationObject(
        rawObj: JsonObject,
        fallbackId: String,
        indexMeta: V3SessionIndexMeta? = null,
    ): DeepSeekImportedConversation? {
        // Unwrap DeepSeek web API wrappers such as { data: { biz_data: { chat_session, chat_messages } } }
        val obj = (((rawObj["data"] as? JsonObject)?.get("biz_data") as? JsonObject)
            ?: (rawObj["biz_data"] as? JsonObject)
            ?: rawObj)
        val sessionMeta = (obj["chat_session"] as? JsonObject)
            ?: (obj["session"] as? JsonObject)

        val rawId = obj.stringField("id")
            ?: obj.stringField("conversation_id")
            ?: obj.stringField("session_id")
            ?: sessionMeta?.stringField("id")
            ?: sessionMeta?.stringField("session_id")
            ?: fallbackId
        val convId = if (rawId.startsWith("ds_")) rawId else "ds_$rawId"

        val now = System.currentTimeMillis()
        val createdAt = parseTimestamp(
            obj["inserted_at"] ?: obj["created_at"] ?: obj["create_time"]
                ?: sessionMeta?.get("inserted_at") ?: sessionMeta?.get("created_at") ?: sessionMeta?.get("create_time"),
        ) ?: indexMeta?.insertedAt ?: now
        val updatedAt = parseTimestamp(
            obj["updated_at"] ?: obj["update_time"]
                ?: sessionMeta?.get("updated_at") ?: sessionMeta?.get("update_time"),
        ) ?: indexMeta?.updatedAt ?: createdAt

        val currentNodeId = obj.stringField("current_message_id")
            ?: obj.stringField("current_node")
            ?: obj.stringField("active_message_id")
            ?: obj.stringField("current_leaf_message_id")
            ?: sessionMeta?.stringField("current_message_id")
            ?: sessionMeta?.stringField("current_node")
            ?: indexMeta?.currentMessageId

        val parsedMessages = when {
            obj["mapping"] is JsonObject -> parseMappingTree(
                mapping = obj["mapping"]!!.jsonObject,
                convId = convId,
                currentNodeId = currentNodeId,
                fallbackTimeMs = createdAt,
            )
            obj["chat_messages"] is JsonArray -> parseMessagesArray(
                arr = obj["chat_messages"] as JsonArray,
                convId = convId,
                currentNodeId = currentNodeId,
                fallbackTimeMs = createdAt,
            )
            obj["messages"] is JsonArray -> parseMessagesArray(
                arr = obj["messages"] as JsonArray,
                convId = convId,
                currentNodeId = currentNodeId,
                fallbackTimeMs = createdAt,
            )
            else -> emptyList()
        }

        if (parsedMessages.isEmpty()) return null

        val rawTitle = obj.stringField("title")?.trim().orEmpty()
            .ifBlank { sessionMeta?.stringField("title")?.trim().orEmpty() }
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

        val isPinned = (obj["pinned"] as? JsonPrimitive)?.booleanOrNull
            ?: (sessionMeta?.get("pinned") as? JsonPrimitive)?.booleanOrNull
            ?: indexMeta?.pinned
            ?: false

        val conv = ChatConversation(
            id = convId,
            title = effectiveTitle,
            mode = ConversationMode.chat,
            groupId = null,
            model = null,
            pinned = isPinned,
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
        val hasExplicitTimestamp: Boolean = false,
        val orderIndex: Int = 0,
    )

    private fun parseMappingTree(
        mapping: JsonObject,
        convId: String,
        currentNodeId: String?,
        fallbackTimeMs: Long,
    ): List<ChatMessage> {
        if (mapping.isEmpty()) return emptyList()

        val nodeIds = LinkedHashSet<String>()
        val parentOf = HashMap<String, String?>()
        val childrenOf = LinkedHashMap<String, MutableList<String>>()
        val msgOf = HashMap<String, ExtractedNodeMsg?>()
        val orderOf = HashMap<String, Int>()
        var idx = 0

        for ((key, value) in mapping) {
            val node = value as? JsonObject ?: continue
            val nodeId = node.stringField("id")?.takeIf { it.isNotBlank() } ?: key
            nodeIds.add(nodeId)
            orderOf[nodeId] = idx
            val parentId = (node.stringField("parent") ?: node.stringField("parent_id"))
                ?.takeIf { it.isNotBlank() && it != "null" }
            parentOf[nodeId] = parentId
            val kids = (node["children"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                ?.toMutableList()
                ?: mutableListOf()
            childrenOf[nodeId] = kids
            msgOf[nodeId] = extractMessageFromNode(
                nodeId = nodeId,
                msgObj = node["message"] as? JsonObject,
                fallbackTimeMs = fallbackTimeMs + idx * 1000L,
                orderIndex = idx,
            )
            idx += 1
        }

        // Supplement missing children pointers from parent links so partial/broken mapping exports still connect.
        for (nodeId in nodeIds) {
            val p = parentOf[nodeId]
            if (p != null && p in nodeIds) {
                val kids = childrenOf.getOrPut(p) { mutableListOf() }
                if (nodeId !in kids) kids.add(nodeId)
            }
        }

        val rootIds = when {
            "root" in nodeIds -> listOf("root")
            else -> nodeIds.filter { id ->
                val p = parentOf[id]
                p == null || p !in nodeIds
            }.ifEmpty { listOf(nodeIds.first()) }
        }

        return walkMessageTree(
            convId = convId,
            rootIds = rootIds,
            parentOf = parentOf,
            childrenOf = childrenOf,
            msgOf = msgOf,
            orderOf = orderOf,
            currentNodeId = currentNodeId,
        )
    }

    private fun parseMessagesArray(
        arr: JsonArray,
        convId: String,
        currentNodeId: String?,
        fallbackTimeMs: Long,
    ): List<ChatMessage> {
        val objects = arr.mapNotNull { it as? JsonObject }
        if (objects.isEmpty()) return emptyList()

        // DeepSeek V3 (`chat_sessions/*.json`) and web API exports store the full branching message tree
        // inside `messages` / `chat_messages` using `id`/`message_id` and `parent_id`/`parent` links.
        val hasTreeLinks = objects.any { obj ->
            obj.containsKey("parent_id") || obj.containsKey("parent") || obj["children"] is JsonArray
        }
        if (hasTreeLinks) {
            val nodeIds = LinkedHashSet<String>()
            val parentOf = HashMap<String, String?>()
            val childrenOf = LinkedHashMap<String, MutableList<String>>()
            val msgOf = HashMap<String, ExtractedNodeMsg?>()
            val orderOf = HashMap<String, Int>()

            objects.forEachIndexed { idx, obj ->
                val nodeId = (obj.stringField("id") ?: obj.stringField("message_id"))
                    ?.takeIf { it.isNotBlank() }
                    ?: "m$idx"
                nodeIds.add(nodeId)
                orderOf[nodeId] = idx
                val rawParent = (obj.stringField("parent_id") ?: obj.stringField("parent"))
                    ?.takeIf { it.isNotBlank() && it != "null" }
                parentOf[nodeId] = rawParent
                val explicitKids = (obj["children"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                    ?.toMutableList()
                    ?: mutableListOf()
                childrenOf[nodeId] = explicitKids
                msgOf[nodeId] = extractMessageFromNode(
                    nodeId = nodeId,
                    msgObj = obj,
                    fallbackTimeMs = fallbackTimeMs + idx * 1000L,
                    orderIndex = idx,
                )
            }

            // Build parent -> children edges preserving file order
            for (nodeId in nodeIds) {
                val p = parentOf[nodeId]
                if (p != null && p in nodeIds && p != nodeId) {
                    val kids = childrenOf.getOrPut(p) { mutableListOf() }
                    if (nodeId !in kids) kids.add(nodeId)
                }
            }

            val rootIds = nodeIds.filter { id ->
                val p = parentOf[id]
                p == null || p !in nodeIds || p == id
            }.ifEmpty { listOf(nodeIds.first()) }

            val treeMessages = walkMessageTree(
                convId = convId,
                rootIds = rootIds,
                parentOf = parentOf,
                childrenOf = childrenOf,
                msgOf = msgOf,
                orderOf = orderOf,
                currentNodeId = currentNodeId,
            )
            if (treeMessages.isNotEmpty()) {
                return treeMessages
            }
        }

        return parseFlatMessagesArray(objects, convId, fallbackTimeMs)
    }

    private fun walkMessageTree(
        convId: String,
        rootIds: List<String>,
        parentOf: Map<String, String?>,
        childrenOf: Map<String, List<String>>,
        msgOf: Map<String, ExtractedNodeMsg?>,
        orderOf: Map<String, Int>,
        currentNodeId: String?,
    ): List<ChatMessage> {
        if (rootIds.isEmpty()) return emptyList()

        // Create a virtual super-root if there are multiple root nodes (e.g. edited first prompt in V3)
        // or if the single root itself holds a real message.
        val virtualRoot = "__ds_virtual_root__"
        val effectiveChildren = HashMap<String, List<String>>(childrenOf.size + 1)
        childrenOf.forEach { (k, v) -> effectiveChildren[k] = v }
        val startNodeId = if (rootIds.size == 1 && msgOf[rootIds.first()] == null) {
            rootIds.first()
        } else {
            effectiveChildren[virtualRoot] = rootIds
            virtualRoot
        }

        // Compute best active tip when currentNodeId is absent or invalid.
        val targetNodeId = currentNodeId?.takeIf { it in msgOf || it in effectiveChildren }
            ?: selectActiveLeafNode(
                startNodeId = startNodeId,
                childrenOf = effectiveChildren,
                msgOf = msgOf,
                orderOf = orderOf,
            )

        val preferredAncestors = HashSet<String>()
        if (targetNodeId != null) {
            var cur: String? = targetNodeId
            val guard = HashSet<String>()
            while (cur != null && guard.add(cur)) {
                preferredAncestors.add(cur)
                cur = parentOf[cur]
            }
        }

        val result = ArrayList<ChatMessage>()
        val visited = HashSet<String>()
        var seq = 0

        fun walkFrom(nodeId: String) {
            if (!visited.add(nodeId)) return
            val kids = effectiveChildren[nodeId].orEmpty().filter { it !in visited && (it in msgOf || it in effectiveChildren) }
            if (kids.isEmpty()) return

            val chosenChildId = kids.firstOrNull { it in preferredAncestors } ?: kids.last()
            val siblingMsgs = kids.mapNotNull { kidId -> msgOf[kidId] }
            val chosenMsg = siblingMsgs.firstOrNull { it.nodeId == chosenChildId }

            if (chosenMsg != null) {
                seq += 1
                val assistantSiblings = if (chosenMsg.role == "assistant" && siblingMsgs.size > 1) {
                    siblingMsgs
                        .filter { it.role == "assistant" }
                        .sortedWith(
                            compareBy<ExtractedNodeMsg>(
                                { if (it.hasExplicitTimestamp) it.createdAt else Long.MAX_VALUE },
                                { it.nodeId.toLongOrNull() ?: Long.MAX_VALUE },
                                { it.orderIndex },
                            ),
                        )
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

        walkFrom(startNodeId)
        return result
    }

    private data class LeafCandidate(
        val nodeId: String,
        val maxExplicitTs: Long,
        val depth: Int,
        val numericId: Long,
        val orderIndex: Int,
    )

    private fun selectActiveLeafNode(
        startNodeId: String,
        childrenOf: Map<String, List<String>>,
        msgOf: Map<String, ExtractedNodeMsg?>,
        orderOf: Map<String, Int>,
    ): String? {
        var best: LeafCandidate? = null
        val visited = HashSet<String>()

        fun dfs(nodeId: String, depth: Int, maxExplicitTs: Long) {
            if (!visited.add(nodeId)) return
            val msg = msgOf[nodeId]
            val nextDepth = if (msg != null) depth + 1 else depth
            val nextTs = if (msg != null && msg.hasExplicitTimestamp) {
                maxOf(maxExplicitTs, msg.createdAt)
            } else {
                maxExplicitTs
            }
            val kids = childrenOf[nodeId].orEmpty().filter { it !in visited && (it in msgOf || it in childrenOf) }
            if (kids.isEmpty()) {
                if (msg != null) {
                    val cand = LeafCandidate(
                        nodeId = nodeId,
                        maxExplicitTs = nextTs,
                        depth = nextDepth,
                        numericId = nodeId.toLongOrNull() ?: Long.MIN_VALUE,
                        orderIndex = orderOf[nodeId] ?: -1,
                    )
                    val curBest = best
                    if (curBest == null || isBetterLeaf(cand, curBest)) {
                        best = cand
                    }
                }
            } else {
                for (kid in kids) {
                    dfs(kid, nextDepth, nextTs)
                }
            }
            visited.remove(nodeId)
        }

        dfs(startNodeId, depth = 0, maxExplicitTs = 0L)
        return best?.nodeId
    }

    private fun isBetterLeaf(a: LeafCandidate, b: LeafCandidate): Boolean {
        if (a.maxExplicitTs != b.maxExplicitTs) return a.maxExplicitTs > b.maxExplicitTs
        if (a.depth != b.depth) return a.depth > b.depth
        if (a.numericId != b.numericId) return a.numericId > b.numericId
        return a.orderIndex > b.orderIndex
    }

    private fun parseFlatMessagesArray(
        objects: List<JsonObject>,
        convId: String,
        fallbackTimeMs: Long,
    ): List<ChatMessage> {
        val extractedList = objects.mapIndexedNotNull { idx, obj ->
            val nodeId = (obj.stringField("id") ?: obj.stringField("message_id"))
                ?.takeIf { it.isNotBlank() }
                ?: "m$idx"
            extractMessageFromNode(nodeId, obj, fallbackTimeMs + idx * 1000L, orderIndex = idx)
        }
        if (extractedList.isEmpty()) return emptyList()

        // Fold consecutive assistant entries (produced by flattened exports that stripped parent_id)
        // into variants of a single assistant turn so regenerated replies never spam the chat timeline.
        val out = ArrayList<ChatMessage>()
        var i = 0
        while (i < extractedList.size) {
            val cur = extractedList[i]
            if (cur.role == "assistant") {
                val run = ArrayList<ExtractedNodeMsg>()
                while (i < extractedList.size && extractedList[i].role == "assistant") {
                    run.add(extractedList[i])
                    i += 1
                }
                val chosen = run.last()
                val variants = if (run.size > 1) {
                    run.map { sib ->
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
                out.add(
                    ChatMessage(
                        id = "${convId}_${chosen.nodeId}",
                        conversationId = convId,
                        role = chosen.role,
                        content = chosen.content,
                        reasoning = chosen.reasoning,
                        model = chosen.model,
                        createdAt = chosen.createdAt,
                        seq = out.size + 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(chosen.content),
                        variants = variants,
                        activeVariantIndex = if (variants.isNotEmpty()) variants.lastIndex else 0,
                    ),
                )
            } else {
                out.add(
                    ChatMessage(
                        id = "${convId}_${cur.nodeId}",
                        conversationId = convId,
                        role = cur.role,
                        content = cur.content,
                        reasoning = cur.reasoning,
                        model = cur.model,
                        createdAt = cur.createdAt,
                        seq = out.size + 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(cur.content),
                    ),
                )
                i += 1
            }
        }
        return out
    }

    private fun extractMessageFromNode(
        nodeId: String,
        msgObj: JsonObject?,
        fallbackTimeMs: Long,
        orderIndex: Int = 0,
    ): ExtractedNodeMsg? {
        if (msgObj == null) return null

        val explicitTs = parseTimestamp(
            msgObj["inserted_at"] ?: msgObj["created_at"] ?: msgObj["create_time"],
        )
        val createdAt = explicitTs ?: fallbackTimeMs
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
            val templateParts = ArrayList<String>()
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
                    "TEMPLATE_RESPONSE" -> {
                        if (inferredRole == null) inferredRole = "assistant"
                        if (text.isNotBlank()) templateParts.add(text)
                    }
                    "FILE", "READ_FILE" -> {
                        if (inferredRole == null) inferredRole = "user"
                        val nestedFiles = frag["files"] as? JsonArray
                        if (nestedFiles != null && nestedFiles.isNotEmpty()) {
                            nestedFiles.forEach { fElem ->
                                val fObj = fElem as? JsonObject
                                val fname = fObj?.stringField("file_name")
                                    ?: fObj?.stringField("name")
                                    ?: (fElem as? JsonPrimitive)?.contentOrNull
                                if (!fname.isNullOrBlank()) {
                                    val isImg = (fObj?.get("is_image") as? JsonPrimitive)?.booleanOrNull == true ||
                                        fname.lowercase().let {
                                            it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                                                it.endsWith(".webp") || it.endsWith(".gif")
                                        }
                                    val label = if (isImg) "[图片: $fname]" else "[附件: $fname]"
                                    contentParts.add(label)
                                }
                            }
                        } else {
                            val fname = frag.stringField("file_name") ?: frag.stringField("name")
                            if (!fname.isNullOrBlank()) contentParts.add("[附件: $fname]")
                            else if (text.isNotBlank()) contentParts.add(text)
                        }
                    }
                    "CONTENT_RISK", "SEARCH", "TOOL_SEARCH", "TOOL_OPEN", "SEARCH_RESULT", "PARTIAL_FILE_READ", "TIP" -> {
                        // Skip AI-generated disclaimers, raw search/webpage-open telemetry, and partial-read metadata
                    }
                    else -> {
                        if (text.isNotBlank()) contentParts.add(text)
                    }
                }
            }

            if (contentParts.isEmpty() && templateParts.isNotEmpty()) {
                contentParts.addAll(templateParts)
            }

            if (reasoningParts.isEmpty()) {
                val topReasoning = msgObj.stringField("reasoning_content")
                    ?: msgObj.stringField("thinking_content")
                    ?: msgObj.stringField("reasoning")
                    ?: msgObj.stringField("thinking")
                if (!topReasoning.isNullOrBlank()) {
                    reasoningParts.add(topReasoning.trim())
                }
            }

            if (fileNotes.isNotEmpty() && inferredRole == "user") {
                val prefix = fileNotes.joinToString(" ") { "[附件: $it]" }
                if (contentParts.none { it.contains(prefix) }) {
                    contentParts.add(0, prefix)
                }
            }

            val role = inferredRole ?: return null
            if (role == "system" || role == "tool") return null
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
                hasExplicitTimestamp = explicitTs != null,
                orderIndex = orderIndex,
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
            hasExplicitTimestamp = explicitTs != null,
            orderIndex = orderIndex,
        )
    }

    private fun normalizeRole(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "user", "human", "request" -> "user"
        "assistant", "ai", "model", "response", "deepseek" -> "assistant"
        "system" -> "system"
        "tool" -> "tool"
        else -> null
    }

    private fun parseTimestamp(elem: JsonElement?): Long? =
        com.gugachat.app.core.util.JsonUtils.parseFlexibleTimestamp(elem)

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
