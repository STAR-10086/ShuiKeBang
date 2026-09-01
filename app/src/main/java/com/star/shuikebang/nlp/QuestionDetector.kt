package com.star.shuikebang.nlp

/**
 * 提问检测结果
 * @param level 1=可能被提问（点名/祈使预警） 2=问题回溯（确认问句）
 */
data class QuestionDetection(
    val level: Int,
    val hitKeyword: String?,
    val rawSentence: String,
    val coreQuestion: String,
)

/** 检测灵敏度；OFF 为完全关闭提问检测 */
enum class DetectSensitivity { HIGH, NORMAL, LOW, OFF }

/**
 * 轻量规则提问检测器：纯本地、无模型、无网络。
 *
 * 判定思路（宁少勿滥，默认 NORMAL）：
 *  1. 归一化 + 长度噪声过滤
 *  2. 收集信号：问号 / 句末"吗" / 强疑问结构 / 弱疑问词 / 讲课框架词 / 点名短语
 *  3. 按灵敏度组合：强信号直接 L2；弱信号需第二证据且未被讲课框架词否决；点名短语为 L1
 *  4. 核心问题提取：剥除课堂口头禅与称呼
 */
class QuestionDetector(
    private var sensitivity: DetectSensitivity = DetectSensitivity.NORMAL,
) {

    fun setSensitivity(s: DetectSensitivity) {
        sensitivity = s
    }

    fun detect(rawInput: String): QuestionDetection? {
        if (sensitivity == DetectSensitivity.OFF) return null
        val raw = TextNorm.normalize(rawInput)
        if (raw.isBlank()) return null
        val english = TextNorm.isMostlyEnglish(raw)
        if (tooShort(raw, english)) return null

        return if (english) detectEn(raw) else detectZh(raw)
    }

    // ---------------- 中文 ----------------

    private fun detectZh(raw: String): QuestionDetection? {
        val hasQuestionMark = raw.contains('?')
        val strong = QuestionRules.ZH_STRONG.firstOrNull { raw.contains(it) }
        val strongSoft = QuestionRules.ZH_STRONG_SOFT.firstOrNull { raw.contains(it) }
        val weak = QuestionRules.ZH_WEAK.firstOrNull { raw.contains(it) }
        val inLecture = QuestionRules.ZH_LECTURE.any { raw.contains(it) }
        val callStrong = QuestionRules.ZH_CALL_STRONG.firstOrNull { raw.contains(it) }
        val callWeak = QuestionRules.ZH_CALL_WEAK.firstOrNull { raw.contains(it) }

        val body = raw.trimEnd('.', '。', '!', '！', ',', '，', '?', ' ', '呢', '吧', '吗')
        val lastChar = raw.trimEnd('.', '。', '!', '！', ',', '，', ' ').lastOrNull()?.toString()
        val tailMa = lastChar == QuestionRules.ZH_TAIL_MA
        val tailSoft = lastChar != null && QuestionRules.ZH_TAIL_SOFT.contains(lastChar)
        // 旁证1：特定弱词（有没有/是不是…）位于句首（容许 1 个字发语词）
        val weakAtHead = weak != null &&
            weak in QuestionRules.ZH_WEAK_HEAD_ELIGIBLE && raw.indexOf(weak) in 0..1
        // 旁证2：弱疑问词落在句末（"结果等于多少""你选哪个"类）
        val weakAtTail = weak != null &&
            body.length - (raw.indexOf(weak) + weak.length) <= 3
        val weakCorroborated = !inLecture && weak != null &&
            (tailSoft || callStrong != null || weakAtHead || weakAtTail)

        val confirmed = when (sensitivity) {
            DetectSensitivity.HIGH ->
                hasQuestionMark || tailMa || strong != null || strongSoft != null ||
                    weak != null || callStrong != null
            DetectSensitivity.NORMAL ->
                hasQuestionMark || tailMa || strong != null || strongSoft != null || weakCorroborated
            DetectSensitivity.LOW ->
                hasQuestionMark || tailMa || strong != null
            DetectSensitivity.OFF -> false
        }

        val core = extractZhCore(raw)
        return when {
            confirmed -> QuestionDetection(
                level = 2,
                hitKeyword = strong ?: strongSoft ?: weak ?: callStrong ?: if (tailMa) "吗" else "?",
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
            // L1 点名预警：硬点名短语任意档可用；软祈使仅 HIGH
            callStrong != null -> l1(callStrong, raw, core)
            callWeak != null && sensitivity == DetectSensitivity.HIGH -> l1(callWeak, raw, core)
            else -> null
        }
    }

    private fun l1(hit: String, raw: String, core: String) = QuestionDetection(
        level = 1,
        hitKeyword = hit,
        rawSentence = raw,
        coreQuestion = core.ifBlank { raw },
    )

    private fun extractZhCore(raw: String): String {
        var s = raw.trim().trimEnd('?', '？', '.', '。', '!', '！', ',', '，')
        // 反复剥前缀
        var changed = true
        while (changed) {
            changed = false
            for (p in QuestionRules.ZH_PREFIX_STRIP) {
                val cleaned = p.trimEnd(',', '，')
                if (s.startsWith(cleaned)) {
                    s = s.removePrefix(cleaned).trimStart(' ', ',', '，', '、', ':').trim()
                    changed = true
                }
            }
        }
        // 剥后缀
        var ended = true
        while (ended) {
            ended = false
            for (suffix in QuestionRules.ZH_SUFFIX_STRIP) {
                if (s.endsWith(suffix) && s.length - suffix.length >= QuestionRules.ZH_MIN_LEN) {
                    s = s.removeSuffix(suffix).trimEnd(' ', ',', '，')
                    ended = true
                }
            }
        }
        return s.trim()
    }

    // ---------------- 英文 ----------------

    private fun detectEn(raw: String): QuestionDetection? {
        val lower = raw.lowercase().trim()
        val hasQuestionMark = raw.contains('?')
        val words = lower.split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        val firstWord = words.firstOrNull() ?: return null

        val whInside = QuestionRules.EN_WH.firstOrNull { words.contains(it) }
        val whAtStart = QuestionRules.EN_WH.contains(firstWord)
        val auxInversion = QuestionRules.EN_AUX.contains(firstWord)
        val inLecture = QuestionRules.EN_LECTURE.any { lower.contains(it) }
        val callStrong = QuestionRules.EN_CALL_STRONG.firstOrNull { lower.contains(it) }
        val callWeak = QuestionRules.EN_CALL_WEAK.firstOrNull { lower.contains(it) }

        val confirmed = when (sensitivity) {
            DetectSensitivity.HIGH ->
                hasQuestionMark || whInside != null || auxInversion || callStrong != null
            // NORMAL/LOW：句中 Wh 词但属于讲课陈述（this is what...）不判；无问号的句中 wh 不判
            DetectSensitivity.NORMAL, DetectSensitivity.LOW ->
                hasQuestionMark || whAtStart || auxInversion ||
                    (!inLecture && whInside != null && hasQuestionMark)
            DetectSensitivity.OFF -> false
        }

        val core = extractEnCore(raw)
        return when {
            confirmed -> QuestionDetection(
                level = 2,
                hitKeyword = if (whAtStart || whInside != null) whInside ?: firstWord
                else firstWord.takeIf { auxInversion } ?: callStrong,
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
            callStrong != null -> l1(callStrong, raw, core)
            callWeak != null && sensitivity == DetectSensitivity.HIGH -> l1(callWeak, raw, core)
            else -> null
        }
    }

    private fun extractEnCore(raw: String): String {
        var s = raw.trim().trimEnd('?', '.', '!', ',')
        val lower0 = s.lowercase()
        for (p in QuestionRules.EN_PREFIX_STRIP) {
            if (lower0.startsWith(p)) {
                s = s.substring(p.length).trimStart(' ', ',').replaceFirstChar { it.uppercase() }
                break
            }
        }
        for (suf in QuestionRules.EN_SUFFIX_STRIP) {
            if (s.lowercase().endsWith(suf)) s = s.dropLast(suf.length).trim()
        }
        return s.trim()
    }

    private fun tooShort(raw: String, english: Boolean): Boolean =
        if (english) raw.split(Regex("\\s+")).filter { it.isNotBlank() }.size < QuestionRules.EN_MIN_WORDS
        else raw.replace(Regex("[\\s\\p{Punct}]"), "").length < QuestionRules.ZH_MIN_LEN
}
