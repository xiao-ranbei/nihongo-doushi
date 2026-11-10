package com.kotonoha.app.ui.glass.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import com.kyant.shapes.Capsule
import com.kotonoha.app.ui.glass.currentContentBackdrop
import com.kotonoha.app.ui.theme.glassColors
import com.kotonoha.app.ui.theme.paperColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 液态玻璃底栏。
 *
 * 实现**逐行对齐** Kyant0/AndroidLiquidGlass 的
 * `catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`（分支 `android`）。
 * 结构、层数、参数值、动效曲线、层级关系全部保留原样，不做「优化」。
 *
 * ## 三层结构
 *
 * 1. **底层胶囊**（64dp）：玻璃本体，`vibrancy → blur(8dp) → lens(24,24)`；
 *    `layerBlock` 里按 `pressProgress` 把整条放大约 16dp 宽度对应的比例。
 * 2. **中层图标层**（56dp）：`.alpha(0.001f)` + `layerBackdrop(tabsBackdrop)`，
 *    把「图标内容 + 玻璃底 + 染色」录成一份底片给选中块采样。
 *    `alpha` 不能用精确的 0 —— Compose 会跳过绘制，录制结果为空，选中块里就没有图标。
 * 3. **选中块**（56dp，宽 1/N）：采 `rememberCombinedBackdrop(背景, tabsBackdrop)`，
 *    块里因此同时有背景折射与染色图标；`lens / highlight / shadow / innerShadow` 全部乘
 *    `pressProgress`，静止时只剩 `onDrawSurface` 那层 10% 黑（浅色）/ 白（深色）。
 *
 * 选中块的质感几乎全部来自折射与内阴影，填充只占 10% —— 这是原实现的取舍，不能改成常显高光
 * 或常显内阴影，否则整条底栏会读成一圈凹槽（本项目试过，见 git 历史 e15f52a 与其后三个提交）。
 *
 * ## 交互
 *
 * - `DampedDragAnimation`：拖动跨格切换，速度参与横向拉伸 / 纵向压扁，松手 spring 吸附
 * - `InteractiveHighlight`：触点处的径向高光（RuntimeShader，API 33+）
 * - `panelOffset`：拖到边界时整条胶囊平移最多 4dp
 *
 * ## 与官方实现的差异（仅这四处）
 *
 * | 官方 | 这里 | 原因 |
 * | --- | --- | --- |
 * | `accentColor` 写死 `#0088FF` / `#0091FF` | `glassColors().brand` | 配色只能由主题层给（D-053） |
 * | `containerColor` 写死 `#FAFAFA40` / `#12121240` | `glassColors().surface.copy(alpha = 0.4f)` | 同上 |
 * | `content: @Composable RowScope.() -> Unit` 由调用方写 | 内部按 [DockTab] 列表渲染 | 底栏固定四个标签，调用方不必重复一段 `repeat` |
 * | `clickable(role = Role.Tab)` | `selectable(selected, role = Role.Tab)` | 项目红线：选中态必须由语义表达（D-030） |
 *
 * 采样源从官方的「调用方传入 `Backdrop`」改为 `currentContentBackdrop()`，
 * 因为本项目的底栏挂在 `GlassLayer(overlay = …)` 槽里，内容层 backdrop 由它提供（D-059）。
 */
@Composable
fun LiquidDock(
    tabs: List<DockTab>,
    selectedIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = currentContentBackdrop()
    if (backdrop == null || tabs.isEmpty()) {
        // 没有玻璃层（Compose 预览 / 独立测试）时退化成不透明胶囊，避免整条底栏消失
        FallbackDock(
            tabs = tabs,
            selectedIndex = selectedIndex(),
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
        return
    }

    val isLightTheme = !isSystemInDarkTheme()
    val colors = glassColors()
    val accentColor = colors.brand
    val containerColor = colors.surface.copy(alpha = 0.4f)
    // 官方用 Color.Black / Color.White；项目禁硬编码色，取主题里最深的一档正文色
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
        var currentIndex by remember(selectedIndex) {
            mutableIntStateOf(selectedIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex().toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
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
        LaunchedEffect(selectedIndex) {
            snapshotFlow { selectedIndex() }
                .collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
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
                    shape = { Capsule() },
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
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockTabs(tabs, currentIndex, contentColor, onTabSelected)
        }

        // ② 中层图标层：录一份「染色图标底片」给选中块采样
        CompositionLocalProvider(
            LocalDockTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    // 不能用精确的 0：Compose 会跳过绘制，layerBackdrop 录不到内容
                    .alpha(0.001f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockTabs(tabs, currentIndex, contentColor, onTabSelected)
            }
        }

        // ③ 选中块
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
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
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8f.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // 官方原值：浅色皮肤 `Black 0.1f`、深色皮肤 `White 0.1f`。
                        // 这里浅色改成「提亮」而不是「压暗」：官方 demo 的底是深色系统桌面，
                        // 压 10% 黑读作"稍微暗一点"；本项目纸底是浅青，压黑会变成一块脏灰，
                        // 边缘一脏就整块读成凹槽。视觉意图相同（选中格从底上分出来），方向相反而已。
                        drawRect(
                            if (isLightTheme) Color.White.copy(0.22f) else Color.White.copy(0.10f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.White.copy(alpha = 0.06f * progress))
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabs.size),
        )
    }
}

/** 一个标签的数据：图标资源 + 文案。底栏不依赖 `AppTab`，由调用方映射。 */
data class DockTab(val iconRes: Int, val label: String)

/** 选中格的缩放，由按压进度驱动（对应官方的 `LocalLiquidBottomTabScale`）。 */
internal val LocalDockTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 四格内容。对应官方 `BottomTabsContent` 里对 `LiquidBottomTab` 的 `repeat` 调用：
 * 28dp 图标（`paint` + tint `contentColor`）+ 文案。
 */
@Composable
private fun RowScope.DockTabs(
    tabs: List<DockTab>,
    currentIndex: Int,
    contentColor: Color,
    onTabSelected: (Int) -> Unit,
) {
    tabs.forEachIndexed { index, tab ->
        DockTabItem(
            selected = index == currentIndex,
            onClick = { onTabSelected(index) },
        ) {
            Box(
                Modifier
                    .size(28f.dp)
                    .paint(
                        painter = painterResource(tab.iconRes),
                        colorFilter = ColorFilter.tint(contentColor),
                    ),
            )
            Text(
                text = tab.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * 单格。对应官方 `LiquidBottomTab`：`clip(Capsule())` + `fillMaxHeight()` + `weight(1f)` + 按 scale 缩放。
 *
 * 唯一差异：官方用 `clickable(role = Tab)`，这里用 `selectable` —— 项目红线要求选中态由
 * `selected` 语义表达，否则读屏用户只能从颜色猜（D-030）。
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
            .clip(Capsule())
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
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * 没有玻璃层时的兜底（Compose 预览、单元测试环境）：同样的尺寸与形状，但不采样。
 * 结构与 [LiquidDock] 一致，只是三层压成一层。
 */
@Composable
private fun FallbackDock(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 官方用 Color.Black / Color.White；项目禁硬编码色，取主题里最深的一档正文色
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(64f.dp)
            .fillMaxWidth()
            .padding(4f.dp)
            .clip(Capsule())
            .background(glassColors().surface.copy(alpha = 0.4f))
            .padding(horizontal = 4f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockTabs(tabs, selectedIndex, contentColor, onTabSelected)
    }
}
