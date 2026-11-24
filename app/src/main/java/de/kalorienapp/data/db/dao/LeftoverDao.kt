package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.LeftoverEntity
import kotlinx.coroutines.flow.Flow

/** Offene Reste; Zusammenfassen/Übernehmen ändern Tagebuch und Reste in einem Schritt. */
@Dao
abstract class LeftoverDao {
    @Query("SELECT * FROM leftover ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<LeftoverEntity>>

    @Upsert
    abstract suspend fun upsert(leftover: LeftoverEntity)

    @Query("DELETE FROM leftover WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Insert
    protected abstract suspend fun insertLeftover(leftover: LeftoverEntity): Long

    @Insert
    protected abstract suspend fun insertEntry(entry: FoodEntryEntity): Long

    @Upsert
    protected abstract suspend fun upsertEntries(entries: List<FoodEntryEntity>)

    @Query("DELETE FROM food_entry WHERE id IN (:ids)")
    protected abstract suspend fun deleteEntries(ids: List<Long>)

    /** Einträge [removeIds] durch [dish] ersetzen, [rest] offen halten. @return neue ids (Gericht, Rest oder 0). */
    @Transaction
    open suspend fun group(removeIds: List<Long>, dish: FoodEntryEntity, rest: LeftoverEntity?): Pair<Long, Long> {
        deleteEntries(removeIds)
        val dishId = insertEntry(dish)
        return dishId to (rest?.let { insertLeftover(it) } ?: 0L)
    }

    /** Zusammenfassen zurücknehmen. */
    @Transaction
    open suspend fun ungroup(dishId: Long, restId: Long, originals: List<FoodEntryEntity>) {
        deleteEntries(listOf(dishId))
        if (restId != 0L) delete(restId)
        upsertEntries(originals)
    }

    /** Eintrag auf den gegessenen Teil kürzen, Rest anlegen. @return id des Rests. */
    @Transaction
    open suspend fun partial(updated: FoodEntryEntity, rest: LeftoverEntity): Long {
        upsertEntries(listOf(updated))
        return insertLeftover(rest)
    }

    @Transaction
    open suspend fun undoPartial(original: FoodEntryEntity, restId: Long) {
        upsertEntries(listOf(original))
        delete(restId)
    }

    /** Teil eines Rests eintragen; [remaining] null = aufgebraucht. @return id des neuen Eintrags. */
    @Transaction
    open suspend fun take(entry: FoodEntryEntity, leftoverId: Long, remaining: LeftoverEntity?): Long {
        val id = insertEntry(entry)
        if (remaining != null) upsert(remaining) else delete(leftoverId)
        return id
    }

    @Transaction
    open suspend fun undoTake(entryId: Long, before: LeftoverEntity) {
        deleteEntries(listOf(entryId))
        upsert(before)
    }
}
