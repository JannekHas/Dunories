package de.kalorienapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon
import kotlin.math.roundToLong

/**
 * Zahl groß anzeigen und mit einem Finger einstellen (Einrichtung, Wunsch des Nutzers Nov. 2026):
 * Ziehleiste für den groben Wert, „−“/„+“ für die Feinheit, Antippen der Zahl öffnet die Tastatur.
 *
 * @param slideStep Raster der Ziehleiste (z. B. 0,5 kg), [step] Raster von „−“/„+“ (z. B. 0,1 kg).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ValuePicker(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    slideStep: Double = step,
    decimals: Int = 0,
    /** Bereich der Ziehleiste (typische Werte, damit sie fein genug ist); Eintippen erlaubt den ganzen [range]. */
    sliderRange: ClosedFloatingPointRange<Double> = range,
) {
    fun snap(v: Double, raster: Double) = ((v / raster).roundToLong() * raster).coerceIn(range.start, range.endInclusive)
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (editing) {
            NumberEditor(
                initial = value.toInputString(decimals),
                unit = unit,
                label = label,
                decimal = decimals > 0,
                onDone = { text ->
                    text.parseDecimal()?.let { onValueChange(snap(it, step)) }
                    editing = false
                },
            )
        } else {
            val tapToType = stringResource(R.string.picker_tap_to_type)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = tapToType) { editing = true }
                    .semantics(mergeDescendants = true) { contentDescription = "$label: ${value.toInputString(decimals)} $unit" }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    value.toInputString(decimals),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Neon.TextPrimary,
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Neon.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
            }
            Text(tapToType, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(null, stringResource(R.string.stepper_less)) { onValueChange(snap(value - step, step)) }
            // Handy-Test 26.11.: der schmale Standard-Strich war schwer zu treffen und ruckelte (jeder Schritt lief
            // über den ViewModel-Zustand). Jetzt großer runder Knopf, dicke Leiste, große Tippfläche; beim Ziehen
            // gleitet der Knopf frei mit, nur die Zahl rastet ein.
            var sliding by remember { mutableStateOf<Float?>(null) }
            val interaction = remember { MutableInteractionSource() }
            val colors = SliderDefaults.colors(
                thumbColor = Neon.Cyan,
                activeTrackColor = Neon.Cyan,
                inactiveTrackColor = Neon.Track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            )
            Slider(
                value = sliding ?: value.coerceIn(sliderRange.start, sliderRange.endInclusive).toFloat(),
                onValueChange = {
                    sliding = it
                    onValueChange(snap(it.toDouble(), slideStep))
                },
                onValueChangeFinished = { sliding = null },
                valueRange = sliderRange.start.toFloat()..sliderRange.endInclusive.toFloat(),
                colors = colors,
                interactionSource = interaction,
                thumb = {
                    Box(
                        Modifier
                            .size(30.dp)
                            .shadow(4.dp, CircleShape)
                            .background(Neon.Cyan, CircleShape),
                    )
                },
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors = colors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        modifier = Modifier.height(12.dp),
                    )
                },
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = label },
            )
            StepButton(Icons.Filled.Add, stringResource(R.string.stepper_more)) { onValueChange(snap(value + step, step)) }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector?, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Neon.SurfaceHighest, contentColor = Neon.Cyan),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        // Ohne erweiterte Icon-Bibliothek gibt es kein „Minus“-Symbol → Zeichen wie beim bestehenden Stepper.
        if (icon != null) Icon(icon, contentDescription = null)
        else Text(stringResource(R.string.minus), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Eintippen statt ziehen: Feld bekommt sofort den Fokus, „Fertig“ oder Wegtippen übernimmt. */
@Composable
private fun NumberEditor(initial: String, unit: String, label: String, decimal: Boolean, onDone: (String) -> Unit) {
    // Ganze Zahl markiert → Tippen ersetzt sie (sonst landet der Cursor vorne und hängt an).
    var field by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = field,
        onValueChange = { new ->
            val clean = new.text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(6)
            field = if (clean == new.text) new else TextFieldValue(clean, selection = TextRange(clean.length))
        },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone(field.text) }),
        modifier = Modifier
            .width(220.dp)
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused) hadFocus = true else if (hadFocus) onDone(field.text)
            },
    )
}
