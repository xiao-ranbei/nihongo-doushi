package com.nihongodoushi.app.engine.quiz

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEntry

/**
 * 经典误形表（D3 干扰项来源，教学价值最高）。
 *
 * 纯数据 + 查询函数：无 Android/androidx 依赖，无文件系统 / 随机数访问，全部确定性。
 * 表内每一条误形都必须**确实不等于**同一语境下的正确形态——
 * 该不变式由 QuizBuilderTest 的数据驱动断言逐条守护，勿随手改动形态。
 *
 * 语境由三元组（verbId, voiceId, inflectionId）唯一确定，
 * 因此同一条误形可安全地只挂在它真正错误的那一个组合上（如「行いて」只挂在 iku/base/te）。
 */

/** 一条动词误形及其适用语境（verbId × voiceId × inflectionId）。 */
data class VerbWrongForm(
    val verbId: String,
    val voiceId: String,
    val inflectionId: String,
    val wrong: String,
)

/** 一条形容词误形及其适用语境（adjectiveId × formId）。 */
data class AdjectiveWrongForm(
    val adjectiveId: String,
    val formId: String,
    val wrong: String,
)

object WrongForms {

    /** 全部动词误形条目（含按规则批量生成的部分，与手工条目同质）。 */
    val verbTable: List<VerbWrongForm> = buildVerbTable()

    /** 全部形容词误形条目。 */
    val adjectiveTable: List<AdjectiveWrongForm> = buildAdjectiveTable()

    /** 取某动词在指定语态 + 活用形式下的全部经典误形；无对应条目时返回空表。 */
    fun verbWrongForms(verb: VerbEntry, voiceId: String, inflectionId: String): List<String> =
        verbTable
            .filter { it.verbId == verb.id && it.voiceId == voiceId && it.inflectionId == inflectionId }
            .map { it.wrong }

    /** 取某形容词在指定形式下的全部经典误形；无对应条目时返回空表。 */
    fun adjectiveWrongForms(adjective: AdjectiveEntry, formId: String): List<String> =
        adjectiveTable
            .filter { it.adjectiveId == adjective.id && it.formId == formId }
            .map { it.wrong }

    // ------------------------------------------------------------------
    // 动词误形表：六类来源
    // ------------------------------------------------------------------

    private fun buildVerbTable(): List<VerbWrongForm> {
        val entries = mutableListOf<VerbWrongForm>()

        // 1) 五段动词 て形 / た形 未发生音便：把词尾改成い段（连用形）再接 て / た。
        //    「す」结尾的 て形/た形 本就等于 い段 + て/た（話して = 話し + て），故排除。
        for (verb in VerbData.VERBS) {
            if (verb.group != "godan") continue
            val ending = verb.dictionary.takeLast(1)
            if (ending == "す") continue
            val iRow = VerbData.I_ROW[ending] ?: continue
            val stem = verb.dictionary.dropLast(1)
            entries.add(VerbWrongForm(verb.id, "base", "te", stem + iRow + "て"))
            entries.add(VerbWrongForm(verb.id, "base", "past", stem + iRow + "た"))
        }

        // 2) 五段动词 可能形 误接「られる」：把五段当成一段，直接用 连用形 + られる。
        //    正确可能是 え段 + る（書ける / 読める），此处给出 書きられる / 読みられる 一类非标准形式。
        for (verb in VerbData.VERBS) {
            if (verb.group != "godan") continue
            val ending = verb.dictionary.takeLast(1)
            val iRow = VerbData.I_ROW[ending] ?: continue
            val stem = verb.dictionary.dropLast(1)
            entries.add(VerbWrongForm(verb.id, "potential", "dictionary", stem + iRow + "られる"))
        }

        // 3) 一段动词 可能形「ら抜き」：られる → れる（食べれる / 見れる）。
        for (verb in VerbData.VERBS) {
            if (verb.group != "ichidan") continue
            entries.add(
                VerbWrongForm(verb.id, "potential", "dictionary", verb.dictionary.dropLast(1) + "れる"),
            )
        }

        // 4) 「行く」特殊变化：未走 い音便，误按普通「く」规则变成 行いて / 行いた。
        entries.add(VerbWrongForm("iku", "base", "te", "行いて"))
        entries.add(VerbWrongForm("iku", "base", "past", "行いた"))
        entries.add(VerbWrongForm("iku", "base", "conditionalTara", "行いたら"))
        entries.add(VerbWrongForm("iku", "base", "tari", "行いたり"))

        // 5) 「する」系特例：可能形 しれる / 受身形 できる / 使役形 しさせる。
        //    base 为词干（する → ""，勉強する → "勉強"）。
        for (verb in VerbData.VERBS) {
            if (verb.kind != "suru") continue
            val base = verb.dictionary.dropLast(2)
            entries.add(VerbWrongForm(verb.id, "potential", "dictionary", base + "しれる"))
            entries.add(VerbWrongForm(verb.id, "passive", "dictionary", base + "できる"))
            entries.add(VerbWrongForm(verb.id, "causative", "dictionary", base + "しさせる"))
        }

        // 6) 「来る」ら抜き：来られる → 来れる（可能形 / 受身形）。
        entries.add(VerbWrongForm("kuru", "potential", "dictionary", "来れる"))
        entries.add(VerbWrongForm("kuru", "passive", "dictionary", "来れる"))

        return entries
    }

    // ------------------------------------------------------------------
    // 形容词误形表：四类来源
    // ------------------------------------------------------------------

    private fun buildAdjectiveTable(): List<AdjectiveWrongForm> = listOf(
        // A) い形容词误按な形容词接「ではなかった」/「ではない」。
        AdjectiveWrongForm("takai", "pastNegative", "高いではなかった"),
        AdjectiveWrongForm("takai", "presentNegative", "高いではない"),
        AdjectiveWrongForm("nagai", "pastNegative", "長いではなかった"),
        AdjectiveWrongForm("samui", "pastNegative", "寒いではなかった"),
        // B) い形容词现在否定时忘了去掉词尾「い」（高いくない）。
        AdjectiveWrongForm("takai", "presentNegative", "高いくない"),

        // C) な形容词误按い形容词接「くない」/「かった」系列。
        AdjectiveWrongForm("shizuka", "presentNegative", "静かくない"),
        AdjectiveWrongForm("shizuka", "pastNegative", "静かくなかった"),
        AdjectiveWrongForm("shizuka", "past", "静かかった"),
        AdjectiveWrongForm("suki", "presentNegative", "好きくない"),
        AdjectiveWrongForm("suki", "past", "好きかった"),
        AdjectiveWrongForm("benri", "past", "便利かった"),
        AdjectiveWrongForm("genki", "presentNegative", "元気くない"),
        AdjectiveWrongForm("kantan", "past", "簡単かった"),
        AdjectiveWrongForm("yuumei", "presentNegative", "有名くない"),
        AdjectiveWrongForm("yuumei", "past", "有名かった"),

        // D) 「きれい」形似い形容词（词尾是「い」实为な形容词）的典型误判。
        AdjectiveWrongForm("kirei", "presentNegative", "きれいくない"),
        AdjectiveWrongForm("kirei", "pastNegative", "きれいくなかった"),
        AdjectiveWrongForm("kirei", "past", "きれいかった"),

        // E) 「いい」特例：全部换成「よく」系列，误用「い」系列即错。
        AdjectiveWrongForm("ii", "presentNegative", "いくない"),
        AdjectiveWrongForm("ii", "past", "いかった"),
        AdjectiveWrongForm("ii", "pastNegative", "いくなかった"),
        AdjectiveWrongForm("ii", "te", "いくて"),
        AdjectiveWrongForm("ii", "adverbial", "いく"),
    )
}
