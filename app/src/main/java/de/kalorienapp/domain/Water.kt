package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FoodEntry
import kotlin.math.roundToInt

/** Wasser-Tagesziel: eigener Wert oder automatisch 35 ml pro kg, auf 250 ml gerundet (1,5–4 l). */
object Water {
    const val ML_PER_KG = 35
    const val GLASS_ML = 250
    const val BOTTLE_ML = 500

    fun goalMl(customMl: Int?, weightKg: Double): Int =
        customMl ?: ((weightKg * ML_PER_KG / GLASS_ML).roundToInt() * GLASS_ML).coerceIn(1500, 4000)

    /**
     * Getränke aus dem Tagebuch, die zum Wasser zählen (Wunsch des Nutzers): jeder Eintrag in ml
     * (Diet-Limo, Kaffee, Tee, Saft, Milch …) – außer Alkohol.
     */
    fun drinksMl(entries: List<FoodEntry>): Int = entries
        .filter { it.unit == AmountUnit.MILLILITER && !isAlcoholic(it.name) }
        .sumOf { it.amount ?: 0.0 }
        .roundToInt()

    /** Alkohol am Namen erkennen; „alkoholfrei“ zählt als normales Getränk. */
    fun isAlcoholic(name: String): Boolean {
        val n = name.lowercase()
        if ("alkoholfrei" in n || "0,0" in n || "0.0" in n) return false
        // Ganzes Wort oder Wortende (Rotwein, Weißbier) – nicht Wortanfang („Ginger Ale“, „Weintraubensaft“)
        val words = n.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return words.any { word -> ALCOHOL.any { word == it || (word.length > it.length + 2 && word.endsWith(it)) } }
    }

    private val ALCOHOL = listOf(
        "bier", "pils", "weizen", "radler", "wein", "sekt", "prosecco", "champagner", "schnaps", "wodka", "vodka",
        "whisky", "whiskey", "rum", "gin", "likör", "likoer", "tequila", "cocktail", "aperol", "spritz", "cider",
        "korn", "jägermeister", "jaegermeister", "ouzo", "sambuca", "glühwein", "gluehwein", "met",
    )
}
