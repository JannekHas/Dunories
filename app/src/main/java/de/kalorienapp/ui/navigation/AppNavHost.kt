package de.kalorienapp.ui.navigation

import de.kalorienapp.ui.plan.MealPlanDayScreen
import de.kalorienapp.ui.plan.MealPlanImportScreen
import de.kalorienapp.ui.plan.MealPlanScreen
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import de.kalorienapp.ui.components.GlowBackground
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.LocalTips
import de.kalorienapp.ui.components.Tip
import de.kalorienapp.ui.components.TipCaret
import de.kalorienapp.ui.components.TipSlot
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavDestination
import de.kalorienapp.ui.components.PressableSurface
import de.kalorienapp.ui.stats.WeightScreen
import java.time.LocalDate
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.kalorienapp.BuildConfig
import de.kalorienapp.R
import de.kalorienapp.ui.capture.CaptureScreen
import de.kalorienapp.ui.entry.EntryEditScreen
import de.kalorienapp.ui.goal.GoalCelebrationScreen
import de.kalorienapp.ui.goal.GoalScreen
import de.kalorienapp.ui.goal.GoalShareScreen
import de.kalorienapp.ui.onboarding.OnboardingScreen
import de.kalorienapp.recipe.ImportSource
import de.kalorienapp.AppContainer
import de.kalorienapp.ui.stats.WeeklyReportScreen
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import de.kalorienapp.ui.profile.ProfileScreen
import de.kalorienapp.ui.recipes.RecipeDetailScreen
import de.kalorienapp.ui.recipes.RecipeEditScreen
import de.kalorienapp.ui.recipes.RecipeImportScreen
import de.kalorienapp.ui.recipes.RecipesScreen
import de.kalorienapp.ui.recipes.ShoppingListScreen
import de.kalorienapp.ui.stats.StatsScreen
import de.kalorienapp.ui.theme.Neon
import de.kalorienapp.ui.today.TodayScreen
import de.kalorienapp.ui.stats.FoodPreferencesScreen
import de.kalorienapp.ui.stats.AllReportsScreen
import de.kalorienapp.ui.today.DayNutrientsScreen

private data class TopLevelDestination(val route: Any, val icon: @Composable () -> ImageVector, val labelRes: Int)

private val topLevelDestinations = listOf(
    TopLevelDestination(TodayRoute, { Icons.Filled.Home }, R.string.nav_today),
    TopLevelDestination(StatsRoute, { ImageVector.vectorResource(R.drawable.ic_bar_chart) }, R.string.nav_stats),
    TopLevelDestination(WeightRoute, { ImageVector.vectorResource(R.drawable.ic_scale) }, R.string.nav_weight),
    TopLevelDestination(RecipesRoute, { ImageVector.vectorResource(R.drawable.ic_restaurant) }, R.string.nav_recipes),
    TopLevelDestination(ProfileRoute, { Icons.Filled.Person }, R.string.nav_profile),
).filter { it.route != RecipesRoute || BuildConfig.FEATURE_RECIPES }

private fun NavDestination?.isTopLevel() = this != null && topLevelDestinations.any { hasRoute(it.route::class) }

/** Reiterwechsel blenden nur über; Unterseiten kommen von rechts und gehen dorthin zurück. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

private val slideSpring = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

@Composable
fun AppNavHost(startDestination: Any, container: AppContainer) {
    val navController = rememberNavController()
    val shareInbox = container.shareInbox
    val pendingShare by shareInbox.pending.collectAsStateWithLifecycle()
    val pendingNav by container.navRequests.pending.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.appScope.launch { container.calorieNotifier.refresh() }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination.isTopLevel()
    var navUnlockedAt by remember { mutableStateOf(0L) }
    // Inhalte laufen unter der gläsernen Leiste durch; sie wird daraus weichgezeichnet.
    val hazeState = rememberHazeState()
    val addEntry = {
        // Immer für heute (Wunsch des Nutzers); andere Tage über die Mahlzeit-Zeilen des Tages.
        navController.navigate(CaptureRoute(epochDay = LocalDate.now().toEpochDay()))
    }

    Scaffold(
        // Insets übernehmen die Screens selbst (eigene Scaffolds mit TopAppBar).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .hazeBlur(HazeInput.Sources(hazeState), style = BottomBarGlass)
                        .drawWithContent {
                            drawContent()
                            drawLine(Neon.GlassEdgeTop, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                        },
                ) {
                    val middle = topLevelDestinations.size / 2
                    topLevelDestinations.forEachIndexed { index, dest ->
                        if (index == middle) AddButton(onClick = addEntry)
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute(dest.route::class) == true,
                            // Kurz nach der Einrichtung gesperrt: ein Doppeltipp auf „Fertig“ landete sonst im Profil.
                            onClick = { if (System.currentTimeMillis() > navUnlockedAt) navController.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon(), contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Neon.OnNeon,
                                selectedTextColor = Neon.Cyan,
                                indicatorColor = Neon.Cyan,
                                unselectedIconColor = Neon.TextSecondary,
                                unselectedTextColor = Neon.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Geteilter Inhalt → Import öffnen (erst nach dem Onboarding; bis dahin bleibt er liegen).
        LaunchedEffect(pendingShare, currentDestination) {
            val destination = currentDestination ?: return@LaunchedEffect
            if (pendingShare != null && !destination.hasRoute(OnboardingRoute::class) && !destination.hasRoute(RecipeImportRoute::class)) {
                navController.navigate(RecipeImportRoute)
            }
        }
        // Antippen einer Benachrichtigung → passendes Ziel öffnen.
        LaunchedEffect(pendingNav, currentDestination) {
            val destination = currentDestination ?: return@LaunchedEffect
            if (pendingNav == null || destination.hasRoute(OnboardingRoute::class)) return@LaunchedEffect
            when (val request = container.navRequests.take()) {
                NavRequest.Weigh -> navController.navigateTopLevel(WeightRoute)
                is NavRequest.Report -> navController.navigate(WeeklyReportRoute(request.id))
                is NavRequest.Capture -> navController.navigate(
                    CaptureRoute(epochDay = java.time.LocalDate.now().toEpochDay(), mealType = request.mealType),
                )
                null -> Unit
            }
        }
        // Einmalig nach dem Onboarding um Erlaubnis für Benachrichtigungen bitten (Erinnerungen sind standardmäßig an).
        LaunchedEffect(currentDestination) {
            val destination = currentDestination ?: return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= 33 && !destination.hasRoute(OnboardingRoute::class) &&
                !container.settings.notificationPermissionAsked
            ) {
                container.settings.notificationPermissionAsked = true
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        CompositionLocalProvider(
            LocalBottomBarPadding provides padding.calculateBottomPadding(),
            LocalTips provides container.settings,
        ) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    // Kein Abstand nach unten: Screens scrollen unter die Leiste und holen sich ihre Höhe über LocalBottomBarPadding.
                    modifier = Modifier.hazeSource(hazeState).consumeWindowInsets(padding),
                    enterTransition = {
                        if (isTabSwitch()) fadeIn(tween(180))
                        else slideInHorizontally(slideSpring) { it / 4 } + fadeIn(tween(180))
                    },
                    exitTransition = {
                        if (isTabSwitch()) fadeOut(tween(120))
                        else slideOutHorizontally(slideSpring) { -it / 10 } + fadeOut(tween(180))
                    },
                    popEnterTransition = {
                        if (isTabSwitch()) fadeIn(tween(180))
                        else slideInHorizontally(slideSpring) { -it / 10 } + fadeIn(tween(180))
                    },
                    popExitTransition = {
                        if (isTabSwitch()) fadeOut(tween(120))
                        else slideOutHorizontally(slideSpring) { it / 4 } + fadeOut(tween(180))
                    },
                ) {
                    screen<OnboardingRoute> {
                        OnboardingScreen(
                            onFinished = {
                                navUnlockedAt = System.currentTimeMillis() + 800
                                navController.navigate(TodayRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            },
                        )
                    }
                    screen<TodayRoute> {
                        TodayScreen(
                            onGoalEnded = { navController.navigate(GoalCelebrationRoute(it)) },
                            requestedDay = container.navRequests.showDay.collectAsStateWithLifecycle().value,
                            onDayShown = container.navRequests::dayShown,
                            onSetGoal = { navController.navigate(GoalRoute) },
                            onAddEntry = { date, meal ->
                                navController.navigate(CaptureRoute(epochDay = date.toEpochDay(), mealType = meal?.name))
                            },
                            onEditEntry = { entry ->
                                navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                            },
                            onNutrientClick = { date, nutrient ->
                                navController.navigate(DayNutrientsRoute(date.toEpochDay(), nutrient.name))
                            },
                            onOpenPlan = { navController.navigate(MealPlanRoute) },
                            onAdjustPlan = { date, meal, day ->
                                navController.navigate(
                                    CaptureRoute(epochDay = date.toEpochDay(), mealType = meal.name, planDay = day, fromPlan = true),
                                )
                            },
                        )
                    }
                    screen<StatsRoute> {
                        StatsScreen(
                            onOpenReport = { navController.navigate(WeeklyReportRoute(it)) },
                            onShowAllReports = { navController.navigate(AllReportsRoute) },
                            // Tag antippen → Reiter „Heute“ mit diesem Tag (keine eigene Unterseite mehr; Wunsch des Nutzers)
                            onDayClick = { date ->
                                container.navRequests.showDay(date)
                                navController.navigateTopLevel(TodayRoute)
                            },
                            onAddEntry = addEntry,
                        )
                    }
                    screen<WeightRoute> {
                        WeightScreen(
                            onOpenGoal = { navController.navigate(GoalCelebrationRoute(it)) },
                            openWeighDialog = container.navRequests.openWeighDialog.collectAsStateWithLifecycle().value,
                            onWeighDialogShown = container.navRequests::weighDialogShown,
                        )
                    }
                    screen<DayNutrientsRoute> {
                        DayNutrientsScreen(
                            onBack = { navController.popBackStack() },
                            onEditEntry = { entry ->
                                navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                            },
                        )
                    }
                    screen<WeeklyReportRoute> {
                        WeeklyReportScreen(
                            onBack = { navController.popBackStack() },
                            onEditPreferences = { navController.navigate(FoodPreferencesRoute) },
                        )
                    }
                    screen<AllReportsRoute> {
                        AllReportsScreen(
                            onBack = { navController.popBackStack() },
                            onOpen = { navController.navigate(WeeklyReportRoute(it)) },
                        )
                    }
                    screen<FoodPreferencesRoute> {
                        FoodPreferencesScreen(onBack = { navController.popBackStack() })
                    }
                    screen<DayRoute> {
                        TodayScreen(
                            onSetGoal = { navController.navigate(GoalRoute) },
                            onAddEntry = { date, meal ->
                                navController.navigate(CaptureRoute(epochDay = date.toEpochDay(), mealType = meal?.name))
                            },
                            onEditEntry = { entry ->
                                navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                            },
                            onNutrientClick = { date, nutrient ->
                                navController.navigate(DayNutrientsRoute(date.toEpochDay(), nutrient.name))
                            },
                            onOpenPlan = { navController.navigate(MealPlanRoute) },
                            onAdjustPlan = { date, meal, day ->
                                navController.navigate(
                                    CaptureRoute(epochDay = date.toEpochDay(), mealType = meal.name, planDay = day, fromPlan = true),
                                )
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    screen<ProfileRoute> {
                        ProfileScreen(
                            onNewGoal = { navController.navigate(GoalRoute) },
                            onOpenPlan = { navController.navigate(MealPlanRoute) },
                        )
                    }
                    screen<MealPlanRoute> {
                        MealPlanScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDay = { navController.navigate(MealPlanDayRoute(it)) },
                            onImport = { navController.navigate(MealPlanImportRoute) },
                        )
                    }
                    screen<MealPlanDayRoute> { entry ->
                        val day = entry.toRoute<MealPlanDayRoute>().dayIndex
                        val today = java.time.LocalDate.now().toEpochDay()
                        MealPlanDayScreen(
                            dayIndex = day,
                            onBack = { navController.popBackStack() },
                            onAdd = { meal -> navController.navigate(CaptureRoute(epochDay = today, mealType = meal.name, planDay = day)) },
                            onEdit = { item ->
                                navController.navigate(
                                    CaptureRoute(epochDay = today, mealType = item.mealType.name, planDay = day, planItemId = item.id),
                                )
                            },
                        )
                    }
                    screen<MealPlanImportRoute> {
                        MealPlanImportScreen(
                            onBack = { navController.popBackStack() },
                            onDone = { navController.popBackStack() },
                        )
                    }
                    screen<RecipesRoute> {
                        RecipesScreen(
                            onOpen = { navController.navigate(RecipeDetailRoute(it.id)) },
                            onCreate = { navController.navigate(RecipeEditRoute()) },
                            onImportLink = { text -> shareInbox.put(ImportSource.Text(text)) },
                            onOpenShopping = { navController.navigate(ShoppingListRoute) },
                        )
                    }
                    screen<ShoppingListRoute> {
                        ShoppingListScreen(onBack = { navController.popBackStack() })
                    }
                    screen<RecipeDetailRoute> {
                        RecipeDetailScreen(
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(RecipeEditRoute(id = it.id)) },
                        )
                    }
                    screen<RecipeEditRoute> { entry ->
                        val route = entry.toRoute<RecipeEditRoute>()
                        RecipeEditScreen(
                            onClose = { navController.popBackStack() },
                            onSaved = { id ->
                                if (route.id != 0L) {
                                    navController.popBackStack()
                                } else {
                                    // Neues Rezept → Detailansicht statt Editor im Back-Stack.
                                    navController.navigate(RecipeDetailRoute(id)) {
                                        popUpTo<RecipeEditRoute> { inclusive = true }
                                    }
                                }
                            },
                        )
                    }
                    screen<RecipeImportRoute> {
                        RecipeImportScreen(
                            onReview = {
                                navController.navigate(RecipeEditRoute(fromImport = true)) {
                                    popUpTo<RecipeImportRoute> { inclusive = true }
                                }
                            },
                            onManual = {
                                navController.navigate(RecipeEditRoute()) {
                                    popUpTo<RecipeImportRoute> { inclusive = true }
                                }
                            },
                            onClose = { navController.popBackStack() },
                        )
                    }
                    screen<GoalCelebrationRoute> { entry ->
                        val route = entry.toRoute<GoalCelebrationRoute>()
                        GoalCelebrationScreen(
                            onShare = { navController.navigate(GoalShareRoute(route.goalId)) },
                            onNewGoal = {
                                navController.navigate(GoalRoute) { popUpTo<GoalCelebrationRoute> { inclusive = true } }
                            },
                            onClose = { navController.popBackStack() },
                        )
                    }
                    screen<GoalShareRoute> {
                        GoalShareScreen(onBack = { navController.popBackStack() })
                    }
                    screen<GoalRoute> {
                        GoalScreen(onDone = { navController.popBackStack() })
                    }
                    screen<CaptureRoute> { entry ->
                        val route = entry.toRoute<CaptureRoute>()
                        CaptureScreen(
                            onClose = { navController.popBackStack() },
                            onManualEntry = { meal ->
                                // Erfassung durch den manuellen Editor ersetzen, damit „Zurück“ zur Übersicht führt.
                                navController.navigate(EntryEditRoute(epochDay = route.epochDay, mealType = meal.name)) {
                                    popUpTo<CaptureRoute> { inclusive = true }
                                }
                            },
                        )
                    }
                    screen<EntryEditRoute> {
                        EntryEditScreen(onDone = { navController.popBackStack() })
                    }
                }
                // Erster Tipp für neue Nutzer: zeigt auf das große „+“.
                TipSlot(
                    Tip.ADD,
                    caret = TipCaret.DOWN_CENTER,
                    condition = currentDestination?.hasRoute(TodayRoute::class) == true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 4.dp),
                )
            }
        }
    }
}

/** Gläserne Leiste: weichgezeichneter Inhalt, abgedunkelt für lesbare Symbole. Ohne Android 12 nur abgedunkelt. */
private val BottomBarGlass = HazeBlurStyle {
    blurRadius(28.dp)
    backgroundColor(Neon.Background)
    colorEffects(listOf(HazeColorEffect.tint(Neon.Background.copy(alpha = 0.55f))))
    fallbackColorEffect(HazeColorEffect.tint(Neon.Background.copy(alpha = 0.92f)))
    noiseFactor(0.04f)
}

/** Jeder Screen bekommt den Hintergrund mit Lichtflecken (gleitet bei Unterseiten mit). */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> GlowBackground { content(entry) } }

/** Großes „+“ in der Mitte der Leiste: Essen erfassen (Mahlzeit nach Uhrzeit vorgewählt). */
@Composable
private fun RowScope.AddButton(onClick: () -> Unit) {
    Box(Modifier.weight(1f).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
        PressableSurface(
            onClick = onClick,
            shape = CircleShape,
            color = Neon.Yellow,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.add_entry), tint = Neon.OnNeon, modifier = Modifier.size(30.dp))
        }
    }
}

/** Heute ist nach dem Onboarding immer die Wurzel des Back-Stacks. */
private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo<TodayRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
