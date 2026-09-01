package com.star.shuikebang.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 课堂数据仓库：会话 / 转录 / 提问 的统一读写入口
 */
class ClassRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val sessionDao = db.sessionDao()
    private val transcriptDao = db.transcriptDao()
    private val questionDao = db.questionDao()

    // ---------- Session ----------

    suspend fun startSession(title: String, lang: String, startTs: Long): Long =
        sessionDao.insert(SessionEntity(title = title, startTs = startTs, lang = lang))

    suspend fun finishSession(id: Long, endTs: Long) {
        val s = sessionDao.getById(id) ?: return
        val count = questionDao.countBySession(id)
        sessionDao.update(
            s.copy(
                endTs = endTs,
                durationSec = ((endTs - s.startTs) / 1000).toInt().coerceAtLeast(0),
                questionCount = count,
                finished = true,
            )
        )
    }

    suspend fun renameSession(id: Long, title: String) = sessionDao.rename(id, title)

    suspend fun deleteSessions(ids: List<Long>) = sessionDao.deleteByIds(ids)

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getById(id)

    // ---------- Transcript ----------

    suspend fun addTranscript(sessionId: Long, ts: Long, text: String, questionId: Long? = null): Long =
        transcriptDao.insert(TranscriptEntity(sessionId = sessionId, ts = ts, text = text, questionId = questionId))

    fun observeTranscripts(sessionId: Long): Flow<List<TranscriptEntity>> =
        transcriptDao.observeBySession(sessionId)

    // ---------- Question ----------

    suspend fun addQuestion(question: QuestionEntity): Long = questionDao.insert(question)

    fun observeQuestions(sessionId: Long): Flow<List<QuestionEntity>> =
        questionDao.observeBySession(sessionId)

    suspend fun markCopied(question: QuestionEntity) =
        questionDao.update(question.copy(copied = true))

    suspend fun fullTranscriptText(sessionId: Long): String =
        transcriptDao.listBySession(sessionId).joinToString("\n") { it.text }

    companion object {
        @Volatile
        private var instance: ClassRepository? = null

        fun get(context: Context): ClassRepository =
            instance ?: synchronized(this) {
                instance ?: ClassRepository(context.applicationContext).also { instance = it }
            }
    }
}
