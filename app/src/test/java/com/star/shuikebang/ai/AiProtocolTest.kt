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
}
