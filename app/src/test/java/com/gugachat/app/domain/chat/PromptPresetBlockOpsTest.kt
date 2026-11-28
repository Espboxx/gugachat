package com.gugachat.app.domain.chat

import com.gugachat.app.data.local.WorldbookEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPresetBlockOpsTest {

    private fun block(
        id: String,
        name: String = id,
        role: String = "system",
        content: String = "Sample prompt text for $id",
        marker: Boolean = false,
        enabled: Boolean = true,
        injectionPosition: Int = 0,
        injectionDepth: Int = 4,
    ) = PromptPresetBlock(
        identifier = id,
        name = name,
        role = role,
        content = content,
        marker = marker,
        enabled = enabled,
        injectionPosition = injectionPosition,
        injectionDepth = injectionDepth,
    )

    @Test
    fun toggleBlock_togglesExactIndexEvenWithDuplicateAndBlankIdentifiers() {
        val blocks = listOf(
            block(id = "dup", name = "First", enabled = true),
            block(id = "dup", name = "Second", enabled = true),
            block(id = "", name = "Blank", enabled = false),
        )

        val afterSecond = PromptPresetBlockOps.toggleBlock(blocks, originalIndex = 1)
        assertTrue(afterSecond[0].enabled)
        assertFalse(afterSecond[1].enabled)
        assertFalse(afterSecond[2].enabled)

        val afterBlank = PromptPresetBlockOps.toggleBlock(afterSecond, originalIndex = 2, enabled = true)
        assertTrue(afterBlank[0].enabled)
        assertFalse(afterBlank[1].enabled)
        assertTrue(afterBlank[2].enabled)

        // Out-of-bounds index is a no-op returning the original list reference
        assertSame(afterBlank, PromptPresetBlockOps.toggleBlock(afterBlank, originalIndex = -1))
        assertSame(afterBlank, PromptPresetBlockOps.toggleBlock(afterBlank, originalIndex = 99))
    }

    @Test
    fun batchSetEnabled_alwaysSkipsStructuralMarkers() {
        val blocks = listOf(
            block(id = "main", enabled = true, marker = false),
            block(id = "chatHistory", content = "", enabled = true, marker = true),
            block(id = "nsfw", enabled = true, marker = false),
            block(id = "worldInfoBefore", content = "", enabled = false, marker = true),
        )

        val disabled = PromptPresetBlockOps.batchSetEnabled(blocks, listOf(0, 1, 2, 3), enabled = false)
        assertFalse(disabled[0].enabled)
        assertTrue("Enabled marker must remain untouched", disabled[1].enabled)
        assertFalse(disabled[2].enabled)
        assertFalse("Disabled marker must remain untouched", disabled[3].enabled)

        val reEnabled = PromptPresetBlockOps.batchSetEnabled(disabled, listOf(0, 1, 2, 3), enabled = true)
        assertTrue(reEnabled[0].enabled)
        assertTrue(reEnabled[1].enabled)
        assertTrue(reEnabled[2].enabled)
        assertFalse("Disabled marker must NOT be enabled by batch action", reEnabled[3].enabled)
    }

    @Test
    fun batchSetEnabled_onlyTouchesSpecifiedIndices() {
        val blocks = listOf(
            block(id = "a", enabled = true),
            block(id = "b", enabled = true),
            block(id = "c", enabled = false),
        )

        val updated = PromptPresetBlockOps.batchSetEnabled(blocks, listOf(0, 2), enabled = false)
        assertFalse(updated[0].enabled)
        assertTrue(updated[1].enabled)
        assertFalse(updated[2].enabled)
    }

    @Test
    fun moveBlock_swapsWithinBoundsAndPreservesOtherOrder() {
        val blocks = listOf(
            block(id = "a"),
            block(id = "b"),
            block(id = "c"),
        )

        // Out-of-bounds no-ops
        assertSame(blocks, PromptPresetBlockOps.moveBlock(blocks, originalIndex = 0, delta = -1))
        assertSame(blocks, PromptPresetBlockOps.moveBlock(blocks, originalIndex = 2, delta = +1))
        assertSame(blocks, PromptPresetBlockOps.moveBlock(blocks, originalIndex = 1, delta = 0))

        // Move "c" up -> [a, c, b]
        val movedUp = PromptPresetBlockOps.moveBlock(blocks, originalIndex = 2, delta = -1)
        assertEquals(listOf("a", "c", "b"), movedUp.map { it.identifier })

        // Move "a" down -> [c, a, b]
        val movedDown = PromptPresetBlockOps.moveBlock(movedUp, originalIndex = 0, delta = +1)
        assertEquals(listOf("c", "a", "b"), movedDown.map { it.identifier })
    }

    @Test
    fun findSameZoneSwapDelta_skipsInterleavedOtherZoneBlocks() {
        val blocks = listOf(
            block(id = "static_0", injectionPosition = 0),
            block(id = "dyn_1", injectionPosition = 1),
            block(id = "static_2", injectionPosition = 0),
            block(id = "dyn_3", injectionPosition = 1),
        )

        assertNull(PromptPresetBlockOps.findSameZoneSwapDelta(blocks, 0, -1))
        assertEquals(2, PromptPresetBlockOps.findSameZoneSwapDelta(blocks, 0, +1))
        assertEquals(-2, PromptPresetBlockOps.findSameZoneSwapDelta(blocks, 2, -1))
        assertNull(PromptPresetBlockOps.findSameZoneSwapDelta(blocks, 2, +1))

        val delta = PromptPresetBlockOps.findSameZoneSwapDelta(blocks, 2, -1)!!
        val swapped = PromptPresetBlockOps.moveBlock(blocks, 2, delta)
        assertEquals(listOf("static_2", "dyn_1", "static_0", "dyn_3"), swapped.map { it.identifier })
    }

    @Test
    fun filterBlocks_supportsStatusAndFuzzySearchPreservingOriginalIndices() {
        val blocks = listOf(
            block(id = "jailbreak_main", name = "越狱破限主指令", role = "system", content = "保持沉浸式文风", enabled = true),
            block(id = "cot_think", name = "思维链引导", role = "assistant", content = "先分析角色动机", enabled = false),
            block(id = "style_guide", name = "文风细则", role = "system", content = "避免重复修辞与水字数", enabled = true),
        )

        val enabledOnly = PromptPresetBlockOps.filterBlocks(blocks, "", PresetBlockStatusFilter.ENABLED)
        assertEquals(listOf(0, 2), enabledOnly.map { it.originalIndex })

        val disabledOnly = PromptPresetBlockOps.filterBlocks(blocks, "", PresetBlockStatusFilter.DISABLED)
        assertEquals(listOf(1), disabledOnly.map { it.originalIndex })

        val byContent = PromptPresetBlockOps.filterBlocks(blocks, "水字数", PresetBlockStatusFilter.ALL)
        assertEquals(listOf(2), byContent.map { it.originalIndex })

        val byRoleAndStatus = PromptPresetBlockOps.filterBlocks(blocks, "assistant", PresetBlockStatusFilter.DISABLED)
        assertEquals(listOf(1), byRoleAndStatus.map { it.originalIndex })

        val noMatch = PromptPresetBlockOps.filterBlocks(blocks, "assistant", PresetBlockStatusFilter.ENABLED)
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun estimateActiveTokens_countsOnlyEnabledNonMarkerBlocks() {
        val blocks = listOf(
            block(id = "active", content = "这是一段激活的系统提示词设定。", enabled = true, marker = false),
            block(id = "disabled", content = "这段已停用不应该计入。", enabled = false, marker = false),
            block(id = "marker", content = "Marker placeholder text", enabled = true, marker = true),
            block(id = "blank", content = "   ", enabled = true, marker = false),
        )

        val expected = TokenEstimator.estimateText("这是一段激活的系统提示词设定。")
        assertEquals(expected, PromptPresetBlockOps.estimateActiveTokens(blocks))
        assertEquals(0, PromptPresetBlockOps.estimateBlockTokens(blocks[2]))
        assertEquals(0, PromptPresetBlockOps.estimateBlockTokens(blocks[3]))
    }

    @Test
    fun worldbookEntryOps_reordersDistinctAndTiedInsertionOrdersDeterministically() {
        val distinct = listOf(
            WorldbookEntryEntity(id = "e1", uid = 1, insertionOrder = 200),
            WorldbookEntryEntity(id = "e2", uid = 2, insertionOrder = 100),
        )
        val updatesDistinct = WorldbookEntryOps.computeReorderedUpdates(distinct, index = 1, delta = -1, updatedAt = 999L)
        assertEquals(2, updatesDistinct.size)
        val mergedDistinct = applyUpdates(distinct, updatesDistinct)
            .sortedWith(compareByDescending<WorldbookEntryEntity> { it.insertionOrder }.thenBy { it.uid })
        assertEquals(listOf("e2", "e1"), mergedDistinct.map { it.id })

        // All tied insertionOrder=100: moving e2 up must still put e2 before e1 in DB sort order
        val tied = listOf(
            WorldbookEntryEntity(id = "e1", uid = 1, insertionOrder = 100),
            WorldbookEntryEntity(id = "e2", uid = 2, insertionOrder = 100),
            WorldbookEntryEntity(id = "e3", uid = 3, insertionOrder = 100),
        )
        val updatesTied = WorldbookEntryOps.computeReorderedUpdates(tied, index = 1, delta = -1, updatedAt = 1000L)
        val mergedTied = applyUpdates(tied, updatesTied)
            .sortedWith(compareByDescending<WorldbookEntryEntity> { it.insertionOrder }.thenBy { it.uid })
        assertEquals(listOf("e2", "e1", "e3"), mergedTied.map { it.id })

        // Mixed tie edge case: moving lower-uid e3(90) up past e2(100) must NOT jump above e1(100)
        val mixed = listOf(
            WorldbookEntryEntity(id = "e1", uid = 3, insertionOrder = 100),
            WorldbookEntryEntity(id = "e2", uid = 5, insertionOrder = 100),
            WorldbookEntryEntity(id = "e3", uid = 1, insertionOrder = 90),
        )
        val updatesMixed = WorldbookEntryOps.computeReorderedUpdates(mixed, index = 2, delta = -1, updatedAt = 1001L)
        val mergedMixed = applyUpdates(mixed, updatesMixed)
            .sortedWith(compareByDescending<WorldbookEntryEntity> { it.insertionOrder }.thenBy { it.uid })
        assertEquals(listOf("e1", "e3", "e2"), mergedMixed.map { it.id })
    }

    private fun applyUpdates(
        original: List<WorldbookEntryEntity>,
        updates: List<WorldbookEntryEntity>,
    ): List<WorldbookEntryEntity> {
        val byId = updates.associateBy { it.id }
        return original.map { byId[it.id] ?: it }
    }
}
