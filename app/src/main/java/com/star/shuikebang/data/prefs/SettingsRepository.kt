package com.star.shuikebang.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.star.shuikebang.nlp.DetectSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 全局应用设置（纯本地，DataStore 持久化） */
data class AppSettings(
    /** 检测到老师提问时是否震动 */
    val vibrateOnQuestion: Boolean = true,
    /** 提问检测灵敏度 */
    val sensitivity: DetectSensitivity = DetectSensitivity.NORMAL,
    /** 是否启用 L2 自绘悬浮胶囊（厂商岛不可用时），默认关闭 */
    val overlayCapsule: Boolean = false,
    /** 是否把 L1「可能被提问」也沉淀进提问列表，默认开启 */
    val showL1Suspect: Boolean = true,
    /** 模型下载源 id，见 [com.star.shuikebang.asr.DownloadSource] */
    val downloadSourceId: String = "auto",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(context: Context) {

    private val ds = context.applicationContext.dataStore

    val flow: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            vibrateOnQuestion = p[K_VIBRATE] ?: true,
            sensitivity = runCatching {
                DetectSensitivity.valueOf(p[K_SENSITIVITY] ?: DetectSensitivity.NORMAL.name)
            }.getOrDefault(DetectSensitivity.NORMAL),
            overlayCapsule = p[K_OVERLAY] ?: false,
            showL1Suspect = p[K_SHOW_L1] ?: true,
            downloadSourceId = p[K_SOURCE] ?: "auto",
        )
    }

    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun setVibrate(v: Boolean) = ds.edit { it[K_VIBRATE] = v }
    suspend fun setSensitivity(s: DetectSensitivity) = ds.edit { it[K_SENSITIVITY] = s.name }
    suspend fun setOverlay(v: Boolean) = ds.edit { it[K_OVERLAY] = v }
    suspend fun setShowL1(v: Boolean) = ds.edit { it[K_SHOW_L1] = v }
    suspend fun setDownloadSource(id: String) = ds.edit { it[K_SOURCE] = id }

    companion object {
        private val K_VIBRATE = booleanPreferencesKey("vibrate_on_question")
        val K_SENSITIVITY = stringPreferencesKey("detect_sensitivity")
        val K_OVERLAY = booleanPreferencesKey("overlay_capsule")
        val K_SHOW_L1 = booleanPreferencesKey("show_l1_suspect")
        val K_SOURCE = stringPreferencesKey("download_source")

        @Volatile
        private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
