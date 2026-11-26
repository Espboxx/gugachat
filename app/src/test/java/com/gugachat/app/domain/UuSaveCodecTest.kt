package com.gugachat.app.domain

import com.gugachat.app.domain.chat.ConversationMode
import com.gugachat.app.domain.chat.ExternalImportCodec
import com.gugachat.app.domain.chat.ExternalImportSource
import com.gugachat.app.domain.chat.FileTextExtractor
import com.gugachat.app.domain.chat.UuSaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UuSaveCodecTest {

    private val sampleUuSaveJson = """
        {
          "format": "uu-game-save",
          "version": 1,
          "exportedAt": "2026-11-26T02:03:51.516Z",
          "scriptId": "a3hsewrrdd",
          "round": 2,
          "slotName": null,
          "gameState": {
            "scriptId": "a3hsewrrdd",
            "playthroughId": "pt:test-uuid-001",
            "playMode": "game",
            "playerName": "林逸朗",
            "round": 2,
            "activeSideEventId": "evt-dinner-2",
            "actionsRemainingInPeriod": 19,
            "location": "兰溪谷公馆",
            "timeLabel": "第1年·2月",
            "playerAgeAnchorAge": 18,
            "nextPeriodQuickActionSlots": ["学习/工作", "锻炼/运动", "社交/人脉"],
            "customNextPeriodQuickActions": ["逛二奢市场"],
            "setupFieldAnswers": {
              "uu-setup-gender": "男",
              "sf-ignore": "无",
              "sf-trait": "眼光毒辣、独立果断",
              "sf-tags": ["粤语", "鉴定师"]
            },
            "attributes": {
              "现金": 515651,
              "智力": 100,
              "体质": 59,
              "魅力": 95,
              "新属性_2": 100,
              "attr-4mio1": 10,
              "新属性_21": "175cm",
              "attr-v81d2": "豪门背景"
            },
            "connections": [
              {
                "id": "npc-he",
                "name": "何瑞璋",
                "relationship": "重要伙伴",
                "romanceable": true,
                "affinity": 38,
                "friendliness": 15,
                "description": "身形高大，气场沉冷",
                "background": "港澳商界掌权人",
                "opinion": "这小狐狸眼光很毒。",
                "npcAttributes": { "新属性_14": "男", "新属性_10": 33 },
                "avatarCutoutUrl": "https://cdn.example.com/npc_he.png",
                "companionsPlayer": false
              },
              {
                "id": "npc-zhao",
                "name": "赵敏言",
                "relationship": "商界对手",
                "romanceable": false,
                "affinity": 0,
                "friendliness": -25,
                "description": "精明大气，谈判桌上的强势女性",
                "background": "金融世家继承人",
                "opinion": "下周茶局见分晓。",
                "npcAttributes": { "新属性_14": "女" }
              }
            ],
            "events": [
              {
                "id": "evt-news-1",
                "title": "京都财经新闻",
                "description": "看到一条峰会新闻。",
                "resolved": true,
                "periodKey": "第1年·1月",
                "summary": "一月看清局势后独立创业。"
              },
              {
                "id": "evt-skip-1",
                "title": "独自午餐",
                "description": "略过的支线",
                "resolved": false,
                "abandoned": true,
                "periodKey": "第1年·1月"
              },
              {
                "id": "evt-dinner-2",
                "title": "顶层生日晚餐",
                "description": "二月一日生日晚餐与成果检验。",
                "resolved": false,
                "periodKey": "第1年·2月",
                "relatedConnections": [{ "id": "npc-he", "name": "何瑞璋" }]
              }
            ],
            "assets": [
              {
                "id": "a1",
                "name": "复古羊皮腰包",
                "icon": "👜",
                "quantity": 1,
                "description": "98新停产款，市场估值约22000元。"
              },
              {
                "id": "a2",
                "name": "便携光谱放大镜",
                "icon": "🔍",
                "quantity": 2,
                "description": "二奢鉴定专用工具。",
                "useFeedback": "鉴定准确率显著提升。"
              }
            ],
            "messages": [
              {
                "id": "m1",
                "role": "user",
                "content": "（游戏开始，请生成第一段剧情）",
                "timestamp": "2026-11-25T05:24:32.521Z"
              },
              {
                "id": "m2",
                "role": "narrator",
                "content": "一月的阳光透过落地窗洒进房间。",
                "timestamp": "第1年·1月",
                "timeLabel": "第1年·1月",
                "sideEventId": "evt-news-1",
                "aiEngineLabel": "专家(旧)"
              },
              {
                "id": "m3",
                "role": "user",
                "content": "拿出鉴定笔记准备赴约",
                "timestamp": "2026-11-25T22:49:11.787Z",
                "sideEventId": "evt-dinner-2"
              },
              {
                "id": "m4",
                "role": "narrator",
                "content": "餐厅水晶灯亮起，何瑞璋看着桌上整齐的账单微微挑眉。",
                "timestamp": "2026-11-25T22:52:21.506Z",
                "timeLabel": "第1年·2月",
                "sideEventId": "evt-dinner-2",
                "onStageConnectionIds": ["npc-he"],
                "aiEngineLabel": "专家(旧)",
                "connectionChanges": [
                  {
                    "name": "何瑞璋",
                    "affinityDelta": 5,
                    "npcAttributes": { "羁绊值": 60, "信任值": 48 }
                  }
                ],
                "choices": [
                  { "id": "c1", "text": "主动展示二十万自有本金的账单，谈下长期合作", "style": "bold" },
                  { "id": "c2", "text": "试探他对周五下午茶的态度", "style": "cautious" }
                ]
              }
            ],
            "mainlineSummary": "二月一日生日当天，林逸朗已凭实力在深港二奢圈站稳脚跟。",
            "mainlineSummaryHistory": [
              { "timeLabel": "第1年·1月", "summary": "一月起步积累第一桶金。", "atRound": 1 },
              { "timeLabel": "第1年·2月", "summary": "二月迎来生日关键转折。", "atRound": 2 }
            ],
            "sideEventChronicles": {
              "evt-news-1": {
                "body": "一月从新闻事件出发，林逸朗五天滚出二十万现金。",
                "timeLabel": "第1年·1月",
                "createdAt": "2026-11-25T06:34:53.525Z"
              }
            },
            "sideEventFacts": {
              "evt-dinner-2": [
                {
                  "id": "mf-1",
                  "content": "林逸朗获得朗月国际臻品鉴定有限公司牌照",
                  "tags": ["林逸朗", "朗月国际"],
                  "periodKey": "第1年·2月",
                  "createdAt": "2026-11-25T22:41:26.875Z",
                  "sourceRef": { "fromIndex": 3 }
                }
              ]
            },
            "contactChats": {
              "npc-zhao": [
                { "id": "dm0", "role": "npc", "content": "我通过了你的朋友验证请求，现在我们可以开始聊天了" }
              ],
              "npc-he": [
                { "id": "dm1", "role": "npc", "content": "我通过了你的朋友验证请求，现在我们可以开始聊天了" },
                { "id": "dm2", "role": "npc", "content": "明晚返港。" },
                { "id": "dm3", "role": "user", "content": "好，我等你消息。" }
              ]
            },
            "contactMomentPosts": [
              {
                "id": "mom-1",
                "authorName": "华强北老表",
                "content": "祝朗少十八岁生日快乐！",
                "timeLabel": "第1年·2月"
              }
            ],
            "forumPosts": [
              {
                "id": "fp-1",
                "title": "深港二奢圈热议新锐鉴定师",
                "author": "中环食花生",
                "content": "听说最近有位十八岁的新买手眼光极准。",
                "replies": [
                  { "author": "维港吹风", "content": "何瑞璋都亲自撑场了。" }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parseBytes_uuGameSave_restoresRpConfigMessagesCastChoicesMemoryAndWorldbook() {
        val bundle = ExternalImportCodec.parseBytes(
            sampleUuSaveJson.toByteArray(Charsets.UTF_8),
            fileName = "uu-save-a3hsewrrdd-林逸朗-r2.json",
        )

        assertEquals(ExternalImportSource.UU_GAME, bundle.source)
        assertEquals("uu_game_save_v1", bundle.sourceFormat)
        assertEquals(1, bundle.items.size)

        val item = bundle.items.first()
        assertEquals(ConversationMode.rp, item.conversation.mode)
        assertEquals("林逸朗 · 顶层生日晚餐", item.conversation.title)

        val rp = item.conversation.rp
        assertNotNull(rp)
        rp!!
        assertEquals("林逸朗", rp.playerName)
        assertTrue(rp.playerPersona.contains("眼光毒辣"))
        assertTrue(rp.playerPersona.contains("175cm"))
        assertFalse(rp.playerPersona.contains("\n无\n"))
        assertTrue(rp.worldview.contains("深港二奢圈"))

        // RPG state: gold, named stats only (no placeholder 新属性_* / attr-*), inventory, active quest
        assertEquals(515651, rp.state.gold)
        assertEquals(100, rp.state.stats["智力"])
        assertEquals(59, rp.state.stats["体质"])
        assertFalse(rp.state.stats.containsKey("新属性_2"))
        assertFalse(rp.state.stats.containsKey("attr-4mio1"))
        assertEquals("兰溪谷公馆", rp.state.scene)
        assertEquals("第1年·2月", rp.state.time)
        assertEquals(2, rp.state.actNumber)
        assertEquals(listOf("👜 复古羊皮腰包", "🔍 便携光谱放大镜 ×2"), rp.state.inventory)
        assertEquals(1, rp.state.quests.size)
        assertTrue(rp.state.quests.first().startsWith("★ 顶层生日晚餐"))
        assertEquals(4, rp.quickActions.size)

        // Cast: 2 NPCs, on-stage flag, opinion -> secret, connectionChanges named attrs -> status, remote avatar URL
        assertEquals(2, rp.cast.size)
        val he = rp.cast.first { it.name == "何瑞璋" }
        assertEquals("romance", he.role)
        assertEquals("male", he.gender)
        assertEquals(38, he.affinity)
        assertTrue(he.inScene)
        assertEquals("这小狐狸眼光很毒。", he.secret)
        assertEquals("https://cdn.example.com/npc_he.png", he.avatar)
        assertTrue(he.status.contains("羁绊值 60"))
        assertTrue(he.status.contains("信任值 48"))

        val zhao = rp.cast.first { it.name == "赵敏言" }
        assertEquals("rival", zhao.role)
        assertEquals("female", zhao.gender)
        assertEquals(-25, zhao.affinity)
        assertFalse(zhao.inScene)

        // Messages: 4 turns, narrator mapped to assistant, monotonic timestamps even with "第1年·1月", live pendingChoices
        assertEquals(4, item.messages.size)
        assertEquals("user", item.messages[0].role)
        assertEquals("assistant", item.messages[1].role)
        assertTrue(item.messages[1].createdAt > item.messages[0].createdAt)
        assertEquals("专家(旧)", item.messages[3].model)
        assertEquals(2, rp.pendingChoices.size)
        assertEquals(2, item.messages[3].rpChoices.size)
        assertTrue(rp.pendingChoices[0].actionText.contains("二十万自有本金"))

        // Long-term memory: sideEventFacts -> MemoryLedgerItem, summaryHistory + chronicles -> MemoryEpisode
        assertEquals(1, item.memoryLedger.size)
        assertTrue(item.memoryLedger.first().content.contains("朗月国际"))
        assertTrue(item.memoryLedger.first().keyEntities.contains("林逸朗"))
        assertEquals(3, item.memoryEpisodes.size) // 2 mainline summaries + 1 sideEventChronicle
        assertTrue(item.memoryEpisodes.any { it.level == 3 && it.title.contains("京都财经新闻") })

        // Worldbook: assets (2) + events (3) + non-boilerplate contactChat (1, zhao skipped!) + moments (1) + forum (1) = 8
        assertTrue(item.loreEntries.any { it.category == "item" && it.title.contains("复古羊皮腰包") })
        assertTrue(item.loreEntries.any { it.title == "私聊记录 · 何瑞璋" && it.content.contains("明晚返港") })
        assertFalse(item.loreEntries.any { it.title.contains("赵敏言") && it.title.startsWith("私聊记录") })
        assertTrue(item.loreEntries.any { it.title.startsWith("朋友圈动态") && it.content.contains("华强北老表") })
        assertTrue(item.loreEntries.any { it.title.startsWith("论坛 ·") && it.keywords.contains("何瑞璋") })
    }

    @Test
    fun parseJsonText_unwrappedGameState_isAlsoRecognized() {
        val bareGameState = """
            {
              "playthroughId": "pt:bare-1",
              "playerName": "沈念",
              "round": 1,
              "location": "书房",
              "messages": [
                { "id": "u1", "role": "user", "content": "翻开旧日记" },
                { "id": "n1", "role": "narrator", "content": "泛黄的纸页间滑出一张车票。" }
              ]
            }
        """.trimIndent()
        val bundle = UuSaveCodec.parseJsonText(bareGameState, fileName = "bare.json")
        assertEquals(ExternalImportSource.UU_GAME, bundle.source)
        assertEquals(2, bundle.items.first().messages.size)
        assertEquals("沈念", bundle.items.first().conversation.rp?.playerName)
    }

    @Test
    fun fileTextExtractor_distillsUuGameSaveIntoCompactMarkdownWithoutJsonNoise() {
        val extracted = FileTextExtractor.extractFromBytes(
            bytes = sampleUuSaveJson.toByteArray(Charsets.UTF_8),
            fileName = "uu-save-a3hsewrrdd-林逸朗-r2.json",
            mimeType = "application/json",
        )
        assertTrue(extracted.extractionNote.contains("UU 互动文游存档"))
        assertTrue(extracted.text.contains("# UU 互动文游存档：林逸朗 · 顶层生日晚餐"))
        assertTrue(extracted.text.contains("何瑞璋"))
        assertTrue(extracted.text.contains("朗月国际臻品鉴定有限公司"))
        assertTrue(extracted.text.contains("当前可选分支"))
        assertFalse(extracted.text.contains("attr-4mio1"))
        assertFalse(extracted.text.contains("新属性_2"))
    }

    @Test
    fun parseBytes_removesGenericKeywordsUnlocksEpisodesAndSetsStage0WindowOnHugeSaves() {
        // 1. Small sample save: no generic keywords, L2/L3 unlocked, activeWindowStartSeq remains null
        val smallItem = ExternalImportCodec.parseBytes(
            sampleUuSaveJson.toByteArray(Charsets.UTF_8),
            fileName = "small.json",
        ).items.first()

        val forbiddenGeneric = setOf("消息", "动态", "微信", "朋友圈", "论坛", "热帖", "日记", "林逸朗")
        for (lore in smallItem.loreEntries) {
            assertTrue(
                "Lore '${lore.title}' must not contain ultra-generic keywords: ${lore.keywords}",
                lore.keywords.none { it in forbiddenGeneric },
            )
        }
        assertTrue("Imported L2/L3 episodes must be unlocked for rollback", smallItem.memoryEpisodes.all { !it.locked })
        assertEquals(null, smallItem.conversation.params.activeWindowStartSeq)

        // 2. Huge 30-turn save (~150K CJK chars): sets Stage-0 activeWindowStartSeq so initial waterline stays ~55%
        val bigTurnProse = "深港夜色璀璨，兰溪谷公馆水晶灯下众人举杯畅谈二奢拍卖行情与古董钟表收藏细节。".repeat(120)
        val hugeMsgsJson = (1..30).joinToString(",") { idx ->
            val role = if (idx % 2 == 1) "user" else "narrator"
            """{"id":"hm$idx","role":"$role","content":"第${idx}轮：$bigTurnProse"}"""
        }
        val hugeJson = """
            {
              "format": "uu-game-save",
              "version": 1,
              "gameState": {
                "playthroughId": "pt:huge-stage0",
                "playerName": "林逸朗",
                "round": 15,
                "location": "兰溪谷公馆",
                "timeLabel": "第2年·6月",
                "messages": [$hugeMsgsJson]
              }
            }
        """.trimIndent()

        val hugeItem = UuSaveCodec.parseJsonText(hugeJson, "huge.json").items.first()
        val startSeq = hugeItem.conversation.params.activeWindowStartSeq
        assertNotNull("Huge save must set Stage-0 activeWindowStartSeq", startSeq)
        assertTrue("Stage-0 startSeq ($startSeq) must skip older turns", startSeq!! > 1)
        assertEquals(0, hugeItem.conversation.summarizedUntilSeq)

        val wl = com.gugachat.app.domain.chat.ContextBuilder.computeContextWaterline(
            com.gugachat.app.domain.chat.ContextBuildInput(
                conversation = hugeItem.conversation,
                messages = hugeItem.messages,
                modelContextWindow = 128_000,
            ),
        )
        assertFalse("Huge UU save should open cleanly without red hardOverflow", wl.hardOverflow)
        assertFalse(wl.exceedsModelWindow)
        assertTrue("Initial sent percentage (${wl.sentPercentage}%) should stay around <= 65%", wl.sentPercentage <= 65)
        assertTrue("Archive total (${wl.archiveTotalTokens}) must exceed sent (${wl.sentTokens})", wl.archiveTotalTokens > wl.sentTokens)
        assertTrue("Uncompacted overflow count should reflect archived earlier turns", wl.uncompactedOverflowCount > 0)
    }
}
