package com.gugachat.app.ui.auth

import com.gugachat.app.data.api.ActiveCaptchaConfig
import com.gugachat.app.data.api.CaptchaProof
import com.gugachat.app.data.api.GugagaHosts
import com.gugachat.app.data.api.PublicSettingsDto
import com.gugachat.app.data.api.resolveActiveCaptcha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppCaptchaTest {
    @Test
    fun resolveActiveCaptcha_selectsProviderAndValidatesRequiredKeys() {
        assertEquals(
            ActiveCaptchaConfig.None,
            PublicSettingsDto().resolveActiveCaptcha(),
        )

        // Missing site key yields None even when flag is true
        assertEquals(
            ActiveCaptchaConfig.None,
            PublicSettingsDto(turnstile_enabled = true, turnstile_site_key = "   ").resolveActiveCaptcha(),
        )

        // Turnstile wins when configured and enabled
        assertEquals(
            ActiveCaptchaConfig.Turnstile(siteKey = "0x4AAAAAAFm2MCrnWLTlaRjZ"),
            PublicSettingsDto(
                turnstile_enabled = true,
                turnstile_site_key = " 0x4AAAAAAFm2MCrnWLTlaRjZ ",
                tencent_captcha_enabled = true,
                tencent_captcha_app_id = "1900001",
            ).resolveActiveCaptcha(),
        )

        // Tencent cn & intl regions
        assertEquals(
            ActiveCaptchaConfig.Tencent(appId = "1900001", region = "cn"),
            PublicSettingsDto(
                tencent_captcha_enabled = true,
                tencent_captcha_app_id = "1900001",
                tencent_captcha_region = "other",
            ).resolveActiveCaptcha(),
        )
        assertEquals(
            ActiveCaptchaConfig.Tencent(appId = "1900002", region = "intl"),
            PublicSettingsDto(
                tencent_captcha_enabled = true,
                tencent_captcha_app_id = "1900002",
                tencent_captcha_region = "INTL",
            ).resolveActiveCaptcha(),
        )

        // Aliyun requires both sceneId & prefix
        assertEquals(
            ActiveCaptchaConfig.None,
            PublicSettingsDto(
                aliyun_captcha_enabled = true,
                aliyun_captcha_scene_id = "scene-1",
                aliyun_captcha_prefix = "",
            ).resolveActiveCaptcha(),
        )
        assertEquals(
            ActiveCaptchaConfig.Aliyun(sceneId = "scene-1", prefix = "prefix-1", region = "sgp"),
            PublicSettingsDto(
                aliyun_captcha_enabled = true,
                aliyun_captcha_scene_id = " scene-1 ",
                aliyun_captcha_prefix = " prefix-1 ",
                aliyun_captcha_region = "SGP",
            ).resolveActiveCaptcha(),
        )
    }

    @Test
    fun captchaProof_isReadyChecksEdgeCases() {
        assertFalse(CaptchaProof().isReady)
        assertFalse(CaptchaProof(turnstileToken = "   ").isReady)
        assertTrue(CaptchaProof(turnstileToken = "cf-token-abc").isReady)
        assertFalse(CaptchaProof(tencentTicket = "t1", tencentRandstr = "").isReady)
        assertFalse(CaptchaProof(tencentTicket = "", tencentRandstr = "@abc").isReady)
        assertTrue(CaptchaProof(tencentTicket = "t1", tencentRandstr = "@abc").isReady)
    }

    @Test
    fun buildBaseUrl_normalizesAndFallsBackToPrimaryGugagaHost() {
        assertEquals(
            "${GugagaHosts.PRESETS.first().url}/",
            InAppCaptchaHtmlBuilder.buildBaseUrl(""),
        )
        assertEquals(
            "https://www.aicode2api.cc/",
            InAppCaptchaHtmlBuilder.buildBaseUrl("https://www.aicode2api.cc/api/v1/"),
        )
        assertEquals(
            "https://api.aicode2api.cc/",
            InAppCaptchaHtmlBuilder.buildBaseUrl("api.aicode2api.cc"),
        )
    }

    @Test
    fun buildTurnstileHtml_escapesInputsAndEmbedsScriptAndBridge() {
        val maliciousKey = "0x4\"</script><img src=x>"
        val html = InAppCaptchaHtmlBuilder.buildTurnstileHtml(
            siteKey = maliciousKey,
            darkTheme = true,
            languageTag = "zh-CN",
        )
        assertTrue(html.contains("https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=onTurnstileLoad"))
        assertTrue(html.contains("window.${InAppCaptchaHtmlBuilder.JS_BRIDGE_NAME}"))
        assertTrue(html.contains("theme: \"dark\""))
        assertTrue(html.contains("language: \"zh-cn\""))
        assertFalse(html.contains("\"</script>"))
        assertTrue(html.contains("\\u003c/script\\u003e"))

        val lightEnHtml = InAppCaptchaHtmlBuilder.buildTurnstileHtml(
            siteKey = "0x4AAA",
            darkTheme = false,
            languageTag = "en-US",
        )
        assertTrue(lightEnHtml.contains("theme: \"light\""))
        assertTrue(lightEnHtml.contains("language: \"en\""))
    }

    @Test
    fun buildTencentAndAliyunHtml_selectExpectedSdksAndRegions() {
        val tencentCn = InAppCaptchaHtmlBuilder.buildTencentHtml("19001", "cn", "zh")
        assertTrue(tencentCn.contains("https://turing.captcha.qcloud.com/TJCaptcha.js"))
        assertTrue(tencentCn.contains("false"))

        val tencentIntl = InAppCaptchaHtmlBuilder.buildTencentHtml("19002", "intl", "en")
        assertTrue(tencentIntl.contains("https://ca.turing.captcha.qcloud.com/TJNCaptcha-global.js"))
        assertTrue(tencentIntl.contains("userLanguage: \"en\""))

        val aliyunSgp = InAppCaptchaHtmlBuilder.buildAliyunHtml("sceneA", "prefB", "sgp", "zh-CN")
        assertTrue(aliyunSgp.contains("https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js"))
        assertTrue(aliyunSgp.contains("region: \"sgp\""))
        assertTrue(aliyunSgp.contains("prefix: \"prefB\""))
        assertTrue(aliyunSgp.contains("SceneId: \"sceneA\""))
        assertTrue(aliyunSgp.contains("language: \"cn\""))
    }
}
