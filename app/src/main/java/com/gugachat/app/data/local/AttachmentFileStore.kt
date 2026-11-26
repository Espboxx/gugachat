package com.gugachat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.gugachat.app.domain.chat.AttachmentKind
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.FileInjectMode
import com.gugachat.app.domain.chat.FileTextExtractor
import com.gugachat.app.domain.chat.PdfExtractionResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

sealed interface AttachmentSaveResult {
    data class Success(val meta: ChatAttachmentMeta) : AttachmentSaveResult
    data class ScannedPdf(val fileName: String, val pageCount: Int, val uri: Uri) : AttachmentSaveResult
    data class Error(val message: String) : AttachmentSaveResult
}

class AttachmentFileStore(
    private val context: Context,
    private val attachmentDao: AttachmentDao,
) {
    private val dir: File by lazy {
        File(context.applicationContext.filesDir, "chat_attachments").apply { mkdirs() }
    }

    private val cameraCacheDir: File by lazy {
        File(context.applicationContext.cacheDir, "camera_captures").apply { mkdirs() }
    }

    companion object {
        const val MAX_DIMENSION_PX = 2048
        const val MAX_FILE_BYTES = 6 * 1024 * 1024
        const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
        const val MAX_SCANNED_PDF_PAGES = 4
    }

    fun createCameraTempFile(): Pair<File, Uri> {
        val file = File(cameraCacheDir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(
            context.applicationContext,
            "${context.applicationContext.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    suspend fun saveFromUri(uri: Uri): AttachmentMetaEntity? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val rawBytes = readBoundedBytes(uri, MAX_DOCUMENT_BYTES) ?: return@withContext null
        saveCompressedImageBytes(rawBytes, fileName = displayName)
    }

    suspend fun saveDocumentFromUri(uri: Uri): AttachmentSaveResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val fileName = queryDisplayName(uri).ifBlank { "document.txt" }
            val mimeType = resolver.getType(uri).orEmpty()
            val declaredSize = queryFileSize(uri)
            if (declaredSize > MAX_DOCUMENT_BYTES) {
                return@withContext AttachmentSaveResult.Error("文件超过 25 MB 大小上限")
            }

            val rawBytes = readBoundedBytes(uri, MAX_DOCUMENT_BYTES)
                ?: return@withContext AttachmentSaveResult.Error("无法读取文件或文件超过 25 MB 上限")
            if (rawBytes.isEmpty()) {
                return@withContext AttachmentSaveResult.Error("文件内容为空")
            }

            // If user picked an image via document picker, route seamlessly to image pipeline
            if (FileTextExtractor.isImageFile(fileName, mimeType)) {
                val imgEntity = saveCompressedImageBytes(rawBytes, fileName = fileName)
                    ?: return@withContext AttachmentSaveResult.Error("无法解码该图片")
                return@withContext AttachmentSaveResult.Success(imgEntity.toDomain())
            }

            val extracted = FileTextExtractor.extractFromBytes(
                bytes = rawBytes,
                fileName = fileName,
                mimeType = mimeType,
                pdfExtractor = ::extractPdfWithPdfBox,
            )

            if (extracted.isLikelyScannedPdf) {
                return@withContext AttachmentSaveResult.ScannedPdf(
                    fileName = fileName,
                    pageCount = extracted.pageCount,
                    uri = uri,
                )
            }

            if (extracted.text.isBlank()) {
                return@withContext AttachmentSaveResult.Error("未能从「$fileName」提取出有效文本内容")
            }

            val id = UUID.randomUUID().toString()
            val textFile = File(dir, "$id.txt")
            textFile.writeText(extracted.text, Charsets.UTF_8)

            val entity = AttachmentMetaEntity(
                id = id,
                mimeType = mimeType.ifBlank { inferMimeFromName(fileName) },
                width = 0,
                height = 0,
                sizeBytes = rawBytes.size.toLong(),
                localPath = textFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                kind = AttachmentKind.file.name,
                fileName = fileName,
                textPath = textFile.absolutePath,
                charCount = extracted.charCount,
                estimatedTokens = extracted.estimatedTokens,
                pageCount = extracted.pageCount,
                previewSnippet = extracted.previewSnippet,
                extractionNote = extracted.extractionNote,
                injectMode = FileInjectMode.auto.name,
            )
            attachmentDao.upsert(entity)
            AttachmentSaveResult.Success(entity.toDomain())
        } catch (e: Throwable) {
            AttachmentSaveResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "文件解析失败")
        }
    }

    suspend fun renderScannedPdfPagesAsImages(
        uri: Uri,
        maxPages: Int = MAX_SCANNED_PDF_PAGES,
    ): List<ChatAttachmentMeta> = withContext(Dispatchers.IO) {
        val tempPdf = File(cameraCacheDir, "scan_${UUID.randomUUID()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                FileOutputStream(tempPdf).use { fos -> ins.copyTo(fos) }
            } ?: return@withContext emptyList()

            val pfd = android.os.ParcelFileDescriptor.open(tempPdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val rendered = ArrayList<ChatAttachmentMeta>()
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = min(renderer.pageCount, maxPages.coerceIn(1, 6))
                    val baseName = queryDisplayName(uri).substringBeforeLast('.').ifBlank { "pdf" }
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val targetW = 1280
                            val scale = targetW.toFloat() / max(1, page.width).toFloat()
                            val targetH = max(1, (page.height * scale).toInt()).coerceAtMost(2048)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            try {
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val baos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.JPEG, 86, baos)
                                val saved = saveCompressedImageBytes(
                                    rawBytes = baos.toByteArray(),
                                    fileName = "${baseName}_p${i + 1}.jpg",
                                )
                                if (saved != null) rendered.add(saved.toDomain())
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            rendered
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { tempPdf.delete() }
        }
    }

    suspend fun saveCompressedImageBytes(
        rawBytes: ByteArray,
        fileName: String = "",
    ): AttachmentMetaEntity? = withContext(Dispatchers.IO) {
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
            kind = AttachmentKind.image.name,
            fileName = fileName.ifBlank { "image_${id.take(6)}.jpg" },
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
        val meta = attachmentDao.getById(attachmentId) ?: return@withContext null
        if (!meta.kind.equals(AttachmentKind.image.name, ignoreCase = true)) return@withContext null
        val f = File(meta.localPath)
        if (!f.exists()) return@withContext null
        val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        "data:${meta.mimeType};base64,$b64"
    }

    suspend fun readExtractedText(attachmentId: String): String? = withContext(Dispatchers.IO) {
        val meta = attachmentDao.getById(attachmentId) ?: return@withContext null
        val path = meta.textPath.ifBlank { meta.localPath }
        val f = File(path)
        if (!f.isFile) return@withContext null
        runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    }

    suspend fun loadAttachmentsMap(ids: Collection<String>): Map<String, ChatAttachmentMeta> = withContext(Dispatchers.IO) {
        val unique = ids.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty()) return@withContext emptyMap()
        unique.chunked(200)
            .flatMap { chunk -> attachmentDao.getByIds(chunk) }
            .associate { it.id to it.toDomain() }
    }

    suspend fun updateInjectMode(attachmentId: String, mode: FileInjectMode) = withContext(Dispatchers.IO) {
        attachmentDao.updateInjectMode(attachmentId, mode.name)
    }

    suspend fun deleteAttachment(attachmentId: String) = withContext(Dispatchers.IO) {
        val meta = attachmentDao.getById(attachmentId)
        if (meta != null) {
            runCatching { File(meta.localPath).delete() }
            if (meta.textPath.isNotBlank() && meta.textPath != meta.localPath) {
                runCatching { File(meta.textPath).delete() }
            }
            attachmentDao.deleteById(attachmentId)
        }
    }

    private fun extractPdfWithPdfBox(bytes: ByteArray): PdfExtractionResult {
        PDDocument.load(bytes).use { doc ->
            val pageCount = doc.numberOfPages
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val sb = StringBuilder()
            var truncated = false
            for (page in 1..pageCount) {
                if (sb.length >= FileTextExtractor.MAX_EXTRACTED_CHARS) {
                    truncated = true
                    break
                }
                stripper.startPage = page
                stripper.endPage = page
                val pageText = runCatching { stripper.getText(doc) }.getOrDefault("").trim()
                if (pageText.isNotEmpty()) {
                    sb.appendLine("[第 $page 页]")
                    sb.appendLine(pageText)
                    sb.appendLine()
                }
            }
            return PdfExtractionResult(
                text = sb.toString(),
                pageCount = pageCount,
                truncated = truncated,
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = context.contentResolver
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name.trim()
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
    }

    private fun queryFileSize(uri: Uri): Long {
        val resolver = context.contentResolver
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        }
        return -1L
    }

    private fun readBoundedBytes(uri: Uri, maxBytes: Long): ByteArray? {
        val ins = context.contentResolver.openInputStream(uri) ?: return null
        ins.use { stream ->
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBytes) return null
                baos.write(buf, 0, n)
            }
            return baos.toByteArray()
        }
    }

    private fun inferMimeFromName(fileName: String): String = when (FileTextExtractor.extensionOf(fileName)) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "epub" -> "application/epub+zip"
        "md", "markdown" -> "text/markdown"
        "json", "jsonl" -> "application/json"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        else -> "text/plain"
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
