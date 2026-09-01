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

    private val selectedId = MutableStateFlow(BuiltinModels.RECOMMENDED_ID)
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

    fun isReady(id: String): Boolean = manager.isReady(id)
}
