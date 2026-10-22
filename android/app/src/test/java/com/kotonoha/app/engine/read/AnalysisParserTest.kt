package com.kotonoha.app.engine.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 断言口径：只从 [AnalysisParser.parse] 的返回类型与字段判断，不看异常。
 * 每个失败分支都要求 rawSnippet 保留原文痕迹（否则 UI 的「查看原始返回」是空的），
 * 每个成功分支都要求 aligned 与 alignmentNote 一起自洽。
 */
class AnalysisParserTest {

    private val json = """{"tokens":[{"word":"食べ","pos":"動詞","furigana":"たべ"},{"word":"た","pos":"助動詞","furigana":"た"}]}"""

    @Test
    fun `纯 JSON 直接解析成功`() {
        val outcome = AnalysisParser.parse(json, "食べた")
        assertTrue(outcome is ParseOutcome.Ok)
        val analysis = (outcome as ParseOutcome.Ok).analysis
        assertEquals(2, analysis.tokens.size)
        assertEquals("食べ", analysis.tokens[0].word)
        assertEquals("動詞", analysis.tokens[0].pos)
        assertEquals("たべ", analysis.tokens[0].furigana)
        assertTrue(analysis.aligned)
        assertEquals("", analysis.alignmentNote)
    }

    @Test
    fun `剥离 markdown 代码围栏`() {
        val fenced = "\u0060\u0060\u0060json\n" + json + "\n\u0060\u0060\u0060"
        val outcome = AnalysisParser.parse(fenced, "食べた")
        assertTrue(outcome is ParseOutcome.Ok)
        assertEquals(2, (outcome as ParseOutcome.Ok).analysis.tokens.size)
    }

    @Test
    fun `剥离 JSON 前后的解说文字`() {
        val noisy = "分析结果如下：" + json + "\n以上。"
        val outcome = AnalysisParser.parse(noisy, "食べた")
        assertTrue(outcome is ParseOutcome.Ok)
        assertEquals("食べた", (outcome as ParseOutcome.Ok).analysis.sentence)
    }

    @Test
    fun `忽略未知字段`() {
        val extra = """{"model":"x","usage":{"total":1},"tokens":[{"word":"食べ","pos":"動詞","furigana":"たべ"},{"word":"た","pos":"助動詞","furigana":"た"}]}"""
        val outcome = AnalysisParser.parse(extra, "食べた")
        assertTrue(outcome is ParseOutcome.Ok)
    }

    @Test
    fun `完全不是 JSON 时返回失败并保留原始片段`() {
        val outcome = AnalysisParser.parse("对不起，我无法处理这个请求。", "食べた")
        assertTrue(outcome is ParseOutcome.Failed)
        val failed = outcome as ParseOutcome.Failed
        assertTrue(failed.rawSnippet.contains("无法处理"))
        assertTrue(failed.reason.isNotBlank())
    }

    @Test
    fun `tokens 为空视同失败`() {
        val outcome = AnalysisParser.parse("""{"tokens":[]}""", "食べた")
        assertTrue(outcome is ParseOutcome.Failed)
        val failed = outcome as ParseOutcome.Failed
        assertTrue(failed.rawSnippet.contains("tokens"))
        assertTrue(failed.reason.contains("空"))
    }

    @Test
    fun `缺 pos 与 furigana 降级为空串而不失败`() {
        val outcome = AnalysisParser.parse("""{"tokens":[{"word":"太郎"}]}""", "太郎")
        assertTrue(outcome is ParseOutcome.Ok)
        val token = (outcome as ParseOutcome.Ok).analysis.tokens.single()
        assertEquals("太郎", token.word)
        assertEquals("", token.pos)
        assertEquals("", token.furigana)
    }

    @Test
    fun `逐词拼接与原句不一致时标 aligned 为 false 并指出缺失字符`() {
        val outcome = AnalysisParser.parse(json, "食べた。")
        assertTrue(outcome is ParseOutcome.Ok)
        val analysis = (outcome as ParseOutcome.Ok).analysis
        assertFalse(analysis.aligned)
        assertTrue(analysis.alignmentNote.contains("缺失"))
        assertTrue(analysis.alignmentNote.contains("。"))
        assertTrue(analysis.alignmentNote.length <= 60)
    }

    @Test
    fun `少了一个助词时指出缺失的具体字符`() {
        val missingWa = """{"tokens":[{"word":"私","pos":"名詞"},{"word":"学生","pos":"名詞"},{"word":"です","pos":"助動詞"}]}"""
        val outcome = AnalysisParser.parse(missingWa, "私は学生です")
        assertTrue(outcome is ParseOutcome.Ok)
        val analysis = (outcome as ParseOutcome.Ok).analysis
        assertTrue(!analysis.aligned)
        assertTrue(analysis.alignmentNote.contains("は"), analysis.alignmentNote)
        assertTrue(analysis.alignmentNote.startsWith("缺失 1 个字符"))
    }

    @Test
    fun `换行与零宽字符差异经规范化后仍判为对齐`() {
        val tokens = """{"tokens":[{"word":"あ"},{"word":"\r\n"},{"word":"い"}]}"""
        val outcome = AnalysisParser.parse(tokens, "あ\u200b\nい")
        assertTrue(outcome is ParseOutcome.Ok)
        val analysis = (outcome as ParseOutcome.Ok).analysis
        assertTrue(analysis.aligned)
        assertEquals("", analysis.alignmentNote)
    }

    @Test
    fun `多出字符时提示多出的内容`() {
        val more = """{"tokens":[{"word":"食べ","pos":"動詞"},{"word":"た","pos":"助動詞"},{"word":"。","pos":"記号"}]}"""
        val outcome = AnalysisParser.parse(more, "食べた")
        assertTrue(outcome is ParseOutcome.Ok)
        val analysis = (outcome as ParseOutcome.Ok).analysis
        assertFalse(analysis.aligned)
        assertTrue(analysis.alignmentNote.contains("多出"))
    }
}
