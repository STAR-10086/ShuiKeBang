package com.star.shuikebang.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.star.shuikebang.asr.DownloadSource
import com.star.shuikebang.asr.MicGainMode
import com.star.shuikebang.nlp.DetectSensitivity
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.DividerLine
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                Text(
                    "检测灵敏度",
                    color = TextMain, fontSize = TextUnit(14f, TextUnitType.Sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                SensitivityOption("灵敏", "尽量多提示，可能有少量误报", s.sensitivity == DetectSensitivity.HIGH) {
                    vm.setSensitivity(DetectSensitivity.HIGH)
                }
                SensitivityOption("均衡（推荐）", "大多数课堂适用，误报较少", s.sensitivity == DetectSensitivity.NORMAL) {
                    vm.setSensitivity(DetectSensitivity.NORMAL)
                }
                SensitivityOption("保守", "只提示最明确的提问", s.sensitivity == DetectSensitivity.LOW) {
                    vm.setSensitivity(DetectSensitivity.LOW)
                }
                SensitivityOption("关闭检测", "只转写文字，不做提问提醒", s.sensitivity == DetectSensitivity.OFF) {
                    vm.setSensitivity(DetectSensitivity.OFF)
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

            // ---------- 提醒方式 ----------
            SectionTitle("提醒方式")
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
                    subtitle = "切到其他应用也能悬浮显示状态与最近提问，可拖动展开、暂停/继续/结束（需悬浮窗权限）",
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

            // ---------- 录音增益 ----------
            SectionTitle("录音增益")
            GroupCard {
                MicGainMode.entries.forEachIndexed { i, g ->
                    SensitivityOption(g.label, g.hint, s.micGainId == g.id) {
                        vm.setMicGain(g.id)
                    }
                    if (i != MicGainMode.entries.lastIndex) CellDivider()
                }
            }

            // ---------- 模型下载源 ----------
            SectionTitle("模型下载源")
            GroupCard {
                DownloadSource.OPTIONS.forEachIndexed { i, opt ->
                    SensitivityOption(opt.label, opt.hint, s.downloadSourceId == opt.id) {
                        vm.setSource(opt.id)
                    }
                    if (i != DownloadSource.OPTIONS.lastIndex) CellDivider()
                }
            }

            // ---------- 关于 ----------
            SectionTitle("关于与隐私")
            GroupCard {
                Column(Modifier.padding(14.dp)) {
                    Text("水课帮 · v0.1.0", color = TextMain, fontSize = TextUnit(13.5f, TextUnitType.Sp), fontWeight = FontWeight.Medium)
                    Text(
                        "音频全程在本机离线识别，不保存录音、不上传云端、无账号、无广告；仅下载识别模型时联网。",
                        color = TextSub, fontSize = TextUnit(11.5f, TextUnitType.Sp),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
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

@Composable
private fun SensitivityOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = TextUnit(13.5f, TextUnitType.Sp))
            Text(subtitle, color = TextSub, fontSize = TextUnit(11f, TextUnitType.Sp), modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Icon(Icons.Outlined.Check, "已选", tint = Brand)
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
