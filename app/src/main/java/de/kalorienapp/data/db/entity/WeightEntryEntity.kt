package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.WeightEntry
import java.time.LocalDate

/** Höchstens ein Gewicht pro Tag – ein neuer Eintrag am selben Tag ersetzt den alten. */
@Entity(tableName = "weight_entry")
data class WeightEntryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val weightKg: Double,
    val createdAt: Long,
) {
    fun toDomain() = WeightEntry(LocalDate.ofEpochDay(dateEpochDay), weightKg)

    companion object {
        fun fromDomain(w: WeightEntry) = WeightEntryEntity(w.date.toEpochDay(), w.weightKg, System.currentTimeMillis())
    }
}
