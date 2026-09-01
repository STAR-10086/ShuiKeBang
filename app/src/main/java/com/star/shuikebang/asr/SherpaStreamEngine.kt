package com.star.shuikebang.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/** 流式识别回调：partial 为边说边刷新的当前句，final 为断句后的定稿句 */
interface StreamAsrCallback {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(t: Throwable)
}

/**
 * sherpa-onnx 真流式识别引擎封装（OnlineRecognizer / Transducer Zipformer）。
 * 所有调用应来自同一个采集线程；识别全部在端侧完成，无网络。
 */
class SherpaStreamEngine(
    private val spec: AsrModelSpec,
    private val paths: ModelPaths,
    private val numThreads: Int = 2,
) {
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    @Volatile
    private var started = false

    val isReady: Boolean get() = recognizer != null

    fun init(): Boolean = try {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = paths.encoder,
                    decoder = paths.decoder,
                    joiner = paths.joiner,
                ),
                tokens = paths.tokens,
                numThreads = numThreads,
                provider = "cpu",
                modelType = spec.modelType,
                debug = false,
            ),
            // 课堂场景：检测到人声后停顿 1.2s 即断句；单句最长 20s 强制断
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.2f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        recognizer = OnlineRecognizer(config = config)
        stream = recognizer!!.createStream()
        true
    } catch (t: Throwable) {
        Log.e(TAG, "init failed", t)
        false
    }

    fun start() {
        check(recognizer != null) { "引擎未初始化" }
        stream = recognizer!!.createStream()
        started = true
    }

    /** 喂入一帧 PCM float（16kHz），驱动 partial / final 回调 */
    fun accept(samples: FloatArray, sampleRate: Int, callback: StreamAsrCallback) {
        val rec = recognizer ?: return
        val st = stream ?: return
        if (!started) return
        try {
            st.acceptWaveform(samples, sampleRate)
            while (rec.isReady(st)) rec.decode(st)
            val partial = rec.getResult(st).text.trim()
            if (partial.isNotEmpty()) callback.onPartial(partial)
            if (rec.isEndpoint(st)) {
                val finalText = rec.getResult(st).text.trim()
                if (finalText.isNotEmpty()) callback.onFinal(finalText)
                rec.reset(st)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "accept failed", t)
            callback.onError(t)
        }
    }

    /** 手动结束当前句（用户点停止时把残余 partial 定稿） */
    fun flush(): String? {
        val rec = recognizer ?: return null
        val st = stream ?: return null
        return try {
            st.inputFinished()
            while (rec.isReady(st)) rec.decode(st)
            rec.getResult(st).text.trim().ifEmpty { null }
        } catch (t: Throwable) {
            Log.e(TAG, "flush failed", t)
            null
        }
    }

    fun stop() {
        started = false
        runCatching { stream?.release() }
        stream = null
    }

    fun release() {
        stop()
        runCatching { recognizer?.release() }
        recognizer = null
    }

    companion object {
        private const val TAG = "SherpaStream"
    }
}
