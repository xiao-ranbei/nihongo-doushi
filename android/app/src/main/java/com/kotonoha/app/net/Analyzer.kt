package com.kotonoha.app.net

import com.kotonoha.app.engine.read.AnalysisParser
import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ParseOutcome
import com.kotonoha.app.engine.read.ReadPrompt
import com.kotonoha.app.engine.read.SentenceAnalysis
import com.kotonoha.app.engine.read.WordDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * [AnalyzerError] / [AnalyzerResult] 定义在同包的 ChatCompletionClient.kt 里：
 * 那一层要进纯 JVM 单测，而本文件带 suspend + withContext，单测驱动加载不了。
 * 对调用方来说两者都在 `com.kotonoha.app.net` 包内，import 不受影响。
 */

/** 阅读模块对「模型」的全部需求：解析一句、翻译一句、查一个词 */
interface Analyzer {
    suspend fun analyze(sentence: String, cfg: ModelConfig): AnalyzerResult<SentenceAnalysis>
    suspend fun translate(sentence: String, cfg: ModelConfig): AnalyzerResult<String>
    suspend fun wordDetail(sentence: String, word: String, cfg: ModelConfig): AnalyzerResult<WordDetail>
}

/**
 * [Analyzer] 的 HTTP 实现。
 *
 * 这一层不参与纯 JVM 单测（它 suspend、需要 coroutines），所以它只做三件薄事：
 * 选 system prompt、把异常/解析失败映射成 [AnalyzerError]、用 [Dispatchers.IO] 包住阻塞调用。
 * 真正的可测逻辑都在 [ChatCompletionClient] 与 [AnalysisParser] 里。
 */
class HttpAnalyzer(private val client: ChatCompletionClient = ChatCompletionClient()) : Analyzer {

    override suspend fun analyze(sentence: String, cfg: ModelConfig): AnalyzerResult<SentenceAnalysis> {
        // 单句上限：在这里拦而不是在 complete 里拦，是因为「500 字」是业务规则（一句话的长度），
        // 不是 HTTP 层的规则——翻译与查词走同一个 complete，却不需要这条限制。
        if (sentence.length > MAX_SENTENCE_LENGTH) {
            return AnalyzerResult.Err(AnalyzerError.BadRequest(0, TOO_LONG_MESSAGE))
        }
        val userText = ReadPrompt.wrapUserText(sentence)
        return when (val result = call(cfg, ReadPrompt.ANALYZE_SYSTEM, userText, jsonMode = true)) {
            is AnalyzerResult.Err -> result
            is AnalyzerResult.Ok -> when (val parsed = AnalysisParser.parse(result.value, sentence)) {
                is ParseOutcome.Ok -> AnalyzerResult.Ok(parsed.analysis)
                is ParseOutcome.Failed ->
                    AnalyzerResult.Err(AnalyzerError.ParseFailed(parsed.rawSnippet, parsed.reason))
            }
        }
    }

    override suspend fun translate(sentence: String, cfg: ModelConfig): AnalyzerResult<String> {
        val userText = ReadPrompt.wrapUserText(sentence)
        return when (val result = call(cfg, ReadPrompt.TRANSLATE_SYSTEM, userText, jsonMode = false)) {
            is AnalyzerResult.Err -> result
            // 纯文本模式下模型常把译文包在引号或换行里，trim 掉外层空白才是可直接显示的译文；
            // 内部换行保留（长句译文允许换行）。
            is AnalyzerResult.Ok -> AnalyzerResult.Ok(result.value.trim())
        }
    }

    override suspend fun wordDetail(
        sentence: String,
        word: String,
        cfg: ModelConfig,
    ): AnalyzerResult<WordDetail> {
        val userText = ReadPrompt.wrapUserText(sentence + "\n" + word)
        return when (val result = call(cfg, ReadPrompt.WORD_DETAIL_SYSTEM, userText, jsonMode = false)) {
            is AnalyzerResult.Err -> result
            is AnalyzerResult.Ok -> parseDetail(result.value)
        }
    }

    /** 把阻塞的 HTTP 调用挪到 IO 线程：主线程上跑 60 秒超时的请求会直接 ANR */
    private suspend fun call(
        cfg: ModelConfig,
        systemPrompt: String,
        userText: String,
        jsonMode: Boolean,
    ): AnalyzerResult<String> = withContext(Dispatchers.IO) {
        client.complete(cfg, systemPrompt, userText, jsonMode)
    }

    /**
     * wordDetail 的宽松解析：剥围栏 → 取首个 { 到末个 } → 解 8 字段 DTO。
     *
     * 为什么不复用 AnalysisParser：那个管的是「句子 + token 数组 + 重建校验」，
     * 与这里的 8 个平铺字符串字段没有共同形状；强行合并会让两边都长出分支。
     * 这也是本文件唯一不参与单测的逻辑，所以刻意写得极简（缺字段一律降级空串）。
     */
    private fun parseDetail(content: String): AnalyzerResult<WordDetail> {
        val snippet = content.trim()
        val start = snippet.indexOf('{')
        val end = snippet.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return AnalyzerResult.Err(
                AnalyzerError.ParseFailed(snippet.take(RAW_SNIPPET_LIMIT), "未找到 JSON 对象"),
            )
        }
        val dto = try {
            json.decodeFromString<WordDetailDto>(snippet.substring(start, end + 1))
        } catch (error: Exception) {
            return AnalyzerResult.Err(
                AnalyzerError.ParseFailed(snippet.take(RAW_SNIPPET_LIMIT), error.javaClass.simpleName),
            )
        }
        return AnalyzerResult.Ok(
            WordDetail(
                meaning = dto.meaning,
                dictionaryForm = dto.dictionaryForm,
                usage = dto.usage,
                conjugation = dto.conjugation,
                example = dto.example,
                exampleMeaning = dto.exampleMeaning,
            ),
        )
    }

    companion object {
        /** spec §6.2 的单句上限 */
        const val MAX_SENTENCE_LENGTH = 500

        private const val TOO_LONG_MESSAGE = "单句超过 500 字符"
        private const val RAW_SNIPPET_LIMIT = 200

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * wordDetail 响应的 6 个字段：**键名必须与 [ReadPrompt.WORD_DETAIL_SYSTEM] 要求模型返回的
 * JSON 逐字一致**（meaning / dictionaryForm / usage / conjugation / example / exampleMeaning）。
 *
 * 这里曾经写成参考仓库的 chineseTranslation / explanation / exampleTranslation，与提示词对不上：
 * `ignoreUnknownKeys = true` 会把模型按提示词返回的 meaning、usage、exampleMeaning 当作未知字段丢掉，
 * 三个字段静默降级成空串，UI 上「意思」「用法」「例句译文」三行永远不出现（真机实测复现）。
 * 改键名而不是改提示词：spec §6.3 与 [WordDetail] 的字段口径都是 meaning/usage/exampleMeaning。
 *
 * `pos` / `furigana` 没有对应属性：analyze 的 token 表已经带了这两项，
 * 保留会形成两处来源不一致的隐患；模型多回传时由 ignoreUnknownKeys 忽略。
 * 全字段 String = "" 是为了缺字段时降级空串而非抛异常。
 */
@Serializable
internal class WordDetailDto(
    val meaning: String = "",
    val dictionaryForm: String = "",
    val usage: String = "",
    val conjugation: String = "",
    val example: String = "",
    val exampleMeaning: String = "",
)
