package com.kotonoha.app.engine.read

import java.security.MessageDigest

/**
 * 配置、缓存与容量规则的纯函数集合（纯逻辑模块：禁止 import Android/androidx，禁 IO、禁随机）。
 *
 * 为什么集中在这里：这几条规则同时被网络层（拼接 URL）、存储层（缓存键、裁剪）和设置页（校验）用到，
 * 放在同一个无状态 object 里，三处就不会各自演化出不一致的口径。
 * 唯一的外部依赖是 [MessageDigest]——它属于 java.security，不涉及 IO。
 */
object ReadRules {

    /** 上限来自 spec §8：缓存 500 条、文章 50 篇 */
    const val MAX_CACHE_ENTRIES = 500
    const val MAX_ARTICLES = 50

    private const val CHAT_COMPLETIONS = "/chat/completions"
    private const val V1_CHAT_COMPLETIONS = "/v1/chat/completions"

    /**
     * 把用户填的 baseUrl 归一成可以直接 POST 的完整 endpoint（spec §6.2）。
     *
     * 规则：已经以 /chat/completions 结尾就原样返回（大小写与尾斜杠容错）；
     * 否则看去掉 scheme:// 之后的路径——**路径为空**才补 /v1/chat/completions，
     * 因为各家的版本前缀各不相同（DeepSeek 要 /v1，智谱要 /api/paas/v4），
     * 空路径说明用户只填了域名，这时 /v1 是覆盖面最广的默认；路径非空则只补 /chat/completions，
     * 前缀交给用户自己写，免得拼出 /v1/v4 这种废路径。
     */
    fun normalizeEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return ""
        val withoutTrailingSlash = trimmed.trimEnd('/')
        if (withoutTrailingSlash.isEmpty()) return trimmed
        if (withoutTrailingSlash.endsWith(CHAT_COMPLETIONS, ignoreCase = true)) {
            // 只把尾斜杠去掉，大小写保持用户原样：部分网关对路径大小写敏感
            return withoutTrailingSlash
        }
        val schemeEnd = withoutTrailingSlash.indexOf("://")
        val pathStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
        val slash = withoutTrailingSlash.indexOf('/', pathStart)
        val path = if (slash >= 0) withoutTrailingSlash.substring(slash).trim('/') else ""
        return if (path.isEmpty()) {
            withoutTrailingSlash + V1_CHAT_COMPLETIONS
        } else {
            withoutTrailingSlash + CHAT_COMPLETIONS
        }
    }

    /**
     * 缓存键 = sha256(sentence + NUL + model) 的小写十六进制。
     *
     * 中间放一个 NUL 分隔符：否则「ab」+「c」与「a」+「bc」会撞成同一个键，
     * 而句子和模型名都可能包含任意可见字符，只有 NUL 不会出现在两者之中。
     * 键里必须含 model——换模型等于换翻译口径，不能复用旧结果。
     */
    fun cacheKey(sentence: String, model: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((sentence + "\u0000" + model).toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX = "0123456789abcdef"

    /**
     * 只保留 createdAt 最新的 max 条。
     *
     * createdAt 是 ISO-8601 字符串，字典序即时间序，所以无需日期解析（也就无需 java.time）。
     * 排序用 sortedByDescending 的稳定版本：时间戳相同时保持原顺序，保证同一输入永远得到同一输出。
     */
    fun trimCache(entries: List<AnalysisCacheEntry>, max: Int = MAX_CACHE_ENTRIES): List<AnalysisCacheEntry> {
        if (max <= 0) return emptyList()
        if (entries.size <= max) return entries
        return entries
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<AnalysisCacheEntry>> { it.value.createdAt }.thenBy { it.index })
            .take(max)
            .map { it.value }
    }

    /** 文章按 updatedAt 保留最新的 max 篇，口径同 [trimCache] */
    fun trimArticles(items: List<ReadArticle>, max: Int = MAX_ARTICLES): List<ReadArticle> {
        if (max <= 0) return emptyList()
        if (items.size <= max) return items
        return items
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<ReadArticle>> { it.value.updatedAt }.thenBy { it.index })
            .take(max)
            .map { it.value }
    }

    /**
     * 设置页校验：返回中文错误列表，空列表表示通过（spec §6.5）。
     *
     * 一次返回全部问题而不是首个错误：用户改设置时希望一遍看到所有要改的地方。
     * temperature 用闭区间判断，NaN 也算不合法——它会让请求体序列化失败，属于必须挡在发送前的输入。
     */
    fun validateConfig(cfg: ModelConfig): List<String> {
        val errors = ArrayList<String>(4)
        if (cfg.baseUrl.isBlank()) {
            errors.add("请填写 API 地址")
        } else {
            val lower = cfg.baseUrl.trim().lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                errors.add("API 地址必须以 http:// 或 https:// 开头")
            }
        }
        if (cfg.model.isBlank()) {
            errors.add("请填写模型名称")
        }
        if (cfg.apiKey.isBlank()) {
            errors.add("请填写 API Key")
        }
        if (cfg.temperature.isNaN() || cfg.temperature < 0.0 || cfg.temperature > 1.0) {
            errors.add("温度必须在 0.0 到 1.0 之间")
        }
        return errors
    }
}
