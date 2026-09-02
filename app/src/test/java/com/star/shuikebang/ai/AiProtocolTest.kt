package com.star.shuikebang.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProtocolTest {

    @Test
    fun normalizeBase_trimsSlashAndFullPath() {
        assertEquals("https://api.x.com/v1", AiProtocol.normalizeBase(" https://api.x.com/v1/ "))
        assertEquals(
            "https://api.x.com/v1",
            AiProtocol.normalizeBase("https://api.x.com/v1/chat/completions"),
        )
    }

    @Test
    fun chatEndpoint_appendsPath() {
        assertEquals(
            "https://api.x.com/v1/chat/completions",
            AiProtocol.chatEndpoint("https://api.x.com/v1/"),
        )
    }

    @Test
    fun isReady_checksSchemeAndKey() {
        assertTrue(AiProtocol.isReady("https://api.x.com/v1", "sk-1"))
        assertFalse(AiProtocol.isReady("https://api.x.com/v1", " "))
        assertFalse(AiProtocol.isReady("ftp://x", "sk-1"))
        assertFalse(AiProtocol.isReady("https://", "sk-1"))
    }

    @Test
    fun buildRequestBody_containsModelMessagesAndEscapes() {
        val body = JSONObject(AiProtocol.buildRequestBody("", "什么是\"递归\"？"))
        assertEquals(AiProtocol.DEFAULT_MODEL, body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val msgs = body.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        val user = msgs.getJSONObject(1).getString("content")
        assertTrue(user.contains("递归"))
    }

    @Test
    fun parseAnswer_extractsContent() {
        val resp = JSONObject()
            .put("choices", org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", "  答案是 42 ")),
            )).toString()
        assertEquals("答案是 42", AiProtocol.parseAnswer(resp))
    }

    @Test(expected = RuntimeException::class)
    fun parseAnswer_rejectsServiceError() {
        AiProtocol.parseAnswer(JSONObject().put("error", JSONObject().put("message", "bad key")).toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseAnswer_rejectsEmptyChoices() {
        AiProtocol.parseAnswer(JSONObject().put("choices", org.json.JSONArray()).toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseAnswer_rejectsBlankContent() {
        val resp = JSONObject().put(
            "choices", org.json.JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", "  ")),
            ),
        ).toString()
        AiProtocol.parseAnswer(resp)
    }

    @Test
    fun localEndpoint_allowsHttpAndBlankKey() {
        // 模拟器访问宿主机 / 回环 / 常见局域网段：允许 http 且不需要 Key
        assertTrue(AiProtocol.isReady("http://10.0.2.2:11434/v1", ""))
        assertTrue(AiProtocol.isReady("http://127.0.0.1:1234/v1/", ""))
        assertTrue(AiProtocol.isReady("http://192.168.31.7:11434/v1", ""))
        assertTrue(AiProtocol.isReady("http://10.8.0.5/v1", " "))
        assertTrue(AiProtocol.isReady("http://172.16.0.2/v1", ""))
        assertTrue(AiProtocol.isReady("http://localhost:11434/v1", ""))
    }

    @Test
    fun externalHttp_isRejected_httpsNeedsKey() {
        // 外部地址的明文 HTTP 一律拒绝（即使带 Key）
        assertFalse(AiProtocol.isReady("http://api.x.com/v1", "sk-1"))
        // 外部 HTTPS 仍必须有 Key
        assertFalse(AiProtocol.isReady("https://api.x.com/v1", ""))
        assertTrue(AiProtocol.isReady("https://api.x.com/v1", "sk-1"))
    }

    @Test
    fun isLocalEndpoint_classifiesRanges() {
        assertTrue(AiProtocol.isLocalEndpoint("http://127.0.0.1"))
        assertTrue(AiProtocol.isLocalEndpoint("http://192.168.1.1"))
        assertTrue(AiProtocol.isLocalEndpoint("http://172.20.0.1"))
        assertFalse(AiProtocol.isLocalEndpoint("http://172.32.0.1")) // 超出 172.16/12
        assertFalse(AiProtocol.isLocalEndpoint("http://8.8.8.8"))
        assertFalse(AiProtocol.isLocalEndpoint("https://api.openai.com"))
    }
}
