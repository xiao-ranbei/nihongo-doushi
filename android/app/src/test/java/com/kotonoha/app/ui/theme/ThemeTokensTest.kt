package com.kotonoha.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 双主题地基的契约测试：糖果卡（浅色）与墨夜（深色）两套令牌的关键值。
 *
 * 断言的是 paletteFor / geometryFor / colorSchemeFor / glassColorsFor 这几个纯函数，
 * 不进入 Composable 上下文，所以纯 JVM 就能跑。
 * 钉住的点是「换肤不许漂移」与「圆角不许长出第二个值」：色值或圆角逐字改动会立刻红灯，
 * 而不是等到某个页面的卡片在真机上圆角变钝才被发现。
 */
class ThemeTokensTest {

    @Test
    fun `浅色糖果卡 primary 与圆角单值`() {
        val palette = paletteFor(dark = false)
        assertEquals(Color(0xFF14857F), palette.green)
        assertEquals(Color(0xFF14857F), colorSchemeFor(palette).primary)
        assertEquals(Color(0xFF14857F), palette.brand)

        val geometry = geometryFor(dark = false)
        // 方案 B / D-051：三个别名同源于同一个 24dp，皮肤之间也不许有差异
        assertEquals(GlassTokens.radius, geometry.cardRadius)
        assertEquals(GlassTokens.radius, geometry.smallRadius)
        assertEquals(GlassTokens.radius, geometry.pillRadius)
        assertEquals(24.dp, geometry.cardRadius)
        assertEquals(3.dp, geometry.cardShadow)
        assertTrue(geometry.navFloating)
    }

    @Test
    fun `深色墨夜 primary 与圆角与浅色同值`() {
        val palette = paletteFor(dark = true)
        assertEquals(Color(0xFF7FD3C7), palette.green)
        assertEquals(Color(0xFF7FD3C7), colorSchemeFor(palette).primary)

        val geometry = geometryFor(dark = true)
        val light = geometryFor(dark = false)
        // 两套皮肤同形：圆角不许再按皮肤分叉
        assertEquals(light.cardRadius, geometry.cardRadius)
        assertEquals(light.smallRadius, geometry.smallRadius)
        assertEquals(light.pillRadius, geometry.pillRadius)
        assertEquals(3.dp, geometry.cardShadow)
        assertTrue(geometry.navFloating)
    }

    @Test
    fun `深色背景不是浅色背景`() {
        val light = paletteFor(dark = false)
        val dark = paletteFor(dark = true)

        assertNotEquals(light.paper, dark.paper)
        assertNotEquals(light.surface, dark.surface)
        assertEquals(Color(0xFFE9F1F3), light.paper)
        assertEquals(Color(0xFF0A1218), dark.paper)

        // 分量而不是字面值：墨夜底必须真的暗，糖果卡底必须真的亮
        assertTrue(dark.paper.red < 0.1f && dark.paper.green < 0.1f && dark.paper.blue < 0.12f)
        assertTrue(light.paper.red > 0.9f && light.paper.green > 0.9f && light.paper.blue > 0.9f)
        // 深色下 M3 容器色阶也必须落在墨黑冷调上，不许漏回 M3 默认中性紫
        val darkScheme = colorSchemeFor(dark)
        assertTrue(darkScheme.surfaceContainer.red < 0.2f && darkScheme.surfaceContainer.blue > darkScheme.surfaceContainer.red)
    }

    @Test
    fun `玻璃令牌带 alpha 且两皮肤各成一套`() {
        val light = glassColorsFor(paletteFor(dark = false))
        val dark = glassColorsFor(paletteFor(dark = true))

        // --glass-surface：浅 rgba(255,255,255,.55) / 墨夜 rgba(24,40,50,.55)
        assertEquals(Color(0x8CFFFFFF), light.surface)
        assertEquals(Color(0x8C182832), dark.surface)
        // 玻璃底必须半透明，否则 blur 采不到身后内容，折射退化成一块实色
        assertTrue(light.surface.alpha in 0.4f..0.7f)
        assertTrue(dark.surface.alpha in 0.4f..0.7f)

        // --brand 与圆角一样是全应用唯一强调色
        assertEquals(Color(0xFF14857F), light.brand)
        assertEquals(Color(0xFF7FD3C7), dark.brand)

        // 屏幕底的渐变两端必须不同，否则 165° 渐变等于纯色，光斑外就没有明暗差
        assertNotEquals(light.screenTop, light.screenBottom)
        assertNotEquals(dark.screenTop, dark.screenBottom)
        // 三团光斑同色会让背景只剩单层色雾，这里钉住它们是三个不同色
        assertNotEquals(light.blobTeal, light.blobBlue)
        assertNotEquals(light.blobBlue, light.blobMint)
    }
}
