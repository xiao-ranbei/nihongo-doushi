package com.nihongodoushi.app.ui.adjective

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
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.practice.AdjectivePracticeScreen
import com.nihongodoushi.app.ui.practice.AdjectivePracticeState

/** 形容词讲解页主题：活用形式（形容词无语态维度）。 */
sealed class AdjectiveTheme {
    data class Form(val formId: String) : AdjectiveTheme()
}

private object AdjectiveRoute {
    const val HOME = "adj-home"
    const val LESSON = "adj-lesson"
    const val PRACTICE = "adj-practice"
}

/** 形容词模块三级导航，与动词/助词同构。 */
@Composable
fun AdjectiveFlow(
    app: AppState,
    adjectiveState: AdjectivePracticeState,
    onExit: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf(AdjectiveRoute.HOME) }
    var formId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun backTarget() {
        if (formId != null) {
            route = AdjectiveRoute.LESSON
        } else {
            onExit()
        }
    }

    fun goBack() {
        when (route) {
            AdjectiveRoute.PRACTICE -> {
                if (adjectiveState.roundAnswered > 0) {
                    pendingAction = { backTarget() }
                    confirmExit = true
                } else {
                    backTarget()
                }
            }
            AdjectiveRoute.LESSON -> route = AdjectiveRoute.HOME
            else -> onExit()
        }
    }

    BackHandler { goBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            AdjectiveRoute.LESSON -> {
                val current = formId
                if (current == null) {
                    route = AdjectiveRoute.HOME
                } else {
                    AdjectiveLessonScreen(
                        formId = current,
                        onBack = { route = AdjectiveRoute.HOME },
                        onStart = {
                            adjectiveState.startScope(current, null)
                            route = AdjectiveRoute.PRACTICE
                        },
                    )
                }
            }

            AdjectiveRoute.PRACTICE -> AdjectivePracticeScreen(
                state = adjectiveState,
                onBack = { goBack() },
            )

            else -> AdjectiveHomeScreen(
                app = app,
                onBack = onExit,
                onOpenTheme = {
                    formId = it
                    route = AdjectiveRoute.LESSON
                },
                onStartTheme = {
                    adjectiveState.startScope(it, null)
                    route = AdjectiveRoute.PRACTICE
                },
                onStartAll = {
                    adjectiveState.startAll("综合练习")
                    route = AdjectiveRoute.PRACTICE
                },
                onStartReview = {
                    adjectiveState.startMistakeReview()
                    if (adjectiveState.reviewingMistakes) {
                        route = AdjectiveRoute.PRACTICE
                    }
                },
            )
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出本轮？") },
            text = { Text("本轮已作答 ${adjectiveState.roundAnswered} 题，退出后本轮进度不保存；错题与统计已经记录。") },
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
