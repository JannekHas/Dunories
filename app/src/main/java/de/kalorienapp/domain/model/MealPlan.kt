package de.kalorienapp.domain.model

import java.time.LocalDate

/**
 * Einstellungen des Ernährungsplans (aus = in der App unsichtbar). **Rhythmus** und **Laufzeit** sind getrennt
 * (Tester-Feedback: 30 Tage, jede Woche gleich → nur 7 Tage eintragen): Der Plan wiederholt sich alle [days] Tage
 * ab [startEpochDay] und gilt bis [end] bzw. ohne Ende ([openEnded]); „Verlängern“ schiebt nur das Ende weiter.
 */
data class MealPlanSettings(
    val enabled: Boolean = false,
    /** Plan-Tag 1 des ersten Durchlaufs. */
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    /** Länge eines Durchlaufs, 1–[MAX_DAYS] (7 = Wochenplan). */
    val days: Int = 7,
    /** Letzter gültiger Tag; null = Ende des ersten Durchlaufs. */
    val endEpochDay: Long? = null,
    /** „Beenden“ getippt: läuft bis [end] normal weiter, danach automatisch aus (keine Verlängern-Frage mehr). */
    val stopAtEnd: Boolean = false,
    /** Läuft ohne Ende (keine Verlängern-Frage); [end] spielt dann keine Rolle. */
    val openEnded: Boolean = false,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)

    /** Letzter Tag, für den der Plan gilt. */
    val end: LocalDate get() = LocalDate.ofEpochDay(endEpochDay ?: (startEpochDay + days - 1))

    companion object {
        const val MAX_DAYS = 14
    }
}

/**
 * Ein Lebensmittel oder Gericht im Plan: [dayIndex] 0 = Plan-Tag 1. Nährwerte pro 100 g/ml wie im Formular;
 * bei Gerichten stehen die Zutaten in [components] (Summen dann aus den Zutaten).
 */
data class MealPlanItem(
    val id: Long = 0,
    val dayIndex: Int,
    val mealType: MealType,
    val position: Int = 0,
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
    val components: List<DishComponent> = emptyList(),
) {
    val nutrients: Nutrients get() = per100.per100ToAmount(amount)

    fun toEntry(date: LocalDate, createdAt: Long) = FoodEntry(
        date = date,
        mealType = mealType,
        name = name,
        amount = amount,
        unit = unit,
        nutrients = nutrients,
        source = EntrySource.PLAN,
        createdAt = createdAt,
        components = components,
    )

    fun toDraft() = FoodDraft(
        name = name,
        amount = amount,
        unit = unit,
        per100 = per100,
        source = EntrySource.PLAN,
        components = components.map { FoodDraft(it.name, it.amount, it.unit, it.per100, EntrySource.PLAN) },
    )

    companion object {
        /** Aus einem (Vorschlags-)Eintrag – so landen Erfassen, Barcode, KI usw. im Plan. */
        fun fromEntry(entry: FoodEntry, dayIndex: Int, position: Int): MealPlanItem {
            val amount = entry.amount?.takeIf { it > 0 } ?: 100.0
            return MealPlanItem(
                dayIndex = dayIndex,
                mealType = entry.mealType,
                position = position,
                name = entry.name,
                amount = amount,
                unit = entry.unit,
                per100 = entry.nutrients.amountToPer100(amount),
                components = entry.components,
            )
        }
    }
}

/** Welcher Plan-Tag wann gilt, Verlängern. */
object MealPlanSchedule {
    /** Plan-Tag (0-basiert) für [date], sonst null (aus, vor dem Start oder nach dem Ende). */
    fun dayIndex(settings: MealPlanSettings, date: LocalDate): Int? {
        if (!settings.enabled || date.isBefore(settings.start)) return null
        if (!settings.openEnded && date.isAfter(settings.end)) return null
        return ((date.toEpochDay() - settings.startEpochDay) % settings.days).toInt()
    }

    /** Erster Tag des Durchlaufs, in dem [today] liegt (bzw. des ersten/letzten, wenn davor/danach). */
    fun cycleStart(settings: MealPlanSettings, today: LocalDate): LocalDate {
        val last = if (settings.openEnded) maxOf(today.toEpochDay(), settings.startEpochDay) else settings.end.toEpochDay()
        val reference = today.toEpochDay().coerceIn(settings.startEpochDay, maxOf(last, settings.startEpochDay))
        val offset = (reference - settings.startEpochDay) / settings.days * settings.days
        return settings.start.plusDays(offset)
    }

    fun isExpired(settings: MealPlanSettings, today: LocalDate) =
        settings.enabled && !settings.openEnded && today.isAfter(settings.end)

    /** Frage „Stimmt dein Plan noch?“ – am letzten gültigen Tag und danach (nicht nach „Beenden“). */
    fun askToExtend(settings: MealPlanSettings, today: LocalDate) =
        settings.enabled && !settings.openEnded && !settings.stopAtEnd && !today.isBefore(settings.end)

    /** Nach „Beenden“ ist der letzte Tag vorbei → Plan ausschalten (Inhalt bleibt). */
    fun finishIfEnded(settings: MealPlanSettings, today: LocalDate): MealPlanSettings =
        if (settings.enabled && !settings.openEnded && settings.stopAtEnd && today.isAfter(settings.end)) {
            settings.copy(enabled = false, stopAtEnd = false)
        } else {
            settings
        }

    /**
     * Weitere Termine von Plan-Tag [dayIndex] nach dem aktuellen Durchlauf (für „auch 23.11., 30.11. …“),
     * höchstens [limit]; [more] = es gibt noch weitere.
     */
    fun laterDates(settings: MealPlanSettings, dayIndex: Int, today: LocalDate, limit: Int = 2): Pair<List<LocalDate>, Boolean> {
        val first = cycleStart(settings, today).plusDays(dayIndex.toLong())
        val dates = generateSequence(first.plusDays(settings.days.toLong())) { it.plusDays(settings.days.toLong()) }
            .takeWhile { settings.openEnded || !it.isAfter(settings.end) }
            .take(limit + 1)
            .toList()
        return dates.take(limit) to (dates.size > limit)
    }

    /** Um einen Durchlauf verlängern – nach einer Pause so oft, bis heute wieder enthalten ist. */
    fun extend(settings: MealPlanSettings, today: LocalDate): MealPlanSettings {
        var end = settings.end.toEpochDay() + settings.days
        while (end < today.toEpochDay()) end += settings.days
        return settings.copy(endEpochDay = end, stopAtEnd = false)
    }
}
