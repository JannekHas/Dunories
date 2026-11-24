package de.kalorienapp.ui.leftover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.Leftovers
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Dish
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Leftover
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.SettingSwitchRow
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import de.kalorienapp.ui.today.entryValueText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d.M.", Locale.GERMAN)

private val FRACTION_LABELS = listOf("¾", "⅔", "½", "⅓", "¼")

/** Gewählter Anteil (Chip) oder eigene Menge (Feld) – zusammen die gegessene Menge. */
private class PortionState(fraction: Double?, text: String) {
    var fraction by mutableStateOf(fraction)
    var text by mutableStateOf(text)

    fun amount(total: Double): Double? = fraction?.let { total * it } ?: text.parseDecimal()?.takeIf { it > 0 }
}

/** Schnellwahl Alles · ¾ · ⅔ · ½ · ⅓ · ¼ (vom ganzen [total]) plus freie Menge in g/ml. */
@Composable
private fun PortionPicker(title: String, total: Double, unit: AmountUnit, state: PortionState, withAll: Boolean = true) {
    val unitLabel = stringResource(unit.labelRes)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val allLabel = stringResource(R.string.leftover_all)
        ChoiceChips(
            options = if (withAll) Leftovers.FRACTIONS else Leftovers.FRACTIONS.drop(1),
            selected = state.fraction,
            onSelect = { state.fraction = it; state.text = "" },
            label = { f -> if (f == 1.0) allLabel else FRACTION_LABELS[Leftovers.FRACTIONS.indexOf(f) - 1] },
        )
        NumberField(
            value = state.fraction?.let { (total * it).toInputString(0) } ?: state.text,
            onValueChange = { state.fraction = null; state.text = it },
            label = stringResource(R.string.leftover_amount),
            suffix = unitLabel,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.leftover_of_total, total.format(), unitLabel),
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
        )
    }
}

/** Anteil an den Nährwerten: [amount] von [total]. */
private fun share(n: Nutrients, amount: Double, total: Double): Nutrients =
    if (total > 0) n * (amount.coerceIn(0.0, total) / total) else n

/** Mahlzeit-⋮ → „Als Gericht zusammenfassen“: Zutaten wählen, Name, gegessener Anteil. */
@Composable
fun GroupDishDialog(entries: List<FoodEntry>, onConfirm: (List<FoodEntry>, String, Double) -> Unit, onDismiss: () -> Unit) {
    val usable = remember(entries) { Leftovers.groupable(entries) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember(usable) { mutableStateOf(usable.map { it.id }.toSet()) }
    val portion = remember { PortionState(1.0, "") }
    val selected = usable.filter { it.id in selectedIds }
    val components = Leftovers.componentsOf(selected)
    val total = Dish.totalAmount(components)
    val nutrients = Dish.total(components)
    val eaten = portion.amount(total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leftover_group)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.leftover_group_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.leftover_name)) },
                    placeholder = { Text(stringResource(R.string.leftover_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Text(stringResource(R.string.leftover_ingredients), style = MaterialTheme.typography.titleSmall)
                    usable.forEach { entry ->
                        val checked = entry.id in selectedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (checked) selectedIds - entry.id else selectedIds + entry.id
                            },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(entry.name, modifier = Modifier.weight(1f), maxLines = 2)
                            Text(entryValueText(entry.nutrients, entry.isEstimate), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (total > 0) {
                    PortionPicker(stringResource(R.string.leftover_eaten_title), total, Dish.unit(components), portion)
                    eaten?.let { amount ->
                        val mine = entryValueText(share(nutrients, amount, total))
                        Text(
                            if (amount >= total) {
                                stringResource(R.string.leftover_preview_all, mine)
                            } else {
                                stringResource(R.string.leftover_preview, mine, entryValueText(share(nutrients, total - amount, total)))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neon.Cyan,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { eaten?.let { onConfirm(selected, name.trim(), it) } },
                enabled = name.isNotBlank() && selected.size >= 2 && eaten != null,
            ) { Text(stringResource(R.string.leftover_confirm_group)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Eintrags-⋮ → „Nicht alles gegessen“: gegessener Teil bleibt, der Rest wird offen. */
@Composable
fun PartialDialog(entry: FoodEntry, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    val total = entry.amount ?: 0.0
    val portion = remember { PortionState(0.5, "") }
    val eaten = portion.amount(total)?.takeIf { it < total }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leftover_partial)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.leftover_partial_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                PortionPicker(stringResource(R.string.leftover_eaten_title), total, entry.unit, portion, withAll = false)
                eaten?.let { amount ->
                    Text(
                        stringResource(
                            R.string.leftover_preview,
                            entryValueText(share(entry.nutrients, amount, total)),
                            entryValueText(share(entry.nutrients, total - amount, total)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.Cyan,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { eaten?.let(onConfirm) }, enabled = eaten != null) { Text(stringResource(R.string.leftover_keep_rest)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Rest eintragen (ganz oder teilweise), einfrieren oder verwerfen. */
@Composable
fun TakeLeftoverDialog(
    leftover: Leftover,
    onTake: (Double) -> Unit,
    onFreeze: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = leftover.amount
    val portion = remember(leftover.id) {
        val default = Leftovers.defaultPortion(leftover)
        if (default >= total) PortionState(1.0, "") else PortionState(null, default.toInputString(0))
    }
    val amount = portion.amount(total)
    val unitLabel = stringResource(leftover.unit.labelRes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leftover_take_title, leftover.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeftoverInfo(leftover)
                PortionPicker(stringResource(R.string.leftover_take_amount), total, leftover.unit, portion)
                amount?.let { a ->
                    val mine = entryValueText(share(leftover.nutrients, a, total))
                    Text(
                        if (a >= total) {
                            stringResource(R.string.leftover_preview_take_all, mine)
                        } else {
                            stringResource(R.string.leftover_preview_take, mine, (total - a).format(), unitLabel)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.Cyan,
                    )
                }
                SettingSwitchRow(
                    title = stringResource(R.string.leftover_freeze),
                    text = stringResource(R.string.leftover_freeze_hint),
                    checked = leftover.frozen,
                    onChange = onFreeze,
                )
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.leftover_discard), color = Neon.Red) }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onTake) }, enabled = amount != null) { Text(stringResource(R.string.leftover_take)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** „750 g · ≈ 1.200 kcal · von Mo, 23.11.“ (+ „❄ eingefroren“). */
@Composable
fun LeftoverInfo(leftover: Leftover, modifier: Modifier = Modifier) {
    val info = stringResource(
        R.string.leftover_info,
        leftover.amount.format(),
        stringResource(leftover.unit.labelRes),
        entryValueText(leftover.nutrients, leftover.source.isEstimate),
        when (Leftovers.daysOpen(leftover, LocalDate.now())) {
            0L -> stringResource(R.string.leftover_day_today)
            1L -> stringResource(R.string.leftover_day_yesterday)
            else -> leftover.createdDay.format(dayFormatter)
        },
    )
    Text(
        if (leftover.frozen) "$info · ${stringResource(R.string.leftover_frozen_label)}" else info,
        style = MaterialTheme.typography.bodySmall,
        color = Neon.TextSecondary,
        modifier = modifier,
    )
}

/**
 * In einer leeren Mittag-/Abend-Mahlzeit auf „Heute“: „Rest da: Chili“ mit Eintragen und ✕ (heute nicht).
 * Nach [Leftovers.CHECK_AFTER_DAYS] Tagen ohne Einfrieren stattdessen: Noch gut? · Eingefroren · Verwerfen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeftoverSuggestionRow(
    leftovers: List<Leftover>,
    today: LocalDate,
    color: Color,
    onTake: (Leftover) -> Unit,
    onNotToday: (Leftover) -> Unit,
    onStillGood: (Leftover) -> Unit,
    onFreeze: (Leftover) -> Unit,
    onDiscard: (Leftover) -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        leftovers.forEach { leftover ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.leftover_suggestion, leftover.name), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        LeftoverInfo(leftover)
                    }
                    IconButton(onClick = { onNotToday(leftover) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.leftover_not_today), tint = Neon.TextSecondary)
                    }
                }
                if (Leftovers.needsCheck(leftover, today)) {
                    Text(
                        stringResource(R.string.leftover_still_there, Leftovers.daysOpen(leftover, today).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.Yellow,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, end = 12.dp)) {
                        OutlinedButton(onClick = { onStillGood(leftover) }) { Text(stringResource(R.string.leftover_still_good)) }
                        OutlinedButton(onClick = { onFreeze(leftover) }) { Text(stringResource(R.string.leftover_freeze)) }
                        TextButton(onClick = { onDiscard(leftover) }) { Text(stringResource(R.string.leftover_discard), color = Neon.Red) }
                    }
                } else {
                    Button(
                        onClick = { onTake(leftover) },
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Neon.OnNeon),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(stringResource(R.string.leftover_take), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
