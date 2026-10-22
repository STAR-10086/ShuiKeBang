package com.star.shuikebang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

@Composable
fun AppNav(openQuestionId: Long? = null) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsRepository.get(context)

    // 是否由首页“开始记录”跳来模型页：仅用于在模型页给出“先下载再开始”的提示文案
    var fromStartRequest by remember { mutableStateOf(false) }

    /**
     * 开始录音的唯一入口：选中的模型就绪就直接起服务并进录音页，否则去模型页下载。
     * 模型 id 由调用方给出——首页在**点击那一刻**现读 DataStore，
     * 不再用 AppNav 组合期读一次的缓存值（模型页改过选择后缓存会过期，
     * 会导致“下载好了却还是被送回模型页、且模型页没有继续按钮”的死循环）。
     */
    fun startRecording(modelId: String) {
        val spec = BuiltinModels.byId(modelId)
        if (ModelManager.get(context).isReady(spec)) {
            RecordService.start(context, spec.id)
            nav.navigate(Routes.RECORD)
        } else {
            fromStartRequest = true
            nav.navigate(Routes.MODEL)
        }
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
                    scope.launch {
                        val id = runCatching { settings.snapshot().selectedModelId }
                            .getOrDefault(BuiltinModels.RECOMMENDED_ID)
                        startRecording(id)
                    }
                },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenModel = {
                    fromStartRequest = false
                    nav.navigate(Routes.MODEL)
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.MODEL) {
            ModelDownloadScreen(
                onBack = { nav.popBackStack() },
                fromStartRequest = fromStartRequest,
                onStartRecording = { id -> startRecording(id) },
            )
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
