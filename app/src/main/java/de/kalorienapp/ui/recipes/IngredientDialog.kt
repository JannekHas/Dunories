package de.kalorienapp.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.kalorienapp.R
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.food.FoodFormFields
import de.kalorienapp.ui.theme.Neon

/**
 * Zutat bearbeiten: Menge wie im Rezept („2 Stück“), Gewicht für die Nährwerte und
 * Nährwerte pro 100 g/ml in Etikett-Reihenfolge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientDialog(
    initial: IngredientForm,
    isNew: Boolean,
    onSave: (IngredientForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    val units = stringArrayResource(R.array.ingredient_units)
    val weightLinked = IngredientForm.weightUnitOf(form.quantityUnit) != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (isNew) R.string.ingredient_add else R.string.ingredient_edit)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    },
                    actions = {
                        TextButton(onClick = { onSave(form) }, enabled = form.isValid) {
                            Text(stringResource(R.string.ingredient_apply), color = if (form.isValid) Neon.Cyan else Neon.TextSecondary)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                FoodFormFields(
                    form = form.food,
                    onChange = { food ->
                        // Bei g/ml sind Rezeptmenge und Gewicht dasselbe → beide Richtungen synchron halten.
                        form = if (weightLinked && food.amount != form.food.amount) {
                            form.copy(food = food, quantity = food.amount)
                        } else {
                            form.copy(food = food)
                        }
                    },
                    amountLabel = stringResource(R.string.ingredient_weight),
                    afterName = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    value = form.quantity,
                                    onValueChange = { form = form.withQuantity(it, form.quantityUnit) },
                                    label = stringResource(R.string.ingredient_quantity),
                                    isError = form.quantityError,
                                    errorText = stringResource(R.string.error_number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = form.quantityUnit,
                                    onValueChange = { form = form.withQuantity(form.quantity, it) },
                                    label = { Text(stringResource(R.string.ingredient_unit)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                units.forEach { unit ->
                                    FilterChip(
                                        selected = form.quantityUnit.equals(unit, ignoreCase = true),
                                        onClick = { form = form.withQuantity(form.quantity, unit) },
                                        label = { Text(unit) },
                                        colors = neonChipColors(),
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = form.note,
                                onValueChange = { form = form.copy(note = it) },
                                label = { Text(stringResource(R.string.ingredient_note)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(if (weightLinked) R.string.ingredient_weight_linked else R.string.ingredient_weight_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Neon.TextSecondary,
                            )
                        }
                    },
                )
            }
        }
    }
}
