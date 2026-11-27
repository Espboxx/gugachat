package com.gugachat.app.ui.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioRoutingPolicyTest {

    @Test
    fun `persisted subtab wins regardless of artworks or cards`() {
        for (saved in 0..2) {
            assertEquals(
                saved,
                StudioRoutingPolicy.resolveInitialSubtab(
                    savedSubtab = saved,
                    imageWorksCount = 12,
                    directImageWorksCount = 5,
                    hasCustomCards = false,
                ),
            )
            assertEquals(
                saved,
                StudioRoutingPolicy.resolveInitialSubtab(
                    savedSubtab = saved,
                    imageWorksCount = 0,
                    directImageWorksCount = 0,
                    hasCustomCards = true,
                ),
            )
        }
    }

    @Test
    fun `new BYOK user with no artworks and no custom cards defaults to Characters tab`() {
        assertEquals(
            StudioRoutingPolicy.TAB_CHARACTERS,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = null,
                imageWorksCount = 0,
                directImageWorksCount = 0,
                hasCustomCards = false,
            ),
        )
    }

    @Test
    fun `legacy GuGuGa artwork user with no custom cards defaults to Atelier tab`() {
        assertEquals(
            StudioRoutingPolicy.TAB_ATELIER,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = null,
                imageWorksCount = 4,
                directImageWorksCount = 0,
                hasCustomCards = false,
            ),
        )
    }

    @Test
    fun `direct image user with no custom cards defaults to Atelier tab`() {
        assertEquals(
            StudioRoutingPolicy.TAB_ATELIER,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = null,
                imageWorksCount = 0,
                directImageWorksCount = 2,
                hasCustomCards = false,
            ),
        )
    }

    @Test
    fun `user who already has custom cards starts on Characters tab even with artworks`() {
        assertEquals(
            StudioRoutingPolicy.TAB_CHARACTERS,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = null,
                imageWorksCount = 8,
                directImageWorksCount = 3,
                hasCustomCards = true,
            ),
        )
    }

    @Test
    fun `out of range saved subtab falls back cleanly to heuristic`() {
        assertEquals(
            StudioRoutingPolicy.TAB_ATELIER,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = -1,
                imageWorksCount = 1,
                directImageWorksCount = 0,
                hasCustomCards = false,
            ),
        )
        assertEquals(
            StudioRoutingPolicy.TAB_CHARACTERS,
            StudioRoutingPolicy.resolveInitialSubtab(
                savedSubtab = 99,
                imageWorksCount = 0,
                directImageWorksCount = 0,
                hasCustomCards = false,
            ),
        )
    }
}
