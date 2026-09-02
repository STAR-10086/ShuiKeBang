package com.star.shuikebang.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM sessions ORDER BY startTs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY ts ASC, id ASC")
    fun observeBySession(sessionId: Long): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY ts ASC, id ASC")
    suspend fun listBySession(sessionId: Long): List<TranscriptEntity>
}

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity): Long

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY ts ASC, id ASC")
    fun observeBySession(sessionId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    /** 按问题 id 反查所属会话 id（点击提问通知跳转用） */
    @Query("SELECT sessionId FROM questions WHERE id = :questionId LIMIT 1")
    suspend fun sessionOfQuestion(questionId: Long): Long?

    @Query(
        """
        SELECT * FROM questions
        WHERE sessionId = :sessionId AND level = 2
        ORDER BY ts DESC LIMIT 1
        """
    )
    suspend fun latestConfirmed(sessionId: Long): QuestionEntity?

    @Query(
        """
        SELECT * FROM questions
        WHERE coreQuestion LIKE '%' || :kw || '%'
           OR rawSentence LIKE '%' || :kw || '%'
        ORDER BY ts DESC LIMIT 50
        """
    )
    suspend fun search(kw: String): List<QuestionEntity>
}
