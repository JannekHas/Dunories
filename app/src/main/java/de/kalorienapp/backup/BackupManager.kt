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
    data class Created(
        val file: File,
        val summary: BackupFormat.Summary,
        /** Fingerabdruck von Datenbank + Einstellungen (ohne Zeitpunkt) – Cloud-Sicherung lädt nur bei Änderung hoch. */
        val contentHash: String = "",
    )

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
                mealReminders = settings.mealReminders.value,
                dietStyle = settings.foodPreferences.value.style.name,
                excludedFoods = settings.foodPreferences.value.excluded.map { it.name },
                intolerances = settings.foodPreferences.value.intolerances.map { it.name },
                cookingTime = settings.foodPreferences.value.cooking.name,
                budget = settings.foodPreferences.value.budget.name,
                mealPrep = settings.foodPreferences.value.mealPrep,
                dislikedFoods = settings.foodPreferences.value.dislikes,
                mealPlanEnabled = settings.mealPlan.value.enabled,
                mealPlanStart = settings.mealPlan.value.startEpochDay,
                mealPlanDays = settings.mealPlan.value.days,
                mealPlanEnd = settings.mealPlan.value.endEpochDay,
                mealPlanStopAtEnd = settings.mealPlan.value.stopAtEnd,
                mealPlanOpenEnded = settings.mealPlan.value.openEnded,
                waterEnabled = settings.waterEnabled.value,
                proteinOnly = settings.proteinOnly.value,
                weightEnabled = settings.weightEnabled.value,
                bodyAnalysisEnabled = settings.bodyAnalysisEnabled.value,
                measuresEnabled = settings.measuresEnabled.value,
                goalSkipped = settings.goalSkipped.value,
                seenTips = settings.seenTips.value.toList(),
                pendingTips = settings.pendingTips.value.toList(),
                manualTyped = settings.manualTyped,
                weeklyDoneFor = settings.weeklyDoneFor?.toEpochDay(),
                changelogSeen = settings.changelogSeen.value,
                feedbackName = settings.feedbackName.ifBlank { null },
                feedbackCredits = settings.feedbackCredits.takeIf { it },
                feedbackCreditsEver = settings.creditsEverOn.takeIf { it },
                gamificationEnabled = settings.gamificationEnabled.value,
                celebratedAchievements = settings.celebratedAchievements.value.toList(),
                favoriteBadge = settings.favoriteBadge.value,
                feedbackSent = settings.feedbackSentCount.value,
                testerPoints = settings.testerPoints.value,
                shareBadges = settings.shareBadges.value,
                shareAchievements = settings.shareAchievements.value,
                shareStreak = settings.shareStreak.value,
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
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        context.getDatabasePath(AppDatabase.NAME).inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.update(BackupFormat.encode(manifest.copy(createdAt = 0)).toByteArray())
        imagesDir.listFiles().orEmpty().sortedBy { it.name }.forEach { digest.update(it.name.toByteArray()) }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        Created(file, localSummary(manifest.createdAt), hash)
    }

    /** Übersicht der Daten auf diesem Handy (für den Vergleich mit der Cloud). */
    suspend fun localSummary(createdAt: Long? = null): BackupFormat.Summary = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        BackupFormat.summary(createdAt) { sql ->
            runCatching { db.query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null } }.getOrNull()
        }
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
        manifest.settings.waterEnabled?.let(settings::setWaterEnabled)
        manifest.settings.proteinOnly?.let(settings::setProteinOnly)
        manifest.settings.weightEnabled?.let(settings::setWeightEnabled)
        manifest.settings.bodyAnalysisEnabled?.let(settings::setBodyAnalysisEnabled)
        manifest.settings.measuresEnabled?.let(settings::setMeasuresEnabled)
        manifest.settings.goalSkipped?.let(settings::setGoalSkipped)
        manifest.settings.let { m ->
            if (m.seenTips != null) {
                settings.restoreTips(m.seenTips.toSet(), m.pendingTips.orEmpty().toSet(), m.manualTyped ?: 0)
            } else if (prepared.summary.foodEntries > 0) {
                // Ältere Sicherung (z. B. Umzug aus 0.6.0) kennt die weggeklickten Tipps nicht: wer schon Einträge hat,
                // bekommt keinen Tipp ein zweites Mal (Wunsch des Nutzers: alles Angeklickte bleibt).
                val all = de.kalorienapp.ui.components.Tip.entries.map { it.name } + de.kalorienapp.domain.BehaviorTip.entries.map { it.name }
                settings.restoreTips(all.toSet(), emptySet(), 0)
            }
            m.feedbackName?.let(settings::setFeedbackName)
            m.feedbackCredits?.let(settings::setFeedbackCredits)
            if (m.feedbackCreditsEver == true && m.feedbackCredits != true) {
                settings.setFeedbackCredits(true)
                settings.setFeedbackCredits(false)
            }
            m.gamificationEnabled?.let(settings::setGamificationEnabled)
            m.celebratedAchievements?.let { settings.markCelebrated(it) }
            m.favoriteBadge?.let(settings::setFavoriteBadge)
            m.feedbackSent?.let { settings.countFeedbackSent(it) }
            m.testerPoints?.let(settings::setTesterPoints)
            m.shareBadges?.let(settings::setShareBadges)
            m.shareAchievements?.let(settings::setShareAchievements)
            m.shareStreak?.let(settings::setShareStreak)
            m.weeklyDoneFor?.let { settings.weeklyDoneFor = LocalDate.ofEpochDay(it) }
            // Sicherung aus ≤ 0.6.0 (Umzug auf de.dunories): einmal „Neu in 0.7.0“ zeigen.
            settings.markChangelogSeen(m.changelogSeen ?: OLD_BACKUP_CHANGELOG)
        }
        settings.setFastingEnabled(manifest.settings.fastingEnabled)
        settings.setMealReminders(manifest.settings.mealReminders)
        manifest.settings.let { m ->
            if (m.mealPlanEnabled != null) {
                val d = de.kalorienapp.domain.model.MealPlanSettings()
                settings.setMealPlan(
                    de.kalorienapp.domain.model.MealPlanSettings(
                        enabled = m.mealPlanEnabled,
                        startEpochDay = m.mealPlanStart ?: d.startEpochDay,
                        days = (m.mealPlanDays ?: d.days).coerceIn(1, de.kalorienapp.domain.model.MealPlanSettings.MAX_DAYS),
                        endEpochDay = m.mealPlanEnd,
                        stopAtEnd = m.mealPlanStopAtEnd ?: false,
                        openEnded = m.mealPlanOpenEnded ?: false,
                    ),
                )
            }
        }
        manifest.settings.let { m ->
            if (m.dietStyle != null || m.dislikedFoods != null) {
                val d = de.kalorienapp.domain.model.FoodPreferences()
                settings.setFoodPreferences(
                    de.kalorienapp.domain.model.FoodPreferences(
                        style = de.kalorienapp.domain.model.DietStyle.entries.find { it.name == m.dietStyle } ?: d.style,
                        excluded = m.excludedFoods.orEmpty()
                            .mapNotNull { n -> de.kalorienapp.domain.model.ExcludedFood.entries.find { it.name == n } }.toSet(),
                        intolerances = m.intolerances.orEmpty()
                            .mapNotNull { n -> de.kalorienapp.domain.model.Intolerance.entries.find { it.name == n } }.toSet(),
                        cooking = de.kalorienapp.domain.model.CookingTime.entries.find { it.name == m.cookingTime } ?: d.cooking,
                        budget = de.kalorienapp.domain.model.Budget.entries.find { it.name == m.budget } ?: d.budget,
                        mealPrep = m.mealPrep ?: false,
                        dislikes = m.dislikedFoods.orEmpty(),
                    ),
                )
            }
        }
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
        settings.flushToDisk()
        // Alte Termine (mit Werten von vor dem Einspielen) verwerfen – nach dem Neustart plant scheduleAll() neu.
        runCatching { androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get() }
        staging.deleteRecursively()
    }

    private companion object {
        /** versionCode 7 = 0.6.0, die letzte Version ohne gemerkte Neuigkeiten in der Sicherung. */
        const val OLD_BACKUP_CHANGELOG = 7
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
