# 交接文档（给下一个 Agent）

> 最后更新：2026-09-01。本文件是「水课帮 Android」开发接力的唯一权威上下文，读完它 + README + DEV_PLAN 即可无缝接手。用户本机环境为 Windows，执行模式为按需确认、无沙箱。

## 0. 一句话现状

仿照 iOS「水课帮」的 Android App（包名 `com.star.shuikebang`）已完成全部编码，debug+release 编译通过、12 个单测全绿。模型分发已从 k2-fsa 官方 437MB 整包切换到**本仓库自建 GitHub Release（tag `asr-models-v1`，已上传两个精简 zip）**，并修复了"模型没起来时录制页卡在假'正在记录中 00:00'"的链路问题（准备中进度可见、失败弹窗退回）。**网络实测结论：GitHub Release 直连国内超时不通；ghfast.top / gh-proxy.com 两个加速镜像不走代理直连 200 且大小正确（ghproxy.net 已失效移除），故代码里下载顺序为「镜像优先、官方兜底」。接手时若 git 尚未 push 完成，按 §5 收尾。**

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

- GitHub 仓库：`https://github.com/STAR-10086/ShuiKeBang`，默认分支 main，目前仅有一个 LICENSE（空仓库）。
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

## 3. 工程结构（39 个 Kotlin 文件，包 com.star.shuikebang）

| 包/文件 | 职责与关键点 |
|---|---|
| `asr/AsrModels.kt` | 模型清单 `BuiltinModels`：两个 AsrModelSpec，archiveUrl 指向自建 Release，mirrorUrls 为加速镜像，downloadCandidates 主源+镜像 |
| `asr/ModelManager.kt` | OkHttp 下载（.part 断点续传、sha256 可选）、zip/tar 解压到 filesDir/models/<id>/；**archive 下载已实现多源回退**（逐个 candidate，失败删 part 换下一个）；单例 |
| `asr/AudioCapture.kt` | AudioRecord，VOICE_RECOGNITION/16k/mono/PCM16/100ms 帧，short→float，音频只在内存不落盘 |
| `asr/SherpaStreamEngine.kt` | sherpa OnlineRecognizer 真流式封装。**API 签名已逐字核对官方源码**：字段是 `decodingMethod`（不是 decodeMethod）；modelType="zipformer"；Endpoint 三规则 rule1(false,2.4,0)/rule2(true,1.2,0)/rule3(false,0,20)；acceptWaveform(FloatArray,Int)；isReady→decode 循环；isEndpoint 断句后 reset |
| `nlp/` | TextNorm 归一 + QuestionRules 中英规则 + QuestionDetector 两级判定（L1 点名祈使预警/L2 疑问词·问号·句末"吗"确认）。**曾修过严重 bug：规则"吗?"被当正则导致任何句子都命中，已改为句末字精确匹配，"呢/嘛"不单独成证。** 12 个 JVM 单测在 app/src/test，全绿 |
| `data/db/` | Room 三表 Session/Transcript/Question（外键级删）+ Daos + ClassRepository 单例 |
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

## 5. 接手后第一步（按序）

1. 先跑一次 `:app:assembleDebug :app:testDebugUnitTest` 确认本轮改动编译通过、单测仍全绿（上一次失败仅因 Downloading 漏写 `: ModelState`，已修，需复跑确认）。
2. 再 `:app:assembleRelease` 出新 APK。
3. 按 §2 建 GitHub Release 上传两个 zip，**不走代理**实测直链；把结果（可达/不可达、是否需要镜像）告诉用户。
4. git 初始化并推送（仓库已有 LICENSE，先 pull --rebase 或 merge 再 push）：
   ```powershell
   cd D:\Develop\ShuiKeBang
   git init; git branch -M main
   git remote add origin git@github.com:STAR-10086/ShuiKeBang.git   # 已存在则 set-url
   git add -A; git commit -m "feat: 水课帮 Android 初始版本（离线流式ASR+提问检测+本地存储+厂商岛）"
   git pull origin main --allow-unrelated-histories   # 合并 LICENSE
   git push -u origin main
   ```
   推送前用 `git status`/`git ls-files` 确认 research、dist-models、build、local.properties **没有**被暂存。
5. present_files 向用户交付新 release APK。

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
