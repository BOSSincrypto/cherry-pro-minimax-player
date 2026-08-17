package io.cherry.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-function [SettingsRepository.clampSpeed] logic.
 * These run on the JVM (no Android instrumentation required) so they
 * execute in `gradle test` and don't need an emulator.
 */
class SettingsRepositoryTest {

    @Test
    fun `clampSpeed preserves in-range values`() {
        assertEquals(1.0f, SettingsRepository.clampSpeed(1.0f), 0.001f)
        assertEquals(2.0f, SettingsRepository.clampSpeed(2.0f), 0.001f)
        assertEquals(0.5f, SettingsRepository.clampSpeed(0.5f), 0.001f)
    }

    @Test
    fun `clampSpeed coerces below minimum`() {
        assertEquals(SettingsRepository.MIN_SPEED, SettingsRepository.clampSpeed(0.1f), 0.001f)
        assertEquals(SettingsRepository.MIN_SPEED, SettingsRepository.clampSpeed(-1.0f), 0.001f)
        assertEquals(SettingsRepository.MIN_SPEED, SettingsRepository.clampSpeed(0.0f), 0.001f)
    }

    @Test
    fun `clampSpeed coerces above maximum`() {
        assertEquals(SettingsRepository.MAX_SPEED, SettingsRepository.clampSpeed(4.5f), 0.001f)
        assertEquals(SettingsRepository.MAX_SPEED, SettingsRepository.clampSpeed(99.0f), 0.001f)
        assertEquals(SettingsRepository.MAX_SPEED, SettingsRepository.clampSpeed(Float.MAX_VALUE), 0.001f)
    }

    @Test
    fun `clampSpeed snaps to nearest 0_05 step`() {
        // 0.825 is between 0.80 and 0.85; steps = floor(0.825 / 0.05) = 16 → 0.80
        assertEquals(0.80f, SettingsRepository.clampSpeed(0.825f), 0.001f)
        // 0.876 → steps = floor(0.876 / 0.05) = 17 → 0.85
        assertEquals(0.85f, SettingsRepository.clampSpeed(0.876f), 0.001f)
        // 1.024 → steps = floor(1.024 / 0.05) = 20 → 1.00
        assertEquals(1.00f, SettingsRepository.clampSpeed(1.024f), 0.001f)
        // 1.025 → steps = floor(1.025 / 0.05) = 20 → 1.00 (avoids floating-point exactness)
        assertEquals(1.00f, SettingsRepository.clampSpeed(1.025f), 0.001f)
        // 1.026 → steps = floor(1.026 / 0.05) = 20 → 1.00
        assertEquals(1.00f, SettingsRepository.clampSpeed(1.026f), 0.001f)
        // 1.030 → steps = floor(1.030 / 0.05) = 20 → 1.00
        assertEquals(1.00f, SettingsRepository.clampSpeed(1.030f), 0.001f)
    }

    @Test
    fun `clampSpeed result is always inside legal range`() {
        // Sweep across the full input range; every output must be inside [MIN, MAX].
        var x = -10f
        while (x <= 10f) {
            val clamped = SettingsRepository.clampSpeed(x)
            assertTrue(
                "clampSpeed($x) = $clamped out of range",
                clamped in SettingsRepository.MIN_SPEED..SettingsRepository.MAX_SPEED,
            )
            x += 0.013f
        }
    }
}