package com.star.shuikebang.asr

/** 单个离线模型规格 */
data class AsrModelSpec(
    val id: String,
    val displayName: String,
    val version: String,
    /** 整包地址（zip），与 files 二选一；files 优先 */
    val archiveUrl: String? = null,
    val archiveSha256: String? = null,
    /** archiveUrl 的加速镜像/备用源，按顺序回退（元素为可直接请求的完整 URL） */
    val mirrorUrls: List<String> = emptyList(),
    /** 单文件分发（将来放 R2 / GitHub Release 解包后的文件） */
    val files: List<AsrModelFile> = emptyList(),
    /** 下载体积（用于进度条分母）：archive 时为压缩包字节数，files 时为文件总和 */
    val sizeBytes: Long,
    /** 包内顶层目录；zip 内为扁平结构时留空 */
    val innerDir: String = "",
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
    // bilingual / zh-14M 系列在 sherpa 中注册类型为 "zipformer"
    val modelType: String = "zipformer",
) {
    /** 自动模式候选地址：国内镜像优先（官方 release 直连国内会超时），官方源末尾兜底 */
    val downloadCandidates: List<String>
        get() = mirrorUrls + listOfNotNull(archiveUrl)

    /**
     * 按用户选择的下载源返回候选顺序：首选选中源，失败时仍自动回退到其余源。
     * sourceId 取值见 DownloadSource；mirrorUrls 顺序与镜像源一一对应（0=ghfast,1=gh-proxy）。
     */
    fun candidatesFor(sourceId: String): List<String> {
        val all = downloadCandidates
        val preferred = when (sourceId) {
            DownloadSource.GITHUB -> archiveUrl
            DownloadSource.GHFAST -> mirrorUrls.getOrNull(0)
            DownloadSource.GHPROXY -> mirrorUrls.getOrNull(1)
            else -> null
        } ?: return all
        return listOf(preferred) + all.filter { it != preferred }
    }
}

data class AsrModelFile(
    val name: String,           // 相对 innerDir 的文件名
    val url: String,
    val sha256: String? = null,
    val sizeBytes: Long = 0,
)

/** 远端模型清单 JSON */
data class AsrModelManifest(
    val manifestVersion: Int = 1,
    val recommendedId: String,
    val models: List<AsrModelSpec>,
)

/** 模型下载/就绪状态 */
sealed interface ModelState {
    data object NotExist : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState {
        val percent: Int get() = if (total <= 0) 0 else (downloaded * 100 / total).toInt().coerceIn(0, 100)
    }
    data object Extracting : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * 内置模型清单。
 *
 * 模型托管在本仓库自己的 GitHub Release（tag=[RELEASE_TAG]），只打包运行所需的 int8 四件套，
 * 不使用 k2-fsa 官方 437MB 整包。主源直连失败时按 [GITHUB_MIRROR_PREFIXES] 逐个回退加速镜像；
 * 将来迁移到 Cloudflare R2 时，只需把 archiveUrl 换成 R2 直链。
 */
object BuiltinModels {

    const val RELEASE_TAG = "asr-models-v1"
    private const val OWNER = "STAR-10086"
    private const val REPO = "ShuiKeBang"
    private const val RELEASE_BASE =
        "https://github.com/$OWNER/$REPO/releases/download/$RELEASE_TAG"

    /**
     * 公共 GitHub 加速镜像前缀（拼接在完整 github.com 地址前）。
     * 2026-09-01 实测（不走代理、国内直连）：ghfast.top / gh-proxy.com 返回 200 且大小正确，
     * ghproxy.net SSL 失败已移除；镜像可用性会变化，官方源保留在候选末尾兜底（海外/代理环境）。
     */
    private val GITHUB_MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
    )

    private fun mirrorsOf(asset: String): List<String> {
        val origin = "$RELEASE_BASE/$asset"
        return GITHUB_MIRROR_PREFIXES.map { it + origin }
    }

    /** 首选：中英双语真流式 small，INT8 四件套，zip 50.0MB（解压约 57MB） */
    val SMALL_BILINGUAL = AsrModelSpec(
        id = "streaming-zipformer-small-bilingual-zh-en",
        displayName = "Streaming Zipformer · 中英双语 INT8",
        version = "2023-02-16",
        archiveUrl = "$RELEASE_BASE/small-bilingual-zh-en-int8.zip",
        mirrorUrls = mirrorsOf("small-bilingual-zh-en-int8.zip"),
        sizeBytes = 52_430_525L,
        innerDir = "",
        encoder = "encoder-epoch-99-avg-1.int8.onnx",
        decoder = "decoder-epoch-99-avg-1.onnx",
        joiner = "joiner-epoch-99-avg-1.int8.onnx",
        tokens = "tokens.txt",
    )

    /** 省流：纯中文 14M，zip 25.5MB（解压约 29.5MB） */
    val ZH_14M = AsrModelSpec(
        id = "streaming-zipformer-zh-14m",
        displayName = "Streaming Zipformer · 纯中文省流 14M",
        version = "2023-02-23",
        archiveUrl = "$RELEASE_BASE/zh-14m-int8.zip",
        mirrorUrls = mirrorsOf("zh-14m-int8.zip"),
        sizeBytes = 26_681_115L,
        innerDir = "",
        encoder = "encoder-epoch-99-avg-1.int8.onnx",
        decoder = "decoder-epoch-99-avg-1.onnx",
        joiner = "joiner-epoch-99-avg-1.int8.onnx",
        tokens = "tokens.txt",
    )

    val ALL = listOf(SMALL_BILINGUAL, ZH_14M)
    const val RECOMMENDED_ID = "streaming-zipformer-small-bilingual-zh-en"

    fun byId(id: String): AsrModelSpec = ALL.firstOrNull { it.id == id } ?: SMALL_BILINGUAL
}
