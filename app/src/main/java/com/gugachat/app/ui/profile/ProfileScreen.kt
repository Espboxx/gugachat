package com.gugachat.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.BuildConfig
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.storage.WebDavSyncConfig
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalDragHandle
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.theme.resolveAppTheme
import com.gugachat.app.core.util.FormatUtils
import com.gugachat.app.data.api.AppUpdateService
import com.gugachat.app.data.api.UpdateChannel
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.ui.chat.ExternalImportSheet
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderEditorSheet
import com.gugachat.app.ui.providers.ApiProviderImportSheet
import com.gugachat.app.ui.providers.ApiProviderManagerCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    container: AppContainer,
    viewModel: ProfileViewModel,
    imageGroupsCount: Int? = null,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val updateUi by container.updateController.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 6 } }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeepSeekImport by remember { mutableStateOf(false) }
    var showRetrievalSettings by remember { mutableStateOf(false) }
    var showProvidersSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showScaleLanguageSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showWebDavSheet by remember { mutableStateOf(false) }
    var showAboutUpdateSheet by remember { mutableStateOf(false) }
    var editingProviderDraft by remember { mutableStateOf<ApiProviderDraft?>(null) }
    var showProviderImport by remember { mutableStateOf(false) }
    var activeBackupOp by remember { mutableStateOf<String?>(null) }

    var backupIncludeVault by remember { mutableStateOf(true) }
    var backupExportPassphrase by remember { mutableStateOf("") }
    var backupIncludeDirectImages by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            activeBackupOp = "export"
            viewModel.exportBackup(
                uri = uri,
                contentResolver = ctx.contentResolver,
                includeVault = backupIncludeVault,
                passphrase = backupExportPassphrase,
                includeDirectImages = backupIncludeDirectImages,
                successMessage = strings.chat.exportDone,
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            activeBackupOp = "import"
            viewModel.importBackup(
                uri = uri,
                contentResolver = ctx.contentResolver,
                replaceExisting = false,
                passphrase = null,
                successMessage = strings.chat.importDone,
                failedMessage = strings.chat.importFailed,
            )
        }
    }

    if (showRetrievalSettings) {
        RetrievalSettingsSheet(
            retrievalSettings = state.retrievalSettings,
            apiProviders = state.providers,
            localModelSpecs = state.localModelSpecs,
            localModelStates = state.localModelStates,
            vectorIndexProgress = state.vectorIndexProgress,
            indexedVectorCount = state.indexedVectorCount,
            isLocalEmbedLoaded = state.isLocalEmbedLoaded,
            embeddingTestStatus = state.embeddingTestStatus,
            isTestingEmbedding = state.isTestingEmbedding,
            discoveredRetrievalModels = state.discoveredRetrievalModels,
            isFetchingRetrievalModels = state.isFetchingRetrievalModels,
            onSetRetrievalMode = viewModel::setRetrievalMode,
            onSelectLocalModel = viewModel::selectLocalModel,
            onDownloadLocalModel = viewModel::downloadLocalModel,
            onCancelLocalModelDownload = viewModel::cancelLocalModelDownload,
            onDeleteLocalModel = viewModel::deleteLocalModel,
            onUnloadLocalModelNow = viewModel::unloadLocalModelNow,
            onUpdateRemoteEmbeddingConfig = viewModel::updateRemoteEmbeddingConfig,
            onUpdateRemoteRerankConfig = viewModel::updateRemoteRerankConfig,
            onTestRemoteEmbedding = viewModel::testRemoteEmbedding,
            onFetchRemoteRetrievalModels = viewModel::fetchRemoteRetrievalModels,
            onClearVectorCache = viewModel::clearVectorCache,
            onDismiss = {
                viewModel.clearEmbeddingTestStatus()
                showRetrievalSettings = false
            },
            memoryBudget = state.settings.memoryBudget,
            onSetMemoryBudget = viewModel::setMemoryBudget,
            sseAutoRetryCount = state.settings.sseAutoRetryCount,
            onSetSseAutoRetryCount = viewModel::setSseAutoRetryCount,
        )
    }

    if (showProvidersSheet) {
        ApiProvidersManagerSheet(
            providers = state.providers,
            onImportClick = { showProviderImport = true },
            onAddClick = {
                viewModel.clearProviderTestState()
                editingProviderDraft = ApiProviderDraft()
            },
            onEditClick = { cfg ->
                viewModel.clearProviderTestState()
                editingProviderDraft = ApiProviderDraft.fromConfig(cfg)
            },
            onToggleEnabled = viewModel::toggleProviderEnabled,
            onDeleteClick = viewModel::deleteProvider,
            onDismiss = { showProvidersSheet = false },
        )
    }

    if (showAppearanceSheet) {
        AppearanceSettingsSheet(
            themeStyle = state.settings.themeStyle,
            colorMode = state.settings.colorMode,
            onSelectThemeStyle = viewModel::setThemeStyle,
            onSelectColorMode = viewModel::setColorMode,
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showScaleLanguageSheet) {
        ScaleAndLanguageSheet(
            uiScale = state.settings.uiScale,
            language = state.settings.language,
            onSelectUiScale = viewModel::setUiScale,
            onSelectLanguage = viewModel::setLanguage,
            onDismiss = { showScaleLanguageSheet = false },
        )
    }

    if (showBackupSheet) {
        val exportingNow = state.isBackupBusy && activeBackupOp == "export"
        val importingNow = state.isBackupBusy && activeBackupOp == "import"
        DataMigrationBackupSheet(
            isBackupBusy = state.isBackupBusy,
            exportingNow = exportingNow,
            importingNow = importingNow,
            includeVault = backupIncludeVault,
            onIncludeVaultChange = { backupIncludeVault = it },
            passphrase = backupExportPassphrase,
            onPassphraseChange = { backupExportPassphrase = it },
            includeDirectImages = backupIncludeDirectImages,
            onIncludeDirectImagesChange = { backupIncludeDirectImages = it },
            onOpenExternalImport = {
                showBackupSheet = false
                showDeepSeekImport = true
            },
            onExportBackup = {
                exportLauncher.launch("gugachat-backup-${System.currentTimeMillis()}.gugubackup")
            },
            onImportBackup = {
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showBackupSheet = false },
        )
    }

    if (showWebDavSheet) {
        WebDavSyncSheet(
            config = state.webDavConfig,
            password = state.webDavPassword,
            passphrase = state.webDavPassphrase,
            isTesting = state.isTestingWebDav,
            testStatus = state.webDavTestStatus,
            remoteUpdatedAt = state.webDavRemoteUpdatedAt,
            isSyncBusy = state.isWebDavSyncBusy,
            lastSyncSummary = state.lastWebDavSyncSummary,
            onTestConnection = { cfg, pwd, pass ->
                viewModel.testWebDavConnection(cfg, pwd, pass)
            },
            onSyncNow = { cfg, pwd, pass ->
                viewModel.syncWebDavNow(cfg, pwd, pass)
            },
            onRestoreFromCloud = { cfg, pwd, pass ->
                viewModel.restoreFromWebDav(cfg, pwd, pass)
            },
            onSaveSettings = { cfg, pwd, pass ->
                viewModel.updateWebDavSettings(cfg, pwd, pass)
            },
            onDismiss = {
                viewModel.clearWebDavTestStatus()
                showWebDavSheet = false
            },
        )
    }

    state.pendingRestoreUri?.let { pendingUri ->
        RestorePassphraseDialog(
            errorMessage = state.pendingRestorePassphraseError,
            isBusy = state.isBackupBusy,
            onConfirm = { pass ->
                viewModel.importBackup(
                    uri = pendingUri,
                    contentResolver = ctx.contentResolver,
                    replaceExisting = state.pendingRestoreReplaceExisting,
                    passphrase = pass,
                    successMessage = strings.chat.importDone,
                    failedMessage = strings.chat.importFailed,
                )
            },
            onDismiss = viewModel::dismissRestorePassphrasePrompt,
        )
    }

    if (showAboutUpdateSheet) {
        AboutUpdateSheet(
            updateChannel = state.settings.updateChannel,
            isChecking = updateUi.isChecking,
            hasUpdate = updateUi.availableUpdate != null,
            latestVersion = updateUi.availableUpdate?.versionName,
            onCheckUpdate = {
                container.updateController.checkForUpdate(
                    silent = false,
                    onUpToDate = { ver ->
                        container.toastController.show(
                            strings.update.alreadyLatestToast.fmt("version" to ver),
                        )
                    },
                    onError = {
                        container.toastController.show(strings.update.checkFailedToast, isError = true)
                    },
                )
            },
            onSelectChannel = viewModel::setUpdateChannel,
            onCopyQqGroup = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("qq_group", "587660465"))
                container.toastController.show(strings.common.copied)
            },
            onOpenDownloadPage = {
                val manualUrl = AppUpdateService.resolveManualDownloadUrl(
                    info = updateUi.availableUpdate,
                    channel = state.settings.updateChannel,
                    preferCn = isZh,
                )
                AppUpdateService.openDownloadPage(ctx, manualUrl)
            },
            onDismiss = { showAboutUpdateSheet = false },
        )
    }

    if (showDeepSeekImport) {
        ExternalImportSheet(
            container = container,
            onImported = {},
            onDismiss = { showDeepSeekImport = false },
        )
    }

    editingProviderDraft?.let { draft ->
        ApiProviderEditorSheet(
            initialDraft = draft,
            isTesting = state.providerTesting,
            testSuccessCount = state.providerTestCount,
            testError = state.providerTestError,
            onDraftChange = {
                editingProviderDraft = it
                viewModel.clearProviderTestState()
            },
            onTestConnection = viewModel::testProvider,
            onSave = {
                viewModel.saveProvider(it)
                editingProviderDraft = null
            },
            onDismiss = {
                editingProviderDraft = null
                viewModel.clearProviderTestState()
            },
        )
    }

    if (showProviderImport) {
        ApiProviderImportSheet(
            onImportSelected = { selected ->
                viewModel.importProviders(selected)
            },
            onDismiss = { showProviderImport = false },
        )
    }

    if (showLogoutConfirm) {
        GuConfirmDialog(
            title = if (state.isDirectApiOnly) strings.providers.switchToSub2api else strings.profile.logoutConfirmTitle,
            message = strings.profile.logoutConfirmMsg,
            confirmText = if (state.isDirectApiOnly) strings.common.confirm else strings.profile.logout,
            cancelText = strings.common.cancel,
            isDestructive = !state.isDirectApiOnly,
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout(strings.profile.loggedOut)
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page),
    ) {
        // 1. Scroll-aware 48.dp single-row top bar
        GuAwningHeader(
            dividerColor = if (isScrolled) c.border.copy(alpha = 0.55f) else Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.profile.title,
                    color = c.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!state.isDirectApiOnly) {
                    IconButton(
                        onClick = viewModel::refreshProfile,
                        enabled = !state.isRefreshing,
                        modifier = Modifier.size(36.dp),
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                color = c.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = strings.profile.refreshProfile,
                                tint = c.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val user = state.user
            val displayName = user?.username?.ifBlank { user.email } ?: "GuGuUser"
            val enabledProvidersCount = remember(state.providers) { state.providers.count { it.enabled } }
            val resolvedGroupsCount = imageGroupsCount ?: state.imageGroupsCount ?: (user?.concurrency ?: 0)
            val channelStatCount = if (state.isDirectApiOnly) enabledProvidersCount else resolvedGroupsCount
            val isDirect = state.isDirectApiOnly
            val isAdmin = user?.role.equals("admin", ignoreCase = true)

            // 2. Compact Account Hero Card (~108.dp)
            GuCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_guguga_badge),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = displayName,
                                color = c.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            GuBadge(
                                text = when {
                                    isDirect -> strings.providers.directModeBadge
                                    isAdmin -> strings.profile.roleAdmin
                                    else -> strings.profile.roleUser
                                },
                                variant = if (isDirect || isAdmin) GuBadgeVariant.HONEY else GuBadgeVariant.BLUE,
                            )
                        }

                        val copyValue = if (isDirect) {
                            state.providers.firstOrNull { it.enabled }?.baseUrl?.trim().orEmpty()
                                .ifBlank { if (isZh) "本地加密直连 (BYOK)" else "Local Encrypted BYOK" }
                        } else {
                            state.settings.serverUrl.ifBlank { "ID ${user?.id ?: 0L}" }
                        }
                        val chipLabel = if (isDirect) {
                            if (isZh) "本地加密直连 · $enabledProvidersCount 通道" else "Local BYOK · $enabledProvidersCount ch"
                        } else {
                            state.settings.serverUrl
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .trimEnd('/')
                                .ifBlank { "${strings.profile.idPrefix} ${user?.id ?: 0L}" }
                        }

                        Surface(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("endpoint", copyValue))
                                container.toastController.show(strings.common.copied)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = c.surfaceSoft,
                            border = null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = chipLabel,
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = strings.common.copy,
                                    tint = c.muted,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = c.border.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))

                // Lightweight single-line stat bar: 余额 | 作品 | 通道
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactStatItem(
                        label = if (isZh) "余额" else strings.profile.statBalance,
                        value = if (isDirect) "—" else FormatUtils.formatBalance(user?.balance ?: 0.0),
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.height(14.dp),
                        thickness = 0.5.dp,
                        color = c.border.copy(alpha = 0.65f),
                    )
                    CompactStatItem(
                        label = if (isZh) "作品" else strings.profile.statWorks,
                        value = "${state.worksCount}",
                        modifier = Modifier.weight(1f),
                    )
                    VerticalDivider(
                        modifier = Modifier.height(14.dp),
                        thickness = 0.5.dp,
                        color = c.border.copy(alpha = 0.65f),
                    )
                    CompactStatItem(
                        label = if (isZh) "通道" else "Channels",
                        value = "$channelStatCount",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 3. Group 1: Models & Memory Engine
            val memorySummary = when (state.settings.memoryBudget.trim().lowercase()) {
                "2k", "low" -> "2K"
                "8k", "high" -> "8K"
                else -> "4K"
            }
            val retrievalSummary = when (state.retrievalSettings.mode) {
                RetrievalMode.LEXICAL_ONLY -> if (isZh) "纯词法" else "Lexical"
                RetrievalMode.LOCAL_SMALL_MODEL -> if (isZh) "本地小模型" else "Local Model"
                RetrievalMode.REMOTE_API -> if (isZh) "云端向量" else "Cloud API"
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GuSectionHeader(
                    title = if (isZh) "模型与记忆引擎" else "Models & Memory Engine",
                )
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isZh) "自定义 API 通道" else "Custom API Channels",
                        icon = Icons.Default.Key,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        valueText = if (isZh) "已启用 $enabledProvidersCount 个" else "$enabledProvidersCount enabled",
                        showChevron = true,
                        showDivider = true,
                        onClick = { showProvidersSheet = true },
                    )
                    GuSettingsRow(
                        title = if (isZh) "记忆预算与语义检索" else "Memory Budget & Retrieval",
                        icon = Icons.Default.Hub,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        valueText = "$memorySummary · $retrievalSummary",
                        showChevron = true,
                        showDivider = false,
                        onClick = {
                            viewModel.refreshVectorStats()
                            showRetrievalSettings = true
                        },
                    )
                }
            }

            // 4. Group 2: Appearance & Preferences
            val styleSummary = when (state.settings.themeStyle) {
                ThemeStyle.GUGUGA -> if (isZh) "咕咕嘎" else "Comic"
                ThemeStyle.CLASSIC -> if (isZh) "极简" else "Classic"
            }
            val colorSummary = when (state.settings.colorMode) {
                ColorMode.SYSTEM -> if (isZh) "自动" else "Auto"
                ColorMode.LIGHT -> strings.profile.colorLight
                ColorMode.DARK -> strings.profile.colorDark
            }
            val scaleSummary = when (state.settings.uiScale) {
                UiScalePreset.COMPACT -> strings.profile.uiScaleCompact
                UiScalePreset.COMFORTABLE -> strings.profile.uiScaleComfortable
                UiScalePreset.STANDARD -> strings.profile.uiScaleStandard
                UiScalePreset.LARGE -> strings.profile.uiScaleLarge
            }
            val langSummary = if (state.settings.language == AppLanguage.ZH) {
                if (isZh) "简中" else "ZH"
            } else {
                "EN"
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GuSectionHeader(
                    title = if (isZh) "外观与偏好" else "Appearance & Preferences",
                )
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isZh) "主题与色彩" else "Theme & Colors",
                        icon = Icons.Default.Palette,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        valueText = "$styleSummary · $colorSummary",
                        showChevron = true,
                        showDivider = true,
                        onClick = { showAppearanceSheet = true },
                        trailing = {
                            Surface(
                                onClick = {
                                    val next = if (theme.isDark) ColorMode.LIGHT else ColorMode.DARK
                                    viewModel.setColorMode(next)
                                },
                                shape = CircleShape,
                                color = c.surfaceSoft,
                                border = null,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (theme.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = strings.profile.colorModeLabel,
                                        tint = c.ink,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        },
                    )
                    GuSettingsRow(
                        title = if (isZh) "界面缩放与语言" else "UI Scale & Language",
                        icon = Icons.Default.FormatSize,
                        iconTint = c.success,
                        iconBg = c.successBg,
                        valueText = "$scaleSummary · $langSummary",
                        showChevron = true,
                        showDivider = false,
                        onClick = { showScaleLanguageSheet = true },
                    )
                }
            }

            // 5. Group 3: Data & System
            val hasAvailableUpdate = updateUi.availableUpdate != null
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GuSectionHeader(
                    title = if (isZh) "数据与系统" else "Data & System",
                )
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isZh) "外部导入与本地备份" else "Import & Local Backup",
                        subtitle = if (isZh) ".gugubackup 全量存档 · 兼容旧 JSON" else ".gugubackup archive & JSON restore",
                        icon = Icons.Default.FolderZip,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        valueText = if (state.isBackupBusy) strings.common.loading else null,
                        showChevron = !state.isBackupBusy,
                        showDivider = true,
                        onClick = { showBackupSheet = true },
                        trailing = if (state.isBackupBusy) {
                            {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    val webDavValue = when {
                        state.isWebDavSyncBusy -> strings.common.loading
                        state.webDavConfig.lastSyncAt > 0L -> formatSyncShortTime(state.webDavConfig.lastSyncAt, isZh)
                        state.webDavConfig.url.isNotBlank() -> if (isZh) "已配置" else "Configured"
                        else -> if (isZh) "未启用" else "Not set"
                    }
                    GuSettingsRow(
                        title = if (isZh) "WebDAV 加密云同步" else "WebDAV Encrypted Sync",
                        subtitle = if (isZh) "坚果云 / Nextcloud / Alist 端到端加密同步" else "Jianguoyun / Nextcloud / Alist E2EE sync",
                        icon = Icons.Default.CloudSync,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        valueText = webDavValue,
                        showChevron = !state.isWebDavSyncBusy,
                        showDivider = true,
                        onClick = { showWebDavSheet = true },
                        trailing = if (state.isWebDavSyncBusy) {
                            {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    GuSettingsRow(
                        title = if (isZh) "版本与更新" else "About & Updates",
                        icon = Icons.Default.SystemUpdate,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        valueText = "v${BuildConfig.VERSION_NAME}",
                        showChevron = true,
                        showDivider = false,
                        onClick = { showAboutUpdateSheet = true },
                        trailing = when {
                            updateUi.isChecking -> {
                                {
                                    CircularProgressIndicator(
                                        color = c.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            hasAvailableUpdate -> {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(c.accent),
                                    )
                                }
                            }
                            else -> null
                        },
                    )
                }
            }

            // 6. Restrained centered text sign-out / switch action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { showLogoutConfirm = true }) {
                    Text(
                        text = if (state.isDirectApiOnly) {
                            strings.providers.switchToSub2api
                        } else {
                            strings.profile.logout
                        },
                        color = if (state.isDirectApiOnly) c.muted else c.danger,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = c.muted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = value,
            color = c.ink,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiProvidersManagerSheet(
    providers: List<com.gugachat.app.core.storage.ApiProviderConfig>,
    onImportClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (com.gugachat.app.core.storage.ApiProviderConfig) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.providers.managerTitle,
                subtitle = strings.providers.managerSubtitle,
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GuButton(
                            onClick = onImportClick,
                            compact = true,
                            variant = GuButtonVariant.SECONDARY,
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(strings.providers.importProviderBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GuButton(
                            onClick = onAddClick,
                            compact = true,
                            variant = GuButtonVariant.SUBTLE,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (isZh) "添加" else strings.common.add, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ApiProviderManagerCard(
                    providers = providers,
                    onAddClick = onAddClick,
                    onEditClick = onEditClick,
                    onToggleEnabled = onToggleEnabled,
                    onDeleteClick = onDeleteClick,
                    showHeader = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsSheet(
    themeStyle: ThemeStyle,
    colorMode: ColorMode,
    onSelectThemeStyle: (ThemeStyle) -> Unit,
    onSelectColorMode: (ColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "主题与色彩" else "Theme & Colors",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.themeStyleLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ThemePreviewTile(
                            style = ThemeStyle.GUGUGA,
                            title = if (isZh) "咕咕嘎" else "Comic",
                            subtitle = if (isZh) "暖奶油 · 柔和圆角" else "Warm cream · Soft",
                            selected = themeStyle == ThemeStyle.GUGUGA,
                            isDark = theme.isDark,
                            onClick = { onSelectThemeStyle(ThemeStyle.GUGUGA) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemePreviewTile(
                            style = ThemeStyle.CLASSIC,
                            title = if (isZh) "极简" else "Classic",
                            subtitle = if (isZh) "中性灰 · 利落边框" else "Neutral · Crisp",
                            selected = themeStyle == ThemeStyle.CLASSIC,
                            isDark = theme.isDark,
                            onClick = { onSelectThemeStyle(ThemeStyle.CLASSIC) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.colorModeLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuSegmentedControl(
                        items = listOf(
                            strings.profile.colorSystem,
                            strings.profile.colorLight,
                            strings.profile.colorDark,
                        ),
                        selectedIndex = when (colorMode) {
                            ColorMode.SYSTEM -> 0
                            ColorMode.LIGHT -> 1
                            ColorMode.DARK -> 2
                        },
                        onSelect = { idx ->
                            onSelectColorMode(
                                when (idx) {
                                    1 -> ColorMode.LIGHT
                                    2 -> ColorMode.DARK
                                    else -> ColorMode.SYSTEM
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ThemePreviewTile(
    style: ThemeStyle,
    title: String,
    subtitle: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val previewColors = remember(style, isDark) { resolveAppTheme(style, isDark).colors }

    GuCard(
        modifier = modifier,
        backgroundColor = if (selected) c.primarySoft.copy(alpha = if (isDark) 0.55f else 0.72f) else c.surface,
        borderColor = if (selected) c.primary else null,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.page),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.primary),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColors.accent),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = c.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = c.muted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleAndLanguageSheet(
    uiScale: UiScalePreset,
    language: AppLanguage,
    onSelectUiScale: (UiScalePreset) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scalePresets = UiScalePreset.entries
    val scaleIndex = scalePresets.indexOf(uiScale).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "界面缩放与语言" else "UI Scale & Language",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.profile.uiScaleLabel,
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuBadge(
                            text = "${(uiScale.scale * 100).toInt()}%",
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                    GuSegmentedControl(
                        items = listOf(
                            strings.profile.uiScaleCompact,
                            strings.profile.uiScaleComfortable,
                            strings.profile.uiScaleStandard,
                            strings.profile.uiScaleLarge,
                        ),
                        selectedIndex = scaleIndex,
                        onSelect = { idx ->
                            scalePresets.getOrNull(idx)?.let(onSelectUiScale)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.profile.languageLabel,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GuSegmentedControl(
                        items = listOf(AppLanguage.ZH.displayName, AppLanguage.EN.displayName),
                        selectedIndex = if (language == AppLanguage.ZH) 0 else 1,
                        onSelect = { idx ->
                            onSelectLanguage(if (idx == 0) AppLanguage.ZH else AppLanguage.EN)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataMigrationBackupSheet(
    isBackupBusy: Boolean,
    exportingNow: Boolean,
    importingNow: Boolean,
    includeVault: Boolean,
    onIncludeVaultChange: (Boolean) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    includeDirectImages: Boolean,
    onIncludeDirectImagesChange: (Boolean) -> Unit,
    onOpenExternalImport: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPassphrase by remember { mutableStateOf(false) }
    val canExportNow = !isBackupBusy && (!includeVault || passphrase.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "外部导入与本地备份" else "Import & Local Backup",
                subtitle = if (isZh) "导出全量 .gugubackup 存档（含角色卡、世界书、媒体与加密密钥）"
                else "Export full .gugubackup archive & restore v2/JSON",
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuCard(backgroundColor = c.surface) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuSwitchRow(
                            label = if (isZh) "包含 API 密钥保险箱（设口令加密）" else "Include API Vault (passphrase encrypted)",
                            subtitle = if (isZh) "加密打包直连渠道、检索密钥与 WebDAV 凭据"
                            else "Encrypts Direct API keys, retrieval keys & WebDAV secrets",
                            checked = includeVault,
                            onCheckedChange = onIncludeVaultChange,
                        )
                        AnimatedVisibility(visible = includeVault) {
                            GuTextField(
                                value = passphrase,
                                onValueChange = onPassphraseChange,
                                label = if (isZh) "归档加密口令" else "Archive Encryption Passphrase",
                                placeholder = if (isZh) "输入用于解密恢复的保护口令" else "Enter passphrase for vault restore",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                visualTransformation = if (showPassphrase) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                        Icon(
                                            imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = c.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                isError = includeVault && passphrase.isBlank(),
                            )
                        }
                        HorizontalDivider(color = c.border.copy(alpha = 0.45f))
                        GuSwitchRow(
                            label = if (isZh) "打包画室直连生图原图" else "Include Atelier Direct Images",
                            subtitle = if (isZh) "关闭可保持 .gugubackup 体积轻巧" else "Keep off for lightweight archive size",
                            checked = includeDirectImages,
                            onCheckedChange = onIncludeDirectImagesChange,
                        )
                    }
                }

                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = strings.chat.importExternalTitle,
                        subtitle = if (isZh) "迁移 DeepSeek、酒馆角色卡、橙光剧本与 Chatbox" else "Migrate DeepSeek, SillyTavern, Chengguang & Chatbox",
                        icon = Icons.Default.FolderZip,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = true,
                        onClick = onOpenExternalImport,
                    )
                    GuSettingsRow(
                        title = if (isZh) "导出本地存档 (.gugubackup)" else "Export Local Archive (.gugubackup)",
                        subtitle = if (includeVault && passphrase.isBlank()) {
                            if (isZh) "请先在上方填写加密口令" else "Enter passphrase above first"
                        } else {
                            if (isZh) "打包全部对话、角色卡、世界书、预设与立绘"
                            else "Archive all chats, cards, worldbooks, presets & media"
                        },
                        icon = Icons.Default.Download,
                        iconTint = if (canExportNow) c.primarySoftInk else c.muted,
                        iconBg = if (canExportNow) c.primarySoft else c.surfaceSoft,
                        showChevron = canExportNow && !exportingNow,
                        showDivider = true,
                        onClick = if (canExportNow) onExportBackup else null,
                        trailing = if (exportingNow) {
                            {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    GuSettingsRow(
                        title = if (isZh) "从本地备份恢复 (.gugubackup / .json)" else "Restore Backup (.gugubackup / .json)",
                        subtitle = if (isZh) "自动识别 v2 加密压缩包与旧版 JSON 备份"
                        else "Auto-detects .gugubackup v2 archive & legacy JSON",
                        icon = Icons.Default.Upload,
                        iconTint = c.success,
                        iconBg = c.successBg,
                        showChevron = !importingNow,
                        showDivider = false,
                        onClick = if (isBackupBusy) null else onImportBackup,
                        trailing = if (importingNow) {
                            {
                                CircularProgressIndicator(
                                    color = c.success,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RestorePassphraseDialog(
    errorMessage: String?,
    isBusy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    var passphraseDraft by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = c.surfaceElevated,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = if (isZh) "输入备份解密口令" else "Enter Backup Passphrase",
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isZh) "此 .gugubackup 存档包含受口令保护的 API 密钥保险箱，请输入导出口令以完整恢复。"
                    else "This .gugubackup archive contains an encrypted API vault. Enter the passphrase to restore.",
                    color = c.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                GuTextField(
                    value = passphraseDraft,
                    onValueChange = { passphraseDraft = it },
                    placeholder = if (isZh) "请输入备份保护口令" else "Passphrase",
                    isError = !errorMessage.isNullOrBlank(),
                    visualTransformation = if (showPassphrase) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = c.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphraseDraft) },
                enabled = !isBusy && passphraseDraft.isNotBlank(),
            ) {
                Text(
                    text = if (isZh) "解密并恢复" else "Unlock & Restore",
                    color = c.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(strings.common.cancel, color = c.muted)
            }
        },
    )
}

private enum class WebDavPreset(val url: String, val labelZh: String, val labelEn: String) {
    JIANGUOYUN("https://dav.jianguoyun.com/dav/", "坚果云", "Jianguoyun"),
    NEXTCLOUD("https://example.com/remote.php/dav/files/username/", "Nextcloud", "Nextcloud"),
    ALIST("http://127.0.0.1:5244/dav/", "Alist", "Alist"),
    CUSTOM("", "自定义", "Custom"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSyncSheet(
    config: WebDavSyncConfig,
    password: String,
    passphrase: String,
    isTesting: Boolean,
    testStatus: String?,
    remoteUpdatedAt: Long?,
    isSyncBusy: Boolean,
    lastSyncSummary: String?,
    onTestConnection: (WebDavSyncConfig, String, String) -> Unit,
    onSyncNow: (WebDavSyncConfig, String, String) -> Unit,
    onRestoreFromCloud: (WebDavSyncConfig, String, String) -> Unit,
    onSaveSettings: (WebDavSyncConfig, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val isZh = strings.language == AppLanguage.ZH
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var urlDraft by remember { mutableStateOf(config.url) }
    var usernameDraft by remember { mutableStateOf(config.username) }
    var passwordDraft by remember { mutableStateOf(password) }
    var passphraseDraft by remember { mutableStateOf(passphrase) }
    var syncDirectImagesDraft by remember { mutableStateOf(config.syncDirectImages) }
    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var confirmCloudRestore by remember { mutableStateOf(false) }

    LaunchedEffect(config.url, config.username, config.syncDirectImages) {
        if (urlDraft.isBlank() && config.url.isNotBlank()) urlDraft = config.url
        if (usernameDraft.isBlank() && config.username.isNotBlank()) usernameDraft = config.username
        syncDirectImagesDraft = config.syncDirectImages
    }
    LaunchedEffect(password) {
        if (passwordDraft.isBlank() && password.isNotBlank()) passwordDraft = password
    }
    LaunchedEffect(passphrase) {
        if (passphraseDraft.isBlank() && passphrase.isNotBlank()) passphraseDraft = passphrase
    }

    val currentDraftConfig = WebDavSyncConfig(
        url = urlDraft.trim(),
        username = usernameDraft.trim(),
        syncDirectImages = syncDirectImagesDraft,
        autoSyncOnBackground = config.autoSyncOnBackground,
        lastSyncAt = config.lastSyncAt,
    )

    if (confirmCloudRestore) {
        GuConfirmDialog(
            title = if (isZh) "从云端恢复档案" else "Restore from WebDAV",
            message = if (isZh) "将从 WebDAV 拉取加密云端快照并按最新时间合并到本地数据库与媒体文件，确认继续？"
            else "Pull the encrypted WebDAV archive and merge latest records into local storage?",
            confirmText = if (isZh) "立即拉取恢复" else "Restore Now",
            cancelText = strings.common.cancel,
            onConfirm = {
                confirmCloudRestore = false
                onRestoreFromCloud(currentDraftConfig, passwordDraft, passphraseDraft)
            },
            onDismiss = { confirmCloudRestore = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            onSaveSettings(currentDraftConfig, passwordDraft, passphraseDraft)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = if (isZh) "WebDAV 加密云同步" else "WebDAV Encrypted Sync",
                subtitle = if (isZh) "全量 E2EE 端到端加密：仅上传密文块与增量差异" else "E2EE encrypted incremental cloud sync",
                closeContentDescription = strings.common.close,
                onClose = {
                    onSaveSettings(currentDraftConfig, passwordDraft, passphraseDraft)
                    onDismiss()
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1-tap server presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isZh) "服务器模板" else "Server Presets",
                        color = c.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WebDavPreset.entries.forEach { preset ->
                            val selected = when (preset) {
                                WebDavPreset.JIANGUOYUN -> urlDraft.contains("jianguoyun.com", ignoreCase = true)
                                WebDavPreset.NEXTCLOUD -> urlDraft.contains("remote.php/dav", ignoreCase = true)
                                WebDavPreset.ALIST -> urlDraft.contains("5244/dav", ignoreCase = true)
                                WebDavPreset.CUSTOM -> urlDraft.isBlank() || (
                                    !urlDraft.contains("jianguoyun.com", ignoreCase = true) &&
                                        !urlDraft.contains("remote.php/dav", ignoreCase = true) &&
                                        !urlDraft.contains("5244/dav", ignoreCase = true)
                                    )
                            }
                            GuPill(
                                label = if (isZh) preset.labelZh else preset.labelEn,
                                selected = selected,
                                onClick = {
                                    urlDraft = when (preset) {
                                        WebDavPreset.CUSTOM -> if (selected) urlDraft else ""
                                        else -> preset.url
                                    }
                                },
                            )
                        }
                    }
                }

                GuCard(backgroundColor = c.surface) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            label = if (isZh) "WebDAV 服务器地址" else "WebDAV Server URL",
                            placeholder = "https://dav.jianguoyun.com/dav/",
                        )
                        GuTextField(
                            value = usernameDraft,
                            onValueChange = { usernameDraft = it },
                            label = if (isZh) "账号 / 邮箱" else "Username",
                            placeholder = if (isZh) "例如：user@example.com" else "Username",
                        )
                        GuTextField(
                            value = passwordDraft,
                            onValueChange = { passwordDraft = it },
                            label = if (isZh) "应用专属密码" else "App Password",
                            placeholder = if (isZh) "坚果云请前往账户安全生成应用密码" else "App-specific password",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                        GuTextField(
                            value = passphraseDraft,
                            onValueChange = { passphraseDraft = it },
                            label = if (isZh) "云端加密口令 (E2EE Passphrase)" else "Cloud Encryption Passphrase",
                            placeholder = if (isZh) "本地加密密钥，丢失无法从云端找回" else "Secret passphrase for encryption",
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                            },
                            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Icon(
                                        imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }

                GuCard(backgroundColor = c.surface) {
                    GuSwitchRow(
                        label = if (isZh) "同步画室生图原图" else "Sync Atelier Direct Images",
                        subtitle = if (isZh) "关闭可节省坚果云每月 1GB 流量" else "Off to save 1GB/mo Jianguoyun quota",
                        checked = syncDirectImagesDraft,
                        onCheckedChange = { checked ->
                            syncDirectImagesDraft = checked
                            onSaveSettings(
                                currentDraftConfig.copy(syncDirectImages = checked),
                                passwordDraft,
                                passphraseDraft,
                            )
                        },
                    )
                }

                // Action buttons: [⚡ 测通连接], [☁️ 立即增量同步], [📥 从云端恢复]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuButton(
                        onClick = { onTestConnection(currentDraftConfig, passwordDraft, passphraseDraft) },
                        variant = GuButtonVariant.SECONDARY,
                        loading = isTesting,
                        enabled = !isTesting && !isSyncBusy,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isZh) "⚡ 测通连接" else "⚡ Test", fontSize = 12.sp, maxLines = 1)
                    }
                    GuButton(
                        onClick = { onSyncNow(currentDraftConfig, passwordDraft, passphraseDraft) },
                        variant = GuButtonVariant.PRIMARY,
                        loading = isSyncBusy,
                        enabled = !isSyncBusy && !isTesting,
                        compact = true,
                        modifier = Modifier.weight(1.25f),
                    ) {
                        Text(if (isZh) "☁️ 立即增量同步" else "☁️ Sync Now", fontSize = 12.sp, maxLines = 1)
                    }
                }

                GuButton(
                    onClick = { confirmCloudRestore = true },
                    variant = GuButtonVariant.HONEY,
                    enabled = !isSyncBusy && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isZh) "从云端恢复" else "Restore from Cloud")
                }

                // Status & Last Sync card
                if (testStatus != null || config.lastSyncAt > 0L || remoteUpdatedAt != null || lastSyncSummary != null) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surfaceSoft,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (testStatus != null) {
                                val isError = testStatus.startsWith("失败") || testStatus.startsWith("Failed")
                                Text(
                                    text = testStatus,
                                    color = if (isError) c.danger else c.success,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (config.lastSyncAt > 0L) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (isZh) {
                                            "最近本地同步：${formatSyncFullTime(config.lastSyncAt)}"
                                        } else {
                                            "Last local sync: ${formatSyncFullTime(config.lastSyncAt)}"
                                        },
                                        color = c.ink,
                                        fontSize = 12.5.sp,
                                    )
                                }
                            }
                            if (remoteUpdatedAt != null) {
                                Text(
                                    text = if (isZh) {
                                        "云端快照时间戳：${formatSyncFullTime(remoteUpdatedAt)}"
                                    } else {
                                        "Remote snapshot timestamp: ${formatSyncFullTime(remoteUpdatedAt)}"
                                    },
                                    color = c.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (!lastSyncSummary.isNullOrBlank()) {
                                Text(
                                    text = lastSyncSummary,
                                    color = c.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSyncShortTime(timestamp: Long, isZh: Boolean): String {
    if (timestamp <= 0L) return if (isZh) "未同步" else "Never"
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

private fun formatSyncFullTime(timestamp: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutUpdateSheet(
    updateChannel: UpdateChannel,
    isChecking: Boolean,
    hasUpdate: Boolean,
    latestVersion: String?,
    onCheckUpdate: () -> Unit,
    onSelectChannel: (UpdateChannel) -> Unit,
    onCopyQqGroup: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val updateChannels = UpdateChannel.entries
    val updateChannelIdx = updateChannels.indexOf(updateChannel).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.page,
        dragHandle = { GuModalDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GuSheetHeader(
                title = strings.update.aboutCardTitle,
                subtitle = strings.update.currentVersionLabel.fmt("version" to BuildConfig.VERSION_NAME),
                closeContentDescription = strings.common.close,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (isChecking) strings.update.checkingUpdate else strings.update.checkUpdateBtn,
                        subtitle = if (hasUpdate && !latestVersion.isNullOrBlank()) {
                            "v${BuildConfig.VERSION_NAME} → v$latestVersion"
                        } else {
                            strings.update.currentVersionLabel.fmt("version" to BuildConfig.VERSION_NAME)
                        },
                        icon = Icons.Default.SystemUpdate,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = !isChecking,
                        showDivider = true,
                        onClick = if (isChecking) null else onCheckUpdate,
                        trailing = {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else if (hasUpdate && !latestVersion.isNullOrBlank()) {
                                GuBadge(
                                    text = "v$latestVersion",
                                    variant = GuBadgeVariant.AMBER,
                                )
                            } else {
                                GuBadge(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                        },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        GuSettingsRow(
                            title = strings.update.channelLabel,
                            subtitle = strings.update.channelSubtitle,
                            icon = Icons.Default.Public,
                            iconTint = c.success,
                            iconBg = c.successBg,
                            showDivider = false,
                        )
                        GuSegmentedControl(
                            items = listOf(
                                strings.update.channelAuto,
                                strings.update.channelCnMirror,
                                strings.update.channelGithub,
                            ),
                            selectedIndex = updateChannelIdx,
                            onSelect = { idx ->
                                updateChannels.getOrNull(idx)?.let(onSelectChannel)
                            },
                            compact = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 14.dp),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = c.border.copy(alpha = 0.65f),
                    )

                    GuSettingsRow(
                        title = strings.update.qqGroupLabel,
                        icon = Icons.Default.ContentCopy,
                        iconTint = c.muted,
                        iconBg = c.surfaceSoft,
                        valueText = strings.common.copy,
                        showDivider = true,
                        onClick = onCopyQqGroup,
                    )

                    GuSettingsRow(
                        title = strings.update.manualDownloadBtn,
                        icon = Icons.Default.OpenInBrowser,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = false,
                        onClick = onOpenDownloadPage,
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
