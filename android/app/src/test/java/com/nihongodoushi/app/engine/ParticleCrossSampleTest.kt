package com.nihongodoushi.app.engine

import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 助词跨端一致性测试：读取 classpath 上的 particle-samples.json
 * （由 web/tools/generate-particle-data.js 以 web/particle-engine.js 的实际输出生成），
 * 逐字段断言 Kotlin 数据与判题口径和 Web 端一致。
 *
 * 为什么不并入 cross-samples.json：既有 verb/adjective 基线与当前 JS 存在历史差异
 * （形容词 Web 10 形式、Android 7 形式），重生成会连带改动既有断言；助词样例因此单独成文件。
 *
 * 修改题目内容后：先改 web/particle-engine.js，再跑生成器，最后跑本测试。
 */
@Serializable
private data class ParticleSamplesFile(
    val generatedAt: String,
    val lessons: List<ParticleLessonSample>,
    val questions: List<ParticleQuestionSample>,
)

@Serializable
private data class ParticleLessonSample(
    val id: String,
    val title: String,
    val particles: List<String>,
    val beginnerSummary: String,
    val advancedSummary: String,
    val examples: List<ParticleExampleSample>,
)

@Serializable
private data class ParticleExampleSample(
    val sentence: String,
    val contrastGroup: String? = null,
    val contrastNote: String? = null,
)

@Serializable
private data class ParticleQuestionSample(
    val id: String,
    val type: String,
    val lessonId: String,
    val level: String,
    val context: String,
    val before: String? = null,
    val after: String? = null,
    val readingBefore: String? = null,
    val readingAfter: String? = null,
    val translation: String,
    val targetParticle: String? = null,
    val targetReading: String? = null,
    val options: List<String>,
    val acceptedAnswers: List<String>,
    val correctIndexes: List<Int>,
    val answerLabel: String,
    val beginnerExplanation: String,
    val advancedExplanation: String,
    val optionNotes: Map<String, String> = emptyMap(),
)

class ParticleCrossSampleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val samples: ParticleSamplesFile by lazy {
        val stream = ParticleCrossSampleTest::class.java
            .getResourceAsStream("/particle-samples.json")
            ?: error("找不到 particle-samples.json；先运行 node web/tools/generate-particle-data.js")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<ParticleSamplesFile>(text)
    }

    @Test
    fun `样例规模与当前数据一致`() {
        assertEquals(ParticleData.LESSONS.size, samples.lessons.size)
        assertEquals(ParticleData.QUESTIONS.size, samples.questions.size)
        assertEquals(9, samples.lessons.size)
        assertEquals(48, samples.questions.size)
    }

    @Test
    fun `专题文案与 Web 端逐字段一致`() {
        samples.lessons.forEach { sample ->
            val lesson = ParticleData.LESSON_BY_ID[sample.id] ?: error("缺少专题 ${sample.id}")
            assertEquals(sample.title, lesson.title, "${sample.id} title")
            assertEquals(sample.particles, lesson.particles, "${sample.id} particles")
            assertEquals(sample.beginnerSummary, lesson.beginnerSummary, "${sample.id} beginnerSummary")
            assertEquals(sample.advancedSummary, lesson.advancedSummary, "${sample.id} advancedSummary")
            assertEquals(
                sample.examples.map { it.sentence },
                lesson.examples.map { it.sentence },
                "${sample.id} 例句",
            )
            assertEquals(
                sample.examples.map { it.contrastGroup },
                lesson.examples.map { it.contrastGroup },
                "${sample.id} 对比分组",
            )
            assertEquals(
                sample.examples.map { it.contrastNote },
                lesson.examples.map { it.contrastNote },
                "${sample.id} 对比说明",
            )
        }
    }

    @Test
    fun `题目文案与 Web 端逐字段一致`() {
        samples.questions.forEach { sample ->
            val question = ParticleData.QUESTION_BY_ID[sample.id] ?: error("缺少题目 ${sample.id}")
            assertEquals(sample.type, question.type, "${sample.id} type")
            assertEquals(sample.lessonId, question.lessonId, "${sample.id} lessonId")
            assertEquals(sample.level, question.level, "${sample.id} level")
            assertEquals(sample.context, question.context, "${sample.id} context")
            assertEquals(sample.before, question.before, "${sample.id} before")
            assertEquals(sample.after, question.after, "${sample.id} after")
            assertEquals(sample.readingBefore, question.readingBefore, "${sample.id} readingBefore")
            assertEquals(sample.readingAfter, question.readingAfter, "${sample.id} readingAfter")
            assertEquals(sample.translation, question.translation, "${sample.id} translation")
            assertEquals(sample.targetParticle, question.targetParticle, "${sample.id} targetParticle")
            assertEquals(sample.targetReading, question.targetReading, "${sample.id} targetReading")
            assertEquals(sample.options, question.options, "${sample.id} options")
            assertEquals(sample.acceptedAnswers, question.acceptedAnswers, "${sample.id} acceptedAnswers")
            assertEquals(sample.beginnerExplanation, question.beginnerExplanation, "${sample.id} 入门解析")
            assertEquals(sample.advancedExplanation, question.advancedExplanation, "${sample.id} 进阶解析")
            assertEquals(sample.optionNotes, question.optionNotes, "${sample.id} 逐项说明")
        }
    }

    @Test
    fun `判题口径与 Web 端一致`() {
        samples.questions.forEach { sample ->
            val question = ParticleData.QUESTION_BY_ID.getValue(sample.id)
            assertEquals(sample.correctIndexes.toSet(), ParticleEngine.correctIndexes(question), "${sample.id} 正确下标")
            assertEquals(sample.answerLabel, ParticleEngine.answerLabel(question), "${sample.id} 答案标签")
            sample.options.forEachIndexed { index, _ ->
                val expected = index in sample.correctIndexes
                assertEquals(expected, ParticleEngine.isCorrect(question, index), "${sample.id} 第 $index 项判定")
            }
        }
    }

    @Test
    fun `洗牌不改变判定结果`() {
        samples.questions.forEach { sample ->
            val question = ParticleData.QUESTION_BY_ID.getValue(sample.id)
            repeat(5) { seed ->
                val shuffled = ParticleEngine.shuffledOptions(question, kotlin.random.Random(seed))
                assertEquals(sample.options.toSet(), shuffled.options.toSet(), "${sample.id} 洗牌后选项集合")
                shuffled.correctIndexes.forEach { index ->
                    assertTrue(
                        ParticleEngine.isCorrect(question, sample.options.indexOf(shuffled.options[index])),
                        "${sample.id} 洗牌后正确项判错",
                    )
                }
            }
        }
    }
}
