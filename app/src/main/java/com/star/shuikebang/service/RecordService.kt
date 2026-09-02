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
import com.star.shuikebang.asr.AudioPreamp
import com.star.shuikebang.asr.AsrModelSpec
import com.star.shuikebang.asr.BuiltinModels
import com.star.shuikebang.asr.MicGainMode
import com.star.shuikebang.asr.ModelManager
import com.star.shuikebang.asr.ModelPaths
import com.star.shuikebang.asr.ModelState
import com.star.shuikebang.asr.SherpaStreamEngine
import com.star.shuikebang.asr.StreamAsrCallback
import com.star.shuikebang.data.db.ClassRepository
import com.star.shuikebang.data.prefs.AppSettings
import com.star.shuikebang.data.prefs.SettingsRepository
import com.star.shuikebang.data.db.QuestionEntity
import com.star.shuikebang.feedback.Hapticx
import com.star.shuikebang.island.StatusIsland
import com.star.shuikebang.nlp.DetectSensitivity
import com.star.shuikebang.nlp.QuestionDetection
import com.star.shuikebang.nlp.QuestionDetector
import com.star.shuikebang.nlp.QuestionMlClassifier
import com.star.shuikebang.nlp.SelfAnswerDetector
import com.star.shuikebang.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class RecordService : LifecycleService() {

    private lateinit var repo: ClassRepository
    private lateinit var models: ModelManager
    private lateinit var detector: QuestionDetector
    private lateinit var island: StatusIsland

    private var engine: SherpaStreamEngine? = null
    private var capture: AudioCapture? = null
    private var preamp: AudioPreamp? = null
    private var spec: AsrModelSpec? = null
    private var tickerJob: Job? = null
    private var startJob: Job? = null
    private lateinit var settingsRepo: SettingsRepository
    @Volatile private var cfg: AppSettings = AppSettings()

    // 提问去重冷却：同一句 4 秒内不重复提醒
    private var lastQuestionText: String = ""
    private var lastQuestionTs: Long = 0L

    // 在途入库任务：停止录音时必须全部 join，确保最后一句真正落库
    private val writeJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())

    // 断句处理统一在单并行度上下文串行执行：延迟确认门状态与入库都无需额外加锁
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val gateDispatcher = Dispatchers.IO.limitedParallelism(1)

    // 一条等待“二次确认”的 L2 提问
    private class PendingQuestion(
        val ts: Long,
        val clean: String,
        val detection: QuestionDetection,
    )
    private var pending: PendingQuestion? = null
    private var confirmJob: Job? = null

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
        val amp = AudioPreamp(MicGainMode.of(cfg.micGainId)).also { it.reset() }
        preamp = amp
        val cap = AudioCapture(preamp = amp, onSamples = { samples, sr ->
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

    // ---------------- 断句定稿（串行） ----------------

    private fun handleFinal(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        RecSession.update { it.copy(partial = "") }
        val detection = detector.detect(clean)

        // 全部断句处理进入单并行度队列，保证“待定—撤销/确认”严格按说话顺序发生
        val job = lifecycleScope.launch(gateDispatcher) {
            val sid = RecSession.state.value.sessionId ?: return@launch

            // 1) 上一条 L2 正在待定：用当前句判断老师是否在自问自答
            val pend = pending
            if (pend != null) {
                if (cfg.confirmQuestion && SelfAnswerDetector.isSelfAnswer(pend.clean, clean)) {
                    revokePending(pend, sid)      // 自答 → 撤销，按普通讲课处理
                } else {
                    confirmPending(sid)           // 不是自答 → 上一条提前确认为提问
                }
            }

            // 2) 当前句分流
            when {
                detection == null -> addPlainLine(sid, now, clean)
                detection.level == 1 && !cfg.showL1Suspect -> addPlainLine(sid, now, clean)
                detection.level == 2 && cfg.confirmQuestion -> armPending(sid, now, clean, detection)
                else -> commitQuestion(sid, now, clean, detection)
            }
        }
        trackWrite(job)
    }

    /** 普通讲课行：直接落库 + 上屏 */
    private suspend fun addPlainLine(sid: Long, ts: Long, text: String) {
        repo.addTranscript(sid, ts, text)
        RecSession.update { it.copy(lines = it.lines + UtteranceLine(ts, text, 0)) }
    }

    /** L2 进入待定：先以普通行上屏，延迟窗口内若自答则撤销，否则升级为提问 */
    private fun armPending(sid: Long, ts: Long, clean: String, det: QuestionDetection) {
        RecSession.update { it.copy(lines = it.lines + UtteranceLine(ts, clean, 0)) }
        val p = PendingQuestion(ts, clean, det)
        pending = p
        val job = lifecycleScope.launch(gateDispatcher) {
            delay(CONFIRM_DELAY_MS)
            if (pending === p) confirmPending(sid)
        }
        confirmJob = job
        trackWrite(job)
    }

    /** 撤销待定提问：保持普通行，仅补落库一条普通转录 */
    private suspend fun revokePending(p: PendingQuestion, sid: Long) {
        if (pending !== p) return
        confirmJob?.cancel()
        confirmJob = null
        pending = null
        repo.addTranscript(sid, p.ts, p.clean)
        // 上屏行在 armPending 时已按普通行加入，这里保持 level=0 不变
    }

    /** 确认待定/即时提问：写提问与转录、普通行升级高亮、按需震动/状态岛提醒 */
    private suspend fun confirmPending(sid: Long) {
        val p = pending ?: return
        confirmJob = null
        pending = null
        commitQuestion(sid, p.ts, p.clean, p.detection)
    }

    private suspend fun commitQuestion(
        sid: Long, ts: Long, clean: String, detection: QuestionDetection,
    ) {
        val isDup = clean == lastQuestionText && ts - lastQuestionTs < 4000
        val qid = repo.addQuestion(
            QuestionEntity(
                sessionId = sid,
                ts = ts,
                level = detection.level,
                hitKeyword = detection.hitKeyword,
                rawSentence = detection.rawSentence,
                coreQuestion = detection.coreQuestion,
            )
        )
        repo.addTranscript(sid, ts, clean, qid)
        val question = QuestionEntity(
            id = qid, sessionId = sid, ts = ts, level = detection.level,
            hitKeyword = detection.hitKeyword, rawSentence = detection.rawSentence,
            coreQuestion = detection.coreQuestion,
        )
        // 待定句此前是普通行，这里原地升级为高亮问题行；即时句则新增高亮行
        val existed = RecSession.state.value.lines.any { it.ts == ts }
        if (existed) {
            RecSession.replaceLine(ts) { it.copy(level = detection.level, questionId = qid) }
        } else {
            RecSession.update {
                it.copy(lines = it.lines + UtteranceLine(ts, clean, detection.level, qid))
            }
        }
        RecSession.update { it.copy(questions = it.questions + question) }
        if (!isDup && detection.level == 2) {
            lastQuestionText = clean
            lastQuestionTs = ts
            if (cfg.vibrateOnQuestion) Hapticx.questionAlert(this@RecordService)
            island.onQuestion(qid, detection.coreQuestion, detection.rawSentence)
            RecSession.emitQuestion(question)
        }
    }

    /** 登记入库协程，停止录音时 joinAll 等待其真正完成 */
    private fun trackWrite(job: Job) {
        synchronized(writeJobs) { writeJobs.add(job) }
        job.invokeOnCompletion { synchronized(writeJobs) { writeJobs.remove(job) } }
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
            try {
                // 1) 先停麦克风采集并等采集线程退出（AudioCapture.stop 内部 join），
                //    此后不再有 onSamples/accept/onFinal，避免与 flush 并发操作同一原生 OnlineStream
                capture?.stop()
                capture = null
                tickerJob?.cancel()
                // 2) 采集已停，安全取引擎残句（handleFinal 会把处理排队进 gateDispatcher）
                val residual = engine?.flush()
                if (!residual.isNullOrBlank()) handleFinal(residual)
                // 3) 排在残句处理之后：若仍有待定提问，录音已结束、不会再有自答，立即确认
                val tail = lifecycleScope.launch(gateDispatcher) {
                    if (pending != null) confirmPending(RecSession.state.value.sessionId ?: return@launch)
                }
                trackWrite(tail)
                // 4) 等待所有入库/确认任务真正落库（替代固定 delay，避免最后一句丢失/questionCount 少算）
                synchronized(writeJobs) { writeJobs.toList() }.joinAll()
                // 5) 全部写完再结束会话；引擎在 finally 的 stopSelfClean 中释放
                RecSession.state.value.sessionId?.let {
                    repo.finishSession(it, System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e(TAG, "stop failed", t)
            } finally {
                stopSelfClean()
            }
        }
    }

    private fun stopSelfClean() {
        tickerJob?.cancel()
        startJob?.cancel(); startJob = null
        confirmJob?.cancel(); confirmJob = null; pending = null
        capture?.stop(); capture = null
        preamp = null
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

        /** L2 提问二次确认窗口：真实提问老师会停顿等学生，延迟 2.2s 再提醒不影响体验，却能拦住自问自答 */
        private const val CONFIRM_DELAY_MS = 2200L

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
