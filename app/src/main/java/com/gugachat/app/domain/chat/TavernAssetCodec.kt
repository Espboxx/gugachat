package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.RpCardCodec.arrOrNull
import com.gugachat.app.domain.chat.RpCardCodec.boolOrNull
import com.gugachat.app.domain.chat.RpCardCodec.intOrNull
import com.gugachat.app.domain.chat.RpCardCodec.objOrNull
import com.gugachat.app.domain.chat.RpCardCodec.strListOrEmpty
import com.gugachat.app.domain.chat.RpCardCodec.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.UUID

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
        val root = runCatching { AppJson.parseToJsonElement(cleaned) }.getOrNull() ?: return null
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
        val title = obj?.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: stripExtension(fileName).ifBlank { "酒馆世界书" }
        val desc = obj?.strOrNull("description").orEmpty().trim()

        val rawEntries: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> when (val e = root["entries"]) {
                is JsonObject -> e.values.mapNotNull { it as? JsonObject }
                is JsonArray -> e.mapNotNull { it as? JsonObject }
                else -> emptyList()
            }
            else -> emptyList()
        }

        val isTavernHelper = rawEntries.any { it["strategy"] is JsonObject }
        val loreEntries = rawEntries.mapIndexedNotNull { idx, entry ->
            val content = entry.strOrNull("content").orEmpty().trim()
            if (content.isEmpty()) return@mapIndexedNotNull null

            val strategyObj = entry.objOrNull("strategy")
            val secondaryObj = strategyObj?.objOrNull("keys_secondary")
            val posObj = entry.objOrNull("position")
            val extObj = entry.objOrNull("extensions")

            val keywords = (
                entry.strListOrEmpty("key") +
                    entry.strListOrEmpty("keys") +
                    entry.strListOrEmpty("keywords") +
                    strategyObj?.strListOrEmpty("keys").orEmpty()
                ).distinct()
            val secondaryKeys = (
                entry.strListOrEmpty("keysecondary") +
                    entry.strListOrEmpty("secondary_keys") +
                    entry.strListOrEmpty("secondaryKeys") +
                    secondaryObj?.strListOrEmpty("keys").orEmpty()
                ).distinct()
            val entryTitle = (
                entry.strOrNull("comment")?.takeIf { it.isNotBlank() }
                    ?: entry.strOrNull("name")?.takeIf { it.isNotBlank() }
                    ?: keywords.firstOrNull()
                    ?: "条目 #${idx + 1}"
                ).trim()
            val disabled = entry.boolOrNull("disable") == true
            val enabled = entry.boolOrNull("enabled") ?: !disabled
            val constant = entry.boolOrNull("constant")
                ?: entry.boolOrNull("constantActive")
                ?: strategyObj?.strOrNull("type").equals("constant", ignoreCase = true)
            val priority = entry.intOrNull("order")
                ?: entry.intOrNull("insertion_order")
                ?: entry.intOrNull("priority")
                ?: posObj?.intOrNull("order")
                ?: extObj?.intOrNull("order")
                ?: 50

            val lore = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "concept",
                title = entryTitle,
                content = content,
                keywords = keywords.ifEmpty { listOf(entryTitle) },
                aliases = secondaryKeys,
                alwaysActive = constant,
                priority = priority,
                enabled = enabled,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            lore.copy(estimatedTokens = NovelEngine.estimateLoreTokens(lore))
        }

        if (loreEntries.isEmpty()) {
            throw IllegalArgumentException("世界书中未解析到有效条目")
        }

        return ParsedLorebookAsset(
            name = title,
            description = desc,
            entries = loreEntries,
            sourceFormat = if (isTavernHelper) "sillytavern_worldbook_v2" else "sillytavern_lorebook",
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
        val temp = (obj["temperature"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
        val topP = (obj["top_p"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
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
            val byId = parsedPrompts.associateBy { it.identifier }

            // Align with RikkaHub: prefer prompt_order with character_id == 100001, else longest list
            val orderGroups = obj.arrOrNull("prompt_order").orEmpty().mapNotNull { it as? JsonObject }
            val chosenOrderArr: JsonArray? = orderGroups
                .firstOrNull { g ->
                    val cid = (g["character_id"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.longOrNull
                        ?: g.strOrNull("character_id")?.toLongOrNull()
                    cid == 100001L
                }
                ?.arrOrNull("order")
                ?: orderGroups
                    .maxByOrNull { it.arrOrNull("order")?.size ?: 0 }
                    ?.arrOrNull("order")

            val resolvedEntries = ArrayList<Pair<RawPresetPrompt, Boolean>>()
            val seenIds = LinkedHashSet<String>()

            if (chosenOrderArr != null && chosenOrderArr.isNotEmpty()) {
                chosenOrderArr.forEach { el ->
                    val o = el as? JsonObject ?: return@forEach
                    val id = o.strOrNull("identifier")?.trim() ?: return@forEach
                    val prompt = byId[id] ?: return@forEach
                    seenIds.add(id)
                    if (prompt.marker) return@forEach
                    // Align with RikkaHub: missing enabled in prompt_order defaults to true
                    val orderEnabled = o.boolOrNull("enabled") ?: true
                    val effectiveEnabled = orderEnabled && prompt.enabled
                    if (prompt.content.isBlank()) return@forEach
                    resolvedEntries.add(prompt to effectiveEnabled)
                }
            }
            // Append prompts not listed in prompt_order so alternate/disabled entries are preserved
            parsedPrompts.forEach { prompt ->
                if (prompt.identifier in seenIds || prompt.marker || prompt.content.isBlank()) return@forEach
                resolvedEntries.add(prompt to prompt.enabled)
            }

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
}
