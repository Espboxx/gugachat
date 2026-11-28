package com.gugachat.app.domain.chat

import com.gugachat.app.core.util.FuzzyMatcher
import com.gugachat.app.data.local.WorldbookEntryEntity

enum class PresetBlockStatusFilter {
    ALL,
    ENABLED,
    DISABLED,
}

data class IndexedPresetBlock(
    val originalIndex: Int,
    val block: PromptPresetBlock,
)

/**
 * Pure-JVM operations for manipulating SillyTavern / GuGuChat [PromptPresetBlock] lists safely
 * by original index (preventing duplicate/blank identifier collisions and preserving structural
 * `marker == true` placeholders during batch toggles).
 */
object PromptPresetBlockOps {

    fun toggleBlock(
        blocks: List<PromptPresetBlock>,
        originalIndex: Int,
        enabled: Boolean? = null,
    ): List<PromptPresetBlock> {
        if (originalIndex !in blocks.indices) return blocks
        val current = blocks[originalIndex]
        val nextEnabled = enabled ?: !current.enabled
        if (current.enabled == nextEnabled) return blocks
        return blocks.mapIndexed { idx, block ->
            if (idx == originalIndex) block.copy(enabled = nextEnabled) else block
        }
    }

    /**
     * Enables or disables all non-marker blocks at [indices].
     * Structural marker blocks (`marker == true`, e.g. chatHistory/worldInfo) are always skipped.
     */
    fun batchSetEnabled(
        blocks: List<PromptPresetBlock>,
        indices: Collection<Int>,
        enabled: Boolean,
    ): List<PromptPresetBlock> {
        if (blocks.isEmpty() || indices.isEmpty()) return blocks
        val targetSet = indices.filterTo(HashSet()) { it in blocks.indices }
        if (targetSet.isEmpty()) return blocks
        var changed = false
        val updated = blocks.mapIndexed { idx, block ->
            if (idx in targetSet && !block.marker && block.enabled != enabled) {
                changed = true
                block.copy(enabled = enabled)
            } else {
                block
            }
        }
        return if (changed) updated else blocks
    }

    /**
     * Swaps the block at [originalIndex] with the block at `originalIndex + delta`, preserving
     * list order of all other blocks. Returns [blocks] unchanged if out of bounds or `delta == 0`.
     */
    fun moveBlock(
        blocks: List<PromptPresetBlock>,
        originalIndex: Int,
        delta: Int,
    ): List<PromptPresetBlock> {
        if (delta == 0 || originalIndex !in blocks.indices) return blocks
        val targetIndex = originalIndex + delta
        if (targetIndex !in blocks.indices) return blocks
        val mutable = blocks.toMutableList()
        val temp = mutable[originalIndex]
        mutable[originalIndex] = mutable[targetIndex]
        mutable[targetIndex] = temp
        return mutable
    }

    /**
     * Finds the swap delta to move [originalIndex] by one step (`direction < 0` up, `> 0` down)
     * within the same injection zone (`injectionPosition != 1` static vs `== 1` dynamic).
     */
    fun findSameZoneSwapDelta(
        blocks: List<PromptPresetBlock>,
        originalIndex: Int,
        direction: Int,
    ): Int? {
        if (direction == 0 || originalIndex !in blocks.indices) return null
        val isDynamic = blocks[originalIndex].injectionPosition == 1
        val step = if (direction < 0) -1 else 1
        var cursor = originalIndex + step
        while (cursor in blocks.indices) {
            if ((blocks[cursor].injectionPosition == 1) == isDynamic) {
                return cursor - originalIndex
            }
            cursor += step
        }
        return null
    }

    fun filterBlocks(
        blocks: List<PromptPresetBlock>,
        query: String,
        statusFilter: PresetBlockStatusFilter = PresetBlockStatusFilter.ALL,
    ): List<IndexedPresetBlock> {
        val statusFiltered = blocks.mapIndexedNotNull { idx, block ->
            val keep = when (statusFilter) {
                PresetBlockStatusFilter.ALL -> true
                PresetBlockStatusFilter.ENABLED -> block.enabled
                PresetBlockStatusFilter.DISABLED -> !block.enabled
            }
            if (keep) IndexedPresetBlock(idx, block) else null
        }
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return statusFiltered
        return FuzzyMatcher.filterAndRank(statusFiltered, cleanQuery) { item ->
            listOf(
                item.block.name,
                item.block.identifier,
                item.block.role,
                item.block.content,
            )
        }
    }

    fun estimateBlockTokens(block: PromptPresetBlock, modelId: String? = null): Int {
        if (block.marker || block.content.isBlank()) return 0
        return TokenEstimator.estimateText(block.content, modelId)
    }

    fun estimateActiveTokens(blocks: List<PromptPresetBlock>, modelId: String? = null): Int =
        blocks.sumOf { block ->
            if (block.enabled && !block.marker) estimateBlockTokens(block, modelId) else 0
        }
}

/**
 * Pure-JVM helpers for worldbook entry ordering (`insertionOrder DESC, uid ASC` in Room)
 * without requiring any database schema migration.
 */
object WorldbookEntryOps {

    private val DB_COMPARATOR: Comparator<WorldbookEntryEntity> =
        compareByDescending<WorldbookEntryEntity> { it.insertionOrder }
            .thenBy { it.uid }

    /**
     * Computes the minimal list of updated [WorldbookEntryEntity] rows required to move the entry
     * at [index] by [delta] (`-1` up, `+1` down) while guaranteeing the Room query ordering
     * (`ORDER BY insertionOrder DESC, uid ASC`) matches the swapped visual order even when
     * entries have tied `insertionOrder` values.
     */
    fun computeReorderedUpdates(
        entriesInDbOrder: List<WorldbookEntryEntity>,
        index: Int,
        delta: Int,
        updatedAt: Long,
    ): List<WorldbookEntryEntity> {
        if (delta == 0 || index !in entriesInDbOrder.indices) return emptyList()
        val targetIndex = index + delta
        if (targetIndex !in entriesInDbOrder.indices) return emptyList()

        val desired = entriesInDbOrder.toMutableList()
        val moving = desired[index]
        val neighbor = desired[targetIndex]
        desired[index] = neighbor
        desired[targetIndex] = moving

        // Fast path: swapping only the two entries' insertionOrder values when it yields the exact desired DB order.
        if (moving.insertionOrder != neighbor.insertionOrder) {
            val swappedMoving = moving.copy(insertionOrder = neighbor.insertionOrder, updatedAt = updatedAt)
            val swappedNeighbor = neighbor.copy(insertionOrder = moving.insertionOrder, updatedAt = updatedAt)
            val candidate = entriesInDbOrder.mapIndexed { idx, item ->
                when (idx) {
                    index -> swappedMoving
                    targetIndex -> swappedNeighbor
                    else -> item
                }
            }
            if (candidate.sortedWith(DB_COMPARATOR).map { it.id } == desired.map { it.id }) {
                return listOf(swappedMoving, swappedNeighbor)
            }
        }

        // Tie-safe fallback: assign strictly descending insertionOrder across the desired sequence.
        val step = 10
        val base = maxOf(
            entriesInDbOrder.maxOf { it.insertionOrder },
            desired.size * step,
        )
        return desired.mapIndexedNotNull { pos, item ->
            val nextOrder = base - pos * step
            if (item.insertionOrder != nextOrder) {
                item.copy(insertionOrder = nextOrder, updatedAt = updatedAt)
            } else {
                null
            }
        }
    }
}
