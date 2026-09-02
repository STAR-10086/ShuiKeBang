package com.star.shuikebang.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.star.shuikebang.ai.AiProtocol
import com.star.shuikebang.asr.DownloadSource
import com.star.shuikebang.asr.MicGainMode
import com.star.shuikebang.nlp.DetectSensitivity
import com.star.shuikebang.perm.PermissionHelper
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.DividerLine
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import android.widget.Toast

private data class PickerOpt(val id: String, val label: String, val hint: String)
private data class PickerState(
    val title: String,
    val opts: List<PickerOpt>,
    val selectedId: String,
    val onPick: (String) -> Unit,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picker by remember { mutableStateOf<PickerState?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain) }
            Text("设置", color = TextMain, fontSize = TextUnit(18f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 提问检测 ----------
            SectionTitle("提问检测")
            GroupCard {
                OptionRow(
                    "检测灵敏度",
                    sensitivityLabel(s.sensitivity),
                ) {
                    picker = PickerState(
                        "检测灵敏度",
                        listOf(
                            PickerOpt("HIGH", "灵敏", "尽量多提示，可能有少量误报"),
                            PickerOpt("NORMAL", "均衡（推荐）", "大多数课堂适用，误报较少"),
                            PickerOpt("LOW", "保守", "只提示最明确的提问"),
                            PickerOpt("OFF", "关闭检测", "只转写文字，不做提问提醒"),
                        ),
                        s.sensitivity.name,
                    ) { id -> vm.setSensitivity(DetectSensitivity.valueOf(id)) }
                }
                CellDivider()
                SwitchRow(
                    title = "保留“可能被提问”预警",
                    subtitle = "点名、让同学回答时先给一条浅色预警",
                    checked = s.showL1Suspect,
                    onCheckedChange = vm::setShowL1,
                )
                CellDivider()
                SwitchRow(
                    title = "提问二次确认",
                    subtitle = "短暂延迟再提醒；老师紧接着自答时自动撤销，抑制自问自答误报",
                    checked = s.confirmQuestion,
                    onCheckedChange = vm::setConfirmQuestion,
                )
            }

            // ---------- 提醒与悬浮窗 ----------
            SectionTitle("提醒与悬浮窗")
            GroupCard {
                SwitchRow(
                    title = "检测到提问时震动",
                    subtitle = "两段式短促震动，区别于普通通知",
                    checked = s.vibrateOnQuestion,
                    onCheckedChange = vm::setVibrate,
                )
                CellDivider()
                SwitchRow(
                    title = "悬浮控制窗",
                    subtitle = "切到其他应用也能悬浮显示状态/最近提问并操作（需悬浮窗权限）",
                    checked = s.overlayCapsule,
                    onCheckedChange = { want ->
                        if (want && !AndroidSettings.canDrawOverlays(context)) {
                            val intent = Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(intent) }
                        } else {
                            vm.setOverlay(want)
                        }
                    },
                )
            }

            // ---------- 录音与识别 ----------
            SectionTitle("录音与识别")
            GroupCard {
                OptionRow("录音增益", MicGainMode.of(s.micGainId).label) {
                    picker = PickerState(
                        "录音增益",
                        MicGainMode.entries.map { PickerOpt(it.id, it.label, it.hint) },
                        s.micGainId,
                    ) { id -> vm.setMicGain(id) }
                }
                CellDivider()
                val sourceLabel = DownloadSource.OPTIONS.firstOrNull { it.id == s.downloadSourceId }?.label
                    ?: "自动"
                OptionRow("模型下载源", sourceLabel) {
                    picker = PickerState(
                        "模型下载源",
                        DownloadSource.OPTIONS.map { PickerOpt(it.id, it.label, it.hint) },
                        s.downloadSourceId,
                    ) { id -> vm.setSource(id) }
                }
            }

            // ---------- AI 解答 ----------
            SectionTitle("AI 解答")
            GroupCard {
                NavRow(
                    icon = Icons.Outlined.Api,
                    title = "AI 端点与 Key",
                    subtitle = if (AiProtocol.isReady(s.aiBaseUrl, s.aiApiKey))
                        "已配置 · ${s.aiModel.ifBlank { AiProtocol.DEFAULT_MODEL }}"
                    else "未配置 · 使用你自己的兼容端点（App 不内置 Key）",
                    onClick = onOpenAi,
                )
            }

            // ---------- 其他 ----------
            SectionTitle("其他")
            GroupCard {
                NavRow(
                    icon = Icons.Outlined.Security,
                    title = "权限与后台保活",
                    subtitle = "通知权限、忽略电池优化（防录音被系统杀掉）",
                ) {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !PermissionHelper.hasPostNotifications(context) ->
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                        !PermissionHelper.isIgnoringBatteryOptimizations(context) ->
                            PermissionHelper.requestIgnoreBatteryOptimizations(context)

                        else -> Toast.makeText(context, "相关权限已就绪", Toast.LENGTH_SHORT).show()
                    }
                }
                CellDivider()
                NavRow(
                    icon = Icons.Outlined.Info,
                    title = "关于与隐私",
                    subtitle = "版本、功能、开源地址与隐私说明",
                    onClick = onOpenAbout,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // 二级单选弹窗
    picker?.let { state ->
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(state.title) },
            text = {
                Column {
                    state.opts.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    state.onPick(opt.id)
                                    picker = null
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opt.id == state.selectedId, onClick = {
                                state.onPick(opt.id); picker = null
                            })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(opt.label, color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp))
                                Text(opt.hint, color = TextSub, fontSize = TextUnit(11f, TextUnitType.Sp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("关闭") } },
        )
    }
}

private fun sensitivityLabel(s: DetectSensitivity): String = when (s) {
    DetectSensitivity.HIGH -> "灵敏"
    DetectSensitivity.NORMAL -> "均衡（推荐）"
    DetectSensitivity.LOW -> "保守"
    DetectSensitivity.OFF -> "关闭检测"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = TextFaint, fontSize = TextUnit(12f, TextUnitType.Sp),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite),
    ) { content() }
}

/** 点击弹出二级选择的行 */
@Composable
private fun OptionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, color = TextMain, fontSize = TextUnit(13.5f, TextUnitType.Sp),
            modifier = Modifier.weight(1f),
        )
        Text(value, color = TextSub, fontSize = TextUnit(12.5f, TextUnitType.Sp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextSub)
    }
}

/** 跳转到子页面的行 */
@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = TextUnit(13.5f, TextUnitType.Sp))
            Text(subtitle, color = TextSub, fontSize = TextUnit(11f, TextUnitType.Sp), modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextSub)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = TextUnit(13.5f, TextUnitType.Sp))
            Text(subtitle, color = TextSub, fontSize = TextUnit(11f, TextUnitType.Sp), modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CardWhite, checkedTrackColor = Brand),
        )
    }
}

@Composable
private fun CellDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(1.dp)
            .background(DividerLine),
    )
}
