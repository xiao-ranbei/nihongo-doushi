package com.nihongodoushi.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.nihongodoushi.app.R

/**
 * 纸感主题：色值全部移植自 web/styles.css 的 :root 语义令牌（D-015 / android-requirements §5）。
 * 本文件是全应用唯一的颜色定义处；UI 组件禁止硬编码颜色（P0-3）。
 */
object PaperTokens {
    val Paper = Color(0xFFF6F5F0)        // --paper
    val Surface = Color(0xFFFFFEFA)      // --surface
    val Ink = Color(0xFF26332E)          // --ink
    val Muted = Color(0xFF7B867E)        // --muted
    val Line = Color(0xFFE7E9E1)         // --line
    val Green = Color(0xFF326151)        // --green
    val GreenDark = Color(0xFF254A3D)    // --green-dark
    val GreenPale = Color(0xFFEDF3EB)    // --green-pale
    val Orange = Color(0xFFB8784F)       // --orange
    val OrangePale = Color(0xFFF8EFE6)   // --orange-pale
    val OrangeTextDeep = Color(0xFF895936) // --orange-text-deep
    val Red = Color(0xFFA54E45)          // --red
    val RedPale = Color(0xFFFBF0ED)      // --red-pale
    val TextRed = Color(0xFF9C5B50)      // --text-red
    val ReadingText = Color(0xFF839087)  // --reading
    val MeaningText = Color(0xFF627967)  // --meaning-text
    val TextStrong = Color(0xFF657268)   // --text-strong
    val TextGreen = Color(0xFF657C69)    // --text-green
    val StatCorrectText = Color(0xFF54705A) // --stat-correct-text
    val StatIncorrectText = Color(0xFF98645B) // --stat-incorrect-text
    val LineAccent = Color(0xFFC6D4C7)   // --line-accent
    val BgPaleGreen = Color(0xFFF4F8F2)  // --bg-pale-green
    val BgPaleStrong = Color(0xFFE9EEE7) // --bg-pale-strong
    val LineControl = Color(0xFFDFE5DC)  // --line-control
}

/** 细粒度语义色：Material 3 ColorScheme 覆盖不到的 Web 令牌，经 CompositionLocal 下发 */
data class PaperColors(
    val textStrong: Color,
    val textGreen: Color,
    val statCorrectText: Color,
    val statIncorrectText: Color,
    val textRed: Color,
    val reading: Color,
    val meaning: Color,
    val greenPale: Color,
    val orangePale: Color,
    val line: Color,
    val lineControl: Color,
    val lineAccent: Color,
    val bgPaleGreen: Color,
)

private val LocalPaperColors = staticCompositionLocalOf {
    PaperColors(
        textStrong = PaperTokens.TextStrong,
        textGreen = PaperTokens.TextGreen,
        statCorrectText = PaperTokens.StatCorrectText,
        statIncorrectText = PaperTokens.StatIncorrectText,
        textRed = PaperTokens.TextRed,
        reading = PaperTokens.ReadingText,
        meaning = PaperTokens.MeaningText,
        greenPale = PaperTokens.GreenPale,
        orangePale = PaperTokens.OrangePale,
        line = PaperTokens.Line,
        lineControl = PaperTokens.LineControl,
        lineAccent = PaperTokens.LineAccent,
        bgPaleGreen = PaperTokens.BgPaleGreen,
    )
}

/** 纸感字体：LXGW 楷体 TTF 子集（449KB，字符集复用 web/font/subset-chars.txt） */
val PaperFontFamily = FontFamily(Font(R.font.lxgw_zhenkai_subset))

private val PaperTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PaperFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = PaperFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = PaperFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = PaperFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = PaperFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = PaperFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = PaperFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = PaperFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = PaperFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = PaperFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = PaperFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = PaperFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = PaperFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = PaperFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = PaperFontFamily),
    )
}

private val PaperColorScheme: ColorScheme = lightColorScheme(
    primary = PaperTokens.Green,
    onPrimary = PaperTokens.Surface,
    primaryContainer = PaperTokens.GreenPale,
    onPrimaryContainer = PaperTokens.GreenDark,
    secondary = PaperTokens.TextGreen,
    onSecondary = PaperTokens.Surface,
    secondaryContainer = PaperTokens.GreenPale,
    onSecondaryContainer = PaperTokens.GreenDark,
    tertiary = PaperTokens.Orange,
    onTertiary = PaperTokens.Surface,
    tertiaryContainer = PaperTokens.OrangePale,
    onTertiaryContainer = PaperTokens.OrangeTextDeep,
    error = PaperTokens.Red,
    onError = PaperTokens.Surface,
    errorContainer = PaperTokens.RedPale,
    onErrorContainer = PaperTokens.TextRed,
    background = PaperTokens.Paper,
    onBackground = PaperTokens.Ink,
    surface = PaperTokens.Surface,
    onSurface = PaperTokens.Ink,
    surfaceVariant = Color(0xFFEEF0EB), // --line-faint
    onSurfaceVariant = PaperTokens.Muted,
    outline = PaperTokens.LineControl,
    outlineVariant = PaperTokens.Line,
    // M3 容器色阶全部覆写为纸感色，避免默认中性基色带出的淡紫（P0-2）
    surfaceContainerLowest = PaperTokens.Surface,
    surfaceContainerLow = PaperTokens.Surface,
    surfaceContainer = PaperTokens.BgPaleGreen,
    surfaceContainerHigh = PaperTokens.GreenPale,
    surfaceContainerHighest = PaperTokens.BgPaleStrong,
    surfaceBright = PaperTokens.Surface,
    surfaceDim = PaperTokens.Paper,
)

/** 细粒度语义色访问入口：paperColors().textGreen 等（仅限 Composable 上下文） */
@Composable
fun paperColors(): PaperColors = LocalPaperColors.current

@Composable
fun NihongoDoushiTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPaperColors provides defaultPaperColors()) {
        MaterialTheme(
            colorScheme = PaperColorScheme,
            typography = PaperTypography,
            content = content,
        )
    }
}

private fun defaultPaperColors(): PaperColors = PaperColors(
    textStrong = PaperTokens.TextStrong,
    textGreen = PaperTokens.TextGreen,
    statCorrectText = PaperTokens.StatCorrectText,
    statIncorrectText = PaperTokens.StatIncorrectText,
    textRed = PaperTokens.TextRed,
    reading = PaperTokens.ReadingText,
    meaning = PaperTokens.MeaningText,
    greenPale = PaperTokens.GreenPale,
    orangePale = PaperTokens.OrangePale,
    line = PaperTokens.Line,
    lineControl = PaperTokens.LineControl,
    lineAccent = PaperTokens.LineAccent,
    bgPaleGreen = PaperTokens.BgPaleGreen,
)
