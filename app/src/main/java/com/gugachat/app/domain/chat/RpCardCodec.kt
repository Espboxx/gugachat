package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

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

data class ExtractedZipCard(
    val cardJson: String,
    val avatarBytes: ByteArray? = null,
    val avatarExt: String = "png",
)

object RpCardCodec {
    private const val MAX_ZIP_JSON_BYTES = 16 * 1024 * 1024
    private const val MAX_ZIP_IMAGE_BYTES = 12 * 1024 * 1024

    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun parseCardJson(rawJson: String, conversationId: String = "preview"): ImportedRpCard {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        val root = try {
            AppJson.parseToJsonElement(cleaned) as? JsonObject
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
     * Extracts SillyTavern `.charx` / `.byaf` ZIP card archive (`card.json` + optional embedded avatar image).
     */
    fun extractCardFromZipBytes(zipBytes: ByteArray): ExtractedZipCard? {
        if (!DeepSeekImportCodec.isZipBytes(zipBytes)) return null
        var exactCardJson: String? = null
        var fallbackCardJson: String? = null
        var preferredAvatar: Pair<ByteArray, String>? = null
        var fallbackImage: Pair<ByteArray, String>? = null

        runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }
                    val norm = entry.name.replace('\\', '/').trimStart('/')
                    val fileName = norm.substringAfterLast('/')
                    val lowerPath = norm.lowercase()
                    val lowerName = fileName.lowercase()
                    if (lowerName.startsWith("._") || lowerPath.contains("__macosx/")) {
                        zis.closeEntry()
                        continue
                    }

                    when {
                        lowerName == "card.json" && exactCardJson == null -> {
                            val text = readZipBounded(zis, MAX_ZIP_JSON_BYTES)
                                ?.toString(Charsets.UTF_8)
                                ?.removePrefix("\uFEFF")
                                ?.trim()
                            if (!text.isNullOrEmpty() && text.startsWith("{")) {
                                exactCardJson = text
                            }
                        }
                        lowerName.endsWith(".json") && exactCardJson == null && fallbackCardJson == null -> {
                            val text = readZipBounded(zis, MAX_ZIP_JSON_BYTES)
                                ?.toString(Charsets.UTF_8)
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
                            val ext = lowerName.substringAfterLast('.')
                            val isIconPath = lowerPath.contains("assets/icon/") ||
                                lowerName.startsWith("avatar.") ||
                                lowerName.startsWith("main.")
                            if (isIconPath && preferredAvatar == null) {
                                readZipBounded(zis, MAX_ZIP_IMAGE_BYTES)?.takeIf { it.isNotEmpty() }?.let {
                                    preferredAvatar = it to ext
                                }
                            } else if (fallbackImage == null) {
                                readZipBounded(zis, MAX_ZIP_IMAGE_BYTES)?.takeIf { it.isNotEmpty() }?.let {
                                    fallbackImage = it to ext
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
        }

        val jsonText = exactCardJson ?: fallbackCardJson ?: return null
        val chosenImg = preferredAvatar ?: fallbackImage
        return ExtractedZipCard(
            cardJson = jsonText,
            avatarBytes = chosenImg?.first,
            avatarExt = chosenImg?.second ?: "png",
        )
    }

    private fun parseDepthPromptObject(extensions: JsonObject?): JsonObject? {
        if (extensions == null) return null
        val elem = extensions["depth_prompt"] ?: return null
        if (elem is JsonObject) return elem
        val raw = (elem as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.trim()
            ?: return null
        if (!raw.startsWith("{")) return null
        return runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
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
                entries = AppJson.encodeToJsonElement(serializedEntries),
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
        return AppJson.encodeToString(env)
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
            val decoded = Base64.getDecoder().decode(encoded.trim())
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
        this[key] as? JsonObject

    internal fun JsonObject.arrOrNull(key: String): JsonArray? =
        this[key] as? JsonArray

    internal fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    internal fun JsonObject.boolOrNull(key: String): Boolean? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        prim.booleanOrNull?.let { return it }
        return when (prim.contentOrNull?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    internal fun JsonObject.intOrNull(key: String): Int? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.intOrNull ?: prim.contentOrNull?.trim()?.toIntOrNull()
    }

    internal fun JsonObject.strListOrEmpty(key: String): List<String> {
        val arr = this[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            (el as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun readZipBounded(zis: ZipInputStream, maxBytes: Int): ByteArray? {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zis.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            baos.write(buf, 0, read)
        }
        return baos.toByteArray()
    }

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
            key to String(baos.toByteArray(), StandardCharsets.ISO_8859_1)
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
