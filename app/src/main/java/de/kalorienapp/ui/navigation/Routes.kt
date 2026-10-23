package de.kalorienapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object TodayRoute

@Serializable
data object ProfileRoute

@Serializable
data object CalendarRoute

@Serializable
data object StatsRoute

@Serializable
data object RecipesRoute

@Serializable
data class RecipeDetailRoute(val id: Long)

/** id = 0 → neues Rezept; fromImport → Vorschlag aus dem [de.kalorienapp.recipe.RecipeDraftStore]. */
@Serializable
data class RecipeEditRoute(val id: Long = 0, val fromImport: Boolean = false)

/** Import aus dem Teilen-Menü bzw. eingefügtem Link (Inhalt liegt in der ShareInbox). */
@Serializable
data object RecipeImportRoute

@Serializable
data class WeeklyReportRoute(val id: Long)

@Serializable
data object ShoppingListRoute

/** Neues Gewichtsziel setzen. */
@Serializable
data object GoalRoute

/** Tagesansicht für ein bestimmtes Datum (aus dem Kalender geöffnet). */
@Serializable
data class DayRoute(val epochDay: Long)

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
