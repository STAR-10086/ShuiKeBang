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

    /** 配置是否可用：base 是 http(s) 地址且 Key 非空（本地/自建服务可能无 Key，但这里仍要求填写，避免误触） */
    fun isReady(base: String, key: String): Boolean {
        val b = normalizeBase(base)
        val okScheme = b.startsWith("http://", ignoreCase = true) ||
            b.startsWith("https://", ignoreCase = true)
        return okScheme && b.length > "http://".length && key.isNotBlank()
    }

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
