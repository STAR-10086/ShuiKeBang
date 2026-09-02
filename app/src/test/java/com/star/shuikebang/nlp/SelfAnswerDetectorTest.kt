package com.star.shuikebang.nlp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAnswerDetectorTest {

    // ---------- 应判为自问自答（撤销提问提醒） ----------

    @Test
    fun zh_definition_followup_is_self_answer() {
        assertTrue(SelfAnswerDetector.isSelfAnswer("什么是递归", "递归就是函数调用自身的过程"))
        assertTrue(SelfAnswerDetector.isSelfAnswer("什么叫闭包", "闭包指的是能够访问外部变量的函数"))
    }

    @Test
    fun zh_strong_cue_needs_no_topic_overlap() {
        // 老师直接公布答案，即使换了词也判自答
        assertTrue(SelfAnswerDetector.isSelfAnswer("这道题选什么", "答案是C"))
        assertTrue(SelfAnswerDetector.isSelfAnswer("结果等于多少", "结果为四十二"))
    }

    @Test
    fun en_weak_cue_with_shared_topic_is_self_answer() {
        assertTrue(SelfAnswerDetector.isSelfAnswer("what is recursion", "this is recursion in its essence"))
        assertTrue(SelfAnswerDetector.isSelfAnswer("which option is correct", "the answer is option b"))
    }

    // ---------- 不应判自答（保留真提问提醒） ----------

    @Test
    fun followup_calling_student_is_not_self_answer() {
        assertFalse(SelfAnswerDetector.isSelfAnswer("什么是递归", "好，谁来回答一下"))
        assertFalse(SelfAnswerDetector.isSelfAnswer("这道题怎么做", "那位同学你来说"))
    }

    @Test
    fun followup_is_another_question() {
        assertFalse(SelfAnswerDetector.isSelfAnswer("什么是递归", "那它和循环有什么区别呢"))
        assertFalse(SelfAnswerDetector.isSelfAnswer("为什么会这样", "哪位同学知道原因吗"))
    }

    @Test
    fun unrelated_statement_keeps_question() {
        // 学生式回答 / 无解答引导词，不撤销
        assertFalse(SelfAnswerDetector.isSelfAnswer("为什么天空是蓝的", "因为瑞利散射"))
        // 有弱引导词但与问题主题无关，不撤销
        assertFalse(SelfAnswerDetector.isSelfAnswer("什么是递归", "这是我们上节课的重点"))
    }

    @Test
    fun blank_inputs_are_safe() {
        assertFalse(SelfAnswerDetector.isSelfAnswer("", "随便说点什么"))
        assertFalse(SelfAnswerDetector.isSelfAnswer("什么是递归", "  "))
    }
}
