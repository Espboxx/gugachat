package com.gugachat.app.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuLinearProgressBar
import com.gugachat.app.core.theme.LocalAppTheme

@SuppressLint("SetJavaScriptEnabled")
internal fun configureAuthWebViewDefaults(
    webView: WebView,
    wideViewport: Boolean,
    mixedContentCompatibility: Boolean = false,
    allowPopupWindows: Boolean = false,
) {
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = allowPopupWindows
        if (mixedContentCompatibility) {
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        useWideViewPort = wideViewport
        loadWithOverviewMode = wideViewport
    }
}

internal fun destroyAuthWebView(
    webView: WebView,
    jsBridgeName: String? = null,
) {
    if (!jsBridgeName.isNullOrBlank()) {
        webView.removeJavascriptInterface(jsBridgeName)
    }
    webView.stopLoading()
    webView.loadUrl("about:blank")
    webView.destroy()
}

@Composable
internal fun GuFullscreenWebDialogScaffold(
    onDismiss: () -> Unit,
    applyImePadding: Boolean = false,
    loadProgress: Int? = null,
    headerContent: @Composable RowScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val columnModifier = Modifier
            .fillMaxSize()
            .background(c.page)
            .navigationBarsPadding()
            .let { if (applyImePadding) it.imePadding() else it }

        Column(modifier = columnModifier) {
            GuAwningHeader(includeStatusBarPadding = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = headerContent,
                )
            }

            if (loadProgress != null && loadProgress in 1..99) {
                GuLinearProgressBar(
                    progress = loadProgress / 100f,
                    height = 2.5.dp,
                    rounded = false,
                    color = c.primary,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
        }
    }
}
