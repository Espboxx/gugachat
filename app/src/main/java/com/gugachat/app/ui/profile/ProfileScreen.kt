package com.gugachat.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.util.FormatUtils
import com.gugachat.app.data.api.AppUpdateService
import com.gugachat.app.domain.chat.RetrievalMode
import com.gugachat.app.ui.chat.ExternalImportSheet
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderEditorSheet
import com.gugachat.app.ui.providers.ApiProviderImportSheet

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
                com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, "qq_group", "587660465")
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
                                com.gugachat.app.core.util.ClipboardUtils.copyText(ctx, "endpoint", copyValue)
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
internal fun CompactStatItem(
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

