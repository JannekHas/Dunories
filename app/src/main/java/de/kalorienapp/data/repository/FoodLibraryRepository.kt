package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.FavoriteFoodDao
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.entity.FavoriteFoodEntity
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.FrequentFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Schnellauswahl: Favoriten und häufig gegessene Lebensmittel. */
class FoodLibraryRepository(
    private val favoriteDao: FavoriteFoodDao,
    private val entryDao: FoodEntryDao,
) {
    val favorites: Flow<List<FavoriteFood>> = favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun frequent(limit: Int = 30): Flow<List<FrequentFood>> =
        entryDao.observeFrequent(limit).map { rows ->
            rows.mapNotNull { row ->
                val entry = row.entry.toDomain()
                val amount = entry.amount?.takeIf { it > 0 } ?: return@mapNotNull null
                FrequentFood(
                    name = entry.name,
                    amount = amount,
                    unit = entry.unit,
                    per100 = entry.nutrients.amountToPer100(amount),
                    timesEaten = row.useCount,
                )
            }
        }

    fun isFavorite(name: String): Flow<Boolean> = favoriteDao.observeCountByName(name).map { it > 0 }

    /** Speichert einen Eintrag als Favorit (Werte pro 100 g/ml, Menge als übliche Portion). */
    suspend fun addFavorite(entry: FoodEntry) {
        val amount = entry.amount?.takeIf { it > 0 } ?: 100.0
        // Pro Name nur ein Favorit – ein erneutes Speichern ersetzt den alten.
        favoriteDao.deleteByName(entry.name)
        favoriteDao.insert(
            FavoriteFoodEntity.fromDomain(
                FavoriteFood(
                    name = entry.name,
                    amount = amount,
                    unit = entry.unit,
                    per100 = entry.nutrients.amountToPer100(amount),
                ),
            ),
        )
    }

    suspend fun removeFavorite(id: Long) = favoriteDao.deleteById(id)

    suspend fun removeFavoriteByName(name: String) = favoriteDao.deleteByName(name)
}
