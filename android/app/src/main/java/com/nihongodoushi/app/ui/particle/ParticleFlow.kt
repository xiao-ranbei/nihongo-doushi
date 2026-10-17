package com.nihongodoushi.app.ui.particle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nihongodoushi.app.ui.AppState

/**
 * 助词模块内的三级导航（spec §1.1 / §1.4）。
 *
 * 模块首页 → 专题讲解 → 练习屏；系统返回逐级退栈，练习中已作答则先确认。
 * 本容器持有练习状态，切到「我的」再回来不会丢本轮。
 */
private object ParticleRoute {
    const val HOME = "particle-home"
    const val LESSON = "particle-lesson"
    const val PRACTICE = "particle-practice"
}

@Composable
fun ParticleFlow(
    app: AppState,
    onExit: () -> Unit,
    autoReview: Boolean = false,
    onAutoReviewConsumed: () -> Unit = {},
) {
    val practice = remember { ParticlePracticeState(app) }
    var route by rememberSaveable { mutableStateOf(ParticleRoute.HOME) }
    var lessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** 从错题本进入时直接开复习（spec §1.2 的复习入口之一）。 */
    LaunchedEffect(autoReview) {
        if (autoReview) {
            practice.start(ParticleScope.Review)
            route = ParticleRoute.PRACTICE
            onAutoReviewConsumed()
        }
    }

    fun backTarget() {
        if (practice.scope is ParticleScope.Lesson) {
            route = ParticleRoute.LESSON
        } else {
            // 综合练习 / 错题复习：直接退出模块，由调用方决定回主页还是回错题本
            onExit()
        }
    }

    fun goBack() {
        when (route) {
            ParticleRoute.PRACTICE -> {
                val dirty = practice.answered > 0 && !practice.roundComplete
                if (dirty) {
                    pendingAction = { backTarget() }
                    confirmExit = true
                } else {
                    backTarget()
                }
            }
            ParticleRoute.LESSON -> route = ParticleRoute.HOME
            else -> onExit()
        }
    }

    BackHandler { goBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            ParticleRoute.LESSON -> {
                val currentLesson = lessonId
                if (currentLesson == null) {
                    route = ParticleRoute.HOME
                } else {
                    ParticleLessonScreen(
                        app = app,
                        lessonId = currentLesson,
                        onBack = { route = ParticleRoute.HOME },
                        onStart = {
                            practice.start(ParticleScope.Lesson(currentLesson))
                            route = ParticleRoute.PRACTICE
                        },
                    )
                }
            }

            ParticleRoute.PRACTICE -> {
                ParticlePracticeScreen(
                    state = practice,
                    onBack = { goBack() },
                    onStartAll = {
                        practice.start(ParticleScope.All)
                    },
                )
            }

            else -> ParticleHomeScreen(
                app = app,
                onBack = onExit,
                onOpenLesson = {
                    lessonId = it
                    route = ParticleRoute.LESSON
                },
                onStartLesson = {
                    lessonId = it
                    practice.start(ParticleScope.Lesson(it))
                    route = ParticleRoute.PRACTICE
                },
                onStartAll = {
                    practice.start(ParticleScope.All)
                    route = ParticleRoute.PRACTICE
                },
                onStartReview = {
                    practice.start(ParticleScope.Review)
                    route = ParticleRoute.PRACTICE
                },
            )
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出本轮？") },
            text = { Text("本轮已作答 ${practice.answered} 题，退出后本轮进度不保存；错题与统计已经记录。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    practice.stopRound()
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
