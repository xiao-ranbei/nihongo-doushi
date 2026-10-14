package com.nihongodoushi.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors

/** 形容词练习：与动词模块同构但完全独立（独立题池、统计与错题，对齐 web/app.js 形容词模块）。 */

internal data class AdjPair(val adjective: AdjectiveEntry, val formId: String) {
    val key: String = AppState.adjMistakeKey(adjective.id, formId)
}

class AdjectivePracticeState(internal val app: AppState) {
    var groupFilter by mutableStateOf("all")
    var inflectionFilter by mutableStateOf("all")
    var levelFilter by mutableStateOf("all")

    var currentQuestion by mutableStateOf<PracticeQuestion.AdjQ?>(null)
        private set
    var reviewingMistakes by mutableStateOf(false)
        private set
    var answerText by mutableStateOf("")
    var judged by mutableStateOf(false)
        private set
    var lastCorrect by mutableStateOf(false)
        private set
    var detailsVisible by mutableStateOf(false)
    var hintVisible by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
        private set

    private val deck = QuestionDeck<AdjPair>()
    private var lastPairKey: String? = null
    private var recentWordIds: List<String> = emptyList()
    private val reviewedKeys = mutableSetOf<String>()

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

    private fun chooseDueReview(): PracticeQuestion.AdjQ? {
        val allowedFormIds = buildTargets().map { it.formId }.toSet()
        val due = app.adjectiveMistakes().filter { mistake ->
            val adjective = AppState.adjectiveById[mistake.adjectiveId]
            adjective != null && app.isReviewDue(mistake) &&
                currentQuestion?.key != mistake.key &&
                (groupFilter == "all" || adjective.kind == groupFilter) &&
                mistake.formId in allowedFormIds
        }
        if (due.isEmpty()) return null
        return questionFrom(weightedItem(due, app::mistakeWeight))
            ?.copy(isScheduledReview = true)
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

    private fun questionFrom(mistake: MistakeRecord): PracticeQuestion.AdjQ? {
        val adjective = AppState.adjectiveById[mistake.adjectiveId] ?: return null
        val formId = mistake.formId ?: return null
        return PracticeQuestion.AdjQ(adjective, formId)
    }

    fun startQuestion(question: PracticeQuestion.AdjQ) {
        currentQuestion = question
        recentWordIds = (listOf(question.adjective.id) + recentWordIds.filter { it != question.adjective.id }).take(3)
        answerText = ""
        judged = false
        detailsVisible = false
        hintVisible = false
    }

    fun newRandomQuestion() {
        val wasReviewing = reviewingMistakes
        reviewingMistakes = false
        val question = chooseDueReview() ?: chooseRandom()
        if (question != null) startQuestion(question) else currentQuestion = null
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
        startQuestion(questionFrom(weightedItem(targets, app::mistakeWeight)) ?: return)
    }

    fun submit() {
        val question = currentQuestion ?: return
        if (judged || answerText.isBlank()) return
        detailsVisible = true
        val correct = judgeAnswer(question, answerText)
        app.onAnswerSubmitted()
        if (correct) {
            app.recordSuccessfulReview(question.key)
        } else {
            app.recordAdjectiveMistake(question.adjective, question.formId, answerText.trim())
        }
        app.updateAdjectiveStats(correct)
        app.updateDaily("adjective")
        lastCorrect = correct
        judged = true
    }

    fun next() {
        if (reviewingMistakes) {
            currentQuestion?.let { reviewedKeys.add(it.key) }
            val alternatives = app.adjectiveMistakes().filter { it.key !in reviewedKeys }
            if (alternatives.isNotEmpty()) {
                startQuestion(questionFrom(alternatives.random()) ?: return)
                return
            }
            reviewingMistakes = false
            toast = if (app.adjectiveMistakes().isEmpty()) "错题已全部完成间隔复习。" else "本轮错题已复习，剩余题目会按间隔继续复习。"
            newRandomQuestion()
            return
        }
        newRandomQuestion()
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
    ToastLine(state.toast, onConsume = { state.consumeToast() })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterDropdown(
                label = "类别",
                options = listOf("all" to "全部类别", "i" to "い形容词", "na" to "な形容词"),
                selected = state.groupFilter,
                onSelect = { state.groupFilter = it; state.newRandomQuestion() },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterDropdown(
                label = "接续形式",
                options = buildList {
                    add("all" to "全部形式")
                    AdjectiveData.FORMS.forEach { add(it.id to it.label) }
                },
                selected = state.inflectionFilter,
                onSelect = { state.inflectionFilter = it; state.newRandomQuestion() },
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = "难度",
                options = listOf("all" to "全部难度", "basic" to "基础", "advanced" to "进阶"),
                selected = state.levelFilter,
                enabled = state.inflectionFilter == "all",
                onSelect = { state.levelFilter = it; state.newRandomQuestion() },
                modifier = Modifier.weight(1f),
            )
        }
        StatSummaryRow(stats = state.app.adjectiveStats, onReset = { state.app.resetAdjectiveStats() })
        ReviewBanner(state.reviewingMistakes, state.remainingReviewCount())
        val question = state.currentQuestion
        if (question == null) {
            EmptyPoolHint { state.newRandomQuestion() }
        } else {
            AdjQuestionCard(state, question)
            if (state.judged) AdjFeedbackCard(state, question)
        }
    }
}

@Composable
private fun AdjQuestionCard(state: AdjectivePracticeState, question: PracticeQuestion.AdjQ) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ModeChip(
                    when {
                        state.reviewingMistakes -> "错题重练"
                        question.isScheduledReview -> "间隔复习"
                        else -> "随机练习"
                    },
                )
                Surface(color = paperColors().bgPaleGreen, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = question.targetLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = paperColors().textGreen,
                    )
                }
            }
            QuestionTitleBlock(question, state.detailsVisible)
            Text(text = question.instruction, style = MaterialTheme.typography.bodyMedium, color = paperColors().textStrong)
            OutlinedTextField(
                value = state.answerText,
                onValueChange = { state.answerText = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.judged,
                label = { Text("输入答案（假名或汉字）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { state.submit() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.submit() }, enabled = !state.judged && state.answerText.isNotBlank()) {
                    Text("提交")
                }
                TextButton(onClick = { state.hintVisible = !state.hintVisible }) {
                    Text(if (state.hintVisible) "隐藏规律提示" else "显示规律提示")
                }
                TextButton(onClick = { state.detailsVisible = !state.detailsVisible }) {
                    Text(if (state.detailsVisible) "隐藏读音类别" else "显示读音类别")
                }
            }
            if (state.hintVisible) {
                RuleExplanationText(ruleExplanation(question.explain, allFormLabels()))
            }
        }
    }
}

@Composable
private fun AdjFeedbackCard(state: AdjectivePracticeState, question: PracticeQuestion.AdjQ) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state.lastCorrect) "答对了" else "再记一下这个变化",
                style = MaterialTheme.typography.titleLarge,
                color = if (state.lastCorrect) paperColors().statCorrectText else paperColors().textRed,
            )
            Text(text = "你的答案：" + state.answerText.trim(), style = MaterialTheme.typography.bodyMedium)
            Text(text = "正确答案：" + question.written, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (question.kana != question.written) {
                Text(text = "读音：" + question.kana, style = MaterialTheme.typography.bodyMedium, color = paperColors().reading)
            }
            Text(text = "形式大意：" + question.formMeaning, style = MaterialTheme.typography.bodySmall, color = paperColors().textStrong)
            RuleExplanationText(ruleExplanation(question.explain, allFormLabels()))
            Button(onClick = { state.next() }) {
                Text("下一题")
            }
        }
    }
}
