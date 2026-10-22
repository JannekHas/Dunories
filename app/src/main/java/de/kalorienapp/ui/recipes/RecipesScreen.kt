package de.kalorienapp.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipesUiState(
    val recipes: List<Recipe> = emptyList(),
    val categories: List<RecipeCategory> = emptyList(),
    val query: String = "",
    val categoryId: Long? = null,
    val hasAnyRecipe: Boolean = false,
)

class RecipesViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<RecipesUiState> = combine(
        repository.recipes, repository.categories, query, categoryId,
    ) { recipes, categories, q, category ->
        val needle = q.trim().lowercase()
        RecipesUiState(
            recipes = recipes.filter { recipe ->
                (category == null || category in recipe.categoryIds) &&
                    (needle.isEmpty() || needle in recipe.name.lowercase() ||
                        recipe.ingredients.any { needle in it.name.lowercase() })
            },
            categories = categories,
            query = q,
            categoryId = category?.takeIf { id -> categories.any { it.id == id } },
            hasAnyRecipe = recipes.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectCategory(id: Long?) {
        categoryId.value = id
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addCategory(name) }
    }

    fun renameCategory(category: RecipeCategory, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameCategory(category, name) }
    }

    fun deleteCategory(category: RecipeCategory) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}

/** Rezeptbuch: Suche, Kategorien, Karten mit Dauer, Aufwand und Nährwerten pro Portion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onOpen: (Recipe) -> Unit,
    onCreate: () -> Unit,
    onImportLink: (String) -> Unit,
    viewModel: RecipesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    if (showLinkDialog) {
        LinkDialog(onImport = { showLinkDialog = false; onImportLink(it) }, onDismiss = { showLinkDialog = false })
    }
    if (showCategories) {
        CategoryManagerSheet(
            categories = state.categories,
            onAdd = viewModel::addCategory,
            onRename = viewModel::renameCategory,
            onDelete = viewModel::deleteCategory,
            onDismiss = { showCategories = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    IconButton(onClick = { showCategories = true }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.categories_manage))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipe_new)) },
                        onClick = { showMenu = false; onCreate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipe_import_link)) },
                        onClick = { showMenu = false; showLinkDialog = true },
                    )
                }
                FloatingActionButton(
                    onClick = { showMenu = true },
                    containerColor = Neon.Yellow,
                    contentColor = Neon.OnNeon,
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.recipe_new))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.recipes_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "categories") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.categoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text(stringResource(R.string.recipes_all)) },
                            colors = neonChipColors(),
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.categoryId == category.id,
                            onClick = { viewModel.selectCategory(if (state.categoryId == category.id) null else category.id) },
                            label = { Text(category.name) },
                            colors = neonChipColors(),
                        )
                    }
                }
            }
            if (!state.hasAnyRecipe) {
                item(key = "empty") { EmptyHint() }
            } else if (state.recipes.isEmpty()) {
                item(key = "none") {
                    Text(stringResource(R.string.recipes_no_match), color = Neon.TextSecondary)
                }
            }
            items(state.recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe, state.categories, onClick = { onOpen(recipe) })
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, categories: List<RecipeCategory>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeImage(
                recipe,
                maxSide = 320,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RecipeMetaLine(recipe)
                val names = categories.filter { it.id in recipe.categoryIds }.joinToString(" · ") { it.name }
                if (names.isNotEmpty()) {
                    Text(names, style = MaterialTheme.typography.labelMedium, color = Neon.Violet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MacroLine(recipe.perServing)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_restaurant), contentDescription = null, tint = Neon.Yellow, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.recipes_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.recipes_empty_text), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
        }
    }
}

@Composable
private fun LinkDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_import_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recipe_import_link_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.recipe_import_link_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.recipe_import_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Platzhalter für Bilder im 4:3-Format. */
fun Modifier.recipeImageAspect(): Modifier = this.fillMaxWidth().aspectRatio(4f / 3f)
