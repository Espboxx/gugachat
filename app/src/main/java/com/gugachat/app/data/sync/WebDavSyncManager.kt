package com.gugachat.app.data.sync

import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class WebDavRemoteManifest(
    val formatVersion: Int = 2,
    val dbSchemaVersion: Int = 4,
    val updatedAt: Long,
    val kdfSaltHex: String,
    val snapshotRelPath: String,
    val snapshotSha256: String,
    val tablesSha256: String,
    val vaultRelPath: String? = null,
    val vaultSha256: String? = null,
    val vaultPlainSha256: String? = null,
    val mediaIndex: List<MediaBlobRef> = emptyList(),
    val includesDirectImages: Boolean = false,
)

data class WebDavConnectionResult(
    val rootSyncUrl: String,
    val hasRemoteManifest: Boolean,
    val remoteUpdatedAt: Long? = null,
)

data class WebDavBackupResult(
    val manifest: WebDavRemoteManifest,
    val uploadedBlobs: Int,
    val skippedExistingBlobs: Int,
    val prunedSnapshots: Int,
    val skippedNoChanges: Boolean = false,
)

data class WebDavSyncResult(
    val remotePulled: Boolean,
    val restoreResult: BackupRestoreResult? = null,
    val backupResult: WebDavBackupResult? = null,
    val syncedAt: Long,
)

class WebDavPassphraseMismatchException(
    message: String = "Sync passphrase does not match existing remote WebDAV backup",
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

/**
 * Coordinates encrypted incremental WebDAV backup/restore/sync under `<webdavRoot>/GuGuChatSync/`:
 * - `vault/providers_settings.enc`
 * - `snapshots/db_<ts>_<sha8>.zip.enc` (retains latest 5 rolling snapshots)
 * - `blobs/<prefix2>/<sha256>.enc` (`PROPFIND Depth:1` incremental diff upload; respects `syncDirectImages`)
 * - `manifest.json` (written last as atomic commit pointer)
 *
 * Reuses [BackupArchiveEngine.prepareStageDirectory] and [BackupArchiveEngine.applyStageDirectory]
 * so local `.gugubackup` and WebDAV sync share 100% of table/media serialization & merge semantics.
 */
class WebDavSyncManager(
    private val webDavClient: WebDavClient,
    private val dbGateway: BackupDbGateway,
    private val filesDir: File,
    private val vaultAdapter: BackupVaultAdapter? = null,
    private val tempRootDir: File = File(filesDir, ".webdav_sync_tmp"),
    private val blobConcurrency: Int = DEFAULT_BLOB_CONCURRENCY,
) {
    constructor(
        webDavClient: WebDavClient,
        db: AppDatabase,
        filesDir: File,
        vaultAdapter: BackupVaultAdapter? = null,
        tempRootDir: File = File(filesDir, ".webdav_sync_tmp"),
    ) : this(
        webDavClient = webDavClient,
        dbGateway = RoomBackupDbGateway(db),
        filesDir = filesDir,
        vaultAdapter = vaultAdapter,
        tempRootDir = tempRootDir,
    )

    init {
        require(blobConcurrency in 1..2) { "WebDAV blobConcurrency must be 1 or 2 (got $blobConcurrency)" }
    }

    suspend fun testConnection(
        config: WebDavSyncConfig,
        password: String,
    ): WebDavConnectionResult {
        val creds = buildCreds(config, password)
        val syncRootUrl = webDavClient.ensureDirectoryHierarchy(config.url, SYNC_ROOT_DIR, creds)
        val remoteManifest = fetchRemoteManifestIfExists(syncRootUrl, creds)
        return WebDavConnectionResult(
            rootSyncUrl = syncRootUrl,
            hasRemoteManifest = remoteManifest != null,
            remoteUpdatedAt = remoteManifest?.updatedAt,
        )
    }

    suspend fun backupToWebDav(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
        forceOverwriteIfPassphraseChanged: Boolean = false,
        skipIfUnchanged: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): WebDavBackupResult = withContext(Dispatchers.IO) {
        require(passphrase.isNotEmpty()) { "WebDAV E2EE sync requires a non-empty sync passphrase" }
        val creds = buildCreds(config, password)
        val syncRootUrl = webDavClient.ensureDirectoryHierarchy(config.url, SYNC_ROOT_DIR, creds)
        webDavClient.ensureDirectoryHierarchy(syncRootUrl, DIR_VAULT, creds)
        webDavClient.ensureDirectoryHierarchy(syncRootUrl, DIR_SNAPSHOTS, creds)
        webDavClient.ensureDirectoryHierarchy(syncRootUrl, DIR_BLOBS, creds)

        val remoteManifest = fetchRemoteManifestIfExists(syncRootUrl, creds)
        var reuseExistingRepoBlobs = false
        val saltBytes: ByteArray = if (remoteManifest != null) {
            val existingSalt = SyncCryptoCodec.fromHex(remoteManifest.kdfSaltHex)
            val remoteVerified = verifyRemotePassphrase(syncRootUrl, remoteManifest, passphrase, existingSalt, creds)
            if (remoteVerified) {
                reuseExistingRepoBlobs = true
                existingSalt
            } else if (forceOverwriteIfPassphraseChanged) {
                reuseExistingRepoBlobs = false
                SyncCryptoCodec.generateSalt()
            } else {
                throw WebDavPassphraseMismatchException()
            }
        } else {
            SyncCryptoCodec.generateSalt()
        }

        val workDir = createWorkDir("push")
        val keys = SyncCryptoCodec.deriveKeys(passphrase, saltBytes)
        try {
            val stageDir = File(workDir, "stage")
            val staged = BackupArchiveEngine.prepareStageDirectory(
                stageDir = stageDir,
                gateway = dbGateway,
                filesDir = filesDir,
                vaultAdapter = vaultAdapter,
                options = BackupExportOptions(
                    passphrase = passphrase,
                    kdfSalt = saltBytes,
                    includeVault = vaultAdapter != null,
                    includeDirectImages = config.syncDirectImages,
                    createdAt = nowMs,
                ),
            )

            if (skipIfUnchanged && remoteManifest != null && reuseExistingRepoBlobs) {
                val sameTables = remoteManifest.tablesSha256.equals(staged.manifest.tablesSha256, ignoreCase = true)
                val sameVault = remoteManifest.vaultPlainSha256 == staged.vaultPlainSha256
                val sameMedia = remoteManifest.mediaIndex.map { it.sha256 to it.relPath }.toSet() ==
                    staged.manifest.mediaIndex.map { it.sha256 to it.relPath }.toSet()
                if (sameTables && sameVault && sameMedia) {
                    return@withContext WebDavBackupResult(
                        manifest = remoteManifest,
                        uploadedBlobs = 0,
                        skippedExistingBlobs = staged.uniqueBlobsBySha().size,
                        prunedSnapshots = 0,
                        skippedNoChanges = true,
                    )
                }
            }

            // 1. Upload vault/providers_settings.enc if vault file is present
            var vaultRelPath: String? = null
            var vaultSha256: String? = null
            staged.vaultFile?.takeIf { it.isFile }?.let { vf ->
                vaultRelPath = VAULT_REMOTE_REL_PATH
                vaultSha256 = staged.manifest.vaultSha256 ?: SyncCryptoCodec.sha256Hex(vf)
                val vaultUrl = WebDavClient.resolveUrl(syncRootUrl, VAULT_REMOTE_REL_PATH)
                webDavClient.putFileFixedLength(vaultUrl, vf, "application/octet-stream", creds)
            }

            // 2. Build tables-only snapshot ZIP -> encrypt stream -> upload snapshots/db_<ts>_<sha8>.zip.enc
            val plainSnapshotZip = File(workDir, "snapshot_plain.zip")
            packSnapshotZip(staged, plainSnapshotZip)
            val snapshotSha256 = SyncCryptoCodec.sha256Hex(plainSnapshotZip)
            val snapshotName = "db_${nowMs}_${snapshotSha256.take(8)}.zip.enc"
            val snapshotRelPath = "$DIR_SNAPSHOTS/$snapshotName"
            val encSnapshotFile = File(workDir, snapshotName)
            SyncCryptoCodec.encryptFileWithKey(plainSnapshotZip, encSnapshotFile, keys.streamKey)
            plainSnapshotZip.delete()

            val snapshotUrl = WebDavClient.resolveUrl(syncRootUrl, snapshotRelPath)
            webDavClient.putFileFixedLength(snapshotUrl, encSnapshotFile, "application/octet-stream", creds)
            encSnapshotFile.delete()

            // 3. Incremental blob upload grouped by 2-char prefix using PROPFIND Depth: 1
            val uniqueBlobs = staged.uniqueBlobsBySha()
            val blobsByPrefix = uniqueBlobs.groupBy { it.sha256.take(2).lowercase() }
            var uploadedBlobs = 0
            var skippedBlobs = 0

            val uploadTasks = ArrayList<Pair<String, StagedMediaBlob>>()
            for ((prefix2, group) in blobsByPrefix) {
                val prefixDirRel = "$DIR_BLOBS/$prefix2"
                val prefixDirUrl = webDavClient.ensureDirectoryHierarchy(syncRootUrl, prefixDirRel, creds)
                val existingNames: Set<String> = if (reuseExistingRepoBlobs) {
                    webDavClient.listDirectoryIfExists(prefixDirUrl, creds)
                        .filter { !it.isCollection && (it.contentLength < 0L || it.contentLength >= MIN_ENCRYPTED_BLOB_BYTES) }
                        .map { it.displayName }
                        .toHashSet()
                } else {
                    emptySet()
                }

                for (blob in group) {
                    val fileName = "${blob.sha256}.enc"
                    if (fileName in existingNames) {
                        skippedBlobs += 1
                    } else {
                        uploadTasks.add(WebDavClient.resolveUrl(prefixDirUrl, fileName) to blob)
                    }
                }
            }

            if (uploadTasks.isNotEmpty()) {
                val sem = Semaphore(blobConcurrency)
                coroutineScope {
                    uploadTasks.mapIndexed { idx, (blobUrl, blob) ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val encBlob = File(workDir, "blob_${idx}_${blob.sha256}.enc")
                                try {
                                    SyncCryptoCodec.encryptFileWithKey(blob.sourceFile, encBlob, keys.streamKey)
                                    webDavClient.putFileFixedLength(blobUrl, encBlob, "application/octet-stream", creds)
                                } finally {
                                    encBlob.delete()
                                }
                            }
                        }
                    }.awaitAll()
                }
                uploadedBlobs = uploadTasks.size
            }

            // 4. Write manifest.json LAST as atomic commit pointer
            val remoteOutManifest = WebDavRemoteManifest(
                formatVersion = 2,
                dbSchemaVersion = 4,
                updatedAt = nowMs,
                kdfSaltHex = SyncCryptoCodec.toHex(saltBytes),
                snapshotRelPath = snapshotRelPath,
                snapshotSha256 = snapshotSha256,
                tablesSha256 = staged.manifest.tablesSha256,
                vaultRelPath = vaultRelPath,
                vaultSha256 = vaultSha256,
                vaultPlainSha256 = staged.vaultPlainSha256,
                mediaIndex = staged.manifest.mediaIndex,
                includesDirectImages = config.syncDirectImages,
            )
            val manifestBytes = AppJson.encodeToString(WebDavRemoteManifest.serializer(), remoteOutManifest)
                .toByteArray(StandardCharsets.UTF_8)
            val manifestUrl = WebDavClient.resolveUrl(syncRootUrl, MANIFEST_REMOTE_REL_PATH)
            webDavClient.putBytesFixedLength(manifestUrl, manifestBytes, "application/json; charset=utf-8", creds)

            // 5. Prune snapshots beyond latest 5 rolling snapshots
            val pruned = pruneOldSnapshots(syncRootUrl, snapshotName, creds)

            WebDavBackupResult(
                manifest = remoteOutManifest,
                uploadedBlobs = uploadedBlobs,
                skippedExistingBlobs = skippedBlobs,
                prunedSnapshots = pruned,
                skippedNoChanges = false,
            )
        } finally {
            keys.clear()
            workDir.deleteRecursively()
        }
    }

    suspend fun restoreFromWebDav(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
        replaceExisting: Boolean = false,
        restoreVault: Boolean = true,
        restoreDirectImages: Boolean = config.syncDirectImages,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        require(passphrase.isNotEmpty()) { "WebDAV E2EE restore requires a non-empty sync passphrase" }
        val creds = buildCreds(config, password)
        val syncRootUrl = WebDavClient.normalizeDirectoryUrl(
            WebDavClient.resolveUrl(config.url, "$SYNC_ROOT_DIR/"),
        )
        val remoteManifest = fetchRemoteManifestIfExists(syncRootUrl, creds)
            ?: throw IllegalStateException("Remote WebDAV directory has no $MANIFEST_REMOTE_REL_PATH backup")

        val salt = SyncCryptoCodec.fromHex(remoteManifest.kdfSaltHex)
        val keys = SyncCryptoCodec.deriveKeys(passphrase, salt)
        val workDir = createWorkDir("pull")
        try {
            val stageDir = File(workDir, "stage").apply { mkdirs() }

            // 1. Download & verify vault first so wrong passphrase fails immediately before snapshot/blob downloads
            if (restoreVault && !remoteManifest.vaultRelPath.isNullOrBlank()) {
                val vaultEncFile = File(stageDir, BackupArchiveEngine.VAULT_ENC_FILE)
                val vaultUrl = WebDavClient.resolveUrl(syncRootUrl, remoteManifest.vaultRelPath)
                webDavClient.getToFile(vaultUrl, vaultEncFile, creds)
                val cipherBytes = vaultEncFile.readBytes()
                remoteManifest.vaultSha256?.let { expected ->
                    if (!SyncCryptoCodec.sha256Hex(cipherBytes).equals(expected, ignoreCase = true)) {
                        throw SecurityException("Remote vault SHA-256 verification failed")
                    }
                }
                try {
                    SyncCryptoCodec.decryptVaultBytesWithKey(keys.vaultKey, cipherBytes)
                } catch (e: GeneralSecurityException) {
                    throw WebDavPassphraseMismatchException("Incorrect sync passphrase for remote vault", e)
                }
            }

            // 2. Download & decrypt snapshot ZIP -> unpack tables & manifest.json to stageDir
            val encSnapshotFile = File(workDir, "snapshot.zip.enc")
            val plainSnapshotZip = File(workDir, "snapshot.zip")
            val snapshotUrl = WebDavClient.resolveUrl(syncRootUrl, remoteManifest.snapshotRelPath)
            webDavClient.getToFile(snapshotUrl, encSnapshotFile, creds)
            try {
                SyncCryptoCodec.decryptFileWithKey(encSnapshotFile, plainSnapshotZip, keys.streamKey)
            } catch (e: GeneralSecurityException) {
                throw WebDavPassphraseMismatchException("Failed to decrypt remote WebDAV snapshot", e)
            } finally {
                encSnapshotFile.delete()
            }
            val actualSnapSha = SyncCryptoCodec.sha256Hex(plainSnapshotZip)
            if (!actualSnapSha.equals(remoteManifest.snapshotSha256, ignoreCase = true)) {
                throw SecurityException("Remote snapshot SHA-256 verification failed")
            }

            BufferedInputStream(FileInputStream(plainSnapshotZip), IO_BUFFER_BYTES).use { bis ->
                BackupArchiveEngine.unpackArchiveToStageDirectory(bis, stageDir)
            }
            plainSnapshotZip.delete()

            // 3. Download only missing or changed media blobs into stageDir/blobs/<sha256>
            val blobsDir = File(stageDir, "blobs").apply { mkdirs() }
            val neededRefs = remoteManifest.mediaIndex
                .filter { ref -> restoreDirectImages || !ref.relPath.startsWith("direct_images/") }
                .filter { ref ->
                    val localFile = File(filesDir, ref.relPath)
                    !(localFile.isFile && localFile.length() == ref.sizeBytes &&
                        SyncCryptoCodec.sha256Hex(localFile).equals(ref.sha256, ignoreCase = true))
                }
                .distinctBy { it.sha256 }

            if (neededRefs.isNotEmpty()) {
                val sem = Semaphore(blobConcurrency)
                coroutineScope {
                    neededRefs.mapIndexed { idx, ref ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val prefix2 = ref.sha256.take(2).lowercase()
                                val remoteBlobUrl = WebDavClient.resolveUrl(
                                    syncRootUrl,
                                    "$DIR_BLOBS/$prefix2/${ref.sha256}.enc",
                                )
                                val encBlob = File(workDir, "dl_blob_${idx}_${ref.sha256}.enc")
                                val plainBlob = File(blobsDir, ref.sha256)
                                try {
                                    webDavClient.getToFile(remoteBlobUrl, encBlob, creds)
                                    SyncCryptoCodec.decryptFileWithKey(encBlob, plainBlob, keys.streamKey)
                                    val actualBlobSha = SyncCryptoCodec.sha256Hex(plainBlob)
                                    if (!actualBlobSha.equals(ref.sha256, ignoreCase = true)) {
                                        throw SecurityException("Downloaded media blob SHA-256 mismatch: ${ref.sha256}")
                                    }
                                } finally {
                                    encBlob.delete()
                                }
                            }
                        }
                    }.awaitAll()
                }
            }

            // 4. Apply stage directory using exact shared BackupArchiveEngine atomic restore
            BackupArchiveEngine.applyStageDirectory(
                stageDir = stageDir,
                gateway = dbGateway,
                filesDir = filesDir,
                replaceExisting = replaceExisting,
                passphrase = passphrase,
                vaultAdapter = if (restoreVault) vaultAdapter else null,
                includeDirectImages = restoreDirectImages,
            )
        } finally {
            keys.clear()
            workDir.deleteRecursively()
        }
    }

    suspend fun syncNow(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
        restoreVault: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): WebDavSyncResult {
        require(passphrase.isNotEmpty()) { "WebDAV E2EE sync requires a non-empty sync passphrase" }
        val creds = buildCreds(config, password)
        val syncRootUrl = webDavClient.ensureDirectoryHierarchy(config.url, SYNC_ROOT_DIR, creds)
        val remoteManifest = fetchRemoteManifestIfExists(syncRootUrl, creds)

        var restoreResult: BackupRestoreResult? = null
        var pulled = false
        if (remoteManifest != null) {
            restoreResult = restoreFromWebDav(
                config = config,
                password = password,
                passphrase = passphrase,
                replaceExisting = false,
                restoreVault = restoreVault,
                restoreDirectImages = config.syncDirectImages,
            )
            pulled = true
        }

        val backupResult = backupToWebDav(
            config = config,
            password = password,
            passphrase = passphrase,
            forceOverwriteIfPassphraseChanged = false,
            skipIfUnchanged = remoteManifest != null,
            nowMs = nowMs,
        )

        return WebDavSyncResult(
            remotePulled = pulled,
            restoreResult = restoreResult,
            backupResult = backupResult,
            syncedAt = nowMs,
        )
    }

    private suspend fun fetchRemoteManifestIfExists(
        syncRootUrl: String,
        creds: WebDavCredentials,
    ): WebDavRemoteManifest? {
        val manifestUrl = WebDavClient.resolveUrl(syncRootUrl, MANIFEST_REMOTE_REL_PATH)
        val exists = try {
            webDavClient.exists(manifestUrl, creds)
        } catch (e: WebDavException) {
            if (e.statusCode == 404) return null else throw e
        }
        if (!exists) return null
        val bytes = try {
            webDavClient.getBytes(manifestUrl, creds)
        } catch (e: WebDavException) {
            if (e.statusCode == 404) return null else throw e
        }
        val text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return null
        return AppJson.decodeFromString(WebDavRemoteManifest.serializer(), text)
    }

    private suspend fun verifyRemotePassphrase(
        syncRootUrl: String,
        remoteManifest: WebDavRemoteManifest,
        passphrase: String,
        salt: ByteArray,
        creds: WebDavCredentials,
    ): Boolean {
        val vaultRel = remoteManifest.vaultRelPath?.takeIf { it.isNotBlank() }
        if (vaultRel != null) {
            val vaultBytes = try {
                webDavClient.getBytes(WebDavClient.resolveUrl(syncRootUrl, vaultRel), creds)
            } catch (e: WebDavException) {
                if (e.statusCode == 404) null else throw e
            }
            if (vaultBytes != null) {
                return try {
                    SyncCryptoCodec.decryptVaultBytes(passphrase, salt, vaultBytes)
                    true
                } catch (_: GeneralSecurityException) {
                    false
                }
            }
        }
        // Fallback: verify against remote snapshot header/chunk 0 if vault wasn't included
        val workDir = createWorkDir("verify")
        return try {
            val encSnap = File(workDir, "verify_snap.enc")
            val decSnap = File(workDir, "verify_snap.zip")
            webDavClient.getToFile(WebDavClient.resolveUrl(syncRootUrl, remoteManifest.snapshotRelPath), encSnap, creds)
            try {
                SyncCryptoCodec.decryptFile(encSnap, decSnap, passphrase, salt)
                true
            } catch (_: GeneralSecurityException) {
                false
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun packSnapshotZip(staged: BackupStageResult, destZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip), IO_BUFFER_BYTES)).use { zos ->
            writeZipEntry(zos, BackupArchiveEngine.MANIFEST_FILE, staged.manifestFile)
            for (name in BackupArchiveEngine.TABLE_FILES_IN_ORDER) {
                val f = File(staged.tablesDir, name)
                if (f.isFile) {
                    writeZipEntry(zos, "tables/$name", f)
                }
            }
            val directMeta = File(staged.tablesDir, BackupArchiveEngine.DIRECT_IMAGES_META_FILE)
            if (directMeta.isFile) {
                writeZipEntry(zos, "tables/${BackupArchiveEngine.DIRECT_IMAGES_META_FILE}", directMeta)
            }
            zos.finish()
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, file: File) {
        val buf = ByteArray(IO_BUFFER_BYTES)
        zos.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(file), IO_BUFFER_BYTES).use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                zos.write(buf, 0, n)
            }
        }
        zos.closeEntry()
    }

    private suspend fun pruneOldSnapshots(
        syncRootUrl: String,
        latestSnapshotName: String,
        creds: WebDavCredentials,
    ): Int {
        val snapshotsDirUrl = WebDavClient.resolveUrl(syncRootUrl, "$DIR_SNAPSHOTS/")
        val items = runCatching {
            webDavClient.listDirectoryIfExists(snapshotsDirUrl, creds)
        }.getOrDefault(emptyList())

        val candidates = items
            .filter { !it.isCollection && SNAPSHOT_NAME_REGEX.matches(it.displayName) }
            .sortedWith(
                compareByDescending<WebDavResource> { parseSnapshotTimestamp(it.displayName) }
                    .thenByDescending { it.displayName },
            )

        // Ensure the freshly uploaded snapshot is always retained even if listing is stale
        val toKeep = LinkedHashSet<String>()
        toKeep.add(latestSnapshotName)
        for (res in candidates) {
            if (toKeep.size >= MAX_ROLLING_SNAPSHOTS) break
            toKeep.add(res.displayName)
        }

        val toDelete = candidates.filter { it.displayName !in toKeep }
        var deleted = 0
        for (old in toDelete) {
            val url = WebDavClient.resolveUrl(snapshotsDirUrl, old.displayName)
            runCatching {
                if (webDavClient.delete(url, creds, ignoreNotFound = true)) {
                    deleted += 1
                }
            }
        }
        return deleted
    }

    private fun parseSnapshotTimestamp(name: String): Long {
        val match = SNAPSHOT_NAME_REGEX.matchEntire(name) ?: return 0L
        return match.groupValues[1].toLongOrNull() ?: 0L
    }

    private fun buildCreds(config: WebDavSyncConfig, password: String): WebDavCredentials {
        require(config.url.trim().startsWith("http://", ignoreCase = true) ||
            config.url.trim().startsWith("https://", ignoreCase = true)
        ) {
            "WebDAV server URL must start with http:// or https://"
        }
        require(config.username.isNotBlank()) { "WebDAV username must not be blank" }
        require(password.isNotEmpty()) { "WebDAV password must not be empty" }
        return WebDavCredentials(username = config.username.trim(), password = password)
    }

    private fun createWorkDir(prefix: String): File {
        tempRootDir.mkdirs()
        return File(tempRootDir, "${prefix}_${System.nanoTime()}").apply { mkdirs() }
    }

    companion object {
        const val SYNC_ROOT_DIR = "GuGuChatSync"
        const val DIR_VAULT = "vault"
        const val DIR_SNAPSHOTS = "snapshots"
        const val DIR_BLOBS = "blobs"
        const val MANIFEST_REMOTE_REL_PATH = "manifest.json"
        const val VAULT_REMOTE_REL_PATH = "vault/providers_settings.enc"
        const val MAX_ROLLING_SNAPSHOTS = 5
        const val DEFAULT_BLOB_CONCURRENCY = 2
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val MIN_ENCRYPTED_BLOB_BYTES = 19L + 16L
        private val SNAPSHOT_NAME_REGEX = Regex("^db_(\\d+)_[0-9a-fA-F]{8}\\.zip\\.enc$")
    }
}
