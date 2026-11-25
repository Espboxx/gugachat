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
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    suspend fun checkLatestRelease(
        httpClient: OkHttpClient,
        currentVersion: String = BuildConfig.VERSION_NAME,
        endpointUrl: String = LATEST_RELEASE_API,
    ): UpdateCheckResult {
        val checkClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url(endpointUrl)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "GuGuChat-Android/$currentVersion")
            .build()

        return try {
            val resp = checkClient.awaitCall(req)
            val (code, bodyText) = withContext(Dispatchers.IO) {
                resp.use { r -> r.code to r.body?.string().orEmpty() }
            }
            if (code == 404) {
                return UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestVersion = normalizeVersion(currentVersion),
                )
            }
            if (code !in 200..299) {
                return UpdateCheckResult.Error("HTTP $code")
            }
            val obj = AppJson.parseToJsonElement(bodyText) as? JsonObject
                ?: return UpdateCheckResult.Error("Invalid GitHub release payload")

            val isDraft = (obj["draft"] as? JsonPrimitive)?.booleanOrNull == true
            if (isDraft) {
                return UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestVersion = normalizeVersion(currentVersion),
                )
            }

            val tagName = obj.str("tag_name").orEmpty().trim()
            val remoteVersion = normalizeVersion(tagName)
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
            val title = obj.str("name")?.takeIf { it.isNotBlank() } ?: tagName
            val notes = obj.str("body").orEmpty().trim()
            val publishedAt = obj.str("published_at")

            val assets = obj["assets"] as? JsonArray
            val apkAsset = assets?.mapNotNull { it as? JsonObject }?.firstOrNull { asset ->
                val name = asset.str("name").orEmpty().lowercase(Locale.ROOT)
                val dl = asset.str("browser_download_url").orEmpty()
                name.endsWith(".apk") && dl.startsWith("http")
            }

            UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    tagName = tagName,
                    versionName = remoteVersion,
                    releaseTitle = title,
                    releaseNotes = notes,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkAsset?.str("browser_download_url"),
                    apkFileName = apkAsset?.str("name"),
                    apkSizeBytes = (apkAsset?.get("size") as? JsonPrimitive)?.longOrNull ?: 0L,
                    publishedAt = publishedAt,
                ),
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
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        val dlClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("User-Agent", "GuGuChat-Android/${BuildConfig.VERSION_NAME}")
            .build()

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
}

class AppUpdateController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val endpointUrl: String = AppUpdateService.LATEST_RELEASE_API,
) {
    private val _ui = MutableStateFlow(AppUpdateUiState())
    val ui: StateFlow<AppUpdateUiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null

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
            when (val res = AppUpdateService.checkLatestRelease(httpClient, currentVersion, endpointUrl)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val info = res.info
                    val cached = cachedApkFile(info)
                    val dlState = if (cached.exists() && cached.length() > 0L && (info.apkSizeBytes <= 0L || cached.length() == info.apkSizeBytes)) {
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
        val apkUrl = info.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            AppUpdateService.openDownloadPage(context, info.htmlUrl)
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
                    ),
                )
            }
            try {
                cleanOldUpdateApks(keepFileName = destFile.name)
                val downloadedFile = AppUpdateService.downloadApk(
                    httpClient = httpClient,
                    url = apkUrl,
                    destFile = destFile,
                    expectedSizeBytes = info.apkSizeBytes,
                ) { downloaded, total ->
                    _ui.update { st ->
                        st.copy(
                            downloadState = UpdateDownloadState.Downloading(
                                downloadedBytes = downloaded,
                                totalBytes = total,
                            ),
                        )
                    }
                }
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
        val url = _ui.value.availableUpdate?.htmlUrl ?: AppUpdateService.RELEASES_PAGE_URL
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
