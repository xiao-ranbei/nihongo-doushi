package com.kotonoha.app.engine.read

import kotlinx.serialization.json.Json

/**
 * analyze 响应的抽取管线（纯逻辑模块：禁止 import Android/androidx、禁 IO、禁随机）。
 *
 * 六条（spec §6.3）在 [parse] 里按顺序执行：
 * 1. 剥掉 ``` 围栏行——模型常自作主张加 json 代码块。
 * 2. 先按原样反序列化，失败再取第一个 { 到最后一个 } 的子串——模型常在 JSON 前后加解说。
 * 3. 两次都失败返回 [ParseOutcome.Failed]，调用方据此不写缓存。
 * 4. tokens 为空同样视同失败：空卡片缓存 500 条会把真正的失败永久固化。
 * 5. 单个 token 缺 pos/furigana 降级为空串，不整体失败（缺一个读音不值得让整句不能读）。
 * 6. 重建校验：逐词拼接必须与原句逐字符一致；不一致时先规范化再比一次，
 *    仍不一致才标 aligned=false。逐词错位会让读音挂到错的汉字上，这是本功能唯一会教错人的故障面。
 */
object AnalysisParser {

    private val json = Json { ignoreUnknownKeys = true }

    private const val ALIGNMENT_NOTE_LIMIT = 60

    /** 命中失败时回传的原始片段上限：够看清模型干什么，又不会把整段提示回显给用户 */
    private const val RAW_SNIPPET_LIMIT = 200

    fun parse(content: String, sentence: String): ParseOutcome {
        val unfenced = stripCodeFence(content)
        val response = decode(unfenced) ?: decode(compactJson(unfenced))
        if (response == null) {
            return ParseOutcome.Failed(rawSnippet(content), "模型返回不是可解析的 JSON")
        }
        if (response.tokens.isEmpty()) {
            return ParseOutcome.Failed(rawSnippet(content), "模型返回的 tokens 为空")
        }
        val tokens = response.tokens.map { token ->
            AnalyzeToken(word = token.word, pos = token.pos, furigana = token.furigana)
        }
        val rebuilt = tokens.joinToString("") { it.word }
        if (rebuilt == sentence) {
            return ParseOutcome.Ok(
                SentenceAnalysis(sentence = sentence, tokens = tokens, aligned = true, alignmentNote = ""),
            )
        }
        if (normalize(rebuilt) == normalize(sentence)) {
            return ParseOutcome.Ok(
                SentenceAnalysis(sentence = sentence, tokens = tokens, aligned = true, alignmentNote = ""),
            )
        }
        val note = describeDifference(sentence, rebuilt)
        return ParseOutcome.Ok(
            SentenceAnalysis(
                sentence = sentence,
                tokens = tokens,
                aligned = false,
                alignmentNote = note,
            ),
        )
    }

    /** 剥围栏：只在首尾确实是 ``` 行时动手，正文里出现的反引号（代码/引用）原样保留 */
    private fun stripCodeFence(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        var lines = trimmed.lines()
        lines = lines.drop(1)
        while (lines.isNotEmpty() && lines.last().trim().isEmpty()) lines = lines.dropLast(1)
        if (lines.isNotEmpty() && lines.last().trim().startsWith("```")) lines = lines.dropLast(1)
        return lines.joinToString("\n").trim()
    }

    /** 取第一个 { 到最后一个 } 的子串；没有花括号时返回空串让反序列化直接失败 */
    private fun compactJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return ""
        return content.substring(start, end + 1)
    }

    private fun decode(content: String): AnalyzeResponse? {
        if (content.isBlank()) return null
        return runCatching { json.decodeFromString<AnalyzeResponse>(content) }.getOrNull()
    }

    /** 规范化后重比：换行口径差异与零宽字符是粘贴/模型两侧最常见的伪差异，不该判成错位 */
    private fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c == '\r' && index + 1 < text.length && text[index + 1] == '\n' -> {
                    builder.append('\n')
                    index += 2
                }
                c == '\u200b' || c == '\u200c' || c == '\ufeff' -> index++
                else -> {
                    builder.append(c)
                    index++
                }
            }
        }
        return builder.toString()
    }

    /**
     * 最小差异提取：只做「逐字符对齐后多出/缺少了什么」的定位，不做完整 diff。
     * 目的不是复现编辑距离，而是让用户和排查者看到具体是哪个字丢了，
     * 因此优先报原句有而重建缺的字符，其次报重建多出来的字符。
     */
    private fun describeDifference(original: String, rebuilt: String): String {
        val originalChars = normalize(original)
        val rebuiltChars = normalize(rebuilt)
        val missing = StringBuilder()
        val extra = StringBuilder()
        var i = 0
        var j = 0
        while (i < originalChars.length || j < rebuiltChars.length) {
            if (i < originalChars.length && j < rebuiltChars.length && originalChars[i] == rebuiltChars[j]) {
                i++
                j++
            } else if (i < originalChars.length && (j >= rebuiltChars.length || !rebuiltChars.contains(originalChars[i]))) {
                missing.append(originalChars[i])
                i++
            } else if (j < rebuiltChars.length) {
                extra.append(rebuiltChars[j])
                j++
            } else {
                missing.append(originalChars[i])
                i++
            }
        }
        val head = when {
            missing.isNotEmpty() -> "缺失 " + missing.length + " 个字符：" + quoteEach(missing.toString())
            extra.isNotEmpty() -> "多出 " + extra.length + " 个字符：" + quoteEach(extra.toString())
            else -> "顺序不一致"
        }
        return if (head.length <= ALIGNMENT_NOTE_LIMIT) head else head.substring(0, ALIGNMENT_NOTE_LIMIT)
    }

    private fun quoteEach(text: String): String = text.map { "『" + it + "』" }.joinToString("")

    private fun rawSnippet(content: String): String {
        val compact = content.trim()
        return if (compact.length <= RAW_SNIPPET_LIMIT) compact else compact.substring(0, RAW_SNIPPET_LIMIT)
    }
}
