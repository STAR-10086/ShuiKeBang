package com.star.shuikebang.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.asr.AsrModelSpec
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.DownloadSource
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.asr.ModelState
import com.star.shuikebang.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelDownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = ModelManager.get(app)
    private val settings = SettingsRepository.get(app)

    // 选中模型 id 以设置里的持久化值为准（开始录音时读取同一个值，保证“所选即所用”）
    private val selectedId = MutableStateFlow(BuiltinModels.RECOMMENDED_ID)

    init {
        viewModelScope.launch { selectedId.value = settings.snapshot().selectedModelId }
    }

    val selectedSpec: StateFlow<AsrModelSpec> = selectedId
        .map { BuiltinModels.byId(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuiltinModels.SMALL_BILINGUAL)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ModelState> = selectedId
        .flatMapLatest { manager.stateFlow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelState.NotExist)

    /** 当前选择的下载源 id */
    val sourceId: StateFlow<String> = settings.flow
        .map { it.downloadSourceId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadSource.AUTO)

    val allModels = BuiltinModels.ALL
    val sourceOptions = DownloadSource.OPTIONS

    fun select(id: String) {
        selectedId.value = id
        viewModelScope.launch { settings.setSelectedModel(id) }
    }

    fun selectSource(id: String) {
        viewModelScope.launch { settings.setDownloadSource(id) }
    }

    fun download() {
        val spec = BuiltinModels.byId(selectedId.value)
        val src = sourceId.value
        viewModelScope.launch {
            runCatching { manager.ensureModel(spec, src) }
        }
    }

    /** 删除当前选中模型（.ready/文件/断点/临时目录一并清理），状态回到未安装 */
    fun deleteSelected() {
        val spec = BuiltinModels.byId(selectedId.value)
        manager.delete(spec)
    }

    /** 删除后立即重新下载（模型损坏时的恢复入口） */
    fun redownload() {
        val spec = BuiltinModels.byId(selectedId.value)
        val src = sourceId.value
        manager.delete(spec)
        viewModelScope.launch {
            runCatching { manager.ensureModel(spec, src) }
        }
    }

    fun isReady(id: String): Boolean = manager.isReady(id)
}
