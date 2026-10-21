package de.kalorienapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zwischenspeicher der aktiven Kalorien pro Tag aus Health Connect – damit Kalender und
 * Statistiken (Phase 4) auch ältere Tage kennen, ohne Health Connect erneut zu fragen.
 */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val dateEpochDay: Long,
    val activeKcal: Int,
    val updatedAt: Long,
    // Ungenutzte Spalten aus Schema-Version 4 (siehe AppDatabase.MIGRATION_3_4).
    val steps: Long? = null,
    val source: String? = null,
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false,
)
