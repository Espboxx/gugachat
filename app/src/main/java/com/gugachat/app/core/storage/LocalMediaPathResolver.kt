package com.gugachat.app.core.storage

import android.content.Context
import java.io.File
import java.util.UUID

object LocalMediaPathResolver {
    const val ANDROID_ASSET_URI_PREFIX = "file:///android_asset/"
    private val EXTERNAL_URI_PREFIXES = listOf("http://", "https://", "content://", "data:")
    private val LEGACY_DATA_USER_FILES_REGEX = Regex("^/data/user/[^/]+/[^/]+/files(?:/(.*))?$")
    private val LEGACY_DATA_DATA_FILES_REGEX = Regex("^/data/data/[^/]+/files(?:/(.*))?$")

    fun isAssetUri(path: String?): Boolean {
        val trimmed = path?.trim() ?: return false
        return trimmed.startsWith(ANDROID_ASSET_URI_PREFIX, ignoreCase = true)
    }

    fun extractAssetPath(path: String?): String? {
        val trimmed = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!trimmed.startsWith(ANDROID_ASSET_URI_PREFIX, ignoreCase = true)) return null
        val rel = trimmed.substring(ANDROID_ASSET_URI_PREFIX.length)
            .replace('\\', '/')
            .trimStart('/')
        if (rel.isEmpty()) return null
        val segments = rel.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return rel
    }

    fun toRelativePath(context: Context, rawPath: String?): String? =
        toRelativePath(context.applicationContext.filesDir.absolutePath, rawPath)

    fun toRelativePath(filesDirPath: String, rawPath: String?): String? {
        val trimmed = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isExternalOrDataUri(trimmed) || isAssetUri(trimmed)) return trimmed

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
        if (isExternalOrDataUri(trimmed) || isAssetUri(trimmed)) return trimmed

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
        if (isExternalOrDataUri(trimmed) || isAssetUri(trimmed)) return null
        val normalized = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.substring("file://".length)
        } else {
            trimmed
        }
        val resolved = resolveAbsolutePath(filesDirPath, normalized) ?: return null
        return File(resolved)
    }

    fun resolveImageModel(context: Context, storedPath: String?): Any? =
        resolveImageModel(context.applicationContext.filesDir.absolutePath, storedPath)

    fun resolveImageModel(filesDirPath: String, storedPath: String?): Any? {
        val trimmed = storedPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val assetRel = extractAssetPath(trimmed)
        if (assetRel != null) {
            return "$ANDROID_ASSET_URI_PREFIX$assetRel"
        }
        if (isAssetUri(trimmed)) return null
        return resolveFile(filesDirPath, trimmed)?.takeIf { it.isFile }
    }

    fun readBytes(context: Context, storedPath: String?, maxBytes: Long = 20L * 1024L * 1024L): ByteArray? {
        val assetRel = extractAssetPath(storedPath)
        if (assetRel != null) {
            return runCatching {
                context.applicationContext.assets.open(assetRel).use { input ->
                    input.readBytes().takeIf { it.isNotEmpty() && it.size.toLong() <= maxBytes }
                }
            }.getOrNull()
        }
        val file = resolveFile(context, storedPath)?.takeIf { it.isFile && it.length() in 1L..maxBytes }
            ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun saveRpMediaBytes(
        filesDir: File,
        bytes: ByteArray,
        prefix: String,
        ext: String,
        shortUuid: Boolean = false,
    ): Pair<String, File> {
        val dir = File(filesDir, "rp_media")
        if (!dir.exists()) dir.mkdirs()
        val safeExt = ext.trim().lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "png" }
        val token = UUID.randomUUID().toString().let { if (shortUuid) it.take(12) else it }
        val outFile = File(dir, "${prefix}_$token.$safeExt")
        outFile.writeBytes(bytes)
        val rel = toRelativePath(filesDir.absolutePath, outFile.absolutePath) ?: "rp_media/${outFile.name}"
        return rel to outFile
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
