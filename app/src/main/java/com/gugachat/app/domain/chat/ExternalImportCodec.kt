package com.gugachat.app.domain.chat

import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderImportCodec
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.RpCardCodec.arrOrNull
import com.gugachat.app.domain.chat.RpCardCodec.boolOrNull
import com.gugachat.app.domain.chat.RpCardCodec.intOrNull
import com.gugachat.app.domain.chat.RpCardCodec.objOrNull
import com.gugachat.app.domain.chat.RpCardCodec.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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
    RIKKAHUB,
    CHERRY_STUDIO,
    UU_GAME,
    GENERIC,
}

data class ExternalImportedItem(
    val source: ExternalImportSource,
    val sourceId: String,
    val conversation: ChatConversation,
    val messages: List<ChatMessage>,
    val loreEntries: List<NovelLoreEntry> = emptyList(),
    val novelChapters: List<NovelChapter> = emptyList(),
    val memoryLedger: List<MemoryLedgerItem> = emptyList(),
    val memoryEpisodes: List<MemoryEpisode> = emptyList(),
    val hasReasoning: Boolean = false,
)

data class ExternalImportBundle(
    val source: ExternalImportSource,
    val sourceFormat: String,
    val items: List<ExternalImportedItem>,
    val importedProviders: List<ApiProviderConfig> = emptyList(),
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

        // 2. ZIP Archive (.zip / .charx / .byaf: DeepSeek, Chatbox v2, Cherry Studio, RikkaHub, SillyTavern, ChatGPT, Claude, Chengguang)
        if (DeepSeekImportCodec.isZipBytes(bytes)) {
            return parseZipBytes(bytes, fileName, hintSource)
        }

        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw IllegalArgumentException("文件内容为空")
        if (text.startsWith("<!DOCTYPE", ignoreCase = true) || text.startsWith("<html", ignoreCase = true)) {
            throw IllegalArgumentException("HTML_RESPONSE")
        }

        // 2b. RikkaHub provider share string (`ai-provider:v1:...`)
        if (text.contains("ai-provider:v1:")) {
            val provBundle = runCatching { ApiProviderImportCodec.parseText(text, fileName) }.getOrNull()
            if (provBundle != null && provBundle.providers.isNotEmpty()) {
                return ExternalImportBundle(
                    source = ExternalImportSource.RIKKAHUB,
                    sourceFormat = provBundle.sourceFormat,
                    items = emptyList(),
                    importedProviders = provBundle.providers,
                )
            }
        }

        val lowerName = fileName.orEmpty().lowercase()

        // 3. SillyTavern JSONL chat log (.jsonl or multi-line JSON objects with is_user / mes)
        if (lowerName.endsWith(".jsonl") || looksLikeSillyTavernJsonl(text)) {
            return parseSillyTavernJsonl(text, fileName)
        }

        // 4. JSON payload (auto-detect RikkaHub, Chatbox, SillyTavern Card/Lore/Preset/Regex/QR, Chengguang, Claude, ChatGPT, DeepSeek, Cherry Studio, Generic/ShareGPT)
        if (text.startsWith("{") || text.startsWith("[")) {
            return parseJsonText(text, fileName, hintSource)
        }

        // 5. Legacy DeepSeek CSV export (chat.csv / coder.csv)
        if (lowerName.endsWith(".csv") || DeepSeekImportCodec.looksLikeLegacyCsv(text, fileName)) {
            val ds = DeepSeekImportCodec.parseBytes(bytes, fileName = fileName)
            return ds.toExternalBundle(ExternalImportSource.DEEPSEEK)
        }

        // 6. Plain text / Markdown (Chengguang script or generic User/Assistant transcript)
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

    // ==================== ZIP Dispatch ====================

    private fun parseZipBytes(
        zipBytes: ByteArray,
        fileName: String?,
        hintSource: ExternalImportSource?,
    ): ExternalImportBundle {
        val lowerZipName = fileName.orEmpty().lowercase()

        // 0. SillyTavern `.charx` / `.byaf` single-card ZIP package
        if (lowerZipName.endsWith(".charx") || lowerZipName.endsWith(".byaf")) {
            val extracted = RpCardCodec.extractCardFromZipBytes(zipBytes)
                ?: throw IllegalArgumentException("未在 .charx / .byaf 压缩包中找到有效角色卡（card.json）")
            return parseSillyTavernCardJson(extracted.cardJson, fileName)
        }

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
                val baseName = lower.substringAfterLast('/')
                when {
                    lower.contains("__macosx/") || baseName.startsWith("._") -> Unit
                    lower.contains("_files/") -> Unit
                    lower.endsWith(".json") || lower.endsWith(".jsonl") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") -> {
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

        // 0b. Single-card ZIP containing `card.json` (e.g. renamed .charx/.byaf -> .zip)
        textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("card.json", ignoreCase = true) }?.let { (path, content) ->
            val obj = runCatching { AppJson.parseToJsonElement(content) as? JsonObject }.getOrNull()
            if (obj != null && isSillyTavernCardObject(obj)) {
                return parseSillyTavernCardJson(content, fileName ?: path)
            }
        }

        // 1. DeepSeek v3 ZIP (`chat_sessions/*.json`)
        val hasDeepSeekV3 = textEntries.keys.any { it.lowercase().contains("chat_sessions/") && it.lowercase().endsWith(".json") }
        if (hasDeepSeekV3) {
            val ds = DeepSeekImportCodec.parseZipTextEntries(textEntries)
            return ds.toExternalBundle(ExternalImportSource.DEEPSEEK)
        }

        // 2. Chatbox Backup v2 ZIP (`manifest.json` with format="chatbox-backup")
        val manifestEntry = textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("manifest.json", ignoreCase = true) }
        if (manifestEntry != null) {
            val manifestObj = runCatching { AppJson.parseToJsonElement(manifestEntry.value) as? JsonObject }.getOrNull()
            if (manifestObj?.strOrNull("format") == "chatbox-backup") {
                return parseChatboxV2Zip(manifestObj, textEntries)
            }
        }

        // 3. Cherry Studio Backup ZIP (`data.json` containing `persist:cherry-studio`)
        val dataJsonEntry = textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("data.json", ignoreCase = true) }
        if (dataJsonEntry != null) {
            val dataObj = runCatching { AppJson.parseToJsonElement(dataJsonEntry.value) as? JsonObject }.getOrNull()
            val cherryProviders = dataObj?.let { ApiProviderImportCodec.extractCherryStudioProviders(it) }
            if (cherryProviders != null && cherryProviders.isNotEmpty()) {
                return ExternalImportBundle(
                    source = ExternalImportSource.CHERRY_STUDIO,
                    sourceFormat = "cherry_studio_zip",
                    items = emptyList(),
                    importedProviders = cherryProviders.distinctBy(ApiProviderImportCodec::dedupeKey),
                )
            }
        }

        // 4. RikkaHub Backup ZIP (`settings.json` with assistants/lorebooks/providers)
        val settingsEntry = textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("settings.json", ignoreCase = true) }
        if (settingsEntry != null) {
            val settingsObj = runCatching { AppJson.parseToJsonElement(settingsEntry.value) as? JsonObject }.getOrNull()
            if (settingsObj != null && isRikkaHubSettingsJson(settingsObj)) {
                return parseRikkaHubSettingsJson(settingsObj, sourceFormat = "rikkahub_backup_zip")
            }
        }

        // 5. SillyTavern full directory/backup archive (`characters/`, `worlds/`, `OpenAI Settings/`, `QuickReplies/`, `settings.json`)
        val hasStLayout = (textEntries.keys + pngEntries.keys).any { p ->
            val low = p.lowercase()
            low.startsWith("characters/") || low.contains("/characters/") ||
                low.startsWith("worlds/") || low.contains("/worlds/") ||
                low.startsWith("openai settings/") || low.contains("/openai settings/") ||
                low.startsWith("quickreplies/") || low.contains("/quickreplies/")
        }
        if (hasStLayout) {
            val stBundle = parseSillyTavernArchiveZip(textEntries, pngEntries)
            if (stBundle.items.isNotEmpty()) return stBundle
        }

        // 6. Standard `conversations.json` inside ZIP (ChatGPT / Claude / DeepSeek)
        val convJsonEntry = textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("conversations.json", ignoreCase = true) }
        if (convJsonEntry != null) {
            return parseJsonText(convJsonEntry.value, convJsonEntry.key, hintSource)
        }

        // 7. DeepSeek legacy CSV inside ZIP
        val hasDeepSeekLegacyCsv = textEntries.entries.any { (name, content) ->
            name.lowercase().endsWith(".csv") && DeepSeekImportCodec.looksLikeLegacyCsv(content, name)
        }
        if (hasDeepSeekLegacyCsv) {
            val ds = DeepSeekImportCodec.parseZipTextEntries(textEntries)
            return ds.toExternalBundle(ExternalImportSource.DEEPSEEK)
        }

        // 8. Collect all recognizable items & providers inside a generic multi-file ZIP
        val collected = ArrayList<ExternalImportedItem>()
        val collectedProviders = ArrayList<ApiProviderConfig>()
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
            if (parsed != null && (parsed.items.isNotEmpty() || parsed.importedProviders.isNotEmpty())) {
                collected.addAll(parsed.items)
                collectedProviders.addAll(parsed.importedProviders)
                if (primarySource == null) primarySource = parsed.source
            }
        }

        val dedupedProviders = collectedProviders.distinctBy(ApiProviderImportCodec::dedupeKey)
        if (collected.isNotEmpty() || dedupedProviders.isNotEmpty()) {
            return ExternalImportBundle(
                source = primarySource ?: hintSource ?: ExternalImportSource.GENERIC,
                sourceFormat = "zip_multi",
                items = collected.sortedByDescending { it.conversation.updatedAt },
                importedProviders = dedupedProviders,
            )
        }

        return DeepSeekImportCodec.parseZipBytes(zipBytes).toExternalBundle(ExternalImportSource.DEEPSEEK)
    }

    // ==================== JSON Auto-Detection Chain ====================

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

        // 0. UUGame interactive RPG / life-sim save (`format: "uu-game-save"` or unwrapped `gameState`)
        if (root is JsonObject && UuSaveCodec.isUuGameSave(root)) {
            return UuSaveCodec.parseJsonElement(root, fileName)
        }

        // 1. RikkaHub native ExportData (`type: "lorebook" | "mode_injection"`)
        if (root is JsonObject && TavernAssetCodec.isRikkaExportEnvelope(root)) {
            return parseTavernAssetToBundle(
                asset = TavernAssetCodec.parseRikkaExportEnvelope(root, conversationId = "pending", fileName = fileName)
                    ?: throw IllegalArgumentException("RikkaHub 导出文件中未解析到有效条目"),
                source = ExternalImportSource.RIKKAHUB,
                fileName = fileName,
            )
        }

        // 2. Chatbox v1 backup JSON (`chat-sessions` or `sessions` with `messages`)
        if (root is JsonObject && isChatboxBackup(root)) {
            return parseChatboxJson(root)
        }

        // 3. SillyTavern Character Card JSON (V3 / V2 / V1 incl. spec-less cards)
        if (root is JsonObject && isSillyTavernCardObject(root)) {
            return parseSillyTavernCardJson(rawJson, fileName)
        }

        // 4. SillyTavern Standalone World Info / Lorebook / Tavern Helper WorldbookEntry JSON
        if (TavernAssetCodec.isLorebookJson(root)) {
            val baseId = (fileName ?: rawJson.take(120)).hashCode().toUInt().toString(16)
            val convId = "st_lore_$baseId"
            val loreAsset = TavernAssetCodec.parseLorebookJson(root, conversationId = convId, fileName = fileName)
            return buildLorebookBundle(loreAsset, convId, baseId, ExternalImportSource.SILLYTAVERN)
        }

        // 5. SillyTavern Chat Completion Preset JSON (ordered `prompts` + `prompt_order` or flat `main_prompt`)
        if (root is JsonObject && TavernAssetCodec.isChatPresetJson(root)) {
            val baseId = (fileName ?: rawJson.take(120)).hashCode().toUInt().toString(16)
            val convId = "st_preset_$baseId"
            val preset = TavernAssetCodec.parseChatPresetJson(root, conversationId = convId, fileName = fileName)
            return buildPresetBundle(preset, convId, baseId, ExternalImportSource.SILLYTAVERN)
        }

        // 6. SillyTavern Quick Replies JSON (`qrList` / `quickReplies`)
        if (TavernAssetCodec.isQuickRepliesJson(root)) {
            val qr = TavernAssetCodec.parseQuickRepliesJson(root, fileName)
            return buildQuickRepliesBundle(qr, fileName)
        }

        // 7. SillyTavern Standalone Regex Script JSON (single object or array, camelCase or snake_case)
        if (TavernAssetCodec.isRegexScriptJson(root)) {
            val scripts = TavernAssetCodec.parseRegexScriptsJson(root)
            if (scripts.isNotEmpty()) {
                return buildRegexScriptsBundle(scripts, fileName, "sillytavern_regex")
            }
        }

        // 8. SillyTavern settings.json (`extension_settings.regex` / `note.default`)
        if (root is JsonObject && TavernAssetCodec.isSillyTavernSettingsJson(root)) {
            val stSettings = TavernAssetCodec.parseSillyTavernSettingsJson(root)
            if (stSettings.regexScripts.isNotEmpty() || stSettings.authorNote.isNotBlank()) {
                return buildTavernSettingsBundle(stSettings, fileName)
            }
        }

        // 9. Chengguang / Interactive Story JSON (`scenes` / `dialogues` / `chapters` + `choices` / `characters`)
        if (root is JsonObject && isChengguangStoryJson(root)) {
            return parseChengguangJson(root, fileName)
        }

        // 10. Claude official export (`conversations.json` array or object with `chat_messages` & `sender`)
        if (isClaudeExport(root)) {
            return parseClaudeJson(root)
        }

        // 11. RikkaHub settings.json (assistants / lorebooks / providers)
        if (root is JsonObject && isRikkaHubSettingsJson(root)) {
            return parseRikkaHubSettingsJson(root, sourceFormat = "rikkahub_settings_json")
        }

        // 12. Distinguish ChatGPT official `conversations.json` vs DeepSeek `conversations.json`
        val detectedTreeSource = detectMappingSource(root)
        if (detectedTreeSource == ExternalImportSource.CHATGPT) {
            val bundle = DeepSeekImportCodec.parseJsonText(rawJson)
            return bundle.toExternalBundle(ExternalImportSource.CHATGPT, idPrefix = "cgpt_")
        }

        // 13. DeepSeek standard / single JSON
        val dsResult = runCatching { DeepSeekImportCodec.parseJsonText(rawJson) }.getOrNull()
        if (dsResult != null && dsResult.items.isNotEmpty()) {
            val effectiveSource = when {
                detectedTreeSource == ExternalImportSource.DEEPSEEK -> ExternalImportSource.DEEPSEEK
                hintSource != null -> hintSource
                else -> ExternalImportSource.DEEPSEEK
            }
            return dsResult.toExternalBundle(effectiveSource)
        }

        // 14. Pure API Provider config JSON (Cherry Studio data.json / Chatbox settings.json / generic providers JSON)
        val providerBundle = ApiProviderImportCodec.tryExtractFromJsonElement(root, fileName)
        if (providerBundle != null && providerBundle.providers.isNotEmpty()) {
            val provSource = when {
                providerBundle.sourceFormat.startsWith("cherry_studio") -> ExternalImportSource.CHERRY_STUDIO
                providerBundle.sourceFormat.startsWith("chatbox") -> ExternalImportSource.CHATBOX
                providerBundle.sourceFormat.startsWith("rikkahub") -> ExternalImportSource.RIKKAHUB
                else -> hintSource ?: ExternalImportSource.GENERIC
            }
            return ExternalImportBundle(
                source = provSource,
                sourceFormat = providerBundle.sourceFormat,
                items = emptyList(),
                importedProviders = providerBundle.providers,
            )
        }

        // 15. Relaxed Generic Chat JSON & ShareGPT JSON fallback (bare message array, `chats`/`records`/`history`, `from`/`value`)
        val genericBundle = tryParseGenericChatJson(root, fileName, hintSource)
        if (genericBundle != null && genericBundle.items.isNotEmpty()) {
            return genericBundle
        }

        throw IllegalArgumentException(
            "未在 JSON 中识别到支持的对话、角色卡、世界书、预设或供应商配置，请检查文件内容",
        )
    }

    // ==================== Chatbox v1 & v2 Parsers ====================

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

        val items = sessionsArr.mapIndexedNotNull { idx, elem ->
            val sObj = elem as? JsonObject ?: return@mapIndexedNotNull null
            parseChatboxSessionObject(
                sObj = sObj,
                fallbackId = "cb_$idx",
                metaName = null,
                metaCreatedAt = null,
                metaStarred = false,
            )
        }.sortedByDescending { it.conversation.updatedAt }

        val providers = root.objOrNull("settings")?.objOrNull("providers")
            ?.let(ApiProviderImportCodec::parseChatboxProvidersMap)
            .orEmpty()
            .distinctBy(ApiProviderImportCodec::dedupeKey)

        if (items.isEmpty() && providers.isEmpty()) {
            throw IllegalArgumentException("Chatbox 备份中未解析到有效对话")
        }
        return ExternalImportBundle(
            source = ExternalImportSource.CHATBOX,
            sourceFormat = "chatbox_json",
            items = items,
            importedProviders = providers,
        )
    }

    private fun parseChatboxV2Zip(
        manifest: JsonObject,
        textEntries: Map<String, String>,
    ): ExternalImportBundle {
        val version = manifest.intOrNull("formatVersion")
        if (version != null && version != 2) {
            throw IllegalArgumentException("暂不支持的 Chatbox 备份版本（v$version）")
        }

        // Extract API providers from settings.json inside Chatbox v2 archive
        val settingsPath = manifest.objOrNull("data")?.objOrNull("settings")?.strOrNull("path") ?: "settings.json"
        val settingsRaw = textEntries[settingsPath]
            ?: textEntries.entries.firstOrNull { it.key.substringAfterLast('/').equals("settings.json", ignoreCase = true) }?.value
        val providers = settingsRaw?.let { raw ->
            val sObj = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            sObj?.objOrNull("providers")?.let(ApiProviderImportCodec::parseChatboxProvidersMap).orEmpty()
        }.orEmpty().distinctBy(ApiProviderImportCodec::dedupeKey)

        val sessionManifestEntries = manifest.arrOrNull("sessions").orEmpty().mapNotNull { it as? JsonObject }
        val items = ArrayList<ExternalImportedItem>()

        if (sessionManifestEntries.isNotEmpty()) {
            sessionManifestEntries.forEachIndexed { idx, entry ->
                val path = entry.strOrNull("path") ?: return@forEachIndexed
                if (!isSafeArchivePath(path)) return@forEachIndexed
                val raw = textEntries[path] ?: return@forEachIndexed
                val sObj = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return@forEachIndexed
                val meta = entry.objOrNull("meta")
                val parsed = parseChatboxSessionObject(
                    sObj = sObj,
                    fallbackId = entry.strOrNull("id")?.takeIf { it.isNotBlank() } ?: "cb2_$idx",
                    metaName = meta?.strOrNull("name"),
                    metaCreatedAt = parseTimestamp(meta?.get("createdAt")),
                    metaStarred = meta?.boolOrNull("starred") ?: false,
                )
                if (parsed != null) items.add(parsed)
            }
        } else {
            // Fallback scan `sessions/*/session.json`
            textEntries.entries
                .filter { it.key.lowercase().startsWith("sessions/") && it.key.lowercase().endsWith(".json") }
                .forEachIndexed { idx, (_, raw) ->
                    val sObj = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return@forEachIndexed
                    parseChatboxSessionObject(sObj, "cb2_$idx", null, null, false)?.let(items::add)
                }
        }

        if (items.isEmpty() && providers.isEmpty()) {
            throw IllegalArgumentException("Chatbox v2 备份中未解析到有效会话或供应商配置")
        }

        return ExternalImportBundle(
            source = ExternalImportSource.CHATBOX,
            sourceFormat = "chatbox_v2_zip",
            items = items.sortedByDescending { it.conversation.updatedAt },
            importedProviders = providers,
        )
    }

    private data class ChatboxForkEntry(
        val position: Int,
        val lists: List<List<JsonObject>>,
    )

    private fun parseChatboxSessionObject(
        sObj: JsonObject,
        fallbackId: String,
        metaName: String?,
        metaCreatedAt: Long?,
        metaStarred: Boolean,
    ): ExternalImportedItem? {
        val now = System.currentTimeMillis()
        val rawId = sObj.strOrNull("id")?.takeIf { it.isNotBlank() } ?: fallbackId
        val convId = "cb_$rawId"
        val title = (
            sObj.strOrNull("threadName")?.takeIf { it.isNotBlank() }
                ?: sObj.strOrNull("name")?.takeIf { it.isNotBlank() }
                ?: sObj.strOrNull("title")?.takeIf { it.isNotBlank() }
                ?: metaName?.takeIf { it.isNotBlank() }
                ?: "Chatbox 对话"
            ).trim()
        val rawMsgs = sObj.arrOrNull("messages")?.mapNotNull { it as? JsonObject } ?: return null

        val forks = parseChatboxForks(sObj.objOrNull("messageForksHash"))
        val sessionModel = sObj.objOrNull("settings")?.strOrNull("modelId")

        var systemPrompt = ""
        var reachedNonSystem = false
        val messages = ArrayList<ChatMessage>()

        rawMsgs.forEachIndexed { mIdx, mObj ->
            val role = mObj.strOrNull("role")?.lowercase()?.trim() ?: return@forEachIndexed
            val (text, reasoning) = extractChatboxContentAndReasoning(mObj)
            val msgTime = parseTimestamp(mObj["timestamp"] ?: mObj["createdAt"]) ?: (now + mIdx * 1000L)

            if (role == "system" && !reachedNonSystem) {
                if (text.isNotBlank()) {
                    systemPrompt = if (systemPrompt.isBlank()) text else "$systemPrompt\n\n$text"
                }
                return@forEachIndexed
            }
            if (role != "user" && role != "assistant") return@forEachIndexed
            reachedNonSystem = true
            if (text.isBlank() && reasoning.isBlank()) return@forEachIndexed

            val msgId = mObj.strOrNull("id") ?: "m$mIdx"
            val model = if (role == "assistant") (mObj.strOrNull("model") ?: sessionModel) else null

            // Reconstruct alternative branches from Chatbox `messageForksHash` (supports both msgId and parent-anchor keys)
            val anchorId = rawMsgs.getOrNull(mIdx - 1)?.strOrNull("id")
            val fork = if (role == "assistant") (forks[msgId] ?: anchorId?.let { forks[it] }) else null
            val variants = ArrayList<MessageVariant>()
            var activeVariantIdx = 0
            if (fork != null && fork.lists.size > 1) {
                fork.lists.forEachIndexed { fIdx, branchMsgs ->
                    if (fIdx == fork.position) {
                        activeVariantIdx = variants.size
                        variants.add(
                            MessageVariant(
                                id = "${convId}_${msgId}_v$fIdx",
                                content = text,
                                reasoning = reasoning,
                                createdAt = msgTime,
                                model = model,
                            ),
                        )
                    } else {
                        val altObj = branchMsgs.firstOrNull()
                        if (altObj != null) {
                            val (altText, altReasoning) = extractChatboxContentAndReasoning(altObj)
                            if (altText.isNotBlank() || altReasoning.isNotBlank()) {
                                val altId = altObj.strOrNull("id") ?: "f$fIdx"
                                variants.add(
                                    MessageVariant(
                                        id = "${convId}_${altId}_v$fIdx",
                                        content = altText,
                                        reasoning = altReasoning,
                                        createdAt = parseTimestamp(altObj["timestamp"] ?: altObj["createdAt"]) ?: msgTime,
                                        model = altObj.strOrNull("model") ?: model,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            val effectiveVariants = if (variants.size > 1) variants else emptyList()

            messages.add(
                ChatMessage(
                    id = "${convId}_$msgId",
                    conversationId = convId,
                    role = role,
                    content = text,
                    reasoning = reasoning,
                    model = model,
                    createdAt = msgTime,
                    seq = messages.size + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(text),
                    variants = effectiveVariants,
                    activeVariantIndex = if (effectiveVariants.isNotEmpty()) activeVariantIdx.coerceIn(0, effectiveVariants.lastIndex) else 0,
                ),
            )
        }

        if (messages.isEmpty()) return null

        val createdAt = parseTimestamp(sObj["createdAt"]) ?: metaCreatedAt ?: messages.first().createdAt
        val updatedAt = maxOf(
            parseTimestamp(sObj["updatedAt"]) ?: createdAt,
            messages.maxOf { it.createdAt },
        )
        val pinned = sObj.boolOrNull("starred") ?: metaStarred
        val conv = ChatConversation(
            id = convId,
            title = title,
            mode = ConversationMode.chat,
            systemPrompt = systemPrompt,
            pinned = pinned,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        return ExternalImportedItem(
            source = ExternalImportSource.CHATBOX,
            sourceId = rawId,
            conversation = conv,
            messages = messages,
            hasReasoning = messages.any { it.reasoning.isNotBlank() },
        )
    }

    private fun parseChatboxForks(forksObj: JsonObject?): Map<String, ChatboxForkEntry> {
        if (forksObj == null || forksObj.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, ChatboxForkEntry>()
        forksObj.forEach { (anchorId, elem) ->
            val fork = elem as? JsonObject ?: return@forEach
            val lists = fork.arrOrNull("lists").orEmpty().map { listElem ->
                (listElem as? JsonObject)?.arrOrNull("messages").orEmpty().mapNotNull { it as? JsonObject }
            }
            if (lists.isNotEmpty()) {
                val pos = (fork.intOrNull("position") ?: lists.lastIndex).coerceIn(0, lists.lastIndex)
                out[anchorId] = ChatboxForkEntry(position = pos, lists = lists)
            }
        }
        return out
    }

    private fun extractChatboxContentAndReasoning(mObj: JsonObject): Pair<String, String> {
        val textParts = ArrayList<String>()
        val reasoningParts = ArrayList<String>()

        val parts = mObj.arrOrNull("contentParts")
        if (parts != null && parts.isNotEmpty()) {
            parts.forEach { part ->
                val pObj = part as? JsonObject
                if (pObj == null) {
                    (part as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }?.let(textParts::add)
                    return@forEach
                }
                when (pObj.strOrNull("type")?.lowercase()) {
                    "reasoning", "thinking" -> {
                        val r = pObj.strOrNull("text") ?: pObj.strOrNull("content")
                        if (!r.isNullOrBlank()) reasoningParts.add(r.trim())
                    }
                    "text", null -> {
                        val t = pObj.strOrNull("text") ?: pObj.strOrNull("content")
                        if (!t.isNullOrBlank()) textParts.add(t)
                    }
                    "image" -> textParts.add("[图片]")
                    "tool-call" -> {
                        val toolName = pObj.strOrNull("toolName").orEmpty().ifBlank { "tool" }
                        val res = pObj["result"]?.let { r ->
                            (r as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
                        }.orEmpty().trim()
                        if (res.isNotBlank()) textParts.add("[工具 $toolName] $res")
                    }
                    else -> {
                        val t = pObj.strOrNull("text") ?: pObj.strOrNull("content")
                        if (!t.isNullOrBlank()) textParts.add(t)
                    }
                }
            }
        }

        val body = if (textParts.isNotEmpty()) {
            textParts.joinToString("\n\n").trim()
        } else {
            when (val contentElem = mObj["content"]) {
                is JsonPrimitive -> contentElem.takeUnless { it is JsonNull }?.contentOrNull.orEmpty().trim()
                is JsonArray -> contentElem.mapNotNull { p ->
                    (p as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
                        ?: (p as? JsonObject)?.strOrNull("text")
                }.joinToString("\n").trim()
                else -> ""
            }
        }

        if (reasoningParts.isEmpty()) {
            val topReasoning = mObj.strOrNull("reasoningContent")
                ?: mObj.strOrNull("reasoning_content")
                ?: mObj.strOrNull("reasoning")
                ?: mObj.strOrNull("thinking")
            if (!topReasoning.isNullOrBlank()) reasoningParts.add(topReasoning.trim())
        }

        return body to reasoningParts.joinToString("\n\n").trim()
    }

    // ==================== RikkaHub settings.json Parser ====================

    private fun isRikkaHubSettingsJson(obj: JsonObject): Boolean {
        val hasAssistants = obj["assistants"] is JsonArray
        val hasRikkaMarker = obj.containsKey("assistantId") ||
            obj.containsKey("displaySetting") ||
            obj.containsKey("lorebooks") ||
            obj.containsKey("modeInjections") ||
            (obj["providers"] as? JsonArray)?.any { (it as? JsonObject)?.strOrNull("type")?.contains("ProviderSetting") == true } == true
        return hasAssistants && hasRikkaMarker || (obj["lorebooks"] is JsonArray && hasRikkaMarker)
    }

    private fun parseRikkaHubSettingsJson(
        root: JsonObject,
        sourceFormat: String,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val providers = ApiProviderImportCodec.tryExtractFromJsonElement(root)?.providers.orEmpty()
        val items = ArrayList<ExternalImportedItem>()

        // 1. Parse RikkaHub assistants (each assistant with systemPrompt or presetMessages -> RP/Chat item)
        root.arrOrNull("assistants").orEmpty().forEachIndexed { idx, elem ->
            val aObj = elem as? JsonObject ?: return@forEachIndexed
            val name = aObj.strOrNull("name")?.trim().orEmpty().ifBlank { "RikkaHub 助手 #${idx + 1}" }
            val sysPrompt = aObj.strOrNull("systemPrompt").orEmpty().trim()
            val presetMsgs = aObj.arrOrNull("presetMessages").orEmpty().mapNotNull { it as? JsonObject }
            val rawId = aObj.strOrNull("id")?.takeIf { it.isNotBlank() } ?: "rh_a$idx"
            val convId = "rh_asst_$rawId"

            val messages = ArrayList<ChatMessage>()
            presetMsgs.forEachIndexed { mIdx, mObj ->
                val role = normalizeGenericRole(mObj.strOrNull("role")) ?: return@forEachIndexed
                if (role != "user" && role != "assistant") return@forEachIndexed
                val (content, reasoning) = extractRikkaUiMessageText(mObj)
                if (content.isBlank() && reasoning.isBlank()) return@forEachIndexed
                messages.add(
                    ChatMessage(
                        id = "${convId}_m$mIdx",
                        conversationId = convId,
                        role = role,
                        content = content,
                        reasoning = reasoning,
                        createdAt = now + mIdx * 1000L,
                        seq = messages.size + 1,
                        status = "done",
                        tokenEstimate = ContextBuilder.estimateMessageTokens(content),
                    ),
                )
            }

            // Skip bare default assistant with neither prompt nor messages
            if (sysPrompt.isBlank() && messages.isEmpty() && aObj["tavernData"] !is JsonObject) return@forEachIndexed

            val tavernObj = aObj.objOrNull("tavernData")
            val isRp = tavernObj != null
            val conv = if (isRp) {
                val charName = tavernObj!!.strOrNull("name")?.takeIf { it.isNotBlank() } ?: name
                val desc = tavernObj.strOrNull("description").orEmpty()
                val personality = tavernObj.strOrNull("personality").orEmpty()
                val scenario = tavernObj.strOrNull("scenario").orEmpty()
                val firstMes = tavernObj.strOrNull("firstMessage") ?: tavernObj.strOrNull("first_mes") ?: ""
                if (messages.isEmpty() && firstMes.isNotBlank()) {
                    messages.add(
                        ChatMessage(
                            id = "${convId}_greeting",
                            conversationId = convId,
                            role = "assistant",
                            content = firstMes,
                            createdAt = now,
                            seq = 1,
                            status = "done",
                            tokenEstimate = ContextBuilder.estimateMessageTokens(firstMes),
                        ),
                    )
                }
                val rp = RpConfig(
                    title = charName,
                    worldview = scenario,
                    systemRules = sysPrompt.ifBlank { tavernObj.strOrNull("systemPrompt").orEmpty() },
                    cast = listOf(
                        RpCharacter(
                            id = UUID.randomUUID().toString(),
                            name = charName,
                            role = "protagonist",
                            appearance = desc.take(600),
                            personality = personality,
                            background = desc,
                            affinity = 20,
                        ),
                    ),
                    greeting = firstMes,
                    cardSource = "rikkahub_assistant",
                    playStyle = RpPlayStyle.tavern,
                )
                ChatConversation(
                    id = convId,
                    title = charName,
                    mode = ConversationMode.rp,
                    rp = rp,
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                ChatConversation(
                    id = convId,
                    title = name,
                    mode = ConversationMode.chat,
                    systemPrompt = sysPrompt,
                    createdAt = now,
                    updatedAt = now,
                )
            }

            items.add(
                ExternalImportedItem(
                    source = ExternalImportSource.RIKKAHUB,
                    sourceId = rawId,
                    conversation = conv,
                    messages = messages,
                    hasReasoning = messages.any { it.reasoning.isNotBlank() },
                ),
            )
        }

        // 2. Parse RikkaHub top-level lorebooks
        root.arrOrNull("lorebooks").orEmpty().forEachIndexed { idx, elem ->
            val lbObj = elem as? JsonObject ?: return@forEachIndexed
            val baseId = lbObj.strOrNull("id")?.takeIf { it.isNotBlank() } ?: "rh_lb$idx"
            val convId = "rh_lore_$baseId"
            val wrapped = JsonObject(mapOf("type" to JsonPrimitive("lorebook"), "data" to lbObj))
            val parsed = TavernAssetCodec.parseRikkaExportEnvelope(wrapped, convId) as? ParsedTavernAsset.Lorebook
            if (parsed != null) {
                items.addAll(buildLorebookBundle(parsed.asset, convId, baseId, ExternalImportSource.RIKKAHUB).items)
            }
        }

        if (items.isEmpty() && providers.isEmpty()) {
            throw IllegalArgumentException("RikkaHub 配置文件中未解析到助手、世界书或 API 供应商")
        }

        return ExternalImportBundle(
            source = ExternalImportSource.RIKKAHUB,
            sourceFormat = sourceFormat,
            items = items,
            importedProviders = providers,
        )
    }

    private fun extractRikkaUiMessageText(mObj: JsonObject): Pair<String, String> {
        val textParts = ArrayList<String>()
        val reasoningParts = ArrayList<String>()
        mObj.arrOrNull("parts").orEmpty().forEach { p ->
            val pObj = p as? JsonObject ?: return@forEach
            val type = pObj.strOrNull("type").orEmpty().lowercase()
            when {
                type.contains("reasoning") -> pObj.strOrNull("reasoning")?.takeIf { it.isNotBlank() }?.let(reasoningParts::add)
                type.contains("text") || pObj.containsKey("text") -> pObj.strOrNull("text")?.takeIf { it.isNotBlank() }?.let(textParts::add)
            }
        }
        if (textParts.isEmpty()) {
            mObj.strOrNull("content")?.takeIf { it.isNotBlank() }?.let(textParts::add)
        }
        return textParts.joinToString("\n\n").trim() to reasoningParts.joinToString("\n\n").trim()
    }

    // ==================== SillyTavern (酒馆) Parsers ====================

    private fun parseSillyTavernArchiveZip(
        textEntries: Map<String, String>,
        pngEntries: Map<String, ByteArray>,
    ): ExternalImportBundle {
        val collected = ArrayList<ExternalImportedItem>()

        fun inDir(path: String, dir: String): Boolean {
            val low = path.lowercase()
            val d = dir.lowercase()
            return low.startsWith("$d/") || low.contains("/$d/")
        }

        pngEntries.forEach { (path, pngBytes) ->
            val cardJson = RpCardCodec.extractCardJsonFromPng(pngBytes)
            if (cardJson != null) {
                runCatching { parseSillyTavernCardJson(cardJson, path) }.getOrNull()?.let { b ->
                    collected.addAll(b.items)
                }
            }
        }

        textEntries.forEach { (path, content) ->
            val lower = path.lowercase()
            val baseName = lower.substringAfterLast('/')
            when {
                inDir(path, "characters") && lower.endsWith(".json") -> {
                    runCatching { parseSillyTavernCardJson(content, path) }.getOrNull()?.let { collected.addAll(it.items) }
                }
                inDir(path, "worlds") && lower.endsWith(".json") -> {
                    runCatching {
                        val root = AppJson.parseToJsonElement(content)
                        val baseId = path.hashCode().toUInt().toString(16)
                        val convId = "st_lore_$baseId"
                        val asset = TavernAssetCodec.parseLorebookJson(root, convId, path)
                        buildLorebookBundle(asset, convId, baseId, ExternalImportSource.SILLYTAVERN)
                    }.getOrNull()?.let { collected.addAll(it.items) }
                }
                inDir(path, "OpenAI Settings") && lower.endsWith(".json") -> {
                    runCatching {
                        val obj = AppJson.parseToJsonElement(content) as? JsonObject ?: return@runCatching null
                        val baseId = path.hashCode().toUInt().toString(16)
                        val convId = "st_preset_$baseId"
                        val asset = TavernAssetCodec.parseChatPresetJson(obj, convId, path)
                        buildPresetBundle(asset, convId, baseId, ExternalImportSource.SILLYTAVERN)
                    }.getOrNull()?.let { collected.addAll(it.items) }
                }
                inDir(path, "QuickReplies") && lower.endsWith(".json") -> {
                    runCatching {
                        val root = AppJson.parseToJsonElement(content)
                        val qr = TavernAssetCodec.parseQuickRepliesJson(root, path)
                        buildQuickRepliesBundle(qr, path)
                    }.getOrNull()?.let { collected.addAll(it.items) }
                }
                baseName == "settings.json" -> {
                    runCatching {
                        val obj = AppJson.parseToJsonElement(content) as? JsonObject ?: return@runCatching null
                        val st = TavernAssetCodec.parseSillyTavernSettingsJson(obj)
                        if (st.regexScripts.isNotEmpty() || st.authorNote.isNotBlank()) {
                            buildTavernSettingsBundle(st, path)
                        } else {
                            null
                        }
                    }.getOrNull()?.let { collected.addAll(it.items) }
                }
                lower.endsWith(".jsonl") -> {
                    runCatching { parseSillyTavernJsonl(content, path) }.getOrNull()?.let { collected.addAll(it.items) }
                }
            }
        }

        return ExternalImportBundle(
            source = ExternalImportSource.SILLYTAVERN,
            sourceFormat = "sillytavern_archive_zip",
            items = collected.sortedByDescending { it.conversation.updatedAt },
        )
    }

    private fun isSillyTavernCardObject(obj: JsonObject): Boolean {
        val spec = obj.strOrNull("spec").orEmpty()
        if (spec.equals("chara_card_v2", ignoreCase = true) ||
            spec.equals("chara_card_v3", ignoreCase = true) ||
            spec.equals("chara_card_v1", ignoreCase = true) ||
            spec.equals("chara_card", ignoreCase = true)
        ) {
            return true
        }
        val data = obj.objOrNull("data")
        if (data != null && (
                data.containsKey("first_mes") ||
                    data.containsKey("mes_example") ||
                    data.containsKey("character_book") ||
                    (!data.strOrNull("name").isNullOrBlank() &&
                        (data.containsKey("description") || data.containsKey("personality") || data.containsKey("scenario")))
                )
        ) {
            return true
        }
        if (obj.containsKey("first_mes") ||
            obj.containsKey("mes_example") ||
            obj.containsKey("char_greeting") ||
            (obj.containsKey("char_name") && obj.containsKey("char_persona"))
        ) {
            return true
        }
        // Spec-less V1 card: top-level `name` + (`description` / `personality` / `scenario`), excluding chat/preset/lorebook/provider objects
        val hasName = !obj.strOrNull("name").isNullOrBlank()
        val hasPersonaField = obj.containsKey("description") || obj.containsKey("personality") || obj.containsKey("scenario")
        val looksLikeOtherFormat = obj.containsKey("messages") ||
            obj.containsKey("chat_messages") ||
            obj.containsKey("mapping") ||
            obj.containsKey("conversations") ||
            obj.containsKey("sessions") ||
            obj.containsKey("prompts") ||
            obj.containsKey("entries") ||
            obj.containsKey("providers") ||
            obj.containsKey("scenes") ||
            obj.containsKey("chapters") ||
            obj.containsKey("dialogues") ||
            obj.containsKey("qrList") ||
            obj.containsKey("quickReplies") ||
            obj.containsKey("baseUrl") ||
            obj.containsKey("apiKey")
        return hasName && hasPersonaField && !looksLikeOtherFormat
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

        val allGreetings = (listOf(rp.greeting) + rp.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }
        val messages = if (allGreetings.isNotEmpty()) {
            val variants = allGreetings.mapIndexed { idx, g ->
                MessageVariant(
                    id = "${convId}_greeting_v$idx",
                    content = g,
                    createdAt = now,
                    rpStateSnapshot = rp.state,
                )
            }
            listOf(
                ChatMessage(
                    id = "${convId}_greeting",
                    conversationId = convId,
                    role = "assistant",
                    content = allGreetings.first(),
                    createdAt = now,
                    seq = 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(allGreetings.first()),
                    variants = variants,
                    activeVariantIndex = 0,
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

    private fun parseTavernAssetToBundle(
        asset: ParsedTavernAsset,
        source: ExternalImportSource,
        fileName: String?,
    ): ExternalImportBundle = when (asset) {
        is ParsedTavernAsset.Lorebook -> {
            val baseId = asset.asset.name.hashCode().toUInt().toString(16)
            val convId = if (source == ExternalImportSource.RIKKAHUB) "rh_lore_$baseId" else "st_lore_$baseId"
            val remapped = asset.asset.copy(entries = asset.asset.entries.map { it.copy(conversationId = convId) })
            buildLorebookBundle(remapped, convId, baseId, source)
        }
        is ParsedTavernAsset.Preset -> {
            val baseId = asset.asset.name.hashCode().toUInt().toString(16)
            val convId = if (source == ExternalImportSource.RIKKAHUB) "rh_preset_$baseId" else "st_preset_$baseId"
            val remapped = asset.asset.copy(loreEntries = asset.asset.loreEntries.map { it.copy(conversationId = convId) })
            buildPresetBundle(remapped, convId, baseId, source)
        }
        is ParsedTavernAsset.RegexScripts -> buildRegexScriptsBundle(asset.scripts, fileName, asset.sourceFormat)
        is ParsedTavernAsset.QuickReplies -> buildQuickRepliesBundle(asset.asset, fileName)
        is ParsedTavernAsset.Settings -> buildTavernSettingsBundle(asset.asset, fileName)
    }

    private fun buildLorebookBundle(
        loreAsset: ParsedLorebookAsset,
        convId: String,
        baseId: String,
        source: ExternalImportSource,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = loreAsset.name
        val summary = "已导入世界书《$title》，包含 ${loreAsset.entries.size} 条设定（可在右上角文游台「世界书」或记忆库中查看与触发）。"
        val rp = RpConfig(
            title = title,
            worldview = loreAsset.description.ifBlank {
                loreAsset.entries.take(3).joinToString("\n\n") { "【${it.title}】${it.content.take(180)}" }
            },
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = loreAsset.sourceFormat,
            playStyle = RpPlayStyle.tavern,
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
            source = source,
            sourceFormat = loreAsset.sourceFormat,
            items = listOf(
                ExternalImportedItem(
                    source = source,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
                    loreEntries = loreAsset.entries,
                ),
            ),
        )
    }

    private fun buildPresetBundle(
        preset: ParsedPresetAsset,
        convId: String,
        baseId: String,
        source: ExternalImportSource,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = preset.name
        val details = buildList {
            if (preset.systemRules.isNotBlank()) add("主系统提示词")
            if (preset.postHistoryInstructions.isNotBlank()) add("后置/破限指令")
            if (preset.loreEntries.isNotEmpty()) add("${preset.loreEntries.size} 条预设条目")
            if (preset.regexScripts.isNotEmpty()) add("${preset.regexScripts.size} 条正则脚本")
        }.joinToString("、")
        val summary = "已导入预设《$title》（含 $details）。也可在任意文游会话右上角「文游台 → 世界书 → 导入世界书/预设」直接合并到已有角色。"
        val rp = RpConfig(
            title = title,
            systemRules = preset.systemRules,
            postHistoryInstructions = preset.postHistoryInstructions,
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = preset.sourceFormat,
            playStyle = RpPlayStyle.tavern,
            regexScripts = preset.regexScripts,
        )
        val params = if (preset.temperature != null || preset.topP != null || preset.maxTokens != null) {
            ChatParams(
                temperature = preset.temperature ?: 0.7,
                topP = preset.topP ?: 1.0,
                maxTokens = preset.maxTokens,
            )
        } else {
            ChatParams()
        }
        val conv = ChatConversation(
            id = convId,
            title = title,
            mode = ConversationMode.rp,
            systemPrompt = preset.systemRules,
            params = params,
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
            source = source,
            sourceFormat = preset.sourceFormat,
            items = listOf(
                ExternalImportedItem(
                    source = source,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
                    loreEntries = preset.loreEntries,
                ),
            ),
        )
    }

    private fun buildRegexScriptsBundle(
        scripts: List<RpRegexScript>,
        fileName: String?,
        sourceFormat: String,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()
            .ifBlank { scripts.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "酒馆正则脚本" }
        val baseId = (title + scripts.size).hashCode().toUInt().toString(16)
        val convId = "st_regex_$baseId"
        val namesPreview = scripts.take(5).joinToString("、") { it.name }
        val summary = "已导入 ${scripts.size} 条酒馆正则脚本（$namesPreview）。也可在已有文游会话「文游台 → 前端与美化 → 导入正则 JSON」中直接追加。"
        val rp = RpConfig(
            title = title,
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = sourceFormat,
            playStyle = RpPlayStyle.tavern,
            regexScripts = scripts,
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
            sourceFormat = sourceFormat,
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
                ),
            ),
        )
    }

    private fun buildQuickRepliesBundle(
        qr: ParsedQuickRepliesAsset,
        fileName: String?,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = qr.name.ifBlank { fileName?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty().ifBlank { "酒馆快速回复" } }
        val baseId = (title + qr.actions.size).hashCode().toUInt().toString(16)
        val convId = "st_qr_$baseId"
        val summary = "已导入快速回复组《$title》，包含 ${qr.actions.size} 条快捷指令（已写入文游快捷行动栏）。"
        val rp = RpConfig(
            title = title,
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = qr.sourceFormat,
            playStyle = RpPlayStyle.tavern,
            quickActions = qr.actions,
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
            sourceFormat = qr.sourceFormat,
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
                ),
            ),
        )
    }

    private fun buildTavernSettingsBundle(
        settings: ParsedTavernSettingsAsset,
        fileName: String?,
    ): ExternalImportBundle {
        val now = System.currentTimeMillis()
        val title = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "酒馆全局设置" }
        val baseId = (title + settings.regexScripts.size).hashCode().toUInt().toString(16)
        val convId = "st_cfg_$baseId"
        val parts = buildList {
            if (settings.regexScripts.isNotEmpty()) add("${settings.regexScripts.size} 条全局正则脚本")
            if (settings.authorNote.isNotBlank()) add("默认作者注释")
        }.joinToString("、")
        val summary = "已从 SillyTavern settings.json 提取 $parts。"
        val rp = RpConfig(
            title = title,
            authorNote = settings.authorNote,
            cast = listOf(RpCharacter(id = "${convId}_gm", name = title, role = "narrator", affinity = 20)),
            greeting = summary,
            cardSource = settings.sourceFormat,
            playStyle = RpPlayStyle.tavern,
            regexScripts = settings.regexScripts,
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
            sourceFormat = settings.sourceFormat,
            items = listOf(
                ExternalImportedItem(
                    source = ExternalImportSource.SILLYTAVERN,
                    sourceId = baseId,
                    conversation = conv,
                    messages = listOf(greetingMsg),
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
                obj.strOrNull("user_name")?.takeIf { it.isNotBlank() }?.let { userName = it }
                obj.strOrNull("character_name")?.takeIf { it.isNotBlank() }?.let { charName = it }
                parseTimestamp(obj["create_date"])?.let { createTime = it }
                return@forEachIndexed
            }

            if (obj.boolOrNull("is_system") == true) return@forEachIndexed
            val isUser = obj.boolOrNull("is_user") ?: false
            val mes = obj.strOrNull("mes").orEmpty().trim()
            val swipesArr = obj.arrOrNull("swipes")?.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.contentOrNull }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val swipeId = obj.intOrNull("swipe_id") ?: 0
            val extra = obj.objOrNull("extra")
            val reasoning = (extra?.strOrNull("reasoning")
                ?: obj.strOrNull("reasoning")).orEmpty().trim()
            val model = extra?.strOrNull("model")
            val msgTime = parseTimestamp(obj["send_date"] ?: obj["gen_finished"]) ?: (createTime + idx * 1000L)
            if (!isUser && charName.isBlank()) {
                obj.strOrNull("name")?.takeIf { it.isNotBlank() }?.let { charName = it }
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
        if (firstMsg.containsKey("fragments") || firstMsg.containsKey("parent_id")) return false
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
            val rawId = obj.strOrNull("uuid") ?: obj.strOrNull("id") ?: "claude_$idx"
            val convId = "claude_$rawId"
            val rawMsgs = obj.arrOrNull("chat_messages") ?: return@mapIndexedNotNull null
            val messages = ArrayList<ChatMessage>()
            rawMsgs.forEachIndexed { mIdx, mElem ->
                val mObj = mElem as? JsonObject ?: return@forEachIndexed
                val sender = mObj.strOrNull("sender")?.lowercase() ?: mObj.strOrNull("role")?.lowercase() ?: return@forEachIndexed
                val role = when (sender) {
                    "human", "user" -> "user"
                    "assistant" -> "assistant"
                    else -> return@forEachIndexed
                }
                val contentParts = ArrayList<String>()
                val reasoningParts = ArrayList<String>()
                val contentArr = mObj.arrOrNull("content")
                if (contentArr != null && contentArr.isNotEmpty()) {
                    contentArr.forEach { blockElem ->
                        val block = blockElem as? JsonObject ?: return@forEach
                        when (block.strOrNull("type")?.lowercase()) {
                            "thinking" -> block.strOrNull("thinking")?.takeIf { it.isNotBlank() }?.let { reasoningParts.add(it) }
                            "text" -> block.strOrNull("text")?.takeIf { it.isNotBlank() }?.let { contentParts.add(it) }
                        }
                    }
                }
                if (contentParts.isEmpty()) {
                    mObj.strOrNull("text")?.takeIf { it.isNotBlank() }?.let { contentParts.add(it) }
                }
                val body = contentParts.joinToString("\n\n").trim()
                val reasoning = reasoningParts.joinToString("\n\n").trim()
                if (body.isBlank() && reasoning.isBlank()) return@forEachIndexed
                val msgTime = parseTimestamp(mObj["created_at"]) ?: (now + mIdx * 1000L)
                val msgId = mObj.strOrNull("uuid") ?: "m$mIdx"
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
            val title = obj.strOrNull("name")?.trim().orEmpty()
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

    // ==================== Relaxed Generic Chat & ShareGPT JSON Fallback ====================

    private fun tryParseGenericChatJson(
        root: JsonElement,
        fileName: String?,
        hintSource: ExternalImportSource?,
    ): ExternalImportBundle? {
        val now = System.currentTimeMillis()
        val fallbackTitle = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()
            .ifBlank { "导入对话" }
        val items = ArrayList<ExternalImportedItem>()

        when (root) {
            is JsonArray -> {
                val objects = root.mapNotNull { it as? JsonObject }
                if (objects.isEmpty()) return null
                // Case A: Bare array of messages (e.g. OpenAI messages[] or ShareGPT [{from, value}])
                val directMsgs = parseGenericMessageObjects(objects, "gen_${fallbackTitle.hashCode().toUInt().toString(16)}", now)
                if (directMsgs.first.isNotEmpty()) {
                    val baseId = fallbackTitle.hashCode().toUInt().toString(16)
                    val convId = "gen_$baseId"
                    val (msgs, sysPrompt) = directMsgs
                    val title = fallbackTitle.ifBlank {
                        msgs.firstOrNull { it.role == "user" }?.content?.take(24).orEmpty().ifBlank { "导入对话" }
                    }
                    items.add(
                        ExternalImportedItem(
                            source = hintSource ?: ExternalImportSource.GENERIC,
                            sourceId = baseId,
                            conversation = ChatConversation(
                                id = convId,
                                title = title,
                                mode = ConversationMode.chat,
                                systemPrompt = sysPrompt,
                                createdAt = msgs.first().createdAt,
                                updatedAt = msgs.maxOf { it.createdAt },
                            ),
                            messages = msgs.map { it.copy(conversationId = convId) },
                            hasReasoning = msgs.any { it.reasoning.isNotBlank() },
                        ),
                    )
                } else {
                    // Case B: Array of conversation objects with flexible message list keys
                    objects.forEachIndexed { idx, convObj ->
                        parseGenericConversationObject(convObj, "gen_$idx", fallbackTitle, now, hintSource)?.let(items::add)
                    }
                }
            }
            is JsonObject -> {
                val convListKeys = listOf("conversations", "chats", "records", "history", "items", "list", "sessions", "data")
                var foundList = false
                for (key in convListKeys) {
                    val arr = root.arrOrNull(key) ?: continue
                    val objs = arr.mapNotNull { it as? JsonObject }
                    if (objs.isEmpty()) continue
                    // Check if the array is a direct message array inside a wrapper object
                    val asMsgs = parseGenericMessageObjects(objs, "gen_${fallbackTitle.hashCode().toUInt().toString(16)}", now)
                    if (asMsgs.first.isNotEmpty()) {
                        foundList = true
                        val baseId = root.strOrNull("id")?.takeIf { it.isNotBlank() }
                            ?: fallbackTitle.hashCode().toUInt().toString(16)
                        val convId = "gen_$baseId"
                        val title = root.strOrNull("title")?.takeIf { it.isNotBlank() }
                            ?: root.strOrNull("name")?.takeIf { it.isNotBlank() }
                            ?: fallbackTitle
                        val (msgs, sysPrompt) = asMsgs
                        val convSys = root.strOrNull("systemPrompt") ?: root.strOrNull("system_prompt") ?: root.strOrNull("system") ?: ""
                        items.add(
                            ExternalImportedItem(
                                source = hintSource ?: ExternalImportSource.GENERIC,
                                sourceId = baseId,
                                conversation = ChatConversation(
                                    id = convId,
                                    title = title,
                                    mode = ConversationMode.chat,
                                    systemPrompt = listOf(convSys, sysPrompt).filter { it.isNotBlank() }.joinToString("\n\n"),
                                    createdAt = parseTimestamp(root["createdAt"] ?: root["created_at"]) ?: msgs.first().createdAt,
                                    updatedAt = maxOf(parseTimestamp(root["updatedAt"] ?: root["updated_at"]) ?: msgs.first().createdAt, msgs.maxOf { it.createdAt }),
                                ),
                                messages = msgs.map { it.copy(conversationId = convId) },
                                hasReasoning = msgs.any { it.reasoning.isNotBlank() },
                            ),
                        )
                        break
                    }
                    // Otherwise treat as array of conversations
                    val parsedConvs = objs.mapIndexedNotNull { idx, cObj ->
                        parseGenericConversationObject(cObj, "gen_$idx", fallbackTitle, now, hintSource)
                    }
                    if (parsedConvs.isNotEmpty()) {
                        foundList = true
                        items.addAll(parsedConvs)
                        break
                    }
                }
                if (!foundList) {
                    parseGenericConversationObject(root, "gen_0", fallbackTitle, now, hintSource)?.let(items::add)
                }
            }
            else -> return null
        }

        if (items.isEmpty()) return null
        return ExternalImportBundle(
            source = hintSource ?: ExternalImportSource.GENERIC,
            sourceFormat = "generic_chat_json",
            items = items.sortedByDescending { it.conversation.updatedAt },
        )
    }

    private fun parseGenericConversationObject(
        obj: JsonObject,
        fallbackId: String,
        fallbackTitle: String,
        now: Long,
        hintSource: ExternalImportSource?,
    ): ExternalImportedItem? {
        val msgKeys = listOf("messages", "chat_messages", "conversation", "conversations", "history", "records", "items", "turns", "dialog")
        val msgArr = msgKeys.firstNotNullOfOrNull { obj.arrOrNull(it) } ?: return null
        val msgObjs = msgArr.mapNotNull { it as? JsonObject }
        if (msgObjs.isEmpty()) return null

        val rawId = obj.strOrNull("id") ?: obj.strOrNull("uuid") ?: fallbackId
        val convId = "gen_$rawId"
        val (msgs, extractedSys) = parseGenericMessageObjects(msgObjs, convId, now)
        if (msgs.isEmpty()) return null

        val topSys = obj.strOrNull("systemPrompt") ?: obj.strOrNull("system_prompt") ?: obj.strOrNull("system") ?: ""
        val title = (
            obj.strOrNull("title")?.takeIf { it.isNotBlank() }
                ?: obj.strOrNull("name")?.takeIf { it.isNotBlank() }
                ?: msgs.firstOrNull { it.role == "user" }?.content?.lineSequence()?.firstOrNull()?.trim()?.take(28)?.takeIf { it.isNotBlank() }
                ?: fallbackTitle
            ).trim()
        val createdAt = parseTimestamp(obj["createdAt"] ?: obj["created_at"] ?: obj["create_time"]) ?: msgs.first().createdAt
        val updatedAt = maxOf(
            parseTimestamp(obj["updatedAt"] ?: obj["updated_at"] ?: obj["update_time"]) ?: createdAt,
            msgs.maxOf { it.createdAt },
        )
        return ExternalImportedItem(
            source = hintSource ?: ExternalImportSource.GENERIC,
            sourceId = rawId,
            conversation = ChatConversation(
                id = convId,
                title = title,
                mode = ConversationMode.chat,
                systemPrompt = listOf(topSys, extractedSys).filter { it.isNotBlank() }.joinToString("\n\n"),
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            messages = msgs,
            hasReasoning = msgs.any { it.reasoning.isNotBlank() },
        )
    }

    private fun parseGenericMessageObjects(
        objects: List<JsonObject>,
        convId: String,
        now: Long,
    ): Pair<List<ChatMessage>, String> {
        val messages = ArrayList<ChatMessage>()
        val systemParts = ArrayList<String>()

        objects.forEachIndexed { idx, obj ->
            val rawRole = obj.strOrNull("role")
                ?: obj.strOrNull("sender")
                ?: obj.strOrNull("from")
                ?: obj.strOrNull("speaker")
                ?: obj.objOrNull("author")?.strOrNull("role")
                ?: obj.boolOrNull("is_user")?.let { if (it) "user" else "assistant" }
                ?: return@forEachIndexed
            val role = normalizeGenericRole(rawRole) ?: return@forEachIndexed

            val content = extractGenericMessageContent(obj)
            val reasoning = (
                obj.strOrNull("reasoning")
                    ?: obj.strOrNull("reasoning_content")
                    ?: obj.strOrNull("reasoningContent")
                    ?: obj.strOrNull("thinking")
                    ?: obj.strOrNull("thinking_content")
                    ?: obj.strOrNull("thought")
                ).orEmpty().trim()

            if (role == "system") {
                if (content.isNotBlank()) systemParts.add(content)
                return@forEachIndexed
            }
            if (content.isBlank() && reasoning.isBlank()) return@forEachIndexed

            val msgTime = parseTimestamp(
                obj["timestamp"] ?: obj["createdAt"] ?: obj["created_at"] ?: obj["create_time"]
                    ?: obj["inserted_at"] ?: obj["time"] ?: obj["date"],
            ) ?: (now + idx * 1000L)
            val msgId = obj.strOrNull("id") ?: obj.strOrNull("uuid") ?: "m$idx"
            messages.add(
                ChatMessage(
                    id = "${convId}_$msgId",
                    conversationId = convId,
                    role = role,
                    content = content,
                    reasoning = reasoning,
                    model = if (role == "assistant") obj.strOrNull("model") else null,
                    createdAt = msgTime,
                    seq = messages.size + 1,
                    status = "done",
                    tokenEstimate = ContextBuilder.estimateMessageTokens(content),
                ),
            )
        }
        return messages to systemParts.joinToString("\n\n").trim()
    }

    private fun normalizeGenericRole(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "user", "human", "you", "me", "request", "question", "用户", "我", "提问" -> "user"
        "assistant", "ai", "gpt", "chatgpt", "claude", "deepseek", "bot", "model", "char", "response", "answer", "助手", "模型", "回复" -> "assistant"
        "system", "系统" -> "system"
        else -> null
    }

    private fun extractGenericMessageContent(obj: JsonObject): String {
        val candidateKeys = listOf("content", "text", "value", "body", "message", "mes")
        for (k in candidateKeys) {
            val elem = obj[k] ?: continue
            val extracted = when (elem) {
                is JsonPrimitive -> elem.takeUnless { it is JsonNull }?.contentOrNull.orEmpty().trim()
                is JsonArray -> elem.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> item.takeUnless { it is JsonNull }?.contentOrNull
                        is JsonObject -> item.strOrNull("text") ?: item.strOrNull("content") ?: item.strOrNull("value")
                        else -> null
                    }
                }.filter { it.isNotBlank() }.joinToString("\n\n").trim()
                is JsonObject -> {
                    val parts = elem.arrOrNull("parts")
                    parts?.mapNotNull { p ->
                        (p as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
                            ?: (p as? JsonObject)?.strOrNull("text")
                    }?.joinToString("\n").orEmpty().trim()
                        .ifBlank { elem.strOrNull("text") ?: elem.strOrNull("content") ?: "" }
                }
            }
            if (extracted.isNotEmpty()) return extracted
        }
        return ""
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
        val title = (root.strOrNull("title") ?: root.strOrNull("name") ?: fileName?.substringAfterLast('/')?.substringBeforeLast('.'))
            .orEmpty().trim().ifBlank { "橙光互动剧本" }
        val baseId = (root.strOrNull("id") ?: title.hashCode().toUInt().toString(16))
        val convId = "cg_$baseId"
        val worldview = (root.strOrNull("worldview") ?: root.strOrNull("synopsis") ?: root.strOrNull("description")).orEmpty().trim()

        val cast = root.arrOrNull("characters")?.mapIndexedNotNull { idx, elem ->
            when (elem) {
                is JsonPrimitive -> elem.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }?.let { n ->
                    RpCharacter(id = "${convId}_c$idx", name = n, role = "companion", affinity = 20)
                }
                is JsonObject -> {
                    val name = elem.strOrNull("name")?.trim().orEmpty()
                    if (name.isEmpty()) null
                    else RpCharacter(
                        id = "${convId}_c$idx",
                        name = name,
                        role = elem.strOrNull("role") ?: "companion",
                        personality = (elem.strOrNull("personality") ?: elem.strOrNull("desc")).orEmpty(),
                        background = elem.strOrNull("background").orEmpty(),
                        affinity = elem.intOrNull("affinity") ?: 20,
                    )
                }
                else -> null
            }
        }.orEmpty()

        val extractedCastNames = LinkedHashSet<String>(cast.map { it.name })
        val messages = ArrayList<ChatMessage>()
        var lastChoices: List<RpChoice> = emptyList()
        var firstSceneName = ""

        val sceneArray = root.arrOrNull("scenes")
            ?: root.arrOrNull("chapters")
            ?: root.arrOrNull("story_nodes")

        if (sceneArray != null && sceneArray.isNotEmpty()) {
            sceneArray.forEachIndexed { sIdx, sElem ->
                val sObj = sElem as? JsonObject ?: return@forEachIndexed
                val sTitle = (sObj.strOrNull("title") ?: sObj.strOrNull("name") ?: sObj.strOrNull("scene")).orEmpty().trim()
                if (firstSceneName.isBlank() && sTitle.isNotBlank()) firstSceneName = sTitle
                val sb = StringBuilder()
                if (sTitle.isNotBlank()) sb.appendLine("### $sTitle\n")

                val directText = (sObj.strOrNull("narration") ?: sObj.strOrNull("content") ?: sObj.strOrNull("text")).orEmpty().trim()
                if (directText.isNotEmpty()) sb.appendLine(directText)

                val lines = sObj.arrOrNull("dialogues") ?: sObj.arrOrNull("lines")
                lines?.forEach { lElem ->
                    val lObj = lElem as? JsonObject
                    if (lObj != null) {
                        val sp = (lObj.strOrNull("speaker") ?: lObj.strOrNull("character") ?: lObj.strOrNull("name")).orEmpty().trim()
                        val txt = (lObj.strOrNull("text") ?: lObj.strOrNull("content") ?: lObj.strOrNull("line")).orEmpty().trim()
                        if (txt.isNotEmpty()) {
                            if (sp.isNotEmpty()) {
                                if (sp != "旁白" && sp != "系统") extractedCastNames.add(sp)
                                sb.appendLine("**$sp**：$txt")
                            } else {
                                sb.appendLine(txt)
                            }
                        }
                    } else if (lElem is JsonPrimitive && lElem !is JsonNull && !lElem.contentOrNull.isNullOrBlank()) {
                        sb.appendLine(lElem.content)
                    }
                }

                val choices = parseJsonChoices(sObj.arrOrNull("choices") ?: sObj.arrOrNull("options"), "${convId}_s${sIdx}")
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
                val sp = (dObj.strOrNull("speaker") ?: dObj.strOrNull("character")).orEmpty().trim()
                val txt = (dObj.strOrNull("text") ?: dObj.strOrNull("content")).orEmpty().trim()
                if (txt.isNotEmpty()) {
                    if (sp.isNotEmpty()) {
                        if (sp != "旁白") extractedCastNames.add(sp)
                        sb.appendLine("**$sp**：$txt")
                    } else {
                        sb.appendLine(txt)
                    }
                }
            }
            lastChoices = parseJsonChoices(root.arrOrNull("choices") ?: root.arrOrNull("options"), convId)
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
                is JsonPrimitive -> elem.takeUnless { it is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { txt ->
                    RpChoice(id = "${prefix}_ch$idx", label = txt, actionText = txt)
                }
                is JsonObject -> {
                    val label = (elem.strOrNull("label") ?: elem.strOrNull("text") ?: elem.strOrNull("title")).orEmpty().trim()
                    if (label.isEmpty()) null
                    else RpChoice(
                        id = "${prefix}_ch$idx",
                        label = label,
                        actionText = elem.strOrNull("actionText")?.takeIf { it.isNotBlank() } ?: label,
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

    private fun isSafeArchivePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        return path.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

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

    private fun parseTimestamp(elem: JsonElement?): Long? {
        val prim = elem as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
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
