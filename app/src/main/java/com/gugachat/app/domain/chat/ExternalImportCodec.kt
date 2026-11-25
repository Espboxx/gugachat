package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
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

enum class ExternalImportSource {
    DEEPSEEK,
    SILLYTAVERN,
    CHENGGUANG,
    CHATBOX,
    CHATGPT,
    CLAUDE,
    GENERIC,
}

data class ExternalImportedItem(
    val source: ExternalImportSource,
    val sourceId: String,
    val conversation: ChatConversation,
    val messages: List<ChatMessage>,
    val loreEntries: List<NovelLoreEntry> = emptyList(),
    val novelChapters: List<NovelChapter> = emptyList(),
    val hasReasoning: Boolean = false,
)

data class ExternalImportBundle(
    val source: ExternalImportSource,
    val sourceFormat: String,
    val items: List<ExternalImportedItem>,
)

object ExternalImportCodec {
    private const val MAX_ENTRY_BYTES = 64 * 1024 * 1024

    private val CHAPTER_HEADING_RE = Regex(
        """^(?:#{1,3}\s+)?(第[零一二三四五六七八九十百千0-9]+[章回节幕卷].*|Chapter\s+\d+.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val SPEAKER_BRACKET_RE = Regex("""^【([^】]{1,18})】\s*[:：]?\s*(.+)$""")
    private val SPEAKER_COLON_RE = Regex("""^([\u4e00-\u9fa5A-Za-z0-9_·]{1,14})[：:]\s*(.+)$""")
    private val CHOICE_LINE_RE = Regex("""^(?:[-*•]\s*)?(?:选项|分支|选择)?\s*(?:[A-Da-d]|[1-6])[.、)）:：]\s*(.+)$""")
    private val MD_USER_HEADING_RE = Regex("""^(?:#{1,4}\s*)?(?:\*\*)?(?:User|Human|You|用户|我|提问)(?:\*\*)?\s*[:：]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val MD_ASSISTANT_HEADING_RE = Regex("""^(?:#{1,4}\s*)?(?:\*\*)?(?:Assistant|AI|DeepSeek|ChatGPT|Claude|Bot|助手|模型|回复)(?:\*\*)?\s*[:：]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parseBytes(
        bytes: ByteArray,
        fileName: String? = null,
        hintSource: ExternalImportSource? = null,
    ): ExternalImportBundle {
        if (bytes.isEmpty()) throw IllegalArgumentException("文件内容为空")

        // 1. SillyTavern PNG Character Card (ccv3 / chara chunk)
        if (RpCardCodec.isPngBytes(bytes)) {
            val jsonText = RpCardCodec.extractCardJsonFromPng(bytes)
                ?: throw IllegalArgumentException("PNG 图片中未检测到酒馆角色卡元数据（ccv3 / chara）")
            return parseSillyTavernCardJson(jsonText, fileName)
        }

        // 2. ZIP Archive (DeepSeek, ChatGPT, Claude, SillyTavern bundle, Chengguang pack)
        if (DeepSeekImportCodec.isZipBytes(bytes)) {
            return parseZipBytes(bytes, hintSource)
        }

        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw IllegalArgumentException("文件内容为空")
        if (text.startsWith("<!DOCTYPE", ignoreCase = true) || text.startsWith("<html", ignoreCase = true)) {
            throw IllegalArgumentException("HTML_RESPONSE")
        }

        val lowerName = fileName.orEmpty().lowercase()

        // 3. SillyTavern JSONL chat log (.jsonl or multi-line JSON objects with is_user / mes)
        if (lowerName.endsWith(".jsonl") || looksLikeSillyTavernJsonl(text)) {
            return parseSillyTavernJsonl(text, fileName)
        }

        // 4. JSON payload (auto-detect Chatbox, SillyTavern Card/Lore, Claude, Chengguang JSON, ChatGPT, DeepSeek)
        if (text.startsWith("{") || text.startsWith("[")) {
            return parseJsonText(text, fileName, hintSource)
        }

        // 5. Plain text / Markdown (Chengguang script or generic User/Assistant transcript)
        return parseTextOrScript(text, fileName, hintSource)
    }

    fun parsePastedScript(
        rawText: String,
        titleHint: String? = null,
        preferNovel: Boolean = false,
    ): ExternalImportBundle {
        val clean = rawText.trim()
        if (clean.isEmpty()) throw IllegalArgumentException("请先输入或粘贴剧本内容")
        if (clean.startsWith("{") || clean.startsWith("[")) {
            return parseJsonText(clean, titleHint, ExternalImportSource.CHENGGUANG)
        }
        return parseChengguangScriptText(clean, titleHint, preferNovel)
    }

    fun applyTargetMode(
        item: ExternalImportedItem,
        targetMode: ConversationMode?,
    ): ExternalImportedItem {
        if (targetMode == null || targetMode == item.conversation.mode) return item
        val conv = item.conversation
        val now = conv.updatedAt
        return when (targetMode) {
            ConversationMode.chat -> item.copy(
                conversation = conv.copy(mode = ConversationMode.chat, rp = null, novel = null),
                novelChapters = emptyList(),
            )
            ConversationMode.rp -> {
                val rp = conv.rp ?: buildRpConfigFromMessages(conv.title, item.messages)
                item.copy(
                    conversation = conv.copy(mode = ConversationMode.rp, rp = rp, novel = null),
                )
            }
            ConversationMode.novel -> {
                val novelTitle = conv.title.removePrefix("《").removeSuffix("》").ifBlank { "导入小说" }
                val chId = item.novelChapters.firstOrNull()?.id ?: UUID.randomUUID().toString()
                val chapters = item.novelChapters.ifEmpty {
                    listOf(
                        NovelChapter(
                            id = chId,
                            conversationId = conv.id,
                            order = 1,
                            title = "第一章 开篇",
                            status = "writing",
                            startSeq = 0,
                            wordCount = item.messages.filter { it.role == "assistant" }.sumOf { it.content.length },
                            createdAt = conv.createdAt,
                            updatedAt = now,
                        ),
                    )
                }
                val novelCfg = conv.novel ?: NovelConfig(
                    title = novelTitle,
                    synopsis = item.messages.firstOrNull()?.content?.take(120).orEmpty(),
                    genre = "导入续写",
                    activeChapterId = chapters.last().id,
                )
                item.copy(
                    conversation = conv.copy(
                        title = if (conv.title.startsWith("《")) conv.title else "《$novelTitle》",
                        mode = ConversationMode.novel,
                        novel = novelCfg,
                        rp = null,
                    ),
                    novelChapters = chapters,
                )
            }
        }
    }

    private fun parseZipBytes(
        zipBytes: ByteArray,
        hintSource: ExternalImportSource?,
    ): ExternalImportBundle {
        // First inspect ZIP entries to detect SillyTavern/Chatbox/Claude/ChatGPT/Chengguang vs DeepSeek
        val textEntries = LinkedHashMap<String, String>()
        val pngEntries = LinkedHashMap<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val norm = entry.name.replace('\\', '/').trimStart('/')
                val lower = norm.lowercase()
                when {
                    lower.endsWith(".json") || lower.endsWith(".jsonl") || lower.endsWith(".txt") || lower.endsWith(".md") -> {
                        val content = readBoundedBytes(zis, MAX_ENTRY_BYTES).toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                        if (content.isNotEmpty()) textEntries[norm] = content
                    }
                    lower.endsWith(".png") -> {
                        val b = readBoundedBytes(zis, 16 * 1024 * 1024)
                        if (RpCardCodec.isPngBytes(b)) pngEntries[norm] = b
                    }
                }
                zis.closeEntry()
            }
        }

        // Check if ZIP is DeepSeek v3 (chat_sessions/*.json) or DeepSeek conversations.json
        val hasDeepSeekV3 = textEntries.keys.any { it.lowercase().contains("chat_sessions/") && it.lowercase().endsWith(".json") }
        if (hasDeepSeekV3) {
            val ds = DeepSeekImportCodec.parseZipBytes(zipBytes)
            return ds.toExternalBundle(ExternalImportSource.DEEPSEEK)
        }

        val convJsonEntry = textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("conversations.json", ignoreCase = true) }
        if (convJsonEntry != null) {
            return parseJsonText(convJsonEntry.value, convJsonEntry.key, hintSource)
        }

        // Collect all recognizable items inside ZIP (e.g. SillyTavern PNG/JSONL pack, Chatbox JSON, Chengguang TXT/JSON)
        val collected = ArrayList<ExternalImportedItem>()
        var primarySource: ExternalImportSource? = null

        pngEntries.forEach { (name, pngBytes) ->
            val cardJson = RpCardCodec.extractCardJsonFromPng(pngBytes)
            if (cardJson != null) {
                runCatching { parseSillyTavernCardJson(cardJson, name) }.getOrNull()?.let { b ->
                    collected.addAll(b.items)
                    if (primarySource == null) primarySource = ExternalImportSource.SILLYTAVERN
                }
            }
        }

        textEntries.forEach { (name, content) ->
            val lower = name.substringAfterLast('/').lowercase()
            if (lower == "manifest.json" || lower == "user.json" || lower == "user_info.json" || lower == "projects.json") {
                return@forEach
            }
            val parsed = runCatching {
                when {
                    lower.endsWith(".jsonl") || looksLikeSillyTavernJsonl(content) -> parseSillyTavernJsonl(content, name)
                    lower.endsWith(".json") -> parseJsonText(content, name, hintSource)
                    else -> parseTextOrScript(content, name, hintSource)
                }
            }.getOrNull()
            if (parsed != null && parsed.items.isNotEmpty()) {
                collected.addAll(parsed.items)
                if (primarySource == null) primarySource = parsed.source
            }
        }

        if (collected.isNotEmpty()) {
            return ExternalImportBundle(
                source = primarySource ?: hintSource ?: ExternalImportSource.GENERIC,
                sourceFormat = "zip_multi",
                items = collected.sortedByDescending { it.conversation.updatedAt },
            )
        }

        // Fallback to DeepSeekImportCodec error semantics
        return DeepSeekImportCodec.parseZipBytes(zipBytes).toExternalBundle(ExternalImportSource.DEEPSEEK)
    }

    private fun parseJsonText(
        rawJson: String,
        fileName: String?,
        hintSource: ExternalImportSource?,
    ): ExternalImportBundle {
        val root = try {
            AppJson.parseToJsonElement(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的 JSON 格式：${e.message}")
        }

        // 1. Chatbox backup JSON (`chat-sessions` or `sessions` with `contentParts` / `messages`)
        if (root is JsonObject && isChatboxBackup(root)) {
            return parseChatboxJson(root)
        }

        // 2. SillyTavern Character Card JSON (spec=chara_card_v2/v3 or data.first_mes / first_mes)
        if (root is JsonObject && isSillyTavernCardObject(root)) {
            return parseSillyTavernCardJson(rawJson, fileName)
        }

        // 3. SillyTavern Standalone World Info / Lorebook JSON (`entries` object/array with `key`/`keys` + `content`)
        if (root is JsonObject && isSillyTavernLorebook(root)) {
            return parseSillyTavernLorebook(root, fileName)
        }

        // 4. Chengguang / Interactive Story JSON (`scenes` / `dialogues` / `chapters` + `choices` / `characters`)
        if (root is JsonObject && isChengguangStoryJson(root)) {
            return parseChengguangJson(root, fileName)
        }

        // 5. Claude official export (`conversations.json` array or object with `chat_messages` & `sender`)
        if (isClaudeExport(root)) {
            return parseClaudeJson(root)
        }

        // 6. Distinguish ChatGPT official `conversations.json` vs DeepSeek `conversations.json`
        val detectedTreeSource = detectMappingSource(root)
        if (detectedTreeSource == ExternalImportSource.CHATGPT) {
            val bundle = DeepSeekImportCodec.parseJsonText(rawJson)
            return bundle.toExternalBundle(ExternalImportSource.CHATGPT, idPrefix = "cgpt_")
        }

        // 7. DeepSeek standard / single JSON / generic messages JSON
        val dsBundle = DeepSeekImportCodec.parseJsonText(rawJson)
        val effectiveSource = when {
            detectedTreeSource == ExternalImportSource.DEEPSEEK -> ExternalImportSource.DEEPSEEK
            hintSource != null -> hintSource
            else -> ExternalImportSource.DEEPSEEK
        }
        return dsBundle.toExternalBundle(effectiveSource)
    }

    // ==================== Chatbox Parser ====================

    private fun isChatboxBackup(obj: JsonObject): Boolean {
        if (obj["chat-sessions"] is JsonArray) return true
        val sessions = obj["sessions"] as? JsonArray ?: return false
        val first = sessions.firstOrNull() as? JsonObject ?: return false
        return first["messages"] is JsonArray && (first.containsKey("name") || first.containsKey("type") || first.containsKey("picUrl"))
    }

    private fun parseChatboxJson(root: JsonObject): ExternalImportBundle {
        val sessionsArr = (root["chat-sessions"] as? JsonArray)
            ?: (root["sessions"] as? JsonArray)
            ?: throw IllegalArgumentException("Chatbox 备份中未找到会话列表")

        val now = System.currentTimeMillis()
        val items = sessionsArr.mapIndexedNotNull { idx, elem ->
            val sObj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val rawId = sObj.stringField("id") ?: "cb_$idx"
            val convId = "cb_$rawId"
            val title = (sObj.stringField("name") ?: sObj.stringField("title")).orEmpty().trim().ifBlank { "Chatbox 对话" }
            val rawMsgs = sObj["messages"] as? JsonArray ?: return@mapIndexedNotNull null

            var systemPrompt = ""
            val messages = ArrayList<ChatMessage>()
            rawMsgs.forEachIndexed { mIdx, mElem ->
                val mObj = mElem as? JsonObject ?: return@forEachIndexed
                val role = mObj.stringField("role")?.lowercase()?.trim() ?: return@forEachIndexed
                val text = extractChatboxContent(mObj)
                val reasoning = (mObj.stringField("reasoningContent")
                    ?: mObj.stringField("reasoning_content")
                    ?: mObj.stringField("reasoning")).orEmpty().trim()
                val msgTime = parseTimestamp(mObj["timestamp"] ?: mObj["createdAt"]) ?: (now + mIdx * 1000L)

                if (role == "system") {
                    if (text.isNotBlank()) {
                        systemPrompt = if (systemPrompt.isBlank()) text else "$systemPrompt\n\n$text"
                    }
                    return@forEachIndexed
                }
                if (role != "user" && role != "assistant") return@forEachIndexed
                if (text.isBlank() && reasoning.isBlank()) return@forEachIndexed

                val msgId = mObj.stringField("id") ?: "m$mIdx"
                messages.add(
                    ChatMessage(
                        id = "${convId}_$msgId",
                        conversationId = convId,
                        role = role,
                        content = text,
                        reasoning = reasoning,
                        model = if (role == "assistant") mObj.stringField("model") else null,
                        createdAt = msgTime,
                        seq = messages.size + 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(text),
                    ),
                )
            }

            if (messages.isEmpty()) return@mapIndexedNotNull null

            val createdAt = parseTimestamp(sObj["createdAt"]) ?: messages.first().createdAt
            val updatedAt = maxOf(
                parseTimestamp(sObj["updatedAt"]) ?: createdAt,
                messages.maxOf { it.createdAt },
            )
            val conv = ChatConversation(
                id = convId,
                title = title,
                mode = ConversationMode.chat,
                systemPrompt = systemPrompt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            ExternalImportedItem(
                source = ExternalImportSource.CHATBOX,
                sourceId = rawId,
                conversation = conv,
                messages = messages,
                hasReasoning = messages.any { it.reasoning.isNotBlank() },
            )
        }.sortedByDescending { it.conversation.updatedAt }

        if (items.isEmpty()) {
            throw IllegalArgumentException("Chatbox 备份中未解析到有效对话")
        }
        return ExternalImportBundle(
            source = ExternalImportSource.CHATBOX,
            sourceFormat = "chatbox_json",
            items = items,
        )
    }

    private fun extractChatboxContent(mObj: JsonObject): String {
        val parts = mObj["contentParts"] as? JsonArray
        if (parts != null && parts.isNotEmpty()) {
            val joined = parts.mapNotNull { part ->
                val pObj = part as? JsonObject ?: return@mapNotNull (part as? JsonPrimitive)?.contentOrNull
                when (pObj.stringField("type")?.lowercase()) {
                    "text", null -> pObj.stringField("text") ?: pObj.stringField("content")
                    "image" -> "[图片]"
                    else -> pObj.stringField("text") ?: pObj.stringField("content")
                }
            }.filter { it.isNotBlank() }.joinToString("\n\n").trim()
            if (joined.isNotEmpty()) return joined
        }
        val contentElem = mObj["content"]
        return when (contentElem) {
            is JsonPrimitive -> contentElem.contentOrNull.orEmpty().trim()
            is JsonArray -> contentElem.mapNotNull { p ->
                (p as? JsonPrimitive)?.contentOrNull ?: (p as? JsonObject)?.stringField("text")
            }.joinToString("\n").trim()
            else -> ""
        }
    }

    // ==================== SillyTavern (酒馆) Parsers ====================

    private fun isSillyTavernCardObject(obj: JsonObject): Boolean {
        val spec = obj.stringField("spec").orEmpty()
        if (spec.equals("chara_card_v2", ignoreCase = true) || spec.equals("chara_card_v3", ignoreCase = true)) {
            return true
        }
        val data = obj["data"] as? JsonObject
        if (data != null && (data.containsKey("first_mes") || data.containsKey("mes_example") || data.containsKey("character_book"))) {
            return true
        }
        return obj.containsKey("first_mes") || obj.containsKey("mes_example") || (obj.containsKey("char_name") && obj.containsKey("char_persona"))
    }

    private fun parseSillyTavernCardJson(rawJson: String, fileName: String?): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val baseId = fileName?.substringAfterLast('/')?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?.take(32)
            .orEmpty()
            .ifBlank { rawJson.take(512).hashCode().toUInt().toString(16) }
        val convId = "st_card_$baseId"
        val imported = RpCardCodec.parseCardJson(rawJson, conversationId = convId)
        val rp = imported.rpConfig

        val messages = if (rp.greeting.isNotBlank()) {
            listOf(
                ChatMessage(
                    id = "${convId}_greeting",
                    conversationId = convId,
                    role = "assistant",
                    content = rp.greeting,
                    createdAt = now,
                    seq = 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(rp.greeting),
                    variants = listOf(
                        MessageVariant(
                            id = "${convId}_greeting_v0",
                            content = rp.greeting,
                            createdAt = now,
                            rpStateSnapshot = rp.state,
                        ),
                    ),
                ),
            )
        } else {
            emptyList()
        }

        val conv = ChatConversation(
            id = convId,
            title = rp.title,
            mode = ConversationMode.rp,
            rp = rp,
            createdAt = now,
            updatedAt = now,
        )
        return ExternalImportBundle(
            source = ExternalImportSource.SILLYTAVERN,
            sourceFormat = "sillytavern_card_${imported.specVersion}",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = messages,
                    loreEntries = imported.importedLore,
                    hasReasoning = false,
                ),
            ),
        )
    }

    private fun isSillyTavernLorebook(obj: JsonObject): Boolean {
        val entries = obj["entries"] ?: return false
        val firstEntry = when (entries) {
            is JsonObject -> entries.values.firstOrNull() as? JsonObject
            is JsonArray -> entries.firstOrNull() as? JsonObject
            else -> null
        } ?: return false
        return firstEntry.containsKey("content") && (firstEntry.containsKey("key") || firstEntry.containsKey("keys"))
    }

    private fun parseSillyTavernLorebook(root: JsonObject, fileName: String?): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = root.stringField("name")?.trim().orEmpty()
            .ifBlank { fileName?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty() }
            .ifBlank { "酒馆世界书" }
        val baseId = title.hashCode().toUInt().toString(16)
        val convId = "st_lore_$baseId"

        val entriesList: List<JsonObject> = when (val e = root["entries"]) {
            is JsonObject -> e.values.mapNotNull { it as? JsonObject }
            is JsonArray -> e.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }

        val loreEntries = entriesList.mapIndexedNotNull { idx, entry ->
            val content = entry.stringField("content").orEmpty().trim()
            if (content.isEmpty()) return@mapIndexedNotNull null
            val keysArr = (entry["key"] as? JsonArray) ?: (entry["keys"] as? JsonArray)
            val keywords = keysArr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val entryTitle = (entry.stringField("comment") ?: entry.stringField("name") ?: keywords.firstOrNull() ?: "条目 #${idx + 1}").trim()
            val disabled = (entry["disable"] as? JsonPrimitive)?.booleanOrNull == true
            val enabled = (entry["enabled"] as? JsonPrimitive)?.booleanOrNull ?: !disabled
            val constant = (entry["constant"] as? JsonPrimitive)?.booleanOrNull == true
            val order = (entry["order"] as? JsonPrimitive)?.intOrNull
                ?: (entry["priority"] as? JsonPrimitive)?.intOrNull
                ?: 50
            val lore = NovelLoreEntry(
                id = "${convId}_lore_$idx",
                conversationId = convId,
                category = "concept",
                title = entryTitle,
                content = content,
                keywords = keywords.ifEmpty { listOf(entryTitle) },
                alwaysActive = constant,
                priority = order,
                enabled = enabled,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore))
        }

        if (loreEntries.isEmpty()) throw IllegalArgumentException("世界书中未解析到有效条目")

        val summary = "已导入酒馆世界书《$title》，包含 ${loreEntries.size} 条设定（可在右上角文游台或记忆库中查看与触发）。"
        val rp = RpConfig(
            title = title,
            worldview = loreEntries.take(3).joinToString("\n\n") { "【${it.title}】${it.content.take(180)}" },
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = "sillytavern_lorebook",
        )
        val conv = ChatConversation(
            id = convId,
            title = title,
            mode = ConversationMode.rp,
            rp = rp,
            createdAt = now,
            updatedAt = now,
        )
        val greetingMsg = ChatMessage(
            id = "${convId}_m1",
            conversationId = convId,
            role = "assistant",
            content = summary,
            createdAt = now,
            seq = 1,
            status = "done",
            tokenEstimate = ContextBuilder.estimateMessageTokens(summary),
        )
        return ExternalImportBundle(
            source = ExternalImportSource.SILLYTAVERN,
            sourceFormat = "sillytavern_lorebook",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
                    loreEntries = loreEntries,
                ),
            ),
        )
    }

    private fun looksLikeSillyTavernJsonl(text: String): Boolean {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(3).toList()
        if (lines.size < 2 || !lines.all { it.startsWith("{") && it.endsWith("}") }) return false
        return lines.any { it.contains("\"is_user\"") || it.contains("\"character_name\"") || it.contains("\"mes\"") }
    }

    private fun parseSillyTavernJsonl(text: String, fileName: String?): ExternalImportBundle {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var userName = "主角"
        var charName = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.substringBefore(" - ")?.trim().orEmpty()
        val now = System.currentTimeMillis()
        var createTime = now
        val messages = ArrayList<ChatMessage>()
        val baseId = (fileName ?: text.take(80)).hashCode().toUInt().toString(16)
        val convId = "st_chat_$baseId"

        lines.forEachIndexed { idx, line ->
            val obj = runCatching { AppJson.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return@forEachIndexed
            // Header line carries user_name / character_name / chat_metadata without mes
            if (!obj.containsKey("mes") && (obj.containsKey("character_name") || obj.containsKey("user_name"))) {
                obj.stringField("user_name")?.takeIf { it.isNotBlank() }?.let { userName = it }
                obj.stringField("character_name")?.takeIf { it.isNotBlank() }?.let { charName = it }
                parseTimestamp(obj["create_date"])?.let { createTime = it }
                return@forEachIndexed
            }

            if ((obj["is_system"] as? JsonPrimitive)?.booleanOrNull == true) return@forEachIndexed
            val isUser = (obj["is_user"] as? JsonPrimitive)?.booleanOrNull ?: false
            val mes = obj.stringField("mes").orEmpty().trim()
            val swipesArr = (obj["swipes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val swipeId = (obj["swipe_id"] as? JsonPrimitive)?.intOrNull ?: 0
            val extra = obj["extra"] as? JsonObject
            val reasoning = (extra?.stringField("reasoning")
                ?: obj.stringField("reasoning")).orEmpty().trim()
            val model = extra?.stringField("model")
            val msgTime = parseTimestamp(obj["send_date"] ?: obj["gen_finished"]) ?: (createTime + idx * 1000L)
            if (!isUser && charName.isBlank()) {
                obj.stringField("name")?.takeIf { it.isNotBlank() }?.let { charName = it }
            }

            val body = mes.ifBlank { swipesArr.getOrNull(swipeId) ?: swipesArr.firstOrNull().orEmpty() }
            if (body.isBlank() && reasoning.isBlank()) return@forEachIndexed

            val variants = if (!isUser && swipesArr.size > 1) {
                swipesArr.mapIndexed { sIdx, swipeText ->
                    MessageVariant(
                        id = "${convId}_${idx}_v$sIdx",
                        content = swipeText,
                        reasoning = if (sIdx == swipeId) reasoning else "",
                        createdAt = msgTime,
                        model = model,
                    )
                }
            } else {
                emptyList()
            }

            messages.add(
                ChatMessage(
                    id = "${convId}_$idx",
                    conversationId = convId,
                    role = if (isUser) "user" else "assistant",
                    content = body,
                    reasoning = reasoning,
                    model = if (!isUser) model else null,
                    createdAt = msgTime,
                    seq = messages.size + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                    variants = variants,
                    activeVariantIndex = if (variants.isNotEmpty()) swipeId.coerceIn(0, variants.lastIndex) else 0,
                ),
            )
        }

        if (messages.isEmpty()) throw IllegalArgumentException("酒馆 .jsonl 文件中未解析到对话消息")

        val finalTitle = charName.ifBlank { "酒馆对话记录" }
        val rp = RpConfig(
            title = finalTitle,
            playerName = userName,
            cast = listOf(
                RpCharacter(id = "${convId}_player", name = userName, role = "player", isPlayer = true),
                RpCharacter(id = "${convId}_npc", name = finalTitle, role = "protagonist", affinity = 30),
            ),
            cardSource = "sillytavern_jsonl",
        )
        val conv = ChatConversation(
            id = convId,
            title = finalTitle,
            mode = ConversationMode.rp,
            rp = rp,
            createdAt = minOf(createTime, messages.first().createdAt),
            updatedAt = messages.maxOf { it.createdAt },
        )
        return ExternalImportBundle(
            source = ExternalImportSource.SILLYTAVERN,
            sourceFormat = "sillytavern_jsonl",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = messages,
                    hasReasoning = messages.any { it.reasoning.isNotBlank() },
                ),
            ),
        )
    }

    // ==================== Claude Parser ====================

    private fun isClaudeExport(root: JsonElement): Boolean {
        val sample = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return false
        val msgs = sample["chat_messages"] as? JsonArray ?: return false
        val firstMsg = msgs.firstOrNull() as? JsonObject ?: return false
        return firstMsg.containsKey("sender") || sample.containsKey("uuid")
    }

    private fun parseClaudeJson(root: JsonElement): ExternalImportBundle {
        val convList: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        val now = System.currentTimeMillis()
        val items = convList.mapIndexedNotNull { idx, obj ->
            val rawId = obj.stringField("uuid") ?: obj.stringField("id") ?: "claude_$idx"
            val convId = "claude_$rawId"
            val rawMsgs = obj["chat_messages"] as? JsonArray ?: return@mapIndexedNotNull null
            val messages = ArrayList<ChatMessage>()
            rawMsgs.forEachIndexed { mIdx, mElem ->
                val mObj = mElem as? JsonObject ?: return@forEachIndexed
                val sender = mObj.stringField("sender")?.lowercase() ?: mObj.stringField("role")?.lowercase() ?: return@forEachIndexed
                val role = when (sender) {
                    "human", "user" -> "user"
                    "assistant" -> "assistant"
                    else -> return@forEachIndexed
                }
                val contentParts = ArrayList<String>()
                val reasoningParts = ArrayList<String>()
                val contentArr = mObj["content"] as? JsonArray
                if (contentArr != null && contentArr.isNotEmpty()) {
                    contentArr.forEach { blockElem ->
                        val block = blockElem as? JsonObject ?: return@forEach
                        when (block.stringField("type")?.lowercase()) {
                            "thinking" -> block.stringField("thinking")?.takeIf { it.isNotBlank() }?.let { reasoningParts.add(it) }
                            "text" -> block.stringField("text")?.takeIf { it.isNotBlank() }?.let { contentParts.add(it) }
                        }
                    }
                }
                if (contentParts.isEmpty()) {
                    mObj.stringField("text")?.takeIf { it.isNotBlank() }?.let { contentParts.add(it) }
                }
                val body = contentParts.joinToString("\n\n").trim()
                val reasoning = reasoningParts.joinToString("\n\n").trim()
                if (body.isBlank() && reasoning.isBlank()) return@forEachIndexed
                val msgTime = parseTimestamp(mObj["created_at"]) ?: (now + mIdx * 1000L)
                val msgId = mObj.stringField("uuid") ?: "m$mIdx"
                messages.add(
                    ChatMessage(
                        id = "${convId}_$msgId",
                        conversationId = convId,
                        role = role,
                        content = body,
                        reasoning = reasoning,
                        model = if (role == "assistant") "claude" else null,
                        createdAt = msgTime,
                        seq = messages.size + 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                    ),
                )
            }
            if (messages.isEmpty()) return@mapIndexedNotNull null
            val title = obj.stringField("name")?.trim().orEmpty()
                .ifBlank { messages.firstOrNull { it.role == "user" }?.content?.take(24).orEmpty() }
                .ifBlank { "Claude 对话" }
            val createdAt = parseTimestamp(obj["created_at"]) ?: messages.first().createdAt
            val updatedAt = maxOf(parseTimestamp(obj["updated_at"]) ?: createdAt, messages.maxOf { it.createdAt })
            ExternalImportedItem(
                source = ExternalImportSource.CLAUDE,
                sourceId = rawId,
                conversation = ChatConversation(
                    id = convId,
                    title = title,
                    mode = ConversationMode.chat,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
                messages = messages,
                hasReasoning = messages.any { it.reasoning.isNotBlank() },
            )
        }.sortedByDescending { it.conversation.updatedAt }

        if (items.isEmpty()) throw IllegalArgumentException("未解析到有效的 Claude 对话记录")
        return ExternalImportBundle(source = ExternalImportSource.CLAUDE, sourceFormat = "claude_json", items = items)
    }

    // ==================== Chengguang (橙光) / Interactive Script Parsers ====================

    private fun isChengguangStoryJson(obj: JsonObject): Boolean =
        obj["scenes"] is JsonArray ||
            obj["dialogues"] is JsonArray ||
            obj["chapters"] is JsonArray ||
            obj["story_nodes"] is JsonArray ||
            (obj["characters"] is JsonArray && (obj.containsKey("plot") || obj.containsKey("script") || obj.containsKey("nodes")))

    private fun parseChengguangJson(root: JsonObject, fileName: String?): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = (root.stringField("title") ?: root.stringField("name") ?: fileName?.substringAfterLast('/')?.substringBeforeLast('.'))
            .orEmpty().trim().ifBlank { "橙光互动剧本" }
        val baseId = (root.stringField("id") ?: title.hashCode().toUInt().toString(16))
        val convId = "cg_$baseId"
        val worldview = (root.stringField("worldview") ?: root.stringField("synopsis") ?: root.stringField("description")).orEmpty().trim()

        val cast = (root["characters"] as? JsonArray)?.mapIndexedNotNull { idx, elem ->
            when (elem) {
                is JsonPrimitive -> elem.contentOrNull?.takeIf { it.isNotBlank() }?.let { n ->
                    RpCharacter(id = "${convId}_c$idx", name = n, role = "companion", affinity = 20)
                }
                is JsonObject -> {
                    val name = elem.stringField("name")?.trim().orEmpty()
                    if (name.isEmpty()) null
                    else RpCharacter(
                        id = "${convId}_c$idx",
                        name = name,
                        role = elem.stringField("role") ?: "companion",
                        personality = (elem.stringField("personality") ?: elem.stringField("desc")).orEmpty(),
                        background = elem.stringField("background").orEmpty(),
                        affinity = (elem["affinity"] as? JsonPrimitive)?.intOrNull ?: 20,
                    )
                }
                else -> null
            }
        }.orEmpty()

        val extractedCastNames = LinkedHashSet<String>(cast.map { it.name })
        val messages = ArrayList<ChatMessage>()
        var lastChoices: List<RpChoice> = emptyList()
        var firstSceneName = ""

        val sceneArray = (root["scenes"] as? JsonArray)
            ?: (root["chapters"] as? JsonArray)
            ?: (root["story_nodes"] as? JsonArray)

        if (sceneArray != null && sceneArray.isNotEmpty()) {
            sceneArray.forEachIndexed { sIdx, sElem ->
                val sObj = sElem as? JsonObject ?: return@forEachIndexed
                val sTitle = (sObj.stringField("title") ?: sObj.stringField("name") ?: sObj.stringField("scene")).orEmpty().trim()
                if (firstSceneName.isBlank() && sTitle.isNotBlank()) firstSceneName = sTitle
                val sb = StringBuilder()
                if (sTitle.isNotBlank()) sb.appendLine("### $sTitle\n")

                val directText = (sObj.stringField("narration") ?: sObj.stringField("content") ?: sObj.stringField("text")).orEmpty().trim()
                if (directText.isNotEmpty()) sb.appendLine(directText)

                val lines = (sObj["dialogues"] as? JsonArray) ?: (sObj["lines"] as? JsonArray)
                lines?.forEach { lElem ->
                    val lObj = lElem as? JsonObject
                    if (lObj != null) {
                        val sp = (lObj.stringField("speaker") ?: lObj.stringField("character") ?: lObj.stringField("name")).orEmpty().trim()
                        val txt = (lObj.stringField("text") ?: lObj.stringField("content") ?: lObj.stringField("line")).orEmpty().trim()
                        if (txt.isNotEmpty()) {
                            if (sp.isNotEmpty()) {
                                if (sp != "旁白" && sp != "系统") extractedCastNames.add(sp)
                                sb.appendLine("**$sp**：$txt")
                            } else {
                                sb.appendLine(txt)
                            }
                        }
                    } else if (lElem is JsonPrimitive && !lElem.contentOrNull.isNullOrBlank()) {
                        sb.appendLine(lElem.content)
                    }
                }

                val choices = parseJsonChoices(sObj["choices"] as? JsonArray ?: sObj["options"] as? JsonArray, "${convId}_s${sIdx}")
                if (choices.isNotEmpty()) lastChoices = choices

                val body = sb.toString().trim()
                if (body.isNotEmpty()) {
                    messages.add(
                        ChatMessage(
                            id = "${convId}_s$sIdx",
                            conversationId = convId,
                            role = "assistant",
                            content = body,
                            createdAt = now + sIdx * 1000L,
                            seq = messages.size + 1,
                            rpChoices = choices,
                            status = "done",
                            tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                        ),
                    )
                }
            }
        } else if (root["dialogues"] is JsonArray) {
            val sb = StringBuilder()
            (root["dialogues"] as JsonArray).forEach { dElem ->
                val dObj = dElem as? JsonObject ?: return@forEach
                val sp = (dObj.stringField("speaker") ?: dObj.stringField("character")).orEmpty().trim()
                val txt = (dObj.stringField("text") ?: dObj.stringField("content")).orEmpty().trim()
                if (txt.isNotEmpty()) {
                    if (sp.isNotEmpty()) {
                        if (sp != "旁白") extractedCastNames.add(sp)
                        sb.appendLine("**$sp**：$txt")
                    } else {
                        sb.appendLine(txt)
                    }
                }
            }
            lastChoices = parseJsonChoices(root["choices"] as? JsonArray ?: root["options"] as? JsonArray, convId)
            val body = sb.toString().trim()
            if (body.isNotEmpty()) {
                messages.add(
                    ChatMessage(
                        id = "${convId}_m1",
                        conversationId = convId,
                        role = "assistant",
                        content = body,
                        createdAt = now,
                        seq = 1,
                        rpChoices = lastChoices,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                    ),
                )
            }
        }

        if (messages.isEmpty()) throw IllegalArgumentException("橙光剧本 JSON 中未解析到剧情内容")

        val finalCast = cast.ifEmpty {
            extractedCastNames.take(6).mapIndexed { idx, name ->
                RpCharacter(id = "${convId}_cast_$idx", name = name, role = "companion", affinity = 20)
            }
        }
        val rp = RpConfig(
            title = title,
            worldview = worldview.ifBlank { messages.first().content.take(180) },
            cast = finalCast,
            state = RpGameState(scene = firstSceneName.ifBlank { title }),
            pendingChoices = lastChoices,
            cardSource = "chengguang_json",
        )
        return ExternalImportBundle(
            source = ExternalImportSource.CHENGGUANG,
            sourceFormat = "chengguang_json",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.CHENGGUANG,
                    sourceId = baseId,
                    conversation = ChatConversation(
                        id = convId,
                        title = title,
                        mode = ConversationMode.rp,
                        rp = rp,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    messages = messages,
                ),
            ),
        )
    }

    private fun parseJsonChoices(arr: JsonArray?, prefix: String): List<RpChoice> {
        if (arr == null || arr.isEmpty()) return emptyList()
        return arr.mapIndexedNotNull { idx, elem ->
            when (elem) {
                is JsonPrimitive -> elem.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
                    RpChoice(id = "${prefix}_ch$idx", label = txt, actionText = txt)
                }
                is JsonObject -> {
                    val label = (elem.stringField("label") ?: elem.stringField("text") ?: elem.stringField("title")).orEmpty().trim()
                    if (label.isEmpty()) null
                    else RpChoice(
                        id = "${prefix}_ch$idx",
                        label = label,
                        actionText = elem.stringField("actionText")?.takeIf { it.isNotBlank() } ?: label,
                    )
                }
                else -> null
            }
        }
    }

    private fun parseTextOrScript(
        text: String,
        fileName: String?,
        hintSource: ExternalImportSource?,
    ): ExternalImportBundle {
        // If user explicitly chose Chengguang or text has script/chapter structure, parse as Chengguang script!
        val hasMdDialogue = text.lineSequence().count { MD_USER_HEADING_RE.matches(it.trim()) || MD_ASSISTANT_HEADING_RE.matches(it.trim()) } >= 2
        if (hintSource != ExternalImportSource.CHENGGUANG && hasMdDialogue) {
            return parseMarkdownTranscript(text, fileName)
        }
        return parseChengguangScriptText(text, fileName, preferNovel = false)
    }

    private fun parseChengguangScriptText(
        rawText: String,
        titleHint: String?,
        preferNovel: Boolean,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val firstNonBlank = lines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val firstHeading = when {
            firstNonBlank.startsWith("# ") -> firstNonBlank.removePrefix("# ").trim()
            firstNonBlank.startsWith("《") && firstNonBlank.endsWith("》") && firstNonBlank.length in 3..32 ->
                firstNonBlank.removePrefix("《").removeSuffix("》").trim()
            else -> lines.firstOrNull { it.trim().startsWith("# ") }?.trim()?.removePrefix("# ")?.trim()
        }
        val title = firstHeading?.takeIf { it.isNotBlank() }
            ?: titleHint?.substringAfterLast('/')?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: "橙光互动剧本"
        val baseId = (title + rawText.take(120)).hashCode().toUInt().toString(16)
        val convId = "cg_txt_$baseId"

        val castNames = LinkedHashSet<String>()
        val extractedChoices = ArrayList<RpChoice>()
        val chapters = ArrayList<Pair<String, StringBuilder>>()
        var currentChapterTitle = "第一章 开篇"
        var currentBuf = StringBuilder()
        var inChoiceBlock = false
        val choiceHeaderRe = Regex("""^(?:【)?(?:选项|分支|分支选择|抉择|选择)(?:】)?\s*[:：]?$""")
        val numberedOrBulletChoiceRe = Regex("""^(?:[-*•]\s*|(?:[A-Da-d]|[1-6])[.、)）:：]\s*)(.+)$""")

        fun flushChapter() {
            val body = currentBuf.toString().trim()
            if (body.isNotEmpty()) {
                chapters.add(currentChapterTitle to StringBuilder(body))
            }
            currentBuf = StringBuilder()
        }

        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                inChoiceBlock = false
                if (currentBuf.isNotEmpty()) currentBuf.appendLine()
                return@forEach
            }
            if (trimmed == firstNonBlank && (trimmed.startsWith("# ") || (trimmed.startsWith("《") && trimmed.endsWith("》") && trimmed.length in 3..32))) {
                return@forEach
            }
            val chMatch = CHAPTER_HEADING_RE.matchEntire(trimmed)
            if (chMatch != null) {
                inChoiceBlock = false
                flushChapter()
                currentChapterTitle = chMatch.groupValues[1].trim()
                return@forEach
            }
            if (choiceHeaderRe.matches(trimmed)) {
                inChoiceBlock = true
                return@forEach
            }
            val choiceMatch = CHOICE_LINE_RE.matchEntire(trimmed)
            val isExplicitChoice = choiceMatch != null && (
                trimmed.startsWith("选项") ||
                    trimmed.startsWith("分支") ||
                    trimmed.startsWith("- 选项") ||
                    trimmed.startsWith("- 分支") ||
                    trimmed.matches(Regex("""^[A-Da-d][.、)）:：].*"""))
                )
            if (isExplicitChoice) {
                val label = choiceMatch!!.groupValues[1].trim()
                if (label.isNotEmpty()) {
                    extractedChoices.add(RpChoice(id = "${convId}_c${extractedChoices.size}", label = label, actionText = label))
                }
                return@forEach
            }
            if (inChoiceBlock) {
                val numMatch = numberedOrBulletChoiceRe.matchEntire(trimmed)
                if (numMatch != null) {
                    val label = numMatch.groupValues[1].trim()
                    if (label.isNotEmpty()) {
                        extractedChoices.add(RpChoice(id = "${convId}_c${extractedChoices.size}", label = label, actionText = label))
                    }
                    return@forEach
                }
                inChoiceBlock = false
            }
            val brMatch = SPEAKER_BRACKET_RE.matchEntire(trimmed)
            if (brMatch != null) {
                val speaker = brMatch.groupValues[1].trim()
                val speech = brMatch.groupValues[2].trim()
                if (speaker != "旁白" && speaker != "系统" && speaker != "选项") {
                    castNames.add(speaker)
                }
                currentBuf.appendLine("**$speaker**：$speech")
                return@forEach
            }
            val colonMatch = SPEAKER_COLON_RE.matchEntire(trimmed)
            if (colonMatch != null) {
                val speaker = colonMatch.groupValues[1].trim()
                val speech = colonMatch.groupValues[2].trim()
                if (speaker !in setOf("旁白", "系统", "场景", "时间", "地点", "选项", "分支", "提示")) {
                    castNames.add(speaker)
                }
                currentBuf.appendLine("**$speaker**：$speech")
                return@forEach
            }
            currentBuf.appendLine(trimmed)
        }
        flushChapter()

        if (chapters.isEmpty()) throw IllegalArgumentException("未解析到有效剧情正文")

        val messages = ArrayList<ChatMessage>()
        val novelChapters = ArrayList<NovelChapter>()
        chapters.forEachIndexed { idx, (chTitle, bodyBuf) ->
            val body = bodyBuf.toString().trim()
            val isLast = idx == chapters.lastIndex
            val msgSeq = idx + 1
            messages.add(
                ChatMessage(
                    id = "${convId}_ch$idx",
                    conversationId = convId,
                    role = "assistant",
                    content = if (chapters.size > 1) "### $chTitle\n\n$body" else body,
                    createdAt = now + idx * 1000L,
                    seq = msgSeq,
                    rpChoices = if (isLast) extractedChoices else emptyList(),
                    novelChoices = if (isLast) extractedChoices.map { it.label } else emptyList(),
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                ),
            )
            novelChapters.add(
                NovelChapter(
                    id = "${convId}_nc$idx",
                    conversationId = convId,
                    order = idx + 1,
                    title = chTitle,
                    status = if (isLast) "writing" else "done",
                    startSeq = msgSeq,
                    endSeq = if (isLast) null else msgSeq,
                    wordCount = body.count { !it.isWhitespace() },
                    createdAt = now + idx * 1000L,
                    updatedAt = now + idx * 1000L,
                ),
            )
        }

        val useNovelMode = preferNovel || (chapters.size >= 2 && castNames.isEmpty() && extractedChoices.isEmpty())
        val conv = if (useNovelMode) {
            ChatConversation(
                id = convId,
                title = if (title.startsWith("《")) title else "《$title》",
                mode = ConversationMode.novel,
                novel = NovelConfig(
                    title = title,
                    synopsis = chapters.first().second.toString().take(160),
                    genre = "导入剧本",
                    activeChapterId = novelChapters.last().id,
                    pendingChoices = extractedChoices.map { it.label },
                ),
                createdAt = now,
                updatedAt = now,
            )
        } else {
            val cast = castNames.take(8).mapIndexed { idx, name ->
                RpCharacter(id = "${convId}_npc$idx", name = name, role = "companion", affinity = 20)
            }
            ChatConversation(
                id = convId,
                title = title,
                mode = ConversationMode.rp,
                rp = RpConfig(
                    title = title,
                    worldview = chapters.first().second.toString().take(200),
                    cast = cast,
                    state = RpGameState(scene = chapters.first().first),
                    pendingChoices = extractedChoices,
                    cardSource = "chengguang_script",
                ),
                createdAt = now,
                updatedAt = now,
            )
        }

        return ExternalImportBundle(
            source = ExternalImportSource.CHENGGUANG,
            sourceFormat = "chengguang_text",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.CHENGGUANG,
                    sourceId = baseId,
                    conversation = conv,
                    messages = messages,
                    novelChapters = novelChapters,
                ),
            ),
        )
    }

    private fun parseMarkdownTranscript(rawText: String, fileName: String?): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "导入对话" }
        val baseId = (title + rawText.take(80)).hashCode().toUInt().toString(16)
        val convId = "md_$baseId"

        val messages = ArrayList<ChatMessage>()
        var currentRole: String? = null
        val buf = StringBuilder()

        fun flush() {
            val role = currentRole ?: return
            val raw = buf.toString().trim()
            if (raw.isEmpty()) return
            var reasoning = ""
            var body = raw
            val thinkMatch = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE).find(body)
            if (thinkMatch != null) {
                reasoning = thinkMatch.groupValues[1].trim()
                body = body.removeRange(thinkMatch.range).trim()
            }
            messages.add(
                ChatMessage(
                    id = "${convId}_m${messages.size + 1}",
                    conversationId = convId,
                    role = role,
                    content = body,
                    reasoning = reasoning,
                    createdAt = now + messages.size * 1000L,
                    seq = messages.size + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(body),
                ),
            )
            buf.clear()
        }

        rawText.replace("\r\n", "\n").split("\n").forEach { line ->
            val trimmed = line.trim()
            val uMatch = MD_USER_HEADING_RE.matchEntire(trimmed)
            val aMatch = if (uMatch == null) MD_ASSISTANT_HEADING_RE.matchEntire(trimmed) else null
            when {
                uMatch != null -> {
                    flush()
                    currentRole = "user"
                    uMatch.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { buf.appendLine(it) }
                }
                aMatch != null -> {
                    flush()
                    currentRole = "assistant"
                    aMatch.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { buf.appendLine(it) }
                }
                else -> if (currentRole != null) buf.appendLine(line)
            }
        }
        flush()

        if (messages.isEmpty()) throw IllegalArgumentException("未在文本中识别到对话回合")

        return ExternalImportBundle(
            source = ExternalImportSource.GENERIC,
            sourceFormat = "markdown_transcript",
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.GENERIC,
                    sourceId = baseId,
                    conversation = ChatConversation(
                        id = convId,
                        title = title,
                        mode = ConversationMode.chat,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    messages = messages,
                    hasReasoning = messages.any { it.reasoning.isNotBlank() },
                ),
            ),
        )
    }

    // ==================== Helpers ====================

    private fun detectMappingSource(root: JsonElement): ExternalImportSource? {
        val sample = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> ((root["conversations"] as? JsonArray)?.firstOrNull() as? JsonObject) ?: root
            else -> null
        } ?: return null
        val mapping = sample["mapping"] as? JsonObject ?: return null
        val sampleNodeMsg = mapping.values
            .mapNotNull { (it as? JsonObject)?.get("message") as? JsonObject }
            .firstOrNull() ?: return null
        return when {
            sampleNodeMsg["fragments"] is JsonArray -> ExternalImportSource.DEEPSEEK
            sampleNodeMsg["author"] is JsonObject && sampleNodeMsg["content"] is JsonObject -> ExternalImportSource.CHATGPT
            else -> null
        }
    }

    private fun DeepSeekImportBundle.toExternalBundle(
        source: ExternalImportSource,
        idPrefix: String? = null,
    ): ExternalImportBundle = ExternalImportBundle(
        source = source,
        sourceFormat = sourceFormat,
        items = items.map { ds ->
            val remappedConv = if (idPrefix != null && ds.conversation.id.startsWith("ds_")) {
                val newId = idPrefix + ds.conversation.id.removePrefix("ds_")
                ds.conversation.copy(id = newId)
            } else {
                ds.conversation
            }
            val remappedMsgs = if (remappedConv.id != ds.conversation.id) {
                ds.messages.map { m ->
                    m.copy(
                        id = m.id.replaceFirst(ds.conversation.id, remappedConv.id),
                        conversationId = remappedConv.id,
                    )
                }
            } else {
                ds.messages
            }
            ExternalImportedItem(
                source = source,
                sourceId = ds.sourceId,
                conversation = remappedConv,
                messages = remappedMsgs,
                hasReasoning = ds.hasReasoning,
            )
        },
    )

    private fun buildRpConfigFromMessages(title: String, messages: List<ChatMessage>): RpConfig {
        val firstBody = messages.firstOrNull()?.content?.take(200).orEmpty()
        return RpConfig(
            title = title,
            worldview = firstBody,
            cast = listOf(RpCharacter(id = UUID.randomUUID().toString(), name = title, role = "companion", affinity = 20)),
            state = RpGameState(scene = title),
            cardSource = "imported_chat",
        )
    }

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

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
                return LocalDateTime.parse(s.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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

    private fun readBoundedBytes(zis: ZipInputStream, maxBytes: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zis.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) throw IllegalArgumentException("压缩包内单文件体积超过限制")
            baos.write(buf, 0, read)
        }
        return baos.toByteArray()
    }
}
