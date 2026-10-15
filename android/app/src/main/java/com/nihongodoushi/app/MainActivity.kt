package com.nihongodoushi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.home.HomeDestination
import com.nihongodoushi.app.ui.home.HomeScreen
import com.nihongodoushi.app.ui.mine.MineScreen
import com.nihongodoushi.app.ui.mistakes.MistakesScreen
import com.nihongodoushi.app.ui.practice.AdjectivePracticeScreen
import com.nihongodoushi.app.ui.practice.AdjectivePracticeState
import com.nihongodoushi.app.ui.practice.VerbPracticeScreen
import com.nihongodoushi.app.ui.practice.VerbPracticeState
import com.nihongodoushi.app.ui.theme.NihongoDoushiTheme

/** 底栏两个一级标签（2026-10-15 重设计：动词/形容词/错题本降为二级页） */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Filled.Home),
    MINE("我的", Icons.Filled.Person),
}

/** 二级页标识（可保存进 Bundle，故用字符串常量） */
private object SubPage {
    const val VERB = "verb"
    const val ADJECTIVE = "adjective"
    const val MISTAKES = "mistakes"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (sub) {
                SubPage.VERB -> VerbPracticeScreen(verbState, onBack = { sub = null })
                SubPage.ADJECTIVE -> AdjectivePracticeScreen(adjectiveState, onBack = { sub = null })
                SubPage.MISTAKES -> MistakesScreen(
                    appState,
                    verbState,
                    adjectiveState,
                    onBack = { sub = null },
                )
                else -> when (AppTab.valueOf(tab)) {
                    AppTab.HOME -> HomeScreen(appState) { destination ->
                        sub = when (destination) {
                            HomeDestination.VERB -> SubPage.VERB
                            HomeDestination.ADJECTIVE -> SubPage.ADJECTIVE
                            HomeDestination.MISTAKES -> SubPage.MISTAKES
                        }
                    }
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
                icon = { Icon(imageVector = entry.icon, contentDescription = entry.label) },
                label = { Text(text = entry.label) },
            )
        }
    }
}
