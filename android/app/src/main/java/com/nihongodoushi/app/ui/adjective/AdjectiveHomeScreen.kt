package com.nihongodoushi.app.ui.adjective

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.mastery.Mastery
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.common.ModuleBottomSurface
import com.nihongodoushi.app.ui.common.ModuleProgressBar
import com.nihongodoushi.app.ui.common.ModuleSectionLabel
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.practice.ProgressRing
import com.nihongodoushi.app.ui.theme.paperColors
import kotlin.math.roundToInt

/** 形容词模块首页：7 张形式卡 + 今日卡 + 固定「综合练习」。 */
@Composable
fun AdjectiveHomeScreen(
    app: AppState,
    onBack: () -> Unit,
    onOpenTheme: (String) -> Unit,
    onStartTheme: (String) -> Unit,
    onStartAll: () -> Unit,
    onStartReview: () -> Unit,
) {
    val paper = paperColors()
    val total = AdjectiveData.ADJECTIVES.size
    val percent = app.adjectiveModulePercent()
    val mistakes = app.adjectiveMistakes().size
    val due = app.adjectiveMistakes().count { app.isReviewDue(it) }
    val todayAnswers = app.todayActivity().adjectiveAnswers

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            title = "形容词练习",
            onBack = onBack,
            trailing = {
                ProgressRing(
                    progress = percent / 100f,
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
                        text = "已掌握 ${app.progress.adjMastered.size} 个组合",
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
                        text = "今日形容词",
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                    )
                    Text(
                        text = when {
                            due > 0 -> "今天有 $due 道错题到期"
                            todayAnswers > 0 -> "今天已练 $todayAnswers 题，继续"
                            else -> "今天还没有练习"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (mistakes == 0) {
                            "错题 0 道 · 15 个词条 · ${AdjectiveData.FORMS.size} 种形式"
                        } else {
                            "错题 $mistakes 道 · 到期 $due 道"
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
                            Text("随机 5 题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

            ModuleSectionLabel(
                title = "活用形式",
                caption = "${AdjectiveData.FORMS.size} 个（基础/进阶混合）",
                modifier = Modifier.padding(top = 2.dp),
            )

            AdjectiveData.FORMS.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { form ->
                        val mastered = app.adjectiveMasteredForForm(form.id)
                        val fraction = if (total == 0) 0f else mastered.toFloat() / total
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { onOpenTheme(form.id) },
                                    onLongClick = { onStartTheme(form.id) },
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
                                        text = form.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = { onStartTheme(form.id) },
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
                                    text = if (form.level == "advanced") "进阶" else "基础",
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

            Text(
                text = "单击卡片看讲解；点「练」或长按直接开练",
                modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
        }

        ModuleBottomSurface {
            Button(
                onClick = onStartAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "综合练习 · 全部组合",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
