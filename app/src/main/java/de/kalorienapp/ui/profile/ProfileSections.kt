package de.kalorienapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.ActivityLevel
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Pace
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.descriptionRes
import de.kalorienapp.ui.components.toInputString
import kotlin.math.roundToInt

typealias FormUpdate = ((ProfileFormState) -> ProfileFormState) -> Unit

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun BodyDataSection(form: ProfileFormState, onUpdate: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            options = Sex.entries,
            selected = form.sex,
            onSelect = { sex -> onUpdate { it.copy(sex = sex) } },
            label = { stringResource(it.labelRes) },
        )
        NumberField(
            value = form.age,
            onValueChange = { v -> onUpdate { it.copy(age = v) } },
            label = stringResource(R.string.profile_age),
            suffix = stringResource(R.string.unit_years),
            isError = form.ageError,
            errorText = stringResource(R.string.error_age),
            decimal = false,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = form.heightCm,
            onValueChange = { v -> onUpdate { it.copy(heightCm = v) } },
            label = stringResource(R.string.profile_height),
            suffix = stringResource(R.string.unit_cm),
            isError = form.heightError,
            errorText = stringResource(R.string.error_height),
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = form.weightKg,
            onValueChange = { v -> onUpdate { it.copy(weightKg = v) } },
            label = stringResource(R.string.profile_weight),
            suffix = stringResource(R.string.unit_kg),
            isError = form.weightError,
            errorText = stringResource(R.string.error_weight),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun GoalSection(form: ProfileFormState, onUpdate: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_goal), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            options = Goal.entries,
            selected = form.goal,
            onSelect = { goal -> onUpdate { it.copy(goal = goal) } },
            label = { stringResource(it.labelRes) },
        )
        if (form.goal != Goal.MAINTAIN) {
            Text(stringResource(R.string.profile_pace), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(
                options = Pace.entries,
                selected = form.pace,
                onSelect = { pace -> onUpdate { it.copy(pace = pace) } },
                label = { stringResource(it.labelRes(form.goal)) },
            )
        }
    }
}

@Composable
fun ActivitySection(form: ProfileFormState, onUpdate: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_health_connect_question), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            options = listOf(true, false),
            selected = form.useHealthConnect,
            onSelect = { use -> onUpdate { it.copy(useHealthConnect = use) } },
            label = { stringResource(if (it) R.string.yes_connect else R.string.no) },
        )
        when (form.useHealthConnect) {
            true -> Text(
                stringResource(R.string.profile_health_connect_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            false -> ActivityLevelPicker(form.activityLevel) { level -> onUpdate { it.copy(activityLevel = level) } }
            null -> Unit
        }
    }
}

@Composable
private fun ActivityLevelPicker(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(stringResource(R.string.profile_activity_level), style = MaterialTheme.typography.labelLarge)
        ActivityLevel.entries.forEach { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = level == selected, onClick = { onSelect(level) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = level == selected, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(level.labelRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(level.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Übersicht der effektiven Tagesziele. */
@Composable
fun TargetsCard(targets: DailyTargets?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.targets_title), style = MaterialTheme.typography.titleMedium)
            if (targets == null) {
                Text(stringResource(R.string.targets_incomplete), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.kcal_value, targets.kcal.format()),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.targets_macros, targets.proteinG, targets.carbsG, targets.fatG),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Feineinstellungen: Protein/kg, manuelle Overrides, Gutschrift aktiver Kalorien. */
@Composable
fun TargetAdjustmentsSection(state: ProfileUiState, onUpdate: FormUpdate) {
    val form = state.form
    val auto = state.autoTargets
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.targets_override_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(
            value = form.proteinPerKg,
            onValueChange = { v -> onUpdate { it.copy(proteinPerKg = v) } },
            label = stringResource(R.string.targets_protein_per_kg),
            suffix = stringResource(R.string.unit_g_per_kg),
            autoValue = state.defaultProteinPerKg.toInputString(),
            isError = form.proteinPerKgError,
            errorText = stringResource(R.string.error_protein_per_kg),
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = form.kcalOverride,
            onValueChange = { v -> onUpdate { it.copy(kcalOverride = v) } },
            label = stringResource(R.string.targets_kcal),
            suffix = stringResource(R.string.unit_kcal),
            autoValue = auto?.kcal?.toString(),
            isError = form.kcalOverrideError,
            errorText = stringResource(R.string.error_kcal_override),
            decimal = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = form.proteinOverride,
                onValueChange = { v -> onUpdate { it.copy(proteinOverride = v) } },
                label = stringResource(R.string.protein),
                suffix = stringResource(R.string.unit_g),
                autoValue = auto?.proteinG?.toString(),
                isError = form.proteinOverrideError,
                decimal = false,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = form.carbsOverride,
                onValueChange = { v -> onUpdate { it.copy(carbsOverride = v) } },
                label = stringResource(R.string.carbs_short),
                suffix = stringResource(R.string.unit_g),
                autoValue = auto?.carbsG?.toString(),
                isError = form.carbsOverrideError,
                decimal = false,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = form.fatOverride,
                onValueChange = { v -> onUpdate { it.copy(fatOverride = v) } },
                label = stringResource(R.string.fat),
                suffix = stringResource(R.string.unit_g),
                autoValue = auto?.fatG?.toString(),
                isError = form.fatOverrideError,
                decimal = false,
                modifier = Modifier.weight(1f),
            )
        }
        // Gutschrift ist nur mit Health Connect relevant.
        if (form.useHealthConnect == true) {
            Text(
                stringResource(R.string.targets_credit_percent, form.creditPercent),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = form.creditPercent.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(creditPercent = (v / 5).roundToInt() * 5) } },
                valueRange = 0f..100f,
                steps = 19,
            )
            Text(
                stringResource(R.string.targets_credit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
