package com.star.shuikebang.island

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.star.shuikebang.MainActivity
import com.star.shuikebang.util.TimeFmt

/**
 * L2：自绘悬浮胶囊（dynamicSpot / 小组件盒子原理）。
 * 厂商原生岛不可用、且用户授予悬浮窗权限时启用；默认开关关闭。
 */
class OverlayCapsule(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var dot: View? = null
    private var titleView: TextView? = null
    private var subView: TextView? = null

    fun available(): Boolean = Settings.canDrawOverlays(context)

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics,
    ).toInt()

    fun show() {
        if (!available() || root != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(12)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            // 圆角深色底
            background?.setTint(Color.parseColor("#111318"))
        }
        val redDot = View(context).apply {
            val size = dp(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(8) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#F23C3C"))
            }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(context).apply {
            text = "正在记录课堂"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val sub = TextView(context).apply {
            text = "本地识别中 · 00:00"
            setTextColor(Color.parseColor("#B6BAC3"))
            textSize = 10f
        }
        texts.addView(title)
        texts.addView(sub)
        container.addView(redDot)
        container.addView(texts)
        container.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
        runCatching {
            wm.addView(container, lp)
            root = container; dot = redDot; titleView = title; subView = sub
        }
    }

    fun updateTimer(durationSec: Int) {
        subView?.post { subView?.text = "本地识别中 · ${TimeFmt.duration(durationSec)}" }
    }

    /** 提问瞬间：胶囊变红、显示问题摘要，数秒后恢复 */
    fun flashQuestion(short: String) {
        val view = root ?: return
        view.post {
            titleView?.text = "检测到老师提问"
            subView?.text = short
            dot?.background?.setTint(Color.parseColor("#F23C3C"))
            view.background?.setTint(Color.parseColor("#3A1416"))
            view.postDelayed({
                titleView?.text = "正在记录课堂"
                view.background?.setTint(Color.parseColor("#111318"))
            }, 4000)
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; dot = null; titleView = null; subView = null
    }
}
