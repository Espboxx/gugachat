package com.gugachat.app.ui.chat

import android.net.Uri
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.data.local.AttachmentSaveResult
import com.gugachat.app.data.local.toDomain
import com.gugachat.app.domain.chat.ChatAttachmentMeta
import com.gugachat.app.domain.chat.FileInjectMode
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun updateDraftAttachmentInjectMode(id: String, mode: FileInjectMode) {
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
            host.scheduleWaterline()
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
