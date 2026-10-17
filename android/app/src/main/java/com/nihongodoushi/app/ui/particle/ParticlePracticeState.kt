package com.nihongodoushi.app.ui.particle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleQuestion
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.practice.OptionState
import kotlin.random.Random

/** 助词练习范围（对应 spec §1.2 的 scope 三态）。 */
sealed class ParticleScope {
    data class Lesson(val lessonId: String) : ParticleScope()
    data object All : ParticleScope()
    data object Review : ParticleScope()
}

/** 一轮里的一道题：题目 + 洗牌后的选项 + 正确下标。 */
data class ParticleRoundItem(
    val question: ParticleQuestion,
    val options: List<String>,
    val correctIndexes: Set<Int>,
)

/**
 * 助词练习状态机：idle → picked → judged → next / roundEnd。
 *
 * 口径（与 spec 对齐）：
 * - 判题用 `correctIndexes` 集合，多答案题选任一正确项都算对；
 * - 掌握度只认「本题未用提示且答对」（D-020）；
 * - 答错写入错题目 ID 数组；复习模式答对即移出（D-017）；
 * - 一轮题量由引擎按题池决定（§3.4），不重复出题；
 * - 同一题在本轮内再次出现且上次用过提示时，隐藏提示按钮（§4.6）。
 */
class ParticlePracticeState(
    internal val app: AppState,
    private val random: Random = Random.Default,
) {

    companion object {
        const val DOUBLE_TAP_MS = 300L
        const val ROUND_SIZE = 5
    }

    var scope: ParticleScope = ParticleScope.All
        private set

    var round by mutableStateOf<List<ParticleRoundItem>>(emptyList())
        private set

    var index by mutableStateOf(0)
        private set

    var selectedIndex by mutableStateOf(-1)
        private set

    var judged by mutableStateOf(false)
        private set

    var lastCorrect by mutableStateOf(false)
        private set

    var hintOn by mutableStateOf(false)
        private set

    var roundComplete by mutableStateOf(false)
        private set

    var roundCorrect by mutableStateOf(0)
        private set

    var roundWrong by mutableStateOf(0)
        private set

    var answered by mutableStateOf(0)
        private set

    var toast by mutableStateOf<String?>(null)
        private set

    /** 本轮内用过提示的题目 ID：用于「同题第二次出现强制无提示」 */
    private var hintUsedIds by mutableStateOf(setOf<String>())

    /** 当前题是否用过提示（只在切题时复位，切换开关不回滚） */
    private var hintUsedThisQuestion by mutableStateOf(false)

    private var lastTapIndex = -1
    private var lastTapTime = 0L

    val current: ParticleRoundItem? get() = round.getOrNull(index)

    val total: Int get() = round.size

    val reviewing: Boolean get() = scope is ParticleScope.Review

    /** 辅助信息开关：显示提示时同时显示读音（对齐答题契约 §4.5） */
    val kanaVisible: Boolean get() = hintOn || judged

    val hintEnabled: Boolean
        get() {
            val question = current?.question ?: return false
            return !judged && question.id !in hintUsedIds
        }

    val progress: Float
        get() = if (total == 0) 0f else (index + 1).toFloat() / total

    val progressLabel: String get() = "${if (total == 0) 0 else index + 1}/$total"

    val roundLabel: String
        get() = if (roundComplete) "本轮完成" else "第 ${index + 1} / $total 题"

    val statsLabel: String
        get() {
            val totalAnswered = roundCorrect + roundWrong
            val accuracy = if (totalAnswered == 0) {
                "—"
            } else {
                Math.round(roundCorrect * 100.0 / totalAnswered).toString() + "%"
            }
            return "答对 $roundCorrect · 答错 $roundWrong · 正确率 $accuracy"
        }

    val modeLabel: String
        get() = when (val value = scope) {
            is ParticleScope.Lesson -> ParticleEngine.lesson(value.lessonId)?.title ?: "专题练习"
            ParticleScope.Review -> "错题复习"
            ParticleScope.All -> "综合练习"
        }

    val lessonTitle: String
        get() = current?.question?.let { ParticleEngine.lesson(it.lessonId)?.title } ?: modeLabel

    /** 开始一轮。复习模式按加入顺序取全部错题（先错先练），练习模式交给引擎调度。 */
    fun start(scope: ParticleScope = this.scope, size: Int = ROUND_SIZE) {
        this.scope = scope
        val mistakes = app.particleMistakes
        val questions: List<ParticleQuestion> = when (scope) {
            is ParticleScope.Lesson -> ParticleEngine.planRound(
                lessonId = scope.lessonId,
                mistakeIds = mistakes,
                size = size,
                random = random,
            )
            ParticleScope.All -> ParticleEngine.planRound(
                mistakeIds = mistakes,
                size = size,
                random = random,
            )
            ParticleScope.Review -> mistakes.mapNotNull { ParticleData.QUESTION_BY_ID[it] }
        }

        round = questions.map { question ->
            val shuffled = ParticleEngine.shuffledOptions(question, random)
            ParticleRoundItem(
                question = question,
                options = shuffled.options,
                correctIndexes = shuffled.correctIndexes,
            )
        }
        index = 0
        answered = 0
        roundCorrect = 0
        roundWrong = 0
        roundComplete = round.isEmpty()
        hintUsedIds = emptySet()
        selectReset()
    }

    /** 进入模块时按需开一轮（幂等） */
    fun ensureStarted(scope: ParticleScope) {
        if (round.isEmpty() || this.scope != scope) {
            start(scope)
        }
    }

    fun tapOption(index: Int) {
        if (judged) {
            return
        }
        val now = System.currentTimeMillis()
        val isDoubleTap = index == lastTapIndex && now - lastTapTime < DOUBLE_TAP_MS
        lastTapIndex = if (isDoubleTap) -1 else index
        lastTapTime = now
        selectedIndex = index
        if (isDoubleTap) {
            commit()
        }
    }

    fun toggleHint() {
        if (judged || !hintEnabled) {
            return
        }
        if (!hintOn) {
            hintUsedThisQuestion = true
            current?.question?.id?.let { hintUsedIds = hintUsedIds + it }
        }
        hintOn = !hintOn
    }

    fun commit() {
        val item = current ?: return
        if (judged || selectedIndex !in item.options.indices) {
            return
        }
        judged = true
        lastCorrect = selectedIndex in item.correctIndexes
        answered += 1
        if (lastCorrect) {
            roundCorrect += 1
        } else {
            roundWrong += 1
        }

        app.onAnswerSubmitted()
        app.updateParticleStats(lastCorrect)
        app.updateDaily("particle")

        val question = item.question
        if (!lastCorrect) {
            app.recordParticleMistake(question.id)
        } else if (reviewing) {
            app.removeParticleMistake(question.id)
        }
        if (lastCorrect && !hintUsedThisQuestion) {
            app.markParticleMastered(question.id)
        }
        if (answered >= total) {
            roundComplete = true
            app.addCompletedTask()
        }
    }

    fun next() {
        if (!judged) {
            return
        }
        if (index + 1 >= total) {
            roundComplete = true
            return
        }
        index += 1
        selectReset()
    }

    fun restart() {
        start(scope)
    }

    /** 中途退出：丢弃本轮题面状态（错题与统计已落盘），下次进入重新开一轮 */
    fun stopRound() {
        round = emptyList()
        index = 0
        answered = 0
        roundCorrect = 0
        roundWrong = 0
        roundComplete = false
        selectReset()
    }

    fun optionState(index: Int): OptionState {
        val item = current ?: return OptionState.Default
        if (!judged) {
            return if (index == selectedIndex) OptionState.Selected else OptionState.Default
        }
        return when {
            index in item.correctIndexes ->
                if (index == selectedIndex) OptionState.Correct else OptionState.Reveal
            index == selectedIndex -> OptionState.Wrong
            else -> OptionState.Locked
        }
    }

    fun consumeToast() {
        toast = null
    }

    private fun selectReset() {
        selectedIndex = -1
        judged = false
        lastCorrect = false
        hintOn = false
        hintUsedThisQuestion = false
        lastTapIndex = -1
        lastTapTime = 0L
    }
}
