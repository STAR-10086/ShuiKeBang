package com.star.shuikebang.nlp

import android.content.Context
import kotlin.math.exp

/**
 * 极小「是否向学生提问」字级分类器（纯 Kotlin 前向，无任何 native/第三方依赖）。
 *
 * 结构与 research/mini_q/train_export.py 严格对应：
 *   句向量 = 字 embedding 平均 -> h = ReLU(句向量·W1 + b1) -> logit = h·W2 + b2 -> sigmoid
 * 权重为 int8 对称量化（real = q * scale），整模型约 35KB，随 APK 放在 assets/qclassifier。
 *
 * 模型只负责对「含疑问信号但规则拿不准」的歧义句（如自问自答、含"什么"的陈述）做概率仲裁，
 * 不替代高置信规则；加载失败时 [score] 返回 null，调用方回退纯规则，绝不影响主流程。
 */
class QuestionMlClassifier private constructor(
    private val vocab: Map<String, Int>,
    private val dim: Int,
    private val hidden: Int,
    private val scaleE: Float,
    private val qEmb: IntArray,
    private val scaleW1: Float,
    private val qW1: IntArray,
    private val b1: FloatArray,
    private val scaleW2: Float,
    private val qW2: IntArray,
    private val b2: Float,
) {
    /** 返回提问概率 [0,1]；空句返回 0f。 */
    fun score(raw: String): Float {
        val text = raw.trim()
        if (text.isEmpty()) return 0f
        // 句向量：按字出现次数累加 embedding 后平均（与训练一致，重复字重复计入）
        val emb = FloatArray(dim)
        var n = 0
        var i = 0
        while (i < text.length) {
            val ch = text.substring(i, i + 1)
            val id = vocab[ch] ?: UNK
            val base = id * dim
            for (d in 0 until dim) emb[d] += qEmb[base + d].toFloat()
            n++
            i++
        }
        if (n == 0) return 0f
        var inv = scaleE / n
        for (d in 0 until dim) emb[d] *= inv

        val h = FloatArray(hidden)
        for (hh in 0 until hidden) {
            var acc = b1[hh]
            for (d in 0 until dim) {
                acc += emb[d] * (qW1[d * hidden + hh].toFloat() * scaleW1)
            }
            h[hh] = if (acc > 0f) acc else 0f // ReLU
        }
        var logit = b2
        for (hh in 0 until hidden) {
            logit += h[hh] * (qW2[hh].toFloat() * scaleW2)
        }
        return 1f / (1f + exp(-logit))
    }

    companion object {
        private const val UNK = 1
        private const val DIR = "qclassifier"

        /** 从 assets 加载；任何异常都安全降级为 null（回退纯规则）。 */
        fun fromAssets(context: Context): QuestionMlClassifier? = try {
            val vocabText = context.assets.open("$DIR/vocab.txt").bufferedReader().use { it.readText() }
            val modelText = context.assets.open("$DIR/model.txt").bufferedReader().use { it.readText() }
            parse(vocabText, modelText)
        } catch (t: Throwable) {
            null
        }

        /** 纯 JVM 解析，便于单元测试直接构造。 */
        fun parse(vocabText: String, modelText: String): QuestionMlClassifier? = try {
            val lines = modelText.split("\n")
            require(lines.size >= 9) { "model.txt 行数不足" }
            val meta = lines[0].trim().split(" ")
            val v = meta[0].toInt(); val d = meta[1].toInt(); val hh = meta[2].toInt()
            val scaleE = lines[1].trim().toFloat()
            val qE = parseInts(lines[2], v * d)
            val scaleW1 = lines[3].trim().toFloat()
            val qW1 = parseInts(lines[4], d * hh)
            val b1 = parseFloats(lines[5], hh)
            val scaleW2 = lines[6].trim().toFloat()
            val qW2 = parseInts(lines[7], hh)
            val b2 = lines[8].trim().toFloat()
            val vocab = LinkedHashMap<String, Int>(v * 2)
            vocabText.split("\n").forEachIndexed { idx, w ->
                val word = w.trim { it <= ' ' || it == '\r' }
                if (word.isNotEmpty()) vocab[word] = idx
            }
            QuestionMlClassifier(vocab, d, hh, scaleE, qE, scaleW1, qW1, b1, scaleW2, qW2, b2)
        } catch (t: Throwable) {
            null
        }

        private val ws = Regex("\\s+")
        private fun parseInts(line: String, expect: Int): IntArray {
            val parts = line.trim().split(ws).filter { it.isNotEmpty() }
            require(parts.size == expect) { "int 张量长度 ${parts.size} != $expect" }
            return IntArray(expect) { parts[it].trim().toInt() }
        }

        private fun parseFloats(line: String, expect: Int): FloatArray {
            val parts = line.trim().split(ws).filter { it.isNotEmpty() }
            require(parts.size == expect) { "float 张量长度 ${parts.size} != $expect" }
            return FloatArray(expect) { parts[it].trim().toFloat() }
        }
    }
}
