package com.nihongodoushi.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEntry
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEntry
import com.nihongodoushi.app.storage.ActivityAggregation
import com.nihongodoushi.app.storage.DayActivity
import com.nihongodoushi.app.storage.LocalStore
import com.nihongodoushi.app.storage.NicknameCheck
import com.nihongodoushi.app.storage.ProfileRules
import com.nihongodoushi.app.storage.UserProfile
import java.time.Instant
import java.time.LocalDate

/**
 * 全局共享状态（IK-06 按领域拆分，本文件只保留状态声明与会话核心；
 * 领域方法在顶层扩展文件里，调用语法不变）：
 * - AppStateMistakes.kt  错题：动词/形容词共享错题本 + 到期判定 + 权重
 * - AppStateStats.kt     统计：模块统计 + 每日打卡 + 学习行为采集（history-v1）
 * - AppStateParticle.kt  助词：v2 错题复习排期（D-022）+ 助词统计
 * - AppStateMastery.kt   掌握度：三层改造组合掌握度 + 词库覆盖进度
 *
 * 状态属性 setter 收窄为 internal：仅领域扩展文件可写，UI 层照旧只读。
 * 行为逐条对齐 web/app.js（recordMistake / recordSuccessfulReview / updateDailyStreak）。
 */
class AppState(internal val store: LocalStore) {

    var mistakes by mutableStateOf(store.loadMistakes())
        internal set
    var verbStats by mutableStateOf(store.loadVerbStats())
        internal set
    var adjectiveStats by mutableStateOf(store.loadAdjectiveStats())
        internal set

    /** 助词错题：带复习字段的记录（v2，D-022；v1 纯 ID 数组由 LocalStore 迁移） */
    var particleMistakes by mutableStateOf(
        store.loadParticleMistakes().filter { ParticleData.QUESTION_BY_ID.containsKey(it.questionId) },
    )
        internal set

    var particleStats by mutableStateOf(store.loadParticleStats())
        internal set
    var daily by mutableStateOf(store.loadDaily())
        internal set

    /** 最近若干天学习行为（history-v1）：主页周历条与「我的」页统计的数据源 */
    var history by mutableStateOf(store.loadHistory())
        internal set

    /** 已练词条集合（practice-v1）：词库覆盖进度 */
    var progress by mutableStateOf(store.loadProgress())
        internal set

    /** 读取到脏数据时的提示标记（UI 层消费一次即可） */
    var loadInvalid by mutableStateOf(store.lastLoadInvalid)
        private set

    /** 会话内已提交作答数（跨模块共享，对应 web 端 sessionAnswerCount） */
    var sessionAnswerCount: Int = 0
        internal set

    /** 用户资料（profile-v1）：当前仅昵称 */
    var profile by mutableStateOf(store.loadProfile())
        private set

    /** 书签收藏（bookmarks-v1）：本期仅存储骨架与计数，无写入入口（阅读模块上线后启用） */
    var bookmarks by mutableStateOf(store.loadBookmarks())
        private set

    /**
     * 昵称编辑：校验通过才写入并落盘，返回 true；非法输入原值不变、返回 false。
     */
    fun updateNickname(raw: String): Boolean {
        val checked = ProfileRules.check(raw)
        if (checked !is NicknameCheck.Ok) {
            return false
        }
        profile = UserProfile(nickname = checked.value)
        store.saveProfile(profile)
        return true
    }

    init {
        // history-v1 上线时的回填：历史里今天的答题数少于 daily（老键）时取较大值补齐，
        // 保证「主页周历条 / 我的页答题数 / 今日打卡」三者口径一致。
        // 幂等且只升不降，不会覆盖真实历史。
        val todayKey = LocalDate.now().toString()
        if (daily.lastDate == todayKey) {
            val existing = history.dayOf(todayKey)
            val answersInHistory = existing?.answers ?: 0
            val answersInDaily = daily.verbAnswers + daily.adjectiveAnswers + daily.particleAnswers
            if (answersInHistory < answersInDaily) {
                val merged = (existing ?: DayActivity(date = todayKey)).copy(
                    verbAnswers = maxOf(existing?.verbAnswers ?: 0, daily.verbAnswers),
                    adjectiveAnswers = maxOf(existing?.adjectiveAnswers ?: 0, daily.adjectiveAnswers),
                    particleAnswers = maxOf(existing?.particleAnswers ?: 0, daily.particleAnswers),
                )
                val seeded = ActivityAggregation.upsert(history, merged)
                history = seeded
                store.saveHistory(seeded)
            }
        }
    }

    fun onAnswerSubmitted() {
        sessionAnswerCount += 1
    }

    companion object {
        fun verbMistakeKey(verbId: String, voiceId: String, inflectionId: String): String =
            listOf(verbId, voiceId, inflectionId).joinToString("::")

        fun adjMistakeKey(adjectiveId: String, formId: String): String =
            "adj::" + listOf(adjectiveId, formId).joinToString("::")

        /** 对应 web 端 addDays：以当前时刻加 N 天的 ISO 串存储 */
        fun addDays(days: Int): String =
            Instant.now().plus(java.time.Duration.ofDays(days.toLong())).toString()

        val verbById: Map<String, VerbEntry> = VerbData.VERBS.associateBy { it.id }
        val adjectiveById: Map<String, AdjectiveEntry> = AdjectiveData.ADJECTIVES.associateBy { it.id }
    }
}
