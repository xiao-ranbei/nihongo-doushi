package com.kotonoha.app.storage

import com.kotonoha.app.engine.read.AnalysisCache
import com.kotonoha.app.engine.read.AnalysisCacheEntry
import com.kotonoha.app.engine.read.ArticleList
import com.kotonoha.app.engine.read.ModelConfig
import com.kotonoha.app.engine.read.ReadArticle
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.engine.read.SentenceAnalysis
import com.kotonoha.app.engine.read.WordDetail
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 三个新存储键的读写测试：用 `Files.createTempDirectory` 起独立目录，
 * 每个用例一个 [LocalStore]，互不干扰（与 `storage/` 下既有测试同一写法）。
 */
class ReadStorageTest {

    private fun newStore(): LocalStore = LocalStore(Files.createTempDirectory("read-store").toFile())

    private fun now(offsetSeconds: Long = 0): String =
        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(offsetSeconds).toString()

    private fun article(id: String, updatedOffset: Long = 0): ReadArticle = ReadArticle(
        id = id,
        title = "标题" + id,
        text = "本文" + id,
        createdAt = now(0),
        updatedAt = now(updatedOffset),
    )

    private fun entry(key: String, createdOffset: Long = 0): AnalysisCacheEntry = AnalysisCacheEntry(
        key = key,
        sentence = "句子" + key,
        model = "m1",
        result = SentenceAnalysis(sentence = "句子" + key, tokens = emptyList()),
        details = mapOf("語" to WordDetail(meaning = "词义")),
        createdAt = now(createdOffset),
    )

    @Test
    fun `配置读写往返`() {
        val store = newStore()
        val cfg = ModelConfig(
            baseUrl = "https://example.test/v1",
            model = "my-model",
            apiKey = "key-1234",
            temperature = 0.7,
        )
        assertTrue(store.saveReadConfig(cfg))
        val loaded = store.loadReadConfig()
        assertEquals(cfg, loaded)
        assertFalse(store.lastLoadInvalid)
    }

    @Test
    fun `未写入过时三个键都回退缺省`() {
        val store = newStore()
        assertEquals(ModelConfig(), store.loadReadConfig())
        assertEquals(ArticleList(), store.loadReadArticles())
        assertEquals(AnalysisCache(), store.loadReadCache())
        assertFalse(store.lastLoadInvalid)
    }

    @Test
    fun `文章与缓存读写往返`() {
        val store = newStore()
        assertTrue(store.saveReadArticles(ArticleList(listOf(article("a"), article("b", 10)))))
        assertTrue(store.saveReadCache(AnalysisCache(listOf(entry("k1"), entry("k2", 10)))))

        val articles = store.loadReadArticles()
        assertEquals(2, articles.items.size)
        assertEquals("a", articles.items[0].id)

        val cache = store.loadReadCache()
        assertEquals(2, cache.entries.size)
        assertNotNull(cache.entries.firstOrNull { it.key == "k1" })
        assertEquals("词义", cache.entries.first { it.key == "k1" }.details["語"]!!.meaning)
    }

    @Test
    fun `坏 JSON 回退缺省并置 lastLoadInvalid`() {
        val store = newStore()
        val dir = Files.createTempDirectory("read-store-bad").toFile()
        val broken = LocalStore(dir)
        File(dir, StorageKeys.READ_ANALYZER_CONFIG + ".json").writeText("{ this is not json")
        File(dir, StorageKeys.READ_ANALYZER_ARTICLES + ".json").writeText("[[[")
        File(dir, StorageKeys.READ_ANALYZER_CACHE + ".json").writeText("{\"entries\": oops}")

        val cfg = broken.loadReadConfig()
        assertTrue(broken.lastLoadInvalid)
        assertEquals(ModelConfig(), cfg)

        val articles = broken.loadReadArticles()
        assertTrue(articles.items.isEmpty())

        val cache = broken.loadReadCache()
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `文章超上限时裁剪并保留最新的`() {
        val store = newStore()
        val many = (1..60).map { article("id" + it, it.toLong()) }
        assertTrue(store.saveReadArticles(ArticleList(many)))
        val loaded = store.loadReadArticles()
        assertEquals(ReadRules.MAX_ARTICLES, loaded.items.size)
        assertEquals("id60", loaded.items[0].id)
        assertNull(loaded.items.firstOrNull { it.id == "id1" })
    }

    @Test
    fun `缓存超上限时裁剪并保留最新的`() {
        val store = newStore()
        val many = (1..510).map { entry("k" + it, it.toLong()) }
        assertTrue(store.saveReadCache(AnalysisCache(many)))
        val loaded = store.loadReadCache()
        assertEquals(ReadRules.MAX_CACHE_ENTRIES, loaded.entries.size)
        assertEquals("k510", loaded.entries[0].key)
        assertNull(loaded.entries.firstOrNull { it.key == "k1" })
    }

    @Test
    fun `非法配置拒绝写入`() {
        val store = newStore()
        assertFalse(store.saveReadConfig(ModelConfig()))
        assertEquals(ModelConfig(), store.loadReadConfig())

        assertFalse(store.saveReadConfig(ModelConfig(baseUrl = "ftp://x", model = "m", apiKey = "k")))
        assertFalse(store.saveReadConfig(ModelConfig(baseUrl = "https://x", model = "", apiKey = "k")))
        assertFalse(store.saveReadConfig(ModelConfig(baseUrl = "https://x", model = "m", apiKey = "")))
        assertFalse(
            store.saveReadConfig(
                ModelConfig(baseUrl = "https://x", model = "m", apiKey = "k", temperature = 1.5),
            ),
        )
    }

    @Test
    fun `读取时丢弃不合法条目`() {
        val dir = Files.createTempDirectory("read-store-dirty").toFile()
        val store = LocalStore(dir)
        // id 为空 / text 为空 的文章，key 为空 / model 为空的缓存条目都应被丢弃
        File(dir, StorageKeys.READ_ANALYZER_ARTICLES + ".json").writeText(
            "{\"items\":[{\"id\":\"\",\"title\":\"t\",\"text\":\"x\",\"createdAt\":\"" + now() +
                "\",\"updatedAt\":\"" + now() + "\"}," +
                "{\"id\":\"ok\",\"title\":\"t\",\"text\":\"x\",\"createdAt\":\"" + now() +
                "\",\"updatedAt\":\"" + now() + "\"}]}",
        )
        assertEquals(listOf("ok"), store.loadReadArticles().items.map { it.id })

        File(dir, StorageKeys.READ_ANALYZER_CACHE + ".json").writeText(
            "{\"entries\":[{\"key\":\"\",\"sentence\":\"s\",\"model\":\"m\"," +
                "\"result\":{\"sentence\":\"s\",\"tokens\":[]},\"details\":{},\"createdAt\":\"" +
                now() + "\"}," +
                "{\"key\":\"k\",\"sentence\":\"s\",\"model\":\"\"," +
                "\"result\":{\"sentence\":\"s\",\"tokens\":[]},\"details\":{},\"createdAt\":\"" +
                now() + "\"}," +
                "{\"key\":\"good\",\"sentence\":\"s\",\"model\":\"m\"," +
                "\"result\":{\"sentence\":\"s\",\"tokens\":[]},\"details\":{},\"createdAt\":\"" +
                now() + "\"}]}",
        )
        assertEquals(listOf("good"), store.loadReadCache().entries.map { it.key })
    }

    @Test
    fun `句子书签增删与同 id 幂等`() {
        val store = newStore()
        val first = Bookmark(
            id = "sentence::k1",
            type = "sentence",
            refId = "k1",
            title = "测试句子",
            note = "译文",
            createdAt = now(),
        )
        assertTrue(BookmarkRules.isValid(first))
        store.saveBookmarks(BookmarkList(listOf(first)))
        assertEquals(1, store.loadBookmarks().items.size)

        // 同 id 再收藏一次：覆盖而不追加
        val again = first.copy(title = "改过的标题", createdAt = now(5))
        store.saveBookmarks(BookmarkRules.add(store.loadBookmarks(), again))
        val afterAdd = store.loadBookmarks()
        assertEquals(1, afterAdd.items.size)
        assertEquals("改过的标题", afterAdd.items[0].title)

        store.saveBookmarks(BookmarkRules.remove(afterAdd, "sentence::k1"))
        assertTrue(store.loadBookmarks().items.isEmpty())
    }

    @Test
    fun `缓存键由句子与模型共同决定`() {
        assertEquals(ReadRules.cacheKey("同じ", "m1"), ReadRules.cacheKey("同じ", "m1"))
        assertFalse(ReadRules.cacheKey("同じ", "m1") == ReadRules.cacheKey("同じ", "m2"))
        assertEquals(64, ReadRules.cacheKey("同じ", "m1").length)
    }
}
