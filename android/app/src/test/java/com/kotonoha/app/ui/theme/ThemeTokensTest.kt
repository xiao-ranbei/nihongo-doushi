package com.kotonoha.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 双主题地基的契约测试：糖果卡（浅色）与墨夜（深色）两套令牌的关键值。
 *
 * 断言的是 paletteFor / geometryFor / colorSchemeFor 这三个纯函数，
 * 不进入 Composable 上下文，所以纯 JVM 就能跑。
 * 钉住的点是「换肤不许漂移」：色值或圆角逐字改动会立刻红灯，
 * 而不是等到某个页面的卡片在真机上圆角变钝才被发现。
 */
class ThemeTokensTest {

    @Test
    fun `浅色糖果卡 primary 与卡片几何`() {
        val palette = paletteFor(dark = false)
        assertEquals(Color(0xFF2E9463), palette.green)
        assertEquals(Color(0xFF2E9463), colorSchemeFor(palette).primary)

        val geometry = geometryFor(dark = false)
        assertEquals(20.dp, geometry.cardRadius)
        assertEquals(12.dp, geometry.smallRadius)
        assertEquals(3.dp, geometry.cardShadow)
        assertEquals(999.dp, geometry.pillRadius)
        assertTrue(geometry.navFloating)
    }

    @Test
    fun `深色墨夜 primary 与卡片几何`() {
        val palette = paletteFor(dark = true)
        assertEquals(Color(0xFF9FBBD3), palette.green)
        assertEquals(Color(0xFF9FBBD3), colorSchemeFor(palette).primary)

        val geometry = geometryFor(dark = true)
        assertEquals(13.dp, geometry.cardRadius)
        assertEquals(9.dp, geometry.smallRadius)
        assertEquals(0.dp, geometry.cardShadow)
        assertEquals(999.dp, geometry.pillRadius)
        assertFalse(geometry.navFloating)
    }

    @Test
    fun `深色背景不是浅色背景`() {
        val light = paletteFor(dark = false)
        val dark = paletteFor(dark = true)

        assertNotEquals(light.paper, dark.paper)
        assertNotEquals(light.surface, dark.surface)
        assertEquals(Color(0xFFFAFAF8), light.paper)
        assertEquals(Color(0xFF0F1114), dark.paper)

        // 分量而不是字面值：墨夜底必须真的暗，糖果卡底必须真的亮
        assertTrue(dark.paper.red < 0.1f && dark.paper.green < 0.1f && dark.paper.blue < 0.1f)
        assertTrue(light.paper.red > 0.9f && light.paper.green > 0.9f && light.paper.blue > 0.9f)
        // 深色下 M3 容器色阶也必须落在墨黑冷调上，不许漏回 M3 默认中性紫
        val darkScheme = colorSchemeFor(dark)
        assertTrue(darkScheme.surfaceContainer.red < 0.2f && darkScheme.surfaceContainer.blue > darkScheme.surfaceContainer.red)
    }
}
