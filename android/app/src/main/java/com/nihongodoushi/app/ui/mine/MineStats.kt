package com.nihongodoushi.app.ui.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.storage.DayActivity
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 我的页「学习统计」三张卡（设计定稿 §6.1 段②，内容与两标签版一致，仅原样迁出）：
 * 时长/任务紧凑条 → 学习进度（模块进度条）→ 今日明细。
 */

/** 时长 / 任务紧凑条：今日学习时长（大数字锚点）+ 完成任务数 */
@Composable
fun StudyDurationCard(today: DayActivity) {
    val paper = paperColors()
    val (durationValue, durationUnit) = ActivityAggregation.durationParts(today.seconds)
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = durationValue,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = durationUnit,
                        style = MaterialTheme.typography.labelMedium,
                        color = paper.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    text = "今日学习时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(paper.line),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = today.tasks.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "完成任务",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
        }
    }
}

/** 学习进度卡：动词 / 形容词 / 错题本三条进度线 */
@Composable
fun StudyProgressCard(app: AppState) {
    val verbPracticed = app.progress.verbIds.size
    val adjectivePracticed = app.progress.adjectiveIds.size
    val verbTotal = VerbData.VERBS.size
    val adjectiveTotal = AdjectiveData.ADJECTIVES.size
    val mastered = app.masteredCount
    val mistakeTotal = app.mistakes.size + mastered

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
                .padding(horizontal = 15.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgressLine(
                label = "动词 · 已练词条",
                value = "$verbPracticed / $verbTotal",
                ratio = ActivityAggregation.coverage(verbPracticed, verbTotal),
                color = MaterialTheme.colorScheme.primary,
            )
            ProgressLine(
                label = "形容词 · 已练词条",
                value = "$adjectivePracticed / $adjectiveTotal",
                ratio = ActivityAggregation.coverage(adjectivePracticed, adjectiveTotal),
                color = MaterialTheme.colorScheme.primary,
            )
            ProgressLine(
                label = "错题本 · 已掌握",
                value = "$mastered / $mistakeTotal",
                ratio = ActivityAggregation.coverage(mastered, mistakeTotal),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** 今日明细卡：答题数 / 正确率 / 连续学习 */
@Composable
fun StudyDetailCard(app: AppState) {
    val today = app.todayActivity()
    val verbStats = app.verbStats
    val adjStats = app.adjectiveStats
    val totalAnswers = verbStats.correct + verbStats.incorrect + adjStats.correct + adjStats.incorrect
    val accuracy = if (totalAnswers == 0) {
        "—"
    } else {
        Math.round((verbStats.correct + adjStats.correct) * 100.0 / totalAnswers).toString() + "%"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DetailRow(
                title = "答题数",
                subtitle = "动词 ${today.verbAnswers} · 形容词 ${today.adjectiveAnswers}",
                value = today.answers.toString() + " 题",
            )
            DetailRow(
                title = "正确率",
                subtitle = "累计口径，含全部练习",
                value = accuracy,
            )
            DetailRow(
                title = "连续学习",
                subtitle = "最长 " + app.daily.best + " 天",
                value = app.currentStreak().toString() + " 天",
                divider = false,
            )
        }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    value: String,
    ratio: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun DetailRow(title: String, subtitle: String, value: String, divider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = paperColors().muted,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = paperColors().textStrong,
            )
        }
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
