package com.nihongodoushi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.storage.DailyRecord
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.storage.ModuleStats
import com.nihongodoushi.app.storage.ReviewPolicy
import java.time.Instant
import java.time.LocalDate

/**
 * 全局共享状态：错题、两模块统计与每日打卡的内存副本 + 落盘。
 * 行为逐条对齐 web/app.js（recordMistake / recordSuccessfulReview / updateDailyStreak）。
 */
class AppState(private val store: LocalStore) {

    var mistakes by mutableStateOf(store.loadMistakes())
        private set
    var verbStats by mutableStateOf(store.loadVerbStats())
        private set
    var adjectiveStats by mutableStateOf(store.loadAdjectiveStats())
        private set
    var daily by mutableStateOf(store.loadDaily())
        private set

    /** 读取到脏数据时的提示标记（UI 层消费一次即可） */
    var loadInvalid by mutableStateOf(store.lastLoadInvalid)
        private set

    /** 会话内已提交作答数（跨模块共享，对应 web 端 sessionAnswerCount） */
    var sessionAnswerCount: Int = 0
        private set

    fun onAnswerSubmitted() {
        sessionAnswerCount += 1
    }

    // ---------- 错题 ----------

    fun verbMistakes(): List<MistakeRecord> = mistakes.filter { !it.isAdjective }

    fun adjectiveMistakes(): List<MistakeRecord> = mistakes.filter { it.isAdjective }

    fun findMistake(key: String): MistakeRecord? = mistakes.firstOrNull { it.key == key }

    fun recordVerbMistake(verb: VerbEntry, voiceId: String, inflectionId: String, answer: String) {
        recordMistake(verbMistakeKey(verb.id, voiceId, inflectionId), answer) { now ->
            MistakeRecord(
                key = verbMistakeKey(verb.id, voiceId, inflectionId),
                verbId = verb.id,
                voiceId = voiceId,
                inflectionId = inflectionId,
                count = 1,
                wrongAnswer = answer.ifBlank { "（未作答）" },
                updatedAt = now,
                correctStreak = 0,
                intervalDays = 1,
                dueAt = addDays(1),
            )
        }
    }

    fun recordAdjectiveMistake(adjective: AdjectiveEntry, formId: String, answer: String) {
        recordMistake(adjMistakeKey(adjective.id, formId), answer) { now ->
            MistakeRecord(
                key = adjMistakeKey(adjective.id, formId),
                module = "adjective",
                adjectiveId = adjective.id,
                formId = formId,
                count = 1,
                wrongAnswer = answer.ifBlank { "（未作答）" },
                updatedAt = now,
                correctStreak = 0,
                intervalDays = 1,
                dueAt = addDays(1),
            )
        }
    }

    /** 答对后的复习推进：毕业条件满足则移除，否则按间隔顺延（对齐 web recordSuccessfulReview）。 */
    fun recordSuccessfulReview(key: String) {
        val mistake = findMistake(key) ?: return
        val updated = mistake.copy(
            correctStreak = mistake.correctStreak + 1,
            updatedAt = Instant.now().toString(),
            sessionDueAtAnswerCount = null,
        )
        if (updated.correctStreak >= ReviewPolicy.MASTERY_CORRECT_STREAK) {
            removeMistake(key)
            return
        }
        val interval = ReviewPolicy.INTERVALS_DAYS[updated.correctStreak - 1]
        mistakes = mistakes.map {
            if (it.key == key) {
                updated.copy(intervalDays = interval, dueAt = addDays(interval))
            } else {
                it
            }
        }
        store.saveMistakes(mistakes)
    }

    fun removeMistake(key: String) {
        mistakes = mistakes.filter { it.key != key }
        store.saveMistakes(mistakes)
    }

    fun clearMistakes() {
        mistakes = emptyList()
        store.saveMistakes(mistakes)
    }

    // ---------- 统计 ----------

    fun updateVerbStats(correct: Boolean) {
        verbStats = bumpStats(verbStats, correct)
        store.saveVerbStats(verbStats)
    }

    fun updateAdjectiveStats(correct: Boolean) {
        adjectiveStats = bumpStats(adjectiveStats, correct)
        store.saveAdjectiveStats(adjectiveStats)
    }

    fun resetVerbStats() {
        verbStats = ModuleStats()
        store.saveVerbStats(verbStats)
    }

    fun resetAdjectiveStats() {
        adjectiveStats = ModuleStats()
        store.saveAdjectiveStats(adjectiveStats)
    }

    // ---------- 每日打卡 ----------

    /** 连续天数：今天或昨天有记录才连续（对齐 web getCurrentStreak）。 */
    fun currentStreak(): Int {
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        return if (daily.lastDate == today || daily.lastDate == yesterday) daily.streak else 0
    }

    fun updateDaily(module: String) {
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
            else -> error("Unknown daily activity module: $module")
        }
        daily = record
        store.saveDaily(record)
    }

    // ---------- 调度辅助 ----------

    /** 到期判定：会话内重试到期或跨会话计划到期（对齐 web isReviewDue）。 */
    fun isReviewDue(mistake: MistakeRecord): Boolean {
        val session = mistake.sessionDueAtAnswerCount
        if (session != null && session <= sessionAnswerCount) {
            return true
        }
        val dueAt = runCatching { Instant.parse(mistake.dueAt) }.getOrNull() ?: return true
        return !dueAt.isAfter(Instant.now())
    }

    /** 错题权重：按错误次数加权，封顶避免长期占用（对齐 web mistakeWeight）。 */
    fun mistakeWeight(mistake: MistakeRecord): Int =
        maxOf(1, minOf(ReviewPolicy.MAX_MISTAKE_WEIGHT, mistake.count))

    // ---------- 内部 ----------

    private fun recordMistake(key: String, answer: String, build: (String) -> MistakeRecord) {
        val existing = findMistake(key)
        val base = existing?.copy(
            count = existing.count + 1,
            wrongAnswer = answer.ifBlank { "（未作答）" },
            updatedAt = Instant.now().toString(),
        ) ?: build(Instant.now().toString())
        val updated = base.copy(
            correctStreak = 0,
            intervalDays = 1,
            dueAt = addDays(1),
            sessionDueAtAnswerCount = sessionAnswerCount + ReviewPolicy.SESSION_RETRY_AFTER_ANSWERS,
        )
        mistakes = if (existing == null) mistakes + updated else mistakes.map { if (it.key == key) updated else it }
        store.saveMistakes(mistakes)
    }

    private fun bumpStats(stats: ModuleStats, correct: Boolean): ModuleStats =
        if (correct) stats.copy(correct = stats.correct + 1) else stats.copy(incorrect = stats.incorrect + 1)

    companion object {
        fun verbMistakeKey(verbId: String, voiceId: String, inflectionId: String): String =
            listOf(verbId, voiceId, inflectionId).joinToString("::")

        fun adjMistakeKey(adjectiveId: String, formId: String): String =
            "adj::" + listOf(adjectiveId, formId).joinToString("::")

        /** 对应 web 端 addDays：以当前时刻加 N 天的 ISO 串存储 */
        fun addDays(days: Int): String =
            Instant.now().plus(java.time.Duration.ofDays(days.toLong())).toString()

        val verbById: Map<String, VerbEntry> = VerbData.VERBS.associateBy { it.id }
        val adjectiveById: Map<String, AdjectiveEntry> = AdjectiveData.ADJECTIVES.associateBy { it.id }
    }
}
