package de.kalorienapp.domain

import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.Dish
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Leftover
import de.kalorienapp.domain.model.MealType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Reste / offene Mengen (Wunsch des Nutzers, Nov. 2026): mehrere Einträge zu einem Gericht zusammenfassen,
 * nur den gegessenen Anteil zählen, den Rest offen halten und später ganz oder teilweise übernehmen.
 */
object Leftovers {
    /** Schnellwahl für den gegessenen Anteil (Alles, ¾, ⅔, ½, ⅓, ¼). */
    val FRACTIONS = listOf(1.0, 0.75, 2.0 / 3.0, 0.5, 1.0 / 3.0, 0.25)

    /** Nach so vielen Tagen ohne Einfrieren fragt „Heute“: Noch da? */
    const val CHECK_AFTER_DAYS = 4L

    /** Weniger bleibt nicht als Rest stehen (Rundungsreste, ein Löffel im Topf). */
    private const val MIN_REST_AMOUNT = 1.0
    private const val MIN_REST_SHARE = 0.01

    /** Diese Einträge lassen sich zusammenfassen (Alt-Einträge ohne Menge nicht). */
    fun groupable(entries: List<FoodEntry>): List<FoodEntry> = entries.filter { (it.amount ?: 0.0) > 0 }

    /** Einträge → Zutaten; Gerichte werden in ihre Zutaten aufgelöst (eine Ebene tief wie bei der KI). */
    fun componentsOf(entries: List<FoodEntry>): List<DishComponent> = entries.flatMap { entry ->
        val amount = entry.amount ?: return@flatMap emptyList()
        when {
            entry.isDish -> entry.components
            amount <= 0 -> emptyList()
            else -> listOf(DishComponent(entry.name, amount, entry.unit, entry.nutrients.amountToPer100(amount)))
        }
    }

    /** Herkunft des Gerichts: gemeinsame Quelle, sonst „geschätzt“, sobald etwas geschätzt war, sonst Manuell. */
    fun combinedSource(entries: List<FoodEntry>): EntrySource {
        val sources = entries.map { it.source }.distinct()
        return when {
            sources.size == 1 -> sources.single()
            sources.any { it.isEstimate } -> EntrySource.AI_TEXT
            else -> EntrySource.MANUAL
        }
    }

    /** Zutaten in „gegessen“ ([eatenAmount] g/ml) und „Rest“ teilen; kleiner Rest → alles gegessen (Rest leer). */
    fun split(components: List<DishComponent>, eatenAmount: Double): Pair<List<DishComponent>, List<DishComponent>> {
        val total = Dish.totalAmount(components)
        if (total <= 0) return components to emptyList()
        val eaten = eatenAmount.coerceIn(0.0, total)
        val rest = total - eaten
        if (rest < MIN_REST_AMOUNT || rest < total * MIN_REST_SHARE) return components to emptyList()
        val eatenFactor = eaten / total
        return components.map { it.copy(amount = it.amount * eatenFactor) } to
            components.map { it.copy(amount = it.amount * (1 - eatenFactor)) }
    }

    /** Eintrag aus Zutaten: eine Zutat = normales Lebensmittel, mehrere = Gericht. */
    fun entryFrom(
        name: String,
        components: List<DishComponent>,
        date: LocalDate,
        mealType: MealType,
        source: EntrySource,
        createdAt: Long,
        sortKey: Long? = null,
        id: Long = 0,
    ): FoodEntry {
        val single = components.singleOrNull()
        return FoodEntry(
            id = id,
            date = date,
            mealType = mealType,
            name = name,
            amount = single?.amount ?: Dish.totalAmount(components),
            unit = single?.unit ?: Dish.unit(components),
            nutrients = single?.nutrients ?: Dish.total(components),
            source = source,
            createdAt = createdAt,
            sortKey = sortKey,
            components = if (single != null) emptyList() else components,
        )
    }

    /** Ergebnis von Zusammenfassen oder „Nicht alles gegessen“: was im Tag bleibt + offener Rest. */
    data class Split(val eaten: FoodEntry, val leftover: Leftover?)

    /**
     * [entries] (eine Mahlzeit, mindestens zwei) zu einem Gericht [name] zusammenfassen, davon [eatenAmount] g/ml gegessen.
     * Das Gericht steht an der Stelle des ersten Eintrags (Reihenfolge und Fasten-Uhrzeit bleiben).
     */
    fun group(entries: List<FoodEntry>, name: String, eatenAmount: Double, now: Long): Split? {
        val usable = groupable(entries)
        val first = usable.minByOrNull { it.orderKey } ?: return null
        val components = componentsOf(usable)
        if (components.isEmpty()) return null
        val source = combinedSource(usable)
        val (eaten, rest) = split(components, eatenAmount)
        val dish = entryFrom(
            name = name.trim(),
            components = eaten,
            date = first.date,
            mealType = first.mealType,
            source = source,
            createdAt = usable.minOf { it.createdAt },
            sortKey = first.sortKey,
        )
        return Split(dish, leftoverOf(name.trim(), rest, first, source, eatenAmount, now))
    }

    /** „Nicht alles gegessen“ an einem vorhandenen Eintrag (Gericht oder Lebensmittel mit Menge). */
    fun partial(entry: FoodEntry, eatenAmount: Double, now: Long): Split? {
        val components = componentsOf(listOf(entry))
        if (components.isEmpty()) return null
        val (eaten, rest) = split(components, eatenAmount)
        val updated = entryFrom(entry.name, eaten, entry.date, entry.mealType, entry.source, entry.createdAt, entry.sortKey, entry.id)
            .copy(lastAddedAt = entry.lastAddedAt)
        return Split(updated, leftoverOf(entry.name, rest, entry, entry.source, eatenAmount, now))
    }

    private fun leftoverOf(name: String, rest: List<DishComponent>, from: FoodEntry, source: EntrySource, portion: Double, now: Long) =
        rest.takeIf { it.isNotEmpty() }?.let {
            Leftover(
                name = name,
                components = it,
                createdDay = from.date,
                mealType = from.mealType,
                source = source,
                portionAmount = portion,
                createdAt = now,
            )
        }

    /** Ergebnis von „Rest übernehmen“: neuer Eintrag + was danach noch übrig ist (null = Rest aufgebraucht). */
    data class Take(val entry: FoodEntry, val remaining: Leftover?)

    fun take(leftover: Leftover, amount: Double, date: LocalDate, mealType: MealType, now: Long): Take {
        val (eaten, rest) = split(leftover.components, amount)
        val entry = entryFrom(leftover.name, eaten, date, mealType, leftover.source, now)
        val remaining = rest.takeIf { it.isNotEmpty() }?.let {
            leftover.copy(components = it, portionAmount = amount, dismissedDay = null)
        }
        return Take(entry, remaining)
    }

    /** Vorauswahl beim Übernehmen: zuletzt gegessene Portion, höchstens der ganze Rest. */
    fun defaultPortion(leftover: Leftover): Double =
        leftover.portionAmount?.takeIf { it > 0 }?.coerceAtMost(leftover.amount) ?: leftover.amount

    /** Auf „Heute“ in [meal] von [day] vorschlagen? Nicht eingefroren, nicht weggeklickt, nicht in der eigenen Mahlzeit. */
    fun suggestOn(leftover: Leftover, day: LocalDate, meal: MealType): Boolean = when {
        leftover.frozen || leftover.dismissedDay == day -> false
        leftover.createdDay.isBefore(day) -> true
        leftover.createdDay == day -> meal.ordinal > leftover.mealType.ordinal && meal != MealType.SNACK
        else -> false
    }

    /**
     * In welcher leeren Mahlzeit der Vorschlag steht: Mittag (bis 15 Uhr), sonst Abend.
     * Kein Platz (beide schon gefüllt) → kein Vorschlag – die Reste stehen dann nur beim Erfassen.
     */
    fun suggestionMeal(hour: Int, emptyMeals: Set<MealType>): MealType? {
        val candidates = if (hour < 15) listOf(MealType.LUNCH, MealType.DINNER) else listOf(MealType.DINNER)
        return candidates.firstOrNull { it in emptyMeals }
    }

    /** Seit [CHECK_AFTER_DAYS] Tagen offen und nicht eingefroren → „Noch da?“. */
    fun needsCheck(leftover: Leftover, today: LocalDate): Boolean =
        !leftover.frozen && ChronoUnit.DAYS.between(leftover.checkedDay ?: leftover.createdDay, today) >= CHECK_AFTER_DAYS

    fun daysOpen(leftover: Leftover, today: LocalDate): Long = ChronoUnit.DAYS.between(leftover.createdDay, today)
}
