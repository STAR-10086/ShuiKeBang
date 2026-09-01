package com.star.shuikebang.ui.model

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star.shuikebang.asr.AsrModelSpec
import com.star.shuikebang.asr.ModelState
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.BrandSoft
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.OkGreen
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import java.util.Locale

@Composable
fun ModelDownloadScreen(
    onBack: () -> Unit,
    vm: ModelDownloadViewModel = viewModel(),
) {
    val spec by vm.selectedSpec.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val sourceId by vm.sourceId.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain)
            }
            Text("离线识别模型", color = TextMain, fontSize = TextUnit(18f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "APK 本体仅数 MB，识别模型不打进安装包；首次开启识别时下载到应用私有目录，卸载随 App 清除。",
                color = TextSub,
                fontSize = TextUnit(12.5f, TextUnitType.Sp),
            )

            vm.allModels.forEach { m ->
                ModelOption(
                    spec = m,
                    selected = m.id == spec.id,
                    ready = vm.isReady(m.id),
                    onClick = { vm.select(m.id) },
                )
            }

            SourceCard(currentId = sourceId, onSelect = vm::selectSource, options = vm.sourceOptions)

            Spacer(Modifier.height(4.dp))
            DownloadCard(spec = spec, state = state, onDownload = vm::download)
        }
    }
}

@Composable
private fun ModelOption(spec: AsrModelSpec, selected: Boolean, ready: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) Brand else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.displayName, color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold)
            Text(
                "v${spec.version} · ${spec.sizeBytes / 1024 / 1024}MB · 本地离线",
                color = TextSub,
                fontSize = TextUnit(11.5f, TextUnitType.Sp),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (ready) Icon(Icons.Outlined.CheckCircle, "已就绪", tint = OkGreen)
    }
}

@Composable
private fun DownloadCard(spec: AsrModelSpec, state: ModelState, onDownload: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(spec.displayName, color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold)
            Text("v${spec.version}", color = TextSub, fontSize = TextUnit(12f, TextUnitType.Sp))
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is ModelState.NotExist -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand),
                ) { Text("下载模型（约 ${spec.sizeBytes / 1024 / 1024}MB）") }
            }
            is ModelState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.percent}% · ${fmtMb(state.downloaded)} / ${fmtMb(state.total)}",
                    color = TextSub, fontSize = TextUnit(12f, TextUnitType.Sp),
                )
            }
            ModelState.Extracting -> Text("正在解压模型…", color = Brand, fontSize = TextUnit(13f, TextUnitType.Sp))
            ModelState.Ready -> Text("模型已就绪，可以开始课堂记录", color = OkGreen, fontSize = TextUnit(13f, TextUnitType.Sp))
            is ModelState.Failed -> {
                Text("下载失败：${state.message}", color = RecordRed, fontSize = TextUnit(12.5f, TextUnitType.Sp))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("重试") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "仅模型下载需要联网，识别过程全程离线；建议在 WiFi 下下载。",
            color = TextSub, fontSize = TextUnit(11f, TextUnitType.Sp),
        )
    }
}

@Composable
private fun SourceCard(
    currentId: String,
    onSelect: (String) -> Unit,
    options: List<com.star.shuikebang.asr.DownloadSource.Option>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(vertical = 6.dp),
    ) {
        Text(
            "下载源（国内直连建议选加速镜像）",
            color = TextMain, fontSize = TextUnit(13f, TextUnitType.Sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        options.forEach { o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(o.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(o.label, color = TextMain, fontSize = TextUnit(13f, TextUnitType.Sp))
                    Text(o.hint, color = TextSub, fontSize = TextUnit(10.5f, TextUnitType.Sp), modifier = Modifier.padding(top = 2.dp))
                }
                if (o.id == currentId) Icon(Icons.Outlined.Check, "已选", tint = Brand)
            }
        }
    }
}

private fun fmtMb(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
