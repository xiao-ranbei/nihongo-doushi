package com.nihongodoushi.app.ui.practice

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.storage.ReviewPolicy
import com.nihongodoushi.app.ui.theme.paperColors
import kotlinx.coroutines.delay

/** 选择题界面组件：进度环、顶部信息区、题面卡、选项四态、判定抽屉。 */

/** 选项视觉状态（颜色 / 图标 / 边框样式三重冗余，色觉障碍下仍可区分） */
enum class OptionState { Default, Selected, Correct, Wrong, Reveal, Locked }

enum class ChipTone { Neutral, Green, Target }

@Composable
fun ProgressRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    strokeWidth: Dp = 4.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = scheme.outlineVariant,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            drawArc(
                color = scheme.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
        )
    }
}

/** 顶部统计卡：纸白卡（细边 + 轻阴影），含返回 · 进度环 · 题号 · 本轮统计 · 卡内底部进度条 */
@Composable
fun PracticeTopArea(
    roundLabel: String,
    statsLabel: String,
    progress: Float,
    progressLabel: String,
    hintEnabled: Boolean,
    hintOn: Boolean,
    onBack: (() -> Unit)? = null,
    onFilterClick: () -> Unit,
    onHintClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (onBack != null) 2.dp else 14.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回主页",
                            tint = paperColors().textStrong,
                        )
                    }
                }
                ProgressRing(progress = progress, label = progressLabel)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = roundLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = statsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = paperColors().muted,
                    )
                }
                IconButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "筛选练习范围",
                        tint = paperColors().textStrong,
                    )
                }
                IconButton(onClick = onHintClick, enabled = hintEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = if (hintOn) "隐藏假名与规律" else "显示假名与规律",
                        tint = if (hintOn) MaterialTheme.colorScheme.primary else paperColors().textStrong,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** 题面卡：chip 行 + 词条大字 + 读音（受辅助开关控制）+ 释义 + 指令 + 提示 */
@Composable
fun PromptCard(
    question: PracticeQuestion,
    modeLabel: String,
    kanaVisible: Boolean,
    compact: Boolean,
    hintOn: Boolean,
    onToggleHint: () -> Unit,
) {
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
                .padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = if (compact) 12.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Chip(modeLabel, ChipTone.Neutral)
                Chip(question.groupLabel, ChipTone.Green)
                Chip(question.targetLabel, ChipTone.Target)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = question.promptWord,
                    style = if (compact) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (kanaVisible) question.promptReading else "假名已隐藏",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (kanaVisible) paperColors().reading else paperColors().textStrong,
                )
            }
            Text(
                text = question.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = paperColors().meaning,
            )
            if (!compact) {
                Text(
                    text = question.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = paperColors().textStrong,
                )
                TextButton(onClick = onToggleHint) {
                    Text(
                        text = if (hintOn) "隐藏假名与规律" else "显示假名与规律",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (hintOn) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = paperColors().bgPaleGreen,
                    ) {
                        Text(
                            text = "提示：先判断词尾所在的段，再看这个形式是否需要音便；" +
                                "词尾为「む・ぶ・ぬ」时注意拨音便。",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = paperColors().textStrong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, tone: ChipTone) {
    val scheme = MaterialTheme.colorScheme
    val paper = paperColors()
    val background = when (tone) {
        ChipTone.Neutral -> paper.bgPaleGreen
        ChipTone.Green -> scheme.primaryContainer
        ChipTone.Target -> scheme.tertiaryContainer
    }
    val foreground = when (tone) {
        ChipTone.Neutral -> paper.textStrong
        ChipTone.Green -> scheme.onPrimaryContainer
        ChipTone.Target -> scheme.onTertiaryContainer
    }
    val border = when (tone) {
        ChipTone.Neutral -> scheme.outline
        ChipTone.Green -> paper.lineAccent
        ChipTone.Target -> scheme.tertiary
    }
    Surface(shape = RoundedCornerShape(999.dp), color = background, border = BorderStroke(1.dp, border)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (tone == ChipTone.Target) FontWeight.Bold else FontWeight.Normal,
            color = foreground,
        )
    }
}

/** 选项行：四态着色 + 序号块 + 状态图标 */
@Composable
fun OptionRow(
    index: Int,
    text: String,
    state: OptionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val paper = paperColors()
    val locked = state == OptionState.Locked
    val shape = RoundedCornerShape(12.dp)
    val cornerPx = with(LocalDensity.current) { 12.dp.toPx() }

    val borderColor = when (state) {
        OptionState.Selected, OptionState.Correct, OptionState.Reveal -> scheme.primary
        OptionState.Wrong -> scheme.error
        else -> scheme.outlineVariant
    }
    val background = when (state) {
        OptionState.Selected, OptionState.Correct -> scheme.primaryContainer
        OptionState.Wrong -> scheme.errorContainer
        else -> scheme.surface
    }
    val badgeBackground = when (state) {
        OptionState.Selected, OptionState.Correct -> scheme.primary
        OptionState.Wrong -> scheme.error
        else -> scheme.background
    }
    val badgeForeground = when (state) {
        OptionState.Selected, OptionState.Correct, OptionState.Wrong -> scheme.onPrimary
        else -> paper.textStrong
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .drawBehind {
                val width = 1.5.dp.toPx()
                val effect = if (state == OptionState.Reveal) {
                    PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
                } else {
                    null
                }
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(width / 2f, width / 2f),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = width, pathEffect = effect),
                )
            }
            // 读屏口径（IK-18）：clickable 自带合并子语义——
            // 序号 + 选项文本 + 判定图标（「正确」/「错误」）即一个焦点站；
            // 锁定态不可点但内容仍可读
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBackground)
                .drawBehind {
                    if (state != OptionState.Selected && state != OptionState.Correct && state != OptionState.Wrong) {
                        val width = 1.dp.toPx()
                        drawRoundRect(
                            color = scheme.outline,
                            topLeft = Offset(width / 2f, width / 2f),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = width),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = badgeForeground,
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        when (state) {
            OptionState.Correct, OptionState.Reveal -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "正确",
                tint = scheme.primary,
            )
            OptionState.Wrong -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "错误",
                tint = scheme.error,
            )
            else -> Unit
        }
    }
}

/** 判定抽屉：收起（未作答 / 已选，含双击提示）/ 展开（判定结果 + 解析）/ 本轮小结 */
@Composable
fun JudgementDock(
    question: PracticeQuestion?,
    pickedIndex: Int,
    judged: Boolean,
    correct: Boolean,
    roundComplete: Boolean = false,
    roundCorrect: Int = 0,
    roundWrong: Int = 0,
    roundSize: Int = 5,
    onConfirm: () -> Unit,
    onNext: () -> Unit,
) {
    if (question == null) return
    val scheme = MaterialTheme.colorScheme
    val paper = paperColors()
    val picked = pickedIndex in question.options.indices

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                // 抽屉背景铺到屏幕底，内容抬到手势条上方（与助词模块一致）
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (roundComplete) {
                Text(
                    text = "本轮完成",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "答对 $roundCorrect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = paper.statCorrectText,
                    )
                    Text(
                        text = "答错 $roundWrong",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = paper.statIncorrectText,
                    )
                    Text(
                        text = "正确率 " + if (roundCorrect + roundWrong == 0) {
                            "—"
                        } else {
                            Math.round(roundCorrect * 100.0 / (roundCorrect + roundWrong)).toString() + "%"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = paper.textStrong,
                    )
                }
                Text(
                    text = "本轮 $roundSize 题已完成；错题已按间隔复习计划排入。",
                    style = MaterialTheme.typography.labelMedium,
                    color = paper.textStrong,
                )
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("再来一轮", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            } else if (!judged) {
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
                            text = if (picked) question.options[pickedIndex].text else "请选择一个答案",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                            color = if (picked) scheme.onPrimaryContainer else paper.textStrong,
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
                        onClick = onConfirm,
                        enabled = picked,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("确认提交", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = if (correct) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            tint = if (correct) paper.statCorrectText else paper.statIncorrectText,
                        )
                        Text(
                            text = if (correct) "答对了" else "答错了",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (correct) paper.statCorrectText else paper.statIncorrectText,
                        )
                    }
                    if (!correct) {
                        Surface(shape = RoundedCornerShape(999.dp), color = scheme.tertiaryContainer) {
                            Text(
                                text = "已加入错题本",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Text(
                    text = question.written,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Text(
                    text = "读音：" + question.kana,
                    style = MaterialTheme.typography.labelMedium,
                    color = paper.reading,
                )
                if (picked) {
                    Text(
                        text = "你的答案：" + question.options[pickedIndex].text,
                        style = MaterialTheme.typography.labelMedium,
                        color = paper.textStrong,
                    )
                }
                question.voiceMeaning?.let { meaning ->
                    Text(
                        text = "语态：" + meaning,
                        style = MaterialTheme.typography.labelSmall,
                        color = paper.textStrong,
                    )
                }
                Text(
                    text = (if (question.voiceMeaning != null) "活用：" else "形式：") + question.formMeaning,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.textStrong,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = scheme.outlineVariant)
                Text(
                    text = ruleExplanation(question.explain, allFormLabels()),
                    style = MaterialTheme.typography.bodySmall,
                    color = paper.textStrong,
                )
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("下一题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyPoolHint(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "当前筛选范围没有可练习的题目。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onReset) { Text("重置筛选") }
    }
}

@Composable
fun ToastLine(toast: String?, onConsume: () -> Unit) {
    if (toast == null) return
    LaunchedEffect(toast) {
        delay(2500)
        onConsume()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = toast,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** 错题重练横幅：本轮待练数量 */
@Composable
fun ReviewBanner(reviewing: Boolean, remainingCount: Int) {
    if (!reviewing) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "错题重练中：本轮待练 $remainingCount 题，完成 " +
                ReviewPolicy.MASTERY_CORRECT_STREAK + " 次正确复习后移出",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * 学习时长采集：在当前屏可见期间计时，退到后台暂停，离屏时把累计秒数交给调用方落盘。
 * 只统计前台时间——应用切到后台不计，避免把挂机时间算成学习时长。
 */
@Composable
fun StudyTimeTracker(onSeconds: (Int) -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        var startedAt = System.currentTimeMillis()
        var accumulatedMs = 0L
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    if (startedAt > 0) {
                        accumulatedMs += System.currentTimeMillis() - startedAt
                        startedAt = 0L
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (startedAt == 0L) startedAt = System.currentTimeMillis()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (startedAt > 0) accumulatedMs += System.currentTimeMillis() - startedAt
            val seconds = (accumulatedMs / 1000L).toInt()
            if (seconds > 0) onSeconds(seconds)
        }
    }
}
