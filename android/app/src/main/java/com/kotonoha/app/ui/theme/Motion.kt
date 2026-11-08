package com.kotonoha.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 过渡动效令牌（计划 §4.4 / M17）。
 *
 * 原则：动效只表达「谁进来了 / 谁被选中 / 谁在加载」，不做装饰性弹跳。
 * 所有时值走 tween / animate*AsState，系统「动画时长缩放 = 0」（无障碍移除动画）时
 * Compose 会自动退化为瞬时切换，这里不需要额外分支。
 */
object GlassMotion {

    /** 按压反馈、颜色微变。 */
    const val instant = 100

    /** 选中态、图标换色、判定结果淡入。 */
    const val fast = 160

    /** Tab 内容切换、页面进出、Toast。 */
    const val base = 220

    /** 弹层进出、抽屉。 */
    const val emphasized = 320

    /** 出场通常比入场快一档。 */
    const val exit = 240

    /** M3 standard：绝大多数过渡。 */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 入场：起步快、收尾缓。 */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 出场：起步缓、收尾快。 */
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 仅用于弹层拖拽回弹，别处禁用 spring。 */
    fun <T> sheetSpring() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    /** Tab 内容位移距离。 */
    val tabShift = 8

    /** 页面入场位移距离。 */
    val pageShift = 8

    /** Toast 入场位移距离。 */
    val toastShift = 6
}
