package com.star.shuikebang.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.exp

class QuestionMlClassifierTest {

    /** 用内联极小模型验证解析与前向/量化反量化管道（不依赖 assets、必定可跑） */
    @Test
    fun forward_pipeline_math() {
        // V=4 D=2 H=1；E/W1/b1/W2 全 0，则 logit=b2，score=sigmoid(b2)
        val vocab = "<pad>\n<unk>\n甲\n乙"
        fun modelWith(b2: Float): String = listOf(
            "4 2 1",
            "0.01",
            "0 0 0 0 0 0 0 0",   // qE V*D=8
            "0.01",
            "0 0",                 // qW1 D*H=2
            "0.0",                 // b1
            "0.01",
            "0",                   // qW2 H=1
            b2.toString(),
        ).joinToString("\n")

        val pos = QuestionMlClassifier.parse(vocab, modelWith(2.0f))!!
        val expectPos = 1f / (1f + exp(-2.0f))
        assertEquals(expectPos, pos.score("甲乙"), 1e-5f)
        assertTrue(pos.score("甲乙") > 0.8f)

        val neg = QuestionMlClassifier.parse(vocab, modelWith(-2.0f))!!
        assertTrue(neg.score("甲乙") < 0.2f)

        // 空句安全
        assertEquals(0f, pos.score("   "), 1e-6f)
        // 未登录字走 unk，不崩溃且范围合法
        val p = pos.score("不存在的字")
        assertTrue(p in 0f..1f)
    }

    @Test
    fun parse_rejects_malformed() {
        assertNull(QuestionMlClassifier.parse("a\nb", "only one line"))
    }

    /** 真实导出模型存在时（CI/本地源码树）校验语义与规则融合；不存在则跳过 */
    @Test
    fun real_model_disambiguates_and_fuses_with_rules() {
        val dir = File("src/main/assets/qclassifier")
        val modelFile = File(dir, "model.txt")
        val vocabFile = File(dir, "vocab.txt")
        assumeTrue(modelFile.exists() && vocabFile.exists())

        val ml = QuestionMlClassifier.parse(vocabFile.readText(), modelFile.readText())
        assertNotNull("真实模型应可解析", ml)
        ml!!

        // 纯模型语义：真提问高分，自问自答/讲课/含疑问词陈述低分
        assertTrue("真提问应高分", ml.score("什么是偏导数") >= 0.5f)
        assertTrue("真提问应高分", ml.score("这个方程有几个解") >= 0.5f)
        assertTrue("自问自答应低分", ml.score("什么是梯度呢梯度就是一个向量") < 0.5f)
        assertTrue("讲课陈述应低分", ml.score("下面我们讲几个概念") < 0.5f)
        assertTrue("含疑问词陈述应低分", ml.score("这个问题怎么处理后面讲") < 0.5f)

        // 规则 + 模型融合：ml=null 与 ml 注入都不应把自问自答/讲课误报为 L2
        val withRulesOnly = QuestionDetector(DetectSensitivity.NORMAL, null)
        val withMl = QuestionDetector(DetectSensitivity.NORMAL, ml)
        listOf("什么是梯度呢梯度就是一个向量", "这个问题怎么处理我们后面讲", "下面我们讲几个概念").forEach { s ->
            assertTrue("纯规则误报: $s", (withRulesOnly.detect(s)?.level ?: 0) < 2)
            assertTrue("融合后误报: $s", (withMl.detect(s)?.level ?: 0) < 2)
        }
        // 真问句在融合后稳定判 L2
        listOf("什么是偏导数", "如何证明这个定理", "结果等于多少").forEach { s ->
            assertEquals("漏报真提问: $s", 2, withMl.detect(s)?.level)
        }
    }
}
