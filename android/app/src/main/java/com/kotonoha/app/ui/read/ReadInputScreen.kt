package com.kotonoha.app.ui.read

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kotonoha.app.engine.read.ReadArticle
import com.kotonoha.app.engine.read.ReadRules
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.common.ModuleTopBar
import com.kotonoha.app.ui.deleteArticle
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.paperColors
import java.time.Instant

/** 最近文章行的字号写死：混用 typography 会让长列表的行高随正文字号档位跳动 */
private val HISTORY_TITLE_SIZE = 15.sp
private val HISTORY_META_SIZE = 12.sp

/**
 * AI 阅读输入页（spec §9.3）。
 *
 * 结构：页头 → 隐私提示 → 正文输入卡 → 主按钮 → 最近文章。
 * 主按钮在一个位置承载两种意图：配置没填好时它就是「去配置模型」，
 * 而不是先让人贴完一千字再告诉他缺 API Key——那是最伤人的失败顺序。
 *
 * 「N 字」用非空白字符数（[ReadUiLogic.charCount]）：日语原文里的空行与缩进不少，
 * 按 String.length 算会把「看着不到 10 字」的文章判成合格。
 *
 * 比任务书签名多出的 onBack：页头必须有个返回按钮（任务书 §C 要求 ModuleTopBar 带返回），
 * 而容器里 INPUT 路由不让 BackHandler 接管（§B），所以返回只能由这里的按钮回调出去。
 */
@Composable
fun ReadInputScreen(
    app: AppState,
    text: String,
    onTextChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (text: String) -> Unit,
    onOpenArticle: (ReadArticle) -> Unit,
    onBack: () -> Unit,
) {
    val g = geometry()
    val paper = paperColors()
    val configReady = ReadRules.validateConfig(app.readConfig).isEmpty()
    val canStart = ReadUiLogic.canStart(text)
    // ISO-8601 同一格式的字符串字典序等于时间序，所以排序不必解析 Instant，也不会因为解析失败抛异常
    val articles = remember(app.readArticles.items) {
        app.readArticles.items.sortedByDescending { it.updatedAt }
    }
    var pendingDelete by remember { mutableStateOf<ReadArticle?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(title = "AI 阅读", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "文章内容会发送到你填写的服务地址，仅用于逐句解析。",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
                modifier = Modifier.padding(start = 2.dp),
            )

            // 正文输入卡：minLines = 8 保证首屏就有「这是一篇文章」的体积感
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(g.cardRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = g.cardShadow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴日语文章…") },
                        minLines = 8,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                        ),
                        shape = RoundedCornerShape(g.smallRadius),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ReadUiLogic.charCount(text).toString() + " 字",
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }

            // 配置缺失优先于正文长度：先解决「跑不起来」，再解决「跑什么」。
            // 未配置时按钮不禁用而是直接改成「去配置模型」——禁用的按钮不告诉你去哪儿修。
            if (configReady) {
                Button(
                    onClick = { onStart(text) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canStart,
                ) {
                    Text("开始阅读")
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("去配置模型")
                }
            }

            if (articles.isNotEmpty()) {
                Text(
                    text = "最近文章",
                    style = MaterialTheme.typography.labelMedium,
                    color = paper.muted,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(g.cardRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = g.cardShadow,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        articles.forEachIndexed { index, article ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            ArticleRow(
                                article = article,
                                onClick = { onOpenArticle(article) },
                                onLongClick = { pendingDelete = article },
                            )
                        }
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这篇文章？") },
            text = { Text("「" + target.title + "」将从最近文章里移除，已解析的句子缓存不受影响。") },
            confirmButton = {
                Button(onClick = {
                    app.deleteArticle(target.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 最近文章行：点击打开，长按删除。
 *
 * 用 combinedClickable 而不是在行尾常驻删除按钮：删除是低频破坏性操作，
 * 常驻按钮给每行多一个误触面；长按 + 二次确认才是这类操作的常规做法。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleRow(
    article: ReadArticle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val paper = paperColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = article.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            fontSize = HISTORY_TITLE_SIZE,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ReadUiLogic.relativeTime(article.updatedAt, Instant.now()),
                style = MaterialTheme.typography.labelSmall,
                fontSize = HISTORY_META_SIZE,
                color = paper.muted,
            )
            Text(
                text = ReadUiLogic.charCount(article.text).toString() + " 字",
                style = MaterialTheme.typography.labelSmall,
                fontSize = HISTORY_META_SIZE,
                color = paper.muted,
            )
        }
    }
}
