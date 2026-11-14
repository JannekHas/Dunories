package de.kalorienapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.kalorienapp.data.db.dao.ActivityEntryDao
import de.kalorienapp.data.db.dao.BodyMeasurementDao
import de.kalorienapp.data.db.dao.FavoriteFoodDao
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.MealComboDao
import de.kalorienapp.data.db.dao.ProfileDao
import de.kalorienapp.data.db.dao.RecipeDao
import de.kalorienapp.data.db.dao.ShoppingDao
import de.kalorienapp.data.db.dao.StatsDao
import de.kalorienapp.data.db.dao.WaterDao
import de.kalorienapp.data.db.dao.WeeklyReportDao
import de.kalorienapp.data.db.dao.WeightEntryDao
import de.kalorienapp.data.db.dao.WeightGoalDao
import de.kalorienapp.data.db.entity.ActivityEntryEntity
import de.kalorienapp.data.db.entity.BodyMeasurementEntity
import de.kalorienapp.data.db.entity.FavoriteFoodEntity
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.data.db.entity.MealComboEntity
import de.kalorienapp.data.db.entity.MealComboItemEntity
import de.kalorienapp.data.db.entity.ProfileEntity
import de.kalorienapp.data.db.entity.RecipeCategoryEntity
import de.kalorienapp.data.db.entity.RecipeCategoryRefEntity
import de.kalorienapp.data.db.entity.RecipeEntity
import de.kalorienapp.data.db.entity.RecipeIngredientEntity
import de.kalorienapp.data.db.entity.RecipeStepEntity
import de.kalorienapp.data.db.entity.ShoppingItemEntity
import de.kalorienapp.data.db.entity.WaterEntryEntity
import de.kalorienapp.data.db.entity.WeeklyReportEntity
import de.kalorienapp.data.db.entity.WeightEntryEntity
import de.kalorienapp.data.db.entity.WeightGoalEntity

/** Schemaänderungen nur mit Versionserhöhung und Migration (Schemas liegen in app/schemas). */
@Database(
    entities = [
        ProfileEntity::class,
        FoodEntryEntity::class,
        FavoriteFoodEntity::class,
        ActivityEntryEntity::class,
        WeightEntryEntity::class,
        WeightGoalEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        RecipeCategoryEntity::class,
        RecipeCategoryRefEntity::class,
        WeeklyReportEntity::class,
        ShoppingItemEntity::class,
        BodyMeasurementEntity::class,
        WaterEntryEntity::class,
        MealComboEntity::class,
        MealComboItemEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
    abstract fun activityEntryDao(): ActivityEntryDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun statsDao(): StatsDao
    abstract fun weightGoalDao(): WeightGoalDao
    abstract fun recipeDao(): RecipeDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun waterDao(): WaterDao
    abstract fun mealComboDao(): MealComboDao

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

        /** v6: Trainingskalorien aus Health Connect (Schalter im Profil + Tagesspeicher). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN importWorkouts INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_training (
                        dateEpochDay INTEGER NOT NULL,
                        activeKcal INTEGER NOT NULL,
                        workoutCount INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(dateEpochDay)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v7: Kein Health Connect und kein Aktivitätslevel mehr – Tagesziel = Grundumsatz ± Ziel/Tempo.
         * Bewegung trägt der Nutzer selbst ein (Tabelle activity_entry). daily_training entfällt.
         * Profil ohne activityLevel/importWorkouts → Tabelle neu anlegen (kein DROP COLUMN auf API 28).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_training")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_entry (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        kcal INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_entry_dateEpochDay ON activity_entry (dateEpochDay)")
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
                    INSERT INTO profile_new (id, sex, birthYear, heightCm, weightKg, goal, pace,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG)
                    SELECT id, sex, birthYear, heightCm, weightKg, goal, pace,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG
                    FROM profile
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE profile")
                db.execSQL("ALTER TABLE profile_new RENAME TO profile")
            }
        }

        /** v8: Gewichtsverlauf (höchstens ein Eintrag pro Tag). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weight_entry (
                        dateEpochDay INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(dateEpochDay)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v9: Mehrere Gewichte pro Tag – eigene ID und Messzeitpunkt statt „ein Eintrag pro Tag“. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weight_entry_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        measuredAt INTEGER NOT NULL,
                        weightKg REAL NOT NULL
                    )
                    """.trimIndent(),
                )
                // Bisheriger Erfassungszeitpunkt wird zum Messzeitpunkt.
                db.execSQL("INSERT INTO weight_entry_new (measuredAt, weightKg) SELECT createdAt, weightKg FROM weight_entry")
                db.execSQL("DROP TABLE weight_entry")
                db.execSQL("ALTER TABLE weight_entry_new RENAME TO weight_entry")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_weight_entry_measuredAt ON weight_entry (measuredAt)")
            }
        }

        /**
         * v10: Gewichtsziel (Wunschgewicht + Zieldatum) statt Ziel/Tempo im Profil.
         * Wer bisher „Halten“ hatte, bekommt ein aktives Halten-Ziel; Abnehmen/Zunehmen wird neu gefragt.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weight_goal (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startDate INTEGER NOT NULL,
                        startWeightKg REAL NOT NULL,
                        targetWeightKg REAL NOT NULL,
                        targetDate INTEGER,
                        endedDate INTEGER,
                        endWeightKg REAL,
                        endReason TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO weight_goal (startDate, startWeightKg, targetWeightKg)
                    SELECT CAST(julianday('now', 'localtime') - 2440587.5 AS INTEGER), weightKg, weightKg
                    FROM profile WHERE goal = 'MAINTAIN'
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profile_new (
                        id INTEGER NOT NULL,
                        sex TEXT NOT NULL,
                        birthYear INTEGER NOT NULL,
                        heightCm REAL NOT NULL,
                        weightKg REAL NOT NULL,
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
                    INSERT INTO profile_new (id, sex, birthYear, heightCm, weightKg,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG)
                    SELECT id, sex, birthYear, heightCm, weightKg,
                        proteinPerKg, kcalTargetOverride, proteinOverrideG, fatOverrideG, carbsOverrideG
                    FROM profile
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE profile")
                db.execSQL("ALTER TABLE profile_new RENAME TO profile")
            }
        }

        /** Startliste der Rezept-Kategorien (umbenennbar/löschbar). */
        val DEFAULT_CATEGORIES = listOf("Frühstück", "Hauptgericht", "Salat", "Snack", "Dessert", "Backen", "Getränk", "Meal Prep")

        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            DEFAULT_CATEGORIES.forEachIndexed { index, name ->
                db.execSQL("INSERT INTO recipe_category (name, position) VALUES (?, ?)", arrayOf<Any>(name, index))
            }
        }

        /** v11: Rezeptbuch – Rezepte, Zutaten (Nährwerte pro 100 g/ml), Schritte, Kategorien. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                RECIPE_TABLES_SQL.forEach(db::execSQL)
                insertDefaultCategories(db)
            }
        }

        /** v12: KI-Wochenauswertungen (Inhalt als JSON). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weekly_report` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromEpochDay` INTEGER NOT NULL, `toEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `content` TEXT NOT NULL)",
                )
            }
        }

        /** v13: Einkaufsliste. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shopping_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `quantity` REAL, `unit` TEXT NOT NULL, `checked` INTEGER NOT NULL, " +
                        "`sources` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /** v14: Körpermaße. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `value` REAL NOT NULL, `measuredAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurement_measuredAt` ON `body_measurement` (`measuredAt`)")
            }
        }

        /** v15: Wasser-Tracker. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `ml` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_entry_dateEpochDay` ON `water_entry` (`dateEpochDay`)")
            }
        }

        /** v16: eigene Mahlzeiten-Kombis. SQL aus dem exportierten Schema (16.json). */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                COMBO_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** v17: Vitamine & Mineralstoffe – eine Textspalte je Nährwert-Tabelle (siehe Micro.encode). */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("food_entry", "favorite_food", "recipe_ingredient", "meal_combo_item").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `micros` TEXT")
                }
            }
        }

        /** v18: Alltag ohne Sport (DGE-PAL) im Profil; bestehende Profile starten mit „überwiegend sitzend“. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `lifestyle` TEXT NOT NULL DEFAULT 'SEDENTARY'")
            }
        }

        /** v19: eigene Reihenfolge der Einträge innerhalb einer Mahlzeit (Verschieben auf „Heute“). */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `sortKey` INTEGER")
            }
        }

        /** Gerichte mit Zutaten und Zusammenfassen gleicher Einträge. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `components` TEXT")
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `lastAddedAt` INTEGER")
            }
        }

        private val COMBO_TABLES_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `meal_combo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `meal_combo_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `comboId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `unit` TEXT NOT NULL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `saturatedFatG` REAL, `sugarG` REAL, `fiberG` REAL, `saltG` REAL, FOREIGN KEY(`comboId`) REFERENCES `meal_combo`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_meal_combo_item_comboId` ON `meal_combo_item` (`comboId`)",
        )

        /** Aus dem exportierten Schema (app/schemas/…/11.json) übernommen. */
        private val RECIPE_TABLES_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `servings` INTEGER NOT NULL, `totalMinutes` INTEGER, `effort` TEXT, `imagePath` TEXT, `sourceUrl` TEXT, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `recipe_category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `recipe_ingredient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `quantity` REAL, `quantityUnit` TEXT NOT NULL, `amount` REAL, `unit` TEXT NOT NULL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `saturatedFatG` REAL, `sugarG` REAL, `fiberG` REAL, `saltG` REAL, `note` TEXT, FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_recipeId` ON `recipe_ingredient` (`recipeId`)",
            "CREATE TABLE IF NOT EXISTS `recipe_step` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_recipe_step_recipeId` ON `recipe_step` (`recipeId`)",
            "CREATE TABLE IF NOT EXISTS `recipe_category_ref` (`recipeId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`recipeId`, `categoryId`), FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `recipe_category`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_recipe_category_ref_categoryId` ON `recipe_category_ref` (`categoryId`)",
        )

        const val NAME = "kalorien.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = insertDefaultCategories(db)
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .build()
    }
}
