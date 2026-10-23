package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.DailyNutritionRow
import de.kalorienapp.data.db.dao.StatsDao
import de.kalorienapp.domain.StatsCalculator
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.sum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tageswerte für Kalender und Statistik. Ziele werden mit dem **aktuellen** Profil und Zielplan
 * berechnet – historische Tagesziele werden nicht gespeichert.
 */
class StatsRepository(
    private val dao: StatsDao,
    private val goalRepository: GoalRepository,
) {
    fun days(from: LocalDate, to: LocalDate): Flow<List<DayStats>> = combine(
        dao.observeDailyNutrition(from.toEpochDay(), to.toEpochDay()),
        dao.observeDailyActivity(from.toEpochDay(), to.toEpochDay()),
        goalRepository.current,
        dao.observeMicros(from.toEpochDay(), to.toEpochDay()),
    ) { nutrition, activity, state, micros ->
        if (state == null) return@combine emptyList()
        val microsByDay = micros.groupBy { it.dateEpochDay }.mapValues { (_, rows) ->
            rows.map { Nutrients(micros = Micro.decode(it.micros)) }.sum().micros
        }
        StatsCalculator.days(
            from = from,
            to = to,
            eatenByDay = nutrition.associate {
                LocalDate.ofEpochDay(it.dateEpochDay) to it.toNutrients().copy(micros = microsByDay[it.dateEpochDay].orEmpty())
            },
            activityByDay = activity.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.kcal },
            targetsFor = { date -> state.targetsFor(date.year) },
        )
    }

    /**
     * Uhrzeiten der Einträge je Tag – nur am selben Tag erfasste (nachgetragene verfälschen die Essenszeit).
     * Für die Fasten-Auswertung.
     */
    fun entryTimes(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<Map<LocalDate, List<LocalTime>>> =
        dao.observeEntryTimes(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.mapNotNull { row ->
                val created = Instant.ofEpochMilli(row.createdAt).atZone(zone)
                val day = LocalDate.ofEpochDay(row.dateEpochDay)
                if (created.toLocalDate() == day) day to created.toLocalTime() else null
            }.groupBy({ it.first }, { it.second })
        }

    private fun DailyNutritionRow.toNutrients() = Nutrients(
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        saturatedFatG = saturatedFatG,
        sugarG = sugarG,
        fiberG = fiberG,
        saltG = saltG,
    )
}
