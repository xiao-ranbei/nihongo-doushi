package com.nihongodoushi.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihongodoushi.app.ui.AppState
import com.nihongodoushi.app.ui.theme.paperColors
import java.time.LocalDate

/** 学习主页：今日答题量、连续天数、已开始模块数与各模块状态（对齐 web renderHomeDashboard）。 */

@Composable
fun HomeScreen(app: AppState) {
    val isToday = app.daily.lastDate == LocalDate.now().toString()
    val verbCount = if (isToday) app.daily.verbAnswers else 0
    val adjectiveCount = if (isToday) app.daily.adjectiveAnswers else 0
    val total = verbCount + adjectiveCount
    val activeModules = (if (verbCount > 0) 1 else 0) + (if (adjectiveCount > 0) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "学习主页",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(modifier = Modifier.weight(1f), value = "$total 题", label = "今日答题量")
            StatCard(modifier = Modifier.weight(1f), value = app.currentStreak().toString(), label = "连续学习天数")
            StatCard(modifier = Modifier.weight(1f), value = activeModules.toString(), label = "已开始模块")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ModuleCard(
                modifier = Modifier.weight(1f),
                title = "动词",
                todayCount = verbCount,
                extra = "错题 ${app.verbMistakes().size} 题",
            )
            ModuleCard(
                modifier = Modifier.weight(1f),
                title = "形容词",
                todayCount = adjectiveCount,
                extra = "错题 ${app.adjectiveMistakes().size} 题",
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = paperColors().textStrong)
        }
    }
}

@Composable
private fun ModuleCard(modifier: Modifier, title: String, todayCount: Int, extra: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Surface(color = paperColors().bgPaleGreen, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = if (todayCount > 0) "今日已练 $todayCount 题" else "今日尚未练习",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (todayCount > 0) paperColors().textGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = extra, style = MaterialTheme.typography.labelMedium, color = paperColors().textStrong)
        }
    }
}
