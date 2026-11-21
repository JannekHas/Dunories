package de.kalorienapp.domain

import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import java.time.LocalDate

/**
 * Tipps nach Verhalten (Paradox of the Active User): eine Funktion wird erst gezeigt, wenn man sie
 * gerade brauchen könnte – je einmal, höchstens einer pro Tag (Auswahl des Nutzers Nov. 2026).
 * Die Namen sind zugleich die Schlüssel in `AppSettings.seenTips`.
 */
enum class BehaviorTip {
    /** Gleiches Lebensmittel zum 3. Mal in 14 Tagen → „Als Favorit speichern?“ */
    FAVORITE,

    /** Gleiche Zusammenstellung in derselben Mahlzeit an 2 Tagen → „Als Kombi speichern?“ */
    COMBO,

    /** Mehrere Fotos einzeln für eine Mahlzeit → beim Erfassen „Ein Foto reicht für den ganzen Teller“. */
    ONE_PHOTO,

    /** Mehrmals von Hand abgetippt → beim Erfassen „Barcode oder Nährwerttabelle fotografieren“. */
    SCAN_INSTEAD,

    /** Gelöscht und gleich in einer anderen Mahlzeit neu eingetragen → Verschieben-Tipp (heißt wie der alte feste Tipp). */
    MOVE,
}

/** Gerade gelöschter Eintrag – um „gelöscht und woanders neu eingetragen“ zu erkennen. */
data class DeletedEntry(val name: String, val date: LocalDate, val mealType: MealType, val deletedAt: Long)

object BehaviorTips {
    const val FAVORITE_COUNT = 3
    const val LOOKBACK_DAYS = 14L
    const val PHOTO_COUNT = 3
    const val PHOTO_WINDOW_MS = 10 * 60_000L

    /** Einträge eines Fotos werden mit fortlaufender Zeit gespeichert (ms auseinander) → eine Aufnahme. */
    private const val SAME_CAPTURE_MS = 2_000L
    const val MANUAL_COUNT = 5
    const val MOVE_WINDOW_MS = 15 * 60_000L

    fun key(name: String) = name.trim().lowercase()

    /**
     * Heute eingetragenes Lebensmittel, das in [entries] (letzte 14 Tage inkl. heute) mindestens 3× vorkommt
     * und noch kein Favorit ist; das zuletzt eingetragene zuerst. Gerichte zählen mit.
     */
    fun favoriteCandidate(entries: List<FoodEntry>, today: LocalDate, favoriteNames: Set<String>): FoodEntry? {
        val counts = entries.filter { it.date >= today.minusDays(LOOKBACK_DAYS) }.groupingBy { key(it.name) }.eachCount()
        return entries
            .filter { it.date == today && key(it.name).isNotEmpty() && key(it.name) !in favoriteNames }
            .sortedByDescending { it.createdAt }
            .firstOrNull { (counts[key(it.name)] ?: 0) >= FAVORITE_COUNT }
    }

    /**
     * Mahlzeit von heute mit mindestens 2 Einträgen, deren Lebensmittel alle schon an einem früheren Tag
     * (letzte 14 Tage) in derselben Mahlzeit vorkamen – und die noch nicht als Kombi gespeichert ist.
     */
    fun comboCandidate(
        entries: List<FoodEntry>,
        today: LocalDate,
        comboKeys: Set<Set<String>>,
    ): Pair<MealType, List<FoodEntry>>? {
        val recent = entries.filter { it.date >= today.minusDays(LOOKBACK_DAYS) }
        val todayMeals = recent.filter { it.date == today }.groupBy { it.mealType }
        val earlier = recent.filter { it.date < today }.groupBy { it.date to it.mealType }
            .mapValues { (_, list) -> list.map { key(it.name) }.toSet() }
        return todayMeals.entries.firstNotNullOfOrNull { (meal, list) ->
            val keys = list.map { key(it.name) }.toSet()
            val repeated = keys.size >= 2 && keys !in comboKeys &&
                earlier.any { (dayMeal, earlierKeys) -> dayMeal.second == meal && earlierKeys.containsAll(keys) }
            if (repeated) meal to list else null
        }
    }

    /** Mindestens 3 einzelne Foto-Aufnahmen für dieselbe Mahlzeit desselben Tages innerhalb von 10 Minuten. */
    fun manySinglePhotos(entries: List<FoodEntry>): Boolean =
        entries.filter { it.source == EntrySource.AI_PHOTO }
            .groupBy { it.date to it.mealType }
            .values
            .any { list ->
                // Einträge eines Fotos liegen ms auseinander → zu Aufnahmen zusammenfassen.
                val captures = mutableListOf<Long>()
                list.map { it.createdAt }.sorted().forEach { t ->
                    if (captures.isEmpty() || t - captures.last() > SAME_CAPTURE_MS) captures += t
                }
                captures.indices.any { i ->
                    val end = i + PHOTO_COUNT - 1
                    end < captures.size && captures[end] - captures[i] <= PHOTO_WINDOW_MS
                }
            }

    /** Der gelöschte Eintrag wurde kurz danach am selben Tag in einer anderen Mahlzeit neu eingetragen. */
    fun reEnteredElsewhere(deleted: DeletedEntry?, entries: List<FoodEntry>): Boolean {
        deleted ?: return false
        return entries.any {
            key(it.name) == key(deleted.name) && it.date == deleted.date && it.mealType != deleted.mealType &&
                it.createdAt >= deleted.deletedAt && it.createdAt - deleted.deletedAt <= MOVE_WINDOW_MS
        }
    }
}
