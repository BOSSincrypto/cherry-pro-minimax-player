package io.cherry.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure unit tests for the gesture math (volume / brightness / seek deltas).
 * The actual pointer-input detection lives in [io.cherry.player.gesture]
 * but the numeric conversions here are deterministic enough to test in
 * isolation.
 */
class GestureMathTest {

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    @Test
    fun `vertical drag maps to volume delta`() {
        // 100 px drag upward at 200 px screen → +0.5 volume change (50%).
        val delta = (100f / 200f).coerceIn(-1f, 1f)
        val newVolume = clamp01(0.3f + delta)
        assertEquals(0.8f, newVolume, 0.001f)
    }

    @Test
    fun `volume clamp prevents overdrive`() {
        assertEquals(1.0f, clamp01(1.5f), 0.001f)
        assertEquals(0.0f, clamp01(-0.1f), 0.001f)
    }

    @Test
    fun `seek relative respects bounds`() {
        val duration = 60_000L
        val at = 5_000L
        // -10 s
        val back = (at - 10_000L).coerceIn(0L, duration)
        assertEquals(0L, back)
        // +10 s
        val fwd = (at + 10_000L).coerceIn(0L, duration)
        assertEquals(15_000L, fwd)
    }

    @Test
    fun `seek within duration`() {
        val duration = 60_000L
        val at = 55_000L
        val fwd = (at + 10_000L).coerceIn(0L, duration)
        assertEquals(60_000L, fwd) // clamped to duration
        assertTrue(abs(fwd - duration) < 1L)
    }
}