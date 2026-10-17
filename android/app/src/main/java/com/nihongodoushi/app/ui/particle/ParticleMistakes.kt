package com.nihongodoushi.app.ui.particle

import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleQuestionTypes
import com.nihongodoushi.app.ui.AppState

/**
 * 全局错题本里「助词」组的展示模型。
 *
 * 助词错题自 v2 起与动词/形容词同一套复习字段（D-022），这里把记录还原成
 * 可读的语境、句子与复习进度（错几次 / 连对几次 / 下次复习）。
 */
data class ParticleMistakeItem(
    val questionId: String,
    val lessonParticles: String,
    val context: String,
    val sentence: String,
    val count: Int,
    val correctStreak: Int,
    val intervalDays: Int,
    val dueAt: String,
)

/** 把 stored 的记录还原成展示项；未知 ID 直接跳过（题库变动后的容错）。 */
fun AppState.particleMistakeItems(): List<ParticleMistakeItem> = particleMistakes.mapNotNull { record ->
    val question = ParticleData.QUESTION_BY_ID[record.questionId] ?: return@mapNotNull null
    val lesson = ParticleEngine.lesson(question.lessonId)
    val sentence = if (question.type == ParticleQuestionTypes.FILL) {
        (question.before ?: "") + "（　）" + (question.after ?: "")
    } else {
        (question.before ?: "") + (question.targetParticle ?: "") + (question.after ?: "")
    }
    ParticleMistakeItem(
        questionId = record.questionId,
        lessonParticles = lesson?.particles?.joinToString(" / ") ?: "",
        context = question.context,
        sentence = sentence,
        count = record.count,
        correctStreak = record.correctStreak,
        intervalDays = record.intervalDays,
        dueAt = record.dueAt,
    )
}
