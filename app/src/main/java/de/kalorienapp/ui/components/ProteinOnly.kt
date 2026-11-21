package de.kalorienapp.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Modus „Nur Eiweiß“ (Profil → Ziele): Kalorien, Kohlenhydrate und Fett werden überall ausgeblendet,
 * nur Eiweiß zählt. Erfasst und gespeichert wird weiter alles – Zurückschalten zeigt sofort wieder alles.
 * Gesetzt in [de.kalorienapp.ui.navigation.AppNavHost].
 */
val LocalProteinOnly = staticCompositionLocalOf { false }

/** „Gewicht tracken“ (Profil → Ziele, standardmäßig an); aus = Reiter „Körper“ in der Statistik fehlt. */
val LocalWeightEnabled = staticCompositionLocalOf { true }
