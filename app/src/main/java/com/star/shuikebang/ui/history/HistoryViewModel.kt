package com.star.shuikebang.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.data.db.AppDatabase
import com.star.shuikebang.data.db.ClassRepository
import com.star.shuikebang.data.db.QuestionEntity
import com.star.shuikebang.data.db.SessionEntity
import com.star.shuikebang.data.db.TranscriptEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ClassRepository.get(app)

    val sessions: StateFlow<List<SessionEntity>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun transcripts(sessionId: Long): StateFlow<List<TranscriptEntity>> =
        repo.observeTranscripts(sessionId).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun questions(sessionId: Long): StateFlow<List<QuestionEntity>> =
        repo.observeQuestions(sessionId).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun delete(session: SessionEntity) = viewModelScope.launch {
        repo.deleteSessions(listOf(session.id))
    }

    fun rename(session: SessionEntity, title: String) = viewModelScope.launch {
        repo.renameSession(session.id, title)
    }

    suspend fun fullText(sessionId: Long): String = repo.fullTranscriptText(sessionId)
}
