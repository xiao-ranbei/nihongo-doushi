package com.nihongodoushi.app.ui.read

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.common.PlannedRow
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate

/** 工具状态：PLANNED = 规划中（不可点）；AVAILABLE = 已落地（翻状态后补路由） */
enum class ReadToolStatus { PLANNED, AVAILABLE }

/** 阅读工具清单项：新增工具 = 加一条数据 + 状态翻 AVAILABLE + 补路由 */
data class ReadTool(
    val id: String,
    val name: String,
    val description: String,
    val status: ReadToolStatus,
)

private val READ_TOOLS = listOf(
    ReadTool("ai-reading", "AI 阅读", "贴入日语文章，边读边查词句", ReadToolStatus.PLANNED),
    ReadTool("layout", "排版", "调整字体、行距与注音的显示方式", ReadToolStatus.PLANNED),
    ReadTool("writing", "撰写", "写日语短文，实时检查语法与用词", ReadToolStatus.PLANNED),
    ReadTool("analysis", "语法单词解析", "解析句子结构，标注单词读音与词性", ReadToolStatus.PLANNED),
)

private fun iconFor(tool: ReadTool): ImageVector = when (tool.id) {
    "ai-reading" -> Icons.Filled.Info
    "layout" -> Icons.AutoMirrored.Filled.List
    "writing" -> Icons.Filled.Create
    else -> Icons.Filled.Search
}

/**
 * 阅读页（2026-10-17 四标签改版）：工具框架页，全部为「规划中」位。
 * 结构：页头 → 框架说明卡 → 工具列表（一张卡内 4 个规划中行，行间 1dp 分隔）。
 */
@Composable
fun ReadScreen(app: AppState) {
    val paper = paperColors()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 段① 页头（四页页头一致律）
        Column(modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)) {
            Text(
                text = today.year.toString() + "年" + today.monthValue + "月" + today.dayOfMonth + "日",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
            )
            Text(
                text = "阅读",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 段② 框架说明卡：全页唯一「可读长文」
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "阅读工具",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "这里将提供 AI 辅助的日语文章阅读与写作工具。" +
                            "联网能力与模型配置就绪后逐个开放，模型配置入口会先出现在「我的」页。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = paper.textStrong,
                    )
                }
            }
        }

        // 段③ 工具列表：数据驱动，PLANNED 行不可点
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                READ_TOOLS.forEachIndexed { index, tool ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    PlannedRow(
                        icon = iconFor(tool),
                        title = tool.name,
                        description = tool.description,
                    )
                }
            }
        }
    }
}
