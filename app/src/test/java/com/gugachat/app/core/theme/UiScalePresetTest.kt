package com.gugachat.app.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScalePresetTest {
    @Test
    fun `default preset is comfortable 92 percent`() {
        assertEquals(UiScalePreset.COMFORTABLE, UiScalePreset.DEFAULT)
        assertEquals(0.92f, UiScalePreset.DEFAULT.scale, 0.0001f)
    }

    @Test
    fun `fromWire round-trips all presets case-insensitively`() {
        UiScalePreset.entries.forEach { preset ->
            assertEquals(preset, UiScalePreset.fromWire(preset.wireValue))
            assertEquals(preset, UiScalePreset.fromWire("  ${preset.wireValue.uppercase()}  "))
        }
    }

    @Test
    fun `fromWire falls back to default for null or unknown values`() {
        assertEquals(UiScalePreset.DEFAULT, UiScalePreset.fromWire(null))
        assertEquals(UiScalePreset.DEFAULT, UiScalePreset.fromWire(""))
        assertEquals(UiScalePreset.DEFAULT, UiScalePreset.fromWire("huge"))
    }

    @Test
    fun `scales are strictly monotonic within ergonomic range`() {
        val scales = UiScalePreset.entries.map { it.scale }
        for (i in 0 until scales.lastIndex) {
            assertTrue("Expected ${scales[i]} < ${scales[i + 1]}", scales[i] < scales[i + 1])
        }
        assertTrue(scales.first() >= 0.80f)
        assertTrue(scales.last() <= 1.15f)
    }
}
