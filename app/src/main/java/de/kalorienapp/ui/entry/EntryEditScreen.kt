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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NumberField
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
            NumberField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange,
                label = stringResource(R.string.entry_amount),
                suffix = stringResource(R.string.unit_g),
                isError = state.amountError,
                errorText = stringResource(R.string.error_number),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = state.kcal,
                onValueChange = { viewModel.onNutrientChange(NutrientField.KCAL, it) },
                label = stringResource(R.string.entry_kcal),
                suffix = stringResource(R.string.unit_kcal),
                isError = state.kcalError,
                errorText = stringResource(R.string.error_number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = state.protein,
                    onValueChange = { viewModel.onNutrientChange(NutrientField.PROTEIN, it) },
                    label = stringResource(R.string.protein),
                    suffix = stringResource(R.string.unit_g),
                    isError = state.proteinError,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.carbs,
                    onValueChange = { viewModel.onNutrientChange(NutrientField.CARBS, it) },
                    label = stringResource(R.string.carbs_short),
                    suffix = stringResource(R.string.unit_g),
                    isError = state.carbsError,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.fat,
                    onValueChange = { viewModel.onNutrientChange(NutrientField.FAT, it) },
                    label = stringResource(R.string.fat),
                    suffix = stringResource(R.string.unit_g),
                    isError = state.fatError,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.entry_scale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
