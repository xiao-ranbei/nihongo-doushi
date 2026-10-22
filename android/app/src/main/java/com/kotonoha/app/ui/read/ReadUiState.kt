package com.kotonoha.app.ui.read

import com.kotonoha.app.engine.read.AnalysisCacheEntry
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 阅读器里「一句」的显示状态。
 *
 * spec §9.4 列了 5 个显示态，实现上只有 4 个状态：「已解析但未对齐」不是独立状态，
 * 而是 Parsed 且 analysis.aligned == false 时的一个渲染分支——对不对齐是解析结果自带的字段，
 * 再单开一个状态就得把同一份 analysis 存两遍，两边还可能不同步。
 *
 * 本文件是 UI 层唯一要进纯 JVM 单测的文件，因此禁止引入 Compose 与 Android 框架的任何符号：
 * 只碰 engine/read、net 与 storage 的纯 Kotlin 类型。
 */
sealed interface SentenceUiState {
    /** 还没解析：只显示原文与「解析」入口 */
    data object Idle : SentenceUiState

    /** 请求在飞：显示进度条，点击即取消 */
    data object Loading : SentenceUiState

    /** 解析成功（含译词失败、对齐失败的降级情况，见 ReadSentenceRequests） */
    data class Parsed(val analysis: SentenceAnalysis) : SentenceUiState

    /** 解析失败：message 已经是给人看的中文文案 */
    data class Failed(val message: String) : SentenceUiState
}

/** 阅读页用到的纯计算：标题、相对时间、字数门槛、收藏 id 换算。 */
object ReadUiLogic {
    private const val FALLBACK_TITLE = "未命名文章"
    private const val TITLE_LIMIT = 20
    private const val SENTENCE_BOOKMARK_PREFIX = "sentence::"
    private const val WORD_BOOKMARK_PREFIX = "word::"

    private val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    /**
     * 文章标题 = 首个非空句。超过 20 字截断加省略号：标题只用来在「最近文章」里认文章，
     * 一整段贴在标题行会把副信息（时间/字数）挤掉。
     */
    fun articleTitle(text: String): String {
        val first = SentenceSplitter.split(text).firstOrNull { it.text.isNotBlank() }?.text?.trim()
        if (first.isNullOrEmpty()) return FALLBACK_TITLE
        return if (first.length > TITLE_LIMIT) first.take(TITLE_LIMIT) + "…" else first
    }

    /**
     * 相对时间。解析失败返回原文而不抛异常：时间串来自本地存储，
     * 坏一条不该让整个「最近文章」列表崩掉。
     * 超过 7 天改显示日期——「12 天前」这种说法对读者没有信息量。
     */
    fun relativeTime(iso: String, now: Instant): String {
        val then = parseInstant(iso) ?: return iso
        val seconds = Duration.between(then, now).seconds
        return when {
            seconds < 60L -> "刚刚"
            seconds < 3600L -> (seconds / 60L).toString() + " 分钟前"
            seconds < 86400L -> (seconds / 3600L).toString() + " 小时前"
            seconds < 7L * 86400L -> (seconds / 86400L).toString() + " 天前"
            else -> DATE_FORMAT.format(then)
        }
    }

    /**
     * 优先按带时区的 ISO-8601 解析（App 自己写入的格式）。
     * 失败再按**不带时区**的本地日期时间解析：本地存储是可以被外部工具改写的 JSON，
     * 一条没有 Z 的时间串不该让「最近文章」把 2026-10-20T09:00:00 原样糊在用户脸上。
     */
    private fun parseInstant(iso: String): Instant? {
        try {
            return Instant.parse(iso)
        } catch (e: Exception) {
            // 落到下一种格式
        }
        return try {
            LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 收藏 id 规则与 AppStateRead.addSentenceBookmark 内部拼接方式一致，改一边必须改另一边。
     * 段落解析上线后这里传入的是**段落**缓存键，id 前缀仍沿用 "sentence::"：
     * BookmarkRules.ALLOWED_TYPES 只有 sentence/word 两种，而且早期已入库的句子书签也带这个前缀，
     * 改成 "paragraph::" 会让老书签变成失效 type 且无法取消收藏。
     */
    fun bookmarkIdFor(cacheKey: String): String = SENTENCE_BOOKMARK_PREFIX + cacheKey

    fun isBookmarked(items: List<Bookmark>, cacheKey: String): Boolean {
        val id = bookmarkIdFor(cacheKey)
        return items.any { it.id == id }
    }

    /**
     * 单词收藏 id：与 AppStateRead.addWordBookmark 内部拼接一致，改一边必须改另一边。
     * [wordKey] 由调用方拼成「段落缓存键|词面」，因此同一段里的同一个词反复收藏是同一条。
     */
    fun wordBookmarkIdFor(wordKey: String): String = WORD_BOOKMARK_PREFIX + wordKey

    fun isWordBookmarked(items: List<Bookmark>, wordKey: String): Boolean {
        val id = wordBookmarkIdFor(wordKey)
        return items.any { it.id == id }
    }

    /** 「N 字」按非空白字符算：粘贴的文章常带缩进与空行，按 length 报数会虚高 */
    fun charCount(text: String): Int = text.count { !it.isWhitespace() }

    /** 少于 10 字不值得发一次请求（还会白花钱） */
    fun canStart(text: String): Boolean = charCount(text) >= 10
}

/**
 * 整句解析成功后的逐词详情预取队列（用户 2026-10：点词要有基本瞬时的观感）。
 *
 * 为什么串行而不是并行：一口气并行发 20 个 wordDetail 会把连接池同时打满，首句瞬间烧掉一大截额度，
 * 而且用户第一个点到的词还得和其余 19 个抢不到带宽的请求一起排队。串行时队列的顺序就是词序，
 * 用户第一次点词看到的基本就是这份结果——预取的价值在「提前」，不在「全部一起提前」。
 *
 * 状态用两个集合而不是一个 Map：调用方只关心三件事——这个词是不是已经定局（不再发）、
 * 是不是在飞（点了就等它回来别重复发）、还剩哪些要发，两个 Set 刚好一一对应，
 * 多出来的状态字段都会成为「谁先改的」这类竞态的温床。
 *
 * 本文件禁止 import Compose / android / androidx：这里的并发语义必须能在纯 JVM 单测里断言。
 */
class ReadWordDetailPrefetcher(
    private val analyzer: Analyzer,
    private val onDetail: (sentence: String, word: String, detail: WordDetail) -> Unit,
    private val onFail: (sentence: String, word: String) -> Unit = { _, _ -> },
) {
    // 已定局：成功、失败、或被用户点击认领（点击后由 UI 自己发请求，预取不能再抢着发一遍）
    private val settled = mutableSetOf<String>()
    // 正在等待响应
    private val inFlight = mutableSetOf<String>()

    private fun key(sentence: String, word: String): String = sentence + "\u0000" + word

    /**
     * 把这句里还没结果的词排进预取队列，用调用方给的 [scope] 起一个串行任务。
     *
     * [scope] 必须是屏幕级而不是某个 LaunchedEffect 的生命周期：卡片划出屏幕被销毁时，
     * 不能把它所在句子的后台预取一起掐掉——预取的意义正是「用户还没看，词先备好」。
     *
     * [cached] 是这句在磁盘缓存里已有的词，已经在表里的不必再花一次额度。
     */
    fun prefetch(
        sentence: String,
        words: List<String>,
        cfg: ModelConfig,
        cached: Set<String>,
        scope: CoroutineScope,
    ) {
        val queue = words.distinct().filter { word ->
            val k = key(sentence, word)
            word !in cached && k !in settled && k !in inFlight
        }.take(MAX_PREFETCH_WORDS)
        if (queue.isEmpty()) return
        queue.forEach { inFlight.add(key(sentence, it)) }
        scope.launch {
            for (word in queue) {
                val k = key(sentence, word)
                // 用户抢在前面点了这个词：claim 已把它标为 settled，这一轮不再发
                if (k in settled) {
                    inFlight.remove(k)
                    continue
                }
                try {
                    when (val result = analyzer.wordDetail(sentence, word, cfg)) {
                        is AnalyzerResult.Ok -> onDetail(sentence, word, result.value)
                        is AnalyzerResult.Err -> onFail(sentence, word)
                    }
                } catch (e: CancellationException) {
                    // 取消是页面/会话退出，不是这个词失败：标记必须清掉，之后再次进入还能重试
                    inFlight.remove(k)
                    throw e
                }
                inFlight.remove(k)
                settled.add(k)
            }
        }
    }

    /** 这个词是否正在等预取结果：UI 据此只置 Loading，等回填而不是再发一枪 */
    fun isInFlight(sentence: String, word: String): Boolean = inFlight.contains(key(sentence, word))

    /**
     * 用户点击认领这个词：预取队列到这里就不再管它，由点击方自己发请求。
     *
     * [markSettled] 与它语义等价，合并成一个函数：两者都是「这个词已经有归属了，别再预取」，
     * 区别只在谁先到——预取成功/失败由队列自己登记，磁盘命中与用户点击由调用方登记。
     */
    fun claim(sentence: String, word: String) {
        val k = key(sentence, word)
        settled.add(k)
    }

    /** 同 [claim]：磁盘命中或预取已有结果时登记，避免同一批词被反复预取 */
    fun markSettled(sentence: String, word: String) {
        claim(sentence, word)
    }

    /** 清空两个集合（清本篇缓存后调用）：下一轮同一批词要重新预取 */
    fun reset() {
        settled.clear()
        inFlight.clear()
    }

    private companion object {
        /** 一次最多预取 40 个词：再长的句子也不可能有人挨个点完，只会白烧额度 */
        const val MAX_PREFETCH_WORDS = 40
    }
}

/**
 * 一句的解析请求管理：同一句不重复发枪，取消后不再回调。
 *
 * 为什么要自己管 Job 表：LazyColumn 里每一句独立请求，用户可能连点、也可能在请求中划走。
 * 没有这张表就只能靠 UI 的 disabled 状态挡重复点击，而组合函数的 state 更新不是同帧可见的。
 *
 * [errorText] 由调用方注入（UI 传 ModelSettingsScreen 里的 analyzerErrorText）：
 * 错误文案只有一个出处，而本文件不能 import 任何带平台依赖的 UI 文件，否则进不了 JVM 单测。
 */
class ReadSentenceRequests(
    private val analyzer: Analyzer,
    private val onState: (key: String, state: SentenceUiState) -> Unit,
    private val onParsed: (key: String, analysis: SentenceAnalysis) -> Unit,
    // 默认值只是防御：真正的 11 分支文案由 UI 注入（ModelSettingsScreen 里的 analyzerErrorText），
    // 这里再写一套就会有两个出处，改一处漏一处。
    private val errorText: (AnalyzerError) -> String = { "解析失败，请重试" },
) {
    private val running = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()

    /** 返回 false 表示该 key 已有请求在跑，本次没有发第二枪 */
    fun start(key: String, sentence: String, cfg: ModelConfig, scope: CoroutineScope): Boolean {
        if (running.contains(key)) return false
        running.add(key)
        onState(key, SentenceUiState.Loading)
        val job = scope.launch {
            try {
                // 解析与翻译互不依赖，必须并发：串行等于把用户等待时间相加（AC-03）。
                // coroutineScope 保证两个 deferred 都在本句的生命周期内收尾，
                // 不会出现「取消了这句、另一个请求还在后台跑」。
                val outcome = coroutineScope {
                    val analyzing = async { analyzer.analyze(sentence, cfg) }
                    val translating = async { analyzer.translate(sentence, cfg) }
                    when (val result = analyzing.await()) {
                        is AnalyzerResult.Err -> {
                            // 解析失败时译文没有落点：显式取消，别让它继续占网络与并发槽位。
                            // 只 cancel 不 await，CancellationException 就被吞在这个子协程里，不会漏到 UI。
                            translating.cancel()
                            AnalyzerResult.Err(result.error)
                        }
                        is AnalyzerResult.Ok -> {
                            // 译文失败不推翻解析结果：逐词读音词性才是阅读器的主力功能，
                            // 因为译文这一句没回来就把整句标红，等于用一个次要失败否掉主要成果。
                            val translation = when (val t = translating.await()) {
                                is AnalyzerResult.Ok -> t.value
                                is AnalyzerResult.Err -> ""
                            }
                            AnalyzerResult.Ok(result.value.copy(translation = translation))
                        }
                    }
                }
                if (!isActive) return@launch
                when (outcome) {
                    is AnalyzerResult.Err -> onState(key, SentenceUiState.Failed(errorText(outcome.error)))
                    is AnalyzerResult.Ok -> {
                        onState(key, SentenceUiState.Parsed(outcome.value))
                        onParsed(key, outcome.value)
                    }
                }
            } catch (e: CancellationException) {
                // 取消是用户意图，不是解析失败：不写 Failed，也不再回调任何东西
                throw e
            } finally {
                running.remove(key)
                jobs.remove(key)
            }
        }
        // 请求可能在 launch 里同步跑完并已在 finally 里出表，这时不能再补写一条僵尸 Job
        if (running.contains(key)) jobs[key] = job
        return true
    }

    fun cancel(key: String) {
        running.remove(key)
        jobs.remove(key)?.cancel()
    }

    fun isRunning(key: String): Boolean = running.contains(key)
}

/**
 * 区间合并分析里的一批：请求正文 + 该批的解析结果。
 *
 * 为什么不把各批拼成一个 SentenceAnalysis：弹层里点开一个词、收藏「这一段」时必须知道
 * 这个词出自哪一批，才有一个真实存在过的段文本与缓存键可指；拼成一条就再也认不回来了。
 */
data class ReadRangeBatch(val text: String, val analysis: SentenceAnalysis)

/** 区间合并分析的弹层状态。Idle 表示还没请求过（选择模式本身不归它管）。 */
sealed interface ReadRangeUiState {
    data object Idle : ReadRangeUiState

    /** done = 已拿到的批数，total = 总批数：多批时要让用户看见还剩几批 */
    data class Loading(val done: Int, val total: Int) : ReadRangeUiState

    data class Parsed(val batches: List<ReadRangeBatch>) : ReadRangeUiState

    data class Failed(val message: String) : ReadRangeUiState
}

/** 把多批结果拼成给人看的一条流：纯计算，放这里才能进 JVM 单测。 */
object ReadRangeLogic {
    /**
     * 译文逐批换行接起来：批与批之间本来就是段落边界，另起一行才不会把两句译文的句号挤在一起。
     * 空译文（该批译文降级成空串）直接丢掉，免得弹层里留一段空白。
     */
    fun mergedTranslation(batches: List<ReadRangeBatch>): String =
        batches.map { it.analysis.translation.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** 只要有一批没对齐，整条流就不能按 token 排版：混排会让对不上的那批看起来也像对齐了 */
    fun allAligned(batches: List<ReadRangeBatch>): Boolean =
        batches.isNotEmpty() && batches.all { it.analysis.aligned && it.analysis.tokens.isNotEmpty() }
}

/**
 * 区间合并分析的请求管理：把选中的段落区间按 500 字上限分批，逐批查缓存/发请求，拼成一条连续结果。
 *
 * 为什么串行不并发：批与批是同一段文字的先后，弹层要按顺序拼起来。并发虽然更快，但失败语义会变复杂
 * （一批挂了另一批还在往回写结果），而这里真正的耗时大头是首次请求本身；重复框选同一段时每批都命中
 * 磁盘缓存、一个请求都不发——那才是用户抱怨的「慢」。
 *
 * 缓存两个回调由调用方注入（UI 传 AppState.cacheLookup / cacheStore 的薄包装）：
 * 本文件不能 import 带平台依赖的东西，否则进不了 JVM 单测。
 */
class ReadRangeRequests(
    private val analyzer: Analyzer,
    private val onState: (ReadRangeUiState) -> Unit,
    private val cacheLookup: (sentence: String, model: String) -> SentenceAnalysis?,
    private val cacheStore: (entry: AnalysisCacheEntry) -> Unit = {},
    // 与 ReadSentenceRequests 同理：真正的 11 分支文案由 UI 注入，这里不再写第二套
    private val errorText: (AnalyzerError) -> String = { "解析失败，请重试" },
) {
    private var job: Job? = null

    // 正在跑的那次区间（含模型）：同一个区间连点两下不该发第二枪。
    // 用区间而不是批文本做键，因为「同一个区间」才是用户视角的一次操作。
    private var runningKey: String? = null

    /** 返回 false 表示这个区间已经在跑，本次没有发第二枪 */
    fun start(range: List<ReadParagraph>, cfg: ModelConfig, scope: CoroutineScope): Boolean {
        if (range.isEmpty()) return false
        val batches = SentenceSplitter.chunkParagraphs(range)
        if (batches.isEmpty()) return false
        val key = rangeKey(range, cfg.model)
        if (runningKey == key) return false
        runningKey = key
        job?.cancel()
        onState(ReadRangeUiState.Loading(0, batches.size))
        job = scope.launch {
            try {
                val collected = ArrayList<ReadRangeBatch>(batches.size)
                for ((index, batch) in batches.withIndex()) {
                    // 每一批开始前重看一眼：用户在等第 2 批时按了取消，第 3 批就不该再发
                    if (!isActive) return@launch
                    val text = SentenceSplitter.mergedText(batch)
                    val analysis = when (val hit = cacheLookup(text, cfg.model)) {
                        // 命中就直接用，也不回写缓存：回写只会把同一条记录的 createdAt 刷成现在
                        null -> when (val outcome = analyzeBatch(text, cfg)) {
                            is AnalyzerResult.Err -> {
                                onState(ReadRangeUiState.Failed(errorText(outcome.error)))
                                return@launch
                            }
                            is AnalyzerResult.Ok -> {
                                cacheStore(
                                    AnalysisCacheEntry(
                                        key = ReadRules.cacheKey(text, cfg.model),
                                        sentence = text,
                                        model = cfg.model,
                                        result = outcome.value,
                                        createdAt = Instant.now().toString(),
                                    ),
                                )
                                outcome.value
                            }
                        }
                        else -> hit
                    }
                    if (!isActive) return@launch
                    collected.add(ReadRangeBatch(text, analysis))
                    if (index + 1 < batches.size) {
                        onState(ReadRangeUiState.Loading(index + 1, batches.size))
                    }
                }
                onState(ReadRangeUiState.Parsed(collected))
            } catch (e: CancellationException) {
                // 取消是用户意图，不是解析失败：不写 Failed，也不再回调任何东西
                throw e
            } finally {
                // 只有自己仍是当前那次请求时才让位：被后一次 start 顶替时，键已经归后者
                if (runningKey == key) runningKey = null
            }
        }
        return true
    }

    /**
     * 单批的解析 + 翻译，沿用 ReadSentenceRequests 的并发与降级口径：
     * 解析失败时译文立即取消（没有落点），译文失败不推翻解析结果。
     */
    private suspend fun analyzeBatch(text: String, cfg: ModelConfig): AnalyzerResult<SentenceAnalysis> =
        coroutineScope {
            val analyzing = async { analyzer.analyze(text, cfg) }
            val translating = async { analyzer.translate(text, cfg) }
            when (val result = analyzing.await()) {
                is AnalyzerResult.Err -> {
                    translating.cancel()
                    AnalyzerResult.Err(result.error)
                }
                is AnalyzerResult.Ok -> {
                    val translation = when (val t = translating.await()) {
                        is AnalyzerResult.Ok -> t.value
                        is AnalyzerResult.Err -> ""
                    }
                    AnalyzerResult.Ok(result.value.copy(translation = translation))
                }
            }
        }

    /** 取消这次区间：放弃后面还没发的批次，且不再回调任何状态 */
    fun cancel() {
        runningKey = null
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = runningKey != null

    private fun rangeKey(range: List<ReadParagraph>, model: String): String =
        model + "|" + range.first().start + "-" + range.last().end
}
