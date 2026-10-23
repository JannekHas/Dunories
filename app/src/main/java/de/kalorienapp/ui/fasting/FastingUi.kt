package de.kalorienapp.ui.fasting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.domain.Fasting
import de.kalorienapp.domain.FastingConfig
import de.kalorienapp.domain.FastingPhase
import de.kalorienapp.domain.FastingPlan
import de.kalorienapp.domain.FastingStats
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.notify.WorkScheduler
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class FastingSettingsViewModel(private val settings: AppSettings, private val scheduler: WorkScheduler) : ViewModel() {
    val enabled = settings.fastingEnabled
    val config = settings.fastingConfig

    fun setEnabled(value: Boolean) {
        settings.setFastingEnabled(value)
        scheduler.scheduleFasting()
    }

    fun update(transform: (FastingConfig) -> FastingConfig) {
        settings.setFastingConfig(transform(settings.fastingConfig.value))
        scheduler.scheduleFasting()
    }
}

@get:androidx.annotation.StringRes
val FastingPlan.labelRes: Int
    get() = when (this) {
        FastingPlan.H16_8 -> R.string.fasting_plan_16_8
        FastingPlan.H18_6 -> R.string.fasting_plan_18_6
        FastingPlan.H20_4 -> R.string.fasting_plan_20_4
        FastingPlan.CUSTOM -> R.string.fasting_plan_custom
        FastingPlan.FIVE_TWO -> R.string.fasting_plan_5_2
    }

private fun durationText(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(0)
    return "%d:%02d".format(minutes / 60, minutes % 60)
}

/** Profil: Intervallfasten ein/aus, Plan, Essensfenster bzw. Fastentage. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FastingSettingsSection(viewModel: FastingSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }

    if (pickTime) {
        val state = rememberTimePickerState(config.windowStart.hour, config.windowStart.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.fasting_window_start)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(windowStart = LocalTime.of(state.hour, state.minute)) }
                    pickTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.fasting_enable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.fasting_enable_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
            Switch(
                checked = enabled,
                onCheckedChange = viewModel::setEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
            )
        }
        if (!enabled) return@Column
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FastingPlan.entries.forEach { plan ->
                FilterChip(
                    selected = config.plan == plan,
                    onClick = { viewModel.update { it.copy(plan = plan) } },
                    label = { Text(stringResource(plan.labelRes)) },
                    colors = neonChipColors(),
                )
            }
        }
        if (config.isDaily) {
            if (config.plan == FastingPlan.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fasting_custom_hours), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon.SurfaceHighest, contentColor = Neon.TextPrimary)
                    FilledIconButton(
                        onClick = { viewModel.update { it.copy(customFastHours = (it.customFastHours - 1).coerceAtLeast(12)) } },
                        colors = colors,
                        modifier = Modifier.size(32.dp),
                    ) { Text(stringResource(R.string.minus), fontWeight = FontWeight.Bold) }
                    Text(
                        stringResource(R.string.fasting_hours_value, config.fastHours, config.windowHours),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    FilledIconButton(
                        onClick = { viewModel.update { it.copy(customFastHours = (it.customFastHours + 1).coerceAtMost(23)) } },
                        colors = colors,
                        modifier = Modifier.size(32.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                }
            }
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fasting_window_value, config.windowStart.toString(), config.windowEnd.toString()))
            }
            Text(stringResource(R.string.fasting_daily_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        } else {
            Text(stringResource(R.string.fasting_days_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in config.fastDays,
                        onClick = {
                            // Höchstens zwei Fastentage; abwählen geht immer.
                            viewModel.update { c ->
                                when {
                                    day in c.fastDays -> c.copy(fastDays = c.fastDays - day)
                                    c.fastDays.size < 2 -> c.copy(fastDays = c.fastDays + day)
                                    else -> c
                                }
                            }
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.GERMAN)) },
                        colors = neonChipColors(),
                    )
                }
            }
            Text(stringResource(R.string.fasting_5_2_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
    }
}

/** Timer auf „Heute“ – aktualisiert sich jede halbe Minute. */
@Composable
fun FastingCard(config: FastingConfig, sex: Sex?, eatenKcal: Double, entriesInFastingTime: Int) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val status = Fasting.status(config, now)
    val color = when (status.phase) {
        FastingPhase.FASTING, FastingPhase.FAST_DAY -> Neon.Violet
        FastingPhase.EATING, FastingPhase.NORMAL_DAY -> Neon.Green
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when (status.phase) {
                            FastingPhase.FASTING -> R.string.fasting_phase_fasting
                            FastingPhase.EATING -> R.string.fasting_phase_eating
                            FastingPhase.FAST_DAY -> R.string.fasting_phase_fast_day
                            FastingPhase.NORMAL_DAY -> R.string.fasting_phase_normal_day
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(config.plan.labelRes), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
            }
            when (status.phase) {
                FastingPhase.FASTING, FastingPhase.EATING -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                        color = color,
                        trackColor = Neon.Track,
                        drawStopIndicator = {},
                    )
                    Text(
                        stringResource(
                            if (status.phase == FastingPhase.FASTING) R.string.fasting_until_eat else R.string.fasting_until_fast,
                            durationText(Duration.between(now, status.phaseEnd)),
                            status.phaseEnd.toLocalTime().toString(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FastingPhase.FAST_DAY -> {
                    val limit = sex?.let(Fasting::fastDayLimitKcal) ?: 500
                    Text(
                        stringResource(R.string.fasting_fast_day_text, eatenKcal.format(), limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (eatenKcal > limit) Neon.Yellow else Neon.TextPrimary,
                    )
                }
                FastingPhase.NORMAL_DAY -> Text(stringResource(R.string.fasting_normal_day_text), style = MaterialTheme.typography.bodyMedium)
            }
            if (entriesInFastingTime > 0) {
                Text(
                    pluralStringResource(R.plurals.fasting_entries_outside, entriesInFastingTime, entriesInFastingTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.Yellow,
                )
            }
        }
    }
}

/** Statistik: eingehaltene Tage und Ø Fastenpause im Zeitraum. */
@Composable
fun FastingStatsCard(stats: FastingStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.fasting_stats_title), style = MaterialTheme.typography.titleMedium)
            if (stats.evaluatedDays == 0) {
                Text(stringResource(R.string.fasting_stats_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                return@Column
            }
            Text(
                stringResource(R.string.fasting_stats_kept, stats.keptDays, stats.evaluatedDays),
                style = MaterialTheme.typography.bodyLarge,
                color = if (stats.keptDays == stats.evaluatedDays) Neon.Green else Neon.TextPrimary,
            )
            stats.averageFastHours?.let {
                Text(stringResource(R.string.fasting_stats_average, it.format(1)), style = MaterialTheme.typography.bodyMedium, color = Neon.Violet)
            }
            Text(stringResource(R.string.fasting_stats_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
    }
}
