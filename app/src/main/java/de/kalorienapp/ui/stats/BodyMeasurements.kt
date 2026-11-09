package de.kalorienapp.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.data.repository.BodyMeasurementRepository
import de.kalorienapp.domain.model.BodyMeasurement
import de.kalorienapp.domain.model.MeasureType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BodyMeasurementViewModel(private val repository: BodyMeasurementRepository) : ViewModel() {
    /** Messungen je Art, älteste zuerst. */
    val byType: StateFlow<Map<MeasureType, List<BodyMeasurement>>> = repository.all
        .map { list -> list.groupBy { it.type } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Alle eingegebenen Werte mit demselben Zeitpunkt speichern. */
    fun save(values: Map<MeasureType, Double>) {
        if (values.isEmpty()) return
        val now = Instant.now()
        viewModelScope.launch { repository.add(values.map { (type, value) -> BodyMeasurement(type = type, value = value, measuredAt = now) }) }
    }

    fun delete(item: BodyMeasurement) = viewModelScope.launch { repository.delete(item) }
}

@get:androidx.annotation.StringRes
val MeasureType.labelRes: Int
    get() = when (this) {
        MeasureType.WAIST -> R.string.measure_waist
        MeasureType.HIP -> R.string.measure_hip
        MeasureType.CHEST -> R.string.measure_chest
        MeasureType.UPPER_ARM -> R.string.measure_upper_arm
        MeasureType.THIGH -> R.string.measure_thigh
        MeasureType.BODY_FAT -> R.string.measure_body_fat
    }

private val MeasureType.unitRes: Int get() = if (isPercent) R.string.unit_percent else R.string.unit_cm

private val measuredFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** Körpermaße in der Statistik: je Maß aktueller Wert, Veränderung und aufklappbarer Verlauf. */
@Composable
fun BodyMeasurementsSection(viewModel: BodyMeasurementViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val byType by viewModel.byType.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    if (showDialog) {
        MeasurementDialog(
            latest = byType.mapValues { it.value.last().value },
            onSave = { viewModel.save(it); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.measure_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Magenta, contentColor = Neon.OnNeon),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.weight_add), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (byType.isEmpty()) {
            Text(stringResource(R.string.measure_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            return@Column
        }
        MeasureType.entries.filter { it in byType }.forEach { type ->
            val list = byType.getValue(type)
            val unit = stringResource(type.unitRes)
            val latest = list.last()
            val diff = latest.value - list.first().value
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.clickable { expanded = if (expanded == type.name) null else type.name }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${latest.value.toInputString()} $unit", style = MaterialTheme.typography.titleMedium)
                        if (list.size > 1) {
                            Text(
                                (if (diff > 0) "+" else "") + diff.format(1),
                                style = MaterialTheme.typography.labelMedium,
                                // Weniger Umfang/Körperfett = grün (Muskelaufbau am Arm wäre die Ausnahme – bleibt neutral).
                                color = when {
                                    type == MeasureType.UPPER_ARM -> Neon.TextSecondary
                                    diff <= 0 -> Neon.Green
                                    else -> Neon.Orange
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    AnimatedVisibility(expanded == type.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (list.size > 1) MeasureLine(list)
                            list.reversed().forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        m.measuredAt.atZone(ZoneId.systemDefault()).format(measuredFormatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${m.value.toInputString()} $unit", style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { viewModel.delete(m) }) {
                                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureLine(list: List<BodyMeasurement>) {
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val min = list.minOf { it.value }
        val max = list.maxOf { it.value }
        val range = (max - min).coerceAtLeast(1.0)
        val first = list.first().measuredAt.toEpochMilli()
        val span = (list.last().measuredAt.toEpochMilli() - first).coerceAtLeast(1L).toFloat()
        val pad = 6.dp.toPx()
        fun point(m: BodyMeasurement) = Offset(
            pad + (m.measuredAt.toEpochMilli() - first) / span * (size.width - 2 * pad),
            pad + ((max - m.value) / range).toFloat() * (size.height - 2 * pad),
        )
        val path = Path()
        list.forEachIndexed { i, m -> point(m).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, Neon.Magenta, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        list.forEach { drawCircle(Neon.Magenta, 3.5.dp.toPx(), point(it)) }
    }
}

/** Alle Maße auf einmal eintragen – leere Felder werden übersprungen. */
@Composable
private fun MeasurementDialog(latest: Map<MeasureType, Double>, onSave: (Map<MeasureType, Double>) -> Unit, onDismiss: () -> Unit) {
    val texts = remember { mutableStateMapOf<MeasureType, String>() }
    fun valid(type: MeasureType, text: String): Double? =
        text.parseDecimal()?.takeIf { if (type.isPercent) it in 2.0..70.0 else it in 10.0..250.0 }
    val values = texts.mapNotNull { (type, text) -> valid(type, text)?.let { type to it } }.toMap()
    val hasError = texts.any { (type, text) -> text.isNotBlank() && valid(type, text) == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measure_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.measure_add_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                MeasureType.entries.forEach { type ->
                    val text = texts[type].orEmpty()
                    NumberField(
                        value = text,
                        onValueChange = { texts[type] = it },
                        label = stringResource(type.labelRes),
                        suffix = stringResource(type.unitRes),
                        // Letzter Wert als Orientierung im Platzhalter.
                        placeholder = latest[type]?.toInputString(),
                        isError = text.isNotBlank() && valid(type, text) == null,
                        errorText = stringResource(R.string.error_number),
                        imeAction = if (type == MeasureType.entries.last()) ImeAction.Done else ImeAction.Next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }, enabled = values.isNotEmpty() && !hasError) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
