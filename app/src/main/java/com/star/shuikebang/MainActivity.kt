package com.star.shuikebang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.star.shuikebang.ui.navigation.AppNav
import com.star.shuikebang.ui.theme.ShuikeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShuikeTheme {
                AppNav(
                    openQuestionId = intent?.getLongExtra(EXTRA_QUESTION_ID, -1L)
                        ?.takeIf { it > 0 },
                )
            }
        }
    }

    companion object {
        const val EXTRA_QUESTION_ID = "extra_question_id"
    }
}
