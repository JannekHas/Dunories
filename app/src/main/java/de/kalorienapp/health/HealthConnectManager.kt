package de.kalorienapp.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.kalorienapp.domain.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthConnectAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, NOT_SUPPORTED }

/**
 * Liest Trainings und deren Kalorien aus Health Connect (nur lesen).
 *
 * Samsung Health schreibt für bewusst gestartete Trainings eine Trainingseinheit plus
 * „verbrannte Kalorien insgesamt“ für deren Zeitraum. Alltagsbewegung wird nicht übertragen.
 */
class HealthConnectManager(private val context: Context) {

    private val exercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val totalPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    private val activePermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

    /** Angefragte Berechtigungen; „aktive Kalorien“ nur für Quellen, die sie liefern. */
    val permissions: Set<String> = setOf(exercisePermission, totalPermission, activePermission)

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HealthConnectAvailability.NOT_SUPPORTED
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    /** Mindestens nötig: Trainings und Gesamt-Kalorien. */
    suspend fun hasPermission(): Boolean = grantedPermissions().containsAll(setOf(exercisePermission, totalPermission))

    /** Alle Trainings, die an [date] begonnen haben, mit den Kalorien aus ihrem Zeitraum. */
    suspend fun readWorkouts(date: LocalDate, now: Instant = Instant.now()): List<Workout> {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (!dayEnd.isAfter(dayStart)) return emptyList()

        val granted = grantedPermissions()
        val sessions = readAll(ExerciseSessionRecord::class, dayStart, dayEnd)
            .filter { !it.startTime.isBefore(dayStart) && it.startTime.isBefore(dayEnd) }
        if (sessions.isEmpty()) return emptyList()

        // Kalorien-Datensätze im Zeitraum aller Trainings (Trainings können über Mitternacht gehen).
        val rangeStart = sessions.minOf { it.startTime }
        val rangeEnd = sessions.maxOf { it.endTime }
        val totals = if (totalPermission in granted) readAll(TotalCaloriesBurnedRecord::class, rangeStart, rangeEnd) else emptyList()
        val actives = if (activePermission in granted) readAll(ActiveCaloriesBurnedRecord::class, rangeStart, rangeEnd) else emptyList()

        return sessions.map { session ->
            Workout(
                durationMinutes = Duration.between(session.startTime, session.endTime).seconds / 60.0,
                totalKcal = totals.sumWithin(session) { it.startTime to it.endTime to it.energy.inKilocalories },
                activeKcal = actives.sumWithin(session) { it.startTime to it.endTime to it.energy.inKilocalories },
            )
        }
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val result = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageSize = 1000, pageToken = pageToken),
            )
            result += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return result
    }

    /**
     * Summiert die Energie der Datensätze, anteilig nach zeitlicher Überschneidung mit dem Training.
     * null, wenn kein Datensatz in das Training fällt.
     */
    private fun <T> List<T>.sumWithin(
        session: ExerciseSessionRecord,
        values: (T) -> Pair<Pair<Instant, Instant>, Double>,
    ): Double? {
        var sum = 0.0
        var found = false
        forEach { record ->
            val (times, kcal) = values(record)
            val (start, end) = times
            val overlapStart = maxOf(start, session.startTime)
            val overlapEnd = minOf(end, session.endTime)
            val length = Duration.between(start, end).toMillis()
            when {
                length <= 0 && !start.isBefore(session.startTime) && !start.isAfter(session.endTime) -> {
                    sum += kcal
                    found = true
                }
                length > 0 && overlapEnd.isAfter(overlapStart) -> {
                    sum += kcal * Duration.between(overlapStart, overlapEnd).toMillis() / length
                    found = true
                }
            }
        }
        return if (found) sum else null
    }
}
