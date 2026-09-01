package com.star.shuikebang.ui.idle

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.BrandSoft
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.DividerLine
import com.star.shuikebang.ui.theme.OkGreen
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub

@Composable
fun IdleScreen(
    onStartRecording: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val modelReady by produceState(initialValue = false) {
        value = ModelManager.get(context).currentReadySpecOrNull() != null
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onStartRecording() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 32.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, "设置", tint = TextSub)
            }
        }
        Spacer(Modifier.height(48.dp))
        Text("水课帮", color = TextMain, fontSize = TextUnit(28f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        Text(
            "课堂提问助手 · 走神也能秒回溯",
            color = TextSub,
            fontSize = TextUnit(13f, TextUnitType.Sp),
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.weight(1f))

        // 中央大按钮 + 呼吸光圈
        val transition = rememberInfiniteTransition(label = "halo")
        val halo by transition.animateFloat(
            initialValue = 0.85f, targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "h",
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(190.dp)
                    .scale(halo)
                    .clip(CircleShape)
                    .background(BrandSoft.copy(alpha = 0.7f)),
            )
            Box(
                Modifier
                    .size(138.dp)
                    .clip(CircleShape)
                    .background(Brand)
                    .clickable {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) onStartRecording() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Mic, null, tint = CardWhite, modifier = Modifier.size(46.dp))
                    Text(
                        "开始记录",
                        color = CardWhite,
                        fontSize = TextUnit(17f, TextUnitType.Sp),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(BrandSoft)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, null, tint = OkGreen, modifier = Modifier.size(16.dp))
            Text(
                "  音频全程本地离线识别，不上传、不保存录音",
                color = TextSub,
                fontSize = TextUnit(11.5f, TextUnitType.Sp),
            )
        }

        Spacer(Modifier.weight(1f))

        // 模型状态入口
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
                .clickable(onClick = onOpenModel)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (modelReady) OkGreen else Color(0xFFF2994A)),
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    if (modelReady) "识别模型已就绪" else "尚未下载识别模型",
                    color = TextMain,
                    fontSize = TextUnit(14f, TextUnitType.Sp),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (modelReady) BuiltinModels.SMALL_BILINGUAL.displayName else "首次使用需下载，约 50MB（仅下载时联网）",
                    color = TextSub,
                    fontSize = TextUnit(11f, TextUnitType.Sp),
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextSub)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
                .clickable(onClick = onOpenHistory)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.History, null, tint = Brand)
            Text(
                "  历史课堂记录",
                color = TextMain,
                fontSize = TextUnit(14f, TextUnitType.Sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextSub)
        }
        Spacer(Modifier.height(28.dp))
    }
}
