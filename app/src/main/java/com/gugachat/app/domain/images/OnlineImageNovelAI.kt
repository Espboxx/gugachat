package com.gugachat.app.domain.images

import com.gugachat.app.core.util.JsonUtils.boolOrNull
import com.gugachat.app.core.util.JsonUtils.doubleOrNull
import com.gugachat.app.core.util.JsonUtils.floatOrNull
import com.gugachat.app.core.util.JsonUtils.intOrNull
import com.gugachat.app.core.util.JsonUtils.longOrNull
import com.gugachat.app.core.util.JsonUtils.strOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NovelAIVersion(val label: String) {
    V3("V3"),
    V4("V4"),
    V4_5("V4.5 / V5"),
}

@Serializable
data class NovelAICharacterPrompt(
    val prompt: String = "",
    val negativePrompt: String = "",
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
)

@Serializable
data class NovelAIParams(
    val negativePrompt: String = "lowres, bad anatomy, bad hands, text, error, missing fingers, extra digit, fewer digits, cropped, worst quality, low quality, normal quality, jpeg artifacts, signature, watermark, username, blurry",
    val width: Int = 832,
    val height: Int = 1216,
    val steps: Int = 28,
    val scale: Double = 5.0,
    val cfgRescale: Double = 0.0,
    val sampler: String = "k_euler_ancestral",
    val noiseSchedule: String = "karras",
    val seed: Long? = null,
    val sm: Boolean = false,
    val smDyn: Boolean = false,
    val varietyPlus: Boolean = false,
    val qualityToggle: Boolean = true,
    val ucPreset: Int = 0, // 0=Heavy, 1=Light, 2=Human Focus, 3=None
    val strength: Double = 0.7,
    val noise: Double = 0.0,
    val referenceStrength: Double = 0.6,
    val infoExtracted: Double = 1.0,
    val useCoords: Boolean = false,
    val characters: List<NovelAICharacterPrompt> = emptyList(),
)

object OnlineImageNovelAI {
    const val MAX_CHARACTERS = 6
    const val MAX_STEPS = 28
    const val MAX_SCALE = 10.0
    const val MAX_SEED = 4_294_967_295L

    val COORD_GRID_COLS = listOf("A", "B", "C", "D", "E")
    val COORD_GRID_VALUES = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)

    private val DEFAULT_CHAR_SLOTS = listOf(
        0.5f to 0.5f, // C3
        0.3f to 0.5f, // B3
        0.7f to 0.5f, // D3
        0.1f to 0.5f, // A3
        0.9f to 0.5f, // E3
        0.5f to 0.3f, // C2
    )

    val SAMPLERS = listOf(
        "k_euler_ancestral",
        "k_euler",
        "k_dpmpp_2s_ancestral",
        "k_dpmpp_2m_sde",
        "k_dpmpp_2m",
        "k_dpmpp_sde",
    )

    val SAMPLERS_V3 = SAMPLERS
    val SAMPLERS_V4 = SAMPLERS

    val NOISE_SCHEDULES_V3 = listOf("karras", "exponential", "polyexponential", "native")
    val NOISE_SCHEDULES_V4 = listOf("karras", "exponential", "polyexponential")
    val NOISE_SCHEDULES = NOISE_SCHEDULES_V3

    val PRESET_DIMENSIONS = listOf(
        832 to 1216,
        1216 to 832,
        1024 to 1024,
        704 to 1408,
        1408 to 704,
        1152 to 896,
        896 to 1152,
    )

    fun detectVersion(modelId: String, upstreamModel: String? = null): NovelAIVersion {
        val m = "${modelId.lowercase()} ${upstreamModel.orEmpty().lowercase()}"
        return when {
            m.contains("4-5") || m.contains("4.5") || m.contains("v5") || m.contains("diffusion-5") -> NovelAIVersion.V4_5
            m.contains("4") -> NovelAIVersion.V4
            else -> NovelAIVersion.V3
        }
    }

    fun availableSamplers(version: NovelAIVersion): List<String> =
        if (version == NovelAIVersion.V3) SAMPLERS_V3 else SAMPLERS_V4

    fun availableNoiseSchedules(version: NovelAIVersion): List<String> =
        if (version == NovelAIVersion.V3) NOISE_SCHEDULES_V3 else NOISE_SCHEDULES_V4

    fun defaultParamsForVersion(version: NovelAIVersion): NovelAIParams =
        if (version == NovelAIVersion.V3) {
            NovelAIParams(
                steps = 28,
                scale = 5.5,
                sampler = "k_euler_ancestral",
                noiseSchedule = "karras",
                sm = false,
                smDyn = false,
            )
        } else {
            NovelAIParams(
                steps = 28,
                scale = 5.0,
                cfgRescale = 0.0,
                sampler = "k_euler_ancestral",
                noiseSchedule = "karras",
                varietyPlus = false,
            )
        }

    fun adaptParamsForVersion(existing: NovelAIParams, version: NovelAIVersion): NovelAIParams {
        val validSamplers = availableSamplers(version)
        val validSchedules = availableNoiseSchedules(version)
        val normalizedSampler = if (existing.sampler in validSamplers) existing.sampler else validSamplers.first()
        val normalizedSchedule = if (existing.noiseSchedule in validSchedules) existing.noiseSchedule else validSchedules.first()
        return existing.copy(
            sampler = normalizedSampler,
            noiseSchedule = normalizedSchedule,
            steps = existing.steps.coerceIn(1, MAX_STEPS),
            scale = existing.scale.coerceIn(0.0, MAX_SCALE),
            sm = if (version == NovelAIVersion.V3) existing.sm else false,
            smDyn = if (version == NovelAIVersion.V3) existing.smDyn else false,
        )
    }

    fun nextDefaultCharacterPosition(existing: List<NovelAICharacterPrompt>): Pair<Float, Float> {
        for ((sx, sy) in DEFAULT_CHAR_SLOTS) {
            val occupied = existing.any { abs(it.centerX - sx) < 0.08f && abs(it.centerY - sy) < 0.08f }
            if (!occupied) return sx to sy
        }
        return 0.5f to 0.5f
    }

    fun nearestGridCell(x: Float, y: Float): Pair<Int, Int> {
        val clampedX = x.coerceIn(0f, 1f)
        val clampedY = y.coerceIn(0f, 1f)
        val col = COORD_GRID_VALUES.indices.minByOrNull { abs(COORD_GRID_VALUES[it] - clampedX) } ?: 2
        val row = COORD_GRID_VALUES.indices.minByOrNull { abs(COORD_GRID_VALUES[it] - clampedY) } ?: 2
        return col to row
    }

    fun gridCellLabel(col: Int, row: Int): String {
        val c = COORD_GRID_COLS.getOrElse(col.coerceIn(0, 4)) { "C" }
        val r = row.coerceIn(0, 4) + 1
        return "$c$r"
    }

    fun formatCoordinateLabel(x: Float, y: Float): String {
        val (col, row) = nearestGridCell(x, y)
        val snapX = COORD_GRID_VALUES[col]
        val snapY = COORD_GRID_VALUES[row]
        val rx = formatCoordNumber(x)
        val ry = formatCoordNumber(y)
        return if (abs(x - snapX) < 0.02f && abs(y - snapY) < 0.02f) {
            "${gridCellLabel(col, row)} ($rx, $ry)"
        } else {
            "($rx, $ry)"
        }
    }

    fun formatCoordNumber(value: Float): String {
        val rounded = (value.coerceIn(0f, 1f) * 100f).roundToInt() / 100.0
        val text = String.format(java.util.Locale.US, "%.2f", rounded)
        return text.trimEnd('0').trimEnd('.').let { if (it.isEmpty()) "0" else it }
    }

    private fun roundCoordDouble(value: Float): Double =
        ((value.coerceIn(0f, 1f) * 100f).roundToInt()) / 100.0

    fun applyDimensionPreset(params: NovelAIParams, width: Int, height: Int): NovelAIParams =
        params.copy(
            width = OnlineImageStudioUtils.normalizeDimensionMultiple64(width),
            height = OnlineImageStudioUtils.normalizeDimensionMultiple64(height),
        )

    /**
     * Serializes NovelAI parameters into the gateway's strict `NovelAIParameters` JSON schema.
     * The gateway translates `characterPrompts` + `use_coords` into upstream `v4_prompt` / `v4_negative_prompt`.
     */
    fun buildNovelAIPayloadJsonObject(
        prompt: String,
        params: NovelAIParams,
        modelId: String,
        hasReferenceImages: Boolean = false,
        isImg2Img: Boolean = false,
        upstreamModel: String? = null,
    ): JsonObject {
        val version = detectVersion(modelId, upstreamModel)
        val validSamplers = availableSamplers(version)
        val validSchedules = availableNoiseSchedules(version)
        val normalizedSampler = if (params.sampler in validSamplers) params.sampler else validSamplers.first()
        val normalizedSchedule = if (params.noiseSchedule in validSchedules) params.noiseSchedule else validSchedules.first()
        val width = OnlineImageStudioUtils.normalizeDimensionMultiple64(params.width)
        val height = OnlineImageStudioUtils.normalizeDimensionMultiple64(params.height)

        return buildJsonObject {
            put("negative_prompt", params.negativePrompt)
            put("width", width)
            put("height", height)
            put("steps", params.steps.coerceIn(1, MAX_STEPS))
            put("scale", params.scale.coerceIn(0.0, MAX_SCALE))
            put("cfg_rescale", params.cfgRescale.coerceIn(0.0, 1.0))
            put("sampler", normalizedSampler)
            put("noise_schedule", normalizedSchedule)
            put("ucPreset", params.ucPreset.coerceIn(0, 4))
            put("qualityToggle", params.qualityToggle)
            params.seed?.takeIf { it in 0L..MAX_SEED }?.let { put("seed", it) }

            if (version == NovelAIVersion.V3) {
                put("sm", params.sm || params.smDyn)
                put("sm_dyn", params.smDyn)
            } else {
                if (params.varietyPlus) {
                    put("skip_cfg_above_sigma", 19.0)
                }
                val activeCharacters = params.characters
                    .take(MAX_CHARACTERS)
                    .filter { it.prompt.isNotBlank() || it.negativePrompt.isNotBlank() }
                if (activeCharacters.isNotEmpty()) {
                    put("characterPrompts", buildCharacterPromptsArray(activeCharacters))
                    put("use_coords", params.useCoords)
                }
            }

            if (isImg2Img) {
                put("strength", params.strength.coerceIn(0.01, 0.99))
                put("noise", params.noise.coerceIn(0.0, 0.99))
            }
        }
    }

    fun parseNovelAIParams(
        raw: JsonObject,
        modelId: String = "",
        upstreamModel: String? = null,
    ): NovelAIParams {
        val version = detectVersion(modelId, upstreamModel)
        val base = defaultParamsForVersion(version)
        val v4PromptObj = raw["v4_prompt"] as? JsonObject
        val v4NegObj = raw["v4_negative_prompt"] as? JsonObject

        val negPrompt = raw.strOrNull("negative_prompt")
            ?: raw.strOrNull("negativePrompt")
            ?: (v4NegObj?.get("caption") as? JsonObject)?.strOrNull("base_caption")
            ?: base.negativePrompt
        val width = raw.intOrNull("width")?.let { OnlineImageStudioUtils.normalizeDimensionMultiple64(it) } ?: base.width
        val height = raw.intOrNull("height")?.let { OnlineImageStudioUtils.normalizeDimensionMultiple64(it) } ?: base.height
        val steps = (raw.intOrNull("steps") ?: base.steps).coerceIn(1, MAX_STEPS)
        val scale = (raw.doubleOrNull("scale") ?: base.scale).coerceIn(0.0, MAX_SCALE)
        val cfgRescale = (raw.doubleOrNull("cfg_rescale") ?: raw.doubleOrNull("cfgRescale") ?: base.cfgRescale).coerceIn(0.0, 1.0)
        val validSamplers = availableSamplers(version)
        val rawSampler = raw.strOrNull("sampler") ?: base.sampler
        val sampler = if (rawSampler in validSamplers) rawSampler else validSamplers.first()
        val validSchedules = availableNoiseSchedules(version)
        val rawSchedule = raw.strOrNull("noise_schedule") ?: raw.strOrNull("noiseSchedule") ?: base.noiseSchedule
        val noiseSchedule = if (rawSchedule in validSchedules) rawSchedule else validSchedules.first()
        val seed = raw.longOrNull("seed")?.takeIf { it in 0L..MAX_SEED }
        val sm = if (version == NovelAIVersion.V3) (raw.boolOrNull("sm") ?: base.sm) else false
        val smDyn = if (version == NovelAIVersion.V3) (raw.boolOrNull("sm_dyn") ?: raw.boolOrNull("smDyn") ?: base.smDyn) else false
        val varietyPlus = raw.boolOrNull("varietyPlus")
            ?: raw.doubleOrNull("skip_cfg_above_sigma")?.let { it > 0.0 }
            ?: base.varietyPlus
        val qualityToggle = raw.boolOrNull("qualityToggle") ?: raw.boolOrNull("quality_toggle") ?: base.qualityToggle
        val ucPreset = (raw.intOrNull("ucPreset") ?: raw.intOrNull("uc_preset") ?: base.ucPreset).coerceIn(0, 4)
        val strength = (raw.doubleOrNull("strength") ?: base.strength).coerceIn(0.01, 0.99)
        val noise = (raw.doubleOrNull("noise") ?: base.noise).coerceIn(0.0, 0.99)
        val refStrength = (raw.doubleOrNull("reference_strength") ?: raw.doubleOrNull("referenceStrength") ?: base.referenceStrength).coerceIn(0.0, 1.0)
        val infoExtracted = (raw.doubleOrNull("reference_information_extracted") ?: raw.doubleOrNull("infoExtracted") ?: base.infoExtracted).coerceIn(0.0, 1.0)

        val useCoords = if (version == NovelAIVersion.V3) {
            false
        } else {
            raw.boolOrNull("use_coords")
                ?: v4PromptObj?.boolOrNull("use_coords")
                ?: raw.boolOrNull("useCoords")
                ?: base.useCoords
        }

        val parsedChars = if (version == NovelAIVersion.V3) {
            emptyList()
        } else {
            parseCharactersFromRaw(raw, v4PromptObj, v4NegObj)
        }

        return NovelAIParams(
            negativePrompt = negPrompt,
            width = width,
            height = height,
            steps = steps,
            scale = scale,
            cfgRescale = cfgRescale,
            sampler = sampler,
            noiseSchedule = noiseSchedule,
            seed = seed,
            sm = sm,
            smDyn = smDyn,
            varietyPlus = varietyPlus,
            qualityToggle = qualityToggle,
            ucPreset = ucPreset,
            strength = strength,
            noise = noise,
            referenceStrength = refStrength,
            infoExtracted = infoExtracted,
            useCoords = useCoords,
            characters = parsedChars,
        )
    }

    fun countCharactersInPayload(raw: JsonObject?): Int {
        if (raw == null) return 0
        val v4PromptObj = raw["v4_prompt"] as? JsonObject
        val v4NegObj = raw["v4_negative_prompt"] as? JsonObject
        return parseCharactersFromRaw(raw, v4PromptObj, v4NegObj).size
    }

    private fun parseCharactersFromRaw(
        raw: JsonObject,
        v4PromptObj: JsonObject?,
        v4NegObj: JsonObject?,
    ): List<NovelAICharacterPrompt> {
        // 1. Canonical gateway schema: characterPrompts: [{ prompt, uc, center: { x, y } }]
        val gwArr = raw["characterPrompts"] as? JsonArray
        if (gwArr != null && gwArr.isNotEmpty()) {
            return gwArr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val pos = obj.strOrNull("prompt") ?: obj.strOrNull("char_caption") ?: ""
                val neg = obj.strOrNull("uc") ?: obj.strOrNull("negative_prompt") ?: obj.strOrNull("negativePrompt") ?: ""
                val centerObj = obj["center"] as? JsonObject
                val cx = (centerObj?.floatOrNull("x") ?: obj.floatOrNull("centerX") ?: obj.floatOrNull("center_x") ?: 0.5f).coerceIn(0f, 1f)
                val cy = (centerObj?.floatOrNull("y") ?: obj.floatOrNull("centerY") ?: obj.floatOrNull("center_y") ?: 0.5f).coerceIn(0f, 1f)
                if (pos.isBlank() && neg.isBlank()) null
                else NovelAICharacterPrompt(prompt = pos, negativePrompt = neg, centerX = cx, centerY = cy)
            }.take(MAX_CHARACTERS)
        }

        // 2. Upstream NovelAI expanded format: v4_prompt.caption.char_captions + v4_negative_prompt.caption.char_captions
        val posCaps = (v4PromptObj?.get("caption") as? JsonObject)?.get("char_captions") as? JsonArray
        val negCaps = (v4NegObj?.get("caption") as? JsonObject)?.get("char_captions") as? JsonArray
        if (posCaps != null && posCaps.isNotEmpty()) {
            return posCaps.mapIndexedNotNull { idx, elem ->
                val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
                val posPrompt = obj.strOrNull("char_caption").orEmpty()
                val negObj = negCaps?.getOrNull(idx) as? JsonObject
                val negPrompt = negObj?.strOrNull("char_caption").orEmpty()
                val centerObj = ((obj["centers"] as? JsonArray)?.firstOrNull() as? JsonObject)
                    ?: ((negObj?.get("centers") as? JsonArray)?.firstOrNull() as? JsonObject)
                val cx = (centerObj?.floatOrNull("x") ?: 0.5f).coerceIn(0f, 1f)
                val cy = (centerObj?.floatOrNull("y") ?: 0.5f).coerceIn(0f, 1f)
                if (posPrompt.isBlank() && negPrompt.isBlank()) null
                else NovelAICharacterPrompt(
                    prompt = posPrompt,
                    negativePrompt = negPrompt,
                    centerX = cx,
                    centerY = cy,
                )
            }.take(MAX_CHARACTERS)
        }

        // 3. Legacy simple format: characters: [{ prompt, negativePrompt, centerX, centerY }]
        val simpleArr = raw["characters"] as? JsonArray ?: return emptyList()
        return simpleArr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val pos = obj.strOrNull("prompt") ?: obj.strOrNull("char_caption") ?: ""
            val neg = obj.strOrNull("negativePrompt") ?: obj.strOrNull("negative_prompt") ?: obj.strOrNull("uc") ?: ""
            val centerObj = obj["center"] as? JsonObject
            val cx = (centerObj?.floatOrNull("x") ?: obj.floatOrNull("centerX") ?: obj.floatOrNull("center_x") ?: obj.floatOrNull("x") ?: 0.5f).coerceIn(0f, 1f)
            val cy = (centerObj?.floatOrNull("y") ?: obj.floatOrNull("centerY") ?: obj.floatOrNull("center_y") ?: obj.floatOrNull("y") ?: 0.5f).coerceIn(0f, 1f)
            if (pos.isBlank() && neg.isBlank()) null
            else NovelAICharacterPrompt(prompt = pos, negativePrompt = neg, centerX = cx, centerY = cy)
        }.take(MAX_CHARACTERS)
    }

    private fun buildCharacterPromptsArray(chars: List<NovelAICharacterPrompt>): JsonArray =
        buildJsonArray {
            chars.forEach { ch ->
                add(
                    buildJsonObject {
                        put("prompt", ch.prompt.trim())
                        put("uc", ch.negativePrompt.trim())
                        put(
                            "center",
                            buildJsonObject {
                                put("x", roundCoordDouble(ch.centerX))
                                put("y", roundCoordDouble(ch.centerY))
                            },
                        )
                    },
                )
            }
        }
}
