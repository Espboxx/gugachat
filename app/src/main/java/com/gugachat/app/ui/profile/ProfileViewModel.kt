package com.gugachat.app.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.ApiProviderConfig
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.data.api.ApiException
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.local.ChatBackupCodec
import com.gugachat.app.ui.providers.ApiProviderDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(
        ProfileUiState(
            user = (container.authSession.value as? AuthSessionState.LoggedIn)?.user,
            isDirectApiOnly = (container.authSession.value as? AuthSessionState.LoggedIn)?.isDirectApiOnly == true,
            settings = container.settingsState.value,
            worksCount = container.imageWorksTotal.value,
            providers = container.apiProviderStore.currentProviders(),
        ),
    )
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
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
                _ui.update { it.copy(settings = st) }
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
    }

    private fun refreshImageGroupsCount() {
        viewModelScope.launch {
            val customImgCount = container.apiProviderStore.enabledProviders().count { it.protocol.supportsImages }
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
        viewModelScope.launch { container.settingsStore.setMemoryBudget(budget) }
    }

    fun setSseAutoRetryCount(count: Int) {
        viewModelScope.launch { container.settingsStore.setSseAutoRetryCount(count) }
    }

    fun exportBackup(uri: Uri, contentResolver: ContentResolver, successMessage: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isBackupBusy = true) }
            try {
                val json = withContext(Dispatchers.IO) {
                    ChatBackupCodec.exportAll(container.database)
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
                container.toastController.show(successMessage)
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
        successMessage: String,
        failedMessage: String,
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(isBackupBusy = true) }
            try {
                val raw = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Cannot read file")
                }
                val res = withContext(Dispatchers.IO) {
                    ChatBackupCodec.importJson(container.database, raw, replaceExisting = replaceExisting)
                }
                container.toastController.show(
                    successMessage.fmt("conversations" to res.conversationsCount, "memories" to res.memoriesCount),
                )
            } catch (_: Throwable) {
                container.toastController.show(failedMessage, isError = true)
            } finally {
                _ui.update { it.copy(isBackupBusy = false) }
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
