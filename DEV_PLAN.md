# 水课帮 Android 版 · 开发规划（Agent 开发执行手册）

> 版本：v0.2 规划稿　最后更新：2026-09-01
> 本应用包名（定稿）：**`com.star.shuikebang`**
> 对标产品：iOS《水课帮-课堂提问助手》v4.0（bundleId `com.aiclassroom.assistant`，本体 1.82MB，使用 Apple 原生 `SFSpeechRecognizer`）
> UI 设计稿：`design/ui-mockup.html`（浏览器直接打开，6 屏高保真 + 设计令牌）
> 原版参考截图：`research/ios_ss1~8.jpg`（App Store 官方图）
>
> **已确认决策**：① 包名 com.star.shuikebang；② ASR 用**真流式**模型（OnlineRecognizer，边说边出字）；③ 模型先用 **GitHub Releases** 分发，R2 备选；④ 状态岛全部走**客户端本地通知**，不接 MiPush / VPush 服务端通道。

---

## 0. 一句话产品定义

大学生上课时把手机放桌上，App 用**麦克风 + 端侧离线 ASR** 实时转写讲课内容，**本地规则判断老师是否在提问**，命中时震动并把问题高亮沉淀；音频不出设备、不保存录音、无账号无云。

### 做（In Scope）
实时离线转写（中/英/中英混合）、两级提问检测与问题回溯、课堂会话本地存储与历史管理、复制/问 AI（系统分享/快捷指令式外发）、录音前台服务保活、状态胶囊（应用内 + 悬浮 + 厂商岛 + 通知四级）、模型动态下载。

### 不做（Out of Scope，防止体积/复杂度膨胀）
不保存音频文件、不导入外部音频、不做云端/大模型总结、不做账号与云同步、不做广告社区分享、不做手表端（iOS 专属，后置）、不做语音合成。

---

## 1. 技术栈（定稿）

| 层 | 选型 | 说明 |
|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose + Material 3 | 单 Activity + Navigation-Compose |
| 最低版本 | minSdk 26（Android 8.0），targetSdk 35 | 覆盖 99% 设备；Android 14/15 单独适配 FGS |
| 架构 | MVVM + StateFlow，单 app module 起步 | `ui / service / asr / nlp / data / island / perm` 分包；复杂后再拆 module |
| 异步 | Kotlin Coroutines + Flow | 音频循环跑专用 Dispatcher |
| 数据库 | Room（SQLite） | 只存文本与时间戳，**不存音频字节** |
| 偏好存储 | DataStore | 语言、灵敏度、岛开关等 |
| 下载 | OkHttp + 自管断点续传 | 仅模型下载使用网络 |
| ASR | **sherpa-onnx（onnxruntime）+ 真流式 Streaming Zipformer（中英双语 small，INT8）** | OnlineRecognizer 边说边出字；模型不打进 APK，首启从 GitHub Release 下载到 `filesDir/models`，整套约 28MB |
| DI | 轻量手动 ServiceLocator（或 Hilt，二选一，建议手动以控体积） | 控包体优先 |
| 构建 | Gradle KTS、R8、ABI 只出 arm64-v8a（x86_64 仅 debug） | 目标 APK 本体 ≤ 8MB |

### 关键事实（已调研核实）
- iOS 版本体仅 1.82MB，是因为直接调用系统 `SFSpeechRecognizer`；**Android 没有等价的系统离线中文识别**，必须自带引擎，这是安卓版"本体小 + 模型动态下发"的根本原因。
- **ASR 选型定稿：真流式 Streaming Zipformer**（sherpa-onnx OnlineRecognizer / Transducer）：
  - 首选 `sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16`：中英双语、可中英混说，INT8 整套（encoder+decoder+joiner+tokens）**约 28MB**，老核 Cortex-A7 RTF≈0.3，中端机余量充足；每喂一帧即出 partial 文本，内置 endpoint 规则自动断句，**不需要 VAD 切段**。
  - 省流备选 `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`（纯中文约 14MB，适合老机/流量敏感）；英文场景另有 en-20M。
  - 后置精度增强：若小模型字准不够，可选大一号 bilingual-2023-02-20，或用非流式 SenseVoice 做 two-pass 修正（230MB，作为可选资源包，不默认下载）。
  - 注意：流式 Zipformer 中文输出基本不带标点，提问检测不依赖标点（靠疑问词/句式），UI 按 endpoint 断句分行；标点恢复列为后置可选项。
- **状态岛全部走客户端本地通知，不接任何 Push 通道**：
  - 小米超级岛/焦点通知：本地 `NotificationManager.notify`，在 `Notification.extras` 放 `miui.focus.param`（JSON，≤3072 字节）与 `miui.focus.pics` 即可，**不需要 MiPush SDK、不需要服务器**；MiPush 只是"进程被杀也能远程刷新岛"的另一条通道，本场景录音时进程本来就活着，用不上。权限仍需发邮件 mipush-permission@xiaomi.com 申请（应用上架后），**代码可先写**：HyperOS3 检测 `Settings.System "notification_focus_protocol"==3`，`ContentResolver.call(Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", …)` 查权限，未开通则静默降级为普通通知/L2/L3。免费。
  - vivo 原子岛/原子通知：官方架构明确"**本地发送通知 或 push 更新通知**"二选一。本地 `notify` 携带 `notification.superx.*` extras：`operation`(0 创建/1 更新/2 结束)、`template`、`baseInfos/infos/shortInfos`、`capsule`（状态栏胶囊：icon/content/颜色/展示秒数）、`island`（OS5 原子岛左右岛）、`clickResp`(PendingIntent)、`scene`。**不需要 VPush SDK、不需要服务器**（VPush 仅用于服务端远程刷新）。注意 `scene` 目前是枚举（打车/外卖/会议 METTING 等），课堂录音最接近 `METTING`，申请权限（邮件 oosyztz@vivo.com，需先上架，7~10 工作日）时需确认可用场景。
- 跨厂商兜底：`SYSTEM_ALERT_WINDOW` 悬浮窗自绘顶部胶囊（dynamicSpot / 小组件盒子的原理）；以及 `foregroundServiceType="microphone"` 前台服务常驻通知（Android 14+ 系统自带麦克风使用指示）。

---

## 2. 总体架构与数据流

```
AudioRecord(16k/mono/16bit, VOICE_RECOGNITION)
   │  每 ~32~100ms 一帧 PCM
   ▼
OnlineRecognizer（Streaming Zipformer，真流式）
   ├── 每帧解码 → partial 文本（边说边出字，当前行实时刷新）
   └── Endpoint 规则检测到句尾停顿 → 定稿整句 isFinal
         │
         ├──► QuestionDetector（规则 NLP，两级判定 + 核心问题提取 + 去重冷却）
         │        ├─ L1 可能被提问（点名/祈使短语）→ 预警
         │        └─ L2 确认问题（疑问结构/句式）→ 红色问题回溯
         │              ├──► 震动 + 提示音(可选)
         │              ├──► 状态胶囊展开（L0/L1/L2/L3 四级）
         │              └──► questions 表
         ├──► transcripts 表 + 转录流 StateFlow → Compose（partial 刷当前行 / final 换行）
         └──► 前台服务通知/胶囊上的计时与状态
```

- 真流式：partial 文本实时刷新当前行，endpoint 断句后定稿换行——不再需要 VAD 先切段（Silero VAD 仅作为后置的降噪/拒识增强选项）。
- 全部链路在手机本地闭环；**唯一网络请求是从 GitHub Release 下载模型**。

---

## 3. 工程目录结构

```
app/src/main/java/com/shuikebang/app/
├── ShuikeApp.kt                  # Application，ServiceLocator 初始化
├── MainActivity.kt               # 单 Activity
├── ui/
│   ├── theme/                    # Color/Type/Shape（对齐 design/ui-mockup.html 令牌）
│   ├── navigation/AppNav.kt
│   ├── record/                   # 屏③④ 实时记录页（核心）
│   ├── idle/                     # 屏① 待机首页
│   ├── model/                    # 屏② 模型下载页
│   ├── history/                  # 屏⑤ 历史列表、屏⑥ 会话详情
│   └── component/                # 胶囊、提问卡片、控制 Dock、权限引导弹窗
├── service/
│   └── RecordService.kt          # 前台服务：录音+ASR+检测+通知，保活主体
├── asr/
│   ├── AudioCapture.kt           # AudioRecord 封装（16k/mono/PCM16）
│   ├── SherpaStreamEngine.kt     # OnlineRecognizer 封装：喂帧/partial/endpoint 断句/重置
│   ├── ModelManager.kt           # GitHub Release 模型清单、下载、断点续传、sha256、版本管理
│   └── AsrModels.kt              # 数据类（模型规格：small-bilingual / zh-14M）
├── nlp/
│   ├── QuestionDetector.kt       # 两级判定主逻辑
│   ├── QuestionRules.kt          # 中/英规则表、停用剥除词表
│   └── TextNorm.kt               # 标点/空白/语气词归一
├── data/
│   ├── db/ (AppDatabase, Entity: Session/Transcript/Question, Dao×3)
│   └── repo/ClassRepository.kt
├── island/
│   ├── StatusNotifier.kt         # 统一入口，按环境选择实现
│   ├── MiFocusNotifier.kt        # L1 小米 miui.focus.param
│   ├── VivoAtomicNotifier.kt     # L1 vivo 原子通知（后期）
│   ├── OverlayCapsule.kt         # L2 WindowManager 悬浮胶囊
│   └── FgsNotifier.kt            # L3 前台服务常驻通知
├── perm/PermissionHelper.kt      # 麦克风/通知/悬浮窗/电池白名单/厂商自启动
├── feedback/Hapticx.kt           # 震动模式
└── util/(时间格式化、剪贴板、系统分享)
```

---

## 4. 模块任务树（Agent 可直接按叶子任务派发）

### M0 工程骨架与静态 UI（先把设计稿变成可点的空壳）
- M0.1 建 Gradle KTS 工程：minSdk26/target35、Compose BOM、Material3、Navigation、Room、OkHttp、sherpa-onnx 依赖占位；ABI `arm64-v8a`（debug 加 x86_64）；R8 开启。
- M0.2 主题令牌 1:1 落地：品牌蓝 `#2E6BE6`、录音红 `#F23C3C`、卡片底 `#FFF3F2`/边框 `#FFDAD7`、浅蓝 `#EAF1FF`、页面 `#F4F6F9`、墨黑 `#1B1E24`；圆角 13~24dp；数字等宽。
- M0.3 六个屏幕静态 Compose：①待机 ②模型下载 ③录音中 ④提问瞬间 ⑤历史列表 ⑥会话详情；假数据走通导航与全部组件（提问卡片、录音状态条、控制 Dock、胶囊、Seg、FAB）。
- 验收：真机/模拟器上六屏与 `ui-mockup.html` 视觉一致，无文字溢出/错位。

### M1 权限 + 模型动态下发
- M1.1 首启权限流：麦克风（RECORD_AUDIO）→ 通知（POST_NOTIFICATIONS, API33+）→ 电池优化白名单（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）→ 悬浮窗（可选，Settings.ACTION_MANAGE_OVERLAY_PERMISSION）→ 厂商自启动设置页引导（按 `Build.MANUFACTURER` 匹配常见 Intent，失败回退应用详情页）。
- M1.2 ModelManager：模型清单 JSON 放 **GitHub Release**（字段：版本、各模型 URL、大小、sha256；R2 为后续备选源，做多源可切换）；下载到 `filesDir/models/streaming-zh-en/`，进度 Flow、断点续传、WiFi 提示、sha256 校验、解压；状态机 NotExist/Downloading(%)/Ready/Failed。
- M1.3 模型就绪检查：点"开始记录"时无模型→跳屏②；卸载自动清除（本来就在私有目录）。
- 验收：飞行模式下除下载外全流程可用；删数据后重新下载正常；模型文件不进 APK（验证 release APK 体积）。

### M2 真流式离线 ASR 链路（项目最大技术风险，优先攻坚）
- M2.1 引入 sherpa-onnx：优先官方 Maven 坐标（以 k2-fsa 官方文档为准），否则用官方 AAR；只保留 arm64-v8a jniLibs（debug 加 x86_64 供模拟器）。
- M2.2 AudioCapture：`MediaRecorder.AudioSource.VOICE_RECOGNITION`、16kHz/mono/PCM16；按 OnlineRecognizer 要求的帧长（如每 100ms ≈1600 samples）喂入；开始/停止/异常恢复（初始化失败、麦克风被占用、录音权限被撤销）。
- M2.3 SherpaStreamEngine：`OnlineRecognizer` 配置 streaming-zipformer-small-bilingual-zh-en INT8（encoder/decoder/joiner/tokens 四件套）；每帧 `decodeStream` 取 `getResult().text` 作为 partial；按 `isEndpoint(endpointConfig)`（rule1/2/3：句尾静音、min-tailing、min-utterance）判定断句，断句时定稿整句并 `reset()`；实例单例、识别线程独占、生命周期与 RecordService 绑定。
- M2.4 模型包：从 GitHub Release 下载 tar.bz2/zip 并解压到 `filesDir/models/streaming-zh-en/`，校验文件数与 sha256；支持切换 zh-14M 省流模型；模型清单 JSON 也放 Release（含版本号/URL/大小/校验值）。
- M2.5 真机跑通"边说边出字"：说话过程中当前行持续刷新，停顿后自动换行定稿；主观延迟（开口到出字）≤300ms。
- 验收（真机）：中英混读整句主观正确率 ≥90%；连续 45 分钟不崩、内存平稳（小模型常驻内存应 <100MB）；飞行模式可用；音频全程内存流转、杀进程后无音频落地。

### M3 录音会话 + 实时 UI + 持久化
- M3.1 Room 三表：
  - `sessions(id, title, startTs, endTs, durationSec, lang, questionCount, createdAt)`
  - `transcripts(id, sessionId, ts, text, questionId?)`
  - `questions(id, sessionId, ts, level, hitKeyword, rawSentence, coreQuestion, copied)`
- M3.2 RecordService：`foregroundServiceType="microphone"` + `FOREGROUND_SERVICE_MICROPHONE` 权限（API34+），可见 Activity 启动；常驻通知（停止 action）；会话开始建 session、结束写 endTs/duration。
- M3.3 录音状态条计时（mm:ss）、转录流逐行刷新（LazyColumn 自动滚底，用户上滑时暂停自动滚）、普通行/提问卡片分流渲染。
- M3.4 停止→保存→回待机；异常崩溃后下次进入提示"恢复上次未正常结束的会话"。
- 验收：切后台/锁屏持续转写不中断（在已授权保活的测试机上）；重启 App 历史可查。

### M4 提问检测 NLP（差异化核心，纯规则、零模型依赖）
- M4.1 TextNorm：全半角/标点归一、去重复语气词、英文小写化与缩写还原。
- M4.2 L1 预警规则：点名/祈使短语表——"请…回答/说一下/讲一下/解释/介绍/谈谈/复述"、"哪位同学"、"谁来/谁能"、"你来…"、"点个同学"；英文 "who can/could you/tell me/explain/describe/anyone"。命中但句子不完整→"可能被提问：检测到「关键词」"。
- M4.3 L2 确认规则：
  - 中文疑问代词/结构：什么、谁、哪(个/里)、怎么、怎样、如何、为什么、为何、多少、几、是否、能否、是不是、有没有、…吗/呢/啊？；句末问号强信号。
  - 英文：Wh-（what/why/how/when/where/which/who/whose/whom）、助动词倒装（do/does/did/is/are/was/were/can/could/would/should/have/has…）、句末 "?"。
- M4.4 核心问题提取：剥前缀（来、那个、请你、请这位同学、哪位同学来、I want you to/can you tell me）与后缀（一下、好吗、啊、呢），保留命题本体（例："来，请这位同学介绍一下麦克斯韦方程组"→"介绍麦克斯韦方程组"）。
- M4.5 去重冷却与灵敏度：60s 内高度相似（包含/编辑距离）不重复提醒；三档灵敏度（高=宽召回/中=默认/低=仅强疑问）；过短噪声过滤（中文<4 字、英文<3 词不提醒）。
- M4.6 命中动作：震动（短促双震模式）、问题卡片脉冲、胶囊展开、写入 questions；提供"复制问题/问 AI（ACTION_SEND 外发文本到豆包/千问等，不内置大模型）/定位原文"。
- 验收：用 10~20 段课堂录音样例（自备，含中文/英文/闲聊噪声）回归，统计命中率/误报率并迭代词表；规则单元测试覆盖。

### M5 历史会话管理
- M5.1 列表（屏⑤）：倒序、课程名（默认"MM月dd日 HH:mm 课堂"，可重命名）、时长、提问条数、最近问题预览、搜索（标题+正文+问题）、左滑/长按删除、多选删除。
- M5.2 详情（屏⑥）：「全部记录 / 提问列表 N」Seg；全部记录按时间流、提问高亮可点；提问列表支持单条复制、问 AI、定位原文；顶部"复制全部/导出文本（本地，不分享到网络）"。
- 验收：千条文本滚动流畅（Paging 或分段查询）；删除会话级联清理 transcripts/questions。

### M6 状态胶囊四级承接 + 保活（重点：小米/vivo）
- L0 应用内胶囊：Compose 顶部，录音中声波+计时，命中展开问题+"有提问"标签（设计稿屏③④），零权限，必做。
- L3 前台服务通知：最先做，全机型兜底；通知上带"停止"；Android14+ 系统麦克风指示天然呈现。
- L2 悬浮胶囊：`TYPE_APPLICATION_OVERLAY` 顶部贴挖孔（默认居中，设置里可手动横向/纵向偏移以适配不同挖孔），录音时常驻、命中展开、点击回 App；与厂商岛互斥（检测到 L1 可用则默认关 L2，避免双胶囊）。
- L1 厂商原生岛（**纯客户端本地通知实现，不集成任何 Push SDK、不依赖服务器**）：
  - 小米：本地 `NotificationManager.notify` + extras 注入 `miui.focus.param`（通用信息模板：小岛=图标+录音状态，大岛=问题文本），同一 notificationId 重复 notify 平滑刷新，timeout/islandTimeout 控制下岛；先做 HyperOS 检测（`notification_focus_protocol`）与 `canShowFocus` 权限查询，未开通权限时自动降级，不报错。**权限邮件申请是上架前的独立事项，不阻塞开发**（材料模板见小米开发者文档 pId=2146）。
  - vivo：本地 notify + extras 注入 `notification.superx.*`（operation 0/1/2 完成创建/更新/结束，template 选基础或左右对称模板，capsule/island 分别承载状态栏胶囊与原子岛，scene 拟用 `METTING` 并在申请时确认）；同样先检测能力、无权限自动降级；**不接 VPush SDK**（其仅用于服务端远程刷新，本场景不需要）。
- 保活矩阵测试：小米（澎湃 2/3）、vivo（OriginOS 5/6）、OPPO、三星、原生各一台：锁屏、切后台、最近任务划掉、息屏 30 分钟的存活与录音连续性；输出各机型"必开设置"引导清单。
- 验收：小米/vivo 测试机上至少 L0+L3 稳定；L2 开权限后全局可见；拿到权限的机器 L1 原生岛正确上岛/刷新/下岛。

### M7 双语、打磨与发布
- 英文/中英混合识别（small-bilingual 单模型支持中英混说，另提供纯中文 zh-14M 省流档切换）、英文提问规则；深色模式；横竖屏/平板（后置）；无障碍。
- 包体积治理：R8 全量、去冗余 so、资源压缩、release APK 实测 ≤8MB；启动耗时、内存、电量曲线（连续 1 节课）。
- 隐私合规：隐私说明（本地处理、不存音频、仅下载模型用网）、权限用途说明、无 INTERNET 业务外发；准备小米/vivo 焦点/原子通知申请材料。

---

## 5. 里程碑（建议顺序，每阶段都可独立演示）

| 阶段 | 产出 | 粗估 |
|---|---|---|
| M0 | 六屏可点空壳（设计落地） | 0.5~1 天 |
| M1 | 权限流 + 模型下载 | 0.5~1 天 |
| M2 | 真机离线转写跑通（**风险闸门，先做 Spike**） | 1.5~3 天 |
| M3 | 前台服务 + 实时转录 + 入库 | 1.5 天 |
| M4 | 提问检测 + 提醒 + 复制/问AI | 1.5~2 天（含样例回归） |
| M5 | 历史管理 | 1 天 |
| M6 | 四级胶囊 + 厂商适配 + 保活矩阵 | 2~3 天 |
| M7 | 双语/深色/体积/合规/真机回归 | 1.5~2 天 |

> 建议第一件事是 M2 的技术 Spike：先写个最小 Demo 验证 sherpa-onnx 真流式模型在目标测试机上的出字延迟、字准、内存与 28MB 模型下载体验，再全面开工。

---

## 6. 主要风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 小流式模型（28MB）字准率不如大模型 | 识别效果 | 先用 small-bilingual 真机回归；不够则提供"高清模型"选项（大一号 bilingual 流式，或 SenseVoice two-pass 修正包，按需下载） |
| 流式中文输出无标点 | 可读性 | 检测/展示不依赖标点，按 endpoint 断句分行；后置可选轻量标点模型（按需下载，不进 APK） |
| 小米/vivo 后台杀录音 | 核心失效 | 前台服务+麦克风类型、电池白名单与自启动引导、厂商设置直达；保活矩阵逐机验证并沉淀指引 |
| 教室混响/学生说话导致误报 | 信任度 | 三档灵敏度、冷却去重、长度阈值；规则表持续回归迭代；不承诺声纹区分说话人 |
| 厂商岛权限需上架+审核，周期不可控 | L1 延期 | L0/L2/L3 先保证体验；L1 做成"检测到能力自动启用"，申请通过即生效，不阻塞主流程 |
| 悬浮窗与系统岛重叠/被国产 ROM 限制 | 视觉 | L1 优先、L2 默认关、提供位置手动校准 |
| Android 14/15 FGS 合规 | 无法后台录音 | 严格声明类型与对应权限、从可见界面启动、不要在后台启动 FGS |
| 合规风险（课堂录音） | 上架 | 全程本地、显著录音状态、不存音频、隐私政策写清；不做任何云端传输 |

---

## 7. 决策记录与遗留问题

### 已确认（v0.2）
1. **包名**：`com.star.shuikebang`。
2. **ASR**：使用**真流式** Streaming Zipformer（首选 small-bilingual-zh-en INT8，整套约 28MB，边说边出字）；非流式 SenseVoice 仅作为后置可选精度包。
3. **模型分发**：先用 **GitHub Releases**（模型文件 + 清单 JSON），后续可平滑切到 Cloudflare R2（ModelManager 预留多源）。
4. **状态岛**：只做**客户端本地通知**（小米 `miui.focus.param` / vivo `notification.superx.*`），**不接 MiPush、不接 VPush、不搭应用服务器**；厂商权限申请与开发解耦，未授权时自动降级到 L2/L3。

### 仍待确认（不阻塞 M0/M1 开工）
1. "问 AI"外发方式：Android 无 iOS 快捷指令，默认走系统分享面板（`ACTION_SEND`，用户选豆包/千问等 App），是否可以？
2. 悬浮胶囊 L2 第一版是否默认开启（需要悬浮窗权限，部分用户敏感）？建议做成设置开关、默认关闭，先用 L0+L3。
3. 目标测试机型号与系统版本（优先适配你手上的小米/vivo 具体机型及 HyperOS / OriginOS 版本号）。
4. GitHub Release 仓库归属（你的 GitHub 账号/组织名），用于预置模型清单兜底 URL。
