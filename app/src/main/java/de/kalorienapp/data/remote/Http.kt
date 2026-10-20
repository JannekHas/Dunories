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
