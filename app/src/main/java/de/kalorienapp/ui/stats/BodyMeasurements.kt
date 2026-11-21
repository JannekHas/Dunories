package de.kalorienapp.ui.stats

import de.kalorienapp.domain.model.MeasureUnit
import de.kalorienapp.data.AppSettings
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

class BodyMeasurementViewModel(
    private val repository: BodyMeasurementRepository,
    settings: AppSettings,
) : ViewModel() {
    /** Körperanalyse an → Körperfett wandert aus den Körpermaßen in die Körperanalyse. */
    val analysisEnabled: StateFlow<Boolean> = settings.bodyAnalysisEnabled

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
        MeasureType.MUSCLE_MASS -> R.string.measure_muscle_mass
        MeasureType.BODY_WATER -> R.string.measure_body_water
        MeasureType.VISCERAL_FAT -> R.string.measure_visceral_fat
        MeasureType.BONE_MASS -> R.string.measure_bone_mass
        MeasureType.BMR -> R.string.measure_bmr
    }

/** Einheit zum Anzeigen; viszerales Fett ist eine Stufe ohne Einheit (leerer Text). */
val MeasureType.unitRes: Int
    get() = when (unit) {
        MeasureUnit.CM -> R.string.unit_cm
        MeasureUnit.PERCENT -> R.string.unit_percent
        MeasureUnit.KG -> R.string.unit_kg
        MeasureUnit.KCAL -> R.string.unit_kcal
        MeasureUnit.LEVEL -> R.string.unit_level
    }

private val measuredFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** Körpermaße in der Statistik: je Maß aktueller Wert, Veränderung und aufklappbarer Verlauf. */
@Composable
fun BodyMeasurementsSection(viewModel: BodyMeasurementViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val byType by viewModel.byType.collectAsStateWithLifecycle()
    val analysis by viewModel.analysisEnabled.collectAsStateWithLifecycle()
    val types = MeasureType.measures(analysis)
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        MeasurementDialog(
            types = types,
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
        if (types.none { it in byType }) {
            Text(stringResource(R.string.measure_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            return@Column
        }
        MeasureCards(types, byType, onDelete = { viewModel.delete(it) })
    }
}

/**
 * Körperanalyse (Bioimpedanz-Waage, Schalter „Körperanalyse“): Werte mit Verlauf, „Eintragen“ öffnet die
 * Eingabe-Seite (eintippen, Foto vom Display/Ausdruck oder einsprechen). Nur Anzeige – das Tagesziel
 * rechnet nicht damit (Entscheidung des Nutzers, Nov. 2026); Körperfett wirkt wie bisher aufs Protein.
 */
@Composable
fun BodyAnalysisSection(onAdd: () -> Unit, viewModel: BodyMeasurementViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val byType by viewModel.byType.collectAsStateWithLifecycle()
    val analysis by viewModel.analysisEnabled.collectAsStateWithLifecycle()
    if (!analysis) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.body_analysis_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.weight_add), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (MeasureType.ANALYSIS.none { it in byType }) {
            Text(stringResource(R.string.body_analysis_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            return@Column
        }
        MeasureCards(MeasureType.ANALYSIS, byType, onDelete = { viewModel.delete(it) })
    }
}

/** Je Messart eine Karte: letzter Wert, Veränderung (Farbe nach [MeasureType.goodDirection]), aufklappbar mit Verlauf. */
@Composable
private fun MeasureCards(types: List<MeasureType>, byType: Map<MeasureType, List<BodyMeasurement>>, onDelete: (BodyMeasurement) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    types.filter { it in byType }.forEach { type ->
        val list = byType.getValue(type)
        val unit = stringResource(type.unitRes)
        val latest = list.last()
        val diff = latest.value - list.first().value
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.clickable { expanded = if (expanded == type.name) null else type.name }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${latest.value.toInputString()} $unit".trim(), style = MaterialTheme.typography.titleMedium)
                    if (list.size > 1) {
                        Text(
                            (if (diff > 0) "+" else "") + diff.format(1),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                type.goodDirection == 0 || diff == 0.0 -> Neon.TextSecondary
                                diff * type.goodDirection > 0 -> Neon.Green
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
                                Text("${m.value.toInputString()} $unit".trim(), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { onDelete(m) }) {
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
private fun MeasurementDialog(
    types: List<MeasureType>,
    latest: Map<MeasureType, Double>,
    onSave: (Map<MeasureType, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val texts = remember { mutableStateMapOf<MeasureType, String>() }
    fun valid(type: MeasureType, text: String): Double? = text.parseDecimal()?.takeIf(type::isValid)
    val values = texts.mapNotNull { (type, text) -> valid(type, text)?.let { type to it } }.toMap()
    val hasError = texts.any { (type, text) -> text.isNotBlank() && valid(type, text) == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measure_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.measure_add_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                types.forEach { type ->
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
                        imeAction = if (type == types.last()) ImeAction.Done else ImeAction.Next,
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
