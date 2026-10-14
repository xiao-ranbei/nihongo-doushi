package com.nihongodoushi.app.engine.verb

/**
 * 动词引擎词库与形式常量。
 *
 * 数据忠实移植自 web/verb-engine.js（只读参考源），逐字段一致：
 * 语态/活用形式的说明文案、词库词条的每个字段均与 JS 版相同，
 * 日文与中文文案原样保留，不做改写。
 *
 * 本文件与 VerbEngine.kt 均为纯逻辑模块：
 * 禁止 import Android/androidx 类，禁止文件系统 / DOM / 随机数访问，
 * 所有函数确定性、可复现。
 */

/** 语态元信息（对应 JS 的 VOICES 条目）。 */
data class VoiceInfo(
    val id: String,
    val label: String,
    val meaning: String,
)

/** 活用形式元信息（对应 JS 的 INFLECTIONS 条目）。 */
data class InflectionInfo(
    val id: String,
    val label: String,
    val level: String,
    val meaning: String,
)

/** 动词词条（对应 JS 的 VERBS 条目）。 */
data class VerbEntry(
    val id: String,
    val dictionary: String,
    val reading: String,
    val meaning: String,
    val group: String,
    val kind: String,
)

object VerbData {

    val VOICES: List<VoiceInfo> = listOf(
        VoiceInfo("base", "原形", "普通形态，不附加可能、受身或使役等语态意义。"),
        VoiceInfo("potential", "可能形", "表示“能／可以做……”，常见为能力或许可。"),
        VoiceInfo("passive", "受身形", "表示“被……”，也可表达受到影响或感受；是否带负面语气看上下文。"),
        VoiceInfo("causative", "使役形", "表示“让／使某人做……”，也可表示允许对方做。"),
        VoiceInfo("causativeShort", "短使役形", "与使役形大意相同的短形，较多见于口语；使用场合会因动词和语境而异。"),
        VoiceInfo("causativePassive", "使役受身形", "表示“被迫做……”或“不得不做……”，强调受到他人驱使。"),
        VoiceInfo("causativePassiveShort", "短使役受身形", "与使役受身形大意相同的短形，较多见于口语；实际用法因动词和语境而异。"),
    )

    val INFLECTIONS: List<InflectionInfo> = listOf(
        InflectionInfo("dictionary", "辞书形", "basic", "普通形的非过去表达，可表示习惯、事实或将来；具体时间由语境判断。"),
        InflectionInfo("masuStem", "连用形（ます词干）", "basic", "连用词干本身通常不单独表达时态或礼貌，常接「ます」及其他后续表达。"),
        InflectionInfo("masu", "ます形", "basic", "礼貌的非过去表达，可表示习惯、事实或将来。"),
        InflectionInfo("masuNegative", "ます否定形", "basic", "礼貌地表示不做、不会做或目前不是……。"),
        InflectionInfo("masuPast", "ます过去形", "basic", "礼貌地表示过去做了某事或过去的状态。"),
        InflectionInfo("masuNegativePast", "ます过去否定形", "basic", "礼貌地表示过去没有做某事或不处于某状态。"),
        InflectionInfo("negative", "ない形", "basic", "普通形的非过去否定，可表示不做、不会做或不是……。"),
        InflectionInfo("negativePast", "ない过去形", "basic", "普通形的过去否定，表示过去没有做某事或不处于某状态。"),
        InflectionInfo("past", "た形", "basic", "普通形的过去／完成表达；也可结合语境表示经历或结果状态。"),
        InflectionInfo("te", "て形", "basic", "用于连接动作或状态；也常用于请求「てください」、持续或结果「ている」等，具体含义取决于后续表达。"),
        InflectionInfo("conditionalBa", "条件形（ば）", "basic", "表示“如果……就……”，常用于一般条件、规律或假设。"),
        InflectionInfo("conditionalTara", "条件形（たら）", "basic", "表示“如果……／当……时”；也可表示前项完成后再发生后项。"),
        InflectionInfo("volitional", "意向形", "advanced", "表示提议“一起……吧”或说话者的意志“我来……／打算……”。"),
        InflectionInfo("imperative", "命令形", "advanced", "直接命令对方“做……！”，语气较强，使用时注意场合。"),
        InflectionInfo("sou", "そう形", "advanced", "根据外观或迹象表示“看起来要……／似乎……”（样态）；注意与传闻「そうだ」区分。"),
        InflectionInfo("tai", "たい形", "advanced", "表示（说话者）想做……；主语和具体用法受语境影响。"),
        InflectionInfo("zu", "ず形", "advanced", "表示“不……／未……”；偏书面，常见于「〜ずに」（不做……而……）。"),
        InflectionInfo("tari", "たり形", "advanced", "用来举例列举动作，如“做做……之类的事”，常和「する」搭配，不表示穷举。"),
    )

    val VERBS: List<VerbEntry> = listOf(
        VerbEntry("iku", "行く", "いく", "去、前往", "godan", "iku"),
        VerbEntry("kaku", "書く", "かく", "写", "godan", "godan"),
        VerbEntry("kiku", "聞く", "きく", "听、询问", "godan", "godan"),
        VerbEntry("oyogu", "泳ぐ", "およぐ", "游泳", "godan", "godan"),
        VerbEntry("isogu", "急ぐ", "いそぐ", "赶快", "godan", "godan"),
        VerbEntry("hanasu", "話す", "はなす", "说话", "godan", "godan"),
        VerbEntry("dasu", "出す", "だす", "拿出、提交", "godan", "godan"),
        VerbEntry("matsu", "待つ", "まつ", "等待", "godan", "godan"),
        VerbEntry("motsu", "持つ", "もつ", "拿、持有", "godan", "godan"),
        VerbEntry("shinu", "死ぬ", "しぬ", "死亡", "godan", "godan"),
        VerbEntry("asobu", "遊ぶ", "あそぶ", "玩耍", "godan", "godan"),
        VerbEntry("yomu", "読む", "よむ", "阅读", "godan", "godan"),
        VerbEntry("nomu", "飲む", "のむ", "喝", "godan", "godan"),
        VerbEntry("kau", "買う", "かう", "买", "godan", "godan"),
        VerbEntry("au", "会う", "あう", "见面", "godan", "godan"),
        VerbEntry("utau", "歌う", "うたう", "唱歌", "godan", "godan"),
        VerbEntry("omou", "思う", "おもう", "想、认为", "godan", "godan"),
        VerbEntry("kaeru", "帰る", "かえる", "回去", "godan", "godan"),
        VerbEntry("hairu", "入る", "はいる", "进入", "godan", "godan"),
        VerbEntry("kiru", "切る", "きる", "切、剪", "godan", "godan"),
        VerbEntry("hashiru", "走る", "はしる", "跑", "godan", "godan"),
        VerbEntry("tsukuru", "作る", "つくる", "制作", "godan", "godan"),
        VerbEntry("toru", "取る", "とる", "拿、取得", "godan", "godan"),
        VerbEntry("tatsu", "立つ", "たつ", "站立", "godan", "godan"),
        VerbEntry("shiru", "知る", "しる", "知道", "godan", "godan"),
        VerbEntry("kau-tsukau", "使う", "つかう", "使用", "godan", "godan"),
        VerbEntry("taberu", "食べる", "たべる", "吃", "ichidan", "ichidan"),
        VerbEntry("miru", "見る", "みる", "看", "ichidan", "ichidan"),
        VerbEntry("okiru", "起きる", "おきる", "起床", "ichidan", "ichidan"),
        VerbEntry("neru", "寝る", "ねる", "睡觉", "ichidan", "ichidan"),
        VerbEntry("oshieru", "教える", "おしえる", "教、告诉", "ichidan", "ichidan"),
        VerbEntry("akeru", "開ける", "あける", "打开", "ichidan", "ichidan"),
        VerbEntry("shimeru", "閉める", "しめる", "关闭", "ichidan", "ichidan"),
        VerbEntry("deru", "出る", "でる", "出去、出现", "ichidan", "ichidan"),
        VerbEntry("wasureru", "忘れる", "わすれる", "忘记", "ichidan", "ichidan"),
        VerbEntry("kariru", "借りる", "かりる", "借入", "ichidan", "ichidan"),
        VerbEntry("oriru", "降りる", "おりる", "下车、下来", "ichidan", "ichidan"),
        VerbEntry("hajimeru", "始める", "はじめる", "开始", "ichidan", "ichidan"),
        VerbEntry("kangaeru", "考える", "かんがえる", "思考", "ichidan", "ichidan"),
        VerbEntry("suru", "する", "する", "做", "irregular", "suru"),
        VerbEntry("benkyou-suru", "勉強する", "べんきょうする", "学习", "irregular", "suru"),
        VerbEntry("undou-suru", "運動する", "うんどうする", "运动", "irregular", "suru"),
        VerbEntry("souji-suru", "掃除する", "そうじする", "打扫", "irregular", "suru"),
        VerbEntry("kuru", "来る", "くる", "来", "irregular", "kuru"),
    )

    val VOICE_IDS: Set<String> = VOICES.map { it.id }.toSet()
    val INFLECTION_IDS: Set<String> = INFLECTIONS.map { it.id }.toSet()

    val VERB_BY_ID: Map<String, VerbEntry> = VERBS.associateBy { it.id }

    // 五段动词词尾对应行（う段 -> 各段），键值与 JS 版逐项一致。
    internal val I_ROW: Map<String, String> = mapOf(
        "う" to "い", "く" to "き", "ぐ" to "ぎ", "す" to "し", "つ" to "ち",
        "ぬ" to "に", "ぶ" to "び", "む" to "み", "る" to "り",
    )

    internal val A_ROW: Map<String, String> = mapOf(
        "う" to "わ", "く" to "か", "ぐ" to "が", "す" to "さ", "つ" to "た",
        "ぬ" to "な", "ぶ" to "ば", "む" to "ま", "る" to "ら",
    )

    internal val E_ROW: Map<String, String> = mapOf(
        "う" to "え", "く" to "け", "ぐ" to "げ", "す" to "せ", "つ" to "て",
        "ぬ" to "ね", "ぶ" to "べ", "む" to "め", "る" to "れ",
    )

    internal val O_ROW: Map<String, String> = mapOf(
        "う" to "お", "く" to "こ", "ぐ" to "ご", "す" to "そ", "つ" to "と",
        "ぬ" to "の", "ぶ" to "ぼ", "む" to "も", "る" to "ろ",
    )

    internal val GODAN_TE: Map<String, String> = mapOf(
        "う" to "って", "つ" to "って", "る" to "って",
        "む" to "んで", "ぶ" to "んで", "ぬ" to "んで",
        "く" to "いて", "ぐ" to "いで", "す" to "して",
    )

    internal val GODAN_PAST: Map<String, String> = mapOf(
        "う" to "った", "つ" to "った", "る" to "った",
        "む" to "んだ", "ぶ" to "んだ", "ぬ" to "んだ",
        "く" to "いた", "ぐ" to "いだ", "す" to "した",
    )

    // 罗马字 -> 平假名 映射，键值与 JS 版 ROMAJI 表逐项一致（含 si/ti/tu/hu/zi/di/du 等变体拼写）。
    internal val ROMAJI: Map<String, String> = mapOf(
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "shi" to "し", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "chi" to "ち", "ti" to "ち", "tsu" to "つ", "tu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を", "n" to "ん",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "ji" to "じ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
    )
}
