package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trainingskalorien pro Tag aus Health Connect (bereits ohne Grundumsatz) – zwischengespeichert,
 * damit Kalender und Statistiken auch ältere Tage kennen.
 */
@Entity(tableName = "daily_training")
data class DailyTrainingEntity(
    @PrimaryKey val dateEpochDay: Long,
    val activeKcal: Int,
    val workoutCount: Int,
    val updatedAt: Long,
)
