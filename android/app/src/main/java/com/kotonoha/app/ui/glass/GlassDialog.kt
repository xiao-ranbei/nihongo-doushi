package com.kotonoha.app.ui.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.glassColors

/**
 * 独立窗口里的玻璃弹层（IK-23 的收口）。
 *
 * M3 的 `ModalBottomSheet` 与 `AlertDialog` 各自跑在一个 Dialog 窗口上，采样不到主窗口
 * [GlassLayer] 录的 RenderNode——玻璃会退化成一层平涂。这里的做法分两步：
 *
 * 1. 把 M3 容器的底`containerColor`设成透明、`tonalElevation` 归零，容器只当形状与尺寸的外壳；
 * 2. 用 [glassPanel] 把玻璃画在容器自己的 Surface 上，采样源换成
 *    [rememberScreenTextureBackdrop] 造的「按窗口坐标重画的屏幕底」。
 *
 * 玻璃画在 Surface 上而不是 Surface 里面的 Box，是为了四角与尺寸严丝合缝：
 * 在内层铺一层会小一圈，边缘漏出 1dp 的窗口底色。
 */

/**
 * 玻璃版底部抽屉：四角同 24、无抓手、玻璃底。
 *
 * 与直接调 `ModalBottomSheet` 的差别只有材质；手势、遮罩、返回键、隐藏动画仍由 M3 负责。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = rememberScreenTextureBackdrop()
    val colors = glassColors()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = GlassShape.corner,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = null,
        // 内边距自己给：M3 的 windowInsets 会加在 Surface 上，玻璃就只能画在更小的内容框里，
        // 底部露出导航栏高度的透明缝
        contentWindowInsets = { WindowInsets(0) },
    ) {
        ProvideGlassBackdrop(backdrop) {
            // 玻璃必须画在 content 这一层而不是 ModalBottomSheet 的 modifier 上：
            // 那个 modifier 落在 Dialog 的全屏根 Box 上（实测玻璃铺满整屏、抽屉内容反被它盖住）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .glassPanel(backdrop = backdrop, elevation = GlassTokens.sheetElevation)
                    .navigationBarsPadding(),
            ) {
                content()
            }
        }
    }
}

/**
 * 玻璃版对话框：四角 24，材质与抽屉一致。
 * 参数与 [AlertDialog] 一一对应，只为材质而来，布局与按钮语义仍走 M3。
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val backdrop = rememberScreenTextureBackdrop()
    ProvideGlassBackdrop(backdrop) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier.glassPanel(backdrop = backdrop, elevation = GlassTokens.sheetElevation),
            dismissButton = dismissButton,
            title = title,
            text = text,
            shape = GlassShape.corner,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        )
    }
}
