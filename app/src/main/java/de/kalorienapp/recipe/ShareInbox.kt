package de.kalorienapp.recipe

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nimmt per Android-Teilen-Menü geteilte Inhalte entgegen (Link, Text, Video, Bilder),
 * bis der Import-Screen sie abholt.
 */
class ShareInbox {
    private val _pending = MutableStateFlow<ImportSource?>(null)
    val pending: StateFlow<ImportSource?> = _pending.asStateFlow()

    /** @return true, wenn der Intent etwas Importierbares enthielt. */
    fun accept(intent: Intent?): Boolean {
        val source = parse(intent) ?: return false
        _pending.value = source
        return true
    }

    fun put(source: ImportSource) {
        _pending.value = source
    }

    fun take(): ImportSource? = _pending.value.also { _pending.value = null }

    private fun parse(intent: Intent?): ImportSource? {
        intent ?: return null
        val type = intent.type.orEmpty()
        return when (intent.action) {
            Intent.ACTION_SEND -> when {
                type.startsWith("video/") -> intent.stream()?.let { ImportSource.Video(it) }
                type.startsWith("image/") -> intent.stream()?.let { ImportSource.Images(listOf(it)) }
                else -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
                    ImportSource.Text(listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), text).joinToString("\n"))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> intent.streams().takeIf { it.isNotEmpty() }?.let { uris ->
                if (type.startsWith("video/")) ImportSource.Video(uris.first()) else ImportSource.Images(uris)
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.stream(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streams(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}
