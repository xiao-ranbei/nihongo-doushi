package com.kotonoha.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kotonoha.app.ui.theme.GlassColors
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.glassColors
import kotlin.math.max

/**
 * 液态金属：底栏选中块的材质（第 11 轮）。
 *
 * 它不是第三种材质，而是同一条效果链（`vibrancy → blur → lens`）的**更强参数**：
 *
 * | | 普通玻璃 | 液态金属 |
 * | --- | --- | --- |
 * | 折射 | 18dp / 24dp | **30dp / 36dp** |
 * | 投影 | 4–10dp | **12dp** |
 * | 表面 | 均匀半透明白 | **斜向渐变：左上金属高光 → 右下压品牌色** |
 * | 边缘 | 1dp 均匀渐变 | **顶部 2dp 硬高光 + 底边暗线 + 内侧品牌辉光** |
 *
 * 折射推得更狠是有目的的：图标与身后的滚动内容会在块的边缘被明显推挤变形，
 * 这是「液体」唯一的视觉来源；只靠渐变与描边做出来的是塑料，不是金属。
 *
 * 采样层与 [glassPanel] 一致（默认取当前 `GlassLayer` 的背景层）；
 * 在底栏里调用时由调用方显式传 `Layer.Content` 对应的 backdrop（见 `AppBottomBar`）。
 */
@Composable
fun Modifier.metalPanel(
    backdrop: Backdrop? = null,
    shape: Shape = GlassShape.corner,
    colors: GlassColors = glassColors(),
    layer: Layer = Layer.Background,
    elevation: Dp = GlassTokens.metalElevation,
    blurRadius: Dp = GlassTokens.blur,
    refractionHeight: Dp = GlassTokens.metalRefractionHeight,
    refractionAmount: Dp = GlassTokens.metalRefractionAmount,
    /** 选中亮度：0 表示未激活（用在地铁式"当前格"提示上时置 1），影响内侧辉光强度 */
    glow: Float = 1f,
): Modifier {
    val sampled = backdrop ?: when (layer) {
        Layer.Background -> currentBackgroundBackdrop()
        Layer.Content -> currentContentBackdrop()
    }
    val density = LocalDensity.current
    val cornerPx = with(density) { GlassTokens.radius.toPx() }
    val strokePx = with(density) { GlassTokens.hairline.toPx() }
    val hardLightPx = with(density) { 2.dp.toPx() }
    val glowStrokePx = with(density) { 7.dp.toPx() }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.dockShadow,
            spotColor = colors.dockShadow,
        )
        .then(
            if (sampled == null) {
                Modifier.background(colors.metalFallback, shape)
            } else {
                val blurPx = with(density) { blurRadius.toPx() }
                val heightPx = with(density) { refractionHeight.toPx() }
                val amountPx = with(density) { refractionAmount.toPx() }
                Modifier.drawBackdrop(
                    backdrop = sampled,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(blurPx)
                        lens(heightPx, amountPx)
                    },
                    onDrawSurface = {
                        // 斜向金属渐变：左上接高光、右下压品牌色，中间回到玻璃底
                        // 垂直三层：顶面高光（窄而硬）→ 中段淡青 → 底面压暗。
                        // 小块上斜向渐变会糊成一片纯色，只有"上亮下暗"才读得出金属锭的侧面
                        // 打底：上部近白、下部压青，块因此比岛更"沉"
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to colors.metalTop,
                                0.38f to colors.metalMid,
                                1f to colors.metalBottom,
                            ),
                        )
                        // 反射带：块上部 18% 处一条横向椭圆光斑，模拟金属对天空的镜面反射。
                        // 只靠垂直渐变读到的是"染色塑料"，加上这一道才读得出"有一层水面/金属在反光"
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.highlight.copy(alpha = 0.55f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.16f),
                                radius = size.width * 0.62f,
                            ),
                        )
                    },
                )
            },
        )
        .clip(shape)
        .drawWithContent {
            // ① 内阴影：沿边缘向内 7dp 的一圈暗青，让块像"凹"进去的金属而不是贴上的纸片。
            // 必须画在内容之前，否则会盖住图标与文字
            if (glow > 0f) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, colors.metalGlow.copy(alpha = colors.metalGlow.alpha * glow)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = max(size.width, size.height) * 0.72f,
                    ),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(glowStrokePx),
                )
            }
            drawContent()
            // ② 顶部 2dp 硬高光：金属的"棱"，衰减极快，和玻璃的柔和内高光不是一回事
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to colors.highlight,
                    0.12f to colors.highlight.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(hardLightPx),
            )
            // ② 整圈 1dp 边：顶亮底暗，块因此有厚度
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(colors.highlight, colors.metalEdgeDark)),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(strokePx),
            )
        }
}

/**
 * 只画金属"厚度"的边缘，不铺金属底、不采样。
 *
 * 给「整条底栏金属化」那一档用：整条铺金属底会变成一根碧玉条、压过页面内容，
 * 真正要的是「玻璃岛 + 金属包边」——顶面一道硬高光、底边一道暗线，中间仍是玻璃。
 */
@Composable
fun Modifier.metalEdge(colors: GlassColors = glassColors()): Modifier {
    val density = LocalDensity.current
    val cornerPx = with(density) { GlassTokens.radius.toPx() }
    val hardLightPx = with(density) { 2.dp.toPx() }
    return drawWithContent {
        drawContent()
        // 顶面硬高光：只在上缘 3dp 内衰减
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to colors.highlight,
                0.06f to colors.highlight.copy(alpha = 0.30f),
                0.12f to Color.Transparent,
                1f to Color.Transparent,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(hardLightPx),
        )
        // 底边暗线：金属的下缘总是埋在阴影里
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.90f to Color.Transparent,
                1f to colors.metalEdgeDark,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(hardLightPx),
        )
    }
}

/**
 * 底栏选中块的四种候选形态（第 11 轮，关子要"都做一下再挑"）。
 *
 * 这四种只在**选型期**存在：`DockPreview.variant` 由「我的 → 底栏形态」临时入口循环切换，
 * 定档之后其余三种与这个入口一起删除，产品里只剩一种（AC-14：App 内不出现形态选择）。
 */
enum class DockVariant(val label: String, val hint: String) {
    /** 与格子同宽的金属块，把选中格整个包住（选中"区域"最明确）。 */
    Slab("整格滑块", "金属块与格子同宽同高，包住整个选中格"),

    /** 只有图标背后一小块金属胶囊，文字留在外面。 */
    Capsule("图标胶囊", "只有图标背后一块 44×30 金属胶囊，底栏更轻"),

    /** 整条岛换成金属质感，选中格靠局部光环与图标放大表达。 */
    MetalBar("整条金属", "整条底栏金属化，选中格打一圈品牌光环"),

    /** 滑块 + 一条连接相邻格的细金属带，切换时带子拉伸再断开。 */
    Trail("滑块 + 连接带", "整格滑块加一条 6dp 细金属带，像水银被拉过去"),
}

/**
 * 选型期的全局形态状态。
 *
 * 用全局 `mutableStateOf` 而不是从 Activity 层层传参：它只服务临时调试入口，
 * 传参会污染 `AppRoot → AppBottomBar` 的签名；定档后整个对象会被删掉。
 */
object DockPreview {
    var variant by mutableStateOf(DockVariant.Slab)

    fun cycle(): DockVariant {
        val all = DockVariant.entries
        variant = all[(all.indexOf(variant) + 1) % all.size]
        return variant
    }
}

/** 语义描边（如选中格的品牌色细环）：与金属边分层，不互相覆盖。 */
@Composable
fun Modifier.metalRing(colors: GlassColors = glassColors(), width: Dp = 2.5.dp): Modifier {
    val density = LocalDensity.current
    val cornerPx = with(density) { GlassTokens.radius.toPx() }
    val strokePx = with(density) { width.toPx() }
    val haloPx = with(density) { 5.dp.toPx() }
    return drawWithContent {
        drawContent()
        // 内圈细光：让环有金属丝的质感，而不是一根平涂的线
        drawRoundRect(
            color = colors.highlight.copy(alpha = 0.55f),
            size = Size(size.width - strokePx, size.height - strokePx),
            topLeft = Offset(strokePx, strokePx),
            cornerRadius = CornerRadius(cornerPx - strokePx),
            style = Stroke(strokePx / 2f),
        )
        // 主环：品牌色，比玻璃边粗一档才在整条岛上看得见
        drawRoundRect(
            color = colors.brand.copy(alpha = 0.90f),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(strokePx),
        )
        // 外晕：把环"点亮"，比加粗更有效
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, colors.brand.copy(alpha = 0.10f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * 0.72f,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(haloPx),
        )
    }
}

/** 金属块的形状：与全局圆角同一个值，只是这里需要显式拿到 Shape 传给 shadow / clip。 */
val MetalShape: Shape = RoundedCornerShape(GlassTokens.radius)
