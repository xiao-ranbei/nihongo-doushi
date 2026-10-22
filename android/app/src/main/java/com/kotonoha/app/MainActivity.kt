package com.kotonoha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kotonoha.app.storage.LocalStore
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.adjective.AdjectiveFlow
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
import com.kotonoha.app.ui.theme.ModuleTone
import com.kotonoha.app.ui.theme.ModuleTones
import com.kotonoha.app.ui.theme.KotonohaTheme
import com.kotonoha.app.ui.theme.ProvideModuleTone
import com.kotonoha.app.ui.theme.paperColors
import com.kotonoha.app.ui.verb.VerbFlow

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
        enableEdgeToEdge()
        setContent {
            KotonohaTheme {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (sub == null) {
                ProvideModuleTone(toneOf(AppTab.valueOf(tab))) {
                    AppBottomBar(
                        current = AppTab.valueOf(tab),
                        onSelect = { tab = it.name },
                    )
                }
            }
        },
    ) { innerPadding ->
        // 带底部抽屉/操作条的二级页自己处理底部内边距：白条要一直铺到导航栏下方，
        // 不能留出 Scaffold 的底色缝；错题本没有底部条，仍用 Scaffold 的内边距
        val layoutDirection = LocalLayoutDirection.current
        val bottomInset = if (sub == SubPage.MISTAKES || sub == null) {
            innerPadding.calculateBottomPadding()
        } else {
            0.dp
        }
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
                // 通过 moduleTone() 取到本标签的颜色，不必层层传参
                else -> ProvideModuleTone(toneOf(AppTab.valueOf(tab))) {
                    when (AppTab.valueOf(tab)) {
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

/**
 * 底栏（视觉重设计）：纸白底 + 1dp 细边，四个等宽格。
 * 选中格是模块浅底色胶囊（图标与文字同为主色/深字色），未选中格只有 muted 图标与文字。
 *
 * 为什么不用 M3 的 NavigationBar + NavigationBarItem：M3 的选中态是靠指示条 + 填充色阶，
 * 与「纸白 + 模块色块」的配色规则对不上（它的 label 颜色由 NavigationBar 内部 token 强制），
 * 这里换成自绘一行，选中态就是一枚实心色块，规则与页面里的图标底盒同源。
 *
 * 读屏口径与原先一致：整格一个焦点站（selectable 自带合并子语义），role=Tab 供读屏报「标签页」。
 */
@Composable
private fun AppBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    val paper = paperColors()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppTab.entries.forEach { entry ->
                BottomTabItem(
                    entry = entry,
                    selected = current == entry,
                    onClick = { onSelect(entry) },
                    muted = paper.muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 底栏单格：选中 = 模块浅底胶囊 + 主色图标/文字；未选中 = muted 图标/文字，无底色。 */
@Composable
private fun BottomTabItem(
    entry: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    muted: Color,
    modifier: Modifier = Modifier,
) {
    val tone = toneOf(entry)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) tone.pale else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (entry) {
            AppTab.HOME -> Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = entry.label,
                tint = if (selected) tone.main else muted,
                modifier = Modifier.size(22.dp),
            )
            AppTab.STUDY -> Icon(
                painter = painterResource(R.drawable.ic_tab_study),
                contentDescription = entry.label,
                tint = if (selected) tone.main else muted,
                modifier = Modifier.size(22.dp),
            )
            AppTab.READ -> Icon(
                painter = painterResource(R.drawable.ic_tab_read),
                contentDescription = entry.label,
                tint = if (selected) tone.main else muted,
                modifier = Modifier.size(22.dp),
            )
            AppTab.MINE -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = entry.label,
                tint = if (selected) tone.main else muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) tone.deep else muted,
        )
    }
}
