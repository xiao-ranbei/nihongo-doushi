package com.nihongodoushi.app.ui.verb

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.practice.VerbPracticeScreen
import com.nihongodoushi.app.ui.practice.VerbPracticeState

/** 讲解页主题：活用形式（配原形语态）或语态（配辞书形）。 */
sealed class VerbTheme {
    data class Form(val formId: String) : VerbTheme()
    data class Voice(val voiceId: String) : VerbTheme()
}

/** 讲解页主题的可读名（顶栏标题用） */
fun verbThemeLabel(theme: VerbTheme): String = when (theme) {
    is VerbTheme.Form -> VerbData.INFLECTION_BY_ID[theme.formId]?.label ?: theme.formId
    is VerbTheme.Voice -> VerbData.VOICE_BY_ID[theme.voiceId]?.label ?: theme.voiceId
}

private object VerbRoute {
    const val HOME = "verb-home"
    const val LESSON = "verb-lesson"
    const val PRACTICE = "verb-practice"
}

/**
 * 动词模块的三级导航（三层改造 §2）：模块首页 → 讲解页 → 练习屏。
 * 练习状态由本容器持有，切「我的」再回来不丢本轮；退出与返回栈同助词模块。
 */
@Composable
fun VerbFlow(
    app: AppState,
    verbState: VerbPracticeState,
    onExit: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(VerbRoute.HOME) }
    var theme by rememberSaveable { mutableStateOf<String?>(null) } // "form:te" / "voice:potential"
    var confirmExit by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun parseTheme(raw: String?): VerbTheme? = when {
        raw == null -> null
        raw.startsWith("form:") -> VerbTheme.Form(raw.removePrefix("form:"))
        raw.startsWith("voice:") -> VerbTheme.Voice(raw.removePrefix("voice:"))
        else -> null
    }

    fun backTarget() {
        if (theme != null) {
            route = VerbRoute.LESSON
        } else {
            onExit()
        }
    }

    fun goBack() {
        when (route) {
            VerbRoute.PRACTICE -> {
                if (verbState.roundAnswered > 0) {
                    pendingAction = { backTarget() }
                    confirmExit = true
                } else {
                    backTarget()
                }
            }
            VerbRoute.LESSON -> route = VerbRoute.HOME
            else -> onExit()
        }
    }

    BackHandler { goBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            VerbRoute.LESSON -> {
                val parsed = parseTheme(theme)
                if (parsed == null) {
                    route = VerbRoute.HOME
                } else {
                    VerbLessonScreen(
                        theme = parsed,
                        onBack = { route = VerbRoute.HOME },
                        onStart = {
                            when (parsed) {
                                is VerbTheme.Form -> verbState.startScope(
                                    voiceId = "base",
                                    inflectionId = parsed.formId,
                                    label = verbThemeLabel(parsed),
                                )
                                is VerbTheme.Voice -> verbState.startScope(
                                    voiceId = parsed.voiceId,
                                    inflectionId = "dictionary",
                                    label = verbThemeLabel(parsed),
                                )
                            }
                            route = VerbRoute.PRACTICE
                        },
                    )
                }
            }

            VerbRoute.PRACTICE -> VerbPracticeScreen(
                state = verbState,
                onBack = { goBack() },
            )

            else -> VerbHomeScreen(
                app = app,
                onBack = onExit,
                onOpenTheme = {
                    theme = when (it) {
                        is VerbTheme.Form -> "form:${it.formId}"
                        is VerbTheme.Voice -> "voice:${it.voiceId}"
                    }
                    route = VerbRoute.LESSON
                },
                onStartTheme = {
                    verbState.startScope(
                        voiceId = when (it) {
                            is VerbTheme.Form -> "base"
                            is VerbTheme.Voice -> it.voiceId
                        },
                        inflectionId = when (it) {
                            is VerbTheme.Form -> it.formId
                            is VerbTheme.Voice -> "dictionary"
                        },
                        label = verbThemeLabel(it),
                    )
                    route = VerbRoute.PRACTICE
                },
                onStartAll = {
                    verbState.startAll("综合练习")
                    route = VerbRoute.PRACTICE
                },
                onStartReview = {
                    verbState.startMistakeReview()
                    if (verbState.reviewingMistakes) {
                        route = VerbRoute.PRACTICE
                    }
                },
            )
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出本轮？") },
            text = { Text("本轮已作答 ${verbState.roundAnswered} 题，退出后本轮进度不保存；错题与统计已经记录。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    pendingAction?.invoke()
                    pendingAction = null
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续答题") }
            },
        )
    }
}
