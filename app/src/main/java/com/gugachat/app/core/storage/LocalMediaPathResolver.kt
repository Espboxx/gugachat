package com.gugachat.app.core.storage

import android.content.Context
import java.io.File

object LocalMediaPathResolver {
    private val EXTERNAL_URI_PREFIXES = listOf("http://", "https://", "content://", "data:")
    private val LEGACY_DATA_USER_FILES_REGEX = Regex("^/data/user/[^/]+/[^/]+/files(?:/(.*))?$")
    private val LEGACY_DATA_DATA_FILES_REGEX = Regex("^/data/data/[^/]+/files(?:/(.*))?$")

    fun toRelativePath(context: Context, rawPath: String?): String? =
        toRelativePath(context.applicationContext.filesDir.absolutePath, rawPath)

    fun toRelativePath(filesDirPath: String, rawPath: String?): String? {
        val trimmed = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isExternalOrDataUri(trimmed)) return trimmed

        val cleanFilesDir = filesDirPath.trim().trimEnd('/')
        if (cleanFilesDir.isNotEmpty()) {
            if (trimmed == cleanFilesDir || trimmed == "$cleanFilesDir/") return null
            val prefix = "$cleanFilesDir/"
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix).trimStart('/').ifEmpty { null }
            }
        }

        extractLegacyFilesRelativePath(trimmed)?.let { rel ->
            return rel.ifEmpty { null }
        }

        return trimmed
    }

    fun resolveAbsolutePath(context: Context, storedPath: String?): String? =
        resolveAbsolutePath(context.applicationContext.filesDir.absolutePath, storedPath)

    fun resolveAbsolutePath(filesDirPath: String, storedPath: String?): String? {
        val trimmed = storedPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isExternalOrDataUri(trimmed)) return trimmed

        val cleanFilesDir = filesDirPath.trim().trimEnd('/')
        if (!trimmed.startsWith("/")) {
            val cleanRel = trimmed.removePrefix("./").trimStart('/')
            if (cleanRel.isEmpty()) return null
            return if (cleanFilesDir.isNotEmpty()) {
                File(cleanFilesDir, cleanRel).path
            } else {
                cleanRel
            }
        }

        if (cleanFilesDir.isNotEmpty()) {
            if (trimmed == cleanFilesDir || trimmed == "$cleanFilesDir/") return null
            if (trimmed.startsWith("$cleanFilesDir/")) {
                return trimmed
            }
        }

        val legacyRel = extractLegacyFilesRelativePath(trimmed)
        if (legacyRel != null) {
            if (legacyRel.isEmpty()) return null
            if (File(trimmed).exists()) return trimmed
            if (cleanFilesDir.isNotEmpty()) {
                return File(cleanFilesDir, legacyRel).path
            }
        }

        return trimmed
    }

    fun resolveFile(context: Context, storedPath: String?): File? =
        resolveFile(context.applicationContext.filesDir.absolutePath, storedPath)

    fun resolveFile(filesDirPath: String, storedPath: String?): File? {
        val trimmed = storedPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isExternalOrDataUri(trimmed)) return null
        val normalized = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.substring("file://".length)
        } else {
            trimmed
        }
        val resolved = resolveAbsolutePath(filesDirPath, normalized) ?: return null
        return File(resolved)
    }

    private fun isExternalOrDataUri(path: String): Boolean =
        EXTERNAL_URI_PREFIXES.any { prefix -> path.startsWith(prefix, ignoreCase = true) }

    private fun extractLegacyFilesRelativePath(path: String): String? {
        LEGACY_DATA_USER_FILES_REGEX.matchEntire(path)?.let { match ->
            return match.groupValues[1].trimStart('/')
        }
        LEGACY_DATA_DATA_FILES_REGEX.matchEntire(path)?.let { match ->
            return match.groupValues[1].trimStart('/')
        }
        return null
    }
}
