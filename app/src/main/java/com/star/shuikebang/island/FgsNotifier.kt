package com.star.shuikebang.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.star.shuikebang.MainActivity
import com.star.shuikebang.R
import com.star.shuikebang.service.RecordService
import com.star.shuikebang.util.TimeFmt

/**
 * L3：录音前台服务常驻通知（Android 14+ 系统自带麦克风指示），
 * 同时是所有厂商岛不可用时的最终兜底。
 *
 * 可交互：通知内直接「暂停/继续」「结束并保存」，展开可看到最近一条老师提问；
 * 操作通过 PendingIntent.getForegroundService 回送 [RecordService] 的 action。
 */
class FgsNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 最近一条提问，刷新计时时保留展示 */
    @Volatile
    private var latestQuestion: String? = null

    fun ensureChannel() {
        val ch = NotificationChannel(
            CHANNEL_RECORD,
            context.getString(R.string.record_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.record_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun contentIntent(): PendingIntent {
        // CWE-927：显式 class + setPackage 锁定本应用；不用 apply{}，避免静态分析漏判显式性
        val intent = Intent(context, MainActivity::class.java)
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun controlIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecordService::class.java)
        intent.setPackage(context.packageName) // CWE-927：显式锁定本应用
        intent.action = action
        return PendingIntent.getForegroundService(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun build(durationSec: Int, paused: Boolean = false, question: String? = latestQuestion): Notification {
        ensureChannel()
        val time = TimeFmt.duration(durationSec)
        val title = if (paused) "已暂停 · $time" else "正在记录课堂 · $time"
        val body = when {
            paused && !question.isNullOrBlank() -> "已暂停 · 最近提问：$question"
            paused -> "已暂停，点「继续」恢复识别"
            !question.isNullOrBlank() -> "提问：$question"
            else -> "本地离线识别中，音频不会上传"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RECORD)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent())

        if (paused) {
            builder.addAction(
                R.drawable.ic_ntf_play, "继续",
                controlIntent(RecordService.ACTION_RESUME, RC_RESUME),
            )
        } else {
            builder.addAction(
                R.drawable.ic_ntf_pause, "暂停",
                controlIntent(RecordService.ACTION_PAUSE, RC_PAUSE),
            )
        }
        builder.addAction(
            R.drawable.ic_ntf_stop, "结束并保存",
            controlIntent(RecordService.ACTION_STOP, RC_STOP),
        )
        return builder.build()
    }

    /** @param question 传入新提问时更新；传 null 则保留上一条 */
    fun refresh(durationSec: Int, paused: Boolean = false, question: String? = null) {
        if (question != null) latestQuestion = question
        runCatching { nm.notify(NOTI_FGS_ID, build(durationSec, paused, latestQuestion)) }
    }

    fun clearQuestion() { latestQuestion = null }

    fun cancel() = runCatching { nm.cancel(NOTI_FGS_ID) }

    companion object {
        const val CHANNEL_RECORD = "record_fgs"
        const val NOTI_FGS_ID = 1001
        private const val RC_PAUSE = 11
        private const val RC_RESUME = 12
        private const val RC_STOP = 13
    }
}
