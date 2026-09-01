package com.star.shuikebang.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.asr.AsrModelSpec
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.asr.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelDownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = ModelManager.get(app)

    private val selectedId = MutableStateFlow(BuiltinModels.RECOMMENDED_ID)
    val selectedSpec: StateFlow<AsrModelSpec> = selectedId
        .map { BuiltinModels.byId(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuiltinModels.SMALL_BILINGUAL)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ModelState> = selectedId
        .flatMapLatest { manager.stateFlow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelState.NotExist)

    val allModels = BuiltinModels.ALL

    fun select(id: String) {
        selectedId.value = id
    }

    fun download() {
        val spec = BuiltinModels.byId(selectedId.value)
        viewModelScope.launch {
            runCatching { manager.ensureModel(spec) }
        }
    }

    fun isReady(id: String): Boolean = manager.isReady(id)
}
