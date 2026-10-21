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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HealthConnectAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, NOT_SUPPORTED }

/** Rohwerte eines Tages aus Health Connect, jeweils null wenn nicht lesbar bzw. keine Daten. */
data class ActivityReading(val activeKcal: Double?, val totalKcal: Double?, val steps: Long?)

/** Dünne Hülle um den Health-Connect-Client – nur Lesen (Kalorien, Schritte; Trainings nur Diagnose). */
class HealthConnectManager(private val context: Context) {

    private val activePermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val totalPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val activityReadPermissions = setOf(activePermission, totalPermission, stepsPermission)

    /**
     * Angefragte Berechtigungen: aktive/gesamte Kalorien und Schritte (Samsung Health liefert oft nur Schritte).
     * Trainings nur für die Diagnose.
     */
    val permissions: Set<String> = activityReadPermissions +
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HealthConnectAvailability.NOT_SUPPORTED
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    internal val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    internal suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    /** true, wenn mindestens eine Kalorien- oder Schritte-Berechtigung erteilt ist. */
    suspend fun hasPermission(): Boolean = grantedPermissions().any { it in activityReadPermissions }

    /** Liest aktive/gesamte Kalorien und Schritte für [date] (bis jetzt, falls heute). */
    suspend fun readActivity(date: LocalDate, now: Instant = Instant.now()): ActivityReading {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (!end.isAfter(start)) return ActivityReading(null, null, null)

        val granted = grantedPermissions()
        val metrics = buildSet<AggregateMetric<*>> {
            if (activePermission in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (totalPermission in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            // Health Connect entfernt doppelte Schritte (Uhr + Handy) anhand der Quellen-Priorität.
            if (stepsPermission in granted) add(StepsRecord.COUNT_TOTAL)
        }
        if (metrics.isEmpty()) return ActivityReading(null, null, null)

        val range = TimeRangeFilter.between(start, end)
        val result = client.aggregate(AggregateRequest(metrics, range))
        // Health Connect liefert für „gesamt“ auch ohne einen einzigen Datensatz einen geschätzten
        // Grundumsatz (im Emulator z. B. 1564 kcal/Tag). Nur echte Datensätze einer App zählen.
        val hasTotalRecords = totalPermission in granted &&
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range, pageSize = 1)).records.isNotEmpty()
        return ActivityReading(
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            totalKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { hasTotalRecords },
            steps = result[StepsRecord.COUNT_TOTAL],
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
