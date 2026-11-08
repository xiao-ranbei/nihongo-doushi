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
    // ── 方案 B 玻璃层（D-050）。值逐条摘自 docs/design/liquid-glass/src/theme-b.css ──
    val screenTop: Color,         // --screen-texture 的 linear-gradient 起点
    val screenBottom: Color,      // --screen-texture 的 linear-gradient 终点
    val blobTeal: Color,          // radial 1 · 16% / 14%
    val blobBlue: Color,          // radial 2 · 88% / 40%
    val blobMint: Color,          // radial 3 · 28% / 88%
    val glassSurface: Color,      // --glass-surface：卡片 / 岛 / 弹层的玻璃底
    val glassLine: Color,         // --glass-line：1dp 玻璃边
    val glassHighlight: Color,    // inset 1dp 顶部高光
    val brand: Color,             // --brand
    val brandOn: Color,           // --brand-on
    val brandSoft: Color,         // --brand-soft
    val scrim: Color,             // --scrim
    val toastSurface: Color,      // --toast-bg
    val dockShadow: Color,        // 悬浮岛投影 0 12 30
    val sheetShadow: Color,       // 弹层投影 0 -14 40
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
    screenTop = Color(0xFFEAF2F4),
    screenBottom = Color(0xFFDFEBEE),
    blobTeal = Color(0x3D148C84),
    blobBlue = Color(0x38468CC8),
    blobMint = Color(0x3378C8B4),
    glassSurface = Color(0x8CFFFFFF),
    glassLine = Color(0xD1FFFFFF),
    glassHighlight = Color(0xB8FFFFFF),
    brand = Color(0xFF14857F),
    brandOn = Color(0xFFFFFFFF),
    brandSoft = Color(0x2414857F),
    scrim = Color(0x42103036),
    toastSurface = Color(0xCCFFFFFF),
    dockShadow = Color(0x28144648),
    sheetShadow = Color(0x2E103C42),
    paper = Color(0xFFE9F1F3),
    surface = Color(0xFFF7FBFB),
    ink = Color(0xFF143036),
    muted = Color(0xFF6D8A8E),
    line = Color(0xFFE4EBEC),
    lineControl = Color(0xFFD3E0E2),
    green = Color(0xFF14857F),
    greenDark = Color(0xFF0C645F),
    greenPale = Color(0xFFDEEFED),
    lineAccent = Color(0xFFADD4D2),
    orange = Color(0xFFD98243),
    orangePale = Color(0xFFFFE6D1),
    orangeTextDeep = Color(0xFF9A5A2A),
    orangeLine = Color(0xFFF3D3B8),
    red = Color(0xFFB4514A),
    redPale = Color(0xFFF8E9E6),
    textRed = Color(0xFFA8504A),
    readingText = Color(0xFF6D8A8E),
    meaningText = Color(0xFF3D5A60),
    textStrong = Color(0xFF3D5A60),
    textGreen = Color(0xFF0C645F),
    statCorrectText = Color(0xFF14857F),
    statIncorrectText = Color(0xFFB4514A),
    bgPaleGreen = Color(0xFFF2F8F8),
    bgPaleStrong = Color(0xFFE3F0F0),
    slateBlue = Color(0xFF5E7392),
    slateBluePale = Color(0xFFE7EDF6),
    slateBlueDeep = Color(0xFF3A4B63),
    slateBlueLine = Color(0xFFD3DEEC),
    surfaceVariant = Color(0xFFE7EEEF),
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
    screenTop = Color(0xFF0C1720),
    screenBottom = Color(0xFF081016),
    blobTeal = Color(0x4D1E7882),
    blobBlue = Color(0x4D3C6EAA),
    blobMint = Color(0x38288C82),
    glassSurface = Color(0x8C182832),
    glassLine = Color(0x4796C8D7),
    glassHighlight = Color(0x3DFFFFFF),
    brand = Color(0xFF7FD3C7),
    brandOn = Color(0xFF0A1218),
    brandSoft = Color(0x267FD3C7),
    scrim = Color(0x80000000),
    toastSurface = Color(0xDB182C36),
    dockShadow = Color(0x52000000),
    sheetShadow = Color(0x6B000000),
    paper = Color(0xFF0A1218),
    surface = Color(0xFF14222C),
    ink = Color(0xFFE8F3F5),
    muted = Color(0xFF7E9AA3),
    line = Color(0xFF1E3038),
    lineControl = Color(0xFF2C4250),
    green = Color(0xFF7FD3C7),
    greenDark = Color(0xFFA6E4DA),
    greenPale = Color(0xFF1B2E36),
    lineAccent = Color(0xFF3B6264),
    orange = Color(0xFFC9927A),
    orangePale = Color(0xFF2E2119),
    orangeTextDeep = Color(0xFFEFCDBA),
    orangeLine = Color(0xFF4A3A2E),
    red = Color(0xFFE79B92),
    redPale = Color(0xFF2A1D1B),
    textRed = Color(0xFFD09186),
    readingText = Color(0xFF7E9AA3),
    meaningText = Color(0xFFAFC6CD),
    textStrong = Color(0xFFAFC6CD),
    textGreen = Color(0xFFA6E4DA),
    statCorrectText = Color(0xFF7FD3C7),
    statIncorrectText = Color(0xFFE79B92),
    bgPaleGreen = Color(0xFF0F1C22),
    bgPaleStrong = Color(0xFF16262E),
    slateBlue = Color(0xFF9FB3CC),
    slateBluePale = Color(0xFF202A38),
    slateBlueDeep = Color(0xFFC3D3E6),
    slateBlueLine = Color(0xFF36465A),
    surfaceVariant = Color(0xFF1B2830),
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
 * 几何令牌：两套皮肤的圆角与阴影。
 *
 * **圆角已收敛成单值**（D-051 / 计划 §0.2.1）：[cardRadius] / [smallRadius] / [pillRadius]
 * 三个别名同源于 [GlassTokens.radius] = 24dp，不再随皮肤或组件变化。
 * 短边不足 48dp 的元素（20dp 徽标、34dp 小按钮）由形状自身裁到短边一半，属几何事实而非第二套值。
 *
 * @param cardRadius 卡片 / 分组容器 / 弹层圆角（= 24dp）
 * @param smallRadius 行内小色块 / 图标盒 / 徽标圆角（= 24dp，短边不足时自动裁）
 * @param cardShadow Surface 的 shadowElevation
 * @param pillRadius 胶囊圆角（= 24dp，纯圆仅剩功能性圆形：头像 / 状态点 / 开关滑块）
 * @param navFloating 历史字段：底栏是否悬浮。方案 B 起两套皮肤都是悬浮岛，恒 true，仅为兼容保留
 */
data class ModuleGeometry(
    val cardRadius: Dp,
    val smallRadius: Dp,
    val cardShadow: Dp,
    val pillRadius: Dp,
    val navFloating: Boolean,
)

private val CandyGeometry = ModuleGeometry(
    cardRadius = GlassTokens.radius,
    smallRadius = GlassTokens.radius,
    cardShadow = 3.dp,
    pillRadius = GlassTokens.radius,
    navFloating = true,
)

private val InkNightGeometry = ModuleGeometry(
    cardRadius = GlassTokens.radius,
    smallRadius = GlassTokens.radius,
    cardShadow = 3.dp,
    pillRadius = GlassTokens.radius,
    navFloating = true,
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
 * 方案 B 的玻璃层令牌（D-050）：卡片 / 悬浮岛 / 弹层 / Toast 的玻璃底、描边、高光与投影。
 *
 * 与 [PaperColors] 分开的理由：纸色是「墨落在纸上」，玻璃色是「光穿过玻璃」，
 * 两者取值口径不同（玻璃多为带 alpha 的白 / 冷蓝），混一张表会让以后调折射时误改正文色。
 */
data class GlassColors(
    /** 屏幕底：光斑所在的两段渐变 + 三团径向光斑，全部由根 backdrop 绘制 */
    val screenTop: Color,
    val screenBottom: Color,
    val blobTeal: Color,
    val blobBlue: Color,
    val blobMint: Color,
    /** 玻璃面板底：浅 `rgba(255,255,255,.55)` / 墨夜 `rgba(24,40,50,.55)` */
    val surface: Color,
    /** 1dp 玻璃边：浅 `rgba(255,255,255,.82)` / 墨夜 `rgba(150,200,215,.28)` */
    val line: Color,
    /** 内顶高光：1dp，往下渐隐 */
    val highlight: Color,
    /** 主色：浅 `#14857F` / 墨夜 `#7FD3C7` */
    val brand: Color,
    val brandOn: Color,
    val brandSoft: Color,
    /** 遮罩：弹层 / 抽屉背后 */
    val scrim: Color,
    val toastSurface: Color,
    /** 悬浮岛投影与弹层投影（只取色，尺寸在 [GlassTokens]） */
    val dockShadow: Color,
    val sheetShadow: Color,
)

private val LocalGlassColors = staticCompositionLocalOf { glassColorsFor(CandyPalette) }

/** 玻璃层令牌访问入口：glassColors().brand 等（仅限 Composable 上下文） */
@Composable
fun glassColors(): GlassColors = LocalGlassColors.current

internal fun glassColorsFor(palette: Palette): GlassColors = GlassColors(
    screenTop = palette.screenTop,
    screenBottom = palette.screenBottom,
    blobTeal = palette.blobTeal,
    blobBlue = palette.blobBlue,
    blobMint = palette.blobMint,
    surface = palette.glassSurface,
    line = palette.glassLine,
    highlight = palette.glassHighlight,
    brand = palette.brand,
    brandOn = palette.brandOn,
    brandSoft = palette.brandSoft,
    scrim = palette.scrim,
    toastSurface = palette.toastSurface,
    dockShadow = palette.dockShadow,
    sheetShadow = palette.sheetShadow,
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
 * 四标签的模块色块实例。
 *
 * 方案 B / D-044 处置：四个实例的色值全部收进青绿家族——原型 `theme-b.css` 只有一个强调色
 * `--brand`（浅 `#14857F` / 墨夜 `#7FD3C7`），页面里没有第二种强调色，因此四实例同值。
 * 保留四个实例而不是删成一个，是因为页面侧仍按标签取 `moduleTone()`（机制不变，未来要给
 * 单个模块换辅色时只改这里一行，不必回头改页面）。
 */
object ModuleTones {
    private val brand = ModuleTone(
        main = CandyPalette.green,
        pale = CandyPalette.greenPale,
        deep = CandyPalette.greenDark,
        line = CandyPalette.lineAccent,
    )

    val Home = brand
    val Study = brand
    val Read = brand
    val Mine = brand
}

private val InkNightBrandTone = ModuleTone(
    main = InkNightPalette.green,
    pale = InkNightPalette.greenPale,
    deep = InkNightPalette.greenDark,
    line = InkNightPalette.lineAccent,
)

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
    // 方案 B 起四模块同族，墨夜只需换成冷调那一份；浅色直接原样返回
    if (LocalPalette.current.dark) InkNightBrandTone else tone

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
        LocalGlassColors provides glassColorsFor(palette),
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette),
            typography = PaperTypography,
            content = content,
        )
    }
}
