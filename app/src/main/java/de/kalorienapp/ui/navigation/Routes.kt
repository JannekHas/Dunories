package de.kalorienapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object TodayRoute

@Serializable
data object ProfileRoute

/** Gewicht, Körpermaße, erreichte Ziele. */
@Serializable
data object WeightRoute

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

/** Alle KI-Auswertungen, nach Monaten gruppiert. */
@Serializable
data object AllReportsRoute

/** Vorlieben für die KI-Auswertung (Ernährungsweise, Allergien, Alltag, mag ich nicht). */
@Serializable
data object FoodPreferencesRoute

@Serializable
data object ShoppingListRoute

/** Neues Gewichtsziel setzen. */
@Serializable
data object GoalRoute

/** Feier-Bildschirm für ein beendetes Ziel (auch aus „Erreichte Ziele“ erneut aufrufbar). */
@Serializable
data class GoalCelebrationRoute(val goalId: Long)

/** „Erfolg teilen“: Baukasten fürs teilbare Bild. */
@Serializable
data class GoalShareRoute(val goalId: Long)

/** Einträge eines Tages sortiert nach einem Nährwert ([de.kalorienapp.domain.RankedNutrient]-Name). */
@Serializable
data class DayNutrientsRoute(val epochDay: Long, val nutrient: String)

/** Ernährungsplan: Zeitraum und Tage. */
@Serializable
data object MealPlanRoute

/** Ein Tag des Ernährungsplans (0-basiert). */
@Serializable
data class MealPlanDayRoute(val dayIndex: Int)

/** Plan per Foto/Text importieren. */
@Serializable
data object MealPlanImportRoute

/** Tagesansicht für ein bestimmtes Datum (aus dem Kalender geöffnet). */
@Serializable
data class DayRoute(val epochDay: Long)

/** Ein-Foto-Erfassung (Kamera/Galerie/Text) für [epochDay]; mealType = optionale Vorauswahl. */
@Serializable
data class CaptureRoute(
    val epochDay: Long,
    val mealType: String? = null,
    /** Ernährungsplan: Plan-Tag (0-basiert), -1 = normales Erfassen. */
    val planDay: Int = -1,
    /** Plan-Punkt bearbeiten (wird beim Speichern ersetzt). */
    val planItemId: Long = 0,
    /** true = „Anpassen“ auf „Heute“: Plan-Mahlzeit als Vorschlag, gespeichert wird ein normaler Eintrag. */
    val fromPlan: Boolean = false,
)

/** entryId = 0 → neuer Eintrag für [epochDay]; mealType = Name eines [de.kalorienapp.domain.model.MealType] oder null. */
@Serializable
data class EntryEditRoute(
    val entryId: Long = 0,
    val epochDay: Long,
    val mealType: String? = null,
)
