package de.kalorienapp.capture

import android.content.Context
import android.net.Uri
import de.kalorienapp.data.remote.gemini.GeminiMealPlanAnalyzer
import de.kalorienapp.domain.model.MealPlanItem

/** Ernährungsplan aus Fotos/Screenshots und/oder Text → Vorschlag (wird erst nach Bestätigung übernommen). */
class MealPlanImporter(private val context: Context, private val analyzer: GeminiMealPlanAnalyzer) {
    suspend fun import(images: List<Uri>, text: String): List<MealPlanItem> {
        // Pläne sind Text mit kleiner Schrift → höhere Auflösung wie beim Etikett.
        val bytes = images.take(MAX_IMAGES).map { uri ->
            ImageLoader.toAiImage(ImageLoader.load(context, uri, maxSide = MAX_SIDE), quality = 90)
        }
        return analyzer.analyze(bytes, text)
    }

    companion object {
        const val MAX_IMAGES = 5
        private const val MAX_SIDE = 1800
    }
}
