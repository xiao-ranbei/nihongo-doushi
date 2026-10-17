package com.nihongodoushi.app.engine.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 知识点建议规则引擎测试：逐规则命中 / 优先级 / 截断 / 兜底 / 平局顺序。 */
class SuggestionEngineTest {

    private fun snapshot(
        todayAnswers: Int = 10,
        dueMistakes: Int = 0,
        verbPercent: Int = 100,
        adjectivePercent: Int = 100,
        particlePercent: Int = 100,
        currentStreak: Int = 0,
        totalAnswers: Int = 100,
        verbTotal: Int = 44,
    ) = StudySnapshot(
        todayAnswers = todayAnswers,
        dueMistakes = dueMistakes,
        verbPercent = verbPercent,
        adjectivePercent = adjectivePercent,
        particlePercent = particlePercent,
        currentStreak = currentStreak,
        totalAnswers = totalAnswers,
        verbTotal = verbTotal,
    )

    @Test
    fun `R1 到期错题命中并携带正确文案与动作`() {
        val result = SuggestionEngine.suggest(snapshot(dueMistakes = 3))
        assertEquals(1, result.size)
        val s = result.single()
        assertEquals("due-mistakes", s.id)
        assertEquals("有 3 道错题今天到期，复习一遍更牢", s.text)
        assertEquals("进入错题本", s.subText)
        assertEquals(SuggestionAction.MISTAKES, s.action)
    }

    @Test
    fun `R2 今日未学命中并指向学习标签`() {
        val result = SuggestionEngine.suggest(snapshot(todayAnswers = 0))
        assertTrue(result.any { it.id == "today-empty" && it.action == SuggestionAction.STUDY_TAB })
        assertTrue(
            result.none { it.id == "fallback" },
            "today-empty 命中时不应触发兜底",
        )
    }

    @Test
    fun `R3 最弱模块命中文案含模块名与百分比`() {
        val result = SuggestionEngine.suggest(
            snapshot(verbPercent = 30, adjectivePercent = 55, particlePercent = 70),
        )
        assertEquals(listOf("weakest-verb"), result.map { it.id })
        assertEquals("动词掌握度最低（30%），优先补这里", result.single().text)
        assertEquals(SuggestionAction.VERB, result.single().action)
    }

    @Test
    fun `R3 平局按动词形容词助词固定顺序取先者`() {
        val verbFirst = SuggestionEngine.suggest(
            snapshot(verbPercent = 40, adjectivePercent = 40, particlePercent = 60),
        )
        assertEquals("weakest-verb", verbFirst.single().id)

        val adjectiveSecond = SuggestionEngine.suggest(
            snapshot(verbPercent = 50, adjectivePercent = 40, particlePercent = 40),
        )
        assertEquals("weakest-adjective", adjectiveSecond.single().id)

        val particleLast = SuggestionEngine.suggest(
            snapshot(verbPercent = 50, adjectivePercent = 60, particlePercent = 40),
        )
        assertEquals("weakest-particle", particleLast.single().id)
        assertEquals(SuggestionAction.PARTICLE, particleLast.single().action)
    }

    @Test
    fun `R3 掌握度为 0 或达到 60 及以上时不触发`() {
        val allZero = SuggestionEngine.suggest(
            snapshot(todayAnswers = 5, verbPercent = 0, adjectivePercent = 0, particlePercent = 0),
        )
        assertTrue(allZero.none { it.id.startsWith("weakest-") }, "0% 不应触发补最弱")

        val allHigh = SuggestionEngine.suggest(
            snapshot(verbPercent = 60, adjectivePercent = 80, particlePercent = 100),
        )
        assertTrue(allHigh.none { it.id.startsWith("weakest-") }, "60% 及以上不应触发补最弱")
    }

    @Test
    fun `R4 连续三天及以上给出鼓励且不可点`() {
        val result = SuggestionEngine.suggest(snapshot(currentStreak = 5))
        assertEquals(listOf("streak"), result.map { it.id })
        assertEquals("已连续学习 5 天，保持节奏", result.single().text)
        assertEquals(null, result.single().action)
        assertEquals(null, result.single().subText)
    }

    @Test
    fun `R5 新用户全零快照得到今日未学加新用户引导且无最弱模块`() {
        val result = SuggestionEngine.suggest(
            snapshot(
                todayAnswers = 0,
                dueMistakes = 0,
                verbPercent = 0,
                adjectivePercent = 0,
                particlePercent = 0,
                currentStreak = 0,
                totalAnswers = 0,
                verbTotal = 44,
            ),
        )
        assertEquals(listOf("today-empty", "new-user"), result.map { it.id })
        assertEquals("从动词练习开始，先认识 44 个基础词条", result[1].text)
        assertEquals(SuggestionAction.VERB, result[1].action)
        assertTrue(result.none { it.id.startsWith("weakest-") }, "0% 不触发最弱模块")
    }

    @Test
    fun `优先级到期错题排在今日未学之前且最多三条截断`() {
        val allHit = SuggestionEngine.suggest(
            snapshot(
                todayAnswers = 0,
                dueMistakes = 2,
                verbPercent = 30,
                adjectivePercent = 90,
                particlePercent = 90,
                currentStreak = 5,
                totalAnswers = 10,
            ),
        )
        assertEquals(3, allHit.size, "最多返回 3 条")
        assertEquals(
            listOf("due-mistakes", "today-empty", "weakest-verb"),
            allHit.map { it.id },
            "优先级：R1 到期 > R2 今日未学 > R3 最弱",
        )
    }

    @Test
    fun `全部条件不命中时兜底为状态肯定`() {
        val result = SuggestionEngine.suggest(snapshot(todayAnswers = 8))
        assertEquals(listOf("fallback"), result.map { it.id })
        assertEquals("今天已练 8 题，状态不错", result.single().text)
        assertEquals(null, result.single().action)
    }
}
