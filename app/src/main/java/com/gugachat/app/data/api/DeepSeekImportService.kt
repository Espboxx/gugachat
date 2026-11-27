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

class DeepSeekLinkAuthRequiredException(message: String) : Exception(message)

data class DeepSeekPersistSummary(
    val conversationsCount: Int,
    val messagesCount: Int,
    val firstConversationId: String?,
)

object DeepSeekImportService {
    suspend fun downloadAndParseLink(
        httpClient: OkHttpClient,
        rawLinkInput: String,
    ): DeepSeekImportBundle {
        val (bytes, _) = ExternalImportService.downloadExportBytes(
            httpClient = httpClient,
            rawLinkInput = rawLinkInput,
            acceptHeader = "application/zip, application/octet-stream, application/json, */*",
            maxSizeErrorMessage = "导出压缩包超过 100MB 限制",
        )

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
                selectedItems.forEach { item ->
                    chatDao.deleteMessagesForConversation(item.conversation.id)
                }
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
