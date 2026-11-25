package com.gugachat.app.ui.chat

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.chat.RpConfig
import com.gugachat.app.domain.chat.RpRegexEngine
import java.util.Locale

private val HEX_COLOR_RE = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")

internal fun parseOptionalHexColor(raw: String?): Color? {
    val clean = raw?.trim().orEmpty()
    if (clean.isEmpty()) return null
    val normalized = if (clean.startsWith("#")) clean else "#$clean"
    if (!HEX_COLOR_RE.matches(normalized)) return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

internal fun Color.toHexRgb(): String =
    String.format(Locale.US, "#%06X", this.toArgb() and 0xFFFFFF)

private class WebViewSandboxHolder(
    var webView: WebView? = null,
    var lastDocHtml: String? = null,
    var lastHeightDp: Int = -1,
)

private class GuGuSandboxBridge(
    private val mainHandler: Handler,
    private val onHeightState: State<(Int) -> Unit>,
    private val onActionState: State<(String) -> Unit>,
    private val onSetFlagState: State<(String, String) -> Unit>,
    private val onAdjustAffinityState: State<(String, Int) -> Unit>,
) {
    @JavascriptInterface
    fun onHeight(px: Int) {
        if (px <= 0) return
        mainHandler.post { onHeightState.value.invoke(px) }
    }

    @JavascriptInterface
    fun sendAction(text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return
        mainHandler.post { onActionState.value.invoke(clean) }
    }

    @JavascriptInterface
    fun setFlag(key: String?, value: String?) {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) return
        val v = value.orEmpty()
        mainHandler.post { onSetFlagState.value.invoke(k, v) }
    }

    @JavascriptInterface
    fun adjustAffinity(npcName: String?, delta: Int) {
        val name = npcName?.trim().orEmpty()
        if (name.isEmpty() || delta == 0) return
        mainHandler.post { onAdjustAffinityState.value.invoke(name, delta) }
    }
}

@Composable
fun RpHtmlCardSandbox(
    htmlFragment: String,
    rp: RpConfig?,
    customCss: String = rp?.visualTheme?.customCss.orEmpty(),
    minHeightDp: Int = 44,
    maxHeightDp: Int = 520,
    onFrontendAction: (String) -> Unit = {},
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    RpHtmlCardSandboxInternal(
        htmlFragment = htmlFragment,
        rp = rp,
        customCss = customCss,
        minHeightDp = minHeightDp,
        maxHeightDp = maxHeightDp,
        forceDark = null,
        onFrontendAction = onFrontendAction,
        onFrontendSetFlag = onFrontendSetFlag,
        onFrontendAdjustAffinity = onFrontendAdjustAffinity,
        modifier = modifier,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun RpHtmlCardSandboxInternal(
    htmlFragment: String,
    rp: RpConfig?,
    customCss: String = rp?.visualTheme?.customCss.orEmpty(),
    minHeightDp: Int = 44,
    maxHeightDp: Int = 520,
    forceDark: Boolean? = null,
    onFrontendAction: (String) -> Unit = {},
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (htmlFragment.isBlank()) return

    val theme = LocalAppTheme.current
    val c = theme.colors
    val effectiveIsDark = forceDark ?: theme.isDark
    val customAccent = remember(rp?.visualTheme?.accentHex) {
        parseOptionalHexColor(rp?.visualTheme?.accentHex)
    }

    val primaryHex = remember(c.primary) { c.primary.toHexRgb() }
    val accentHex = remember(customAccent, c.accent) { (customAccent ?: c.accent).toHexRgb() }
    val surfaceHex = remember(effectiveIsDark, theme.isDark, c.surface) {
        when {
            effectiveIsDark && !theme.isDark -> "#141A29"
            !effectiveIsDark && theme.isDark -> "#F6EFE0"
            else -> c.surface.toHexRgb()
        }
    }
    val inkHex = remember(effectiveIsDark, theme.isDark, c.ink) {
        when {
            effectiveIsDark && !theme.isDark -> "#F3F6FC"
            !effectiveIsDark && theme.isDark -> "#2C2117"
            else -> c.ink.toHexRgb()
        }
    }
    val mutedHex = remember(effectiveIsDark, theme.isDark, c.muted) {
        when {
            effectiveIsDark && !theme.isDark -> "#B3BED4"
            !effectiveIsDark && theme.isDark -> "#6B5A47"
            else -> c.muted.toHexRgb()
        }
    }
    val borderHex = remember(effectiveIsDark, theme.isDark, c.border) {
        when {
            effectiveIsDark && !theme.isDark -> "#33415C"
            !effectiveIsDark && theme.isDark -> "#CBB894"
            else -> c.border.toHexRgb()
        }
    }

    val docHtml = remember(
        htmlFragment,
        rp,
        customCss,
        effectiveIsDark,
        primaryHex,
        accentHex,
        surfaceHex,
        inkHex,
        mutedHex,
        borderHex,
    ) {
        RpRegexEngine.buildSandboxHtmlDocument(
            fragmentHtml = htmlFragment,
            rp = rp,
            isDark = effectiveIsDark,
            customCss = customCss,
            primaryHex = primaryHex,
            accentHex = accentHex,
            surfaceHex = surfaceHex,
            inkHex = inkHex,
            mutedHex = mutedHex,
            borderHex = borderHex,
        )
    }

    val safeMin = minHeightDp.coerceAtLeast(24)
    val safeMax = maxHeightDp.coerceAtLeast(safeMin)
    var measuredHeightDp by remember { mutableIntStateOf(safeMin) }
    val displayHeightDp = measuredHeightDp.coerceIn(safeMin, safeMax)
    val holder = remember { WebViewSandboxHolder() }

    val onHeightState = rememberUpdatedState<(Int) -> Unit> { cssPx ->
        val next = (cssPx + 4).coerceIn(safeMin, safeMax)
        if (next != measuredHeightDp) {
            measuredHeightDp = next
        }
        holder.webView?.let { wv ->
            wv.requestLayout()
            wv.invalidate()
        }
    }
    val onActionState = rememberUpdatedState(onFrontendAction)
    val onSetFlagState = rememberUpdatedState(onFrontendSetFlag)
    val onAdjustAffinityState = rememberUpdatedState(onFrontendAdjustAffinity)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        GuGuSandboxBridge(
            mainHandler = mainHandler,
            onHeightState = onHeightState,
            onActionState = onActionState,
            onSetFlagState = onSetFlagState,
            onAdjustAffinityState = onAdjustAffinityState,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            holder.webView?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.removeJavascriptInterface("GuGuBridge")
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
            holder.webView = null
            holder.lastDocHtml = null
            holder.lastHeightDp = -1
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    blockNetworkLoads = true
                    loadsImagesAutomatically = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = true

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let { wv ->
                            wv.requestLayout()
                            wv.invalidate()
                            wv.postDelayed({
                                if (holder.webView === wv) {
                                    wv.requestLayout()
                                    wv.invalidate()
                                }
                            }, 120L)
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(bridge, "GuGuBridge")
                holder.webView = this
                holder.lastDocHtml = null
                holder.lastHeightDp = displayHeightDp
            }
        },
        update = { webView ->
            if (holder.webView !== webView) {
                holder.webView = webView
                holder.lastDocHtml = null
                holder.lastHeightDp = -1
            }
            if (holder.lastDocHtml != docHtml) {
                holder.lastDocHtml = docHtml
                webView.loadDataWithBaseURL(
                    "https://gugachat.local/",
                    docHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            if (holder.lastHeightDp != displayHeightDp) {
                holder.lastHeightDp = displayHeightDp
                webView.requestLayout()
                webView.invalidate()
                webView.post {
                    if (holder.webView === webView) {
                        webView.requestLayout()
                        webView.invalidate()
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(displayHeightDp.dp),
    )
}

@Composable
fun RpRichMessageContent(
    rawMarkdown: String,
    rp: RpConfig?,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    useSerif: Boolean,
    onCopyToast: (String) -> Unit,
    onFrontendAction: (String) -> Unit = {},
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    RpRichMessageContentThemed(
        rawMarkdown = rawMarkdown,
        rp = rp,
        fontSize = fontSize,
        lineHeight = lineHeight,
        useSerif = useSerif,
        textColor = LocalAppTheme.current.colors.ink,
        palette = null,
        forceDarkSandbox = null,
        onCopyToast = onCopyToast,
        onFrontendAction = onFrontendAction,
        onFrontendSetFlag = onFrontendSetFlag,
        onFrontendAdjustAffinity = onFrontendAdjustAffinity,
        modifier = modifier,
    )
}

@Composable
internal fun RpRichMessageContentThemed(
    rawMarkdown: String,
    rp: RpConfig?,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    useSerif: Boolean,
    textColor: Color,
    palette: MarkdownPalette?,
    forceDarkSandbox: Boolean?,
    onCopyToast: (String) -> Unit,
    onFrontendAction: (String) -> Unit = {},
    onFrontendSetFlag: (String, String) -> Unit = { _, _ -> },
    onFrontendAdjustAffinity: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val transformed = remember(rawMarkdown, rp?.regexScripts, rp?.title, rp?.playerName, rp?.state, rp?.cast) {
        RpRegexEngine.applyScripts(
            text = rawMarkdown,
            scripts = rp?.regexScripts.orEmpty(),
            placement = "ai_output",
            forDisplay = true,
            rp = rp,
        )
    }
    val segments = remember(transformed, rp?.enableHtmlCards) {
        RpRegexEngine.splitMarkdownAndHtml(
            text = transformed,
            enableHtmlCards = rp?.enableHtmlCards ?: true,
        )
    }

    if (segments.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEach { seg ->
            if (seg.isHtml) {
                if (seg.content.isNotBlank()) {
                    RpHtmlCardSandboxInternal(
                        htmlFragment = seg.content,
                        rp = rp,
                        customCss = rp?.visualTheme?.customCss.orEmpty(),
                        forceDark = forceDarkSandbox,
                        onFrontendAction = onFrontendAction,
                        onFrontendSetFlag = onFrontendSetFlag,
                        onFrontendAdjustAffinity = onFrontendAdjustAffinity,
                    )
                }
            } else if (seg.content.isNotBlank()) {
                MarkdownMessageText(
                    markdown = seg.content,
                    textColor = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    useSerif = useSerif,
                    palette = palette,
                    onCopyToast = onCopyToast,
                )
            }
        }
    }
}
