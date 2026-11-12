package de.kalorienapp.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Spracheingabe, die bei Pausen nicht aufhört: Die Android-Spracherkennung beendet sich nach kurzer
 * Stille von selbst – dann wird sie sofort neu gestartet, bis der Nutzer auf „Fertig“ tippt.
 * Vorläufige Ergebnisse kommen laufend über [onUpdate], damit der Text live mitgeschrieben wird.
 */
class ContinuousSpeech(
    context: Context,
    /** Fertig erkannte Abschnitte + der gerade gesprochene, vorläufige Teil. */
    private val onUpdate: (segments: List<String>, partial: String) -> Unit,
    /** Lautstärke 0..1 für die Anzeige, dass zugehört wird. */
    private val onLevel: (Float) -> Unit,
    /** Erkennung endgültig beendet; `failed` = ging nicht (z. B. keine Verbindung, keine Spracherkennung). */
    private val onStopped: (failed: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val segments = mutableListOf<String>()
    private var partial = ""
    private var active = false
    private var fastFailures = 0
    private var lastStart = 0L
    private var preferOffline = true

    fun start() {
        if (active) return
        segments.clear()
        partial = ""
        fastFailures = 0
        active = true
        listen()
    }

    /** Beendet die Aufnahme; das bis dahin Gesprochene bleibt erhalten. */
    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        commitPartial()
        recognizer?.cancel()
        release()
        onLevel(0f)
        onStopped(false)
    }

    fun destroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!active) return
        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        lastStart = System.currentTimeMillis()
        sr.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                // Wird nicht von jeder Erkennung beachtet – der Neustart fängt den Rest ab
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
            },
        )
    }

    private fun commitPartial() {
        if (partial.isNotBlank()) segments += partial.trim()
        partial = ""
    }

    private fun restart(delayMs: Long = 0) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            onLevel(0f)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            // typisch -2 (Stille) bis 10 (laut)
            onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                partial = text
                onUpdate(segments.toList(), partial)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) partial = text
            commitPartial()
            fastFailures = 0
            onUpdate(segments.toList(), "")
            restart()
        }

        override fun onError(error: Int) {
            if (!active) return
            commitPartial()
            onUpdate(segments.toList(), "")
            when (error) {
                // Pause / nichts verstanden → einfach weiter zuhören
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Fehler direkt nach dem Start mehrfach hintereinander = Erkennung kaputt, nicht endlos neu starten
                    if (System.currentTimeMillis() - lastStart < 500) fastFailures++ else fastFailures = 0
                    if (fastFailures >= 5) fail() else restart()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (++fastFailures >= 5) fail() else {
                        recognizer?.cancel()
                        release()
                        restart(300)
                    }
                }
                // Deutsch nicht auf dem Gerät installiert → online weitermachen
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (preferOffline) {
                        preferOffline = false
                        recognizer?.cancel()
                        release()
                        restart(100)
                    } else fail()
                }
                else -> fail()
            }
        }
    }

    private fun fail() {
        active = false
        handler.removeCallbacksAndMessages(null)
        release()
        onLevel(0f)
        onStopped(true)
    }
}
