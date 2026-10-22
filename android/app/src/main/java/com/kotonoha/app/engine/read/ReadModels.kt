package com.kotonoha.app.engine.read

import kotlinx.serialization.Serializable

/**
 * 阅读解析模块的数据契约（spec §6.3 / §6.4），纯逻辑模块：禁止 import Android/androidx。
 *
 * 为什么不复用 engine/ 既有的 VerbEntry/AdjectiveEntry id 体系：解析结果来自模型，口径是
 * 日本学校文法的品詞名，不参与统计与错题；两套体系混用会让「词性」这个字段在不同页面指不同东西。
 */

/** 单个切分出的词：word 是原文片段，pos 是日文品詞名，furigana 是平假名读音（无读音时为空串） */
@Serializable
data class AnalyzeToken(
    val word: String,
    val pos: String = "",
    val furigana: String = "",
)

/** analyze 调用的原始响应形状：只认 tokens，其余字段一律忽略 */
@Serializable
data class AnalyzeResponse(
    val tokens: List<AnalyzeToken> = emptyList(),
)

/** 逐词详情（第三次调用）：六个字段由提示词约束，缺省空串便于 UI 直接渲染 */
@Serializable
data class WordDetail(
    val meaning: String = "",
    val dictionaryForm: String = "",
    val usage: String = "",
    val conjugation: String = "",
    val example: String = "",
    val exampleMeaning: String = "",
)

/**
 * 一次点句解析的结果。
 *
 * [aligned] 是 P0 级结论而不是装饰：逐词拼接与原句不一致时读音会挂到错的汉字上，
 * UI 据此隐藏逐词区，只展示译文与 [alignmentNote]。
 */
@Serializable
data class SentenceAnalysis(
    val sentence: String,
    val tokens: List<AnalyzeToken> = emptyList(),
    val translation: String = "",
    val aligned: Boolean = true,
    val alignmentNote: String = "",
)

/** 切句产物：[start]/[end] 是原文中的半开区间偏移（start 含、end 不含），供页面定位与收藏取文 */
data class ReadSentence(
    val text: String,
    val start: Int,
    val end: Int,
    val paragraphIndex: Int,
    /** 该片段由超长句硬切而来，非自然句界 */
    val hardCut: Boolean = false,
)

/**
 * 段落切分产物：一篇文章的一个请求单元（用户 2026-10：几段话连起来一起分析）。
 *
 * 偏移口径与 [ReadSentence] 完全相同（text.substring(start, end) == text），
 * 页面靠这组偏移取原文与收藏取文；段落与 [ReadSentence] 是两个平行的入口，
 * 前者给阅读器解析用，后者仍供标题抽取等按句处理的场景使用。
 */
data class ReadParagraph(
    val text: String,
    val start: Int,
    val end: Int,
    val paragraphIndex: Int,
    /** 该片段由超长段落硬切而来，非自然段界 */
    val hardCut: Boolean = false,
)

/** analyze 响应的抽取结果：失败分支带原始片段，供「查看原始返回」使用且明确不写缓存 */
sealed interface ParseOutcome {
    data class Ok(val analysis: SentenceAnalysis) : ParseOutcome
    data class Failed(val rawSnippet: String, val reason: String) : ParseOutcome
}

/** 模型/服务配置：temp 用 Double 是因为滑块与 JSON 都以它为准，避免 Int 再转换引入舍入差 */
@Serializable
data class ModelConfig(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val temperature: Double = 0.2,
)

/** 已读文章：id 由调用方生成，时间戳用 ISO 字符串（字典序即时间序，便于裁剪） */
@Serializable
data class ReadArticle(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: String,
    val updatedAt: String,
)

/** 文章列表的整体形状：沿用既有一键一 JSON、全量重写的存储方式 */
@Serializable
data class ArticleList(
    val items: List<ReadArticle> = emptyList(),
)

/** 按句缓存的单条记录：key 由 ReadRules.cacheKey 生成，details 是逐词详情的子表 */
@Serializable
data class AnalysisCacheEntry(
    val key: String,
    val sentence: String,
    val model: String,
    val result: SentenceAnalysis,
    val details: Map<String, WordDetail> = emptyMap(),
    val createdAt: String,
)

/** 缓存集合：上限由 ReadRules.trimCache 控制，不做索引（500 条线性扫描足够） */
@Serializable
data class AnalysisCache(
    val entries: List<AnalysisCacheEntry> = emptyList(),
)
