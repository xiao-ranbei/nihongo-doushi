package com.kotonoha.app.net

import com.kotonoha.app.engine.read.ReadPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 锁住 [WordDetailDto] 的键名与 [ReadPrompt.WORD_DETAIL_SYSTEM] 的字段清单一致。
 *
 * 为什么值得单独一条：DTO 曾经照抄参考仓库的 chineseTranslation / explanation / exampleTranslation，
 * 而提示词要的是 meaning / usage / exampleMeaning；`ignoreUnknownKeys = true` 把对不上的键静默丢弃，
 * 字段降级成空串，UI 上「意思」「用法」「例句译文」三行直接不出现，编译和单测全绿也发现不了。
 * 断言口径是「按提示词里的 JSON 形状喂进去，六个字段必须各归各位」，而不是比对字符串常量本身。
 */
class WordDetailDtoContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 提示词 §WORD_DETAIL_SYSTEM 里给定的 JSON 形状，值刻意两两不同，字段串味必暴露 */
    private val promptShaped = """
        {"meaning":"释义","dictionaryForm":"辞书形","usage":"本句用法","conjugation":"原形 → 文中形式","example":"例句","exampleMeaning":"例句译文"}
    """.trimIndent()

    @Test
    fun `提示词要求的六个键都能被 DTO 接住`() {
        val dto = json.decodeFromString<WordDetailDto>(promptShaped)
        assertEquals("释义", dto.meaning)
        assertEquals("辞书形", dto.dictionaryForm)
        assertEquals("本句用法", dto.usage)
        assertEquals("原形 → 文中形式", dto.conjugation)
        assertEquals("例句", dto.example)
        assertEquals("例句译文", dto.exampleMeaning)
    }

    @Test
    fun `DTO 的每个键都出现在提示词的字段清单里`() {
        for (key in listOf("meaning", "dictionaryForm", "usage", "conjugation", "example", "exampleMeaning")) {
            assertTrue(ReadPrompt.WORD_DETAIL_SYSTEM.contains("\"$key\""), "提示词缺少键：$key")
        }
    }

    @Test
    fun `参考仓库的旧键名不再被解析成任何字段`() {
        val legacy = """
            {"chineseTranslation":"释义","explanation":"本句用法","exampleTranslation":"例句译文"}
        """.trimIndent()
        val dto = json.decodeFromString<WordDetailDto>(legacy)
        assertEquals("", dto.meaning)
        assertEquals("", dto.usage)
        assertEquals("", dto.exampleMeaning)
    }

    @Test
    fun `缺字段时降级空串不抛异常`() {
        val dto = json.decodeFromString<WordDetailDto>("{}")
        assertEquals("", dto.meaning)
        assertEquals("", dto.exampleMeaning)
    }

    @Test
    fun `模型多回传 pos 与 furigana 不影响解析`() {
        val obj = json.parseToJsonElement(promptShaped).let { it as JsonObject }
        val withExtra = JsonObject(obj + mapOf("pos" to json.parseToJsonElement("\"名詞\"").jsonPrimitive))
        val dto = json.decodeFromString<WordDetailDto>(withExtra.toString())
        assertEquals("释义", dto.meaning)
    }
}
