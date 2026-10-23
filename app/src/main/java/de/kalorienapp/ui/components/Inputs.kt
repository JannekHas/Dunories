package de.kalorienapp.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import de.kalorienapp.domain.model.MealType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon

/** Einzeiliges Zahlenfeld (Komma oder Punkt als Dezimaltrenner). */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    /** Automatisch berechneter Wert, der gilt solange das Feld leer ist. */
    autoValue: String? = null,
    /** Nur grauer Platzhalter (ohne „automatisch“-Hinweis). */
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    decimal: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        placeholder = (autoValue ?: placeholder)?.let { { Text(it) } },
        supportingText = when {
            isError && errorText != null -> ({ Text(errorText) })
            value.isBlank() && autoValue != null -> ({ Text(stringResource(R.string.auto_value, autoValue)) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
    )
}

/** Einfachauswahl als Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = neonChipColors(),
            )
        }
    }
}

/** Ausgewählte Chips in Neon-Cyan mit schwarzer Schrift. */
@Composable
fun neonChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Neon.Cyan,
    selectedLabelColor = Neon.OnNeon,
    selectedLeadingIconColor = Neon.OnNeon,
)

/** Auswahl der Mahlzeit mit Überschrift. */
@Composable
fun MealTypePicker(selected: MealType, onSelect: (MealType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.entry_meal), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            options = MealType.entries,
            selected = selected,
            onSelect = onSelect,
            label = { stringResource(it.labelRes) },
        )
    }
}
