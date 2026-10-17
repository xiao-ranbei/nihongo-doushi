package com.nihongodoushi.app.engine

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 跨端一致性测试：读取 classpath 上的 cross-samples.json（由
 * web/tools/generate-cross-samples.js 以 JS 引擎实际输出生成），
 * 逐条断言 Kotlin 引擎输出与 web 端期望完全一致。
 *
 * 条数断言为派生关系（IK-03）：不硬编码具体数字，改与文件内 meta 自描述节
 * 及当前词库规模核对；数据扩容后由生成器重算，测试随源自动适配。
 */

@Serializable
private data class CrossSamplesFile(
    val generatedAt: String,
    val meta: CrossSamplesMeta,
    val verb: List<VerbSample>,
    val adjective: List<AdjectiveSample>,
    val normalization: List<NormalizationSample>,
)

/** 生成器写入的自描述计数（由当前 JS 引擎数据计算）。 */
@Serializable
private data class CrossSamplesMeta(
    val verbSampleCount: Int,
    val adjectiveSampleCount: Int,
    val normalizationSampleCount: Int,
    val sampledVerbIds: List<String>,
)

@Serializable
private data class VerbSample(
    val verbId: String,
    val voiceId: String,
    val inflectionId: String,
    val expected: VerbExpectation,
)

@Serializable
private data class VerbExpectation(
    val written: String,
    val kana: String,
    val explain: String,
)

@Serializable
private data class AdjectiveSample(
    val adjectiveId: String,
    val formId: String,
    val expected: AdjectiveExpectation,
)

@Serializable
private data class AdjectiveExpectation(
    val written: String,
    val kana: String,
    val variants: List<String>,
    val explain: String,
)

@Serializable
private data class NormalizationSample(
    val input: String,
    val expected: String,
)

class CrossSampleConsistencyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val samples: CrossSamplesFile by lazy {
        val stream = CrossSampleConsistencyTest::class.java
            .getResourceAsStream("/cross-samples.json")
            ?: fail("cross-samples.json 不在测试 classpath 上（应位于 android/app/src/test/resources/）")
        val text = stream.use { it.readBytes().decodeToString() }
        json.decodeFromString<CrossSamplesFile>(text)
    }

    private val verbById = VerbData.VERBS.associateBy { it.id }
    private val adjectiveById = AdjectiveData.ADJECTIVES.associateBy { it.id }

    @Test
    fun `verb 组：条数与 meta 自洽，written_kana_explain 与 web 引擎逐条一致`() {
        val sampledIds = samples.verb.map { it.verbId }.distinct()
        assertEquals(samples.meta.sampledVerbIds, sampledIds, "meta.sampledVerbIds 与样例实际覆盖不一致")
        assertEquals(samples.meta.verbSampleCount, samples.verb.size, "meta.verbSampleCount 与样例条数不一致")
        assertEquals(
            sampledIds.size * VerbData.VOICES.size * VerbData.INFLECTIONS.size,
            samples.verb.size,
            "动词样例条数应等于抽样词条数 × 语态数 × 活用形式数",
        )
        samples.verb.forEachIndexed { index, sample ->
            val verb = verbById[sample.verbId]
                ?: fail("未知动词词条 ${sample.verbId}（样例序号 $index）")
            val label = "[${sample.verbId}/${sample.voiceId}/${sample.inflectionId}]"
            val result = VerbEngine.conjugate(verb, sample.voiceId, sample.inflectionId)
            assertEquals(sample.expected.written, result.written, "$label written")
            assertEquals(sample.expected.kana, result.kana, "$label kana")
            assertEquals(
                sample.expected.explain,
                VerbEngine.explain(verb, sample.voiceId, sample.inflectionId),
                "$label explain",
            )
        }
    }

    @Test
    fun `adjective 组：条数等于词条 × 形式，written_kana_variants_explain 与 web 引擎逐条一致`() {
        assertEquals(
            AdjectiveData.ADJECTIVES.size * AdjectiveData.FORMS.size,
            samples.adjective.size,
            "形容词样例应穷举 词条 × 形式",
        )
        assertEquals(samples.meta.adjectiveSampleCount, samples.adjective.size, "meta.adjectiveSampleCount 与样例条数不一致")
        samples.adjective.forEachIndexed { index, sample ->
            val adjective = adjectiveById[sample.adjectiveId]
                ?: fail("未知形容词词条 ${sample.adjectiveId}（样例序号 $index）")
            val label = "[${sample.adjectiveId}/${sample.formId}]"
            val result = AdjectiveEngine.conjugate(adjective, sample.formId)
            assertEquals(sample.expected.written, result.written, "$label written")
            assertEquals(sample.expected.kana, result.kana, "$label kana")
            assertEquals(
                sample.expected.variants,
                AdjectiveEngine.answerVariants(result),
                "$label variants",
            )
            assertEquals(
                sample.expected.explain,
                AdjectiveEngine.explain(adjective, sample.formId),
                "$label explain",
            )
        }
    }

    @Test
    fun `normalization 组：条数与 meta 一致，归一化结果与 web 引擎逐条一致`() {
        assertEquals(samples.meta.normalizationSampleCount, samples.normalization.size, "meta.normalizationSampleCount 与样例条数不一致")
        samples.normalization.forEachIndexed { index, sample ->
            assertEquals(
                sample.expected,
                VerbEngine.normalizeAnswer(sample.input),
                "normalize(\"${sample.input}\")（样例序号 $index）",
            )
        }
    }
}
