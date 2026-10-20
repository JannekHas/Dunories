package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.DailyActivityDao
import de.kalorienapp.data.db.entity.DailyActivityEntity
import de.kalorienapp.domain.ActiveCalories
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.health.HealthConnectAvailability
import de.kalorienapp.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Ergebnis einer Aktualisierung aus Health Connect – steuert den Hinweis in der Tagesübersicht. */
enum class HealthSyncStatus { OK, NO_PERMISSION, NEEDS_INSTALL, NOT_SUPPORTED, ERROR }

/** Aktive Kalorien pro Tag: aus Health Connect gelesen, lokal zwischengespeichert. */
class ActivityRepository(
    private val healthConnect: HealthConnectManager,
    private val dao: DailyActivityDao,
) {
    fun activeKcal(date: LocalDate): Flow<Int?> = dao.observe(date.toEpochDay()).map { it?.activeKcal }

    fun permissionContract() = healthConnect.permissionContract()

    val permissions: Set<String> get() = healthConnect.permissions

    /** Liest den Tag aus Health Connect und speichert das Ergebnis. Zukünftige Tage werden übersprungen. */
    suspend fun refresh(date: LocalDate, profile: Profile): HealthSyncStatus {
        when (healthConnect.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED -> return HealthSyncStatus.NOT_SUPPORTED
            HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE -> return HealthSyncStatus.NEEDS_INSTALL
            HealthConnectAvailability.AVAILABLE -> Unit
        }
        if (date.isAfter(LocalDate.now())) return HealthSyncStatus.OK
        return try {
            if (!healthConnect.hasPermission()) return HealthSyncStatus.NO_PERMISSION
            val reading = healthConnect.readCalories(date)
            val active = ActiveCalories.resolve(
                activeKcal = reading.activeKcal,
                totalKcal = reading.totalKcal,
                bmrPerDay = GoalCalculator.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(date.year)),
                elapsedFractionOfDay = HealthConnectManager.elapsedFractionOfDay(date),
            )
            if (active != null) {
                dao.upsert(DailyActivityEntity(date.toEpochDay(), active, System.currentTimeMillis()))
            }
            HealthSyncStatus.OK
        } catch (e: SecurityException) {
            HealthSyncStatus.NO_PERMISSION
        } catch (e: Exception) {
            HealthSyncStatus.ERROR
        }
    }
}
