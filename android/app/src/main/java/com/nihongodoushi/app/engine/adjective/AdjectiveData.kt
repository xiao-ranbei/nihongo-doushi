package com.nihongodoushi.app.engine.adjective

/**
 * 形容词引擎词库与形式常量（自动生成，勿手改）。
 *
 * 生成器：web/tools/generate-adjective-data.js
 * 源：web/adjective-engine.js（只读参考源）
 * 规模：15 个词条 / 10 种形式
 *
 * 数据忠实移植自 JS 源，逐字段一致：
 * 词条、形式元信息、规则说明文案均与 JS 版相同，日文与中文文案原样保留。
 *
 * 答案归一化复用 VerbEngine.normalizeAnswer，与 JS 版复用 verb-engine 的做法一致。
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
    /** 仅形态练习用：该形式在日常书面中不独立出现（未然形接推量う / 命令请求表达）。 */
    val morphologyPracticeOnly: Boolean = false,
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
        FormInfo("conditional", "条件形（ば）", "advanced", "表示假设条件；い形容词接「ければ」，な形容词接「なら（ば）」。"),
        FormInfo("adverbial", "状语（修饰动词）", "advanced", "放在动词前面修饰动作；两类形容词的接法不同。"),
        FormInfo("mizen", "未然形（接推量う）", "advanced", "用未然形接推量「う」；日常表达也常使用「高いだろう」等形式。", morphologyPracticeOnly = true),
        FormInfo("imperativeRequest", "命令表达（无独立形）", "advanced", "形容词本身没有独立命令形；练习用「～く／にしてください」表达礼貌请求。", morphologyPracticeOnly = true),
    )

    val ADJECTIVE_BY_ID: Map<String, AdjectiveEntry> = ADJECTIVES.associateBy { it.id }
    val FORM_BY_ID: Map<String, FormInfo> = FORMS.associateBy { it.id }

    // い形容词：去掉词尾「い」，再接词尾。修饰名词与现在肯定保持原形。
    internal val I_SUFFIXES: Map<String, String> =
        mapOf(
            "attributive" to "",
            "present" to "",
            "presentNegative" to "くない",
            "past" to "かった",
            "pastNegative" to "くなかった",
            "te" to "くて",
            "conditional" to "ければ",
            "adverbial" to "く",
            "mizen" to "かろう",
            "imperativeRequest" to "くしてください",
        )

    // 「いい」是唯一不按普通规则变形的い形容词：全部换成「よく」系列。
    internal val II_RESULTS: Map<String, String> =
        mapOf(
            "attributive" to "いい",
            "present" to "いい",
            "presentNegative" to "よくない",
            "past" to "よかった",
            "pastNegative" to "よくなかった",
            "te" to "よくて",
            "conditional" to "よければ",
            "adverbial" to "よく",
            "mizen" to "よかろう",
            "imperativeRequest" to "よくしてください",
        )

    // い形容词规则说明（对应 JS 的 I_RULE_TEXT）。
    internal val I_RULE_TEXT: Map<String, String> =
        mapOf(
            "attributive" to "连体形：直接放在名词前，保持原形。",
            "present" to "终止形：保持原形直接作谓语；更礼貌时在后面接「です」。",
            "presentNegative" to "先用连用形「く」，再接「ない」。",
            "past" to "连用形发生「かっ」变化后接「た」，即词尾「い」换成「かった」。",
            "pastNegative" to "先用连用形「く」接「ない」，再把「ない」变为「なかった」，即「くなかった」。",
            "te" to "连用形「く」接「て」，用于连接性质或表达原因。",
            "conditional" to "仮定形：把词尾「い」换成「ければ」，表示假设条件。",
            "adverbial" to "把词尾「い」换成「く」，再放在动词前。",
            "mizen" to "未然形「かろ」接推量助动词「う」；日常也常说「高いだろう」。",
            "imperativeRequest" to "い形容词没有独立命令形；这里练习礼貌请求「～くしてください」。",
        )

    // な形容词规则说明（对应 JS 的 NA_RULE_TEXT）。
    internal val NA_RULE_TEXT: Map<String, String> =
        mapOf(
            "attributive" to "连体形：在词干后面接「な」，再放在名词前。",
            "present" to "终止形：在词干后面接「だ」；更礼貌时接「です」。",
            "presentNegative" to "接「じゃない」，也可以说「ではない」；不要套用い形容词的「くない」。",
            "past" to "连用形「だっ」接「た」，形成「だった」；更礼貌时接「でした」。",
            "pastNegative" to "接「ではなかった」，也可以接「じゃなかった」。",
            "te" to "连用形「で」用来并列多个性质。",
            "conditional" to "仮定形：在词干后接「なら」；也可以说「ならば」。",
            "adverbial" to "在词干后面接「に」，再放在动词前。",
            "mizen" to "未然形「だろ」接推量助动词「う」，形成「だろう」。",
            "imperativeRequest" to "な形容词没有独立命令形；请求对方安静可说「静かにしてください」。",
        )

}
