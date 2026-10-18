package com.nihongodoushi.app.ui.practice

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.particle.ParticlePracticeState
import com.nihongodoushi.app.ui.particle.ParticleScope

/**
 * 练习状态机的进程重启恢复（IK-04）。
 *
 * 三个练习屏的状态类原本用 `remember` 持有，进程被系统回收或旋屏后整轮进度丢失。
 * 这里给每个状态类配一个 listSaver：把「筛选 + 当前题坐标 + 本轮计数 + 判定态」
 * 序列化为 Bundle 可保存的平铺列表，恢复时重建状态类并按坐标重放当前题
 * （选项顺序对助词轮次原样保留，其余随机调度状态不恢复，仅影响出题随机性）。
 *
 * 类型约束：Kotlin 的 `Any` 是非空根类型，`listOf<Any>` 不接受可空元素；
 * 且 `rememberSaveable` 要求 `Saver<T, out Any>`，Saveable 也不能写成 `Any?`。
 * 因此可空字段（scopeLabel / 题目坐标 / lessonId）保存时规范化为空串，
 * 恢复时 `takeIf { it.isNotEmpty() }` 解释回 null——空串不可能是合法 id 或标签。
 *
 * 用法（状态类创建处）：
 * `rememberSaveable(saver = verbStateSaver(app)) { VerbPracticeState(app) }`
 */

/** 动词练习状态 Saver：字段顺序见 VerbPracticeState.restoreSnapshot。 */
fun verbStateSaver(app: AppState): Saver<VerbPracticeState, Any> = listSaver<VerbPracticeState, Any>(
    save = { state ->
        listOf<Any>(
            state.groupFilter,
            state.voiceFilter,
            state.inflectionFilter,
            state.levelFilter,
            state.scopeLabel ?: "",
            state.reviewingMistakes,
            state.roundAnswered,
            state.roundCorrect,
            state.roundWrong,
            state.roundComplete,
            state.currentQuestion?.verb?.id ?: "",
            state.currentQuestion?.voiceId ?: "",
            state.currentQuestion?.inflectionId ?: "",
            state.selectedIndex,
            state.judged,
            state.showHint,
            state.lastCorrect,
            state.numberAtStart,
            state.hintUsedThisQuestion,
        )
    },
    restore = { values ->
        VerbPracticeState(app).also { it.restoreSnapshot(values) }
    },
)

/** 形容词练习状态 Saver：字段顺序见 AdjectivePracticeState.restoreSnapshot。 */
fun adjectiveStateSaver(app: AppState): Saver<AdjectivePracticeState, Any> = listSaver<AdjectivePracticeState, Any>(
    save = { state ->
        listOf<Any>(
            state.groupFilter,
            state.inflectionFilter,
            state.levelFilter,
            state.scopeLabel ?: "",
            state.reviewingMistakes,
            state.roundAnswered,
            state.roundCorrect,
            state.roundWrong,
            state.roundComplete,
            state.currentQuestion?.adjective?.id ?: "",
            state.currentQuestion?.formId ?: "",
            state.selectedIndex,
            state.judged,
            state.showHint,
            state.lastCorrect,
            state.numberAtStart,
            state.hintUsedThisQuestion,
        )
    },
    restore = { values ->
        AdjectivePracticeState(app).also { it.restoreSnapshot(values) }
    },
)

/** 助词练习状态 Saver：字段顺序见 ParticlePracticeState.restoreSnapshot。 */
fun particleStateSaver(app: AppState): Saver<ParticlePracticeState, Any> = listSaver<ParticlePracticeState, Any>(
    save = { state ->
        val scope = state.scope
        listOf<Any>(
            when (scope) {
                is ParticleScope.Lesson -> "lesson"
                ParticleScope.Review -> "review"
                ParticleScope.All -> "all"
            },
            (scope as? ParticleScope.Lesson)?.lessonId ?: "",
            state.roundPayload(),
            state.index,
            state.selectedIndex,
            state.judged,
            state.lastCorrect,
            state.hintOn,
            state.roundComplete,
            state.roundCorrect,
            state.roundWrong,
            state.answered,
            state.hintUsedIds.joinToString(","),
            state.hintUsedThisQuestion,
        )
    },
    restore = { values ->
        ParticlePracticeState(app).also { it.restoreSnapshot(values) }
    },
)
