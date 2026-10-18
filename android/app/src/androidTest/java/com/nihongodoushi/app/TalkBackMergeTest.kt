package com.nihongodoushi.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IK-18 读屏语义回归（仪器化测试，合并语义树口径）。
 *
 * 背景（字节码 + 变异实测，勿再被 uiautomator dump 误导）：
 * clickable / selectable / combinedClickable 自带合并子语义
 * （AbstractClickableNode.getShouldMergeDescendantSemantics() 返回 true，
 * SelectableNode 继承 ClickableNode）；Surface(onClick)、NavigationBarItem
 * 内部都走 clickable/selectable——卡片、选项、底栏本就是单一焦点站，
 * 不需要手工加 mergeDescendants。
 *
 * uiautomator dump 只能拿到未合并语义树：连字节码证实自带合并的
 * Material3 IconButton，在 dump 里也是「空壳可点节点 + 独立 desc 节点」，
 * 所以 dump 验证不了合并，必须直接用 Compose 测试 API 断言默认（合并）树。
 *
 * 本类是防回归护栏：若有人把卡片改成不可点容器、或显式关掉合并，
 * 下面用例会红。
 *
 * 判定口径：
 * - 合并后，卡片/标签的文本进入可点节点本身 → 该文本节点带点击动作；
 * - 未合并时，文本是卡内独立节点、不带点击动作（反向对照，防空断言）；
 * - 合并型父卡不会吞掉嵌套的可点按钮（SemanticsNode.mergeConfig 跳过
 *   合并型子节点），卡内「练」按钮仍是独立可点节点；
 * - 选中态语义（本轮真修复）：picker 行补了 selected 语义，
 *   当前词条行（带「当前」角标）在合并树上可听出「已选择」。
 */
@RunWith(AndroidJUnit4::class)
class TalkBackMergeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomTabLabelMergesIntoClickableNode() {
        // 底栏标签：合并后「学习」文本就在标签可点节点上
        compose.onNodeWithText("学习").assert(hasClickAction())
        compose.onNodeWithText("学习").performClick()
        // 反向对照：未合并树里同名节点只是卡内文本，没有点击动作
        compose.onNodeWithText("动词练习", useUnmergedTree = true).assert(hasNoClickAction())
    }

    @Test
    fun entryCardMergesLabelIntoClickableNode() {
        compose.onNodeWithText("学习").performClick()
        // 入口卡（动词/形容词/助词/错题本）合并后整卡一个焦点站：标题文本可点
        compose.onNodeWithText("动词练习").assert(hasClickAction())
        compose.onNodeWithText("助词学习").assert(hasClickAction())
    }

    @Test
    fun drillButtonSurvivesInsideMergedCard() {
        compose.onNodeWithText("学习").performClick()
        compose.onNodeWithText("动词练习").performClick()
        // 动词首页主题卡全部合并；卡内「练」按钮必须仍是独立可点节点
        // （父卡若吞掉它，这里取不到任何节点，onFirst 直接抛错）
        compose.onAllNodesWithText("练").onFirst().assert(hasClickAction())
    }

    @Test
    fun pickerRowExposesSelectedState() {
        compose.onNodeWithText("学习").performClick()
        compose.onNodeWithText("动词练习").performClick()
        compose.onNodeWithText("活用速查：按词条查全部变形").performClick()
        // 「换词条」文本在可点卡内（合并后点它即开抽屉）
        compose.onNodeWithText("换词条").performClick()
        // 抽屉里当前词条行带「当前」角标；该行必须有 selected 语义，
        // 否则读屏用户只能靠颜色分辨选中项（修复前 selected 缺失）
        compose.onNodeWithText("当前").assert(isSelected())
        // 该行同时仍是一个可点焦点站（clickable 自带合并 + selected）
        compose.onNodeWithText("当前").assert(hasClickAction())
    }
}
