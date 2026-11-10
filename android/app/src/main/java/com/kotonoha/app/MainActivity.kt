package com.kotonoha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kotonoha.app.storage.LocalStore
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.adjective.AdjectiveFlow
import com.kotonoha.app.ui.glass.DockPreview
import com.kotonoha.app.ui.glass.DockVariant
import com.kotonoha.app.ui.glass.metalEdge
import com.kotonoha.app.ui.glass.metalPanel
import com.kotonoha.app.ui.glass.metalRing
import com.kotonoha.app.ui.glass.GlassLayer
import com.kotonoha.app.ui.glass.Layer
import com.kotonoha.app.ui.glass.GlassShape
import com.kotonoha.app.ui.glass.GlassSurface
import com.kotonoha.app.ui.home.HomeScreen
import com.kotonoha.app.ui.mine.MineScreen
import com.kotonoha.app.ui.mistakes.MistakesScreen
import com.kotonoha.app.ui.particle.ParticleFlow
import com.kotonoha.app.ui.practice.AdjectivePracticeScreen
import com.kotonoha.app.ui.practice.AdjectivePracticeState
import com.kotonoha.app.ui.practice.adjectiveStateSaver
import com.kotonoha.app.ui.practice.VerbPracticeScreen
import com.kotonoha.app.ui.practice.VerbPracticeState
import com.kotonoha.app.ui.practice.verbStateSaver
import com.kotonoha.app.ui.read.ModelSettingsScreen
import com.kotonoha.app.ui.read.ReadFlow
import com.kotonoha.app.ui.read.ReadScreen
import com.kotonoha.app.ui.study.StudyDestination
import com.kotonoha.app.ui.study.StudyScreen
import com.kotonoha.app.ui.theme.GlassColors
import com.kotonoha.app.ui.theme.GlassMotion
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.ModuleTone
import com.kotonoha.app.ui.theme.ModuleTones
import com.kotonoha.app.ui.theme.KotonohaTheme
import com.kotonoha.app.ui.theme.ProvideModuleTone
import com.kotonoha.app.ui.theme.glassColors
import com.kotonoha.app.ui.theme.paperColors
import com.kotonoha.app.ui.verb.VerbFlow
import kotlin.math.abs

/**
 * 底栏四个一级标签（2026-10-17 四标签框架）。
 * 图标在 [AppBottomBar] 内统一解析：主页/我的用 Material Icons core，
 * 学习/阅读用新增 VectorDrawable（ic_tab_study / ic_tab_read）。
 */
enum class AppTab(val label: String) {
    HOME("主页"),
    STUDY("学习"),
    READ("阅读"),
    MINE("我的"),
}

/**
 * 标签 → 模块色块。色值全部在 ui/theme/Theme.kt 的 ModuleTones 里，
 * 这里只做「哪个标签用哪一套」的绑定，一级页与底栏共用同一份映射。
 */
private fun toneOf(tab: AppTab): ModuleTone = when (tab) {
    AppTab.HOME -> ModuleTones.Home
    AppTab.STUDY -> ModuleTones.Study
    AppTab.READ -> ModuleTones.Read
    AppTab.MINE -> ModuleTones.Mine
}

/** 二级页标识（可保存进 Bundle，故用字符串常量） */
private object SubPage {
    const val VERB = "verb"
    const val ADJECTIVE = "adjective"
    const val PARTICLE = "particle"
    const val MISTAKES = "mistakes"
    const val READ_AI = "read-ai"
    const val MODEL_SETTINGS = "model-settings"
}

/** 学习目的地 → 二级页（主页建议与学习页入口共用） */
private fun StudyDestination.subPage(): String = when (this) {
    StudyDestination.VERB -> SubPage.VERB
    StudyDestination.ADJECTIVE -> SubPage.ADJECTIVE
    StudyDestination.PARTICLE -> SubPage.PARTICLE
    StudyDestination.MISTAKES -> SubPage.MISTAKES
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 两块系统栏都要透明的 scrim：AndroidX 默认在 API 29–34 给状态栏叠一层 0xE6FFFFFF，
        // 在纸底上就是一条灰带（API 35+ 系统强制 edge-to-edge 才看不到）。
        // auto() 仍按系统深浅色切图标明暗，只是不留底色。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            KotonohaTheme(darkTheme = isSystemInDarkTheme()) {
                // 状态提升到根级：切换标签 / 进出二级页都不丢练习进度；
                // 进程重启后按 Saver 恢复进行中的一轮（IK-04）
                val store = remember { LocalStore(applicationContext.filesDir) }
                val appState = remember { AppState(store) }
                val verbState = rememberSaveable(saver = verbStateSaver(appState)) {
                    VerbPracticeState(appState)
                }
                val adjectiveState = rememberSaveable(saver = adjectiveStateSaver(appState)) {
                    AdjectivePracticeState(appState)
                }
                AppRoot(appState, verbState, adjectiveState)
            }
        }
    }
}

@Composable
private fun AppRoot(
    appState: AppState,
    verbState: VerbPracticeState,
    adjectiveState: AdjectivePracticeState,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    // 从错题本进入助词模块时：记来源（退出时回错题本）并直接开复习
    var particleFromMistakes by rememberSaveable { mutableStateOf(false) }
    var particleAutoReview by rememberSaveable { mutableStateOf(false) }
    var verbFromMistakes by rememberSaveable { mutableStateOf(false) }
    var adjectiveFromMistakes by rememberSaveable { mutableStateOf(false) }

    // 二级页用系统返回键回到一级页
    BackHandler(enabled = sub != null) { sub = null }

    // Tab 内容入场位移（8dp）换算成像素：slideInVertically 的偏移量是像素 int，不在密度作用域里
    val pageShiftPx = with(LocalDensity.current) { GlassMotion.pageShift.dp.roundToPx() }

    Scaffold(
        // 底色透明：屏幕底由 GlassLayer 的录制层绘制（渐变 + 三团光斑）。
        // Scaffold 再铺一层不透明色会把光斑盖掉，玻璃就只剩模糊、没有可折射的内容
        containerColor = Color.Transparent,
        // 底栏不放进 bottomBar：它要浮在内容之上、被内容折射（覆盖浮法），
        // 而且必须在 GlassLayer 之外——放进录制层会与 drawBackdrop 形成绘制环
    ) { innerPadding ->
        // 带底部抽屉/操作条的二级页自己处理底部内边距：白条要一直铺到导航栏下方，
        // 不能留出 Scaffold 的底色缝；错题本没有底部条，仍用 Scaffold 的内边距
        val layoutDirection = LocalLayoutDirection.current
        val bottomInset = when (sub) {
            // 一级页：内容一直铺到屏幕底，从悬浮岛后方穿过，岛下留白由各页自己加
            null -> 0.dp
            SubPage.MISTAKES -> innerPadding.calculateBottomPadding()
            else -> 0.dp
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // 录制层：屏幕底 + 页面内容。悬浮岛是本层的兄弟节点（见下方），
            // 它采样 Layer.Content 才能做出「内容从岛后方穿过并被折射」的覆盖浮法
            GlassLayer(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = bottomInset,
                        ),
                ) {
            when (sub) {
                SubPage.VERB -> VerbFlow(
                    app = appState,
                    verbState = verbState,
                    onExit = {
                        sub = if (verbFromMistakes) SubPage.MISTAKES else null
                        verbFromMistakes = false
                    },
                )
                SubPage.ADJECTIVE -> AdjectiveFlow(
                    app = appState,
                    adjectiveState = adjectiveState,
                    onExit = {
                        sub = if (adjectiveFromMistakes) SubPage.MISTAKES else null
                        adjectiveFromMistakes = false
                    },
                )
                SubPage.PARTICLE -> ParticleFlow(
                    app = appState,
                    onExit = {
                        sub = if (particleFromMistakes) SubPage.MISTAKES else null
                        particleFromMistakes = false
                    },
                    autoReview = particleAutoReview,
                    onAutoReviewConsumed = { particleAutoReview = false },
                )
                SubPage.MISTAKES -> MistakesScreen(
                    appState,
                    verbState,
                    adjectiveState,
                    onBack = { sub = null },
                    onReviewVerb = {
                        verbFromMistakes = true
                        sub = SubPage.VERB
                    },
                    onReviewAdjective = {
                        adjectiveFromMistakes = true
                        sub = SubPage.ADJECTIVE
                    },
                    onReviewParticle = {
                        particleFromMistakes = true
                        particleAutoReview = true
                        sub = SubPage.PARTICLE
                    },
                )
                // 阅读器内的模型设置走 ReadFlow 自己的 SETTINGS 路由（见 ReadFlow 注释），
                // 这里的两条是给「我的」页与阅读工具的独立二级页入口
                SubPage.READ_AI -> ReadFlow(
                    app = appState,
                    onExit = { sub = null },
                )
                SubPage.MODEL_SETTINGS -> ModelSettingsScreen(
                    app = appState,
                    onBack = { sub = null },
                )
                // 一级页统一包一层模块色块：页面内的页头/图标底盒/区块标题
                // 通过 moduleTone() 取到本标签的颜色，不必层层传参。
                // 切换标签时内容淡入 + 8dp 上移（M17）：只动 alpha 与位移，不做共享元素、不做弹跳
                else -> AnimatedContent(
                    targetState = AppTab.valueOf(tab),
                    transitionSpec = {
                        (
                            fadeIn(tween(GlassMotion.base, easing = GlassMotion.enter)) +
                                slideInVertically(tween(GlassMotion.base, easing = GlassMotion.enter)) { pageShiftPx }
                            ).togetherWith(
                            fadeOut(tween(GlassMotion.exit, easing = GlassMotion.exitEasing)),
                        )
                    },
                    label = "tab-content",
                ) { current ->
                    ProvideModuleTone(toneOf(current)) {
                        when (current) {
                            AppTab.HOME -> HomeScreen(
                                app = appState,
                                onSelectTab = { tab = it.name },
                                onOpen = { dest -> sub = dest.subPage() },
                            )
                            AppTab.STUDY -> StudyScreen(
                                app = appState,
                                onOpen = { dest -> sub = dest.subPage() },
                            )
                            AppTab.READ -> ReadScreen(
                                app = appState,
                                onOpen = { tool -> if (tool.id == "ai-reading") sub = SubPage.READ_AI },
                            )
                            AppTab.MINE -> MineScreen(
                                app = appState,
                                onOpenModelSettings = { sub = SubPage.MODEL_SETTINGS },
                            )
                        }
                    }
                }
                }
                }
            }
            // 悬浮岛：贴底浮起，左右与底部各 16dp。它采样 Layer.Content（内容层），
            // 且必须是 GlassLayer 的兄弟节点——放进录制层会与 drawBackdrop 形成绘制环
            if (sub == null) {
                AppBottomBar(
                    current = AppTab.valueOf(tab),
                    onSelect = { tab = it.name },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = GlassTokens.islandInset,
                            end = GlassTokens.islandInset,
                            bottom = GlassTokens.islandInset,
                        ),
                )
            }
        }
    }
}

/**
 * 悬浮岛底栏。壳体形态随选型档位变化（见 [DockVariant]），内部结构不变：
 * 一层选中块 + 一层四个标签。
 *
 * 三条与旧实现的差异，都是「必须」而不是偏好：
 * 1. **脱离屏幕**：左右与底部各 16dp，岛不再铺到导航栏下方；内容从岛后方穿过并被折射（覆盖浮法）。
 * 2. **四角同 r**：取消「顶圆角 + 底部直角」，也不再按皮肤切换悬浮/平铺——两套皮肤同形，只换配色。
 * 3. **选中态有实体**：不再只换字色，选中格背后有一块液态金属（折射 30/36、斜向金属渐变、
 *    顶部 2dp 硬高光、内侧品牌辉光），切换时它从旧格滑到新格并做一次横向收缩回弹。
 *
 * 为什么不用 M3 NavigationBar：它的选中态由指示条 + 填充色阶驱动，label 颜色还被内部 token 强制，
 * 与「玻璃岛 + 单块金属」的口径对不上。
 *
 * 读屏口径与原先一致：整格一个焦点站（`selectable` 自带合并子语义，禁手工 mergeDescendants）、
 * role = Tab 供读屏报「标签页」，选中态由 selected 语义而非颜色表达。
 */
@Composable
private fun AppBottomBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val variant = DockPreview.variant
    val colors = glassColors()
    val paper = paperColors()
    val shell = modifier.fillMaxWidth().navigationBarsPadding()

    DockShell(variant = variant, modifier = shell) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = 4.dp
            val inner = maxWidth - GlassTokens.islandPaddingH * 2
            val cell = (inner - spacing * 3) / 4
            val index = AppTab.entries.indexOf(current)
            val target = GlassTokens.islandPaddingH + (cell + spacing) * index

            if (variant != DockVariant.MetalBar) {
                DockIndicator(variant = variant, cell = cell, target = target, colors = colors)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = GlassTokens.islandPaddingH,
                        vertical = GlassTokens.islandPaddingV,
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                AppTab.entries.forEach { entry ->
                    BottomTabItem(
                        entry = entry,
                        selected = current == entry,
                        // 整条金属形态没有滑块，选中格改用一圈品牌光环表达
                        ring = variant == DockVariant.MetalBar && current == entry,
                        onClick = { onSelect(entry) },
                        muted = paper.muted,
                        brand = colors.brand,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 底栏壳体：整条金属形态把岛本身换成金属材质，其余形态仍是普通玻璃岛。 */
@Composable
private fun DockShell(
    variant: DockVariant,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (variant == DockVariant.MetalBar) {
        // 整条铺金属底会变成一根碧玉条、压过页面内容；这里只要「玻璃岛 + 金属包边」
        GlassSurface(
            modifier = modifier.metalEdge(),
            layer = Layer.Content,
            elevation = GlassTokens.islandElevation,
            content = content,
        )
    } else {
        GlassSurface(
            modifier = modifier,
            layer = Layer.Content,
            elevation = GlassTokens.islandElevation,
            content = content,
        )
    }
}

/**
 * 选中块的三种形态。位置与格宽由调用方算好传进来，这里只负责「怎么画 + 怎么迁移」。
 *
 * 迁移统一是「滑动 + 横向收缩回弹」：`animateDpAsState` 负责位移（340ms，起步快收尾缓），
 * `Animatable` 在每次切换时 snapTo(0.92) 再 spring 回 1.0（dampingRatio 0.85，只过冲一点点）。
 * 收缩围绕格中心进行，所以视觉上是「液体被拉动后回弹」，不是「块从左往右长出来」。
 */
@Composable
private fun DockIndicator(
    variant: DockVariant,
    cell: Dp,
    target: Dp,
    colors: com.kotonoha.app.ui.theme.GlassColors,
) {
    val x by animateDpAsState(
        targetValue = target,
        animationSpec = tween(GlassMotion.emphasized + 20, easing = GlassMotion.enter),
        label = "dock-indicator-x",
    )
    val squash = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        // 首帧不弹：App 启动时块应该已经就位，不该先收一下再展开
        if (first) {
            first = false
            return@LaunchedEffect
        }
        squash.snapTo(GlassTokens.metalSquash)
        squash.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
    }

    when (variant) {
        DockVariant.Slab, DockVariant.Trail -> {
            val w = cell * squash.value
            Box(
                modifier = Modifier
                    .offset(x = x + (cell - w) / 2, y = GlassTokens.islandPaddingV)
                    .width(w)
                    .height(GlassTokens.metalSlabHeight)
                    .metalPanel(layer = Layer.Content, colors = colors),
            )
            if (variant == DockVariant.Trail) {
                // 连接带：只在迁移途中存在，宽度等于「块离目标还有多远」，
                // 所以它天然随块靠近而缩短——看起来像被拉过去的水银尾巴
                val distance = abs(x.value - target.value)
                if (distance > 1f) {
                    val trail = Dp(distance)
                    Box(
                        modifier = Modifier
                            .offset(
                                x = Dp(minOf(x.value, target.value)) + cell / 2 - trail / 2,
                                y = GlassTokens.islandPaddingV + GlassTokens.metalSlabHeight -
                                    GlassTokens.metalTrailHeight - 8.dp,
                            )
                            .width(trail)
                            .height(GlassTokens.metalTrailHeight)
                            .background(
                                color = colors.brand.copy(alpha = 0.32f),
                                shape = RoundedCornerShape(GlassTokens.metalTrailHeight / 2),
                            ),
                    )
                }
            }
        }

        DockVariant.Capsule -> {
            val w = GlassTokens.metalCapsuleWidth
            // 图标槽中心在 tab 顶部下方 18dp（26dp 槽 + 2dp 间距 + 12dp 文字，整体居中），
            // 胶囊要咬住图标而不是连文字一起吞：中心对齐 18dp
            val y = GlassTokens.islandPaddingV + 17.dp - GlassTokens.metalCapsuleHeight / 2
            Box(
                modifier = Modifier
                    .offset(x = x + (cell - w) / 2, y = y)
                    .width(w)
                    .height(GlassTokens.metalCapsuleHeight)
                    .metalPanel(
                        layer = Layer.Content,
                        colors = colors,
                        elevation = 8.dp,
                    ),
            )
        }

        DockVariant.MetalBar -> Unit
    }
}

/** 标签 → 矢量图标。四枚都是 24 viewBox 描边图标，与原型 `src/icons/tab-*.svg` 同形。 */
private val AppTab.iconRes: Int
    get() = when (this) {
        AppTab.HOME -> R.drawable.ic_tab_home
        AppTab.STUDY -> R.drawable.ic_tab_study
        AppTab.READ -> R.drawable.ic_tab_read
        AppTab.MINE -> R.drawable.ic_tab_mine
    }

/**
 * 底栏单格：26dp 图标槽 + 10sp 标签。
 *
 * 选中态由「背后的金属块」承担主要表达，这里只做配合：颜色换 brand、图标 1.06 倍、
 * 字重 SemiBold。整条金属形态没有块，才由 [ring] 补一圈品牌光环。
 *
 * 动效（M17）：颜色 160ms、图标缩放 220ms，都走标准缓动，不做弹跳。
 * 缩放只作用在图标本体（graphicsLayer），不参与测量，因此不会引起文字与格宽的重排。
 */
@Composable
private fun BottomTabItem(
    entry: AppTab,
    selected: Boolean,
    ring: Boolean,
    onClick: () -> Unit,
    muted: Color,
    brand: Color,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) brand else muted,
        animationSpec = tween(GlassMotion.fast, easing = GlassMotion.standard),
        label = "tab-tint",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(GlassMotion.base, easing = GlassMotion.enter),
        label = "tab-glyph-scale",
    )
    Column(
        modifier = modifier
            .height(GlassTokens.tabHeight)
            .clip(GlassShape.corner)
            .then(if (ring) Modifier.metalRing() else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(GlassTokens.tabGlyphSlot), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = entry.label,
                tint = tint,
                modifier = Modifier
                    .size(GlassTokens.tabIcon)
                    .graphicsLayer {
                        scaleX = glyphScale
                        scaleY = glyphScale
                    },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}
