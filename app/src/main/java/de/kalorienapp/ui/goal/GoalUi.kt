package de.kalorienapp.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.data.repository.GoalState
import de.kalorienapp.domain.GoalPlanner
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import de.kalorienapp.ui.components.NumberField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val goalDateFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
private const val MILLIS_PER_DAY = 86_400_000L

fun LocalDate.formatGoalDate(): String = format(goalDateFormatter)

fun Goal.color(): Color = when (this) {
    Goal.LOSE -> Neon.Green
    Goal.MAINTAIN -> Neon.Cyan
    Goal.GAIN -> Neon.Orange
}

internal fun signed(value: Double, decimals: Int = 1): String =
    (if (value > 0) "+" else if (value < 0) "−" else "") + abs(value).format(decimals)

private fun signed(value: Int): String = (if (value > 0) "+" else if (value < 0) "−" else "") + abs(value).format()

/** Wunschgewicht, Schnellwahl „Halten“ und Zieldatum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalInputs(form: GoalFormState, currentWeightKg: Double?, onUpdate: (GoalFormState) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    val maintain = form.isMaintain(currentWeightKg)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        currentWeightKg?.let {
            Text(
                stringResource(R.string.goal_current_weight, it.toInputString()),
                style = MaterialTheme.typography.bodyMedium,
                color = Neon.TextSecondary,
            )
        }
        NumberField(
            value = form.targetWeight,
            onValueChange = { onUpdate(form.copy(targetWeight = it)) },
            label = stringResource(R.string.goal_target_weight),
            suffix = stringResource(R.string.unit_kg),
            isError = form.targetError,
            errorText = stringResource(R.string.error_weight),
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        if (currentWeightKg != null) {
            FilterChip(
                selected = maintain,
                onClick = { onUpdate(form.copy(targetWeight = currentWeightKg.toInputString())) },
                label = { Text(stringResource(R.string.goal_maintain_button)) },
            )
        }
        if (maintain) {
            Text(
                stringResource(R.string.goal_maintain_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
        } else {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text(
                    form.targetDate?.let { stringResource(R.string.goal_target_date_value, it.formatGoalDate()) }
                        ?: stringResource(R.string.goal_pick_date),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        val tomorrow = LocalDate.now().plusDays(1)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (form.targetDate ?: LocalDate.now().plusWeeks(12)).toEpochDay() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis / MILLIS_PER_DAY >= tomorrow.toEpochDay()

                override fun isSelectableYear(year: Int) = year >= tomorrow.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onUpdate(form.copy(targetDate = LocalDate.ofEpochDay(it / MILLIS_PER_DAY)))
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Was das eingegebene Ziel bedeutet: Tempo, Anpassung und ggf. Hinweis auf Begrenzung. */
@Composable
fun GoalPreviewCard(preview: GoalState?, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val goal = preview?.goal
            val plan = preview?.plan
            if (goal == null || plan == null) {
                Text(stringResource(R.string.goal_preview_incomplete), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(
                stringResource(goal.direction.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = goal.direction.color(),
            )
            if (goal.direction != Goal.MAINTAIN) {
                Text(
                    stringResource(R.string.goal_preview_pace, signed(plan.kgPerWeek, 2), signed(plan.dailyAdjustmentKcal)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.goal_preview_kcal, preview.targets.kcal.format()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            LimitedHint(plan.limited, plan.realisticDate, goal.direction)
        }
    }
}

@Composable
private fun LimitedHint(limited: Boolean, realisticDate: LocalDate?, direction: Goal) {
    if (!limited || realisticDate == null) return
    val maxKg = if (direction == Goal.GAIN) GoalPlanner.MAX_GAIN_KG_PER_WEEK else GoalPlanner.MAX_LOSS_KG_PER_WEEK
    Text(
        stringResource(R.string.goal_limited_hint, maxKg.toInputString(), realisticDate.formatGoalDate()),
        style = MaterialTheme.typography.bodySmall,
        color = Neon.Yellow,
    )
}

/** Aktives Ziel im Profil mit Fortschritt; ohne Ziel ein Hinweis. */
@Composable
fun GoalCard(state: GoalState?, onNewGoal: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val goal = state?.goal
            val plan = state?.plan
            if (goal == null || plan == null) {
                Text(stringResource(R.string.goal_none_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.goal_none_text), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            } else {
                val current = state.profile.weightKg
                Text(
                    stringResource(goal.direction.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = goal.direction.color(),
                )
                if (goal.direction == Goal.MAINTAIN) {
                    Text(
                        stringResource(R.string.goal_maintain_since, goal.targetWeightKg.toInputString(), goal.startDate.formatGoalDate()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val targetDate = goal.targetDate!!
                    Text(
                        stringResource(
                            R.string.goal_range,
                            goal.startWeightKg.toInputString(),
                            goal.targetWeightKg.toInputString(),
                            targetDate.formatGoalDate(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val total = goal.targetWeightKg - goal.startWeightKg
                    val progress = if (total != 0.0) ((current - goal.startWeightKg) / total).toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = goal.direction.color(),
                        trackColor = Neon.Track,
                    )
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt().coerceAtLeast(0)
                    Text(
                        stringResource(
                            R.string.goal_remaining,
                            current.toInputString(),
                            abs(goal.targetWeightKg - current).format(1),
                            pluralStringResource(R.plurals.days, days, days),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextSecondary,
                    )
                    Text(
                        stringResource(R.string.goal_preview_pace, signed(plan.kgPerWeek, 2), signed(plan.dailyAdjustmentKcal)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LimitedHint(plan.limited, plan.realisticDate, goal.direction)
                }
            }
            Button(
                onClick = onNewGoal,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (goal == null) R.string.goal_set else R.string.goal_new))
            }
        }
    }
}

@Composable
private fun goalSummaryText(goal: WeightGoal): String {
    val end = goal.endWeightKg ?: goal.startWeightKg
    val days = ChronoUnit.DAYS.between(goal.startDate, goal.endedDate ?: LocalDate.now()).toInt()
    return stringResource(
        R.string.goal_result_text,
        goal.startWeightKg.toInputString(),
        goal.targetWeightKg.toInputString(),
        end.toInputString(),
        signed(end - goal.startWeightKg),
        pluralStringResource(R.plurals.days, days, days),
    )
}

/** Ein erreichtes Ziel in der Historie (löschbar). */
@Composable
fun GoalHistoryItem(goal: WeightGoal, onDelete: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(goal.direction.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = goal.direction.color(),
            )
            Text(
                stringResource(
                    R.string.goal_history_dates,
                    goal.startDate.formatGoalDate(),
                    (goal.endedDate ?: LocalDate.now()).formatGoalDate(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
            Text(goalSummaryText(goal), style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
        }
    }
}
