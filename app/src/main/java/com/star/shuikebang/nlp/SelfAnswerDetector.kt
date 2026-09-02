package com.star.shuikebang.nlp

/**
 * 跨句「自问自答」判定（纯本地、无模型、无网络）。
 *
 * [QuestionDetector] 只能识别“同一句内”疑问结构后紧跟解答词（如“什么是递归，就是…”）。
 * 真实课堂更常见的是断成两句：上一句“什么是递归？”被识别成提问，紧接着下一句
 * “递归就是函数调用自身”是老师自己在讲。本类判断 [followUp] 是否构成对 [question] 的自答，
 * 供延迟确认门决定是否撤销上一条提问提醒。
 *
 * 判定刻意保守：只有“解答引导词”且（强引导词，或与问题主题词重合）才判自答，
 * 后续句若又在提问 / 点名学生，则绝不是自答。宁可保留一次提醒，也不漏掉真正的课堂提问。
 */
object SelfAnswerDetector {

    /** 强解答引导：老师直接公布答案/结论，命中即视为自答，无需主题重合 */
    private val ZH_STRONG_CUE = listOf(
        "答案是", "正确答案", "应该选", "选的是", "答案选", "结果是", "结果为", "得出",
    )

    /** 弱解答引导：需与问题主题词重合才算自答 */
    private val ZH_WEAK_CUE =
        QuestionRules.ZH_ANSWER_CUE + listOf("等于", "记作", "记为", "化简为", "化简得", "求得")

    /** 从问题中剔除的疑问结构 / 停用词，长词在前，先删长的再删单字 */
    private val ZH_Q_REMOVE = listOf(
        "什么是", "什么叫", "是什么", "为什么", "怎么办", "怎么会", "怎么样", "什么样",
        "是谁", "哪里", "哪儿", "哪个", "哪些", "哪一", "几点", "为何", "为啥", "怎样",
        "能不能", "可不可以", "是不是", "有没有", "对不对", "好不好", "是否", "能否",
        "什么", "怎么", "如何", "谁", "哪", "多少", "几个",
        "请问", "同学", "一下", "这个", "那个", "我们", "你们", "他们",
        "吗", "呢", "吧", "啊", "呀", "嘛", "的", "了", "在", "和", "与", "是",
        "这", "那", "我", "你", "他", "她", "它", "们", "请", "问", "有", "个",
        "把", "被", "让", "使", "对", "为", "及", "说", "讲",
    )

    private val EN_STRONG_CUE = listOf("the answer is", "answer is", "correct answer")
    private val EN_WEAK_CUE = listOf("it is", "this is", "that is", "we get", "which equals", "equals")
    private val EN_STOP = setOf(
        "what", "why", "how", "when", "where", "which", "who", "whom", "whose",
        "is", "are", "was", "were", "do", "does", "did", "the", "a", "an", "to", "of",
        "in", "on", "for", "and", "or", "we", "you", "i", "it", "this", "that",
    )

    private const val RELATED_THRESHOLD = 0.34
    private val SEP = Regex("[\\s，。、,.!?！？：:；;\"'“”‘’（）()\\-—…]+")

    fun isSelfAnswer(question: String, followUp: String): Boolean {
        val q = clean(question)
        val f = clean(followUp)
        if (q.isEmpty() || f.isEmpty()) return false
        return if (TextNorm.isMostlyEnglish(q)) isEn(q, f) else isZh(q, f)
    }

    // ---------------- 中文 ----------------

    private fun isZh(q: String, f: String): Boolean {
        // 后续句又在提问 / 点名学生作答 → 不是自答
        if (looksLikeZhQuestionOrCalling(f)) return false
        val head = f.take(10)
        if (ZH_STRONG_CUE.any { head.contains(it) }) return true
        val weakHit = ZH_WEAK_CUE.any { head.contains(it) }
        if (!weakHit) return false
        return topicOverlap(q, f) >= RELATED_THRESHOLD
    }

    private fun looksLikeZhQuestionOrCalling(f: String): Boolean {
        if (f.contains('?') || f.contains('？') || f.endsWith("吗") || f.endsWith("呢")) return true
        if (QuestionRules.ZH_CALL_STRONG.any { f.contains(it) }) return true
        if (QuestionRules.ZH_STRONG.any { f.contains(it) }) return true
        if (QuestionRules.ZH_STRONG_SOFT.any { f.contains(it) }) return true
        return false
    }

    /** 问题主题 bigram 被后续句覆盖的比例 */
    private fun topicOverlap(q: String, f: String): Double {
        val qTopic = topicString(q)
        val qBg = bigrams(qTopic)
        if (qBg.isEmpty()) return 0.0
        val fBg = bigrams(keepChars(f))
        val hit = qBg.count { it in fBg }
        return hit.toDouble() / qBg.size
    }

    private fun topicString(q: String): String {
        var s = q
        for (token in ZH_Q_REMOVE) s = s.replace(token, "")
        return keepChars(s)
    }

    private fun keepChars(s: String): String =
        s.filter { it in '\u4e00'..'\u9fff' || it in 'a'..'z' || it in 'A'..'Z' }

    private fun bigrams(s: String): Set<String> {
        if (s.length < 2) return emptySet()
        val out = HashSet<String>(s.length)
        for (i in 0 until s.length - 1) out.add(s.substring(i, i + 2))
        return out
    }

    // ---------------- 英文 ----------------

    private fun isEn(q: String, f: String): Boolean {
        if (f.contains('?')) return false
        val fWords = f.split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        val first = fWords.firstOrNull() ?: return false
        if (first in QuestionRules.EN_WH || first in QuestionRules.EN_AUX) return false
        val head = f.take(16)
        if (EN_STRONG_CUE.any { head.contains(it) }) return true
        val weakHit = EN_WEAK_CUE.any { head.contains(it) }
        if (!weakHit) return false
        val qContent = q.lowercase().split(Regex("[^a-z']+"))
            .filter { it.length > 3 && it !in EN_STOP }.toSet()
        return qContent.any { fWords.contains(it) }
    }

    private fun clean(s: String): String =
        SEP.replace(s.lowercase().trim(), " ").replace(Regex("\\s+"), " ").trim()
}
