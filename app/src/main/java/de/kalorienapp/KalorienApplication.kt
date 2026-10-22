package de.kalorienapp

import android.app.Application
import de.kalorienapp.notify.Notifications
import kotlinx.coroutines.launch

class KalorienApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Kalorien-Benachrichtigung folgt jeder Änderung; Erinnerungen/Auswertung neu planen.
        container.calorieNotifier.observe(container.appScope)
        container.appScope.launch { container.workScheduler.scheduleAll() }
        container.appScope.launch { container.recipeRepository.cleanupImages() }
    }
}
