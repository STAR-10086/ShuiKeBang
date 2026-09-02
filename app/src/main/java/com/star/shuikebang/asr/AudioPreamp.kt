package com.star.shuikebang.asr

import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 麦克风增益档位。
 *
 * 课堂上老师离手机较远、声音偏小时，原始 PCM 电平过低会让离线识别明显变差。
 * 在音频送入识别引擎前做软件放大可显著改善小声识别率；音频仍只在内存流转、不落盘。
 */
enum class MicGainMode(
    val id: String,
    val label: String,
    val hint: String,
    /** 固定倍数档使用；AUTO/OFF 不走该值 */
    val fixedGain: Float,
) {
    AUTO("auto", "自动（推荐）", "老师声音小时自动放大，并抑制爆音", 0f),
    OFF("off", "关闭", "不放大，使用原始音量", 1f),
    X2("x2", "2 倍", "轻微放大", 2f),
    X3("x3", "3 倍", "明显放大", 3f),
    X5("x5", "5 倍", "离得很远 / 很安静时使用", 5f);

    companion object {
        fun of(id: String?): MicGainMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * 录音前级增益：纯 Kotlin、无 Android 依赖，可单元测试。
 *
 * - 固定档：样本 × 倍数后做 tanh 软限幅，避免硬削波爆音；
 * - [MicGainMode.AUTO]：轻量自动增益（AGC），按帧 RMS 缓慢把语音拉到目标电平，
 *   增益有上限、近静音不抬升（避免把底噪一起放大），增益变化做平滑防止“抽吸”。
 *
 * 处理在唯一的采集线程内进行；[mode] 允许其它线程读取，故标记 @Volatile。
 */
class AudioPreamp(initial: MicGainMode = MicGainMode.AUTO) {

    @Volatile
    var mode: MicGainMode = initial

    // ---- AGC 运行状态（仅采集线程访问）----
    private var agcLevel = TARGET_RMS
    private var agcGain = 1f

    /**
     * 原地处理一帧 [-1,1] PCM。
     * @return 本帧实际应用的线性增益（便于日志/调试）
     */
    fun process(buf: FloatArray, n: Int): Float {
        when (mode) {
            MicGainMode.OFF -> return 1f
            MicGainMode.AUTO -> return processAuto(buf, n)
            else -> {
                applyGain(buf, n, mode.fixedGain)
                return mode.fixedGain
            }
        }
    }

    /** 开始一段新录音时复位 AGC 平滑状态 */
    fun reset() {
        agcLevel = TARGET_RMS
        agcGain = 1f
    }

    private fun processAuto(buf: FloatArray, n: Int): Float {
        // 1) 本帧均方根电平
        var sumSq = 0.0
        for (i in 0 until n) sumSq += buf[i].toDouble() * buf[i]
        val rms = if (n > 0) sqrt(sumSq / n).toFloat() else 0f

        // 2) 电平包络：上升快、下降慢，贴合语音起止
        agcLevel = if (rms > agcLevel) {
            agcLevel + LEVEL_UP * (rms - agcLevel)
        } else {
            agcLevel + LEVEL_DOWN * (rms - agcLevel)
        }

        // 3) 目标增益：近静音不抬（不放大底噪），其余拉向目标电平并限制上限
        val desired = if (agcLevel < NOISE_FLOOR) {
            1f
        } else {
            (TARGET_RMS / agcLevel).coerceIn(1f, MAX_GAIN)
        }
        // 4) 增益自身平滑，避免帧间跳变
        agcGain += GAIN_SMOOTH * (desired - agcGain)

        applyGain(buf, n, agcGain)
        return agcGain
    }

    private fun applyGain(buf: FloatArray, n: Int, gain: Float) {
        if (gain == 1f) return
        for (i in 0 until n) buf[i] = tanh(buf[i] * gain)
    }

    companion object {
        /** 低于该 RMS（约 -46dBFS）视为近静音/底噪，不做提升 */
        private const val NOISE_FLOOR = 0.005f
        /** 目标语音 RMS（约 -21dBFS），离线识别较舒适的电平区间 */
        private const val TARGET_RMS = 0.09f
        /** 自动增益上限，避免过度放大造成失真 */
        private const val MAX_GAIN = 4.5f
        private const val LEVEL_UP = 0.55f
        private const val LEVEL_DOWN = 0.12f
        private const val GAIN_SMOOTH = 0.18f
    }
}
