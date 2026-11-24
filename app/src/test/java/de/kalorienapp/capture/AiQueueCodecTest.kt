package de.kalorienapp.capture

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AiQueueCodecTest {
    private val nudeln = FoodDraft("Nudeln", 200.0, AmountUnit.GRAM, Nutrients(150.0, 5.0, 30.0, 1.0, sugarG = 1.5), EntrySource.AI_PHOTO)
    private val sosse = FoodDraft("Bolognese", 150.0, AmountUnit.GRAM, Nutrients(90.0, 7.0, 4.0, 5.0, saltG = 0.8), EntrySource.AI_PHOTO)

    @Test
    fun `Warteschlange übersteht Speichern und Laden`() {
        val jobs = listOf(
            QueuedJob("a", QueuedKind.PHOTO, LocalDate.of(2026, 11, 24), MealType.LUNCH, 123L, hint = "Linsen"),
            QueuedJob(
                "b", QueuedKind.TEXT, LocalDate.of(2026, 11, 25), MealType.DINNER, 456L, text = "Spaghetti",
                status = QueuedStatus.DONE,
                drafts = listOf(
                    FoodDraft("Spaghetti Bolognese", 350.0, AmountUnit.GRAM, Nutrients(125.0, 6.0, 19.0, 2.7), EntrySource.AI_TEXT, listOf(nudeln, sosse)),
                    FoodDraft("Wasser", null, AmountUnit.MILLILITER, Nutrients(0.0, 0.0, 0.0, 0.0), EntrySource.AI_TEXT),
                ),
            ),
            QueuedJob("c", QueuedKind.LABEL, LocalDate.of(2026, 11, 24), MealType.BREAKFAST, 789L, status = QueuedStatus.FAILED, error = "NO_FOOD"),
        )
        assertEquals(jobs, AiQueueCodec.decode(AiQueueCodec.encode(jobs)))
    }

    @Test
    fun `defekte Datei oder Einträge werden übersprungen`() {
        assertTrue(AiQueueCodec.decode("kaputt").isEmpty())
        assertTrue(AiQueueCodec.decode(null).isEmpty())
        val ok = AiQueueCodec.encode(listOf(QueuedJob("a", QueuedKind.PHOTO, LocalDate.of(2026, 11, 24), MealType.SNACK, 1L)))
        val mixed = "[" + """{"id":"x","kind":"UNBEKANNT"},""" + ok.removePrefix("[")
        assertEquals(listOf("a"), AiQueueCodec.decode(mixed).map { it.id })
    }
}
