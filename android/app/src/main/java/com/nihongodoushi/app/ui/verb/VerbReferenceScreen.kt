package com.nihongodoushi.app.ui.verb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.engine.verb.VerbEngine
import com.nihongodoushi.app.ui.common.ModuleSectionLabel
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.common.ReferencePickerItem
import com.nihongodoushi.app.ui.common.ReferencePickerSheet
import com.nihongodoushi.app.ui.practice.VerbGroupLabels
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 动词活用速查页（D-008 暂缓项转正）：选一个词条，看它在各语态、各活用形式下的
 * 全部变形与规则说明，任一行可「练」直接进入该范围出题。
 *
 * 内容全部来自引擎（conjugate / explainVoice / explain），不自造规则文案；
 * 语态对照与活用表的展示口径对齐 Web 速查页（renderReferenceTable）。
 */
@Composable
fun VerbReferenceScreen(
    onBack: () -> Unit,
    onPractice: (voiceId: String, inflectionId: String, label: String) -> Unit,
) {
    val paper = paperColors()
    var verbId by rememberSaveable { mutableStateOf(VerbData.VERBS.first().id) }
    var voiceId by rememberSaveable { mutableStateOf("base") }
    var showPicker by remember { mutableStateOf(false) }

    val verb = VerbData.VERB_BY_ID[verbId] ?: VerbData.VERBS.first()
    val voice = VerbData.VOICE_BY_ID[voiceId] ?: VerbData.VOICES.first()

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(title = "活用速查", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPicker = true },
                shape = RoundedCornerShape(16.dp),
                color = paper.bgPaleGreen,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = verb.dictionary + "（" + verb.reading + "）",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = verb.meaning + " · " + VerbGroupLabels.byGroup(verb.group),
                            style = MaterialTheme.typography.labelMedium,
                            color = paper.textStrong,
                        )
                    }
                    Text(
                        text = "换词条",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = "语态大意：" + voice.meaning,
                style = MaterialTheme.typography.labelMedium,
                color = paper.textStrong,
            )

            Text(
                text = "选择语态",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
                modifier = Modifier.padding(start = 2.dp),
            )
            VoiceChips(
                selectedId = voiceId,
                onSelect = { voiceId = it },
            )

            ModuleSectionLabel(
                title = "辞书形对照",
                caption = "${VerbData.VOICES.size} 种语态",
                modifier = Modifier.padding(top = 2.dp),
            )
            VerbData.VOICES.forEach { item ->
                val result = VerbEngine.conjugate(verb, item.id, "dictionary")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (item.id == voiceId) paper.bgPaleGreen else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (item.id == voiceId) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (item.id == "base") {
                                    verb.dictionary + "（不变）"
                                } else {
                                    verb.dictionary + " → " + result.written
                                } + if (result.kana != result.written) "（" + result.kana + "）" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        TextButton(
                            onClick = {
                                voiceId = item.id
                                onPractice(item.id, "dictionary", verb.dictionary + " · " + item.label)
                            },
                        ) {
                            Text("练", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            listOf("basic" to "活用形式 · 基础", "advanced" to "活用形式 · 进阶").forEach { (level, title) ->
                val forms = VerbData.INFLECTIONS.filter { it.level == level }
                ModuleSectionLabel(
                    title = title,
                    caption = "${forms.size} 个",
                    modifier = Modifier.padding(top = 6.dp),
                )
                forms.forEach { form ->
                    val result = VerbEngine.conjugate(verb, voiceId, form.id)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = form.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TextButton(
                                    onClick = {
                                        onPractice(
                                            voiceId,
                                            form.id,
                                            verb.dictionary + " · " + VerbGroupLabels.targetLabel(voiceId, form.id),
                                        )
                                    },
                                ) {
                                    Text("练", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = result.written + if (result.kana != result.written) "（" + result.kana + "）" else "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = form.meaning,
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.textStrong,
                            )
                            Text(
                                text = VerbEngine.explain(verb, voiceId, form.id),
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.muted,
                            )
                        }
                    }
                }
            }

            Text(
                text = "变形与规则说明均由引擎实时计算，与练习判题口径一致。",
                modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = paper.muted,
            )
        }
    }

    if (showPicker) {
        ReferencePickerSheet(
            items = VerbData.VERBS.map { item ->
                ReferencePickerItem(
                    id = item.id,
                    title = item.dictionary + "（" + item.reading + "）",
                    subtitle = item.meaning,
                    caption = VerbGroupLabels.byGroup(item.group),
                    keywords = listOf(item.dictionary, item.reading, item.meaning, item.id),
                )
            },
            selectedId = verb.id,
            onSelect = {
                verbId = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 语态选择 chips（原形 + 6 种派生语态）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceChips(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val paper = paperColors()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        VerbData.VOICES.forEach { item ->
            val selected = item.id == selectedId
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                onClick = { onSelect(item.id) },
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> paper.textStrong
                    },
                )
            }
        }
    }
}
