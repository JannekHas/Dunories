package de.kalorienapp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupFormatTest {

    private fun manifest(dbVersion: Int = 13, app: String = BackupFormat.APP_ID, format: Int = 1) =
        BackupFormat.encode(BackupFormat.Manifest(app = app, format = format, databaseVersion = dbVersion, createdAt = 1L))

    @Test
    fun `gueltige und aeltere sicherung`() {
        val current = BackupFormat.check(manifest(13), currentDatabaseVersion = 13)
        assertTrue(current is BackupFormat.Result.Valid)
        // Ältere Datenbank wird beim Öffnen von Room migriert
        assertTrue(BackupFormat.check(manifest(11), currentDatabaseVersion = 13) is BackupFormat.Result.Valid)
    }

    @Test
    fun `einstellungen bleiben erhalten`() {
        val json = BackupFormat.encode(
            BackupFormat.Manifest(
                databaseVersion = 13, createdAt = 5L,
                settings = BackupFormat.Settings(caloriesNotification = true, weightReminderDays = null, weeklyReport = false),
            ),
        )
        val valid = BackupFormat.check(json, 13) as BackupFormat.Result.Valid
        assertEquals(BackupFormat.Settings(caloriesNotification = true, weightReminderDays = null, weeklyReport = false), valid.manifest.settings)
    }

    @Test
    fun `neuere app version wird abgelehnt`() {
        assertEquals(
            BackupFormat.Result.Invalid(BackupFormat.Problem.NEWER_APP),
            BackupFormat.check(manifest(14), currentDatabaseVersion = 13),
        )
        assertEquals(
            BackupFormat.Result.Invalid(BackupFormat.Problem.NEWER_APP),
            BackupFormat.check(manifest(13, format = 2), currentDatabaseVersion = 13),
        )
    }

    @Test
    fun `fremde oder kaputte datei`() {
        val notABackup = BackupFormat.Result.Invalid(BackupFormat.Problem.NOT_A_BACKUP)
        assertEquals(notABackup, BackupFormat.check(null, 13))
        assertEquals(notABackup, BackupFormat.check("kein json", 13))
        assertEquals(notABackup, BackupFormat.check(manifest(app = "com.andere.app"), 13))
    }

    @Test
    fun `bildnamen ohne pfad tricks`() {
        assertEquals("abc.jpg", BackupFormat.imageName("images/abc.jpg"))
        assertNull(BackupFormat.imageName("images/../kalorien.db"))
        assertNull(BackupFormat.imageName("images/sub/x.jpg"))
        assertNull(BackupFormat.imageName("images/"))
        assertNull(BackupFormat.imageName("kalorien.db"))
    }

    @Test
    fun `bildpfad auf neues geraet umbiegen`() {
        assertEquals(
            "/data/user/0/de.kalorienapp/files/recipe_images/a1.jpg",
            BackupFormat.relocate("/data/data/de.kalorienapp/files/recipe_images/a1.jpg", "/data/user/0/de.kalorienapp/files/recipe_images/"),
        )
    }

    @Test
    fun dateiname() {
        assertEquals("KalorienApp-Sicherung-2026-10-23.zip", BackupFormat.fileName(LocalDate.of(2026, 10, 23)))
    }
}
