<div align="center">

# 水课帮 · Android

**课堂提问助手 —— 走神也能秒回溯老师的问题**

仿 iOS「水课帮」的 Android 实现：麦克风实时收音 → 设备端**真流式离线语音识别** → 轻量规则检测老师提问 → 震动 / 高亮 / 问题回溯 → 本地存档。

`package = com.star.shuikebang` · minSdk 26 · target/compile 34 · Kotlin + Jetpack Compose

![platform](https://img.shields.io/badge/Android-minSdk%2026-3DDC84)
![ui](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![asr](https://img.shields.io/badge/ASR-sherpa--onnx%20Streaming%20Zipformer-FF6B6B)
![privacy](https://img.shields.io/badge/隐私-全本地%20·%20不上传音频-8E7CFF)

</div>

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [整体架构与数据流](#整体架构与数据流)
- [工程目录](#工程目录)
- [快速开始](#快速开始)
- [模型动态下发](#模型动态下发)
- [自有签名（可选）](#自有签名可选)
- [提问检测规则（可调）](#提问检测规则可调)
- [状态岛四级降级](#状态岛四级降级)
- [设置项](#设置项)
- [测试](#测试)
- [真机自测清单](#真机自测清单)
- [隐私边界（明确不做）](#隐私边界明确不做)
- [已知限制与后续](#已知限制与后续)
- [交接文档](#交接文档)

---

## 功能特性

- **真流式离线识别**：基于 sherpa-onnx Streaming Zipformer（INT8 量化），边说边出字，**全程不需要联网**，音频不出设备、不落地保存。
- **中英双语**：默认 small 双语模型（英语课可用），另提供 25MB 纯中文省流模型。
- **两级提问检测**：轻量规则 + 约 35KB 字级极小分类器（非大模型、纯本地、零网络），识别「点名 / 让同学回答」的 L1 预警与「疑问句」的 L2 确认，支持四档灵敏度；L2 命中后还会延迟约 2.2s 做**跨句二次确认**，若识别到老师紧接着自答则自动撤销提醒（可在设置关闭）。
- **即时提醒与回溯**：命中 L2 时两段式震动 + 录制页高亮 + 提问列表沉淀，随时复制问题。
- **麦克风增益**：老师声音偏小时在端侧放大 PCM——自动 AGC（默认，带噪声门与上限、软限幅防爆音）或 2/3/5 倍固定档，提升小声识别率；音频仍只在内存、不落盘。
- **本地课堂档案**：Room 存储每节课的完整转录与提问，按时间浏览、重命名、删除、复制。
- **模型动态下发**：APK 本体不含模型，首次使用下载到应用私有目录，卸载即清除。
- **多下载源可选**：自动 / ghfast / gh-proxy / GitHub 官方，失败自动回退（国内直连友好）。
- **后台可控的状态岛**：小米超级岛 / vivo 原子岛 → 自绘悬浮控制窗 → 前台常驻通知逐级降级，纯本地、**不接入任何 Push**。前台通知可直接「暂停/继续 · 结束并保存」、展开查看最近提问；悬浮窗可拖动、展开看问题并操作，切到其他 App 也能用。
- **暂停 / 继续**：暂停时停止采集与计时但保留会话，随时继续；计时只统计真正录音的时长，通知、悬浮窗、录制页三处都能操作。
- **可选 AI 解答（自带端点）**：录制页点「解答」，把这一句问题发给你自己配置的 OpenAI 兼容端点（OpenAI / DeepSeek / 通义兼容模式 / 硅基流动 / 本地 Ollama 等）直接获取答案；App **不内置任何 API Key、不代理请求**，仅在你主动点击时发送该问题文本，原「分享」系统分享面板保留。

## 技术栈

| 领域 | 选型 | 说明 |
|---|---|---|
| 语言 / 构建 | Kotlin 2.0.20、AGP 8.6.1、Gradle 8.10.2、JVM 17 | KSP 注解处理 |
| UI | Jetpack Compose（BOM 2024.09）、Material3 | 单 Activity + Navigation Compose |
| 离线 ASR | `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.7`（JitPack） | Streaming Zipformer，真流式 |
| 本地存储 | Room 2.6.1 | 只存文本 / 时间戳，不存音频 |
| 偏好存储 | DataStore Preferences | 设置项持久化 |
| 网络 | OkHttp 4.12 | 模型下载（断点续传）+ 用户自配端点的 AI 解答 |
| 解压 | commons-compress 1.27.1 | zip / tar.bz2 |
| 后台 | LifecycleService + 前台服务（microphone 类型） | 录音保活 |

> **体积说明**：release APK 约 12MB，其中业务 dex 经 R8 后仅约 1MB，绝大部分体积是 onnxruntime / sherpa 的 native 库（`.so` 已在包内压缩）。iOS 原版只有 1.82MB 是因为它直接调用系统 `SFSpeechRecognizer`；Android 没有等价的离线系统能力，必须自带运行时，这是平台差异而非业务膨胀。模型不占包体。

## 整体架构与数据流

```mermaid
flowchart LR
    MIC[AudioCapture<br/>16k 单声道, 仅内存] --> ENG[SherpaStreamEngine<br/>真流式解码]
    ENG -->|partial/final 文本| DET[QuestionDetector<br/>规则 + 35KB 极小模型仲裁]
    DET -->|普通讲课行| DB[(Room<br/>转录表)]
    DET -->|L1/L2 提问| DB2[(Room<br/>提问表)]
    DET -->|L2 命中| ALERT[震动 + 状态岛 + UI 高亮]
    MM[ModelManager<br/>下载/解压/校验] -->|filesDir 私有模型| ENG
    CFG[DataStore 设置] -.灵敏度/震动/源.-> DET
    CFG -.下载源.-> MM
    REC[RecordService 前台服务] -.编排.-> MIC
    REC -.编排.-> ENG
    REC -.编排.-> DET
```

核心原则：**音频永远只在内存中流动**；落盘的只有识别后的文字。

## 工程目录

```
app/src/main/java/com/star/shuikebang/
├─ MainActivity.kt / ShuiKeApp.kt        # 单 Activity、应用入口
├─ asr/                                  # 语音识别层
│  ├─ AudioCapture.kt                    #   AudioRecord 采集（音频不落地）
│  ├─ SherpaStreamEngine.kt              #   sherpa-onnx 真流式封装
│  ├─ AsrModels.kt                       #   内置模型清单 / 候选下载源
│  ├─ DownloadSource.kt                  #   下载源选项定义
│  └─ ModelManager.kt                    #   下载、断点续传、解压、sha256
├─ nlp/                                  # 提问检测：规则 + 极小模型仲裁
│  ├─ TextNorm.kt / QuestionRules.kt / QuestionDetector.kt
│  └─ QuestionMlClassifier.kt            #   35KB 字级 MLP，纯 Kotlin 前向（权重在 assets/qclassifier）
├─ data/
│  ├─ db/                                #   Room：会话 / 转录 / 提问三表
│  └─ prefs/SettingsRepository.kt        #   DataStore 设置
├─ ai/                                   # AI 解答：OpenAI 兼容协议 AiProtocol/AiClient（用户自带 Key）
├─ service/                              # RecordService 编排 + RecSession 状态
├─ island/                               # 厂商岛 / 可操作悬浮窗 / 可交互前台通知
├─ feedback/Hapticx.kt                   # 震动
├─ perm/                                 # 麦克风 / 电池优化引导
├─ util/                                 # 剪贴板、时间格式化
└─ ui/                                   # Compose 界面
   ├─ idle/        首页（开始 / 模型 / 历史 / 设置入口）
   ├─ model/       模型下载与下载源选择
   ├─ record/      录制页（实时转录 + 提问卡 + 底部操作）
   ├─ history/     历史列表 / 会话详情
   ├─ settings/    设置主页（二级弹窗选择）/ AI 端点配置页 / 关于与隐私页
   ├─ ai/          AI 解答弹层（加载 / 成功 / 失败 / 未配置四态）
   ├─ component/   复用组件（状态条、提问卡、操作坞）
   └─ theme/       设计令牌（颜色 / 字体 / 主题）
```

## 快速开始

### 1. 环境要求

- JDK 17+（开发机使用 JDK 21 验证通过）
- Android SDK：`compileSdk = targetSdk = 34`，`minSdk = 26`
- 在项目根目录创建 `local.properties`（已被 .gitignore 忽略）：

```properties
sdk.dir=D\:\\apps\\AndroidSDK
```

### 2. 依赖源与代理

- Maven 依赖**统一使用官方源**（gradlePluginPortal / google / mavenCentral / JitPack），见 `settings.gradle.kts`；GitHub Actions 在海外直连即可，**不要加回阿里云镜像**（海外访问会 502 导致 CI 失败）。
- Gradle wrapper distribution 走腾讯云镜像（`gradle/wrapper/gradle-wrapper.properties`），仅加速 wrapper 本身下载。
- 国内本地构建让 Gradle 走本地代理：在**用户全局** `~/.gradle/gradle.properties`（不进仓库）写 `systemProp.https.proxyHost=127.0.0.1`、`systemProp.https.proxyPort=7897`（http 同理）。代理节点必须能访问 `dl.google.com`（AGP / AndroidX 只在该域名）。仓库内 `gradle.properties` 刻意不含代理，以免污染云端 CI。
- 不想依赖代理节点时，可在**用户全局** `~/.gradle/init.d/` 放 init 脚本，把插件/依赖仓库重定向到阿里云镜像（google/central/public/gradle-plugin）+ JitPack；仓库内 `settings.gradle.kts` 始终保持官方源，CI 不受影响（HANDOVER §11 附可用脚本）。

### 3. 构建

```powershell
# Debug：arm64-v8a + x86_64（可跑模拟器）
.\gradlew.bat :app:assembleDebug

# Release 三架构（arm64-v8a / armeabi-v7a / universal），加 -PsplitAbi
.\gradlew.bat :app:assembleRelease -PsplitAbi

# 只打单一 arm64-v8a
.\gradlew.bat :app:assembleRelease

# 单元测试
.\gradlew.bat :app:testDebugUnitTest
```

产物路径：

| 产物 | 路径 | 用途 |
|---|---|---|
| release | `app/build/outputs/apk/release/app-release.apk`（约 12MB） | 真机安装 |
| debug | `app/build/outputs/apk/debug/app-debug.apk` | 模拟器（含 x86_64） |

> 加 `-PsplitAbi` 一次产出 `app-arm64-v8a-release.apk`（约 12MB）、`app-armeabi-v7a-release.apk`（约 11MB）、`app-universal-release.apk`（约 22MB）；不加开关只打 arm64-v8a。Release 优先使用自有正式签名（本机经 local.properties、CI 经 Actions Secrets 注入），未配置时回退 debug 签名。**推送 `v*` tag 时 GitHub Actions 会自动构建三架构并发布 Release**（见 `.github/workflows/android.yml`）。

## 自有签名（可选）

Release 优先使用自有正式签名（仓库 CI 已配置好，本机侧载正式包也按下面配置一次即可）：

```powershell
# 1) 生成 keystore（只需一次，妥善保管，丢了将无法对同一应用覆盖升级）
keytool -genkeypair -v -keystore shuikebang-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shuikebang
```

```properties
# 2) 写入工程根目录 local.properties（已被 .gitignore 忽略，*.jks 同样忽略）
RELEASE_STORE_FILE=shuikebang-release.jks
RELEASE_STORE_PASSWORD=你的密钥库密码
RELEASE_KEY_ALIAS=shuikebang
RELEASE_KEY_PASSWORD=你的密钥密码
```

之后 `:app:assembleRelease` 即自动使用自有签名；四个键缺失时回退 debug 签名并继续构建。`local.properties` 以 UTF-8 读取，密钥库路径含中文也可直接写（建议用正斜杠）。

CI（GitHub Actions）使用 4 个 Repository Secrets：`KEYSTORE_B64`（keystore 的 base64，`base64 -w0 my-release.jks` 生成）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；workflow 在发版时解码为 `ci-release.jks` 并映射成同名 `RELEASE_*` 环境变量，keystore 不进仓库。

## 模型动态下发

模型**不打进 APK**，由本仓库自己的 GitHub Release（tag `asr-models-v1`）分发，只含运行所需的 INT8 四件套：

| 模型 | 适用 | zip 体积 | 解压后 |
|---|---|---|---|
| Streaming Zipformer small（**默认**） | 中英双语 | 50 MB | ≈57 MB |
| Streaming Zipformer zh-14M | 纯中文、省流 | 25.5 MB | ≈29.5 MB |

四件套命名固定：`encoder-*.int8.onnx`、`decoder-*.onnx`（fp32）、`joiner-*.int8.onnx`、`tokens.txt`，zip 为**扁平结构**（解压即四文件，故 `AsrModelSpec.innerDir = ""`）。

- 下载到 `filesDir/models/<modelId>/`，应用私有目录、无需存储权限，卸载随 App 清除。
- 支持断点续传（`.part`）、多源失败自动回退、可选 sha256 校验。
- **网络实测**：GitHub Release 直连在国内会超时；`ghfast.top` / `gh-proxy.com` 公共镜像不走代理可直连，因此默认「镜像优先、官方兜底」。
- **迁移到 Cloudflare R2 / 自建对象存储**：只改 `asr/AsrModels.kt` 中 `BuiltinModels` 的 `archiveUrl / mirrorUrls / sizeBytes`，以及 `DownloadSource.kt` 的源列表，其余代码无需改动。

## 提问检测：规则 + 极小模型（可调）

检测保持轻量、纯本地：高置信规则直通，另用一个约 35KB 的字级极小分类器裁决歧义（不是大模型、无网络、无 native 依赖）。规则在 `nlp/QuestionRules.kt`，判定在 `nlp/QuestionDetector.kt`，模型前向在 `nlp/QuestionMlClassifier.kt`。规则层：

- **强疑问结构**（`ZH_STRONG`，如「什么是 / 为什么 / 哪位 / 是什么」）：命中即较可信；
- **次强结构**（`ZH_STRONG_SOFT`，如「如何」，保守档不判）；
- **弱疑问词**（`ZH_WEAK`，如「什么 / 怎么 / 几个 / 是不是」）：讲课中也高频出现，**单独不判**，需句末语气词 / 点名短语 / 位于句首或句末等旁证；
- **讲课框架词**（`ZH_LECTURE`，如「下面我们 / 区别在于 / 分为」）：命中后否决弱信号（不影响强信号），专治「下面讲几个概念」这类误报；
- **点名短语**分硬 / 软两档（`ZH_CALL_STRONG / ZH_CALL_WEAK`），对应 L1 预警。

四档灵敏度（设置页可调）：

| 档位 | 行为 |
|---|---|
| 灵敏 HIGH | 弱词也判，宁多勿漏；软祈使也给 L1 |
| 均衡 NORMAL（默认） | 强信号 + 有旁证的弱信号 |
| 保守 LOW | 只认问号 / 句末「吗」/ 核心强结构 |
| 关闭 OFF | 只转写，完全不检测、不提醒 |

> 调整误报 / 漏报时，先在 `QuestionRules.kt` 增删词表，再到 `QuestionDetectorTest.kt` 补对应用例（已内置一批「讲课句不得误报」反例），跑 `testDebugUnitTest` 守护。

**极小分类器：处理规则拿不准的歧义**

- 结构：字 embedding 平均(32 维) → 单隐层(32, ReLU) → sigmoid，int8 量化；`app/src/main/assets/qclassifier/` 下 `vocab.txt` + `model.txt` 合计约 35KB，随 APK 打包、离线可用；纯 Kotlin 前向，加载失败自动回退纯规则。
- 融合：问号 / 句末「吗」/ 硬强结构 / 点名等高置信信号由规则直接定；**仅当句中出现疑问信号但规则不敢确认**（疑似自问自答、弱词旁证不足）时才跑模型，概率 ≥ 0.55 才判 L2；纯陈述句不跑模型（省算力、防误报）。
- 重训：编辑 `research/mini_q/train_export.py` 的模板与槽位后运行，会直接覆盖导出到 `app/src/main/assets/qclassifier/`；守护测试见 `QuestionMlClassifierTest.kt`（前向数学 + 真实模型语义/融合）。
- 边界：模型只判单句；**跨句自问自答（上一句问、下一句答）由 `SelfAnswerDetector` + RecordService 延迟确认门处理**——L2 先以普通行上屏、延迟约 2.2s，期间识别到老师自答则撤销、否则升级为提问（可在设置关闭）。目前仅中文走模型，英文仍纯规则。

## 状态岛四级降级

`island/StatusIsland.kt` 统一门面，录制状态按可用性逐级降级，全部为**本地**能力，不依赖推送：

1. **L0 应用内胶囊**：录制页 Compose 状态条，始终可用；
2. **L1 厂商原生岛**：小米超级岛 / vivo 原子岛（`VendorIslandNotifier`，本地通知 extras 实现）；
3. **L2 自绘悬浮控制窗**：`OverlayCapsule`，需悬浮窗权限（默认开启，未授权自动降级并提示）；可拖动、点按展开，显示最近提问并提供暂停/继续、结束、打开、收起操作；
4. **L3 前台常驻通知**：`FgsNotifier`，录音合规要求、始终存在；为可交互通知，可直接暂停/继续、结束并保存，BigText 展开显示最近一条提问。

## 设置项

设置采用「分组 + 二级选择」：主页只显示当前值，点击行弹出单选（灵敏度 / 增益 / 下载源），复杂配置进入子页。DataStore 持久化：

- **提问检测**：灵敏度（灵敏 / 均衡 / 保守 / 关闭，弹窗选）、保留 L1「可能被提问」预警、提问二次确认（延迟确认、撤销自问自答）
- **提醒与悬浮窗**：检测到提问时震动、悬浮控制窗开关（默认开；开启时引导授予悬浮窗权限，未授权降级为通知控制）
- **录音与识别**：麦克风增益（自动 / 关闭 / 2 / 3 / 5 倍，弹窗选）、模型下载源（自动 / ghfast / gh-proxy / GitHub 官方，弹窗选）
- **AI 解答**：进入子页填写端点 Base URL、API Key、模型名，提供常见兼容端点快捷填入；Key 仅存本机
- **其他**：权限与后台保活引导、关于与隐私（版本 / 功能 / 技术栈 / 隐私 / 致谢，含可点击的作者 GitHub 与仓库链接）

> 开始录音时会自动申请麦克风与（Android 13+）通知权限；悬浮窗属系统特殊权限、无法直接弹授权框，首次会弹说明并跳转系统设置。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

当前单测聚焦最易出错的提问检测：正向疑问句、点名 L1、核心问题剥除、**讲课陈述反例（不得误报）**、弱词旁证、四档灵敏度差异、中英文本归一；另含 `AudioPreampTest`（增益/AGC/软限幅边界）、`SelfAnswerDetectorTest`（跨句自问自答判定）、`ArchiveSafetyTest`（解压路径穿越防护）、`AiProtocolTest`（端点规整 / 请求体构造 / 响应解析与错误分支）。

## 真机自测清单

1. 安装 release 包，授予麦克风、通知权限，按引导将 App 加入电池优化白名单（国产 ROM 杀后台重灾区）。
2. 首次点「开始记录」→ 在模型页选择下载源并下载模型（仅这一步联网，建议 WiFi）。
3. 说出「什么是线性回归？」「哪位同学来回答一下」「下面我们讲几个概念」（最后一句**不应**误报），观察：
   - 转录是否流式刷新；问句是否震动、高亮、进入提问列表；讲课句是否被正确放过；
4. 停止后进入「历史」，打开会话查看完整转录与提问、复制文本（重点回归：含提问的会话详情不再闪退）。
5. 切后台 / 锁屏，验证前台服务保活；下拉通知点「暂停/继续/结束」、拖动并展开悬浮窗查看最近提问与操作，再回到 App 验证状态同步。
6. 在「设置」中切换灵敏度、关闭震动、切换下载源后重新录制验证生效。

## 隐私边界（明确不做）

对齐 iOS 原版的产品红线，**不做**以下功能，避免体积与复杂度膨胀：

- ❌ 不保存原始录音（只存识别后的文字）
- ❌ 不导入外部音频文件转写（仅麦克风实时流）
- ❌ 不内置任何 AI 总结 / 大模型问答 / 思维导图，**APK 本地不打包任何大模型**
- ✅ 仅提供一个**可选**「AI 解答」：由用户自填 OpenAI 兼容端点与 Key，主动点击时才把这一句问题文本发往其自填端点（App 不内置 Key、不中转、不收集）；「分享」仍可调起系统分享面板 ACTION_SEND
- ❌ 无云同步、无账号登录（除模型下载与用户主动发起的 AI 解答外无任何网络请求）；`android:allowBackup=false`，课堂文本不进入 Google 系统云备份 / 换机迁移，卸载即彻底清除
- ❌ 无广告、无社区、无分享 Feed

## 已知限制与后续

- 小米超级岛 / vivo 原子岛的通知 extras 按公开文档实现，**尚待目标真机校准**（建议 HyperOS 小米 14/15、OriginOS 4/5 vivo 机型）；不支持时自动降级 L3 通知，功能不受影响。
- Release 使用**自有正式签名**（v0.2.1 起）：本机在 `local.properties` 配置 `RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD`（模板见 `local.properties.example`，文件以 UTF-8 读取、支持中文路径）；CI 经 `KEYSTORE_B64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` 四个 Secrets 注入；任一缺失才回退 debug 签名。
- Release 提供 arm64-v8a / armeabi-v7a / universal 三架构包（CI 加 `-PsplitAbi` 产出）；debug 默认 arm64-v8a + x86_64 以便模拟器调试。
- 后续可选项：watchOS 对应能力在 Android 为手表通知（暂未做）、平板横竖屏自适应。

## 交接文档

继续开发 / 接手前请先读仓库根目录的 **[`HANDOVER.md`](./HANDOVER.md)**，其中记录了：开发环境与构建命令、模型 Release 与 sha256、关键技术决策与踩坑记录、真机待办、以及外部参考链接。
