package com.gugachat.app.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

class ApiException(
    val status: Int,
    val code: String?,
    override val message: String,
    val reason: String? = null,
    val isContextTooLong: Boolean = isContextLengthErrorMessage(message, code, status),
) : Exception(message) {
    companion object {
        fun isContextLengthErrorMessage(message: String, code: String?, status: Int?): Boolean {
            if (status != null && status !in setOf(0, 200, 400, 413, 422, 500, 502)) {
                return false
            }
            val hay = "${code.orEmpty()} $message".lowercase()
            return hay.contains("context_length") ||
                hay.contains("context length") ||
                hay.contains("maximum context") ||
                hay.contains("too many tokens") ||
                hay.contains("prompt is too long") ||
                hay.contains("input is too long") ||
                hay.contains("exceeds the context") ||
                hay.contains("token limit") ||
                hay.contains("request entity too large")
        }
    }
}

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val reason: String? = null,
    val metadata: Map<String, String>? = null,
    val data: T? = null,
)

@Serializable
data class PublicSettingsDto(
    val registration_enabled: Boolean = false,
    val email_verify_enabled: Boolean = false,
    val force_email_on_third_party_signup: Boolean = false,
    val promo_code_enabled: Boolean = false,
    val password_reset_enabled: Boolean = false,
    val invitation_code_enabled: Boolean = false,
    val turnstile_enabled: Boolean = false,
    val turnstile_site_key: String = "",
    val tencent_captcha_enabled: Boolean = false,
    val tencent_captcha_app_id: String = "",
    val tencent_captcha_region: String = "cn",
    val aliyun_captcha_enabled: Boolean = false,
    val aliyun_captcha_scene_id: String = "",
    val aliyun_captcha_prefix: String = "",
    val aliyun_captcha_region: String = "cn",
    val passkey_enabled: Boolean = false,
    val site_name: String = "GuGuChat",
    val site_logo: String = "",
    val site_subtitle: String = "",
    val api_base_url: String = "",
    val backend_mode_enabled: Boolean = false,
    val linuxdo_oauth_enabled: Boolean = false,
    val github_oauth_enabled: Boolean = false,
    val google_oauth_enabled: Boolean = false,
    val oidc_oauth_enabled: Boolean = false,
    val oidc_oauth_provider_name: String = "",
    val wechat_oauth_enabled: Boolean = false,
    val wechat_oauth_open_enabled: Boolean = false,
    val wechat_oauth_mp_enabled: Boolean = false,
    val wechat_oauth_mobile_enabled: Boolean = false,
    val dingtalk_oauth_enabled: Boolean = false,
    val online_chat_enabled: Boolean = true,
    val version: String = "",
)

/**
 * Human-verification proof submitted along with login, register, or verify-code requests.
 */
@Serializable
data class CaptchaProof(
    val turnstileToken: String = "",
    val tencentTicket: String = "",
    val tencentRandstr: String = "",
) {
    val isReady: Boolean
        get() = turnstileToken.isNotBlank() ||
            (tencentTicket.isNotBlank() && tencentRandstr.isNotBlank())
}

/**
 * Active captcha provider enabled on the target server, in priority order matching sub2api-auto frontend.
 */
sealed class ActiveCaptchaConfig {
    data object None : ActiveCaptchaConfig()
    data class Turnstile(val siteKey: String) : ActiveCaptchaConfig()
    data class Tencent(val appId: String, val region: String) : ActiveCaptchaConfig()
    data class Aliyun(val sceneId: String, val prefix: String, val region: String) : ActiveCaptchaConfig()
}

fun PublicSettingsDto.resolveActiveCaptcha(): ActiveCaptchaConfig = when {
    turnstile_enabled && turnstile_site_key.isNotBlank() ->
        ActiveCaptchaConfig.Turnstile(siteKey = turnstile_site_key.trim())
    tencent_captcha_enabled && tencent_captcha_app_id.isNotBlank() ->
        ActiveCaptchaConfig.Tencent(
            appId = tencent_captcha_app_id.trim(),
            region = if (tencent_captcha_region.trim().equals("intl", ignoreCase = true)) "intl" else "cn",
        )
    aliyun_captcha_enabled && aliyun_captcha_scene_id.isNotBlank() && aliyun_captcha_prefix.isNotBlank() ->
        ActiveCaptchaConfig.Aliyun(
            sceneId = aliyun_captcha_scene_id.trim(),
            prefix = aliyun_captcha_prefix.trim(),
            region = if (aliyun_captcha_region.trim().equals("sgp", ignoreCase = true)) "sgp" else "cn",
        )
    else -> ActiveCaptchaConfig.None
}

@Serializable
data class UserDto(
    val id: Long = 0L,
    val username: String = "",
    val email: String = "",
    val avatar_url: String? = null,
    val role: String = "user",
    val balance: Double = 0.0,
    val frozen_balance: Double? = null,
    val concurrency: Int = 1,
    val status: String = "active",
    val run_mode: String? = null,
)

@Serializable
data class AuthLoginRequest(
    val email: String,
    val password: String,
    val turnstile_token: String? = null,
    val tencent_captcha_ticket: String? = null,
    val tencent_captcha_randstr: String? = null,
)

@Serializable
data class AuthRegisterRequest(
    val email: String,
    val password: String,
    val verify_code: String? = null,
    val promo_code: String? = null,
    val invitation_code: String? = null,
    val turnstile_token: String? = null,
    val tencent_captcha_ticket: String? = null,
    val tencent_captcha_randstr: String? = null,
)

@Serializable
data class SendVerifyCodeRequest(
    val email: String,
    val turnstile_token: String? = null,
    val tencent_captcha_ticket: String? = null,
    val tencent_captcha_randstr: String? = null,
)

@Serializable
data class SendVerifyCodeResponse(
    val message: String = "",
    val countdown: Int = 60,
)

@Serializable
data class Login2FARequest(
    val temp_token: String,
    val totp_code: String,
)

@Serializable
data class LoginResponseDto(
    val requires_2fa: Boolean = false,
    val temp_token: String? = null,
    val user_email_masked: String? = null,
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String,
)

@Serializable
data class RefreshTokenResponseDto(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long = 3600L,
    val token_type: String = "Bearer",
)

@Serializable
data class LogoutRequest(
    val refresh_token: String,
)

@Serializable
data class GroupModelFreeQuotaStatusDto(
    val model_pattern: String = "",
    val rule_key: String = "",
    val quota_type: String = "requests",
    val scope: String? = null,
    val daily_limit: Double = 0.0,
    val used: Double = 0.0,
    val remaining: Double = 0.0,
    val resets_at: String = "",
)

// ==================== Online Chat DTOs ====================

@Serializable
data class OnlineChatGroupDto(
    val id: Long,
    val name: String,
    val platform: String = "",
    val subscription_type: String = "",
    val rate_multiplier: Double = 1.0,
    val is_exclusive: Boolean = false,
    val has_free_quotas: Boolean = false,
)

@Serializable
data class OnlineChatCapabilitiesDto(
    val enabled: Boolean = false,
    val groups: List<OnlineChatGroupDto> = emptyList(),
)

@Serializable
data class OnlineChatModelDto(
    val id: String,
    val supports_vision: Boolean = false,
    val supports_reasoning: Boolean = false,
    val context_window: Int = 128000,
    val free_quota: GroupModelFreeQuotaStatusDto? = null,
)

@Serializable
data class OnlineChatModelsResponseDto(
    val models: List<OnlineChatModelDto> = emptyList(),
)

@Serializable
data class WireImageUrlDto(
    val url: String,
)

@Serializable
data class WireChatContentPartDto(
    val type: String,
    val text: String? = null,
    val image_url: WireImageUrlDto? = null,
)

@Serializable
data class WireChatMessageDto(
    val role: String,
    val content: JsonElement,
    @kotlinx.serialization.Transient val isDepthInjection: Boolean = false,
) {
    companion object {
        fun text(role: String, content: String, isDepthInjection: Boolean = false): WireChatMessageDto =
            WireChatMessageDto(role = role, content = JsonPrimitive(content), isDepthInjection = isDepthInjection)

        fun parts(
            role: String,
            parts: List<WireChatContentPartDto>,
            isDepthInjection: Boolean = false,
        ): WireChatMessageDto =
            WireChatMessageDto(
                role = role,
                content = AppJson.encodeToJsonElement(parts),
                isDepthInjection = isDepthInjection,
            )
    }
}

@Serializable
data class PromptTokensDetailsDto(
    val cached_tokens: Int? = null,
)

@Serializable
data class ChatTokenUsageDto(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null,
    val cache_read_input_tokens: Int? = null,
    val prompt_cache_hit_tokens: Int? = null,
    val prompt_tokens_details: PromptTokensDetailsDto? = null,
) {
    val effectiveCacheReadTokens: Int?
        get() = cache_read_input_tokens
            ?: prompt_cache_hit_tokens
            ?: prompt_tokens_details?.cached_tokens
}

@Serializable
data class OnlineChatCompletionRequestDto(
    val group_id: Long,
    val model: String,
    val messages: List<WireChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val reasoning_effort: String? = null,
    val frequency_penalty: Double? = null,
    val presence_penalty: Double? = null,
    val repetition_penalty: Double? = null,
    val top_k: Int? = null,
    val min_p: Double? = null,
    val stop: List<String>? = null,
    @kotlinx.serialization.Transient val enablePromptCaching: Boolean = true,
    @kotlinx.serialization.Transient val promptCacheKey: String? = null,
    @kotlinx.serialization.Transient val assistantPrefill: String? = null,
)

// ==================== Online Images DTOs ====================

@Serializable
data class OnlineImageReferenceIdDto(
    val generation_id: String,
    val image_index: Int,
)

@Serializable
data class OnlineImageCanvasDto(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val image_width: Int,
    val image_height: Int,
)

@Serializable
data class OnlineImageCanvasSizeDto(
    val width: Int,
    val height: Int,
    val aspect_ratio: String,
    val resolution: String,
)

@Serializable
data class OnlineImageParameterPricingDto(
    val billing_mode: String? = null,
    val fixed_price_usd: Double? = null,
    val opus_free_tier: Boolean? = null,
    val anlas_usd: Double? = null,
    val profit_multiplier: Double? = null,
)

@Serializable
data class OnlineImageParameterSchemaDto(
    val upstream_model: String? = null,
    val pricing: OnlineImageParameterPricingDto? = null,
)

@Serializable
data class OnlineImageModelPriceHintDto(
    val models: List<String> = emptyList(),
    val billing_mode: String? = null,
    val per_request_price: Double? = null,
    val image_price: Double? = null,
    val tiers: Map<String, Double>? = null,
)

@Serializable
data class OnlineImageKeyPricingDto(
    val rate_multiplier: Double? = null,
    val image_rate_independent: Boolean? = null,
    val image_rate_multiplier: Double? = null,
    val image_price_1k: Double? = null,
    val image_price_2k: Double? = null,
    val image_price_4k: Double? = null,
    val model_prices: List<OnlineImageModelPriceHintDto>? = null,
)

@Serializable
data class OnlineImageModelDto(
    val id: String,
    val provider: String,
    val transport: String,
    val max_count: Int = 4,
    val max_reference_images: Int = 0,
    val aspect_ratios: List<String> = listOf("auto"),
    val resolutions: List<String> = listOf("auto"),
    val supports_text_edit: Boolean = false,
    val supports_mask_edit: Boolean = false,
    val supports_outpaint: Boolean = false,
    val canvas_sizes: List<OnlineImageCanvasSizeDto> = emptyList(),
    val parameter_schema: OnlineImageParameterSchemaDto? = null,
    val free_quota: GroupModelFreeQuotaStatusDto? = null,
)

@Serializable
data class OnlineImageKeyDto(
    val id: Long,
    val name: String,
    val group_id: Long? = null,
    val group_name: String? = null,
    val key_ids: List<Long>? = null,
    val has_free_quotas: Boolean = false,
    val pricing: OnlineImageKeyPricingDto? = null,
    val models: List<OnlineImageModelDto> = emptyList(),
)

@Serializable
data class OnlineImageCapabilitiesDto(
    val enabled: Boolean = false,
    val reason: String? = null,
    val keys: List<OnlineImageKeyDto> = emptyList(),
)

@Serializable
data class OnlineImageModelsResponseDto(
    val models: List<OnlineImageModelDto> = emptyList(),
)

@Serializable
data class OnlineImageAssetDto(
    val index: Int,
    val content_type: String = "image/png",
    val bytes: Long = 0L,
    val thumb_bytes: Long? = null,
    val has_thumb: Boolean? = null,
    val direct_access: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class OnlineImageFailureDto(
    val index: Int = -1,
    val code: String = "",
    val message: String = "",
)

@Serializable
data class OnlineImageGenerationDto(
    val id: String,
    val api_key_id: Long = 0L,
    val provider: String = "openai",
    val transport: String = "",
    val model: String = "",
    val prompt: String = "",
    val aspect_ratio: String = "auto",
    val resolution: String = "auto",
    val requested_count: Int = 1,
    val actual_count: Int = 0,
    val status: String = "processing",
    val queue_position: Int? = null,
    val images: List<OnlineImageAssetDto> = emptyList(),
    val errors: List<OnlineImageFailureDto> = emptyList(),
    val created_at: Long = 0L,
    val updated_at: Long? = null,
    val expires_at: Long = 0L,
    val reference_count: Int? = null,
    val action: String? = null,
    val source_generation_id: String? = null,
    val source_image_index: Int? = null,
    val root_generation_id: String? = null,
    val can_regenerate: Boolean? = null,
    val novelai: JsonObject? = null,
)

@Serializable
data class OnlineImageHistoryDto(
    val items: List<OnlineImageGenerationDto> = emptyList(),
    val total: Int = 0,
    val all_total: Int? = null,
    val has_more: Boolean? = null,
    val next_cursor: String? = null,
    val active_ids: List<String>? = null,
    val limit: Int = 0,
    val retention_days: Int = 7,
    val retention_hours: Int? = null,
)

@Serializable
data class OnlineImageVersionsResponseDto(
    val items: List<OnlineImageGenerationDto> = emptyList(),
)

@Serializable
data class OnlineImageContentUrlDto(
    val url: String = "",
    val content_type: String = "",
    val expires_in: Int = 0,
)

@Serializable
data class OnlineImagePresetDto(
    val id: String,
    val name: String,
    val group_id: Long? = null,
    val api_key_id: Long? = null,
    val provider: String,
    val model: String,
    val aspect_ratio: String = "auto",
    val resolution: String = "auto",
    val requested_count: Int = 1,
    val include_prompt: Boolean = false,
    val prompt: String? = null,
    val novelai: JsonObject? = null,
    val created_at: Long = 0L,
    val updated_at: Long = 0L,
)

@Serializable
data class OnlineImagePresetInputDto(
    val name: String,
    val group_id: Long? = null,
    val api_key_id: Long? = null,
    val provider: String,
    val model: String,
    val aspect_ratio: String = "auto",
    val resolution: String = "auto",
    val requested_count: Int = 1,
    val include_prompt: Boolean = false,
    val prompt: String? = null,
    val novelai: JsonObject? = null,
)

@Serializable
data class OnlineImagePresetListDto(
    val items: List<OnlineImagePresetDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class OnlineImagePresetRenameDto(
    val name: String,
)

@Serializable
data class OnlineImageBatchDownloadRequestDto(
    val ids: List<String>,
)

@Serializable
data class DeleteResultDto(
    val deleted: Boolean = true,
)
