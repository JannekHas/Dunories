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

    sealed interface RestoreResult {
        data object Success : RestoreResult
        data class Failed(val problem: BackupFormat.Problem?) : RestoreResult
    }

    /** Erstellt die Sicherung im Cache (zum Teilen/Speichern). */
    suspend fun create(): File = withContext(Dispatchers.IO) {
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
        file
    }

    /** Kopiert die erstellte Sicherung an einen vom Nutzer gewählten Ort („Speichern unter“). */
    suspend fun saveTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Ziel nicht beschreibbar")
    }

    /**
     * Prüft und entpackt die Sicherung erst in einen Zwischenordner; nur wenn alles passt,
     * werden die aktuellen Daten ersetzt.
     */
    suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val stagedImages = File(staging, "images").apply { mkdirs() }
        val stagedDb = File(staging, BackupFormat.DATABASE)
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
        if (!ok || !stagedDb.exists()) return@withContext RestoreResult.Failed(BackupFormat.Problem.NOT_A_BACKUP)
        val check = BackupFormat.check(manifestJson, database.openHelper.readableDatabase.version)
        if (check is BackupFormat.Result.Invalid) return@withContext RestoreResult.Failed(check.problem)
        val manifest = (check as BackupFormat.Result.Valid).manifest
        if (!prepareDatabase(stagedDb)) return@withContext RestoreResult.Failed(BackupFormat.Problem.NOT_A_BACKUP)

        // Ab hier wird ersetzt.
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
        staging.deleteRecursively()
        RestoreResult.Success
    }

    /** Datenbank lesbar? Bildpfade auf dieses Gerät umbiegen. */
    private fun prepareDatabase(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" } || return false
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
            true
        }
    }.getOrDefault(false)

    /** Startet die App mit den eingespielten Daten neu (Datenbank wurde geschlossen). */
    fun restartApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Runtime.getRuntime().exit(0)
    }
}
