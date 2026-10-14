package com.nihongodoushi.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors

/** 动词练习：筛选、出题调度、判题与复习推进（行为对齐 web/app.js 动词模块）。 */

internal data class VerbPair(val verb: VerbEntry, val voiceId: String, val inflectionId: String) {
    val key: String = AppState.verbMistakeKey(verb.id, voiceId, inflectionId)
}

class VerbPracticeState(internal val app: AppState) {
    var groupFilter by mutableStateOf("all")
    var voiceFilter by mutableStateOf("base")
    var inflectionFilter by mutableStateOf("all")
    var levelFilter by mutableStateOf("all")

    var currentQuestion by mutableStateOf<PracticeQuestion.VerbQ?>(null)
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

    private val deck = QuestionDeck<VerbPair>()
    private var lastPairKey: String? = null
    private var recentVerbIds: List<String> = emptyList()
    private val reviewedKeys = mutableSetOf<String>()

    private fun buildTargets(): List<VerbPair> {
        val voices = if (voiceFilter == "all") VerbData.VOICES else VerbData.VOICES.filter { it.id == voiceFilter }
        val inflections = when {
            inflectionFilter != "all" -> VerbData.INFLECTIONS.filter { it.id == inflectionFilter }
            levelFilter == "all" -> VerbData.INFLECTIONS
            else -> VerbData.INFLECTIONS.filter { it.level == levelFilter }
        }
        val verbs = VerbData.VERBS.filter { groupFilter == "all" || it.group == groupFilter }
        return verbs.flatMap { verb -> voices.flatMap { voice -> inflections.map { form -> VerbPair(verb, voice.id, form.id) } } }
    }

    private fun filterKey(): String = listOf(groupFilter, voiceFilter, inflectionFilter, levelFilter).joinToString("|")

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
        val question = questionFrom(weightedItem(due, app::mistakeWeight))
        return question?.copy(isScheduledReview = true)
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
        return PracticeQuestion.VerbQ(pair.verb, pair.voiceId, pair.inflectionId, isScheduledReview = false)
    }

    private fun questionFrom(mistake: com.nihongodoushi.app.storage.MistakeRecord): PracticeQuestion.VerbQ? {
        val verb = AppState.verbById[mistake.verbId] ?: return null
        val voiceId = mistake.voiceId ?: return null
        val inflectionId = mistake.inflectionId ?: return null
        return PracticeQuestion.VerbQ(verb, voiceId, inflectionId, isScheduledReview = false)
    }

    fun startQuestion(question: PracticeQuestion.VerbQ) {
        currentQuestion = question
        recentVerbIds = (listOf(question.verb.id) + recentVerbIds.filter { it != question.verb.id }).take(3)
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
        val targets = app.verbMistakes()
        if (targets.isEmpty()) {
            toast = "没有动词错题；形容词错题请在形容词练习里重练。"
            return
        }
        reviewedKeys.clear()
        reviewingMistakes = true
        startQuestion(questionFrom(weightedItem(targets, app::mistakeWeight)) ?: return)
    }

    /** 判题：锁定输入并落统计/错题/打卡（对齐 web submitAnswer 后半段）。 */
    fun submit() {
        val question = currentQuestion ?: return
        if (judged || answerText.isBlank()) return
        detailsVisible = true
        val correct = judgeAnswer(question, answerText)
        app.onAnswerSubmitted()
        if (correct) {
            app.recordSuccessfulReview(question.key)
        } else {
            app.recordVerbMistake(question.verb, question.voiceId, question.inflectionId, answerText.trim())
        }
        app.updateVerbStats(correct)
        app.updateDaily("verb")
        lastCorrect = correct
        judged = true
    }

    /** 下一题：重练模式走未复习集合，清空则退出并提示（对齐 web continueAfterAnswer）。 */
    fun next() {
        if (reviewingMistakes) {
            currentQuestion?.let { reviewedKeys.add(it.key) }
            val alternatives = app.verbMistakes().filter { it.key !in reviewedKeys }
            if (alternatives.isNotEmpty()) {
                startQuestion(questionFrom(alternatives.random()) ?: return)
                return
            }
            reviewingMistakes = false
            toast = if (app.verbMistakes().isEmpty()) "错题已全部完成间隔复习。" else "本轮错题已复习，剩余题目会按间隔继续复习。"
            newRandomQuestion()
            return
        }
        newRandomQuestion()
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
    ToastLine(state.toast, onConsume = { state.consumeToast() })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterRow(state)
        StatSummaryRow(stats = state.app.verbStats, onReset = { state.app.resetVerbStats() })
        ReviewBanner(state.reviewingMistakes, state.remainingReviewCount())
        val question = state.currentQuestion
        if (question == null) {
            EmptyPoolHint { state.newRandomQuestion() }
        } else {
            QuestionCard(state, question)
            if (state.judged) FeedbackCard(state, question)
        }
    }
}

@Composable
private fun FilterRow(state: VerbPracticeState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterDropdown(
            label = "类别",
            options = listOf(
                "all" to "全部类别", "godan" to "五段动词", "ichidan" to "一段动词", "irregular" to "不规则动词",
            ),
            selected = state.groupFilter,
            onSelect = { state.groupFilter = it; state.newRandomQuestion() },
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = "语态",
            options = buildList {
                add("all" to "全部变形"); add("base" to "基础形态")
                VerbData.VOICES.filter { it.id != "base" }.forEach { add(it.id to it.label) }
            },
            selected = state.voiceFilter,
            onSelect = { state.voiceFilter = it; state.newRandomQuestion() },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterDropdown(
            label = "活用形式",
            options = buildList {
                add("all" to "全部活用")
                VerbData.INFLECTIONS.forEach { add(it.id to it.label) }
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
}

@Composable
internal fun FilterDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (enabled) "$label：$selectedLabel" else "$label：已按形式锁定", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, fontWeight = if (value == selected) FontWeight.Bold else null) },
                    onClick = { expanded = false; onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(state: VerbPracticeState, question: PracticeQuestion.VerbQ) {
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
private fun FeedbackCard(state: VerbPracticeState, question: PracticeQuestion.VerbQ) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state.lastCorrect) "答对了" else "再记一下这个变化",
                style = MaterialTheme.typography.titleLarge,
                color = if (state.lastCorrect) paperColors().statCorrectText else paperColors().textRed,
            )
            val raw = state.answerText.trim()
            val romaji = Regex("^[a-z']+$", RegexOption.IGNORE_CASE).matches(raw)
            Text(
                text = "你的答案：" + raw +
                    (if (state.lastCorrect && romaji) "（" + VerbEngine.normalizeAnswer(raw) + "）" else ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = "正确答案：" + question.written, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (question.kana != question.written) {
                Text(text = "读音：" + question.kana, style = MaterialTheme.typography.bodyMedium, color = paperColors().reading)
            }
            question.voiceMeaning?.let {
                Text(text = "语态：" + it, style = MaterialTheme.typography.bodySmall, color = paperColors().textStrong)
            }
            Text(
                text = (if (question.voiceMeaning != null) "活用：" else "") + question.formMeaning,
                style = MaterialTheme.typography.bodySmall,
                color = paperColors().textStrong,
            )
            RuleExplanationText(ruleExplanation(question.explain, allFormLabels()))
            Button(onClick = { state.next() }) {
                Text("下一题")
            }
        }
    }
}

@Composable
internal fun RuleExplanationText(text: androidx.compose.ui.text.AnnotatedString) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = paperColors().textStrong)
}

@Composable
internal fun EmptyPoolHint(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("当前筛选范围没有可练习的题目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
internal fun ToastLine(toast: String?, onConsume: () -> Unit) {
    if (toast != null) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(2500)
            onConsume()
        }
        Surface(
            color = paperColors().greenPale,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = toast,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
