package com.star.shuikebang.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object Clip {
    fun copy(context: Context, text: String, toast: String = "已复制") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("shuikebang", text))
        Toast.makeText(context.applicationContext, toast, Toast.LENGTH_SHORT).show()
    }
}
