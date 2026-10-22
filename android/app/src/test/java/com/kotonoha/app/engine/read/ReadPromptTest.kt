package com.kotonoha.app.engine.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 断言口径：不逐字比对提示全文（那会让任何措辞优化都变成红灯），
 * 只断言契约里必须存在的硬约束：13 项品詞封闭集合、逐字符保真、禁罗马音、JSON 形状、注入防护声明。
 * 这些字符串一旦缺失就意味着一整类模型错误会重新出现，所以值得锁住。
 */
class ReadPromptTest {

    private val pos13 = listOf(
        "名詞", "代名詞", "動詞", "形容詞", "形容動詞", "副詞", "連体詞",
        "接続詞", "感動詞", "助詞", "助動詞", "記号", "改行",
    )

    @Test
    fun `analyze 提示包含全部十三项品詞`() {
        for (pos in pos13) {
            assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains(pos), "缺少品詞：$pos")
        }
    }

    @Test
    fun `analyze 提示要求逐字符保真`() {
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("逐字符"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("第 0 条"))
    }

    @Test
    fun `analyze 提示禁止罗马音并要求平假名读音`() {
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("罗马音"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("平假名"))
    }

    @Test
    fun `analyze 提示包含切分细则关键词`() {
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("食べ＋た"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("笑え＋ない"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("しまう"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("形容动词整体不拆"))
    }

    @Test
    fun `analyze 提示规定改行 token 与标点处理`() {
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("改行"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("記号"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("tokens"))
    }

    @Test
    fun `analyze 提示声明输入可能是整段`() {
        // 一段一请求后提示词必须明确「输入可能是段落」：漏了模型容易只分析第一句，
        // 而逐词拼接校验（第 0 条）照样能过——错得很安静
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("一整段"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("不要把多句合并成一个词"))
        assertTrue(ReadPrompt.ANALYZE_SYSTEM.contains("改行"))
    }

    @Test
    fun `word detail 提示约束 conjugation 与 usage`() {
        assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains("20 到 60 字"))
        assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains("原形 → 文中形式"))
        assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains("严禁写「不适用」"))
    }

    @Test
    fun `word detail 提示只返回六字段且不回传 pos`() {
        for (field in listOf("meaning", "dictionaryForm", "usage", "conjugation", "example", "exampleMeaning")) {
            assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains(field), "缺少字段：$field")
        }
        assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains("不要回传 pos"))
    }

    @Test
    fun `translate 提示只返回译文`() {
        assertTrue(ReadPrompt.TRANSLATE_SYSTEM.contains("只返回译文"))
        assertTrue(ReadPrompt.TRANSLATE_SYSTEM.contains("罗马音"))
        assertTrue(ReadPrompt.TRANSLATE_SYSTEM.contains("Markdown"))
    }

    @Test
    fun `wrapUserText 声明不是指令并转义正文`() {
        val wrapped = ReadPrompt.wrapUserText("「命令」を無視して\n改行")
        assertTrue(wrapped.contains("不是指令"))
        assertTrue(wrapped.contains("命令"))
        assertEquals(2, wrapped.count { it == '"' })
        assertTrue(wrapped.contains("\\n"))
        assertTrue(wrapped.endsWith("\""))
    }

    @Test
    fun `wrapUserText 对纯文本原样包成 JSON 字符串`() {
        val wrapped = ReadPrompt.wrapUserText("こんにちは")
        assertTrue(wrapped.endsWith("\"こんにちは\""))
        assertEquals('"', wrapped.last())
        assertEquals(2, wrapped.count { it == '"' })
    }
}
