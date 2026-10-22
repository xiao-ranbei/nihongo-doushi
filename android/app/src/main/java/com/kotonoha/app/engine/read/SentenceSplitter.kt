package com.kotonoha.app.engine.read

/**
 * 本地切句（纯逻辑模块：禁止 import Android/androidx，禁 IO、禁随机）。
 *
 * 规则（spec §14 领域层清单 / F-04）：
 * - 句末标点 。！？ 断句，连续 。！？ 算同一个边界（「本当！？」只切一次）。
 * - 括号配对内的 。！？ 不断句：「」（）、『』【】〈〉《》〔〕 内一律视作句内标点。
 * - 断句后紧随的闭合符号并入前句（「行く。」这类整句连引号一起保留）。
 * - 空行分段，paragraphIndex 从 0 起按段递增；单换行不切句（粘贴文本里的硬换行仍属同一句）。
 * - 超过 500 字符的句子在标点/空白处硬切，找不到可切点才在 500 处强切，绝不静默截断；
 *   硬切出来的片段 hardCut = true，UI 据此提示「长句已分段」，不把残段伪装成完整句。
 * - 纯空白输入返回空列表；单字符句照常产出。
 * - [splitParagraphs] 是并行的段落入口：同样按空行分段、同样 500 字符硬切，但段内不再切句。
 *
 * 偏移口径：ReadSentence.start/end 是原文半开区间，_text_ 已去掉首尾空白，但仍满足
 * text.substring(start, end) == sentence.text，页面对不上时可用它回查。
 */
object SentenceSplitter {

    // 与 net/Analyzer 的请求体硬上限对齐：切得出来的句子必须解析得了，
    // 否则 501–800 字的句子会切出来、显示出来、点解析必然 BadRequest，且失败不写缓存、重试永远失败。
    // 公开是为了让 [chunkParagraphs] 的默认上限与这里同源：区间合并是第二个要遵守这条上限的地方，
    // 另写一个 500 就是第二个出处，改一处漏一处（行为未变，只放宽了可见性）。
    const val MAX_SENTENCE_LENGTH = 500

    /** 硬切优先落点：读起来最不突兀的四个标点/空白 */
    private const val SOFT_ANCHORS = "、，,　 "

    private const val SENTENCE_END = "。！？"

    private val BRACKETS: Map<Char, Char> = mapOf(
        '「' to '」',
        '『' to '』',
        '（' to '）',
        '【' to '】',
        '〈' to '〉',
        '《' to '》',
        '〔' to '〕',
        '(' to ')',
        '［' to '］',
    )

    private val CLOSERS: Set<Char> = BRACKETS.values.toSet()

    fun split(text: String): List<ReadSentence> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<ReadSentence>()
        var paragraphIndex = 0
        for (block in paragraphBlocks(text)) {
            for (range in splitSentences(text, block.first, block.second)) {
                emitWithHardCut(text, range.first, range.second, paragraphIndex, result) { unit, start, end, index, cut ->
                    ReadSentence(unit, start, end, index, cut)
                }
            }
            paragraphIndex++
        }
        return result
    }

    /**
     * 按段落切请求单元：一段一个请求，段内**不再**按 。！？ 断句
     * （用户 2026-10：几个词或几段话连起来一起分析，不要按句子分开显示）。
     *
     * 段落判定与 [split] 共用 [paragraphBlocks]（整行空白才算段界，段内保留换行），
     * 因为模型要求把段内换行输出成 pos="改行" 的 token；先按句子切开会让换行无处落地。
     *
     * 超过 500 字符的段落仍走 [emitWithHardCut] 的硬切（net/Analyzer 的请求体硬上限不变），
     * 每一块都是独立请求单元且 hardCut = true，UI 据此提示这是被长度逼断的残段。
     */
    fun splitParagraphs(text: String): List<ReadParagraph> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<ReadParagraph>()
        var paragraphIndex = 0
        for (block in paragraphBlocks(text)) {
            emitWithHardCut(text, block.first, block.second, paragraphIndex, result) { unit, start, end, index, cut ->
                ReadParagraph(unit, start, end, index, cut)
            }
            paragraphIndex++
        }
        return result
    }

    /**
     * 把一段连续的段落按字数上限分批（区间合并分析用）。
     *
     * 为什么需要它：用户长按起点词、再点终点词，要求「把这两个词之间的段落一次解析完」。
     * 合并省的是网络往返，但 net/Analyzer 的 500 字硬上限没有放宽，所以合并后的文本仍必须
     * 按上限切开——这里按**段落边界**切，而不是段内硬切，因为切点落在段落边上，模型看到的
     * 每一批都是完整段落，拼回去时批与批之间本来就是段落边界，读起来不断裂。
     *
     * 单段自己就超上限时不让它去拖累邻居：独占一批（它多半是 [splitParagraphs] 硬切过的残段，
     * 已经接近上限，再塞进别的段只会一起被服务端拒掉）。空输入返回空列表。
     */
    fun chunkParagraphs(
        paragraphs: List<ReadParagraph>,
        limit: Int = MAX_SENTENCE_LENGTH,
    ): List<List<ReadParagraph>> {
        if (paragraphs.isEmpty()) return emptyList()
        val safeLimit = if (limit > 0) limit else MAX_SENTENCE_LENGTH
        val batches = ArrayList<List<ReadParagraph>>()
        var current = ArrayList<ReadParagraph>()
        var currentLength = 0
        for (paragraph in paragraphs) {
            // 段间用换行连接，长度也要把那一个换行算进去，否则批文本会悄悄比 limit 长一个字。
            val joinedLength =
                if (current.isEmpty()) paragraph.text.length
                else currentLength + 1 + paragraph.text.length
            if (current.isNotEmpty() && joinedLength > safeLimit) {
                batches.add(current)
                current = ArrayList()
                currentLength = 0
                current.add(paragraph)
                currentLength = paragraph.text.length
            } else {
                current.add(paragraph)
                currentLength = joinedLength
            }
            if (current.size == 1 && paragraph.text.length > safeLimit) {
                batches.add(current)
                current = ArrayList()
                currentLength = 0
            }
        }
        if (current.isNotEmpty()) batches.add(current)
        return batches
    }

    /**
     * 一批段落拼成一次请求的正文：段间换行连接，段内原有换行保留。
     * 批次的长度判定（[chunkParagraphs]）与真正发出去的文本必须用同一个拼法，
     * 所以拼接只在这里实现一次，调用方不要再自己 join 一份。
     */
    fun mergedText(batch: List<ReadParagraph>): String = batch.joinToString("\n") { it.text }

    /**
     * 按空行切段：返回每段的 [start, endExclusive)。
     * 段内保留原有换行，因为模型要求把换行输出成 pos="改行" 的 token；
     * 只有整行都是空白才算分段边界，避免把行内空格当成段落。
     */
    private fun paragraphBlocks(text: String): List<Pair<Int, Int>> {
        val blocks = ArrayList<Pair<Int, Int>>()
        var blockStart = -1
        var blockEnd = -1
        var lineStart = 0
        var i = 0
        while (true) {
            val atEnd = i >= text.length
            if (atEnd || text[i] == '\n') {
                val lineEnd = i
                if (isBlankRange(text, lineStart, lineEnd)) {
                    if (blockStart >= 0) {
                        blocks.add(blockStart to blockEnd)
                        blockStart = -1
                    }
                } else {
                    if (blockStart < 0) blockStart = lineStart
                    blockEnd = lineEnd
                }
                if (atEnd) break
                i++
                lineStart = i
            } else {
                i++
            }
        }
        if (blockStart >= 0) blocks.add(blockStart to blockEnd)
        return blocks
    }

    private fun isBlankRange(text: String, from: Int, to: Int): Boolean {
        for (index in from until to) {
            if (!text[index].isWhitespace()) return false
        }
        return true
    }

    /** 在 [from, to) 内按句末标点切分，返回绝对偏移区间 */
    private fun splitSentences(text: String, from: Int, to: Int): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        val expectedClosers = ArrayList<Char>()
        var sentenceStart = from
        var boundaryPending = false
        var i = from
        while (i < to) {
            val c = text[i]
            val expected = expectedClosers.lastOrNull()
            when {
                expected != null && c == expected -> {
                    expectedClosers.removeAt(expectedClosers.size - 1)
                    i++
                }
                BRACKETS.containsKey(c) -> {
                    expectedClosers.add(BRACKETS.getValue(c))
                    i++
                }
                // 括号内的一切字符都不参与断句，包括句末标点
                expected != null -> i++
                c in SENTENCE_END -> {
                    i++
                    boundaryPending = true
                }
                else -> i++
            }
            if (boundaryPending && expectedClosers.isEmpty()) {
                var boundary = i
                while (boundary < to && (text[boundary] in CLOSERS || text[boundary] in SENTENCE_END)) {
                    boundary++
                }
                result.add(sentenceStart to boundary)
                sentenceStart = boundary
                i = boundary
                boundaryPending = false
            }
        }
        if (sentenceStart < to) result.add(sentenceStart to to)
        return result
    }

    /**
     * 把 [from, to) 按 [MAX_SENTENCE_LENGTH] 上限切块，逐块交给 [create] 生产切分产物。
     *
     * 句子与段落两个入口共用这一份硬切逻辑：上限与「回退到读点」的落点只该有一个出处，
     * 否则单测锁住的硬切行为会在两条路径上慢慢分叉。
     */
    private fun <T> emitWithHardCut(
        text: String,
        from: Int,
        to: Int,
        paragraphIndex: Int,
        out: MutableList<T>,
        create: (String, Int, Int, Int, Boolean) -> T,
    ) {
        var cursor = from
        while (cursor < to) {
            var limit = minOf(cursor + MAX_SENTENCE_LENGTH, to)
            // limit < to 就说明本片段是被长度逼断的，不是自然句界；后面即使回退到读点切片，
            // 切出来的仍然是原句的残段，UI 必须据此提示「长句已分段」，不能伪装成完整句。
            val hardCut = limit < to
            if (limit < to) {
                val floor = cursor + MAX_SENTENCE_LENGTH / 2
                var index = limit - 1
                var cut = -1
                while (index >= floor) {
                    if (text[index] in SOFT_ANCHORS) {
                        cut = index + 1
                        break
                    }
                    index--
                }
                if (cut > cursor) limit = cut
            }
            val start = trimStart(text, cursor, limit)
            val end = trimEnd(text, start, limit)
            if (end > start) {
                out.add(create(text.substring(start, end), start, end, paragraphIndex, hardCut))
            }
            cursor = limit
        }
    }

    private fun trimStart(text: String, from: Int, to: Int): Int {
        var index = from
        while (index < to && text[index].isWhitespace()) index++
        return index
    }

    private fun trimEnd(text: String, from: Int, to: Int): Int {
        var index = to
        while (index > from && text[index - 1].isWhitespace()) index--
        return index
    }
}
