package com.nihongodoushi.app.engine.adjective

/**
 * 形容词引擎词库与形式常量。
 *
 * 数据忠实移植自 web/adjective-engine.js（只读参考源），逐字段一致：
 * 词条、形式元信息、规则说明文案均与 JS 版相同，日文与中文文案原样保留。
 *
 * 答案归一化复用 VerbEngine.normalizeAnswer（与 JS 版一致），本包不重复实现。
 *
 * 纯逻辑模块：无 Android/androidx 依赖，无文件系统 / DOM / 随机数访问，全部确定性。
 */

/** 形容词词条（对应 JS 的 ADJECTIVES 条目）。 */
data class AdjectiveEntry(
    val id: String,
    val word: String,
    val kana: String,
    val meaning: String,
    val kind: String,
    val special: Boolean = false,
)

/** 形式元信息（对应 JS 的 FORMS 条目）。 */
data class FormInfo(
    val id: String,
    val label: String,
    val level: String,
    val meaning: String,
)

object AdjectiveData {

    val ADJECTIVES: List<AdjectiveEntry> = listOf(
        AdjectiveEntry("takai", "高い", "たかい", "高的", "i"),
        AdjectiveEntry("nagai", "長い", "ながい", "长的", "i"),
        AdjectiveEntry("samui", "寒い", "さむい", "寒冷的", "i"),
        AdjectiveEntry("ookii", "大きい", "おおきい", "大的", "i"),
        AdjectiveEntry("hayai", "早い", "はやい", "早的", "i"),
        AdjectiveEntry("oishii", "おいしい", "おいしい", "好吃的", "i"),
        AdjectiveEntry("isogashii", "忙しい", "いそがしい", "忙碌的", "i"),
        AdjectiveEntry("ii", "いい", "いい", "好的", "i", special = true),
        AdjectiveEntry("shizuka", "静か", "しずか", "安静的", "na"),
        AdjectiveEntry("benri", "便利", "べんり", "方便的", "na"),
        AdjectiveEntry("suki", "好き", "すき", "喜欢的", "na"),
        AdjectiveEntry("kirei", "きれい", "きれい", "干净的、美丽的", "na"),
        AdjectiveEntry("yuumei", "有名", "ゆうめい", "有名的", "na"),
        AdjectiveEntry("kantan", "簡単", "かんたん", "简单的", "na"),
        AdjectiveEntry("genki", "元気", "げんき", "有精神的", "na"),
    )

    val FORMS: List<FormInfo> = listOf(
        FormInfo("attributive", "修饰名词", "basic", "直接放在名词前面使用。"),
        FormInfo("present", "现在肯定", "basic", "作谓语表示现在的性质或状态；更礼貌时可以接「です」。"),
        FormInfo("presentNegative", "现在否定", "basic", "表示「不是……」；两类形容词的否定接法不同。"),
        FormInfo("past", "过去肯定", "basic", "表示过去的性质或状态。"),
        FormInfo("pastNegative", "过去否定", "advanced", "表示过去「不是……」。"),
        FormInfo("te", "て形", "advanced", "用来并列多个性质，也可以表示原因。"),
        FormInfo("adverbial", "状语（修饰动词）", "advanced", "放在动词前面修饰动作；两类形容词的接法不同。"),
    )

    val ADJECTIVE_BY_ID: Map<String, AdjectiveEntry> = ADJECTIVES.associateBy { it.id }
    val FORM_BY_ID: Map<String, FormInfo> = FORMS.associateBy { it.id }

    // い形容词：去掉词尾「い」，再接词尾。修饰名词与现在肯定保持原形。
    internal val I_SUFFIXES: Map<String, String> = mapOf(
        "attributive" to "",
        "present" to "",
        "presentNegative" to "くない",
        "past" to "かった",
        "pastNegative" to "くなかった",
        "te" to "くて",
        "adverbial" to "く",
    )

    // 「いい」是唯一不按普通规则变形的い形容词：全部换成「よく」系列。
    internal val II_RESULTS: Map<String, String> = mapOf(
        "attributive" to "いい",
        "present" to "いい",
        "presentNegative" to "よくない",
        "past" to "よかった",
        "pastNegative" to "よくなかった",
        "te" to "よくて",
        "adverbial" to "よく",
    )

    internal val I_RULE_TEXT: Map<String, String> = mapOf(
        "attributive" to "直接放在名词前，保持原形。",
        "present" to "保持原形直接作谓语；更礼貌时在后面接「です」。",
        "presentNegative" to "把词尾「い」换成「くない」。",
        "past" to "把词尾「い」换成「かった」。",
        "pastNegative" to "把词尾「い」换成「くなかった」。",
        "te" to "把词尾「い」换成「くて」。",
        "adverbial" to "把词尾「い」换成「く」，再放在动词前。",
    )

    internal val NA_RULE_TEXT: Map<String, String> = mapOf(
        "attributive" to "在词干后面接「な」，再放在名词前。",
        "present" to "在词干后面接「だ」；更礼貌时接「です」。",
        "presentNegative" to "在词干后面接「じゃない」，也可以接「ではない」。",
        "past" to "在词干后面接「だった」；更礼貌时接「でした」。",
        "pastNegative" to "在词干后面接「ではなかった」，也可以接「じゃなかった」。",
        "te" to "在词干后面接「で」，用来并列多个性质。",
        "adverbial" to "在词干后面接「に」，再放在动词前。",
    )
}
