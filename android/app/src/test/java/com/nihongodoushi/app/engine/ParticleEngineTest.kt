package com.nihongodoushi.app.engine

import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleLevels
import com.nihongodoushi.app.engine.particle.ParticleQuestion
import com.nihongodoushi.app.engine.particle.ParticleQuestionTypes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 助词引擎测试：守卫数据完整性、判题口径与出题调度。
 *
 * 重点守两条容易出错的口径：
 * 1. 多可接受答案的题（he-destination 的 へ／に）不能按 options[0] 判；
 * 2. 一轮题量受题池限制，不允许重复出题凑数。
 */
class ParticleEngineTest {

    private fun q(id: String): ParticleQuestion = ParticleData.QUESTION_BY_ID.getValue(id)

    private fun indexOf(question: ParticleQuestion, text: String): Int =
        question.options.indexOf(text).also { require(it >= 0) { "选项里没有 $text" } }

    @Test
    fun `数据规模与分布符合源数据`() {
        assertEquals(9, ParticleData.LESSONS.size)
        assertEquals(48, ParticleData.QUESTIONS.size)
        assertEquals(24, ParticleEngine.questions(type = ParticleQuestionTypes.FILL).size)
        assertEquals(24, ParticleEngine.questions(type = ParticleQuestionTypes.FUNCTION).size)
        assertEquals(28, ParticleData.QUESTIONS.count { it.level == ParticleLevels.BEGINNER })
        assertEquals(20, ParticleData.QUESTIONS.count { it.level == ParticleLevels.ADVANCED })
    }

    @Test
    fun `每个专题都有题且四格难度题型均有覆盖`() {
        ParticleData.LESSONS.forEach { lesson ->
            val questions = ParticleEngine.questionsOf(lesson.id)
            assertTrue(questions.size >= 4, "${lesson.id} 题量不足")
            listOf(ParticleLevels.BEGINNER, ParticleLevels.ADVANCED).forEach { level ->
                listOf(ParticleQuestionTypes.FILL, ParticleQuestionTypes.FUNCTION).forEach { type ->
                    assertTrue(
                        questions.any { it.level == level && it.type == type },
                        "${lesson.id} 缺少 $level/$type",
                    )
                }
            }
        }
    }

    @Test
    fun `每题选项四个且答案都在选项内`() {
        ParticleData.QUESTIONS.forEach { question ->
            assertEquals(4, question.options.size, "${question.id} 选项数不是 4")
            question.acceptedAnswers.forEach { answer ->
                assertTrue(answer in question.options, "${question.id} 的答案 $answer 不在选项里")
            }
            assertTrue(question.beginnerExplanation.isNotBlank(), "${question.id} 缺入门解析")
            assertTrue(question.advancedExplanation.isNotBlank(), "${question.id} 缺进阶解析")
            assertTrue(ParticleData.LESSON_BY_ID.containsKey(question.lessonId), "${question.id} 专题不存在")
        }
    }

    @Test
    fun `判作用题有目标助词而选助词题没有`() {
        ParticleData.QUESTIONS.forEach { question ->
            if (question.type == ParticleQuestionTypes.FUNCTION) {
                assertTrue(question.targetParticle != null, "${question.id} 判作用题缺目标助词")
                assertTrue(question.translation.isNotBlank())
            } else {
                assertNull(question.targetParticle, "${question.id} 选助词题不该有目标助词")
                assertTrue(question.before != null && question.after != null)
            }
        }
    }

    @Test
    fun `多可接受答案的题两个选项都判对`() {
        val question = q("he-destination")
        assertEquals(setOf(0, 1), ParticleEngine.correctIndexes(question))
        assertEquals("へ／に", ParticleEngine.answerLabel(question))
        assertTrue(ParticleEngine.isCorrect(question, indexOf(question, "へ")))
        assertTrue(ParticleEngine.isCorrect(question, indexOf(question, "に")))
        assertTrue(!ParticleEngine.isCorrect(question, indexOf(question, "で")))
    }

    @Test
    fun `选中另一个可接受答案时给出补充说明`() {
        val question = q("he-destination")
        val pickedNi = indexOf(question, "に")
        assertEquals(listOf("へ"), ParticleEngine.otherAccepted(question, pickedNi))
        assertEquals(listOf("に"), ParticleEngine.otherAccepted(question, indexOf(question, "へ")))
        assertTrue(ParticleEngine.otherAccepted(question, indexOf(question, "で")).isEmpty())
    }

    @Test
    fun `答错时解析同时给入门与进阶`() {
        val question = q("wa-topic")
        assertEquals(question.beginnerExplanation, ParticleEngine.explanation(question, correct = true))
        val wrong = ParticleEngine.explanation(question, correct = false)
        assertTrue(wrong.contains(question.beginnerExplanation))
        assertTrue(wrong.contains(question.advancedExplanation))
    }

    @Test
    fun `提示取所属专题的入门摘要`() {
        assertEquals("话题与焦点：は／が", ParticleEngine.lesson("topic-focus")?.title)
        assertTrue(ParticleEngine.hint(q("wa-topic")).contains("主题"))
        val question = q("wa-topic")
        val distractor = question.options.indexOf("が")
        val accepted = question.options.indexOf("は")
        assertTrue(ParticleEngine.optionNote(question, distractor)!!.contains("焦点"))
        assertNull(ParticleEngine.optionNote(question, accepted))
    }

    @Test
    fun `每个例句都有最小对立字段且分组等于专题`() {
        ParticleData.LESSONS.forEach { lesson ->
            lesson.examples.forEachIndexed { index, example ->
                val label = "${lesson.id} 例句 ${index + 1}"
                assertEquals(lesson.id, example.contrastGroup, "$label 的分组应与专题一致")
                assertTrue((example.contrastNote ?: "").length >= 8, "$label 缺对比说明")
            }
        }
    }

    @Test
    fun `选助词题的逐项说明只覆盖干扰项`() {
        val fill = ParticleEngine.questions(type = ParticleQuestionTypes.FILL)
        assertTrue(fill.isNotEmpty())
        fill.forEach { question ->
            assertTrue(question.optionNotes.isNotEmpty(), "${question.id} 缺 optionNotes")
            question.optionNotes.forEach { (option, note) ->
                assertTrue(option in question.options, "${question.id} 的说明键不在选项内：$option")
                assertTrue(option !in question.acceptedAnswers, "${question.id} 不该给正确答案写说明：$option")
                assertTrue(note.length >= 8, "${question.id} 的说明过短：$option")
            }
        }
    }

    @Test
    fun `选项洗牌保持文本集合且正确下标随文本移动`() {
        val question = q("he-destination")
        val original = question.options.toSet()
        val seenPositions = mutableSetOf<Int>()
        repeat(30) { seed ->
            val result = ParticleEngine.shuffledOptions(question, Random(seed))
            assertEquals(original, result.options.toSet(), "洗牌后文本集合变了")
            assertEquals(2, result.correctIndexes.size, "多答案题应有两个正确下标")
            result.correctIndexes.forEach { index ->
                assertTrue(result.options[index] in question.acceptedAnswers, "正确下标指向了错误文本")
            }
            val firstCorrect = result.correctIndexes.min()
            seenPositions += firstCorrect
        }
        assertTrue(seenPositions.size >= 3, "正确项位置应有变化，实际 $seenPositions")
    }

    @Test
    fun `未知专题或题型直接报错`() {
        assertFailsWith<IllegalArgumentException> { ParticleEngine.questions(lessonId = "nope") }
        assertFailsWith<IllegalArgumentException> { ParticleEngine.questions(type = "judge") }
    }

    @Test
    fun `题量受池限制不重复出题`() {
        assertEquals(4, ParticleEngine.roundSize(4))
        assertEquals(5, ParticleEngine.roundSize(48))
        assertEquals(0, ParticleEngine.roundSize(0))

        val smallPool = ParticleEngine.questionsOf("object-route")
        val round = ParticleEngine.planRound(lessonId = "object-route", random = Random(7))
        assertEquals(smallPool.size, round.size)
        assertEquals(round.size, round.map { it.id }.toSet().size, "一轮内出现了重复题")
    }

    @Test
    fun `专题练习只出本专题的题`() {
        repeat(10) { seed ->
            val round = ParticleEngine.planRound(lessonId = "listing", random = Random(seed))
            assertTrue(round.all { it.lessonId == "listing" })
            assertTrue(round.size <= 5)
        }
    }

    @Test
    fun `错题优先成为本轮首题`() {
        val mistakes = listOf("ni-existence", "de-action-place")
        val round = ParticleEngine.planRound(lessonId = "location", mistakeIds = mistakes, random = Random(3))
        assertEquals("de-action-place", round.first().id, "应取最近加入的错题")
        assertEquals(round.size, round.map { it.id }.toSet().size)
    }

    @Test
    fun `错题不属于当前池时不跨专题插队`() {
        val round = ParticleEngine.planRound(
            lessonId = "listing",
            mistakeIds = listOf("ni-existence"),
            random = Random(5),
        )
        assertTrue(round.none { it.id == "ni-existence" })
        assertTrue(round.all { it.lessonId == "listing" })
    }

    @Test
    fun `综合练习交替题型且不连续同专题`() {
        repeat(20) { seed ->
            val round = ParticleEngine.planRound(random = Random(seed))
            assertEquals(5, round.size)
            assertEquals(5, round.map { it.id }.toSet().size)
            round.zipWithNext().forEach { (a, b) ->
                assertTrue(a.type != b.type, "相邻两题题型相同：${a.id} / ${b.id}")
            }
            // 专题不同是软约束：9 个专题下应基本满足，允许一次兜底
            val sameLesson = round.zipWithNext().count { (a, b) -> a.lessonId == b.lessonId }
            assertTrue(sameLesson <= 1, "相邻同专题过多：seed=$seed, $sameLesson 处")
        }
    }

    @Test
    fun `指定题型时整轮同型`() {
        val round = ParticleEngine.planRound(type = ParticleQuestionTypes.FUNCTION, random = Random(11))
        assertEquals(5, round.size)
        assertTrue(round.all { it.type == ParticleQuestionTypes.FUNCTION })
    }
}
