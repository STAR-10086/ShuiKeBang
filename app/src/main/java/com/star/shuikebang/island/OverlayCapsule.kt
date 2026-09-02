package com.star.shuikebang.island

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.star.shuikebang.MainActivity
import com.star.shuikebang.service.RecordService
import com.star.shuikebang.util.TimeFmt
import kotlin.math.abs

/**
 * L2：自绘可操作悬浮窗（dynamicSpot / 小组件盒子原理）。
 *
 * - 折叠态：顶部胶囊，显示录制状态与计时，可拖动、点按展开；
 * - 展开态：显示最近一条老师提问，并提供「暂停/继续 · 结束 · 打开应用 · 收起」操作，
 *   操作直接回送 [RecordService]，无需把 App 切到前台。
 *
 * 厂商原生岛不可用、且用户授予悬浮窗权限时启用（设置开关）。
 */
class OverlayCapsule(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var dot: View? = null
    private var titleView: TextView? = null
    private var subView: TextView? = null
    private var expandArea: LinearLayout? = null
    private var questionView: TextView? = null
    private var btnPause: TextView? = null

    private var expanded = false
    private var paused = false
    private var durationSec = 0
    private var latestQuestion: String? = null

    fun available(): Boolean = Settings.canDrawOverlays(context)

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics,
    ).toInt()

    private fun sp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics,
    )

    private fun roundedBg(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    fun show() {
        if (!available() || root != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
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
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBg(Color.parseColor("#111318"), 20)
        }

        // —— 折叠行：红点 + 标题/计时（点按展开，长按区域可拖动） ——
        val pillRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val redDot = View(context).apply {
            val size = dp(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(8) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F23C3C"))
            }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(context).apply {
            text = "正在记录课堂"
            setTextColor(Color.WHITE)
            textSize = sp(13f)
        }
        val sub = TextView(context).apply {
            text = "本地识别中 · 00:00"
            setTextColor(Color.parseColor("#B6BAC3"))
            textSize = sp(10f)
        }
        texts.addView(title)
        texts.addView(sub)
        pillRow.addView(redDot)
        pillRow.addView(texts)

        // —— 展开区：最近提问 + 操作按钮 ——
        val area = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        val q = TextView(context).apply {
            text = "还没有检测到提问"
            setTextColor(Color.parseColor("#E7E9EE"))
            textSize = sp(12.5f)
            maxLines = 3
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pause = makeButton("暂停", "#2E6BE6") { sendControl(if (paused) RecordService.ACTION_RESUME else RecordService.ACTION_PAUSE) }
        val stop = makeButton("结束", "#D94040") { sendControl(RecordService.ACTION_STOP) }
        val open = makeButton("打开", "#3A3D46") { openApp() }
        val collapse = makeButton("收起", "#3A3D46") { setExpanded(false) }
        btnRow.addView(pause); btnRow.addView(stop); btnRow.addView(open); btnRow.addView(collapse)
        area.addView(q)
        area.addView(btnRow)

        container.addView(pillRow)
        container.addView(area)
        attachDrag(container, params) { setExpanded(!expanded) }

        runCatching {
            wm.addView(container, params)
            root = container; lp = params; dot = redDot
            titleView = title; subView = sub; expandArea = area
            questionView = q; btnPause = pause
        }
    }

    private fun makeButton(text: String, bgHex: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = sp(12f)
            gravity = Gravity.CENTER
            val padH = dp(12); val padV = dp(6)
            setPadding(padH, padV, padH, padV)
            background = roundedBg(Color.parseColor(bgHex), 10)
            val lpBtn = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(8) }
            layoutParams = lpBtn
            setOnClickListener { onClick() }
        }
    }

    private fun sendControl(action: String) {
        runCatching {
            val i = Intent(context, RecordService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
    }

    private fun openApp() {
        runCatching {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(i)
        }
    }

    /** 拖动 + 点击区分：移动超过 touchSlop 视为拖动，抬手未拖动则 onClick（展开/收起） */
    private fun attachDrag(v: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0; var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { root?.let { wm.updateViewLayout(it, params) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setExpanded(open: Boolean) {
        expanded = open
        expandArea?.visibility = if (open) View.VISIBLE else View.GONE
        questionView?.text = latestQuestion ?: "还没有检测到提问"
    }

    fun updateTimer(sec: Int) {
        durationSec = sec
        val v = root ?: return
        v.post {
            if (!paused) subView?.text = "本地识别中 · ${TimeFmt.duration(sec)}"
        }
    }

    fun setPaused(p: Boolean, sec: Int = durationSec) {
        paused = p; durationSec = sec
        val v = root ?: return
        v.post {
            titleView?.text = if (p) "已暂停" else "正在记录课堂"
            subView?.text = if (p) "已暂停 · ${TimeFmt.duration(sec)}"
            else "本地识别中 · ${TimeFmt.duration(sec)}"
            dot?.background?.setTint(if (p) Color.parseColor("#F0A830") else Color.parseColor("#F23C3C"))
            btnPause?.text = if (p) "继续" else "暂停"
        }
    }

    /** 提问瞬间：折叠胶囊高亮、记录问题文本供展开查看 */
    fun flashQuestion(short: String) {
        latestQuestion = short
        val v = root ?: return
        v.post {
            questionView?.text = short
            titleView?.text = "检测到老师提问"
            subView?.text = short
            dot?.background?.setTint(Color.parseColor("#F23C3C"))
            v.background?.setTint(Color.parseColor("#3A1416"))
            v.postDelayed({
                titleView?.text = if (paused) "已暂停" else "正在记录课堂"
                v.background?.setTint(Color.parseColor("#111318"))
                if (!paused) subView?.text = "本地识别中 · ${TimeFmt.duration(durationSec)}"
            }, 4000)
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; lp = null; dot = null; titleView = null; subView = null
        expandArea = null; questionView = null; btnPause = null
        expanded = false; paused = false; latestQuestion = null
    }
}
