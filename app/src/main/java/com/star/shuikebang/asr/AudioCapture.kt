package com.star.shuikebang.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.concurrent.thread

/**
 * 麦克风采集：16kHz / 单声道 / PCM16，转 Float[-1,1] 后按帧回调。
 * 音频数据只在内存中流转，绝不写文件。
 */
class AudioCapture(
    private val sampleRate: Int = 16_000,
    /** 每帧毫秒数，sherpa 流式建议 100ms */
    private val frameMs: Int = 100,
    private val onSamples: (FloatArray, Int) -> Unit,
    private val onError: (Throwable) -> Unit = {},
) {
    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate) // 至少 1s 缓冲，防止低端机抖动
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // 语音识别源，系统会做降噪/回声抑制
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                onError(IllegalStateException("AudioRecord 初始化失败（麦克风被占用或权限缺失）"))
                return false
            }
            record = rec
            running = true
            rec.startRecording()
            worker = thread(name = "audio-capture", isDaemon = true) { loop() }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            onError(t)
            false
        }
    }

    private fun loop() {
        val frameShorts = sampleRate * frameMs / 1000
        val shortBuf = ShortArray(frameShorts)
        val floatBuf = FloatArray(frameShorts)
        val rec = record ?: return
        while (running) {
            val n = rec.read(shortBuf, 0, frameShorts)
            if (n > 0) {
                for (i in 0 until n) floatBuf[i] = shortBuf[i].toFloat() / 32768f
                val out = if (n == frameShorts) floatBuf else floatBuf.copyOf(n)
                onSamples(out, sampleRate)
            } else if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE) {
                onError(IllegalStateException("AudioRecord.read 错误码：$n"))
                break
            }
        }
    }

    fun stop() {
        running = false
        worker?.let { runCatching { it.join(400) } }
        worker = null
        runCatching { record?.stop() }
        record?.release()
        record = null
    }

    companion object {
        private const val TAG = "AudioCapture"
    }
}
