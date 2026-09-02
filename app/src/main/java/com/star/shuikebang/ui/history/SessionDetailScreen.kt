package com.star.shuikebang.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star.shuikebang.ui.component.QuestionCard
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import com.star.shuikebang.util.Clip
import com.star.shuikebang.util.TimeFmt
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    highlightQuestionId: Long? = null,
    vm: HistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 关键：用 remember(sessionId) 固定两个数据库 Flow，避免每次重组都新建 stateIn 观察者
    val transcriptsFlow = remember(sessionId) { vm.transcripts(sessionId) }
    val questionsFlow = remember(sessionId) { vm.questions(sessionId) }
    val transcripts by transcriptsFlow.collectAsStateWithLifecycle()
    val questions by questionsFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 从提问通知进入：问题列表加载后滚动并定位到对应问题（列表首项是“老师提问”标题）
    LaunchedEffect(highlightQuestionId, questions.size) {
        val hq = highlightQuestionId ?: return@LaunchedEffect
        if (questions.isEmpty()) return@LaunchedEffect
        val idx = questions.indexOfFirst { it.id == hq }
        if (idx >= 0) listState.scrollToItem(idx + 1)
    }

    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain) }
            Text(
                "课堂详情",
                color = TextMain,
                fontSize = TextUnit(18f, TextUnitType.Sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                scope.launch { Clip.copy(context, vm.fullText(sessionId), "全部文本已复制") }
            }) { Icon(Icons.Outlined.ContentCopy, "复制全部", tint = Brand) }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (questions.isNotEmpty()) {
                item(key = "q_header") {
                    Text(
                        "老师提问 · ${questions.size}",
                        color = TextMain,
                        fontSize = TextUnit(14f, TextUnitType.Sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                items(questions, key = { "q_${it.id}" }) { q ->
                    QuestionCard(question = q, onCopy = { Clip.copy(context, it.coreQuestion, "问题已复制") })
                }
            }
            item(key = "t_header") {
                Text(
                    "完整转录",
                    color = TextMain,
                    fontSize = TextUnit(14f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            if (transcripts.isEmpty()) {
                item(key = "t_empty") { Text("暂无转录内容", color = TextFaint, fontSize = TextUnit(12f, TextUnitType.Sp)) }
            }
            items(transcripts, key = { "t_${it.id}" }) { t ->
                val isMark = t.text.startsWith("★")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMark) Brand.copy(alpha = 0.10f) else CardWhite)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(TimeFmt.stamp(t.ts), color = TextFaint, fontSize = TextUnit(10.5f, TextUnitType.Sp))
                    Text(
                        t.text,
                        color = when {
                            isMark -> Brand
                            t.questionId != null -> Brand
                            else -> TextMain
                        },
                        fontWeight = if (isMark) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = TextUnit(14f, TextUnitType.Sp),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
