package com.star.shuikebang.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.MicGainMode
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
    /** 是否启用 L2 自绘悬浮窗（可拖动/展开/操作）；默认开启，未授予悬浮窗权限时自动降级为通知 */
    val overlayCapsule: Boolean = true,
    /** 是否把 L1「可能被提问」也沉淀进提问列表，默认开启 */
    val showL1Suspect: Boolean = true,
    /** 模型下载源 id，见 [com.star.shuikebang.asr.DownloadSource] */
    val downloadSourceId: String = "auto",
    /** 麦克风增益档位 id，见 [com.star.shuikebang.asr.MicGainMode]，默认自动 */
    val micGainId: String = MicGainMode.AUTO.id,
    /** L2 提问二次确认：短暂延迟，若老师紧接着自答则撤销提醒，抑制自问自答误报，默认开启 */
    val confirmQuestion: Boolean = true,

    // —— 模型选择（用户在模型页选定后持久化，开始录音时明确传给识别服务，不再写死默认双语模型）——
    /** 当前选用的离线识别模型 id，见 [com.star.shuikebang.asr.BuiltinModels] */
    val selectedModelId: String = BuiltinModels.RECOMMENDED_ID,

    /**
     * 是否启用 L1 厂商原生岛（小米超级岛 / vivo 原子岛）。
     * 两家都需要 App 上架后向厂商申请展示授权，未授权时调用静默失败，因此默认关闭；
     * 等取得授权后再在设置里打开，未开启时统一走 L2 悬浮窗 / L3 前台通知。
     */
    val vendorIsland: Boolean = false,

    // —— AI 解答（用户自带端点与 Key，App 不内置任何 API/Key）——
    /** OpenAI 兼容端点 base url，填到 /v1，例如 https://api.openai.com/v1；留空则 AI 解答不可用 */
    val aiBaseUrl: String = "",
    /** 用户自填 API Key，仅存本机 DataStore；本地 Ollama/LM Studio 可留空 */
    val aiApiKey: String = "",
    /** 模型名，例如 gpt-4o-mini / deepseek-chat / qwen-plus */
    val aiModel: String = "gpt-4o-mini",

    /** 是否已向用户引导过悬浮窗权限（避免每次开始录音都弹） */
    val overlayGuideShown: Boolean = false,
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
            overlayCapsule = p[K_OVERLAY] ?: true,
            showL1Suspect = p[K_SHOW_L1] ?: true,
            downloadSourceId = p[K_SOURCE] ?: "auto",
            micGainId = p[K_MIC_GAIN] ?: MicGainMode.AUTO.id,
            confirmQuestion = p[K_CONFIRM] ?: true,
            selectedModelId = p[K_SELECTED_MODEL] ?: BuiltinModels.RECOMMENDED_ID,
            vendorIsland = p[K_VENDOR_ISLAND] ?: false,
            aiBaseUrl = p[K_AI_BASE_URL] ?: "",
            aiApiKey = p[K_AI_API_KEY] ?: "",
            aiModel = p[K_AI_MODEL] ?: "gpt-4o-mini",
            overlayGuideShown = p[K_OVERLAY_GUIDE] ?: false,
        )
    }

    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun setVibrate(v: Boolean) = ds.edit { it[K_VIBRATE] = v }
    suspend fun setSensitivity(s: DetectSensitivity) = ds.edit { it[K_SENSITIVITY] = s.name }
    suspend fun setOverlay(v: Boolean) = ds.edit { it[K_OVERLAY] = v }
    suspend fun setShowL1(v: Boolean) = ds.edit { it[K_SHOW_L1] = v }
    suspend fun setDownloadSource(id: String) = ds.edit { it[K_SOURCE] = id }
    suspend fun setMicGain(id: String) = ds.edit { it[K_MIC_GAIN] = id }
    suspend fun setConfirmQuestion(v: Boolean) = ds.edit { it[K_CONFIRM] = v }
    suspend fun setSelectedModel(id: String) = ds.edit { it[K_SELECTED_MODEL] = id }
    suspend fun setVendorIsland(v: Boolean) = ds.edit { it[K_VENDOR_ISLAND] = v }
    suspend fun setAiBaseUrl(v: String) = ds.edit { it[K_AI_BASE_URL] = v.trim() }
    suspend fun setAiApiKey(v: String) = ds.edit { it[K_AI_API_KEY] = v.trim() }
    suspend fun setAiModel(v: String) = ds.edit { it[K_AI_MODEL] = v.trim() }
    suspend fun setOverlayGuideShown(v: Boolean) = ds.edit { it[K_OVERLAY_GUIDE] = v }

    companion object {
        private val K_VIBRATE = booleanPreferencesKey("vibrate_on_question")
        val K_SENSITIVITY = stringPreferencesKey("detect_sensitivity")
        private val K_OVERLAY = booleanPreferencesKey("overlay_capsule")
        private val K_SHOW_L1 = booleanPreferencesKey("show_l1_suspect")
        private val K_SOURCE = stringPreferencesKey("download_source")
        private val K_MIC_GAIN = stringPreferencesKey("mic_gain")
        private val K_CONFIRM = booleanPreferencesKey("confirm_question")
        private val K_SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        private val K_VENDOR_ISLAND = booleanPreferencesKey("vendor_island")
        private val K_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        private val K_AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val K_AI_MODEL = stringPreferencesKey("ai_model")
        private val K_OVERLAY_GUIDE = booleanPreferencesKey("overlay_guide_shown")

        @Volatile
        private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
