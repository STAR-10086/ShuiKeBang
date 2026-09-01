package com.star.shuikebang.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.star.shuikebang.data.prefs.AppSettings
import com.star.shuikebang.data.prefs.SettingsRepository
import com.star.shuikebang.nlp.DetectSensitivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository.get(app)

    val settings: StateFlow<AppSettings> =
        repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setVibrate(v: Boolean) = viewModelScope.launch { repo.setVibrate(v) }
    fun setSensitivity(s: DetectSensitivity) = viewModelScope.launch { repo.setSensitivity(s) }
    fun setOverlay(v: Boolean) = viewModelScope.launch { repo.setOverlay(v) }
    fun setShowL1(v: Boolean) = viewModelScope.launch { repo.setShowL1(v) }
    fun setSource(id: String) = viewModelScope.launch { repo.setDownloadSource(id) }
}
