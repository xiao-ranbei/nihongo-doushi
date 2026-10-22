package com.kotonoha.app.ui.read

import com.kotonoha.app.storage.BookmarkRules
import com.kotonoha.app.storage.LocalStore
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.addSentenceBookmark
import com.kotonoha.app.ui.addWordBookmark
import com.kotonoha.app.ui.removeBookmark
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 收藏写入入口的单测（用户 2026-10：收藏按钮从卡片移到点词展开页）。
 *
 * 覆盖点都是「不会崩、只会静静写错」的口径：id 前缀、type、截断长度、同 id 幂等、取消。
 * 这些错一条就会让「我的」页出现重复项或永远取消不掉的书签。
 */
class ReadBookmarkTest {

    private lateinit var dir: File
    private lateinit var app: AppState

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("read-bookmark-test").toFile()
        app = AppState(LocalStore(dir))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `收藏单词写入 word 类型且 id 带 word 前缀`() {
        val refId = "pkey|今日"
        assertTrue(app.addWordBookmark(refId = refId, title = "今日", note = "今日は晴れです。"))

        val items = app.bookmarks.items
        assertEquals(1, items.size)
        val bookmark = items[0]
        assertEquals("word::" + refId, bookmark.id)
        assertEquals("word", bookmark.type)
        assertEquals(refId, bookmark.refId)
        assertEquals("今日", bookmark.title)
        // 结构与 BookmarkRules 的准入条件一致，"我的"页才能原样展示
        assertTrue(BookmarkRules.isValid(bookmark))
        assertTrue(ReadUiLogic.isWordBookmarked(items, refId))
        assertFalse(ReadUiLogic.isBookmarked(items, "pkey"))
    }

    @Test
    fun `收藏同一段同一词是幂等的`() {
        val refId = "pkey|今日"
        assertTrue(app.addWordBookmark(refId = refId, title = "今日"))
        assertTrue(app.addWordBookmark(refId = refId, title = "今日"))
        // 同 id 覆盖而不追加：反复点「收藏这个词」不该在列表里堆重复项
        assertEquals(1, app.bookmarks.items.size)
    }

    @Test
    fun `再次点击取消单词收藏`() {
        val refId = "pkey|今日"
        assertTrue(app.addWordBookmark(refId = refId, title = "今日"))
        assertTrue(ReadUiLogic.isWordBookmarked(app.bookmarks.items, refId))

        assertTrue(app.removeBookmark(ReadUiLogic.wordBookmarkIdFor(refId)))
        assertFalse(ReadUiLogic.isWordBookmarked(app.bookmarks.items, refId))
        // 已经取消过再取消：不报成功，UI 不会误弹「已取消」
        assertFalse(app.removeBookmark(ReadUiLogic.wordBookmarkIdFor(refId)))
    }

    @Test
    fun `收藏这一段沿用 sentence 类型且 id 带 sentence 前缀`() {
        val key = "pkey"
        assertTrue(app.addSentenceBookmark(refId = key, title = "今日は晴れです。明日は雨です。"))

        val bookmark = app.bookmarks.items.single()
        assertEquals("sentence::" + key, bookmark.id)
        assertEquals("sentence", bookmark.type)
        assertTrue(BookmarkRules.isValid(bookmark))
        assertTrue(ReadUiLogic.isBookmarked(app.bookmarks.items, key))
        assertFalse(ReadUiLogic.isWordBookmarked(app.bookmarks.items, key))
    }

    @Test
    fun `单词与段落收藏互不覆盖`() {
        val key = "pkey"
        assertTrue(app.addWordBookmark(refId = key + "|今日", title = "今日"))
        assertTrue(app.addSentenceBookmark(refId = key, title = "段落标题"))
        assertEquals(2, app.bookmarks.items.size)

        assertTrue(app.removeBookmark(ReadUiLogic.bookmarkIdFor(key)))
        // 取消段落收藏不该带走同段里的词收藏
        assertEquals(1, app.bookmarks.items.size)
        assertTrue(ReadUiLogic.isWordBookmarked(app.bookmarks.items, key + "|今日"))
    }

    @Test
    fun `标题与备注按上限截断且空标题不写入`() {
        val longTitle = "あ".repeat(50)
        val longNote = "い".repeat(80)
        assertTrue(app.addWordBookmark(refId = "k|x", title = longTitle, note = longNote))
        val bookmark = app.bookmarks.items.single()
        assertEquals(30, bookmark.title.length)
        assertEquals(60, bookmark.note.length)

        assertFalse(app.addWordBookmark(refId = "k|y", title = "   "))
        assertFalse(app.addWordBookmark(refId = "  ", title = "語"))
        assertEquals(1, app.bookmarks.items.size)
    }
}
