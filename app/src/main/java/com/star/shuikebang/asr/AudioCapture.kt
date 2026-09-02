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
    /** 可选的麦克风前级增益（小声放大），作用在转 float 之后、回调之前 */
    private val preamp: AudioPreamp? = null,
    private val onSamples: (FloatArray, Int) -> Unit,
    /** 采集启动失败 / 运行中 AudioRecord 出错时回调（在采集线程触发，调用方需自行切线程） */
    private val onError: (Throwable) -> Unit = {},
) {
    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    @Volatile
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
        var rec: AudioRecord? = null
        return try {
            rec = AudioRecord(
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
            rec.startRecording() // 可能抛异常，必须在赋 running 前/失败时回滚
            record = rec
            running = true
            worker = thread(name = "audio-capture", isDaemon = true) { loop() }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            // 关键：startRecording 抛异常时不能留下 running=true 与未释放的 record，
            // 否则下一次 start 会因 running 直接误判成功（“假录音”）并泄漏麦克风
            running = false
            runCatching { rec?.stop() }
            rec?.release()
            record = null
            worker = null
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
                // 送入识别前做增益（自动/固定档），老师声音偏小时提升识别率
                preamp?.process(floatBuf, n)
                val out = if (n == frameShorts) floatBuf else floatBuf.copyOf(n)
                onSamples(out, sampleRate)
            } else if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE) {
                // 运行中读取失败：采集已不可恢复，释放硬件并上报，避免界面停在“正在记录”的假状态
                abort(IllegalStateException("AudioRecord.read 错误码：$n"))
                break
            }
        }
    }

    /** 采集线程内部出错时的自我回收：停硬件、释放、复位标志，再上报一次 */
    private fun abort(t: Throwable) {
        running = false
        runCatching { record?.stop() }
        record?.release()
        record = null
        worker = null
        Log.e(TAG, "capture aborted", t)
        onError(t)
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
