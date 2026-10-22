package com.kotonoha.app.ui

import com.kotonoha.app.engine.read.AnalysisCache
import com.kotonoha.app.engine.read.AnalysisCacheEntry
import com.kotonoha.app.engine.read.ArticleList
import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ReadArticle
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.engine.read.WordDetail
import com.kotonoha.app.storage.Bookmark
import com.kotonoha.app.storage.BookmarkRules
import java.time.Instant

/**
 * 阅读模块的领域函数（按 D-032 放在顶层扩展文件里，形态与 `AppStateMistakes.kt` 一致）。
 *
 * 这些函数一共四类职责：配置落盘、文章增删、缓存读写、句子书签。
 * 全部返回 Boolean 表示「是否真的写下去了」——UI 靠这个决定要不要弹 Snackbar，
 * 而不是先去查一遍状态再猜。
 */

/** 校验不通过就不写盘：宁可让用户看到「请填写 API 地址」，也不要存一份半成品配置 */
fun AppState.saveReadConfig(cfg: ModelConfig): Boolean {
    if (ReadRules.validateConfig(cfg).isNotEmpty()) return false
    readConfig = cfg
    return store.saveReadConfig(cfg)
}

/** 同 id 覆盖（更新时间戳），按 updatedAt 淘汰到上限 */
fun AppState.saveArticle(article: ReadArticle): Boolean {
    val now = Instant.now().toString()
    val existing = readArticles.items.firstOrNull { it.id == article.id }
    // createdAt 保留第一次写入的值：文章的「创建时间」不该因为编辑正文而漂移
    val merged = article.copy(
        createdAt = existing?.createdAt ?: article.createdAt.ifBlank { now },
        updatedAt = now,
    )
    val next = ReadRules.trimArticles(
        listOf(merged) + readArticles.items.filter { it.id != merged.id },
    )
    readArticles = ArticleList(next)
    return store.saveReadArticles(readArticles)
}

fun AppState.deleteArticle(id: String): Boolean {
    val next = readArticles.items.filter { it.id != id }
    if (next.size == readArticles.items.size) return false
    readArticles = ArticleList(next)
    return store.saveReadArticles(readArticles)
}

/** 查缓存：调用方给句子与模型，键由 [ReadRules.cacheKey] 统一算，避免各处各写一遍拼接 */
fun AppState.cacheLookup(sentence: String, model: String): AnalysisCacheEntry? {
    val key = ReadRules.cacheKey(sentence, model)
    return readCache.entries.firstOrNull { it.key == key }
}

/** 同 key 覆盖；顺带把 details 一并保留（覆盖时不该丢掉已查过的词详情） */
fun AppState.cacheStore(entry: AnalysisCacheEntry): Boolean {
    val previous = readCache.entries.firstOrNull { it.key == entry.key }
    val merged = if (previous == null || entry.details.isNotEmpty()) {
        entry
    } else {
        entry.copy(details = previous.details)
    }
    val next = ReadRules.trimCache(
        listOf(merged) + readCache.entries.filter { it.key != merged.key },
    )
    readCache = AnalysisCache(next)
    return store.saveReadCache(readCache)
}

/**
 * 清掉一组句子在某个模型下的全部缓存条目（含词详情子表），返回实际删掉的条数。
 *
 * 阅读器侧边抽屉的「清空本篇解析缓存」用它：按句粒度删而不是清空整张表，
 * 用户清一篇不该把别的文章一起废掉。模型维度一并进 key，换模型后旧模型的结果仍留在表里。
 *
 * 一条都没命中就返回 0 且**不写盘**：空写等于把同一份 JSON 再落一次，纯属磨损存储。
 */
fun AppState.clearSentenceCache(sentences: List<String>, model: String): Int {
    val keys = sentences.map { ReadRules.cacheKey(it, model) }.toSet()
    val next = readCache.entries.filterNot { keys.contains(it.key) }
    val removed = readCache.entries.size - next.size
    if (removed == 0) return 0
    readCache = AnalysisCache(next)
    store.saveReadCache(readCache)
    return removed
}

/**
 * 把一个词详情挂到已有缓存条目下。
 *
 * 找不到条目就返回 false 而不新建：wordDetail 的入参只有句子与词，没有整句分析结果，
 * 凭空造一条 result 为空的条目会让后续读取拿到一份「有词义、无句子」的残缺数据。
 */
fun AppState.cacheStoreDetail(
    sentence: String,
    model: String,
    word: String,
    detail: WordDetail,
): Boolean {
    val key = ReadRules.cacheKey(sentence, model)
    val existing = readCache.entries.firstOrNull { it.key == key } ?: return false
    return cacheStore(existing.copy(details = existing.details + (word to detail)))
}

/**
 * 句子书签（spec §6.4）：type 固定 "sentence"，refId 存缓存键。
 *
 * id 拼成 `sentence::<refId>` 而不是随机值——同一句同一模型反复收藏必须是同一条，
 * 否则 BookmarkRules.add 的同 id 覆盖就失效，列表里会出现重复句子。
 */
fun AppState.addSentenceBookmark(refId: String, title: String, note: String = ""): Boolean {
    if (refId.isBlank() || title.isBlank()) return false
    val bookmark = Bookmark(
        id = SENTENCE_BOOKMARK_PREFIX + refId,
        type = SENTENCE_BOOKMARK_TYPE,
        refId = refId,
        title = title.take(BOOKMARK_TITLE_LIMIT),
        note = note.take(BOOKMARK_NOTE_LIMIT),
        createdAt = Instant.now().toString(),
    )
    return applyBookmarks(BookmarkRules.add(bookmarks, bookmark))
}

/**
 * 单词书签（用户 2026-10：收藏移动到点词展开页里，收藏选中的单词）。
 *
 * 与句子书签共用 BookmarkRules，只有 type 与前缀不同；id 仍是确定性拼接，
 * 同一段同一词反复点「收藏这个词」必须是同一条。refId 由调用方拼成「段落缓存键|词面」，
 * 「我的」页只负责展示，不需要理解这个格式。
 */
fun AppState.addWordBookmark(refId: String, title: String, note: String = ""): Boolean {
    if (refId.isBlank() || title.isBlank()) return false
    val bookmark = Bookmark(
        id = WORD_BOOKMARK_PREFIX + refId,
        type = WORD_BOOKMARK_TYPE,
        refId = refId,
        title = title.take(BOOKMARK_TITLE_LIMIT),
        note = note.take(BOOKMARK_NOTE_LIMIT),
        createdAt = Instant.now().toString(),
    )
    return applyBookmarks(BookmarkRules.add(bookmarks, bookmark))
}

fun AppState.removeBookmark(id: String): Boolean {
    val next = BookmarkRules.remove(bookmarks, id)
    if (next.items.size == bookmarks.items.size) return false
    return applyBookmarks(next)
}

private const val SENTENCE_BOOKMARK_TYPE = "sentence"
private const val SENTENCE_BOOKMARK_PREFIX = "sentence::"
private const val WORD_BOOKMARK_TYPE = "word"
private const val WORD_BOOKMARK_PREFIX = "word::"

/** spec §6.4：title 取句子前 30 字，note 取译文前 60 字 */
private const val BOOKMARK_TITLE_LIMIT = 30
private const val BOOKMARK_NOTE_LIMIT = 60
