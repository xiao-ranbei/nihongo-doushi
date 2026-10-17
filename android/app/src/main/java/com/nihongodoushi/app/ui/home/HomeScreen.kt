package com.nihongodoushi.app.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.AppTab
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.proverb.ProverbData
import com.nihongodoushi.app.engine.proverb.ProverbPicker
import com.nihongodoushi.app.engine.suggest.StudySnapshot
import com.nihongodoushi.app.engine.suggest.SuggestionEngine
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.study.StudyDestination
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 主页（2026-10-17 四标签改版）：每日第一屏。
 * 结构：页头 → 谚语卡（每日一句 + 换一句）→ 今日概览（周历条 + 本周小结）→ 知识点建议。
 * 模块入口已迁至「学习」页（ui/study/StudyScreen.kt）。
 */
@Composable
fun HomeScreen(
    app: AppState,
    onSelectTab: (AppTab) -> Unit,
    onOpen: (StudyDestination) -> Unit,
) {
    val paper = paperColors()
    val today = LocalDate.now()
    val week = ActivityAggregation.weekStrip(app.history, today)
    val weekTotal = week.sumOf { it.answers }

    val verbStats = app.verbStats
    val adjStats = app.adjectiveStats
    val totalAnswers = verbStats.correct + verbStats.incorrect + adjStats.correct + adjStats.incorrect
    val accuracy = if (totalAnswers == 0) {
        "—"
    } else {
        Math.round((verbStats.correct + adjStats.correct) * 100.0 / totalAnswers).toString() + "%"
    }

    var proverbIndex by rememberSaveable {
        mutableStateOf(ProverbPicker.initial(Random.Default, ProverbData.PROVERBS.size))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 段① 页头（沿用）
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

        // 段② 谚语卡：进入随机一条，「换一句」不与当前重复
        ProverbCard(
            proverb = ProverbData.PROVERBS[proverbIndex],
            onRefresh = {
                proverbIndex = ProverbPicker.next(proverbIndex, ProverbData.PROVERBS.size, Random.Default)
            },
        )

        // 段③ 今日概览（沿用现有周历条 + 本周小结行，样式不改）
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

        // 段④ 知识点建议：引擎返回空列表时隐藏整节
        val suggestions = SuggestionEngine.suggest(buildStudySnapshot(app))
        if (suggestions.isNotEmpty()) {
            SuggestionSection(
                suggestions = suggestions,
                onOpen = onOpen,
                onSelectTab = onSelectTab,
            )
        }
    }
}

/** 建议快照口径（规格 §5.1）：到期错题 = 两模块到期 + 助词到期；掌握度用现有算法 */
private fun buildStudySnapshot(app: AppState): StudySnapshot {
    val verbStats = app.verbStats
    val adjStats = app.adjectiveStats
    val particlePercent = if (ParticleData.TOTAL_QUESTIONS == 0) {
        0
    } else {
        (app.particleMasteredCount() * 100.0 / ParticleData.TOTAL_QUESTIONS).roundToInt()
    }
    return StudySnapshot(
        todayAnswers = app.todayActivity().answers,
        dueMistakes = app.mistakes.count { app.isReviewDue(it) } + app.particleDueCount(),
        verbPercent = app.verbModulePercent(),
        adjectivePercent = app.adjectiveModulePercent(),
        particlePercent = particlePercent,
        currentStreak = app.currentStreak(),
        totalAnswers = verbStats.correct + verbStats.incorrect + adjStats.correct + adjStats.incorrect,
        verbTotal = VerbData.VERBS.size,
    )
}
