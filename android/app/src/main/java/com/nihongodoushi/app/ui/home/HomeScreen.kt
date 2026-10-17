package com.nihongodoushi.app.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate
import kotlin.math.roundToInt

/** 主页可进入的二级页 */
enum class HomeDestination { VERB, ADJECTIVE, PARTICLE, MISTAKES }

/**
 * 主页：模块统一入口。
 * 结构：页头 → 一周打卡条（V3）→ 本周小结行 → 三张大入口卡（V1）。
 */
@Composable
fun HomeScreen(app: AppState, onOpen: (HomeDestination) -> Unit) {
    val paper = paperColors()
    val today = LocalDate.now()
    val week = ActivityAggregation.weekStrip(app.history, today)
    val weekTotal = week.sumOf { it.answers }

    val verbTotal = VerbData.VERBS.size
    val adjectiveTotal = AdjectiveData.ADJECTIVES.size
    val verbPracticed = app.progress.verbIds.size
    val adjectivePracticed = app.progress.adjectiveIds.size
    val mistakeCount = app.mistakes.size
    val dueCount = app.mistakes.count { app.isReviewDue(it) }

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
                text = today.year.toString() + "年" + today.monthValue + "月" + today.dayOfMonth + "日",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
            )
            Text(
                text = "今天继续",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 一周打卡条（V3）：每格显示当天答题量，今天用实心主色
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
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    week.forEach { cell ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = cell.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cell.isToday) MaterialTheme.colorScheme.primary else paper.muted,
                                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            cell.isToday -> MaterialTheme.colorScheme.primary
                                            cell.isFuture -> MaterialTheme.colorScheme.background
                                            cell.answers > 0 -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.background
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (cell.isFuture) "–" else cell.answers.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (cell.isToday || cell.answers > 0) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = when {
                                        cell.isToday -> MaterialTheme.colorScheme.onPrimary
                                        cell.answers > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> paper.muted
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "本周 $weekTotal 题 · 连续 ${app.currentStreak()} 天 · 正确率 $accuracy",
                    style = MaterialTheme.typography.labelMedium,
                    color = paper.textStrong,
                )
            }
        }

        Text(
            text = "练习入口",
            style = MaterialTheme.typography.labelMedium,
            color = paper.muted,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        EntryCard(
            icon = Icons.Filled.Create,
            title = "动词练习",
            subtitle = "$verbTotal 个词条 · 已练 $verbPracticed · 7 语态 × 18 活用",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(HomeDestination.VERB) },
        )
        EntryCard(
            icon = Icons.Filled.List,
            title = "形容词练习",
            subtitle = "$adjectiveTotal 个词条 · 已练 $adjectivePracticed · い / な 两类",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(HomeDestination.ADJECTIVE) },
        )
        val particleTotal = ParticleData.TOTAL_QUESTIONS
        val particleMastered = app.particleMasteredCount()
        val particlePercent = if (particleTotal == 0) 0 else (particleMastered * 100.0 / particleTotal).roundToInt()
        EntryCard(
            icon = Icons.Filled.Info,
            title = "助词学习",
            subtitle = "9 个专题 · 已掌握 $particlePercent% · 错题 ${app.particleMistakes.size} 道",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(HomeDestination.PARTICLE) },
        )
        val particleMistakeCount = app.particleMistakes.size
        val mistakeTotal = mistakeCount + particleMistakeCount
        EntryCard(
            icon = Icons.Filled.Refresh,
            title = "错题本",
            subtitle = if (mistakeTotal == 0) {
                "暂无错题 · 答错的题会按间隔复习排入"
            } else {
                "$mistakeTotal 道待复习" +
                    (if (dueCount > 0) " · $dueCount 道今天到期" else " · 今天无到期") +
                    (if (particleMistakeCount > 0) " · 助词 $particleMistakeCount 道" else "")
            },
            tint = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onOpen(HomeDestination.MISTAKES) },
        )
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = paper.muted,
            )
        }
    }
}
