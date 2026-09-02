package com.star.shuikebang.asr

import java.io.File

/**
 * 压缩包解压安全工具：防御 Zip-Slip / Tar-Slip 路径穿越。
 *
 * 纯 JVM 实现、不依赖 Android，便于单元测试。规则：
 *  - 反斜杠归一为正斜杠，去掉前导 '/'（拒绝把绝对路径当绝对路径写）
 *  - 任何路径段为 `..` 直接拒绝
 *  - 最终用 canonical 路径确认结果一定落在目标目录之内
 */
internal object ArchiveSafety {

    /** 把压缩包内条目名安全映射到 [targetDir] 内；非法条目抛 [IllegalArgumentException]，并已建好父目录 */
    fun safeResolve(targetDir: File, rawName: String): File {
        val name = rawName.replace('\\', '/').trim().trimStart('/')
        require(name.isNotEmpty()) { "压缩包条目路径为空" }
        val segments = name.split('/')
        if (segments.any { it == ".." }) {
            throw IllegalArgumentException("路径穿越条目已拒绝：$rawName")
        }
        val root = targetDir.canonicalFile
        val out = File(targetDir, name)
        val outCanonical = out.canonicalFile
        if (outCanonical != root && !outCanonical.path.startsWith(root.path + File.separator)) {
            throw IllegalArgumentException("解压目标越出模型目录，已拒绝：$rawName")
        }
        out.parentFile?.mkdirs()
        return out
    }
}
