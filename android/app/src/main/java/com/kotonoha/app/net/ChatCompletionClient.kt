package com.kotonoha.app.net

import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ReadRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 网络层（纯 JVM：只准 import java.* / kotlinx.serialization / engine.read）。
 *
 * 为什么不用 OkHttp 之类的库：项目铁律是不加第三方依赖（D-015）；而且本模块只需要
 * 「发一个 POST、读一个字符串」，`HttpURLConnection` 足够。为了让这一层能被纯 JVM 单测覆盖
 * （测试驱动的 classpath 里没有 Android jar，也没有 coroutines），这里有两个刻意的约束：
 *
 * 1. 全部方法是**同步阻塞**的；suspend 只出现在 [Analyzer] 的实现里（那里才有 coroutines）。
 * 2. HTTP 往返被抽成 [HttpTransport]，单测注入假实现即可，不需要真网络、也不需要 mock 框架。
 *
 * 错误分类只看 HTTP 状态码，不解析错误响应体：同为 401，DeepSeek 返回
 * `{"error":{"message":…}}`、智谱返回 `{"error":{"code":…}}`，body 结构不通用，只有状态行通用
 * （spec §12）。唯一读 body 的地方是 400 的 response_format 重试判定。
 */
fun interface HttpTransport {
    /** 网络层异常（IOException 及其子类）原样抛出，由 client 折叠成 [AnalyzerError] */
    fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResponse
}

/** 一次 HTTP 往返的结果：只要状态码与响应体，够 [ChatCompletionClient] 判定即可 */
data class HttpResponse(val code: Int, val body: String)

/**
 * 一次调用失败的原因（spec §12 的 11 类文案各自对应一个分支）。
 *
 * 为什么分类而不直接带 message：UI 文案要集中在一处维护（设置页的
 * `analyzerErrorText`），错误对象只负责「是哪一类、附带的数字/片段是什么」。
 *
 * 放在本文件而不是 Analyzer.kt：本文件要进纯 JVM 单测，而 `Analyzer.kt` 带 suspend +
 * `withContext(Dispatchers.IO)`，单测驱动的 classpath 里没有 coroutines。两者同属 net 包，
 * 对调用方的 import 完全一致。
 */
sealed interface AnalyzerError {
    /** baseUrl / model / apiKey 有空者 */
    data object NotConfigured : AnalyzerError
    /** UnknownHost / ConnectException / IOException / SSL */
    data class Network(val detail: String) : AnalyzerError
    /** SocketTimeoutException */
    data object Timeout : AnalyzerError
    /** 401 403 */
    data object Auth : AnalyzerError
    /** 404 */
    data object Endpoint : AnalyzerError
    /** 429 */
    data object RateLimit : AnalyzerError
    /** 500..599 */
    data class Server(val code: Int) : AnalyzerError
    /** 其它 4xx，body 已截前 120 字 */
    data class BadRequest(val code: Int, val body: String) : AnalyzerError
    /** 无 choices[0].message.content */
    data object Malformed : AnalyzerError
    /** finish_reason == "length" */
    data object Truncated : AnalyzerError
    data class ParseFailed(val rawSnippet: String, val reason: String) : AnalyzerError
}

sealed interface AnalyzerResult<out T> {
    data class Ok<T>(val value: T) : AnalyzerResult<T>
    data class Err(val error: AnalyzerError) : AnalyzerResult<Nothing>
}

/**
 * 一次 chat/completions 调用。
 *
 * 设计取舍：这里只负责「把请求发出去、把 content 取回来」，
 * 不解析业务结构（那是 [Analyzer] 的事）——解析失败属于 ParseFailed，与网络失败是两类错误，
 * 混在一层会让重试策略（网络可重试、解析失败不该无脑重试）变得说不清。
 */
class ChatCompletionClient(private val transport: HttpTransport = UrlConnectionTransport) {

    /**
     * 发一次 chat/completions 请求，成功返回 `choices[0].message.content` 原文（不解析业务结构）。
     *
     * [jsonMode] = true 时请求体带 `response_format:{"type":"json_object"}`；若因此收到 400 且
     * 响应体含 "response_format"，自动去掉该字段重试一次（仅本次调用内，不持久化）。
     *
     * 返回值里的 content **原样保留**（含前后空白），要不要 trim 由调用方决定：
     * 解析管线自己会剥围栏与取花括号，提前 trim 反而会让 ParseFailed 的 rawSnippet 失真。
     */
    fun complete(
        cfg: ModelConfig,
        systemPrompt: String,
        userText: String,
        jsonMode: Boolean,
    ): AnalyzerResult<String> {
        // 未配置时**不发请求**：空地址会拼出 "…/v1/chat/completions" 这种废 URL，
        // 空密钥则注定 401，两者都不该占用一次网络往返。
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank() || cfg.apiKey.isBlank()) {
            return AnalyzerResult.Err(AnalyzerError.NotConfigured)
        }
        val url = ReadRules.normalizeEndpoint(cfg.baseUrl)
        if (url.isBlank()) {
            return AnalyzerResult.Err(AnalyzerError.NotConfigured)
        }
        val headers = mapOf(
            "Authorization" to "Bearer " + cfg.apiKey,
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )

        var withJsonMode = jsonMode
        var response = send(url, headers, buildBody(cfg, systemPrompt, userText, withJsonMode))
        if (response is AnalyzerResult.Err) {
            return response
        }
        var http = (response as AnalyzerResult.Ok).value
        if (withJsonMode && http.code == 400 && http.body.contains("response_format", ignoreCase = true)) {
            // 兼容接口不认 response_format 的兜底：只重试一次，且只对本次调用生效。
            // 判据用 body 里出现 "response_format"——服务商是否真的因为该字段拒绝，
            // 只看 400 猜不出来（400 也可能是模型名错），body 提到它才算指向明确。
            withJsonMode = false
            response = send(url, headers, buildBody(cfg, systemPrompt, userText, withJsonMode))
            if (response is AnalyzerResult.Err) {
                return response
            }
            http = (response as AnalyzerResult.Ok).value
        }
        if (http.code in 200..299) {
            return contentOf(http.body)
        }
        return AnalyzerResult.Err(errorOf(http.code, http.body))
    }

    /** 包一层 try/catch：网络异常在这一处折叠成 [AnalyzerError]，绝不向调用方抛 */
    private fun send(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AnalyzerResult<HttpResponse> {
        return try {
            AnalyzerResult.Ok(
                transport.post(url, headers, body, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS),
            )
        } catch (error: SocketTimeoutException) {
            AnalyzerResult.Err(AnalyzerError.Timeout)
        } catch (error: IOException) {
            // UnknownHost / Connect / SSL 都是 IOException 的子类，统一归 Network；
            // detail 只放类名 + 简短 message，避免把 URL（可能含查询串）抄进日志。
            AnalyzerResult.Err(AnalyzerError.Network(describe(error)))
        } catch (error: Exception) {
            AnalyzerResult.Err(AnalyzerError.Network(describe(error)))
        }
    }

    private fun describe(error: Throwable): String {
        val name = error.javaClass.simpleName
        val message = error.message?.take(DETAIL_LIMIT) ?: ""
        return if (message.isEmpty()) name else name + ": " + message
    }

    /**
     * 只按状态码分类（spec §12）。鉴权先于路由：DeepSeek 在 key 无效时对任何路径都返回 401，
     * 所以 401/403 一律 Auth，不去猜「是不是地址错了」——那样会让用户改地址改不出结果。
     */
    private fun errorOf(code: Int, body: String): AnalyzerError = when {
        code == 401 || code == 403 -> AnalyzerError.Auth
        code == 404 -> AnalyzerError.Endpoint
        code == 429 -> AnalyzerError.RateLimit
        code in 500..599 -> AnalyzerError.Server(code)
        else -> AnalyzerError.BadRequest(code, body.trim().take(BODY_SNIPPET_LIMIT))
    }

    /**
     * 取 choices[0].message.content。
     *
     * finish_reason == "length" 优先判定为 Truncated：那时 content 可能残缺但仍非空，
     * 拼出来的 token 表会被重建校验打回，不如直接给出「返回被截断」这句可操作的提示。
     */
    private fun contentOf(body: String): AnalyzerResult<String> {
        val root = try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            return AnalyzerResult.Err(AnalyzerError.Malformed)
        }
        val rootObject = root as? JsonObject ?: return AnalyzerResult.Err(AnalyzerError.Malformed)
        val choices = rootObject["choices"] as? JsonArray ?: return AnalyzerResult.Err(AnalyzerError.Malformed)
        val first = choices.firstOrNull() as? JsonObject ?: return AnalyzerResult.Err(AnalyzerError.Malformed)
        val finishReason = (first["finish_reason"] as? JsonPrimitive)?.contentOrNull
        val content = ((first["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
        if (content == null) {
            return if (finishReason == FINISH_LENGTH) {
                AnalyzerResult.Err(AnalyzerError.Truncated)
            } else {
                AnalyzerResult.Err(AnalyzerError.Malformed)
            }
        }
        if (finishReason == FINISH_LENGTH) {
            return AnalyzerResult.Err(AnalyzerError.Truncated)
        }
        return AnalyzerResult.Ok(content)
    }

    /**
     * 请求体：只发 spec §6.2 列出的字段。
     *
     * 绝不发 thinking / reasoning_effort / json_schema：兼容接口对未知字段常直接 400，
     * 而这些字段对本功能没有收益。temperature 做一次防御性收敛——单测之外的调用方
     * （设置页测试连接）可能还没走 validateConfig，NaN 会让序列化直接抛异常。
     */
    private fun buildBody(
        cfg: ModelConfig,
        systemPrompt: String,
        userText: String,
        jsonMode: Boolean,
    ): String {
        val temperature = if (cfg.temperature.isFinite()) cfg.temperature.coerceIn(0.0, 1.0) else DEFAULT_TEMPERATURE
        return buildJsonObject {
            put("model", cfg.model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userText)
                }
            }
            put("temperature", temperature)
            put("stream", false)
            if (jsonMode) {
                putJsonObject("response_format") { put("type", "json_object") }
            }
        }.toString()
    }

    companion object {
        /** spec §6.2：连接 15s、读 60s。写成常量而不是可配项，免得用户把超时调成 0 之后无解 */
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000

        private const val DEFAULT_TEMPERATURE = 0.2
        private const val FINISH_LENGTH = "length"

        /** BadRequest 的响应体截断长度（spec §12：「响应体前 120 字」） */
        private const val BODY_SNIPPET_LIMIT = 120
        private const val DETAIL_LIMIT = 80

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * [HttpTransport] 的 [HttpURLConnection] 实现（spec §6.2）。
 *
 * 单例而不是每次 new：它不持任何状态，唯一的可变物是每次调用内的 connection。
 * 这里**不做错误分类**（那是 client 的事），只把「怎么发、怎么读、什么时候 disconnect」写死一处。
 */
object UrlConnectionTransport : HttpTransport {

    override fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            for ((name, value) in headers) {
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            // ≥400 时 inputStream 会抛 FileNotFoundException，错误体只在 errorStream 上
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }
}
