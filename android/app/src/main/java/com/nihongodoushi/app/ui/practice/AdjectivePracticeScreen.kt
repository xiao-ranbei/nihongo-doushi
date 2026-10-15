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
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.ui.AppState
import kotlinx.coroutines.delay

/** 形容词练习：与动词同构、独立题池与统计。行为依据 docs/android-answer-screen-spec.md。 */

internal data class AdjPair(val adjective: AdjectiveEntry, val formId: String) {
    val key: String = AppState.adjMistakeKey(adjective.id, formId)
}

class AdjectivePracticeState(internal val app: AppState) {

    companion object {
        const val DOUBLE_TAP_MS = 300L
        const val ROUND_SIZE = 5
    }

    var groupFilter by mutableStateOf("all")
        private set
    var inflectionFilter by mutableStateOf("all")
        private set
    var levelFilter by mutableStateOf("all")
        private set

    var currentQuestion by mutableStateOf<PracticeQuestion.AdjQ?>(null)
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

    private val deck = QuestionDeck<AdjPair>()
    private var lastPairKey: String? = null
    private var recentWordIds: List<String> = emptyList()
    private val reviewedKeys = mutableSetOf<String>()
    private var lastTapIndex = -1
    private var lastTapTime = 0L

    val kanaVisible: Boolean get() = showHint || judged

    val progress: Float get() = roundAnswered.toFloat() / ROUND_SIZE

    val progressLabel: String
        get() = "${(roundAnswered + 1).coerceAtMost(ROUND_SIZE)}/$ROUND_SIZE"

    val roundLabel: String
        get() = "第 ${(roundAnswered + 1).coerceAtMost(ROUND_SIZE)} / $ROUND_SIZE 题"

    val statsLabel: String
        get() {
            val stats = app.adjectiveStats
            val total = stats.correct + stats.incorrect
            val accuracy = if (total == 0) "—" else Math.round(stats.correct * 100.0 / total).toString() + "%"
            return "答对 ${stats.correct} · 答错 ${stats.incorrect} · 正确率 $accuracy"
        }

    val modeLabel: String
        get() = when {
            reviewingMistakes -> "错题重练"
            currentQuestion?.isScheduledReview == true -> "间隔复习"
            else -> "随机练习"
        }

    fun applyFilters(group: String, inflection: String, level: String) {
        groupFilter = group
        inflectionFilter = inflection
        levelFilter = level
        roundAnswered = 0
        roundCorrect = 0
        roundWrong = 0
        roundComplete = false
        newRandomQuestion()
    }

    fun resetFilters() {
        applyFilters("all", "all", "all")
    }

    fun filterSummary(): String {
        val group = when (groupFilter) {
            "i" -> "い形容词"
            "na" -> "な形容词"
            else -> "全部类别"
        }
        val form = when {
            inflectionFilter != "all" -> VerbGroupLabels.adjFormLabel(inflectionFilter)
            levelFilter == "all" -> "全部形式"
            levelFilter == "basic" -> "基础形式"
            else -> "进阶形式"
        }
        return "$group · $form"
    }

    private fun buildTargets(): List<AdjPair> {
        val adjectives = AdjectiveData.ADJECTIVES.filter { groupFilter == "all" || it.kind == groupFilter }
        val forms = when {
            inflectionFilter != "all" -> AdjectiveData.FORMS.filter { it.id == inflectionFilter }
            levelFilter == "all" -> AdjectiveData.FORMS
            else -> AdjectiveData.FORMS.filter { it.level == levelFilter }
        }
        return adjectives.flatMap { adjective -> forms.map { form -> AdjPair(adjective, form.id) } }
    }

    private fun filterKey(): String = listOf(groupFilter, inflectionFilter, levelFilter).joinToString("|")

    private fun questionFrom(mistake: MistakeRecord): PracticeQuestion.AdjQ? {
        val adjective = AppState.adjectiveById[mistake.adjectiveId] ?: return null
        val formId = mistake.formId ?: return null
        return PracticeQuestion.AdjQ(adjective, formId)
    }

    private fun chooseDueReview(): PracticeQuestion.AdjQ? {
        val allowedForms = buildTargets().map { it.formId }.toSet()
        val due = app.adjectiveMistakes().filter { mistake ->
            val adjective = AppState.adjectiveById[mistake.adjectiveId]
            adjective != null && app.isReviewDue(mistake) &&
                currentQuestion?.key != mistake.key &&
                (groupFilter == "all" || adjective.kind == groupFilter) &&
                mistake.formId in allowedForms
        }
        if (due.isEmpty()) return null
        return questionFrom(weightedItem(due, app::mistakeWeight))?.copy(isScheduledReview = true)
    }

    private fun chooseRandom(): PracticeQuestion.AdjQ? {
        val pair = deck.next(
            filterKey = filterKey(),
            allPairs = buildTargets(),
            keyOf = { it.key },
            entityOf = { it.adjective.id },
            activeMistakeKeys = app.adjectiveMistakes().map { it.key }.toSet(),
            lastKey = lastPairKey,
            recentEntities = recentWordIds,
        ) ?: return null
        lastPairKey = pair.key
        return PracticeQuestion.AdjQ(pair.adjective, pair.formId)
    }

    /** 唯一的题目状态入口：进入新题统一复位（Q4） */
    private fun startQuestion(question: PracticeQuestion.AdjQ) {
        currentQuestion = question
        recentWordIds = (listOf(question.adjective.id) +
            recentWordIds.filter { it != question.adjective.id }).take(3)
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
        val targets = app.adjectiveMistakes()
        if (targets.isEmpty()) {
            toast = "目前没有形容词错题。"
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

    fun commit() {
        val question = currentQuestion ?: return
        if (judged || selectedIndex !in question.options.indices) return
        val correct = judgeIndex(question, selectedIndex)
        app.onAnswerSubmitted()
        if (correct) {
            app.recordSuccessfulReview(question.key)
        } else {
            app.recordAdjectiveMistake(
                question.adjective,
                question.formId,
                question.options[selectedIndex].text,
            )
        }
        app.updateAdjectiveStats(correct)
        app.updateDaily("adjective")
        lastCorrect = correct
        judged = true
        lastTapIndex = -1

        roundAnswered += 1
        if (correct) roundCorrect += 1 else roundWrong += 1
        if (roundAnswered >= ROUND_SIZE) roundComplete = true
    }

    /** Q3：答对不自动跳转 */
    fun next() {
        if (roundComplete) {
            roundAnswered = 0
            roundCorrect = 0
            roundWrong = 0
            roundComplete = false
        }
        if (reviewingMistakes) {
            currentQuestion?.let { reviewedKeys.add(it.key) }
            val alternatives = app.adjectiveMistakes().filter { it.key !in reviewedKeys }
            if (alternatives.isNotEmpty()) {
                startQuestion(questionFrom(alternatives.random()) ?: return)
                return
            }
            reviewingMistakes = false
            toast = if (app.adjectiveMistakes().isEmpty()) "错题已全部完成间隔复习。"
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

    fun remainingReviewCount(): Int = app.adjectiveMistakes().count { it.key !in reviewedKeys }

    fun consumeToast() {
        toast = null
    }
}

@Composable
fun AdjectivePracticeScreen(state: AdjectivePracticeState) {
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
            roundSize = AdjectivePracticeState.ROUND_SIZE,
            onConfirm = { state.commit() },
            onNext = { state.next() },
        )
    }

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
            sections = adjectiveFilterSections(
                group = state.groupFilter,
                inflection = state.inflectionFilter,
                level = state.levelFilter,
            ),
            onDismiss = { sheetOpen = false },
            onResetStats = { state.app.resetAdjectiveStats() },
            onApply = { values ->
                state.applyFilters(
                    group = values["group"] ?: "all",
                    inflection = values["inflection"] ?: "all",
                    level = values["level"] ?: "all",
                )
                sheetOpen = false
            },
        )
    }
}

internal fun adjectiveFilterSections(
    group: String,
    inflection: String,
    level: String,
): List<FilterSection> = listOf(
    FilterSection(
        key = "group",
        label = "类别",
        selected = group,
        options = listOf("all" to "全部类别", "i" to "い形容词", "na" to "な形容词"),
    ),
    FilterSection(
        key = "inflection",
        label = "接续形式",
        selected = inflection,
        options = buildList {
            add("all" to "全部形式")
            AdjectiveData.FORMS.forEach { add(it.id to it.label) }
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
