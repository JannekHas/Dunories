package de.kalorienapp

import android.content.Context
import de.kalorienapp.capture.FoodAnalyzer
import de.kalorienapp.data.db.AppDatabase
import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsClient
import de.kalorienapp.data.repository.ActivityRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.health.HealthConnectManager

/** Manuelle Dependency Injection: alle app-weiten Abhängigkeiten an einer Stelle. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val foodEntryRepository = FoodEntryRepository(database.foodEntryDao())
    val foodLibraryRepository = FoodLibraryRepository(database.favoriteFoodDao(), database.foodEntryDao())
    val activityRepository = ActivityRepository(HealthConnectManager(context), database.dailyActivityDao())

    val foodAnalyzer = FoodAnalyzer(
        context = context,
        openFoodFacts = OpenFoodFactsClient(),
        gemini = GeminiClient(BuildConfig.GEMINI_API_KEY),
    )
}
