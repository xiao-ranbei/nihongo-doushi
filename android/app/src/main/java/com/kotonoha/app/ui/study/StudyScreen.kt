package com.kotonoha.app.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kotonoha.app.ui.glass.GlassSurface
import com.kotonoha.app.engine.adjective.AdjectiveData
import com.kotonoha.app.engine.particle.ParticleData
import com.kotonoha.app.engine.verb.VerbData
import com.kotonoha.app.ui.AppState
import com.kotonoha.app.ui.adjectiveModulePercent
import com.kotonoha.app.ui.isReviewDue
import com.kotonoha.app.ui.particleMasteredCount
import com.kotonoha.app.ui.verbModulePercent
import com.kotonoha.app.ui.common.EntryCard
import com.kotonoha.app.ui.common.ModuleHeader
import com.kotonoha.app.ui.common.ModuleSectionLabel
import com.kotonoha.app.ui.common.PlannedRow
import com.kotonoha.app.ui.theme.GlassTokens
import com.kotonoha.app.ui.theme.geometry
import com.kotonoha.app.ui.theme.moduleTone
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
    val g = geometry()
    val tone = moduleTone()
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
    val practicedTotal = verbPracticed + adjectivePracticed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 底部 112dp 让位给悬浮岛（覆盖浮法下内容会从岛后方穿过，滚到底时最后一项不被遮）
            .padding(
                // 页边距 22dp = 方案 B 的 --sp-page
                start = GlassTokens.pagePadding,
                end = GlassTokens.pagePadding,
                top = 6.dp,
                bottom = GlassTokens.islandContentInset,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 段① 页头：大标题 + 副信息（四页页头一致律）
        ModuleHeader(
            title = "学习",
            subtitle = today.year.toString() + "年" + today.monthValue + "月" +
                today.dayOfMonth + "日 · 已练 $practicedTotal 项",
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
        )

        // 段② 练习模块：四张入口卡，图标底盒与区块标题取墨绿
        ModuleSectionLabel(
            title = "练习模块",
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            tone = tone,
        )
        EntryCard(
            icon = Icons.Filled.Create,
            title = "动词练习",
            subtitle = "$verbTotal 个词条 · 已练 $verbPracticed · 掌握度 ${app.verbModulePercent()}%",
            onClick = { onOpen(StudyDestination.VERB) },
        )
        EntryCard(
            icon = Icons.AutoMirrored.Filled.List,
            title = "形容词练习",
            subtitle = "$adjectiveTotal 个词条 · 已练 $adjectivePracticed · 掌握度 ${app.adjectiveModulePercent()}%",
            onClick = { onOpen(StudyDestination.ADJECTIVE) },
        )
        EntryCard(
            icon = Icons.Filled.Info,
            title = "助词学习",
            subtitle = "${ParticleData.LESSONS.size} 个专题 · 已掌握 $particlePercent% · 错题 $particleMistakeCount 道",
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
            onClick = { onOpen(StudyDestination.MISTAKES) },
        )

        // 段③ 更多工具：查词规划中位（可见、标注「规划中」、不可点击）
        ModuleSectionLabel(
            title = "更多工具",
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            tone = tone,
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlannedRow(
                icon = Icons.Filled.Search,
                title = "查词",
                description = "查日语单词的读音、释义和用法",
            )
        }
    }
}
