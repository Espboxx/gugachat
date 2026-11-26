package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpRegexEngineTest {

    @Test
    fun applyScripts_honorsPlacementMarkdownOnlyPromptOnlyFlagsAndMacros() {
        val rp = RpPresets.findById("modern_tavern").config
        val scripts = listOf(
            RpRegexScript(
                id = "s_disp",
                name = "Display only badge",
                enabled = true,
                findRegex = "\\[TAG:([^\\]]+)\\]",
                replaceString = "<b>{{char}}:$1</b>",
                placement = "ai_output",
                markdownOnly = true,
                promptOnly = false,
                flags = "g",
            ),
            RpRegexScript(
                id = "s_prompt",
                name = "Prompt only rewrite",
                enabled = true,
                findRegex = "secret",
                replaceString = "REDACTED",
                placement = "user_input",
                markdownOnly = false,
                promptOnly = true,
                flags = "i", // non-global: replaces only first match
            ),
            RpRegexScript(
                id = "s_both",
                name = "Both placements & modes",
                enabled = true,
                findRegex = "/foo/gi",
                replaceString = "bar",
                placement = "both",
                markdownOnly = false,
                promptOnly = false,
                flags = "",
            ),
            RpRegexScript(
                id = "s_disabled",
                name = "Disabled script",
                enabled = false,
                findRegex = "bar",
                replaceString = "SHOULD_NOT_APPLY",
                placement = "both",
                markdownOnly = false,
                promptOnly = false,
            ),
        )

        val aiInput = "[TAG:A] [TAG:B] Foo FOO"
        val aiDisplay = RpRegexEngine.applyScripts(aiInput, scripts, placement = "ai_output", forDisplay = true, rp = rp)
        assertEquals("<b>江晚柠:A</b> <b>江晚柠:B</b> bar bar", aiDisplay)

        // For non-display context, markdownOnly script MUST NOT run, while markdownOnly=false & promptOnly=false DOES run
        val aiContext = RpRegexEngine.applyScripts(aiInput, scripts, placement = "ai_output", forDisplay = false, rp = rp)
        assertEquals("[TAG:A] [TAG:B] bar bar", aiContext)

        // User input promptOnly script runs only when forDisplay=false, and non-global flag replaces only first match
        val userInput = "Secret one and SECRET two Foo"
        val userPrompt = RpRegexEngine.applyScripts(userInput, scripts, placement = "user_input", forDisplay = false, rp = rp)
        assertEquals("REDACTED one and SECRET two bar", userPrompt)

        val userDisplay = RpRegexEngine.applyScripts(userInput, scripts, placement = "user_input", forDisplay = true, rp = rp)
        assertEquals("Secret one and SECRET two bar", userDisplay)
    }

    @Test
    fun applyScripts_safelySkipsMalformedRegexAndSpecialDollarTemplates() {
        val brokenAndValid = listOf(
            RpRegexScript(
                id = "bad1",
                name = "Unclosed group",
                findRegex = "(unclosed[bracket",
                replaceString = "broken",
                placement = "ai_output",
                markdownOnly = true,
            ),
            RpRegexScript(
                id = "good_dollar",
                name = "Price template with literal dollar and match",
                findRegex = "price=(\\d+)",
                replaceString = "<span>$$$1 / {{match}} / \$foo</span>",
                placement = "ai_output",
                markdownOnly = true,
                flags = "g",
            ),
        )

        val out = RpRegexEngine.applyScripts(
            text = "item price=42 end",
            scripts = brokenAndValid,
            placement = "ai_output",
            forDisplay = true,
        )
        assertEquals("item <span>$42 / price=42 / \$foo</span> end", out)
    }

    @Test
    fun splitMarkdownAndHtml_handlesDisabledFencedHtmlStyleMergeAndNestedDivs() {
        val mixed = """
            她从袖中取出一枚玉牌。

            <style>.box { color: gold; }</style>
            <div class="box">
              <div class="nested"><span>好感 +5</span></div>
            </div>
            <script>console.log("<div>ok</div>");</script>

            接着她轻轻转身。
        """.trimIndent()

        // When disabled, always returns a single non-HTML segment
        val disabled = RpRegexEngine.splitMarkdownAndHtml(mixed, enableHtmlCards = false)
        assertEquals(1, disabled.size)
        assertFalse(disabled[0].isHtml)
        assertEquals(mixed, disabled[0].content)

        // When enabled, merges <style> + nested <div> + <script> into ONE HTML segment
        val enabled = RpRegexEngine.splitMarkdownAndHtml(mixed, enableHtmlCards = true)
        assertEquals(3, enabled.size)
        assertFalse(enabled[0].isHtml)
        assertEquals("她从袖中取出一枚玉牌。", enabled[0].content)
        assertTrue(enabled[1].isHtml)
        assertTrue(enabled[1].content.startsWith("<style>"))
        assertTrue(enabled[1].content.contains("<div class=\"nested\">"))
        assertTrue(enabled[1].content.endsWith("</script>"))
        assertFalse(enabled[2].isHtml)
        assertEquals("接着她轻轻转身。", enabled[2].content)

        // Fenced html code block vs non-html fenced code block
        val fenced = """
            前文说明
            ```html
            <section><h3>状态</h3></section>
            ```
            中间段落
            ```xml
            <div>不应该被当成卡片</div>
            ```
        """.trimIndent()
        val fencedSegs = RpRegexEngine.splitMarkdownAndHtml(fenced, enableHtmlCards = true)
        assertEquals(3, fencedSegs.size)
        assertFalse(fencedSegs[0].isHtml)
        assertTrue(fencedSegs[1].isHtml)
        assertEquals("<section><h3>状态</h3></section>", fencedSegs[1].content)
        assertFalse(fencedSegs[2].isHtml)
        assertTrue(fencedSegs[2].content.contains("```xml"))

        // DOCTYPE html full document
        val fullDoc = "<!DOCTYPE html><html><body><div>Full Card</div></body></html>"
        val docSegs = RpRegexEngine.splitMarkdownAndHtml(fullDoc, enableHtmlCards = true)
        assertEquals(1, docSegs.size)
        assertTrue(docSegs[0].isHtml)
        assertEquals(fullDoc, docSegs[0].content)
    }

    @Test
    fun buildSandboxHtmlDocument_injectsThemeVarsRpStateMacrosBridgeAndStripsExternalScripts() {
        val palace = RpPresets.findById("palace_intrigue").config.copy(
            state = RpPresets.findById("palace_intrigue").config.state.copy(
                flags = mapOf("banquet" to "active"),
            ),
        )
        val fragment = """
            <script src="https://evil.example.com/inject.js"></script>
            <script src="//cdn.example.com/x.js" />
            <div class="card">{{char}} / {{user}} / {{scene}} / HP:{{hp}}/{{maxHp}} / MP:{{mp}}/{{maxMp}} / Gold:{{gold}}</div>
            <script>window._inlineOk = true; var s = {{stats_json}}; var c = {{cast_json}}; var f = {{flags_json}};</script>
        """.trimIndent()

        val doc = RpRegexEngine.buildSandboxHtmlDocument(
            fragmentHtml = fragment,
            rp = palace,
            isDark = true,
            customCss = ".card { border-radius: 12px; }",
            primaryHex = "#C84B5B",
            accentHex = "#E5C17B",
            surfaceHex = "#1A1215",
            inkHex = "#F9ECD2",
            mutedHex = "#BCA88E",
            borderHex = "#5A3B42",
        )

        assertTrue(doc.startsWith("<!DOCTYPE html>"))
        assertTrue(doc.contains("--gu-primary:#C84B5B;"))
        assertTrue(doc.contains("--gu-accent:#E5C17B;"))
        assertTrue(doc.contains("--bg:#1A1215;"))
        assertTrue(doc.contains("--fg:#F9ECD2;"))
        assertTrue(doc.contains(".card { border-radius: 12px; }"))
        // Macros expanded
        assertTrue(doc.contains("萧景珩 / 云疏月 / 太液池畔 · 澄心亭秋宴 / HP:100/100 / MP:80/80 / Gold:120"))
        assertFalse(doc.contains("{{char}}"))
        assertFalse(doc.contains("{{stats_json}}"))
        // RP_STATE + GuGuRp + ResizeObserver height bridge present
        assertTrue(doc.contains("window.RP_STATE="))
        assertTrue(doc.contains("\"banquet\":\"active\""))
        assertTrue(doc.contains("window.GuGuRp="))
        assertTrue(doc.contains("GuGuBridge.sendAction"))
        assertTrue(doc.contains("GuGuBridge.setFlag"))
        assertTrue(doc.contains("GuGuBridge.adjustAffinity"))
        assertTrue(doc.contains("GuGuBridge.onHeight"))
        assertTrue(doc.contains("ResizeObserver"))
        // External scripts stripped, inline script preserved
        assertFalse(doc.contains("evil.example.com"))
        assertFalse(doc.contains("cdn.example.com"))
        assertTrue(doc.contains("window._inlineOk = true"))
    }

    @Test
    fun builtInPresetsAndStatusWidgetTemplates_areCompleteAndFunctional() {
        assertEquals(4, RpRegexEngine.REGEX_PRESETS.size)
        assertEquals(4, RpRegexEngine.STATUS_WIDGET_TEMPLATES.size)

        val statusPreset = RpRegexEngine.findRegexPreset("status_card")
        assertNotNull(statusPreset)
        val renderedStatus = RpRegexEngine.applyScripts(
            text = "<status>\n位置: 澄心亭\n好感: 22\n</status>",
            scripts = listOf(statusPreset!!.script),
            placement = "ai_output",
            forDisplay = true,
        )
        assertTrue(renderedStatus.contains("gu-st-card"))
        val splitStatus = RpRegexEngine.splitMarkdownAndHtml(renderedStatus, enableHtmlCards = true)
        assertEquals(1, splitStatus.size)
        assertTrue(splitStatus[0].isHtml)

        val phonePreset = RpRegexEngine.findRegexPreset("phone_chat")!!
        val renderedPhone = RpRegexEngine.applyScripts(
            text = "<phone>\n我: 今晚下雨了\n江晚柠: 来喝杯失眠蓝吧\n</phone>",
            scripts = listOf(phonePreset.script),
            placement = "ai_output",
            forDisplay = true,
        )
        assertTrue(renderedPhone.contains("gu-phone"))
        assertEquals(1, RpRegexEngine.splitMarkdownAndHtml(renderedPhone, true).size)

        val quotePreset = RpRegexEngine.findRegexPreset("quote_highlight")!!
        val renderedQuote = RpRegexEngine.applyScripts(
            text = "他微微蹙眉。【内心：她究竟知道多少？】<thought>先试探一下。</thought>",
            scripts = listOf(quotePreset.script),
            placement = "ai_output",
            forDisplay = true,
        )
        assertTrue(renderedQuote.contains("她究竟知道多少？"))
        assertTrue(renderedQuote.contains("先试探一下。"))
        assertFalse(renderedQuote.contains("<thought>"))

        val cleanupPreset = RpRegexEngine.findRegexPreset("cleanup_ooc")!!
        val cleaned = RpRegexEngine.applyScripts(
            text = "月色渐沉。(OOC: 已自动扣除体力) [系统提示: 进入夜晚]",
            scripts = listOf(cleanupPreset.script),
            placement = "ai_output",
            forDisplay = true,
        ).trim()
        assertEquals("月色渐沉。", cleaned)

        // All 4 status widget templates render cleanly inside sandbox document
        val rp = RpPresets.findById("palace_intrigue").config
        for (tpl in RpRegexEngine.STATUS_WIDGET_TEMPLATES) {
            assertTrue(tpl.zhName.isNotBlank())
            assertTrue(tpl.enName.isNotBlank())
            assertTrue(tpl.html.contains("RP_STATE"))
            assertTrue(tpl.html.contains("GuGuRp.sendAction"))
            val doc = RpRegexEngine.buildSandboxHtmlDocument(tpl.html, rp = rp, isDark = false)
            assertTrue(doc.contains("<!DOCTYPE html>"))
        }

        // Verify {{char}} macros inside quests/inventory expand cleanly in RP_STATE
        val tavernRp = RpPresets.findById("modern_tavern").config
        val cyberTpl = RpRegexEngine.findStatusWidgetTemplate("cyber_hud")
        val tavernDoc = RpRegexEngine.buildSandboxHtmlDocument(cyberTpl.html, rp = tavernRp, isDark = true)
        assertTrue(tavernDoc.contains("听懂江晚柠藏在特调"))
        assertFalse(tavernDoc.contains("听懂{{char}}"))
    }
}
