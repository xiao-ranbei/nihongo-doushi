package com.nihongodoushi.app.storage

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 助词错题调度（D-022）：与动词/形容词同一套节奏，常量必须与 [ReviewPolicy] 完全一致。
 */
class ParticleReviewTest {

    private val now: Instant = Instant.parse("2026-10-17T02:00:00Z")

    private fun record(
        streak: Int = 0,
        count: Int = 1,
        interval: Int = 1,
        dueOffsetHours: Long = 24,
        sessionDue: Int? = null,
    ) = ParticleMistakeRecord(
        questionId = "wa-topic",
        count = count,
        wrongAnswer = "が",
        updatedAt = now.toString(),
        correctStreak = streak,
        intervalDays = interval,
        dueAt = now.plus(Duration.ofHours(dueOffsetHours)).toString(),
        sessionDueAtAnswerCount = sessionDue,
    )

    @Test
    fun `节奏常量与动词形容词一致`() {
        assertEquals(ReviewPolicy.INTERVALS_DAYS.toList(), ParticleReview.INTERVALS_DAYS.toList())
        assertEquals(ReviewPolicy.MASTERY_CORRECT_STREAK, ParticleReview.MASTERY_CORRECT_STREAK)
        assertEquals(ReviewPolicy.SESSION_RETRY_AFTER_ANSWERS, ParticleReview.SESSION_RETRY_AFTER_ANSWERS)
        assertEquals(ReviewPolicy.MAX_MISTAKE_WEIGHT, ParticleReview.MAX_WEIGHT)
    }

    @Test
    fun `新错题次日到期并在本会话重建重现`() {
        val created = ParticleReview.newRecord("wa-topic", "が", now, sessionAnswerCount = 3)
        assertEquals(1, created.count)
        assertEquals(0, created.correctStreak)
        assertEquals(1, created.intervalDays)
        assertEquals(now.plus(Duration.ofDays(1)), Instant.parse(created.dueAt))
        assertEquals(3 + ParticleReview.SESSION_RETRY_AFTER_ANSWERS, created.sessionDueAtAnswerCount)
    }

    @Test
    fun `再答错把进度清零并重新排到明天`() {
        val existing = record(streak = 3, count = 2, interval = 7, dueOffsetHours = 100)
        val updated = ParticleReview.afterWrong(existing, "で", now, sessionAnswerCount = 5)
        assertEquals(3, updated.count)
        assertEquals("で", updated.wrongAnswer)
        assertEquals(0, updated.correctStreak)
        assertEquals(1, updated.intervalDays)
        assertEquals(now.plus(Duration.ofDays(1)), Instant.parse(updated.dueAt))
        assertEquals(5 + ParticleReview.SESSION_RETRY_AFTER_ANSWERS, updated.sessionDueAtAnswerCount)
    }

    @Test
    fun `复习答对按 1 3 7 14 30 天推进后毕业`() {
        var current: ParticleMistakeRecord? = record()
        val intervals = mutableListOf<Int>()
        repeat(ParticleReview.MASTERY_CORRECT_STREAK - 1) { index ->
            val next = assertNotNull(
                ParticleReview.afterCorrect(current!!, now),
                "第 ${index + 1} 次答对不应该毕业",
            )
            intervals += next.intervalDays
            assertEquals(index + 1, next.correctStreak)
            assertEquals(now.plus(Duration.ofDays(next.intervalDays.toLong())), Instant.parse(next.dueAt))
            assertNull(next.sessionDueAtAnswerCount, "排期后不应再有会话内重试")
            current = next
        }
        assertEquals(listOf(1, 3, 7, 14, 30), intervals)

        val graduated = ParticleReview.afterCorrect(current!!, now)
        assertNull(graduated, "连续第 6 次答对应毕业")
    }

    @Test
    fun `到期判定的三条路径`() {
        val future = record(dueOffsetHours = 48)
        assertFalse(ParticleReview.isDue(future, now, sessionAnswerCount = 0))

        val past = record(dueOffsetHours = -1)
        assertTrue(ParticleReview.isDue(past, now, sessionAnswerCount = 0))

        val sessionDue = record(dueOffsetHours = 48, sessionDue = 4)
        assertFalse(ParticleReview.isDue(sessionDue, now, sessionAnswerCount = 3))
        assertTrue(ParticleReview.isDue(sessionDue, now, sessionAnswerCount = 4))

        val broken = record().copy(dueAt = "not-a-date")
        assertTrue(ParticleReview.isDue(broken, now, sessionAnswerCount = 0), "无法解析的日期视为到期")
    }

    @Test
    fun `抽题权重按错误次数封顶`() {
        assertEquals(1, ParticleReview.weight(record(count = 0)))
        assertEquals(2, ParticleReview.weight(record(count = 2)))
        assertEquals(3, ParticleReview.weight(record(count = 9)))
    }
}
