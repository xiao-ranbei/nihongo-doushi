package com.nihongodoushi.app.ui

import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.mastery.Mastery
import com.nihongodoushi.app.engine.verb.VerbData

/**
 * 掌握度与覆盖进度领域（IK-06 拆分）：
 * 三层改造的组合掌握度（voice::inflection::verbId / form::adjId）+ 词库覆盖进度。
 * 掌握度只认「未用提示答对」（D-020 同口径）。函数体自 AppState.kt 原样迁入，行为不变。
 */

/** 助词掌握度：只认「未用提示答对」的题目，按 ID 去重（D-020） */
fun AppState.markParticleMastered(questionId: String) {
    if (questionId.isBlank() || questionId in progress.particleMastered) {
        return
    }
    progress = progress.copy(particleMastered = progress.particleMastered + questionId)
    store.saveProgress(progress)
}

fun AppState.particleMasteredCount(): Int = progress.particleMastered.size

fun AppState.particleMasteredIn(lessonId: String): Int {
    val ids = com.nihongodoushi.app.engine.particle.ParticleData.QUESTIONS.filter { it.lessonId == lessonId }.map { it.id }.toSet()
    return progress.particleMastered.count { it in ids }
}

fun AppState.markVerbPracticed(verbId: String) {
    if (verbId.isBlank() || verbId in progress.verbIds) return
    progress = progress.copy(verbIds = progress.verbIds + verbId)
    store.saveProgress(progress)
}

fun AppState.markAdjectivePracticed(adjectiveId: String) {
    if (adjectiveId.isBlank() || adjectiveId in progress.adjectiveIds) return
    progress = progress.copy(adjectiveIds = progress.adjectiveIds + adjectiveId)
    store.saveProgress(progress)
}

// ---------- 掌握度（三层改造）：只认「未用提示答对」的组合 ----------

fun AppState.markVerbMastered(voiceId: String, inflectionId: String, verbId: String) {
    val key = Mastery.verbKey(voiceId, inflectionId, verbId)
    if (key in progress.verbMastered) return
    progress = progress.copy(verbMastered = progress.verbMastered + key)
    store.saveProgress(progress)
}

fun AppState.markAdjectiveMastered(formId: String, adjectiveId: String) {
    val key = Mastery.adjectiveKey(formId, adjectiveId)
    if (key in progress.adjMastered) return
    progress = progress.copy(adjMastered = progress.adjMastered + key)
    store.saveProgress(progress)
}

/** 某活用形式下已掌握的不同词条数（分母 = 词条总数） */
fun AppState.verbMasteredForForm(inflectionId: String): Int =
    Mastery.verbCountForForm(progress.verbMastered, inflectionId)

/** 某语态下已掌握的不同词条数（按辞书形统计） */
fun AppState.verbMasteredForVoice(voiceId: String): Int =
    Mastery.verbCountForVoice(progress.verbMastered, voiceId)

/** 形容词某形式下已掌握的不同词条数（分母 = 词条总数） */
fun AppState.adjectiveMasteredForForm(formId: String): Int =
    Mastery.adjectiveCountForForm(progress.adjMastered, formId)

/** 动词模块整体掌握度：各形式/语态卡进度的等权平均（概览用，见三层改造 §3.2） */
fun AppState.verbModulePercent(): Int {
    val fractions = VerbData.INFLECTIONS.map { verbMasteredForForm(it.id) / VerbData.VERBS.size.toFloat() } +
        VerbData.VOICES.filter { it.id != "base" }
            .map { verbMasteredForVoice(it.id) / VerbData.VERBS.size.toFloat() }
    return Mastery.modulePercent(fractions)
}

/** 形容词模块整体掌握度 */
fun AppState.adjectiveModulePercent(): Int {
    val fractions = AdjectiveData.FORMS.map {
        adjectiveMasteredForForm(it.id) / AdjectiveData.ADJECTIVES.size.toFloat()
    }
    return Mastery.modulePercent(fractions)
}

/** 错题「已掌握」数：连续答对达到毕业次数后从错题本移除的累计数 */
val AppState.masteredCount: Int get() = progress.masteredMistakes

fun AppState.recordMastered() {
    progress = progress.copy(masteredMistakes = progress.masteredMistakes + 1)
    store.saveProgress(progress)
}
