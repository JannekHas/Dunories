package de.kalorienapp.ui.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.MealCombo
import de.kalorienapp.domain.model.FrequentFood
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.food.trimDecimalZero

/** Favoriten und häufig Gegessenes auswählen (Mehrfachauswahl) → bearbeitbarer Vorschlag. */
@Composable
fun QuickPickContent(
    combos: List<MealCombo>,
    onRemoveCombo: (Long) -> Unit,
    favorites: List<FavoriteFood>,
    frequent: List<FrequentFood>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onRemoveFavorite: (Long) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (combos.isNotEmpty()) {
                item(key = "comboHeader") { SectionHeader(stringResource(R.string.quick_combos)) }
                items(combos, key = { CaptureViewModel.comboKey(it) }) { combo ->
                    val key = CaptureViewModel.comboKey(combo)
                    ListItem(
                        modifier = Modifier.clickable { onToggle(key) },
                        leadingContent = { Checkbox(checked = key in selected, onCheckedChange = { onToggle(key) }) },
                        headlineContent = { Text(combo.name) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.quick_combo_summary, combo.items.joinToString(", ") { it.name }, combo.totalKcal.format()),
                                maxLines = 2,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveCombo(combo.id) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.quick_remove_combo))
                            }
                        },
                    )
                }
            }
            if (favorites.isEmpty() && frequent.isEmpty() && combos.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.quick_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (favorites.isNotEmpty()) {
                item(key = "favHeader") { SectionHeader(stringResource(R.string.quick_favorites)) }
                items(favorites, key = { CaptureViewModel.favoriteKey(it) }) { fav ->
                    val key = CaptureViewModel.favoriteKey(fav)
                    QuickPickRow(
                        name = fav.name,
                        amount = fav.amount,
                        unit = fav.unit,
                        per100 = fav.per100,
                        extra = null,
                        checked = key in selected,
                        onToggle = { onToggle(key) },
                        trailing = {
                            IconButton(onClick = { onRemoveFavorite(fav.id) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.quick_remove_favorite))
                            }
                        },
                    )
                }
            }
            if (frequent.isNotEmpty()) {
                item(key = "freqHeader") { SectionHeader(stringResource(R.string.quick_frequent)) }
                items(frequent, key = { CaptureViewModel.frequentKey(it) }) { food ->
                    val key = CaptureViewModel.frequentKey(food)
                    QuickPickRow(
                        name = food.name,
                        amount = food.amount,
                        unit = food.unit,
                        per100 = food.per100,
                        extra = stringResource(R.string.quick_times_eaten, food.timesEaten),
                        checked = key in selected,
                        onToggle = { onToggle(key) },
                        trailing = null,
                    )
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = onConfirm,
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.quick_confirm, selected.size))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun QuickPickRow(
    name: String,
    amount: Double,
    unit: AmountUnit,
    per100: Nutrients,
    extra: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val kcal = per100.per100ToAmount(amount).kcal
    val summary = stringResource(
        R.string.proposal_item_summary,
        amount.format(1).trimDecimalZero(),
        stringResource(unit.labelRes),
        kcal.format(),
    )
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        headlineContent = { Text(name) },
        supportingContent = { Text(if (extra != null) "$summary · $extra" else summary) },
        trailingContent = trailing,
    )
}
