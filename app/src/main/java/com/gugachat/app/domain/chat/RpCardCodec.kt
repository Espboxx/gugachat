package com.gugachat.app.domain.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.util.CoreAppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.data.local.WorldbookEntryEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

@Serializable
private data class StLoreEntryJson(
    val keys: List<String> = emptyList(),
    val secondary_keys: List<String> = emptyList(),
    val content: String = "",
    val name: String? = null,
    val comment: String? = null,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val priority: Int = 50,
    val insertion_order: Int = 50,
)

@Serializable
private data class StCharacterBookJson(
    val name: String? = null,
    val entries: JsonElement? = null,
)

@Serializable
private data class StCardDataJson(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val first_mes: String = "",
    val alternate_greetings: List<String> = emptyList(),
    val mes_example: String = "",
    val creator_notes: String = "",
    val system_prompt: String = "",
    val post_history_instructions: String = "",
    val tags: List<String> = emptyList(),
    val character_book: StCharacterBookJson? = null,
    val extensions: JsonObject? = null,
)

@Serializable
private data class StCardEnvelopeJson(
    val spec: String? = null,
    val spec_version: String? = null,
    val data: StCardDataJson? = null,
)

data class ImportedRpCard(
    val rpConfig: RpConfig,
    val importedLore: List<NovelLoreEntry>,
    val specVersion: String,
)

data class ParsedCharacterCardV4(
    val card: CharacterCardEntity,
    val worldbook: ParsedWorldbookV4?,
    val rpConfig: RpConfig,
    val specVersion: String,
)

data class ExtractedZipCard(
    val cardJson: String,
    val avatarBytes: ByteArray? = null,
    val avatarExt: String = "png",
    val spriteBytes: ByteArray? = null,
    val spriteExt: String = "png",
    val expressionSprites: Map<String, Pair<ByteArray, String>> = emptyMap(),
    val backgroundBytes: ByteArray? = null,
    val backgroundExt: String = "png",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtractedZipCard) return false
        return cardJson == other.cardJson &&
            avatarExt == other.avatarExt &&
            spriteExt == other.spriteExt &&
            backgroundExt == other.backgroundExt &&
            (avatarBytes?.contentEquals(other.avatarBytes) ?: (other.avatarBytes == null)) &&
            (spriteBytes?.contentEquals(other.spriteBytes) ?: (other.spriteBytes == null)) &&
            (backgroundBytes?.contentEquals(other.backgroundBytes) ?: (other.backgroundBytes == null)) &&
            expressionSprites.keys == other.expressionSprites.keys
    }

    override fun hashCode(): Int {
        var result = cardJson.hashCode()
        result = 31 * result + (avatarBytes?.contentHashCode() ?: 0)
        result = 31 * result + avatarExt.hashCode()
        result = 31 * result + (spriteBytes?.contentHashCode() ?: 0)
        result = 31 * result + spriteExt.hashCode()
        result = 31 * result + expressionSprites.keys.hashCode()
        result = 31 * result + (backgroundBytes?.contentHashCode() ?: 0)
        result = 31 * result + backgroundExt.hashCode()
        return result
    }
}

object RpCardCodec {
    private const val MAX_ZIP_JSON_BYTES = 16 * 1024 * 1024
    private const val MAX_ZIP_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_ZIP_TOTAL_IMAGE_BYTES = 48 * 1024 * 1024

    private val DATA_URI_IMAGE_RE = Regex("^data:image/([a-zA-Z0-9+.-]+);base64,(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun parseCardJson(rawJson: String, conversationId: String = "preview"): ImportedRpCard {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            CoreAppJson.parseToJsonElement(cleaned) as? JsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的角色卡 JSON 格式：${e.message}")
        } ?: throw IllegalArgumentException("角色卡 JSON 根节点必须为对象")

        val data = root.objOrNull("data")
        val spec = root.strOrNull("spec").orEmpty()
        val isV3 = spec.equals("chara_card_v3", ignoreCase = true)
        val isV2 = spec.equals("chara_card_v2", ignoreCase = true)
        val specVersion = when {
            isV3 -> "v3"
            isV2 || data != null -> "v2"
            else -> "v1"
        }

        fun pickStr(vararg keys: String): String {
            for (k in keys) {
                data?.strOrNull(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            for (k in keys) {
                root.strOrNull(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return ""
        }

        fun pickStrList(vararg keys: String): List<String> {
            for (k in keys) {
                val fromData = data?.strListOrEmpty(k).orEmpty()
                if (fromData.isNotEmpty()) return fromData
            }
            for (k in keys) {
                val fromRoot = root.strListOrEmpty(k)
                if (fromRoot.isNotEmpty()) return fromRoot
            }
            return emptyList()
        }

        val charName = pickStr("name", "char_name").ifEmpty { "角色卡" }
        val desc = pickStr("description", "char_persona")
        val personality = pickStr("personality")
        val scenario = pickStr("scenario", "world_scenario")
        val firstMes = pickStr("first_mes", "char_greeting")
        val altGreetings = pickStrList("alternate_greetings")
        val sysPrompt = pickStr("system_prompt")
        val postHistory = pickStr("post_history_instructions")
        val notes = pickStr("creator_notes")
        val examples = pickStr("mes_example", "example_dialogue")
        val tags = pickStrList("tags")

        val worldview = buildList {
            if (scenario.isNotBlank()) add(scenario)
            if (tags.isNotEmpty() && scenario.isBlank()) add("标签：${tags.joinToString("、")}")
        }.joinToString("\n\n")

        val castChar = RpCharacter(
            id = UUID.randomUUID().toString(),
            name = charName,
            role = "protagonist",
            appearance = desc.take(600),
            personality = personality,
            background = desc,
            relationship = "主角搭档",
            affinity = 20,
            mood = "初遇",
            inScene = true,
        )

        val dataExt = data?.objOrNull("extensions")
        val rootExt = root.objOrNull("extensions")
        val regexScripts = (
            parseRegexScriptsFromElement(dataExt?.get("regex_scripts")) +
                parseRegexScriptsFromElement(rootExt?.get("regex_scripts"))
            ).distinctBy { "${it.id}|${it.findRegex}|${it.replaceString}" }

        val depthPromptObj = parseDepthPromptObject(dataExt) ?: parseDepthPromptObject(rootExt)
        val depthPromptText = depthPromptObj?.strOrNull("prompt").orEmpty().trim()
        val depthPromptDepth = depthPromptObj?.intOrNull("depth")?.coerceIn(0, 12)
            ?: if (depthPromptText.isNotEmpty()) 4 else 1

        val rp = RpConfig(
            title = charName,
            worldview = worldview,
            systemRules = sysPrompt,
            narratorStyle = notes,
            authorNote = depthPromptText,
            authorNoteDepth = depthPromptDepth,
            cast = listOf(castChar),
            state = RpGameState(scene = scenario.take(40).ifBlank { "初次相遇" }),
            greeting = firstMes,
            cardSource = "sillytavern_$specVersion",
            playStyle = RpPlayStyle.tavern,
            alternateGreetings = altGreetings,
            mesExample = examples,
            postHistoryInstructions = postHistory,
            regexScripts = regexScripts,
        )

        val charBookObj = data?.objOrNull("character_book")
            ?: root.objOrNull("character_book")
            ?: root.objOrNull("character_embdings")
        val now = System.currentTimeMillis()
        val loreList = parseCharacterBookEntries(
            entriesElem = charBookObj?.get("entries"),
            conversationId = conversationId,
            now = now,
        )

        return ImportedRpCard(rpConfig = rp, importedLore = loreList, specVersion = specVersion)
    }

    /**
     * Parses SillyTavern CCv1/CCv2/CCv3 JSON directly into a v0.4 [CharacterCardEntity] + optional
     * [ParsedWorldbookV4] alongside legacy [RpConfig] projection.
     */
    fun parseCardToV4(
        rawJson: String,
        cardId: String = UUID.randomUUID().toString(),
        worldbookId: String = UUID.randomUUID().toString(),
        avatarPath: String? = null,
        spritePath: String? = null,
        expressionSprites: Map<String, String> = emptyMap(),
    ): ParsedCharacterCardV4 {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            CoreAppJson.parseToJsonElement(cleaned) as? JsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的角色卡 JSON 格式：${e.message}")
        } ?: throw IllegalArgumentException("角色卡 JSON 根节点必须为对象")

        val imported = parseCardJson(cleaned, conversationId = "preview")
        val data = root.objOrNull("data")
        fun pickStr(vararg keys: String): String {
            for (k in keys) {
                data?.strOrNull(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            for (k in keys) {
                root.strOrNull(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            return ""
        }
        fun pickStrList(vararg keys: String): List<String> {
            for (k in keys) {
                val fromData = data?.strListOrEmpty(k).orEmpty()
                if (fromData.isNotEmpty()) return fromData
            }
            for (k in keys) {
                val fromRoot = root.strListOrEmpty(k)
                if (fromRoot.isNotEmpty()) return fromRoot
            }
            return emptyList()
        }

        val charName = pickStr("name", "char_name").ifEmpty { imported.rpConfig.title }
        val creator = pickStr("creator")
        val charVersion = pickStr("character_version")
        val desc = pickStr("description", "char_persona")
        val personality = pickStr("personality")
        val scenario = pickStr("scenario", "world_scenario")
        val firstMes = pickStr("first_mes", "char_greeting")
        val groupGreetings = pickStrList("group_only_greetings")
        val tags = pickStrList("tags")

        val dataExt = data?.objOrNull("extensions")
        val rootExt = root.objOrNull("extensions")
        val depthPromptObj = parseDepthPromptObject(dataExt) ?: parseDepthPromptObject(rootExt)
        val depthPromptRole = depthPromptObj?.strOrNull("role")?.trim()?.lowercase()
            ?.takeIf { it in setOf("system", "user", "assistant") }
            ?: "system"

        val charBookObj = data?.objOrNull("character_book")
            ?: root.objOrNull("character_book")
            ?: root.objOrNull("character_embdings")
        val parsedBook = charBookObj?.let { bookObj ->
            TavernAssetCodec.parseCharacterBookToWorldbookV4(
                charBookObj = bookObj,
                fallbackName = bookObj.strOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: "$charName 世界书",
                worldbookId = worldbookId,
            )
        }

        val leadChar = imported.rpConfig.cast.firstOrNull { !it.isPlayer } ?: imported.rpConfig.cast.firstOrNull()
        val updatedCast = imported.rpConfig.cast.map { ch ->
            if (leadChar != null && ch.id == leadChar.id) {
                ch.copy(
                    avatarPath = avatarPath ?: ch.avatarPath,
                    spritePath = spritePath ?: ch.spritePath,
                    expressionSprites = if (expressionSprites.isNotEmpty()) expressionSprites else ch.expressionSprites,
                )
            } else {
                ch
            }
        }
        val now = System.currentTimeMillis()
        val fingerprint = LegacyAssetHarvester.computeCardFingerprint(
            title = charName,
            greeting = firstMes,
            leadDesc = desc,
        )
        val cardEntity = CharacterCardEntity(
            id = cardId,
            name = charName,
            creator = creator,
            characterVersion = charVersion,
            avatarPath = avatarPath,
            spritePath = spritePath,
            expressionSpritesJson = CoreAppJson.encodeToString(expressionSprites),
            tagsJson = CoreAppJson.encodeToString(tags),
            playStyle = imported.rpConfig.playStyle.name,
            description = desc,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            alternateGreetingsJson = CoreAppJson.encodeToString(imported.rpConfig.alternateGreetings),
            groupOnlyGreetingsJson = CoreAppJson.encodeToString(groupGreetings),
            mesExample = imported.rpConfig.mesExample,
            systemPrompt = imported.rpConfig.systemRules,
            postHistoryInstructions = imported.rpConfig.postHistoryInstructions,
            creatorNotes = imported.rpConfig.narratorStyle,
            depthPromptText = imported.rpConfig.authorNote,
            depthPromptDepth = imported.rpConfig.authorNoteDepth,
            depthPromptRole = depthPromptRole,
            castJson = CoreAppJson.encodeToString(updatedCast),
            regexScriptsJson = CoreAppJson.encodeToString(imported.rpConfig.regexScripts),
            visualThemeJson = CoreAppJson.encodeToString(imported.rpConfig.visualTheme),
            primaryWorldbookId = parsedBook?.worldbook?.id,
            sourceSpec = imported.specVersion,
            contentFingerprint = fingerprint,
            favorite = false,
            createdAt = now,
            updatedAt = now,
        )
        return ParsedCharacterCardV4(
            card = cardEntity,
            worldbook = parsedBook,
            rpConfig = imported.rpConfig.copy(cast = updatedCast),
            specVersion = imported.specVersion,
        )
    }

    /**
     * Extracts SillyTavern `.charx` / `.byaf` ZIP card archive (`card.json` + optional CCv3
     * `data.assets` icon/sprite/emotion/background images).
     */
    fun extractCardFromZipBytes(zipBytes: ByteArray): ExtractedZipCard? {
        if (!com.gugachat.app.core.util.StreamZipUtils.isZipBytes(zipBytes)) return null
        var exactCardJson: String? = null
        var fallbackCardJson: String? = null
        val imageEntries = LinkedHashMap<String, Pair<ByteArray, String>>()
        var totalImgBytes = 0

        runCatching {
            com.gugachat.app.core.util.StreamZipUtils.forEachZipEntry(zipBytes) { norm, zis ->
                val lowerName = norm.substringAfterLast('/').lowercase()
                when {
                    lowerName == "card.json" && exactCardJson == null -> {
                        val text = com.gugachat.app.core.util.StreamZipUtils.run {
                            zis.readBoundedBytesOrNull(MAX_ZIP_JSON_BYTES.toLong())
                        }?.toString(Charsets.UTF_8)
                            ?.removePrefix("\uFEFF")
                            ?.trim()
                        if (!text.isNullOrEmpty() && text.startsWith("{")) {
                            exactCardJson = text
                        }
                    }
                    lowerName.endsWith(".json") && exactCardJson == null && fallbackCardJson == null -> {
                        val text = com.gugachat.app.core.util.StreamZipUtils.run {
                            zis.readBoundedBytesOrNull(MAX_ZIP_JSON_BYTES.toLong())
                        }?.toString(Charsets.UTF_8)
                            ?.removePrefix("\uFEFF")
                            ?.trim()
                        if (!text.isNullOrEmpty() && text.startsWith("{") &&
                            (text.contains("\"spec\"") || text.contains("\"character_book\"") || text.contains("\"first_mes\""))
                        ) {
                            fallbackCardJson = text
                        }
                    }
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
                        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> {
                        val ext = lowerName.substringAfterLast('.').let { if (it == "jpeg") "jpg" else it }
                        if (totalImgBytes < MAX_ZIP_TOTAL_IMAGE_BYTES) {
                            com.gugachat.app.core.util.StreamZipUtils.run {
                                zis.readBoundedBytesOrNull(MAX_ZIP_IMAGE_BYTES.toLong())
                            }?.takeIf { it.isNotEmpty() }?.let { bytes ->
                                if (totalImgBytes + bytes.size <= MAX_ZIP_TOTAL_IMAGE_BYTES) {
                                    imageEntries[norm] = bytes to ext
                                    totalImgBytes += bytes.size
                                }
                            }
                        }
                    }
                }
            }
        }

        val jsonText = exactCardJson ?: fallbackCardJson ?: return null

        val imageByLowerPath = HashMap<String, Pair<ByteArray, String>>(imageEntries.size * 2)
        for ((p, payload) in imageEntries) {
            imageByLowerPath[p.lowercase()] = payload
        }

        fun resolveZipAsset(rawUri: String): Pair<ByteArray, String>? {
            val uri = rawUri.trim()
            if (uri.isEmpty()) return null
            DATA_URI_IMAGE_RE.matchEntire(uri)?.let { m ->
                val ext = m.groupValues[1].lowercase().let {
                    when {
                        it.contains("png") -> "png"
                        it.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                }
                val decoded = runCatching { decodeBase64Lenient(m.groupValues[2]) }.getOrNull()
                if (decoded != null && decoded.isNotEmpty()) return decoded to ext
            }
            val cleaned = uri
                .removePrefix("embeded://")
                .removePrefix("embedded://")
                .removePrefix("./")
                .replace('\\', '/')
                .trimStart('/')
                .lowercase()
            if (cleaned.isEmpty()) return null
            imageByLowerPath[cleaned]?.let { return it }
            imageByLowerPath.entries.firstOrNull { (k, _) ->
                k.endsWith("/$cleaned") || k == cleaned
            }?.value?.let { return it }
            val baseName = cleaned.substringAfterLast('/')
            return imageByLowerPath.entries.firstOrNull { (k, _) ->
                k.substringAfterLast('/') == baseName
            }?.value
        }

        var avatarPayload: Pair<ByteArray, String>? = null
        var spritePayload: Pair<ByteArray, String>? = null
        val expressionMap = LinkedHashMap<String, Pair<ByteArray, String>>()
        var backgroundPayload: Pair<ByteArray, String>? = null

        // 1. Parse CCv3 `data.assets` manifest if present
        val rootObj = runCatching { CoreAppJson.parseToJsonElement(jsonText) as? JsonObject }.getOrNull()
        val assetsArr = rootObj?.objOrNull("data")?.arrOrNull("assets") ?: rootObj?.arrOrNull("assets")
        assetsArr?.forEach { el ->
            val assetObj = el as? JsonObject ?: return@forEach
            val type = assetObj.strOrNull("type")?.trim()?.lowercase().orEmpty()
            val name = assetObj.strOrNull("name")?.trim().orEmpty()
            val uri = assetObj.strOrNull("uri")?.trim().orEmpty()
            val resolved = resolveZipAsset(uri) ?: return@forEach
            val explicitExt = assetObj.strOrNull("ext")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val finalPair = if (explicitExt != null && resolved.second.isEmpty()) resolved.first to explicitExt else resolved

            when (type) {
                "icon" -> {
                    val lowerName = name.lowercase()
                    if (lowerName == "sprite" || lowerName == "x_sprite") {
                        if (spritePayload == null) spritePayload = finalPair
                    } else if (avatarPayload == null || lowerName == "main") {
                        avatarPayload = finalPair
                    }
                }
                "sprite", "x_sprite" -> {
                    if (spritePayload == null) spritePayload = finalPair
                }
                "emotion" -> {
                    val stem = uri.substringAfterLast('/').substringBeforeLast('.')
                    val key = name.ifBlank { stem }.trim().lowercase()
                    if (key.isNotEmpty() && key !in expressionMap) {
                        expressionMap[key] = finalPair
                    }
                }
                "background" -> {
                    if (backgroundPayload == null) backgroundPayload = finalPair
                }
            }
        }

        // 2. Directory-convention fallback for archives without explicit CCv3 `data.assets`
        var fallbackIcon: Pair<ByteArray, String>? = null
        var fallbackAny: Pair<ByteArray, String>? = null
        for ((norm, payload) in imageEntries) {
            val lowerPath = norm.lowercase()
            val lowerName = lowerPath.substringAfterLast('/')
            val stem = lowerName.substringBeforeLast('.')
            when {
                lowerPath.contains("assets/emotion/") || lowerPath.contains("assets/emotions/") || lowerPath.startsWith("emotions/") -> {
                    if (stem.isNotEmpty() && stem !in expressionMap) {
                        expressionMap[stem] = payload
                    }
                }
                lowerPath.contains("assets/background/") || lowerPath.contains("assets/bg/") ||
                    lowerName.startsWith("background.") || lowerName.startsWith("bg.") -> {
                    if (backgroundPayload == null) backgroundPayload = payload
                }
                lowerPath.contains("assets/sprite/") || lowerName.startsWith("sprite.") -> {
                    if (spritePayload == null) spritePayload = payload
                }
                lowerPath.contains("assets/icon/") || lowerName.startsWith("avatar.") || lowerName.startsWith("main.") -> {
                    if (fallbackIcon == null) fallbackIcon = payload
                }
                else -> {
                    if (fallbackAny == null) fallbackAny = payload
                }
            }
        }

        val chosenAvatar = avatarPayload ?: fallbackIcon ?: fallbackAny
        return ExtractedZipCard(
            cardJson = jsonText,
            avatarBytes = chosenAvatar?.first,
            avatarExt = chosenAvatar?.second ?: "png",
            spriteBytes = spritePayload?.first,
            spriteExt = spritePayload?.second ?: "png",
            expressionSprites = expressionMap,
            backgroundBytes = backgroundPayload?.first,
            backgroundExt = backgroundPayload?.second ?: "png",
        )
    }

    private fun parseDepthPromptObject(extensions: JsonObject?): JsonObject? {
        if (extensions == null) return null
        val elem = extensions["depth_prompt"] ?: return null
        if (elem is JsonObject) return elem
        val raw = (elem as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.trim()
            ?: return null
        if (!raw.startsWith("{")) return null
        return runCatching { CoreAppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    }

    internal fun parseCharacterBookEntries(
        entriesElem: JsonElement?,
        conversationId: String,
        now: Long,
    ): List<NovelLoreEntry> {
        if (entriesElem == null || entriesElem is JsonNull) return emptyList()
        val rawObjects: List<JsonObject> = when (entriesElem) {
            is JsonArray -> entriesElem.mapNotNull { it as? JsonObject }
            is JsonObject -> entriesElem.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        return rawObjects.mapIndexedNotNull { idx, obj ->
            val content = obj.strOrNull("content").orEmpty().trim()
            if (content.isEmpty()) return@mapIndexedNotNull null

            val keys = (obj.strListOrEmpty("keys") + obj.strListOrEmpty("key")).distinct()
            val secondaryKeys = (obj.strListOrEmpty("secondary_keys") + obj.strListOrEmpty("keysecondary")).distinct()
            val ext = obj.objOrNull("extensions")
            val title = (
                obj.strOrNull("name")?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("comment")?.takeIf { it.isNotBlank() }
                    ?: keys.firstOrNull()
                    ?: "Lore #$idx"
                ).trim()
            val disabled = obj.boolOrNull("disable") == true
            val enabled = obj.boolOrNull("enabled") ?: !disabled
            val constant = obj.boolOrNull("constant") == true
            val priority = obj.intOrNull("priority")
                ?: obj.intOrNull("insertion_order")
                ?: obj.intOrNull("order")
                ?: ext?.intOrNull("order")
                ?: 50

            val entry = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "concept",
                title = title,
                content = content,
                keywords = keys.ifEmpty { listOf(title) },
                aliases = secondaryKeys,
                alwaysActive = constant,
                priority = priority,
                enabled = enabled,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            entry.copy(estimatedTokens = NovelEngine.estimateLoreTokens(entry))
        }
    }

    /**
     * Parses SillyTavern regex scripts from a JSON array or single JSON object.
     * Supports both official ST camelCase (`scriptName`/`findRegex`/`placement`) and
     * Tavern Helper snake_case (`script_name`/`find_regex`/`source`/`destination`).
     */
    fun parseRegexScriptsFromElement(elem: JsonElement?): List<RpRegexScript> {
        val objects: List<JsonObject> = when (elem) {
            is JsonArray -> elem.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(elem)
            else -> emptyList()
        }
        if (objects.isEmpty()) return emptyList()

        return objects.mapIndexedNotNull { idx, obj ->
            val rawFind = obj.strOrNull("findRegex") ?: obj.strOrNull("find_regex") ?: ""
            if (rawFind.isBlank()) return@mapIndexedNotNull null

            val explicitFlags = obj.strOrNull("flags")
            val (pattern, parsedFlags) = RpRegexEngine.parseRegexLiteral(
                raw = rawFind,
                fallbackFlags = explicitFlags ?: "gms",
            )
            val name = (
                obj.strOrNull("scriptName")?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("script_name")?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("name")?.takeIf { it.isNotBlank() }
                    ?: "Regex #${idx + 1}"
                ).trim()
            val id = obj.strOrNull("id")?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val disabled = obj.boolOrNull("disabled") == true
            val enabled = obj.boolOrNull("enabled") ?: !disabled

            val destinationObj = obj.objOrNull("destination")
            val promptOnly: Boolean
            val markdownOnly: Boolean
            if (destinationObj != null) {
                val display = destinationObj.boolOrNull("display") ?: true
                val prompt = destinationObj.boolOrNull("prompt") ?: true
                markdownOnly = display && !prompt
                promptOnly = prompt && !display
            } else {
                promptOnly = obj.boolOrNull("promptOnly") ?: false
                markdownOnly = obj.boolOrNull("markdownOnly") ?: !promptOnly
            }

            val placement = parseRegexPlacement(obj["placement"], obj.objOrNull("source"))
            val trimStrings = parseTrimStrings(obj["trimStrings"] ?: obj["trim_strings"])
            val replaceString = obj.strOrNull("replaceString") ?: obj.strOrNull("replace_string") ?: ""

            RpRegexScript(
                id = id,
                name = name,
                enabled = enabled,
                findRegex = pattern,
                replaceString = replaceString,
                placement = placement,
                markdownOnly = markdownOnly,
                promptOnly = promptOnly,
                flags = parsedFlags,
                trimStrings = trimStrings,
            )
        }
    }

    private fun parseTrimStrings(elem: JsonElement?): List<String> = when (elem) {
        is JsonArray -> elem.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.contentOrNull }
            .filter { it.isNotEmpty() }
        is JsonPrimitive -> elem.takeUnless { it is JsonNull }?.contentOrNull
            ?.split('\n')
            ?.map { it.trimEnd('\r') }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        else -> emptyList()
    }

    private fun parseRegexPlacement(elem: JsonElement?, sourceObj: JsonObject? = null): String {
        if (sourceObj != null) {
            val hasUser = sourceObj.boolOrNull("user_input") == true
            val hasAi = sourceObj.boolOrNull("ai_output") == true
            return when {
                hasUser && hasAi -> "both"
                hasUser -> "user_input"
                hasAi -> "ai_output"
                else -> "ai_output"
            }
        }
        if (elem is JsonPrimitive && elem !is JsonNull) {
            val s = elem.contentOrNull?.trim()?.lowercase()
            if (s == "user_input" || s == "ai_output" || s == "both") return s
            return when (elem.intOrNull ?: s?.toIntOrNull()) {
                1 -> "user_input"
                2 -> "ai_output"
                else -> "ai_output"
            }
        }
        val arr = elem as? JsonArray ?: return "ai_output"
        val codes = arr.mapNotNull { p ->
            val prim = p as? JsonPrimitive ?: return@mapNotNull null
            if (prim is JsonNull) null else prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
        }.toSet()
        val hasUser = 1 in codes
        val hasAi = 2 in codes
        return when {
            hasUser && hasAi -> "both"
            hasUser -> "user_input"
            hasAi -> "ai_output"
            else -> "ai_output"
        }
    }

    private fun buildRegexScriptsExtensions(scripts: List<RpRegexScript>): JsonObject? {
        if (scripts.isEmpty()) return null
        return buildJsonObject {
            put(
                "regex_scripts",
                buildJsonArray {
                    scripts.forEach { s ->
                        val placementCodes = when (s.placement.trim().lowercase()) {
                            "user_input" -> listOf(1)
                            "both" -> listOf(1, 2)
                            else -> listOf(2)
                        }
                        add(
                            buildJsonObject {
                                put("id", s.id)
                                put("scriptName", s.name)
                                put("findRegex", RpRegexEngine.formatRegexLiteral(s.findRegex, s.flags))
                                put("replaceString", s.replaceString)
                                put(
                                    "trimStrings",
                                    buildJsonArray { s.trimStrings.forEach { add(JsonPrimitive(it)) } },
                                )
                                put(
                                    "placement",
                                    buildJsonArray { placementCodes.forEach { add(JsonPrimitive(it)) } },
                                )
                                put("disabled", !s.enabled)
                                put("markdownOnly", s.markdownOnly)
                                put("promptOnly", s.promptOnly)
                            },
                        )
                    }
                },
            )
        }
    }

    fun exportToV3Json(rp: RpConfig, lore: List<NovelLoreEntry> = emptyList()): String {
        val mainChar = rp.cast.firstOrNull { !it.isPlayer } ?: rp.cast.firstOrNull()
        val charBook = if (lore.isNotEmpty()) {
            val serializedEntries = lore.map { l ->
                StLoreEntryJson(
                    keys = l.keywords.ifEmpty { listOf(l.title) },
                    secondary_keys = l.aliases,
                    content = l.content,
                    name = l.title,
                    enabled = l.enabled,
                    constant = l.alwaysActive,
                    priority = l.priority,
                    insertion_order = l.priority,
                )
            }
            StCharacterBookJson(
                name = rp.title,
                entries = CoreAppJson.encodeToJsonElement(serializedEntries),
            )
        } else {
            null
        }
        val cardData = StCardDataJson(
            name = mainChar?.name ?: rp.title,
            description = buildString {
                if (!mainChar?.background.isNullOrBlank()) append(mainChar!!.background)
                else if (!mainChar?.appearance.isNullOrBlank()) append(mainChar!!.appearance)
            },
            personality = mainChar?.personality.orEmpty(),
            scenario = rp.worldview,
            first_mes = rp.greeting,
            alternate_greetings = rp.alternateGreetings,
            mes_example = rp.mesExample,
            creator_notes = rp.narratorStyle,
            system_prompt = rp.systemRules,
            post_history_instructions = rp.postHistoryInstructions,
            character_book = charBook,
            extensions = buildRegexScriptsExtensions(rp.regexScripts),
        )
        val env = StCardEnvelopeJson(
            spec = "chara_card_v3",
            spec_version = "3.0",
            data = cardData,
        )
        return CoreAppJson.encodeToString(env)
    }

    fun createMinimalCardPngBytes(
        width: Int = 64,
        height: Int = 64,
        r: Int = 46,
        g: Int = 58,
        b: Int = 92,
    ): ByteArray {
        val w = width.coerceIn(1, 512)
        val h = height.coerceIn(1, 512)
        val rb = r.coerceIn(0, 255).toByte()
        val gb = g.coerceIn(0, 255).toByte()
        val bb = b.coerceIn(0, 255).toByte()

        val rawScanlines = ByteArray(h * (1 + w * 3))
        var offset = 0
        for (y in 0 until h) {
            rawScanlines[offset++] = 0 // Filter type 0 (None)
            for (x in 0 until w) {
                rawScanlines[offset++] = rb
                rawScanlines[offset++] = gb
                rawScanlines[offset++] = bb
            }
        }

        val deflater = Deflater()
        deflater.setInput(rawScanlines)
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n <= 0) break
            compressed.write(buf, 0, n)
        }
        deflater.end()

        val ihdr = ByteBuffer.allocate(13)
            .putInt(w)
            .putInt(h)
            .put(8) // bit depth
            .put(2) // color type: Truecolor RGB
            .put(0) // compression method
            .put(0) // filter method
            .put(0) // interlace method
            .array()

        val out = ByteArrayOutputStream()
        out.write(PNG_SIG)
        writeChunk(out, "IHDR", ihdr)
        writeChunk(out, "IDAT", compressed.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /**
     * Pure-JVM lenient Base64 decoder supporting MIME whitespace/newlines, URL-safe `-`/`_` alphabets,
     * and missing `=` padding without touching `android.util.Base64`.
     */
    internal fun decodeBase64Lenient(raw: String): ByteArray {
        val compact = raw.replace(Regex("\\s+"), "")
        val mod = compact.length % 4
        val padded = when (mod) {
            2 -> "$compact=="
            3 -> "$compact="
            else -> compact
        }
        return if ('-' in padded || '_' in padded) {
            Base64.getUrlDecoder().decode(padded)
        } else {
            Base64.getMimeDecoder().decode(padded)
        }
    }

    /**
     * Transcodes JPG/WebP/PNG bytes to bounded PNG bytes via Android [BitmapFactory]/[Bitmap.compress].
     * Returns raw PNG bytes directly if already PNG; returns null on JVM when Android graphics stubs return null.
     */
    fun transcodeToPngBytes(rawBytes: ByteArray, maxSide: Int = 1024): ByteArray? {
        if (rawBytes.isEmpty()) return null
        if (isPngBytes(rawBytes)) return rawBytes
        return runCatching {
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@runCatching null
            val bitmap = if (decoded.width > maxSide || decoded.height > maxSide) {
                val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
                val w = (decoded.width * scale).toInt().coerceAtLeast(1)
                val h = (decoded.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, w, h, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            try {
                val out = ByteArrayOutputStream()
                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (ok) out.toByteArray().takeIf { isPngBytes(it) } else null
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    /**
     * Resolves the first valid PNG base image from [candidateImages], transcoding non-PNG (JPG/WebP)
     * candidates via [imageTranscoder] before falling back to [createMinimalCardPngBytes].
     */
    fun resolveCardBasePngBytes(
        candidateImages: List<ByteArray>,
        imageTranscoder: (ByteArray) -> ByteArray? = { transcodeToPngBytes(it) },
    ): ByteArray {
        for (raw in candidateImages) {
            if (raw.isEmpty()) continue
            if (isPngBytes(raw)) return raw
            val transcoded = runCatching { imageTranscoder(raw) }.getOrNull()
            if (transcoded != null && isPngBytes(transcoded)) {
                return transcoded
            }
        }
        return createMinimalCardPngBytes()
    }

    fun exportCardToPngBytes(
        rp: RpConfig,
        lore: List<NovelLoreEntry> = emptyList(),
        candidateImages: List<ByteArray> = emptyList(),
        imageTranscoder: (ByteArray) -> ByteArray? = { transcodeToPngBytes(it) },
    ): ByteArray {
        val json = exportToV3Json(rp, lore)
        val basePng = resolveCardBasePngBytes(candidateImages, imageTranscoder)
        return embedCardJsonInPng(basePng, json)
    }

    fun exportCardToPngBytesFromPaths(
        rp: RpConfig,
        lore: List<NovelLoreEntry> = emptyList(),
        filesDirPath: String? = null,
        extraPaths: List<String?> = emptyList(),
        assetReader: ((String) -> ByteArray?)? = null,
        imageTranscoder: (ByteArray) -> ByteArray? = { transcodeToPngBytes(it) },
    ): ByteArray {
        val leadChar = rp.cast.firstOrNull { !it.isPlayer } ?: rp.cast.firstOrNull()
        val orderedPaths = buildList {
            extraPaths.forEach { if (!it.isNullOrBlank()) add(it) }
            leadChar?.avatarPath?.takeIf { it.isNotBlank() }?.let { add(it) }
            leadChar?.spritePath?.takeIf { it.isNotBlank() }?.let { add(it) }
            leadChar?.expressionSprites?.values?.forEach { if (it.isNotBlank()) add(it) }
            rp.state.sceneImagePath?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()

        val candidateBytes = orderedPaths.mapNotNull { rawPath ->
            val assetRel = LocalMediaPathResolver.extractAssetPath(rawPath)
            if (assetRel != null) {
                return@mapNotNull runCatching {
                    assetReader?.invoke(assetRel)?.takeIf { it.isNotEmpty() && it.size.toLong() <= 20L * 1024L * 1024L }
                }.getOrNull()
            }
            val file = if (!filesDirPath.isNullOrBlank()) {
                LocalMediaPathResolver.resolveFile(filesDirPath, rawPath)
            } else {
                File(rawPath)
            }
            runCatching { file?.takeIf { it.isFile && it.length() in 1L..(20L * 1024L * 1024L) }?.readBytes() }.getOrNull()
        }
        return exportCardToPngBytes(
            rp = rp,
            lore = lore,
            candidateImages = candidateBytes,
            imageTranscoder = imageTranscoder,
        )
    }

    fun isPngBytes(bytes: ByteArray): Boolean =
        bytes.size >= PNG_SIG.size && PNG_SIG.indices.all { bytes[it] == PNG_SIG[it] }

    fun extractCardJsonFromPng(pngBytes: ByteArray): String? {
        if (!isPngBytes(pngBytes)) return null
        val chunks = LinkedHashMap<String, String>()
        var pos = PNG_SIG.size
        while (pos + 12 <= pngBytes.size) {
            val len = ByteBuffer.wrap(pngBytes, pos, 4).int
            if (len < 0 || pos + 12 + len > pngBytes.size) break
            val type = String(pngBytes, pos + 4, 4, StandardCharsets.US_ASCII)
            val dataStart = pos + 8
            val dataEnd = dataStart + len

            when (type) {
                "tEXt" -> parseTextChunk(pngBytes, dataStart, dataEnd)?.let { (k, v) ->
                    chunks[k.lowercase()] = v
                }
                "zTXt" -> parseZtxtChunk(pngBytes, dataStart, dataEnd)?.let { (k, v) ->
                    chunks[k.lowercase()] = v
                }
                "iTXt" -> parseItxtChunk(pngBytes, dataStart, dataEnd)?.let { (k, v) ->
                    chunks[k.lowercase()] = v
                }
            }
            if (type == "IEND") break
            pos = dataEnd + 4
        }

        val encoded = chunks["ccv3"] ?: chunks["chara"] ?: return null
        return try {
            val decoded = decodeBase64Lenient(encoded)
            String(decoded, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            if (encoded.trim().startsWith("{")) encoded else null
        }
    }

    fun embedCardJsonInPng(pngBytes: ByteArray, jsonText: String): ByteArray {
        require(isPngBytes(pngBytes)) { "Invalid PNG image bytes" }
        val encoded = Base64.getEncoder().encodeToString(jsonText.toByteArray(StandardCharsets.UTF_8))
        val out = ByteArrayOutputStream()
        out.write(PNG_SIG)

        var pos = PNG_SIG.size
        var wroteCardChunks = false
        while (pos + 12 <= pngBytes.size) {
            val len = ByteBuffer.wrap(pngBytes, pos, 4).int
            if (len < 0 || pos + 12 + len > pngBytes.size) break
            val type = String(pngBytes, pos + 4, 4, StandardCharsets.US_ASCII)
            val dataStart = pos + 8
            val totalChunkLen = 12 + len

            if (type == "tEXt" || type == "zTXt" || type == "iTXt") {
                val nulIdx = (dataStart until dataStart + len).firstOrNull { pngBytes[it] == 0.toByte() }
                if (nulIdx != null) {
                    val kw = String(pngBytes, dataStart, nulIdx - dataStart, StandardCharsets.ISO_8859_1).lowercase()
                    if (kw == "ccv3" || kw == "chara") {
                        pos += totalChunkLen
                        continue
                    }
                }
            }

            if (type == "IEND" && !wroteCardChunks) {
                writeTextChunk(out, "ccv3", encoded)
                writeTextChunk(out, "chara", encoded)
                wroteCardChunks = true
            }

            out.write(pngBytes, pos, totalChunkLen)
            pos += totalChunkLen
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    // ==================== Safe JSON Accessors ====================

    internal fun JsonObject.objOrNull(key: String): JsonObject? =
        com.gugachat.app.core.util.JsonUtils.run { objOrNull(key) }

    internal fun JsonObject.arrOrNull(key: String): JsonArray? =
        com.gugachat.app.core.util.JsonUtils.run { arrOrNull(key) }

    internal fun JsonObject.strOrNull(key: String): String? =
        com.gugachat.app.core.util.JsonUtils.run { strOrNull(key) }

    internal fun JsonObject.boolOrNull(key: String): Boolean? =
        com.gugachat.app.core.util.JsonUtils.run { boolOrNull(key) }

    internal fun JsonObject.intOrNull(key: String): Int? =
        com.gugachat.app.core.util.JsonUtils.run { intOrNull(key) }

    internal fun JsonObject.strListOrEmpty(key: String): List<String> =
        com.gugachat.app.core.util.JsonUtils.run { strListOrEmpty(key) }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(typeBytes)
        out.write(data)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private fun writeTextChunk(out: ByteArrayOutputStream, keyword: String, value: String) {
        val kwBytes = keyword.toByteArray(StandardCharsets.ISO_8859_1)
        val valBytes = value.toByteArray(StandardCharsets.ISO_8859_1)
        val data = ByteArray(kwBytes.size + 1 + valBytes.size)
        System.arraycopy(kwBytes, 0, data, 0, kwBytes.size)
        data[kwBytes.size] = 0
        System.arraycopy(valBytes, 0, data, kwBytes.size + 1, valBytes.size)
        writeChunk(out, "tEXt", data)
    }

    private fun parseTextChunk(buf: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val nul = (start until end).firstOrNull { buf[it] == 0.toByte() } ?: return null
        val key = String(buf, start, nul - start, StandardCharsets.ISO_8859_1)
        val value = String(buf, nul + 1, end - (nul + 1), StandardCharsets.ISO_8859_1)
        return key to value
    }

    private fun parseZtxtChunk(buf: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val nul = (start until end).firstOrNull { buf[it] == 0.toByte() } ?: return null
        if (nul + 2 > end) return null
        val key = String(buf, start, nul - start, StandardCharsets.ISO_8859_1)
        val method = buf[nul + 1].toInt()
        if (method != 0) return null
        val compressed = buf.copyOfRange(nul + 2, end)
        return try {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val baos = ByteArrayOutputStream()
            val tmp = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(tmp)
                if (n <= 0) break
                baos.write(tmp, 0, n)
            }
            inflater.end()
            val rawBytes = baos.toByteArray()
            val text = try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(rawBytes, StandardCharsets.ISO_8859_1)
            }
            key to text
        } catch (_: Exception) {
            null
        }
    }

    private fun parseItxtChunk(buf: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val nul = (start until end).firstOrNull { buf[it] == 0.toByte() } ?: return null
        if (nul + 3 >= end) return null
        val key = String(buf, start, nul - start, StandardCharsets.ISO_8859_1)
        val compressedFlag = buf[nul + 1].toInt()
        var p = nul + 3 // skip compressionFlag + compressionMethod
        val langNul = (p until end).firstOrNull { buf[it] == 0.toByte() } ?: return null
        p = langNul + 1
        val transNul = (p until end).firstOrNull { buf[it] == 0.toByte() } ?: return null
        p = transNul + 1
        if (p >= end) return null
        return if (compressedFlag == 0) {
            key to String(buf, p, end - p, StandardCharsets.UTF_8)
        } else {
            val compressed = buf.copyOfRange(p, end)
            try {
                val inflater = Inflater()
                inflater.setInput(compressed)
                val baos = ByteArrayOutputStream()
                val tmp = ByteArray(1024)
                while (!inflater.finished()) {
                    val n = inflater.inflate(tmp)
                    if (n <= 0) break
                    baos.write(tmp, 0, n)
                }
                inflater.end()
                key to String(baos.toByteArray(), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
