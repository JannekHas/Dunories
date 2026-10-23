package de.kalorienapp.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Aufbau einer Sicherung (ZIP):
 * - `backup.json` – Kennung, Formatversion, Datenbankversion, Zeitpunkt, Einstellungen
 * - `kalorien.db` – komplette Room-Datenbank
 * - `images/…` – Rezeptbilder
 * Reine Logik ohne Android-Abhängigkeiten (unit-getestet).
 */
object BackupFormat {
    const val APP_ID = "de.kalorienapp"
    const val FORMAT_VERSION = 1
    const val MANIFEST = "backup.json"
    const val DATABASE = "kalorien.db"
    const val IMAGES_DIR = "images/"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class Manifest(
        val app: String = APP_ID,
        val format: Int = FORMAT_VERSION,
        val databaseVersion: Int,
        val createdAt: Long,
        val settings: Settings = Settings(),
    )

    @Serializable
    data class Settings(
        val caloriesNotification: Boolean = false,
        val weightReminderDays: Int? = 3,
        val weeklyReport: Boolean = true,
        val waterGoalMl: Int? = null,
        val fastingEnabled: Boolean = false,
        /** Plan;Beginn in Minuten;eigene Stunden;Fastentage – siehe BackupManager. */
        val fasting: String? = null,
    )

    enum class Problem { NOT_A_BACKUP, NEWER_APP }

    /** Was in einer Sicherung steckt – zum Prüfen, ob es die richtige Datei ist. */
    data class Summary(
        /** Zeitraum des Tagebuchs (erster/letzter Tag mit Einträgen). */
        val firstDay: java.time.LocalDate?,
        val lastDay: java.time.LocalDate?,
        val foodEntries: Long,
        val recipes: Long,
        val weights: Long,
        val measurements: Long,
        val shoppingItems: Long,
        val reports: Long,
        /** Zeitpunkt der Sicherung (Epoch-ms), falls bekannt. */
        val createdAt: Long? = null,
    )

    /**
     * Übersicht aus einer Datenbank lesen. [query] liefert die erste Zahl eines SELECT oder null
     * (z. B. wenn es die Tabelle in einer älteren Sicherung noch nicht gibt).
     */
    fun summary(createdAt: Long?, query: (String) -> Long?): Summary {
        fun count(table: String) = query("SELECT COUNT(*) FROM $table") ?: 0L
        return Summary(
            firstDay = query("SELECT MIN(dateEpochDay) FROM food_entry")?.let(java.time.LocalDate::ofEpochDay),
            lastDay = query("SELECT MAX(dateEpochDay) FROM food_entry")?.let(java.time.LocalDate::ofEpochDay),
            foodEntries = count("food_entry"),
            recipes = count("recipe"),
            weights = count("weight_entry"),
            measurements = count("body_measurement"),
            shoppingItems = count("shopping_item"),
            reports = count("weekly_report"),
            createdAt = createdAt,
        )
    }

    fun encode(manifest: Manifest): String = json.encodeToString(Manifest.serializer(), manifest)

    /** @return Manifest oder das Problem, warum die Datei nicht eingespielt werden kann. */
    fun check(manifestJson: String?, currentDatabaseVersion: Int): Result {
        val manifest = manifestJson?.let { runCatching { json.decodeFromString(Manifest.serializer(), it) }.getOrNull() }
            ?: return Result.Invalid(Problem.NOT_A_BACKUP)
        return when {
            manifest.app != APP_ID -> Result.Invalid(Problem.NOT_A_BACKUP)
            // Neuere Datenbank/Format kann diese App-Version nicht lesen → erst App aktualisieren.
            manifest.format > FORMAT_VERSION || manifest.databaseVersion > currentDatabaseVersion -> Result.Invalid(Problem.NEWER_APP)
            manifest.databaseVersion < 1 -> Result.Invalid(Problem.NOT_A_BACKUP)
            else -> Result.Valid(manifest)
        }
    }

    sealed interface Result {
        data class Valid(val manifest: Manifest) : Result
        data class Invalid(val problem: Problem) : Result
    }

    /** Nur einfache Dateinamen aus `images/` übernehmen (Schutz vor „../“ in manipulierten ZIPs). */
    fun imageName(entryName: String): String? {
        if (!entryName.startsWith(IMAGES_DIR)) return null
        val name = entryName.removePrefix(IMAGES_DIR)
        return name.takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != "." && it != ".." }
    }

    /** Gespeicherter Bildpfad vom alten Gerät → gleicher Dateiname im Bildordner dieses Geräts. */
    fun relocate(oldPath: String, imagesDir: String): String =
        imagesDir.trimEnd('/', '\\') + "/" + oldPath.substringAfterLast('/').substringAfterLast('\\')

    fun fileName(date: java.time.LocalDate) = "KalorienApp-Sicherung-$date.zip"
}
