package de.kalorienapp.data.repository

import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.MealPlanDao
import de.kalorienapp.data.db.entity.MealPlanItemEntity
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSettings
import de.kalorienapp.domain.model.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Ernährungsplan: Inhalt in der DB (`meal_plan_item`), Schalter/Zeitraum in [AppSettings]. */
class MealPlanRepository(private val dao: MealPlanDao, private val settings: AppSettings) {
    val config: StateFlow<MealPlanSettings> = settings.mealPlan

    val items: Flow<List<MealPlanItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun setConfig(value: MealPlanSettings) = settings.setMealPlan(value)

    fun updateConfig(transform: (MealPlanSettings) -> MealPlanSettings) = settings.updateMealPlan(transform)

    suspend fun forDay(dayIndex: Int): List<MealPlanItem> = dao.forDay(dayIndex).map { it.toDomain() }

    suspend fun get(id: Long): MealPlanItem? = dao.get(id)?.toDomain()

    /** Erfasste (Vorschlags-)Einträge hinten an die jeweilige Mahlzeit des Plan-Tags hängen. */
    suspend fun addFromEntries(dayIndex: Int, entries: List<FoodEntry>) {
        val next = mutableMapOf<MealType, Int>()
        val items = entries.map { entry ->
            val position = next.getOrPut(entry.mealType) { dao.maxPosition(dayIndex, entry.mealType.name) + 1 }
            next[entry.mealType] = position + 1
            MealPlanItemEntity.fromDomain(MealPlanItem.fromEntry(entry, dayIndex, position))
        }
        dao.insertAll(items)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Ganzen Plan ersetzen (Import). */
    suspend fun replaceAll(items: List<MealPlanItem>) =
        dao.replaceAll(items.map { MealPlanItemEntity.fromDomain(it.copy(id = 0)) })

    /** Tag [from] nach [to] kopieren (ersetzt [to]). */
    suspend fun copyDay(from: Int, to: Int) {
        if (from == to) return
        dao.replaceDay(to, dao.forDay(from).map { it.copy(id = 0, dayIndex = to) })
    }

    suspend fun clearDay(dayIndex: Int) = dao.deleteDay(dayIndex)
}
