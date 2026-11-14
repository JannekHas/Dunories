package de.kalorienapp.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.theme.Neon

/**
 * Gericht bearbeiten: Name, Gesamtmenge (ändert alle Zutaten anteilig), darunter die Zutaten –
 * jede aufklappbar mit dem normalen Lebensmittel-Formular, hinzufügen und löschen.
 */
@Composable
fun DishFormFields(
    dish: DishForm,
    onChange: (DishForm) -> Unit,
    onAddPart: () -> Unit,
    modifier: Modifier = Modifier,
    /** Wird zwischen Name und Menge eingefügt (z. B. Mahlzeit-Auswahl). */
    afterName: @Composable () -> Unit = {},
) {
    val unitLabel = stringResource(dish.unit.labelRes)
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = dish.name,
            onValueChange = { onChange(dish.copy(name = it)) },
            label = { Text(stringResource(R.string.dish_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        afterName()
        NumberField(
            value = dish.amountText,
            onValueChange = { onChange(dish.withAmount(it)) },
            label = stringResource(R.string.dish_amount),
            suffix = unitLabel,
            isError = dish.amountError,
            errorText = stringResource(R.string.error_number),
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.dish_amount_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
        )
        Text(
            stringResource(R.string.dish_components),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        dish.parts.forEach { part ->
            DishPartCard(
                part = part,
                expanded = expanded == part.id,
                onToggle = { expanded = if (expanded == part.id) null else part.id },
                onChange = { onChange(dish.withPart(part.id, it)) },
                onRemove = { onChange(dish.withoutPart(part.id)) },
            )
        }
        OutlinedButton(onClick = onAddPart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.dish_add_component), modifier = Modifier.padding(start = 8.dp))
        }
        DishTotalCard(dish)
    }
}

@Composable
private fun DishPartCard(
    part: DishPart,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (FoodForm) -> Unit,
    onRemove: () -> Unit,
) {
    val form = part.form
    val total = form.totalNutrients()
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(form.name.ifBlank { stringResource(R.string.proposal_unnamed) }, style = MaterialTheme.typography.titleSmall)
                if (total != null && form.amountValue != null) {
                    Text(
                        stringResource(
                            R.string.proposal_item_summary,
                            form.amountValue!!.format(1).trimDecimalZero(),
                            stringResource(form.unit.labelRes),
                            total.kcal.format(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                } else {
                    Text(
                        stringResource(R.string.proposal_amount_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, stringResource(R.string.dish_remove_component))
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (expanded) {
            HorizontalDivider()
            FoodFormFields(form = form, onChange = onChange, modifier = Modifier.padding(16.dp))
        }
    }
}

/** Summe des ganzen Gerichts – das wird gezählt. */
@Composable
private fun DishTotalCard(dish: DishForm) {
    val total = dish.totalNutrients()
    GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.entry_total_for, dish.totalAmount.format(1).trimDecimalZero(), stringResource(dish.unit.labelRes)),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(stringResource(R.string.kcal_value, total.kcal.format()), style = MaterialTheme.typography.headlineSmall)
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
