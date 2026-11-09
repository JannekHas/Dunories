package de.kalorienapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Lädt Fotos verkleinert und korrekt gedreht (ImageDecoder wertet die EXIF-Ausrichtung aus). */
internal object ImageLoader {

    suspend fun load(context: Context, uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software-Bitmap, damit ML Kit und JPEG-Kompression darauf zugreifen können.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt(),
                )
            }
        }
    }

    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true,
        )
    }

    /**
     * Bild für die KI: WebP ist bei gleicher Qualität deutlich kleiner als JPEG (gemessen: 25 statt 70 KB
     * bei 768×1024, ~70 ms mehr Rechenzeit) → schnellerer Upload, vor allem im Handynetz. Gemini nimmt WebP direkt an ([de.kalorienapp.data.remote.gemini.GeminiClient.IMAGE_MIME]).
     */
    fun toAiImage(bitmap: Bitmap, quality: Int = 80): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(webp, quality, out)
            out.toByteArray()
        }

    @Suppress("DEPRECATION")
    private val webp = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** Für gespeicherte Rezeptbilder (Dateien und Sicherungen bleiben JPEG). */
    fun toJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
