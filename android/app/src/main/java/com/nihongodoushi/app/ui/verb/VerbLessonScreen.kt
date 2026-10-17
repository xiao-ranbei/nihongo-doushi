package com.nihongodoushi.app.ui.verb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.ui.common.ModuleBottomSurface
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 动词讲解页（三层改造 §4.1/§4.2）。
 *
 * 不自造「通用规则文案」：一句话作用来自语态/形式的 `meaning`，变形规则来自
 * 三个真实例词（五段/一段/不规则各一）的 `explain`——与引擎实际行为保持一致。
 */
@Composable
fun VerbLessonScreen(
    theme: VerbTheme,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val paper = paperColors()
    val (title, meaning) = when (theme) {
        is VerbTheme.Form -> {
            val info = VerbData.INFLECTION_BY_ID[theme.formId]
            Pair(
                info?.label ?: theme.formId,
                info?.meaning ?: "",
            )
        }
        is VerbTheme.Voice -> {
            val info = VerbData.VOICE_BY_ID[theme.voiceId]
            Pair(
                info?.label ?: theme.voiceId,
                info?.meaning ?: "",
            )
        }
    }
    val level = (theme as? VerbTheme.Form)?.let {
        VerbData.INFLECTION_BY_ID[it.formId]?.level
    }
    val examples = lessonExampleVerbs(theme)

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            title = title,
            onBack = onBack,
            trailing = {
                Text(
                    text = when (theme) {
                        is VerbTheme.Form -> if (level == "advanced") "进阶" else "基础"
                        is VerbTheme.Voice -> "语态 · 辞书形"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = paper.muted,
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = meaning,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = "三个例词（变形步骤与规则以引擎说明为准）",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
                modifier = Modifier.padding(start = 2.dp),
            )

            examples.forEach { example ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = example.groupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = paper.muted,
                        )
                        Text(
                            text = example.dictionary + "（" + example.reading + "）",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "→ " + example.result.written + "（" + example.result.kana + "）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = example.explain,
                            style = MaterialTheme.typography.labelMedium,
                            color = paper.textStrong,
                        )
                    }
                }
            }
        }

        ModuleBottomSurface {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = when (theme) {
                        is VerbTheme.Form -> "练这个形式（${VerbData.VERBS.size} 个词条）"
                        is VerbTheme.Voice -> "练这个语态（${VerbData.VERBS.size} 个词条）"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private data class VerbLessonExample(
    val groupLabel: String,
    val dictionary: String,
    val reading: String,
    val result: com.nihongodoushi.app.engine.verb.VerbConjugation,
    val explain: String,
)

private fun lessonExampleVerbs(theme: VerbTheme): List<VerbLessonExample> {
    val pairs: List<Triple<com.nihongodoushi.app.engine.verb.VerbEntry, String, String>> = when (theme) {
        is VerbTheme.Form -> listOf(
            Triple("godan", "五段动词", "base"),
            Triple("ichidan", "一段动词", "base"),
            Triple("irregular", "不规则动词", "base"),
        ).map { (group, label, voice) ->
            val verb = VerbData.VERBS.first { it.group == group }
            Triple(verb, label, voice)
        }
        is VerbTheme.Voice -> listOf(
            Triple("godan", "五段动词", theme.voiceId),
            Triple("ichidan", "一段动词", theme.voiceId),
            Triple("irregular", "不规则动词", theme.voiceId),
        ).map { (group, label, voice) ->
            val verb = VerbData.VERBS.first { it.group == group }
            Triple(verb, label, voice)
        }
    }
    return pairs.map { (verb, label, voice) ->
        val inflection = when (theme) {
            is VerbTheme.Form -> theme.formId
            is VerbTheme.Voice -> "dictionary"
        }
        VerbLessonExample(
            groupLabel = label,
            dictionary = verb.dictionary,
            reading = verb.reading,
            result = VerbEngine.conjugate(verb, voice, inflection),
            explain = VerbEngine.explain(verb, voice, inflection),
        )
    }
}
