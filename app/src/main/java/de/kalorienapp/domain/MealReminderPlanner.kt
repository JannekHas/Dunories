package de.kalorienapp.domain

import de.kalorienapp.domain.model.MealType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Eintrage-Erinnerung: lernt je Hauptmahlzeit die übliche Eintragszeit (Median) und erinnert
 * [DELAY_MINUTES] später, falls die Mahlzeit dann noch fehlt. Snacks sind zu unregelmäßig.
 */
object MealReminderPlanner {
    const val DELAY_MINUTES = 60L
    private const val MIN_DAYS = 3
    private val LATEST: LocalTime = LocalTime.of(22, 30)
    val MEALS = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)

    /** Übliche Eintragszeit je Mahlzeit; nur bei genug Daten. [samples] = erste Eintragszeit je Tag und Mahlzeit. */
    fun typicalTimes(samples: Map<MealType, List<LocalTime>>): Map<MealType, LocalTime> =
        MEALS.mapNotNull { meal ->
            val times = samples[meal].orEmpty()
            ReminderPlanner.typicalFirstEntryTime(times)?.takeIf { times.size >= MIN_DAYS }?.let { meal to it }
        }.toMap()

    /** Erinnerungszeit je Mahlzeit (übliche Zeit + Verzögerung, spätestens 22:30). */
    fun reminderTimes(typical: Map<MealType, LocalTime>): Map<MealType, LocalTime> =
        typical.mapValues { (_, time) ->
            val reminder = time.plusMinutes(DELAY_MINUTES)
            // Über Mitternacht gerutscht oder zu spät → 22:30.
            if (reminder.isBefore(time) || reminder.isAfter(LATEST)) LATEST else reminder
        }

    /** Nächster Erinnerungszeitpunkt nach [now] (heute oder morgen) samt Mahlzeit. */
    fun next(reminders: Map<MealType, LocalTime>, now: LocalDateTime): Pair<LocalDateTime, MealType>? =
        reminders.map { (meal, time) ->
            val today = now.toLocalDate().atTime(time)
            (if (today.isAfter(now)) today else today.plusDays(1)) to meal
        }.minByOrNull { it.first }

    /** Erinnern? Nur wenn für die Mahlzeit heute noch nichts eingetragen ist. */
    fun isDue(meal: MealType, mealsLoggedToday: Set<MealType>): Boolean = meal !in mealsLoggedToday

    /** Erste Eintragszeit je Tag und Mahlzeit aus (Tag, Mahlzeit, Zeit) – nur am selben Tag erfasste. */
    fun samples(entries: List<Triple<LocalDate, MealType, LocalTime>>): Map<MealType, List<LocalTime>> =
        entries.groupBy { it.first to it.second }
            .map { (key, list) -> key.second to list.minOf { it.third } }
            .groupBy({ it.first }, { it.second })
}
