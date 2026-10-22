package com.kotonoha.app.engine.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 断言口径：只看分组结果的「词序列」与组数，不关心实现如何切。
 * 每条断言对应一条禁则：行首禁则并入前组、开括号不留组尾、改行独立成组、
 * 全記号句不死循环（用返回值而非超时判断——死循环会让测试直接卡住，所以仍要跑通）。
 */
class TokenGroupingTest {

    private fun token(word: String, pos: String = "名詞") = AnalyzeToken(word = word, pos = pos)

    @Test
    fun `普通词各自成组`() {
        val groups = TokenGrouping.group(listOf(token("私"), token("は"), token("学生"), token("です")))
        assertEquals(4, groups.size)
        assertEquals(listOf("私", "は", "学生", "です"), groups.map { it.single().word })
    }

    @Test
    fun `句号并入前一组不落行首`() {
        val groups = TokenGrouping.group(listOf(token("行く", "動詞"), token("。", "記号")))
        assertEquals(1, groups.size)
        assertEquals(listOf("行く", "。"), groups[0].map { it.word })
    }

    @Test
    fun `小假名并入前一组`() {
        val groups = TokenGrouping.group(listOf(token("き"), token("ゃ"), token("く")))
        assertEquals(2, groups.size)
        assertEquals(listOf("き", "ゃ"), groups[0].map { it.word })
    }

    @Test
    fun `开括号不留组尾而是并入后一组`() {
        val groups = TokenGrouping.group(listOf(token("「", "記号"), token("行く", "動詞"), token("」", "記号")))
        assertEquals(1, groups.size)
        assertEquals(listOf("「", "行く", "」"), groups[0].map { it.word })
    }

    @Test
    fun `改行 token 独立成组且不与前后合并`() {
        val groups = TokenGrouping.group(listOf(token("あ"), token("\n", "改行"), token("い")))
        assertEquals(3, groups.size)
        assertEquals("改行", groups[1].single().pos)
        assertEquals("\n", groups[1].single().word)
    }

    @Test
    fun `连续行首禁则符号全部并入同一前组`() {
        val groups = TokenGrouping.group(listOf(token("本当", "名詞"), token("！", "記号"), token("？", "記号")))
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
    }

    @Test
    fun `全記号输入正常返回不卡死`() {
        val tokens = List(20) { token("。", "記号") }
        val groups = TokenGrouping.group(tokens)
        assertEquals(1, groups.size)
        assertEquals(20, groups[0].size)
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(TokenGrouping.group(emptyList()).isEmpty())
    }

    @Test
    fun `分组不增加也不丢失 token`() {
        val tokens = listOf(token("私"), token("は", "助詞"), token("「"), token("走る", "動詞"), token("」。", "記号"))
        val flat = TokenGrouping.group(tokens).flatten()
        assertEquals(tokens, flat)
    }
}
