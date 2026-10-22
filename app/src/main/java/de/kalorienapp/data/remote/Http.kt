package de.kalorienapp.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP-Fehler mit Statuscode (z. B. 429 = Kontingent erschöpft). */
class HttpStatusException(val code: Int, message: String) : IOException(message)

/** Minimaler HTTP-Client auf Basis von HttpURLConnection – reicht für die wenigen Aufrufe der App. */
internal object Http {
    private const val USER_AGENT = "KalorienApp/0.1 (private Android-App)"

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15_000): String =
        request(url, "GET", null, headers, timeoutMs)

    /** Wie ein Mobil-Browser – für öffentliche Beitragsseiten (TikTok/Instagram). */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    /** Seite laden (Weiterleitungen werden verfolgt). @return finale URL und HTML */
    suspend fun getPage(url: String, userAgent: String = BROWSER_USER_AGENT): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                val code = connection.responseCode
                if (code !in 200..299) throw HttpStatusException(code, "HTTP $code")
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.url.toString() to text
            } finally {
                connection.disconnect()
            }
        }

    /** Binärdaten laden (Bilder), höchstens [maxBytes]. */
    suspend fun getBytes(url: String, maxBytes: Int = 8_000_000): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code, "HTTP $code")
            // readNBytes gibt es erst ab API 33 → selbst begrenzt lesen.
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("Datei zu groß")
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int): String =
        request(url, "POST", body, headers + ("Content-Type" to "application/json; charset=utf-8"), timeoutMs)

    private suspend fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpStatusException(code, "HTTP $code")
            text
        } finally {
            connection.disconnect()
        }
    }
}
