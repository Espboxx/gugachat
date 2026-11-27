package com.gugachat.app.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.ZhStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SecretVaultStore
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.DirectProtocolCodec
import com.gugachat.app.data.api.DiscoveredRetrievalModel
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.local.LocalModelDownloadState
import com.gugachat.app.data.local.LocalModelSpec
import com.gugachat.app.data.sync.BackupArchiveEngine
import com.gugachat.app.data.sync.BackupExportOptions
import com.gugachat.app.data.sync.BackupPassphraseRequiredException
import com.gugachat.app.data.sync.BackupVaultAdapter
import com.gugachat.app.data.sync.WebDavClient
import com.gugachat.app.data.sync.WebDavException
import com.gugachat.app.data.sync.WebDavPassphraseMismatchException
import com.gugachat.app.data.sync.WebDavSyncManager
import com.gugachat.app.domain.chat.RemoteEmbeddingConfig
import com.gugachat.app.domain.chat.RemoteRerankConfig
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.domain.chat.RetrievalSettings
import com.gugachat.app.domain.chat.VectorIndexProgress
import com.gugachat.app.ui.providers.ApiProviderDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

data class ProfileUiState(
    val user: UserDto? = null,
    val isDirectApiOnly: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val worksCount: Int = 0,
    val imageGroupsCount: Int? = null,
    val providers: List<ApiProviderConfig> = emptyList(),
    val providerTesting: Boolean = false,
    val providerTestCount: Int? = null,
    val providerTestError: String? = null,
    val isRefreshing: Boolean = false,
    val isBackupBusy: Boolean = false,
    val retrievalSettings: RetrievalSettings = RetrievalSettings(),
    val localModelSpecs: List<LocalModelSpec> = emptyList(),
    val localModelStates: Map<String, LocalModelDownloadState> = emptyMap(),
    val vectorIndexProgress: VectorIndexProgress = VectorIndexProgress(),
    val indexedVectorCount: Int = 0,
    val isLocalEmbedLoaded: Boolean = false,
    val embeddingTestStatus: String? = null,
    val isTestingEmbedding: Boolean = false,
    val discoveredRetrievalModels: List<DiscoveredRetrievalModel> = emptyList(),
    val isFetchingRetrievalModels: Boolean = false,
    val pendingRestoreUri: Uri? = null,
    val pendingRestoreReplaceExisting: Boolean = false,
    val pendingRestorePassphraseError: String? = null,
    val webDavConfig: WebDavSyncConfig = WebDavSyncConfig(),
    val webDavPassword: String = "",
    val webDavPassphrase: String = "",
    val isTestingWebDav: Boolean = false,
    val webDavTestStatus: String? = null,
    val webDavRemoteUpdatedAt: Long? = null,
    val isWebDavSyncBusy: Boolean = false,
    val lastWebDavSyncSummary: String? = null,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val secretVaultStore: SecretVaultStore by lazy {
        SecretVaultStore(container.appContext)
    }
    private val backupVaultAdapter: BackupVaultAdapter by lazy {
        BackupVaultAdapter.fromStores(
            apiProviderStore = container.apiProviderStore,
            secretVaultStore = secretVaultStore,
            settingsStore = container.settingsStore,
        )
    }
    private val webDavSyncManager: WebDavSyncManager by lazy {
        WebDavSyncManager(
            webDavClient = WebDavClient(container.apiClient.standardClient),
            db = container.database,
            filesDir = container.appContext.filesDir,
            vaultAdapter = backupVaultAdapter,
        )
    }

    private val _ui = MutableStateFlow(
        ProfileUiState(
            user = (container.authSession.value as? AuthSessionState.LoggedIn)?.user,
            isDirectApiOnly = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true,
            settings = container.settingsState.value,
            worksCount = container.imageWorksTotal.value,
            providers = container.apiProviderStore.currentProviders(),
            retrievalSettings = container.settingsState.value.retrievalSettings,
            localModelSpecs = container.localEmbeddingModelManager.catalog(),
            localModelStates = container.localEmbeddingModelManager.states.value,
            vectorIndexProgress = container.retrievalCoordinator.indexProgress.value,
            isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
            webDavConfig = container.settingsState.value.webDavConfig,
        ),
    )
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.settingsStore.migratePlaintextRetrievalKeysIfNeeded(secretVaultStore)
            }
            _ui.update { cur ->
                cur.copy(
                    retrievalSettings = hydrateRetrievalSettings(cur.settings.retrievalSettings),
                    webDavPassword = secretVaultStore.getWebDavPassword(),
                    webDavPassphrase = secretVaultStore.getWebDavSyncPassphrase(),
                )
            }
        }
        viewModelScope.launch {
            secretVaultStore.secretsFlow.collect {
                _ui.update { cur ->
                    cur.copy(
                        retrievalSettings = hydrateRetrievalSettings(cur.settings.retrievalSettings),
                        webDavPassword = secretVaultStore.getWebDavPassword(),
                        webDavPassphrase = secretVaultStore.getWebDavSyncPassphrase(),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.authSession.collect { session ->
                val logged = session as? AuthSessionState.LoggedIn
                _ui.update {
                    it.copy(
                        user = logged?.user,
                        isDirectApiOnly = logged?.isDirectApiOnly == true,
                    )
                }
                refreshImageGroupsCount()
            }
        }
        viewModelScope.launch {
            container.settingsState.collect { st ->
                _ui.update {
                    it.copy(
                        settings = st,
                        retrievalSettings = hydrateRetrievalSettings(st.retrievalSettings),
                        webDavConfig = st.webDavConfig,
                        isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                    )
                }
                refreshVectorStats()
            }
        }
        viewModelScope.launch {
            container.imageWorksTotal.collect { count ->
                _ui.update { it.copy(worksCount = count) }
            }
        }
        viewModelScope.launch {
            container.apiProviderStore.providers.collect { list ->
                _ui.update { it.copy(providers = list) }
                refreshImageGroupsCount()
            }
        }
        viewModelScope.launch {
            container.localEmbeddingModelManager.states.collect { states ->
                _ui.update {
                    it.copy(
                        localModelStates = states,
                        isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.retrievalCoordinator.indexProgress.collect { progress ->
                _ui.update {
                    it.copy(
                        vectorIndexProgress = progress,
                        isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                    )
                }
                if (!progress.running) {
                    refreshVectorStats()
                }
            }
        }
        refreshVectorStats()
    }

    private fun refreshImageGroupsCount() {
        viewModelScope.launch {
            val customImgCount = container.apiProviderStore.enabledProviders().count {
                DirectProtocolCodec.supportsImageGeneration(
                    protocol = it.protocol,
                    baseUrl = it.baseUrl,
                    customModels = it.customModels,
                )
            }
            val isDirect = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
            if (isDirect) {
                _ui.update { it.copy(imageGroupsCount = customImgCount) }
                return@launch
            }
            val serverCount = runCatching { container.onlineImagesApi.capabilities().keys.size }.getOrNull()
            if (serverCount != null) {
                _ui.update { it.copy(imageGroupsCount = serverCount + customImgCount) }
            }
        }
    }

    fun clearProviderTestState() {
        _ui.update { it.copy(providerTesting = false, providerTestCount = null, providerTestError = null) }
    }

    fun testProvider(draft: ApiProviderDraft) {
        if (_ui.value.providerTesting) return
        viewModelScope.launch {
            _ui.update { it.copy(providerTesting = true, providerTestCount = null, providerTestError = null) }
            try {
                val models = container.directApiClient.testAndFetchModels(draft.toConfig())
                _ui.update { it.copy(providerTesting = false, providerTestCount = models.size) }
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        providerTesting = false,
                        providerTestError = (e as? ApiException)?.message ?: e.message ?: "Test failed",
                    )
                }
            }
        }
    }

    fun saveProvider(draft: ApiProviderDraft) {
        container.apiProviderStore.upsert(draft.toConfig())
        clearProviderTestState()
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        container.toastController.show(if (isZh) "已保存 API 接口配置" else "Saved API endpoint")
    }

    fun importProviders(selected: List<ApiProviderConfig>): Int {
        val added = container.apiProviderStore.upsertAllDeduped(selected)
        val strings = if (container.settingsState.value.language == AppLanguage.EN) EnStrings else ZhStrings
        val msg = if (added > 0) {
            strings.providers.importProviderSuccess.fmt("added" to added)
        } else {
            strings.providers.importProviderAllDup
        }
        container.toastController.show(msg)
        return added
    }

    fun toggleProviderEnabled(id: String, enabled: Boolean) {
        container.apiProviderStore.setEnabled(id, enabled)
    }

    fun deleteProvider(id: String) {
        container.apiProviderStore.delete(id)
    }

    fun refreshProfile() {
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        if (_ui.value.isDirectApiOnly) {
            refreshImageGroupsCount()
            container.toastController.show(if (isZh) "已刷新本地接口状态" else "Local endpoints refreshed")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            try {
                val user = runCatching { container.authApi.getUserProfile() }
                    .getOrElse { container.authApi.getCurrentUser() }
                container.setLoggedIn(user)
                refreshImageGroupsCount()
                container.toastController.show(if (isZh) "已同步最新账户与能力数据" else "Account & capabilities refreshed")
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Refresh failed", isError = true)
            } finally {
                _ui.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setThemeStyle(style: ThemeStyle) = container.setThemeStyle(style)
    fun setColorMode(mode: ColorMode) = container.setColorMode(mode)
    fun setUiScale(preset: UiScalePreset) = container.setUiScale(preset)
    fun setLanguage(lang: AppLanguage) = container.setLanguage(lang)
    fun setUpdateChannel(channel: UpdateChannel) = container.setUpdateChannel(channel)
    fun setMemoryBudget(budget: String) {
        val normalized = SettingsStore.normalizeMemoryBudget(budget)
        viewModelScope.launch { container.settingsStore.setMemoryBudget(normalized) }
    }

    fun setSseAutoRetryCount(count: Int) {
        viewModelScope.launch { container.settingsStore.setSseAutoRetryCount(count) }
    }

    // ==================== Hybrid Semantic Retrieval Settings & Actions ====================

    private fun hydrateRetrievalSettings(base: RetrievalSettings): RetrievalSettings {
        val embedKey = secretVaultStore.getRemoteEmbeddingApiKey()
            .ifBlank { base.remoteEmbedding.customApiKey }
        val rerankKey = secretVaultStore.getRemoteRerankApiKey()
            .ifBlank { base.remoteRerank.customApiKey }
        return base.copy(
            remoteEmbedding = base.remoteEmbedding.copy(customApiKey = embedKey),
            remoteRerank = base.remoteRerank.copy(customApiKey = rerankKey),
        )
    }

    fun setRetrievalMode(mode: RetrievalMode) {
        viewModelScope.launch {
            val updated = _ui.value.retrievalSettings.copy(mode = mode)
            container.settingsStore.setRetrievalSettings(updated, secretVaultStore)
            if (mode != RetrievalMode.LOCAL_SMALL_MODEL && container.retrievalCoordinator.isLocalSessionLoaded()) {
                container.retrievalCoordinator.unloadLocalModelNow()
            }
            _ui.update {
                it.copy(
                    retrievalSettings = updated,
                    isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                )
            }
            refreshVectorStats()
        }
    }

    fun selectLocalModel(modelId: String) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val prev = _ui.value.retrievalSettings
            if (prev.localModelId != clean && container.retrievalCoordinator.isLocalSessionLoaded()) {
                container.retrievalCoordinator.unloadLocalModelNow()
            }
            val updated = prev.copy(localModelId = clean)
            container.settingsStore.setRetrievalSettings(updated, secretVaultStore)
            _ui.update {
                it.copy(
                    retrievalSettings = updated,
                    isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                )
            }
            refreshVectorStats()
        }
    }

    fun downloadLocalModel(modelId: String) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        container.localEmbeddingModelManager.startDownload(clean, container.scope)
    }

    fun cancelLocalModelDownload(modelId: String) {
        container.localEmbeddingModelManager.cancelDownload(modelId)
    }

    fun deleteLocalModel(modelId: String) {
        viewModelScope.launch {
            if (_ui.value.retrievalSettings.localModelId == modelId && container.retrievalCoordinator.isLocalSessionLoaded()) {
                container.retrievalCoordinator.unloadLocalModelNow()
            }
            val deleted = withContext(Dispatchers.IO) {
                container.localEmbeddingModelManager.deleteModel(modelId)
            }
            _ui.update { it.copy(isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded()) }
            val isZh = container.settingsState.value.language == AppLanguage.ZH
            if (deleted) {
                container.toastController.show(if (isZh) "已删除本地向量模型" else "Deleted local embedding model")
            }
            refreshVectorStats()
        }
    }

    fun unloadLocalModelNow() {
        container.retrievalCoordinator.unloadLocalModelNow()
        _ui.update { it.copy(isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded()) }
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        container.toastController.show(if (isZh) "已释放本地向量模型内存" else "Unloaded local embedding model from memory")
    }

    fun updateRemoteEmbeddingConfig(config: RemoteEmbeddingConfig) {
        viewModelScope.launch {
            val updated = _ui.value.retrievalSettings.copy(remoteEmbedding = config)
            container.settingsStore.setRetrievalSettings(updated, secretVaultStore)
            _ui.update {
                it.copy(
                    retrievalSettings = updated,
                    embeddingTestStatus = null,
                )
            }
            refreshVectorStats()
        }
    }

    fun updateRemoteRerankConfig(config: RemoteRerankConfig) {
        viewModelScope.launch {
            val updated = _ui.value.retrievalSettings.copy(remoteRerank = config)
            container.settingsStore.setRetrievalSettings(updated, secretVaultStore)
            _ui.update { it.copy(retrievalSettings = updated) }
        }
    }

    fun clearEmbeddingTestStatus() {
        _ui.update { it.copy(embeddingTestStatus = null) }
    }

    fun testRemoteEmbedding() {
        if (_ui.value.isTestingEmbedding) return
        val cfg = _ui.value.retrievalSettings.remoteEmbedding
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        viewModelScope.launch {
            _ui.update { it.copy(isTestingEmbedding = true, embeddingTestStatus = null) }
            val result = container.retrievalCoordinator.testRemoteEmbedding(cfg)
            result.fold(
                onSuccess = { res ->
                    val status = if (isZh) {
                        "连接成功 · ${res.dimension} 维 · ${res.latencyMs}ms"
                    } else {
                        "Connected · ${res.dimension}d · ${res.latencyMs}ms"
                    }
                    _ui.update { it.copy(isTestingEmbedding = false, embeddingTestStatus = status) }
                },
                onFailure = { err ->
                    val msg = (err as? ApiException)?.message ?: err.message ?: if (isZh) "测试失败" else "Test failed"
                    val status = if (isZh) "失败：$msg" else "Failed: $msg"
                    _ui.update { it.copy(isTestingEmbedding = false, embeddingTestStatus = status) }
                },
            )
        }
    }

    fun fetchRemoteRetrievalModels(
        providerId: String,
        customBaseUrl: String,
        customApiKey: String,
        protocol: ApiProtocol,
    ) {
        if (_ui.value.isFetchingRetrievalModels) return
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        viewModelScope.launch {
            _ui.update { it.copy(isFetchingRetrievalModels = true) }
            val result = container.retrievalCoordinator.fetchRemoteRetrievalModels(
                providerId = providerId,
                customBaseUrl = customBaseUrl,
                customApiKey = customApiKey,
                protocol = protocol,
            )
            result.fold(
                onSuccess = { models ->
                    _ui.update {
                        it.copy(
                            isFetchingRetrievalModels = false,
                            discoveredRetrievalModels = models,
                        )
                    }
                    if (models.isEmpty()) {
                        container.toastController.show(
                            if (isZh) "未发现可用的向量或精排模型" else "No embedding or rerank models found",
                        )
                    }
                },
                onFailure = { err ->
                    _ui.update { it.copy(isFetchingRetrievalModels = false) }
                    container.toastController.show(
                        (err as? ApiException)?.message ?: err.message ?: if (isZh) "获取模型列表失败" else "Failed to fetch models",
                        isError = true,
                    )
                },
            )
        }
    }

    fun clearVectorCache() {
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        viewModelScope.launch {
            container.retrievalCoordinator.clearAllVectors()
            _ui.update {
                it.copy(
                    indexedVectorCount = 0,
                    vectorIndexProgress = VectorIndexProgress(),
                )
            }
            container.toastController.show(if (isZh) "已清空本地向量索引缓存" else "Cleared local vector index cache")
        }
    }

    fun refreshVectorStats() {
        viewModelScope.launch {
            val count = container.retrievalCoordinator.countIndexedVectors()
            _ui.update {
                it.copy(
                    indexedVectorCount = count,
                    isLocalEmbedLoaded = container.retrievalCoordinator.isLocalSessionLoaded(),
                )
            }
        }
    }

    fun exportBackup(
        uri: Uri,
        contentResolver: ContentResolver,
        includeVault: Boolean,
        passphrase: String?,
        includeDirectImages: Boolean = false,
        successMessage: String,
    ) {
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        val cleanPass = passphrase?.trim()?.takeIf { it.isNotEmpty() }
        if (includeVault && cleanPass == null) {
            container.toastController.show(
                if (isZh) "启用密钥保险箱导出时请先设置加密口令" else "Please set a passphrase to encrypt API vault",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isBackupBusy = true) }
            try {
                val manifest = withContext(Dispatchers.IO) {
                    val options = BackupExportOptions(
                        passphrase = if (includeVault) cleanPass else null,
                        includeVault = includeVault,
                        includeDirectImages = includeDirectImages,
                    )
                    contentResolver.openOutputStream(uri)?.use { os ->
                        BackupArchiveEngine.exportBackupToStream(
                            db = container.database,
                            filesDir = container.appContext.filesDir,
                            output = os,
                            vaultAdapter = if (includeVault) backupVaultAdapter else null,
                            options = options,
                        )
                    } ?: error("Cannot open output stream")
                }
                val vaultTag = if (includeVault) {
                    if (isZh) "（含加密密钥保险箱）" else " (encrypted vault included)"
                } else {
                    ""
                }
                val doneMsg = if (manifest.mediaIndex.isNotEmpty()) {
                    "$successMessage$vaultTag · ${manifest.mediaIndex.size} 媒体文件"
                } else {
                    "$successMessage$vaultTag"
                }
                container.toastController.show(doneMsg)
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Export failed", isError = true)
            } finally {
                _ui.update { it.copy(isBackupBusy = false) }
            }
        }
    }

    fun importBackup(
        uri: Uri,
        contentResolver: ContentResolver,
        replaceExisting: Boolean,
        passphrase: String? = null,
        successMessage: String,
        failedMessage: String,
    ) {
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        viewModelScope.launch {
            _ui.update { it.copy(isBackupBusy = true) }
            try {
                val res = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        BackupArchiveEngine.restoreFromStream(
                            db = container.database,
                            filesDir = container.appContext.filesDir,
                            input = ins,
                            replaceExisting = replaceExisting,
                            passphrase = passphrase?.ifBlank { null },
                            vaultAdapter = backupVaultAdapter,
                            includeDirectImages = true,
                        )
                    } ?: error("Cannot read file")
                }
                _ui.update {
                    it.copy(
                        pendingRestoreUri = null,
                        pendingRestorePassphraseError = null,
                        pendingRestoreReplaceExisting = false,
                    )
                }
                val extraParts = buildList {
                    if (res.characterCardsCount > 0) add("${res.characterCardsCount} 角色卡")
                    if (res.worldbooksCount > 0) add("${res.worldbooksCount} 世界书")
                    if (res.personasCount > 0) add("${res.personasCount} 自设")
                    if (res.promptPresetsCount > 0) add("${res.promptPresetsCount} 预设")
                    if (res.mediaBlobsRestored > 0) add("${res.mediaBlobsRestored} 媒体")
                    if (res.vaultRestored) add(if (isZh) "已恢复密钥保险箱" else "Vault restored")
                }
                val baseToast = successMessage.fmt(
                    "conversations" to res.conversationsCount,
                    "memories" to res.userMemoriesCount,
                )
                val fullToast = if (extraParts.isNotEmpty()) {
                    "$baseToast (${extraParts.joinToString(" · ")})"
                } else {
                    baseToast
                }
                container.toastController.show(fullToast)
            } catch (_: BackupPassphraseRequiredException) {
                _ui.update {
                    it.copy(
                        pendingRestoreUri = uri,
                        pendingRestoreReplaceExisting = replaceExisting,
                        pendingRestorePassphraseError = if (!passphrase.isNullOrBlank()) {
                            if (isZh) "口令不能为空" else "Passphrase required"
                        } else {
                            null
                        },
                    )
                }
            } catch (_: AEADBadTagException) {
                _ui.update {
                    it.copy(
                        pendingRestoreUri = uri,
                        pendingRestoreReplaceExisting = replaceExisting,
                        pendingRestorePassphraseError = if (isZh) "加密口令错误，请重试" else "Incorrect passphrase, please retry",
                    )
                }
            } catch (e: GeneralSecurityException) {
                val msg = e.message.orEmpty()
                if (msg.contains("AES", ignoreCase = true) || msg.contains("tag", ignoreCase = true) || msg.contains("vault", ignoreCase = true)) {
                    _ui.update {
                        it.copy(
                            pendingRestoreUri = uri,
                            pendingRestoreReplaceExisting = replaceExisting,
                            pendingRestorePassphraseError = if (isZh) "解密失败，请核对口令是否正确" else "Decryption failed; verify passphrase",
                        )
                    }
                } else {
                    container.toastController.show(msg.ifBlank { failedMessage }, isError = true)
                }
            } catch (e: Throwable) {
                val errMsg = if (e is IllegalArgumentException && !e.message.isNullOrBlank()) {
                    e.message!!
                } else {
                    e.message?.takeIf { it.isNotBlank() } ?: failedMessage
                }
                container.toastController.show(errMsg, isError = true)
            } finally {
                _ui.update { it.copy(isBackupBusy = false) }
            }
        }
    }

    fun dismissRestorePassphrasePrompt() {
        _ui.update {
            it.copy(
                pendingRestoreUri = null,
                pendingRestorePassphraseError = null,
                pendingRestoreReplaceExisting = false,
            )
        }
    }

    // ==================== WebDAV E2EE Cloud Sync ====================

    fun updateWebDavSettings(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
    ) {
        _ui.update {
            it.copy(
                webDavConfig = config,
                webDavPassword = password,
                webDavPassphrase = passphrase,
            )
        }
        viewModelScope.launch {
            container.settingsStore.setWebDavConfig(config)
            secretVaultStore.setWebDavPassword(password)
            secretVaultStore.setWebDavSyncPassphrase(passphrase)
        }
    }

    fun clearWebDavTestStatus() {
        _ui.update { it.copy(webDavTestStatus = null) }
    }

    fun testWebDavConnection(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
    ) {
        if (_ui.value.isTestingWebDav) return
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        val cleanCfg = config.copy(url = config.url.trim(), username = config.username.trim())
        if (cleanCfg.url.isEmpty() || cleanCfg.username.isEmpty() || password.isBlank()) {
            _ui.update {
                it.copy(
                    webDavTestStatus = if (isZh) "请填写 WebDAV 地址、账号与应用密码" else "Fill WebDAV URL, username & app password",
                )
            }
            return
        }
        updateWebDavSettings(cleanCfg, password, passphrase)
        viewModelScope.launch {
            _ui.update { it.copy(isTestingWebDav = true, webDavTestStatus = null) }
            try {
                val res = withContext(Dispatchers.IO) {
                    webDavSyncManager.testConnection(cleanCfg, password)
                }
                val status = if (res.hasRemoteManifest) {
                    if (isZh) "连接成功 · 已发现云端加密档案" else "Connected · Remote encrypted archive found"
                } else {
                    if (isZh) "连接成功 · 目录就绪（暂无云端档案）" else "Connected · Directory ready (no remote snapshot)"
                }
                _ui.update {
                    it.copy(
                        isTestingWebDav = false,
                        webDavTestStatus = status,
                        webDavRemoteUpdatedAt = res.remoteUpdatedAt,
                    )
                }
            } catch (e: Throwable) {
                val err = (e as? WebDavException)?.message ?: e.message ?: if (isZh) "WebDAV 连接失败" else "Connection failed"
                _ui.update {
                    it.copy(
                        isTestingWebDav = false,
                        webDavTestStatus = if (isZh) "失败：$err" else "Failed: $err",
                    )
                }
            }
        }
    }

    fun syncWebDavNow(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
    ) {
        if (_ui.value.isWebDavSyncBusy) return
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        val cleanCfg = config.copy(url = config.url.trim(), username = config.username.trim())
        if (cleanCfg.url.isEmpty() || cleanCfg.username.isEmpty() || password.isBlank() || passphrase.isBlank()) {
            container.toastController.show(
                if (isZh) "请填写 WebDAV 地址、账号、应用密码及云端加密口令"
                else "Complete URL, username, password & sync passphrase",
                isError = true,
            )
            return
        }
        updateWebDavSettings(cleanCfg, password, passphrase)
        viewModelScope.launch {
            _ui.update { it.copy(isWebDavSyncBusy = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    webDavSyncManager.syncNow(
                        config = cleanCfg,
                        password = password,
                        passphrase = passphrase,
                    )
                }
                val syncedCfg = cleanCfg.copy(lastSyncAt = result.syncedAt)
                container.settingsStore.setWebDavConfig(syncedCfg)

                val summary = buildString {
                    if (result.remotePulled && result.restoreResult != null) {
                        append(
                            if (isZh) "已合并云端数据(${result.restoreResult.conversationsCount}会话)"
                            else "Pulled ${result.restoreResult.conversationsCount} chats",
                        )
                        append(" · ")
                    }
                    val up = result.backupResult
                    when {
                        up == null -> Unit
                        up.skippedNoChanges -> append(if (isZh) "云端与本地一致" else "Already up to date")
                        else -> append(
                            if (isZh) {
                                "新增上传 ${up.uploadedBlobs} 个媒体块（复用 ${up.skippedExistingBlobs}）"
                            } else {
                                "Uploaded ${up.uploadedBlobs} blobs (${up.skippedExistingBlobs} deduplicated)"
                            },
                        )
                    }
                }.ifBlank { if (isZh) "同步完成" else "Sync complete" }

                _ui.update {
                    it.copy(
                        isWebDavSyncBusy = false,
                        webDavConfig = syncedCfg,
                        webDavRemoteUpdatedAt = result.syncedAt,
                        lastWebDavSyncSummary = summary,
                    )
                }
                container.toastController.show(summary)
            } catch (_: WebDavPassphraseMismatchException) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    if (isZh) "加密口令与云端已有档案不匹配，请核对后重试"
                    else "Passphrase mismatch with remote WebDAV archive",
                    isError = true,
                )
            } catch (_: AEADBadTagException) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    if (isZh) "云端加密口令校验失败" else "Wrong cloud encryption passphrase",
                    isError = true,
                )
            } catch (e: Throwable) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    e.message ?: if (isZh) "WebDAV 同步失败" else "WebDAV sync failed",
                    isError = true,
                )
            }
        }
    }

    fun restoreFromWebDav(
        config: WebDavSyncConfig,
        password: String,
        passphrase: String,
    ) {
        if (_ui.value.isWebDavSyncBusy) return
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        val cleanCfg = config.copy(url = config.url.trim(), username = config.username.trim())
        if (cleanCfg.url.isEmpty() || cleanCfg.username.isEmpty() || password.isBlank() || passphrase.isBlank()) {
            container.toastController.show(
                if (isZh) "请填写完整 WebDAV 配置及加密口令" else "Complete WebDAV credentials and passphrase",
                isError = true,
            )
            return
        }
        updateWebDavSettings(cleanCfg, password, passphrase)
        viewModelScope.launch {
            _ui.update { it.copy(isWebDavSyncBusy = true) }
            try {
                val res = withContext(Dispatchers.IO) {
                    webDavSyncManager.restoreFromWebDav(
                        config = cleanCfg,
                        password = password,
                        passphrase = passphrase,
                        replaceExisting = false,
                        restoreVault = true,
                        restoreDirectImages = cleanCfg.syncDirectImages,
                    )
                }
                val now = System.currentTimeMillis()
                val syncedCfg = cleanCfg.copy(lastSyncAt = now)
                container.settingsStore.setWebDavConfig(syncedCfg)
                val summary = if (isZh) {
                    "已从云端恢复 ${res.conversationsCount} 会话 · ${res.characterCardsCount} 角色卡 · ${res.worldbooksCount} 世界书"
                } else {
                    "Restored ${res.conversationsCount} chats · ${res.characterCardsCount} cards · ${res.worldbooksCount} books"
                }
                _ui.update {
                    it.copy(
                        isWebDavSyncBusy = false,
                        webDavConfig = syncedCfg,
                        lastWebDavSyncSummary = summary,
                    )
                }
                container.toastController.show(summary)
            } catch (_: WebDavPassphraseMismatchException) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    if (isZh) "云端加密口令错误，无法解密云端备份" else "Incorrect cloud passphrase",
                    isError = true,
                )
            } catch (_: AEADBadTagException) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    if (isZh) "云端档案解密失败，请核对加密口令" else "Failed to decrypt remote archive",
                    isError = true,
                )
            } catch (e: Throwable) {
                _ui.update { it.copy(isWebDavSyncBusy = false) }
                container.toastController.show(
                    e.message ?: if (isZh) "云端恢复失败" else "Cloud restore failed",
                    isError = true,
                )
            }
        }
    }

    fun logout(doneToast: String) {
        viewModelScope.launch {
            container.logout()
            container.toastController.show(doneToast)
        }
    }
}
