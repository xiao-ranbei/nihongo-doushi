package com.nihongodoushi.app.engine.mastery

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 掌握度口径测试（三层改造）：
 * - 未提示答对才计入（写入侧由练习状态保证，这里测聚合纯函数）；
 * - 同一词条对同一形式去重；
 * - 语态卡按辞书形统计；
 * - 模块环 = 各卡片进度的等权平均。
 */
class MasteryTest {

    private fun verbKeys(vararg triples: Triple<String, String, String>): List<String> =
        triples.map { Mastery.verbKey(it.first, it.second, it.third) }

    @Test
    fun `动词键为三段式且可按形式聚合`() {
        val keys = verbKeys(
            Triple("base", "te", "kaku"),
            Triple("base", "te", "kiku"),
            Triple("potential", "dictionary", "kaku"), // 同词条换了语态：计入て形以外
            Triple("base", "te", "kaku"),              // 重复：去重
        )
        assertEquals(2, Mastery.verbCountForForm(keys, "te"))
        assertEquals(0, Mastery.verbCountForForm(keys, "past"))
        assertEquals(1, Mastery.verbCountForVoice(keys, "potential"))
        assertEquals(0, Mastery.verbCountForVoice(keys, "base"), "语态卡只统计辞书形")
    }

    @Test
    fun `畸形键不计入聚合`() {
        val keys = listOf("broken", "", Mastery.verbKey("base", "te", "kaku") + "::extra")
        assertEquals(0, Mastery.verbCountForForm(keys, "te"))
    }

    @Test
    fun `形容词键为两段式且可按形式聚合`() {
        val keys = listOf(
            Mastery.adjectiveKey("past", "takai"),
            Mastery.adjectiveKey("past", "shizuka"),
            Mastery.adjectiveKey("past", "takai"),
            Mastery.adjectiveKey("te", "takai"),
        )
        assertEquals(2, Mastery.adjectiveCountForForm(keys, "past"))
        assertEquals(1, Mastery.adjectiveCountForForm(keys, "te"))
        assertEquals(0, Mastery.adjectiveCountForForm(keys, "present"))
    }

    @Test
    fun `模块环取各卡片进度的等权平均`() {
        assertEquals(0, Mastery.modulePercent(emptyList()))
        assertEquals(50, Mastery.modulePercent(listOf(0.5f, 0.5f)))
        assertEquals(33, Mastery.modulePercent(listOf(1f, 0f, 0f)))
    }
}
