package com.nihongodoushi.app.storage

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 学习历史的聚合计算（纯逻辑，无 Android 依赖，可单测）：
 * 周历条、词库覆盖率、时长文案、历史裁剪。
 */

/** 周历条上的一格 */
data class DayCell(
    val date: String,
    val label: String,
    val answers: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
)

object ActivityAggregation {

    /** 历史保留天数（每日写入时裁剪，避免文件无限增长） */
    const val HISTORY_DAYS = 14

    private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 写入某天并裁剪到最近 [HISTORY_DAYS] 天（按日期升序返回） */
    fun upsert(history: ActivityHistory, day: DayActivity): ActivityHistory {
        val merged = history.days.filter { it.date != day.date } + day
        return ActivityHistory(
            days = merged.sortedBy { it.date }.takeLast(HISTORY_DAYS),
        )
    }

    /** 取某天的记录，不存在则返回该日期的零值 */
    fun dayOrEmpty(history: ActivityHistory, date: String): DayActivity =
        history.dayOf(date) ?: DayActivity(date = date)

    /** 本周（周一为起点）七天的视图模型；今天的格子带 isToday */
    fun weekStrip(history: ActivityHistory, today: LocalDate): List<DayCell> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val key = date.toString()
            DayCell(
                date = key,
                label = if (date == today) "今天" else WEEK_LABELS[offset],
                answers = history.answersOn(key),
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }

    /** 覆盖率（0..1）；总数不大于 0 时返回 0 */
    fun coverage(practiced: Int, total: Int): Float =
        if (total <= 0) 0f else (practiced.toFloat() / total).coerceIn(0f, 1f)

    /** 时长文案：<1 分钟显示「不足 1 分」，<60 分钟显示「n 分」，否则「n 小时 m 分」 */
    fun durationText(seconds: Int): String = when {
        seconds <= 0 -> "0 分"
        seconds < 60 -> "不足 1 分"
        seconds < 3600 -> "${seconds / 60} 分"
        else -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            if (minutes == 0) "$hours 小时" else "$hours 小时 $minutes 分"
        }
    }

    /** 时长数字部分（配合单位展示）：不足 1 分钟显示秒，避免刚练完却显示「0 分钟」 */
    fun durationParts(seconds: Int): Pair<String, String> = when {
        seconds < 60 -> seconds.toString() to "秒"
        seconds < 3600 -> (seconds / 60).toString() to "分钟"
        else -> (seconds / 3600).toString() to "小时"
    }
}
