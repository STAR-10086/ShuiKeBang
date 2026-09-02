package com.star.shuikebang.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star.shuikebang.ai.AiProtocol
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.BrandSoft
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.OkGreen
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub

private data class EndpointPreset(val name: String, val url: String, val model: String)

private val PRESETS = listOf(
    EndpointPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    EndpointPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    EndpointPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    EndpointPreset("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
    EndpointPreset("本地 Ollama", "http://手机能访问的IP:11434/v1", "llama3.1"),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ready = AiProtocol.isReady(s.aiBaseUrl, s.aiApiKey)

    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain) }
            Text("AI 解答", color = TextMain, fontSize = TextUnit(18f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteCard(
                "使用你自己的 AI 端点与 Key，App 不内置、不代理任何 API。仅当你主动点「AI 解答」时，" +
                    "才把这一句问题文本发送到你填写的端点；录音与音频始终留在本机、不会上传。",
            )

            StatusPill(ready)

            Field("端点 Base URL（填到 /v1）", s.aiBaseUrl, "https://api.openai.com/v1") { vm.setAiBaseUrl(it) }
            Field("API Key", s.aiApiKey, "sk-...", password = true) { vm.setAiApiKey(it) }
            Field("模型名", s.aiModel, AiProtocol.DEFAULT_MODEL) { vm.setAiModel(it) }

            Text("快捷填入常见兼容端点", color = TextFaint, fontSize = TextUnit(12f, TextUnitType.Sp),
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    Text(
                        p.name,
                        color = Brand,
                        fontSize = TextUnit(12.5f, TextUnitType.Sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BrandSoft)
                            .clickable {
                                vm.setAiBaseUrl(p.url)
                                vm.setAiModel(p.model)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            NoteCard(
                "兼容任何 OpenAI Chat Completions 协议的服务：官方 OpenAI、DeepSeek、通义千问兼容模式、" +
                    "硅基流动、OneAPI 中转，或本地 Ollama / LM Studio（地址填手机能访问的局域网 IP，不能用 127.0.0.1）。\n" +
                    "Key 仅保存在本机 DataStore，随卸载清除。",
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(14.dp),
    ) {
        Text(text, color = TextSub, fontSize = TextUnit(12f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp))
    }
}

@Composable
private fun StatusPill(ready: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (ready) OkGreen.copy(alpha = 0.14f) else BrandSoft)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ready) "● 已配置，可在问题卡片上点「AI 解答」" else "○ 尚未配置完成",
            color = if (ready) OkGreen else TextSub,
            fontSize = TextUnit(12f, TextUnitType.Sp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = !password,
        maxLines = if (password) 1 else 3,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
