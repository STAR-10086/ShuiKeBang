package com.star.shuikebang.island

import android.content.Context

/**
 * 状态岛统一门面，实现四级降级：
 * L0 应用内胶囊（Compose，RecordScreen 内自行渲染）
 * L1 厂商原生岛（小米超级岛 / vivo 原子岛，本地通知）
 * L2 自绘悬浮胶囊 OverlayCapsule（需悬浮窗权限，设置开关）
 * L3 前台服务常驻通知 FgsNotifier（始终存在，录音合规要求）
 */
class StatusIsland(context: Context) {

    private val appContext = context.applicationContext
    private val fgs = FgsNotifier(appContext)
    private val vendor = VendorIslandNotifier(appContext)
    private val overlay = OverlayCapsule(appContext)

    /** 用户设置：是否启用 L2 悬浮胶囊，默认关闭 */
    @Volatile
    var overlayEnabled: Boolean = false

    private var useVendorIsland = false

    fun fgsNotifier(): FgsNotifier = fgs

    fun onStart(title: String) {
        useVendorIsland = vendor.isSupported()
        if (useVendorIsland) vendor.showCapsule(0, title)
        if (overlayEnabled && !useVendorIsland) overlay.show()
    }

    fun onTick(durationSec: Int, title: String) {
        fgs.refresh(durationSec) // L3 始终刷新
        if (useVendorIsland) vendor.updateCapsule(durationSec, title)
        else if (overlayEnabled) overlay.updateTimer(durationSec)
    }

    fun onQuestion(questionId: Long, short: String, full: String) {
        when {
            useVendorIsland -> vendor.showQuestion(questionId, short, full)
            overlayEnabled -> overlay.flashQuestion(short)
        }
    }

    fun onStop() {
        vendor.finish()
        overlay.hide()
        fgs.cancel()
    }

    fun describeActiveLayer(): String = when {
        useVendorIsland -> "L1 厂商原生岛（${if (vendor.vendor == VendorIslandNotifier.Vendor.XIAOMI) "小米超级岛" else "vivo 原子岛"}）"
        overlayEnabled && overlay.available() -> "L2 悬浮胶囊"
        else -> "L3 前台通知（L0 应用内胶囊始终可用）"
    }
}
