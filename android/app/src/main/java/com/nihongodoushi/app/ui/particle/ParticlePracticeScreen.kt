package com.nihongodoushi.app.ui.particle

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.particle.ParticleEngine
import com.nihongodoushi.app.engine.particle.ParticleQuestion
import com.nihongodoushi.app.engine.particle.ParticleQuestionTypes
import com.nihongodoushi.app.ui.practice.OptionRow
import com.nihongodoushi.app.ui.practice.OptionState
import com.nihongodoushi.app.ui.practice.ProgressRing
import com.nihongodoushi.app.ui.practice.StudyTimeTracker
import com.nihongodoushi.app.ui.practice.ToastLine
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 助词练习屏。
 *
 * 沿用答题契约的三段式：题面卡 → 选项组 → 底部判定抽屉；两步式作答（选择 → 确认提交），
 * 双击同一选项可直接提交。差异在于题面：选助词题给「前文 + 空位 + 后文」，
 * 判作用题给整句并高亮目标助词。
 */
@Composable
fun ParticlePracticeScreen(
    state: ParticlePracticeState,
    onBack: () -> Unit,
    onStartAll: () -> Unit,
) {
    StudyTimeTracker(onSeconds = { state.app.addStudySeconds(it) })
    val item = state.current
    val paper = paperColors()
    val scrollState = rememberScrollState()

    // 换题时回到顶部，避免上一题的滚动位置带过来
    LaunchedEffect(state.index, state.scope) {
        scrollState.scrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                ProgressRing(
                    progress = state.progress,
                    label = state.progressLabel,
                    size = 36.dp,
                    strokeWidth = 3.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.roundLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = state.statsLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.muted,
                    )
                }
                IconButton(onClick = { state.toggleHint() }, enabled = state.hintEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = if (state.hintOn) "隐藏提示与读音" else "显示提示与读音",
                        tint = when {
                            state.hintOn -> MaterialTheme.colorScheme.primary
                            state.hintEnabled -> paper.textStrong
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                }
            }
        }

        ToastLine(state.toast, onConsume = { state.consumeToast() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (state.reviewing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "错题复习中：本轮待复习 ${state.total} 题，答对推进间隔，连续 6 次移出",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            if (state.total == 0) {
                EmptyReviewPanel(onStartAll = onStartAll)
            } else if (item != null) {
                ParticlePromptCard(
                    question = item.question,
                    kanaVisible = state.kanaVisible,
                    hintOn = state.hintOn,
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (item.question.type == ParticleQuestionTypes.FILL) {
                    item.options.chunked(2).forEachIndexed { rowIndex, texts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            texts.forEachIndexed { columnIndex, text ->
                                val index = rowIndex * 2 + columnIndex
                                OptionRow(
                                    index = index,
                                    text = text,
                                    state = state.optionState(index),
                                    modifier = Modifier.weight(1f),
                                    onClick = { state.tapOption(index) },
                                )
                            }
                            if (texts.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    item.options.forEachIndexed { index, text ->
                        OptionRow(
                            index = index,
                            text = text,
                            state = state.optionState(index),
                            onClick = { state.tapOption(index) },
                        )
                    }
                }
            }
        }

        ParticleDock(
            state = state,
            onStartAll = onStartAll,
        )
    }
}

/** 题面卡：语境 → 句子（填空位 / 高亮目标助词）→ 读音 → 提示。 */
@Composable
private fun ParticlePromptCard(
    question: ParticleQuestion,
    kanaVisible: Boolean,
    hintOn: Boolean,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = question.context,
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
            if (question.type == ParticleQuestionTypes.FILL) {
                Text(
                    text = fillSentence(question),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "请选出句中缺少的助词",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.textStrong,
                )
            } else {
                Text(
                    text = functionSentence(question),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = question.targetParticle?.let { "句中「$it」在这里起什么作用？" } ?: "判断目标助词的作用",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.textStrong,
                )
            }
            Text(
                text = if (kanaVisible) kanaLine(question) else "假名已隐藏",
                style = MaterialTheme.typography.labelMedium,
                color = if (kanaVisible) paper.reading else paper.textStrong,
            )
            if (hintOn) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = paper.bgPaleGreen,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = ParticleEngine.hint(question),
                            style = MaterialTheme.typography.labelMedium,
                            color = paper.textStrong,
                        )
                        Text(
                            text = "用过提示的题不计入掌握度",
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.muted,
                        )
                    }
                }
            }
        }
    }
}

private fun fillSentence(question: ParticleQuestion): String =
    (question.before ?: "") + "（　）" + (question.after ?: "")

private fun functionSentence(question: ParticleQuestion): AnnotatedString {
    val before = question.before ?: ""
    val after = question.after ?: ""
    val target = question.targetParticle
    return buildAnnotatedString {
        append(before)
        if (target != null) {
            append(target)
        }
        append(after)
        if (target != null) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = Color(0xFF254A3D),
                    background = Color(0xFFEDF3EB),
                ),
                before.length,
                before.length + target.length,
            )
        }
    }
}

private fun kanaLine(question: ParticleQuestion): String {
    val readBefore = question.readingBefore ?: ""
    val readAfter = question.readingAfter ?: ""
    if (question.type == ParticleQuestionTypes.FILL) {
        return readBefore + "（　）" + readAfter
    }
    // 判作用题：题库里 readingBefore 有的已含目标助词（母は → はは），有的没有（天気 → てんき），
    // 这里按「读音是否已经包含目标助词」决定是否补 targetReading，避免出现「てんきいいから」。
    val particle = question.targetParticle.orEmpty()
    val particleKana = question.targetReading.orEmpty()
    val alreadyIncluded = particle.isNotEmpty() &&
        (readBefore.endsWith(particle) || (particleKana.isNotEmpty() && readBefore.endsWith(particleKana)))
    return readBefore + (if (alreadyIncluded) "" else particleKana.ifEmpty { particle }) + readAfter
}

/** 底部判定抽屉：未判定 → 已选/提交；已判定 → 结果 + 解析；本轮完成 → 小结。 */
@Composable
private fun ParticleDock(
    state: ParticlePracticeState,
    onStartAll: () -> Unit,
) {
    val paper = paperColors()
    val item = state.current
    val question = item?.question

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                // 抽屉背景铺到屏幕底，内容再抬到手势条上方：既不露出 Scaffold 底色缝，也不压住导航条
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                state.roundComplete -> {
                    Text(
                        text = "本轮完成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "答对 ${state.roundCorrect}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = paper.statCorrectText,
                        )
                        Text(
                            text = "答错 ${state.roundWrong}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = paper.statIncorrectText,
                        )
                        Text(
                            text = state.statsLabel.substringAfter("正确率"),
                            style = MaterialTheme.typography.labelLarge,
                            color = paper.textStrong,
                        )
                    }
                    Text(
                        text = if (state.reviewing) {
                            "答对的错题已推进复习间隔；连续答对 6 次才移出，再答错会从头计算。"
                        } else {
                            "本轮 ${state.total} 题已完成；答错已加入错题本。"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = paper.textStrong,
                    )
                    Button(
                        onClick = { state.restart() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("再来一轮", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                question == null -> {
                    Button(
                        onClick = onStartAll,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("去综合练习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }

                !state.judged -> {
                    val picked = state.selectedIndex in (item?.options?.indices ?: IntRange.EMPTY)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (picked) "已选答案" else "尚未作答",
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.textStrong,
                            )
                            Text(
                                text = if (picked) item!!.options[state.selectedIndex] else "请选择一个答案",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                color = if (picked) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    paper.textStrong
                                },
                            )
                            if (picked) {
                                Text(
                                    text = "再次点击该选项可直接提交",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = paper.textStrong,
                                )
                            }
                        }
                        Button(
                            onClick = { state.commit() },
                            enabled = picked,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("确认提交", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = if (state.lastCorrect) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (state.lastCorrect) paper.statCorrectText else paper.statIncorrectText,
                        )
                        Text(
                            text = if (state.lastCorrect) "答对了" else "答错了",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.lastCorrect) paper.statCorrectText else paper.statIncorrectText,
                        )
                        if (!state.lastCorrect) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = "已加入错题本",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                    Text(
                        text = "正确答案：" + ParticleEngine.answerLabel(question),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val pickedText = item?.options?.getOrNull(state.selectedIndex)
                    if (state.lastCorrect && ParticleEngine.otherAccepted(question, pickedText).isNotEmpty()) {
                        Text(
                            text = "「" + ParticleEngine.otherAccepted(question, pickedText).joinToString("」「") +
                                "」也成立，两种说法都自然。",
                            style = MaterialTheme.typography.labelMedium,
                            color = paper.textStrong,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = ParticleEngine.explanation(question, state.lastCorrect),
                        style = MaterialTheme.typography.bodySmall,
                        color = paper.textStrong,
                    )
                    if (!state.lastCorrect) {
                        val note = ParticleEngine.optionNote(question, pickedText)
                        Text(
                            text = "你的选择「" + (pickedText ?: "") + "」：" +
                                (note ?: "在本句语境下不成立。"),
                            style = MaterialTheme.typography.labelMedium,
                            color = paper.statIncorrectText,
                        )
                    }
                    Button(
                        onClick = { state.next() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = if (state.index + 1 >= state.total) "查看本轮小结" else "下一题",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReviewPanel(onStartAll: () -> Unit) {
    val paper = paperColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "没有待复习的助词错题",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "错题在综合练习或专题练习中答错时自动加入。",
            style = MaterialTheme.typography.labelMedium,
            color = paper.muted,
        )
        TextButton(onClick = onStartAll) { Text("去综合练习") }
    }
}
