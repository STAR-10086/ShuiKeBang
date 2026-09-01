package com.star.shuikebang.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.star.shuikebang.data.db.SessionEntity
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.CardWhite
import com.star.shuikebang.ui.theme.PageBg
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.RecordSoft
import com.star.shuikebang.ui.theme.TextFaint
import com.star.shuikebang.ui.theme.TextMain
import com.star.shuikebang.ui.theme.TextSub
import com.star.shuikebang.util.TimeFmt

@Composable
fun HistoryListScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier.padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = TextMain) }
            Text("历史课堂", color = TextMain, fontSize = TextUnit(18f, TextUnitType.Sp), fontWeight = FontWeight.Bold)
        }

        if (sessions.isEmpty()) {
            EmptyHistory()
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                SessionRow(s, onClick = { onOpen(s.id) }, onDelete = { vm.delete(s) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SessionRow(s: SessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = TextMain, fontSize = TextUnit(14.5f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold)
            Text(
                "${TimeFmt.friendlyDay(s.startTs)} ${TimeFmt.hm(s.startTs)} · ${TimeFmt.duration(s.durationSec)}",
                color = TextSub, fontSize = TextUnit(11.5f, TextUnitType.Sp),
                modifier = Modifier.padding(top = 4.dp),
            )
            if (s.questionCount > 0) {
                Box(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(RecordSoft).padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("提问 ${s.questionCount}", color = RecordRed, fontSize = TextUnit(10.5f, TextUnitType.Sp))
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, "删除", tint = TextFaint)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = TextFaint)
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.History, null, tint = TextFaint, modifier = Modifier.padding(8.dp))
        Text("还没有课堂记录", color = TextSub, fontSize = TextUnit(14f, TextUnitType.Sp))
        Text("开始一次记录后会保存在这里", color = TextFaint, fontSize = TextUnit(12f, TextUnitType.Sp))
    }
}
