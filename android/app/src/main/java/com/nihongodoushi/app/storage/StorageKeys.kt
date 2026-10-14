package com.nihongodoushi.app.storage

/**
 * 本地存储键（与 Web 端 localStorage 键一一对应，D-015）。
 * Android 端每个键对应 filesDir 下的同名 .json 文件。
 */
object StorageKeys {
    const val MISTAKES = "japanese-verb-practice-mistakes-v1"
    const val VERB_STATS = "japanese-verb-practice-stats-v1"
    const val ADJECTIVE_STATS = "japanese-verb-practice-adj-stats-v1"
    const val DAILY = "japanese-verb-practice-daily-v1"
}

/** 间隔复习常量（与 Web 端 app.js 一致） */
object ReviewPolicy {
    /** 连续答对后的复习间隔（天） */
    val INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30)

    /** 连续正确达到该次数后错题毕业移除 */
    const val MASTERY_CORRECT_STREAK = 6

    /** 会话内重试：再答若干题后到期错题重新出现 */
    const val SESSION_RETRY_AFTER_ANSWERS = 4
}
