package com.nihongodoushi.app.engine.adjective

/**
 * 形容词形式转换与规则说明。
 *
 * 忠实移植自 web/adjective-engine.js（只读参考源）：
 * - 答案归一化复用 VerbEngine.normalizeAnswer，与 JS 版复用 verb-engine 的做法一致。
 * - JS 的 RangeError/TypeError 统一映射为 IllegalArgumentException。
 * - JS conjugate 会校验 adjective.word/kana 为字符串；Kotlin 非空类型天然保证，
 *   仅保留「い形容词必须以「い」结尾」等与类型无关的防御分支。
 *
 * 纯逻辑模块：无 Android/androidx 依赖，无文件系统 / DOM / 随机数访问，全部确定性。
 */

/** 礼貌变体（对应 JS pair() 生成的 { written, kana }）。 */
data class AdjectiveAlt(
    val written: String,
    val kana: String,
)

/** conjugate 的结果（对应 JS 的 { written, kana, alts }）。 */
data class AdjectiveConjugation(
    val written: String,
    val kana: String,
    val alts: List<AdjectiveAlt>,
)

object AdjectiveEngine {

    private fun pair(written: String, kana: String, alts: List<AdjectiveAlt> = emptyList()) =
        AdjectiveConjugation(written, kana, alts)

    /** い形容词：去掉词尾「い」，再接后缀；修饰名词与现在肯定保持原形。 */
    private fun buildI(adjective: AdjectiveEntry, formId: String): AdjectiveConjugation {
        val suffix = AdjectiveData.I_SUFFIXES.getValue(formId)
        if (suffix.isEmpty()) {
            return pair(adjective.word, adjective.kana)
        }
        return pair(
            adjective.word.dropLast(1) + suffix,
            adjective.kana.dropLast(1) + suffix,
        )
    }

    /** 特例「いい」：全部换成「よく」系列。 */
    private fun buildSpecialI(adjective: AdjectiveEntry, formId: String): AdjectiveConjugation {
        val result = AdjectiveData.II_RESULTS.getValue(formId)
        return pair(result, result)
    }

    private fun buildNa(adjective: AdjectiveEntry, formId: String): AdjectiveConjugation {
        val word = adjective.word
        val kana = adjective.kana
        return when (formId) {
            "attributive" -> pair(word + "な", kana + "な")
            "present" -> pair(
                word + "だ", kana + "だ",
                listOf(AdjectiveAlt(word + "です", kana + "です")),
            )
            "presentNegative" -> pair(
                word + "じゃない", kana + "じゃない",
                listOf(
                    AdjectiveAlt(word + "ではない", kana + "ではない"),
                    AdjectiveAlt(word + "じゃありません", kana + "じゃありません"),
                    AdjectiveAlt(word + "ではありません", kana + "ではありません"),
                ),
            )
            "past" -> pair(
                word + "だった", kana + "だった",
                listOf(AdjectiveAlt(word + "でした", kana + "でした")),
            )
            "pastNegative" -> pair(
                word + "ではなかった", kana + "ではなかった",
                listOf(
                    AdjectiveAlt(word + "じゃなかった", kana + "じゃなかった"),
                    AdjectiveAlt(word + "ではありませんでした", kana + "ではありませんでした"),
                    AdjectiveAlt(word + "じゃありませんでした", kana + "じゃありませんでした"),
                ),
            )
            "te" -> pair(word + "で", kana + "で")
            "conditional" -> pair(
                word + "なら", kana + "なら",
                listOf(AdjectiveAlt(word + "ならば", kana + "ならば")),
            )
            "adverbial" -> pair(word + "に", kana + "に")
            "mizen" -> pair(word + "だろう", kana + "だろう")
            "imperativeRequest" -> pair(word + "にしてください", kana + "にしてください")
            else -> throw IllegalArgumentException("Unknown adjective form: $formId")
        }
    }

    fun conjugate(adjective: AdjectiveEntry, formId: String): AdjectiveConjugation {
        if (formId !in AdjectiveData.FORM_BY_ID) {
            throw IllegalArgumentException("Unknown adjective form: $formId")
        }
        if (adjective.kind == "i") {
            if (!adjective.word.endsWith("い") || !adjective.kana.endsWith("い")) {
                throw IllegalArgumentException("い形容词必须以「い」结尾: " + adjective.word)
            }
            return if (adjective.special) {
                buildSpecialI(adjective, formId)
            } else {
                buildI(adjective, formId)
            }
        }
        if (adjective.kind == "na") {
            return buildNa(adjective, formId)
        }
        throw IllegalArgumentException("Unsupported adjective kind: " + adjective.kind)
    }

    /**
     * 判定用答案集合：主形态（written/kana）+ 礼貌变体（alts）+ 助词音变体。
     * 「では」的 は 是助词读 wa，罗马字 dewanakatta 忠实转写为「でわ」，
     * 与书写形态「では」不一致；这里把含「では」的串补一个「でわ」变体，
     * 让罗马字输入与假名书写都能判对。仅此一处拼写变体，不做通用替换。
     */
    fun answerVariants(result: AdjectiveConjugation): List<String> {
        val variants = mutableListOf(result.written, result.kana)
        result.alts.forEach { alt ->
            variants.add(alt.written)
            variants.add(alt.kana)
        }
        val particleVariants = variants
            .filter { it.contains("では") }
            .map { it.replace("では", "でわ") }
        return variants + particleVariants
    }

    fun explain(adjective: AdjectiveEntry, formId: String): String {
        if (adjective.id !in AdjectiveData.ADJECTIVE_BY_ID || formId !in AdjectiveData.FORM_BY_ID) {
            throw IllegalArgumentException("A known adjective and form are required.")
        }
        val label = AdjectiveData.FORM_BY_ID.getValue(formId).label
        if (adjective.kind == "i" && adjective.special) {
            // 与 JS 版一致：「いい」对条件形/未然形/命令请求有专门文案，不走通用「よく」系列描述。
            if (formId == "conditional") {
                return "变「" + label + "」：「いい」的条件形是「よければ」，不能按普通规则写成「いければ」。"
            }
            if (formId == "mizen") {
                return "变「" + label + "」：「いい」可用「よかろう」表达推量；日常也常说「いいだろう」。"
            }
            if (formId == "imperativeRequest") {
                return "变「" + label + "」：「いい」本身没有命令形；礼貌请求可说「よくしてください」。"
            }
            return "变「" + label + "」：「いい」不参与变形，先换成「よく」再接词尾，得到「" +
                AdjectiveData.II_RESULTS.getValue(formId) + "」。"
        }
        val rule = if (adjective.kind == "i") {
            AdjectiveData.I_RULE_TEXT.getValue(formId)
        } else {
            AdjectiveData.NA_RULE_TEXT.getValue(formId)
        }
        return "变「" + label + "」：" + rule
    }
}
