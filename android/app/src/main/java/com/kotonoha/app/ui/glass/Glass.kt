package com.kotonoha.app.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kotonoha.app.ui.theme.GlassColors
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.glassColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 方案 B 的玻璃底座。
 *
 * ## 为什么是两层 backdrop，而不是一层
 *
 * `drawBackdrop` 采样的是「被 `layerBackdrop` 录下来的那一层」。如果玻璃面自己也在那一层里，
 * 就形成绘制环——RenderThread 会在 `RenderNode::prepareTreeImpl` 里无限递归，
 * 实测直接 native 崩溃（栈帧上百层），表现为一启动就闪退。
 *
 * 所以录制层与采样层必须拆开：
 * - [Layer.Background]：**只**录屏幕底（渐变 + 三团光斑）。卡片 / 弹层 / Toast 采它
 *   —— 它们要的是「透过玻璃看见背景」，不是看见彼此。
 * - [Layer.Content]：录屏幕底 **+** 页面内容。悬浮岛 / 吸底条采它
 *   —— 这两者要的是「内容从身后穿过并被折射」，这是方案 B 的标志性效果。
 *
 * 悬浮岛必须是 [GlassLayer] 的**兄弟节点**，不能放进它的 content 里；放进去就又回到绘制环。
 *
 * ## 效果链顺序
 * 强制 `vibrancy ⇒ blur ⇒ lens`。顺序错了高光与折射会互相吃掉（库内 `lens` 依赖 blur 的结果）。
 */
enum class Layer {
    /** 只含屏幕底，供位于内容流中的玻璃面采样。 */
    Background,

    /** 含屏幕底与页面内容，供悬浮在内容之上的玻璃面采样。 */
    Content,
}

private val LocalBackgroundBackdrop = staticCompositionLocalOf<Backdrop?> { null }
private val LocalContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 全局唯一形状：24dp 圆角，别处不许再写数字半径。 */
object GlassShape {
    val corner: Shape = RoundedCornerShape(GlassTokens.radius)
}

/**
 * 玻璃录制的根层：铺屏幕底（165° 渐变 + 三团光斑）并录制给上层玻璃采样。
 *
 * 使用约定：本组件内部的 `content` 会被录进 [Layer.Content]，
 * 因此**悬浮类玻璃面（岛 / 吸底条）必须放在本组件外面**，与本组件平级。
 */
@Composable
fun GlassLayer(
    modifier: Modifier = Modifier,
    colors: GlassColors = glassColors(),
    content: @Composable BoxScope.() -> Unit,
) {
    val backgroundLayer = rememberLayerBackdrop { drawScreenTexture(colors) }
    val contentLayer = rememberLayerBackdrop {
        drawScreenTexture(colors)
        drawContent()
    }
    Box(modifier = modifier) {
        // ① 背景层：只画屏幕底，没有任何内容叠在上面，采样它永远不会成环
        Box(modifier = Modifier.matchParentSize().layerBackdrop(backgroundLayer)) {
            Spacer(modifier = Modifier.matchParentSize().drawBehind { drawScreenTexture(colors) })
        }
        // ② 内容层：屏幕底 + 页面内容；悬浮岛在它外面采它
        CompositionLocalProvider(
            LocalBackgroundBackdrop provides backgroundLayer,
            LocalContentBackdrop provides contentLayer,
        ) {
            Box(modifier = Modifier.matchParentSize().layerBackdrop(contentLayer)) {
                Spacer(modifier = Modifier.matchParentSize().drawBehind { drawScreenTexture(colors) })
                content()
            }
        }
    }
}

/**
 * 独立窗口（Dialog / BottomSheet / Drawer）里的采样源。
 *
 * 这些容器跑在自己的 Window 上，采样不到主窗口 [GlassLayer] 录的 RenderNode——
 * 表现是弹层玻璃只剩一层平涂、模糊与折射全缺席（IK-23）。
 * 这里的替代做法是**按窗口坐标重画一遍屏幕底纹理**：玻璃至少能拿到同一条渐变与同一批光斑，
 * 模糊与折射才有东西可作用，弹层与主窗口的观感也就接上了。
 *
 * 为什么不用 `rememberCanvasBackdrop`：那条路径只把 `onDraw` 直接丢进本地 DrawScope，
 * 拿不到玻璃面在窗口里的位置，纹理会被压缩进每块玻璃自己的矩形里。
 * 本类自己实现 [Backdrop]，用 `positionInWindow()` 做反向平移，纹理才是"钉在屏幕上"的。
 */
private class ScreenTextureBackdrop(
    private val colors: GlassColors,
    private val windowSize: Size,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val origin = coordinates?.positionInWindow() ?: Offset.Zero
        translate(-origin.x, -origin.y) {
            drawScreenTexture(colors, windowSize)
        }
    }
}

/**
 * 给弹层用的采样源：屏幕底纹理的静态副本，坐标对齐窗口。
 * 屏幕尺寸取 `LocalConfiguration`（Dialog 的 `containerSize` 是弹窗自己的尺寸，不能用）。
 */
@Composable
fun rememberScreenTextureBackdrop(colors: GlassColors = glassColors()): Backdrop {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = configuration.screenWidthDp * density.density
    val height = configuration.screenHeightDp * density.density
    return remember(colors, width, height) {
        ScreenTextureBackdrop(colors, Size(width, height))
    }
}

/**
 * 把 [backdrop] 注入给子树里的所有 [GlassSurface]（两层都指过去：弹层里没有"内容层"概念）。
 * 只给独立窗口里的弹层用；主窗口的层级由 [GlassLayer] 自己管。
 */
@Composable
fun ProvideGlassBackdrop(backdrop: Backdrop, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBackgroundBackdrop provides backdrop,
        LocalContentBackdrop provides backdrop,
        content = content,
    )
}

/**
 * 玻璃绘制本体，做成 Modifier 是为了能贴到别人的容器上——
 * M3 的 `ModalBottomSheet` / `AlertDialog` / `ModalDrawerSheet` 都自带一个 Surface，
 * 只有把玻璃画在它们的 Surface 上，四角与尺寸才和容器严丝合缝（不会在边缘露出 1dp 缝）。
 *
 * 三层结构：投影（外）→ 玻璃本体（drawBackdrop）→ 1dp 渐变描边（内，顶亮底暗）。
 *
 * @param backdrop 显式指定采样源；为 null 时按 [layer] 取当前 `GlassLayer` 提供的层
 * @param layer 采样哪一层：内容流里的卡片用 [Layer.Background]；悬浮岛 / 吸底条用 [Layer.Content]
 * @param elevation 外部投影高度；卡片 4dp、岛 10dp、弹层 24dp
 */
@Composable
fun Modifier.glassPanel(
    backdrop: Backdrop? = null,
    shape: Shape = GlassShape.corner,
    colors: GlassColors = glassColors(),
    layer: Layer = Layer.Background,
    elevation: Dp = GlassTokens.cardElevation,
    borderStroke: BorderStroke? = null,
    blurRadius: Dp = GlassTokens.blur,
    refractionHeight: Dp = GlassTokens.refractionHeight,
    refractionAmount: Dp = GlassTokens.refractionAmount,
): Modifier {
    val sampled = backdrop ?: when (layer) {
        Layer.Background -> LocalBackgroundBackdrop.current
        Layer.Content -> LocalContentBackdrop.current
    }
    val density = LocalDensity.current
    val cornerPx = with(density) { GlassTokens.radius.toPx() }
    val strokePx = with(density) { GlassTokens.hairline.toPx() }

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
                // 兜底：没有玻璃层（独立预览 / Compose 预览面板）时铺半透明实底，形状与描边保持一致
                Modifier.background(colors.surface, shape)
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
                    onDrawSurface = { drawRect(colors.surface) },
                )
            },
        )
        .clip(shape)
        .drawWithContent {
            drawContent()
            // 1dp 玻璃边：顶部取高光、底部回落到描边色，等价于 CSS 的 inset 0 1px 0 + border
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(colors.highlight, colors.line)),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(strokePx),
            )
            // 语义描边（如「选中的段」主色加粗）：与玻璃边分层，不互相覆盖
            if (borderStroke != null) {
                drawRoundRect(
                    brush = borderStroke.brush,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(borderStroke.width.toPx()),
                )
            }
        }
}

/** 一块玻璃面。参数见 [glassPanel]。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.corner,
    colors: GlassColors = glassColors(),
    layer: Layer = Layer.Background,
    elevation: Dp = GlassTokens.cardElevation,
    borderStroke: BorderStroke? = null,
    blurRadius: Dp = GlassTokens.blur,
    refractionHeight: Dp = GlassTokens.refractionHeight,
    refractionAmount: Dp = GlassTokens.refractionAmount,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassPanel(
            backdrop = null,
            shape = shape,
            colors = colors,
            layer = layer,
            elevation = elevation,
            borderStroke = borderStroke,
            blurRadius = blurRadius,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
        ),
        content = content,
    )
}

/**
 * `--screen-texture` 的 Compose 版：先 165° 两段线性渐变，再叠三团椭圆光斑。
 * 光斑的尺寸与位置逐值对齐 Golden（190×230 @ 16%/14%、230×270 @ 88%/40%、210×210 @ 28%/88%）。
 *
 * 光斑不是装饰：玻璃的折射与模糊在纯色底上完全看不出来，必须有低频明暗差才成立。
 *
 * @param canvas 绘制范围。默认取当前 DrawScope 尺寸；弹层的静态副本会显式传整屏尺寸，
 *   再靠调用方平移坐标，纹理才与主窗口那条对得上。
 */
private fun DrawScope.drawScreenTexture(colors: GlassColors, canvas: Size = size) {
    val w = canvas.width
    val h = canvas.height
    // 165deg：CSS 里 0deg 朝上、顺时针，换算成向量的 (sin, -cos)
    val radians = 165.0 * PI / 180.0
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(colors.screenTop, colors.screenBottom),
            start = Offset.Zero,
            end = Offset(w * dx, h * dy),
        ),
        size = canvas,
    )
    drawBlob(Offset(w * 0.16f, h * 0.14f), 190.dp.toPx(), 230.dp.toPx(), colors.blobTeal)
    drawBlob(Offset(w * 0.88f, h * 0.40f), 230.dp.toPx(), 270.dp.toPx(), colors.blobBlue)
    drawBlob(Offset(w * 0.28f, h * 0.88f), 210.dp.toPx(), 210.dp.toPx(), colors.blobMint)
}

/**
 * 一团椭圆光斑：CSS 的 `radial-gradient(rx ry at cx cy, color, transparent 70%)`。
 * Compose 的径向渐变只有正圆，靠一次纵向缩放把它压成椭圆。
 */
private fun DrawScope.drawBlob(center: Offset, radiusX: Float, radiusY: Float, color: Color) {
    withTransform({
        translate(center.x, center.y)
        scale(1f, radiusY / radiusX, pivot = Offset.Zero)
        translate(-center.x, -center.y)
    }) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = radiusX,
            ),
        )
    }
}
