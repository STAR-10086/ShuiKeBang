package com.star.shuikebang.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFmt {

    private val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val md = SimpleDateFormat("M月d日", Locale.CHINA)
    private val full = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)

    /** 转录行时间戳 [HH:mm:ss] */
    fun stamp(ts: Long): String {
        val t = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ts))
        return "[$t]"
    }

    fun hm(ts: Long): String = hm.format(Date(ts))

    /** 计时器 mm:ss / h:mm:ss */
    fun duration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** 历史列表的友好日期：今天/昨天/M月d日 */
    fun friendlyDay(ts: Long, now: Long = System.currentTimeMillis()): String {
        fun startOfDay(t: Long): Long {
            val c = Calendar.getInstance().apply {
                timeInMillis = t
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
        val today = startOfDay(now)
        val that = startOfDay(ts)
        val diffDays = ((today - that) / 86_400_000L).toInt()
        return when (diffDays) {
            0 -> "今天"
            1 -> "昨天"
            in 2..6 -> "${diffDays}天前"
            else -> md.format(Date(ts))
        }
    }

    fun autoSessionTitle(startTs: Long): String = "${full.format(Date(startTs))} 课堂记录"
}
