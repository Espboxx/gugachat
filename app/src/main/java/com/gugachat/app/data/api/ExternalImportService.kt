package com.gugachat.app.data.api

import android.content.Context
import androidx.room.withTransaction
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import com.gugachat.app.domain.chat.ExternalImportBundle
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import com.gugachat.app.domain.chat.ExternalImportedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ExternalPersistSummary(
    val conversationsCount: Int,
    val messagesCount: Int,
    val loreCount: Int,
    val chaptersCount: Int,
    val firstConversationId: String?,
    val memoryCount: Int = 0,
)

object ExternalImportService {
    private const val MAX_DOWNLOAD_BYTES = 100 * 1024 * 1024

    internal suspend fun downloadExportBytes(
        httpClient: OkHttpClient,
        rawLinkInput: String,
        acceptHeader: String,
        maxSizeErrorMessage: String,
    ): Pair<ByteArray, String?> {
        val url = DeepSeekImportCodec.extractDownloadUrl(rawLinkInput)
            ?: throw IllegalArgumentException("INVALID_LINK")

        val downloadClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", acceptHeader)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .build()

        val resp = downloadClient.awaitCall(req)
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                if (r.code == 401 || r.code == 403 || r.code == 404 || r.code == 410) {
                    throw DeepSeekLinkAuthRequiredException("HTTP ${r.code}")
                }
                if (!r.isSuccessful) {
                    throw IllegalStateException("下载失败（HTTP ${r.code}）")
                }
                val contentType = r.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html")) {
                    throw DeepSeekLinkAuthRequiredException("HTML login or error page")
                }
                val dispName = r.header("Content-Disposition")
                    ?.let { Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                    ?: url.substringBefore('?').substringAfterLast('/').takeIf { it.contains('.') }
                val body = r.body ?: throw IllegalStateException("空响应内容")
                val bytes = try {
                    body.byteStream().use { ins ->
                        com.gugachat.app.core.util.StreamZipUtils.run {
                            ins.readBoundedBytesOrThrow(
                                maxBytes = MAX_DOWNLOAD_BYTES.toLong(),
                                errorMessage = maxSizeErrorMessage,
                                bufSize = 16384,
                            )
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(e.message ?: maxSizeErrorMessage, e)
                }
                bytes to dispName
            }
        }
    }

    suspend fun downloadAndParseLink(
        httpClient: OkHttpClient,
        rawLinkInput: String,
        hintSource: ExternalImportSource = ExternalImportSource.DEEPSEEK,
    ): ExternalImportBundle {
        val (bytes, fileName) = downloadExportBytes(
            httpClient = httpClient,
            rawLinkInput = rawLinkInput,
            acceptHeader = "application/zip, application/octet-stream, application/json, text/plain, */*",
            maxSizeErrorMessage = "导出文件超过 100MB 限制",
        )

        return withContext(Dispatchers.Default) {
            try {
                ExternalImportCodec.parseBytes(bytes, fileName = fileName, hintSource = hintSource)
            } catch (e: IllegalArgumentException) {
                if (e.message == "HTML_RESPONSE") {
                    throw DeepSeekLinkAuthRequiredException("HTML response body")
                }
                throw e
            }
        }
    }

    suspend fun persistSelected(
        db: AppDatabase,
        selectedItems: List<ExternalImportedItem>,
    ): ExternalPersistSummary {
        if (selectedItems.isEmpty()) {
            return ExternalPersistSummary(0, 0, 0, 0, null, 0)
        }
        val chatDao = db.chatDao()
        val convEntities = selectedItems.map { it.conversation.toEntity() }
        val msgEntities = selectedItems.flatMap { item -> item.messages.map { it.toEntity() } }
        val loreEntities = selectedItems.flatMap { item -> item.loreEntries.map { it.toEntity() } }
        val ledgerEntities = selectedItems.flatMap { item -> item.memoryLedger.map { it.toEntity() } }
        val episodeEntities = selectedItems.flatMap { item -> item.memoryEpisodes.map { it.toEntity() } }
        val chapterEntities = selectedItems
            .filter { it.conversation.mode == ConversationMode.novel }
            .flatMap { item -> item.novelChapters.map { it.toEntity() } }

        withContext(Dispatchers.IO) {
            db.withTransaction {
                chatDao.upsertConversations(convEntities)
                selectedItems.forEach { item ->
                    chatDao.deleteMessagesForConversation(item.conversation.id)
                    if (item.loreEntries.isNotEmpty()) {
                        chatDao.deleteNovelLoreForConversation(item.conversation.id)
                    }
                    if (item.memoryLedger.isNotEmpty()) {
                        chatDao.deleteMemoryLedgerForConversation(item.conversation.id)
                    }
                    if (item.memoryEpisodes.isNotEmpty()) {
                        chatDao.deleteMemoryEpisodesForConversation(item.conversation.id)
                    }
                }
                if (msgEntities.isNotEmpty()) chatDao.upsertMessages(msgEntities)
                if (loreEntities.isNotEmpty()) chatDao.upsertNovelLoreList(loreEntities)
                if (ledgerEntities.isNotEmpty()) chatDao.upsertMemoryLedgerList(ledgerEntities)
                if (episodeEntities.isNotEmpty()) chatDao.upsertMemoryEpisodes(episodeEntities)
                if (chapterEntities.isNotEmpty()) chatDao.upsertNovelChapters(chapterEntities)
            }
        }

        return ExternalPersistSummary(
            conversationsCount = selectedItems.size,
            messagesCount = msgEntities.size,
            loreCount = loreEntities.size,
            chaptersCount = chapterEntities.size,
            firstConversationId = selectedItems.firstOrNull()?.conversation?.id,
            memoryCount = ledgerEntities.size + episodeEntities.size,
        )
    }

    /**
     * Best-effort background downloader for remote NPC avatar/cutout URLs carried by imported saves
     * (e.g. UUGame `avatarCutoutUrl`). Gracefully skips offline, slow, or non-image URLs.
     */
    suspend fun cacheRemoteRpAvatars(
        appContext: Context,
        httpClient: OkHttpClient,
        db: AppDatabase,
        conversationIds: List<String>,
    ) {
        if (conversationIds.isEmpty()) return
        val maxImageBytes = 6 * 1024 * 1024
        val mediaDir = File(appContext.filesDir, "rp_media").apply { if (!exists()) mkdirs() }
        val fetchClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
        val chatDao = db.chatDao()

        withContext(Dispatchers.IO) {
            conversationIds.distinct().forEach { convId ->
                val conv = chatDao.getConversationById(convId)?.toDomain() ?: return@forEach
                val rp = conv.rp ?: return@forEach
                val pending = rp.cast.filter { c ->
                    c.avatarPath.isNullOrBlank() &&
                        !c.avatar.isNullOrBlank() &&
                        (c.avatar.startsWith("https://", ignoreCase = true) || c.avatar.startsWith("http://", ignoreCase = true))
                }
                if (pending.isEmpty()) return@forEach

                val downloaded = HashMap<String, String>()
                pending.forEach { npc ->
                    val url = npc.avatar ?: return@forEach
                    runCatching {
                        val req = Request.Builder()
                            .url(url)
                            .get()
                            .header("Accept", "image/png, image/webp, image/jpeg, image/*")
                            .build()
                        fetchClient.awaitCall(req).use { resp ->
                            if (!resp.isSuccessful) return@use
                            val ctype = resp.header("Content-Type").orEmpty().lowercase()
                            val body = resp.body ?: return@use
                            val bytes = com.gugachat.app.core.util.StreamZipUtils.run {
                                body.byteStream().readBoundedBytesOrNull(maxImageBytes.toLong(), bufSize = 8192)
                            } ?: return@use
                            if (bytes.size < 16) return@use
                            val ext = inferRemoteImageExt(bytes, ctype, url) ?: return@use
                            val safeCharId = npc.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)
                            val safeConv = convId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)
                            val file = File(mediaDir, "imp_${safeConv}_${safeCharId}.$ext")
                            file.writeBytes(bytes)
                            downloaded[npc.id] = com.gugachat.app.core.storage.LocalMediaPathResolver.toRelativePath(
                                appContext.filesDir.absolutePath,
                                file.absolutePath,
                            ) ?: "rp_media/${file.name}"
                        }
                    }
                }

                if (downloaded.isNotEmpty()) {
                    val fresh = chatDao.getConversationById(convId)?.toDomain() ?: return@forEach
                    val freshRp = fresh.rp ?: return@forEach
                    val updatedCast = freshRp.cast.map { c ->
                        val localPath = downloaded[c.id] ?: return@map c
                        c.copy(
                            avatarPath = c.avatarPath?.takeIf { it.isNotBlank() } ?: localPath,
                            spritePath = c.spritePath?.takeIf { it.isNotBlank() } ?: localPath,
                        )
                    }
                    chatDao.upsertConversation(
                        fresh.copy(rp = freshRp.copy(cast = updatedCast)).toEntity(),
                    )
                }
            }
        }
    }

    private fun inferRemoteImageExt(bytes: ByteArray, contentType: String, url: String): String? {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "jpg"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "webp"
        }
        return when {
            contentType.contains("png") -> "png"
            contentType.contains("webp") -> "webp"
            contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
            url.lowercase().substringBefore('?').endsWith(".png") -> "png"
            url.lowercase().substringBefore('?').endsWith(".webp") -> "webp"
            url.lowercase().substringBefore('?').endsWith(".jpg") ||
                url.lowercase().substringBefore('?').endsWith(".jpeg") -> "jpg"
            else -> null
        }
    }
}
