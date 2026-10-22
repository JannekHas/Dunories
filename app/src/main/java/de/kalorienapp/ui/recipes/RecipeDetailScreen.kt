package de.kalorienapp.ui.recipes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.domain.RecipeCalculator
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.data.remote.social.SocialPageParser
import de.kalorienapp.data.remote.social.SocialPlatform
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.MealTypePicker
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val recipe: Recipe? = null,
    val categories: List<RecipeCategory> = emptyList(),
)

class RecipeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val entryRepository: FoodEntryRepository,
) : ViewModel() {
    private val id: Long = savedStateHandle["id"] ?: 0L

    val uiState: StateFlow<RecipeDetailUiState> = combine(repository.recipe(id), repository.categories) { recipe, categories ->
        RecipeDetailUiState(isLoading = false, recipe = recipe, categories = categories.filter { recipe != null && it.id in recipe.categoryIds })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun log(portions: Double, mealType: MealType, onDone: () -> Unit) {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch {
            entryRepository.save(RecipeCalculator.toFoodEntry(recipe, portions, LocalDate.now(), mealType))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch {
            repository.delete(recipe)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (Recipe) -> Unit,
    viewModel: RecipeDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recipe = state.recipe
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var servings by rememberSaveable(recipe?.id, recipe?.servings) { mutableIntStateOf(recipe?.safeServings ?: 1) }
    var showLog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val loggedMessage = stringResource(R.string.recipe_logged)

    if (showLog && recipe != null) {
        LogRecipeDialog(
            onLog = { portions, meal ->
                showLog = false
                viewModel.log(portions, meal) { scope.launch { snackbar.showSnackbar(loggedMessage) } }
            },
            onDismiss = { showLog = false },
        )
    }
    if (confirmDelete && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_text, recipe.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = { onEdit(recipe) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
        bottomBar = {
            if (recipe != null) {
                Surface(color = Neon.Surface) {
                    Button(
                        onClick = { showLog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.Yellow, contentColor = Neon.OnNeon),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.recipe_log), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (recipe == null) {
            if (!state.isLoading) Text(stringResource(R.string.recipe_not_found), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val factor = RecipeCalculator.scaleFactor(recipe, servings)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "image") {
                RecipeImage(recipe, Modifier.recipeImageAspect().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)))
            }
            item(key = "head") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    RecipeMetaLine(recipe)
                    if (state.categories.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.categories.forEach { CategoryTag(it.name) }
                        }
                    }
                    if (recipe.description.isNotBlank()) {
                        Text(recipe.description, style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                    }
                }
            }
            item(key = "nutrition") { NutritionOverviewCard(recipe, Modifier.padding(horizontal = 16.dp)) }
            item(key = "ingredients_head") {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ServingsStepper(servings, onChange = { servings = it })
                }
            }
            itemsIndexed(recipe.ingredients, key = { index, _ -> "ing$index" }) { _, ingredient ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ingredient.quantityText(factor),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Neon.Cyan,
                        modifier = Modifier.width(96.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        ingredient.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary) }
                    }
                    if (ingredient.amount != null) {
                        Text(
                            stringResource(R.string.kcal_value, (ingredient.nutrients.kcal * factor).format()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Neon.TextSecondary,
                        )
                    }
                }
            }
            if (recipe.steps.isNotEmpty()) {
                item(key = "steps_head") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        HorizontalDivider(Modifier.padding(bottom = 12.dp))
                        Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleLarge)
                    }
                }
                itemsIndexed(recipe.steps, key = { index, _ -> "step$index" }) { index, step ->
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Neon.Rainbow[index % Neon.Rainbow.size]),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", color = Neon.OnNeon, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    }
                }
            }
            recipe.sourceUrl?.let { url ->
                item(key = "source") {
                    OutlinedButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                when (SocialPageParser.platformOf(url)) {
                                    SocialPlatform.TIKTOK -> R.string.recipe_source_tiktok
                                    SocialPlatform.INSTAGRAM -> R.string.recipe_source_instagram
                                    SocialPlatform.OTHER -> R.string.recipe_source_link
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryTag(name: String) {
    Surface(color = Neon.Violet, contentColor = Neon.OnNeon, shape = RoundedCornerShape(50)) {
        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun ServingsStepper(value: Int, onChange: (Int) -> Unit) {
    val colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon.SurfaceHighest, contentColor = Neon.TextPrimary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, colors = colors, modifier = Modifier.size(32.dp)) {
            Text(stringResource(R.string.minus), fontWeight = FontWeight.Bold)
        }
        Text(
            androidx.compose.ui.res.pluralStringResource(R.plurals.recipe_servings, value, value),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        FilledIconButton(onClick = { onChange((value + 1).coerceAtMost(50)) }, colors = colors, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Rezept ins heutige Tagebuch eintragen: Portionen (auch halbe) und Mahlzeit. */
@Composable
private fun LogRecipeDialog(onLog: (Double, MealType) -> Unit, onDismiss: () -> Unit) {
    var portions by rememberSaveable { mutableStateOf("1") }
    var meal by rememberSaveable { mutableStateOf(MealType.forHour(LocalTime.now().hour)) }
    val value = portions.parseDecimal()?.takeIf { it > 0 && it <= 50 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = portions,
                    onValueChange = { portions = it },
                    label = stringResource(R.string.recipe_portions),
                    isError = portions.isNotBlank() && value == null,
                    errorText = stringResource(R.string.error_number),
                    modifier = Modifier.fillMaxWidth(),
                )
                MealTypePicker(selected = meal, onSelect = { meal = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onLog(it, meal) } }, enabled = value != null) { Text(stringResource(R.string.recipe_log)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
