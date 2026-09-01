package com.star.shuikebang.nlp

/**
 * 文本归一化：全半角、空白、标点统一。不做语义改写。
 */
object TextNorm {

    private val multiSpace = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = when (ch) {
                '？' -> '?'
                '！' -> '!'
                '，' -> ','
                '。' -> '.'
                '：' -> ':'
                '；' -> ';'
                '（' -> '('
                '）' -> ')'
                '【' -> '['
                '】' -> ']'
                '“', '”' -> '"'
                '‘', '’' -> '\''
                else -> ch
            }
            sb.append(c)
        }
        return sb.toString().replace(multiSpace, " ").trim()
    }

    /** 是否主要由 ASCII（英文/数字）构成，用于选择中英规则 */
    fun isMostlyEnglish(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val asciiLetters = text.count { it in 'A'..'z' }
        return asciiLetters.toDouble() / letters > 0.6
    }
}
