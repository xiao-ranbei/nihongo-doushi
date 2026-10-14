package com.nihongodoushi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nihongodoushi.app.ui.theme.NihongoDoushiTheme

/** 首版四个目的地（android-requirements §2）；图标统一 Material Icons 填充风格 */
enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Filled.Home),
    VERB("动词", Icons.Filled.Edit),
    ADJECTIVE("形容词", Icons.Filled.List),
    MISTAKES("错题本", Icons.Filled.Refresh),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NihongoDoushiTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(currentScreen, onScreenSelected = { currentScreen = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            // 占位：各屏实现在引擎接入后补齐（学习主页/动词练习/形容词练习/错题复习）
            Text(text = currentScreen.label)
        }
    }
}

@Composable
private fun AppBottomBar(current: AppScreen, onScreenSelected: (AppScreen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        AppScreen.entries.forEach { screen ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onScreenSelected(screen) },
                icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                label = { Text(text = screen.label) },
            )
        }
    }
}
