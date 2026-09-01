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

/** 检测灵敏度 */
enum class DetectSensitivity { HIGH, NORMAL, LOW }

/**
 * 轻量规则提问检测器：纯本地、无模型、无网络。
 *
 * 判定顺序：
 *  1. 归一化 + 长度噪声过滤
 *  2. L2 确认：疑问代词/疑问助词/问号（中文）；Wh-/助动词倒装/?（英文）
 *  3. L1 预警：点名、祈使回答短语
 *  4. 核心问题提取：剥除课堂口头禅与称呼
 */
class QuestionDetector(
    private var sensitivity: DetectSensitivity = DetectSensitivity.NORMAL,
) {

    fun setSensitivity(s: DetectSensitivity) {
        sensitivity = s
    }

    fun detect(rawInput: String): QuestionDetection? {
        val raw = TextNorm.normalize(rawInput)
        if (raw.isBlank()) return null
        val english = TextNorm.isMostlyEnglish(raw)
        if (tooShort(raw, english)) return null

        return if (english) detectEn(raw) else detectZh(raw)
    }

    // ---------------- 中文 ----------------

    private fun detectZh(raw: String): QuestionDetection? {
        val hasQuestionMark = raw.contains('?')
        val hitInterrogator = QuestionRules.ZH_INTERROGATORS.firstOrNull { raw.contains(it) }
        val lastChar = raw.trimEnd('.', '。', '!', '！', ',', '，', ' ').lastOrNull()?.toString()
        val hitTail = when {
            hasQuestionMark -> "?"
            QuestionRules.ZH_TAIL_STRONG.any { it == lastChar } -> "吗"
            else -> null
        }
        val hitCall = QuestionRules.ZH_CALL_PATTERNS.firstOrNull { raw.contains(it) }

        val confirmed = when (sensitivity) {
            DetectSensitivity.HIGH -> hasQuestionMark || hitInterrogator != null || hitTail != null || hitCall != null
            DetectSensitivity.NORMAL -> hasQuestionMark || hitInterrogator != null || hitTail != null
            DetectSensitivity.LOW -> hasQuestionMark || (hitInterrogator != null && hitTail != null)
        }

        val core = extractZhCore(raw)
        return when {
            confirmed -> QuestionDetection(
                level = 2,
                hitKeyword = hitInterrogator ?: hitCall ?: hitTail,
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
            hitCall != null && sensitivity != DetectSensitivity.LOW -> QuestionDetection(
                level = 1,
                hitKeyword = hitCall,
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
            else -> null
        }
    }

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

        val hitWh = QuestionRules.EN_WH.firstOrNull { words.contains(it) }
        val auxInversion = QuestionRules.EN_AUX.contains(firstWord)
        val hitCall = QuestionRules.EN_CALL_PATTERNS.firstOrNull { lower.contains(it) }

        val confirmed = when (sensitivity) {
            DetectSensitivity.HIGH -> hasQuestionMark || hitWh != null || auxInversion || hitCall != null
            DetectSensitivity.NORMAL -> hasQuestionMark || hitWh != null || auxInversion
            DetectSensitivity.LOW -> hasQuestionMark && (hitWh != null || auxInversion)
        }

        val core = extractEnCore(raw)
        return when {
            confirmed -> QuestionDetection(
                level = 2,
                hitKeyword = hitWh ?: firstWord.takeIf { auxInversion } ?: hitCall,
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
            hitCall != null && sensitivity != DetectSensitivity.LOW -> QuestionDetection(
                level = 1,
                hitKeyword = hitCall,
                rawSentence = raw,
                coreQuestion = core.ifBlank { raw },
            )
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
