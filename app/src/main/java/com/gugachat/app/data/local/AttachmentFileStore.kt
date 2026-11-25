package com.gugachat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

class AttachmentFileStore(
    private val context: Context,
    private val attachmentDao: AttachmentDao,
) {
    private val dir: File by lazy {
        File(context.applicationContext.filesDir, "chat_attachments").apply { mkdirs() }
    }

    companion object {
        const val MAX_DIMENSION_PX = 2048
        const val MAX_FILE_BYTES = 6 * 1024 * 1024
    }

    suspend fun saveFromUri(uri: Uri): AttachmentMetaEntity? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        saveCompressedImageBytes(rawBytes)
    }

    suspend fun saveCompressedImageBytes(rawBytes: ByteArray): AttachmentMetaEntity? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        var curW = bounds.outWidth
        var curH = bounds.outHeight
        while (max(curW, curH) > MAX_DIMENSION_PX * 2) {
            sample *= 2
            curW /= 2
            curH /= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return@withContext null

        val maxSide = max(decoded.width, decoded.height)
        val scaled = if (maxSide > MAX_DIMENSION_PX) {
            val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
            val targetW = max(1, (decoded.width * scale).toInt())
            val targetH = max(1, (decoded.height * scale).toInt())
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val compressedBytes = compressWithinLimit(scaled)
        val w = scaled.width
        val h = scaled.height
        scaled.recycle()

        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.jpg")
        file.writeBytes(compressedBytes)

        val meta = AttachmentMetaEntity(
            id = id,
            mimeType = "image/jpeg",
            width = w,
            height = h,
            sizeBytes = compressedBytes.size.toLong(),
            localPath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
        )
        attachmentDao.upsert(meta)
        meta
    }

    suspend fun readBytes(attachmentId: String): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val meta = attachmentDao.getById(attachmentId) ?: return@withContext null
        val f = File(meta.localPath)
        if (!f.exists()) return@withContext null
        meta.mimeType to f.readBytes()
    }

    suspend fun resolveDataUrl(attachmentId: String): String? = withContext(Dispatchers.IO) {
        val (mime, bytes) = readBytes(attachmentId) ?: return@withContext null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:$mime;base64,$b64"
    }

    suspend fun deleteAttachment(attachmentId: String) = withContext(Dispatchers.IO) {
        val meta = attachmentDao.getById(attachmentId)
        if (meta != null) {
            runCatching { File(meta.localPath).delete() }
            attachmentDao.deleteById(attachmentId)
        }
    }

    private fun compressWithinLimit(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        var quality = 90
        while (quality >= 40) {
            baos.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (baos.size() <= MAX_FILE_BYTES) {
                return baos.toByteArray()
            }
            quality -= 10
        }
        return baos.toByteArray()
    }
}
