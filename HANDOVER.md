# 交接文档（给下一个 Agent）

> 最后更新：2026-09-01。本文件是「水课帮 Android」开发接力的唯一权威上下文，读完它 + README + DEV_PLAN 即可无缝接手。用户本机环境为 Windows，执行模式为按需确认、无沙箱。

## 0. 一句话现状

> **【最新见 §9 第三轮进展，与前文冲突处以 §9 为准】** 第三轮已用 GitHub Actions 跑通 CI/CD，**Release v0.1.0 已发布（arm64-v8a / armeabi-v7a / universal 三个 APK）**，并接入约 35KB 字级极小提问分类器降低自问自答误报。


**第一轮代码已推送到 main、模型 Release `asr-models-v1` 已建好（两个 zip 已上传）。** 第二轮根据用户真机/体验反馈完成 5 项改动（历史详情闪退修复、提问检测大幅降误报并支持四档灵敏度、新增设置页、模型下载源可手动选择、README 重写），本地 `compileDebugKotlin + testDebugUnitTest` 已通过、单测扩到 19 个全绿，release APK 已重新打出，**但用户要求先在本地真机自测，确认后再由下一轮执行 git 提交/推送并发 Release（见 §5）。** 模型网络实测：GitHub Release 直连国内超时，ghfast.top / gh-proxy.com 不走代理直连 200 且大小正确（ghproxy.net 已失效移除），默认「镜像优先、官方兜底」。

## 1. 本机环境（已核实，直接用）

- JDK：`E:\games\mc\zulu21.28.85-ca-jdk21.0.0-win_x64`（JDK21，JAVA_HOME 通常已配）
- Android SDK：`D:\apps\AndroidSDK`（platforms android-34、build-tools 34.0.0、platform-tools 齐全）；`local.properties` 已写 `sdk.dir=D\:\\apps\\AndroidSDK`（该文件被 gitignore，clone 后需重建）
- 代理：Clash 类本地代理 `http://127.0.0.1:7897`，访问 GitHub/JitPack/HuggingFace 走它；**国内直连测试要显式 `curl --noproxy '*'`**。`gradle.properties` 已写入该代理，不用可删该段。
- Gradle：仓库自带 wrapper 8.10.2（distribution 走腾讯云镜像）；本机无独立 gradle/Android Studio，全部命令行构建。
- git 2.50.1，全局用户 STAR-10086 / hejunxing2006@gmail.com；**`gh` CLI 已登录 STAR-10086（token 含 repo scope，git 走 ssh）**，对 GitHub 写操作优先用 `gh`，命令前设 `$env:HTTPS_PROXY='http://127.0.0.1:7897'`。
- PowerShell 注意：不要用 `&&`；长命令前台 15s 会自动转后台，用返回的 task id + TaskOutput 取结果。

### 构建命令（在 D:\Develop\ShuiKeBang 下）

```powershell
.\gradlew.bat :app:assembleDebug   # debug，含 arm64+x86_64
.\gradlew.bat :app:assembleRelease # release，仅 arm64，R8+so压缩，用debug签名，约12MB
.\gradlew.bat :app:testDebugUnitTest
```

## 2. 仓库与产物规划（重要）

- GitHub 仓库：`https://github.com/STAR-10086/ShuiKeBang`，默认分支 main。**第一轮源码已推送（含 LICENSE 共两个 commit）；模型 Release `asr-models-v1` 已创建并上传两个 zip，直链可用。第二轮改动尚未提交（用户要求先真机自测）。**
- **源码走 git main；模型二进制走 GitHub Release，绝不进 git 历史**（.gitignore 已排除 /dist-models、/research、build、local.properties）。
- 模型 Release 规划：tag `asr-models-v1`，上传两个 asset：
  - `small-bilingual-zh-en-int8.zip`（50.0MB，52,430,525 字节，**推荐**，中英双语）
  - `zh-14m-int8.zip`（25.45MB，26,681,115 字节，纯中文省流）
  - 两个 zip 都是**扁平结构**（解压直接是 4 个文件，无顶层目录），故代码里 `innerDir=""`。
  - 文件本地位置：`D:\Develop\ShuiKeBang\dist-models\`（含两个 zip、两个解压目录、MODELS_MANIFEST.md，该目录不进 git）。
  - 建 Release 命令示例：
    ```powershell
    $env:HTTPS_PROXY='http://127.0.0.1:7897'
    gh release create asr-models-v1 `
      D:\Develop\ShuiKeBang\dist-models\small-bilingual-zh-en-int8.zip `
      D:\Develop\ShuiKeBang\dist-models\zh-14m-int8.zip `
      --repo STAR-10086/ShuiKeBang --title "ASR Models v1" --notes "sherpa-onnx streaming zipformer int8"
    ```
  - **已实测（2026-09-01，curl --noproxy 不走代理）**：GitHub Release 直连国内超时（curl exit 28）；`ghfast.top/`、`gh-proxy.com/` 前缀镜像返回 200 且 Content-Length 正确；`ghproxy.net` SSL 失败已移除。AsrModels.kt 下载顺序已改为镜像优先、官方源末尾兜底，OkHttp connectTimeout=8s 让坏源快速跳过。若日后这两个公共镜像也失效，换前缀或上 R2，只改 GITHUB_MIRROR_PREFIXES / archiveUrl。

## 3. 工程结构（约 44 个 Kotlin 文件，包 com.star.shuikebang）

| 包/文件 | 职责与关键点 |
|---|---|
| `asr/AsrModels.kt` | 模型清单 `BuiltinModels`：两个 AsrModelSpec，archiveUrl 指向自建 Release，mirrorUrls 为加速镜像，downloadCandidates 主源+镜像 |
| `asr/ModelManager.kt` | OkHttp 下载（.part 断点续传、sha256 可选）、zip/tar 解压到 filesDir/models/<id>/；**archive 下载已实现多源回退**（逐个 candidate，失败删 part 换下一个）；单例 |
| `asr/AudioCapture.kt` | AudioRecord，VOICE_RECOGNITION/16k/mono/PCM16/100ms 帧，short→float，音频只在内存不落盘 |
| `asr/SherpaStreamEngine.kt` | sherpa OnlineRecognizer 真流式封装。**API 签名已逐字核对官方源码**：字段是 `decodingMethod`（不是 decodeMethod）；modelType="zipformer"；Endpoint 三规则 rule1(false,2.4,0)/rule2(true,1.2,0)/rule3(false,0,20)；acceptWaveform(FloatArray,Int)；isReady→decode 循环；isEndpoint 断句后 reset |
| `nlp/` | TextNorm 归一 + QuestionRules 中英规则 + QuestionDetector 两级判定。**第二轮重构降误报**：信号分 ZH_STRONG 核心强 / ZH_STRONG_SOFT 次强(如何) / ZH_WEAK 弱词(需旁证) / ZH_LECTURE 讲课框架否决 / 点名硬软两档；DetectSensitivity 增加 OFF，共 HIGH/NORMAL/LOW/OFF 四档。历史 bug：规则"吗?"曾被当正则导致句句命中，已改句末字精确匹配。20 个 JVM 单测全绿 |
| `data/db/` | Room 三表 Session/Transcript/Question（外键级删）+ Daos + ClassRepository 单例 |
| `data/prefs/SettingsRepository.kt` | **第二轮新增**：DataStore 偏好，AppSettings（震动/灵敏度/悬浮胶囊/L1 开关/下载源），单例，flow + suspend snapshot() |
| `asr/DownloadSource.kt` | **第二轮新增**：下载源枚举（auto/ghfast/ghproxy/github）与选项文案；AsrModelSpec.candidatesFor(sourceId) 按选择排序候选、失败仍回退 |
| `ui/settings/` | **第二轮新增**：SettingsScreen + SettingsViewModel，分组设置页（灵敏度/震动/悬浮胶囊/L1/下载源/隐私） |
| `service/RecSession.kt` | 全局录制状态单例 StateFlow + 提问事件 SharedFlow；RecUiState 含 recording/starting/prepareMsg/error 等 |
| `service/RecordService.kt` | LifecycleService，串联：前台麦克风服务→(按需下载模型,进度桥接到 prepareMsg)→引擎 init/start→建会话→采集→检测→入库→震动→状态岛。startJob 可在停止时取消；失败写 error 并 stopSelfClean |
| `island/` | 四级状态展示 StatusIsland 门面：L1 小米超级岛/vivo 原子岛（纯本地通知无 Push，VendorIslandNotifier，**字段按公开文档写、未经真机校准**）→L2 OverlayCapsule 悬浮胶囊（默认关）→L3 FgsNotifier 常驻通知→L4 应用内状态条 |
| `ui/` | Compose：idle 待机首页（呼吸大按钮）/model 模型下载页/record 录制页/history 列表+详情/component 通用组件/theme 设计令牌。设计稿在 design/ui-mockup.html（用户已截图确认） |
| `perm/ feedback/ util/` | 权限与电池优化引导、震动、时间格式、剪贴板 |

## 4. 本轮修复内容（模型 + 录制链路）

1. **模型源**：AsrModels.kt 从官方 437MB tar.bz2 改为自建 Release 的 int8 精简 zip（体积真相：官方整包含 fp32/fp16/int8 多精度+96/64 子目录+测试 wav；small 实际只需 4 文件、解压 57MB）。
2. **多镜像回退**：ModelManager.downloadAndExtractArchive 遍历 downloadCandidates。
3. **录制状态链路**（修截图里"正在记录中 00:00 卡死"）：
   - RecUiState 加 `prepareMsg`；RecordService 把下载百分比/解压/引擎加载桥接进去。
   - RecStatusBar 加 `preparing` 形参：准备中显示蓝色转圈+文案，不显示计时；录制中才是红色+计时。
   - RecordScreen：preparing 时中央显示准备提示；观察 ui.error 弹 AlertDialog，确认后 RecSession.reset()+onStopped() 回首页（不再卡死）；停止可取消下载协程。
4. 首页模型大小文案 28MB→50MB。

## 4b. 第二轮改动明细（2026-09-01，真机反馈后）

1. **历史详情闪退（用户：只有第一条会闪退）**：根因是 SessionDetailScreen 同一个 LazyColumn 内 questions 用 `it.id`、transcripts 也用 `it.id` 当 key，两表各自从 1 自增，**会话同时含提问+转录时 key 重复崩溃**；第一条恰是唯一含提问的会话。已改为 `"q_${id}" / "t_${id}" / 固定 header key`。
2. **提问误报多（用户：老师普通说话也被判提问）**：见 §3 nlp 行的规则重构。弱词（什么/怎么/几个/是不是/区别…）单独不判，需句末语气词/点名短语/句首(仅有没有·是不是等)/句末旁证，且被"下面我们/区别在于/分为"等讲课框架词否决；新增一批"讲课句不得误报"单测。
3. **设置页**：新增 data/prefs + ui/settings；RecordService 启动时 snapshot() 应用灵敏度/悬浮胶囊/下载源，L2 震动受震动开关控制，关闭 L1 时一级疑似按普通转录行处理不进提问列表；IdleScreen 右上角齿轮进入，Routes.SETTINGS。
4. **下载源可选**：DownloadSource + AsrModelSpec.candidatesFor()，ModelManager.ensureModel(spec, sourceId)；模型下载页与设置页都能选源，选中源优先、失败仍自动回退其余源。
5. **README 重写**：面向开发者，加徽章/目录/mermaid 数据流/快速开始/规则调法/状态岛/自测清单。

## 5. 接手后第一步（按序）

> 前提：用户要先在真机装第二轮的 release APK 自测；他确认 OK 后才做下面的提交/发版。

1. 复跑 `:app:assembleDebug :app:testDebugUnitTest` 与 `:app:assembleRelease` 确认绿（第二轮本地已通过，提交前再确认一次）。
2. 提交第二轮改动并推送（remote 已配为 HTTPS + gh 凭据，推送前 `$env:HTTPS_PROXY='http://127.0.0.1:7897'`；SSH 22 端口在本机不通，别改回 ssh）：
   ```powershell
   cd D:\Develop\ShuiKeBang
   git add -A
   git commit -m "fix: 修复历史详情闪退、降低提问误报，新增设置页与下载源选择，重写 README"
   git push origin main
   ```
   提交前 `git status` 确认 research、dist-models、build、local.properties 未被暂存（.gitignore 已排除）。
3. 若本轮 APK 要作为正式发行版，用 gh 建一个 App Release（如 tag `app-v0.1.0`）上传 app-release.apk；模型 Release `asr-models-v1` 已存在、模型没变，**不要重建**。
4. present_files 交付最终 release APK。

## 6. 已知坑点 / 不要踩

- sherpa 正确依赖坐标是 `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.7`（groupId 带仓库名；写成 com.github.k2-fsa 聚合坐标会 Duplicate class）。经 JitPack，settings.gradle.kts 已配阿里云镜像+jitpack。
- release APK 约 12MB 是正常的：iOS 1.82MB 是白嫖系统 SFSpeechRecognizer，Android 必须自带 onnxruntime+sherpa 的 .so（约 11MB，已开 useLegacyPackaging 压缩）；业务 dex 经 R8 仅约 1MB。不要再为"为什么不是几 MB"返工。
- 只打 arm64-v8a（debug 额外加 x86_64 供模拟器）；老机型要 armeabi-v7a 再在 app/build.gradle.kts 加。
- 备份 xml 里 exclude 路径必须先 include 同 domain，否则 lintVitalRelease 报错（已修，勿回退）。
- Compose 里 TextUnit 的正确包是 `androidx.compose.ui.unit.TextUnit/TextUnitType`（曾误写成 ui.text，已批量修过，新增文件别再写错）。
- AudioCapture 构造最后一个参数是 onError，trailing lambda 会错配；必须用命名参数 `AudioCapture(onSamples = { ... })`。
- **不要用浏览器工具**（computer_use_tool 曾 150s 卡死，用户明确禁止）；UI 验证靠用户截图或命令行构建。
- 用户硬约束：不存音频、不导外部音频、无大模型总结/问答（"问 AI"只用 ACTION_SEND 系统分享面板）、无云同步/账号、无广告社区、提问检测只能轻量规则、模型不打进 APK。

## 7. 待真机验证（编译期无法完成，需用户手机）

- 首次下载模型→真流式识别（说"什么是线性回归"看 partial 刷新与断句）。
- 小米超级岛 / vivo 原子岛 extras 字段真机校准（VendorIslandNotifier），不支持的机型自动降级 L3 通知。
- 切后台保活、电池优化白名单引导。
- 目标机型/系统版本用户尚未给。

## 8. 关键外部参考（需要时用代理重取，勿凭记忆）

- sherpa Kotlin API：`https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/sherpa-onnx/kotlin-api/OnlineRecognizer.kt`（及同目录 OnlineStream.kt）
- 官方安卓依赖示例：`.../master/android/SherpaOnnxJavaDemo/app/build.gradle`
- 模型来源：HF `csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`、`csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`（注意 small-2023-02-16 在 HF 无 onnx 单文件仓库，只有 ncnn；其 int8 文件已从官方 tar 提取到 dist-models）；国内镜像 hf-mirror.com 已实测不走代理可直连。
- 厂商岛：小米 dev.mi.com HyperOS 通知文档、vivo dev.vivo.com.cn doc/894、896；GitHub peacemo/codup HyperOS3_notify.md。


## 9. 第三轮进展（2026-09-02，CI/CD 发版 + 三架构 + 提问小模型）

> 本节为当前最新状态，与 §0/§4b/§5/§6 冲突处以本节为准。

### 9.1 已完成
- **CI/CD 全绿并发版**：`.github/workflows/android.yml`，push/PR 跑 `verify`（单测+debug APK+上传 artifact），
  打 `v*` tag 额外跑 `release`（`assembleRelease -PsplitAbi`，重命名后 softprops/action-gh-release 发 Release）。
  **Release `v0.1.0` 已发布**：shuikebang-v0.1.0-arm64-v8a.apk(约12MB)、-armeabi-v7a.apk(约11MB)、
  -universal.apk(约22MB)。仓库简介与 topics 已用 `gh repo edit` 设置。
- **三架构 ABI splits**：`app/build.gradle.kts` 用 `project.hasProperty("splitAbi")` 开关；
  `./gradlew :app:assembleRelease -PsplitAbi` 一次产出三个 APK（splits.abi + isUniversalApk，
  并用 ndk.abiFilters 防止 AAR 自带 x86 混进 universal）。debug 非 split 时 arm64+x86_64 供模拟器。
- **提问小模型接入（重点，替代纯关键词）**：见 9.3。
- 规则层先加了"自问自答"抑制（QuestionRules.ZH_ANSWER_CUE + QuestionDetector.isSelfAnswered）。

### 9.2 构建源 / 代理（覆盖 §6 旧描述，重要）
- **settings.gradle.kts 现统一只用官方源**：pluginManagement = gradlePluginPortal/google/mavenCentral，
  dependencyResolution = google/mavenCentral/jitpack；**已移除全部阿里云镜像**（阿里云公共镜像从 GHA 海外
  runner 访问会 HTTP 502，且 Gradle 对 5xx 不保证回退，曾导致 CI 连挂）。用户明确要求本地也走官方源。
- pluginManagement 保留 `resolutionStrategy.eachPlugin` 把 KSP 插件映射到真实构件
  `com.google.devtools.ksp:symbol-processing-gradle-plugin:<ver>`（全新环境 KSP plugin marker 偶发解析不到，保留勿删）。
- **代理只在用户全局 `C:\Users\STAR\.gradle\gradle.properties`**（127.0.0.1:7897，nonProxyHosts 含国内镜像）；
  仓库内 gradle.properties **不含代理**（否则云端 CI 直接失败）。
- git/gh 命令前需进程内 `$env:HTTPS_PROXY=$env:HTTP_PROXY='http://127.0.0.1:7897'`；remote 是 HTTPS（SSH 22 不通，勿改回）。
  gh 已登录 STAR-10086，scopes 含 workflow（曾因缺 workflow scope 用 `gh auth refresh -s workflow` device 授权补齐）。

### 9.3 提问小模型（极小、纯本地、非大模型）
- 动机：纯关键词遇到老师"自问自答"（句中出现"什么"就报）误报；用一个几十 KB 的字级分类器裁决歧义。
- 结构（与 `research/mini_q/train_export.py` 严格对应，纯 numpy 训练）：
  字 embedding 平均(D=32) → 单隐层(H=32, ReLU) → 1 维 sigmoid；权重 int8 对称量化。
- 产物随 **APK assets** 下发：`app/src/main/assets/qclassifier/vocab.txt`(约1.5KB) + `model.txt`(约33KB)，合计约35KB
  （这是文本分类器、不是 ASR 大模型，体积可忽略，故打进 assets 保证离线可用、无需下载）。
- 运行端 `nlp/QuestionMlClassifier.kt`：**纯 Kotlin 前向、零 native/第三方依赖**；
  `fromAssets(context)` 读取，任何异常返回 null；`parse(vocabText, modelText)` 与 assets 读取分离以便 JVM 单测；
  解析对 CRLF/多空白健壮（防 git autocrlf）。
- 融合策略在 `QuestionDetector`：构造新增 `ml: QuestionMlClassifier? = null, mlThreshold=0.55f`。
  高置信规则（问号/句末"吗"/硬强结构/点名）直通；**ML 只在"句中有疑问信号(strong/strongSoft/weak/?/吗)但规则未确认"
  时跑**，概率≥0.55 才翻成 L2；纯陈述不跑模型（省算力+防误报）。**ml=null 时行为与纯规则完全一致**（老单测不破）。
  RecordService.onCreate 用 fromAssets 加载并注入。
- 效果：训练 4200、留出 900 句 acc 98.9%（零误报、少量保守漏报），手写边界集 26 句 100%。
  单测 `QuestionMlClassifierTest`：内联小模型验证前向数学（必跑）+ 真实 assets 模型存在时验证语义与融合（assumeTrue 跳过）。
- **局限/后续**：训练语料是模板生成，真实课堂泛化需采集真实 ASR 文本再训（改 train_export.py 重导出即可覆盖 assets）；
  模型只管单句，**跨句自问自答（上句问、下句答）管不了，需要独立的"延迟确认/撤销"缓冲机制**；目前只做中文，英文仍纯规则。

### 9.4 本地环境当前坑（需用户配合一次）
- 当前 Clash 节点**能上 www.google.com、repo1.maven.org、plugins.gradle.org、jitpack.io（均 200），
  但 dl.google.com 稳定 TLS 握手失败（curl http=000 / exit35）**；maven.google.com 会 301 跳到 dl.google.com 也无效。
  AGP/androidx 只在 dl.google.com，故本地联网构建卡在下载 AGP classpath。**换一个能打开
  https://dl.google.com 的代理节点后，联网跑一次 assembleDebug 补齐缓存即可**；CI 在海外直连 dl.google.com 正常，不受影响。
- 之前跑过 `--refresh-dependencies` 使 AGP classpath 的解析元数据失效（jar 实体仍在
  ~/.gradle/caches/modules-2/files-2.1，但 offline 报 No cached），所以在换节点前本地 offline 也编译不了；
  **此期间可把代码 push 到 main，用 CI 的 verify job 代编译/代跑单测**（推 main 不触发 release，只有 v* tag 才发版）。

### 9.5 下一步建议（按优先级）
1. 用户换代理节点 → 本地 `:app:testDebugUnitTest :app:assembleDebug` 复绿；真机装 v0.1.0 验证小模型是否显著减少自问自答误报。
2. 采集真实课堂 ASR 误报/漏报句子，迭代 train_export.py 语料并重导出 assets；必要时加跨句延迟确认机制。
3. release 目前用 debug 签名，正式上架需自建 keystore 并在 CI 配 secrets。
4. 小米/vivo 厂商岛 extras 字段仍需真机校准（不支持则降级 L3 通知）；目标机型/系统版本待用户提供。
5. 公共 GitHub 镜像前缀日后失效则换前缀或迁 R2（只改 AsrModels.kt/DownloadSource.kt）。


---

## 10. 第四轮：静态代码审查 8 项加固（commit 02de152 + 1061e8c，CI verify 全绿）

针对一份外部静态审查逐项修复，CI（单测 + lintDebug + assembleDebug）已全部通过。

### 10.1 已修复与关键实现
1. **停止录音原生并发（高）**：`RecordService.stopRecording` 改为严格顺序——先 `capture?.stop()`
   （AudioCapture.stop 内部 `worker.join(400)` 等采集线程退出，此后不再有 accept/onFinal）→
   `engine?.flush()` 取残句 → 等入库 → 最后在 `stopSelfClean()` 里 `engine.release()`。
   杜绝同一 OnlineStream 被 accept 与 flush 并发操作导致的 native 崩溃/残句重复丢失。
2. **入库竞态（高）**：删除固定 `delay(150)`。每个 `handleFinal` 的入库协程经 `trackWrite(job)`
   登记进线程安全集合 `writeJobs`（invokeOnCompletion 自动移除）；停止时
   `synchronized(writeJobs){toList()}.joinAll()` 等全部落库再 `finishSession`，保证最后一句不丢、questionCount 准确。
3. **状态原子更新（高）**：`RecSession.update/reset` 改用 `kotlinx.coroutines.flow.update`（CAS 原子），
   消除多线程"读-改-写"互相覆盖导致转录行从 UI 消失。
4. **解压安全（中高）**：新增纯 JVM、无 Android 依赖的 `asr/ArchiveSafety.kt`（`safeResolve`：
   反斜杠归一、去前导/、拒绝 `..` 段、canonical 路径越界兜底），zip/tar 都走它；tar 额外拒绝符号链接。
   单测 `ArchiveSafetyTest` 覆盖正常/`..`/深层/反斜杠穿越/空名。两个模型整包补固定 SHA-256（见 §10.3）。
5. **原子安装**：解压到 `.${id}.staging` 临时目录 → 校验四个必需文件 → 写 `.ready` 标志 →
   删旧目录 + `renameTo` 原子替换（失败退化为 copyRecursively）；`isReady()` 额外要求 `.ready` 存在，
   半成品不会被误判可用。
6. **并发下载**：`ensureModel` 按 modelId 用 `Mutex.withLock` 串行化整个下载/校验/安装；
   states/locks 均为 `ConcurrentHashMap`。
7. **隐私一致**：`android:allowBackup="false"` 并删除 dataExtractionRules/fullBackupContent 引用与
   res/xml 下两个备份规则文件，课堂文本不进 Google 云备份/换机迁移，卸载即清。
8. **gradlew 可执行位**：`git update-index --chmod=+x`，仓库内 mode 100755；workflow 移除两处 chmod step。

### 10.2 CI/工程收尾
- workflow 顶层 `permissions: contents: read`（最小），仅 release job 单独 `contents: write`；
  verify job 新增 `:app:lintDebug`；`app/build.gradle.kts` 加 `lint{abortOnError=false;checkReleaseBuilds=false}`
  （只产报告不阻断）。Room `fallbackToDestructiveMigration` 旁加"正式版前必须换显式 Migration"的 TODO。
  README 统一架构措辞（release 三架构 / debug arm64+x86_64）并补 allowBackup=false 隐私说明。
- **教训**：XML 标签的属性列表之间不能插 `<!-- -->` 注释（更不能用 `//`），注释只能放在标签外；
  改 XML 后用 `xml.dom.minidom.parse` 本地校验良构再提交（本轮因此失败过一次 CI）。

### 10.3 模型整包 SHA-256（已写入 AsrModels.archiveSha256，下载后强制校验）
- small-bilingual-zh-en-int8.zip（52,430,525 B）：`39aee1c1590fb60d73b91ebd0ca5ed9585c6275e260554d7d12b1b8e73badbf3`
- zh-14m-int8.zip（26,681,115 B）：`0ae36ec8aca458f4d8490f56d4d4cd9e973785da29560817772855d66750055f`

### 10.4 仍未做（低优先/待决策）
- CI 的 Node.js 20 / setup-java v4 deprecation 仅为警告不阻断；想消除可升 actions/checkout@v5、
  actions/setup-java@v5（参数兼容），upload-artifact/setup-gradle 待其发布 node24 版本，改动后需再跑一次 CI。
- release 仍 debug 签名（正式上架需自有 keystore + CI secrets）。
- 跨句自问自答的"延迟确认/撤销"缓冲仍未做（宜在能本地/真机验证时改 RecordService 时序）。
- 本轮修复 + 小模型都在 main，尚未发新版 Release；是否打 v0.2.0 由用户拍板（打 v* tag 即自动出三架构包）。


---

## 11. 第五轮：本地镜像构建 + 麦克风增益 + 跨句自问自答延迟确认

### 11.1 本地走镜像、远端走官方（关键，解决本机无法编译）
- 背景：当前 Clash 节点连不上 `dl.google.com`，而 AGP/AndroidX 只在该域名，本地官方源+代理编译失败。
- 方案：在**用户全局** `C:\Users\STAR\.gradle\init.d\local-mirrors.gradle`（不进任何仓库、CI 读不到）
  用 `settingsEvaluated` 把 settings 层的 pluginManagement / dependencyResolutionManagement 仓库列表
  `clear()` 后换成阿里云镜像（gradle-plugin / google / public / central）+ JitPack；**不新增 project 级仓库**，
  因此不触发 `FAIL_ON_PROJECT_REPOS`。仓库 `settings.gradle.kts` 保持纯官方源不变。
- 全局 `~/.gradle/gradle.properties` 的 7897 代理保留，且 `nonProxyHosts` 已含 `maven.aliyun.com`（镜像直连、JitPack 走代理）。
- 注意 init 脚本是 **Groovy（.gradle）**，变量用 `def` 不是 `val`（踩过一次编译错）。
- 结果：本地 `:app:testDebugUnitTest / :app:assembleDebug / :app:lintDebug` 全部成功，lint 0 error；
  debug APK 约 44MB（含全 ABI 且未裁剪，正常，release split 后 arm 包约 11–12MB）。

### 11.2 麦克风增益（老师声音小识别不到）
- 新文件 `asr/AudioPreamp.kt`：枚举 `MicGainMode`（AUTO 默认 / OFF / X2 / X3 / X5）+ 纯 Kotlin 前级。
  - 固定档：样本×倍数后 `tanh` 软限幅（有界[-1,1]、不爆音）；
  - AUTO 轻量 AGC：逐帧 RMS → 包络 EMA（上升 0.55/下降 0.12）→ 目标 RMS 0.09、噪声门 0.005（静音不抬底噪）、
    增益上限 4.5、增益自身平滑 0.18 防抽吸。
- 接入：`AudioCapture` 构造新增可空 `preamp`，在 short→float 之后、回调之前 `preamp.process(buf,n)`；
  `RecordService.startCapture` 按 `cfg.micGainId` 创建（录音开始时生效，与灵敏度一致）。
- 设置：DataStore `mic_gain`，设置页新增“录音增益”分组；单测 `AudioPreampTest`（直通/固定放大/不削波/小声抬升/静音不抬/有界/枚举兜底）。
- 音频源仍用 `VOICE_RECOGNITION`（系统降噪、不做系统 AGC，增益由我们自己掌控，避免双重 AGC）。

### 11.3 跨句自问自答延迟确认门（核心降误报）
- 旧逻辑只拦“同一句内”自答（QuestionDetector.isSelfAnswered）。真实场景是断成两句：
  “什么是递归？”(判 L2 立即提醒) → 1~2s 后“递归就是函数调用自身”(老师自答)。
- 新文件 `nlp/SelfAnswerDetector.kt`（纯 Kotlin、单测 `SelfAnswerDetectorTest`）：
  - 后续句又含疑问结构/问号/“吗呢”/点名短语 → 不是自答；
  - 强解答引导（答案是/正确答案/结果是/应该选…）命中句首即判自答；
  - 弱引导（就是/指的是/这是…复用 ZH_ANSWER_CUE）需与问题**主题 bigram 覆盖率 ≥ 0.34**（去疑问词/停用词后）；
  - 英文：the answer is 为强，it is/this is 等需与问题实义词重合；刻意保守，宁可保留提醒也不漏真提问。
- `RecordService` 重构（配合第四轮的串行化）：
  - 新增 `gateDispatcher = Dispatchers.IO.limitedParallelism(1)`（需 `@OptIn(ExperimentalCoroutinesApi)`），
    所有断句处理排队执行，pending 状态无需加锁；
  - L2 且开关开启时 `armPending`：先以**普通行**上屏、不震动/不进提问列表/不写 question 表，
    启动 2.2s `confirmJob`；期间来下一句：判为自答则 `revokePending`（普通转录落库、行保持普通），
    否则提前 `confirmPending`；到期也确认；停止录音时残句后排队把残留 pending 立即确认（宁报勿漏）；
  - 确认时 `commitQuestion`：写 question/transcript、`RecSession.replaceLine(ts)` 把普通行升级，
    RecordScreen 按 `line.questionId` 在 `questionMap` 命中即从 TranscriptRow 重组成 QuestionCard，
    并经 questionEvents 自动滚动定位。
- 设置：DataStore `confirm_question`（默认 true），设置页“提问检测”组内开关“提问二次确认”。
- 单测覆盖判定器；Service 时序依赖 Android，靠本地 assemble/lint + 代码审查，**仍需真机验证延迟体验与撤销准确率**。

### 11.4 待办 / 真机验证
- 真机重点：①AUTO 增益在不同距离/教室的实际识别率与是否抬底噪；②2.2s 延迟震动是否自然、自问自答撤销是否准确，
  不准就调 `SelfAnswerDetector` 的 cue 词表 / `RELATED_THRESHOLD` / `CONFIRM_DELAY_MS`。
- 本轮未发版；与第四轮加固一起积累在 main，验证 OK 后可打 v0.2.0（三架构）。


---

## 12. 第六轮：可交互前台通知 + 可操作悬浮窗 + 暂停/继续

### 12.1 要解决的问题
此前 L3 通知是“假通知”——只有一条“正在记录课堂”，不能操作、看不到问题；L2 悬浮胶囊只读、
点一下只回 App；且没有暂停能力，切到别的应用就无法控制录音。

### 12.2 暂停 / 继续（RecordService）
- `RecUiState` 新增 `paused`；companion 暴露 public `ACTION_PAUSE/ACTION_RESUME/ACTION_STOP`
  与静态 `pause(context)/resume(context)`，`onStartCommand` 分发。
- 暂停：`capture.stop()/=null`（**保留 engine / session / preamp**）、取消 ticker、在途 pending 立即确认、
  `paused=true`、`island.onPause()`；继续：重建 `AudioCapture`（复用同一 engine 与 preamp）、重启 ticker。
- 计时由“wallclock(now-startedAt)”改为 **ticker 每秒 durationSec+1**：暂停取消 ticker 即冻结，
  继续接着累加，durationSec 天然等于真正录音秒数；`sessionTitle` 提为字段供重启 ticker 使用。
- 录音前台服务类型始终是 microphone；暂停期间不采集但仍在前台，合法。

### 12.3 可交互通知（FgsNotifier 重写）
- `build(sec, paused, question)`：标题随 记录中/已暂停 + 计时切换；正文优先显示最近一条提问，
  `BigTextStyle` 展开看全；`addAction` 暂停↔继续（ic_ntf_pause/ic_ntf_play）、结束并保存（ic_ntf_stop）。
- Action 用 `PendingIntent.getForegroundService` 回送 RecordService action（服务已在前台，不受后台启动限制）；
  缓存 latestQuestion，refresh 时传 null 保留。新增 3 个白色 vector：ic_ntf_pause/play/stop。

### 12.4 可操作悬浮窗（OverlayCapsule 重写）
- 单 window 根布局：折叠行（红点+状态+计时）+ 展开区（最近提问 + 暂停/继续、结束、打开、收起）。
- **可拖动**：root OnTouch 用 scaledTouchSlop 区分点击与拖动，移动改 LayoutParams.x/y（gravity TOP|CENTER_HORIZONTAL），
  抬手未拖动则展开/收起；按钮是可点击子 View，自行消费事件、不会被拖动吞掉。
- 操作直接 `startForegroundService(RecordService action)`，打开应用跳 MainActivity；`setPaused/flashQuestion/updateTimer`
  由 StatusIsland 驱动，全部 `view.post` 回主线程。圆角深色背景改用 GradientDrawable（不再用系统 dialog frame）。
- `StatusIsland.onStart` 改为只要 overlayEnabled 就 show（不再因 useVendorIsland 互斥）：小米焦点通知未授权时
  原生岛静默失败，悬浮窗仍能兜底；新增 `overlayMissingPermission()`，想开却没权限时 Service 弹一次 Toast 引导。

### 12.5 App 内与设置
- ControlDock 新增“暂停/继续”圆形按钮（Outlined.Pause/PlayArrow），RecordScreen 用 ui.paused 接线；
  RecStatusBar 暂停时变橙(#F0A830)、文案“已暂停”、隐藏波形。
- `AppSettings.overlayCapsule` 默认改为 **true**（data class 默认与 DataStore `?: true` 同步）；
  设置项改名“悬浮控制窗”并更新说明。

### 12.6 验证 / 待真机
- 本地镜像源：`:app:testDebugUnitTest`（42, 0 失败）、`:app:assembleDebug`、`:app:lintDebug`（0 error）全过。
- 待真机：①通知上暂停/继续/结束在小米/vivo 各类 ROM 是否都显示 action（厂商可能折叠通知 action）；
  ②悬浮窗拖动手感、展开宽度与不挡操作、TYPE_APPLICATION_OVERLAY 在国产 ROM 的权限与保活；
  ③暂停数分钟后继续，识别与计时是否正确、麦克风不会被其他应用抢占后无法恢复。


---

## 13. 第七轮：设置二级化、AI 解答（自带端点）、关于页、权限主动申请、自有签名框架

> 与前文冲突处以本节为准。版本升到 **versionCode 2 / versionName "0.2.0-beta"**，发预发行 v0.2.0-beta。

### 13.1 设置页二级菜单重构（SettingsScreen 重写）
- 不再把单选项全部平铺；主页只放“当前值”的可点行，点击弹 `AlertDialog` 单选列表（通用 `PickerState/PickerOpt`）。
- 分组：提问检测（灵敏度弹窗 / 保留L1 / 二次确认）、提醒与悬浮窗（震动 / 悬浮控制窗）、录音与识别（录音增益弹窗 / 模型下载源弹窗）、
  AI 解答（进 AiSettingsScreen，副标题显示已配置/未配置）、其他（权限与后台保活 / 关于与隐私）。
- 私有组件：`SectionTitle/GroupCard/OptionRow(点击弹窗)/NavRow(跳子页)/SwitchRow/CellDivider`。
- “权限与后台保活”行：缺通知权限先申请，否则引导电池优化白名单，都齐则 Toast。

### 13.2 AI 解答（重点：用户自带 OpenAI 兼容端点，App 不内置任何 Key/代理）
- 数据：`AppSettings` 新增 `aiBaseUrl/aiApiKey/aiModel(默认 gpt-4o-mini)/overlayGuideShown`；DataStore key 见 SettingsRepository。
- 网络层（新包 `ai/`，复用既有 okhttp，**未加任何新网络依赖**；JSON 用系统 org.json）：
  - `AiProtocol.kt`（纯 Kotlin、可单测）：normalizeBase（去尾斜杠/容错用户填到 /chat/completions）、chatEndpoint、
    isReady（http(s)+key 非空）、buildRequestBody（非流式、temperature0.3、system+user）、parseAnswer（取 choices[0].message.content，
    识别 error/缺字段/空内容并抛错）。system prompt 内置“大学课程助教、简体、分点、≤300字、不复述”。
  - `AiClient.kt`：suspend ask()，POST `{base}/chat/completions`，`Authorization: Bearer`，connect12/read90s，Dispatchers.IO。
  - 单测 `AiProtocolTest`（8 个）。为在 JVM 单测用到真实 org.json，`app/build.gradle.kts` 加
    `testImplementation("org.json:json:20240303")`（仅测试期、不进 APK）。
- UI：
  - `ui/settings/AiSettingsScreen.kt`：端点/Key（PasswordVisualTransformation）/模型三个输入框，5 个常用兼容端点快捷 chip
    （OpenAI/DeepSeek/通义兼容模式/硅基流动/本地 Ollama），状态胶囊，隐私说明；onChange 直接落 DataStore。
  - `ui/ai/AiAnswerDialog.kt`：`Dialog` 弹层，Loading/Success/Error/NotConfigured 四态，成功可复制、失败可重试、未配置可跳设置。
  - 入口在录制页 `ControlDock`：**保留**原“分享”（ACTION_SEND 系统分享），新增“解答”按钮（AutoAwesome）走 AiAnswerDialog；
    RecordScreen 新增 `aiQuestion` 状态与 `onOpenSettings` 回调（未配置时可从弹层跳设置）。
- 隐私边界（写进关于页与 AI 设置页）：仅用户主动点“解答”时把**这一句问题文本**发往其自填端点；音频与其他文本不发；Key 只存本机、卸载即清。
  这是用户明确新增的远程能力，**本地提问检测仍是轻量规则+35KB 小模型，APK 不打包任何大模型**，别混淆。

### 13.3 关于页（AboutScreen，独立路由 Routes.ABOUT）
- 详细卡片：头部（名称/版本 versionName+versionCode/一句话）、核心功能、隐私承诺、技术栈、开源与致谢；
- 可点击链接（Intent ACTION_VIEW）：作者 GitHub 主页 `https://github.com/STAR-10086`、仓库 `https://github.com/STAR-10086/ShuiKeBang`。

### 13.4 权限主动申请（IdleScreen 重写）
- 点“开始记录”统一走 `requestThenStart()`：用 `RequestMultiplePermissions` 一次性申请当前缺失的
  RECORD_AUDIO 与（Android13+）POST_NOTIFICATIONS；回调只要麦克风授予就继续。
- 悬浮窗是**特殊权限、系统不提供标准授权弹窗**（只能跳 Settings.ACTION_MANAGE_OVERLAY_PERMISSION，别尝试 requestPermission）：
  麦克风就绪后 `proceed()`，若设置里开了悬浮窗、当前无权限且没引导过（overlayGuideShown=false），先弹**说明性 AlertDialog**，
  “去开启”跳系统悬浮窗设置并标记已引导，“暂不”也标记并照常开始录音（无权限自动降级为前台通知，不阻塞）。

### 13.5 自有 release 签名框架（用户问“怎么用自己的签名”）
- `app/build.gradle.kts`：顶部 import Properties/FileInputStream；读 `local.properties`（其次同名环境变量，给 CI 用）的
  RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD，构建 `signingConfigs.release`；
  release buildType **配到了就用 release 签名、没配回退 debug**（CI 无 keystore 仍能出可安装 beta 包）。
- 仓库新增 `local.properties.example` 模板（含 keytool 生成命令，注释态）；`.gitignore` 增补 `*.jks`（local.properties、*.keystore 本就忽略）。
- 用户操作：`keytool -genkeypair -v -keystore shuikebang-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shuikebang`，
  把四个键写进 local.properties 即可，本地 `assembleRelease` 就是自有签名；**私钥密码不入库、不经过 Agent**。
- 将来 CI 正式签名：workflow 里把 base64 jks 存 secret 解码成文件，再用 env 注入四个 RELEASE_* 变量（gradle 已支持读环境变量）。

### 13.6 动画规划（本轮只规划、未实现，下轮按需做）
原则：只用 Compose 声明式动画（GPU 友好的属性/透明度动画），不引 Lottie 等重依赖、不做持续高频空转，避免无谓重组。
候选（按性价比排序）：
1. 提问卡片入场：`AnimatedVisibility`+`slideInVertically/fadeIn`，命中瞬间弹性 `scale 1.04→1f`（spring, DampingRatioMediumBouncy）
   + 背景色短暂脉冲（animateColorAsState 从 RecordSoft 到高亮再回落），强化“被点到”的感知。
2. 列表动画：转录 LazyColumn `Modifier.animateItem()`（foundation 1.7 稳定），新行平滑插入、提问升级为卡片时不跳变。
3. 状态切换：待机↔准备↔录音中↔暂停用 `AnimatedContent`/`Crossfade` 过渡 RecStatusBar；暂停按钮图标 `AnimatedContent` 旋转 morph。
4. 录音波形：把现有静态波形换成按 RMS 驱动的等高条（用识别回调里的音量做 animateFloat，限 3–5 根条、`infiniteTransition` 仅在录音中运行，暂停/离开立即停）。
5. 悬浮窗展开/收起：原生 Window 无法用 Compose 动画，改 ValueAnimator 对高度/透明度做 160ms 插值；拖动松手加轻微回弹。
6. 页面转场：NavHost `composable(enterTransition/exitTransition)` 做统一 220ms 横向滑动+淡入（注意给 RecordScreen 保留状态）。
7. 设置弹窗/AI 弹层：AlertDialog/Dialog 内容用 `animateContentSize`；按钮按压已有 ripple，不必再加。
落地注意：动画状态用 `remember`/`Animatable`，不要在滚动列表里每帧分配对象；弱机关闭部分非必要动画（可加“减少动态效果”开关，低优先）。

### 13.7 “从没看到胶囊通知”的根因（用户说先不管，仅记录）
四级岛里：L1 小米/vivo 原生焦点通知/原子通知**需要 App 上架后向厂商邮件申请展示授权**，未授权时 VendorIslandNotifier 直接静默 return；
L2 悬浮窗需要“显示在其他应用上层”授权（本轮已做首次引导）。所以普通未授权、未上架机型上实际只有 L3 前台通知（第六轮已升级为可交互），
这是预期降级而非 bug；真机若已授悬浮窗权限仍不显示，再查 OverlayCapsule 的 TYPE_APPLICATION_OVERLAY 与国产 ROM 后台弹出界面权限。

### 13.8 发版
- workflow release job 的 softprops/action-gh-release 增加 prerelease 动态判定（tag 名含 beta/alpha/rc 即标预发行）并更新 release body；
- 本地 `:app:testDebugUnitTest / :app:lintDebug / :app:assembleRelease -PsplitAbi` 全过后提交推送 main，CI verify 绿再打 `v0.2.0-beta` 触发三架构包，
  gh 确认 Release 被勾选 Pre-release。


## 14. 第八轮：静态审查 11 项修复（commit 9a296a6，本地已提交、**未 push**，等用户发话）

本轮针对用户贴来的 3 高 + 8 中审查逐项修复。本地 `:app:testDebugUnitTest`（53 用例全过）、`:app:lintDebug`（无 error）、`:app:assembleRelease -PsplitAbi`（arm64 12.73MB / v7a 11.9MB / universal 23.03MB，均不含模型）全绿。

### 14.1 三项高优先
1. **模型选择真正生效**：`SettingsRepository` 新增持久化 `selectedModelId`（默认 RECOMMENDED_ID）；模型页 VM 的 selectedId 以它为准、select 时写回；`AppNav` 开始录音时只认选中模型——就绪才 `RecordService.start(context, spec.id)` 并跳录音页，未就绪跳模型页（不再“下了 25M 又自动下 50M”）；`IdleScreen` 就绪状态与模型名都显示选中的那个。
2. **录音控制串行状态机**：`RecordService` 新增 `controlDispatcher = Dispatchers.IO.limitedParallelism(1)`，开始/暂停/继续/停止/标记/采集异常全部排队；`@Volatile stopping` + `requestStop()` 保证停止幂等（通知、悬浮窗、页面同时点也只停一次）；停止顺序=先停采集 join → flush → joinAll 入库协程 → 释放引擎，杜绝 accept/flush 并发与 delay(150) 丢句。
3. **AudioCapture 异常治理**：`startRecording()` 抛错时回滚 running 并 stop/release 麦克风；采集线程 read 致命错误时自回收并回调 `onError` → Service `onCaptureFailed` → `requestStop(错误文案)` 受控中止，不再“假录音”。

### 14.2 八项中优先
4. 本地 AI 端点：新增 `res/xml/network_security_config.xml`（Manifest 挂载）；`AiProtocol` 新增 hostOf/isLocalEndpoint（localhost/回环/10/172.16-31/192.168/169.254/100.64-127/10.0.2.2/IPv6 本地）与 notReadyReason——**仅本机/局域网允许 http 且可空 Key，外部强制 https + Key**；`AiClient` 空 Key 不发 Authorization；AI 设置页加 10.0.2.2 与 LM Studio 预设、Key 可留空提示、未就绪原因。
5. 历史时长：`ClassRepository.finishSession(..., actualDurationSec)` 用 `RecSession.durationSec` 真实录音秒数，暂停时间不再计入。
6. 提问通知跳转：MainActivity 改 singleTop + `onNewIntent` + 可观察 pendingQuestionId；`Routes.session(id, highlightQuestionId)` 加可选 `?hq=` 参数；AppNav 用新增 `QuestionDao.sessionOfQuestion` 反查课堂并导航，详情页 `LaunchedEffect` 滚动定位该问题。
7. 真断点续传：`ModelManager` 不再在 ensureModel/换源/catch 时盲删 `.part`，新增 partLooksComplete（已下满才删），中断后按已有长度发 Range 续传。
8. 模型可恢复：模型页 Ready 状态加“重新下载 / 删除模型”（删除有二次确认），VM 加 redownload/deleteSelected，损坏后无需清数据。
9. 状态岛：`vendorIsland` 持久化默认 **false**（L1 胶囊需厂商授权，默认不假设支持）；`StatusIsland` 悬浮窗更新与厂商岛解耦（独立 if，不再卡 00:00）；小米 isSupported 加 `miCanShowFocus` 展示授权校验，vivo 不再无条件支持。
10. 标记/重命名补齐：`RecordService.mark()`（ACTION_MARK，MARK_TEXT="★ 标记重点"、LEVEL_MARK=3）把标记作为 transcript 落库，RecordScreen 改调它并用琥珀色高亮标记行；历史列表每行加编辑图标 + AlertDialog 重命名（调已有 vm.rename）。
11. Flow 泄漏：`SessionDetailScreen` 用 `remember(sessionId){ vm.transcripts/questions(...) }` 固定 Flow，重组不再新建 stateIn。

### 14.3 给下一轮的提示
- **未 push、未发版**：用户约定“本地测试通过后由他发话再上传”。push 前记得 git/gh 命令前 `$env:HTTPS_PROXY=$env:HTTP_PROXY='http://127.0.0.1:7897'`；远端 CI 只用官方源，勿加阿里云镜像。
- 若发 v0.2.1，versionCode 需 +1（当前 =2 / versionName=0.2.0-beta）；release 三架构 workflow 已就绪。
- 本轮把 `gradle.properties` 里 Android Studio 自动加的 `org.gradle.tooling.parallel=true` 主动还原了（与修复无关、不进提交）；AS 可能再次自动写入，提交前留意 `git status`。
- 新增/改动持久化字段后老用户升级：selectedModelId 缺省回退 RECOMMENDED_ID、vendorIsland 缺省 false，均向后兼容，无需 Room 迁移（DataStore 层）。
- 仍只能真机验证：本地 Ollama 连通（模拟器 10.0.2.2 / 真机局域网 IP）、采集 onError 路径、通知 action 与厂商岛在小米/vivo 的折叠、标记落库后历史可见、通知点击定位。


## 15. 第九轮：自有签名正式落地 + v0.2.1 + CWE-927 核查（本轮，进行到 push/tag）

### 15.1 自有签名（用户提供 keystore，以后所有 release 都用它）
- keystore：`D:\Develop\STAR的apk签名\my-release.jks`（PKCS12，普通连字符；别名 **key0**；store/key 密码相同，由用户掌握、不写进仓库）。证书 SHA-256 指纹 `A2:0C:58:4D:20:DB:5B:24:26:26:D5:AD:6C:52:0B:0D:12:7F:2E:98:82:BD:C9:AE:62:2C:9E:5E:10:ED:09:37`。同目录 `jks_base64.txt` 已验证与 jks 字节一致。
- **本机**：`local.properties`（已 gitignore）写了 4 个 `RELEASE_*`，`RELEASE_STORE_FILE` 用正斜杠中文绝对路径。关键修复：`app/build.gradle.kts` 改为 `InputStreamReader(FileInputStream, UTF-8)` 读 local.properties——否则 Java Properties 默认 ISO-8859-1 会把中文密钥目录读乱导致找不到文件。
- **CI**：用户已在仓库 Settings→Secrets→Actions 配好 `KEYSTORE_B64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD`。`.github/workflows/android.yml` 的 **release job** 新增 “Decode release keystore”（base64 -d 成 `ci-release.jks` 并用 keytool 自检），构建步骤用 env 把它们映射成 build.gradle 认的 `RELEASE_STORE_FILE=ci-release.jks / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD`。verify job 仍打 debug、不需要密钥。
- 本地 `:app:assembleRelease -PsplitAbi` 三包用 apksigner 校验：签名者指纹与 key0 一致、三个 verify 退出码均 0。
- 版本：versionCode 2→**3**、versionName 0.2.0-beta→**0.2.1**；tag 名不含 beta，workflow 的 prerelease 表达式判定为**正式版**。

### 15.2 CWE-927 隐式 PendingIntent（用户给了 VendorIslandNotifier 190/236、FgsNotifier 103）
- 结论：**当前 main 代码已合规，无需改 Kotlin**。全工程仅 3 个 PendingIntent 构造点——FgsNotifier.contentIntent/controlIntent、VendorIslandNotifier.tapIntent，全部是显式 `Intent(context, Xxx::class.java)` 且 flag 含 `FLAG_IMMUTABLE`（配 UPDATE_CURRENT 以便通知刷新，这是可交互通知的标准写法，不要机械换成 ONE_SHOT，否则刷新后按钮会失效）。
- 用户引用的行号在远端 HEAD(8f9e2c1) 上已是 nm.notify/.build，并非 PendingIntent；那 3 条 CodeQL High 来自更早的 v0.1.0 旧版（`git log -S "Intent()"` 仅初始提交 c3ab418 出现过空构造）。**push 本轮代码触发 CodeQL 重扫 HEAD 后即应关闭**，需在 Security→Code scanning 确认 3 条变为 Closed。
- OverlayCapsule/RecordService 里的 `Intent(context, RecordService::class.java)` 是直接 startForegroundService、不经过 PendingIntent，CodeQL 不报；同样已是显式。

### 15.3 发版动作与回滚
- 提交内容：app/build.gradle.kts（UTF-8 读取+版本号）、.github/workflows/android.yml（解码 keystore+签名 env+v0.2.1 release body）、README.md（签名表述更新）。local.properties/research 不进仓库。
- 流程：push main（触发 verify + CodeQL）→ 打并推送 tag `v0.2.1`（触发 release job 出 arm64-v8a/armeabi-v7a/universal 三个**正式签名** APK 并建 Release）。
- 若 CI 报 keystore 解码/密码失败：先在本机 `echo $B64 | base64 -d | keytool -list` 思路排查；Secret 是用户维护，不要把任何密码/密钥写进仓库或日志。
