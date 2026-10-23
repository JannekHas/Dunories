package de.kalorienapp.domain

import de.kalorienapp.domain.model.Sex
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Fastenplan: tägliches Essensfenster (16:8 …) oder 5:2 (zwei Fastentage pro Woche). */
enum class FastingPlan(val fastHours: Int?) {
    H16_8(16), H18_6(18), H20_4(20), CUSTOM(null), FIVE_TWO(null)
}

data class FastingConfig(
    val plan: FastingPlan = FastingPlan.H16_8,
    /** Beginn des Essensfensters (tägliche Pläne). */
    val windowStart: LocalTime = LocalTime.of(12, 0),
    /** Fastenstunden beim eigenen Plan (12–23). */
    val customFastHours: Int = 16,
    /** Fastentage bei 5:2. */
    val fastDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
) {
    val isDaily: Boolean get() = plan != FastingPlan.FIVE_TWO
    val fastHours: Int get() = plan.fastHours ?: customFastHours.coerceIn(12, 23)
    val windowHours: Int get() = 24 - fastHours
    val windowEnd: LocalTime get() = windowStart.plusHours(windowHours.toLong())
}

enum class FastingPhase { FASTING, EATING, FAST_DAY, NORMAL_DAY }

/** Aktueller Stand: Phase, wann sie endet und wie weit sie fortgeschritten ist (0..1). */
data class FastingStatus(val phase: FastingPhase, val phaseEnd: LocalDateTime, val progress: Float)

/** Kennzahlen für die Statistik. */
data class FastingStats(
    /** Tage mit Einträgen, die ausgewertet wurden. */
    val evaluatedDays: Int,
    /** Davon eingehalten (alles im Essensfenster bzw. Fastentag unter dem Limit). */
    val keptDays: Int,
    /** Ø Pause zwischen letztem Eintrag am Vortag und erstem Eintrag (Stunden), null ohne Daten. */
    val averageFastHours: Double?,
)

/** Reine Logik fürs Intervallfasten (unit-getestet). */
object Fasting {

    /** Kalorienlimit an 5:2-Fastentagen. */
    fun fastDayLimitKcal(sex: Sex): Int = if (sex == Sex.MALE) 600 else 500

    fun isInEatingWindow(config: FastingConfig, time: LocalTime): Boolean {
        val start = config.windowStart
        val end = config.windowEnd
        // Fenster über Mitternacht (z. B. 20–4 Uhr) berücksichtigen.
        return if (start <= end) time >= start && time < end else time >= start || time < end
    }

    fun status(config: FastingConfig, now: LocalDateTime): FastingStatus {
        if (!config.isDaily) {
            val fastDay = now.dayOfWeek in config.fastDays
            val end = now.toLocalDate().plusDays(1).atStartOfDay()
            val progress = Duration.between(now.toLocalDate().atStartOfDay(), now).toMinutes() / (24f * 60)
            return FastingStatus(if (fastDay) FastingPhase.FAST_DAY else FastingPhase.NORMAL_DAY, end, progress)
        }
        val eating = isInEatingWindow(config, now.toLocalTime())
        val (phaseStart, phaseEnd) = if (eating) {
            val start = lastOccurrence(config.windowStart, now)
            start to start.plusHours(config.windowHours.toLong())
        } else {
            val start = lastOccurrence(config.windowEnd, now)
            start to start.plusHours(config.fastHours.toLong())
        }
        val total = Duration.between(phaseStart, phaseEnd).toMinutes().coerceAtLeast(1)
        val done = Duration.between(phaseStart, now).toMinutes()
        return FastingStatus(
            if (eating) FastingPhase.EATING else FastingPhase.FASTING,
            phaseEnd,
            (done.toFloat() / total).coerceIn(0f, 1f),
        )
    }

    /** Nächster Wechsel (Beginn bzw. Ende des Essensfensters) – für die Benachrichtigungen. */
    fun nextTransition(config: FastingConfig, now: LocalDateTime): Pair<LocalDateTime, Boolean>? {
        if (!config.isDaily) return null
        val nextStart = nextOccurrence(config.windowStart, now)
        val nextEnd = nextOccurrence(config.windowEnd, now)
        // true = Essensfenster beginnt
        return if (nextStart <= nextEnd) nextStart to true else nextEnd to false
    }

    /**
     * Auswertung über Tage: [entryTimes] = Eintragszeitpunkte je Tag (nur am selben Tag erfasste),
     * [kcalByDay] = gegessene kcal (für 5:2).
     */
    fun stats(
        config: FastingConfig,
        days: List<LocalDate>,
        entryTimes: Map<LocalDate, List<LocalTime>>,
        kcalByDay: Map<LocalDate, Double>,
        sex: Sex,
    ): FastingStats {
        val evaluated = days.filter { !entryTimes[it].isNullOrEmpty() }
        val kept = evaluated.count { day ->
            if (config.isDaily) {
                entryTimes.getValue(day).all { isInEatingWindow(config, it) }
            } else {
                day.dayOfWeek !in config.fastDays || (kcalByDay[day] ?: 0.0) <= fastDayLimitKcal(sex)
            }
        }
        val gaps = evaluated.mapNotNull { day ->
            val previousLast = entryTimes[day.minusDays(1)]?.maxOrNull() ?: return@mapNotNull null
            val first = entryTimes.getValue(day).min()
            Duration.between(day.minusDays(1).atTime(previousLast), day.atTime(first)).toMinutes() / 60.0
        }
        return FastingStats(evaluated.size, kept, gaps.takeIf { it.isNotEmpty() }?.average())
    }

    private fun lastOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today <= now) today else today.minusDays(1)
    }

    private fun nextOccurrence(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today > now) today else today.plusDays(1)
    }
}
