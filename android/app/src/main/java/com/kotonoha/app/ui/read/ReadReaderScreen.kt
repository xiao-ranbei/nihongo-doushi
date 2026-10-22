package com.kotonoha.app.ui.read

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kotonoha.app.engine.read.AnalysisCacheEntry
import com.kotonoha.app.engine.read.AnalyzeToken
import com.kotonoha.app.engine.read.ReadParagraph
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.engine.read.SentenceSplitter
import com.kotonoha.app.engine.read.TokenGrouping
import com.kotonoha.app.engine.read.WordDetail
import com.kotonoha.app.net.Analyzer
import com.kotonoha.app.net.AnalyzerResult
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.addSentenceBookmark
import com.kotonoha.app.ui.addWordBookmark
import com.kotonoha.app.ui.cacheLookup
import com.kotonoha.app.ui.cacheStore
import com.kotonoha.app.ui.cacheStoreDetail
import com.kotonoha.app.ui.clearSentenceCache
import com.kotonoha.app.ui.common.ModuleTopBar
import com.kotonoha.app.ui.removeBookmark
import com.kotonoha.app.ui.theme.paperColors
import com.kotonoha.app.ui.theme.posBackgroundColor
import java.time.Instant
import kotlinx.coroutines.launch

// 字号全部写死并配 lineHeight：日文假名与汉字在缺字回退时会换字体，
// 行高跟着字体度量走会让整页在逐句解析过程中不断跳动。spec §11 定的三档如下。
private val SENTENCE_TEXT_SIZE = 17.sp
private val SENTENCE_LINE_HEIGHT = 30.sp
private val TRANSLATION_TEXT_SIZE = 15.sp
private val TRANSLATION_LINE_HEIGHT = 26.sp
private val TOKEN_TEXT_SIZE = 14.sp
private val TOKEN_LINE_HEIGHT = 18.sp
private val FURIGANA_TEXT_SIZE = 12.sp
private val FURIGANA_LINE_HEIGHT = 14.sp
// 「长句已分段」提示用的最小字号（品詞行下线后只剩这一处使用）
private val NOTE_TEXT_SIZE = 11.sp
private val NOTE_LINE_HEIGHT = 14.sp

/** 逐词详情的取数状态：弹层里只有这三种显示，复用一个 sealed 比三个布尔量清楚 */
private sealed interface WordDetailState {
    data object Loading : WordDetailState
    data class Loaded(val detail: WordDetail) : WordDetailState
    data class Failed(val message: String) : WordDetailState
}

/**
 * 弹层要显示哪个词。
 *
 * [wordKey] 同时用来在 detailStates 里取结果，避免重复请求同一个词；
 * [paragraphKey]/[paragraphText] 是这个词所在段落，供弹层里的「收藏这一段」使用——
 * 收藏入口只在弹层里，弹层因此必须自己知道所属段落（用户 2026-10 反馈）。
 */
private data class WordDetailTarget(
    val word: String,
    val wordKey: String,
    val paragraphKey: String,
    val paragraphText: String,
)

/**
 * 区间合并分析的起点：长按哪个词就把哪一段记下来（用户在 2026-11 要求「长按开始选择，
 * 点击另一个单词后，取得他俩这一段的句子」）。
 *
 * 只记段落下标而不是记那一段的文本：段落列表由 [SentenceSplitter.splitParagraphs] 现算，
 * 存文本就要在每个回调里比对字符串，存下标直接对着同一个 paragraphs 列表取即可。
 * [word] 只用于把起点那个词涂亮，让用户确认自己长按的是哪个词。
 */
private data class RangeAnchor(val paragraphIndex: Int, val word: String)

/**
 * 阅读器页：一段一卡，逐段解析（用户 2026-10：几段话连起来一起分析，不要按句子分开显示）。
 *
 * 卡片数量从「句数」降到「段数」，配合收紧的边距，一屏能看到的正文明显变多。
 *
 * 每段的状态不集中摆放而是按 cacheKey 存进 mutableStateMap：
 * 列表是懒加载的，只为屏幕上出现的段落建状态，长文才不会被一百个 Idle 对象拖慢首帧。
 *
 * @param analyzer 由容器注入（便于以后换实现或做离线桩），本页只按 AnalyzerResult 分支。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReadReaderScreen(
    app: AppState,
    analyzer: Analyzer,
    title: String,
    text: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    showFurigana: Boolean,
    onToggleFurigana: (Boolean) -> Unit,
    showTranslation: Boolean,
    onToggleTranslation: (Boolean) -> Unit,
) {
    val cfg = app.readConfig
    val scope = rememberCoroutineScope()
    val configReady = ReadRules.validateConfig(cfg).isEmpty()
    val paragraphs = remember(text) { SentenceSplitter.splitParagraphs(text) }

    // key 带上 model：换模型后旧结果不再复用，requests 与状态表必须一起重建
    val states = remember(cfg.model) { mutableStateMapOf<String, SentenceUiState>() }
    val requests = remember(cfg.model) {
        ReadSentenceRequests(
            analyzer = analyzer,
            onState = { key, state -> states[key] = state },
            onParsed = { key, analysis ->
                // 只缓存成功结果：失败多半是网络或配置问题，缓存下来会一直错到过期
                app.cacheStore(
                    AnalysisCacheEntry(
                        key = key,
                        sentence = analysis.sentence,
                        model = cfg.model,
                        result = analysis,
                        createdAt = Instant.now().toString(),
                    ),
                )
            },
            errorText = ::analyzerErrorText,
        )
    }

    val detailStates = remember(cfg.model) { mutableStateMapOf<String, WordDetailState>() }
    var detailTarget by remember { mutableStateOf<WordDetailTarget?>(null) }

    // 选择模式的起点；null = 不在选择模式。终点由下一次点词给出，点完立刻发请求。
    var rangeAnchor by remember { mutableStateOf<RangeAnchor?>(null) }
    // 合并结果只进弹层，不写 states：列表里那些卡片的状态必须原样不动（这是这次改造的硬口径）
    var rangeState by remember(cfg.model) { mutableStateOf<ReadRangeUiState>(ReadRangeUiState.Idle) }
    val rangeRequests = remember(cfg.model) {
        ReadRangeRequests(
            analyzer = analyzer,
            onState = { state -> rangeState = state },
            // 两个缓存回调传 AppState 的薄包装：ReadRangeRequests 自己不碰平台依赖，才进得了 JVM 单测
            cacheLookup = { sentence, model -> app.cacheLookup(sentence, model)?.result },
            cacheStore = { entry -> app.cacheStore(entry) },
            errorText = ::analyzerErrorText,
        )
    }
    // 选中区间立刻发枪：点完终点就出结果，不再多按一次「解析」。
    // 起止谁先谁后由用户点词顺序决定，这里统一归一化，反向框选也用同一段。
    val startRange: (Int, Int) -> Unit = start@{ from, to ->
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        if (lo < 0 || hi >= paragraphs.size) return@start
        rangeAnchor = null
        rangeRequests.start(paragraphs.subList(lo, hi + 1).toList(), cfg, scope)
    }

    // 预取器跟着 model 重建：换了模型，旧词的详情既不该复用也不该继续在飞
    val prefetcher = remember(cfg.model) {
        ReadWordDetailPrefetcher(
            analyzer = analyzer,
            onDetail = { sentenceText, word, detail ->
                detailStates[ReadRules.cacheKey(sentenceText, cfg.model) + "|" + word] =
                    WordDetailState.Loaded(detail)
                app.cacheStoreDetail(sentenceText, cfg.model, word, detail)
            },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }

    // 点词：内存 → 磁盘 → 在飞的预取 → 自己发请求。前三步都是为了不再发一枪重复请求。
    // paragraphKey 一并带进弹层：弹层里的「收藏这一段」要用它当 refId。
    val openWordDetail: (String, String, AnalyzeToken) -> Unit = open@{ sentenceText, key, token ->
        val wordKey = ReadRules.cacheKey(sentenceText, cfg.model) + "|" + token.word
        detailTarget = WordDetailTarget(
            word = token.word,
            wordKey = wordKey,
            paragraphKey = key,
            paragraphText = sentenceText,
        )
        // 已经在表里（预取回填或自己刚查过）：直接复用，别把已有结果盖成 Loading
        if (detailStates[wordKey] is WordDetailState.Loaded) return@open
        val cachedDetail = app.cacheLookup(sentenceText, cfg.model)?.details?.get(token.word)
        if (cachedDetail != null) {
            detailStates[wordKey] = WordDetailState.Loaded(cachedDetail)
            prefetcher.markSettled(sentenceText, token.word)
            return@open
        }
        if (prefetcher.isInFlight(sentenceText, token.word)) {
            // 预取在飞：只置 Loading 等它回填。再发一枪等于同一个词付两次额度，先回来的那份还可能被覆盖
            detailStates[wordKey] = WordDetailState.Loading
            return@open
        }
        // 认领这个词，让预取队列跳过它：用户点了就先满足这一次点击
        prefetcher.claim(sentenceText, token.word)
        detailStates[wordKey] = WordDetailState.Loading
        scope.launch {
            when (val result = analyzer.wordDetail(sentenceText, token.word, cfg)) {
                is AnalyzerResult.Ok -> {
                    app.cacheStoreDetail(sentenceText, cfg.model, token.word, result.value)
                    detailStates[wordKey] = WordDetailState.Loaded(result.value)
                }
                is AnalyzerResult.Err -> {
                    detailStates[wordKey] = WordDetailState.Failed(analyzerErrorText(result.error))
                }
            }
        }
    }

    // 选择模式下返回键只退出选择：容器在阅读器路由上无条件接管返回键（ReadFlow），
    // 这里不抢的话用户一次误长按就会被整个踢出正文回到输入页。
    // 放在 ReadSideMenu 之前：抽屉打开时它的 BackHandler 后组合，先拿到事件，关抽屉的行为不受影响。
    BackHandler(enabled = rangeAnchor != null) { rangeAnchor = null }

    ReadSideMenu(
        showFurigana = showFurigana,
        onToggleFurigana = onToggleFurigana,
        showTranslation = showTranslation,
        onToggleTranslation = onToggleTranslation,
        onOpenSettings = onOpenSettings,
        onClearCache = {
            val removed = app.clearSentenceCache(paragraphs.map { it.text }, cfg.model)
            // 内存里的三份状态必须一起清：只删磁盘的话卡片还显示着旧解析，用户会以为没生效
            states.clear()
            detailStates.clear()
            prefetcher.reset()
            detailTarget = null
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (removed > 0) "已清空，重新解析" else "没有可清除的记录",
                )
            }
        },
    ) { openMenu ->
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            title = title,
            onBack = onBack,
            trailing = {
                IconButton(onClick = openMenu) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "更多设置",
                    )
                }
            },
        )

        if (!configReady) {
            // 常驻条而不是一次性提示：没配置时每一句都解析不了，用户随时需要看到出路
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "还没配置模型与服务地址，解析用不了",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("去配置模型")
                    }
                }
            }
        }

        LazyColumn(
            // weight 而不是 fillMaxSize：下面的选择条要占一行，占满高度的列表会把它挤成 0 高
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            // 段间只留 8dp：卡片本身已有描边与内边距，再给大间距就回到「一段一大片空白」的旧观感
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 用下标而不是段落本身做选择区间的锚点：区间是「第几段到第几段」，
            // 段落对象带着 start/end 偏移，直接比下标最不容易对错。
            itemsIndexed(items = paragraphs, key = { _, paragraph -> paragraph.start }) { index, paragraph ->
                val key = ReadRules.cacheKey(paragraph.text, cfg.model)
                // 命中缓存时直接用缓存结果当初始态，而不是先渲染 Idle 再改：否则每次进页都会闪一下「解析」按钮
                val cachedState = remember(key) {
                    val hit = app.cacheLookup(paragraph.text, cfg.model)
                    if (hit != null) SentenceUiState.Parsed(hit.result) else SentenceUiState.Idle
                }
                val state = states[key] ?: cachedState
                // 解析成功就后台预取这一段的所有词。scope 用屏幕级的而不是 LaunchedEffect 自己的：
                // 卡片被划出屏幕销毁时，预取不能跟着断——它存在的意义就是用户还没看时先把词备好
                if (state is SentenceUiState.Parsed) {
                    val analysis = state.analysis
                    LaunchedEffect(key) {
                        prefetcher.prefetch(
                            sentence = paragraph.text,
                            words = analysis.tokens.map { it.word },
                            cfg = cfg,
                            cached = app.cacheLookup(paragraph.text, cfg.model)?.details?.keys ?: emptySet(),
                            scope = scope,
                        )
                    }
                }
                val anchor = rangeAnchor
                ReadParagraphCard(
                    paragraph = paragraph,
                    state = state,
                    showFurigana = showFurigana,
                    showTranslation = showTranslation,
                    // 选择模式下点词 = 定终点，点卡片任意处 = 拿整段当终点：
                    // 两种都落到同一个回调，用户不用先学会哪种点击才算数
                    selectMode = anchor != null,
                    onSelectEnd = { anchor?.let { startRange(it.paragraphIndex, index) } },
                    onWordLongClick = { token -> rangeAnchor = RangeAnchor(index, token.word) },
                    selected = anchor != null && anchor.paragraphIndex == index,
                    onParse = { requests.start(key, paragraph.text, cfg, scope) },
                    // 取消后状态必须显式回到 Idle：cancel 只掐 Job、不动状态表，
                    // 不回落的话这一段会永远停在「解析中…」，再点也只是重复取消
                    onCancel = {
                        requests.cancel(key)
                        states[key] = SentenceUiState.Idle
                    },
                    onWordClick = { token ->
                        // 选择模式里点词是「选终点」而不是「查词」：同一手势不能有两种解释
                        val a = rangeAnchor
                        if (a != null) startRange(a.paragraphIndex, index)
                        else openWordDetail(paragraph.text, key, token)
                    },
                )
            }
        }
        val anchor = rangeAnchor
        if (anchor != null) {
            // 常驻底部条而不是长按菜单：选择是一段持续的状态，用户要能随时看到「已经在选」，
            // 并且立刻有「只解析这一段」和「退出去」两条出路，不必猜怎么取消。
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "已选「" + anchor.word + "」，再点一个词作为终点",
                        modifier = Modifier.weight(1f),
                        fontSize = NOTE_TEXT_SIZE,
                        lineHeight = NOTE_LINE_HEIGHT,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = { startRange(anchor.paragraphIndex, anchor.paragraphIndex) }) {
                        Text("仅这一段")
                    }
                    TextButton(onClick = { rangeAnchor = null }) {
                        Text("取消")
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }

    // 合并结果弹层与词详情弹层可以同时存在：从合并结果里点词，词详情叠在它上面，
    // 关掉词详情回到合并结果，而不是一起消失（用户要的是「先看这一段，再挑词」）。
    if (rangeState !is ReadRangeUiState.Idle) {
        MergedResultSheet(
            state = rangeState,
            showFurigana = showFurigana,
            showTranslation = showTranslation,
            model = cfg.model,
            onWordClick = { batch, batchKey, token ->
                // 点词要能收藏「这一段」：上下文用该词所属批次的文本与缓存键，
                // 那才是一段真实发出过请求、也真实写进缓存的文本
                openWordDetail(batch.text, batchKey, token)
            },
            onDismiss = {
                // 关掉弹层等于放弃这次合并：还在跑的批次必须停，否则占着额度用户也看不见
                rangeRequests.cancel()
                rangeState = ReadRangeUiState.Idle
            },
        )
    }

    val target = detailTarget
    if (target != null) {
        // 收藏入口全在弹层里（用户 2026-10：收藏按钮从卡片移到点词展开页）。两种收藏互不干扰：
        // 词用 "word::<段落键|词面>"，段用 "sentence::<段落键>"，各自独立可取消。
        val wordBookmarkKey = target.paragraphKey + "|" + target.word
        WordDetailSheet(
            word = target.word,
            state = detailStates[target.wordKey] ?: WordDetailState.Loading,
            wordBookmarked = ReadUiLogic.isWordBookmarked(app.bookmarks.items, wordBookmarkKey),
            paragraphBookmarked = ReadUiLogic.isBookmarked(app.bookmarks.items, target.paragraphKey),
            onToggleWordBookmark = {
                if (ReadUiLogic.isWordBookmarked(app.bookmarks.items, wordBookmarkKey)) {
                    app.removeBookmark(ReadUiLogic.wordBookmarkIdFor(wordBookmarkKey))
                } else {
                    // note 存所在段落开头：收藏列表只显示 title，光一个词看不出是哪篇文章里的
                    app.addWordBookmark(
                        refId = wordBookmarkKey,
                        title = target.word,
                        note = target.paragraphText.take(60),
                    )
                }
            },
            onToggleParagraphBookmark = {
                if (ReadUiLogic.isBookmarked(app.bookmarks.items, target.paragraphKey)) {
                    app.removeBookmark(ReadUiLogic.bookmarkIdFor(target.paragraphKey))
                } else {
                    app.addSentenceBookmark(
                        refId = target.paragraphKey,
                        title = target.paragraphText.take(30),
                    )
                }
            },
            onDismiss = { detailTarget = null },
        )
    }
    }
}

/**
 * 一段一卡。四种状态各自决定纵向结构与可点区域：
 * Idle/Loading 整卡可点（分别等于「解析」与「取消」），Parsed/Failed 整卡不点，
 * 免得用户想点某个词时被整卡点击抢走事件。
 *
 * 卡上没有任何收藏控件：收藏只从点词弹层进（用户 2026-10 反馈）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadParagraphCard(
    paragraph: ReadParagraph,
    state: SentenceUiState,
    showFurigana: Boolean,
    showTranslation: Boolean,
    onParse: () -> Unit,
    onCancel: () -> Unit,
    onWordClick: (AnalyzeToken) -> Unit,
    selected: Boolean = false,
    selectMode: Boolean = false,
    onSelectEnd: () -> Unit = {},
    // 默认空实现：卡片在非选择模式下没有长按语义，不让每个调用点都被迫关心它
    onWordLongClick: (AnalyzeToken) -> Unit = {},
) {
    val paper = paperColors()
    // 选择模式下整卡点击 = 把这一整段当终点（用户点了卡片空白处而不是某个词时，
    // 意图显然也是「到这一段为止」，此时不该再去抢 Idle 卡的「解析」点击）
    val cardClick = when {
        selectMode -> Modifier.clickable(onClick = onSelectEnd, role = Role.Button)
        state is SentenceUiState.Idle -> Modifier.clickable(onClick = onParse, role = Role.Button)
        state is SentenceUiState.Loading -> Modifier.clickable(onClick = onCancel, role = Role.Button)
        else -> Modifier
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(cardClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        // 选中的起点用主色加粗描边：底部条只说了选了哪个词，用户还得在列表里一眼找到它落在哪一段
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
            // 解析成功后整行原句不再渲染（用户 2026-10 反馈：原句 + 译文 + 逐词块三段太乱）：
            // 逐词块本身就是原文，留一行原句只是把卡片撑高一倍。
            // 唯一的例外是「解析成功但逐词区拿不出词」——未对齐或空 token 时逐词区等于把段落弄丢了，
            // 这时必须把原段落在原位，否则卡片会变成一张只有译文的空卡。
            val showOriginal = when (state) {
                is SentenceUiState.Idle, is SentenceUiState.Loading, is SentenceUiState.Failed -> true
                is SentenceUiState.Parsed ->
                    !state.analysis.aligned || state.analysis.tokens.isEmpty()
            }
            if (showOriginal) {
                Text(
                    text = paragraph.text,
                    fontSize = SENTENCE_TEXT_SIZE,
                    lineHeight = SENTENCE_LINE_HEIGHT,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 用户看到的「一段」可能只是超长段落的前半截：不标注就等于把截断伪装成段界
            if (paragraph.hardCut) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "长段落已分段",
                    fontSize = NOTE_TEXT_SIZE,
                    lineHeight = NOTE_LINE_HEIGHT,
                    color = paper.muted,
                )
            }

            when (state) {
                is SentenceUiState.Idle -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onParse) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "解析这段",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("解析")
                    }
                }

                is SentenceUiState.Loading -> Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "解析中…",
                        fontSize = FURIGANA_TEXT_SIZE,
                        color = paper.muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    // 不定进度：解析耗时取决于模型，给百分比是假精度
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is SentenceUiState.Parsed -> ParsedBody(
                    analysis = state.analysis,
                    showFurigana = showFurigana,
                    showTranslation = showTranslation,
                    onWordClick = onWordClick,
                    onWordLongClick = onWordLongClick,
                )

                is SentenceUiState.Failed -> Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = state.message,
                        fontSize = TRANSLATION_TEXT_SIZE,
                        lineHeight = 20.sp,
                        color = paper.textRed,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onParse) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParsedBody(
    analysis: com.kotonoha.app.engine.read.SentenceAnalysis,
    showFurigana: Boolean,
    showTranslation: Boolean,
    onWordClick: (AnalyzeToken) -> Unit,
    // 只有阅读页主列表的长按才进选择模式，弹层里的词块不需要，
    // 所以这里给默认空实现，弹层就能当同一个渲染器复用
    onWordLongClick: ((AnalyzeToken) -> Unit)? = null,
) {
    val paper = paperColors()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        // 译文默认关闭（用户要的是「只看拆解句」）。关闭时整块不渲染、不留空行：
        // 留空高度等于把开关做成半失效，而用户关掉译文就是为了让卡片变矮变清（同 TokenCell 假名那条口径）。
        if (showTranslation && analysis.translation.isNotBlank()) {
            Text(
                text = analysis.translation,
                fontSize = TRANSLATION_TEXT_SIZE,
                lineHeight = TRANSLATION_LINE_HEIGHT,
                color = paper.textStrong,
            )
        }

        if (analysis.aligned) {
            // 没有译文时不需要再空一行：卡片顶部内边距已经隔开原句位置
            if (showTranslation && analysis.translation.isNotBlank()) Spacer(Modifier.height(6.dp))
            // 段落里带换行：一段一请求后换行 token 变多，必须真的断行而不是渲染成一个空白词。
            // 每行还是一个 FlowRow（一组是一个 item：组内必须紧排让标点贴住前一个词，组间才允许折行），
            // 行距 4dp、逐词块行间距 2dp——改造前是「一句一张卡 + 10dp 卡间距」，
            // 现在整段连排才装得下一屏（用户 2026-10：段落太分开，显示更紧凑）。
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tokenLines(analysis.tokens).forEach { line ->
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TokenGrouping.group(line).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                group.forEach { token ->
                                    TokenCell(
                                        token = token,
                                        showFurigana = showFurigana,
                                        onClick = { onWordClick(token) },
                                        onLongClick = onWordLongClick?.let { longClick -> { longClick(token) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "分词不可用（原文重建不一致）",
                fontSize = FURIGANA_TEXT_SIZE,
                color = paper.muted,
            )
            if (analysis.alignmentNote.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = analysis.alignmentNote,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = paper.muted,
                )
            }
        }
    }
}

/** 模型用这个品詞表示原文换行（与 TokenGrouping 内部约定一致） */
private const val LINE_BREAK_POS = "改行"

/**
 * 把一段的 token 按换行切成「显示行」，换行 token 本身不保留。
 *
 * 段落解析上线后一段里可能有很多换行：不真的断行的话，FlowRow 会把换行 token 渲染成一个
 * 占位的空白块，整段挤成一坨。空行（连续两个换行）会切出一个空 group，这里顺手丢掉，
 * 否则会在卡片里留下无来源的空隙。
 *
 * 切行之后的每一行再交给 [TokenGrouping.group]，禁则分组仍然只在行内生效——
 * 跨行的标点本来就不该被粘回上一行末尾。
 */
private fun tokenLines(tokens: List<AnalyzeToken>): List<List<AnalyzeToken>> {
    val lines = ArrayList<List<AnalyzeToken>>()
    var current = ArrayList<AnalyzeToken>()
    for (token in tokens) {
        if (token.pos == LINE_BREAK_POS) {
            if (current.isNotEmpty()) lines.add(current)
            current = ArrayList()
        } else {
            current.add(token)
        }
    }
    if (current.isNotEmpty()) lines.add(current)
    return lines
}

/**
 * 一个词的读音 + 词体，底色按品詞区分。假名行空串也占一行固定高度，否则同一组里的词会上下错位。
 * 品詞文字不再渲染：它在每个词下面重复一遍，逐词区会糊成一片（用户 2026-10 反馈），
 * 底色接管这条信息后单字高度也从三行降回两行。
 *
 * @param showFurigana 关闭时**整行不渲染**而不是留空：留一行空高度等于把开关做成半失效，
 *   用户关掉注音就是为了让逐词区变矮变清。整篇是同一开关，同组词的相对高度仍然一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TokenCell(
    token: AnalyzeToken,
    showFurigana: Boolean,
    onClick: () -> Unit,
    // 只有主列表传它（长按 = 区间起点）。默认 null 时退回纯 clickable：
    // combinedClickable 在空长按时会多占一份手势识别，弹层里没有理由付出这个代价
    onLongClick: (() -> Unit)? = null,
) {
    val background = posBackgroundColor(token.pos)
    // 长按必须走 combinedClickable：clickable 没有 onLongClick 参数，
    // 把长按写进 clickable 的 lambda 里只会静默失效（点击永远先到）
    val gestures = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
    } else {
        Modifier.clickable(onClick = onClick, role = Role.Button)
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (background != null) Modifier.background(background) else Modifier)
            .then(gestures)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showFurigana) {
            Text(
                text = token.furigana.ifEmpty { " " },
                fontSize = FURIGANA_TEXT_SIZE,
                lineHeight = FURIGANA_LINE_HEIGHT,
                color = paperColors().muted,
                maxLines = 1,
            )
        }
        Text(
            text = token.word,
            fontSize = TOKEN_TEXT_SIZE,
            lineHeight = TOKEN_LINE_HEIGHT,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 区间合并结果弹层。
 *
 * 结果只放在这里、列表一张卡都不动（用户 2026-10 明确要的交互）：合并分析得到的是一整段
 * 跨段的词流，塞回某一张卡就意味着要把本来属于别的段的词写进那张卡的状态里，
 * 下次单独解析那一段时两边会打架。弹层是「临时看一眼」的容器，关掉即走，不污染列表状态。
 *
 * 内容用 verticalScroll 而不是 LazyColumn：弹层高度由 ModalBottomSheet 决定，
 * LazyColumn 需要确定的高度约束才能工作，套进 Column 里会直接崩或高度塌成 0。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MergedResultSheet(
    state: ReadRangeUiState,
    showFurigana: Boolean,
    showTranslation: Boolean,
    // 批次文本的缓存键必须和发请求时用同一个模型算出，否则收藏「这一段」会指向一条不存在的记录
    model: String,
    onWordClick: (ReadRangeBatch, String, AnalyzeToken) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = paperColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is ReadRangeUiState.Idle -> Unit

                is ReadRangeUiState.Loading -> {
                    Text(
                        text = "正在解析… 第 " + (state.done + 1) + "/" + state.total + " 批",
                        fontSize = TRANSLATION_TEXT_SIZE,
                        lineHeight = TRANSLATION_LINE_HEIGHT,
                        color = paper.muted,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is ReadRangeUiState.Failed -> {
                    Text(
                        text = state.message,
                        fontSize = TRANSLATION_TEXT_SIZE,
                        lineHeight = TRANSLATION_LINE_HEIGHT,
                        color = paper.textRed,
                    )
                }

                is ReadRangeUiState.Parsed -> {
                    // 译文合起来放在最上面一格：逐批各渲染一段译文会把整篇切成若干截，
                    // 而用户想看的是「这一段话连起来是什么意思」
                    val translation = ReadRangeLogic.mergedTranslation(state.batches)
                    if (showTranslation && translation.isNotBlank()) {
                        Text(
                            text = translation,
                            fontSize = TRANSLATION_TEXT_SIZE,
                            lineHeight = TRANSLATION_LINE_HEIGHT,
                            color = paper.textStrong,
                        )
                    }
                    state.batches.forEach { batch ->
                        val batchKey = ReadRules.cacheKey(batch.text, model)
                        ParsedBody(
                            analysis = batch.analysis,
                            showFurigana = showFurigana,
                            // 译文已在上方合并渲染，这里逐批再画一遍就是重复
                            showTranslation = false,
                            onWordClick = { token -> onWordClick(batch, batchKey, token) },
                        )
                    }
                    if (!ReadRangeLogic.allAligned(state.batches)) {
                        Text(
                            text = "部分分段分词不可用，已按原文显示",
                            fontSize = NOTE_TEXT_SIZE,
                            lineHeight = NOTE_LINE_HEIGHT,
                            color = paper.muted,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordDetailSheet(
    word: String,
    state: WordDetailState,
    wordBookmarked: Boolean,
    paragraphBookmarked: Boolean,
    onToggleWordBookmark: () -> Unit,
    onToggleParagraphBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = paperColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = word,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (state) {
                is WordDetailState.Loading -> Text(
                    text = "查询中…",
                    fontSize = TRANSLATION_TEXT_SIZE,
                    color = paper.muted,
                )

                is WordDetailState.Failed -> Text(
                    text = state.message,
                    fontSize = TRANSLATION_TEXT_SIZE,
                    lineHeight = 22.sp,
                    color = paper.textRed,
                )

                is WordDetailState.Loaded -> {
                    val fields = listOf(
                        "意思" to state.detail.meaning,
                        "辞书形" to state.detail.dictionaryForm,
                        "用法" to state.detail.usage,
                        "活用" to state.detail.conjugation,
                        "例句" to state.detail.example,
                        "例句译文" to state.detail.exampleMeaning,
                    ).filter { it.second.isNotBlank() }
                    if (fields.isEmpty()) {
                        Text(
                            text = "模型没有给出这个词的更多信息",
                            fontSize = TRANSLATION_TEXT_SIZE,
                            color = paper.muted,
                        )
                    } else {
                        fields.forEach { (label, value) -> DetailField(label = label, value = value) }
                    }
                }
            }

            // 收藏是弹层里唯一的动作区：卡片上不再有星标，用户想收藏必须先点开一个词
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onToggleWordBookmark) {
                    Text(if (wordBookmarked) "已收藏 · 取消" else "收藏这个词")
                }
                TextButton(onClick = onToggleParagraphBookmark) {
                    Text(if (paragraphBookmarked) "已收藏 · 取消" else "收藏这一段")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

/** 六字段里有几个常年为空，空的直接不占位（调用方已过滤，这里不再判空） */
@Composable
private fun DetailField(label: String, value: String) {
    val paper = paperColors()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 12.sp, color = paper.muted)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = TRANSLATION_TEXT_SIZE,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


