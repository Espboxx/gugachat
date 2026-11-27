package com.gugachat.app.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.gugachat.app.core.util.StreamZipUtils.readBoundedBytesOrNull

object UriUtils {
    fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.trim()?.takeIf { it.isNotEmpty() } else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() }

    fun queryFileSize(contentResolver: ContentResolver, uri: Uri): Long =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
                } else {
                    -1L
                }
            }
        }.getOrNull() ?: -1L

    fun readBoundedBytes(contentResolver: ContentResolver, uri: Uri, maxBytes: Long): ByteArray? =
        contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBoundedBytesOrNull(maxBytes = maxBytes, bufSize = 16 * 1024)
        }
}
