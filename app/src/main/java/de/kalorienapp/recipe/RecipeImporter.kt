package de.kalorienapp.recipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import de.kalorienapp.capture.ImageLoader
import de.kalorienapp.data.remote.gemini.GeminiException
import de.kalorienapp.data.remote.gemini.GeminiFile
import de.kalorienapp.data.remote.gemini.GeminiFiles
import de.kalorienapp.data.remote.gemini.GeminiRecipeAnalyzer
import de.kalorienapp.data.remote.gemini.RecipeAnalysisInput
import de.kalorienapp.data.remote.social.SocialClient
import de.kalorienapp.data.remote.social.SocialPageParser
import de.kalorienapp.data.remote.social.SocialPlatform
import de.kalorienapp.domain.RecipeCalculator
import de.kalorienapp.domain.model.RecipeDraft
import de.kalorienapp.domain.model.RecipeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Was an die App geteilt wurde. */
sealed interface ImportSource {
    data class Link(val url: String) : ImportSource
    data class Text(val text: String) : ImportSource
    data class Images(val uris: List<Uri>) : ImportSource
    data class Video(val uri: Uri) : ImportSource
}

enum class ImportStage { READING_LINK, UPLOADING_VIDEO, ANALYZING, PREPARING_IMAGE }

sealed interface ImportResult {
    /** Vorschlag; das zugeschnittene Bild liegt bereits im Bildspeicher ([RecipeDraft.recipe].imagePath). */
    data class Success(val draft: RecipeDraft) : ImportResult

    data class Failure(val reason: ImportFailure, val platform: SocialPlatform? = null) : ImportResult
}

enum class ImportFailure {
    /** Beitragsseite nicht lesbar (privat, gelöscht, gesperrt, Netzwerk). */
    LINK_UNREADABLE,

    /** Inhalt gelesen, aber kein Rezept erkannt. */
    NO_RECIPE,
    NO_API_KEY,
    QUOTA,
    NETWORK,
    AI_ERROR,
    FILE_UNREADABLE,
}

/**
 * Social-Media-Import: Link → öffentliche Beitragsseite (Beschreibung, Untertitel, Cover),
 * oder geteilte Datei (Video/Screenshots) → Gemini → Rezeptvorschlag mit zugeschnittenem Gerichtsbild.
 */
class RecipeImporter(
    private val context: Context,
    private val social: SocialClient,
    private val analyzer: GeminiRecipeAnalyzer,
    private val files: GeminiFiles,
    private val imageStore: RecipeImageStore,
) {

    suspend fun import(source: ImportSource, categories: List<String>, onStage: (ImportStage) -> Unit): ImportResult =
        try {
            when (source) {
                is ImportSource.Link -> importLink(source.url, categories, onStage)
                is ImportSource.Text -> {
                    // Geteilter Text mit Link → Link importieren (z. B. „Schau dir das an: https://…“).
                    val url = SocialPageParser.extractUrl(source.text)
                    if (url != null && SocialPageParser.platformOf(url) != SocialPlatform.OTHER) {
                        importLink(url, categories, onStage)
                    } else {
                        analyze(RecipeAnalysisInput(caption = source.text, source = RecipeSource.TEXT, sourceUrl = url), categories, onStage, emptyList())
                    }
                }
                is ImportSource.Images -> importImages(source.uris, categories, onStage)
                is ImportSource.Video -> importVideo(source.uri, categories, onStage)
            }
        } catch (e: GeminiException) {
            ImportResult.Failure(e.toFailure())
        } catch (e: IOException) {
            ImportResult.Failure(ImportFailure.NETWORK)
        }

    private suspend fun importLink(url: String, categories: List<String>, onStage: (ImportStage) -> Unit): ImportResult {
        onStage(ImportStage.READING_LINK)
        val platform = SocialPageParser.platformOf(url)
        val post = runCatching { social.fetch(url) }.getOrNull()
        if (post == null || (!post.hasText && post.imageUrls.isEmpty())) {
            return ImportResult.Failure(ImportFailure.LINK_UNREADABLE, platform)
        }
        val imageUrls = (listOfNotNull(post.coverUrl) + post.imageUrls).distinct().take(MAX_IMAGES)
        val bitmaps = imageUrls.mapNotNull { imageUrl ->
            runCatching { decode(social.downloadImage(imageUrl)) }.getOrNull()
        }
        val source = when (post.platform) {
            SocialPlatform.TIKTOK -> RecipeSource.TIKTOK
            SocialPlatform.INSTAGRAM -> RecipeSource.INSTAGRAM
            SocialPlatform.OTHER -> RecipeSource.TEXT
        }
        val input = RecipeAnalysisInput(
            caption = post.caption,
            transcript = post.transcript,
            images = bitmaps.map(::toAiJpeg),
            source = source,
            sourceUrl = post.url,
        )
        return when (val result = analyze(input, categories, onStage, bitmaps)) {
            is ImportResult.Failure -> result.copy(platform = post.platform)
            else -> result
        }
    }

    private suspend fun importImages(uris: List<Uri>, categories: List<String>, onStage: (ImportStage) -> Unit): ImportResult {
        onStage(ImportStage.ANALYZING)
        val bitmaps = uris.take(MAX_IMAGES).mapNotNull { uri ->
            runCatching { ImageLoader.load(context, uri, maxSide = CROP_MAX_SIDE) }.getOrNull()
        }
        if (bitmaps.isEmpty()) return ImportResult.Failure(ImportFailure.FILE_UNREADABLE)
        return analyze(
            RecipeAnalysisInput(images = bitmaps.map(::toAiJpeg), source = RecipeSource.IMAGES),
            categories, onStage, bitmaps,
        )
    }

    private suspend fun importVideo(uri: Uri, categories: List<String>, onStage: (ImportStage) -> Unit): ImportResult {
        onStage(ImportStage.UPLOADING_VIDEO)
        val frames = runCatching { extractFrames(uri) }.getOrDefault(emptyList())
        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
        val local = withContext(Dispatchers.IO) { copyToCache(uri) } ?: return ImportResult.Failure(ImportFailure.FILE_UNREADABLE)
        var uploaded: GeminiFile? = null
        try {
            uploaded = local.inputStream().let { files.upload(it, local.length(), mimeType) }
            return analyze(
                RecipeAnalysisInput(video = uploaded, images = frames.map(::toAiJpeg), source = RecipeSource.VIDEO),
                categories, onStage, frames,
            )
        } finally {
            uploaded?.let { files.delete(it) }
            local.delete()
        }
    }

    /** KI-Analyse; danach Gerichtsbild aus dem gewählten Bild zuschneiden und speichern. */
    private suspend fun analyze(
        input: RecipeAnalysisInput,
        categories: List<String>,
        onStage: (ImportStage) -> Unit,
        bitmaps: List<Bitmap>,
    ): ImportResult {
        onStage(ImportStage.ANALYZING)
        val draft = analyzer.analyze(input, categories)
        onStage(ImportStage.PREPARING_IMAGE)
        val hint = draft.image
        val bitmap = hint?.let { bitmaps.getOrNull(it.index) }
        val crop = bitmap?.let { RecipeCalculator.cropRect(hint.box, it.width, it.height) }
        val imagePath = if (bitmap != null && crop != null) {
            val cropped = Bitmap.createBitmap(bitmap, crop[0], crop[1], crop[2] - crop[0], crop[3] - crop[1])
            runCatching { imageStore.save(cropped) }.getOrNull()
        } else {
            null
        }
        return ImportResult.Success(draft.copy(recipe = draft.recipe.copy(imagePath = imagePath)))
    }

    /** Einzelbilder über die Videolänge verteilt (das fertige Gericht kommt meist gegen Ende). */
    private suspend fun extractFrames(uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0) return@withContext emptyList()
            FRAME_POSITIONS.mapNotNull { fraction ->
                retriever.getScaledFrameAtTime(
                    (durationMs * fraction * 1000).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    CROP_MAX_SIDE,
                    CROP_MAX_SIDE,
                )
            }
        } finally {
            retriever.release()
        }
    }

    /** Geteilte Videos sind oft nur kurz lesbar → in den Cache kopieren (Länge wird für den Upload gebraucht). */
    private fun copyToCache(uri: Uri): File? {
        val file = File(context.cacheDir, "import_video.tmp")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return null
            file.takeIf { it.length() in 1..MAX_VIDEO_BYTES } ?: run { file.delete(); null }
        }.getOrNull()
    }

    private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun toAiJpeg(bitmap: Bitmap) = ImageLoader.toJpeg(ImageLoader.scaleDown(bitmap, AI_MAX_SIDE), quality = 80)

    private fun GeminiException.toFailure() = when (error) {
        de.kalorienapp.data.remote.gemini.GeminiError.NO_API_KEY -> ImportFailure.NO_API_KEY
        de.kalorienapp.data.remote.gemini.GeminiError.QUOTA -> ImportFailure.QUOTA
        de.kalorienapp.data.remote.gemini.GeminiError.NETWORK -> ImportFailure.NETWORK
        de.kalorienapp.data.remote.gemini.GeminiError.NO_FOOD -> ImportFailure.NO_RECIPE
        else -> ImportFailure.AI_ERROR
    }

    private companion object {
        const val MAX_IMAGES = 6
        const val AI_MAX_SIDE = 768
        const val CROP_MAX_SIDE = 1280
        const val MAX_VIDEO_BYTES = 300L * 1024 * 1024
        val FRAME_POSITIONS = listOf(0.1, 0.3, 0.5, 0.7, 0.85, 0.97)
    }
}
