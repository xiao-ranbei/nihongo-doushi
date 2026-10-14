package com.nihongodoushi.app.ui.practice

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.ui.AppState
import kotlin.random.Random

/**
 * 练习题目统一模型：动词与形容词两模块共用展示与判定字段。
 * targetLabel / instruction 等文案在构造处按模块生成，保持与 web 端一致。
 */
sealed class PracticeQuestion {
    abstract val key: String
    abstract val written: String
    abstract val kana: String
    abstract val explain: String
    abstract val promptWord: String
    abstract val promptReading: String
    abstract val meaning: String
    abstract val groupLabel: String
    abstract val targetLabel: String
    abstract val instruction: String
    abstract val voiceMeaning: String?
    abstract val formMeaning: String

    data class VerbQ(
        val verb: com.nihongodoushi.app.engine.verb.VerbEntry,
        val voiceId: String,
        val inflectionId: String,
        val isScheduledReview: Boolean,
    ) : PracticeQuestion() {
        override val key: String = AppState.verbMistakeKey(verb.id, voiceId, inflectionId)
        private val result = VerbEngine.conjugate(verb, voiceId, inflectionId)
        override val written: String = result.written
        override val kana: String = result.kana
        override val explain: String = VerbEngine.explain(verb, voiceId, inflectionId)
        override val promptWord: String = verb.dictionary
        override val promptReading: String = verb.reading
        override val meaning: String = verb.meaning
        override val groupLabel: String = VerbGroupLabels.byGroup(verb.group)
        override val targetLabel: String = VerbGroupLabels.targetLabel(voiceId, inflectionId)
        override val instruction: String =
            if (voiceId == "base") {
                "请把「" + VerbGroupLabels.inflectionLabel(inflectionId) + "」写出来。"
            } else {
                "请将动词变为「" + VerbGroupLabels.voiceLabel(voiceId) + "」，再活用成「" +
                    VerbGroupLabels.inflectionLabel(inflectionId) + "」。"
            }
        override val voiceMeaning: String? =
            if (voiceId == "base") null else VerbGroupLabels.voiceMeaning(voiceId)
        override val formMeaning: String = VerbGroupLabels.inflectionMeaning(inflectionId)
    }

    data class AdjQ(
        val adjective: com.nihongodoushi.app.engine.adjective.AdjectiveEntry,
        val formId: String,
        val isScheduledReview: Boolean = false,
    ) : PracticeQuestion() {
        override val key: String = AppState.adjMistakeKey(adjective.id, formId)
        private val result = com.nihongodoushi.app.engine.adjective.AdjectiveEngine.conjugate(adjective, formId)
        override val written: String = result.written
        override val kana: String = result.kana
        override val explain: String =
            com.nihongodoushi.app.engine.adjective.AdjectiveEngine.explain(adjective, formId)
        override val promptWord: String = adjective.word
        override val promptReading: String = adjective.kana
        override val meaning: String = adjective.meaning
        override val groupLabel: String = if (adjective.kind == "i") "い形容词" else "な形容词"
        override val targetLabel: String = VerbGroupLabels.adjFormLabel(formId)
        override val instruction: String = "请写出形容词的「" + VerbGroupLabels.adjFormLabel(formId) + "」。"
        override val voiceMeaning: String? = null
        override val formMeaning: String = VerbGroupLabels.adjFormMeaning(formId)
    }
}

/** 判定用期望集合：主形态 + 假名 + 引擎变体，全部经归一化比较。 */
fun expectedAnswers(question: PracticeQuestion): Set<String> = buildSet {
    add(VerbEngine.normalizeAnswer(question.written))
    add(VerbEngine.normalizeAnswer(question.kana))
    if (question is PracticeQuestion.AdjQ) {
        val result = com.nihongodoushi.app.engine.adjective.AdjectiveEngine.conjugate(
            question.adjective, question.formId,
        )
        com.nihongodoushi.app.engine.adjective.AdjectiveEngine.answerVariants(result).forEach {
            add(VerbEngine.normalizeAnswer(it))
        }
    }
}

fun judgeAnswer(question: PracticeQuestion, rawInput: String): Boolean =
    expectedAnswers(question).contains(VerbEngine.normalizeAnswer(rawInput.trim()))

/** 权重随机抽取：权重为正整数（对齐 web weightedItem）。 */
fun <T> weightedItem(items: List<T>, weightOf: (T) -> Int, random: Random = Random): T {
    val total = items.sumOf(weightOf)
    var roll = random.nextDouble() * total
    items.forEach { item ->
        roll -= weightOf(item)
        if (roll < 0) return item
    }
    return items.last()
}

fun <T> shuffleItems(items: List<T>, random: Random = Random): List<T> = items.shuffled(random)

/**
 * 题库调度：筛选键变更即重建；抽空时重洗（优先剔除当前错题 key）；
 * 末位抽取并对「与上一题重复 / 近期词条重复」做换位规避（对齐 web chooseRandomQuestion）。
 */
internal class QuestionDeck<T> {
    private var deck: List<T> = emptyList()
    private var filterKey: String = ""

    fun next(
        filterKey: String,
        allPairs: List<T>,
        keyOf: (T) -> String,
        entityOf: (T) -> String,
        activeMistakeKeys: Set<String>,
        lastKey: String?,
        recentEntities: List<String>,
        random: Random = Random,
    ): T? {
        if (filterKey != this.filterKey) {
            deck = emptyList()
            this.filterKey = filterKey
        }
        deck = deck.filter { keyOf(it) !in activeMistakeKeys }
        if (deck.isEmpty()) {
            val fresh = allPairs.filter { keyOf(it) !in activeMistakeKeys }
            deck = shuffleItems(fresh.ifEmpty { allPairs }, random)
            if (deck.isEmpty()) return null
        }

        val nextIndex = deck.size - 1
        if (nextIndex > 0) {
            val candidate = deck[nextIndex]
            val repeatsQuestion = keyOf(candidate) == lastKey
            val repeatsRecentEntity = candidate.let(entityOf) in recentEntities
            if (repeatsQuestion || repeatsRecentEntity) {
                val alternatives = (0 until nextIndex).filter {
                    keyOf(deck[it]) != lastKey && entityOf(deck[it]) !in recentEntities
                }.ifEmpty {
                    if (repeatsQuestion) (0 until nextIndex).filter { keyOf(deck[it]) != lastKey }
                    else emptyList()
                }
                if (alternatives.isNotEmpty()) {
                    val swapIndex = alternatives[random.nextInt(alternatives.size)]
                    deck = deck.toMutableList().apply {
                        this[nextIndex] = deck[swapIndex].also { this[swapIndex] = this[nextIndex] }
                    }
                }
            }
        }
        val pair = deck.last()
        deck = deck.dropLast(1)
        return pair
    }
}

/** 规则说明文本渲染：「术语」与「あ段」等关键片段加粗强调（对齐 web renderRuleExplanation）。 */
fun ruleExplanation(text: String, formLabels: Set<String>): AnnotatedString {
    val pattern = "(「[^」]+」|[あいうえお]段|\n)".toRegex()
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val part = match.value
            when {
                part == "\n" -> append('\n')
                part.matches("[あいうえお]段".toRegex()) -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
                part.startsWith("「") && part.drop(1).dropLast(1) !in formLabels ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
                else -> append(part)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** 全项目形式标签集合：规则说明里这些词不加粗（对齐 web RULE_FORM_LABELS）。 */
fun allFormLabels(): Set<String> = buildSet {
    com.nihongodoushi.app.engine.verb.VerbData.VOICES.forEach { add(it.label) }
    com.nihongodoushi.app.engine.verb.VerbData.INFLECTIONS.forEach { add(it.label) }
    com.nihongodoushi.app.engine.adjective.AdjectiveData.FORMS.forEach { add(it.label) }
}

/** 展示层标签集中管理（对齐 web 端 GROUP_LABELS / getTargetLabel 等）。 */
object VerbGroupLabels {
    private val voiceById = com.nihongodoushi.app.engine.verb.VerbData.VOICES.associateBy { it.id }
    private val inflectionById = com.nihongodoushi.app.engine.verb.VerbData.INFLECTIONS.associateBy { it.id }
    private val adjFormById = com.nihongodoushi.app.engine.adjective.AdjectiveData.FORMS.associateBy { it.id }

    fun byGroup(group: String): String = when (group) {
        "godan" -> "五段动词"
        "ichidan" -> "一段动词"
        "irregular" -> "不规则动词"
        else -> group
    }

    fun voiceLabel(voiceId: String): String = voiceById.getValue(voiceId).label

    fun voiceMeaning(voiceId: String): String = voiceById.getValue(voiceId).meaning

    fun inflectionLabel(inflectionId: String): String = inflectionById.getValue(inflectionId).label

    fun inflectionMeaning(inflectionId: String): String = inflectionById.getValue(inflectionId).meaning

    fun targetLabel(voiceId: String, inflectionId: String): String =
        if (voiceId == "base") inflectionLabel(inflectionId)
        else voiceLabel(voiceId) + " · " + inflectionLabel(inflectionId)

    fun adjFormLabel(formId: String): String = adjFormById.getValue(formId).label

    fun adjFormMeaning(formId: String): String = adjFormById.getValue(formId).meaning
}
