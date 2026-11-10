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
import com.kotonoha.app.ui.glass.GlassLayer
import com.kotonoha.app.ui.glass.Layer
import com.kotonoha.app.ui.glass.GlassShape
import com.kotonoha.app.ui.glass.dock.DockTab
import com.kotonoha.app.ui.glass.dock.LiquidDock
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
            GlassLayer(
                modifier = Modifier.fillMaxSize(),
                // 悬浮岛交给 GlassLayer 的 overlay 槽：它要采内容层，就必须在 Provider 的子树里
                overlay = {
                    if (sub == null) {
                        LiquidDock(
                            tabs = AppTab.entries.map { DockTab(it.iconRes, it.label) },
                            // 传 lambda 而不是值：官方 API 就是 `selectedTabIndex: () -> Int`，
                            // 内部的 snapshotFlow 靠它感知外部切换
                            selectedIndex = { AppTab.entries.indexOf(AppTab.valueOf(tab)) },
                            onTabSelected = { index -> tab = AppTab.entries[index].name },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = GlassTokens.islandInset, vertical = 14.dp),
                        )
                    }
                },
            ) {
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
        }
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
