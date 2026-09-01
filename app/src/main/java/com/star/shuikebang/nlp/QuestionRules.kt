package com.star.shuikebang.nlp

/**
 * 提问检测规则表（轻量、可迭代，禁止引入大模型）。
 *
 * 信号分级（核心是降低讲课陈述句的误报）：
 *  - [ZH_STRONG]   强疑问结构：命中基本可确认是提问，任意灵敏度都判 L2
 *  - [ZH_WEAK]     弱疑问词：讲课中也常出现，单独不判，需第二证据（句末语气/点名短语/位于句首）
 *  - [ZH_LECTURE]  讲课框架词：命中后弱疑问词失效（强信号不受影响）
 *  - [ZH_CALL_STRONG] / [ZH_CALL_WEAK]：点名祈使，分硬/软两档，对应 L1 预警
 */
object QuestionRules {

    // ---- 中文：核心强疑问结构（最硬，LOW/NORMAL/HIGH 均判 L2） ----
    val ZH_STRONG = listOf(
        "什么是", "什么叫", "叫什么", "是什么", "为什么", "为何", "为啥",
        "怎么会", "怎么办", "怎样",
        "是谁", "哪位", "哪里", "哪儿", "哪个", "哪些", "哪一",
        "什么样", "几点", "多大",
    )

    // ---- 中文：次强结构（NORMAL/HIGH 判 L2，LOW 不判；"如何"也常用于讲解引导） ----
    val ZH_STRONG_SOFT = listOf("如何")

    // ---- 中文：弱疑问词（单独不判，需第二证据；讲课陈述高频，务必保守） ----
    val ZH_WEAK = listOf(
        "什么", "怎么", "谁", "哪", "多少", "几个",
        "是否", "能否", "能不能", "可不可以", "是不是", "有没有",
        "对不对", "好不好", "区别",
    )

    // ---- 弱词中只有这些"位于句首"才像面向全班提问（其余弱词不靠句首成证） ----
    val ZH_WEAK_HEAD_ELIGIBLE = setOf(
        "是否", "能否", "能不能", "可不可以", "是不是", "有没有",
    )

    // ---- 中文：句末强语气词（按句末字精确匹配，勿写成正则，"?"会被当正则量词） ----
    const val ZH_TAIL_MA = "吗"

    // ---- 中文：句末弱语气词（弱疑问词的第二证据） ----
    val ZH_TAIL_SOFT = listOf("呢", "吧")

    // ---- 中文：讲课/陈述框架词，命中则弱疑问词不成立（不影响强信号） ----
    val ZH_LECTURE = listOf(
        "也就是说", "这就是", "所谓", "指的是", "是什么意思",
        "我们今天", "下面我们", "下面", "接下来", "上节课", "本节课", "这一节", "本章", "这章",
        "区别在于", "分为", "分别是", "主要有", "包括以下",
        "大家可以", "我们知道", "都知道", "换句话说", "简单来说", "总的来说",
        "我来", "给大家", "我们先", "我们再", "刚才讲", "前面讲", "之前讲",
        "如图", "可以看到", "注意看",
    )

    // ---- 中文：硬点名/祈使短语（明确要求学生作答，NORMAL 即判 L1） ----
    val ZH_CALL_STRONG = listOf(
        "回答一下", "回答下", "来回答", "请回答", "你来回答", "起来回答", "举手回答",
        "哪位同学", "哪个同学", "谁来", "谁能", "谁说", "你来", "你来说",
        "站起来说", "起来说", "我点个同学", "点个同学", "随机点",
    )

    // ---- 中文：软祈使（老师也常对自己说，仅 HIGH 档判 L1） ----
    val ZH_CALL_WEAK = listOf(
        "说一下", "说一说", "讲一下", "讲一讲", "解释一下", "解释下",
        "介绍一下", "谈谈", "谈一谈", "复述", "描述一下", "给大家讲", "给大家说",
    )

    // ---- 中文：剥除的前缀（课堂口头禅/称呼） ----
    val ZH_PREFIX_STRIP = listOf(
        "那么", "那个", "来,", "来,", "好,", "好", "嗯", "呃", "这个", "就是说",
        "请你", "请", "你", "这位同学", "这名同学", "那位同学", "哪位同学来",
        "哪位同学", "谁来", "你来", "我想让你", "大家想一想,", "大家想想,",
    )

    // ---- 中文：剥除的后缀 ----
    val ZH_SUFFIX_STRIP = listOf(
        "一下", "好不好", "好吗", "行吗", "行不", "啊", "呀", "呢", "吧", "嘛", "哈",
    )

    // ---- 英文：Wh- 疑问词 ----
    val EN_WH = listOf(
        "what", "why", "how", "when", "where", "which", "who", "whom", "whose",
    )

    // ---- 英文：句首助动词倒装 ----
    val EN_AUX = listOf(
        "do", "does", "did", "is", "are", "was", "were", "am", "can", "could",
        "would", "should", "shall", "will", "may", "might", "must", "have", "has", "had",
    )

    // ---- 英文：硬点名/提问短语（NORMAL 即 L1） ----
    val EN_CALL_STRONG = listOf(
        "who can", "anyone", "anybody", "could you", "can you", "would you",
        "please answer", "answer the question",
    )

    // ---- 英文：软祈使（仅 HIGH 判 L1） ----
    val EN_CALL_WEAK = listOf("explain", "describe", "tell me")

    // ---- 英文：陈述框架，句中含 Wh 词但属于讲课陈述时否决 ----
    val EN_LECTURE = listOf(
        "this is what", "that is what", "we know what", "we will", "i will",
        "let us", "let's", "as we", "which means", "that is why",
    )

    val EN_PREFIX_STRIP = listOf(
        "please", "ok", "okay", "well", "now", "so", "i want you to",
        "could you", "would you", "can you", "can anyone",
    )

    val EN_SUFFIX_STRIP = listOf("please", "ok", "okay")

    // 最短长度阈值（低于则视为噪声，不提醒）
    const val ZH_MIN_LEN = 4
    const val EN_MIN_WORDS = 3
}
