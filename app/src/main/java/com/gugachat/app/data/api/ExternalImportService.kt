package com.gugachat.app.data.api

import androidx.room.withTransaction
import com.gugachat.app.data.local.AppDatabase
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ExternalPersistSummary(
    val conversationsCount: Int,
    val messagesCount: Int,
    val loreCount: Int,
    val chaptersCount: Int,
    val firstConversationId: String?,
)

object ExternalImportService {
    private const val MAX_DOWNLOAD_BYTES = 100 * 1024 * 1024

    suspend fun downloadAndParseLink(
        httpClient: OkHttpClient,
        rawLinkInput: String,
        hintSource: ExternalImportSource = ExternalImportSource.DEEPSEEK,
    ): ExternalImportBundle {
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
            .header("Accept", "application/zip, application/octet-stream, application/json, text/plain, */*")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .build()

        val resp = downloadClient.awaitCall(req)
        val (bytes, fileName) = withContext(Dispatchers.IO) {
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
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16384)
                var total = 0
                body.byteStream().use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw IllegalStateException("导出文件超过 100MB 限制")
                        }
                        out.write(buf, 0, n)
                    }
                }
                out.toByteArray() to dispName
            }
        }

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
            return ExternalPersistSummary(0, 0, 0, 0, null)
        }
        val chatDao = db.chatDao()
        val convEntities = selectedItems.map { it.conversation.toEntity() }
        val msgEntities = selectedItems.flatMap { item -> item.messages.map { it.toEntity() } }
        val loreEntities = selectedItems.flatMap { item -> item.loreEntries.map { it.toEntity() } }
        val chapterEntities = selectedItems
            .filter { it.conversation.mode == ConversationMode.novel }
            .flatMap { item -> item.novelChapters.map { it.toEntity() } }

        withContext(Dispatchers.IO) {
            db.withTransaction {
                chatDao.upsertConversations(convEntities)
                selectedItems.forEach { item ->
                    chatDao.deleteMessagesForConversation(item.conversation.id)
                }
                if (msgEntities.isNotEmpty()) chatDao.upsertMessages(msgEntities)
                if (loreEntities.isNotEmpty()) chatDao.upsertNovelLoreList(loreEntities)
                if (chapterEntities.isNotEmpty()) chatDao.upsertNovelChapters(chapterEntities)
            }
        }

        return ExternalPersistSummary(
            conversationsCount = selectedItems.size,
            messagesCount = msgEntities.size,
            loreCount = loreEntities.size,
            chaptersCount = chapterEntities.size,
            firstConversationId = selectedItems.firstOrNull()?.conversation?.id,
        )
    }
}
