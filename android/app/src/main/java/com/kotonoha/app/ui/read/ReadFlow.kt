package com.kotonoha.app.ui.read

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kotonoha.app.engine.read.ReadArticle
import com.kotonoha.app.net.HttpAnalyzer
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.saveArticle
import java.time.Instant
import java.util.UUID

/**
 * AI 阅读模块内的导航（照 [com.kotonoha.app.ui.particle.ParticleFlow] 的形态）：
 * 输入页 → 阅读器页，外加一个仅供阅读器使用的设置页。
 */
private object ReadRoute {
    const val INPUT = "read-input"
    const val READER = "read-reader"
    const val SETTINGS = "read-settings"
}

/**
 * 阅读模块二级页容器。
 *
 * 设置页为什么在这里还有一条自己的路由：「我的」页 → MainActivity 的 MODEL_SETTINGS 是独立二级页，
 * 从阅读器跳过去要先退出本容器，粘贴的文章与已解析结果全没了；走本容器内的 SETTINGS 路由，
 * 文章文本与每句状态都还留在 rememberSaveable / mutableStateMap 里，配置完回来接着读。
 *
 * BackHandler 只在 READER 路由启用：MainActivity 已经有一个「sub != null 就接管」的 BackHandler，
 * 这里若无条件接管，输入页按返回会先被本容器吃掉一次，用户得按两下才退得出去。
 */
@Composable
fun ReadFlow(app: AppState, onExit: () -> Unit) {
    var route by rememberSaveable { mutableStateOf(ReadRoute.INPUT) }
    // 正文与标题都要 rememberSaveable：进程被回收后回来，粘贴的长文还在
    var articleText by rememberSaveable { mutableStateOf("") }
    var articleTitle by rememberSaveable { mutableStateOf("") }
    // 注音开关放在流容器而不是阅读器里：进设置页再回来时阅读器会整体离开组合，
    // 留在阅读器内的 rememberSaveable 会被丢掉，用户刚关掉的注音又自己打开了。
    var showFurigana by rememberSaveable { mutableStateOf(true) }
    // 译文开关同样放在流容器而不是阅读器里（理由同上一条），默认关：
    // 用户要的是「只看拆解句」，译文是按需才打开的附加信息。
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    // analyzer 持有 ChatCompletionClient（内含 OkHttp），每次重组都新建既浪费又会丢掉连接池
    val analyzer = remember { HttpAnalyzer() }

    BackHandler(enabled = route == ReadRoute.READER) {
        route = ReadRoute.INPUT
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            ReadRoute.INPUT -> ReadInputScreen(
                app = app,
                text = articleText,
                onTextChange = { articleText = it },
                onOpenSettings = { route = ReadRoute.SETTINGS },
                onStart = { text ->
                    val title = ReadUiLogic.articleTitle(text)
                    val now = Instant.now().toString()
                    articleText = text
                    articleTitle = title
                    // 先落盘再进阅读器：进页后再存，用户中途退出就丢一篇；updatedAt 由 saveArticle 统一刷新
                    app.saveArticle(
                        ReadArticle(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            text = text,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    route = ReadRoute.READER
                },
                onOpenArticle = { article ->
                    articleText = article.text
                    articleTitle = article.title
                    route = ReadRoute.READER
                },
                onBack = onExit,
            )

            ReadRoute.READER -> ReadReaderScreen(
                app = app,
                analyzer = analyzer,
                title = articleTitle,
                text = articleText,
                onBack = { route = ReadRoute.INPUT },
                onOpenSettings = { route = ReadRoute.SETTINGS },
                showFurigana = showFurigana,
                onToggleFurigana = { showFurigana = it },
                showTranslation = showTranslation,
                onToggleTranslation = { showTranslation = it },
            )

            ReadRoute.SETTINGS -> ModelSettingsScreen(
                app = app,
                onBack = { route = ReadRoute.READER },
            )
        }
    }
}
