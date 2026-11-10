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
import com.kotonoha.app.ui.common.GroupDivider
import com.kotonoha.app.ui.common.GroupRow
import com.kotonoha.app.ui.common.ModuleHeader
import com.kotonoha.app.ui.common.SectionGroup
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

        // 段② 练习模块：四个入口从「四张独立卡」收进一个容器。
        // 分割方式因此从「卡片边界」变成「容器 + 1dp 分隔线」——
        // 四项是同一件事（今天要练什么）的四个选项，不该各自占一张卡
        SectionGroup(title = "练习模块", tone = tone) {
            GroupRow(
                icon = Icons.Filled.Create,
                iconTone = tone,
                title = "动词练习",
                subtitle = "$verbTotal 个词条 · 已练 $verbPracticed · 掌握度 ${app.verbModulePercent()}%",
                showChevron = true,
                onClick = { onOpen(StudyDestination.VERB) },
            )
            GroupDivider(startInset = 62.dp)
            GroupRow(
                icon = Icons.AutoMirrored.Filled.List,
                iconTone = tone,
                title = "形容词练习",
                subtitle = "$adjectiveTotal 个词条 · 已练 $adjectivePracticed · 掌握度 ${app.adjectiveModulePercent()}%",
                showChevron = true,
                onClick = { onOpen(StudyDestination.ADJECTIVE) },
            )
            GroupDivider(startInset = 62.dp)
            GroupRow(
                icon = Icons.Filled.Info,
                iconTone = tone,
                title = "助词学习",
                subtitle = "${ParticleData.LESSONS.size} 个专题 · 已掌握 $particlePercent% · 错题 $particleMistakeCount 道",
                showChevron = true,
                onClick = { onOpen(StudyDestination.PARTICLE) },
            )
            GroupDivider(startInset = 62.dp)
            GroupRow(
                icon = Icons.Filled.Refresh,
                iconTone = tone,
                title = "错题本",
                subtitle = if (mistakeTotal == 0) {
                    "暂无错题 · 答错的题会按间隔复习排入"
                } else {
                    "$mistakeTotal 道待复习" +
                        (if (dueCount > 0) " · $dueCount 道今天到期" else " · 今天无到期") +
                        (if (particleMistakeCount > 0) " · 助词 $particleMistakeCount 道" else "")
                },
                showChevron = true,
                onClick = { onOpen(StudyDestination.MISTAKES) },
            )
        }

        // 段③ 更多工具：规划中的能力留一个位，用「规划中」徽章而不是整张卡
        SectionGroup(title = "更多工具", tone = tone) {
            GroupRow(
                icon = Icons.Filled.Search,
                iconTone = tone,
                title = "查词",
                subtitle = "查日语单词的读音、释义和用法",
                badge = "规划中",
            )
        }

    }
}
