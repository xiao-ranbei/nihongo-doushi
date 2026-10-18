package com.nihongodoushi.app.ui.adjective

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.ui.common.ModuleSectionLabel
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.common.ReferencePickerItem
import com.nihongodoushi.app.ui.common.ReferencePickerSheet
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 形容词速查页：选一个词条，看它在 10 种形式下的全部变形与规则说明，
 * 任一张卡可「练」直接进入该形式出题（与动词速查页同构）。
 *
 * 变形与规则均来自引擎（conjugate / explain），「仅形态练习」形式标注
 * morphologyPracticeOnly——日常书面不独立出现，仅作形态训练。
 */
@Composable
fun AdjectiveReferenceScreen(
    onBack: () -> Unit,
    onPractice: (formId: String, label: String) -> Unit,
) {
    val paper = paperColors()
    var adjectiveId by rememberSaveable { mutableStateOf(AdjectiveData.ADJECTIVES.first().id) }
    var showPicker by remember { mutableStateOf(false) }

    val adjective = AdjectiveData.ADJECTIVE_BY_ID[adjectiveId] ?: AdjectiveData.ADJECTIVES.first()
    val kindLabel = if (adjective.kind == "i") "い形容词" else "な形容词"

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(title = "形容词速查", onBack = onBack)

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
                    // 读屏口径（IK-18）：clickable 自带合并子语义，整卡一个焦点站
                    .clickable(role = Role.Button) { showPicker = true },
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
                            text = adjective.word + "（" + adjective.kana + "）",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = adjective.meaning + " · " + kindLabel,
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

            listOf("basic" to "基础形式", "advanced" to "进阶形式").forEach { (level, title) ->
                val forms = AdjectiveData.FORMS.filter { it.level == level }
                ModuleSectionLabel(
                    title = title,
                    caption = "${forms.size} 个",
                    modifier = Modifier.padding(top = 2.dp),
                )
                forms.forEach { form ->
                    val result = AdjectiveEngine.conjugate(adjective, form.id)
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
                                if (form.morphologyPracticeOnly) {
                                    Text(
                                        text = "仅形态练习",
                                        modifier = Modifier.padding(end = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = paper.muted,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onPractice(form.id, adjective.word + " · " + form.label)
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
                            if (result.alts.isNotEmpty()) {
                                Text(
                                    text = "也可说：" + result.alts.joinToString("、") { it.written },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = paper.textStrong,
                                )
                            }
                            Text(
                                text = form.meaning,
                                style = MaterialTheme.typography.labelSmall,
                                color = paper.textStrong,
                            )
                            Text(
                                text = AdjectiveEngine.explain(adjective, form.id),
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
            items = AdjectiveData.ADJECTIVES.map { item ->
                ReferencePickerItem(
                    id = item.id,
                    title = item.word + "（" + item.kana + "）",
                    subtitle = item.meaning,
                    caption = if (item.kind == "i") "い形容词" else "な形容词",
                    keywords = listOf(item.word, item.kana, item.meaning, item.id),
                )
            },
            selectedId = adjective.id,
            onSelect = {
                adjectiveId = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
