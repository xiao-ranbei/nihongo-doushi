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
    /** 助词模块当日题数；旧存档无该字段时按 0 兼容 */
    val particleAnswers: Int = 0,
)

/** 单日学习行为（history-v1 的一天） */
@Serializable
data class DayActivity(
    val date: String,
    val verbAnswers: Int = 0,
    val adjectiveAnswers: Int = 0,
    val particleAnswers: Int = 0,
    /** 该日前台学习秒数（进练习屏累计，退到后台暂停） */
    val seconds: Int = 0,
    /** 该日完成任务数（一轮 5 题记 1 个；完成一次错题重练也记 1 个） */
    val tasks: Int = 0,
) {
    val answers: Int get() = verbAnswers + adjectiveAnswers + particleAnswers
}

/** 最近若干天的学习历史（history-v1），按日期升序保存 */
@Serializable
data class ActivityHistory(
    val days: List<DayActivity> = emptyList(),
) {
    fun dayOf(date: String): DayActivity? = days.firstOrNull { it.date == date }

    /** 今日答题总量（动词 + 形容词） */
    fun answersOn(date: String): Int = dayOf(date)?.answers ?: 0
}

/**
 * 助词错题记录（D-022）：字段与 [MistakeRecord] 对齐，只是把动词/形容词的
 * 「词条 + 形式」换成单一 `questionId`。旧版（v1）存的是纯题目 ID 数组，读取时迁移。
 */
@Serializable
data class ParticleMistakeRecord(
    val questionId: String,
    val count: Int = 1,
    val wrongAnswer: String = "",
    val updatedAt: String = "",
    val correctStreak: Int = 0,
    val intervalDays: Int = 1,
    val dueAt: String = "",
    val sessionDueAtAnswerCount: Int? = null,
)

/** 学习进度（practice-v1）：已练词条集合 + 错题毕业数（用于「词库覆盖」与「错题掌握」） */
@Serializable
data class PracticeProgress(
    val verbIds: List<String> = emptyList(),
    val adjectiveIds: List<String> = emptyList(),
    /** 连续答对达到毕业次数、已从错题本移除的错题数 */
    val masteredMistakes: Int = 0,
    /** 助词：「未用提示答对过」的题目 ID 集合（掌握度口径，见 D-020） */
    val particleMastered: List<String> = emptyList(),
    /** 动词：「未用提示答对过」的组合 `voiceId::inflectionId::verbId`（三层改造掌握度口径） */
    val verbMastered: List<String> = emptyList(),
    /** 形容词：「未用提示答对过」的组合 `formId::adjectiveId` */
    val adjMastered: List<String> = emptyList(),
)
