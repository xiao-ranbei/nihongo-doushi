package com.kotonoha.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 方案 B 的形状与间距令牌（Golden v2 冻结值，见 docs/android-liquid-glass-plan-b.md §0.2）。
 *
 * 全局只有一个圆角：24dp。卡片 / 按钮 / 输入框 / 弹层 / 图片 / 底栏岛 / 吸底条全部取它，
 * 不允许再出现第二个半径常量；短边不足 48dp 的元素由形状自身裁切，这属于几何事实而非第二套值。
 */
object GlassTokens {

    /** 全局唯一圆角。 */
    val radius = 24.dp

    /** 悬浮岛离屏幕左 / 右 / 底的距离。 */
    val islandInset = 16.dp

    /** 覆盖浮法下页面底部需要额外留出的高度，保证滚到底时最后一项不被岛遮住。 */
    val islandContentInset = 112.dp

    /** 弹层四角同为 r，三边离屏 12dp（浮起而非贴底）。 */
    val sheetInset = 12.dp

    /** 页边距 / 卡内边距 / 元素间隙。 */
    val pagePadding = 22.dp
    val cardPadding = 18.dp
    val gap = 16.dp
    val sectionGap = 16.dp

    /** 玻璃模糊半径（Golden：blur(16px) saturate(1.6)）。 */
    val blur = 16.dp

    /** 玻璃边与内高光的厚度（Golden `--border-w:1px`）。 */
    val hairline = 1.dp

    /** 三档投影：卡片 0 10 30 / 悬浮岛 0 12 30 / 弹层 0 -14 40，取等效 elevation。 */
    val cardElevation = 4.dp
    val islandElevation = 10.dp
    val sheetElevation = 24.dp

    /** lens 折射：带宽与偏移量，受 shape 最小圆角 24dp 约束，实机校准见 Q-B4。 */
    val refractionHeight = 18.dp
    val refractionAmount = 24.dp

    /** 底栏内部：岛的内边距、tab 高度、图标槽位与图标本体。 */
    val islandPaddingH = 10.dp
    val islandPaddingV = 8.dp
    val tabHeight = 50.dp
    val tabGlyphSlot = 26.dp
    val tabIcon = 20.dp

    /**
     * 屏幕底三团椭圆光斑的尺寸与中心（比例坐标）。
     * 逐值对齐 Golden 的 `--screen-texture`，改动会由 [com.kotonoha.app.ui.theme.GlassContractTest] 拦下。
     */
    val blobWidths = listOf(190.dp, 230.dp, 210.dp)
    val blobHeights = listOf(230.dp, 270.dp, 210.dp)
    val blobCenterX = listOf(0.16f, 0.88f, 0.28f)
    val blobCenterY = listOf(0.14f, 0.40f, 0.88f)

    // ── 液态金属（底栏选中块）────────────────────────────────────────
    // 金属不是另一种材质，而是同一条效果链的更强参数：折射推得更狠、边缘更硬、表面带斜向金属渐变。

    /** 金属块的折射带宽与偏移：比玻璃（18/24）明显更强，图标与身后内容才会被推挤变形。 */
    val metalRefractionHeight = 30.dp
    val metalRefractionAmount = 36.dp

    /** 金属块投影：比普通玻璃厚一档，才像一块实体浮在岛上。 */
    val metalElevation = 12.dp

    /** 金属块三种形态的几何：整格滑块高度、图标胶囊宽高、连接带高度。 */
    val metalSlabHeight = 50.dp
    val metalCapsuleWidth = 56.dp
    val metalCapsuleHeight = 32.dp
    val metalTrailHeight = 6.dp

    /** 切换瞬间宽度收到 92% 再弹回，模拟液体被拉动时的横向收缩。 */
    const val metalSquash = 0.92f

    // ── 一级页布局（焦点卡 + 分区列表）───────────────────────────────

    /** 焦点卡投影：比普通卡片厚一档，作为整页唯一视觉重心。 */
    val focusElevation = 8.dp

    /** 分区容器内的行高下限（行间用 1dp 分隔线，不再各自成卡）。 */
    val groupRowMinHeight = 52.dp
}
