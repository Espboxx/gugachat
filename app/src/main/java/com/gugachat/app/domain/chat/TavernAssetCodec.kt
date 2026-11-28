package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.core.util.HashUtils
import com.gugachat.app.core.util.JsonUtils.arrOrNull
import com.gugachat.app.core.util.JsonUtils.boolOrNull
import com.gugachat.app.core.util.JsonUtils.intOrNull
import com.gugachat.app.core.util.JsonUtils.objOrNull
import com.gugachat.app.core.util.JsonUtils.strListOrEmpty
import com.gugachat.app.core.util.JsonUtils.strOrNull
import com.gugachat.app.data.local.PromptPresetEntity
import com.gugachat.app.data.local.WorldbookEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

data class ParsedWorldbookV4(
    val worldbook: WorldbookEntity,
    val entries: List<WorldbookEntryEntity>,
)

data class ParsedLorebookAsset(
    val name: String,
    val description: String = "",
    val entries: List<NovelLoreEntry>,
    val sourceFormat: String,
)

data class ParsedPresetAsset(
    val name: String,
    val systemRules: String = "",
    val postHistoryInstructions: String = "",
    val loreEntries: List<NovelLoreEntry> = emptyList(),
    val regexScripts: List<RpRegexScript> = emptyList(),
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val sourceFormat: String,
)

data class ParsedQuickRepliesAsset(
    val name: String,
    val actions: List<String>,
    val sourceFormat: String,
)

data class ParsedTavernSettingsAsset(
    val regexScripts: List<RpRegexScript> = emptyList(),
    val authorNote: String = "",
    val sourceFormat: String = "sillytavern_settings",
)

sealed interface ParsedTavernAsset {
    data class Lorebook(val asset: ParsedLorebookAsset) : ParsedTavernAsset
    data class Preset(val asset: ParsedPresetAsset) : ParsedTavernAsset
    data class RegexScripts(val scripts: List<RpRegexScript>, val sourceFormat: String) : ParsedTavernAsset
    data class QuickReplies(val asset: ParsedQuickRepliesAsset) : ParsedTavernAsset
    data class Settings(val asset: ParsedTavernSettingsAsset) : ParsedTavernAsset
}

/**
 * Pure-JVM codec aligning with RikkaHub (upstream + SillyTavern fork) standalone JSON asset importers:
 * - RikkaHub native `ExportData` (`type: "lorebook" | "mode_injection"`)
 * - SillyTavern standalone World Info / Lorebook (`entries` Map or Array, plus Tavern Helper `WorldbookEntry`)
 * - SillyTavern Chat Completion Presets (ordered `prompts` + `prompt_order`, flat `main_prompt` / `jailbreak_prompt`, embedded `regex_scripts`)
 * - SillyTavern standalone Regex Scripts (single object, array, snake_case Tavern Helper, or `settings.json` `extension_settings.regex`)
 * - SillyTavern Quick Replies v1 / v2 (`qrList` / `quickReplies`)
 */
object TavernAssetCodec {

    fun parseJsonText(
        rawJson: String,
        conversationId: String,
        fileName: String? = null,
    ): ParsedTavernAsset? {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val root = runCatching { CoreAppJson.parseToJsonElement(cleaned) }.getOrNull() ?: return null
        return parseJsonElement(root, conversationId, fileName)
    }

    fun parseJsonElement(
        root: JsonElement,
        conversationId: String,
        fileName: String? = null,
    ): ParsedTavernAsset? {
        if (root is JsonObject) {
            if (isRikkaExportEnvelope(root)) {
                return parseRikkaExportEnvelope(root, conversationId, fileName)
            }
            if (isLorebookJson(root)) {
                return ParsedTavernAsset.Lorebook(parseLorebookJson(root, conversationId, fileName))
            }
            if (isChatPresetJson(root)) {
                return ParsedTavernAsset.Preset(parseChatPresetJson(root, conversationId, fileName))
            }
            if (isQuickRepliesJson(root)) {
                return ParsedTavernAsset.QuickReplies(parseQuickRepliesJson(root, fileName))
            }
            if (isRegexScriptJson(root)) {
                val scripts = parseRegexScriptsJson(root)
                if (scripts.isNotEmpty()) {
                    return ParsedTavernAsset.RegexScripts(scripts, "sillytavern_regex")
                }
            }
            if (isSillyTavernSettingsJson(root)) {
                val settings = parseSillyTavernSettingsJson(root)
                if (settings.regexScripts.isNotEmpty() || settings.authorNote.isNotBlank()) {
                    return ParsedTavernAsset.Settings(settings)
                }
            }
        } else if (root is JsonArray) {
            if (isTavernHelperWorldbookArray(root)) {
                return ParsedTavernAsset.Lorebook(parseLorebookJson(root, conversationId, fileName))
            }
            if (isRegexScriptJson(root)) {
                val scripts = parseRegexScriptsJson(root)
                if (scripts.isNotEmpty()) {
                    return ParsedTavernAsset.RegexScripts(scripts, "sillytavern_regex_array")
                }
            }
        }
        return null
    }

    // ==================== RikkaHub ExportData Envelope ====================

    fun isRikkaExportEnvelope(obj: JsonObject): Boolean {
        val type = obj.strOrNull("type")?.trim()?.lowercase() ?: return false
        return obj["data"] is JsonObject && (type == "lorebook" || type == "mode_injection")
    }

    fun parseRikkaExportEnvelope(
        obj: JsonObject,
        conversationId: String,
        fileName: String? = null,
    ): ParsedTavernAsset? {
        val type = obj.strOrNull("type")?.trim()?.lowercase() ?: return null
        val data = obj.objOrNull("data") ?: return null
        val now = System.currentTimeMillis()
        val fallbackName = stripExtension(fileName)

        return when (type) {
            "lorebook" -> {
                val name = data.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: fallbackName.ifBlank { "RikkaHub 世界书" }
                val desc = data.strOrNull("description").orEmpty().trim()
                val rawEntries = data.arrOrNull("entries").orEmpty().mapNotNull { it as? JsonObject }
                val entries = rawEntries.mapIndexedNotNull { idx, e ->
                    val content = e.strOrNull("content").orEmpty().trim()
                    if (content.isEmpty()) return@mapIndexedNotNull null
                    val keywords = (
                        e.strListOrEmpty("keywords") +
                            e.strListOrEmpty("keys") +
                            e.strListOrEmpty("key")
                        ).distinct()
                    val aliases = (
                        e.strListOrEmpty("secondaryKeys") +
                            e.strListOrEmpty("secondary_keys") +
                            e.strListOrEmpty("keysecondary")
                        ).distinct()
                    val title = e.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: e.strOrNull("comment")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: keywords.firstOrNull()
                        ?: "条目 #${idx + 1}"
                    val constant = e.boolOrNull("constantActive")
                        ?: e.boolOrNull("constant")
                        ?: false
                    val enabled = e.boolOrNull("enabled") ?: !(e.boolOrNull("disable") ?: false)
                    val priority = e.intOrNull("priority") ?: e.intOrNull("order") ?: 50
                    val lore = NovelLoreEntry(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        category = "concept",
                        title = title,
                        content = content,
                        keywords = keywords.ifEmpty { listOf(title) },
                        aliases = aliases,
                        alwaysActive = constant,
                        priority = priority,
                        enabled = enabled,
                        validFromSeq = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                    lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore))
                }
                if (entries.isEmpty()) return null
                ParsedTavernAsset.Lorebook(
                    ParsedLorebookAsset(
                        name = name,
                        description = desc,
                        entries = entries,
                        sourceFormat = "rikkahub_lorebook",
                    ),
                )
            }

            "mode_injection" -> {
                val name = data.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: fallbackName.ifBlank { "RikkaHub 模式注入" }
                val content = data.strOrNull("content").orEmpty().trim()
                if (content.isEmpty()) return null
                val enabled = data.boolOrNull("enabled") ?: true
                val position = data.strOrNull("position")?.uppercase().orEmpty()
                val lore = NovelLoreEntry(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    category = "rule",
                    title = name,
                    content = content,
                    keywords = listOf(name),
                    alwaysActive = true,
                    priority = 90,
                    enabled = enabled,
                    validFromSeq = 0,
                    createdAt = now,
                    updatedAt = now,
                ).let { it.copy(estimatedTokens = NovelEngine.estimateLoreTokens(it)) }
                val isBottom = position.contains("BOTTOM") || position.contains("AFTER_DIALOG")
                ParsedTavernAsset.Preset(
                    ParsedPresetAsset(
                        name = name,
                        systemRules = if (enabled && !isBottom) content else "",
                        postHistoryInstructions = if (enabled && isBottom) content else "",
                        loreEntries = listOf(lore),
                        sourceFormat = "rikkahub_mode_injection",
                    ),
                )
            }

            else -> null
        }
    }

    // ==================== Worldbook / Lorebook ====================

    fun isLorebookJson(root: JsonElement): Boolean {
        if (root is JsonArray) return isTavernHelperWorldbookArray(root)
        val obj = root as? JsonObject ?: return false
        if (isRikkaExportEnvelope(obj) && obj.strOrNull("type").equals("lorebook", ignoreCase = true)) {
            return true
        }
        if (isUuWorldbookObject(obj)) return true
        val entries = obj["entries"] ?: return false
        val firstEntry = when (entries) {
            is JsonObject -> entries.values.firstOrNull { it is JsonObject } as? JsonObject
            is JsonArray -> entries.firstOrNull { it is JsonObject } as? JsonObject
            else -> null
        } ?: return false
        return firstEntry.containsKey("content") && (
            firstEntry.containsKey("key") ||
                firstEntry.containsKey("keys") ||
                firstEntry.containsKey("keywords") ||
                firstEntry["strategy"] is JsonObject
            )
    }

    private fun isTavernHelperWorldbookArray(arr: JsonArray): Boolean {
        val first = arr.firstOrNull { it is JsonObject } as? JsonObject ?: return false
        return first.containsKey("content") && first["strategy"] is JsonObject
    }

    private fun extractUuWorldInfoEntries(obj: JsonObject): JsonArray? =
        (obj["worldInfoEntries"] as? JsonArray)
            ?: (obj["world_info_entries"] as? JsonArray)

    private fun isUuWorldbookObject(obj: JsonObject): Boolean {
        val arr = extractUuWorldInfoEntries(obj) ?: return false
        val firstEntry = arr.firstOrNull { it is JsonObject && it.containsKey("content") } as? JsonObject
            ?: return false
        return firstEntry.containsKey("keys") ||
            firstEntry.containsKey("key") ||
            firstEntry.containsKey("keywords") ||
            firstEntry.containsKey("title") ||
            firstEntry.containsKey("constant")
    }

    fun parseLorebookJson(
        root: JsonElement,
        conversationId: String,
        fileName: String? = null,
    ): ParsedLorebookAsset {
        if (root is JsonObject && isRikkaExportEnvelope(root)) {
            val env = parseRikkaExportEnvelope(root, conversationId, fileName) as? ParsedTavernAsset.Lorebook
            if (env != null) return env.asset
        }

        val now = System.currentTimeMillis()
        val obj = root as? JsonObject
        val isUuWorldbook = obj != null && isUuWorldbookObject(obj)
        val title = obj?.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: obj?.strOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { if (isUuWorldbook) "UU 世界书" else "酒馆世界书" }
        val desc = obj?.strOrNull("description").orEmpty().trim()

        val rawEntries = extractRawWorldbookEntries(root)

        val isTavernHelper = rawEntries.any { it["strategy"] is JsonObject }
        val loreEntries = rawEntries.mapIndexedNotNull { idx, entry ->
            val core = extractLoreCoreFields(idx, entry, fallbackOrder = 50) ?: return@mapIndexedNotNull null
            val lore = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "concept",
                title = core.title,
                content = core.content,
                keywords = core.keywords.ifEmpty { listOf(core.title) },
                aliases = core.secondaryKeys,
                alwaysActive = core.constant,
                priority = core.priority,
                enabled = core.enabled,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore))
        }

        if (loreEntries.isEmpty()) {
            throw IllegalArgumentException("世界书中未解析到有效条目")
        }

        val sourceFormat = when {
            isUuWorldbook -> "uu_worldbook"
            isTavernHelper -> "sillytavern_worldbook_v2"
            else -> "sillytavern_lorebook"
        }

        return ParsedLorebookAsset(
            name = title,
            description = desc,
            entries = loreEntries,
            sourceFormat = sourceFormat,
        )
    }

    // ==================== Chat Completion Preset ====================

    fun isChatPresetJson(obj: JsonObject): Boolean {
        if (isRikkaExportEnvelope(obj) && obj.strOrNull("type").equals("mode_injection", ignoreCase = true)) {
            return true
        }
        val prompts = obj.arrOrNull("prompts")
        if (prompts != null && prompts.isNotEmpty()) {
            val hasPromptShape = prompts.any { el ->
                val p = el as? JsonObject ?: return@any false
                p.containsKey("identifier") || p.containsKey("content") || p.containsKey("marker")
            }
            if (hasPromptShape) return true
        }
        return !obj.strOrNull("main_prompt").isNullOrBlank() ||
            !obj.strOrNull("nsfw_prompt").isNullOrBlank() ||
            !obj.strOrNull("jailbreak_prompt").isNullOrBlank()
    }

    private data class RawPresetPrompt(
        val identifier: String,
        val name: String,
        val content: String,
        val marker: Boolean,
        val enabled: Boolean,
        val role: String,
        val injectionPosition: Int?,
        val injectionDepth: Int?,
    )

    fun parseChatPresetJson(
        obj: JsonObject,
        conversationId: String,
        fileName: String? = null,
    ): ParsedPresetAsset {
        if (isRikkaExportEnvelope(obj)) {
            val env = parseRikkaExportEnvelope(obj, conversationId, fileName) as? ParsedTavernAsset.Preset
            if (env != null) return env.asset
        }

        val now = System.currentTimeMillis()
        val presetName = obj.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { "酒馆预设" }
        val embeddedRegex = RpCardCodec.parseRegexScriptsFromElement(
            obj.objOrNull("extensions")?.get("regex_scripts"),
        )
        val temp = obj.nullableDouble("temperature")
        val topP = obj.nullableDouble("top_p")
        val maxTokens = obj.intOrNull("openai_max_tokens") ?: obj.intOrNull("max_tokens")

        val promptsArr = obj.arrOrNull("prompts")
        if (promptsArr != null && promptsArr.isNotEmpty()) {
            val parsedPrompts = promptsArr.mapIndexedNotNull { idx, el ->
                val p = el as? JsonObject ?: return@mapIndexedNotNull null
                val id = p.strOrNull("identifier")?.trim().orEmpty().ifBlank { "prompt_$idx" }
                RawPresetPrompt(
                    identifier = id,
                    name = p.strOrNull("name")?.trim().orEmpty().ifBlank { id },
                    content = p.strOrNull("content").orEmpty().trim(),
                    marker = p.boolOrNull("marker") ?: false,
                    enabled = p.boolOrNull("enabled") ?: true,
                    role = p.strOrNull("role")?.trim()?.lowercase().orEmpty().ifBlank { "system" },
                    injectionPosition = p.intOrNull("injection_position"),
                    injectionDepth = p.intOrNull("injection_depth"),
                )
            }
            val resolvedEntries = resolveOrderedPresetPrompts(
                obj = obj,
                parsedPrompts = parsedPrompts,
                deduplicateOrderIds = false,
                includeMarkersAndBlank = false,
            )

            var mainRules = ""
            var postHistory = ""
            val loreEntries = ArrayList<NovelLoreEntry>()

            resolvedEntries.forEachIndexed { idx, (prompt, effectiveEnabled) ->
                when {
                    prompt.identifier == "main" && effectiveEnabled && mainRules.isBlank() -> {
                        mainRules = prompt.content
                    }
                    prompt.identifier == "jailbreak" && effectiveEnabled && postHistory.isBlank() -> {
                        postHistory = prompt.content
                    }
                    else -> {
                        val lore = NovelLoreEntry(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            category = "rule",
                            title = prompt.name,
                            content = prompt.content,
                            keywords = listOf(prompt.name),
                            alwaysActive = true,
                            priority = (100 - idx).coerceAtLeast(10),
                            enabled = effectiveEnabled,
                            validFromSeq = 0,
                            createdAt = now,
                            updatedAt = now,
                        )
                        loreEntries.add(lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore)))
                    }
                }
            }

            if (mainRules.isBlank() && postHistory.isBlank() && loreEntries.isEmpty() && embeddedRegex.isEmpty()) {
                throw IllegalArgumentException("酒馆预设 JSON 中未解析到有效提示词或正则脚本")
            }

            return ParsedPresetAsset(
                name = presetName,
                systemRules = mainRules,
                postHistoryInstructions = postHistory,
                loreEntries = loreEntries,
                regexScripts = embeddedRegex,
                temperature = temp,
                topP = topP,
                maxTokens = maxTokens,
                sourceFormat = "sillytavern_preset_ordered",
            )
        }

        // Legacy flat preset (`main_prompt` / `nsfw_prompt` / `jailbreak_prompt`)
        val mainPrompt = obj.strOrNull("main_prompt").orEmpty().trim()
        val nsfwPrompt = obj.strOrNull("nsfw_prompt").orEmpty().trim()
        val nsfwToggle = obj.boolOrNull("nsfw_toggle") ?: false
        val jailbreakPrompt = obj.strOrNull("jailbreak_prompt").orEmpty().trim()
        val loreEntries = ArrayList<NovelLoreEntry>()

        if (nsfwPrompt.isNotBlank()) {
            val lore = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "rule",
                title = "NSFW Prompt",
                content = nsfwPrompt,
                keywords = listOf("NSFW Prompt"),
                alwaysActive = true,
                priority = 80,
                enabled = nsfwToggle,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            loreEntries.add(lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore)))
        }

        if (mainPrompt.isBlank() && jailbreakPrompt.isBlank() && loreEntries.isEmpty() && embeddedRegex.isEmpty()) {
            throw IllegalArgumentException("旧版酒馆预设 JSON 中未解析到有效提示词")
        }

        return ParsedPresetAsset(
            name = presetName,
            systemRules = mainPrompt,
            postHistoryInstructions = jailbreakPrompt,
            loreEntries = loreEntries,
            regexScripts = embeddedRegex,
            temperature = temp,
            topP = topP,
            maxTokens = maxTokens,
            sourceFormat = "sillytavern_preset_flat",
        )
    }

    // ==================== Standalone Regex Scripts ====================

    fun isRegexScriptJson(root: JsonElement): Boolean {
        val sample = when (root) {
            is JsonObject -> root
            is JsonArray -> root.firstOrNull { it is JsonObject } as? JsonObject
            else -> null
        } ?: return false
        val hasFind = sample.containsKey("findRegex") || sample.containsKey("find_regex")
        val hasShape = sample.containsKey("scriptName") ||
            sample.containsKey("script_name") ||
            sample.containsKey("replaceString") ||
            sample.containsKey("replace_string")
        return hasFind && hasShape
    }

    fun parseRegexScriptsJson(root: JsonElement): List<RpRegexScript> {
        if (root is JsonObject) {
            // 1. Direct single script object
            if (isRegexScriptJson(root)) {
                return RpCardCodec.parseRegexScriptsFromElement(root)
            }
            // 2. Preset / Character Card embedded extensions.regex_scripts
            val fromCardData = root.objOrNull("data")?.objOrNull("extensions")?.get("regex_scripts")
            val fromTopExt = root.objOrNull("extensions")?.get("regex_scripts")
            // 3. SillyTavern settings.json extension_settings.regex
            val fromSettings = root.objOrNull("extension_settings")?.get("regex")
            val combined = RpCardCodec.parseRegexScriptsFromElement(fromCardData) +
                RpCardCodec.parseRegexScriptsFromElement(fromTopExt) +
                RpCardCodec.parseRegexScriptsFromElement(fromSettings)
            return combined.distinctBy { "${it.findRegex}|${it.replaceString}" }
        }
        return RpCardCodec.parseRegexScriptsFromElement(root)
    }

    // ==================== Quick Replies ====================

    fun isQuickRepliesJson(root: JsonElement): Boolean {
        val obj = root as? JsonObject ?: return false
        val arr = obj.arrOrNull("qrList") ?: obj.arrOrNull("quickReplies") ?: return false
        val first = arr.firstOrNull { it is JsonObject } as? JsonObject ?: return false
        return first.containsKey("message") || first.containsKey("value") || first.containsKey("label")
    }

    fun parseQuickRepliesJson(root: JsonElement, fileName: String? = null): ParsedQuickRepliesAsset {
        val obj = root as? JsonObject
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> root.arrOrNull("qrList") ?: root.arrOrNull("quickReplies")
            else -> null
        } ?: throw IllegalArgumentException("未在文件中找到快速回复列表（qrList / quickReplies）")

        val actions = arr.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val message = item.strOrNull("message")?.trim()
                ?: item.strOrNull("value")?.trim()
                ?: item.strOrNull("label")?.trim()
                ?: item.strOrNull("title")?.trim()
            message?.takeIf { it.isNotEmpty() }
        }.distinct()

        if (actions.isEmpty()) {
            throw IllegalArgumentException("快速回复文件中未解析到有效条目")
        }

        val name = obj?.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { "酒馆快速回复" }
        return ParsedQuickRepliesAsset(
            name = name,
            actions = actions,
            sourceFormat = "sillytavern_quick_replies",
        )
    }

    // ==================== SillyTavern settings.json ====================

    fun isSillyTavernSettingsJson(obj: JsonObject): Boolean {
        val ext = obj.objOrNull("extension_settings")
        if (ext != null && (ext["regex"] is JsonArray || ext["note"] is JsonObject)) return true
        return obj["power_user"] is JsonObject &&
            (obj["oai_settings"] is JsonObject || obj["world_info_settings"] is JsonObject || ext != null)
    }

    fun parseSillyTavernSettingsJson(obj: JsonObject): ParsedTavernSettingsAsset {
        val ext = obj.objOrNull("extension_settings")
        val regexScripts = RpCardCodec.parseRegexScriptsFromElement(ext?.get("regex"))
        val authorNote = ext?.objOrNull("note")?.strOrNull("default").orEmpty().trim()
        return ParsedTavernSettingsAsset(
            regexScripts = regexScripts,
            authorNote = authorNote,
        )
    }

    private fun stripExtension(fileName: String?): String =
        fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()

    // ==================== V4 Rich Asset Parsers ====================

    fun parseWorldbookV4(
        rawJson: String,
        worldbookId: String = UUID.randomUUID().toString(),
        fileName: String? = null,
    ): ParsedWorldbookV4 {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            CoreAppJson.parseToJsonElement(cleaned)
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的世界书 JSON 格式：${e.message}")
        }
        return parseWorldbookV4(root, worldbookId = worldbookId, fileName = fileName)
    }

    fun parseWorldbookV4(
        root: JsonElement,
        worldbookId: String = UUID.randomUUID().toString(),
        fileName: String? = null,
    ): ParsedWorldbookV4 {
        val now = System.currentTimeMillis()
        if (root is JsonObject && isRikkaExportEnvelope(root)) {
            val type = root.strOrNull("type")?.trim()?.lowercase()
            if (type == "lorebook") {
                val data = root.objOrNull("data") ?: throw IllegalArgumentException("RikkaHub 世界书 data 为空")
                val name = data.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stripExtension(fileName).ifBlank { "RikkaHub 世界书" }
                val desc = data.strOrNull("description").orEmpty().trim()
                val rawEntries = data.arrOrNull("entries").orEmpty().mapNotNull { it as? JsonObject }
                return buildWorldbookV4FromRawEntries(
                    worldbookId = worldbookId,
                    name = name,
                    description = desc,
                    sourceFormat = "rikkahub_lorebook",
                    bookObj = data,
                    rawEntries = rawEntries,
                    now = now,
                )
            }
        }

        val obj = root as? JsonObject
        val isUuWorldbook = obj != null && isUuWorldbookObject(obj)
        val title = obj?.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: obj?.strOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { if (isUuWorldbook) "UU 世界书" else "酒馆世界书" }
        val desc = obj?.strOrNull("description").orEmpty().trim()
        val rawEntries = extractRawWorldbookEntries(root)
        val isTavernHelper = rawEntries.any { it["strategy"] is JsonObject }
        val sourceFormat = when {
            isUuWorldbook -> "uu_worldbook"
            isTavernHelper -> "sillytavern_worldbook_v2"
            else -> "sillytavern_lorebook"
        }
        return buildWorldbookV4FromRawEntries(
            worldbookId = worldbookId,
            name = title,
            description = desc,
            sourceFormat = sourceFormat,
            bookObj = obj,
            rawEntries = rawEntries,
            now = now,
        )
    }

    fun parseCharacterBookToWorldbookV4(
        charBookObj: JsonObject,
        fallbackName: String = "角色卡世界书",
        worldbookId: String = UUID.randomUUID().toString(),
    ): ParsedWorldbookV4? {
        val rawEntries = extractRawWorldbookEntries(charBookObj)
        if (rawEntries.isEmpty()) return null
        val name = charBookObj.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName
        val desc = charBookObj.strOrNull("description").orEmpty().trim()
        return buildWorldbookV4FromRawEntries(
            worldbookId = worldbookId,
            name = name,
            description = desc,
            sourceFormat = "sillytavern_character_book",
            bookObj = charBookObj,
            rawEntries = rawEntries,
            now = System.currentTimeMillis(),
        )
    }

    private fun buildWorldbookV4FromRawEntries(
        worldbookId: String,
        name: String,
        description: String,
        sourceFormat: String,
        bookObj: JsonObject?,
        rawEntries: List<JsonObject>,
        now: Long,
    ): ParsedWorldbookV4 {
        val scanDepth = (bookObj?.intOrNull("scan_depth") ?: bookObj?.intOrNull("scanDepth") ?: 4).coerceIn(1, 24)
        val tokenBudget = (bookObj?.intOrNull("token_budget") ?: bookObj?.intOrNull("tokenBudget") ?: 1600).coerceAtLeast(100)
        val recursiveScanning = bookObj?.boolOrNull("recursive_scanning") ?: bookObj?.boolOrNull("recursiveScanning") ?: true

        val entries = rawEntries.mapIndexedNotNull { idx, entry ->
            val core = extractLoreCoreFields(idx, entry, fallbackOrder = 100) ?: return@mapIndexedNotNull null
            val extObj = core.extObj
            val posObj = core.posObj
            val secondaryObj = core.secondaryObj
            val uid = entry.intOrNull("uid") ?: entry.intOrNull("id") ?: idx
            val selectiveLogic = parseSelectiveLogic(
                entry["selectiveLogic"] ?: entry["selective_logic"] ?: secondaryObj?.get("logic"),
            )
            val caseSensitive = entry.boolOrNull("case_sensitive")
                ?: entry.boolOrNull("caseSensitive")
                ?: extObj?.boolOrNull("case_sensitive")
                ?: false
            val matchWholeWords = entry.boolOrNull("match_whole_words")
                ?: entry.boolOrNull("matchWholeWords")
                ?: extObj?.boolOrNull("match_whole_words")
                ?: false
            val useRegexKey = entry.boolOrNull("use_regex")
                ?: entry.boolOrNull("useRegex")
                ?: entry.boolOrNull("useRegexKey")
                ?: false
            val probability = (entry.intOrNull("probability") ?: extObj?.intOrNull("probability") ?: 100).coerceIn(0, 100)
            val position = parseWorldbookPosition(entry["position"] ?: extObj?.get("position"))
            val depth = (entry.intOrNull("depth")
                ?: posObj?.intOrNull("depth")
                ?: extObj?.intOrNull("depth")
                ?: 4).coerceAtLeast(0)
            val outletRole = parseWorldbookRole(
                entry["role"] ?: posObj?.get("role") ?: extObj?.get("role") ?: entry["outletRole"],
            )
            val scanDepthOverride = entry.intOrNull("scanDepth")
                ?: entry.intOrNull("scan_depth")
                ?: extObj?.intOrNull("scanDepth")
            val sticky = (entry.intOrNull("sticky") ?: extObj?.intOrNull("sticky") ?: 0).coerceAtLeast(0)
            val cooldown = (entry.intOrNull("cooldown") ?: extObj?.intOrNull("cooldown") ?: 0).coerceAtLeast(0)
            val delay = (entry.intOrNull("delay") ?: extObj?.intOrNull("delay") ?: 0).coerceAtLeast(0)
            val preventRecursion = entry.boolOrNull("preventRecursion")
                ?: entry.boolOrNull("prevent_recursion")
                ?: extObj?.boolOrNull("preventRecursion")
                ?: false
            val excludeRecursion = entry.boolOrNull("excludeRecursion")
                ?: entry.boolOrNull("exclude_recursion")
                ?: extObj?.boolOrNull("excludeRecursion")
                ?: false
            val groupName = (entry.strOrNull("group") ?: entry.strOrNull("groupName") ?: extObj?.strOrNull("group")).orEmpty().trim()
            val groupWeight = (entry.intOrNull("groupWeight") ?: entry.intOrNull("group_weight") ?: extObj?.intOrNull("groupWeight") ?: 100).coerceAtLeast(1)
            val estTokens = TokenEstimator.estimateItem(core.title, core.content, overhead = 8, minTokens = 8)

            WorldbookEntryEntity(
                id = UUID.randomUUID().toString(),
                worldbookId = worldbookId,
                uid = uid,
                title = core.title,
                content = core.content,
                category = entry.strOrNull("category")?.trim()?.takeIf { it.isNotEmpty() } ?: "concept",
                keysJson = CoreAppJson.encodeToString(core.keywords),
                secondaryKeysJson = CoreAppJson.encodeToString(core.secondaryKeys),
                selectiveLogic = selectiveLogic,
                constant = core.constant,
                enabled = core.enabled,
                caseSensitive = caseSensitive,
                matchWholeWords = matchWholeWords,
                useRegexKey = useRegexKey,
                probability = probability,
                insertionOrder = core.priority,
                position = position,
                depth = depth,
                outletRole = outletRole,
                scanDepthOverride = scanDepthOverride,
                sticky = sticky,
                cooldown = cooldown,
                delay = delay,
                preventRecursion = preventRecursion,
                excludeRecursion = excludeRecursion,
                groupName = groupName,
                groupWeight = groupWeight,
                estimatedTokens = estTokens,
                createdAt = now,
                updatedAt = now,
            )
        }

        if (entries.isEmpty()) {
            throw IllegalArgumentException("世界书中未解析到有效条目")
        }

        val totalTokens = entries.sumOf { it.estimatedTokens }
        val fpInput = buildString {
            append(name)
            entries.forEach { e ->
                append('|').append(e.uid).append(':').append(e.keysJson).append(':').append(e.content)
            }
        }
        val worldbook = WorldbookEntity(
            id = worldbookId,
            name = name,
            description = description,
            sourceFormat = sourceFormat,
            globalEnabled = false,
            scanDepth = scanDepth,
            tokenBudget = tokenBudget,
            recursiveScanning = recursiveScanning,
            entryCount = entries.size,
            totalEstimatedTokens = totalTokens,
            contentFingerprint = sha256Hex(fpInput),
            createdAt = now,
            updatedAt = now,
        )
        return ParsedWorldbookV4(worldbook = worldbook, entries = entries)
    }

    fun parsePromptPresetV4(
        rawJson: String,
        presetId: String = UUID.randomUUID().toString(),
        fileName: String? = null,
    ): PromptPresetEntity {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val obj = try {
            CoreAppJson.parseToJsonElement(cleaned) as? JsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的预设 JSON 格式：${e.message}")
        } ?: throw IllegalArgumentException("预设 JSON 根节点必须为对象")
        return parsePromptPresetV4(obj, presetId = presetId, fileName = fileName)
    }

    fun parsePromptPresetV4(
        obj: JsonObject,
        presetId: String = UUID.randomUUID().toString(),
        fileName: String? = null,
    ): PromptPresetEntity {
        val now = System.currentTimeMillis()
        if (isRikkaExportEnvelope(obj) && obj.strOrNull("type").equals("mode_injection", ignoreCase = true)) {
            val data = obj.objOrNull("data") ?: throw IllegalArgumentException("RikkaHub 模式注入 data 为空")
            val name = data.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: stripExtension(fileName).ifBlank { "RikkaHub 模式注入" }
            val content = data.strOrNull("content").orEmpty().trim()
            if (content.isEmpty()) throw IllegalArgumentException("RikkaHub 模式注入内容为空")
            val enabled = data.boolOrNull("enabled") ?: true
            val posStr = data.strOrNull("position")?.uppercase().orEmpty()
            val isBottom = posStr.contains("BOTTOM") || posStr.contains("AFTER_DIALOG")
            val block = PromptPresetBlock(
                identifier = "mode_injection",
                name = name,
                role = "system",
                content = content,
                marker = false,
                enabled = enabled,
                injectionPosition = if (isBottom) 1 else 0,
                injectionDepth = 0,
            )
            return PromptPresetEntity(
                id = presetId,
                name = name,
                sourceFormat = "rikkahub_mode_injection",
                blocksJson = CoreAppJson.encodeToString(listOf(block)),
                regexScriptsJson = "[]",
                samplingPatchJson = "{}",
                isDefaultRp = false,
                createdAt = now,
                updatedAt = now,
            )
        }

        val presetName = obj.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { "酒馆预设" }
        val embeddedRegex = RpCardCodec.parseRegexScriptsFromElement(
            obj.objOrNull("extensions")?.get("regex_scripts"),
        )
        val temp = obj.nullableDouble("temperature") ?: obj.nullableDouble("temp")
        val topP = obj.nullableDouble("top_p")
        val maxTokens = obj.intOrNull("openai_max_tokens") ?: obj.intOrNull("max_tokens")
        val topK = obj.intOrNull("top_k")
        val minP = obj.nullableDouble("min_p")
        val freqPen = obj.nullableDouble("frequency_penalty")
        val presPen = obj.nullableDouble("presence_penalty")
        val repPen = obj.nullableDouble("repetition_penalty")

        val samplingJson = buildJsonObject {
            temp?.let { put("temperature", it) }
            topP?.let { put("top_p", it) }
            maxTokens?.let { put("max_tokens", it) }
            topK?.let { put("top_k", it) }
            minP?.let { put("min_p", it) }
            freqPen?.let { put("frequency_penalty", it) }
            presPen?.let { put("presence_penalty", it) }
            repPen?.let { put("repetition_penalty", it) }
        }.toString()

        val promptsArr = obj.arrOrNull("prompts")
        if (promptsArr != null && promptsArr.isNotEmpty()) {
            val parsedPrompts = promptsArr.mapIndexedNotNull { idx, el ->
                val p = el as? JsonObject ?: return@mapIndexedNotNull null
                val id = p.strOrNull("identifier")?.trim().orEmpty().ifBlank { "prompt_$idx" }
                RawPresetPrompt(
                    identifier = id,
                    name = p.strOrNull("name")?.trim().orEmpty().ifBlank { id },
                    content = p.strOrNull("content").orEmpty().trim(),
                    marker = p.boolOrNull("marker") ?: false,
                    enabled = p.boolOrNull("enabled") ?: true,
                    role = parseWorldbookRole(p["role"]),
                    injectionPosition = p.intOrNull("injection_position") ?: p.intOrNull("injectionPosition") ?: 0,
                    injectionDepth = p.intOrNull("injection_depth") ?: p.intOrNull("injectionDepth") ?: 4,
                )
            }
            val blocks = resolveOrderedPresetPrompts(
                obj = obj,
                parsedPrompts = parsedPrompts,
                deduplicateOrderIds = true,
                includeMarkersAndBlank = true,
            ).map { (prompt, effectiveEnabled) ->
                PromptPresetBlock(
                    identifier = prompt.identifier,
                    name = prompt.name,
                    role = prompt.role,
                    content = prompt.content,
                    marker = prompt.marker,
                    enabled = effectiveEnabled,
                    injectionPosition = prompt.injectionPosition ?: 0,
                    injectionDepth = prompt.injectionDepth ?: 4,
                )
            }

            if (blocks.none { !it.marker && it.content.isNotBlank() } && embeddedRegex.isEmpty()) {
                throw IllegalArgumentException("酒馆预设 JSON 中未解析到有效提示词或正则脚本")
            }

            return PromptPresetEntity(
                id = presetId,
                name = presetName,
                sourceFormat = "sillytavern_preset_ordered",
                blocksJson = CoreAppJson.encodeToString(blocks),
                regexScriptsJson = CoreAppJson.encodeToString(embeddedRegex),
                samplingPatchJson = samplingJson,
                isDefaultRp = false,
                createdAt = now,
                updatedAt = now,
            )
        }

        // Legacy flat preset (`main_prompt` / `nsfw_prompt` / `jailbreak_prompt`)
        val mainPrompt = obj.strOrNull("main_prompt").orEmpty().trim()
        val nsfwPrompt = obj.strOrNull("nsfw_prompt").orEmpty().trim()
        val nsfwToggle = obj.boolOrNull("nsfw_toggle") ?: false
        val jailbreakPrompt = obj.strOrNull("jailbreak_prompt").orEmpty().trim()
        val flatBlocks = buildList {
            if (mainPrompt.isNotBlank()) {
                add(PromptPresetBlock(identifier = "main", name = "Main Prompt", role = "system", content = mainPrompt, enabled = true, injectionPosition = 0))
            }
            if (nsfwPrompt.isNotBlank()) {
                add(PromptPresetBlock(identifier = "nsfw", name = "NSFW Prompt", role = "system", content = nsfwPrompt, enabled = nsfwToggle, injectionPosition = 0))
            }
            if (jailbreakPrompt.isNotBlank()) {
                add(PromptPresetBlock(identifier = "jailbreak", name = "Post-History", role = "system", content = jailbreakPrompt, enabled = true, injectionPosition = 1, injectionDepth = 0))
            }
        }
        if (flatBlocks.isEmpty() && embeddedRegex.isEmpty()) {
            throw IllegalArgumentException("旧版酒馆预设 JSON 中未解析到有效提示词")
        }
        return PromptPresetEntity(
            id = presetId,
            name = presetName,
            sourceFormat = "sillytavern_preset_flat",
            blocksJson = CoreAppJson.encodeToString(flatBlocks),
            regexScriptsJson = CoreAppJson.encodeToString(embeddedRegex),
            samplingPatchJson = samplingJson,
            isDefaultRp = false,
            createdAt = now,
            updatedAt = now,
        )
    }

    private data class ExtractedLoreFields(
        val content: String,
        val keywords: List<String>,
        val secondaryKeys: List<String>,
        val title: String,
        val enabled: Boolean,
        val constant: Boolean,
        val priority: Int,
        val strategyObj: JsonObject?,
        val secondaryObj: JsonObject?,
        val posObj: JsonObject?,
        val extObj: JsonObject?,
    )

    private fun extractRawWorldbookEntries(root: JsonElement): List<JsonObject> = when (root) {
        is JsonArray -> root.mapNotNull { it as? JsonObject }
        is JsonObject -> when (
            val e = root["entries"] ?: root["worldInfoEntries"] ?: root["world_info_entries"]
        ) {
            is JsonObject -> e.values.mapNotNull { it as? JsonObject }
            is JsonArray -> e.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun extractLoreCoreFields(
        idx: Int,
        entry: JsonObject,
        fallbackOrder: Int,
    ): ExtractedLoreFields? {
        val content = entry.strOrNull("content").orEmpty().trim()
        if (content.isEmpty()) return null

        val strategyObj = entry.objOrNull("strategy")
        val secondaryObj = strategyObj?.objOrNull("keys_secondary")
        val posObj = entry.objOrNull("position")
        val extObj = entry.objOrNull("extensions")

        val keywords = (
            entry.strListOrEmpty("keys") +
                entry.strListOrEmpty("key") +
                entry.strListOrEmpty("keywords") +
                strategyObj?.strListOrEmpty("keys").orEmpty()
            ).distinct()
        val secondaryKeys = (
            entry.strListOrEmpty("secondary_keys") +
                entry.strListOrEmpty("keysecondary") +
                entry.strListOrEmpty("secondaryKeys") +
                secondaryObj?.strListOrEmpty("keys").orEmpty()
            ).distinct()
        val title = (
            entry.strOrNull("comment")?.takeIf { it.isNotBlank() }
                ?: entry.strOrNull("title")?.takeIf { it.isNotBlank() }
                ?: entry.strOrNull("name")?.takeIf { it.isNotBlank() }
                ?: keywords.firstOrNull()
                ?: "条目 #${idx + 1}"
            ).trim()
        val disabled = entry.boolOrNull("disable") == true || entry.boolOrNull("disabled") == true
        val enabled = entry.boolOrNull("enabled") ?: !disabled
        val constant = entry.boolOrNull("constant")
            ?: entry.boolOrNull("constantActive")
            ?: strategyObj?.strOrNull("type")?.equals("constant", ignoreCase = true)
            ?: false
        val priority = entry.intOrNull("insertion_order")
            ?: entry.intOrNull("order")
            ?: entry.intOrNull("priority")
            ?: posObj?.intOrNull("order")
            ?: extObj?.intOrNull("order")
            ?: fallbackOrder

        return ExtractedLoreFields(
            content = content,
            keywords = keywords,
            secondaryKeys = secondaryKeys,
            title = title,
            enabled = enabled,
            constant = constant,
            priority = priority,
            strategyObj = strategyObj,
            secondaryObj = secondaryObj,
            posObj = posObj,
            extObj = extObj,
        )
    }

    private fun JsonObject.nullableDouble(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull

    private fun resolveChosenPromptOrder(obj: JsonObject): JsonArray? {
        val orderGroups = obj.arrOrNull("prompt_order").orEmpty().mapNotNull { it as? JsonObject }
        return orderGroups
            .firstOrNull { g ->
                val cid = (g["character_id"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.longOrNull
                    ?: g.strOrNull("character_id")?.toLongOrNull()
                cid == 100001L
            }
            ?.arrOrNull("order")
            ?: orderGroups
                .maxByOrNull { it.arrOrNull("order")?.size ?: 0 }
                ?.arrOrNull("order")
    }

    private fun resolveOrderedPresetPrompts(
        obj: JsonObject,
        parsedPrompts: List<RawPresetPrompt>,
        deduplicateOrderIds: Boolean,
        includeMarkersAndBlank: Boolean,
    ): List<Pair<RawPresetPrompt, Boolean>> {
        val byId = parsedPrompts.associateBy { it.identifier }
        val chosenOrderArr = resolveChosenPromptOrder(obj)
        val out = ArrayList<Pair<RawPresetPrompt, Boolean>>()
        val seenIds = LinkedHashSet<String>()

        if (chosenOrderArr != null && chosenOrderArr.isNotEmpty()) {
            chosenOrderArr.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val id = o.strOrNull("identifier")?.trim() ?: return@forEach
                val prompt = byId[id] ?: return@forEach
                val added = seenIds.add(id)
                if (deduplicateOrderIds && !added) return@forEach
                val orderEnabled = o.boolOrNull("enabled") ?: true
                val effectiveEnabled = orderEnabled && prompt.enabled
                if (!includeMarkersAndBlank && (prompt.marker || prompt.content.isBlank())) return@forEach
                out.add(prompt to effectiveEnabled)
            }
        }
        parsedPrompts.forEach { prompt ->
            if (prompt.identifier in seenIds) return@forEach
            if (!includeMarkersAndBlank && (prompt.marker || prompt.content.isBlank())) return@forEach
            out.add(prompt to prompt.enabled)
        }
        return out
    }

    private fun parseSelectiveLogic(elem: JsonElement?): Int {
        val prim = elem as? JsonPrimitive ?: return 0
        if (prim is JsonNull) return 0
        prim.contentOrNull?.trim()?.toIntOrNull()?.let { return it.coerceIn(0, 3) }
        return when (prim.contentOrNull?.trim()?.lowercase()) {
            "and_any", "0" -> 0
            "not_all", "1" -> 1
            "not_any", "2" -> 2
            "and_all", "3" -> 3
            else -> 0
        }
    }

    private fun parseWorldbookPosition(elem: JsonElement?): Int {
        if (elem is JsonObject) {
            return when (elem.strOrNull("type")?.trim()?.lowercase()) {
                "before_character_definition", "before_char" -> 0
                "after_character_definition", "after_char" -> 1
                "before_author_note", "before_an" -> 2
                "after_author_note", "after_an" -> 3
                "at_depth" -> 4
                else -> 0
            }
        }
        val prim = elem as? JsonPrimitive ?: return 0
        if (prim is JsonNull) return 0
        prim.contentOrNull?.trim()?.toIntOrNull()?.let { return it.coerceIn(0, 7) }
        return when (prim.contentOrNull?.trim()?.lowercase()) {
            "before_char", "before_character_definition", "0" -> 0
            "after_char", "after_character_definition", "1" -> 1
            "before_an", "before_author_note", "2" -> 2
            "after_an", "after_author_note", "3" -> 3
            "at_depth", "in_chat", "4" -> 4
            "before_examples", "5" -> 5
            "after_examples", "6" -> 6
            "outlet", "7" -> 7
            else -> 0
        }
    }

    private fun parseWorldbookRole(elem: JsonElement?): String {
        val prim = elem as? JsonPrimitive ?: return "system"
        if (prim is JsonNull) return "system"
        prim.contentOrNull?.trim()?.toIntOrNull()?.let { code ->
            return when (code) {
                1 -> "user"
                2 -> "assistant"
                else -> "system"
            }
        }
        return when (prim.contentOrNull?.trim()?.lowercase()) {
            "user", "1" -> "user"
            "assistant", "model", "2" -> "assistant"
            else -> "system"
        }
    }

    private fun sha256Hex(text: String): String =
        HashUtils.sha256Hex(text)
}
