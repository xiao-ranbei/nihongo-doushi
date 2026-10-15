package com.nihongodoushi.app.ui.mine

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate

/**
 * 我的：今日学习统计（V3 形态）。
 * 结构：时长 / 任务紧凑条 → 学习进度（模块进度条）→ 今日明细 → 统计清零。
 */
@Composable
fun MineScreen(app: AppState) {
    var confirmReset by remember { mutableStateOf(false) }
    val today = app.todayActivity()
    val (durationValue, durationUnit) = ActivityAggregation.durationParts(today.seconds)
    val paper = paperColors()

    val verbPracticed = app.progress.verbIds.size
    val adjectivePracticed = app.progress.adjectiveIds.size
    val verbTotal = VerbData.VERBS.size
    val adjectiveTotal = AdjectiveData.ADJECTIVES.size
    val mastered = app.masteredCount
    val mistakeTotal = app.mistakes.size + mastered

    val verbStats = app.verbStats
    val adjStats = app.adjectiveStats
    val totalAnswers = verbStats.correct + verbStats.incorrect + adjStats.correct + adjStats.incorrect
    val accuracy = if (totalAnswers == 0) {
        "—"
    } else {
        Math.round((verbStats.correct + adjStats.correct) * 100.0 / totalAnswers).toString() + "%"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        // 时长 / 任务
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

        SectionLabel("学习进度")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

        SectionLabel("今日明细")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = paperColors().muted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
}

@Composable
private fun ProgressLine(
    label: String,
    value: String,
    ratio: Float,
    color: androidx.compose.ui.graphics.Color,
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
