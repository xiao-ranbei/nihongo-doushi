package com.nihongodoushi.app.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 昵称规则与书签规则纯逻辑测试：校验三态 / 防御回落 / 书签合法性 / 增删去重。 */
class ProfileBookmarkTest {

    // ---------- ProfileRules.check ----------

    @Test
    fun `昵称为空或纯空白时校验拒绝`() {
        val empty = ProfileRules.check("")
        assertIs<NicknameCheck.Invalid>(empty)
        assertEquals("昵称不能为空", empty.reason)

        val spaces = ProfileRules.check("   ")
        assertIs<NicknameCheck.Invalid>(spaces)
        assertEquals("昵称不能为空", spaces.reason)

        val fullWidth = ProfileRules.check("　")
        assertIs<NicknameCheck.Invalid>(fullWidth)
        assertEquals("昵称不能为空", fullWidth.reason)
    }

    @Test
    fun `昵称超过十二个字时校验拒绝`() {
        val tooLong = ProfileRules.check("あ".repeat(13))
        assertIs<NicknameCheck.Invalid>(tooLong)
        assertEquals("昵称长度不能超过 12 个字", tooLong.reason)
    }

    @Test
    fun `昵称恰好十二个字时合法`() {
        val ok = ProfileRules.check("あ".repeat(12))
        assertIs<NicknameCheck.Ok>(ok)
        assertEquals("あ".repeat(12), ok.value)
    }

    @Test
    fun `昵称校验会去除首尾空白`() {
        val ok = ProfileRules.check("  小林  ")
        assertIs<NicknameCheck.Ok>(ok)
        assertEquals("小林", ok.value)
    }

    // ---------- ProfileRules.sanitize ----------

    @Test
    fun `sanitize 空白或超长一律回落缺省昵称`() {
        assertEquals(ProfileRules.DEFAULT_NICKNAME, ProfileRules.sanitize("   "))
        assertEquals(ProfileRules.DEFAULT_NICKNAME, ProfileRules.sanitize("あ".repeat(20)))
        assertEquals(ProfileRules.DEFAULT_NICKNAME, ProfileRules.sanitize(""))
        assertEquals("小林", ProfileRules.sanitize("  小林 "))
    }

    @Test
    fun `缺省昵称为日语学习者`() {
        assertEquals("日语学习者", ProfileRules.DEFAULT_NICKNAME)
        assertEquals(12, ProfileRules.MAX_LENGTH)
    }

    // ---------- BookmarkRules.isValid ----------

    private fun bookmark(
        id: String = "sentence:verb-01",
        type: String = "sentence",
        refId: String = "verb-01",
        title: String = "急がば回れ",
        note: String = "",
        createdAt: String = "2026-10-17T00:00:00Z",
    ) = Bookmark(id, type, refId, title, note, createdAt)

    @Test
    fun `字段齐全且类型合法时间可解析的书签合法`() {
        assertTrue(BookmarkRules.isValid(bookmark()))
        assertTrue(BookmarkRules.isValid(bookmark(type = "word", note = "备注可为空")))
    }

    @Test
    fun `书签类型不在白名单内不合法`() {
        assertFalse(BookmarkRules.isValid(bookmark(type = "kanji")))
        assertFalse(BookmarkRules.isValid(bookmark(type = "")))
    }

    @Test
    fun `书签关键字段为空不合法`() {
        assertFalse(BookmarkRules.isValid(bookmark(id = " ")))
        assertFalse(BookmarkRules.isValid(bookmark(refId = "")))
        assertFalse(BookmarkRules.isValid(bookmark(title = "")))
    }

    @Test
    fun `书签时间戳不可解析不合法`() {
        assertFalse(BookmarkRules.isValid(bookmark(createdAt = "not-a-timestamp")))
        assertFalse(BookmarkRules.isValid(bookmark(createdAt = "")))
    }

    // ---------- BookmarkRules.add / remove ----------

    @Test
    fun `书签新增同 id 覆盖且保持原顺序位置`() {
        val a = bookmark(id = "a", title = "A")
        val b = bookmark(id = "b", title = "B")
        val c = bookmark(id = "c", title = "C")
        val list = BookmarkRules.add(BookmarkRules.add(BookmarkList(), a), b)
        list.items.size.let { assertEquals(2, it) }

        val replaced = BookmarkRules.add(list, c)
        assertEquals(listOf("a", "b", "c"), replaced.items.map { it.id })

        val b2 = bookmark(id = "b", title = "B2")
        val overwritten = BookmarkRules.add(replaced, b2)
        assertEquals(3, overwritten.items.size)
        assertEquals(listOf("a", "b", "c"), overwritten.items.map { it.id }, "顺序位置不变")
        assertEquals("B2", overwritten.items[1].title, "后者覆盖前者")
    }

    @Test
    fun `书签移除不存在 id 时原样返回`() {
        val list = BookmarkList(listOf(bookmark(id = "a"), bookmark(id = "b")))
        assertEquals(list, BookmarkRules.remove(list, "nope"))
    }

    @Test
    fun `书签移除命中 id 时只剩其余项`() {
        val list = BookmarkList(listOf(bookmark(id = "a"), bookmark(id = "b")))
        val removed = BookmarkRules.remove(list, "a")
        assertEquals(listOf("b"), removed.items.map { it.id })
    }

    @Test
    fun `书签白名单只含 sentence 与 word`() {
        assertEquals(setOf("sentence", "word"), BookmarkRules.ALLOWED_TYPES)
        assertEquals(500, BookmarkRules.MAX_ITEMS)
    }
}
