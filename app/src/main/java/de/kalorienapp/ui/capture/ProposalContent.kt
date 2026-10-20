package de.kalorienapp.ui.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.components.MealTypePicker
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.food.FoodForm
import de.kalorienapp.ui.food.FoodFormFields
import de.kalorienapp.ui.food.trimDecimalZero

/** Bearbeitbarer Vorschlag: Komponenten anpassen, hinzufügen/entfernen, dann alle eintragen. */
@Composable
fun ProposalContent(
    proposal: CaptureStep.Proposal,
    mealType: MealType,
    viewModel: CaptureViewModel,
    onSaved: () -> Unit,
    modifier: Modifier,
) {
    val isAi = proposal.items.any { it.source == EntrySource.AI_PHOTO || it.source == EntrySource.AI_TEXT }
    Column(modifier.imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAi) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.proposal_ai_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "meal") { MealTypePicker(mealType, viewModel::onMealTypeChange) }
            items(proposal.items, key = { it.id }) { item ->
                ProposalItemCard(
                    item = item,
                    expanded = item.id == proposal.expandedId,
                    onToggle = { viewModel.toggleExpanded(item.id) },
                    onChange = { viewModel.updateItem(item.id, it) },
                    onRemove = { viewModel.removeItem(item.id) },
                )
            }
            item(key = "add") {
                OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.proposal_add_item), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = proposal.items.mapNotNull { it.form.totalNutrients() }.sum()
                Text(
                    stringResource(R.string.proposal_total, total.kcal.format()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(
                    onClick = { viewModel.saveAll(onSaved) },
                    enabled = proposal.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.proposal_save, proposal.items.size))
                }
            }
        }
    }
}

@Composable
private fun ProposalItemCard(
    item: ProposalItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (FoodForm) -> Unit,
    onRemove: () -> Unit,
) {
    val form = item.form
    val total = form.totalNutrients()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    form.name.ifBlank { stringResource(R.string.proposal_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                val unit = stringResource(form.unit.labelRes)
                if (total != null && form.amountValue != null) {
                    Text(
                        stringResource(
                            R.string.proposal_item_summary,
                            form.amountValue!!.format(1).trimDecimalZero(),
                            unit,
                            total.kcal.format(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.proposal_amount_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(item.source.proposalLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, stringResource(R.string.proposal_remove_item))
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

private val EntrySource.proposalLabelRes: Int
    get() = when (this) {
        EntrySource.BARCODE -> R.string.source_barcode
        EntrySource.AI_PHOTO -> R.string.source_ai_photo
        EntrySource.AI_TEXT -> R.string.source_ai_text
        EntrySource.MANUAL -> R.string.source_manual
    }
