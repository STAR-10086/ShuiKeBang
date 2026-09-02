package com.star.shuikebang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.service.RecordService
import com.star.shuikebang.ui.history.HistoryListScreen
import com.star.shuikebang.ui.history.SessionDetailScreen
import com.star.shuikebang.ui.idle.IdleScreen
import com.star.shuikebang.ui.model.ModelDownloadScreen
import com.star.shuikebang.ui.record.RecordScreen
import com.star.shuikebang.ui.settings.AboutScreen
import com.star.shuikebang.ui.settings.AiSettingsScreen
import com.star.shuikebang.ui.settings.SettingsScreen

@Composable
fun AppNav(openQuestionId: Long? = null) {
    val nav = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            IdleScreen(
                onStartRecording = {
                    val ready = ModelManager.get(context).currentReadySpecOrNull() != null
                    if (ready) {
                        RecordService.start(context)
                        nav.navigate(Routes.RECORD)
                    } else {
                        nav.navigate(Routes.MODEL)
                    }
                },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenModel = { nav.navigate(Routes.MODEL) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.MODEL) {
            ModelDownloadScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.RECORD) {
            RecordScreen(
                onStopped = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAi = { nav.navigate(Routes.AI_SETTINGS) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.AI_SETTINGS) { AiSettingsScreen(onBack = { nav.popBackStack() }) }

        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }

        composable(Routes.HISTORY) {
            HistoryListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.session(id)) },
            )
        }

        composable(
            Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("sessionId") ?: 0L
            SessionDetailScreen(sessionId = id, onBack = { nav.popBackStack() })
        }
    }
}
