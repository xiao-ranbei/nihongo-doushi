package com.nihongodoushi.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 筛选抽屉：把原顶部的两行下拉改为底部抽屉，减少顶部拥挤。
 * 选择即改草稿，点「应用并重新出题」才重建题池。
 */
data class FilterSection(
    val key: String,
    val label: String,
    val options: List<Pair<String, String>>,
    val selected: String,
    val defaultValue: String = options.firstOrNull()?.first ?: "all",
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    title: String,
    summary: String,
    sections: List<FilterSection>,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val draft = remember(sections) {
        mutableStateMapOf<String, String>().apply { sections.forEach { put(it.key, it.selected) } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "当前：" + summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = paperColors().textStrong,
                )
            }

            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = if (section.enabled) section.label else section.label + "（已按活用形式锁定）",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (section.enabled) paperColors().textStrong else paperColors().muted,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        section.options.forEach { (value, label) ->
                            val on = draft[section.key] == value
                            FilterPill(
                                label = label,
                                selected = on,
                                enabled = section.enabled,
                                onClick = { draft[section.key] = value },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = {
                    sections.forEach { draft[it.key] = it.defaultValue }
                }) {
                    Text("重置筛选", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onApply(draft.toMap()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("应用并重新出题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background = if (selected && enabled) scheme.primaryContainer else scheme.surface
    val foreground = when {
        !enabled -> paperColors().muted
        selected -> scheme.onPrimaryContainer
        else -> paperColors().textStrong
    }
    val border = when {
        !enabled -> scheme.outlineVariant
        selected -> scheme.primary
        else -> scheme.outline
    }
    val isSelected = selected && enabled
    Surface(
        // 选中态（IK-18）：筛选胶囊的选中目前只有颜色/字重差别，
        // 读屏用户必须能听出「已选择」，否则无法确认当前筛选项
        // （this.selected：函数参数 selected 会遮蔽接收者上的同名扩展属性；
        // 合并由 Surface(onClick) 内部 clickable 自带保证，无需再 merge）
        modifier = Modifier.semantics { this.selected = isSelected },
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = foreground,
        )
    }
}
