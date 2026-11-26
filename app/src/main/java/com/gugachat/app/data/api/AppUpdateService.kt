package com.gugachat.app.data.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gugachat.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class UpdateChannel(val wireValue: String) {
    AUTO("auto"),
    CN_MIRROR("cn_mirror"),
    GITHUB_DIRECT("github");

    companion object {
        fun fromWire(value: String?): UpdateChannel =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) } ?: AUTO
    }
}

data class UpdateMetadataEndpoint(
    val label: String,
    val url: String,
    val isGitHubApi: Boolean,
    val mayBeCdnCached: Boolean = false,
)

data class ApkDownloadCandidate(
    val label: String,
    val url: String,
    val isCnChannel: Boolean,
)

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String? = null,
    val apkSha256: String? = null,
    val cnApkUrls: List<String> = emptyList(),
    val cnProxies: List<String> = emptyList(),
    val cnDownloadPage: String? = null,
    val resolvedSourceLabel: String = "",
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val activeSourceLabel: String = "",
        val attemptIndex: Int = 1,
        val totalCandidates: Int = 1,
    ) : UpdateDownloadState
    data class ReadyToInstall(
        val filePath: String,
        val needsInstallPermission: Boolean = false,
    ) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val showDialog: Boolean = false,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
)

enum class InstallLaunchResult {
    LAUNCHED,
    NEEDS_PERMISSION,
    FAILED,
}

object AppUpdateService {
    const val GITHUB_OWNER = "Espboxx"
    const val GITHUB_REPO = "gugachat"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    const val RAW_MANIFEST_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/docs/latest-release.json"

    val DEFAULT_CN_PROXIES: List<String> = listOf(
        "https://gh-proxy.com/",
        "https://cdn.gh-proxy.org/",
        "https://edgeone.gh-proxy.org/",
        "https://gh-proxy.org/",
        "https://ghfast.top/",
    )

    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Compares semantic versions like "v0.2" vs "0.1", "0.10.0" vs "0.2", "0.2.0" vs "0.2.0-beta.1".
     * Returns true strictly when [remoteVersion] is newer than [currentVersion].
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remote = parseVersionParts(remoteVersion)
        val current = parseVersionParts(currentVersion)
        if (remote.numbers.isEmpty() || current.numbers.isEmpty()) return false

        val maxLen = maxOf(remote.numbers.size, current.numbers.size)
        for (i in 0 until maxLen) {
            val r = remote.numbers.getOrElse(i) { 0 }
            val c = current.numbers.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        // Same numeric base: stable release (no prerelease tag) is newer than a prerelease
        if (remote.preRelease == null && current.preRelease != null) return true
        if (remote.preRelease != null && current.preRelease == null) return false
        if (remote.preRelease != null && current.preRelease != null) {
            return remote.preRelease.compareTo(current.preRelease, ignoreCase = true) > 0
        }
        return false
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String?,
    )

    private fun parseVersionParts(raw: String): ParsedVersion {
        val clean = normalizeVersion(raw).substringBefore('+')
        if (clean.isEmpty()) return ParsedVersion(emptyList(), null)
        val dashIdx = clean.indexOf('-')
        val base = if (dashIdx >= 0) clean.substring(0, dashIdx) else clean
        val pre = if (dashIdx >= 0) clean.substring(dashIdx + 1).takeIf { it.isNotBlank() } else null
        val nums = base.split('.').mapNotNull { part ->
            Regex("""^\d+""").find(part.trim())?.value?.toIntOrNull()
        }
        return ParsedVersion(nums, pre)
    }

    fun normalizeProxyPrefix(raw: String): String? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun hostLabelOf(url: String): String =
        runCatching { URI(url).host?.takeIf { it.isNotBlank() } }.getOrNull() ?: url

    fun buildMetadataEndpoints(
        channel: UpdateChannel,
        preferCn: Boolean = true,
    ): List<UpdateMetadataEndpoint> {
        val cnManifestEndpoints = listOf(
            UpdateMetadataEndpoint(
                label = "国内加速 · gh-proxy.org",
                url = "https://gh-proxy.org/$RAW_MANIFEST_URL",
                isGitHubApi = false,
                mayBeCdnCached = false,
            ),
            UpdateMetadataEndpoint(
                label = "国内加速 · ghfast.top",
                url = "https://ghfast.top/$RAW_MANIFEST_URL",
                isGitHubApi = false,
                mayBeCdnCached = false,
            ),
            UpdateMetadataEndpoint(
                label = "国内 CDN · fastly.jsdelivr",
                url = "https://fastly.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/docs/latest-release.json",
                isGitHubApi = false,
                mayBeCdnCached = true,
            ),
            UpdateMetadataEndpoint(
                label = "国内 CDN · gcore.jsdelivr",
                url = "https://gcore.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/docs/latest-release.json",
                isGitHubApi = false,
                mayBeCdnCached = true,
            ),
            UpdateMetadataEndpoint(
                label = "国内 CDN · cdn.jsdelivr",
                url = "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/docs/latest-release.json",
                isGitHubApi = false,
                mayBeCdnCached = true,
            ),
        )
        val githubEndpoint = UpdateMetadataEndpoint(
            label = "GitHub API",
            url = LATEST_RELEASE_API,
            isGitHubApi = true,
            mayBeCdnCached = false,
        )
        return when (channel) {
            UpdateChannel.GITHUB_DIRECT -> listOf(githubEndpoint)
            UpdateChannel.CN_MIRROR -> cnManifestEndpoints + githubEndpoint
            UpdateChannel.AUTO -> if (preferCn) {
                cnManifestEndpoints + githubEndpoint
            } else {
                listOf(githubEndpoint) + cnManifestEndpoints
            }
        }
    }

    fun buildApkDownloadCandidates(
        info: AppUpdateInfo,
        channel: UpdateChannel,
        preferCn: Boolean = true,
    ): List<ApkDownloadCandidate> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<ApkDownloadCandidate>()

        fun addCandidate(label: String, rawUrl: String?, isCn: Boolean) {
            val clean = rawUrl?.trim().orEmpty()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return
            if (seen.add(clean)) {
                result.add(ApkDownloadCandidate(label = label, url = clean, isCnChannel = isCn))
            }
        }

        val directApk = info.apkDownloadUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val cnDirects = info.cnApkUrls
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }

        val proxyPrefixes = (info.cnProxies + DEFAULT_CN_PROXIES)
            .mapNotNull(::normalizeProxyPrefix)
            .distinct()

        val canProxyDirectApk = directApk != null && (
            directApk.startsWith("https://github.com/") ||
                directApk.startsWith("https://objects.githubusercontent.com/") ||
                directApk.startsWith("https://release-assets.githubusercontent.com/")
            )

        fun addCnSources() {
            cnDirects.forEach { url ->
                addCandidate(label = "国内直链 · ${hostLabelOf(url)}", rawUrl = url, isCn = true)
            }
            if (canProxyDirectApk && directApk != null) {
                proxyPrefixes.forEach { prefix ->
                    val proxied = "$prefix$directApk"
                    addCandidate(label = "国内加速 · ${hostLabelOf(prefix)}", rawUrl = proxied, isCn = true)
                }
            }
        }

        fun addGithubSource() {
            if (directApk != null) {
                addCandidate(label = "GitHub", rawUrl = directApk, isCn = false)
            }
        }

        when (channel) {
            UpdateChannel.GITHUB_DIRECT -> addGithubSource()
            UpdateChannel.CN_MIRROR -> {
                addCnSources()
                addGithubSource()
            }
            UpdateChannel.AUTO -> {
                if (preferCn) {
                    addCnSources()
                    addGithubSource()
                } else {
                    addGithubSource()
                    addCnSources()
                }
            }
        }
        return result
    }

    fun resolveManualDownloadUrl(
        info: AppUpdateInfo?,
        channel: UpdateChannel,
        preferCn: Boolean = true,
    ): String {
        val useCn = channel == UpdateChannel.CN_MIRROR || (channel == UpdateChannel.AUTO && preferCn)
        if (useCn && info != null) {
            info.cnDownloadPage?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
                return it
            }
            val firstCn = buildApkDownloadCandidates(info, UpdateChannel.CN_MIRROR, preferCn = true)
                .firstOrNull { it.isCnChannel }
            if (firstCn != null) return firstCn.url
        }
        return info?.htmlUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: RELEASES_PAGE_URL
    }

    internal fun parseReleasePayload(
        bodyText: String,
        currentVersion: String,
        sourceLabel: String = "",
    ): UpdateCheckResult {
        val obj = runCatching { AppJson.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()
            ?: return UpdateCheckResult.Error("Invalid release payload")

        val isDraft = (obj["draft"] as? JsonPrimitive)?.booleanOrNull == true
        if (isDraft) {
            return UpdateCheckResult.UpToDate(
                currentVersion = normalizeVersion(currentVersion),
                latestVersion = normalizeVersion(currentVersion),
            )
        }

        val tagName = obj.str("tag_name").orEmpty().trim()
            .ifEmpty { obj.str("version_name").orEmpty().trim() }
        val remoteVersion = normalizeVersion(obj.str("version_name").orEmpty().ifBlank { tagName })
        if (remoteVersion.isEmpty()) {
            return UpdateCheckResult.Error("Missing release tag")
        }

        if (!isNewerVersion(remoteVersion, currentVersion)) {
            return UpdateCheckResult.UpToDate(
                currentVersion = normalizeVersion(currentVersion),
                latestVersion = remoteVersion,
            )
        }

        val htmlUrl = obj.str("html_url")?.takeIf { it.startsWith("http") } ?: RELEASES_PAGE_URL
        val title = obj.str("name")?.takeIf { it.isNotBlank() } ?: tagName.ifBlank { "v$remoteVersion" }
        val notes = obj.str("body").orEmpty().trim()
        val publishedAt = obj.str("published_at")

        // Support BOTH GitHub Release API (`assets[]`) AND static `docs/latest-release.json` (`apk_url`, `cn_*`)
        val assets = obj["assets"] as? JsonArray
        val apkAsset = assets?.mapNotNull { it as? JsonObject }?.firstOrNull { asset ->
            val name = asset.str("name").orEmpty().lowercase(Locale.ROOT)
            val dl = asset.str("browser_download_url").orEmpty()
            name.endsWith(".apk") && dl.startsWith("http")
        }

        val apkUrl = apkAsset?.str("browser_download_url")
            ?: obj.str("apk_url")?.takeIf { it.startsWith("http") }
        val apkFileName = apkAsset?.str("name")
            ?: obj.str("apk_name")?.takeIf { it.isNotBlank() }
        val apkSizeBytes = (apkAsset?.get("size") as? JsonPrimitive)?.longOrNull
            ?: (obj["apk_size"] as? JsonPrimitive)?.longOrNull
            ?: 0L
        val rawSha = obj.str("apk_sha256")
            ?: apkAsset?.str("digest")
        val normalizedSha = normalizeSha256(rawSha)

        val cnApkUrls = obj.strList("cn_apk_urls")
            .filter { it.startsWith("http://") || it.startsWith("https://") }
        val cnProxies = obj.strList("cn_proxies")
            .mapNotNull(::normalizeProxyPrefix)
        val cnDownloadPage = obj.str("cn_download_page")
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        return UpdateCheckResult.UpdateAvailable(
            AppUpdateInfo(
                tagName = tagName.ifBlank { "v$remoteVersion" },
                versionName = remoteVersion,
                releaseTitle = title,
                releaseNotes = notes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                publishedAt = publishedAt,
                apkSha256 = normalizedSha,
                cnApkUrls = cnApkUrls,
                cnProxies = cnProxies,
                cnDownloadPage = cnDownloadPage,
                resolvedSourceLabel = sourceLabel,
            ),
        )
    }

    suspend fun checkLatestRelease(
        httpClient: OkHttpClient,
        currentVersion: String = BuildConfig.VERSION_NAME,
        endpointUrl: String = LATEST_RELEASE_API,
    ): UpdateCheckResult {
        val endpoint = UpdateMetadataEndpoint(
            label = if (endpointUrl.contains("api.github.com")) "GitHub API" else hostLabelOf(endpointUrl),
            url = endpointUrl,
            isGitHubApi = endpointUrl.contains("api.github.com"),
        )
        return fetchMetadataFromEndpoint(
            httpClient = httpClient,
            currentVersion = currentVersion,
            endpoint = endpoint,
            connectTimeoutSec = 15,
            readTimeoutSec = 20,
            treat404AsUpToDate = true,
        )
    }

    suspend fun checkLatestReleaseWithFallback(
        httpClient: OkHttpClient,
        currentVersion: String = BuildConfig.VERSION_NAME,
        channel: UpdateChannel = UpdateChannel.AUTO,
        preferCn: Boolean = true,
        endpointsOverride: List<UpdateMetadataEndpoint>? = null,
    ): UpdateCheckResult {
        val endpoints = endpointsOverride ?: buildMetadataEndpoints(channel, preferCn)
        if (endpoints.isEmpty()) return UpdateCheckResult.Error("No update endpoints configured")

        var lastError: String? = null
        var cdnUpToDateCandidate: UpdateCheckResult.UpToDate? = null
        var saw404Only = true

        for (ep in endpoints) {
            currentCoroutineContext().ensureActive()
            val res = fetchMetadataFromEndpoint(
                httpClient = httpClient,
                currentVersion = currentVersion,
                endpoint = ep,
                connectTimeoutSec = 7,
                readTimeoutSec = 10,
                treat404AsUpToDate = ep.isGitHubApi,
            )
            when (res) {
                is UpdateCheckResult.UpdateAvailable -> return res
                is UpdateCheckResult.UpToDate -> {
                    saw404Only = false
                    if (!ep.mayBeCdnCached) {
                        return res
                    }
                    // CDN-cached manifest says up-to-date: remember it and still allow a real-time endpoint to confirm
                    if (cdnUpToDateCandidate == null) cdnUpToDateCandidate = res
                }
                is UpdateCheckResult.Error -> {
                    if (res.message != "HTTP 404") saw404Only = false
                    lastError = "${ep.label}: ${res.message}"
                }
            }
        }

        if (cdnUpToDateCandidate != null) return cdnUpToDateCandidate
        if (saw404Only) {
            val norm = normalizeVersion(currentVersion)
            return UpdateCheckResult.UpToDate(currentVersion = norm, latestVersion = norm)
        }
        return UpdateCheckResult.Error(lastError ?: "Network error")
    }

    private suspend fun fetchMetadataFromEndpoint(
        httpClient: OkHttpClient,
        currentVersion: String,
        endpoint: UpdateMetadataEndpoint,
        connectTimeoutSec: Long,
        readTimeoutSec: Long,
        treat404AsUpToDate: Boolean,
    ): UpdateCheckResult {
        val checkClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(connectTimeoutSec + readTimeoutSec, TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder()
            .url(endpoint.url)
            .get()
            .header("User-Agent", "GuGuChat-Android/$currentVersion")
        if (endpoint.isGitHubApi) {
            reqBuilder
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
        } else {
            reqBuilder.header("Accept", "application/json, text/plain, */*")
        }

        return try {
            val resp = checkClient.awaitCall(reqBuilder.build())
            val (code, contentType, bodyText) = withContext(Dispatchers.IO) {
                resp.use { r ->
                    Triple(
                        r.code,
                        r.header("Content-Type").orEmpty().lowercase(Locale.ROOT),
                        r.body?.string().orEmpty(),
                    )
                }
            }
            if (code == 404) {
                return if (treat404AsUpToDate) {
                    UpdateCheckResult.UpToDate(
                        currentVersion = normalizeVersion(currentVersion),
                        latestVersion = normalizeVersion(currentVersion),
                    )
                } else {
                    UpdateCheckResult.Error("HTTP 404")
                }
            }
            if (code !in 200..299) {
                return UpdateCheckResult.Error("HTTP $code")
            }
            if (contentType.contains("text/html") || bodyText.trimStart().startsWith("<")) {
                return UpdateCheckResult.Error("Unexpected HTML response")
            }
            parseReleasePayload(
                bodyText = bodyText,
                currentVersion = currentVersion,
                sourceLabel = endpoint.label,
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Network error")
        }
    }

    suspend fun downloadApk(
        httpClient: OkHttpClient,
        url: String,
        destFile: File,
        expectedSizeBytes: Long = 0L,
        expectedSha256: String? = null,
        connectTimeoutSec: Long = 20L,
        readTimeoutSec: Long = 120L,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        val dlClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("User-Agent", "GuGuChat-Android/${BuildConfig.VERSION_NAME}")
            .build()

        val normalizedExpectedSha = normalizeSha256(expectedSha256)
        val resp = dlClient.awaitCall(req)
        return withContext(Dispatchers.IO) {
            val parent = destFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val tempFile = File(destFile.parentFile, "${destFile.name}.part")
            try {
                resp.use { r ->
                    if (!r.isSuccessful) {
                        throw IllegalStateException("HTTP ${r.code}")
                    }
                    val contentType = r.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (contentType.contains("text/html")) {
                        throw IllegalStateException("Unexpected HTML response")
                    }
                    val body = r.body ?: throw IllegalStateException("Empty download body")
                    val total = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes
                    val buf = ByteArray(16 * 1024)
                    val digest = if (normalizedExpectedSha != null) MessageDigest.getInstance("SHA-256") else null
                    var downloaded = 0L
                    var lastEmit = 0L
                    var verifiedMagic = false

                    body.byteStream().use { ins ->
                        FileOutputStream(tempFile).use { fos ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = ins.read(buf)
                                if (n <= 0) break
                                if (!verifiedMagic) {
                                    if (n < 4 || buf[0] != 0x50.toByte() || buf[1] != 0x4B.toByte() || buf[2] != 0x03.toByte() || buf[3] != 0x04.toByte()) {
                                        throw IllegalStateException("Invalid APK file header")
                                    }
                                    verifiedMagic = true
                                }
                                downloaded += n
                                if (downloaded > MAX_APK_BYTES) {
                                    throw IllegalStateException("APK exceeds size limit")
                                }
                                digest?.update(buf, 0, n)
                                fos.write(buf, 0, n)
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 80L || (total > 0L && downloaded >= total)) {
                                    lastEmit = now
                                    onProgress(downloaded, total)
                                }
                            }
                        }
                    }
                    if (!verifiedMagic || downloaded <= 0L) {
                        throw IllegalStateException("Empty APK file")
                    }
                    if (digest != null && normalizedExpectedSha != null) {
                        val actualSha = digest.digest().toHexString()
                        if (!actualSha.equals(normalizedExpectedSha, ignoreCase = true)) {
                            throw IllegalStateException("APK SHA-256 mismatch")
                        }
                    }
                    onProgress(downloaded, if (total > 0L) total else downloaded)
                }

                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
                destFile
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                throw t
            }
        }
    }

    suspend fun downloadApkWithFallback(
        httpClient: OkHttpClient,
        candidates: List<ApkDownloadCandidate>,
        destFile: File,
        expectedSizeBytes: Long = 0L,
        expectedSha256: String? = null,
        onCandidateAttempt: (candidate: ApkDownloadCandidate, attemptIndex: Int, totalCandidates: Int) -> Unit = { _, _, _ -> },
        onProgress: (candidate: ApkDownloadCandidate, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): Pair<File, ApkDownloadCandidate> {
        if (candidates.isEmpty()) {
            throw IllegalStateException("No downloadable APK URL available")
        }
        val errors = ArrayList<String>()
        candidates.forEachIndexed { idx, candidate ->
            currentCoroutineContext().ensureActive()
            onCandidateAttempt(candidate, idx + 1, candidates.size)
            try {
                val file = downloadApk(
                    httpClient = httpClient,
                    url = candidate.url,
                    destFile = destFile,
                    expectedSizeBytes = expectedSizeBytes,
                    expectedSha256 = expectedSha256,
                    connectTimeoutSec = 12L,
                    readTimeoutSec = 60L,
                ) { downloaded, total ->
                    onProgress(candidate, downloaded, total)
                }
                return file to candidate
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val msg = t.message?.takeIf { it.isNotBlank() } ?: "Download failed"
                errors.add("${candidate.label}: $msg")
            }
        }
        throw IllegalStateException(errors.lastOrNull() ?: "All download sources failed")
    }

    fun computeFileSha256(file: File): String? {
        if (!file.isFile) return null
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val n = fis.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().toHexString()
        }.getOrNull()
    }

    fun normalizeSha256(raw: String?): String? {
        val clean = raw?.trim()
            ?.removePrefix("sha256:")
            ?.removePrefix("SHA256:")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null
        return if (clean.length == 64 && clean.all { it in '0'..'9' || it in 'a'..'f' }) clean else null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 1.0) return String.format(Locale.US, "%.1f MB", mb)
        val kb = bytes / 1024.0
        return String.format(Locale.US, "%.0f KB", kb)
    }

    fun launchApkInstall(context: Context, apkFile: File): InstallLaunchResult {
        if (!apkFile.exists()) return InstallLaunchResult.FAILED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(permIntent) }
            return InstallLaunchResult.NEEDS_PERMISSION
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            InstallLaunchResult.LAUNCHED
        } catch (_: Throwable) {
            InstallLaunchResult.FAILED
        }
    }

    fun openDownloadPage(context: Context, url: String = RELEASES_PAGE_URL) {
        val safeUrl = url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: RELEASES_PAGE_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            ?: emptyList()

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        for (b in this@toHexString) {
            val i = b.toInt() and 0xFF
            append(HEX_CHARS[i ushr 4])
            append(HEX_CHARS[i and 0x0F])
        }
    }

    private const val HEX_CHARS = "0123456789abcdef"
}

class AppUpdateController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val endpointUrl: String? = null,
    private val channelProvider: () -> UpdateChannel = { UpdateChannel.AUTO },
    private val preferCnProvider: () -> Boolean = { true },
    private val onChannelChanged: (UpdateChannel) -> Unit = {},
) {
    private val _ui = MutableStateFlow(AppUpdateUiState())
    val ui: StateFlow<AppUpdateUiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null

    fun setUpdateChannel(channel: UpdateChannel) {
        onChannelChanged(channel)
        if (_ui.value.downloadState is UpdateDownloadState.Failed) {
            _ui.update { it.copy(downloadState = UpdateDownloadState.Idle) }
        }
    }

    fun checkForUpdate(
        silent: Boolean,
        onUpToDate: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        val current = _ui.value
        if (current.isChecking) return
        if (silent && (current.showDialog || current.downloadState is UpdateDownloadState.Downloading)) return
        scope.launch {
            _ui.update { it.copy(isChecking = true) }
            val res = if (!endpointUrl.isNullOrBlank()) {
                AppUpdateService.checkLatestRelease(httpClient, currentVersion, endpointUrl)
            } else {
                AppUpdateService.checkLatestReleaseWithFallback(
                    httpClient = httpClient,
                    currentVersion = currentVersion,
                    channel = channelProvider(),
                    preferCn = preferCnProvider(),
                )
            }
            when (res) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val info = res.info
                    val cached = cachedApkFile(info)
                    val cachedValid = withContext(Dispatchers.IO) {
                        if (!cached.exists() || cached.length() <= 0L) return@withContext false
                        if (info.apkSizeBytes > 0L && cached.length() != info.apkSizeBytes) return@withContext false
                        val expectedSha = info.apkSha256
                        if (expectedSha != null) {
                            val actualSha = AppUpdateService.computeFileSha256(cached)
                            if (!expectedSha.equals(actualSha, ignoreCase = true)) {
                                runCatching { cached.delete() }
                                return@withContext false
                            }
                        }
                        true
                    }
                    val dlState = if (cachedValid) {
                        UpdateDownloadState.ReadyToInstall(cached.absolutePath)
                    } else {
                        UpdateDownloadState.Idle
                    }
                    _ui.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = info,
                            showDialog = true,
                            downloadState = dlState,
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _ui.update { it.copy(isChecking = false) }
                    if (!silent) onUpToDate?.invoke(res.currentVersion)
                }
                is UpdateCheckResult.Error -> {
                    _ui.update { it.copy(isChecking = false) }
                    if (!silent) onError?.invoke(res.message)
                }
            }
        }
    }

    fun startDownloadOrInstall(context: Context) {
        val info = _ui.value.availableUpdate ?: return
        val currentState = _ui.value.downloadState
        if (currentState is UpdateDownloadState.ReadyToInstall) {
            triggerInstall(context, File(currentState.filePath))
            return
        }
        val channel = channelProvider()
        val preferCn = preferCnProvider()
        val candidates = AppUpdateService.buildApkDownloadCandidates(info, channel, preferCn)
        if (candidates.isEmpty()) {
            val manualUrl = AppUpdateService.resolveManualDownloadUrl(info, channel, preferCn)
            AppUpdateService.openDownloadPage(context, manualUrl)
            dismissDialog()
            return
        }
        if (currentState is UpdateDownloadState.Downloading) return

        val destFile = cachedApkFile(info)
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _ui.update {
                it.copy(
                    downloadState = UpdateDownloadState.Downloading(
                        downloadedBytes = 0L,
                        totalBytes = info.apkSizeBytes,
                        activeSourceLabel = candidates.first().label,
                        attemptIndex = 1,
                        totalCandidates = candidates.size,
                    ),
                )
            }
            try {
                cleanOldUpdateApks(keepFileName = destFile.name)
                val (downloadedFile, _) = AppUpdateService.downloadApkWithFallback(
                    httpClient = httpClient,
                    candidates = candidates,
                    destFile = destFile,
                    expectedSizeBytes = info.apkSizeBytes,
                    expectedSha256 = info.apkSha256,
                    onCandidateAttempt = { cand, attemptIdx, totalCand ->
                        _ui.update { st ->
                            st.copy(
                                downloadState = UpdateDownloadState.Downloading(
                                    downloadedBytes = 0L,
                                    totalBytes = info.apkSizeBytes,
                                    activeSourceLabel = cand.label,
                                    attemptIndex = attemptIdx,
                                    totalCandidates = totalCand,
                                ),
                            )
                        }
                    },
                    onProgress = { cand, downloaded, total ->
                        _ui.update { st ->
                            val cur = st.downloadState as? UpdateDownloadState.Downloading
                            st.copy(
                                downloadState = UpdateDownloadState.Downloading(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    activeSourceLabel = cand.label,
                                    attemptIndex = cur?.attemptIndex ?: 1,
                                    totalCandidates = cur?.totalCandidates ?: candidates.size,
                                ),
                            )
                        }
                    },
                )
                triggerInstall(context, downloadedFile)
            } catch (_: CancellationException) {
                _ui.update { it.copy(downloadState = UpdateDownloadState.Idle) }
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        downloadState = UpdateDownloadState.Failed(
                            e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Download failed",
                        ),
                    )
                }
            }
        }
    }

    private fun triggerInstall(context: Context, apkFile: File) {
        val result = AppUpdateService.launchApkInstall(context, apkFile)
        _ui.update {
            it.copy(
                downloadState = when (result) {
                    InstallLaunchResult.LAUNCHED -> UpdateDownloadState.ReadyToInstall(
                        filePath = apkFile.absolutePath,
                        needsInstallPermission = false,
                    )
                    InstallLaunchResult.NEEDS_PERMISSION -> UpdateDownloadState.ReadyToInstall(
                        filePath = apkFile.absolutePath,
                        needsInstallPermission = true,
                    )
                    InstallLaunchResult.FAILED -> UpdateDownloadState.Failed("Unable to launch installer")
                },
            )
        }
    }

    fun openManualDownloadPage(context: Context) {
        val url = AppUpdateService.resolveManualDownloadUrl(
            info = _ui.value.availableUpdate,
            channel = channelProvider(),
            preferCn = preferCnProvider(),
        )
        cancelDownloadInternal()
        AppUpdateService.openDownloadPage(context, url)
        _ui.update { it.copy(showDialog = false) }
    }

    fun dismissDialog() {
        cancelDownloadInternal()
        _ui.update { it.copy(showDialog = false) }
    }

    private fun cancelDownloadInternal() {
        downloadJob?.cancel()
        downloadJob = null
        if (_ui.value.downloadState is UpdateDownloadState.Downloading) {
            _ui.update { it.copy(downloadState = UpdateDownloadState.Idle) }
        }
    }

    private fun cachedApkFile(info: AppUpdateInfo): File {
        val updatesDir = File(appContext.cacheDir, "updates")
        val safeTag = info.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(updatesDir, "gugachat-v$safeTag.apk")
    }

    private fun cleanOldUpdateApks(keepFileName: String) {
        runCatching {
            val updatesDir = File(appContext.cacheDir, "updates")
            updatesDir.listFiles()?.forEach { f ->
                if (f.name != keepFileName) f.delete()
            }
        }
    }
}
