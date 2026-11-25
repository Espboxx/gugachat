package com.gugachat.app.domain.images

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    val scale: Double = 5.5,
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
    val characters: List<NovelAICharacterPrompt> = emptyList(),
)

object OnlineImageNovelAI {
    val SAMPLERS_V3 = listOf(
        "k_euler_ancestral",
        "k_euler",
        "k_dpmpp_2s_ancestral",
        "k_dpmpp_2m",
        "k_dpmpp_2m_sde",
        "k_dpmpp_sde",
        "ddim_v3",
    )

    val SAMPLERS_V4 = listOf(
        "k_euler_ancestral",
        "k_euler",
        "k_dpmpp_2s_ancestral",
        "k_dpmpp_2m",
        "k_dpmpp_2m_sde",
        "k_dpmpp_sde",
    )

    val NOISE_SCHEDULES = listOf("karras", "exponential", "polyexponential", "native")

    val PRESET_DIMENSIONS = listOf(
        832 to 1216,
        1216 to 832,
        1024 to 1024,
        704 to 1408,
        1408 to 704,
        1152 to 896,
        896 to 1152,
    )

    fun detectVersion(modelId: String): NovelAIVersion {
        val m = modelId.lowercase()
        return when {
            m.contains("4-5") || m.contains("4.5") || m.contains("v5") -> NovelAIVersion.V4_5
            m.contains("4") -> NovelAIVersion.V4
            else -> NovelAIVersion.V3
        }
    }

    fun availableSamplers(version: NovelAIVersion): List<String> =
        if (version == NovelAIVersion.V3) SAMPLERS_V3 else SAMPLERS_V4

    fun defaultParamsForVersion(version: NovelAIVersion): NovelAIParams =
        if (version == NovelAIVersion.V3) {
            NovelAIParams(
                steps = 28,
                scale = 5.5,
                sampler = "k_euler_ancestral",
                noiseSchedule = "native",
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

    fun applyDimensionPreset(params: NovelAIParams, width: Int, height: Int): NovelAIParams =
        params.copy(
            width = OnlineImageStudioUtils.normalizeDimensionMultiple64(width),
            height = OnlineImageStudioUtils.normalizeDimensionMultiple64(height),
        )

    fun buildNovelAIPayloadJsonObject(
        prompt: String,
        params: NovelAIParams,
        modelId: String,
        hasReferenceImages: Boolean = false,
        isImg2Img: Boolean = false,
    ): JsonObject {
        val version = detectVersion(modelId)
        val normalizedSampler = if (params.sampler in availableSamplers(version)) {
            params.sampler
        } else {
            availableSamplers(version).first()
        }
        val width = OnlineImageStudioUtils.normalizeDimensionMultiple64(params.width)
        val height = OnlineImageStudioUtils.normalizeDimensionMultiple64(params.height)

        return buildJsonObject {
            put("negative_prompt", params.negativePrompt)
            put("width", width)
            put("height", height)
            put("steps", params.steps.coerceIn(1, 50))
            put("scale", params.scale.coerceIn(1.0, 20.0))
            put("cfg_rescale", params.cfgRescale.coerceIn(0.0, 1.0))
            put("sampler", normalizedSampler)
            put("noise_schedule", params.noiseSchedule)
            put("ucPreset", params.ucPreset.coerceIn(0, 3))
            put("qualityToggle", params.qualityToggle)
            params.seed?.takeIf { it >= 0L }?.let { put("seed", it) }

            if (version == NovelAIVersion.V3) {
                put("sm", params.sm)
                put("sm_dyn", params.smDyn)
            } else {
                put("skip_cfg_above_sigma", if (params.varietyPlus) 19.0 else 0.0)
                if (params.characters.isNotEmpty()) {
                    put("v4_prompt", buildV4PromptObject(prompt, params.characters))
                    put("v4_negative_prompt", buildV4NegativeObject(params.negativePrompt, params.characters))
                }
            }

            if (isImg2Img) {
                put("strength", params.strength.coerceIn(0.01, 0.99))
                put("noise", params.noise.coerceIn(0.0, 0.99))
            }
            if (hasReferenceImages) {
                put("reference_strength", params.referenceStrength.coerceIn(0.0, 1.0))
                put("reference_information_extracted", params.infoExtracted.coerceIn(0.0, 1.0))
            }
        }
    }

    private fun buildV4PromptObject(basePrompt: String, chars: List<NovelAICharacterPrompt>): JsonObject =
        buildJsonObject {
            put(
                "caption",
                buildJsonObject {
                    put("base_caption", basePrompt)
                    put(
                        "char_captions",
                        buildJsonArray {
                            chars.forEach { ch ->
                                add(
                                    buildJsonObject {
                                        put("char_caption", ch.prompt)
                                        put(
                                            "centers",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("x", ch.centerX.coerceIn(0f, 1f))
                                                        put("y", ch.centerY.coerceIn(0f, 1f))
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    )
                },
            )
            put("use_coords", true)
            put("use_order", true)
        }

    private fun buildV4NegativeObject(baseUc: String, chars: List<NovelAICharacterPrompt>): JsonObject =
        buildJsonObject {
            put(
                "caption",
                buildJsonObject {
                    put("base_caption", baseUc)
                    put(
                        "char_captions",
                        buildJsonArray {
                            chars.forEach { ch ->
                                add(
                                    buildJsonObject {
                                        put("char_caption", ch.negativePrompt)
                                        put(
                                            "centers",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("x", ch.centerX.coerceIn(0f, 1f))
                                                        put("y", ch.centerY.coerceIn(0f, 1f))
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
}
