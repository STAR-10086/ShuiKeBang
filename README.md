# 水课帮 Android（com.star.shuikebang）

仿照 iOS「水课帮-课堂提问助手」的 Android 实现：麦克风实时收音 → 设备端**真流式离线 ASR**（sherpa-onnx Streaming Zipformer，中英双语）→ 轻量规则两级检测老师提问 → 震动 + 高亮 + 问题回溯 → Room 本地存储。**不保存音频、无账号、无云同步、模型动态下发不打进 APK。**

## 一、构建

环境：JDK 17+（本机用 JDK 21）、Android SDK（compileSdk/targetSdk 34、minSdk 26）。

```powershell
# local.properties 指定 SDK
sdk.dir=D\:\\apps\\AndroidSDK

# Debug（含 arm64-v8a + x86_64，方便模拟器）
.\gradlew.bat :app:assembleDebug

# Release（仅 arm64-v8a，R8 + 资源压缩 + so 压缩，用 debug 签名产出可直接安装包）
.\gradlew.bat :app:assembleRelease

# 提问检测单元测试
.\gradlew.bat :app:testDebugUnitTest
```

- 依赖走阿里云镜像 + JitPack；如需代理见 `gradle.properties` 末尾（当前配置 127.0.0.1:7897，不用可删）。
- Gradle 8.10.2 distribution 走腾讯云镜像（见 `gradle/wrapper/gradle-wrapper.properties`）。

产物：
- `app/build/outputs/apk/release/app-release.apk`（约 12.4 MB，真机安装用这个）
- `app/build/outputs/apk/debug/app-debug.apk`（含 x86_64，模拟器用）

体积说明：iOS 原版仅 1.82MB 是因为用了系统 `SFSpeechRecognizer`；Android 没有等价系统能力，必须自带 onnxruntime + sherpa native 库（release 内压缩后约 11MB），这是平台差异，非业务代码膨胀。业务 dex 经 R8 后仅约 1MB，**模型不占包体**。

## 二、模块结构（39 个 Kotlin 文件）

| 包 | 职责 |
|---|---|
| `asr/` | `AsrModels` 内置模型清单；`ModelManager` OkHttp 断点续传 + sha256 校验 + tar.bz2/zip 解压到私有目录；`AudioCapture` AudioRecord 16k/mono 采集（音频只在内存）；`SherpaStreamEngine` 真流式识别封装 |
| `nlp/` | `TextNorm` 文本归一、`QuestionRules` 中英规则表、`QuestionDetector` 两级判定（L1 点名祈使预警 / L2 疑问词·问号·句末"吗"确认），纯规则无大模型 |
| `data/db/` | Room 三表：课堂会话 / 转录行 / 提问（外键级联），Repository 封装 CRUD |
| `service/` | `RecSession` 全局录制状态（StateFlow + 提问事件 SharedFlow）；`RecordService` LifecycleService 串联「模型→引擎→采集→检测→入库→震动→状态岛」，前台麦克风服务保活 |
| `island/` | 四级状态展示门面 `StatusIsland`：L1 小米超级岛/vivo 原子岛（纯本地通知，无 Push）→ L2 `OverlayCapsule` 悬浮胶囊（需悬浮窗权限，默认关）→ L3 `FgsNotifier` 常驻通知 → L4 应用内状态条 |
| `ui/` | Compose：待机首页 / 模型下载 / 录制页（转录流+提问卡）/ 历史列表 / 会话详情；设计令牌见 `ui/theme` |
| `perm/` `feedback/` `util/` | 权限与电池优化引导、震动、时间格式化、剪贴板 |

## 三、模型动态下发

- 内置两个可选模型（`asr/AsrModels.kt` 的 `BuiltinModels`）：
  - **中英双语 small（默认，推荐）**：zip 50MB / 解压约 57MB，英语课也能识别；
  - 纯中文 zh-14M：zip 25.5MB / 解压约 29.5MB，省流。
- 模型只含运行所需的 int8 四件套（encoder/decoder/joiner/tokens），托管在**本仓库自己的 GitHub Release**（tag `asr-models-v1`），不使用 k2-fsa 官方 437MB 整包；主源失败自动回退公共加速镜像（ghfast.top 等，可在源码中调整）。
- 首次开启识别时下载到 `filesDir/models/<id>/`，卸载即清除；只在下载时用网络，识别全程本地。
- **迁移到 Cloudflare R2 / 换源**：只改 `BuiltinModels` 里的 archiveUrl、mirrorUrls、sizeBytes 即可，其余不动。

## 四、真机测试步骤（重点）

1. 安装 release 包 → 授予麦克风、通知权限；按引导关闭电池优化（国产 ROM 杀后台重灾区）。
2. 首次点「开始记录」→ 模型下载页拉取流式模型（约 30MB，需联网一次）。
3. 对着手机说「什么是线性回归？」「哪位同学来回答一下」验证：
   - 转录流式刷新；问句命中后震动、高亮、进入提问列表；
   - 停止后在「历史」里能看到会话、全部转录与提问，可复制。
4. 切后台验证前台服务保活与通知栏常驻状态。

## 五、待真机校准 / 已知限制

- **小米超级岛 / vivo 原子岛的 extras 字段按公开文档实现，未经真机验证**，需在目标机型（建议 HyperOS 小米14/15、OriginOS 4/5 vivo 机型）上对照显示效果微调 `VendorIslandNotifier`；不支持的机型自动降级到 L3 常驻通知，功能不受影响。
- L2 悬浮胶囊 `StatusIsland.overlayEnabled` 默认关闭，验证 SYSTEM_ALERT_WINDOW 链路后可在设置里开放。
- 当前仅 arm64-v8a（覆盖 2019 年后绝大多数真机）；需要 armeabi-v7a 老机型在 `app/build.gradle.kts` 的 abiFilters 加回。
- 「问 AI」按钮当前走系统分享面板（ACTION_SEND），不内置任何大模型能力，符合无云/无大模型约束。
- Release 当前复用 debug 签名，正式上架前换成自有 keystore。

## 六、明确不做（对齐原版）

不保存原始录音、不导入外部音频、无 AI 总结/问答/思维导图、无云同步/账号、无广告/社区/分享。
