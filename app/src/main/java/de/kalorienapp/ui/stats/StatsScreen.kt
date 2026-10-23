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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.domain.TrendPoint
import de.kalorienapp.domain.WeightTrend
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.goal.GoalHistoryItem
import de.kalorienapp.ui.theme.Neon
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy · HH:mm", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    /** Aus der Wiege-Erinnerung geöffnet → Dialog direkt zeigen. */
    openWeighDialog: Boolean = false,
    onWeighDialogShown: () -> Unit = {},
    onOpenReport: (Long) -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val generation by viewModel.reportGeneration.collectAsStateWithLifecycle()
    var showWeightDialog by remember { mutableStateOf(false) }
    LaunchedEffect(openWeighDialog) {
        if (openWeighDialog) {
            showWeightDialog = true
            onWeighDialogShown()
        }
    }
    var goalToDelete by remember { mutableStateOf<WeightGoal?>(null) }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text(stringResource(R.string.goal_delete_title)) },
            text = { Text(stringResource(R.string.goal_delete_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(goal); goalToDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showWeightDialog) {
        WeightDialog(
            initialKg = state.weights.lastOrNull()?.weightKg ?: state.profileWeightKg,
            onSave = { kg -> viewModel.addWeight(kg); showWeightDialog = false },
            onDismiss = { showWeightDialog = false },
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeeklyReportSection(
                reports = state.reports,
                generation = generation,
                onGenerate = { viewModel.generateReport(onOpenReport) },
                onOpen = { onOpenReport(it.id) },
            )
            ChoiceChips(
                options = StatsPeriod.entries,
                selected = state.period,
                onSelect = viewModel::selectPeriod,
                label = { stringResource(if (it == StatsPeriod.WEEK) R.string.stats_period_week else R.string.stats_period_month) },
            )
            val stats = state.stats
            if (stats != null) {
                KcalBarChart(stats.days, Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.stats_chart_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
                if (stats.loggedDays == 0) {
                    Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodyMedium)
                } else {
                    SummaryPills(stats)
                    AverageCard(stats, state.targets)
                }
            }
            WeightSection(
                entries = state.weights,
                trend = state.weightTrend,
                onAdd = { showWeightDialog = true },
                onDelete = viewModel::deleteWeight,
            )
            BodyMeasurementsSection()
            if (state.reachedGoals.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.goal_history_title), style = MaterialTheme.typography.titleLarge)
                state.reachedGoals.forEach { goal ->
                    GoalHistoryItem(goal, onDelete = { goalToDelete = goal })
                }
            }
        }
    }
}

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
private fun AverageCard(stats: PeriodStats, targets: DailyTargets?) {
    val avg = stats.averageEaten ?: return
    Card(Modifier.fillMaxWidth()) {
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

@Composable
private fun WeightSection(entries: List<WeightEntry>, trend: List<TrendPoint>, onAdd: () -> Unit, onDelete: (WeightEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.weight_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.weight_add), modifier = Modifier.padding(start = 6.dp))
            }
        }
        val latest = entries.lastOrNull()
        if (latest == null) {
            Text(stringResource(R.string.weight_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.weight_value, latest.weightKg.toInputString()),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (entries.size > 1) {
                val diff = latest.weightKg - entries.first().weightKg
                Text(
                    stringResource(R.string.weight_change, (if (diff > 0) "+" else "") + diff.format(1)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diff <= 0) Neon.Green else Neon.Orange,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
            }
        }
        trend.lastOrNull()?.let { last ->
            val perWeek = WeightTrend.weeklyChange(trend)
            Text(
                if (perWeek == null) {
                    stringResource(R.string.weight_trend, last.weightKg.format(1))
                } else {
                    stringResource(R.string.weight_trend_change, last.weightKg.format(1), (if (perWeek > 0) "+" else "") + perWeek.format(2))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Neon.Violet,
            )
        }
        WeightLineChart(entries, trend, Modifier.fillMaxWidth())
        if (entries.size >= 2) {
            Text(stringResource(R.string.weight_trend_caption), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
        entries.takeLast(10).reversed().forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.measuredAt.atZone(ZoneId.systemDefault()).format(dateTimeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.weight_value, entry.weightKg.toInputString()), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun WeightDialog(initialKg: Double?, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.toInputString().orEmpty()) }
    val value = text.parseDecimal()?.takeIf { it in 30.0..300.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_add_title)) },
        text = {
            NumberField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.profile_weight),
                suffix = stringResource(R.string.unit_kg),
                isError = text.isNotBlank() && value == null,
                errorText = stringResource(R.string.error_weight),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
