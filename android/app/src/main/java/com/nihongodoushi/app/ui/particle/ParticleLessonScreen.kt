package com.nihongodoushi.app.ui.particle

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleExample
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 专题讲解页。
 *
 * 结构：顶栏（返回 + 专题名 + 「练」）→ 助词徽标 → 入门说明（常显）→ 进阶说明（折叠「再进一步」）
 * → 例句（先给句子，`translation` 与 `note` 折在「看讲解」后）→ 固定「开始练习」。
 *
 * 不做难度选择（D-018）：两层说明同页，进阶只是折叠，不是另一种模式。
 */
@Composable
fun ParticleLessonScreen(
    app: AppState,
    lessonId: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val lesson = ParticleEngine.lesson(lessonId) ?: return
    val paper = paperColors()
    val questions = ParticleEngine.questionsOf(lessonId)
    val mastered = app.particleMasteredIn(lessonId)
    var showAdvanced by remember(lessonId) { mutableStateOf(false) }
    val revealed = remember(lessonId) { mutableStateListOf<Boolean>().apply { repeat(lesson.examples.size) { add(false) } } }

    Column(modifier = Modifier.fillMaxSize()) {
        ParticleTopBar(
            title = lesson.title,
            onBack = onBack,
            trailing = {
                TextButton(onClick = onStart) {
                    Text(
                        text = "练",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                lesson.particles.forEach { particle ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = particle,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    text = "已掌握 $mastered/${questions.size} 题",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = lesson.beginnerSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            text = if (showAdvanced) "收起进阶说明" else "再进一步",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (showAdvanced) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = paper.bgPaleGreen,
                        ) {
                            Text(
                                text = lesson.advancedSummary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = paper.textStrong,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "例句", style = MaterialTheme.typography.labelMedium, color = paper.muted)
                Text(
                    text = "${lesson.examples.size} 条 · 先自己判断再看讲解",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }

            lesson.examples.forEachIndexed { index, example ->
                ParticleExampleCard(
                    example = example,
                    revealed = revealed.getOrElse(index) { false },
                    onToggle = { if (index in revealed.indices) revealed[index] = !revealed[index] },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "开始练习（本专题 ${questions.size} 题）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ParticleExampleCard(
    example: ParticleExample,
    revealed: Boolean,
    onToggle: () -> Unit,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = example.context,
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
            Text(
                text = example.sentence,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = example.reading,
                style = MaterialTheme.typography.labelMedium,
                color = paper.reading,
            )
            if (revealed) {
                Text(
                    text = example.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = paper.meaning,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = paper.bgPaleGreen,
                ) {
                    Text(
                        text = example.note,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = paper.textStrong,
                    )
                }
                example.contrastNote?.let { contrast ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = paper.orangePale,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "换个助词会怎样",
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.muted,
                            )
                            Text(
                                text = contrast,
                                style = MaterialTheme.typography.labelMedium,
                                color = paper.textStrong,
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = onToggle,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = if (revealed) "收起讲解" else "看讲解",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
