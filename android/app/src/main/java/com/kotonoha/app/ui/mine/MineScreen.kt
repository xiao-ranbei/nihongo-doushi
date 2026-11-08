package com.kotonoha.app.ui.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kotonoha.app.storage.Bookmark
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.removeBookmark
import com.kotonoha.app.ui.resetAdjectiveStats
import com.kotonoha.app.ui.resetVerbStats
import com.kotonoha.app.ui.todayActivity
import com.kotonoha.app.ui.common.ModuleGlyphBox
import com.kotonoha.app.ui.common.ModuleHeader
import com.kotonoha.app.ui.common.ModuleSectionLabel
import com.kotonoha.app.ui.common.PlannedRow
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.moduleTone
import com.kotonoha.app.ui.theme.paperColors
import java.time.Instant
import java.time.LocalDate

/**
 * 我的页（2026-10-17 四标签改版）。
 * 结构：页头 → 资料卡（昵称可编辑）→ 学习统计（时长/进度/明细）→ 书签收藏夹（空态 / 由新到旧列表）
 * → 更多设置（1 个可点位 + 2 个规划中位）→ 统计清零。
 *
 * 模型/API 设置已转正为可点入口（spec §9.5）：阅读器要能跑必须先有配置，
 * 所以这个入口不能跟「发音音色」「词库导入导出」一起躺在规划中位里。
 */
@Composable
fun MineScreen(app: AppState, onOpenModelSettings: () -> Unit) {
    val g = geometry()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 底部 112dp 让位给悬浮岛（覆盖浮法下内容会从岛后方穿过，滚到底时最后一项不被遮）
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = GlassTokens.islandContentInset,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 页头：大标题「我的」+ 副信息带应用名（改名后 App 内的署名位，模块色取灰蓝）
        ModuleHeader(
            title = "我的",
            subtitle = "言の葉 · " + LocalDate.now().monthValue + "月" +
                LocalDate.now().dayOfMonth + "日",
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
        )

        // 段① 资料卡：首字圆标 + 昵称 + 编辑
        MineProfileCard(app)

        // 段② 学习统计（原两标签版内容，原样保留）
        StudyDurationCard(app.todayActivity())

        SectionLabel("学习进度")
        StudyProgressCard(app)

        SectionLabel("今日明细")
        StudyDetailCard(app)

        // 段三 书签收藏夹：计数（N > 99 显示「99+ 条」）+ 按数据显示（空态 / 列表）
        BookmarkSectionLabel(app)
        if (app.bookmarks.items.isEmpty()) {
            BookmarkEmptyCard()
        } else {
            BookmarkListSection(
                items = app.bookmarks.items,
                onRemove = { id -> app.removeBookmark(id) },
            )
        }

        // 段④ 更多设置：首项已落地（可点），后两项仍是规划中位（不可点击）
        SectionLabel("更多设置")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(g.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = g.cardShadow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsEntryRow(onClick = onOpenModelSettings)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                PlannedRow(
                    icon = Icons.Filled.Star,
                    title = "发音音色",
                    description = "选择单词与句子的朗读音色",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                PlannedRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "词库导入导出",
                    description = "导入或导出本地词库数据",
                )
            }
        }

        // 段⑤ 统计清零（沿用现有实现，不动）
        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier.padding(start = 2.dp),
        ) {
            Text("统计清零", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("统计清零？") },
            text = {
                Text(
                    "将清零动词与形容词的答题统计（答对/答错/正确率）。" +
                        "错题本、学习进度与打卡记录不受影响。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    app.resetVerbStats()
                    app.resetAdjectiveStats()
                    confirmReset = false
                }) { Text("清零") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            },
        )
    }
}

/** 书签收藏夹小节：左标题 + 右计数（容量防御：>99 折叠为 99+） */
@Composable
private fun BookmarkSectionLabel(app: AppState) {
    val count = app.bookmarks.items.size
    ModuleSectionLabel(
        title = "书签收藏夹",
        caption = if (count > 99) "99+ 条" else "$count 条",
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
}

/** 书签空态卡：星形图标 + 引导文案（收藏入口 2026-10 已移到点词展开的详情里） */
@Composable
private fun BookmarkEmptyCard() {
    val g = geometry()
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModuleGlyphBox(
                icon = Icons.Filled.Star,
                size = 48.dp,
                corner = 999.dp,
                iconSize = 24.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "还没有收藏。在 AI 阅读里点某个词，展开的详情里可以收藏这个词或这一段",
                style = MaterialTheme.typography.labelMedium,
                color = paperColors().muted,
            )
        }
    }
}

/**
 * 书签列表：按 createdAt 由新到旧（最新在上）逐条显示。
 *
 * 排序放在显示侧而不是写入侧——[com.kotonoha.app.storage.BookmarkRules.add] 已有约定是
 * 「同 id 覆盖且保持原位置」，在那边改顺序会连带影响阅读器「重复收藏只留一条」的行为。
 */
@Composable
private fun BookmarkListSection(items: List<Bookmark>, onRemove: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sortedBookmarks(items).forEach { bookmark ->
            BookmarkRow(bookmark = bookmark, onRemove = { onRemove(bookmark.id) })
        }
    }
}

/** 单词收藏的可见标记文案；非 word 的合法值只有 sentence，一律按段落显示 */
internal fun bookmarkTypeLabel(bookmark: Bookmark): String =
    if (bookmarkIsWord(bookmark)) "单词" else "段落"

/** 是否单词收藏：type 是封闭集合（BookmarkRules.ALLOWED_TYPES） */
internal fun bookmarkIsWord(bookmark: Bookmark): Boolean = bookmark.type == "word"

/** 由新到旧排；同时间（含解析失败）保持原有先后，不动存储侧顺序 */
internal fun sortedBookmarks(items: List<Bookmark>): List<Bookmark> =
    items.sortedByDescending { bookmarkEpoch(it.createdAt) }

/** createdAt 解析容错：异常时间放到 EPOCH 排到底部，不让一条记录影响整页 */
private fun bookmarkEpoch(raw: String): Instant =
    runCatching { Instant.parse(raw) }.getOrDefault(Instant.EPOCH)

/** 单条书签：类型标记 + 标题 + 可选备注 + 删除入口 */
@Composable
private fun BookmarkRow(bookmark: Bookmark, onRemove: () -> Unit) {
    val g = geometry()
    val paper = paperColors()
    val tone = moduleTone()
    val isWord = bookmarkIsWord(bookmark)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(g.cardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = g.cardShadow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleGlyphBox(
                icon = if (isWord) Icons.Filled.Search else Icons.AutoMirrored.Filled.List,
                size = 36.dp,
                corner = 10.dp,
                iconSize = 20.dp,
                tone = tone,
            )
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(g.smallRadius),
                    color = tone.pale,
                    border = BorderStroke(1.dp, tone.line),
                ) {
                    Text(
                        text = bookmarkTypeLabel(bookmark),
                        style = MaterialTheme.typography.labelSmall,
                        color = tone.deep,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (bookmark.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = bookmark.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除收藏",
                    tint = paper.muted,
                )
            }
        }
    }
}

/**
 * 「模型/API 设置」可点行：排版与 [com.kotonoha.app.ui.read.ReadScreen] 里的可点工具行同源
 * （40dp 图标块 + 标题副标题 + 右侧箭头 + 水波），只是图标与文字不同。
 *
 * 之所以不写成 PlannedRow 加 clickable：PlannedRow 自带「规划中」徽章，加了点击就成了
 * 「说没做好又让你点」的自相矛盾，且没有箭头，用户看不出这里能点（D-026）。
 */
@Composable
private fun SettingsEntryRow(onClick: () -> Unit) {
    val paper = paperColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ModuleGlyphBox(icon = Icons.Filled.Settings)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "模型/API 设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "配置 AI 阅读使用的模型与服务地址",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开模型设置",
                tint = paper.muted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    ModuleSectionLabel(
        title = text,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        tone = moduleTone(),
    )
}
