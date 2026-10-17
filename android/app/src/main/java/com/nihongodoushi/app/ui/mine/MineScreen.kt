package com.nihongodoushi.app.ui.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.common.ModuleSectionLabel
import com.nihongodoushi.app.ui.common.PlannedRow
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate

/**
 * 我的页（2026-10-17 四标签改版）。
 * 结构：页头 → 资料卡（昵称可编辑）→ 学习统计（时长/进度/明细）→ 书签收藏夹（空态框架）
 * → 更多设置（3 个规划中位）→ 统计清零。
 */
@Composable
fun MineScreen(app: AppState) {
    var confirmReset by remember { mutableStateOf(false) }
    val paper = paperColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 页头（沿用）
        Column(modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)) {
            Text(
                text = "今天 · " + LocalDate.now().monthValue + "月" + LocalDate.now().dayOfMonth + "日",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
            )
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 段① 资料卡：首字圆标 + 昵称 + 编辑
        MineProfileCard(app)

        // 段② 学习统计（原两标签版内容，原样保留）
        StudyDurationCard(app.todayActivity())

        SectionLabel("学习进度")
        StudyProgressCard(app)

        SectionLabel("今日明细")
        StudyDetailCard(app)

        // 段③ 书签收藏夹：本期空态框架 + 计数（N > 99 显示「99+ 条」）
        BookmarkSectionLabel(app)
        BookmarkEmptyCard()

        // 段④ 更多设置：3 个规划中位（不可点击）
        SectionLabel("更多设置")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PlannedRow(
                    icon = Icons.Filled.Settings,
                    title = "模型/API 设置",
                    description = "配置阅读工具使用的模型与服务地址",
                )
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
                    icon = Icons.Filled.List,
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

/** 书签空态卡：星形图标 + 引导文案（阅读功能上线后可收藏句子与单词） */
@Composable
private fun BookmarkEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = paperColors().textStrong,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "还没有收藏。阅读功能上线后，可收藏句子与单词",
                style = MaterialTheme.typography.labelMedium,
                color = paperColors().muted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = paperColors().muted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
}
