package com.gugachat.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuGroupedCard
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    val initialCustomExpanded = state.serverUrlInput.isNotBlank() && !GugagaHosts.isPresetUrl(state.serverUrlInput)
    var showCustomServerUrl by remember(initialCustomExpanded) { mutableStateOf(initialCustomExpanded) }

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
            .navigationBarsPadding()
            .imePadding(),
    ) {
        GuAwningHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_logo),
                    contentDescription = strings.common.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .border(theme.borderWidth, c.border, RoundedCornerShape(11.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.publicSettings?.site_name?.ifBlank { strings.auth.title } ?: strings.auth.title,
                        color = c.ink,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.publicSettings?.site_subtitle?.ifBlank { strings.auth.subtitle } ?: strings.auth.subtitle,
                        color = c.muted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = viewModel::toggleThemeStyle,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = strings.profile.themeStyleLabel,
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = viewModel::toggleLanguage,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = strings.profile.languageLabel,
                        tint = c.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.entryMode == LoginEntryMode.CHOOSER) {
                FirstLaunchModeChooser(
                    colorMode = state.colorMode,
                    onChooseGugaga = { viewModel.chooseEntryMode(LoginEntryMode.SUB2API) },
                    onChooseCustomApi = { viewModel.chooseEntryMode(LoginEntryMode.DIRECT_API) },
                    onToggleLanguage = viewModel::toggleLanguage,
                    onToggleThemeStyle = viewModel::toggleThemeStyle,
                    onCycleColorMode = viewModel::cycleColorMode,
                )
            } else {
                // Mode Switcher: 44dp Back Button + 2-segment GuSegmentedControl
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.chooseEntryMode(LoginEntryMode.CHOOSER) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c.surface)
                                .border(theme.borderWidth, c.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.providers.chooserBackBtn,
                                tint = c.ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    GuSegmentedControl(
                        items = listOf(strings.providers.entrySub2api, strings.providers.entryDirectApi),
                        selectedIndex = if (state.entryMode == LoginEntryMode.SUB2API) 0 else 1,
                        onSelect = { idx ->
                            viewModel.chooseEntryMode(
                                if (idx == 0) LoginEntryMode.SUB2API else LoginEntryMode.DIRECT_API,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.entryMode == LoginEntryMode.DIRECT_API) {
                if (state.savedProvidersCount > 0) {
                    GuCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = c.accent.copy(alpha = 0.45f),
                    ) {
                        GuButton(
                            onClick = viewModel::enterWithSavedProviders,
                            variant = GuButtonVariant.HONEY,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = strings.providers.enterSavedBtn.fmt("count" to state.savedProvidersCount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                GuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuIconTile(
                            icon = Icons.Default.Key,
                            tint = c.tagInk,
                            backgroundColor = c.tag,
                            size = 36.dp,
                            iconSize = 19.dp,
                            cornerRadius = 10.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = strings.providers.entryDirectApi,
                                color = c.ink,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = strings.providers.directSubtitle,
                                color = c.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.5.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
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
                    )
                    Spacer(Modifier.height(14.dp))
                    GuButton(
                        onClick = viewModel::saveAndEnterDirectMode,
                        enabled = state.directDraft.baseUrl.isNotBlank() && state.directDraft.apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.providers.saveAndEnterBtn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                LoginQuickSettingsRow(
                    colorMode = state.colorMode,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onToggleThemeStyle = viewModel::toggleThemeStyle,
                    onCycleColorMode = viewModel::cycleColorMode,
                )
            } else if (state.entryMode == LoginEntryMode.SUB2API) {
                // Host Picker Card (GuGroupedCard with speed-test header, grouped host rows, collapsible custom URL)
                GuGroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                GuIconTile(
                                    icon = Icons.Default.Public,
                                    tint = c.primarySoftInk,
                                    backgroundColor = c.primarySoft,
                                    size = 30.dp,
                                    iconSize = 16.dp,
                                    cornerRadius = 8.dp,
                                )
                                Text(
                                    text = strings.auth.hostPickerTitle,
                                    color = c.ink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            GuButton(
                                onClick = { viewModel.probePresetHosts(autoSelectFastest = true) },
                                loading = state.isProbingHosts,
                                compact = true,
                                variant = GuButtonVariant.SUBTLE,
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.auth.hostSpeedTestBtn, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (state.serverConnected || state.serverError != null || !state.publicSettings?.version.isNullOrBlank()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (state.serverConnected) {
                                    GuBadge(
                                        text = strings.auth.serverConnected.fmt(
                                            "site" to (state.publicSettings?.site_name?.ifBlank { "sub2api" } ?: "sub2api"),
                                        ),
                                        variant = GuBadgeVariant.EMERALD,
                                    )
                                } else if (state.serverError != null) {
                                    GuBadge(
                                        text = strings.auth.serverFailed,
                                        variant = GuBadgeVariant.DANGER,
                                    )
                                }
                                state.publicSettings?.version?.takeIf { it.isNotBlank() }?.let { v ->
                                    GuBadge("v$v", variant = GuBadgeVariant.BLUE)
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.8f),
                    )

                    val fastestHostId = remember(state.hostProbes) {
                        GugagaHosts.pickFastestReachable(state.hostProbes)?.host?.id
                    }
                    val normalizedCurrentUrl = ServerUrlUtils.normalizeServerUrl(state.serverUrlInput)

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
                                thickness = theme.borderWidth,
                                color = c.border.copy(alpha = 0.72f),
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = theme.borderWidth,
                        color = c.border.copy(alpha = 0.8f),
                    )

                    // Collapsible Custom Server URL row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clickable { showCustomServerUrl = !showCustomServerUrl }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
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
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    if (!state.serverError.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        ) {
                            InlineErrorBanner(message = state.serverError!!)
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
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
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
                        Spacer(Modifier.height(14.dp))
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
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GuButton(
                                onClick = viewModel::cancel2fa,
                                variant = GuButtonVariant.SECONDARY,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.auth.backToLogin, fontWeight = FontWeight.Bold)
                            }
                            GuButton(
                                onClick = viewModel::submit2fa,
                                loading = state.isSubmitting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.auth.verifyTotpBtn, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Full-width 2-segment GuSegmentedControl (Login / Register)
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

                        Spacer(Modifier.height(14.dp))
                        val pub = state.publicSettings
                        val regDisabled = state.activeTab == AuthFormTab.REGISTER && pub != null && !pub.registration_enabled
                        if (regDisabled) {
                            GuBadge(strings.auth.registrationDisabled, variant = GuBadgeVariant.AMBER)
                            Spacer(Modifier.height(10.dp))
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
                        Spacer(Modifier.height(11.dp))
                        GuTextField(
                            value = state.password,
                            onValueChange = viewModel::updatePassword,
                            label = strings.auth.passwordLabel,
                            placeholder = strings.auth.passwordPlaceholder,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = c.muted,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            },
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
                                Spacer(Modifier.height(11.dp))
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
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            if (pub?.promo_code_enabled == true) {
                                Spacer(Modifier.height(11.dp))
                                GuTextField(
                                    value = state.promoCode,
                                    onValueChange = viewModel::updatePromoCode,
                                    label = strings.auth.promoCodeLabel,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                )
                            }
                            if (pub?.invitation_code_enabled == true) {
                                Spacer(Modifier.height(11.dp))
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
                            Spacer(Modifier.height(12.dp))
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
                            Spacer(Modifier.height(10.dp))
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

                        Spacer(Modifier.height(16.dp))
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
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        GuButton(
                            onClick = { viewModel.setShowWebLogin(true) },
                            variant = GuButtonVariant.SUBTLE,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.auth.webLoginBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                LoginQuickSettingsRow(
                    colorMode = state.colorMode,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onToggleThemeStyle = viewModel::toggleThemeStyle,
                    onCycleColorMode = viewModel::cycleColorMode,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = c.dangerBg,
        border = BorderStroke(theme.borderWidth, c.danger.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = c.danger,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(15.dp),
            )
            Text(
                text = message,
                color = c.danger,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstLaunchModeChooser(
    colorMode: ColorMode,
    onChooseGugaga: () -> Unit,
    onChooseCustomApi: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleThemeStyle: () -> Unit,
    onCycleColorMode: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Airy 80dp Brand Logo Hero
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.primarySoft)
                .border(theme.borderWidth, c.primary.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_splash),
                contentDescription = strings.common.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
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
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = strings.providers.chooserWelcomeSubtitle,
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.5.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Choice Card 1: GuGuGa Account
        OnboardingChoiceCard(
            icon = Icons.Default.AccountCircle,
            iconTint = c.primarySoftInk,
            iconBg = c.primarySoft,
            borderColor = c.primary.copy(alpha = 0.42f),
            title = strings.providers.chooserGugagaTitle,
            badgeText = strings.providers.chooserGugagaBadge,
            badgeVariant = GuBadgeVariant.BLUE,
            description = strings.providers.chooserGugagaDesc,
            arrowTint = c.primary,
            onClick = onChooseGugaga,
        )

        // Choice Card 2: Direct BYOK API
        OnboardingChoiceCard(
            icon = Icons.Default.Key,
            iconTint = c.tagInk,
            iconBg = c.tag,
            borderColor = c.accent.copy(alpha = 0.45f),
            title = strings.providers.chooserDirectTitle,
            badgeText = strings.providers.chooserDirectBadge,
            badgeVariant = GuBadgeVariant.HONEY,
            description = strings.providers.chooserDirectDesc,
            arrowTint = c.accent,
            onClick = onChooseCustomApi,
        )

        Spacer(Modifier.height(4.dp))

        LoginQuickSettingsRow(
            colorMode = colorMode,
            onToggleLanguage = onToggleLanguage,
            onToggleThemeStyle = onToggleThemeStyle,
            onCycleColorMode = onCycleColorMode,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingChoiceCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    borderColor: Color,
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
        borderColor = borderColor,
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
                size = 44.dp,
                iconSize = 22.dp,
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
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginQuickSettingsRow(
    colorMode: ColorMode,
    onToggleLanguage: () -> Unit,
    onToggleThemeStyle: () -> Unit,
    onCycleColorMode: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val colorLabel = when (colorMode) {
        ColorMode.SYSTEM -> strings.profile.colorSystem
        ColorMode.LIGHT -> strings.profile.colorLight
        ColorMode.DARK -> strings.profile.colorDark
    }
    val colorIcon = when (colorMode) {
        ColorMode.SYSTEM -> Icons.Default.BrightnessAuto
        ColorMode.LIGHT -> Icons.Default.LightMode
        ColorMode.DARK -> Icons.Default.DarkMode
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickSettingChip(
            icon = Icons.Default.Language,
            label = if (strings.language == AppLanguage.ZH) "简体中文" else "English",
            onClick = onToggleLanguage,
        )
        QuickSettingChip(
            icon = Icons.Default.Palette,
            label = if (theme.style.wireValue == "guguga") strings.profile.themeGuguga else strings.profile.themeClassic,
            onClick = onToggleThemeStyle,
        )
        QuickSettingChip(
            icon = colorIcon,
            label = colorLabel,
            onClick = onCycleColorMode,
        )
    }
}

@Composable
private fun QuickSettingChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val c = theme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(theme.pillRadius),
        color = c.surface,
        border = BorderStroke(theme.borderWidth, c.border),
        modifier = Modifier.heightIn(min = 34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = c.ink,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
            .padding(horizontal = 14.dp, vertical = 11.dp),
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
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
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
