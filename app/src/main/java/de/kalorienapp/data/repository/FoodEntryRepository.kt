package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.domain.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FoodEntryRepository(private val dao: FoodEntryDao) {
    fun entriesFor(date: LocalDate): Flow<List<FoodEntry>> =
        dao.observeForDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): FoodEntry? = dao.getById(id)?.toDomain()

    /** Legt an oder aktualisiert – auch zum Wiederherstellen gelöschter Einträge (gleiche id). */
    suspend fun save(entry: FoodEntry) = dao.upsert(FoodEntryEntity.fromDomain(entry))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
