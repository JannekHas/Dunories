package de.kalorienapp

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import android.content.Context
import de.kalorienapp.backup.BackupManager
import de.kalorienapp.capture.FoodAnalyzer
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.AppDatabase
import de.kalorienapp.data.remote.gemini.AppIdentity
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
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.capture.MealPlanImporter
import de.kalorienapp.recipe.ShareGuard
import de.kalorienapp.data.remote.gemini.GeminiMealPlanAnalyzer
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
    val database = AppDatabase.create(context)

    /** Lebt so lange wie der App-Prozess (Benachrichtigung aktualisieren, Receiver). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = AppSettings(context)

    val foodEntryDao = database.foodEntryDao()
    val profileRepository = ProfileRepository(database.profileDao(), database.bodyMeasurementDao())
    val foodEntryRepository = FoodEntryRepository(foodEntryDao)
    val foodLibraryRepository = FoodLibraryRepository(database.favoriteFoodDao(), foodEntryDao, database.mealComboDao())
    val mealPlanRepository = MealPlanRepository(database.mealPlanDao(), settings)
    val activityEntryRepository = ActivityEntryRepository(database.activityEntryDao())
    val goalRepository = GoalRepository(database.weightGoalDao(), profileRepository)
    val statsRepository = StatsRepository(database.statsDao(), goalRepository, settings.proteinOnly)
    val recipeImages = RecipeImageStore(context)
    val recipeRepository = RecipeRepository(database.recipeDao(), recipeImages)
    val bodyMeasurementRepository = BodyMeasurementRepository(database.bodyMeasurementDao())
    val waterRepository = WaterRepository(database.waterDao())
    val leftoverRepository = de.kalorienapp.data.repository.LeftoverRepository(database.leftoverDao())
    val shoppingRepository = ShoppingRepository(database.shoppingDao())
    val weightRepository = WeightRepository(database.weightEntryDao(), profileRepository)

    /** Social-Reiter: Level & Erfolge, live aus den vorhandenen Daten. */
    val gameRepository by lazy { // lazy: braucht `account`, das weiter unten entsteht
        de.kalorienapp.data.repository.GameRepository(
            database.foodEntryDao(), database.statsDao(), database.waterDao(), weightRepository, goalRepository,
            database.weeklyReportDao(), settings, account.user.map { it != null },
        )
    }

    // KI über den eigenen Worker; Anmeldung per Konto oder still angelegtem Gast-Konto.
    private val gemini = GeminiClient(BuildConfig.AI_BASE_URL) { account.aiToken() }

    /** Freunde (nur mit echtem Konto, nie als Gast). */
    val social = de.kalorienapp.account.SocialClient(BuildConfig.AI_BASE_URL) {
        if (account.user.value != null) account.idToken() else null
    }

    /** KI-Stufe (Gast/Konto/Premium) und Code-Einlösen beim Worker. */
    val premium = de.kalorienapp.account.PremiumClient(BuildConfig.AI_BASE_URL) { account.aiToken() }

    /** Gemeinsame Barcode-Sammlung (unbekannte Produkte, per Etikett von anderen erfasst). */
    val barcodeCommunity by lazy {
        de.kalorienapp.data.remote.BarcodeCommunityClient(BuildConfig.AI_BASE_URL, { account.aiToken() }, appScope)
    }

    val foodAnalyzer = FoodAnalyzer(
        context = context,
        openFoodFacts = OpenFoodFactsClient(context.cacheDir),
        gemini = gemini,
        ownBarcodes = database.ownBarcodeDao(),
        community = barcodeCommunity,
    )

    val mealPlanImporter = MealPlanImporter(context, GeminiMealPlanAnalyzer(gemini))
    val bodyAnalysisReader = de.kalorienapp.capture.BodyAnalysisReader(context, de.kalorienapp.data.remote.gemini.GeminiBodyAnalyzer(gemini))
    val streakScreenshotReader = de.kalorienapp.ui.social.StreakScreenshotReader(context, de.kalorienapp.data.remote.gemini.GeminiStreakReader(gemini))

    /** Feedback an Firestore (ohne Konto) + Fernschalter. */
    val feedbackClient = de.kalorienapp.data.remote.firebase.FeedbackClient(
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_API_KEY,
        AppIdentity.headers(context),
        BuildConfig.AI_BASE_URL,
    ) { account.aiToken() }

    /** Per Teilen-Menü empfangene Inhalte für den Rezept-Import. */
    val shareInbox = ShareInbox(ShareGuard(BuildConfig.APPLICATION_ID))
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

    /** Freiwilliges Konto + Cloud-Sicherung (Phase 2). */
    val account = de.kalorienapp.account.AccountManager(context)
    val cloudBackup = de.kalorienapp.backup.CloudBackupManager(context, account, backupManager, settings, AppIdentity.headers(context))

    /** Ziele aus Benachrichtigungen (Wiegen, Wochenauswertung). */
    val navRequests = NavRequests()
    /** Fotos/Beschreibungen, die die KI später im Hintergrund erkennt (bei Überlastung/ohne Netz). */
    val aiQueue = de.kalorienapp.capture.AiQueue(context)

    init {
        // Einladung (Testphase): Wer eingeladen wurde, meldet seine Tage mit Einträgen, bis die 7 geschafft sind –
        // dann bekommt der Einladende seine Tester-Punkte. Nur bei Änderungen, nur mit Konto.
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.invitePending, foodEntryDao.observeLoggedDays().map { it.size }) { pending, days ->
                if (pending && days >= INVITE_DAYS) days else null
            }.distinctUntilChanged().collect { days ->
                if (days != null && account.user.value != null && social.inviteProgress(days) == true) settings.setInvitePending(false)
            }
        }
    }

    private companion object {
        const val INVITE_DAYS = 7
    }
}
