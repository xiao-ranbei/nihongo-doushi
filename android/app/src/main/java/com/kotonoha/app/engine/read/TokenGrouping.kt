package com.kotonoha.app.engine.read

/**
 * 逐词禁则分组（纯逻辑模块：禁止 import Android/androidx，禁 IO、禁随机）。
 *
 * 为什么需要它：UI 用 FlowRow 按组换行而不量宽度（spec A7）。如果标点自成一组就会被推到行首，
 * 开括号自成一组就会留在行尾。分组把「不许落行首」的字符粘到前一组，
 * 把「不许留行尾」的开括号和后一个词绑在一起，从而在不测量宽度的前提下得到可读断行。
 *
 * 单遍扫描，不做回溯：即使整句都是記号也只有一次遍历，不存在死循环的可能。
 */
object TokenGrouping {

    /** 行首禁则：标点、小假名（拗音/促音）、长音符与重复符号——独立成行时读者会当成新短语 */
    private const val LINE_HEAD_FORBIDDEN =
        "、。，．,.!?！？:：;；)]}｝」』】〉》〕ー々ゝゞヽヾぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ"

    /** 行尾禁则：开括号。让它们留在行尾等于把读者引到下一行去找内容 */
    private const val LINE_TAIL_FORBIDDEN = "([{｛「『【〈《〔（［"

    /** 模型用来表示原文换行的特殊词性：必须独立成组，UI 才能渲染成换行而不是一个怪词 */
    private const val LINE_BREAK_POS = "改行"

    fun group(tokens: List<AnalyzeToken>): List<List<AnalyzeToken>> {
        val groups = ArrayList<MutableList<AnalyzeToken>>()
        for (token in tokens) {
            if (token.pos == LINE_BREAK_POS) {
                groups.add(mutableListOf(token))
                continue
            }
            val previous = groups.lastOrNull()
            if (previous == null || previous.last().pos == LINE_BREAK_POS || !joinsPrevious(previous, token)) {
                groups.add(mutableListOf(token))
            } else {
                previous.add(token)
            }
        }
        return groups
    }

    private fun joinsPrevious(group: List<AnalyzeToken>, token: AnalyzeToken): Boolean {
        if (token.word.isEmpty()) return true
        if (token.word[0] in LINE_HEAD_FORBIDDEN) return true
        val tail = group.last().word
        return tail.isNotEmpty() && tail[tail.length - 1] in LINE_TAIL_FORBIDDEN
    }
}
