package com.gugachat.app.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ImageBinaryUpload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class OnlineImageSubmitInput(
    val apiKeyId: Long,
    val provider: String,
    val transport: String? = null,
    val model: String,
    val prompt: String,
    val count: Int = 1,
    val aspectRatio: String = "auto",
    val resolution: String = "auto",
    val referenceImages: List<ImageBinaryUpload> = emptyList(),
    val action: String? = null,
    val sourceGenerationId: String? = null,
    val sourceImageIndex: Int? = null,
    val referenceAssets: List<OnlineImageReferenceIdDto> = emptyList(),
    val mask: ImageBinaryUpload? = null,
    val canvas: OnlineImageCanvasDto? = null,
    val novelai: JsonObject? = null,
)

data class OnlineImageListParams(
    val search: String? = null,
    val filter: String? = null,
    val pageSize: Int? = null,
    val cursor: String? = null,
)

class OnlineImagesApi(private val client: ApiClient) {
    private val base = "/online-images"

    suspend fun capabilities(): OnlineImageCapabilitiesDto =
        client.get("$base/capabilities", OnlineImageCapabilitiesDto.serializer())

    suspend fun models(apiKeyId: Long): List<OnlineImageModelDto> {
        require(apiKeyId != 0L) { "api_key_id must not be 0" }
        return client.get(
            path = "$base/models",
            queryParams = mapOf("api_key_id" to apiKeyId.toString()),
            deserializer = OnlineImageModelsResponseDto.serializer(),
        ).models
    }

    suspend fun submit(input: OnlineImageSubmitInput, idempotencyKey: String): OnlineImageGenerationDto {
        require(input.apiKeyId != 0L) { "api_key_id must not be 0" }
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("api_key_id", input.apiKeyId.toString())
            .addFormDataPart("provider", input.provider)
            .addFormDataPart("model", input.model)
            .addFormDataPart("prompt", input.prompt)
            .addFormDataPart("count", input.count.toString())
            .addFormDataPart("aspect_ratio", input.aspectRatio)
            .addFormDataPart("resolution", input.resolution)

        if (!input.transport.isNullOrBlank()) {
            builder.addFormDataPart("transport", input.transport)
        }
        if (!input.action.isNullOrBlank()) {
            builder.addFormDataPart("action", input.action)
        }
        if (!input.sourceGenerationId.isNullOrBlank()) {
            builder.addFormDataPart("source_generation_id", input.sourceGenerationId)
        }
        if (input.sourceImageIndex != null) {
            builder.addFormDataPart("source_image_index", input.sourceImageIndex.toString())
        }
        if (input.referenceAssets.isNotEmpty()) {
            builder.addFormDataPart("reference_assets", AppJson.encodeToString(input.referenceAssets))
        }
        if (input.canvas != null) {
            builder.addFormDataPart("canvas", AppJson.encodeToString(OnlineImageCanvasDto.serializer(), input.canvas))
        }
        if (input.novelai != null) {
            builder.addFormDataPart("novelai", AppJson.encodeToString(JsonObject.serializer(), input.novelai))
        }
        if (input.mask != null) {
            builder.addFormDataPart(
                "mask",
                input.mask.fileName,
                input.mask.bytes.toRequestBody(input.mask.mimeType.toMediaType()),
            )
        }
        for (ref in input.referenceImages) {
            builder.addFormDataPart(
                "reference_images[]",
                ref.fileName,
                ref.bytes.toRequestBody(ref.mimeType.toMediaType()),
            )
        }

        return client.postMultipart(
            path = "$base/generations",
            body = builder.build(),
            headers = mapOf("Idempotency-Key" to idempotencyKey),
            deserializer = OnlineImageGenerationDto.serializer(),
        )
    }

    suspend fun regenerate(id: String): OnlineImageGenerationDto =
        client.postJson(
            path = "$base/generations/${encodeSegment(id)}/regenerate",
            body = "{}",
            deserializer = OnlineImageGenerationDto.serializer(),
        )

    suspend fun list(params: OnlineImageListParams = OnlineImageListParams()): OnlineImageHistoryDto {
        val qp = buildMap {
            if (!params.search.isNullOrBlank()) put("search", params.search)
            if (!params.filter.isNullOrBlank() && params.filter != "all") put("filter", params.filter)
            if (params.pageSize != null && params.pageSize > 0) put("page_size", params.pageSize.toString())
            if (!params.cursor.isNullOrBlank()) put("cursor", params.cursor)
        }
        return client.get("$base/generations", OnlineImageHistoryDto.serializer(), queryParams = qp)
    }

    suspend fun get(id: String): OnlineImageGenerationDto =
        client.get("$base/generations/${encodeSegment(id)}", OnlineImageGenerationDto.serializer())

    suspend fun remove(id: String) {
        client.delete("$base/generations/${encodeSegment(id)}", DeleteResultDto.serializer())
    }

    suspend fun versions(id: String): List<OnlineImageGenerationDto> =
        client.get("$base/generations/${encodeSegment(id)}/versions", OnlineImageVersionsResponseDto.serializer()).items

    suspend fun contentUrl(id: String, index: Int, variant: String = "original"): OnlineImageContentUrlDto {
        val qp = if (variant == "thumb") mapOf("variant" to "thumb") else emptyMap()
        return client.get(
            path = "$base/generations/${encodeSegment(id)}/images/$index/url",
            deserializer = OnlineImageContentUrlDto.serializer(),
            queryParams = qp,
        )
    }

    suspend fun contentBytes(
        id: String,
        index: Int,
        variant: String = "original",
        revision: Long? = null,
    ): ByteArray {
        val qp = buildMap {
            if (variant == "thumb") put("variant", "thumb")
            if (revision != null && revision != 0L) put("v", revision.toString())
        }
        return client.getBytesAbsoluteOrApi(
            urlOrPath = "$base/generations/${encodeSegment(id)}/images/$index/content",
            queryParams = qp,
            attachAuth = true,
        )
    }

    suspend fun downloadAllZip(id: String): ByteArray =
        client.getBytesAbsoluteOrApi("$base/generations/${encodeSegment(id)}/download", attachAuth = true)

    suspend fun batchDownloadZip(ids: List<String>): ByteArray {
        val body = AppJson.encodeToString(
            OnlineImageBatchDownloadRequestDto.serializer(),
            OnlineImageBatchDownloadRequestDto(ids),
        )
        val resp = client.executeRaw(
            method = "POST",
            path = "$base/generations/batch-download",
            body = body.toRequestBody(ApiClient.JSON_MEDIA_TYPE),
            client = client.streamingClient,
        )
        return resp.use {
            if (!it.isSuccessful) {
                throw client.parseHttpError(it.code, it.body?.string().orEmpty())
            }
            it.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun listPresets(): OnlineImagePresetListDto =
        client.get("$base/presets", OnlineImagePresetListDto.serializer())

    suspend fun createPreset(input: OnlineImagePresetInputDto): OnlineImagePresetDto =
        client.postJson(
            path = "$base/presets",
            body = AppJson.encodeToString(OnlineImagePresetInputDto.serializer(), input),
            deserializer = OnlineImagePresetDto.serializer(),
        )

    suspend fun updatePreset(id: String, input: OnlineImagePresetInputDto): OnlineImagePresetDto =
        client.putJson(
            path = "$base/presets/${encodeSegment(id)}",
            body = AppJson.encodeToString(OnlineImagePresetInputDto.serializer(), input),
            deserializer = OnlineImagePresetDto.serializer(),
        )

    suspend fun renamePreset(id: String, name: String): OnlineImagePresetDto =
        client.patchJson(
            path = "$base/presets/${encodeSegment(id)}",
            body = AppJson.encodeToString(OnlineImagePresetRenameDto.serializer(), OnlineImagePresetRenameDto(name)),
            deserializer = OnlineImagePresetDto.serializer(),
        )

    suspend fun deletePreset(id: String) {
        client.delete("$base/presets/${encodeSegment(id)}", DeleteResultDto.serializer())
    }

    fun contentEndpointPath(id: String, index: Int, variant: String = "original", revision: Long? = null): String {
        val params = buildList {
            if (variant == "thumb") add("variant=thumb")
            if (revision != null && revision != 0L) add("v=$revision")
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$base/generations/${encodeSegment(id)}/images/$index/content$query"
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
