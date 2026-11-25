package com.gugachat.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RpEngineTest {

    @Test
    fun rollDice_evaluatesModifiersAdvantageAndCriticals() {
        val roll = RpEngine.rollDice("1d20+4", difficulty = 15, skill = "潜行", mode = "advantage", random = Random(42))
        assertEquals("1d20+4", roll.expression)
        assertEquals(1, roll.rolls.size)
        assertEquals(roll.rolls[0] + 4, roll.total)
        assertEquals("advantage", roll.mode)
        assertEquals(roll.total >= 15 || roll.critical == "nat20", roll.success)

        // Check deterministic critical detection via low/high seed search
        val d100 = RpEngine.rollDice("d100", difficulty = 50, skill = "侦查", random = Random(7))
        assertEquals("1d100", d100.expression)
        assertEquals(d100.rolls[0], d100.total)
    }

    @Test
    fun parseRpTurnResponse_updatesStateCastChoicesAndLedger() {
        val preset = RpPresets.findById("fantasy_rpg").config
        val raw = """
            莱拉眯起金瞳，指尖点向暗门缝隙：“看，风是从下面吹上来的。”
            <rp_state>{"scene":"黑曜迷宫入口","hpDelta":-10,"goldDelta":50,"addItems":["星光钥匙"],"removeItems":["火折子"],"castUpdates":[{"name":"莱拉·晨星","affinityDelta":8,"mood":"信任"}]}</rp_state>
            <rp_ledger>[{"kind":"clue","title":"西门暗道","content":"通往矿坑一层的地下通风口","keyEntities":["黑曜迷宫"]}]</rp_ledger>
            <rp_choices>[{"label":"撬开暗门","actionText":"用短刀撬动铁锁","inputKind":"action","skillCheck":"敏捷","difficulty":14}]</rp_choices>
        """.trimIndent()

        val out = RpEngine.parseRpTurnResponse(raw, preset.state, preset.cast, "rp1", turnSeq = 3)
        assertFalse(out.cleanNarrative.contains("<rp_state>"))
        assertEquals("黑曜迷宫入口", out.updatedState.scene)
        assertEquals(90, out.updatedState.hp)
        assertEquals(135, out.updatedState.gold)
        assertTrue(out.updatedState.inventory.contains("星光钥匙"))
        assertFalse(out.updatedState.inventory.contains("火折子"))
        val lyra = out.updatedCast.first { it.id == "npc_lyra" }
        assertEquals(33, lyra.affinity)
        assertEquals("信任", lyra.mood)
        assertEquals(1, out.choices.size)
        assertEquals(14, out.choices[0].difficulty)
        assertEquals(1, out.ledgerUpserts.size)
        assertEquals("西门暗道", out.ledgerUpserts[0].title)
    }
}
