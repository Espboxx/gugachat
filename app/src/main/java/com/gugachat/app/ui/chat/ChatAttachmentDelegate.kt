package com.gugachat.app.ui.chat

import android.net.Uri
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.data.local.AttachmentSaveResult
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.FileInjectMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles draft image/document attachments, scanned PDF rendering fallback, and injection mode
 * toggles on behalf of [ChatViewModel].
 */
internal class ChatAttachmentDelegate(private val host: ChatViewModelHost) {

    fun addImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        host.scope.launch {
            val s = host.strings()
            val maxImages = MAX_IMAGES
            val currentImgCount = host.uiFlow.value.draftAttachments.count { it.isImage }
            val slots = (maxImages - currentImgCount).coerceAtLeast(0)
            if (slots <= 0) {
                host.container.toastController.show(s.chat.imageLimitHint.fmt("max" to maxImages), isError = true)
                return@launch
            }
            host.uiFlow.update { it.copy(isImportingAttachment = true) }
            try {
                val added = ArrayList<ChatAttachmentMeta>()
                var failedCount = 0
                for (uri in uris.take(slots)) {
                    val entity = host.container.attachmentStore.saveFromUri(uri)
                    if (entity != null) {
                        added.add(entity.toDomain())
                    } else {
                        failedCount++
                    }
                }
                if (added.isNotEmpty()) {
                    appendDraftAttachments(added)
                }
                if (uris.size > slots) {
                    host.container.toastController.show(s.chat.imageLimitHint.fmt("max" to maxImages), isError = true)
                } else if (failedCount > 0 && added.isEmpty()) {
                    host.container.toastController.show(s.chat.invalidImage, isError = true)
                }
            } finally {
                host.uiFlow.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    fun addFileUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        host.scope.launch {
            val s = host.strings()
            var fileSlots = (MAX_FILES - host.uiFlow.value.draftAttachments.count { it.isFile }).coerceAtLeast(0)
            var imageSlots = (MAX_IMAGES - host.uiFlow.value.draftAttachments.count { it.isImage }).coerceAtLeast(0)
            if (fileSlots <= 0 && imageSlots <= 0) {
                host.container.toastController.show(s.attachments.fileLimitHint.fmt("max" to MAX_FILES), isError = true)
                return@launch
            }
            host.uiFlow.update { it.copy(isImportingAttachment = true) }
            try {
                val added = ArrayList<ChatAttachmentMeta>()
                var firstError: String? = null
                var hitLimit = false
                for (uri in uris) {
                    when (val res = host.container.attachmentStore.saveDocumentFromUri(uri)) {
                        is AttachmentSaveResult.Success -> {
                            val meta = res.meta
                            if (meta.isImage) {
                                if (imageSlots > 0) {
                                    added.add(meta)
                                    imageSlots--
                                } else {
                                    hitLimit = true
                                }
                            } else {
                                if (fileSlots > 0) {
                                    added.add(meta)
                                    fileSlots--
                                } else {
                                    hitLimit = true
                                }
                            }
                        }
                        is AttachmentSaveResult.ScannedPdf -> {
                            host.uiFlow.update {
                                it.copy(
                                    pendingScannedPdf = PendingScannedPdf(
                                        fileName = res.fileName,
                                        pageCount = res.pageCount,
                                        uri = res.uri,
                                    ),
                                )
                            }
                        }
                        is AttachmentSaveResult.Error -> {
                            if (firstError == null) firstError = res.message
                        }
                    }
                }
                if (added.isNotEmpty()) {
                    appendDraftAttachments(added)
                    scheduleFileAttachmentIndexing(added.filter { it.isFile })
                }
                if (firstError != null) {
                    host.container.toastController.show(firstError, isError = true)
                } else if (hitLimit) {
                    host.container.toastController.show(s.attachments.fileLimitHint.fmt("max" to MAX_FILES), isError = true)
                }
            } finally {
                host.uiFlow.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    fun updateDraftAttachmentInjectMode(id: String, mode: FileInjectMode) =
        updateSentAttachmentInjectMode(id, mode)

    /**
     * Updates a file attachment's injection mode both for draft and already-sent attachments,
     * persists to Room (`attachment_meta.injectMode`), and triggers live waterline recalculation.
     */
    fun updateSentAttachmentInjectMode(id: String, mode: FileInjectMode) {
        host.uiFlow.update { st ->
            val updatedDraft = st.draftAttachments.map { if (it.id == id) it.copy(injectMode = mode) else it }
            val existing = st.attachmentsById[id]
            val updatedMap = if (existing != null) {
                st.attachmentsById + (id to existing.copy(injectMode = mode))
            } else {
                st.attachmentsById
            }
            st.copy(draftAttachments = updatedDraft, attachmentsById = updatedMap)
        }
        host.scope.launch {
            host.container.attachmentStore.updateInjectMode(id, mode)
            val meta = host.uiFlow.value.attachmentsById[id]
                ?: host.uiFlow.value.draftAttachments.firstOrNull { it.id == id }
            if (meta != null && meta.isFile) {
                host.container.attachmentStore.loadOrBuildDocumentIndex(meta)
                if (mode == FileInjectMode.retrieve || mode == FileInjectMode.auto) {
                    scheduleFileAttachmentIndexing(listOf(meta))
                }
            }
            host.scheduleWaterline()
        }
    }

    /**
     * Preloads overlapping chunk indexes into [AttachmentFileStore.chunkCache] for active files
     * so live waterline & Recall Inspector match send-time file recall 1:1 with zero UI jank.
     */
    fun preloadChunkCache(attachments: Collection<ChatAttachmentMeta>) {
        val fileMetas = attachments.filter { it.isFile }
        if (fileMetas.isEmpty()) return
        host.scope.launch {
            val loaded = host.container.attachmentStore.preloadDocumentIndexes(fileMetas)
            if (loaded.isNotEmpty()) {
                host.scheduleWaterline()
                scheduleFileAttachmentIndexing(fileMetas)
            }
        }
    }

    /**
     * Deletes `"file:$attId"` vectors (and evicts chunk cache) only for attachment IDs no longer
     * referenced by any remaining conversation message (protecting forked/branched chats).
     */
    suspend fun cleanupOrphanFileVectors(
        candidateAttachmentIds: Collection<String>,
        excludingConversationId: String? = null,
    ) {
        val candidates = candidateAttachmentIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (candidates.isEmpty()) return
        val referencedElsewhere = withContext(Dispatchers.IO) {
            val allMsgs = host.database.chatDao().getAllMessages()
            val refs = HashSet<String>()
            for (entity in allMsgs) {
                if (excludingConversationId != null && entity.conversationId == excludingConversationId) continue
                val domain = entity.toDomain()
                if (domain.deletedAt != null) continue
                for (attId in domain.attachmentIds) {
                    if (attId in candidates) refs.add(attId)
                }
            }
            refs
        }
        for (attId in candidates) {
            if (attId !in referencedElsewhere) {
                host.container.retrievalCoordinator.deleteFileVectors(attId)
                host.container.attachmentStore.chunkCache.evict(attId)
            }
        }
    }

    private fun scheduleFileAttachmentIndexing(files: List<ChatAttachmentMeta>) {
        if (files.isEmpty()) return
        host.scope.launch {
            for (meta in files) {
                if (!meta.isFile) continue
                val indexed = host.container.attachmentStore.loadOrBuildDocumentIndex(meta)
                if (indexed != null && indexed.chunks.isNotEmpty()) {
                    host.container.retrievalCoordinator.scheduleAttachmentIndexing(
                        scope = host.scope,
                        attachmentId = meta.id,
                        fileName = meta.fileName,
                        extractedText = "",
                        chunksOverride = indexed.chunks,
                    )
                }
            }
        }
    }

    fun dismissPendingScannedPdf() {
        host.uiFlow.update { it.copy(pendingScannedPdf = null) }
    }

    fun convertPendingScannedPdfToImages(maxPages: Int = 4) {
        val pending = host.uiFlow.value.pendingScannedPdf ?: return
        host.uiFlow.update { it.copy(pendingScannedPdf = null) }
        host.scope.launch {
            val s = host.strings()
            val slots = (MAX_IMAGES - host.uiFlow.value.draftAttachments.count { it.isImage }).coerceAtLeast(0)
            if (slots <= 0) {
                host.container.toastController.show(s.chat.imageLimitHint.fmt("max" to MAX_IMAGES), isError = true)
                return@launch
            }
            host.uiFlow.update { it.copy(isImportingAttachment = true) }
            try {
                val rendered = host.container.attachmentStore.renderScannedPdfPagesAsImages(
                    uri = pending.uri,
                    maxPages = minOf(maxPages, slots),
                )
                if (rendered.isNotEmpty()) {
                    appendDraftAttachments(rendered)
                } else {
                    host.container.toastController.show(s.chat.invalidImage, isError = true)
                }
            } finally {
                host.uiFlow.update { it.copy(isImportingAttachment = false) }
            }
        }
    }

    suspend fun readAttachmentText(id: String): String? =
        host.container.attachmentStore.readExtractedText(id)

    fun removeDraftAttachment(id: String) {
        host.uiFlow.update { it.copy(draftAttachments = it.draftAttachments.filterNot { a -> a.id == id }) }
        host.scheduleWaterline()
    }

    private fun appendDraftAttachments(added: List<ChatAttachmentMeta>) {
        host.uiFlow.update { st ->
            st.copy(
                draftAttachments = st.draftAttachments + added,
                attachmentsById = st.attachmentsById + added.associateBy { it.id },
            )
        }
        host.scheduleWaterline()
    }

    companion object {
        private const val MAX_IMAGES = 6
        private const val MAX_FILES = 5
    }
}
