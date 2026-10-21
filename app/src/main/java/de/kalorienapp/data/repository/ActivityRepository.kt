package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.DailyActivityDao
import de.kalorienapp.data.db.entity.DailyActivityEntity
import de.kalorienapp.domain.ActiveCalories
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.DailyActivity
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.health.HealthConnectAvailability
import de.kalorienapp.health.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Ergebnis einer Aktualisierung aus Health Connect – steuert den Hinweis in der Tagesübersicht. */
enum class HealthSyncStatus { OK, NO_PERMISSION, NEEDS_INSTALL, NOT_SUPPORTED, ERROR }

/** Aktive Kalorien pro Tag: aus Health Connect geschätzt oder manuell eingetragen, lokal gespeichert. */
class ActivityRepository(
    private val healthConnect: HealthConnectManager,
    private val dao: DailyActivityDao,
) {
    fun activity(date: LocalDate): Flow<DailyActivity?> = dao.observe(date.toEpochDay()).map { it?.toDomain() }

    fun permissionContract() = healthConnect.permissionContract()

    val permissions: Set<String> get() = healthConnect.permissions

    /** Manueller Wert (z. B. aus Samsung Health abgelesen) – hat Vorrang vor Health Connect. */
    suspend fun setManual(date: LocalDate, activeKcal: Int) {
        val existing = dao.get(date.toEpochDay())
        dao.upsert(
            DailyActivityEntity(
                dateEpochDay = date.toEpochDay(),
                activeKcal = activeKcal.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
                steps = existing?.steps,
                manual = true,
            ),
        )
    }

    /** Manuellen Wert entfernen; danach gilt wieder der automatische Wert. */
    suspend fun clearManual(date: LocalDate, profile: Profile?) {
        dao.delete(date.toEpochDay())
        if (profile?.useHealthConnect == true) refresh(date, profile)
    }

    /** Liest den Tag aus Health Connect und speichert das Ergebnis. Manuelle Werte und zukünftige Tage bleiben unberührt. */
    suspend fun refresh(date: LocalDate, profile: Profile): HealthSyncStatus {
        when (healthConnect.availability()) {
            HealthConnectAvailability.NOT_SUPPORTED -> return HealthSyncStatus.NOT_SUPPORTED
            HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE -> return HealthSyncStatus.NEEDS_INSTALL
            HealthConnectAvailability.AVAILABLE -> Unit
        }
        if (date.isAfter(LocalDate.now())) return HealthSyncStatus.OK
        return try {
            if (!healthConnect.hasPermission()) return HealthSyncStatus.NO_PERMISSION
            if (dao.get(date.toEpochDay())?.manual == true) return HealthSyncStatus.OK
            val reading = healthConnect.readActivity(date)
            val estimate = ActiveCalories.resolve(
                activeKcal = reading.activeKcal,
                totalKcal = reading.totalKcal,
                steps = reading.steps,
                bmrPerDay = GoalCalculator.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(date.year)),
                elapsedFractionOfDay = HealthConnectManager.elapsedFractionOfDay(date),
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                sex = profile.sex,
            )
            if (estimate != null) {
                dao.upsert(
                    DailyActivityEntity(
                        dateEpochDay = date.toEpochDay(),
                        activeKcal = estimate.activeKcal,
                        updatedAt = System.currentTimeMillis(),
                        steps = reading.steps,
                        source = estimate.source,
                    ),
                )
            }
            HealthSyncStatus.OK
        } catch (e: SecurityException) {
            HealthSyncStatus.NO_PERMISSION
        } catch (e: Exception) {
            HealthSyncStatus.ERROR
        }
    }
}
