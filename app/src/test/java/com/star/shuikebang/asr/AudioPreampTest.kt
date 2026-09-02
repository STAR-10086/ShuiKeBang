package com.star.shuikebang.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class AudioPreampTest {

    private fun frame(amp: Float, n: Int = 1600): FloatArray = FloatArray(n) { amp }
    private fun rms(b: FloatArray, n: Int = b.size): Float {
        var s = 0.0
        for (i in 0 until n) s += b[i].toDouble() * b[i]
        return sqrt(s / n).toFloat()
    }
    private fun assertBounded(b: FloatArray) {
        for (v in b) {
            assertTrue("finite", v.isFinite())
            assertTrue("|v|<=1: $v", abs(v) <= 1f)
        }
    }

    @Test
    fun off_is_passthrough() {
        val amp = AudioPreamp(MicGainMode.OFF)
        val buf = frame(0.2f)
        val before = buf.copyOf()
        val g = amp.process(buf, buf.size)
        assertEquals(1f, g, 1e-6f)
        for (i in buf.indices) assertEquals(before[i], buf[i], 1e-7f)
    }

    @Test
    fun fixed_gain_amplifies_with_soft_clip() {
        val amp = AudioPreamp(MicGainMode.X2)
        val buf = frame(0.1f)
        amp.process(buf, buf.size)
        // tanh(0.2) ≈ 0.1974，确实被放大
        assertEquals(0.197375f, buf[0], 1e-4f)
    }

    @Test
    fun loud_signal_never_clips_beyond_unit() {
        val amp = AudioPreamp(MicGainMode.X5)
        val buf = frame(1.0f)
        amp.process(buf, buf.size)
        assertBounded(buf)
        // 仍然是正的、被软压缩而不是硬削成完全相同的 1
        assertTrue(buf[0] in 0.9f..1.0f)
    }

    @Test
    fun auto_amplifies_quiet_voice() {
        val amp = AudioPreamp(MicGainMode.AUTO).also { it.reset() }
        var last = FloatArray(0)
        repeat(120) { last = frame(0.012f).also { amp.process(it, it.size) } }
        assertBounded(last)
        val out = rms(last)
        assertTrue("小声应被明显放大，outRms=$out", out > 0.03f)
    }

    @Test
    fun auto_does_not_amplify_silence() {
        val amp = AudioPreamp(MicGainMode.AUTO).also { it.reset() }
        var gain = 1f
        repeat(120) {
            val buf = frame(0f)
            gain = amp.process(buf, buf.size)
            assertTrue(buf.all { it == 0f })
        }
        // 静音段增益应回落到接近 1，不抬底噪
        assertTrue("静音增益应≈1，实际=$gain", gain < 1.08f)
    }

    @Test
    fun auto_always_bounded_with_varied_levels() {
        val amp = AudioPreamp(MicGainMode.AUTO)
        var seed = 42L
        repeat(200) { k ->
            val level = when (k % 4) { 0 -> 0.002f; 1 -> 0.05f; 2 -> 0.4f; else -> 0.9f }
            val buf = FloatArray(1600) {
                seed = seed * 1103515245 + 12345
                level * (((seed ushr 16) and 0x7fff) / 32767f - 0.5f) * 2f
            }
            amp.process(buf, buf.size)
            assertBounded(buf)
        }
    }

    @Test
    fun mode_of_defaults_safely() {
        assertEquals(MicGainMode.AUTO, MicGainMode.of(null))
        assertEquals(MicGainMode.AUTO, MicGainMode.of("不存在的档位"))
        assertEquals(MicGainMode.X3, MicGainMode.of("x3"))
    }
}
