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
import com.star.shuikebang.util.TimeFmt

/**
 * L3：录音前台服务常驻通知（Android 14+ 系统自带麦克风指示），
 * 同时是所有厂商岛不可用时的最终兜底。
 */
class FgsNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun build(durationSec: Int): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_RECORD)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("正在记录课堂 · ${TimeFmt.duration(durationSec)}")
            .setContentText("本地离线识别中，音频不会上传")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent())
            .build()
    }

    fun refresh(durationSec: Int) {
        runCatching { nm.notify(NOTI_FGS_ID, build(durationSec)) }
    }

    fun cancel() = runCatching { nm.cancel(NOTI_FGS_ID) }

    companion object {
        const val CHANNEL_RECORD = "record_fgs"
        const val NOTI_FGS_ID = 1001
    }
}
