package com.nihongodoushi.app.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 错题记录：字段名与 Web 端 localStorage 中的 JSON 逐字段对齐。
 * 动词记录无 module 字段（旧版兼容语义），形容词记录 module = "adjective"。
 */
@Serializable
data class MistakeRecord(
    val key: String,
    val module: String? = null,
    val verbId: String? = null,
    val voiceId: String? = null,
    val inflectionId: String? = null,
    val adjectiveId: String? = null,
    val formId: String? = null,
    val count: Int = 0,
    val wrongAnswer: String = "",
    val updatedAt: String = "",
    val correctStreak: Int = 0,
    val intervalDays: Int = 1,
    val dueAt: String = "",
    @SerialName("sessionDueAtAnswerCount")
    val sessionDueAtAnswerCount: Int? = null,
) {
    val isAdjective: Boolean get() = module == "adjective"
    val refId: String? get() = if (isAdjective) adjectiveId else verbId
}

/** 答题统计（动词与形容词同构）：与 Web 端 { correct, incorrect } 对齐 */
@Serializable
data class ModuleStats(
    val correct: Int = 0,
    val incorrect: Int = 0,
)

/**
 * 每日打卡记录：与 Web 端 daily-v1 结构对齐。
 * lastDate 为本地日期字符串 "YYYY-MM-DD"（以设备本地时区为准）。
 */
@Serializable
data class DailyRecord(
    val lastDate: String = "",
    val streak: Int = 0,
    val best: Int = 0,
    val verbAnswers: Int = 0,
    val adjectiveAnswers: Int = 0,
)
