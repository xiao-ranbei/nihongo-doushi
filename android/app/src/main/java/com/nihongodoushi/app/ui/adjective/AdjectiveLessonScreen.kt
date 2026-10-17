package com.nihongodoushi.app.ui.adjective

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.adjective.AdjectiveEngine
import com.nihongodoushi.app.ui.common.ModuleBottomSurface
import com.nihongodoushi.app.ui.common.ModuleTopBar
import com.nihongodoushi.app.ui.theme.paperColors

/**
 * 形容词形式讲解页：一句话作用（`FormInfo.meaning`）+ 三个真实例词
 * （い / な / 特殊「いい」各一）+ 「练这个形式」。
 */
@Composable
fun AdjectiveLessonScreen(
    formId: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val form = AdjectiveData.FORM_BY_ID[formId] ?: return
    val paper = paperColors()
    val total = AdjectiveData.ADJECTIVES.size

    val examples = listOf(
        AdjectiveData.ADJECTIVES.first { it.kind == "i" && !it.special },
        AdjectiveData.ADJECTIVES.first { it.kind == "na" },
        AdjectiveData.ADJECTIVES.first { it.special },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            title = form.label,
            onBack = onBack,
            trailing = {
                Text(
                    text = if (form.level == "advanced") "进阶" else "基础",
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
                    text = form.meaning,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = "三个例词（い / な / 特殊）",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
                modifier = Modifier.padding(start = 2.dp),
            )

            examples.forEach { adjective ->
                val result = AdjectiveEngine.conjugate(adjective, formId)
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
                            text = adjective.word + "（" + adjective.kana + "）" +
                                if (adjective.special) " · 特殊" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "→ " + result.written + "（" + result.kana + "）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = AdjectiveEngine.explain(adjective, formId),
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
                    text = "练这个形式（$total 个词条）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
