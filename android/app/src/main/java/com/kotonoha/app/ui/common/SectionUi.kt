package com.kotonoha.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kotonoha.app.ui.glass.GlassShape
import com.kotonoha.app.ui.glass.GlassSurface
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.ModuleTone
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.moduleTone
import com.kotonoha.app.ui.theme.paperColors

/**
 * 一级页的信息层级组件（焦点卡 + 分区容器 + 容器行）。
 *
 * ## 为什么要有这一套
 *
 * 改造前四个一级页都是「页头 + 一叠同质玻璃卡」：每日一句、周历、建议、四个入口各占一张卡，
 * 材质、圆角、投影、间距全部相同 —— 视觉上分不出主次，一屏六张卡谁都不突出，下半屏却是空的。
 *
 * 这一套只做一件事：**用「一个焦点 + 若干分区」替代「N 张等权卡」**。
 * - [FocusCard] 全页唯一重心：更大的字号、更厚的投影、底部带主操作按钮
 * - [SectionGroup] 同类条目收进一个容器，行间用 1dp 分隔线，不再是各自一张卡
 * - [GroupRow] 容器里的一行：图标底盒 + 标题 + 副信息 + 右侧状态/箭头
 *
 * 卡片数量因此从 5–6 张降到 2–3 个容器，模块分割从「卡片边界」变成「容器边界」，
 * 层级由「焦点卡 vs 容器」的尺寸与投影差表达，而不是靠字号硬撑。
 */

/**
 * 焦点卡：整页唯一的视觉重心。
 *
 * 与普通玻璃卡的区别不只是尺寸：投影更厚（[GlassTokens.focusElevation]）、内边距更大、
 * 标题用 24sp、底部可以挂一个实心主操作按钮。这条按钮是「这一页现在该做什么」的唯一答案。
 *
 * @param eyebrow 顶部小标签（如「每日一句」「AI 阅读」）
 * @param title 主标题（谚语原文 / 功能名）
 * @param subtitle 副行（读音 / 一句话说明）
 * @param body 正文（释义 / 更长的说明），可为空
 * @param actionLabel 主操作按钮文案，为空则不显示按钮
 * @param onAction 主操作
 * @param trailing 右上角次级操作（如「换一句」）
 */
@Composable
fun FocusCard(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val paper = paperColors()
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = GlassTokens.focusElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
                if (trailing != null) {
                    trailing()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
            if (body != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = paper.textStrong,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(GlassTokens.radius),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 分区容器：一个小标题 + 一个玻璃容器，内部纵向排列 [GroupRow]，行间由调用方插 [GroupDivider]。
 *
 * 不自动插分隔线是为了让调用方能在特殊行之间留白（例如容器内还有一块自定义内容）。
 */
@Composable
fun SectionGroup(
    title: String?,
    modifier: Modifier = Modifier,
    tone: ModuleTone? = moduleTone(),
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            ModuleSectionLabel(
                title = title,
                caption = caption,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                tone = tone,
            )
        }
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** 容器内的 1dp 分隔线：从文字左边缘起画，不顶到卡边 —— 这是「同一组内容」的视觉暗示。 */
@Composable
fun GroupDivider(startInset: androidx.compose.ui.unit.Dp = 15.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * 容器里的一行：可选图标底盒 + 标题 + 副信息 + 右侧状态（文字 / 徽章 / 箭头）。
 *
 * 高度下限 [GlassTokens.groupRowMinHeight]；有副信息时自然撑高。
 * 可点行整行是一个焦点站（`clickable` 自带合并子语义，禁手工 merge）。
 */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: ModuleTone? = null,
    trailing: String? = null,
    badge: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val paper = paperColors()
    val g = geometry()
    val tone = iconTone ?: moduleTone()
    val clickable = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = modifier
            .then(clickable)
            .defaultMinSize(minHeight = GlassTokens.groupRowMinHeight)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(tone.pale, RoundedCornerShape(g.smallRadius)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.deep,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(tone.pale, RoundedCornerShape(GlassTokens.radius))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = tone.deep,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = paper.textStrong,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = paper.muted,
            )
        }
    }
}

/**
 * 容器内的自定义内容块（周历条这类不是「一行」但属于同一组的东西）：
 * 上下内边距与 [GroupRow] 对齐，视觉上仍像一个整体。
 */
@Composable
fun GroupBlock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        content()
    }
}

/** 次级文字按钮（焦点卡右上角这类位置用），命中区按 48dp 给足。 */
@Composable
fun InlineTextAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
