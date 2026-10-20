package de.kalorienapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object TodayRoute

@Serializable
data object ProfileRoute

@Serializable
data object HealthDiagnosticsRoute

/** Ein-Foto-Erfassung (Kamera/Galerie/Text) für [epochDay]; mealType = optionale Vorauswahl. */
@Serializable
data class CaptureRoute(
    val epochDay: Long,
    val mealType: String? = null,
)

/** entryId = 0 → neuer Eintrag für [epochDay]; mealType = Name eines [de.kalorienapp.domain.model.MealType] oder null. */
@Serializable
data class EntryEditRoute(
    val entryId: Long = 0,
    val epochDay: Long,
    val mealType: String? = null,
)
