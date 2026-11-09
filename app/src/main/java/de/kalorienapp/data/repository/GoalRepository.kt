package de.kalorienapp.data.repository

import de.kalorienapp.data.db.dao.WeightGoalDao
import de.kalorienapp.data.db.entity.WeightGoalEntity
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.GoalPlanner
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.domain.model.GoalPlan
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.WeightGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Gewichtsziele und daraus abgeleitete Tagesziele.
 * Der Plan wird täglich neu aus aktuellem Gewicht (Profil) und verbleibenden Tagen berechnet.
 */
class GoalRepository(
    private val dao: WeightGoalDao,
    private val profileRepository: ProfileRepository,
) {
    val activeGoal: Flow<WeightGoal?> = dao.observeActive().map { it?.toDomain() }

    val reachedGoals: Flow<List<WeightGoal>> = dao.observeReached().map { list -> list.map { it.toDomain() } }

    /** Profil, aktives Ziel und dessen heutiger Plan (null ohne Profil). */
    val current: Flow<GoalState?> = combine(profileRepository.profile, activeGoal) { profile, goal ->
        profile?.let { GoalState.of(it, goal, LocalDate.now()) }
    }

    /** Nur für beendete Ziele (Historie); das aktive Ziel wird durch ein neues ersetzt. */
    suspend fun deleteEnded(goal: WeightGoal) {
        if (!goal.isActive) dao.delete(goal.id)
    }

    suspend fun active(): WeightGoal? = dao.active()?.toDomain()

    suspend fun get(id: Long): WeightGoal? = dao.get(id)?.toDomain()

    /** Neues Ziel ab heute; ein laufendes Ziel wird als „ersetzt“ beendet. */
    suspend fun startGoal(currentWeightKg: Double, targetWeightKg: Double, targetDate: LocalDate?) {
        val today = LocalDate.now()
        dao.active()?.let { end(it.toDomain(), GoalEndReason.REPLACED, currentWeightKg, today) }
        val goal = WeightGoal(
            startDate = today,
            startWeightKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            targetDate = targetDate,
        )
        dao.upsert(WeightGoalEntity.fromDomain(if (goal.direction == Goal.MAINTAIN) goal.copy(targetDate = null) else goal))
    }

    /** Prüft das aktive Ziel; ist es erreicht oder abgelaufen, wird es beendet und zurückgegeben. */
    suspend fun checkEnded(currentWeightKg: Double, today: LocalDate = LocalDate.now()): WeightGoal? {
        val goal = active() ?: return null
        val reason = GoalPlanner.endReason(goal, currentWeightKg, today) ?: return null
        return end(goal, reason, currentWeightKg, today)
    }

    private suspend fun end(goal: WeightGoal, reason: GoalEndReason, weightKg: Double, today: LocalDate): WeightGoal {
        val ended = goal.copy(endedDate = today, endWeightKg = weightKg, endReason = reason)
        dao.upsert(WeightGoalEntity.fromDomain(ended))
        return ended
    }
}

data class GoalState(
    val profile: Profile,
    val goal: WeightGoal?,
    val plan: GoalPlan?,
) {
    val direction: Goal get() = goal?.direction ?: Goal.MAINTAIN

    fun targetsFor(year: Int): DailyTargets =
        GoalCalculator.targets(profile, year, direction, plan?.dailyAdjustmentKcal ?: 0, goal?.targetWeightKg)

    val targets: DailyTargets get() = targetsFor(LocalDate.now().year)

    companion object {
        fun of(profile: Profile, goal: WeightGoal?, today: LocalDate) =
            GoalState(profile, goal, goal?.let { GoalPlanner.plan(it, profile.weightKg, today) })
    }
}
