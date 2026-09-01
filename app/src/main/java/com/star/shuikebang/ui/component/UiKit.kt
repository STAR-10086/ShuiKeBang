package com.star.shuikebang.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.TextSub

/** 录音红点（呼吸动画） */
@Composable
fun PulseDot(color: Color = RecordRed, size: Int = 8) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a",
    )
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

/** 声波条（录音中律动） */
@Composable
fun WaveBars(color: Color = RecordRed, barCount: Int = 5) {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(barCount) { i ->
            val ratio by transition.animateFloat(
                initialValue = 0.35f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(520 + i * 90), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 90),
                ),
                label = "b$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height((14 * ratio).dp.coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** 小标签 */
@Composable
fun MiniTag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
    ) {
        Text(
            text,
            color = fg,
            fontSize = TextUnit(11f, TextUnitType.Sp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 灰色辅助说明 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TextSub,
        fontSize = TextUnit(12f, TextUnitType.Sp),
        modifier = modifier,
    )
}
