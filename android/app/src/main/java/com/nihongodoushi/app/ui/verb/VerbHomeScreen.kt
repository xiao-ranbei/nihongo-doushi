package com.nihongodoushi.app.ui.verb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.common.ModuleBottomSurface
import com.nihongodoushi.app.ui.common.ModuleSectionLabel
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.practice.ProgressRing
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 动词模块首页（三层改造 §3）：形式卡（基础/进阶两段）+ 语态卡 + 今日卡 + 速查入口 + 固定「综合练习」。
 * 卡片规格与助词专题卡一致：掌握度按「该形式下已掌握的词条数 / 词库总数」计算（数据驱动）。
 */
@Composable
fun VerbHomeScreen(
    app: AppState,
    onBack: () -> Unit,
    onOpenTheme: (VerbTheme) -> Unit,
    onStartTheme: (VerbTheme) -> Unit,
    onStartAll: () -> Unit,
    onStartReview: () -> Unit,
    onOpenReference: () -> Unit,
) {
    val paper = paperColors()
    val total = VerbData.VERBS.size
    val percent = app.verbModulePercent()
    val mistakes = app.verbMistakes().size
    val due = app.verbMistakes().count { app.isReviewDue(it) }
    val todayAnswers = app.todayActivity().verbAnswers

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            title = "动词练习",
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
                        text = "已掌握 ${app.progress.verbMastered.size} 个组合",
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
                        text = "今日动词",
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
                            "错题 0 道 · $total 个词条 · ${VerbData.INFLECTIONS.size} 种形式 + ${VerbData.VOICES.size} 种语态"
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

            OutlinedButton(
                onClick = onOpenReference,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "活用速查：按词条查全部变形",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            ModuleSectionLabel(
                title = "活用形式 · 基础",
                caption = "${VerbData.INFLECTIONS.count { it.level == "basic" }} 个",
                modifier = Modifier.padding(top = 2.dp),
            )
            ThemeCardGrid(
                themes = VerbData.INFLECTIONS.filter { it.level == "basic" }
                    .map { VerbTheme.Form(it.id) },
                masteredOf = { theme ->
                    when (theme) {
                        is VerbTheme.Form -> app.verbMasteredForForm(theme.formId)
                        is VerbTheme.Voice -> app.verbMasteredForVoice(theme.voiceId)
                    }
                },
                onClick = onOpenTheme,
                onDrill = onStartTheme,
            )

            ModuleSectionLabel(
                title = "活用形式 · 进阶",
                caption = "${VerbData.INFLECTIONS.count { it.level == "advanced" }} 个",
                modifier = Modifier.padding(top = 6.dp),
            )
            ThemeCardGrid(
                themes = VerbData.INFLECTIONS.filter { it.level == "advanced" }
                    .map { VerbTheme.Form(it.id) },
                masteredOf = { theme ->
                    when (theme) {
                        is VerbTheme.Form -> app.verbMasteredForForm(theme.formId)
                        is VerbTheme.Voice -> app.verbMasteredForVoice(theme.voiceId)
                    }
                },
                onClick = onOpenTheme,
                onDrill = onStartTheme,
            )

            ModuleSectionLabel(
                title = "语态",
                caption = "${VerbData.VOICES.count { it.id != "base" }} 个 · 配合辞书形",
                modifier = Modifier.padding(top = 6.dp),
            )
            ThemeCardGrid(
                themes = VerbData.VOICES.filter { it.id != "base" }
                    .map { VerbTheme.Voice(it.id) },
                masteredOf = { theme ->
                    when (theme) {
                        is VerbTheme.Form -> app.verbMasteredForForm(theme.formId)
                        is VerbTheme.Voice -> app.verbMasteredForVoice(theme.voiceId)
                    }
                },
                onClick = onOpenTheme,
                onDrill = onStartTheme,
            )

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
