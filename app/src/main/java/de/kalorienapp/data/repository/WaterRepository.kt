package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.WaterDao
import de.kalorienapp.data.db.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class WaterRepository(private val dao: WaterDao) {
    fun totalFor(date: LocalDate): Flow<Int> = dao.observeTotal(date.toEpochDay())

    suspend fun dailyTotals(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        dao.dailyTotals(from.toEpochDay(), to.toEpochDay()).associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.ml }

    suspend fun add(date: LocalDate, ml: Int) =
        dao.insert(WaterEntryEntity(dateEpochDay = date.toEpochDay(), ml = ml, createdAt = System.currentTimeMillis()))

    suspend fun undo(date: LocalDate) = dao.deleteLast(date.toEpochDay())
}
