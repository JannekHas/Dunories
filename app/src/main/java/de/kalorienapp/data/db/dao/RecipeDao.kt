package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.RecipeCategoryEntity
import de.kalorienapp.data.db.entity.RecipeCategoryRefEntity
import de.kalorienapp.data.db.entity.RecipeEntity
import de.kalorienapp.data.db.entity.RecipeIngredientEntity
import de.kalorienapp.data.db.entity.RecipeStepEntity
import de.kalorienapp.data.db.entity.RecipeWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observe(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun get(id: Long): RecipeWithDetails?

    @Query("SELECT imagePath FROM recipe WHERE imagePath IS NOT NULL")
    suspend fun imagePaths(): List<String>

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity): Long

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteRecipe(id: Long)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("DELETE FROM recipe_step WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: Long)

    @Query("DELETE FROM recipe_category_ref WHERE recipeId = :recipeId")
    suspend fun deleteCategoryRefs(recipeId: Long)

    @Insert
    suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Insert
    suspend fun insertSteps(items: List<RecipeStepEntity>)

    @Insert
    suspend fun insertCategoryRefs(items: List<RecipeCategoryRefEntity>)

    /** Rezept samt Zutaten, Schritten und Kategorien speichern (Kinder werden ersetzt). @return Rezept-ID */
    @Transaction
    suspend fun save(
        recipe: RecipeEntity,
        ingredients: (Long) -> List<RecipeIngredientEntity>,
        steps: (Long) -> List<RecipeStepEntity>,
        categoryIds: Set<Long>,
    ): Long {
        val upserted = upsertRecipe(recipe)
        // Upsert liefert bei einem Update -1.
        val id = if (recipe.id != 0L) recipe.id else upserted
        deleteIngredients(id)
        deleteSteps(id)
        deleteCategoryRefs(id)
        insertIngredients(ingredients(id))
        insertSteps(steps(id))
        insertCategoryRefs(categoryIds.map { RecipeCategoryRefEntity(id, it) })
        return id
    }

    @Query("SELECT * FROM recipe_category ORDER BY position, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<RecipeCategoryEntity>>

    @Query("SELECT * FROM recipe_category ORDER BY position, name COLLATE NOCASE")
    suspend fun categories(): List<RecipeCategoryEntity>

    @Upsert
    suspend fun upsertCategory(category: RecipeCategoryEntity): Long

    @Query("DELETE FROM recipe_category WHERE id = :id")
    suspend fun deleteCategory(id: Long)
}
