package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.DailyTrainingDao
import de.kalorienapp.data.db.entity.DailyTrainingEntity
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.TrainingCalories
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.health.HealthConnectAvailability
import de.kalorienapp.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Ergebnis einer Aktualisierung aus Health Connect – steuert den Hinweis in der Tagesübersicht. */
enum class HealthSyncStatus { OK, NO_PERMISSION, NEEDS_INSTALL, NOT_SUPPORTED, ERROR }

/** Trainingskalorien eines Tages. */
data class DailyTraining(val activeKcal: Int, val workoutCount: Int)

/** Trainingskalorien pro Tag: aus Health Connect gelesen, lokal zwischengespeichert. */
class TrainingRepository(
    private val healthConnect: HealthConnectManager,
    private val dao: DailyTrainingDao,
) {
    fun training(date: LocalDate): Flow<DailyTraining?> =
        dao.observe(date.toEpochDay()).map { it?.let { e -> DailyTraining(e.activeKcal, e.workoutCount) } }

    fun permissionContract() = healthConnect.permissionContract()

    val permissions: Set<String> get() = healthConnect.permissions

    /** Liest die Trainings des Tages aus Health Connect und speichert die Summe. */
    suspend fun refresh(date: LocalDate, profile: Profile): HealthSyncStatus {
        when (healthConnect.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED -> return HealthSyncStatus.NOT_SUPPORTED
            HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE -> return HealthSyncStatus.NEEDS_INSTALL
            HealthConnectAvailability.AVAILABLE -> Unit
        }
        if (date.isAfter(LocalDate.now())) return HealthSyncStatus.OK
        return try {
            if (!healthConnect.hasPermission()) return HealthSyncStatus.NO_PERMISSION
            val workouts = healthConnect.readWorkouts(date)
            val bmr = GoalCalculator.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(date.year))
            dao.upsert(
                DailyTrainingEntity(
                    dateEpochDay = date.toEpochDay(),
                    activeKcal = TrainingCalories.dayTotal(workouts, bmr),
                    workoutCount = workouts.size,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            HealthSyncStatus.OK
        } catch (e: SecurityException) {
            HealthSyncStatus.NO_PERMISSION
        } catch (e: Exception) {
            HealthSyncStatus.ERROR
        }
    }
}
