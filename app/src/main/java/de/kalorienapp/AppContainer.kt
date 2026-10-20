package de.kalorienapp

import android.content.Context
import de.kalorienapp.data.db.AppDatabase
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.ProfileRepository

/** Manuelle Dependency Injection: alle app-weiten Abhängigkeiten an einer Stelle. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val foodEntryRepository = FoodEntryRepository(database.foodEntryDao())
}
