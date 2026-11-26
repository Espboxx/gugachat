package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

@Serializable
private data class StLoreEntryJson(
    val keys: List<String> = emptyList(),
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
    // V1 / top-level fallback fields
    val name: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    val first_mes: String? = null,
    val alternate_greetings: List<String>? = null,
    val mes_example: String? = null,
    val system_prompt: String? = null,
    val post_history_instructions: String? = null,
    val creator_notes: String? = null,
    val tags: List<String>? = null,
    val extensions: JsonObject? = null,
)

data class ImportedRpCard(
    val rpConfig: RpConfig,
    val importedLore: List<NovelLoreEntry>,
    val specVersion: String,
)

object RpCardCodec {
    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun parseCardJson(rawJson: String, conversationId: String = "preview"): ImportedRpCard {
        val env = AppJson.decodeFromString(StCardEnvelopeJson.serializer(), rawJson.trim())
        val data = env.data
        val isV3 = env.spec.equals("chara_card_v3", ignoreCase = true)
        val isV2 = env.spec.equals("chara_card_v2", ignoreCase = true)
        val specVersion = when {
            isV3 -> "v3"
            isV2 || data != null -> "v2"
            else -> "v1"
        }

        val charName = (data?.name ?: env.name).orEmpty().trim().ifEmpty { "角色卡" }
        val desc = (data?.description ?: env.description).orEmpty().trim()
        val personality = (data?.personality ?: env.personality).orEmpty().trim()
        val scenario = (data?.scenario ?: env.scenario).orEmpty().trim()
        val firstMes = (data?.first_mes ?: env.first_mes).orEmpty().trim()
        val altGreetings = (data?.alternate_greetings?.takeIf { it.isNotEmpty() } ?: env.alternate_greetings.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val sysPrompt = (data?.system_prompt ?: env.system_prompt).orEmpty().trim()
        val postHistory = (data?.post_history_instructions ?: env.post_history_instructions).orEmpty().trim()
        val notes = (data?.creator_notes ?: env.creator_notes).orEmpty().trim()
        val examples = (data?.mes_example ?: env.mes_example).orEmpty().trim()
        val tags = (data?.tags?.takeIf { it.isNotEmpty() } ?: env.tags.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

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

        val regexScripts = (parseRegexScripts(data?.extensions) + parseRegexScripts(env.extensions))
            .distinctBy { "${it.id}|${it.findRegex}|${it.replaceString}" }

        val rp = RpConfig(
            title = charName,
            worldview = worldview,
            systemRules = sysPrompt,
            narratorStyle = notes,
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

        val now = System.currentTimeMillis()
        val loreList = parseCharacterBookEntries(
            entriesElem = data?.character_book?.entries,
            conversationId = conversationId,
            now = now,
        )

        return ImportedRpCard(rpConfig = rp, importedLore = loreList, specVersion = specVersion)
    }

    private fun parseCharacterBookEntries(
        entriesElem: JsonElement?,
        conversationId: String,
        now: Long,
    ): List<NovelLoreEntry> {
        if (entriesElem == null) return emptyList()
        val rawObjects: List<JsonObject> = when (entriesElem) {
            is JsonArray -> entriesElem.mapNotNull { it as? JsonObject }
            is JsonObject -> entriesElem.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        return rawObjects.mapIndexedNotNull { idx, obj ->
            val content = obj.str("content").orEmpty().trim()
            if (content.isEmpty()) return@mapIndexedNotNull null

            val keysArr = (obj["keys"] as? JsonArray) ?: (obj["key"] as? JsonArray)
            val keys = keysArr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val title = (obj.str("name") ?: obj.str("comment") ?: keys.firstOrNull() ?: "Lore #$idx").trim()
            val disabled = (obj["disable"] as? JsonPrimitive)?.booleanOrNull == true
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: !disabled
            val constant = (obj["constant"] as? JsonPrimitive)?.booleanOrNull == true
            val priority = (obj["priority"] as? JsonPrimitive)?.intOrNull
                ?: (obj["insertion_order"] as? JsonPrimitive)?.intOrNull
                ?: (obj["order"] as? JsonPrimitive)?.intOrNull
                ?: 50

            val entry = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "concept",
                title = title,
                content = content,
                keywords = keys.ifEmpty { listOf(title) },
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

    private fun parseRegexScripts(extensions: JsonObject?): List<RpRegexScript> {
        val rawArr = extensions?.get("regex_scripts") as? JsonArray ?: return emptyList()
        return rawArr.mapIndexedNotNull { idx, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val rawFind = obj.str("findRegex").orEmpty()
            if (rawFind.isBlank()) return@mapIndexedNotNull null

            val explicitFlags = obj.str("flags")
            val (pattern, parsedFlags) = RpRegexEngine.parseRegexLiteral(
                raw = rawFind,
                fallbackFlags = explicitFlags ?: "gms",
            )
            val name = (obj.str("scriptName") ?: obj.str("name") ?: "Regex #${idx + 1}").trim()
            val id = obj.str("id")?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val disabled = (obj["disabled"] as? JsonPrimitive)?.booleanOrNull == true
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: !disabled
            val promptOnly = (obj["promptOnly"] as? JsonPrimitive)?.booleanOrNull ?: false
            val markdownOnly = (obj["markdownOnly"] as? JsonPrimitive)?.booleanOrNull ?: !promptOnly

            val placement = parseRegexPlacement(obj["placement"])
            val trimStrings = (obj["trimStrings"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            RpRegexScript(
                id = id,
                name = name,
                enabled = enabled,
                findRegex = pattern,
                replaceString = obj.str("replaceString").orEmpty(),
                placement = placement,
                markdownOnly = markdownOnly,
                promptOnly = promptOnly,
                flags = parsedFlags,
                trimStrings = trimStrings,
            )
        }
    }

    private fun parseRegexPlacement(elem: JsonElement?): String {
        if (elem is JsonPrimitive) {
            val s = elem.contentOrNull?.trim()?.lowercase()
            if (s == "user_input" || s == "ai_output" || s == "both") return s
            return when (elem.intOrNull) {
                1 -> "user_input"
                2 -> "ai_output"
                else -> "ai_output"
            }
        }
        val arr = elem as? JsonArray ?: return "ai_output"
        val codes = arr.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.toSet()
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

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    fun exportToV3Json(rp: RpConfig, lore: List<NovelLoreEntry> = emptyList()): String {
        val mainChar = rp.cast.firstOrNull { !it.isPlayer } ?: rp.cast.firstOrNull()
        val charBook = if (lore.isNotEmpty()) {
            val serializedEntries = lore.map { l ->
                StLoreEntryJson(
                    keys = l.keywords.ifEmpty { listOf(l.title) },
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
