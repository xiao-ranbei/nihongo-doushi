package com.nihongodoushi.app.engine.suggest

/**
 * 主页「知识点建议」规则引擎（纯函数）。
 *
 * 规则与优先级（R1 > R2 > R3 > R4 > R5，R6 仅在其余全不命中时兜底），最多返回 3 条：
 * - R1 due-mistakes：到期错题提醒
 * - R2 today-empty：今日未练引导
 * - R3 weakest-verb/adjective/particle：补最弱模块（掌握度 1..59 且为最低；平局按 动词<形容词<助词）
 * - R4 streak：连续学习鼓励
 * - R5 new-user：新用户引导
 * - R6 fallback：以上全不命中时的状态肯定
 *
 * 纯逻辑模块：禁止 import Android/androidx 类，全部确定性、可复现。
 */

/** 学习状态快照（口径由调用方构造，与主页/我的页统计一致） */
data class StudySnapshot(
    val todayAnswers: Int,
    val dueMistakes: Int,
    val verbPercent: Int,
    val adjectivePercent: Int,
    val particlePercent: Int,
    val currentStreak: Int,
    val totalAnswers: Int,
    val verbTotal: Int,
)

/** 建议动作类型 */
enum class SuggestionAction { VERB, ADJECTIVE, PARTICLE, MISTAKES, STUDY_TAB }

/** 单条建议：id 稳定（UI 凭 id 定图标/色调），action=null 表示纯鼓励行不可点 */
data class Suggestion(
    val id: String,
    val text: String,
    val subText: String?,
    val action: SuggestionAction?,
)

object SuggestionEngine {

    /** 最多同时展示的建议条数 */
    private const val MAX_SUGGESTIONS = 3

    fun suggest(s: StudySnapshot): List<Suggestion> {
        val results = mutableListOf<Suggestion>()

        // R1 到期错题
        if (s.dueMistakes > 0) {
            results += Suggestion(
                id = "due-mistakes",
                text = "有 ${s.dueMistakes} 道错题今天到期，复习一遍更牢",
                subText = "进入错题本",
                action = SuggestionAction.MISTAKES,
            )
        }

        // R2 今日未学
        if (s.todayAnswers == 0) {
            results += Suggestion(
                id = "today-empty",
                text = "今天还没练习，去学习页开始一组吧",
                subText = "切换到学习页",
                action = SuggestionAction.STUDY_TAB,
            )
        }

        // R3 补最弱模块：三个 percent 取最小；平局按 动词<形容词<助词 固定顺序取先者
        val candidates = listOf(
            "verb" to s.verbPercent,
            "adjective" to s.adjectivePercent,
            "particle" to s.particlePercent,
        )
        val minPercent = candidates.minOf { it.second }
        if (minPercent in 1..59) {
            val weakestId = candidates.first { it.second == minPercent }.first
            results += weakestSuggestion(weakestId, minPercent)
        }

        // R4 连续鼓励
        if (s.currentStreak >= 3) {
            results += Suggestion(
                id = "streak",
                text = "已连续学习 ${s.currentStreak} 天，保持节奏",
                subText = null,
                action = null,
            )
        }

        // R5 新用户引导
        if (s.totalAnswers == 0) {
            results += Suggestion(
                id = "new-user",
                text = "从动词练习开始，先认识 ${s.verbTotal} 个基础词条",
                subText = "进入动词练习",
                action = SuggestionAction.VERB,
            )
        }

        // R6 兜底：以上全不命中
        if (results.isEmpty()) {
            results += Suggestion(
                id = "fallback",
                text = "今天已练 ${s.todayAnswers} 题，状态不错",
                subText = null,
                action = null,
            )
        }

        return results.take(MAX_SUGGESTIONS)
    }

    private fun weakestSuggestion(moduleId: String, percent: Int): Suggestion = when (moduleId) {
        "verb" -> Suggestion(
            id = "weakest-verb",
            text = "动词掌握度最低（$percent%），优先补这里",
            subText = "进入动词练习",
            action = SuggestionAction.VERB,
        )
        "adjective" -> Suggestion(
            id = "weakest-adjective",
            text = "形容词掌握度最低（$percent%），优先补这里",
            subText = "进入形容词练习",
            action = SuggestionAction.ADJECTIVE,
        )
        else -> Suggestion(
            id = "weakest-particle",
            text = "助词掌握度最低（$percent%），优先补这里",
            subText = "进入助词学习",
            action = SuggestionAction.PARTICLE,
        )
    }
}
