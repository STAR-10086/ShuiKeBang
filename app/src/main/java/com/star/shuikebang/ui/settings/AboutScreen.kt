package com.star.shuikebang.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.star.shuikebang.BuildConfig
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub

private const val URL_AUTHOR = "https://github.com/STAR-10086"
private const val URL_REPO = "https://github.com/STAR-10086/ShuiKeBang"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain) }
            Text("关于与隐私", color = TextMain, fontSize = TextUnit(18f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 头部
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .padding(18.dp),
            ) {
                Text("水课帮", color = TextMain, fontSize = TextUnit(22f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
                Text("课堂提问助手 · Android", color = TextSub, fontSize = TextUnit(13f, TextUnitType.Sp),
                    modifier = Modifier.padding(top = 4.dp))
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）",
                    color = TextFaint, fontSize = TextUnit(11.5f, TextUnitType.Sp),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "麦克风实时收音、本地离线流式语音识别，自动捕捉老师提问，走神也能快速回溯问题。",
                    color = TextSub, fontSize = TextUnit(12.5f, TextUnitType.Sp), lineHeight = TextUnit(19f, TextUnitType.Sp),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            CardSection("核心功能") {
                Bullet("真流式离线识别：sherpa-onnx Streaming Zipformer（INT8），中英双语，边说边出字")
                Bullet("两级提问检测：轻量规则 + 约 35KB 极小分类器，叠加跨句二次确认，抑制自问自答误报")
                Bullet("提问即时提醒：震动、录制页高亮、提问列表沉淀，一键复制回溯")
                Bullet("可交互通知与悬浮控制窗：切到其他应用也能看最近提问、暂停/继续/结束")
                Bullet("麦克风增益：自动 AGC / 多档固定放大，改善老师声音偏小导致的漏识别")
                Bullet("本地课堂档案：Room 保存转录与提问，可浏览、重命名、删除、复制")
                Bullet("可选 AI 解答：使用你自己填写的兼容端点与 Key，App 不内置任何 API")
            }

            CardSection("隐私承诺") {
                Bullet("音频只在内存中用于识别，不录音保存、不上传云端，卸载即清除全部数据")
                Bullet("无账号、无广告、无社区、无云同步；除「下载识别模型」与你主动发起的 AI 解答外不联网")
                Bullet("已关闭系统云备份（allowBackup=false），课堂文本不会进入换机/云备份")
                Bullet("AI 解答仅在你点击时发送对应问题文本到你自填的端点，API Key 只存本机")
            }

            CardSection("技术栈") {
                Bullet("Kotlin + Jetpack Compose + Material3 极简 UI")
                Bullet("sherpa-onnx / ONNX Runtime 离线流式 ASR，模型动态下发、不打进 APK")
                Bullet("Room 本地数据库 · DataStore 设置 · OkHttp（模型下载 / AI 解答）")
                Bullet("前台服务 + 多级状态岛：小米/vivo 原生岛 → 悬浮窗 → 常驻通知")
            }

            CardSection("开源与致谢") {
                Text(
                    "本项目开源，离线识别基于 k2-fsa/sherpa-onnx 与开源语音模型，特此致谢。",
                    color = TextSub, fontSize = TextUnit(12f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp),
                )
                Spacer(Modifier.height(8.dp))
                LinkRow(Icons.Outlined.Person, "作者 GitHub 主页", URL_AUTHOR) { open(URL_AUTHOR) }
                Spacer(Modifier.height(8.dp))
                LinkRow(Icons.Outlined.Code, "项目源码仓库", URL_REPO) { open(URL_REPO) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CardSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(14.dp),
    ) {
        Text(title, color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("·  ", color = Brand, fontSize = TextUnit(12.5f, TextUnitType.Sp))
        Text(text, color = TextSub, fontSize = TextUnit(12.5f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp))
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    url: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, color = TextMain, fontSize = TextUnit(13f, TextUnitType.Sp))
            Text(url, color = TextFaint, fontSize = TextUnit(11f, TextUnitType.Sp))
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = TextSub)
    }
}
