package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.ActivityEntryDao
import de.kalorienapp.data.db.entity.ActivityEntryEntity
import de.kalorienapp.domain.model.ActivityEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Selbst eingetragene Aktivitäten (verbrannte Kalorien) pro Tag. */
class ActivityEntryRepository(private val dao: ActivityEntryDao) {
    fun entriesFor(date: LocalDate): Flow<List<ActivityEntry>> =
        dao.observeForDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    /** Legt an oder aktualisiert – auch zum Wiederherstellen gelöschter Einträge (gleiche id). */
    suspend fun save(entry: ActivityEntry) = dao.upsert(ActivityEntryEntity.fromDomain(entry))

    suspend fun delete(id: Long) = dao.deleteById(id)
}
