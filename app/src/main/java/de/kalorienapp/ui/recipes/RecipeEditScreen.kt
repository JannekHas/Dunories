package de.kalorienapp.ui.recipes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.theme.Neon
import java.io.File

/** Rezept anlegen/bearbeiten bzw. Import-Vorschlag prüfen. Gesamtübersicht der Nährwerte rechnet live mit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecipeEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current

    var editingIngredient by remember { mutableStateOf<Pair<IngredientForm, Boolean>?>(null) }
    var showImageMenu by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setImage)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(viewModel::setImage)
    }
    fun launchCamera() {
        val uri = cameraOutputUri(context)
        cameraUri = uri
        takePhoto.launch(uri)
    }
    // Die App deklariert CAMERA (Ein-Foto-Erfassung) → ohne Erlaubnis würde die System-Kamera abgelehnt.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    editingIngredient?.let { (ingredient, isNew) ->
        IngredientDialog(
            initial = ingredient,
            isNew = isNew,
            onSave = { viewModel.putIngredient(it); editingIngredient = null },
            onDismiss = { editingIngredient = null },
        )
    }
    if (showNewCategory) {
        CategoryNameDialog(
            initial = "",
            title = stringResource(R.string.category_new),
            onSave = { viewModel.toggleNewCategory(it); showNewCategory = false },
            onDismiss = { showNewCategory = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.recipe_discard_title)) },
            text = { Text(stringResource(R.string.recipe_discard_text)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.recipe_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state.fromImport -> R.string.recipe_import_review
                                form.id != 0L -> R.string.recipe_edit
                                else -> R.string.recipe_new
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.fromImport) confirmDiscard = true else onClose() }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = form.isValid && !state.isSaving) {
                        Text(stringResource(R.string.save), color = if (form.isValid) Neon.Cyan else Neon.TextSecondary, fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.fromImport) {
                item(key = "hint") {
                    Card(colors = CardDefaults.cardColors(containerColor = Neon.SurfaceHigh)) {
                        Text(
                            stringResource(R.string.recipe_import_review_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            item(key = "image") {
                Box {
                    Box(
                        Modifier
                            .recipeImageAspect()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showImageMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        RecipeImage(form.toRecipe(), Modifier.fillMaxSize())
                        if (state.imageLoading) CircularProgressIndicator(color = Neon.OnNeon)
                        Text(
                            stringResource(if (form.imagePath == null) R.string.recipe_image_add else R.string.recipe_image_change),
                            style = MaterialTheme.typography.labelLarge,
                            color = Neon.TextPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Neon.Background.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    DropdownMenu(expanded = showImageMenu, onDismissRequest = { showImageMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recipe_image_gallery)) },
                            onClick = {
                                showImageMenu = false
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recipe_image_camera)) },
                            onClick = {
                                showImageMenu = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    launchCamera()
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                        )
                        if (form.imagePath != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipe_image_remove)) },
                                onClick = { showImageMenu = false; viewModel.removeImage() },
                            )
                        }
                    }
                }
            }
            item(key = "name") {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.recipe_name)) },
                    singleLine = true,
                    isError = form.name.isBlank(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "description") {
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.recipe_description)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "numbers") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = form.servings,
                        onValueChange = { v -> viewModel.update { it.copy(servings = v) } },
                        label = stringResource(R.string.recipe_servings_label),
                        isError = form.servingsError || form.servings.isBlank(),
                        errorText = stringResource(R.string.error_servings),
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = form.minutes,
                        onValueChange = { v -> viewModel.update { it.copy(minutes = v) } },
                        label = stringResource(R.string.recipe_total_time),
                        suffix = stringResource(R.string.unit_min),
                        isError = form.minutesError,
                        errorText = stringResource(R.string.error_number),
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "effort") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.recipe_effort), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Effort.entries.forEach { effort ->
                            FilterChip(
                                selected = form.effort == effort,
                                onClick = { viewModel.update { it.copy(effort = if (it.effort == effort) null else effort) } },
                                label = { Text(stringResource(effort.labelRes)) },
                                colors = neonChipColors(),
                            )
                        }
                    }
                }
            }
            item(key = "categories") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.recipe_categories), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = category.id in form.categoryIds,
                                onClick = { viewModel.toggleCategory(category.id) },
                                label = { Text(category.name) },
                                colors = neonChipColors(),
                            )
                        }
                        form.newCategoryNames.forEach { name ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleNewCategory(name) },
                                label = { Text(stringResource(R.string.category_suggested, name)) },
                                colors = neonChipColors(),
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showNewCategory = true },
                            label = { Text(stringResource(R.string.category_new)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            item(key = "overview") { NutritionOverviewCard(form.toRecipe()) }
            item(key = "ingredients_head") {
                Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge)
            }
            itemsIndexed(form.ingredients, key = { _, it -> "ing${it.key}" }) { _, ingredient ->
                IngredientRow(
                    ingredient = ingredient,
                    onClick = { editingIngredient = ingredient to false },
                    onDelete = { viewModel.removeIngredient(ingredient.key) },
                )
            }
            item(key = "ingredient_add") {
                OutlinedButton(onClick = { editingIngredient = viewModel.newIngredient() to true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.ingredient_add), modifier = Modifier.padding(start = 6.dp))
                }
            }
            item(key = "steps_head") {
                Column {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleLarge)
                }
            }
            itemsIndexed(form.steps, key = { _, it -> "step${it.key}" }) { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 14.dp).size(28.dp).clip(CircleShape).background(Neon.Rainbow[index % Neon.Rainbow.size]),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", color = Neon.OnNeon, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedTextField(
                        value = step.text,
                        onValueChange = { viewModel.setStep(step.key, it) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = { viewModel.removeStep(step.key) }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
                    }
                }
            }
            item(key = "step_add") {
                OutlinedButton(onClick = viewModel::addStep, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.recipe_step_add), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: IngredientForm, onClick: () -> Unit, onDelete: () -> Unit) {
    val domain = ingredient.toDomain()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(domain.quantityText(), domain.name.ifBlank { "?" }).filter(String::isNotBlank).joinToString(" "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ingredient.isValid) Neon.TextPrimary else Neon.Red,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (domain.amount != null) {
                        stringResource(R.string.ingredient_summary, domain.amount.format(), stringResource(domain.unit.labelRes), domain.nutrients.kcal.format())
                    } else {
                        stringResource(R.string.ingredient_not_counted)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Neon.TextSecondary,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary) }
        }
    }
}

/** Ziel für Kamerafotos (über FileProvider freigegeben, liegt im Cache). */
private fun cameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "recipe_photo.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
