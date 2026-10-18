package com.nihongodoushi.app.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.engine.adjective.AdjectiveData
import com.nihongodoushi.app.engine.particle.ParticleData
import com.nihongodoushi.app.engine.verb.VerbData
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.adjectiveModulePercent
import com.nihongodoushi.app.ui.isReviewDue
import com.nihongodoushi.app.ui.particleMasteredCount
import com.nihongodoushi.app.ui.verbModulePercent
import com.nihongodoushi.app.ui.common.EntryCard
import com.nihongodoushi.app.ui.common.PlannedRow
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate
import kotlin.math.roundToInt

/** 学习页可进入的二级页（由主页建议与学习页入口共用） */
enum class StudyDestination { VERB, ADJECTIVE, PARTICLE, MISTAKES }

/**
 * 学习页（2026-10-17 四标签改版）：承接原主页的模块入口职责。
 * 结构：页头 → 练习模块（四张入口卡，副信息口径与原主页一致）→ 更多工具（查词规划中位）。
 */
@Composable
fun StudyScreen(
    app: AppState,
    onOpen: (StudyDestination) -> Unit,
) {
    val paper = paperColors()
    val today = LocalDate.now()

    val verbTotal = VerbData.VERBS.size
    val adjectiveTotal = AdjectiveData.ADJECTIVES.size
    val verbPracticed = app.progress.verbIds.size
    val adjectivePracticed = app.progress.adjectiveIds.size
    val mistakeCount = app.mistakes.size
    val dueCount = app.mistakes.count { app.isReviewDue(it) }

    val particleMastered = app.particleMasteredCount()
    val particlePercent = if (ParticleData.TOTAL_QUESTIONS == 0) {
        0
    } else {
        (particleMastered * 100.0 / ParticleData.TOTAL_QUESTIONS).roundToInt()
    }
    val particleMistakeCount = app.particleMistakes.size
    val mistakeTotal = mistakeCount + particleMistakeCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 段① 页头：主标题 + 日期 + 副标题（四页页头一致律）
        Column(modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)) {
            Text(
                text = today.year.toString() + "年" + today.monthValue + "月" + today.dayOfMonth + "日",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
            )
            Text(
                text = "学习",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "按模块练习，错题会按间隔复习",
                style = MaterialTheme.typography.labelMedium,
                color = paper.muted,
            )
        }

        // 段② 练习模块：四张入口卡（样式与副信息口径和原主页完全一致）
        Text(
            text = "练习模块",
            style = MaterialTheme.typography.labelMedium,
            color = paper.muted,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )
        EntryCard(
            icon = Icons.Filled.Create,
            title = "动词练习",
            subtitle = "$verbTotal 个词条 · 已练 $verbPracticed · 掌握度 ${app.verbModulePercent()}%",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(StudyDestination.VERB) },
        )
        EntryCard(
            icon = Icons.AutoMirrored.Filled.List,
            title = "形容词练习",
            subtitle = "$adjectiveTotal 个词条 · 已练 $adjectivePracticed · 掌握度 ${app.adjectiveModulePercent()}%",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(StudyDestination.ADJECTIVE) },
        )
        EntryCard(
            icon = Icons.Filled.Info,
            title = "助词学习",
            subtitle = "${ParticleData.LESSONS.size} 个专题 · 已掌握 $particlePercent% · 错题 $particleMistakeCount 道",
            tint = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onOpen(StudyDestination.PARTICLE) },
        )
        EntryCard(
            icon = Icons.Filled.Refresh,
            title = "错题本",
            subtitle = if (mistakeTotal == 0) {
                "暂无错题 · 答错的题会按间隔复习排入"
            } else {
                "$mistakeTotal 道待复习" +
                    (if (dueCount > 0) " · $dueCount 道今天到期" else " · 今天无到期") +
                    (if (particleMistakeCount > 0) " · 助词 $particleMistakeCount 道" else "")
            },
            tint = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onOpen(StudyDestination.MISTAKES) },
        )

        // 段③ 更多工具：查词规划中位（可见、标注「规划中」、不可点击）
        Text(
            text = "更多工具",
            style = MaterialTheme.typography.labelMedium,
            color = paper.muted,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            PlannedRow(
                icon = Icons.Filled.Search,
                title = "查词",
                description = "查日语单词的读音、释义和用法",
            )
        }
    }
}
