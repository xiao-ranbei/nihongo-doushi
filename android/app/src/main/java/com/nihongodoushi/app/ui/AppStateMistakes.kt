package com.nihongodoushi.app.ui

import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.storage.ReviewPolicy
import java.time.Instant

/**
 * 错题领域（IK-06 拆分）：动词/形容词共享错题本 + 到期判定 + 权重。
 * 函数体自 AppState.kt 原样迁入，行为不变；对齐 web recordMistake / recordSuccessfulReview。
 */

fun AppState.verbMistakes(): List<MistakeRecord> = mistakes.filter { !it.isAdjective }

fun AppState.adjectiveMistakes(): List<MistakeRecord> = mistakes.filter { it.isAdjective }

fun AppState.findMistake(key: String): MistakeRecord? = mistakes.firstOrNull { it.key == key }

fun AppState.recordVerbMistake(verb: VerbEntry, voiceId: String, inflectionId: String, answer: String) {
    recordMistake(AppState.verbMistakeKey(verb.id, voiceId, inflectionId), answer) { now ->
        MistakeRecord(
            key = AppState.verbMistakeKey(verb.id, voiceId, inflectionId),
            verbId = verb.id,
            voiceId = voiceId,
            inflectionId = inflectionId,
            count = 1,
            wrongAnswer = answer.ifBlank { "（未作答）" },
            updatedAt = now,
            correctStreak = 0,
            intervalDays = 1,
            dueAt = AppState.addDays(1),
        )
    }
}

fun AppState.recordAdjectiveMistake(adjective: AdjectiveEntry, formId: String, answer: String) {
    recordMistake(AppState.adjMistakeKey(adjective.id, formId), answer) { now ->
        MistakeRecord(
            key = AppState.adjMistakeKey(adjective.id, formId),
            module = "adjective",
            adjectiveId = adjective.id,
            formId = formId,
            count = 1,
            wrongAnswer = answer.ifBlank { "（未作答）" },
            updatedAt = now,
            correctStreak = 0,
            intervalDays = 1,
            dueAt = AppState.addDays(1),
        )
    }
}

/** 答对后的复习推进：毕业条件满足则移除，否则按间隔顺延（对齐 web recordSuccessfulReview）。 */
fun AppState.recordSuccessfulReview(key: String) {
    val mistake = findMistake(key) ?: return
    val updated = mistake.copy(
        correctStreak = mistake.correctStreak + 1,
        updatedAt = Instant.now().toString(),
        sessionDueAtAnswerCount = null,
    )
    if (updated.correctStreak >= ReviewPolicy.MASTERY_CORRECT_STREAK) {
        removeMistake(key)
        recordMastered()
        return
    }
    val interval = ReviewPolicy.INTERVALS_DAYS[updated.correctStreak - 1]
    mistakes = mistakes.map {
        if (it.key == key) {
            updated.copy(intervalDays = interval, dueAt = AppState.addDays(interval))
        } else {
            it
        }
    }
    store.saveMistakes(mistakes)
}

fun AppState.removeMistake(key: String) {
    mistakes = mistakes.filter { it.key != key }
    store.saveMistakes(mistakes)
}

fun AppState.clearMistakes() {
    mistakes = emptyList()
    store.saveMistakes(mistakes)
}

/** 到期判定：会话内重试到期或跨会话计划到期（对齐 web isReviewDue）。 */
fun AppState.isReviewDue(mistake: MistakeRecord): Boolean {
    val session = mistake.sessionDueAtAnswerCount
    if (session != null && session <= sessionAnswerCount) {
        return true
    }
    val dueAt = runCatching { Instant.parse(mistake.dueAt) }.getOrNull() ?: return true
    return !dueAt.isAfter(Instant.now())
}

/** 错题权重：按错误次数加权，封顶避免长期占用（对齐 web mistakeWeight）。 */
fun AppState.mistakeWeight(mistake: MistakeRecord): Int =
    maxOf(1, minOf(ReviewPolicy.MAX_MISTAKE_WEIGHT, mistake.count))

// ---------- 内部 ----------

internal fun AppState.recordMistake(key: String, answer: String, build: (String) -> MistakeRecord) {
    val existing = findMistake(key)
    val base = existing?.copy(
        count = existing.count + 1,
        wrongAnswer = answer.ifBlank { "（未作答）" },
        updatedAt = Instant.now().toString(),
    ) ?: build(Instant.now().toString())
    val updated = base.copy(
        correctStreak = 0,
        intervalDays = 1,
        dueAt = AppState.addDays(1),
        sessionDueAtAnswerCount = sessionAnswerCount + ReviewPolicy.SESSION_RETRY_AFTER_ANSWERS,
    )
    mistakes = if (existing == null) mistakes + updated else mistakes.map { if (it.key == key) updated else it }
    store.saveMistakes(mistakes)
}
