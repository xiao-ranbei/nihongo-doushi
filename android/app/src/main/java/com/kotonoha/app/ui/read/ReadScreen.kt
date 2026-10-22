package com.kotonoha.app.ui.read

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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.common.ModuleGlyphBox
import com.kotonoha.app.ui.common.ModuleHeader
import com.kotonoha.app.ui.common.PlannedRow
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.moduleTone
import com.kotonoha.app.ui.theme.paperColors
import java.time.LocalDate

/** 工具状态：PLANNED = 规划中（不可点）；AVAILABLE = 已落地（可点，由调用方补路由） */
enum class ReadToolStatus { PLANNED, AVAILABLE }

/** 阅读工具清单项：新增工具 = 加一条数据 + 状态翻 AVAILABLE + 补路由 */
data class ReadTool(
    val id: String,
    val name: String,
    val description: String,
    val status: ReadToolStatus,
)

/**
 * 工具清单：3 项（原「语法单词解析」已并入 AI 阅读后删除）。
 *
 * 删除理由：句子解析即 AI 阅读的核心能力，单列会形成两个入口指向同一能力——
 * 用户在两处点进去看到的是同一个阅读器，只是入口名字不同，属于自我重复。
 */
private val READ_TOOLS = listOf(
    ReadTool("ai-reading", "AI 阅读", "贴入日语文章，逐句解析读音、词性与译文", ReadToolStatus.AVAILABLE),
    ReadTool("layout", "排版", "调整字体、行距与注音的显示方式", ReadToolStatus.PLANNED),
    ReadTool("writing", "撰写", "写日语短文，实时检查语法与用词", ReadToolStatus.PLANNED),
)

private fun iconFor(tool: ReadTool): ImageVector = when (tool.id) {
    "ai-reading" -> Icons.Filled.Info
    "layout" -> Icons.AutoMirrored.Filled.List
    "writing" -> Icons.Filled.Create
    else -> Icons.Filled.Search
}

/**
 * 阅读页：工具框架页，一项已落地（AI 阅读）+ 两项规划中。
 *
 * 可点行与 [PlannedRow] 的差别不只是「能不能点」：按 D-026，规划中位不能带可点 affordance，
 * 反过来可点行必须给足 affordance（右侧箭头 + 水波 + 48dp 级触控区），
 * 否则用户看不出哪些能点。因此这里不写成 PlannedRow 加 clickable。
 */
@Composable
fun ReadScreen(app: AppState, onOpen: (ReadTool) -> Unit) {
    val g = geometry()
    val paper = paperColors()
    val tone = moduleTone()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 段① 页头：大标题 + 副信息，模块色取赭橙（四页页头一致律）
        ModuleHeader(
            title = "阅读",
            subtitle = today.year.toString() + "年" + today.monthValue + "月" +
                today.dayOfMonth + "日 · ${READ_TOOLS.count { it.status == ReadToolStatus.AVAILABLE }} 项工具已开放",
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
        )

        // 段② 框架说明卡：全页唯一「可读长文」
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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ModuleGlyphBox(
                    icon = Icons.Filled.Info,
                    size = 36.dp,
                    corner = 10.dp,
                    iconSize = 20.dp,
                    tone = tone,
                )
                Column {
                    Text(
                        text = "阅读工具",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI 阅读已开放：贴入日语文章即可逐句查看读音、词性与译文。" +
                            "排版与撰写仍在规划中，模型与服务地址在「我的」页或阅读器里都能配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = paper.textStrong,
                    )
                }
            }
        }

        // 段③ 工具列表：数据驱动，AVAILABLE 行可点，PLANNED 行不可点
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(g.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = g.cardShadow,
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
                    if (tool.status == ReadToolStatus.AVAILABLE) {
                        AvailableToolRow(tool = tool, onClick = { onOpen(tool) })
                    } else {
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
}

/**
 * 可点工具行：排版照 [com.kotonoha.app.ui.common.EntryCard]（40dp 图标块 + 标题副标题 + 右侧箭头），
 * 但不自带 Surface——它住在段③那张卡里，自带边框会变成卡中卡。
 * 水波由 clickable 提供，Surface 已按 cardRadius 圆角裁掉溢出部分。
 */
@Composable
private fun AvailableToolRow(tool: ReadTool, onClick: () -> Unit) {
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
            ModuleGlyphBox(icon = iconFor(tool), tone = moduleTone())
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开 AI 阅读",
                tint = paper.muted,
            )
        }
    }
}
