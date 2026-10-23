package de.kalorienapp

import android.content.Context
import de.kalorienapp.backup.BackupManager
import de.kalorienapp.capture.FoodAnalyzer
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.AppDatabase
import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.gemini.GeminiFiles
import de.kalorienapp.data.remote.gemini.GeminiRecipeAnalyzer
import de.kalorienapp.data.remote.gemini.GeminiWeeklyAnalyzer
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsClient
import de.kalorienapp.data.remote.social.SocialClient
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.BodyMeasurementRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.data.repository.ShoppingRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WaterRepository
import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.notify.CalorieNotifier
import de.kalorienapp.notify.WorkScheduler
import de.kalorienapp.recipe.RecipeDraftStore
import de.kalorienapp.recipe.RecipeImageStore
import de.kalorienapp.recipe.RecipeImporter
import de.kalorienapp.recipe.ShareInbox
import de.kalorienapp.ui.navigation.NavRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manuelle Dependency Injection: alle app-weiten Abhängigkeiten an einer Stelle. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    /** Lebt so lange wie der App-Prozess (Benachrichtigung aktualisieren, Receiver). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(context)

    val foodEntryDao = database.foodEntryDao()
    val profileRepository = ProfileRepository(database.profileDao())
    val foodEntryRepository = FoodEntryRepository(foodEntryDao)
    val foodLibraryRepository = FoodLibraryRepository(database.favoriteFoodDao(), foodEntryDao)
    val activityEntryRepository = ActivityEntryRepository(database.activityEntryDao())
    val goalRepository = GoalRepository(database.weightGoalDao(), profileRepository)
    val statsRepository = StatsRepository(database.statsDao(), goalRepository)
    val recipeImages = RecipeImageStore(context)
    val recipeRepository = RecipeRepository(database.recipeDao(), recipeImages)
    val bodyMeasurementRepository = BodyMeasurementRepository(database.bodyMeasurementDao())
    val waterRepository = WaterRepository(database.waterDao())
    val shoppingRepository = ShoppingRepository(database.shoppingDao())
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

    val weeklyReportRepository = WeeklyReportRepository(
        dao = database.weeklyReportDao(),
        foodDao = foodEntryDao,
        activityDao = database.activityEntryDao(),
        statsRepository = statsRepository,
        goalRepository = goalRepository,
        weightRepository = weightRepository,
        measurementRepository = bodyMeasurementRepository,
        waterRepository = waterRepository,
        settings = settings,
        analyzer = GeminiWeeklyAnalyzer(gemini),
    )

    val calorieNotifier = CalorieNotifier(context, settings, goalRepository, foodEntryRepository, activityEntryRepository)
    val workScheduler = WorkScheduler(context, settings, foodEntryDao)

    val backupManager = BackupManager(context, database, settings)

    /** Ziele aus Benachrichtigungen (Wiegen, Wochenauswertung). */
    val navRequests = NavRequests()
}
