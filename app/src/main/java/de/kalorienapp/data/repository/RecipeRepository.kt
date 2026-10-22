package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.RecipeDao
import de.kalorienapp.data.db.entity.RecipeCategoryEntity
import de.kalorienapp.data.db.entity.RecipeEntity
import de.kalorienapp.data.db.entity.RecipeIngredientEntity
import de.kalorienapp.data.db.entity.RecipeStepEntity
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.recipe.RecipeImageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepository(
    private val dao: RecipeDao,
    private val images: RecipeImageStore,
) {
    val recipes: Flow<List<Recipe>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val categories: Flow<List<RecipeCategory>> = dao.observeCategories().map { list -> list.map { it.toDomain() } }

    fun recipe(id: Long): Flow<Recipe?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: Long): Recipe? = dao.get(id)?.toDomain()

    /** @return ID des gespeicherten Rezepts. Ein ersetztes Bild wird gelöscht. */
    suspend fun save(recipe: Recipe): Long {
        val previousImage = if (recipe.id != 0L) dao.get(recipe.id)?.recipe?.imagePath else null
        val id = dao.save(
            recipe = RecipeEntity(
                id = recipe.id,
                name = recipe.name.trim(),
                description = recipe.description.trim(),
                servings = recipe.safeServings,
                totalMinutes = recipe.totalMinutes,
                effort = recipe.effort,
                imagePath = recipe.imagePath,
                sourceUrl = recipe.sourceUrl,
                source = recipe.source,
                createdAt = recipe.createdAt,
            ),
            ingredients = { id ->
                recipe.ingredients.mapIndexed { index, item -> RecipeIngredientEntity.fromDomain(id, index, item) }
            },
            steps = { id ->
                recipe.steps.map(String::trim).filter(String::isNotEmpty)
                    .mapIndexed { index, text -> RecipeStepEntity(recipeId = id, position = index, text = text) }
            },
            categoryIds = recipe.categoryIds,
        )
        if (previousImage != null && previousImage != recipe.imagePath) images.delete(previousImage)
        return id
    }

    suspend fun delete(recipe: Recipe) {
        dao.deleteRecipe(recipe.id)
        recipe.imagePath?.let(images::delete)
    }

    /** Neue Kategorie ans Ende; vorhandene (gleicher Name) wird wiederverwendet. @return ID */
    suspend fun addCategory(name: String): Long {
        val trimmed = name.trim()
        val existing = dao.categories()
        existing.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        return dao.upsertCategory(RecipeCategoryEntity(name = trimmed, position = (existing.maxOfOrNull { it.position } ?: -1) + 1))
    }

    suspend fun renameCategory(category: RecipeCategory, name: String) {
        dao.upsertCategory(RecipeCategoryEntity(category.id, name.trim(), category.position))
    }

    suspend fun deleteCategory(category: RecipeCategory) = dao.deleteCategory(category.id)
}
