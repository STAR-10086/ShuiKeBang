package com.star.shuikebang.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.star.shuikebang.ai.AiClient
import com.star.shuikebang.ai.AiProtocol
import com.star.shuikebang.data.prefs.SettingsRepository
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import com.star.shuikebang.util.Clip
import kotlinx.coroutines.launch

private sealed interface AnsState {
    data object Loading : AnsState
    data object NotConfigured : AnsState
    data class Success(val text: String) : AnsState
    data class Error(val msg: String) : AnsState
}

/** AI 解答弹层：用用户自填端点回答这一句问题；音频不参与，仅发送问题文本 */
@Composable
fun AiAnswerDialog(
    question: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AnsState>(AnsState.Loading) }

    fun launch() {
        state = AnsState.Loading
        scope.launch {
            val cfg = SettingsRepository.get(context).snapshot()
            state = if (!AiProtocol.isReady(cfg.aiBaseUrl, cfg.aiApiKey)) {
                AnsState.NotConfigured
            } else {
                runCatching {
                    AiClient().ask(
                        cfg.aiBaseUrl, cfg.aiApiKey,
                        cfg.aiModel.ifBlank { AiProtocol.DEFAULT_MODEL }, question,
                    )
                }.fold(
                    onSuccess = { AnsState.Success(it) },
                    onFailure = { AnsState.Error(it.message ?: "请求失败") },
                )
            }
        }
    }
    LaunchedEffect(question) { launch() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(CardWhite, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Brand)
                Text(
                    "  AI 解答",
                    color = TextMain, fontSize = TextUnit(16f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                question,
                color = TextSub, fontSize = TextUnit(12.5f, TextUnitType.Sp),
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.padding(top = 12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 320.dp)
                    .background(PageBg, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                when (val st = state) {
                    AnsState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("  正在思考…", color = TextSub, fontSize = TextUnit(13f, TextUnitType.Sp))
                    }
                    AnsState.NotConfigured -> Text(
                        "还没有配置 AI 端点或 Key。\n请到「设置 - AI 解答」填入你自己的 OpenAI 兼容端点与 Key，App 不内置任何 API。",
                        color = RecordRed, fontSize = TextUnit(13f, TextUnitType.Sp),
                    )
                    is AnsState.Error -> Text(
                        "出错了：${st.msg}", color = RecordRed, fontSize = TextUnit(13f, TextUnitType.Sp),
                    )
                    is AnsState.Success -> Text(
                        st.text, color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp),
                        lineHeight = TextUnit(21f, TextUnitType.Sp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                val st = state
                if (st is AnsState.Success) {
                    TextButton(onClick = { Clip.copy(context, st.text, "解答已复制") }) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Text(" 复制")
                    }
                }
                if (st is AnsState.Error) {
                    TextButton(onClick = { launch() }) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp)); Text(" 重试")
                    }
                }
                if (st is AnsState.NotConfigured) {
                    TextButton(onClick = { onDismiss(); onOpenSettings() }) { Text("去配置") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}
