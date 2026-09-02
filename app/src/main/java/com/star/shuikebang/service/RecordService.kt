package com.star.shuikebang.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.star.shuikebang.asr.AudioCapture
import com.star.shuikebang.asr.AsrModelSpec
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.asr.ModelPaths
import com.star.shuikebang.asr.ModelState
import com.star.shuikebang.asr.SherpaStreamEngine
import com.star.shuikebang.asr.StreamAsrCallback
import com.star.shuikebang.data.db.ClassRepository
import com.star.shuikebang.data.prefs.AppSettings
import com.star.shuikebang.data.prefs.SettingsRepository
import com.star.shuikebang.data.db.QuestionEntity
import com.star.shuikebang.data.db.TranscriptEntity
import com.star.shuikebang.feedback.Hapticx
import com.star.shuikebang.island.StatusIsland
import com.star.shuikebang.nlp.DetectSensitivity
import com.star.shuikebang.nlp.QuestionDetector
import com.star.shuikebang.nlp.QuestionMlClassifier
import com.star.shuikebang.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecordService : LifecycleService() {

    private lateinit var repo: ClassRepository
    private lateinit var models: ModelManager
    private lateinit var detector: QuestionDetector
    private lateinit var island: StatusIsland

    private var engine: SherpaStreamEngine? = null
    private var capture: AudioCapture? = null
    private var spec: AsrModelSpec? = null
    private var tickerJob: Job? = null
    private var startJob: Job? = null
    private lateinit var settingsRepo: SettingsRepository
    @Volatile private var cfg: AppSettings = AppSettings()

    // 提问去重冷却：同一句 4 秒内不重复提醒
    private var lastQuestionText: String = ""
    private var lastQuestionTs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        repo = ClassRepository.get(this)
        models = ModelManager.get(this)
        // 加载约 35KB 的极小提问分类器（失败返回 null，检测器自动回退纯规则）
        val ml = QuestionMlClassifier.fromAssets(this)
        detector = QuestionDetector(DetectSensitivity.NORMAL, ml)
        island = StatusIsland(this)
        settingsRepo = SettingsRepository.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: BuiltinModels.RECOMMENDED_ID
                startRecording(modelId)
            }
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    // ---------------- 开始 ----------------

    private fun startRecording(modelId: String) {
        if (RecSession.state.value.recording || RecSession.state.value.starting) return
        RecSession.update { it.copy(starting = true, error = null) }

        // 先以前台服务身份存活（microphone 类型）
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(
            this, FGS_ID, island.fgsNotifier().build(0), type,
        )

        startJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                cfg = settingsRepo.snapshot()
                detector.setSensitivity(cfg.sensitivity)
                island.overlayEnabled = cfg.overlayCapsule
                val chosen = BuiltinModels.byId(modelId)
                spec = chosen
                // 模型未就绪则下载，并把进度桥接到录制页（避免界面卡在无反馈的"记录中"）
                if (!models.isReady(chosen)) {
                    val bridge = launch {
                        models.stateFlow(chosen.id).collect { st ->
                            val msg = when (st) {
                                is ModelState.Downloading -> "正在下载识别模型 ${st.percent}%"
                                ModelState.Extracting -> "正在解压模型…"
                                else -> "正在准备识别模型…"
                            }
                            RecSession.update { it.copy(prepareMsg = msg) }
                        }
                    }
                    models.ensureModel(chosen, cfg.downloadSourceId)
                    bridge.cancel()
                }
                RecSession.update { it.copy(prepareMsg = "正在加载识别引擎…") }
                val paths: ModelPaths = models.modelPaths(chosen)
                val eng = SherpaStreamEngine(chosen, paths).also { engine = it }
                if (!eng.init()) error("识别引擎初始化失败，请在模型页删除后重新下载")
                eng.start()

                val now = System.currentTimeMillis()
                val title = TimeFmt.autoSessionTitle(now)
                val sid = repo.startSession(title, "mix", now)

                RecSession.update {
                    it.copy(
                        recording = true, starting = false, prepareMsg = null, sessionId = sid,
                        startedAt = now, durationSec = 0, lines = emptyList(),
                        partial = "", questions = emptyList(), engineReady = true,
                    )
                }
                island.onStart(title)
                startTicker(title)
                startCapture(eng)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e(TAG, "start failed", t)
                RecSession.update {
                    it.copy(
                        starting = false, recording = false, engineReady = false,
                        prepareMsg = null, error = t.message ?: "启动失败",
                    )
                }
                stopSelfClean()
            }
        }
    }

    private fun startCapture(eng: SherpaStreamEngine) {
        val cap = AudioCapture(onSamples = { samples, sr ->
            eng.accept(samples, sr, object : StreamAsrCallback {
                override fun onPartial(text: String) {
                    RecSession.update { it.copy(partial = text) }
                }

                override fun onFinal(text: String) = handleFinal(text)

                override fun onError(t: Throwable) {
                    Log.e(TAG, "asr error", t)
                }
            })
        })
        if (!cap.start()) {
            RecSession.update { it.copy(error = "麦克风启动失败") }
            stopSelfClean()
            return
        }
        capture = cap
    }

    // ---------------- 断句定稿 ----------------

    private fun handleFinal(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        RecSession.update { it.copy(partial = "") }

        val detection = detector.detect(clean)
        val sid = RecSession.state.value.sessionId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            // 用户关闭 L1 预警时，一级疑似按普通讲课行处理，不进提问列表
            if (detection == null || (detection.level == 1 && !cfg.showL1Suspect)) {
                repo.addTranscript(sid, now, clean)
                RecSession.update {
                    it.copy(lines = it.lines + UtteranceLine(now, clean, 0))
                }
                return@launch
            }

            // 冷却去重
            val isDup = clean == lastQuestionText && now - lastQuestionTs < 4000
            val qid = repo.addQuestion(
                QuestionEntity(
                    sessionId = sid,
                    ts = now,
                    level = detection.level,
                    hitKeyword = detection.hitKeyword,
                    rawSentence = detection.rawSentence,
                    coreQuestion = detection.coreQuestion,
                )
            )
            repo.addTranscript(sid, now, clean, qid)
            val question = QuestionEntity(
                id = qid, sessionId = sid, ts = now, level = detection.level,
                hitKeyword = detection.hitKeyword, rawSentence = detection.rawSentence,
                coreQuestion = detection.coreQuestion,
            )
            RecSession.update {
                it.copy(
                    lines = it.lines + UtteranceLine(now, clean, detection.level, qid),
                    questions = it.questions + question,
                )
            }
            if (!isDup && detection.level == 2) {
                lastQuestionText = clean
                lastQuestionTs = now
                if (cfg.vibrateOnQuestion) Hapticx.questionAlert(this@RecordService)
                island.onQuestion(qid, detection.coreQuestion, detection.rawSentence)
                RecSession.emitQuestion(question)
            }
        }
    }

    // ---------------- 计时 ----------------

    private fun startTicker(title: String) {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            val start = RecSession.state.value.startedAt
            while (true) {
                delay(1000)
                val sec = ((System.currentTimeMillis() - start) / 1000).toInt()
                RecSession.update { it.copy(durationSec = sec) }
                island.onTick(sec, title)
            }
        }
    }

    // ---------------- 停止 ----------------

    private fun stopRecording() {
        lifecycleScope.launch(Dispatchers.IO) {
            val residual = engine?.flush()
            if (!residual.isNullOrBlank()) handleFinal(residual)
            delay(150) // 等最后的入库完成
            RecSession.state.value.sessionId?.let {
                repo.finishSession(it, System.currentTimeMillis())
            }
            stopSelfClean()
        }
    }

    private fun stopSelfClean() {
        tickerJob?.cancel()
        startJob?.cancel(); startJob = null
        capture?.stop(); capture = null
        engine?.release(); engine = null
        island.onStop()
        RecSession.update {
            it.copy(recording = false, starting = false, engineReady = false, prepareMsg = null)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        capture?.stop()
        engine?.release()
        island.onStop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordService"
        private const val FGS_ID = 1001
        private const val ACTION_START = "com.star.shuikebang.START"
        private const val ACTION_STOP = "com.star.shuikebang.STOP"
        private const val EXTRA_MODEL_ID = "model_id"

        fun start(context: Context, modelId: String = BuiltinModels.RECOMMENDED_ID) {
            val intent = Intent(context, RecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
