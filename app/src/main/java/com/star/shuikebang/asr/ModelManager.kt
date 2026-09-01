package com.star.shuikebang.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 模型下载/解压/校验/版本管理。
 * - 模型存于应用私有目录 filesDir/models/<modelId>/，卸载随 App 清除，无需存储权限
 * - 支持单文件列表与 tar.bz2 / zip 整包两种分发
 * - 断点续传（.part）+ sha256 校验
 */
class ModelManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val rootDir: File = File(appContext.filesDir, "models").apply { mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _states = mutableMapOf<String, MutableStateFlow<ModelState>>()

    fun stateFlow(modelId: String): StateFlow<ModelState> =
        _states.getOrPut(modelId) { MutableStateFlow(if (isReady(modelId)) ModelState.Ready else ModelState.NotExist) }

    fun modelDir(spec: AsrModelSpec): File = File(rootDir, spec.id)

    fun isReady(modelId: String): Boolean = isReady(BuiltinModels.byId(modelId))

    fun isReady(spec: AsrModelSpec): Boolean {
        val dir = File(rootDir, spec.id)
        val base = File(dir, spec.innerDir)
        fun exists(rel: String) = File(base, rel).let { it.isFile && it.length() > 0 }
        return exists(spec.encoder) && exists(spec.decoder) &&
            exists(spec.joiner) && exists(spec.tokens)
    }

    /** 返回识别所需四个文件的绝对路径；未就绪抛异常 */
    fun modelPaths(spec: AsrModelSpec): ModelPaths {
        val base = File(modelDir(spec), spec.innerDir)
        return ModelPaths(
            encoder = File(base, spec.encoder).absolutePath,
            decoder = File(base, spec.decoder).absolutePath,
            joiner = File(base, spec.joiner).absolutePath,
            tokens = File(base, spec.tokens).absolutePath,
        )
    }

    fun currentReadySpecOrNull(preferId: String = BuiltinModels.RECOMMENDED_ID): AsrModelSpec? {
        val preferred = BuiltinModels.byId(preferId)
        if (isReady(preferred)) return preferred
        return BuiltinModels.ALL.firstOrNull { isReady(it) }
    }

    /** 下载并解压模型，进度通过 stateFlow 暴露 */
    suspend fun ensureModel(spec: AsrModelSpec) = withContext(Dispatchers.IO) {
        val state = _states.getOrPut(spec.id) { MutableStateFlow(ModelState.NotExist) }
        if (isReady(spec)) {
            state.value = ModelState.Ready
            return@withContext
        }
        val dir = modelDir(spec).apply { mkdirs() }
        try {
            if (spec.files.isNotEmpty()) {
                downloadFiles(spec, dir, state)
            } else {
                downloadAndExtractArchive(spec, dir, state)
            }
            if (!isReady(spec)) error("解压完成但模型文件缺失")
            state.value = ModelState.Ready
        } catch (e: Exception) {
            state.value = ModelState.Failed(e.message ?: "模型下载失败")
            throw e
        }
    }

    private fun downloadFiles(
        spec: AsrModelSpec,
        dir: File,
        state: MutableStateFlow<ModelState>,
    ) {
        val total = spec.files.sumOf { it.sizeBytes }.takeIf { it > 0 } ?: spec.sizeBytes
        var done = 0L
        val base = File(dir, spec.innerDir).apply { mkdirs() }
        for (f in spec.files) {
            val target = File(base, f.name)
            downloadOne(f.url, target, f.sha256) { delta ->
                done += delta
                state.value = ModelState.Downloading(done, total)
            }
        }
    }

    private fun downloadAndExtractArchive(
        spec: AsrModelSpec,
        dir: File,
        state: MutableStateFlow<ModelState>,
    ) {
        val candidates = spec.downloadCandidates
        check(candidates.isNotEmpty()) { "模型没有可用的下载地址" }
        val archiveName = candidates.first().substringAfterLast('?').substringAfterLast('/')
            .ifBlank { "model.bin" }
        val part = File(dir, "$archiveName.part")

        // 主源失败自动切换镜像；断点文件可能来自上一个失败源，切换时删除重来
        var lastError: Throwable? = null
        var downloaded = false
        for (url in candidates) {
            try {
                part.delete()
                downloadOne(url, part, spec.archiveSha256) { delta ->
                    state.value = ModelState.Downloading(part.length(), spec.sizeBytes)
                }
                downloaded = true
                break
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (!downloaded) error("所有下载源均失败：${lastError?.message ?: "未知错误"}")

        state.value = ModelState.Extracting
        when {
            archiveName.endsWith(".tar.bz2") ->
                TarArchiveInputStream(BZip2CompressorInputStream(part.inputStream().buffered()))
                    .use { extractTar(it, dir) }
            archiveName.endsWith(".tar.gz") || archiveName.endsWith(".tgz") ->
                TarArchiveInputStream(java.util.zip.GZIPInputStream(part.inputStream().buffered()))
                    .use { extractTar(it, dir) }
            archiveName.endsWith(".zip") ->
                ZipInputStream(part.inputStream().buffered()).use { extractZip(it, dir) }
            else -> error("不支持的模型包格式：$archiveName")
        }
        part.delete()
    }

    /** 统一单文件下载：支持断点续传，完成后可选 sha256 校验，onBytes 报告本次新增字节 */
    private fun downloadOne(
        url: String,
        target: File,
        sha256: String?,
        onBytes: (Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var existing = if (target.exists()) target.length() else 0L
        val builder = Request.Builder().url(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val resp = http.newCall(builder.build()).execute()
        resp.use { r ->
            if (!r.isSuccessful) error("下载失败 HTTP ${r.code}")
            // 服务器不支持 Range 时从头开始
            if (r.code != 206) existing = 0L
            val body = r.body ?: error("响应为空")
            FileOutputStream(target, r.code == 206).use { out ->
                val buf = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        }
        if (sha256 != null) {
            val actual = sha256Hex(target)
            check(actual.equals(sha256, ignoreCase = true)) { "模型校验失败：$target" }
        }
    }

    private fun extractTar(tar: TarArchiveInputStream, targetDir: File) {
        while (true) {
            val entry = tar.nextEntry as? org.apache.commons.compress.archivers.tar.TarArchiveEntry
                ?: break
            if (entry.isDirectory) continue
            val outFile = File(targetDir, entry.name)
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { tar.copyTo(it, 64 * 1024) }
        }
    }

    private fun extractZip(zip: ZipInputStream, targetDir: File) {
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val outFile = File(targetDir, entry.name)
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { zip.copyTo(it, 64 * 1024) }
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun delete(spec: AsrModelSpec) {
        modelDir(spec).deleteRecursively()
        _states[spec.id]?.value = ModelState.NotExist
    }

    companion object {
        @Volatile
        private var instance: ModelManager? = null
        fun get(context: Context): ModelManager =
            instance ?: synchronized(this) {
                instance ?: ModelManager(context).also { instance = it }
            }
    }
}

data class ModelPaths(
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
)
