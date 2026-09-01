# 交接文档（给下一个 Agent）

> 最后更新：2026-09-01。本文件是「水课帮 Android」开发接力的唯一权威上下文，读完它 + README + DEV_PLAN 即可无缝接手。用户本机环境为 Windows，执行模式为按需确认、无沙箱。

## 0. 一句话现状

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
