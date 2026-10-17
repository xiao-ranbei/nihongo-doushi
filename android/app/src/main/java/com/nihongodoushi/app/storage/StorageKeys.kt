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

    /** 助词错题（v2，D-022）：带复习字段的错题记录；v1 仅作迁移来源读取 */
    const val PARTICLE_MISTAKES = "japanese-particle-practice-mistakes-v2"

    /** 助词错题旧结构（纯题目 ID 数组），迁移后原样保留作为备份 */
    const val PARTICLE_MISTAKES_LEGACY = "japanese-particle-practice-mistakes-v1"

    /** 助词答题统计：与 Web 端同构 */
    const val PARTICLE_STATS = "japanese-particle-practice-stats-v1"

    /** 最近若干天学习行为（答题数 / 时长 / 任务数），供主页周历条与「我的」页统计 */
    const val HISTORY = "japanese-verb-practice-history-v1"

    /** 已练过的词条集合，供「词库覆盖进度」 */
    const val PRACTICE = "japanese-verb-practice-progress-v1"

    /** 用户资料（profile-v1）：当前仅昵称 */
    const val PROFILE = "japanese-verb-practice-profile-v1"

    /** 书签收藏（bookmarks-v1）：阅读模块上线前只有存储骨架，无写入入口 */
    const val BOOKMARKS = "japanese-verb-practice-bookmarks-v1"
}

/** 间隔复习常量（与 Web 端 app.js 一致） */
object ReviewPolicy {
    /** 连续答对后的复习间隔（天） */
    val INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30)

    /** 连续正确达到该次数后错题毕业移除 */
    const val MASTERY_CORRECT_STREAK = 6

    /** 会话内重试：再答若干题后到期错题重新出现 */
    const val SESSION_RETRY_AFTER_ANSWERS = 4

    /** 错题加权上限（对应 web 端 MAX_MISTAKE_WEIGHT），避免单题长期占用 */
    const val MAX_MISTAKE_WEIGHT = 3
}
