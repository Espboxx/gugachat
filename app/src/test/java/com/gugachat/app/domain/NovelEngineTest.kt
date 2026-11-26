package com.gugachat.app.domain.chat

import com.gugachat.app.core.i18n.EnStrings
import com.gugachat.app.core.i18n.ZhStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelEngineTest {

    @Test
    fun matchNovelLore_supportsConstantDirectAndOneHopRecursion() {
        val lore = listOf(
            NovelLoreEntry(
                id = "const",
                conversationId = "n1",
                category = "rule",
                title = "五境体系",
                content = "炼气至化神",
                alwaysActive = true,
                priority = 90,
                estimatedTokens = 20,
            ),
            NovelLoreEntry(
                id = "hop0",
                conversationId = "n1",
                category = "location",
                title = "青岚剑冢",
                content = "位于宗门后山，深处镇压着太古残碑。",
                keywords = listOf("剑冢"),
                estimatedTokens = 30,
            ),
            NovelLoreEntry(
                id = "hop1",
                conversationId = "n1",
                category = "item",
                title = "太古残碑",
                content = "刻有九道先天灵纹，唯有废脉体质能唤醒。",
                keywords = listOf("残碑"),
                estimatedTokens = 30,
            ),
        )

        val res = NovelEngine.matchNovelLore(
            lore = lore,
            recentMessages = emptyList(),
            draftText = "顾尘夜访青岚剑冢",
            chapterOutline = "",
            loreTokenBudget = 500,
            activeTipSeq = 5,
        )

        assertEquals(listOf("const"), res.constant.map { it.id })
        val trigIds = res.triggered.map { it.id }
        assertTrue(trigIds.contains("hop0"))
        assertTrue(trigIds.contains("hop1"))
        assertEquals("keyword", res.triggeredKinds["hop0"])
        assertEquals("recursion_1hop", res.triggeredKinds["hop1"])
    }

    @Test
    fun matchNovelLore_oneHopIsExactKeyOnlyCappedAt3AndSortedCanonically() {
        val lore = listOf(
            NovelLoreEntry(
                id = "seed",
                conversationId = "n1",
                category = "location",
                title = "青岚剑冢",
                content = "剑冢深处刻着残碑、玄霜剑、星陨石、苍龙印，风声呼啸。",
                keywords = listOf("剑冢"),
                priority = 50,
                createdAt = 10L,
            ),
            NovelLoreEntry(
                id = "hop-a",
                conversationId = "n1",
                category = "item",
                title = "太古残碑",
                content = "第一块碑",
                keywords = listOf("残碑"),
                priority = 90,
                createdAt = 20L,
            ),
            NovelLoreEntry(
                id = "hop-b",
                conversationId = "n1",
                category = "item",
                title = "玄霜剑",
                content = "寒冰名剑",
                keywords = listOf("玄霜剑"),
                priority = 80,
                createdAt = 30L,
            ),
            NovelLoreEntry(
                id = "hop-c",
                conversationId = "n1",
                category = "item",
                title = "星陨石",
                content = "天外奇石",
                keywords = listOf("星陨石"),
                priority = 70,
                createdAt = 40L,
            ),
            NovelLoreEntry(
                id = "hop-d-overflow",
                conversationId = "n1",
                category = "item",
                title = "苍龙印",
                content = "第四条候选，应被 1-hop 上限 3 条截断",
                keywords = listOf("苍龙印"),
                priority = 40,
                createdAt = 50L,
            ),
            NovelLoreEntry(
                id = "fuzzy-only-should-not-hop",
                conversationId = "n1",
                category = "concept",
                title = "风声呼啸深处",
                content = "深处风声呼啸的模糊重叠不应触发 1-hop 级联",
                keywords = listOf("绝对不会匹配的词"),
                priority = 95,
                createdAt = 5L,
            ),
        )

        val res = NovelEngine.matchNovelLore(
            lore = lore,
            recentMessages = emptyList(),
            draftText = "夜探剑冢",
            chapterOutline = "",
            loreTokenBudget = 2000,
            activeTipSeq = 5,
            presentEntities = listOf("不应经由1hop泄漏的角色"),
        )

        val hopIds = res.triggeredKinds.filterValues { it == "recursion_1hop" }.keys
        assertEquals(3, hopIds.size)
        assertTrue(hopIds.containsAll(setOf("hop-a", "hop-b", "hop-c")))
        assertFalse(hopIds.contains("hop-d-overflow"))
        assertFalse(res.triggered.any { it.id == "fuzzy-only-should-not-hop" })

        // Canonical sort: priority DESC -> hop-a (90), hop-b (80), hop-c (70), seed (50)
        assertEquals(listOf("hop-a", "hop-b", "hop-c", "seed"), res.triggered.map { it.id })
    }

    @Test
    fun parseNovelTurnResponse_andExport_stripTagsCleanly() {
        val raw = """
            雨点噼啪打在青石阶上，顾尘指尖轻触碑面。
            <status_hud>{"chapterTitle":"第一章 残碑微光","time":"子夜","location":"青岚剑冢","present":["顾尘"],"mood":"肃杀","conflict":"灵纹暴走","foreshadows":["黑衣人窥伺"]}</status_hud>
            <lore_sync>[{"category":"item","title":"玄铁扫帚","content":"杂役院特制法器","keywords":["扫帚"]}]</lore_sync>
            <chapter_end>{"summary":"顾尘唤醒残碑成功","nextChapterTitle":"第二章 外门风波"}</chapter_end>
            <choices>["按压灵纹","转身隐入剑林"]</choices>
        """.trimIndent()

        val outcome = NovelEngine.parseNovelTurnResponse(raw, "n1", turnSeq = 2)
        assertEquals("雨点噼啪打在青石阶上，顾尘指尖轻触碑面。", outcome.cleanProse)
        assertEquals("青岚剑冢", outcome.statusHud?.location)
        assertEquals(1, outcome.loreUpserts.size)
        assertEquals("玄铁扫帚", outcome.loreUpserts[0].title)
        assertEquals("顾尘唤醒残碑成功", outcome.chapterEndSummary)
        assertEquals("第二章 外门风波", outcome.suggestedNextChapterTitle)
        assertEquals(2, outcome.choices.size)

        val novel = NovelConfig(title = "问剑/测试?", worldview = "五境修仙体系", mainOutline = "外门崛起")
        val ch1 = NovelChapter(id = "ch1", conversationId = "n1", order = 1, title = "第一章 残碑微光", startSeq = 1)
        val msgs = listOf(ChatMessage(id = "m2", conversationId = "n1", role = "assistant", content = raw, createdAt = 1L, seq = 2))
        val md = NovelExport.formatAsMarkdown(
            novel = novel,
            chapters = listOf(ch1),
            messages = msgs,
            lore = outcome.loreUpserts,
            includeLoreAppendix = true,
        )
        assertTrue(md.contains("# 问剑/测试?"))
        assertTrue(md.contains("雨点噼啪打在青石阶上"))
        assertTrue(md.contains("## 附录：大纲与世界设定集"))
        assertTrue(md.contains("玄铁扫帚"))
        assertFalse(md.contains("<status_hud>"))
        assertEquals("问剑_测试_.md", NovelExport.sanitizeFileName(novel.title, "md"))
    }

    @Test
    fun stripInternalTags_hidesUnclosedAndPartialStreamingMetaTags() {
        val midStream = "寒风穿过长廊，烛火微微摇曳。\n<status_hud>{\"location\":\"藏经阁\",\"time\":\"子"
        assertEquals("寒风穿过长廊，烛火微微摇曳。", NovelExport.stripInternalTags(midStream))
        assertTrue(NovelExport.isStreamingInternalMeta(midStream))

        val partialPrefix = "他推开厚重的木门。\n<lore_sy"
        assertEquals("他推开厚重的木门。", NovelExport.stripInternalTags(partialPrefix))
        assertTrue(NovelExport.isStreamingInternalMeta(partialPrefix))

        val normalProse = "他说：若力量 < 100 则难以破阵。"
        assertEquals(normalProse, NovelExport.stripInternalTags(normalProse))
        assertFalse(NovelExport.isStreamingInternalMeta(normalProse))
    }

    @Test
    fun groupMessagesAndRecalculateWordCounts_splitsChaptersDeterministically() {
        val ch1 = NovelChapter(id = "c1", conversationId = "n1", order = 1, title = "第一章 启程", startSeq = 0, endSeq = 2)
        val ch2 = NovelChapter(id = "c2", conversationId = "n1", order = 2, title = "第二章 暗潮", startSeq = 3, endSeq = null)
        val msgs = listOf(
            ChatMessage(id = "u1", conversationId = "n1", role = "user", content = "开篇", createdAt = 1L, seq = 1),
            ChatMessage(id = "a1", conversationId = "n1", role = "assistant", content = "一二三四五<choices>[\"甲\"]</choices>", createdAt = 2L, seq = 2),
            ChatMessage(id = "u2", conversationId = "n1", role = "user", content = "进入第二章", createdAt = 3L, seq = 3),
            ChatMessage(id = "a2", conversationId = "n1", role = "assistant", content = "六七八九十甲乙", createdAt = 4L, seq = 4),
            ChatMessage(id = "a3_del", conversationId = "n1", role = "assistant", content = "已删除段落", createdAt = 5L, seq = 5, deletedAt = 99L),
        )

        val sections = NovelExport.groupMessagesByChapter(listOf(ch1, ch2), msgs)
        assertEquals(2, sections.size)
        assertEquals(5, sections[0].wordCount)
        assertEquals(7, sections[1].wordCount)

        val updatedChapters = NovelEngine.recalculateChapterWordCounts(listOf(ch1, ch2), msgs)
        assertEquals(5, updatedChapters[0].wordCount)
        assertEquals(7, updatedChapters[1].wordCount)

        val single = NovelExport.formatSingleChapter(NovelConfig(title = "问道"), ch2, updatedChapters, msgs, markdown = false)
        assertTrue(single.contains("第二章 暗潮"))
        assertTrue(single.contains("六七八九十甲乙"))
        assertFalse(single.contains("一二三四五"))
    }

    @Test
    fun buildNovelSystemPrompt_respectsAntiAiPlayStyleStyleSampleAndRecaps() {
        val novel = NovelConfig(
            title = "寒江孤影",
            pov = "first",
            playStyle = "author",
            antiAiRules = true,
            styleSample = "雨落青瓦，檐角滴水声如断续更鼓。",
            enableChoices = true,
        )
        val ch1 = NovelChapter(id = "c1", conversationId = "n1", order = 1, title = "第一章 雨夜", summary = "主角在旧码头救下密探。", status = "done")
        val ch2 = NovelChapter(id = "c2", conversationId = "n1", order = 2, title = "第二章 渡口", outline = "追兵封锁城门", wordCount = 600)

        val sys = NovelEngine.buildNovelSystemPrompt(
            novel = novel,
            activeChapter = ch2,
            constantLore = emptyList(),
            previousChapters = listOf(ch1, ch2),
        )
        assertTrue(sys.contains("第一人称"))
        assertTrue(sys.contains("纯作者连载模式"))
        assertTrue(sys.contains("反 AI 味约束"))
        assertTrue(sys.contains("<style_reference>"))
        assertTrue(sys.contains("主角在旧码头救下密探"))

        val userTurn = NovelEngine.wrapNovelTurnUserMessage(
            rawContent = "写出城门盘查时的紧绷感",
            inputKind = NovelInputKind.director,
            novel = novel,
            activeChapter = ch2,
            triggeredLore = emptyList(),
            existingLoreTitles = emptyList(),
        )
        assertTrue(userTurn.contains("已写 600 字"))
        assertFalse(userTurn.contains("<choices>"))
    }

    @Test
    fun craftParsers_handleGenesisLoreExtractChaptersAndChoices() {
        val preset = NovelPresets.findById("custom")
        val genesisJson = """
            ```json
            {
              "title": "雾港时钟",
              "synopsis": "永雾笼罩的港口中，每一座停摆的钟塔都藏着一段被窃走的时间。",
              "worldview": "蒸汽与时砂共存的孤岛城邦。",
              "mainOutline": "第一卷 寻钟 -> 第二卷 逆时",
              "style": "克制悬疑",
              "authorNote": "重视钟表机械细节",
              "firstChapterTitle": "第一章 停摆的七点整",
              "firstChapterOutline": "修表匠收到一枚倒走的怀表。",
              "starterLore": [{"category":"item","title":"逆行怀表","content":"表针每秒倒退半格","keywords":["怀表","逆行"]}],
              "openingProse": "浓雾沿着防波堤爬上窗台，柜台上的黄铜怀表突然逆向咔哒一声。"
            }
            ```
        """.trimIndent()
        val genesis = NovelEngine.parseWizardGenesisResponse(genesisJson, preset, "", "conv1")
        assertNotNull(genesis)
        assertEquals("雾港时钟", genesis!!.title)
        assertEquals(1, genesis.starterLore.size)
        assertEquals("逆行怀表", genesis.starterLore[0].title)
        assertTrue(genesis.openingProse.contains("黄铜怀表"))

        val existing = listOf(
            NovelLoreEntry(
                id = "lore-existing",
                conversationId = "conv1",
                category = "item",
                title = "逆行怀表",
                content = "旧描述",
                keywords = listOf("怀表"),
                priority = 88,
            ),
        )
        val extracted = NovelEngine.parseLoreExtractResponse(
            raw = """[{"category":"item","title":"逆行怀表","content":"内盖刻有旧历日期","keywords":["旧历"],"aliases":["黑表"]}]""",
            conversationId = "conv1",
            turnSeq = 4,
            existingLore = existing,
        )
        assertEquals(1, extracted.size)
        assertEquals("lore-existing", extracted[0].id)
        assertEquals(88, extracted[0].priority)
        assertTrue(extracted[0].keywords.containsAll(listOf("旧历", "怀表")))
        assertEquals(listOf("黑表"), extracted[0].aliases)

        val chapters = NovelEngine.parseSuggestedChapters(
            """[{"title":"第二章 钟塔暗门","outline":"潜入废弃钟楼"},{"title":"第三章 时砂交易","outline":"黑市遭遇守夜人"}]""",
        )
        assertEquals(2, chapters.size)
        assertEquals("第二章 钟塔暗门", chapters[0].title)

        val choicesFromBullets = NovelEngine.parseChoicesOnly("1. 拆开怀表后盖查验机芯\n2. 跟踪送表人的背影\n3. 立刻锁门报案")
        assertEquals(3, choicesFromBullets.size)
        assertEquals("拆开怀表后盖查验机芯", choicesFromBullets[0])

        val polishMsgs = NovelEngine.buildPolishMessages("他嘴角微微上扬。", "古雅", "", presetMode = "deai")
        assertTrue(polishMsgs[1].content.toString().contains("去 AI 味精修"))

        val continueMsgs = NovelEngine.buildContinueMessages(
            novel = NovelConfig(title = "雾港时钟", authorNote = "保持冷峻节奏"),
            activeChapter = NovelChapter(id = "c1", conversationId = "conv1", order = 1, title = "第一章 停摆", wordCount = 520),
            recentMessages = listOf(
                ChatMessage(id = "a1", conversationId = "conv1", role = "assistant", content = "浓雾漫过窗台。", createdAt = 1L, seq = 1),
            ),
            constantLore = emptyList(),
            triggeredLore = existing,
            existingLoreTitles = listOf("逆行怀表"),
            instruction = "镜头切向门外脚步声",
        )
        assertTrue(continueMsgs.last().content.toString().contains("无缝续写指令"))
        assertTrue(continueMsgs.last().content.toString().contains("逆行怀表"))
        assertTrue(continueMsgs.last().content.toString().contains("镜头切向门外脚步声"))
    }

    @Test
    fun allNovelPresets_haveZhAndEnGenreLabels() {
        NovelPresets.ALL.forEach { preset ->
            assertTrue("Missing ZH genre for ${preset.id}", !ZhStrings.novel.genres[preset.id].isNullOrBlank())
            assertTrue("Missing EN genre for ${preset.id}", !EnStrings.novel.genres[preset.id].isNullOrBlank())
        }
    }
}
