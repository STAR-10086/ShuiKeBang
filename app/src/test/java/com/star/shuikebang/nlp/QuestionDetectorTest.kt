package com.star.shuikebang.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDetectorTest {

    private val detector = QuestionDetector(DetectSensitivity.NORMAL)

    @Test
    fun zh_interrogative_with_mark_is_confirmed() {
        val r = detector.detect("什么是线性回归？")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_interrogative_without_mark_is_confirmed() {
        // 流式模型常无标点，疑问词也应命中
        val r = detector.detect("我们如何理解这个概念")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_call_pattern_is_suspect() {
        // 纯点名祈使句（不含疑问代词/问号）应为 L1 预警
        val r = detector.detect("你来回答一下这道题")
        assertNotNull(r)
        assertEquals(1, r!!.level)
        assertEquals("回答一下", r.hitKeyword)
    }

    @Test
    fun zh_question_pronoun_makes_confirmed() {
        // "哪位"本身是疑问代词，应判 L2 确认问题
        val r = detector.detect("哪位同学来回答一下这个问题")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_weak_particle_alone_not_question() {
        // "呢/嘛"单独出现不构成提问
        assertNull(detector.detect("我们继续往下讲呢"))
    }

    @Test
    fun zh_plain_lecture_is_not_question() {
        assertNull(detector.detect("我们今天讲第三章的内容"))
        assertNull(detector.detect("下面把公式推导一遍"))
    }

    @Test
    fun zh_short_noise_filtered() {
        assertNull(detector.detect("嗯嗯"))
        assertNull(detector.detect("好"))
    }

    @Test
    fun zh_core_question_stripped() {
        val r = detector.detect("那么请你说一下什么是梯度下降")
        assertNotNull(r)
        val core = r!!.coreQuestion
        assertTrue("核心问题应剥除口头语，实际=$core", !core.startsWith("请你") && !core.startsWith("那么"))
        assertTrue(core.contains("梯度下降"))
    }

    @Test
    fun en_wh_question_confirmed() {
        val r = detector.detect("What is gradient descent")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun en_aux_inversion_confirmed() {
        val r = detector.detect("Can you explain the central limit theorem?")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun en_plain_sentence_not_question() {
        assertNull(detector.detect("we will review the chapter next week"))
    }

    @Test
    fun dedup_input_normalized() {
        val r = detector.detect("什么是矩阵的秩？？") // 全角问号归一
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }
}
