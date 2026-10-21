package de.kalorienapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.ActivitySource
import de.kalorienapp.domain.model.DailyActivity
import java.time.LocalDate

/**
 * Aktive Kalorien pro Tag (aus Health Connect geschätzt oder manuell eingetragen) – damit Kalender
 * und Statistiken auch ältere Tage kennen. Manuelle Werte werden nie automatisch überschrieben.
 */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val dateEpochDay: Long,
    val activeKcal: Int,
    val updatedAt: Long,
    val steps: Long? = null,
    val source: ActivitySource? = null,
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false,
) {
    fun toDomain() = DailyActivity(
        date = LocalDate.ofEpochDay(dateEpochDay),
        activeKcal = activeKcal,
        steps = steps,
        source = if (manual) ActivitySource.MANUAL else source,
    )
}
