package com.nihongodoushi.app.engine.mastery

import kotlin.math.roundToInt

/**
 * 掌握度口径（三层改造，见 docs/android-module-three-tier-refactor.md §3.2 / §7）。
 *
 * 口径：**未用提示答对过**的组合才计入；按词条去重，同一词条对同一形式只算一次。
 * - 动词组合键：`voiceId::inflectionId::verbId`
 * - 形容词组合键：`formId::adjectiveId`
 *
 * 卡片掌握度 = 该形式下已掌握的词条数 / 词条总数，分母固定、卡片之间可比较。
 * 纯函数：无状态、无 Android 依赖。
 */
object Mastery {

    const val SEPARATOR = "::"

    fun verbKey(voiceId: String, inflectionId: String, verbId: String): String =
        listOf(voiceId, inflectionId, verbId).joinToString(SEPARATOR)

    fun adjectiveKey(formId: String, adjectiveId: String): String =
        listOf(formId, adjectiveId).joinToString(SEPARATOR)

    /** 某活用形式下已掌握的不同词条数（不限语态：换过语态答对同样说明该形式会用）。 */
    fun verbCountForForm(keys: Collection<String>, inflectionId: String): Int =
        keys.mapNotNull(::verbParts)
            .filter { it[1] == inflectionId }
            .map { it[2] }
            .distinct()
            .size

    /** 某语态下已掌握的不同词条数（按辞书形统计，与语态讲解页的练习范围一致）。 */
    fun verbCountForVoice(keys: Collection<String>, voiceId: String): Int =
        keys.mapNotNull(::verbParts)
            .filter { it[0] == voiceId && it[1] == "dictionary" }
            .map { it[2] }
            .distinct()
            .size

    /** 形容词某形式下已掌握的不同词条数。 */
    fun adjectiveCountForForm(keys: Collection<String>, formId: String): Int =
        keys.mapNotNull(::adjectiveParts)
            .filter { it[0] == formId }
            .map { it[1] }
            .distinct()
            .size

    /** 模块整体掌握度：各卡片进度的等权平均，百分比取整（仅作概览，不参与调度）。 */
    fun modulePercent(fractions: List<Float>): Int {
        if (fractions.isEmpty()) {
            return 0
        }
        val average = fractions.sum() / fractions.size
        return (average * 100).roundToInt()
    }

    private fun verbParts(key: String): List<String>? {
        val parts = key.split(SEPARATOR)
        return if (parts.size == 3) parts else null
    }

    private fun adjectiveParts(key: String): List<String>? {
        val parts = key.split(SEPARATOR)
        return if (parts.size == 2) parts else null
    }
}
