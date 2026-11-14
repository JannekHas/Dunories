package de.kalorienapp.capture

import android.content.Context
import android.net.Uri
import de.kalorienapp.data.remote.gemini.GeminiMealPlanAnalyzer
import de.kalorienapp.domain.model.MealPlanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ernährungsplan aus Fotos/Screenshots und/oder Text → Vorschlag (wird erst nach Bestätigung übernommen). */
class MealPlanImporter(private val context: Context, private val analyzer: GeminiMealPlanAnalyzer) {
    suspend fun import(images: List<Uri>, text: String, pdfs: List<Uri> = emptyList()): List<MealPlanItem> {
        // Pläne sind Text mit kleiner Schrift → höhere Auflösung wie beim Etikett.
        val bytes = images.take(MAX_IMAGES).map { uri ->
            ImageLoader.toAiImage(ImageLoader.load(context, uri, maxSide = MAX_SIDE), quality = 90)
        }
        val pdfBytes = withContext(Dispatchers.IO) {
            pdfs.take(MAX_PDFS).map { uri ->
                val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("PDF nicht lesbar")
                if (data.size > MAX_PDF_BYTES) throw PdfTooLargeException()
                data
            }
        }
        return analyzer.analyze(bytes, text, pdfBytes)
    }

    /** PDF größer als [MAX_PDF_BYTES] (Gemini nimmt inline höchstens ~20 MB pro Anfrage). */
    class PdfTooLargeException : Exception()

    companion object {
        const val MAX_IMAGES = 5
        const val MAX_PDFS = 2
        const val MAX_PDF_MB = 15
        private const val MAX_PDF_BYTES = MAX_PDF_MB * 1024 * 1024
        private const val MAX_SIDE = 1800
    }
}
