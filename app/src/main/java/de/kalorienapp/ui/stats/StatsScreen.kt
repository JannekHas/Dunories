package de.kalorienapp.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.components.MicroList
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.calendar.CalendarSection
import de.kalorienapp.ui.components.EmptyState
import de.kalorienapp.ui.components.Tip
import de.kalorienapp.ui.components.TipSlot
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.TabbedPages
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.fasting.FastingStatsCard
import de.kalorienapp.ui.theme.Neon
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenReport: (Long) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onAddEntry: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val generation by viewModel.reportGeneration.collectAsStateWithLifecycle()
    val fastingStats by viewModel.fastingStats.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        TabbedPages(
            tabs = listOf(
                stringResource(R.string.stats_tab_food),
                stringResource(R.string.nav_calendar),
                stringResource(R.string.stats_tab_ai),
            ),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                TAB_FOOD -> {
                    TipSlot(Tip.STATS)
                    ChoiceChips(
                        options = StatsPeriod.entries,
                        selected = state.period,
                        onSelect = viewModel::selectPeriod,
                        label = { stringResource(if (it == StatsPeriod.WEEK) R.string.stats_period_week else R.string.stats_period_month) },
                    )
                    val stats = state.stats
                    if (stats != null && stats.loggedDays == 0) {
                        EmptyState(
                            title = stringResource(R.string.stats_empty_title),
                            text = stringResource(R.string.stats_empty_text),
                            action = stringResource(R.string.stats_empty_action),
                            onAction = onAddEntry,
                        )
                    } else if (stats != null) {
                        KcalBarChart(stats.days, Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.stats_chart_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = Neon.TextSecondary,
                        )
                        SummaryPills(stats)
                        AverageCard(stats, state.targets, state.sex)
                        fastingStats?.let { FastingStatsCard(it) }
                    }
                }
                TAB_CALENDAR -> CalendarSection(onDayClick = onDayClick)
                else -> WeeklyReportSection(
                    reports = state.reports,
                    generation = generation,
                    onGenerate = { viewModel.generateReport(onOpenReport) },
                    onOpen = { onOpenReport(it.id) },
                )
            }
        }
    }
}

private const val TAB_FOOD = 0
private const val TAB_CALENDAR = 1

private fun percent(value: Double?) = value?.let { "${(it * 100).roundToInt()} %" } ?: "–"

@Composable
private fun SummaryPills(stats: PeriodStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonPill(
            value = stats.averageEaten?.kcal?.format() ?: "–",
            label = stringResource(R.string.stats_avg_kcal),
            color = Neon.Orange,
            modifier = Modifier.weight(1f),
        )
        NeonPill(
            value = percent(stats.onTargetRate),
            label = stringResource(R.string.stats_on_target),
            color = Neon.Green,
            modifier = Modifier.weight(1f),
        )
        NeonPill(
            value = percent(stats.proteinHitRate),
            label = stringResource(R.string.stats_protein_hit),
            color = Neon.Magenta,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Durchschnitt pro erfasstem Tag – Nährwerte in derselben Struktur wie in der Tagesübersicht. */
@Composable
private fun AverageCard(stats: PeriodStats, targets: DailyTargets?, sex: Sex?) {
    val avg = stats.averageEaten ?: return
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.stats_avg_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.stats_logged_days_text, stats.loggedDays, stats.days.size),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
            AvgRow(stringResource(R.string.nutrient_energy), avg.kcal, stats.averageBudgetKcal, Neon.Orange, unit = R.string.unit_kcal)
            AvgRow(stringResource(R.string.protein), avg.proteinG, targets?.proteinG, Neon.Magenta)
            AvgRow(stringResource(R.string.carbs), avg.carbsG, targets?.carbsG, Neon.Yellow)
            AvgSubRow(stringResource(R.string.nutrient_sugar), avg.sugarG, Neon.Yellow)
            AvgRow(stringResource(R.string.fat), avg.fatG, targets?.fatG, Neon.Green)
            AvgSubRow(stringResource(R.string.nutrient_saturated_fat), avg.saturatedFatG, Neon.Green)
            AvgRow(stringResource(R.string.nutrient_fiber), avg.fiberG, null, Neon.TextSecondary)
            AvgRow(stringResource(R.string.nutrient_salt), avg.saltG, null, Neon.TextSecondary)
            if (stats.averageActivityKcal > 0) {
                AvgRow(stringResource(R.string.summary_activity), stats.averageActivityKcal.toDouble(), null, Neon.Violet, unit = R.string.unit_kcal)
            }
            if (avg.micros.isNotEmpty()) {
                Text(stringResource(R.string.micros_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                MicroList(avg.micros, sex)
                Text(stringResource(R.string.micros_stats_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
        }
    }
}

@Composable
private fun AvgRow(label: String, value: Double?, target: Int?, color: Color, unit: Int = R.string.unit_g) {
    val unitText = stringResource(unit)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(
            when {
                value == null -> "–"
                target != null -> "${value.format()} / ${target.format()} $unitText"
                else -> "${value.format(1).removeSuffix(",0")} $unitText"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AvgSubRow(label: String, value: Double?, parentColor: Color) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(parentColor.copy(alpha = 0.7f)))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        Text(
            value?.let { stringResource(R.string.grams_value, it.format(1)) } ?: "–",
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
        )
    }
}
