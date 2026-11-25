package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
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
    val entries: List<StLoreEntryJson> = emptyList(),
)

@Serializable
private data class StCardDataJson(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val first_mes: String = "",
    val mes_example: String = "",
    val creator_notes: String = "",
    val system_prompt: String = "",
    val tags: List<String> = emptyList(),
    val character_book: StCharacterBookJson? = null,
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
    val mes_example: String? = null,
    val system_prompt: String? = null,
    val creator_notes: String? = null,
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
        val sysPrompt = (data?.system_prompt ?: env.system_prompt).orEmpty().trim()
        val notes = (data?.creator_notes ?: env.creator_notes).orEmpty().trim()
        val examples = data?.mes_example ?: env.mes_example.orEmpty()

        val worldview = buildList {
            if (scenario.isNotBlank()) add(scenario)
            if (examples.isNotBlank()) add("【对话示例】\n$examples")
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
        )

        val rp = RpConfig(
            title = charName,
            worldview = worldview,
            systemRules = sysPrompt,
            narratorStyle = notes,
            cast = listOf(castChar),
            state = RpGameState(scene = scenario.take(40).ifBlank { "初次相遇" }),
            greeting = firstMes,
            cardSource = "sillytavern_$specVersion",
        )

        val now = System.currentTimeMillis()
        val loreList = data?.character_book?.entries.orEmpty().mapIndexedNotNull { idx, entry ->
            if (entry.content.isBlank()) return@mapIndexedNotNull null
            val title = (entry.name ?: entry.comment ?: entry.keys.firstOrNull() ?: "Lore #$idx").trim()
            val obj = NovelLoreEntry(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                category = "concept",
                title = title,
                content = entry.content.trim(),
                keywords = entry.keys.ifEmpty { listOf(title) },
                alwaysActive = entry.constant,
                priority = entry.priority,
                enabled = entry.enabled,
                validFromSeq = 0,
                createdAt = now,
                updatedAt = now,
            )
            obj.copy(estimatedTokens = NovelEngine.estimateLoreTokens(obj))
        }

        return ImportedRpCard(rpConfig = rp, importedLore = loreList, specVersion = specVersion)
    }

    fun exportToV3Json(rp: RpConfig, lore: List<NovelLoreEntry> = emptyList()): String {
        val mainChar = rp.cast.firstOrNull()
        val charBook = if (lore.isNotEmpty()) {
            StCharacterBookJson(
                name = rp.title,
                entries = lore.map { l ->
                    StLoreEntryJson(
                        keys = l.keywords.ifEmpty { listOf(l.title) },
                        content = l.content,
                        name = l.title,
                        enabled = l.enabled,
                        constant = l.alwaysActive,
                        priority = l.priority,
                    )
                },
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
            creator_notes = rp.narratorStyle,
            system_prompt = rp.systemRules,
            character_book = charBook,
        )
        val env = StCardEnvelopeJson(
            spec = "chara_card_v3",
            spec_version = "3.0",
            data = cardData,
        )
        return AppJson.encodeToString(env)
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

    private fun writeTextChunk(out: ByteArrayOutputStream, keyword: String, value: String) {
        val kwBytes = keyword.toByteArray(StandardCharsets.ISO_8859_1)
        val valBytes = value.toByteArray(StandardCharsets.ISO_8859_1)
        val data = ByteArray(kwBytes.size + 1 + valBytes.size)
        System.arraycopy(kwBytes, 0, data, 0, kwBytes.size)
        data[kwBytes.size] = 0
        System.arraycopy(valBytes, 0, data, kwBytes.size + 1, valBytes.size)

        val typeBytes = "tEXt".toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)

        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(typeBytes)
        out.write(data)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
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
