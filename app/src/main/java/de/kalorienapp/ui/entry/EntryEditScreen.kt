package de.kalorienapp.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    onDone: () -> Unit,
    viewModel: EntryEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.entry_new_title else R.string.entry_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onDone) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val unitLabel = stringResource(state.unit.labelRes)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.entry_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.entry_meal), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(
                options = MealType.entries,
                selected = state.mealType,
                onSelect = viewModel::onMealTypeChange,
                label = { stringResource(it.labelRes) },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    value = state.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = stringResource(R.string.entry_amount),
                    suffix = unitLabel,
                    isError = state.amountError,
                    errorText = stringResource(R.string.error_number),
                    modifier = Modifier.weight(1f),
                )
                AmountUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == state.unit,
                        onClick = { viewModel.onUnitChange(unit) },
                        label = { Text(stringResource(unit.labelRes)) },
                    )
                }
            }

            Text(
                stringResource(R.string.entry_per_100, unitLabel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            NutrientInput(state, NutrientField.KCAL, viewModel, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientInput(state, NutrientField.PROTEIN, viewModel, Modifier.weight(1f))
                NutrientInput(state, NutrientField.CARBS, viewModel, Modifier.weight(1f))
                NutrientInput(state, NutrientField.FAT, viewModel, Modifier.weight(1f))
            }

            TextButton(onClick = viewModel::toggleExtras) {
                Icon(
                    if (state.showExtras) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
                Text(stringResource(R.string.entry_more_nutrients), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.showExtras) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutrientInput(state, NutrientField.SATURATED_FAT, viewModel, Modifier.weight(1f))
                    NutrientInput(state, NutrientField.SUGAR, viewModel, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutrientInput(state, NutrientField.FIBER, viewModel, Modifier.weight(1f))
                    NutrientInput(state, NutrientField.SALT, viewModel, Modifier.weight(1f))
                }
            }

            TotalCard(state.amountValue, unitLabel, state.totalNutrients())

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun NutrientInput(
    state: EntryFormState,
    field: NutrientField,
    viewModel: EntryEditViewModel,
    modifier: Modifier,
) {
    NumberField(
        value = state.value(field),
        onValueChange = { viewModel.onNutrientChange(field, it) },
        label = stringResource(field.labelRes),
        suffix = stringResource(if (field == NutrientField.KCAL) R.string.unit_kcal else R.string.unit_g),
        isError = state.isError(field),
        errorText = if (field == NutrientField.KCAL) stringResource(R.string.error_number) else null,
        modifier = modifier,
    )
}

/** Summe für die eingegebene Menge – das wird tatsächlich gezählt. */
@Composable
private fun TotalCard(amount: Double?, unitLabel: String, total: Nutrients?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (amount == null || total == null) {
                Text(stringResource(R.string.entry_total_missing_amount), style = MaterialTheme.typography.bodyMedium)
                return@Card
            }
            Text(
                stringResource(R.string.entry_total_for, amount.format(1).trimDecimalZero(), unitLabel),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.kcal_value, total.kcal.format()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(
                    R.string.entry_macros,
                    total.proteinG.format(1).trimDecimalZero(),
                    total.carbsG.format(1).trimDecimalZero(),
                    total.fatG.format(1).trimDecimalZero(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun String.trimDecimalZero() = removeSuffix(",0")

private val NutrientField.labelRes: Int
    get() = when (this) {
        NutrientField.KCAL -> R.string.entry_kcal
        NutrientField.PROTEIN -> R.string.protein
        NutrientField.CARBS -> R.string.carbs_short
        NutrientField.FAT -> R.string.fat
        NutrientField.SATURATED_FAT -> R.string.nutrient_saturated_fat
        NutrientField.SUGAR -> R.string.nutrient_sugar
        NutrientField.FIBER -> R.string.nutrient_fiber
        NutrientField.SALT -> R.string.nutrient_salt
    }
