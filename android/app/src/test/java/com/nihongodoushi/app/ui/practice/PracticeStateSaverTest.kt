package com.nihongodoushi.app.ui.practice

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.particle.ParticlePracticeState
import com.nihongodoushi.app.ui.particle.ParticleScope
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IK-04 练习状态进程重启恢复：三个模块的 save → restore 往返。
 *
 * 覆盖：
 * - 筛选 / 范围标签 / 当前题坐标 / 本轮计数 / 判定态原样恢复；
 * - 可空字段（scopeLabel、题目坐标、lessonId）保存时规范化为空串、恢复时解释回 null；
 * - 提示记账随快照恢复（D-020：掌握度只认「本题未用提示且答对」）；
 * - 字段类型损坏（理论上的脏 Bundle）回落默认值不崩溃。
 */
class PracticeStateSaverTest {

    private lateinit var dir: File
    private lateinit var app: AppState

    /** listSaver 的 save 需要 SaverScope；单测里恒允许保存 */
    private val saverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("practice-saver-test").toFile()
        app = AppState(LocalStore(dir))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> snapshotOf(saver: Saver<T, Any>, value: T): List<Any> {
        // 本环境编译器对成员扩展的解析要求：显式接收者匹配扩展接收者（SaverScope），
        // 调度接收者来自 with(saver) 隐式作用域
        val saved = with(saver) { saverScope.save(value) }
        assertNotNull(saved, "save 不应返回 null")
        assertTrue(saved is List<*>, "save 结果应为平铺列表")
        return (saved as List<*>).filterNotNull()
    }

    // ---------- 动词 ----------

    @Test
    fun `动词判定中的状态按坐标重放`() {
        val state = VerbPracticeState(app)
        state.startScope("base", "masu", "五段 · ます形")
        val question = assertNotNull(state.currentQuestion, "开练后应有当前题")
        state.toggleHint()
        state.tapOption(0)
        state.commit()

        val saved = snapshotOf(verbStateSaver(app), state)
        assertEquals(19, saved.size, "动词快照字段数")

        val restored = assertNotNull(verbStateSaver(app).restore(saved))
        assertEquals("all", restored.groupFilter)
        assertEquals("base", restored.voiceFilter)
        assertEquals("masu", restored.inflectionFilter)
        assertEquals("all", restored.levelFilter)
        assertEquals("五段 · ます形", restored.scopeLabel)
        assertEquals(state.roundAnswered, restored.roundAnswered)
        assertEquals(state.roundCorrect, restored.roundCorrect)
        assertEquals(state.roundWrong, restored.roundWrong)
        assertEquals(state.roundComplete, restored.roundComplete)
        assertTrue(restored.judged)
        assertEquals(state.lastCorrect, restored.lastCorrect)
        assertEquals(0, restored.selectedIndex)
        assertTrue(restored.showHint)
        assertTrue(restored.hintUsedThisQuestion, "本题用过提示必须随快照恢复（D-020）")
        assertEquals(state.numberAtStart, restored.numberAtStart)

        val restoredQuestion = assertNotNull(restored.currentQuestion)
        assertEquals(question.verb.id, restoredQuestion.verb.id)
        assertEquals(question.voiceId, restoredQuestion.voiceId)
        assertEquals(question.inflectionId, restoredQuestion.inflectionId)
    }

    @Test
    fun `动词无范围标签时恢复为 null 而非空串`() {
        val state = VerbPracticeState(app)
        state.startAll()

        val saved = snapshotOf(verbStateSaver(app), state)
        assertEquals("", saved[4], "可空标签保存时规范化为空串")

        val restored = assertNotNull(verbStateSaver(app).restore(saved))
        assertNull(restored.scopeLabel)
        assertFalse(restored.roundLabel.contains("·"), "无标签不应出现悬挂间隔号")
    }

    @Test
    fun `动词快照坐标为空串时恢复为无当前题`() {
        val state = VerbPracticeState(app)
        // 字段顺序同 verbStateSaver：坐标三位留空 = 无当前题
        state.restoreSnapshot(
            listOf<Any>(
                "godan", "base", "masu", "all", "范围", false,
                2, 1, 1, false,
                "", "", "",
                1, true, true, false, 3, true,
            ),
        )
        assertNull(state.currentQuestion)
        assertEquals("godan", state.groupFilter)
        assertEquals("范围", state.scopeLabel)
        assertEquals(2, state.roundAnswered)
        assertTrue(state.judged)
        assertTrue(state.hintUsedThisQuestion)
    }

    @Test
    fun `动词快照字段类型损坏时回落默认值`() {
        val state = VerbPracticeState(app)
        state.restoreSnapshot(
            listOf<Any>(123, true, 4.5, 999, 999, "nope", 0, 0, 0, 0, 1, 2, 3, "4", "5", "6", "7", "8", "9"),
        )
        assertEquals("all", state.groupFilter)
        assertEquals("base", state.voiceFilter)
        assertEquals("all", state.inflectionFilter)
        assertEquals("all", state.levelFilter)
        assertNull(state.scopeLabel)
        assertFalse(state.reviewingMistakes)
        assertEquals(0, state.roundAnswered)
        assertFalse(state.judged)
        assertEquals(-1, state.selectedIndex)
        assertEquals(1, state.numberAtStart)
        assertFalse(state.hintUsedThisQuestion)
        assertNull(state.currentQuestion)
    }

    // ---------- 形容词 ----------

    @Test
    fun `形容词判定中的状态按坐标重放`() {
        val state = AdjectivePracticeState(app)
        state.startScope("present", "现在肯定")
        val question = assertNotNull(state.currentQuestion, "开练后应有当前题")
        state.toggleHint()
        state.tapOption(0)
        state.commit()

        val saved = snapshotOf(adjectiveStateSaver(app), state)
        assertEquals(17, saved.size, "形容词快照字段数")

        val restored = assertNotNull(adjectiveStateSaver(app).restore(saved))
        assertEquals("all", restored.groupFilter)
        assertEquals("present", restored.inflectionFilter)
        assertEquals("all", restored.levelFilter)
        assertEquals("现在肯定", restored.scopeLabel)
        assertEquals(state.roundAnswered, restored.roundAnswered)
        assertTrue(restored.judged)
        assertEquals(state.lastCorrect, restored.lastCorrect)
        assertEquals(0, restored.selectedIndex)
        assertTrue(restored.showHint)
        assertTrue(restored.hintUsedThisQuestion)

        val restoredQuestion = assertNotNull(restored.currentQuestion)
        assertEquals(question.adjective.id, restoredQuestion.adjective.id)
        assertEquals(question.formId, restoredQuestion.formId)
    }

    @Test
    fun `形容词快照坐标为空串时恢复为无当前题`() {
        val state = AdjectivePracticeState(app)
        state.restoreSnapshot(
            listOf<Any>(
                "all", "present", "all", "", false,
                1, 1, 0, false,
                "", "",
                -1, false, false, false, 2, false,
            ),
        )
        assertNull(state.currentQuestion)
        assertNull(state.scopeLabel)
        assertEquals(1, state.roundAnswered)
    }

    // ---------- 助词 ----------

    @Test
    fun `助词专题轮次按选项顺序原样恢复`() {
        val state = ParticlePracticeState(app)
        state.start(ParticleScope.Lesson("topic-focus"))
        val before = assertNotNull(state.current, "开轮后应有当前题")
        state.toggleHint()

        val saved = snapshotOf(particleStateSaver(app), state)
        assertEquals(14, saved.size, "助词快照字段数")
        assertEquals("lesson", saved[0])
        assertEquals("topic-focus", saved[1])

        val restored = assertNotNull(particleStateSaver(app).restore(saved))
        val scope = restored.scope
        assertTrue(scope is ParticleScope.Lesson, "范围类型应恢复")
        assertEquals("topic-focus", (scope as ParticleScope.Lesson).lessonId)
        assertEquals(state.total, restored.total)
        val after = assertNotNull(restored.current)
        assertEquals(before.question.id, after.question.id)
        assertEquals(before.options, after.options, "选项顺序应原样保留")
        assertEquals(before.correctIndexes, after.correctIndexes, "正确下标按选项重算应一致")
        assertTrue(restored.hintOn)
        assertTrue(before.question.id in restored.hintUsedIds, "本轮提示记录应恢复")
    }

    @Test
    fun `助词综合与复习范围往返`() {
        val all = ParticlePracticeState(app)
        all.start(ParticleScope.All)
        val restoredAll = assertNotNull(particleStateSaver(app).restore(snapshotOf(particleStateSaver(app), all)))
        assertEquals(ParticleScope.All, restoredAll.scope)

        val review = ParticlePracticeState(app)
        review.start(ParticleScope.Review)
        val restoredReview = assertNotNull(particleStateSaver(app).restore(snapshotOf(particleStateSaver(app), review)))
        assertEquals(ParticleScope.Review, restoredReview.scope)
    }

    @Test
    fun `助词空轮次保存为空串并恢复为空轮次`() {
        val state = ParticlePracticeState(app)
        state.start(ParticleScope.Review) // 无错题 → 空轮次
        assertTrue(state.round.isEmpty())

        val saved = snapshotOf(particleStateSaver(app), state)
        assertEquals("", saved[2], "空轮次 payload 为空串")

        val restored = assertNotNull(particleStateSaver(app).restore(saved))
        assertTrue(restored.round.isEmpty())
        assertEquals(0, restored.index)
    }

    @Test
    fun `助词快照字段类型损坏时回落默认值`() {
        val state = ParticlePracticeState(app)
        state.restoreSnapshot(
            listOf<Any>(42, 999, 99, "x", true, 0, false, 1, 2, 3, 4, "5", "6", "7"),
        )
        assertEquals(ParticleScope.All, state.scope)
        assertTrue(state.round.isEmpty())
        assertEquals(-1, state.selectedIndex)
        assertFalse(state.judged)
        assertEquals(0, state.answered)
        assertFalse(state.hintUsedThisQuestion)
    }
}
