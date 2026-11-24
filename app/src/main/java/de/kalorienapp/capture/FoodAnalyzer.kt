package de.kalorienapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsClient
import de.kalorienapp.domain.model.FoodDraft

/** Fortschritt der Analyse, damit die UI anzeigen kann, was gerade passiert. */
enum class AnalysisStage { BARCODE, PRODUCT_LOOKUP, AI }

sealed interface AnalysisResult {
    /**
     * [ownBarcode] = aus den eigenen Barcodes oder der gemeinsamen Sammlung (beim Speichern lokal gemerkt);
     * [community] = von anderen Nutzern → „kurz prüfen“.
     */
    data class Drafts(val drafts: List<FoodDraft>, val ownBarcode: String? = null, val community: Boolean = false) : AnalysisResult

    /** Barcode erkannt, aber nicht in Open Food Facts → Nutzer kann KI-Analyse oder manuelle Eingabe wählen. */
    data class UnknownBarcode(val barcode: String) : AnalysisResult
}

/**
 * Ein-Foto-Erfassung: erst lokal nach Barcode suchen (ML Kit), bei Treffer eigene Barcodes, dann Open Food Facts,
 * sonst das Foto an Gemini. Fehler werden als Exceptions weitergereicht (z. B. [de.kalorienapp.data.remote.gemini.GeminiException]).
 */
class FoodAnalyzer(
    private val context: Context,
    private val openFoodFacts: OpenFoodFactsClient,
    private val gemini: GeminiClient,
    /** Eigene Barcodes: zuerst lokal nachsehen (Produkte, die Open Food Facts nicht kennt). */
    private val ownBarcodes: de.kalorienapp.data.db.dao.OwnBarcodeDao? = null,
    /** Gemeinsame Sammlung: Produkte, die andere per Etikett erfasst haben (wenn Open Food Facts sie nicht kennt). */
    private val community: de.kalorienapp.data.remote.BarcodeCommunityClient? = null,
) {
    private val barcodeScanner = BarcodeScanner()

    suspend fun analyzePhoto(uri: Uri, onStage: (AnalysisStage) -> Unit): AnalysisResult {
        onStage(AnalysisStage.BARCODE)
        val bitmap = ImageLoader.load(context, uri, maxSide = BARCODE_MAX_SIDE)
        val barcode = barcodeScanner.scan(bitmap)
        if (barcode != null) {
            onStage(AnalysisStage.PRODUCT_LOOKUP)
            ownBarcodes?.find(barcode)?.let { return AnalysisResult.Drafts(listOf(it.toDraft()), ownBarcode = barcode) }
            openFoodFacts.lookup(barcode)?.let { return AnalysisResult.Drafts(listOf(it)) }
            community?.lookup(barcode)?.let { return AnalysisResult.Drafts(listOf(it), ownBarcode = barcode, community = true) }
            return AnalysisResult.UnknownBarcode(barcode)
        }
        return AnalysisResult.Drafts(analyzeWithAi(bitmap, onStage))
    }

    /** Foto direkt per KI analysieren (z. B. wenn der Barcode unbekannt ist). */
    suspend fun analyzePhotoWithAi(uri: Uri, onStage: (AnalysisStage) -> Unit, hint: String? = null): List<FoodDraft> =
        analyzeWithAi(ImageLoader.load(context, uri, maxSide = AI_MAX_SIDE), onStage, hint)

    /** Nährwerttabelle ablesen – höhere Auflösung, damit die kleine Schrift lesbar bleibt. */
    suspend fun analyzeLabel(uri: Uri, onStage: (AnalysisStage) -> Unit): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        val bitmap = ImageLoader.load(context, uri, maxSide = LABEL_MAX_SIDE)
        return gemini.analyzeLabel(ImageLoader.toAiImage(bitmap, quality = 90))
    }

    suspend fun analyzeText(description: String, onStage: (AnalysisStage) -> Unit): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        return gemini.analyzeText(description)
    }

    private suspend fun analyzeWithAi(bitmap: Bitmap, onStage: (AnalysisStage) -> Unit, hint: String? = null): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        // Kleineres Bild spart Upload-Zeit und Tokens, reicht für die Erkennung.
        return gemini.analyzePhoto(ImageLoader.toAiImage(ImageLoader.scaleDown(bitmap, AI_MAX_SIDE)), hint)
    }

    private companion object {
        const val BARCODE_MAX_SIDE = 2048
        const val AI_MAX_SIDE = 1024
        const val LABEL_MAX_SIDE = 1800
    }
}
