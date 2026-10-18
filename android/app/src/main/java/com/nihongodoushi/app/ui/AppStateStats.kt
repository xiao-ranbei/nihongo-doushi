package com.nihongodoushi.app.ui

import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.storage.DayActivity
import com.nihongodoushi.app.storage.ModuleStats
import java.time.LocalDate

/**
 * 统计与采集领域（IK-06 拆分）：模块对错统计 + 每日打卡 + 学习行为采集（history-v1）。
 * 函数体自 AppState.kt 原样迁入，行为不变；对齐 web updateDailyStreak / getCurrentStreak。
 */

// ---------- 统计 ----------

fun AppState.updateVerbStats(correct: Boolean) {
    verbStats = bumpStats(verbStats, correct)
    store.saveVerbStats(verbStats)
}

fun AppState.updateAdjectiveStats(correct: Boolean) {
    adjectiveStats = bumpStats(adjectiveStats, correct)
    store.saveAdjectiveStats(adjectiveStats)
}

fun AppState.resetVerbStats() {
    verbStats = ModuleStats()
    store.saveVerbStats(verbStats)
}

fun AppState.resetAdjectiveStats() {
    adjectiveStats = ModuleStats()
    store.saveAdjectiveStats(adjectiveStats)
}

internal fun AppState.bumpStats(stats: ModuleStats, correct: Boolean): ModuleStats =
    if (correct) stats.copy(correct = stats.correct + 1) else stats.copy(incorrect = stats.incorrect + 1)

// ---------- 每日打卡 ----------

/** 连续天数：今天或昨天有记录才连续（对齐 web getCurrentStreak）。 */
fun AppState.currentStreak(): Int {
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()
    return if (daily.lastDate == today || daily.lastDate == yesterday) daily.streak else 0
}

fun AppState.updateDaily(module: String) {
    val today = LocalDate.now()
    var record = daily
    if (record.lastDate != today.toString()) {
        val streak = if (record.lastDate == today.minusDays(1).toString()) record.streak + 1 else 1
        record = record.copy(
            lastDate = today.toString(),
            streak = streak,
            best = maxOf(record.best, streak),
            verbAnswers = 0,
            adjectiveAnswers = 0,
        )
    }
    record = when (module) {
        "verb" -> record.copy(verbAnswers = record.verbAnswers + 1)
        "adjective" -> record.copy(adjectiveAnswers = record.adjectiveAnswers + 1)
        "particle" -> record.copy(particleAnswers = record.particleAnswers + 1)
        else -> error("Unknown daily activity module: $module")
    }
    daily = record
    store.saveDaily(record)

    // 同步写入 history-v1：今日答题数（供主页周历条与「我的」页）
    updateToday { day ->
        when (module) {
            "verb" -> day.copy(verbAnswers = day.verbAnswers + 1)
            "adjective" -> day.copy(adjectiveAnswers = day.adjectiveAnswers + 1)
            "particle" -> day.copy(particleAnswers = day.particleAnswers + 1)
            else -> day
        }
    }
}

// ---------- 采集：学习时长 / 完成任务数 ----------

internal fun AppState.todayKey(): String = LocalDate.now().toString()

fun AppState.todayActivity(): DayActivity = ActivityAggregation.dayOrEmpty(history, todayKey())

internal fun AppState.updateToday(transform: (DayActivity) -> DayActivity) {
    val key = todayKey()
    val current = ActivityAggregation.dayOrEmpty(history, key)
    val updated = ActivityAggregation.upsert(history, transform(current))
    history = updated
    store.saveHistory(updated)
}

/** 练习屏前台计时累计（秒）；不足 1 秒的调用请由调用方过滤 */
fun AppState.addStudySeconds(seconds: Int) {
    if (seconds <= 0) return
    updateToday { it.copy(seconds = it.seconds + seconds) }
}

/** 完成一轮练习（或完成一次错题重练）记 1 个任务 */
fun AppState.addCompletedTask() {
    updateToday { it.copy(tasks = it.tasks + 1) }
}
