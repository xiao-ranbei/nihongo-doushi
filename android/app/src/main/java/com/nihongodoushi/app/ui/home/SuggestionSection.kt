package com.nihongodoushi.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.AppTab
import com.nihongodoushi.app.engine.suggest.Suggestion
import com.nihongodoushi.app.engine.suggest.SuggestionAction
import com.nihongodoushi.app.ui.study.StudyDestination
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 主页「知识点建议」区（设计定稿 §3.4）：一张卡内 ≤3 行建议，行间 1dp 分隔。
 * 可执行项（action != null）：暖色图标盒 + 副文案 + 右箭头 + 整行可点；
 * 鼓励项：中性图标盒、无副文案、无箭头、不可点。引擎返回空列表时整节隐藏（由调用方控制）。
 */
@Composable
fun SuggestionSection(
    suggestions: List<Suggestion>,
    onOpen: (StudyDestination) -> Unit,
    onSelectTab: (AppTab) -> Unit,
) {
    Text(
        text = "知识点建议",
        style = MaterialTheme.typography.labelMedium,
        color = paperColors().muted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                SuggestionRow(
                    suggestion = suggestion,
                    onClick = { dispatch(suggestion, onOpen, onSelectTab) },
                )
            }
        }
    }
}

/** 动作分发：切学习标签 / 打开对应二级页 */
private fun dispatch(
    suggestion: Suggestion,
    onOpen: (StudyDestination) -> Unit,
    onSelectTab: (AppTab) -> Unit,
) {
    when (suggestion.action) {
        SuggestionAction.STUDY_TAB -> onSelectTab(AppTab.STUDY)
        SuggestionAction.VERB -> onOpen(StudyDestination.VERB)
        SuggestionAction.ADJECTIVE -> onOpen(StudyDestination.ADJECTIVE)
        SuggestionAction.PARTICLE -> onOpen(StudyDestination.PARTICLE)
        SuggestionAction.MISTAKES -> onOpen(StudyDestination.MISTAKES)
        null -> Unit
    }
}

/** 图标随规则固定（R1–R6 一一对应），用户可凭图标复认建议类型 */
private fun iconFor(suggestion: Suggestion): ImageVector = when {
    suggestion.id == "due-mistakes" -> Icons.Filled.Refresh
    suggestion.id == "today-empty" -> Icons.Filled.Create
    suggestion.id.startsWith("weakest-") -> Icons.Filled.Warning
    suggestion.id == "streak" -> Icons.Filled.ThumbUp
    suggestion.id == "new-user" -> Icons.Filled.Star
    else -> Icons.Filled.CheckCircle
}

/** 暖色 = 需要行动的可执行项；中性 = 纯展示的鼓励项（沿用「行动暖色 / 展示中性」通则） */
private fun isWarm(suggestion: Suggestion): Boolean =
    suggestion.action != null

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val paper = paperColors()
    val icon = iconFor(suggestion)
    val warm = isWarm(suggestion)
    val executable = suggestion.action != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (executable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (warm) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (warm) MaterialTheme.colorScheme.onTertiaryContainer else paper.textStrong,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (executable && suggestion.subText != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = suggestion.subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
        }
        if (executable) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = paper.muted,
            )
        }
    }
}
