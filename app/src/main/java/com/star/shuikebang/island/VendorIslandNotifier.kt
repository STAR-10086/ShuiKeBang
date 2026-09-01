package com.star.shuikebang.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.star.shuikebang.MainActivity
import com.star.shuikebang.R
import org.json.JSONObject

/**
 * L1：厂商原生「灵动岛」——全部走本地 NotificationManager.notify，
 * 不集成 MiPush / VPush，不需要服务器。
 *
 * 两家都需要应用上架后向厂商邮件申请展示权限；未授权时调用静默失败，
 * 由 [StatusIsland] 自动降级到 L2 悬浮胶囊 / L3 前台通知。
 */
class VendorIslandNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val vendor: Vendor = detectVendor()

    enum class Vendor { XIAOMI, VIVO, OTHER }

    // ---------------- 能力检测 ----------------

    private fun detectVendor(): Vendor {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || Brand.isMiHyperOs -> Vendor.XIAOMI
            m.contains("vivo") || m.contains("iqoo") -> Vendor.VIVO
            else -> Vendor.OTHER
        }
    }

    /** 小米：是否 HyperOS3（焦点通知协议版本==3） */
    private val isHyperOs3: Boolean
        get() = try {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) == 3
        } catch (t: Throwable) {
            false
        }

    /** 小米：查询本应用是否已获焦点通知展示权限 */
    fun miCanShowFocus(): Boolean {
        if (vendor != Vendor.XIAOMI || !isHyperOs3) return false
        return try {
            val arg = Bundle().apply { putString("package", context.packageName) }
            val res = context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"),
                "canShowFocus", null, arg,
            )
            res?.getBoolean("canShowFocus", false) == true
        } catch (t: Throwable) {
            Log.i(TAG, "canShowFocus query failed: ${t.message}")
            false
        }
    }

    fun isSupported(): Boolean = when (vendor) {
        Vendor.XIAOMI -> isHyperOs3 // 权限运行时再查，不支持机型直接 false
        Vendor.VIVO -> true         // vivo 无公开能力查询，尝试发送即可
        Vendor.OTHER -> false
    }

    // ---------------- 发送/更新/结束 ----------------

    private fun tapIntent(questionId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_QUESTION_ID, questionId)
        }
        return PendingIntent.getActivity(
            context, questionId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 小岛常驻：录音计时 */
    fun showCapsule(durationSec: Int, title: String) {
        runCatching {
            when (vendor) {
                Vendor.XIAOMI -> miNotify(buildMiCapsule(durationSec, title), update = false)
                Vendor.VIVO -> vivoNotifyCapsule(durationSec, title, operation = 0)
                Vendor.OTHER -> Unit
            }
        }.onFailure { Log.i(TAG, "showCapsule fail: ${it.message}") }
    }

    fun updateCapsule(durationSec: Int, title: String) {
        runCatching {
            when (vendor) {
                Vendor.XIAOMI -> miNotify(buildMiCapsule(durationSec, title), update = true)
                Vendor.VIVO -> vivoNotifyCapsule(durationSec, title, operation = 1)
                Vendor.OTHER -> Unit
            }
        }
    }

    /** 大岛展开：检测到老师提问 */
    fun showQuestion(questionId: Long, short: String, full: String) {
        runCatching {
            when (vendor) {
                Vendor.XIAOMI -> miNotify(buildMiQuestion(questionId, short, full), update = false)
                Vendor.VIVO -> vivoNotifyQuestion(questionId, short, full, operation = 0)
                Vendor.OTHER -> Unit
            }
        }.onFailure { Log.i(TAG, "showQuestion fail: ${it.message}") }
    }

    fun finish() {
        runCatching {
            when (vendor) {
                Vendor.VIVO -> vivoEnd()
                else -> nm.cancel(ISLAND_NOTI_ID)
            }
        }
    }

    // ---------------- 小米焦点通知（HyperOS 超级岛） ----------------

    private fun baseBuilder(): NotificationCompat.Builder {
        FgsNotifier(context).ensureChannel()
        return NotificationCompat.Builder(context, FgsNotifier.CHANNEL_RECORD)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private fun buildMiCapsule(durationSec: Int, title: String): Notification {
        // 字段以小米 HyperOS3 焦点通知文档（pId=2146）与社区逆向为准，需真机校准
        val param = JSONObject().apply {
            put("template", "information")
            put("island", JSONObject().apply {
                put("timeout", -1)            // 小岛常驻（-1 走默认）
                put("islandTimeout", 5)
            })
            put("information", JSONObject().apply {
                put("state", 1)
                put("builder", JSONObject().apply {
                    put("text1", title)
                    put("text2", "本地识别中 · ${com.star.shuikebang.util.TimeFmt.duration(durationSec)}")
                })
            })
        }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText("本地识别中")
            .addExtrasBundle().apply {
                extras.putString(MI_FOCUS_PARAM, param.toString())
            }.build()
    }

    private fun buildMiQuestion(questionId: Long, short: String, full: String): Notification {
        val param = JSONObject().apply {
            put("template", "information")
            put("island", JSONObject().apply {
                put("timeout", 1)             // 大岛停留 1 分钟
                put("islandTimeout", 5)
            })
            put("information", JSONObject().apply {
                put("state", 0)
                put("builder", JSONObject().apply {
                    put("text1", "检测到老师提问")
                    put("text2", short)
                })
            })
        }
        return baseBuilder()
            .setContentTitle("检测到老师提问")
            .setContentText(full)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(tapIntent(questionId))
            .apply { extras.putString(MI_FOCUS_PARAM, param.toString()) }
            .build()
    }

    private fun miNotify(n: Notification, update: Boolean) {
        if (!miCanShowFocus()) return // 未授权直接放弃，交由降级链路
        // 同一 notificationId 重复 notify 即平滑刷新
        nm.notify(ISLAND_NOTI_ID, n)
    }

    // ---------------- vivo 原子通知（本地 extras） ----------------

    private fun vivoBaseExtras(operation: Int): Bundle = Bundle().apply {
        putInt("notification.superx.operation", operation) // 0 创建 1 更新 2 结束
        putInt("notification.superx.template", 4)          // 4 基础模板
        putString("notification.superx.scene", "METTING")  // 课堂最接近会议场景，申请时确认
    }

    private fun vivoNotifyCapsule(durationSec: Int, title: String, operation: Int) {
        val capsule = Bundle().apply {
            putString("state", "running")
            putString("content", "记录中 ${com.star.shuikebang.util.TimeFmt.duration(durationSec)}")
            putInt("showTime", -1)
        }
        val extras = vivoBaseExtras(operation).apply {
            putBundle("notification.superx.capsule", capsule)
        }
        val n = baseBuilder().setContentTitle(title).setContentText("记录中").build().apply {
            this.extras.putAll(extras)
        }
        nm.notify(ISLAND_NOTI_ID, n)
    }

    private fun vivoNotifyQuestion(questionId: Long, short: String, full: String, operation: Int) {
        val infos = Bundle().apply {
            putString("title", "检测到老师提问")
            putString("subTitle", short)
            putString("content", full)
        }
        val island = Bundle().apply {
            putString("leftTitle", "老师提问")
            putString("rightTitle", short)
        }
        val extras = vivoBaseExtras(operation).apply {
            putBundle("notification.superx.infos", infos)
            putBundle("notification.superx.island", island)
        }
        val n = baseBuilder()
            .setContentTitle("检测到老师提问")
            .setContentText(full)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(tapIntent(questionId))
            .build().apply { this.extras.putAll(extras) }
        nm.notify(ISLAND_NOTI_ID, n)
    }

    private fun vivoEnd() {
        val extras = vivoBaseExtras(2)
        val n = baseBuilder().build().apply { this.extras.putAll(extras) }
        nm.notify(ISLAND_NOTI_ID, n)
        nm.cancel(ISLAND_NOTI_ID)
    }

    private fun NotificationCompat.Builder.addExtrasBundle(): NotificationCompat.Builder = this

    private object Brand {
        val isMiHyperOs: Boolean = runCatching {
            !getProperty("ro.mi.os.version.name").isNullOrBlank()
        }.getOrDefault(false)

        private fun getProperty(key: String): String? =
            try {
                val clz = Class.forName("android.os.SystemProperties")
                val m = clz.getMethod("get", String::class.java)
                m.invoke(null, key) as? String
            } catch (t: Throwable) {
                null
            }
    }

    companion object {
        private const val TAG = "VendorIsland"
        private const val ISLAND_NOTI_ID = 2001
        private const val MI_FOCUS_PARAM = "miui.focus.param"
    }
}
