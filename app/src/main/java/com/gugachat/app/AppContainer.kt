package com.gugachat.app

import android.content.Context
import coil.ImageLoader
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.storage.SecureTokenStore
import com.gugachat.app.core.storage.SettingsStore
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.ToastController
import com.gugachat.app.data.api.ApiClient
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.AuthApi
import com.gugachat.app.data.api.OnlineChatApi
import com.gugachat.app.data.api.OnlineImagesApi
import com.gugachat.app.data.api.PublicSettingsDto
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.local.AppDatabase
import com.gugachat.app.data.local.AttachmentFileStore
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
    data class LoggedIn(val user: UserDto) : AuthSessionState
}

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore = SettingsStore(appContext)
    val tokenStore = SecureTokenStore(appContext)
    val database: AppDatabase = AppDatabase.create(appContext)
    val attachmentStore = AttachmentFileStore(appContext, database.attachmentDao())
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

    val authApi = AuthApi(apiClient, tokenStore)
    val onlineChatApi = OnlineChatApi(apiClient)
    val onlineImagesApi = OnlineImagesApi(apiClient)
    val imageLoader: ImageLoader = apiClient.createAuthenticatedImageLoader(appContext)

    init {
        scope.launch {
            restoreSession()
        }
    }

    suspend fun restoreSession() {
        val current = settingsStore.currentSettings()
        val snap = tokenStore.readSnapshot()
        if (current.serverUrl.isBlank() || snap.accessToken.isNullOrBlank()) {
            _authSession.value = AuthSessionState.LoggedOut
            return
        }

        val cachedUser = snap.cachedUserJson?.let { raw ->
            runCatching { AppJson.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
        }
        if (cachedUser != null) {
            _authSession.value = AuthSessionState.LoggedIn(cachedUser)
        }

        runCatching {
            val pub = authApi.getPublicSettings(current.serverUrl)
            _publicSettings.value = pub
        }

        try {
            val freshUser = authApi.getCurrentUser()
            _authSession.value = AuthSessionState.LoggedIn(freshUser)
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
        _authSession.value = AuthSessionState.LoggedIn(user)
    }

    fun setPublicSettings(settings: PublicSettingsDto) {
        _publicSettings.value = settings
    }

    fun updateImageWorksTotal(total: Int) {
        _imageWorksTotal.value = total.coerceAtLeast(0)
    }

    suspend fun logout() {
        authApi.logout()
        _authSession.value = AuthSessionState.LoggedOut
    }

    fun setThemeStyle(style: ThemeStyle) {
        scope.launch { settingsStore.setThemeStyle(style) }
    }

    fun setColorMode(mode: ColorMode) {
        scope.launch { settingsStore.setColorMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        scope.launch { settingsStore.setLanguage(language) }
    }
}
