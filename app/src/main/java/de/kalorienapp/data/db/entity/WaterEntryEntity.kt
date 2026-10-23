package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Getrunkenes Wasser (eine Zeile pro Antippen, damit „Rückgängig“ den letzten Eintrag entfernt). */
@Entity(tableName = "water_entry", indices = [Index("dateEpochDay")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val ml: Int,
    val createdAt: Long,
)
