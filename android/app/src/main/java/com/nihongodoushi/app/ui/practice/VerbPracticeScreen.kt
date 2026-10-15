package com.nihongodoushi.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.ui.AppState
import kotlinx.coroutines.delay

/** 动词练习：选择题（纸卡竖排 + 底部判定抽屉）。行为依据 docs/android-answer-screen-spec.md。 */

internal data class VerbPair(val verb: VerbEntry, val voiceId: String, val inflectionId: String) {
    val key: String = AppState.verbMistakeKey(verb.id, voiceId, inflectionId)
}

class VerbPracticeState(internal val app: AppState) {

    companion object {
        /** 双击同一选项直接提交的判定窗口（= Android 默认双击超时） */
        const val DOUBLE_TAP_MS = 300L
        const val ROUND_SIZE = 5
    }

    var groupFilter by mutableStateOf("all")
        private set
    var voiceFilter by mutableStateOf("base")
        private set
    var inflectionFilter by mutableStateOf("all")
        private set
    var levelFilter by mutableStateOf("all")
        private set

    var currentQuestion by mutableStateOf<PracticeQuestion.VerbQ?>(null)
        private set
    var selectedIndex by mutableStateOf(-1)
        private set
    var judged by mutableStateOf(false)
        private set
    var lastCorrect by mutableStateOf(false)
        private set
    var showHint by mutableStateOf(false)
        private set
    var reviewingMistakes by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
        private set

    var roundAnswered by mutableStateOf(0)
        private set
    var roundCorrect by mutableStateOf(0)
        private set
    var roundWrong by mutableStateOf(0)
        private set
    var roundComplete by mutableStateOf(false)
        private set

    /** 当前题号（进入本题时冻结）：判定后不前进，下一题才推进（对齐已确认规格） */
    private var numberAtStart by mutableStateOf(1)

    private val deck = QuestionDeck<VerbPair>()
    private var lastPairKey: String? = null
    private var recentVerbIds: List<String> = emptyList()
    private val reviewedKeys = mutableSetOf<String>()
    private var lastTapIndex = -1
    private var lastTapTime = 0L

    /** 辅助信息开关（Q6）：同一按钮控制假名与规律提示 */
    val kanaVisible: Boolean get() = showHint || judged

    val progress: Float get() = numberAtStart.toFloat() / ROUND_SIZE

    val progressLabel: String get() = "$numberAtStart/$ROUND_SIZE"

    val roundLabel: String get() = "第 $numberAtStart / $ROUND_SIZE 题"

    /** 本轮累计口径：与抽屉内「本轮小结」使用同一公式，两处数字一致 */
    val statsLabel: String
        get() {
            val total = roundCorrect + roundWrong
            val accuracy = if (total == 0) "—" else Math.round(roundCorrect * 100.0 / total).toString() + "%"
            return "答对 $roundCorrect · 答错 $roundWrong · 正确率 $accuracy"
        }

    val modeLabel: String
        get() = when {
            reviewingMistakes -> "错题重练"
            currentQuestion?.isScheduledReview == true -> "间隔复习"
            else -> "随机练习"
        }

    fun applyFilters(group: String, voice: String, inflection: String, level: String) {
        groupFilter = group
        voiceFilter = voice
        inflectionFilter = inflection
        levelFilter = level
        roundAnswered = 0
        roundCorrect = 0
        roundWrong = 0
        roundComplete = false
        newRandomQuestion()
    }

    fun resetFilters() {
        applyFilters("all", "base", "all", "all")
    }

    fun filterSummary(): String {
        val group = when (groupFilter) {
            "godan" -> "五段动词"
            "ichidan" -> "一段动词"
            "irregular" -> "不规则动词"
            else -> "全部类别"
        }
        val voice = when (voiceFilter) {
            "all" -> "全部变形"
            "base" -> "基础形态"
            else -> VerbGroupLabels.voiceLabel(voiceFilter)
        }
        val inflection = when {
            inflectionFilter != "all" -> VerbGroupLabels.inflectionLabel(inflectionFilter)
            levelFilter == "all" -> "全部活用"
            levelFilter == "basic" -> "基础活用"
            else -> "进阶活用"
        }
        return "$group · $voice · $inflection"
    }

    private fun buildTargets(): List<VerbPair> {
        val voices = if (voiceFilter == "all") VerbData.VOICES else VerbData.VOICES.filter { it.id == voiceFilter }
        val inflections = when {
            inflectionFilter != "all" -> VerbData.INFLECTIONS.filter { it.id == inflectionFilter }
            levelFilter == "all" -> VerbData.INFLECTIONS
            else -> VerbData.INFLECTIONS.filter { it.level == levelFilter }
        }
        val verbs = VerbData.VERBS.filter { groupFilter == "all" || it.group == groupFilter }
        return verbs.flatMap { verb ->
            voices.flatMap { voice -> inflections.map { form -> VerbPair(verb, voice.id, form.id) } }
        }
    }

    private fun filterKey(): String =
        listOf(groupFilter, voiceFilter, inflectionFilter, levelFilter).joinToString("|")

    private fun questionFrom(mistake: MistakeRecord): PracticeQuestion.VerbQ? {
        val verb = AppState.verbById[mistake.verbId] ?: return null
        val voiceId = mistake.voiceId ?: return null
        val inflectionId = mistake.inflectionId ?: return null
        return PracticeQuestion.VerbQ(verb, voiceId, inflectionId)
    }

    private fun chooseDueReview(): PracticeQuestion.VerbQ? {
        val allowed = buildTargets().map { it.voiceId + "::" + it.inflectionId }.toSet()
        val due = app.verbMistakes().filter { mistake ->
            val verb = AppState.verbById[mistake.verbId]
            verb != null && app.isReviewDue(mistake) &&
                currentQuestion?.key != mistake.key &&
                (groupFilter == "all" || verb.group == groupFilter) &&
                (mistake.voiceId + "::" + mistake.inflectionId) in allowed
        }
        if (due.isEmpty()) return null
        return questionFrom(weightedItem(due, app::mistakeWeight))?.copy(isScheduledReview = true)
    }

    private fun chooseRandom(): PracticeQuestion.VerbQ? {
        val pair = deck.next(
            filterKey = filterKey(),
            allPairs = buildTargets(),
            keyOf = { it.key },
            entityOf = { it.verb.id },
            activeMistakeKeys = app.mistakes.map { it.key }.toSet(),
            lastKey = lastPairKey,
            recentEntities = recentVerbIds,
        ) ?: return null
        lastPairKey = pair.key
        return PracticeQuestion.VerbQ(pair.verb, pair.voiceId, pair.inflectionId)
    }

    /** 唯一的题目状态入口：进入新题统一复位（Q4），并冻结当前题号 */
    private fun startQuestion(question: PracticeQuestion.VerbQ) {
        currentQuestion = question
        recentVerbIds = (listOf(question.verb.id) + recentVerbIds.filter { it != question.verb.id }).take(3)
        numberAtStart = (roundAnswered + 1).coerceAtMost(ROUND_SIZE)
        selectedIndex = -1
        judged = false
        showHint = false
        lastTapIndex = -1
        lastTapTime = 0L
    }

    fun newRandomQuestion() {
        val wasReviewing = reviewingMistakes
        reviewingMistakes = false
        val question = chooseDueReview() ?: chooseRandom()
        if (question != null) {
            startQuestion(question)
        } else {
            currentQuestion = null
            toast = "当前筛选范围没有可练习的题目。"
        }
        if (wasReviewing) toast = "已退出错题重练，回到练习。"
    }

    fun startMistakeReview() {
        val targets = app.verbMistakes()
        if (targets.isEmpty()) {
            toast = "没有动词错题；形容词错题请在形容词练习里重练。"
            return
        }
        reviewedKeys.clear()
        reviewingMistakes = true
        roundAnswered = 0
        roundCorrect = 0
        roundWrong = 0
        roundComplete = false
        startQuestion(questionFrom(weightedItem(targets, app::mistakeWeight)) ?: return)
    }

    /** 单击选中（可改选）；同一选项 300ms 内再点一次 = 直接提交 */
    fun tapOption(index: Int) {
        if (judged) return
        val now = System.currentTimeMillis()
        val isDouble = lastTapIndex == index && (now - lastTapTime) < DOUBLE_TAP_MS
        lastTapIndex = index
        lastTapTime = now
        selectedIndex = index
        if (isDouble) {
            lastTapIndex = -1
            commit()
        }
    }

    /** 提交判定：双击与按钮共用同一条链，保证统计/错题/复习/打卡只执行一次 */
    fun commit() {
        val question = currentQuestion ?: return
        if (judged || selectedIndex !in question.options.indices) return
        val correct = judgeIndex(question, selectedIndex)
        app.onAnswerSubmitted()
        if (correct) {
            app.recordSuccessfulReview(question.key)
        } else {
            app.recordVerbMistake(
                question.verb,
                question.voiceId,
                question.inflectionId,
                question.options[selectedIndex].text,
            )
        }
        app.updateVerbStats(correct)
        app.updateDaily("verb")
        lastCorrect = correct
        judged = true
        lastTapIndex = -1

        roundAnswered += 1
        if (correct) roundCorrect += 1 else roundWrong += 1
        if (roundAnswered >= ROUND_SIZE) roundComplete = true
    }

    /** Q3：答对不自动跳转，只能由用户点「下一题」推进 */
    fun next() {
        if (roundComplete) {
            roundAnswered = 0
            roundCorrect = 0
            roundWrong = 0
            roundComplete = false
        }
        if (reviewingMistakes) {
            currentQuestion?.let { reviewedKeys.add(it.key) }
            val alternatives = app.verbMistakes().filter { it.key !in reviewedKeys }
            if (alternatives.isNotEmpty()) {
                startQuestion(questionFrom(alternatives.random()) ?: return)
                return
            }
            reviewingMistakes = false
            toast = if (app.verbMistakes().isEmpty()) "错题已全部完成间隔复习。"
            else "本轮错题已复习，剩余题目会按间隔继续复习。"
            newRandomQuestion()
            return
        }
        newRandomQuestion()
    }

    fun toggleHint() {
        if (judged) return
        showHint = !showHint
    }

    fun optionState(index: Int): OptionState {
        val question = currentQuestion ?: return OptionState.Default
        if (!judged) {
            return if (index == selectedIndex) OptionState.Selected else OptionState.Default
        }
        return when {
            index == question.correctIndex ->
                if (index == selectedIndex) OptionState.Correct else OptionState.Reveal
            index == selectedIndex -> OptionState.Wrong
            else -> OptionState.Locked
        }
    }

    fun remainingReviewCount(): Int = app.verbMistakes().count { it.key !in reviewedKeys }

    fun consumeToast() {
        toast = null
    }
}

@Composable
fun VerbPracticeScreen(state: VerbPracticeState) {
    LaunchedEffect(Unit) {
        if (state.currentQuestion == null) state.newRandomQuestion()
    }
    var sheetOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val correctRequester = remember { BringIntoViewRequester() }
    val question = state.currentQuestion

    Column(modifier = Modifier.fillMaxSize()) {
        PracticeTopArea(
            roundLabel = state.roundLabel,
            statsLabel = state.statsLabel,
            progress = state.progress,
            progressLabel = state.progressLabel,
            hintEnabled = !state.judged,
            hintOn = state.showHint,
            onFilterClick = { sheetOpen = true },
            onHintClick = { state.toggleHint() },
        )
        ToastLine(state.toast, onConsume = { state.consumeToast() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ReviewBanner(state.reviewingMistakes, state.remainingReviewCount())
            if (question == null) {
                EmptyPoolHint { state.resetFilters() }
            } else {
                PromptCard(
                    question = question,
                    modeLabel = state.modeLabel,
                    kanaVisible = state.kanaVisible,
                    compact = state.judged,
                    hintOn = state.showHint,
                    onToggleHint = { state.toggleHint() },
                )
                Spacer(modifier = Modifier.height(2.dp))
                question.options.forEachIndexed { index, option ->
                    OptionRow(
                        index = index,
                        text = option.text,
                        state = state.optionState(index),
                        modifier = if (state.judged && index == question.correctIndex) {
                            Modifier.bringIntoViewRequester(correctRequester)
                        } else {
                            Modifier
                        },
                        onClick = { state.tapOption(index) },
                    )
                }
            }
        }

        JudgementDock(
            question = question,
            pickedIndex = state.selectedIndex,
            judged = state.judged,
            correct = state.lastCorrect,
            roundComplete = state.roundComplete,
            roundCorrect = state.roundCorrect,
            roundWrong = state.roundWrong,
            roundSize = VerbPracticeState.ROUND_SIZE,
            onConfirm = { state.commit() },
            onNext = { state.next() },
        )
    }

    // 判定后抽屉展开会压缩内容区：把正确项滚进可视范围
    LaunchedEffect(state.judged) {
        if (state.judged) {
            delay(150)
            runCatching { correctRequester.bringIntoView() }
        }
    }

    if (sheetOpen) {
        FilterSheet(
            title = "练习范围",
            summary = state.filterSummary(),
            sections = verbFilterSections(
                group = state.groupFilter,
                voice = state.voiceFilter,
                inflection = state.inflectionFilter,
                level = state.levelFilter,
            ),
            onDismiss = { sheetOpen = false },
            onResetStats = { state.app.resetVerbStats() },
            onApply = { values ->
                state.applyFilters(
                    group = values["group"] ?: "all",
                    voice = values["voice"] ?: "base",
                    inflection = values["inflection"] ?: "all",
                    level = values["level"] ?: "all",
                )
                sheetOpen = false
            },
        )
    }
}

internal fun verbFilterSections(
    group: String,
    voice: String,
    inflection: String,
    level: String,
): List<FilterSection> = listOf(
    FilterSection(
        key = "group",
        label = "类别",
        selected = group,
        options = listOf(
            "all" to "全部类别",
            "godan" to "五段动词",
            "ichidan" to "一段动词",
            "irregular" to "不规则动词",
        ),
    ),
    FilterSection(
        key = "voice",
        label = "语态",
        selected = voice,
        options = buildList {
            add("all" to "全部变形")
            add("base" to "基础形态")
            VerbData.VOICES.filter { it.id != "base" }.forEach { add(it.id to it.label) }
        },
    ),
    FilterSection(
        key = "inflection",
        label = "活用形式",
        selected = inflection,
        options = buildList {
            add("all" to "全部活用")
            VerbData.INFLECTIONS.forEach { add(it.id to it.label) }
        },
    ),
    FilterSection(
        key = "level",
        label = "难度",
        selected = if (inflection == "all") level else "all",
        enabled = inflection == "all",
        options = listOf("all" to "全部难度", "basic" to "基础", "advanced" to "进阶"),
    ),
)
