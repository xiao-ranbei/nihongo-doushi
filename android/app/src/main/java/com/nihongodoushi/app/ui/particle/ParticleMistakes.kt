package com.nihongodoushi.app.ui.particle

import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleQuestionTypes
import com.nihongodoushi.app.ui.AppState

/**
 * 全局错题本里「助词」组的展示模型。
 *
 * 助词错题存的是纯题目 ID 数组（D-017），没有 `dueAt` / `count` 这类字段，
 * 所以这里只把 ID 还原成可读的语境与句子，不伪装出「到期时间」。
 */
data class ParticleMistakeItem(
    val questionId: String,
    val lessonParticles: String,
    val context: String,
    val sentence: String,
)

/** 把 stored 的题目 ID 还原成展示项；未知 ID 直接跳过（题库变动后的容错）。 */
fun AppState.particleMistakeItems(): List<ParticleMistakeItem> = particleMistakes.mapNotNull { id ->
    val question = ParticleData.QUESTION_BY_ID[id] ?: return@mapNotNull null
    val lesson = ParticleEngine.lesson(question.lessonId)
    val sentence = if (question.type == ParticleQuestionTypes.FILL) {
        (question.before ?: "") + "（　）" + (question.after ?: "")
    } else {
        (question.before ?: "") + (question.targetParticle ?: "") + (question.after ?: "")
    }
    ParticleMistakeItem(
        questionId = id,
        lessonParticles = lesson?.particles?.joinToString(" / ") ?: "",
        context = question.context,
        sentence = sentence,
    )
}
