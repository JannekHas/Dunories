package de.kalorienapp.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import de.kalorienapp.ui.theme.Neon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.components.MealTypePicker
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.food.DishForm
import de.kalorienapp.ui.food.DishFormFields
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
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // Weiteres Foto: für den Vorschlag (null) oder als Zutat eines Gerichts (dessen id).
    var photoTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    val launcher = rememberAddPhotoLauncher { uri, label -> viewModel.addPhoto(uri, photoTarget, label) }
    Column(modifier.imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Falsch erkannt? Ganz oben, eingeklappt: Hinweis tippen oder sprechen → KI schätzt neu.
            if (isAi) item(key = "refine") { RefineCard(onRefine = viewModel::refine) }
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
                    onDishChange = { viewModel.updateDish(item.id, it) },
                    onAddDishPart = { viewModel.addDishPart(item.id) },
                    addPartButton = {
                        AddWithPhotoButton(
                            text = stringResource(R.string.dish_add_component),
                            enabled = !ui.adding,
                            launcher = AddPhotoLauncher(
                                camera = { photoTarget = item.id; launcher.camera(it) },
                                gallery = { photoTarget = item.id; launcher.gallery() },
                            ),
                            onType = { viewModel.addDishPart(item.id) },
                        )
                    },
                    onSplitDish = { viewModel.splitDish(item.id) },
                    onRemove = { viewModel.removeItem(item.id) },
                )
            }
            item(key = "add") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.adding) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan,
                            )
                            Text(stringResource(R.string.add_photo_running), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    ui.addError?.let { error ->
                        Text(
                            stringResource(if (error == CaptureError.NO_FOOD) R.string.add_photo_nothing else error.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    AddWithPhotoButton(
                        text = stringResource(R.string.proposal_add_item),
                        enabled = !ui.adding,
                        launcher = AddPhotoLauncher(
                            camera = { photoTarget = null; viewModel.clearAddError(); launcher.camera(it) },
                            gallery = { photoTarget = null; viewModel.clearAddError(); launcher.gallery() },
                        ),
                        onType = viewModel::addItem,
                    )
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = proposal.items.mapNotNull { it.totalNutrients() }.sum()
                Text(
                    stringResource(R.string.proposal_total, total.kcal.format()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(
                    onClick = { viewModel.saveAll(onSaved) },
                    enabled = proposal.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (viewModel.planMode) R.string.proposal_save_plan else R.string.proposal_save, proposal.items.size))
                }
            }
        }
    }
}

@Composable
private fun RefineCard(onRefine: (String) -> Unit) {
    var hint by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Neon.Cyan)
            Text(
                stringResource(R.string.refine_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.refine_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    placeholder = { Text(stringResource(R.string.refine_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpeechButton(text = hint, onChange = { hint = it }, separator = " ")
                Button(
                    onClick = { onRefine(hint) },
                    enabled = hint.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                    Text(stringResource(R.string.refine_button), modifier = Modifier.padding(start = 8.dp))
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
    onDishChange: (DishForm) -> Unit,
    onAddDishPart: () -> Unit,
    addPartButton: @Composable () -> Unit,
    onSplitDish: () -> Unit,
    onRemove: () -> Unit,
) {
    val form = item.form
    val dish = item.dish
    val total = item.totalNutrients()
    val name = dish?.name ?: form.name
    val amount = if (dish != null) dish.totalAmount.takeIf { it > 0 } else form.amountValue
    val unitRes = (dish?.unit ?: form.unit).labelRes
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { stringResource(R.string.proposal_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                val unit = stringResource(unitRes)
                if (total != null && amount != null) {
                    Text(
                        stringResource(
                            R.string.proposal_item_summary,
                            amount.format(1).trimDecimalZero(),
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
                    if (dish != null) {
                        stringResource(R.string.dish_source_line, stringResource(item.source.proposalLabelRes), dish.parts.size)
                    } else if (item.source == EntrySource.BARCODE && item.barcode != null) {
                        // Aus den eigenen Barcodes, nicht aus Open Food Facts
                        stringResource(R.string.proposal_source_own_barcode)
                    } else {
                        stringResource(item.source.proposalLabelRes)
                    },
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
            if (dish != null) {
                DishFormFields(
                    dish = dish,
                    onChange = onDishChange,
                    onAddPart = onAddDishPart,
                    modifier = Modifier.padding(16.dp),
                    addPartButton = addPartButton,
                )
                TextButton(onClick = onSplitDish, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.dish_split))
                }
            } else {
                FoodFormFields(form = form, onChange = onChange, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private val EntrySource.proposalLabelRes: Int
    get() = when (this) {
        EntrySource.BARCODE -> R.string.source_barcode
        EntrySource.AI_PHOTO -> R.string.source_ai_photo
        EntrySource.AI_TEXT -> R.string.source_ai_text
        EntrySource.MANUAL -> R.string.source_manual
        EntrySource.RECIPE -> R.string.source_recipe
        EntrySource.LABEL -> R.string.source_label
        EntrySource.PLAN -> R.string.source_plan
    }
