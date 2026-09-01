package com.star.shuikebang.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一节课堂记录 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                 // 默认按时间命名，可手动重命名
    val startTs: Long,                 // 开始时间戳 ms
    var endTs: Long? = null,           // 结束时间戳
    var durationSec: Int = 0,          // 录音时长
    val lang: String = "zh",           // zh / en / mix
    var questionCount: Int = 0,
    var finished: Boolean = false,
)

/** 一行转录文本（普通讲课行；提问行通过 questionId 关联） */
@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val text: String,
    val questionId: Long? = null,
)

/**
 * 一条被提取的问题
 * level: 1 = 可能被提问（点名/祈使预警），2 = 问题回溯（确认问句）
 */
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val level: Int,
    val hitKeyword: String? = null,    // L1 命中的关键词，如「回答一下」
    val rawSentence: String,           // 原句
    val coreQuestion: String,          // 剥除口头禅后的核心问题
    var copied: Boolean = false,
)
