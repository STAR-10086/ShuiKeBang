package com.star.shuikebang.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI 兼容 Chat Completions 协议的纯逻辑封装（不依赖 Android，可单测）。
 *
 * 用户在设置里自填「端点 base url / API Key / 模型名」，App 不内置任何官方 Key 或代理；
 * 任何兼容该协议的服务（OpenAI、DeepSeek、通义、本地 Ollama/LM Studio、各类中转）都可接入。
 */
object AiProtocol {

    const val DEFAULT_MODEL = "gpt-4o-mini"

    const val SYSTEM_PROMPT =
        "你是一名大学课程助教。学生会发给你一句老师在课堂上提出的问题，" +
            "请用简体中文给出准确、简洁、有条理的解答：先给结论或关键定义，再给必要步骤/要点，" +
            "尽量分点、控制在 300 字以内，不要复述问题，不要寒暄。"

    /** 规整 base url：去空白、去尾部斜杠；若用户误填到 /chat/completions 也能容错回退到 /v1 级 */
    fun normalizeBase(raw: String): String {
        var b = raw.trim().trimEnd('/')
        if (b.endsWith("/chat/completions", ignoreCase = true)) {
            b = b.dropLast("/chat/completions".length).trimEnd('/')
        }
        return b
    }

    fun chatEndpoint(base: String): String = normalizeBase(base) + "/chat/completions"

    /** 取出主机名（去端口、去路径、小写），用于判断是否本地/局域网地址 */
    fun hostOf(base: String): String {
        val b = normalizeBase(base)
        val after = b.substringAfter("://", b)
        return after.substringBefore('/').substringBefore(':').lowercase()
    }

    /**
     * 是否本机/局域网端点（允许明文 HTTP、允许无 Key）：
     * localhost / 回环 / 私有网段 10/8、172.16/12、192.168/16、链路本地、CGNAT、IPv6 本地。
     */
    fun isLocalEndpoint(base: String): Boolean {
        val h = hostOf(base)
        if (h.isBlank()) return false
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")) return true
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull()?.let { x -> x in 0..255 } == true }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return when {
                a == 127 -> true                    // 127.0.0.0/8 回环
                a == 10 -> true                     // 10.0.0.0/8
                a == 192 && b == 168 -> true        // 192.168.0.0/16
                a == 172 && b in 16..31 -> true     // 172.16.0.0/12
                a == 169 && b == 254 -> true        // 169.254.0.0/16 链路本地
                a == 100 && b in 64..127 -> true    // 100.64.0.0/10 CGNAT
                h == "0.0.0.0" -> true
                else -> false
            }
        }
        return false
    }

    private fun usesPlainHttp(base: String): Boolean =
        normalizeBase(base).startsWith("http://", ignoreCase = true)

    /**
     * 配置是否可用；返回 null 表示可用，否则返回不可用原因（可直接展示给用户）。
     * 规则：
     * - 必须是 http(s) 地址且主机非空；
     * - 明文 HTTP 只允许本机/局域网（外部服务强制 HTTPS）；
     * - 本机/局域网服务（Ollama/LM Studio）允许不填 Key，外部服务必须有 Key。
     */
    fun notReadyReason(base: String, key: String): String? {
        val b = normalizeBase(base)
        val okScheme = b.startsWith("http://", ignoreCase = true) ||
            b.startsWith("https://", ignoreCase = true)
        if (!okScheme || hostOf(b).isBlank()) return "请填写正确的端点地址（http/https）"
        if (usesPlainHttp(b) && !isLocalEndpoint(b)) {
            return "外部端点必须使用 HTTPS；只有本机/局域网地址允许 http://"
        }
        if (!isLocalEndpoint(b) && key.isBlank()) return "外部端点需要填写 API Key（本地 Ollama/LM Studio 可留空）"
        return null
    }

    fun isReady(base: String, key: String): Boolean = notReadyReason(base, key) == null

    /** 构造请求体 JSON（非流式，低温，system+user 两条消息） */
    fun buildRequestBody(model: String, question: String, systemPrompt: String = SYSTEM_PROMPT): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.put(JSONObject().put("role", "user").put("content", question))
        return JSONObject()
            .put("model", model.ifBlank { DEFAULT_MODEL })
            .put("stream", false)
            .put("temperature", 0.3)
            .put("messages", messages)
            .toString()
    }

    /** 从响应 JSON 取 choices[0].message.content；任何异常结构都转成带原因的错误 */
    fun parseAnswer(respJson: String): String {
        val root = try {
            JSONObject(respJson)
        } catch (t: Throwable) {
            throw IllegalArgumentException("返回不是合法 JSON：${t.message}")
        }
        if (root.has("error")) {
            val msg = root.optJSONObject("error")?.optString("message")
                ?: root.optString("error")
            throw RuntimeException("服务返回错误：${msg ?: "未知"}")
        }
        val choices = root.optJSONArray("choices")
            ?: throw IllegalArgumentException("返回缺少 choices 字段")
        if (choices.length() == 0) throw IllegalArgumentException("choices 为空")
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw IllegalArgumentException("返回缺少 message 字段")
        val content = message.optString("content", "").trim()
        if (content.isBlank()) throw IllegalArgumentException("回答内容为空")
        return content
    }
}
