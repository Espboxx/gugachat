package com.gugachat.app.ui.auth

import com.gugachat.app.core.util.ServerUrlUtils
import com.gugachat.app.data.api.GugagaHosts
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generates self-contained HTML pages loaded into in-app WebView with the selected GuGuGa host
 * as `baseUrl` so Turnstile / Tencent / Aliyun domain verification matches the site's origin.
 */
object InAppCaptchaHtmlBuilder {
    const val JS_BRIDGE_NAME = "GuCaptchaBridge"

    fun buildBaseUrl(serverUrl: String): String {
        val normalized = ServerUrlUtils.normalizeServerUrl(serverUrl)
            .ifBlank { GugagaHosts.PRESETS.first().url }
            .trimEnd('/')
        return "$normalized/"
    }

    fun buildTurnstileHtml(
        siteKey: String,
        darkTheme: Boolean,
        languageTag: String,
    ): String {
        val jsSiteKey = jsString(siteKey.trim())
        val jsTheme = jsString(if (darkTheme) "dark" else "light")
        val jsLang = jsString(if (languageTag.lowercase().startsWith("zh")) "zh-cn" else "en")
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <style>
                * { box-sizing: border-box; }
                html, body {
                  margin: 0;
                  padding: 0;
                  width: 100%;
                  height: auto;
                  background: transparent;
                  overflow: hidden;
                  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                }
                .wrap {
                  width: 100%;
                  padding: 2px 0;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                #cf-turnstile {
                  width: 100%;
                  min-height: 66px;
                  display: flex;
                  justify-content: center;
                }
                #cf-turnstile iframe {
                  width: 100% !important;
                  max-width: 100%;
                }
              </style>
              <script>
                (function() {
                  var widgetId = null;
                  var rendered = false;
                  var settledTimeout = false;

                  function callBridge(method, args) {
                    try {
                      var b = window.$JS_BRIDGE_NAME;
                      if (b && typeof b[method] === 'function') {
                        b[method].apply(b, args || []);
                      }
                    } catch (_) {}
                  }

                  function reportHeight() {
                    try {
                      var el = document.getElementById('cf-turnstile');
                      var iframe = el ? el.querySelector('iframe') : null;
                      var targetH = Math.max(
                        iframe ? Math.ceil(iframe.getBoundingClientRect().height) : 0,
                        el ? Math.ceil(el.getBoundingClientRect().height) : 0,
                        66
                      );
                      callBridge('onHeightChanged', [targetH]);
                    } catch (_) {}
                  }

                  window.guReportLoadError = function(reason) {
                    if (settledTimeout) return;
                    settledTimeout = true;
                    callBridge('onError', [String(reason || 'script_load_failed')]);
                  };

                  window.guResetCaptcha = function() {
                    try {
                      if (window.turnstile && widgetId !== null) {
                        window.turnstile.reset(widgetId);
                        reportHeight();
                      }
                    } catch (_) {}
                  };

                  var loadTimer = setTimeout(function() {
                    if (!rendered) {
                      window.guReportLoadError('script_load_timeout');
                    }
                  }, 15000);

                  window.onTurnstileLoad = function() {
                    clearTimeout(loadTimer);
                    var el = document.getElementById('cf-turnstile');
                    if (!window.turnstile || !el) {
                      window.guReportLoadError('turnstile_unavailable');
                      return;
                    }
                    try {
                      el.innerHTML = '';
                      widgetId = window.turnstile.render(el, {
                        sitekey: $jsSiteKey,
                        theme: $jsTheme,
                        language: $jsLang,
                        size: 'flexible',
                        callback: function(token) {
                          callBridge('onVerified', [String(token || ''), '']);
                          setTimeout(reportHeight, 60);
                        },
                        'expired-callback': function() {
                          callBridge('onExpired', []);
                          setTimeout(reportHeight, 60);
                        },
                        'error-callback': function(code) {
                          callBridge('onError', [String(code || 'turnstile_error')]);
                          setTimeout(reportHeight, 60);
                        },
                        'timeout-callback': function() {
                          callBridge('onExpired', []);
                        }
                      });
                      rendered = true;
                      callBridge('onReady', []);
                      reportHeight();

                      if (typeof ResizeObserver !== 'undefined') {
                        var ro = new ResizeObserver(function() { reportHeight(); });
                        ro.observe(el);
                        if (document.body) ro.observe(document.body);
                      }
                      var checks = 0;
                      var poll = setInterval(function() {
                        reportHeight();
                        if (++checks >= 16) clearInterval(poll);
                      }, 300);
                    } catch (e) {
                      window.guReportLoadError(e && e.message ? e.message : 'render_failed');
                    }
                  };
                })();
              </script>
              <script
                src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=onTurnstileLoad"
                async
                defer
                onerror="guReportLoadError('script_load_failed')">
              </script>
            </head>
            <body>
              <div class="wrap">
                <div id="cf-turnstile"></div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildTencentHtml(
        appId: String,
        region: String,
        languageTag: String,
    ): String {
        val isIntl = region.trim().equals("intl", ignoreCase = true)
        val scriptUrl = if (isIntl) {
            "https://ca.turing.captcha.qcloud.com/TJNCaptcha-global.js"
        } else {
            "https://turing.captcha.qcloud.com/TJCaptcha.js"
        }
        val jsAppId = jsString(appId.trim())
        val jsLang = jsString(if (languageTag.lowercase().startsWith("zh")) "zh-cn" else "en")
        val jsIsIntl = if (isIntl) "true" else "false"
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <style>
                html, body {
                  margin: 0;
                  padding: 0;
                  width: 100%;
                  height: 100%;
                  background: transparent;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                }
                #tc-container {
                  width: 100%;
                  min-height: 64px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
              </style>
              <script>
                (function() {
                  function callBridge(method, args) {
                    try {
                      var b = window.$JS_BRIDGE_NAME;
                      if (b && typeof b[method] === 'function') {
                        b[method].apply(b, args || []);
                      }
                    } catch (_) {}
                  }
                  window.guTencentError = function(msg) {
                    callBridge('onError', [String(msg || 'tencent_captcha_failed')]);
                  };
                  window.guTencentInit = function() {
                    if (typeof window.TencentCaptcha !== 'function') {
                      window.guTencentError('sdk_unavailable');
                      return;
                    }
                    try {
                      var handleResult = function(res) {
                        if (!res || res.ret === 2) {
                          callBridge('onCancelled', []);
                          return;
                        }
                        var ticket = res.ticket ? String(res.ticket).trim() : '';
                        var randstr = res.randstr ? String(res.randstr).trim() : '';
                        if (!ticket || !randstr || ticket.indexOf('trerror_') === 0 || res.errorCode !== undefined) {
                          window.guTencentError(res.errorMessage || ('error_' + (res.errorCode || 'unknown')));
                          return;
                        }
                        callBridge('onVerified', [ticket, randstr]);
                      };
                      var instance;
                      if ($jsIsIntl) {
                        var box = document.getElementById('tc-container');
                        instance = new window.TencentCaptcha(box, $jsAppId, handleResult, {
                          enableAutoCheck: false,
                          userLanguage: $jsLang,
                          type: 'popup'
                        });
                      } else {
                        instance = new window.TencentCaptcha($jsAppId, handleResult, {
                          userLanguage: $jsLang
                        });
                      }
                      callBridge('onReady', []);
                      instance.show();
                    } catch (e) {
                      window.guTencentError(e && e.message ? e.message : 'init_failed');
                    }
                  };
                })();
              </script>
              <script
                src="$scriptUrl"
                async
                onload="guTencentInit()"
                onerror="guTencentError('script_load_failed')">
              </script>
            </head>
            <body>
              <div id="tc-container"></div>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildAliyunHtml(
        sceneId: String,
        prefix: String,
        region: String,
        languageTag: String,
    ): String {
        val normRegion = if (region.trim().equals("sgp", ignoreCase = true)) "sgp" else "cn"
        val jsSceneId = jsString(sceneId.trim())
        val jsPrefix = jsString(prefix.trim())
        val jsRegion = jsString(normRegion)
        val jsLang = jsString(if (languageTag.lowercase().startsWith("zh")) "cn" else "en")
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <style>
                html, body {
                  margin: 0;
                  padding: 12px;
                  width: 100%;
                  background: transparent;
                  box-sizing: border-box;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                }
                #aliyun-el {
                  width: 100%;
                  display: flex;
                  justify-content: center;
                }
                #aliyun-btn {
                  display: none;
                }
              </style>
              <script>
                window.AliyunCaptchaConfig = { region: $jsRegion, prefix: $jsPrefix };
                (function() {
                  function callBridge(method, args) {
                    try {
                      var b = window.$JS_BRIDGE_NAME;
                      if (b && typeof b[method] === 'function') {
                        b[method].apply(b, args || []);
                      }
                    } catch (_) {}
                  }
                  window.guAliyunError = function(msg) {
                    callBridge('onError', [String(msg || 'aliyun_captcha_failed')]);
                  };
                  window.guAliyunInit = function() {
                    if (typeof window.initAliyunCaptcha !== 'function') {
                      window.guAliyunError('sdk_unavailable');
                      return;
                    }
                    try {
                      window.initAliyunCaptcha({
                        SceneId: $jsSceneId,
                        prefix: $jsPrefix,
                        mode: 'embed',
                        element: '#aliyun-el',
                        button: '#aliyun-btn',
                        captchaVerifyCallback: function(captchaVerifyParam) {
                          callBridge('onVerified', [String(captchaVerifyParam || ''), '']);
                          return { captchaResult: true };
                        },
                        onBizResultCallback: function() {},
                        getInstance: function() {
                          callBridge('onReady', []);
                        },
                        slideStyle: { width: 320, height: 42 },
                        language: $jsLang
                      });
                    } catch (e) {
                      window.guAliyunError(e && e.message ? e.message : 'init_failed');
                    }
                  };
                })();
              </script>
              <script
                src="https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js"
                async
                onload="guAliyunInit()"
                onerror="guAliyunError('script_load_failed')">
              </script>
            </head>
            <body>
              <div id="aliyun-el"></div>
              <button id="aliyun-btn" type="button">Verify</button>
            </body>
            </html>
        """.trimIndent()
    }

    private fun jsString(raw: String): String =
        JsonPrimitive(raw).toString()
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
}
