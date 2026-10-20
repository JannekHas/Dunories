package de.kalorienapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.ProfileEntity

/** Schemaänderungen nur mit Versionserhöhung und Migration (Schemas liegen in app/schemas). */
@Database(
    entities = [ProfileEntity::class, FoodEntryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodEntryDao(): FoodEntryDao

    companion object {
        /** v2: Mengeneinheit (g/ml) und optionale Zusatznährwerte. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entry ADD COLUMN unit TEXT NOT NULL DEFAULT 'GRAM'")
                db.execSQL("ALTER TABLE food_entry ADD COLUMN saturatedFatG REAL")
                db.execSQL("ALTER TABLE food_entry ADD COLUMN sugarG REAL")
                db.execSQL("ALTER TABLE food_entry ADD COLUMN fiberG REAL")
                db.execSQL("ALTER TABLE food_entry ADD COLUMN saltG REAL")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
