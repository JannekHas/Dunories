package de.kalorienapp.capture

import android.content.Context
import android.net.Uri
import de.kalorienapp.data.remote.gemini.GeminiBodyAnalyzer
import de.kalorienapp.domain.model.MeasureType

/** Körperanalyse aus Fotos (Waagen-Display, Ausdruck, App-Screenshot) und/oder Text → Vorschlag. */
class BodyAnalysisReader(private val context: Context, private val analyzer: GeminiBodyAnalyzer) {
    suspend fun read(images: List<Uri>, text: String): Map<MeasureType, Double> {
        // Kleine Ziffern auf Displays/Ausdrucken → höhere Auflösung wie beim Etikett.
        val bytes = images.take(MAX_IMAGES).map { uri ->
            ImageLoader.toAiImage(ImageLoader.load(context, uri, maxSide = MAX_SIDE), quality = 90)
        }
        return analyzer.analyze(bytes, text)
    }

    companion object {
        const val MAX_IMAGES = 3
        private const val MAX_SIDE = 1800
    }
}
