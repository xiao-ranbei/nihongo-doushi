package com.nihongodoushi.app.ui.mistakes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.storage.MistakeRecord
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.particle.ParticleMistakeItem
import com.nihongodoushi.app.ui.particle.particleMistakeItems
import com.nihongodoushi.app.ui.practice.AdjectivePracticeState
import com.nihongodoushi.app.ui.practice.VerbGroupLabels
import com.nihongodoushi.app.ui.practice.VerbPracticeState
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.Instant
import java.time.ZoneId

/** 错题本：按模块分组展示、重练入口与清空（对齐 web 错题视图，MVP 不含导入导出）。 */

@Composable
fun MistakesScreen(
    app: AppState,
    verbState: VerbPracticeState,
    adjectiveState: AdjectivePracticeState,
    onBack: (() -> Unit)? = null,
    onReviewVerb: (() -> Unit)? = null,
    onReviewAdjective: (() -> Unit)? = null,
    onReviewParticle: (() -> Unit)? = null,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val particleItems = app.particleMistakeItems()
    val totalCount = app.mistakes.size + particleItems.size

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回主页",
                        tint = paperColors().textStrong,
                    )
                }
            }
            Text(
                text = "错题本",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (totalCount == 0) {
            Text(text = "当前没有错题。答错的题目会按间隔复习计划出现在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MistakeGroup(
                title = "动词错题（${app.verbMistakes().size}）",
                records = app.verbMistakes(),
                onReview = { onReviewVerb?.invoke() ?: verbState.startMistakeReview() },
            )
            MistakeGroup(
                title = "形容词错题（${app.adjectiveMistakes().size}）",
                records = app.adjectiveMistakes(),
                onReview = { onReviewAdjective?.invoke() ?: adjectiveState.startMistakeReview() },
            )
            ParticleMistakeGroup(
                items = particleItems,
                onReview = onReviewParticle,
            )
            OutlinedButton(onClick = { confirmClear = true }) {
                Text("清空错题")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空错题？") },
            text = {
                Text(
                    "将删除全部 $totalCount 道错题（含助词 ${particleItems.size} 道），" +
                        "动词与形容词的复习进度一并清除，此操作不可撤销。练习统计不受影响。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    app.clearMistakes()
                    app.clearParticleMistakes()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MistakeGroup(title: String, records: List<MistakeRecord>, onReview: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onReview, enabled = records.isNotEmpty()) { Text("重练") }
            }
            if (records.isEmpty()) {
                Text(text = "本组暂无错题。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(records, key = { it.key }) { record ->
                        MistakeRow(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeRow(record: MistakeRecord) {
    val entryText: String
    val targetText: String
    if (record.isAdjective) {
        val adjective = AppState.adjectiveById[record.adjectiveId]
        entryText = adjective?.word ?: record.adjectiveId.orEmpty()
        targetText = record.formId?.let { VerbGroupLabels.adjFormLabel(it) }.orEmpty()
    } else {
        val verb = AppState.verbById[record.verbId]
        entryText = if (verb != null) verb.dictionary + "（" + verb.reading + "）" else record.verbId.orEmpty()
        targetText = listOfNotNull(
            record.voiceId?.takeIf { it != "base" }?.let { VerbGroupLabels.voiceLabel(it) },
            record.inflectionId?.let { VerbGroupLabels.inflectionLabel(it) },
        ).joinToString(" · ")
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$entryText · $targetText",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(text = "错 ${record.count} 次", style = MaterialTheme.typography.labelMedium, color = paperColors().textRed)
        }
        if (record.wrongAnswer.isNotBlank()) {
            Text(text = "上次错误：" + record.wrongAnswer, style = MaterialTheme.typography.bodySmall, color = paperColors().textStrong)
        }
        Text(text = "下次复习：" + formatDue(record), style = MaterialTheme.typography.bodySmall, color = paperColors().textStrong)
    }
}

/** 对齐 web formatReviewDate：已到期或无效显示「现在」，否则显示本地 M/d。 */
private fun formatDue(record: MistakeRecord): String = formatDueAt(record.dueAt)

private fun formatDueAt(dueAt: String): String {
    val parsed = runCatching { Instant.parse(dueAt) }.getOrNull() ?: return "现在"
    if (!parsed.isAfter(Instant.now())) return "现在"
    val date = parsed.atZone(ZoneId.systemDefault())
    return date.monthValue.toString() + "/" + date.dayOfMonth
}

private fun particleDueLabel(dueAt: String): String = formatDueAt(dueAt)

/**
 * 助词组：与动词/形容词组并列，同样显示「下次复习」与错误次数（D-022 起助词也有排期）。
 * 组头按钮进入模块内复习模式，答对推进间隔，连续 6 次移出。
 */
@Composable
private fun ParticleMistakeGroup(
    items: List<ParticleMistakeItem>,
    onReview: (() -> Unit)?,
) {
    val paper = paperColors()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "助词错题（${items.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (onReview != null) {
                    Button(onClick = onReview, enabled = items.isNotEmpty()) { Text("复习") }
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "本组暂无错题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items, key = { it.questionId }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.lessonParticles + " · " + item.context,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "下次 " + particleDueLabel(item.dueAt),
                                    modifier = Modifier.padding(start = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = paper.textRed,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            Text(
                                text = item.sentence,
                                style = MaterialTheme.typography.bodySmall,
                                color = paper.textStrong,
                            )
                            Text(
                                text = "错 ${item.count} 次" +
                                    (if (item.correctStreak > 0) " · 已连对 ${item.correctStreak}/6" else "") +
                                    " · 间隔 ${item.intervalDays} 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.muted,
                            )
                        }
                    }
                }
                Text(
                    text = "答对推进 1/3/7/14/30 天间隔，连续答对 6 次移出。",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
        }
    }
}
