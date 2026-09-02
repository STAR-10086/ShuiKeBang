package com.star.shuikebang.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, TranscriptEntity::class, QuestionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shuikebang.db", // 只存文本与时间戳，不存音频
                )
                    // TODO(正式版前)：当前 version=1 尚无历史版本，暂用破坏性迁移兜底；
                    // 之后每次改表都必须新增显式 Migration，并在升 version 时移除对该兜底的依赖，
                    // 否则忘记写 Migration 会直接清空用户全部课堂记录。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
