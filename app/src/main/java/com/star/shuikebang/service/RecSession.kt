package com.star.shuikebang.service

import com.star.shuikebang.data.db.QuestionEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 实时转录列表中的一行；level: 0 普通 / 1 可能被提问 / 2 确认问题 */
data class UtteranceLine(
    val ts: Long,
    val text: String,
    val level: Int = 0,
    val questionId: Long? = null,
)

data class RecUiState(
    val recording: Boolean = false,
    val starting: Boolean = false,
    /** 引擎/模型准备阶段的提示（如"正在下载识别模型 45%"），recording=true 后清空 */
    val prepareMsg: String? = null,
    val sessionId: Long? = null,
    val startedAt: Long = 0,
    val durationSec: Int = 0,
    val lines: List<UtteranceLine> = emptyList(),
    val partial: String = "",
    val questions: List<QuestionEntity> = emptyList(),
    val engineReady: Boolean = false,
    val error: String? = null,
)

/**
 * 录音中的全局可观察状态（Service 与 Compose UI 共享的单一事实源）。
 */
object RecSession {

    private val _state = MutableStateFlow(RecUiState())
    val state: StateFlow<RecUiState> = _state.asStateFlow()

    /** 检测到确认提问的事件流（用于 UI 震动反馈/自动滚动/瞬时提示） */
    private val _questionEvents = MutableSharedFlow<QuestionEntity>(extraBufferCapacity = 8)
    val questionEvents: SharedFlow<QuestionEntity> = _questionEvents.asSharedFlow()

    fun update(block: (RecUiState) -> RecUiState) {
        _state.value = block(_state.value)
    }

    fun reset() {
        _state.value = RecUiState()
    }

    suspend fun emitQuestion(q: QuestionEntity) {
        _questionEvents.emit(q)
    }
}
