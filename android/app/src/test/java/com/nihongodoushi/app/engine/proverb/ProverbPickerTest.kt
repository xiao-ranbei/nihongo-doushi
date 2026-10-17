package com.nihongodoushi.app.engine.proverb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 谚语抽取纯逻辑测试：种子确定性 / 不连续重复 / 索引边界 / 数据完整性。 */
class ProverbPickerTest {

    private val size = ProverbData.PROVERBS.size

    @Test
    fun `同种子随机结果确定可复现`() {
        val first = ProverbPicker.initial(Random(42), size)
        val second = ProverbPicker.initial(Random(42), size)
        assertEquals(first, second, "同种子同 size 的 initial 结果必须一致")

        val r1 = Random(42)
        val r2 = Random(42)
        var c1 = first
        var c2 = second
        repeat(50) {
            c1 = ProverbPicker.next(c1, size, r1)
            c2 = ProverbPicker.next(c2, size, r2)
            assertEquals(c1, c2, "同种子的 next 序列必须逐步一致")
        }
    }

    @Test
    fun `next 连续二百次抽签永不等于当前索引`() {
        val random = Random(42)
        var current = ProverbPicker.initial(random, size)
        repeat(200) {
            val next = ProverbPicker.next(current, size, random)
            assertTrue(next != current, "第 $it 次换一句出现连续重复：$next == $current")
            assertTrue(next in 0 until size, "索引越界：$next")
            current = next
        }
    }

    @Test
    fun `size 为 2 时 next 恒取另一边`() {
        val random = Random(7)
        repeat(100) { i ->
            val current = i % 2
            assertEquals(1 - current, ProverbPicker.next(current, 2, random))
        }
    }

    @Test
    fun `initial 边界 size 为 0 与 1 时回退 0`() {
        assertEquals(0, ProverbPicker.initial(Random(42), 0))
        assertEquals(0, ProverbPicker.initial(Random(42), 1))
        assertEquals(0, ProverbPicker.initial(Random(42), -3), "负 size 也应回退 0")
    }

    @Test
    fun `initial 边界 size 为 25 时索引落在合法区间`() {
        val value = ProverbPicker.initial(Random(42), 25)
        assertTrue(value in 0 until 25, "size=25 时索引应在 0..24：$value")
    }

    @Test
    fun `size 小于等于 1 时 next 原样返回 current`() {
        assertEquals(5, ProverbPicker.next(5, 1, Random(42)))
        assertEquals(0, ProverbPicker.next(0, 0, Random(42)))
    }

    @Test
    fun `谚语数据共二十四条且 id 唯一按序编号`() {
        assertEquals(24, size)
        assertEquals(size, ProverbData.PROVERBS.map { it.id }.toSet().size, "id 必须唯一")
        ProverbData.PROVERBS.forEachIndexed { index, proverb ->
            assertEquals("proverb-" + (index + 1).toString().padStart(2, '0'), proverb.id)
        }
    }

    @Test
    fun `谚语数据原文读音大意全部非空`() {
        ProverbData.PROVERBS.forEach { proverb ->
            assertTrue(proverb.text.isNotBlank(), "${proverb.id} 原文为空")
            assertTrue(proverb.reading.isNotBlank(), "${proverb.id} 读音为空")
            assertTrue(proverb.meaning.isNotBlank(), "${proverb.id} 大意为空")
        }
    }
}
