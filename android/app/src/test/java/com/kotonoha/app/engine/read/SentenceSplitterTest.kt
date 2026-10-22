package com.kotonoha.app.engine.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 断言口径：以「切出几个句子 + 每句原文 + paragraphIndex + 偏移可回查」为准，
 * 不比对内部实现细节。偏移一律用 text.substring(start, end) 与 text 反向验证，
 * 因为页面靠这组偏移取原文，偏移错了比切错句更难发现。
 */
class SentenceSplitterTest {

    @Test
    fun `句末标点切分出两句并可回查偏移`() {
        val text = "今日は晴れです。明日は雨です。"
        val sentences = SentenceSplitter.split(text)
        assertEquals(2, sentences.size)
        assertEquals("今日は晴れです。", sentences[0].text)
        assertEquals("明日は雨です。", sentences[1].text)
        for (sentence in sentences) {
            assertEquals(sentence.text, text.substring(sentence.start, sentence.end))
        }
    }

    @Test
    fun `纯空白输入返回空列表`() {
        assertEquals(emptyList(), SentenceSplitter.split(""))
        assertEquals(emptyList(), SentenceSplitter.split("   "))
        assertEquals(emptyList(), SentenceSplitter.split("\n\n  \n"))
        assertEquals(emptyList(), SentenceSplitter.split("\u3000\u3000"))
    }

    @Test
    fun `连续句末标点只切一次`() {
        val sentences = SentenceSplitter.split("本当！？そうなの。")
        assertEquals(2, sentences.size)
        assertEquals("本当！？", sentences[0].text)
        assertEquals("そうなの。", sentences[1].text)
    }

    @Test
    fun `括号内的句末标点不断句`() {
        val text = "彼は「行く。」と言った。"
        val sentences = SentenceSplitter.split(text)
        assertEquals(1, sentences.size)
        assertEquals(text, sentences[0].text)
    }

    @Test
    fun `引用句内的句末标点不断句且闭合引号并入前句`() {
        // 括号内不切：整段引用连句末标点算同一句，闭合后紧跟的「と言った。」也仍属该句
        val sentences = SentenceSplitter.split("「行く。」と言った。次へ。")
        assertEquals(2, sentences.size)
        assertEquals("「行く。」と言った。", sentences[0].text)
        assertEquals("次へ。", sentences[1].text)
    }

    @Test
    fun `各种括号配对内的句末标点都不断句`() {
        val pairs = listOf("（）", "【】", "『』", "〈〉", "《》")
        for (pair in pairs) {
            val open = pair[0]
            val close = pair[1]
            val text = "これは" + open + "本当？" + close + "です。"
            val sentences = SentenceSplitter.split(text)
            assertEquals(1, sentences.size, "括号 " + pair + " 内的问号不该断句")
            assertEquals(text, sentences[0].text)
        }
    }

    @Test
    fun `闭引号紧跟句末标点时并入前句`() {
        val sentences = SentenceSplitter.split("「行く」。次へ。")
        assertEquals(2, sentences.size)
        assertEquals("「行く」。", sentences[0].text)
        assertEquals("次へ。", sentences[1].text)
    }

    @Test
    fun `空行分段且段落序号递增`() {
        val sentences = SentenceSplitter.split("一句目。\n\n二句目。\n\n三句目。")
        assertEquals(3, sentences.size)
        assertEquals(0, sentences[0].paragraphIndex)
        assertEquals(1, sentences[1].paragraphIndex)
        assertEquals(2, sentences[2].paragraphIndex)
    }

    @Test
    fun `单换行不当作分段`() {
        val sentences = SentenceSplitter.split("長い文の\n途中です。")
        assertEquals(1, sentences.size)
        assertEquals(0, sentences[0].paragraphIndex)
    }

    @Test
    fun `单字符句正常产出`() {
        val sentences = SentenceSplitter.split("あ")
        assertEquals(1, sentences.size)
        assertEquals("あ", sentences[0].text)
    }

    @Test
    fun `超长句在五百字符处硬切且不丢内容`() {
        val text = "あ".repeat(1000)
        val sentences = SentenceSplitter.split(text)
        assertEquals(2, sentences.size)
        // 上限与 net/Analyzer 的请求体硬上限同为 500：切得出来的句子必须解析得了
        assertEquals(500, sentences[0].text.length)
        assertEquals(500, sentences[1].text.length)
        assertEquals(text, sentences.joinToString("") { it.text })
        // 前一片段被长度逼断，末片段落在原句末尾（自然句界），两者必须可区分
        assertTrue(sentences[0].hardCut)
        assertFalse(sentences[1].hardCut)
    }

    @Test
    fun `硬切优先落在读点或空白处`() {
        val text = "あ".repeat(400) + "、" + "い".repeat(300)
        val sentences = SentenceSplitter.split(text)
        assertEquals(2, sentences.size)
        assertTrue(sentences[0].text.endsWith("、"))
        assertEquals(401, sentences[0].text.length)
        assertEquals(text, sentences.joinToString("") { it.text })
        // 回退到读点切片仍是硬切：切点是标点选的，句界依然是长度逼出来的
        assertTrue(sentences[0].hardCut)
        assertFalse(sentences[1].hardCut)
    }

    @Test
    fun `自然句不带硬切标记`() {
        val sentences = SentenceSplitter.split("今日は晴れです。明日は雨です。")
        assertEquals(2, sentences.size)
        assertTrue(sentences.none { it.hardCut })
    }

    // ---------- splitParagraphs：一段一个请求单元 ----------

    @Test
    fun `段落切分不按句末标点拆开`() {
        val text = "今日は晴れです。明日は雨です。明後日は雪です。"
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(1, paragraphs.size)
        assertEquals(text, paragraphs[0].text)
        assertEquals(0, paragraphs[0].paragraphIndex)
        assertFalse(paragraphs[0].hardCut)
        // 偏移口径与句子切分一致：页面靠它回查原文与收藏取文
        assertEquals(text, text.substring(paragraphs[0].start, paragraphs[0].end))
    }

    @Test
    fun `空行分出多段且段内保留换行`() {
        val first = "今日は晴れです。明日は雨です。"
        val second = "次は雪です。\nとても寒いです。"
        val text = first + "\n\n" + second
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(2, paragraphs.size)
        assertEquals(first, paragraphs[0].text)
        // 段内的单个换行不切段：模型要把它输出成 pos="改行" 的 token
        assertEquals(second, paragraphs[1].text)
        assertEquals(listOf(0, 1), paragraphs.map { it.paragraphIndex })
        paragraphs.forEach { assertEquals(it.text, text.substring(it.start, it.end)) }
    }

    @Test
    fun `段落切分忽略纯空白输入`() {
        assertTrue(SentenceSplitter.splitParagraphs("").isEmpty())
        assertTrue(SentenceSplitter.splitParagraphs(" \n\n\t ").isEmpty())
    }

    @Test
    fun `括号内的句末标点不影响段落数`() {
        val text = "「今日は晴れです。」と彼は言った。\n\nもう一文。"
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(2, paragraphs.size)
        assertEquals("「今日は晴れです。」と彼は言った。", paragraphs[0].text)
        assertFalse(paragraphs.any { it.hardCut })
    }

    @Test
    fun `超长段落被硬切成多块且只标残段`() {
        val text = "あ".repeat(1200)
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(3, paragraphs.size)
        assertEquals(listOf(500, 500, 200), paragraphs.map { it.text.length })
        assertEquals(text, paragraphs.joinToString("") { it.text })
        // 前两块是长度逼出来的残段，末块落在段落末尾（自然段界）
        assertEquals(listOf(true, true, false), paragraphs.map { it.hardCut })
        assertEquals(listOf(0, 0, 0), paragraphs.map { it.paragraphIndex })
    }

    // ---------- chunkParagraphs（区间合并分析按段分批） ----------

    /** 造一段给定长度的段落，偏移随便给：分批只看 text 长度，偏移不参与判断 */
    private fun paragraph(text: String, index: Int = 0) =
        ReadParagraph(text = text, start = 0, end = text.length, paragraphIndex = index)

    @Test
    fun `空区间不分批`() {
        assertEquals(emptyList(), SentenceSplitter.chunkParagraphs(emptyList()))
    }

    @Test
    fun `总长在上限内时全部合成一批`() {
        val paragraphs = listOf(paragraph("あ".repeat(200), 0), paragraph("い".repeat(200), 1))
        val batches = SentenceSplitter.chunkParagraphs(paragraphs)
        assertEquals(1, batches.size)
        assertEquals(paragraphs, batches[0])
        // 批文本用换行连接：段间那一个换行也要算进上限，否则发出去会悄悄超一个字
        assertEquals(401, SentenceSplitter.mergedText(batches[0]).length)
    }

    @Test
    fun `超过上限按段边界分批且每批都不超限`() {
        val paragraphs = listOf(
            paragraph("あ".repeat(300), 0),
            paragraph("い".repeat(300), 1),
            paragraph("う".repeat(300), 2),
        )
        val batches = SentenceSplitter.chunkParagraphs(paragraphs)
        assertEquals(3, batches.size)
        assertEquals(listOf(0, 1, 2), batches.map { it.single().paragraphIndex })
        for (batch in batches) {
            assertFalse(SentenceSplitter.mergedText(batch).length > SentenceSplitter.MAX_SENTENCE_LENGTH)
        }
    }

    @Test
    fun `单段自身超限时独占一批且不拖累邻居`() {
        val long = paragraph("い".repeat(700), 1)
        val paragraphs = listOf(paragraph("短い段です。", 0), long, paragraph("また短い段です。", 2))
        val batches = SentenceSplitter.chunkParagraphs(paragraphs)
        assertEquals(3, batches.size)
        assertEquals(long, batches[1].single())
        // 邻居各自成批，不会被那段超长段落拉进来一起被服务端拒掉
        assertEquals(0, batches[0].single().paragraphIndex)
        assertEquals(2, batches[2].single().paragraphIndex)
    }

    @Test
    fun `恰好等于上限时仍算一批`() {
        val paragraphs = listOf(paragraph("あ".repeat(250)), paragraph("い".repeat(249)))
        // 250 + 换行 1 + 249 = 500，正好卡在上限上
        val batches = SentenceSplitter.chunkParagraphs(paragraphs)
        assertEquals(1, batches.size)
        assertEquals(500, SentenceSplitter.mergedText(batches[0]).length)
    }

    @Test
    fun `多段中的超长段各自独立硬切`() {
        val long = "い".repeat(700)
        val text = "短い段です。\n\n" + long + "\n\nまた短い段です。"
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(4, paragraphs.size)
        assertEquals(listOf(0, 1, 1, 2), paragraphs.map { it.paragraphIndex })
        assertEquals(listOf(false, true, false, false), paragraphs.map { it.hardCut })
        assertEquals(paragraphs[1].text + paragraphs[2].text, long)
    }
}
