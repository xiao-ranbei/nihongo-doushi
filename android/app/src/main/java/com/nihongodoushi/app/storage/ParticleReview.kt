package com.nihongodoushi.app.storage

import java.time.Duration
import java.time.Instant

/**
 * 助词错题的复习调度（D-022）。与动词/形容词共用同一套节奏，只是作用在
 * [ParticleMistakeRecord] 上：
 * - 答错：错误次数 +1，连续正确清零，次日到期，并在本会话再答 4 题后重现；
 * - 复习答对：连续正确 +1，间隔按 1/3/7/14/30 天推进；
 * - 连续答对 6 次：毕业，从错题本移除。
 *
 * 纯函数：不碰存储、不读时钟（`now` 由调用方传入），可直接单测。
 * 常量与 [ReviewPolicy] 的对应值由 ParticleReviewTest 断言相等，避免两套节奏漂移。
 */
object ParticleReview {

    val INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30)

    const val MASTERY_CORRECT_STREAK = 6

    const val SESSION_RETRY_AFTER_ANSWERS = 4

    /** 抽题权重上限：错得多的题优先，但不会长期占位 */
    const val MAX_WEIGHT = 3

    fun newRecord(
        questionId: String,
        wrongAnswer: String,
        now: Instant,
        sessionAnswerCount: Int,
    ): ParticleMistakeRecord = ParticleMistakeRecord(
        questionId = questionId,
        count = 1,
        wrongAnswer = wrongAnswer,
        updatedAt = now.toString(),
        correctStreak = 0,
        intervalDays = 1,
        dueAt = now.plus(Duration.ofDays(1)).toString(),
        sessionDueAtAnswerCount = sessionAnswerCount + SESSION_RETRY_AFTER_ANSWERS,
    )

    fun afterWrong(
        record: ParticleMistakeRecord,
        wrongAnswer: String,
        now: Instant,
        sessionAnswerCount: Int,
    ): ParticleMistakeRecord = record.copy(
        count = record.count + 1,
        wrongAnswer = wrongAnswer.ifBlank { record.wrongAnswer },
        updatedAt = now.toString(),
        correctStreak = 0,
        intervalDays = 1,
        dueAt = now.plus(Duration.ofDays(1)).toString(),
        sessionDueAtAnswerCount = sessionAnswerCount + SESSION_RETRY_AFTER_ANSWERS,
    )

    /** 复习答对：返回新记录；返回 null 表示已毕业，应从错题本移除。 */
    fun afterCorrect(
        record: ParticleMistakeRecord,
        now: Instant,
    ): ParticleMistakeRecord? {
        val nextStreak = record.correctStreak + 1
        if (nextStreak >= MASTERY_CORRECT_STREAK) {
            return null
        }
        val interval = INTERVALS_DAYS[nextStreak - 1]
        return record.copy(
            correctStreak = nextStreak,
            intervalDays = interval,
            dueAt = now.plus(Duration.ofDays(interval.toLong())).toString(),
            updatedAt = now.toString(),
            sessionDueAtAnswerCount = null,
        )
    }

    /** 到期判定：会话内重试到期，或跨会话计划到期。`dueAt` 无法解析时视为到期（不吞题）。 */
    fun isDue(
        record: ParticleMistakeRecord,
        now: Instant,
        sessionAnswerCount: Int,
    ): Boolean {
        val sessionDue = record.sessionDueAtAnswerCount
        if (sessionDue != null && sessionDue <= sessionAnswerCount) {
            return true
        }
        val due = runCatching { Instant.parse(record.dueAt) }.getOrNull() ?: return true
        return !due.isAfter(now)
    }

    fun weight(record: ParticleMistakeRecord): Int =
        maxOf(1, minOf(MAX_WEIGHT, record.count))
}
