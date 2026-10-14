package com.nihongodoushi.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.storage.ModuleStats
import com.nihongodoushi.app.storage.ReviewPolicy
import com.nihongodoushi.app.ui.theme.paperColors

/** 练习共用小件：统计条、重练横幅、模式徽标、题面标题。颜色一律取主题令牌。 */

@Composable
fun StatSummaryRow(stats: ModuleStats, onReset: () -> Unit) {
    val total = stats.correct + stats.incorrect
    val correctPercent = if (total == 0) 0 else Math.round(stats.correct * 100.0 / total).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "答对 ${stats.correct}", style = MaterialTheme.typography.labelLarge, color = paperColors().statCorrectText)
        Text(text = "答错 ${stats.incorrect}", style = MaterialTheme.typography.labelLarge, color = paperColors().statIncorrectText)
        Text(text = "共 $total", style = MaterialTheme.typography.labelLarge, color = paperColors().textStrong)
        Text(
            text = if (total == 0) "正确率 —" else "正确率 $correctPercent%",
            style = MaterialTheme.typography.labelLarge,
            color = paperColors().textStrong,
        )
        TextButton(onClick = onReset, enabled = total > 0) {
            Text(text = "统计清零", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ReviewBanner(reviewing: Boolean, remainingCount: Int) {
    if (!reviewing) {
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = paperColors().orangePale,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "错题重练中：本轮待练 $remainingCount 题，完成 " +
                ReviewPolicy.MASTERY_CORRECT_STREAK + " 次正确复习后移出",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** 模式徽标：随机练习 / 间隔复习 / 错题重练 */
@Composable
fun ModeChip(modeLabel: String) {
    val (bg, fg) = when (modeLabel) {
        "错题重练" -> paperColors().orangePale to MaterialTheme.colorScheme.onTertiaryContainer
        "间隔复习" -> paperColors().greenPale to MaterialTheme.colorScheme.onPrimaryContainer
        else -> paperColors().bgPaleGreen to paperColors().textGreen
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = modeLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Composable
fun QuestionTitleBlock(question: PracticeQuestion, detailsVisible: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = question.promptWord,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (detailsVisible) question.promptReading else "假名",
            style = MaterialTheme.typography.bodyMedium,
            color = if (detailsVisible) paperColors().reading else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (detailsVisible) question.groupLabel else "词条",
            style = MaterialTheme.typography.labelMedium,
            color = if (detailsVisible) paperColors().textGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = question.meaning,
            style = MaterialTheme.typography.bodyLarge,
            color = paperColors().meaning,
        )
    }
}
