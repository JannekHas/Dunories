package de.kalorienapp.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.kalorienapp.KalorienApplication
import de.kalorienapp.ui.calendar.CalendarViewModel
import de.kalorienapp.ui.capture.CaptureViewModel
import de.kalorienapp.ui.entry.EntryEditViewModel
import de.kalorienapp.ui.goal.GoalViewModel
import de.kalorienapp.ui.profile.BackupViewModel
import de.kalorienapp.ui.profile.NotificationSettingsViewModel
import de.kalorienapp.ui.profile.ProfileViewModel
import de.kalorienapp.ui.recipes.RecipeDetailViewModel
import de.kalorienapp.ui.recipes.RecipeEditViewModel
import de.kalorienapp.ui.recipes.RecipeImportViewModel
import de.kalorienapp.ui.recipes.RecipesViewModel
import de.kalorienapp.ui.recipes.ShoppingListViewModel
import de.kalorienapp.ui.stats.BodyMeasurementViewModel
import de.kalorienapp.ui.stats.StatsViewModel
import de.kalorienapp.ui.stats.WeeklyReportViewModel
import de.kalorienapp.ui.today.TodayViewModel

/** Factory für alle ViewModels – holt Abhängigkeiten aus dem [de.kalorienapp.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            TodayViewModel(
                createSavedStateHandle(),
                container().goalRepository,
                container().foodEntryRepository,
                container().foodLibraryRepository,
                container().activityEntryRepository,
            )
        }
        initializer {
            ProfileViewModel(container().profileRepository, container().weightRepository, container().goalRepository)
        }
        initializer {
            CalendarViewModel(container().statsRepository)
        }
        initializer {
            StatsViewModel(
                container().statsRepository,
                container().weightRepository,
                container().goalRepository,
                container().weeklyReportRepository,
            )
        }
        initializer {
            BodyMeasurementViewModel(container().bodyMeasurementRepository)
        }
        initializer {
            WeeklyReportViewModel(createSavedStateHandle(), container().weeklyReportRepository)
        }
        initializer {
            BackupViewModel(container().backupManager)
        }
        initializer {
            NotificationSettingsViewModel(container().settings, container().calorieNotifier, container().workScheduler)
        }
        initializer {
            RecipesViewModel(container().recipeRepository, container().shoppingRepository)
        }
        initializer {
            RecipeDetailViewModel(
                createSavedStateHandle(),
                container().recipeRepository,
                container().foodEntryRepository,
                container().shoppingRepository,
            )
        }
        initializer {
            ShoppingListViewModel(container().shoppingRepository)
        }
        initializer {
            RecipeEditViewModel(
                createSavedStateHandle(),
                container().recipeRepository,
                container().recipeImages,
                container().recipeDrafts,
            )
        }
        initializer {
            RecipeImportViewModel(
                container().shareInbox,
                container().recipeImporter,
                container().recipeRepository,
                container().recipeDrafts,
            )
        }
        initializer {
            GoalViewModel(container().profileRepository, container().goalRepository)
        }
        initializer {
            EntryEditViewModel(createSavedStateHandle(), container().foodEntryRepository)
        }
        initializer {
            CaptureViewModel(
                createSavedStateHandle(),
                container().foodAnalyzer,
                container().foodEntryRepository,
                container().foodLibraryRepository,
            )
        }
    }

    private fun CreationExtras.container() = (this[APPLICATION_KEY] as KalorienApplication).container
}
