package de.kalorienapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsClient
import de.kalorienapp.domain.model.FoodDraft
import java.io.IOException

/** Das Foto ließ sich nicht lesen (z. B. Datei weg) – eigene Meldung statt „keine Verbindung“ (Marek, 27.11.2026). */
class ImageReadException(cause: Throwable) : Exception(cause)

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

    /**
     * Zuletzt an die KI geschicktes Foto: Eine Korrektur („das ist eigentlich …“) schickt dasselbe Bild noch
     * einmal, ohne die Datei neu zu lesen.
     */
    private var lastAiImage: Pair<Uri, ByteArray>? = null

    private suspend fun load(uri: Uri, maxSide: Int): Bitmap =
        try {
            ImageLoader.load(context, uri, maxSide)
        } catch (e: IOException) {
            throw ImageReadException(e)
        }

    /** Gerade Internet? Ohne Netz gar nicht erst auf die KI warten (Handy-Test 26.11.). */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Nur nachsehen, ob ein Barcode auf dem Foto ist (vor der Foto-Vorschau: Barcode → Vorschau überspringen). */
    suspend fun scanBarcode(uri: Uri): String? = barcodeScanner.scan(load(uri, maxSide = BARCODE_MAX_SIDE))

    /** [hint] = was der Nutzer zum Foto gesagt hat (nur für die KI; bei einem Barcode egal). */
    suspend fun analyzePhoto(uri: Uri, onStage: (AnalysisStage) -> Unit, hint: String? = null): AnalysisResult {
        onStage(AnalysisStage.BARCODE)
        val bitmap = load(uri, maxSide = BARCODE_MAX_SIDE)
        val barcode = barcodeScanner.scan(bitmap)
        if (barcode != null) {
            onStage(AnalysisStage.PRODUCT_LOOKUP)
            ownBarcodes?.find(barcode)?.let { return AnalysisResult.Drafts(listOf(it.toDraft()), ownBarcode = barcode) }
            openFoodFacts.lookup(barcode)?.let { return AnalysisResult.Drafts(listOf(it)) }
            community?.lookup(barcode)?.let { return AnalysisResult.Drafts(listOf(it), ownBarcode = barcode, community = true) }
            return AnalysisResult.UnknownBarcode(barcode)
        }
        return AnalysisResult.Drafts(analyzeWithAi(aiImageOf(uri, bitmap), onStage, hint))
    }

    /** Foto direkt per KI analysieren (z. B. wenn der Barcode unbekannt ist). */
    suspend fun analyzePhotoWithAi(uri: Uri, onStage: (AnalysisStage) -> Unit, hint: String? = null): List<FoodDraft> {
        val image = lastAiImage?.takeIf { it.first == uri }?.second ?: aiImageOf(uri, load(uri, maxSide = AI_MAX_SIDE))
        return analyzeWithAi(image, onStage, hint)
    }

    /** Nährwerttabelle ablesen – höhere Auflösung, damit die kleine Schrift lesbar bleibt. */
    suspend fun analyzeLabel(uri: Uri, onStage: (AnalysisStage) -> Unit): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        val bitmap = load(uri, maxSide = LABEL_MAX_SIDE)
        return gemini.analyzeLabel(ImageLoader.toAiImage(bitmap, quality = 90))
    }

    suspend fun analyzeText(description: String, onStage: (AnalysisStage) -> Unit): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        return gemini.analyzeText(description)
    }

    /** Kleineres Bild spart Upload-Zeit und Tokens, reicht für die Erkennung. */
    private fun aiImageOf(uri: Uri, bitmap: Bitmap): ByteArray =
        ImageLoader.toAiImage(ImageLoader.scaleDown(bitmap, AI_MAX_SIDE)).also { lastAiImage = uri to it }

    private suspend fun analyzeWithAi(image: ByteArray, onStage: (AnalysisStage) -> Unit, hint: String? = null): List<FoodDraft> {
        onStage(AnalysisStage.AI)
        return gemini.analyzePhoto(image, hint)
    }

    private companion object {
        const val BARCODE_MAX_SIDE = 2048
        const val AI_MAX_SIDE = 1024
        const val LABEL_MAX_SIDE = 1800
    }
}
