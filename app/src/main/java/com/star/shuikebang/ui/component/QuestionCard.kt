package com.star.shuikebang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.shuikebang.data.db.QuestionEntity
import com.star.shuikebang.ui.theme.Brand
import com.star.shuikebang.ui.theme.RecordLine
import com.star.shuikebang.ui.theme.RecordRed
import com.star.shuikebang.ui.theme.RecordSoft
import com.star.shuikebang.ui.theme.TextSub
import com.star.shuikebang.ui.theme.WarnOrange
import com.star.shuikebang.util.TimeFmt

/** 老师提问卡片：两级结构（可能被提问 / 问题回溯） */
@Composable
fun QuestionCard(
    question: QuestionEntity,
    onCopy: (QuestionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConfirmed = question.level == 2
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecordSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniTag(
                if (isConfirmed) "老师提问" else "可能被提问",
                if (isConfirmed) RecordRed else WarnOrange,
                androidx.compose.ui.graphics.Color.White,
            )
            val kw = question.hitKeyword
            if (!kw.isNullOrBlank()) {
                Text(
                    "  检测到关键词「$kw」",
                    color = if (isConfirmed) RecordRed else WarnOrange,
                    fontSize = TextUnit(11f, TextUnitType.Sp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "问题回溯",
                    color = RecordRed,
                    fontSize = TextUnit(11f, TextUnitType.Sp),
                )
                Text(
                    question.coreQuestion,
                    color = androidx.compose.ui.graphics.Color(0xFF1B1E24),
                    fontSize = TextUnit(15f, TextUnitType.Sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "${TimeFmt.stamp(question.ts)} ${question.rawSentence}",
                    color = TextSub,
                    fontSize = TextUnit(11f, TextUnitType.Sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = { onCopy(question) }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制问题", tint = Brand)
            }
        }
    }
}
