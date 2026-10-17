package com.nihongodoushi.app.engine

import com.nihongodoushi.app.engine.adjective.AdjectiveConjugation
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.verb.VerbEngine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 形容词引擎单元测试：移植自 web/tests/adjective-engine.test.js 的关键断言，
 * 行为等价（断言目标一致，不必逐条照搬）。
 *
 * JS 的 RangeError/TypeError 在 Kotlin 引擎中统一为 IllegalArgumentException；
 * JS 测试中的 null 入参（conjugate(null)/answerVariants(null)）在 Kotlin 非空类型下
 * 无法构造，对应防御分支不可达，故不移植这两条。
 */
class AdjectiveEngineTest {

    private val formIds = AdjectiveData.FORMS.map { it.id }

    private fun byWord(word: String): AdjectiveEntry =
        AdjectiveData.ADJECTIVES.first { it.word == word }

    private fun assertShape(result: AdjectiveConjugation) {
        assertTrue(result.written.isNotEmpty())
        assertTrue(result.kana.isNotEmpty())
        result.alts.forEach { alt ->
            assertTrue(alt.written.isNotEmpty())
            assertTrue(alt.kana.isNotEmpty())
        }
    }

    @Test
    fun `全词库乘全形式冒烟 written_kana 恒非空`() {
        AdjectiveData.ADJECTIVES.forEach { adjective ->
            formIds.forEach { formId ->
                assertShape(AdjectiveEngine.conjugate(adjective, formId))
            }
        }
    }

    @Test
    fun `い形容词高い的十种形式`() {
        val takai = byWord("高い")
        val expected = mapOf(
            "attributive" to "高い",
            "present" to "高い",
            "presentNegative" to "高くない",
            "past" to "高かった",
            "pastNegative" to "高くなかった",
            "te" to "高くて",
            "conditional" to "高ければ",
            "adverbial" to "高く",
            "mizen" to "高かろう",
            "imperativeRequest" to "高くしてください",
        )
        formIds.forEach { formId ->
            val result = AdjectiveEngine.conjugate(takai, formId)
            assertEquals(expected.getValue(formId), result.written, formId)
            assertEquals(expected.getValue(formId).replace("高", "たか"), result.kana, formId)
            assertEquals(emptyList(), result.alts, formId)
        }
    }

    @Test
    fun `な形容词静か十种形式与礼貌变体`() {
        val shizuka = byWord("静か")
        val main = mapOf(
            "attributive" to "静かな",
            "present" to "静かだ",
            "presentNegative" to "静かじゃない",
            "past" to "静かだった",
            "pastNegative" to "静かではなかった",
            "te" to "静かで",
            "conditional" to "静かなら",
            "adverbial" to "静かに",
            "mizen" to "静かだろう",
            "imperativeRequest" to "静かにしてください",
        )
        formIds.forEach { formId ->
            val result = AdjectiveEngine.conjugate(shizuka, formId)
            assertEquals(main.getValue(formId), result.written, formId)
            assertEquals(main.getValue(formId).replace("静か", "しずか"), result.kana, formId)
        }

        val present = AdjectiveEngine.conjugate(shizuka, "present")
        assertEquals(listOf("静かです"), present.alts.map { it.written })

        val negative = AdjectiveEngine.conjugate(shizuka, "presentNegative")
        assertTrue(negative.alts.any { it.written == "静かではない" })
        assertTrue(negative.alts.any { it.written == "静かじゃありません" })
        assertTrue(negative.alts.any { it.written == "静かではありません" })

        val past = AdjectiveEngine.conjugate(shizuka, "past")
        assertEquals(listOf("静かでした"), past.alts.map { it.written })

        val pastNegative = AdjectiveEngine.conjugate(shizuka, "pastNegative")
        assertTrue(pastNegative.alts.any { it.written == "静かじゃなかった" })
        assertTrue(pastNegative.alts.any { it.written == "静かではありませんでした" })

        // 条件形带「ならば」礼貌变体（与 JS 版一致）
        val conditional = AdjectiveEngine.conjugate(shizuka, "conditional")
        assertEquals(listOf("静かならば"), conditional.alts.map { it.written })
    }

    @Test
    fun `特例いい全部换成よく系列`() {
        val ii = byWord("いい")
        val expected = mapOf(
            "attributive" to "いい",
            "present" to "いい",
            "presentNegative" to "よくない",
            "past" to "よかった",
            "pastNegative" to "よくなかった",
            "te" to "よくて",
            "conditional" to "よければ",
            "adverbial" to "よく",
            "mizen" to "よかろう",
            "imperativeRequest" to "よくしてください",
        )
        formIds.forEach { formId ->
            val result = AdjectiveEngine.conjugate(ii, formId)
            assertEquals(expected.getValue(formId), result.written, formId)
            assertEquals(expected.getValue(formId), result.kana, formId)
        }
    }

    @Test
    fun `きれい是な形容词 否定不接くない`() {
        val kirei = byWord("きれい")
        assertEquals("na", kirei.kind)
        val negative = AdjectiveEngine.conjugate(kirei, "presentNegative")
        assertEquals("きれいじゃない", negative.written)
        assertFalse(negative.written.contains("くない"))
    }

    @Test
    fun `变形结果与罗马字归一化互通 复用verb引擎管线`() {
        val shizuka = byWord("静か")
        val negative = AdjectiveEngine.conjugate(shizuka, "presentNegative")
        assertEquals(
            VerbEngine.normalizeAnswer("shizukajyanai"),
            VerbEngine.normalizeAnswer(negative.kana),
        )
        assertEquals(
            VerbEngine.normalizeAnswer("takanakatta"),
            VerbEngine.normalizeAnswer("たかなかった"),
        )
    }

    @Test
    fun `explain 文本包含目标形式与变化提示`() {
        val takai = byWord("高い")
        assertContains(AdjectiveEngine.explain(takai, "pastNegative"), "变「过去否定」")
        assertContains(AdjectiveEngine.explain(takai, "pastNegative"), "「くなかった」")

        val ii = byWord("いい")
        assertContains(AdjectiveEngine.explain(ii, "past"), "不参与变形")
        assertContains(AdjectiveEngine.explain(ii, "past"), "「よかった」")
        // 条件形/未然形/命令请求有专门文案，不走通用「よく」系列描述（与 JS 版一致）
        assertContains(AdjectiveEngine.explain(ii, "conditional"), "「よければ」")
        assertContains(AdjectiveEngine.explain(ii, "mizen"), "「よかろう」")
        assertContains(AdjectiveEngine.explain(ii, "imperativeRequest"), "「よくしてください」")

        val shizuka = byWord("静か")
        assertContains(AdjectiveEngine.explain(shizuka, "attributive"), "「な」")
        assertContains(AdjectiveEngine.explain(shizuka, "adverbial"), "「に」")
    }

    @Test
    fun `未知形式与非法输入按类型报错`() {
        val takai = byWord("高い")
        assertFailsWith<IllegalArgumentException> { AdjectiveEngine.conjugate(takai, "not-a-form") }
        // JS 版此处为 conjugate(null, "past") 抛 TypeError；Kotlin 非空类型下不可构造。
        // 等价移植：kind 为 i 但词尾不是「い」的词条应报错。
        assertFailsWith<IllegalArgumentException> {
            AdjectiveEngine.conjugate(
                AdjectiveEntry(id = "fake-shizuka", word = "静か", kana = "しずか", meaning = "", kind = "i"),
                "past",
            )
        }
        assertFailsWith<IllegalArgumentException> { AdjectiveEngine.explain(takai, "not-a-form") }
        // explain 还要求词条存在于内置词库（对应 JS 的 ADJECTIVE_BY_ID.has 校验）。
        assertFailsWith<IllegalArgumentException> {
            AdjectiveEngine.explain(
                AdjectiveEntry(id = "fake-shizuka", word = "静か", kana = "しずか", meaning = "", kind = "na"),
                "past",
            )
        }
    }

    @Test
    fun `判定集合覆盖罗马字输入的所有礼貌变体`() {
        val shizuka = byWord("静か")

        val pastNegative = AdjectiveEngine.conjugate(shizuka, "pastNegative")
        val expected = AdjectiveEngine.answerVariants(pastNegative)
            .map { VerbEngine.normalizeAnswer(it) }
            .toSet()
        assertContains(expected, VerbEngine.normalizeAnswer("shizukadewanakatta"))
        assertContains(expected, VerbEngine.normalizeAnswer("しずかじゃなかった"))
        assertContains(expected, VerbEngine.normalizeAnswer("shizukajyanakatta"))

        val past = AdjectiveEngine.conjugate(shizuka, "past")
        val pastExpected = AdjectiveEngine.answerVariants(past)
            .map { VerbEngine.normalizeAnswer(it) }
            .toSet()
        assertContains(pastExpected, VerbEngine.normalizeAnswer("shizukadesita"))
        // 词干裸用不应判对
        assertFalse(pastExpected.contains(VerbEngine.normalizeAnswer("静かだ")))
    }
}
