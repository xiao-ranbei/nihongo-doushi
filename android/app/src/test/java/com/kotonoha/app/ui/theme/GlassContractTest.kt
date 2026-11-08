package com.kotonoha.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 方案 B 的「Golden ↔ 代码」契约：把 `docs/design/liquid-glass/src/theme-b.css` 当成唯一真源，
 * 逐值比对 Compose 侧令牌。Golden 一改、代码没跟，这里立刻红灯，不用等真机截图肉眼比对。
 *
 * 另外两条静态守卫钉住「材质不许绕过」：
 * - 数字圆角：`RoundedCornerShape(12.dp)` 这类写法一律不许出现，形状只能取 `GlassShape.corner`；
 * - 不透明 surface：`color = ...surface` 铺在 `Surface` 上就是「半透明冒充玻璃」（D-056），
 *   玻璃面必须走 `GlassSurface` / `glassPanel`。
 */
class GlassContractTest {

    private val moduleDir = File(".").absoluteFile
    private val sourceRoot = File(moduleDir, "src/main/java/com/kotonoha/app")
    private val goldenCss = File(moduleDir, "../../docs/design/liquid-glass/src/theme-b.css")

    private val css: String by lazy {
        assertTrue(goldenCss.exists(), "Golden 缺失，方案 B 无从比对：${goldenCss.path}")
        goldenCss.readText()
    }

    /** 从 CSS 里按出现顺序抓某个属性的全部 rgba 值（浅色在前、墨夜在后）。 */
    private fun cssRgba(prop: String): List<Color> =
        Regex("""$prop:rgba\((\d+),(\d+),(\d+),([0-9.]+)\)""")
            .findAll(css)
            .map { m ->
                Color(
                    red = m.groupValues[1].toInt(),
                    green = m.groupValues[2].toInt(),
                    blue = m.groupValues[3].toInt(),
                    alpha = (m.groupValues[4].toFloat() * 255f).roundToInt(),
                )
            }
            .toList()

    private fun assertSameColor(expected: Color, actual: Color, label: String) {
        assertEquals(expected.toArgb(), actual.toArgb(), "$label 与 Golden 不一致")
    }

    @Test
    fun `圆角与 GlassTokens 对齐 Golden`() {
        val radius = Regex("""--radius:(\d+)px""").find(css)
        assertTrue(radius != null, "Golden 里找不到 --radius")
        assertEquals(radius.groupValues[1].toInt().dp, GlassTokens.radius)
        // `--radius-device` 只用于原型模拟机外框，不属于产品 UI，不该被误当成产品圆角
        assertTrue(css.contains("--radius-device:"), "Golden 的模拟机外框令牌不见了，检查是否被误改")
    }

    @Test
    fun `玻璃底色与描边两侧一致`() {
        val light = glassColorsFor(paletteFor(dark = false))
        val dark = glassColorsFor(paletteFor(dark = true))

        val surfaces = cssRgba("--glass-surface")
        assertEquals(2, surfaces.size, "Golden 应有两套 --glass-surface（浅色 / 墨夜）")
        assertSameColor(surfaces[0], light.surface, "浅色玻璃底")
        assertSameColor(surfaces[1], dark.surface, "墨夜玻璃底")

        val lines = cssRgba("--glass-line")
        assertEquals(2, lines.size, "Golden 应有两套 --glass-line")
        assertSameColor(lines[0], light.line, "浅色玻璃边")
        assertSameColor(lines[1], dark.line, "墨夜玻璃边")

        // 玻璃底必须半透明：不透明就没有 blur 可采，折射会退化成一块实色
        assertTrue(light.surface.alpha in 0.4f..0.7f && dark.surface.alpha in 0.4f..0.7f)
    }

    @Test
    fun `屏幕底渐变两端与 Golden 同值`() {
        val palettes = listOf(paletteFor(dark = false), paletteFor(dark = true))

        val gradients = Regex("""linear-gradient\(165deg,(#[0-9A-Fa-f]{6}),(#[0-9A-Fa-f]{6})\)""")
            .findAll(css).toList()
        assertEquals(2, gradients.size, "Golden 应有两处 165° 屏幕底渐变")
        gradients.forEachIndexed { i, m ->
            val top = Color(("FF" + m.groupValues[1].removePrefix("#")).toLong(16))
            val bottom = Color(("FF" + m.groupValues[2].removePrefix("#")).toLong(16))
            assertSameColor(top, palettes[i].screenTop, "第 ${i + 1} 套皮肤的渐变起点")
            assertSameColor(bottom, palettes[i].screenBottom, "第 ${i + 1} 套皮肤的渐变终点")
        }
    }

    @Test
    fun `三团光斑的尺寸位置与颜色对齐 Golden`() {
        val blobs = Regex("""radial-gradient\((\d+)px (\d+)px at (\d+)% (\d+)%,\s*rgba\((\d+),(\d+),(\d+),([0-9.]+)\)""")
            .findAll(css).toList()
        // 浅色三团 + 墨夜三团
        assertEquals(6, blobs.size, "Golden 应有三团光斑 × 两套皮肤")

        val light = paletteFor(dark = false)
        val dark = paletteFor(dark = true)
        val lightBlobColors = listOf(light.blobTeal, light.blobBlue, light.blobMint)
        val darkBlobColors = listOf(dark.blobTeal, dark.blobBlue, dark.blobMint)

        blobs.forEachIndexed { i, m ->
            val index = i % 3
            val paletteIndex = i / 3
            assertEquals(GlassTokens.blobWidths[index].value.toInt(), m.groupValues[1].toInt(), "光斑宽")
            assertEquals(GlassTokens.blobHeights[index].value.toInt(), m.groupValues[2].toInt(), "光斑高")
            assertEquals(
                (GlassTokens.blobCenterX[index] * 100).roundToInt(),
                m.groupValues[3].toInt(),
                "光斑中心 X",
            )
            assertEquals(
                (GlassTokens.blobCenterY[index] * 100).roundToInt(),
                m.groupValues[4].toInt(),
                "光斑中心 Y",
            )
            val expected = if (paletteIndex == 0) lightBlobColors[index] else darkBlobColors[index]
            // 直接比 rgba 分量：alpha 用 0-1 表示，四舍五入到 8bit 后与 Color 对齐
            val cssColor = Color(
                red = m.groupValues[5].toInt(),
                green = m.groupValues[6].toInt(),
                blue = m.groupValues[7].toInt(),
                alpha = (m.groupValues[8].toFloat() * 255f).roundToInt(),
            )
            assertSameColor(cssColor, expected, "第 $index 团光斑（皮肤 $paletteIndex）")
        }
    }

    @Test
    fun `源码里没有数字圆角`() {
        val numeric = Regex("""RoundedCornerShape\(\s*\d+(\.\d+)?\.dp\s*\)""")
        val offenders = kotlinSources().filter { numeric.containsMatchIn(it.readText()) }
        assertTrue(
            offenders.isEmpty(),
            "圆角只能取 GlassShape.corner / 令牌，禁止写字面量半径：" + offenders.joinToString { it.name },
        )
    }

    @Test
    fun `源码里没有用不透明 surface 冒充玻璃`() {
        val opaque = Regex("""color = (scheme|MaterialTheme\.colorScheme)\.surface\b""")
        val offenders = kotlinSources().filter { opaque.containsMatchIn(it.readText()) }
        assertTrue(
            offenders.isEmpty(),
            "玻璃面必须走 GlassSurface / glassPanel（D-056），这些文件还在铺不透明 surface：" +
                offenders.joinToString { it.name },
        )
    }

    @Test
    fun `弹层只能通过玻璃包装件打开`() {
        val direct = Regex("""(?<![A-Za-z])(ModalBottomSheet|AlertDialog)\(""")
        val offenders = kotlinSources()
            .filter { it.name != "GlassDialog.kt" }
            .filter { direct.containsMatchIn(it.readText()) }
        assertTrue(
            offenders.isEmpty(),
            "弹层必须走 GlassBottomSheet / GlassAlertDialog：这些文件直接调了 M3 容器：" +
                offenders.joinToString { it.name },
        )
    }

    @Test
    fun `抽屉容器必须自带玻璃绘制`() {
        // ModalNavigationDrawer 在主窗口内、能直接采主窗口背景层，所以不走弹层包装件；
        // 但它的 Sheet 必须自己挂 glassPanel，否则容器是不透明的 M3 默认底
        val drawer = File(sourceRoot, "ui/read/ReadSideMenu.kt")
        assertTrue(drawer.exists(), "ReadSideMenu.kt 不在了，检查抽屉是否被改动")
        val text = drawer.readText()
        assertTrue(text.contains("ModalDrawerSheet("), "抽屉应仍用 M3 容器（手势与返回键由它管）")
        assertTrue(
            Regex("""ModalDrawerSheet\([\s\S]{0,400}?glassPanel\(""").containsMatchIn(text),
            "ModalDrawerSheet 必须挂 glassPanel，否则抽屉是一块不透明底",
        )
        assertTrue(
            text.contains("drawerContainerColor = Color.Transparent"),
            "抽屉容器要透明，玻璃才画得出来",
        )
    }

    private fun kotlinSources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
