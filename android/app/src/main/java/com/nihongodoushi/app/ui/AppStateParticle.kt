package com.nihongodoushi.app.ui

import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.storage.ParticleReview
import com.nihongodoushi.app.storage.ParticleMistakeRecord
import com.nihongodoushi.app.storage.ModuleStats
import java.time.Instant

/**
 * 助词领域（IK-06 拆分）：v2 错题复习排期（D-022）+ 助词统计。
 * 函数体自 AppState.kt 原样迁入，行为不变。
 */

fun AppState.findParticleMistake(questionId: String): ParticleMistakeRecord? =
    particleMistakes.firstOrNull { it.questionId == questionId }

/** 答错：新建或加重记录，并重置复习进度（D-022） */
fun AppState.recordParticleMistake(questionId: String, wrongAnswer: String = "") {
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
fun AppState.recordParticleReviewSuccess(questionId: String): Boolean {
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

fun AppState.removeParticleMistake(questionId: String) {
    if (particleMistakes.none { it.questionId == questionId }) {
        return
    }
    particleMistakes = particleMistakes.filter { it.questionId != questionId }
    store.saveParticleMistakes(particleMistakes)
}

fun AppState.clearParticleMistakes() {
    if (particleMistakes.isEmpty()) {
        return
    }
    particleMistakes = emptyList()
    store.saveParticleMistakes(particleMistakes)
}

/** 今天到期（含会话内重试到期）的助词错题数 */
fun AppState.particleDueCount(): Int {
    val now = Instant.now()
    return particleMistakes.count { ParticleReview.isDue(it, now, sessionAnswerCount) }
}

/** 到期的错题 ID，供练习「首题插队」使用 */
fun AppState.dueParticleMistakeIds(): List<String> {
    val now = Instant.now()
    return particleMistakes
        .filter { ParticleReview.isDue(it, now, sessionAnswerCount) }
        .map { it.questionId }
}

fun AppState.particleMistakeWeight(record: ParticleMistakeRecord): Int = ParticleReview.weight(record)

fun AppState.updateParticleStats(correct: Boolean) {
    particleStats = bumpStats(particleStats, correct)
    store.saveParticleStats(particleStats)
}

fun AppState.resetParticleStats() {
    particleStats = ModuleStats()
    store.saveParticleStats(particleStats)
}
