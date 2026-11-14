package de.kalorienapp.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.theme.Neon
import java.time.format.DateTimeFormatter
import java.util.Locale

private val endFormatter = DateTimeFormatter.ofPattern("EEE, d.M.", Locale.GERMAN)

/** Ernährungsplan oben auf „Heute“: welcher Tag, ganzer Tag wie geplant, am Ende „Stimmt dein Plan noch?“. */
@Composable
internal fun MealPlanCard(
    plan: TodayPlan,
    openMeals: Int,
    onApplyDay: () -> Unit,
    onExtend: () -> Unit,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = plan.dayIndex?.let { stringResource(R.string.plan_card_title, it + 1, plan.days) }
                ?: stringResource(R.string.plan_title)
            Text(title, style = MaterialTheme.typography.titleMedium, color = Neon.Cyan)
            if (plan.dayIndex != null && openMeals >= 2) {
                Button(
                    onClick = onApplyDay,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.plan_apply_day, openMeals), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (plan.askToExtend) {
                Text(stringResource(R.string.plan_ask_extend), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (plan.expired) {
                        stringResource(R.string.plan_ask_extend_expired, plan.end.format(endFormatter))
                    } else {
                        stringResource(R.string.plan_ask_extend_last)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onExtend,
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.Green, contentColor = Neon.OnNeon),
                    ) { Text(stringResource(R.string.plan_extend)) }
                    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.plan_edit_short)) }
                    TextButton(onClick = onEnd) { Text(stringResource(R.string.plan_end)) }
                }
            }
        }
    }
}

/** In einer leeren Mahlzeit: „Laut Plan: Haferflocken, Milch · 420 kcal“ + „Wie geplant“ / „Anpassen“. */
@Composable
internal fun PlanSuggestionRow(items: List<MealPlanItem>, color: Color, onApply: () -> Unit, onAdjust: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.plan_suggestion, items.joinToString { it.name }),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
        )
        Text(
            stringResource(R.string.kcal_value, items.map { it.nutrients }.sum().kcal.format()),
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Neon.OnNeon),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(stringResource(R.string.plan_apply), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onAdjust) { Text(stringResource(R.string.plan_adjust)) }
        }
    }
}
