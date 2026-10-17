package com.nihongodoushi.app.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 错题记录：字段名与 Web 端 localStorage 中的 JSON 逐字段对齐。
 * 动词记录无 module 字段（旧版兼容语义），形容词记录 module = "adjective"。
 */
@Serializable
data class MistakeRecord(
    val key: String,
    val module: String? = null,
    val verbId: String? = null,
    val voiceId: String? = null,
    val inflectionId: String? = null,
    val adjectiveId: String? = null,
    val formId: String? = null,
    val count: Int = 0,
    val wrongAnswer: String = "",
    val updatedAt: String = "",
    val correctStreak: Int = 0,
    val intervalDays: Int = 1,
    val dueAt: String = "",
    @SerialName("sessionDueAtAnswerCount")
    val sessionDueAtAnswerCount: Int? = null,
) {
    val isAdjective: Boolean get() = module == "adjective"
    val refId: String? get() = if (isAdjective) adjectiveId else verbId
}

/** 答题统计（动词与形容词同构）：与 Web 端 { correct, incorrect } 对齐 */
@Serializable
data class ModuleStats(
    val correct: Int = 0,
    val incorrect: Int = 0,
)

/**
 * 每日打卡记录：与 Web 端 daily-v1 结构对齐。
 * lastDate 为本地日期字符串 "YYYY-MM-DD"（以设备本地时区为准）。
 */
@Serializable
data class DailyRecord(
    val lastDate: String = "",
    val streak: Int = 0,
    val best: Int = 0,
    val verbAnswers: Int = 0,
    val adjectiveAnswers: Int = 0,
    /** 助词模块当日题数；旧存档无该字段时按 0 兼容 */
    val particleAnswers: Int = 0,
)

/** 单日学习行为（history-v1 的一天） */
@Serializable
data class DayActivity(
    val date: String,
    val verbAnswers: Int = 0,
    val adjectiveAnswers: Int = 0,
    val particleAnswers: Int = 0,
    /** 该日前台学习秒数（进练习屏累计，退到后台暂停） */
    val seconds: Int = 0,
    /** 该日完成任务数（一轮 5 题记 1 个；完成一次错题重练也记 1 个） */
    val tasks: Int = 0,
) {
    val answers: Int get() = verbAnswers + adjectiveAnswers + particleAnswers
}

/** 最近若干天的学习历史（history-v1），按日期升序保存 */
@Serializable
data class ActivityHistory(
    val days: List<DayActivity> = emptyList(),
) {
    fun dayOf(date: String): DayActivity? = days.firstOrNull { it.date == date }

    /** 今日答题总量（动词 + 形容词） */
    fun answersOn(date: String): Int = dayOf(date)?.answers ?: 0
}

/**
 * 助词错题记录（D-022）：字段与 [MistakeRecord] 对齐，只是把动词/形容词的
 * 「词条 + 形式」换成单一 `questionId`。旧版（v1）存的是纯题目 ID 数组，读取时迁移。
 */
@Serializable
data class ParticleMistakeRecord(
    val questionId: String,
    val count: Int = 1,
    val wrongAnswer: String = "",
    val updatedAt: String = "",
    val correctStreak: Int = 0,
    val intervalDays: Int = 1,
    val dueAt: String = "",
    val sessionDueAtAnswerCount: Int? = null,
)

/** 学习进度（practice-v1）：已练词条集合 + 错题毕业数（用于「词库覆盖」与「错题掌握」） */
@Serializable
data class PracticeProgress(
    val verbIds: List<String> = emptyList(),
    val adjectiveIds: List<String> = emptyList(),
    /** 连续答对达到毕业次数、已从错题本移除的错题数 */
    val masteredMistakes: Int = 0,
    /** 助词：「未用提示答对过」的题目 ID 集合（掌握度口径，见 D-020） */
    val particleMastered: List<String> = emptyList(),
    /** 动词：「未用提示答对过」的组合 `voiceId::inflectionId::verbId`（三层改造掌握度口径） */
    val verbMastered: List<String> = emptyList(),
    /** 形容词：「未用提示答对过」的组合 `formId::adjectiveId` */
    val adjMastered: List<String> = emptyList(),
)

/** 用户资料（profile-v1）：当前仅昵称，缺省「日语学习者」 */
@Serializable
data class UserProfile(
    val nickname: String = ProfileRules.DEFAULT_NICKNAME,
)

/** 昵称校验结果：Ok 携带归一化后的合法值，Invalid 携带面向用户的错误文案 */
sealed interface NicknameCheck {
    data class Ok(val value: String) : NicknameCheck
    data class Invalid(val reason: String) : NicknameCheck
}

/** 昵称规则（纯函数）：trim 后 1..12 个字 */
object ProfileRules {
    const val MAX_LENGTH = 12
    const val DEFAULT_NICKNAME = "日语学习者"

    /** UI 层保存前校验：空白拒绝、超长拒绝、合法返回 trim 后的值 */
    fun check(raw: String): NicknameCheck {
        val value = raw.trim()
        return when {
            value.isEmpty() -> NicknameCheck.Invalid("昵称不能为空")
            value.length > MAX_LENGTH -> NicknameCheck.Invalid("昵称长度不能超过 12 个字")
            else -> NicknameCheck.Ok(value)
        }
    }

    /** 存储层防御：trim 后空白或超长一律回落缺省昵称 */
    fun sanitize(raw: String): String {
        val value = raw.trim()
        return if (value.isEmpty() || value.length > MAX_LENGTH) DEFAULT_NICKNAME else value
    }
}

/** 单条书签：type 限定 sentence / word（为阅读模块预留），id 稳定唯一 */
@Serializable
data class Bookmark(
    val id: String,
    val type: String,
    val refId: String,
    val title: String,
    val note: String = "",
    val createdAt: String,
)

/** 书签收藏夹（bookmarks-v1） */
@Serializable
data class BookmarkList(
    val items: List<Bookmark> = emptyList(),
)

/** 书签规则（纯函数）：校验与增删去重 */
object BookmarkRules {
    val ALLOWED_TYPES = setOf("sentence", "word")

    /** 收藏夹容量上限（读取与写入时同样裁剪，防坏数据撑爆存储） */
    const val MAX_ITEMS = 500

    /** 结构合法性：id/refId/title 非空 + type 受限 + createdAt 可解析为 Instant（note 允许为空） */
    fun isValid(b: Bookmark): Boolean =
        b.id.isNotBlank() && b.refId.isNotBlank() && b.title.isNotBlank() &&
            b.type in ALLOWED_TYPES &&
            b.createdAt.isNotBlank() && runCatching { Instant.parse(b.createdAt) }.isSuccess

    /** 新增：同 id 覆盖原位置，否则追加到末尾 */
    fun add(list: BookmarkList, b: Bookmark): BookmarkList =
        if (list.items.any { it.id == b.id }) {
            BookmarkList(list.items.map { if (it.id == b.id) b else it })
        } else {
            BookmarkList(list.items + b)
        }

    /** 移除：id 不存在时原样返回 */
    fun remove(list: BookmarkList, id: String): BookmarkList =
        BookmarkList(list.items.filter { it.id != id })
}
