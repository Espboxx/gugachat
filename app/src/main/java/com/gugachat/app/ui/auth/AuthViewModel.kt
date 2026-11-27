package com.gugachat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.ApiProtocol
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.ApiProviderImportCodec
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.ActiveCaptchaConfig
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.api.CaptchaProof
import com.gugachat.app.data.api.DirectApiClient
import com.gugachat.app.data.api.GugagaHostProber
import com.gugachat.app.data.api.GugagaHosts
import com.gugachat.app.data.api.GugagaPresetHost
import com.gugachat.app.data.api.HostLatencyResult
import com.gugachat.app.data.api.HostProbeStatus
import com.gugachat.app.data.api.PublicSettingsDto
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.api.resolveActiveCaptcha
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderQuickPreset
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

enum class CaptchaUiStatus {
    IDLE,
    LOADING,
    READY,
    VERIFIED,
    EXPIRED,
    ERROR,
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
    val captchaProof: CaptchaProof = CaptchaProof(),
    val captchaStatus: CaptchaUiStatus = CaptchaUiStatus.IDLE,
    val captchaErrorMsg: String? = null,
    val captchaResetNonce: Int = 0,
    val directDraft: ApiProviderDraft = ApiProviderDraft(),
    val directTesting: Boolean = false,
    val directTestCount: Int? = null,
    val directTestError: String? = null,
    val savedProvidersCount: Int = 0,
    val hostProbes: List<HostLatencyResult> = GugagaHosts.PRESETS.map { HostLatencyResult(it) },
    val isProbingHosts: Boolean = false,
    val colorMode: ColorMode = ColorMode.SYSTEM,
) {
    val activeCaptcha: ActiveCaptchaConfig
        get() = publicSettings?.resolveActiveCaptcha() ?: ActiveCaptchaConfig.None

    val requiresInAppCaptcha: Boolean
        get() = activeCaptcha !is ActiveCaptchaConfig.None
}

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

    private val hostProber = GugagaHostProber(container.apiClient.standardClient)
    private var countdownJob: Job? = null
    private var hostProbeJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsState.collect { st ->
                _ui.update { it.copy(colorMode = st.colorMode) }
            }
        }
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
            if (resolvedMode == LoginEntryMode.SUB2API) {
                probePresetHosts(
                    autoSelectFastest = saved.serverUrl.isBlank() || GugagaHosts.isPresetUrl(saved.serverUrl),
                )
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
            if (mode == LoginEntryMode.SUB2API && _ui.value.hostProbes.all { it.status == HostProbeStatus.IDLE }) {
                probePresetHosts(
                    autoSelectFastest = _ui.value.serverUrlInput.isBlank() || GugagaHosts.isPresetUrl(_ui.value.serverUrlInput),
                )
            }
        }
    }

    fun setEntryMode(mode: LoginEntryMode) = chooseEntryMode(mode)

    fun probePresetHosts(autoSelectFastest: Boolean = true) {
        hostProbeJob?.cancel()
        hostProbeJob = viewModelScope.launch {
            _ui.update { cur ->
                cur.copy(
                    isProbingHosts = true,
                    hostProbes = GugagaHosts.PRESETS.map { preset ->
                        HostLatencyResult(host = preset, status = HostProbeStatus.TESTING)
                    },
                )
            }
            val results = hostProber.probeAll(GugagaHosts.PRESETS) { completed ->
                _ui.update { cur ->
                    cur.copy(
                        hostProbes = cur.hostProbes.map { item ->
                            if (item.host.id == completed.host.id) completed else item
                        },
                    )
                }
            }
            _ui.update { cur -> cur.copy(isProbingHosts = false, hostProbes = results) }

            val fastest = GugagaHosts.pickFastestReachable(results)
            val currentUrl = _ui.value.serverUrlInput
            if (fastest != null && autoSelectFastest && (currentUrl.isBlank() || GugagaHosts.isPresetUrl(currentUrl))) {
                applySelectedHostResult(fastest)
            }
        }
    }

    fun selectPresetHost(host: GugagaPresetHost) {
        val cached = _ui.value.hostProbes.firstOrNull { it.host.id == host.id }
        if (cached?.status == HostProbeStatus.REACHABLE && cached.publicSettings != null) {
            viewModelScope.launch { applySelectedHostResult(cached) }
        } else {
            val prevUrl = ServerUrlUtils.normalizeServerUrl(_ui.value.serverUrlInput)
            val nextUrl = ServerUrlUtils.normalizeServerUrl(host.url)
            val hostChanged = prevUrl != nextUrl
            _ui.update { cur ->
                cur.copy(
                    serverUrlInput = host.url,
                    serverError = null,
                    errorMsg = null,
                    captchaProof = if (hostChanged) CaptchaProof() else cur.captchaProof,
                    captchaStatus = if (hostChanged && cur.requiresInAppCaptcha) CaptchaUiStatus.LOADING else cur.captchaStatus,
                    captchaErrorMsg = if (hostChanged) null else cur.captchaErrorMsg,
                )
            }
            probeServer(host.url, silent = false)
        }
    }

    private suspend fun applySelectedHostResult(result: HostLatencyResult) {
        val normalized = ServerUrlUtils.normalizeServerUrl(result.host.url)
        container.settingsStore.setServerUrl(normalized)
        result.publicSettings?.let { container.setPublicSettings(it) }
        _ui.update { cur ->
            val prevUrl = ServerUrlUtils.normalizeServerUrl(cur.serverUrlInput)
            val nextSettings = result.publicSettings ?: cur.publicSettings
            val prevCaptcha = cur.activeCaptcha
            val nextCaptcha = nextSettings?.resolveActiveCaptcha() ?: ActiveCaptchaConfig.None
            val shouldReset = normalized != prevUrl || nextCaptcha != prevCaptcha
            cur.copy(
                serverUrlInput = normalized,
                serverConnected = true,
                serverError = null,
                publicSettings = nextSettings,
                captchaProof = if (shouldReset) CaptchaProof() else cur.captchaProof,
                captchaStatus = when {
                    nextCaptcha is ActiveCaptchaConfig.None -> CaptchaUiStatus.IDLE
                    shouldReset -> CaptchaUiStatus.LOADING
                    else -> cur.captchaStatus
                },
                captchaErrorMsg = if (shouldReset) null else cur.captchaErrorMsg,
                captchaResetNonce = if (shouldReset && nextCaptcha !is ActiveCaptchaConfig.None) cur.captchaResetNonce + 1 else cur.captchaResetNonce,
            )
        }
    }

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

    fun applyDirectPreset(preset: ApiProviderQuickPreset) {
        _ui.update { state ->
            state.copy(
                directDraft = state.directDraft.copy(
                    protocol = preset.protocol,
                    name = preset.displayName,
                    baseUrl = preset.baseUrl,
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
        val allowEmptyKey = DirectApiClient.isLocalOrLanHost(draft.baseUrl)
        if (draft.baseUrl.isBlank() || (!allowEmptyKey && draft.apiKey.isBlank())) {
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

    fun enterGuestMode() {
        viewModelScope.launch {
            container.settingsStore.setPreferredEntryMode("direct")
            container.enterDirectApiMode()
        }
    }

    fun importProvidersFromLogin(selected: List<ApiProviderConfig>) {
        if (selected.isEmpty()) return
        val existingKeys = container.apiProviderStore.currentProviders()
            .map(ApiProviderImportCodec::dedupeKey)
            .toMutableSet()
        var importedCount = 0
        var firstSaved: ApiProviderConfig? = null
        for (p in selected) {
            val key = ApiProviderImportCodec.dedupeKey(p)
            if (existingKeys.add(key)) {
                val saved = container.apiProviderStore.upsert(p)
                if (firstSaved == null) firstSaved = saved
                importedCount++
            }
        }
        val isZh = container.settingsState.value.language == AppLanguage.ZH
        if (importedCount > 0 && firstSaved != null) {
            container.toastController.show(
                if (isZh) "已导入 $importedCount 个 API 通道" else "Imported $importedCount API providers",
            )
            val savedFirst = firstSaved
            viewModelScope.launch {
                container.settingsStore.setPreferredEntryMode("direct")
                container.settingsStore.setLastChatSelection(
                    savedFirst.syntheticGroupId,
                    savedFirst.customModels.firstOrNull().orEmpty(),
                )
                container.enterDirectApiMode()
            }
        } else {
            container.toastController.show(
                if (isZh) "所选通道已存在，未重复导入" else "All selected providers already exist",
            )
        }
    }

    fun updateServerUrlInput(value: String) {
        _ui.update { it.copy(serverUrlInput = value, serverError = null, errorMsg = null) }
    }

    fun setActiveTab(tab: AuthFormTab) {
        val changed = _ui.value.activeTab != tab
        _ui.update { cur ->
            val shouldReset = changed && cur.requiresInAppCaptcha
            cur.copy(
                activeTab = tab,
                errorMsg = null,
                captchaProof = if (shouldReset) CaptchaProof() else cur.captchaProof,
                captchaStatus = if (shouldReset) CaptchaUiStatus.LOADING else cur.captchaStatus,
                captchaErrorMsg = if (shouldReset) null else cur.captchaErrorMsg,
                captchaResetNonce = if (shouldReset) cur.captchaResetNonce + 1 else cur.captchaResetNonce,
            )
        }
    }

    fun onCaptchaLoading() {
        _ui.update { it.copy(captchaStatus = CaptchaUiStatus.LOADING, captchaErrorMsg = null) }
    }

    fun onCaptchaReady() {
        _ui.update { cur ->
            if (cur.captchaStatus == CaptchaUiStatus.VERIFIED && cur.captchaProof.isReady) {
                cur
            } else {
                cur.copy(captchaStatus = CaptchaUiStatus.READY, captchaErrorMsg = null)
            }
        }
    }

    fun onCaptchaVerified(token: String, randstr: String = "") {
        val cleanToken = token.trim()
        val cleanRandstr = randstr.trim()
        if (cleanToken.isEmpty()) {
            onCaptchaError("empty_token")
            return
        }
        _ui.update { cur ->
            val proof = if (cur.activeCaptcha is ActiveCaptchaConfig.Tencent) {
                CaptchaProof(tencentTicket = cleanToken, tencentRandstr = cleanRandstr)
            } else {
                CaptchaProof(turnstileToken = cleanToken)
            }
            if (!proof.isReady) {
                cur.copy(
                    captchaProof = CaptchaProof(),
                    captchaStatus = CaptchaUiStatus.ERROR,
                    captchaErrorMsg = "invalid_proof",
                )
            } else {
                cur.copy(
                    captchaProof = proof,
                    captchaStatus = CaptchaUiStatus.VERIFIED,
                    captchaErrorMsg = null,
                    errorMsg = null,
                )
            }
        }
    }

    fun onCaptchaExpired() {
        _ui.update { cur ->
            cur.copy(
                captchaProof = CaptchaProof(),
                captchaStatus = if (cur.requiresInAppCaptcha) CaptchaUiStatus.EXPIRED else CaptchaUiStatus.IDLE,
            )
        }
    }

    fun onCaptchaError(detail: String?) {
        _ui.update { cur ->
            cur.copy(
                captchaProof = CaptchaProof(),
                captchaStatus = if (cur.requiresInAppCaptcha) CaptchaUiStatus.ERROR else CaptchaUiStatus.IDLE,
                captchaErrorMsg = detail,
            )
        }
    }

    fun resetCaptcha() = clearCaptchaAndReload()

    private fun clearCaptchaAndReload() {
        _ui.update { cur ->
            val hasCaptcha = cur.requiresInAppCaptcha
            cur.copy(
                captchaProof = CaptchaProof(),
                captchaStatus = if (hasCaptcha) CaptchaUiStatus.LOADING else CaptchaUiStatus.IDLE,
                captchaErrorMsg = null,
                captchaResetNonce = if (hasCaptcha) cur.captchaResetNonce + 1 else cur.captchaResetNonce,
            )
        }
    }

    private fun captchaRequiredMessage(): String =
        if (container.settingsState.value.language == AppLanguage.ZH) {
            "请先完成人机验证"
        } else {
            "Please complete human verification first"
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
            val resolved = resolveSelectedHostUrl()
            _ui.update { it.copy(serverUrlInput = resolved, serverError = null, errorMsg = null) }
            viewModelScope.launch {
                container.settingsStore.setServerUrl(resolved)
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
            _ui.update { cur ->
                val prevUrl = ServerUrlUtils.normalizeServerUrl(cur.serverUrlInput)
                val urlChanged = prevUrl != normalized
                cur.copy(
                    isProbing = true,
                    serverError = null,
                    serverUrlInput = normalized,
                    captchaProof = if (urlChanged) CaptchaProof() else cur.captchaProof,
                    captchaErrorMsg = if (urlChanged) null else cur.captchaErrorMsg,
                )
            }
            container.settingsStore.setServerUrl(normalized)
            try {
                val settings = container.authApi.getPublicSettings(normalized)
                container.setPublicSettings(settings)
                _ui.update { cur ->
                    val prevCaptcha = cur.activeCaptcha
                    val nextCaptcha = settings.resolveActiveCaptcha()
                    val captchaChanged = prevCaptcha != nextCaptcha
                    cur.copy(
                        isProbing = false,
                        serverConnected = true,
                        serverError = null,
                        publicSettings = settings,
                        captchaProof = if (captchaChanged) CaptchaProof() else cur.captchaProof,
                        captchaStatus = when {
                            nextCaptcha is ActiveCaptchaConfig.None -> CaptchaUiStatus.IDLE
                            captchaChanged -> CaptchaUiStatus.LOADING
                            else -> cur.captchaStatus
                        },
                        captchaErrorMsg = if (captchaChanged) null else cur.captchaErrorMsg,
                        captchaResetNonce = if (captchaChanged && nextCaptcha !is ActiveCaptchaConfig.None) cur.captchaResetNonce + 1 else cur.captchaResetNonce,
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
            val cur = _ui.value
            if (cur.email.isBlank()) {
                _ui.update { it.copy(errorMsg = "Email required") }
                return@launch
            }
            if (cur.requiresInAppCaptcha && !cur.captchaProof.isReady) {
                _ui.update { it.copy(errorMsg = captchaRequiredMessage()) }
                return@launch
            }
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val proof = cur.captchaProof.takeIf { cur.requiresInAppCaptcha }
                val res = container.authApi.sendVerifyCode(cur.email, captchaProof = proof)
                startCountdown(res.countdown.coerceIn(30, 120))
                container.toastController.show(res.message.ifBlank { "Code sent" })
                _ui.update { it.copy(isSubmitting = false) }
                if (cur.requiresInAppCaptcha) clearCaptchaAndReload()
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "Failed to send code") }
                if (cur.requiresInAppCaptcha) clearCaptchaAndReload()
            }
        }
    }

    fun submitLogin() {
        val state = _ui.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            if (!ensureServerConfigured()) return@launch
            val cur = _ui.value
            if (cur.email.isBlank() || cur.password.isBlank()) {
                _ui.update { it.copy(errorMsg = "Please enter email and password") }
                return@launch
            }
            if (cur.requiresInAppCaptcha && !cur.captchaProof.isReady) {
                _ui.update { it.copy(errorMsg = captchaRequiredMessage()) }
                return@launch
            }
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val proof = cur.captchaProof.takeIf { cur.requiresInAppCaptcha }
                val resp = container.authApi.login(cur.email, cur.password, captchaProof = proof)
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
                    if (cur.requiresInAppCaptcha) clearCaptchaAndReload()
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
                if (cur.requiresInAppCaptcha) clearCaptchaAndReload()
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
            val cur = _ui.value
            val needsCaptcha = cur.requiresInAppCaptcha &&
                !(cur.publicSettings?.email_verify_enabled == true && cur.verifyCode.isNotBlank())
            if (needsCaptcha && !cur.captchaProof.isReady) {
                _ui.update { it.copy(errorMsg = captchaRequiredMessage()) }
                return@launch
            }
            _ui.update { it.copy(isSubmitting = true, errorMsg = null) }
            try {
                val proof = cur.captchaProof.takeIf { needsCaptcha }
                val resp = container.authApi.register(
                    email = cur.email,
                    password = cur.password,
                    verifyCode = cur.verifyCode,
                    promoCode = cur.promoCode,
                    invitationCode = cur.invitationCode,
                    captchaProof = proof,
                )
                val user = resp.user ?: container.authApi.getCurrentUser()
                container.setLoggedIn(user)
                _ui.update { it.copy(isSubmitting = false) }
            } catch (e: Throwable) {
                _ui.update { it.copy(isSubmitting = false, errorMsg = e.message ?: "Registration failed") }
                if (needsCaptcha) clearCaptchaAndReload()
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

    fun cycleColorMode() {
        val next = when (container.settingsState.value.colorMode) {
            ColorMode.SYSTEM -> ColorMode.LIGHT
            ColorMode.LIGHT -> ColorMode.DARK
            ColorMode.DARK -> ColorMode.SYSTEM
        }
        container.setColorMode(next)
    }

    private fun resolveSelectedHostUrl(): String {
        val current = ServerUrlUtils.normalizeServerUrl(_ui.value.serverUrlInput)
        if (current.isNotEmpty()) return current
        return GugagaHosts.pickFastestReachable(_ui.value.hostProbes)?.host?.url
            ?: GugagaHosts.PRESETS.first().url
    }

    private suspend fun ensureServerConfigured(): Boolean {
        val normalized = resolveSelectedHostUrl()
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
