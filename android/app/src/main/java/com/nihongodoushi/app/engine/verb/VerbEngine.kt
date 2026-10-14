package com.nihongodoushi.app.engine.verb

import java.text.Normalizer

/**
 * 动词语态派生、活用、规则说明与答案归一化。
 *
 * 忠实移植自 web/verb-engine.js（只读参考源），函数行为与 JS 版一致：
 * - JS 的 RangeError/TypeError 在此统一映射为 IllegalArgumentException（Kotlin 无内建对应层级）。
 * - JS 以普通对象 { text, kind } 在 deriveVoice/inflect 间传递，此处用 [VoiceDerived] 表达。
 * - JS 的 String.prototype.normalize("NFKC") 以 java.text.Normalizer（JDK 标准 API，非 Android 专有）等价实现。
 * - JS 正则 \s 匹配的空白集合比 Java \s 宽（含 \u00A0/\u3000/\uFEFF 等），
 *   为保持跨端一致，显式使用 [JS_WHITESPACE_REGEX] 而非 Regex("\\s")。
 *
 * 纯逻辑模块：无 Android/androidx 依赖，无文件系统 / DOM / 随机数访问，全部确定性。
 */

/** 语态派生后的中间形态（对应 JS deriveVoice 返回的 { text, kind }）。 */
data class VoiceDerived(
    val text: String,
    val kind: String,
)

/** conjugate 的结果（对应 JS 的 { written, kana }）。 */
data class VerbConjugation(
    val written: String,
    val kana: String,
)

object VerbEngine {

    private fun isGodan(kind: String): Boolean = kind == "godan" || kind == "iku"

    /**
     * 语态派生。representation 为 "written"（辞书写法）或 "kana"（假名读法），
     * 仅影响不规则动词「来る」的输出文本，与 JS 版一致。
     */
    fun deriveVoice(text: String, kind: String, voiceId: String, representation: String): VoiceDerived {
        if (voiceId !in VerbData.VOICE_IDS) {
            throw IllegalArgumentException("Unknown verb form: $voiceId")
        }
        if (voiceId == "base") {
            return VoiceDerived(text, kind)
        }

        if (kind == "suru") {
            val base = text.dropLast(2)
            return when (voiceId) {
                "potential" -> VoiceDerived(base + "できる", "ichidan")
                "passive" -> VoiceDerived(base + "される", "ichidan")
                "causative" -> VoiceDerived(base + "させる", "ichidan")
                "causativeShort" -> VoiceDerived(base + "さす", "godan")
                "causativePassive" -> VoiceDerived(base + "させられる", "ichidan")
                "causativePassiveShort" -> VoiceDerived(base + "させられる", "ichidan")
                else -> throw IllegalArgumentException("Unknown verb form: $voiceId")
            }
        }

        if (kind == "kuru") {
            val writing = representation != "kana"
            val (kanji, kana, derivedKind) = when (voiceId) {
                "potential" -> Triple("来られる", "こられる", "ichidan")
                "passive" -> Triple("来られる", "こられる", "ichidan")
                "causative" -> Triple("来させる", "こさせる", "ichidan")
                "causativeShort" -> Triple("来さす", "こさす", "godan")
                "causativePassive" -> Triple("来させられる", "こさせられる", "ichidan")
                "causativePassiveShort" -> Triple("来させられる", "こさせられる", "ichidan")
                else -> throw IllegalArgumentException("Unknown verb form: $voiceId")
            }
            return VoiceDerived(if (writing) kanji else kana, derivedKind)
        }

        val ending = text.takeLast(1)
        val stem = text.dropLast(1)

        if (isGodan(kind)) {
            val aRow = VerbData.A_ROW[ending]
                ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")
            val eRow = VerbData.E_ROW[ending]
                ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")
            val aStem = stem + aRow
            return when (voiceId) {
                "potential" -> VoiceDerived(stem + eRow + "る", "ichidan")
                "passive" -> VoiceDerived(aStem + "れる", "ichidan")
                "causative" -> VoiceDerived(aStem + "せる", "ichidan")
                "causativeShort" -> VoiceDerived(aStem + "す", "godan")
                "causativePassive" -> VoiceDerived(aStem + "せられる", "ichidan")
                "causativePassiveShort" ->
                    VoiceDerived(aStem + (if (ending == "す") "せられる" else "される"), "ichidan")
                else -> throw IllegalArgumentException("Unknown verb form: $voiceId")
            }
        }

        if (kind == "ichidan") {
            return when (voiceId) {
                "potential" -> VoiceDerived(stem + "られる", "ichidan")
                "passive" -> VoiceDerived(stem + "られる", "ichidan")
                "causative" -> VoiceDerived(stem + "させる", "ichidan")
                "causativeShort" -> VoiceDerived(stem + "さす", "godan")
                "causativePassive" -> VoiceDerived(stem + "させられる", "ichidan")
                "causativePassiveShort" -> VoiceDerived(stem + "させられる", "ichidan")
                else -> throw IllegalArgumentException("Unknown verb form: $voiceId")
            }
        }

        throw IllegalArgumentException("Unsupported verb group: $kind")
    }

    /**
     * 在某个（已派生语态的）形态上做活用变形，返回变形后的文本。
     * JS 版 inflect 接收 { text, kind } 对象；此处对应 [VoiceDerived]。
     */
    fun inflect(verb: VoiceDerived, formId: String, representation: String): String {
        if (formId !in VerbData.INFLECTION_IDS) {
            throw IllegalArgumentException("Unknown inflection: $formId")
        }
        val text = verb.text
        val kind = verb.kind

        if (kind == "suru") {
            val base = text.dropLast(2)
            return when (formId) {
                "dictionary" -> text
                "masuStem" -> base + "し"
                "masu" -> base + "します"
                "masuNegative" -> base + "しません"
                "masuPast" -> base + "しました"
                "masuNegativePast" -> base + "しませんでした"
                "negative" -> base + "しない"
                "negativePast" -> base + "しなかった"
                "past" -> base + "した"
                "te" -> base + "して"
                "conditionalBa" -> base + "すれば"
                "conditionalTara" -> base + "したら"
                "volitional" -> base + "しよう"
                "imperative" -> base + "しろ"
                "sou" -> base + "しそう"
                "tai" -> base + "したい"
                "zu" -> base + "せず"
                "tari" -> base + "したり"
                else -> throw IllegalArgumentException("Unknown inflection: $formId")
            }
        }

        if (kind == "kuru") {
            val writing = representation != "kana"
            val base = if (writing) "来" else "き"
            val negativeBase = if (writing) "来" else "こ"
            return when (formId) {
                "dictionary" -> if (writing) "来る" else "くる"
                "masuStem" -> base
                "masu" -> base + "ます"
                "masuNegative" -> base + "ません"
                "masuPast" -> base + "ました"
                "masuNegativePast" -> base + "ませんでした"
                "negative" -> negativeBase + "ない"
                "negativePast" -> negativeBase + "なかった"
                "past" -> base + "た"
                "te" -> base + "て"
                "conditionalBa" -> (if (writing) "来れ" else "くれ") + "ば"
                "conditionalTara" -> base + "たら"
                "volitional" -> (if (writing) "来よ" else "こよ") + "う"
                "imperative" -> if (writing) "来い" else "こい"
                "sou" -> base + "そう"
                "tai" -> base + "たい"
                "zu" -> (if (writing) "来" else "こ") + "ず"
                "tari" -> base + "たり"
                else -> throw IllegalArgumentException("Unknown inflection: $formId")
            }
        }

        if (kind == "ichidan") {
            val stem = text.dropLast(1)
            return when (formId) {
                "dictionary" -> text
                "masuStem" -> stem
                "masu" -> stem + "ます"
                "masuNegative" -> stem + "ません"
                "masuPast" -> stem + "ました"
                "masuNegativePast" -> stem + "ませんでした"
                "negative" -> stem + "ない"
                "negativePast" -> stem + "なかった"
                "past" -> stem + "た"
                "te" -> stem + "て"
                "conditionalBa" -> stem + "れば"
                "conditionalTara" -> stem + "たら"
                "volitional" -> stem + "よう"
                "imperative" -> stem + "ろ"
                "sou" -> stem + "そう"
                "tai" -> stem + "たい"
                "zu" -> stem + "ず"
                "tari" -> stem + "たり"
                else -> throw IllegalArgumentException("Unknown inflection: $formId")
            }
        }

        if (!isGodan(kind)) {
            throw IllegalArgumentException("Unsupported verb group: $kind")
        }

        val ending = text.takeLast(1)
        val stem = text.dropLast(1)
        val iRow = VerbData.I_ROW[ending]
            ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")
        val aRow = VerbData.A_ROW[ending]
            ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")
        val eRow = VerbData.E_ROW[ending]
            ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")
        val oRow = VerbData.O_ROW[ending]
            ?: throw IllegalArgumentException("Unsupported godan verb ending: $ending")

        val iStem = stem + iRow
        val aStem = stem + aRow
        // 「行く」是特殊变化：て形/た形（及由此派生的たら・たり）为「行って／行った」。
        val pastStem = stem + (if (kind == "iku") "った" else VerbData.GODAN_PAST.getValue(ending))
        val teStem = stem + (if (kind == "iku") "って" else VerbData.GODAN_TE.getValue(ending))
        return when (formId) {
            "dictionary" -> text
            "masuStem" -> iStem
            "masu" -> iStem + "ます"
            "masuNegative" -> iStem + "ません"
            "masuPast" -> iStem + "ました"
            "masuNegativePast" -> iStem + "ませんでした"
            "negative" -> aStem + "ない"
            "negativePast" -> aStem + "なかった"
            "past" -> pastStem
            "te" -> teStem
            "conditionalBa" -> stem + eRow + "ば"
            "conditionalTara" -> pastStem + "ら"
            "volitional" -> stem + oRow + "う"
            "imperative" -> stem + eRow
            "sou" -> iStem + "そう"
            "tai" -> iStem + "たい"
            "zu" -> aStem + "ず"
            "tari" -> pastStem + "り"
            else -> throw IllegalArgumentException("Unknown inflection: $formId")
        }
    }

    /**
     * 完整活用：先对辞书写法与假名读法分别做语态派生，再各自活用。
     * JS 版会校验 verb.dictionary/reading 为字符串；Kotlin 非空类型天然保证，无需防御分支。
     */
    fun conjugate(verb: VerbEntry, voiceId: String, inflectionId: String): VerbConjugation {
        val writtenVerb = deriveVoice(verb.dictionary, verb.kind, voiceId, "written")
        val kanaVerb = deriveVoice(verb.reading, verb.kind, voiceId, "kana")
        return VerbConjugation(
            written = inflect(writtenVerb, inflectionId, "written"),
            kana = inflect(kanaVerb, inflectionId, "kana"),
        )
    }

    /** 语态变化规则的说明文案（对应 JS 的 voiceRule，未在 API 导出）。 */
    private fun voiceRule(verb: VerbEntry, voiceId: String): String {
        val godan = isGodan(verb.kind)
        return when (voiceId) {
            "base" -> ""
            "potential" -> when {
                godan -> "五段动词词尾改为对应え段，再接「る」。"
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接「られる」。"
                verb.kind == "suru" -> "「する」的可能形是「できる」。"
                else -> "「来る」的可能形是「来られる（こられる）」。"
            }
            "passive" -> when {
                godan -> "五段动词词尾改为对应あ段，再接「れる」。"
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接「られる」。"
                verb.kind == "suru" -> "「する」变为「される」。"
                else -> "「来る」变为「来られる（こられる）」。"
            }
            "causative" -> when {
                godan -> "五段动词词尾改为对应あ段，再接「せる」。"
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接「させる」。"
                verb.kind == "suru" -> "「する」变为「させる」。"
                else -> "「来る」变为「来させる（こさせる）」。"
            }
            "causativeShort" -> when {
                godan -> "五段动词词尾改为对应あ段，再接较简短的「す」。"
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接较简短的「さす」。"
                verb.kind == "suru" -> "「する」变为较简短的「さす」。"
                else -> "「来る」变为较简短的「来さす（こさす）」。"
            }
            "causativePassive" -> when {
                godan -> "先形成五段动词使役形，再接「られる」。"
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接「させられる」。"
                verb.kind == "suru" -> "「する」变为「させられる」。"
                else -> "「来る」变为「来させられる（こさせられる）」。"
            }
            "causativePassiveShort" -> when {
                godan ->
                    if (verb.dictionary.endsWith("す")) {
                        "「す」结尾的五段动词通常使用「させられる」。"
                    } else {
                        "五段动词词尾改为对应あ段，再接较简短的「される」。"
                    }
                verb.kind == "ichidan" -> "一段动词去掉「る」，再接「させられる」。"
                verb.kind == "suru" -> "「する」的短使役受身也常用「させられる」。"
                else -> "「来る」的短使役受身也常用「来させられる」。"
            }
            else -> throw IllegalArgumentException("Unknown verb form: $voiceId")
        }
    }

    fun explainVoice(verb: VerbEntry, voiceId: String): String {
        if (voiceId !in VerbData.VOICE_IDS) {
            throw IllegalArgumentException("A known verb and voice are required.")
        }
        return if (voiceId == "base") {
            "不做语态变化，保留辞书形。"
        } else {
            voiceRule(verb, voiceId)
        }
    }

    /** 活用规则的说明文案（对应 JS 的 inflectionRule，未在 API 导出）。 */
    private fun inflectionRule(kind: String, inflectionId: String, verb: VerbEntry, voiceId: String): String {
        if (voiceId == "base" && verb.kind == "iku" && (inflectionId == "te" || inflectionId == "past")) {
            return "「行く」是特殊变化：て形为「行って」，た形为「行った」。"
        }
        if (kind == "suru") {
            return when (inflectionId) {
                "dictionary" -> "保留辞书形「する」。"
                "masuStem" -> "「する」的连用形是「し」。"
                "masu" -> "连用形「し」加「ます」。"
                "masuNegative" -> "连用形「し」加「ません」。"
                "masuPast" -> "连用形「し」加「ました」。"
                "masuNegativePast" -> "连用形「し」加「ませんでした」。"
                "negative" -> "「する」变为「しない」。"
                "negativePast" -> "「する」变为「しなかった」。"
                "past" -> "「する」变为「した」。"
                "te" -> "「する」变为「して」。"
                "conditionalBa" -> "「する」变为「すれば」。"
                "conditionalTara" -> "た形「した」加「ら」。"
                "volitional" -> "「する」变为「しよう」。"
                "imperative" -> "「する」变为「しろ」。"
                "sou" -> "连用形「し」加「そう」。"
                "tai" -> "连用形「し」加「たい」。"
                "zu" -> "「する」变为「せず」。"
                "tari" -> "た形「した」加「り」。"
                else -> throw IllegalArgumentException("Unknown inflection: $inflectionId")
            }
        }
        if (kind == "kuru") {
            return when (inflectionId) {
                "dictionary" -> "保留辞书形「来る（くる）」。"
                "masuStem" -> "「来る」的ます词干读作「き」。"
                "masu" -> "「来る」的ます形读作「きます」。"
                "masuNegative" -> "「来る」的ます否定形读作「きません」。"
                "masuPast" -> "「来る」的ます过去形读作「きました」。"
                "masuNegativePast" -> "「来る」的ます过去否定形读作「きませんでした」。"
                "negative" -> "「来る」的ない形读作「こない」。"
                "negativePast" -> "「来る」的ない过去形读作「こなかった」。"
                "past" -> "「来る」的た形读作「きた」。"
                "te" -> "「来る」的て形读作「きて」。"
                "conditionalBa" -> "「来る」变为「くれば」。"
                "conditionalTara" -> "た形「来た」加「ら」。"
                "volitional" -> "「来る」变为「来よう（こよう）」。"
                "imperative" -> "「来る」变为「来い（こい）」。"
                "sou" -> "ます词干读作「き」，再接「そう」。"
                "tai" -> "ます词干读作「き」，再接「たい」。"
                "zu" -> "「来る」变为「来ず（こず）」。"
                "tari" -> "た形「来た」加「り」。"
                else -> throw IllegalArgumentException("Unknown inflection: $inflectionId")
            }
        }
        if (kind == "ichidan") {
            return when (inflectionId) {
                "dictionary" -> "保持辞书形；这是其它活用的基础。"
                "masuStem" -> "去掉词尾「る」，得到连用形。"
                "masu" -> "去掉「る」后接「ます」。"
                "masuNegative" -> "去掉「る」后接「ません」。"
                "masuPast" -> "去掉「る」后接「ました」。"
                "masuNegativePast" -> "去掉「る」后接「ませんでした」。"
                "negative" -> "去掉「る」后接「ない」。"
                "negativePast" -> "去掉「る」后接「なかった」。"
                "past" -> "去掉「る」后接「た」。"
                "te" -> "去掉「る」后接「て」。"
                "conditionalBa" -> "去掉「る」后接「れば」。"
                "conditionalTara" -> "先变成た形，再接「ら」。"
                "volitional" -> "去掉「る」后接「よう」。"
                "imperative" -> "去掉「る」后接「ろ」。"
                "sou" -> "去掉「る」得到ます词干，再接「そう」。"
                "tai" -> "去掉「る」得到ます词干，再接「たい」。"
                "zu" -> "去掉「る」后接「ず」。"
                "tari" -> "先变成た形，再接「り」。"
                else -> throw IllegalArgumentException("Unknown inflection: $inflectionId")
            }
        }
        return when (inflectionId) {
            "dictionary" -> "保持辞书形；这是其它活用的基础。"
            "masuStem" -> "把词尾换成对应的い段，得到连用形。"
            "masu" -> "把词尾换成对应的い段，再接「ます」。"
            "masuNegative" -> "把词尾换成对应的い段，再接「ません」。"
            "masuPast" -> "把词尾换成对应的い段，再接「ました」。"
            "masuNegativePast" -> "把词尾换成对应的い段，再接「ませんでした」。"
            "negative" -> "把词尾换成对应的あ段，再接「ない」；「う」要变成「わ」。"
            "negativePast" -> "把词尾换成对应的あ段，再接「なかった」；「う」要变成「わ」。"
            "past" -> "根据五段动词词尾发生音便，再接成た形。"
            "te" -> "根据五段动词词尾发生音便，再接成て形。"
            "conditionalBa" -> "把词尾换成对应的え段，再接「ば」。"
            "conditionalTara" -> "先变成た形，再接「ら」。"
            "volitional" -> "把词尾换成对应的お段，再接「う」。"
            "imperative" -> "把词尾换成对应的え段。"
            "sou" -> "把词尾换成对应的い段，再接「そう」。"
            "tai" -> "把词尾换成对应的い段，再接「たい」。"
            "zu" -> "把词尾换成对应的あ段，再接「ず」；「う」要变成「わ」。"
            "tari" -> "先变成た形，再接「り」。"
            else -> throw IllegalArgumentException("Unknown inflection: $inflectionId")
        }
    }

    /**
     * 完整说明：语态 + 活用两步的组合文案。
     * base 语态为一步，其余语态为「先变语态，再变形」两行。
     */
    fun explain(verb: VerbEntry, voiceId: String, inflectionId: String): String {
        if (voiceId !in VerbData.VOICE_IDS || inflectionId !in VerbData.INFLECTION_IDS) {
            throw IllegalArgumentException("A known verb form and inflection are required.")
        }
        val derived = deriveVoice(verb.dictionary, verb.kind, voiceId, "written")
        val baseRule = inflectionRule(derived.kind, inflectionId, verb, voiceId)
        val voice = VerbData.VOICES.first { it.id == voiceId }
        val inflection = VerbData.INFLECTIONS.first { it.id == inflectionId }
        return if (voiceId == "base") {
            "变「" + inflection.label + "」：" + baseRule
        } else {
            "先变「" + voice.label + "」：" + explainVoice(verb, voiceId) +
                "\n再变「" + inflection.label + "」：" + baseRule
        }
    }

    /** 罗马字转平假名：NFKC + 小写 + 去空白，逐段最长匹配；遇到无法映射的字符时整体返回规范化后的原串。 */
    fun toHiraganaFromRomaji(input: String): String {
        val source = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .lowercase()
            .replace(JS_WHITESPACE_REGEX, "")
        val result = StringBuilder()
        var index = 0

        while (index < source.length) {
            val char = source[index]
            if (JS_WHITESPACE_REGEX.containsMatchIn(char.toString()) || char == '\'') {
                index += 1
                continue
            }

            if (char == 'n') {
                val next = source.getOrNull(index + 1)
                val afterNext = source.getOrNull(index + 2)
                if (next == '\'') {
                    result.append("ん")
                    index += 2
                    continue
                }
                if (next == 'n') {
                    result.append("ん")
                    index += if (afterNext == null || ROMAJI_CONSONANT_REGEX.containsMatchIn(afterNext.toString())) 2 else 1
                    continue
                }
                if (next == null || (ROMAJI_CONSONANT_REGEX.containsMatchIn(next.toString()) && next != 'y')) {
                    result.append("ん")
                    index += 1
                    continue
                }
            }

            if (ROMAJI_CONSONANT_REGEX.containsMatchIn(char.toString()) &&
                char != 'n' &&
                source.getOrNull(index + 1) == char
            ) {
                result.append("っ")
                index += 1
                continue
            }

            val three = source.substring(index, minOf(index + 3, source.length))
            val two = source.substring(index, minOf(index + 2, source.length))
            when {
                VerbData.ROMAJI.containsKey(three) -> {
                    result.append(VerbData.ROMAJI.getValue(three))
                    index += 3
                }
                VerbData.ROMAJI.containsKey(two) -> {
                    result.append(VerbData.ROMAJI.getValue(two))
                    index += 2
                }
                VerbData.ROMAJI.containsKey(char.toString()) -> {
                    result.append(VerbData.ROMAJI.getValue(char.toString()))
                    index += 1
                }
                else -> return source
            }
        }
        return result.toString()
    }

    // 答案归一化管线：NFKC + 去空白 -> 去首尾中英标点 -> 片假名转平假名
    // -> 长音符号展开 -> 纯罗马字输入转假名。各步对空串与异常输入均安全。

    /**
     * JS 正则 \s 的等价字符类。Java 正则的 \s 只匹配 [ \t\n\x0B\f\r]，
     * 而 JS \s 还包含 \u00A0、\u3000（全角空格）、\uFEFF 等；
     * 为与 JS 版行为一致，此处显式列出 JS 的完整空白集合。
     */
    private val JS_WHITESPACE_REGEX =
        Regex("[\\t\\n\\x0B\\f\\r\\x20\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]")

    private val ROMAJI_CONSONANT_REGEX = Regex("[bcdfghjklmpqrstvwxyz]")

    private val ROMAJI_ONLY_REGEX = Regex("^[a-z']+$", RegexOption.IGNORE_CASE)

    private val LEADING_PUNCTUATION =
        Regex("^[\\u0021-\\u002F\\u003A-\\u0040\\u005B-\\u0060\\u007B-\\u007E\\u3001\\u3002\\u00B7\\u2014\\u2026\\u300C-\\u300F\\u3010\\u3011\\uFF01\\uFF0C\\uFF0E\\uFF1A\\uFF1B\\uFF1F]+")

    private val TRAILING_PUNCTUATION =
        Regex("[\\u0021-\\u002F\\u003A-\\u0040\\u005B-\\u0060\\u007B-\\u007E\\u3001\\u3002\\u00B7\\u2014\\u2026\\u300C-\\u300F\\u3010\\u3011\\uFF01\\uFF0C\\uFF0E\\uFF1A\\uFF1B\\uFF1F]+$")

    private val KATAKANA_REGEX = Regex("[\\u30A1-\\u30F6]")

    private val MACRON_REGEX = Regex("[\\u0101\\u0113\\u012B\\u014D\\u016B]")

    private val MACRON_EXPANSIONS: Map<String, String> = mapOf(
        "\u0101" to "aa",
        "\u0113" to "ee",
        "\u012B" to "ii",
        "\u014D" to "ou",
        "\u016B" to "uu",
    )

    /** NFKC 规范化并移除全部空白（对应 JS 的 normalizeText）。 */
    fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).replace(JS_WHITESPACE_REGEX, "")

    private fun katakanaToHiragana(text: String): String =
        KATAKANA_REGEX.replace(text) { match ->
            // JS: String.fromCharCode(charCodeAt(0) - 0x60)，即码位前移 0x60。
            (match.value[0].code - 0x60).toChar().toString()
        }

    private fun expandMacrons(text: String): String =
        MACRON_REGEX.replace(text) { match -> MACRON_EXPANSIONS.getValue(match.value) }

    /** 答案归一化：容忍中英标点、全半角、片假名、长音符与罗马字输入。 */
    fun normalizeAnswer(value: String): String {
        val stripped = normalizeText(value)
            .replace(LEADING_PUNCTUATION, "")
            .replace(TRAILING_PUNCTUATION, "")
        val normalized = expandMacrons(katakanaToHiragana(stripped))
        return if (ROMAJI_ONLY_REGEX.matches(normalized)) {
            toHiraganaFromRomaji(normalized)
        } else {
            normalized
        }
    }
}
