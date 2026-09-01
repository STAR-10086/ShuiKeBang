package com.star.shuikebang.ui.navigation

object Routes {
    const val HOME = "home"
    const val MODEL = "model"
    const val RECORD = "record"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SESSION = "session/{sessionId}"
    fun session(id: Long) = "session/$id"
}
