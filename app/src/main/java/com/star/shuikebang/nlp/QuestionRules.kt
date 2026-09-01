package com.star.shuikebang.nlp

/**
 * 提问检测规则表（轻量、可迭代）。
 * 规则只覆盖课堂常见句式，宁少勿滥；命中后由 QuestionDetector 组合判定。
 */
object QuestionRules {

    // ---- 中文：强疑问信号（疑问代词/结构） ----
    val ZH_INTERROGATORS = listOf(
        "什么", "谁", "哪", "怎么", "怎样", "咋", "如何", "为什么", "为啥", "为何",
        "多少", "几个", "几点", "是否", "能否", "能不能", "可不可以", "可不可以",
        "是不是", "有没有", "对不对", "好不好", "区别", "什么样", "哪一",
    )

    // ---- 中文：句末疑问语气词（纯字符串，按句末字精确匹配，勿写成正则） ----
    // "吗"几乎只用于疑问句，可独立成证；"呢/嘛"过弱（"我在上课呢"），不单独成证
    val ZH_TAIL_STRONG = listOf("吗")
    val ZH_TAIL_WEAK = listOf("呢", "嘛")

    // ---- 中文：点名/祈使提问短语（L1 预警） ----
    val ZH_CALL_PATTERNS = listOf(
        "回答一下", "回答下", "来回答", "请回答", "说一下", "说一说", "讲一下", "讲一讲",
        "解释一下", "解释下", "介绍一下", "谈谈", "谈一谈", "复述", "描述一下",
        "哪位同学", "哪个同学", "这位同学", "这名同学", "那位同学", "谁来", "谁能",
        "谁说", "你来", "我点个同学", "点个同学", "随机点", "举手回答", "给大家讲",
        "给大家说", "起来说", "站起来说",
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

    // ---- 英文：祈使提问短语 ----
    val EN_CALL_PATTERNS = listOf(
        "explain", "describe", "tell me", "who can", "anyone", "anybody",
        "could you", "can you", "would you", "please answer", "answer the question",
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
