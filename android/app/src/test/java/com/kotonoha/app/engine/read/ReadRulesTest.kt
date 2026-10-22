package com.kotonoha.app.engine.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 断言口径：normalizeEndpoint 用 spec §6.2 的四个真实例子逐条钉住；
 * cacheKey 用已知的 SHA-256 向量（外部算好写死）证明实现口径，而不是自己算一遍自证；
 * trim 系列同时验证「保留的是最新的」与「顺序稳定」；validateConfig 按错误条数断言。
 */
class ReadRulesTest {

    @Test
    fun `归一化只填域名时补 v1 路径`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ReadRules.normalizeEndpoint("https://api.deepseek.com"),
        )
    }

    @Test
    fun `归一化已带版本前缀时只补 chat completions`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ReadRules.normalizeEndpoint("https://api.deepseek.com/v1"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            ReadRules.normalizeEndpoint("https://open.bigmodel.cn/api/paas/v4"),
        )
    }

    @Test
    fun `归一化已完整时原样返回`() {
        val full = "https://api.deepseek.com/v1/chat/completions"
        assertEquals(full, ReadRules.normalizeEndpoint(full))
        assertEquals(full, ReadRules.normalizeEndpoint(full + "/"))
    }

    @Test
    fun `归一化容忍尾斜杠与大小写`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ReadRules.normalizeEndpoint("https://api.deepseek.com/"),
        )
        assertEquals(
            "https://api.deepseek.com/Chat/Completions",
            ReadRules.normalizeEndpoint("https://api.deepseek.com/Chat/Completions"),
        )
    }

    @Test
    fun `缓存键为六十四位小写十六进制且符合已知向量`() {
        val key = ReadRules.cacheKey("こんにちは。", "deepseek-chat")
        assertEquals(64, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
        assertEquals("dd3e93f6e5462fd6c5c347f6ec8cc76b321117c3c3beb89a60f1e653fb99a254", key)
    }

    @Test
    fun `缓存键用 NUL 分隔避免拼接歧义`() {
        assertNotEquals(ReadRules.cacheKey("ab", "c"), ReadRules.cacheKey("a", "bc"))
        assertNotEquals(ReadRules.cacheKey("同じ句", "model-a"), ReadRules.cacheKey("同じ句", "model-b"))
        assertEquals(ReadRules.cacheKey("同じ句", "m"), ReadRules.cacheKey("同じ句", "m"))
    }

    @Test
    fun `段落缓存键与其中任一句的缓存键都不同`() {
        val paragraph = "今日は晴れです。明日は雨です。"
        val sentences = SentenceSplitter.split(paragraph)
        assertEquals(2, sentences.size)
        val paragraphKey = ReadRules.cacheKey(paragraph, "m")
        // 一段一请求后缓存键自然变成整段键：同 id 冲突会让两个请求单元互相覆盖
        sentences.forEach { assertNotEquals(paragraphKey, ReadRules.cacheKey(it.text, "m")) }
        // 同段同模型必须稳定，否则每次进页都当缓存未命中重新花钱
        assertEquals(paragraphKey, ReadRules.cacheKey(paragraph, "m"))
    }

    @Test
    fun `缓存裁剪保留最新的若干条`() {
        val entries = (1..10).map { index ->
            AnalysisCacheEntry(
                key = "k" + index,
                sentence = "s" + index,
                model = "m",
                result = SentenceAnalysis(sentence = "s" + index),
                createdAt = "2024-01-" + index.toString().padStart(2, '0') + "T00:00:00Z",
            )
        }
        val trimmed = ReadRules.trimCache(entries, max = 3)
        assertEquals(3, trimmed.size)
        assertEquals(listOf("k10", "k9", "k8"), trimmed.map { it.key })
    }

    @Test
    fun `缓存条目未超上限时原样返回`() {
        val entries = listOf(
            AnalysisCacheEntry("k1", "s", "m", SentenceAnalysis(sentence = "s"), createdAt = "2024-01-01T00:00:00Z"),
        )
        assertEquals(entries, ReadRules.trimCache(entries, max = 500))
        assertTrue(ReadRules.trimCache(entries, max = 0).isEmpty())
    }

    @Test
    fun `文章裁剪按更新时间保留最新`() {
        val items = (1..5).map { index ->
            ReadArticle(
                id = "a" + index,
                title = "t" + index,
                text = "body",
                createdAt = "2024-01-0" + index + "T00:00:00Z",
                updatedAt = "2024-02-0" + index + "T00:00:00Z",
            )
        }
        val trimmed = ReadRules.trimArticles(items, max = 2)
        assertEquals(listOf("a5", "a4"), trimmed.map { it.id })
    }

    @Test
    fun `默认上限为缓存五百条`() {
        val entries = (1..600).map { index ->
            AnalysisCacheEntry(
                key = "k" + index,
                sentence = "s",
                model = "m",
                result = SentenceAnalysis(sentence = "s"),
                createdAt = "2024-01-01T00:00:00Z#" + index.toString().padStart(4, '0'),
            )
        }
        val trimmed = ReadRules.trimCache(entries)
        assertEquals(500, trimmed.size)
        assertEquals("k600", trimmed.first().key)
        assertEquals("k101", trimmed.last().key)
    }

    @Test
    fun `默认上限为文章五十篇`() {
        val items = (1..60).map { index ->
            ReadArticle(
                id = "a" + index,
                title = "t",
                text = "body",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-02-01T00:00:00Z#" + index.toString().padStart(4, '0'),
            )
        }
        val trimmed = ReadRules.trimArticles(items)
        assertEquals(50, trimmed.size)
        assertEquals("a60", trimmed.first().id)
    }

    @Test
    fun `配置合法时返回空错误列表`() {
        val errors = ReadRules.validateConfig(
            ModelConfig(baseUrl = "https://api.deepseek.com", model = "deepseek-chat", apiKey = "sk-x", temperature = 0.2),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `配置三项为空时各报一条中文错误`() {
        // temperature 默认 0.2 合法，所以只有地址/模型/密钥三条
        val errors = ReadRules.validateConfig(ModelConfig())
        assertEquals(3, errors.size)
        assertTrue(errors.all { it.isNotBlank() })
    }

    @Test
    fun `缺少协议头与温度越界各自报错`() {
        val withoutScheme = ReadRules.validateConfig(
            ModelConfig(baseUrl = "api.deepseek.com", model = "m", apiKey = "k", temperature = 0.2),
        )
        assertEquals(1, withoutScheme.size)
        assertTrue(withoutScheme[0].contains("http"))

        val tooHot = ReadRules.validateConfig(
            ModelConfig(baseUrl = "https://x.y", model = "m", apiKey = "k", temperature = 1.5),
        )
        assertEquals(1, tooHot.size)
        assertFalse(tooHot[0].isBlank())

        val negative = ReadRules.validateConfig(
            ModelConfig(baseUrl = "https://x.y", model = "m", apiKey = "k", temperature = -0.1),
        )
        assertEquals(1, negative.size)
        assertEquals(1, tooHot.size)
    }
}
