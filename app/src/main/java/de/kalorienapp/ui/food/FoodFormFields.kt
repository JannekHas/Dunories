package de.kalorienapp.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.ui.components.unitRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.components.labelRes

/** Name, Menge + Einheit, Nährwerte pro 100 in Etikett-Reihenfolge und die gezählte Summe. */
@Composable
fun FoodFormFields(
    form: FoodForm,
    onChange: (FoodForm) -> Unit,
    modifier: Modifier = Modifier,
    /** Wird zwischen Name und Menge eingefügt (z. B. Mahlzeit-Auswahl). */
    afterName: @Composable () -> Unit = {},
    /** Beschriftung des Mengenfelds (Rezeptzutaten: „Gewicht für Nährwerte“). */
    amountLabel: String = stringResource(R.string.entry_amount),
) {
    val unitLabel = stringResource(form.unit.labelRes)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { onChange(form.copy(name = it)) },
            label = { Text(stringResource(R.string.entry_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        afterName()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                value = form.amount,
                onValueChange = { onChange(form.copy(amount = it)) },
                label = amountLabel,
                suffix = unitLabel,
                isError = form.amountError,
                errorText = stringResource(R.string.error_number),
                modifier = Modifier.weight(1f),
            )
            AmountUnit.entries.forEach { unit ->
                FilterChip(
                    selected = unit == form.unit,
                    onClick = { onChange(form.copy(unit = unit)) },
                    label = { Text(stringResource(unit.labelRes)) },
                    colors = neonChipColors(),
                )
            }
        }
        Text(
            stringResource(R.string.entry_per_100, unitLabel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        NutrientField.entries.forEach { field ->
            NumberField(
                value = form.value(field),
                onValueChange = { onChange(form.withNutrient(field, it)) },
                label = stringResource(field.labelRes),
                suffix = stringResource(if (field == NutrientField.KCAL) R.string.unit_kcal else R.string.unit_g),
                isError = form.isError(field),
                errorText = stringResource(R.string.error_number),
                imeAction = if (field == NutrientField.entries.last()) ImeAction.Done else ImeAction.Next,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (field.subItem) 24.dp else 0.dp),
            )
        }
        MicroFields(form, onChange, unitLabel)
        TotalCard(form.amountValue, unitLabel, form.totalNutrients())
    }
}

/** Summe für die eingegebene Menge – das wird tatsächlich gezählt. */
@Composable
private fun TotalCard(amount: Double?, unitLabel: String, total: Nutrients?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
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

internal fun String.trimDecimalZero() = removeSuffix(",0")

private val NutrientField.labelRes: Int
    get() = when (this) {
        NutrientField.KCAL -> R.string.nutrient_energy
        NutrientField.FAT -> R.string.fat
        NutrientField.SATURATED_FAT -> R.string.nutrient_saturated_fat
        NutrientField.CARBS -> R.string.carbs
        NutrientField.SUGAR -> R.string.nutrient_sugar
        NutrientField.FIBER -> R.string.nutrient_fiber
        NutrientField.PROTEIN -> R.string.nutrient_protein
        NutrientField.SALT -> R.string.nutrient_salt
    }

/** Aufklappbar: Vitamine/Mineralstoffe pro 100 g/ml – offen, wenn schon Werte da sind (KI/Barcode). */
@Composable
private fun MicroFields(form: FoodForm, onChange: (FoodForm) -> Unit, unitLabel: String) {
    var open by rememberSaveable { mutableStateOf(form.micros.values.any { it.isNotBlank() }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.micros_section, unitLabel),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (open) {
            Micro.entries.forEach { micro ->
                NumberField(
                    value = form.microValue(micro),
                    onValueChange = { onChange(form.withMicro(micro, it)) },
                    label = stringResource(micro.labelRes),
                    suffix = stringResource(micro.unitRes),
                    isError = form.isMicroError(micro),
                    errorText = stringResource(R.string.error_number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
