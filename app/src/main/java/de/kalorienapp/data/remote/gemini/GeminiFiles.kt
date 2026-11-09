package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Hochgeladene Datei bei Gemini (nur für die Analyse, wird danach gelöscht). */
data class GeminiFile(val name: String, val uri: String, val mimeType: String)

/**
 * Datei-Schnittstelle von Gemini für Videos (zu groß für Inline-Daten):
 * Upload (resumable) → warten bis ACTIVE → nach der Analyse löschen.
 */
class GeminiFiles(private val client: GeminiClient) {

    suspend fun upload(input: InputStream, length: Long, mimeType: String): GeminiFile {
        if (client.key.isBlank()) throw GeminiException(GeminiError.NO_API_KEY)
        val uploadUrl = withContext(Dispatchers.IO) {
            val connection = open("$BASE/upload/v1beta/files", "POST")
            try {
                connection.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
                connection.setRequestProperty("X-Goog-Upload-Command", "start")
                connection.setRequestProperty("X-Goog-Upload-Header-Content-Length", length.toString())
                connection.setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write("""{"file":{"display_name":"rezept-video"}}""".toByteArray()) }
                check(connection)
                connection.getHeaderField("X-Goog-Upload-URL") ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
            } finally {
                connection.disconnect()
            }
        }
        val fileJson = withContext(Dispatchers.IO) {
            val connection = open(uploadUrl, "POST")
            try {
                connection.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
                connection.setRequestProperty("X-Goog-Upload-Offset", "0")
                connection.setFixedLengthStreamingMode(length)
                connection.readTimeout = 300_000
                connection.doOutput = true
                connection.outputStream.use { out -> input.use { it.copyTo(out, 64 * 1024) } }
                check(connection)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        var file = parseFile(fileJson) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        // Videos werden serverseitig verarbeitet; erst bei ACTIVE nutzbar.
        repeat(60) {
            when (file.second) {
                "ACTIVE" -> return file.first
                "FAILED" -> throw GeminiException(GeminiError.INVALID_RESPONSE)
            }
            delay(2_000)
            file = parseFile(get("$BASE/v1beta/${file.first.name}")) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        }
        throw GeminiException(GeminiError.NETWORK)
    }

    /** Fehler beim Löschen werden ignoriert (Gemini löscht Dateien ohnehin nach 48 h). */
    suspend fun delete(file: GeminiFile) {
        runCatching {
            withContext(Dispatchers.IO) {
                val connection = open("$BASE/v1beta/${file.name}", "DELETE")
                try {
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = open(url, "GET")
        try {
            check(connection)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            client.authHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun check(connection: HttpURLConnection) {
        val code = try {
            connection.responseCode
        } catch (e: IOException) {
            throw GeminiException(GeminiError.NETWORK, e)
        }
        if (code !in 200..299) throw GeminiException(GeminiClient.errorFor(code), HttpStatusException(code, "HTTP $code"))
    }

    /** @return Datei und Status (PROCESSING/ACTIVE/FAILED) */
    private fun parseFile(json: String): Pair<GeminiFile, String>? {
        val root = runCatching { lenientJson.parseToJsonElement(json).asObject() }.getOrNull() ?: return null
        val file = root["file"].asObject() ?: root
        val name = file.string("name") ?: return null
        val uri = file.string("uri") ?: return null
        return GeminiFile(name, uri, file.string("mimeType") ?: "video/mp4") to (file.string("state") ?: "ACTIVE")
    }

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com"
    }
}
