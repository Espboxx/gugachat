package com.gugachat.app.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.LocalMediaPathResolver
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.util.newIdempotencyKey
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.ImageBinaryUpload
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import com.gugachat.app.data.api.OnlineImageSubmitInput
import com.gugachat.app.data.api.OnlineImagesApi
import com.gugachat.app.domain.chat.FreeQuotaUtils
import com.gugachat.app.domain.chat.RpCardCodec
import com.gugachat.app.domain.images.OnlineImageNovelAI
import com.gugachat.app.domain.images.OnlineImageStudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class GeneratedRpMediaResult(
    val relativePath: String,
    val absoluteFile: File,
)

/**
 * Shared image generation and local media storage coordinator for RP portraits/CGs
 * and Novel book covers. Uses [DirectApiClient.supportsImageGeneration] to filter custom
 * endpoints accurately and always returns relative paths via [LocalMediaPathResolver].
 */
class StudioImageGenerationUseCase(
    private val appContext: Context,
    private val authSession: StateFlow<AuthSessionState>,
    private val apiProviderStore: ApiProviderStore,
    private val directApiClient: DirectApiClient,
    private val onlineImagesApi: OnlineImagesApi,
    private val settingsStore: SettingsStore,
) {
    private val filesDir: File
        get() = appContext.filesDir

    suspend fun generateAndSaveRpMedia(
        prompt: String,
        aspectRatio: String,
        referenceFilePath: String? = null,
        prefix: String = "ai",
        noChannelMessage: String = "No available image generation channel",
    ): GeneratedRpMediaResult {
        val isDirectOnly = (authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
        val customKeys = apiProviderStore.enabledProviders()
            .filter { directApiClient.supportsImageGeneration(it) }
            .map { p ->
                OnlineImageKeyDto(
                    id = p.syntheticGroupId,
                    name = "${p.displayTitle()} · ${p.protocol.displayName}",
                    models = runCatching { directApiClient.listImageModels(p) }.getOrDefault(emptyList()),
                )
            }
        val cap = if (isDirectOnly) null else runCatching { onlineImagesApi.capabilities() }.getOrNull()
        val serverKeys = cap?.keys?.let { FreeQuotaUtils.sortImageKeysByFreeQuota(it) }.orEmpty()
        val allKeys = serverKeys + customKeys

        val saved = settingsStore.currentSettings()
        val orderedKeys = buildList {
            allKeys.firstOrNull { it.id == saved.lastImageKeyId && saved.lastImageKeyId != 0L }?.let { add(it) }
            allKeys.forEach { k -> if (none { it.id == k.id }) add(k) }
        }

        var chosenKey: OnlineImageKeyDto? = null
        var chosenModel: OnlineImageModelDto? = null
        for (key in orderedKeys) {
            val fetched = runCatching { onlineImagesApi.models(key.id) }.getOrDefault(emptyList())
            val sortedModels = FreeQuotaUtils.sortImageModelsByFreeQuota(fetched.ifEmpty { key.models })
            if (sortedModels.isNotEmpty()) {
                chosenKey = key
                chosenModel = sortedModels.firstOrNull { it.id == saved.lastImageModel } ?: sortedModels.first()
                break
            }
        }
        if (chosenKey == null || chosenModel == null) {
            throw IllegalStateException(noChannelMessage)
        }

        val effectiveRatio = when {
            chosenModel.aspect_ratios.isEmpty() -> aspectRatio
            aspectRatio in chosenModel.aspect_ratios -> aspectRatio
            "3:4" in chosenModel.aspect_ratios && (aspectRatio == "3:4" || aspectRatio == "9:16") -> "3:4"
            "auto" in chosenModel.aspect_ratios -> "auto"
            else -> chosenModel.aspect_ratios.first()
        }

        val referenceUploads = if (!referenceFilePath.isNullOrBlank() && chosenModel.max_reference_images > 0) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val refFile = LocalMediaPathResolver.resolveFile(filesDir.absolutePath, referenceFilePath)
                    if (refFile != null && refFile.isFile && refFile.length() in 1L..(10L * 1024L * 1024L)) {
                        val rawBytes = refFile.readBytes()
                        val isPng = RpCardCodec.isPngBytes(rawBytes)
                        listOf(
                            ImageBinaryUpload(
                                fileName = if (isPng) "rp_ref.png" else "rp_ref.jpg",
                                mimeType = if (isPng) "image/png" else "image/jpeg",
                                bytes = rawBytes,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
            }
        } else {
            emptyList()
        }

        val naiJson = if (OnlineImageStudioUtils.isNovelAIModel(chosenModel)) {
            val defaultNai = OnlineImageNovelAI.defaultParamsForVersion(OnlineImageNovelAI.detectVersion(chosenModel.id))
            OnlineImageNovelAI.buildNovelAIPayloadJsonObject(
                prompt = prompt,
                params = defaultNai,
                modelId = chosenModel.id,
                hasReferenceImages = referenceUploads.isNotEmpty(),
                isImg2Img = false,
            )
        } else {
            null
        }

        var current = onlineImagesApi.submit(
            input = OnlineImageSubmitInput(
                apiKeyId = chosenKey.id,
                provider = chosenModel.provider,
                transport = chosenModel.transport,
                model = chosenModel.id,
                prompt = prompt,
                count = 1,
                aspectRatio = effectiveRatio,
                resolution = "auto",
                referenceImages = referenceUploads,
                novelai = naiJson,
            ),
            idempotencyKey = newIdempotencyKey(),
        )

        val deadline = System.currentTimeMillis() + 60_000L
        while (current.status.lowercase() in setOf("pending", "queued", "processing", "running") &&
            System.currentTimeMillis() < deadline
        ) {
            delay(1500L)
            current = onlineImagesApi.get(current.id)
        }

        val finalStatus = current.status.lowercase()
        if (finalStatus != "completed" && current.images.isEmpty()) {
            val errMsg = current.errors.firstOrNull()?.message?.takeIf { it.isNotBlank() }
                ?: "Image generation $finalStatus"
            throw IllegalStateException(errMsg)
        }

        val bytes = onlineImagesApi.contentBytes(current.id, 0, variant = "original")
        if (bytes.isEmpty()) throw IllegalStateException("Empty generated image")

        return saveMediaBytes(bytes = bytes, prefix = prefix, ext = "png")
    }

    suspend fun saveMediaBytes(
        bytes: ByteArray,
        prefix: String,
        ext: String,
    ): GeneratedRpMediaResult = withContext(Dispatchers.IO) {
        val dir = File(filesDir, "rp_media")
        if (!dir.exists()) dir.mkdirs()
        val safeExt = ext.trim().lowercase().ifBlank { "png" }
        val outFile = File(dir, "${prefix}_${UUID.randomUUID()}.$safeExt")
        outFile.writeBytes(bytes)
        val relPath = LocalMediaPathResolver.toRelativePath(filesDir.absolutePath, outFile.absolutePath)
            ?: "rp_media/${outFile.name}"
        GeneratedRpMediaResult(relativePath = relPath, absoluteFile = outFile)
    }

    fun saveMediaBytesSync(
        bytes: ByteArray,
        prefix: String,
        ext: String,
    ): String {
        val dir = File(filesDir, "rp_media")
        if (!dir.exists()) dir.mkdirs()
        val safeExt = ext.trim().lowercase().ifBlank { "png" }
        val outFile = File(dir, "${prefix}_${UUID.randomUUID()}.$safeExt")
        outFile.writeBytes(bytes)
        return LocalMediaPathResolver.toRelativePath(filesDir.absolutePath, outFile.absolutePath)
            ?: "rp_media/${outFile.name}"
    }

    fun resolveMediaFile(storedPath: String?): File? =
        LocalMediaPathResolver.resolveFile(filesDir.absolutePath, storedPath)?.takeIf { it.isFile }

    fun transcodeToPngBytes(rawBytes: ByteArray): ByteArray? = runCatching {
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@runCatching null
        val maxSide = 1024
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
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    fun inferImageExt(mimeType: String?): String = when (mimeType?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}
