package de.kalorienapp.ui.stats

import androidx.compose.runtime.saveable.rememberSaveable
import de.kalorienapp.ui.components.neonChipColors
import androidx.compose.material3.FilterChip
import de.kalorienapp.domain.model.MeasureType
import de.kalorienapp.domain.model.BodyMeasurement
import de.kalorienapp.ui.components.LocalWeightEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.TrendPoint
import de.kalorienapp.domain.WeightTrend
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.Tip
import de.kalorienapp.ui.components.TipSlot
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.goal.GoalHistoryItem
import de.kalorienapp.ui.theme.Neon
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy · HH:mm", Locale.GERMAN)

/**
 * Reiter „Körper“ in der Statistik (früher eigener Tab „Gewicht“): Messungen + Trend, Körpermaße,
 * erreichte Ziele. Nur sichtbar, wenn „Gewicht tracken“ an ist.
 */
@Composable
fun BodySection(
    /** Aus der Wiege-Erinnerung geöffnet → Dialog direkt zeigen. */
    openWeighDialog: Boolean = false,
    onWeighDialogShown: () -> Unit = {},
    /** Erreichtes Ziel antippen → Feier-Bildschirm (dort auch „Erfolg teilen“). */
    onOpenGoal: (Long) -> Unit = {},
    /** Körperanalyse eintragen (eigene Seite). */
    onAddAnalysis: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showWeightDialog by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<WeightGoal?>(null) }
    LaunchedEffect(openWeighDialog) {
        if (openWeighDialog) {
            showWeightDialog = true
            onWeighDialogShown()
        }
    }

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
            onSave = { kg, fat -> viewModel.addWeight(kg, fat); showWeightDialog = false },
            onDismiss = { showWeightDialog = false },
        )
    }

    run {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Drei unabhängige Schalter: Gewicht, Körperanalyse, Körpermaße – hier steht nur, was an ist.
            val weightOn = LocalWeightEnabled.current
            if (weightOn) {
                TipSlot(Tip.WEIGHT)
                WeightSection(
                    entries = state.weights,
                    trend = state.weightTrend,
                    bodyFat = state.bodyFat,
                    onAdd = { showWeightDialog = true },
                    onDelete = viewModel::deleteWeight,
                )
            }
            BodyAnalysisSection(onAdd = onAddAnalysis)
            BodyMeasurementsSection()
            if (weightOn && state.reachedGoals.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.goal_history_title), style = MaterialTheme.typography.titleLarge)
                state.reachedGoals.forEach { goal ->
                    GoalHistoryItem(goal, onDelete = { goalToDelete = goal }, onOpen = { onOpenGoal(goal.id) })
                }
            }
        }
    }
}

@Composable
private fun WeightSection(
    entries: List<WeightEntry>,
    trend: List<TrendPoint>,
    bodyFat: List<BodyMeasurement>,
    onAdd: () -> Unit,
    onDelete: (WeightEntry) -> Unit,
) {
    // Umschalter Gewicht | Körperfett – nur sinnvoll ab zwei Körperfett-Werten.
    var showFat by rememberSaveable { mutableStateOf(false) }
    val fatChart = showFat && bodyFat.size >= 2
    val zone = ZoneId.systemDefault()
    // Körperfett je Tag (letzte Messung des Tages) – steht an der Gewichtsmessung desselben Tages.
    val fatByDay = bodyFat.groupBy { it.measuredAt.atZone(zone).toLocalDate() }.mapValues { (_, list) -> list.maxBy { it.measuredAt }.value }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (bodyFat.size >= 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showFat,
                    onClick = { showFat = false },
                    label = { Text(stringResource(R.string.chart_weight)) },
                    colors = neonChipColors(),
                )
                FilterChip(
                    selected = showFat,
                    onClick = { showFat = true },
                    label = { Text(stringResource(R.string.chart_body_fat)) },
                    colors = neonChipColors(),
                )
            }
        }
        if (fatChart) {
            BodyFatChart(bodyFat)
        } else Row(verticalAlignment = Alignment.Bottom) {
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
        if (!fatChart) trend.lastOrNull()?.let { last ->
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
        if (!fatChart) {
            WeightLineChart(entries, trend, Modifier.fillMaxWidth())
            if (entries.size >= 2) {
                Text(stringResource(R.string.weight_trend_caption), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
        }
        entries.takeLast(10).reversed().forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.measuredAt.atZone(ZoneId.systemDefault()).format(dateTimeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val fat = fatByDay[entry.measuredAt.atZone(zone).toLocalDate()]
                Text(
                    stringResource(R.string.weight_value, entry.weightKg.toInputString()) +
                        (fat?.let { " · " + it.toInputString() + " %" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
                }
            }
        }
    }
}

/** Körperfett-Verlauf (statt Gewicht): letzter Wert groß, Veränderung, Linie über alle Messungen. */
@Composable
private fun BodyFatChart(bodyFat: List<BodyMeasurement>) {
    val latest = bodyFat.last().value
    val diff = latest - bodyFat.first().value
    Row(verticalAlignment = Alignment.Bottom) {
        Text(latest.toInputString() + " %", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            (if (diff > 0) "+" else "") + diff.format(1) + " %",
            style = MaterialTheme.typography.bodyMedium,
            color = if (diff <= 0) Neon.Green else Neon.Orange,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
    }
    // Gleiche Linie wie beim Gewicht, nur ohne Trend.
    WeightLineChart(bodyFat.map { WeightEntry(measuredAt = it.measuredAt, weightKg = it.value) }, emptyList(), Modifier.fillMaxWidth())
    Text(stringResource(R.string.body_fat_caption), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
}

@Composable
private fun WeightDialog(initialKg: Double?, onSave: (Double, Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.toInputString().orEmpty()) }
    var fatText by remember { mutableStateOf("") }
    val value = text.parseDecimal()?.takeIf { it in 30.0..300.0 }
    // Körperfett freiwillig: leer = nicht gemessen
    val fat = fatText.parseDecimal()?.takeIf(MeasureType.BODY_FAT::isValid)
    val fatError = fatText.isNotBlank() && fat == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.profile_weight),
                    suffix = stringResource(R.string.unit_kg),
                    isError = text.isNotBlank() && value == null,
                    errorText = stringResource(R.string.error_weight),
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    label = stringResource(R.string.weight_body_fat_optional),
                    suffix = stringResource(R.string.unit_percent),
                    isError = fatError,
                    errorText = stringResource(R.string.error_number),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onSave(it, fat) } }, enabled = value != null && !fatError) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
