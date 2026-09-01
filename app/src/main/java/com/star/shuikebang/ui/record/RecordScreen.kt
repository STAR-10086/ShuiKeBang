package com.star.shuikebang.ui.record

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.shuikebang.data.db.QuestionEntity
import com.star.shuikebang.service.RecSession
import com.star.shuikebang.service.RecordService
import com.star.shuikebang.service.UtteranceLine
import com.star.shuikebang.ui.component.ControlDock
import com.star.shuikebang.ui.component.QuestionCard
import com.star.shuikebang.ui.component.RecStatusBar
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.RecordLine
import com.star.shuikebang.ui.theme.RecordSoft
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import com.star.shuikebang.util.Clip
import com.star.shuikebang.util.TimeFmt

@Composable
fun RecordScreen(onStopped: () -> Unit) {
    val context = LocalContext.current
    val ui by RecSession.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 新行/新提问时自动滚动到底部
    LaunchedEffect(ui.lines.size, ui.partial) {
        val total = ui.lines.size
        if (total > 0) listState.animateScrollToItem(total)
    }
    // 提问事件：自动定位到最新问题
    LaunchedEffect(Unit) {
        RecSession.questionEvents.collect {
            val idx = ui.lines.indexOfLast { l -> l.questionId == it.id }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }

    val questionMap = ui.questions.associateBy { it.id }
    val preparing = ui.starting || !ui.recording

    // 启动失败（模型下载失败/引擎初始化失败）：弹窗提示并退回首页，不再卡在假"记录中"
    var errorDialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.error) {
        if (!ui.error.isNullOrBlank()) errorDialog = ui.error
    }
    errorDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("无法开始记录") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    errorDialog = null
                    RecSession.reset()
                    onStopped()
                }) { Text("返回") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        RecStatusBar(
            durationSec = ui.durationSec,
            preparing = preparing,
            prepareMsg = ui.prepareMsg,
        )

        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.lines) { line ->
                    val q: QuestionEntity? = line.questionId?.let { questionMap[it] }
                    if (q != null) {
                        QuestionCard(question = q, onCopy = {
                            Clip.copy(context, it.coreQuestion, "问题已复制")
                        })
                    } else {
                        TranscriptRow(line)
                    }
                }
                if (ui.partial.isNotBlank()) {
                    item { PartialRow(ui.partial) }
                }
            }
            if (ui.lines.isEmpty() && ui.partial.isBlank()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (preparing) ui.prepareMsg ?: "正在准备识别…" else "正在聆听，讲课内容会显示在这里",
                        color = TextFaint,
                        fontSize = TextUnit(13f, TextUnitType.Sp),
                    )
                }
            }
        }

        ControlDock(
            onStop = {
                RecordService.stop(context)
                onStopped()
            },
            onCopy = {
                val all = ui.lines.joinToString("\n") { "${TimeFmt.stamp(it.ts)} ${it.text}" }
                Clip.copy(context, all, "全部文本已复制")
            },
            onAskAi = {
                val latest = ui.questions.lastOrNull()?.coreQuestion
                    ?: ui.lines.lastOrNull()?.text.orEmpty()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "请帮我解答这个课堂问题：$latest")
                }
                context.startActivity(Intent.createChooser(send, "问 AI").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            onMark = {
                // 轻量标记：插入一条普通标记行（不落音频）
                RecSession.update {
                    it.copy(
                        lines = it.lines + UtteranceLine(
                            System.currentTimeMillis(), "【标记重点】", level = 0,
                        ),
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TranscriptRow(line: UtteranceLine) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardWhite, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            TimeFmt.stamp(line.ts),
            color = TextFaint,
            fontSize = TextUnit(10.5f, TextUnitType.Sp),
        )
        Text(
            line.text,
            color = TextMain,
            fontSize = TextUnit(14f, TextUnitType.Sp),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PartialRow(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(RecordSoft, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = TextSub,
            fontStyle = FontStyle.Italic,
            fontSize = TextUnit(14f, TextUnitType.Sp),
            fontWeight = FontWeight.Normal,
        )
    }
}
