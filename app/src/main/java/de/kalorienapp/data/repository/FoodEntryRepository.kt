package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.domain.EntryMerge
import de.kalorienapp.domain.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Ein neuer Eintrag ist in einen gleichen von eben eingeflossen – für „Rückgängig“ auf „Heute“. */
data class MergeNotice(val before: FoodEntry, val merged: FoodEntry, val added: FoodEntry)

class FoodEntryRepository(private val dao: FoodEntryDao) {
    fun entriesFor(date: LocalDate): Flow<List<FoodEntry>> =
        dao.observeForDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): FoodEntry? = dao.getById(id)?.toDomain()

    /** Legt an oder aktualisiert – auch zum Wiederherstellen gelöschter Einträge (gleiche id). */
    suspend fun save(entry: FoodEntry) = dao.upsert(FoodEntryEntity.fromDomain(entry))

    suspend fun saveAll(entries: List<FoodEntry>) = dao.upsertAll(entries.map(FoodEntryEntity::fromDomain))

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Neue Einträge ohne Zusammenfassen (z. B. „Wie geplant“); @return ihre ids – für „Rückgängig“. */
    suspend fun insertAll(entries: List<FoodEntry>): List<Long> = dao.insertAll(entries.map(FoodEntryEntity::fromDomain))

    suspend fun deleteAll(ids: List<Long>) = dao.deleteAll(ids)

    private val _lastMerge = MutableStateFlow<MergeNotice?>(null)

    /** Letztes Zusammenfassen, bis „Heute“ es angezeigt hat. */
    val lastMerge: StateFlow<MergeNotice?> = _lastMerge.asStateFlow()

    fun mergeShown() {
        _lastMerge.value = null
    }

    /**
     * Neuer Eintrag: fließt in einen gleichen von eben ein ([EntryMerge], z. B. dritter Salami-Stick),
     * sonst wird er neu angelegt.
     */
    suspend fun add(entry: FoodEntry) {
        val day = entry.date.toEpochDay()
        val existing = dao.between(day, day).map { it.toDomain() }
        val target = EntryMerge.findTarget(existing, entry, now = entry.createdAt)
        if (target == null) {
            save(entry)
            return
        }
        val merged = EntryMerge.merge(target, entry, now = entry.createdAt)
        save(merged)
        _lastMerge.value = MergeNotice(before = target, merged = merged, added = entry)
    }

    /** Zusammenfassen zurücknehmen: alter Eintrag wie vorher, der neue als eigener Eintrag. */
    suspend fun undoMerge(notice: MergeNotice) {
        save(notice.before)
        save(notice.added.copy(id = 0))
    }
}
