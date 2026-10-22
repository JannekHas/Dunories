package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.WeightEntry
import java.time.Instant

/** Eine Gewichtsmessung – beliebig viele pro Tag, nichts wird überschrieben. */
@Entity(tableName = "weight_entry", indices = [Index("measuredAt")])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Zeitpunkt der Messung (Epoch-Millisekunden). */
    val measuredAt: Long,
    val weightKg: Double,
) {
    fun toDomain() = WeightEntry(id = id, measuredAt = Instant.ofEpochMilli(measuredAt), weightKg = weightKg)

    companion object {
        fun fromDomain(w: WeightEntry) = WeightEntryEntity(w.id, w.measuredAt.toEpochMilli(), w.weightKg)
    }
}
