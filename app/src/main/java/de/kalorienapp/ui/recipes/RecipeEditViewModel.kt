package de.kalorienapp.ui.recipes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.recipe.RecipeDraftStore
import de.kalorienapp.recipe.RecipeImageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeEditUiState(
    val isLoading: Boolean = true,
    val form: RecipeForm = RecipeForm(),
    /** true = KI-Vorschlag aus dem Import (Hinweis „bitte prüfen“). */
    val fromImport: Boolean = false,
    val isSaving: Boolean = false,
    val imageLoading: Boolean = false,
)

/** Rezept anlegen, bearbeiten oder einen Import-Vorschlag prüfen. */
class RecipeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val images: RecipeImageStore,
    drafts: RecipeDraftStore,
) : ViewModel() {

    private val id: Long = savedStateHandle["id"] ?: 0L
    private val fromImport: Boolean = savedStateHandle["fromImport"] ?: false

    private val _uiState = MutableStateFlow(RecipeEditUiState(fromImport = fromImport))
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<RecipeCategory>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextKey = 1L
    private fun key() = nextKey++

    /** In dieser Sitzung neu gespeicherte Bilder – nicht verwendete werden wieder gelöscht. */
    private val createdImages = mutableSetOf<String>()
    private var saved = false

    init {
        val draft = if (fromImport) drafts.take() else null
        draft?.recipe?.imagePath?.let(createdImages::add)
        viewModelScope.launch {
            val existing = if (id != 0L) repository.get(id) else null
            val form = when {
                existing != null -> formOf(existing)
                draft != null -> {
                    val known = repository.categories.first()
                    val (matched, unknown) = draft.categoryNames.partition { name -> known.any { it.name.equals(name, ignoreCase = true) } }
                    formOf(draft.recipe).copy(
                        categoryIds = known.filter { c -> matched.any { it.equals(c.name, ignoreCase = true) } }.map { it.id }.toSet(),
                        newCategoryNames = unknown.toSet(),
                    )
                }
                else -> RecipeForm(ingredients = listOf(IngredientForm(key())), steps = listOf(StepForm(key())))
            }
            _uiState.update { it.copy(isLoading = false, form = form) }
        }
    }

    private fun formOf(recipe: Recipe) = RecipeForm(
        id = recipe.id,
        name = recipe.name,
        description = recipe.description,
        servings = recipe.safeServings.toString(),
        minutes = recipe.totalMinutes?.toString().orEmpty(),
        effort = recipe.effort,
        categoryIds = recipe.categoryIds,
        imagePath = recipe.imagePath,
        sourceUrl = recipe.sourceUrl,
        source = recipe.source,
        createdAt = recipe.createdAt,
        ingredients = recipe.ingredients.map { IngredientForm.from(key(), it) },
        steps = recipe.steps.map { StepForm(key(), it) },
    )

    fun update(transform: (RecipeForm) -> RecipeForm) = _uiState.update { it.copy(form = transform(it.form)) }

    fun toggleCategory(id: Long) = update { f ->
        f.copy(categoryIds = if (id in f.categoryIds) f.categoryIds - id else f.categoryIds + id)
    }

    fun toggleNewCategory(name: String) = update { f ->
        f.copy(newCategoryNames = if (name in f.newCategoryNames) f.newCategoryNames - name else f.newCategoryNames + name)
    }

    fun newIngredient(): IngredientForm = IngredientForm(key())

    /** Neue oder geänderte Zutat übernehmen (Schlüssel identifiziert sie). */
    fun putIngredient(ingredient: IngredientForm) = update { f ->
        val exists = f.ingredients.any { it.key == ingredient.key }
        f.copy(ingredients = if (exists) f.ingredients.map { if (it.key == ingredient.key) ingredient else it } else f.ingredients + ingredient)
    }

    fun removeIngredient(key: Long) = update { f -> f.copy(ingredients = f.ingredients.filterNot { it.key == key }) }

    fun addStep() = update { f -> f.copy(steps = f.steps + StepForm(key())) }

    fun setStep(key: Long, text: String) = update { f -> f.copy(steps = f.steps.map { if (it.key == key) it.copy(text = text) else it }) }

    fun removeStep(key: Long) = update { f -> f.copy(steps = f.steps.filterNot { it.key == key }) }

    fun setImage(uri: Uri) {
        _uiState.update { it.copy(imageLoading = true) }
        viewModelScope.launch {
            val path = runCatching { images.saveFromUri(uri) }.getOrNull()
            path?.let(createdImages::add)
            _uiState.update { state ->
                state.copy(imageLoading = false, form = if (path != null) state.form.copy(imagePath = path) else state.form)
            }
        }
    }

    fun removeImage() = update { it.copy(imagePath = null) }

    fun save(onSaved: (Long) -> Unit) {
        val form = _uiState.value.form
        if (!form.isValid || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val newIds = form.newCategoryNames.map { repository.addCategory(it) }.toSet()
            val savedId = repository.save(form.toRecipe(newIds))
            saved = true
            (createdImages - setOfNotNull(form.imagePath)).forEach(images::delete)
            createdImages.clear()
            onSaved(savedId)
        }
    }

    override fun onCleared() {
        // Verworfen → in dieser Sitzung erzeugte Bilder wieder entfernen.
        if (!saved) createdImages.forEach(images::delete)
        super.onCleared()
    }
}
