package com.gugachat.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.AppContainer
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.util.FormatUtils
import com.gugachat.app.ui.chat.ExternalImportSheet
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderEditorSheet
import com.gugachat.app.ui.providers.ApiProviderManagerCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    viewModel: ProfileViewModel,
    imageGroupsCount: Int? = null,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val ctx = LocalContext.current
    val isZh = strings.language == AppLanguage.ZH

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeepSeekImport by remember { mutableStateOf(false) }
    var editingProviderDraft by remember { mutableStateOf<ApiProviderDraft?>(null) }
    var activeBackupOp by remember { mutableStateOf<String?>(null) }

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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            activeBackupOp = "export"
            viewModel.exportBackup(uri, ctx.contentResolver, strings.chat.exportDone)
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
                successMessage = strings.chat.importDone,
                failedMessage = strings.chat.importFailed,
            )
        }
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
        GuAwningHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.profile.title,
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!state.isDirectApiOnly) {
                    IconButton(
                        onClick = viewModel::refreshProfile,
                        enabled = !state.isRefreshing,
                        modifier = Modifier.size(44.dp),
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                color = c.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = strings.profile.refreshProfile,
                                tint = c.ink,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val user = state.user
            val displayName = user?.username?.ifBlank { user.email } ?: "GuGuUser"
            val showEmailSubtitle = !user?.email.isNullOrBlank() &&
                !user!!.email.equals(displayName, ignoreCase = true)
            val resolvedGroupsCount = imageGroupsCount ?: state.imageGroupsCount ?: (user?.concurrency ?: 0)

            // Account Hero Card (includes folded Server URL footer)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_guguga_badge),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = displayName,
                                color = c.ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            val isDirect = state.isDirectApiOnly
                            val isAdmin = user?.role.equals("admin", ignoreCase = true)
                            GuBadge(
                                text = when {
                                    isDirect -> strings.providers.directModeBadge
                                    isAdmin -> strings.profile.roleAdmin
                                    else -> strings.profile.roleUser
                                },
                                variant = if (isDirect || isAdmin) GuBadgeVariant.HONEY else GuBadgeVariant.BLUE,
                            )
                        }
                        if (showEmailSubtitle) {
                            Text(
                                text = user!!.email,
                                color = c.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!state.isDirectApiOnly) {
                            Text(
                                text = "${strings.profile.idPrefix} ${user?.id ?: 0L}",
                                color = c.muted,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(
                        label = strings.profile.statKeys,
                        value = "$resolvedGroupsCount",
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = strings.profile.statBalance,
                        value = if (state.isDirectApiOnly) "—" else FormatUtils.formatBalance(user?.balance ?: 0.0),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = strings.profile.statWorks,
                        value = "${state.worksCount}",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!state.isDirectApiOnly && state.settings.serverUrl.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = c.muted,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = state.settings.serverUrl,
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("server_url", state.settings.serverUrl))
                                container.toastController.show(strings.common.copied)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = strings.common.copy,
                                tint = c.muted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            ApiProviderManagerCard(
                providers = state.providers,
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
            )

            // Appearance & Preferences Card (clean section dividers, unified primary pill selection)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isZh) "外观与偏好" else "Appearance & Preferences",
                    color = c.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(12.dp))

                Text(strings.profile.themeStyleLabel, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = strings.profile.themeGuguga,
                        selected = state.settings.themeStyle == ThemeStyle.GUGUGA,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.GUGUGA) },
                    )
                    GuPill(
                        label = strings.profile.themeClassic,
                        selected = state.settings.themeStyle == ThemeStyle.CLASSIC,
                        onClick = { viewModel.setThemeStyle(ThemeStyle.CLASSIC) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))

                Text(strings.profile.colorModeLabel, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = strings.profile.colorSystem,
                        selected = state.settings.colorMode == ColorMode.SYSTEM,
                        onClick = { viewModel.setColorMode(ColorMode.SYSTEM) },
                    )
                    GuPill(
                        label = strings.profile.colorLight,
                        selected = state.settings.colorMode == ColorMode.LIGHT,
                        onClick = { viewModel.setColorMode(ColorMode.LIGHT) },
                    )
                    GuPill(
                        label = strings.profile.colorDark,
                        selected = state.settings.colorMode == ColorMode.DARK,
                        onClick = { viewModel.setColorMode(ColorMode.DARK) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))

                Text(strings.profile.languageLabel, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuPill(
                        label = AppLanguage.ZH.displayName,
                        selected = state.settings.language == AppLanguage.ZH,
                        onClick = { viewModel.setLanguage(AppLanguage.ZH) },
                    )
                    GuPill(
                        label = AppLanguage.EN.displayName,
                        selected = state.settings.language == AppLanguage.EN,
                        onClick = { viewModel.setLanguage(AppLanguage.EN) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))

                Text(strings.chat.memoryBudgetLabel, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "standard", "high").forEach { preset ->
                        val label = when (preset) {
                            "low" -> if (isZh) "精简 (2K)" else "Low (2K)"
                            "high" -> if (isZh) "高配 (8K)" else "High (8K)"
                            else -> if (isZh) "标准 (4K)" else "Standard (4K)"
                        }
                        GuPill(
                            label = label,
                            selected = state.settings.memoryBudget == preset || (preset == "standard" && state.settings.memoryBudget == "4K"),
                            onClick = { viewModel.setMemoryBudget(preset) },
                        )
                    }
                }
            }

            // Backup & Restore Card (harmonized sibling actions + loading state)
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Text(strings.profile.backupSectionTitle, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuButton(
                        onClick = { exportLauncher.launch("gugachat-backup-${System.currentTimeMillis()}.json") },
                        enabled = !state.isBackupBusy,
                        loading = state.isBackupBusy && activeBackupOp == "export",
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.profile.exportBackup, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GuButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        enabled = !state.isBackupBusy,
                        loading = state.isBackupBusy && activeBackupOp == "import",
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.profile.importBackup, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                GuButton(
                    onClick = { showDeepSeekImport = true },
                    compact = true,
                    variant = GuButtonVariant.SUBTLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.chat.importExternalTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Softened Sign Out / Switch Account action
            if (state.isDirectApiOnly) {
                GuButton(
                    onClick = { showLogoutConfirm = true },
                    variant = GuButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.providers.switchToSub2api, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    onClick = { showLogoutConfirm = true },
                    shape = RoundedCornerShape(999.dp),
                    color = c.dangerBg.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = c.danger,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.profile.logout,
                            color = c.danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(c.surfaceSoft)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Column {
            Text(label, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(value, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}
