package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.DailyNutritionRow
import de.kalorienapp.data.db.dao.StatsDao
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.StatsCalculator
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.Nutrients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Tageswerte für Kalender und Statistik. Ziele werden mit dem **aktuellen** Profil berechnet –
 * historische Ziele werden nicht gespeichert.
 */
class StatsRepository(
    private val dao: StatsDao,
    private val profileRepository: ProfileRepository,
) {
    fun days(from: LocalDate, to: LocalDate): Flow<List<DayStats>> = combine(
        dao.observeDailyNutrition(from.toEpochDay(), to.toEpochDay()),
        dao.observeDailyActivity(from.toEpochDay(), to.toEpochDay()),
        profileRepository.profile,
    ) { nutrition, activity, profile ->
        if (profile == null) return@combine emptyList()
        StatsCalculator.days(
            from = from,
            to = to,
            eatenByDay = nutrition.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.toNutrients() },
            activityByDay = activity.associate { LocalDate.ofEpochDay(it.dateEpochDay) to it.kcal },
            targetsFor = { date -> GoalCalculator.targets(profile, date.year) },
        )
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
