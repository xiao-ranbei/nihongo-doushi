package com.nihongodoushi.app.ui.verb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.ui.common.ModuleProgressBar
import com.nihongodoushi.app.ui.theme.paperColors
import kotlin.math.roundToInt

/**
 * 动词主题卡网格（三层改造 §3）：从 VerbHomeScreen 抽出的复用件，
 * 动词速查页不再使用，但保持首页文件在 300 行纪律内。
 *
 * 掌握度口径：该主题下「未提示答对」的词条数 / 词库总数（数据驱动，词库扩容自动适配）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeCardGrid(
    themes: List<VerbTheme>,
    masteredOf: (VerbTheme) -> Int,
    onClick: (VerbTheme) -> Unit,
    onDrill: (VerbTheme) -> Unit,
) {
    val paper = paperColors()
    themes.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            row.forEach { theme ->
                val mastered = masteredOf(theme)
                val total = VerbData.VERBS.size
                val fraction = if (total == 0) 0f else mastered.toFloat() / total
                val label = verbThemeLabel(theme)
                val subtitle = when (theme) {
                    is VerbTheme.Form -> "活用形式"
                    is VerbTheme.Voice -> "语态 · 辞书形"
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        // 整卡一个焦点站由 combinedClickable 自带合并保证
                        // （AbstractClickableNode.shouldMergeDescendantSemantics=true）；
                        // 长按动作带标签，读屏用户可从自定义操作进入「直接练习」
                        .combinedClickable(
                            onClick = { onClick(theme) },
                            onLongClick = { onDrill(theme) },
                            onLongClickLabel = "直接练习",
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 11.dp, end = 8.dp, top = 9.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(
                                onClick = { onDrill(theme) },
                                modifier = Modifier
                                    .size(width = 40.dp, height = 28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = "练",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.textStrong,
                        )
                        Text(
                            text = "${(fraction * 100).roundToInt()}%",
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "$mastered/$total 词",
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.muted,
                        )
                        ModuleProgressBar(
                            fraction = fraction,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .height(4.dp),
                        )
                    }
                }
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
