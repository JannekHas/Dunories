package de.kalorienapp.domain

import de.kalorienapp.recipe.ShareGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareGuardTest {
    private val guard = ShareGuard("de.kalorienapp")

    @Test
    fun `nur fremde content-dateien`() {
        assertTrue(guard.isAllowedFile("content", "com.android.providers.media.documents"))
        assertTrue(guard.isAllowedFile("content", "com.whatsapp.provider.media"))
        // eigene Dateien (Datenbank, Sicherungen im Cache) und direkte Dateipfade nie
        assertFalse(guard.isAllowedFile("content", "de.kalorienapp.fileprovider"))
        assertFalse(guard.isAllowedFile("content", "de.kalorienapp.cleantest.fileprovider"))
        assertFalse(guard.isAllowedFile("file", null))
        assertFalse(guard.isAllowedFile("FILE", "localhost"))
        assertFalse(guard.isAllowedFile(null, null))
    }

    @Test
    fun `hoechstens ein geteilter inhalt pro minute`() {
        assertTrue(guard.tryAccept(now = 0))
        assertFalse(guard.tryAccept(now = 1_000))
        assertFalse(guard.tryAccept(now = 59_999))
        assertTrue(guard.tryAccept(now = 60_000))
    }
}
