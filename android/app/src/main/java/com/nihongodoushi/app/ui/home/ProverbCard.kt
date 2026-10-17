package com.nihongodoushi.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.proverb.Proverb
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 主页谚语卡（设计定稿 §3.2）：「每日一句」标记 + 原文/读音/大意 + 右上「换一句」。
 * 文案由 [ProverbData] 逐字交付；换一句交互由 [ProverbPicker] 保证不连续重复。
 */
@Composable
fun ProverbCard(
    proverb: Proverb,
    onRefresh: () -> Unit,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "每日一句",
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "换一句",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = proverb.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = proverb.reading,
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = proverb.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = paper.textStrong,
            )
        }
    }
}
