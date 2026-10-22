package de.kalorienapp.recipe

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import de.kalorienapp.capture.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Rezeptbilder als JPEG im privaten App-Speicher (nicht in der Galerie, nicht in Backups). */
class RecipeImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "recipe_images")

    /** Foto aus Galerie/Kamera übernehmen (verkleinert, richtig gedreht). */
    suspend fun saveFromUri(uri: Uri): String = save(ImageLoader.load(appContext, uri, maxSide = MAX_SIDE))

    suspend fun save(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(ImageLoader.toJpeg(ImageLoader.scaleDown(bitmap, MAX_SIDE), quality = 88))
        file.absolutePath
    }

    /** Löscht nur Dateien aus dem eigenen Ordner. */
    fun delete(path: String) {
        val file = File(path)
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    private companion object {
        const val MAX_SIDE = 1280
    }
}
