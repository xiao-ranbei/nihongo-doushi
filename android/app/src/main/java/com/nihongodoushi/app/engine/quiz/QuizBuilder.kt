package com.nihongodoushi.app.engine.quiz

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.engine.verb.VerbEntry
import kotlin.random.Random

/** 一个选项：展示文本 + 是否为正确项。 */
data class QuizOption(val text: String, val isCorrect: Boolean)

/**
 * 选择题选项生成（纯逻辑层，见 docs/android-redesign-proposal.md §1.2）。
 *
 * 硬约束（由 QuizBuilderTest 全词库穷举守护）：
 * 1. 正确项恰出现一次，位置随机；
 * 2. 干扰项优先级 D3 误形表 → D2 同动词其他语态 → D1 同动词其他活用形式 → D4 同组其他动词；
 *    （形容词无语态维，D2 跳过，D4 取同 kind 其他形容词）
 * 3. 全部选项经 [VerbEngine.normalizeAnswer] 归一化后互不相等，且均 ≠ 归一化后的正确项；
 * 4. 取满 [OPTION_COUNT] 项；确无足够唯一选项时允许更少（≥2）；
 * 5. 返回顺序即展示顺序（已随机打乱），调用方不再洗牌。
 *
 * 纯逻辑：无 Android/androidx 依赖，无文件 / DOM 访问；除传入的 [random] 外完全确定性。
 */
object QuizBuilder {

    const val OPTION_COUNT = 4

    /** 生成某动词在指定语态 + 活用形式下的选项组。 */
    fun buildVerbOptions(
        verb: VerbEntry,
        voiceId: String,
        inflectionId: String,
        random: Random = Random.Default,
    ): List<QuizOption> {
        val correct = VerbEngine.conjugate(verb, voiceId, inflectionId).written

        // D3：经典误形表（最高优先级）
        val d3 = WrongForms.verbWrongForms(verb, voiceId, inflectionId).shuffled(random)
        // D2：同动词、同活用形式、其他语态
        val d2 = VerbData.VOICES
            .filter { it.id != voiceId }
            .map { VerbEngine.conjugate(verb, it.id, inflectionId).written }
            .shuffled(random)
        // D1：同动词、同语态、其他活用形式
        val d1 = VerbData.INFLECTIONS
            .filter { it.id != inflectionId }
            .map { VerbEngine.conjugate(verb, voiceId, it.id).written }
            .shuffled(random)
        // D4：同组其他动词、同语态、同活用形式
        val d4 = VerbData.VERBS
            .filter { it.id != verb.id && it.group == verb.group }
            .map { VerbEngine.conjugate(it, voiceId, inflectionId).written }
            .shuffled(random)

        return assemble(correct, listOf(d3, d2, d1, d4), random)
    }

    /** 生成某形容词在指定形式下的选项组。 */
    fun buildAdjectiveOptions(
        adjective: AdjectiveEntry,
        formId: String,
        random: Random = Random.Default,
    ): List<QuizOption> {
        val correct = AdjectiveEngine.conjugate(adjective, formId).written

        // D3：经典误形表
        val d3 = WrongForms.adjectiveWrongForms(adjective, formId).shuffled(random)
        // D1：同形容词、其他形式（形容词无语态，D2 跳过）
        val d1 = AdjectiveData.FORMS
            .filter { it.id != formId }
            .map { AdjectiveEngine.conjugate(adjective, it.id).written }
            .shuffled(random)
        // D4：同 kind 其他形容词、同形式
        val d4 = AdjectiveData.ADJECTIVES
            .filter { it.id != adjective.id && it.kind == adjective.kind }
            .map { AdjectiveEngine.conjugate(it, formId).written }
            .shuffled(random)

        return assemble(correct, listOf(d3, d1, d4), random)
    }

    /**
     * 按优先级各层轮流取一个干扰项（尽量让干扰项来自不同层），
     * 归一化去重、剔除等于正确项者，取满 OPTION_COUNT - 1 个后与正确项合并并打乱。
     */
    private fun assemble(correct: String, tiers: List<List<String>>, random: Random): List<QuizOption> {
        val target = OPTION_COUNT - 1
        val correctNorm = VerbEngine.normalizeAnswer(correct)
        val seen = mutableSetOf(correctNorm)
        val picked = mutableListOf<String>()
        val cursors = IntArray(tiers.size)

        var progressed = true
        while (picked.size < target && progressed) {
            progressed = false
            for (tierIndex in tiers.indices) {
                if (picked.size >= target) break
                val tier = tiers[tierIndex]
                while (cursors[tierIndex] < tier.size) {
                    val candidate = tier[cursors[tierIndex]]
                    cursors[tierIndex] += 1
                    val norm = VerbEngine.normalizeAnswer(candidate)
                    if (norm.isEmpty() || norm in seen) continue
                    seen.add(norm)
                    picked.add(candidate)
                    progressed = true
                    break
                }
            }
        }

        val options = mutableListOf(QuizOption(correct, true))
        picked.forEach { options.add(QuizOption(it, false)) }
        return options.shuffled(random)
    }
}
