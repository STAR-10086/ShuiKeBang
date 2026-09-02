package com.star.shuikebang.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/**
 * 调用用户自配置的 OpenAI 兼容端点回答课堂问题。
 * 仅在用户主动点「AI 解答」时联网，发送的只是这一句问题文本（音频永不上传）。
 */
class AiClient(
    private val http: OkHttpClient = defaultClient,
) {
    suspend fun ask(baseUrl: String, apiKey: String, model: String, question: String): String =
        withContext(Dispatchers.IO) {
            val q = question.trim()
            require(q.isNotBlank()) { "问题为空" }
            if (!AiProtocol.isReady(baseUrl, apiKey)) {
                throw IllegalStateException("尚未配置 AI 端点或 API Key，请到「设置 - AI 解答」填写")
            }
            val json = AiProtocol.buildRequestBody(model, q)
            val req = Request.Builder()
                .url(AiProtocol.chatEndpoint(baseUrl))
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(json.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw RuntimeException("请求失败 HTTP ${resp.code}：${text.take(200)}")
                }
                AiProtocol.parseAnswer(text)
            }
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // 小模型回答可能较慢
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
