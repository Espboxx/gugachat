package com.gugachat.app.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gugachat.app.AppContainer
import com.gugachat.app.AuthSessionState
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.storage.AppSettings
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.data.api.UserDto
import com.gugachat.app.data.local.ChatBackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val user: UserDto? = null,
    val settings: AppSettings = AppSettings(),
    val worksCount: Int = 0,
    val isRefreshing: Boolean = false,
    val isBackupBusy: Boolean = false,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(
        ProfileUiState(
            user = (container.authSession.value as? AuthSessionState.LoggedIn)?.user,
            settings = container.settingsState.value,
            worksCount = container.imageWorksTotal.value,
        ),
    )
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            container.authSession.collect { session ->
                _ui.update { it.copy(user = (session as? AuthSessionState.LoggedIn)?.user) }
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
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            try {
                val user = runCatching { container.authApi.getUserProfile() }
                    .getOrElse { container.authApi.getCurrentUser() }
                container.setLoggedIn(user)
                container.toastController.show("Profile updated")
            } catch (e: Throwable) {
                container.toastController.show(e.message ?: "Refresh failed", isError = true)
            } finally {
                _ui.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setThemeStyle(style: ThemeStyle) = container.setThemeStyle(style)
    fun setColorMode(mode: ColorMode) = container.setColorMode(mode)
    fun setLanguage(lang: AppLanguage) = container.setLanguage(lang)
    fun setMemoryBudget(budget: String) {
        viewModelScope.launch { container.settingsStore.setMemoryBudget(budget) }
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
                withContext(Dispatchers.IO) {
                    ChatBackupCodec.importJson(container.database, raw, replaceExisting = replaceExisting)
                }
                container.toastController.show(successMessage)
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
