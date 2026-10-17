package com.nihongodoushi.app.engine

import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 动词引擎单元测试：移植自 web/tests/verb-engine.test.js 的关键断言，
 * 行为等价（断言目标一致，不必逐条照搬）。
 *
 * JS 的 RangeError/TypeError 在 Kotlin 引擎中统一为 IllegalArgumentException。
 */
class VerbEngineTest {

    private fun form(verbId: String, voiceId: String, inflectionId: String) =
        VerbEngine.conjugate(VerbData.VERB_BY_ID.getValue(verbId), voiceId, inflectionId)

    @Test
    fun `五段动词的五段词尾 否定与音便正确`() {
        assertEquals("読みます", form("yomu", "base", "masu").written)
        assertEquals("買わない", form("kau", "base", "negative").written)
        assertEquals("泳いで", form("oyogu", "base", "te").written)
        assertEquals("話した", form("hanasu", "base", "past").written)
        assertEquals("待って", form("matsu", "base", "te").written)
        assertEquals("死んだ", form("shinu", "base", "past").written)
    }

    @Test
    fun `行く和一段动词使用正确的特例`() {
        assertEquals("行って", form("iku", "base", "te").written)
        assertEquals("行った", form("iku", "base", "past").written)
        assertEquals("帰れる", form("kaeru", "potential", "dictionary").written)
        assertEquals("食べない", form("taberu", "base", "negative").written)
        assertEquals("食べられる", form("taberu", "potential", "dictionary").written)
    }

    @Test
    fun `五段 一段与不规则动词支持所有派生语态`() {
        assertEquals("読めて", form("yomu", "potential", "te").written)
        assertEquals("読まれます", form("yomu", "passive", "masu").written)
        assertEquals("読ませない", form("yomu", "causative", "negative").written)
        assertEquals("読ました", form("yomu", "causativeShort", "past").written)
        assertEquals("読ませられて", form("yomu", "causativePassive", "te").written)
        assertEquals("読まされる", form("yomu", "causativePassiveShort", "dictionary").written)
        assertEquals("話させられる", form("hanasu", "causativePassiveShort", "dictionary").written)
        assertEquals("食べさす", form("taberu", "causativeShort", "dictionary").written)
        assertEquals("勉強できます", form("benkyou-suru", "potential", "masu").written)
        assertEquals("来させる", form("kuru", "causative", "dictionary").written)
    }

    @Test
    fun `不规则动词的活用读音与辞书写法均可得到`() {
        assertEquals("来ます", form("kuru", "base", "masu").written)
        assertEquals("きます", form("kuru", "base", "masu").kana)
        assertEquals("こない", form("kuru", "base", "negative").kana)
        assertEquals("せず", form("suru", "base", "zu").written)
        assertEquals("勉強すれば", form("benkyou-suru", "base", "conditionalBa").written)
    }

    @Test
    fun `完整活用集合包含指定变形并可生成说明`() {
        val formIds = VerbData.INFLECTIONS.map { it.id }.toSet()
        val voiceIds = VerbData.VOICES.map { it.id }.toSet()
        assertTrue("te" in formIds)
        assertTrue("tari" in formIds)
        assertTrue("causativePassiveShort" in voiceIds)
        assertContains(VerbEngine.explain(VerbData.VERB_BY_ID.getValue("kau"), "base", "negative"), "う")
        assertContains(VerbEngine.explain(VerbData.VERB_BY_ID.getValue("iku"), "base", "te"), "行って")
        assertEquals(
            "先变「短使役形」：「する」变为较简短的「さす」。\n" +
                "再变「ます否定形」：把词尾换成对应的い段，再接「ません」。\n" +
                "六形对应：連用形接礼貌否定「ません」。",
            VerbEngine.explain(VerbData.VERB_BY_ID.getValue("suru"), "causativeShort", "masuNegative"),
        )
    }

    @Test
    fun `语态说明按动词类别返回对应变化规则`() {
        val kaku = VerbData.VERB_BY_ID.getValue("kaku")
        val taberu = VerbData.VERB_BY_ID.getValue("taberu")
        val suru = VerbData.VERB_BY_ID.getValue("suru")
        val iku = VerbData.VERB_BY_ID.getValue("iku")
        assertEquals("五段动词词尾改为对应え段，再接「る」。", VerbEngine.explainVoice(kaku, "potential"))
        assertEquals("一段动词去掉「る」，再接「られる」。", VerbEngine.explainVoice(taberu, "potential"))
        assertEquals("「する」的可能形是「できる」。", VerbEngine.explainVoice(suru, "potential"))
        assertEquals("不做语态变化，保留辞书形。", VerbEngine.explainVoice(iku, "base"))
        assertFailsWith<IllegalArgumentException> { VerbEngine.explainVoice(iku, "unknown") }
    }

    @Test
    fun `所有语态与活用形式都提供常见表达大意`() {
        assertTrue(VerbData.VOICES.all { it.meaning.isNotBlank() })
        assertTrue(VerbData.INFLECTIONS.all { it.meaning.isNotBlank() })
    }

    @Test
    fun `所有内置动词都能生成全部变形组合`() {
        for (verb in VerbData.VERBS) {
            for (voice in VerbData.VOICES) {
                for (inflection in VerbData.INFLECTIONS) {
                    val result = VerbEngine.conjugate(verb, voice.id, inflection.id)
                    val label = verb.id + " / " + voice.id + " / " + inflection.id
                    assertTrue(result.written.isNotEmpty(), label)
                    assertTrue(result.kana.isNotEmpty(), label)
                }
            }
        }
    }

    @Test
    fun `罗马字输入可转换为平假名`() {
        assertEquals("かきます", VerbEngine.toHiraganaFromRomaji("kakimasu"))
        assertEquals("よみました", VerbEngine.toHiraganaFromRomaji("yomimashita"))
        assertEquals("かんじ", VerbEngine.toHiraganaFromRomaji("kanji"))
        assertEquals("まって", VerbEngine.toHiraganaFromRomaji("matt e"))
    }

    @Test
    fun `答案归一化容忍中英标点 片假名与长音符号`() {
        val normalize = VerbEngine::normalizeAnswer
        assertEquals("かきます", normalize("かきます。"))
        assertEquals("かきます", normalize("、かきます"))
        assertEquals("かきます", normalize("かきます」"))
        assertEquals("かきます", normalize("カキマス"))
        assertEquals("かきます", normalize("ｶｷﾏｽ"))
        assertEquals("かきます", normalize("kakimasu."))
        assertEquals("かきます", normalize("kakimasu!"))
        assertEquals("とうきょう", normalize("tōkyō"))
        assertEquals("おかあさん", normalize("okāsan"))
        assertEquals("きてー", normalize("きてー"))
        assertEquals("たべます", normalize("ｔａｂｅｍａｓｕ"))
        assertEquals("かきます", normalize("  ka ki masu  "))
        assertEquals("", normalize(""))
        assertEquals("", normalize("。。。"))
    }
}
