package com.kotonoha.app.ui.read

import com.kotonoha.app.engine.read.AnalysisCacheEntry
import com.kotonoha.app.engine.read.AnalyzeToken
import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ReadParagraph
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.engine.read.SentenceAnalysis
import com.kotonoha.app.engine.read.SentenceSplitter
import com.kotonoha.app.engine.read.WordDetail
import com.kotonoha.app.net.Analyzer
import com.kotonoha.app.net.AnalyzerError
import com.kotonoha.app.net.AnalyzerResult
import com.kotonoha.app.storage.Bookmark
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * ReadUiState 的纯逻辑单测。这一层不能碰 androidx，所以整个文件可以在纯 JVM 下跑，
 * 覆盖的点是「文案/计数口径」与「请求生命周期」——两者出错都不会崩溃，只会静静错下去，
 * 恰恰是最需要断言而不是靠肉眼验收的部分。
 */
class ReadUiStateTest {

    private val cfg = ModelConfig(baseUrl = "https://example.test", model = "m", apiKey = "k")

    // ---------- articleTitle ----------

    @Test
    fun `标题取首个非空句`() {
        val text = "今日は晴れです。明日は雨です。"
        assertEquals("今日は晴れです。", ReadUiLogic.articleTitle(text))
    }

    @Test
    fun `标题超过 20 字截断并加省略号`() {
        val long = "あ".repeat(30) + "。"
        val title = ReadUiLogic.articleTitle(long)
        assertEquals("あ".repeat(20) + "…", title)
    }

    @Test
    fun `标题空输入回退未命名文章`() {
        assertEquals("未命名文章", ReadUiLogic.articleTitle(""))
        assertEquals("未命名文章", ReadUiLogic.articleTitle("   \n\n  "))
    }

    // ---------- relativeTime ----------

    private val now = Instant.parse("2025-01-10T12:00:00Z")

    @Test
    fun `相对时间一分钟内是刚刚`() {
        assertEquals("刚刚", ReadUiLogic.relativeTime("2025-01-10T11:59:30Z", now))
    }

    @Test
    fun `相对时间分钟与小时`() {
        assertEquals("5 分钟前", ReadUiLogic.relativeTime("2025-01-10T11:55:00Z", now))
        assertEquals("3 小时前", ReadUiLogic.relativeTime("2025-01-10T09:00:00Z", now))
    }

    @Test
    fun `相对时间天与超过七天转日期`() {
        assertEquals("2 天前", ReadUiLogic.relativeTime("2025-01-08T12:00:00Z", now))
        val older = ReadUiLogic.relativeTime("2024-12-01T12:00:00Z", now)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(older), older)
    }

    @Test
    fun `相对时间非法串原样返回不抛`() {
        assertEquals("不是时间", ReadUiLogic.relativeTime("不是时间", now))
        assertEquals("", ReadUiLogic.relativeTime("", now))
    }

    @Test
    fun `相对时间缺时区后缀按本地时间解析`() {
        // 外部工具写进来的文章可能是 LocalDateTime 形态；原样贴出 2026-10-20T09:00:00 对读者等于乱码
        val local = java.time.LocalDateTime.parse("2025-01-10T11:55:00")
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        val shifted = local.plusSeconds(300)
        assertEquals("5 分钟前", ReadUiLogic.relativeTime("2025-01-10T11:55:00", shifted))
    }

    // ---------- charCount / canStart ----------

    @Test
    fun `字数按非空白字符计数`() {
        assertEquals(6, ReadUiLogic.charCount("あ い\nう\tえ\u3000お か"))
        assertEquals(0, ReadUiLogic.charCount(" \n\t\u3000"))
    }

    @Test
    fun `八字不可开始十字可开始`() {
        assertFalse(ReadUiLogic.canStart("あいうえおかきく"))
        assertTrue(ReadUiLogic.canStart("あいうえおかきくけこ"))
        // 空白不计数：带空格换行的 10 字实字仍然放行，8 字实字加空白仍然拦下
        assertFalse(ReadUiLogic.canStart("あ い う え お か き く"))
        assertTrue(ReadUiLogic.canStart("あ い う え お か き く け こ"))
    }

    // ---------- bookmarkIdFor / isBookmarked ----------

    @Test
    fun `收藏 id 与书签判定`() {
        val key = "abc123"
        assertEquals("sentence::abc123", ReadUiLogic.bookmarkIdFor(key))
        val hit = listOf(Bookmark(id = "sentence::abc123", type = "sentence", refId = key, title = "标题", createdAt = "2025-01-10T12:00:00Z"))
        val miss = listOf(Bookmark(id = "sentence::other", type = "sentence", refId = "other", title = "标题", createdAt = "2025-01-10T12:00:00Z"))
        assertTrue(ReadUiLogic.isBookmarked(hit, key))
        assertFalse(ReadUiLogic.isBookmarked(miss, key))
        assertFalse(ReadUiLogic.isBookmarked(emptyList(), key))
    }

    // ---------- ReadSentenceRequests ----------

    private class FakeAnalyzer(
        private val analyzeImpl: suspend (String) -> AnalyzerResult<SentenceAnalysis> =
            { sentence -> AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence)) },
        private val translateImpl: suspend (String) -> AnalyzerResult<String> =
            { AnalyzerResult.Ok("译文") },
        private val wordDetailImpl: suspend (String, String) -> AnalyzerResult<WordDetail> =
            { _, _ -> AnalyzerResult.Ok(WordDetail()) },
    ) : Analyzer {
        var analyzeCalls = 0
        var translateCalls = 0
        var wordDetailCalls = 0
        /** 按调用顺序记下被问到的词，用来断言预取的队列顺序与去重 */
        val wordDetailWords = mutableListOf<String>()

        override suspend fun analyze(sentence: String, cfg: ModelConfig): AnalyzerResult<SentenceAnalysis> {
            analyzeCalls += 1
            return analyzeImpl(sentence)
        }

        override suspend fun translate(sentence: String, cfg: ModelConfig): AnalyzerResult<String> {
            translateCalls += 1
            return translateImpl(sentence)
        }

        override suspend fun wordDetail(sentence: String, word: String, cfg: ModelConfig): AnalyzerResult<WordDetail> {
            wordDetailCalls += 1
            wordDetailWords += word
            return wordDetailImpl(sentence, word)
        }
    }

    private class Recorder {
        val states = mutableListOf<Pair<String, SentenceUiState>>()
        val parsed = mutableListOf<Pair<String, SentenceAnalysis>>()
    }

    private fun requestsFor(analyzer: Analyzer, recorder: Recorder): ReadSentenceRequests =
        ReadSentenceRequests(
            analyzer = analyzer,
            onState = { key, state -> recorder.states += key to state },
            onParsed = { key, analysis -> recorder.parsed += key to analysis },
            errorText = { error -> "E:" + error::class.simpleName },
        )

    @Test
    fun `成功解析先 Loading 后 Parsed 且 onParsed 只调一次`() = runBlocking {
        val analyzer = FakeAnalyzer()
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        val started = requests.start("k1", "今日は晴れです。", cfg, this)
        assertTrue(started)
        assertEquals(SentenceUiState.Loading, recorder.states.first().second)
        assertEquals("k1", recorder.states.first().first)

        delay(50)
        assertEquals(2, recorder.states.size)
        val parsedState = recorder.states[1].second
        assertTrue(parsedState is SentenceUiState.Parsed)
        assertEquals("译文", (parsedState as SentenceUiState.Parsed).analysis.translation)
        assertEquals(1, recorder.parsed.size)
        assertEquals("k1", recorder.parsed[0].first)
        assertEquals(1, analyzer.analyzeCalls)
        assertEquals(1, analyzer.translateCalls)
        assertFalse(requests.isRunning("k1"))
    }

    @Test
    fun `同 key 二次 start 返回 false 且只发一次请求`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(analyzeImpl = { sentence ->
            gate.await()
            AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
        })
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        assertTrue(requests.start("k1", "今日は晴れです。", cfg, this))
        assertEquals(0, analyzer.analyzeCalls)
        assertTrue(requests.isRunning("k1"))
        delay(20)
        assertEquals(1, analyzer.analyzeCalls)

        assertFalse(requests.start("k1", "今日は晴れです。", cfg, this))
        delay(20)
        assertEquals(1, analyzer.analyzeCalls)

        requests.cancel("k1")
        gate.complete(Unit)
        delay(20)
        assertFalse(requests.isRunning("k1"))
    }

    @Test
    fun `取消后不再回调 onParsed`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(analyzeImpl = { sentence ->
            gate.await()
            AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
        })
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        assertTrue(requests.start("k1", "今日は晴れです。", cfg, this))
        delay(20)
        requests.cancel("k1")
        gate.complete(Unit)
        delay(50)

        assertTrue(recorder.parsed.isEmpty())
        assertTrue(recorder.states.none { it.second is SentenceUiState.Parsed })
        // 取消不能被当成失败：不该出现 Failed
        assertTrue(recorder.states.none { it.second is SentenceUiState.Failed })
        assertFalse(requests.isRunning("k1"))
    }

    @Test
    fun `译文失败仍是 Parsed 但译文为空`() = runBlocking {
        val analyzer = FakeAnalyzer(
            translateImpl = { AnalyzerResult.Err(AnalyzerError.Timeout) },
        )
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        requests.start("k1", "今日は晴れです。", cfg, this)
        delay(50)

        val state = recorder.states.last().second
        assertTrue(state is SentenceUiState.Parsed)
        assertEquals("", (state as SentenceUiState.Parsed).analysis.translation)
        assertEquals(1, recorder.parsed.size)
        assertEquals(1, analyzer.translateCalls)
    }

    @Test
    fun `analyze 与 translate 并发发出而非串行`() = runBlocking {
        // analyze 卡在闸门上，translate 若必须等 analyze 返回才发，这里就永远读不到 true，
        // 这正是 AC-03 要求并发的原因：串行等于把用户等待时间相加。
        val gate = CompletableDeferred<Unit>()
        var translateStarted = false
        val analyzer = FakeAnalyzer(
            analyzeImpl = { sentence ->
                gate.await()
                AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
            },
            translateImpl = {
                translateStarted = true
                AnalyzerResult.Ok("译文")
            },
        )
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        requests.start("k1", "今日は晴れです。", cfg, this)
        delay(50)
        assertTrue(translateStarted)
        assertEquals(1, analyzer.translateCalls)
        assertTrue(recorder.parsed.isEmpty())

        gate.complete(Unit)
        delay(50)
        assertEquals(1, recorder.parsed.size)
        assertEquals("译文", recorder.parsed[0].second.translation)
    }

    @Test
    fun `解析失败时不等挂起的译文即给 Failed`() = runBlocking {
        // 译文挂死不返回、解析已失败：必须立刻 Failed，而不是等译文超时。
        // 若另一路 deferred 没被取消，coroutineScope 会一直等它，这条断言就会失败。
        val neverEnding = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(
            analyzeImpl = { AnalyzerResult.Err(AnalyzerError.Network("connect failed")) },
            translateImpl = {
                neverEnding.await()
                AnalyzerResult.Ok("译文")
            },
        )
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        requests.start("k1", "今日は晴れです。", cfg, this)
        withTimeout(2000) {
            while (recorder.states.isEmpty() || recorder.states.last().second == SentenceUiState.Loading) {
                delay(10)
            }
        }
        val failed = recorder.states.last().second
        assertTrue(failed is SentenceUiState.Failed)
        assertEquals("E:Network", (failed as SentenceUiState.Failed).message)
        assertTrue(recorder.parsed.isEmpty())
    }

    @Test
    fun `解析失败给 Failed 并走注入的错误文案`() = runBlocking {
        val analyzer = FakeAnalyzer(
            analyzeImpl = { AnalyzerResult.Err(AnalyzerError.Network("boom")) },
        )
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        requests.start("k1", "今日は晴れです。", cfg, this)
        delay(50)

        val state = recorder.states.last().second
        assertTrue(state is SentenceUiState.Failed)
        assertEquals("E:Network", (state as SentenceUiState.Failed).message)
        assertTrue(recorder.parsed.isEmpty())
        // 并发下译文请求总是会被发出，解析失败时它的结果必须被丢弃而不是补一个空译文上去
        assertEquals(1, analyzer.translateCalls)
        assertFalse(requests.isRunning("k1"))
    }

    // ---------- ReadWordDetailPrefetcher ----------

    private class DetailRecorder {
        val details = mutableListOf<Triple<String, String, WordDetail>>()
        val fails = mutableListOf<Pair<String, String>>()
    }

    private fun prefetcherFor(analyzer: Analyzer, recorder: DetailRecorder): ReadWordDetailPrefetcher =
        ReadWordDetailPrefetcher(
            analyzer = analyzer,
            onDetail = { sentence, word, detail -> recorder.details += Triple(sentence, word, detail) },
            onFail = { sentence, word -> recorder.fails += sentence to word },
        )

    /** 等 analyzer 被问到第 n 次：预取是 fire-and-forget，只能从副作用上判进度 */
    private suspend fun awaitWordDetailCalls(analyzer: FakeAnalyzer, n: Int) {
        withTimeout(2000) {
            while (analyzer.wordDetailCalls < n) delay(5)
        }
    }

    @Test
    fun `预取跳过 cached 里的词只对剩下的发请求`() = runBlocking {
        val analyzer = FakeAnalyzer()
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は", "晴れ"), cfg, setOf("今日"), this)
        awaitWordDetailCalls(analyzer, 2)

        assertEquals(listOf("は", "晴れ"), analyzer.wordDetailWords)
        assertEquals(2, recorder.details.size)
        assertEquals(Triple("今日は晴れです。", "は", WordDetail()), recorder.details[0])
    }

    @Test
    fun `同一个词重复 prefetch 不会发第二次`() = runBlocking {
        val analyzer = FakeAnalyzer()
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 2)
        // 第二批里带一个上一批已经发过的词：settled 生效，只补新词
        prefetcher.prefetch("今日は晴れです。", listOf("今日", "晴れ"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 3)
        delay(30)

        assertEquals(listOf("今日", "は", "晴れ"), analyzer.wordDetailWords)
        assertEquals(3, recorder.details.size)
    }

    @Test
    fun `claim 之后预取队列跳过该词`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(
            wordDetailImpl = { _, word ->
                if (word == "今日") gate.await()
                AnalyzerResult.Ok(WordDetail(meaning = word))
            },
        )
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は", "晴れ"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 1)
        // 队首卡在闸门上时认领后面的词：轮到它就该直接跳过，不再发请求
        prefetcher.claim("今日は晴れです。", "は")
        assertTrue(prefetcher.isInFlight("今日は晴れです。", "今日"))
        gate.complete(Unit)
        awaitWordDetailCalls(analyzer, 2)
        delay(30)

        assertEquals(listOf("今日", "晴れ"), analyzer.wordDetailWords)
        assertEquals(listOf("今日" to WordDetail(meaning = "今日"), "晴れ" to WordDetail(meaning = "晴れ")),
            recorder.details.map { it.second to it.third })
    }

    @Test
    fun `预取成功回调交回三元组而失败走 onFail 且不再重试`() = runBlocking {
        val analyzer = FakeAnalyzer(
            wordDetailImpl = { _, word ->
                if (word == "晴れ") AnalyzerResult.Err(AnalyzerError.Timeout)
                else AnalyzerResult.Ok(WordDetail(meaning = word))
            },
        )
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "晴れ"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 2)
        delay(30)

        assertEquals(listOf(Triple("今日は晴れです。", "今日", WordDetail(meaning = "今日"))), recorder.details)
        assertEquals(listOf("今日は晴れです。" to "晴れ"), recorder.fails)
        // 失败也算定局：再 prefetch 一次同一批词，一次请求都不该再发
        prefetcher.prefetch("今日は晴れです。", listOf("今日", "晴れ"), cfg, emptySet(), this)
        delay(30)
        assertEquals(2, analyzer.wordDetailCalls)
    }

    @Test
    fun `预取串行第二个词必须等第一个词返回`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(
            wordDetailImpl = { _, word ->
                if (word == "今日") gate.await()
                AnalyzerResult.Ok(WordDetail(meaning = word))
            },
        )
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 1)
        delay(30)
        // 并行的话第二个词早就发出去了：这里必须还停在一个
        assertEquals(listOf("今日"), analyzer.wordDetailWords)

        gate.complete(Unit)
        awaitWordDetailCalls(analyzer, 2)
        assertEquals(listOf("今日", "は"), analyzer.wordDetailWords)
        assertEquals(2, recorder.details.size)
    }

    @Test
    fun `reset 后同一批词会重新预取`() = runBlocking {
        val analyzer = FakeAnalyzer()
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 2)
        delay(20)

        prefetcher.reset()
        prefetcher.prefetch("今日は晴れです。", listOf("今日", "は"), cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 4)

        assertEquals(4, analyzer.wordDetailCalls)
        assertEquals(listOf("今日", "は", "今日", "は"), analyzer.wordDetailWords)
        assertEquals(4, recorder.details.size)
    }

    @Test
    fun `预取一次最多四十个词`() = runBlocking {
        val analyzer = FakeAnalyzer()
        val recorder = DetailRecorder()
        val prefetcher = prefetcherFor(analyzer, recorder)

        val words = (1..45).map { "w" + it }
        prefetcher.prefetch("長い句。", words, cfg, emptySet(), this)
        awaitWordDetailCalls(analyzer, 40)
        delay(30)

        assertEquals(40, analyzer.wordDetailCalls)
        assertEquals(40, recorder.details.size)
    }

    // ---------- 整段解析（用户 2026-10：几段话连起来一起分析） ----------

    @Test
    fun `整段解析只发一次请求且请求体是整段原文`() = runBlocking {
        val paragraph = "今日は晴れです。明日は雨です。明後日は雪です。"
        // 前提：这一段在旧口径下会被切成三句，改造后必须只发一次
        assertEquals(3, SentenceSplitter.split(paragraph).size)
        val paragraphs = SentenceSplitter.splitParagraphs(paragraph)
        assertEquals(1, paragraphs.size)

        val analyzer = FakeAnalyzer()
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)
        val key = ReadRules.cacheKey(paragraphs[0].text, cfg.model)

        assertTrue(requests.start(key, paragraphs[0].text, cfg, this))
        delay(50)

        assertEquals(1, analyzer.analyzeCalls)
        assertEquals(1, analyzer.translateCalls)
        assertEquals(1, recorder.parsed.size)
        // 解析器拿到的必须是整段，而不是段里的第一句
        assertEquals(paragraph, recorder.parsed[0].second.sentence)
    }

    @Test
    fun `多段文章每段各发一次请求`() = runBlocking {
        val text = "今日は晴れです。明日は雨です。\n\n次は雪です。とても寒いです。"
        val paragraphs = SentenceSplitter.splitParagraphs(text)
        assertEquals(2, paragraphs.size)
        val analyzer = FakeAnalyzer()
        val recorder = Recorder()
        val requests = requestsFor(analyzer, recorder)

        paragraphs.forEach { paragraph ->
            requests.start(ReadRules.cacheKey(paragraph.text, cfg.model), paragraph.text, cfg, this)
        }
        delay(50)

        assertEquals(2, analyzer.analyzeCalls)
        assertEquals(2, recorder.parsed.size)
        assertEquals(paragraphs.map { it.text }, recorder.parsed.map { it.second.sentence })
    }

    // ---------- ReadRangeRequests（区间合并分析） ----------

    private class RangeRecorder {
        val states = mutableListOf<ReadRangeUiState>()
        val parsed: List<ReadRangeBatch>
            get() = (states.lastOrNull() as? ReadRangeUiState.Parsed)?.batches ?: emptyList()
    }

    private fun rangeRequestsFor(
        analyzer: Analyzer,
        recorder: RangeRecorder,
        cacheLookup: (String, String) -> SentenceAnalysis? = { _, _ -> null },
        cacheStore: (AnalysisCacheEntry) -> Unit = {},
    ): ReadRangeRequests = ReadRangeRequests(
        analyzer = analyzer,
        onState = { state -> recorder.states += state },
        cacheLookup = cacheLookup,
        cacheStore = cacheStore,
        errorText = { error -> "E:" + error::class.simpleName },
    )

    /** 按段文本造区间：[start, end] 闭区间，和 UI 传给 start 的 subList 一致 */
    private fun range(paragraphs: List<ReadParagraph>, from: Int, to: Int): List<ReadParagraph> =
        paragraphs.subList(from, to + 1).toList()

    private fun paragraphsOf(vararg texts: String): List<ReadParagraph> {
        var offset = 0
        return texts.mapIndexed { index, text ->
            val paragraph = ReadParagraph(text = text, start = offset, end = offset + text.length, paragraphIndex = index)
            offset += text.length
            paragraph
        }
    }

    @Test
    fun `区间合并按段分批且多批结果按顺序拼接`() = runBlocking {
        // 每段 300 字，两段就会超 500，必须切成两批
        val paragraphs = paragraphsOf("あ".repeat(300), "い".repeat(300))
        val analyzer = FakeAnalyzer(analyzeImpl = { AnalyzerResult.Ok(SentenceAnalysis(sentence = it)) })
        val recorder = RangeRecorder()
        val requests = rangeRequestsFor(analyzer, recorder)

        assertTrue(requests.start(range(paragraphs, 0, 1), cfg, this))
        withTimeout(5_000) {
            while (recorder.states.lastOrNull() !is ReadRangeUiState.Parsed) delay(10)
        }

        assertEquals(2, analyzer.analyzeCalls)
        assertEquals(paragraphs.map { it.text }, recorder.parsed.map { it.text })
        // 第一批必须是 Loading，且带着总批数，弹层才知道还剩几批
        val first = recorder.states.first()
        assertTrue(first is ReadRangeUiState.Loading)
        assertEquals(2, (first as ReadRangeUiState.Loading).total)
    }

    @Test
    fun `同一区间不重复发枪`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(analyzeImpl = { sentence ->
            gate.await()
            AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
        })
        val recorder = RangeRecorder()
        val requests = rangeRequestsFor(analyzer, recorder)
        val paragraphs = paragraphsOf("短い段です。")

        assertTrue(requests.start(range(paragraphs, 0, 0), cfg, this))
        // 第二次同样的区间：还没跑完，不能再发一枪
        assertFalse(requests.start(range(paragraphs, 0, 0), cfg, this))
        gate.complete(Unit)
        withTimeout(5_000) {
            while (recorder.states.lastOrNull() !is ReadRangeUiState.Parsed) delay(10)
        }
        assertEquals(1, analyzer.analyzeCalls)
    }

    @Test
    fun `命中缓存时一个请求都不发`() = runBlocking {
        val paragraphs = paragraphsOf("短い段です。", "もう一段です。")
        val cached = SentenceAnalysis(sentence = SentenceSplitter.mergedText(paragraphs), translation = "缓存译文")
        val analyzer = FakeAnalyzer()
        val recorder = RangeRecorder()
        val requests = rangeRequestsFor(
            analyzer = analyzer,
            recorder = recorder,
            cacheLookup = { _, _ -> cached },
        )

        assertTrue(requests.start(range(paragraphs, 0, 1), cfg, this))
        withTimeout(5_000) {
            while (recorder.states.lastOrNull() !is ReadRangeUiState.Parsed) delay(10)
        }
        // 用户抱怨的「慢」就慢在这里：重复框选同一段必须瞬间出结果
        assertEquals(0, analyzer.analyzeCalls)
        assertEquals(0, analyzer.translateCalls)
        assertEquals(1, recorder.parsed.size)
        assertEquals("缓存译文", recorder.parsed.single().analysis.translation)
    }

    @Test
    fun `请求成功时结果写回缓存`() = runBlocking {
        val paragraphs = paragraphsOf("短い段です。")
        val analyzer = FakeAnalyzer()
        val recorder = RangeRecorder()
        val stored = mutableListOf<AnalysisCacheEntry>()
        val requests = rangeRequestsFor(analyzer, recorder, cacheStore = { stored += it })

        assertTrue(requests.start(range(paragraphs, 0, 0), cfg, this))
        withTimeout(5_000) {
            while (recorder.states.lastOrNull() !is ReadRangeUiState.Parsed) delay(10)
        }
        val entry = stored.single()
        assertEquals(ReadRules.cacheKey(paragraphs.single().text, cfg.model), entry.key)
        assertEquals(paragraphs.single().text, entry.sentence)
    }

    @Test
    fun `任一批失败则整体失败并带错误文案`() = runBlocking {
        val paragraphs = paragraphsOf("あ".repeat(300), "い".repeat(300))
        var call = 0
        val analyzer = FakeAnalyzer(analyzeImpl = { sentence ->
            call += 1
            if (call == 2) {
                AnalyzerResult.Err(AnalyzerError.Network("boom"))
            } else {
                AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
            }
        })
        val recorder = RangeRecorder()
        val requests = rangeRequestsFor(analyzer, recorder)

        assertTrue(requests.start(range(paragraphs, 0, 1), cfg, this))
        withTimeout(5_000) {
            // 等到终态：要么 Failed，要么（错误的）Parsed
            while (recorder.states.lastOrNull() is ReadRangeUiState.Loading || recorder.states.isEmpty()) delay(10)
        }
        val last = recorder.states.last()
        assertTrue(last is ReadRangeUiState.Failed)
        // 失败不返回半截结果：拼出来的东西缺一块，比直接说失败更误导
        assertEquals("E:Network", (last as ReadRangeUiState.Failed).message)
    }

    @Test
    fun `取消后不再回调任何状态`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer(analyzeImpl = { sentence ->
            gate.await()
            AnalyzerResult.Ok(SentenceAnalysis(sentence = sentence))
        })
        val recorder = RangeRecorder()
        val requests = rangeRequestsFor(analyzer, recorder)
        val paragraphs = paragraphsOf("短い段です。")

        assertTrue(requests.start(range(paragraphs, 0, 0), cfg, this))
        delay(20)
        requests.cancel()
        gate.complete(Unit)
        delay(100)

        // 取消只掐 Job：既不能变成 Failed（用户没失败），也不能冒出 Parsed
        assertTrue(recorder.states.all { it is ReadRangeUiState.Loading })
        assertFalse(requests.isRunning())
    }

    @Test
    fun `合并译文逐批换行拼接并丢掉空译文`() {
        val batches = listOf(
            ReadRangeBatch("a", SentenceAnalysis(sentence = "a", translation = "一段译文 ")),
            ReadRangeBatch("b", SentenceAnalysis(sentence = "b", translation = "  ")),
            ReadRangeBatch("c", SentenceAnalysis(sentence = "c", translation = "三段译文")),
        )
        assertEquals("一段译文\n三段译文", ReadRangeLogic.mergedTranslation(batches))
    }

    @Test
    fun `任一批未对齐时整体不算对齐`() {
        val aligned = ReadRangeBatch(
            "a",
            SentenceAnalysis(sentence = "a", tokens = listOf(AnalyzeToken("a")), aligned = true),
        )
        val broken = ReadRangeBatch(
            "b",
            SentenceAnalysis(sentence = "b", tokens = listOf(AnalyzeToken("b")), aligned = false),
        )
        assertTrue(ReadRangeLogic.allAligned(listOf(aligned)))
        assertFalse(ReadRangeLogic.allAligned(listOf(aligned, broken)))
        // 空批次不算对齐：没有内容就谈不上排版
        assertFalse(ReadRangeLogic.allAligned(emptyList()))
    }

    // ---------- 单词收藏 id ----------

    @Test
    fun `单词收藏 id 用 word 前缀且与段落收藏互不干扰`() {
        val wordKey = "pkey|今日"
        assertEquals("word::pkey|今日", ReadUiLogic.wordBookmarkIdFor(wordKey))

        val wordHit = listOf(
            Bookmark(id = "word::pkey|今日", type = "word", refId = wordKey, title = "今日", createdAt = "2025-01-10T12:00:00Z"),
        )
        assertTrue(ReadUiLogic.isWordBookmarked(wordHit, wordKey))
        assertFalse(ReadUiLogic.isWordBookmarked(emptyList(), wordKey))
        // 同 id 换词面就不是同一条收藏，否则一段里只能收藏一个词
        assertFalse(ReadUiLogic.isWordBookmarked(wordHit, "pkey|明日"))
        // 词收藏不冒充段落收藏：两者前缀不同，取消一条不会连带取消另一条
        assertFalse(ReadUiLogic.isBookmarked(wordHit, "pkey"))
        val paragraphHit = listOf(
            Bookmark(id = "sentence::pkey", type = "sentence", refId = "pkey", title = "段落", createdAt = "2025-01-10T12:00:00Z"),
        )
        assertFalse(ReadUiLogic.isWordBookmarked(paragraphHit, "pkey"))
    }
}
