package de.kalorienapp.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Rohdaten eines Record-Typs im Diagnose-Zeitraum. */
data class RecordSummary(
    val type: String,
    val permissionGranted: Boolean,
    val count: Int,
    /** Quell-Apps (Paketname) mit Anzahl Datensätzen. */
    val origins: Map<String, Int>,
    /** Neuester Datensatz (Startzeit), null wenn keiner. */
    val latest: Instant?,
    val error: String? = null,
)

data class DayDiagnosis(val date: LocalDate, val activeKcal: Double?, val totalKcal: Double?)

data class HealthDiagnosis(
    val availability: HealthConnectAvailability,
    val grantedPermissions: Set<String>,
    val records: List<RecordSummary>,
    val days: List<DayDiagnosis>,
) {
    /** Klartext zum Kopieren und Weitergeben (enthält keine Gesundheitsdetails außer Summen). */
    fun toReport(): String = buildString {
        appendLine("Health Connect: $availability")
        appendLine("Berechtigungen: " + grantedPermissions.joinToString { it.substringAfterLast('.') }.ifEmpty { "keine" })
        appendLine()
        records.forEach { r ->
            append("${r.type}: ")
            when {
                !r.permissionGranted -> appendLine("keine Berechtigung")
                r.error != null -> appendLine("Fehler: ${r.error}")
                else -> appendLine(
                    "${r.count} Datensätze, neuester ${r.latest ?: "-"}, Quellen: " +
                        r.origins.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "-" },
                )
            }
        }
        appendLine()
        appendLine("Tag: aktiv / gesamt (kcal, Health-Connect-Summen)")
        days.forEach { d ->
            appendLine("${d.date}: ${d.activeKcal?.toInt() ?: "-"} / ${d.totalKcal?.toInt() ?: "-"}")
        }
    }
}

/** Liest zur Fehlersuche, welche Daten Health Connect für die letzten [days] Tage tatsächlich hat. */
suspend fun HealthConnectManager.diagnose(days: Int = 30): HealthDiagnosis {
    val availability = availability()
    if (availability != HealthConnectAvailability.AVAILABLE) {
        return HealthDiagnosis(availability, emptySet(), emptyList(), emptyList())
    }
    val granted = grantedPermissions()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val start = today.minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant()
    val end = Instant.now()

    suspend fun <T : Record> summarize(
        label: String,
        type: KClass<T>,
        startOf: (T) -> Instant,
    ): RecordSummary {
        if (HealthPermission.getReadPermission(type) !in granted) {
            return RecordSummary(label, false, 0, emptyMap(), null)
        }
        return try {
            val all = mutableListOf<T>()
            var pageToken: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageSize = 1000,
                        pageToken = pageToken,
                    ),
                )
                all += response.records
                pageToken = response.pageToken
            } while (pageToken != null && all.size < 10_000)
            RecordSummary(
                type = label,
                permissionGranted = true,
                count = all.size,
                origins = all.groupingBy { it.metadata.dataOrigin.packageName }.eachCount(),
                latest = all.maxOfOrNull(startOf),
            )
        } catch (e: Exception) {
            RecordSummary(label, true, 0, emptyMap(), null, error = e.javaClass.simpleName + ": " + e.message)
        }
    }

    val records = listOf(
        summarize("Aktive Kalorien", ActiveCaloriesBurnedRecord::class) { it.startTime },
        summarize("Gesamt-Kalorien", TotalCaloriesBurnedRecord::class) { it.startTime },
        summarize("Trainings", ExerciseSessionRecord::class) { it.startTime },
        summarize("Schritte", StepsRecord::class) { it.startTime },
    )

    val dayList = (0 until days).map { offset ->
        val date = today.minusDays(offset.toLong())
        val reading = runCatching { readCalories(date) }.getOrNull()
        DayDiagnosis(date, reading?.activeKcal, reading?.totalKcal)
    }
    return HealthDiagnosis(availability, granted, records, dayList)
}
