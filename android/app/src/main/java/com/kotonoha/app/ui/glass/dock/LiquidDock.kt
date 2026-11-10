package com.kotonoha.app.ui.glass.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kotonoha.app.ui.glass.currentContentBackdrop
import com.kotonoha.app.ui.theme.glassColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import com.kotonoha.app.ui.theme.paperColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 液态玻璃底栏 —— 照 **Kyant0/AndroidLiquidGlass** 的 `LiquidBottomTabs` 重写。
 *
 * ## 三层结构（这是整套效果的关键，缺一层就不成立）
 *
 * 1. **底层胶囊**（64dp，玻璃本体）：`drawBackdrop` + `vibrancy → blur(8dp) → lens(24,24)`。
 *    它只是"容器"，按压时整体轻微放大（`layerBlock` 里按进度缩放）。
 * 2. **中层图标层**（56dp，`alpha = 0f`）：内容与底层完全一样，但被 `layerBackdrop` **录进**
 *    `tabsBackdrop`。它在正常合成里不可见，作用只有一个 —— 给选中块提供"可被折射的图标"。
 * 3. **选中块**（56dp，宽 1/N）：采 `rememberCombinedBackdrop(背景, tabsBackdrop)`，
 *    所以块里同时有**背景的折射**和**图标的染色重绘**；`highlight / shadow / innerShadow / lens`
 *    全部乘 `pressProgress`，静止时只剩一片 10% 的黑（浅色）/ 白（深色）。
 *
 * 与上一版最大的观念差别：**选中块在静止时几乎不可见**。它不是一个"青玉块"，
 * 而是一片贴着图标的薄玻璃；质感来自折射、色差、内阴影，且只在按下的那 200ms 里出现。
 * 选中态主要靠"图标被染成强调色 + 那片薄影"表达，而不是靠一块色块。
 *
 * ## 交互
 *
 * - 拖动可跨格切换（`DampedDragAnimation.value` 连续变化），松手按速度吸附到最近一格
 * - 拖动到面板边界时，整个胶囊跟着平移最多 4dp（`panelOffset`），像液面被推了一下
 * - 按压时块横向拉伸、纵向压扁（`velocity` 参与 `layerBlock` 的 scale 计算）
 * - `InteractiveHighlight` 在触点位置点一片径向高光
 *
 * ## 与现有代码的兼容
 *
 * 采样源走 `currentContentBackdrop()`（`GlassLayer(overlay = …)` 里可拿到内容层），
 * 因此它必须由 `MainActivity` 放进 overlay 槽；颜色全部来自 `glassColors()` / `paperColors()`；
 * 每格是可访问的 `selectable(role = Tab)` 焦点站，选中态由语义而非颜色表达。
 */
@Composable
fun LiquidDock(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = currentContentBackdrop()
    if (backdrop == null || tabs.isEmpty()) {
        // 没有玻璃层（Compose 预览 / 独立测试）时退化成不透明胶囊，避免整条底栏消失
        FallbackDock(tabs, selectedIndex, onSelect, modifier)
        return
    }

    val dark = isSystemInDarkTheme()
    val colors = glassColors()
    val accentColor = colors.brand
    // 官方容器底是 40% 不透明；我们的 glassSurface 本身已有 55%，再压一档才透得出背景
    val containerColor = colors.surface.copy(alpha = 0.34f)
    val contentColor = MaterialTheme.colorScheme.onSurface

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                // 56 → 78dp 的观感：按压时选中块明显鼓起
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabs.size - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }
        // 外部（点击）改了选中项时，把块动画挪过去
        LaunchedEffect(selectedIndex) {
            snapshotFlow { selectedIndex }
                .collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onSelect(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        size.height / 2f,
                    )
                },
            )
        }

        // ① 底层胶囊：玻璃本体
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { DockShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(DockHeight)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockTabs(tabs, selectedIndex, onSelect, contentColor, alpha = 1f)
        }

        // ② 中层图标层：alpha = 0，只负责把图标录进 tabsBackdrop 供选中块折射
        CompositionLocalProvider(
            LocalDockTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    // 这一层只做两件事：把图标内容录进 tabsBackdrop（供选中块采样）+ 染成强调色。
                    // 官方这里还叠了一层 drawBackdrop 让图标自带玻璃感；我们去掉它 ——
                    // 少一次全屏采样，选中块里依然是「染色图标 + 折射」，视觉差别只在按下瞬间
                    .graphicsLayer { translationX = panelOffset }
                    // 用 0.001 而不是 0：alpha 精确为 0 时 Compose 会跳过绘制，
                    // layerBackdrop 就录不到内容，选中块里便没有可折射的染色图标
                    .alpha(0.001f)
                    .layerBackdrop(tabsBackdrop)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockTabs(tabs, selectedIndex, onSelect, contentColor, alpha = 1f)
            }
        }

        // ③ 选中块：采「背景 + 图标层」，所以块里既有折射也有染色图标
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) {
                            dampedDragAnimation.value * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                        }
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { DockShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 色差只在按下时出现：这是"液态"最像玻璃的一帧
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    // 静止时也留一条高光与内阴影：块要一直"有玻璃的边"，不能只在按下时才有质感
                    highlight = {
                        // 用 Ambient（无方向环境光）而不是 Default：Default 会在某一角拉出一道很亮的弧，
                        // 与胶囊自身的高光叠在一起形成"两个亮角"，看着乱
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(alpha = 0.42f + 0.58f * progress)
                    },
                    shadow = { Shadow(alpha = 0.22f + 0.6f * dampedDragAnimation.pressProgress) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = (7f + 5f * progress).dp,
                            alpha = 0.45f + 0.55f * progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        // 速度形变：往哪边甩就往哪边拉长，停下来再回圆
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // 关键改动：用「白提亮」而不是「黑压暗」。
                        // 浅青玻璃上压 7% 黑会出来一块灰贴纸，看着脏；提亮 30% 白才是"浮起的玻璃"，
                        // 手机上的液体玻璃也是这个道理 —— 选中块比周围更亮，不是更暗
                        drawRect(
                            if (!dark) Color.White.copy(0.26f) else Color.White.copy(0.07f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.White.copy(alpha = 0.10f * progress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size),
        )
    }
}

/** 底栏高度：容器 64dp，内部内容 56dp，四周 4dp 留白。 */
private val DockHeight = 64.dp

/** 胶囊形状：官方用 `com.kyant.shapes.Capsule()`，这里用等价的全圆角，避免多引一个依赖。 */
private val DockShape: Shape = RoundedCornerShape(percent = 50)

internal val LocalDockTabScale = staticCompositionLocalOf { { 1f } }

/** 一个标签的数据：图标资源 + 文案。由调用方从 `AppTab` 映射过来，底栏不依赖 AppTab。 */
data class DockTab(val iconRes: Int, val label: String)

@Composable
private fun RowScope.DockTabs(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentColor: Color,
    alpha: Float,
) {
    tabs.forEachIndexed { index, tab ->
        val selected = index == selectedIndex
        DockTabItem(
            selected = selected,
            onClick = { onSelect(index) },
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val s = if (selected) 1.08f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .paint(
                        painter = painterResource(tab.iconRes),
                        colorFilter = ColorFilter.tint(contentColor),
                        alpha = alpha,
                    ),
            )
            Text(
                text = tab.label,
                color = contentColor.copy(alpha = contentColor.alpha * alpha),
                fontSize = 11.sp,
                fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * 单格：整格一个可访问焦点站。
 *
 * 官方用的是 `clickable(role = Tab)`；这里换成 `selectable`，因为我们依赖 `selected` 语义
 * 让 TalkBack 念出"已选中"，而不是让读屏用户从颜色上猜。
 */
@Composable
private fun RowScope.DockTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalDockTabScale.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clip(DockShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 没有玻璃层时的兜底：同样尺寸与形状，只是不采样（Compose 预览、单元测试环境）。 */
@Composable
private fun FallbackDock(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = glassColors()
    Row(
        modifier = modifier
            .height(DockHeight)
            .fillMaxWidth()
            .clip(DockShape)
            .background(colors.surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockTabs(tabs, selectedIndex, onSelect, MaterialTheme.colorScheme.onSurface, alpha = 1f)
    }
}
