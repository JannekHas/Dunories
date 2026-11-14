package de.kalorienapp.domain

import de.kalorienapp.domain.model.FoodEntry
import kotlin.math.abs
import kotlin.math.max

/**
 * Gleiche Einträge zusammenfassen (Wunsch des Nutzers): z. B. 3× Salami-Stick à 10 g kurz hintereinander
 * → ein Eintrag mit 30 g. Nur bei gleichem Lebensmittel (Name, Einheit, Nährwerte pro 100), gleichem Tag,
 * gleicher Mahlzeit und höchstens [WINDOW_MS] nach der letzten Ergänzung – morgens und abends dasselbe
 * bleibt getrennt (sonst nicht mehr einzeln verschiebbar). Gerichte werden nie zusammengefasst.
 */
object EntryMerge {
    const val WINDOW_MS = 30 * 60 * 1000L

    /** Eintrag, in den [new] zum Zeitpunkt [now] einfließt, oder null. */
    fun findTarget(existing: List<FoodEntry>, new: FoodEntry, now: Long): FoodEntry? {
        if (!mergeable(new)) return null
        return existing
            .filter { it.id != new.id && mergeable(it) && sameFood(it, new) }
            .filter { val last = lastAdded(it); now - last in 0..WINDOW_MS }
            .maxByOrNull(::lastAdded)
    }

    fun merge(target: FoodEntry, new: FoodEntry, now: Long): FoodEntry = target.copy(
        amount = (target.amount ?: 0.0) + (new.amount ?: 0.0),
        nutrients = target.nutrients + new.nutrients,
        lastAddedAt = now,
    )

    private fun lastAdded(e: FoodEntry) = e.lastAddedAt ?: e.createdAt

    private fun mergeable(e: FoodEntry) = !e.isDish && (e.amount ?: 0.0) > 0

    private fun sameFood(a: FoodEntry, b: FoodEntry): Boolean {
        if (a.date != b.date || a.mealType != b.mealType || a.unit != b.unit) return false
        if (!a.name.trim().equals(b.name.trim(), ignoreCase = true)) return false
        val pa = a.nutrients.amountToPer100(a.amount!!)
        val pb = b.nutrients.amountToPer100(b.amount!!)
        return close(pa.kcal, pb.kcal) && close(pa.proteinG, pb.proteinG) &&
            close(pa.carbsG, pb.carbsG) && close(pa.fatG, pb.fatG)
    }

    /** Rundung beim Speichern/Umrechnen erlauben (1 % bzw. 0,5). */
    private fun close(x: Double, y: Double) = abs(x - y) <= max(0.5, max(abs(x), abs(y)) * 0.01)
}
