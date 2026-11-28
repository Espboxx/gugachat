package com.gugachat.app.domain

import com.gugachat.app.data.api.AppJson
import com.gugachat.app.domain.chat.ParsedTavernAsset
import com.gugachat.app.domain.chat.TavernAssetCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernAssetCodecTest {

    @Test
    fun parseRikkaExportEnvelope_lorebookAndModeInjection() {
        val rikkaLore = """
            {
              "version": 1,
              "type": "lorebook",
              "data": {
                "name": "星港档案",
                "description": "近地轨道港口设定",
                "entries": [
                  {
                    "name": "曲率航道",
                    "keywords": ["曲率", "航道"],
                    "secondaryKeys": ["跃迁"],
                    "content": "航道仅在潮汐窗口开放。",
                    "constantActive": true,
                    "enabled": true,
                    "priority": 88
                  },
                  {
                    "comment": "黑市暗号",
                    "keywords": ["黑市"],
                    "content": "三短一长敲击金属门。",
                    "enabled": false
                  }
                ]
              }
            }
        """.trimIndent()

        val parsedLore = TavernAssetCodec.parseJsonText(rikkaLore, conversationId = "c1")
        assertTrue(parsedLore is ParsedTavernAsset.Lorebook)
        val loreAsset = (parsedLore as ParsedTavernAsset.Lorebook).asset
        assertEquals("rikkahub_lorebook", loreAsset.sourceFormat)
        assertEquals("星港档案", loreAsset.name)
        assertEquals(2, loreAsset.entries.size)
        assertTrue(loreAsset.entries[0].alwaysActive)
        assertEquals(listOf("跃迁"), loreAsset.entries[0].aliases)
        assertEquals(88, loreAsset.entries[0].priority)
        assertFalse(loreAsset.entries[1].enabled)

        val rikkaMode = """
            {
              "version": 1,
              "type": "mode_injection",
              "data": {
                "name": "沉浸叙事模式",
                "content": "保持第二人称细腻描写，避免跳脱视角。"
              }
            }
        """.trimIndent()
        val parsedMode = TavernAssetCodec.parseJsonText(rikkaMode, conversationId = "c1")
        assertTrue(parsedMode is ParsedTavernAsset.Preset)
        val modeAsset = (parsedMode as ParsedTavernAsset.Preset).asset
        assertEquals("rikkahub_mode_injection", modeAsset.sourceFormat)
        assertEquals("沉浸叙事模式", modeAsset.name)
        assertTrue(modeAsset.systemRules.contains("第二人称"))
    }

    @Test
    fun parseTavernHelperWorldbookArray_extractsStrategyKeysAndConstantFlag() {
        val thWorldbook = """
            [
              {
                "name": "银月塔",
                "enabled": true,
                "strategy": {
                  "type": "constant",
                  "keys": ["银月塔", "高塔"],
                  "keys_secondary": { "keys": ["钟楼"] }
                },
                "position": { "order": 120 },
                "content": "位于王城中心的秘银观测塔。"
              },
              {
                "name": "禁林",
                "enabled": true,
                "strategy": {
                  "type": "selective",
                  "keys": ["禁林"]
                },
                "content": "迷雾笼罩的古老森林。"
              }
            ]
        """.trimIndent()

        val parsed = TavernAssetCodec.parseJsonText(thWorldbook, conversationId = "c2", fileName = "silver_world.json")
        assertTrue(parsed is ParsedTavernAsset.Lorebook)
        val lore = (parsed as ParsedTavernAsset.Lorebook).asset
        assertEquals("sillytavern_worldbook_v2", lore.sourceFormat)
        assertEquals(2, lore.entries.size)
        val tower = lore.entries.first { it.title == "银月塔" }
        assertTrue(tower.alwaysActive)
        assertEquals(120, tower.priority)
        assertTrue(tower.keywords.contains("银月塔"))
        assertTrue(tower.aliases.contains("钟楼"))
    }

    @Test
    fun parseOrderedChatPreset_prefersChar100001PreservesDisabledAndExtractsEmbeddedRegex() {
        val presetJson = """
            {
              "temperature": 0.85,
              "top_p": 0.92,
              "openai_max_tokens": 2048,
              "prompts": [
                { "identifier": "main", "name": "Main Prompt", "role": "system", "content": "你是专业叙事导演。", "enabled": true },
                { "identifier": "jailbreak", "name": "Post-History", "role": "system", "content": "始终紧扣角色性格。", "enabled": true },
                { "identifier": "style_opt", "name": "古风润色", "role": "system", "content": "酌情加入古典意象。", "enabled": true },
                { "identifier": "extra_note", "name": "额外补充", "role": "system", "content": "不在 prompt_order 里的条目也要保留。", "enabled": true },
                { "identifier": "chatHistory", "name": "Chat History", "marker": true }
              ],
              "prompt_order": [
                {
                  "character_id": 100000,
                  "order": [
                    { "identifier": "main", "enabled": false }
                  ]
                },
                {
                  "character_id": 100001,
                  "order": [
                    { "identifier": "main", "enabled": true },
                    { "identifier": "style_opt", "enabled": false },
                    { "identifier": "chatHistory", "enabled": true },
                    { "identifier": "jailbreak", "enabled": true }
                  ]
                }
              ],
              "extensions": {
                "regex_scripts": [
                  {
                    "id": "p-r1",
                    "scriptName": "Strip OOC",
                    "findRegex": "/\\(OOC:[\\s\\S]*?\\)/gi",
                    "replaceString": "",
                    "placement": [2],
                    "disabled": false
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = TavernAssetCodec.parseJsonText(presetJson, conversationId = "c3", fileName = "DirectorPreset.json")
        assertTrue(parsed is ParsedTavernAsset.Preset)
        val preset = (parsed as ParsedTavernAsset.Preset).asset
        assertEquals("sillytavern_preset_ordered", preset.sourceFormat)
        assertEquals("DirectorPreset", preset.name)
        assertEquals(0.85, preset.temperature!!, 0.001)
        assertEquals(0.92, preset.topP!!, 0.001)
        assertEquals(2048, preset.maxTokens)
        assertTrue(preset.systemRules.contains("专业叙事导演"))
        assertTrue(preset.postHistoryInstructions.contains("紧扣角色性格"))

        // Disabled entry (`style_opt`) and unordered entry (`extra_note`) are both preserved as lore entries
        val disabledEntry = preset.loreEntries.firstOrNull { it.title == "古风润色" }
        assertNotNull(disabledEntry)
        assertFalse(disabledEntry!!.enabled)
        val unorderedEntry = preset.loreEntries.firstOrNull { it.title == "额外补充" }
        assertNotNull(unorderedEntry)
        assertTrue(unorderedEntry!!.enabled)

        // Embedded regex extracted
        assertEquals(1, preset.regexScripts.size)
        assertEquals("Strip OOC", preset.regexScripts.first().name)
    }

    @Test
    fun parseFlatLegacyPreset_extractsMainNsfwAndJailbreak() {
        val flatPreset = """
            {
              "main_prompt": "Write vivid interactive fiction.",
              "nsfw_prompt": "Allow mature dramatic themes.",
              "nsfw_toggle": false,
              "jailbreak_prompt": "Stay strictly in character.",
              "temp": 0.9,
              "top_p": 0.95
            }
        """.trimIndent()

        val parsed = TavernAssetCodec.parseJsonText(flatPreset, conversationId = "c4", fileName = "legacy_oai.json")
        assertTrue(parsed is ParsedTavernAsset.Preset)
        val preset = (parsed as ParsedTavernAsset.Preset).asset
        assertEquals("sillytavern_preset_flat", preset.sourceFormat)
        assertEquals("Write vivid interactive fiction.", preset.systemRules)
        assertEquals("Stay strictly in character.", preset.postHistoryInstructions)
        assertEquals(1, preset.loreEntries.size)
        assertFalse(preset.loreEntries.first().enabled)
    }

    @Test
    fun parseStandaloneRegex_supportsSingleArraySnakeCaseAndSettingsJson() {
        val singleSnake = """
            {
              "id": "th-1",
              "script_name": "Format Status",
              "find_regex": "/<stat>(.*?)<\\/stat>/gs",
              "replace_string": "<b>$1</b>",
              "enabled": true,
              "source": { "ai_output": true, "user_input": false },
              "destination": { "display": true, "prompt": false }
            }
        """.trimIndent()

        val singleParsed = TavernAssetCodec.parseJsonText(singleSnake, conversationId = "c5")
        assertTrue(singleParsed is ParsedTavernAsset.RegexScripts)
        val s1 = (singleParsed as ParsedTavernAsset.RegexScripts).scripts.single()
        assertEquals("Format Status", s1.name)
        assertEquals("<stat>(.*?)</stat>", s1.findRegex)
        assertEquals("gs", s1.flags)
        assertEquals("ai_output", s1.placement)
        assertTrue(s1.enabled)
        assertTrue(s1.markdownOnly)
        assertFalse(s1.promptOnly)

        val settingsJson = """
            {
              "power_user": { "theme": "dark" },
              "extension_settings": {
                "note": { "default": "始终以第三人称全知视角叙述。" },
                "regex": [
                  {
                    "id": "g-1",
                    "scriptName": "Trim Think",
                    "findRegex": "/<think>[\\s\\S]*?<\\/think>/g",
                    "replaceString": "",
                    "placement": [2],
                    "disabled": false
                  }
                ]
              }
            }
        """.trimIndent()

        val parsedSettings = TavernAssetCodec.parseJsonText(settingsJson, conversationId = "c5")
        assertTrue(parsedSettings is ParsedTavernAsset.Settings)
        val st = (parsedSettings as ParsedTavernAsset.Settings).asset
        assertEquals(1, st.regexScripts.size)
        assertEquals("Trim Think", st.regexScripts.first().name)
        assertTrue(st.authorNote.contains("第三人称"))

        // Also test direct parseRegexScriptsJson helper against settings.json
        val directScripts = TavernAssetCodec.parseRegexScriptsJson(AppJson.parseToJsonElement(settingsJson))
        assertEquals(1, directScripts.size)
    }

    @Test
    fun parseQuickReplies_supportsV1AndV2() {
        val qrJson = """
            {
              "version": 2,
              "name": "战斗快捷指令",
              "qrList": [
                { "label": "观察", "message": "环顾四周寻找掩体" },
                { "label": "拔剑", "message": "抽出长剑进入戒备姿态" }
              ]
            }
        """.trimIndent()
        val parsed = TavernAssetCodec.parseJsonText(qrJson, conversationId = "c6")
        assertTrue(parsed is ParsedTavernAsset.QuickReplies)
        val qr = (parsed as ParsedTavernAsset.QuickReplies).asset
        assertEquals("战斗快捷指令", qr.name)
        assertEquals(listOf("环顾四周寻找掩体", "抽出长剑进入戒备姿态"), qr.actions)
    }

    @Test
    fun parseUuWorldbookScriptJson_extractsAllEntriesTitlesAndConstantFlags() {
        val uuWorldbookJson = """
            {
              "worldInfoEntries": [
                {
                  "id": "wi-eea4e456-72ec",
                  "content": "{\"_meta\":{\"generator\":\"测试\",\"count\":1}}",
                  "title": "攻性癖",
                  "keys": ["攻性癖"],
                  "constant": true
                },
                {
                  "id": "wi-7abd7106-d940",
                  "content": "常驻条目但未填写触发词，需要保留独立标题。",
                  "title": "独立标题A",
                  "keys": [],
                  "constant": true
                },
                {
                  "id": "wi-849f04b9-c829",
                  "content": "标题与触发关键词不一致的词条。",
                  "title": "受性癖·分段2",
                  "keys": ["受性癖"],
                  "constant": false,
                  "disabled": true
                }
              ]
            }
        """.trimIndent()

        val root = AppJson.parseToJsonElement(uuWorldbookJson)
        assertTrue(TavernAssetCodec.isLorebookJson(root))

        val parsed = TavernAssetCodec.parseJsonText(
            uuWorldbookJson,
            conversationId = "c7",
            fileName = "uu-worldbook-script (11).json",
        )
        assertTrue(parsed is ParsedTavernAsset.Lorebook)
        val lore = (parsed as ParsedTavernAsset.Lorebook).asset
        assertEquals("uu_worldbook", lore.sourceFormat)
        assertEquals("uu-worldbook-script (11)", lore.name)
        assertEquals(3, lore.entries.size)
        assertEquals("攻性癖", lore.entries[0].title)
        assertTrue(lore.entries[0].alwaysActive)
        assertTrue(lore.entries[0].enabled)
        assertEquals("独立标题A", lore.entries[1].title)
        assertEquals(listOf("独立标题A"), lore.entries[1].keywords)
        assertTrue(lore.entries[1].alwaysActive)
        assertEquals("受性癖·分段2", lore.entries[2].title)
        assertEquals(listOf("受性癖"), lore.entries[2].keywords)
        assertFalse(lore.entries[2].alwaysActive)
        assertFalse(lore.entries[2].enabled)

        val v4 = TavernAssetCodec.parseWorldbookV4(
            rawJson = uuWorldbookJson,
            worldbookId = "wb-uu-1",
            fileName = "uu-worldbook-script (11).json",
        )
        assertEquals("uu_worldbook", v4.worldbook.sourceFormat)
        assertEquals("uu-worldbook-script (11)", v4.worldbook.name)
        assertEquals(3, v4.entries.size)
        assertEquals(listOf(0, 1, 2), v4.entries.map { it.uid })
        assertTrue(v4.worldbook.contentFingerprint.isNotBlank())
        assertTrue(v4.entries[0].constant)
        assertFalse(v4.entries[2].enabled)
    }
}
