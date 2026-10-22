package com.kotonoha.app.ui.read

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.paperColors
import kotlinx.coroutines.launch

private val DRAWER_LABEL_SIZE = 14.sp
private val DRAWER_HINT_SIZE = 11.sp

/**
 * 阅读器右侧的侧边抽屉（用户 2026-10 要求把注音/译文两个开关收进抽屉，顶栏只留标题与图标）。
 *
 * 为什么用 [ModalNavigationDrawer] 而不是自绘浮层：抽屉的遮罩、返回键与关闭动画都由 M3 管着，
 * 自绘一份就要自己处理这三件事，且和 WordDetailSheet（另一个 ModalBottomSheet）叠加时会出现
 * 两层遮罩抢返回键的问题。
 *
 * [gesturesEnabled] 写成 `drawerState.isOpen` 而不是常量：M3 的遮罩点击（onClose）与收起手势共用这一个
 * 开关，写 false 会连带把「点抽屉外空白关闭」一起关掉——那样用户打开抽屉后除了点某一项（会顺带导航或
 * 清缓存）就再没有只关掉抽屉的路。关着时它为 false：左缘横滑会和系统返回手势争同一片屏幕边缘，
 * 在阅读页上「划一下想返回」变成「滑出抽屉」是更糟的错；打开时它为 true，遮罩可点、也能滑动收起。
 *
 * 返回键自己接管（[BackHandler]）：阅读器的容器 ReadFlow 在 READER 路由上无条件接管返回键，
 * 抽屉开着时若不先吃掉这次返回，用户会直接从阅读器退回输入页，观感上等于文章丢了。
 * 本组件在 ReadReaderScreen 内部组合、晚于 ReadFlow 注册，所以开着时这一段先拿到事件。
 *
 * 抽屉侧边靠 RTL 布局方向整体镜像到右侧，内容是 LTR（正文与逐词块都是横向文字，不能被镜像）。
 *
 * 抽屉里的文案只能用 web/font/subset-chars.txt 收录的汉字：子集外的字会触发回退字体并改变行高。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadSideMenu(
    showFurigana: Boolean,
    onToggleFurigana: (Boolean) -> Unit,
    showTranslation: Boolean,
    onToggleTranslation: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onClearCache: () -> Unit,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { drawerState.close() } }
    // 只在抽屉开着时接管返回：关着时不注册回调，把手势原样交回 ReadFlow（退出阅读器）
    BackHandler(enabled = drawerState.isOpen, onBack = close)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // 关着时禁用滑出手势（避开系统返回手势的边缘），打开时恢复：遮罩点击靠它才能关闭抽屉
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                        ) {
                            Text(
                                text = "阅读设置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(14.dp))
                            ReadToggle(
                                label = "注音",
                                checked = showFurigana,
                                onCheckedChange = { onToggleFurigana(it); close() },
                            )
                            Spacer(Modifier.height(6.dp))
                            ReadToggle(
                                label = "译文",
                                checked = showTranslation,
                                onCheckedChange = { onToggleTranslation(it); close() },
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { close(); onOpenSettings() }) {
                                Text("模型设置", fontSize = DRAWER_LABEL_SIZE)
                            }
                            TextButton(onClick = { close(); onClearCache() }) {
                                Text("清空本篇解析缓存", fontSize = DRAWER_LABEL_SIZE)
                            }
                            Text(
                                text = "删掉这一篇已解析的句子和词义",
                                modifier = Modifier.padding(start = 12.dp),
                                fontSize = DRAWER_HINT_SIZE,
                                color = paperColors().muted,
                            )
                        }
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                content({ scope.launch { drawerState.open() } })
            }
        }
    }
}

/**
 * 抽屉里的一行开关：左侧文案、右侧轨道，整行可点。
 *
 * 为什么不用 Material3 的 [androidx.compose.material3.Switch]：它的轨道按整行表单设计，
 * 比这里需要的宽一档，塞进 20dp 侧边距的抽屉里会把行高和文案基线一起抬高。这里沿用 30x18dp
 * 轨道 + 14dp 圆点（原顶栏 FuriganaSwitch 的形态）。
 *
 * 语义上它是一个开关（role = Role.Switch）：TalkBack 念「注音，开关，已关闭」，
 * 所以文案只做视觉标签，不再单独挂 contentDescription。
 */
@Composable
private fun ReadToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val g = geometry()
    val paper = paperColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(g.smallRadius))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = DRAWER_LABEL_SIZE,
            color = if (checked) paper.textStrong else paper.muted,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(g.pillRadius))
                .background(if (checked) MaterialTheme.colorScheme.primary else paper.lineControl),
            // 圆点靠容器对齐切换两端，省掉动画依赖：开关要的是状态可辨，不是缓动
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
