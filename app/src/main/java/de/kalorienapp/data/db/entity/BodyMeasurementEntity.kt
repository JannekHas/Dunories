package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.BodyMeasurement
import de.kalorienapp.domain.model.MeasureType
import java.time.Instant

@Entity(tableName = "body_measurement", indices = [Index("measuredAt")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MeasureType,
    val value: Double,
    val measuredAt: Long,
) {
    fun toDomain() = BodyMeasurement(id, type, value, Instant.ofEpochMilli(measuredAt))

    companion object {
        fun fromDomain(m: BodyMeasurement) = BodyMeasurementEntity(m.id, m.type, m.value, m.measuredAt.toEpochMilli())
    }
}
