package com.nihongodoushi.app.storage

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 学习历史聚合的纯逻辑测试（周历条 / 裁剪 / 覆盖率 / 时长文案）。 */
class ActivityAggregationTest {

    private val today = LocalDate.of(2026, 10, 15) // 周四

    @Test
    fun `周历条为本周七格且今天被标记`() {
        val history = ActivityHistory(
            days = listOf(
                DayActivity("2026-10-12", verbAnswers = 12),
                DayActivity("2026-10-13", verbAnswers = 8, adjectiveAnswers = 2),
                DayActivity("2026-10-15", verbAnswers = 5, adjectiveAnswers = 3),
            ),
        )
        val strip = ActivityAggregation.weekStrip(history, today)

        assertEquals(7, strip.size)
        assertEquals(listOf("一", "二", "三", "今天", "五", "六", "日"), strip.map { it.label })
        assertEquals("2026-10-12", strip.first().date)
        assertEquals("2026-10-18", strip.last().date)

        val cellToday = strip.single { it.isToday }
        assertEquals("2026-10-15", cellToday.date)
        assertEquals("今天", cellToday.label)
        assertEquals(8, cellToday.answers)

        assertEquals(12, strip[0].answers)
        assertEquals(10, strip[1].answers)
        assertEquals(0, strip[2].answers)
        assertTrue(strip.drop(4).all { it.isFuture }, "今天之后的格子应标记为未来")
        assertTrue(strip.take(4).none { it.isFuture })
    }

    @Test
    fun `同一天重复写入只保留一条且字段累加由调用方负责`() {
        var history = ActivityHistory()
        history = ActivityAggregation.upsert(history, DayActivity("2026-10-15", verbAnswers = 1))
        history = ActivityAggregation.upsert(history, DayActivity("2026-10-15", verbAnswers = 2, seconds = 60))
        history = ActivityAggregation.upsert(history, DayActivity("2026-10-14", verbAnswers = 9))

        assertEquals(2, history.days.size, "同一天应只保留一条：${history.days}")
        assertEquals(listOf("2026-10-14", "2026-10-15"), history.days.map { it.date }, "应按日期升序")
        assertEquals(2, history.answersOn("2026-10-15"))
        assertEquals(60, history.dayOf("2026-10-15")?.seconds)
    }

    @Test
    fun `历史只保留最近十四天`() {
        var history = ActivityHistory()
        for (offset in 0 until 20) {
            val date = today.minusDays(offset.toLong()).toString()
            history = ActivityAggregation.upsert(history, DayActivity(date, verbAnswers = 1))
        }
        assertEquals(ActivityAggregation.HISTORY_DAYS, history.days.size)
        assertEquals(today.toString(), history.days.last().date)
        assertTrue(history.dayOf(today.minusDays(19).toString()) == null, "超出保留期的日期应被裁剪")
    }

    @Test
    fun `覆盖率计算与边界`() {
        assertEquals(0f, ActivityAggregation.coverage(0, 0))
        assertEquals(0f, ActivityAggregation.coverage(5, 0))
        assertEquals(1f, ActivityAggregation.coverage(59, 59))
        assertEquals(1f, ActivityAggregation.coverage(70, 59), "结果应被裁剪到 1")
        assertTrue(kotlin.math.abs(ActivityAggregation.coverage(18, 59) - 0.305f) < 0.001f)
    }

    @Test
    fun `时长文案与数字单位`() {
        assertEquals("0 分", ActivityAggregation.durationText(0))
        assertEquals("不足 1 分", ActivityAggregation.durationText(45))
        assertEquals("24 分", ActivityAggregation.durationText(24 * 60))
        assertEquals("1 小时", ActivityAggregation.durationText(3600))
        assertEquals("1 小时 12 分", ActivityAggregation.durationText(3600 + 12 * 60))

        assertEquals("24" to "分钟", ActivityAggregation.durationParts(24 * 60))
        assertEquals("30" to "秒", ActivityAggregation.durationParts(30), "不足 1 分钟应显示秒")
        assertEquals("0" to "秒", ActivityAggregation.durationParts(0))
        assertEquals("1" to "小时", ActivityAggregation.durationParts(3600))
    }
}
