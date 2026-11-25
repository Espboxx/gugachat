package com.gugachat.app.data.api

import androidx.room.withTransaction
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.toEntity
import com.gugachat.app.domain.chat.DeepSeekImportBundle
import com.gugachat.app.domain.chat.DeepSeekImportCodec
import com.gugachat.app.domain.chat.DeepSeekImportedConversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class DeepSeekLinkAuthRequiredException(message: String) : Exception(message)

data class DeepSeekPersistSummary(
    val conversationsCount: Int,
    val messagesCount: Int,
    val firstConversationId: String?,
)

object DeepSeekImportService {
    private const val MAX_DOWNLOAD_BYTES = 100 * 1024 * 1024

    suspend fun downloadAndParseLink(
        httpClient: OkHttpClient,
        rawLinkInput: String,
    ): DeepSeekImportBundle {
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
            .header("Accept", "application/zip, application/octet-stream, application/json, */*")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .build()

        val resp = downloadClient.awaitCall(req)
        val bytes = withContext(Dispatchers.IO) {
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
                            throw IllegalStateException("导出压缩包超过 100MB 限制")
                        }
                        out.write(buf, 0, n)
                    }
                }
                out.toByteArray()
            }
        }

        return withContext(Dispatchers.Default) {
            try {
                DeepSeekImportCodec.parseBytes(bytes)
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
        selectedItems: List<DeepSeekImportedConversation>,
    ): DeepSeekPersistSummary {
        if (selectedItems.isEmpty()) {
            return DeepSeekPersistSummary(0, 0, null)
        }
        val chatDao = db.chatDao()
        val convEntities = selectedItems.map { it.conversation.toEntity() }
        val msgEntities = selectedItems.flatMap { item -> item.messages.map { it.toEntity() } }

        withContext(Dispatchers.IO) {
            db.withTransaction {
                chatDao.upsertConversations(convEntities)
                if (msgEntities.isNotEmpty()) {
                    chatDao.upsertMessages(msgEntities)
                }
            }
        }

        return DeepSeekPersistSummary(
            conversationsCount = selectedItems.size,
            messagesCount = msgEntities.size,
            firstConversationId = selectedItems.firstOrNull()?.conversation?.id,
        )
    }
}
