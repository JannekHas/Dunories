package de.kalorienapp.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.kalorienapp.KalorienApplication
import de.kalorienapp.ui.capture.CaptureViewModel
import de.kalorienapp.ui.entry.EntryEditViewModel
import de.kalorienapp.ui.profile.ProfileViewModel
import de.kalorienapp.ui.today.TodayViewModel

/** Factory für alle ViewModels – holt Abhängigkeiten aus dem [de.kalorienapp.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            TodayViewModel(
                container().profileRepository,
                container().foodEntryRepository,
                container().foodLibraryRepository,
            )
        }
        initializer {
            ProfileViewModel(container().profileRepository)
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
