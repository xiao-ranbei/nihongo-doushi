package com.nihongodoushi.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 三层模块（动词/形容词/助词）共用的视觉组件，规格与助词模块一致：
 * - 顶栏：返回 + 标题 + 右侧自定义区
 * - 底部操作条：16dp 顶圆角 + 1dp 细边 + 2dp 阴影，背景铺到屏幕底
 *
 * 改动这里会同时影响三个模块，需对照 docs/design/particle-ui/ 的截图做视觉回归。
 */

/** 模块顶栏：返回 + 标题 + 右侧自定义区。 */
@Composable
fun ModuleTopBar(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = paperColors().textStrong,
                    )
                }
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            trailing?.invoke()
        }
    }
}

/**
 * 模块底部操作条：16dp 顶圆角 + 1dp 细边 + 2dp 阴影，与纸卡同层级。
 * 背景铺到屏幕底，由调用方在内容里加 [androidx.compose.foundation.layout.navigationBarsPadding]。
 */
@Composable
fun ModuleBottomSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(content = content)
    }
}

/** 掌握度进度条：x/n 的可视化部分（文案由调用方给）。 */
@Composable
fun ModuleProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 分段标题：左标题 + 右侧说明（如「18 个」「基础 11」）。 */
@Composable
fun ModuleSectionLabel(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = paperColors().muted,
        )
        caption?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = paperColors().muted)
        }
    }
}

/**
 * 模块入口卡（原主页样式迁入，主页/学习页共用）：
 * 16dp 圆角纸白卡 + 40dp 图标盒 + 标题/副信息 + 右箭头，整行可点。
 */
@Composable
fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val paper = paperColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = paper.muted,
            )
        }
    }
}

/** 「规划中」徽标：6dp 圆角（非 pill），surface 底 + 1dp 描边，与选中 pill 明确区隔。 */
@Composable
fun PlannedBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = "规划中",
            style = MaterialTheme.typography.labelSmall,
            color = paperColors().textStrong,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/**
 * 规划中信息行（查词 / 阅读工具 / 预留设置位共用）：
 * 36dp 图标盒 + 标题/描述 + 徽标。不包 clickable、无水波、无箭头——不可点击就不给可点 affordance。
 */
@Composable
fun PlannedRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val paper = paperColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = paper.textStrong,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
        }
        PlannedBadge()
    }
}
