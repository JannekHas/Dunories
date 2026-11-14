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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
    // Eigener Text-Zustand (mit Markierung): steht nur eine vorgefertigte 0 im Feld, wird sie beim
    // Hineinspringen markiert – Tippen ersetzt sie (aus „6,5“ wird nicht „6,50“), ohne Eingabe bleibt sie.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var selectZero by remember { mutableStateOf(false) }
    val current = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = current,
        onValueChange = { new ->
            if (selectZero && new.text == current.text) {
                // Antippen setzt nach dem Fokus noch den Cursor – Markierung der 0 behalten
                selectZero = false
                return@OutlinedTextField
            }
            selectZero = false
            val filtered = new.text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }
            field = if (filtered == new.text) new else TextFieldValue(filtered, TextRange(filtered.length))
            if (filtered != value) onValueChange(filtered)
        },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused && isZero(current.text)) {
                field = current.copy(selection = TextRange(0, current.text.length))
                selectZero = true
            } else if (!state.isFocused) {
                selectZero = false
            }
        },
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

private fun isZero(text: String) = text.isNotBlank() && text.replace(',', '.').toDoubleOrNull() == 0.0

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
