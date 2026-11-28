package com.gugachat.app

import android.content.Context
import coil.ImageLoader
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.runtime.AppForegroundTracker
import com.gugachat.app.core.runtime.RuntimeKeepAliveController
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SavedAccount
import com.gugachat.app.core.storage.SavedAccountStore
import com.gugachat.app.core.storage.SecretVaultStore
import com.gugachat.app.core.storage.SecureTokenStore
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.ToastController
import com.gugachat.app.data.api.ApiClient
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.AppUpdateController
import com.gugachat.app.data.api.AuthApi
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.OnlineChatApi
import com.gugachat.app.data.api.OnlineImagesApi
import com.gugachat.app.data.api.PublicSettingsDto
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.chat.AssetImportCoordinator
import com.gugachat.app.data.chat.CampaignLaunchUseCase
import com.gugachat.app.data.chat.ChatContextAssembler
import com.gugachat.app.data.chat.ChatStreamManager
import com.gugachat.app.data.chat.ConversationForkUseCase
import com.gugachat.app.data.chat.ImageChannelResolver
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.StudioImageGenerationUseCase
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.AttachmentFileStore
import com.gugachat.app.data.local.DirectImageStore
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalOnnxEmbeddingEngine
import com.gugachat.app.data.sync.BackupVaultAdapter
import com.gugachat.app.data.sync.WebDavClient
import com.gugachat.app.data.sync.WebDavSyncManager
import com.gugachat.app.domain.chat.AndroidIcuCjkSegmenter
import com.gugachat.app.domain.chat.CjkSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

sealed interface AuthSessionState {
    data object Checking : AuthSessionState
    data object LoggedOut : AuthSessionState
    data class LoggedIn(
        val user: UserDto,
        val isDirectApiOnly: Boolean = false,
        val serverUrl: String = "",
        val accountKey: String = "",
    ) : AuthSessionState
}

sealed interface SwitchAccountResult {
    data class Switched(val account: SavedAccount) : SwitchAccountResult
    data class Expired(val account: SavedAccount) : SwitchAccountResult
    data object NotFound : SwitchAccountResult
}

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val secretVaultStore = SecretVaultStore(appContext)
    val foregroundTracker = (appContext as? GuGuChatApp)?.foregroundTracker ?: AppForegroundTracker()
    val keepAliveController = RuntimeKeepAliveController(appContext, foregroundTracker)

    val settingsStore = SettingsStore(appContext)
    val tokenStore = SecureTokenStore(appContext)
    val savedAccountStore = SavedAccountStore(appContext)
    val apiProviderStore = ApiProviderStore(appContext)
    val database: AppDatabase = AppDatabase.create(appContext)
    val attachmentStore = AttachmentFileStore(appContext, database.attachmentDao())
    val directImageStore = DirectImageStore(appContext)
    val toastController = ToastController()
    val backupVaultAdapter = BackupVaultAdapter.fromStores(apiProviderStore, secretVaultStore, settingsStore)

    val settingsState: StateFlow<AppSettings> = settingsStore.settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    private val _authSession = MutableStateFlow<AuthSessionState>(AuthSessionState.Checking)
    val authSession: StateFlow<AuthSessionState> = _authSession.asStateFlow()

    private val _publicSettings = MutableStateFlow<PublicSettingsDto?>(null)
    val publicSettings: StateFlow<PublicSettingsDto?> = _publicSettings.asStateFlow()

    private val _imageWorksTotal = MutableStateFlow(0)
    val imageWorksTotal: StateFlow<Int> = _imageWorksTotal.asStateFlow()

    val apiClient = ApiClient(
        tokenStore = tokenStore,
        serverUrlProvider = {
            val active = (_authSession.value as? AuthSessionState.LoggedIn)
                ?.takeIf { !it.isDirectApiOnly && it.serverUrl.isNotBlank() }
            active?.serverUrl ?: settingsState.value.serverUrl
        },
        languageTagProvider = { settingsState.value.language.tag },
        onSessionExpired = {
            val current = _authSession.value as? AuthSessionState.LoggedIn
            if (current != null && !current.isDirectApiOnly && current.accountKey.isNotBlank()) {
                savedAccountStore.markExpired(current.accountKey)
            }
            _authSession.value = AuthSessionState.LoggedOut
        },
    )

    val directApiClient = DirectApiClient(
        standardClient = apiClient.standardClient,
        streamingClient = apiClient.streamingClient,
        parseHttpError = apiClient::parseHttpError,
    )

    val authApi = AuthApi(apiClient, tokenStore)
    val onlineChatApi = OnlineChatApi(
        client = apiClient,
        providerLookup = { id -> apiProviderStore.findBySyntheticId(id) },
        directClient = directApiClient,
        maxSseRetriesProvider = { settingsState.value.sseAutoRetryCount },
    )
    val onlineImagesApi = OnlineImagesApi(
        client = apiClient,
        providerLookup = { id -> apiProviderStore.findBySyntheticId(id) },
        directClient = directApiClient,
        directImageStore = directImageStore,
        isDirectOnlyProvider = { (_authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true },
    )
    val imageLoader: ImageLoader = apiClient.createAuthenticatedImageLoader(appContext)
    val updateController = AppUpdateController(
        appContext = appContext,
        scope = scope,
        httpClient = apiClient.streamingClient,
        channelProvider = { settingsState.value.updateChannel },
        preferCnProvider = { settingsState.value.language == AppLanguage.ZH },
        onChannelChanged = { ch -> setUpdateChannel(ch) },
    )

    val localEmbeddingModelManager = LocalEmbeddingModelManager(
        context = appContext,
        httpClient = apiClient.standardClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
    )
    val localOnnxEmbeddingEngine = LocalOnnxEmbeddingEngine(
        modelManager = localEmbeddingModelManager,
        activeModelIdProvider = { settingsState.value.retrievalSettings.localModelId },
        lifecycleScope = scope,
    )
    val vectorMemoryIndex = VectorMemoryIndex(dao = database.memoryEmbeddingDao())
    val retrievalCoordinator = RetrievalCoordinator(
        settingsState = settingsState,
        apiProviderStore = apiProviderStore,
        directApiClient = directApiClient,
        localModelManager = localEmbeddingModelManager,
        localOnnxEngine = localOnnxEmbeddingEngine,
        vectorMemoryIndex = vectorMemoryIndex,
    )
    val webDavClient = WebDavClient(
        httpClient = apiClient.standardClient.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
    )
    val webDavSyncManager = WebDavSyncManager(webDavClient, database, appContext.filesDir, backupVaultAdapter)
    val chatContextAssembler = ChatContextAssembler(
        database = database,
        settingsStore = settingsStore,
        apiProviderStore = apiProviderStore,
        attachmentStore = attachmentStore,
        retrievalCoordinator = retrievalCoordinator,
    )
    val assetImportCoordinator = AssetImportCoordinator(
        appContext = appContext,
        database = database,
        retrievalCoordinator = retrievalCoordinator,
        scope = scope,
    )
    val imageChannelResolver = ImageChannelResolver(
        authSession = authSession,
        apiProviderStore = apiProviderStore,
        directApiClient = directApiClient,
        onlineImagesApi = onlineImagesApi,
    )
    val studioImageGenerationUseCase = StudioImageGenerationUseCase(
        appContext = appContext,
        imageChannelResolver = imageChannelResolver,
        onlineImagesApi = onlineImagesApi,
        settingsStore = settingsStore,
    )
    val campaignLaunchUseCase = CampaignLaunchUseCase(
        appContext = appContext,
        database = database,
        settingsStore = settingsStore,
    )
    val conversationForkUseCase = ConversationForkUseCase(
        database = database,
    )
    val chatStreamManager = ChatStreamManager(
        appContext = appContext,
        scope = scope,
        database = database,
        settingsStore = settingsStore,
        settingsState = settingsState,
        onlineChatApi = onlineChatApi,
        contextAssembler = chatContextAssembler,
        retrievalCoordinator = retrievalCoordinator,
        foregroundTracker = foregroundTracker,
        keepAliveController = keepAliveController,
        toastController = toastController,
    )

    init {
        CjkSearch.segmenter = AndroidIcuCjkSegmenter()
        tokenStore.onTokensSaved = { snap ->
            val active = _authSession.value as? AuthSessionState.LoggedIn
            if (active != null &&
                !active.isDirectApiOnly &&
                active.accountKey.isNotBlank() &&
                !snap.accessToken.isNullOrBlank()
            ) {
                savedAccountStore.syncTokens(
                    accountKey = active.accountKey,
                    accessToken = snap.accessToken,
                    refreshToken = snap.refreshToken,
                    tokenExpiresAtMs = snap.tokenExpiresAtMs,
                )
            }
        }
        tokenStore.onUserJsonSaved = { userJson ->
            val active = _authSession.value as? AuthSessionState.LoggedIn
            if (active != null && !active.isDirectApiOnly && active.accountKey.isNotBlank()) {
                runCatching { AppJson.decodeFromString(UserDto.serializer(), userJson) }
                    .getOrNull()
                    ?.let { user ->
                        if (user.id == active.user.id) {
                            savedAccountStore.syncUser(active.accountKey, user)
                        }
                    }
            }
        }
        scope.launch {
            settingsStore.migratePlaintextRetrievalKeysIfNeeded(secretVaultStore)
            chatStreamManager.recoverOrphanStreams()
            restoreSession()
        }
    }

    fun authSessionIdentityKey(
        session: AuthSessionState = authSession.value,
        serverUrl: String = settingsState.value.serverUrl,
    ): String = when (session) {
        is AuthSessionState.LoggedIn -> if (session.isDirectApiOnly) {
            "direct"
        } else if (session.accountKey.isNotBlank()) {
            session.accountKey
        } else {
            val resolvedUrl = session.serverUrl.ifBlank { serverUrl }
            "sub2api:${session.user.id}:${resolvedUrl.trim().trimEnd('/')}"
        }
        AuthSessionState.Checking, AuthSessionState.LoggedOut -> "logged_out"
    }

    private fun snapshotActiveAccountToVault() {
        val current = _authSession.value as? AuthSessionState.LoggedIn ?: return
        if (current.isDirectApiOnly || current.serverUrl.isBlank()) return
        val snap = tokenStore.readSnapshot()
        val access = snap.accessToken?.trim().orEmpty()
        if (access.isEmpty()) return
        val existingLastUsed = savedAccountStore.findByKey(current.accountKey)?.lastUsedAt
            ?: System.currentTimeMillis()
        savedAccountStore.upsert(
            serverUrl = current.serverUrl,
            user = current.user,
            accessToken = access,
            refreshToken = snap.refreshToken,
            tokenExpiresAtMs = snap.tokenExpiresAtMs,
            sessionExpired = false,
            lastUsedAt = existingLastUsed,
        )
    }

    suspend fun restoreSession() {
        val current = settingsStore.currentSettings()
        if (current.directModeActive) {
            _authSession.value = AuthSessionState.LoggedIn(
                user = directModeUser(),
                isDirectApiOnly = true,
                serverUrl = "",
                accountKey = "direct",
            )
            return
        }

        val normalizedUrl = ServerUrlUtils.normalizeServerUrl(current.serverUrl)
        val snap = tokenStore.readSnapshot()
        if (normalizedUrl.isBlank() || snap.accessToken.isNullOrBlank()) {
            _authSession.value = AuthSessionState.LoggedOut
            return
        }

        val cachedUser = snap.cachedUserJson?.let { raw ->
            runCatching { AppJson.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
        }
        if (cachedUser != null) {
            val cachedKey = SavedAccount.buildAccountKey(normalizedUrl, cachedUser.id)
            if (savedAccountStore.findByKey(cachedKey) == null) {
                savedAccountStore.upsert(
                    serverUrl = normalizedUrl,
                    user = cachedUser,
                    accessToken = snap.accessToken,
                    refreshToken = snap.refreshToken,
                    tokenExpiresAtMs = snap.tokenExpiresAtMs,
                    sessionExpired = false,
                )
            }
            _authSession.value = AuthSessionState.LoggedIn(
                user = cachedUser,
                isDirectApiOnly = false,
                serverUrl = normalizedUrl,
                accountKey = cachedKey,
            )
        }

        runCatching {
            val pub = authApi.getPublicSettings(normalizedUrl)
            _publicSettings.value = pub
        }

        try {
            val freshUser = authApi.getCurrentUser()
            val freshKey = SavedAccount.buildAccountKey(normalizedUrl, freshUser.id)
            val latestSnap = tokenStore.readSnapshot()
            savedAccountStore.upsert(
                serverUrl = normalizedUrl,
                user = freshUser,
                accessToken = latestSnap.accessToken ?: snap.accessToken,
                refreshToken = latestSnap.refreshToken ?: snap.refreshToken,
                tokenExpiresAtMs = latestSnap.tokenExpiresAtMs,
                sessionExpired = false,
            )
            _authSession.value = AuthSessionState.LoggedIn(
                user = freshUser,
                isDirectApiOnly = false,
                serverUrl = normalizedUrl,
                accountKey = freshKey,
            )
        } catch (e: ApiException) {
            if (e.status == 401 || _authSession.value !is AuthSessionState.LoggedIn) {
                val activeKey = (_authSession.value as? AuthSessionState.LoggedIn)?.accountKey.orEmpty()
                if (activeKey.isNotBlank()) {
                    savedAccountStore.markExpired(activeKey)
                }
                tokenStore.clear()
                _authSession.value = AuthSessionState.LoggedOut
            }
        } catch (_: Throwable) {
            if (_authSession.value !is AuthSessionState.LoggedIn) {
                _authSession.value = AuthSessionState.LoggedOut
            }
        }
    }

    suspend fun activateLoggedInAccount(
        serverUrl: String,
        user: UserDto,
        accessToken: String,
        refreshToken: String? = null,
        expiresInSec: Long? = null,
        tokenExpiresAtMs: Long? = null,
    ): SavedAccount {
        snapshotActiveAccountToVault()
        val normalizedUrl = ServerUrlUtils.normalizeServerUrl(serverUrl)
        val resolvedExpiresAtMs = when {
            tokenExpiresAtMs != null && tokenExpiresAtMs > 0L -> tokenExpiresAtMs
            expiresInSec != null && expiresInSec > 0L -> System.currentTimeMillis() + expiresInSec * 1000L
            else -> 0L
        }
        val saved = savedAccountStore.upsert(
            serverUrl = normalizedUrl,
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAtMs = resolvedExpiresAtMs,
            sessionExpired = false,
            lastUsedAt = System.currentTimeMillis(),
        )
        val userJson = AppJson.encodeToString(UserDto.serializer(), user)
        settingsStore.setServerUrl(normalizedUrl)
        settingsStore.setDirectModeActive(false)
        settingsStore.setPreferredEntryMode("gugaga")
        tokenStore.restoreSnapshot(
            accessToken = saved.accessToken,
            refreshToken = saved.refreshToken,
            tokenExpiresAtMs = saved.tokenExpiresAtMs,
            userJson = userJson,
        )
        _authSession.value = AuthSessionState.LoggedIn(
            user = user,
            isDirectApiOnly = false,
            serverUrl = normalizedUrl,
            accountKey = saved.accountKey,
        )
        scope.launch {
            runCatching {
                val pub = authApi.getPublicSettings(normalizedUrl)
                val nowActive = _authSession.value as? AuthSessionState.LoggedIn
                if (nowActive != null && !nowActive.isDirectApiOnly && nowActive.serverUrl == normalizedUrl) {
                    _publicSettings.value = pub
                }
            }
        }
        return saved
    }

    suspend fun switchToSavedAccount(accountKey: String): SwitchAccountResult {
        val target = savedAccountStore.findByKey(accountKey) ?: return SwitchAccountResult.NotFound
        if (!target.hasValidTokens) {
            return SwitchAccountResult.Expired(target)
        }
        val current = _authSession.value as? AuthSessionState.LoggedIn
        if (current != null && !current.isDirectApiOnly && current.accountKey == target.accountKey) {
            savedAccountStore.touchLastUsed(target.accountKey)
            return SwitchAccountResult.Switched(target)
        }
        val activated = activateLoggedInAccount(
            serverUrl = target.serverUrl,
            user = target.user,
            accessToken = target.accessToken,
            refreshToken = target.refreshToken,
            tokenExpiresAtMs = target.tokenExpiresAtMs,
        )
        val targetKey = activated.accountKey
        scope.launch {
            try {
                val freshUser = authApi.getCurrentUser()
                val nowActive = _authSession.value as? AuthSessionState.LoggedIn
                if (nowActive != null && !nowActive.isDirectApiOnly && nowActive.accountKey == targetKey) {
                    savedAccountStore.syncUser(targetKey, freshUser)
                    _authSession.value = nowActive.copy(user = freshUser)
                }
            } catch (e: ApiException) {
                val nowActive = _authSession.value as? AuthSessionState.LoggedIn
                if (e.status == 401 && nowActive != null && !nowActive.isDirectApiOnly && nowActive.accountKey == targetKey) {
                    savedAccountStore.markExpired(targetKey)
                    tokenStore.clear()
                    _authSession.value = AuthSessionState.LoggedOut
                    val isZh = settingsState.value.language == AppLanguage.ZH
                    toastController.show(
                        if (isZh) "该账号登录已过期，请重新登录" else "Session expired, please sign in again",
                        isError = true,
                    )
                }
            } catch (_: Throwable) {
                // Keep cached session active on transient network failure
            }
        }
        return SwitchAccountResult.Switched(activated)
    }

    fun setLoggedIn(user: UserDto, serverUrlOverride: String? = null) {
        val current = _authSession.value as? AuthSessionState.LoggedIn
        val resolvedServerUrl = ServerUrlUtils.normalizeServerUrl(
            serverUrlOverride?.takeIf { it.isNotBlank() }
                ?: current?.takeIf { !it.isDirectApiOnly }?.serverUrl?.takeIf { it.isNotBlank() }
                ?: settingsState.value.serverUrl,
        )
        val accountKey = if (resolvedServerUrl.isNotBlank()) {
            SavedAccount.buildAccountKey(resolvedServerUrl, user.id)
        } else {
            ""
        }
        val snap = tokenStore.readSnapshot()
        if (resolvedServerUrl.isNotBlank() && !snap.accessToken.isNullOrBlank()) {
            savedAccountStore.upsert(
                serverUrl = resolvedServerUrl,
                user = user,
                accessToken = snap.accessToken,
                refreshToken = snap.refreshToken,
                tokenExpiresAtMs = snap.tokenExpiresAtMs,
                sessionExpired = false,
            )
        }
        scope.launch { settingsStore.setDirectModeActive(false) }
        _authSession.value = AuthSessionState.LoggedIn(
            user = user,
            isDirectApiOnly = false,
            serverUrl = resolvedServerUrl,
            accountKey = accountKey,
        )
    }

    fun enterDirectApiMode() {
        snapshotActiveAccountToVault()
        tokenStore.clear()
        scope.launch { settingsStore.setDirectModeActive(true) }
        _authSession.value = AuthSessionState.LoggedIn(
            user = directModeUser(),
            isDirectApiOnly = true,
            serverUrl = "",
            accountKey = "direct",
        )
    }

    private fun directModeUser(): UserDto = UserDto(
        id = 0L,
        username = "Direct API",
        email = "Custom Endpoints",
        role = "direct",
        concurrency = apiProviderStore.enabledProviders().size.coerceAtLeast(1),
    )

    fun setPublicSettings(settings: PublicSettingsDto) {
        _publicSettings.value = settings
    }

    fun updateImageWorksTotal(total: Int) {
        _imageWorksTotal.value = total.coerceAtLeast(0)
    }

    suspend fun removeSavedAccount(accountKey: String) {
        val target = savedAccountStore.findByKey(accountKey) ?: return
        val current = _authSession.value as? AuthSessionState.LoggedIn
        val isActiveTarget = current != null && !current.isDirectApiOnly && current.accountKey == target.accountKey
        if (isActiveTarget) {
            logout()
            return
        }
        authApi.revokeRefreshToken(target.serverUrl, target.refreshToken)
        savedAccountStore.remove(target.accountKey)
    }

    suspend fun logout() {
        val current = _authSession.value as? AuthSessionState.LoggedIn
        val isDirect = current?.isDirectApiOnly == true
        settingsStore.setDirectModeActive(false)
        if (!isDirect) {
            if (current != null && current.accountKey.isNotBlank()) {
                savedAccountStore.remove(current.accountKey)
            }
            authApi.logout()
        }
        _authSession.value = AuthSessionState.LoggedOut
    }

    fun setThemeStyle(style: ThemeStyle) {
        scope.launch { settingsStore.setThemeStyle(style) }
    }

    fun setColorMode(mode: ColorMode) {
        scope.launch { settingsStore.setColorMode(mode) }
    }

    fun setUiScale(preset: com.gugachat.app.core.theme.UiScalePreset) {
        scope.launch { settingsStore.setUiScale(preset) }
    }

    fun setLanguage(language: AppLanguage) {
        scope.launch { settingsStore.setLanguage(language) }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        scope.launch { settingsStore.setUpdateChannel(channel) }
    }
}
