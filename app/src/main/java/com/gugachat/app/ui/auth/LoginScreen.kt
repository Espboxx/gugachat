package com.gugachat.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.ColorMode
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuGroupedCard
import com.gugachat.app.core.theme.GuPasswordField
import com.gugachat.app.core.theme.GuStatusBanner
import com.gugachat.app.core.theme.GuStatusTone
import com.gugachat.app.core.theme.GuIconTile
import com.gugachat.app.core.theme.GuSegmentedControl
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.ActiveCaptchaConfig
import com.gugachat.app.data.api.GugagaHosts
import com.gugachat.app.data.api.HostLatencyResult
import com.gugachat.app.data.api.HostProbeStatus
import com.gugachat.app.ui.providers.ApiProviderFormContent
import com.gugachat.app.ui.providers.ApiProviderImportSheet
import com.gugachat.app.ui.providers.canSubmitProviderDraft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val isZh = strings.language == AppLanguage.ZH
    val focusManager = LocalFocusManager.current

    var showProviderImportSheet by remember { mutableStateOf(false) }

    val initialCustomExpanded = state.serverUrlInput.isNotBlank() && !GugagaHosts.isPresetUrl(state.serverUrlInput)
    var showCustomServerUrl by remember(initialCustomExpanded) { mutableStateOf(initialCustomExpanded) }
    var userExpandedHosts by remember { mutableStateOf(false) }

    if (showProviderImportSheet) {
        ApiProviderImportSheet(
            onImportSelected = { selected ->
                showProviderImportSheet = false
                viewModel.importProvidersFromLogin(selected)
            },
            onDismiss = { showProviderImportSheet = false },
        )
    }

    if (state.showWebLoginDialog) {
        WebLoginDialog(
            serverUrl = state.serverUrlInput,
            onDismiss = { viewModel.setShowWebLogin(false) },
            onTokensCaptured = viewModel::handleWebLoginCaptured,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (state.entryMode == LoginEntryMode.CHOOSER) {
            // Clean top-right quick icon controls under status bar
            ChooserTopActionRow(
                colorMode = state.colorMode,
                isZh = isZh,
                onToggleLanguage = viewModel::toggleLanguage,
                onCycleColorMode = viewModel::cycleColorMode,
                onToggleThemeStyle = viewModel::toggleThemeStyle,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FirstLaunchModeChooser(
                    isZh = isZh,
                    onChooseCustomApi = { viewModel.chooseEntryMode(LoginEntryMode.DIRECT_API) },
                    onChooseGugaga = { viewModel.chooseEntryMode(LoginEntryMode.SUB2API) },
                    onEnterGuest = viewModel::enterGuestMode,
                )
            }
        } else {
            // Single-row 48.dp header: [<-] + centered GuSegmentedControl
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.chooseEntryMode(LoginEntryMode.CHOOSER) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.providers.chooserBackBtn,
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    GuSegmentedControl(
                        items = listOf(strings.providers.entryDirectApi, strings.providers.entrySub2api),
                        selectedIndex = if (state.entryMode == LoginEntryMode.DIRECT_API) 0 else 1,
                        onSelect = { idx ->
                            viewModel.chooseEntryMode(
                                if (idx == 0) LoginEntryMode.DIRECT_API else LoginEntryMode.SUB2API,
                            )
                        },
                        modifier = Modifier
                            .widthIn(max = 264.dp)
                            .fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(44.dp))
            }

            if (state.entryMode == LoginEntryMode.DIRECT_API) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.savedProvidersCount > 0) {
                        GuCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = c.tag.copy(alpha = if (theme.isDark) 0.52f else 0.85f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                            onClick = viewModel::enterWithSavedProviders,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = c.tagInk,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = strings.providers.enterSavedBtn.fmt("count" to state.savedProvidersCount),
                                    color = c.tagInk,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = c.tagInk,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }

                    ApiProviderFormContent(
                        draft = state.directDraft,
                        isTesting = state.directTesting,
                        testSuccessCount = state.directTestCount,
                        testError = state.directTestError,
                        onProtocolChange = viewModel::updateDirectProtocol,
                        onNameChange = viewModel::updateDirectName,
                        onBaseUrlChange = viewModel::updateDirectBaseUrl,
                        onApiKeyChange = viewModel::updateDirectApiKey,
                        onCustomModelsChange = viewModel::updateDirectCustomModels,
                        onTestConnection = viewModel::testDirectConnection,
                        onApplyPreset = viewModel::applyDirectPreset,
                        onOpenImport = { showProviderImportSheet = true },
                        showInlineTestBar = false,
                        fadeEndColor = c.page,
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = viewModel::enterGuestMode) {
                            Text(
                                text = if (isZh) {
                                    "先逛逛 / 离线导入体验（发消息时再配 Key）"
                                } else {
                                    "Explore first / import offline (add key later)"
                                },
                                color = c.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Sticky bottom action dock (stays above IME keyboard)
                val canSubmitDirect = canSubmitProviderDraft(state.directDraft)
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = c.border.copy(alpha = 0.55f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.testDirectConnection()
                            },
                            enabled = canSubmitDirect && !state.directTesting,
                            loading = state.directTesting,
                            variant = GuButtonVariant.SECONDARY,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isZh) "测通" else "Test",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            state.directTestCount?.let { count ->
                                Spacer(Modifier.width(5.dp))
                                GuBadge(
                                    text = "$count",
                                    variant = GuBadgeVariant.EMERALD,
                                )
                            }
                        }

                        GuButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveAndEnterDirectMode()
                            },
                            enabled = canSubmitDirect,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (isZh) "保存并进入" else "Save & Enter",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            } else {
                // SUB2API Mode: Slim collapsible 1-line node status bar + Auth card in 1 screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val normalizedCurrentUrl = ServerUrlUtils.normalizeServerUrl(state.serverUrlInput)
                    val selectedProbe = remember(state.hostProbes, normalizedCurrentUrl) {
                        state.hostProbes.firstOrNull {
                            ServerUrlUtils.normalizeServerUrl(it.host.url).equals(normalizedCurrentUrl, ignoreCase = true)
                        }
                    }
                    val allProbesFailed = !state.isProbingHosts &&
                        state.hostProbes.isNotEmpty() &&
                        state.hostProbes.all { it.status == HostProbeStatus.FAILED }
                    val autoExpandHosts = !state.serverError.isNullOrBlank() ||
                        (allProbesFailed && !state.serverConnected)
                    val hostsExpanded = userExpandedHosts || autoExpandHosts

                    GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                        val nodeDisplayName = selectedProbe?.host?.label
                            ?: state.serverUrlInput.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                .ifBlank { GugagaHosts.PRESETS.first().label }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .clickable { userExpandedHosts = !hostsExpanded }
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = when {
                                    state.serverError != null -> c.danger
                                    state.serverConnected -> c.success
                                    else -> c.primary
                                },
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                text = if (isZh) "已选节点：$nodeDisplayName" else "Node: $nodeDisplayName",
                                color = c.ink,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            when {
                                state.isProbingHosts || state.isProbing -> {
                                    GuBadge(strings.auth.hostTesting, variant = GuBadgeVariant.NEUTRAL)
                                }
                                state.serverError != null -> {
                                    GuBadge(strings.auth.hostUnreachable, variant = GuBadgeVariant.DANGER)
                                }
                                selectedProbe?.status == HostProbeStatus.REACHABLE -> {
                                    val ms = selectedProbe.latencyMs ?: 0L
                                    val variant = when {
                                        ms <= 300L -> GuBadgeVariant.EMERALD
                                        ms <= 900L -> GuBadgeVariant.AMBER
                                        else -> GuBadgeVariant.DANGER
                                    }
                                    GuBadge("${ms}ms", variant = variant)
                                }
                                state.serverConnected -> {
                                    GuBadge(
                                        text = state.publicSettings?.site_name?.ifBlank { "OK" } ?: "OK",
                                        variant = GuBadgeVariant.EMERALD,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = if (isZh) {
                                        if (hostsExpanded) "收起" else "切换/测速"
                                    } else {
                                        if (hostsExpanded) "Hide" else "Switch"
                                    },
                                    color = c.primarySoftInk,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    imageVector = if (hostsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = c.primarySoftInk,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        AnimatedVisibility(visible = hostsExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = c.border.copy(alpha = 0.65f),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = strings.auth.hostPickerTitle,
                                            color = c.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        state.publicSettings?.version?.takeIf { it.isNotBlank() }?.let { v ->
                                            GuBadge("v$v", variant = GuBadgeVariant.BLUE)
                                        }
                                    }
                                    GuButton(
                                        onClick = { viewModel.probePresetHosts(autoSelectFastest = true) },
                                        loading = state.isProbingHosts,
                                        compact = true,
                                        variant = GuButtonVariant.SUBTLE,
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.auth.hostSpeedTestBtn, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                val fastestHostId = remember(state.hostProbes) {
                                    GugagaHosts.pickFastestReachable(state.hostProbes)?.host?.id
                                }
                                state.hostProbes.forEachIndexed { index, item ->
                                    PresetHostRow(
                                        item = item,
                                        selected = normalizedCurrentUrl.equals(item.host.url, ignoreCase = true),
                                        isFastest = item.status == HostProbeStatus.REACHABLE && item.host.id == fastestHostId,
                                        onClick = { viewModel.selectPresetHost(item.host) },
                                    )
                                    if (index < state.hostProbes.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 42.dp),
                                            thickness = 0.5.dp,
                                            color = c.border.copy(alpha = 0.65f),
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = c.border.copy(alpha = 0.65f),
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .clickable { showCustomServerUrl = !showCustomServerUrl }
                                        .padding(horizontal = 13.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = strings.auth.hostCustomUrlHint,
                                        color = c.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = if (showCustomServerUrl) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                AnimatedVisibility(visible = showCustomServerUrl) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 13.dp, end = 13.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuTextField(
                                            value = state.serverUrlInput,
                                            onValueChange = viewModel::updateServerUrlInput,
                                            label = strings.auth.serverUrlLabel,
                                            placeholder = strings.auth.serverUrlPlaceholder,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Done,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    focusManager.clearFocus()
                                                    viewModel.probeServer()
                                                },
                                            ),
                                        )
                                        GuButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.probeServer()
                                            },
                                            enabled = state.serverUrlInput.isNotBlank() && !state.isProbing,
                                            loading = state.isProbing,
                                            compact = true,
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = if (state.isProbing) strings.auth.connecting else strings.auth.testConnection,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }

                                if (!state.serverError.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 13.dp, end = 13.dp, bottom = 12.dp),
                                    ) {
                                        InlineErrorBanner(message = state.serverError!!)
                                    }
                                }
                            }
                        }
                    }

                    // Auth Card
                    GuCard(modifier = Modifier.fillMaxWidth()) {
                        if (state.requires2fa) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.Security,
                                    tint = c.primarySoftInk,
                                    backgroundColor = c.primarySoft,
                                    size = 36.dp,
                                    iconSize = 19.dp,
                                    cornerRadius = 10.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = strings.auth.totpTitle,
                                        color = c.ink,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = strings.auth.totpSubtitle.fmt(
                                            "email" to state.maskedEmail.ifBlank { state.email },
                                        ),
                                        color = c.muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            GuTextField(
                                value = state.totpCode,
                                onValueChange = viewModel::updateTotpCode,
                                label = strings.auth.totpCodeLabel,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { viewModel.submit2fa() }),
                            )
                            state.errorMsg?.let { err ->
                                Spacer(Modifier.height(10.dp))
                                InlineErrorBanner(message = err)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GuButton(
                                    onClick = viewModel::cancel2fa,
                                    variant = GuButtonVariant.SECONDARY,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(strings.auth.backToLogin, fontWeight = FontWeight.SemiBold)
                                }
                                GuButton(
                                    onClick = viewModel::submit2fa,
                                    loading = state.isSubmitting,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(strings.auth.verifyTotpBtn, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            GuSegmentedControl(
                                items = listOf(strings.auth.tabLogin, strings.auth.tabRegister),
                                selectedIndex = if (state.activeTab == AuthFormTab.LOGIN) 0 else 1,
                                onSelect = { idx ->
                                    viewModel.setActiveTab(
                                        if (idx == 0) AuthFormTab.LOGIN else AuthFormTab.REGISTER,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(12.dp))
                            val pub = state.publicSettings
                            val regDisabled = state.activeTab == AuthFormTab.REGISTER && pub != null && !pub.registration_enabled
                            if (regDisabled) {
                                GuBadge(strings.auth.registrationDisabled, variant = GuBadgeVariant.AMBER)
                                Spacer(Modifier.height(8.dp))
                            }

                            GuTextField(
                                value = state.email,
                                onValueChange = viewModel::updateEmail,
                                label = strings.auth.emailLabel,
                                placeholder = strings.auth.emailPlaceholder,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                ),
                            )
                            Spacer(Modifier.height(10.dp))
                            GuPasswordField(
                                value = state.password,
                                onValueChange = viewModel::updatePassword,
                                label = strings.auth.passwordLabel,
                                placeholder = strings.auth.passwordPlaceholder,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = if (state.activeTab == AuthFormTab.LOGIN) ImeAction.Done else ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (state.activeTab == AuthFormTab.LOGIN) viewModel.submitLogin()
                                    },
                                ),
                            )

                            if (state.activeTab == AuthFormTab.REGISTER) {
                                if (pub?.email_verify_enabled == true) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        GuTextField(
                                            value = state.verifyCode,
                                            onValueChange = viewModel::updateVerifyCode,
                                            label = strings.auth.verifyCodeLabel,
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                            ),
                                        )
                                        GuButton(
                                            onClick = viewModel::sendVerifyCode,
                                            enabled = state.verifyCountdownSec <= 0 &&
                                                (!state.requiresInAppCaptcha || state.captchaProof.isReady),
                                            variant = GuButtonVariant.SECONDARY,
                                            modifier = Modifier.height(54.dp),
                                        ) {
                                            Text(
                                                text = if (state.verifyCountdownSec > 0) {
                                                    strings.auth.resendCountdown.fmt("sec" to state.verifyCountdownSec)
                                                } else {
                                                    strings.auth.sendCode
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                                if (pub?.promo_code_enabled == true) {
                                    Spacer(Modifier.height(10.dp))
                                    GuTextField(
                                        value = state.promoCode,
                                        onValueChange = viewModel::updatePromoCode,
                                        label = strings.auth.promoCodeLabel,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    )
                                }
                                if (pub?.invitation_code_enabled == true) {
                                    Spacer(Modifier.height(10.dp))
                                    GuTextField(
                                        value = state.invitationCode,
                                        onValueChange = viewModel::updateInvitationCode,
                                        label = strings.auth.invitationCodeLabel,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    )
                                }
                            }

                            val needsCaptchaForForm = state.requiresInAppCaptcha && !(
                                state.activeTab == AuthFormTab.REGISTER &&
                                    pub?.email_verify_enabled == true &&
                                    state.verifyCode.isNotBlank()
                                )
                            if (needsCaptchaForForm) {
                                Spacer(Modifier.height(10.dp))
                                InAppCaptchaWidget(
                                    config = state.activeCaptcha,
                                    serverUrl = state.serverUrlInput,
                                    status = state.captchaStatus,
                                    resetNonce = state.captchaResetNonce,
                                    errorDetail = state.captchaErrorMsg,
                                    onLoading = viewModel::onCaptchaLoading,
                                    onReady = viewModel::onCaptchaReady,
                                    onVerified = viewModel::onCaptchaVerified,
                                    onExpired = viewModel::onCaptchaExpired,
                                    onError = viewModel::onCaptchaError,
                                    onRefresh = viewModel::resetCaptcha,
                                )
                            }

                            val hasRawCaptchaFlag = pub != null && (
                                pub.turnstile_enabled ||
                                    pub.tencent_captcha_enabled ||
                                    pub.aliyun_captcha_enabled
                                )
                            if (!state.requiresInAppCaptcha && hasRawCaptchaFlag) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = strings.auth.captchaRequiredHint,
                                    color = c.warning,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                )
                            }

                            val waitingForTurnstile = needsCaptchaForForm &&
                                state.activeCaptcha is ActiveCaptchaConfig.Turnstile &&
                                !state.captchaProof.isReady
                            if (waitingForTurnstile) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = strings.auth.captchaRequiredError,
                                    color = c.muted,
                                    fontSize = 11.5.sp,
                                )
                            }

                            state.errorMsg?.let { err ->
                                Spacer(Modifier.height(10.dp))
                                InlineErrorBanner(message = err)
                            }

                            Spacer(Modifier.height(14.dp))
                            GuButton(
                                onClick = {
                                    if (state.activeTab == AuthFormTab.LOGIN) {
                                        viewModel.submitLogin()
                                    } else {
                                        viewModel.submitRegister()
                                    }
                                },
                                enabled = !regDisabled && !waitingForTurnstile,
                                loading = state.isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (state.activeTab == AuthFormTab.LOGIN) {
                                        strings.auth.loginBtn
                                    } else {
                                        strings.auth.registerBtn
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            GuButton(
                                onClick = { viewModel.setShowWebLogin(true) },
                                variant = GuButtonVariant.SUBTLE,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.auth.webLoginBtn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChooserTopActionRow(
    colorMode: ColorMode,
    isZh: Boolean,
    onToggleLanguage: () -> Unit,
    onCycleColorMode: () -> Unit,
    onToggleThemeStyle: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors
    val colorIcon = when (colorMode) {
        ColorMode.SYSTEM -> Icons.Default.BrightnessAuto
        ColorMode.LIGHT -> Icons.Default.LightMode
        ColorMode.DARK -> Icons.Default.DarkMode
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onToggleLanguage,
            shape = CircleShape,
            color = c.surfaceSoft,
            border = null,
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = strings.profile.languageLabel,
                    tint = c.ink,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (isZh) "中/EN" else "EN/中",
                    color = c.ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Surface(
            onClick = onCycleColorMode,
            shape = CircleShape,
            color = c.surfaceSoft,
            border = null,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = colorIcon,
                    contentDescription = strings.profile.colorModeLabel,
                    tint = c.ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Surface(
            onClick = onToggleThemeStyle,
            shape = CircleShape,
            color = c.surfaceSoft,
            border = null,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = strings.profile.themeStyleLabel,
                    tint = c.ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    GuStatusBanner(
        message = message,
        tone = GuStatusTone.ERROR,
        modifier = modifier,
    )
}

@Composable
private fun FirstLaunchModeChooser(
    isZh: Boolean,
    onChooseCustomApi: () -> Unit,
    onChooseGugaga: () -> Unit,
    onEnterGuest: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Clean 72.dp Brand Logo Hero
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.primarySoft,
            shadowElevation = if (theme.isDark) 0.dp else 3.dp,
            border = null,
            modifier = Modifier.size(72.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = strings.providers.chooserWelcomeTitle,
                color = c.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = strings.providers.chooserWelcomeSubtitle,
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Zero-border Choice Card 1: Custom API Direct
        OnboardingChoiceCard(
            icon = Icons.Default.Key,
            iconTint = c.tagInk,
            iconBg = c.tag,
            title = strings.providers.chooserDirectTitle,
            badgeText = strings.providers.chooserDirectBadge,
            badgeVariant = GuBadgeVariant.HONEY,
            description = strings.providers.chooserDirectDesc,
            arrowTint = c.accent,
            onClick = onChooseCustomApi,
        )

        // Zero-border Choice Card 2: GuGuGa Account
        OnboardingChoiceCard(
            icon = Icons.Default.AccountCircle,
            iconTint = c.primarySoftInk,
            iconBg = c.primarySoft,
            title = strings.providers.chooserGugagaTitle,
            badgeText = strings.providers.chooserGugagaBadge,
            badgeVariant = GuBadgeVariant.BLUE,
            description = strings.providers.chooserGugagaDesc,
            arrowTint = c.primary,
            onClick = onChooseGugaga,
        )

        Spacer(Modifier.height(2.dp))

        TextButton(onClick = onEnterGuest) {
            Text(
                text = if (isZh) {
                    "先逛逛 / 离线导入存档（发消息时再配 Key）"
                } else {
                    "Explore first / import offline (add key later)"
                },
                color = c.muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingChoiceCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    badgeText: String,
    badgeVariant: GuBadgeVariant,
    description: String,
    arrowTint: Color,
    onClick: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    GuCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = null,
        contentPadding = PaddingValues(15.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuIconTile(
                icon = icon,
                tint = iconTint,
                backgroundColor = iconBg,
                size = 42.dp,
                iconSize = 21.dp,
                cornerRadius = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        color = c.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    GuBadge(
                        text = badgeText,
                        variant = badgeVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                Text(
                    text = description,
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = arrowTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PresetHostRow(
    item: HostLatencyResult,
    selected: Boolean,
    isFastest: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val rowBg = if (selected) {
        c.primarySoft.copy(alpha = if (theme.isDark) 0.55f else 0.72f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) c.primary else c.muted,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = item.host.label,
                color = if (selected) c.primarySoftInk else c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.host.url,
                color = c.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFastest) {
                GuBadge(
                    text = strings.auth.hostFastestBadge,
                    variant = GuBadgeVariant.EMERALD,
                )
            }
            when (item.status) {
                HostProbeStatus.IDLE, HostProbeStatus.TESTING -> {
                    GuBadge(strings.auth.hostTesting, variant = GuBadgeVariant.NEUTRAL)
                }
                HostProbeStatus.REACHABLE -> {
                    val ms = item.latencyMs ?: 0L
                    val variant = when {
                        ms <= 300L -> GuBadgeVariant.EMERALD
                        ms <= 900L -> GuBadgeVariant.AMBER
                        else -> GuBadgeVariant.DANGER
                    }
                    GuBadge("${ms}ms", variant = variant)
                }
                HostProbeStatus.FAILED -> {
                    GuBadge(strings.auth.hostUnreachable, variant = GuBadgeVariant.DANGER)
                }
            }
        }
    }
}
