package com.nihongodoushi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.storage.ActivityHistory
import com.nihongodoushi.app.storage.DailyRecord
import com.nihongodoushi.app.storage.DayActivity
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.storage.ModuleStats
import com.nihongodoushi.app.storage.ParticleMistakeRecord
import com.nihongodoushi.app.storage.ParticleReview
import com.nihongodoushi.app.storage.PracticeProgress
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

    /** 助词错题：带复习字段的记录（v2，D-022；v1 纯 ID 数组由 LocalStore 迁移） */
    var particleMistakes by mutableStateOf(
        store.loadParticleMistakes().filter { ParticleData.QUESTION_BY_ID.containsKey(it.questionId) },
    )
        private set

    var particleStats by mutableStateOf(store.loadParticleStats())
        private set
    var daily by mutableStateOf(store.loadDaily())
        private set

    /** 最近若干天学习行为（history-v1）：主页周历条与「我的」页统计的数据源 */
    var history by mutableStateOf(store.loadHistory())
        private set

    /** 已练词条集合（practice-v1）：词库覆盖进度 */
    var progress by mutableStateOf(store.loadProgress())
        private set

    /** 读取到脏数据时的提示标记（UI 层消费一次即可） */
    var loadInvalid by mutableStateOf(store.lastLoadInvalid)
        private set

    /** 会话内已提交作答数（跨模块共享，对应 web 端 sessionAnswerCount） */
    var sessionAnswerCount: Int = 0
        private set

    init {
        // history-v1 上线时的回填：历史里今天的答题数少于 daily（老键）时取较大值补齐，
        // 保证「主页周历条 / 我的页答题数 / 今日打卡」三者口径一致。
        // 幂等且只升不降，不会覆盖真实历史。
        val todayKey = LocalDate.now().toString()
        if (daily.lastDate == todayKey) {
            val existing = history.dayOf(todayKey)
            val answersInHistory = existing?.answers ?: 0
            val answersInDaily = daily.verbAnswers + daily.adjectiveAnswers + daily.particleAnswers
            if (answersInHistory < answersInDaily) {
                val merged = (existing ?: DayActivity(date = todayKey)).copy(
                    verbAnswers = maxOf(existing?.verbAnswers ?: 0, daily.verbAnswers),
                    adjectiveAnswers = maxOf(existing?.adjectiveAnswers ?: 0, daily.adjectiveAnswers),
                    particleAnswers = maxOf(existing?.particleAnswers ?: 0, daily.particleAnswers),
                )
                val seeded = ActivityAggregation.upsert(history, merged)
                history = seeded
                store.saveHistory(seeded)
            }
        }
    }

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
            recordMastered()
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

    // ---------- 助词 ----------

    fun findParticleMistake(questionId: String): ParticleMistakeRecord? =
        particleMistakes.firstOrNull { it.questionId == questionId }

    /** 答错：新建或加重记录，并重置复习进度（D-022） */
    fun recordParticleMistake(questionId: String, wrongAnswer: String = "") {
        if (questionId.isBlank() || !ParticleData.QUESTION_BY_ID.containsKey(questionId)) {
            return
        }
        val now = Instant.now()
        val existing = findParticleMistake(questionId)
        val updated = if (existing == null) {
            ParticleReview.newRecord(questionId, wrongAnswer, now, sessionAnswerCount)
        } else {
            ParticleReview.afterWrong(existing, wrongAnswer, now, sessionAnswerCount)
        }
        particleMistakes = if (existing == null) {
            particleMistakes + updated
        } else {
            particleMistakes.map { if (it.questionId == questionId) updated else it }
        }
        store.saveParticleMistakes(particleMistakes)
    }

    /**
     * 复习答对：推进一档间隔，连续答对达到毕业次数则移出。
     * 返回 true 表示本题已毕业（供 UI 提示）。
     */
    fun recordParticleReviewSuccess(questionId: String): Boolean {
        val existing = findParticleMistake(questionId) ?: return false
        val next = ParticleReview.afterCorrect(existing, Instant.now())
        if (next == null) {
            removeParticleMistake(questionId)
            return true
        }
        particleMistakes = particleMistakes.map { if (it.questionId == questionId) next else it }
        store.saveParticleMistakes(particleMistakes)
        return false
    }

    fun removeParticleMistake(questionId: String) {
        if (particleMistakes.none { it.questionId == questionId }) {
            return
        }
        particleMistakes = particleMistakes.filter { it.questionId != questionId }
        store.saveParticleMistakes(particleMistakes)
    }

    fun clearParticleMistakes() {
        if (particleMistakes.isEmpty()) {
            return
        }
        particleMistakes = emptyList()
        store.saveParticleMistakes(particleMistakes)
    }

    /** 今天到期（含会话内重试到期）的助词错题数 */
    fun particleDueCount(): Int {
        val now = Instant.now()
        return particleMistakes.count { ParticleReview.isDue(it, now, sessionAnswerCount) }
    }

    /** 到期的错题 ID，供练习「首题插队」使用 */
    fun dueParticleMistakeIds(): List<String> {
        val now = Instant.now()
        return particleMistakes
            .filter { ParticleReview.isDue(it, now, sessionAnswerCount) }
            .map { it.questionId }
    }

    fun particleMistakeWeight(record: ParticleMistakeRecord): Int = ParticleReview.weight(record)

    fun updateParticleStats(correct: Boolean) {
        particleStats = bumpStats(particleStats, correct)
        store.saveParticleStats(particleStats)
    }

    fun resetParticleStats() {
        particleStats = ModuleStats()
        store.saveParticleStats(particleStats)
    }

    /** 助词掌握度：只认「未用提示答对」的题目，按 ID 去重（D-020） */
    fun markParticleMastered(questionId: String) {
        if (questionId.isBlank() || questionId in progress.particleMastered) {
            return
        }
        progress = progress.copy(particleMastered = progress.particleMastered + questionId)
        store.saveProgress(progress)
    }

    fun particleMasteredCount(): Int = progress.particleMastered.size

    fun particleMasteredIn(lessonId: String): Int {
        val ids = ParticleData.QUESTIONS.filter { it.lessonId == lessonId }.map { it.id }.toSet()
        return progress.particleMastered.count { it in ids }
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

    // ---------- 采集：学习时长 / 完成任务数 / 词库覆盖 ----------

    private fun todayKey(): String = LocalDate.now().toString()

    fun todayActivity(): DayActivity = ActivityAggregation.dayOrEmpty(history, todayKey())

    private fun updateToday(transform: (DayActivity) -> DayActivity) {
        val key = todayKey()
        val current = ActivityAggregation.dayOrEmpty(history, key)
        val updated = ActivityAggregation.upsert(history, transform(current))
        history = updated
        store.saveHistory(updated)
    }

    /** 练习屏前台计时累计（秒）；不足 1 秒的调用请由调用方过滤 */
    fun addStudySeconds(seconds: Int) {
        if (seconds <= 0) return
        updateToday { it.copy(seconds = it.seconds + seconds) }
    }

    /** 完成一轮练习（或完成一次错题重练）记 1 个任务 */
    fun addCompletedTask() {
        updateToday { it.copy(tasks = it.tasks + 1) }
    }

    fun markVerbPracticed(verbId: String) {
        if (verbId.isBlank() || verbId in progress.verbIds) return
        progress = progress.copy(verbIds = progress.verbIds + verbId)
        store.saveProgress(progress)
    }

    fun markAdjectivePracticed(adjectiveId: String) {
        if (adjectiveId.isBlank() || adjectiveId in progress.adjectiveIds) return
        progress = progress.copy(adjectiveIds = progress.adjectiveIds + adjectiveId)
        store.saveProgress(progress)
    }

    /** 错题「已掌握」数：连续答对达到毕业次数后从错题本移除的累计数 */
    val masteredCount: Int get() = progress.masteredMistakes

    fun recordMastered() {
        progress = progress.copy(masteredMistakes = progress.masteredMistakes + 1)
        store.saveProgress(progress)
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
