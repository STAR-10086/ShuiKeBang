package com.star.shuikebang.ui.navigation

object Routes {
    const val HOME = "home"
    const val MODEL = "model"
    const val RECORD = "record"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val AI_SETTINGS = "ai_settings"
    const val ABOUT = "about"

    /** 可选查询参数：从提问通知进入时要定位高亮的问题 id，-1 表示不高亮 */
    const val ARG_HIGHLIGHT_QID = "hq"
    const val SESSION = "session/{sessionId}?$ARG_HIGHLIGHT_QID={$ARG_HIGHLIGHT_QID}"

    fun session(id: Long, highlightQuestionId: Long? = null): String =
        if (highlightQuestionId == null) "session/$id"
        else "session/$id?$ARG_HIGHLIGHT_QID=$highlightQuestionId"
}
