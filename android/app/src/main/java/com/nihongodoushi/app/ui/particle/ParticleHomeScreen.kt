package com.nihongodoushi.app.ui.particle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleLesson
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.practice.ProgressRing
import com.nihongodoushi.app.ui.theme.paperColors
import kotlin.math.roundToInt

/**
 * 助词模块首页（方案 A · 专题地图）。
 *
 * 结构：顶栏（返回 + 掌握度环 + x/48）→ 今日助词卡 → 9 张专题卡（2 列）→ 固定「综合练习」按钮。
 * 专题卡：单击进讲解页；点右上「练」或长按直接开练（D-021 要求长按不是唯一路径）。
 */
@Composable
fun ParticleHomeScreen(
    app: AppState,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onStartLesson: (String) -> Unit,
    onStartAll: () -> Unit,
    onStartReview: () -> Unit,
) {
    val paper = paperColors()
    val total = ParticleData.TOTAL_QUESTIONS
    val mastered = app.particleMasteredCount()
    val percent = if (total == 0) 0 else (mastered * 100.0 / total).roundToInt()
    val mistakes = app.particleMistakes.size
    val due = app.particleDueCount()

    Column(modifier = Modifier.fillMaxSize()) {
        ParticleTopBar(
            title = "助词学习",
            onBack = onBack,
            trailing = {
                ProgressRing(
                    progress = if (total == 0) 0f else mastered.toFloat() / total,
                    label = "",
                    size = 30.dp,
                    strokeWidth = 3.dp,
                )
                Column(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$mastered/$total",
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = paper.bgPaleGreen,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "今日助词",
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                    )
                    Text(
                        text = when {
                            due > 0 -> "今天有 $due 道错题到期"
                            mistakes > 0 -> "错题都在计划里，今天无到期"
                            app.todayActivity().particleAnswers > 0 ->
                                "今天已练 ${app.todayActivity().particleAnswers} 题，继续"
                            else -> "今天还没有练习"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (mistakes == 0) {
                            "已掌握 $mastered / $total 题 · 9 个专题"
                        } else {
                            "错题 $mistakes 道 · 到期 $due 道 · 已掌握 $mastered / $total 题"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = paper.textStrong,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onStartAll,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("开始 5 题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onStartReview,
                            enabled = mistakes > 0,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (mistakes == 0) "暂无错题" else "错题 $mistakes 道",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "专题", style = MaterialTheme.typography.labelMedium, color = paper.muted)
                Text(text = "9 个", style = MaterialTheme.typography.labelSmall, color = paper.muted)
            }

            ParticleData.LESSONS.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { lesson ->
                        ParticleLessonCard(
                            lesson = lesson,
                            mastered = app.particleMasteredIn(lesson.id),
                            total = ParticleEngine.questionsOf(lesson.id).size,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenLesson(lesson.id) },
                            onDrill = { onStartLesson(lesson.id) },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Text(
                text = "单击专题卡看讲解；点「练」或长按直接开练",
                modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Button(
                onClick = onStartAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "综合练习 · $total 题",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParticleLessonCard(
    lesson: ParticleLesson,
    mastered: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDrill: () -> Unit,
) {
    val paper = paperColors()
    val fraction = if (total == 0) 0f else mastered.toFloat() / total
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDrill),
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
                    text = lesson.particles.joinToString(" / "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = onDrill,
                    modifier = Modifier.size(width = 40.dp, height = 28.dp),
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
                text = lesson.title.substringBefore("：", lesson.title),
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
                text = "$mastered/$total 题",
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** 助词模块共用的顶栏：返回 + 标题 + 右侧自定义区。 */
@Composable
internal fun ParticleTopBar(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = paperColors().textStrong,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            trailing?.invoke()
        }
    }
}
