package de.kalorienapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.ProfileEntity

/** Schemaänderungen nur mit Versionserhöhung und Migration (Schemas liegen in app/schemas). */
@Database(
    entities = [ProfileEntity::class, FoodEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodEntryDao(): FoodEntryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db").build()
    }
}
