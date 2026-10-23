package de.kalorienapp.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.domain.model.RecipeSource

@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val servings: Int,
    val totalMinutes: Int?,
    val effort: Effort?,
    val imagePath: String?,
    val sourceUrl: String?,
    val source: RecipeSource,
    val createdAt: Long,
)

/** Zutat; Nährwerte pro 100 g/ml, [amount] in g/ml für die Berechnung. */
@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val name: String,
    val quantity: Double?,
    val quantityUnit: String,
    val amount: Double?,
    val unit: AmountUnit,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val saltG: Double?,
    val note: String?,
    /** Vitamine/Mineralstoffe pro 100 g/ml (siehe [Micro.encode]). */
    val micros: String? = null,
) {
    fun toDomain() = RecipeIngredient(
        id = id,
        name = name,
        quantity = quantity,
        quantityUnit = quantityUnit,
        amount = amount,
        unit = unit,
        per100 = Nutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarG, fiberG, saltG, Micro.decode(micros)),
        note = note,
    )

    companion object {
        fun fromDomain(recipeId: Long, position: Int, i: RecipeIngredient) = RecipeIngredientEntity(
            recipeId = recipeId,
            position = position,
            name = i.name,
            quantity = i.quantity,
            quantityUnit = i.quantityUnit,
            amount = i.amount,
            unit = i.unit,
            kcal = i.per100.kcal,
            proteinG = i.per100.proteinG,
            carbsG = i.per100.carbsG,
            fatG = i.per100.fatG,
            saturatedFatG = i.per100.saturatedFatG,
            sugarG = i.per100.sugarG,
            fiberG = i.per100.fiberG,
            saltG = i.per100.saltG,
            note = i.note,
            micros = Micro.encode(i.per100.micros),
        )
    }
}

@Entity(
    tableName = "recipe_step",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class RecipeStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val text: String,
)

@Entity(tableName = "recipe_category")
data class RecipeCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
) {
    fun toDomain() = RecipeCategory(id, name, position)
}

/** Ein Rezept kann in mehreren Kategorien sein. */
@Entity(
    tableName = "recipe_category_ref",
    primaryKeys = ["recipeId", "categoryId"],
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RecipeCategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class RecipeCategoryRefEntity(
    val recipeId: Long,
    val categoryId: Long,
)

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<RecipeStepEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(RecipeCategoryRefEntity::class, parentColumn = "recipeId", entityColumn = "categoryId"),
    )
    val categories: List<RecipeCategoryEntity>,
) {
    fun toDomain() = Recipe(
        id = recipe.id,
        name = recipe.name,
        description = recipe.description,
        servings = recipe.servings,
        totalMinutes = recipe.totalMinutes,
        effort = recipe.effort,
        imagePath = recipe.imagePath,
        sourceUrl = recipe.sourceUrl,
        source = recipe.source,
        categoryIds = categories.map { it.id }.toSet(),
        ingredients = ingredients.sortedBy { it.position }.map { it.toDomain() },
        steps = steps.sortedBy { it.position }.map { it.text },
        createdAt = recipe.createdAt,
    )
}
