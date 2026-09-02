package com.star.shuikebang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.util.TimeFmt

/**
 * 录音页顶部状态条。
 * @param preparing 模型下载/引擎加载阶段：显示蓝色"准备中"，不显示计时
 */
@Composable
fun RecStatusBar(
    durationSec: Int,
    modifier: Modifier = Modifier,
    preparing: Boolean = false,
    prepareMsg: String? = null,
    paused: Boolean = false,
) {
    val bg = when {
        preparing -> Brand
        paused -> Color(0xFFF0A830)
        else -> RecordRed
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preparing) {
                CircularProgressIndicator(
                    color = CardWhite,
                    strokeWidth = 1.8.dp,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "  ${prepareMsg ?: "正在准备…"}",
                    color = CardWhite,
                    fontSize = TextUnit(14f, TextUnitType.Sp),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                PulseDot(CardWhite, 8)
                Text(
                    if (paused) "  已暂停" else "  正在记录中",
                    color = CardWhite,
                    fontSize = TextUnit(15f, TextUnitType.Sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!preparing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!paused) WaveBars(CardWhite)
                Text(
                    "  ${TimeFmt.duration(durationSec)}",
                    color = CardWhite,
                    fontSize = TextUnit(15f, TextUnitType.Sp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
