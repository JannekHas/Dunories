package de.kalorienapp.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HealthConnectAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, NOT_SUPPORTED }

/** Rohwerte eines Tages aus Health Connect (kcal), jeweils null wenn nicht lesbar. */
data class CaloriesReading(val activeKcal: Double?, val totalKcal: Double?)

/** Dünne Hülle um den Health-Connect-Client – nur Lesen der verbrannten Kalorien. */
class HealthConnectManager(private val context: Context) {

    private val activePermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val totalPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    private val calorieReadPermissions = setOf(activePermission, totalPermission)

    /**
     * Angefragte Berechtigungen: „aktive Kalorien“ reicht, „gesamt“ ist der Fallback (z. B. Samsung Health).
     * Training und Schritte nur für die Diagnose (welche Daten liefert die Quell-App überhaupt?).
     */
    val permissions: Set<String> = calorieReadPermissions + setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HealthConnectAvailability.NOT_SUPPORTED
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    internal val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    internal suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    /** true, wenn mindestens eine der beiden Kalorien-Berechtigungen erteilt ist. */
    suspend fun hasPermission(): Boolean = grantedPermissions().any { it in calorieReadPermissions }

    /** Liest aktive und gesamte verbrannte Kalorien für [date] (bis jetzt, falls heute). */
    suspend fun readCalories(date: LocalDate, now: Instant = Instant.now()): CaloriesReading {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (!end.isAfter(start)) return CaloriesReading(null, null)

        val granted = grantedPermissions()
        val metrics = buildSet<AggregateMetric<Energy>> {
            if (activePermission in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (totalPermission in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        if (metrics.isEmpty()) return CaloriesReading(null, null)

        val range = TimeRangeFilter.between(start, end)
        val result = client.aggregate(AggregateRequest(metrics, range))
        // Health Connect liefert für „gesamt“ auch ohne einen einzigen Datensatz einen geschätzten
        // Grundumsatz (im Emulator z. B. 1564 kcal/Tag). Nur echte Datensätze einer App zählen.
        val hasTotalRecords = totalPermission in granted &&
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range, pageSize = 1)).records.isNotEmpty()
        return CaloriesReading(
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            totalKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { hasTotalRecords },
        )
    }

    companion object {
        /** Anteil des Tages, der für [date] bereits vergangen ist (0..1). */
        fun elapsedFractionOfDay(date: LocalDate, now: Instant = Instant.now()): Double {
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            return when {
                !now.isAfter(start) -> 0.0
                !now.isBefore(end) -> 1.0
                else -> Duration.between(start, now).seconds.toDouble() / Duration.between(start, end).seconds
            }
        }
    }
}
