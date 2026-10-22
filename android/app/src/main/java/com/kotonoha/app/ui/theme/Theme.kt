package com.kotonoha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotonoha.app.R

/**
 * 双主题：浅色「糖果卡」、深色「墨夜」，完全跟随系统深浅色，不提供手动开关。
 * 本文件是全应用唯一的颜色与几何定义处；UI 组件禁止硬编码颜色（P0-3）。
 */

/**
 * 一套主题的全部语义色。糖果卡与墨夜各一份，字段一一对应：
 * 换肤只换这张表，四个取色入口（MaterialTheme.colorScheme / paperColors / moduleTone / geometry）不变。
 */
internal data class Palette(
    val dark: Boolean,
    val paper: Color,             // --paper
    val surface: Color,           // --surface
    val ink: Color,               // --ink
    val muted: Color,             // --muted
    val line: Color,              // --line
    val lineControl: Color,       // --line-control
    val green: Color,             // --green（深色下即 M3 primary 冷蓝）
    val greenDark: Color,         // --green-dark
    val greenPale: Color,         // --green-pale
    val lineAccent: Color,        // --line-accent
    val orange: Color,            // --orange
    val orangePale: Color,        // --orange-pale
    val orangeTextDeep: Color,    // --orange-text-deep
    val orangeLine: Color,
    val red: Color,               // --red
    val redPale: Color,           // --red-pale
    val textRed: Color,           // --text-red
    val readingText: Color,       // --reading
    val meaningText: Color,       // --meaning-text
    val textStrong: Color,        // --text-strong
    val textGreen: Color,         // --text-green
    val statCorrectText: Color,   // --stat-correct-text
    val statIncorrectText: Color, // --stat-incorrect-text
    val bgPaleGreen: Color,       // --bg-pale-green
    val bgPaleStrong: Color,      // --bg-pale-strong
    val slateBlue: Color,
    val slateBluePale: Color,
    val slateBlueDeep: Color,
    val slateBlueLine: Color,
    val surfaceVariant: Color,
    // 逐词区品詞底色块：13 项品詞归成 9 组，同组共用一色。
    // 浅色是实色；深色是带 alpha 的叠色，让它们叠在 surface 上自己混。
    val posNoun: Color,
    val posPronoun: Color,
    val posVerb: Color,
    val posAdjective: Color,
    val posNaAdjective: Color,
    val posAdverb: Color,
    val posOther: Color,
    val posParticle: Color,
    val posAuxiliary: Color,
)

/** 浅色 = 糖果卡：白卡 + 大圆角 + 柔投影 + 竹绿强调色。 */
private val CandyPalette = Palette(
    dark = false,
    paper = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF212430),
    muted = Color(0xFF8A8D9C),
    line = Color(0xFFEFF0F4),
    lineControl = Color(0xFFE4E7EE),
    green = Color(0xFF2E9463),
    greenDark = Color(0xFF1F6B47),
    greenPale = Color(0xFFE9F7EF),
    lineAccent = Color(0xFFCFE9DA),
    orange = Color(0xFFD98243),
    orangePale = Color(0xFFFFE6D1),
    orangeTextDeep = Color(0xFF9A5A2A),
    orangeLine = Color(0xFFF3D3B8),
    red = Color(0xFFD2544C),
    redPale = Color(0xFFFFE7E3),
    textRed = Color(0xFFB05048),
    readingText = Color(0xFF8A8D9C),
    meaningText = Color(0xFF6C7D72),
    textStrong = Color(0xFF6E7180),
    textGreen = Color(0xFF4E8C74),
    statCorrectText = Color(0xFF2E9463),
    statIncorrectText = Color(0xFFB4635B),
    bgPaleGreen = Color(0xFFF4F8F4),
    bgPaleStrong = Color(0xFFEAF2EC),
    slateBlue = Color(0xFF5E7392),
    slateBluePale = Color(0xFFE7EDF6),
    slateBlueDeep = Color(0xFF3A4B63),
    slateBlueLine = Color(0xFFD3DEEC),
    surfaceVariant = Color(0xFFEEF0F5),
    posNoun = Color(0xFFDBF2E4),
    posPronoun = Color(0xFFD9EEF0),
    posVerb = Color(0xFFFFE6D1),
    posAdjective = Color(0xFFDAE8FB),
    posNaAdjective = Color(0xFFEDE3FB),
    posAdverb = Color(0xFFFBF3D6),
    posOther = Color(0xFFEEF0F5),
    posParticle = Color(0xFFD7EDEC),
    posAuxiliary = Color(0xFFE2E5F6),
)

/** 深色 = 墨夜：墨黑底 + 冷蓝强调 + 小圆角 + 零阴影。 */
private val InkNightPalette = Palette(
    dark = true,
    paper = Color(0xFF0F1114),
    surface = Color(0xFF181C21),
    ink = Color(0xFFE9E7E2),
    muted = Color(0xFF8B93A0),
    line = Color(0xFF262B33),
    lineControl = Color(0xFF333A44),
    green = Color(0xFF9FBBD3),
    greenDark = Color(0xFFCFE0EE),
    greenPale = Color(0xFF1D2831),
    lineAccent = Color(0xFF3B4A57),
    orange = Color(0xFFC9927A),
    orangePale = Color(0xFF2E2119),
    orangeTextDeep = Color(0xFFEFCDBA),
    orangeLine = Color(0xFF4A3A2E),
    red = Color(0xFFD98B80),
    redPale = Color(0xFF2A1D1B),
    textRed = Color(0xFFD09186),
    readingText = Color(0xFF8490A0),
    meaningText = Color(0xFF93A8A2),
    textStrong = Color(0xFFA9B4BE),
    textGreen = Color(0xFF9CC0B4),
    statCorrectText = Color(0xFF8FC0A0),
    statIncorrectText = Color(0xFFD0928A),
    bgPaleGreen = Color(0xFF14181A),
    bgPaleStrong = Color(0xFF1C2226),
    slateBlue = Color(0xFF9FB3CC),
    slateBluePale = Color(0xFF202A38),
    slateBlueDeep = Color(0xFFC3D3E6),
    slateBlueLine = Color(0xFF36465A),
    surfaceVariant = Color(0xFF20262E),
    posNoun = Color(0x299FBBD3),
    posPronoun = Color(0x249FBBD3),
    posVerb = Color(0x38CE6E58),
    posAdjective = Color(0x33AE98E0),
    posNaAdjective = Color(0x33C9A0E0),
    posAdverb = Color(0x2ED9C978),
    posOther = Color(0x29A0A8B0),
    posParticle = Color(0x2978C4A8),
    posAuxiliary = Color(0x2E8FA0C0),
)

/** 纯函数取表：不依赖 Composable，单测可直接钉住两套皮肤的值。 */
internal fun paletteFor(dark: Boolean): Palette = if (dark) InkNightPalette else CandyPalette

/**
 * 几何令牌：两套皮肤的圆角与阴影差异。
 * - [cardRadius] 一级卡片/入口卡圆角
 * - [smallRadius] 行/小色块/图标盒圆角
 * - [cardShadow] Surface 的 shadowElevation
 * - [pillRadius] 胶囊圆角
 * - [navFloating] 底栏是否悬浮药丸
 */
data class ModuleGeometry(
    val cardRadius: Dp,
    val smallRadius: Dp,
    val cardShadow: Dp,
    val pillRadius: Dp,
    val navFloating: Boolean,
)

private val CandyGeometry = ModuleGeometry(
    cardRadius = 20.dp,
    smallRadius = 12.dp,
    cardShadow = 3.dp,
    pillRadius = 999.dp,
    navFloating = true,
)

private val InkNightGeometry = ModuleGeometry(
    cardRadius = 13.dp,
    smallRadius = 9.dp,
    cardShadow = 0.dp,
    pillRadius = 999.dp,
    navFloating = false,
)

/** 纯函数取几何：不依赖 Composable，单测可直接钉住两套皮肤的圆角与阴影。 */
internal fun geometryFor(dark: Boolean): ModuleGeometry =
    if (dark) InkNightGeometry else CandyGeometry

/** 细粒度语义色：Material 3 ColorScheme 覆盖不到的令牌，经 CompositionLocal 下发 */
data class PaperColors(
    val textStrong: Color,
    val textGreen: Color,
    val muted: Color,
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

private val LocalPalette = staticCompositionLocalOf { CandyPalette }
private val LocalGeometry = staticCompositionLocalOf { CandyGeometry }
private val LocalPaperColors = staticCompositionLocalOf { paperColorsFor(CandyPalette) }

/** 细粒度语义色访问入口：paperColors().textGreen 等（仅限 Composable 上下文） */
@Composable
fun paperColors(): PaperColors = LocalPaperColors.current

/** 几何令牌访问入口：geometry().cardRadius 等（仅限 Composable 上下文） */
@Composable
fun geometry(): ModuleGeometry = LocalGeometry.current

internal fun paperColorsFor(palette: Palette): PaperColors = PaperColors(
    textStrong = palette.textStrong,
    textGreen = palette.textGreen,
    muted = palette.muted,
    statCorrectText = palette.statCorrectText,
    statIncorrectText = palette.statIncorrectText,
    textRed = palette.textRed,
    reading = palette.readingText,
    meaning = palette.meaningText,
    greenPale = palette.greenPale,
    orangePale = palette.orangePale,
    line = palette.line,
    lineControl = palette.lineControl,
    lineAccent = palette.lineAccent,
    bgPaleGreen = palette.bgPaleGreen,
)

/**
 * 品詞 → 逐词区底色；未收录的品詞返回 null（不铺色块）。
 * 逐词区不再渲染品詞文字，色块是唯一的品詞线索，因此只给模型须守的 13 项封闭集合配色：
 * 模型偶尔吐出的其它值保持无底色，免得把不认识的东西染成看起来有意义的颜色。
 */
@Composable
fun posBackgroundColor(pos: String): Color? {
    val palette = LocalPalette.current
    return when (pos) {
        "名詞" -> palette.posNoun
        "代名詞" -> palette.posPronoun
        "動詞" -> palette.posVerb
        "形容詞" -> palette.posAdjective
        "形容動詞" -> palette.posNaAdjective
        "副詞" -> palette.posAdverb
        "連体詞", "接続詞", "感動詞" -> palette.posOther
        "助詞" -> palette.posParticle
        "助動詞" -> palette.posAuxiliary
        else -> null
    }
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

/** 浅色糖果卡：M3 色板全部换成本主题令牌，不留 M3 默认中性基色带出的淡紫（P0-2）。 */
private fun candyColorScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.green,
    onPrimary = p.surface,
    primaryContainer = p.greenPale,
    onPrimaryContainer = p.greenDark,
    secondary = p.textGreen,
    onSecondary = p.surface,
    secondaryContainer = p.greenPale,
    onSecondaryContainer = p.greenDark,
    tertiary = p.orange,
    onTertiary = p.surface,
    tertiaryContainer = p.orangePale,
    onTertiaryContainer = p.orangeTextDeep,
    error = p.red,
    onError = p.surface,
    errorContainer = p.redPale,
    onErrorContainer = p.textRed,
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surfaceVariant,
    onSurfaceVariant = p.muted,
    outline = p.lineControl,
    outlineVariant = p.line,
    surfaceContainerLowest = p.surface,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.bgPaleGreen,
    surfaceContainerHigh = p.greenPale,
    surfaceContainerHighest = p.bgPaleStrong,
    surfaceBright = p.surface,
    surfaceDim = p.paper,
)

/** 深色墨夜：同样整组覆写，容器色阶全部落在墨黑冷调上，不留 M3 默认中性紫。 */
private fun inkNightColorScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.green,
    onPrimary = p.paper,
    primaryContainer = Color(0xFF263340),
    onPrimaryContainer = p.greenDark,
    secondary = p.textGreen,
    onSecondary = p.paper,
    secondaryContainer = Color(0xFF223330),
    onSecondaryContainer = Color(0xFFC9E2DA),
    tertiary = p.orange,
    onTertiary = p.paper,
    tertiaryContainer = Color(0xFF33241D),
    onTertiaryContainer = p.orangeTextDeep,
    error = p.red,
    onError = p.paper,
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFF0C0B8),
    background = p.paper,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surfaceVariant,
    onSurfaceVariant = p.muted,
    outline = p.lineControl,
    outlineVariant = p.line,
    surfaceContainerLowest = Color(0xFF14181C),
    surfaceContainerLow = Color(0xFF1A1F24),
    surfaceContainer = Color(0xFF1C2226),
    surfaceContainerHigh = Color(0xFF212931),
    surfaceContainerHighest = Color(0xFF262E36),
    surfaceBright = Color(0xFF232A31),
    surfaceDim = Color(0xFF0B0D10),
)

internal fun colorSchemeFor(palette: Palette): ColorScheme =
    if (palette.dark) inkNightColorScheme(palette) else candyColorScheme(palette)

/**
 * 一个模块的一组色块：主色 / 浅底 / 深字 / 描边。
 * 四条色各自有固定用途，UI 不得跨用：
 * - [main]  图标、选中强调、进度
 * - [pale]  图标底盒、选中胶囊底
 * - [deep]  落在纸白上的小字（labelMedium 及以下），主色对小字对比度不足时用它
 * - [line]  该模块色系的描边
 */
data class ModuleTone(
    val main: Color,
    val pale: Color,
    val deep: Color,
    val line: Color,
)

/**
 * 四标签 + 主页的模块色块实例，取浅色糖果卡的值。
 * 深色墨夜的对应值由 [ProvideModuleTone] 按同一族自动换表，页面侧无需分支。
 * 主页是总览页，与学习同族（品牌绿）。
 */
object ModuleTones {
    val Home = ModuleTone(
        main = CandyPalette.green,
        pale = CandyPalette.greenPale,
        deep = CandyPalette.greenDark,
        line = CandyPalette.lineAccent,
    )
    val Study = ModuleTone(
        main = CandyPalette.green,
        pale = CandyPalette.greenPale,
        deep = CandyPalette.greenDark,
        line = CandyPalette.lineAccent,
    )
    val Read = ModuleTone(
        main = CandyPalette.orange,
        pale = CandyPalette.orangePale,
        deep = CandyPalette.orangeTextDeep,
        line = CandyPalette.orangeLine,
    )
    val Mine = ModuleTone(
        main = CandyPalette.slateBlue,
        pale = CandyPalette.slateBluePale,
        deep = CandyPalette.slateBlueDeep,
        line = CandyPalette.slateBlueLine,
    )
}

private val InkNightHomeTone = ModuleTone(
    main = InkNightPalette.green,
    pale = InkNightPalette.greenPale,
    deep = InkNightPalette.greenDark,
    line = InkNightPalette.lineAccent,
)

private val InkNightReadTone = ModuleTone(
    main = InkNightPalette.orange,
    pale = InkNightPalette.orangePale,
    deep = InkNightPalette.orangeTextDeep,
    line = InkNightPalette.orangeLine,
)

private val InkNightMineTone = ModuleTone(
    main = InkNightPalette.slateBlue,
    pale = InkNightPalette.slateBluePale,
    deep = InkNightPalette.slateBlueDeep,
    line = InkNightPalette.slateBlueLine,
)

/** 浅色色块 → 墨夜同族色块；Home 与 Study 同族（数据相等），一并落到绿系。 */
private fun inkNightToneOf(tone: ModuleTone): ModuleTone = when (tone) {
    ModuleTones.Read -> InkNightReadTone
    ModuleTones.Mine -> InkNightMineTone
    else -> InkNightHomeTone
}

/** 当前模块色块访问入口：moduleTone().main 等（仅限 Composable 上下文） */
private val LocalModuleTone = staticCompositionLocalOf { ModuleTones.Home }

@Composable
fun moduleTone(): ModuleTone = LocalModuleTone.current

/**
 * 把浅色色块实例解析成当前主题下的同族色块：浅色原样返回，深色换成墨夜同色系。
 * 非 Composable 代码拿不到主题，必须经这里；Composable 里若自己又取了一遍 ModuleTones 常量，也要用这个方法归一。
 */
@Composable
fun themedTone(tone: ModuleTone): ModuleTone =
    if (LocalPalette.current.dark) inkNightToneOf(tone) else tone

/**
 * 给一段 UI 指定模块色块。一级页在 AppRoot 里按当前标签包一层，
 * 页面内的页头/色块/底栏因此自动取到本模块的颜色，不必层层传参。
 * 深色主题下在这里把浅色色块换成墨夜同族色，调用方仍只传 ModuleTones 的实例。
 */
@Composable
fun ProvideModuleTone(tone: ModuleTone, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalModuleTone provides themedTone(tone), content = content)
}

@Composable
fun KotonohaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(darkTheme)
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalGeometry provides geometryFor(darkTheme),
        LocalPaperColors provides paperColorsFor(palette),
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette),
            typography = PaperTypography,
            content = content,
        )
    }
}
