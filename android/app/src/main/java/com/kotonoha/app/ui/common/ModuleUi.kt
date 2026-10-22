package com.kotonoha.app.ui.common

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotonoha.app.ui.theme.ModuleTone
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.moduleTone
import com.kotonoha.app.ui.theme.paperColors

/**
 * 三层模块（动词/形容词/助词）共用的视觉组件，规格与助词模块一致：
 * - 顶栏：返回 + 标题 + 右侧自定义区
 * - 底部操作条：cardRadius 顶圆角 + 1dp 细边 + cardShadow 阴影，背景铺到屏幕底
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
 * 模块底部操作条：cardRadius 顶圆角 + 1dp 细边 + cardShadow 阴影，与纸卡同层级。
 * 背景铺到屏幕底，由调用方在内容里加 [androidx.compose.foundation.layout.navigationBarsPadding]。
 */
@Composable
fun ModuleBottomSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val g = geometry()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = g.cardRadius, topEnd = g.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = g.cardShadow,
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
    val g = geometry()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(g.pillRadius))
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

/**
 * 分段标题：左标题 + 右侧说明（如「18 个」「基础 11」）。
 * 默认 muted；一级页传入本模块色块（tone = moduleTone()）时标题染成模块深字色，
 * 让区块标题成为页面上的第二处模块色落点。二级模块页保持 muted，视觉不变。
 */
@Composable
fun ModuleSectionLabel(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    tone: ModuleTone? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = tone?.deep ?: paperColors().muted,
        )
        caption?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = paperColors().muted)
        }
    }
}

/**
 * 一级页页头：大标题 + 一行副信息 + 可选右侧动作。
 *
 * 与 [ModuleTopBar] 的分工：TopBar 是二级页的返回条（48dp 高、带返回键），
 * 本组件是四个一级标签页的页头（无返回键、标题更大、跟随滚动）。
 * 标题取当前模块的深字色 [ModuleTone.deep]——模块色因此在每一页页头都出现一次。
 */
@Composable
fun ModuleHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tone = moduleTone()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tone.deep,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = paperColors().muted,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 模块图标底盒：主色浅底 + 主色图标，是「色块规则」的最小单元，
 * 入口卡、可点行、设置行、空态圆标共用同一个尺寸梯度。
 * 尺寸沿用既有视觉：卡片内 40dp、行内 36dp、圆标；圆角走 [geometry] 的 smallRadius，个别圆标用 [corner] 覆盖。
 */
@Composable
fun ModuleGlyphBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    corner: Dp? = null,
    iconSize: Dp = 22.dp,
    tone: ModuleTone = moduleTone(),
    contentDescription: String? = null,
) {
    val g = geometry()
    val c = corner ?: g.smallRadius
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(c))
            .background(tone.pale),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tone.main,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 模块入口卡（原主页样式迁入，主页/学习页共用）：
 * cardRadius 圆角纸白卡 + 40dp 图标盒 + 标题/副信息 + 右箭头，整行可点。
 * 读屏口径：clickable 自带合并子语义（AbstractClickableNode
 * .getShouldMergeDescendantSemantics=true，字节码实测），整卡即一个焦点站；
 * role=Button 让读屏报「按钮」而非只有可双击提示（IK-18）。
 */
@Composable
fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tone: ModuleTone = moduleTone(),
) {
    val g = geometry()
    val paper = paperColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(g.cardRadius))
            .clickable(onClick = onClick, role = Role.Button),
        shape = RoundedCornerShape(g.cardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = g.cardShadow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ModuleGlyphBox(icon = icon, tone = tone)
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = paper.muted,
            )
        }
    }
}

/** 「规划中」徽标：smallRadius 圆角（非 pill），模块浅底 + 1dp 模块描边，与选中胶囊明确区隔。 */
@Composable
fun PlannedBadge(tone: ModuleTone = moduleTone()) {
    val g = geometry()
    Surface(
        shape = RoundedCornerShape(g.smallRadius),
        color = tone.pale,
        border = BorderStroke(1.dp, tone.line),
    ) {
        Text(
            text = "规划中",
            style = MaterialTheme.typography.labelSmall,
            color = tone.deep,
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
        ModuleGlyphBox(icon = icon, size = 36.dp, corner = 10.dp, iconSize = 20.dp)
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
