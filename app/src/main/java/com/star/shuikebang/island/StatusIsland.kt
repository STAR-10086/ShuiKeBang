package com.star.shuikebang.island

import android.content.Context

/**
 * 状态岛统一门面，实现四级降级：
 * L0 应用内胶囊（Compose，RecordScreen 内自行渲染）
 * L1 厂商原生岛（小米超级岛 / vivo 原子岛，本地通知）
 * L2 自绘悬浮窗 OverlayCapsule（可拖动/展开/操作，需悬浮窗权限，设置开关）
 * L3 前台服务常驻通知 FgsNotifier（可暂停/继续/结束、展示最近提问，始终存在）
 */
class StatusIsland(context: Context) {

    private val appContext = context.applicationContext
    private val fgs = FgsNotifier(appContext)
    private val vendor = VendorIslandNotifier(appContext)
    private val overlay = OverlayCapsule(appContext)

    /** 用户设置：是否启用 L2 悬浮窗 */
    @Volatile
    var overlayEnabled: Boolean = false

    private var useVendorIsland = false
    private var paused = false
    private var lastSec = 0
    private var lastTitle = "正在记录课堂"
    private var lastQuestion: String? = null

    fun fgsNotifier(): FgsNotifier = fgs

    fun onStart(title: String) {
        paused = false; lastSec = 0; lastTitle = title; lastQuestion = null
        fgs.clearQuestion()
        useVendorIsland = vendor.isSupported()
        if (useVendorIsland) vendor.showCapsule(0, title)
        if (overlayEnabled) overlay.show()
    }

    fun onTick(durationSec: Int, title: String) {
        lastSec = durationSec; lastTitle = title
        fgs.refresh(durationSec, paused, lastQuestion) // L3 始终刷新
        if (useVendorIsland) vendor.updateCapsule(durationSec, islandTitle())
        else if (overlayEnabled) overlay.updateTimer(durationSec)
    }

    fun onPause() {
        if (paused) return
        paused = true
        fgs.refresh(lastSec, true, lastQuestion)
        if (useVendorIsland) vendor.updateCapsule(lastSec, "已暂停")
        else if (overlayEnabled) overlay.setPaused(true, lastSec)
    }

    fun onResume() {
        if (!paused) return
        paused = false
        fgs.refresh(lastSec, false, lastQuestion)
        if (useVendorIsland) vendor.updateCapsule(lastSec, lastTitle)
        else if (overlayEnabled) overlay.setPaused(false, lastSec)
    }

    fun onQuestion(questionId: Long, short: String, full: String) {
        lastQuestion = short
        fgs.refresh(lastSec, paused, short)
        when {
            useVendorIsland -> vendor.showQuestion(questionId, short, full)
            overlayEnabled -> overlay.flashQuestion(short)
        }
    }

    /** 想开悬浮窗但尚未取得悬浮窗权限（用于一次性引导） */
    fun overlayMissingPermission(): Boolean = overlayEnabled && !overlay.available()

    fun onStop() {
        vendor.finish()
        overlay.hide()
        fgs.cancel()
        paused = false; lastQuestion = null
    }

    private fun islandTitle(): String = if (paused) "已暂停" else lastTitle

    fun describeActiveLayer(): String = when {
        useVendorIsland -> "L1 厂商原生岛（${if (vendor.vendor == VendorIslandNotifier.Vendor.XIAOMI) "小米超级岛" else "vivo 原子岛"}）"
        overlayEnabled && overlay.available() -> "L2 悬浮窗"
        else -> "L3 前台通知（L0 应用内胶囊始终可用）"
    }
}
