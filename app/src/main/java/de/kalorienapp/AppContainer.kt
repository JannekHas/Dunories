package de.kalorienapp

import android.content.Context
import de.kalorienapp.capture.FoodAnalyzer
import de.kalorienapp.data.db.AppDatabase
import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.gemini.GeminiFiles
import de.kalorienapp.data.remote.gemini.GeminiRecipeAnalyzer
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsClient
import de.kalorienapp.data.remote.social.SocialClient
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.recipe.RecipeDraftStore
import de.kalorienapp.recipe.RecipeImageStore
import de.kalorienapp.recipe.RecipeImporter
import de.kalorienapp.recipe.ShareInbox

/** Manuelle Dependency Injection: alle app-weiten Abhängigkeiten an einer Stelle. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val foodEntryRepository = FoodEntryRepository(database.foodEntryDao())
    val foodLibraryRepository = FoodLibraryRepository(database.favoriteFoodDao(), database.foodEntryDao())
    val activityEntryRepository = ActivityEntryRepository(database.activityEntryDao())
    val goalRepository = GoalRepository(database.weightGoalDao(), profileRepository)
    val statsRepository = StatsRepository(database.statsDao(), goalRepository)
    val recipeImages = RecipeImageStore(context)
    val recipeRepository = RecipeRepository(database.recipeDao(), recipeImages)
    val weightRepository = WeightRepository(database.weightEntryDao(), profileRepository)

    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY)

    val foodAnalyzer = FoodAnalyzer(
        context = context,
        openFoodFacts = OpenFoodFactsClient(),
        gemini = gemini,
    )

    /** Per Teilen-Menü empfangene Inhalte für den Rezept-Import. */
    val shareInbox = ShareInbox()
    val recipeDrafts = RecipeDraftStore()

    val recipeImporter = RecipeImporter(
        context = context,
        social = SocialClient(),
        analyzer = GeminiRecipeAnalyzer(gemini),
        files = GeminiFiles(gemini),
        imageStore = recipeImages,
    )
}
