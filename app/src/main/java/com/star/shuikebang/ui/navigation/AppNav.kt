package com.star.shuikebang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.data.db.ClassRepository
import com.star.shuikebang.data.prefs.SettingsRepository
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

    // 用户持久化选择的模型 id：开始录音时明确传给 Service，避免“下了小模型却又去下默认双语模型”
    val selectedModelId by produceState(initialValue = BuiltinModels.RECOMMENDED_ID) {
        value = SettingsRepository.get(context).snapshot().selectedModelId
    }

    // 点击提问通知（含应用已在前台的二次点击）：按问题反查所属课堂并跳转、定位该问题
    LaunchedEffect(openQuestionId) {
        val qid = openQuestionId ?: return@LaunchedEffect
        val sid = ClassRepository.get(context).sessionOfQuestion(qid)
        if (sid != null) nav.navigate(Routes.session(sid, qid))
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            IdleScreen(
                onStartRecording = {
                    val spec = BuiltinModels.byId(selectedModelId)
                    // 只认用户选中的模型：它就绪才直接开始（并把 id 显式传入），否则去模型页下载
                    if (ModelManager.get(context).isReady(spec)) {
                        RecordService.start(context, spec.id)
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
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument(Routes.ARG_HIGHLIGHT_QID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("sessionId") ?: 0L
            val hq = entry.arguments?.getLong(Routes.ARG_HIGHLIGHT_QID)?.takeIf { it > 0 }
            SessionDetailScreen(
                sessionId = id,
                highlightQuestionId = hq,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
