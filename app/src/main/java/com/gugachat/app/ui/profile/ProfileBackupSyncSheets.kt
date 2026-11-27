package com.gugachat.app.ui.profile

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
import com.gugachat.app.core.theme.GuHairlineDivider
import com.gugachat.app.core.theme.GuMetaText
import com.gugachat.app.core.theme.GuModalBottomSheet
import com.gugachat.app.core.theme.GuPasswordField
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.GuSheetHeader
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuSwitchRow
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.GuTextInputDialog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataMigrationBackupSheet(
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

    GuModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                            GuPasswordField(
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
                                isError = includeVault && passphrase.isBlank(),
                            )
                        }
                        GuHairlineDivider(alpha = 0.45f)
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
internal fun RestorePassphraseDialog(
    errorMessage: String?,
    isBusy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isZh = strings.language == AppLanguage.ZH
    var passphraseDraft by remember { mutableStateOf("") }

    GuTextInputDialog(
        title = if (isZh) "输入备份解密口令" else "Enter Backup Passphrase",
        value = passphraseDraft,
        onValueChange = { passphraseDraft = it },
        confirmText = if (isZh) "解密并恢复" else "Unlock & Restore",
        cancelText = strings.common.cancel,
        onConfirm = { onConfirm(passphraseDraft) },
        onDismiss = onDismiss,
        placeholder = if (isZh) "请输入备份保护口令" else "Passphrase",
        supportingMessage = if (isZh) {
            "此 .gugubackup 存档包含受口令保护的 API 密钥保险箱，请输入导出口令以完整恢复。"
        } else {
            "This .gugubackup archive contains an encrypted API vault. Enter the passphrase to restore."
        },
        errorMessage = errorMessage,
        isPassword = true,
        singleLine = true,
        isBusy = isBusy,
    )
}

internal enum class WebDavPreset(val url: String, val labelZh: String, val labelEn: String) {
    JIANGUOYUN("https://dav.jianguoyun.com/dav/", "坚果云", "Jianguoyun"),
    NEXTCLOUD("https://example.com/remote.php/dav/files/username/", "Nextcloud", "Nextcloud"),
    ALIST("http://127.0.0.1:5244/dav/", "Alist", "Alist"),
    CUSTOM("", "自定义", "Custom"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebDavSyncSheet(
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

    GuModalBottomSheet(
        onDismissRequest = {
            onSaveSettings(currentDraftConfig, passwordDraft, passphraseDraft)
            onDismiss()
        },
        sheetState = sheetState,
        applyStatusBarsPadding = false,
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
                        GuPasswordField(
                            value = passwordDraft,
                            onValueChange = { passwordDraft = it },
                            label = if (isZh) "应用专属密码" else "App Password",
                            placeholder = if (isZh) "坚果云请前往账户安全生成应用密码" else "App-specific password",
                        )
                        GuPasswordField(
                            value = passphraseDraft,
                            onValueChange = { passphraseDraft = it },
                            label = if (isZh) "云端加密口令 (E2EE Passphrase)" else "Cloud Encryption Passphrase",
                            placeholder = if (isZh) "本地加密密钥，丢失无法从云端找回" else "Secret passphrase for encryption",
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
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

internal fun formatSyncShortTime(timestamp: Long, isZh: Boolean): String {
    if (timestamp <= 0L) return if (isZh) "未同步" else "Never"
    return com.gugachat.app.core.util.FormatUtils.formatDateTimeShort(timestamp)
}

internal fun formatSyncFullTime(timestamp: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

