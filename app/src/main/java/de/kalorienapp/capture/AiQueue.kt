package de.kalorienapp.capture

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.notify.AiQueueWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * KI-Warteschlange (24.11.2026, Entscheidung des Nutzers nach Gemini-Ausfall): Ist die KI überlastet oder das Netz
 * weg, wird das Foto bzw. die Beschreibung lokal abgelegt und per WorkManager im Hintergrund weiter versucht
 * ([AiQueueWorker]). Fertig → Benachrichtigung + Zeile in der Mahlzeit auf „Heute“; gespeichert wird erst nach Prüfen.
 * Dateien in `files/ai_queue/` (nicht in der Sicherung – nur vorübergehend).
 */
class AiQueue(private val context: Context) {
    private val dir = File(context.filesDir, "ai_queue")
    private val file = File(dir, "queue.json")
    private val mutex = Mutex()

    private val _jobs = MutableStateFlow(runCatching { AiQueueCodec.decode(file.takeIf { it.exists() }?.readText()) }.getOrDefault(emptyList()))
    val jobs: StateFlow<List<QueuedJob>> = _jobs.asStateFlow()

    fun get(id: String): QueuedJob? = _jobs.value.find { it.id == id }

    fun photoUri(job: QueuedJob): Uri = Uri.fromFile(photoFile(job.id))

    /** Ablegen und Hintergrund-Versuche starten. [uri] = Foto (bei [QueuedKind.TEXT] null). */
    suspend fun enqueue(kind: QueuedKind, uri: Uri?, text: String?, hint: String?, date: LocalDate, mealType: MealType): QueuedJob {
        val job = QueuedJob(UUID.randomUUID().toString(), kind, date, mealType, System.currentTimeMillis(), text, hint)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            if (uri != null) copyPhoto(uri, photoFile(job.id))
        }
        change { it + job }
        schedule(job.id)
        return job
    }

    /** Fehlgeschlagenen Eintrag noch einmal im Hintergrund versuchen. */
    suspend fun requeue(id: String) {
        change { list -> list.map { if (it.id == id) it.copy(status = QueuedStatus.WAITING, error = null) else it } }
        schedule(id)
    }

    suspend fun finish(id: String, drafts: List<FoodDraft>) =
        change { list -> list.map { if (it.id == id) it.copy(status = QueuedStatus.DONE, drafts = drafts, error = null) else it } }

    suspend fun fail(id: String, error: String) =
        change { list -> list.map { if (it.id == id) it.copy(status = QueuedStatus.FAILED, error = error) else it } }

    /** Gespeichert oder verworfen → Foto, Hintergrund-Versuch und Benachrichtigung weg. */
    suspend fun remove(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        NotificationManagerCompat.from(context).cancel(notificationId(id))
        change { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) { photoFile(id).delete() }
    }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<AiQueueWorker>()
            .setInputData(workDataOf(AiQueueWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Erster Versuch nach einer halben Minute, danach 1, 2, 4 … Minuten (Überlastung dauert oft etwas).
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun change(transform: (List<QueuedJob>) -> List<QueuedJob>) = mutex.withLock {
        val next = transform(_jobs.value)
        _jobs.value = next
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            file.writeText(AiQueueCodec.encode(next))
        }
    }

    private fun photoFile(id: String) = File(dir, "$id.img")

    /** Foto kopieren (Kamera-Datei im Cache kann verschwinden), höchstens [MAX_PHOTO_BYTES]. */
    private fun copyPhoto(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Foto nicht lesbar")
        input.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_PHOTO_BYTES) throw java.io.IOException("Foto zu groß")
                    out.write(buffer, 0, n)
                }
            }
        }
    }

    companion object {
        private const val MAX_PHOTO_BYTES = 30L * 1024 * 1024

        fun workName(id: String) = "ai_queue_$id"

        /** Eigene Benachrichtigung je Foto (mehrere gleichzeitig möglich). */
        fun notificationId(id: String) = 1000 + (id.hashCode() and 0xFFF)
    }
}
