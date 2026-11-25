package com.gugachat.app.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuBadge
import com.gugachat.app.core.theme.GuBadgeVariant
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.ActiveCaptchaConfig

private const val CAPTCHA_TAG = "InAppCaptcha"

private class CaptchaJsBridge(
    private val mainHandler: Handler,
    private val onReadyCb: () -> Unit,
    private val onVerifiedCb: (String, String) -> Unit,
    private val onExpiredCb: () -> Unit,
    private val onErrorCb: (String?) -> Unit,
    private val onCancelledCb: () -> Unit,
    private val onHeightChangedCb: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onReady() {
        mainHandler.post { onReadyCb() }
    }

    @JavascriptInterface
    fun onVerified(token: String?, randstr: String?) {
        val cleanToken = token?.trim().orEmpty()
        val cleanRandstr = randstr?.trim().orEmpty()
        mainHandler.post {
            if (cleanToken.isNotEmpty()) {
                onVerifiedCb(cleanToken, cleanRandstr)
            } else {
                onErrorCb("empty_token")
            }
        }
    }

    @JavascriptInterface
    fun onExpired() {
        mainHandler.post { onExpiredCb() }
    }

    @JavascriptInterface
    fun onError(detail: String?) {
        mainHandler.post { onErrorCb(detail?.trim()?.ifEmpty { null }) }
    }

    @JavascriptInterface
    fun onCancelled() {
        mainHandler.post { onCancelledCb() }
    }

    @JavascriptInterface
    fun onHeightChanged(heightCssPx: Int) {
        mainHandler.post { onHeightChangedCb(heightCssPx) }
    }
}

private val ALLOWED_CAPTCHA_HOST_SUFFIXES = listOf(
    "cloudflare.com",
    "qcloud.com",
    "qq.com",
    "alicdn.com",
    "aliyuncs.com",
)

private fun isAllowedCaptchaHost(host: String?, serverHost: String?): Boolean {
    val normalized = host?.lowercase()?.trim().orEmpty()
    if (normalized.isEmpty()) return false
    if (!serverHost.isNullOrBlank() && normalized.equals(serverHost, ignoreCase = true)) {
        return true
    }
    return ALLOWED_CAPTCHA_HOST_SUFFIXES.any { suffix ->
        normalized == suffix || normalized.endsWith(".$suffix")
    }
}

private fun handleMainFrameNavigation(
    context: Context,
    request: WebResourceRequest?,
    serverHost: String?,
): Boolean {
    if (request == null || !request.isForMainFrame) return false
    val uri = request.url ?: return false
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme == "about" || scheme == "data" || scheme == "blob") return false
    if (isAllowedCaptchaHost(uri.host, serverHost)) return false
    if (scheme == "http" || scheme == "https") {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        return true
    }
    return true
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredCaptchaWebView(
    context: Context,
    bridge: CaptchaJsBridge,
    serverHost: String?,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(this, true)

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.useWideViewPort = false
    settings.loadWithOverviewMode = false

    setBackgroundColor(Color.TRANSPARENT)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false

    addJavascriptInterface(bridge, InAppCaptchaHtmlBuilder.JS_BRIDGE_NAME)
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
                Log.d(CAPTCHA_TAG, "WebView [${it.messageLevel()}]: ${it.message()}")
            }
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean = handleMainFrameNavigation(context, request, serverHost)
    }
}

private fun releaseCaptchaWebView(wv: WebView) {
    wv.removeJavascriptInterface(InAppCaptchaHtmlBuilder.JS_BRIDGE_NAME)
    wv.stopLoading()
    wv.loadUrl("about:blank")
    wv.destroy()
}

/**
 * Native card embedding Cloudflare Turnstile inline or opening Tencent/Aliyun captcha inside an in-app modal.
 */
@Composable
fun InAppCaptchaWidget(
    config: ActiveCaptchaConfig,
    serverUrl: String,
    status: CaptchaUiStatus,
    resetNonce: Int,
    errorDetail: String?,
    onLoading: () -> Unit,
    onReady: () -> Unit,
    onVerified: (token: String, randstr: String) -> Unit,
    onExpired: () -> Unit,
    onError: (String?) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config is ActiveCaptchaConfig.None) return

    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val baseUrl = remember(serverUrl) { InAppCaptchaHtmlBuilder.buildBaseUrl(serverUrl) }
    val serverHost = remember(baseUrl) { runCatching { Uri.parse(baseUrl).host }.getOrNull() }
    val languageTag = strings.language.tag

    val onLoadingLatest by rememberUpdatedState(onLoading)
    val onReadyLatest by rememberUpdatedState(onReady)
    val onVerifiedLatest by rememberUpdatedState(onVerified)
    val onExpiredLatest by rememberUpdatedState(onExpired)
    val onErrorLatest by rememberUpdatedState(onError)

    var showActionDialog by remember { mutableStateOf(false) }

    val borderColor = when (status) {
        CaptchaUiStatus.VERIFIED -> c.success
        CaptchaUiStatus.ERROR -> c.danger
        CaptchaUiStatus.EXPIRED -> c.warning
        else -> c.border
    }

    val (badgeText, badgeVariant) = when (status) {
        CaptchaUiStatus.IDLE, CaptchaUiStatus.LOADING ->
            strings.auth.captchaLoading to GuBadgeVariant.NEUTRAL
        CaptchaUiStatus.READY ->
            strings.auth.captchaReadyHint to GuBadgeVariant.BLUE
        CaptchaUiStatus.VERIFIED ->
            strings.auth.captchaVerified to GuBadgeVariant.EMERALD
        CaptchaUiStatus.EXPIRED ->
            strings.auth.captchaExpired to GuBadgeVariant.AMBER
        CaptchaUiStatus.ERROR ->
            strings.auth.captchaError to GuBadgeVariant.DANGER
    }

    val headerIcon = when (status) {
        CaptchaUiStatus.VERIFIED -> Icons.Default.VerifiedUser
        CaptchaUiStatus.EXPIRED, CaptchaUiStatus.ERROR -> Icons.Default.ErrorOutline
        else -> Icons.Default.Security
    }
    val headerIconTint = when (status) {
        CaptchaUiStatus.VERIFIED -> c.success
        CaptchaUiStatus.EXPIRED -> c.warning
        CaptchaUiStatus.ERROR -> c.danger
        else -> c.primary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceSoft,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = headerIconTint,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = strings.auth.captchaSectionTitle,
                    color = c.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                GuBadge(text = badgeText, variant = badgeVariant)
                IconButton(
                    onClick = {
                        onRefresh()
                        if (config !is ActiveCaptchaConfig.Turnstile) {
                            showActionDialog = true
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.auth.captchaRefresh,
                        tint = c.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            when (config) {
                is ActiveCaptchaConfig.Turnstile -> {
                    var widgetHeightDp by remember { mutableIntStateOf(74) }
                    var webViewRef by remember { mutableStateOf<WebView?>(null) }

                    val bridge = remember {
                        CaptchaJsBridge(
                            mainHandler = Handler(Looper.getMainLooper()),
                            onReadyCb = { onReadyLatest() },
                            onVerifiedCb = { token, randstr -> onVerifiedLatest(token, randstr) },
                            onExpiredCb = { onExpiredLatest() },
                            onErrorCb = { detail -> onErrorLatest(detail) },
                            onCancelledCb = {},
                            onHeightChangedCb = { heightCssPx ->
                                widgetHeightDp = (heightCssPx + 6).coerceIn(72, 260)
                            },
                        )
                    }

                    LaunchedEffect(
                        webViewRef,
                        baseUrl,
                        config.siteKey,
                        theme.isDark,
                        languageTag,
                        resetNonce,
                    ) {
                        val wv = webViewRef ?: return@LaunchedEffect
                        widgetHeightDp = 74
                        onLoadingLatest()
                        val html = InAppCaptchaHtmlBuilder.buildTurnstileHtml(
                            siteKey = config.siteKey,
                            darkTheme = theme.isDark,
                            languageTag = languageTag,
                        )
                        wv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    }

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(widgetHeightDp.dp),
                        factory = { ctx ->
                            createConfiguredCaptchaWebView(
                                context = ctx,
                                bridge = bridge,
                                serverHost = serverHost,
                            ).also { webViewRef = it }
                        },
                        onRelease = { wv ->
                            releaseCaptchaWebView(wv)
                            webViewRef = null
                        },
                    )
                }

                is ActiveCaptchaConfig.Tencent, is ActiveCaptchaConfig.Aliyun -> {
                    GuButton(
                        onClick = { showActionDialog = true },
                        variant = if (status == CaptchaUiStatus.VERIFIED) GuButtonVariant.SECONDARY else GuButtonVariant.HONEY,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (status == CaptchaUiStatus.VERIFIED) Icons.Default.VerifiedUser else Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (status == CaptchaUiStatus.VERIFIED) strings.auth.captchaVerified else strings.auth.captchaClickToVerify,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                is ActiveCaptchaConfig.None -> Unit
            }

            if (status == CaptchaUiStatus.ERROR && !errorDetail.isNullOrBlank()) {
                Text(
                    text = "${strings.auth.captchaError} ($errorDetail)",
                    color = c.danger,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showActionDialog && (config is ActiveCaptchaConfig.Tencent || config is ActiveCaptchaConfig.Aliyun)) {
        ActionCaptchaDialog(
            config = config,
            baseUrl = baseUrl,
            serverHost = serverHost,
            languageTag = languageTag,
            onDismiss = { showActionDialog = false },
            onLoading = onLoading,
            onReady = onReady,
            onVerified = { token, randstr ->
                showActionDialog = false
                onVerified(token, randstr)
            },
            onError = { detail -> onError(detail) },
        )
    }
}

@Composable
private fun ActionCaptchaDialog(
    config: ActiveCaptchaConfig,
    baseUrl: String,
    serverHost: String?,
    languageTag: String,
    onDismiss: () -> Unit,
    onLoading: () -> Unit,
    onReady: () -> Unit,
    onVerified: (token: String, randstr: String) -> Unit,
    onError: (String?) -> Unit,
) {
    val strings = LocalAppStrings.current
    val c = LocalAppTheme.current.colors

    val onLoadingLatest by rememberUpdatedState(onLoading)
    val onReadyLatest by rememberUpdatedState(onReady)
    val onVerifiedLatest by rememberUpdatedState(onVerified)
    val onErrorLatest by rememberUpdatedState(onError)
    val onDismissLatest by rememberUpdatedState(onDismiss)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    val bridge = remember {
        CaptchaJsBridge(
            mainHandler = Handler(Looper.getMainLooper()),
            onReadyCb = { onReadyLatest() },
            onVerifiedCb = { token, randstr -> onVerifiedLatest(token, randstr) },
            onExpiredCb = { onDismissLatest() },
            onErrorCb = { detail -> onErrorLatest(detail) },
            onCancelledCb = { onDismissLatest() },
            onHeightChangedCb = {},
        )
    }

    LaunchedEffect(webViewRef, baseUrl, config, languageTag, reloadNonce) {
        val wv = webViewRef ?: return@LaunchedEffect
        onLoadingLatest()
        val html = when (config) {
            is ActiveCaptchaConfig.Tencent ->
                InAppCaptchaHtmlBuilder.buildTencentHtml(config.appId, config.region, languageTag)
            is ActiveCaptchaConfig.Aliyun ->
                InAppCaptchaHtmlBuilder.buildAliyunHtml(config.sceneId, config.prefix, config.region, languageTag)
            else -> ""
        }
        if (html.isNotEmpty()) {
            wv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.page),
        ) {
            GuAwningHeader(includeStatusBarPadding = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.common.close, tint = c.ink)
                    }
                    Text(
                        text = strings.auth.captchaSectionTitle,
                        color = c.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { reloadNonce++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.common.refresh, tint = c.ink)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createConfiguredCaptchaWebView(
                            context = ctx,
                            bridge = bridge,
                            serverHost = serverHost,
                        ).also { webViewRef = it }
                    },
                    onRelease = { wv ->
                        releaseCaptchaWebView(wv)
                        webViewRef = null
                    },
                )
            }
        }
    }
}
