package de.kalorienapp.domain

import de.kalorienapp.domain.CloudBackup.Decision
import de.kalorienapp.domain.CloudBackup.Meta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CloudBackupTest {
    private fun meta(slot: String, at: Long, complete: Boolean = true) = Meta(slot, at, chunks = 1, complete = complete)

    @Test
    fun `neuester vollstaendiger stand`() {
        assertNull(CloudBackup.newest(emptyList()))
        assertEquals("b", CloudBackup.newest(listOf(meta("a", 1), meta("b", 2)))?.slot)
        // halb geschriebener neuerer Stand zählt nicht
        assertEquals("a", CloudBackup.newest(listOf(meta("a", 1), meta("b", 5, complete = false)))?.slot)
    }

    @Test
    fun `neu wird nie ueber den neuesten stand geschrieben`() {
        assertEquals("a", CloudBackup.targetSlot(emptyList()))
        assertEquals("b", CloudBackup.targetSlot(listOf(meta("a", 1))))
        assertEquals("a", CloudBackup.targetSlot(listOf(meta("a", 1), meta("b", 2))))
        assertEquals("b", CloudBackup.targetSlot(listOf(meta("a", 3), meta("b", 9, complete = false))))
    }

    @Test
    fun `aufteilen und wieder zusammensetzen`() {
        val text = "x".repeat(2_500)
        val parts = CloudBackup.split(text, size = 1_000)
        assertEquals(listOf(1_000, 1_000, 500), parts.map { it.length })
        assertEquals(text, parts.joinToString(""))
        assertEquals(listOf(""), CloudBackup.split(""))
    }

    @Test
    fun `entscheidung beim anmelden`() {
        assertEquals(Decision.UPLOAD, CloudBackup.decide(localEmpty = false, cloud = null, lastUploadAt = null))
        assertEquals(Decision.UPLOAD, CloudBackup.decide(localEmpty = true, cloud = null, lastUploadAt = null))
        assertEquals(Decision.OFFER_RESTORE, CloudBackup.decide(localEmpty = true, cloud = meta("a", 5), lastUploadAt = null))
        assertEquals(Decision.UPLOAD, CloudBackup.decide(localEmpty = false, cloud = meta("a", 5), lastUploadAt = 5))
        assertEquals(Decision.COMPARE, CloudBackup.decide(localEmpty = false, cloud = meta("a", 7), lastUploadAt = 5))
        assertEquals(Decision.COMPARE, CloudBackup.decide(localEmpty = false, cloud = meta("a", 7), lastUploadAt = null))
    }

    @Test
    fun `loesch code hat sechs ziffern`() {
        repeat(20) {
            val code = CloudBackup.deleteCode(Random(it))
            assertEquals(6, code.length)
            assertTrue(code.all(Char::isDigit))
        }
    }
}
