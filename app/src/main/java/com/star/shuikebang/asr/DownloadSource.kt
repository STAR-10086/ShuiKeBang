package com.star.shuikebang.asr

/** 模型下载源选项（纯客户端下载，不涉及推送/账号） */
object DownloadSource {
    const val AUTO = "auto"
    const val GHFAST = "ghfast"
    const val GHPROXY = "ghproxy"
    const val GITHUB = "github"

    data class Option(val id: String, val label: String, val hint: String)

    val OPTIONS = listOf(
        Option(AUTO, "自动选择（推荐）", "国内加速镜像优先，失败自动切换下一个源"),
        Option(GHFAST, "ghfast 加速镜像", "国内可直连的公共 GitHub 加速"),
        Option(GHPROXY, "gh-proxy 加速镜像", "国内可直连的公共 GitHub 加速"),
        Option(GITHUB, "GitHub 官方源", "海外网络或已挂代理时使用，国内直连可能超时"),
    )

    fun labelOf(id: String): String = OPTIONS.firstOrNull { it.id == id }?.label ?: OPTIONS[0].label
}
