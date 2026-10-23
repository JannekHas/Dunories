package de.kalorienapp.domain

import kotlin.math.roundToInt

/** Wasser-Tagesziel: eigener Wert oder automatisch 35 ml pro kg, auf 250 ml gerundet (1,5–4 l). */
object Water {
    const val ML_PER_KG = 35
    const val GLASS_ML = 250
    const val BOTTLE_ML = 500

    fun goalMl(customMl: Int?, weightKg: Double): Int =
        customMl ?: ((weightKg * ML_PER_KG / GLASS_ML).roundToInt() * GLASS_ML).coerceIn(1500, 4000)
}
