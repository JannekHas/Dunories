package de.kalorienapp.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import de.kalorienapp.MainActivity
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sicherung erstellen und einspielen. Die Datei bleibt beim Nutzer (Teilen/Speichern) –
 * keine Cloud. Beim Einspielen werden alle aktuellen Daten ersetzt und die App neu gestartet.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: AppSettings,
) {
    private val imagesDir get() = File(context.filesDir, "recipe_images")

    /** Erstellte Sicherung samt Übersicht des Inhalts. */
    data class Created(val file: File, val summary: BackupFormat.Summary)

    /** Geprüfte, entpackte Sicherung – wird erst mit [applyRestore] übernommen. */
    data class Prepared(val summary: BackupFormat.Summary, internal val manifest: BackupFormat.Manifest)

    sealed interface PrepareResult {
        data class Ready(val prepared: Prepared) : PrepareResult
        data class Failed(val problem: BackupFormat.Problem) : PrepareResult
    }

    private val staging get() = File(context.cacheDir, "restore")
    private val stagedDb get() = File(staging, BackupFormat.DATABASE)
    private val stagedImages get() = File(staging, "images")

    /** Erstellt die Sicherung im Cache (zum Teilen/Speichern). */
    suspend fun create(): Created = withContext(Dispatchers.IO) {
        // Alles aus dem WAL in die Hauptdatei schreiben, damit die Kopie vollständig ist.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, BackupFormat.fileName(LocalDate.now()))
        val manifest = BackupFormat.Manifest(
            databaseVersion = database.openHelper.readableDatabase.version,
            createdAt = System.currentTimeMillis(),
            settings = BackupFormat.Settings(
                caloriesNotification = settings.caloriesNotification.value,
                weightReminderDays = settings.weightReminderDays.value,
                weeklyReport = settings.weeklyReport.value,
                waterGoalMl = settings.waterGoalMl.value,
                fastingEnabled = settings.fastingEnabled.value,
                fasting = settings.fastingConfig.value.let { c ->
                    listOf(c.plan.name, c.windowStart.toSecondOfDay() / 60, c.customFastHours, c.fastDays.joinToString(",") { it.name })
                        .joinToString(";")
                },
            ),
        )
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
            zip.write(BackupFormat.encode(manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupFormat.DATABASE))
            context.getDatabasePath(AppDatabase.NAME).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            imagesDir.listFiles().orEmpty().filter { it.isFile }.forEach { image ->
                zip.putNextEntry(ZipEntry(BackupFormat.IMAGES_DIR + image.name))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val db = database.openHelper.readableDatabase
        val summary = BackupFormat.summary(manifest.createdAt) { sql ->
            runCatching { db.query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null } }.getOrNull()
        }
        Created(file, summary)
    }

    /** Kopiert die erstellte Sicherung an einen vom Nutzer gewählten Ort („Speichern unter“). */
    suspend fun saveTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Ziel nicht beschreibbar")
    }

    /**
     * Schritt 1: Sicherung prüfen und in einen Zwischenordner entpacken, Übersicht lesen.
     * Die aktuellen Daten bleiben unangetastet, bis der Nutzer bestätigt ([applyRestore]).
     */
    suspend fun prepareRestore(uri: Uri): PrepareResult = withContext(Dispatchers.IO) {
        staging.deleteRecursively()
        stagedImages.mkdirs()
        var manifestJson: String? = null
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.isDirectory -> Unit
                            entry.name == BackupFormat.MANIFEST -> manifestJson = zip.readBytes().decodeToString()
                            entry.name == BackupFormat.DATABASE -> stagedDb.outputStream().use { zip.copyTo(it) }
                            else -> BackupFormat.imageName(entry.name)?.let { name ->
                                File(stagedImages, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            } != null
        }.getOrDefault(false)
        if (!ok || !stagedDb.exists()) return@withContext PrepareResult.Failed(BackupFormat.Problem.NOT_A_BACKUP)
        val check = BackupFormat.check(manifestJson, database.openHelper.readableDatabase.version)
        if (check is BackupFormat.Result.Invalid) return@withContext PrepareResult.Failed(check.problem)
        val manifest = (check as BackupFormat.Result.Valid).manifest
        val summary = prepareDatabase(stagedDb, manifest.createdAt)
            ?: return@withContext PrepareResult.Failed(BackupFormat.Problem.NOT_A_BACKUP)
        PrepareResult.Ready(Prepared(summary, manifest))
    }

    /** Schritt 2: aktuelle Daten durch die vorbereitete Sicherung ersetzen (danach [restartApp]). */
    suspend fun applyRestore(prepared: Prepared) = withContext(Dispatchers.IO) {
        val manifest = prepared.manifest
        check(stagedDb.exists()) { "Keine vorbereitete Sicherung" }
        database.close()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
        stagedDb.copyTo(dbFile, overwrite = true)
        imagesDir.deleteRecursively()
        imagesDir.mkdirs()
        stagedImages.listFiles().orEmpty().forEach { it.copyTo(File(imagesDir, it.name), overwrite = true) }
        settings.setCaloriesNotification(manifest.settings.caloriesNotification)
        settings.setWeightReminderDays(manifest.settings.weightReminderDays)
        settings.setWeeklyReport(manifest.settings.weeklyReport)
        settings.setWaterGoalMl(manifest.settings.waterGoalMl)
        settings.setFastingEnabled(manifest.settings.fastingEnabled)
        manifest.settings.fasting?.split(';')?.takeIf { it.size == 4 }?.let { (plan, start, hours, days) ->
            val defaults = de.kalorienapp.domain.FastingConfig()
            settings.setFastingConfig(
                de.kalorienapp.domain.FastingConfig(
                    plan = de.kalorienapp.domain.FastingPlan.entries.find { it.name == plan } ?: defaults.plan,
                    windowStart = start.toIntOrNull()?.takeIf { it in 0 until 1440 }?.let { java.time.LocalTime.of(it / 60, it % 60) }
                        ?: defaults.windowStart,
                    customFastHours = hours.toIntOrNull() ?: defaults.customFastHours,
                    fastDays = days.split(',').mapNotNull { d -> java.time.DayOfWeek.entries.find { it.name == d } }.toSet(),
                ),
            )
        }
        staging.deleteRecursively()
    }

    /** Abgebrochen → Zwischenordner aufräumen. */
    fun discardRestore() {
        staging.deleteRecursively()
    }

    /** Datenbank lesbar? Bildpfade auf dieses Gerät umbiegen und Übersicht lesen; null = unbrauchbar. */
    private fun prepareDatabase(file: File, createdAt: Long): BackupFormat.Summary? = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" } || return null
            val hasRecipes = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='recipe'", null).use { it.count > 0 }
            if (hasRecipes) {
                val rows = db.rawQuery("SELECT id, imagePath FROM recipe WHERE imagePath IS NOT NULL", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
                }
                rows.forEach { (id, path) ->
                    db.execSQL("UPDATE recipe SET imagePath = ? WHERE id = ?", arrayOf<Any>(BackupFormat.relocate(path, imagesDir.path), id))
                }
            }
            // Room erwartet keinen WAL-Rest aus der fremden Datei.
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            BackupFormat.summary(createdAt) { sql ->
                runCatching { db.rawQuery(sql, null).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null } }.getOrNull()
            }
        }
    }.getOrNull()

    /** Startet die App mit den eingespielten Daten neu (Datenbank wurde geschlossen). */
    fun restartApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Runtime.getRuntime().exit(0)
    }
}
