package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.LeftoverDao
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.LeftoverEntity
import de.kalorienapp.domain.Leftovers
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Leftover
import de.kalorienapp.domain.model.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Was „Rückgängig“ braucht, um eine Reste-Aktion zurückzunehmen. */
sealed interface LeftoverUndo {
    data class Grouped(val dishId: Long, val restId: Long, val originals: List<FoodEntry>) : LeftoverUndo
    data class Partial(val original: FoodEntry, val restId: Long) : LeftoverUndo
    data class Taken(val entryId: Long, val before: Leftover) : LeftoverUndo
    data class Discarded(val before: Leftover) : LeftoverUndo
    data class AteAll(val original: FoodEntry, val rest: Leftover) : LeftoverUndo
}

/** Reste / offene Mengen (siehe [Leftovers]). */
class LeftoverRepository(private val dao: LeftoverDao) {
    /** Alle offenen Reste, neueste zuerst. */
    val open: Flow<List<Leftover>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Einträge zu einem Gericht zusammenfassen, davon [eatenAmount] gegessen. @return null = nichts zum Zusammenfassen. */
    suspend fun group(entries: List<FoodEntry>, name: String, eatenAmount: Double): LeftoverUndo.Grouped? {
        val used = Leftovers.groupable(entries)
        val split = Leftovers.group(used, name, eatenAmount, System.currentTimeMillis()) ?: return null
        val (dishId, restId) = dao.group(
            removeIds = used.map { it.id },
            dish = FoodEntryEntity.fromDomain(split.eaten),
            rest = split.leftover?.let(LeftoverEntity::fromDomain),
        )
        return LeftoverUndo.Grouped(dishId, restId, used)
    }

    /** „Nicht alles gegessen“: Eintrag kürzen, Rest offen halten. @return null = nichts übrig. */
    suspend fun partial(entry: FoodEntry, eatenAmount: Double): LeftoverUndo.Partial? {
        val split = Leftovers.partial(entry, eatenAmount, System.currentTimeMillis()) ?: return null
        val rest = split.leftover ?: return null
        val restId = dao.partial(FoodEntryEntity.fromDomain(split.eaten), LeftoverEntity.fromDomain(rest))
        return LeftoverUndo.Partial(entry, restId)
    }

    /** [amount] g/ml vom Rest in [date]/[mealType] eintragen. */
    suspend fun take(leftover: Leftover, amount: Double, date: LocalDate, mealType: MealType): LeftoverUndo.Taken {
        val result = Leftovers.take(leftover, amount, date, mealType, System.currentTimeMillis())
        val entryId = dao.take(
            entry = FoodEntryEntity.fromDomain(result.entry),
            leftoverId = leftover.id,
            remaining = result.remaining?.let(LeftoverEntity::fromDomain),
        )
        return LeftoverUndo.Taken(entryId, leftover)
    }

    /** Für immer verwerfen (gegessen von jemand anderem, weggeworfen, schlecht geworden). */
    suspend fun discard(leftover: Leftover): LeftoverUndo.Discarded {
        dao.delete(leftover.id)
        return LeftoverUndo.Discarded(leftover)
    }

    /** „Doch alles gegessen“: Rest zurück in den Eintrag, Rest schließen. */
    suspend fun eatAll(entry: FoodEntry, rest: Leftover): LeftoverUndo.AteAll {
        dao.undoPartial(FoodEntryEntity.fromDomain(Leftovers.eatAll(entry, rest)), rest.id)
        return LeftoverUndo.AteAll(entry, rest)
    }

    /** Eingefroren, „heute nicht“ oder „noch gut“. */
    suspend fun update(leftover: Leftover) = dao.upsert(LeftoverEntity.fromDomain(leftover))

    suspend fun undo(undo: LeftoverUndo) = when (undo) {
        is LeftoverUndo.Grouped -> dao.ungroup(undo.dishId, undo.restId, undo.originals.map(FoodEntryEntity::fromDomain))
        is LeftoverUndo.Partial -> dao.undoPartial(FoodEntryEntity.fromDomain(undo.original), undo.restId)
        is LeftoverUndo.Taken -> dao.undoTake(undo.entryId, LeftoverEntity.fromDomain(undo.before))
        is LeftoverUndo.Discarded -> dao.upsert(LeftoverEntity.fromDomain(undo.before))
        is LeftoverUndo.AteAll -> dao.partial(FoodEntryEntity.fromDomain(undo.original), LeftoverEntity.fromDomain(undo.rest)).let { }
    }
}
