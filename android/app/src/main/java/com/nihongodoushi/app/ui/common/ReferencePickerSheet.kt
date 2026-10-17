package com.nihongodoushi.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.theme.paperColors

/** 速查页词条选择器的一行数据（动词/形容词共用）。 */
data class ReferencePickerItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val caption: String,
    /** 搜索匹配用的附加字段（读音、词义等），与展示无关。 */
    val keywords: List<String> = emptyList(),
)

/**
 * 速查页词条选择抽屉（动词/形容词共用）：搜索框 + 全量列表，点选即换词条。
 * 搜索覆盖标题、副标题与关键词（写法/读音/词义），空关键词时显示全部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferencePickerSheet(
    items: List<ReferencePickerItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val visible = if (trimmed.isEmpty()) {
        items
    } else {
        items.filter { item ->
            item.title.contains(trimmed, ignoreCase = true) ||
                item.subtitle.contains(trimmed, ignoreCase = true) ||
                item.keywords.any { it.contains(trimmed, ignoreCase = true) }
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "选择词条",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索写法、读音或词义") },
                shape = RoundedCornerShape(12.dp),
            )
            Text(
                text = "共 ${items.size} 个词条" + if (visible.size != items.size) " · 匹配 ${visible.size} 个" else "",
                style = MaterialTheme.typography.labelMedium,
                color = paperColors().textStrong,
            )
            if (visible.isEmpty()) {
                Text(
                    text = "没有匹配的词条，换个关键词试试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = paperColors().muted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { item ->
                        ReferencePickerRow(
                            item = item,
                            selected = item.id == selectedId,
                            onClick = { onSelect(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferencePickerRow(
    item: ReferencePickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else paper.textStrong,
                )
            }
            Text(
                text = item.caption,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else paper.muted,
            )
            if (selected) {
                Text(
                    text = "当前",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 32.dp, height = 20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
