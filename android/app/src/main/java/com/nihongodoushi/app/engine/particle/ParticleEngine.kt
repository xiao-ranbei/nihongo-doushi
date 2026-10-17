package com.nihongodoushi.app.engine.particle

import kotlin.random.Random

/**
 * 助词判题、选项洗牌与出题调度（纯逻辑层）。
 *
 * 对应设计：[docs/android-particle-module-spec.md] §3.4 练习范围、§3.6 错题与复习。
 *
 * 硬性口径：
 * 1. 判题按 `acceptedAnswers` 集合判定，**不是**取 `options[0]`——题库存在一题多答案的情形
 *    （`he-destination` 的 へ／に 都成立），按首项判会制造假错题。
 * 2. 选项每次洗牌；正确项位置不固定，但正确项文本集合不变。
 * 3. 一轮题量 = `min(size, 题池)`；题池不足时不重复出题（重复会把「答对」变成噪声）。
 * 4. `All`（综合）模式强制交替 fill / function，并避免相邻两题同专题（§4.3 交错练习）。
 * 5. 错题优先：题池内存在错题时，本轮首题取**最近加入**的一道（D-017 的排期补偿机制）。
 *
 * 纯逻辑：无 Android/androidx 依赖，无文件系统或时间访问；除传入的 [random] 外完全确定性。
 */
object ParticleEngine {

    /** 一轮默认题量。 */
    const val ROUND_SIZE = 5

    /** 判作用题型中，目标助词在句中的显示位置由 UI 决定，引擎只提供文本。 */

    fun lessons(): List<ParticleLesson> = ParticleData.LESSONS

    fun lesson(id: String): ParticleLesson? = ParticleData.LESSON_BY_ID[id]

    fun question(id: String): ParticleQuestion? = ParticleData.QUESTION_BY_ID[id]

    /**
     * 按专题与题型过滤题目。两个参数都可为 null（不限制）。
     * 不按 level 过滤——助词模块不做难度分层（D-018）。
     */
    fun questions(
        lessonId: String? = null,
        type: String? = null,
    ): List<ParticleQuestion> {
        if (lessonId != null && lessonId !in ParticleData.LESSON_BY_ID) {
            throw IllegalArgumentException("Unknown particle lesson: $lessonId")
        }
        if (type != null && type !in TYPE_IDS) {
            throw IllegalArgumentException("Unknown particle question type: $type")
        }
        return ParticleData.QUESTIONS.filter { question ->
            (lessonId == null || question.lessonId == lessonId) &&
                (type == null || question.type == type)
        }
    }

    /** 某个专题的全部题目（用于专题卡的「x/n 题」与掌握度分母）。 */
    fun questionsOf(lessonId: String): List<ParticleQuestion> = questions(lessonId = lessonId)

    // ---------- 判题 ----------

    /** 正确选项的下标集合；一题多答案时返回多个下标。 */
    fun correctIndexes(question: ParticleQuestion): Set<Int> {
        val accepted = question.acceptedAnswers.toSet()
        return question.options.indices.filter { question.options[it] in accepted }.toSet()
    }

    fun isCorrect(question: ParticleQuestion, pickedIndex: Int): Boolean =
        pickedIndex in correctIndexes(question)

    /**
     * 正确答案的展示文本。多答案时用「／」连接（如 `へ／に`），
     * 反馈里应配合 [otherAccepted] 说明「另一个也成立」。
     */
    fun answerLabel(question: ParticleQuestion): String =
        question.acceptedAnswers.distinct().joinToString("／")

    /**
     * 用户选中项之外、同样成立的答案（仅一题多答案时非空）。
     * 返回空表示「选错了」；返回非空表示「选对了，但还有另一种说法」。
     *
     * **必须传展示用的选项文本**：练习屏的选项是洗过牌的，`question.options` 的下标与界面对不上，
     * 按下标查会取到别的选项（真实踩过的坑）。
     */
    fun otherAccepted(question: ParticleQuestion, pickedText: String?): List<String> {
        if (pickedText.isNullOrEmpty() || pickedText !in question.acceptedAnswers) {
            return emptyList()
        }
        return question.acceptedAnswers.filter { it != pickedText }
    }

    /**
     * 解析文本。答对给当前题目的入门解析；答错追加进阶解析（先结论后加深，§3.5）。
     * 进阶段是否折叠由 UI 决定。
     */
    fun explanation(question: ParticleQuestion, correct: Boolean): String =
        if (correct) {
            question.beginnerExplanation
        } else {
            question.beginnerExplanation + "\n" + question.advancedExplanation
        }

    /** 「显示提示」面板内容：题干所属专题的入门摘要。进阶段由 UI 单独折叠展示。 */
    fun hint(question: ParticleQuestion): String =
        lesson(question.lessonId)?.beginnerSummary ?: ""

    /** 该题的干扰项说明（D-019 补充字段）；未补时返回 null，UI 用整句解析兜底。同样要传展示文本。 */
    fun optionNote(question: ParticleQuestion, pickedText: String?): String? {
        if (pickedText.isNullOrEmpty()) {
            return null
        }
        return question.optionNotes[pickedText]
    }

    // ---------- 选项洗牌 ----------

    /** 洗牌结果：展示顺序 + 对应的正确下标。 */
    data class ShuffledOptions(
        val options: List<String>,
        val correctIndexes: Set<Int>,
    )

    /**
     * 洗牌选项。正确项文本集合不变，正确下标随文本一起移动；
     * 调用方不得再洗牌，否则下标与文本会脱节。
     */
    fun shuffledOptions(
        question: ParticleQuestion,
        random: Random = Random.Default,
    ): ShuffledOptions {
        val indexed = question.options.mapIndexed { index, text -> index to text }.shuffled(random)
        val accepted = question.acceptedAnswers.toSet()
        val correct = indexed
            .mapIndexedNotNull { newIndex, pair -> if (pair.second in accepted) newIndex else null }
            .toSet()
        return ShuffledOptions(indexed.map { it.second }, correct)
    }

    // ---------- 出题调度 ----------

    /**
     * 一轮题量：`min(size, 题池)`，且至少 1。
     * 单专题池实测 4–8 题，因此一轮可能只有 4 题（界面需如实显示）。
     */
    fun roundSize(poolSize: Int, size: Int = ROUND_SIZE): Int =
        if (poolSize <= 0) 0 else minOf(size, poolSize).coerceAtLeast(1)

    /**
     * 规划一轮题目。
     *
     * - [lessonId] 与 [type] 同时为 null 视为综合练习：交替题型并跨专题打散。
     * - [mistakeIds] 为错题清单（append 顺序即加入顺序）。仅在错题属于当前题池时插为首题；
     *   专题练习不会为了「还债」跨专题出题。
     * - 一轮内不重复；题池不足时按池大小返回。
     */
    fun planRound(
        lessonId: String? = null,
        type: String? = null,
        mistakeIds: List<String> = emptyList(),
        size: Int = ROUND_SIZE,
        random: Random = Random.Default,
    ): List<ParticleQuestion> {
        val pool = questions(lessonId, type)
        val limit = roundSize(pool.size, size)
        if (limit == 0) {
            return emptyList()
        }

        val byId = pool.associateBy { it.id }
        val recentMistake = mistakeIds.asReversed().firstOrNull { byId.containsKey(it) }
        val first = recentMistake?.let { byId[it] }

        val remainingPool = if (first == null) pool else pool.filter { it.id != first.id }
        val need = limit - if (first == null) 0 else 1
        // 综合练习按题型交替抽题（题型配比先平衡），再在已选集合内优化专题顺序
        val rest = if (lessonId == null) {
            selectAlternating(remainingPool, need, random)
        } else {
            remainingPool.shuffled(random).take(need)
        }
        val raw = if (first == null) rest else listOf(first) + rest
        // 首题（错题）位置固定；其余在此之后兼顾「交替题型」与「相邻不同专题」
        val lockedPrefix = if (first == null) 0 else 1
        return arrange(raw, lockedPrefix, alternateTypes = lessonId == null)
    }

    /**
     * 从题池里交替抽取 fill / function，直到取满 [count]。
     * 题型配比先平衡，[arrange] 才有可能排出严格交替。
     */
    private fun selectAlternating(
        pool: List<ParticleQuestion>,
        count: Int,
        random: Random,
    ): List<ParticleQuestion> {
        val fill = pool.filter { it.type == ParticleQuestionTypes.FILL }.shuffled(random).toMutableList()
        val function = pool.filter { it.type == ParticleQuestionTypes.FUNCTION }.shuffled(random).toMutableList()
        val out = mutableListOf<ParticleQuestion>()
        var takeFill = random.nextBoolean()
        while (out.size < count && (fill.isNotEmpty() || function.isNotEmpty())) {
            val primary = if (takeFill) fill else function
            val fallback = if (takeFill) function else fill
            out += if (primary.isNotEmpty()) primary.removeAt(0) else fallback.removeAt(0)
            takeFill = !takeFill
        }
        return out
    }

    /**
     * 逐位挑选：优先「题型与专题都不同于上一位」；否则先保题型交替，再保专题不同；
     * 都无解时取剩余首项。题型交替是硬约束（§4.3），专题不同是软约束。
     */
    private fun arrange(
        items: List<ParticleQuestion>,
        lockedPrefix: Int,
        alternateTypes: Boolean,
    ): List<ParticleQuestion> {
        if (items.size < 2) {
            return items
        }
        val fixed = items.take(lockedPrefix.coerceIn(0, items.size))
        val remaining = items.drop(fixed.size).toMutableList()
        val result = fixed.toMutableList()
        while (remaining.isNotEmpty()) {
            val previous = result.lastOrNull()
            var chosen = -1
            if (previous != null) {
                chosen = remaining.indexOfFirst { item ->
                    (!alternateTypes || item.type != previous.type) && item.lessonId != previous.lessonId
                }
                if (chosen < 0 && alternateTypes) {
                    chosen = remaining.indexOfFirst { it.type != previous.type }
                }
                if (chosen < 0) {
                    chosen = remaining.indexOfFirst { it.lessonId != previous.lessonId }
                }
            }
            if (chosen < 0) {
                chosen = 0
            }
            result += remaining.removeAt(chosen)
        }
        return result
    }

    private val TYPE_IDS: Set<String> = setOf(
        ParticleQuestionTypes.FILL,
        ParticleQuestionTypes.FUNCTION,
    )
}
