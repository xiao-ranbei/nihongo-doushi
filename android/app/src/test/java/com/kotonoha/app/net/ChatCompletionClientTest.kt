package com.kotonoha.app.net

import com.kotonoha.app.engine.read.ModelConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ChatCompletionClient] 的纯 JVM 单测：全部用假 [HttpTransport] 注入固定的 [HttpResponse]，
 * 不发真网络。classpath 只有 stdlib + serialization-json（没有 coroutines、没有 Android），
 * 所以这里的所有方法都是阻塞的普通函数，不用 runBlocking。
 */
class ChatCompletionClientTest {

    private val cfg = ModelConfig(
        baseUrl = "https://example.test/v1",
        model = "test-model",
        apiKey = "secret-key-abcd",
        temperature = 0.2,
    )

    /** 记录每次调用的 transport：断言「发了几次、每次长什么样」都靠它 */
    private class Recorder(private val responses: List<HttpResponse>) : HttpTransport {
        val bodies = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        var calls = 0

        override fun post(
            url: String,
            headers: Map<String, String>,
            body: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
        ): HttpResponse {
            this.urls += url
            this.headers += headers
            this.bodies += body
            val response = responses.getOrElse(calls) { responses.last() }
            calls += 1
            return response
        }
    }

    private fun okBody(content: String, finishReason: String = "stop"): String {
        val escaped = JsonPrimitive(content).toString()
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped +
            "},\"finish_reason\":\"" + finishReason + "\"}]}"
    }

    private fun requestJson(index: Int, recorder: Recorder): JsonObject =
        Json.parseToJsonElement(recorder.bodies[index]).jsonObject

    @Test
    fun `请求体只含 model messages temperature stream 四个字段`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("{\"tokens\":[]}"))))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertTrue(result is AnalyzerResult.Ok)
        assertEquals(1, recorder.calls)
        val json = requestJson(0, recorder)
        assertEquals(setOf("model", "messages", "temperature", "stream"), json.keys)
        assertEquals("test-model", json["model"]!!.jsonPrimitive.content)
        assertEquals(false, json["stream"]!!.jsonPrimitive.content.toBoolean())
        val messages = json["messages"] as JsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("sys", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `jsonMode 打开时多一个 response_format 字段`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("{}"))))
        ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = true)
        val json = requestJson(0, recorder)
        assertEquals(
            setOf("model", "messages", "temperature", "stream", "response_format"),
            json.keys,
        )
        assertEquals(
            "json_object",
            json["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `请求头带 Bearer 与 json 内容类型`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("x"))))
        ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        val headers = recorder.headers[0]
        assertEquals("Bearer secret-key-abcd", headers["Authorization"])
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("application/json", headers["Accept"])
    }

    @Test
    fun `地址按 normalizeEndpoint 补全为 chat-completions`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("x"))))
        ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals("https://example.test/v1/chat/completions", recorder.urls[0])
    }

    @Test
    fun `400 且响应体提到 response_format 时去掉该字段重试一次`() {
        val recorder = Recorder(
            listOf(
                HttpResponse(400, "{\"error\":\"unknown field response_format\"}"),
                HttpResponse(200, okBody("{}")),
            ),
        )
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = true)
        assertTrue(result is AnalyzerResult.Ok)
        assertEquals(2, recorder.calls)
        assertTrue(requestJson(0, recorder).containsKey("response_format"))
        assertFalse(requestJson(1, recorder).containsKey("response_format"))
    }

    @Test
    fun `400 重试后仍然失败时只调用两次`() {
        val recorder = Recorder(listOf(HttpResponse(400, "response_format not supported")))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = true)
        assertEquals(2, recorder.calls)
        assertTrue(result is AnalyzerResult.Err)
        val error = (result as AnalyzerResult.Err).error
        assertTrue(error is AnalyzerError.BadRequest)
        assertEquals(400, (error as AnalyzerError.BadRequest).code)
    }

    @Test
    fun `400 但响应体与 response_format 无关时不重试`() {
        val recorder = Recorder(listOf(HttpResponse(400, "{\"error\":\"model not found\"}")))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = true)
        assertEquals(1, recorder.calls)
        assertTrue((result as AnalyzerResult.Err).error is AnalyzerError.BadRequest)
    }

    @Test
    fun `401 与 403 都映射为 Auth`() {
        for (code in listOf(401, 403)) {
            val recorder = Recorder(listOf(HttpResponse(code, "denied")))
            val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
            assertEquals(AnalyzerError.Auth, (result as AnalyzerResult.Err).error)
        }
    }

    @Test
    fun `404 映射为 Endpoint`() {
        val recorder = Recorder(listOf(HttpResponse(404, "no such path")))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.Endpoint, (result as AnalyzerResult.Err).error)
    }

    @Test
    fun `429 映射为 RateLimit`() {
        val recorder = Recorder(listOf(HttpResponse(429, "slow down")))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.RateLimit, (result as AnalyzerResult.Err).error)
    }

    @Test
    fun `500 映射为 Server 并带状态码`() {
        val recorder = Recorder(listOf(HttpResponse(503, "boom")))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.Server(503), (result as AnalyzerResult.Err).error)
    }

    @Test
    fun `其它 4xx 映射为 BadRequest 且响应体截断到 120 字`() {
        val longBody = "x".repeat(400)
        val recorder = Recorder(listOf(HttpResponse(422, longBody)))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        val error = (result as AnalyzerResult.Err).error as AnalyzerError.BadRequest
        assertEquals(422, error.code)
        assertEquals(120, error.body.length)
    }

    @Test
    fun `没有 choices 内容时返回 Malformed`() {
        val bodies = listOf(
            "{\"choices\":[]}",
            "{\"choices\":[{\"message\":{}}]}",
            "{\"id\":\"1\"}",
            "not json at all",
        )
        for (body in bodies) {
            val recorder = Recorder(listOf(HttpResponse(200, body)))
            val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
            assertEquals(AnalyzerError.Malformed, (result as AnalyzerResult.Err).error)
        }
    }

    @Test
    fun `finish_reason 为 length 时返回 Truncated`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("{\"tokens\":", finishReason = "length"))))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.Truncated, (result as AnalyzerResult.Err).error)
    }

    @Test
    fun `SocketTimeoutException 映射为 Timeout`() {
        val transport = HttpTransport { _, _, _, _, _ -> throw SocketTimeoutException("read timed out") }
        val result = ChatCompletionClient(transport).complete(cfg, "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.Timeout, (result as AnalyzerResult.Err).error)
    }

    @Test
    fun `UnknownHostException 映射为 Network 且 detail 不含密钥`() {
        val transport = HttpTransport { _, _, _, _, _ -> throw UnknownHostException("bad-host") }
        val result = ChatCompletionClient(transport).complete(cfg, "sys", "user", jsonMode = false)
        val error = (result as AnalyzerResult.Err).error as AnalyzerError.Network
        assertTrue(error.detail.contains("UnknownHostException"))
        assertFalse(error.detail.contains(cfg.apiKey))
    }

    @Test
    fun `apiKey 为空时返回 NotConfigured 且不发请求`() {
        var called = false
        val transport = HttpTransport { _, _, _, _, _ ->
            called = true
            HttpResponse(200, okBody("x"))
        }
        val result = ChatCompletionClient(transport)
            .complete(cfg.copy(apiKey = ""), "sys", "user", jsonMode = false)
        assertEquals(AnalyzerError.NotConfigured, (result as AnalyzerResult.Err).error)
        assertFalse(called)
    }

    @Test
    fun `content 原样返回且保留首尾空白`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("  {\"a\":1}  "))))
        val result = ChatCompletionClient(recorder).complete(cfg, "sys", "user", jsonMode = true)
        assertEquals("  {\"a\":1}  ", (result as AnalyzerResult.Ok).value)
    }

    @Test
    fun `温度越界时收敛到 0 到 1 之间`() {
        val recorder = Recorder(listOf(HttpResponse(200, okBody("{}"))))
        ChatCompletionClient(recorder)
            .complete(cfg.copy(temperature = 3.5), "sys", "user", jsonMode = false)
        val temperature = requestJson(0, recorder)["temperature"]!!.jsonPrimitive.content
        assertEquals(1.0, temperature.toDouble())
    }
}
