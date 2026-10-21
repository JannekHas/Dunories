package de.kalorienapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.kalorienapp.data.db.dao.FavoriteFoodDao
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.entity.FavoriteFoodEntity
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.ProfileEntity

/** Schemaänderungen nur mit Versionserhöhung und Migration (Schemas liegen in app/schemas). */
@Database(
    entities = [ProfileEntity::class, FoodEntryEntity::class, FavoriteFoodEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao

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

        /** v3: Favoriten und Tageswerte der aktiven Kalorien (Health Connect). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_food (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        amount REAL NOT NULL,
                        unit TEXT NOT NULL,
                        kcal REAL NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        saturatedFatG REAL,
                        sugarG REAL,
                        fiberG REAL,
                        saltG REAL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_activity (
                        dateEpochDay INTEGER PRIMARY KEY NOT NULL,
                        activeKcal INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v4: Zusatzspalten in daily_activity (Tabelle entfällt mit v5 wieder). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_activity ADD COLUMN steps INTEGER")
                db.execSQL("ALTER TABLE daily_activity ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE daily_activity ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5: Health Connect entfernt (Samsung Health liefert dort keine Kalorien).
         * Tabelle daily_activity weg; Profil ohne useHealthConnect/activeCalorieCreditPercent.
         * SQLite auf API 28 kennt kein DROP COLUMN → Profil-Tabelle neu anlegen und umkopieren.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_activity")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profile_new (
                        id INTEGER NOT NULL,
                        sex TEXT NOT NULL,
                        birthYear INTEGER NOT NULL,
                        heightCm REAL NOT NULL,
                        weightKg REAL NOT NULL,
                        goal TEXT NOT NULL,
                        pace TEXT NOT NULL,
                        activityLevel TEXT NOT NULL,
                        proteinPerKg REAL,
                        kcalTargetOverride INTEGER,
                        proteinOverrideG INTEGER,
                        fatOverrideG INTEGER,
                        carbsOverrideG INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO profile_new (id, sex, birthYear, heightCm, weightKg, goal, pace, activityLevel,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG)
                    SELECT id, sex, birthYear, heightCm, weightKg, goal, pace, activityLevel,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG
                    FROM profile
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE profile")
                db.execSQL("ALTER TABLE profile_new RENAME TO profile")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
