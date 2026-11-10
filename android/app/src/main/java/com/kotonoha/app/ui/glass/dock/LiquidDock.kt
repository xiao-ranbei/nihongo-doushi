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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.graphics.Brush
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
    val accentColor = paperColors().textGreen
    // 官方容器底是 40% 不透明；我们的 glassSurface 本身已有 55%，再压一档才透得出背景
    val containerColor = colors.surface.copy(alpha = 0.34f)
    val contentColor = MaterialTheme.colorScheme.onSurface


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

        // ③ 选中块：一块比胶囊更亮的玻璃，块内自带一条"图标条"。
        //
        // 为什么不用官方的「采 combined backdrop（背景+图标底片）」：
        // 块的表面色画在采样结果**之上**，只要填充超过 ~20% 白就会把块内图标一起冲淡；
        // 而填充低于 20% 又读不出"浮起"，只剩边缘光 → 又变成凹槽。这是个死结。
        // 改成块内自绘图标条（一条与底栏等宽的图标带，按当前格偏移、被块的形状裁掉），
        // 填充与图标就解耦了：填充想多亮都行，图标自己画在最上层。
        // 附带好处：少一次全屏 backdrop 采样。
        Box(
            Modifier
                .offset(
                    x = with(density) { (dampedDragAnimation.value * tabWidth).toDp() } + 4.dp,
                    y = 4.dp,
                )
                .width(with(density) { tabWidth.toDp() })
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = backdrop,
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
                    // 块要「浮起来」，不是「陷下去」——
                    // 这里的方向是：外投影常驻（托起块）、内阴影几乎为零（只在按下那一刻才凹）。
                    // 上一版把内阴影做成常态 0.45，整条底栏读起来就是一个凹槽
                    highlight = {
                        // 静止时完全不画边缘光：只要有一圈亮边，就会与胶囊的边缘形成"两个圈"，
                        // 在均匀浅色背景上必然读成凹槽。选中感交给下面那团光
                        val progress = dampedDragAnimation.pressProgress
                        if (progress <= 0.01f) null else Highlight.Ambient.copy(alpha = progress)
                    },
                    shadow = {
                        // 静止不给外投影：这套 Shadow 是均匀外扩的，落在块四周会围出一圈暗边，
                        // 读起来就是"嵌进玻璃里的凹槽"。凸起感改由「中心亮 + 顶面反光」承担
                        Shadow(alpha = 0.42f * dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = (1.5f + 9f * progress).dp,
                            alpha = 0.06f + 0.94f * progress,
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
                        val top = if (!dark) 0.30f else 0.12f
                        val edge = if (!dark) 0.02f else 0.01f
                        // 中心亮、边缘略暗 —— 这是「凸起」的光照特征。
                        // 均匀填充 + 边缘高光会被读成凹槽（亮边围着暗心），前面的版本就是栽在这里
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(top),
                                    Color.White.copy(edge),
                                ),
                                center = Offset(size.width / 2f, size.height * 0.42f),
                                radius = size.maxDimension * 1.15f,
                            ),
                            alpha = 1f - progress,
                        )
                        // 顶面 1px 反光：让块有一条"棱"，而不是一团雾
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(0.38f),
                                0.05f to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.White.copy(alpha = 0.10f * progress))
                    },
                )
                .height(56.dp)
                .clip(DockShape),
        ) {
            // 块内图标：按「块当前落在哪一格」直接画一个。
            //
            // 为什么不做成"整条图标带 + 偏移"：块 Box 的宽度就是一格，任何子项都会被父约束
            // 卡在这一格内，即使 requiredWidth 突破，weight 分配的仍是块宽，图标会糊成一团。
            // 直接取最近格更稳，拖动过程中块在移动、注意力在位移上，图标切换看不出来。
            val focus = dampedDragAnimation.value.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            // 按下时图标跟着块一起放大，与官方 LiquidBottomTab 的 1.2 倍对齐
                            val s = 1f + 0.2f * dampedDragAnimation.pressProgress
                            scaleX = s
                            scaleY = s
                        }
                        .paint(
                            painter = painterResource(tabs[focus].iconRes),
                            colorFilter = ColorFilter.tint(accentColor),
                        ),
                )
                Text(
                    text = tabs[focus].label,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp,
                )
            }
        }
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
