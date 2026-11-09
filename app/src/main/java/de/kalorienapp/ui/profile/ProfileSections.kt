package de.kalorienapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon

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
            options = listOf(Sex.FEMALE, Sex.MALE),
            selected = form.sex,
            onSelect = { sex -> onUpdate { it.copy(sex = sex) } },
            label = { stringResource(it.labelRes) },
        )
        Text(
            stringResource(R.string.profile_sex_info),
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
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

/** Übersicht der effektiven Tagesziele. */
@Composable
fun TargetsCard(targets: DailyTargets?, modifier: Modifier = Modifier, atMinimum: Boolean = false) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.targets_title), style = MaterialTheme.typography.titleMedium)
            if (targets == null) {
                Text(stringResource(R.string.targets_incomplete), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.kcal_value, targets.kcal.format()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Neon.Cyan,
                )
                Text(
                    stringResource(R.string.targets_macros, targets.proteinG, targets.carbsG, targets.fatG),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Ziel rechnerisch unter 1.200 kcal → gedeckelt; sonst wundert man sich über sehr wenig KH.
                if (atMinimum) {
                    Text(
                        stringResource(R.string.targets_at_minimum, GoalCalculator.MIN_KCAL_TARGET.format()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.Yellow,
                    )
                }
            }
        }
    }
}

/** Feineinstellungen: Protein/kg und manuelle Overrides. */
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
    }
}
