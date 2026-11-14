package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogTest {
    private val available = listOf(5, 6, 7)

    @Test
    fun `nach update kommen alle verpassten versionen, neueste zuerst`() {
        assertEquals(listOf(7, 6), Changelog.unseen(lastSeen = 5, current = 7, available = available))
    }

    @Test
    fun `gelesen heisst nichts mehr zeigen`() {
        assertEquals(emptyList<Int>(), Changelog.unseen(lastSeen = 7, current = 7, available = available))
    }

    @Test
    fun `erstes mal mit der funktion zeigt nur die aktuelle version`() {
        assertEquals(listOf(7), Changelog.unseen(lastSeen = null, current = 7, available = available))
        assertEquals(emptyList<Int>(), Changelog.unseen(lastSeen = null, current = 8, available = available))
    }
}
