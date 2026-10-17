package com.nihongodoushi.app.engine.proverb

import kotlin.random.Random

/**
 * 谚语抽取（纯函数，随机源由调用方注入以保证可测）：
 * - [initial]：进入主页时随机取一条的起始索引
 * - [next]：「换一句」时取一个与当前不同的索引（不连续重复）
 */
object ProverbPicker {

    /** 起始索引：size <= 0 时回退 0（数据异常防御），否则等概率取 [0, size) */
    fun initial(random: Random, size: Int): Int {
        if (size <= 0) return 0
        return random.nextInt(size)
    }

    /**
     * 下一个索引：size <= 1 时没有别的可换，返回 current；
     * 否则等概率取一个 != current 的索引（拒绝采样，size=2 时期望约 2 次内收敛）。
     */
    fun next(current: Int, size: Int, random: Random): Int {
        if (size <= 1) return current
        var index = random.nextInt(size)
        while (index == current) {
            index = random.nextInt(size)
        }
        return index
    }
}
