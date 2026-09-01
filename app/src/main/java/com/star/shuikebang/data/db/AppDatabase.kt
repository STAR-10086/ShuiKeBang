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
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
