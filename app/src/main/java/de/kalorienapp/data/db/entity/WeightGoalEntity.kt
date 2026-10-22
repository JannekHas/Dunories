package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.domain.model.WeightGoal
import java.time.LocalDate

/** Gewichtsziel; höchstens eines ist aktiv (endedDate = null), beendete bleiben als Historie. */
@Entity(tableName = "weight_goal")
data class WeightGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: Long,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val targetDate: Long?,
    val endedDate: Long?,
    val endWeightKg: Double?,
    val endReason: GoalEndReason?,
) {
    fun toDomain() = WeightGoal(
        id = id,
        startDate = LocalDate.ofEpochDay(startDate),
        startWeightKg = startWeightKg,
        targetWeightKg = targetWeightKg,
        targetDate = targetDate?.let(LocalDate::ofEpochDay),
        endedDate = endedDate?.let(LocalDate::ofEpochDay),
        endWeightKg = endWeightKg,
        endReason = endReason,
    )

    companion object {
        fun fromDomain(g: WeightGoal) = WeightGoalEntity(
            id = g.id,
            startDate = g.startDate.toEpochDay(),
            startWeightKg = g.startWeightKg,
            targetWeightKg = g.targetWeightKg,
            targetDate = g.targetDate?.toEpochDay(),
            endedDate = g.endedDate?.toEpochDay(),
            endWeightKg = g.endWeightKg,
            endReason = g.endReason,
        )
    }
}
