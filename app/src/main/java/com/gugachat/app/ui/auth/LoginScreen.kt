package com.gugachat.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gugachat.app.R
import com.gugachat.app.core.i18n.AppLanguage
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.i18n.fmt
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.GuCard
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.GuTextField
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.ServerUrlUtils
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
    var passwordVisible by remember { mutableStateOf(false) }

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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_logo),
                    contentDescription = strings.common.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.publicSettings?.site_name?.ifBlank { strings.auth.title } ?: strings.auth.title,
                        color = c.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = state.publicSettings?.site_subtitle?.ifBlank { strings.auth.subtitle } ?: strings.auth.subtitle,
                        color = c.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = viewModel::toggleThemeStyle) {
                    Icon(Icons.Default.Palette, contentDescription = strings.profile.themeStyleLabel, tint = c.ink)
                }
                IconButton(onClick = viewModel::toggleLanguage) {
                    Icon(Icons.Default.Language, contentDescription = strings.profile.languageLabel, tint = c.ink)
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
                    onChooseGugaga = { viewModel.chooseEntryMode(LoginEntryMode.SUB2API) },
                    onChooseCustomApi = { viewModel.chooseEntryMode(LoginEntryMode.DIRECT_API) },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.chooseEntryMode(LoginEntryMode.CHOOSER) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.providers.chooserBackBtn,
                            tint = c.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuPill(
                            label = strings.providers.entrySub2api,
                            selected = state.entryMode == LoginEntryMode.SUB2API,
                            onClick = { viewModel.chooseEntryMode(LoginEntryMode.SUB2API) },
                        )
                        GuPill(
                            label = strings.providers.entryDirectApi,
                            selected = state.entryMode == LoginEntryMode.DIRECT_API,
                            onClick = { viewModel.chooseEntryMode(LoginEntryMode.DIRECT_API) },
                            useHoneySelected = true,
                        )
                    }
                }
            }

            if (state.entryMode == LoginEntryMode.DIRECT_API) {
                if (state.savedProvidersCount > 0) {
                    GuCard(modifier = Modifier.fillMaxWidth()) {
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
                    Text(
                        text = strings.providers.entryDirectApi,
                        color = c.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.providers.directSubtitle,
                        color = c.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
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
            } else if (state.entryMode == LoginEntryMode.SUB2API) {
                // Server URL Card
                GuCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.auth.serverUrlLabel,
                        color = c.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
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
                }
                Spacer(Modifier.height(10.dp))
                // GuGuGa Preset Hosts with Live Latency Probe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.auth.hostPickerTitle,
                        color = c.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    GuButton(
                        onClick = { viewModel.probePresetHosts(autoSelectFastest = true) },
                        loading = state.isProbingHosts,
                        compact = true,
                        variant = GuButtonVariant.HONEY,
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.auth.hostSpeedTestBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))

                val fastestHostId = remember(state.hostProbes) {
                    GugagaHosts.pickFastestReachable(state.hostProbes)?.host?.id
                }
                val normalizedCurrentUrl = ServerUrlUtils.normalizeServerUrl(state.serverUrlInput)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.hostProbes.forEach { item ->
                        PresetHostRow(
                            item = item,
                            selected = normalizedCurrentUrl.equals(item.host.url, ignoreCase = true),
                            isFastest = item.status == HostProbeStatus.REACHABLE && item.host.id == fastestHostId,
                            onClick = { viewModel.selectPresetHost(item.host) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border)
                Spacer(Modifier.height(10.dp))

                GuTextField(
                    value = state.serverUrlInput,
                    onValueChange = viewModel::updateServerUrlInput,
                    label = strings.auth.hostCustomUrlHint,
                    placeholder = strings.auth.serverUrlPlaceholder,
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = c.muted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.probeServer() }),
                )
                if (!state.serverError.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.serverError!!, color = c.danger, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GuButton(
                        onClick = { viewModel.probeServer() },
                        loading = state.isProbing,
                        compact = true,
                        variant = GuButtonVariant.SECONDARY,
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isProbing) strings.auth.connecting else strings.auth.testConnection,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    state.publicSettings?.version?.takeIf { it.isNotBlank() }?.let { v ->
                        GuBadge("v$v", variant = GuBadgeVariant.BLUE)
                    }
                }
            }

            // Auth Card
            GuCard(modifier = Modifier.fillMaxWidth()) {
                if (state.requires2fa) {
                    Text(
                        text = strings.auth.totpTitle,
                        color = c.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.auth.totpSubtitle.fmt("email" to state.maskedEmail.ifBlank { state.email }),
                        color = c.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    GuTextField(
                        value = state.totpCode,
                        onValueChange = viewModel::updateTotpCode,
                        label = strings.auth.totpCodeLabel,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                    state.errorMsg?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = c.danger, fontSize = 12.sp)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuPill(
                            label = strings.auth.tabLogin,
                            selected = state.activeTab == AuthFormTab.LOGIN,
                            onClick = { viewModel.setActiveTab(AuthFormTab.LOGIN) },
                        )
                        GuPill(
                            label = strings.auth.tabRegister,
                            selected = state.activeTab == AuthFormTab.REGISTER,
                            onClick = { viewModel.setActiveTab(AuthFormTab.REGISTER) },
                        )
                    }

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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(10.dp))
                    GuTextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        label = strings.auth.passwordLabel,
                        placeholder = strings.auth.passwordPlaceholder,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = c.muted,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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
                                )
                                GuButton(
                                    onClick = viewModel::sendVerifyCode,
                                    enabled = state.verifyCountdownSec <= 0,
                                    variant = GuButtonVariant.SECONDARY,
                                ) {
                                    Text(
                                        if (state.verifyCountdownSec > 0) {
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
                            Spacer(Modifier.height(10.dp))
                            GuTextField(
                                value = state.promoCode,
                                onValueChange = viewModel::updatePromoCode,
                                label = strings.auth.promoCodeLabel,
                            )
                        }
                        if (pub?.invitation_code_enabled == true) {
                            Spacer(Modifier.height(10.dp))
                            GuTextField(
                                value = state.invitationCode,
                                onValueChange = viewModel::updateInvitationCode,
                                label = strings.auth.invitationCodeLabel,
                            )
                        }
                    }

                    val hasCaptchaOrOAuth = pub != null && (
                        pub.turnstile_enabled ||
                            pub.tencent_captcha_enabled ||
                            pub.aliyun_captcha_enabled ||
                            pub.linuxdo_oauth_enabled ||
                            pub.github_oauth_enabled ||
                            pub.google_oauth_enabled ||
                            pub.oidc_oauth_enabled
                        )
                    if (hasCaptchaOrOAuth) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = strings.auth.captchaRequiredHint,
                            color = c.warning,
                            fontSize = 11.sp,
                        )
                    }

                    state.errorMsg?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = c.danger, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))
                    GuButton(
                        onClick = {
                            if (state.activeTab == AuthFormTab.LOGIN) viewModel.submitLogin() else viewModel.submitRegister()
                        },
                        enabled = !regDisabled,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.activeTab == AuthFormTab.LOGIN) strings.auth.loginBtn else strings.auth.registerBtn,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    GuButton(
                        onClick = { viewModel.setShowWebLogin(true) },
                        variant = GuButtonVariant.HONEY,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.auth.webLoginBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GuBadge(
                            text = if (strings.language == AppLanguage.ZH) "简体中文" else "English",
                            variant = GuBadgeVariant.NEUTRAL,
                        )
                        GuBadge(
                            text = if (theme.style.wireValue == "guguga") strings.profile.themeGuguga else strings.profile.themeClassic,
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstLaunchModeChooser(
    onChooseGugaga: () -> Unit,
    onChooseCustomApi: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash),
            contentDescription = strings.common.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
        Text(
            text = strings.providers.chooserWelcomeTitle,
            color = c.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = strings.providers.chooserWelcomeSubtitle,
            color = c.muted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(4.dp))

        GuCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = c.primary,
            onClick = onChooseGugaga,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = strings.providers.chooserGugagaTitle,
                            color = c.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        GuBadge(
                            text = strings.providers.chooserGugagaBadge,
                            variant = GuBadgeVariant.BLUE,
                        )
                    }
                    Text(
                        text = strings.providers.chooserGugagaDesc,
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        GuCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = c.accent,
            onClick = onChooseCustomApi,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = strings.providers.chooserDirectTitle,
                            color = c.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        GuBadge(
                            text = strings.providers.chooserDirectBadge,
                            variant = GuBadgeVariant.HONEY,
                        )
                    }
                    Text(
                        text = strings.providers.chooserDirectDesc,
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = c.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuBadge(
                text = if (strings.language == AppLanguage.ZH) "简体中文" else "English",
                variant = GuBadgeVariant.NEUTRAL,
            )
            GuBadge(
                text = if (theme.style.wireValue == "guguga") strings.profile.themeGuguga else strings.profile.themeClassic,
                variant = GuBadgeVariant.BLUE,
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
    val c = LocalAppTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.primarySoft else c.surfaceSoft)
            .border(1.dp, if (selected) c.primary else c.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) c.primary else c.muted,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.host.label,
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.host.url,
                color = c.muted,
                fontSize = 11.sp,
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
                        ms <= 900L -> GuBadgeVariant.HONEY
                        else -> GuBadgeVariant.AMBER
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
