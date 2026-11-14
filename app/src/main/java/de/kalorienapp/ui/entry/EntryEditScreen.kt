package de.kalorienapp.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.MealTypePicker
import de.kalorienapp.ui.food.DishFormFields
import de.kalorienapp.ui.food.FoodFormFields
import de.kalorienapp.ui.theme.Neon

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
                    Text(stringResource(if (state.dish != null) R.string.dish_edit_title else if (state.isNew) R.string.entry_new_title else R.string.entry_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    // Speichern von jeder Scrollposition aus (Tester-Wunsch), zusätzlich zum Knopf unten.
                    IconButton(onClick = { viewModel.save(onDone) }, enabled = state.isValid) {
                        Icon(
                            Icons.Filled.Check,
                            stringResource(R.string.save),
                            tint = if (state.isValid) Neon.Cyan else Neon.TextSecondary,
                        )
                    }
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
            val dish = state.dish
            if (dish != null) {
                DishFormFields(
                    dish = dish,
                    onChange = viewModel::onDishChange,
                    onAddPart = viewModel::addDishPart,
                    afterName = { MealTypePicker(state.mealType, viewModel::onMealTypeChange) },
                )
            } else {
                FoodFormFields(
                    form = state.form,
                    onChange = viewModel::onFormChange,
                    afterName = { MealTypePicker(state.mealType, viewModel::onMealTypeChange) },
                )
            }
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            if (state.dish != null) {
                TextButton(onClick = { viewModel.splitDish(onDone) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dish_split))
                }
            }
        }
    }
}
