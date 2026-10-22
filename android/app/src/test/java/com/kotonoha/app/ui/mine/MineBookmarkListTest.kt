package com.kotonoha.app.ui.mine

import com.kotonoha.app.storage.Bookmark
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 「我的」页书签区的展示逻辑：由新到旧排序，以及 type 到可见标记的映射。
 *
 * 这两条错了都不会崩，只会静静显示错——顺序反了最新收藏看不到，
 * type 映射漏了就把单词收藏标成段落收藏。
 */
class MineBookmarkListTest {

    private fun bookmark(
        id: String,
        type: String = "sentence",
        createdAt: String = "2026-10-17T00:00:00Z",
    ) = Bookmark(
        id = id,
        type = type,
        refId = "ref-" + id,
        title = "标题" + id,
        note = "",
        createdAt = createdAt,
    )

    @Test
    fun `书签按创建时间由新到旧排列`() {
        val items = listOf(
            bookmark("a", createdAt = "2026-10-15T00:00:00Z"),
            bookmark("c", createdAt = "2026-10-17T00:00:00Z"),
            bookmark("b", createdAt = "2026-10-16T00:00:00Z"),
        )
        assertEquals(listOf("c", "b", "a"), sortedBookmarks(items).map { it.id })
    }

    @Test
    fun `时间无法解析的书签排到末尾而不是排到最前`() {
        val items = listOf(
            bookmark("bad", createdAt = "not-a-timestamp"),
            bookmark("ok", createdAt = "2026-10-17T00:00:00Z"),
        )
        assertEquals(listOf("ok", "bad"), sortedBookmarks(items).map { it.id })
    }

    @Test
    fun `空列表保持为空`() {
        assertEquals(emptyList(), sortedBookmarks(emptyList()))
    }

    @Test
    fun `单词收藏与段落收藏映射到不同标记`() {
        assertEquals("单词", bookmarkTypeLabel(bookmark("w", type = "word")))
        assertEquals("段落", bookmarkTypeLabel(bookmark("s", type = "sentence")))
        assertEquals(true, bookmarkIsWord(bookmark("w", type = "word")))
        assertEquals(false, bookmarkIsWord(bookmark("s", type = "sentence")))
    }
}
