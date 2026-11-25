package com.gugachat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.PublicSettingsDto
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.ui.providers.ApiProviderDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginEntryMode {
    CHOOSER,
    SUB2API,
    DIRECT_API,
}

enum class AuthFormTab {
    LOGIN,
    REGISTER,
}

data class AuthUiState(
    val entryMode: LoginEntryMode = LoginEntryMode.CHOOSER,
    val serverUrlInput: String = "",
    val isProbing: Boolean = false,
    val serverConnected: Boolean = false,
    val serverError: String? = null,
    val publicSettings: PublicSettingsDto? = null,
    val activeTab: AuthFormTab = AuthFormTab.LOGIN,
    val email: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val promoCode: String = "",
    val invitationCode: String = "",
    val requires2fa: Boolean = false,
    val tempToken: String = "",
    val maskedEmail: String = "",
    val totpCode: String = "",
    val verifyCountdownSec: Int = 0,
    val isSubmitting: Boolean = false,
    val showWebLoginDialog: Boolean = false,
    val errorMsg: String? = null,
    val directDraft: ApiProviderDraft = ApiProviderDraft(),
    val directTesting: Boolean = false,
    val directTestCount: Int? = null,
    val directTestError: String? = null,
    val savedProvidersCount: Int = 0,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(
        AuthUiState(
            entryMode = computeInitialEntryMode(
                settings = container.settingsState.value,
                hasSavedProviders = container.apiProviderStore.currentProviders().isNotEmpty(),
            ),
            serverUrlInput = container.settingsState.value.serverUrl,
            publicSettings = container.publicSettings.value,
            serverConnected = container.publicSettings.value != null,
        ),
    )
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            container.apiProviderStore.providers.collect { list ->
                _ui.update { it.copy(savedProvidersCount = list.count { p -> p.enabled }) }
            }
        }
        viewModelScope.launch {
            val saved = container.settingsStore.currentSettings()
            val resolvedMode = computeInitialEntryMode(
                settings = saved,
                hasSavedProviders = container.apiProviderStore.currentProviders().isNotEmpty(),
            )
            _ui.update { cur ->
                cur.copy(
                    entryMode = resolvedMode,
                    serverUrlInput = cur.serverUrlInput.ifBlank { saved.serverUrl },
                )
            }
            if (saved.serverUrl.isNotBlank()) {
                probeServer(saved.serverUrl, silent = true)
            }
        }
    }

    private fun computeInitialEntryMode(
        settings: com.gugachat.app.core.storage.AppSettings,
        hasSavedProviders: Boolean,
    ): LoginEntryMode {
        if (!settings.hasChosenEntryMode && settings.serverUrl.isBlank() && !hasSavedProviders) {
            return LoginEntryMode.CHOOSER
        }
        return when (settings.preferredEntryMode) {
            "direct" -> LoginEntryMode.DIRECT_API
            "gugaga" -> LoginEntryMode.SUB2API
            else -> if (hasSavedProviders && settings.serverUrl.isBlank()) {
                LoginEntryMode.DIRECT_API
            } else if (settings.serverUrl.isNotBlank()) {
                LoginEntryMode.SUB2API
            } else {
                LoginEntryMode.CHOOSER
            }
        }
    }

    fun chooseEntryMode(mode: LoginEntryMode) {
        _ui.update { it.copy(entryMode = mode, errorMsg = null, directTestError = null) }
        if (mode != LoginEntryMode.CHOOSER) {
            val wire = if (mode == LoginEntryMode.DIRECT_API) "direct" else "gugaga"
            viewModelScope.launch { container.settingsStore.setPreferredEntryMode(wire) }
        }
    }

    fun setEntryMode(mode: LoginEntryMode) = chooseEntryMode(mode)

    fun updateDirectProtocol(protocol: ApiProtocol) {
        _ui.update { state ->
            val curBase = state.directDraft.baseUrl.trim()
            val shouldResetBase = curBase.isEmpty() || ApiProtocol.entries.any { it.defaultBaseUrl == curBase }
            state.copy(
                directDraft = state.directDraft.copy(
                    protocol = protocol,
                    baseUrl = if (shouldResetBase) protocol.defaultBaseUrl else curBase,
                ),
                directTestCount = null,
                directTestError = null,
            )
        }
    }

    fun updateDirectName(v: String) = _ui.update { it.copy(directDraft = it.directDraft.copy(name = v)) }
    fun updateDirectBaseUrl(v: String) = _ui.update {
        it.copy(directDraft = it.directDraft.copy(baseUrl = v), directTestCount = null, directTestError = null)
    }
    fun updateDirectApiKey(v: String) = _ui.update {
        it.copy(directDraft = it.directDraft.copy(apiKey = v), directTestCount = null, directTestError = null)
    }
    fun updateDirectCustomModels(v: String) = _ui.update {
        it.copy(directDraft = it.directDraft.copy(customModelsText = v), directTestError = null)
    }

    fun testDirectConnection() {
        val draft = _ui.value.directDraft
        if (_ui.value.directTesting) return
        viewModelScope.launch {
            _ui.update { it.copy(directTesting = true, directTestCount = null, directTestError = null) }
            try {
                val cfg = draft.toConfig()
                val models = container.directApiClient.testAndFetchModels(cfg)
                _ui.update { it.copy(directTesting = false, directTestCount = models.size) }
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        directTesting = false,
                        directTestError = (e as? ApiException)?.message ?: e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun saveAndEnterDirectMode() {
        val draft = _ui.value.directDraft
        if (draft.baseUrl.isBlank() || draft.apiKey.isBlank()) {
            _ui.update { it.copy(directTestError = "Base URL and API Key are required") }
            return
        }
        val saved = container.apiProviderStore.upsert(draft.toConfig())
        viewModelScope.launch {
            container.settingsStore.setLastChatSelection(saved.syntheticGroupId, saved.customModels.firstOrNull().orEmpty())
            if (saved.protocol.supportsImages) {
                container.settingsStore.setLastImageSelection(saved.syntheticGroupId, "")
            }
            container.enterDirectApiMode()
        }
    }

    fun enterWithSavedProviders() {
        val enabled = container.apiProviderStore.enabledProviders()
        if (enabled.isEmpty()) return
        container.enterDirectApiMode()
    }

    fun updateServerUrlInput(value: String) {
        _ui.update { it.copy(serverUrlInput = value, serverError = null, errorMsg = null) }
    }

    fun setActiveTab(tab: AuthFormTab) {
        _ui.update { it.copy(activeTab = tab, errorMsg = null) }
    }

    fun updateEmail(value: String) {
        _ui.update { it.copy(email = value, errorMsg = null) }
    }

    fun updatePassword(value: String) {
        _ui.update { it.copy(password = value, errorMsg = null) }
    }

    fun updateVerifyCode(value: String) {
        _ui.update { it.copy(verifyCode = value, errorMsg = null) }
    }

    fun updatePromoCode(value: String) {
        _ui.update { it.copy(promoCode = value, errorMsg = null) }
    }

    fun updateInvitationCode(value: String) {
        _ui.update { it.copy(invitationCode = value, errorMsg = null) }
    }

    fun updateTotpCode(value: String) {
        _ui.update { it.copy(totpCode = value, errorMsg = null) }
    }

    fun cancel2fa() {
        _ui.update { it.copy(requires2fa = false, tempToken = "", totpCode = "", errorMsg = null) }
    }

    fun setShowWebLogin(show: Boolean) {
        if (show) {
            val normalized = ServerUrlUtils.normalizeServerUrl(_ui.value.serverUrlInput)
            if (normalized.isEmpty()) {
                _ui.update { it.copy(serverError = "Server URL required", errorMsg = null) }
                return
            }
            viewModelScope.launch {
                container.settingsStore.setServerUrl(normalized)
            }
        }
        _ui.update { it.copy(showWebLoginDialog = show) }
    }

    fun probeServer(rawUrl: String = _ui.value.serverUrlInput, silent: Boolean = false) {
        val normalized = ServerUrlUtils.normalizeServerUrl(rawUrl)
        if (normalized.isEmpty()) {
            _ui.update {
                it.copy(
                    serverConnected = false,
                    serverError = if (silent) null else "Invalid server URL",
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isProbing = true, serverError = null, serverUrlInput = normalized) }
            container.settingsStore.setServerUrl(normalized)
            try {
                val settings = container.authApi.getPublicSettings(normalized)
                container.setPublicSettings(settings)
                _ui.update {
                    it.copy(
                        isProbing = false,
                        serverConnected = true,
                        serverError = null,
                        publicSettings = settings,
                    )
                }
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        isProbing = false,
                        serverConnected = false,
                        serverError = e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun sendVerifyCode() {
        val state = _ui.value
        if (state.verifyCountdownSec > 0 || state.isSubmitting) return
        viewModelScope.launch {
            if (!ensureServerConfigured()) return@launch
            if (state.email.isBlank()) {
                _ui.update { it.copy(errorMsg = "Email required") }
                return@launch
            }
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val res = container.authApi.sendVerifyCode(state.email)
                startCountdown(res.countdown.coerceIn(30, 120))
                container.toastController.show(res.message.ifBlank { "Code sent" })
                _ui.update { it.copy(isSubmitting = false) }
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "Failed to send code") }
            }
        }
    }

    fun submitLogin() {
        val state = _ui.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            if (!ensureServerConfigured()) return@launch
            if (state.email.isBlank() || state.password.isBlank()) {
                _ui.update { it.copy(errorMsg = "Please enter email and password") }
                return@launch
            }
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val resp = container.authApi.login(state.email, state.password)
                if (resp.requires_2fa) {
                    _ui.update {
                        it.copy(
                            isSubmitting = false,
                            requires2fa = true,
                            tempToken = resp.temp_token.orEmpty(),
                            maskedEmail = resp.user_email_masked.orEmpty(),
                            totpCode = "",
                        )
                    }
                    return@launch
                }
                val user = resp.user ?: container.authApi.getCurrentUser()
                container.setLoggedIn(user)
                _ui.update { it.copy(isSubmitting = false) }
            } catch (e: Throwable) {
                _ui.update {
                    it.copy(
                        isSubmitting = false,
                        errorMsg = (e as? ApiException)?.message ?: e.message ?: "Login failed",
                    )
                }
            }
        }
    }

    fun submit2fa() {
        val state = _ui.value
        if (state.isSubmitting || state.tempToken.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val resp = container.authApi.login2FA(state.tempToken, state.totpCode)
                val user = resp.user ?: container.authApi.getCurrentUser()
                container.setLoggedIn(user)
                _ui.update { it.copy(isSubmitting = false, requires2fa = false, tempToken = "") }
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "2FA verification failed") }
            }
        }
    }

    fun submitRegister() {
        val state = _ui.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            if (!ensureServerConfigured()) return@launch
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val resp = container.authApi.register(
                    email = state.email,
                    password = state.password,
                    verifyCode = state.verifyCode,
                    promoCode = state.promoCode,
                    invitationCode = state.invitationCode,
                )
                val user = resp.user ?: container.authApi.getCurrentUser()
                container.setLoggedIn(user)
                _ui.update { it.copy(isSubmitting = false) }
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "Registration failed") }
            }
        }
    }

    fun handleWebLoginCaptured(accessToken: String, refreshToken: String?, userJson: String?) {
        _ui.update { it.copy(showWebLoginDialog = false, isSubmitting = true, errorMsg = null) }
        viewModelScope.launch {
            try {
                container.tokenStore.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userJson = userJson,
                )
                val parsed = userJson?.let { raw ->
                    runCatching { AppJson.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
                }
                val user = runCatching { container.authApi.getCurrentUser() }.getOrElse { err ->
                    parsed ?: throw err
                }
                container.setLoggedIn(user)
                _ui.update { it.copy(isSubmitting = false) }
            } catch (e: Throwable) {
                container.tokenStore.clear()
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "Web login failed") }
            }
        }
    }

    fun toggleLanguage() {
        val cur = container.settingsState.value.language
        container.setLanguage(if (cur == AppLanguage.ZH) AppLanguage.EN else AppLanguage.ZH)
    }

    fun toggleThemeStyle() {
        val cur = container.settingsState.value.themeStyle
        container.setThemeStyle(if (cur == ThemeStyle.GUGUGA) ThemeStyle.CLASSIC else ThemeStyle.GUGUGA)
    }

    private suspend fun ensureServerConfigured(): Boolean {
        val normalized = ServerUrlUtils.normalizeServerUrl(_ui.value.serverUrlInput)
        if (normalized.isEmpty()) {
            _ui.update { it.copy(serverError = "Server URL required") }
            return false
        }
        container.settingsStore.setServerUrl(normalized)
        _ui.update { it.copy(serverUrlInput = normalized) }
        if (_ui.value.publicSettings == null) {
            runCatching {
                val settings = container.authApi.getPublicSettings(normalized)
                container.setPublicSettings(settings)
                _ui.update { it.copy(publicSettings = settings, serverConnected = true, serverError = null) }
            }
        }
        return true
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _ui.update { it.copy(verifyCountdownSec = left) }
                delay(1000L)
                left--
            }
            _ui.update { it.copy(verifyCountdownSec = 0) }
        }
    }
}
