package de.kalorienapp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import de.kalorienapp.ui.calendar.CalendarScreen
import de.kalorienapp.ui.capture.CaptureScreen
import de.kalorienapp.ui.entry.EntryEditScreen
import de.kalorienapp.ui.goal.GoalScreen
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

private data class TopLevelDestination(val route: Any, val icon: @Composable () -> ImageVector, val labelRes: Int)

private val topLevelDestinations = listOf(
    TopLevelDestination(TodayRoute, { Icons.Filled.Home }, R.string.nav_today),
    TopLevelDestination(CalendarRoute, { Icons.Filled.DateRange }, R.string.nav_calendar),
    TopLevelDestination(RecipesRoute, { ImageVector.vectorResource(R.drawable.ic_restaurant) }, R.string.nav_recipes),
    TopLevelDestination(StatsRoute, { ImageVector.vectorResource(R.drawable.ic_bar_chart) }, R.string.nav_stats),
    TopLevelDestination(ProfileRoute, { Icons.Filled.Person }, R.string.nav_profile),
).filter { it.route != RecipesRoute || BuildConfig.FEATURE_RECIPES }

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
    val showBottomBar = topLevelDestinations.any { dest ->
        currentDestination?.hasRoute(dest.route::class) == true
    }

    Scaffold(
        // Insets übernehmen die Screens selbst (eigene Scaffolds mit TopAppBar).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Neon.Surface) {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination?.hasRoute(dest.route::class) == true,
                            onClick = { navController.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon(), contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
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
                NavRequest.Weigh -> navController.navigateTopLevel(StatsRoute)
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
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(TodayRoute) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<TodayRoute> {
                TodayScreen(
                    onSetGoal = { navController.navigate(GoalRoute) },
                    onAddEntry = { date, meal ->
                        navController.navigate(CaptureRoute(epochDay = date.toEpochDay(), mealType = meal?.name))
                    },
                    onEditEntry = { entry ->
                        navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                    },
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(onDayClick = { date -> navController.navigate(DayRoute(date.toEpochDay())) })
            }
            composable<StatsRoute> {
                StatsScreen(
                    openWeighDialog = container.navRequests.openWeighDialog.collectAsStateWithLifecycle().value,
                    onWeighDialogShown = container.navRequests::weighDialogShown,
                    onOpenReport = { navController.navigate(WeeklyReportRoute(it)) },
                )
            }
            composable<WeeklyReportRoute> {
                WeeklyReportScreen(onBack = { navController.popBackStack() })
            }
            composable<DayRoute> {
                TodayScreen(
                    onSetGoal = { navController.navigate(GoalRoute) },
                    onAddEntry = { date, meal ->
                        navController.navigate(CaptureRoute(epochDay = date.toEpochDay(), mealType = meal?.name))
                    },
                    onEditEntry = { entry ->
                        navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(onNewGoal = { navController.navigate(GoalRoute) })
            }
            composable<RecipesRoute> {
                RecipesScreen(
                    onOpen = { navController.navigate(RecipeDetailRoute(it.id)) },
                    onCreate = { navController.navigate(RecipeEditRoute()) },
                    onImportLink = { text -> shareInbox.put(ImportSource.Text(text)) },
                    onOpenShopping = { navController.navigate(ShoppingListRoute) },
                )
            }
            composable<ShoppingListRoute> {
                ShoppingListScreen(onBack = { navController.popBackStack() })
            }
            composable<RecipeDetailRoute> {
                RecipeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(RecipeEditRoute(id = it.id)) },
                )
            }
            composable<RecipeEditRoute> { entry ->
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
            composable<RecipeImportRoute> {
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
            composable<GoalRoute> {
                GoalScreen(onDone = { navController.popBackStack() })
            }
            composable<CaptureRoute> { entry ->
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
            composable<EntryEditRoute> {
                EntryEditScreen(onDone = { navController.popBackStack() })
            }
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
