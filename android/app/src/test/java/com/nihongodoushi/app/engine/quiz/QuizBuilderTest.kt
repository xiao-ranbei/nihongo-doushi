package com.nihongodoushi.app.engine.quiz

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * QuizBuilder / WrongForms 单元测试。
 *
 * 最关键的两条门禁：
 * - 全词库穷举（动词 44×7×18、形容词 15×7）断言选项结构约束恒成立；
 * - 误形表逐条断言「误形 ≠ 正确形态」，防止误形表本身写错。
 */
class QuizBuilderTest {

    private fun norm(text: String) = VerbEngine.normalizeAnswer(text)

    // ------------------------------------------------------------------
    // 1) 词库穷举：动词与形容词
    // ------------------------------------------------------------------

    @Test
    fun `动词全词库穷举 恰好一个正确项且归一化后互不相等`() {
        var cases = 0
        for (verb in VerbData.VERBS) {
            for (voice in VerbData.VOICES) {
                for (inflection in VerbData.INFLECTIONS) {
                    val label = "${verb.id} / ${voice.id} / ${inflection.id}"
                    val correct = VerbEngine.conjugate(verb, voice.id, inflection.id).written
                    val options = QuizBuilder.buildVerbOptions(verb, voice.id, inflection.id, Random(0))

                    assertEquals(1, options.count { it.isCorrect }, "应恰有一个正确项：$label")
                    assertEquals(correct, options.first { it.isCorrect }.text, "正确项文本应等于引擎输出：$label")
                    assertTrue(
                        options.size in 2..QuizBuilder.OPTION_COUNT,
                        "选项数应在 2..${QuizBuilder.OPTION_COUNT}：$label（实为 ${options.size}）",
                    )

                    val norms = options.map { norm(it.text) }
                    assertEquals(norms.size, norms.toSet().size, "归一化后选项应互不相等：$label -> $norms")
                    assertTrue(norms.none { it.isEmpty() }, "选项归一化后不应为空：$label -> $norms")
                    cases += 1
                }
            }
        }
        assertEquals(VerbData.VERBS.size * VerbData.VOICES.size * VerbData.INFLECTIONS.size, cases)
    }

    @Test
    fun `形容词全词库穷举 恰好一个正确项且归一化后互不相等`() {
        var cases = 0
        for (adjective in AdjectiveData.ADJECTIVES) {
            for (form in AdjectiveData.FORMS) {
                val label = "${adjective.id} / ${form.id}"
                val correct = AdjectiveEngine.conjugate(adjective, form.id).written
                val options = QuizBuilder.buildAdjectiveOptions(adjective, form.id, Random(0))

                assertEquals(1, options.count { it.isCorrect }, "应恰有一个正确项：$label")
                assertEquals(correct, options.first { it.isCorrect }.text, "正确项文本应等于引擎输出：$label")
                assertTrue(
                    options.size in 2..QuizBuilder.OPTION_COUNT,
                    "选项数应在 2..${QuizBuilder.OPTION_COUNT}：$label（实为 ${options.size}）",
                )

                val norms = options.map { norm(it.text) }
                assertEquals(norms.size, norms.toSet().size, "归一化后选项应互不相等：$label -> $norms")
                assertTrue(norms.none { it.isEmpty() }, "选项归一化后不应为空：$label -> $norms")
                cases += 1
            }
        }
        assertEquals(AdjectiveData.ADJECTIVES.size * AdjectiveData.FORMS.size, cases)
    }

    // ------------------------------------------------------------------
    // 2) 误形表逐条断言
    // ------------------------------------------------------------------

    @Test
    fun `动词误形表每条都确实不是正确形态`() {
        assertTrue(WrongForms.verbTable.isNotEmpty(), "动词误形表不应为空")
        for (entry in WrongForms.verbTable) {
            val verb = VerbData.VERB_BY_ID[entry.verbId]
                ?: error("误形表引用了不存在的动词 id：$entry")
            assertTrue(entry.voiceId in VerbData.VOICE_IDS, "误形表语态 id 非法：$entry")
            assertTrue(entry.inflectionId in VerbData.INFLECTION_IDS, "误形表形式 id 非法：$entry")

            val correct = VerbEngine.conjugate(verb, entry.voiceId, entry.inflectionId).written
            assertNotEquals(
                norm(correct),
                norm(entry.wrong),
                "误形表条目其实等于正确形态：verb=${entry.verbId} voice=${entry.voiceId} " +
                    "inflection=${entry.inflectionId} 误形=${entry.wrong} 正确=$correct",
            )
            assertTrue(norm(entry.wrong).isNotEmpty(), "误形归一化后为空：$entry")
        }
    }

    @Test
    fun `形容词误形表每条都确实不是正确形态`() {
        assertTrue(WrongForms.adjectiveTable.isNotEmpty(), "形容词误形表不应为空")
        for (entry in WrongForms.adjectiveTable) {
            val adjective = AdjectiveData.ADJECTIVE_BY_ID[entry.adjectiveId]
                ?: error("误形表引用了不存在的形容词 id：$entry")
            assertTrue(entry.formId in AdjectiveData.FORM_BY_ID, "误形表形式 id 非法：$entry")

            val correct = AdjectiveEngine.conjugate(adjective, entry.formId).written
            assertNotEquals(
                norm(correct),
                norm(entry.wrong),
                "误形表条目其实等于正确形态：adjective=${entry.adjectiveId} form=${entry.formId} " +
                    "误形=${entry.wrong} 正确=$correct",
            )
            assertTrue(norm(entry.wrong).isNotEmpty(), "误形归一化后为空：$entry")
        }
    }

    // ------------------------------------------------------------------
    // 3) 优先级：可行时 D3 误形进入选项
    // ------------------------------------------------------------------

    @Test
    fun `可行时经典误形表进入干扰项`() {
        val iku = VerbData.VERB_BY_ID.getValue("iku")
        val verbOptions = QuizBuilder.buildVerbOptions(iku, "base", "te", Random(1))
        val verbD3 = WrongForms.verbWrongForms(iku, "base", "te").map(::norm).toSet()
        assertTrue(
            verbOptions.any { !it.isCorrect && norm(it.text) in verbD3 },
            "行く て形 应至少含一个经典误形干扰项，实际=${verbOptions.map { it.text }}",
        )

        val takai = AdjectiveData.ADJECTIVE_BY_ID.getValue("takai")
        val adjOptions = QuizBuilder.buildAdjectiveOptions(takai, "pastNegative", Random(1))
        val adjD3 = WrongForms.adjectiveWrongForms(takai, "pastNegative").map(::norm).toSet()
        assertTrue(
            adjOptions.any { !it.isCorrect && norm(it.text) in adjD3 },
            "高い 过去否定 应至少含一个经典误形干扰项，实际=${adjOptions.map { it.text }}",
        )
    }

    // ------------------------------------------------------------------
    // 4) 位置随机性 + 确定性
    // ------------------------------------------------------------------

    @Test
    fun `正确项位置随机分布`() {
        val yomu = VerbData.VERB_BY_ID.getValue("yomu")
        val verbPositions = mutableSetOf<Int>()
        repeat(40) { seed ->
            verbPositions.add(
                QuizBuilder.buildVerbOptions(yomu, "base", "te", Random(seed)).indexOfFirst { it.isCorrect },
            )
        }
        assertTrue(verbPositions.size >= 3, "40 个种子下正确项位置应至少 3 种，实际 $verbPositions")

        val takai = AdjectiveData.ADJECTIVE_BY_ID.getValue("takai")
        val adjPositions = mutableSetOf<Int>()
        repeat(40) { seed ->
            adjPositions.add(
                QuizBuilder.buildAdjectiveOptions(takai, "past", Random(seed)).indexOfFirst { it.isCorrect },
            )
        }
        assertTrue(adjPositions.size >= 3, "40 个种子下形容词正确项位置应至少 3 种，实际 $adjPositions")
    }

    @Test
    fun `同一随机种子生成结果确定`() {
        val taberu = VerbData.VERB_BY_ID.getValue("taberu")
        val a = QuizBuilder.buildVerbOptions(taberu, "potential", "dictionary", Random(20261015))
        val b = QuizBuilder.buildVerbOptions(taberu, "potential", "dictionary", Random(20261015))
        assertEquals(a.map { it.text }, b.map { it.text })
        assertEquals(a.map { it.isCorrect }, b.map { it.isCorrect })

        val ii = AdjectiveData.ADJECTIVE_BY_ID.getValue("ii")
        val c = QuizBuilder.buildAdjectiveOptions(ii, "past", Random(7))
        val d = QuizBuilder.buildAdjectiveOptions(ii, "past", Random(7))
        assertEquals(c.map { it.text }, d.map { it.text })
        assertEquals(c.map { it.isCorrect }, d.map { it.isCorrect })
    }
}
