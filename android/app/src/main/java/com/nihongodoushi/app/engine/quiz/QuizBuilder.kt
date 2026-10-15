package com.nihongodoushi.app.engine.quiz

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.adjective.FormInfo
import com.nihongodoushi.app.engine.verb.InflectionInfo
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.engine.verb.VerbEntry
import kotlin.random.Random

/** 一个选项：展示文本 + 是否为正确项。 */
data class QuizOption(val text: String, val isCorrect: Boolean)

/**
 * 选择题选项生成（纯逻辑层，见 docs/android-answer-screen-spec.md §1.2）。
 *
 * **硬性口径（2026-10-15 关子确定）**：四个选项必须**全部是当前目标词的变形形式**
 * （不同语态／时态／活用形式，含该词的经典误形），**不得混入任何其他词汇**。
 * 因此本生成器**不使用**「同组其他词的同形」作为干扰项来源。
 *
 * 硬约束（由 QuizBuilderTest 全词库穷举守护）：
 * 1. 正确项恰出现一次，位置随机；
 * 2. 干扰项优先级：D3 该词误形表 → D2 该词同形式其他语态 → D1 该词同语态其他活用形式；
 * 3. 全部选项经 [VerbEngine.normalizeAnswer] 归一化后互不相等，且均 ≠ 归一化后的正确项；
 * 4. 取满 [OPTION_COUNT] 项；确无足够唯一选项时允许更少（≥2）；
 * 5. 返回顺序即展示顺序（已随机打乱），调用方不再洗牌。
 *
 * 纯逻辑：无 Android/androidx 依赖，无文件 / DOM 访问；除传入的 [random] 外完全确定性。
 */
object QuizBuilder {

    const val OPTION_COUNT = 4

    /** 生成某动词在指定语态 + 活用形式下的选项组（选项全部来自该动词）。 */
    fun buildVerbOptions(
        verb: VerbEntry,
        voiceId: String,
        inflectionId: String,
        random: Random = Random.Default,
    ): List<QuizOption> {
        val correct = VerbEngine.conjugate(verb, voiceId, inflectionId).written
        val target = VerbData.INFLECTIONS.firstOrNull { it.id == inflectionId }

        // D3：该词的经典误形（最高优先级）
        val d3 = WrongForms.verbWrongForms(verb, voiceId, inflectionId).shuffled(random)
        // D2：该词、同活用形式、其他语态
        val d2 = VerbData.VOICES
            .filter { it.id != voiceId }
            .map { VerbEngine.conjugate(verb, it.id, inflectionId).written }
            .shuffled(random)
        // D1：该词、同语态、其他活用形式——按「与目标形式的相近度」排序，让干扰项更像
        val d1 = VerbData.INFLECTIONS
            .filter { it.id != inflectionId }
            .sortedWith(compareBy({ inflectionDistance(it, target) }, { it.id }))
            .map { VerbEngine.conjugate(verb, voiceId, it.id).written }

        return assemble(correct, listOf(d3, d2, d1), random)
    }

    /** 生成某形容词在指定形式下的选项组（选项全部来自该形容词）。 */
    fun buildAdjectiveOptions(
        adjective: AdjectiveEntry,
        formId: String,
        random: Random = Random.Default,
    ): List<QuizOption> {
        val correct = AdjectiveEngine.conjugate(adjective, formId).written
        val target = AdjectiveData.FORMS.firstOrNull { it.id == formId }

        // D3：该词的经典误形
        val d3 = WrongForms.adjectiveWrongForms(adjective, formId).shuffled(random)
        // D1：该词、其他形式（形容词无语态维），按相近度排序
        val d1 = AdjectiveData.FORMS
            .filter { it.id != formId }
            .sortedWith(compareBy({ adjectiveFormDistance(it, target) }, { it.id }))
            .map { AdjectiveEngine.conjugate(adjective, it.id).written }

        return assemble(correct, listOf(d3, d1), random)
    }

    /** 活用形式分组：同组内形式更容易互相混淆，优先作为干扰项。 */
    private fun inflectionGroup(id: String): String = when {
        id.startsWith("masu") -> "masu"
        id.startsWith("negative") -> "negative"
        id.startsWith("conditional") -> "conditional"
        id == "dictionary" || id == "masuStem" -> "stem"
        id == "past" || id == "te" -> "ta-te"
        else -> id
    }

    /** 距离越小越相近：同组 -3，同难度 -2。 */
    private fun inflectionDistance(candidate: InflectionInfo, target: InflectionInfo?): Int {
        var score = 0
        if (target != null && candidate.level == target.level) score -= 2
        if (target != null && inflectionGroup(candidate.id) == inflectionGroup(target.id)) score -= 3
        return score
    }

    private fun adjectiveFormDistance(candidate: FormInfo, target: FormInfo?): Int {
        var score = 0
        if (target != null && candidate.level == target.level) score -= 2
        val group = { id: String -> if (id.startsWith("present")) "present" else if (id.startsWith("past")) "past" else id }
        if (target != null && group(candidate.id) == group(target.id)) score -= 3
        return score
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
