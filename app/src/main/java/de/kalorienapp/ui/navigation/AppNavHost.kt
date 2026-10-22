package de.kalorienapp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.kalorienapp.R
import de.kalorienapp.ui.capture.CaptureScreen
import de.kalorienapp.ui.entry.EntryEditScreen
import de.kalorienapp.ui.onboarding.OnboardingScreen
import de.kalorienapp.ui.profile.ProfileScreen
import de.kalorienapp.ui.theme.Neon
import de.kalorienapp.ui.today.TodayScreen

private data class TopLevelDestination(val route: Any, val icon: ImageVector, val labelRes: Int)

private val topLevelDestinations = listOf(
    TopLevelDestination(TodayRoute, Icons.Filled.Home, R.string.nav_today),
    TopLevelDestination(ProfileRoute, Icons.Filled.Person, R.string.nav_profile),
)

@Composable
fun AppNavHost(startDestination: Any) {
    val navController = rememberNavController()
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
                            icon = { Icon(dest.icon, contentDescription = null) },
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
                    onAddEntry = { date, meal ->
                        navController.navigate(CaptureRoute(epochDay = date.toEpochDay(), mealType = meal?.name))
                    },
                    onEditEntry = { entry ->
                        navController.navigate(EntryEditRoute(entryId = entry.id, epochDay = entry.date.toEpochDay()))
                    },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen()
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
