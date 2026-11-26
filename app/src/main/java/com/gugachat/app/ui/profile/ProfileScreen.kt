package com.gugachat.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuConfirmDialog
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuSectionHeader
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuSettingsRow
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.theme.ThemeStyle
import com.gugachat.app.core.theme.UiScalePreset
import com.gugachat.app.core.util.FormatUtils
import com.gugachat.app.data.api.AppUpdateService
import com.gugachat.app.ui.chat.ExternalImportSheet
import com.gugachat.app.ui.providers.ApiProviderDraft
import com.gugachat.app.ui.providers.ApiProviderEditorSheet
import com.gugachat.app.ui.providers.ApiProviderManagerCard

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
                    .height(46.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.profile.title,
                    color = c.ink,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!state.isDirectApiOnly) {
                    IconButton(
                        onClick = viewModel::refreshProfile,
                        enabled = !state.isRefreshing,
                        modifier = Modifier.size(38.dp),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val user = state.user
            val displayName = user?.username?.ifBlank { user.email } ?: "GuGuUser"
            val resolvedGroupsCount = imageGroupsCount ?: state.imageGroupsCount ?: (user?.concurrency ?: 0)
            val heroMetaLine = if (state.isDirectApiOnly) {
                if (isZh) "本地加密直连 · 随时可切换账号" else "Local encrypted BYOK · Switch anytime"
            } else {
                "${strings.profile.idPrefix} ${user?.id ?: 0L}"
            }

            // 1. Account Hero Card
            GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(c.primarySoft)
                            .border(1.2.dp, c.primary.copy(alpha = 0.34f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_guguga_badge),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
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
                                fontSize = 17.sp,
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
                        Text(
                            text = heroMetaLine,
                            color = c.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    StatCell(
                        label = strings.profile.statKeys,
                        value = "$resolvedGroupsCount",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!state.isDirectApiOnly && state.settings.serverUrl.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
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
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = strings.common.copy,
                                tint = c.muted,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            // 2. Custom API Channels Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuSectionHeader(
                    title = if (isZh) "自定义 API 通道" else "Custom API Channels",
                    subtitle = strings.providers.managerSubtitle,
                    trailing = {
                        Surface(
                            onClick = {
                                viewModel.clearProviderTestState()
                                editingProviderDraft = ApiProviderDraft()
                            },
                            shape = RoundedCornerShape(theme.pillRadius),
                            color = c.primarySoft,
                            border = BorderStroke(theme.borderWidth, c.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.heightIn(min = 32.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = c.primarySoftInk,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (isZh) "添加" else strings.common.add,
                                    color = c.primarySoftInk,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                )
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
                    showHeader = false,
                )
            }

            // 3. Appearance & Preferences Section
            val colorModeIcon = when (state.settings.colorMode) {
                ColorMode.SYSTEM -> Icons.Default.BrightnessAuto
                ColorMode.LIGHT -> Icons.Default.LightMode
                ColorMode.DARK -> Icons.Default.DarkMode
            }
            val indigoTint = if (theme.isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
            val indigoBg = if (theme.isDark) Color(0xFF1E1F42) else Color(0xFFEEF2FF)
            val memoryIndex = when (state.settings.memoryBudget) {
                "low" -> 0
                "high" -> 2
                else -> 1
            }
            val memoryBadgeText = when (memoryIndex) {
                0 -> "2K"
                2 -> "8K"
                else -> "4K"
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuSectionHeader(
                    title = if (isZh) "外观与偏好" else "Appearance & Preferences",
                )
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Theme Style
                    GuSettingsRow(
                        title = strings.profile.themeStyleLabel,
                        icon = Icons.Default.Palette,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showDivider = true,
                        trailing = {
                            GuSegmentedControl(
                                items = listOf(
                                    if (isZh) "咕咕嘎" else "Comic",
                                    if (isZh) "极简" else "Classic",
                                ),
                                selectedIndex = if (state.settings.themeStyle == ThemeStyle.GUGUGA) 0 else 1,
                                onSelect = { idx ->
                                    viewModel.setThemeStyle(if (idx == 0) ThemeStyle.GUGUGA else ThemeStyle.CLASSIC)
                                },
                                compact = true,
                                modifier = Modifier.width(156.dp),
                            )
                        },
                    )

                    // Row 2: Color Mode
                    GuSettingsRow(
                        title = strings.profile.colorModeLabel,
                        icon = colorModeIcon,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showDivider = true,
                        trailing = {
                            GuSegmentedControl(
                                items = listOf(
                                    if (isZh) "系统" else "Auto",
                                    strings.profile.colorLight,
                                    strings.profile.colorDark,
                                ),
                                selectedIndex = when (state.settings.colorMode) {
                                    ColorMode.SYSTEM -> 0
                                    ColorMode.LIGHT -> 1
                                    ColorMode.DARK -> 2
                                },
                                onSelect = { idx ->
                                    val next = when (idx) {
                                        1 -> ColorMode.LIGHT
                                        2 -> ColorMode.DARK
                                        else -> ColorMode.SYSTEM
                                    }
                                    viewModel.setColorMode(next)
                                },
                                compact = true,
                                modifier = Modifier.width(168.dp),
                            )
                        },
                    )

                    // Row 2.5: Global UI Scale
                    val scalePresets = UiScalePreset.entries
                    val scaleIndex = scalePresets.indexOf(state.settings.uiScale).coerceAtLeast(0)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        GuSettingsRow(
                            title = strings.profile.uiScaleLabel,
                            icon = Icons.Default.FormatSize,
                            iconTint = c.primarySoftInk,
                            iconBg = c.primarySoft,
                            showDivider = false,
                            trailing = {
                                GuBadge(
                                    text = "${(state.settings.uiScale.scale * 100).toInt()}%",
                                    variant = GuBadgeVariant.BLUE,
                                )
                            },
                        )
                        GuSegmentedControl(
                            items = listOf(
                                strings.profile.uiScaleCompact,
                                strings.profile.uiScaleComfortable,
                                strings.profile.uiScaleStandard,
                                strings.profile.uiScaleLarge,
                            ),
                            selectedIndex = scaleIndex,
                            onSelect = { idx ->
                                scalePresets.getOrNull(idx)?.let(viewModel::setUiScale)
                            },
                            compact = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 14.dp),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.78f),
                    )

                    // Row 3: Language
                    GuSettingsRow(
                        title = strings.profile.languageLabel,
                        icon = Icons.Default.Language,
                        iconTint = indigoTint,
                        iconBg = indigoBg,
                        showDivider = true,
                        trailing = {
                            GuSegmentedControl(
                                items = listOf(AppLanguage.ZH.displayName, AppLanguage.EN.displayName),
                                selectedIndex = if (state.settings.language == AppLanguage.ZH) 0 else 1,
                                onSelect = { idx ->
                                    viewModel.setLanguage(if (idx == 0) AppLanguage.ZH else AppLanguage.EN)
                                },
                                compact = true,
                                modifier = Modifier.width(156.dp),
                            )
                        },
                    )

                    // Row 4: Memory Budget
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        GuSettingsRow(
                            title = strings.chat.memoryBudgetLabel,
                            icon = Icons.Default.Psychology,
                            iconTint = c.success,
                            iconBg = c.successBg,
                            showDivider = false,
                            trailing = {
                                GuBadge(
                                    text = memoryBadgeText,
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            },
                        )
                        GuSegmentedControl(
                            items = listOf(
                                if (isZh) "精简 · 2K" else "Low · 2K",
                                if (isZh) "标准 · 4K" else "Std · 4K",
                                if (isZh) "高配 · 8K" else "High · 8K",
                            ),
                            selectedIndex = memoryIndex,
                            onSelect = { idx ->
                                val preset = when (idx) {
                                    0 -> "low"
                                    2 -> "high"
                                    else -> "standard"
                                }
                                viewModel.setMemoryBudget(preset)
                            },
                            compact = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 14.dp),
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.78f),
                    )

                    // Row 5: SSE Stream Auto-Retry Count (default 3, range 0..10)
                    val retryCount = state.settings.sseAutoRetryCount
                    val retryPresets = remember { listOf(0, 1, 3, 5) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        GuSettingsRow(
                            title = strings.profile.sseRetryLabel,
                            subtitle = strings.profile.sseRetrySubtitle,
                            icon = Icons.Default.Autorenew,
                            iconTint = c.accent,
                            iconBg = c.accentSoft,
                            showDivider = false,
                            trailing = {
                                GuBadge(
                                    text = if (retryCount == 0) {
                                        strings.profile.sseRetryOff
                                    } else {
                                        strings.profile.sseRetryTimes.fmt("count" to retryCount)
                                    },
                                    variant = if (retryCount == 0) GuBadgeVariant.NEUTRAL else GuBadgeVariant.BLUE,
                                )
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            retryPresets.forEach { presetVal ->
                                val selected = retryCount == presetVal
                                Surface(
                                    onClick = { viewModel.setSseAutoRetryCount(presetVal) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) c.primarySoft else c.surfaceSoft,
                                    border = BorderStroke(
                                        theme.borderWidth,
                                        if (selected) c.primary.copy(alpha = 0.48f) else c.border,
                                    ),
                                    modifier = Modifier.heightIn(min = 32.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (presetVal == 0) {
                                                strings.profile.sseRetryOff
                                            } else {
                                                strings.profile.sseRetryTimes.fmt("count" to presetVal)
                                            },
                                            color = if (selected) c.primarySoftInk else c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = c.surfaceSoft,
                                border = BorderStroke(theme.borderWidth, c.border),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.setSseAutoRetryCount((retryCount - 1).coerceAtLeast(0)) },
                                        enabled = retryCount > 0,
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = null,
                                            tint = if (retryCount > 0) c.ink else c.muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Text(
                                        text = "$retryCount",
                                        color = c.ink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                    IconButton(
                                        onClick = { viewModel.setSseAutoRetryCount((retryCount + 1).coerceAtMost(10)) },
                                        enabled = retryCount < 10,
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (retryCount < 10) c.ink else c.muted.copy(alpha = 0.35f),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Data Backup & Migration Section
            val exportingNow = state.isBackupBusy && activeBackupOp == "export"
            val importingNow = state.isBackupBusy && activeBackupOp == "import"
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuSectionHeader(title = strings.profile.backupSectionTitle)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = strings.profile.exportBackup,
                        subtitle = if (isZh) "导出全部本地对话与长期记忆为 JSON" else "Export all local chats & memories as JSON",
                        icon = Icons.Default.Download,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = !exportingNow,
                        showDivider = true,
                        onClick = if (state.isBackupBusy) {
                            null
                        } else {
                            { exportLauncher.launch("gugachat-backup-${System.currentTimeMillis()}.json") }
                        },
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
                        title = strings.profile.importBackup,
                        subtitle = if (isZh) "从本地 JSON 备份合并恢复数据" else "Restore chats & memories from a JSON backup",
                        icon = Icons.Default.Upload,
                        iconTint = c.success,
                        iconBg = c.successBg,
                        showChevron = !importingNow,
                        showDivider = true,
                        onClick = if (state.isBackupBusy) {
                            null
                        } else {
                            { importLauncher.launch(arrayOf("application/json", "text/*")) }
                        },
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
                    GuSettingsRow(
                        title = strings.chat.importExternalTitle,
                        subtitle = if (isZh) "迁移 DeepSeek、酒馆角色卡、橙光剧本与 Chatbox" else "Migrate DeepSeek, SillyTavern, Chengguang & Chatbox",
                        icon = Icons.Default.FolderZip,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = false,
                        onClick = { showDeepSeekImport = true },
                    )
                }
            }

            // 5. About & Updates Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuSectionHeader(title = strings.update.aboutCardTitle)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    GuSettingsRow(
                        title = if (updateUi.isChecking) strings.update.checkingUpdate else strings.update.checkUpdateBtn,
                        subtitle = strings.update.currentVersionLabel.fmt("version" to BuildConfig.VERSION_NAME),
                        icon = Icons.Default.SystemUpdate,
                        iconTint = c.primarySoftInk,
                        iconBg = c.primarySoft,
                        showChevron = !updateUi.isChecking,
                        showDivider = true,
                        onClick = if (updateUi.isChecking) {
                            null
                        } else {
                            {
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
                            }
                        },
                        trailing = {
                            if (updateUi.isChecking) {
                                CircularProgressIndicator(
                                    color = c.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                GuBadge(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    variant = GuBadgeVariant.BLUE,
                                )
                            }
                        },
                    )
                    GuSettingsRow(
                        title = strings.update.manualDownloadBtn,
                        subtitle = strings.update.qqGroupLabel,
                        icon = Icons.Default.OpenInBrowser,
                        iconTint = c.tagInk,
                        iconBg = c.tag,
                        showChevron = true,
                        showDivider = false,
                        onClick = { AppUpdateService.openDownloadPage(ctx) },
                    )
                }
            }

            // 6. Sign Out / Switch Mode
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
                    shape = RoundedCornerShape(theme.pillRadius),
                    color = c.dangerBg.copy(alpha = if (theme.isDark) 0.55f else 0.48f),
                    border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.42f)),
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
                            imageVector = Icons.AutoMirrored.Filled.Logout,
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

            Spacer(Modifier.height(20.dp))
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
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = label,
                color = c.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = c.ink,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
