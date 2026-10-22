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
import de.kalorienapp.ui.profile.ProfileViewModel
import de.kalorienapp.ui.stats.StatsViewModel
import de.kalorienapp.ui.today.TodayViewModel

/** Factory für alle ViewModels – holt Abhängigkeiten aus dem [de.kalorienapp.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            TodayViewModel(
                createSavedStateHandle(),
                container().profileRepository,
                container().foodEntryRepository,
                container().foodLibraryRepository,
                container().activityEntryRepository,
            )
        }
        initializer {
            ProfileViewModel(container().profileRepository, container().weightRepository)
        }
        initializer {
            CalendarViewModel(container().statsRepository)
        }
        initializer {
            StatsViewModel(container().statsRepository, container().weightRepository, container().profileRepository)
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
