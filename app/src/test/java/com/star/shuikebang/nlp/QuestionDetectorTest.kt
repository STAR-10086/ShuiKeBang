package com.star.shuikebang.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDetectorTest {

    private val detector = QuestionDetector(DetectSensitivity.NORMAL)

    // ---------- 原有正向用例 ----------

    @Test
    fun zh_interrogative_with_mark_is_confirmed() {
        val r = detector.detect("什么是线性回归？")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_interrogative_without_mark_is_confirmed() {
        // 流式模型常无标点，"如何"为次强信号，NORMAL 应命中
        val r = detector.detect("我们如何理解这个概念")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_call_pattern_is_suspect() {
        val r = detector.detect("你来回答一下这道题")
        assertNotNull(r)
        assertEquals(1, r!!.level)
        assertEquals("回答一下", r.hitKeyword)
    }

    @Test
    fun zh_question_pronoun_makes_confirmed() {
        val r = detector.detect("哪位同学来回答一下这个问题")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    @Test
    fun zh_weak_particle_alone_not_question() {
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
        val r = detector.detect("什么是矩阵的秩？？")
        assertNotNull(r)
        assertEquals(2, r!!.level)
    }

    // ---------- 新增：讲课陈述句不得误报（NORMAL） ----------

    @Test
    fun zh_lecture_statements_not_flagged() {
        val lectures = listOf(
            "它们的区别在于适用场景不同",
            "下面我们讲几个基本概念",
            "这部分多少会涉及一点内容",
            "谁都知道这个结论",
            "我们是不是之前讲过这个点",
            "这个公式接下来会详细推导",
            "也就是说它的核心是连续性",
            "整个过程分为三个步骤",
            "我来给大家演示一遍操作",
            "这一章主要有四个重点",
            "上节课我们学过极限的定义",
            "大家可以看到曲线在这里上升",
        )
        for (s in lectures) {
            val r = detector.detect(s)
            assertTrue("讲课句被误判为提问: $s -> $r", r == null)
        }
    }

    @Test
    fun en_lecture_statement_not_flagged() {
        // 句中 what 但属于 "this is what we call" 讲句式，无问号不得判
        assertNull(detector.detect("this is what we call a derivative"))
        assertNull(detector.detect("that is why we need this assumption"))
    }

    // ---------- 新增：弱词需要旁证 ----------

    @Test
    fun zh_weak_word_needs_corroboration() {
        // 句中弱词、无旁证、非句首句末 -> 不判
        assertNull(detector.detect("这个公式怎么推导我们后面说"))
        // 弱词 + 句末语气词"呢" -> L2
        assertEquals(2, detector.detect("这个问题该怎么解决呢")!!.level)
        // 弱词落在句末 -> L2
        assertEquals(2, detector.detect("最后的结果等于多少")!!.level)
        // 句首"有没有" -> L2
        assertEquals(2, detector.detect("有没有同学知道答案")!!.level)
    }

    @Test
    fun zh_strong_call_is_l1() {
        val r = detector.detect("你来给大家算一下这一步")
        assertNotNull(r)
        assertEquals(1, r!!.level)
    }

    // ---------- 灵敏度分档 ----------

    @Test
    fun sensitivity_low_more_conservative() {
        val low = QuestionDetector(DetectSensitivity.LOW)
        // "如何"为次强，LOW 不判
        assertNull(low.detect("我们如何理解这个概念"))
        // 核心强信号 LOW 仍判
        assertEquals(2, low.detect("什么是导数")!!.level)
        // 硬点名 LOW 仍 L1
        assertEquals(1, low.detect("你来回答一下")!!.level)
    }

    @Test
    fun sensitivity_high_more_sensitive() {
        val high = QuestionDetector(DetectSensitivity.HIGH)
        // 软祈使"解释一下"仅 HIGH 判 L1
        assertEquals(1, high.detect("解释一下这个公式")!!.level)
        // NORMAL 下软祈使不判
        assertNull(detector.detect("解释一下这个公式"))
    }

    @Test
    fun sensitivity_off_detects_nothing() {
        val off = QuestionDetector(DetectSensitivity.OFF)
        assertNull(off.detect("什么是线性回归？"))
        assertNull(off.detect("哪位同学来回答一下"))
    }
}
