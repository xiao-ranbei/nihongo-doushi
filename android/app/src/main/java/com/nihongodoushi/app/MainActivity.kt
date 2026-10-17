package com.nihongodoushi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.adjective.AdjectiveFlow
import com.nihongodoushi.app.ui.home.HomeScreen
import com.nihongodoushi.app.ui.mine.MineScreen
import com.nihongodoushi.app.ui.mistakes.MistakesScreen
import com.nihongodoushi.app.ui.particle.ParticleFlow
import com.nihongodoushi.app.ui.practice.AdjectivePracticeScreen
import com.nihongodoushi.app.ui.practice.AdjectivePracticeState
import com.nihongodoushi.app.ui.practice.VerbPracticeScreen
import com.nihongodoushi.app.ui.practice.VerbPracticeState
import com.nihongodoushi.app.ui.read.ReadScreen
import com.nihongodoushi.app.ui.study.StudyDestination
import com.nihongodoushi.app.ui.study.StudyScreen
import com.nihongodoushi.app.ui.theme.NihongoDoushiTheme
import com.nihongodoushi.app.ui.verb.VerbFlow

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

/** 二级页标识（可保存进 Bundle，故用字符串常量） */
private object SubPage {
    const val VERB = "verb"
    const val ADJECTIVE = "adjective"
    const val PARTICLE = "particle"
    const val MISTAKES = "mistakes"
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
            NihongoDoushiTheme {
                // 状态提升到根级：切换标签 / 进出二级页都不丢练习进度
                val store = remember { LocalStore(applicationContext) }
                val appState = remember { AppState(store) }
                val verbState = remember { VerbPracticeState(appState) }
                val adjectiveState = remember { AdjectivePracticeState(appState) }
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
                AppBottomBar(
                    current = AppTab.valueOf(tab),
                    onSelect = { tab = it.name },
                )
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
                else -> when (AppTab.valueOf(tab)) {
                    AppTab.HOME -> HomeScreen(
                        app = appState,
                        onSelectTab = { tab = it.name },
                        onOpen = { dest -> sub = dest.subPage() },
                    )
                    AppTab.STUDY -> StudyScreen(
                        app = appState,
                        onOpen = { dest -> sub = dest.subPage() },
                    )
                    AppTab.READ -> ReadScreen(appState)
                    AppTab.MINE -> MineScreen(appState)
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        AppTab.entries.forEach { entry ->
            NavigationBarItem(
                selected = current == entry,
                onClick = { onSelect(entry) },
                icon = {
                    when (entry) {
                        AppTab.HOME -> Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = entry.label,
                        )
                        AppTab.STUDY -> Icon(
                            painter = painterResource(R.drawable.ic_tab_study),
                            contentDescription = entry.label,
                        )
                        AppTab.READ -> Icon(
                            painter = painterResource(R.drawable.ic_tab_read),
                            contentDescription = entry.label,
                        )
                        AppTab.MINE -> Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = entry.label,
                        )
                    }
                },
                label = { Text(text = entry.label) },
            )
        }
    }
}
