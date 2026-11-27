package com.gugachat.app

import android.content.Context
import coil.ImageLoader
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.ApiProviderStore
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SecureTokenStore
import com.gugachat.app.core.storage.SettingsStore
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
import com.gugachat.app.data.chat.RetrievalCoordinator
import com.gugachat.app.data.chat.VectorMemoryIndex
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.AttachmentFileStore
import com.gugachat.app.data.local.DirectImageStore
import com.gugachat.app.data.local.LocalEmbeddingModelManager
import com.gugachat.app.data.local.LocalOnnxEmbeddingEngine
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

sealed interface AuthSessionState {
    data object Checking : AuthSessionState
    data object LoggedOut : AuthSessionState
    data class LoggedIn(
        val user: UserDto,
        val isDirectApiOnly: Boolean = false,
    ) : AuthSessionState
}

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore = SettingsStore(appContext)
    val tokenStore = SecureTokenStore(appContext)
    val apiProviderStore = ApiProviderStore(appContext)
    val database: AppDatabase = AppDatabase.create(appContext)
    val attachmentStore = AttachmentFileStore(appContext, database.attachmentDao())
    val directImageStore = DirectImageStore(appContext)
    val toastController = ToastController()

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
        serverUrlProvider = { settingsState.value.serverUrl },
        languageTagProvider = { settingsState.value.language.tag },
        onSessionExpired = {
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

    val localEmbeddingModelManager = LocalEmbeddingModelManager(appContext)
    val localOnnxEmbeddingEngine = LocalOnnxEmbeddingEngine(
        modelManager = localEmbeddingModelManager,
        activeModelIdProvider = { settingsState.value.retrievalSettings.localModelId },
        lifecycleScope = scope,
    )
    val vectorMemoryIndex = VectorMemoryIndex(dao = database.memoryEmbeddingDao())
    val retrievalCoordinator = RetrievalCoordinator(
        settingsStore = settingsStore,
        settingsState = settingsState,
        apiProviderStore = apiProviderStore,
        directApiClient = directApiClient,
        localModelManager = localEmbeddingModelManager,
        localOnnxEngine = localOnnxEmbeddingEngine,
        vectorMemoryIndex = vectorMemoryIndex,
    )

    init {
        CjkSearch.segmenter = AndroidIcuCjkSegmenter()
        scope.launch {
            restoreSession()
        }
    }

    suspend fun restoreSession() {
        val current = settingsStore.currentSettings()
        val snap = tokenStore.readSnapshot()
        if (current.serverUrl.isBlank() || snap.accessToken.isNullOrBlank()) {
            if (current.directModeActive) {
                _authSession.value = AuthSessionState.LoggedIn(
                    user = directModeUser(),
                    isDirectApiOnly = true,
                )
            } else {
                _authSession.value = AuthSessionState.LoggedOut
            }
            return
        }

        val cachedUser = snap.cachedUserJson?.let { raw ->
            runCatching { AppJson.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
        }
        if (cachedUser != null) {
            _authSession.value = AuthSessionState.LoggedIn(cachedUser, isDirectApiOnly = false)
        }

        runCatching {
            val pub = authApi.getPublicSettings(current.serverUrl)
            _publicSettings.value = pub
        }

        try {
            val freshUser = authApi.getCurrentUser()
            _authSession.value = AuthSessionState.LoggedIn(freshUser, isDirectApiOnly = false)
        } catch (e: ApiException) {
            if (e.status == 401 || _authSession.value !is AuthSessionState.LoggedIn) {
                tokenStore.clear()
                _authSession.value = AuthSessionState.LoggedOut
            }
        } catch (_: Throwable) {
            if (_authSession.value !is AuthSessionState.LoggedIn) {
                _authSession.value = AuthSessionState.LoggedOut
            }
        }
    }

    fun setLoggedIn(user: UserDto) {
        scope.launch { settingsStore.setDirectModeActive(false) }
        _authSession.value = AuthSessionState.LoggedIn(user, isDirectApiOnly = false)
    }

    fun enterDirectApiMode() {
        scope.launch { settingsStore.setDirectModeActive(true) }
        _authSession.value = AuthSessionState.LoggedIn(user = directModeUser(), isDirectApiOnly = true)
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

    suspend fun logout() {
        val isDirect = (_authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true
        settingsStore.setDirectModeActive(false)
        if (!isDirect) {
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
