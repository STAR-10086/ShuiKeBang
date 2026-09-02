package com.star.shuikebang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.star.shuikebang.ui.theme.BrandSoft
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.DividerLine
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub

/** 录音页底部 Dock：停止 / 复制 / 问AI / 标记 */
@Composable
fun ControlDock(
    onStop: () -> Unit,
    onCopy: () -> Unit,
    onAskAi: () -> Unit,
    onMark: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    onTogglePause: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem(
            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            if (paused) "继续" else "暂停", BrandSoft, TextMain, onTogglePause,
        )
        DockItem(Icons.Outlined.Stop, "停止", RecordRed, CardWhite, onStop, primary = true)
        DockItem(Icons.Outlined.ContentCopy, "复制", BrandSoft, TextMain, onCopy)
        DockItem(Icons.Outlined.AutoAwesome, "问AI", BrandSoft, TextMain, onAskAi)
        DockItem(Icons.Outlined.BookmarkBorder, "标记", BrandSoft, TextMain, onMark)
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (primary) 58.dp else 50.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(if (primary) 28.dp else 23.dp),
            )
        }
        Text(
            label,
            color = TextSub,
            fontSize = TextUnit(11f, TextUnitType.Sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
