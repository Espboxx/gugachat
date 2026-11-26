package com.gugachat.app.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gugachat.app.core.i18n.LocalAppStrings
import com.gugachat.app.core.theme.GuAwningHeader
import com.gugachat.app.core.theme.GuButton
import com.gugachat.app.core.theme.GuButtonVariant
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.core.util.ServerUrlUtils
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class WebAuthSnapshot(
    val token: String = "",
    val refresh: String = "",
    val userJson: String = "",
)

private val WEB_SNAPSHOT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginDialog(
    serverUrl: String,
    onDismiss: () -> Unit,
    onTokensCaptured: (accessToken: String, refreshToken: String?, userJson: String?) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val targetUrl = remember(serverUrl) { ServerUrlUtils.buildWebLoginUrl(serverUrl, "/login") }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var capturedHandled by remember { mutableStateOf(false) }

    fun evaluateLocalStorageToken(wv: WebView?) {
        if (wv == null || capturedHandled) return
        val js = """
            (function() {
                try {
                    var t = localStorage.getItem('auth_token') || localStorage.getItem('token') || '';
                    var r = localStorage.getItem('refresh_token') || '';
                    var u = localStorage.getItem('auth_user') || '';
                    return JSON.stringify({token: t, refresh: r, userJson: u});
                } catch (e) {
                    return JSON.stringify({token: '', refresh: '', userJson: ''});
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js) { raw ->
            if (capturedHandled || raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            val unquoted = runCatching {
                WEB_SNAPSHOT_JSON.decodeFromString(String.serializer(), raw)
            }.getOrElse { raw }
            val snap = runCatching {
                WEB_SNAPSHOT_JSON.decodeFromString(WebAuthSnapshot.serializer(), unquoted)
            }.getOrNull() ?: return@evaluateJavascript

            if (snap.token.isNotBlank() && !capturedHandled) {
                capturedHandled = true
                onTokensCaptured(
                    snap.token.trim(),
                    snap.refresh.trim().ifBlank { null },
                    snap.userJson.trim().ifBlank { null },
                )
            }
        }
    }

    LaunchedEffect(webViewRef) {
        while (!capturedHandled) {
            delay(850L)
            evaluateLocalStorageToken(webViewRef)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.page)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            GuAwningHeader(includeStatusBarPadding = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c.surfaceSoft)
                                .border(theme.borderWidth, c.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.common.close,
                                tint = c.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = strings.auth.webLoginTitle,
                            color = c.ink,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = strings.auth.webLoginHint,
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.common.refresh,
                            tint = c.ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    GuButton(
                        onClick = { evaluateLocalStorageToken(webViewRef) },
                        compact = true,
                        variant = GuButtonVariant.HONEY,
                    ) {
                        Text(strings.common.confirm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = c.primary,
                    trackColor = Color.Transparent,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cookies = CookieManager.getInstance()
                            cookies.setAcceptCookie(true)
                            cookies.setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    evaluateLocalStorageToken(view)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = false
                            }
                            loadUrl(targetUrl)
                            webViewRef = this
                        }
                    },
                    onRelease = { wv ->
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.destroy()
                    },
                )
            }
        }
    }
}
