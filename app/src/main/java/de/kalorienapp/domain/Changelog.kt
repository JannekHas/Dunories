package de.kalorienapp.domain

/**
 * Welche Patchnotes nach einem Update gezeigt werden (Wunsch des Nutzers: einmal auf „Heute“, bis gelesen).
 * Versionen = versionCode; [available] = Versionen, für die es Patchnotes in der App gibt.
 */
object Changelog {
    /**
     * Ungelesene Versionen, neueste zuerst. [lastSeen] = null heißt: Funktion neu für diesen Nutzer →
     * nur die aktuelle Version zeigen, nicht die ganze Geschichte.
     */
    fun unseen(lastSeen: Int?, current: Int, available: List<Int>): List<Int> {
        val shown = if (lastSeen == null) available.filter { it == current } else available.filter { it in (lastSeen + 1)..current }
        return shown.sortedDescending()
    }
}
