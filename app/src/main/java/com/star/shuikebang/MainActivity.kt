package com.star.shuikebang

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.star.shuikebang.ui.navigation.AppNav
import com.star.shuikebang.ui.theme.ShuikeTheme

class MainActivity : ComponentActivity() {

    // 待跳转的问题 id：onCreate 与 singleTop 下的 onNewIntent 都汇入这里驱动导航
    private var pendingQuestionId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingQuestionId = intent.extractQuestionId()
        enableEdgeToEdge()
        setContent {
            ShuikeTheme {
                AppNav(openQuestionId = pendingQuestionId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 应用已存在时收到新通知：更新状态触发 AppNav 重新跳转
        pendingQuestionId = intent.extractQuestionId()
    }

    private fun Intent?.extractQuestionId(): Long? =
        this?.getLongExtra(EXTRA_QUESTION_ID, -1L)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_QUESTION_ID = "extra_question_id"
    }
}
